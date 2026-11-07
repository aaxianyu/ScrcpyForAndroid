package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import io.github.miuzarte.scrcpyforandroid.ui.component.miuix.modifier.inspectDragGestures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 液态玻璃按压反馈。
 *
 * 反馈构成：白色光泽跟随手指、胶囊胀放（由调用方的 graphicsLayer 完成）、
 * 透镜增厚与色散（调用方按 [pressProgress] 调 lens 参数）、内阴影。
 *
 * ## API 门槛
 * 本类在构造函数里实例化 `RuntimeShader`（API 33+）。低版本上该类不存在，
 * 类初始化即抛 `NoClassDefFoundError`，因此**只能在 API ≥ 33 时构造**：
 * [rememberGlassPressState] 在低版本直接返回 null，调用方据此整体不挂反馈
 * （高斯 / 纯色态本就不挂）。不要用 `@SuppressLint("NewApi")`
 * 代替这层守卫——它只压 lint 警告，不产生任何运行时保护。
 */
class GlassPressState(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {

    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    val pressProgress = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader = RuntimeShader(
        """
    uniform float2 size;
    layout(color) uniform half4 color;
    uniform float radius;
    uniform float2 position;

    half4 main(float2 coord) {
        float dist = distance(coord, position);
        float intensity = smoothstep(radius, radius * 0.5, dist);
        return color * intensity;
    }""",
    )

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgress.value
        if (progress > 0f) {
            drawRect(
                Color.White.copy(0.06f * progress),
                blendMode = BlendMode.Plus,
            )
            shader.apply {
                val shaderPosition = position(size, positionAnimation.value)
                val posX = shaderPosition.x.fastCoerceIn(0f, size.width)
                val posY = shaderPosition.y.fastCoerceIn(0f, size.height)
                setFloatUniform("size", size.width, size.height)
                setColorUniform("color", Color.White.copy(0.12f * progress).toArgb())
                setFloatUniform("radius", size.minDimension * 1.2f)
                setFloatUniform("position", posX, posY)
            }
            drawRect(
                ShaderBrush(shader),
                blendMode = BlendMode.Plus,
            )
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgress.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgress.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgress.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

/**
 * 记住一个 [GlassPressState]；**API < 33 时返回 null 且不构造该类**。
 *
 * 低版本上顶栏按钮退化为普通按钮（无玻璃按压高光、无圆形遮罩），
 * 页面其余部分正常。
 */
@Composable
fun rememberGlassPressState(
    enabled: Boolean,
    position: (Size, Offset) -> Offset = { _, offset -> offset },
): GlassPressState? {
    if (!enabled) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassPressState(scope, position) }
}
