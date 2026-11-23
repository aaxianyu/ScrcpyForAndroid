package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.miuzarte.scrcpyforandroid.appmanager.data.DeviceAppRepository

/** 导出产物的查看意图；条目属于本应用，交给对方之前先把读权限让出来。 */
internal fun exportViewIntent(uri: Uri, mimeType: String): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        // clipData 让 chooser 把读权限一并转给用户选中的应用
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

/** 按 [kind] 的 MIME 向系统拉起「打开方式」，由用户挑能打开它的应用；`.apks` 起不来时退回通配 MIME。 */
internal fun openExportTarget(context: Context, uri: Uri, kind: DeviceAppRepository.ExportKind): Boolean {
    fun open(mimeType: String): Boolean = runCatching {
        val intent = exportViewIntent(uri, mimeType)
        // Resolver 总能解析 ACTION_CHOOSER：先看内层 intent 的候选，才判得出没有应用能接
        val candidates = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        if (candidates.isEmpty()) return@runCatching false
        // 系统可能已给这个 MIME 设了默认应用：套 chooser 才会把候选摆给用户
        val chooser = Intent.createChooser(intent, null)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
    // 分包安装器未必注册 `.apks` 这个 type：先按它起，起不来再退回 */*，让系统把全部候选摆出来
    val mimeTypes =
        if (kind == DeviceAppRepository.ExportKind.APK_SET) {
            listOf(kind.mimeType, "*/*")
        } else {
            listOf(kind.mimeType)
        }
    return mimeTypes.any { open(it) }
}
