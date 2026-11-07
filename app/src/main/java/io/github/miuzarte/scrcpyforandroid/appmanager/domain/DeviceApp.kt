package io.github.miuzarte.scrcpyforandroid.appmanager.domain

/**
 * 被控设备上的一个应用。
 *
 * 唯一键是 [packageName]；[label] 为空时由读取方回退为包名最后一段，
 * [versionName] 为空表示整段不展示，[apkSizeBytes] <= 0 视为未知不展示。
 * [apkSizeBytes] 是 base 与全部分包之和，[splitCount] 是分包数量：确认框要靠它当场说清导出产物是
 * 单 apk 还是 zip，不必为此再问一次设备。
 */
data class DeviceApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val apkSizeBytes: Long,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val splitCount: Int = 0,
) {
    /** 应用名为空时回退展示包名最后一段。 */
    val displayLabel: String
        get() = label.ifBlank { packageName.substringAfterLast('.') }

    /** 带分包：导出产物是 zip，安装要把全部分包一并推送。 */
    val isSplit: Boolean
        get() = splitCount > 0
}
