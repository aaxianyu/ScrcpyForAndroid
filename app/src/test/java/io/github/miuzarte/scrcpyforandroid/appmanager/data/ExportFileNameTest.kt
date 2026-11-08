package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFileNameTest {
    @Test
    fun labelPrefixesPackageName() {
        assertEquals("2048_com.game2048.apk", exportFileName("2048", "com.game2048", "apk"))
    }

    @Test
    fun keepsChineseLabel() {
        assertEquals(
            "红果短剧_com.phoenix.read.oversea.gp.apks",
            exportFileName("红果短剧", "com.phoenix.read.oversea.gp", "apks"),
        )
    }

    @Test
    fun blanksBecomeSingleSeparator() {
        assertEquals("My_App_com.example.apk", exportFileName("My   App", "com.example", "apk"))
    }

    @Test
    fun illegalCharsBecomeSeparator() {
        assertEquals(
            "a_b_c_d_e_f_g_h_i_j_com.example.apk",
            exportFileName("a/b\\c:d*e?f\"g<h>i|j", "com.example", "apk"),
        )
    }

    @Test
    fun trimsEdgesAndTrailingDot() {
        assertEquals("My_App_com.example.apk", exportFileName(" ..My App.. ", "com.example", "apk"))
    }

    @Test
    fun emptyLabelFallsBackToPackageName() {
        assertEquals("com.example.apk", exportFileName("", "com.example", "apk"))
        assertEquals("com.example.apk", exportFileName("///", "com.example", "apk"))
    }

    @Test
    fun capsLongLabel() {
        val name = exportFileName("a".repeat(60), "com.example", "apk")

        assertEquals("a".repeat(40) + "_com.example.apk", name)
    }

    @Test
    fun truncationDoesNotKeepTrailingDot() {
        val name = exportFileName("a".repeat(39) + ".bc", "com.x", "apk")

        assertEquals("a".repeat(39) + "_com.x.apk", name)
    }

    @Test
    fun capsWholeNameBytes() {
        val name = exportFileName("字".repeat(40), "com.example.long.package.name", "apks")

        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 200)
    }

    @Test
    fun keepsPackageTailWhenNameOverflows() {
        val name = exportFileName("字".repeat(40), "com.example", "apk")

        assertTrue(name, name.endsWith("_com.example.apk"))
        assertTrue(name, name.toByteArray(Charsets.UTF_8).size <= 200)
    }

    @Test
    fun neverSplitsSurrogatePair() {
        val label = "a".repeat(39) + String(Character.toChars(0x1F600))
        val name = exportFileName(label, "com.example", "apk")

        assertFalse(name, name.hasLoneSurrogate())
        assertEquals("a".repeat(39) + "_com.example.apk", name)
    }

    @Test
    fun byteLimitDoesNotSplitSurrogatePair() {
        val label = String(Character.toChars(0x1F600)).repeat(60)
        val name = exportFileName(label, "com.example", "apks")

        assertFalse(name, name.hasLoneSurrogate())
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 200)
    }

    @Test
    fun byteLimitTrimsLabelBudget() {
        val packageName = "com.example." + "a".repeat(70)
        val name = exportFileName("字".repeat(40), packageName, "apks")

        assertTrue(name, name.endsWith("_$packageName.apks"))
        assertTrue(name, name.toByteArray(Charsets.UTF_8).size <= 200)
    }

    @Test
    fun extensionSurvivesOverlongPackageName() {
        val name = exportFileName("2048", "a".repeat(210), "apk")

        assertTrue(name, name.endsWith(".apk"))
        assertTrue(name, name.toByteArray(Charsets.UTF_8).size <= 200)
    }

    private fun String.hasLoneSurrogate(): Boolean {
        var index = 0
        while (index < length) {
            val char = this[index]
            if (char.isHighSurrogate()) {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return true
                index += 2
            } else if (char.isLowSurrogate()) {
                return true
            } else {
                index++
            }
        }
        return false
    }
}
