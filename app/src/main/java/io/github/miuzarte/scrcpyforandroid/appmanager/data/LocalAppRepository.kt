package io.github.miuzarte.scrcpyforandroid.appmanager.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppOrdering
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.LocalApp
import kotlin.math.max
import kotlin.math.roundToInt
import java.io.File

/**
 * 本机（客户端）应用枚举。
 *
 * 只保留有 APK 路径且非系统应用的条目；排序与设备侧应用管理页共用同一把尺子
 * （[AppOrdering.sortedLocal]）。系统应用过滤判定取 `FLAG_SYSTEM | FLAG_UPDATED_SYSTEM_APP`。
 */
class LocalAppRepository(context: Context) {

    private val appContext = context.applicationContext

    sealed interface Result {
        data class Success(val apps: List<LocalApp>) : Result

        /** 只能枚举到本应用自身：ROM 未授予「读取应用列表」权限。 */
        data object PermissionMissing : Result

        /**
         * 枚举超过 [DeviceAppRepository.LOCAL_LOAD_TIMEOUT_MS] 仍没回来。
         *
         * 枚举是同步的逐包 Binder 调用，不响应取消，卡住时协程取消唤不醒它。这不是失败：
         * 后台那次枚举仍在跑，真回来了会照常覆盖这个状态；它只是让界面别一直转圈。
         */
        data object Slow : Result
    }

    fun loadUserApps(): Result {
        val pm = appContext.packageManager
        // 异常时不按权限缺失上报：直接当成「没有可加装项」空结果返。
        val infos = runCatching {
            pm.getInstalledApplications(0)
        }.getOrNull() ?: return Result.Success(emptyList())
        val ownPackage = appContext.packageName
        // 只能枚举到自身 = 权限缺失；此时列表没有任何可用安装源
        if (infos.none { it.packageName != ownPackage }) return Result.PermissionMissing
        val apps = infos.asSequence()
            .filter { info -> info.apkPaths().isNotEmpty() && !info.isSystemApp() }
            .map { info ->
                val paths = info.apkPaths()
                // PackageManager 枚举带 versionName；该字符串用于列表第三行展示
                val versionName = runCatching {
                    pm.getPackageInfo(info.packageName, 0).versionName.orEmpty()
                }.getOrDefault("")
                LocalApp(
                    packageName = info.packageName,
                    label = labelOf(pm, info),
                    apkPaths = paths,
                    isSystem = false,
                    apkSizeBytes = paths.sumOf { path -> File(path).length().coerceAtLeast(0L) },
                    versionName = versionName,
                )
            }
            .toList()
            .let { AppOrdering.sortedLocal(it) }
        return Result.Success(apps)
    }

    // ---------------------------------------------------------------- 本机图标

    /**
     * 本机应用图标内存缓存：行内图标只有 40dp，组合里只读这里。
     * 图标本身是跨进程 Binder 调用，必须在 [warmIcons]（IO 线程）里取，
     * 解码结果按 [ICON_TARGET_PX] 上限采样，大图标不整份进内存。
     */
    private val iconCache =
        object : android.util.LruCache<String, ImageBitmap>(ICON_CACHE_MAX_ENTRIES) {}

    /** 图标解码结果：组合里调用，纯内存命中；未命中返回 null（调用方出占位图）。 */
    fun icon(packageName: String): ImageBitmap? = iconCache.get(packageName)

    /**
     * 预热本机应用图标（IO 线程调用）：[icon] 只做内存命中，
     * 图标读取是跨进程 Binder 调用，离主线程整份灌入 LRU；
     * 完成后由调用方按代次推一次重组，行即取到图标。
     */
    fun warmIcons(packages: Collection<String>) {
        for (packageName in packages) warmIcon(packageName)
    }

    fun warmIcon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        val pm = appContext.packageManager
        val icon = runCatching { pm.getApplicationIcon(packageName) }
            .getOrNull()
            ?.toIconBitmap()
            ?: return null
        return icon.asImageBitmap().also { iconCache.put(packageName, it) }
    }

    /** 任意 Drawable 渲染成 ≤[ICON_TARGET_PX] 位图：满足行内 40dp，又不把大图标全尺寸读进内存。 */
    private fun Drawable.toIconBitmap(): Bitmap? = runCatching {
        val width = intrinsicWidth.takeIf { it > 0 } ?: ICON_TARGET_PX
        val height = intrinsicHeight.takeIf { it > 0 } ?: ICON_TARGET_PX
        val scale = minOf(1f, ICON_TARGET_PX.toFloat() / max(width, height))
        val w = (width * scale).roundToInt().coerceAtLeast(1)
        val h = (height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, w, h)
        draw(canvas)
        bitmap
    }.getOrNull()

    private fun labelOf(pm: PackageManager, info: ApplicationInfo): String =
        runCatching { pm.getApplicationLabel(info).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: info.packageName.substringAfterLast('.')

    private fun ApplicationInfo.apkPaths(): List<String> = buildList {
        sourceDir?.takeIf { it.isNotBlank() }?.let { add(it) }
        splitSourceDirs?.forEach { path -> if (path.isNotBlank()) add(path) }
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    private companion object {
        const val ICON_CACHE_MAX_ENTRIES = 512
        const val ICON_TARGET_PX = 128
    }
}
