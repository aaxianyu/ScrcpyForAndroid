package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppOrdering
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppSection
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滑块位置模型的算术核心：item 高度前缀和与两个标定值。
 *
 * 「已滚像素 / 可滚像素」都由这里的前缀和算出，前缀和错了滑块速度就全程不匀。
 * 这一层不碰组合与手势，因此可在纯 JVM 下覆盖。
 *
 * 列表里每节是「1 个字母头 + 每个应用 1 行 + 1 个节末间隔」，行数逐节不等，
 * item 下标的换算只能查 [AppOrdering.layoutOf] 出来的表。
 */
class ScrollPxTest {

    private fun section(letter: String, appCount: Int) =
        AppSection(letter, List(appCount) { DeviceApp("com.$letter$it", "App$it", "1.0", 1024L, false, true) })

    /** A 节 2 行、B 节 3 行；配 68 的头、8 的间隔后总高 652。 */
    private fun sections() = listOf(section("A", 2), section("B", 3))

    private fun headerHeightPx() = 68f
    private fun gapPx() = 8f

    private fun assertArray(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.toList(), actual.toList())
    }

    // ---------------------------------------------------------------- 前缀和

    @Test
    fun `the prefix sums header rows and the section gap`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        // 头68 + 2 行各 100 + 间隔 8 + 头68 + 3 行各 100 + 间隔 8
        assertArray(
            floatArrayOf(0f, 68f, 168f, 268f, 276f, 344f, 444f, 544f, 644f, 652f),
            px.prefixOf(sections(), 100f, gapPx()),
        )
    }

    @Test
    fun `before calibration the header falls back to the row height`() {
        val px = ScrollPx()
        assertArray(
            floatArrayOf(0f, 100f, 200f, 300f, 308f, 408f, 508f, 608f, 708f, 716f),
            px.prefixOf(sections(), 100f, gapPx()),
        )
    }

    @Test
    fun `the prefix has one more slot than there are items and starts at zero`() {
        val px = ScrollPx()
        val layout = AppOrdering.layoutOf(sections())
        val prefix = px.prefixOf(sections(), 100f, gapPx())
        assertEquals(layout.last().endIndex + 1, prefix.size)
        assertEquals(0f, prefix.first(), 0f)
    }

    @Test
    fun `no sections yields a single zero slot`() {
        val px = ScrollPx()
        assertArray(floatArrayOf(0f), px.prefixOf(emptyList(), 100f, gapPx()))
    }

    @Test
    fun `a section with no apps still contributes its header and gap`() {
        val px = ScrollPx()
        px.setHeaderHeight(50f)
        assertArray(floatArrayOf(0f, 50f, 58f), px.prefixOf(listOf(section("Z", 0)), 100f, gapPx()))
    }

    // ---------------------------------------------------------------- 前缀和缓存

    @Test
    fun `an unchanged calibration reuses the same prefix array`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        // 缓存键覆盖全部参与计算的量：节表实例、实测头高、兜底头高、行高、节末间隔、
        // 实测高度计数。生产里这些量逐帧不变，所以每帧取前缀和命中缓存；重建会带来
        // 数十个元素的分配压力。
        val secs = sections()
        val first = px.prefixOf(secs, 100f, gapPx())
        assertSame(first, px.prefixOf(secs, 100f, gapPx()))
    }

    @Test
    fun `a calibration change within one pixel keeps the cached array`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        val secs = sections()
        val first = px.prefixOf(secs, 100f, gapPx())
        px.setHeaderHeight(68.5f)
        assertSame(first, px.prefixOf(secs, 100f, gapPx()))
    }

    @Test
    fun `a calibration change beyond one pixel rebuilds the prefix`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        // 同一个列表实例：重建只能来自头高变化，不能是节表换了对象
        val secs = sections()
        val first = px.prefixOf(secs, 100f, gapPx())
        px.setHeaderHeight(72f)
        val second = px.prefixOf(secs, 100f, gapPx())
        assertNotSame(first, second)
        assertArray(
            floatArrayOf(0f, 72f, 172f, 272f, 280f, 352f, 452f, 552f, 652f, 660f),
            second,
        )
    }

    @Test
    fun `a different section list rebuilds the prefix`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        val first = px.prefixOf(sections(), 100f, gapPx())
        assertNotSame(first, px.prefixOf(listOf(section("A", 5)), 100f, gapPx()))
    }

    @Test
    fun `a different row height rebuilds the prefix`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        val secs = section("A", 2)
        val first = px.prefixOf(listOf(secs), 100f, gapPx())
        val rebuilt = px.prefixOf(listOf(secs), 120f, gapPx())
        assertNotSame(first, rebuilt)
        assertEquals(headerHeightPx() + 120f, rebuilt[2], 0f)
    }

    @Test
    fun `a different gap rebuilds the prefix`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        val secs = sections()
        val first = px.prefixOf(secs, 100f, gapPx())
        assertNotSame(first, px.prefixOf(secs, 100f, 16f))
    }

    // ---------------------------------------------------------------- 标定门控

    @Test
    fun `a non-positive header measurement leaves the header on the row-height fallback`() {
        val px = ScrollPx()
        px.setHeaderHeight(0f)
        // 字母头仍按行高兜底（不是 0 高度）
        assertArray(
            floatArrayOf(0f, 100f, 200f, 300f, 308f, 408f, 508f, 608f, 708f, 716f),
            px.prefixOf(sections(), 100f, gapPx()),
        )
    }

    // ---------------------------------------------------------------- 逐节实测行高

    @Test
    fun `a measured row height overrides the fallback for that section only`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        val secs = sections()
        // B 节的行实测 120（应用名两行）：3 行共 360
        px.setItemHeight(5, 120f)
        px.setItemHeight(6, 120f)
        px.setItemHeight(7, 120f)
        assertArray(
            floatArrayOf(0f, 68f, 168f, 268f, 276f, 344f, 464f, 584f, 704f, 712f),
            px.prefixOf(secs, 100f, gapPx()),
        )
    }

    @Test
    fun `an unmeasured section keeps the fallback row height`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        px.setItemHeight(5, 120f)
        px.setItemHeight(6, 120f)
        px.setItemHeight(7, 120f)
        val secs = listOf(section("A", 2), section("B", 3), section("C", 1))
        val prefix = px.prefixOf(secs, 100f, gapPx())
        // A 68+200+8  B 68+360+8  C 68+100+8
        assertEquals(68f + 200f + 8f + 68f + 360f + 8f + 68f + 100f + 8f, prefix.last(), 0f)
    }

    @Test
    fun `each section's height is decided by its own rows`() {
        // B 的实测行高只影响 B 自己：C 仍按默认行高走
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        val secs = listOf(section("A", 2), section("B", 3), section("C", 1))
        px.setItemHeight(5, 120f)
        px.setItemHeight(6, 120f)
        px.setItemHeight(7, 120f)
        val prefix = px.prefixOf(secs, 100f, gapPx())
        assertEquals("C 节仍按 100 走，不被 B 的实测行高带偏", 68f + 200f + 8f + 68f + 360f + 8f + 68f + 100f + 8f, prefix.last(), 0f)
    }

    @Test
    fun `resetting sections drops measured row heights`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        px.setItemHeight(5, 120f)
        px.setItemHeight(6, 120f)
        px.setItemHeight(7, 120f)
        px.resetSections()
        assertArray(
            floatArrayOf(0f, 68f, 168f, 268f, 276f, 344f, 444f, 544f, 644f, 652f),
            px.prefixOf(sections(), 100f, gapPx()),
        )
    }

    @Test
    fun `measured heights are keyed by index so a table swap must reset them`() {
        // 这条守的是 [ScrollPx.resetSections] 的必要性：高度按 item 下标存，换分组时同一个
        // 下标指向别的节，不重置就会把旧行高套到新表的字母头或间隔上
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        // B 节的节末间隔（下标 5）被量成 120
        px.setItemHeight(5, 120f)
        val other = listOf(section("A", 1), section("B", 1), section("C", 1))
        assertEquals(
            "下标 5 在新表里是间隔，却沿用了旧表的 120：三个节各 176 / 288 / 176",
            640f,
            px.prefixOf(other, 100f, gapPx()).last(),
            0f,
        )
        px.resetSections()
        assertEquals(
            "清空后三个节各 68+100+8",
            528f,
            px.prefixOf(other, 100f, gapPx()).last(),
            0f,
        )
    }

    @Test
    fun `a row height change rebuilds the cached prefix`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        val secs = sections()
        val first = px.prefixOf(secs, 100f, gapPx())
        px.setItemHeight(1, 90f)
        assertNotSame(first, px.prefixOf(secs, 100f, gapPx()))
    }

    @Test
    fun `a repeated row measurement within one pixel keeps the cached prefix`() {
        val px = ScrollPx()
        px.setHeaderHeight(headerHeightPx())
        px.setItemHeight(1, 90f)
        val secs = sections()
        val first = px.prefixOf(secs, 100f, gapPx())
        px.setItemHeight(1, 90.5f)
        assertSame(first, px.prefixOf(secs, 100f, gapPx()))
    }

    // ---------------------------------------------------------------- 节 ↔ item 下标

    @Test
    fun `each section occupies header rows and a trailing gap`() {
        val layout = AppOrdering.layoutOf(sections())
        assertEquals(AppOrdering.SectionLayout(headerIndex = 0, firstRowIndex = 1, rowCount = 2, gapIndex = 3), layout[0])
        assertEquals(AppOrdering.SectionLayout(headerIndex = 4, firstRowIndex = 5, rowCount = 3, gapIndex = 8), layout[1])
        assertEquals(4, layout[0].endIndex)
        assertEquals(9, layout[1].endIndex)
    }

    @Test
    fun `every item inside a section maps back to it including the gap`() {
        val layout = AppOrdering.layoutOf(sections())
        for (index in 0..3) assertEquals("A item $index", 0, AppOrdering.sectionOfItem(layout, index))
        for (index in 4..8) assertEquals("B item $index", 1, AppOrdering.sectionOfItem(layout, index))
    }

    @Test
    fun `an item outside the list maps to no section`() {
        val layout = AppOrdering.layoutOf(sections())
        assertNull(AppOrdering.sectionOfItem(layout, -1))
        assertNull(AppOrdering.sectionOfItem(layout, 9))
        assertNull(AppOrdering.sectionOfItem(emptyList(), 0))
    }

    // ---------------------------------------------------------------- 位置模型

    @Test
    fun `height before the first item is zero`() {
        val prefix = floatArrayOf(0f, 68f, 168f, 268f)
        assertEquals(0f, heightBeforeIn(prefix, 8f, 0), 0f)
        assertEquals(0f, heightBeforeIn(prefix, 8f, -3), 0f)
    }

    @Test
    fun `height before an item adds one spacing per item in front of it`() {
        val prefix = floatArrayOf(0f, 68f, 168f, 268f)
        assertEquals(268f + 24f, heightBeforeIn(prefix, 8f, 3), 0f)
    }

    @Test
    fun `height before clamps past the end of the prefix`() {
        val prefix = floatArrayOf(0f, 68f, 168f, 268f)
        assertEquals(268f + 24f, heightBeforeIn(prefix, 8f, 99), 0f)
        assertEquals(0f, heightBeforeIn(floatArrayOf(0f), 8f, 5), 0f)
    }

    @Test
    fun `scrollable pixels keep both paddings and drop them when content fits`() {
        assertEquals(3397f, maxScrollPxOf(5000f, 277f, 120f, 2000f), 0.001f)
        assertEquals(0f, maxScrollPxOf(100f, 277f, 120f, 2000f), 0.001f)
        assertEquals(0f, maxScrollPxOf(0f, 0f, 0f, 0f), 0.001f)
    }

    @Test
    fun `progress divides by the scrollable pixels and never leaves the unit range`() {
        assertEquals(0.25f, listProgressOf(100f, 400f), 0.001f)
        assertEquals(1f, listProgressOf(900f, 400f), 0.001f)
        assertEquals(0f, listProgressOf(-40f, 400f), 0.001f)
    }

    @Test
    fun `progress without a scrollable range is zero`() {
        assertEquals(0f, listProgressOf(100f, 0f), 0.001f)
        assertEquals(0f, listProgressOf(100f, -1f), 0.001f)
    }

    @Test
    fun `the item at a scroll offset owns the pixels from its own start to the next one`() {
        val prefix = floatArrayOf(0f, 100f, 200f, 300f, 400f)
        assertEquals(0 to 50, itemAtScrollOffsetIn(prefix, 10f, 50f, 1000f))
        // 第二项起点是 100 + 10 个间距：100 还落在第一项里
        assertEquals(0 to 100, itemAtScrollOffsetIn(prefix, 10f, 100f, 1000f))
        assertEquals(1 to 0, itemAtScrollOffsetIn(prefix, 10f, 110f, 1000f))
        assertEquals(2 to 30, itemAtScrollOffsetIn(prefix, 10f, 250f, 1000f))
    }

    @Test
    fun `the item lookup clamps to the scrollable range like the list does`() {
        val prefix = floatArrayOf(0f, 100f, 200f)
        // 目标超出可滚范围时按上界算：最后一项吃掉剩下的像素
        assertEquals(1 to 30, itemAtScrollOffsetIn(prefix, 10f, 999f, 140f))
        assertEquals(0 to 0, itemAtScrollOffsetIn(prefix, 10f, -50f, 140f))
    }

    @Test
    fun `the binary search agrees with a linear scan of the same model`() {
        val prefix = FloatArray(41) { it * 75.5f }
        for (target in 0..3100 step 7) {
            val clamped = target.toFloat().coerceIn(0f, 3000f)
            var linear = 0
            while (linear + 1 < prefix.size && prefix[linear + 1] + 6f * (linear + 1) <= clamped) linear++
            assertEquals(linear, itemAtScrollOffsetIn(prefix, 6f, clamped, 3000f).first)
        }
    }

    @Test
    fun `finger progress is measured from the thumb centre along the track`() {
        // 轨道 1000、滑块 140：可走 860，中心线从 70 到 930
        assertEquals(0f, progressOfFingerIn(70f, 1000f, 140f), 0.001f)
        assertEquals(0.5f, progressOfFingerIn(500f, 1000f, 140f), 0.001f)
        assertEquals(1f, progressOfFingerIn(930f, 1000f, 140f), 0.001f)
        assertEquals(1f, progressOfFingerIn(5000f, 1000f, 140f), 0.001f)
    }

    @Test
    fun `finger progress is zero when the track is shorter than the thumb`() {
        assertEquals(0f, progressOfFingerIn(10f, 100f, 140f), 0.001f)
        assertEquals(0f, progressOfFingerIn(10f, 140f, 140f), 0.001f)
    }

    // ---------------------------------------------------------------- 已滚像素 / 会话归属

    @Test
    fun `scrolled pixels are the height before the first item minus its offset`() {
        val prefix = floatArrayOf(0f, 100f, 200f, 300f)
        // 首项顶边被推到视口上方 40px（偏移 -40）：已滚 40
        assertEquals(40f, scrolledPxOf(0, -40, prefix, 10f), 0.001f)
        // 第 2 项贴顶（偏移 0）：已滚 = 前两项高度和 200 + 两项间距 20
        assertEquals(220f, scrolledPxOf(2, 0, prefix, 10f), 0.001f)
        // 第 2 项被推上去 340：梯形高度和再加上这 340
        assertEquals(560f, scrolledPxOf(2, -340, prefix, 10f), 0.001f)
    }

    @Test
    fun `scrolled pixels start at zero on the top item with no padding`() {
        // 列表顶端：首项贴顶、没有任何偏移，已滚就是 0——contentPadding 不参与这个式子，
        // 否则顶端的进度会被抬到 padding / max，滑块贴不到轨道顶端
        val prefix = floatArrayOf(0f, 400f, 800f)
        assertEquals(0f, scrolledPxOf(0, 0, prefix, 0f), 0.001f)
        assertEquals(0f, listProgressOf(scrolledPxOf(0, 0, prefix, 0f), 3397f), 0.001f)
    }

    @Test
    fun `scrolled pixels clamp at the top of the list`() {
        val prefix = floatArrayOf(0f, 100f)
        // 列表被过度滚动（负偏移）时不产生负的已滚像素
        assertEquals(0f, listProgressOf(scrolledPxOf(0, 60, prefix, 0f), 1000f), 0.001f)
    }

    @Test
    fun `a layout only counts once its item count matches the section table`() {
        // 两节：头 + 行 + 间隔 逐项计，A 节 2 行、B 节 3 行 ⇒ 2 + 5 + 2 = 9
        val secs = sections()
        assertEquals(9, AppOrdering.layoutOf(secs).last().endIndex)
        assertTrue(layoutReadyOf(9, secs))
        // 换表后先到的那份布局仍是旧表的项数：此时记高度会把旧行高套到新表的下标上
        assertFalse(layoutReadyOf(8, secs))
        assertFalse(layoutReadyOf(10, secs))
        assertFalse(layoutReadyOf(0, secs))
    }

    @Test
    fun `a session only finishes its own drag`() {
        assertTrue(mayFinishDrag(4, 4))
        // 松手收尾是异步的：期间用户又按下，会话号已经加过，旧协程不许再动标志
        assertFalse(mayFinishDrag(5, 4))
        assertFalse(mayFinishDrag(3, 4))
    }
}
