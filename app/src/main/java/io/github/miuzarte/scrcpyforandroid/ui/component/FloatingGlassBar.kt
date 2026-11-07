package io.github.miuzarte.scrcpyforandroid.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.miuzarte.scrcpyforandroid.ui.component.liquid.InnerShadow
import io.github.miuzarte.scrcpyforandroid.ui.component.liquid.innerShadow
import io.github.miuzarte.scrcpyforandroid.ui.component.liquid.lens
import io.github.miuzarte.scrcpyforandroid.ui.component.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 液态玻璃容器色（组件库值）：表面容器色 40%。 */
private val GlassContainerAlpha = 0.4f

/** 高斯回退混合色（组件库值）：表面容器色 60%。 */
private val GaussianContainerAlpha = 0.6f

private val ShadowRadius = 10.dp
private val GlassBlur = 4.dp
private val GlassLens = 24.dp
private val SamplePadding = 40.dp
private val GaussianBlur = 25f
private val PressLensHeight = 10.dp
private val PressLensAmount = 14.dp
private val InnerShadowRadius = 8.dp
private const val ChromaticAberration = 0.5f

/**
 * 悬浮胶囊容器。
 *
 * 三态材质自动切换：
 * - 液态玻璃开且 backdrop 非空 → `drawBackdrop`（vibrancy + 4dp 模糊 + 24dp 透镜 + 重力高光）
 * - 否则 backdrop 非空（高斯回退开）→ `textureBlur` 25dp + 表面容器色 60% 混合
 * - 否则 → 表面容器色实底
 *
 * 按压反馈（仅在液态玻璃态可见）由调用方通过 [pressProgress] 与 [pressModifier] 注入：
 * - 胶囊胀放：[pressScaleAmount] 给定产品自定义量（宽栏 16dp / 圆钮 6dp）
 * - 透镜增厚：[pressLens] 开启时按压把 24dp×24dp 收到 10dp×14dp 并启色散 0.5
 * - 内阴影：[pressInnerShadow] 开启时 8dp × 按压进度
 *
 * 调用方的 [modifier] 施加在材质之后（与主页悬浮底栏一致：先材质、后尺寸与内边距）。
 *
 * @param backdrop 采样层（玻璃与高斯共用；null 走纯色）
 * @param isBlurEnabled 液态玻璃开关
 * @param isGaussianBlurEnabled 高斯回退开关（液态玻璃关闭但模糊仍开时）
 * @param pressScaleAmount 按压胀放量（dp）
 * @param pressProgress 按压进度读取器（layerBlock / effects 内延迟读取，避免重组）
 * @param pressModifier 按压光泽与手势层（InteractiveHighlight / GlassPressState）
 * @param highlight 重力感应镜面高光
 * @param pressLens 是否启用按压透镜增厚（产品自定义反馈）
 * @param pressInnerShadow 是否启用按压内阴影（产品自定义反馈）
 */
@Composable
fun FloatingGlassBar(
    backdrop: LayerBackdrop?,
    isBlurEnabled: Boolean,
    isGaussianBlurEnabled: Boolean,
    pressScaleAmount: Dp,
    pressProgress: () -> Float,
    pressModifier: Modifier,
    highlight: State<Highlight>,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    pressLens: Boolean = false,
    pressInnerShadow: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = colorScheme.background.luminance() < 0.5f
    // 液态玻璃关闭时回退高斯；两者都关时纯色
    val gaussianBlurBackdrop = if (!isBlurEnabled && isGaussianBlurEnabled) backdrop else null
    val containerColor = when {
        isBlurEnabled -> colorScheme.surfaceContainer.copy(alpha = GlassContainerAlpha)
        gaussianBlurBackdrop != null -> Color.Transparent
        else -> colorScheme.surfaceContainer
    }
    val innerShadowModifier = if (pressInnerShadow) {
        Modifier.innerShadow(shape) {
            val progress = pressProgress()
            if (progress > 0f) {
                InnerShadow(
                    radius = InnerShadowRadius * progress,
                    color = Color.Black.copy(alpha = 0.15f),
                    alpha = progress,
                )
            } else {
                null
            }
        }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = ShadowRadius,
                    color = Color.Black,
                    alpha = if (isDark) 0.2f else 0.1f,
                ),
            )
            .then(
                if (isBlurEnabled && backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            val progress = pressProgress()
                            padding = maxOf(padding, SamplePadding.toPx())
                            vibrancy()
                            blur(GlassBlur.toPx(), GlassBlur.toPx())
                            if (pressLens && progress > 0f) {
                                lens(
                                    refractionHeight = lerp(
                                        GlassLens.value,
                                        PressLensHeight.value,
                                        progress,
                                    ).dp.toPx(),
                                    refractionAmount = lerp(
                                        GlassLens.value,
                                        PressLensAmount.value,
                                        progress,
                                    ).dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = ChromaticAberration * progress,
                                )
                            } else {
                                lens(
                                    refractionHeight = GlassLens.toPx(),
                                    refractionAmount = GlassLens.toPx(),
                                )
                            }
                        },
                        highlight = { highlight.value.copy(alpha = 0.75f) },
                        layerBlock = {
                            val progress = pressProgress()
                            if (progress > 0f) {
                                val scale = 1f + pressScaleAmount.toPx() / size.width * progress
                                scaleX = scale
                                scaleY = scale
                            }
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                } else if (gaussianBlurBackdrop != null) {
                    Modifier
                        .textureBlur(
                            backdrop = gaussianBlurBackdrop,
                            shape = shape,
                            blurRadius = GaussianBlur,
                            colors = BlurDefaults.blurColors(
                                blendColors = listOf(
                                    BlendColorEntry(
                                        color = colorScheme.surfaceContainer.copy(
                                            alpha = GaussianContainerAlpha,
                                        ),
                                    ),
                                ),
                            ),
                        )
                        .background(containerColor, shape)
                } else {
                    Modifier.background(containerColor, shape)
                },
            )
            .then(pressModifier)
            .then(innerShadowModifier)
            .then(modifier),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
