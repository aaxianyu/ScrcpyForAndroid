package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ADB not connected` 这句话在两种成因下都会出现（外部客户端占了通道 / 链路自己断了），
 * 归一必须看传输层此刻是否还活着，否则会在断连时替用户编一个占用人。
 */
class AdbErrorsTest {

    @Test
    fun `channel refusal with a live transport is reported as occupied`() {
        val error = AdbErrors.classify("ADB not connected", transportAlive = true)
        assertEquals(AppManagerError.ChannelOccupied("ADB not connected"), error)
    }

    @Test
    fun `same string on a dead transport is not blamed on another client`() {
        val error = AdbErrors.classify("ADB not connected", transportAlive = false)
        assertEquals(AppManagerError.NotConnected, error)
    }

    @Test
    fun `the device phrase is matched regardless of case`() {
        assertTrue(AdbErrors.classify("adb not connected", transportAlive = true) is AppManagerError.ChannelOccupied)
        assertEquals(
            AppManagerError.NotConnected,
            AdbErrors.classify("Adb Not Connected", transportAlive = false),
        )
    }

    @Test
    fun `the phrase is also recognised inside a longer device line`() {
        assertTrue(
            AdbErrors.classify(
                "error: cannot run: ADB not connected",
                transportAlive = true,
            ) is AppManagerError.ChannelOccupied,
        )
    }

    @Test
    fun `other device failures pass through trimmed regardless of transport`() {
        assertEquals(
            AppManagerError.Device("Failure [DELETE_FAILED_INTERNAL_ERROR]"),
            AdbErrors.classify(
                "  Failure [DELETE_FAILED_INTERNAL_ERROR]  ",
                transportAlive = true,
            ),
        )
        assertEquals(
            AppManagerError.Device("Failure [DELETE_FAILED_INTERNAL_ERROR]"),
            AdbErrors.classify("Failure [DELETE_FAILED_INTERNAL_ERROR]", transportAlive = false),
        )
    }

    @Test
    fun `an empty reason keeps a non-empty placeholder`() {
        assertEquals(AppManagerError.Device("unknown error"), AdbErrors.classify("", transportAlive = true))
        assertEquals(AppManagerError.Device("unknown error"), AdbErrors.classify("   ", transportAlive = false))
    }

    @Test
    fun `the slow hint is its own distinct state`() {
        // 它不是失败：后台那次加载仍在跑，数据到齐会覆盖这个状态。这里钉住「它和
        // 真·失败是彼此不同的值」，UI 才能一个停转圈、一个报错误
        assertEquals(AppManagerError.Slow, AppManagerError.Slow)
        assertTrue(AppManagerError.Slow != AppManagerError.NotConnected)
        assertTrue(AppManagerError.Slow != AppManagerError.Device("boom"))
    }

    @Test
    fun `the slow budget leaves headroom over the measured 723-app load`() {
        // 实测 723 个应用走 app 自研 ADB 约 12 秒；低于它会把正常路径误判成卡死
        assertTrue(
            "LOAD_TIMEOUT_MS must exceed the measured 12s worst case",
            DeviceAppRepository.LOAD_TIMEOUT_MS > 12_000L,
        )
    }
}
