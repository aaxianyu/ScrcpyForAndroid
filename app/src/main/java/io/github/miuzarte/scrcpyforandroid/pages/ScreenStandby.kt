package io.github.miuzarte.scrcpyforandroid.pages

import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Path

/** 熄屏待机的两种操作；[argument] 即设备端 helper 的动作名。 */
internal enum class ScreenStandbyAction(val argument: String) {
    TurnOff("off"),
    TurnOn("on"),
}

/** 熄屏待机的执行结果。 */
internal sealed interface ScreenStandbyOutcome {
    data object Done : ScreenStandbyOutcome
    data object NotConnected : ScreenStandbyOutcome

    /** 等待设备返回时超时（含传输层被中断的情形）；界面用固定文案提示。 */
    data object TimedOut : ScreenStandbyOutcome

    data class Failed(val reason: String) : ScreenStandbyOutcome
}

/**
 * 执行一次熄屏 / 点亮。
 *
 * 通道与探测一致：构建期编好的 helper dex 推到 /data/local/tmp，用
 * `CLASSPATH=… app_process … ScreenStandby <off|on>` 跑一次，回执是一行 JSON。远程临时
 * 文件无论成败都清理，取消时也清理。
 *
 * 熄屏只翻显示面板的电源开关，不进入系统睡眠；点亮分两步：先补发唤醒键把睡眠 / 锁屏
 * 中的设备唤醒，再恢复面板电源——只点面板时系统电源状态机仍停在 Asleep，屏幕是黑的。
 */
internal suspend fun runScreenStandby(action: ScreenStandbyAction): ScreenStandbyOutcome =
    defaultScreenStandbyRunner().run(action)

internal fun defaultScreenStandbyRunner(): ScreenStandbyRunner = ScreenStandbyRunner(
    isConnected = { NativeAdbService.isConnected() },
    isTransportAlive = { NativeAdbService.isTransportAlive() },
    localDex = ::ensureLocalStandbyDex,
    push = { local, remote -> NativeAdbService.push(local, remote) },
    shell = { command, timeoutMs -> NativeAdbService.shell(command, timeoutMs) },
)

/**
 * 熄屏待机执行器。
 *
 * 传输层以函数注入，便于 JVM 单测覆盖超时、失败与取消路径。每一步都带超时：
 * helper 只翻一次电源开关，20 秒足够；唤醒键和清理各自更短。
 */
internal class ScreenStandbyRunner(
    private val isConnected: suspend () -> Boolean,
    private val isTransportAlive: () -> Boolean,
    private val localDex: () -> File,
    private val push: suspend (Path, String) -> Unit,
    private val shell: suspend (String, Long) -> String,
    private val execTimeoutMs: Long = EXEC_TIMEOUT_MS,
    private val wakeTimeoutMs: Long = WAKE_KEY_TIMEOUT_MS,
    private val cleanupTimeoutMs: Long = CLEANUP_TIMEOUT_MS,
) {
    suspend fun run(action: ScreenStandbyAction): ScreenStandbyOutcome = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext ScreenStandbyOutcome.NotConnected
        }
        // 传输层的超时与中断同样以 CancellationException 形态抛出，类型与文案无法
        // 区分，因此以「自身 Job 是否仍存活」判定「本步骤超时」与「父作用域取消」。
        val job = currentCoroutineContext()[Job]
        try {
            runCatching {
                // 只翻面板点不亮睡眠中的设备（真机实测：框架 Asleep + 面板 On = 黑屏）：
                // KEYCODE_WAKEUP 走系统电源状态机，把设备唤醒到锁屏。设备本来就醒着时它是
                // 空操作，待机（面板灭、框架醒）的恢复路径不受影响。
                if (action == ScreenStandbyAction.TurnOn) {
                    shell(WAKE_KEY_COMMAND, wakeTimeoutMs)
                }
                push(localDex().toPath(), REMOTE_DEX_PATH)
                val output = shell(execCommand(action), execTimeoutMs)
                when (val reply = parseScreenStandbyReply(output)) {
                    ScreenStandbyReply.Ok -> ScreenStandbyOutcome.Done
                    is ScreenStandbyReply.Error -> ScreenStandbyOutcome.Failed(limitReason(reply.reason))
                    // 认不出回执且链路已死：连接可能是执行途中被断开的，读流会以「正常结束」
                    // 返回空串或半截 JSON——按「未连接设备」处理，而不是把内部兜底文案当失败；
                    // 链路还活着才是真正的执行失败。
                    is ScreenStandbyReply.Unrecognized ->
                        if (isTransportAlive()) {
                            ScreenStandbyOutcome.Failed(limitReason(reply.snippet))
                        } else {
                            ScreenStandbyOutcome.NotConnected
                        }
                }
            }.getOrElse { error -> failureOf(error, job) }
        } finally {
            // 取消路径也要清理：清理是一次挂起调用，必须放在 NonCancellable 里，
            // 否则协程已取消时它会直接被跳过。
            withContext(NonCancellable) {
                runCatching { shell(CLEANUP_COMMAND, cleanupTimeoutMs) }
            }
        }
    }

    private fun failureOf(error: Throwable, job: Job?): ScreenStandbyOutcome {
        if (error !is CancellationException) {
            val message = error.message.orEmpty().ifBlank { error.javaClass.simpleName }
            // 传输层断开（含入口检查与真正执行之间的竞态）按「未连接」处理，不透传英文；
            // socket 级 IOException 还要看链路是否还活着——死了就是连接没了，活着才是本地错误
            if (message.contains(ADB_NOT_CONNECTED) || message.contains(ADB_LINK_LOST)) {
                return ScreenStandbyOutcome.NotConnected
            }
            if (error is IOException && !isTransportAlive()) {
                return ScreenStandbyOutcome.NotConnected
            }
            return ScreenStandbyOutcome.Failed(limitReason(message))
        }
        // 自身仍在运行：这是某一步的超时 / 线程中断，按失败上报；
        // 自身也被取消：真正的父作用域取消，照常向调用方传播。
        if (job?.isActive != false) {
            return ScreenStandbyOutcome.TimedOut
        }
        throw error
    }
}

/** 把资产里的 helper dex 拷到应用缓存；每次执行前都重拷，避免用到过期的旧版本。 */
private fun ensureLocalStandbyDex(): File {
    val target = File(AppRuntime.context.cacheDir, LOCAL_DEX_NAME)
    AppRuntime.context.assets.open(ASSET_DEX_PATH).use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    return target
}

/** 失败原因会直接进 snackbar：截断到上限，避免 helper 的长堆栈把提示撑爆。 */
internal fun limitReason(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.length <= MAX_REASON_CHARS) trimmed else trimmed.take(MAX_REASON_CHARS) + "…"
}

private fun execCommand(action: ScreenStandbyAction): String =
    "CLASSPATH=$REMOTE_DEX_PATH app_process $REMOTE_DIR ScreenStandby ${action.argument}"

private const val ASSET_DEX_PATH = "bin/screenstandby.dex"
private const val LOCAL_DEX_NAME = "screenstandby.dex"
private const val REMOTE_DIR = "/data/local/tmp"
private const val REMOTE_DEX_PATH = "$REMOTE_DIR/scrcpy_screen_standby.dex"
private const val CLEANUP_COMMAND = "rm -f $REMOTE_DEX_PATH"

/** KEYCODE_WAKEUP = 224；用数字形式，input 命令各版本都认 */
private const val WAKE_KEY_COMMAND = "input keyevent 224"

/** 失败原因上限：足够说明问题，又不至于把 snackbar 撑爆 */
private const val MAX_REASON_CHARS = 500

/** helper 只翻一次电源开关，20 秒已是宽裕上限；超时按失败报出而不是无限等 */
private const val EXEC_TIMEOUT_MS = 20_000L

/** 唤醒键只是一次 input 注入，10 秒等不到响应就按失败收场 */
private const val WAKE_KEY_TIMEOUT_MS = 10_000L

/** 清理只是一条 rm，5 秒足够；失败也无所谓，不能把收尾拖住 */
private const val CLEANUP_TIMEOUT_MS = 5_000L

/** 传输层断开的已知文案（与 NativeAdbService 的抛出一致） */
private const val ADB_NOT_CONNECTED = "ADB not connected"
private const val ADB_LINK_LOST = "ADB connection is no longer available"
