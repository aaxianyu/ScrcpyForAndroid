package io.github.miuzarte.scrcpyforandroid.pages

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.data.AppManagerError
import io.github.miuzarte.scrcpyforandroid.appmanager.data.DeviceAppRepository
import io.github.miuzarte.scrcpyforandroid.appmanager.data.LocalApkInstall
import io.github.miuzarte.scrcpyforandroid.appmanager.data.PackageDetailClient
import io.github.miuzarte.scrcpyforandroid.appmanager.data.PackageDetailResult
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.appManagerErrorText
import io.github.miuzarte.scrcpyforandroid.appmanager.data.exportFileName
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.PackageDetail
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.SizeFormat
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppConfirmDialog
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppManagerMotion
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.MaskedTopBarAction
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.TransferProgressDialog
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.rememberAnimationsDisabled
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.rememberGlassHighlight
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.scaledBy
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalBlurMode
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.SkeletonBar
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.ui.rememberPageScrollBehavior
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 应用详情：只读的单包信息页。
 *
 * 数据分两路：能直接从列表缓存拿到的不等探测（图标、名称、包名、安装包大小、启用状态、
 * 运行状态），要读 `dumpsys package` 的版本号 / UID / ABI / SDK / 时间戳 / 路径才等这一次
 * 读取，等的时候对应位置铺骨架块。
 *
 * 导出复用应用管理页那条链路（系统 Download 目录直写或系统保存框），收尾同样给产物配
 * 动作按钮：单个 APK 交给系统安装器，`.apks` 与 zip 交给「打开方式」。
 */
@Composable
internal fun AppDetailScreen(packageName: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val navigator = LocalRootNavigator.current
    val scrollBehavior = rememberPageScrollBehavior()
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    val pageScroll = rememberScrollState()
    val density = LocalDensity.current
    // 顶栏材质与列表页同一套：顶栏按钮的圆形玻璃遮罩只在渐进模糊下随滚动淡入
    val asBundle by appSettings.bundleState.collectAsState()
    val glassSwitch = asBundle.floatingBottomBarBlur
    val blurMode = LocalBlurMode.current
    val topBarGlass = blurBackdrop != null && blurMode == AppSettings.BlurMode.PROGRESSIVE && glassSwitch
    val maskEnabled = blurBackdrop != null && blurMode == AppSettings.BlurMode.PROGRESSIVE
    val maskAlpha = remember { Animatable(0f) }
    val topBarHighlight = rememberGlassHighlight()
    val animationsDisabled = rememberAnimationsDisabled()
    LaunchedEffect(maskEnabled, pageScroll) {
        if (!maskEnabled) {
            maskAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        snapshotFlow { with(density) { (pageScroll.value / 48.dp.toPx()).coerceIn(0f, 1f) } }
            .collectLatest { target ->
                maskAlpha.animateTo(
                    targetValue = target,
                    animationSpec = tween(
                        AppManagerMotion.MASK_FADE_MS.scaledBy(animationsDisabled),
                        easing = LinearEasing,
                    ),
                )
            }
    }
    // 折叠态标题：头部图标+名称块滚出后才出现。顶栏高度固定不塌陷——miuix 大标题只收一行
    // 字符串，撑不起图标+名称+包名这块；出现点取头部块滚掉七成，滚动方向的反向过程同样淡出
    var headerHeightPx by remember { mutableStateOf(0) }
    // 头部随滚动收起：淡出并轻微缩小，与关于页标题区的收起方式一致
    val headerCollapse by remember {
        derivedStateOf {
            if (headerHeightPx <= 0) 0f
            else (pageScroll.value.toFloat() / (headerHeightPx * 0.7f)).coerceIn(0f, 1f)
        }
    }
    val titleVisible by remember {
        derivedStateOf { headerHeightPx > 0 && pageScroll.value >= headerHeightPx * 0.7f }
    }

    val snackHost = remember { SnackbarHostState() }
    DisposableEffect(snackHost) {
        val unregister = AppRuntime.registerSnackbarHostState(snackHost)
        onDispose(unregister)
    }

    val apps by DeviceAppRepository.apps.collectAsState()
    // 运行态与图标版本都从这里读：详情页不进列表页的 ViewModel，但这两样在仓库的同一份状态里
    val listState by DeviceAppRepository.uiState.collectAsState()
    val transfer by DeviceAppRepository.transfer.collectAsState()
    val app = apps.firstOrNull { it.packageName == packageName }

    var state by remember(packageName) { mutableStateOf<DetailState>(DetailState.Loading) }
    // 进入页面读一次：校准启用/运行态，再读单包安装信息。重新进入会再读一次，所以失败态不需要重试按钮
    LaunchedEffect(packageName) {
        state = DetailState.Loading
        DeviceAppRepository.refreshAppState(packageName)
        state = when (val result = PackageDetailClient.load(packageName)) {
            is PackageDetailResult.Success -> DetailState.Ready(result.detail)
            PackageDetailResult.NotFound -> DetailState.NotFound
            PackageDetailResult.Unrecognized -> DetailState.Unrecognized
            is PackageDetailResult.Failure -> DetailState.Failed(result.error)
        }
    }

    var confirmVisible by remember { mutableStateOf(false) }
    var exportPrompt by remember { mutableStateOf<DetailExportPrompt?>(null) }

    fun copyToClipboard(text: String, messageResId: Int) {
        LocalInputService.setClipboardText(context, text)
        AppRuntime.snackbar(snackHost, messageResId)
    }

    /** 导出产物的查看意图；条目属于本应用，交给对方之前先把读权限让出来。 */
    fun exportViewIntent(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            // clipData 让 chooser 把读权限一并转给用户选中的应用
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** 按 [kind] 的 MIME 向系统拉起「打开方式」；`.apks` 起不来时退回通配 MIME。 */
    fun openExportTarget(uri: Uri, kind: DeviceAppRepository.ExportKind): Boolean {
        fun open(mimeType: String): Boolean = runCatching {
            val intent = exportViewIntent(uri, mimeType)
            // 先看内层 intent 的候选，才判得出没有应用能接这份产物
            val candidates = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            if (candidates.isEmpty()) return@runCatching false
            // 系统可能已给这个 MIME 设了默认应用：套 chooser 才会把候选摆给用户
            val chooser = Intent.createChooser(intent, null)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

    // 导出后点「安装」的重试状态：缺「安装未知应用」权限时先拉系统设置，回页后续装一次。
    // 续装请求只在本进程内有效，进程重建后不替用户续一个陈旧请求，重新导出即可。
    var pendingInstallUri by remember { mutableStateOf<Uri?>(null) }
    var pendingInstallContent by remember { mutableStateOf<Uri?>(null) }
    // 复制期间再点一次「安装」会叠出第二次复制与第二个安装器，用这个短命标记挡住。
    var installInFlight by remember { mutableStateOf(false) }

    /** 清掉待续装的请求：成功、拒绝或失败之后都不再重试。 */
    fun clearPendingInstall() {
        pendingInstallUri = null
        pendingInstallContent = null
    }

    /** 暂存件已经在缓存里：只拉起安装器，不再复制一遍整包。 */
    suspend fun launchStagedInstall(content: Uri) {
        when (val outcome = LocalApkInstall.launch(context, content)) {
            LocalApkInstall.Outcome.Started -> clearPendingInstall()

            is LocalApkInstall.Outcome.Failed -> {
                android.util.Log.w("AppDetailScreen", "install exported apk failed: ${outcome.reason}")
                clearPendingInstall()
                AppRuntime.snackbar(snackHost, R.string.app_manager_export_install_failed)
            }

            else -> Unit
        }
    }

    fun installExportedApk(uri: Uri, reopenSettings: Boolean = true) {
        if (installInFlight) {
            // 动作按钮被点掉后，反馈只剩一句「正在准备」：告诉用户上一次复制还没结束
            AppRuntime.snackbar(snackHost, R.string.app_manager_export_install_preparing)
            return
        }
        installInFlight = true
        if (reopenSettings) AppRuntime.snackbar(snackHost, R.string.app_manager_export_install_preparing)
        scope.launch {
            var resumeRetry = false
            try {
                when (val outcome = LocalApkInstall.install(
                    context = context,
                    source = uri,
                    isForeground = {
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    },
                )) {
                    LocalApkInstall.Outcome.Started -> clearPendingInstall()

                    is LocalApkInstall.Outcome.Deferred -> {
                        // 复制结束时页面已在后台：系统会丢弃这次启动，留到回前台再拉起
                        pendingInstallUri = uri
                        pendingInstallContent = outcome.contentUri
                        resumeRetry = lifecycleOwner.lifecycle.currentState
                            .isAtLeast(Lifecycle.State.RESUMED)
                    }

                    LocalApkInstall.Outcome.NeedsPermission -> {
                        // 从设置页回来仍没授权：丢掉这次请求，别再把它顶回设置页
                        if (!reopenSettings) {
                            clearPendingInstall()
                            AppRuntime.snackbar(snackHost, R.string.app_manager_export_install_denied)
                            return@launch
                        }
                        pendingInstallUri = uri
                        pendingInstallContent = null
                        val opened = runCatching {
                            context.startActivity(LocalApkInstall.permissionSettingsIntent(context))
                        }.isSuccess
                        if (!opened) {
                            clearPendingInstall()
                            AppRuntime.snackbar(snackHost, R.string.app_manager_export_install_failed)
                        }
                    }

                    is LocalApkInstall.Outcome.Failed -> {
                        clearPendingInstall()
                        AppRuntime.snackbar(snackHost, R.string.app_manager_export_install_failed)
                    }
                }
            } finally {
                installInFlight = false
            }
            // 复制还没结束用户就回来了：ON_RESUME 会被单飞标记挡住，这里补一次拉起
            if (resumeRetry) pendingInstallContent?.let { launchStagedInstall(it) }
        }
    }

    /** 回页后继续待续装的请求：有暂存件就只拉起安装器，只有源 uri 才重新复制。 */
    fun continuePendingInstall() {
        val content = pendingInstallContent
        if (content != null) {
            if (installInFlight) return
            installInFlight = true
            scope.launch {
                try {
                    launchStagedInstall(content)
                } finally {
                    installInFlight = false
                }
            }
            return
        }
        pendingInstallUri?.let { installExportedApk(it, reopenSettings = false) }
    }

    // 从「安装未知应用」设置页回来：权限已给就接着装；没给则作废这次请求，
    // 免得多小时后用户从别处给了权限，一回到本页就被一个陈旧的 uri 顶进安装器
    val continueInstallLatest by rememberUpdatedState<() -> Unit>({ continuePendingInstall() })
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (pendingInstallUri == null && pendingInstallContent == null) {
                return@LifecycleEventObserver
            }
            if (LocalApkInstall.needsPermission(context)) {
                clearPendingInstall()
                AppRuntime.snackbar(snackHost, R.string.app_manager_export_install_denied)
            } else {
                continueInstallLatest()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /**
     * 导出成功后的提示：产物能装能开时给它配一个动作按钮。
     *
     * 单个 APK 直接交给系统安装器（缺「安装未知应用」权限时先申请）；`.apks` 与 zip 交给
     * 系统弹「打开方式」，让用户挑能打开它的应用。
     */
    fun showExportDone(message: String, uri: Uri?, kind: DeviceAppRepository.ExportKind) {
        val apk = uri?.takeIf { kind == DeviceAppRepository.ExportKind.APK }
        if (apk != null) {
            AppRuntime.snackbar(snackHost, 
                message = message,
                actionLabel = AppRuntime.stringResource(R.string.app_manager_snackbar_install),
                onResult = { result ->
                    if (result == SnackbarResult.ActionPerformed) installExportedApk(apk)
                },
            )
            return
        }
        val target = uri?.takeIf { kind != DeviceAppRepository.ExportKind.APK }
        if (target == null) {
            AppRuntime.snackbar(snackHost, message)
            return
        }
        AppRuntime.snackbar(snackHost, 
            message = message,
            actionLabel = AppRuntime.stringResource(R.string.app_manager_snackbar_open),
            onResult = { result ->
                if (result == SnackbarResult.ActionPerformed && !openExportTarget(target, kind)) {
                    AppRuntime.snackbar(snackHost, R.string.app_manager_export_open_failed)
                }
            },
        )
    }

    /** 单包导出的收尾：成功给动作按钮，失败只报原因。 */
    fun reportExport(message: String, result: DeviceAppRepository.ExportResult) {
        when (result) {
            is DeviceAppRepository.ExportResult.Success ->
                showExportDone(message, result.uri, result.kind)

            is DeviceAppRepository.ExportResult.Failure -> AppRuntime.snackbar(snackHost, 
                AppRuntime.stringResource(R.string.app_manager_export_failed, detailErrorText(result.error)),
            )
        }
    }

    /** 保存框选定的 uri 对应的流；打不开时返回 null。 */
    fun openExportStream(uri: Uri?): java.io.OutputStream? =
        uri?.let { runCatching { context.contentResolver.openOutputStream(it) }.getOrNull() }

    /** 保存框返回时条目已经建好；没能落盘就删掉它，别在用户选的目录里留个空壳。 */
    fun discardCreatedDocument(uri: Uri?) {
        uri ?: return
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    fun writePendingExport(uri: Uri?) {
        val prompt = exportPrompt as? DetailExportPrompt.AwaitingLocation
        exportPrompt = null
        if (uri == null) return
        if (prompt == null) {
            discardCreatedDocument(uri)
            AppRuntime.snackbar(snackHost, R.string.app_manager_export_lost)
            return
        }
        val stream = openExportStream(uri)
        if (stream == null) {
            discardCreatedDocument(uri)
            AppRuntime.snackbar(snackHost, 
                AppRuntime.stringResource(
                    R.string.app_manager_export_failed,
                    AppRuntime.stringResource(R.string.app_manager_export_open_failed),
                ),
            )
            return
        }
        DeviceAppRepository.exportApk(prompt.app, stream, uri, expectedKind = prompt.kind) { result ->
            reportExport(AppRuntime.stringResource(R.string.app_manager_exported, prompt.app.displayLabel), result)
        }
    }

    // 保存框的 MIME 决定系统是否给名字补后缀，所以两个形态各一个 launcher
    val exportCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DeviceAppRepository.ExportKind.APK.mimeType),
    ) { uri: Uri? -> writePendingExport(uri) }
    val apksExportCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DeviceAppRepository.ExportKind.APK_SET.mimeType),
    ) { uri: Uri? -> writePendingExport(uri) }

    fun launchExportPicker(target: DeviceApp, kind: DeviceAppRepository.ExportKind) {
        val name = exportFileName(target.displayLabel, target.packageName, kind.extension)
        if (kind == DeviceAppRepository.ExportKind.APK_SET) {
            apksExportCreator.launch(name)
        } else {
            exportCreator.launch(name)
        }
    }

    /** 单包导出：能写 Download/Scrcpy 目录就直接写，否则先问设备要形态，再弹保存框。 */
    fun startExport(target: DeviceApp) {
        if (DeviceAppRepository.canPublishToDownloads()) {
            DeviceAppRepository.exportApk(target) { result ->
                reportExport(
                    AppRuntime.stringResource(R.string.app_manager_exported_to_downloads, target.displayLabel),
                    result,
                )
            }
        } else {
            exportPrompt = when (exportPrompt) {
                null -> {
                    AppRuntime.snackbar(snackHost, R.string.app_manager_export_kind_querying)
                    DetailExportPrompt.Resolving(target)
                }

                is DetailExportPrompt.Resolving -> {
                    AppRuntime.snackbar(snackHost, R.string.app_manager_export_kind_querying)
                    exportPrompt
                }

                is DetailExportPrompt.AwaitingLocation -> {
                    AppRuntime.snackbar(snackHost, R.string.app_manager_export_prompt_busy)
                    exportPrompt
                }
            }
        }
    }

    // 形态查询与弹框都在组合里推进：页面离场时这两个 effect 一并取消，不会在 launcher 注销之后再启动它
    LaunchedEffect(exportPrompt) {
        val resolving = exportPrompt as? DetailExportPrompt.Resolving ?: return@LaunchedEffect
        val result = DeviceAppRepository.resolveExportKind(resolving.app.packageName)
        if (exportPrompt !== resolving) return@LaunchedEffect
        exportPrompt = when (result) {
            is DeviceAppRepository.ExportKindResult.Ready ->
                DetailExportPrompt.AwaitingLocation(resolving.app, result.kind)

            is DeviceAppRepository.ExportKindResult.TimedOut -> {
                AppRuntime.snackbar(snackHost, 
                    AppRuntime.stringResource(
                        R.string.app_manager_export_failed,
                        AppRuntime.stringResource(R.string.app_manager_export_kind_timeout),
                    ),
                )
                null
            }

            is DeviceAppRepository.ExportKindResult.Unavailable -> {
                AppRuntime.snackbar(snackHost, 
                    AppRuntime.stringResource(R.string.app_manager_export_failed, detailErrorText(result.reason)),
                )
                null
            }
        }
    }

    LaunchedEffect(exportPrompt) {
        val ready = exportPrompt as? DetailExportPrompt.AwaitingLocation ?: return@LaunchedEffect
        launchExportPicker(ready.app, ready.kind)
    }

    val label = app?.displayLabel ?: packageName
    val icon = remember(packageName, listState.iconEpoch) { DeviceAppRepository.icon(packageName) }
    val running = listState.runningPackages?.contains(packageName)
    val enabled = app?.isEnabled
    val isSystem = (state as? DetailState.Ready)?.detail?.isSystem ?: app?.isSystem
    val loading = state is DetailState.Loading

    val splitBadge = stringResource(R.string.app_manager_split_badge)
    val sizeText = app?.takeIf { it.apkSizeBytes > 0 }?.let { cached ->
        val human = SizeFormat.humanReadable(cached.apkSizeBytes)
        if (cached.isSplit) {
            stringResource(R.string.app_manager_detail_size_split, human, splitBadge)
        } else {
            human
        }
    }

    // 安装来源本身只是个包名：应用列表缓存里就有它的显示名，直接拼出来，不再为一行字查一次设备。
    // 设备没记住安装器时，系统应用只可能是出厂预装，其余来源确实是未知
    val readyDetail = (state as? DetailState.Ready)?.detail

    // 版本名与版本号合成一格：'15.9 (30229)'。详情还没回来时先用列表缓存里的版本名占位
    val versionText = run {
        val name = readyDetail?.versionName ?: app?.versionName?.takeIf { it.isNotBlank() }
        val code = readyDetail?.versionCode
        when {
            name != null && code != null -> stringResource(R.string.app_manager_detail_version_combined, name, code)
            name != null -> name
            code != null -> code
            else -> null
        }
    }
    val runningText = running?.let {
        stringResource(
            if (it) R.string.app_manager_detail_running_yes
            else R.string.app_manager_detail_running_no,
        )
    }
    val disabledText = enabled?.let {
        stringResource(
            if (it) R.string.app_manager_detail_disabled_no
            else R.string.app_manager_detail_disabled_yes,
        )
    }

    val installerSource = readyDetail?.let { it.installerPackageName ?: it.initiatingPackageName }
    // 出厂预装的系统应用没有安装器也没有发起安装的包名，来源与安装方式会是同一句「系统预装」，
    // 这一行留空不显示
    val installerRowVisible = loading || readyDetail == null || readyDetail.isSystem != true || installerSource != null
    val installerText = installerSource?.let { source ->
        val appLabel = listState.apps.firstOrNull { it.packageName == source }?.displayLabel
        if (appLabel.isNullOrBlank() || appLabel == source) source else "$appLabel\n$source"
    }

    // 只有设备明确表态的两种方式才显示：安装器是 shell 即 ADB 推装，系统应用没有安装器即出厂预装。
    // 其余安装器（InstallerX 之类）装了什么应用都记在来源里，方式写出来只会误导
    val installMethodText = readyDetail?.let { ready ->
        val installer = ready.installerPackageName
        val initiating = ready.initiatingPackageName
        when {
            installer == SHELL_PACKAGE || (installer == null && initiating == SHELL_PACKAGE) ->
                stringResource(R.string.app_manager_detail_install_method_adb)

            installer == null && ready.isSystem == true ->
                stringResource(R.string.app_manager_detail_install_method_preinstalled)

            else -> null
        }
    }

    val signingText = readyDetail?.apkSigningVersion?.let {
        stringResource(R.string.app_manager_detail_signing_value, it)
    }

    val permissionsText = readyDetail?.let {
        stringResource(
            R.string.app_manager_detail_permissions_value,
            it.requestedPermissionCount,
            it.runtimePermissionCount,
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(
                snackHost,
                modifier = Modifier.onGloballyPositioned {
                    AppRuntime.markSnackbarHostStateLaidOut(snackHost)
                },
            )
        },
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                Box {
                    SmallTopAppBar(
                        // 标题由下面的折叠态层画：字号与列表页折叠态标题一致，固定居中
                        title = "",
                        scrollBehavior = scrollBehavior,
                        color = if (blurActive) Color.Transparent else colorScheme.surface,
                        defaultWindowInsetsPadding = false,
                        navigationIcon = {
                            MaskedTopBarAction(
                                icon = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.cd_back),
                                onClick = {
                                    haptic.contextClick()
                                    navigator.pop()
                                },
                                maskAlpha = { maskAlpha.value },
                                glassActive = topBarGlass,
                                backdrop = blurBackdrop,
                                highlight = topBarHighlight,
                            )
                        },
                        actions = {
                            MaskedTopBarAction(
                                icon = MiuixIcons.Download,
                                contentDescription = stringResource(R.string.app_manager_export_apk),
                                onClick = {
                                    haptic.contextClick()
                                    confirmVisible = true
                                },
                                maskAlpha = { maskAlpha.value },
                                glassActive = topBarGlass,
                                backdrop = blurBackdrop,
                                highlight = topBarHighlight,
                                // 等待形态查询或保存框的这段时间里再点没有新目标，只会把前一次导出丢掉
                                enabled = app != null && !transfer.active && exportPrompt == null,
                            )
                        },
                    )
                    DetailCollapsedTitle(title = label, visible = titleVisible)
                }
            }
        },
    ) { pagePadding ->
        Box(
            modifier = if (blurActive) Modifier.layerBackdrop(blurBackdrop) else Modifier,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(pageScroll)
                    .padding(
                        start = UiSpacing.PageHorizontal,
                        end = UiSpacing.PageHorizontal,
                        top = pagePadding.calculateTopPadding() + UiSpacing.MediumLarge,
                        bottom = pagePadding.calculateBottomPadding() + UiSpacing.PageBottom,
                    ),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.MediumLarge),
            ) {
                DetailHeader(
                    modifier = Modifier.onGloballyPositioned { headerHeightPx = it.size.height },
                    label = label,
                    packageName = app?.packageName ?: packageName,
                    icon = icon,
                    isSystem = isSystem ?: false,
                    collapseProgress = headerCollapse,
                    onCopyLabel = { copyToClipboard(label, R.string.app_manager_detail_copied_label) },
                    onCopyPackage = { copyToClipboard(app?.packageName ?: packageName, R.string.app_manager_detail_copied_package) },
                )

                when (val current = state) {
                    DetailState.NotFound -> DetailMessageCard(
                        message = stringResource(R.string.app_manager_detail_not_found),
                    )

                    DetailState.Unrecognized -> DetailMessageCard(
                        message = stringResource(R.string.app_manager_detail_unrecognized),
                    )

                    is DetailState.Failed -> DetailMessageCard(
                        message = stringResource(
                            R.string.app_manager_detail_load_failed,
                            detailErrorText(current.error),
                        ),
                    )

                    DetailState.Loading, is DetailState.Ready -> {
                        val detail = (current as? DetailState.Ready)?.detail

                        DetailSpecGrid(
                            sizeText = sizeText,
                            versionText = versionText,
                            runningText = runningText,
                            disabledText = disabledText,
                            versionSkeleton = loading && versionText == null,
                        )

                        DetailCard {
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_uid),
                                value = detail?.uid,
                                loading = loading,
                            )
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_system_app),
                                value = isSystem?.let {
                                    stringResource(
                                        if (it) R.string.app_manager_detail_yes else R.string.app_manager_detail_no,
                                    )
                                },
                                loading = false,
                            )
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_min_sdk),
                                value = detail?.minSdk,
                                loading = loading,
                            )
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_target_sdk),
                                value = detail?.targetSdk,
                                loading = loading,
                            )
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_abi),
                                value = detail?.primaryCpuAbi,
                                loading = loading,
                            )
                        }

                        DetailCard {
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_first_install),
                                value = detail?.firstInstallTime,
                                loading = loading,
                            )
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_last_update),
                                value = detail?.lastUpdateTime,
                                loading = loading,
                            )
                            if (loading || installMethodText != null) {
                                DetailRow(
                                    label = stringResource(R.string.app_manager_detail_install_method),
                                    value = installMethodText,
                                    loading = loading,
                                )
                            }
                            if (installerRowVisible) {
                                DetailRow(
                                    label = stringResource(R.string.app_manager_detail_installer),
                                    value = installerText,
                                    loading = loading,
                                    valueMaxLines = 2,
                                )
                            }
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_signing_scheme),
                                value = signingText,
                                loading = loading,
                            )
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_permissions),
                                value = permissionsText,
                                loading = loading,
                            )
                        }

                        DetailCard {
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_code_path),
                                value = detail?.codePath,
                                loading = loading,
                                copyable = true,
                                compact = true,
                                onCopy = { detail?.codePath?.let { copyToClipboard(it, R.string.app_manager_detail_copied_code_path) } },
                            )
                            DetailRow(
                                label = stringResource(R.string.app_manager_detail_data_dir),
                                value = detail?.dataDir,
                                loading = loading,
                                copyable = true,
                                compact = true,
                                onCopy = { detail?.dataDir?.let { copyToClipboard(it, R.string.app_manager_detail_copied_data_dir) } },
                            )
                        }
                    }
                }
            }

            AppConfirmDialog(
                visible = confirmVisible,
                confirmLabel = stringResource(R.string.app_manager_action_export),
                title = app?.let {
                    stringResource(R.string.app_manager_confirm_title_export_single, it.displayLabel)
                } ?: "",
                summary = app?.let {
                    val base = stringResource(
                        if (DeviceAppRepository.canPublishToDownloads()) {
                            R.string.app_manager_confirm_summary_export_single_downloads
                        } else {
                            R.string.app_manager_confirm_summary_export_single
                        },
                    )
                    if (it.isSplit) {
                        base + "\n" + stringResource(R.string.app_manager_confirm_summary_export_single_split)
                    } else {
                        base
                    }
                } ?: "",
                onConfirm = {
                    confirmVisible = false
                    app?.let { startExport(it) }
                },
                onDismissRequest = { confirmVisible = false },
            )

            TransferProgressDialog(
                progress = transfer,
                exporting = transfer.exporting,
                onAbortConfirm = {
                    DeviceAppRepository.cancelTransfer()
                    AppRuntime.snackbar(snackHost, R.string.app_manager_transfer_aborted)
                },
            )
        }
    }
}

/** 页面的一次读取状态；Ready 之外都只显示一句原因，不提供重试按钮（重新进入页面会再读）。 */
private sealed interface DetailState {
    data object Loading : DetailState

    data class Ready(val detail: PackageDetail) : DetailState

    data object NotFound : DetailState

    data object Unrecognized : DetailState

    data class Failed(val error: AppManagerError) : DetailState
}

/** 导出等待中的两个阶段：问设备要形态 → 弹系统保存框。 */
private sealed interface DetailExportPrompt {
    data class Resolving(val app: DeviceApp) : DetailExportPrompt

    data class AwaitingLocation(
        val app: DeviceApp,
        val kind: DeviceAppRepository.ExportKind,
    ) : DetailExportPrompt
}

private fun detailErrorText(error: AppManagerError): String = when (error) {
    // 详情页的超时来自单包查询，用这个页面的等待时长文案
    AppManagerError.Slow ->
        AppRuntime.stringResource(R.string.app_manager_detail_timeout, PackageDetailClient.QUERY_TIMEOUT_MS / 1000)

    else -> appManagerErrorText(error)
}


/**
 * 折叠态标题左右内缩 = 顶栏水平 padding 16dp + 返回钮 40dp，让开两侧按钮。
 * 左右内缩等宽是标题落在屏幕中线的前提，改了这里就不再居中。
 */
private val CollapsedTitleStart = 56.dp

private const val SHELL_PACKAGE = "com.android.shell"

/**
 * 折叠态标题：字号与列表页折叠态标题一致（title4 / 中等字重）。左右内缩等宽，标题固定在顶栏中部；
 * 详情类顶栏只有返回钮与少量动作钮，不需要像列表页那样按形态给动作区让位。长标题在中间省略。
 */
@Composable
internal fun BoxScope.DetailCollapsedTitle(title: String, visible: Boolean) {
    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    val translationY = remember { Animatable(if (visible) 0f else 20f) }
    LaunchedEffect(visible) {
        if (visible) {
            val showSpec = folmeSpring<Float>(damping = 1.0f, response = 0.3f)
            launch { alpha.animateTo(1f, showSpec) }
            launch { translationY.animateTo(0f, showSpec) }
        } else {
            val hideSpec = folmeSpring<Float>(damping = 1.0f, response = 0.15f)
            launch { alpha.animateTo(0f, hideSpec) }
            launch { translationY.animateTo(20f, hideSpec) }
        }
    }
    // 与 SmallTopAppBar 同一套安全区叠加：它无论 defaultWindowInsetsPadding 与否都会自带
    // systemBars 顶部 padding，覆盖层不叠加同样的 inset 就会在更高的盒子里居中，标题比图标偏高。
    val barModifier = Modifier
        .align(Alignment.TopStart)
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        .height(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val titleModifier = Modifier.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
    }
    Box(
        modifier = barModifier.padding(horizontal = CollapsedTitleStart),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.title4.fontSize,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = titleModifier,
        )
    }
}

/** 头部：squircle 图标 + 应用名 + 包名；名称与包名各自可单击复制。 */
@Composable
private fun DetailHeader(
    modifier: Modifier = Modifier,
    label: String,
    packageName: String,
    icon: ImageBitmap?,
    isSystem: Boolean,
    collapseProgress: Float,
    onCopyLabel: () -> Unit,
    onCopyPackage: () -> Unit,
) {
    // 头部按应用商店详情页的比例：大图标 + 名字 + 13sp 包名，图标与整列文字垂直居中。
    // 名字宽到要折行时用正文字号（17sp），一行放得下就用标题级字号；判定只向「折行」单向收敛，
    // 否则字号变大导致折行、退回小字号又不折行，会来回跳。
    var wrappedName by remember(label) { mutableStateOf(false) }
    val nameFontSize = if (wrappedName) {
        MiuixTheme.textStyles.headline1.fontSize
    } else {
        MiuixTheme.textStyles.title3.fontSize
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 1f - collapseProgress
                scaleX = 1f - collapseProgress * 0.05f
                scaleY = 1f - collapseProgress * 0.05f
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(DetailIconSize)
                .squircleClip(cornerRadius = DetailIconCornerRadius),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = stringResource(R.string.app_manager_cd_app_icon),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                // 取不到图标才垫底：系统应用=机器人、用户应用=商店，与列表行同一套占位
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .squircleSurface(color = colorScheme.secondaryVariant, cornerRadius = DetailIconCornerRadius),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isSystem) Icons.Rounded.Android else MiuixIcons.Store,
                        contentDescription = null,
                        modifier = Modifier.size(DetailIconSize / 2),
                        tint = colorScheme.onSecondaryVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(UiSpacing.Large + UiSpacing.Small))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.Tiny),
        ) {
            Text(
                text = label,
                fontSize = nameFontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!wrappedName && it.lineCount > 1) wrappedName = true },
                modifier = Modifier.copyableText(onCopyLabel),
            )
            Text(
                text = packageName,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.copyableText(onCopyPackage),
            )
        }
    }
}

/** 2×2 规格网格：标签在上、值在下。 */
@Composable
private fun DetailSpecGrid(
    sizeText: String?,
    versionText: String?,
    runningText: String?,
    disabledText: String?,
    versionSkeleton: Boolean,
) {
    DetailCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            GridCell(
                label = stringResource(R.string.app_manager_detail_apk_size),
                value = sizeText,
                loading = false,
                modifier = Modifier.weight(1f),
            )
            GridCell(
                label = stringResource(R.string.app_manager_detail_version),
                value = versionText,
                loading = versionSkeleton,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            GridCell(
                label = stringResource(R.string.app_manager_detail_running),
                value = runningText,
                loading = false,
                modifier = Modifier.weight(1f),
            )
            GridCell(
                label = stringResource(R.string.app_manager_detail_disabled),
                value = disabledText,
                loading = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GridCell(
    label: String,
    value: String?,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = UiSpacing.Small)) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
        )
        Spacer(Modifier.height(UiSpacing.Small))
        if (loading) {
            SkeletonBar(width = 72.dp)
        } else {
            Text(
                text = value ?: stringResource(R.string.app_manager_detail_unknown),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = UiSpacing.Large, vertical = UiSpacing.ContentVertical),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceContainer),
    ) {
        content()
    }
}

/**
 * 一行「标签 / 值」。
 *
 * [compact] 用于路径这类长值：字号缩小、最多两行、行尾留复制入口；其余行值显示成单行省略。
 * [copyable] 为真时整行可单击，复制动作由 [onCopy] 给——值为空时不挂点击（复制「未知」没有意义）。
 */
@Composable
private fun DetailRow(
    label: String,
    value: String?,
    loading: Boolean,
    copyable: Boolean = false,
    compact: Boolean = false,
    valueMaxLines: Int = 0,
    onCopy: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickable = copyable && !loading && value != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                if (clickable) {
                    Modifier
                        .pressable(interactionSource = interactionSource, indication = SinkFeedback())
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onCopy,
                        )
                } else {
                    Modifier
                },
            )
            .padding(vertical = UiSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = UiSpacing.MediumLarge),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                SkeletonBar(width = if (compact) 64.dp else 88.dp)
            } else {
                Text(
                    text = value ?: stringResource(R.string.app_manager_detail_unknown),
                    fontSize = if (compact) 11.sp else 14.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.End,
                    maxLines = if (valueMaxLines > 0) valueMaxLines else if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (clickable) {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/** 读取失败 / 包不在了：只给一句原因，卡片占住规格卡的位置。 */
@Composable
private fun DetailMessageCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = UiSpacing.Large, vertical = UiSpacing.ContentVertical),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceContainer),
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(vertical = UiSpacing.Small),
        )
    }
}

/** 可复制文本的统一交互：MIUIx 按压反馈 + 单击。 */
@Composable
private fun Modifier.copyableText(onCopy: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressable(interactionSource = interactionSource, indication = SinkFeedback())
        .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onCopy)
}

private val DetailIconSize = 72.dp
private val DetailIconCornerRadius = 16.dp