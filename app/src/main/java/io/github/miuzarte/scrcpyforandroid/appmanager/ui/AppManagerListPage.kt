package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.data.AppListContent
import io.github.miuzarte.scrcpyforandroid.appmanager.data.AppListUiState
import io.github.miuzarte.scrcpyforandroid.appmanager.data.AppManagerError
import io.github.miuzarte.scrcpyforandroid.appmanager.data.ProbePayloadParser
import io.github.miuzarte.scrcpyforandroid.appmanager.data.isRunning
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppOrdering
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppSection
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 一个分组页（用户 / 系统）：分组列表 + 下拉刷新 + 空态 / 加载态。
 *
 * 下拉刷新用组件库 [PullToRefresh]（isRefreshing 提升到数据层），文案沿用产品四项。
 * 空态按分组各自判定：一个分组为空不影响另一个分组。
 */
@Composable
fun AppManagerListPage(
    apps: List<DeviceApp>,
    uiState: AppListUiState,
    isSystemGroup: Boolean,
    batchMode: Boolean,
    selectedPackages: Set<String>,
    showVersion: Boolean,
    showApkSize: Boolean,
    animationsDisabled: Boolean,
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    iconProvider: (String) -> ImageBitmap?,
    bottomInnerPadding: Dp,
    topInnerPadding: Dp,
    onRefresh: () -> Unit,
    onAppClick: (DeviceApp) -> Unit,
    onAppLongClick: (DeviceApp) -> Unit,
    onBackToDevices: () -> Unit,
    onListTouch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupApps by remember(apps, isSystemGroup) {
        derivedStateOf { apps.filter { it.isSystem == isSystemGroup } }
    }
    val sections = remember(groupApps) { AppOrdering.groupBySection(groupApps) }
    val content by remember(groupApps, uiState.isLoading, uiState.notConnected, uiState.error) {
        derivedStateOf {
            when {
                groupApps.isNotEmpty() -> AppListContent.List
                uiState.notConnected -> AppListContent.NotConnected
                uiState.isLoading -> AppListContent.Loading
                uiState.error != null -> AppListContent.Error
                else -> AppListContent.Empty
            }
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefresh(
        // isRefreshing 不接数据层：组件库在 Refreshing 态会消费掉全部嵌套滚动，
        // 刷新中列表就无法滑动。手势照常触发 onRefresh，刷新进度
        // 由页面顶栏标题「刷新中」呈现。
        isRefreshing = false,
        onRefresh = onRefresh,
        pullToRefreshState = pullToRefreshState,
        topAppBarScrollBehavior = scrollBehavior,
        // 刷新完成文案置空：isRefreshing 不接数据层时组件库会在半秒内走完回弹，
        // 显示「刷新完成」会误导（真实进度看顶栏「正在刷新」）
        refreshTexts = listOf(
            stringResource(R.string.app_manager_pull_to_refresh),
            stringResource(R.string.app_manager_release_to_refresh),
            stringResource(R.string.app_manager_refreshing),
            "",
        ),
        contentPadding = PaddingValues(top = topInnerPadding + 12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (content) {
                AppListContent.Loading -> CenteredState {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(size = 40.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.app_manager_loading),
                            style = textStyles.footnote1,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                AppListContent.NotConnected -> CenteredState {
                    EmptyState(
                        title = stringResource(R.string.app_manager_not_connected),
                        subtitle = stringResource(R.string.app_manager_not_connected_subtitle),
                        actionLabel = stringResource(R.string.app_manager_back_to_devices),
                        onAction = onBackToDevices,
                    )
                }

                AppListContent.Error -> CenteredState {
                    EmptyState(
                        title = errorText(uiState.error),
                        subtitle = stringResource(R.string.app_manager_no_apps_subtitle),
                        actionLabel = null,
                        onAction = null,
                    )
                }

                AppListContent.Empty -> CenteredState {
                    EmptyState(
                        title = stringResource(R.string.app_manager_no_apps),
                        subtitle = stringResource(R.string.app_manager_no_apps_subtitle),
                        actionLabel = null,
                        onAction = null,
                    )
                }

                AppListContent.List -> AppList(
                    sections = sections,
                    batchMode = batchMode,
                    selectedPackages = selectedPackages,
                    pendingPackages = uiState.pendingPackages,
                    runningPackages = uiState.runningPackages,
                    showVersion = showVersion,
                    showApkSize = showApkSize,
                    iconProvider = iconProvider,
                    iconEpoch = uiState.iconEpoch,
                    listState = listState,
                    scrollBehavior = scrollBehavior,
                    bottomInnerPadding = bottomInnerPadding,
                    topInnerPadding = topInnerPadding,
                    onAppClick = onAppClick,
                    onAppLongClick = onAppLongClick,
                    onListTouch = onListTouch,
                )
            }
        }
    }
}

@Composable
private fun AppList(
    sections: List<AppSection>,
    batchMode: Boolean,
    selectedPackages: Set<String>,
    pendingPackages: Set<String>,
    runningPackages: Set<String>?,
    showVersion: Boolean,
    showApkSize: Boolean,
    iconProvider: (String) -> ImageBitmap?,
    iconEpoch: Int,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    topInnerPadding: Dp,
    onAppClick: (DeviceApp) -> Unit,
    onAppLongClick: (DeviceApp) -> Unit,
    onListTouch: () -> Unit,
) {
    val currentOnListTouch by rememberUpdatedState(onListTouch)

    // 每节 = 字母头 item + 每行一个 item + 节末间隔 item。索引条按 AppOrdering.layoutOf()
    // 换算「节 ↔ item」下标，两边 item 顺序必须一致；要改这里先改 layoutOf
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            // 手指一碰列表就让索引条气泡收起来：那 1s 滞留只在用户没有继续操作时成立。
            // 只看按下、不消费事件，滚动仍然交给列表自己
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    currentOnListTouch()
                }
            }
            // 顶栏折叠的唯一驱动点：页面级 Scaffold 上不挂这条连接（否则弹层的滚动
            // 会经它冒泡到 ScrollBehavior、把顶栏顶起来），只剩列表这一条
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        state = listState,
        contentPadding = PaddingValues(
            start = UiSpacing.PageHorizontal,
            end = UiSpacing.PageHorizontal,
            top = topInnerPadding + UiSpacing.PageVertical,
            bottom = bottomInnerPadding,
        ),
        verticalArrangement = Arrangement.Top,
    ) {
        sections.forEach { section ->
            item(key = "header-${section.letter}", contentType = "section-header") {
                Column {
                    Text(
                        text = section.letter,
                        style = textStyles.subtitle,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 19.dp, top = 4.dp, bottom = 2.dp),
                    )
                    // 行间 0 间距；节的上下留白由两处各 8dp 拼出：字母头下方这里一块，
                    // 节末一块是 gap item（与索引条按 item 计数的节表一致）
                    Spacer(Modifier.height(AppSectionGap))
                }
            }
            // 每行单独成项：LazyColumn 只组合进入视口的行，上百个 com.* 的节滚动时
            // 也只有屏幕上那十几行参与组合
            section.apps.forEachIndexed { index, app ->
                val isFirst = index == 0
                val isLast = index == section.apps.lastIndex
                item(key = "row-${app.packageName}", contentType = "app-row") {
                    AppRow(
                        app = app,
                        showVersion = showVersion,
                        showApkSize = showApkSize,
                        batchMode = batchMode,
                        selected = app.packageName in selectedPackages,
                        pending = app.packageName in pendingPackages,
                        iconProvider = iconProvider,
                        iconEpoch = iconEpoch,
                        // 运行态未知（null）时一律 false：未知不当作运行中
                        running = runningPackages.isRunning(app.packageName),
                        onClick = { onAppClick(app) },
                        onLongClick = { onAppLongClick(app) },
                        modifier = rowSurface(isFirst, isLast, colorScheme.surfaceContainer),
                    )
                }
            }
            item(key = "gap-${section.letter}") { Spacer(Modifier.height(AppSectionGap)) }
        }
    }
}

/**
 * 一行在节里的底色与圆角。
 *
 * 圆角取 miuix 卡片的 squircle：节首与节末逐角取圆角，与卡片的观感一致；中间行四角为 0，
 * 直接填色，省掉每行一次的 shader 遮罩。
 */
@Composable
private fun rowSurface(first: Boolean, last: Boolean, color: androidx.compose.ui.graphics.Color): Modifier =
    if (first || last) {
        Modifier.squircleSurface(
            color = color,
            topStart = if (first) 16.dp else 0.dp,
            topEnd = if (first) 16.dp else 0.dp,
            bottomEnd = if (last) 16.dp else 0.dp,
            bottomStart = if (last) 16.dp else 0.dp,
        )
    } else {
        Modifier.background(color)
    }

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 空态：居中列 = 标题（正文样式）+ 副文案（脚注样式）+ 可选操作按钮（文本按钮）。 */
@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = textStyles.body1,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = textStyles.footnote1,
            color = colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun errorText(error: AppManagerError?): String = when (error) {
    is AppManagerError.ChannelOccupied -> stringResource(R.string.app_manager_adb_channel_occupied)
    is AppManagerError.Device -> localizeProbeFailure(error.reason)
    else -> stringResource(R.string.app_manager_no_apps)
}

/**
 * 探测层的失败原因是诊断串（英文），不能直接当文案显示。
 *
 * 解析层保持纯数据、只产出可判定的串；本地化在 UI 层做。截断串的格式由
 * `ProbePayloadParser.truncatedChars` 反解。未识别的串原样透传——
 * 那些是设备自己回的错误（如 `Failure [DELETE_FAILED_INTERNAL_ERROR]`），
 * 翻译不了，保留原文比换成「未知错误」更有用。
 */
@Composable
private fun localizeProbeFailure(reason: String): String {
    val chars = ProbePayloadParser.truncatedChars(reason) ?: return reason
    return stringResource(R.string.app_manager_probe_truncated, chars)
}
