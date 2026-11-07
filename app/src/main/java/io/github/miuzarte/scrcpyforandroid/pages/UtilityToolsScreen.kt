package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.ui.SheetActionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import java.io.File
import java.io.IOException

private const val REMOTE_SHOT_DIR = "/data/local/tmp"
private const val SCREENSHOT_DIR = "screenshots"
private const val DENSITY_MIN = 80
private const val DENSITY_MAX = 640
// 重启/关机必然切断连接：发出命令后最多等这么久就必须让用户去看设备，回读永远等不到有效结果
private const val REBOOT_RESULT_WAIT_MS = 3_000L

private fun extractDensity(shellOutput: String, kind: String): String {
    val target = "$kind density:"
    return shellOutput.lineSequence()
        .firstOrNull { it.startsWith(target) }
        ?.removePrefix(target)?.trim().orEmpty()
}

@Composable
internal fun UtilityToolsScreen(
    scrollBehavior: ScrollBehavior,
) {
    val haptic = LocalHapticFeedback.current
    val navigator = LocalRootNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null

    // NavDisplay 里本页在 Home 之上渲染，Home 的 snackbar 宿主被本页遮挡，
    // 必须自注册一个，否则全部提示渲染在页面背后
    val snackHostState = remember { SnackbarHostState() }
    DisposableEffect(snackHostState) {
        val unregister = AppRuntime.registerSnackbarHostState(snackHostState)
        onDispose(unregister)
    }

    // SAF 选择器弹出时 Activity 可能被系统回收，rememberSaveable 才能在重建后捞回待保存的临时文件
    var pendingShot by rememberSaveable { mutableStateOf<File?>(null) }
    var showRebootDialog by remember { mutableStateOf(false) }
    // 弹窗列表里选中了哪个模式，等待二次确认；null 表示没有待确认的操作
    var pendingReboot by remember { mutableStateOf<RebootMode?>(null) }
    // 目标设备 SDK：null 表示还没查过或查询失败，此时软重启一律不展示
    var targetSdk by remember { mutableStateOf<Int?>(null) }
    var densitySheetOpen by remember { mutableStateOf(false) }
    var stockDensity by remember { mutableStateOf("") }
    var activeDensity by remember { mutableStateOf("") }

    val saveShotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri: Uri? ->
        val local = pendingShot
        pendingShot = null
        if (uri == null || local == null) {
            // 用户放弃保存或没有待处理截图：清理缓存
            local?.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                resolver.openOutputStream(uri)?.use { os ->
                    local.inputStream().use { input -> input.copyTo(os) }
                } ?: throw IOException(resolver.toString())
                local.delete()
            }
                .onSuccess { AppRuntime.snackbar(R.string.tools_screenshot_saved, local.name) }
                .onFailure {
                    local.delete()
                    AppRuntime.snackbar(R.string.tools_screenshot_failed, it.message ?: "")
                }
        }
    }

    fun reboot(mode: RebootMode) {
        scope.launch {
            if (!NativeAdbService.isConnected()) {
                AppRuntime.snackbar(R.string.tools_not_connected)
                return@launch
            }
            showRebootDialog = false
            pendingReboot = null
            // 标题是资源 ID，直接当格式化参数会印出整数；必须先解析成本地化文案
            AppRuntime.snackbar(R.string.tools_reboot_executing, AppRuntime.stringResource(mode.titleResId))
            runCatching {
                NativeAdbService.shell(mode.shellCommand, REBOOT_RESULT_WAIT_MS)
            }.onFailure { AppRuntime.snackbar(R.string.tools_reboot_unconfirmed, AppRuntime.stringResource(mode.titleResId)) }
        }
    }

    fun applyDensity(value: String) {
        scope.launch {
            densitySheetOpen = false
            // shell 传输成功不代表命令生效：设完回读，Override 对得上才认成功
            runCatching {
                withContext(Dispatchers.IO) {
                    NativeAdbService.shell("wm density $value")
                    parseWmSetting(NativeAdbService.shell("wm density"), "density")?.override == value
                }
            }.onSuccess { effective ->
                if (effective) {
                    activeDensity = value
                    AppRuntime.snackbar(R.string.tools_density_applied, value)
                } else {
                    AppRuntime.snackbar(R.string.tools_density_failed, AppRuntime.stringResource(R.string.tools_apply_not_effective))
                }
            }.onFailure { AppRuntime.snackbar(R.string.tools_density_failed, it.message ?: "") }
        }
    }

    fun restoreDensity() {
        scope.launch {
            densitySheetOpen = false
            // reset 生效的判据：回读有效（Physical 在场且格式合法）且 Override 行消失
            runCatching {
                withContext(Dispatchers.IO) {
                    NativeAdbService.shell("wm density reset")
                    isWmRestored(NativeAdbService.shell("wm density"), "density")
                        ?: throw IOException(AppRuntime.stringResource(R.string.tools_readback_unconfirmed))
                }
            }.onSuccess { effective ->
                if (effective) {
                    activeDensity = stockDensity
                    AppRuntime.snackbar(R.string.tools_density_restored, stockDensity)
                } else {
                    AppRuntime.snackbar(R.string.tools_density_failed, AppRuntime.stringResource(R.string.tools_apply_not_effective))
                }
            }.onFailure { AppRuntime.snackbar(R.string.tools_density_failed, it.message ?: "") }
        }
    }

    val tools = listOf(
        ToolItem(
            icon = Icons.Rounded.CameraAlt,
            titleResId = R.string.tools_screenshot,
            descResId = R.string.tools_screenshot_desc,
            onClick = {
                scope.launch {
                    if (!NativeAdbService.isConnected()) {
                        AppRuntime.snackbar(R.string.tools_not_connected)
                        return@launch
                    }
                    AppRuntime.snackbar(R.string.tools_screenshot_executing)
                    val stamp = "img_${System.currentTimeMillis()}"
                    val remote = File(REMOTE_SHOT_DIR, "$stamp.png").path
                    val outcome = runCatching {
                        withContext(Dispatchers.IO) {
                            NativeAdbService.shell("screencap -p $remote")
                            val bytes = NativeAdbService.pull(remote)
                            // 写进专用子目录：file_paths.xml 只声明该目录，
                            // FileProvider 的可共享范围不扩大到整个 cache
                            val shotDir = File(context.cacheDir, SCREENSHOT_DIR).apply { mkdirs() }
                            File(shotDir, "$stamp.png").apply { writeBytes(bytes) }
                        }
                    }
                    // 无论成功失败都清理设备端临时文件
                    runCatching {
                        withContext(Dispatchers.IO) { NativeAdbService.shell("rm -f $remote") }
                    }
                    outcome
                        .onSuccess { pendingShot = it }
                        .onFailure { AppRuntime.snackbar(R.string.tools_screenshot_failed, it.message ?: "") }
                }
            },
        ),
        ToolItem(
            icon = Icons.Rounded.RestartAlt,
            titleResId = R.string.tools_advanced_reboot,
            descResId = R.string.tools_advanced_reboot_desc,
            onClick = {
                scope.launch {
                    if (!NativeAdbService.isConnected()) {
                        AppRuntime.snackbar(R.string.tools_not_connected)
                        return@launch
                    }
                    // 用户态软重启要求 Android 11+：开列表前查一次目标 SDK 并缓存，
                    // 查询失败按不支持处理，老设备不会看到一个点了无效的选项
                    if (targetSdk == null) {
                        targetSdk = runCatching {
                            withContext(Dispatchers.IO) {
                                NativeAdbService.shell("getprop ro.build.version.sdk")
                                    .trim().toIntOrNull()
                            }
                        }.getOrNull()
                    }
                    showRebootDialog = true
                }
            },
        ),
        ToolItem(
            icon = Icons.Rounded.Visibility,
            titleResId = R.string.tools_screen_density,
            descResId = R.string.tools_screen_density_desc,
            onClick = {
                scope.launch {
                    if (!NativeAdbService.isConnected()) {
                        AppRuntime.snackbar(R.string.tools_not_connected)
                        return@launch
                    }
                    // 取值失败就不开弹窗，避免用户对着空表单操作
                    val loaded = runCatching {
                        withContext(Dispatchers.IO) {
                            val output = NativeAdbService.shell("wm density")
                            val stock = extractDensity(output, "Physical")
                            val override = extractDensity(output, "Override")
                            stockDensity = stock
                            activeDensity = override.ifBlank { stock }
                        }
                    }.onFailure { AppRuntime.snackbar(R.string.tools_density_read_failed, it.message ?: "") }
                    if (loaded.isSuccess) densitySheetOpen = true
                }
            },
        ),
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackHostState) },
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = stringResource(R.string.tools_title),
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptic.contextClick()
                                navigator.pop()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { pagePadding ->
        Box(
            modifier =
                if (blurActive) Modifier.layerBackdrop(blurBackdrop)
                else Modifier,
        ) {
            LazyColumn(
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = UiSpacing.PageBottom,
            ) {
                item {
                    Card {
                        Column {
                            tools.forEach { tool ->
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

            // 对话框必须位于 Scaffold 内部: OverlayDialog 默认 renderInRootScaffold=true,
            // 在 Scaffold 外部时会落入 LocalDialogStates 的默认空列表, 没有任何 MiuixPopupHost 渲染它
            val shot = pendingShot
            if (shot != null) {
                ScreenshotResultDialog(
                    fileName = shot.name,
                    onCancel = {
                        shot.delete()
                        pendingShot = null
                    },
                    onSave = { saveShotLauncher.launch(shot.name) },
                    onOpen = {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            shot,
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "image/png")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure { AppRuntime.snackbar(R.string.tools_screenshot_failed, it.message ?: "") }
                        pendingShot = null
                    },
                )
            }

            if (showRebootDialog) {
                RebootDialog(
                    showSoftReboot = (targetSdk ?: 0) >= 30,
                    onDismiss = { showRebootDialog = false },
                    // 先收起义确认弹窗，确认后再真正执行
                    onReboot = {
                        showRebootDialog = false
                        pendingReboot = it
                    },
                )
            }

            pendingReboot?.let { mode ->
                RebootConfirmDialog(
                    mode = mode,
                    // 取消确认回到动作列表，让用户改选别的模式而不是从头再来
                    onDismiss = {
                        pendingReboot = null
                        showRebootDialog = true
                    },
                    onConfirm = { reboot(mode) },
                )
            }

            if (densitySheetOpen) {
                DensitySheet(
                    stockDensity = stockDensity,
                    activeDensity = activeDensity,
                    onDismiss = { densitySheetOpen = false },
                    onApply = ::applyDensity,
                    onRestore = ::restoreDensity,
                )
            }
        }
    }
}

@Composable
private fun DensitySheet(
    stockDensity: String,
    activeDensity: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onRestore: () -> Unit,
) {
    var input by remember { mutableStateOf("") }

    // 极端密度值会让设备 UI 几乎不可用，限制在常见安全区间
    val inSafeRange = input.toIntOrNull()?.let { it in DENSITY_MIN..DENSITY_MAX } ?: false

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_screen_density),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
            if (stockDensity.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_density_factory, stockDensity),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            if (activeDensity.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_density_now, activeDensity),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            SuperTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit) },
                label = stringResource(R.string.tools_density_hint, DENSITY_MIN, DENSITY_MAX),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    text = stringResource(R.string.tools_density_restore),
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    // 统一走规范化值，避免前导零输入与回读不一致
                    onClick = { onApply(normalizeToolValue(input, DENSITY_MIN..DENSITY_MAX) ?: return@TextButton) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = inSafeRange,
                )
            }
        }
    }
}

@Composable
private fun ScreenshotResultDialog(
    fileName: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_screenshot),
        summary = fileName,
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
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

private data class ToolItem(
    val icon: ImageVector,
    val titleResId: Int,
    val descResId: Int,
    val onClick: () -> Unit,
)

private enum class RebootMode(val shellCommand: String) {
    Normal("reboot"),
    // 用户态软重启只重启 Android 运行时，需 Android 11+ 且 init 支持
    Soft("setprop sys.powerctl reboot,userspace"),
    Shutdown("setprop sys.powerctl shutdown"),
    Recovery("reboot recovery"),
    Fastboot("reboot bootloader"),
}

@Composable
private fun RebootDialog(
    showSoftReboot: Boolean,
    onDismiss: () -> Unit,
    onReboot: (RebootMode) -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_advanced_reboot),
        // 水平内缩 10dp / 垂直 24dp，与应用管理单击列表项弹出的动作面板一致
        insideMargin = DpSize(10.dp, 24.dp),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
    ) {
        Column(
            modifier = Modifier,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // 软重启由调用方按目标设备版本决定，其余选项所有设备都支持
            RebootMode.entries.filter { it != RebootMode.Soft || showSoftReboot }.forEach { mode ->
                SheetActionRow(
                    label = stringResource(mode.titleResId),
                    enabled = true,
                    onClick = { onReboot(mode) },
                )
            }
            // 末行「取消」：与应用管理动作面板同一组件与配色，点它等于什么都不做
            Spacer(Modifier.height(16.dp))
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                textStyle = textStyles.button.copy(fontWeight = FontWeight.Medium),
            )
            // 面板底部留白由 dialog 的垂直 insideMargin（24dp）给出
        }
    }
}

// 高级重启的每一步都会立即生效，列表里点完直接执行没有反悔机会，所以再加一道确认
@Composable
private fun RebootConfirmDialog(
    mode: RebootMode,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_reboot_confirm_title),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium)) {
            Text(
                text = stringResource(R.string.tools_reboot_confirm, stringResource(mode.titleResId)),
                color = colorScheme.onSurfaceVariantSummary,
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
                    text = stringResource(R.string.button_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val RebootMode.titleResId: Int
    get() = when (this) {
        RebootMode.Normal -> R.string.tools_reboot_normal
        RebootMode.Soft -> R.string.tools_reboot_soft
        RebootMode.Shutdown -> R.string.tools_reboot_shutdown
        RebootMode.Recovery -> R.string.tools_reboot_recovery
        RebootMode.Fastboot -> R.string.tools_reboot_fastboot
    }
