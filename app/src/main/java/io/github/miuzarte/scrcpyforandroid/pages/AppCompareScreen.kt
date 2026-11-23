package io.github.miuzarte.scrcpyforandroid.pages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.data.DeviceAppRepository
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppManagerMotion
import io.github.miuzarte.scrcpyforandroid.appmanager.data.LocalApkInstall
import io.github.miuzarte.scrcpyforandroid.appmanager.data.LocalAppRepository
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppAction
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppOrdering
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.SizeFormat
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.LocalApp
import io.github.miuzarte.scrcpyforandroid.appmanager.data.currentDeviceKey
import io.github.miuzarte.scrcpyforandroid.appmanager.data.exportFileName
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.openExportTarget
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppManagerRefreshIconSize
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.appManagerErrorText
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.installFailureReason
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.LOCAL_PERMISSION_RETRY_ATTEMPTS
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.LOCAL_PERMISSION_RETRY_DELAY_MS
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.localReadPermissionSettingsIntent
import io.github.miuzarte.scrcpyforandroid.ui.DialogHeading
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.MaskedTopBarAction
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.TransferProgressDialog
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.rememberGlassHighlight
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalBlurMode
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.ui.rememberPageScrollBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.OutputStream
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface

/** 对比页的操作方向：本机应用装到 ADB 设备，或把 ADB 设备的应用导回本机。 */
internal enum class CompareDirection { LOCAL_TO_REMOTE, REMOTE_TO_LOCAL }

/**
 * 一行对比结果。[presence] 为 true 是单边安装行，其余是版本候选行：
 * 两侧都报版本时比新旧，某一侧没报版本时按方向保留，不做新旧判断。
 *
 * [split] 表示这次要搬运的那一侧带分包：安装要推整组分包，导出产物是 `.apks` 容器。
 * [update] 已知这次是更新：目标侧报了旧版本、本机版本更高；目标侧没报版本时按「安装」处理。
 */
internal data class CompareRow(
    val packageName: String,
    val label: String,
    val localVersion: String?,
    val remoteVersion: String?,
    val localSizeBytes: Long = 0L,
    val remoteSizeBytes: Long = 0L,
    val split: Boolean = false,
    /** 单边安装的列表行：详情行写「仅本机 / 仅 ADB 设备」，不是版本差。 */
    val presence: Boolean = false,
    /** 单边行只存在于 ADB 设备侧。 */
    val presenceOnRemote: Boolean = false,
    /** 大小取 ADB 设备那一侧：版本行看目标侧，单边行看实际存在的一侧。 */
    val sizeOnRemote: Boolean = false,
    val update: Boolean = false,
)

/**
 * 按方向取版本差异：本机 → ADB 设备收本机版本更高的行，ADB 设备 → 本机收设备版本更高的行；
 * 目标侧没报版本时保留（本机 → ADB 设备两侧都没报也保留），源侧没报版本就不作候选。
 */
internal fun compareVersionRows(
    direction: CompareDirection,
    local: List<LocalApp>,
    remote: List<DeviceApp>,
): List<CompareRow> {
    // 远端按全量包名建索引：同包名在设备上是系统应用时，本机那份用户应用照样要能比较，
    // 否则版本区块看不到它、存在性区块又因包名命中不算独有，整行静默消失
    val remoteByPackage = remote.associateBy { it.packageName }
    return local.filterNot { it.isSystem }.mapNotNull { localApp ->
        val remoteApp = remoteByPackage[localApp.packageName] ?: return@mapNotNull null
        val localVersion = localApp.versionName.ifBlank { null }
        val remoteVersion = remoteApp.versionName.ifBlank { null }
        // 目标侧没报版本就保留：换不换得动说不准，列出来由用户定；源侧没报版本时连对比基线都没有
        val newer = when {
            localVersion == null && remoteVersion == null ->
                direction == CompareDirection.LOCAL_TO_REMOTE

            localVersion == null -> direction == CompareDirection.REMOTE_TO_LOCAL
            remoteVersion == null -> direction == CompareDirection.LOCAL_TO_REMOTE
            direction == CompareDirection.LOCAL_TO_REMOTE ->
                compareVersionNames(localVersion, remoteVersion) > 0
            else -> compareVersionNames(remoteVersion, localVersion) > 0
        }
        if (!newer) return@mapNotNull null
        CompareRow(
            packageName = localApp.packageName,
            label = localApp.label.ifBlank { localApp.packageName },
            localVersion = localVersion,
            remoteVersion = remoteVersion,
            localSizeBytes = localApp.apkSizeBytes,
            remoteSizeBytes = remoteApp.apkSizeBytes,
            split = when (direction) {
                CompareDirection.LOCAL_TO_REMOTE -> localApp.apkPaths.size > 1
                CompareDirection.REMOTE_TO_LOCAL -> remoteApp.isSplit
            },
            sizeOnRemote = direction == CompareDirection.REMOTE_TO_LOCAL,
            // 设备上已有这个包、版本也报得出来，才说得上「更新」；两侧都缺版本时它只是同包名的安装项
            update = direction == CompareDirection.LOCAL_TO_REMOTE &&
                    localVersion != null && remoteVersion != null,
        )
    }.sortedLikeAppManager()
}

/** 取只有一侧安装的应用；方向决定保留本机独有还是 ADB 设备独有。 */
internal fun comparePresenceRows(
    direction: CompareDirection,
    local: List<LocalApp>,
    remote: List<DeviceApp>,
): List<CompareRow> {
    // 包名集合按全量算：设备上的系统应用同样占着这个包名，只看用户应用会把同包名的本机应用误报成独有
    val localPackages = local.mapTo(HashSet()) { it.packageName }
    val remotePackages = remote.mapTo(HashSet()) { it.packageName }
    return when (direction) {
        CompareDirection.LOCAL_TO_REMOTE -> local.filterNot { it.isSystem }.mapNotNull { app ->
            if (app.packageName in remotePackages) return@mapNotNull null
            CompareRow(
                packageName = app.packageName,
                label = app.label.ifBlank { app.packageName },
                localVersion = app.versionName.ifBlank { null },
                remoteVersion = null,
                localSizeBytes = app.apkSizeBytes,
                split = app.apkPaths.size > 1,
                presence = true,
            )
        }

        CompareDirection.REMOTE_TO_LOCAL -> remote.filterNot { it.isSystem }.mapNotNull { app ->
            if (app.packageName in localPackages) return@mapNotNull null
            CompareRow(
                packageName = app.packageName,
                label = app.label.ifBlank { app.packageName },
                localVersion = null,
                remoteVersion = app.versionName.ifBlank { null },
                remoteSizeBytes = app.apkSizeBytes,
                split = app.isSplit,
                presence = true,
                presenceOnRemote = true,
                sizeOnRemote = true,
            )
        }
    }.sortedLikeAppManager()
}

/**
 * 与应用管理同一把排序尺：拼音首字母、档位、包名兜底。
 *
 * 每比较一对都要重算拼音，先把键算出来再排，几百行时不会卡一下。
 */
private fun List<CompareRow>.sortedLikeAppManager(): List<CompareRow> {
    val keyed = map { row -> row to AppOrdering.sortKey(row.label, row.packageName) }
    return keyed.sortedWith { a, b -> AppOrdering.compareKeys(a.second, b.second) }.map { it.first }
}

/** 本机 → ADB 设备方向上，已经装成功的行不再列出；另一方向与这批包无关。 */
private fun List<CompareRow>.withoutInstalled(
    direction: CompareDirection,
    installed: Set<String>,
): List<CompareRow> {
    if (direction != CompareDirection.LOCAL_TO_REMOTE || installed.isEmpty()) return this
    return filterNot { it.packageName in installed }
}

@Composable
internal fun AppCompareScreen() {
    val snackHost = remember { SnackbarHostState() }
    DisposableEffect(snackHost) {
        val unregister = AppRuntime.registerSnackbarHostState(snackHost)
        onDispose { unregister() }
    }
    val navigator = LocalRootNavigator.current
    val scrollBehavior = rememberPageScrollBehavior()
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    val scope = rememberCoroutineScope()
    // 顶栏按钮材质与列表页同一套：渐进模糊下遮罩随列表滚动淡入，其余模式不着遮罩
    val asBundle by appSettings.bundleState.collectAsState()
    val blurMode = LocalBlurMode.current
    val topBarMaskEnabled = blurBackdrop != null && blurMode == AppSettings.BlurMode.PROGRESSIVE
    val topBarGlass = topBarMaskEnabled && asBundle.floatingBottomBarBlur
    val maskAlpha = remember { Animatable(0f) }
    val topBarHighlight = rememberGlassHighlight()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    LaunchedEffect(topBarMaskEnabled, listState) {
        if (!topBarMaskEnabled) {
            maskAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        snapshotFlow {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                with(density) {
                    (listState.firstVisibleItemScrollOffset / 48.dp.toPx()).coerceIn(0f, 1f)
                }
            }
        }.collectLatest { maskAlpha.animateTo(it) }
    }

    val uiState by DeviceAppRepository.uiState.collectAsState()
    // 从入口直达这一页时没人替它触发远端加载
    SideEffect { DeviceAppRepository.onEnter(currentDeviceKey()) }
    var localApps by remember { mutableStateOf<List<LocalApp>>(emptyList()) }
    var localLoading by remember { mutableStateOf(true) }
    var localTimedOut by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var remoteModel by remember { mutableStateOf<String?>(null) }
    var remoteBrand by remember { mutableStateOf<String?>(null) }
    // 折叠状态不按方向分开存：切方向时展开状态若跟着变，会播一段没意义的收起/展开动画
    var versionCollapsed by remember { mutableStateOf(false) }
    // 「仅本机存在 / 仅 ADB 设备存在」默认收起：几百行单边安装的应用没必要一进来就铺满
    var presenceCollapsed by remember { mutableStateOf(true) }
    var confirmOpen by remember { mutableStateOf(false) }
    // 待执行的行：底部按钮带整批，行尾按钮带单条，确认窗与执行都按这份列表走
    var pendingRows by remember { mutableStateOf<List<CompareRow>>(emptyList()) }
    // 长按才进多选：平时行尾是单条操作，点行本身不该在选择状态里来回跳
    var selectMode by remember { mutableStateOf(false) }
    var direction by remember { mutableStateOf(CompareDirection.LOCAL_TO_REMOTE) }
    var localIconEpoch by remember { mutableStateOf(0) }
    // 这一轮刚装成功的包名：行立刻退场，不等远端刷新回来
    var installedPackages by remember { mutableStateOf(emptySet<String>()) }
    // 本机侧读不到列表（没给「读取应用列表」权限）：此时两侧都没有比较的底
    var localUnavailable by remember { mutableStateOf(false) }
    var localRetryActive by remember { mutableStateOf(false) }
    // 本机列表的加载代次：自动刷新与授权后补读会同时发生，只认最后一次发起的结论
    var localLoadGeneration by remember { mutableStateOf(0) }
    // Android 9 及以下写不进 Download/Scrcpy，落盘位置交给系统保存框，这里存它等的对象
    var safSingle by remember { mutableStateOf<DeviceApp?>(null) }
    var safSingleKind by remember { mutableStateOf<DeviceAppRepository.ExportKind?>(null) }
    var safZipApps by remember { mutableStateOf<List<DeviceApp>>(emptyList()) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 导出产物点「安装」的续装状态：缺「安装未知应用」权限时先拉系统设置，回页后续装一次
    var installInFlight by remember { mutableStateOf(false) }
    var pendingInstallUri by remember { mutableStateOf<Uri?>(null) }
    var pendingInstallContent by remember { mutableStateOf<Uri?>(null) }
    // 导出产物落在本机后，用户可能已经装上或打开了它；离开过 app 再回来就重读两侧列表
    var exportedRecently by remember { mutableStateOf(false) }
    var leftAfterExport by remember { mutableStateOf(false) }
    // 后台期间的时刻：回到前台要重读两侧列表，断线或装完都不该等用户去下拉
    var backgroundedAt by remember { mutableStateOf(0L) }
    var lastAutoRefreshAt by remember { mutableStateOf(0L) }
    // 浮层式安装器不会让页面离开、也不一定抢焦点，只能靠系统包变更广播知道「装完了」
    var packageEvents by remember { mutableStateOf(0) }

    fun clearPendingInstall() {
        pendingInstallUri = null
        pendingInstallContent = null
    }

    fun reportInstallFailure(messageRes: Int) {
        AppRuntime.snackbar(AppRuntime.stringResource(messageRes))
    }

    /** 暂存件已经在缓存里：只拉起安装器，不再复制一遍整包。 */
    suspend fun launchStagedInstall(content: Uri) {
        when (val outcome = LocalApkInstall.launch(context, content)) {
            LocalApkInstall.Outcome.Started -> clearPendingInstall()

            LocalApkInstall.Outcome.NeedsPermission -> {
                clearPendingInstall()
                reportInstallFailure(R.string.app_manager_export_install_denied)
            }

            is LocalApkInstall.Outcome.Deferred -> Unit

            is LocalApkInstall.Outcome.Failed -> {
                clearPendingInstall()
                reportInstallFailure(R.string.app_manager_export_install_failed)
            }
        }
    }

    fun installExportedApk(uri: Uri, reopenSettings: Boolean = true) {
        if (installInFlight) {
            AppRuntime.snackbar(AppRuntime.stringResource(R.string.app_manager_export_install_preparing))
            return
        }
        installInFlight = true
        scope.launch {
            var resumeRetry = false
            try {
                val outcome = LocalApkInstall.install(
                    context = context,
                    source = uri,
                    isForeground = {
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    },
                )
                when (outcome) {
                    LocalApkInstall.Outcome.Started -> clearPendingInstall()

                    is LocalApkInstall.Outcome.Deferred -> {
                        // 复制结束时页面已经退到后台：系统会丢弃这次启动，留给回页时拉起
                        pendingInstallUri = uri
                        pendingInstallContent = outcome.contentUri
                        resumeRetry = lifecycleOwner.lifecycle.currentState
                            .isAtLeast(Lifecycle.State.RESUMED)
                    }

                    LocalApkInstall.Outcome.NeedsPermission -> {
                        // 从设置页回来仍没授权：丢掉这次请求，别再把它顶回设置页
                        if (!reopenSettings) {
                            clearPendingInstall()
                            reportInstallFailure(R.string.app_manager_export_install_denied)
                            return@launch
                        }
                        pendingInstallUri = uri
                        pendingInstallContent = null
                        val opened = runCatching {
                            context.startActivity(LocalApkInstall.permissionSettingsIntent(context))
                        }.isSuccess
                        if (!opened) {
                            clearPendingInstall()
                            reportInstallFailure(R.string.app_manager_export_install_failed)
                        }
                    }

                    is LocalApkInstall.Outcome.Failed -> {
                        clearPendingInstall()
                        reportInstallFailure(R.string.app_manager_export_install_failed)
                    }
                }
            } finally {
                installInFlight = false
            }
            // 复制还没结束用户就回来了：ON_RESUME 会被单飞标记挡住，这里补一次拉起
            if (resumeRetry) {
                pendingInstallContent?.let { launchStagedInstall(it) }
            }
        }
    }

    /** 导出成功的提示：单个安装包给「安装」，`.apks` 与 ZIP 给「打开」交给系统挑应用。 */
    fun notifyExportDone(
        message: String,
        uri: Uri?,
        kind: DeviceAppRepository.ExportKind?,
    ) {
        exportedRecently = true
        if (uri == null || kind == null) {
            AppRuntime.snackbar(message)
            return
        }
        if (kind == DeviceAppRepository.ExportKind.APK) {
            AppRuntime.snackbar(
                message = message,
                actionLabel = AppRuntime.stringResource(R.string.app_manager_snackbar_install),
                onResult = { result ->
                    if (result == SnackbarResult.ActionPerformed) installExportedApk(uri)
                },
            )
            return
        }
        AppRuntime.snackbar(
            message = message,
            actionLabel = AppRuntime.stringResource(R.string.app_manager_snackbar_open),
            onResult = { result ->
                if (result == SnackbarResult.ActionPerformed &&
                    !openExportTarget(context, uri, kind)
                ) {
                    AppRuntime.snackbar(
                        AppRuntime.stringResource(R.string.app_manager_export_open_failed),
                    )
                }
            },
        )
    }

    // 两个方向的选择互相独立，切回来时保留原勾选
    val installSelection = remember { mutableStateListOf<String>() }
    val exportSelection = remember { mutableStateListOf<String>() }

    fun applyLocalResult(generation: Int, result: LocalAppRepository.Result) {
        if (generation != localLoadGeneration) return
        localTimedOut = result is LocalAppRepository.Result.Slow
        localUnavailable = result is LocalAppRepository.Result.PermissionMissing
        if (result is LocalAppRepository.Result.Success) {
            localApps = result.apps
            // 行内只查内存 LRU；读图是跨进程调用，预热只在 IO 里跑一次
            scope.launch {
                withContext(Dispatchers.IO) {
                    result.apps.forEach { DeviceAppRepository.warmLocalIcon(it) }
                }
                localIconEpoch += 1
            }
        }
    }

    fun loadLocal() {
        val generation = ++localLoadGeneration
        localLoading = true
        DeviceAppRepository.loadLocalApps { result ->
            if (generation != localLoadGeneration) return@loadLocalApps
            localLoading = false
            applyLocalResult(generation, result)
        }
    }

    // 从授权页回来：系统把权限写下去与本机枚举读到全量列表之间有时差，读一次常常
    // 还是只能枚举到自身，这里按次数补读，直到拿到列表
    fun retryLocalPermission() {
        if (localRetryActive) return
        localRetryActive = true
        // 补读走后台节流：整页载入卡只留给首次枚举，提示卡原位等结果
        localLoading = false
        scope.launch {
            try {
                for (attempt in 0 until LOCAL_PERMISSION_RETRY_ATTEMPTS) {
                    val generation = ++localLoadGeneration
                    val result = DeviceAppRepository.loadLocalAppsNow(background = true)
                    // Slow 是看门狗先到点：那次枚举还在跑，权限提示留在原位，
                    // 本轮到此为止，等下一次回页再补读
                    if (result is LocalAppRepository.Result.Slow) return@launch
                    applyLocalResult(generation, result)
                    if (result is LocalAppRepository.Result.Success) return@launch
                    if (attempt < LOCAL_PERMISSION_RETRY_ATTEMPTS - 1) delay(LOCAL_PERMISSION_RETRY_DELAY_MS)
                }
            } finally {
                localRetryActive = false
            }
        }
    }

    LaunchedEffect(Unit) { loadLocal() }

    // 回到前台时的重读：焦点回来、包变更广播、后台返回都会走到这里，节流合并成一次
    fun autoRefresh() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAutoRefreshAt < AUTO_REFRESH_MIN_INTERVAL_MS) return
        lastAutoRefreshAt = now
        DeviceAppRepository.refresh()
        loadLocal()
    }

    // 系统掐断又自己接回来的时候没人通知这页（ColorOS 在后台会断 ADB）：
    // 断开期间只问传输层，链路真的回来了才重读，免得每几秒空跑一次加载
    LaunchedEffect(uiState.notConnected) {
        if (!uiState.notConnected) return@LaunchedEffect
        while (isActive) {
            delay(CONNECTIVITY_RETRY_INTERVAL_MS)
            if (!DeviceAppRepository.uiState.value.notConnected) break
            if (!NativeAdbService.isConnected()) continue
            autoRefresh()
            break
        }
    }

    // 探测一次性失败时只留下 error：链路还在就自己再试一次，不必等下一次手动下拉
    LaunchedEffect(uiState.error) {
        if (uiState.error == null) return@LaunchedEffect
        while (isActive) {
            delay(CONNECTIVITY_RETRY_INTERVAL_MS)
            if (DeviceAppRepository.uiState.value.error == null) break
            if (!NativeAdbService.isConnected()) continue
            autoRefresh()
            break
        }
    }

    // 从「安装未知应用」设置页回来：有暂存件就拉起安装器，只有源 uri 才重新复制。
    // 观察者只登记一次：拿 pending 状态当 key 会在回页重组时重新登记，正好错过这一次 ON_RESUME
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_RESUME -> {
                    val leftAt = backgroundedAt
                    backgroundedAt = 0L
                    // 退出几秒再回来也要重读：ADB 链路在后台可能已经断开，界面不能停在旧清单上
                    if (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt >= AUTO_REFRESH_AFTER_BACKGROUND_MS) {
                        autoRefresh()
                    }
                    // 回页时本机列表还读不出来：权限可能刚在别处给过，补读几轮把列表取回来
                    if (localUnavailable) retryLocalPermission()
                    if (installInFlight) return@LifecycleEventObserver
                    val content = pendingInstallContent
                    val source = pendingInstallUri
                    if (content != null) {
                        scope.launch { launchStagedInstall(content) }
                    } else if (source != null) {
                        installExportedApk(source, reopenSettings = false)
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 第三方安装器常常是叠在本页上的对话框：页面仍停在 RESUMED，窗口焦点被拿走又还回来，
    // 焦点回来就当人回来了。装完的浮层还会让本页一条生命周期回调都收不到，包变更广播补这一次。
    DisposableEffect(exportedRecently) {
        if (!exportedRecently) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                packageEvents += 1
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(packageEvents) {
        if (packageEvents == 0) return@LaunchedEffect
        // 这一批产物可能已经被装上：先收旗再重读，免得焦点回来又刷一次
        exportedRecently = false
        leftAfterExport = false
        autoRefresh()
    }

    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) {
            if (exportedRecently) leftAfterExport = true
            return@LaunchedEffect
        }
        if (!leftAfterExport) return@LaunchedEffect
        leftAfterExport = false
        exportedRecently = false
        autoRefresh()
    }

    LaunchedEffect(uiState.notConnected, uiState.isRefreshing) {
        if (uiState.notConnected) {
            remoteModel = null
            remoteBrand = null
            return@LaunchedEffect
        }
        // 会话可用时取一次品牌与型号；刷新后也重取，换设备能跟上
        val props = runCatching {
            withContext(Dispatchers.IO) {
                NativeAdbService.shellBatch {
                    command("getprop ro.product.brand")
                    command("getprop ro.product.model")
                }
            }
        }.getOrNull()
        remoteBrand = props?.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() }
        remoteModel = props?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    // 本机侧没读出来：这时「只在本机 / 只在设备 / 版本更新」全是猜的，不能拿来当结论。
    // 远端断开是另一回事——设备清单还在手里，配上未连接提示照常展示，不把整页清空
    val localReady = !localTimedOut && !localUnavailable
    val dataReady = localReady && !uiState.notConnected && uiState.error == null
    val versionRowsRaw = remember(localApps, uiState.apps, direction, localReady) {
        if (localReady) compareVersionRows(direction, localApps, uiState.apps) else emptyList()
    }
    val presenceRowsRaw = remember(localApps, uiState.apps, direction, localReady) {
        if (localReady) comparePresenceRows(direction, localApps, uiState.apps) else emptyList()
    }
    // 装成功的行先撤下：远端列表要等刷新回来才知道设备已经变新
    val versionRows = remember(versionRowsRaw, installedPackages, direction) {
        versionRowsRaw.withoutInstalled(direction, installedPackages)
    }
    val presenceRows = remember(presenceRowsRaw, installedPackages, direction) {
        presenceRowsRaw.withoutInstalled(direction, installedPackages)
    }
    // 远端数据跟上后清空：同一个包以后又落后于设备时不该被上一轮的结果挡住
    LaunchedEffect(versionRowsRaw, presenceRowsRaw, direction) {
        if (direction == CompareDirection.LOCAL_TO_REMOTE &&
            installedPackages.isNotEmpty() &&
            (versionRowsRaw + presenceRowsRaw).none { it.packageName in installedPackages }
        ) {
            installedPackages = emptySet()
        }
    }
    val allRows = remember(versionRows, presenceRows) { versionRows + presenceRows }
    // 图标预热是跨进程读盘，epoch 变了就换一份映射，行按新图标重组
    val localByPackage = remember(localApps, localIconEpoch) { localApps.associateBy { it.packageName } }
    val selection = when (direction) {
        CompareDirection.LOCAL_TO_REMOTE -> installSelection
        CompareDirection.REMOTE_TO_LOCAL -> exportSelection
    }
    // 勾选里可能留着已经被数据裁剪掉的包：计数与执行都按现存行算，先把残项清掉
    LaunchedEffect(direction, allRows) {
        val alive = allRows.mapTo(HashSet()) { it.packageName }
        selection.retainAll(alive)
    }
    val selectedCount = selection.size
    val allSelected = allRows.isNotEmpty() && allRows.all { it.packageName in selection }
    val noVersionDiff = stringResource(R.string.app_compare_no_version_diff)
    val noPresenceDiff = stringResource(R.string.app_compare_no_presence_diff)
    // 本机枚举或远端加载进行中时显示载入卡；远端不可用由 dataReady 抑制空态文案
    val loading = (localLoading && localApps.isEmpty()) ||
        (uiState.isLoading && uiState.apps.isEmpty())

    val rowActionLabelRes = when (direction) {
        CompareDirection.LOCAL_TO_REMOTE -> R.string.app_compare_row_install
        CompareDirection.REMOTE_TO_LOCAL -> R.string.app_compare_row_export
    }

    fun askSingle(row: CompareRow) {
        if (!dataReady) {
            AppRuntime.snackbar(AppRuntime.stringResource(R.string.app_manager_load_failed))
            return
        }
        pendingRows = listOf(row)
        confirmOpen = true
    }

    // 选择态是页面级模式：退出时连同两个方向的勾选一起清掉，顶栏返回钮与返回键都走这里
    fun exitSelectMode() {
        selectMode = false
        installSelection.clear()
        exportSelection.clear()
    }

    fun toggleRow(packageName: String) {
        // 取消到 0 项仍留在选择态：顶栏显示「已选择 0 项」，退出交给返回键或关闭按钮
        if (!selection.remove(packageName)) selection.add(packageName)
    }

    fun enterSelect(packageName: String) {
        selectMode = true
        if (packageName !in selection) selection.add(packageName)
    }

    fun toggleSection(rows: List<CompareRow>) {
        val allChosen = rows.isNotEmpty() && rows.all { it.packageName in selection }
        if (allChosen) {
            rows.forEach { selection.remove(it.packageName) }
        } else {
            rows.forEach { if (it.packageName !in selection) selection.add(it.packageName) }
        }
    }

    fun finishAction() {
        // 安装收尾由仓库自己 reload，导出不改两侧数据：这里只收选择态
        selection.clear()
        selectMode = false
    }

    fun runAction(rows: List<CompareRow>) {
        if (busy || !dataReady) return
        val packages = rows.mapTo(HashSet()) { it.packageName }
        if (packages.isEmpty()) return
        when (direction) {
            CompareDirection.LOCAL_TO_REMOTE -> {
                val targets = localApps.filter { it.packageName in packages }
                if (targets.isEmpty()) return
                busy = true
                DeviceAppRepository.installLocalApps(targets) { outcome ->
                    busy = false
                    // 装成功的行当场退场：远端列表随后才刷新，不用等它确认
                    installedPackages = installedPackages + outcome.succeededPackages
                    AppRuntime.snackbar(installSummary(outcome))
                    finishAction()
                }
            }

            CompareDirection.REMOTE_TO_LOCAL -> {
                val targets = uiState.apps.filter { it.packageName in packages }
                if (targets.isEmpty()) return
                busy = true
                val single = targets.singleOrNull()
                if (!DeviceAppRepository.canPublishToDownloads()) {
                    // 旧系统没有 Download/Scrcpy 的写入通道：单个应用先问设备要形态，再弹保存框
                    if (single != null) {
                        scope.launch {
                            when (val resolved = DeviceAppRepository.resolveExportKind(single.packageName)) {
                                is DeviceAppRepository.ExportKindResult.Ready -> {
                                    safSingle = single
                                    safSingleKind = resolved.kind
                                }

                                is DeviceAppRepository.ExportKindResult.TimedOut -> {
                                    busy = false
                                    AppRuntime.snackbar(
                                        AppRuntime.stringResource(
                                            R.string.app_manager_export_failed,
                                            AppRuntime.stringResource(R.string.app_manager_export_kind_timeout),
                                        ),
                                    )
                                }

                                is DeviceAppRepository.ExportKindResult.Unavailable -> {
                                    busy = false
                                    AppRuntime.snackbar(
                                        appActionFailureText(AppAction.EXPORT_APK, single, resolved.reason),
                                    )
                                }
                            }
                        }
                    } else {
                        safZipApps = targets
                    }
                    return
                }
                // 单个应用直接落盘成安装包：只有批量才需要 ZIP 这个归档容器
                if (single != null) {
                    DeviceAppRepository.exportApk(single) { result ->
                        busy = false
                        finishAction()
                        when (result) {
                            is DeviceAppRepository.ExportResult.Success ->
                                notifyExportDone(
                                    singleExportSummary(single, result),
                                    result.uri,
                                    result.kind,
                                )

                            is DeviceAppRepository.ExportResult.Failure ->
                                AppRuntime.snackbar(singleExportSummary(single, result))
                        }
                    }
                } else {
                    DeviceAppRepository.exportAppsAsZip(targets) { outcome ->
                        busy = false
                        finishAction()
                        notifyExportDone(
                            message = exportSummary(outcome),
                            uri = if (outcome.archived) outcome.uri else null,
                            kind = DeviceAppRepository.ExportKind.ARCHIVE,
                        )
                    }
                }
            }
        }
    }

    val transfer by DeviceAppRepository.transfer.collectAsState()

    // 数据层取消或超时收掉传输时不回回调：进度消失就代表这次动作结束，页面这边跟着解锁
    var transferSeen by remember { mutableStateOf(false) }
    LaunchedEffect(transfer.active, busy) {
        if (!busy) {
            transferSeen = false
            return@LaunchedEffect
        }
        if (transfer.active) {
            transferSeen = true
        } else if (transferSeen) {
            transferSeen = false
            busy = false
        }
    }

    /** 保存框返回的流；打不开时返回 null，调用方把系统刚建的空条目收掉。 */
    fun openSafStream(uri: Uri): OutputStream? =
        runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()

    fun discardSafDocument(uri: Uri) {
        runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    /** 用户选好位置后落盘；取消保存框没有产物，只把忙碌态收回来。 */
    fun writeSafSingle(uri: Uri?) {
        val app = safSingle ?: return
        val kind = safSingleKind
        safSingle = null
        safSingleKind = null
        if (uri == null) {
            busy = false
            return
        }
        val stream = openSafStream(uri)
        if (stream == null) {
            discardSafDocument(uri)
            busy = false
            AppRuntime.snackbar(AppRuntime.stringResource(R.string.app_manager_export_lost))
            return
        }
        DeviceAppRepository.exportApk(app, stream, uri, expectedKind = kind) { result ->
            busy = false
            finishAction()
            when (result) {
                is DeviceAppRepository.ExportResult.Success ->
                    notifyExportDone(
                        AppRuntime.stringResource(R.string.app_manager_exported, app.displayLabel),
                        uri,
                        kind,
                    )

                is DeviceAppRepository.ExportResult.Failure ->
                    AppRuntime.snackbar(appActionFailureText(AppAction.EXPORT_APK, app, result.error))
            }
        }
    }

    // 每个产物形态各一个 launcher：MIME 在创建时就定死，名称按同一形态算
    val safApkCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DeviceAppRepository.ExportKind.APK.mimeType),
    ) { uri: Uri? -> writeSafSingle(uri) }

    val safApksCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DeviceAppRepository.ExportKind.APK_SET.mimeType),
    ) { uri: Uri? -> writeSafSingle(uri) }

    val safZipCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DeviceAppRepository.ExportKind.ARCHIVE.mimeType),
    ) { uri: Uri? ->
        val apps = safZipApps
        safZipApps = emptyList()
        if (uri == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        val stream = openSafStream(uri)
        if (apps.isEmpty() || stream == null) {
            discardSafDocument(uri)
            busy = false
            AppRuntime.snackbar(AppRuntime.stringResource(R.string.app_manager_export_lost))
            return@rememberLauncherForActivityResult
        }
        DeviceAppRepository.exportAppsAsZip(apps, stream, uri) { outcome ->
            busy = false
            finishAction()
            notifyExportDone(
                message = exportSummary(outcome),
                uri = if (outcome.archived) uri else null,
                kind = DeviceAppRepository.ExportKind.ARCHIVE,
            )
        }
    }

    // 形态查完才弹保存框：扩展名与 MIME 在弹框时就定死，落盘时再变会写出名实不符的文件
    LaunchedEffect(safSingle, safSingleKind) {
        val app = safSingle ?: return@LaunchedEffect
        val kind = safSingleKind ?: return@LaunchedEffect
        val name = exportFileName(app.displayLabel, app.packageName, kind.extension)
        if (kind == DeviceAppRepository.ExportKind.APK_SET) {
            safApksCreator.launch(name)
        } else {
            safApkCreator.launch(name)
        }
    }

    // 批量 ZIP 也只在这种系统上问落盘位置
    LaunchedEffect(safZipApps) {
        if (safZipApps.isEmpty()) return@LaunchedEffect
        safZipCreator.launch("scrcpy-apps-${System.currentTimeMillis()}.zip")
    }

    // 刷新进度只由顶栏图标表达：下拉刷新那套指示器会把列表整体往下推，
    // 安装/导出刚收起列表时这一跳正好落在视线上
    val refreshing = uiState.isRefreshing || localLoading
    val refreshRotation = remember { Animatable(0f) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            while (true) {
                refreshRotation.animateTo(
                    targetValue = refreshRotation.value + 360f,
                    animationSpec = tween(900, easing = LinearEasing),
                )
            }
        }
    }

    // 选择态下返回键只退出选择：此时返回钮的语义是「关闭选择」，不是「退出页面」
    BackHandler(enabled = selectMode) { exitSelectMode() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                val heading = if (selectMode) {
                    stringResource(R.string.app_manager_selected, selectedCount)
                } else {
                    stringResource(R.string.app_compare_title)
                }
                TopAppBar(
                    title = heading,
                    largeTitle = heading,
                    color = if (blurActive) Color.Transparent else colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        // 选择态这枚按钮退出选择，不退出页面：图标跟着换成 Close
                        MaskedTopBarAction(
                            icon = if (selectMode) MiuixIcons.Basic.Close else MiuixIcons.Back,
                            contentDescription = stringResource(
                                if (selectMode) R.string.cd_close else R.string.cd_back,
                            ),
                            onClick = {
                                if (selectMode) exitSelectMode() else navigator.pop()
                            },
                            maskAlpha = { maskAlpha.value },
                            glassActive = topBarGlass,
                            backdrop = blurBackdrop,
                            highlight = topBarHighlight,
                        )
                    },
                    actions = {
                        // 刷新中：标题旁转一枚刷新钮，列表本身不跟着位移
                        if (refreshing) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(R.string.app_manager_refreshing),
                                tint = colorScheme.onSurface,
                                modifier = Modifier
                                    .size(AppManagerRefreshIconSize)
                                    .graphicsLayer { rotationZ = refreshRotation.value },
                            )
                        }
                        // 整批入口不占非选择态的顶栏
                        if (selectMode) {
                            // 图标始终是「全选」，靠底色区分全选与否
                            MaskedTopBarAction(
                                icon = MiuixIcons.SelectAll,
                                contentDescription = stringResource(
                                    if (allSelected) R.string.cd_clear_selection else R.string.cd_select_all,
                                ),
                                onClick = {
                                    if (allSelected) {
                                        // 清空勾选但不退出选择态：与「取消到 0 项」保持同一个状态
                                        selection.clear()
                                    } else {
                                        allRows.forEach {
                                            if (it.packageName !in selection) selection.add(it.packageName)
                                        }
                                    }
                                },
                                maskAlpha = { maskAlpha.value },
                                glassActive = topBarGlass,
                                backdrop = blurBackdrop,
                                highlight = topBarHighlight,
                                tint = if (allSelected) colorScheme.primary else colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            // 底部操作条只在选择态出现：没进长按选择前不占位、不留渐变底衬
            if (selectMode && selectedCount > 0) {
                val selectedRows = allRows.filter { it.packageName in selection }
                val updates = selectedRows.count { it.update }
                // 按钮说清这一批是已知更新、都按安装处理，还是两种都有
                val actionText = when (direction) {
                    CompareDirection.REMOTE_TO_LOCAL ->
                        stringResource(R.string.app_compare_export_selected, selectedCount)

                    CompareDirection.LOCAL_TO_REMOTE -> when {
                        selectedRows.isNotEmpty() && updates == selectedRows.size ->
                            stringResource(R.string.app_compare_update_selected, selectedCount)

                        selectedRows.isNotEmpty() && updates == 0 ->
                            stringResource(R.string.app_compare_install_only_selected, selectedCount)

                        else -> stringResource(R.string.app_compare_install_selected, selectedCount)
                    }
                }
                CompareBottomBar(
                    actionText = actionText,
                    enabled = !busy && dataReady,
                    onAction = {
                        pendingRows = selectedRows
                        confirmOpen = true
                    },
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                state = snackHost,
                modifier = Modifier.onGloballyPositioned {
                    AppRuntime.markSnackbarHostStateLaidOut(snackHost)
                },
            )
        },
    ) { pagePadding ->
        // 进度弹窗挂在页面内容层：库用 popup 宿主呈现，离开根 Scaffold 内容就不出窗；
        // 也不能放进 LazyColumn 的 item（滚出视口就不组合，弹窗会跟着消失）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurActive) Modifier.layerBackdrop(blurBackdrop) else Modifier),
        ) {
            PullToRefresh(
                // 指示器只跟手势走：程序化刷新（安装/导出完成）只转顶栏图标，列表不往下推
                isRefreshing = false,
                onRefresh = {
                    DeviceAppRepository.refresh()
                    loadLocal()
                },
                topAppBarScrollBehavior = scrollBehavior,
                // 刷新完成文案置空：不接数据层时指示器半秒内回弹，真实进度看顶栏图标
                refreshTexts = listOf(
                    stringResource(R.string.app_manager_pull_to_refresh),
                    stringResource(R.string.app_manager_release_to_refresh),
                    stringResource(R.string.app_manager_refreshing),
                    "",
                ),
                contentPadding = PaddingValues(top = pagePadding.calculateTopPadding()),
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = UiSpacing.PageHorizontal,
                        end = UiSpacing.PageHorizontal,
                        top = pagePadding.calculateTopPadding() + UiSpacing.PageVertical,
                        bottom = pagePadding.calculateBottomPadding() + UiSpacing.PageVertical,
                    ),
                    // 区块间距由各块自己的下边距给出：长列表的每行独立成 item，
                    // 用页级 spacedBy 会在行与行之间也插空档，行就拼不成一张卡
                    verticalArrangement = Arrangement.Top,
                ) {
                    item {
                        DeviceCard(
                            connected = !uiState.notConnected,
                            remoteBrand = remoteBrand,
                            remoteModel = remoteModel,
                            modifier = Modifier.padding(bottom = UiSpacing.PageItem),
                        )
                    }
                    item {
                        DirectionTabs(
                            direction = direction,
                            // 换方向先收掉选择态：两个方向各存一份勾选，留着另一边的勾选，
                            // 标题与底部操作条会按当前方向算，看起来像什么都没选
                            onSelect = { next ->
                                if (next != direction) {
                                    if (selectMode) exitSelectMode()
                                    direction = next
                                }
                            },
                            modifier = Modifier.padding(bottom = UiSpacing.PageItem),
                        )
                    }
                    if (uiState.notConnected) {
                        item {
                            NoticeCard(
                                text = stringResource(R.string.app_compare_not_connected),
                                modifier = Modifier.padding(bottom = UiSpacing.PageItem),
                            )
                        }
                    }
                    if (localTimedOut) {
                        item {
                            NoticeCard(
                                text = stringResource(R.string.app_compare_local_timeout),
                                modifier = Modifier.padding(bottom = UiSpacing.PageItem),
                            )
                        }
                    }
                    if (localUnavailable) {
                        item {
                            NoticeCard(
                                text = stringResource(R.string.app_manager_local_permission_hint),
                                modifier = Modifier.padding(bottom = UiSpacing.PageItem),
                                actionText = stringResource(R.string.app_manager_local_go_to_settings),
                                onAction = {
                                    val opened = runCatching {
                                        context.startActivity(localReadPermissionSettingsIntent(context))
                                        true
                                    }.getOrDefault(false)
                                    if (!opened) {
                                        AppRuntime.snackbar(
                                            AppRuntime.stringResource(R.string.pref_cannot_open_settings),
                                        )
                                    }
                                },
                            )
                        }
                    }
                    val remoteError = uiState.error
                    if (!uiState.notConnected && remoteError != null) {
                        item {
                            NoticeCard(
                                text = appManagerErrorText(remoteError),
                                modifier = Modifier.padding(bottom = UiSpacing.PageItem),
                            )
                        }
                    }
                    item {
                        CompareSectionHeader(
                            title = stringResource(R.string.app_compare_section_version, versionRows.size),
                            rows = versionRows,
                            selection = selection,
                            selectMode = selectMode,
                            collapsed = versionCollapsed,
                            onToggleCollapse = {
                                versionCollapsed = !versionCollapsed
                            },
                            onToggleSection = { toggleSection(versionRows) },
                            modifier = Modifier.padding(bottom = UiSpacing.PageItem),
                        )
                    }
                    if (loading) {
                        // 载入时卡片就占住行的位置：标题、方向、区块头都先立起来，只等数据
                        item { CompareLoadingCard(modifier = Modifier.fillMaxWidth()) }
                    }
                    compareSectionRows(
                        rows = versionRows,
                        collapsed = versionCollapsed,
                        loading = loading || !localReady,
                        showEmpty = dataReady,
                        emptyText = noVersionDiff,
                        rowContent = { row, shape ->
                            CompareRowItem(
                                row = row,
                                checked = row.packageName in selection,
                                selectMode = selectMode,
                                onToggle = { toggleRow(row.packageName) },
                                onEnterSelect = { enterSelect(row.packageName) },
                                onAction = { askSingle(row) },
                                // 设备上已有旧版的这一行说「更新」，首次装才说「安装」
                                actionLabelRes = if (row.update) {
                                    R.string.app_compare_row_update
                                } else {
                                    rowActionLabelRes
                                },
                                actionEnabled = dataReady,
                                icon = compareIcon(row, localByPackage),
                                shape = shape,
                            )
                        },
                    )
                    // 分割线只在这两个区块之间：末块收起时页面底部不需要收口线
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = UiSpacing.PageVertical),
                            // 只是区块间的提示线，不抢卡片的分量
                            color = colorScheme.dividerLine.copy(alpha = 0.7f),
                        )
                    }
                    item {
                        CompareSectionHeader(
                            // 单边应用按当前方向说话：本机独有的和 ADB 设备独有的，不是同一批
                            title = stringResource(
                                when (direction) {
                                    CompareDirection.LOCAL_TO_REMOTE ->
                                        R.string.app_compare_section_presence_local
                                    CompareDirection.REMOTE_TO_LOCAL ->
                                        R.string.app_compare_section_presence_remote
                                },
                                presenceRows.size,
                            ),
                            rows = presenceRows,
                            selection = selection,
                            selectMode = selectMode,
                            collapsed = presenceCollapsed,
                            onToggleCollapse = {
                                presenceCollapsed = !presenceCollapsed
                            },
                            onToggleSection = { toggleSection(presenceRows) },
                            modifier = Modifier.padding(bottom = UiSpacing.PageItem),
                        )
                    }
                    compareSectionRows(
                        rows = presenceRows,
                        collapsed = presenceCollapsed,
                        loading = loading || !localReady,
                        showEmpty = dataReady,
                        emptyText = noPresenceDiff,
                        rowContent = { row, shape ->
                            CompareRowItem(
                                row = row,
                                checked = row.packageName in selection,
                                selectMode = selectMode,
                                onToggle = { toggleRow(row.packageName) },
                                onEnterSelect = { enterSelect(row.packageName) },
                                onAction = { askSingle(row) },
                                actionLabelRes = rowActionLabelRes,
                                actionEnabled = dataReady,
                                icon = compareIcon(row, localByPackage),
                                shape = shape,
                            )
                        },
                    )
                }
            }
            TransferProgressDialog(
                progress = transfer,
                exporting = transfer.exporting,
                onAbortConfirm = {
                    DeviceAppRepository.cancelTransfer()
                    // 仓库取消时不回回调：忙碌态在这里就地解开，否则底栏一直按不动
                    busy = false
                    AppRuntime.snackbar(
                        AppRuntime.stringResource(R.string.app_manager_transfer_aborted),
                    )
                },
            )
            if (confirmOpen) {
                val updates = pendingRows.count { it.update }
                // 整批都是已知更新时标题说「更新」；含版本未知的行时退到「安装」
                val title = when (direction) {
                    CompareDirection.LOCAL_TO_REMOTE -> stringResource(
                        if (updates == pendingRows.size) R.string.app_compare_confirm_update_title
                        else R.string.app_compare_confirm_install_title,
                    )

                    CompareDirection.REMOTE_TO_LOCAL -> stringResource(R.string.app_compare_confirm_export_title)
                }
                val singleLabel = pendingRows.firstOrNull()?.label.orEmpty()
                // 目标设备说清楚：装到哪台、导到哪台的存储
                val target = when (direction) {
                    CompareDirection.LOCAL_TO_REMOTE -> remoteModel ?: stringResource(R.string.app_compare_adb_device)
                    CompareDirection.REMOTE_TO_LOCAL -> Build.MODEL
                }
                // Android 8/9 没有写公有目录的权限，导出走保存框，落点由用户选
                val canWriteDownloads = DeviceAppRepository.canPublishToDownloads()
                // 单条走单包语义，批量才谈「逐个」与「打包为 ZIP」
                val summary = when {
                    direction == CompareDirection.LOCAL_TO_REMOTE && pendingRows.size == 1 ->
                        stringResource(R.string.app_compare_confirm_install_single, singleLabel, target)

                    direction == CompareDirection.REMOTE_TO_LOCAL && pendingRows.size == 1 ->
                        stringResource(
                            if (canWriteDownloads) R.string.app_compare_confirm_export_single
                            else R.string.app_compare_confirm_export_single_legacy,
                            singleLabel,
                            target,
                        )

                    direction == CompareDirection.LOCAL_TO_REMOTE -> stringResource(
                        R.string.app_compare_confirm_install_message,
                        pendingRows.size,
                        target,
                    )

                    else -> stringResource(
                        if (canWriteDownloads) R.string.app_compare_confirm_export_message
                        else R.string.app_compare_confirm_export_message_legacy,
                        pendingRows.size,
                        target,
                    )
                }
                // 按钮写明这批里已知的走向：只有更新、只有安装处理，还是两者都有
                val action = when (direction) {
                    CompareDirection.REMOTE_TO_LOCAL ->
                        stringResource(R.string.app_compare_export_action)

                    CompareDirection.LOCAL_TO_REMOTE -> when {
                        updates == pendingRows.size ->
                            stringResource(R.string.app_compare_update_action)

                        updates == 0 ->
                            stringResource(R.string.app_compare_install_action)

                        else ->
                            stringResource(R.string.app_compare_install_update_action)
                    }
                }
                OverlayDialog(
                    show = true,
                    onDismissRequest = { confirmOpen = false },
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DialogHeading(title = title, summary = summary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                text = stringResource(R.string.button_cancel),
                                onClick = { confirmOpen = false },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = action,
                                onClick = {
                                    confirmOpen = false
                                    runAction(pendingRows)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = dataReady,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 版本更新行优先用本机图标，只有 ADB 设备一侧的用设备图标。 */
private fun compareIcon(
    row: CompareRow,
    localByPackage: Map<String, LocalApp>,
): ImageBitmap? = run {
    val local = localByPackage[row.packageName]
    (if (local != null) DeviceAppRepository.localIcon(local) else null)
        ?: DeviceAppRepository.icon(row.packageName)
}

@Composable
private fun DeviceCard(
    connected: Boolean,
    remoteBrand: String?,
    remoteModel: String?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.PageItem),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceSide(
                brand = Build.BRAND.orEmpty().ifBlank { null },
                model = Build.MODEL.orEmpty().ifBlank { "?" },
                badge = stringResource(R.string.app_compare_this_device),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = colorScheme.onSurfaceVariantSummary,
            )
            if (connected) {
                DeviceSide(
                    brand = remoteBrand,
                    model = remoteModel ?: "?",
                    badge = stringResource(R.string.app_compare_adb_device),
                    modifier = Modifier.weight(1f),
                )
            } else {
                DeviceSide(
                    model = stringResource(R.string.app_compare_not_connected_short),
                    badge = stringResource(R.string.app_compare_adb_device),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DeviceSide(
    model: String,
    badge: String,
    brand: String? = null,
    modifier: Modifier = Modifier,
) {
    // 品牌与身份角标同一行，型号独占整行：长型号不再被角标挤窄
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!brand.isNullOrBlank()) {
                Text(
                    text = brand,
                    style = textStyles.footnote2,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(UiSpacing.Small))
            }
            CompareBadge(text = badge)
        }
        Text(
            text = model,
            modifier = Modifier.fillMaxWidth(),
            style = textStyles.body1,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DirectionTabs(
    direction: CompareDirection,
    onSelect: (CompareDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 一张卡片里两段：选中段是一枚会左右滑过去的胶囊，切换方向时位置连续
    TabRowWithContour(
        tabs = listOf(
            stringResource(R.string.app_compare_tab_install),
            stringResource(R.string.app_compare_tab_export),
        ),
        selectedTabIndex = when (direction) {
            CompareDirection.LOCAL_TO_REMOTE -> 0
            CompareDirection.REMOTE_TO_LOCAL -> 1
        },
        onTabSelected = { index ->
            onSelect(
                when (index) {
                    0 -> CompareDirection.LOCAL_TO_REMOTE
                    else -> CompareDirection.REMOTE_TO_LOCAL
                },
            )
        },
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurfaceVariantSummary,
            selectedBackgroundColor = colorScheme.secondaryVariant,
            selectedContentColor = colorScheme.onSurface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun NoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val hasAction = actionText != null && onAction != null
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.PageItem),
            horizontalAlignment = if (hasAction) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = textStyles.footnote2,
                color = colorScheme.onSurfaceVariantSummary,
                textAlign = if (hasAction) TextAlign.Center else TextAlign.Start,
            )
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(UiSpacing.ContentVertical))
                ComparePill(
                    text = actionText,
                    onClick = onAction,
                    small = true,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UiSpacing.PageItem),
        style = textStyles.footnote2,
        color = colorScheme.onSurfaceVariantSummary,
    )
}

/** 首次载入的占位卡：摆在本该是行的位置，页面骨架先立着，数据回来后原位换成列表。 */
@Composable
private fun CompareLoadingCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceContainer)
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(size = 40.dp)
        Spacer(Modifier.height(UiSpacing.ContentVertical))
        Text(
            text = stringResource(R.string.app_manager_loading),
            style = textStyles.footnote1,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 区块全选、行尾单条操作、提示卡动作共用这套胶囊。 */
@Composable
private fun ComparePill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colorScheme.secondaryVariant)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(
                horizontal = if (small) 14.dp else 18.dp,
                vertical = if (small) 4.dp else 5.dp,
            ),
    ) {
        Text(
            text = text,
            style = if (small) textStyles.footnote1 else textStyles.body1,
            color = colorScheme.onSecondaryVariant.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

@Composable
private fun CompareSectionHeader(
    title: String,
    rows: List<CompareRow>,
    selection: List<String>,
    selectMode: Boolean,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleSection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = rows.count { it.packageName in selection }
    val allChosen = rows.isNotEmpty() && chosen == rows.size
    val arrowRotation by animateFloatAsState(
        targetValue = if (collapsed) 90f else -90f,
        label = "sectionArrow",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleCollapse,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            // 与「设备工具」等页内小标题同一套排版
            style = textStyles.subtitle,
            color = colorScheme.onBackgroundVariant,
        )
        // 整批入口只在选择态出现：非选择态留着它，点了勾也不会有底部操作条接住
        if (selectMode && chosen > 0) {
            ComparePill(
                text = stringResource(
                    if (allChosen) R.string.cd_clear_selection else R.string.cd_select_all,
                ),
                onClick = onToggleSection,
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(arrowRotation),
                tint = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun CompareRowItem(
    row: CompareRow,
    checked: Boolean,
    selectMode: Boolean,
    onToggle: () -> Unit,
    onEnterSelect: () -> Unit,
    onAction: () -> Unit,
    actionLabelRes: Int,
    actionEnabled: Boolean,
    icon: ImageBitmap?,
    shape: Shape = RectangleShape,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colorScheme.surfaceContainer)
            .combinedClickable(
                onClick = { if (selectMode) onToggle() },
                onLongClick = { if (!selectMode) onEnterSelect() },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.PageItem),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon = icon, label = row.label)
            Spacer(Modifier.width(UiSpacing.ContentHorizontal))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = row.label,
                        style = textStyles.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.split) {
                        Spacer(Modifier.width(UiSpacing.Small))
                        CompareBadge(text = stringResource(R.string.app_manager_split_badge))
                    }
                }
                CompareRowDetail(row = row)
            }
            if (selectMode) {
                Checkbox(state = ToggleableState(checked), onClick = null)
            } else {
                // 未进多选时行尾是单条操作
                ComparePill(
                    text = stringResource(actionLabelRes),
                    onClick = onAction,
                    enabled = actionEnabled,
                )
            }
        }
    }
}

/** 行内小角标：分包应用与设备身份共用这一套，文字由调用方给。 */
@Composable
private fun CompareBadge(text: String) {
    Text(
        text = text,
        style = textStyles.footnote2,
        color = colorScheme.onSecondaryVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colorScheme.secondaryVariant)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** 同一区块内各行拼成一张大卡片：只有首尾两行需要圆角。 */
private fun compareRowShape(index: Int, count: Int): Shape {
    val radius = 16.dp
    return when {
        count <= 1 -> RoundedCornerShape(radius)
        index == 0 -> RoundedCornerShape(topStart = radius, topEnd = radius)
        index == count - 1 -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
        else -> RectangleShape
    }
}

/** 把当前区块的行铺进 LazyColumn：行少时整体做展开/收起动画，行多时逐行懒加载免得一次组合几百行。 */
private fun LazyListScope.compareSectionRows(
    rows: List<CompareRow>,
    collapsed: Boolean,
    loading: Boolean,
    showEmpty: Boolean,
    emptyText: String,
    rowContent: @Composable (CompareRow, Shape) -> Unit,
) {
    // 载入中或数据不可用时不报「没有差异」：行还没回来，这个结论是假的
    if (loading) return
    if (rows.isEmpty()) {
        if (showEmpty) item { EmptyHint(text = emptyText) }
        return
    }
    if (rows.size > ANIMATION_ROW_LIMIT) {
        if (!collapsed) {
            itemsIndexed(rows, key = { _, row -> row.packageName }) { index, row ->
                rowContent(row, compareRowShape(index, rows.size))
            }
        }
        return
    }
    item {
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surfaceContainer),
            ) {
                rows.forEachIndexed { index, row ->
                    key(row.packageName) {
                        rowContent(row, compareRowShape(index, rows.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?, label: String) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .squircleClip(8.dp)
            // 有图标只裁边：图标自带留白时，垫底色会从留白处透出来
            .then(
                if (icon == null) {
                    Modifier.squircleSurface(color = colorScheme.secondaryVariant, cornerRadius = 8.dp)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private const val ANIMATION_ROW_LIMIT = 30
private val HEADER_HEIGHT = 40.dp

// 回前台重读的节流：焦点、包变更、后台返回三种信号常一起到，只留一次
private const val AUTO_REFRESH_MIN_INTERVAL_MS = 1_500L

// 后台不足这个时长就回来，多半只是切了一下窗口，没必要重读整份清单
private const val AUTO_REFRESH_AFTER_BACKGROUND_MS = 800L

// 断开期间的探针间隔：链路通常在一两秒内被系统重连回来，这个频率够快又不会一直占着通道
private const val CONNECTIVITY_RETRY_INTERVAL_MS = 3_000L


@Composable
private fun CompareBottomBar(
    actionText: String,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // 悬浮按钮压住列表尾部：底衬从透明渐到页面底色，滚动时内容在按钮下淡出
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppManagerMotion.BOTTOM_SCROLL_SCrim_DP.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, colorScheme.surface),
                    ),
                ),
        )
        Button(
            onClick = onAction,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = UiSpacing.PageHorizontal, vertical = UiSpacing.PageVertical)
                .fillMaxWidth(),
        ) {
            Text(text = actionText)
        }
    }
}

@Composable
private fun CompareRowDetail(row: CompareRow) {
    val local = row.localVersion
    val remote = row.remoteVersion
    if (row.presence) {
        val version = if (row.presenceOnRemote) remote else local
        Text(
            text = if (version.isNullOrBlank()) {
                AppRuntime.stringResource(
                    if (row.presenceOnRemote) R.string.app_compare_only_adb_device_plain
                    else R.string.app_compare_only_this_device_plain,
                )
            } else {
                AppRuntime.stringResource(
                    if (row.presenceOnRemote) R.string.app_compare_only_adb_device
                    else R.string.app_compare_only_this_device,
                    version,
                )
            },
            style = textStyles.footnote2,
            color = colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val size = if (row.presenceOnRemote) row.remoteSizeBytes else row.localSizeBytes
        if (size > 0) {
            Text(
                text = stringResource(R.string.app_compare_size_prefix) + SizeFormat.humanReadable(size),
                style = textStyles.footnote2,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    if (local != null && remote != null) {
        // 较新的一侧就是本次要装/要导出的那一侧
        val localWins = compareVersionNames(local, remote) > 0
        val olderVersion = if (localWins) remote else local
        val newerVersion = if (localWins) local else remote
        val targetSize = if (localWins) row.localSizeBytes else row.remoteSizeBytes
        // 旧版本在前、新版本在后：一次升级读一行
        val versionLine = buildAnnotatedString {
            withStyle(SpanStyle(color = colorScheme.onSurfaceVariantSummary)) {
                append(stringResource(R.string.app_compare_version_prefix))
                append(olderVersion)
                append(" → ")
            }
            // 新旧同字阶，新版本只靠主色区分
            withStyle(SpanStyle(color = colorScheme.primary)) {
                append(newerVersion)
            }
        }
        Text(
            text = versionLine,
            style = textStyles.footnote2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (targetSize > 0) {
            Text(
                text = stringResource(R.string.app_compare_size_prefix) +
                    SizeFormat.humanReadable(targetSize),
                style = textStyles.footnote2,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    // 有一侧没报版本：写能确定的那半句，两侧都缺时明说不知道
    val knownVersion = local ?: remote
    Text(
        text = if (knownVersion != null) {
            stringResource(R.string.app_compare_version_prefix) + knownVersion
        } else {
            stringResource(R.string.app_compare_version_unknown)
        },
        style = textStyles.footnote2,
        color = colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val knownSize = if (row.sizeOnRemote) row.remoteSizeBytes else row.localSizeBytes
    if (knownSize > 0) {
        Text(
            text = stringResource(R.string.app_compare_size_prefix) + SizeFormat.humanReadable(knownSize),
            style = textStyles.footnote2,
            color = colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun installSummary(outcome: DeviceAppRepository.InstallOutcome): String = when {
    outcome.failureCount == 0 ->
        AppRuntime.stringResource(R.string.app_manager_install_success, outcome.successCount)

    outcome.successCount == 0 ->
        AppRuntime.stringResource(R.string.app_manager_install_failed, installErrorText(outcome.lastError))

    else ->
        AppRuntime.stringResource(
            R.string.app_manager_install_partial,
            outcome.successCount,
            outcome.failureCount,
            installErrorText(outcome.lastError),
        )
}

/** 单包导出的收尾：成功报落盘目录，失败报设备给的原因。 */
private fun singleExportSummary(
    app: DeviceApp,
    result: DeviceAppRepository.ExportResult,
): String = when (result) {
    is DeviceAppRepository.ExportResult.Success ->
        AppRuntime.stringResource(R.string.app_manager_exported_to_downloads, app.displayLabel)

    is DeviceAppRepository.ExportResult.Failure ->
        appActionFailureText(AppAction.EXPORT_APK, app, result.error)
}

private fun installErrorText(raw: String?): String =
    installFailureReason(raw)
        ?: raw?.trim()?.takeIf { it.isNotEmpty() }
        ?: AppRuntime.stringResource(R.string.app_compare_install_failed_unknown)

private fun exportErrorText(raw: String?): String = when (raw) {
    DeviceAppRepository.EXPORT_NO_TARGET ->
        AppRuntime.stringResource(R.string.app_manager_export_no_target)

    DeviceAppRepository.EXPORT_NOT_PUBLISHED ->
        AppRuntime.stringResource(R.string.app_manager_export_not_published)

    else -> AppRuntime.stringResource(R.string.app_compare_export_failed_unknown)
}

private fun exportSummary(outcome: DeviceAppRepository.ExportOutcome): String = when {
    outcome.failureCount == 0 && outcome.archived ->
        AppRuntime.stringResource(R.string.app_compare_export_done, outcome.successCount)

    outcome.successCount == 0 || !outcome.archived ->
        AppRuntime.stringResource(R.string.app_compare_export_failed, exportErrorText(outcome.lastError))

    else ->
        AppRuntime.stringResource(
            R.string.app_compare_export_partial,
            outcome.successCount,
            outcome.failureCount,
            exportErrorText(outcome.lastError),
        )
}