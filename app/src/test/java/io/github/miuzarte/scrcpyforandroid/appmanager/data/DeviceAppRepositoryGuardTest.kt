package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAppRepositoryGuardTest {

    @Test
    fun shouldAutoLoadOnEnter_trueWhenEverythingClean() {
        assertTrue(
            DeviceAppRepository.shouldAutoLoadOnEnter(
                appsEmpty = true,
                error = null,
                notConnected = false,
                loading = false,
                lastCompletedKey = false,
            ),
        )
    }

    @Test
    fun shouldAutoLoadOnEnter_falseWhenLastCompleted() {
        assertFalse(
            DeviceAppRepository.shouldAutoLoadOnEnter(
                appsEmpty = true,
                error = null,
                notConnected = false,
                loading = false,
                lastCompletedKey = true,
            ),
        )
    }

    @Test
    fun shouldAutoLoadOnEnter_falseWhenInError() {
        assertFalse(
            DeviceAppRepository.shouldAutoLoadOnEnter(
                appsEmpty = true,
                error = AppManagerError.Device("boom"),
                notConnected = false,
                loading = false,
                lastCompletedKey = false,
            ),
        )
    }

    @Test
    fun shouldAutoLoadOnEnter_falseWhenNotConnected() {
        assertFalse(
            DeviceAppRepository.shouldAutoLoadOnEnter(
                appsEmpty = true,
                error = null,
                notConnected = true,
                loading = false,
                lastCompletedKey = false,
            ),
        )
    }

    @Test
    fun shouldAutoLoadOnEnter_falseWhileLoading() {
        assertFalse(
            DeviceAppRepository.shouldAutoLoadOnEnter(
                appsEmpty = true,
                error = null,
                notConnected = false,
                loading = true,
                lastCompletedKey = false,
            ),
        )
    }

    @Test
    fun shouldAutoLoadOnEnter_falseWhenAppsNonEmpty() {
        assertFalse(
            DeviceAppRepository.shouldAutoLoadOnEnter(
                appsEmpty = false,
                error = null,
                notConnected = false,
                loading = false,
                lastCompletedKey = false,
            ),
        )
    }

    @Test
    fun commitKey_returnsFrozenKeyWhenGenerationAndDeviceBothMatch() {
        assertEquals(
            "192.168.1.5:5555",
            DeviceAppRepository.commitKey(
                generation = 3,
                latestGeneration = 3,
                loadKey = "192.168.1.5:5555",
                currentDeviceKey = "192.168.1.5:5555",
            ),
        )
    }

    @Test
    fun commitKey_nullWhenDeviceSwitchedEvenWithLatestGeneration() {
        // 切设备的那一步先改 deviceKey、新一次 load 的 ++loadGeneration 还没跑到：
        // 只看代次会放过旧协程，让它按新 deviceKey 落盘旧清单
        assertNull(
            DeviceAppRepository.commitKey(
                generation = 3,
                latestGeneration = 3,
                loadKey = "192.168.1.5:5555",
                currentDeviceKey = "192.168.1.9:5555",
            ),
        )
    }

    @Test
    fun commitKey_nullWhenNewerLoadStartedOnSameDevice() {
        assertNull(
            DeviceAppRepository.commitKey(
                generation = 3,
                latestGeneration = 4,
                loadKey = "192.168.1.5:5555",
                currentDeviceKey = "192.168.1.5:5555",
            ),
        )
    }

    @Test
    fun commitKey_treatsUnsetDeviceKeyAsUnknownDevice() {
        assertEquals(
            "unknown",
            DeviceAppRepository.commitKey(
                generation = 1,
                latestGeneration = 1,
                loadKey = "unknown",
                currentDeviceKey = null,
            ),
        )
    }
}
