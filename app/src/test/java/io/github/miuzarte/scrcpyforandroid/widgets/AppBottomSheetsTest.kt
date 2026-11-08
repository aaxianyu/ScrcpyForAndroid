package io.github.miuzarte.scrcpyforandroid.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 面板可见条目：过滤（名称 / 包名 / 键，大小写不敏感）与「收藏优先 + 应用名排序」。
 *
 * 都是纯函数，不碰 Compose / Android，可在纯 JVM 下覆盖。
 */
class AppBottomSheetsTest {

    private fun entry(
        key: String,
        title: String,
        summary: String? = null,
        favorite: Boolean = false,
    ) = AppListEntry(
        key = key,
        title = title,
        summary = summary,
        favorite = favorite,
        onClick = {},
    )

    private fun titles(entries: List<AppListEntry>) = entries.map { it.title }

    // ---------------------------------------------------------------- 最近任务：不排序

    @Test
    fun `a null query keeps the recent-tasks order and favorite flags unused`() {
        // 最近任务的语义是「最近使用」，排序或收藏分区都会破坏它
        val entries = listOf(
            entry("com.z", "Zebra", favorite = true),
            entry("com.a", "Alpha"),
        )
        assertEquals(titles(entries), titles(appSheetVisibleEntries(entries, null)))
    }

    // ---------------------------------------------------------------- 全部应用：过滤

    @Test
    fun `an empty query filters nothing but still sorts`() {
        val entries = listOf(entry("com.z", "Zebra"), entry("com.a", "Alpha"))
        assertEquals(listOf("Alpha", "Zebra"), titles(appSheetVisibleEntries(entries, "")))
    }

    @Test
    fun `search matches the title case-insensitively`() {
        val entries = listOf(entry("com.one", "Settings"), entry("com.two", "Chrome"))
        assertEquals(listOf("Settings"), titles(appSheetVisibleEntries(entries, "set")))
        assertEquals(listOf("Settings"), titles(appSheetVisibleEntries(entries, "SET")))
    }

    @Test
    fun `search matches the package name through the summary`() {
        // summary 展示的就是包名；没有应用名时条目靠它区分
        val entries = listOf(
            entry("com.android.settings", "设置", summary = "com.android.settings"),
            entry("com.android.chrome", "Chrome", summary = "com.android.chrome"),
        )
        assertEquals(listOf("Chrome"), titles(appSheetVisibleEntries(entries, "chrome")))
    }

    @Test
    fun `search matches the entry key`() {
        val entries = listOf(
            entry("com.a", "Alpha"),
            entry("com.b", "Bravo"),
        )
        assertEquals(listOf("Bravo"), titles(appSheetVisibleEntries(entries, "com.b")))
    }

    @Test
    fun `surrounding blanks in the query are ignored`() {
        val entries = listOf(entry("com.a", "Alpha"), entry("com.b", "Bravo"))
        assertEquals(listOf("Alpha"), titles(appSheetVisibleEntries(entries, "  alp  ")))
    }

    @Test
    fun `a query matching nothing yields an empty list`() {
        val entries = listOf(entry("com.a", "Alpha"))
        assertEquals(emptyList<AppListEntry>(), appSheetVisibleEntries(entries, "zzz"))
    }

    // ---------------------------------------------------------------- 全部应用：排序

    @Test
    fun `favorites float above non-favorites`() {
        val entries = listOf(
            entry("com.a", "Alpha"),
            entry("com.z", "Zebra", favorite = true),
        )
        assertEquals(listOf("Zebra", "Alpha"), titles(appSheetVisibleEntries(entries, "")))
    }

    @Test
    fun `each group is ordered by app name`() {
        val entries = listOf(
            entry("com.c", "Charlie"),
            entry("com.b", "Bravo", favorite = true),
            entry("com.a", "Alpha"),
            entry("com.y", "Yankee", favorite = true),
        )
        assertEquals(
            listOf("Bravo", "Yankee", "Alpha", "Charlie"),
            titles(appSheetVisibleEntries(entries, "")),
        )
    }

    @Test
    fun `same-name apps fall back to the package name so the order is stable`() {
        val entries = listOf(
            entry("com.z", "Same"),
            entry("com.a", "Same"),
        )
        assertEquals(listOf("com.a", "com.z"), appSheetVisibleEntries(entries, "").map { it.key })
    }
}
