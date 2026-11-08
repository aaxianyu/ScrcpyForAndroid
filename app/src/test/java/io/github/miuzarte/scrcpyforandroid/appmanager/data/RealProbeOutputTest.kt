package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真机（Motorola / Android 15，467 个应用）捕获的探测输出驱动解析器，
 * 防止 JSON 契约在设备侧悄悄漂移（例如字段间缺逗号、中文标签编码）。
 */
class RealProbeOutputTest {

    @Test
    fun `parses real device probe output`() {
        val raw = readResource("appmanager/probe-real-sample.json")
        val result = ProbePayloadParser.parse(raw)
        assertTrue("expected success but was $result", result is ProbePayloadParser.Result.Success)
        val apps = (result as ProbePayloadParser.Result.Success).apps
        assertEquals(3, apps.size)

        val hidden = apps[0]
        assertEquals("com.android.internal.display.cutout.emulation.noCutout", hidden.packageName)
        assertEquals("隐藏", hidden.label)
        assertEquals("1.0", hidden.versionName)
        assertEquals(29022L, hidden.apkSizeBytes)
        assertEquals(true, hidden.isSystem)
        assertEquals(true, hidden.isEnabled)

        val antitheft = apps[1]
        assertEquals("查找手机", antitheft.label)
        assertEquals("11.1.0.0.099", antitheft.versionName)
        assertEquals(25982209L, antitheft.apkSizeBytes)

        val tethering = apps[2]
        assertEquals("Tethering", tethering.label)
        assertEquals("14-11267313", tethering.versionName)
    }

    @Test
    fun `decodes real icon payloads`() {
        val raw = readResource("appmanager/probe-real-sample.json")
        val result = ProbePayloadParser.parse(raw) as ProbePayloadParser.Result.Success
        result.iconBytes.forEach { (packageName, bytes) ->
            assertTrue("$packageName icon is empty", bytes.isNotEmpty())
        }
        assertEquals(3, result.iconBytes.size)
    }

    private fun readResource(path: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "test resource missing: $path"
        }.use { it.readBytes().toString(Charsets.UTF_8) }
}
