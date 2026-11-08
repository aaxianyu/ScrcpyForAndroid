package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 缓存的承重逻辑：设备目录名清洗、跨设备内存 key 隔离、解码采样倍数。
 *
 * 这三处都不碰 Context / 文件系统 / 位图，因此可在纯 JVM 下覆盖。
 */
class DeviceAppCacheKeysTest {

    // ---------------------------------------------------------------- 设备目录名

    @Test
    fun `a plain host and port keeps its digits and dots`() {
        assertEquals("192.168.102.35_5555", DeviceAppCache.sanitizeKey("192.168.102.35:5555"))
    }

    @Test
    fun `usb targets keep their dashes and underscores`() {
        assertEquals("usb_1-2_3", DeviceAppCache.sanitizeKey("usb_1-2_3"))
    }

    @Test
    fun `separators and traversal segments stay inside one directory level`() {
        // '/' 与 '\' 都不在合法字符表里，一律换成 '_'：多层路径被压成单个目录名
        val sanitized = DeviceAppCache.sanitizeKey("../../etc/passwd")
        assertEquals(".._.._etc_passwd", sanitized)
        assertEquals(false, sanitized.contains('/'))
        assertEquals(false, sanitized.contains('\\'))
    }

    @Test
    fun `a dot-only device key falls back like an empty key`() {
        // '.' 逐字符都合法，但纯点串落到目录上会变成上级引用，与空串同样兜底成 unknown
        assertEquals("unknown", DeviceAppCache.sanitizeKey(".."))
        assertEquals("unknown", DeviceAppCache.sanitizeKey("."))
    }

    @Test
    fun `an empty key falls back to a usable directory name`() {
        assertEquals("unknown", DeviceAppCache.sanitizeKey(""))
    }

    @Test
    fun `a key made only of rejected characters keeps one underscore per character`() {
        // ifEmpty 只在结果为空串时兜底；每个被拒字符各换一个 '_'，所以是 "__" 而不是 "unknown"
        assertEquals("__", DeviceAppCache.sanitizeKey("/:"))
        assertEquals("___", DeviceAppCache.sanitizeKey("/::"))
    }

    @Test
    fun `keys differing only in a rejected character share one directory`() {
        // ':' 与 '/' 都映射到 '_'，这两种写法落到同一目录名。设备标识的实际形态是
        // host:port 与 usb:id，不含 '/'，真实数据上不会发生；这里把现状钉住。
        assertEquals(DeviceAppCache.sanitizeKey("a:b"), DeviceAppCache.sanitizeKey("a/b"))
    }

    @Test
    fun `distinct device keys never collapse onto one directory`() {
        val keys = listOf("192.168.0.2:5555", "192.168.0.2:5556", "usb_1", "usb_2", "unknown")
        assertEquals(keys.size, keys.map { DeviceAppCache.sanitizeKey(it) }.toSet().size)
    }

    // ---------------------------------------------------------------- 跨设备隔离

    @Test
    fun `the same package on two devices gets two distinct memory keys`() {
        assertNotEquals(
            DeviceAppCache.memoryKey("192.168.0.2:5555", "com.a"),
            DeviceAppCache.memoryKey("192.168.0.3:5555", "com.a"),
        )
    }

    @Test
    fun `the same device and package always maps to one key`() {
        assertEquals(
            DeviceAppCache.memoryKey("dev", "com.a"),
            DeviceAppCache.memoryKey("dev", "com.a"),
        )
    }

    @Test
    fun `a device key is a distinguishable prefix so eviction can target one device`() {
        assertEquals("dev/com.a", DeviceAppCache.memoryKey("dev", "com.a"))
        assertEquals(true, DeviceAppCache.memoryKey("dev", "com.a").startsWith("dev/"))
        assertEquals(false, DeviceAppCache.memoryKey("dev2", "com.a").startsWith("dev/"))
    }

    // ---------------------------------------------------------------- 解码采样

    @Test
    fun `a sample size of one keeps icons already at or below the target`() {
        assertEquals(1, DeviceAppCache.sampleSizeFor(128, 128))
        assertEquals(1, DeviceAppCache.sampleSizeFor(64, 64))
        assertEquals(1, DeviceAppCache.sampleSizeFor(0, 0))
    }

    @Test
    fun `unknown bounds decode unsampled rather than throwing`() {
        assertEquals(1, DeviceAppCache.sampleSizeFor(-1, 512))
        assertEquals(1, DeviceAppCache.sampleSizeFor(512, -1))
    }

    @Test
    fun `larger icons halve until both edges stay at or above the target`() {
        assertEquals(2, DeviceAppCache.sampleSizeFor(256, 256))
        assertEquals(4, DeviceAppCache.sampleSizeFor(512, 512))
        assertEquals(8, DeviceAppCache.sampleSizeFor(1024, 1024))
    }

    @Test
    fun `one short edge holds the whole sample size back`() {
        // 规则要求「两条边在折半后都仍然 ≥ 目标」，任一条边不达标就不再翻倍，
        // 因此解码出的图不会有一边小于目标尺寸
        assertEquals(1, DeviceAppCache.sampleSizeFor(1024, 64))
        assertEquals(1, DeviceAppCache.sampleSizeFor(256, 200))
        // 短边仍够折半一次时，折半照常发生
        assertEquals(2, DeviceAppCache.sampleSizeFor(512, 300))
    }
}
