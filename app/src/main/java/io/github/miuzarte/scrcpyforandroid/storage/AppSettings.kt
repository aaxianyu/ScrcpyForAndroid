package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.*
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class AppSettings(context: Context): Settings(context, "AppSettings") {
    object ThemeModes {
        data class Option(
            @field:StringRes val labelResId: Int,
            val mode: ColorSchemeMode,
        )

        val baseOptions = listOf(
            Option(R.string.theme_follow_system, ColorSchemeMode.System),
            Option(R.string.theme_light, ColorSchemeMode.Light),
            Option(R.string.theme_dark, ColorSchemeMode.Dark),
        )
    }

    enum class FullscreenVirtualButtonDock(
        val rawValue: String,
        val isFixed: Boolean,
        @field:StringRes val directionLabelResId: Int,
    ) {
        FOLLOW_TOP("FOLLOW_TOP", false, R.string.dock_top),
        FOLLOW_BOTTOM("FOLLOW_BOTTOM", false, R.string.dock_bottom),
        FOLLOW_LEFT("FOLLOW_LEFT", false, R.string.dock_left),
        FOLLOW_RIGHT("FOLLOW_RIGHT", false, R.string.dock_right),
        FIXED_TOP("FIXED_TOP", true, R.string.dock_top),
        FIXED_BOTTOM("FIXED_BOTTOM", true, R.string.dock_bottom),
        FIXED_LEFT("FIXED_LEFT", true, R.string.dock_left),
        FIXED_RIGHT("FIXED_RIGHT", true, R.string.dock_right);

        fun toStoredValue(): String = rawValue

        val modeIndex: Int
            get() = if (!isFixed) 0 else 1

        val directionIndex: Int
            get() = when (this) {
                FOLLOW_TOP, FIXED_TOP -> 0
                FOLLOW_BOTTOM, FIXED_BOTTOM -> 1
                FOLLOW_LEFT, FIXED_LEFT -> 2
                FOLLOW_RIGHT, FIXED_RIGHT -> 3
            }

        companion object {
            val modeItemsResIds = listOf(
                R.string.dock_follow,
                R.string.dock_fixed,
            )
            val directionItemsResIds = listOf(
                R.string.dock_top,
                R.string.dock_bottom,
                R.string.dock_left,
                R.string.dock_right,
            )

            fun fromBundle(bundle: Bundle) =
                entries.firstOrNull { it.rawValue == bundle.fullscreenVirtualButtonDock }
                    ?: FOLLOW_BOTTOM

            fun fromStoredValue(value: String) =
                entries.firstOrNull { it.rawValue == value }
                    ?: FOLLOW_BOTTOM

            fun fromModeAndDirection(modeIndex: Int, directionIndex: Int) =
                when (directionIndex) {
                    0 -> if (modeIndex == 0) FOLLOW_TOP else FIXED_TOP
                    1 -> if (modeIndex == 0) FOLLOW_BOTTOM else FIXED_BOTTOM
                    2 -> if (modeIndex == 0) FOLLOW_LEFT else FIXED_LEFT
                    3 -> if (modeIndex == 0) FOLLOW_RIGHT else FIXED_RIGHT
                    else -> if (modeIndex == 0) FOLLOW_BOTTOM else FIXED_BOTTOM
                }
        }
    }

    enum class FullscreenControlMode(val rawValue: String) {
        VIRTUAL_BUTTONS("VIRTUAL_BUTTONS"),
        SWIPE_FLOATING_BALL("SWIPE_FLOATING_BALL"),
        OFF("OFF");

        fun toStoredValue(): String = rawValue

        companion object {
            fun fromStoredValue(value: String) =
                entries.firstOrNull { it.rawValue == value } ?: VIRTUAL_BUTTONS
        }
    }

    companion object {
        val LANGUAGE_TAG = Pair(
            stringPreferencesKey("language_tag"),
            "",
        )

        // Theme
        val THEME_BASE_INDEX = Pair(
            intPreferencesKey("theme_base_index"),
            0,
        )
        val MONET = Pair(
            booleanPreferencesKey("monet"),
            false,
        )
        val MONET_SEED_INDEX = Pair(
            intPreferencesKey("monet_seed_index"),
            0,
        )
        val MONET_PALETTE_STYLE = Pair(
            intPreferencesKey("monet_palette_style"),
            0,
        )
        val MONET_COLOR_SPEC = Pair(
            intPreferencesKey("monet_color_spec"),
            0,
        )
        val BLUR = Pair(
            booleanPreferencesKey("blur"),
            true,
        )
        val FLOATING_BOTTOM_BAR = Pair(
            booleanPreferencesKey("floating_bottom_bar"),
            false,
        )
        val FLOATING_BOTTOM_BAR_BLUR = Pair(
            booleanPreferencesKey("floating_bottom_bar_blur"),
            false,
        )

        // Scrcpy
        val LOW_LATENCY = Pair(
            booleanPreferencesKey("low_latency"),
            false,
        )
        val DOWNSIZE_ON_DECODE_ERROR = Pair(
            booleanPreferencesKey("downsize_on_decode_error"),
            true,
        )
        val FULLSCREEN_DEBUG_INFO = Pair(
            booleanPreferencesKey("fullscreen_debug_info"),
            false,
        )
        val HIDE_SIMPLE_CONFIG_ITEMS = Pair(
            booleanPreferencesKey("hide_simple_config_items"),
            false,
        )
        val PREVIEW_CARD_ON_TOP = Pair(
            booleanPreferencesKey("preview_card_on_top"),
            false,
        )
        val DEVICE_PREVIEW_CARD_HEIGHT_DP = Pair(
            intPreferencesKey("device_preview_card_height_dp"),
            1080 / 3,
        )
        val PREVIEW_CARD_TAP_TO_FULLSCREEN = Pair(
            booleanPreferencesKey("preview_card_tap_to_fullscreen"),
            false,
        )
        val REALTIME_CLIPBOARD_SYNC_TO_DEVICE = Pair(
            booleanPreferencesKey("realtime_clipboard_sync_to_device"),
            true,
        )

        // Fullscreen
        val FULLSCREEN_CONTROL_IGNORE_SYSTEM_ROTATION_LOCK = Pair(
            booleanPreferencesKey("fullscreen_control_ignore_system_rotation_lock"),
            true,
        )
        val FULLSCREEN_CONTROL_BACK_TO_DEVICE = Pair(
            booleanPreferencesKey("fullscreen_control_back_to_device"),
            false,
        )
        val SHOW_FULLSCREEN_VIRTUAL_BUTTONS = Pair(
            booleanPreferencesKey("show_fullscreen_virtual_buttons"),
            true,
        )
        val FULLSCREEN_VIRTUAL_BUTTON_HEIGHT_DP = Pair(
            intPreferencesKey("fullscreen_virtual_button_height_dp"),
            16,
        )
        val FULLSCREEN_VIRTUAL_BUTTON_DOCK = Pair(
            stringPreferencesKey("fullscreen_virtual_button_dock"),
            FullscreenVirtualButtonDock.FIXED_BOTTOM.toStoredValue(),
        )
        val SHOW_FULLSCREEN_FLOATING_BUTTON = Pair(
            booleanPreferencesKey("show_fullscreen_floating_button"),
            false,
        )
        val FULLSCREEN_FLOATING_BUTTON_SIZE_DP = Pair(
            intPreferencesKey("fullscreen_floating_button_size_dp"),
            48,
        )
        val TEMP_FLOATING_BUTTON_SIZE_DP = Pair(
            intPreferencesKey("temp_floating_button_size_dp"),
            48,
        )
        val SWIPE_FLOATING_MENU_ITEM_COUNT = Pair(
            intPreferencesKey("swipe_floating_menu_item_count"),
            5,
        )
        val FULLSCREEN_CONTROL_MODE = Pair(
            stringPreferencesKey("fullscreen_control_mode"),
            FullscreenControlMode.VIRTUAL_BUTTONS.rawValue,
        )
        val SWIPE_FLOATING_BALL_OFFSET_X = Pair(
            floatPreferencesKey("swipe_floating_ball_offset_x"),
            0.85f,
        )
        val SWIPE_FLOATING_BALL_OFFSET_Y = Pair(
            floatPreferencesKey("swipe_floating_ball_offset_y"),
            0.85f,
        )
        val SWIPE_FLOATING_BALL_BACKGROUND_ALPHA_PERCENT = Pair(
            intPreferencesKey("swipe_floating_ball_background_alpha_percent"),
            50,
        )
        val SWIPE_FLOATING_BALL_RING_ALPHA_PERCENT = Pair(
            intPreferencesKey("swipe_floating_ball_ring_alpha_percent"),
            50,
        )
        val SHOW_SWIPE_FLOATING_BALL = Pair(
            booleanPreferencesKey("show_swipe_floating_ball"),
            false,
        )
        val FULLSCREEN_FLOATING_BUTTON_BACKGROUND_ALPHA_PERCENT = Pair(
            intPreferencesKey("fullscreen_floating_button_background_alpha_percent"),
            25,
        )
        val FULLSCREEN_FLOATING_BUTTON_RING_ALPHA_PERCENT = Pair(
            intPreferencesKey("fullscreen_floating_button_ring_alpha_percent"),
            100,
        )
        val FULLSCREEN_COMPATIBILITY_MODE = Pair(
            booleanPreferencesKey("fullscreen_compatibility_mode"),
            false,
        )

        val FULLSCREEN_FLOATING_BUTTON_X_FRACTION = Pair(
            floatPreferencesKey("fullscreen_floating_button_x_fraction"),
            0.84f,
        )
        val FULLSCREEN_FLOATING_BUTTON_Y_FRACTION = Pair(
            floatPreferencesKey("fullscreen_floating_button_y_fraction"),
            0.72f,
        )
        val PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT = Pair(
            booleanPreferencesKey("preview_virtual_button_show_text"),
            false,
        )
        // 虚拟按钮排序（格式：id:showOutside,id:showOutside,...）
        // showOutside=1 表示显示在外部，=0 表示显示在更多菜单内
        // 默认外部：更多、多任务、主页、返回、退出全屏
        // 注意：HIDE_SWIPE_FLOATING_BALL 不在此默认布局中，由 parseStoredLayout 自动补充
        val VIRTUAL_BUTTONS_LAYOUT = Pair(
            stringPreferencesKey("virtual_buttons_layout"),
            "more:1" +
                    ",app_switch:1,home:1,back:1,exit_fullscreen:1" +
                    ",show_swipe_floating_ball:0,power:0" +
                    ",all_apps:0,paste_local_clipboard:0,recent_tasks:0,toggle_ime:0" +
                    ",expand_status_bar:0,notification:0" +
                    ",volume_up:0,volume_down:0,volume_mute:0" +
                    ",screenshot:0,menu:0,password_input:0" +
                    "",
        )
        // 滑动悬浮球菜单项的独立排序（格式：id:visible,id:visible,...）
        // 不包含 SHOW_SWIPE_FLOATING_BALL 和 MORE
        // 默认滑动显示：锁屏、多任务、主页、退出全屏
        val SWIPE_FLOATING_BALL_ACTIONS_LAYOUT = Pair(
            stringPreferencesKey("swipe_floating_ball_actions_layout"),
            "power:1,app_switch:1,home:1,exit_fullscreen:1" +
                    ",hide_swipe_floating_ball:0,back:0" +
                    ",all_apps:0,paste_local_clipboard:0,recent_tasks:0,toggle_ime:0" +
                    ",expand_status_bar:0,notification:0" +
                    ",volume_up:0,volume_down:0,volume_mute:0" +
                    ",screenshot:0,menu:0,password_input:0" +
                    "",
        )
        val DEVICE_TWO_PANE_CONFIG_ON_RIGHT = Pair(
            booleanPreferencesKey("device_two_pane_config_on_right"),
            false,
        )

        // Scrcpy Server
        val CUSTOM_SERVER_URI = Pair(
            stringPreferencesKey("custom_server_uri"),
            "",
        )
        val CUSTOM_SERVER_VERSION = Pair(
            stringPreferencesKey("custom_server_version"),
            "",
        )
        val SERVER_REMOTE_PATH = Pair(
            stringPreferencesKey("server_remote_path"),
            Scrcpy.DEFAULT_REMOTE_PATH,
        )

        // ADB
        val ADB_KEY_NAME = Pair(
            stringPreferencesKey("adb_key_name"),
            "scrcpy",
        )
        val ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN = Pair(
            booleanPreferencesKey("adb_pairing_auto_discover_on_dialog_open"),
            true,
        )
        val ADB_AUTO_RECONNECT_PAIRED_DEVICE = Pair(
            booleanPreferencesKey("adb_auto_reconnect_paired_device"),
            true,
        )
        val ADB_MDNS_LAN_DISCOVERY = Pair(
            // 没必要加开关, 保持启用
            booleanPreferencesKey("adb_mdns_lan_discovery"),
            true,
        )
        val ADB_AUTO_LOAD_APP_LIST_ON_CONNECT = Pair(
            booleanPreferencesKey("adb_auto_load_app_list_on_connect"),
            false,
        )
        val ADB_FLOW_CONTROL_WINDOW = Pair(
            intPreferencesKey("adb_flow_control_window"),
            0,
        )

        // Terminal
        val TERMINAL_FONT_SIZE_SP = Pair(
            floatPreferencesKey("terminal_font_size_sp"),
            12f,
        )
        val TERMINAL_FONT_DISPLAY_NAME = Pair(
            stringPreferencesKey("terminal_font_display_name"),
            "",
        )
        val TERMINAL_BOOKMARK_AUTO_ENTER = Pair(
            booleanPreferencesKey("terminal_bookmark_auto_enter"),
            false,
        )

        val PASSWORD_REQUIRE_AUTH = Pair(
            booleanPreferencesKey("password_require_auth"),
            true,
        )

        val FILE_MANAGER_SORT_BY = Pair(
            stringPreferencesKey("file_manager_sort_by"),
            "NAME",
        )
        val FILE_MANAGER_SORT_DESCENDING = Pair(
            booleanPreferencesKey("file_manager_sort_descending"),
            false,
        )
        val LAST_UPDATE_CHECK_AT = Pair(
            longPreferencesKey("last_update_check_at"),
            0L,
        )
        val CLEAR_LOGS_ON_EXIT = Pair(
            booleanPreferencesKey("clear_logs_on_exit"),
            true,
        )
        val HIDE_DEVICE_LOGS = Pair(
            booleanPreferencesKey("hide_device_logs"),
            false,
        )
        val FAVORITE_APPS = Pair(
            stringPreferencesKey("favorite_apps"),
            "",
        )
        val SHOW_APP_ICONS = Pair(
            booleanPreferencesKey("show_app_icons"),
            true,
        )
        val SNACKBAR_DURATION_MS = Pair(
            intPreferencesKey("snackbar_duration_ms"),
            3000,
        )
    }

    @Parcelize
    data class Bundle(
        // Theme
        val languageTag: String,
        val themeBaseIndex: Int,
        val monet: Boolean,
        val monetSeedIndex: Int,
        val monetPaletteStyle: Int,
        val monetColorSpec: Int,
        val blur: Boolean,
        val floatingBottomBar: Boolean,
        val floatingBottomBarBlur: Boolean,

        // Scrcpy
        val lowLatency: Boolean,
        val downsizeOnDecodeError: Boolean,
        val fullscreenDebugInfo: Boolean,
        val hideSimpleConfigItems: Boolean,
        val previewCardOnTop: Boolean,
        val devicePreviewCardHeightDp: Int,
        val previewCardTapToFullscreen: Boolean,
        val realtimeClipboardSyncToDevice: Boolean,

        // Fullscreen
        val fullscreenControlIgnoreSystemRotationLock: Boolean,
        val fullscreenControlBackToDevice: Boolean,
        val showFullscreenVirtualButtons: Boolean,
        val fullscreenVirtualButtonHeightDp: Int,
        val fullscreenVirtualButtonDock: String,
        val showFullscreenFloatingButton: Boolean,
        val fullscreenFloatingButtonSizeDp: Int,
        val tempFloatingButtonSizeDp: Int,
        val swipeFloatingMenuItemCount: Int,
        val showSwipeFloatingBall: Boolean,
        val fullscreenControlMode: String,
        val swipeFloatingBallOffsetX: Float,
        val swipeFloatingBallOffsetY: Float,
        val swipeFloatingBallBackgroundAlphaPercent: Int,
        val swipeFloatingBallRingAlphaPercent: Int,
        val fullscreenFloatingButtonBackgroundAlphaPercent: Int,
        val fullscreenFloatingButtonRingAlphaPercent: Int,
        val fullscreenCompatibilityMode: Boolean,

        val fullscreenFloatingButtonXFraction: Float,
        val fullscreenFloatingButtonYFraction: Float,
        val previewVirtualButtonShowText: Boolean,
        val virtualButtonsLayout: String,
        val swipeFloatingBallActionsLayout: String,
        val deviceTwoPaneConfigOnRight: Boolean,

        // Scrcpy Server
        val customServerUri: String,
        val customServerVersion: String,
        val serverRemotePath: String,

        // ADB
        val adbKeyName: String,
        val adbPairingAutoDiscoverOnDialogOpen: Boolean,
        val adbAutoReconnectPairedDevice: Boolean,
        val adbMdnsLanDiscovery: Boolean,
        val adbAutoLoadAppListOnConnect: Boolean,
        val adbFlowControlWindow: Int,

        // Terminal
        val terminalFontSizeSp: Float,
        val terminalFontDisplayName: String,
        val terminalBookmarkAutoEnter: Boolean,

        val passwordRequireAuth: Boolean,

        val fileManagerSortBy: String,
        val fileManagerSortDescending: Boolean,
        val lastUpdateCheckAt: Long,
        val clearLogsOnExit: Boolean,
        val hideDeviceLogs: Boolean,
        val favoriteApps: String,
        val showAppIcons: Boolean,
        val snackbarDurationMs: Int,
    ) : Parcelable {
    }

    private val bundleFields = arrayOf<BundleField<Bundle>>(
        // Theme
        bundleField(LANGUAGE_TAG) { it.languageTag },
        bundleField(THEME_BASE_INDEX) { it.themeBaseIndex },
        bundleField(MONET) { it.monet },
        bundleField(MONET_SEED_INDEX) { it.monetSeedIndex },
        bundleField(MONET_PALETTE_STYLE) { it.monetPaletteStyle },
        bundleField(MONET_COLOR_SPEC) { it.monetColorSpec },
        bundleField(BLUR) { it.blur },
        bundleField(FLOATING_BOTTOM_BAR) { it.floatingBottomBar },
        bundleField(FLOATING_BOTTOM_BAR_BLUR) { it.floatingBottomBarBlur },

        // Scrcpy
        bundleField(LOW_LATENCY) { it.lowLatency },
        bundleField(DOWNSIZE_ON_DECODE_ERROR) { it.downsizeOnDecodeError },
        bundleField(FULLSCREEN_DEBUG_INFO) { it.fullscreenDebugInfo },
        bundleField(HIDE_SIMPLE_CONFIG_ITEMS) { it.hideSimpleConfigItems },
        bundleField(PREVIEW_CARD_ON_TOP) { it.previewCardOnTop },
        bundleField(DEVICE_PREVIEW_CARD_HEIGHT_DP) { it.devicePreviewCardHeightDp },
        bundleField(PREVIEW_CARD_TAP_TO_FULLSCREEN) { it.previewCardTapToFullscreen },
        bundleField(REALTIME_CLIPBOARD_SYNC_TO_DEVICE) { it.realtimeClipboardSyncToDevice },

        // Fullscreen
        bundleField(FULLSCREEN_CONTROL_IGNORE_SYSTEM_ROTATION_LOCK) { it.fullscreenControlIgnoreSystemRotationLock },
        bundleField(FULLSCREEN_CONTROL_BACK_TO_DEVICE) { it.fullscreenControlBackToDevice },
        bundleField(SHOW_FULLSCREEN_VIRTUAL_BUTTONS) { it.showFullscreenVirtualButtons },
        bundleField(FULLSCREEN_VIRTUAL_BUTTON_HEIGHT_DP) { it.fullscreenVirtualButtonHeightDp },
        bundleField(FULLSCREEN_VIRTUAL_BUTTON_DOCK) { it.fullscreenVirtualButtonDock },
        bundleField(SHOW_FULLSCREEN_FLOATING_BUTTON) { it.showFullscreenFloatingButton },
        bundleField(FULLSCREEN_FLOATING_BUTTON_SIZE_DP) { it.fullscreenFloatingButtonSizeDp },
        bundleField(TEMP_FLOATING_BUTTON_SIZE_DP) { it.tempFloatingButtonSizeDp },
        bundleField(SWIPE_FLOATING_MENU_ITEM_COUNT) { it.swipeFloatingMenuItemCount },
        bundleField(FULLSCREEN_CONTROL_MODE) { it.fullscreenControlMode },
        bundleField(SWIPE_FLOATING_BALL_OFFSET_X) { it.swipeFloatingBallOffsetX },
        bundleField(SWIPE_FLOATING_BALL_OFFSET_Y) { it.swipeFloatingBallOffsetY },
        bundleField(SWIPE_FLOATING_BALL_BACKGROUND_ALPHA_PERCENT) { it.swipeFloatingBallBackgroundAlphaPercent },
        bundleField(SWIPE_FLOATING_BALL_RING_ALPHA_PERCENT) { it.swipeFloatingBallRingAlphaPercent },
        bundleField(SHOW_SWIPE_FLOATING_BALL) { it.showSwipeFloatingBall },
        bundleField(FULLSCREEN_FLOATING_BUTTON_BACKGROUND_ALPHA_PERCENT) { it.fullscreenFloatingButtonBackgroundAlphaPercent },
        bundleField(FULLSCREEN_FLOATING_BUTTON_RING_ALPHA_PERCENT) { it.fullscreenFloatingButtonRingAlphaPercent },
        bundleField(FULLSCREEN_COMPATIBILITY_MODE) { it.fullscreenCompatibilityMode },

        bundleField(FULLSCREEN_FLOATING_BUTTON_X_FRACTION) { it.fullscreenFloatingButtonXFraction },
        bundleField(FULLSCREEN_FLOATING_BUTTON_Y_FRACTION) { it.fullscreenFloatingButtonYFraction },
        bundleField(PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT) { it.previewVirtualButtonShowText },
        bundleField(VIRTUAL_BUTTONS_LAYOUT) { it.virtualButtonsLayout },
        bundleField(SWIPE_FLOATING_BALL_ACTIONS_LAYOUT) { it.swipeFloatingBallActionsLayout },
        bundleField(DEVICE_TWO_PANE_CONFIG_ON_RIGHT) { it.deviceTwoPaneConfigOnRight },

        // Scrcpy Server
        bundleField(CUSTOM_SERVER_URI) { it.customServerUri },
        bundleField(CUSTOM_SERVER_VERSION) { it.customServerVersion },
        bundleField(SERVER_REMOTE_PATH) { it.serverRemotePath },

        // ADB
        bundleField(ADB_KEY_NAME) { it.adbKeyName },
        bundleField(ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN) { it.adbPairingAutoDiscoverOnDialogOpen },
        bundleField(ADB_AUTO_RECONNECT_PAIRED_DEVICE) { it.adbAutoReconnectPairedDevice },
        bundleField(ADB_MDNS_LAN_DISCOVERY) { it.adbMdnsLanDiscovery },
        bundleField(ADB_AUTO_LOAD_APP_LIST_ON_CONNECT) { it.adbAutoLoadAppListOnConnect },
        bundleField(ADB_FLOW_CONTROL_WINDOW) { it.adbFlowControlWindow },

        // Terminal
        bundleField(TERMINAL_FONT_SIZE_SP) { it.terminalFontSizeSp },
        bundleField(TERMINAL_FONT_DISPLAY_NAME) { it.terminalFontDisplayName },
        bundleField(TERMINAL_BOOKMARK_AUTO_ENTER) { it.terminalBookmarkAutoEnter },

        bundleField(PASSWORD_REQUIRE_AUTH) { it.passwordRequireAuth },

        bundleField(FILE_MANAGER_SORT_BY) { it.fileManagerSortBy },
        bundleField(FILE_MANAGER_SORT_DESCENDING) { it.fileManagerSortDescending },
        bundleField(LAST_UPDATE_CHECK_AT) { it.lastUpdateCheckAt },
        bundleField(CLEAR_LOGS_ON_EXIT) { it.clearLogsOnExit },
        bundleField(HIDE_DEVICE_LOGS) { it.hideDeviceLogs },
        bundleField(FAVORITE_APPS) { it.favoriteApps },
        bundleField(SHOW_APP_ICONS) { it.showAppIcons },
        bundleField(SNACKBAR_DURATION_MS) { it.snackbarDurationMs },
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = Bundle(
        // Theme
        languageTag = preferences.read(LANGUAGE_TAG),
        themeBaseIndex = preferences.read(THEME_BASE_INDEX),
        monet = preferences.read(MONET),
        monetSeedIndex = preferences.read(MONET_SEED_INDEX),
        monetPaletteStyle = preferences.read(MONET_PALETTE_STYLE),
        monetColorSpec = preferences.read(MONET_COLOR_SPEC),
        blur = preferences.read(BLUR),
        floatingBottomBar = preferences.read(FLOATING_BOTTOM_BAR),
        floatingBottomBarBlur = preferences.read(FLOATING_BOTTOM_BAR_BLUR),

        // Scrcpy
        lowLatency = preferences.read(LOW_LATENCY),
        downsizeOnDecodeError = preferences.read(DOWNSIZE_ON_DECODE_ERROR),
        fullscreenDebugInfo = preferences.read(FULLSCREEN_DEBUG_INFO),
        hideSimpleConfigItems = preferences.read(HIDE_SIMPLE_CONFIG_ITEMS),
        previewCardOnTop = preferences.read(PREVIEW_CARD_ON_TOP),
        devicePreviewCardHeightDp = preferences.read(DEVICE_PREVIEW_CARD_HEIGHT_DP),
        previewCardTapToFullscreen = preferences.read(PREVIEW_CARD_TAP_TO_FULLSCREEN),
        realtimeClipboardSyncToDevice = preferences.read(REALTIME_CLIPBOARD_SYNC_TO_DEVICE),

        // Fullscreen
        fullscreenControlIgnoreSystemRotationLock =
            preferences.read(FULLSCREEN_CONTROL_IGNORE_SYSTEM_ROTATION_LOCK),
        fullscreenControlBackToDevice = preferences.read(FULLSCREEN_CONTROL_BACK_TO_DEVICE),
        showFullscreenVirtualButtons = preferences.read(SHOW_FULLSCREEN_VIRTUAL_BUTTONS),
        fullscreenVirtualButtonHeightDp = preferences.read(FULLSCREEN_VIRTUAL_BUTTON_HEIGHT_DP),
        fullscreenVirtualButtonDock = preferences.read(FULLSCREEN_VIRTUAL_BUTTON_DOCK),
        showFullscreenFloatingButton = preferences.read(SHOW_FULLSCREEN_FLOATING_BUTTON),
        fullscreenFloatingButtonSizeDp = preferences.read(FULLSCREEN_FLOATING_BUTTON_SIZE_DP),
        tempFloatingButtonSizeDp = preferences.read(TEMP_FLOATING_BUTTON_SIZE_DP),
        swipeFloatingMenuItemCount = preferences.read(SWIPE_FLOATING_MENU_ITEM_COUNT),
        showSwipeFloatingBall = preferences.read(SHOW_SWIPE_FLOATING_BALL),
        fullscreenControlMode = preferences.read(FULLSCREEN_CONTROL_MODE),
        swipeFloatingBallOffsetX = preferences.read(SWIPE_FLOATING_BALL_OFFSET_X),
        swipeFloatingBallOffsetY = preferences.read(SWIPE_FLOATING_BALL_OFFSET_Y),
        swipeFloatingBallBackgroundAlphaPercent =
            preferences.read(SWIPE_FLOATING_BALL_BACKGROUND_ALPHA_PERCENT),
        swipeFloatingBallRingAlphaPercent =
            preferences.read(SWIPE_FLOATING_BALL_RING_ALPHA_PERCENT),
        fullscreenFloatingButtonBackgroundAlphaPercent =
            preferences.read(FULLSCREEN_FLOATING_BUTTON_BACKGROUND_ALPHA_PERCENT),
        fullscreenFloatingButtonRingAlphaPercent =
            preferences.read(FULLSCREEN_FLOATING_BUTTON_RING_ALPHA_PERCENT),
        fullscreenCompatibilityMode = preferences.read(FULLSCREEN_COMPATIBILITY_MODE),

        fullscreenFloatingButtonXFraction = preferences.read(FULLSCREEN_FLOATING_BUTTON_X_FRACTION),
        fullscreenFloatingButtonYFraction = preferences.read(FULLSCREEN_FLOATING_BUTTON_Y_FRACTION),
        previewVirtualButtonShowText = preferences.read(PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT),
        virtualButtonsLayout = preferences.read(VIRTUAL_BUTTONS_LAYOUT),
        swipeFloatingBallActionsLayout = preferences.read(SWIPE_FLOATING_BALL_ACTIONS_LAYOUT),
        deviceTwoPaneConfigOnRight = preferences.read(DEVICE_TWO_PANE_CONFIG_ON_RIGHT),

        // Scrcpy Server
        customServerUri = preferences.read(CUSTOM_SERVER_URI),
        customServerVersion = preferences.read(CUSTOM_SERVER_VERSION),
        serverRemotePath = preferences.read(SERVER_REMOTE_PATH),

        // ADB
        adbKeyName = preferences.read(ADB_KEY_NAME),
        adbPairingAutoDiscoverOnDialogOpen =
            preferences.read(ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN),
        adbAutoReconnectPairedDevice = preferences.read(ADB_AUTO_RECONNECT_PAIRED_DEVICE),
        adbMdnsLanDiscovery = preferences.read(ADB_MDNS_LAN_DISCOVERY),
        adbAutoLoadAppListOnConnect = preferences.read(ADB_AUTO_LOAD_APP_LIST_ON_CONNECT),
        adbFlowControlWindow = preferences.read(ADB_FLOW_CONTROL_WINDOW),

        // Terminal
        terminalFontSizeSp = preferences.read(TERMINAL_FONT_SIZE_SP),
        terminalFontDisplayName = preferences.read(TERMINAL_FONT_DISPLAY_NAME),
        terminalBookmarkAutoEnter = preferences.read(TERMINAL_BOOKMARK_AUTO_ENTER),

        passwordRequireAuth = preferences.read(PASSWORD_REQUIRE_AUTH),
        fileManagerSortBy = preferences.read(FILE_MANAGER_SORT_BY),
        fileManagerSortDescending = preferences.read(FILE_MANAGER_SORT_DESCENDING),
        lastUpdateCheckAt = preferences.read(LAST_UPDATE_CHECK_AT),
        clearLogsOnExit = preferences.read(CLEAR_LOGS_ON_EXIT),
        hideDeviceLogs = preferences.read(HIDE_DEVICE_LOGS),
        favoriteApps = preferences.read(FAVORITE_APPS),
        showAppIcons = preferences.read(SHOW_APP_ICONS),
        snackbarDurationMs = preferences.read(SNACKBAR_DURATION_MS),
    )

    suspend fun loadBundle() = loadBundle(::bundleFromPreferences)

    suspend fun saveBundle(new: Bundle) = saveBundle(bundleState.value, new, bundleFields)

    suspend fun updateBundle(transform: (Bundle) -> Bundle) {
        saveBundle(transform(bundleState.value))
    }

    // TODO?
    // fun validate(): Boolean = true
}
