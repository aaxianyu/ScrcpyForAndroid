package io.github.miuzarte.scrcpyforandroid.pages

import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.models.WmSetting
import io.github.miuzarte.scrcpyforandroid.models.isWmRestored
import io.github.miuzarte.scrcpyforandroid.models.parseWmSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilityToolResultsTest {
    @Test
    fun restoreRequiresValidPhysicalReadback() {
        for ((field, value) in listOf("density" to "320", "size" to "1080x1920")) {
            assertEquals(true, isWmRestored("Physical $field: $value\n", field))
            assertEquals(false, isWmRestored("Physical $field: $value\nOverride $field: $value\n", field))
            assertEquals(WmSetting(value, null), parseWmSetting("Physical $field: $value\n", field))
            assertEquals(value, parseWmSetting("Physical $field: $value\nOverride $field: $value\n", field)?.override)
            for (output in listOf("", "Permission denied", "Physical $field:",
                "Physical $field: $value\nOverride $field:", "Physical $field: $value\nerror")) {
                assertNull(output, parseWmSetting(output, field))
                assertNull(output, isWmRestored(output, field))
            }
        }
    }

    @Test
    fun toolValuesNormalizeToCanonicalForm() {
        assertEquals("320", normalizeToolValue("0320", 80..640))
        assertEquals("320", normalizeToolValue("320", 80..640))
        assertEquals("320x480", listOf("0320", "0480").joinToString("x") { normalizeToolValue(it, 320..4320)!! })
        assertNull(normalizeToolValue("79", 80..640))
        assertNull(normalizeToolValue("641", 80..640))
        assertNull(normalizeToolValue("abc", 80..640))
        assertNull(normalizeToolValue("", 80..640))
        for (range in listOf(80..640, 320..4320)) {
            assertEquals(range.first.toString(), normalizeToolValue(range.first.toString(), range))
            assertEquals(range.last.toString(), normalizeToolValue(range.last.toString(), range))
            assertNull(normalizeToolValue("99999999999999999999", range))
        }
        assertEquals("320", parseWmSetting("Physical density: 440\nOverride density: 0320", "density")?.override)
        assertFalse(normalizeToolValue("0320", 80..640) ==
            parseWmSetting("Physical density: 440\nOverride density: 321", "density")?.override)
    }

    @Test
    fun tcpipSuccessOnlyForExactProtocolReply() {
        assertTrue(isTcpipSuccess("restarting in TCP mode port: 5555\n", 5555))
        for (reply in listOf(
            "cannot bind 'tcpip:5555'",
            "adbd is already running in TCP mode on port 5555",
            "restarting in TCP mode port: 5554",
            "",
            "error: device offline",
        )) {
            assertFalse(reply, isTcpipSuccess(reply, 5555))
        }
    }

    @Test
    fun portReconnectRetriesNewEndpointAndStopsOnSuccess() = runBlocking {
        val attempts = mutableListOf<Pair<String, Int>>()
        var waits = 0
        reconnectAdbPort("192.0.2.1", 6000, waitForRestart = { waits++ }) { host, port ->
            attempts += host to port
            if (attempts.size == 1) error("adbd restarting")
        }
        assertEquals(listOf("192.0.2.1" to 6000, "192.0.2.1" to 6000), attempts)
        assertEquals(2, waits)
    }

    @Test
    fun portReconnectStopsAfterThreeFailuresOrCancellation() = runBlocking {
        for (failure in listOf(IllegalStateException("offline"), CancellationException("cancelled"))) {
            var attempts = 0
            val result = runCatching {
                reconnectAdbPort("192.0.2.1", 6000, waitForRestart = {}) { _, _ ->
                    attempts++
                    throw failure
                }
            }
            assertEquals(failure, result.exceptionOrNull())
            assertEquals(if (failure is CancellationException) 1 else 3, attempts)
        }
    }

    @Test
    fun portSwitchMigratesExactEndpointIncludingSecondaryAddresses() {
        val first = DeviceShortcut(id = "first", addresses = listOf("192.0.2.2:5555", "192.0.2.1:5555"))
        val second = DeviceShortcut(id = "second", addresses = listOf("192.0.2.1:5555", "192.0.2.1:6000", "192.0.2.1:7000"))
        val updated = DeviceShortcuts(listOf(first, second)).withAdbPort(ConnectionTarget("192.0.2.1", 5555), 6000)
        assertEquals(first.copy(addresses = listOf("192.0.2.2:5555", "192.0.2.1:6000")), updated[0])
        assertEquals(second.copy(addresses = listOf("192.0.2.1:6000", "192.0.2.1:7000")), updated[1])
        val ipv6 = DeviceShortcut(addresses = listOf("[2001:db8::1]:5555"))
        assertEquals(listOf("[2001:db8::1]:6000"),
            DeviceShortcuts(listOf(ipv6)).withAdbPort(ConnectionTarget("2001:db8::1"), 6000)[0].addresses)
    }

    @Test
    fun startupScriptFailsOnlyOnNonZeroExit() {
        val cmd = startupCommand("sh /data/start.sh")
        assertTrue(cmd.contains("( sh /data/start.sh )"))
        assertEquals(StartupAttempt(1, "failed to start service"),
            parseStartupAttempt("failed to start service\n__SCRCPY_STARTUP_EXIT__:1\n"))
        assertEquals(StartupAttempt(0, ""), parseStartupAttempt("__SCRCPY_STARTUP_EXIT__:0\n"))
        // 空输出、无标记回复：状态未知，不算成功也不算失败
        assertEquals(StartupAttempt(null, ""), parseStartupAttempt(""))
        assertEquals(StartupAttempt(null, "no marker"), parseStartupAttempt("no marker"))
        assertEquals(StartupAttempt(null, "junk __SCRCPY_STARTUP_EXIT__:x"),
            parseStartupAttempt("junk __SCRCPY_STARTUP_EXIT__:x"))
    }

    @Test
    fun startupMarkerIgnoresMarkerLikeScriptOutput() {
        // 脚本自身打印同形标记：取最后一次出现，尾部真实标记胜出，假标记留在输出里
        assertEquals(
            StartupAttempt(0, "log\n__SCRCPY_STARTUP_EXIT__:7\nmore"),
            parseStartupAttempt("log\n__SCRCPY_STARTUP_EXIT__:7\nmore\n__SCRCPY_STARTUP_EXIT__:0\n"),
        )
        // 标记行之后还有内容：整段不是退出码，状态未知
        assertEquals(
            StartupAttempt(null, "__SCRCPY_STARTUP_EXIT__:0\ntrailing"),
            parseStartupAttempt("__SCRCPY_STARTUP_EXIT__:0\ntrailing"),
        )
        // 退出码超出 0..255：状态未知，不按截断值处理
        assertEquals(
            StartupAttempt(null, "x\n__SCRCPY_STARTUP_EXIT__:300"),
            parseStartupAttempt("x\n__SCRCPY_STARTUP_EXIT__:300"),
        )
    }

    @Test
    fun startupScriptsRetryAfterNonZeroExit() = runBlocking {
        val ran = mutableListOf<String>()
        val attempt = tryStartupScripts(listOf("script-a", "script-b")) { script ->
            ran.add(script)
            if (script == startupCommand("script-a")) "failed\n__SCRCPY_STARTUP_EXIT__:1\n" else "__SCRCPY_STARTUP_EXIT__:0\n"
        }
        assertEquals(listOf("script-a", "script-b").map(::startupCommand), ran)
        assertEquals(0, attempt.exitCode)
    }

    @Test
    fun startupScriptsStopWhenExitCodeUnknown() = runBlocking {
        val ran = mutableListOf<String>()
        val attempt = tryStartupScripts(listOf("script-a", "script-b")) { script ->
            ran.add(script)
            ""
        }
        assertEquals(listOf(startupCommand("script-a")), ran)
        assertNull(attempt.exitCode)
    }

    // ---------------------------------------------------------------- 进程列表

    /** 真机 `ps -A` 摘录，列宽与列序都与 Mi 10（SDK 35）实测一致。 */
    private val realPsOutput = """
        USER           PID  PPID        VSZ    RSS WCHAN            ADDR S NAME
        root             1     0   10941564   8308 0                   0 S init
        root             2     0          0      0 0                   0 S [kthreadd]
        u0_a149       3424  1069   17763760 288960 0                   0 S com.miui.home
        system       23839  1069   18931436 444304 0                   0 S com.android.systemui
        system       17624  1069   15745596 258412 0                   0 S com.miui.securitycenter
    """.trimIndent()

    /**
     * 真机上「主进程 + `:子进程`」同时在跑的形态。前缀是 [realPsOutput] 那几行，后面接两个
     * 同包的子进程（含两段后缀的那种）。
     */
    private val realPsOutputWithSubprocesses = realPsOutput + "\n" + """
        u0_a296       7327  1069  121200924 128848 0                   0 S com.tencent.mm
        u0_a296       7328  1069  121200924   9000 0                   0 S com.tencent.mm:appbrand1
        u0_i9465      7963  1069  279186084  89000 0                   0 S com.android.browser:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0
    """.trimIndent()

    @Test
    fun processRowsTakeValuesByHeaderOffsets() {
        val rows = parseProcessRows(realPsOutput)
        val home = rows.single { it.packageName == "com.miui.home" }
        // RSS 紧挨 VSZ 而 VSZ 有八位：按列号写死会取到 VSZ 的尾数
        assertEquals(3424, home.pid)
        assertEquals(288960L, home.rssKb)
        assertEquals(8308L, rows.single { it.packageName == "init" }.rssKb)
        assertEquals(5, rows.size)
    }

    @Test
    fun processRowsFollowReorderedColumns() {
        // toybox 版本或 ROM 变了一档列序：取值必须跟着表头走而不是跟着位置走。
        // NAME 仍在末位——ps 的名字列永远是最后一列，按区间切才成立
        val reordered = """
            USER RSS ADDR PID NAME
            u0_a149 288960 0 3424 com.miui.home
        """.trimIndent()
        val row = parseProcessRows(reordered).single()
        assertEquals("com.miui.home", row.packageName)
        assertEquals(3424, row.pid)
        assertEquals(288960L, row.rssKb)
    }

    @Test
    fun processRowsDropOutputWithoutRecognizableHeader() {
        assertTrue(parseProcessRows("").isEmpty())
        assertTrue(parseProcessRows("Permission denied").isEmpty())
        // 表头缺列：宁可不解析，也不用猜出来的列号取错值
        assertTrue(parseProcessRows("USER PID\nroot 1").isEmpty())
    }

    @Test
    fun processRowsSkipLinesWhosePidIsUnreadable() {
        val rows = parseProcessRows(
            """
            PID  RSS NAME
            1    8308 init
            root 0    broken-row
            """.trimIndent(),
        )
        assertEquals(listOf("init"), rows.map { it.packageName })
    }

    @Test
    fun processRowsWithEmptyMiddleColumnsAreReadAsDegraded() {
        // 部分 ROM 的 WCHAN / ADDR 是空列，split 后整行少格。以前这种行被整行丢掉：
        // 列表少显示，回读被放大成「无法确认」，功能整体不可用。现在按降级口径读出来
        val emptyWchan = """
            USER PID PPID VSZ RSS WCHAN ADDR S NAME
            u0_a1 100 1 0 512  0 S com.foo
        """.trimIndent()
        val row = parseProcessRows(emptyWchan).single()
        assertEquals("com.foo", row.packageName)
        assertEquals(100, row.pid)
        assertEquals(512L, row.rssKb)
        assertFalse(hasDroppedProcessRows(emptyWchan))
    }

    @Test
    fun processTableCountsDegradedAndDroppedRowsApart() {
        val mixed = """
            USER PID PPID VSZ RSS WCHAN ADDR S NAME
            u0_a1 100 1 0 512  S com.a
            u0_a1 101 1 0 512  0 S com.b
            u0_a1 102 1 0 512 0 0 S
            u0_a1 103 1 0 512 0 0 S com.d
        """.trimIndent()
        val table = checkNotNull(parseProcessTable(mixed))
        // com.a：WCHAN+ADDR 空 → 降级；com.b：WCHAN 空 → 降级；
        // 第三行尾巴是状态列（截断形态）→ 丢弃；com.d 完整
        assertEquals(listOf("com.a", "com.b", "com.d"), table.rows.map { it.packageName })
        assertEquals(2, table.degradedRows)
        assertEquals(1, table.droppedRows)
    }

    @Test
    fun truncatedLookingRowsAreStillDropped() {
        val header = "USER PID PPID VSZ RSS WCHAN ADDR S NAME\n"
        // 缺列太多（> 3 列）
        assertTrue(hasDroppedProcessRows(header + "u0_a1 100 1 0 512\n"))
        // 尾巴是数值：NAME 位上是别的列
        assertTrue(hasDroppedProcessRows(header + "u0_a1 100 1 0 512 0 0\n"))
        // 尾巴是单字符状态列
        assertTrue(hasDroppedProcessRows(header + "u0_a1 100 1 0 512 0 0 S\n"))
    }

    // ---------------------------------------------------------------- 应用归类

    @Test
    fun classifyKeepsOnlyNamesPresentInThePackageLists() {
        val rows = parseProcessRows(realPsOutput)
        val result = classifyProcesses(
            rows = rows,
            systemPackages = setOf("com.android.systemui"),
            thirdPartyPackages = setOf("com.miui.home", "com.miui.securitycenter"),
        )
        // init / [kthreadd] 两个集合都不含，不是已安装的应用
        assertEquals(
            listOf("com.android.systemui", "com.miui.home", "com.miui.securitycenter"),
            result.map { it.packageName },
        )
        assertEquals(listOf(true, false, false), result.map { it.isSystem })
    }

    @Test
    fun classifyDropsHalServicesThatOnlyLookLikePackages() {
        val rows = parseProcessRows(
            """
            PID  RSS NAME
            100  512 android.hardware.audio.service
            101  256 android.hardware.bluetooth@1.0-service-qti
            102  128 com.absinthe.anywhere_
            """.trimIndent(),
        )
        val result = classifyProcesses(
            rows,
            systemPackages = emptySet(),
            thirdPartyPackages = setOf("com.absinthe.anywhere_"),
        )
        // 带点号的 HAL 服务不是应用，靠进程名形态筛会放进这个列表
        assertEquals(listOf("com.absinthe.anywhere_"), result.map { it.packageName })
    }

    @Test
    fun classifyKeepsLargestRssPerPackageAndSortsDescending() {
        val rows = parseProcessRows(
            """
            PID  RSS NAME
            100  2048 com.miui.home
            101  9000 com.miui.home:remote
            102  4096 com.android.settings
            """.trimIndent(),
        )
        val result = classifyProcesses(
            rows,
            systemPackages = setOf("com.android.settings"),
            thirdPartyPackages = setOf("com.miui.home"),
        )
        // :remote 并进基包：结束进程按包名执行，一个包一次收掉，列表该按同一口径归组
        assertEquals(listOf("com.miui.home", "com.android.settings"), result.map { it.packageName })
        assertEquals(9000L, result.first().rssKb)
        assertEquals(4096L, result.first { it.packageName == "com.android.settings" }.rssKb)
    }

    @Test
    fun packageSetParsesPmOutputAndIgnoresNoise() {
        assertEquals(
            setOf("com.miui.home", "com.android.settings"),
            parsePackageSet("package:com.miui.home\npackage:com.android.settings\n"),
        )
        assertEquals(emptySet<String>(), parsePackageSet(""))
        assertEquals(emptySet<String>(), parsePackageSet("Error: unknown command\n"))
    }

    // ---------------------------------------------------------------- 结束进程

    @Test
    fun forceStopIsConfirmedByReadingTheProcessListBack() = runBlocking {
        // 新判定按 PID 比对：停止前快照里有 miui.home，回读与观察窗里都没有——
        // 旧 PID 确认消失且没回来，判「已结束」而不是「被重新启动」
        val calls = mutableListOf<String>()
        var psCalls = 0
        val outcome = forceStopProcess("com.miui.home", observePause = {}) { command ->
            calls.add(command)
            if (command.startsWith("am force-stop")) "" else {
                psCalls++
                if (psCalls == 1) realPsOutput else realPsOutput.replace("com.miui.home", "gone")
            }
        }
        // 第一次 ps 是停止前快照；随后是回读与两次观察窗采样
        assertEquals(
            listOf("ps -A", "am force-stop \"com.miui.home\"", "ps -A", "ps -A", "ps -A"),
            calls,
        )
        assertTrue(outcome.gone)
        assertFalse(outcome.restarted)
        assertEquals("", outcome.reason)
    }

    @Test
    fun forceStopReportsShellReplyWhenProcessSurvives() = runBlocking {
        // shell v1 没有退出码：系统包被拒只体现在输出里。快照与回读里 systemui 的 PID
        // 都是 23839——「PID 没变」说明命令压根没生效（而不是被杀后又被系统拉起），
        // 报「未能结束」并把命令输出带回去
        val outcome = forceStopProcess("com.android.systemui") { command ->
            if (command.startsWith("am force-stop")) "Permission denial: force-stop com.android.systemui"
            else realPsOutput
        }
        assertEquals(false, outcome.gone)
        assertFalse(outcome.readbackUnavailable)
        assertEquals("Permission denial: force-stop com.android.systemui", outcome.reason)
    }

    @Test
    fun forceStopTreatsAnAlreadyAbsentProcessAsReached() = runBlocking {
        // 快照里就没有这个包（列表那一行是陈旧的）：包不在场，观察窗里也不会长出进程来
        val outcome = forceStopProcess("com.absinthe.anywhere_", observePause = {}) { command ->
            if (command.startsWith("am force-stop")) "" else realPsOutput
        }
        assertTrue(outcome.gone)
        assertFalse(outcome.restarted)
    }

    @Test
    fun forceStopStillCountsASurvivingSubprocessAsRunning() = runBlocking {
        // 主进程没了、:子进程还在：回读与快照里子进程的 PID 相同，说明命令没把它收掉——
        // am force-stop 是按包收的，子进程留着就等于没停掉。只比对裸进程名会把它当成
        // 已结束，报出去的成功是假的
        val afterMainGone = realPsOutputWithSubprocesses
            .lineSequence()
            .filterNot { it.trim().endsWith("com.tencent.mm") }
            .joinToString("\n")
        val outcome = forceStopProcess("com.tencent.mm") { command ->
            if (command.startsWith("am force-stop")) "" else afterMainGone
        }
        assertFalse(outcome.gone)
        assertFalse(outcome.readbackUnavailable)
    }

    @Test
    fun forceStopReportsSuccessOnceTheSubprocessesAreGoneToo() = runBlocking {
        // 与上一条互为对照：主进程和子进程都没了，才算真的停掉
        val afterAllGone = realPsOutputWithSubprocesses
            .lineSequence()
            .filterNot { it.contains("com.tencent.mm") }
            .joinToString("\n")
        val outcome = forceStopProcess("com.tencent.mm", observePause = {}) { command ->
            if (command.startsWith("am force-stop")) "" else afterAllGone
        }
        assertTrue(outcome.gone)
        assertFalse(outcome.restarted)
    }

    @Test
    fun forceStopQuotesThePackageNameInTheCommand() = runBlocking {
        // 全部命令都返回空输出：快照读不出、回读读不出，直接给「无法确认」，
        // 不会有观察窗采样那两次调用
        val calls = mutableListOf<String>()
        forceStopProcess("com.foo.bar") { command ->
            calls.add(command)
            ""
        }
        assertEquals(listOf("ps -A", "am force-stop \"com.foo.bar\"", "ps -A"), calls)
    }

    @Test
    fun forceStopReportsRestartWhenANewPidIsAlreadyBackAtReadback() = runBlocking {
        // 看门狗包：命令生效（旧 PID 消失）但系统在回读前就把它拉回来了——
        // 回读里是同名进程、不同 PID。旧 PID 全灭 + 新进程在场 = 「已结束，但系统重新启动了它」
        val calls = mutableListOf<String>()
        var psCalls = 0
        val outcome = forceStopProcess("com.miui.home", observePause = {}) { command ->
            calls.add(command)
            if (command.startsWith("am force-stop")) "" else {
                psCalls++
                if (psCalls == 1) realPsOutput else realPsOutput.replace("3424", "5555")
            }
        }
        assertTrue(outcome.gone)
        assertTrue(outcome.restarted)
        // 回读已经看到新进程，观察窗不必再采样：总共只有快照与回读两次 ps
        assertEquals(2, calls.count { it == "ps -A" })
    }

    @Test
    fun forceStopReportsRestartWhenThePackageReappearsDuringTheObservationWindow() = runBlocking {
        // 慢一点的保活：回读时它确实不在，观察窗第一次采样发现它已经回来了（新 PID）
        var psCalls = 0
        val outcome = forceStopProcess("com.miui.home", observePause = {}) { command ->
            if (command.startsWith("am force-stop")) "" else when (++psCalls) {
                1 -> realPsOutput
                2 -> realPsOutput.replace("com.miui.home", "gone")
                else -> realPsOutput.replace("3424", "5555")
            }
        }
        assertTrue(outcome.gone)
        assertTrue(outcome.restarted)
    }

    @Test
    fun forceStopCannotConfirmWithoutASnapshotWhenSomethingIsRunning() = runBlocking {
        // 停止前快照读不出来（空输出），回读里还有本包进程：「没杀掉」和「杀掉后又重生」
        // 无法区分，不猜——报「无法确认」，保留该行
        var psCalls = 0
        val outcome = forceStopProcess("com.miui.home") { command ->
            if (command.startsWith("am force-stop")) "" else {
                psCalls++
                if (psCalls == 1) "" else realPsOutput
            }
        }
        assertFalse(outcome.gone)
        assertTrue(outcome.readbackUnavailable)
        assertFalse(outcome.restarted)
    }

    @Test
    fun forceStopKeepsStoppedVerdictWhenObservationSamplesAreUnreadable() = runBlocking {
        // 旧 PID 全部消失已经确认结束；观察窗采样读不出来只是「看不到有没有回来」，
        // 不能反向抹掉已确认的结束，也不能猜成「被重新启动」
        var psCalls = 0
        val outcome = forceStopProcess("com.miui.home", observePause = {}) { command ->
            if (command.startsWith("am force-stop")) "" else when (++psCalls) {
                1 -> realPsOutput
                2 -> realPsOutput.replace("com.miui.home", "gone")
                else -> "ps: bad process name"
            }
        }
        assertTrue(outcome.gone)
        assertFalse(outcome.restarted)
    }

    @Test
    fun forceStopReportsRestartWhenOnlyASubprocessComesBack() = runBlocking {
        // 基包名归组同样管着观察窗：主进程死了，:appbrand1 以新 PID 重新出现也算这个包回来
        val noTencent = realPsOutputWithSubprocesses
            .lineSequence()
            .filterNot { it.contains("com.tencent.mm") }
            .joinToString("\n")
        val subprocessBack = realPsOutputWithSubprocesses
            .lineSequence()
            .filterNot { it.trim().endsWith("com.tencent.mm") }
            .joinToString("\n")
            .replace("7328", "9001")
        var psCalls = 0
        val outcome = forceStopProcess("com.tencent.mm", observePause = {}) { command ->
            if (command.startsWith("am force-stop")) "" else when (++psCalls) {
                1 -> realPsOutputWithSubprocesses
                2 -> noTencent
                else -> subprocessBack
            }
        }
        assertTrue(outcome.gone)
        assertTrue(outcome.restarted)
    }

    @Test
    fun forceStopNeverReportsSuccessWhenTheReadbackItselfIsUnreadable() = runBlocking {
        // 回读不可读 ≠ 「表里没有这个包」。parseProcessRows 对这三种输入都会给空列表，
        // 不拦住的话就会反向输出成功、还会把那一行从列表里摘掉——最坏的一种失败方向
        val unreadable = listOf(
            "" to "空输出",
            "ps: bad process name" to "命令报错文本",
            "USER PID PPID VSZ RSS WCHAN ADDR S CMD\nu0_a1 963 2 0 0 0 0 S init" to "列名对不上",
            "ERROR: java.lang.SecurityException: uid=2000" to "权限报错",
        )
        for ((readBack, label) in unreadable) {
            val outcome = forceStopProcess("com.foo") { command ->
                if (command.startsWith("am force-stop")) "" else readBack
            }
            assertFalse("回读=$label 不该报成功", outcome.gone)
            assertTrue("回读=$label 应标记为不可确认", outcome.readbackUnavailable)
        }
    }

    @Test
    fun forceStopKeepsTheCommandReplyAsReasonWhenTheReadbackFails() = runBlocking {
        // 回读挂掉时命令自己的输出仍然有用：系统包被拒的原文就在 reply 里
        val outcome = forceStopProcess("com.android.systemui") { command ->
            if (command.startsWith("am force-stop")) "Permission denial: force-stop com.android.systemui"
            else ""
        }
        assertFalse(outcome.gone)
        assertTrue(outcome.readbackUnavailable)
        assertEquals("Permission denial: force-stop com.android.systemui", outcome.reason)
    }

    @Test
    fun packageListIsDistinguishableFromAReadThatFailed() {
        // 两个集合都当成空集合读，会把所有进程在归类阶段丢掉，界面显示成「没有应用在跑」——
        // 实际是读取没成。非空输出里一条 package: 行都没有才算读不出来
        assertTrue(hasPackageListLines(""))
        assertTrue(hasPackageListLines("package:com.a\npackage:com.b\n"))
        assertFalse(hasPackageListLines("Error: Unable to find service package"))
        assertFalse(hasPackageListLines("java.lang.SecurityException: requires android.permission"))
        assertFalse(hasPackageListLines("Warning: package path does not exist"))
    }

    @Test
    fun anUnreadablePackageListYieldsNoRowsRatherThanAnEmptyButTruthfulList() {
        // 与上面配套：pm 读不出来时，不该得到一个「合法但为空」的集合去喂归类
        val rows = parseProcessRows(realPsOutput)
        val brokenPackages = parsePackageSet("Error: Unable to find service package")
        assertTrue(brokenPackages.isEmpty())
        assertFalse(hasPackageListLines("Error: Unable to find service package"))
        // 用真实包集合时才有行；用读不出来的集合时一行都不剩
        val good = parsePackageSet("package:com.miui.home")
        assertEquals(1, classifyProcesses(rows, good, emptySet()).size)
        assertEquals(0, classifyProcesses(rows, brokenPackages, emptySet()).size)
    }

    @Test
    fun droppedRowsInTheReadbackCountAsUnreadable() {
        // 回读是「这个包还在不在」的唯一依据。表头认得、但有数据行被丢弃时，被丢掉的行
        // 恰好就是那个包的话，回读会判成「已结束」——所以丢行即视为读不出，不下结论
        val withDroppedRow = realPsOutput + "\n" +
            "u0_a1      broken  1069        0    999 0                   0 S com.foo.bar"
        assertTrue(hasDroppedProcessRows(withDroppedRow))
        val outcome = runBlocking {
            forceStopProcess("com.foo.bar") { command ->
                if (command.startsWith("am force-stop")) "" else withDroppedRow
            }
        }
        assertFalse(outcome.gone)
        assertTrue(outcome.readbackUnavailable)
        // 丢弃计数带进失败原因，便于定位是哪份输出、丢了多少行
        assertTrue(outcome.reason.contains("丢弃 1/6 行"))
    }

    @Test
    fun readbackWithEmptyColumnRowsStillConfirmsTheRunningProcess() = runBlocking {
        // 回读带空列（少格）时，改造前一律判「无法确认」；现在能按 PID 正常判定——
        // 同包 PID 未变：命令没生效，报「未能结束」而不是「无法确认」
        val emptyColumns = """
            USER PID PPID VSZ RSS WCHAN ADDR S NAME
            u0_a1 100 1 0 512  0 S com.foo
            u0_a1 101 1 0 512  S com.foo:remote
        """.trimIndent()
        val surviving = forceStopProcess("com.foo") { command ->
            if (command.startsWith("am force-stop")) "" else emptyColumns
        }
        assertFalse(surviving.gone)
        assertFalse(surviving.readbackUnavailable)

        // 反过来：回读里该包没了（同一个空列形态）→ 判「已结束」
        var psCalls = 0
        val gone = forceStopProcess("com.foo", observePause = {}) { command ->
            if (command.startsWith("am force-stop")) {
                ""
            } else if (++psCalls == 1) {
                emptyColumns
            } else {
                emptyColumns.lineSequence().filterNot { it.contains("com.foo") }.joinToString("\n")
            }
        }
        assertTrue(gone.gone)
        assertFalse(gone.restarted)
    }

    @Test
    fun aFullyReadableProcessTableHasNoDroppedRows() {
        assertFalse(hasDroppedProcessRows(realPsOutput))
        assertFalse(hasDroppedProcessRows(""))
        assertFalse(hasDroppedProcessRows("not a process table at all"))
    }

    @Test
    fun kernelThreadsWithBracketsAreNotCountedAsDroppedRows() {
        // [kthreadd] 这类带方括号的内核线程照常走正常解析路径，不该被当成「丢弃的行」，
        // 否则每次回读都会被判成不可读，功能直接不可用
        assertFalse(hasDroppedProcessRows(realPsOutput))
        val onlyKernel = "USER PID PPID VSZ RSS WCHAN ADDR S NAME\n" +
            "root 2 0 0 0 0 0 S [kthreadd]\n" +
            "root 3 0 0 0 0 0 S [kworker/0:1]\n"
        assertFalse(hasDroppedProcessRows(onlyKernel))
    }

    @Test
    fun blankLinesInTheProcessTableAreNotCountedAsDroppedRows() {
        // 空行不是数据行：某些通道拼接提示行、或 ROM 输出夹空行时，空行会被 split 成 [""]
        // 而误判成「列数少于表头的丢弃行」，让每次回读都报「无法确认」（功能整体不可用）
        val withBlankLine = realPsOutput.replaceFirst("\n", "\n\n")
        assertFalse(hasDroppedProcessRows(withBlankLine))
        assertFalse(hasDroppedProcessRows(withBlankLine.replaceFirst("\n\n", "\n\n\n")))
        // 末尾空行本来就会被 trim 掉，这里一并锁定行为
        assertFalse(hasDroppedProcessRows(realPsOutput + "\n"))
    }

    @Test
    fun aRepeatedHeaderRowIsNotCountedAsDropped() {
        // 有些通道会把表头重新拼进输出；首列同样是 USER，按表头行跳过，不算丢行
        val withRepeatedHeader = realPsOutput + "\nUSER PID PPID VSZ RSS WCHAN ADDR S NAME\n"
        assertFalse(hasDroppedProcessRows(withRepeatedHeader))
    }

    @Test
    fun processBaseNameStripsOnlyTheSubprocessSuffix() {
        assertEquals("com.tencent.mm", processBaseName("com.tencent.mm"))
        assertEquals("com.tencent.mm", processBaseName("com.tencent.mm:appbrand1"))
        assertEquals(
            "com.android.browser",
            processBaseName("com.android.browser:sandboxed_process0:org.chromium.S:0"),
        )
    }

    @Test
    fun classificationAndReadbackAgreeOnWhatCountsAsTheSamePackage() {
        // 归组按剥后缀的基包名，回读也必须按同一个口径，否则列表里的一行和回读的判据对不上
        val rows = parseProcessRows(
            """
            USER PID PPID VSZ RSS WCHAN ADDR S NAME
            u0_a296 7327 1069 121200924 128848 0 0 S com.tencent.mm
            u0_a296 7328 1069 121200924 9000 0 0 S com.tencent.mm:appbrand1
            """.trimIndent(),
        )
        val packages = setOf("com.tencent.mm")
        val listed = classifyProcesses(
            rows = rows,
            systemPackages = packages,
            thirdPartyPackages = emptySet(),
        )
        assertEquals(listOf("com.tencent.mm"), listed.map { it.packageName })
        // 同一条 ps 输出交给回读：主进程不在了、子进程还在，仍要判成还在
        val subprocessOnly = rows.filter { it.packageName.endsWith(":appbrand1") }
        assertTrue(subprocessOnly.any { processBaseName(it.packageName) == "com.tencent.mm" })
        assertFalse(subprocessOnly.any { it.packageName == "com.tencent.mm" })
    }

    @Test
    fun processTableHeaderIsRecognizedOnlyWhenAllThreeColumnsArePresent() {
        assertTrue(hasProcessTableHeader(realPsOutput))
        assertTrue(hasProcessTableHeader("USER PID PPID VSZ RSS WCHAN ADDR S NAME"))
        // 列名对不上就是读不出来，不能跟「没有应用在跑」共用一个空态
        assertFalse(hasProcessTableHeader("USER PID PPID VSZ RSS WCHAN ADDR S CMD"))
        assertFalse(hasProcessTableHeader("ps: bad process name"))
        assertFalse(hasProcessTableHeader(""))
    }

    @Test
    fun unreadableTableYieldsNoRowsJustLikeAnEmptyOne() {
        val broken = "USER PID PPID VSZ RSS WCHAN ADDR S CMD\nu0_a1 963 2 0 0 0 0 S init\n"
        assertFalse(hasProcessTableHeader(broken))
        assertTrue(parseProcessRows(broken).isEmpty())
    }

    // ---------------------------------------------------------------- 名字与图标

    @Test
    fun labelsFallBackToPackageNameWhenTheProbeHasNoName() {
        val apps = listOf(
            DeviceApp("com.miui.home", "桌面", "1.0", 0L, isSystem = false, isEnabled = true),
            DeviceApp("com.android.settings", "", "1.0", 0L, isSystem = true, isEnabled = true),
        )
        // 空标签不算有名字：界面据此回落到包名
        assertEquals(mapOf("com.miui.home" to "桌面"), parseProcessLabels(apps))
    }

    @Test
    fun iconsDropEmptyByteArrays() {
        val icons = parseProcessIcons(
            icons = mapOf(
                "com.miui.home" to byteArrayOf(1, 2, 3),
                "com.android.settings" to ByteArray(0),
            ),
            iconSuffix = ".webp",
        )
        // 探测端在应用没有可用图标时给零长字节，留着会占一个永远解不出内容的图标位
        assertEquals(setOf("com.miui.home"), icons.keys)
        assertEquals(".webp", icons.getValue("com.miui.home").suffix)
        assertTrue(icons.getValue("com.miui.home").bytes.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun iconBytesCompareByContentNotReference() {
        val a = ProcessIcon(byteArrayOf(1, 2), ".webp")
        val b = ProcessIcon(byteArrayOf(1, 2), ".webp")
        val c = ProcessIcon(byteArrayOf(1, 3), ".webp")
        // 数据类默认按引用比数组，两个内容相同的图标会不相等——这里显式改成按内容
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun classifyCarriesLabelsAndIconsOntoTheRow() {
        val rows = parseProcessRows(realPsOutput)
        val result = classifyProcesses(
            rows = rows,
            systemPackages = setOf("com.android.systemui"),
            thirdPartyPackages = setOf("com.miui.home", "com.miui.securitycenter"),
            labels = mapOf("com.miui.home" to "桌面"),
            icons = mapOf("com.miui.home" to ProcessIcon(byteArrayOf(7), ".png")),
        )
        val home = result.first { it.packageName == "com.miui.home" }
        assertEquals("桌面", home.label)
        assertEquals(".png", home.icon?.suffix)
        // 没给的项保持 null，界面回落到包名
        assertNull(result.first { it.packageName == "com.android.systemui" }.label)
    }

    @Test
    fun screenStandbyReplyParsesOkAndReportedErrors() {
        assertEquals(ScreenStandbyReply.Ok, parseScreenStandbyReply("{\"ok\":true}\n"))
        // 前置噪声行（linker 告警等）不干扰 JSON 的定位
        assertEquals(
            ScreenStandbyReply.Ok,
            parseScreenStandbyReply("WARNING: linker: app_process: unused DT entry\n{\"ok\":true}\n"),
        )
        assertEquals(
            ScreenStandbyReply.Error("no display token available (sdk=34, brand=honor)"),
            parseScreenStandbyReply("{\"ok\":false,\"error\":\"no display token available (sdk=34, brand=honor)\"}\n"),
        )
        // 转义序列按 JSON 语义还原，而不是把原始字面量带进界面
        assertEquals(
            ScreenStandbyReply.Error("boom\nquoted \"x\"\\tail"),
            parseScreenStandbyReply("{\"ok\":false,\"error\":\"boom\\nquoted \\\"x\\\"\\\\tail\"}\n"),
        )
    }

    @Test
    fun screenStandbyReplyExplainsUnrecognizableOutput() {
        // 认不出回执：把首个非空行当原因带回，界面能说清「为什么不知道结果」；
        // 归为 Unrecognized 而不是 Error——执行器还要结合链路存活状态再定性
        assertEquals(
            ScreenStandbyReply.Unrecognized("Permission denied"),
            parseScreenStandbyReply("Permission denied\n"),
        )
        assertEquals(
            ScreenStandbyReply.Unrecognized("empty screen standby reply"),
            parseScreenStandbyReply(""),
        )
        // 截断的 JSON、缺 ok 字段的 JSON 都不是可辨认的回执
        assertEquals(
            ScreenStandbyReply.Unrecognized("{\"ok\":tru"),
            parseScreenStandbyReply("{\"ok\":tru"),
        )
        assertEquals(
            ScreenStandbyReply.Unrecognized("{\"status\":1}"),
            parseScreenStandbyReply("{\"status\":1}"),
        )
        // ok:false 但没带原因：helper 明确报了失败，不能当成功，也不能装作没看见
        assertEquals(
            ScreenStandbyReply.Error("screen standby reported failure without a reason"),
            parseScreenStandbyReply("{\"ok\":false}"),
        )
    }

    @Test
    fun screenStandbyReplyToleratesNoiseLinesWithBraces() {
        // 噪声行含花括号：逐行定位不受影响（整段截取会被带偏成无法解析）
        assertEquals(
            ScreenStandbyReply.Ok,
            parseScreenStandbyReply("WARN {scanner} ignored\n{\"ok\":true}\n"),
        )
        assertEquals(
            ScreenStandbyReply.Ok,
            parseScreenStandbyReply("{\"ok\":true}\nWARN }trailing{\n"),
        )
        // JSON 跨行：helper 不会这样输出，但整段回退路径要能兜住
        assertEquals(
            ScreenStandbyReply.Ok,
            parseScreenStandbyReply("{\n  \"ok\": true\n}\n"),
        )
    }

    @Test
    fun screenStandbyReplyPinsFailureEdgeCases() {
        // 原因字段为空白：仍按失败上报，用兜底说明
        assertEquals(
            ScreenStandbyReply.Error("screen standby reported failure without a reason"),
            parseScreenStandbyReply("{\"ok\":false,\"error\":\" \"}\n"),
        )
        // ok 不是布尔值：不是可辨认的回执，首个非空行原样带回
        assertEquals(
            ScreenStandbyReply.Unrecognized("{\"ok\":1}"),
            parseScreenStandbyReply("{\"ok\":1}\n"),
        )
    }
}
