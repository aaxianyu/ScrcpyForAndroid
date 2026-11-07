package io.github.miuzarte.scrcpyforandroid.pages

import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal class AdbPortReconnectFailure(cause: Throwable) : Exception(cause.message, cause)

internal suspend fun reconnectAdbPort(
    host: String,
    port: Int,
    waitForRestart: suspend () -> Unit = { delay(2000) },
    connect: suspend (String, Int) -> Unit,
) {
    repeat(3) { attempt ->
        waitForRestart()
        try {
            connect(host, port)
            return
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            if (attempt == 2) throw error
        }
    }
}

internal fun DeviceShortcuts.withAdbPort(target: ConnectionTarget, port: Int): DeviceShortcuts =
    DeviceShortcuts(map { device ->
        device.copy(addresses = device.addresses.map { address ->
            if (ConnectionTarget.unmarshalFrom(address) == target) target.copy(port = port).toString()
            else address
        }.distinct())
    })

internal data class WmSetting(val physical: String, val override: String?)

internal fun normalizeToolValue(input: String, range: IntRange): String? =
    input.toIntOrNull()?.takeIf { it in range }?.toString()

private fun normalizeWmValue(value: String, field: String): String? {
    val parts = value.split('x')
    if (parts.size != if (field == "size") 2 else 1) return null
    return parts.map { part ->
        if (!part.matches(Regex("[0-9]+"))) return null
        part.toIntOrNull()?.takeIf { it > 0 } ?: return null
    }.joinToString("x")
}

internal fun parseWmSetting(output: String, field: String): WmSetting? {
    val lines = output.trim().lines()
    if (lines.size !in 1..2) return null
    val physicalPrefix = "Physical $field: "
    val overridePrefix = "Override $field: "
    if (!lines[0].startsWith(physicalPrefix)) return null
    val physical = normalizeWmValue(lines[0].removePrefix(physicalPrefix), field) ?: return null
    val override = if (lines.size == 2) {
        if (!lines[1].startsWith(overridePrefix)) return null
        normalizeWmValue(lines[1].removePrefix(overridePrefix), field) ?: return null
    } else null
    return WmSetting(physical, override)
}

internal fun isWmRestored(output: String, field: String): Boolean? =
    parseWmSetting(output, field)?.let { it.override == null }

internal fun isTcpipSuccess(reply: String, port: Int): Boolean =
    reply.trim() == "restarting in TCP mode port: $port"

internal data class StartupAttempt(val exitCode: Int?, val output: String)

// shell v1 无协议级退出码，只能靠约定标记回传；解析取最后一次出现，并要求其后整段就是退出码。
// 已知例外：脚本输出恰好以同形标记行结尾、且 printf 未执行时，二者无法区分。
private const val STARTUP_EXIT_MARKER = "__SCRCPY_STARTUP_EXIT__:"

internal fun startupCommand(script: String): String =
    "( $script ); result=\$?; printf '\\n$STARTUP_EXIT_MARKER%s\\n' \"\$result\""

internal fun parseStartupAttempt(reply: String): StartupAttempt {
    val text = reply.trimEnd()
    // printf 会在标记行前补 \n，但 trim 后整条回复可能只剩标记行本身
    val atLineStart = text.startsWith(STARTUP_EXIT_MARKER)
    val marker = text.lastIndexOf("\n$STARTUP_EXIT_MARKER")
    if (!atLineStart && marker < 0) return StartupAttempt(null, text.trim())
    val lineStart = if (atLineStart) 0 else marker + 1
    val code = text.substring(lineStart + STARTUP_EXIT_MARKER.length)
        .toIntOrNull()?.takeIf { it in 0..255 }
        ?: return StartupAttempt(null, text.trim())
    return StartupAttempt(code, if (lineStart == 0) "" else text.substring(0, marker).trim())
}

internal suspend fun tryStartupScripts(
    scripts: List<String>,
    shell: suspend (String) -> String,
): StartupAttempt {
    var last = StartupAttempt(null, "")
    for (script in scripts) {
        last = parseStartupAttempt(shell(startupCommand(script)))
        // shell v1 没有退出码；缺少尾标记时状态未知，不重复启动另一个入口。
        if (last.exitCode == null || last.exitCode == 0) break
    }
    return last
}
