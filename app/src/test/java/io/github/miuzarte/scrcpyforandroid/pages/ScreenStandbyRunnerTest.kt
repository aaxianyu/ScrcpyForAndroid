package io.github.miuzarte.scrcpyforandroid.pages

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * 熄屏待机执行器的传输层替身。
 *
 * 超时用真实的 `withTimeout` 生成（形状必须与 `NativeAdbService.shell` 内部一致，
 * 否则测不到真实分支）；`execBlocks` 用来复现「设备不回包」的挂起路径。
 */
private class FakeDevice(
    private val connected: Boolean = true,
    private val transportAlive: Boolean = true,
) {
    val commands = mutableListOf<Pair<String, Long>>()
    val pushes = mutableListOf<String>()
    val execStarted = CompletableDeferred<Unit>()
    var wakeError: Throwable? = null
    var wakeTimesOut = false
    var pushError: Throwable? = null
    var execError: Throwable? = null
    var execTimesOut = false
    var execBlocks = false
    var cleanupError: Throwable? = null
    var reply = "{\"ok\":true}"

    val runner = ScreenStandbyRunner(
        isConnected = { connected },
        isTransportAlive = { transportAlive },
        localDex = { File("screenstandby-test.dex") },
        push = { _, remote ->
            pushError?.let { throw it }
            pushes.add(remote)
        },
        shell = { command, timeoutMs ->
            commands.add(command to timeoutMs)
            when {
                command.startsWith("rm -f") -> cleanupError?.let { throw it } ?: ""
                command.startsWith("input ") -> when {
                    wakeTimesOut -> withTimeout(1) { delay(1_000); "" }
                    else -> wakeError?.let { throw it } ?: ""
                }

                else -> when {
                    execTimesOut -> withTimeout(1) { delay(1_000); reply }
                    execBlocks -> {
                        execStarted.complete(Unit)
                        awaitCancellation()
                    }

                    else -> execError?.let { throw it } ?: reply
                }
            }
        },
    )

    // 清理用完整命令串断言：路径或写法变了就该失败，而不是只数次数
    val cleanupCommands get() = commands.filter { it.first == "rm -f /data/local/tmp/scrcpy_screen_standby.dex" }
    val execCommands get() = commands.filter { it.first.startsWith("CLASSPATH") }
    val wakeCommands get() = commands.filter { it.first.startsWith("input ") }
}

class ScreenStandbyRunnerTest {

    @Test
    fun unconnectedDeviceIsReportedWithoutTouchingIt() = runBlocking {
        val device = FakeDevice(connected = false)
        assertEquals(ScreenStandbyOutcome.NotConnected, device.runner.run(ScreenStandbyAction.TurnOff))
        assertTrue(device.commands.isEmpty())
        assertTrue(device.pushes.isEmpty())
    }

    @Test
    fun helperRunsAndRemoteDexIsCleanedUp() = runBlocking {
        val device = FakeDevice()
        assertEquals(ScreenStandbyOutcome.Done, device.runner.run(ScreenStandbyAction.TurnOff))
        assertEquals(1, device.execCommands.size)
        assertTrue(device.execCommands.single().first.endsWith("ScreenStandby off"))
        assertEquals("scrcpy_screen_standby.dex", device.pushes.single().substringAfterLast('/'))
        assertEquals(1, device.cleanupCommands.size)
        // 熄屏不注入唤醒键：设备本来就醒着，注入按键会把它唤醒
        assertTrue(device.wakeCommands.isEmpty())
    }

    @Test
    fun turningOnWakesFirstAndEveryStepCarriesATimeout() = runBlocking {
        val device = FakeDevice()
        assertEquals(ScreenStandbyOutcome.Done, device.runner.run(ScreenStandbyAction.TurnOn))
        // 先唤醒（KEYCODE_WAKEUP），再跑 helper
        assertEquals("input keyevent 224", device.wakeCommands.single().first)
        assertTrue(device.wakeCommands.single().second > 0)
        assertTrue(device.execCommands.single().second > 0)
        assertTrue(device.cleanupCommands.single().second > 0)
        val wakeIndex = device.commands.indexOfFirst { it.first.startsWith("input ") }
        val execIndex = device.commands.indexOfFirst { it.first.startsWith("CLASSPATH") }
        assertTrue(wakeIndex in 0 until execIndex)
    }

    @Test
    fun helperTimeoutIsReportedAsFailureAndCleansUp() = runBlocking {
        val device = FakeDevice()
        device.execTimesOut = true
        assertEquals(ScreenStandbyOutcome.TimedOut, device.runner.run(ScreenStandbyAction.TurnOff))
        assertEquals(1, device.cleanupCommands.size)
    }

    @Test
    fun interruptedTransportCountsAsTimeoutNotAsCancellation() = runBlocking {
        // runInterruptible 被打断时同样以 CancellationException 形态抛出：
        // 只要自身作用域还在，就应该按超时上报，而不是把异常抛出去
        val device = FakeDevice()
        device.execError = CancellationException("Blocking call was interrupted while waiting")
        assertEquals(ScreenStandbyOutcome.TimedOut, device.runner.run(ScreenStandbyAction.TurnOff))
        assertEquals(1, device.cleanupCommands.size)
    }

    @Test
    fun wakeTimeoutIsReportedWithoutRunningHelper() = runBlocking {
        val device = FakeDevice()
        device.wakeTimesOut = true
        assertEquals(ScreenStandbyOutcome.TimedOut, device.runner.run(ScreenStandbyAction.TurnOn))
        assertTrue(device.execCommands.isEmpty())
        assertEquals(1, device.cleanupCommands.size)
    }

    @Test
    fun parentCancellationPropagatesButCleanupStillRuns() = runBlocking {
        val device = FakeDevice()
        device.execBlocks = true
        val job = launch { device.runner.run(ScreenStandbyAction.TurnOff) }
        withTimeout(5_000) { device.execStarted.await() }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(1, device.cleanupCommands.size)
    }

    @Test
    fun droppedConnectionMidRunIsReportedAsNotConnected() = runBlocking {
        val device = FakeDevice()
        device.execError = IllegalStateException("ADB not connected")
        assertEquals(ScreenStandbyOutcome.NotConnected, device.runner.run(ScreenStandbyAction.TurnOff))
        assertEquals(1, device.cleanupCommands.size)
    }

    @Test
    fun socketFailureOnDeadTransportIsReportedAsNotConnected() = runBlocking {
        // 链路已死时的 socket 级异常（英文原文）不该透传给用户：归类为「未连接」
        val device = FakeDevice(transportAlive = false)
        device.execError = IOException("Software caused connection abort")
        assertEquals(ScreenStandbyOutcome.NotConnected, device.runner.run(ScreenStandbyAction.TurnOff))
        assertEquals(1, device.cleanupCommands.size)
    }

    @Test
    fun pushFailureIsReportedWithReasonAndCleansUp() = runBlocking {
        val device = FakeDevice()
        device.pushError = IOException("read failed")
        val outcome = device.runner.run(ScreenStandbyAction.TurnOff)
        assertEquals(ScreenStandbyOutcome.Failed("read failed"), outcome)
        assertTrue(device.execCommands.isEmpty())
        assertEquals(1, device.cleanupCommands.size)
    }

    @Test
    fun reportedFailureKeepsReasonAndOverlongTextIsTruncated() = runBlocking {
        val device = FakeDevice()
        device.reply = "{\"ok\":false,\"error\":\"boom\"}"
        assertEquals(ScreenStandbyOutcome.Failed("boom"), device.runner.run(ScreenStandbyAction.TurnOff))

        device.reply = "{\"ok\":false,\"error\":\"${"x".repeat(600)}\"}"
        val outcome = device.runner.run(ScreenStandbyAction.TurnOff)
        assertTrue(outcome is ScreenStandbyOutcome.Failed)
        assertEquals(501, (outcome as ScreenStandbyOutcome.Failed).reason.length)
        assertTrue(outcome.reason.endsWith("…"))

        // 边界：恰好 500 字符不截断；501 字符截断为「500 字符 + 省略号」
        device.reply = "{\"ok\":false,\"error\":\"${"y".repeat(500)}\"}"
        assertEquals(ScreenStandbyOutcome.Failed("y".repeat(500)), device.runner.run(ScreenStandbyAction.TurnOff))
        device.reply = "{\"ok\":false,\"error\":\"${"z".repeat(501)}\"}"
        assertEquals(
            ScreenStandbyOutcome.Failed("z".repeat(500) + "…"),
            device.runner.run(ScreenStandbyAction.TurnOff),
        )
    }

    @Test
    fun wakeFailureIsReportedWithoutRunningHelper() = runBlocking {
        val device = FakeDevice()
        device.wakeError = IOException("wake injection failed")
        assertEquals(
            ScreenStandbyOutcome.Failed("wake injection failed"),
            device.runner.run(ScreenStandbyAction.TurnOn),
        )
        assertTrue(device.execCommands.isEmpty())
        assertEquals(1, device.cleanupCommands.size)
    }

    @Test
    fun linkLostMessageIsReportedAsNotConnected() = runBlocking {
        val device = FakeDevice()
        device.execError = IllegalStateException("ADB connection is no longer available")
        assertEquals(ScreenStandbyOutcome.NotConnected, device.runner.run(ScreenStandbyAction.TurnOff))
        assertEquals(1, device.cleanupCommands.size)
    }

    @Test
    fun unrecognizedReplyOnDeadTransportIsReportedAsNotConnected() = runBlocking {
        // 连接在执行途中被断开时，读流会以「正常结束」返回空串或半截 JSON：
        // 不当作动作失败，而是按「未连接设备」上报（执行中途断连场景）
        val device = FakeDevice(transportAlive = false)
        device.reply = ""
        assertEquals(ScreenStandbyOutcome.NotConnected, device.runner.run(ScreenStandbyAction.TurnOff))
        device.reply = "{\"ok\":tru"
        assertEquals(ScreenStandbyOutcome.NotConnected, device.runner.run(ScreenStandbyAction.TurnOff))
        assertEquals(2, device.cleanupCommands.size)
    }

    @Test
    fun unrecognizedReplyOnLiveTransportStaysFailure() = runBlocking {
        // 链路还活着：认不出回执就是真失败，不能被归成「未连接」
        val device = FakeDevice()
        device.reply = ""
        assertEquals(
            ScreenStandbyOutcome.Failed("empty screen standby reply"),
            device.runner.run(ScreenStandbyAction.TurnOff),
        )
    }

    @Test
    fun cleanupFailureDoesNotChangeTheOutcome() = runBlocking {
        val device = FakeDevice()
        device.cleanupError = IOException("rm failed")
        assertEquals(ScreenStandbyOutcome.Done, device.runner.run(ScreenStandbyAction.TurnOff))
        assertEquals(1, device.cleanupCommands.size)
    }
}
