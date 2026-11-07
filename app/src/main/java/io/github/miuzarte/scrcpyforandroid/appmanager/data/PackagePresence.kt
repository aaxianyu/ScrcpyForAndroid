package io.github.miuzarte.scrcpyforandroid.appmanager.data

import android.util.Log
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 包是否仍安装在设备上。
 *
 * 应用清单解析可能不完整（ADB 流截断 / 探测输出漏匹配），「清单里没有」不能直接当成
 * 「已卸载」。逐个用 `pm path` 复核：有输出=仍在，空输出=确实没装，命令失败=无法确认。
 * 无法确认的一律不算缺失——收藏是用户数据，宁可这次不删。
 */
internal object PackagePresence {

    /** 返回 [packages] 中确认未安装的那些；查询失败或包名含非法字符的一律不返回。 */
    suspend fun confirmedMissing(packages: Collection<String>): Set<String> = withContext(Dispatchers.IO) {
        if (packages.isEmpty()) return@withContext emptySet()
        val missing = mutableSetOf<String>()
        for (packageName in packages) {
            // 包名只含字母数字与下划线点；脏值（历史偏好 / 手工写入）不进命令行
            if (!PACKAGE_NAME.matches(packageName)) continue
            val output = runCatching { NativeAdbService.shell("pm path $packageName") }
                .onFailure { Log.w(TAG, "pm path failed: $packageName", it) }
                .getOrNull() ?: continue
            if (output.isBlank()) missing += packageName
        }
        missing
    }

    private const val TAG = "PackagePresence"
    private val PACKAGE_NAME = Regex("[A-Za-z0-9_.]+")
}
