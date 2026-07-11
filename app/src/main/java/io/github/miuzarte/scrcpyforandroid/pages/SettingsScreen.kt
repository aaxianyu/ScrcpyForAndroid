package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.LockscreenPasswordActivity
import io.github.miuzarte.scrcpyforandroid.MainActivity
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.DirectAdbTransport
import io.github.miuzarte.scrcpyforandroid.scaffolds.ArrowSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppUpdateChecker
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings.FullscreenVirtualButtonDock
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.adbClientData
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.MonetKeyColorOptions
import io.github.miuzarte.scrcpyforandroid.ui.confirm
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import java.io.File
import kotlin.math.roundToInt
import android.provider.Settings as AndroidSettings

private val languages = listOf(
    R.string.language_follow_system to "",
    R.string.language_english to "en",
    R.string.language_chinese to "zh",
)
private const val TERMINAL_FONT_RELATIVE_PATH = "terminal/font.ttf"
private val monetPaletteStyleOptions = ThemePaletteStyle.entries.map { it.name }
private val monetColorSpecOptions = ThemeColorSpec.entries.map { it.name }

suspend fun clearTerminalFont(context: Context) =
    withContext(Dispatchers.IO) {
        val target = File(
            context.filesDir,
            TERMINAL_FONT_RELATIVE_PATH,
        )
        target.exists() && target.delete()
    }

suspend fun readTextFromUri(context: Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot open selected file")
    }

fun queryAdbKeyDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) cursor.getString(columnIndex)
            else null
        }

@Composable
fun SettingsScreen(
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    onOpenReorderDevices: () -> Unit,
) {
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = stringResource(R.string.settings_title),
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
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
            SettingsPage(
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = bottomInnerPadding,
                onOpenReorderDevices = onOpenReorderDevices,
            )
        }
    }
}

@Composable
fun SettingsPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    onOpenReorderDevices: () -> Unit,
) {
    val context = LocalContext.current
    val updateState by AppUpdateChecker.state.collectAsState()

    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val scope = rememberCoroutineScope()

    val haptic = LocalHapticFeedback.current
    val navigator = LocalRootNavigator.current
    val serverPicker = LocalServerPicker.current
    val terminalFontPicker = LocalTerminalFontPicker.current
    val isScrcpyStreaming = AppRuntime.scrcpy?.isStarted() == true

    val acBundle by adbClientData.bundleState.collectAsState()

    val asBundleShared by appSettings.bundleState.collectAsState()
    val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
    var asBundle by rememberSaveable(asBundleShared) { mutableStateOf(asBundleShared) }
    val asBundleLatest by rememberUpdatedState(asBundle)
    LaunchedEffect(asBundleShared) {
        if (asBundle != asBundleShared)
            asBundle = asBundleShared
    }
    LaunchedEffect(asBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (asBundle != asBundleSharedLatest)
            appSettings.saveBundle(asBundle)
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                appSettings.saveBundle(asBundleLatest)
            }
        }
    }

    val themeItems = AppSettings.ThemeModes.baseOptions.map { stringResource(it.labelResId) }

    val fullscreenVirtualButtonDock = remember(asBundle.fullscreenVirtualButtonDock) {
        FullscreenVirtualButtonDock.fromStoredValue(asBundle.fullscreenVirtualButtonDock)
    }

    val customServerVersionShowInput = rememberSaveable(asBundle.customServerUri) {
        asBundle.customServerUri.isNotBlank()
    }
    var customServerVersionInput by rememberSaveable(asBundle.customServerVersion) {
        mutableStateOf(asBundle.customServerVersion)
    }
    var serverRemotePathInput by rememberSaveable(asBundle.serverRemotePath) {
        mutableStateOf(
            if (asBundle.serverRemotePath == AppSettings.SERVER_REMOTE_PATH.defaultValue) ""
            else asBundle.serverRemotePath,
        )
    }

    var adbKeyNameInput by rememberSaveable(asBundle.adbKeyName) {
        mutableStateOf(
            if (asBundle.adbKeyName == AppSettings.ADB_KEY_NAME.defaultValue) ""
            else asBundle.adbKeyName,
        )
    }

    val adbPrivateKeyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val fileName = queryAdbKeyDisplayName(context, uri)
                    ?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment.orEmpty()
                DirectAdbTransport.importPrivateKey(readTextFromUri(context, uri), fileName)
            }.onSuccess {
                AppRuntime.snackbar(R.string.pref_adb_private_key_imported, it.fingerprint)
            }.onFailure { e ->
                AppRuntime.snackbar(
                    R.string.pref_adb_key_import_failed,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }
    val adbPublicKeyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val fileName = queryAdbKeyDisplayName(context, uri)
                    ?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment.orEmpty()
                DirectAdbTransport.importPublicKey(readTextFromUri(context, uri), fileName)
            }.onSuccess {
                AppRuntime.snackbar(R.string.pref_adb_public_key_imported_snackbar, it.fingerprint)
            }.onFailure { e ->
                AppRuntime.snackbar(
                    R.string.pref_adb_key_import_failed,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }
    val adbPrivateKeyExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val privKeyB64 = acBundle.importedPrivateKey
                    .ifBlank { acBundle.rsaPrivateKey }
                if (privKeyB64.isBlank()) {
                    Toast.makeText(context, "没有可导出的私钥", Toast.LENGTH_SHORT).show()
                    return@runCatching
                }
                val keyDer = android.util.Base64.decode(privKeyB64, android.util.Base64.DEFAULT)
                val b64Encoded = android.util.Base64.encodeToString(keyDer, android.util.Base64.NO_WRAP)
                val keyPem = buildString {
                    appendLine("-----BEGIN PRIVATE KEY-----")
                    b64Encoded.chunked(64) { appendLine(it) }
                    appendLine("-----END PRIVATE KEY-----")
                }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(keyPem.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open output file")
            }.onSuccess {
                Toast.makeText(context, "私钥已导出", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                AppRuntime.snackbar(
                    R.string.pref_adb_key_import_failed,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }
    val adbPublicKeyExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val pubKeyB64 = acBundle.importedPublicKeyX509
                    .ifBlank { acBundle.rsaPublicKeyX509 }
                if (pubKeyB64.isBlank()) {
                    Toast.makeText(context, "没有可导出的公钥", Toast.LENGTH_SHORT).show()
                    return@runCatching
                }
                val keyDer = android.util.Base64.decode(pubKeyB64, android.util.Base64.DEFAULT)
                val b64Encoded = android.util.Base64.encodeToString(keyDer, android.util.Base64.NO_WRAP)
                val keyPem = buildString {
                    appendLine("-----BEGIN PUBLIC KEY-----")
                    b64Encoded.chunked(64) { appendLine(it) }
                    appendLine("-----END PUBLIC KEY-----")
                }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(keyPem.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open output file")
            }.onSuccess {
                Toast.makeText(context, "公钥已导出", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                AppRuntime.snackbar(
                    R.string.pref_adb_key_import_failed,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    var exportingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showResetKeyConfirm by rememberSaveable { mutableStateOf(false) }

    val exportPrivateKeyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    DirectAdbTransport.privateKey
                    val pem = DirectAdbTransport.getPrivateKeyPem()
                        ?: error("Private key not available")
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(pem.toByteArray())
                    } ?: error("Cannot open output stream")
                }
            }.onSuccess {
                AppRuntime.snackbar(R.string.pref_adb_key_exported_file)
            }.onFailure { e ->
                AppRuntime.snackbar(
                    R.string.pref_adb_key_export_failed,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    val exportPublicKeyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    DirectAdbTransport.publicKeyX509
                    val pem = DirectAdbTransport.getPublicKeyPem()
                        ?: error("Public key not available")
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(pem.toByteArray())
                    } ?: error("Cannot open output stream")
                }
            }.onSuccess {
                AppRuntime.snackbar(R.string.pref_adb_key_exported_file)
            }.onFailure { e ->
                AppRuntime.snackbar(
                    R.string.pref_adb_key_export_failed,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    val updateSummary = stringResource(R.string.pref_update_current, BuildConfig.VERSION_NAME) +
            when (val state = updateState) {
                AppUpdateChecker.State.Idle -> ""
                AppUpdateChecker.State.Checking -> stringResource(R.string.pref_update_checking)
                AppUpdateChecker.State.Error -> stringResource(R.string.pref_update_failed)

                is AppUpdateChecker.State.Ready -> when {
                    state.release.hasUpdate ->
                        stringResource(R.string.pref_update_found, state.release.latestVersion)

                    state.release.currentVersion == state.release.latestVersion.removePrefix("v")
                            || state.release.currentVersion == state.release.latestVersion ->
                        stringResource(R.string.pref_update_latest)

                    else -> stringResource(R.string.pref_update_newer, state.release.latestVersion)
                }
            }

    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    // 设置
    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        state = listState,
        bottomInnerPadding = bottomInnerPadding,
    ) {
        item {
            SectionSmallTitle(stringResource(R.string.section_theme))
            Card {
                OverlayDropdownPreference(
                    title = stringResource(R.string.pref_title_language),
                    summary = stringResource(R.string.pref_summary_language),
                    entries = listOf(
                        DropdownEntry(
                            items = languages.map { lang ->
                                DropdownItem(
                                    text = stringResource(lang.first),
                                    selected = lang.second == asBundle.languageTag,
                                    onClick = {
                                        asBundle = asBundle.copy(
                                            languageTag = lang.second,
                                        )
                                        MainActivity.setAppLanguageTag(context, lang.second)
                                    },
                                )
                            },
                        ),
                    ),
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.pref_title_appearance_mode),
                    summary = stringResource(R.string.pref_summary_appearance_mode),
                    items = themeItems,
                    selectedIndex = asBundle.themeBaseIndex
                        .coerceIn(0, AppSettings.ThemeModes.baseOptions.lastIndex),
                    onSelectedIndexChange = {
                        asBundle = asBundle.copy(
                            themeBaseIndex = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_monet),
                    summary = stringResource(R.string.pref_summary_monet),
                    checked = asBundle.monet,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            monet = it,
                        )
                    },
                )
                AnimatedVisibility(asBundle.monet) {
                    Column {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.pref_title_monet_key_color),
                            summary = stringResource(R.string.pref_summary_monet_key_color),
                            items = MonetKeyColorOptions,
                            selectedIndex = asBundle.monetSeedIndex
                                .coerceIn(0, MonetKeyColorOptions.lastIndex),
                            onSelectedIndexChange = {
                                asBundle = asBundle.copy(
                                    monetSeedIndex = it,
                                )
                            },
                        )
                    }
                }
                AnimatedVisibility(asBundle.monet && asBundle.monetSeedIndex > 0) {
                    Column {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.pref_title_monet_palette_style),
                            summary = stringResource(R.string.pref_summary_monet_palette_style),
                            items = monetPaletteStyleOptions,
                            selectedIndex = asBundle.monetPaletteStyle
                                .coerceIn(0, monetPaletteStyleOptions.lastIndex),
                            onSelectedIndexChange = {
                                asBundle = asBundle.copy(
                                    monetPaletteStyle = it,
                                )
                            },
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.pref_title_monet_color_spec),
                            summary = stringResource(R.string.pref_summary_monet_color_spec),
                            items = monetColorSpecOptions,
                            selectedIndex = asBundle.monetColorSpec
                                .coerceIn(0, monetColorSpecOptions.lastIndex),
                            onSelectedIndexChange = {
                                asBundle = asBundle.copy(
                                    monetColorSpec = it,
                                )
                            },
                        )
                    }
                }
                SwitchPreference(
                    title = stringResource(R.string.pref_title_blur),
                    summary = stringResource(R.string.pref_summary_blur),
                    checked = asBundle.blur,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            blur = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_floating_bottom_bar),
                    summary = stringResource(R.string.pref_summary_floating_bottom_bar),
                    checked = asBundle.floatingBottomBar,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            floatingBottomBar = it,
                        )
                    },
                )
                AnimatedVisibility(asBundle.floatingBottomBar && asBundle.blur) {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.pref_title_liquid_glass),
                            summary = stringResource(R.string.pref_summary_liquid_glass),
                            checked = asBundle.floatingBottomBar && asBundle.blur
                                    && asBundle.floatingBottomBarBlur,
                            onCheckedChange = {
                                asBundle = asBundle.copy(
                                    floatingBottomBarBlur = it,
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            SectionSmallTitle(stringResource(R.string.section_screen_mirroring))
            Card {
                SwitchPreference(
                    title = stringResource(R.string.pref_title_low_latency_audio),
                    summary = stringResource(R.string.pref_summary_low_latency_audio),
                    enabled = !isScrcpyStreaming,
                    checked = asBundle.lowLatency,
                    onCheckedChange = {
                        if (!isScrcpyStreaming)
                            asBundle = asBundle.copy(
                                lowLatency = it,
                            )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_debug_info),
                    summary = stringResource(R.string.pref_summary_debug_info),
                    checked = asBundle.fullscreenDebugInfo,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenDebugInfo = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_hide_simple_settings),
                    summary = stringResource(R.string.pref_summary_hide_simple_settings),
                    checked = asBundle.hideSimpleConfigItems,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            hideSimpleConfigItems = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_preview_card_on_top),
                    summary = stringResource(R.string.pref_summary_preview_card_on_top),
                    checked = asBundle.previewCardOnTop,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            previewCardOnTop = it,
                        )
                        if (it) AppRuntime.snackbar(R.string.pref_tips_preview_card_on_top)
                    },
                )
                ArrowSlider(
                    title = stringResource(R.string.pref_title_preview_card_height),
                    summary = stringResource(R.string.pref_summary_preview_card_height),
                    value = asBundle.devicePreviewCardHeightDp.toFloat(),
                    onValueChange = {
                        asBundle = asBundle.copy(
                            devicePreviewCardHeightDp =
                                it.roundToInt().coerceAtLeast(120),
                        )
                    },
                    valueRange = 160f..600f,
                    steps = 600 - 160 - 1,
                    unit = "dp",
                    displayFormatter = { it.roundToInt().toString() },
                    inputInitialValue = asBundle.devicePreviewCardHeightDp.toString(),
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 120f..UShort.MAX_VALUE.toFloat(),
                    onInputConfirm = { input ->
                        input.toIntOrNull()?.let {
                            asBundle = asBundle.copy(
                                devicePreviewCardHeightDp = it.coerceAtLeast(120),
                            )
                        }
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_preview_card_tap_fullscreen),
                    summary = stringResource(R.string.pref_summary_preview_card_tap_fullscreen),
                    checked = asBundle.previewCardTapToFullscreen,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            previewCardTapToFullscreen = it
                        )
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.pref_title_quick_device_sort),
                    summary = stringResource(R.string.pref_summary_quick_device_sort),
                    onClick = {
                        haptic.contextClick()
                        onOpenReorderDevices()
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.pref_title_virtual_button_sort),
                    summary = stringResource(R.string.pref_summary_virtual_button_sort),
                    onClick = {
                        haptic.contextClick()
                        navigator.push(RootScreen.VirtualButtonOrder)
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.pref_title_swipe_floating_ball_sort),
                    summary = stringResource(R.string.pref_summary_swipe_floating_ball_sort),
                    onClick = { navigator.push(RootScreen.SwipeFloatingBallOrder) },
                )
                ArrowPreference(
                    title = stringResource(R.string.pref_title_password_autofill),
                    summary = stringResource(R.string.pref_summary_password_autofill),
                    onClick = {
                        haptic.contextClick()
                        context.startActivity(LockscreenPasswordActivity.createIntent(context))
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_clipboard_sync),
                    summary = stringResource(R.string.pref_summary_clipboard_sync),
                    checked = asBundle.realtimeClipboardSyncToDevice,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            realtimeClipboardSyncToDevice = it,
                        )
                    },
                )
            }
        }

        item {
            SectionSmallTitle(stringResource(R.string.section_fullscreen))
            Card {
                SwitchPreference(
                    title = stringResource(R.string.pref_title_ignore_rotation_lock),
                    summary = stringResource(R.string.pref_summary_ignore_rotation_lock),
                    checked = asBundle.fullscreenControlIgnoreSystemRotationLock,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenControlIgnoreSystemRotationLock = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_back_to_device),
                    summary = stringResource(R.string.pref_summary_back_to_device),
                    checked = asBundle.fullscreenControlBackToDevice,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenControlBackToDevice = it,
                        )
                    },
                )
                // 全屏控制模式选择（三选一）
                val controlMode = AppSettings.FullscreenControlMode.fromStoredValue(asBundle.fullscreenControlMode)
                OverlayDropdownPreference(
                    title = stringResource(R.string.pref_title_fullscreen_control_mode),
                    entries = listOf(
                        DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = stringResource(R.string.vb_virtual_buttons),
                                    selected = controlMode == AppSettings.FullscreenControlMode.VIRTUAL_BUTTONS,
                                    onClick = {
                                        asBundle = asBundle.copy(
                                            fullscreenControlMode = AppSettings.FullscreenControlMode.VIRTUAL_BUTTONS.rawValue,
                                        )
                                    },
                                ),
                                DropdownItem(
                                    text = stringResource(R.string.pref_title_show_swipe_floating_ball),
                                    selected = controlMode == AppSettings.FullscreenControlMode.SWIPE_FLOATING_BALL,
                                    onClick = {
                                        asBundle = asBundle.copy(
                                            fullscreenControlMode = AppSettings.FullscreenControlMode.SWIPE_FLOATING_BALL.rawValue,
                                        )
                                    },
                                ),
                                DropdownItem(
                                    text = stringResource(R.string.pref_title_off),
                                    selected = controlMode == AppSettings.FullscreenControlMode.OFF,
                                    onClick = {
                                        asBundle = asBundle.copy(
                                            fullscreenControlMode = AppSettings.FullscreenControlMode.OFF.rawValue,
                                        )
                                    },
                                ),
                            )
                        )
                    ),
                )
                AnimatedVisibility(controlMode == AppSettings.FullscreenControlMode.VIRTUAL_BUTTONS) {
                    Column {
                        OverlayDropdownPreference(
                            entries = listOf(
                                DropdownEntry(
                                    items = FullscreenVirtualButtonDock
                                        .modeItemsResIds.mapIndexed { index, id ->
                                            DropdownItem(
                                                text = stringResource(id),
                                                selected = index == fullscreenVirtualButtonDock.modeIndex,
                                                onClick = {
                                                    asBundle = asBundle.copy(
                                                        fullscreenVirtualButtonDock = FullscreenVirtualButtonDock
                                                            .fromModeAndDirection(
                                                                modeIndex = index,
                                                                directionIndex = fullscreenVirtualButtonDock.directionIndex,
                                                            )
                                                            .toStoredValue(),
                                                    )
                                                },
                                            )
                                        },
                                ),
                                DropdownEntry(
                                    items = FullscreenVirtualButtonDock
                                        .directionItemsResIds.mapIndexed { index, id ->
                                            DropdownItem(
                                                text = stringResource(id),
                                                selected = index == fullscreenVirtualButtonDock.directionIndex,
                                                onClick = {
                                                    asBundle = asBundle.copy(
                                                        fullscreenVirtualButtonDock = FullscreenVirtualButtonDock
                                                            .fromModeAndDirection(
                                                                modeIndex = fullscreenVirtualButtonDock.modeIndex,
                                                                directionIndex = index,
                                                            )
                                                            .toStoredValue(),
                                                    )
                                                },
                                            )
                                        },
                                ),
                            ),
                            title = stringResource(R.string.pref_title_virtual_button_direction),
                            summary = stringResource(
                                if (fullscreenVirtualButtonDock.isFixed) R.string.dock_fixed
                                else R.string.dock_follow,
                            ) +
                                    stringResource(R.string.dock_display_on) +
                                    stringResource(fullscreenVirtualButtonDock.directionLabelResId),
                        )
                        ArrowSlider(
                            title = stringResource(R.string.pref_title_virtual_button_height),
                            value = asBundle.fullscreenVirtualButtonHeightDp.toFloat(),
                            onValueChange = {
                                asBundle = asBundle.copy(
                                    fullscreenVirtualButtonHeightDp =
                                        it.roundToInt().coerceIn(16, 80),
                                )
                            },
                            valueRange = 16f..80f,
                            steps = 80 - 16 - 1,
                            unit = "dp",
                            displayFormatter = { it.roundToInt().toString() },
                            inputInitialValue = asBundle.fullscreenVirtualButtonHeightDp.toString(),
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 1f..160f,
                            onInputConfirm = { input ->
                                input.toIntOrNull()?.let {
                                    asBundle = asBundle.copy(
                                        fullscreenVirtualButtonHeightDp =
                                            it.coerceIn(1, 160),
                                    )
                                }
                            },
                        )
                    }
                }
                AnimatedVisibility(controlMode == AppSettings.FullscreenControlMode.SWIPE_FLOATING_BALL) {
                    Column {
                        ArrowSlider(
                            title = stringResource(R.string.pref_title_temp_floating_button_size),
                            value = asBundle.tempFloatingButtonSizeDp.toFloat(),
                            onValueChange = {
                                asBundle = asBundle.copy(
                                    tempFloatingButtonSizeDp =
                                        it.roundToInt().coerceIn(32, 64)
                                )
                            },
                            valueRange = 32f..64f,
                            steps = 64 - 32 - 1,
                            unit = "dp",
                            displayFormatter = { it.roundToInt().toString() },
                            inputInitialValue = asBundle.tempFloatingButtonSizeDp.toString(),
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 1f..160f,
                            onInputConfirm = { input ->
                                input.toIntOrNull()?.let {
                                    asBundle = asBundle.copy(
                                        tempFloatingButtonSizeDp =
                                            it.coerceIn(32, 64)
                                    )
                                }
                            },
                        )
                        ArrowSlider(
                            title = stringResource(R.string.pref_title_swipe_floating_ball_bg_opacity),
                            value = asBundle.swipeFloatingBallBackgroundAlphaPercent.toFloat(),
                            onValueChange = {
                                asBundle = asBundle.copy(
                                    swipeFloatingBallBackgroundAlphaPercent =
                                        it.roundToInt().coerceIn(10, 100)
                                )
                            },
                            valueRange = 10f..100f,
                            steps = 100 - 10 - 1,
                            unit = "%",
                            displayFormatter = { it.roundToInt().toString() },
                            inputInitialValue = asBundle.swipeFloatingBallBackgroundAlphaPercent.toString(),
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 10f..100f,
                            onInputConfirm = { input ->
                                input.toIntOrNull()?.let {
                                    asBundle = asBundle.copy(
                                        swipeFloatingBallBackgroundAlphaPercent =
                                            it.coerceIn(10, 100)
                                    )
                                }
                            },
                        )
                        ArrowSlider(
                            title = stringResource(R.string.pref_title_swipe_floating_ball_ring_opacity),
                            value = asBundle.swipeFloatingBallRingAlphaPercent.toFloat(),
                            onValueChange = {
                                asBundle = asBundle.copy(
                                    swipeFloatingBallRingAlphaPercent =
                                        it.roundToInt().coerceIn(0, 100)
                                )
                            },
                            valueRange = 0f..100f,
                            steps = 100 - 0 - 1,
                            unit = "%",
                            displayFormatter = { it.roundToInt().toString() },
                            inputInitialValue = asBundle.swipeFloatingBallRingAlphaPercent.toString(),
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 0f..100f,
                            onInputConfirm = { input ->
                                input.toIntOrNull()?.let {
                                    asBundle = asBundle.copy(
                                        swipeFloatingBallRingAlphaPercent =
                                            it.coerceIn(0, 100)
                                    )
                                }
                            },
                        )

                    }
                }
                // 全屏悬浮球已隐藏，由滑动悬浮球替代
                SwitchPreference(
                    title = stringResource(R.string.pref_title_fullscreen_compat_mode),
                    summary = stringResource(R.string.pref_summary_fullscreen_compat_mode),
                    checked = asBundle.fullscreenCompatibilityMode,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenCompatibilityMode = it,
                        )
                    },
                )
            }
        }

        item {
            SectionSmallTitle(stringResource(R.string.section_scrcpy_server))
            Card {
                Column(
                    modifier = Modifier.padding(vertical = UiSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        Text(
                            text = stringResource(R.string.pref_title_custom_binary),
                            fontWeight = FontWeight.Medium,
                        )
                        TextField(
                            value = asBundle.customServerUri,
                            onValueChange = {},
                            readOnly = true,
                            label = Scrcpy.DEFAULT_SERVER_ASSET_NAME,
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Row(
                                    modifier = Modifier
                                        .padding(end = UiSpacing.Medium),
                                ) {
                                    if (asBundle.customServerUri.isNotBlank())
                                        IconButton(
                                            onClick = {
                                                haptic.contextClick()
                                                asBundle = asBundle.copy(
                                                    customServerUri = "",
                                                    customServerVersion = "",
                                                )
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Clear,
                                                contentDescription = stringResource(R.string.cd_clear),
                                            )
                                        }
                                    IconButton(
                                        onClick = {
                                            haptic.contextClick()
                                            serverPicker.pick()
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileOpen,
                                            contentDescription = stringResource(R.string.cd_select_file),
                                        )
                                    }
                                }
                            },
                        )
                    }
                    AnimatedVisibility(customServerVersionShowInput) {
                        Column(
                            modifier = Modifier.padding(horizontal = UiSpacing.Large),
                            verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                        ) {
                            Text(
                                text = stringResource(R.string.pref_title_custom_binary_version),
                                fontWeight = FontWeight.Medium,
                            )
                            SuperTextField(
                                value = customServerVersionInput,
                                onValueChange = { customServerVersionInput = it },
                                onFocusLost = {
                                    if (customServerVersionInput == AppSettings.CUSTOM_SERVER_VERSION.defaultValue)
                                        customServerVersionInput = ""
                                    asBundle = asBundle.copy(
                                        customServerVersion = customServerVersionInput
                                            .ifBlank { AppSettings.CUSTOM_SERVER_VERSION.defaultValue },
                                    )
                                },
                                label = Scrcpy.DEFAULT_SERVER_VERSION,
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        Text(
                            text = "Remote Path",
                            fontWeight = FontWeight.Medium,
                        )
                        SuperTextField(
                            value = serverRemotePathInput,
                            onValueChange = { serverRemotePathInput = it },
                            onFocusLost = {
                                if (serverRemotePathInput == AppSettings.SERVER_REMOTE_PATH.defaultValue)
                                    serverRemotePathInput = ""
                                asBundle = asBundle.copy(
                                    serverRemotePath = serverRemotePathInput
                                        .ifBlank { AppSettings.SERVER_REMOTE_PATH.defaultValue },
                                )
                            },
                            label = Scrcpy.DEFAULT_REMOTE_PATH,
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item {
            SectionSmallTitle(stringResource(R.string.section_adb))
            Card {
                val textTitleAppInfo = stringResource(R.string.pref_title_app_info)
                ArrowPreference(
                    title = stringResource(R.string.pref_title_battery_optimization),
                    summary = stringResource(R.string.pref_summary_battery_optimization),
                    onClick = {
                        haptic.contextClick()
                        val appInfoArgs = Bundle().apply {
                            putString("package", context.packageName)
                            putInt("uid", context.applicationInfo.uid)
                        }
                        val appDetailsIntent = Intent(Intent.ACTION_MAIN).apply {
                            setClassName(
                                "com.android.settings",
                                "com.android.settings.SubSettings",
                            )
                            putExtra(
                                ":settings:show_fragment",
                                "com.android.settings.applications.appinfo.AppInfoDashboardFragment",
                            )
                            putExtra(
                                ":settings:show_fragment_title",
                                textTitleAppInfo,
                            )
                            putExtra(":settings:show_fragment_args", appInfoArgs)
                            putExtra("package", context.packageName)
                            putExtra("uid", context.applicationInfo.uid)
                            putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                        }
                        val requestIntent = Intent(
                            AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            "package:${context.packageName}".toUri(),
                        )
                        val fallbackIntent = Intent(
                            AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                        )
                        runCatching { context.startActivity(appDetailsIntent) }
                            .recoverCatching { context.startActivity(requestIntent) }
                            .recoverCatching { context.startActivity(fallbackIntent) }
                            .onFailure { AppRuntime.snackbar(R.string.pref_cannot_open_settings) }
                    },
                )
                Column(
                    modifier = Modifier.padding(vertical = UiSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    val hasImportedAdbKey =
                        acBundle.importedPrivateKey.isNotBlank() ||
                                acBundle.importedPublicKeyX509.isNotBlank()
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        Text(
                            text = stringResource(R.string.pref_title_custom_adb_key),
                            fontWeight = FontWeight.Medium,
                        )
                        SuperTextField(
                            value = adbKeyNameInput,
                            onValueChange = { adbKeyNameInput = it },
                            onFocusLost = {
                                if (adbKeyNameInput == AppSettings.ADB_KEY_NAME.defaultValue)
                                    adbKeyNameInput = ""
                                asBundle = asBundle.copy(
                                    adbKeyName = adbKeyNameInput
                                        .ifBlank { AppSettings.ADB_KEY_NAME.defaultValue },
                                )
                            },
                            label = AppSettings.ADB_KEY_NAME.defaultValue,
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        Text(
                            text = stringResource(R.string.pref_title_adb_private_key),
                            fontWeight = FontWeight.Medium,
                        )
                        TextField(
                            value = acBundle.importedPrivateKeyFileName,
                            onValueChange = {},
                            readOnly = true,
                            label = stringResource(
                                when {
                                    acBundle.importedPrivateKey.isBlank() &&
                                            acBundle.rsaPrivateKey.isBlank() ->
                                        R.string.pref_adb_key_not_imported

                                    acBundle.importedPrivateKey.isNotBlank() ->
                                        R.string.pref_adb_key_imported

                                    else -> R.string.pref_adb_key_generated
                                },
                            ),
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Row(modifier = Modifier.padding(end = UiSpacing.Medium)) {
                                    IconButton(
                                        onClick = {
                                            haptic.contextClick()
                                            showResetKeyConfirm = true
                                        },
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (hasImportedAdbKey) Icons.Rounded.Clear
                                                else Icons.Rounded.Refresh,
                                            contentDescription = stringResource(
                                                if (hasImportedAdbKey) R.string.pref_adb_key_remove
                                                else R.string.pref_adb_key_regenerate,
                                            ),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.contextClick()
                                            exportingKey = "private"
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Share,
                                            contentDescription = stringResource(R.string.cd_export_adb_key),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.contextClick()
                                            adbPrivateKeyPicker.launch(arrayOf("*/*"))
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Download,
                                            contentDescription = stringResource(R.string.cd_select_file),
                                        )
                                    }
                                }
                            },
                        )
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        val publicKeyLabel = stringResource(
                            when {
                                acBundle.importedPublicKeyX509.isBlank() &&
                                        acBundle.rsaPublicKeyX509.isBlank() ->
                                    R.string.pref_adb_key_not_imported

                                acBundle.importedPublicKeyFileName.isNotBlank() ->
                                    R.string.pref_adb_key_imported

                                else -> R.string.pref_adb_key_generated
                            },
                        )
                        Text(
                            text = stringResource(R.string.pref_title_adb_public_key),
                            fontWeight = FontWeight.Medium,
                        )
                        TextField(
                            value = acBundle.importedPublicKeyFileName,
                            onValueChange = {},
                            readOnly = true,
                            label = publicKeyLabel,
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Row(modifier = Modifier.padding(end = UiSpacing.Medium)) {
                                    IconButton(
                                        onClick = {
                                            haptic.contextClick()
                                            exportingKey = "public"
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Share,
                                            contentDescription = stringResource(R.string.cd_export_adb_key),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.contextClick()
                                            adbPublicKeyPicker.launch(arrayOf("*/*"))
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Download,
                                            contentDescription = stringResource(R.string.cd_select_file),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                SwitchPreference(
                    title = stringResource(R.string.pref_title_auto_discovery),
                    summary = stringResource(R.string.pref_summary_auto_discovery),
                    checked = asBundle.adbPairingAutoDiscoverOnDialogOpen,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            adbPairingAutoDiscoverOnDialogOpen = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_auto_reconnect),
                    summary = stringResource(R.string.pref_summary_auto_reconnect),
                    checked = asBundle.adbAutoReconnectPairedDevice,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            adbAutoReconnectPairedDevice = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_auto_load_apps),
                    summary = stringResource(R.string.pref_summary_auto_load_apps),
                    checked = asBundle.adbAutoLoadAppListOnConnect,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            adbAutoLoadAppListOnConnect = it,
                        )
                        if (it) AppRuntime.snackbar(
                            R.string.pref_warning_list_apps,
                        )
                    },
                )
                ArrowSlider(
                    title = stringResource(R.string.pref_title_adb_flow_control),
                    summary = stringResource(R.string.pref_summary_adb_flow_control),
                    value = asBundle.adbFlowControlWindow.toFloat(),
                    onValueChange = {
                        asBundle = asBundle.copy(
                            adbFlowControlWindow = it.roundToInt(),
                        )
                    },
                    valueRange = 0f..4f,
                    steps = 4 - 0 - 1,
                    zeroStateText = stringResource(R.string.pref_adb_flow_control_disabled),
                    displayFormatter = { it.roundToInt().toString() },
                    inputInitialValue = asBundle.adbFlowControlWindow.toString(),
                    inputFilter = { input -> input.filter(Char::isDigit) },
                    inputValueRange = 0f..4f,
                    onInputConfirm = { input ->
                        input.toIntOrNull()?.let {
                            asBundle = asBundle.copy(
                                adbFlowControlWindow = it.coerceIn(0, 4),
                            )
                        }
                    },
                )
            }
        }

        item {
            SectionSmallTitle(stringResource(R.string.section_terminal))
            Card {
                ArrowSlider(
                    title = stringResource(R.string.pref_title_terminal_font_size),
                    summary = stringResource(R.string.pref_summary_terminal_font_size),
                    value = asBundle.terminalFontSizeSp,
                    onValueChange = {
                        asBundle = asBundle.copy(
                            terminalFontSizeSp = it.roundToInt().toFloat(),
                        )
                    },
                    valueRange = 1f..32f,
                    steps = 32 - 1 - 1,
                    unit = "sp",
                    displayFormatter = { it.roundToInt().toString() },
                    inputInitialValue = asBundle.terminalFontSizeSp.roundToInt().toString(),
                    inputFilter = { input -> input.filter(Char::isDigit) },
                    inputValueRange = 1f..32f,
                    onInputConfirm = { input ->
                        input.toIntOrNull()?.let {
                            asBundle = asBundle.copy(
                                terminalFontSizeSp = it.coerceIn(1, 32).toFloat(),
                            )
                        }
                    },
                )
                Column(
                    modifier = Modifier.padding(vertical = UiSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        Text(
                            text = stringResource(R.string.pref_title_custom_font),
                            fontWeight = FontWeight.Medium,
                        )
                        TextField(
                            value = asBundle.terminalFontDisplayName,
                            onValueChange = {},
                            readOnly = true,
                            label = stringResource(R.string.pref_hint_builtin_font),
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Row(modifier = Modifier.padding(end = UiSpacing.Medium)) {
                                    if (asBundle.terminalFontDisplayName.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                haptic.contextClick()
                                                scope.launch {
                                                    val cleared = clearTerminalFont(context)
                                                    asBundle = asBundle.copy(
                                                        terminalFontDisplayName = "",
                                                    )
                                                    AppRuntime.snackbar(
                                                        if (cleared) R.string.pref_font_restored
                                                        else R.string.pref_no_custom_font,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Clear,
                                                contentDescription = stringResource(R.string.cd_clear),
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.contextClick()
                                            terminalFontPicker.pick()
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileOpen,
                                            contentDescription = stringResource(R.string.cd_select_font),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        item {
            SectionSmallTitle(stringResource(R.string.section_misc))
            Card {
                ArrowPreference(
                    title = stringResource(R.string.pref_title_view_local_ip),
                    summary = stringResource(R.string.pref_summary_view_local_ip),
                    onClick = { navigator.push(RootScreen.LocalIp) },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_clear_logs_on_exit),
                    summary = stringResource(R.string.pref_summary_clear_logs_on_exit),
                    checked = asBundle.clearLogsOnExit,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            clearLogsOnExit = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_hide_log_box),
                    summary = stringResource(R.string.pref_summary_hide_log_box),
                    checked = asBundle.hideDeviceLogs,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            hideDeviceLogs = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_show_app_icons),
                    summary = stringResource(R.string.pref_summary_show_app_icons),
                    checked = asBundle.showAppIcons,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            showAppIcons = it
                        )
                    },
                )
                ArrowSlider(
                    title = stringResource(R.string.pref_title_snackbar_duration),
                    value = asBundle.snackbarDurationMs.toFloat(),
                    onValueChange = {
                        asBundle = asBundle.copy(
                            snackbarDurationMs = it.roundToInt()
                        )
                    },
                    valueRange = 500f..5000f,
                    steps = 9,
                    unit = "ms",
                    displayFormatter = { it.roundToInt().toString() },
                    inputInitialValue = asBundle.snackbarDurationMs.toString(),
                    inputFilter = { input -> input.filter(Char::isDigit) },
                    inputValueRange = 500f..5000f,
                    onInputConfirm = { input ->
                        input.toIntOrNull()?.let {
                            asBundle = asBundle.copy(
                                snackbarDurationMs = it.coerceIn(500, 5000)
                            )
                        }
                    },
                )
            }
        }

        item {
            SectionSmallTitle("")
            Card {
                ArrowPreference(
                    title = stringResource(R.string.about_title),
                    summary = updateSummary,
                    onClick = {
                        haptic.contextClick()
                        navigator.push(RootScreen.About)
                    },
                )
            }
        }
    }

    ExportAdbKeyDialog(
        show = exportingKey != null,
        isPrivateKey = exportingKey == "private",
        onDismissRequest = { exportingKey = null },
        onDismissFinished = { exportingKey = null },
        onExportToClipboard = {
            val target = exportingKey
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        if (target == "private") {
                            DirectAdbTransport.privateKey
                            DirectAdbTransport.getPrivateKeyPem()
                        } else {
                            DirectAdbTransport.publicKeyX509
                            DirectAdbTransport.getPublicKeyPem()
                        } ?: error("Key not available")
                    }
                }.onSuccess { pem ->
                    LocalInputService.setClipboardText(context, pem)
                    AppRuntime.snackbar(R.string.pref_adb_key_exported_clipboard)
                }.onFailure { e ->
                    AppRuntime.snackbar(
                        R.string.pref_adb_key_export_failed,
                        e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        },
        onExportToFile = {
            if (exportingKey == "private") {
                exportPrivateKeyPicker.launch("adbkey")
            } else {
                exportPublicKeyPicker.launch("adbkey.pub")
            }
        },
    )

    OverlayDialog(
        show = showResetKeyConfirm,
        title = stringResource(R.string.pref_adb_key_reset_confirm_title),
        summary = stringResource(R.string.pref_adb_key_reset_confirm_summary),
        defaultWindowInsetsPadding = false,
        onDismissRequest = { showResetKeyConfirm = false },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = {
                    haptic.contextClick()
                    showResetKeyConfirm = false
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.button_confirm),
                onClick = {
                    haptic.confirm()
                    showResetKeyConfirm = false
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                DirectAdbTransport.resetKeys()
                            }
                        }.onSuccess {
                            AppRuntime.snackbar(
                                if (it.removedImportedKey)
                                    R.string.pref_adb_key_removed
                                else
                                    R.string.pref_adb_key_reset,
                                it.fingerprint,
                            )
                        }.onFailure { e ->
                            AppRuntime.snackbar(
                                R.string.pref_adb_key_import_failed,
                                e.message ?: e.javaClass.simpleName,
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun ExportAdbKeyDialog(
    show: Boolean,
    isPrivateKey: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onExportToClipboard: () -> Unit,
    onExportToFile: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    OverlayDialog(
        show = show,
        title = stringResource(R.string.pref_adb_key_export),
        summary = stringResource(
            if (isPrivateKey) R.string.pref_adb_key_export_private_summary
            else R.string.pref_adb_key_export_public_summary,
        ),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        Spacer(Modifier.height(UiSpacing.ContentVertical * 2))
        Column(
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            TextButton(
                text = stringResource(R.string.pref_adb_key_export_to_clipboard),
                onClick = {
                    haptic.contextClick()
                    onExportToClipboard()
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = stringResource(R.string.pref_adb_key_export_to_file),
                onClick = {
                    haptic.contextClick()
                    onExportToFile()
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
