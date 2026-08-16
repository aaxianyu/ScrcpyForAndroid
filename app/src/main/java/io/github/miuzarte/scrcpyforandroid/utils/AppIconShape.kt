package io.github.miuzarte.scrcpyforandroid.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp

/**
 * 应用图标圆角矩形遮罩，圆角 = 图标尺寸的 20%（MIUI 风格比例）。
 */
fun Modifier.appIconRounded(iconSize: Dp): Modifier = clip(RoundedCornerShape(iconSize * 0.2f))
