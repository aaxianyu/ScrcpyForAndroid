package io.github.miuzarte.scrcpyforandroid.pages

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.basic.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppManagerService
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.RemoteAppInfo
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleBackground

private val IconColors = listOf(
    Color(0xFFE57373),
    Color(0xFF64B5F6),
    Color(0xFF81C784),
    Color(0xFFFFB74D),
    Color(0xFFBA68C8),
    Color(0xFF4DD0E1),
    Color(0xFFF06292),
    Color(0xFFAED581),
    Color(0xFFFFD54F),
    Color(0xFF7986CB),
)

private data class LocalAppInfo(
    val packageName: String,
    val label: String,
    val sourceDir: String,
    val isSystem: Boolean,
    val icon: Bitmap? = null,
)

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) return drawable.bitmap
    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    // AdaptiveIconDrawable 的实际内容只占中心约 72/108 的安全区，
    // 直接绘制完整画布再裁圆会导致圆角方形背景与圆形相交产生"八边形"。
    // 这里先绘制完整画布，再裁剪中心安全区，确保圆形裁剪时只切到图标内容。
    if (drawable is android.graphics.drawable.AdaptiveIconDrawable) {
        val fullBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val fullCanvas = Canvas(fullBitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(fullCanvas)
        val ratio = 72f / 108f
        val cropW = (w * ratio).toInt().coerceAtLeast(1)
        val cropH = (h * ratio).toInt().coerceAtLeast(1)
        val offsetX = (w - cropW) / 2
        val offsetY = (h - cropH) / 2
        return Bitmap.createBitmap(fullBitmap, offsetX, offsetY, cropW, cropH)
    }
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return bitmap
}

internal class AppManagerViewModel : ViewModel() {
    private val _userApps = MutableStateFlow<List<RemoteAppInfo>>(emptyList())
    val userApps: StateFlow<List<RemoteAppInfo>> = _userApps.asStateFlow()

    private val _systemApps = MutableStateFlow<List<RemoteAppInfo>>(emptyList())
    val systemApps: StateFlow<List<RemoteAppInfo>> = _systemApps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedApp = MutableStateFlow<RemoteAppInfo?>(null)
    val selectedApp: StateFlow<RemoteAppInfo?> = _selectedApp.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _notConnected = MutableStateFlow(false)
    val notConnected: StateFlow<Boolean> = _notConnected.asStateFlow()

    fun loadApps(fetchIcons: Boolean = true) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _notConnected.value = false
            try {
                val result = AppManagerService.loadAllApps(fetchIcons)
                _userApps.value = result.userApps
                _systemApps.value = result.systemApps
                if (result.userApps.isEmpty() && result.systemApps.isEmpty()) {
                    _error.value = AppRuntime.stringResource(R.string.appmgr_error_no_device)
                    _notConnected.value = true
                }
            } catch (e: Exception) {
                _error.value = e.message ?: AppRuntime.stringResource(R.string.appmgr_error_no_device)
                _notConnected.value = true
            } finally {
                _loading.value = false
            }
        }
    }

    fun selectApp(app: RemoteAppInfo?) {
        _selectedApp.value = app
    }

    fun refreshApp() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = AppManagerService.loadAllApps()
                _userApps.value = result.userApps
                _systemApps.value = result.systemApps
            } catch (e: Exception) {
                AppRuntime.snackbar("刷新失败: ${e.message}")
            }
        }
    }

    fun setUploading(value: Boolean) {
        _uploading.value = value
    }

    fun exportApk(app: RemoteAppInfo) {
        viewModelScope.launch {
            _exporting.value = true
            val result = withContext(Dispatchers.IO) {
                AppManagerService.exportApk(app.packageName, app.apkPath)
            }
            _exporting.value = false
            result
                .onSuccess {
                    AppRuntime.snackbar("已导出到Download", duration = SnackbarDuration.Custom(2000L))
                }
                .onFailure {
                    AppRuntime.snackbar("导出失败: ${it.message ?: ""}", duration = SnackbarDuration.Custom(2000L))
                }
        }
    }

    fun freezePackages(packages: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            for (pkg in packages) {
                AppManagerService.disable(pkg)
                    .onSuccess { successCount++ }
                    .onFailure { failCount++ }
            }
            AppRuntime.snackbar("停用完成: 成功 $successCount, 失败 $failCount", duration = SnackbarDuration.Custom(3000L))
            refreshApp()
        }
    }

    fun unfreezePackages(packages: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            for (pkg in packages) {
                AppManagerService.enable(pkg)
                    .onSuccess { successCount++ }
                    .onFailure { failCount++ }
            }
            AppRuntime.snackbar("启用完成: 成功 $successCount, 失败 $failCount", duration = SnackbarDuration.Custom(3000L))
            refreshApp()
        }
    }

    fun uninstallPackages(packages: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            for (pkg in packages) {
                AppManagerService.uninstall(pkg)
                    .onSuccess { successCount++ }
                    .onFailure { failCount++ }
            }
            AppRuntime.snackbar("卸载完成: 成功 $successCount, 失败 $failCount", duration = SnackbarDuration.Custom(3000L))
            refreshApp()
        }
    }

    fun exportPackages(packages: Set<String>) {
        viewModelScope.launch {
            _exporting.value = true
            var successCount = 0
            var failCount = 0
            for (pkg in packages) {
                val app = _userApps.value.find { it.packageName == pkg }
                    ?: _systemApps.value.find { it.packageName == pkg }
                if (app != null) {
                    val result = withContext(Dispatchers.IO) {
                        AppManagerService.exportApk(app.packageName, app.apkPath)
                    }
                    result
                        .onSuccess { successCount++ }
                        .onFailure { failCount++ }
                } else {
                    failCount++
                }
            }
            _exporting.value = false
            AppRuntime.snackbar("导出完成: 成功 $successCount, 失败 $failCount", duration = SnackbarDuration.Custom(3000L))
        }
    }
}

@Composable
fun AppManagerScreen(
    onBack: () -> Unit,
    scrcpy: Scrcpy,
    onNavigateToDeviceTab: () -> Unit = {},
) {
    val viewModel = remember { AppManagerViewModel() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AppManagerService.scrcpy = scrcpy
    AppManagerService.appContext = context

    val localSnackbarHostState = remember { SnackbarHostState() }
    DisposableEffect(localSnackbarHostState) {
        val unregister = AppRuntime.registerSnackbarHostState(localSnackbarHostState)
        onDispose(unregister)
    }

    val userApps by viewModel.userApps.collectAsState()
    val systemApps by viewModel.systemApps.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    val uploading by viewModel.uploading.collectAsState()
    val exporting by viewModel.exporting.collectAsState()
    val notConnected by viewModel.notConnected.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 2 })

    var showUploadMethod by rememberSaveable { mutableStateOf(false) }
    var showLocalAppPicker by rememberSaveable { mutableStateOf(false) }
    var localApps by remember { mutableStateOf<List<LocalAppInfo>>(emptyList()) }
    var localAppsLoading by remember { mutableStateOf(false) }
    var selectedLocalPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var batchConfirmAction by rememberSaveable { mutableStateOf<String?>(null) }

    val asBundle by appSettings.bundleState.collectAsState()
    val showIcons = asBundle.showAppIcons
    val pullToRefreshState = rememberPullToRefreshState()

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            viewModel.setUploading(true)
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@runCatching
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "app.apk"
                val remotePath = "/data/local/tmp/$fileName"

                val connected = try {
                    NativeAdbService.ensureConnectionResponsive()
                    true
                } catch (e: Exception) {
                    false
                }

                if (!connected) {
                    AppRuntime.snackbar("ADB未连接，请先连接设备")
                    return@runCatching
                }

                AppRuntime.snackbar("正在上传: $fileName", dismissNewest = true)

                inputStream.use { input ->
                    NativeAdbService.push(input, remotePath)
                }
                AppRuntime.snackbar("$fileName 已上传，正在安装...", dismissNewest = true)
                AppManagerService.installApk(remotePath)
                    .onSuccess {
                        AppRuntime.snackbar("$fileName 安装成功", dismissNewest = true, duration = SnackbarDuration.Custom(1500L))
                    }
                    .onFailure {
                        AppRuntime.snackbar("$fileName 安装失败: ${it.message}", dismissNewest = true, duration = SnackbarDuration.Custom(2000L))
                    }
            }.onFailure {
                AppRuntime.snackbar("上传失败: ${it.message}", dismissNewest = true)
            }
            viewModel.refreshApp()
            viewModel.setUploading(false)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    BackHandler(enabled = selectedPackages.isNotEmpty()) {
        selectedPackages = emptySet()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.appmgr_title),
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPackages.isNotEmpty()) {
                            selectedPackages = emptySet()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.appmgr_search_hint),
                        )
                    }
                    if (selectedPackages.isNotEmpty()) {
                        val selectedList = selectedPackages.toList()
                        val enabledList = selectedList.filter { pkg ->
                            val app = userApps.find { it.packageName == pkg }
                                ?: systemApps.find { it.packageName == pkg }
                            app?.isEnabled == true
                        }
                        val disabledList = selectedList.filter { pkg ->
                            val app = userApps.find { it.packageName == pkg }
                                ?: systemApps.find { it.packageName == pkg }
                            app?.isEnabled == false
                        }
                        OverlayIconDropdownMenu(
                            entry = DropdownEntry(
                                items = listOfNotNull(
                                    DropdownItem(
                                        text = stringResource(R.string.appmgr_action_uninstall),
                                        onClick = { batchConfirmAction = "uninstall" },
                                    ),
                                    if (enabledList.isNotEmpty()) DropdownItem(
                                        text = stringResource(R.string.appmgr_action_disable),
                                        onClick = { batchConfirmAction = "freeze" },
                                    ) else null,
                                    if (disabledList.isNotEmpty()) DropdownItem(
                                        text = stringResource(R.string.appmgr_action_enable),
                                        onClick = { batchConfirmAction = "unfreeze" },
                                    ) else null,
                                    DropdownItem(
                                        text = stringResource(R.string.appmgr_action_export),
                                        onClick = { batchConfirmAction = "export" },
                                    ),
                                )
                            )
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = stringResource(R.string.cd_more),
                            )
                        }
                    } else {
                        IconButton(onClick = { showUploadMethod = true }) {
                            Icon(
                                imageVector = Icons.Rounded.FileUpload,
                                contentDescription = stringResource(R.string.appmgr_upload_apk),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(localSnackbarHostState) },
    ) { padding ->
        PullToRefresh(
            isRefreshing = loading,
            onRefresh = { viewModel.loadApps(fetchIcons = showIcons) },
            pullToRefreshState = pullToRefreshState,
            modifier = Modifier.padding(padding),
            refreshTexts = listOf(
                stringResource(R.string.pull_refresh_pull_down),
                stringResource(R.string.pull_refresh_release),
                stringResource(R.string.pull_refresh_refreshing),
                stringResource(R.string.pull_refresh_complete),
            ),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (showSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.PageHorizontal, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.appmgr_search_hint),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = MiuixIcons.Basic.Search,
                                contentDescription = stringResource(R.string.appmgr_search_hint),
                                tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                            )
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = MiuixIcons.Basic.SearchCleanup,
                                        contentDescription = stringResource(R.string.cd_clear),
                                        tint = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                                    )
                                }
                            }
                        } else null,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.PageHorizontal, vertical = 4.dp),
            ) {
                val tabTitles = listOf(
                    stringResource(R.string.appmgr_tab_user_apps),
                    stringResource(R.string.appmgr_tab_system_apps),
                )
                tabTitles.forEachIndexed { index, title ->
                    val selected = pagerState.currentPage == index
                    MiuixCard(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        cornerRadius = 12.dp,
                        colors = CardDefaults.defaultColors(
                            color = if (selected)
                                MiuixTheme.colorScheme.primary
                            else
                                MiuixTheme.colorScheme.surfaceVariant,
                        ),
                        pressFeedbackType = PressFeedbackType.Sink,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            color = if (selected)
                                MiuixTheme.colorScheme.onPrimary
                            else
                                MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.appmgr_loading),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                error != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillParentMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MiuixTheme.colorScheme.error,
                                    )
                                    Text(
                                        text = error!!,
                                        color = MiuixTheme.colorScheme.error,
                                    )
                                    if (notConnected) {
                                        TextButton(
                                            text = stringResource(R.string.appmgr_go_to_device),
                                            onClick = onNavigateToDeviceTab,
                                        )
                                    } else {
                                        TextButton(
                                            text = stringResource(R.string.appmgr_refresh),
                                            onClick = { viewModel.loadApps() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(0.dp),
                    ) { page ->
                        val allApps = if (page == 0) userApps else systemApps
                        val filteredApps = if (searchQuery.isBlank()) allApps
                        else allApps.filter {
                            it.label.contains(searchQuery, ignoreCase = true) ||
                                    it.packageName.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredApps.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.appmgr_no_apps),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(filteredApps, key = { it.packageName }) { app ->
                                    val isSelected = app.packageName in selectedPackages
                                    AppItemCard(
                                        app = app,
                                        onClick = {
                                            if (selectedPackages.isNotEmpty()) {
                                                selectedPackages = if (isSelected)
                                                    selectedPackages - app.packageName
                                                else
                                                    selectedPackages + app.packageName
                                            } else {
                                                viewModel.selectApp(app)
                                            }
                                        },
                                        onLongClick = {
                                            selectedPackages = if (isSelected)
                                                selectedPackages - app.packageName
                                            else
                                                selectedPackages + app.packageName
                                        },
                                        showIcons = showIcons,
                                        showCheckbox = selectedPackages.isNotEmpty(),
                                        selected = isSelected,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedPackages.isNotEmpty()) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .squircleBackground(
                            color = MiuixTheme.colorScheme.surface,
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomEnd = 0.dp,
                            bottomStart = 0.dp,
                        )
                        .padding(horizontal = UiSpacing.PageHorizontal, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.appmgr_batch_selected, selectedPackages.size),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        TextButton(
                            text = stringResource(R.string.appmgr_batch_clear),
                            onClick = { selectedPackages = emptySet() },
                        )
                    }
                }
            }

            val currentSelectedApp = selectedApp
            if (currentSelectedApp != null) {
AppActionSheet(
show = true,
app = currentSelectedApp,
onDismiss = { viewModel.selectApp(null) },
onActionComplete = {
viewModel.refreshApp()
viewModel.selectApp(null)
},
onExport = {
viewModel.selectApp(null)
viewModel.exportApk(currentSelectedApp)
},
onOpen = {
val appToOpen = currentSelectedApp
scope.launch(Dispatchers.IO) {
val result = runCatching {
NativeAdbService.shell(
"monkey -p ${appToOpen.packageName} -c android.intent.category.LAUNCHER 1 2>&1"
)
}
val output = result.getOrDefault("")
if (result.isSuccess && output.contains("Events injected")) {
AppRuntime.snackbar(R.string.appmgr_open_success, appToOpen.label)
} else {
AppRuntime.snackbar(R.string.appmgr_open_failed, appToOpen.label)
}
}
},
)
            }

            UploadingDialog(
                show = uploading,
                onDismiss = { },
            )

            ExportingDialog(
                show = exporting,
            )

            when (batchConfirmAction) {
                "freeze" -> OverlayDialog(
                    show = true,
                    title = stringResource(R.string.appmgr_action_disable),
                    summary = stringResource(R.string.appmgr_batch_freeze_confirm, selectedPackages.size),
                    onDismissRequest = { batchConfirmAction = null },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                        TextButton(
                            text = stringResource(R.string.button_cancel),
                            onClick = { batchConfirmAction = null },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(R.string.appmgr_action_disable),
                            onClick = {
                                val pkgs = selectedPackages.toSet()
                                batchConfirmAction = null
                                selectedPackages = emptySet()
                                viewModel.freezePackages(pkgs)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
                "unfreeze" -> OverlayDialog(
                    show = true,
                    title = stringResource(R.string.appmgr_action_enable),
                    summary = stringResource(R.string.appmgr_batch_unfreeze_confirm, selectedPackages.size),
                    onDismissRequest = { batchConfirmAction = null },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                        TextButton(
                            text = stringResource(R.string.button_cancel),
                            onClick = { batchConfirmAction = null },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(R.string.appmgr_action_enable),
                            onClick = {
                                val pkgs = selectedPackages.toSet()
                                batchConfirmAction = null
                                selectedPackages = emptySet()
                                viewModel.unfreezePackages(pkgs)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
                "uninstall" -> OverlayDialog(
                    show = true,
                    title = stringResource(R.string.appmgr_action_uninstall),
                    summary = stringResource(R.string.appmgr_batch_uninstall_confirm, selectedPackages.size),
                    onDismissRequest = { batchConfirmAction = null },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                        TextButton(
                            text = stringResource(R.string.button_cancel),
                            onClick = { batchConfirmAction = null },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(R.string.appmgr_action_uninstall),
                            onClick = {
                                val pkgs = selectedPackages.toSet()
                                batchConfirmAction = null
                                selectedPackages = emptySet()
                                viewModel.uninstallPackages(pkgs)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
                "export" -> OverlayDialog(
                    show = true,
                    title = stringResource(R.string.appmgr_action_export),
                    summary = stringResource(R.string.appmgr_batch_export_confirm, selectedPackages.size),
                    onDismissRequest = { batchConfirmAction = null },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                        TextButton(
                            text = stringResource(R.string.button_cancel),
                            onClick = { batchConfirmAction = null },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(R.string.appmgr_action_export),
                            onClick = {
                                val pkgs = selectedPackages.toSet()
                                batchConfirmAction = null
                                selectedPackages = emptySet()
                                viewModel.exportPackages(pkgs)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }

            UploadMethodSheet(
                show = showUploadMethod,
                onSelectFile = {
                    showUploadMethod = false
                    uploadLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                },
                onSelectLocalApp = {
                    showUploadMethod = false
                    showLocalAppPicker = true
                    localAppsLoading = true
                    scope.launch(Dispatchers.IO) {
                        val pm = context.packageManager
                        // 先快速加载列表（不包含图标）
                        val apps = pm.getInstalledApplications(0)
                            .mapNotNull { appInfo ->
                                try {
                                    val label = pm.getApplicationLabel(appInfo).toString()
                                    LocalAppInfo(
                                        packageName = appInfo.packageName,
                                        label = label,
                                        sourceDir = appInfo.sourceDir,
                                        isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                                        icon = null,
                                    )
                                } catch (e: Exception) { null }
                            }
                            .filter { !it.isSystem }
                            .sortedBy { it.label.lowercase() }
                        localApps = apps
                        localAppsLoading = false
                        // 异步加载图标
                        apps.forEachIndexed { index, app ->
                            try {
                                val appInfo = pm.getApplicationInfo(app.packageName, 0)
                                val icon = drawableToBitmap(pm.getApplicationIcon(appInfo))
                                localApps = localApps.map { current ->
                                    if (current.packageName == app.packageName)
                                        current.copy(icon = icon)
                                    else current
                                }
                            } catch (e: Exception) { /* ignore */ }
                        }
                    }
                },
                onDismiss = { showUploadMethod = false },
            )

            LocalAppPickerSheet(
                show = showLocalAppPicker,
                apps = localApps,
                loading = localAppsLoading,
                selectedPackages = selectedLocalPackages,
                onSelect = { app ->
                    showLocalAppPicker = false
                    selectedLocalPackages = emptySet()
                    scope.launch(Dispatchers.IO) {
                        viewModel.setUploading(true)
                        runCatching {
                            val connected = try {
                                NativeAdbService.ensureConnectionResponsive()
                                true
                            } catch (e: Exception) { false }
                            if (!connected) {
                                AppRuntime.snackbar("ADB未连接，请先连接设备")
                                return@runCatching
                            }

                            val remotePath = "/data/local/tmp/${app.packageName}.apk"
                            AppRuntime.snackbar("正在上传 ${app.label}...")

                            FileInputStream(File(app.sourceDir)).use { input ->
                                NativeAdbService.push(input, remotePath)
                            }
                            AppRuntime.snackbar("APK已上传，正在安装...", dismissNewest = true)
                            AppManagerService.installApk(remotePath)
                                .onSuccess {
                                    AppRuntime.snackbar("安装成功", dismissNewest = true, duration = SnackbarDuration.Custom(2000L))
                                    viewModel.refreshApp()
                                }
                                .onFailure { AppRuntime.snackbar("安装失败: ${it.message}", dismissNewest = true, duration = SnackbarDuration.Custom(2000L)) }
                        }.onFailure {
                            AppRuntime.snackbar("上传失败: ${it.message}", dismissNewest = true)
                        }.also {
                            viewModel.setUploading(false)
                        }
                    }
                },
                onBatchInstall = { selectedApps ->
                    showLocalAppPicker = false
                    selectedLocalPackages = emptySet()
                    scope.launch(Dispatchers.IO) {
                        viewModel.setUploading(true)
                        var successCount = 0
                        var failCount = 0
                        for (app in selectedApps) {
                            runCatching {
                                val connected = try {
                                    NativeAdbService.ensureConnectionResponsive()
                                    true
                                } catch (e: Exception) { false }
                                if (!connected) {
                                    AppRuntime.snackbar("ADB未连接，请先连接设备")
                                    return@runCatching
                                }

                                val remotePath = "/data/local/tmp/${app.packageName}.apk"
                                FileInputStream(File(app.sourceDir)).use { input ->
                                    NativeAdbService.push(input, remotePath)
                                }
                                AppManagerService.installApk(remotePath)
                                    .onSuccess { successCount++ }
                                    .onFailure { failCount++ }
                            }.onFailure { failCount++ }
                        }
                        AppRuntime.snackbar(
                            "安装完成: 成功 $successCount, 失败 $failCount",
                            duration = SnackbarDuration.Custom(3000L),
                        )
                        viewModel.refreshApp()
                        viewModel.setUploading(false)
                    }
                },
                onToggleSelection = { pkg ->
                    selectedLocalPackages = if (pkg in selectedLocalPackages)
                        selectedLocalPackages - pkg else selectedLocalPackages + pkg
                },
                onDismiss = {
                    showLocalAppPicker = false
                    selectedLocalPackages = emptySet()
                },
                onClearSelection = {
                    selectedLocalPackages = emptySet()
                },
            )
        }
        }
    }
}

@Composable
private fun AppIconPlaceholder(label: String, packageName: String, iconBase64: String?) {
    val bitmap = remember(iconBase64) {
        if (iconBase64 != null) {
            try {
                val bytes = Base64.decode(iconBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    if (bitmap != null) {
Image(
bitmap = bitmap.asImageBitmap(),
contentDescription = label,
contentScale = ContentScale.Fit,
modifier = Modifier
.size(40.dp),
)
    } else {
        val color = remember(packageName) {
            IconColors[Math.abs(packageName.hashCode()) % IconColors.size]
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1).uppercase(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AppItemCard(
    app: RemoteAppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    showIcons: Boolean = true,
    showCheckbox: Boolean = false,
    selected: Boolean = false,
) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiSpacing.PageHorizontal, vertical = 2.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(all = UiSpacing.ContentVertical)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcons) {
                AppIconPlaceholder(label = app.label, packageName = app.packageName, iconBase64 = app.iconBase64)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (app.versionName.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.appmgr_version, app.versionName),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    if (app.sizeBytes > 0) {
                        Text(
                            text = app.formattedSize,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    if (!app.isEnabled) {
                        Text(
                            text = stringResource(R.string.appmgr_disabled),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (showCheckbox) {
                // 多选模式下，右侧显示勾选标记
                Spacer(Modifier.width(8.dp))
                Checkbox(
                    state = ToggleableState(selected),
                    onClick = onClick,
                )
            }
        }
    }
}

@Composable
private fun UploadingDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    if (!show) return

    OverlayDialog(
        show = true,
        title = "上传APK",
        summary = "正在上传和安装APK，请稍候...",
        onDismissRequest = onDismiss,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "上传中，请稍候...",
                style = MiuixTheme.textStyles.body1,
            )
        }
    }
}

@Composable
private fun ExportingDialog(
    show: Boolean,
) {
    if (!show) return

    OverlayDialog(
        show = true,
        title = "导出APK",
        summary = "正在导出APK到Download目录，请稍候...",
        onDismissRequest = { },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "导出中，请稍候...",
                style = MiuixTheme.textStyles.body1,
            )
        }
    }
}

@Composable
private fun AppActionSheet(
show: Boolean,
app: RemoteAppInfo,
onDismiss: () -> Unit,
onActionComplete: () -> Unit,
onExport: () -> Unit,
onOpen: () -> Unit,
) {
val scope = rememberCoroutineScope()
var showConfirmDialog by rememberSaveable { mutableStateOf<String?>(null) }

OverlayBottomSheet(
show = show,
title = app.label,
defaultWindowInsetsPadding = false,
onDismissRequest = onDismiss,
) {
Column(
modifier = Modifier.padding(vertical = 4.dp),
) {
ActionItem(
text = stringResource(R.string.appmgr_action_open),
enabled = app.isEnabled,
onClick = {
onDismiss()
onOpen()
},
)

if (!app.isSystem) {
                ActionItem(
                    text = stringResource(R.string.appmgr_action_uninstall),
                    onClick = { showConfirmDialog = "uninstall" },
                )
            }

            ActionItem(
                text = stringResource(R.string.appmgr_action_export),
                onClick = {
                    onDismiss()
                    onExport()
                },
            )

            if (app.isEnabled) {
                ActionItem(
                    text = stringResource(R.string.appmgr_action_disable),
                    onClick = { showConfirmDialog = "disable" },
                )
            } else {
                ActionItem(
                    text = stringResource(R.string.appmgr_action_enable),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            AppManagerService.enable(app.packageName)
                                .onSuccess { AppRuntime.snackbar(R.string.appmgr_enable_success, app.label) }
                                .onFailure { AppRuntime.snackbar(R.string.appmgr_enable_failed, it.message ?: "") }
                            onActionComplete()
                        }
                    },
                )
            }

            ActionItem(
                text = stringResource(R.string.appmgr_action_force_stop),
                onClick = { showConfirmDialog = "force_stop" },
            )

            ActionItem(
                text = stringResource(R.string.appmgr_action_clear_data),
                onClick = { showConfirmDialog = "clear_data" },
            )
        }
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }

    when (showConfirmDialog) {
        "uninstall" -> OverlayDialog(
            show = true,
            title = stringResource(R.string.appmgr_action_uninstall),
            summary = stringResource(R.string.appmgr_uninstall_confirm, app.label),
            onDismissRequest = { showConfirmDialog = null },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = { showConfirmDialog = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.appmgr_action_uninstall),
                    onClick = {
                        showConfirmDialog = null
                        scope.launch(Dispatchers.IO) {
                            AppManagerService.uninstall(app.packageName)
                                .onSuccess { AppRuntime.snackbar(R.string.appmgr_uninstall_success, app.label) }
                                .onFailure { AppRuntime.snackbar(R.string.appmgr_uninstall_failed, it.message ?: "") }
                            onActionComplete()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        "disable" -> OverlayDialog(
            show = true,
            title = stringResource(R.string.appmgr_action_disable),
            summary = stringResource(R.string.appmgr_disable_confirm, app.label),
            onDismissRequest = { showConfirmDialog = null },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = { showConfirmDialog = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.appmgr_action_disable),
                    onClick = {
                        showConfirmDialog = null
                        scope.launch(Dispatchers.IO) {
                            AppManagerService.disable(app.packageName)
                                .onSuccess { AppRuntime.snackbar(R.string.appmgr_disable_success, app.label) }
                                .onFailure { AppRuntime.snackbar(R.string.appmgr_disable_failed, it.message ?: "") }
                            onActionComplete()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        "force_stop" -> OverlayDialog(
            show = true,
            title = stringResource(R.string.appmgr_action_force_stop),
            summary = stringResource(R.string.appmgr_force_stop_confirm, app.label),
            onDismissRequest = { showConfirmDialog = null },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = { showConfirmDialog = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.appmgr_action_force_stop),
                    onClick = {
                        showConfirmDialog = null
                        scope.launch(Dispatchers.IO) {
                            AppManagerService.forceStop(app.packageName)
                                .onSuccess { AppRuntime.snackbar(R.string.appmgr_force_stop_success, app.label) }
                                .onFailure { AppRuntime.snackbar(R.string.appmgr_force_stop_failed, it.message ?: "") }
                            onActionComplete()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        "clear_data" -> OverlayDialog(
            show = true,
            title = stringResource(R.string.appmgr_action_clear_data),
            summary = stringResource(R.string.appmgr_clear_data_confirm, app.label),
            onDismissRequest = { showConfirmDialog = null },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = { showConfirmDialog = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.appmgr_action_clear_data),
                    onClick = {
                        showConfirmDialog = null
                        scope.launch(Dispatchers.IO) {
                            AppManagerService.clearData(app.packageName)
                                .onSuccess { AppRuntime.snackbar(R.string.appmgr_clear_data_success, app.label) }
                                .onFailure { AppRuntime.snackbar(R.string.appmgr_clear_data_failed, it.message ?: "") }
                            onActionComplete()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun ActionItem(
text: String,
onClick: () -> Unit,
enabled: Boolean = true,
) {
MiuixCard(
modifier = Modifier
.fillMaxWidth()
.padding(vertical = 1.dp),
pressFeedbackType = PressFeedbackType.Sink,
showIndication = enabled,
onClick = { if (enabled) onClick() },
) {
Text(
text = text,
modifier = Modifier
.fillMaxWidth()
.padding(UiSpacing.ContentVertical),
fontSize = 15.sp,
color = if (enabled) MiuixTheme.colorScheme.onSurface
else MiuixTheme.colorScheme.onSurfaceVariantSummary,
)
}
}

@Composable
private fun UploadMethodSheet(
    show: Boolean,
    onSelectFile: () -> Unit,
    onSelectLocalApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.appmgr_upload_apk),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            ActionItem(
                text = stringResource(R.string.appmgr_upload_from_file),
                onClick = {
                    onDismiss()
                    onSelectFile()
                },
            )
            ActionItem(
                text = stringResource(R.string.appmgr_upload_from_device),
                onClick = {
                    onDismiss()
                    onSelectLocalApp()
                },
            )
        }
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }
}

@Composable
private fun LocalAppPickerSheet(
    show: Boolean,
    apps: List<LocalAppInfo>,
    loading: Boolean,
    selectedPackages: Set<String>,
    onSelect: (LocalAppInfo) -> Unit,
    onBatchInstall: (List<LocalAppInfo>) -> Unit,
    onToggleSelection: (String) -> Unit,
    onDismiss: () -> Unit,
    onClearSelection: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    // 确认对话框状态
    var confirmSingleApp by remember { mutableStateOf<LocalAppInfo?>(null) }
    var confirmBatchApps by remember { mutableStateOf<List<LocalAppInfo>?>(null) }

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.appmgr_local_app_picker_title),
        defaultWindowInsetsPadding = false,
        onDismissRequest = {
            searchQuery = ""
            onDismiss()
        },
    ) {
        BackHandler(enabled = selectedPackages.isNotEmpty()) {
            onClearSelection()
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.appmgr_search_hint),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = MiuixIcons.Basic.Search,
                            contentDescription = stringResource(R.string.appmgr_search_hint),
                            tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.SearchCleanup,
                                    contentDescription = stringResource(R.string.cd_clear),
                                    tint = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                                )
                            }
                        }
                    } else null,
                )
                if (selectedPackages.isNotEmpty()) {
                    TextButton(
                        text = stringResource(R.string.appmgr_batch_install),
                        onClick = {
                            val selectedApps = apps.filter { it.packageName in selectedPackages }
                            confirmBatchApps = selectedApps
                        },
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.appmgr_loading_local_apps),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                val filteredApps = if (searchQuery.isBlank()) apps
                else apps.filter {
                    it.label.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                }

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .padding(bottom = UiSpacing.SheetBottom),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Text(
                            text = stringResource(R.string.appmgr_no_apps),
                            modifier = Modifier.padding(top = 16.dp),
                            textAlign = TextAlign.Center,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = app.packageName in selectedPackages
                            LocalAppItem(
                                app = app,
                                showCheckbox = selectedPackages.isNotEmpty(),
                                selected = isSelected,
                                onClick = {
                                    if (selectedPackages.isNotEmpty()) {
                                        onToggleSelection(app.packageName)
                                    } else {
                                        confirmSingleApp = app
                                    }
                                },
                                onLongClick = {
                                    onToggleSelection(app.packageName)
                                },
                                onCheckToggle = {
                                    onToggleSelection(app.packageName)
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }

    // 单个安装确认对话框
    confirmSingleApp?.let { app ->
        OverlayDialog(
            show = true,
            title = "安装应用",
            summary = "确定要安装 ${app.label} 吗？",
            onDismissRequest = { confirmSingleApp = null },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = { confirmSingleApp = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "安装",
                    onClick = {
                        val target = app
                        confirmSingleApp = null
                        searchQuery = ""
                        onSelect(target)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    // 批量安装确认对话框
    confirmBatchApps?.let { selectedApps ->
        OverlayDialog(
            show = true,
            title = "批量安装",
            summary = "确定要安装选中的 ${selectedApps.size} 个应用吗？",
            onDismissRequest = { confirmBatchApps = null },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = { confirmBatchApps = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "安装",
                    onClick = {
                        val target = selectedApps
                        confirmBatchApps = null
                        onBatchInstall(target)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun LocalAppItem(
    app: LocalAppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    showCheckbox: Boolean = false,
    selected: Boolean = false,
    onCheckToggle: () -> Unit = {},
) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(all = UiSpacing.ContentVertical)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bitmap = app.icon
            if (bitmap != null) {
Image(
bitmap = bitmap.asImageBitmap(),
contentDescription = app.label,
contentScale = ContentScale.Fit,
modifier = Modifier
.size(36.dp),
)
            } else {
                val color = remember(app.packageName) {
                    IconColors[Math.abs(app.packageName.hashCode()) % IconColors.size]
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = app.label.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (app.isSystem) {
                Text(
                    text = "系统",
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (showCheckbox) {
                // 多选模式下，右侧显示勾选标记
                Spacer(Modifier.width(8.dp))
                Checkbox(
                    state = ToggleableState(selected),
                    onClick = onCheckToggle,
                )
            }
        }
    }
}
