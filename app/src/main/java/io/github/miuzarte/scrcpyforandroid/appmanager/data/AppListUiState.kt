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
    /**
     * 有进程在跑的应用包名集合；**null = 运行态未知**（这次没读到 `ps -A`）。
     *
     * 未知与空集合必须分开：空集合表示「确实没有应用在跑」，此时所有应用的强制停止都该置灰；
     * 未知只是这次没读到，按产品约定强制停止仍然可点、也不显示「运行中」标签。
     */
    val runningPackages: Set<String>? = null,
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

/**
 * 运行集合里某包是否在跑；集合为 null（未知）时一律 false——未知不当作运行中。
 *
 * 界面层拿到的往往只是 [AppListUiState.runningPackages] 本身（列表行、搜索结果），
 * 语义判定收在这一个函数里，免得每处各写一遍 `?.contains(...) == true`。
 */
internal fun Set<String>?.isRunning(packageName: String): Boolean =
    this?.contains(packageName) == true

/**
 * 三态版本：null = 运行态未知，true / false 才是确定的结论。
 *
 * 「强制停止」是否置灰需要这个区别：未知时不置灰（仍可点），确定没在跑才置灰。
 */
internal fun Set<String>?.runningOrNull(packageName: String): Boolean? =
    this?.contains(packageName)

enum class AppListContent { List, Loading, NotConnected, Error, Empty, FilteredEmpty }
