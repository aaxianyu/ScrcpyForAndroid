package io.github.miuzarte.scrcpyforandroid.appmanager.data

/**
 * 导出条目的文件名：`应用标签_包名.扩展名`。
 *
 * 标签让用户在下载目录里认得出是哪个应用，包名保证唯一：同名应用与同名标签都很常见。
 */
internal fun exportFileName(label: String, packageName: String, extension: String): String {
    val tail = "$packageName.$extension"
    val cleaned = cleanLabel(label)
    if (cleaned.isEmpty()) return fitPackage(packageName, extension)
    val name = "${cleaned}_$tail"
    if (name.utf8Size <= FILE_NAME_LIMIT_BYTES) return name
    // 整名超限时先保包名段：它决定这个文件还能不能被追回到哪个应用
    val labelBudget = FILE_NAME_LIMIT_BYTES - tail.utf8Size - 1
    val shortened = cleaned.limitUtf8Bytes(labelBudget).trimEnd('_', '.')
    return if (shortened.isEmpty()) fitPackage(packageName, extension) else "${shortened}_$tail"
}

/** 包名段自己就占满上限时保住扩展名：文件页面靠它认产物是什么。 */
private fun fitPackage(packageName: String, extension: String): String {
    val suffix = ".$extension"
    val budget = FILE_NAME_LIMIT_BYTES - suffix.utf8Size
    return if (budget <= 0) suffix else packageName.limitUtf8Bytes(budget) + suffix
}

/** 非法字符与空白折叠为单个下划线，再削掉首尾的下划线与点。 */
private fun cleanLabel(label: String): String {
    val collapsed = buildString {
        var pendingSeparator = false
        for (char in label) {
            if (char in ILLEGAL_FILE_NAME_CHARS || char.isISOControl() || char.isWhitespace()) {
                pendingSeparator = true
                continue
            }
            if (pendingSeparator && isNotEmpty()) append('_')
            pendingSeparator = false
            append(char)
        }
    }
    return collapsed.trim('_', '.').limitChars(LABEL_LIMIT).trimEnd('_', '.')
}

/** 按码元截断；截断点落在代理对中间时退一格，不切出半个字符。 */
private fun String.limitChars(limit: Int): String {
    if (length <= limit) return this
    val end = if (this[limit - 1].isHighSurrogate()) limit - 1 else limit
    return substring(0, end)
}

/** 按 UTF-8 字节截断：文件系统按字节算目录项上限，一个汉字占 3 字节。 */
private fun String.limitUtf8Bytes(limit: Int): String {
    var bytes = 0
    for ((index, char) in withIndex()) {
        val size = when {
            char.isHighSurrogate() -> 4
            char.isLowSurrogate() -> 0
            char.code < 0x80 -> 1
            char.code < 0x800 -> 2
            else -> 3
        }
        if (bytes + size > limit) return substring(0, index)
        bytes += size
    }
    return this
}

private val String.utf8Size: Int get() = toByteArray(Charsets.UTF_8).size

/** 标签段最多 40 个码元，再长就看不出重点了。 */
private const val LABEL_LIMIT = 40

/**
 * 整名上限 200 字节：ext4 与 F2FS 的目录项上限 255 字节，MediaStore 收不下 DISPLAY_NAME 时
 * 插入失败，Android 10 起的下载目录写入没有退路。
 */
private const val FILE_NAME_LIMIT_BYTES = 200

/**
 * 产物常经 MTP 拷到电脑，这些是 Windows 不接受的字符；`/` 是 POSIX 的路径分隔符，
 * `:` 还会撞上磁盘卷前缀。
 */
private const val ILLEGAL_FILE_NAME_CHARS = "/\\:*?\"<>|"
