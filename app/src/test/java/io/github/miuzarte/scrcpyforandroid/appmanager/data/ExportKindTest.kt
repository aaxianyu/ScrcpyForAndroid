package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 产物形态只由安装包件数决定：一个包是 `.apk`，多件（base + split）是 `.apks`。 */
class ExportKindTest {
    @Test
    fun singlePartIsApk() {
        assertEquals(DeviceAppRepository.ExportKind.APK, DeviceAppRepository.exportKindOf(1))
    }

    @Test
    fun splitPartsAreApkSet() {
        assertEquals(DeviceAppRepository.ExportKind.APK_SET, DeviceAppRepository.exportKindOf(2))
        assertEquals(DeviceAppRepository.ExportKind.APK_SET, DeviceAppRepository.exportKindOf(4))
    }

    @Test
    fun extensionFollowsKind() {
        assertEquals("apk", DeviceAppRepository.exportKindOf(1).extension)
        assertEquals("apks", DeviceAppRepository.exportKindOf(3).extension)
    }
}
