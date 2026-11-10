package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.*
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.scrcpy.GamepadHid
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.parcelize.Parcelize
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class AppSettings(context: Context): Settings(context, "AppSettings") {
    object BlurMode {
        const val NONE = 0
        const val GAUSSIAN = 1
        const val PROGRESSIVE = 2
    }

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

    // 三种控制方式：按钮条 / 悬浮球 / 全部隐藏。「关闭」与「显示全屏虚拟按钮」开关
    // 曾并存导致双门控（两处状态都能收起按钮条），现统一由本枚举承担显隐职责。
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
        val SQUIRCLE = Pair(
            booleanPreferencesKey("squircle"),
            true,
        )
        val BLUR_MODE = Pair(
            intPreferencesKey("blur_mode"),
            BlurMode.NONE,
        )
        val NAV_TRANSITION_STYLE = Pair(
            intPreferencesKey("nav_transition_style"),
            0,
        )
        val SWIPE_BACK = Pair(
            booleanPreferencesKey("swipe_back"),
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

        // App manager
        val APP_MANAGER_HIDE_VERSION = Pair(
            booleanPreferencesKey("app_manager_hide_version"),
            false,
        )
        val APP_MANAGER_HIDE_APK_SIZE = Pair(
            booleanPreferencesKey("app_manager_hide_apk_size"),
            false,
        )
        val APP_MANAGER_HIDE_DISABLED = Pair(
            booleanPreferencesKey("app_manager_hide_disabled"),
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
        val BOOKMARK_AUTO_ENTER = Pair(
            booleanPreferencesKey("bookmark_auto_enter"),
            false,
        )
        val DEVICE_PREVIEW_CARD_TAP_FULLSCREEN = Pair(
            booleanPreferencesKey("device_preview_card_tap_fullscreen"),
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
        val FULLSCREEN_CONTROL_MODE = Pair(
            stringPreferencesKey("fullscreen_control_mode"),
            FullscreenControlMode.VIRTUAL_BUTTONS.rawValue,
        )
        val SWIPE_FLOATING_BALL_OFFSET_X = Pair(
            floatPreferencesKey("swipe_floating_ball_offset_x"),
            1f,
        )
        val SWIPE_FLOATING_BALL_OFFSET_Y = Pair(
            floatPreferencesKey("swipe_floating_ball_offset_y"),
            0.5f,
        )
        // 悬浮球合并不透明度：单一滑块直读（100 = 完全不透明）。
        // 取代旧的背景/圆环/滑出菜单三个透明度滑块（偏好键废弃不再读取）。
        val SWIPE_FLOATING_BALL_OPACITY_PERCENT = Pair(
            intPreferencesKey("swipe_floating_ball_opacity_percent"),
            70,
        )
        // MIUI 悬浮球行为开关：边缘吸附 / 无操作渐隐 / 操作名提示 / 展开动画
        val MIUI_SNAP_ENABLED = Pair(
            booleanPreferencesKey("miui_snap_enabled"),
            true,
        )
        val MIUI_IDLE_DIM_ENABLED = Pair(
            booleanPreferencesKey("miui_idle_dim_enabled"),
            true,
        )
        val MIUI_MENU_ACTION_HINT = Pair(
            booleanPreferencesKey("miui_menu_action_hint"),
            true,
        )
        val MIUI_EXPAND_ANIMATION_ENABLED = Pair(
            booleanPreferencesKey("miui_expand_animation_enabled"),
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
        // 虚拟按钮条（格式：id:showOutside,id:showOutside,...）
        // showOutside=1 表示外显在按钮条上，=0 表示收进「更多」菜单
        // 缺项由 parseStoredLayout 自动补全，因此新增按钮项无须迁移已有偏好
        val VIRTUAL_BUTTONS_LAYOUT = Pair(
            stringPreferencesKey("virtual_buttons_layout"),
            "more:1" +

                    ",app_switch:1,home:1,back:1,exit_fullscreen:1" +

                    ",show_swipe_floating_ball:0" +

                    ",password_input:0" +
                    ",all_apps:0" +
                    ",recent_tasks:0" +
                    ",toggle_ime:0" +
                    ",paste_local_clipboard:0" +

                    ",menu:0,notification:0" +
                    ",volume_up:0,volume_down:0,volume_mute:0" +
                    ",power:0,screenshot:0" +
                    ",expand_status_bar:0" +

                    "",
        )
        // 滑动悬浮球滑出菜单的独立排序（格式：id:visible,id:visible,...）
        // 不包含 MORE 与 SHOW_SWIPE_FLOATING_BALL：这两个在悬浮球语境下无意义
        val SWIPE_FLOATING_BALL_ACTIONS_LAYOUT = Pair(
            stringPreferencesKey("swipe_floating_ball_actions_layout"),
            "power:1,app_switch:1,home:1,back:1,exit_fullscreen:1" +

                    ",hide_swipe_floating_ball:0" +

                    ",password_input:0" +
                    ",all_apps:0" +
                    ",recent_tasks:0" +
                    ",toggle_ime:0" +
                    ",paste_local_clipboard:0" +

                    ",menu:0,notification:0" +
                    ",volume_up:0,volume_down:0,volume_mute:0" +
                    ",screenshot:0" +
                    ",expand_status_bar:0" +

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
        val SNACKBAR_DURATION_MS = Pair(
            intPreferencesKey("snackbar_duration_ms"),
            4000,
        )
        val KEEP_ALIVE = Pair(
            booleanPreferencesKey("keep_alive"),
            false,
        )
        val GAMEPAD_DEVICE_NAME = Pair(
            stringPreferencesKey("gamepad_device_name"),
            GamepadHid.NAME,
        )

        // 应用面板（最近任务 / 全部应用）：收藏的包名清单（逗号分隔，见 storage/FavoriteApps.kt）
        val FAVORITE_APPS = Pair(
            stringPreferencesKey("favorite_apps"),
            "",
        )
    }

    @Parcelize
    data class Bundle(
        // Theme
        val themeBaseIndex: Int,
        val monet: Boolean,
        val monetSeedIndex: Int,
        val monetPaletteStyle: Int,
        val monetColorSpec: Int,
        val squircle: Boolean,
        val blur: Int,
        val navTransitionStyle: Int,
        val swipeBack: Boolean,
        val floatingBottomBar: Boolean,
        val floatingBottomBarBlur: Boolean,

        // Scrcpy
        val lowLatency: Boolean,
        val downsizeOnDecodeError: Boolean,
        val fullscreenDebugInfo: Boolean,
        val hideSimpleConfigItems: Boolean,
        val previewCardOnTop: Boolean,
        val devicePreviewCardHeightDp: Int,
        val realtimeClipboardSyncToDevice: Boolean,

        // Fullscreen
        val fullscreenControlIgnoreSystemRotationLock: Boolean,
        val fullscreenControlBackToDevice: Boolean,
        val fullscreenVirtualButtonHeightDp: Int,
        val fullscreenVirtualButtonDock: String,
        val showFullscreenFloatingButton: Boolean,
        val fullscreenFloatingButtonSizeDp: Int,
        val fullscreenFloatingButtonBackgroundAlphaPercent: Int,
        val fullscreenFloatingButtonRingAlphaPercent: Int,
        val fullscreenCompatibilityMode: Boolean,

        val fullscreenFloatingButtonXFraction: Float,
        val fullscreenFloatingButtonYFraction: Float,
        val previewVirtualButtonShowText: Boolean,
        val virtualButtonsLayout: String,
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

        val passwordRequireAuth: Boolean,

        val fileManagerSortBy: String,
        val fileManagerSortDescending: Boolean,
        val lastUpdateCheckAt: Long,
        val clearLogsOnExit: Boolean,
        val hideDeviceLogs: Boolean,
        val gamepadDeviceName: String,

        // 位置参数构造：新字段只能追加在末尾
        val tempFloatingButtonSizeDp: Int,
        val fullscreenControlMode: String,
        val swipeFloatingBallOffsetX: Float,
        val swipeFloatingBallOffsetY: Float,
        val swipeFloatingBallActionsLayout: String,
        val swipeFloatingBallOpacityPercent: Int,
        val miuiSnapEnabled: Boolean,
        val miuiIdleDimEnabled: Boolean,
        val miuiMenuActionHint: Boolean,
        val appManagerHideVersion: Boolean = false,
        val appManagerHideApkSize: Boolean = false,
        val favoriteApps: String = "",
        val miuiExpandAnimationEnabled: Boolean = false,
        val devicePreviewCardTapFullscreen: Boolean = false,
        val bookmarkAutoEnter: Boolean = false,
        val snackbarDurationMs: Int = 4000,
        val keepAlive: Boolean = false,
        /** 应用管理：隐藏已停用应用（默认关闭，只在应用管理页生效） */
        val appManagerHideDisabled: Boolean = false,
    ): Parcelable {
    }

    private val bundleFields = arrayOf<BundleField<Bundle>>(
        // Theme
        bundleField(THEME_BASE_INDEX) { it.themeBaseIndex },
        bundleField(MONET) { it.monet },
        bundleField(MONET_SEED_INDEX) { it.monetSeedIndex },
        bundleField(MONET_PALETTE_STYLE) { it.monetPaletteStyle },
        bundleField(MONET_COLOR_SPEC) { it.monetColorSpec },
        bundleField(SQUIRCLE) { it.squircle },
        bundleField(BLUR_MODE) { it.blur },
        bundleField(NAV_TRANSITION_STYLE) { it.navTransitionStyle },
        bundleField(SWIPE_BACK) { it.swipeBack },
        bundleField(FLOATING_BOTTOM_BAR) { it.floatingBottomBar },
        bundleField(FLOATING_BOTTOM_BAR_BLUR) { it.floatingBottomBarBlur },

        // Scrcpy
        bundleField(LOW_LATENCY) { it.lowLatency },
        bundleField(DOWNSIZE_ON_DECODE_ERROR) { it.downsizeOnDecodeError },
        bundleField(FULLSCREEN_DEBUG_INFO) { it.fullscreenDebugInfo },
        bundleField(HIDE_SIMPLE_CONFIG_ITEMS) { it.hideSimpleConfigItems },
        bundleField(PREVIEW_CARD_ON_TOP) { it.previewCardOnTop },
        bundleField(DEVICE_PREVIEW_CARD_HEIGHT_DP) { it.devicePreviewCardHeightDp },
        bundleField(DEVICE_PREVIEW_CARD_TAP_FULLSCREEN) { it.devicePreviewCardTapFullscreen },
        bundleField(BOOKMARK_AUTO_ENTER) { it.bookmarkAutoEnter },
        bundleField(REALTIME_CLIPBOARD_SYNC_TO_DEVICE) { it.realtimeClipboardSyncToDevice },

        // Fullscreen
        bundleField(FULLSCREEN_CONTROL_IGNORE_SYSTEM_ROTATION_LOCK) { it.fullscreenControlIgnoreSystemRotationLock },
        bundleField(FULLSCREEN_CONTROL_BACK_TO_DEVICE) { it.fullscreenControlBackToDevice },
        bundleField(FULLSCREEN_VIRTUAL_BUTTON_HEIGHT_DP) { it.fullscreenVirtualButtonHeightDp },
        bundleField(FULLSCREEN_VIRTUAL_BUTTON_DOCK) { it.fullscreenVirtualButtonDock },
        bundleField(SHOW_FULLSCREEN_FLOATING_BUTTON) { it.showFullscreenFloatingButton },
        bundleField(FULLSCREEN_FLOATING_BUTTON_SIZE_DP) { it.fullscreenFloatingButtonSizeDp },
        bundleField(FULLSCREEN_FLOATING_BUTTON_BACKGROUND_ALPHA_PERCENT) { it.fullscreenFloatingButtonBackgroundAlphaPercent },
        bundleField(FULLSCREEN_FLOATING_BUTTON_RING_ALPHA_PERCENT) { it.fullscreenFloatingButtonRingAlphaPercent },
        bundleField(FULLSCREEN_COMPATIBILITY_MODE) { it.fullscreenCompatibilityMode },

        bundleField(FULLSCREEN_FLOATING_BUTTON_X_FRACTION) { it.fullscreenFloatingButtonXFraction },
        bundleField(FULLSCREEN_FLOATING_BUTTON_Y_FRACTION) { it.fullscreenFloatingButtonYFraction },
        bundleField(PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT) { it.previewVirtualButtonShowText },
        bundleField(VIRTUAL_BUTTONS_LAYOUT) { it.virtualButtonsLayout },
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

        bundleField(PASSWORD_REQUIRE_AUTH) { it.passwordRequireAuth },

        bundleField(FILE_MANAGER_SORT_BY) { it.fileManagerSortBy },
        bundleField(FILE_MANAGER_SORT_DESCENDING) { it.fileManagerSortDescending },
        bundleField(LAST_UPDATE_CHECK_AT) { it.lastUpdateCheckAt },
        bundleField(CLEAR_LOGS_ON_EXIT) { it.clearLogsOnExit },
        bundleField(HIDE_DEVICE_LOGS) { it.hideDeviceLogs },
        bundleField(SNACKBAR_DURATION_MS) { it.snackbarDurationMs },
        bundleField(KEEP_ALIVE) { it.keepAlive },
        bundleField(GAMEPAD_DEVICE_NAME) { it.gamepadDeviceName },

        bundleField(TEMP_FLOATING_BUTTON_SIZE_DP) { it.tempFloatingButtonSizeDp },
        bundleField(FULLSCREEN_CONTROL_MODE) { it.fullscreenControlMode },
        bundleField(SWIPE_FLOATING_BALL_OFFSET_X) { it.swipeFloatingBallOffsetX },
        bundleField(SWIPE_FLOATING_BALL_OFFSET_Y) { it.swipeFloatingBallOffsetY },
        bundleField(SWIPE_FLOATING_BALL_ACTIONS_LAYOUT) { it.swipeFloatingBallActionsLayout },
        bundleField(SWIPE_FLOATING_BALL_OPACITY_PERCENT) { it.swipeFloatingBallOpacityPercent },
        bundleField(MIUI_SNAP_ENABLED) { it.miuiSnapEnabled },
        bundleField(MIUI_IDLE_DIM_ENABLED) { it.miuiIdleDimEnabled },
        bundleField(MIUI_MENU_ACTION_HINT) { it.miuiMenuActionHint },
        bundleField(MIUI_EXPAND_ANIMATION_ENABLED) { it.miuiExpandAnimationEnabled },
        bundleField(APP_MANAGER_HIDE_VERSION) { it.appManagerHideVersion },
        bundleField(APP_MANAGER_HIDE_APK_SIZE) { it.appManagerHideApkSize },
        bundleField(APP_MANAGER_HIDE_DISABLED) { it.appManagerHideDisabled },
        // 收藏不放进整包字段：两个页面各持整包副本 + 防抖保存，一旦放进列表，旧副本在保存
        // 别的设置时会把收藏回滚。收藏的写入只走 toggleFavoriteApp（DataStore 当前值读改写）
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = Bundle(
        // Theme
        themeBaseIndex = preferences.read(THEME_BASE_INDEX),
        monet = preferences.read(MONET),
        monetSeedIndex = preferences.read(MONET_SEED_INDEX),
        monetPaletteStyle = preferences.read(MONET_PALETTE_STYLE),
        monetColorSpec = preferences.read(MONET_COLOR_SPEC),
        squircle = preferences.read(SQUIRCLE),
        blur = preferences.read(BLUR_MODE),
        navTransitionStyle = preferences.read(NAV_TRANSITION_STYLE),
        swipeBack = preferences.read(SWIPE_BACK),
        floatingBottomBar = preferences.read(FLOATING_BOTTOM_BAR),
        floatingBottomBarBlur = preferences.read(FLOATING_BOTTOM_BAR_BLUR),

        // Scrcpy
        lowLatency = preferences.read(LOW_LATENCY),
        downsizeOnDecodeError = preferences.read(DOWNSIZE_ON_DECODE_ERROR),
        fullscreenDebugInfo = preferences.read(FULLSCREEN_DEBUG_INFO),
        hideSimpleConfigItems = preferences.read(HIDE_SIMPLE_CONFIG_ITEMS),
        previewCardOnTop = preferences.read(PREVIEW_CARD_ON_TOP),
        devicePreviewCardHeightDp = preferences.read(DEVICE_PREVIEW_CARD_HEIGHT_DP),
        devicePreviewCardTapFullscreen = preferences.read(DEVICE_PREVIEW_CARD_TAP_FULLSCREEN),
        bookmarkAutoEnter = preferences.read(BOOKMARK_AUTO_ENTER),
        realtimeClipboardSyncToDevice = preferences.read(REALTIME_CLIPBOARD_SYNC_TO_DEVICE),

        // Fullscreen
        fullscreenControlIgnoreSystemRotationLock =
            preferences.read(FULLSCREEN_CONTROL_IGNORE_SYSTEM_ROTATION_LOCK),
        fullscreenControlBackToDevice = preferences.read(FULLSCREEN_CONTROL_BACK_TO_DEVICE),
        fullscreenVirtualButtonHeightDp = preferences.read(FULLSCREEN_VIRTUAL_BUTTON_HEIGHT_DP),
        fullscreenVirtualButtonDock = preferences.read(FULLSCREEN_VIRTUAL_BUTTON_DOCK),
        showFullscreenFloatingButton = preferences.read(SHOW_FULLSCREEN_FLOATING_BUTTON),
        fullscreenFloatingButtonSizeDp = preferences.read(FULLSCREEN_FLOATING_BUTTON_SIZE_DP),
        fullscreenFloatingButtonBackgroundAlphaPercent =
            preferences.read(FULLSCREEN_FLOATING_BUTTON_BACKGROUND_ALPHA_PERCENT),
        fullscreenFloatingButtonRingAlphaPercent =
            preferences.read(FULLSCREEN_FLOATING_BUTTON_RING_ALPHA_PERCENT),
        fullscreenCompatibilityMode = preferences.read(FULLSCREEN_COMPATIBILITY_MODE),

        fullscreenFloatingButtonXFraction = preferences.read(FULLSCREEN_FLOATING_BUTTON_X_FRACTION),
        fullscreenFloatingButtonYFraction = preferences.read(FULLSCREEN_FLOATING_BUTTON_Y_FRACTION),
        previewVirtualButtonShowText = preferences.read(PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT),
        virtualButtonsLayout = preferences.read(VIRTUAL_BUTTONS_LAYOUT),
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

        passwordRequireAuth = preferences.read(PASSWORD_REQUIRE_AUTH),
        fileManagerSortBy = preferences.read(FILE_MANAGER_SORT_BY),
        fileManagerSortDescending = preferences.read(FILE_MANAGER_SORT_DESCENDING),
        lastUpdateCheckAt = preferences.read(LAST_UPDATE_CHECK_AT),
        clearLogsOnExit = preferences.read(CLEAR_LOGS_ON_EXIT),
        hideDeviceLogs = preferences.read(HIDE_DEVICE_LOGS),
        snackbarDurationMs = preferences.read(SNACKBAR_DURATION_MS),
        keepAlive = preferences.read(KEEP_ALIVE),
        gamepadDeviceName = preferences.read(GAMEPAD_DEVICE_NAME),

        tempFloatingButtonSizeDp = preferences.read(TEMP_FLOATING_BUTTON_SIZE_DP),
        fullscreenControlMode = preferences.read(FULLSCREEN_CONTROL_MODE),
        swipeFloatingBallOffsetX = preferences.read(SWIPE_FLOATING_BALL_OFFSET_X),
        swipeFloatingBallOffsetY = preferences.read(SWIPE_FLOATING_BALL_OFFSET_Y),
        swipeFloatingBallActionsLayout = preferences.read(SWIPE_FLOATING_BALL_ACTIONS_LAYOUT),
        swipeFloatingBallOpacityPercent = preferences.read(SWIPE_FLOATING_BALL_OPACITY_PERCENT),
        miuiSnapEnabled = preferences.read(MIUI_SNAP_ENABLED),
        miuiIdleDimEnabled = preferences.read(MIUI_IDLE_DIM_ENABLED),
        miuiMenuActionHint = preferences.read(MIUI_MENU_ACTION_HINT),
        miuiExpandAnimationEnabled = preferences.read(MIUI_EXPAND_ANIMATION_ENABLED),
        appManagerHideVersion = preferences.read(APP_MANAGER_HIDE_VERSION),
        appManagerHideApkSize = preferences.read(APP_MANAGER_HIDE_APK_SIZE),
        appManagerHideDisabled = preferences.read(APP_MANAGER_HIDE_DISABLED),
        favoriteApps = preferences.read(FAVORITE_APPS),
    )

    suspend fun loadBundle() = loadBundle(::bundleFromPreferences)

    suspend fun saveBundle(new: Bundle) = saveBundle(bundleState.value, new, bundleFields)

    suspend fun updateBundle(transform: (Bundle) -> Bundle) {
        saveBundle(transform(bundleState.value))
    }

    /** 收藏写的串行闸：读改写之间不让两次点击互相吞掉。 */
    private val favoriteAppsMutex = Mutex()

    /**
     * 切换收藏（单点写）。收藏不经过任何页面的整包副本：直接基于 DataStore 当前值读改写，
     * 页面切换 / 防抖窗口里的旧副本无从覆盖它。
     */
    suspend fun toggleFavoriteApp(packageName: String) {
        favoriteAppsMutex.withLock {
            setValue(FAVORITE_APPS, toggleFavorite(getValue(FAVORITE_APPS), packageName))
        }
    }

    /**
     * 按当前设备清单对账收藏。
     *
     * 清单里没有的包名先交给 [confirmMissing] 复核（逐个 `pm path`）：确认未安装才剔除；
     * 复核失败 / 清单不完整时保留——收藏是用户数据，宁可这次不删也不误删。
     */
    suspend fun pruneFavoriteApps(
        livePackages: Collection<String>,
        confirmMissing: suspend (Collection<String>) -> Set<String>,
    ) {
        val live = livePackages.toSet()
        // 复核逐个走 `pm path` 往返，放在锁外：持锁做会把收藏点击挡在后面
        val candidates = parseFavoriteApps(getValue(FAVORITE_APPS)) - live
        val confirmed = if (candidates.isEmpty()) emptySet() else confirmMissing(candidates)
        favoriteAppsMutex.withLock {
            val stored = getValue(FAVORITE_APPS)
            val pruned = pruneFavorites(stored, live, confirmed)
            if (pruned != stored) setValue(FAVORITE_APPS, pruned)
        }
    }

    // TODO?
    // fun validate(): Boolean = true
}
