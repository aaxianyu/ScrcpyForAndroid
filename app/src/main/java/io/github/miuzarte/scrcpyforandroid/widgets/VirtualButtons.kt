package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiAndroidKeycodes
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.confirm
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.longPress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.ScreenCapture
import top.yukonga.miuix.kmp.icon.extended.ZoomOut
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.ranges.coerceAtLeast

enum class VirtualButtonAction(
    val id: String,
    @field:StringRes val titleResId: Int,
    val icon: ImageVector,
    val keycode: Int?,
) {
    MORE(
        "more",
        R.string.vb_more,
        MiuixIcons.More,
        null,
    ),
    HOME(
        "home",
        R.string.vb_home,
        ScrcpyIcons.SysbarHome,
        UiAndroidKeycodes.HOME
    ),
    BACK(
        "back",
        R.string.vb_back,
        ScrcpyIcons.SysbarBack,
        UiAndroidKeycodes.BACK
    ),
    APP_SWITCH(
        "app_switch",
        R.string.vb_app_switch,
        ScrcpyIcons.SysbarRecent,
        UiAndroidKeycodes.APP_SWITCH
    ),
    MENU(
        "menu",
        R.string.vb_menu,
        MiuixIcons.ListView,
        UiAndroidKeycodes.MENU
    ),
    NOTIFICATION(
        "notification",
        R.string.vb_notifications,
        Icons.Rounded.Notifications,
        UiAndroidKeycodes.NOTIFICATION,
    ),
    VOLUME_UP(
        "volume_up",
        R.string.vb_volume_up,
        Icons.AutoMirrored.Rounded.VolumeUp,
        UiAndroidKeycodes.VOLUME_UP,
    ),
    VOLUME_DOWN(
        "volume_down",
        R.string.vb_volume_down,
        Icons.AutoMirrored.Rounded.VolumeDown,
        UiAndroidKeycodes.VOLUME_DOWN,
    ),
    VOLUME_MUTE(
        "volume_mute",
        R.string.vb_volume_mute,
        Icons.AutoMirrored.Rounded.VolumeOff,
        UiAndroidKeycodes.VOLUME_MUTE,
    ),
    POWER(
        "power",
        R.string.vb_lock_screen,
        Icons.Rounded.PowerSettingsNew,
        UiAndroidKeycodes.POWER,
    ),
    SCREENSHOT(
        "screenshot",
        R.string.vb_screenshot,
        MiuixIcons.ScreenCapture,
        UiAndroidKeycodes.SYSRQ,
    ),
    PASSWORD_INPUT(
        "password_input",
        R.string.vb_fill_password,
        Icons.Rounded.Password,
        null,
    ),
    ALL_APPS(
        "all_apps",
        R.string.vb_all_apps,
        MiuixIcons.All,
        null,
    ),
    RECENT_TASKS(
        "recent_tasks",
        R.string.vb_recent_tasks,
        Icons.Rounded.DashboardCustomize,
        null,
    ),
    TOGGLE_IME(
        "toggle_ime",
        R.string.vb_toggle_ime,
        Icons.Rounded.Keyboard,
        null,
    ),
    PASTE_LOCAL_CLIPBOARD(
        "paste_local_clipboard",
        R.string.vb_paste_clipboard,
        Icons.Rounded.ContentPaste,
        null
    ),
    EXIT_FULLSCREEN(
        "exit_fullscreen",
        R.string.vb_exit_fullscreen,
        MiuixIcons.ZoomOut,
        null
    ),
    EXPAND_STATUS_BAR(
        "expand_status_bar",
        R.string.vb_expand_status_bar,
        Icons.Rounded.Dashboard,
        null
    ),
    SHOW_SWIPE_FLOATING_BALL(
        "show_swipe_floating_ball",
        R.string.vb_temp_hide_virtual_buttons,
        Icons.Rounded.RadioButtonChecked,
        null
    ),
    HIDE_SWIPE_FLOATING_BALL(
        "hide_swipe_floating_ball",
        R.string.vb_restore_virtual_buttons,
        ScrcpyIcons.SysbarNavigation,
        null
    );
}

data class VirtualButtonItem(
    val action: VirtualButtonAction,
    val showOutside: Boolean,
)

object VirtualButtonActions {
    val all = VirtualButtonAction.entries

    private val byId = all.associateBy { it.id }

    fun parseStoredLayout(raw: String): List<VirtualButtonItem> {
        val parsed = raw.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.mapNotNull { item ->
                val parts = item.trim().split(':')
                if (parts.size != 2) return@mapNotNull null
                val id = parts[0]
                val showOutside = parts[1] == "1"
                val action = byId[id] ?: return@mapNotNull null
                VirtualButtonItem(action, showOutside)
            }
            .orEmpty()
            .distinctBy { it.action.id }
        val base = parsed.ifEmpty {
            parseStoredLayout(AppSettings.VIRTUAL_BUTTONS_LAYOUT.defaultValue)
        }
        val missing = all
            .filterNot { action -> base.any { it.action == action } }
            .map { action ->
                VirtualButtonItem(
                    action = action,
                    showOutside = action == VirtualButtonAction.MORE,
                )
            }
        return base + missing
    }

    fun encodeStoredLayout(items: List<VirtualButtonItem>): String {
        return items.joinToString(",") { item ->
            "${item.action.id}:${if (item.showOutside) "1" else "0"}"
        }
    }

    fun splitLayout(items: List<VirtualButtonItem>): Pair<List<VirtualButtonAction>, List<VirtualButtonAction>> {
        val outside = items.filter { it.showOutside }.map { it.action }
        val more = items.filter { !it.showOutside }.map { it.action }
        return outside to more
    }
}

class VirtualButtonBar(
    private val outsideActions: List<VirtualButtonAction>,
    private val moreActions: List<VirtualButtonAction>,
    private val showSwipeFloatingBall: Boolean = false,
) {
    enum class FullscreenDock {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
    }

    private enum class ActionPopupDestination {
        Actions,
        Passwords,
    }

    @Composable
    fun Preview(
        enabled: Boolean,
        showText: Boolean,
        onAction: (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
        popupBottomPadding: Dp = 0.dp,
    ) {
        val haptic = LocalHapticFeedback.current

        val activeContainerColor = colorScheme.primary
        val disabledContainerColor = colorScheme.primary.copy(alpha = 0.35f)
        val activeContentColor = colorScheme.onPrimary
        val disabledContentColor = colorScheme.onPrimary.copy(alpha = 0.45f)

        var showMorePopup by remember { mutableStateOf(false) }

        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            outsideActions.forEach { action ->
                var showPasswordPopup by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptic.contextClick()
                            when (action) {
                                VirtualButtonAction.MORE -> {
                                    showMorePopup = true
                                }

                                VirtualButtonAction.PASSWORD_INPUT
                                    if passwordPopupContent != null -> {
                                    showPasswordPopup = true
                                }

                                else -> onAction(action)
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            color = activeContainerColor,
                            disabledColor = disabledContainerColor,
                        ),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        val contentColor =
                            if (enabled) activeContentColor
                            else disabledContentColor
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.titleResId),
                            modifier = Modifier.size(18.dp),
                            tint = contentColor,
                        )
                        if (showText) {
                            Spacer(Modifier.width(UiSpacing.Small))
                            Text(stringResource(action.titleResId), color = contentColor)
                        }
                    }
                    if (action == VirtualButtonAction.MORE) {
                        // 预览卡片中过滤掉全屏专属操作
                        val previewActions = moreActions.filter {
                            it != VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL &&
                            it != VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL &&
                            it != VirtualButtonAction.EXIT_FULLSCREEN
                        }
                        ActionPopup(
                            show = showMorePopup,
                            actions = previewActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                onAction(it)
                                showMorePopup = false
                            },
                            passwordPopupContent = passwordPopupContent,
                            renderInRootScaffold = false,
                            popupBottomPadding = popupBottomPadding,
                        )
                    }
                    if (
                        action == VirtualButtonAction.PASSWORD_INPUT &&
                        passwordPopupContent != null
                    ) {
                        OverlayListPopup(
                            show = showPasswordPopup,
                            popupPositionProvider =
                                rememberBottomSafeContextMenuPositionProvider(popupBottomPadding),
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showPasswordPopup = false },
                            renderInRootScaffold = false,
                            enableWindowDim = false,
                        ) {
                            passwordPopupContent { showPasswordPopup = false }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Fullscreen(
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        dock: FullscreenDock = FullscreenDock.BOTTOM,
        reverseOrder: Boolean = false,
        thickness: Dp = 16.dp,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
    ) {
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        var showMorePopup by remember { mutableStateOf(false) }
        var showPasswordPopup by remember { mutableStateOf(false) }

        val isVertical = dock == FullscreenDock.LEFT || dock == FullscreenDock.RIGHT
        val visibleActions =
            if (reverseOrder) outsideActions.asReversed()
            else outsideActions
        val containerModifier =
            if (isVertical) modifier
                .width(thickness)
                .fillMaxHeight()
            else modifier
                .fillMaxWidth()
                .height(thickness)

        val buttonModifier =
            if (isVertical) Modifier
                .fillMaxSize()
            else Modifier
                .fillMaxWidth()
                .height(thickness)

        if (isVertical) Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            visibleActions.forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptic.contextClick()
                            when (action) {
                                VirtualButtonAction.MORE -> {
                                    showMorePopup = true
                                }

                                VirtualButtonAction.PASSWORD_INPUT
                                    if passwordPopupContent != null -> {
                                    showPasswordPopup = true
                                }

                                else -> scope.launch { onAction(action) }
                            }
                        },
                        modifier = buttonModifier,
                        cornerRadius = 0.dp,
                        minHeight = thickness,
                        insideMargin = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            color = Color.Black.copy(alpha = 0.1f),
                        ),
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.titleResId),
                            tint = Color.White
                        )
                    }

                    if (action == VirtualButtonAction.MORE) {
                        val filteredMoreActions = if (showSwipeFloatingBall) {
                            moreActions
                        } else {
                            moreActions.filter { it != VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL }
                        }
                        val popupAlignment = when (dock) {
                            FullscreenDock.LEFT -> PopupPositionProvider.Align.TopStart
                            FullscreenDock.RIGHT -> PopupPositionProvider.Align.TopEnd
                            FullscreenDock.TOP -> PopupPositionProvider.Align.BottomEnd
                            FullscreenDock.BOTTOM -> PopupPositionProvider.Align.TopEnd
                        }
                        ActionPopup(
                            show = showMorePopup,
                            actions = filteredMoreActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                if (it == VirtualButtonAction.PASSWORD_INPUT
                                    && passwordPopupContent != null
                                ) showPasswordPopup = true
                                else onAction(it)

                                showMorePopup = false
                            },
                            passwordPopupContent = passwordPopupContent,
                            renderInRootScaffold = false,
                            popupAlignment = popupAlignment,
                        )
                    }
                }
            }
        }
        else Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            visibleActions.forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptic.contextClick()
                            when (action) {
                                VirtualButtonAction.MORE -> {
                                    showMorePopup = true
                                }

                                VirtualButtonAction.PASSWORD_INPUT
                                    if passwordPopupContent != null -> {
                                    showPasswordPopup = true
                                }

                                else -> scope.launch { onAction(action) }
                            }
                        },
                        modifier = buttonModifier,
                        cornerRadius = 0.dp,
                        minHeight = thickness,
                        insideMargin = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            color = Color.Black.copy(alpha = 0.1f),
                        ),
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.titleResId),
                            tint = Color.White
                        )
                    }

                    if (action == VirtualButtonAction.MORE) {
                        val filteredMoreActions = if (showSwipeFloatingBall) {
                            moreActions
                        } else {
                            moreActions.filter { it != VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL }
                        }
                        val popupAlignment = when (dock) {
                            FullscreenDock.LEFT -> PopupPositionProvider.Align.TopStart
                            FullscreenDock.RIGHT -> PopupPositionProvider.Align.TopEnd
                            FullscreenDock.TOP -> PopupPositionProvider.Align.BottomEnd
                            FullscreenDock.BOTTOM -> PopupPositionProvider.Align.TopEnd
                        }
                        ActionPopup(
                            show = showMorePopup,
                            actions = filteredMoreActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                if (it == VirtualButtonAction.PASSWORD_INPUT
                                    && passwordPopupContent != null
                                ) showPasswordPopup = true
                                else onAction(it)

                                showMorePopup = false
                            },
                            passwordPopupContent = passwordPopupContent,
                            renderInRootScaffold = false,
                            popupAlignment = popupAlignment,
                        )
                    }
                }
            }
        }

        if (passwordPopupContent != null) {
            OverlayListPopup(
                show = showPasswordPopup,
                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                alignment = PopupPositionProvider.Align.TopEnd,
                onDismissRequest = { showPasswordPopup = false },
                renderInRootScaffold = true,
                enableWindowDim = false,
            ) {
                passwordPopupContent { showPasswordPopup = false }
            }
        }
    }

    @Composable
    fun FloatingBall(
        actions: List<VirtualButtonAction>,
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
    ) {
        val scope = rememberCoroutineScope()
        val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
        val haptic = LocalHapticFeedback.current
        var showActions by remember { mutableStateOf(false) }
        var showPasswordPopup by remember { mutableStateOf(false) }
        val asBundleShared by appSettings.bundleState.collectAsState()
        val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
        var offsetXFraction by rememberSaveable(asBundleShared.fullscreenFloatingButtonXFraction) {
            mutableFloatStateOf(asBundleShared.fullscreenFloatingButtonXFraction)
        }
        var offsetYFraction by rememberSaveable(asBundleShared.fullscreenFloatingButtonYFraction) {
            mutableFloatStateOf(asBundleShared.fullscreenFloatingButtonYFraction)
        }
        DisposableEffect(Unit) {
            onDispose {
                taskScope.launch {
                    val latest = asBundleSharedLatest
                    if (
                        offsetXFraction != latest.fullscreenFloatingButtonXFraction ||
                        offsetYFraction != latest.fullscreenFloatingButtonYFraction
                    ) {
                        // 使用 updateBundle 而非 saveBundle：只更新悬浮球位置字段，
                        // 避免将过时的其他字段（如 fullscreenControlMode）写回 appSettings。
                        appSettings.updateBundle { current ->
                            current.copy(
                                fullscreenFloatingButtonXFraction = offsetXFraction,
                                fullscreenFloatingButtonYFraction = offsetYFraction,
                            )
                        }
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
        ) {
            val ballSize = asBundleShared.fullscreenFloatingButtonSizeDp.dp
            val ringSize = ballSize / 2
            val ringWidth = ballSize / 24
            val backgroundAlpha =
                (asBundleShared.fullscreenFloatingButtonBackgroundAlphaPercent / 100f)
                    .coerceIn(0.1f, 1f)
            val ringAlpha =
                (asBundleShared.fullscreenFloatingButtonRingAlphaPercent / 100f)
                    .coerceIn(0f, 1f)
            val maxX = (maxWidth - ballSize).coerceAtLeast(0.dp)
            val maxY = (maxHeight - ballSize).coerceAtLeast(0.dp)
            val currentX =
                maxX * offsetXFraction.coerceIn(0f, 1f)
            val currentY =
                maxY * offsetYFraction.coerceIn(0f, 1f)
            val popupAlignment =
                if (offsetXFraction > 0.5f) PopupPositionProvider.Align.TopEnd
                else PopupPositionProvider.Align.TopStart

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            currentX.roundToPx(),
                            currentY.roundToPx(),
                        )
                    }
                    .size(ballSize)
                    .pointerInput(maxX, maxY) {
                        var dragStartXFraction = offsetXFraction
                        var dragStartYFraction = offsetYFraction
                        detectDragGestures(
                            onDragStart = {
                                dragStartXFraction = offsetXFraction
                                dragStartYFraction = offsetYFraction
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            val nextX = (maxX.toPx() * dragStartXFraction + dragAmount.x)
                                .coerceIn(0f, maxX.toPx())
                            val nextY = (maxY.toPx() * dragStartYFraction + dragAmount.y)
                                .coerceIn(0f, maxY.toPx())
                            val nextXFraction =
                                if (maxX > 0.dp) nextX / maxX.toPx()
                                else 0f
                            val nextYFraction =
                                if (maxY > 0.dp) nextY / maxY.toPx()
                                else 0f
                            dragStartXFraction = nextXFraction
                            dragStartYFraction = nextYFraction
                            offsetXFraction = nextXFraction
                            offsetYFraction = nextYFraction
                        }
                    },
            ) {
                Button(
                    modifier = Modifier.fillMaxSize(),
                    onClick = {
                        haptic.contextClick()
                        showActions = true
                    },
                    cornerRadius = ballSize / 2,
                    minHeight = ballSize,
                    insideMargin = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        color = Color.Black.copy(alpha = backgroundAlpha),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .clip(CircleShape)
                            .then(
                                if (ringAlpha > 0f) {
                                    Modifier.border(
                                        ringWidth,
                                        Color.White.copy(alpha = ringAlpha),
                                        CircleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }

                ActionPopup(
                    show = showActions,
                    actions = actions,
                    onDismiss = { showActions = false },
                    onAction = {
                        if (it == VirtualButtonAction.PASSWORD_INPUT &&
                            passwordPopupContent != null
                        ) showPasswordPopup = true
                        else scope.launch { onAction(it) }

                        showActions = false
                    },
                    passwordPopupContent = passwordPopupContent,
                    renderInRootScaffold = true,
                    popupAlignment = popupAlignment,
                )

                if (passwordPopupContent != null) {
                    OverlayListPopup(
                        show = showPasswordPopup,
                        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                        alignment = popupAlignment,
                        onDismissRequest = { showPasswordPopup = false },
                        renderInRootScaffold = true,
                        enableWindowDim = false,
                    ) {
                        passwordPopupContent { showPasswordPopup = false }
                    }
                }
            }
        }
    }

    @Composable
    fun TempFloatingBall(
        actions: List<VirtualButtonAction>,
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
    ) {
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        var showMenu by remember { mutableStateOf(false) }
        val asBundleShared by appSettings.bundleState.collectAsState()

        var asBundle by rememberSaveable(asBundleShared) { mutableStateOf(asBundleShared) }
        LaunchedEffect(asBundleShared) {
            if (asBundle != asBundleShared) {
                asBundle = asBundleShared
            }
        }
        LaunchedEffect(asBundle) {
            delay(500.milliseconds)
            // 使用 updateBundle 而非 saveBundle：只保存 TempFloatingBall 实际修改的字段
            // （悬浮球位置），避免将过时的 fullscreenControlMode 等字段写回 appSettings，
            // 从而防止覆盖 FullscreenControlScreen 中用户最新的控制模式切换。
            appSettings.updateBundle { current ->
                current.copy(
                    swipeFloatingBallOffsetX = asBundle.swipeFloatingBallOffsetX,
                    swipeFloatingBallOffsetY = asBundle.swipeFloatingBallOffsetY,
                )
            }
        }

        var offsetXFraction by rememberSaveable { mutableFloatStateOf(asBundle.swipeFloatingBallOffsetX) }
        var offsetYFraction by rememberSaveable { mutableFloatStateOf(asBundle.swipeFloatingBallOffsetY) }

        // 单击菜单：显示未勾选的项
        val clickPopupActions = remember(asBundle.swipeFloatingBallActionsLayout) {
            val byId = VirtualButtonAction.entries.associateBy { it.id }
            val parsed = asBundle.swipeFloatingBallActionsLayout
                .takeIf { it.isNotBlank() }
                ?.split(',')
                ?.mapNotNull { item ->
                    val parts = item.trim().split(':')
                    if (parts.size != 2) return@mapNotNull null
                    val id = parts[0]
                    val visible = parts[1] == "1"
                    val action = byId[id]?.takeIf {
                        it != VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL &&
                        it != VirtualButtonAction.MORE
                    } ?: return@mapNotNull null
                    action to visible
                }
                .orEmpty()
                .distinctBy { it.first.id }
            // 已排序且未勾选的项
            val uncheckedSorted = parsed.filter { !it.second }.map { it.first }
            // 补充缺失的项（默认显示在单击菜单）
            val missing = VirtualButtonAction.entries
                .filter {
                    it != VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL &&
                    it != VirtualButtonAction.MORE
                }
                .filterNot { action -> parsed.any { it.first.id == action.id } }
            (uncheckedSorted + missing).distinctBy { it.id }
        }

        // 滑动菜单：只显示勾选的项
        val swipePopupActions = remember(asBundle.swipeFloatingBallActionsLayout) {
            val byId = VirtualButtonAction.entries.associateBy { it.id }
            val parsed = asBundle.swipeFloatingBallActionsLayout
                .takeIf { it.isNotBlank() }
                ?.split(',')
                ?.mapNotNull { item ->
                    val parts = item.trim().split(':')
                    if (parts.size != 2) return@mapNotNull null
                    val id = parts[0]
                    val visible = parts[1] == "1"
                    val action = byId[id]?.takeIf {
                        it != VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL &&
                        it != VirtualButtonAction.MORE
                    } ?: return@mapNotNull null
                    action to visible
                }
                .orEmpty()
                .distinctBy { it.first.id }
            // 已排序且勾选的项
            val visibleSorted = parsed.filter { it.second }.map { it.first }
            // 补充缺失的项（默认不显示在滑动菜单）
            val missing = VirtualButtonAction.entries
                .filter {
                    it != VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL &&
                    it != VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL &&
                    it != VirtualButtonAction.MORE
                }
                .filterNot { action -> parsed.any { it.first.id == action.id } }
            (visibleSorted + missing).distinctBy { it.id }
        }

        // 长按滑动选择状态
        var showSwipeMenu by remember { mutableStateOf(false) }
        var selectedIndex by remember { mutableStateOf(-1) }  // -1 表示手指不在任何菜单项上
        val density = androidx.compose.ui.platform.LocalDensity.current
        val itemHeight = 48.dp

        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
        ) {
            val ballSize = asBundleShared.tempFloatingButtonSizeDp.dp
            val maxX = (maxWidth - ballSize).coerceAtLeast(0.dp)
            val maxY = (maxHeight - ballSize).coerceAtLeast(0.dp)
            val currentX = maxX * offsetXFraction.coerceIn(0f, 1f)
            val currentY = maxY * offsetYFraction.coerceIn(0f, 1f)

            // 菜单位置计算
            val menuOnLeft = offsetXFraction > 0.5f
            val menuWidth = 180.dp
            val swipeVisibleItemCount = swipePopupActions.size
            val swipeVisibleMenuHeight = itemHeight * swipeVisibleItemCount

            // 拖动选择时的菜单（放在外层，避免被悬浮球裁剪）
            if (showSwipeMenu) {
                val menuOffsetX = if (menuOnLeft) currentX - menuWidth - 8.dp else currentX + ballSize + 8.dp
                val menuOffsetY = currentY + (ballSize - swipeVisibleMenuHeight) / 2
                
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                menuOffsetX.roundToPx(),
                                menuOffsetY.roundToPx(),
                            )
                        }
                        .width(menuWidth)
                        .height(swipeVisibleMenuHeight)
                        .background(
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    Column {
                        swipePopupActions.forEachIndexed { index, action ->
                            val isSelected = index == selectedIndex
                            
                            Row(
                                modifier = Modifier
                                    .width(menuWidth)
                                    .height(itemHeight)
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(
                                                color = colorScheme.primary.copy(alpha = 0.5f),
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = stringResource(action.titleResId),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(action.titleResId),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            currentX.roundToPx(),
                            currentY.roundToPx(),
                        )
                    }
                    .size(ballSize)
                    .pointerInput(maxX, maxY, swipePopupActions) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startTime = System.currentTimeMillis()
                            var hasMoved = false
                            var isLongPress = false
                            var totalDragX = 0f
                            var totalDragY = 0f
                            val dragStartXFraction = offsetXFraction
                            val dragStartYFraction = offsetYFraction

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (!change.pressed) {
                                    // 手指抬起
                                    val pressDuration = System.currentTimeMillis() - startTime
                                    if (hasMoved && showSwipeMenu && selectedIndex >= 0) {
                                        // 拖动选择模式松手且手指在菜单项上：执行选中项
                                        val action = swipePopupActions[selectedIndex]
                                        if (action == VirtualButtonAction.PASSWORD_INPUT && passwordPopupContent != null) {
                                            // 密码输入特殊处理
                                        } else {
                                            scope.launch { onAction(action) }
                                        }
                                    }
                                    showSwipeMenu = false
                                    selectedIndex = -1
                                    if (!hasMoved && pressDuration < 300) {
                                        showMenu = true
                                    }
                                    isLongPress = false
                                    break
                                }

                                // 累积拖动增量
                                totalDragX += change.position.x - change.previousPosition.x
                                totalDragY += change.position.y - change.previousPosition.y

                                // 检查是否移动超过阈值
                                if (kotlin.math.abs(totalDragX) > 5 || kotlin.math.abs(totalDragY) > 5) {
                                    hasMoved = true
                                }

                                val pressDuration = System.currentTimeMillis() - startTime

                                if (!isLongPress && !showSwipeMenu && pressDuration > 300) {
                                    // 长按触发：先振动提示可拖动
                                    isLongPress = true
                                    haptic.longPress()
                                }

                                if (isLongPress && hasMoved) {
                                    // 长按后拖动：移动悬浮球位置
                                    change.consume()
                                    val nextX = (maxX.toPx() * dragStartXFraction + totalDragX)
                                        .coerceIn(0f, maxX.toPx())
                                    val nextY = (maxY.toPx() * dragStartYFraction + totalDragY)
                                        .coerceIn(0f, maxY.toPx())
                                    offsetXFraction = if (maxX > 0.dp) nextX / maxX.toPx() else 0f
                                    offsetYFraction = if (maxY > 0.dp) nextY / maxY.toPx() else 0f

                                    // 持久化保存位置
                                    asBundle = asBundle.copy(
                                        swipeFloatingBallOffsetX = offsetXFraction,
                                        swipeFloatingBallOffsetY = offsetYFraction
                                    )
                                } else if (hasMoved && !isLongPress && !showSwipeMenu) {
                                    // 直接拖动：进入滑动选择模式
                                    showSwipeMenu = true
                                    selectedIndex = -1
                                    haptic.contextClick()
                                }

                                if (showSwipeMenu && !isLongPress) {
                                    with(density) {
                                        val itemHeightPx = itemHeight.toPx()
                                        val menuWidthPx = menuWidth.toPx()
                                        val swipeVisibleMenuHeightPx = swipeVisibleMenuHeight.toPx()
                                        val ballSizePx = ballSize.toPx()
                                        val spacingPx = 8.dp.toPx()

                                        // 实时计算悬浮球位置和菜单位置方向
                                        val realOffsetXFraction = offsetXFraction.coerceIn(0f, 1f)
                                        val realOffsetYFraction = offsetYFraction.coerceIn(0f, 1f)
                                        val realMenuOnLeft = realOffsetXFraction > 0.5f

                                        // 基于手指相对于悬浮球的偏移量来判断是否在菜单区域
                                        // 菜单总是在悬浮球的左边或右边（根据位置）
                                        val fingerRelativeX = change.position.x
                                        val fingerRelativeY = change.position.y

                                        // 计算菜单相对于悬浮球的边界（使用相对坐标）
                                        val menuStartX: Float
                                        val menuEndX: Float
                                        if (realMenuOnLeft) {
                                            // 菜单在左侧：从悬浮球左边向左延伸
                                            menuEndX = -spacingPx  // 菜单右边缘在悬浮球左边缘左边一点
                                            menuStartX = menuEndX - menuWidthPx  // 菜单左边缘
                                        } else {
                                            // 菜单在右侧：从悬浮球右边向右延伸
                                            menuStartX = ballSizePx + spacingPx  // 菜单左边缘在悬浮球右边缘右边一点
                                            menuEndX = menuStartX + menuWidthPx  // 菜单右边缘
                                        }

                                        // 菜单垂直居中对齐悬浮球
                                        val menuStartY = (ballSizePx - swipeVisibleMenuHeightPx) / 2
                                        val menuEndY = menuStartY + swipeVisibleMenuHeightPx

                                        // 判断手指是否在菜单区域内（全部使用相对坐标）
                                        if (fingerRelativeX >= menuStartX && fingerRelativeX <= menuEndX &&
                                            fingerRelativeY >= menuStartY && fingerRelativeY <= menuEndY) {

                                            val relativeY = fingerRelativeY - menuStartY
                                            val newSelectedIndex = (relativeY / itemHeightPx).toInt()
                                                .coerceIn(0, swipePopupActions.size - 1)

                                            if (newSelectedIndex != selectedIndex) {
                                                selectedIndex = newSelectedIndex
                                                haptic.contextClick()
                                            }
                                        } else {
                                            if (selectedIndex != -1) {
                                                selectedIndex = -1
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
            ) {
                var showPasswordPopup by remember { mutableStateOf(false) }

                val ringSize = ballSize / 2
                val ringWidth = ballSize / 24
                val backgroundAlpha =
                    (asBundleShared.swipeFloatingBallBackgroundAlphaPercent / 100f)
                        .coerceIn(0.1f, 1f)
                val ringAlpha =
                    (asBundleShared.swipeFloatingBallRingAlphaPercent / 100f)
                        .coerceIn(0f, 1f)
                
                Button(
                    modifier = Modifier.fillMaxSize(),
                    onClick = {
                        haptic.contextClick()
                        showMenu = true
                    },
                    cornerRadius = ballSize / 2,
                    minHeight = ballSize,
                    insideMargin = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        color = Color.Black.copy(alpha = backgroundAlpha),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .clip(CircleShape)
                            .then(
                                if (ringAlpha > 0f) {
                                    Modifier.border(
                                        ringWidth,
                                        Color.White.copy(alpha = ringAlpha),
                                        CircleShape,
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }

                ActionPopup(
                    show = showMenu,
                    actions = clickPopupActions,
                    onDismiss = { showMenu = false },
                    onAction = {
                        if (it == VirtualButtonAction.PASSWORD_INPUT && passwordPopupContent != null) {
                            showPasswordPopup = true
                        } else {
                            scope.launch { onAction(it) }
                        }
                        showMenu = false
                    },
                    passwordPopupContent = passwordPopupContent,
                    renderInRootScaffold = false,
                    popupAlignment = if (menuOnLeft) PopupPositionProvider.Align.TopEnd else PopupPositionProvider.Align.TopStart,
                )

                if (passwordPopupContent != null) {
                    OverlayListPopup(
                        show = showPasswordPopup,
                        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                        alignment = if (menuOnLeft) PopupPositionProvider.Align.TopEnd else PopupPositionProvider.Align.TopStart,
                        onDismissRequest = { showPasswordPopup = false },
                        renderInRootScaffold = true,
                        enableWindowDim = false,
                    ) {
                        passwordPopupContent { showPasswordPopup = false }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionPopup(
        show: Boolean,
        actions: List<VirtualButtonAction>,
        onDismiss: () -> Unit,
        onAction: suspend (VirtualButtonAction) -> Unit,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
        renderInRootScaffold: Boolean,
        popupAlignment: PopupPositionProvider.Align = PopupPositionProvider.Align.TopEnd,
        popupBottomPadding: Dp = 0.dp,
    ) {
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val spinnerItems = actions.map { action ->
            val title = stringResource(action.titleResId)
            DropdownItem(
                icon = {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = title,
                        modifier = Modifier
                            .padding(end = UiSpacing.ContentVertical),
                    )
                },
                title = title,
            )
        }

        NavOverlayListPopup(
            show = show,
            startDestination = ActionPopupDestination.Actions,
            popupAlignment = popupAlignment,
            onDismiss = onDismiss,
            renderInRootScaffold = renderInRootScaffold,
            popupBottomPadding = popupBottomPadding,
        ) { destination, navigateTo, dismiss ->
            ListPopupColumn {
                if (destination == ActionPopupDestination.Actions)
                    spinnerItems.forEachIndexed { index, entry ->
                        SpinnerItemImpl(
                            entry = entry,
                            entryCount = spinnerItems.size,
                            isSelected = false,
                            index = index,
                            spinnerColors = DropdownDefaults.dropdownColors(),
                            dialogMode = false,
                            onSelectedIndexChange = { selectedIdx ->
                                haptic.confirm()
                                val selectedAction = actions[selectedIdx]
                                if (
                                    selectedAction == VirtualButtonAction.PASSWORD_INPUT &&
                                    passwordPopupContent != null
                                ) {
                                    navigateTo(ActionPopupDestination.Passwords)
                                } else {
                                    scope.launch { onAction(selectedAction) }
                                    dismiss()
                                }
                            },
                        )
                    }
                else if (passwordPopupContent != null)
                    passwordPopupContent { dismiss() }
                else
                    dismiss()
            }
        }
    }

    @Composable
    private fun <Destination> NavOverlayListPopup(
        show: Boolean,
        startDestination: Destination,
        popupAlignment: PopupPositionProvider.Align,
        onDismiss: () -> Unit,
        renderInRootScaffold: Boolean,
        popupBottomPadding: Dp = 0.dp,
        content: @Composable (
            destination: Destination,
            navigateTo: (Destination) -> Unit,
            dismiss: () -> Unit,
        ) -> Unit,
    ) {
        var destination by remember(show, startDestination) { mutableStateOf(startDestination) }
        OverlayListPopup(
            show = show,
            popupPositionProvider =
                rememberBottomSafeContextMenuPositionProvider(popupBottomPadding),
            alignment = popupAlignment,
            onDismissRequest = onDismiss,
            renderInRootScaffold = renderInRootScaffold,
            enableWindowDim = false,
        ) {
            content(destination, { destination = it }, onDismiss)
        }
    }

    @Composable
    private fun rememberBottomSafeContextMenuPositionProvider(
        bottomPadding: Dp,
    ): PopupPositionProvider = remember(bottomPadding) {
        if (bottomPadding <= 0.dp) {
            ListPopupDefaults.ContextMenuPositionProvider
        } else {
            BottomSafeContextMenuPositionProvider(bottomPadding)
        }
    }

    private class BottomSafeContextMenuPositionProvider(
        private val bottomPadding: Dp,
    ): PopupPositionProvider {
        private val delegate = ListPopupDefaults.ContextMenuPositionProvider

        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): IntOffset = delegate.calculatePosition(
            anchorBounds = anchorBounds,
            windowBounds = windowBounds,
            layoutDirection = layoutDirection,
            popupContentSize = popupContentSize,
            popupMargin = popupMargin,
            alignment = alignment,
        )

        override fun getMargins(): PaddingValues = PaddingValues(bottom = bottomPadding)
    }
}
