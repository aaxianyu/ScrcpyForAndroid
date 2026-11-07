package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.rememberAnimationsDisabled
import io.github.miuzarte.scrcpyforandroid.constants.UiAndroidKeycodes
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.longPress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.ScreenCapture
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.math.sin
import kotlin.ranges.coerceAtLeast
import kotlin.time.Duration.Companion.milliseconds

// 动作语义: 决定动作由谁执行, 分发方只需按行为分支, 不必再按具体动作穷举
enum class VirtualButtonBehavior {
    // 注入设备按键: 由客户端向设备发送 keycode
    INJECT_KEYCODE,

    // 客户端本地动作: 需要宿主的 scrcpy 会话 / 界面状态, 客户端内部消化
    HOST_ACTION,
}

// 动作出现的界面: 用于把只在流媒体全屏页有意义的动作挡在预览卡之外
enum class VirtualButtonSurface {
    // 设备页预览卡, 全屏专属动作在这里整体隐藏
    // (虚拟按钮排序页不受此过滤, 它要列出全部动作让用户配置)
    PREVIEW,

    // 流媒体全屏页的停靠栏
    FULLSCREEN,
}

enum class VirtualButtonAction(
    val id: String,
    @field:StringRes val titleResId: Int,
    val icon: ImageVector,
    val keycode: Int?,
    val behavior: VirtualButtonBehavior,
    // 是否只在流媒体全屏页显示, 预览卡与排序页会整体隐藏该动作
    val fullscreenOnly: Boolean = false,
) {
    MORE(
        id = "more",
        titleResId = R.string.vb_more,
        icon = MiuixIcons.More,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    HOME(
        id = "home",
        titleResId = R.string.vb_home,
        icon = ScrcpyIcons.SysbarHome,
        keycode = UiAndroidKeycodes.HOME,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    BACK(
        id = "back",
        titleResId = R.string.vb_back,
        icon = ScrcpyIcons.SysbarBack,
        keycode = UiAndroidKeycodes.BACK,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    APP_SWITCH(
        id = "app_switch",
        titleResId = R.string.vb_app_switch,
        icon = ScrcpyIcons.SysbarRecent,
        keycode = UiAndroidKeycodes.APP_SWITCH,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    MENU(
        id = "menu",
        titleResId = R.string.vb_menu,
        icon = MiuixIcons.ListView,
        keycode = UiAndroidKeycodes.MENU,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    NOTIFICATION(
        id = "notification",
        titleResId = R.string.vb_notifications,
        icon = Icons.Rounded.Notifications,
        keycode = UiAndroidKeycodes.NOTIFICATION,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    VOLUME_UP(
        id = "volume_up",
        titleResId = R.string.vb_volume_up,
        icon = Icons.AutoMirrored.Rounded.VolumeUp,
        keycode = UiAndroidKeycodes.VOLUME_UP,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    VOLUME_DOWN(
        id = "volume_down",
        titleResId = R.string.vb_volume_down,
        icon = Icons.AutoMirrored.Rounded.VolumeDown,
        keycode = UiAndroidKeycodes.VOLUME_DOWN,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    VOLUME_MUTE(
        id = "volume_mute",
        titleResId = R.string.vb_volume_mute,
        icon = Icons.AutoMirrored.Rounded.VolumeOff,
        keycode = UiAndroidKeycodes.VOLUME_MUTE,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    POWER(
        id = "power",
        titleResId = R.string.vb_lock_screen,
        icon = Icons.Rounded.PowerSettingsNew,
        keycode = UiAndroidKeycodes.POWER,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    SCREENSHOT(
        id = "screenshot",
        titleResId = R.string.vb_screenshot,
        icon = MiuixIcons.ScreenCapture,
        keycode = UiAndroidKeycodes.SYSRQ,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    PASSWORD_INPUT(
        id = "password_input",
        titleResId = R.string.vb_fill_password,
        icon = Icons.Rounded.Password,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    ALL_APPS(
        id = "all_apps",
        titleResId = R.string.vb_all_apps,
        icon = MiuixIcons.All,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    RECENT_TASKS(
        id = "recent_tasks",
        titleResId = R.string.vb_recent_tasks,
        icon = Icons.Rounded.DashboardCustomize,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    TOGGLE_IME(
        id = "toggle_ime",
        titleResId = R.string.vb_toggle_ime,
        icon = Icons.Rounded.Keyboard,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    PASTE_LOCAL_CLIPBOARD(
        id = "paste_local_clipboard",
        titleResId = R.string.vb_paste_clipboard,
        icon = Icons.Rounded.ContentPaste,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    EXIT_FULLSCREEN(
        id = "exit_fullscreen",
        titleResId = R.string.vb_exit_fullscreen,
        icon = Icons.Rounded.FullscreenExit,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
        fullscreenOnly = true,
    ),
    EXPAND_STATUS_BAR(
        id = "expand_status_bar",
        titleResId = R.string.vb_expand_status_bar,
        icon = Icons.Rounded.Dashboard,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    SHOW_SWIPE_FLOATING_BALL(
        id = "show_swipe_floating_ball",
        titleResId = R.string.vb_temp_hide_virtual_buttons,
        icon = Icons.Rounded.RadioButtonChecked,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    HIDE_SWIPE_FLOATING_BALL(
        id = "hide_swipe_floating_ball",
        titleResId = R.string.vb_restore_virtual_buttons,
        icon = ScrcpyIcons.SysbarNavigation,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    );
}

data class VirtualButtonItem(
    val action: VirtualButtonAction,
    val showOutside: Boolean,
)

data class FloatingBallMenuItem(
    val action: VirtualButtonAction,
    val visible: Boolean,
)

// 动作列表中「展开密码列表」的那一项: 它是级联菜单的触发项, 本身不是可执行动作
private val PasswordMenuAction = VirtualButtonAction.PASSWORD_INPUT

/**
 * 宿主动作回调: 由承载虚拟按钮的界面实现, 只有界面自己知道这些动作该落到什么状态上
 *
 * 新增一个宿主动作时, 在这里加一个方法即可, 不需要再去每个界面补一个 when 分支
 */
interface VirtualButtonHost {
    // 关闭流媒体全屏页, 回到设备页
    fun handleExitFullscreen() = Unit

    // 打开最近任务面板
    fun handleShowRecentTasks() = Unit

    // 打开应用列表面板
    fun handleShowAllApps() = Unit

    // 拉起设备输入法
    fun handleToggleIme() = Unit

    // 展开/收起设备状态栏（控制中心）
    fun handleExpandStatusBar() = Unit

    // 切换到滑动悬浮球 / 切回虚拟按钮（仅全屏控制模式页实现）
    fun handleShowSwipeFloatingBall() = Unit
    fun handleHideSwipeFloatingBall() = Unit

    // 把本机剪贴板内容粘贴到设备
    fun handlePasteLocalClipboard() = Unit
}

object VirtualButtonActions {
    val all = VirtualButtonAction.entries

    /** 悬浮球语境下无意义的项：没有任何可收起的菜单，也没有可切换的按钮条 */
    val floatingBallExcluded = setOf(
        VirtualButtonAction.MORE,
        VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL,
    )

    private val byId = all.associateBy { it.id }

    private val byKeycode = all.mapNotNull { action ->
        action.keycode?.let { keycode -> keycode to action }
    }.toMap()

    fun byKeycode(keycode: Int): VirtualButtonAction? = byKeycode[keycode]

    // 该界面上可见的动作: 全屏专属动作只在流媒体全屏页提供
    fun visibleOn(surface: VirtualButtonSurface): List<VirtualButtonAction> = all.filter { action ->
        when (surface) {
            VirtualButtonSurface.FULLSCREEN -> true
            VirtualButtonSurface.PREVIEW -> !action.fullscreenOnly
        }
    }

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
        // 新增动作无需迁移存储: 未出现在已存布局里的动作统一追加到更多菜单
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

    fun splitLayout(
        items: List<VirtualButtonItem>,
        surface: VirtualButtonSurface = VirtualButtonSurface.FULLSCREEN,
    ): Pair<List<VirtualButtonAction>, List<VirtualButtonAction>> {
        val visible = visibleOn(surface).toSet()
        val shown = items.filter { it.action in visible }
        val outside = shown.filter { it.showOutside }.map { it.action }
        val more = shown.filter { !it.showOutside }.map { it.action }
        return outside to more
    }

    /** 悬浮球菜单可排序列（排除悬浮球语境下无意义的项） */
    val floatingBallCandidates = all.filterNot { it in floatingBallExcluded }

    /**
     * 解析悬浮球菜单布局。
     *
     * 存储串里缺失的项会补在末尾并沿用「不显示」，这样后续版本新增按钮不会
     * 突然挤进用户的滑出菜单；用户在排序页主动勾选后才出现。
     */
    fun parseFloatingBallLayout(raw: String): List<FloatingBallMenuItem> {
        val parsed = raw.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.mapNotNull { item ->
                val parts = item.trim().split(':')
                if (parts.size != 2) return@mapNotNull null
                val action = byId[parts[0]] ?: return@mapNotNull null
                if (action in floatingBallExcluded) return@mapNotNull null
                FloatingBallMenuItem(action, parts[1] == "1")
            }
            .orEmpty()
            .distinctBy { it.action.id }
        val base = parsed.ifEmpty {
            parseFloatingBallLayout(AppSettings.SWIPE_FLOATING_BALL_ACTIONS_LAYOUT.defaultValue)
        }
        val missing = floatingBallCandidates
            .filterNot { action -> base.any { it.action == action } }
            .map { FloatingBallMenuItem(it, visible = false) }
        return base + missing
    }

    fun encodeFloatingBallLayout(items: List<FloatingBallMenuItem>): String {
        return items.joinToString(",") { item ->
            "${item.action.id}:${if (item.visible) "1" else "0"}"
        }
    }

    /**
     * 不分「栏上 / 更多」的动作顺序, 用于把动作收进单个入口的形态 (悬浮球)
     *
     * 与 [splitLayout] 同一份用户排序, 因此悬浮球的菜单顺序与停靠栏一致;
     * 不需要的项 (例如"更多"这个开关本身) 由调用方剔除
     */
    fun mergedOrder(
        items: List<VirtualButtonItem>,
        excluded: Set<VirtualButtonAction> = emptySet(),
    ): List<VirtualButtonAction> {
        val visible = visibleOn(VirtualButtonSurface.FULLSCREEN).toSet()
        return items.map { it.action }
            .filter { it in visible && it !in excluded }
            .distinct()
    }

    /**
     * 虚拟按钮动作的唯一分发点
     *
     * 按 [VirtualButtonBehavior] 分类处理: 注入按键的动作直接下发设备, 客户端本地动作交给
     * [host]; 因此新增动作不会再掉进某个界面的 else 兜底分支里被静默忽略
     *
     * [scope] 用一个主线程作用域即可: 界面回调本就在主线程, 设备侧的下发由各回调内部
     * 自行切到 IO, 与重构前逐动作手写时的线程语义保持一致
     */
    fun perform(
        scope: CoroutineScope,
        action: VirtualButtonAction,
        onInjectKeycode: suspend (Int) -> Unit,
        host: VirtualButtonHost,
    ) {
        when (action.behavior) {
            VirtualButtonBehavior.INJECT_KEYCODE -> {
                val keycode = action.keycode ?: return
                scope.launch { onInjectKeycode(keycode) }
            }

            // 宿主动作同步执行, 不额外启动协程, 保证在调用线程 (主线程) 上落地
            VirtualButtonBehavior.HOST_ACTION -> when (action) {
                VirtualButtonAction.MORE -> Unit
                // 密码列表由菜单自己做二级展开, 选中密码才是填充动作
                VirtualButtonAction.PASSWORD_INPUT -> Unit
                VirtualButtonAction.EXIT_FULLSCREEN -> host.handleExitFullscreen()
                VirtualButtonAction.RECENT_TASKS -> host.handleShowRecentTasks()
                VirtualButtonAction.ALL_APPS -> host.handleShowAllApps()
                VirtualButtonAction.TOGGLE_IME -> host.handleToggleIme()
                VirtualButtonAction.EXPAND_STATUS_BAR -> host.handleExpandStatusBar()
                VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL -> host.handleShowSwipeFloatingBall()
                VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL -> host.handleHideSwipeFloatingBall()
                VirtualButtonAction.PASTE_LOCAL_CLIPBOARD -> host.handlePasteLocalClipboard()
                // 该动作标了 HOST_ACTION 却没有分发目标: 属于接错线, 直接暴露而不是静默吞掉
                else -> error("unhandled host action: ${action.id}")
            }
        }
    }
}

/**
 * 动作列表转成级联菜单的一级条目: [PasswordMenuAction] 展开后就是二级的密码列表
 *
 * [passwordChildren] 为 null 表示宿主没有提供密码列表, 此时该动作仍然展示, 但禁用且不展开,
 * 避免用户点了没有任何反馈
 */
@Composable
private fun rememberMenuEntries(
    actions: List<VirtualButtonAction>,
    passwordChildren: List<DropdownItem>?,
    dispatch: (VirtualButtonAction) -> Unit,
): List<DropdownEntry> {
    val titles = actions.map { stringResource(it.titleResId) }
    // 回调每个组合都是新实例, 所以不进 key: 条目在菜单展开期间必须保持同一份实例,
    // 否则级联菜单会把已展开的二级菜单收起
    val currentDispatch by rememberUpdatedState(dispatch)
    return remember(actions, titles, passwordChildren) {
        menuEntries(actions, titles, passwordChildren) { action ->
            currentDispatch(action)
        }
    }
}

private fun menuEntries(
    actions: List<VirtualButtonAction>,
    titles: List<String>,
    passwordChildren: List<DropdownItem>?,
    dispatch: (VirtualButtonAction) -> Unit,
): List<DropdownEntry> {
    val items = actions.mapIndexed { index, action ->
        val title = titles[index]
        val icon: @Composable (Modifier) -> Unit = { iconModifier ->
            Icon(
                imageVector = action.icon,
                contentDescription = title,
                modifier = iconModifier,
            )
        }
        if (action == PasswordMenuAction) {
            DropdownItem(
                text = title,
                enabled = !passwordChildren.isNullOrEmpty(),
                icon = icon,
                children = passwordChildren,
            )
        } else {
            DropdownItem(
                text = title,
                icon = icon,
                onClick = { dispatch(action) },
            )
        }
    }
    return listOf(DropdownEntry(items = items))
}

// 密码列表是一层平铺的条目, 作为二级菜单时不需要再分组
private fun passwordEntry(items: List<DropdownItem>): List<DropdownEntry> =
    listOf(DropdownEntry(items = items))

/** 弹层槽位的唯一持久状态: 当前打开的是哪个槽位 */
private class PopupSlots<S> {
    private var openSlot: S? by mutableStateOf(null)

    fun isOpen(slot: S): Boolean = openSlot == slot

    fun open(slot: S) {
        openSlot = slot
    }

    fun close() {
        openSlot = null
    }
}

/**
 * 虚拟按钮弹层的当次组合视图
 *
 * 只有槽位状态留在 [PopupSlots] 里跨组合保留; 条目、密码列表与分发回调都取当次组合的值,
 * 否则弹层会一直展示首次组合时的快照 (密码列表变了或者宿主状态变了都读不到)
 *
 * 槽位由调用方传进来, 因为预览卡与停靠栏可以同时存在, 两侧的菜单是互不影响的两份状态
 */
private class ActionPopups<S>(
    private val slots: PopupSlots<S>,
    val menuSlot: S,
    val passwordSlot: S?,
    val entries: List<DropdownEntry>,
    val passwordChildren: List<DropdownItem>?,
    // 动作分发: 注入按键的在回调内部切到 IO, 与重构前的线程语义一致
    private val dispatch: (VirtualButtonAction) -> Unit,
) {
    fun isOpen(slot: S): Boolean = slots.isOpen(slot)

    fun close() {
        slots.close()
    }

    /** 点了某个动作按钮: 「更多」开菜单, 密码项开二级列表, 其余按行为分发 */
    fun trigger(action: VirtualButtonAction) {
        when {
            action == VirtualButtonAction.MORE -> slots.open(menuSlot)

            // 没有密码列表可展开 (例如没配密码) 时仍然弹出菜单, 而不是点了没反应
            action == PasswordMenuAction ->
                if (passwordChildren.isNullOrEmpty() || passwordSlot == null) slots.open(menuSlot)
                else slots.open(passwordSlot)

            else -> dispatch(action)
        }
    }
}

class VirtualButtonBar(
    private val outside: List<VirtualButtonAction>,
    private val more: List<VirtualButtonAction>,
    private val showSwipeFloatingBall: Boolean = false,
) {
    enum class FullscreenDock {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
    }

    /**
     * 弹层槽位: 每个"锚点"各自持有独立的状态, 因此预览卡、停靠栏、悬浮球各自的菜单互不影响
     *
     * 菜单与密码二级列表共用一个锚点 (二级菜单本来就挂在它的锚点上), 所以要分成两套槽位
     */
    private enum class PopupSlot {
        PreviewMore,
        PreviewPassword,
        FullscreenMore,
        FullscreenPassword,
        Ball,
    }

    // 每条渲染路径各持一份状态 (预览卡与停靠栏会同时存在), 但结构、条目与分发完全同源
    @Composable
    private fun rememberActionPopups(
        passwordChildren: List<DropdownItem>?,
        onAction: suspend (VirtualButtonAction) -> Unit,
        menuSlot: PopupSlot = PopupSlot.FullscreenMore,
        passwordSlot: PopupSlot? = PopupSlot.FullscreenPassword,
    ): ActionPopups<PopupSlot> {
        val scope = rememberCoroutineScope()
        // 只有槽位状态跨组合保留, 其余数据每次组合重新构造
        val slots = remember(this) { PopupSlots<PopupSlot>() }
        fun dispatch(action: VirtualButtonAction) {
            scope.launch { onAction(action) }
        }
        // 条目回调经 rememberUpdatedState 转发, 点下去时读到的总是当次组合的分发
        val entries = rememberMenuEntries(more, passwordChildren) { action -> dispatch(action) }
        return ActionPopups(
            slots = slots,
            menuSlot = menuSlot,
            passwordSlot = passwordSlot,
            entries = entries,
            passwordChildren = passwordChildren,
            dispatch = ::dispatch,
        )
    }

    // 预览卡只渲染该界面可见的动作, 全屏专属动作在这里整体隐藏
    @Composable
    fun Preview(
        enabled: Boolean,
        showText: Boolean,
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordChildren: List<DropdownItem>? = null,
        popupBottomPadding: Dp = 0.dp,
    ) {
        val popups = rememberActionPopups(
            passwordChildren = passwordChildren,
            onAction = onAction,
            menuSlot = PopupSlot.PreviewMore,
            passwordSlot = PopupSlot.PreviewPassword,
        )
        val previewVisible = remember { VirtualButtonActions.visibleOn(VirtualButtonSurface.PREVIEW).toSet() }
        val visibleActions = outside.filter { it in previewVisible }

        // 预览条与全屏条共用「更多」菜单，但两者的可用项不同：预览里没有全屏可退出，
        // 悬浮球是否存在于预览也由调用方决定。过滤在这里做，避免让调用方各自传一遍菜单。
        val previewMoreActions = remember(more, showSwipeFloatingBall) {
            more.filterNot { action ->
                action == VirtualButtonAction.EXIT_FULLSCREEN ||
                        action == VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL ||
                        action == VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL
            }
        }
        // 外显按钮同理：这三个动作在预览语境下不成立或点了无反应
        // （退出全屏无全屏可退；两处切换项与控制模式联动，预览卡不参与），
        // 默认布局中 exit_fullscreen 为外显，不过滤就会在预览卡出现一个假按钮
        val previewOutsideActions = remember(outside) {
            outside.filterNot { action ->
                action == VirtualButtonAction.EXIT_FULLSCREEN ||
                        action == VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL ||
                        action == VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL
            }
        }

        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            previewOutsideActions.forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    PreviewActionButton(
                        action = action,
                        enabled = enabled,
                        showText = showText,
                        onClick = { popups.trigger(action) },
                    )
                    PreviewActionPopups(
                        state = popups,
                        visibleAction = action,
                        popupBottomPadding = popupBottomPadding,
                    )
                }
            }
        }
    }

    @Composable
    private fun PreviewActionButton(
        action: VirtualButtonAction,
        enabled: Boolean,
        showText: Boolean,
        onClick: () -> Unit,
    ) {
        val haptic = LocalHapticFeedback.current
        Button(
            onClick = {
                haptic.contextClick()
                onClick()
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                color = colorScheme.primary,
                disabledColor = colorScheme.primary.copy(alpha = 0.35f),
            ),
            insideMargin = PaddingValues(0.dp),
        ) {
            val contentColor =
                if (enabled) colorScheme.onPrimary
                else colorScheme.onPrimary.copy(alpha = 0.45f)
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
    }

    @Composable
    private fun PreviewActionPopups(
        state: ActionPopups<PopupSlot>,
        visibleAction: VirtualButtonAction,
        popupBottomPadding: Dp,
    ) {
        if (visibleAction == VirtualButtonAction.MORE) {
            ActionCascadingPopup(
                show = state.isOpen(state.menuSlot),
                entries = state.entries,
                onDismissRequest = state::close,
                alignment = PopupPositionProvider.Align.TopEnd,
                renderInRootScaffold = false,
                popupBottomPadding = popupBottomPadding,
            )
        }
        val passwordSlot = state.passwordSlot
        val passwordChildren = state.passwordChildren
        if (visibleAction == PasswordMenuAction && passwordSlot != null && !passwordChildren.isNullOrEmpty()) {
            ActionCascadingPopup(
                show = state.isOpen(passwordSlot),
                entries = passwordEntry(passwordChildren),
                onDismissRequest = state::close,
                alignment = PopupPositionProvider.Align.TopEnd,
                renderInRootScaffold = false,
                popupBottomPadding = popupBottomPadding,
            )
        }
    }

    @Composable
    fun Fullscreen(
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        dock: FullscreenDock = FullscreenDock.BOTTOM,
        reverseOrder: Boolean = false,
        thickness: Dp = 16.dp,
        passwordChildren: List<DropdownItem>? = null,
    ) {
        val popups = rememberActionPopups(passwordChildren, onAction)

        val isVertical = dock == FullscreenDock.LEFT || dock == FullscreenDock.RIGHT
        val visibleActions =
            if (reverseOrder) outside.asReversed()
            else outside

        // HIDE_SWIPE_FLOATING_BALL 只在悬浮球接管时才有意义，否则弹出的是个点不动的死项
        val fullscreenMoreActions = remember(more, showSwipeFloatingBall) {
            more.filterNot { action ->
                action == VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL && !showSwipeFloatingBall
            }
        }

        // 菜单贴着按钮条弹出。横向停靠时按钮条贴上下边，菜单该往下展开；
        // 纵向停靠时按钮条贴左右边，水平方向按停靠侧对齐。
        val popupAlignment = when (dock) {
            FullscreenDock.LEFT -> PopupPositionProvider.Align.TopStart
            FullscreenDock.RIGHT -> PopupPositionProvider.Align.TopEnd
            FullscreenDock.TOP -> PopupPositionProvider.Align.BottomEnd
            FullscreenDock.BOTTOM -> PopupPositionProvider.Align.TopEnd
        }
        val containerModifier =
            if (isVertical) modifier
                .width(thickness)
                .fillMaxHeight()
            else modifier
                .fillMaxWidth()
                .height(thickness)
        // 按钮要占满自己那一份槽位: 纵向停靠时如果还用横向的"高度 = 停靠栏厚度",
        // 按钮会全部挤在槽位起点那一侧, 看起来整排按钮偏向一端
        val buttonModifier =
            if (isVertical) Modifier.fillMaxSize()
            else Modifier
                .fillMaxWidth()
                .height(thickness)

        if (isVertical) Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val itemModifier = Modifier.weight(1f)
            visibleActions.forEach {
                FullscreenAction(it, thickness, itemModifier, buttonModifier, popups)
            }
        }
        else Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val itemModifier = Modifier.weight(1f)
            visibleActions.forEach {
                FullscreenAction(it, thickness, itemModifier, buttonModifier, popups)
            }
        }
    }

    // 纵向与横向只有容器与按钮尺寸不同, 按钮本体与弹层共用同一套渲染
    // weight 是 RowScope / ColumnScope 的作用域扩展, 因此由调用方算好等分修饰符与按钮尺寸传进来
    @Composable
    private fun FullscreenAction(
        action: VirtualButtonAction,
        thickness: Dp,
        itemModifier: Modifier,
        buttonModifier: Modifier,
        popups: ActionPopups<PopupSlot>,
    ) {
        val haptic = LocalHapticFeedback.current
        Box(modifier = itemModifier) {
            Button(
                onClick = {
                    haptic.contextClick()
                    popups.trigger(action)
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
                    tint = Color.White,
                )
            }

            if (action == VirtualButtonAction.MORE) {
                ActionCascadingPopup(
                    show = popups.isOpen(popups.menuSlot),
                    entries = popups.entries,
                    onDismissRequest = popups::close,
                )
            }
            val passwordSlot = popups.passwordSlot
            val passwordChildren = popups.passwordChildren
            if (action == PasswordMenuAction && passwordSlot != null && !passwordChildren.isNullOrEmpty()) {
                ActionCascadingPopup(
                    show = popups.isOpen(passwordSlot),
                    entries = passwordEntry(passwordChildren),
                    onDismissRequest = popups::close,
                )
            }
        }
    }

    /**
     * 悬浮球: 与停靠栏共用同一套弹层与分发, 只是把可点的东西收成一个球
     *
     * 调用方构造时把 more 传成完整动作列表 (走 [VirtualButtonActions.mergedOrder]), outside 传空;
     * 两边读的是同一份用户排序, 因此球的菜单顺序与停靠栏一致
     *
     * more 由 [VirtualButtonActions.parseStoredLayout] 补全过, 除「更多」以外不会漏动作,
     * 所以这里不需要再兜一个"空菜单"的分支
     */
    @Composable
    fun TempFloatingBall(
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordChildren: List<DropdownItem>? = null,
    ) {
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val asBundle by appSettings.bundleState.collectAsState()

        // 布局解析结果随偏好一起变化；滑出菜单与单击菜单共用同一份排序
        val menuItems = remember(asBundle.swipeFloatingBallActionsLayout) {
            VirtualButtonActions.parseFloatingBallLayout(asBundle.swipeFloatingBallActionsLayout)
        }
        val swipeActions = remember(menuItems) {
            // MIUI 径向形态球数上限 5：排序页勾选时已限制，此处兜底截断
            menuItems.filter { it.visible }.map { it.action }.take(MIUI_RADIAL_MAX)
        }
        val clickActions = remember(menuItems) {
            menuItems.filterNot { it.visible }.map { it.action }
        }

        var showClickMenu by remember { mutableStateOf(false) }
        var showPasswordPopup by remember { mutableStateOf(false) }
        var swipeMenuVisible by remember { mutableStateOf(false) }
        var selectedIndex by remember { mutableIntStateOf(-1) }

        // 刻意不给 rememberSaveable 传 key：位置停止变化后会防抖写回 asBundle，
        // 若把 asBundle 的值当 key，这次写回就会让 state 重建，而手势协程仍握着旧实例
        // —— 表现为「第一次能拖，之后拖不动；切走再切回又恢复一次」。
        // 位置只由本组件自增自减，不需要跟随外部变化。
        var offsetXFraction by rememberSaveable {
            mutableFloatStateOf(asBundle.swipeFloatingBallOffsetX)
        }
        var offsetYFraction by rememberSaveable {
            mutableFloatStateOf(asBundle.swipeFloatingBallOffsetY)
        }

        // 径向子球逐项弹出/收起，对齐 MIUI 面板动效：
        // 弹出 spring(0.6, 420) + 每项 25ms stagger，总长 ≈430ms（含回弹）；
        // 收起 spring(0.99, 1000) + 每项 20ms stagger —— stiffness 220 时 Compose spring
        // 实际收敛 ~340ms 明显偏慢（用户实测反馈），1000 让尾球约 200ms 收完、干脆无过冲。
        // 收起 = 逐球收回球心并淡出（不是瞬间消失）。
        // 项动画只管视觉，命中判定始终按最终位置算，动画飞行中也可正常滑动选择。
        val itemProgress = remember(swipeActions.size) {
            List(swipeActions.size) { Animatable(0f) }
        }

        // 展开态主球对齐 MIUI：白环缩没（150ms decel）→ 白 X 从中心向两端绘入
        // （200ms decelMore，延迟 100ms，同时从 -225° 转正）；收起全部反向。
        // xProgress：0 = 无 X，1 = X 完整；ringScale：1 = 环正常，0 = 缩没。
        // 展开态 = 滑出菜单或单击菜单任一打开；X 与球 0.88 都在该期间保持（MIUI 双操作语义）
        val xActive = swipeMenuVisible || showClickMenu
        // 展开动画关掉（或系统「动画程序时长缩放」为 0）时，子球串直接弹出、收起仍走原动画：
        // 那 25ms×N 的错峰加 spring 回弹是操作前那段等待；命中判定本来就按最终位置算
        val expandInstant = !asBundle.miuiExpandAnimationEnabled || rememberAnimationsDisabled()
        val xProgress = remember { Animatable(0f) }
        val ringScale = remember { Animatable(1f) }
        LaunchedEffect(xActive) {
            if (xActive) {
                launch {
                    ringScale.animateTo(0f, tween(150, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)))
                }
                launch {
                    delay(100)
                    xProgress.animateTo(
                        1f,
                        tween(200, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)),
                    )
                }
            } else {
                launch {
                    ringScale.animateTo(1f, tween(150, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)))
                }
                launch {
                    xProgress.animateTo(
                        0f,
                        tween(150, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)),
                    )
                }
            }
        }

        // 径向子球串：仅由滑动呼出（swipeMenuVisible）驱动——单击菜单不展开球串
        LaunchedEffect(swipeMenuVisible, swipeActions.size, expandInstant) {
            if (swipeMenuVisible && expandInstant) {
                itemProgress.forEach { it.snapTo(1f) }
            } else if (swipeMenuVisible) {
                itemProgress.forEachIndexed { index, anim ->
                    launch {
                        delay(index * 25L)
                        anim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 420f))
                    }
                }
            } else {
                itemProgress.forEachIndexed { index, anim ->
                    launch {
                        delay(index * 20L)
                        anim.animateTo(0f, spring(dampingRatio = 0.99f, stiffness = 1000f))
                    }
                }
            }
        }

        // 按下反馈：对齐 MIUI 的按下即缩 0.88（点击与滑动起点共用）；松手 300ms decel 回弹到 1。
        // 展开态（单击菜单/滑出菜单打开）球保持 0.88 —— MIUI 展开态主球 = 50dp × 0.88 = 44dp
        var ballPressed by remember { mutableStateOf(false) }
        val ballScale by animateFloatAsState(
            targetValue = if (ballPressed || xActive) 0.88f else 1f,
            animationSpec = if (ballPressed || xActive) {
                spring(dampingRatio = 1f, stiffness = 1000f)
            } else {
                tween(300, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))
            },
            label = "sfbBallPressScale",
        )

        // 合并不透明度链路（MIUI 语义：单一滑块直读，100 = 完全不透明）：
        // 背景 alpha = 滑块值直接换算；白环按原版比例 = 背景 × 1.243，上限钳 1。
        // 透明度只在这一处驱动，渲染各层只是引用，避免双处绑定互抢。
        val backgroundAlpha =
            (asBundle.swipeFloatingBallOpacityPercent / 100f).coerceIn(0.1f, 1f)
        val ringAlpha = (backgroundAlpha * 1.243f).coerceIn(0f, 1f)

        // 无操作渐隐（MIUI AutoHideHandler 3000ms）：500ms 轮询——最后触摸距今超 3s
        // 且无菜单/拖动时，主球渐隐到固定 14%（不随用户滑块漂移；用户值低于 14%
        // 时保持原样，渐隐不提亮）。
        // lastTouchMs 由各手势按下处更新；打开全屏 3s 后即渐隐（不依赖手势结束）。
        // 渐隐值用倍数实现（idleDim × 各层自身 alpha），恢复 = 倍数回 1。
        // remember{} 首次组合时执行，取值即组合时刻；基准时间为 0 会令首次 500ms
        // 轮询恒判"距上次触摸已远超 3s"，进全屏后 0.5s 即渐隐（S1）
        var lastTouchMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var idleDimmed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(500)
                idleDimmed = asBundle.miuiIdleDimEnabled &&
                    !ballPressed && !swipeMenuVisible && !showClickMenu &&
                    !showPasswordPopup &&
                    System.currentTimeMillis() - lastTouchMs >= 3000
            }
        }
        val idleDim by animateFloatAsState(
            targetValue = if (idleDimmed) {
                // 固定渐隐到绝对 14%（= 默认不透明度 70% × 20%），不随用户滑块漂移；
                // 换算回倍数（14% / 用户值）。用户值低于 14% 时倍数 > 1，钳 1
                // 保持原样——渐隐不该把球变亮
                (0.14f / backgroundAlpha).coerceAtMost(1f)
            } else {
                1f
            },
            animationSpec = tween(300, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)),
            label = "sfbIdleDim",
        )

        // 拖动过程中频繁写盘没有意义，攒够 500ms 没有新位置再落一次
        LaunchedEffect(offsetXFraction, offsetYFraction) {
            delay(500.milliseconds)
            val current = appSettings.bundleState.value
            if (
                current.swipeFloatingBallOffsetX != offsetXFraction ||
                current.swipeFloatingBallOffsetY != offsetYFraction
            ) {
                appSettings.updateBundle {
                    it.copy(
                        swipeFloatingBallOffsetX = offsetXFraction,
                        swipeFloatingBallOffsetY = offsetYFraction,
                    )
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                swipeMenuVisible = false
                // 位置字段必须在本 dispose 内同步落盘：父级（FullscreenControlScreen）的
                // onDispose 会用整包快照覆盖保存，而该快照可能还带着拖动前的旧位置。
                // runBlocking 阻塞卸载几毫秒，换来「子先写完、父再覆盖」的确定时序 ——
                // 异步写会与父级快照交错，交错时位置回退。
                runBlocking {
                    appSettings.updateBundle {
                        it.copy(
                            swipeFloatingBallOffsetX = offsetXFraction,
                            swipeFloatingBallOffsetY = offsetYFraction,
                        )
                    }
                }
            }
        }

        // 容器尺寸用 onSizeChanged 记录，不走 BoxWithConstraints：后者的 content 是 subcompose
        // 出来的，与 Modifier.offset 组合时球的视觉位置不一定跟随 state 更新 ——
        // 表现为拖动时 offsetFraction 一直在变、球却不动，退出重进才跳到新位置。
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current
        val ballSize = asBundle.tempFloatingButtonSizeDp.dp

        // 边缘吸附动画（300ms decel，y 不吸附）：开关打开且球未贴边时，把球滑向最近
        // 左/右屏边。抽成可复用入口——长按拖球松手、进全屏初始判定两处共用。
        // 用 rememberUpdatedState 包 lambda：手势协程只在 pointerInput key 变化时重建，
        // 直接捕获会在重启后拿着旧的 asBundle 值。
        // snapJob 持有运行中的吸附动画：新手势按下时取消，否则吸附循环会与
        // 拖球/滑动同时改 offsetXFraction，球被两边拉扯。
        var snapJob by remember { mutableStateOf<Job?>(null) }
        val snapToEdge by rememberUpdatedState(suspend {
            if (asBundle.miuiSnapEnabled && offsetXFraction != 0f && offsetXFraction != 1f) {
                val start = offsetXFraction
                val target = if (start < 0.5f) 0f else 1f
                val startNanos = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        val t = ((now - startNanos) / 300_000_000f)
                            .coerceIn(0f, 1f)
                        val eased = 1f - (1f - t) * (1f - t)
                        offsetXFraction = start + (target - start) * eased
                    }
                    if (offsetXFraction == target) break
                }
            }
        })
        // 进全屏/开关打开时若球停在屏中间（未贴边），补一次吸附——
        // 不补的话球会一直停在半空，直到用户手动拖一下才贴边。
        // containerSize 变化（进全屏/旋屏）作为触发之一，避免在尺寸未就绪时误判。
        LaunchedEffect(asBundle.miuiSnapEnabled, containerSize) {
            if (asBundle.miuiSnapEnabled && containerSize != IntSize.Zero) {
                snapJob?.cancel()
                snapJob = launch { snapToEdge() }
            }
        }

        // 首次出现弹入：从虚拟按钮切换到悬浮球时，球从小到大弹出（与展开动效同风格）。
        // 同时解决左上角闪现：containerSize 首帧为 Zero、maxX=0，球会先画在左上角
        // 再跳到真实位置——尺寸未就绪期间直接不渲染（ballEntrance == 0f），首帧即正确。
        val ballEntrance = remember { Animatable(0f) }
        val containerReady = containerSize != IntSize.Zero
        LaunchedEffect(containerReady) {
            if (containerReady && ballEntrance.value == 0f) {
                ballEntrance.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 420f))
            }
        }

        // 白圈几何对齐 MIUI（com.miui.touchassistant 16.0.1.6.1 逆向值）：
        // 环中线在球直径的 53.5% 处，环厚为球直径的 3.5%（50dp 球 → 中线 26.75dp、厚 1.75dp）
        val ringSize = ballSize * 0.535f
        val ringWidth = ballSize * 0.035f
        val containerWidthDp = with(density) { containerSize.width.toDp() }
        val containerHeightDp = with(density) { containerSize.height.toDp() }
        // 球缘离屏边 7dp（MIUI 原版 6.66dp，取整）：移动范围两侧各内缩，
        // 吸附目标 0/1 就是贴边位（currentX = edgeMargin + maxX * fraction）
        val maxX = (containerWidthDp - ballSize - MIUI_BALL_EDGE_MARGIN * 2).coerceAtLeast(0.dp)
        val maxY = (containerHeightDp - ballSize - MIUI_BALL_EDGE_MARGIN * 2).coerceAtLeast(0.dp)
        val currentX = MIUI_BALL_EDGE_MARGIN + maxX * offsetXFraction.coerceIn(0f, 1f)
        val currentY = MIUI_BALL_EDGE_MARGIN + maxY * offsetYFraction.coerceIn(0f, 1f)

        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it },
        ) {

            // 球在右半屏时单击菜单往左开，否则往右开
            val menuOnLeft = offsetXFraction > 0.5f
            val popupAlignment =
                if (menuOnLeft) PopupPositionProvider.Align.TopEnd
                else PopupPositionProvider.Align.TopStart

            // MIUI 径向球串：N 个子球按弧围绕球心分布（无文字版）。
            // 弧朝远离屏边一侧展开（球在右半屏 → 95°→265° 对称于正左 180°），左半屏镜像。
            // 球心距、步进、三级 fallback 全部由 miuiRadialOffsetsPx 给出（渲染/命中/边角判定共用）。
            // 边角形态（三级 fallback）下主球缩放+淡出 180ms 隐藏，收起恢复——
            // isCorner 与坐标同一次计算，杜绝「判定与布局各写一份」的漂移。
            val radialLayout = with(density) {
                miuiRadialOffsetsPx(
                    count = swipeActions.size,
                    ballSizePx = ballSize.toPx(),
                    containerWidthPx = containerWidthDp.toPx(),
                    containerHeightPx = containerHeightDp.toPx(),
                    ballLeftPx = currentX.toPx(),
                    ballTopPx = currentY.toPx(),
                    edgeInsetPx = MIUI_BALL_EDGE_MARGIN.toPx(),
                )
            }
            val ballHide by animateFloatAsState(
                targetValue = if (radialLayout.isCorner && swipeMenuVisible) 0f else 1f,
                animationSpec = tween(180, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)),
                label = "radialBallHide",
            )

            if (swipeActions.isNotEmpty() &&
                (swipeMenuVisible || itemProgress.any { it.value > 0.01f })
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = currentX, y = currentY)
                        .size(ballSize),
                ) {
                    // 目标偏移是「相对球心」，手势/渲染同源；球贴边时由函数内部夹回屏内
                    val radialOffsets = radialLayout.offsets
                    swipeActions.forEachIndexed { index, action ->
                        val itemSelected = index == selectedIndex
                        // 选中放大 1.155（覆盖命中 0.6×球径判定）
                        val itemScale by animateFloatAsState(
                            targetValue = if (itemSelected) 1.155f else 1f,
                            label = "miuiRadialScale",
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = ballSize / 2, y = ballSize / 2)
                                .graphicsLayer {
                                    val p = itemProgress[index].value
                                    val target = radialOffsets[index]
                                    // 子球左上角先落在主球球心，再按进度插值到目标位
                                    val halfPx = ballSize.toPx() / 2f
                                    translationX = target.x * p - halfPx
                                    translationY = target.y * p - halfPx
                                    scaleX = itemScale
                                    scaleY = itemScale
                                    alpha = p
                                }
                                .size(ballSize)
                                .clip(CircleShape)
                                .background(
                                    // 选中色对齐 MIUI：#6B6D72 独立叠加态（球串无菜单底，直接用原值）
                                    if (itemSelected) Color(0xC56B6D72)
                                    else Color(0xFF2B2E33).copy(alpha = backgroundAlpha),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = stringResource(action.titleResId),
                                // 前景白与环/X 同链路（背景 × 1.243 钳 1）：低不透明度下
                                // 图标跟着整体变淡，纯白图标会悬在半透明球上突兀
                                tint = Color.White.copy(alpha = ringAlpha),
                                modifier = Modifier.size(ballSize * 0.48f),
                            )
                        }
                    }
                }
            }

            // 操作名提示：滑到某个球时显示即将执行的操作名（排序页可关）。
            // 落点按展开形态（radialLayout.level，与渲染/命中同源）分三套：
            // - 1 级（水平弧）：展开侧、越过最外侧子球 +16dp；垂直对齐弧端部子球的
            //   5 球理想位（上半屏球对齐最底球、下半屏球对齐最顶球）。重叠时沿
            //   「往屏心方向」垂直逃逸，越过实际最低/最高球 +8dp（其余球都在它内侧，
            //   越过必然全清）。闭合公式，不循环。
            // - 2 级（横排弧）：居中于主球 x（端部球在主球正上/正下方），贴在端部
            //   子球 5 球理想位外侧。方向按实际子球在哪侧判定，不以主球位置推断——
            //   矮横屏 + 球在垂直中段时 fallback 首个候选弧的鼓起方向不可假定。
            // - 3 级（边角扇形）：屏幕正中心。
            // 锚点一律按 5 球理想位求稳（球数变化时提示不跳），避让按实际球位求准。
            if (asBundle.miuiMenuActionHint && swipeMenuVisible &&
                selectedIndex in swipeActions.indices
            ) {
                // 测量完成前不显示（alpha=0）：首帧用零尺寸算位置必然错，
                // onSizeChanged 回调后跳到正确位置——这个跳变就是一帧闪现。
                // 出现动效 = 淡入 + 轻微放大，复用项目已有的 decel 曲线；
                // hintAppear 从 0 起，天然把这个测量窗口覆盖掉，不需要额外状态。
                // 切换选中球/菜单收起均为瞬时：滑动反馈必须即时，且收起与球串
                // spring 收回同步，单独留个淡出的胶囊反而突兀。
                var hintSize by remember { mutableStateOf(IntSize.Zero) }
                val hintReady = hintSize != IntSize.Zero
                val hintAppear by animateFloatAsState(
                    targetValue = if (hintReady) 1f else 0f,
                    animationSpec = tween(
                        180, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f),
                    ),
                    label = "hintAppear",
                )
                val hintWidthDp = with(density) { hintSize.width.toDp() }
                val hintHeightDp = with(density) { hintSize.height.toDp() }
                val ballCenterX = currentX + ballSize / 2
                val ballCenterY = currentY + ballSize / 2
                val halfBall = ballSize / 2
                // 球串半径 R = 1.8 × 球径
                val radiusDp = ballSize * MIUI_RADIAL_RADIUS_FACTOR
                // 1 级弧端部子球（中心角 ±85°）的垂直偏移；水平最外侧子球距离 = R
                val extremeOffset = radiusDp * sin(Math.toRadians(85.0)).toFloat()
                // 弧上最外侧子球离球心的水平距离（R + 子球半径 + 16dp 间隙）
                val clearDp = radiusDp + halfBall + 16.dp
                val margin = 8.dp
                // 选中球放大 1.155，重叠检测按放大后的球矩形算
                val hitHalf = halfBall * 1.155f + margin
                val maxHintX = (containerWidthDp - hintWidthDp).coerceAtLeast(0.dp)
                val maxHintY = (containerHeightDp - hintHeightDp).coerceAtLeast(0.dp)

                // 实际子球中心（dp，含贴边平移——与渲染同源）
                val subBallCentersDp = with(density) {
                    radialLayout.offsets.map {
                        ballCenterX + it.x.toDp() to ballCenterY + it.y.toDp()
                    }
                }
                // hint 矩形与任一实际子球矩形（含放大余量）相交
                fun hintOverlapsBalls(x: Dp, y: Dp): Boolean = subBallCentersDp.any { (cx, cy) ->
                    x < cx + hitHalf &&
                        x + hintWidthDp > cx - hitHalf &&
                        y < cy + hitHalf &&
                        y + hintHeightDp > cy - hitHalf
                }

                val (hintX, hintY) = when (radialLayout.level) {
                    LEVEL_CORNER -> {
                        (containerWidthDp / 2 - hintWidthDp / 2).coerceIn(0.dp, maxHintX) to
                            (containerHeightDp / 2 - hintHeightDp / 2).coerceIn(0.dp, maxHintY)
                    }
                    LEVEL_ROTATED -> {
                        // 横排弧整体在主球一侧：中心角 270° 时 y 偏移全负（子球在上方）、
                        // 90° 时全正（下方）。按偏移和的符号取多数侧（比 all{} 稳健：
                        // 兜底平移理论上可能让单个球越过零线），不靠主球位置推断——
                        // 矮横屏 + 球在垂直中段时首个候选弧方向不可假定。
                        // 端部最高/低球 = 中心槽位球，在主球正上/下方、距离恰为 R
                        val ballsAbove = radialLayout.offsets.map { it.y }.sum() < 0f
                        val x = (ballCenterX - hintWidthDp / 2).coerceIn(0.dp, maxHintX)
                        val y = if (ballsAbove) {
                            ballCenterY - radiusDp - halfBall - margin - hintHeightDp
                        } else {
                            ballCenterY + radiusDp + halfBall + margin
                        }
                        x to y.coerceIn(0.dp, maxHintY)
                    }
                    else -> {
                        // 1 级：水平展开侧 +16dp；垂直锚点 = 弧端部 5 球理想位
                        val upperHalf = ballCenterY < containerHeightDp / 2
                        val anchorX = if (menuOnLeft) {
                            (ballCenterX - clearDp - hintWidthDp).coerceIn(0.dp, maxHintX)
                        } else {
                            (ballCenterX + clearDp).coerceIn(0.dp, maxHintX)
                        }
                        // 上半屏球 → 锚点对齐最底球（提示在弧下方）；下半屏球 → 最顶球
                        val anchorY = (
                            if (upperHalf) ballCenterY + extremeOffset
                            else ballCenterY - extremeOffset
                        ).coerceIn(0.dp, maxHintY)
                        // 重叠 → 垂直逃逸到实际最低/最高球之外（往屏心方向）
                        val finalY = if (hintOverlapsBalls(anchorX, anchorY)) {
                            if (upperHalf) {
                                // 往下逃：提示顶边 = 实际最低球球底 + margin
                                (subBallCentersDp.maxOf { it.second } + hitHalf)
                                    .coerceIn(0.dp, maxHintY)
                            } else {
                                // 往上逃：提示底边 = 实际最高球球顶 - margin
                                (subBallCentersDp.minOf { it.second } - hitHalf - hintHeightDp)
                                    .coerceIn(0.dp, maxHintY)
                            }
                        } else {
                            anchorY
                        }
                        anchorX to finalY
                    }
                }

                Box(
                    modifier = Modifier
                        .offset(x = hintX, y = hintY)
                        .graphicsLayer {
                            alpha = hintAppear
                            val s = 0.92f + 0.08f * hintAppear
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin.Center
                        }
                        .onSizeChanged { hintSize = it }
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xCC2B2E33))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 全部标题叠放：Box 宽度取最宽者（含尚未选中的项），
                    // 选中项切换时只有 alpha 变化，提示尺寸与落点完全不动
                    swipeActions.forEachIndexed { index, action ->
                        Text(
                            text = stringResource(action.titleResId),
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.alpha(
                                if (index == selectedIndex) 1f else 0f,
                            ),
                        )
                    }
                }
            }

                Box(
                    modifier = Modifier
                    .offset(x = currentX, y = currentY)
                    .size(ballSize)
                    // 弹入动画（0→1 从球心放大）兼防左上角闪现：containerSize 首帧为
                    // Zero 时 maxX=0、球会先画在左上角再跳位；entrance 为 0 即不可见，
                    // 尺寸就绪后从原位放大出现——从虚拟按钮切到球时即「从小到大」动画
                    .graphicsLayer {
                        scaleX = ballEntrance.value
                        scaleY = ballEntrance.value
                    }
                    .pointerInput(maxX, maxY, swipeActions) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            ballPressed = true
                            // 打断飞行中的吸附动画：否则吸附循环继续改 offsetXFraction，
                            // 与本次拖球/滑动手势拉扯同一个位置 state
                            snapJob?.cancel()
                            snapJob = null
                            lastTouchMs = System.currentTimeMillis()
                            val startXFraction = offsetXFraction
                            val startYFraction = offsetYFraction
                            // 用平台 touchSlop 而不是固定 5dp：长按判定窗口里手指的自然抖动
                            // 常会超过 5dp，那会把「想长按拖动」误判成「滑动展开菜单」
                            val moveThresholdPx = viewConfiguration.touchSlop
                            val longPressMs = 300L

                            var released = false
                            var dragged = false

                            // 300ms 判定窗口：窗口内既没抬手、也没移动超阈值，就是长按。
                            //
                            // 必须用 AwaitPointerEventScope 自己的 withTimeoutOrNull：它超时抛的是
                            // PointerEventTimeoutCancellationException，和 kotlinx 的
                            // TimeoutCancellationException 不是同一个类，kotlinx 那个捕不到它，
                            // 异常会冒泡出去把整个手势循环打断。
                            //
                            // 也不能靠比较事件时间戳来判长按：手指按住不动时系统不派发事件，
                            // awaitPointerEvent() 不返回，时间比较永远轮不到执行。
                            val settled = withTimeoutOrNull(longPressMs) {
                                var moved = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    // 帧首抢占判定（必须在自身 consume 之前读 isConsumed）：
                                    // 系统手势抢占帧的 pressed 状态不可靠（可能仍为 true），
                                    // 被抢占一律按取消处理：released/dragged 均不置位，自然空转收场
                                    if (change.isConsumed) break
                                    if (!change.pressed) {
                                        released = true
                                        break
                                    }
                                    moved = (change.position - down.position).getDistance()
                                    if (moved > moveThresholdPx) {
                                        dragged = true
                                        break
                                    }
                                }
                                true
                            }

                            if (settled == null) {
                                // 窗口内一直没动 → 长按成立，立刻震动
                                haptic.longPress()
                                var totalX = 0f
                                var totalY = 0f
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        change.consume()
                                        if (!change.pressed) break
                                        // 只能用「单帧增量」累加。change.position 是相对本节点自身的坐标，
                                        // 球一移动、同一屏幕点在这个坐标系里的值就跟着变，
                                        // 若拿它去减按下点，等于把球的位移又减了一遍（负反馈）
                                        // → 球几乎不动、且来回抖动
                                        totalX += change.position.x - change.previousPosition.x
                                        totalY += change.position.y - change.previousPosition.y
                                        val nextX = (maxX.toPx() * startXFraction + totalX)
                                            .coerceIn(0f, maxX.toPx())
                                        val nextY = (maxY.toPx() * startYFraction + totalY)
                                            .coerceIn(0f, maxY.toPx())
                                        offsetXFraction = if (maxX > 0.dp) nextX / maxX.toPx() else 0f
                                        offsetYFraction = if (maxY > 0.dp) nextY / maxY.toPx() else 0f
                                    }
                                } finally {
                                    // 协程被取消（旋屏、排序变更致 pointerInput 重启）时
                                    // return 前的代码不会执行，必须用 finally 复位，
                                    // 否则球卡在 0.88 缩放直到下次触摸
                                    ballPressed = false
                                    // 拖球松手：吸附到最近的左/右屏边（300ms decel，y 不吸附）；
                                    // 开关可关；吸附动画期间再次按下会由新手势自然打断
                                    snapJob?.cancel()
                                    snapJob = scope.launch { snapToEdge() }
                                }
                                return@awaitEachGesture
                            }

                            if (released) {
                                // 窗口内抬手 = 单击，展开完整菜单
                                haptic.contextClick()
                                showClickMenu = true
                                // 选单与径向球串互斥：单击展开选单时强制收起球串
                                swipeMenuVisible = false
                                scope.launch { itemProgress.forEach { it.snapTo(0f) } }
                                ballPressed = false
                                return@awaitEachGesture
                            }

                            // 没有可滑动项时滑动没有意义，直接放弃这次手势，
                            // 免得手指划过球体却弹出一个空菜单。
                            // 不设方向门槛：拖球已由 300ms 长按门独立区分（快滑拖不动球），
                            // 门槛取的是「越过 touchSlop 那一帧」的瞬时方向，手指落屏的
                            // 自然漂移会让该方向任意偏转 → 「有时滑了菜单就是不出」。
                            // 朝屏外的快滑最多是选中 -1、松手不执行，无副作用。
                            if (!dragged || swipeActions.isEmpty()) {
                                ballPressed = false
                                return@awaitEachGesture
                            }

                            swipeMenuVisible = true
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    // 帧首抢占判定（必须在自身 consume 之前读 isConsumed）：
                                    // 系统手势抢占（另一指触发导航/通知栏）后事件流可能就此中断，
                                    // pressed 状态又不可靠 —— 只要在被管线标记 consumed 的帧上，
                                    // 一律按取消处理：收菜单、不执行、不震动
                                    if (change.isConsumed) break

                                    if (!change.pressed) {
                                        // 正常松手：执行选中项（震动挪到这里，滑动途中不震）
                                        if (selectedIndex >= 0) {
                                            haptic.contextClick()
                                            val action = swipeActions[selectedIndex]
                                            // 密码项走球旁的二级弹层（与旧版一致）；perform 对
                                            // PASSWORD_INPUT 是空操作，直接分发会静默吞掉
                                            if (
                                                action == PasswordMenuAction &&
                                                passwordChildren != null
                                            ) {
                                                showPasswordPopup = true
                                            } else {
                                                scope.launch { onAction(action) }
                                            }
                                        }
                                        change.consume()
                                        break
                                    }
                                    change.consume()

                                    // 径向命中：与球心距离 ≤ 0.6×球径即选中（覆盖 1.155 选中放大态）。
                                    // 球心位置由 miuiRadialOffsetsPx 给出（含贴边兜底平移），
                                    // 与渲染同一个函数、同一套参数，保证命中判定与实际球位始终一致。
                                    // 返回的偏移是「相对主球球心」，换算到本节点坐标系要加回半宽。
                                    val ballSizePx = ballSize.toPx()
                                    val halfBall = ballSizePx / 2f
                                    // 滑动期间球不动，屏幕位置即按下时刻的位置（含 7dp 边距基准）
                                    val ballXPx = (MIUI_BALL_EDGE_MARGIN + maxX * startXFraction).toPx()
                                    val ballYPx = (MIUI_BALL_EDGE_MARGIN + maxY * startYFraction).toPx()

                                    val radialOffsets = miuiRadialOffsetsPx(
                                        count = swipeActions.size,
                                        ballSizePx = ballSizePx,
                                        containerWidthPx = containerWidthDp.toPx(),
                                        containerHeightPx = containerHeightDp.toPx(),
                                        ballLeftPx = ballXPx,
                                        ballTopPx = ballYPx,
                                        edgeInsetPx = MIUI_BALL_EDGE_MARGIN.toPx(),
                                    ).offsets
                                    val finger = change.position
                                    var nextIndex = -1
                                    for (index in swipeActions.indices) {
                                        val center = radialOffsets[index]
                                        val cx = center.x + halfBall
                                        val cy = center.y + halfBall
                                        if ((finger - Offset(cx, cy)).getDistance() <= ballSizePx * 0.6f) {
                                            nextIndex = index
                                            break
                                        }
                                    }
                                    if (nextIndex != selectedIndex) {
                                        selectedIndex = nextIndex
                                    }
                                }
                            } finally {
                                // 手势被取消时（旋转屏幕、排序变更导致 pointerInput 重启）
                                // 也要收掉菜单，否则屏幕上会留下一个没人处理的幽灵菜单
                                swipeMenuVisible = false
                                selectedIndex = -1
                                ballPressed = false
                                lastTouchMs = System.currentTimeMillis()
                            }
                        }
                    },

            ) {
                // 绘制与手势分离：单击/长按/滑动全部由上面的 awaitEachGesture 判定，
                // 这里不再挂 onClick，否则同一次抬手会被两条路径各处理一遍
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 边角扇形模式下主球缩放淡出（与子球同判定），收起菜单时恢复；
                            // 无操作 3s 后渐隐（idleDim 倍数 × 自身 alpha），触摸立即恢复
                            scaleX = ballScale * ballHide
                            scaleY = ballScale * ballHide
                            alpha = ballHide * idleDim
                        }
                        .clip(CircleShape)
                        // 背景色对齐 MIUI CircleColorDrawable normal 态：#2B2E33（深灰，非纯黑），
                        // alpha 由用户滑块控制（MIUI 默认 #b3 ≈ 70%）
                        .background(Color(0xFF2B2E33).copy(alpha = backgroundAlpha)),
                    contentAlignment = Alignment.Center,
                ) {
                    // 白环：展开时围绕球心缩没，给白 X 让位
                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .graphicsLayer {
                                scaleX = ringScale.value
                                scaleY = ringScale.value
                            }
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
                    // 白 X：对齐 MIUI vector_cross_large —— 从中心向四端绘入（200ms decelMore），
                    // 同时从 -225° 转正；端点在球心 ±5%（50dp 球即半盒 5dp），描边 2.4%
                    if (xProgress.value > 0.01f) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = -225f * (1f - xProgress.value)
                                    alpha = xProgress.value
                                },
                        ) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            // X 符号整体宽度 = 球显示直径 x 0.20（端点每边 +10%）；
                            // 描边 = 球径 x 0.024（原版实测 bbox 占球径 19~21%、描边 1.2dp/50dp）
                            val halfBox = size.width * 0.10f
                            val stroke = size.width * 0.024f
                            listOf(
                                Offset(-halfBox, -halfBox),
                                Offset(halfBox, -halfBox),
                                Offset(-halfBox, halfBox),
                                Offset(halfBox, halfBox),
                            ).forEach { off ->
                                drawLine(
                                    color = Color.White.copy(alpha = ringAlpha),
                                    start = Offset(cx, cy),
                                    end = Offset(
                                        cx + off.x * xProgress.value,
                                        cy + off.y * xProgress.value,
                                    ),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                    }
                }

                // 单击菜单挂在球这一层, 以「所在节点」为锚点定位, 挂到外层容器上会被
                // 当成整屏锚点, 结果弹到屏幕底部而不是贴着球; 密码项在菜单内二级展开
                ActionCascadingPopup(
                    show = showClickMenu,
                    entries = rememberMenuEntries(clickActions, passwordChildren) { action ->
                        scope.launch { onAction(action) }
                        showClickMenu = false
                        lastTouchMs = System.currentTimeMillis()
                    },
                    onDismissRequest = {
                        showClickMenu = false
                        lastTouchMs = System.currentTimeMillis()
                    },
                    alignment = popupAlignment,
                    renderInRootScaffold = false,
                )

                // 滑出菜单松手选中密码项: 弹层同样锚在球上 (与旧版一致), 内容就是
                // 单击菜单里密码项展开的同一份二级列表; 点选后 miuix 自动收起
                val passwordPopupChildren = passwordChildren
                if (showPasswordPopup && passwordPopupChildren != null) {
                    ActionCascadingPopup(
                        show = true,
                        entries = remember(passwordPopupChildren) {
                            passwordEntry(passwordPopupChildren)
                        },
                        onDismissRequest = {
                            showPasswordPopup = false
                            lastTouchMs = System.currentTimeMillis()
                        },
                        alignment = popupAlignment,
                        renderInRootScaffold = false,
                    )
                }
            }
        }
    }

    @Composable
    private fun FloatingBallButton(
        ballSize: Dp,
        backgroundAlpha: Float,
        ringAlpha: Float,
        onClick: () -> Unit,
    ) {
        val haptic = LocalHapticFeedback.current
        val ringSize = ballSize / 2
        val ringWidth = ballSize / 24
        Button(
            modifier = Modifier.fillMaxSize(),
            onClick = {
                haptic.contextClick()
                onClick()
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
    }

    /**
     * 级联菜单弹层: 一级是动作列表, 「填充锁屏密码」展开后是二级的密码列表
     */
    @Composable
    private fun ActionCascadingPopup(
        show: Boolean,
        entries: List<DropdownEntry>,
        onDismissRequest: () -> Unit,
        alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.TopEnd,
        renderInRootScaffold: Boolean = true,
        popupBottomPadding: Dp = 0.dp,
    ) {
        OverlayCascadingListPopup(
            show = show,
            entries = entries,
            onDismissRequest = onDismissRequest,
            popupPositionProvider =
                if (popupBottomPadding > 0.dp) {
                    rememberBottomSafeContextMenuPositionProvider(popupBottomPadding)
                } else {
                    ListPopupDefaults.ContextMenuPositionProvider
                },
            alignment = alignment,
            renderInRootScaffold = renderInRootScaffold,
            enableWindowDim = false,
        )
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
