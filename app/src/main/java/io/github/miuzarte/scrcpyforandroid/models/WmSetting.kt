package io.github.miuzarte.scrcpyforandroid.models

/** 被控机 `wm size` / `wm density` 的当前状态；`override` 为 null 表示没有覆盖值。 */
internal data class WmSetting(val physical: String, val override: String?)

private fun normalizeWmValue(value: String, field: String): String? {
    val parts = value.split('x')
    if (parts.size != if (field == "size") 2 else 1) return null
    return parts.map { part ->
        if (!part.matches(Regex("[0-9]+"))) return null
        part.toIntOrNull()?.takeIf { it > 0 } ?: return null
    }.joinToString("x")
}

/** 解析 `wm size` / `wm density` 的输出；字段缺失或取值不合法时返回 null。 */
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
