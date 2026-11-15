package io.github.miuzarte.scrcpyforandroid.appmanager.data

import android.util.Log
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.PackageDetail
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

/**
 * 单包安装信息读取：一次 `dumpsys package <pkg>`。
 *
 * 与应用清单的探测通道分开：这里只有一条命令、返回的是文本，不需要推 dex 也不需要串行锁，
 * 详情页进入时读一次即可；[QUERY_TIMEOUT_MS] 覆盖无线 Adb 的慢响应。
 */
internal object PackageDetailClient {

    private const val TAG = "PackageDetailClient"

    /**
     * 单次读的上限。
     *
     * `dumpsys package` 本身几十毫秒就能返回；设上限是为了在链路断流时给「点了没反应」
     * 一个尽头，而不是让骨架屏一直呼吸下去。
     */
    internal const val QUERY_TIMEOUT_MS = 8_000L

    suspend fun load(packageName: String): PackageDetailResult = withContext(Dispatchers.IO) {
        if (!NativeAdbService.isConnected()) return@withContext PackageDetailResult.Failure(
            AppManagerError.NotConnected,
        )
        val output = runCatching {
            NativeAdbService.shell("dumpsys package $packageName", QUERY_TIMEOUT_MS)
        }
        val error = output.exceptionOrNull()
        if (error != null) {
            // 超时说明这条命令没回来，不算调用方取消：按读取超时上报，不把 withTimeout 的
            // 英文 message 当用户文案透传出去
            if (error is TimeoutCancellationException) {
                Log.w(TAG, "load: timed out after ${QUERY_TIMEOUT_MS}ms")
                return@withContext PackageDetailResult.Failure(AppManagerError.Slow)
            }
            if (error is CancellationException) throw error
            Log.w(TAG, "load: command failed: ${error.javaClass.simpleName}: ${error.message}")
            return@withContext PackageDetailResult.Failure(
                AdbErrors.classify(error.message.orEmpty(), NativeAdbService.isTransportAlive()),
            )
        }
        when (val parsed = PackageDetailParser.parse(output.getOrThrow(), packageName)) {
            is PackageDetailParse.Parsed -> PackageDetailResult.Success(parsed.detail)
            PackageDetailParse.NotFound -> PackageDetailResult.NotFound
            PackageDetailParse.Unrecognized -> PackageDetailResult.Unrecognized
        }
    }
}

/** 包详情的一次读取结果。 */
internal sealed interface PackageDetailResult {
    data class Success(val detail: PackageDetail) : PackageDetailResult

    /** 设备上没有这个包（`Unable to find package`），多半是刚被卸载。 */
    data object NotFound : PackageDetailResult

    /** 设备回了内容，但里面没有可识别的包段（换了 ROM 的 `dumpsys` 版式等）。 */
    data object Unrecognized : PackageDetailResult

    /** 读取失败；[error] 复用应用管理域的归因。 */
    data class Failure(val error: AppManagerError) : PackageDetailResult
}