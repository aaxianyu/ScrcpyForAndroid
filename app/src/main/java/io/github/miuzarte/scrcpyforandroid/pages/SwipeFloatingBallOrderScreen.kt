package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.ReorderableList
import io.github.miuzarte.scrcpyforandroid.scaffolds.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.widgets.MIUI_RADIAL_MAX
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

@Composable
internal fun SwipeFloatingBallOrderScreen(
    scrollBehavior: ScrollBehavior,
) {
    val haptic = LocalHapticFeedback.current
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
            SwipeFloatingBallOrderPage(
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
            )
        }
    }
}

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

    var menuItems by remember(asBundle.swipeFloatingBallActionsLayout) {
        mutableStateOf(
            VirtualButtonActions.parseFloatingBallLayout(asBundle.swipeFloatingBallActionsLayout),
        )
    }
    val swipeCheckedCount = menuItems.count { it.visible }

    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        bottomInnerPadding = UiSpacing.PageBottom,
    ) {
        item {
            // 标题与内容同处一个 item：列表的 12dp itemSpacing 只作用于 item 之间，
            // 配合标题 8dp 上内距，卡底到下一个标题为 20dp
            // MIUI 形态固定为径向球串，行为开关须排在说明与列表前面
            SectionSmallTitle(stringResource(R.string.sfb_group_display))
            Card {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.sfb_order_snap_title),
                        summary = stringResource(R.string.sfb_order_snap_summary),
                        checked = asBundle.miuiSnapEnabled,
                        onCheckedChange = {
                            asBundle = asBundle.copy(miuiSnapEnabled = it)
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.sfb_order_idle_dim_title),
                        summary = stringResource(R.string.sfb_order_idle_dim_summary),
                        checked = asBundle.miuiIdleDimEnabled,
                        onCheckedChange = {
                            asBundle = asBundle.copy(miuiIdleDimEnabled = it)
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.sfb_order_action_hint_title),
                        summary = stringResource(R.string.sfb_order_action_hint_summary),
                        checked = asBundle.miuiMenuActionHint,
                        onCheckedChange = {
                            asBundle = asBundle.copy(miuiMenuActionHint = it)
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.sfb_order_expand_anim_title),
                        summary = stringResource(R.string.sfb_order_expand_anim_summary),
                        checked = asBundle.miuiExpandAnimationEnabled,
                        onCheckedChange = {
                            asBundle = asBundle.copy(miuiExpandAnimationEnabled = it)
                        },
                    )
                }
            }
        }

        item {
            SectionSmallTitle(stringResource(R.string.sfb_group_menu))
            // 左右 16dp 让说明与卡内条目文字左缘对齐（同为 28dp），
            // top 4dp 接标题 8dp 下内距成 12dp，bottom 8dp 留出到列表的距离
            Text(
                text = stringResource(R.string.pref_summary_swipe_floating_ball_sort),
                style = textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(
                    start = UiSpacing.Large,
                    end = UiSpacing.Large,
                    top = UiSpacing.Small,
                    bottom = UiSpacing.Medium,
                ),
            )
            val textSwipeVisible = stringResource(R.string.sfb_order_swipe_visible)
            val textClickVisible = stringResource(R.string.sfb_order_click_visible)
            val textSwipeOverflow = stringResource(R.string.sfb_order_swipe_overflow)
            ReorderableList(
                itemsProvider = {
                    menuItems.map { item ->
                        val action = item.action
                        val actionTitle = context.getString(action.titleResId)
                        ReorderableList.Item(
                            id = action.id,
                            icon = action.icon,
                            title =
                                if (action.keycode == null) actionTitle
                                else "$actionTitle (${action.keycode})",
                            subtitle = when {
                                item.visible -> textSwipeVisible
                                // 满员时未勾选项被禁勾：副标题追加说明，复选框保持灰色禁用
                                swipeCheckedCount >= MIUI_RADIAL_MAX ->
                                    "$textClickVisible · $textSwipeOverflow"
                                else -> textClickVisible
                            },
                            endActions = listOf(
                                ReorderableList.EndAction.Checkbox(
                                    checked = item.visible,
                                    // 径向球串最多 5 个：满员后未勾选项禁勾，
                                    // 已勾选的保持可操作（取消勾选腾出名额）
                                    enabled = item.visible || swipeCheckedCount < MIUI_RADIAL_MAX,
                                    onClick = {
                                        val checked = !item.visible
                                        if (checked && swipeCheckedCount >= MIUI_RADIAL_MAX) {
                                            return@Checkbox
                                        }
                                        menuItems = menuItems.map { current ->
                                            if (current.action.id == action.id)
                                                current.copy(visible = checked)
                                            else current
                                        }
                                        asBundle = asBundle.copy(
                                            swipeFloatingBallActionsLayout =
                                                VirtualButtonActions
                                                    .encodeFloatingBallLayout(menuItems),
                                        )
                                    },
                                ),
                            ),
                        )
                    }
                },
                orientation = ReorderableList.Orientation.Column,
                onSettle = { fromIndex, toIndex ->
                    menuItems = menuItems.toMutableList()
                        .apply { add(toIndex, removeAt(fromIndex)) }
                    asBundle = asBundle.copy(
                        swipeFloatingBallActionsLayout =
                            VirtualButtonActions.encodeFloatingBallLayout(menuItems),
                    )
                },
            )()
        }
    }
}