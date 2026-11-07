package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * MIUI 悬浮球径向菜单几何（参数来源：com.miui.touchassistant 16.0.1.6.1 逆向）。
 *
 * 渲染、命中判定、边角形态（主球隐藏）判定必须共用 [miuiRadialOffsetsPx] 的
 * 同一次计算——各处各写一份必然漂移，表现为「菜单能弹出但选不中」或
 * 「该隐藏主球时没隐藏」。
 */
internal const val MIUI_RADIAL_MAX = 5

/** 球缘到屏边的留白（MIUI 原版 6.66dp，取整 7dp）：球移动范围、子球落位判定、
 *  边角圆心都按它内缩，整条球串与屏边保持同一间距 */
internal val MIUI_BALL_EDGE_MARGIN = 7.dp

/** 一二级水平弧的跨度（MIUI 原版 170°） */
private const val MIUI_RADIAL_SPAN_DEG = 170f

/** 一二级球心距 = 1.8 × 球径（子球与主球同径时即 R = 1.8 × 子球直径） */
internal const val MIUI_RADIAL_RADIUS_FACTOR = 1.8f

/** 三级边角形态半径 = 4.05 × 球径 */
private const val MIUI_RADIAL_CORNER_RADIUS_FACTOR = 4.05f

/** 三级边角形态步进 18°/球 */
private const val MIUI_RADIAL_CORNER_STEP_DEG = 18f

/**
 * 球数 < 5 时从 5 球位中取中间位（MIUI 原版语义）：
 * 4 球取 -1/-1/3/1/3/1、3 球取 -0.5/0/0.5、2 球取 ±0.5、1 球居中。
 * slot 为相对弧段中心的偏移比例（× span/2 = 实际角偏移）。
 */
private val MIUI_RADIAL_SLOTS = mapOf(
    1 to listOf(0f),
    2 to listOf(-0.5f, 0.5f),
    3 to listOf(-0.5f, 0f, 0.5f),
    4 to listOf(-1f, -1f / 3f, 1f / 3f, 1f),
    5 to listOf(-1f, -0.5f, 0f, 0.5f, 1f),
)

/** 一二级：从 5 球位中取位。
 *  方向语义（用户验收）：屏幕上从上到下对应排序页列表从上到下——
 *  index 0 取弧的最上端；横排弧（旋转 90° fallback）两端等高，取最左端。
 *  弧位置集合不变，只决定 index 映射到弧的哪一端。 */
private fun miuiRadialSlotsAngleDeg(
    index: Int,
    count: Int,
    centerAngleDeg: Float,
    spanDeg: Float,
): Float {
    val slots = MIUI_RADIAL_SLOTS[count] ?: MIUI_RADIAL_SLOTS.getValue(5)
    val slot = slots[index.coerceIn(0, slots.lastIndex)]
    val firstAngleDeg = centerAngleDeg + slots.first() * spanDeg / 2f
    val lastAngleDeg = centerAngleDeg + slots.last() * spanDeg / 2f
    val orientedSlot = if (arcStartsAtReadingOrder(firstAngleDeg, lastAngleDeg)) slot else -slot
    return centerAngleDeg + orientedSlot * spanDeg / 2f
}

/** 三级（边角）：按球数均分跨度（不用球位表——2/3 球按球位表取位会挤成一团）。
 *  与一二级同一读序规则：index 0 在弧的读序起点（最上端，等高取最左端）。 */
private fun miuiRadialCornerAngleDeg(
    index: Int,
    count: Int,
    centerAngleDeg: Float,
    spanDeg: Float,
): Float {
    if (count <= 1) return centerAngleDeg
    val stepDeg = spanDeg / (count - 1)
    val firstAngleDeg = centerAngleDeg - spanDeg / 2f
    val lastAngleDeg = centerAngleDeg + spanDeg / 2f
    return if (arcStartsAtReadingOrder(firstAngleDeg, lastAngleDeg)) {
        firstAngleDeg + index * stepDeg
    } else {
        lastAngleDeg - index * stepDeg
    }
}

/**
 * 弧两端点中 [firstAngleDeg] 是否已是读序起点。
 * 屏幕坐标 y 向下：sin 越小越靠上；两端等高时 cos 越小越靠左。
 */
private fun arcStartsAtReadingOrder(firstAngleDeg: Float, lastAngleDeg: Float): Boolean {
    val firstY = sin(Math.toRadians(firstAngleDeg.toDouble()))
    val lastY = sin(Math.toRadians(lastAngleDeg.toDouble()))
    if (firstY < lastY - 1e-4) return true
    if (firstY > lastY + 1e-4) return false
    val firstX = cos(Math.toRadians(firstAngleDeg.toDouble()))
    val lastX = cos(Math.toRadians(lastAngleDeg.toDouble()))
    return firstX < lastX - 1e-4
}

/**
 * [miuiRadialOffsetsPx] 的结果：各球目标偏移 + 展开形态等级。
 *
 * - [LEVEL_HORIZONTAL] = 1：水平弧（球在左/右缘，弧朝屏心展开）
 * - [LEVEL_ROTATED] = 2：弧旋转 90° 横排（球在上/下缘）
 * - [LEVEL_CORNER] = 3：边角扇形（球在四角）
 *
 * [isCorner] 为 true（即 [LEVEL_CORNER]）时主球应缩放+淡出隐藏（收起时恢复）。
 * 提示落点也按等级分三套规则，必须与渲染/命中同源，否则会漂移。
 */
internal data class MiuiRadialOffsets(
    val offsets: List<Offset>,
    val level: Int,
) {
    val isCorner: Boolean get() = level == LEVEL_CORNER
}

internal const val LEVEL_HORIZONTAL = 1
internal const val LEVEL_ROTATED = 2
internal const val LEVEL_CORNER = 3

/**
 * 径向菜单各球的偏移量，返回**目标球中心相对主球球心**的偏移（px）。
 *
 * 三级 fallback（逐级检测放不下才降级）：
 * 1. 水平弧（170° 跨度，球在右半屏朝左、左半屏朝右）
 * 2. 弧面旋转 90°（横排，跨度仍 170°，方向取能落进屏内的那个）
 * 3. 边角形态：圆心 = 球贴边时的屏幕角落，中心角 = 角落内角平分线（右下 -135°），
 *    半径 4.05×球径，步进 18°/球
 *
 * [edgeInsetPx] 为球缘到屏边的留白（MIUI 原版 6.66dp，取整 7dp）：
 * 子球落位判定与边角圆心都按它内缩，整条球串与屏边保持同一间距。
 *
 * 最后兜底：极窄容器下整条弧平移夹回屏内（保持弧形不变）。
 */
internal fun miuiRadialOffsetsPx(
    count: Int,
    ballSizePx: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    ballLeftPx: Float,
    ballTopPx: Float,
    edgeInsetPx: Float = 0f,
): MiuiRadialOffsets {
    if (count <= 0) return MiuiRadialOffsets(emptyList(), level = LEVEL_HORIZONTAL)
    val half = ballSizePx / 2f
    val dist = ballSizePx * MIUI_RADIAL_RADIUS_FACTOR
    val ballCenterX = ballLeftPx + half
    val ballCenterY = ballTopPx + half

    fun arc(
        centerX: Float,
        centerY: Float,
        centerAngleDeg: Float,
        spanDeg: Float,
        radiusPx: Float,
        slotsBased: Boolean,
    ): List<Offset> =
        (0 until count).map { index ->
            val angleDeg = if (slotsBased) {
                miuiRadialSlotsAngleDeg(index, count, centerAngleDeg, spanDeg)
            } else {
                miuiRadialCornerAngleDeg(index, count, centerAngleDeg, spanDeg)
            }
            val rad = Math.toRadians(angleDeg.toDouble())
            Offset(
                centerX + (radiusPx * cos(rad)).toFloat(),
                centerY + (radiusPx * sin(rad)).toFloat(),
            )
        }

    fun fitsInContainer(centers: List<Offset>): Boolean = centers.all {
        it.x - half >= edgeInsetPx && it.x + half <= containerWidthPx - edgeInsetPx &&
            it.y - half >= edgeInsetPx && it.y + half <= containerHeightPx - edgeInsetPx
    }

    // 一级：水平弧（170° 跨度，与 MIUI 原版一致）。球在右半屏弧朝左（180°）、左半屏朝右（0°）
    val horizontalAngleDeg = if (ballCenterX >= containerWidthPx / 2f) 180f else 0f
    var centers = arc(
        ballCenterX, ballCenterY,
        horizontalAngleDeg,
        MIUI_RADIAL_SPAN_DEG, dist, slotsBased = true,
    )
    var level = LEVEL_HORIZONTAL
    if (!fitsInContainer(centers)) {
        // 二级：弧面旋转 90°（横排）——主球贴上下边时球串沿边缘排开，跨度仍 170°。
        // 旋转方向取能落进屏内的那个（两个都试）。
        var rotatedApplied = false
        for (angle in listOf(horizontalAngleDeg + 90f, horizontalAngleDeg - 90f)) {
            val candidate = arc(
                ballCenterX, ballCenterY, angle,
                MIUI_RADIAL_SPAN_DEG, dist, slotsBased = true,
            )
            if (fitsInContainer(candidate)) {
                centers = candidate
                rotatedApplied = true
                break
            }
        }
        if (!rotatedApplied) {
            // 三级：边角形态——圆心移到主球贴边时的屏幕角落、内角平分线、
            // 半径 4.05×球径、步进 18°/球（角落也按边距内缩）
            val cornerCX = if (ballCenterX >= containerWidthPx / 2f) containerWidthPx - edgeInsetPx else edgeInsetPx
            val cornerCY = if (ballCenterY >= containerHeightPx / 2f) containerHeightPx - edgeInsetPx else edgeInsetPx
            val bisectorDeg = when {
                cornerCX >= containerWidthPx / 2f && cornerCY >= containerHeightPx / 2f -> -135f
                cornerCX >= containerWidthPx / 2f -> 135f
                cornerCY >= containerHeightPx / 2f -> -45f
                else -> 45f
            }
            centers = arc(
                cornerCX, cornerCY,
                bisectorDeg,
                MIUI_RADIAL_CORNER_STEP_DEG * (count - 1),
                ballSizePx * MIUI_RADIAL_CORNER_RADIUS_FACTOR,
                slotsBased = false,
            )
            level = LEVEL_CORNER
        } else {
            level = LEVEL_ROTATED
        }
    }
    // 兜底平移：三级仍越界（极窄容器）时整条弧夹回屏内，保持弧形（按边距内缩）
    val minX = centers.minOf { it.x } - half
    val maxX = centers.maxOf { it.x } + half
    val minY = centers.minOf { it.y } - half
    val maxY = centers.maxOf { it.y } + half
    val shiftX = when {
        minX < edgeInsetPx -> edgeInsetPx - minX
        maxX > containerWidthPx - edgeInsetPx -> -(maxX - (containerWidthPx - edgeInsetPx))
        else -> 0f
    }
    val shiftY = when {
        minY < edgeInsetPx -> edgeInsetPx - minY
        maxY > containerHeightPx - edgeInsetPx -> -(maxY - (containerHeightPx - edgeInsetPx))
        else -> 0f
    }
    return MiuiRadialOffsets(
        offsets = centers.map {
            Offset(it.x + shiftX - ballCenterX, it.y + shiftY - ballCenterY)
        },
        level = level,
    )
}
