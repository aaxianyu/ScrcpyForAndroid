package io.github.miuzarte.scrcpyforandroid.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 输入按各命令真实输出的换行、缩进与小数位构造，取值用合成样本，
 * 这样解析器一旦对格式做了多余假设，测试会直接炸。
 */
class DeviceInfoModelsTest {

    @Test
    fun `meminfo reads total and available`() {
        val snapshot = parseMemSnapshot(
            """
            MemTotal:        8000000 kB
            MemFree:          200000 kB
            MemAvailable:    3000000 kB
            """.trimIndent(),
        )
        assertEquals(8000000L, snapshot.totalKb)
        assertEquals(3000000L, snapshot.availableKb)
    }

    @Test
    fun `meminfo missing keys stay null`() {
        val snapshot = parseMemSnapshot("MemTotal: 1024 kB")
        assertEquals(1024L, snapshot.totalKb)
        assertNull(snapshot.availableKb)
    }

    @Test
    fun `battery reads level status voltage and temperature`() {
        val snapshot = parseBatterySnapshot(
            """
            Current Battery Service state:
              AC powered: true
              status: 3
              level: 72
              scale: 100
              voltage: 4100
              temperature: 300
            """.trimIndent(),
        )
        assertEquals(72, snapshot.level)
        assertEquals(3, snapshot.status)
        assertEquals(4100, snapshot.voltageMv)
        assertEquals(300, snapshot.temperatureTenthsC)
    }

    @Test
    fun `cpu temperature takes the hottest sensor`() {
        val output = """
            Current temperatures from HAL:
            Temperature{mValue=41.2, mType=0, mName=CPU0, mStatus=0}
            Temperature{mValue=43.0, mType=0, mName=CPU1, mStatus=0}
            Temperature{mValue=45.8, mType=0, mName=CPU7, mStatus=0}
            Temperature{mValue=30.0, mType=3, mName=battery, mStatus=0}
        """.trimIndent()
        assertEquals(45.8f, parseMaxCpuTemperature(output))
        assertNull(parseMaxCpuTemperature("Temperature{mValue=30.0, mType=3, mName=battery, mStatus=0}"))
    }

    @Test
    fun `cpu temperature accepts a sensor named cpu without an index`() {
        val output = "Temperature{mValue=39.5, mType=0, mName=CPU, mStatus=0}"
        assertEquals(39.5f, parseMaxCpuTemperature(output))
    }

    @Test
    fun `df snapshot skips the header and tolerates a mount path with spaces`() {
        val snapshot = parseDfSnapshot(
            """
            Filesystem       1K-blocks     Used Available Use% Mounted on
            /dev/block/dm-0  1000000  400000  600000  40% /storage/emulated/0/Android/obb
            """.trimIndent(),
        )
        assertEquals(1000000L, snapshot.totalKb)
        assertEquals(400000L, snapshot.usedKb)
        assertEquals(600000L, snapshot.availableKb)
    }

    @Test
    fun `display snapshot reads density ppi and refresh rates`() {
        val line =
            """mBaseDisplayInfo=DisplayInfo{"Display 1", displayId 0, real 720 x 1600, modes [{id=1, width=720, height=1600, fps=60.000004, alternativeRefreshRates=[90.0, 120.00001]}, {id=2, width=720, height=1600, fps=120.00001, alternativeRefreshRates=[60.000004, 90.0]}, {id=3, width=720, height=1600, fps=90.0}], density 320 (300.5 x 301.25) dpi, layerStack 0}"""
        val snapshot = parseDisplaySnapshot(line)
        assertEquals(320, snapshot.densityDpi)
        assertEquals(300.5f, snapshot.ppiX!!, 0.001f)
        assertEquals(301.25f, snapshot.ppiY!!, 0.001f)
        assertEquals(720, snapshot.realWidthPx)
        assertEquals(1600, snapshot.realHeightPx)
        assertEquals(listOf(60, 90, 120), snapshot.refreshRates)
    }

    @Test
    fun `display snapshot without real size leaves it null`() {
        val snapshot = parseDisplaySnapshot("  density 320 (300.5 x 300.5) dpi")
        assertNull(snapshot.realWidthPx)
        assertNull(snapshot.realHeightPx)
    }

    @Test
    fun `display snapshot without modes has no refresh rates`() {
        val snapshot = parseDisplaySnapshot("  density 320 (300.5 x 300.5) dpi")
        assertEquals(320, snapshot.densityDpi)
        assertEquals(emptyList<Int>(), snapshot.refreshRates)
    }

    @Test
    fun `ipv4 keeps global addresses and drops loopback`() {
        val output = """
            1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN group default qlen 1000
                inet 127.0.0.1/8 scope host lo
            2: wlan0: <UP,LOWER_UP> mtu 1500 qdisc mq state UNKNOWN group default qlen 1000
                inet 192.0.2.10/24 brd 192.0.2.255 scope global wlan0
            3: eth0: <UP,LOWER_UP> mtu 1500 qdisc mq state UNKNOWN group default qlen 1000
                inet 198.51.100.7/24 scope global eth0
        """.trimIndent()
        assertEquals(listOf("192.0.2.10", "198.51.100.7"), parseIpv4Addresses(output))
    }

    @Test
    fun `ipv6 keeps global addresses and drops link local`() {
        val output = """
            1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 state UNKNOWN qlen 1000
                inet6 ::1/128 scope host
            2: dummy0: <BROADCAST,NOARP,UP,LOWER_UP> mtu 1500 state UNKNOWN qlen 1000
                inet6 fe80::1/64 scope link
            3: wlan0: <UP,LOWER_UP> mtu 1500 state UNKNOWN qlen 1000
                inet6 2001:db8::1/64 scope global dynamic mngtmpaddr
        """.trimIndent()
        assertEquals(listOf("2001:db8::1"), parseIpv6Addresses(output))
    }

    @Test
    fun `uptime parses the first field and splits into days hours minutes`() {
        assertEquals(90_061L, parseUptimeSeconds("90061.25 1000.75"))
        assertNull(parseUptimeSeconds(""))
        assertEquals(Uptime(days = 1, hours = 1, minutes = 1), uptimeOf(90_061L))
    }

    @Test
    fun `wifi ssid handles quoted names disabled output and unknown placeholders`() {
        assertEquals("SampleNet", parseWifiSsid("Wifi is enabled\nWifiInfo SSID: \"SampleNet\", BSSID: 02:00:00:00:00:00"))
        assertEquals("SampleNet", parseWifiSsid("SSID: SampleNet, BSSID: 02:00:00:00:00:00"))
        assertNull(parseWifiSsid("Wifi is disabled\nWifi scanning is always available"))
        assertNull(parseWifiSsid("WifiInfo SSID: <unknown ssid>, BSSID: 02:00:00:00:00:00"))
    }

    @Test
    fun `pixels text splits wm output with a multiplication sign`() {
        assertEquals("720 × 1600", pixelsText("720x1600"))
        assertNull(pixelsText("1080"))
        assertNull(pixelsText(null))
    }

    @Test
    fun `gib text defaults to one decimal and supports two`() {
        assertEquals("7.2 GiB", gibText(7_600_000L * 1024L))
        assertEquals("7.25 GiB", gibText(7_600_000L * 1024L, decimals = 2))
        assertEquals("224.0 GiB", gibText(224L * 1024L * 1024L * 1024L))
    }

    @Test
    fun `screen inches prefers factory ppi over logical density`() {
        val fromPpi = screenInches(1440, 3200, 500f, 500f, 560)!!
        assertEquals(7.02, fromPpi, 0.005)
        val fromLogicalDensity = screenInches(720, 1600, null, null, 320)!!
        assertEquals(5.48, fromLogicalDensity, 0.005)
        assertNull(screenInches(0, 1600, 500f, 500f, 560))
        assertNull(screenInches(720, 1600, null, null, 0))
    }

    @Test
    fun `carrier and network type drop placeholders and commas`() {
        assertEquals("Operator A", parseCarrier("Operator A\n"))
        assertEquals("Operator A", parseCarrier("Operator A,Unknown"))
        assertEquals("Operator A / Operator B", parseCarrier("Operator A, Operator B, Operator A"))
        assertNull(parseCarrier(""))
        assertNull(parseCarrier("Unknown"))
        assertEquals("NR_SA", parseNetworkType("NR_SA,Unknown"))
    }

    @Test
    fun `battery current reads sysfs microamperes and falls back to dumpsys`() {
        assertEquals(-480000L, parseBatteryCurrent("-480000\n"))
        assertEquals(-480000L, parseBatteryCurrent("current now property: -480000\n"))
        assertNull(parseBatteryCurrent(""))
    }

    @Test
    fun `tenths celsius keeps whole degrees without a decimal`() {
        assertEquals("34", tenthsCelsiusText(340))
        assertEquals("28.5", tenthsCelsiusText(285))
        assertNull(tenthsCelsiusText(null))
    }
}