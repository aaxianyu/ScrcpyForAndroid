package io.github.miuzarte.scrcpyforandroid.appmanager.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把导出到本机的单个 `.apk` 交给系统安装器——装的是这台手机，不是被控设备；
 * 后者那条路是 [ApkInstaller]。
 *
 * 导出产物可能是 MediaStore 的 Downloads 条目，也可能是保存框选定的 SAF 文档：两者的
 * 归属都在系统侧，安装器读不到它们，因此先复制进应用缓存，再用 [FileProvider] 授读。
 * 这是浏览器下载 APK 后安装的同一条路径，代价是命中一次缓存大小的复制。
 *
 * 「安装未知应用」是系统级开关而非运行时权限：未授予时返回 [Outcome.NeedsPermission]，
 * 由调用方拉起 [permissionSettingsIntent]，用户返回后重新调用本对象即可。
 */
internal object LocalApkInstall {

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val STAGING_DIR = "appmanager-local-install"

    /** 暂存件保留这么久：安装器可能在点「安装」后几十秒才来读，太早删会砸掉进行中的安装。 */
    private const val STALE_STAGE_MS = 10 * 60 * 1000L
    private const val FALLBACK_NAME = "export.apk"

    /** 一次「安装」尝试的结果。 */
    sealed interface Outcome {
        /** 安装器已拉起，后续由系统界面负责。 */
        data object Started : Outcome

        /** 还缺「安装未知应用」授权：先拉系统设置，回来重试。 */
        data object NeedsPermission : Outcome

        /**
         * 复制完成时页面已不在前台：系统会丢弃后台启动，安装器不会出现；请求留给调用方，
         * 等页面回到前台用 [launch] 直接拉起 [contentUri]（暂存件已在缓存里，不必再复制）。
         */
        data class Deferred(val contentUri: Uri) : Outcome

        /** 复制或拉起失败；[reason] 面向日志，界面给统一文案。 */
        data class Failed(val reason: String) : Outcome
    }

    /** 系统设置里的「安装未知应用」是否还没对本应用放开。 */
    fun needsPermission(context: Context): Boolean =
        !context.packageManager.canRequestPackageInstalls()

    /** 本应用的「安装未知应用」设置页。 */
    fun permissionSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(
            Uri.parse("package:${context.packageName}"),
        )

    /**
     * 复制产物到缓存并拉起安装器。
     *
     * 复制与 `getUriForFile` 都在 IO 线程（产物可能上百 MB；后者要取 canonical path），
     * 只有 `startActivity` 回主线程。交付后暂存件不立即删，留待 [stage] 的过期清理，
     * 避免撞上安装器的后台拷贝。
     *
     * 复制可能比用户切走的动作更慢：拉起前用 [isForeground] 复核页面是否还在前台，
     * 不在就返回 [Outcome.Deferred]（带上已暂存的 uri），交调用方留到下次回页用 [launch]
     * 直接拉起——后台启动会被系统丢弃，而重新复制只会白白多一次整包 I/O。
     */
    suspend fun install(
        context: Context,
        source: Uri,
        isForeground: () -> Boolean = { true },
    ): Outcome {
        if (needsPermission(context)) return Outcome.NeedsPermission
        val contentUri = try {
            withContext(Dispatchers.IO) {
                val staged = stage(context, source)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    staged,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return Outcome.Failed(error.message ?: error.javaClass.simpleName)
        }
        if (!isForeground()) return Outcome.Deferred(contentUri)
        return launch(context, contentUri)
    }

    /** 用已暂存的产物拉起安装器：调用方须确认页面在前台、授权已给。 */
    suspend fun launch(context: Context, contentUri: Uri): Outcome {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            // clipData 让安装器一并拿到读权限，与导出「打开」那条路的授权方式一致
            clipData = ClipData.newRawUri(null, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 与截图外发一致：非 Activity context 启动时缺 NEW_TASK 会抛 AndroidRuntimeException
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return withContext(Dispatchers.Main) {
            runCatching { context.startActivity(intent) }.fold(
                onSuccess = { Outcome.Started },
                onFailure = { Outcome.Failed(it.message ?: "cannot start the installer") },
            )
        }
    }

    /**
     * 复制一份到 [STAGING_DIR] 下的唯一文件名。
     *
     * 只清过期的旧暂存件：整目录清空会删掉上一次安装会话正在读的文件（大包要拷贝几十秒）。
     */
    private fun stage(context: Context, source: Uri): File {
        val dir = File(context.cacheDir, STAGING_DIR)
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() > STALE_STAGE_MS }?.forEach { it.delete() }
        val input = context.contentResolver.openInputStream(source)
            ?: error("cannot open the exported file")
        return copyToTempFile(input, dir, stageFileName(context, source))
    }

    /** 沿用导出时的文件名（用户在安装器里认得出是哪个），只清掉文件名里不允许的字符。 */
    private fun stageFileName(context: Context, source: Uri): String {
        val name = runCatching {
            context.contentResolver.query(
                source,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                // 部分 ROM 的 MediaStore 会多回列，按列名取索引（与 DeviceAppRepository 的写法一致）
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull()
        val base = (name ?: FALLBACK_NAME)
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .removeSuffix(".apk")
            .removeSuffix(".APK")
            .take(96)
        return "$base-${System.currentTimeMillis()}.apk"
    }
}