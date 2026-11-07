package io.github.miuzarte.scrcpyforandroid.appmanager.data

import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * 探测输出的解析。
 *
 * shell 输出可能混有非 JSON 告警（linker 警告等），先定位 JSON 本体再解析；
 * 能区分「探测错误」（返回 error 字段）并取回错误信息。
 */
object ProbePayloadParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed interface Result {
        data class Success(
            val apps: List<DeviceApp>,
            val iconBytes: Map<String, ByteArray>,
            /** 图标实际编码：`webp` / `png`，由探测端报回，落盘后缀按它定 */
            val iconSuffix: String,
            /** 探测端回传的首个图标失败原因（空串 = 全部成功） */
            val iconError: String = "",
        ) : Result
        data class Failure(val reason: String) : Result
    }

    fun parse(rawOutput: String): Result {
        val body = locateJsonBody(rawOutput)
            ?: return Result.Failure(describeMissingBody(rawOutput))
        val payload = runCatching { json.decodeFromString<ProbePayload>(body) }.getOrElse { error ->
            // 探测程序主动上报的错误（{"error": "..."}）。必须在错误体解析前排除截断：
            // 宽松解析会把半截清单也读成一个对象，那条路径会把原始 JSON 带进界面。
            val reported = if (isTruncated(error)) {
                null
            } else {
                runCatching { json.decodeFromString<ProbeError>(body).error }.getOrNull()
            }
            return Result.Failure(reported ?: describeParseFailure(error, body))
        }
        if (payload.apps.isEmpty()) {
            // 条目为空视为失败；错误体优先，带回错误信息。
            // 截断也可能落到这里（断点在某对象闭合之后、数组未闭合时宽松解析不抛错），
            // 与上面那条分支是同一类输入，必须同样不给原文、只报截断。
            val reported = runCatching {
                json.decodeFromString<ProbeError>(body).error
            }.getOrNull()
            return Result.Failure(
                reported?.takeIf { it.isNotBlank() } ?: describeEmptyApps(body),
            )
        }
        val iconBytes = payload.apps.mapNotNull { app ->
            val encoded = app.iconBase64 ?: return@mapNotNull null
            val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
                ?: return@mapNotNull null
            app.pkg to bytes
        }.toMap()
        return Result.Success(
            apps = payload.apps.map { it.toDeviceApp() },
            iconBytes = iconBytes,
            iconSuffix = IconFiles.suffixFor(payload.iconFmt),
            iconError = payload.iconErr,
        )
    }

    /** 从首个 `{` 到末个 `}` 截取 JSON 本体。 */
    internal fun locateJsonBody(rawOutput: String): String? {
        val start = rawOutput.indexOf('{')
        val end = rawOutput.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return rawOutput.substring(start, end + 1)
    }

    /**
     * 找不到 JSON 本体时的失败原因。
     *
     * 有 `{` 但没有闭合 `}` = 报文被截断，不能把半截原文当原因显示（图标 base64 一片
     * 上百字符，界面会变成乱码）。完全不含 `{` 才是探测程序的普通输出（如 `Killed`），
     * 那种情况取首行最有信息量。
     */
    private fun describeMissingBody(rawOutput: String): String {
        if (rawOutput.contains('{')) {
            return truncatedReason(rawOutput.length)
        }
        return rawOutput.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }
            ?: "empty probe output"
    }

    /**
     * 解析失败原因。
     *
     * 解析器会把它收到的 JSON 原文片段拼进消息里（图标是 base64，一片就有上百字符），
     * 原样透传会让界面出现大段乱码。这里只保留结论：截断的报文单独给出可读原因。
     */
    private fun describeParseFailure(error: Throwable, body: String): String {
        val message = error.message.orEmpty()
        if (isTruncated(error)) {
            return truncatedReason(body.length)
        }
        return message.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?: "probe parse failed"
    }

    /** 截断的特征：解析器报 "Expected end of ... but had 'EOF'"。 */
    private fun isTruncated(error: Throwable): Boolean =
        error.message.orEmpty().contains("EOF", ignoreCase = true)

    /**
     * 解析成功但清单为空时的失败原因。
     *
     * 断点落在某个 app 对象闭合之后、数组未闭合时，宽松解析不抛错而是得到一个空清单——
     * 这种输入仍是截断。此时没有异常可看，改用括号配平判定：`locateJsonBody` 只保证
     * body 以 `}` 收尾，截断的报文会缺配对的 `{`。
     */
    private fun describeEmptyApps(body: String): String {
        var depth = 0
        for (ch in body) {
            when (ch) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth < 0) break
        }
        return if (depth != 0) truncatedReason(body.length) else "probe returned no apps"
    }

    /**
     * 截断原因的唯一产地。
     *
     * 三处判定都走这里，格式只在这一处定义；[truncatedChars] 是它的反解，
     * UI 层靠后者取字符数做本地化。
     */
    private fun truncatedReason(chars: Int): String = "$TRUNCATED_PREFIX$chars$TRUNCATED_SUFFIX"

    /**
     * 从截断诊断串里取回已收到的字符数；不是截断串则返回 null。
     *
     * 供 UI 层做本地化：解析层只产纯数据串，文案由 UI 决定。
     */
    fun truncatedChars(reason: String): Int? {
        if (!reason.startsWith(TRUNCATED_PREFIX) || !reason.endsWith(TRUNCATED_SUFFIX)) return null
        val digits = reason.substring(
            TRUNCATED_PREFIX.length,
            reason.length - TRUNCATED_SUFFIX.length,
        )
        return digits.toIntOrNull()
    }

    private const val TRUNCATED_PREFIX = "probe output truncated ("
    private const val TRUNCATED_SUFFIX = " chars received)"

    private fun ProbeApp.toDeviceApp(): DeviceApp = DeviceApp(
        packageName = pkg,
        label = label,
        versionName = ver,
        apkSizeBytes = size,
        isSystem = sys,
        isEnabled = enabled,
        splitCount = split,
    )
}

@kotlinx.serialization.Serializable
private data class ProbeError(val error: String = "")
