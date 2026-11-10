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

    @Test
    fun `running state unknown means nothing is running`() {
        // runningPackages 默认 null = 这次没读到 ps -A：不显示「运行中」
        val state = AppListUiState(apps = listOf(app))
        assertEquals(null, state.runningPackages)
        assertEquals(false, state.runningPackages.isRunning("com.a"))
    }

    @Test
    fun `empty running set is not the same as unknown`() {
        // 空集合 = 确实没有应用在跑（强制停止全部置灰），与 null 语义不同
        val state = AppListUiState(apps = listOf(app), runningPackages = emptySet())
        assertEquals(emptySet<String>(), state.runningPackages)
        assertEquals(false, state.runningPackages.isRunning("com.a"))
    }

    @Test
    fun `running package is reported`() {
        val state = AppListUiState(apps = listOf(app), runningPackages = setOf("com.a"))
        assertEquals(true, state.runningPackages.isRunning("com.a"))
        assertEquals(false, state.runningPackages.isRunning("com.b"))
    }

    @Test
    fun `filtered empty is decided by the list page not by content`() {
        // content 只看数据层状态；「本组有应用但被隐藏已停用滤光」由列表页自己判定
        val state = AppListUiState(apps = listOf(app.copy(isEnabled = false)))
        assertEquals(AppListContent.List, state.content)
    }
}
