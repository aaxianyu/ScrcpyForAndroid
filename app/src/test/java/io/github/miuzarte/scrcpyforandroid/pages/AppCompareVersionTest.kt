package io.github.miuzarte.scrcpyforandroid.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCompareVersionTest {

    /** 只关心方向，具体差值大小无关。 */
    private fun sign(left: String, right: String): Int =
        compareVersionNames(left, right).coerceIn(-1, 1)

    @Test
    fun `numeric segments compare as numbers`() {
        assertEquals(1, sign("1.10", "1.9"))
        assertEquals(-1, sign("1.9", "1.10"))
        assertEquals(0, sign("2.0.0", "2.0"))
        // 缺的段按 0 算：2 与 2.0.1 比到第三段 0 < 1
        assertEquals(-1, sign("2", "2.0.1"))
    }

    @Test
    fun `leading v is ignored`() {
        assertEquals(0, sign("v2.0", "2.0"))
        assertEquals(0, sign("V1.2.3", "1.2.3"))
        assertEquals(1, sign("v1.3.0", "1.2.9"))
    }

    @Test
    fun `prerelease sorts below the release`() {
        assertEquals(-1, sign("1.0.0-beta2", "1.0.0"))
        assertEquals(1, sign("1.0.0", "1.0.0-beta2"))
        assertEquals(-1, sign("1.0.0-rc1", "1.0.0"))
        assertEquals(-1, sign("1.0.0-alpha1", "1.0.0-alpha2"))
    }

    @Test
    fun `prerelease numbers order within the same suffix`() {
        assertEquals(1, sign("1.0.0-beta3", "1.0.0-beta2"))
        assertEquals(-1, sign("1.0.0-rc1", "1.0.0-rc2"))
        assertTrue(sign("1.0.0-beta2", "1.0.0-alpha1") > 0)
    }

    @Test
    fun `missing version counts as zero`() {
        assertEquals(1, sign("1.0", ""))
        assertEquals(-1, sign("", "1.0"))
        assertEquals(0, sign("", ""))
    }
}