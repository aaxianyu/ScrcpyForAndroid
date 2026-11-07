package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.component.liquid.lens
import io.github.miuzarte.scrcpyforandroid.ui.component.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import androidx.compose.ui.util.lerp

/** 圆形遮罩直径 36dp（触摸区 40dp）；亮 / 暗色遮罩色是灰度近似值。 */
private val MaskLightColor = Color(0x80FFFFFF)
private val MaskDarkColor = Color(0x802C2C2C)
private val MaskSize = 36.dp
private val TouchSize = 40.dp

/**
 * 顶栏圆形图标按钮 + 玻璃 / 高斯遮罩。
 *
 * - 遮罩材质跟随**模糊模式**（调用方传入的 [glassActive] 已按模式计算）：
 *   渐进 + 液态玻璃 → 液态玻璃；高斯 → 25dp 高斯采样；无模糊 → 不加遮罩；
 * - 按压反馈跟随遮罩材质：有 backdrop（模糊 / 液态玻璃）时用玻璃按压
 *   （光泽 + 胀放 + 透镜增厚），与底部悬浮大搜索钮一致；
 *   无模糊时不挂玻璃按压，退回组件库按压指示。
 * - 遮罩透明度 = max(滚动淡入进度, 按压进度)：没滚动时按下也能看到玻璃；
 * - 胀放幅度按小尺寸收窄（0.10，与底部 64dp 钮的 6dp 增量同比例），
 *   放大后仍在外层 40dp 圆内，不会把一圈高光裁掉。
 *
 * [maskAlpha] 由调用方在组合外监听滚动状态驱动（在组合期直接读会导致整屏每帧重组）。
 */
@Composable
fun MaskedTopBarAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    maskAlpha: () -> Float,
    glassActive: Boolean,
    backdrop: LayerBackdrop?,
    highlight: State<Highlight>,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    anchorModifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    // 玻璃按压只在有 blur 材质时挂：模糊关掉后 backdrop 为 null，玻璃那层白光与胀放
    // 没有材质可依附，留着就变成与其它按钮不一致的「还带模糊感」的按压。
    // API < 33 时内部返回 null。禁用项不挂——否则按压高光仍在，与
    // 「按不动的按钮不该有反馈」相矛盾
    val glassPress = backdrop != null
    val pressState = rememberGlassPressState(enabled = enabled && glassPress)
    // 兜底按键的是 pressState 而不是 glassPress：API < 33 时 backdrop 可能非空、玻璃态却拿不到，
    // 只按 backdrop 判断会让这些机器一点按压反馈都没有
    val pressIndication = if (pressState != null) null else LocalIndication.current
    val isDark = colorScheme.background.luminance() < 0.5f
    val maskColor = if (isDark) MaskDarkColor else MaskLightColor
    val glassContainerColor = colorScheme.surfaceContainer.copy(
        alpha = AppManagerMotion.GLASS_CONTAINER_ALPHA,
    )
    Box(
        modifier = modifier
            .size(TouchSize)
            .then(anchorModifier)
            // 先裁圆再挂按压层：玻璃材质与白色高光都只能是圆形，不会透出圆钮外
            // （部件顺序：clip 在 pressModifier 之外，高光的整节点矩形才会被裁掉）
            .clip(CircleShape)
            .then(if (pressState != null) pressState.modifier else Modifier)
            .then(if (pressState != null) pressState.gestureModifier else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = pressIndication,
                enabled = enabled,
            ) {
                haptic.contextClick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
    val pressProgress = pressState?.pressProgress?.value ?: 0f
    Box(
        modifier = Modifier
            .size(MaskSize)
            .graphicsLayer {
                // 禁用态整块（含圆形遮罩）一起压暗：只暗图标的话遮罩仍是满强度的，
                // 看起来仍像可点。取值与批量动作项的 disabledIconAlpha 一致
                alpha = maxOf(maskAlpha(), pressProgress) * if (enabled) 1f else DISABLED_ALPHA
                // 缩放幅度与底部 64dp 圆钮同比例（6dp/64dp ≈ 0.10）：放大后不超出
                // 外层 40dp 触摸圆的裁剪，高光圈不会被切掉
                val scale = 1f + AppManagerMotion.PRESS_SCALE_TOP_BAR_BUTTON * pressProgress
                scaleX = scale
                scaleY = scale
            }
                .then(
                    if (glassActive && backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                padding = maxOf(padding, AppManagerMotion.GLASS_SAMPLE_PADDING_DP.dp.toPx())
                                vibrancy()
                                blur(
                                    AppManagerMotion.GLASS_BLUR_DP.dp.toPx(),
                                    AppManagerMotion.GLASS_BLUR_DP.dp.toPx(),
                                )
                                val amount = lerp(0.4f, 1f, pressProgress)
                                lens(
                                    refractionHeight = AppManagerMotion.GLASS_LENS_DP.dp.toPx() * amount,
                                    refractionAmount = AppManagerMotion.GLASS_LENS_DP.dp.toPx() * amount,
                                )
                            },
                            highlight = { highlight.value.copy(alpha = 0.75f) },
                            onDrawSurface = {
                                drawRect(glassContainerColor)
                            },
                        )
                    } else if (backdrop != null) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = CircleShape,
                            blurRadius = AppManagerMotion.GAUSSIAN_FALLBACK_BLUR,
                            colors = BlurDefaults.blurColors(
                                blendColors = listOf(BlendColorEntry(color = maskColor)),
                            ),
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colorScheme.onSurface.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
        )
    }
}

/** 禁用态不透明度：与批量动作项的 [BottomBars] 禁用图标取值一致，两处同为「不可点的动作」。 */
private const val DISABLED_ALPHA = 0.3f
