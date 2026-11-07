package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 行尾圆形操作钮的底色透明度：浅到不抢行文本，又足够和卡片区分 */
private const val ROW_ACTION_CHIP_ALPHA = 0.12f

/**
 * 列表行尾的圆形操作钮。
 *
 * 底色取 [MiuixTheme.colorScheme.primary] 的 12%，图标同色。正圆依赖
 * `IconButtonDefaults` 的 40dp 最小尺寸与 40dp 圆角（二者相等时才呈正圆）。
 * 静态配色与 Monet 动态取色下都是同色系浅底 + 同色图标，
 * 命令书签弹层与局域网扫描弹层因此共用同一套观感。
 *
 * 已知取舍：图标对自身底色的对比度约 2.2–2.8:1，低于非文本元素 3:1 的建议值，
 * 这是为了「浅蓝底 + 同色图标」的观感刻意选的，不是漏改。
 *
 * @param imageVector 要显示的图标
 * @param contentDescription 无障碍描述，须本地化
 * @param onClick 点击回调
 * @param modifier 外部样式
 */
@Composable
internal fun RowActionButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptic.contextClick()
            onClick()
        },
        modifier = modifier,
        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = ROW_ACTION_CHIP_ALPHA),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}