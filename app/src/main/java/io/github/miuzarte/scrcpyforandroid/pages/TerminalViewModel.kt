package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbSocketStream
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.storage.BundleSyncDelegate
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.yukonga.miuix.kmp.basic.SnackbarResult
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

private const val DEFAULT_TERMINAL_FONT_SIZE_SP = 14f
private const val LOG_TAG = "TerminalScreen"

/** 未连接时输入的补发窗口：从第一笔输入起算，到期仍未连上就丢弃。 */
private const val PENDING_INPUT_WINDOW_MS = 3_000L

internal class TerminalViewModel: ViewModel() {

    private val asBundleSync = BundleSyncDelegate(
        sharedFlow = appSettings.bundleState,
        save = { appSettings.saveBundle(it) },
        scope = viewModelScope,
    )
    val asBundle = asBundleSync.value

    private val _terminalFontSizeSp = MutableStateFlow(asBundle.value.terminalFontSizeSp)
    val terminalFontSizeSp: StateFlow<Float> = _terminalFontSizeSp.asStateFlow()

    fun updateTerminalFontSize(newValue: Float, onApplied: (Float) -> Unit): Float {
        val clamped = newValue.coerceIn(1f, 32f)
        if (clamped == _terminalFontSizeSp.value) return clamped
        _terminalFontSizeSp.value = clamped
        asBundleSync.update { it.copy(terminalFontSizeSp = clamped) }
        onApplied(clamped)
        return clamped
    }

    fun adjustTerminalFontSize(delta: Float, onApplied: (Float) -> Unit): Float {
        return updateTerminalFontSize(_terminalFontSizeSp.value + delta, onApplied)
    }

    fun resetFontSizeToDefault(onApplied: (Float) -> Unit) {
        updateTerminalFontSize(DEFAULT_TERMINAL_FONT_SIZE_SP, onApplied)
    }

    private val _shellReady = MutableStateFlow(false)
    val shellReady: StateFlow<Boolean> = _shellReady.asStateFlow()

    private val _shellConnecting = MutableStateFlow(false)
    val shellConnecting: StateFlow<Boolean> = _shellConnecting.asStateFlow()

    private var shellStream: AdbSocketStream? = null
    private var connectJob: Job? = null
    // 每次连接尝试与每次关闭都会自增；在途协程只能改自己代次的状态，
    // 否则旧连接的收尾会把新连接的就绪标志和 writer 一起清掉。
    private var connectGeneration = 0
    private var shellWriterJob: Job? = null
    private var shellWriteChannel = Channel<ByteArray>(Channel.UNLIMITED)

    /**
     * 连接就绪前收到的输入（书签 / 键盘 / 粘贴共用同一入口）。
     *
     * shell 流会在后台被系统掐断（「就绪」标志随之清零），而页面重新回到前台不会重建组合，
     * 需要等 shell 恢复后才能写入。输入按 [PENDING_INPUT_WINDOW_MS] 从第一笔起算攒成一批：
     * 就绪时整批发一次，到期仍未就绪则整批丢弃；每个「连上 → 断开」周期最多提示一次未连接。
     */
    private var pendingInput: ByteArray? = null
    private var pendingInputTimeout: Job? = null

    /** 本次「连上 → 断开」周期是否已经提示过未连接；连上时复位。 */
    private var notConnectedNotified = false

    val sessionHolder = arrayOfNulls<TerminalSession>(1)

    private fun startShellWriter() {
        shellWriteChannel = Channel(Channel.UNLIMITED)
        shellWriterJob = viewModelScope.launch(Dispatchers.IO) {
            for (payload in shellWriteChannel) {
                val stream = shellStream
                if (stream == null || stream.closed) {
                    detachStreamAndRequeue(stream, payload, null)
                    break
                }
                val error = runCatching {
                    stream.outputStream.write(payload)
                    stream.outputStream.flush()
                }.exceptionOrNull()
                if (error != null) {
                    detachStreamAndRequeue(stream, payload, error)
                    break
                }
            }
        }
    }

    /**
     * 一笔输入没写进 shell：断开当前流的引用、复位就绪，并把这一笔交回待发队列。
     * 不释放引用的话，补连会被 openShellSession 的 `shellStream != null` 挡掉；
     * 不交回队列的话，这一笔会落进没有消费者的 channel 静默消失。
     */
    private suspend fun detachStreamAndRequeue(
        stream: AdbSocketStream?,
        payload: ByteArray,
        error: Throwable?,
    ) {
        withContext(Dispatchers.Main) {
            if (shellStream === stream) shellStream = null
            _shellReady.value = false
            // 复位就绪前投递的输入还在旧 channel 里没人消费，和这一笔一起收回待发队列。
            val residual = drainPendingWrites()
            enqueuePendingInput(if (residual == null) payload else payload + residual)
            if (error != null) AppRuntime.snackbar(
                R.string.terminal_snack_input_failed,
                error.message ?: error.javaClass.simpleName,
            )
        }
    }

    /** 取走旧 channel 里尚未写入的输入；writer 已退出时它们不会再被消费。 */
    private fun drainPendingWrites(): ByteArray? {
        var residual: ByteArray? = null
        while (true) {
            val next = shellWriteChannel.tryReceive().getOrNull() ?: break
            residual = residual?.plus(next) ?: next
        }
        return residual
    }

    fun writeBytesToShell(data: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        val payload = data.copyOfRange(offset, offset + count)
        if (!_shellReady.value) {
            enqueuePendingInput(payload)
            return
        }
        shellWriteChannel.trySend(payload)
    }

    /** 未就绪时的输入：按固定窗口攒成一批，顺手发起一次重连。 */
    private fun enqueuePendingInput(payload: ByteArray) {
        if (payload.isEmpty()) return
        pendingInput = pendingInput?.plus(payload) ?: payload
        if (pendingInputTimeout == null) {
            pendingInputTimeout = viewModelScope.launch {
                delay(PENDING_INPUT_WINDOW_MS)
                pendingInput = null
                pendingInputTimeout = null
                if (!_shellReady.value && !notConnectedNotified) {
                    notConnectedNotified = true
                    AppRuntime.snackbar(R.string.terminal_snack_not_connected)
                }
            }
        }
        autoConnectIfNeeded({}, reportFailure = false)
    }

    /** shell 就绪：补发攒下的那一批（若有），并复位本周期的提示状态。 */
    private fun flushPendingInput() {
        pendingInputTimeout?.cancel()
        pendingInputTimeout = null
        notConnectedNotified = false
        val queued = pendingInput ?: return
        pendingInput = null
        shellWriteChannel.trySend(queued)
    }

    /** 将命令书签等整条文本原样送入 shell（不携带 Ctrl/Alt 锁定状态）。 */
    fun writeCommandToShell(command: String) {
        if (command.isEmpty()) return
        val bytes = command.toByteArray(StandardCharsets.UTF_8)
        writeBytesToShell(bytes, 0, bytes.size)
    }

    fun writeClipboardToShell(context: Context) {
        val text = LocalInputService.getClipboardText(context)
        if (!text.isNullOrBlank()) {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            writeBytesToShell(bytes, 0, bytes.size)
        }
    }

    var ctrlLatched by mutableStateOf(false)
    var altLatched by mutableStateOf(false)
    var pendingLatchedConsume by mutableStateOf(false)

    fun consumeLatchedVisualState() {
        ctrlLatched = false
        altLatched = false
        pendingLatchedConsume = false
    }

    fun writeLiteralKey(text: String) {
        var payload = text
        if (ctrlLatched) {
            payload = payload.map { applyCtrlModifier(it) }.joinToString(separator = "")
        }
        if (altLatched) {
            payload = "$payload"
        }
        ctrlLatched = false
        altLatched = false
        pendingLatchedConsume = false
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        writeBytesToShell(bytes, 0, bytes.size)
    }

    fun writeSpecialKey(keyCode: Int) {
        val session = sessionHolder[0] ?: return
        var modifiers = 0
        if (ctrlLatched) modifiers = modifiers or KeyHandler.KEYMOD_CTRL
        if (altLatched) modifiers = modifiers or KeyHandler.KEYMOD_ALT
        ctrlLatched = false
        altLatched = false
        pendingLatchedConsume = false
        val sequence = KeyHandler.getCode(
            keyCode, modifiers,
            session.emulator.isCursorKeysApplicationMode(),
            session.emulator.isKeypadApplicationMode(),
        ) ?: return
        val bytes = sequence.toByteArray(StandardCharsets.UTF_8)
        writeBytesToShell(bytes, 0, bytes.size)
    }

    fun extractTranscript(session: TerminalSession): String {
        val screen = session.emulator.getScreen()
        return screen.getSelectedText(
            selX1 = 0, selY1 = -screen.activeTranscriptRows,
            selX2 = session.emulator.mColumns, selY2 = session.emulator.mRows - 1,
            joinBackLines = false, joinFullLines = false,
        ).trim('\n')
    }

    fun syncOutput(onOutputChange: (String) -> Unit) {
        val session = sessionHolder[0] ?: return
        onOutputChange(extractTranscript(session))
    }

    fun applyTerminalThemeColors(surfaceArgb: Int, onSurfaceArgb: Int, cursorArgb: Int) {
        val session = sessionHolder[0] ?: return
        val colors = session.emulator.mColors.mCurrentColors
        colors[TextStyle.COLOR_INDEX_BACKGROUND] = surfaceArgb
        colors[TextStyle.COLOR_INDEX_FOREGROUND] = onSurfaceArgb
        colors[TextStyle.COLOR_INDEX_CURSOR] = cursorArgb
    }

    fun openShellSession(
        showKeyboardAfterConnect: Boolean,
        requestFocus: () -> Unit,
        reportFailure: Boolean = true,
    ) {
        if (shellStream != null || _shellConnecting.value) {
            if (_shellReady.value && showKeyboardAfterConnect) requestFocus()
            return
        }
        _shellConnecting.value = true
        val generation = ++connectGeneration
        connectJob = viewModelScope.launch(Dispatchers.IO) {
            var opened: AdbSocketStream? = null
            try {
                val streamResult = runCatching {
                    NativeAdbService.openShellStream("")
                }
                val stream = streamResult.getOrElse { error ->
                    withContext(Dispatchers.Main) {
                        if (generation == connectGeneration) {
                            connectJob = null
                            _shellConnecting.value = false
                            _shellReady.value = false
                            // 自动补连（输入触发、前台重试）失败不即时提示，暂存的输入在窗口到期时提示一次；
                            // 用户显式打开的会话（reportFailure = true）失败时直接提示。
                            if (reportFailure) AppRuntime.snackbar(
                                R.string.terminal_snack_session_failed,
                                error.message ?: error.javaClass.simpleName,
                            )
                        }
                    }
                    return@launch
                }
                opened = stream

                var superseded = false
                withContext(Dispatchers.Main) {
                    if (generation != connectGeneration) {
                        superseded = true
                        return@withContext
                    }
                    connectJob = null
                    shellStream = stream
                    _shellReady.value = true
                    _shellConnecting.value = false
                    startShellWriter()
                    flushPendingInput()
                    if (showKeyboardAfterConnect) requestFocus()
                }
                if (superseded) return@launch

                val buffer = ByteArray(4096)
                while (!stream.closed) {
                    val count = stream.inputStream.read(buffer)
                    if (count <= 0) break
                    withContext(Dispatchers.Main) {
                        sessionHolder[0]?.append(buffer, count)
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                withContext(NonCancellable + Dispatchers.Main) {
                    AppRuntime.snackbar(
                        R.string.terminal_snack_output_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
            } finally {
                opened?.let { runCatching { it.close() } }
                // 关闭/取消时 finally 也要能把状态收干净，故不随取消一起中止。
                withContext(NonCancellable + Dispatchers.Main) {
                    if (shellStream === opened) {
                        shellWriterJob?.cancel()
                        shellWriterJob = null
                        shellStream = null
                        _shellReady.value = false
                    }
                    if (generation == connectGeneration) {
                        connectJob = null
                        _shellConnecting.value = false
                        _shellReady.value = false
                    }
                }
            }
        }
    }

    fun autoConnectIfNeeded(onFocus: () -> Unit, reportFailure: Boolean = true) {
        if (_shellReady.value || _shellConnecting.value) return
        viewModelScope.launch {
            val connected = runCatching {
                withContext(Dispatchers.IO) { NativeAdbService.isConnected() }
            }.getOrDefault(false)
            if (connected) openShellSession(false, onFocus, reportFailure)
        }
    }

    fun closeShell() {
        connectGeneration++
        connectJob?.cancel()
        connectJob = null
        shellWriterJob?.cancel()
        shellWriterJob = null
        runCatching { shellStream?.close() }
        shellStream = null
        pendingInputTimeout?.cancel()
        pendingInputTimeout = null
        pendingInput = null
        notConnectedNotified = false
        _shellReady.value = false
        _shellConnecting.value = false
    }

    fun launchFontSizeSnackbar(
        fontSizeSp: Float,
        onReset: (Float) -> Unit,
    ) {
        AppRuntime.snackbar(
            R.string.terminal_font_size_snackbar,
            fontSizeSp.roundToInt(),
            actionLabelResId = R.string.terminal_font_size_restore_default,
            withDismissAction = true,
            onResult = { result ->
                if (result == SnackbarResult.ActionPerformed)
                    resetFontSizeToDefault(onReset)
            },
            dismissNewest = true,
        )
    }

    init {
        asBundleSync.start()
    }

    override fun onCleared() {
        closeShell()
        runBlocking(Dispatchers.IO) { asBundleSync.flush() }
    }

    companion object {
        private fun applyCtrlModifier(ch: Char): Char = when (ch) {
            in 'a'..'z' -> (ch.code - 'a'.code + 1).toChar()
            in 'A'..'Z' -> (ch.code - 'A'.code + 1).toChar()
            ' ' -> 0.toChar()
            '[' -> 27.toChar()
            '\\' -> 28.toChar()
            ']' -> 29.toChar()
            '^' -> 30.toChar()
            '_', '/' -> 31.toChar()
            else -> ch
        }
    }
}
