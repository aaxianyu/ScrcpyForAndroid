package io.github.miuzarte.scrcpyforandroid.pages

import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

internal class AdbPortReconnectFailure(cause: Throwable) : Exception(cause.message, cause)

internal suspend fun reconnectAdbPort(
    host: String,
    port: Int,
    waitForRestart: suspend () -> Unit = { delay(2000) },
    connect: suspend (String, Int) -> Unit,
) {
    repeat(3) { attempt ->
        waitForRestart()
        try {
            connect(host, port)
            return
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            if (attempt == 2) throw error
        }
    }
}

internal fun DeviceShortcuts.withAdbPort(target: ConnectionTarget, port: Int): DeviceShortcuts =
    DeviceShortcuts(map { device ->
        device.copy(addresses = device.addresses.map { address ->
            if (ConnectionTarget.unmarshalFrom(address) == target) target.copy(port = port).toString()
            else address
        }.distinct())
    })

internal fun normalizeToolValue(input: String, range: IntRange): String? =
    input.toIntOrNull()?.takeIf { it in range }?.toString()

internal fun isTcpipSuccess(reply: String, port: Int): Boolean =
    reply.trim() == "restarting in TCP mode port: $port"

internal data class StartupAttempt(val exitCode: Int?, val output: String)

// shell v1 无协议级退出码，只能靠约定标记回传；解析取最后一次出现，并要求其后整段就是退出码。
// 已知例外：脚本输出恰好以同形标记行结尾、且 printf 未执行时，二者无法区分。
private const val STARTUP_EXIT_MARKER = "__SCRCPY_STARTUP_EXIT__:"

internal fun startupCommand(script: String): String =
    "( $script ); result=\$?; printf '\\n$STARTUP_EXIT_MARKER%s\\n' \"\$result\""

internal fun parseStartupAttempt(reply: String): StartupAttempt {
    val text = reply.trimEnd()
    // printf 会在标记行前补 \n，但 trim 后整条回复可能只剩标记行本身
    val atLineStart = text.startsWith(STARTUP_EXIT_MARKER)
    val marker = text.lastIndexOf("\n$STARTUP_EXIT_MARKER")
    if (!atLineStart && marker < 0) return StartupAttempt(null, text.trim())
    val lineStart = if (atLineStart) 0 else marker + 1
    val code = text.substring(lineStart + STARTUP_EXIT_MARKER.length)
        .toIntOrNull()?.takeIf { it in 0..255 }
        ?: return StartupAttempt(null, text.trim())
    return StartupAttempt(code, if (lineStart == 0) "" else text.substring(0, marker).trim())
}

internal suspend fun tryStartupScripts(
    scripts: List<String>,
    shell: suspend (String) -> String,
): StartupAttempt {
    var last = StartupAttempt(null, "")
    for (script in scripts) {
        last = parseStartupAttempt(shell(startupCommand(script)))
        // shell v1 没有退出码；缺少尾标记时状态未知，不重复启动另一个入口。
        if (last.exitCode == null || last.exitCode == 0) break
    }
    return last
}

/**
 * 设备上的一条应用进程。
 *
 * [label] 是应用显示名，取它要设备侧 `PackageManager`，由探测程序按包名回传；探测没跑成或
 * 没返回时为 null，界面回落到 [packageName]。图标同理，见 [ProcessIcon]。
 */
internal data class DeviceProcess(
    val packageName: String,
    val pid: Int,
    val rssKb: Long,
    /** 进程名出现在 `pm list packages -s` 的结果里即为系统应用 */
    val isSystem: Boolean = false,
    val label: String? = null,
    val icon: ProcessIcon? = null,
)

/** 一条进程对应的图标：探测回来的原始字节与它的实际编码。 */
internal data class ProcessIcon(val bytes: ByteArray, val suffix: String) {
    // ByteArray 是数组，equals 按引用比会让两个内容相同的数据类不相等
    override fun equals(other: Any?): Boolean =
        this === other || (other is ProcessIcon && bytes.contentEquals(other.bytes) && suffix == other.suffix)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + suffix.hashCode()
}

/**
 * 探测回传的图标字节 → 进程图标。
 *
 * 字节由 [io.github.miuzarte.scrcpyforandroid.appmanager.data.ProbePayloadParser] 解好了，
 * 这里只丢掉空的：探测端在应用没有可用图标时会给零长字节，留着会让列表出现一个永远解不出
 * 内容的占位图标。编码名一并带上——落盘后缀与解码都要按它来。
 */
internal fun parseProcessIcons(
    icons: Map<String, ByteArray>,
    iconSuffix: String,
): Map<String, ProcessIcon> =
    icons.mapNotNull { (packageName, bytes) ->
        if (bytes.isEmpty()) null else packageName to ProcessIcon(bytes, iconSuffix)
    }.toMap()

/** 探测回传的应用显示名，包名为键；空名不算有名字，界面会回落到包名。 */
internal fun parseProcessLabels(apps: List<DeviceApp>): Map<String, String> =
    apps.mapNotNull { app -> app.label.takeIf { it.isNotBlank() }?.let { app.packageName to it } }.toMap()

private val WHITESPACE = Regex("\\s+")

/** `ps -A` 的表头认不出来：这份输出不是一份能读的进程表，和「没有应用在跑」不是一回事。 */
internal class ProcessListUnsupportedException : Exception("ps -A table header not recognized")

/**
 * `pm list packages` 的输出认不出来：包集合读失败时若当成空集合，所有进程都会被归类阶段丢掉，
 * 最终显示成「没有正在运行的应用」，而实际是这次读取没成。
 */
internal class PackageListUnavailableException : Exception("pm list packages output not recognizable")

/**
 * 这份输出是不是一份认得出的 `ps -A` 表格。
 *
 * 表头里找不到 PID / RSS / NAME 就整段不可解析。这里单独给一个判断，是为了让调用方能把
 * 「读不出来」和「真的没有应用在跑」分开：两者都表现为空列表，混在一起的话，换一台 ROM
 * 改了 `ps -A` 的列名，用户看到的就是「这台设备没有应用在跑」，而且不报错。
 */
internal fun hasProcessTableHeader(output: String): Boolean {
    val header = output.trim().lines().firstOrNull().orEmpty().trim().split(WHITESPACE)
    return header.indexOf("PID") >= 0 && header.indexOf("RSS") >= 0 && header.indexOf("NAME") >= 0
}

/**
 * 解析一份 `ps -A` 的结果。
 *
 * [rows] 含完整行与降级行；[droppedRows] 是「看着像数据行、但取值读不出」的行数——回读路径
 * 上它必须为 0 才能下结论，否则丢掉的行如果恰好是目标包，会把「还在跑」判成「已结束」。
 * 表头认不出 PID / RSS / NAME 时整体为 null（读不出来，与「真的没有进程」分开）。
 */
internal data class ProcessTableParse(
    val rows: List<DeviceProcess>,
    /** 列数少于表头、但按降级口径仍读出取值的行数（取值可靠性低于完整行，如实计数） */
    val degradedRows: Int,
    /** 丢弃的行数（缺列太多 / PID 读不出 / 名字位读不出） */
    val droppedRows: Int,
)

/**
 * 解析 `ps -A`，只做取列与格式校验。
 *
 * 列按**表头里的序号**取，不按字符偏移、也不写死列号：
 * - 写死列号在列序变化时会把 VSZ 当 RSS、PID 当 PPID，而且不报错；
 * - 按字符偏移同样错：数值列是右对齐的，表头里 `PID` 从第 15 列起，而数据里 PID 最宽能到
 *   第 14 列起，按偏移切会把 `3424` 读成 `424`。
 * 序号只依赖「这一列是第几个」，左右对齐和列宽都不影响。
 *
 * 表头认不出 PID / RSS / NAME 返回 null，不猜（与 [hasProcessTableHeader] 同条件）。
 * 数据行列数**少于**表头时分两种口径：
 * - 少得不多（≤ [MAX_MISSING_COLUMNS] 列，如部分 ROM 的 WCHAN / ADDR 为空列）按**降级行**
 *   接受并计数——这类行以前被整行丢掉，列表少显示、回读还会被放大成「无法确认」，功能
 *   整体不可用；现在能读则读；
 * - 少得太多或取值不像正常行（截断等）的仍丢弃并计数（见 [parseDataRow]）。
 * 空白行与重复表头行既不算行也不算丢弃。
 */
internal fun parseProcessTable(output: String): ProcessTableParse? {
    val lines = output.trim().lines()
    val header = lines.firstOrNull().orEmpty().trim().split(WHITESPACE)
    val pidIndex = header.indexOf("PID")
    val rssIndex = header.indexOf("RSS")
    val nameIndex = header.indexOf("NAME")
    if (pidIndex < 0 || rssIndex < 0 || nameIndex < 0) return null

    val rows = ArrayList<DeviceProcess>()
    var degraded = 0
    var dropped = 0
    for (line in lines.drop(1)) {
        if (line.isBlank()) continue
        val cells = line.trim().split(WHITESPACE)
        // 重复表头行（首格与表头首格相同）跳过：某些通道会把表头重新拼进输出
        if (cells.isEmpty() || cells.first() == header.first()) continue
        val parsed = parseDataRow(cells, header, pidIndex, rssIndex, nameIndex)
        if (parsed == null) {
            dropped++
        } else {
            rows += parsed.process
            if (parsed.degraded) degraded++
        }
    }
    return ProcessTableParse(rows = rows, degradedRows = degraded, droppedRows = dropped)
}

/** 只要行的入口（列表路径与既有测试用）；解析口径见 [parseProcessTable]。 */
internal fun parseProcessRows(output: String): List<DeviceProcess> =
    parseProcessTable(output)?.rows ?: emptyList()

/** [parseProcessTable] 的单行结果：取值 + 是否来自降级口径（缺列行）。 */
private class ParsedRow(val process: DeviceProcess, val degraded: Boolean)

/** 缺列行允许的最大缺列数：现实的空列（WCHAN / ADDR 等）最多三两列，缺得更多更像截断，宁可丢。 */
private const val MAX_MISSING_COLUMNS = 3

/**
 * 解析一行数据。完整行（列数 ≥ 表头）按严格口径；少于表头时按降级口径：
 * PID / RSS 仍按表头序号读，NAME 取末格，并要求末格**像进程名**——
 * - 长度 ≥ 2 且至少含一个字母：截断行的尾巴常是单字符的状态列（`S`）或数值列（`0` / `512`）；
 * - 降级口径只在 NAME 仍是末列时启用（表头列序非标准时不猜，宁可丢并计数）。
 * 读不出取值的行返回 null，由调用方计入丢弃。
 */
private fun parseDataRow(
    cells: List<String>,
    header: List<String>,
    pidIndex: Int,
    rssIndex: Int,
    nameIndex: Int,
): ParsedRow? {
    if (cells.size >= header.size) {
        val pid = cells[pidIndex].toIntOrNull() ?: return null
        // NAME 在末列：极少数进程名里带空格，跨格拼回来而不是只取一格
        val name = cells.drop(nameIndex).joinToString(" ").trim()
        if (name.isEmpty()) return null
        return ParsedRow(
            DeviceProcess(packageName = name, pid = pid, rssKb = cells[rssIndex].toLongOrNull() ?: 0L),
            degraded = false,
        )
    }
    if (nameIndex != header.size - 1) return null
    if (header.size - cells.size > MAX_MISSING_COLUMNS) return null
    if (pidIndex >= cells.size || rssIndex >= cells.size) return null
    val pid = cells[pidIndex].toIntOrNull() ?: return null
    val name = cells.last()
    if (name.length < 2 || name.none { it.isLetter() }) return null
    return ParsedRow(
        DeviceProcess(packageName = name, pid = pid, rssKb = cells[rssIndex].toLongOrNull() ?: 0L),
        degraded = true,
    )
}

/**
 * 这份 `ps -A` 输出里有没有「长得像数据行、但取值读不出」的记录。
 *
 * 列表路径丢几行无所谓（顶多少显示一个进程）；但结束后的回读路径不一样——回读是判「这个包
 * 还在不在」的唯一依据，丢掉的行如果恰好就是那个包，就会把「还在跑」判成「已结束」。所以
 * 回读前先问一句：有没有被丢掉的行？有就不下结论。
 *
 * 缺列行不再算丢弃：按降级口径能读出取值的行（如 WCHAN 为空列的 ROM）正常产出，只有连
 * 降级口径都读不出的行才计入（见 [parseProcessTable]）。
 */
internal fun hasDroppedProcessRows(output: String): Boolean =
    (parseProcessTable(output)?.droppedRows ?: 0) > 0

/**
 * 挑出应用进程并标出系统/应用，同包多进程时只留 RSS 最大的那个。
 *
 * 判据用 `pm list packages -s` 与 `-3` 两个集合的归属，不靠进程名的形态猜。设备上跑着的
 * 进程里绝大多数不是应用——内核线程、HAL 服务（如 `android.hardware.*-service`）都带点，
 * 靠「含点且非 `[` 开头」筛会放进一大堆杀不掉、也不该列出来的东西。两个集合都不含的名字
 * 一律丢掉：它们不是已安装的应用。
 *
 * 进程名先去掉 `:子进程` 后缀再归组：`am force-stop <包名>` 一次收掉该包的全部进程，
 * 面板按包名执行也是同一口径，所以一个包只该占一行。多进程时留 RSS 最大的——内存占用是
 * 这个面板的主信息，留哪个都不影响结束进程的结果。
 */
internal fun classifyProcesses(
    rows: List<DeviceProcess>,
    systemPackages: Set<String>,
    thirdPartyPackages: Set<String>,
    labels: Map<String, String> = emptyMap(),
    icons: Map<String, ProcessIcon> = emptyMap(),
): List<DeviceProcess> {
    val best = LinkedHashMap<String, DeviceProcess>()
    for (row in rows) {
        val packageName = processBaseName(row.packageName)
        val isSystem = packageName in systemPackages
        if (!isSystem && packageName !in thirdPartyPackages) continue
        val existing = best[packageName]
        if (existing == null || row.rssKb > existing.rssKb) {
            best[packageName] = row.copy(
                packageName = packageName,
                isSystem = isSystem,
                label = labels[packageName],
                icon = icons[packageName],
            )
        }
    }
    return best.values.sortedByDescending { it.rssKb }
}

/** 进程名里的子进程后缀分隔符，如 `com.miui.home:remote`。 */
private const val PROCESS_SUFFIX = ":"

/**
 * `pm list packages` 的输出 → 包名集合。
 *
 * 只收带 `package:` 前缀的行：shell 输出里可能混着告警与报错行，那些不是包名，
 * 收进来会让一个报错的进程被当成已安装应用列出来。
 */
internal fun parsePackageSet(output: String): Set<String> =
    output.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith(PACKAGE_PREFIX) }
        .map { it.removePrefix(PACKAGE_PREFIX) }
        .filter { it.isNotEmpty() }
        .toSet()

/**
 * 这份输出是不是一份读得出的 `pm list packages` 清单。
 *
 * 只收 `package:` 行会把「命令失败」也变成空集合，而空集合会让 [classifyProcesses] 把所有
 * 行都丢掉——最后界面显示「没有正在运行的应用」，实际是这次读取根本没成。空输出按「真的没有
 * 已安装应用」处理（非空才要求至少有可识别行），其余一律算读不出来。
 */
internal fun hasPackageListLines(output: String): Boolean {
    val trimmed = output.trim()
    if (trimmed.isEmpty()) return true
    return trimmed.lineSequence().any { it.trim().startsWith(PACKAGE_PREFIX) }
}

private const val PACKAGE_PREFIX = "package:"

/**
 * 进程名去掉 `:子进程` 后缀后的包名。
 *
 * `am force-stop <包名>` 一次收掉该包的全部进程，所以「这个包还有没有进程在跑」必须按
 * 基包名判断。列表归组（[classifyProcesses]）与结束后的回读（[forceStopProcess]）都用它，
 * 两处口径必须一致——只在一处剥后缀的话，主进程没了而 `com.foo:remote` 还在时回读会判成
 * 「已结束」，报出去的成功是假的。
 */
internal fun processBaseName(processName: String): String =
    processName.substringBefore(PROCESS_SUFFIX)

/** [forceStopProcess] 的结果；四种组合的判据见该函数的注释。 */
internal data class ForceStopOutcome(
    val gone: Boolean,
    val reason: String,
    /** 进程表读不出来（停止前快照或回读）：既不能确认已结束，也不能确认失败 */
    val readbackUnavailable: Boolean = false,
    /** 已确认结束（旧 PID 全部消失），但观察窗里该包又有了进程：系统把它拉回来了 */
    val restarted: Boolean = false,
)

/** force-stop 后观察「这个包会不会被系统拉回来」的采样次数（不含回读那次）。 */
private const val PROCESS_RESTART_OBSERVE_SAMPLES = 2

/** 观察窗两次采样之间的等待：给「看起来已经结束」的包一次暴露重生的机会。 */
private const val PROCESS_RESTART_OBSERVE_PAUSE_MS = 400L

/** 一份回读的读取结果：可读 → 属于该包的行；不可读 → 可拼进失败原因的诊断（表头不识别时为 null）。 */
private sealed interface PackageRead {
    data class Readable(val processes: List<DeviceProcess>) : PackageRead
    data class Unreadable(val diagnostic: String?) : PackageRead
}

/**
 * 从一份 `ps -A` 输出里取出属于 [packageName] 的进程；读不出时给 [PackageRead.Unreadable]。
 *
 * 「读不出来」和「确实没有进程」必须分开：在结束判定里两者是方向相反的证据——前者不下结论，
 * 后者可以确认已达目标（见 [forceStopProcess]）。表头认得但有行被丢弃时，把丢弃数带进
 * [PackageRead.Unreadable.diagnostic] 供定位（丢弃的行如果恰好是目标包，同样不能下结论）；
 * 缺列行按降级口径读出、不算丢弃（见 [parseProcessTable]），不会被这条误伤。
 */
private fun readPackageProcesses(output: String, packageName: String): PackageRead {
    val table = parseProcessTable(output) ?: return PackageRead.Unreadable(diagnostic = null)
    if (table.droppedRows > 0) {
        val total = table.rows.size + table.droppedRows
        return PackageRead.Unreadable(diagnostic = "进程表丢弃 ${table.droppedRows}/$total 行，无法确认")
    }
    return PackageRead.Readable(table.rows.filter { processBaseName(it.packageName) == packageName })
}

/** 回读不可读时的失败原因：命令原文 + 可定位的诊断（表头不识别时保持原文，不加戏）。 */
private fun unreadableReason(reply: String, read: PackageRead.Unreadable): String {
    val note = read.diagnostic ?: return reply.trim()
    val base = reply.trim()
    return if (base.isEmpty()) note else base + "\n" + note
}

/**
 * 结束一个包的全部进程。
 *
 * 用 `am force-stop <包名>` 而不是 `kill <pid>`：按包执行一次收干净，且不依赖 PID 在
 * 两次调用之间没变。
 *
 * 判成功看**停止前后两次回读的 PID 比对**，而不是「回读时这个包还在不在」：设备实测里
 * 「回读在场」有两类——命令压根没生效（如 `com.android.systemui`，AMS 只记 Force
 * stopping、进程没死，PID 不变），和命令生效但进程被系统看门狗在 1 秒内重新拉起（旧
 * PID 消失、新 PID 随即出现）。「还在不在」区分不了这两类：看门狗包会被报成「未能
 * 结束」，而杀的动作其实成功了。所以：
 *
 * 1. 停止前先取一次 `ps -A` 快照，记下这个包的 PID；
 * 2. 回读里还有**与快照 PID 相同**的本包进程 → 命令没生效（或只收掉一部分），报「未能
 *    结束」，把命令自身的输出带回去给用户看；旧 PID 全部消失 → 命令生效；
 * 3. 命令生效后进约 1 秒的观察窗（[PROCESS_RESTART_OBSERVE_SAMPLES] 次采样）：窗内该
 *    包又有了进程（回读那一刻就已经以新 PID 在场也算）→ 判「已结束，但系统重新启动了
 *    它」；窗内一直干净 → 判「已结束」。窗口长度不承重——看门狗组重启快于一次 adb
 *    往返，任何窗口长度都测不到它「消失」过，窗口的全部作用只是给「已经回来」提供
 *    第二次采样机会。
 *
 * 回读本身失败时**不能**当成已停掉。两种情形都算读不出：`ps -A` 返回空、返回报错文本、
 * 或表头列名对不上；以及表头认得、但有数据行被丢弃（见 [parseProcessTable] 的 droppedRows）
 * ——丢掉的行如果恰好就是那个包，把「还在跑」判成「已结束」同样是假成功。缺列行按降级口径
 * 读出、不算丢弃，不会被这条误伤。读不出时一律判未结束，并带 [readbackUnavailable] 让界面
 * 换一条不说谎的文案；丢弃计数会带进 reason 便于定位。
 * 停止前的快照读不出时同样不下结论：回读里还有本包进程，既不能判「没杀掉」也不能判
 * 「重生」，用 [readbackUnavailable] 报「无法确认」；回读里一个进程都没有是确定的，
 * 按已结束继续观察。
 *
 * 包名加引号：值来自 `ps -A` 而非用户输入，但拼进命令行一律引起来没有成本。
 */
internal suspend fun forceStopProcess(
    packageName: String,
    observePause: suspend () -> Unit = { delay(PROCESS_RESTART_OBSERVE_PAUSE_MS) },
    shell: suspend (String) -> String,
): ForceStopOutcome {
    val snapshot = readPackageProcesses(shell("ps -A"), packageName)
    val snapshotPids = (snapshot as? PackageRead.Readable)?.processes?.map { it.pid }?.toSet()
    val reply = shell("am force-stop \"$packageName\"")
    val after = when (val read = readPackageProcesses(shell("ps -A"), packageName)) {
        is PackageRead.Unreadable -> return ForceStopOutcome(
            gone = false,
            reason = unreadableReason(reply, read),
            readbackUnavailable = true,
        )

        is PackageRead.Readable -> read.processes
    }
    if (snapshotPids == null) {
        // 停止前读不到进程表：回读里还有本包进程时，「没杀掉」与「杀掉后又重生」无法区分
        if (after.isNotEmpty()) {
            return ForceStopOutcome(
                gone = false,
                reason = reply.trim(),
                readbackUnavailable = true,
            )
        }
    } else if (after.any { it.pid in snapshotPids }) {
        // 停止前就在的进程还在：命令没生效（或只收掉一部分）
        return ForceStopOutcome(gone = false, reason = reply.trim())
    }
    var restarted = after.isNotEmpty()
    if (!restarted) {
        for (attempt in 0 until PROCESS_RESTART_OBSERVE_SAMPLES) {
            observePause()
            val sample = readPackageProcesses(shell("ps -A"), packageName)
            if (sample is PackageRead.Readable && sample.processes.isNotEmpty()) {
                restarted = true
                break
            }
        }
    }
    return ForceStopOutcome(gone = true, reason = "", restarted = restarted)
}

/** 熄屏待机 helper 的回执。 */
internal sealed interface ScreenStandbyReply {
    /** helper 明确报告成功。 */
    data object Ok : ScreenStandbyReply

    /** helper 明确报告失败（`ok=false`）；[reason] 是它给出的原因或「失败但没带原因」的兜底说明。 */
    data class Error(val reason: String) : ScreenStandbyReply

    /**
     * 输出里认不出回执（空输出、被截断、没有 `ok` 字段等）。
     *
     * 与 [Error] 分开：这类结果不一定是动作失败——连接在执行途中被断开时，读流会以
     * 「正常结束」的形态返回空串或半截 JSON，执行器要结合链路存活状态再定性。
     */
    data class Unrecognized(val snippet: String) : ScreenStandbyReply
}

/**
 * 解析熄屏待机 helper 的 stdout。
 *
 * helper 保证输出一行 JSON：`{"ok":true}` 或 `{"ok":false,"error":"..."}`。但 adb shell
 * 会把运行时的 stderr 合并进输出，可能混入含花括号的噪声行——用「首个 `{` 到末个 `}`」
 * 截取会被噪声带偏。所以先逐行找「能解码出响应体且带 ok 字段」的行；找不到再退回整段
 * 截取（兼容跨行 JSON）。转义序列按 JSON 语义还原，界面拿到的是可读原因而不是原始字面量。
 *
 * 认不出回执时把首个非空行当原因带回——界面要能说清「为什么不知道结果」，而不是静默。
 */
internal fun parseScreenStandbyReply(output: String): ScreenStandbyReply {
    for (line in output.lineSequence()) {
        val candidate = line.trim()
        if (!candidate.startsWith("{")) continue
        val payload = runCatching {
            screenStandbyJson.decodeFromString<ScreenStandbyPayload>(candidate)
        }.getOrNull() ?: continue
        val ok = payload.ok ?: continue
        return if (ok) ScreenStandbyReply.Ok else reportedFailure(payload.error)
    }
    // 回退：按首个 { 到末个 } 整段截取再试一次（helper 不会跨行输出，但输出环境不可控）
    val start = output.indexOf('{')
    val end = output.lastIndexOf('}')
    val extracted = if (start >= 0 && end > start) {
        runCatching {
            screenStandbyJson.decodeFromString<ScreenStandbyPayload>(output.substring(start, end + 1))
        }.getOrNull()
    } else {
        null
    }
    if (extracted != null) {
        if (extracted.ok == true) return ScreenStandbyReply.Ok
        if (extracted.ok == false) return reportedFailure(extracted.error)
    }
    // 没有 JSON、或 JSON 里没有 ok 字段：不是一份可辨认的回执
    return ScreenStandbyReply.Unrecognized(
        output.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }
            ?: "empty screen standby reply",
    )
}

/** helper 报告了失败但没带原因：不能当成功，也不能装作没看见。 */
private fun reportedFailure(error: String): ScreenStandbyReply.Error =
    ScreenStandbyReply.Error(error.trim().ifEmpty { "screen standby reported failure without a reason" })

@kotlinx.serialization.Serializable
private data class ScreenStandbyPayload(val ok: Boolean? = null, val error: String = "")

private val screenStandbyJson = Json { ignoreUnknownKeys = true; isLenient = true }
