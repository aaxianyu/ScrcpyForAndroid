package io.github.miuzarte.scrcpyforandroid.appmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {

    private val app = DeviceApp(
        packageName = "com.example.2048",
        label = "2048 游戏",
        versionName = "2.1",
        apkSizeBytes = 4096L,
        isSystem = false,
        isEnabled = true,
    )

    @Test
    fun `empty query matches everything`() {
        assertTrue(AppSearch.matches(app, ""))
        assertTrue(AppSearch.matches(app, "   "))
    }

    @Test
    fun `matches label case insensitively`() {
        assertTrue(AppSearch.matches(app, "2048"))
        assertTrue(AppSearch.matches(app, "2048 游戏"))
    }

    @Test
    fun `matches package name`() {
        assertTrue(AppSearch.matches(app, "com.example"))
        assertTrue(AppSearch.matches(app, "EXAMPLE.2048"))
    }

    @Test
    fun `leading and trailing whitespace is trimmed before matching`() {
        // IME 残留的尾随空格不应导致无命中
        assertTrue(AppSearch.matches(app, "  2048  "))
        assertTrue(AppSearch.matches(app, "2048 游戏 "))
    }

    @Test
    fun `inner whitespace is preserved`() {
        // 只修剪首尾；中间多一个空格仍然不命中
        assertFalse(AppSearch.matches(app, "2048  游戏"))
    }

    @Test
    fun `unrelated query does not match`() {
        assertFalse(AppSearch.matches(app, "wechat"))
    }

    @Test
    fun `filter keeps query order of the source list`() {
        val other = app.copy(packageName = "com.other.2048", label = "Another 2048")
        val filtered = AppSearch.filter(listOf(app, other), "another")
        assertEquals(listOf("com.other.2048"), filtered.map { it.packageName })
    }
}
