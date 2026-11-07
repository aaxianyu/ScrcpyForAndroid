package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 应用管理域动效常量，与「移除动画」降级。
 *
 * 系统动画时长缩放 = 0 时，本篇常量一律按 0 处理；上游既有动效不受影响。
 */
object AppManagerMotion {

    /** 搜索圆形揭示。 */
    const val REVEAL_DURATION_MS = 400
    val RevealEasing: Easing = FastOutSlowInEasing

    /** 分组切换（分页）。 */
    const val PAGE_DAMPING_RATIO = 0.9f
    const val PAGE_STIFFNESS = 700f

    /** 批量进出 · 悬浮形态。 */
    const val GROUP_BAR_EXIT_MS = 230
    const val GROUP_BAR_EXIT_SCALE = 0.8f
    const val GROUP_BAR_EXIT_BLUR_DP = 12f
    const val BATCH_BAR_ENTER_MS = 150
    const val BATCH_BAR_EXIT_BLUR_MS = 120
    const val BATCH_BAR_EXIT_SLIDE_MS = 150
    const val BATCH_BAR_SLIDE_DP = 8.7f
    const val BATCH_BAR_BLUR_DP = 10f
    val BatchBarEasing: Easing = CubicBezierEasing(0.22f, 0.82f, 0.30f, 1f)

    /** 批量进出 · 标准形态。 */
    const val STANDARD_BAR_MS = 210

    /** 顶栏圆形遮罩淡入淡出。 */
    const val MASK_FADE_MS = 350

    /** 底部软滚动渐隐。 */
    const val BOTTOM_SCROLL_SCrim_DP = 96

    /** 玻璃按压：产品自定义胀放量。 */
    const val PRESS_SCALE_FAB_DP = 6f
    const val PRESS_SCALE_TOP_BAR_BUTTON = 0.10f

    /**
     * 底栏按压的整体胀放量。
     *
     * 胶囊不随按压胀放，液态玻璃 / 高斯模糊 / 无模糊三态的按压表现因此一致；
     * 触摸反馈由动作项自己的椭圆底色收缩承担。圆钮不在底栏槽位内，仍保留胀放。
     */
    const val PRESS_SCALE_BOTTOM_BAR_DP = 0f
    const val PRESS_LENS_HEIGHT_DP = 10f
    const val PRESS_LENS_AMOUNT_DP = 14f
    const val PRESS_CHROMATIC_ABERRATION = 0.5f
    const val PRESS_INNER_SHADOW_DP = 8f

    /** 静态玻璃参数（组件库值）。 */
    const val GLASS_BLUR_DP = 4f
    const val GLASS_LENS_DP = 24f
    const val GLASS_SAMPLE_PADDING_DP = 40f
    const val GLASS_CONTAINER_ALPHA = 0.4f
    const val GAUSSIAN_FALLBACK_BLUR = 25f
    const val GAUSSIAN_FALLBACK_CONTAINER_ALPHA = 0.6f

    /** 下拉刷新完成后的停留时长。 */
    const val REFRESH_DONE_FLASH_MS = 600L

    /** 底部分组栏横拖切页阈值。 */
    const val GROUP_BAR_DRAG_SWITCH_DP = 48f
}

/** 系统「移除动画」是否开启（只读一次，配置变化由重建兜底）。 */
@Composable
fun rememberAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** 应用管理域时长：移除动画开启时按 0 处理。 */
fun Int.scaledBy(animationsDisabled: Boolean): Int = if (animationsDisabled) 0 else this

fun Long.scaledBy(animationsDisabled: Boolean): Long = if (animationsDisabled) 0L else this
