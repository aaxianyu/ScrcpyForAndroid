package io.github.miuzarte.scrcpyforandroid.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.miuzarte.scrcpyforandroid.constants.ScrcpyPresets
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.utils.appIconRounded
import io.github.miuzarte.scrcpyforandroid.miuix.SpinnerEntry
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.models.ScrcpyOptions.Crop
import io.github.miuzarte.scrcpyforandroid.models.ScrcpyOptions.NewDisplay
import io.github.miuzarte.scrcpyforandroid.scaffolds.*
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.*
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppIconCache
import io.github.miuzarte.scrcpyforandroid.services.AppManagerService
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyProfiles
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.quickDevices
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyProfiles
import io.github.miuzarte.scrcpyforandroid.storage.decodeBundleFromJson
import io.github.miuzarte.scrcpyforandroid.storage.encodeBundleToJson
import io.github.miuzarte.scrcpyforandroid.ui.*
import io.github.miuzarte.scrcpyforandroid.widgets.RecordPreferences
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
internal fun ScrcpyAllOptionsScreen(
    scrollBehavior: ScrollBehavior,
    scrcpy: Scrcpy,
) {
    val haptic = LocalHapticFeedback.current
    val navigator = LocalRootNavigator.current
    val configuration = LocalConfiguration.current
    val deviceScreenWidth = configuration.screenWidthDp
    val deviceScreenHeight = configuration.screenHeightDp
    // 获取本机物理分辨率（像素）
    val resources = LocalResources.current
    val devicePhysicalWidth = resources.displayMetrics.widthPixels
    val devicePhysicalHeight = resources.displayMetrics.heightPixels
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current
    val textNewProfile = stringResource(ScrcpyOptions.NEW_PROFILE_NAME_RES_ID)
    var showManageProfilesSheet by rememberSaveable { mutableStateOf(false) }
    val qdBundleShared by quickDevices.bundleState.collectAsState()
    val soBundleShared by scrcpyOptions.bundleState.collectAsState()
    val scrcpyProfilesState by scrcpyProfiles.state.collectAsState()
    val initialSelectedProfileId = remember {
        // 直接从 session 级状态读取，不依赖快捷设备
        AppRuntime.currentConnectionProfileId.value
    }
    val selectedProfileIdState = rememberSaveable(initialSelectedProfileId) {
        mutableStateOf(initialSelectedProfileId)
    }
    var selectedProfileId by selectedProfileIdState
    val soBundleState = rememberSaveable(selectedProfileId, soBundleShared, scrcpyProfilesState) {
        mutableStateOf(
            if (selectedProfileId == ScrcpyOptions.GLOBAL_PROFILE_ID)
                soBundleShared
            else scrcpyProfilesState.profiles
                .firstOrNull { it.id == selectedProfileId }
                ?.bundle ?: soBundleShared,
        )
    }
    val lastValidSoBundleState = rememberSaveable(selectedProfileId) {
        mutableStateOf(soBundleState.value)
    }
    // 显式同步 bundle：确保 profile 切换时一定更新，绕过 rememberSaveable 缓存
    LaunchedEffect(selectedProfileId) {
        val bundle = if (selectedProfileId == ScrcpyOptions.GLOBAL_PROFILE_ID)
            soBundleShared
        else scrcpyProfilesState.profiles
            .firstOrNull { it.id == selectedProfileId }
            ?.bundle ?: soBundleShared
        soBundleState.value = bundle
    }
    val textGlobal = stringResource(R.string.text_global)
    val profileTabs = remember(scrcpyProfilesState.profiles, textGlobal) {
        scrcpyProfilesState.profiles.map {
            if (it.isBuiltinGlobal) textGlobal else it.name
        }
    }
    val profileIds = remember(scrcpyProfilesState.profiles) {
        scrcpyProfilesState.profiles.map { it.id }
    }
    val selectedProfileIndex = remember(selectedProfileId, profileIds) {
        profileIds.indexOf(selectedProfileId).coerceAtLeast(0)
    }
    val currentConnectedDeviceName = remember(qdBundleShared.quickDevicesList) {
        val currentTarget = AppRuntime.currentConnectionTarget
        if (currentTarget == null) null
        else DeviceShortcuts.unmarshalFrom(qdBundleShared.quickDevicesList)
            .get(currentTarget.host, currentTarget.port)
            ?.name
            ?.ifBlank { currentTarget.host }
            ?: currentTarget.host
    }
    var activeProfileDialog by rememberSaveable { mutableStateOf<ProfileDialogMode?>(null) }
    var profileDialogTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var profileDialogInput by rememberSaveable { mutableStateOf("") }
    var profileDialogCopySourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingProfileId by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun saveBundleForProfile(profileId: String, bundle: ScrcpyOptions.Bundle) {
        if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID)
            scrcpyOptions.saveBundle(bundle)
        else
            scrcpyProfiles.updateBundle(profileId, bundle)
    }

    suspend fun rebindDeletedProfileReferences(profileId: String) {
        val shortcuts = DeviceShortcuts.unmarshalFrom(qdBundleShared.quickDevicesList)
        val updated = shortcuts.copy(
            devices = shortcuts.map { device ->
                if (device.scrcpyProfileId == profileId)
                    device.copy(scrcpyProfileId = ScrcpyOptions.GLOBAL_PROFILE_ID)
                else device
            },
        )
        if (updated != shortcuts) {
            quickDevices.updateBundle { bundle ->
                bundle.copy(quickDevicesList = updated.marshalToString())
            }
        }
    }

    suspend fun bindCurrentConnectedDevice(profileId: String) {
        val target = AppRuntime.currentConnectionTarget ?: return
        val shortcuts = DeviceShortcuts.unmarshalFrom(qdBundleShared.quickDevicesList)
        val device = shortcuts.firstOrNull { it.matchesAddress(target) }
        val updated = if (device != null) shortcuts.update(
            id = device.id,
            scrcpyProfileId = profileId,
        ) else shortcuts
        if (updated != shortcuts) {
            quickDevices.updateBundle { bundle ->
                bundle.copy(quickDevicesList = updated.marshalToString())
            }
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = stringResource(R.string.scrcpyopt_title),
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
                    actions = {
                        OverlayIconDropdownMenu(
                            entry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = stringResource(R.string.scrcpyopt_manage_profiles),
                                        onClick = {
                                            showManageProfilesSheet = true
                                        },
                                    ),
                                    DropdownItem(
                                        text = stringResource(R.string.scrcpyopt_paste_and_create),
                                        onClick = {
                                            scope.launch {
                                                val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                                if (clipText.isNullOrBlank()) {
                                                    AppRuntime.snackbar(R.string.scrcpyopt_paste_invalid)
                                                    return@launch
                                                }
                                                runCatching {
                                                    val json = JSONObject(clipText)
                                                    // 保存当前配置
                                                    saveBundleForProfile(selectedProfileId, soBundleState.value)
                                                    val bundle = decodeBundleFromJson(json)
                                                    val created = scrcpyProfiles.createProfile(
                                                        requestedName = textNewProfile,
                                                        bundle = bundle,
                                                    )
                                                    selectedProfileId = created.id
                                                    bindCurrentConnectedDevice(created.id)
                                                    AppRuntime.currentConnectionProfileId.value = created.id
                                                    AppRuntime.snackbar(R.string.scrcpyopt_paste_success)
                                                }.onFailure {
                                                    AppRuntime.snackbar(R.string.scrcpyopt_paste_invalid)
                                                }
                                            }
                                        },
                                    ),
                                ),
                            ),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = stringResource(R.string.scrcpyopt_cd_profile_manage),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    bottomContent = {
                        TabRow(
                            tabs = profileTabs,
                            selectedTabIndex = selectedProfileIndex,
                            onTabSelected = { index ->
                                val nextProfileId = profileIds.getOrNull(index)
                                    ?: return@TabRow
                                if (nextProfileId == selectedProfileId) return@TabRow
                                haptic.contextClick()
                                scope.launch {
                                    saveBundleForProfile(selectedProfileId, soBundleState.value)
                                    bindCurrentConnectedDevice(nextProfileId)
                                    selectedProfileId = nextProfileId
                                    // 写入 session 级状态，脱离快捷设备独立运作
                                    AppRuntime.currentConnectionProfileId.value = nextProfileId
                                    val profileName = profileTabs.getOrElse(index) { textGlobal }
                                    currentConnectedDeviceName?.let { deviceName ->
                                        AppRuntime.snackbar(
                                            R.string.device_switched_profile,
                                            deviceName,
                                            profileName,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(bottom = UiSpacing.PageHorizontal)
                                .padding(horizontal = UiSpacing.PageVertical),
                            minWidth = 96.dp,
                            maxWidth = 192.dp,
                            height = 48.dp,
                            itemSpacing = UiSpacing.Medium,
                        )
                    },
                )
            }
        },
        snackbarHost = { AppRuntime.snackbarHostState?.let { SnackbarHost(it) } },
    ) { contentPadding ->
        Box(
            modifier =
                if (blurActive) Modifier.layerBackdrop(blurBackdrop)
                else Modifier,
        ) {
            ScrcpyAllOptionsPage(
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                scrcpy = scrcpy,
                soBundleShared = soBundleShared,
                scrcpyProfilesState = scrcpyProfilesState,
                selectedProfileIdState = selectedProfileIdState,
                soBundleState = soBundleState,
                lastValidSoBundleState = lastValidSoBundleState,
                onSaveBundleForProfile = ::saveBundleForProfile,
                deviceScreenWidth = deviceScreenWidth,
                deviceScreenHeight = deviceScreenHeight,
                devicePhysicalWidth = devicePhysicalWidth,
                devicePhysicalHeight = devicePhysicalHeight,
            )
        }

        ProfileNameDialog(
            mode = activeProfileDialog,
            initialInput = profileDialogInput,
            profiles = scrcpyProfilesState.profiles,
            initialCopySourceProfileId = profileDialogCopySourceId,
            onDismissRequest = {
                activeProfileDialog = null
                profileDialogTargetId = null
            },
        ) { input, copySourceProfileId ->
            scope.launch {
                when (activeProfileDialog) {
                    ProfileDialogMode.Create -> {
                        saveBundleForProfile(selectedProfileId, soBundleState.value)
                        val copySourceBundle = when (copySourceProfileId) {
                            null -> ScrcpyOptions.defaultBundle()
                            selectedProfileId -> soBundleState.value
                            ScrcpyOptions.GLOBAL_PROFILE_ID -> soBundleShared
                            else -> scrcpyProfilesState.profiles
                                .firstOrNull { it.id == copySourceProfileId }
                                ?.bundle
                                ?: soBundleShared
                        }
                        val created = scrcpyProfiles.createProfile(
                            requestedName = input,
                            bundle = copySourceBundle,
                        )
                        selectedProfileId = created.id
                        bindCurrentConnectedDevice(created.id)
                        AppRuntime.currentConnectionProfileId.value = created.id
                    }

                    ProfileDialogMode.Rename -> {
                        val profileId = profileDialogTargetId ?: return@launch
                        scrcpyProfiles.renameProfile(
                            id = profileId,
                            requestedName = input,
                        )
                    }

                    null -> Unit
                }
                profileDialogTargetId = null
                profileDialogCopySourceId = selectedProfileId
                activeProfileDialog = null
            }
        }

        val textNewProfile = stringResource(R.string.scrcpyopt_new_profile)
        ManageProfilesSheet(
            show = showManageProfilesSheet,
            profiles = scrcpyProfilesState.profiles,
            selectedProfileId = selectedProfileId,
            onDismissRequest = { showManageProfilesSheet = false },
            onCreateProfile = {
                profileDialogTargetId = null
                profileDialogInput = textNewProfile
                profileDialogCopySourceId = selectedProfileId
                activeProfileDialog = ProfileDialogMode.Create
            },
            onRenameProfile = { profileId ->
                profileDialogTargetId = profileId
                profileDialogInput = scrcpyProfilesState.profiles
                    .firstOrNull { it.id == profileId }
                    ?.name.orEmpty()
                activeProfileDialog = ProfileDialogMode.Rename
            },
            onDeleteProfile = { profileId ->
                deletingProfileId = profileId
            },
            onMoveProfile = { fromIndex, toIndex ->
                scope.launch {
                    scrcpyProfiles.moveProfile(fromIndex, toIndex)
                }
            },
        )

        DeleteProfileDialog(
            show = deletingProfileId != null,
            profileName = scrcpyProfilesState.profiles
                .firstOrNull { it.id == deletingProfileId }
                ?.name.orEmpty(),
            onDismissRequest = { deletingProfileId = null },
        ) {
            scope.launch {
                val profileId = deletingProfileId ?: return@launch
                val deleted = scrcpyProfiles.deleteProfile(profileId)
                if (deleted) {
                    rebindDeletedProfileReferences(profileId)
                    if (selectedProfileId == profileId)
                        selectedProfileId = ScrcpyOptions.GLOBAL_PROFILE_ID
                }
                deletingProfileId = null
            }
        }
    }
}

@Composable
internal fun ScrcpyAllOptionsPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    scrcpy: Scrcpy,
    soBundleShared: ScrcpyOptions.Bundle,
    scrcpyProfilesState: ScrcpyProfiles.State,
    selectedProfileIdState: MutableState<String>,
    soBundleState: MutableState<ScrcpyOptions.Bundle>,
    lastValidSoBundleState: MutableState<ScrcpyOptions.Bundle>,
    onSaveBundleForProfile: suspend (String, ScrcpyOptions.Bundle) -> Unit,
    deviceScreenWidth: Int,
    deviceScreenHeight: Int,
    devicePhysicalWidth: Int,
    devicePhysicalHeight: Int,
) {
    val focusManager = LocalFocusManager.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val pageContext = LocalContext.current
    val taskScope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }

    val refreshBusy by scrcpy.listings.refreshBusyState.collectAsState()
    val listRefreshVersion by scrcpy.listings.refreshVersionState.collectAsState()

    var selectedProfileId by selectedProfileIdState
    val selectedProfileIdLatest by rememberUpdatedState(selectedProfileId)
    var soBundle by soBundleState
    var lastValidSoBundle by lastValidSoBundleState
    val soBundleLatest by rememberUpdatedState(soBundle)
    fun resolveProfileBundle(profileId: String): ScrcpyOptions.Bundle {
        if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID) return soBundleShared
        return scrcpyProfilesState.profiles.firstOrNull { it.id == profileId }?.bundle
            ?: soBundleShared
    }

    LaunchedEffect(selectedProfileId, soBundleShared, scrcpyProfilesState) {
        val bundle = resolveProfileBundle(selectedProfileId)
        if (soBundle != bundle) {
            soBundle = bundle
        }
        lastValidSoBundle = bundle
    }
    LaunchedEffect(soBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        val currentProfileId = selectedProfileIdLatest
        val savedBundle = resolveProfileBundle(currentProfileId)
        if (soBundle != savedBundle) {
            onSaveBundleForProfile(currentProfileId, soBundle)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                onSaveBundleForProfile(selectedProfileIdLatest, soBundleLatest)
            }
        }
    }
    // 输入弹窗状态（弹窗渲染在 LazyColumn 外，避免 item 回收导致弹窗随 IME 弹出被销毁）
    var screenOffTimeoutDialogOpen by remember { mutableStateOf(false) }
    var audioBitrateDialogOpen by remember { mutableStateOf(false) }
    var videoBitrateDialogOpen by remember { mutableStateOf(false) }
    var minSizeAlignmentDialogOpen by remember { mutableStateOf(false) }
    var maxSizeDialogOpen by remember { mutableStateOf(false) }
    var maxFpsDialogOpen by remember { mutableStateOf(false) }
    var cameraFpsDialogOpen by remember { mutableStateOf(false) }

    val listState = rememberSaveable(
        saver = LazyListState.Saver,
    ) {
        LazyListState()
    }

    val audioCodecItems = rememberSaveable { Codec.AUDIO.map { it.displayName } }
    val audioCodecIndex = rememberSaveable(soBundle.audioCodec) {
        Codec.AUDIO
            .indexOfFirst { it.string == soBundle.audioCodec }
            .coerceAtLeast(0)
    }

    val videoCodecItems = rememberSaveable { Codec.VIDEO.map { it.displayName } }
    val videoCodecIndex = rememberSaveable(soBundle.videoCodec) {
        Codec.VIDEO
            .indexOfFirst { it.string == soBundle.videoCodec }
            .coerceAtLeast(0)
    }

    val videoSourceItems = rememberSaveable { VideoSource.entries.map { it.string } }
    val videoSourceIndex = rememberSaveable(soBundle.videoSource) {
        VideoSource.entries
            .indexOfFirst { it.string == soBundle.videoSource }
            .coerceAtLeast(0)
    }

    val textAuto = stringResource(R.string.text_auto)
    val textDefault = stringResource(R.string.text_default)
    val textNone = stringResource(R.string.text_none)
    val textCustom = stringResource(R.string.text_custom)

    val displays = scrcpy.listings.displays
    val displayDropdownItems = rememberSaveable(textDefault, displays, listRefreshVersion) {
        listOf(textDefault) + displays.map { "${it.id} (${it.width}x${it.height})" }
    }
    val displaySpinnerItems = remember(displayDropdownItems) {
        displayDropdownItems.map { SpinnerEntry(title = it) }
    }
    val displayDropdownIndex = rememberSaveable(
        soBundle.displayId,
        displays,
        listRefreshVersion,
    ) {
        (displays.indexOfFirst { it.id == soBundle.displayId } + 1).coerceAtLeast(0)
    }
    val displayOverrideEndActionValue = remember(
        displays, listRefreshVersion, soBundle.displayId,
    ) {
        if (displays.isEmpty() && soBundle.displayId >= 0) soBundle.displayId.toString()
        else null
    }

    val cameras = scrcpy.listings.cameras
    val cameraDropdownItems = rememberSaveable(textDefault, cameras, listRefreshVersion) {
        listOf(textDefault) + cameras.map { info ->
            buildString {
                append(info.id)
                append(" (")
                append(info.facing.string)
                if (info.activeSize.isNotBlank()) {
                    append(", ")
                    append(info.activeSize)
                }
                append(')')
            }
        }
    }
    val cameraSpinnerItems = remember(cameraDropdownItems) {
        cameraDropdownItems.map { SpinnerEntry(title = it) }
    }
    val cameraDropdownIndex = rememberSaveable(
        soBundle.cameraId,
        cameras,
        listRefreshVersion,
    ) {
        (cameras.indexOfFirst { it.id == soBundle.cameraId } + 1).coerceAtLeast(0)
    }
    val cameraOverrideEndActionValue = remember(
        cameras, listRefreshVersion, soBundle.cameraId,
    ) {
        if (cameras.isEmpty() && soBundle.cameraId.isNotBlank()) soBundle.cameraId
        else null
    }

    val cameraFacingItems = rememberSaveable(textDefault) {
        listOf(textDefault) + CameraFacing.entries
            .drop(1)
            .map { it.string }
    }
    val cameraFacingIndex = rememberSaveable(soBundle.cameraFacing) {
        if (soBundle.cameraFacing.isEmpty()) 0
        else CameraFacing.entries
            .indexOfFirst { it.string == soBundle.cameraFacing }
            .takeIf { it > 0 }
            ?: 0
    }

    var cameraSizeCustomInput by rememberSaveable(soBundle.cameraSizeCustom) {
        mutableStateOf(soBundle.cameraSizeCustom)
    }

    val cameraSizes = scrcpy.listings.cameraSizes
    val cameraSizeDropdownItems = rememberSaveable(
        textDefault,
        textCustom,
        cameraSizes,
        listRefreshVersion,
    ) {
        listOf(textDefault, textCustom) + cameraSizes
    }
    val cameraSizeSpinnerItems = remember(cameraSizeDropdownItems) {
        cameraSizeDropdownItems.map { SpinnerEntry(title = it) }
    }
    val cameraSizeDropdownIndex = rememberSaveable(
        soBundle.cameraSize,
        soBundle.cameraSizeCustom,
        soBundle.cameraSizeUseCustom,
        cameraSizes,
        listRefreshVersion,
    ) {
        when {
            soBundle.cameraSizeUseCustom -> 1
            soBundle.cameraSize.isEmpty() -> 0
            soBundle.cameraSize in cameraSizes ->
                cameraSizes.indexOf(soBundle.cameraSize) + 2

            else -> 0
        }
    }
    val cameraSizeOverrideEndActionValue = remember(
        cameraSizes, listRefreshVersion,
        soBundle.cameraSize, soBundle.cameraSizeCustom, soBundle.cameraSizeUseCustom,
    ) {
        when {
            cameraSizes.isNotEmpty() -> null
            soBundle.cameraSizeUseCustom && soBundle.cameraSizeCustom.isNotBlank() -> soBundle.cameraSizeCustom
            soBundle.cameraSize.isNotBlank() -> soBundle.cameraSize
            else -> null
        }
    }

    var cameraArInput by rememberSaveable(soBundle.cameraAr) {
        mutableStateOf(soBundle.cameraAr)
    }
    var cameraZoomInput by rememberSaveable(soBundle.cameraZoom) {
        mutableStateOf(soBundle.cameraZoom)
    }

    val cameraFpsPresetIndex = rememberSaveable(soBundle.cameraFps) {
        ScrcpyPresets.CameraFps.indexOfOrNearest(soBundle.cameraFps)
    }

    val screenOffTimeoutPresetIndex = rememberSaveable(soBundle.screenOffTimeout) {
        ScrcpyPresets.ScreenOffTimeout.indexOfOrNearest(
            Tick(soBundle.screenOffTimeout).sec.toInt().coerceAtLeast(0),
        )
    }

    val audioSourceItems = rememberSaveable {
        AudioSource.entries.map { it.string }
    }
    val audioSourceIndex = rememberSaveable(soBundle.audioSource) {
        AudioSource.entries
            .indexOfFirst { it.string == soBundle.audioSource }
            .coerceAtLeast(0)
    }

    val keyInjectModeTextDefaultMixed = stringResource(R.string.scrcpyopt_default_mixed)
    val keyInjectModeTextPreferText = stringResource(R.string.scrcpyopt_prefer_text)
    val keyInjectModeTextRawKeyEvent = stringResource(R.string.scrcpyopt_raw_key_events)
    val keyInjectModeItems = rememberSaveable(
        keyInjectModeTextDefaultMixed,
        keyInjectModeTextPreferText,
        keyInjectModeTextRawKeyEvent,
    ) {
        listOf(
            keyInjectModeTextDefaultMixed,
            keyInjectModeTextPreferText,
            keyInjectModeTextRawKeyEvent,
        )
    }
    val keyInjectModeIndex = rememberSaveable(soBundle.keyInjectMode) {
        when (soBundle.keyInjectMode) {
            ClientOptions.KeyInjectMode.PREFER_TEXT.string -> 1
            ClientOptions.KeyInjectMode.RAW.string -> 2
            else -> 0
        }
    }

    val minSizeAlignmentPresetIndex = rememberSaveable(soBundle.minSizeAlignment) {
        ScrcpyPresets.MinSizeAlignment.indexOfOrNearest(soBundle.minSizeAlignment)
    }

    val maxSizePresetIndex = rememberSaveable(soBundle.maxSize) {
        ScrcpyPresets.MaxSize.indexOfOrNearest(soBundle.maxSize)
    }

    val maxFpsPresetIndex = rememberSaveable(soBundle.maxFps) {
        ScrcpyPresets.MaxFPS.indexOfOrNearest(soBundle.maxFps.toIntOrNull() ?: 0)
    }

    var videoCodecOptionsInput by rememberSaveable(soBundle.videoCodecOptions) {
        mutableStateOf(soBundle.videoCodecOptions)
    }

    var audioCodecOptionsInput by rememberSaveable(soBundle.audioCodecOptions) {
        mutableStateOf(soBundle.audioCodecOptions)
    }

    val videoEncoders = scrcpy.listings.videoEncoders
    val videoEncoderItems by remember(textAuto, videoEncoders, listRefreshVersion) {
        derivedStateOf {
            buildList {
                add(SpinnerEntry(title = textAuto))
                videoEncoders.forEach { info ->
                    add(
                        SpinnerEntry(
                            title = info.id,
                            summary = info.type.s,
                        ),
                    )
                }
            }
        }
    }
    val videoEncoderIndex = rememberSaveable(
        soBundle.videoEncoder,
        videoEncoders,
        listRefreshVersion,
    ) {
        (videoEncoders.indexOfFirst { it.id == soBundle.videoEncoder } + 1)
            .coerceAtLeast(0)
    }
    val videoEncoderOverrideEndActionValue = remember(
        videoEncoders, listRefreshVersion, soBundle.videoEncoder,
    ) {
        if (videoEncoders.isEmpty() && soBundle.videoEncoder.isNotBlank()) soBundle.videoEncoder
        else null
    }

    val audioEncoders = scrcpy.listings.audioEncoders
    val audioEncoderItems by remember(audioEncoders, listRefreshVersion) {
        derivedStateOf {
            buildList {
                add(SpinnerEntry(title = textAuto))
                audioEncoders.forEach { info ->
                    add(
                        SpinnerEntry(
                            title = info.id,
                            summary = info.type.s,
                        ),
                    )
                }
            }
        }
    }
    val audioEncoderIndex = rememberSaveable(
        soBundle.audioEncoder,
        audioEncoders,
        listRefreshVersion,
    ) {
        (audioEncoders.indexOfFirst { it.id == soBundle.audioEncoder } + 1)
            .coerceAtLeast(0)
    }
    val audioEncoderOverrideEndActionValue = remember(
        audioEncoders, listRefreshVersion, soBundle.audioEncoder,
    ) {
        if (audioEncoders.isEmpty() && soBundle.audioEncoder.isNotBlank()) soBundle.audioEncoder
        else null
    }

    val displayImePolicyItems = rememberSaveable {
        listOf(textDefault) + DisplayImePolicy.entries
            .drop(1)
            .map { it.string }
    }
    val displayImePolicyIndex = rememberSaveable(soBundle.displayImePolicy) {
        if (soBundle.displayImePolicy.isEmpty()) 0
        else DisplayImePolicy.entries
            .indexOfFirst { it.string == soBundle.displayImePolicy }
            .takeIf { it > 0 }
            ?: 0
    }

    val apps = remember(scrcpy.listings.apps, listRefreshVersion) {
        scrcpy.listings.apps
    }
    var appIconMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val appDropdownItems by remember(apps, listRefreshVersion, appIconMap) {
        derivedStateOf {
            buildList {
                add(SpinnerEntry(title = textNone))
                add(SpinnerEntry(title = textCustom))
                apps.forEach { app ->
                    add(
                        SpinnerEntry(
                            icon = {
                                val b64 = appIconMap[app.packageName]
                                val bitmap = remember(b64) {
                                    b64?.let {
                                        runCatching {
                                            val bytes = Base64.decode(it, Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        }.getOrNull()
                                    }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = app.label ?: app.packageName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .appIconRounded(26.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector =
                                            if (app.system == true) Icons.Rounded.Android
                                            else MiuixIcons.Store,
                                        contentDescription = app.label ?: app.packageName,
                                        modifier = Modifier.padding(end = UiSpacing.ContentVertical),
                                    )
                                }
                            },
                            title = app.label?.takeIf { it.isNotBlank() } ?: app.packageName,
                            summary = app.packageName,
                        ),
                    )
                }
            }
        }
    }
    val appDropdownIndex = rememberSaveable(
        soBundle.startApp,
        soBundle.startAppCustom,
        soBundle.startAppUseCustom,
        apps,
        listRefreshVersion,
    ) {
        when {
            soBundle.startAppUseCustom -> 1
            soBundle.startApp.isEmpty() -> 0
            apps.any { it.packageName == soBundle.startApp } ->
                apps.indexOfFirst { it.packageName == soBundle.startApp } + 2

            else -> 0
        }
    }
    val startAppOverrideEndActionValue = remember(
        apps, listRefreshVersion, soBundle.startApp,
    ) {
        if (apps.isEmpty() && soBundle.startApp.isNotBlank()) soBundle.startApp
        else null
    }

    var startAppCustomInput by rememberSaveable(soBundle.startAppCustom) {
        mutableStateOf(soBundle.startAppCustom)
    }

    // [<width>x<height>][/<dpi>]
    val (ndWidth, ndHeight, ndDpi) = remember(soBundle.newDisplay) {
        NewDisplay.parseFrom(soBundle.newDisplay)
    }
    var newDisplayWidthInput by rememberSaveable(soBundle.newDisplay) {
        mutableStateOf(ndWidth?.toString() ?: "")
    }
    var newDisplayHeightInput by rememberSaveable(soBundle.newDisplay) {
        mutableStateOf(ndHeight?.toString() ?: "")
    }
    var newDisplayDpiInput by rememberSaveable(soBundle.newDisplay) {
        mutableStateOf(ndDpi?.toString() ?: "")
    }
    val newDisplayWidthBlank = remember(newDisplayWidthInput) {
        newDisplayWidthInput.trim().isEmpty()
    }
    val newDisplayHeightBlank = remember(newDisplayHeightInput) {
        newDisplayHeightInput.trim().isEmpty()
    }
    val newDisplayAllBlank =
        remember(newDisplayWidthBlank, newDisplayHeightBlank, newDisplayDpiInput) {
            newDisplayWidthBlank && newDisplayHeightBlank && newDisplayDpiInput.trim().isEmpty()
        }

    // width:height:x:y
    val (cWidth, cHeight, cX, cY) = remember(soBundle.crop) {
        Crop.parseFrom(soBundle.crop)
    }
    var cropWidthInput by rememberSaveable(soBundle.crop) {
        mutableStateOf(cWidth?.toString() ?: "")
    }
    var cropHeightInput by rememberSaveable(soBundle.crop) {
        mutableStateOf(cHeight?.toString() ?: "")
    }
    var cropXInput by rememberSaveable(soBundle.crop) {
        mutableStateOf(cX?.toString() ?: "")
    }
    var cropYInput by rememberSaveable(soBundle.crop) {
        mutableStateOf(cY?.toString() ?: "")
    }

    val logLevelItems = rememberSaveable { LogLevel.entries.map { it.string } }
    val logLevelIndex = rememberSaveable(soBundle.logLevel) {
        LogLevel.entries
            .indexOfFirst { it.string == soBundle.logLevel }
            .coerceAtLeast(0)
    }

    var serverParamsPreview by rememberSaveable { mutableStateOf("") }
    // 监听选项变化, 自动更新预览
    LaunchedEffect(soBundle) {
        val clientOptions = scrcpyOptions.toClientOptions(soBundle).fix()

        try {
            clientOptions.validate()
        } catch (e: IllegalArgumentException) {
            if (soBundle != lastValidSoBundle) {
                AppRuntime.snackbar(
                    R.string.scrcpyopt_invalid_options,
                    e.message ?: e.javaClass.simpleName,
                )
                soBundle = lastValidSoBundle
            }
            return@LaunchedEffect
        }

        lastValidSoBundle = soBundle

        val serverParamsList = clientOptions
            .toServerParams(0u)
            .toList(preview = true)
            .toMutableList()
        // 客户端自定义选项（非 scrcpy 协议参数），追加到预览末尾
        if (soBundle.wakeScreenOnFullscreen) {
            serverParamsList.add("wake_screen_on_fullscreen=true")
        }
        serverParamsPreview = serverParamsList
            // improve readability using hard line breaks
            .joinToString("\n")
    }

    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        state = listState,
        bottomInnerPadding = UiSpacing.PageBottom,
    ) {
        item {
            Card(
                onClick = {
                    // 将当前配置的JSON复制到剪贴板（用于粘贴新建配置）
                    val jsonStr = encodeBundleToJson(soBundle).toString(2)
                    val clipboard = pageContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("scrcpy_config", jsonStr))
                    AppRuntime.snackbar(R.string.terminal_copied)
                },
            ) {
                Text(
                    text = serverParamsPreview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiSpacing.Large),
                    fontSize = 14.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
        }

        item {
            Card {
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_turn_screen_off),
                    summary = "--turn-screen-off",
                    checked = soBundle.turnScreenOff,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            turnScreenOff = it,
                        )
                        if (it) AppRuntime.snackbar(
                            // github.com/Genymobile/scrcpy/issues/3376
                            // github.com/Genymobile/scrcpy/issues/4587
                            // github.com/Genymobile/scrcpy/issues/5676
                            R.string.scrcpyopt_turn_screen_off_note,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_control),
                    summary = "--no-control",
                    checked = !soBundle.control,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            control = !it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_video),
                    summary = "--no-video",
                    checked = !soBundle.video,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            video = !it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_video_playback),
                    summary = "--no-video-playback",
                    checked = !soBundle.videoPlayback,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            videoPlayback = !it,
                        )
                    },
                    enabled = soBundle.video,
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_audio),
                    summary = "--no-audio",
                    checked = !soBundle.audio,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            audio = !it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_audio_playback),
                    summary = "--no-audio-playback",
                    checked = !soBundle.audioPlayback,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            audioPlayback = !it,
                        )
                    },
                    enabled = soBundle.audio,
                )
                ArrowSlider(
                    title = stringResource(R.string.scrcpyopt_screen_off_timeout),
                    summary = "--screen-off-timeout",
                    value = screenOffTimeoutPresetIndex.toFloat(),
                    onValueChange = { value ->
                        val idx = value.roundToInt()
                            .coerceIn(0, ScrcpyPresets.ScreenOffTimeout.lastIndex)
                        soBundle = soBundle.copy(
                            screenOffTimeout = ScrcpyPresets.ScreenOffTimeout[idx]
                                .takeIf { it > 0 }
                                ?.toLong()
                                ?.let(Tick::fromSec)
                                ?.value
                                ?: -1,
                        )
                    },
                    valueRange = 0f..ScrcpyPresets.ScreenOffTimeout.lastIndex.toFloat(),
                    steps = (ScrcpyPresets.ScreenOffTimeout.size - 2).coerceAtLeast(0),
                    unit = "s",
                    zeroStateText = stringResource(R.string.scrcpyopt_dont_modify),
                    showKeyPoints = true,
                    keyPoints = ScrcpyPresets.ScreenOffTimeout.indices.map { it.toFloat() },
                    displayText = Tick(soBundle.screenOffTimeout).sec.toString(),
                    inputInitialValue =
                        if (soBundle.screenOffTimeout <= 0) ""
                        else Tick(soBundle.screenOffTimeout).sec.toString(),
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 0f..86400f,
                    onInputConfirm = {
                        soBundle = soBundle.copy(
                            screenOffTimeout = it.toLongOrNull()
                                ?.takeIf { value -> value > 0 }
                                ?.let(Tick::fromSec)
                                ?.value
                                ?: -1,
                        )
                    },
                    renderInputDialog = false,
                    onInputDialogOpenChange = { screenOffTimeoutDialogOpen = it },
                    inputDialogOpen = screenOffTimeoutDialogOpen,
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_power_on),
                    summary = "--no-power-on",
                    checked = !soBundle.powerOn,
                    onCheckedChange = { checked ->
                        soBundle = soBundle.copy(powerOn = !checked)
                        // 打开时提示：进入全屏画面可能静止
                        if (checked) {
                            AppRuntime.snackbar(R.string.vm_screen_off_hint)
                        }
                    },
                )
                // 进入全屏时点亮屏幕（客户端自定义选项，非 scrcpy 协议参数）
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_wake_screen_on_fullscreen),
                    summary = "wake_screen_on_fullscreen=true",
                    checked = soBundle.wakeScreenOnFullscreen,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            wakeScreenOnFullscreen = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_power_off_on_close),
                    summary = "--power-off-on-close",
                    checked = soBundle.powerOffOnClose,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            powerOffOnClose = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_stay_awake),
                    summary = "--stay-awake",
                    checked = soBundle.stayAwake,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            stayAwake = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_keep_active),
                    summary = "--keep-active",
                    checked = soBundle.keepActive,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            keepActive = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_show_touches),
                    summary = "--show-touches",
                    checked = soBundle.showTouches,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            showTouches = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_fullscreen),
                    summary = "--fullscreen",
                    checked = soBundle.fullscreen,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            fullscreen = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_disable_screensaver),
                    summary = "--disable-screensaver",
                    checked = soBundle.disableScreensaver,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            disableScreensaver = it,
                        )
                        if (it) AppRuntime.snackbar(
                            R.string.scrcpyopt_disable_screensaver_note,
                        )
                    },
                )
                RecordPreferences(
                    profileId = selectedProfileId,
                    recordFilenameTemplate = soBundle.recordFilename,
                    recordFormat = soBundle.recordFormat,
                    enabled = true,
                    onRecordFormatChange = {
                        soBundle = soBundle.copy(recordFormat = it)
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_kill_adb_on_close),
                    summary = "--kill-adb-on-close",
                    checked = soBundle.killAdbOnClose,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            killAdbOnClose = it,
                        )
                    },
                )
            }
        }

        item {
            Card {
                OverlayDropdownPreference(
                    title = stringResource(R.string.scrcpyopt_audio_codec),
                    summary = "--audio-codec",
                    items = audioCodecItems,
                    selectedIndex = audioCodecIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            audioCodec = Codec.AUDIO[it].string,
                        )
                    },
                )
                ArrowSlider(
                    title = stringResource(R.string.scrcpyopt_audio_bitrate),
                    summary = "--audio-bit-rate",
                    value = ScrcpyPresets.AudioBitRate
                        .indexOfOrNearest(soBundle.audioBitRate / 1000)
                        .toFloat(),
                    onValueChange = { value ->
                        val idx = value.roundToInt()
                            .coerceIn(0, ScrcpyPresets.AudioBitRate.lastIndex)
                        soBundle = soBundle.copy(
                            audioBitRate = ScrcpyPresets.AudioBitRate[idx] * 1000,
                        )
                    },
                    valueRange = 0f..ScrcpyPresets.AudioBitRate.lastIndex.toFloat(),
                    steps = (ScrcpyPresets.AudioBitRate.size - 2).coerceAtLeast(0),
                    unit = "Kbps",
                    zeroStateText = stringResource(R.string.text_default),
                    displayText = (soBundle.audioBitRate / 1_000).toString(),
                    inputInitialValue =
                        if (soBundle.audioBitRate <= 0) ""
                        else (soBundle.audioBitRate / 1_000).toString(),
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
                    onInputConfirm = { raw ->
                        raw.toIntOrNull()
                            ?.takeIf { it >= 0 }
                            ?.let {
                                soBundle = soBundle.copy(
                                    audioBitRate = it * 1000,
                                )
                            }
                    },
                    renderInputDialog = false,
                    onInputDialogOpenChange = { audioBitrateDialogOpen = it },
                    inputDialogOpen = audioBitrateDialogOpen,
                )

                OverlayDropdownPreference(
                    title = stringResource(R.string.scrcpyopt_video_codec),
                    summary = "--video-codec",
                    items = videoCodecItems,
                    selectedIndex = videoCodecIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            videoCodec = Codec.VIDEO[it].string,
                        )
                    },
                )
                ArrowSlider(
                    title = stringResource(R.string.scrcpyopt_video_bitrate),
                    summary = "--video-bit-rate",
                    value = soBundle.videoBitRate / 1_000_000f,
                    onValueChange = { mbps ->
                        soBundle = soBundle.copy(
                            videoBitRate = (mbps * 10).roundToInt() * (1_000_000 / 10),
                        )
                    },
                    valueRange = 0f..40f,
                    steps = 400 - 1,
                    unit = "Mbps",
                    zeroStateText = stringResource(R.string.text_default),
                    displayFormatter = { "%.1f".format(it) },
                    inputInitialValue =
                        if (soBundle.videoBitRate <= 0) ""
                        else "%.1f".format(soBundle.videoBitRate / 1_000_000f),
                    inputFilter = { text ->
                        var dotUsed = false
                        text.filter { ch ->
                            when {
                                ch.isDigit() -> true
                                ch == '.' && !dotUsed -> {
                                    dotUsed = true
                                    true
                                }

                                else -> false
                            }
                        }
                    },
                    inputValueRange = 0f..UInt.MAX_VALUE.toFloat(),
                    onInputConfirm = { raw ->
                        raw.toFloatOrNull()?.let { parsed ->
                            if (parsed >= 0f) {
                                soBundle = soBundle.copy(
                                    videoBitRate = (parsed * 1_000_000f).roundToInt(),
                                )
                            }
                        }
                    },
                    renderInputDialog = false,
                    onInputDialogOpenChange = { videoBitrateDialogOpen = it },
                    inputDialogOpen = videoBitrateDialogOpen,
                )
            }
        }

        item {
            Card {
                OverlayDropdownPreference(
                    title = stringResource(R.string.scrcpyopt_video_source),
                    summary = "--video-source",
                    items = videoSourceItems,
                    selectedIndex = videoSourceIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            videoSource = VideoSource.entries[it].string,
                        )
                    },
                )
                AnimatedVisibility(soBundle.videoSource == "display") {
                    Column {
                        OverlaySpinnerWithFallback(
                            title = stringResource(R.string.scrcpyopt_display_id),
                            summary = "--display-id",
                            items = displaySpinnerItems,
                            selectedIndex = displayDropdownIndex,
                            dataLoaded = displays.isNotEmpty(),
                            dataLoading = refreshBusy,
                            overrideEndActionValue = displayOverrideEndActionValue,
                            onExpandedChange = { expanded ->
                                if (expanded && displays.isEmpty()) {
                                    scope.launch {
                                        AppRuntime.snackbar(R.string.text_fetching)
                                        try {
                                            withContext(Dispatchers.IO) {
                                                scrcpy.listings.getDisplays(forceRefresh = false)
                                            }
                                            AppRuntime.snackbar(R.string.text_fetch_success)
                                        } catch (e: Exception) {
                                            AppRuntime.snackbar(
                                                R.string.text_fetch_failed,
                                                e.message ?: "",
                                            )
                                        }
                                    }
                                }
                            },
                            onSelectedIndexChange = {
                                soBundle = soBundle.copy(
                                    displayId =
                                        if (it == 0) -1
                                        else displays[it - 1].id,
                                )
                            },
                        )
                        ArrowSlider(
                            title = stringResource(R.string.scrcpyopt_min_size_alignment),
                            summary = "--min-size-alignment",
                            value = minSizeAlignmentPresetIndex.toFloat(),
                            onValueChange = {
                                val idx = it.roundToInt()
                                    .coerceIn(0, ScrcpyPresets.MinSizeAlignment.lastIndex)
                                soBundle = soBundle.copy(
                                    minSizeAlignment = ScrcpyPresets.MinSizeAlignment[idx],
                                )
                            },
                            valueRange = 0f..ScrcpyPresets.MinSizeAlignment.lastIndex.toFloat(),
                            steps = (ScrcpyPresets.MinSizeAlignment.size - 2).coerceAtLeast(0),
                            showKeyPoints = true,
                            keyPoints = ScrcpyPresets.MinSizeAlignment.indices.map { it.toFloat() },
                            displayText = soBundle.minSizeAlignment.toString(),
                            inputInitialValue = soBundle.minSizeAlignment
                                .takeIf { it != 1 }
                                ?.toString()
                                ?: "",
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 1f..16f,
                            onInputConfirm = { raw ->
                                val parsed = raw.toIntOrNull() ?: return@ArrowSlider
                                if (parsed and (parsed - 1) != 0) {
                                    AppRuntime.snackbar(R.string.scrcpyopt_min_size_alignment_invalid)
                                    return@ArrowSlider
                                }
                                soBundle = soBundle.copy(
                                    minSizeAlignment = parsed,
                                )
                            },
                            renderInputDialog = false,
                            onInputDialogOpenChange = { minSizeAlignmentDialogOpen = it },
                            inputDialogOpen = minSizeAlignmentDialogOpen,
                        )
                        ArrowSlider(
                            title = stringResource(R.string.scrcpyopt_max_size),
                            summary = "--max-size",
                            value = maxSizePresetIndex.toFloat(),
                            onValueChange = {
                                val idx = it.roundToInt()
                                    .coerceIn(0, ScrcpyPresets.MaxSize.lastIndex)
                                soBundle = soBundle.copy(
                                    maxSize = ScrcpyPresets.MaxSize[idx],
                                )
                            },
                            valueRange = 0f..ScrcpyPresets.MaxSize.lastIndex.toFloat(),
                            steps = (ScrcpyPresets.MaxSize.size - 2).coerceAtLeast(0),
                            unit = "px",
                            zeroStateText = stringResource(R.string.text_off),
                            showUnitWhenZeroState = false,
                            showKeyPoints = true,
                            keyPoints = ScrcpyPresets.MaxSize.indices.map { it.toFloat() },
                            displayText = soBundle.maxSize.toString(),
                            inputInitialValue = soBundle.maxSize
                                .takeIf { it != 0 }
                                ?.toString()
                                ?: "",
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 0f..UInt.MAX_VALUE.toFloat(),
                            onInputConfirm = {
                                soBundle = soBundle.copy(
                                    maxSize = it.toIntOrNull() ?: run { 0 },
                                )
                            },
                            renderInputDialog = false,
                            onInputDialogOpenChange = { maxSizeDialogOpen = it },
                            inputDialogOpen = maxSizeDialogOpen,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.pref_title_resolution_custom_enabled),
                            summary = stringResource(R.string.pref_summary_resolution_custom_enabled),
                            checked = soBundle.resolutionCustomEnabled,
                            onCheckedChange = {
                                soBundle = soBundle.copy(resolutionCustomEnabled = it)
                            },
                        )
                        if (soBundle.resolutionCustomEnabled) {
                            var resolutionWidthInput by rememberSaveable {
                                mutableStateOf(soBundle.resolutionWidth.takeIf { it > 0 }?.toString() ?: "")
                            }
                            var resolutionHeightInput by rememberSaveable {
                                mutableStateOf(soBundle.resolutionHeight.takeIf { it > 0 }?.toString() ?: "")
                            }
                            Column(
                                modifier = Modifier.padding(horizontal = UiSpacing.Large),
                                verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
                                    ) {
                                        SuperTextField(
                                            label = stringResource(R.string.pref_title_resolution_width),
                                            value = resolutionWidthInput,
                                            onValueChange = {
                                                resolutionWidthInput = it.filter(Char::isDigit)
                                                val value = resolutionWidthInput.toIntOrNull() ?: 0
                                                soBundle = soBundle.copy(resolutionWidth = value)
                                            },
                                            onFocusLost = {
                                                val value = resolutionWidthInput.toIntOrNull() ?: 0
                                                val clamped = when {
                                                    value == 0 -> 0
                                                    value < 200 -> 200
                                                    value > 3000 -> 3000
                                                    else -> value
                                                }
                                                if (clamped > 0) {
                                                    resolutionWidthInput = clamped.toString()
                                                } else {
                                                    resolutionWidthInput = ""
                                                }
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Next,
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                            ),
                                            modifier = Modifier.weight(1f),
                                        )
                                        SuperTextField(
                                            label = stringResource(R.string.pref_title_resolution_height),
                                            value = resolutionHeightInput,
                                            onValueChange = {
                                                resolutionHeightInput = it.filter(Char::isDigit)
                                                val value = resolutionHeightInput.toIntOrNull() ?: 0
                                                soBundle = soBundle.copy(resolutionHeight = value)
                                            },
                                            onFocusLost = {
                                                val value = resolutionHeightInput.toIntOrNull() ?: 0
                                                val clamped = when {
                                                    value == 0 -> 0
                                                    value < 200 -> 200
                                                    value > 3000 -> 3000
                                                    else -> value
                                                }
                                                if (clamped > 0) {
                                                    resolutionHeightInput = clamped.toString()
                                                } else {
                                                    resolutionHeightInput = ""
                                                }
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done,
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = { focusManager.clearFocus() },
                                            ),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    BoxWithConstraints(
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        val gap = UiSpacing.ContentHorizontal
                                        val inputWidth = (maxWidth - gap) / 2
                                        val trailingButtonWidth = inputWidth
                                        val resolutionAllBlank = resolutionWidthInput.isBlank() && resolutionHeightInput.isBlank()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(gap),
                                        ) {
                                            TextButton(
                                                text = stringResource(R.string.cd_clear),
                                                onClick = {
                                                    resolutionWidthInput = ""
                                                    resolutionHeightInput = ""
                                                    soBundle = soBundle.copy(
                                                        resolutionWidth = 0,
                                                        resolutionHeight = 0,
                                                    )
                                                },
                                                modifier = Modifier.width(inputWidth),
                                                enabled = !resolutionAllBlank,
                                            )
                                            TextButton(
                                                text = stringResource(R.string.scrcpyopt_native),
                                                onClick = {
                                                    soBundle = soBundle.copy(
                                                        resolutionWidth = devicePhysicalWidth,
                                                        resolutionHeight = devicePhysicalHeight,
                                                    )
                                                    resolutionWidthInput = devicePhysicalWidth.toString()
                                                    resolutionHeightInput = devicePhysicalHeight.toString()
                                                },
                                                modifier = Modifier.width(trailingButtonWidth),
                                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        ArrowSlider(
                            title = stringResource(R.string.scrcpyopt_max_fps),
                            summary = "--max-fps",
                            value = maxFpsPresetIndex.toFloat(),
                            onValueChange = { value ->
                                val idx = value.roundToInt()
                                    .coerceIn(0, ScrcpyPresets.MaxFPS.lastIndex)
                                soBundle = soBundle.copy(
                                    maxFps =
                                        if (idx == 0) ""
                                        else ScrcpyPresets.MaxFPS[idx].toString(),
                                )
                            },
                            valueRange = 0f..ScrcpyPresets.MaxFPS.lastIndex.toFloat(),
                            steps = (ScrcpyPresets.MaxFPS.size - 2).coerceAtLeast(0),
                            unit = "fps",
                            zeroStateText = stringResource(R.string.text_off),
                            showUnitWhenZeroState = false,
                            showKeyPoints = true,
                            keyPoints = ScrcpyPresets.MaxFPS.indices.map { it.toFloat() },
                            displayText = soBundle.maxFps,
                            inputInitialValue = soBundle.maxFps,
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
                            onInputConfirm = {
                                soBundle = soBundle.copy(
                                    maxFps = it,
                                )
                            },
                            renderInputDialog = false,
                            onInputDialogOpenChange = { maxFpsDialogOpen = it },
                            inputDialogOpen = maxFpsDialogOpen,
                        )
                    }
                }
                AnimatedVisibility(soBundle.videoSource == "camera") {
                    Column {
                        OverlaySpinnerWithFallback(
                            title = stringResource(R.string.scrcpyopt_camera_id),
                            summary = "--camera-id",
                            items = cameraSpinnerItems,
                            selectedIndex = cameraDropdownIndex,
                            dataLoaded = cameras.isNotEmpty(),
                            dataLoading = refreshBusy,
                            overrideEndActionValue = cameraOverrideEndActionValue,
                            onExpandedChange = { expanded ->
                                if (expanded && cameras.isEmpty()) {
                                    scope.launch {
                                        AppRuntime.snackbar(R.string.text_fetching)
                                        try {
                                            withContext(Dispatchers.IO) {
                                                scrcpy.listings.getCameras(forceRefresh = false)
                                            }
                                            AppRuntime.snackbar(R.string.text_fetch_success)
                                        } catch (e: Exception) {
                                            AppRuntime.snackbar(
                                                R.string.text_fetch_failed,
                                                e.message ?: "",
                                            )
                                        }
                                    }
                                }
                            },
                            onSelectedIndexChange = {
                                soBundle = soBundle.copy(
                                    cameraId =
                                        if (it == 0) ""
                                        else cameras[it - 1].id,
                                )
                            },
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.scrcpyopt_camera_facing),
                            summary = "--camera-facing",
                            items = cameraFacingItems,
                            selectedIndex = cameraFacingIndex,
                            onSelectedIndexChange = {
                                soBundle = soBundle.copy(
                                    cameraFacing =
                                        if (it == 0) ""
                                        else CameraFacing.entries[it].string,
                                )
                            },
                        )
                        OverlaySpinnerWithFallback(
                            title = stringResource(R.string.scrcpyopt_camera_size),
                            summary = "--camera-size",
                            items = cameraSizeSpinnerItems,
                            selectedIndex = cameraSizeDropdownIndex,
                            dataLoaded = cameraSizes.isNotEmpty(),
                            dataLoading = refreshBusy,
                            overrideEndActionValue = cameraSizeOverrideEndActionValue,
                            onExpandedChange = { expanded ->
                                if (expanded && cameraSizes.isEmpty()) {
                                    scope.launch {
                                        AppRuntime.snackbar(R.string.text_fetching)
                                        try {
                                            withContext(Dispatchers.IO) {
                                                scrcpy.listings.getCameraSizes(forceRefresh = false)
                                            }
                                            AppRuntime.snackbar(R.string.text_fetch_success)
                                        } catch (e: Exception) {
                                            AppRuntime.snackbar(
                                                R.string.text_fetch_failed,
                                                e.message ?: "",
                                            )
                                        }
                                    }
                                }
                            },
                            onSelectedIndexChange = {
                                when (it) {
                                    0 -> {
                                        // "自动"
                                        soBundle = soBundle.copy(
                                            cameraSize = "",
                                            cameraSizeUseCustom = false,
                                        )
                                        cameraSizeCustomInput = ""
                                    }

                                    1 -> {
                                        // "自定义" - 进入自定义输入模式
                                        soBundle = soBundle.copy(
                                            cameraSizeUseCustom = true,
                                        )
                                        cameraSizeCustomInput = ""
                                    }

                                    else -> {
                                        // 选择列表中的实际分辨率
                                        val selectedCameraSize = cameraSizes[it - 2]
                                        soBundle = soBundle.copy(
                                            cameraSize = selectedCameraSize,
                                            cameraSizeUseCustom = false,
                                        )
                                        cameraSizeCustomInput = ""
                                    }
                                }
                            },
                        )
                        // 只在选择"自定义"时显示输入框
                        AnimatedVisibility(soBundle.cameraSizeUseCustom) {
                            SuperTextField(
                                value = cameraSizeCustomInput,
                                onValueChange = { cameraSizeCustomInput = it },
                                onFocusLost = {
                                    soBundle = if (cameraSizeCustomInput in cameraSizes) {
                                        // 输入的值存在于列表中, 取消自定义输入
                                        soBundle.copy(
                                            cameraSize = cameraSizeCustomInput,
                                            cameraSizeUseCustom = false,
                                        )
                                    } else {
                                        soBundle.copy(
                                            cameraSizeCustom = cameraSizeCustomInput,
                                        )
                                    }
                                },
                                label = "--camera-size",
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(all = UiSpacing.Large),
                            )
                        }
                        SuperTextField(
                            value = cameraArInput,
                            onValueChange = { cameraArInput = it },
                            onFocusLost = {
                                soBundle = soBundle.copy(
                                    cameraAr = cameraArInput,
                                )
                            },
                            label = "--camera-ar",
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = UiSpacing.Large),
                        )
                        SuperTextField(
                            value = cameraZoomInput,
                            onValueChange = { cameraZoomInput = it },
                            onFocusLost = {
                                soBundle = soBundle.copy(
                                    cameraZoom = cameraZoomInput,
                                )
                            },
                            label = "--camera-zoom",
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = UiSpacing.Large),
                        )
                        ArrowSlider(
                            title = stringResource(R.string.scrcpyopt_camera_fps),
                            summary = "--camera-fps",
                            value = cameraFpsPresetIndex.toFloat(),
                            onValueChange = { value ->
                                val idx = value.roundToInt()
                                    .coerceIn(0, ScrcpyPresets.CameraFps.lastIndex)
                                soBundle = soBundle.copy(
                                    cameraFps = ScrcpyPresets.CameraFps[idx],
                                )
                            },
                            valueRange = 0f..ScrcpyPresets.CameraFps.lastIndex.toFloat(),
                            steps = (ScrcpyPresets.CameraFps.size - 2).coerceAtLeast(0),
                            unit = "fps",
                            zeroStateText = stringResource(R.string.text_default),
                            showKeyPoints = true,
                            keyPoints = ScrcpyPresets.CameraFps.indices.map { it.toFloat() },
                            displayText = soBundle.cameraFps.toString(),
                            inputInitialValue =
                                if (soBundle.cameraFps <= 0) ""
                                else soBundle.cameraFps.toString(),
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
                            onInputConfirm = {
                                soBundle = soBundle.copy(
                                    cameraFps = it.toIntOrNull() ?: run { 0 },
                                )
                            },
                            renderInputDialog = false,
                            onInputDialogOpenChange = { cameraFpsDialogOpen = it },
                            inputDialogOpen = cameraFpsDialogOpen,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.scrcpyopt_camera_high_speed),
                            summary = "--camera-high-speed",
                            checked = soBundle.cameraHighSpeed,
                            onCheckedChange = {
                                soBundle = soBundle.copy(
                                    cameraHighSpeed = it,
                                )
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.scrcpyopt_camera_torch),
                            summary = "--camera-torch",
                            checked = soBundle.cameraTorch,
                            onCheckedChange = {
                                soBundle = soBundle.copy(
                                    cameraTorch = it,
                                )
                            },
                        )
                    }
                }

            }
        }

        item {
            Card {
                OverlayDropdownPreference(
                    title = stringResource(R.string.scrcpyopt_audio_source),
                    summary = "--audio-source",
                    items = audioSourceItems,
                    selectedIndex = audioSourceIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            audioSource = AudioSource.entries[it].string,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_audio_dup),
                    summary = "--audio-dup",
                    checked = soBundle.audioDup,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            audioDup = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_require_audio),
                    summary = "--require-audio",
                    checked = soBundle.requireAudio,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            requireAudio = it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_ignore_video_encoder_constraints),
                    summary = "--ignore-video-encoder-constraints",
                    checked = soBundle.ignoreVideoEncoderConstraints,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            ignoreVideoEncoderConstraints = it,
                        )
                    },
                )
            }
        }

        item {
            Card {
                OverlaySpinnerWithFallback(
                    title = stringResource(R.string.scrcpyopt_video_encoder),
                    summary = "--video-encoder",
                    items = videoEncoderItems,
                    selectedIndex = videoEncoderIndex,
                    dataLoaded = videoEncoders.isNotEmpty(),
                    dataLoading = refreshBusy,
                    overrideEndActionValue = videoEncoderOverrideEndActionValue,
                    onExpandedChange = { expanded ->
                        if (expanded && videoEncoders.isEmpty()) {
                            scope.launch {
                                AppRuntime.snackbar(R.string.text_fetching)
                                try {
                                    withContext(Dispatchers.IO) {
                                        scrcpy.listings.getEncoders(forceRefresh = false)
                                    }
                                    AppRuntime.snackbar(R.string.text_fetch_success)
                                } catch (e: Exception) {
                                    AppRuntime.snackbar(
                                        R.string.text_fetch_failed,
                                        e.message ?: "",
                                    )
                                }
                            }
                        }
                    },
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            videoEncoder =
                                if (it == 0) ""
                                else videoEncoders[it - 1].id,
                        )
                    },
                )
                SuperTextField(
                    value = videoCodecOptionsInput,
                    onValueChange = { videoCodecOptionsInput = it },
                    onFocusLost = {
                        soBundle = soBundle.copy(
                            videoCodecOptions = videoCodecOptionsInput,
                        )
                    },
                    label = "--video-codec-options",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = UiSpacing.Large),
                )
                OverlaySpinnerWithFallback(
                    title = stringResource(R.string.scrcpyopt_audio_encoder),
                    summary = "--audio-encoder",
                    items = audioEncoderItems,
                    selectedIndex = audioEncoderIndex,
                    dataLoaded = audioEncoders.isNotEmpty(),
                    dataLoading = refreshBusy,
                    overrideEndActionValue = audioEncoderOverrideEndActionValue,
                    onExpandedChange = { expanded ->
                        if (expanded && audioEncoders.isEmpty()) {
                            scope.launch {
                                AppRuntime.snackbar(R.string.text_fetching)
                                try {
                                    withContext(Dispatchers.IO) {
                                        scrcpy.listings.getEncoders(forceRefresh = false)
                                    }
                                    AppRuntime.snackbar(R.string.text_fetch_success)
                                } catch (e: Exception) {
                                    AppRuntime.snackbar(
                                        R.string.text_fetch_failed,
                                        e.message ?: "",
                                    )
                                }
                            }
                        }
                    },
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            audioEncoder =
                                if (it == 0) ""
                                else audioEncoders[it - 1].id,
                        )
                    },
                )
                SuperTextField(
                    value = audioCodecOptionsInput,
                    onValueChange = { audioCodecOptionsInput = it },
                    onFocusLost = {
                        soBundle = soBundle.copy(
                            audioCodecOptions = audioCodecOptionsInput,
                        )
                    },
                    label = "--audio-codec-options",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = UiSpacing.Large),
                )
            }
        }

        if (soBundle.videoSource == "display") item {
            Card {
                OverlayDropdownPreference(
                    title = stringResource(R.string.scrcpyopt_ime_display_policy),
                    summary = "--display-ime-policy",
                    items = displayImePolicyItems,
                    selectedIndex = displayImePolicyIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            displayImePolicy =
                                if (it == 0) ""
                                else DisplayImePolicy.entries[it].string,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_vd_destroy_content),
                    summary = "--no-vd-destroy-content",
                    checked = !soBundle.vdDestroyContent,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            vdDestroyContent = !it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_vd_decorations),
                    summary = "--no-vd-system-decorations",
                    checked = !soBundle.vdSystemDecorations,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            vdSystemDecorations = !it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_downsize_on_error),
                    summary = "--no-downsize-on-error",
                    checked = !soBundle.downsizeOnError,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            downsizeOnError = !it,
                        )
                        if (it) AppRuntime.snackbar(
                            R.string.scrcpyopt_no_downsize_on_error_desc,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_legacy_paste),
                    summary = "--legacy-paste",
                    checked = soBundle.legacyPaste,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            legacyPaste = it,
                        )
                    },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.scrcpyopt_key_inject_mode),
                    summary = when (keyInjectModeIndex) {
                        1 -> "--prefer-text"
                        2 -> "--raw-key-events"
                        else -> stringResource(R.string.text_default)
                    },
                    items = keyInjectModeItems,
                    selectedIndex = keyInjectModeIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            keyInjectMode = when (it) {
                                1 -> ClientOptions.KeyInjectMode.PREFER_TEXT.string
                                2 -> ClientOptions.KeyInjectMode.RAW.string
                                else -> ClientOptions.KeyInjectMode.MIXED.string
                            },
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_key_repeat),
                    summary = "--no-key-repeat",
                    checked = !soBundle.forwardKeyRepeat,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            forwardKeyRepeat = !it,
                        )
                    },
                    enabled = soBundle.keyInjectMode != ClientOptions.KeyInjectMode.PREFER_TEXT.string,
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_clipboard_autosync),
                    summary = "--no-clipboard-autosync",
                    checked = !soBundle.clipboardAutosync,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            clipboardAutosync = !it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_mouse_hover),
                    summary = "--no-mouse-hover",
                    checked = !soBundle.mouseHover,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            mouseHover = !it,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_no_cleanup),
                    summary = "--no-cleanup",
                    checked = !soBundle.cleanup,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            cleanup = !it,
                        )
                        if (it) AppRuntime.snackbar(
                            R.string.scrcpyopt_no_cleanup_desc,
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_flex_display),
                    summary = "--flex-display",
                    checked = soBundle.flexDisplay,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            flexDisplay = it,
                        )
                        if (it) AppRuntime.snackbar(
                            "untested",
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.scrcpyopt_ignore_video_encoder_constraints),
                    summary = "--ignore-video-encoder-constraints",
                    checked = soBundle.ignoreVideoEncoderConstraints,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            ignoreVideoEncoderConstraints = it,
                        )
                    },
                )
            }
        }

        if (soBundle.videoSource == "display") item {
            Card {
                OverlaySpinnerWithFallback(
                    title = stringResource(R.string.scrcpyopt_start_app),
                    summary = "--start-app",
                    items = appDropdownItems,
                    selectedIndex = appDropdownIndex,
                    dataLoaded = apps.isNotEmpty(),
                    dataLoading = refreshBusy,
                    overrideEndActionValue = startAppOverrideEndActionValue,
                    searchable = true,
                    searchHint = stringResource(R.string.appmgr_search_hint),
                    onExpandedChange = { expanded ->
                        if (expanded) {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        scrcpy.listings.getApps(forceRefresh = true)
                                    }
                                }
                                val currentApps = scrcpy.listings.apps
                                if (currentApps.isNotEmpty() && appIconMap.size < currentApps.size) {
                                    val pkgs = currentApps.map { it.packageName }.toSet()
                                    val cached = pkgs
                                        .mapNotNull { pkg ->
                                            AppIconCache.getIconBase64(pkg)?.let { pkg to it }
                                        }
                                        .toMap()
                                    val missing = pkgs - cached.keys
                                    val fetched = if (missing.isNotEmpty()) {
                                        runCatching {
                                            AppManagerService.scrcpy = scrcpy
                                            AppManagerService.appContext = AppRuntime.context
                                            AppManagerService
                                                .fetchLabelsViaHelper(missing)
                                                .mapValues { it.value.iconBase64 ?: "" }
                                                .filterValues { it.isNotBlank() }
                                        }.getOrElse { emptyMap() }
                                    } else {
                                        emptyMap()
                                    }
                                    fetched.forEach { (pkg, b64) -> AppIconCache.putIcon(pkg, b64) }
                                    appIconMap = cached + fetched
                                }
                            }
                        }
                    },
                    onSelectedIndexChange = {
                        when (it) {
                            0 -> {
                                soBundle = soBundle.copy(
                                    startApp = "",
                                    startAppUseCustom = false,
                                )
                                startAppCustomInput = ""
                            }

                            1 -> {
                                soBundle = soBundle.copy(
                                    startAppUseCustom = true,
                                )
                                startAppCustomInput = ""
                            }

                            else -> {
                                val selectedApp = apps[it - 2]
                                soBundle = soBundle.copy(
                                    startApp = selectedApp.packageName,
                                    startAppUseCustom = false,
                                )
                                startAppCustomInput = ""
                            }
                        }
                    },
                )
                AnimatedVisibility(soBundle.startAppUseCustom) {
                    SuperTextField(
                        value = startAppCustomInput,
                        onValueChange = { startAppCustomInput = it },
                        onFocusLost = {
                            soBundle = if (startAppCustomInput in apps.map { it.packageName }) {
                                soBundle.copy(
                                    startApp = startAppCustomInput,
                                    startAppUseCustom = false,
                                )
                            } else {
                                soBundle.copy(
                                    startAppCustom = startAppCustomInput,
                                )
                            }
                        },
                        label = "--start-app",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = UiSpacing.Large),
                    )
                }
            }
        }

        if (soBundle.videoSource == "display") item {
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
                            text = "--new-display",
                            fontWeight = FontWeight.Medium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
                        ) {
                            SuperTextField(
                                label = "width",
                                value = newDisplayWidthInput,
                                onValueChange = { newDisplayWidthInput = it },
                                onFocusLost = {
                                    soBundle = soBundle.copy(
                                        newDisplay = NewDisplay.parseFrom(
                                            newDisplayWidthInput,
                                            newDisplayHeightInput,
                                            newDisplayDpiInput,
                                        ).toString(),
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            SuperTextField(
                                label = "height",
                                value = newDisplayHeightInput,
                                onValueChange = { newDisplayHeightInput = it },
                                onFocusLost = {
                                    soBundle = soBundle.copy(
                                        newDisplay = NewDisplay.parseFrom(
                                            newDisplayWidthInput,
                                            newDisplayHeightInput,
                                            newDisplayDpiInput,
                                        ).toString(),
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            SuperTextField(
                                label = "dpi",
                                value = newDisplayDpiInput,
                                onValueChange = { newDisplayDpiInput = it },
                                onFocusLost = {
                                    soBundle = soBundle.copy(
                                        newDisplay = NewDisplay.parseFrom(
                                            newDisplayWidthInput,
                                            newDisplayHeightInput,
                                            newDisplayDpiInput,
                                        ).toString(),
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() },
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.Large),
                    ) {
                        val gap = UiSpacing.ContentHorizontal
                        val inputWidth = (maxWidth - gap * 2) / 3
                        val trailingButtonWidth = inputWidth * 2 + gap
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            TextButton(
                                text = stringResource(R.string.cd_clear),
                                onClick = {
                                    newDisplayWidthInput = ""
                                    newDisplayHeightInput = ""
                                    newDisplayDpiInput = ""
                                    soBundle = soBundle.copy(newDisplay = "")
                                },
                                modifier = Modifier.width(inputWidth),
                                enabled = !newDisplayAllBlank,
                            )
                            TextButton(
                                text = stringResource(
                                    if (newDisplayWidthBlank || newDisplayHeightBlank) R.string.scrcpyopt_native
                                    else R.string.button_swap,
                                ),
                                onClick = {
                                    if (newDisplayWidthBlank || newDisplayHeightBlank) {
                                        val metrics = resources.displayMetrics
                                        newDisplayWidthInput = metrics.widthPixels.toString()
                                        newDisplayHeightInput = metrics.heightPixels.toString()
                                        newDisplayDpiInput = metrics.densityDpi
                                            .takeIf { it > 0 }
                                            ?.toString()
                                            .orEmpty()
                                    } else {
                                        val currentWidth = newDisplayWidthInput.trim()
                                        val currentHeight = newDisplayHeightInput.trim()
                                        newDisplayWidthInput = currentHeight
                                        newDisplayHeightInput = currentWidth
                                    }
                                    soBundle = soBundle.copy(
                                        newDisplay = NewDisplay.parseFrom(
                                            newDisplayWidthInput,
                                            newDisplayHeightInput,
                                            newDisplayDpiInput,
                                        ).toString(),
                                    )
                                },
                                modifier = Modifier.width(trailingButtonWidth),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }
        }

        if (soBundle.videoSource == "display") item {
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
                            text = "--crop",
                            fontWeight = FontWeight.Medium,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
                            ) {
                                SuperTextField(
                                    label = "width",
                                    value = cropWidthInput,
                                    onValueChange = { cropWidthInput = it },
                                    onFocusLost = {
                                        soBundle = soBundle.copy(
                                            crop = Crop.parseFrom(
                                                cropWidthInput,
                                                cropHeightInput,
                                                cropXInput,
                                                cropYInput,
                                            ).toString(),
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                SuperTextField(
                                    label = "height",
                                    value = cropHeightInput,
                                    onValueChange = { cropHeightInput = it },
                                    onFocusLost = {
                                        soBundle = soBundle.copy(
                                            crop = Crop.parseFrom(
                                                cropWidthInput,
                                                cropHeightInput,
                                                cropXInput,
                                                cropYInput,
                                            ).toString(),
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
                            ) {
                                SuperTextField(
                                    label = "x",
                                    value = cropXInput,
                                    onValueChange = { cropXInput = it },
                                    onFocusLost = {
                                        soBundle = soBundle.copy(
                                            crop = Crop.parseFrom(
                                                cropWidthInput,
                                                cropHeightInput,
                                                cropXInput,
                                                cropYInput,
                                            ).toString(),
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                SuperTextField(
                                    label = "y",
                                    value = cropYInput,
                                    onValueChange = { cropYInput = it },
                                    onFocusLost = {
                                        soBundle = soBundle.copy(
                                            crop = Crop.parseFrom(
                                                cropWidthInput,
                                                cropHeightInput,
                                                cropXInput,
                                                cropYInput,
                                            ).toString(),
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { focusManager.clearFocus() },
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card {
                OverlayDropdownPreference(
                    title = stringResource(R.string.scrcpyopt_log_level),
                    summary = "--verbosity",
                    items = logLevelItems,
                    selectedIndex = logLevelIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            logLevel = LogLevel.entries[it].string,
                        )
                    },
                )
            }
        }

    }

    // 弹窗渲染在 LazyColumn 外，避免 item 回收导致弹窗随 IME 弹出被销毁
    SliderInputDialog(
        showDialog = screenOffTimeoutDialogOpen,
        title = stringResource(R.string.scrcpyopt_screen_off_timeout),
        summary = "--screen-off-timeout",
        label = "s",
        initialValue =
            if (soBundle.screenOffTimeout <= 0) ""
            else Tick(soBundle.screenOffTimeout).sec.toString(),
        inputFilter = { it.filter(Char::isDigit) },
        inputValueRange = 0f..86400f,
        onDismissRequest = { screenOffTimeoutDialogOpen = false },
        onDismissFinished = { screenOffTimeoutDialogOpen = false },
        onConfirm = {
            screenOffTimeoutDialogOpen = false
            soBundle = soBundle.copy(
                screenOffTimeout = it.toLongOrNull()
                    ?.takeIf { value -> value > 0 }
                    ?.let(Tick::fromSec)
                    ?.value
                    ?: -1,
            )
        },
    )
    SliderInputDialog(
        showDialog = audioBitrateDialogOpen,
        title = stringResource(R.string.scrcpyopt_audio_bitrate),
        summary = "--audio-bit-rate",
        label = "Kbps",
        initialValue =
            if (soBundle.audioBitRate <= 0) ""
            else (soBundle.audioBitRate / 1_000).toString(),
        inputFilter = { it.filter(Char::isDigit) },
        inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
        onDismissRequest = { audioBitrateDialogOpen = false },
        onDismissFinished = { audioBitrateDialogOpen = false },
        onConfirm = { raw ->
            audioBitrateDialogOpen = false
            raw.toIntOrNull()
                ?.takeIf { it >= 0 }
                ?.let {
                    soBundle = soBundle.copy(
                        audioBitRate = it * 1000,
                    )
                }
        },
    )
    SliderInputDialog(
        showDialog = videoBitrateDialogOpen,
        title = stringResource(R.string.scrcpyopt_video_bitrate),
        summary = "--video-bit-rate",
        label = "Mbps",
        initialValue =
            if (soBundle.videoBitRate <= 0) ""
            else "%.1f".format(soBundle.videoBitRate / 1_000_000f),
        inputFilter = { text ->
            var dotUsed = false
            text.filter { ch ->
                when {
                    ch.isDigit() -> true
                    ch == '.' && !dotUsed -> {
                        dotUsed = true
                        true
                    }

                    else -> false
                }
            }
        },
        inputValueRange = 0f..UInt.MAX_VALUE.toFloat(),
        onDismissRequest = { videoBitrateDialogOpen = false },
        onDismissFinished = { videoBitrateDialogOpen = false },
        onConfirm = { raw ->
            videoBitrateDialogOpen = false
            raw.toFloatOrNull()?.let { parsed ->
                if (parsed >= 0f) {
                    soBundle = soBundle.copy(
                        videoBitRate = (parsed * 1_000_000f).roundToInt(),
                    )
                }
            }
        },
    )
    SliderInputDialog(
        showDialog = minSizeAlignmentDialogOpen,
        title = stringResource(R.string.scrcpyopt_min_size_alignment),
        summary = "--min-size-alignment",
        initialValue = soBundle.minSizeAlignment
            .takeIf { it != 1 }
            ?.toString()
            ?: "",
        inputFilter = { it.filter(Char::isDigit) },
        inputValueRange = 1f..16f,
        onDismissRequest = { minSizeAlignmentDialogOpen = false },
        onDismissFinished = { minSizeAlignmentDialogOpen = false },
        onConfirm = { raw ->
            minSizeAlignmentDialogOpen = false
            val parsed = raw.toIntOrNull() ?: return@SliderInputDialog
            if (parsed and (parsed - 1) != 0) {
                AppRuntime.snackbar(R.string.scrcpyopt_min_size_alignment_invalid)
                return@SliderInputDialog
            }
            soBundle = soBundle.copy(
                minSizeAlignment = parsed,
            )
        },
    )
    SliderInputDialog(
        showDialog = maxSizeDialogOpen,
        title = stringResource(R.string.scrcpyopt_max_size),
        summary = "--max-size",
        label = "px",
        initialValue = soBundle.maxSize
            .takeIf { it != 0 }
            ?.toString()
            ?: "",
        inputFilter = { it.filter(Char::isDigit) },
        inputValueRange = 0f..UInt.MAX_VALUE.toFloat(),
        onDismissRequest = { maxSizeDialogOpen = false },
        onDismissFinished = { maxSizeDialogOpen = false },
        onConfirm = {
            maxSizeDialogOpen = false
            soBundle = soBundle.copy(
                maxSize = it.toIntOrNull() ?: run { 0 },
            )
        },
    )
    SliderInputDialog(
        showDialog = maxFpsDialogOpen,
        title = stringResource(R.string.scrcpyopt_max_fps),
        summary = "--max-fps",
        label = "fps",
        initialValue = soBundle.maxFps,
        inputFilter = { it.filter(Char::isDigit) },
        inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
        onDismissRequest = { maxFpsDialogOpen = false },
        onDismissFinished = { maxFpsDialogOpen = false },
        onConfirm = {
            maxFpsDialogOpen = false
            soBundle = soBundle.copy(
                maxFps = it,
            )
        },
    )
    SliderInputDialog(
        showDialog = cameraFpsDialogOpen,
        title = stringResource(R.string.scrcpyopt_camera_fps),
        summary = "--camera-fps",
        label = "fps",
        initialValue =
            if (soBundle.cameraFps <= 0) ""
            else soBundle.cameraFps.toString(),
        inputFilter = { it.filter(Char::isDigit) },
        inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
        onDismissRequest = { cameraFpsDialogOpen = false },
        onDismissFinished = { cameraFpsDialogOpen = false },
        onConfirm = {
            cameraFpsDialogOpen = false
            soBundle = soBundle.copy(
                cameraFps = it.toIntOrNull() ?: run { 0 },
            )
        },
    )
}

private enum class ProfileDialogMode {
    Create,
    Rename,
}

@Composable
private fun ProfileNameDialog(
    mode: ProfileDialogMode?,
    initialInput: String,
    profiles: List<ScrcpyProfiles.Profile>,
    initialCopySourceProfileId: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    if (mode == null) return

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    val textDefault = stringResource(R.string.text_default)

    var input by rememberSaveable(mode, initialInput) { mutableStateOf(initialInput) }
    val profileNames = remember(profiles) { profiles.map { it.name } }
    val profileIds = remember(profiles) { profiles.map { it.id } }
    val copySourceItems = remember(profileNames) {
        listOf(textDefault) + profileNames
    }
    var copySourceProfileId by rememberSaveable(mode, initialCopySourceProfileId) {
        mutableStateOf(initialCopySourceProfileId)
    }
    val copySourceDropdownIndex = remember(copySourceProfileId, profileIds) {
        copySourceProfileId
            ?.let { profileIds.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) }
            ?: 0
    }

    OverlayDialog(
        show = true,
        title = when (mode) {
            ProfileDialogMode.Create -> stringResource(R.string.scrcpyopt_new_profile)
            ProfileDialogMode.Rename -> stringResource(R.string.scrcpyopt_rename_profile)
        },
        summary = stringResource(R.string.scrcpyopt_duplicate_name_hint),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            SuperTextField(
                value = input,
                onValueChange = { input = it },
                label = stringResource(R.string.scrcpyopt_profile_name),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(mode == ProfileDialogMode.Create) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.scrcpyopt_copy_from),
                    items = copySourceItems,
                    selectedIndex = copySourceDropdownIndex,
                    onSelectedIndexChange = { index ->
                        copySourceProfileId = if (index == 0) {
                            null
                        } else {
                            profileIds.getOrElse(index - 1) {
                                ScrcpyOptions.GLOBAL_PROFILE_ID
                            }
                        }
                    },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = {
                        haptic.contextClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    onClick = {
                        haptic.confirm()
                        onConfirm(input, copySourceProfileId)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun ManageProfilesSheet(
    show: Boolean,
    profiles: List<ScrcpyProfiles.Profile>,
    selectedProfileId: String,
    onDismissRequest: () -> Unit,
    onCreateProfile: () -> Unit,
    onRenameProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onMoveProfile: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.scrcpyopt_manage_profiles),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(onClick = onCreateProfile) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.scrcpyopt_new_profile),
                )
            }
        },
    ) {
        val textCurrent = stringResource(R.string.scrcpyopt_current_profile)
        val textRename = stringResource(R.string.scrcpyopt_rename_profile)
        val textDelete = stringResource(R.string.scrcpyopt_delete_profile)
        val textGlobal = stringResource(R.string.text_global)
        ReorderableList(
            itemsProvider = {
                profiles.map { profile ->
                    ReorderableList.Item(
                        id = profile.id,
                        title = if (profile.isBuiltinGlobal) textGlobal else profile.name,
                        subtitle =
                            if (profile.id == selectedProfileId) textCurrent
                            else "",
                        onClick =
                            if (profile.id != ScrcpyOptions.GLOBAL_PROFILE_ID) {
                                { onRenameProfile(profile.id) }
                            } else null,
                        dragEnabled = profile.id != ScrcpyOptions.GLOBAL_PROFILE_ID,
                        endActions = buildList {
                            if (profile.id != ScrcpyOptions.GLOBAL_PROFILE_ID) {
                                add(
                                    ReorderableList.EndAction.Icon(
                                        icon = Icons.Rounded.Edit,
                                        contentDescription = textRename,
                                        onClick = { onRenameProfile(profile.id) },
                                    ),
                                )
                                add(
                                    ReorderableList.EndAction.Icon(
                                        icon = Icons.Rounded.DeleteOutline,
                                        contentDescription = textDelete,
                                        onClick = { onDeleteProfile(profile.id) },
                                    ),
                                )
                            }
                        },
                    )
                }
            },
            onSettle = onMoveProfile,
        ).invoke()
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }
}

@Composable
private fun DeleteProfileDialog(
    show: Boolean,
    profileName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    OverlayDialog(
        show = show,
        title = stringResource(R.string.scrcpyopt_delete_profile),
        summary = stringResource(R.string.scrcpyopt_delete_confirm, profileName),
        onDismissRequest = onDismissRequest,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = {
                    haptic.contextClick()
                    onDismissRequest()
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.button_delete),
                onClick = {
                    haptic.confirm()
                    onConfirm()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
