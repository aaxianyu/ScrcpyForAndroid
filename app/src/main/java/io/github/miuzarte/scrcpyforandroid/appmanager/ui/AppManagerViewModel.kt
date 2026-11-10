package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.miuzarte.scrcpyforandroid.appmanager.data.AppListUiState
import io.github.miuzarte.scrcpyforandroid.appmanager.data.DeviceAppRepository
import io.github.miuzarte.scrcpyforandroid.appmanager.data.LocalAppRepository
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.LocalApp
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用管理页视图模型。
 *
 * 职责划分：
 * - 列表 / 在途 / 忙碌等数据层状态直接来自进程作用域的 [DeviceAppRepository]；
 * - 显示偏好持久化到 AppSettings（切换即写，防抖）；
 * - 本机应用选择器的**选择集与搜索词**在此持有：Activity 重建时保留、选择器本体
 *   关闭态不保留；主动关闭时清空（三条路径语义不同）。
 */
class AppManagerViewModel(
    private val initialHideVersion: Boolean = Storage.appSettings.bundleState.value.appManagerHideVersion,
    private val initialHideApkSize: Boolean = Storage.appSettings.bundleState.value.appManagerHideApkSize,
    private val initialHideDisabled: Boolean = Storage.appSettings.bundleState.value.appManagerHideDisabled,
) : ViewModel() {

    val uiState: StateFlow<AppListUiState> = DeviceAppRepository.uiState

    // ------------------------------------------------------------ 显示偏好

    private val _hideVersion = MutableStateFlow(false)
    val hideVersion: StateFlow<Boolean> = _hideVersion.asStateFlow()

    private val _hideApkSize = MutableStateFlow(false)
    val hideApkSize: StateFlow<Boolean> = _hideApkSize.asStateFlow()

    /** 隐藏已停用应用：只影响应用管理页列表与搜索结果，本机选择器不显示该开关。 */
    private val _hideDisabled = MutableStateFlow(false)
    val hideDisabled: StateFlow<Boolean> = _hideDisabled.asStateFlow()

    private var prefsSaveJob: Job? = null

    init {
        _hideVersion.value = initialHideVersion
        _hideApkSize.value = initialHideApkSize
        _hideDisabled.value = initialHideDisabled
    }

    fun toggleHideVersion() {
        _hideVersion.value = !_hideVersion.value
        schedulePrefsSave()
    }

    fun toggleHideApkSize() {
        _hideApkSize.value = !_hideApkSize.value
        schedulePrefsSave()
    }

    fun toggleHideDisabled() {
        _hideDisabled.value = !_hideDisabled.value
        schedulePrefsSave()
    }

    private fun schedulePrefsSave() {
        prefsSaveJob?.cancel()
        prefsSaveJob = viewModelScope.launch {
            delay(PREFS_SAVE_DEBOUNCE_MS)
            Storage.appSettings.updateBundle {
                it.copy(
                    appManagerHideVersion = _hideVersion.value,
                    appManagerHideApkSize = _hideApkSize.value,
                    appManagerHideDisabled = _hideDisabled.value,
                )
            }
        }
    }

    // ------------------------------------------------------------ 本机选择器会话

    val localPickerSelection: SnapshotStateList<String> = mutableStateListOf()
    var localPickerQuery by mutableStateOf("")
        private set

    /** 选择器多选态：被确认弹窗挤占后保留，仅主动关闭清空。 */
    var localBatchMode by mutableStateOf(false)

    fun onLocalPickerQueryChange(query: String) {
        localPickerQuery = query
    }

    fun toggleLocalSelection(packageName: String) {
        if (!localPickerSelection.remove(packageName)) localPickerSelection.add(packageName)
    }

    /** 退出多选态时仅清选中集，保留搜索词。 */
    fun clearLocalSelection() {
        localPickerSelection.clear()
    }

    /**
     * 结束一次选择器会话：搜索词、选择集与多选态都回到初始。
     *
     * 打开与主动关闭都调用——中断过的安装会把上次的搜索词与勾选留在会话里，下一次从入口
     * 打开必须是干净的；只有「被安装确认框挤占后恢复」那条路径有意保留会话（用户还没选完）。
     */
    fun clearLocalPickerSession() {
        localPickerSelection.clear()
        localPickerQuery = ""
        localBatchMode = false
    }

    // ------------------------------------------------------------ 本机应用列表

    private val _localApps = MutableStateFlow<List<LocalApp>>(emptyList())
    val localApps: StateFlow<List<LocalApp>> = _localApps.asStateFlow()

    private val _localAppsLoading = MutableStateFlow(false)

    /** 本机列表加载中：选择器的下拉刷新指示与「刷新中列表为空」的转圈据此呈现。 */
    val localAppsLoading: StateFlow<Boolean> = _localAppsLoading.asStateFlow()

    /** 是否已成功加载过本机列表：未加载过时不显示空态，只出转圈。 */
    private val _localAppsLoaded = MutableStateFlow(false)
    val localAppsLoaded: StateFlow<Boolean> = _localAppsLoaded.asStateFlow()

    /** 本机图标代次：预热完成后 +1，推选择器行重组取图标。 */
    private val _localIconEpoch = MutableStateFlow(0)
    val localIconEpoch: StateFlow<Int> = _localIconEpoch.asStateFlow()

    /** 进行中的本机图标预热任务，重复加载时取消上一轮。 */
    private var localIconWarmJob: Job? = null

    private val _localAppsPermissionMissing = MutableStateFlow(false)
    val localAppsPermissionMissing: StateFlow<Boolean> = _localAppsPermissionMissing.asStateFlow()

    /** 本机枚举超时未回：转圈换明确提示，后台那次枚举仍在跑（见 [loadLocalApps]）。 */
    private val _localAppsSlow = MutableStateFlow(false)
    val localAppsSlow: StateFlow<Boolean> = _localAppsSlow.asStateFlow()

    /** 本机枚举代次：慢之后用户可能又拉一次，旧结果回来不写回。 */
    private var localLoadGeneration = 0

    fun loadLocalApps() {
        val generation = ++localLoadGeneration
        _localAppsLoading.value = true
        DeviceAppRepository.loadLocalApps { result ->
            // 代次收口：枚举慢了之后用户可能又拉了一次，回来的旧结果不能再写
            if (generation != localLoadGeneration) return@loadLocalApps
            // 回调一到就收：置位在枚举返回的那一刻，不等图标预热（预热是后台的）
            _localAppsLoading.value = false
            when (result) {
                is LocalAppRepository.Result.Success -> {
                    _localApps.value = result.apps
                    _localAppsPermissionMissing.value = false
                    _localAppsLoaded.value = true
                    _localAppsSlow.value = false
                    warmLocalIcons(result.apps)
                }

                LocalAppRepository.Result.PermissionMissing -> {
                    _localApps.value = emptyList()
                    _localAppsPermissionMissing.value = true
                    _localAppsLoaded.value = true
                    _localAppsSlow.value = false
                }

                LocalAppRepository.Result.Slow -> {
                    // 不碰列表与 loaded：后台那次枚举还在跑，回来会覆盖这个状态
                    _localAppsSlow.value = true
                }
            }
        }
    }

    /**
     * 后台预热本机应用图标：行内图标读取是跨进程 Binder 调用，
     * 在 IO 线程整份灌进 LRU，完成后按代次推一次重组。
     * 列表渲染不等它（加载速度不变），未预热的行先出占位图。
     */
    private fun warmLocalIcons(apps: List<LocalApp>) {
        localIconWarmJob?.cancel()
        localIconWarmJob = viewModelScope.launch(Dispatchers.IO) {
            // 逐包确保活性：连续刷新 / 换设备时立即可中断。
            for (app in apps) {
                ensureActive()
                DeviceAppRepository.warmIcon(app.packageName)
            }
            _localIconEpoch.value = _localIconEpoch.value + 1
        }
    }

    private companion object {
        const val PREFS_SAVE_DEBOUNCE_MS = 300L
    }
}
