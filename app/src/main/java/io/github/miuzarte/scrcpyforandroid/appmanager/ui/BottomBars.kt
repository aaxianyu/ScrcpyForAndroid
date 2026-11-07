package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppAction
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.component.FloatingBottomBar
import io.github.miuzarte.scrcpyforandroid.ui.component.FloatingBottomBarItem
import io.github.miuzarte.scrcpyforandroid.ui.component.FloatingGlassBar
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberPressInRootModifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.math.abs

/** 分组栏的两个分组（用户 / 系统）。 */
internal enum class AppGroup(val labelResId: Int, val icon: ImageVector) {
    User(R.string.app_manager_group_user, MiuixIcons.Store),
    // miuix 图标库没有机器人造型，系统分组沿用 material 的 Android 机器人
    System(R.string.app_manager_group_system, Icons.Rounded.Android),
}

/**
 * 底部槽位：分组切换栏与批量操作栏共用同一个槽位，同一时刻只有一个在场。
 *
 * - 悬浮形态：分组胶囊复用主页 [FloatingBottomBar]（含选中 pill 与拖拽切换），
 *   搜索圆钮与批量栏复用 [FloatingGlassBar] 胶囊容器；受「悬浮底栏」总开关 + API 33 门控；
 * - 标准形态：分组栏调 miuix [NavigationBar] + [NavigationBarItem]（选中指示与颜色由组件处理），
 *   批量栏自绘（需从屏幕外上滑盖住分组栏）；
 * - 悬浮形态的批量进出为顺序依赖的接力动画：后一段由前一段的完成事件驱动。
 */
@Composable
internal fun AppManagerBottomSlot(
    batchActive: Boolean,
    batchMode: Boolean,
    /** 多选态下是否有选中集；false 时批量动作整栏置灰（不退出多选态）。 */
    batchHasSelection: Boolean,
    /** 选中集是否全为启用态：此时「启用」无事可做，按钮置灰。全停用态同理收起「停用」。 */
    batchCanEnable: Boolean,
    batchCanDisable: Boolean,
    floatingForm: Boolean,
    glassActive: Boolean,
    gaussianActive: Boolean,
    backdrop: LayerBackdrop?,
    currentPage: Int,
    batchBusy: Boolean,
    animationsDisabled: Boolean,
    onSelectPage: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onSearchAnchor: (Offset) -> Unit,
    onBatchAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (floatingForm) {
        FloatingBottomSlot(
            batchActive = batchActive,
            batchMode = batchMode,
            batchHasSelection = batchHasSelection,
            batchCanEnable = batchCanEnable,
            batchCanDisable = batchCanDisable,
            glassActive = glassActive,
            gaussianActive = gaussianActive,
            backdrop = backdrop,
            currentPage = currentPage,
            batchBusy = batchBusy,
            animationsDisabled = animationsDisabled,
            onSelectPage = onSelectPage,
            onSearchClick = onSearchClick,
            onSearchAnchor = onSearchAnchor,
            onBatchAction = onBatchAction,
            modifier = modifier,
        )
    } else {
        StandardBottomSlot(
            batchActive = batchActive,
            batchMode = batchMode,
            batchHasSelection = batchHasSelection,
            batchCanEnable = batchCanEnable,
            batchCanDisable = batchCanDisable,
            backdrop = backdrop,
            currentPage = currentPage,
            batchBusy = batchBusy,
            animationsDisabled = animationsDisabled,
            onSelectPage = onSelectPage,
            onBatchAction = onBatchAction,
            modifier = modifier,
        )
    }
}

// ---------------------------------------------------------------- 悬浮形态

@Composable
private fun FloatingBottomSlot(
    batchActive: Boolean,
    batchMode: Boolean,
    batchHasSelection: Boolean,
    batchCanEnable: Boolean,
    batchCanDisable: Boolean,
    glassActive: Boolean,
    gaussianActive: Boolean,
    backdrop: LayerBackdrop?,
    currentPage: Int,
    batchBusy: Boolean,
    animationsDisabled: Boolean,
    onSelectPage: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onSearchAnchor: (Offset) -> Unit,
    onBatchAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberFloatingBottomTransition(animationsDisabled)
    val latestBatchActive by rememberUpdatedState(batchActive)
    LaunchedEffect(latestBatchActive) {
        if (latestBatchActive) transition.enterBatch() else transition.exitBatch()
    }
    val highlight = rememberGlassHighlight()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 悬浮底栏区域吞掉滑动手势：NavDisplay 的滑动返回覆盖整页，落在底栏上的横滑
            // 会被识别成返回手势直接退出页面。子组件（胶囊拖拽 / 圆钮按压）先于本节点收到
            // 事件，这里只负责把剩余移动标记为已消费，导航层便认定其已处理。
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) change.consume()
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 分组胶囊 + 搜索圆钮：整组水平居中；外层 8dp 给按压胀放留溢出空间。
        // alpha 只做淡出，不可见时整棵不组合——透明节点仍然接收触摸，会把底栏区域挡住
        val groupVisible = !batchActive || transition.groupAlpha.value > 0.001f
        if (groupVisible) {
            Box(
                modifier = Modifier
                    .padding(FLOATING_BAR_GUTTER)
                    .alpha(transition.groupAlpha.value)
                    .graphicsLayer {
                        val scale = transition.groupScale.value
                        scaleX = scale
                        scaleY = scale
                        renderEffect = blurEffect(
                            maxBlurDp = AppManagerMotion.GROUP_BAR_EXIT_BLUR_DP,
                            progress = 1f - transition.groupAlpha.value,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 分组胶囊：复用主页悬浮底栏（选中 pill 随动、拖拽切页、液态玻璃三态）
                FloatingBottomBar(
                    selectedIndex = currentPage,
                    // 这里直接向该闭包传入当前值索引即可：FloatingBottomBar 内部 LaunchedEffect 冻结
                    // 期间读到的旧索引会让「拖回原页面」被吞掉；真正的守卫在 AppManagerScreen.onSelectPage（读实时 pager 状态）
                    onSelected = { index -> onSelectPage(index) },
                    backdrop = backdrop,
                    tabsCount = AppGroup.entries.size,
                    isBlurEnabled = glassActive,
                    isGaussianBlurEnabled = gaussianActive,
                ) {
                    AppGroup.entries.forEach { group ->
                        FloatingBottomBarItem(
                            onClick = { onSelectPage(group.ordinal) },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                        ) {
                            Icon(
                                imageVector = group.icon,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(group.labelResId),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                // 搜索圆钮：64dp 等宽圆形，复用胶囊容器 + 玻璃按压反馈
                val fabPress = rememberGlassPressState(enabled = glassActive)
                val fabProgress = { fabPress?.pressProgress?.value ?: 0f }
                FloatingGlassBar(
                    backdrop = backdrop,
                    isBlurEnabled = glassActive,
                    isGaussianBlurEnabled = gaussianActive,
                    pressScaleAmount = AppManagerMotion.PRESS_SCALE_FAB_DP.dp,
                    pressProgress = fabProgress,
                    pressModifier = fabPress?.modifier ?: Modifier,
                    highlight = highlight,
                    modifier = Modifier
                        .size(64.dp)
                        // 揭示圆心 = 手指落点（pressInRoot）
                        .then(rememberPressInRootModifier(onSearchAnchor)),
                    pressLens = true,
                    pressInnerShadow = true,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .then(fabPress?.gestureModifier ?: Modifier)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptic.contextClick()
                                onSearchClick()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Search,
                        contentDescription = stringResource(R.string.app_manager_search),
                        tint = colorScheme.onSurface,
                    )
                }
            }
        }
        }
        }

        // 批量操作栏：短胶囊，定宽 275.6dp，容器高 64dp，居中悬浮，与分组栏同底同高。
        // 与分组栏同理：透明态不组合，否则会拦截底栏区域的触摸
        val batchVisible = batchActive || transition.batchAlpha.value > 0.001f
        if (batchVisible) {
            Box(
                modifier = Modifier
                    .padding(FLOATING_BAR_GUTTER)
                    .alpha(transition.batchAlpha.value)
                    .graphicsLayer {
                        translationY = AppManagerMotion.BATCH_BAR_SLIDE_DP.dp.toPx() *
                            (1f - transition.batchSlide.value)
                        renderEffect = blurEffect(
                            maxBlurDp = AppManagerMotion.BATCH_BAR_BLUR_DP,
                            progress = transition.batchBlur.value,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
            val batchPress = rememberGlassPressState(enabled = glassActive)
            val batchProgress = { batchPress?.pressProgress?.value ?: 0f }
            val actions = listOf(AppAction.DISABLE, AppAction.ENABLE, AppAction.EXPORT_APK, AppAction.UNINSTALL)
            val actionColors = batchActionColors()
            FloatingGlassBar(
                backdrop = backdrop,
                isBlurEnabled = glassActive,
                isGaussianBlurEnabled = gaussianActive,
                pressScaleAmount = AppManagerMotion.PRESS_SCALE_BOTTOM_BAR_DP.dp,
                pressProgress = batchProgress,
                pressModifier = batchPress?.modifier ?: Modifier,
                highlight = highlight,
                modifier = Modifier
                    .width(BATCH_BAR_WIDTH)
                    .height(64.dp)
                    .then(batchPress?.gestureModifier ?: Modifier),
                pressLens = true,
                pressInnerShadow = true,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        BatchActionItem(
                            action = action,
                            // 忙碌 / 无选中集置灰；启用与停用还看选中集的现状，
                            // 全体同态时该动作无事可做
                            enabled = !batchBusy && batchHasSelection && action.isApplicable(
                                canEnable = batchCanEnable,
                                canDisable = batchCanDisable,
                            ),
                            colors = actionColors,
                            press = BatchPressFloating,
                            modifier = Modifier.weight(1f),
                            onClick = { onBatchAction(action) },
                        )
                    }
                }
            }
            }
        }
    }
}

// ---------------------------------------------------------------- 标准形态

@Composable
private fun StandardBottomSlot(
    batchActive: Boolean,
    batchMode: Boolean,
    batchHasSelection: Boolean,
    batchCanEnable: Boolean,
    batchCanDisable: Boolean,
    backdrop: LayerBackdrop?,
    currentPage: Int,
    batchBusy: Boolean,
    animationsDisabled: Boolean,
    onSelectPage: (Int) -> Unit,
    onBatchAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val groupAlpha = remember { Animatable(1f) }
    val batchOffset = remember { Animatable(1f) }
    val latestBatchActive by rememberUpdatedState(batchActive)
    LaunchedEffect(latestBatchActive, animationsDisabled) {
        val duration = if (animationsDisabled) 0 else AppManagerMotion.STANDARD_BAR_MS
        if (latestBatchActive) {
            launch { groupAlpha.animateTo(0f, tween(duration)) }
            batchOffset.animateTo(0f, tween(duration, easing = AppManagerMotion.BatchBarEasing))
        } else {
            batchOffset.animateTo(1f, tween(duration, easing = AppManagerMotion.BatchBarEasing))
            groupAlpha.animateTo(1f, tween(duration))
        }
    }

    // 渐进 / 高斯模糊下底栏走采样材质：采样层（全屏列表）就在身后，底栏作为采样方在层外
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (backdrop != null) Modifier else Modifier.background(colorScheme.surface)),
    ) {
        // 分组栏：调 miuix NavigationBar，选中指示与颜色由组件按主题处理。
        // 模糊态把 NavigationBar 的底色透空，材质由外层 BlurredBar 提供。
        // allowProgressive = false：底栏恒定走高斯模糊，与顶栏强制走渐进的表现区分开。
        BlurredBar(backdrop = backdrop, allowProgressive = false) {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(groupAlpha.value),
                color = if (backdrop != null) Color.Transparent else colorScheme.surface,
                defaultWindowInsetsPadding = false,
            ) {
                AppGroup.entries.forEach { group ->
                    NavigationBarItem(
                        selected = group.ordinal == currentPage,
                        onClick = {
                            haptic.contextClick()
                            onSelectPage(group.ordinal)
                        },
                        icon = group.icon,
                        label = stringResource(group.labelResId),
                        enabled = !batchMode,
                    )
                }
            }
        }

        // 批量操作栏：从屏幕外上滑盖住分组栏；模糊态同样套 BlurredBar 材质，
        // 同样恒定高斯（allowProgressive = false）
        if (latestBatchActive || batchOffset.value < 1f) {
            val actions = listOf(AppAction.DISABLE, AppAction.ENABLE, AppAction.EXPORT_APK, AppAction.UNINSTALL)
            val actionColors = batchActionColors()
            BlurredBar(backdrop = backdrop, allowProgressive = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .alpha(1f - batchOffset.value)
                        .offset {
                            IntOffset(0, (BATCH_ROW_HEIGHT.toPx() * batchOffset.value).toInt())
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        BatchActionItem(
                            action = action,
                            // 忙碌 / 无选中集置灰；启用与停用还看选中集的现状，
                            // 全体同态时该动作无事可做
                            enabled = !batchBusy && batchHasSelection && action.isApplicable(
                                canEnable = batchCanEnable,
                                canDisable = batchCanDisable,
                            ),
                            colors = actionColors,
                            press = BatchPressStandard,
                            modifier = Modifier.weight(1f),
                            onClick = { onBatchAction(action) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 批量动作项

private data class BatchActionColors(
    val iconColor: Color,
    val textColor: Color,
    val pressedIconAlpha: Float,
    val pressedTextAlpha: Float,
    val disabledIconAlpha: Float,
    val disabledTextAlpha: Float,
)

/**
 * 按压底色的呈现参数。
 *
 * 悬浮形态用椭圆：上下内缩使高度落在 56dp（64dp 栏减两侧各 4dp），与分组胶囊的选中椭圆
 * 同高，按下时底色由大变小。
 * 标准形态每项横跨近半屏，椭圆会退化成横贯全屏的长条，因此保持通宽矩形、不内缩、
 * 不收缩、瞬时出现。
 */
private data class PressBackground(
    val shape: Shape,
    val verticalInset: Dp,
    val scaleMin: Float,
    val durationMillis: Int,
)

private val BatchPressFloating = PressBackground(CircleShape, 4.dp, 0.92f, 200)
private val BatchPressStandard = PressBackground(RectangleShape, 0.dp, 1f, 0)

/** 按压底色的不透明度。 */
private const val BATCH_PRESS_BG_ALPHA = 0.12f

/**
 * 通宽动作项的按压识别：**纯几何判定，没有 slop、没有缓冲**。
 *
 * 热区取动作项整框（本项高 176px），判定因此不设任何容差：手指落入即按压，越出上 / 下 /
 * 左 / 右任一边即作废本次手势（移回也不恢复），抬手时仍在框内才点击。任何容差都会把
 * 「手指停在动作项上」判成取消。
 *
 * 不消费指针事件，底栏自身的玻璃按压手势层仍能收到同一批事件。
 */
private fun Modifier.pressWithinBounds(
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var inside = true
        onPressedChange(true)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == down.id }
            if (change == null) {
                onPressedChange(false)
                return@awaitEachGesture
            }
            val position = change.position
            if (position.x < 0f || position.x > size.width ||
                position.y < 0f || position.y > size.height
            ) {
                if (inside) {
                    inside = false
                    onPressedChange(false)
                }
            }
            if (!change.pressed) {
                if (inside) onClick()
                onPressedChange(false)
                return@awaitEachGesture
            }
        }
    }
}

/**
 * 批量动作项：图标槽 28.3dp（内 21dp 字形），文字 11sp 粗体
 * （大字体 16sp / 降级 10sp / 大字体降级 14sp），图标与文字间距 -2.2dp，
 * 文字最多两行省略。
 *
 * 图标与文字色**显式指定**为表面色上的全不透明度色（亮色白 / 暗色黑），
 * 不使用组件库的选中/未选中模型（否则自动化与无障碍会把这几个动作报成「已选中」）。
 *
 * 按压反馈分两层，只有底色层缩放：
 * - 底色层按 [press] 的形状与内缩呈现，按下时淡入并由大变小。
 * - 内容层（图标＋文字）只降透明度，尺寸固定不动。
 *
 * 触摸区是整条动作项（底色层的上下内缩部分也在其内），判定为纯几何：越出该项矩形即
 * 作废本次手势。禁用项不产生按压交互，因此没有任何反馈。
 */
@Composable
private fun BatchActionItem(
    action: AppAction,
    enabled: Boolean,
    colors: BatchActionColors,
    press: PressBackground,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    var degraded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val normalSize = (11f * fontScale).coerceAtMost(16f).sp
    val degradedSize = (10f * fontScale).coerceAtMost(14f).sp
    val labelSize = if (degraded) degradedSize else normalSize
    // 底色层的淡入与收缩同用一个进度；内容层不参与缩放
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(press.durationMillis),
        label = "batchItemPress",
    )
    val iconColor = when {
        !enabled -> colors.iconColor.copy(alpha = colors.disabledIconAlpha)
        pressProgress > 0f -> colors.iconColor.copy(alpha = colors.pressedIconAlpha)
        else -> colors.iconColor
    }
    val textColor = when {
        !enabled -> colors.textColor.copy(alpha = colors.disabledTextAlpha)
        pressProgress > 0f -> colors.textColor.copy(alpha = colors.pressedTextAlpha)
        else -> colors.textColor
    }
    val clickAction = {
        haptic.contextClick()
        onClick()
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (enabled) {
                    Modifier
                        .pressWithinBounds(
                            onPressedChange = { pressed = it },
                            onClick = clickAction,
                        )
                        .semantics {
                            role = Role.Button
                            onClick(action = { clickAction(); true })
                        }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 底色层独立于内容，只承载形状、透明度与收缩
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(vertical = press.verticalInset)
                .graphicsLayer {
                    val shrink = 1f - (1f - press.scaleMin) * pressProgress
                    alpha = pressProgress
                    scaleX = shrink
                    scaleY = shrink
                }
                .clip(press.shape)
                .background(colorScheme.onSurface.copy(alpha = BATCH_PRESS_BG_ALPHA)),
        )
        // 纵向空间不足时降级字号，且一旦降级不回落（作用域 = 本次页面实例）
        val neededHeightPx = with(density) {
            BATCH_ICON_SLOT.toPx() - BATCH_ICON_TEXT_GAP.toPx() + 2 * (labelSize.toPx() * 1.25f)
        }
        LaunchedEffect(maxHeight, labelSize) {
            if (!degraded && neededHeightPx > with(density) { maxHeight.toPx() }) {
                degraded = true
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(BATCH_ICON_SLOT),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = batchActionIcon(action),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = iconColor,
                )
            }
            Text(
                text = stringResource(batchActionLabel(action)),
                fontSize = labelSize,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(y = -BATCH_ICON_TEXT_GAP),
            )
        }
    }
}

/**
 * 动作图标与文字的颜色。
 *
 * 按压时整体降到 0.6（浅色）/ 0.5（深色）：`onSurface` 在浅色主题下是暗色、深色主题下
 * 是亮色，据此判定深浅，不额外读设置，于是跟随动态取色与强制深浅色都一致。
 */
@Composable
private fun batchActionColors(): BatchActionColors {
    val onSurface = colorScheme.onSurface
    val pressedAlpha = if (onSurface.luminance() > 0.5f) 0.5f else 0.6f
    return BatchActionColors(
        iconColor = onSurface,
        textColor = onSurface,
        pressedIconAlpha = pressedAlpha,
        pressedTextAlpha = pressedAlpha,
        disabledIconAlpha = 0.3f,
        disabledTextAlpha = 0.15f,
    )
}

/**
 * 该动作对当前选中集是否有意义：启用要求选中集里存在未启用的，停用要求存在启用的。
 *
 * 两者都是「存在即可」——混合选中时两个都可用，等于批量纠正；全体同态时另一个置灰。
 */
private fun AppAction.isApplicable(canEnable: Boolean, canDisable: Boolean): Boolean = when (this) {
    AppAction.ENABLE -> canEnable
    AppAction.DISABLE -> canDisable
    else -> true
}

internal fun batchActionIcon(action: AppAction): ImageVector = when (action) {
    AppAction.DISABLE -> MiuixIcons.Basic.Close
    AppAction.ENABLE -> MiuixIcons.Ok
    AppAction.UNINSTALL -> MiuixIcons.Delete
    AppAction.EXPORT_APK -> MiuixIcons.Download
    else -> MiuixIcons.Basic.Close
}

internal fun batchActionLabel(action: AppAction): Int = when (action) {
    AppAction.DISABLE -> R.string.app_manager_disable
    AppAction.ENABLE -> R.string.app_manager_enable
    AppAction.UNINSTALL -> R.string.app_manager_uninstall
    AppAction.EXPORT_APK -> R.string.app_manager_batch_export
    else -> R.string.app_manager_disable
}

private val BATCH_BAR_WIDTH: Dp = 275.6.dp
private val BATCH_ICON_SLOT: Dp = 28.3.dp
private val BATCH_ICON_TEXT_GAP: Dp = 2.2.dp
private val BATCH_ROW_HEIGHT: Dp = 64.dp

/** 悬浮胶囊自身的外扩留白（压印胀放的空间），槽位两侧各留一份。 */
private val FLOATING_BAR_GUTTER: Dp = 8.dp

/** 悬浮形态距屏幕底 = 系统手势区高度 + 4dp。 */
private val FLOATING_BAR_EDGE_DP: Dp = 4.dp

/**
 * 底部槽位在系统手势区之上占掉的高度，供页面把消息条等内容抬到底栏上方。
 *
 * 分组栏与批量栏同高，所以这个量与当前是哪一个在场无关。两种形态各自的值与
 * [AppManagerBottomSlot] 的布局同源：悬浮形态是胶囊高 64dp + 两侧外扩 8dp +
 * 距屏幕底 4dp；标准形态是 NavigationBar 的 item 高 64dp。
 */
internal fun bottomSlotClearance(floatingForm: Boolean): Dp = when (floatingForm) {
    true -> BATCH_ROW_HEIGHT + FLOATING_BAR_GUTTER * 2 + FLOATING_BAR_EDGE_DP
    else -> BATCH_ROW_HEIGHT
}

// ---------------------------------------------------------------- 过渡与手势

/** 悬浮形态批量进出的接力动画状态（后一段由前一段的完成事件驱动）。 */
private class FloatingBottomTransition(
    private val scope: CoroutineScope,
    private val animationsDisabled: Boolean,
) {
    val groupAlpha = Animatable(1f)
    val groupScale = Animatable(1f)
    val batchAlpha = Animatable(0f)
    val batchSlide = Animatable(0f)
    val batchBlur = Animatable(0f)
    private var job: Job? = null

    fun enterBatch() {
        job?.cancel()
        job = scope.launch {
            // 1. 分组栏后退：缩小 + 模糊 + 淡出（230ms）
            val exitMs = if (animationsDisabled) 0 else AppManagerMotion.GROUP_BAR_EXIT_MS
            launch {
                groupScale.animateTo(
                    AppManagerMotion.GROUP_BAR_EXIT_SCALE,
                    tween(exitMs, easing = FastOutSlowInEasing),
                )
            }
            groupAlpha.animateTo(0f, tween(exitMs, easing = FastOutSlowInEasing))
            // 2. 批量栏上场：由后退动画完成驱动
            val enterMs = if (animationsDisabled) 0 else AppManagerMotion.BATCH_BAR_ENTER_MS
            batchBlur.snapTo(0f)
            launch {
                batchAlpha.animateTo(1f, tween(enterMs, easing = LinearEasing))
            }
            batchSlide.animateTo(1f, tween(enterMs, easing = AppManagerMotion.BatchBarEasing))
        }
    }

    fun exitBatch() {
        job?.cancel()
        job = scope.launch {
            // 1. 先加模糊（0 → 10dp，120ms）
            val blurMs = if (animationsDisabled) 0 else AppManagerMotion.BATCH_BAR_EXIT_BLUR_MS
            batchBlur.animateTo(1f, tween(blurMs, easing = AppManagerMotion.BatchBarEasing))
            // 2. 整体下滑 + 淡出（延迟 120ms，150ms）
            val slideMs = if (animationsDisabled) 0 else AppManagerMotion.BATCH_BAR_EXIT_SLIDE_MS
            launch {
                batchAlpha.animateTo(0f, tween(slideMs, easing = LinearEasing))
            }
            batchSlide.animateTo(0f, tween(slideMs, easing = AppManagerMotion.BatchBarEasing))
            // 3. 分组栏回归：由批量栏退场驱动
            val exitMs = if (animationsDisabled) 0 else AppManagerMotion.GROUP_BAR_EXIT_MS
            launch {
                groupScale.animateTo(1f, tween(exitMs, easing = FastOutSlowInEasing))
            }
            groupAlpha.animateTo(1f, tween(exitMs, easing = FastOutSlowInEasing))
        }
    }
}

@Composable
private fun rememberFloatingBottomTransition(animationsDisabled: Boolean): FloatingBottomTransition {
    val scope = rememberCoroutineScope()
    return remember(scope, animationsDisabled) {
        FloatingBottomTransition(scope, animationsDisabled)
    }
}

/** 批量进出时给胶囊加的 RenderEffect 模糊（px）；模糊量随进度增长。 */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.blurEffect(
    maxBlurDp: Float,
    progress: Float,
): BlurEffect? {
    if (progress <= 0.001f) return null
    val radius = maxBlurDp.dp.toPx() * progress
    if (radius <= 0.01f) return null
    return BlurEffect(radius, radius, TileMode.Decal)
}
