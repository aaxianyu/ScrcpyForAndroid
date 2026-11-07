package io.github.miuzarte.scrcpyforandroid.appmanager.data

import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp

/**
 * 列表页状态。
 *
 * 优先级（同一时刻可满足多个时）：NotConnected > Loading > Error > Empty > List；
 * 列表已有数据时任何失败都不切空态（不清空用户眼前的数据）。
 */
data class AppListUiState(
    val apps: List<DeviceApp> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val notConnected: Boolean = false,
    val error: AppManagerError? = null,
    val pendingPackages: Set<String> = emptySet(),
    val installing: Boolean = false,
    val loadingLocalApps: Boolean = false,
    /**
     * 图标版本号：每次成功拉到列表（图标落盘）后 +1。
     *
     * 列表内容和缓存完全相同时 MutableStateFlow 不发射，UI 不会重组，而图标是刚落盘的——
     * 不重组就永远读不到，界面一直停在占位图标。靠这个字段强制推一次状态变化。
     */
    val iconEpoch: Int = 0,
) {
    /**
     * 有任意操作 / 安装 / 拉取本机列表在进行中：
     * 顶栏安装按钮、批量操作栏整栏、本机选择器右上对勾据此禁用。
     */
    val busy: Boolean
        get() = installing || loadingLocalApps || pendingPackages.isNotEmpty()

    val content: AppListContent
        get() = when {
            apps.isNotEmpty() -> AppListContent.List
            notConnected -> AppListContent.NotConnected
            isLoading -> AppListContent.Loading
            error != null -> AppListContent.Error
            else -> AppListContent.Empty
        }

    fun isPending(packageName: String): Boolean = packageName in pendingPackages
}

enum class AppListContent { List, Loading, NotConnected, Error, Empty }
