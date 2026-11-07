package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
internal fun VirtualButtonOrderScreen(
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
                    title = stringResource(R.string.vb_order_title),
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
            VirtualButtonOrderPage(
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
            )
        }
    }
}

@Composable
internal fun VirtualButtonOrderPage(
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

    var buttonItems by remember(asBundle.virtualButtonsLayout) {
        // 「切换到虚拟按钮」只在悬浮球语境下成立，本页不展示。
        // 统一把它挪到列表末尾，于是展示顺序与原列表前 n 项一一对应，
        // onSettle 拿到的索引可以直接用，省掉一轮容易出错的索引映射
        val parsed = VirtualButtonActions.parseStoredLayout(asBundle.virtualButtonsLayout)
        val shown = parsed.filterNot {
            it.action == VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL
        }
        val hidden = parsed.filter {
            it.action == VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL
        }
        mutableStateOf(shown + hidden)
    }

    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        bottomInnerPadding = UiSpacing.PageBottom,
    ) {
        item {
            Card {
                SwitchPreference(
                    title = stringResource(R.string.vb_order_button_text),
                    summary = stringResource(R.string.vb_order_hint),
                    checked = asBundle.previewVirtualButtonShowText,
                    onCheckedChange = {
                        asBundle = asBundle.copy(previewVirtualButtonShowText = it)
                    },
                )
            }
        }

        item { Spacer(Modifier.height(UiSpacing.Medium)) }

        item {
            val textExternal = stringResource(R.string.vb_order_display_external)
            val textMoreMenu = stringResource(R.string.vb_order_display_more_menu)
            ReorderableList(
                itemsProvider = {
                    // 隐藏项已在列表末尾，这里只取前 n 项即可
                    val shownItems = buttonItems.filter {
                        it.action != VirtualButtonAction.HIDE_SWIPE_FLOATING_BALL
                    }
                    shownItems.map { item ->
                        val action = item.action
                        val actionTitle = context.getString(action.titleResId)
                        ReorderableList.Item(
                            id = action.id,
                            icon = action.icon,
                            title =
                                if (action.keycode == null) actionTitle
                                else "$actionTitle (${action.keycode})",
                            subtitle =
                                if (item.showOutside) textExternal
                                else textMoreMenu,
                            endActions = listOf(
                                ReorderableList.EndAction.Checkbox(
                                    checked = item.showOutside,
                                    enabled = action != VirtualButtonAction.MORE,
                                    onClick = {
                                        val checked = !item.showOutside
                                        buttonItems = buttonItems.map { current ->
                                            if (current.action.id == action.id)
                                                current.copy(showOutside = checked)
                                            else current
                                        }
                                        asBundle = asBundle.copy(
                                            virtualButtonsLayout = VirtualButtonActions
                                                .encodeStoredLayout(buttonItems),
                                        )
                                    },
                                ),
                            ),
                        )
                    }
                },
                orientation = ReorderableList.Orientation.Column,
                onSettle = { fromIndex, toIndex ->
                    // 隐藏项排在末尾，展示索引与原列表索引一致，可直接使用
                    buttonItems = buttonItems.toMutableList()
                        .apply { add(toIndex, removeAt(fromIndex)) }
                    asBundle = asBundle.copy(
                        virtualButtonsLayout = VirtualButtonActions
                            .encodeStoredLayout(buttonItems),
                    )
                },
            )()
        }
    }
}
