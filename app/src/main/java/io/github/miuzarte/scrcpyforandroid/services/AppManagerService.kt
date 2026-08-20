package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.utils.AppSortUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import org.json.JSONObject

data class RemoteAppInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val apkPath: String,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val sizeBytes: Long,
    val iconBase64: String? = null,
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)

    companion object {
        private val sizeFormat = DecimalFormat("#,##0.#")

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var value = bytes.toDouble()
            var unitIndex = 0
            while (value >= 1024 && unitIndex < units.lastIndex) {
                value /= 1024
                unitIndex++
            }
            return "${sizeFormat.format(value)} ${units[unitIndex]}"
        }
    }
}

data class AllAppsResult(
    val userApps: List<RemoteAppInfo>,
    val systemApps: List<RemoteAppInfo>,
)

object AppManagerService {
    private const val TAG = "AppManagerService"
    private const val MAX_RETRIES = 3
    private const val HELPER_JAR = "bin/device_apps_helper.jar"
    private const val HELPER_REMOTE = "/data/local/tmp/device_apps_helper.jar"
    private const val HELPER_CLASS = "DeviceAppsHelper"

    @Volatile
    var scrcpy: Scrcpy? = null

    @Volatile
    var appContext: Context? = null

    suspend fun fetchDisabledPackages(): List<String> = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext emptyList()
        val output = runCatching {
            NativeAdbService.shell("pm list packages -d 2>/dev/null")
        }.getOrDefault("")
        parsePackageNames(output)
    }

    private suspend fun ensureConnected(): Boolean {
        val connected = runCatching {
            if (NativeAdbService.isConnected()) {
                NativeAdbService.ensureConnectionResponsive()
            }
            true
        }.onFailure {
            Log.w(TAG, "ensureConnected: connection check failed: ${it.message}")
        }.getOrDefault(false)

        if (connected) return true

        val target = AppRuntime.currentConnectionTarget
        if (target == null) {
            Log.e(TAG, "ensureConnected: no connection target available")
            return false
        }

        repeat(MAX_RETRIES) { attempt ->
            Log.d(TAG, "ensureConnected: attempt ${attempt + 1}, connecting to ${target.host}:${target.port}")
            runCatching {
                NativeAdbService.connect(target.host, target.port)
            }.onFailure { e ->
                Log.w(TAG, "ensureConnected: connect attempt ${attempt + 1} failed: ${e.message}")
                return@repeat
            }

            runCatching {
                NativeAdbService.ensureConnectionResponsive()
            }.onSuccess {
                Log.d(TAG, "ensureConnected: success on attempt ${attempt + 1}")
                return true
            }.onFailure { e ->
                Log.w(TAG, "ensureConnected: verify attempt ${attempt + 1} failed: ${e.message}")
            }
        }

        Log.e(TAG, "ensureConnected: failed after $MAX_RETRIES attempts")
        return false
    }

    suspend fun loadAllApps(fetchIcons: Boolean = true): AllAppsResult = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext AllAppsResult(emptyList(), emptyList())

        val s = scrcpy
        if (s == null) {
            Log.w(TAG, "loadAllApps: scrcpy not initialized")
            return@withContext AllAppsResult(emptyList(), emptyList())
        }

        val disabledAllOutput = runCatching {
            NativeAdbService.shell("pm list packages -d 2>/dev/null")
        }.getOrNull() ?: return@withContext AllAppsResult(emptyList(), emptyList())
        val disabledAll = parsePackageNames(disabledAllOutput).toSet()
        Log.d(TAG, "loadAllApps: ${disabledAll.size} disabled")

        val disabledUserOutput = runCatching {
            NativeAdbService.shell("pm list packages -d -3 2>/dev/null")
        }.getOrNull() ?: ""
        val disabledUser = parsePackageNames(disabledUserOutput).toSet()

        val appList = runCatching {
            s.listings.getApps(forceRefresh = true)
        }.getOrNull()

        if (appList.isNullOrEmpty()) {
            Log.w(TAG, "loadAllApps: scrcpy returned no apps")
            return@withContext AllAppsResult(emptyList(), emptyList())
        }

        Log.d(TAG, "loadAllApps: scrcpy returned ${appList.size} apps")

        val scrcpyPkgSet = appList.map { it.packageName }.toSet()
        val hiddenDisabled = disabledAll - scrcpyPkgSet

        val allPkgs = scrcpyPkgSet + hiddenDisabled
        val allHelperResults = if (allPkgs.isNotEmpty()) {
            fetchLabelsViaHelper(allPkgs, fetchIcons = fetchIcons)
        } else emptyMap()
        Log.d(TAG, "loadAllApps: ${hiddenDisabled.size} hidden disabled apps, fetchIcons=$fetchIcons")

        val userApps = mutableListOf<RemoteAppInfo>()
        val systemApps = mutableListOf<RemoteAppInfo>()

        for (app in appList) {
            val pkg = app.packageName
            val hr = allHelperResults[pkg]
            val info = RemoteAppInfo(
                packageName = pkg,
                label = app.label ?: pkg.substringAfterLast("."),
                versionName = hr?.versionName ?: "",
                apkPath = "",
                isSystem = app.system == true,
                isEnabled = !disabledAll.contains(pkg),
                sizeBytes = hr?.sizeBytes ?: 0L,
                iconBase64 = hr?.iconBase64,
            )
            if (app.system == true) systemApps.add(info) else userApps.add(info)
        }

        for (pkg in hiddenDisabled) {
            val hr = allHelperResults[pkg]
            val label = hr?.label ?: pkg.substringAfterLast(".")
            val isSystem = pkg !in disabledUser
            val info = RemoteAppInfo(
                packageName = pkg,
                label = label,
                versionName = hr?.versionName ?: "",
                apkPath = "",
                isSystem = isSystem,
                isEnabled = false,
                sizeBytes = hr?.sizeBytes ?: 0L,
                iconBase64 = hr?.iconBase64,
            )
            if (isSystem) systemApps.add(info) else userApps.add(info)
        }

AllAppsResult(
userApps.sortedBy { AppSortUtils.sortKey(it.label, it.packageName) },
systemApps.sortedBy { AppSortUtils.sortKey(it.label, it.packageName) }
)
    }

    data class HelperResult(val label: String, val iconBase64: String?, val versionName: String?, val sizeBytes: Long)

    suspend fun fetchLabelsViaHelper(packages: Set<String>, fetchIcons: Boolean = true): Map<String, HelperResult> {
        if (packages.isEmpty()) return emptyMap()
        val ctx = appContext ?: run {
            Log.w(TAG, "fetchLabelsViaHelper: appContext not set")
            return emptyMap()
        }
        val result = mutableMapOf<String, HelperResult>()

        try {
            val cacheFile = java.io.File(ctx.cacheDir, HELPER_JAR.substringAfterLast("/"))
            ctx.assets.open(HELPER_JAR).use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }

            NativeAdbService.push(cacheFile.toPath(), HELPER_REMOTE)

            val pkgsArg = packages.joinToString(" ")
            val flagsArg = if (!fetchIcons) "--no-icons" else ""
            val output = NativeAdbService.shell(
                "CLASSPATH=$HELPER_REMOTE app_process / $HELPER_CLASS $flagsArg $pkgsArg 2>&1"
            )

            NativeAdbService.shell("rm -f $HELPER_REMOTE")

            val json = JSONObject(output)
            if (json.has("e")) {
                Log.w(TAG, "fetchLabelsViaHelper: device error: ${json.getString("e")}")
                return result
            }

            val apps = json.getJSONArray("apps")
            for (i in 0 until apps.length()) {
                val app = apps.getJSONObject(i)
                val versionName = if (app.has("v")) app.getString("v") else null
                val sizeBytes = if (app.has("s")) app.getLong("s") else 0L
                result[app.getString("p")] = HelperResult(
                    label = app.getString("l"),
                    iconBase64 = if (app.has("i")) app.getString("i") else null,
                    versionName = versionName,
                    sizeBytes = sizeBytes,
                )
            }

            Log.d(TAG, "fetchLabelsViaHelper: got ${result.size}/${packages.size} disabled labels")
        } catch (e: Exception) {
            Log.e(TAG, "fetchLabelsViaHelper: failed: ${e.message}", e)
        }
        return result
    }

    private fun parsePackageNames(output: String): List<String> {
        return output.lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it.contains(".") }
            .toList()
    }

    suspend fun installApk(remotePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Log.i(TAG, "installApk: starting installation for $remotePath")
            if (!ensureConnected()) throw Exception("ADB not connected")
            NativeAdbService.ensureConnectionResponsive()

            val installCommands = listOf(
                "pm install -r -t --user 0 '$remotePath'",
                "pm install -r --user 0 '$remotePath'",
                "pm install -r '$remotePath'"
            )

            var lastError: String? = null
            for (command in installCommands) {
                try {
                    Log.d(TAG, "Trying install command: $command")
                    val output = NativeAdbService.shell(command)
                    if (output.contains("Success")) {
                        Log.i(TAG, "installApk: installation successful")
                        NativeAdbService.shell("rm -f '$remotePath'")
                        return@runCatching "Success"
                    } else {
                        lastError = output.trim()
                        Log.w(TAG, "Install failed with $command: $lastError")
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Unknown error"
                    Log.w(TAG, "Install failed with $command: $lastError", e)
                }
            }

            try {
                NativeAdbService.shell("rm -f '$remotePath'")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean up temporary file", e)
            }

            throw Exception(lastError ?: "Installation failed")
        }
    }

    suspend fun uninstall(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ensureConnected()) throw Exception("ADB not connected")
            NativeAdbService.ensureConnectionResponsive()
            val output = NativeAdbService.shell("pm uninstall $packageName")
            if (output.contains("Success")) "Success" else throw Exception(output.trim())
        }
    }

    suspend fun disable(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ensureConnected()) throw Exception("ADB not connected")
            NativeAdbService.ensureConnectionResponsive()
            val output = NativeAdbService.shell("pm disable-user $packageName")
            if (output.contains("Package $packageName new state: disabled-user") ||
                output.contains("disabled")
            ) "disabled" else throw Exception(output.trim())
        }
    }

    suspend fun enable(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ensureConnected()) throw Exception("ADB not connected")
            NativeAdbService.ensureConnectionResponsive()
            val output = NativeAdbService.shell("pm enable $packageName")
            if (output.contains("Package $packageName new state: enabled") ||
                output.contains("enabled")
            ) "enabled" else throw Exception(output.trim())
        }
    }

    suspend fun clearData(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ensureConnected()) throw Exception("ADB not connected")
            NativeAdbService.ensureConnectionResponsive()
            val output = NativeAdbService.shell("pm clear $packageName")
            if (output.contains("Success")) "Success" else throw Exception(output.trim())
        }
    }

    suspend fun forceStop(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ensureConnected()) throw Exception("ADB not connected")
            NativeAdbService.ensureConnectionResponsive()
            NativeAdbService.shell("am force-stop $packageName")
            "Success"
        }
    }

    suspend fun exportApk(packageName: String, apkPath: String, uri: Uri? = null): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!ensureConnected()) throw Exception("ADB not connected")
                NativeAdbService.ensureConnectionResponsive()

                val remoteBaseApk = if (apkPath.isNotBlank()) {
                    if (apkPath.endsWith(".apk")) apkPath else "$apkPath/base.apk"
                } else {
                    val pathOutput = NativeAdbService.shell("pm path $packageName")
                    Log.d(TAG, "exportApk: pm path output: $pathOutput")
                    val apkLine = pathOutput.lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("package:") }
                        ?: throw Exception("无法获取APK路径")
                    apkLine.removePrefix("package:").trim()
                }

                Log.d(TAG, "exportApk: remote APK path: $remoteBaseApk")

                if (uri != null) {
                    val resolver = appContext?.contentResolver ?: throw Exception("context not ready")
                    resolver.openOutputStream(uri)?.use { fos ->
                        NativeAdbService.pull(remoteBaseApk, fos)
                    } ?: throw Exception("无法打开保存位置")
                    uri.toString()
                } else {
                    val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    )
                    val localFile = java.io.File(downloadDir, "${packageName}.apk")
                    localFile.outputStream().use { fos ->
                        NativeAdbService.pull(remoteBaseApk, fos)
                    }
                    localFile.absolutePath
                }
            }
        }
}
