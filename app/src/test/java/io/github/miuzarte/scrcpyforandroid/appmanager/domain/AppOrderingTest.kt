package io.github.miuzarte.scrcpyforandroid.appmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderingTest {

    private fun app(
        packageName: String,
        label: String,
        versionName: String = "1.0",
        apkSizeBytes: Long = 1024L,
        isSystem: Boolean = false,
        isEnabled: Boolean = true,
    ) = DeviceApp(packageName, label, versionName, apkSizeBytes, isSystem, isEnabled)

    private fun local(packageName: String, label: String) =
        LocalApp(packageName, label, apkPaths = emptyList(), isSystem = false)

    @Test
    fun `ascii sorts before pinyin and other scripts`() {
        val ordered = AppOrdering.sorted(
            listOf(
                app("c.中文", "中文应用"),
                app("a.other", "Яппи"),
                app("b.ascii", "Beta"),
            ),
        )
        assertEquals(listOf("b.ascii", "c.中文", "a.other"), ordered.map { it.packageName })
    }

    @Test
    fun `pinyin initial is the primary key`() {
        // 张 (zhang) < 李 (li)? 不——按拼音首字母：l < z，李四 应排在 张三 前
        val ordered = AppOrdering.sorted(
            listOf(
                app("p.zhang", "张三"),
                app("p.li", "李四"),
            ),
        )
        assertEquals(listOf("p.li", "p.zhang"), ordered.map { it.packageName })
    }

    @Test
    fun `same pinyin initial falls back to full pinyin sequence`() {
        val ordered = AppOrdering.sorted(
            listOf(
                app("p.zhangsan", "张三"),
                app("p.zhangfei", "张飞"),
            ),
        )
        // fei < san
        assertEquals(listOf("p.zhangfei", "p.zhangsan"), ordered.map { it.packageName })
    }

    @Test
    fun `digits form their own section and sort before letters`() {
        val ordered = AppOrdering.sorted(
            listOf(
                app("p.a", "Alpha"),
                app("p.1", "1Password"),
            ),
        )
        assertEquals(listOf("p.1", "p.a"), ordered.map { it.packageName })
    }

    @Test
    fun `labels without letter or digit fall into hash section and sort last`() {
        val ordered = AppOrdering.sorted(
            listOf(
                app("p.emoji", "🎵"),
                app("p.ascii", "Alpha"),
            ),
        )
        assertEquals(listOf("p.ascii", "p.emoji"), ordered.map { it.packageName })
        assertEquals("#", AppOrdering.sectionLetter(app("p.emoji", "🎵")))
    }

    @Test
    fun `emoji prefixed labels sort by their first letter or digit`() {
        // 主键取首个「字母或数字」字符：emoji 跳过，按其后汉字拼音首字母归组
        val ordered = AppOrdering.sorted(
            listOf(
                app("p.music", "🎵 音乐"),
                app("p.alpha", "Alpha"),
            ),
        )
        assertEquals(listOf("p.alpha", "p.music"), ordered.map { it.packageName })
        assertEquals("Y", AppOrdering.sectionLetter(app("p.music", "🎵 音乐")))
    }

    @Test
    fun `blank label falls back to last package segment`() {
        val fallback = app("com.example.calculator", "")
        assertEquals("calculator", fallback.displayLabel)
        assertEquals("C", AppOrdering.sectionLetter(fallback))
    }

    @Test
    fun `same label ties break by lowercase package name`() {
        val ordered = AppOrdering.sorted(
            listOf(
                app("Zeta.App", "Same"),
                app("alpha.app", "Same"),
            ),
        )
        assertEquals(listOf("alpha.app", "Zeta.App"), ordered.map { it.packageName })
    }

    @Test
    fun `local sorting uses the same ruler as the device side`() {
        val ordered = AppOrdering.sortedLocal(
            listOf(
                local("c.zh", "中文应用"),
                local("a.other", "Яппи"),
                local("b.ascii", "Beta"),
            ),
        )
        assertEquals(listOf("b.ascii", "c.zh", "a.other"), ordered.map { it.packageName })
    }

    @Test
    fun `local sorting mixes pinyin and ascii initials into one sequence`() {
        val ordered = AppOrdering.sortedLocal(
            listOf(
                local("z.alpha", "阿里"),
                local("b.beta", "Beta"),
                local("c.chuang", "创作者"),
                local("a.apple", "Apple"),
            ),
        )
        // 主键同为 A 时按档位分：Apple 是 ASCII 档，阿里是拼音档，ASCII 在前
        assertEquals(listOf("a.apple", "z.alpha", "b.beta", "c.chuang"), ordered.map { it.packageName })
    }

    @Test
    fun `local sorting breaks same-label ties by lowercase package name`() {
        val ordered = AppOrdering.sortedLocal(
            listOf(
                local("com.B.upper", "同名"),
                local("com.a.lower", "同名"),
            ),
        )
        assertEquals(listOf("com.a.lower", "com.B.upper"), ordered.map { it.packageName })
    }

    @Test
    fun `a blank local label has no primary key and sorts last`() {
        // DeviceApp 的构造会把空白展示名补成包名末段，LocalApp 没有这一步：
        // 空白名在本机侧取不到主键，按 NO_PRIMARY 垫底。排序键只吃展示名 + 包名，
        // 兜底发生在 DeviceApp 构造里，不在排序键里。
        val ordered = AppOrdering.sortedLocal(
            listOf(
                local("com.a.apple", ""),
                local("com.m.mango", "芒果"),
                local("com.b.beta", "Beta"),
            ),
        )
        assertEquals(listOf("com.b.beta", "com.m.mango", "com.a.apple"), ordered.map { it.packageName })
        assertEquals(AppOrdering.NO_PRIMARY, AppOrdering.sortKey("", "com.a.apple").primary)
        // 对照：设备侧同一个包名会被 DeviceApp 归一成末段名，因此拿得到主键
        val deviceSide = app("com.example.calculator", "")
        assertEquals("calculator", deviceSide.displayLabel)
        assertEquals('c', AppOrdering.sortKey(deviceSide).primary)
    }

    @Test
    fun `local sorting keeps every entry and leaves the input untouched`() {
        val input = listOf(
            local("m.mango", "芒果"),
            local("a.apple", "Apple"),
            local("b.beta", "Beta"),
        )
        val ordered = AppOrdering.sortedLocal(input)
        assertEquals(listOf("a.apple", "b.beta", "m.mango"), ordered.map { it.packageName })
        assertEquals(listOf("m.mango", "a.apple", "b.beta"), input.map { it.packageName })
    }

    @Test
    fun `local sorting of an empty list stays empty`() {
        assertEquals(emptyList<LocalApp>(), AppOrdering.sortedLocal(emptyList()))
    }

    @Test
    fun `sections keep sort order and group same letter together`() {
        val sections = AppOrdering.groupBySection(
            listOf(
                app("p.b2", "Bravo"),
                app("p.a1", "Alpha"),
                app("p.b1", "Beta"),
                app("p.hash", "🎵"),
            ),
        )
        assertEquals(listOf("A", "B", "#"), sections.map { it.letter })
        assertEquals(listOf("p.a1"), sections[0].apps.map { it.packageName })
        assertEquals(listOf("p.b1", "p.b2"), sections[1].apps.map { it.packageName })
        assertEquals(listOf("p.hash"), sections[2].apps.map { it.packageName })
    }

    @Test
    fun `empty list produces no sections`() {
        assertEquals(emptyList<AppSection>(), AppOrdering.groupBySection(emptyList()))
    }
}
