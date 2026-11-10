package io.github.miuzarte.scrcpyforandroid.appmanager.domain

/**
 * 列表行第一行尾部角标。
 *
 * 三种状态互斥，判定见 [appBadge]：已停用与运行中同时成立时只显示已停用。
 */
enum class AppBadge {
    /** 既没停用也没有进程在跑（含「运行态未知」：未知不当作运行中，见 [appBadge] 的调用方） */
    None,

    /** 已停用：持久事实，优先级最高 */
    Disabled,

    /** 有进程在跑：瞬时快照 */
    Running,
}

/**
 * 角标互斥判定：**已停用优先**。
 *
 * 停用是持久事实、运行是某一刻的快照，两者同时成立时信持久的那个——用户看到的「已停用」
 * 与「强制停止置灰」必须来自同一次判定，否则会出现「标着已停用但能强制停止」的自相矛盾。
 */
fun appBadge(isEnabled: Boolean, isRunning: Boolean): AppBadge = when {
    !isEnabled -> AppBadge.Disabled
    isRunning -> AppBadge.Running
    else -> AppBadge.None
}

private val PS_WHITESPACE = Regex("\\s+")

/** 进程名里的子进程后缀分隔符，如 `com.miui.home:remote`。 */
private const val PS_PROCESS_SUFFIX = ":"

/**
 * `ps -A` 输出 → 有进程在跑的应用包名集合；**读不出进程表时返回 null**。
 *
 * 返回 null 与空集合必须分开：空集合意味着「确实没有应用在跑」（于是全部应用的强制停止都要
 * 置灰），而读失败只是「这次没读到」，此时强制停止仍然可点、也不显示「运行中」标签。
 * 把两者混成一个空集合，换一台 ROM 改了 `ps -A` 列名之后，用户会看到所有应用都不能强制
 * 停止，而且界面不会报错。
 *
 * 与进程管理页的 `pages/UtilityToolResults.kt` 里的解析分开写：那份要 RSS、要按降级口径
 * 保留缺列行，服务于「列出进程」；这里只要「哪些包有进程」，多带一列解析反而会把自己的
 * 失败条件绑在别的页面的展示需求上。两处口径必须一致的是**子进程归组**（[PS_PROCESS_SUFFIX]）：
 * `am force-stop <包名>` 一次收掉该包全部进程，所以 `com.foo:remote` 必须算作 `com.foo` 在跑。
 *
 * 名字列取末列（`ps` 的 NAME 是最后一列，进程名里可能带空格）；重复表头行与空白行跳过。
 */
fun runningPackagesFromPs(output: String): Set<String>? {
    val lines = output.trim().lines()
    val header = lines.firstOrNull().orEmpty().trim().split(PS_WHITESPACE)
    val pidIndex = header.indexOf("PID")
    val nameIndex = header.indexOf("NAME")
    if (pidIndex < 0 || nameIndex < 0) return null
    val packages = LinkedHashSet<String>()
    for (line in lines.drop(1)) {
        if (line.isBlank()) continue
        val cells = line.trim().split(PS_WHITESPACE)
        // 重复表头行（首格与表头首格相同）跳过：某些通道会把表头重新拼进输出
        if (cells.isEmpty() || cells.first() == header.first()) continue
        // 先判列数再取列：输出被截断、长进程名换行时会出现只有一两格的短行，
        // 此时 cells[pidIndex] 会直接越界崩在加载协程里（ps 读失败还能降级，解析崩掉就整次读不到）。
        if (cells.size <= pidIndex || cells.size <= nameIndex) continue
        // PID 读不出的行不是数据行
        if (cells[pidIndex].toIntOrNull() == null) continue
        val name = cells.drop(nameIndex).joinToString(" ").trim()
        if (name.isEmpty()) continue
        packages += name.substringBefore(PS_PROCESS_SUFFIX)
    }
    return packages
}
