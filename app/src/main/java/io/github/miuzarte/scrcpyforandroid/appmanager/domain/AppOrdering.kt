package io.github.miuzarte.scrcpyforandroid.appmanager.domain

import com.github.promeg.pinyinhelper.Pinyin

/**
 * 应用清单的全局排序键。
 *
 * 与设备页「全部应用」面板保持同一套可见排序：
 * 1. 主键 = 首个「字母或数字」字符（ASCII 小写 / 拼音首字母 / 其他小写），
 *    无字母数字时垫底（[NO_PRIMARY]）；
 * 2. 次级 = 该字符的档位优先级（ASCII < 中文拼音 < 其他文种）；
 * 3. 再次级 = 全字符的档位化序列；
 * 4. 末位 = 包名小写。
 */
data class AppSortKey(
    val primary: Char,
    val primaryTier: Int,
    val tokens: List<SortToken>,
    val packageLower: String,
)

data class SortToken(val tier: Int, val value: String)

object AppOrdering {

    /** 没有字母/数字字符时的主键垫底值。 */
    const val NO_PRIMARY: Char = Char.MAX_VALUE

    const val TIER_ASCII = 0
    const val TIER_PINYIN = 1
    const val TIER_OTHER = 2

    /** 无字母数字字符时的分组字母（emoji / 特殊符号开头归 `#`）。 */
    const val FALLBACK_SECTION = "#"

    fun sortKey(app: DeviceApp): AppSortKey = sortKey(app.displayLabel, app.packageName)

    /**
     * 排序键的核心实现：只依赖「展示名 + 包名」，与来源无关。
     *
     * 设备侧（应用管理）与本机侧（从本机应用安装的选择器）共用这一套，同一个应用在
     * 两处的位置才一致。
     */
    fun sortKey(label: String, packageName: String): AppSortKey {
        val tokens = label.map { tokenOf(it) }
        val primaryToken = tokens.firstOrNull { it.value.any(Char::isLetterOrDigit) }
        return AppSortKey(
            primary = primaryToken?.value?.firstOrNull(Char::isLetterOrDigit) ?: NO_PRIMARY,
            primaryTier = primaryToken?.tier ?: TIER_OTHER,
            tokens = tokens,
            packageLower = packageName.lowercase(),
        )
    }

    fun compare(a: DeviceApp, b: DeviceApp): Int =
        compareKeys(sortKey(a), sortKey(b))

    fun compareKeys(a: AppSortKey, b: AppSortKey): Int {
        if (a.primary != b.primary) return a.primary.compareTo(b.primary)
        if (a.primaryTier != b.primaryTier) return a.primaryTier.compareTo(b.primaryTier)
        val tokenCount = minOf(a.tokens.size, b.tokens.size)
        for (index in 0 until tokenCount) {
            val ta = a.tokens[index]
            val tb = b.tokens[index]
            if (ta.tier != tb.tier) return ta.tier.compareTo(tb.tier)
            if (ta.value != tb.value) return ta.value.compareTo(tb.value)
        }
        if (a.tokens.size != b.tokens.size) return a.tokens.size.compareTo(b.tokens.size)
        return a.packageLower.compareTo(b.packageLower)
    }

    /** 按排序键稳定排序（同名应用按包名兜底）。 */
    fun sorted(apps: List<DeviceApp>): List<DeviceApp> =
        apps.sortedWith(::compare)

    /**
     * 本机应用排序：与设备侧 [sorted] 同一把尺子。
     *
     * 拼音首字母与英文首字母混排（按排序键主键归组），不是「先 ASCII 再汉字」两坨。
     * 比较键先算一遍缓存起来：每比较一对要算两遍拼音，几百个应用时不缓存会明显卡一下。
     */
    fun sortedLocal(apps: List<LocalApp>): List<LocalApp> {
        val keyed = apps.map { app -> app to sortKey(app.label, app.packageName) }
        return keyed.sortedWith { a, b -> compareKeys(a.second, b.second) }
            .map { it.first }
    }

    /**
     * 展示分组：按排序键主键得到首字母（拼音首字母统一大写，无字母数字归 `#`），
     * 同首字母归一节，节顺序 = 排序键顺序。
     */
    fun sectionLetter(app: DeviceApp): String {
        val key = sortKey(app)
        if (key.primary == NO_PRIMARY) return FALLBACK_SECTION
        return key.primary.uppercaseChar().toString()
    }

    fun groupBySection(apps: List<DeviceApp>): List<AppSection> {
        val sortedApps = sorted(apps)
        if (sortedApps.isEmpty()) return emptyList()
        val sections = mutableListOf<AppSection>()
        var currentLetter = sectionLetter(sortedApps.first())
        var currentApps = mutableListOf<DeviceApp>()
        sortedApps.forEach { app ->
            val letter = sectionLetter(app)
            if (letter != currentLetter) {
                sections += AppSection(currentLetter, currentApps)
                currentLetter = letter
                currentApps = mutableListOf()
            }
            currentApps += app
        }
        sections += AppSection(currentLetter, currentApps)
        return sections
    }

    /**
     * 一节在 `LazyColumn` 里占用的 item 区间：字母头 1 个、应用行各 1 个、节末间隔 1 个。
     *
     * 行数逐节不等，换算只能查表 —— 用乘法会在「系统应用」那种上百行的节上直接错位，
     * 索引条拖一点点就跳到底、显示的字母与实际行对不上。
     */
    data class SectionLayout(
        val headerIndex: Int,
        val firstRowIndex: Int,
        val rowCount: Int,
        val gapIndex: Int,
    ) {
        /** 本节之后第一个 item 的下标。 */
        val endIndex: Int get() = gapIndex + 1
    }

    /** 按列表实际摆放顺序算出每节的 item 区间。 */
    fun layoutOf(sections: List<AppSection>): List<SectionLayout> {
        var next = 0
        return sections.map { section ->
            val header = next
            val firstRow = header + 1
            val gap = firstRow + section.apps.size
            val layout = SectionLayout(header, firstRow, section.apps.size, gap)
            next = gap + 1
            layout
        }
    }

    /** item 下标 → 所属节下标；越界返回 null（调用方负责兜底，不显示错误字母）。 */
    fun sectionOfItem(layout: List<SectionLayout>, itemIndex: Int): Int? {
        var low = 0
        var high = layout.lastIndex
        while (low <= high) {
            val mid = (low + high) / 2
            val section = layout[mid]
            when {
                itemIndex < section.headerIndex -> high = mid - 1
                itemIndex >= section.endIndex -> low = mid + 1
                else -> return mid
            }
        }
        return null
    }

    private fun tokenOf(char: Char): SortToken = when {
        char.code <= 0x7F -> SortToken(TIER_ASCII, char.lowercaseChar().toString())
        Pinyin.isChinese(char) -> SortToken(
            tier = TIER_PINYIN,
            value = Pinyin.toPinyin(char).lowercase(),
        )
        else -> SortToken(TIER_OTHER, char.lowercaseChar().toString())
    }
}

/** 一个字母分组：节标题 + 同节应用（同一张灰色圆角卡片内）。 */
data class AppSection(
    val letter: String,
    val apps: List<DeviceApp>,
)
