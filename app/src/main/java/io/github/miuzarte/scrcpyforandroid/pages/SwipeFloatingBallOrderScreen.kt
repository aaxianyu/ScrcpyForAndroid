package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.ReorderableList
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
internal fun SwipeFloatingBallOrderScreen(
    scrollBehavior: ScrollBehavior,
) {
    val navigator = LocalRootNavigator.current
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = stringResource(R.string.sfb_order_title),
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = navigator.pop) {
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
        Box(modifier = if (blurActive) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
            SwipeFloatingBallOrderPage(
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
            )
        }
    }
}

/**
 * 滑动悬浮球菜单项排序页面
 * 不包含show/hide悬浮球本身和more按钮，只排序实际功能菜单项
 */
@Composable
internal fun SwipeFloatingBallOrderPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    val context = LocalContext.current
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

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

    // 解析当前存储的滑动悬浮球菜单项排序（含显示/隐藏状态）
    var actionItems by remember(asBundle.swipeFloatingBallActionsLayout) {
        mutableStateOf(parseSwipeFloatingBallLayout(asBundle.swipeFloatingBallActionsLayout))
    }

    val textSwipeVisible = stringResource(R.string.sfb_order_swipe_visible)
    val textClickVisible = stringResource(R.string.sfb_order_click_visible)

    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        bottomInnerPadding = UiSpacing.PageBottom,
    ) {
        item {
            Card {
                Text(
                    text = stringResource(R.string.pref_summary_swipe_floating_ball_sort),
                    modifier = Modifier.padding(UiSpacing.Large),
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        item { Spacer(Modifier.height(UiSpacing.Medium)) }

        item {
            ReorderableList(
                itemsProvider = {
                    actionItems.map { item ->
                        val action = item.action
                        val actionTitle = context.getString(action.titleResId)
                        ReorderableList.Item(
                            id = action.id,
                            icon = action.icon,
                            title =
                                if (action.keycode == null) actionTitle
                                else "$actionTitle (${action.keycode})",
                            subtitle =
                                if (item.visible) textSwipeVisible
                                else textClickVisible,
                            endActions = listOf(
                                ReorderableList.EndAction.Checkbox(
                                    checked = item.visible,
                                    enabled = true,
                                    onClick = {
                                        val checked = !item.visible
                                        actionItems = actionItems.map { current ->
                                            if (current.action.id == action.id)
                                                current.copy(visible = checked)
                                            else current
                                        }
                                        asBundle = asBundle.copy(
                                            swipeFloatingBallActionsLayout = encodeSwipeFloatingBallLayout(actionItems)
                                        )
                                    },
                                )
                            ),
                        )
                    }
                },
                orientation = ReorderableList.Orientation.Column,
                onSettle = { fromIndex, toIndex ->
                    actionItems = actionItems.toMutableList()
                        .apply { add(toIndex, removeAt(fromIndex)) }
                    asBundle = asBundle.copy(
                        swipeFloatingBallActionsLayout = encodeSwipeFloatingBallLayout(actionItems)
                    )
                },
            )()
        }
    }
}

/**
 * 滑动悬浮球菜单项数据（含显示/隐藏状态）
 */
data class SwipeFloatingBallActionItem(
    val action: VirtualButtonAction,
    val visible: Boolean,
)

/**
 * 解析存储的滑动悬浮球菜单项排序字符串
 * 格式："home:1,app_switch:1,back:0,password_input:1,..."
 */
private fun parseSwipeFloatingBallLayout(raw: String): List<SwipeFloatingBallActionItem> {
    val byId = VirtualButtonAction.entries.associateBy { it.id }
    val parsed = raw.takeIf { it.isNotBlank() }
        ?.split(',')
        ?.mapNotNull { item ->
            val parts = item.trim().split(':')
            if (parts.size != 2) return@mapNotNull null
            val id = parts[0]
            val visible = parts[1] == "1"
            val action = byId[id]?.takeIf {
                // 排除show/hide悬浮球和more按钮
                it != VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL &&
                it != VirtualButtonAction.MORE
            } ?: return@mapNotNull null
            SwipeFloatingBallActionItem(action, visible)
        }
        .orEmpty()
        .distinctBy { it.action.id }
    val base = parsed.ifEmpty {
        parseSwipeFloatingBallLayout(AppSettings.SWIPE_FLOATING_BALL_ACTIONS_LAYOUT.defaultValue)
    }
    // 补充缺失的项（默认显示）
    val missing = VirtualButtonAction.entries
        .filter {
            it != VirtualButtonAction.SHOW_SWIPE_FLOATING_BALL &&
            it != VirtualButtonAction.MORE
        }
        .filterNot { action -> base.any { it.action.id == action.id } }
        .map { SwipeFloatingBallActionItem(it, true) }
    return base + missing
}

/**
 * 编码滑动悬浮球菜单项排序为存储字符串
 * 格式："home:1,app_switch:1,back:0,..."
 */
private fun encodeSwipeFloatingBallLayout(items: List<SwipeFloatingBallActionItem>): String {
    return items.joinToString(",") { "${it.action.id}:${if (it.visible) "1" else "0"}" }
}
