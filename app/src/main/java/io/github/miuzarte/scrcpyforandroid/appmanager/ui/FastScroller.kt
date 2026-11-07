// Copyright 2026, ScrcpyForAndroid contributors
// SPDX-License-Identifier: Apache-2.0

package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppOrdering
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * MIUI 文件管理器同款快速滚动滑块（右侧圆滑块 + 左侧首字母气泡）。
 *
 * 交互（对照逆向参考实现）：
 * - 滑块只在列表滚动时淡入出现，停止滚动 [FastScrollerMotion.AUTO_HIDE_MS] 后淡出；
 *   滚动 / 交互都会续期，连续滚动或长按拖动超过 2s 不会中途淡出；
 * - 只有滑块圆（外扩 [FastScrollerMotion.TOUCH_PAD]）可拖，隐藏态不响应触摸；
 * - 按住滑块：上下箭头外扩 + 气泡从右侧 overshoot 回弹滑入；
 * - 拖动：气泡贴滑块左侧跟随、内容为当前位置所属节首字母，列表连续滚到手指对应位置；
 * - 松手：气泡滞留 [FastScrollerMotion.CAPSULE_HOLD_MS] 后滑出；
 * - 手势方向锁：滑块上先竖拖则滑块接管（之后横滑也不翻页）；直接横滑则放行给外层
 *   HorizontalPager 翻页。
 *
 * ## 位置模型：滑块跟列表整体连续走，拖到哪就到哪
 *
 * 滑块顶边 = **滚动进度 × 行程**，进度按像素归一：
 *
 * ```
 * 已滚像素 = 首项之前的高度和 - 首项在视口中的偏移
 * 可滚像素 = 内容总高 + 上下 padding - 视口高度
 * progress = 已滚像素 / 可滚像素
 * ```
 *
 * 两个式子是一把尺子的两端：`scrollToItem(i, offset)` 把该项顶边贴到视口 top，该项在
 * `layoutInfo` 里的偏移就是 `-offset`，contentPadding 只抬高绘制位置、不进分子（只在
 * 分母里算一次）。item 高度取自标定值，逐 item 各记各的——字母头、节末间隔、单行与两行
 * 应用名都按自身真值参与，首项换下标那一刻前后两侧算出来是同一个数。
 *
 * 拖动时列表按**同一把尺子连续滚动**：手指位置先算成已滚像素，再由前缀和反查出落在哪个
 * item 里（下标 + 项内偏移，见 [itemAtScrollOffset]），落地循环每帧把最新目标交给
 * `scrollToItem`，两侧相差不超过一帧的目标值；松手时收尾协程补上最后一次，滑块按同一
 * 进度停住，不存在互相让位的跳动。气泡只报当前位置所在节的字母，是标签而非落点，滚动即
 * 连续更新。
 *
 * ## 绘制期读数
 *
 * 位置与动画值都在 draw / offset lambda 里现算——draw 修饰符会观测快照读，值变即
 * 重绘，与列表同一时机、零延迟；在组合期读会让动画每帧重组整个组件，快速滑动时
 * 和列表抢帧 → 卡顿。对照 miuix 组件库 `ScrollBar.kt`：`sliderAdapter.position` 就在
 * drawBehind 里读。因此滑块位置不落 state，也就没有每帧写状态逼重组的问题。
 *
 * 手势检测用 `pointerInput(Unit)` 常驻（key 不含任何 state），在 lambda 里读 state
 * 当前值——把位置当 key 会在拖动途中反复取消重启手势，导致拖不动 / 不跟手。
 *
 * 触摸盒位置在拖动期间冻结：盒子一动，`change.position`（盒内局部坐标）随之漂移，
 * 会把滑块拖动速度打成对折。
 *
 * @param listState 当前可见分组的列表状态：同步滑块位置、承接拖动跳转
 * @param sections 当前分组的节表；节数 < 2 时整体隐藏（单节列表没有跳转需求）。
 *   节顺序必须与列表 item 顺序一致（列表按同一节表摆放 item）
 * @param animationsDisabled 系统「移除动画」开启：动画瞬切，但保留显隐 / 滞留时序
 * @param modifier 调用方用 padding 给出滑块活动范围（避开顶栏 / 底栏）；
 *   组件自身不叠加安全区，以避免双重偏移。
 * @param headerFallbackHeight 字母头还没被实测到时的兜底高度。0 表示按行高顶替；
 *   调用方量得出真实排版高度时应当传进来。
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    sections: List<AppSection>,
    animationsDisabled: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    bottomObscured: androidx.compose.ui.unit.Dp = 0.dp,
    listTouchTick: Int = 0,
    headerFallbackHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val sectionCount = sections.size
    if (sectionCount < 2) return

    // pointerInput(Unit) 不重启，其 lambda 内的 `sections` 拿到的是首帧快照；用
    // rememberUpdatedState 包一层，手势期间读到的就是最新节表。
    val currentSections by rememberUpdatedState(sections)

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    val fillColor = FastScrollerDefaults.fillColor()
    val borderColor = FastScrollerDefaults.borderColor()
    val contentColor = FastScrollerDefaults.contentColor()

    // 尺寸相关像素值
    val thumbSizePx = with(density) { FastScrollerMotion.THUMB_SIZE.toPx() }
    val thumbMarginEndPx = with(density) { FastScrollerMotion.THUMB_MARGIN_END.toPx() }
    val capsuleSizePx = with(density) { FastScrollerMotion.CAPSULE_SIZE.toPx() }
    val capsuleGapPx = with(density) { FastScrollerMotion.CAPSULE_MARGIN_TO_THUMB.toPx() }
    val arrowHalfWidthPx = with(density) { (FastScrollerMotion.ARROW_SIZE / 2f).toPx() }
    val arrowHalfHeightPx = with(density) { (FastScrollerMotion.ARROW_HEIGHT / 2f).toPx() }
    val arrowStrokePx = with(density) { FastScrollerMotion.ARROW_STROKE.toPx() }
    val borderWidthPx = with(density) { FastScrollerMotion.BORDER_WIDTH.toPx() }
    val touchPadPx = with(density) { FastScrollerMotion.TOUCH_PAD.toPx() }
    val bottomObscuredPx = with(density) { bottomObscured.toPx() }
    val enterDistancePx = with(density) { FastScrollerMotion.ENTER_DISTANCE.toPx() }

    // ---- 动画状态（对照参考实现的字段）----
    // 这些 Animatable 只在 draw lambda 里读（draw 修饰符会观测快照读 → 值变即重绘），
    // 不在组合期读：组合期读会让动画每帧重组整个组件，快速滑动时和列表抢帧 → 卡顿。
    // 对照 miuix 组件库 ScrollBar.kt：animatedThumbAlpha / opacity 都是在 drawBehind 里读的
    val thumbScale = remember { Animatable(FastScrollerMotion.THUMB_SCALE_START) }
    val thumbAlpha = remember { Animatable(0f) }
    val arrowSpread = remember { Animatable(FastScrollerMotion.ARROW_SPREAD_START) }
    val capsuleScaleX = remember { Animatable(FastScrollerMotion.CAPSULE_SCALE_START) }
    val capsuleTranslateX = remember { Animatable(enterDistancePx) }
    val capsuleAlpha = remember { Animatable(0f) }

    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    /** 拖动期间触摸盒在轨道内的顶边（按下时冻结，见类注释）。 */
    var frozenBoxTop by remember { mutableFloatStateOf(0f) }
    /**
     * 拖动期间滑块跟手指的进度：连续量。
     *
     * 列表侧同样按这个进度连续滚动（见 [itemAtScrollOffset]），两侧严格跟手。
     */
    var dragProgress by remember { mutableFloatStateOf(0f) }
    /** 松手后滑块按列表真实滚动位置停住：两条通道共用同一把像素尺子。 */
    val settleProgress = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    var capsuleText by remember { mutableStateOf("") }
    var capsuleLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    /** 松手后延迟隐藏气泡的任务；再次 show 前取消，避免 1s 内再次拖动时被旧 hide 打断。 */
    var capsuleHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // item 高度标定 + 前缀和：普通字段持有，不写快照。
    // 写快照会逼组件重组；前缀和每次重建要分配一个 totalItems+1 的 FloatArray，
    // 标定值逐项到来时重建几次无所谓，落到每帧就是实打实的 GC 压力（表现为整页发卡）
    val px = remember { ScrollPx() }
    /**
     * 拖动期间冻结的前缀和。
     *
     * 滚动会让新露出的头/间隔被实测，[prefixOf] 随之变化，同一个手指位置就会换算成不同的
     * 滚动目标——列表自己抖起来（节的头部经过顶栏那一段最明显）。拖动全程用按下那一刻的
     * 快照，指头到位置的映射才单调。
     */
    var dragPrefix by remember { mutableStateOf<FloatArray?>(null) }
    /** 拖动期间冻结的可滚像素，理由同 [dragPrefix]。 */
    var dragMaxScroll by remember { mutableStateOf<Float?>(null) }
    /**
     * 拖动会话号：每次按下 +1。
     *
     * 松手收尾是异步的（要等最后一次跳转落位），期间用户可能又按下一次滑块开新拖动。
     * 收尾协程只认自己发起时那个会话号，免得旧协程把新拖动的 [dragging] / [settling] 改掉。
     */
    var dragSession by remember { mutableIntStateOf(0) }
    val rowHeightPx = with(density) { AppRowHeight.toPx() }
    // 节末间隔 item 的高度，与 AppManagerListPage 里那个 Spacer(8.dp) 保持一致
    val sectionGapPx = with(density) { AppSectionGap.toPx() }
    // 字母头在量到实测值之前的兜底高度（由调用方按字母头的排版量出来）。
    // 头 78 px、行 189 px（2.5 倍密度）：每个头差 111 px，一屏十来个头就是上千像素，分母整段偏大
    val headerFallbackPx = with(density) { headerFallbackHeight.toPx() }
    // 自动隐藏计时：一条长驻协程按截止时间续期
    val hideTimer = remember { HideTimer(scope) }

    // 可滚像素的冻结值：见 [maxScrollPx]。用普通可变字段而非 State——
    // [maxScrollPx] 会在 draw 与 snapshotFlow 里被调用，后者跑在只读快照上，写 State 会崩
    val limit = remember { ScrollLimit() }

    // ---------------------------------------------------------------- 位置模型

    /** 滑块可行程：容器高度减去滑块自身，上下两端各剩半圆，行程对称。 */
    fun travel(trackHeightPx: Float): Float = max(0f, trackHeightPx - thumbSizePx)

    /**
     * 布局是否已经换成当前节表的结果。
     *
     * 换节表（换分组、筛选变化）后，第一份 layoutInfo 往往仍是上一份节表排版出来的：
     * 拿它的下标往新表里记高度，会把旧行高记到字母头或间隔上。只认 item 总数对得上的那份。
     */
    fun layoutReady(info: LazyListLayoutInfo): Boolean =
        layoutReadyOf(info.totalItemsCount, currentSections)

    /** 标定：按 item 记下实测高度。滚动停止后调用一次。 */
    fun calibrate(info: LazyListLayoutInfo) {
        if (!layoutReady(info)) return
        val headers = AppOrdering.layoutOf(currentSections).mapTo(HashSet()) { it.headerIndex }
        info.visibleItemsInfo.forEach { item ->
            if (item.size <= 0) return@forEach
            // 字母头只量一次就够（各节同形）。没露过面的头按调用方量出来的兜底高度算
            // （见 [headerFallbackPx]），等价于同一形状的头，不必逐节都露一面。
            if (item.index in headers) px.setHeaderHeight(item.size.toFloat())
            else px.setItemHeight(item.index, item.size.toFloat())
        }
        limit.dirty = true
    }

    fun prefixOf(): FloatArray =
        px.prefixOf(currentSections, rowHeightPx, sectionGapPx, headerFallbackPx)

    /** `[0, until)` 区间 item 高度和；间距按 [spacing] 折算。O(1)。 */
    fun heightBefore(until: Int, spacing: Int): Float =
        heightBeforeIn(dragPrefix ?: prefixOf(), spacing.toFloat(), until)

    /**
     * 可滚像素：内容总高 + 上下 padding - 视口高度。
     *
     * 滚动期间冻结：前缀和里没滚到的项是按 [AppRowHeight] 外推的，每标定到一个真值它就会
     * 轻微伸缩，分母跟着变会让「同一个滚动位置算出不同进度」——滑块看起来在反向抖。冻结后
     * 分子分母都是定值，进度严格随像素线性变化；停下标定完再放新值进来。
     */
    fun maxScrollPx(info: LazyListLayoutInfo): Float {
        // 冻结值在松手后仍然沿用：一旦松手就重算，分母会随新标定的头高变小，
        // 同一个列表位置算出的进度整体上跳，滑块就在松手那一刻「闪」一下
        dragMaxScroll?.let { return it }
        val total = info.totalItemsCount
        // 行高变了（切「显示版本」「APK 大小」、换字号）而 item 数不变时，内容总高也跟着变：
        // 标定计数进失效条件，否则分母一直用重排前那个值
        val measured = limit.revision != px.revision
        // 三项任一成立都要重算，而且只能趁滚动停下时算：滚动中换分母，同一列表位置
        // 算出的进度会整体跳一下
        val stale = limit.totalCount != total || limit.dirty || measured
        if (stale && !listState.isScrollInProgress) {
            limit.px = maxScrollPxOf(
                contentPx = heightBefore(info.totalItemsCount, info.mainAxisItemSpacing),
                beforePaddingPx = info.beforeContentPadding.toFloat(),
                afterPaddingPx = info.afterContentPadding.toFloat(),
                viewportPx = info.viewportSize.height.toFloat(),
            )
            limit.totalCount = total
            limit.revision = px.revision
            limit.dirty = false
        }
        return limit.px
    }


    /**
     * 列表滚动进度 ∈ [0,1]，连续量，按像素归一。
     *
     * 已滚像素 = 首项之前的高度和 − 首项在视口中的偏移。LazyListState.scrollToItem
     * 把项顶边贴到视口 top，contentPadding 只抬高绘制位置、不进这个式子（它只在 [maxScrollPx]
     * 里参与分母），所以按下时的起滚像素、拖动目标与这里逐像素对齐。前缀和用逐 item
     * 实测高度（见 [itemHeight]），字母头、节间隔、两行应用名都按各自真值参与，所以首项
     * 换下标那一刻前后两侧算出来是同一个数，滑块不会跳。
     */
    fun listProgress(): Float {
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
        return listProgressOf(
            scrolledPx = scrolledPxOf(
                firstIndex = first.index,
                firstOffsetPx = first.offset,
                prefix = dragPrefix ?: prefixOf(),
                spacingPx = info.mainAxisItemSpacing.toFloat(),
            ),
            maxScrollPx = maxScrollPx(info),
        )
    }

    /**
     * 列表可见区正中间那一行所属节的字母。
     *
     * 可见区上下界都按「真正看得见」的范围取：上界是列表视口起点（列表本身已经在
     * 顶栏之下），下界再扣掉被底栏叠住的那一段（[bottomObscured]）。
     */
    fun visibleMidSectionLetter(): String {
        if (currentSections.isEmpty()) return ""
        val info = listState.layoutInfo
        val top = info.viewportStartOffset.toFloat()
        val bottom = info.viewportEndOffset - bottomObscuredPx
        val mid = if (bottom > top) {
            (top + bottom) / 2f
        } else {
            (info.viewportStartOffset + info.viewportEndOffset) / 2f
        }
        val items = info.visibleItemsInfo
        if (items.isEmpty()) return currentSections.first().letter
        // 首末节常常不足半屏，列表到顶/到底时中点落在邻节里，会报出与边缘不符的字母：
        // 这两种情况改取贴着边缘的那一行，拖到底就是末节字母
        val anchor = when {
            !listState.canScrollBackward -> top
            !listState.canScrollForward -> bottom - 1f
            else -> mid
        }
        val hit = items.firstOrNull { it.offset <= anchor && anchor < it.offset + it.size }
            ?: items.minByOrNull { abs(it.offset + it.size / 2f - anchor) }
            ?: return currentSections.first().letter
        // 手势协程由 pointerInput(Unit) 启动、重组不会重启它，闭包里捕获的组合期值会冻在
        // 首帧；节表却是实时的（换分组、搜索过滤、刷新都会变长）。这里现算一份，否则节表
        // 变长后按旧表下标取节会越界或报到别的字母
        val table = AppOrdering.layoutOf(currentSections)
        val section = AppOrdering.sectionOfItem(table, hit.index)
        // 查不到就报空：宁可气泡不显示，也不显示一个与视线不符的字母
        return currentSections.getOrNull(section ?: -1)?.letter ?: ""
    }

    /**
     * 滚动量 → (item 下标, 项内偏移)，滑到哪就到哪，无极。
     *
     * 手指位置先换算成已滚像素，再反查它落在哪个 item 里：前缀和定下标，差值定项内偏移。
     * 定位精确到像素，所以滑块与列表始终指着同一处。
     */
    fun itemAtScrollOffset(
        targetScrolled: Float,
        prefix: FloatArray = prefixOf(),
        maxScroll: Float = maxScrollPx(listState.layoutInfo),
    ): Pair<Int, Int> = itemAtScrollOffsetIn(
        prefix = prefix,
        spacingPx = listState.layoutInfo.mainAxisItemSpacing.toFloat(),
        targetScrolled = targetScrolled,
        maxScroll = maxScroll,
    )

    /** 轨道内绝对 Y → 进度（以滑块中心比对手指位置）。 */
    fun progressOfFinger(fingerTrackY: Float, trackHeightPx: Float): Float =
        progressOfFingerIn(fingerTrackY, trackHeightPx, thumbSizePx)

    // 逐帧向真实进度收敛：每帧只走剩余距离的一部分，等价于一条固定的指数跟随。
    // 用流 + animateTo 做不到这点——collect 会积压目标（滑块走完一段才接下一段），
    // collectLatest 又每帧打断动画，两者都会让跟手变差
    val followProgress = remember { Animatable(0f) }
    /** 本次拖动的目标像素位置（绝对，取自冻结前缀）。 */
    var dragTargetPx by remember { mutableStateOf<Float?>(null) }
    /** 已交给列表的像素位置（拖动中每帧一帧到位，松手时这里就是列表的真实位置）。 */
    var dragAppliedPx by remember { mutableStateOf(-1f) }
    LaunchedEffect(listState) {
        while (true) {
            // 不拖动时不占帧回调：按下把这一轮唤醒，松手再挂回去。循环顶部无条件
            // withFrameNanos 的话，静止的列表也会每帧唤醒一次协程
            snapshotFlow { dragging }.first { it }
            while (dragging) {
                withFrameNanos { }
                val target = dragTargetPx ?: continue
                val before = dragAppliedPx
                dragAppliedPx = target
                // 像素没变就不落位：每帧重发同一个 scrollToItem 会反复重锚，白费一次排版。
                // 列表还在惯性滑行时例外：这一次落位就是停住它的动作——按下时的目标是当
                // 前位置，像素没变，但那一帧不落位的话惯性会一直走到手指开始移动为止
                if (!listState.isScrollInProgress && abs(dragAppliedPx - before) < 0.01f) continue
                val (index, offset) = itemAtScrollOffset(
                    targetScrolled = dragAppliedPx,
                    prefix = dragPrefix ?: prefixOf(),
                )
                listState.scrollToItem(index, offset)
            }
        }
    }

    LaunchedEffect(listState) {
        while (true) {
            // 拖动时滑块跟手指，这条通道不参与，等松手再接管
            if (dragging) {
                snapshotFlow { dragging }.first { !it }
                continue
            }
            val delta = listProgress() - followProgress.value
            if (abs(delta) < FastScrollerMotion.FOLLOW_EPSILON) {
                followProgress.snapTo(followProgress.value + delta)
                // 静止时停在这里：逐帧空转会让滑块每帧都重绘，白烧一整晚
                snapshotFlow { listProgress() }
                    .first { abs(it - followProgress.value) >= FastScrollerMotion.FOLLOW_EPSILON }
                continue
            }
            withFrameNanos { }
            val target = listProgress()
            followProgress.snapTo(followProgress.value + (target - followProgress.value) * FastScrollerMotion.FOLLOW_RATIO)
        }
    }

    /**
     * 滑块当前进度：拖动时跟手指，松手后停在手指落点，否则按列表真实滚动进度连续跟随。
     * 三者共用同一把尺子（见 [listProgress]），互相切换时不跳变。
     */
    fun currentProgress(): Float = when {
        dragging -> dragProgress
        settling -> settleProgress.value
        else -> followProgress.value
    }

    // ---------------------------------------------------------------- 气泡 / 显隐

    fun refreshCapsuleText(letter: String) {
        if (letter == capsuleText) return
        capsuleText = letter
        capsuleLayout = if (letter.isEmpty()) {
            null
        } else {
            textMeasurer.measure(
                text = letter,
                style = TextStyle(
                    fontSize = FastScrollerMotion.CAPSULE_TEXT_SIZE,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    /** 停止滚动 / 交互 [FastScrollerMotion.AUTO_HIDE_MS] 后淡出滑块。 */
    fun scheduleAutoHide() {
        hideTimer.renew(FastScrollerMotion.AUTO_HIDE_MS) {
            visible = false
            if (animationsDisabled) {
                thumbAlpha.snapTo(0f)
            } else {
                thumbAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = FastScrollerMotion.THUMB_FADE_MS,
                        easing = FastScrollerMotion.CubicEaseOut,
                    ),
                )
            }
        }
    }

    fun showThumb() {
        if (!visible) {
            visible = true
            scope.launch {
                if (animationsDisabled) {
                    thumbScale.snapTo(1f)
                    thumbAlpha.snapTo(1f)
                    arrowSpread.snapTo(0f)
                } else {
                    val spec = tween<Float>(
                        durationMillis = FastScrollerMotion.THUMB_FADE_MS,
                        easing = FastScrollerMotion.CubicEaseOut,
                    )
                    launch { thumbScale.animateTo(1f, spec) }
                    launch { thumbAlpha.animateTo(1f, spec) }
                    launch { arrowSpread.animateTo(0f, spec) }
                }
            }
        }
        // 每次滚动 / 交互都续期：连续滚动或长按拖动时滑块不得中途淡出。
        // 拖动中不排隐藏——松手时在 finally 里统一续期，否则按住超过 2s 滑块会中途淡出
        if (!dragging) scheduleAutoHide()
    }

    fun showCapsule() {
        // 取消上一次因松手发起的延迟隐藏：松手后 1s 内再点按滑块时，气泡不应
        // 在中途被旧 hide 动作弹走。
        capsuleHideJob?.cancel()
        scope.launch {
            if (animationsDisabled) {
                capsuleTranslateX.snapTo(0f)
                capsuleScaleX.snapTo(1f)
                capsuleAlpha.snapTo(1f)
            } else {
                launch {
                    capsuleTranslateX.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = FastScrollerMotion.CAPSULE_IN_TRANSLATE_MS,
                            easing = FastScrollerMotion.Overshoot,
                        ),
                    )
                }
                launch {
                    capsuleScaleX.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = FastScrollerMotion.CAPSULE_IN_SCALE_MS,
                            easing = FastScrollerMotion.CubicEaseOut,
                        ),
                    )
                }
                launch {
                    capsuleAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = FastScrollerMotion.CAPSULE_IN_ALPHA_MS,
                            easing = FastScrollerMotion.CubicEaseOut,
                        ),
                    )
                }
            }
        }
    }

    // 松手后气泡带着最后那个字母滞留一段时间，再滑出；holdMillis 为 0 时立刻滑出
    // （用户已经改去碰列表了，滞留对他没有意义）
    fun closeCapsule(holdMillis: Long) {
        if (capsuleText.isEmpty()) return
        capsuleHideJob?.cancel()
        capsuleHideJob = scope.launch {
            if (holdMillis > 0L) delay(holdMillis)
            if (animationsDisabled) {
                capsuleTranslateX.snapTo(enterDistancePx)
                capsuleScaleX.snapTo(FastScrollerMotion.CAPSULE_SCALE_START)
                capsuleAlpha.snapTo(0f)
            } else {
                // 等三条动画都落地再往下走：清文本必须在滑出之后，否则滑出的是一次空气泡
                coroutineScope {
                    launch {
                        capsuleTranslateX.animateTo(
                            targetValue = enterDistancePx,
                            animationSpec = tween(
                                durationMillis = FastScrollerMotion.CAPSULE_OUT_TRANSLATE_MS,
                                easing = FastScrollerMotion.ExpEaseIn,
                            ),
                        )
                    }
                    launch {
                        capsuleScaleX.animateTo(
                            targetValue = FastScrollerMotion.CAPSULE_SCALE_START,
                            animationSpec = tween(
                                durationMillis = FastScrollerMotion.CAPSULE_OUT_SCALE_MS,
                                easing = FastScrollerMotion.ExpEaseIn,
                            ),
                        )
                    }
                    launch {
                        capsuleAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = FastScrollerMotion.CAPSULE_OUT_ALPHA_MS,
                                easing = FastScrollerMotion.CubicEaseOut,
                            ),
                        )
                    }
                }
            }
            // 滑出之后再清：下次按下会用新的字母覆盖，留着只是免得透明度回升时冒出旧字母
            refreshCapsuleText("")
        }
    }

    // 松手：留一段时间再滑出
    fun hideCapsule() {
        closeCapsule(FastScrollerMotion.CAPSULE_HOLD_MS)
    }

    // 用户碰了列表：不等滞留，立刻滑出
    fun dismissCapsule() {
        closeCapsule(0L)
    }

    // 手指落下列表（回传的是按下计数，0 是初始值）就把气泡收起来
    LaunchedEffect(listTouchTick) {
        // 按住滑块同样会让计数前进（那一按是滑块手势的起点）：此时气泡属于这次拖动，
        // 由拖动本身决定显示什么，不在这里收
        if (listTouchTick > 0 && !dragging) dismissCapsule()
    }

    // 滚动时唤出滑块。首帧发射是 listState 的初始状态、并非滚动，drop(1) 丢掉它，
    // 否则换页 / 首帧组合都会把滑块在新位置唤出（miuix 库同样 drop(1)）。滑块位置不在这里
    // 同步——它在 draw 里按 layoutInfo 现算
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.drop(1).collect {
            if (!dragging) showThumb()
        }
    }

    // 标定只在滚动停下后做一次：滚动中每帧量一遍可见项（还要现建布局表）是白花的开销，
    // 进度已按 item 数计算，标定值只影响字母跳转的落点，不必跟着帧走
    LaunchedEffect(listState, sections) {
        // 换分组/换页会换一套节表，而实测高度按 item 下标存：不清就会把旧列表某个下标的
        // 高度套到新列表同一位置上，字母跳转与滑块刻度一起偏
        px.resetSections()
        limit.dirty = true
        // 首帧排版还没就绪时，下面那次「未滚动」是空操作：之后若每次都停在大节中段，
        // 视口里没有字母头，头高就一直量不到，前缀和只剩按行高兜底，分母整体偏大。
        // 拿到第一份非空布局先标一次，把字母头换成实测值
        launch {
            // 等一份已经按当前节表排好的非空布局：换表之后先到的那份往往还是旧表的
            snapshotFlow { listState.layoutInfo }
                .first { it.visibleItemsInfo.isNotEmpty() && layoutReady(it) }
            if (!dragging) calibrate(listState.layoutInfo)
        }
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            // 拖滑块时列表被逐帧跳转，中途会短暂变成「未滚动」：这时量到的高度对本次拖动
            // 没用（全程用按下那一刻的前缀和），等真的停下来再标定
            .collect {
                if (!dragging) calibrate(listState.layoutInfo)
            }
    }

    // 换页（listState 变）时收起滑块：新旧列表滚动位置不同，留着会让滑块跳变；
    // 新页第一次真实滚动时再正常唤出
    LaunchedEffect(listState) {
        hideTimer.cancel()
        visible = false
        thumbAlpha.snapTo(0f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 只接受有效尺寸：过渡期量到 0 不能把已量到的高度冲掉，
            // 否则行程变 0，滑块钉在顶部、拖动也被夹死在 0
            .onSizeChanged { if (it.height > 0 && it.width > 0) trackSize = it },
    ) {
        // 画布：全尺寸。滑块贴右缘、气泡在滑块左侧，都在边界内，不溢出也不裁剪
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 位置现算：以画布自身尺寸为轨道，滑块不会被画出可见区。
            // 拖动跟手指、松手停在落点、否则按列表滚动进度连续跟随（见 currentProgress）
            val progress = currentProgress()
            val thumbTop = progress * travel(size.height)
            val thumbLeft = size.width - thumbMarginEndPx - thumbSizePx
            val cx = thumbLeft + thumbSizePx / 2f
            val cy = thumbTop + thumbSizePx / 2f

            if (thumbAlpha.value > 0.01f) {
                val alpha = thumbAlpha.value
                val r = thumbSizePx / 2f * thumbScale.value
                drawCircle(
                    color = fillColor.copy(alpha = alpha),
                    radius = r,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = borderColor.copy(alpha = alpha),
                    radius = r - borderWidthPx / 2f,
                    center = Offset(cx, cy),
                    style = Stroke(width = borderWidthPx),
                )
                // 箭头：扁平小三角（▲ / ▼），九成填充 + 实色描边；按住时上下外扩
                val arrowFill = contentColor.copy(
                    alpha = FastScrollerMotion.ARROW_FILL_ALPHA * alpha,
                )
                val arrowStroke = contentColor.copy(alpha = alpha)
                val spreadPx = arrowSpread.value * density.density / 3f
                drawArrow(
                    cx = cx,
                    cy = cy - arrowHalfWidthPx - spreadPx,
                    up = true,
                    fillColor = arrowFill,
                    strokeColor = arrowStroke,
                    halfWidth = arrowHalfWidthPx,
                    halfHeight = arrowHalfHeightPx,
                    strokeWidth = arrowStrokePx,
                )
                drawArrow(
                    cx = cx,
                    cy = cy + arrowHalfWidthPx + spreadPx,
                    up = false,
                    fillColor = arrowFill,
                    strokeColor = arrowStroke,
                    halfWidth = arrowHalfWidthPx,
                    halfHeight = arrowHalfHeightPx,
                    strokeWidth = arrowStrokePx,
                )
            }

            val text = capsuleText
            val layout = capsuleLayout
            val capAlpha = capsuleAlpha.value
            if (capAlpha > 0.01f && text.isNotEmpty() && layout != null) {
                val left = thumbLeft - capsuleGapPx - capsuleSizePx
                val anchorX = left + capsuleSizePx
                val tx = capsuleTranslateX.value
                val sx = capsuleScaleX.value
                // 以气泡右端为锚点横向缩放（对照参考实现 scale(sx, 1f, left + width, cy)）
                val drawLeft = anchorX + (left - anchorX) * sx + tx
                val drawRight = anchorX + tx
                val radius = CornerRadius(capsuleSizePx / 2f)
                // 气泡垂直夹取：滑块贴到轨道两端时气泡不许越出可见区（顶栏/底栏那两条带），
                // 滑块本身仍然跟手指走，只是标签停在边界内
                val capsuleTop = thumbTop.coerceIn(
                    0f,
                    (trackSize.height - capsuleSizePx).coerceAtLeast(0f),
                )
                drawRoundRect(
                    color = fillColor.copy(alpha = capAlpha),
                    topLeft = Offset(drawLeft, capsuleTop),
                    size = Size(drawRight - drawLeft, capsuleSizePx),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = borderColor.copy(alpha = capAlpha),
                    topLeft = Offset(drawLeft, capsuleTop),
                    size = Size(drawRight - drawLeft, capsuleSizePx),
                    cornerRadius = radius,
                    style = Stroke(width = borderWidthPx),
                )
                drawText(
                    textLayoutResult = layout,
                    color = contentColor.copy(alpha = capAlpha),
                    topLeft = Offset(
                        (drawLeft + drawRight) / 2f - layout.size.width / 2f,
                        capsuleTop + capsuleSizePx / 2f - layout.size.height / 2f,
                    ),
                )
            }
        }

        // 触摸目标：只有滑块圆 + TOUCH_PAD 外扩的一个小盒，隐藏态不占位。
        // 位置同样现算（offset lambda 里读 layoutInfo，布局期观测 → 只重排、不重组），
        // 拖动期间冻结在手指点下处：盒内局部坐标才稳定
        if (visible) {
            Box(
                modifier = Modifier
                    .offset {
                        // 与 Canvas 同一把尺子算滑块顶边，保证摸到的就是看到的
                        val top = currentProgress() *
                            travel(trackSize.height.toFloat())
                        val boxTop = if (dragging) frozenBoxTop else top - touchPadPx
                        val left = trackSize.width - thumbMarginEndPx - thumbSizePx - touchPadPx
                        IntOffset(left.roundToInt(), boxTop.roundToInt())
                    }
                    .size(with(density) { (thumbSizePx + touchPadPx * 2).toDp() })
                    .then(
                        if (contentDescription != null) {
                            Modifier.semantics { this.contentDescription = contentDescription }
                        } else {
                            Modifier
                        },
                    )
                    // key 固定为 Unit：手势检测全程存活，lambda 内读 state 当前值
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!visible) return@awaitEachGesture

                            // 方向锁：等某一轴先越 slop 且占优才定性。
                            // 竖拖 → 滑块接管，之后横滑也不翻页；横拖 → 立即放行给 Pager。
                            val slop = viewConfiguration.touchSlop
                            var vertical = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@awaitEachGesture
                                if (!change.pressed) return@awaitEachGesture
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (abs(dy) > slop && abs(dy) >= abs(dx)) {
                                    vertical = true
                                    break
                                }
                                if (abs(dx) > slop && abs(dx) > abs(dy)) {
                                    return@awaitEachGesture
                                }
                            }
                            if (!vertical) return@awaitEachGesture

                            // 冻结触摸盒位置：手指在轨道内的绝对 Y = frozenBoxTop + 盒内局部 Y。
                            // 盒子若跟着滑块移动，这个基准就会漂，取档全错
                            //
                            // 接续点取滑块的当前视觉位置（[currentProgress]）：跟随动画可能还在追列表，
                            // 视觉位置才是眼睛看到的那一帧，新拖动从那里接续
                            val startProgress = currentProgress()
                            settling = false
                            frozenBoxTop = startProgress * travel(trackSize.height.toFloat()) -
                                touchPadPx
                            // 手指可能落在这个盒子里偏离圆心的位置（盒高 ≈ 1.5 个滑块）：
                            // 以按下点为位移零点，否则第一次移动就把滑块拽到手指上，一按就偏
                            val grabOffsetPx = down.position.y - touchPadPx - thumbSizePx / 2f

                            dragging = true
                            // 列表还在惯性滑行时按住滑块：触摸落在滑块上，列表收不到事件，
                            // 惯性不会自己停。停它的是落地循环按下后的第一帧（见那条循环：
                            // 滑行中即使像素没变也落一次位）。滚动只由那一条循环发出：同一时刻
                            // 两条 Default 优先级的滚动会互相取消，落地循环一旦被取消，这次
                            // 拖动就再也落不进列表
                            dragPrefix = prefixOf()
                            // 上一轮冻结的分母要在这里清掉：松手后 400ms 内重按，那份收尾协程
                            // 已不属于本次会话、不会再清它，留着会让每一次拖动都继承第一次的值
                            dragMaxScroll = null
                            val startMax = maxScrollPx(listState.layoutInfo)
                            dragMaxScroll = startMax
                            // 按下时把「已落地像素」对齐到列表真实位置：滑块视觉位置是逐帧跟随的，
                            // 与列表可能差几十像素；若从 -1 起步，第一帧就会把列表拽到滑块所在处
                            // （表现为一碰滑块整页跳一下，松手又跳回），这里从真值起步就不动
                            val startPixel = heightBefore(
                                until = listState.firstVisibleItemIndex,
                                spacing = listState.layoutInfo.mainAxisItemSpacing,
                            ) + listState.firstVisibleItemScrollOffset
                            dragAppliedPx = startPixel
                            dragTargetPx = startPixel
                            val session = ++dragSession
                            dragProgress = startProgress
                            // 拖动期间不淡出：取消自动隐藏，松手时再续期
                            hideTimer.cancel()
                            showThumb()
                            scope.launch {
                                if (animationsDisabled) {
                                    arrowSpread.snapTo(FastScrollerMotion.ARROW_SPREAD_MAX)
                                } else {
                                    arrowSpread.animateTo(
                                        targetValue = FastScrollerMotion.ARROW_SPREAD_MAX,
                                        animationSpec = tween(
                                            durationMillis = FastScrollerMotion.ARROW_MS,
                                            easing = FastScrollerMotion.CubicEaseOut,
                                        ),
                                    )
                                }
                            }
                            // 按下即出气泡：先显示当前视口最后一项的节字母，拖动后跟随
                            val downLetter = visibleMidSectionLetter()
                            refreshCapsuleText(downLetter)
                            showCapsule()

                            var lastLetter = downLetter
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    val fingerProgress = progressOfFinger(
                                        fingerTrackY = frozenBoxTop + change.position.y -
                                            grabOffsetPx,
                                        trackHeightPx = trackSize.height.toFloat(),
                                    )
                                    // 滑块严格跟手指（连续量）；列表按同一进度无极滚动
                                    val maxScroll = dragMaxScroll ?: maxScrollPx(listState.layoutInfo)
                                    val target = fingerProgress * maxScroll
                                    // 滑块严格跟手指：拖动中它是手指位置的函数，与列表已落到哪里无关
                                    dragProgress = fingerProgress
                                    // 只登记目标：落地交给逐帧循环，同一帧内不再重复跳转
                                    dragTargetPx = target

                                    // 字母取可见区正中间那一行所属节：气泡报的是视线落在哪一节，
                                    // 既不是顶部也不是底部那一项
                                    val letter = visibleMidSectionLetter()
                                    if (letter != lastLetter) {
                                        lastLetter = letter
                                        refreshCapsuleText(letter)
                                    }
                                }
                            } finally {
                                // 松手不清文本：气泡要带着最后那个字母滞留
                                // [FastScrollerMotion.CAPSULE_HOLD_MS] 再滑出（见 [hideCapsule]），
                                // 绘制条件要求文本非空，先清掉就等于让那一秒的气泡空着
                                // 终点用「手指目标 ÷ 冻结的可滚像素」，不读 listProgress()：
                                // 松手这一帧列表还没重排完，它可能仍返回拖动开始前的位置
                                val settleMax = dragMaxScroll
                                // 手指的最终目标：滑块停在手指处，列表随后补到这里，两边才同一位置
                                val settleTarget = dragTargetPx ?: dragAppliedPx
                                dragTargetPx = null
                                // 松手收尾整段挪进协程：这里在 awaitPointerEventScope 内，
                                // 受限挂起，排版要等也只能等在外层。
                                // 整段里每个挂起点之后都要重查会话（[mayFinishDrag]）：滚动、动画
                                // 都要等排版，这段时间用户可能已经按下滑块开了新拖动，旧协程再写
                                // [dragging] / [settling] 就是把新拖动从手指上摘下来
                                scope.launch {
                                    try {
                                        if (!mayFinishDrag(dragSession, session)) return@launch
                                        // 指针在两次帧之间抬起时，落地循环还没取走最后一次目标：
                                        // 不在这里补落位，[to] 就与列表真实进度不一致，
                                        // 切到跟随通道后滑块会退回到「已落地」的位置
                                        if (settleMax != null && settleMax > 0f &&
                                            abs(settleTarget - dragAppliedPx) >= 0.01f
                                        ) {
                                            val (i, o) = itemAtScrollOffset(
                                                settleTarget,
                                                prefix = dragPrefix ?: prefixOf(),
                                            )
                                            listState.scrollToItem(i, o)
                                            if (!mayFinishDrag(dragSession, session)) return@launch
                                            dragAppliedPx = settleTarget
                                        }
                                        if (!mayFinishDrag(dragSession, session)) return@launch
                                        // [currentProgress] 在 settling 期间画的是 settleProgress，
                                        // 它的旧值可能是 0（从未用过），直接切过去会闪到顶部
                                        settleProgress.snapTo(currentProgress())
                                        if (!mayFinishDrag(dragSession, session)) return@launch
                                        // 先挂上收尾通道再撤拖动：两条通道都关着的那一帧，
                                        // [currentProgress] 会落到跟随通道上——它在拖动期间被挂起，
                                        // 值还停在按下前的位置，滑块会闪回拖动起点
                                        settling = true
                                        dragging = false
                                        // 刚发出的 scrollToItem 要等下一次排版才落地，先让过一帧再取终点
                                        withFrameNanos { }
                                        if (!mayFinishDrag(dragSession, session)) return@launch
                                        // 滑块停在手指处：列表已补到同一位置，两边都不再动
                                        val to = if (settleMax != null && settleMax > 0f)
                                            (settleTarget / settleMax).coerceIn(0f, 1f)
                                        else listProgress()
                                        dragProgress = to
                                        followProgress.snapTo(to)
                                        if (!mayFinishDrag(dragSession, session)) return@launch
                                        settling = false
                                        // 松后续期自动隐藏
                                        scheduleAutoHide()
                                        // 冻结值不能清得太早：松手那几帧若立刻重算分母，
                                        // 同一个列表位置算出的进度会整体上跳（滑块看起来闪一下）。
                                        // 越过这几帧再放开，退回跟实测一致的模型
                                        kotlinx.coroutines.delay(FastScrollerMotion.FROZEN_RELEASE_MS)
                                        if (mayFinishDrag(dragSession, session)) {
                                            dragPrefix = null
                                            dragMaxScroll = null
                                        }
                                    } finally {
                                        // 整段都在这一个 try 里：上面的补落位与 [settleProgress] 也是挂起点，
                                        // 协程在那里被取消（换页、离场）时标志不能留在 true。
                                        // 松手后 400ms 内用户又按下时，这两个标志已属于新拖动：
                                        // 只允许清自己那一次的，否则滑块会从新手指上被摘下来
                                        if (mayFinishDrag(dragSession, session)) {
                                            dragging = false
                                            settling = false
                                        }
                                    }
                                }
                                scope.launch {
                                    if (animationsDisabled) {
                                        arrowSpread.snapTo(0f)
                                    } else {
                                        arrowSpread.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = FastScrollerMotion.ARROW_MS,
                                                easing = LinearEasing,
                                            ),
                                        )
                                    }
                                }
                                hideCapsule()
                            }
                        }
                    },
            )
        }
    }
}

/**
 * 画一个小三角箭头（滑块内的 ▲ / ▼）。
 *
 * 扁平宽三角（底宽约为高的 1.67 倍，对齐参考实现 arrowSize = 7dp / 高 4.2dp），
 * 九成实色填充 + 实色细描边、圆角连接：参考截图上箭头是「接近实心但边缘更实」的
 * 小扁平三角，不是实心大三角。
 */
private fun DrawScope.drawArrow(
    cx: Float,
    cy: Float,
    up: Boolean,
    fillColor: Color,
    strokeColor: Color,
    halfWidth: Float,
    halfHeight: Float,
    strokeWidth: Float,
) {
    val path = Path().apply {
        if (up) {
            moveTo(cx, cy - halfHeight)
            lineTo(cx + halfWidth, cy + halfHeight)
            lineTo(cx - halfWidth, cy + halfHeight)
        } else {
            moveTo(cx, cy + halfHeight)
            lineTo(cx + halfWidth, cy - halfHeight)
            lineTo(cx - halfWidth, cy - halfHeight)
        }
        close()
    }
    drawPath(path, color = fillColor)
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = strokeWidth,
            join = StrokeJoin.Round,
            cap = StrokeCap.Round,
        ),
    )
}

/**
 * item 高度标定 + 前缀和：滑块按像素归一的唯一数据源。
 *
 * 全部普通字段，不写快照——查询发生在 draw 里，写快照会逼组件重组；前缀和只在标定值或
 * 节表真的变化时重建（[TOLERANCE] 容差吸收实测微抖），否则返回缓存，省掉重复分配。
 */
internal class ScrollPx {

    /** 字母头整项高度；0 = 未标定（此时按行高兜底）。 */
    private var headerHeight = 0f


    /**
     * 逐 item 实测高度，按 item 下标索引。
     *
     * 同一节里应用名单行与两行混排：节级的一个「行高」标量会随可见行在两个数之间来回翻，
     * 牵连整条前缀和。按 item 记，每一项的高度只由它自己决定。
     */
    private val itemHeights = HashMap<Int, Float>()

    /** [itemHeights] 的变更计数：参与前缀和缓存键，量到新高度就要重建。 */
    private var itemRevision = 0

    /** [itemRevision] 的只读视图：分母的失效条件要用它。 */
    val revision: Int get() = itemRevision

    private var prefix = FloatArray(1)
    private var builtSections: List<AppSection>? = null
    private var builtHeader = -1f
    private var builtHeaderFallback = -1f
    private var builtRowHeight = -1f
    private var builtGap = -1f
    private var builtItemRevision = -1

    /** item 高度；下标与 LazyColumn item 严格一致（见 [AppOrdering.layoutOf]）。 */
    private fun itemHeight(
        index: Int,
        layout: List<AppOrdering.SectionLayout>,
        rowHeightPx: Float,
        gapPx: Float,
        headerFallbackPx: Float,
    ): Float {
        itemHeights[index]?.let { return it }
        val section = AppOrdering.sectionOfItem(layout, index) ?: return rowHeightPx
        val bounds = layout[section]
        return when (index) {
            // 头高优先用实测；还没露过面的头用调用方量出来的兜底值
            bounds.headerIndex -> when {
                headerHeight > 0f -> headerHeight
                headerFallbackPx > 0f -> headerFallbackPx
                else -> rowHeightPx
            }
            bounds.gapIndex -> gapPx
            else -> rowHeightPx
        }
    }

    /**
     * 前缀和：[i] = 前 i 个 item 的高度和（不含间距）。
     *
     * 已量到的 item 取实测值，没量到的按类型兜底（字母头用实测头高、节末间隔用 [gapPx]、
     * 其余按行高）。滚到哪一节，那一节的兜底就被实测替换，其他节不受影响。
     */
    fun prefixOf(
        sections: List<AppSection>,
        rowHeightPx: Float,
        gapPx: Float = 0f,
        headerFallbackPx: Float = 0f,
    ): FloatArray {
        if (builtSections === sections &&
            builtHeader == headerHeight &&
            builtHeaderFallback == headerFallbackPx &&
            builtRowHeight == rowHeightPx &&
            builtGap == gapPx &&
            builtItemRevision == itemRevision
        ) {
            return prefix
        }
        val layout = AppOrdering.layoutOf(sections)
        val total = layout.lastOrNull()?.endIndex ?: 0
        val out = FloatArray(total + 1)
        for (index in 0 until total) {
            out[index + 1] = out[index] + itemHeight(index, layout, rowHeightPx, gapPx, headerFallbackPx)
        }
        prefix = out
        builtSections = sections
        builtHeader = headerHeight
        builtHeaderFallback = headerFallbackPx
        builtRowHeight = rowHeightPx
        builtGap = gapPx
        builtItemRevision = itemRevision
        return out
    }

    fun setHeaderHeight(value: Float) {
        if (value <= 0f || abs(value - headerHeight) <= TOLERANCE) return
        headerHeight = value
        // 头高参与前缀和，分母也得跟着失效：只改字段而让 limit 一直用旧分母，
        // 标定前后同一列表位置算出的进度会不一样
        itemRevision++
    }

    /**
     * 记一个 item 的实测高度——它自己量得，无需由行高反推。
     *
     * 逐 item 各记各的，行高不匀（应用名两行）也不会互相污染。
     */
    fun setItemHeight(index: Int, value: Float) {
        if (index < 0 || value <= 0f) return
        val known = itemHeights[index]
        if (known != null && abs(known - value) <= TOLERANCE) return
        itemHeights[index] = value
        itemRevision++
    }

    /** 节表换了（换分组 / 换页 / 重新加载）：按 item 下标存的高度全部作废。 */
    fun resetSections() {
        itemHeights.clear()
        itemRevision++
    }

    private companion object {
        /** 实测微抖容差（px）：小于它的变化不触发前缀和重建。 */
        const val TOLERANCE = 1f
    }
}

/**
 * `[0, until)` 区间 item 高度和：前缀和取第 `until` 项，再补上 `until` 个间距。
 *
 * 下标夹到前缀和末位，节表比列表短的那一帧也不会越界。
 */
internal fun heightBeforeIn(prefix: FloatArray, spacingPx: Float, until: Int): Float {
    if (until <= 0) return 0f
    val index = until.coerceAtMost(prefix.size - 1)
    return prefix[index] + spacingPx * index
}

/**
 * 布局的 item 总数是否已经对上当前节表。
 *
 * 换节表（换分组、筛选）之后，先到的那份 layoutInfo 往往仍是上一份节表排版的结果；
 * 拿它的下标往新表里记高度，会把旧行高记到字母头或间隔上。
 */
internal fun layoutReadyOf(itemCount: Int, sections: List<AppSection>): Boolean =
    itemCount == (AppOrdering.layoutOf(sections).lastOrNull()?.endIndex ?: 0)

/**
 * 已滚像素：首项之前的高度和减去首项在视口中的偏移。
 *
 * 不带 contentPadding：[LazyListState.scrollToItem] 把项顶边贴到视口 top，padding 只抬高
 * 绘制位置，进了这个式子就等于每次都多算一段，滑块与列表对不上。
 */
internal fun scrolledPxOf(
    firstIndex: Int,
    firstOffsetPx: Int,
    prefix: FloatArray,
    spacingPx: Float,
): Float = heightBeforeIn(prefix, spacingPx, firstIndex) - firstOffsetPx

/**
 * 可滚像素：内容总高 + 上下 padding − 视口高度。
 *
 * 内容不足一屏时差是负的：夹到 0，调用处的 `coerceIn(0f, max)` 与除法都不会撞上空区间。
 */
internal fun maxScrollPxOf(
    contentPx: Float,
    beforePaddingPx: Float,
    afterPaddingPx: Float,
    viewportPx: Float,
): Float = max(0f, contentPx + beforePaddingPx + afterPaddingPx - viewportPx)

/** 进度 = 已滚像素 / 可滚像素；没有可滚范围时一律 0。 */
internal fun listProgressOf(scrolledPx: Float, maxScrollPx: Float): Float =
    if (maxScrollPx <= 0f) 0f else (scrolledPx / maxScrollPx).coerceIn(0f, 1f)

/**
 * 滚动量 → (item 下标, 项内偏移)。
 *
 * 每个 item 的起点 = `prefix[i] + spacing * i`，随下标单调递增：二分取最后一个起点不晚于
 * 目标的 item。拖动时每帧都要算一次，大节里线性扫会白烧掉一帧的预算。
 */
internal fun itemAtScrollOffsetIn(
    prefix: FloatArray,
    spacingPx: Float,
    targetScrolled: Float,
    maxScroll: Float,
): Pair<Int, Int> {
    val clamped = targetScrolled.coerceIn(0f, maxScroll)
    var low = 0
    var high = prefix.size - 2
    var found = 0
    while (low <= high) {
        val mid = (low + high) / 2
        // 第 mid 项的起点不晚于目标，就说明目标已经越过它
        if (prefix[mid] + spacingPx * mid <= clamped) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    val offset = clamped - (prefix[found] + spacingPx * found)
    return found to offset.toInt().coerceAtLeast(0)
}

/** 轨道内绝对 Y → 进度（以滑块中心比对手指位置）。 */
internal fun progressOfFingerIn(
    fingerTrackY: Float,
    trackHeightPx: Float,
    thumbSizePx: Float,
): Float {
    val travel = max(0f, trackHeightPx - thumbSizePx)
    if (travel <= 0f) return 0f
    return ((fingerTrackY - thumbSizePx / 2f) / travel).coerceIn(0f, 1f)
}

/**
 * 松手收尾的准入判定：只有发起它的那一次拖动才允许改拖动态。
 *
 * 松手收尾要等最后一次列表跳转落位，因此是异步的。这段时间里用户可能又按下滑块开了新拖动，
 * 会话号已经前进——此时旧收尾协程必须自行放弃：它再去清 [dragging] / [settling]，会把新拖动
 * 的滑块从手指上摘下来。
 */
internal fun mayFinishDrag(currentSession: Int, ownSession: Int): Boolean =
    currentSession == ownSession

/**
 * 自动隐藏计时：一条长驻协程按「截止时间」等。
 *
 * 列表滚动时每帧都要续期；每帧取消 + 重launch 会不断分配协程对象并反复排程，
 * 是滑块与列表卡顿的来源之一。这里只改一个时间戳，协程自己循环到点。
 */
private class HideTimer(private val scope: CoroutineScope) {

    private var deadline = 0L
    private var job: Job? = null

    /** 续期：[delayMs] 内没有再次调用就执行 [onHide]。 */
    fun renew(delayMs: Long, onHide: suspend () -> Unit) {
        deadline = android.os.SystemClock.uptimeMillis() + delayMs
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                while (true) {
                    val remaining = deadline - android.os.SystemClock.uptimeMillis()
                    if (remaining <= 0L) break
                    delay(remaining)
                }
                try {
                    onHide()
                } catch (e: CancellationException) {
                    // 淡出动画会被下一次 showThumb 的显示动画抢先取消，这个取消只属于动画、
                    // 不属于计时器：协程还活着就继续等新的截止时间，只有作用域取消才结束
                    if (!currentCoroutineContext().isActive) throw e
                }
                // onHide 是挂起的（淡出动画），这期间到来的续期只会推后 deadline：
                // 执行完再按新的截止时间等一次
                if (deadline - android.os.SystemClock.uptimeMillis() <= 0L) break
            }
        }
    }

    /** 取消：拖动开始 / 换页时调用。 */
    fun cancel() {
        job?.cancel()
        job = null
        deadline = 0L
    }
}

/** MIUI 文件管理器逆向参数（dp / sp / ms 原值）。 */
object FastScrollerMotion {
    val THUMB_SIZE = 50.91.dp
    /** 松手后冻结的前缀和/分母再沿用多久（毫秒），越过松手动画那几帧。 */
    const val FROZEN_RELEASE_MS = 400L
    val THUMB_MARGIN_END = 16.dp
    val CAPSULE_SIZE = 50.91.dp
    val CAPSULE_MARGIN_TO_THUMB = 16.73.dp
    val CAPSULE_TEXT_SIZE = 14.55.sp

    /** 箭头：扁平小三角，底宽 7dp（= 2s）/ 高 4.2dp（= 1.2s），s = 3.5dp。 */
    val ARROW_SIZE = 7.dp
    val ARROW_HEIGHT = 4.2.dp
    val ARROW_STROKE = 0.5.dp
    const val ARROW_FILL_ALPHA = 0.9f

    val BORDER_WIDTH = 0.36.dp
    val TOUCH_PAD = 12.dp

    /** 气泡回弹出入的横向距离（参考实现 182 / 3 dp）。 */
    val ENTER_DISTANCE = 60.67.dp

    const val THUMB_FADE_MS = 200
    const val ARROW_MS = 200
    const val CAPSULE_IN_TRANSLATE_MS = 500
    const val CAPSULE_IN_SCALE_MS = 200
    const val CAPSULE_IN_ALPHA_MS = 200
    const val CAPSULE_OUT_TRANSLATE_MS = 400
    const val CAPSULE_OUT_SCALE_MS = 400
    const val CAPSULE_OUT_ALPHA_MS = 300

    /** 每帧向目标推进的剩余距离比例：越大越跟手，越小越平滑。 */
    const val FOLLOW_RATIO = 0.35f

    /** 剩余距离小于此值直接吸附，避免无限逼近。 */
    const val FOLLOW_EPSILON = 1e-6f
    const val AUTO_HIDE_MS = 2_000L
    const val CAPSULE_HOLD_MS = 1_000L
    const val THUMB_SCALE_START = 0.5f
    const val CAPSULE_SCALE_START = 0.36f
    const val ARROW_SPREAD_START = 25f
    const val ARROW_SPREAD_MAX = 5f

    val CubicEaseOut: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
    val ExpEaseIn: Easing = CubicBezierEasing(0.95f, 0.05f, 0.795f, 0.035f)
    val Overshoot: Easing = CubicBezierEasing(0.2f, 1.8f, 0.3f, 1f)
}

/** 主题色（实色，不接玻璃 / 模糊）。 */
object FastScrollerDefaults {
    @Composable
    fun fillColor(): Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainerHighest

    @Composable
    fun borderColor(): Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.outline

    @Composable
    fun contentColor(): Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface
}

/** 滚动期间冻结的可滚像素缓存，见 [FastScroller] 的 `maxScrollPx`。 */
private class ScrollLimit {
    var px: Float = 0f
    var totalCount: Int = -1
    /** 上次算分母时 [ScrollPx] 的标定计数，见 `maxScrollPx`。 */
    var revision: Int = -1
    var dirty: Boolean = true
}
