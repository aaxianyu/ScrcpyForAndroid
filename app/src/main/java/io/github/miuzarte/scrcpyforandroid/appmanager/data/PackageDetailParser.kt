package io.github.miuzarte.scrcpyforandroid.appmanager.data

import io.github.miuzarte.scrcpyforandroid.appmanager.domain.PackageDetail

/** `dumpsys package <pkg>` 的解析结果。 */
internal sealed interface PackageDetailParse {
    data class Parsed(val detail: PackageDetail) : PackageDetailParse

    data object NotFound : PackageDetailParse

    data object Unrecognized : PackageDetailParse
}

/**
 * `dumpsys package <pkg>` → [PackageDetail]。
 *
 * 只认包段开头的那一行 `Package [<pkg>] (…):`：整份输出里还有 `Packages:` 概览、`Queries:`、
 * `Dexopt state:` 等小节，同一个包名会在这些小节里再出现若干次，从文件头开始 grep 会把别的
 * 小节的行当成包字段。包段的边界是「缩进消失」——顶层小节从第 0 列开始。
 *
 * `firstInstallTime` 只出现在 `User N:` 子段里（Android 7 起），并且是逐用户的值，所以优先取
 * 用户 0；只在完全取不到时才退回第一个见到的值，避免多用户设备上显示成别的用户的时间。
 */
internal object PackageDetailParser {

    private const val NOT_FOUND_MARKER = "Unable to find package"
    private const val SYSTEM_FLAG = "SYSTEM"
    private const val UPDATED_SYSTEM_FLAG = "UPDATED_SYSTEM_APP"
    private const val USER_PREFIX = "User "
    private const val REQUESTED_PERMISSIONS = "requested permissions:"
    private const val RUNTIME_PERMISSIONS = "runtime permissions:"
    private const val PKG_FLAGS = "pkgFlags=["

    fun parse(output: String, packageName: String): PackageDetailParse {
        if (output.contains(NOT_FOUND_MARKER)) return PackageDetailParse.NotFound
        // 部分调用链会把输出按 CRLF 传回来：统一的 \n 之后空行与缩进判定才成立
        val lines = output.replace("\r\n", "\n").replace('\r', '\n').lines()
        val headerIndex = lines.indexOfFirst {
            it.trimStart().startsWith("Package [$packageName]")
        }
        if (headerIndex < 0) return PackageDetailParse.Unrecognized

        var versionName: String? = null
        var versionCode: Long? = null
        var versionCodeMajor: Long? = null
        var uid: String? = null
        var primaryCpuAbi: String? = null
        var minSdk: String? = null
        var targetSdk: String? = null
        var isSystem: Boolean? = null
        var installerPackageName: String? = null
        var initiatingPackageName: String? = null
        var codePath: String? = null
        var dataDir: String? = null
        var lastUpdateTime: String? = null
        var currentUser: Int? = null
        var firstInstallForUser0: String? = null
        var firstInstallAny: String? = null
        var apkSigningVersion: String? = null
        var requestedPermissionCount = 0
        var runtimePermissionCount = 0
        var requestedPermissionIndent = -1
        var runtimePermissionIndent = -1

        for (index in headerIndex + 1 until lines.size) {
            val raw = lines[index]
            if (raw.isEmpty()) continue
            // 顶层小节从第 0 列开始；下一个包段同样以 "Package [" 起头
            if (raw.first() != ' ') break
            val line = raw.trim()
            if (line.startsWith("Package [")) break

            // 权限条目只按缩进判定：小节标题下一级缩进的行就是一条权限，遇到同级或更外层
            // 的行（下一个字段、下一个小节、下一个 "User N:"）就结束计数
            val indent = raw.length - raw.trimStart().length
            if (requestedPermissionIndent >= 0) {
                if (indent > requestedPermissionIndent) requestedPermissionCount++
                else requestedPermissionIndent = -1
            }
            if (runtimePermissionIndent >= 0) {
                if (indent > runtimePermissionIndent) runtimePermissionCount++
                else runtimePermissionIndent = -1
            }

            when {
                line == REQUESTED_PERMISSIONS -> requestedPermissionIndent = indent
                // 运行时权限是逐用户的数据：只统计主用户（用户 0），工作资料等其它用户的小节跳过
                line == RUNTIME_PERMISSIONS ->
                    runtimePermissionIndent = if (currentUser == null || currentUser == 0) indent else -1
                line.startsWith(USER_PREFIX) ->
                    currentUser = line.removePrefix(USER_PREFIX).substringBefore(':').trim().toIntOrNull()

                line.startsWith("appId=") -> uid = line.valueAfter("appId=")
                line.startsWith("userId=") -> if (uid == null) uid = line.valueAfter("userId=")

                line.startsWith("versionCode=") -> {
                    for (token in line.split(' ')) {
                        when {
                            token.startsWith("versionCode=") ->
                                versionCode = token.removePrefix("versionCode=").toLongOrNull()

                            token.startsWith("minSdk=") -> minSdk = token.removePrefix("minSdk=").takeIf { it.isNotEmpty() }
                            token.startsWith("targetSdk=") -> targetSdk = token.removePrefix("targetSdk=").takeIf { it.isNotEmpty() }
                        }
                    }
                }

                line.startsWith("versionCodeMajor=") ->
                    versionCodeMajor = line.removePrefix("versionCodeMajor=").toLongOrNull()

                line.startsWith("versionName=") -> versionName = line.valueAfter("versionName=")
                line.startsWith("primaryCpuAbi=") -> primaryCpuAbi = line.valueAfter("primaryCpuAbi=")
                line.startsWith("codePath=") -> codePath = line.valueAfter("codePath=")
                line.startsWith("dataDir=") -> dataDir = line.valueAfter("dataDir=")
                line.startsWith("installerPackageName=") -> {
                    val value = line.valueAfter("installerPackageName=")
                    if (value != null && (installerPackageName == null || currentUser == 0)) {
                        installerPackageName = value
                    }
                }

                line.startsWith("initiatingPackageName=") -> {
                    val value = line.valueAfter("initiatingPackageName=")
                    if (value != null && (initiatingPackageName == null || currentUser == 0)) {
                        initiatingPackageName = value
                    }
                }

                line.startsWith("lastUpdateTime=") ->
                    if (lastUpdateTime == null) lastUpdateTime = line.valueAfter("lastUpdateTime=")

                line.startsWith("flags=[") || line.startsWith(PKG_FLAGS) -> {
                    // minSdk 26 前后的 ROM 分别用 flags / pkgFlags 记录同一组标记
                    val tokens = line.removePrefix(if (line.startsWith(PKG_FLAGS)) PKG_FLAGS else "flags=[")
                        .substringBefore(']').split(' ')
                    if (isSystem == null) {
                        isSystem = tokens.any { it == SYSTEM_FLAG || it == UPDATED_SYSTEM_FLAG }
                    }
                }

                line.startsWith("apkSigningVersion=") ->
                    apkSigningVersion = line.valueAfter("apkSigningVersion=")

                line.startsWith("firstInstallTime=") -> {
                    val value = line.valueAfter("firstInstallTime=")
                    if (firstInstallAny == null) firstInstallAny = value
                    if (currentUser == 0 && firstInstallForUser0 == null) firstInstallForUser0 = value
                }
            }
        }

        return PackageDetailParse.Parsed(
            PackageDetail(
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCodeText(versionCode, versionCodeMajor),
                uid = uid,
                primaryCpuAbi = primaryCpuAbi,
                minSdk = minSdk,
                targetSdk = targetSdk,
                isSystem = isSystem,
                firstInstallTime = firstInstallForUser0 ?: firstInstallAny,
                lastUpdateTime = lastUpdateTime,
                installerPackageName = installerPackageName,
                initiatingPackageName = initiatingPackageName,
                codePath = codePath,
                dataDir = dataDir,
                apkSigningVersion = apkSigningVersion,
                requestedPermissionCount = requestedPermissionCount,
                runtimePermissionCount = runtimePermissionCount,
            ),
        )
    }

    /** `versionCodeMajor` 非 0 时按 `major << 32 | code` 合成，与 PackageInfo 的 64 位版本号一致。 */
    private fun versionCodeText(code: Long?, major: Long?): String? {
        if (code == null) return null
        val majorValue = major ?: 0L
        return if (majorValue > 0L) (majorValue shl 32 or code).toString() else code.toString()
    }

    /** 取 `key=value` 的值；设备侧常见的 `=null` 与空串都归一成 null（界面统一显示「未知」）。 */
    private fun String.valueAfter(key: String): String? =
        removePrefix(key).trim().takeIf { it.isNotEmpty() && it != "null" }
}