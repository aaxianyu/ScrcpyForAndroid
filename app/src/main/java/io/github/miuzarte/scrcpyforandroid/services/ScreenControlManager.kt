package io.github.miuzarte.scrcpyforandroid.services

import android.util.Log
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.security.MessageDigest

sealed class ScreenControlResult {
    data class Success(val message: String) : ScreenControlResult()
    data class Failure(val error: String, val stderr: String? = null) : ScreenControlResult()
    object NotConnected : ScreenControlResult()
    object DexMissing : ScreenControlResult()
}

object ScreenControlManager {
    private const val TAG = "ScreenControlManager"
    private const val REMOTE_DEX_PATH = "/data/local/tmp/screen_control.dex"
    private const val ASSET_DEX_PATH = "screen_control.dex"
    private const val DEX_SHA256 = "1eb5bf85aaa4a5bd120ea55ccd84e7cec99009b7084152f6af765cbb4d31e796"

    @Volatile
    private var deployed = false

    suspend fun ensureDeployed(): ScreenControlResult = withContext(Dispatchers.IO) {
        if (deployed) return@withContext ScreenControlResult.Success("already deployed")
        if (!NativeAdbService.isConnected()) return@withContext ScreenControlResult.NotConnected

        val ctx = AppRuntime.context
        val dexBytes = try {
            ctx.assets.open(ASSET_DEX_PATH).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "screen_control.dex not found in assets", e)
            return@withContext ScreenControlResult.DexMissing
        }

        val localHash = sha256Hex(dexBytes)
        if (localHash != DEX_SHA256) {
            Log.w(TAG, "dex hash mismatch: expected=$DEX_SHA256 actual=$localHash")
        }

        val remoteMatch = try {
            val out = NativeAdbService.shell("sha256sum $REMOTE_DEX_PATH 2>/dev/null").trim()
            val remoteHash = out.split("\\s+".toRegex()).firstOrNull()?.lowercase()
            remoteHash == DEX_SHA256
        } catch (_: Exception) { false }

        if (remoteMatch) {
            deployed = true
            return@withContext ScreenControlResult.Success("already deployed")
        }

        return@withContext try {
            val input = ByteArrayInputStream(dexBytes)
            NativeAdbService.push(input, REMOTE_DEX_PATH, 420)
            NativeAdbService.shell("chmod 755 $REMOTE_DEX_PATH").trim()
            deployed = true
            ScreenControlResult.Success("deployed")
        } catch (e: Exception) {
            ScreenControlResult.Failure(e.message ?: "push failed")
        }
    }

    suspend fun screenOff(): ScreenControlResult = exec("off")
    suspend fun screenOn(): ScreenControlResult = exec("on")

    private suspend fun exec(action: String): ScreenControlResult = withContext(Dispatchers.IO) {
        if (!NativeAdbService.isConnected()) return@withContext ScreenControlResult.NotConnected
        val dr = ensureDeployed()
        if (dr is ScreenControlResult.Failure || dr is ScreenControlResult.DexMissing) return@withContext dr

        return@withContext try {
            val abi = NativeAdbService.shell("getprop ro.product.cpu.abi").trim()
            val appProcess = when {
                abi.startsWith("arm64") || abi.startsWith("x86_64") -> "app_process64"
                else -> "app_process"
            }
            val cmd = "CLASSPATH=$REMOTE_DEX_PATH $appProcess / ScreenControl $action power; echo __EXIT__$?"
            val raw = NativeAdbService.shell(cmd)
            val marker = "__EXIT__"
            val exitIdx = raw.lastIndexOf(marker)
            val (stdout, exitCode) = if (exitIdx >= 0) {
                val code = raw.substring(exitIdx + marker.length).trim().toIntOrNull() ?: -1
                raw.substring(0, exitIdx).trim() to code
            } else {
                raw.trim() to 0
            }
            when (exitCode) {
                0 -> ScreenControlResult.Success(stdout)
                else -> ScreenControlResult.Failure("exit=$exitCode", stderr = stdout)
            }
        } catch (e: Exception) {
            ScreenControlResult.Failure(e.message ?: "unknown error")
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
