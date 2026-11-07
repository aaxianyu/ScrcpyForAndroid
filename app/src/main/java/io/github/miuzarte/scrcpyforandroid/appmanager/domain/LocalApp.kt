package io.github.miuzarte.scrcpyforandroid.appmanager.domain

/**
 * 本机（客户端）已安装应用。
 *
 * [apkPaths] 为 base.apk + 全部分包路径；无分包时只有 base。
 * [apkSizeBytes] 为 [apkPaths] 全部文件大小之和，列表第三行展示用。
 * [versionName] 为应用版本号，第三行与 [apkSizeBytes] 合并展示。
 */
data class LocalApp(
    val packageName: String,
    val label: String,
    val apkPaths: List<String>,
    val isSystem: Boolean,
    val apkSizeBytes: Long = 0L,
    /** 第三行展示用的版本号（无第三行内容时可以为空串）。 */
    val versionName: String = "",
)
