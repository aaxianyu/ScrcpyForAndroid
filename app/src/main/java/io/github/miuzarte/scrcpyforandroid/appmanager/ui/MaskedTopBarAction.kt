package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
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

/** 投影半径与悬浮底栏同值，不透明度按本处口径（底栏另有取值）；暗色主题里黑色投影不显形，不透明度加倍补偿。 */
private val MaskShadowRadius = 10.dp
private const val MaskShadowAlphaLight = 0.12f
private const val MaskShadowAlphaDark = 0.24f

/**
 * 顶栏圆形图标按钮 + 玻璃 / 高斯遮罩。
 *
 * - 遮罩材质跟随**模糊模式**（调用方传入的 [glassActive] 已按模式计算）：
 *   渐进 + 液态玻璃 → 液态玻璃；高斯 → 25dp 高斯采样；无模糊 → 不加遮罩；
 * - 按压反馈跟随遮罩材质：有 backdrop（模糊 / 液态玻璃）时用玻璃按压
 *   （光泽 + 胀放 + 透镜增厚），与底部悬浮大搜索钮一致；
 *   无模糊时不挂玻璃按压，退回组件库按压指示。
 * - 遮罩透明度 = max(滚动淡入进度, 按压进度)：没滚动时按下也能看到玻璃；
 * - 有遮罩材质时圆下另挂一层投影：浅色主题里玻璃压着的仍是模糊过的浅色内容，
 *   容器色与它亮度接近，圆边只能靠这圈投影分辨；
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
            .then(anchorModifier),
        contentAlignment = Alignment.Center,
    ) {
        // 遮罩与投影共用同一淡出进度，且都在绘制阶段读取：组合期读会在 350ms 里逐帧重组
        val visibility: () -> Float = {
            maxOf(maskAlpha(), pressState?.pressProgress?.value ?: 0f) *
                if (enabled) 1f else DISABLED_ALPHA
        }
        // 遮罩与投影必须同一胀放系数：不同步时遮罩会放大到超出投影轮廓，圆边露出错位。
        // 幅度与底部 64dp 圆钮同比例（6dp/64dp ≈ 0.10）
        val pressScale: () -> Float = {
            1f + AppManagerMotion.PRESS_SCALE_TOP_BAR_BUTTON *
                (pressState?.pressProgress?.value ?: 0f)
        }
        // 投影单独一层、画在裁圆之外：玻璃材质与白色高光被裁在 40dp 圆内，投影挂进同一
        // 节点会连外沿一起裁掉。透明度层按自身边界裁剪，这一层的边界因此必须盖住投影的
        // 扩散范围；模糊材质不挂透明度节点，否则采样区会按矩形轮廓在过渡中透出
        if (backdrop != null) {
            // 这一层同样按自身边界裁剪，必须真实测到投影的扩散范围：.size 会把超出的
            // 尺寸收回父约束的 40dp，所以用 requiredSize
            Box(
                modifier = Modifier
                    .requiredSize(TouchSize + MaskShadowRadius * 4)
                    .graphicsLayer { alpha = visibility() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(MaskSize)
                        .graphicsLayer {
                            val scale = pressScale()
                            scaleX = scale
                            scaleY = scale
                        }
                        .dropShadow(
                            shape = CircleShape,
                            shadow = Shadow(
                                radius = MaskShadowRadius,
                                color = Color.Black,
                                alpha = if (isDark) MaskShadowAlphaDark else MaskShadowAlphaLight,
                            ),
                        ),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(TouchSize)
                // 先裁圆再挂按压层：玻璃材质与白色高光都只能是圆形，不会透出圆钮外
                // （部件顺序：clip 在 pressModifier 之外，高光的整节点矩形才会被裁掉）。
                // 按压层在淡出层之外：无模糊或 API<33 时遮罩恒为 0，指示会被一起抹掉
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
            Box(
                modifier = Modifier
                    .size(TouchSize)
                    .graphicsLayer { alpha = visibility() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(MaskSize)
                        .graphicsLayer {
                            val scale = pressScale()
                            scaleX = scale
                            scaleY = scale
                        }
                        .then(
                            if (glassActive && backdrop != null) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { CircleShape },
                                    effects = {
                                        padding = maxOf(
                                            padding,
                                            AppManagerMotion.GLASS_SAMPLE_PADDING_DP.dp.toPx(),
                                        )
                                        vibrancy()
                                        blur(
                                            AppManagerMotion.GLASS_BLUR_DP.dp.toPx(),
                                            AppManagerMotion.GLASS_BLUR_DP.dp.toPx(),
                                        )
                                        val amount = lerp(0.4f, 1f, pressState?.pressProgress?.value ?: 0f)
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
            }
            // 图标不随遮罩淡出（列表到顶时圆钮已消失，返回键仍要看得见），且必须留在
            // clickable 之内：按钮的无障碍标签由合并进来的这个图标描述提供
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onSurface.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
            )
        }
    }
}

/** 禁用态不透明度：与批量动作项的 [BottomBars] 禁用图标取值一致，两处同为「不可点的动作」。 */
private const val DISABLED_ALPHA = 0.3f
