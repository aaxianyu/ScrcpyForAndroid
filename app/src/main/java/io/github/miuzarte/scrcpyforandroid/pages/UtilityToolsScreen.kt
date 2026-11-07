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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
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
import androidx.core.content.FileProvider
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.io.File
import java.io.IOException

private const val REMOTE_SHOT_DIR = "/data/local/tmp"
private const val SCREENSHOT_DIR = "screenshots"

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
