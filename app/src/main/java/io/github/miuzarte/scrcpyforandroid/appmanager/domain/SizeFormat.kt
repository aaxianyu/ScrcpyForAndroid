package io.github.miuzarte.scrcpyforandroid.appmanager.domain

import java.util.Locale
import kotlin.math.abs

/**
 * APK 大小的人类可读格式化：1024 进制、一位小数、千分位。
 *
 * <= 0 视为未知，由调用方决定不展示；本格式化器对 0 仍返回 `0 B`，
 * 以便「未知」与「确实是 0」在数据层可区分。
 */
object SizeFormat {

    private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

    fun humanReadable(bytes: Long): String {
        if (bytes == 0L) return "0 B"
        val negative = bytes < 0
        var value = abs(bytes).toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < UNITS.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        val text = if (unitIndex == 0) {
            // 字节不做小数：`512.0 B` 只会增加噪音
            String.format(Locale.US, "%,d", abs(bytes))
        } else {
            String.format(Locale.US, "%,.1f", value)
        }
        return buildString {
            if (negative) append('-')
            append(text)
            append(' ')
            append(UNITS[unitIndex])
        }
    }
}
