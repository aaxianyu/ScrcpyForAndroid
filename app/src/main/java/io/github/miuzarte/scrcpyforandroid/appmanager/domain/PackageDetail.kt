package io.github.miuzarte.scrcpyforandroid.appmanager.domain

/**
 * 单包安装信息（`dumpsys package <pkg>` 的展示用摘录）。
 *
 * 每个字段都是「已能直接显示的字符串」，null 表示设备没给这个值（安装器为 `null`、ABI 为
 * `null`、系统应用摘录里没有该行），由界面统一渲染成「未知」。保留原始文本而不在此做本地化：
 * 时间戳与路径都是设备侧的原样输出。
 */
data class PackageDetail(
    val packageName: String,
    /** dumpsys 的 `versionName`；空串按未知处理。 */
    val versionName: String? = null,
    /** 已按 `versionCodeMajor` 合成过的版本号。 */
    val versionCode: String? = null,
    /** dumpsys 的 `appId`，即应用 UID。 */
    val uid: String? = null,
    /** `primaryCpuAbi`；`null` 是设备明确回答「没有主 ABI」，同样按未知展示。 */
    val primaryCpuAbi: String? = null,
    val minSdk: String? = null,
    val targetSdk: String? = null,
    /** `flags` 里带 SYSTEM / UPDATED_SYSTEM_APP 即为系统应用；摘录里没有 flags 行时为 null。 */
    val isSystem: Boolean? = null,
    /** 用户 0 的 `firstInstallTime`，取不到时退回任意用户的同名字段。 */
    val firstInstallTime: String? = null,
    val lastUpdateTime: String? = null,
    /** 安装器包名；系统预装、ADB 或未知来源安装时设备给 `null`。 */
    val installerPackageName: String? = null,
    /**
     * 发起安装的包名。`installerPackageName` 为 null 时只有它还能说明来源：ADB 安装落成
     * `com.android.shell`，而应用商店装的应用两者通常相同。
     */
    val initiatingPackageName: String? = null,
    /** `codePath`：安装包所在目录。 */
    val codePath: String? = null,
    val dataDir: String? = null,
    /** dumpsys 的 `apkSigningVersion`，即包当前使用的签名方案序号（1 / 2 / 3）。 */
    val apkSigningVersion: String? = null,
    /** `requested permissions:` 小节下的权限条数。 */
    val requestedPermissionCount: Int = 0,
    /** 用户 0 的 `runtime permissions:` 小节下的运行时权限条数。 */
    val runtimePermissionCount: Int = 0,
)