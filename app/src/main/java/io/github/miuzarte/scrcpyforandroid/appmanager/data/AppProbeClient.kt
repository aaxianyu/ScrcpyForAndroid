package io.github.miuzarte.scrcpyforandroid.appmanager.data

import android.content.Context
import android.util.Log
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.InstallCommands
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 探测通道。
 *
 * 通道形式：把构建期生成的探测 dex 推到被控设备 /data/local/tmp，用
 * `CLASSPATH=… app_process … AppProbe` 执行，stdout 即 JSON 清单；
 * 整体超时 30 秒，远程临时文件无论成败都清理。
 */
class AppProbeClient(context: Context) {

    private val appContext = context.applicationContext

    suspend fun loadApps(metaOnly: Boolean = false): ProbeResult = loadAppsInternal(emptyList(), metaOnly)

    /**
     * 只取指定包的应用名与图标。
     *
     * 进程管理这类只关心「正在跑的那几十个包」的调用方不必把整台设备的清单搬回来——图标
     * 本来就占输出的大头，全量探测会把没在跑的包一并带回来。
     *
     * 包名分批下发：命令行有长度上限，超了会静默失败，取名取图标全空且看不出原因。
     * 任一批失败即整批失败，不给半份结果——半份结果和全空在界面上长得一样。
     *
     * 空集合在这里是「什么都不要」，不会落到全量探测那条路上：全量只由 [loadApps] 发起。
     */
    suspend fun loadAppMeta(packages: Collection<String>): ProbeResult {
        val all = packages.toList()
        if (all.isEmpty()) {
            return ProbeResult.Success(
                apps = emptyList(),
                icons = emptyMap(),
                iconSuffix = IconFiles.suffixFor(""),
            )
        }
        val apps = mutableListOf<DeviceApp>()
        val icons = mutableMapOf<String, ByteArray>()
        var iconSuffix = IconFiles.suffixFor("")
        for (batch in splitIntoBatches(all)) {
            when (val result = loadAppsInternal(batch)) {
                is ProbeResult.Success -> {
                    apps += result.apps
                    icons += result.icons
                    iconSuffix = result.iconSuffix.ifBlank { iconSuffix }
                }

                else -> return result
            }
        }
        return ProbeResult.Success(apps = apps, icons = icons, iconSuffix = iconSuffix)
    }

    /**
     * 分批（每块 [ICON_FETCH_CHUNK_SIZE] 个包）拉图标并逐块回调。
     *
     * 每块成功立即交给 [onChunk]（调用方落盘 + 刷新界面），某块失败即停：已经回调过的块
     * 不受影响，剩下的下次再补。设备应用多时界面文字与已到的图标照常显示，不会因为
     * 最后一块失败而整包丢弃。
     *
     * [onChunk] 返回 false 或协程取消都会停止后续块。
     */
    suspend fun loadAppMetaProgressive(
        packages: List<String>,
        onChunk: suspend (icons: Map<String, ByteArray>, suffix: String) -> Boolean,
    ) {
        for (chunk in packages.chunked(ICON_FETCH_CHUNK_SIZE)) {
            currentCoroutineContext().ensureActive()
            when (val result = loadAppMeta(chunk)) {
                is ProbeResult.Success -> if (!onChunk(result.icons, result.iconSuffix)) return
                else -> return
            }
        }
    }

    private suspend fun loadAppsInternal(filter: List<String>, metaOnly: Boolean = false): ProbeResult {
        if (!NativeAdbService.isConnected()) {
            Log.w(TAG, "loadApps: adb not connected")
            return ProbeResult.NotConnected
        }
        // 本地 dex 缓存与设备端 dex 路径都是共享的：串行执行，避免两次探测互删文件
        return ProbeSerialization.withLock {
            withContext(Dispatchers.IO) {
                // 包名只含字母数字与下划线点，直接跟在命令后面不必转义；空列表表示全量。
                // --meta 在前，只出清单不要图标。按包名取的那条路已经分批，这里拿到的一定非空
                val metaFlag = if (metaOnly) " --meta" else ""
                val suffix = if (filter.isEmpty()) "" else " " + filter.joinToString(" ")
                val result = runCatching {
                    val localDex = ensureLocalDex()
                    NativeAdbService.push(localDex.toPath(), REMOTE_DEX_PATH)
                    val output = NativeAdbService.shell(
                        "CLASSPATH=$REMOTE_DEX_PATH app_process $REMOTE_DIR AppProbe$metaFlag$suffix",
                        PROBE_TIMEOUT_MS,
                    )
                    Log.d(TAG, "loadApps: probe output ${output.length} chars, filter=${filter.size}")
                    ProbePayloadParser.parse(output)
                }.fold(
                    onSuccess = { parsed ->
                        when (parsed) {
                            is ProbePayloadParser.Result.Success -> {
                                Log.d(TAG, "loadApps: parsed ${parsed.apps.size} apps, icons ${parsed.iconBytes.size}")
                                if (parsed.iconError.isNotBlank()) {
                                    Log.w(TAG, "loadApps: icon error: ${parsed.iconError}")
                                }
                                ProbeResult.Success(
                                    apps = parsed.apps,
                                    icons = parsed.iconBytes,
                                    iconSuffix = parsed.iconSuffix,
                                )
                            }

                            is ProbePayloadParser.Result.Failure -> {
                                Log.w(TAG, "loadApps: probe reported failure: ${parsed.reason}")
                                ProbeResult.Failed(
                                    AdbErrors.classify(parsed.reason, NativeAdbService.isTransportAlive()),
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "loadApps: channel error: ${error.javaClass.simpleName}: ${error.message}")
                        ProbeResult.Failed(
                            AdbErrors.classify(
                                error.message ?: error.javaClass.simpleName,
                                NativeAdbService.isTransportAlive(),
                            ),
                        )
                    },
                )
                // 远程临时文件无论成败都清理（持锁期间清，不会删到别的探测正在用的文件）
                runCatching { NativeAdbService.shell(InstallCommands.cleanupCommand(listOf(REMOTE_DEX_PATH))) }
                result
            }
        }
    }

    /** 已停用应用集合：`pm list packages -d`。 */
    suspend fun disabledPackages(): Set<String> = withContext(Dispatchers.IO) {
        if (!NativeAdbService.isConnected()) return@withContext emptySet()
        runCatching {
            NativeAdbService.shell("pm list packages -d")
                .lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:") }
                .filter { it.isNotEmpty() }
                .toSet()
        }.getOrElse { emptySet() }
    }

    private fun ensureLocalDex(): File {
        val target = File(appContext.cacheDir, LOCAL_DEX_NAME)
        appContext.assets.open(ASSET_DEX_PATH).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    sealed interface ProbeResult {
        data class Success(
            val apps: List<DeviceApp>,
            val icons: Map<String, ByteArray>,
            /** 图标实际编码（`.webp` / `.png`），落盘文件按它定后缀 */
            val iconSuffix: String,
        ) : ProbeResult

        data object NotConnected : ProbeResult
        data class Failed(val error: AppManagerError) : ProbeResult
    }

    private companion object {
        const val TAG = "AppProbeClient"
        const val ASSET_DEX_PATH = "bin/appprobe.dex"
        const val LOCAL_DEX_NAME = "appprobe.dex"
        const val REMOTE_DIR = "/data/local/tmp"
        const val REMOTE_DEX_PATH = "$REMOTE_DIR/scrcpy_app_probe.dex"
        const val PROBE_TIMEOUT_MS = 30_000L
    }
}

/**
 * 单批包名的字节上限。
 *
 * 包名直接跟在 `app_process … AppProbe` 后面，整条命令要过 `adb shell`。命令行有长度上限，
 * 超了不会报错，只会把命令截掉——探测端拿不到完整参数，于是只回一部分应用，看起来跟
 * 「这些包都没取到名字和图标」一模一样。留出足够余量后一批能装下两千多个包名，任何设备
 * 都用不满，实际只会有一批。
 */
internal const val PROBE_FILTER_BATCH_BYTES = 3_000

/**
 * 图标渐进拉取的块大小（包个数）。
 *
 * 首连大设备（几百个应用）时块太大等于退回「全量一批」：一次 app_process 里给每个应用
 * 取图、压图太慢会撞上 30s 超时，整块白干。切小后每块几秒内回来，界面上的图标逐块出现。
 */
internal const val ICON_FETCH_CHUNK_SIZE = 64

/** 把包名按 [PROBE_FILTER_BATCH_BYTES] 切成若干批，保证拼出来的每条命令都不超长。 */
internal fun splitIntoBatches(packages: List<String>): List<List<String>> {
    val batches = mutableListOf<List<String>>()
    var current = mutableListOf<String>()
    var size = 0
    for (name in packages) {
        // 每个包名后面带一个分隔空格
        val cost = name.length + 1
        if (current.isNotEmpty() && size + cost > PROBE_FILTER_BATCH_BYTES) {
            batches += current
            current = mutableListOf()
            size = 0
        }
        current += name
        size += cost
    }
    if (current.isNotEmpty()) batches += current
    return batches
}
