package io.github.miuzarte.scrcpyforandroid.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import top.yukonga.miuix.kmp.utils.pressable

/**
 * 通宽文本动作行：整行可点，文字水平居中，无图标、无选中标记。
 *
 * 内距 26dp（横）/ 16dp（竖）：横向 26dp 是按压高亮的内缩量，文字由 `Arrangement.Center` 居中；
 * 竖向 16dp 让行高落在 56dp。
 *
 * 文字取 `textStyles.headline1`（17sp）+ Medium；禁用态取 `disabledOnSurface`。
 * 按压取主题的 `LocalIndication`（整行矩形变色），`delay = null` 让滚动中按下也立刻出反馈。
 */
@Composable
internal fun SheetActionRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                delay = null,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) {
                haptic.contextClick()
                onClick()
            }
            .padding(horizontal = 26.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = if (enabled) colorScheme.onSurface else colorScheme.disabledOnSurface,
        )
    }
}
