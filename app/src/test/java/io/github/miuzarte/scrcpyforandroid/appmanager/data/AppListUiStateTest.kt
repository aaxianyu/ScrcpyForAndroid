package io.github.miuzarte.scrcpyforandroid.appmanager.data

import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListUiStateTest {

    private val app = DeviceApp("com.a", "A", "1.0", 1L, false, true)

    @Test
    fun `list wins whenever data is present`() {
        val state = AppListUiState(apps = listOf(app), isLoading = true, notConnected = true, error = AppManagerError.NotConnected)
        assertEquals(AppListContent.List, state.content)
    }

    @Test
    fun `not connected beats loading and error`() {
        val state = AppListUiState(isLoading = true, notConnected = true, error = AppManagerError.Device("boom"))
        assertEquals(AppListContent.NotConnected, state.content)
    }

    @Test
    fun `loading beats error and empty`() {
        val state = AppListUiState(isLoading = true, error = AppManagerError.Device("boom"))
        assertEquals(AppListContent.Loading, state.content)
    }

    @Test
    fun `error beats empty`() {
        val state = AppListUiState(error = AppManagerError.Device("boom"))
        assertEquals(AppListContent.Error, state.content)
    }

    @Test
    fun `nothing left means empty`() {
        assertEquals(AppListContent.Empty, AppListUiState().content)
    }

    @Test
    fun `pending packages freeze rows`() {
        val state = AppListUiState(apps = listOf(app), pendingPackages = setOf("com.a"))
        assertTrue(state.isPending("com.a"))
        assertEquals(true, state.busy)
    }
}
