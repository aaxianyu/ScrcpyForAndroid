package io.github.miuzarte.scrcpyforandroid.utils

import com.github.promeg.pinyinhelper.Pinyin
import java.util.Locale

object AppSortUtils {

    private data class SortToken(
        val priority: Int,
        val value: String,
    )

    fun sortKey(label: String, packageName: String = ""): String {
        val safeLabel = label.takeIf { it.isNotBlank() } ?: packageName
        val tokens = safeLabel.map { char ->
            when {
                char.code <= 0x7F -> SortToken(
                    priority = 0,
                    value = char.lowercaseChar().toString(),
                )
                Pinyin.isChinese(char) -> SortToken(
                    priority = 1,
                    value = Pinyin.toPinyin(char).lowercase(Locale.ROOT),
                )
                else -> SortToken(
                    priority = 2,
                    value = char.lowercaseChar().toString(),
                )
            }
        }
        val firstToken = tokens.firstOrNull { it.value.any(Char::isLetterOrDigit) }
            ?: tokens.firstOrNull()
        val firstLetter = firstToken
            ?.value
            ?.firstOrNull(Char::isLetterOrDigit)
            ?: Char.MAX_VALUE

        return buildString {
            append(firstLetter)
            append('\u0000')
            append(firstToken?.priority ?: 2)
            append('\u0000')
            tokens.forEach { token ->
                append(token.value)
                append('\u0000')
            }
            append('\u0001')
            append(packageName.lowercase(Locale.ROOT))
        }
    }
}
