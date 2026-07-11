package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Visibility
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppManagerService
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.ScreenControlManager
import io.github.miuzarte.scrcpyforandroid.services.ScreenControlResult
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.widgets.DeviceInfoSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ToolItem(
    val icon: ImageVector,
    val titleResId: Int,
    val descResId: Int,
    val onClick: () -> Unit,
)

data class ActivatableApp(
    val name: String,
    val packageName: String,
    val activateCommands: List<String>,
)

private val MonitorOff: ImageVector
    get() {
        if (_monitorOff != null) return _monitorOff!!
        _monitorOff = ImageVector.Builder(
            name = "MonitorOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(14f, 18f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                arcTo(2f, 2f, 0f, false, true, 1f, 16f)
                verticalLineTo(4f)
                lineTo(0f, 3f)
                lineTo(1.41f, 1.58f)
                lineTo(22.16f, 22.34f)
                lineTo(20.75f, 23.75f)
                lineTo(15f, 18f)
                close()

                moveTo(3f, 16f)
                horizontalLineToRelative(10f)
                lineTo(3f, 6f)
                close()

                moveTo(21f, 2f)
                arcTo(2f, 2f, 0f, false, true, 23f, 4f)
                verticalLineToRelative(12f)
                arcTo(2f, 2f, 0f, false, true, 21f, 18f)
                horizontalLineToRelative(-0.34f)
                lineToRelative(-2f, -2f)
                horizontalLineTo(21f)
                verticalLineTo(4f)
                horizontalLineTo(6.66f)
                lineToRelative(-2f, -2f)
                close()
            }
        }.build()
        return _monitorOff!!
    }
private var _monitorOff: ImageVector? = null

private const val TAG = "UtilityToolsScreen"

@Composable
fun UtilityToolsScreen(
    onBack: () -> Unit,
    scrcpy: Scrcpy,
    onNavigateToDeviceTab: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    val navigator = LocalRootNavigator.current

    LaunchedEffect(context, scrcpy) {
        AppManagerService.appContext = context
        AppManagerService.scrcpy = scrcpy
    }

    val localSnackbarHostState = remember { SnackbarHostState() }
    DisposableEffect(localSnackbarHostState) {
        val unregister = AppRuntime.registerSnackbarHostState(localSnackbarHostState)
        onDispose(unregister)
    }

    var showScreenshotDialog by rememberSaveable { mutableStateOf(false) }
    var screenshotCacheFile by remember { mutableStateOf<File?>(null) }
    var showRebootDialog by rememberSaveable { mutableStateOf(false) }
    var showScreenStandby by rememberSaveable { mutableStateOf(false) }
    var showDpiDialog by rememberSaveable { mutableStateOf(false) }
    var showResolutionDialog by rememberSaveable { mutableStateOf(false) }
    var showActivateDialog by rememberSaveable { mutableStateOf(false) }
    var showProcessSheet by rememberSaveable { mutableStateOf(false) }
    var showDeviceInfo by rememberSaveable { mutableStateOf(false) }
    var showAdbDisconnected by rememberSaveable { mutableStateOf(false) }
    var hasShownAdbDisconnected by rememberSaveable { mutableStateOf(false) }

    var wirelessAdbEnabled by rememberSaveable { mutableStateOf(false) }
    var wirelessAdbChecking by rememberSaveable { mutableStateOf(false) }
    var currentDpiValue by remember { mutableStateOf("") }
    var defaultDpiValue by remember { mutableStateOf("") }
    var currentResolutionValue by remember { mutableStateOf("") }
    var defaultResolutionValue by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val result = NativeAdbService.shell("getprop service.adb.tcp.port").trim()
                wirelessAdbEnabled = result == "5555"
            } catch (_: Exception) {
                wirelessAdbEnabled = false
            }
        }
    }

    fun executeWithCheck(block: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) {
            val connected = try {
                NativeAdbService.isConnected()
            } catch (_: Exception) { false }
            if (!connected) {
                withContext(Dispatchers.Main) {
                    if (!hasShownAdbDisconnected) {
                        showAdbDisconnected = true
                        hasShownAdbDisconnected = true
                    }
                }
                return@launch
            }
            hasShownAdbDisconnected = false
            block()
        }
    }

    fun takeScreenshot() {
        executeWithCheck {
            try {
                withContext(Dispatchers.Main) {
                    AppRuntime.snackbar(R.string.tools_screenshot)
                }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val remotePath = "/data/local/tmp/screenshot_$timestamp.png"
                NativeAdbService.shell("screencap -p $remotePath")
                val bytes = NativeAdbService.pull(remotePath)
                NativeAdbService.shell("rm $remotePath")
                val cacheDir = context.cacheDir
                val cacheFile = File(cacheDir, "screenshot_$timestamp.png")
                cacheFile.writeBytes(bytes)
                screenshotCacheFile = cacheFile
                showScreenshotDialog = true
            } catch (e: Exception) {
                AppRuntime.snackbar(R.string.tools_screenshot_failed, e.message ?: "")
            }
        }
    }

    fun reboot(mode: String) {
        executeWithCheck {
            try {
                NativeAdbService.shell("reboot $mode")
                AppRuntime.snackbar(R.string.tools_reboot_executing)
            } catch (e: Exception) {
                AppRuntime.snackbar(R.string.tools_reboot_failed, e.message ?: "")
            }
        }
    }

    fun performScreenControl(action: String) {
        showScreenStandby = false
        scope.launch(Dispatchers.IO) {
            val connected = try {
                NativeAdbService.isConnected()
            } catch (_: Exception) { false }
            if (!connected) {
                AppRuntime.snackbar(R.string.tools_screen_standby_not_connected)
                return@launch
            }
            val resId = when (action) {
                "off" -> R.string.tools_screen_standby_success_off
                "on" -> R.string.tools_screen_standby_success_on
                else -> return@launch
            }
            AppRuntime.snackbar(resId)
            val result = when (action) {
                "off" -> ScreenControlManager.screenOff()
                "on" -> ScreenControlManager.screenOn()
                else -> return@launch
            }
            if (result is ScreenControlResult.Failure) {
                Log.w(TAG, "screen control $action failed: ${result.error}")
            }
        }
    }

    fun modifyDpi(dpi: String) {
        executeWithCheck {
            try {
                NativeAdbService.shell("wm density $dpi")
                AppRuntime.snackbar(R.string.tools_dpi_modified, dpi)
            } catch (e: Exception) {
                AppRuntime.snackbar(R.string.tools_dpi_failed, e.message ?: "")
            }
        }
    }

    fun resetDpi() {
        executeWithCheck {
            try {
                NativeAdbService.shell("wm density reset")
                val result = NativeAdbService.shell("wm density").trim()
                val newDpi = result.lineSequence().firstOrNull { it.contains("Physical density:") }
                    ?.substringAfter("Physical density:")?.trim() ?: result
                AppRuntime.snackbar(R.string.tools_dpi_modified, newDpi)
            } catch (e: Exception) {
                AppRuntime.snackbar(R.string.tools_dpi_failed, e.message ?: "")
            }
        }
    }

    fun modifyResolution(width: String, height: String) {
        executeWithCheck {
            try {
                NativeAdbService.shell("wm size ${width}x$height")
                AppRuntime.snackbar(R.string.tools_resolution_modified, width, height)
            } catch (e: Exception) {
                AppRuntime.snackbar(R.string.tools_resolution_failed, e.message ?: "")
            }
        }
    }

    fun resetResolution() {
        executeWithCheck {
            try {
                NativeAdbService.shell("wm size reset")
                val result = NativeAdbService.shell("wm size").trim()
                val newSize = result.lineSequence().firstOrNull { it.contains("Physical size:") }
                    ?.substringAfter("Physical size:")?.trim() ?: result
                val parts = newSize.split("x")
                AppRuntime.snackbar(R.string.tools_resolution_modified, parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
            } catch (e: Exception) {
                AppRuntime.snackbar(R.string.tools_resolution_failed, e.message ?: "")
            }
        }
    }

    fun enableWirelessAdb() {
        scope.launch(Dispatchers.IO) {
            try {
                wirelessAdbChecking = true

                // 方案1：通过ADB协议层发送tcpip命令（推荐，不需要root）
                val response = try {
                    NativeAdbService.tcpip(5555)
                } catch (e: Exception) {
                    Log.w(TAG, "tcpip command failed, trying shell fallback", e)
                    null
                }

                if (response != null && response.contains("5555")) {
                    // tcpip命令成功
                    delay(2000)
                    wirelessAdbEnabled = true
                    wirelessAdbChecking = false
                    AppRuntime.snackbar(R.string.tools_wireless_adb_enabled)
                    return@launch
                }

                // 方案2：通过shell重启adbd作为兜底
                try {
                    NativeAdbService.shell("stop adbd")
                    NativeAdbService.shell("start adbd")
                    delay(2000)
                    val result = NativeAdbService.shell("getprop service.adb.tcp.port").trim()
                    wirelessAdbEnabled = result == "5555"
                } catch (e: Exception) {
                    wirelessAdbEnabled = false
                }

                wirelessAdbChecking = false
                if (wirelessAdbEnabled) {
                    AppRuntime.snackbar(R.string.tools_wireless_adb_enabled)
                } else {
                    AppRuntime.snackbar(R.string.tools_wireless_adb_failed, "tcpip command failed")
                }
            } catch (e: Exception) {
                wirelessAdbChecking = false
                wirelessAdbEnabled = false
                AppRuntime.snackbar(R.string.tools_wireless_adb_failed, e.message ?: "")
            }
        }
    }

    val tools = listOf(
        ToolItem(
            icon = Icons.Rounded.CameraAlt,
            titleResId = R.string.tools_screenshot,
            descResId = R.string.tools_screenshot_desc,
            onClick = ::takeScreenshot,
        ),
        ToolItem(
            icon = Icons.Rounded.RestartAlt,
            titleResId = R.string.tools_advanced_reboot,
            descResId = R.string.tools_advanced_reboot_desc,
            onClick = { showRebootDialog = true },
        ),
        ToolItem(
            icon = MonitorOff,
            titleResId = R.string.tools_screen_standby,
            descResId = R.string.tools_screen_standby_desc,
            onClick = { showScreenStandby = true },
        ),
        ToolItem(
            icon = Icons.Rounded.Visibility,
            titleResId = R.string.tools_modify_dpi,
            descResId = R.string.tools_modify_dpi_desc,
            onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val result = NativeAdbService.shell("wm density").trim()
                        val lines = result.lineSequence().toList()
                        val physical = lines.firstOrNull { it.contains("Physical density:") }
                            ?.substringAfter("Physical density:")?.trim() ?: ""
                        val override = lines.firstOrNull { it.contains("Override density:") }
                            ?.substringAfter("Override density:")?.trim() ?: ""
                        defaultDpiValue = physical
                        currentDpiValue = override.ifEmpty { physical }
                    } catch (_: Exception) {
                        defaultDpiValue = ""
                        currentDpiValue = ""
                    }
                    showDpiDialog = true
                }
            },
        ),
        ToolItem(
            icon = Icons.Rounded.Devices,
            titleResId = R.string.tools_modify_resolution,
            descResId = R.string.tools_modify_resolution_desc,
            onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val result = NativeAdbService.shell("wm size").trim()
                        val lines = result.lineSequence().toList()
                        val physical = lines.firstOrNull { it.contains("Physical size:") }
                            ?.substringAfter("Physical size:")?.trim() ?: ""
                        val override = lines.firstOrNull { it.contains("Override size:") }
                            ?.substringAfter("Override size:")?.trim() ?: ""
                        defaultResolutionValue = physical
                        currentResolutionValue = override.ifEmpty { physical }
                    } catch (_: Exception) {
                        defaultResolutionValue = ""
                        currentResolutionValue = ""
                    }
                    showResolutionDialog = true
                }
            },
        ),
        ToolItem(
            icon = Icons.Rounded.Android,
            titleResId = R.string.tools_activate_app,
            descResId = R.string.tools_activate_app_desc,
            onClick = { showActivateDialog = true },
        ),
        ToolItem(
            icon = Icons.Rounded.Memory,
            titleResId = R.string.tools_process_manager,
            descResId = R.string.tools_process_manager_desc,
            onClick = { showProcessSheet = true },
        ),
        ToolItem(
            icon = Icons.Rounded.Apps,
            titleResId = R.string.tools_app_manager,
            descResId = R.string.tools_app_manager_desc,
            onClick = { navigator.push(RootScreen.AppManager) },
        ),
        ToolItem(
            icon = Icons.Rounded.SettingsEthernet,
            titleResId = R.string.tools_wireless_adb,
            descResId = R.string.tools_wireless_adb_desc,
            onClick = { if (!wirelessAdbEnabled) enableWirelessAdb() },
        ),
        ToolItem(
            icon = Icons.Rounded.Info,
            titleResId = R.string.tools_device_info,
            descResId = R.string.tools_device_info_desc,
            onClick = { showDeviceInfo = true },
        ),
    )

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = stringResource(R.string.tools_title),
                    color = if (blurActive) Color.Transparent else colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(localSnackbarHostState) },
    ) { padding ->
        Box(
            modifier = if (blurActive) Modifier
                .fillMaxSize()
                .padding(padding)
                .layerBackdrop(blurBackdrop)
            else Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = UiSpacing.PageHorizontal,
                    vertical = UiSpacing.PageVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.PageItem),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            tools.forEach { tool ->
                if (tool.titleResId == R.string.tools_wireless_adb) {
                    SwitchPreference(
                        checked = wirelessAdbEnabled,
                        onCheckedChange = { if (!wirelessAdbEnabled && !wirelessAdbChecking) enableWirelessAdb() },
                        title = stringResource(tool.titleResId),
                        summary = stringResource(tool.descResId),
                        startAction = {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = colorScheme.primary,
                            )
                        },
                    )
                } else {
                    ArrowPreference(
                        title = stringResource(tool.titleResId),
                        summary = stringResource(tool.descResId),
                        startAction = {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = colorScheme.primary,
                            )
                        },
                        onClick = {
                            haptic.contextClick()
                            tool.onClick()
                        },
                    )
                }
            }
            }
        }
    }
        }
    }

    ScreenshotResultDialog(
            show = showScreenshotDialog,
            file = screenshotCacheFile,
            onCancel = {
                screenshotCacheFile?.delete()
                screenshotCacheFile = null
                showScreenshotDialog = false
            },
            onSave = {
                screenshotCacheFile?.let { cache ->
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val dest = File(downloadDir, cache.name)
                    cache.copyTo(dest, overwrite = true)
                    cache.delete()
                    AppRuntime.snackbar(R.string.tools_screenshot_saved)
                }
                screenshotCacheFile = null
                showScreenshotDialog = false
            },
            onOpen = {
                screenshotCacheFile?.let { cache ->
                    val authority = "${context.packageName}.fileprovider"
                    val uri = FileProvider.getUriForFile(context, authority, cache)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/png")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                showScreenshotDialog = false
            },
        )

        RebootDialog(
            show = showRebootDialog,
            onDismiss = { showRebootDialog = false },
            onReboot = { mode ->
                showRebootDialog = false
                reboot(mode)
            },
        )

        ScreenStandbyDialog(
            show = showScreenStandby,
            onDismiss = { showScreenStandby = false },
            onAction = { action -> performScreenControl(action) },
        )

        DpiDialog(
            show = showDpiDialog,
            defaultDpi = defaultDpiValue,
            currentDpi = currentDpiValue,
            onDismiss = { showDpiDialog = false },
            onConfirm = { dpi ->
                showDpiDialog = false
                modifyDpi(dpi)
            },
            onReset = {
                showDpiDialog = false
                resetDpi()
            },
        )

        ResolutionDialog(
            show = showResolutionDialog,
            defaultResolution = defaultResolutionValue,
            currentResolution = currentResolutionValue,
            onDismiss = { showResolutionDialog = false },
            onConfirm = { w, h ->
                showResolutionDialog = false
                modifyResolution(w, h)
            },
            onReset = {
                showResolutionDialog = false
                resetResolution()
            },
        )

        ActivateAppDialog(
            show = showActivateDialog,
            onDismiss = { showActivateDialog = false },
            onActivate = { app ->
                showActivateDialog = false
                scope.launch(Dispatchers.IO) {
                    val connected = try {
                        NativeAdbService.isConnected()
                    } catch (_: Exception) { false }
                    if (!connected) {
                        AppRuntime.snackbar(R.string.tools_not_connected)
                        return@launch
                    }
                    try {
                        AppRuntime.snackbar(R.string.tools_activate_executing, app.name)
                        for (cmd in app.activateCommands) {
                            val output = NativeAdbService.shell(cmd)
                            if (output.contains("No such file", ignoreCase = true) ||
                                output.contains("Permission denied", ignoreCase = true) ||
                                output.contains("not found", ignoreCase = true) ||
                                output.contains("error", ignoreCase = true)
                            ) {
                                AppRuntime.snackbar(R.string.tools_activate_failed, "${app.name}: $output")
                                return@launch
                            }
                        }
                        AppRuntime.snackbar(R.string.tools_activate_success, app.name)
                    } catch (e: Exception) {
                        AppRuntime.snackbar(R.string.tools_activate_failed, "${app.name}: ${e.message}")
                    }
                }
            },
        )

        ProcessManagerSheet(
            show = showProcessSheet,
            onDismiss = { showProcessSheet = false },
        )

        if (showDeviceInfo) {
            DeviceInfoSheet(
                show = true,
                onDismiss = { showDeviceInfo = false },
            )
        }

        AdbDisconnectedDialog(
            show = showAdbDisconnected,
            onDismiss = { showAdbDisconnected = false },
            onReconnect = {
                showAdbDisconnected = false
                onNavigateToDeviceTab()
            },
        )
    }
}

@Composable
fun AdbDisconnectedDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onReconnect: () -> Unit,
) {
    if (!show) return
    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_adb_disconnected_title),
        summary = stringResource(R.string.tools_adb_disconnected_msg),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.tools_adb_reconnect),
                onClick = onReconnect,
                modifier = Modifier.weight(1f),
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun ScreenshotResultDialog(
    show: Boolean,
    file: File?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
) {
    if (!show) return

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_screenshot),
        summary = file?.name ?: "",
        onDismissRequest = onCancel,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.tools_screenshot_save),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.tools_screenshot_open),
                onClick = onOpen,
                modifier = Modifier.weight(1f),
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun RebootDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onReboot: (String) -> Unit,
) {
    if (!show) return

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_advanced_reboot),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium)) {
            RebootOption(
                text = stringResource(R.string.tools_reboot_normal),
                onClick = { onReboot("") },
            )
            RebootOption(
                text = stringResource(R.string.tools_reboot_recovery),
                onClick = { onReboot("recovery") },
            )
            RebootOption(
                text = stringResource(R.string.tools_reboot_fastboot),
                onClick = { onReboot("bootloader") },
            )
        }
    }
}

@Composable
private fun RebootOption(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ScreenStandbyDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
) {
    if (!show) return
    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_screen_standby),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium)) {
            TextButton(
                text = stringResource(R.string.tools_screen_standby_turn_off),
                onClick = { onAction("off") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = stringResource(R.string.tools_screen_standby_wake_up),
                onClick = { onAction("on") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DpiDialog(
    show: Boolean,
    defaultDpi: String,
    currentDpi: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit,
) {
    if (!show) return

    var dpi by remember { mutableStateOf("") }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_modify_dpi),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.padding(UiSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            if (defaultDpi.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_dpi_default, defaultDpi),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            if (currentDpi.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_dpi_current, currentDpi),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            SuperTextField(
                value = dpi,
                onValueChange = { dpi = it.filter { c -> c.isDigit() } },
                label = stringResource(R.string.tools_dpi_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.tools_reset_resolution),
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    onClick = { if (dpi.isNotBlank()) onConfirm(dpi) },
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                    enabled = dpi.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun ResolutionDialog(
    show: Boolean,
    defaultResolution: String,
    currentResolution: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onReset: () -> Unit,
) {
    if (!show) return

    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_modify_resolution),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.padding(UiSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            if (defaultResolution.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_resolution_default, defaultResolution),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            if (currentResolution.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_resolution_current, currentResolution),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                SuperTextField(
                    value = width,
                    onValueChange = { width = it.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.tools_resolution_width),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SuperTextField(
                    value = height,
                    onValueChange = { height = it.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.tools_resolution_height),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.tools_reset_resolution),
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    onClick = { if (width.isNotBlank() && height.isNotBlank()) onConfirm(width, height) },
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                    enabled = width.isNotBlank() && height.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun ActivateAppDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onActivate: (ActivatableApp) -> Unit,
) {
    if (!show) return

    val scope = rememberCoroutineScope()
    var detectedApps by remember { mutableStateOf<List<ActivatableApp>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<ActivatableApp?>(null) }

    val knownApps = listOf(
        ActivatableApp(
            "Shizuku",
            "moe.shizuku.privileged.api",
            listOf("sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh")
        ),
        ActivatableApp(
            "冰箱",
            "com.catchingnow.icebox",
            listOf("sh /sdcard/Android/data/com.catchingnow.icebox/files/start.sh")
        ),
        ActivatableApp(
            "黑域",
            "me.piebridge.brevent",
            listOf("sh /data/data/me.piebridge.brevent/brevent.sh")
        ),
        ActivatableApp(
            "小黑屋",
            "web1n.stopapp",
            listOf("sh /storage/emulated/0/Android/data/web1n.stopapp/files/starter.sh")
        ),
    )

    LaunchedEffect(show) {
        if (show) {
            loading = true
            selectedApp = null
            scope.launch(Dispatchers.IO) {
                try {
                    val result = NativeAdbService.shell("pm list packages")
                    val installed = result.lineSequence()
                        .map { it.removePrefix("package:").trim() }
                        .toSet()
                    val detected = knownApps.filter { installed.contains(it.packageName) }
                    withContext(Dispatchers.Main) {
                        detectedApps = detected
                        loading = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        detectedApps = emptyList()
                        loading = false
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_activate_app),
        summary = stringResource(R.string.tools_activate_select),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium)) {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.text_loading),
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else if (detectedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tools_activate_no_apps),
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Small)) {
                    detectedApps.forEach { app ->
                        val isSelected = app == selectedApp
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            pressFeedbackType = PressFeedbackType.Sink,
                            showIndication = true,
                            onClick = {
                                selectedApp = if (isSelected) null else app
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = app.name,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                // 固定宽度占位，避免选中/取消时布局变化
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = MiuixIcons.Basic.Check,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    onClick = {
                        val app = selectedApp
                        if (app != null) {
                            onActivate(app)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                    enabled = selectedApp != null,
                )
            }
        }
    }
}

data class AppProcessInfo(
    val appName: String,
    val packageName: String,
    val pid: String,
    val memoryKB: Long,
    val isSystem: Boolean,
    val iconBase64: String? = null,
)

private fun formatMemory(kb: Long): String {
    return when {
        kb >= 1048576 -> String.format("%.1f GB", kb / 1048576.0)
        kb >= 1024 -> String.format("%.1f MB", kb / 1024.0)
        else -> "$kb KB"
    }
}

@Composable
private fun ProcessManagerSheet(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val scope = rememberCoroutineScope()
    var processes by remember { mutableStateOf<List<AppProcessInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var pendingKill by remember { mutableStateOf<AppProcessInfo?>(null) }
    var hideSystemApps by rememberSaveable { mutableStateOf(false) }

    val loadProcesses: () -> Unit = {
        loading = true
        scope.launch(Dispatchers.IO) {
            try {
                val rawItems = mutableListOf<Triple<String, String, Long>>()
                val seen = mutableSetOf<String>()
                val psResult = NativeAdbService.shell("ps -A")
                for (line in psResult.lineSequence().drop(1)) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 9) continue
                    val pid = parts[1]
                    val rss = parts[4].toLongOrNull() ?: 0L
                    val name = parts[8]
                    if (name.contains(".") && !name.startsWith("/") && !name.startsWith("[")) {
                        if (seen.add(name)) {
                            rawItems.add(Triple(name, pid, rss))
                        }
                    }
                }
                if (rawItems.isEmpty()) {
                    val psResult2 = NativeAdbService.shell("ps -A -o PID,RSS,NAME 2>/dev/null")
                    for (line in psResult2.lineSequence().drop(1)) {
                        val parts = line.trim().split(Regex("\\s+"), limit = 3)
                        if (parts.size < 3) continue
                        val pid = parts[0]
                        val rss = parts[1].toLongOrNull() ?: continue
                        val name = parts[2]
                        if (name.contains(".") && !name.startsWith("/") && !name.startsWith("[")) {
                            if (seen.add(name)) {
                                rawItems.add(Triple(name, pid, rss))
                            }
                        }
                    }
                }
                val labelMap = mutableMapOf<String, String>()
                val iconMap = mutableMapOf<String, String?>()
                val systemMap = mutableMapOf<String, Boolean>()
                if (rawItems.isNotEmpty()) {
                    val allPkgs = rawItems.map { (pkg, _, _) -> pkg }.toSet()
                    try {
                        val fetchIcons = appSettings.bundleState.value.showAppIcons
                        val helperResults = AppManagerService.fetchLabelsViaHelper(allPkgs, fetchIcons = fetchIcons)
                        for ((pkg, hr) in helperResults) {
                            labelMap[pkg] = hr.label
                            iconMap[pkg] = hr.iconBase64
                        }
                    } catch (_: Exception) { }
                    val allInstalledResult = NativeAdbService.shell("pm list packages")
                    val allInstalled = allInstalledResult.lineSequence()
                        .map { it.removePrefix("package:").trim() }
                        .toSet()
                    for ((pkg, _, _) in rawItems) {
                        if (pkg !in allInstalled) {
                            if (pkg !in labelMap) labelMap[pkg] = pkg
                            systemMap[pkg] = false
                            continue
                        }
                        if (pkg !in systemMap || pkg !in labelMap) {
                            try {
                                val dumpResult = NativeAdbService.shell("dumpsys package $pkg")
                                var label = ""
                                var isSys = false
                                for (line in dumpResult.lineSequence()) {
                                    val trimmed = line.trim()
                                    if (label.isEmpty() && trimmed.startsWith("application-label-zh_CN:")) {
                                        label = trimmed.substringAfter("application-label-zh_CN:").trim()
                                    }
                                    if (label.isEmpty() && trimmed.startsWith("application-label-zh:")) {
                                        label = trimmed.substringAfter("application-label-zh:").trim()
                                    }
                                    if (label.isEmpty() && trimmed.startsWith("application-label:")) {
                                        label = trimmed.substringAfter("application-label:").trim()
                                    }
                                    if (!isSys && (trimmed.startsWith("pkgFlags=") || trimmed.startsWith("flags="))) {
                                        isSys = trimmed.contains("SYSTEM", ignoreCase = true)
                                    }
                                    if (label.isNotEmpty() && isSys) break
                                }
                                if (pkg !in labelMap) labelMap[pkg] = if (label.isNotEmpty()) label else pkg
                                systemMap[pkg] = isSys
                            } catch (_: Exception) {
                                if (pkg !in labelMap) labelMap[pkg] = pkg
                                systemMap[pkg] = false
                            }
                        }
                    }
                }
                val parsed = rawItems.map { (pkg, pid, rss) ->
                    AppProcessInfo(
                        appName = labelMap[pkg] ?: pkg,
                        packageName = pkg,
                        pid = pid,
                        memoryKB = rss,
                        isSystem = systemMap[pkg] ?: false,
                        iconBase64 = iconMap[pkg],
                    )
                }.sortedByDescending { it.memoryKB }
                withContext(Dispatchers.Main) {
                    processes = parsed
                    loading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    processes = emptyList()
                    loading = false
                }
            }
        }
    }

    LaunchedEffect(show) {
        if (show) {
            loadProcesses()
        }
    }

    val killProcess: (AppProcessInfo) -> Unit = { process ->
        if (process.isSystem) {
            pendingKill = process
        } else {
            scope.launch(Dispatchers.IO) {
                try {
                    NativeAdbService.shell("am force-stop ${process.packageName}")
                    AppRuntime.snackbar(R.string.tools_process_killed, process.appName)
                    withContext(Dispatchers.Main) {
                        processes = processes.filter { it.pid != process.pid }
                    }
                } catch (e: Exception) {
                    AppRuntime.snackbar(R.string.tools_process_kill_failed, e.message ?: "")
                }
            }
        }
    }

    SystemAppConfirmDialog(
        show = pendingKill != null,
        app = pendingKill,
        onConfirm = {
            val app = pendingKill ?: return@SystemAppConfirmDialog
            pendingKill = null
            scope.launch(Dispatchers.IO) {
                try {
                    NativeAdbService.shell("am force-stop ${app.packageName}")
                    AppRuntime.snackbar(R.string.tools_process_killed, app.appName)
                    withContext(Dispatchers.Main) {
                        processes = processes.filter { it.pid != app.pid }
                    }
                } catch (e: Exception) {
                    AppRuntime.snackbar(R.string.tools_process_kill_failed, e.message ?: "")
                }
            }
        },
        onDismiss = { pendingKill = null },
    )

    OverlayBottomSheet(
        show = true,
        title = stringResource(R.string.tools_process_manager),
        onDismissRequest = onDismiss,
    ) {
        if (!loading && processes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.ContentHorizontal, vertical = UiSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tools_process_hide_system),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = hideSystemApps,
                    onCheckedChange = { hideSystemApps = it },
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
                    text = stringResource(R.string.tools_process_loading),
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        } else if (processes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.tools_process_empty),
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            val displayProcesses = if (hideSystemApps) processes.filter { !it.isSystem } else processes
            if (displayProcesses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tools_process_empty),
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.Tiny),
                ) {
                    items(displayProcesses, key = { it.pid }) { process ->
                        AppProcessRow(
                            process = process,
                            onKill = { killProcess(process) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }
}

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

@Composable
private fun AppProcessRow(
    process: AppProcessInfo,
    onKill: () -> Unit,
) {
    val showIcons = appSettings.bundleState.value.showAppIcons
    val bitmap = remember(process.iconBase64, showIcons) {
        if (showIcons && process.iconBase64 != null) {
            try {
                val bytes = Base64.decode(process.iconBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.ContentHorizontal, vertical = UiSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = process.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
            } else {
                val appColor = remember(process.packageName) {
                    IconColors[Math.abs(process.packageName.hashCode()) % IconColors.size]
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(appColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = process.appName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(UiSpacing.ContentHorizontal))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = process.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = process.packageName,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small)) {
                    Text(
                        text = "PID: ${process.pid}",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = formatMemory(process.memoryKB),
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    if (process.isSystem) {
                        Text(
                            text = stringResource(R.string.tools_app_system),
                            fontSize = 11.sp,
                            color = Color(0xFFFF8F00),
                        )
                    }
                }
            }
            TextButton(
                text = stringResource(R.string.tools_process_kill),
                onClick = onKill,
            )
        }
    }
}

@Composable
private fun SystemAppConfirmDialog(
    show: Boolean,
    app: AppProcessInfo?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show || app == null) return

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_process_system_confirm_title),
        summary = stringResource(R.string.tools_process_system_confirm_msg, app.appName),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.button_confirm),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
