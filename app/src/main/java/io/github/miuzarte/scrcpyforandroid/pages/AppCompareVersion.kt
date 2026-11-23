package io.github.miuzarte.scrcpyforandroid.pages

/**
 * 按数字段比较版本：`1.10` 高于 `1.9`；带预发布后缀的低于同段正式版。
 *
 * 后缀只取 `pre`/`rc`/`beta`/`alpha` 后面的数字参与比较，不区分后缀种类：
 * `beta2` 与 `rc1` 在同一档上比数字。versionName 里同时出现多种后缀的应用极少，
 * 这里只保证同一后缀序列内的先后关系。
 */
internal fun compareVersionNames(left: String, right: String): Int {
    fun parse(value: String): Pair<List<Int>, Int> {
        val cleaned = value.trim().removePrefix("v").removePrefix("V")
        val pre = Regex("(?i)(?:pre|rc|beta|alpha)(\\d*)$").find(cleaned)
        val main = if (pre != null) cleaned.substring(0, pre.range.first) else cleaned
        val parts = main.split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull() }
            .ifEmpty { listOf(0) }
        return parts to (if (pre == null) Int.MAX_VALUE else pre.groupValues[1].toIntOrNull() ?: 0)
    }
    val (leftParts, leftPre) = parse(left)
    val (rightParts, rightPre) = parse(right)
    for (i in 0 until maxOf(leftParts.size, rightParts.size)) {
        val diff = leftParts.getOrElse(i) { 0 }.compareTo(rightParts.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return leftPre.compareTo(rightPre)
}
