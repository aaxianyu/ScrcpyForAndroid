package io.github.miuzarte.scrcpyforandroid.pages

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.data.AppManagerError
import io.github.miuzarte.scrcpyforandroid.appmanager.data.DeviceAppRepository
import io.github.miuzarte.scrcpyforandroid.appmanager.data.LocalApkInstall
import io.github.miuzarte.scrcpyforandroid.appmanager.data.TransferProgress
import io.github.miuzarte.scrcpyforandroid.appmanager.data.currentDeviceKey
import io.github.miuzarte.scrcpyforandroid.appmanager.data.exportFileName
import io.github.miuzarte.scrcpyforandroid.appmanager.data.runningOrNull
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppAction
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppOrdering
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.LocalApp
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppActionSheet
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppConfirmDialog
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppManagerBottomSlot
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppManagerListPage
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppManagerMotion
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppManagerViewModel
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.appSectionHeaderHeight
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.DisplayOptionsMenu
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.FastScroller
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.InstallSourceSheet
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.LocalAppPickerSheet
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.MaskedTopBarAction
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.SearchOverlay
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.TransferProgressDialog
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.bottomSlotClearance
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.rememberAnimationsDisabled
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.rememberGlassHighlight
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.scaledBy
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.longPress
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.ui.rememberPageScrollBehavior
import io.github.miuzarte.scrcpyforandroid.ui.rememberPressInRootModifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 待确认的操作（单个 / 批量共用一条确认链路）。 */
private data class ConfirmRequest(
    val action: AppAction,
    val app: DeviceApp?,
    val batchCount: Int,
    val isInstall: Boolean,
    /**
     * 安装时要装的本机应用快照。
     *
     * 点确认的瞬间从选择集算出并存进请求，执行阶段只读这份快照——弹窗收起后的选择集与
     * 本机列表都不再参与。安装是进程作用域的长任务，出结果时列表可能已经重新加载过。
     */
    val installApps: List<LocalApp> = emptyList(),
    /** 选中项里带 split 分包的应用个数：确认框据此加一句提示，不必再问一次设备。 */
    val splitCount: Int = 0,
)

/**
 * 应用管理页。
 *
 * 状态归属：
 * - 列表 / 在途 / 忙碌 → [DeviceAppRepository]（进程作用域，离开页面不取消）；
 * - 批量选择、搜索过场、弹层开关 → 页面 remember（旋屏不保留）；
 * - 当前分组页、本机选择器的选择集与搜索词 → Saveable / ViewModel（保留）；
 * - 显示偏好 → AppSettings（持久化）。
 */
@Composable
internal fun AppManagerScreen(
    onBackToDevices: () -> Unit,
) {
    // 顶栏滚动行为由本页自持：退出再进入时状态随组合重建，标题首帧即展开态
    val scrollBehavior = rememberPageScrollBehavior()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val navigator = LocalRootNavigator.current
    val snackbar = LocalSnackbarController.current
    val viewModel: AppManagerViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val animationsDisabled = rememberAnimationsDisabled()

    val asBundle by appSettings.bundleState.collectAsState()
    val blurMode = asBundle.blur
    val blurEnabled = blurMode != AppSettings.BlurMode.NONE
    val floatingBarSetting = asBundle.floatingBottomBar
    val glassSwitch = asBundle.floatingBottomBarBlur

    val uiState by viewModel.uiState.collectAsState()
    val hideVersion by viewModel.hideVersion.collectAsState()
    val hideApkSize by viewModel.hideApkSize.collectAsState()

    // 顶栏按钮的可用性跟列表可见性对齐：缓存里已有清单时即使探针报未连接，用户看得见
    // 列表就应该能搜索 / 筛选 / 安装。
    // notConnected 与 apps 可以同时成立——前者在 NotConnected 时被置真，_apps 不清空。
    val actionsEnabled = uiState.apps.isNotEmpty()

    // 同一重组内共用这一次采样：onEnter 与 key-change 复位列表位置共享，避免 2× connectedAddress
    val pageDeviceKey = currentDeviceKey()

    // 进入页面：切设备或列表为空才加载；已有数据直接呈现。
    // SideEffect 每次重组都校验 device key，因此从返回栈恢复页面、或中途换了设备连接时，
    // 缓存立即按当前 key 读写，不会把新设备的数据写进旧设备目录。onEnter 内部有 key
    // 比较，未变时只是一次字符串比较。
    SideEffect { DeviceAppRepository.onEnter(pageDeviceKey) }

    // 设备能力三档
    val renderEffectSupported = isRenderEffectSupported()
    val runtimeShaderSupported = isRuntimeShaderSupported()
    val floatingForm = floatingBarSetting && runtimeShaderSupported
    val glassActive = floatingForm && blurEnabled && glassSwitch
    val gaussianActive = floatingForm && blurEnabled && !glassSwitch
    val blurBackdrop = rememberBlurBackdrop(blurEnabled)
    val surfaceColor = colorScheme.surface
    val glassBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    // 顶栏圆形遮罩材质：跟随**模糊模式**——
    // 渐进 + 液态玻璃 → 液态玻璃；高斯 → 恒为高斯采样（液态玻璃开关不参与）
    val topBarGlass = blurEnabled && blurMode == AppSettings.BlurMode.PROGRESSIVE && glassSwitch
    val topBarBackdrop = if (blurEnabled) blurBackdrop else null
    // 顶栏遮罩淡入：仅渐进模糊模式；滚动进度在组合外监听
    val maskEnabled = blurEnabled && blurMode == AppSettings.BlurMode.PROGRESSIVE && renderEffectSupported
    val maskAlpha = remember { Animatable(0f) }
    val topBarHighlight = rememberGlassHighlight()
    val density = LocalDensity.current

    // 分组页
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { AppManagerGroupCount })
    val userListState = rememberLazyListState()
    val systemListState = rememberLazyListState()
    val currentListState = if (pagerState.currentPage == 0) userListState else systemListState
    // 手指落下列表的次数：索引条据此把松手后的气泡立刻收起来（两页共用一个计数）
    val listTouchTick = remember { mutableIntStateOf(0) }

    // 同一组合内的两个 SideEffect（设备 key 变化复位列表位置）也用上面同一次采样，避免每次重组调用 2×。
    var lastDeviceKey by remember { mutableStateOf<String?>(null) }
    SideEffect {
        if (lastDeviceKey != pageDeviceKey) {
            lastDeviceKey = pageDeviceKey
            scope.launch {
                userListState.scrollToItem(0)
                systemListState.scrollToItem(0)
            }
        }
    }

    LaunchedEffect(maskEnabled, scrollBehavior, currentListState) {
        if (!maskEnabled) {
            maskAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        snapshotFlow {
            // 顶栏背景与按钮遮罩的显隐要合列表自身位置：contentOffset 只反映嵌套滚动
            // 产生的 delta，滑块整页跳方块（FastScroller.scrollTo）或退出/再进被 父
            // 递的同一 ScrollBehavior 实例重入时不走 delta，会把遮罩卡在旧值。故当
            // 列表可见的第一项仍停在第 0 个且偏移为 0（即在列表顶端）时直接判定 0，
            // 否则按 contentOffset 的渐变比例换算。
            val atTop = currentListState.firstVisibleItemIndex == 0 &&
                currentListState.firstVisibleItemScrollOffset == 0
            if (atTop) {
                0f
            } else {
                val offset = scrollBehavior.state.contentOffset
                with(density) { (-offset / 48.dp.toPx()).coerceIn(0f, 1f) }
            }
        }.collectLatest { target ->
            val duration = AppManagerMotion.MASK_FADE_MS.scaledBy(animationsDisabled)
            maskAlpha.animateTo(target, tween(duration, easing = LinearEasing))
        }
    }

    // 分页切换任务：胶囊 / 点击触发时取消上一次，避免两次 animateScrollToPage 叠加
    // 把 pagerState.currentPage 和底部选中态带偏
    var pagerNavigationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val onSelectPage: (Int) -> Unit = { index ->
        if (index != pagerState.currentPage) {
            pagerNavigationJob?.cancel()
            pagerNavigationJob = scope.launch {
                pagerState.animateScrollToPage(
                    page = index,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = AppManagerMotion.PAGE_DAMPING_RATIO,
                        stiffness = AppManagerMotion.PAGE_STIFFNESS,
                    ),
                )
            }
        }
    }

    // 批量选择（旋屏不保留）
    var batchMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    // batchMode = 多选态（长按进入、Close / 返回退出），与选中数解耦：
    // 全部取消勾选只清空选中集，不退出多选态——标题转「请选择项目」、底栏图标置灰，
    // 用户不用重新长按就能继续勾
    val batchHasSelection = selected.isNotEmpty()
    val latestApps by rememberUpdatedState(uiState.apps)

    // 列表加载完成后收口：陈旧包名不该留在选中集里，否则它被重新装上再刷新会带着勾回来。
    // 只掉列表里已经没有的项——还在的勾选原样保留，刷新不清勾选是既有行为，别在这里改掉。
    // 空列表（加载中 / 未连接）不动手，免得把用户刚勾的项一次抹掉。
    val loadedPackages = latestApps.mapTo(mutableSetOf()) { it.packageName }
    LaunchedEffect(loadedPackages) {
        if (loadedPackages.isEmpty()) return@LaunchedEffect
        selected.filterNot { it in loadedPackages }.forEach { selected.remove(it) }
    }
    /**
     * 选中集在当前列表里的对应条目：组合期的判断与点击回调都读它。
     *
     * 两处都取当前帧：回调执行在组合之后，组合期算出的普通列表会被闭包停在那一帧，只有
     * State 读到的才是同一帧。不做 remember——selected 作为 key 是引用比较，增删选中项
     * 不会让 key 变化，缓存会停在首帧的空集。
     */
    fun currentSelection(): List<DeviceApp> = latestApps.filter { it.packageName in selected }

    /**
     * 选中集里该动作真正作用到的条目。
     *
     * 启用只作用于已停用的、停用只作用于已启用的；其余动作（导出 / 卸载 / 强制停止 /
     * 清除数据）作用于全部选中项。混合选中时命令、提示计数与行尾在途圈都按这个集合走，
     * 已经是目标态的条目不会被重复操作。
     */
    fun batchTargets(action: AppAction): List<DeviceApp> =
        currentSelection().filter { app ->
            when (action) {
                AppAction.ENABLE -> !app.isEnabled
                AppAction.DISABLE -> app.isEnabled
                else -> true
            }
        }

    // 搜索浮层（状态机由 SearchOverlay 内部持有）
    var searchOpen by remember { mutableStateOf(false) }
    // 揭示圆心：取手指落点 pressInRoot，由顶栏按钮 / 悬浮圆钮的按下上报
    var searchRevealCenter by remember { mutableStateOf(Offset.Zero) }

    // 弹层族（显示开关与内容引用分离）
    var actionSheetApp by remember { mutableStateOf<DeviceApp?>(null) }
    var actionSheetVisible by remember { mutableStateOf(false) }
    var pendingSheetAction by remember { mutableStateOf<AppAction?>(null) }
    // 面板按包名回读最新行：点开面板时那次状态校准是异步的，读回来要能刷到面板上——
    // 「打开」「强制停止」是否置灰看的正是这一行的启用态与运行态。行已不在列表时退回快照。
    val actionSheetLiveApp = actionSheetApp?.let { snapshot ->
        uiState.apps.firstOrNull { it.packageName == snapshot.packageName } ?: snapshot
    }
    var installSheetOpen by remember { mutableStateOf(false) }
    var localPickerOpen by remember { mutableStateOf(false) }
    var displayOptionsOpen by remember { mutableStateOf(false) }
    // 进多选态时收掉显示选项浮层：它锚在已退场的 Tune 钮上
    LaunchedEffect(batchMode) { if (batchMode) displayOptionsOpen = false }
    var confirmRequest by remember { mutableStateOf<ConfirmRequest?>(null) }
    var confirmVisible by remember { mutableStateOf(false) }
    var pendingExecute by remember { mutableStateOf<(() -> Unit)?>(null) }
    var exportPrompt by remember { mutableStateOf<ExportPrompt?>(null) }
    // 问形态、等保存框这两段时间里，对应的行按「进行中」处理：不能再触发一次，读屏也读得到禁用。
    // 批量栏与搜索层同样按忙碌处理：这个窗口里再发起一次导出会叠出第二个保存框。
    val actionUiState = exportPrompt?.let {
        uiState.copy(
            pendingPackages = uiState.pendingPackages + it.app.packageName,
        )
    } ?: uiState

    // 搜索入口落点（开搜瞬间锁定）：顶栏按钮与悬浮圆钮各自上报
    val onSearchAnchor: (Offset) -> Unit = { pressInRoot -> searchRevealCenter = pressInRoot }

    /** 落盘目标打不开时的提示（用户取消保存框不算失败，静默返回）。 */
    fun exportTargetUnreadableText(): String =
        AppRuntime.stringResource(R.string.app_manager_export_failed, "cannot open target")

    /** 导出产物的查看意图；条目属于本应用，交给对方之前先把读权限让出来。 */
    fun exportViewIntent(uri: android.net.Uri, mimeType: String): android.content.Intent =
        android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            // clipData 让 chooser 把读权限一并转给用户选中的应用
            clipData = android.content.ClipData.newRawUri(null, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** 按 [kind] 的 MIME 向系统拉起「打开方式」，由用户挑能打开它的应用；`.apks` 起不来时退回通配 MIME。 */
    fun openExportTarget(uri: android.net.Uri, kind: DeviceAppRepository.ExportKind): Boolean {
        fun open(mimeType: String): Boolean = runCatching {
            val intent = exportViewIntent(uri, mimeType)
            // Resolver 总能解析 ACTION_CHOOSER：先看内层 intent 的候选，才判得出没有应用能接
            val candidates = context.packageManager.queryIntentActivities(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            )
            if (candidates.isEmpty()) return@runCatching false
            // 系统可能已给这个 MIME 设了默认应用：套 chooser 才会把候选摆给用户
            val chooser = android.content.Intent.createChooser(intent, null)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
        // 分包安装器未必注册 `.apks` 这个 type：先按它起，起不来再退回 */*，让系统把全部候选摆出来
        val mimeTypes =
            if (kind == DeviceAppRepository.ExportKind.APK_SET) {
                listOf(kind.mimeType, "*/*")
            } else {
                listOf(kind.mimeType)
            }
        return mimeTypes.any { open(it) }
    }

    /**
     * 拉起导出产物所在的目录。
     *
     * 目录的接收方是文件页面：优先显式拉起系统文件页面（系统默认候选里常混着网盘与
     * 浏览器），起不来再套 chooser。失败文案只在两次启动都抛错时出现，chooser 的空
     * 态（设备上没有应用认领目录 MIME）不会被它覆盖。
     */
    fun openExportFolder(folderUri: android.net.Uri) {
        val intent = exportViewIntent(
            folderUri,
            android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
        )
        val opened = DocumentsUiPackages.any { packageName ->
            runCatching {
                context.startActivity(android.content.Intent(intent).setPackage(packageName))
                true
            }.getOrDefault(false)
        }
        if (opened) return
        val chooserOpened = runCatching {
            context.startActivity(android.content.Intent.createChooser(intent, null))
            true
        }.getOrDefault(false)
        if (!chooserOpened) {
            snackbar.show(AppRuntime.stringResource(R.string.app_manager_export_open_folder_failed))
        }
    }

    // 导出后的 APK：产物在系统 Downloads/Scrcpy（或保存框选定的目录）里，安装器读不到，
    // 由 [LocalApkInstall] 复制到缓存再授权。缺「安装未知应用」权限时先拉系统设置，
    // 回页（ON_RESUME）后自动续装一次，用户不必再点一次「安装」。
    var pendingInstallUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingInstallFolder by remember { mutableStateOf<android.net.Uri?>(null) }
    // 已暂存好的产物：从后台回到前台时直接拉起它，不再复制一遍整包。
    var pendingInstallContent by remember { mutableStateOf<android.net.Uri?>(null) }
    // 续装请求只在本进程内有效：进程重建后不替用户续一个陈旧请求，重新导出即可。
    // 复制期间再点一次「安装」会叠出第二次复制与第二个安装器，用这个短命标记挡住。
    var installInFlight by remember { mutableStateOf(false) }

    /** 清掉待续装的请求：成功、拒绝或失败之后都不再重试。 */
    fun clearPendingInstall() {
        pendingInstallUri = null
        pendingInstallFolder = null
        pendingInstallContent = null
    }

    /** 安装没能拉起来时的收尾：有导出目录就给「打开」兜底，别让产物彻底找不到。 */
    fun reportInstallFailure(messageRes: Int, folderUri: android.net.Uri?) {
        val message = AppRuntime.stringResource(messageRes)
        if (folderUri == null) {
            snackbar.show(message)
            return
        }
        snackbar.show(
            message = message,
            actionLabel = AppRuntime.stringResource(R.string.app_manager_snackbar_open),
            onAction = { openExportFolder(folderUri) },
        )
    }

    /** 暂存件已经在缓存里：回页时只拉起安装器。 */
    fun resumeStagedInstall(contentUri: android.net.Uri, folderUri: android.net.Uri?) {
        if (installInFlight) return
        installInFlight = true
        scope.launch {
            try {
                when (val outcome = LocalApkInstall.launch(context, contentUri)) {
                    LocalApkInstall.Outcome.Started -> clearPendingInstall()

                    is LocalApkInstall.Outcome.Failed -> {
                        android.util.Log.w(
                            "AppManagerScreen",
                            "resume exported apk install failed: ${outcome.reason}",
                        )
                        clearPendingInstall()
                        reportInstallFailure(
                            R.string.app_manager_export_install_failed,
                            folderUri,
                        )
                    }

                    // 授权在授权离开后被收回：丢掉这次请求并说明原因，不再空转
                    LocalApkInstall.Outcome.NeedsPermission -> {
                        clearPendingInstall()
                        reportInstallFailure(
                            R.string.app_manager_export_install_denied,
                            folderUri,
                        )
                    }

                    // 调用前已确认在前台，这个结果不会出现
                    is LocalApkInstall.Outcome.Deferred -> Unit
                }
            } finally {
                installInFlight = false
            }
        }
    }

    fun installExportedApk(
        uri: android.net.Uri,
        fallbackFolder: android.net.Uri? = null,
        reopenSettings: Boolean = true,
    ) {
        if (installInFlight) {
            // 动作按钮被点掉后，反馈只剩一句「正在准备」：告诉用户上一次复制还没结束
            snackbar.show(AppRuntime.stringResource(R.string.app_manager_export_install_preparing))
            return
        }
        installInFlight = true
        if (reopenSettings) {
            snackbar.show(AppRuntime.stringResource(R.string.app_manager_export_install_preparing))
        }
        scope.launch {
            var resumeRetry = false
            try {
                val outcome = LocalApkInstall.install(
                    context = context,
                    source = uri,
                    isForeground = {
                        lifecycleOwner.lifecycle.currentState
                            .isAtLeast(Lifecycle.State.RESUMED)
                    },
                )
                when (outcome) {
                    LocalApkInstall.Outcome.Started -> clearPendingInstall()

                    is LocalApkInstall.Outcome.Deferred -> {
                        // 复制结束时页面已在后台：系统会丢弃这次启动，留到回前台直接拉起这份暂存件
                        pendingInstallUri = uri
                        pendingInstallFolder = fallbackFolder
                        pendingInstallContent = outcome.contentUri
                        resumeRetry = lifecycleOwner.lifecycle.currentState
                            .isAtLeast(Lifecycle.State.RESUMED)
                    }

                    LocalApkInstall.Outcome.NeedsPermission -> {
                        // 从设置页回来仍没授权：丢掉这次请求，别再把这个请求顶回设置页
                        if (!reopenSettings) {
                            clearPendingInstall()
                            reportInstallFailure(
                                R.string.app_manager_export_install_denied,
                                fallbackFolder,
                            )
                            return@launch
                        }
                        pendingInstallUri = uri
                        pendingInstallFolder = fallbackFolder
                        pendingInstallContent = null
                        val opened = runCatching {
                            context.startActivity(LocalApkInstall.permissionSettingsIntent(context))
                        }.isSuccess
                        if (!opened) {
                            clearPendingInstall()
                            reportInstallFailure(
                                R.string.app_manager_export_install_failed,
                                fallbackFolder,
                            )
                        }
                    }

                    is LocalApkInstall.Outcome.Failed -> {
                        android.util.Log.w(
                            "AppManagerScreen",
                            "install exported apk failed: ${outcome.reason}",
                        )
                        clearPendingInstall()
                        reportInstallFailure(
                            R.string.app_manager_export_install_failed,
                            fallbackFolder,
                        )
                    }
                }
            } finally {
                installInFlight = false
            }
            // 复制还没结束用户就回来了：ON_RESUME 会被单飞标记挡住，这里补一次拉起
            if (resumeRetry) {
                pendingInstallContent?.let { resumeStagedInstall(it, fallbackFolder) }
            }
        }
    }

    /** 回页后继续待续装的请求：有暂存件就只拉起安装器，只有源 uri（授权那条路）才重新复制。 */
    fun continuePendingInstall() {
        val content = pendingInstallContent
        if (content != null) {
            resumeStagedInstall(content, pendingInstallFolder)
            return
        }
        val source = pendingInstallUri ?: return
        installExportedApk(source, pendingInstallFolder, reopenSettings = false)
    }

    // 从「安装未知应用」设置页回来：权限已给就接着装；没给则作废这次请求，
    // 免得多小时后用户从别处给了权限，一回到本页就被一个陈旧的 uri 顶进安装器
    val continuePendingInstallLatest by rememberUpdatedState<() -> Unit>(
        { continuePendingInstall() },
    )
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (pendingInstallUri == null && pendingInstallContent == null) {
                return@LifecycleEventObserver
            }
            val folder = pendingInstallFolder
            if (LocalApkInstall.needsPermission(context)) {
                clearPendingInstall()
                reportInstallFailure(R.string.app_manager_export_install_denied, folder)
            } else {
                continuePendingInstallLatest()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 运行态定时刷新：只有停在本页且处于前台时才每 30 秒重拉一次 `ps -A`。
    // 被控设备上的应用随时会被用户自己打开 / 退出，一次快照放到几十分钟后必然过时，
    // 角标和「强制停止」的置灰就会说谎。只拉进程表、不拉应用清单，代价很小。
    val refreshRunningTick by rememberUpdatedState<() -> Unit>({
        if (!uiState.notConnected) DeviceAppRepository.refreshRunningState()
    })
    LaunchedEffect(lifecycleOwner) {
        while (true) {
            delay(RUNNING_REFRESH_INTERVAL_MS)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                refreshRunningTick()
            }
        }
    }

    /**
     * 导出成功后的提示：产物能装能开时给它配一个动作按钮。
     *
     * 单个 APK 直接交给系统安装器（缺「安装未知应用」权限时先申请）；`.apks`（带 split
     * 分包的应用）与 zip（批量导出）交给系统弹「打开方式」，让用户挑安装器。
     */
    fun showExportDone(
        message: String,
        uri: android.net.Uri?,
        kind: DeviceAppRepository.ExportKind,
        folderUri: android.net.Uri? = null,
    ) {
        val apk = uri?.takeIf { kind == DeviceAppRepository.ExportKind.APK }
        if (apk != null) {
            snackbar.show(
                message = message,
                actionLabel = AppRuntime.stringResource(R.string.app_manager_snackbar_install),
                // 安装失败（复制不了 / 没有安装器）时还能退回打开产物所在目录
                onAction = { installExportedApk(apk, fallbackFolder = folderUri) },
            )
            return
        }
        val actionLabel = AppRuntime.stringResource(R.string.app_manager_snackbar_open)
        val target = uri?.takeIf { kind != DeviceAppRepository.ExportKind.APK }
        if (target == null) {
            snackbar.show(message)
            return
        }
        snackbar.show(
            message = message,
            actionLabel = actionLabel,
            onAction = {
                if (!openExportTarget(target, kind)) {
                    snackbar.show(AppRuntime.stringResource(R.string.app_manager_export_open_failed))
                }
            },
        )
    }

    /** 单包导出的收尾：成功给动作按钮，失败只报原因。 */
    fun reportSingleExport(app: DeviceApp, message: String, result: DeviceAppRepository.ExportResult) {
        when (result) {
            is DeviceAppRepository.ExportResult.Success ->
                showExportDone(message, result.uri, result.kind, result.folderUri)

            is DeviceAppRepository.ExportResult.Failure ->
                snackbar.show(singleFailureText(AppAction.EXPORT_APK, app, result.error))
        }
    }

    /** 批量导出的收尾：产物还在时给「打开」按钮，撤回了就没得打开。 */
    fun showZipExportDone(outcome: DeviceAppRepository.ExportOutcome, appCount: Int, toDownloads: Boolean) {
        showExportDone(
            message = zipExportOutcomeText(outcome, appCount, toDownloads),
            uri = if (outcome.archived) outcome.uri else null,
            kind = DeviceAppRepository.ExportKind.ARCHIVE,
        )
    }

    // 文件选择器：安装 APK
    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<android.net.Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val names = uris.map { uri ->
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull() ?: "install.apk"
        }
        DeviceAppRepository.installDocuments(uris, names) { outcome ->
            snackbar.show(installOutcomeText(outcome, outcome.label))
        }
    }

    // 落盘位置：Android 10 及以上由数据层直接写系统 Download/Scrcpy 目录（条目是它自己插的，
    // 中断能删掉）；更早的版本没有分区插入，退回系统保存框由用户选。
    /** 保存框选定的 uri 对应的流；打不开时返回 null。 */
    fun openExportStream(uri: android.net.Uri?): java.io.OutputStream? =
        uri?.let { runCatching { context.contentResolver.openOutputStream(it) }.getOrNull() }

    /** 保存框返回时条目已经建好；没能落盘就删掉它，别在用户选的目录里留个空壳。 */
    fun discardCreatedDocument(uri: android.net.Uri?) {
        uri ?: return
        runCatching {
            android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
    }

    /** 用户选好位置后落盘；取消保存框没有产物，静默收尾。 */
    fun writePendingExport(uri: android.net.Uri?) {
        val prompt = exportPrompt as? ExportPrompt.AwaitingLocation
        exportPrompt = null
        if (uri == null) return
        if (prompt == null) {
            // 页面重建后待落盘的对象已经没了：系统刚建的那个条目在这里收掉，别留一个空壳
            discardCreatedDocument(uri)
            snackbar.show(AppRuntime.stringResource(R.string.app_manager_export_lost))
            return
        }
        val stream = openExportStream(uri)
        if (stream == null) {
            discardCreatedDocument(uri)
            snackbar.show(exportTargetUnreadableText())
            return
        }
        // 保存框那条路的条目是调用方自己开的，uri 一并传给动作按钮
        DeviceAppRepository.exportApk(prompt.app, stream, uri, expectedKind = prompt.kind) { result ->
            reportSingleExport(
                prompt.app,
                AppRuntime.stringResource(R.string.app_manager_exported, prompt.app.displayLabel),
                result,
            )
            batchMode = false
            selected.clear()
        }
    }

    // 保存框的 MIME 决定系统是否给名字补后缀（`application/zip` 配 `.apks` 会存成 `.apks.zip`），
    // 所以每个形态各有一个 launcher；建议名由 exportFileName 按同一形态算出。
    val exportCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DeviceAppRepository.ExportKind.APK.mimeType),
    ) { uri: android.net.Uri? -> writePendingExport(uri) }

    /** 分包应用的容器：MIME 用 `.apks` 自己的，系统认不出扩展名，名字原样保留。 */
    val apksExportCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DeviceAppRepository.ExportKind.APK_SET.mimeType),
    ) { uri: android.net.Uri? -> writePendingExport(uri) }

    // 批量导出（Android 9 及以下的落盘位置）。忙碌状态（exportPrompt）只覆盖单个应用：
    // 批量保存框开着时行仍可点，批量栏也能再点一次，会叠出第二个窗口；清单丢了由回调删条目。
    var pendingZipExportApps by remember { mutableStateOf<List<DeviceApp>>(emptyList()) }
    val zipExportCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DeviceAppRepository.ExportKind.ARCHIVE.mimeType),
    ) { uri: android.net.Uri? ->
        val apps = pendingZipExportApps
        pendingZipExportApps = emptyList()
        // 取消保存框没有产物，静默
        if (uri == null) return@rememberLauncherForActivityResult
        if (apps.isEmpty()) {
            // 页面重建后待导出的清单已经没了：条目在用户选的目录里，收掉它并说明没做成
            discardCreatedDocument(uri)
            snackbar.show(AppRuntime.stringResource(R.string.app_manager_export_lost))
            return@rememberLauncherForActivityResult
        }
        val stream = openExportStream(uri)
        if (stream == null) {
            discardCreatedDocument(uri)
            snackbar.show(exportTargetUnreadableText())
            return@rememberLauncherForActivityResult
        }
        DeviceAppRepository.exportAppsAsZip(apps, stream, uri) { outcome ->
            showZipExportDone(outcome, apps.size, toDownloads = false)
            batchMode = false
            selected.clear()
        }
    }

    /** 按产物形态挑保存框：`.apks` 走容器的 launcher，单个包走安装包的。 */
    fun launchExportPicker(app: DeviceApp, kind: DeviceAppRepository.ExportKind) {
        val name = exportFileName(app.displayLabel, app.packageName, kind.extension)
        if (kind == DeviceAppRepository.ExportKind.APK_SET) {
            apksExportCreator.launch(name)
        } else {
            exportCreator.launch(name)
        }
    }

    /** 单包导出：能写 Download/Scrcpy 目录就直接写，否则先问设备要形态，再弹保存框。 */
    fun startApkExport(app: DeviceApp) {
        if (DeviceAppRepository.canPublishToDownloads()) {
            DeviceAppRepository.exportApk(app) { result ->
                reportSingleExport(
                    app,
                    AppRuntime.stringResource(
                        R.string.app_manager_exported_to_downloads,
                        app.displayLabel,
                    ),
                    result,
                )
                batchMode = false
                selected.clear()
            }
        } else {
            // 扩展名与 MIME 在弹框时就定死了，所以先问设备要形态。这一步要等一次 `pm path`，
            // 期间再点只把当前阶段说出来，不叠第二个保存框。
            when (exportPrompt) {
                null -> {
                    // 一次 `pm path` 通常几十毫秒；先把话说出来，别让首击像没反应
                    exportPrompt = ExportPrompt.Resolving(app)
                    snackbar.show(AppRuntime.stringResource(R.string.app_manager_export_kind_querying))
                }

                is ExportPrompt.Resolving -> snackbar.show(
                    AppRuntime.stringResource(R.string.app_manager_export_kind_querying),
                )

                is ExportPrompt.AwaitingLocation -> snackbar.show(
                    AppRuntime.stringResource(R.string.app_manager_export_prompt_busy),
                )
            }
        }
    }

    // 形态查询与弹框都在组合里推进：页面离场时这两个 effect 一并取消，不会在 launcher
    // 注销之后再启动它（那时会抛 IllegalStateException 崩掉进程）。
    LaunchedEffect(exportPrompt) {
        val request = exportPrompt as? ExportPrompt.Resolving ?: return@LaunchedEffect
        val result = DeviceAppRepository.resolveExportKind(request.app.packageName)
        if (exportPrompt !== request) return@LaunchedEffect
        exportPrompt = when (result) {
            is DeviceAppRepository.ExportKindResult.Ready ->
                ExportPrompt.AwaitingLocation(request.app, result.kind)

            is DeviceAppRepository.ExportKindResult.TimedOut -> {
                snackbar.show(
                    AppRuntime.stringResource(
                        R.string.app_manager_export_failed,
                        AppRuntime.stringResource(R.string.app_manager_export_kind_timeout),
                    ),
                )
                null
            }

            is DeviceAppRepository.ExportKindResult.Unavailable -> {
                snackbar.show(singleFailureText(AppAction.EXPORT_APK, request.app, result.reason))
                null
            }
        }
    }

    LaunchedEffect(exportPrompt) {
        val ready = exportPrompt as? ExportPrompt.AwaitingLocation ?: return@LaunchedEffect
        launchExportPicker(ready.app, ready.kind)
    }

    /** 批量 ZIP 导出：同上，落盘目标由系统版本决定。 */
    fun startZipExport(apps: List<DeviceApp>) {
        if (DeviceAppRepository.canPublishToDownloads()) {
            DeviceAppRepository.exportAppsAsZip(apps) { outcome ->
                showZipExportDone(outcome, apps.size, toDownloads = true)
                // 与单包导出、保存框那条批量路一致：四条导出路径都在导出跑完后退出多选态
                batchMode = false
                selected.clear()
            }
        } else {
            pendingZipExportApps = apps
            zipExportCreator.launch("scrcpy-apps-${System.currentTimeMillis()}.zip")
        }
    }

    // 返回键优先级：搜索开着先关搜索；否则多选态先退多选；其余交给导航层。
    // 绑 batchMode 而不是选中数：空选时也还在多选态，返回键应该继续退出多选而不是退出页面
    BackHandler(enabled = searchOpen || batchMode) {
        when {
            searchOpen -> searchOpen = false
            batchMode -> {
                batchMode = false
                selected.clear()
            }
        }
    }

    // 本页自己的 snackbar 宿主：消息走 AppRuntime 的宿主栈顶，二级页里也能即时显示
    val pageSnackHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(pageSnackHostState) {
        val unregister = AppRuntime.registerSnackbarHostState(pageSnackHostState)
        onDispose(unregister)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scaffold 上不挂 scrollBehavior 的嵌套滚动连接：OverlayBottomSheet 由本
        // Scaffold 托管，它的列表滚动会顺着这条连接冒泡到 ScrollBehavior、把本页顶栏
        // 标题顶起来（放大态下尤其明显）。顶栏折叠由列表自己那条连接驱动——
        // AppManagerListPage 的 LazyColumn 上已挂同一条（见其 scrollBehavior 参数）
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    pageSnackHostState,
                    // 消息条按底部槽位的高度上移，落到底栏上方而不是压在底栏上。
                    // 底栏自绘在内容层内（不进 Scaffold 的 bottomBar 槽位，那样会
                    // 改变它与搜索浮层的前后关系），所以避让量由这里显式给出。
                    modifier = Modifier.padding(
                        bottom = bottomSlotClearance(floatingForm),
                    ),
                )
            },
        ) { _ ->
            val navBarsBottom = WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()
            // 顶栏在采样层外，内容区元素与列表 contentPadding 都要避开它（onGloballyPositioned 实测）
            var topBarHeightPx by remember { mutableStateOf(0) }
            val topBarHeight = with(density) { topBarHeightPx.toDp() }

            Box(modifier = Modifier.fillMaxSize()) {
                // 内容采样层：全屏，只登记内容。顶栏是 backdrop 的采样方，放在层外
                // （下面的 Column）——顶栏按钮在层内采样自己所在层会形成 RenderNode 循环，
                // RenderThread prepareTreeImpl 无限递归 → 栈溢出 SIGSEGV。
                // 列表经 contentPadding 从顶栏下方起排，上滑时内容滚到顶栏之后，
                // 顶栏 36dp 圆形遮罩才采得到列表内容。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
                        ),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (floatingForm && blurEnabled) {
                                    Modifier.layerBackdrop(glassBackdrop)
                                } else {
                                    Modifier
                                },
                            ),
                        beyondViewportPageCount = 1,
                    ) { page ->
                        AppManagerListPage(
                            apps = uiState.apps,
                            uiState = actionUiState,
                            isSystemGroup = page == 1,
                            batchMode = batchMode,
                            selectedPackages = selected.toSet(),
                            showVersion = !hideVersion,
                            showApkSize = !hideApkSize,
                            animationsDisabled = animationsDisabled,
                            scrollBehavior = scrollBehavior,
                            listState = if (page == 0) userListState else systemListState,
                            iconProvider = { packageName -> DeviceAppRepository.icon(packageName) },
                            bottomInnerPadding = if (floatingForm) 92.dp else 64.dp,
                            topInnerPadding = topBarHeight,
                            onRefresh = { DeviceAppRepository.refresh() },
                            onListTouch = { listTouchTick.intValue++ },
                            onAppClick = { app ->
                                if (batchMode) {
                                    // 取消勾选不退出多选态：空选后标题转「请选择项目」、
                                    // 底栏图标置灰，用户不用重新长按就能继续勾。
                                    // 退出只认 Close / 返回键（见 BackHandler）
                                    if (!selected.remove(app.packageName)) {
                                        selected.add(app.packageName)
                                        // 勾上时校准一次：勾选之后马上要按状态挑动作
                                        // （启用/停用互斥、强制停止要看是否在跑）
                                        DeviceAppRepository.refreshAppState(app.packageName)
                                    }
                                } else {
                                    // 菜单先弹出来，再校准这一个应用的状态：用户可能刚在被控
                                    // 设备上打开、退出或停用过它，上一次快照未必还成立。校准是
                                    // 异步的，读回来会经 actionSheetLiveApp 刷到已打开的面板上；
                                    // 读不到也照样弹菜单——展开菜单的优先级高于状态准确。
                                    actionSheetApp = app
                                    actionSheetVisible = true
                                    DeviceAppRepository.refreshAppState(app.packageName)
                                }
                            },
                            onAppLongClick = { app ->
                                haptic.longPress()
                                batchMode = true
                                if (!selected.contains(app.packageName)) {
                                    selected.add(app.packageName)
                                    DeviceAppRepository.refreshAppState(app.packageName)
                                }
                            },
                            onBackToDevices = onBackToDevices,
                        )
                    }
                }

                // 顶栏（采样方）：必须在采样层之外，渐进 / 高斯模糊与圆形遮罩才有内容可采
                Column(modifier = Modifier.onGloballyPositioned { topBarHeightPx = it.size.height }) {
                    // 列表页顶栏用「允许渐进」变体：随滚动从透明淡入模糊
                    BlurredBar(backdrop = blurBackdrop) {
                        // 刷新中：标题旁转一个刷新图标，让「正在刷新」从普通状态里跳出来
                        val refreshRotation = remember { Animatable(0f) }
                        LaunchedEffect(uiState.isRefreshing) {
                            if (uiState.isRefreshing) {
                                while (true) {
                                    refreshRotation.animateTo(
                                        targetValue = refreshRotation.value + 360f,
                                        animationSpec = tween(900, easing = LinearEasing),
                                    )
                                }
                            }
                        }
                        TopAppBar(
                            title = when {
                                // 刷新中直接占用标题位：进度提示不经由会吞滑动的下拉刷新指示器
                                uiState.isRefreshing -> stringResource(R.string.app_manager_refreshing)
                                // 多选态三态：有选中报数 / 空选提示选择 / 平时显示页标题
                                batchMode && batchHasSelection ->
                                    stringResource(R.string.app_manager_selected, selected.size)

                                batchMode -> stringResource(R.string.app_manager_select_prompt)
                                else -> stringResource(R.string.app_manager_title)
                            },
                            color = if (blurBackdrop != null) Color.Transparent else surfaceColor,
                            navigationIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 返回钮与右侧三钮同一组件：玻璃 / 高斯遮罩 + 常驻按压反馈
                                    // 多选态用 Close 而不是 Back：此时它的语义是「退出多选」而非「返回上级」
                                    MaskedTopBarAction(
                                        icon = if (batchMode) MiuixIcons.Basic.Close else MiuixIcons.Back,
                                        contentDescription = stringResource(
                                            if (batchMode) {
                                                R.string.cd_close
                                            } else {
                                                R.string.cd_back
                                            },
                                        ),
                                        onClick = {
                                            haptic.contextClick()
                                            if (batchMode) {
                                                batchMode = false
                                                selected.clear()
                                            } else {
                                                navigator.pop()
                                            }
                                        },
                                        maskAlpha = { maskAlpha.value },
                                        glassActive = topBarGlass,
                                        backdrop = topBarBackdrop,
                                        highlight = topBarHighlight,
                                    )
                                    // 刷新中：返回钮右侧一个旋转的刷新图标。
                                    // 放导航侧而不是 actions 行首——actions 行会挤占标题宽度，
                                    // 把「正在刷新」挤成 3 个字加省略号；导航侧只有返回钮，空间充裕。
                                    // 间距给 12dp：既和返回钮分开，也朝标题方向靠近
                                    // （miuix TopAppBar 标题位只收 String，图标无法真正贴住文字）
                                    if (uiState.isRefreshing) {
                                        Spacer(Modifier.width(12.dp))
                                        Icon(
                                            imageVector = MiuixIcons.Refresh,
                                            contentDescription = stringResource(R.string.app_manager_refreshing),
                                            tint = colorScheme.onSurface,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .graphicsLayer { rotationZ = refreshRotation.value },
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (batchMode) {
                                    // 多选态：搜索 / 显示选项 / 安装三个动作整体退场，只留「全选 / 取消全选」。
                                    // 之前是 enabled = false 置灰——按钮仍占位，看起来像坏掉的控件。
                                    // 作用域取当前分页组（用户 / 系统）的可见项，不会波及另一页。
                                    val groupApps = uiState.apps.filter { it.isSystem == (pagerState.currentPage == 1) }
                                    val allGroupSelected =
                                        groupApps.isNotEmpty() && groupApps.all { it.packageName in selected }
                                    MaskedTopBarAction(
                                        icon = MiuixIcons.SelectAll,
                                        contentDescription = stringResource(
                                            if (allGroupSelected) R.string.cd_clear_selection else R.string.cd_select_all,
                                        ),
                                        onClick = {
                                            haptic.contextClick()
                                            if (allGroupSelected) {
                                                groupApps.forEach {
                                                    if (it.packageName in selected) selected.remove(it.packageName)
                                                }
                                            } else {
                                                groupApps.forEach {
                                                    if (it.packageName !in selected) selected.add(it.packageName)
                                                }
                                            }
                                        },
                                        enabled = actionsEnabled && groupApps.isNotEmpty(),
                                        maskAlpha = { maskAlpha.value },
                                        glassActive = topBarGlass,
                                        backdrop = topBarBackdrop,
                                        highlight = topBarHighlight,
                                    )
                                } else {
                                    if (!floatingForm) {
                                        MaskedTopBarAction(
                                            icon = MiuixIcons.Basic.Search,
                                            contentDescription = stringResource(R.string.app_manager_search),
                                            onClick = {
                                                haptic.contextClick()
                                                searchOpen = true
                                            },
                                            enabled = actionsEnabled,
                                            maskAlpha = { maskAlpha.value },
                                            glassActive = topBarGlass,
                                            backdrop = topBarBackdrop,
                                            highlight = topBarHighlight,
                                            // 揭示圆心 = 手指落点（pressInRoot）
                                            anchorModifier = rememberPressInRootModifier(onSearchAnchor),
                                        )
                                    }
                                    // 显示选项菜单与 tune 钮包在同一个 Box 里：弹出层以该 Box
                                    // 为锚点。
                                    Box {
                                        MaskedTopBarAction(
                                            icon = MiuixIcons.Tune,
                                            contentDescription = stringResource(R.string.app_manager_display_options),
                                            onClick = {
                                                haptic.contextClick()
                                                displayOptionsOpen = true
                                            },
                                            enabled = actionsEnabled,
                                            maskAlpha = { maskAlpha.value },
                                            glassActive = topBarGlass,
                                            backdrop = topBarBackdrop,
                                            highlight = topBarHighlight,
                                        )
                                        DisplayOptionsMenu(
                                            visible = displayOptionsOpen,
                                            hideVersion = hideVersion,
                                            hideApkSize = hideApkSize,
                                            onToggleHideVersion = viewModel::toggleHideVersion,
                                            onToggleHideApkSize = viewModel::toggleHideApkSize,
                                            onDismissRequest = { displayOptionsOpen = false },
                                        )
                                    }
                                    MaskedTopBarAction(
                                        icon = MiuixIcons.Share,
                                        contentDescription = stringResource(R.string.app_manager_install_apk),
                                        onClick = {
                                            haptic.contextClick()
                                            installSheetOpen = true
                                        },
                                        maskAlpha = { maskAlpha.value },
                                        glassActive = topBarGlass,
                                        backdrop = topBarBackdrop,
                                        enabled = !uiState.busy && actionsEnabled,
                                        highlight = topBarHighlight,
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    }
                }
            }

            // 内容区元素（顶栏之下）：快速滚动滑块 / 底部渐隐 / 底部槽位
            Box(modifier = Modifier.fillMaxSize().padding(top = topBarHeight)) {

                // MIUI 同款快速滚动滑块（右侧滑块 + 左侧首字母气泡）。
                // 活动范围由本处 padding 给出（顶栏之下 8dp、底栏之上）；组件自身不叠加安全区，以避免双重偏移。
                // 映射表随节表走：节下标 ↔ item 下标由 AppOrdering 的约定精确换算，
                // 不从布局反推（见 FastScroller 位置模型注释）
                val currentGroupApps = uiState.apps.filter { it.isSystem == (pagerState.currentPage == 1) }
                val sections = remember(currentGroupApps) { AppOrdering.groupBySection(currentGroupApps) }
                // 滑块底距 = 底栏高度 + 8dp（与顶栏距离一致，不贴底）
                val scrollerBottomInset = (if (floatingForm) 92.dp else 64.dp) + 8.dp
                FastScroller(
                    listState = currentListState,
                    sections = sections,
                    animationsDisabled = animationsDisabled,
                    // 底栏那一带盖住的项还没真正露出来，不该拿它更新气泡字母
                    bottomObscured = scrollerBottomInset,
                    listTouchTick = listTouchTick.intValue,
                    // 字母头还没被实测到时的兜底：按字母头自身的排版量，而不是拿行高顶替
                    headerFallbackHeight = appSectionHeaderHeight(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp, bottom = scrollerBottomInset),
                    contentDescription = stringResource(R.string.app_manager_fast_scroller),
                )

                // 底部 96dp 软滚动渐隐（悬浮形态常驻）：贴在视口底部，
                // 被悬浮栏压在上层；不进无障碍遍历
                if (floatingForm) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(AppManagerMotion.BOTTOM_SCROLL_SCrim_DP.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, surfaceColor),
                                ),
                            ),
                    )
                }

                // 底部槽位：分组栏 / 批量栏（同一时刻只有一个在场）。
                // 悬浮形态距屏幕底 = 4dp + 系统手势区高度
                AppManagerBottomSlot(
                    batchActive = batchMode,
                    batchMode = batchMode,
                    // 空选时底栏仍显示但整栏置灰：批量动作需要一个选中集才有意义
                    batchHasSelection = batchHasSelection,
                    // 选中集的启用现状：全启用时「启用」无事可做，全停用时「停用」无事可做
                    batchCanEnable = currentSelection().any { !it.isEnabled },
                    batchCanDisable = currentSelection().any { it.isEnabled },
                    floatingForm = floatingForm,
                    glassActive = glassActive,
                    gaussianActive = gaussianActive,
                    // 悬浮形态采样分页层（glassBackdrop 由 pager 登记）；标准形态采样
                    // 内容全屏层（blurBackdrop），两条路都是「采样方在登记层之外」
                    backdrop = if (floatingForm) glassBackdrop else blurBackdrop,
                    currentPage = pagerState.currentPage,
                    batchBusy = actionUiState.busy,
                    animationsDisabled = animationsDisabled,
                    onSelectPage = onSelectPage,
                    onSearchClick = {
                        haptic.contextClick()
                        searchOpen = true
                    },
                    onSearchAnchor = onSearchAnchor,
                    onBatchAction = { action ->
                        haptic.contextClick()
                        // 计数按真正会被作用到的条目：混合选中时启用只算已停用的那些
                        confirmRequest = ConfirmRequest(
                            action = action,
                            app = null,
                            batchCount = batchTargets(action).size,
                            isInstall = false,
                            splitCount = batchTargets(action).count { it.isSplit },
                        )
                        confirmVisible = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(
                            if (floatingForm) {
                                Modifier.padding(bottom = navBarsBottom + 4.dp)
                            } else {
                                Modifier
                            },
                        ),
                )
            }

            SearchOverlay(
                visible = searchOpen,
                revealCenterRoot = searchRevealCenter,
                apps = uiState.apps,
                showVersion = !hideVersion,
                showApkSize = !hideApkSize,
                runningPackages = uiState.runningPackages,
                pendingPackages = actionUiState.pendingPackages,
                iconProvider = { packageName -> DeviceAppRepository.icon(packageName) },
                iconEpoch = uiState.iconEpoch,
                animationsDisabled = animationsDisabled,
                onDismissRequest = { searchOpen = false },
                onExitFinished = { },
                onAppClick = { app ->
                    // 先收起输入法再弹操作菜单
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    // 同列表行：先弹菜单，再校准这一个应用的状态（不阻塞展开）
                    actionSheetApp = app
                    actionSheetVisible = true
                    DeviceAppRepository.refreshAppState(app.packageName)
                },
                // 背景与圆形揭示铺满全屏；状态栏、输入法、导航条的间距由浮层内容自行内衬
                modifier = Modifier.fillMaxSize(),
            )

        // ---------------------------------------------------------------- 弹层族
        // 弹层必须在 Scaffold 内组合：overlay 组件默认 renderInRootScaffold=true，
        // 在 Scaffold 外部会落入 LocalDialogStates 的默认空列表而无法渲染

        // 应用操作面板：点动作 → 记下动作 → 收起面板 → 下面的 LaunchedEffect 随
        // visible 翻 false 立即执行下一步
        AppActionSheet(
            visible = actionSheetVisible,
            app = actionSheetLiveApp,
            onDismissRequest = {
                actionSheetVisible = false
            },
            onActionPicked = { action ->
                pendingSheetAction = action
                actionSheetVisible = false
            },
            // 运行态未知（null）时传 null：面板内部据此不置灰强制停止
            running = actionSheetLiveApp?.let { app ->
                uiState.runningPackages.runningOrNull(app.packageName)
            },
        )
        LaunchedEffect(actionSheetVisible, actionSheetApp) {
            if (!actionSheetVisible) {
                val action = pendingSheetAction
                val app = actionSheetLiveApp
                pendingSheetAction = null
                actionSheetApp = null
                if (action == null || app == null) return@LaunchedEffect
                when (action) {
                    AppAction.OPEN -> DeviceAppRepository.performAction(action, app) { result ->
                        snackbar.show(
                            when (result) {
                                is DeviceAppRepository.ActionResult.Success ->
                                    singleSuccessText(action, result.app)

                                is DeviceAppRepository.ActionResult.Failure ->
                                    singleFailureText(action, result.app, result.error)
                            },
                        )
                    }

                    // 导出：与批量导出走同一道确认，落盘位置由系统版本决定
                    AppAction.EXPORT_APK -> {
                        confirmRequest = ConfirmRequest(
                            action = AppAction.EXPORT_APK,
                            app = app,
                            batchCount = 1,
                            isInstall = false,
                            splitCount = if (app.isSplit) 1 else 0,
                        )
                        confirmVisible = true
                    }

                else -> {
                    confirmRequest = ConfirmRequest(action, app, batchCount = 1, isInstall = false)
                    confirmVisible = true
                }
            }
        }
    }

    // 安装来源面板
    InstallSourceSheet(
        visible = installSheetOpen,
        onDismissRequest = { installSheetOpen = false },
        onPickFiles = {
            installSheetOpen = false
            apkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
        },
        onPickLocalApps = {
            installSheetOpen = false
            // 从这里进就是新一次选择：中断过的安装会把上次的搜索词与勾选留在会话里
            viewModel.clearLocalPickerSession()
            localPickerOpen = true
            viewModel.loadLocalApps()
        },
    )

    // 本机应用选择器（会话状态在 ViewModel）
    val localApps by viewModel.localApps.collectAsState()
    val localAppsLoading by viewModel.localAppsLoading.collectAsState()
    val localAppsLoaded by viewModel.localAppsLoaded.collectAsState()
    val localPermissionMissing by viewModel.localAppsPermissionMissing.collectAsState()
    val localSlow by viewModel.localAppsSlow.collectAsState()
    val localIconEpoch by viewModel.localIconEpoch.collectAsState()
    LocalAppPickerSheet(
        visible = localPickerOpen,
        apps = localApps,
        loading = localAppsLoading,
        permissionMissing = localPermissionMissing,
        slow = localSlow,
        query = viewModel.localPickerQuery,
        selectedPackages = viewModel.localPickerSelection.toSet(),
        busy = uiState.busy,
        loaded = localAppsLoaded,
        batchMode = viewModel.localBatchMode,
        onBatchModeChange = { viewModel.localBatchMode = it },
        iconEpoch = localIconEpoch,
        onQueryChange = viewModel::onLocalPickerQueryChange,
        onToggleSelect = viewModel::toggleLocalSelection,
        onRefresh = viewModel::loadLocalApps,
        onClearSelection = viewModel::clearLocalSelection,
        hideVersion = hideVersion,
        hideApkSize = hideApkSize,
        onToggleHideVersion = viewModel::toggleHideVersion,
        onToggleHideApkSize = viewModel::toggleHideApkSize,
        onInstallSingleApp = { app ->
            // 单击直接弹安装确认弹窗：快照该应用，等对话框回来再执行
            localPickerOpen = false
            confirmRequest = ConfirmRequest(
                action = AppAction.UNINSTALL,
                app = null,
                batchCount = 1,
                isInstall = true,
                installApps = listOf(app),
                splitCount = if (app.apkPaths.size > 1) 1 else 0,
            )
            confirmVisible = true
        },
        onConfirm = {
            localPickerOpen = false
            // 选择集必须在这一刻快照：确认框期间选择器被隐藏，但 VM 会话保留；安装完成后 clearLocalPickerSession 才会清它，
            val picked = localApps.filter { it.packageName in viewModel.localPickerSelection }
            confirmRequest = ConfirmRequest(
                action = AppAction.UNINSTALL,
                app = null,
                batchCount = picked.size,
                isInstall = true,
                installApps = picked,
                splitCount = picked.count { it.apkPaths.size > 1 },
            )
            confirmVisible = true
        },
        onDismissRequest = {
            // 主动关闭：搜索词与选择集均清空
            viewModel.clearLocalPickerSession()
            localPickerOpen = false
        },
        onGoToSettings = {
            val opened = runCatching {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (!opened) {
                snackbar.show(AppRuntime.stringResource(R.string.pref_cannot_open_settings))
            }
        },
    )

    // 执行已确认的操作（对话框收起后由 LaunchedEffect 调用）
    val executeConfirm: (ConfirmRequest) -> Unit = { request ->
        when {
            request.isInstall -> {
                // 执行只认点确认时定下的快照：弹窗收起后选择集不再参与本次安装
                val appsToInstall = request.installApps
                DeviceAppRepository.installLocalApps(appsToInstall) { outcome ->
                    snackbar.show(installOutcomeText(outcome, appsToInstall.singleOrNull()?.label))
                    viewModel.clearLocalPickerSession()
                }
            }

            // 单个应用导出：与批量导出同一条路径，落盘位置由系统版本决定
            request.action == AppAction.EXPORT_APK && request.app != null ->
                startApkExport(request.app)

            request.app != null -> DeviceAppRepository.performAction(
                request.action,
                request.app,
            ) { result ->
                snackbar.show(
                    when (result) {
                        is DeviceAppRepository.ActionResult.Success ->
                            singleSuccessText(request.action, result.app)

                        is DeviceAppRepository.ActionResult.Failure ->
                            singleFailureText(request.action, result.app, result.error)
                    },
                )
            }

            // 批量导出：只选一个时按单包导出（文件名 = 包名.apk），多个才打 ZIP
            request.action == AppAction.EXPORT_APK && request.app == null && !request.isInstall -> {
                val appsToExport = currentSelection()
                when (appsToExport.size) {
                    0 -> batchMode = false
                    1 -> startApkExport(appsToExport.first())
                    else -> startZipExport(appsToExport)
                }
            }

            else -> {
                // 先退批量模式再执行：底栏立刻收起；执行与随后的列表刷新在后台跑完，
                // 批量停用 / 启用不占界面等待刷新完成。
                // 清单先取快照——selected.clear() 之后就读不到了；
                // 走 batchTargets 过滤：启用只发给出已停用的条目，已经是目标态的不重复操作
                val targets = batchTargets(request.action).map { it.packageName }
                batchMode = false
                selected.clear()
                DeviceAppRepository.performBatch(request.action, targets) { outcome ->
                    snackbar.show(batchOutcomeText(request.action, outcome))
                }
            }
        }
    }

    // 安装 / 提取进度弹窗：模态盖住本页，期间页面不可操作
    val transfer by DeviceAppRepository.transfer.collectAsState()
    AppManagerTransferProgress(
        progress = transfer,
        onAbort = {
            DeviceAppRepository.cancelTransfer()
            snackbar.show(AppRuntime.stringResource(R.string.app_manager_transfer_aborted))
        },
    )

    // 二次确认对话框：确认后先收起再执行
    val confirm = confirmRequest
    AppConfirmDialog(
        visible = confirmVisible && confirm != null,
        title = when {
            confirm?.isInstall == true -> stringResource(R.string.app_manager_confirm_title_install)
            // 批量导出（含只选一个的情形）：标题带数量
            confirm?.action == AppAction.EXPORT_APK && confirm.app == null ->
                stringResource(R.string.app_manager_confirm_title_batch_export, confirm.batchCount)

            // 单个应用导出：标题带应用名，与其它单应用动作同一形态
            confirm?.action == AppAction.EXPORT_APK && confirm.app != null ->
                stringResource(R.string.app_manager_confirm_title_export_single, confirm.app.displayLabel)

            else -> stringResource(R.string.app_manager_confirm_title_app, confirm?.batchCount ?: 0)
        },
        summary = confirmSummary(confirm),
        onConfirm = {
            if (confirm != null) {
                pendingExecute = { executeConfirm(confirm) }
            }
            confirmVisible = false
        },
        onDismissRequest = {
            confirmVisible = false
            confirmRequest = null
            if (confirm?.isInstall == true) {
                // 被对话框挤占后取消：选择器恢复原状，搜索词与选择集均保留
                localPickerOpen = true
            }
        },
    )
    LaunchedEffect(confirmVisible) {
        if (!confirmVisible) {
            pendingExecute?.invoke()
            pendingExecute = null
        }
    }
        }
    }
}

/**
 * 单包导出在 Android 9 及以下的状态：保存框弹出前先问设备要形态。
 */
private sealed interface ExportPrompt {
    val app: DeviceApp

    /** 正在问设备 */
    data class Resolving(override val app: DeviceApp) : ExportPrompt

    /** 保存框已弹出，等用户选位置 */
    data class AwaitingLocation(
        override val app: DeviceApp,
        val kind: DeviceAppRepository.ExportKind,
    ) : ExportPrompt
}

/**
 * 系统文件页面的包名：GMS 与 AOSP 两种命名，按顺序试。
 *
 * 目录 uri 的其它候选常是网盘与浏览器，显式指包才落得到文件页面。
 */
private val DocumentsUiPackages = listOf(
    "com.google.android.documentsui",
    "com.android.documentsui",
)

private const val AppManagerGroupCount = 2

/** 运行态定时刷新的间隔：只在应用管理页处于前台时生效 */
private const val RUNNING_REFRESH_INTERVAL_MS = 30_000L

/**
 * 错误文案。
 *
 * 回调可能运行在组合之外（协程回调），统一走 [AppRuntime.stringResource]
 * 按应用内语言取文案，不依赖组合上下文。
 */
private fun errorText(error: AppManagerError): String = when (error) {
    is AppManagerError.ChannelOccupied ->
        AppRuntime.stringResource(R.string.app_manager_adb_channel_occupied)

    is AppManagerError.Device -> when (error.reason) {
        DeviceAppRepository.EXPORT_NOT_PUBLISHED ->
            AppRuntime.stringResource(R.string.app_manager_export_not_published)

        DeviceAppRepository.EXPORT_NO_TARGET ->
            AppRuntime.stringResource(R.string.app_manager_export_no_target)

        else -> error.reason
    }
    AppManagerError.NotConnected ->
        AppRuntime.stringResource(R.string.app_manager_not_connected)

    AppManagerError.Slow ->
        AppRuntime.stringResource(R.string.app_manager_load_timeout, DeviceAppRepository.LOAD_TIMEOUT_MS / 1000)
}

private fun singleSuccessText(action: AppAction, app: DeviceApp): String = when (action) {
    AppAction.OPEN -> AppRuntime.stringResource(R.string.app_manager_opening, app.displayLabel)
    AppAction.FORCE_STOP -> AppRuntime.stringResource(R.string.app_manager_stopped, app.displayLabel)
    AppAction.CLEAR_DATA ->
        AppRuntime.stringResource(R.string.app_manager_data_cleared, app.displayLabel)

    AppAction.DISABLE -> AppRuntime.stringResource(R.string.app_manager_disabled_done, app.displayLabel)
    AppAction.ENABLE -> AppRuntime.stringResource(R.string.app_manager_enabled_done, app.displayLabel)
    AppAction.UNINSTALL -> AppRuntime.stringResource(R.string.app_manager_uninstalled, app.displayLabel)
    AppAction.EXPORT_APK -> AppRuntime.stringResource(R.string.app_manager_exported, app.displayLabel)
}

/** 安装 / 提取进度弹窗：模态盖住本页，取消需二次确认。 */
@Composable
private fun AppManagerTransferProgress(
    progress: TransferProgress,
    onAbort: () -> Unit,
) {
    TransferProgressDialog(
        progress = progress,
        exporting = progress.exporting,
        onAbortConfirm = onAbort,
    )
}

/**
 * 批量 ZIP 导出的收尾提示。
 *
 * 产物被撤回落盘目标时按「整批未导出」报，不报计数：
 * [DeviceAppRepository.ExportOutcome.successCount] 答的是「哪些包拉成功过」，用户实际拿到
 * 的是一个字节都没有，提示必须与后者对齐。
 *
 * [toDownloads] 由调用方按实际走的路径给：提示要说清产物落在哪，两条路径的位置不同。
 */
private fun zipExportOutcomeText(
    outcome: DeviceAppRepository.ExportOutcome,
    total: Int,
    toDownloads: Boolean,
): String = if (outcome.archived) {
    AppRuntime.stringResource(
        when {
            // 有落单的应用时把失败数与原因一起说出来：「已导出 3 / 4 个」看不出少的那一个为什么没成
            outcome.failureCount > 0 -> R.string.app_manager_export_outcome_partial
            toDownloads -> R.string.app_manager_export_outcome_downloads
            else -> R.string.app_manager_export_outcome
        },
        outcome.successCount,
        total,
        // 失败数在这里算：资源串只做排版，不带算术
        total - outcome.successCount,
        errorText(AppManagerError.Device(outcome.lastError.orEmpty())),
    )
} else {
    AppRuntime.stringResource(R.string.app_manager_export_rolled_back, total)
}

/** 单包导出的失败文案，按动作分派。 */
private fun singleFailureText(action: AppAction, app: DeviceApp, error: AppManagerError): String =
    when (action) {
        AppAction.OPEN -> AppRuntime.stringResource(
            R.string.app_manager_open_failed,
            app.displayLabel,
            errorText(error),
        )

        AppAction.EXPORT_APK -> AppRuntime.stringResource(
            R.string.app_manager_export_failed,
            errorText(error),
        )

        else -> AppRuntime.stringResource(
            when (action) {
                AppAction.UNINSTALL -> R.string.app_manager_uninstall_failed
                AppAction.DISABLE -> R.string.app_manager_disable_failed
                AppAction.ENABLE -> R.string.app_manager_enable_failed
                AppAction.FORCE_STOP -> R.string.app_manager_force_stop_failed
                AppAction.CLEAR_DATA -> R.string.app_manager_clear_data_failed
                AppAction.OPEN, AppAction.EXPORT_APK -> R.string.app_manager_uninstall_failed
            },
            errorText(error),
        )
    }

/** 确认框摘要末尾的 split 提示行：有分包才多出一行，批量文案里顺带报分包个数。 */
private fun splitHintLine(count: Int, resId: Int, withCount: Boolean = false): String {
    if (count <= 0) return ""
    val text = if (withCount) {
        AppRuntime.stringResource(resId, count)
    } else {
        AppRuntime.stringResource(resId)
    }
    return "\n" + text
}

/**
 * 缺分包失败的原始输出是 `INSTALL_FAILED_MISSING_SPLIT: Missing split for <包名>`：
 * 原样抛给用户等于没说，按标记换成一句能看懂的话。
 */
private const val MISSING_SPLIT_MARKER = "MISSING_SPLIT"

/** 安装结果聚合文案；[appName] 只在本次就装一个包、且调用方拿得到本机应用名时给。 */
private fun installOutcomeText(
    outcome: DeviceAppRepository.InstallOutcome,
    appName: String? = null,
): String {
    val missingSplit = outcome.lastError?.contains(MISSING_SPLIT_MARKER) == true
    return when {
        outcome.successCount == 1 && appName != null -> AppRuntime.stringResource(
            R.string.app_manager_install_success_named,
            appName,
        )

        outcome.failureCount == 0 -> AppRuntime.stringResource(
            R.string.app_manager_install_success,
            outcome.successCount,
        )

        outcome.successCount == 0 -> AppRuntime.stringResource(
            if (missingSplit) {
                R.string.app_manager_install_failed_missing_split
            } else {
                R.string.app_manager_install_failed
            },
            outcome.lastError ?: "",
        )

        else -> AppRuntime.stringResource(
            R.string.app_manager_install_partial,
            outcome.successCount,
            outcome.failureCount,
            // 缺分包是这批里唯一值得单独说清的失败，其余照旧透传原始文本
            if (missingSplit) {
                AppRuntime.stringResource(R.string.app_manager_install_failed_missing_split)
            } else {
                outcome.lastError ?: ""
            },
        )
    }
}

/** 批量结果聚合文案。 */
private fun batchOutcomeText(
    action: AppAction,
    outcome: DeviceAppRepository.BatchOutcome,
): String = when {
    outcome.failureCount == 0 -> AppRuntime.stringResource(
        when (action) {
            AppAction.UNINSTALL -> R.string.app_manager_batch_success_uninstall
            AppAction.DISABLE -> R.string.app_manager_batch_success_disable
            AppAction.ENABLE -> R.string.app_manager_batch_success_enable
            else -> R.string.app_manager_batch_success_uninstall
        },
        outcome.successCount,
    )

    outcome.successCount == 0 -> AppRuntime.stringResource(
        R.string.app_manager_batch_all_failed,
        outcome.failureCount,
        outcome.lastError ?: "",
    )

    else -> AppRuntime.stringResource(
        R.string.app_manager_batch_partial,
        outcome.successCount,
        outcome.failureCount,
        outcome.lastError ?: "",
    )
}

@Composable
private fun confirmSummary(confirm: ConfirmRequest?): String {
    if (confirm == null) return ""
    // 落盘位置由系统版本决定：Android 10 及以上直接写系统 Download/Scrcpy 目录，更早的版本
    // 没有分区插入，弹保存框由用户选。摘要说实际会发生的事——两句话各自对应一条路径。
    val toDownloads = DeviceAppRepository.canPublishToDownloads()
    return when {
        confirm.isInstall && confirm.batchCount == 1 && confirm.installApps.size == 1 -> stringResource(
            R.string.app_manager_local_install_confirm_single,
            confirm.installApps[0].label,
        ) + splitHintLine(
            confirm.splitCount,
            R.string.app_manager_local_install_confirm_single_split,
        )

        confirm.isInstall -> stringResource(
            R.string.app_manager_local_install_confirm,
            confirm.batchCount,
        ) + splitHintLine(
            confirm.splitCount,
            R.string.app_manager_local_install_confirm_split,
            withCount = true,
        )

        // 只选一个包时批量入口会走单包导出：摘要得跟那条路一致，不然确认框说的和落盘的不是一回事
        confirm.action == AppAction.EXPORT_APK && confirm.batchCount == 1 -> stringResource(
            if (toDownloads) {
                R.string.app_manager_confirm_summary_export_single_downloads
            } else {
                R.string.app_manager_confirm_summary_export_single
            },
        ) + splitHintLine(
            confirm.splitCount,
            R.string.app_manager_confirm_summary_export_single_split,
        )

        confirm.action == AppAction.EXPORT_APK -> stringResource(
            if (toDownloads) {
                R.string.app_manager_confirm_summary_batch_export_downloads
            } else {
                R.string.app_manager_confirm_summary_batch_export
            },
        ) + splitHintLine(
            confirm.splitCount,
            R.string.app_manager_confirm_summary_batch_export_split,
            withCount = true,
        )

        confirm.app != null -> stringResource(
            when (confirm.action) {
                AppAction.UNINSTALL -> R.string.app_manager_confirm_uninstall_single
                AppAction.DISABLE -> R.string.app_manager_confirm_disable_single
                AppAction.ENABLE -> R.string.app_manager_confirm_enable_single
                AppAction.FORCE_STOP -> R.string.app_manager_confirm_force_stop_single
                AppAction.CLEAR_DATA -> R.string.app_manager_confirm_clear_data_single
                else -> R.string.app_manager_confirm_uninstall_single
            },
            confirm.app.displayLabel,
        )

        else -> stringResource(
            when (confirm.action) {
                AppAction.UNINSTALL -> R.string.app_manager_confirm_uninstall_batch
                AppAction.DISABLE -> R.string.app_manager_confirm_disable_batch
                AppAction.ENABLE -> R.string.app_manager_confirm_enable_batch
                else -> R.string.app_manager_confirm_uninstall_batch
            },
            confirm.batchCount,
        )
    }
}
