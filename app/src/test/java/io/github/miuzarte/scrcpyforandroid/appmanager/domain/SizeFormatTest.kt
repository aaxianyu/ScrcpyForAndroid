package io.github.miuzarte.scrcpyforandroid.appmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeFormatTest {

    @Test
    fun `bytes below one kib have no decimal`() {
        assertEquals("0 B", SizeFormat.humanReadable(0))
        assertEquals("512 B", SizeFormat.humanReadable(512))
        assertEquals("1,023 B", SizeFormat.humanReadable(1023))
    }

    @Test
    fun `kib and above use one decimal and thousands separator`() {
        assertEquals("1.0 KB", SizeFormat.humanReadable(1024))
        assertEquals("1.2 MB", SizeFormat.humanReadable(1_234_567))
        assertEquals("1.5 GB", SizeFormat.humanReadable(1024L * 1024L * 1536L))
    }

    @Test
    fun `exact multiples scale to the next unit`() {
        assertEquals("1.0 KB", SizeFormat.humanReadable(1024))
        assertEquals("1.0 MB", SizeFormat.humanReadable(1024L * 1024L))
        assertEquals("2.0 GB", SizeFormat.humanReadable(1024L * 1024L * 1024L * 2L))
    }
}
