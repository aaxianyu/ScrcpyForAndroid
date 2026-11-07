package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.data.DeviceAppRepository
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppSearch
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.LocalApp
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.SizeFormat
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.ui.ImeSafeInputField
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.longPress
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

/**
 * 选择器行内容最小高度：三行模式（11sp×3 + 行距 2dp×2 ≈ 45dp）撑满后的
 * 统一值，两行模式也占同一高度，切换第三行显隐时行高不跳。
 */
private val LocalPickerRowMinHeight = 50.dp

/**
 * 本机应用选择器。
 *
 * - 组件库标准搜索栏（含框内 × 清除钮与可用的「取消」钮，触摸区 ≥ 48dp）；
 * - 只列本机用户安装的应用，排序与设备侧应用管理页同一把尺子
 *   （[AppOrdering.sortedLocal]：拼音首字母 + 英文首字母混排）；搜索匹配前修剪首尾空白；
 * - 每行 = 名字 / 包名 / （版本+APK 大小），三行机制与设备侧列表完全一致，共用组件
 *   （[PackageNameSizeRow] / [VersionSizeLine]）；三行出现时字号适当缩小；
 * - 列表区域限高 400dp，支持下拉刷新（中文四项刷新文案）；
 * - 主动关闭 → 搜索词与选择集均清空；被二次确认对话框挤占 → 均保留；Activity 重建 →
 *   均保留但选择器本体关闭（由调用方与 ViewModel 共同保证）。
 */
@Composable
fun LocalAppPickerSheet(
    visible: Boolean,
    apps: List<LocalApp>,
    loading: Boolean,
    permissionMissing: Boolean,
    /** 本机枚举超时未回：转圈换明确提示 + 重试。后台那次枚举仍在跑，回来会覆盖。 */
    slow: Boolean,
    query: String,
    selectedPackages: Set<String>,
    busy: Boolean,
    loaded: Boolean,
    /** 多选态：由 ViewModel 持有，被安装确认对话框挤占时不清零，仅主动关闭时清空。 */
    batchMode: Boolean,
    onBatchModeChange: (Boolean) -> Unit,
    /** 本机图标代次：预热完成后 +1，行按它重取内存缓存里的图标。 */
    iconEpoch: Int,
    onQueryChange: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    onGoToSettings: () -> Unit,
    /** 隐藏版本 / 大小两个开关，与应用管理页共用，由外部（ViewModel 持久化）控制。 */
    hideVersion: Boolean,
    hideApkSize: Boolean,
    onToggleHideVersion: () -> Unit,
    onToggleHideApkSize: () -> Unit,
    /** 多选态点了 Close（退出批量选择）时清掉整个选择集。 */
    onClearSelection: () -> Unit = {},
    /** 非多选态单击某个应用：直接弹安装确认弹窗。 */
    onInstallSingleApp: (LocalApp) -> Unit = {},
) {
    var searchActive by remember { mutableStateOf(false) }
    var tuneOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // 这两个输入态挂在面板内容之外，面板关掉不会自己消失，所以开与关都要重置：
    // 关时不清，下次打开还处于展开 / 聚焦（输入法先弹再被收起）；开时不清，上次的「隐藏」选单继续浮着
    LaunchedEffect(visible) {
        searchActive = false
        tuneOpen = false
        if (!visible) focusManager.clearFocus()
    }
    // 进多选态时收掉「隐藏」选单：它的锚点 Box（Tune 钮）在多选态整体退场，
    // 浮层留着就会在退出多选后带着 visible = true 自行弹开
    LaunchedEffect(batchMode) { if (batchMode) tuneOpen = false }
    val normalizedQuery = query.trim()
    val haptic = LocalHapticFeedback.current
    val filtered = remember(apps, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.contains(normalizedQuery, ignoreCase = true) ||
                    it.packageName.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    OverlayBottomSheet(
        show = visible,
        title = when {
            // 多选态三态：有选中报数 / 空选提示选择 / 平时显示页标题（与应用管理页一致）
            batchMode && selectedPackages.isNotEmpty() ->
                stringResource(R.string.app_manager_selected, selectedPackages.size)

            batchMode -> stringResource(R.string.app_manager_select_prompt)
            else -> stringResource(R.string.app_manager_local_picker_title)
        },
        startAction = {
            IconButton(
                onClick = {
                    // 多选态时左上是「退出多选」（应用管理页同款 Close 语义），平时关面板
                    if (batchMode) {
                        onBatchModeChange(false)
                        onClearSelection()
                        haptic.contextClick()
                    } else {
                        haptic.contextClick()
                        onDismissRequest()
                    }
                },
                // 与设备信息面板同一处光学校正，见 UiSpacing.SheetHeaderIconOpticalOffset
                modifier = Modifier.offset(y = UiSpacing.SheetHeaderIconOpticalOffset),
            ) {
                // 未勾选时就是普通的 Back 箭头；勾选中才出现 Close（退出多选）
                Icon(
                    imageVector = if (batchMode) MiuixIcons.Basic.Close else MiuixIcons.Back,
                    contentDescription = stringResource(
                        if (batchMode) R.string.cd_close else R.string.cd_back,
                    ),
                )
            }
        },
        endAction = {
            val batchCanInstall = selectedPackages.isNotEmpty() && !busy
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (batchMode) {
                    // 多选态：tune 退场，全选顶到它原来的位置；行尾仍是 Ok（选中为空时禁用）
                    val allSelected = filtered.isNotEmpty() && filtered.all { it.packageName in selectedPackages }
                    IconButton(
                        onClick = {
                            haptic.contextClick()
                            if (allSelected) {
                                filtered.forEach {
                                    if (it.packageName in selectedPackages) onToggleSelect(it.packageName)
                                }
                            } else {
                                filtered.forEach {
                                    if (it.packageName !in selectedPackages) onToggleSelect(it.packageName)
                                }
                            }
                        },
                        enabled = filtered.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.SelectAll,
                            contentDescription = stringResource(
                                if (allSelected) R.string.cd_clear_selection else R.string.cd_select_all,
                            ),
                        )
                    }
                    IconButton(
                        onClick = { if (batchCanInstall) onConfirm() },
                        enabled = batchCanInstall,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = stringResource(R.string.app_manager_local_install_n, selectedPackages.size),
                            // 可执行时不强行蓝色，跟其它顶栏图标一个基调；禁用时置灰（空选 / 忙碌）
                            tint = if (batchCanInstall) {
                                LocalContentColor.current
                            } else {
                                colorScheme.disabledOnSurface
                            },
                        )
                    }
                } else {
                    // 非多选态：Ok 不出现，只剩「隐藏」选单 → 它落在行内最右，即原来 Ok 的位置
                    Box {
                        IconButton(
                            onClick = {
                                haptic.contextClick()
                                tuneOpen = true
                            },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Tune,
                                contentDescription = stringResource(R.string.app_manager_display_options),
                            )
                        }
                        DisplayOptionsMenu(
                            visible = tuneOpen,
                            hideVersion = hideVersion,
                            hideApkSize = hideApkSize,
                            onToggleHideVersion = onToggleHideVersion,
                            onToggleHideApkSize = onToggleHideApkSize,
                            onDismissRequest = { tuneOpen = false },
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
    ) {
        // 多选态下返回先退出多选（与设备侧列表同一套返回优先级），面板留着：
        // 长按只是进了选择状态，误触返回不该顺手把整个列表关掉。
        // 必须写在 OverlayBottomSheet 内容里：注册在外层时，返回键仍走面板自己的 onDismissRequest
        BackHandler(enabled = batchMode) {
            onBatchModeChange(false)
            onClearSelection()
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // IME 安全输入框：TextFieldValue 托管，中文组词顺序稳定；
                // 样式与应用管理页 SearchOverlay 同一套胶囊美学（IconField 风格 + 前置搜索图标 + 框尾清空钮）
                ImeSafeInputField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = stringResource(R.string.app_manager_search_hint),
                    color = colorScheme.surfaceContainerHighest,
                    leadingIcon = {
                        Icon(
                            modifier = Modifier,
                            imageVector = MiuixIcons.Basic.Search,
                            tint = colorScheme.onSurfaceContainerHigh,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        // 有文本才出现框尾清空钮（同 miuix InputField 的 SearchCleanup 交互）
                        androidx.compose.animation.AnimatedVisibility(
                            visible = query.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Icon(
                                modifier = Modifier.clickable { onQueryChange("") },
                                imageVector = MiuixIcons.Basic.SearchCleanup,
                                tint = colorScheme.onSurfaceContainerHighest,
                                contentDescription = null,
                            )
                        }
                    },
                    onFocusChanged = { focused ->
                        // 聚焦搜索框 = 进入输入态：才浮出「取消」按钮（非默认状态）
                        if (focused) searchActive = true
                    },
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = searchActive,
                    enter = androidx.compose.animation.expandHorizontally() +
                        androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }) +
                        fadeIn(),
                    exit = androidx.compose.animation.shrinkHorizontally() +
                        androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }) +
                        fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            // 与搜索框高度同高（InputFieldMinHeight = 45dp），否则「取消」浮出
                            // / 收起时会把搜索行整体撑高/降高，让下面的列表头跳动
                            .height(SearchBarDefaults.InputFieldMinHeight)
                            .widthIn(min = 48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onQueryChange("")
                                searchActive = false
                                focusManager.clearFocus()
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.app_manager_search_cancel),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                // 权限缺失优先于一切空态
                permissionMissing -> PermissionHint(onGoToSettings)
                // 枚举超时未回：转圈换明确提示。放 loaded 判定之前——超时通常发生在
                // 首次加载，那时 loaded 还是 false，会被上面那个转圈分支吃掉
                slow && !loading -> LocalPickerSlow(onRetry = onRefresh)
                // 首次加载（还没成功加载过）或刷新中且列表为空：只出转圈，不显示空态
                !loaded || (loading && apps.isEmpty()) -> CenteredLoading(
                    stringResource(R.string.app_manager_loading),
                )

                filtered.isEmpty() -> LocalPickerEmpty(apps.isEmpty())
                else -> LocalAppList(
                    apps = filtered,
                    selectedPackages = selectedPackages,
                    loading = loading,
                    batchMode = batchMode,
                    showVersion = !hideVersion,
                    showApkSize = !hideApkSize,
                    iconEpoch = iconEpoch,
                    onToggleSelect = onToggleSelect,
                    onInstallSingleApp = onInstallSingleApp,
                    onEnterBatchMode = { onBatchModeChange(true) },
                    onRefresh = onRefresh,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LocalAppList(
    apps: List<LocalApp>,
    selectedPackages: Set<String>,
    loading: Boolean,
    batchMode: Boolean,
    showVersion: Boolean,
    showApkSize: Boolean,
    iconEpoch: Int,
    onToggleSelect: (String) -> Unit,
    onInstallSingleApp: (LocalApp) -> Unit,
    onEnterBatchMode: () -> Unit,
    onRefresh: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    // 列表区限高 400dp：下拉刷新的刷新头在这个高度内增长，把列表往下推；
    // 若不限高，头部增高会撑大整个 BottomSheet 内容——表现为「整个浮层连标题一起升起」
    // （PullToRefresh 内部是 Column { 刷新头; 内容 }，头部长高就往下挤内容）
    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
        PullToRefresh(
            // isRefreshing 不接数据层，理由与设备侧列表相同（见 AppManagerListPage）：
            // 组件库的 Refreshing 态会把回弹拆成「保持展开 → 收起」两段，速度非单调回升，
            // 看起来就是跳一下再跳一下。本机枚举很快，进度没有单独呈现的价值
            isRefreshing = false,
            onRefresh = onRefresh,
            pullToRefreshState = pullToRefreshState,
            refreshTexts = listOf(
                stringResource(R.string.app_manager_pull_to_refresh),
                stringResource(R.string.app_manager_release_to_refresh),
                stringResource(R.string.app_manager_refreshing),
                stringResource(R.string.app_manager_refresh_done),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(apps.size) { index ->
                    val app = apps[index]
                    val selected = app.packageName in selectedPackages
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    // 只在勾选态铺底色，用 secondaryContainer——本 App 里「让卡片从弹层底浮起来」的约定用法
                    // （见 CommandBookmarkSheet / UtilityToolsScreen 里的弹层卡）。别改用
                    // CardDefaults.defaultColors() 的默认值：它取 surfaceContainer，静态配色下与弹层底同值，
                    // 勾选态会彻底隐形。静止态不铺底色；primary 淡色则会把勾选态画成蓝色，不是卡片底色的观感
                    val rowColor = when {
                        selected -> colorScheme.secondaryContainer
                        pressed -> colorScheme.onSurface.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(rowColor)
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    haptic.contextClick()
                                    if (batchMode) {
                                        onToggleSelect(app.packageName)
                                    } else {
                                        onInstallSingleApp(app)
                                    }
                                },
                                onLongClick = {
                                    haptic.longPress()
                                    if (!batchMode) onEnterBatchMode()
                                    if (app.packageName !in selectedPackages) {
                                        onToggleSelect(app.packageName)
                                    }
                                },
                            )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LocalAppIcon(packageName = app.packageName, epoch = iconEpoch)
                Spacer(Modifier.width(12.dp))
                // 末尾留 8dp：包名 / 体积那一行是撑满这一格的，不留距会直接贴上复选框
                // 名称 + 包名 + (版本+大小的第三行)
                val showVersionLine = showVersion && app.versionName.isNotBlank()
                val showSizeLine = showApkSize && app.apkSizeBytes > 0
                Column(
                    // 行高稳定：内容几行在「切换第三行显隐」时
                    // 会伸缩，直接导致整块 Row / 列表头跳变。把内容最小高度撑到三行状态的水平，两行
                    // 模式也占同样的高度，视觉上只切换字号 / 可见性
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .heightIn(min = LocalPickerRowMinHeight),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (!showVersionLine && !showSizeLine) {
                        // 两项信息全被隐藏：保留两行排版，行高不变
                        Text(
                            text = app.label,
                            style = textStyles.body1,
                            color = colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PackageNameSizeRow(
                            leadingText = app.packageName,
                            sizeText = null,
                        )
                    } else {
                        // 三行时每行字号适当缩小
                        Text(
                            text = app.label,
                            style = textStyles.body2,
                            color = colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PackageNameSizeRow(
                            leadingText = app.packageName,
                            sizeText = null,
                            compact = true,
                        )
                        VersionSizeLine(
                            version = if (showVersionLine) {
                                stringResource(R.string.app_manager_version_format, app.versionName)
                            } else {
                                null
                            },
                            sizeText = if (showSizeLine) {
                                val size = SizeFormat.humanReadable(app.apkSizeBytes)
                                // 分包标识跟着大小那一格：大小被隐藏时它没有落脚处
                                if (app.apkPaths.size > 1) size + "·" + stringResource(R.string.app_manager_split_badge) else size
                            } else {
                                null
                            },
                            compact = true,
                        )
                    }
                }
                // 只在多选态展示勾选框；非多选态点行直接弹安装确认
                if (batchMode) {
                    Checkbox(
                        state = if (selected) ToggleableState.On else ToggleableState.Off,
                        onClick = { onToggleSelect(app.packageName) },
                    )
                }
                }
            }
            }
        }
    }
}

/**
 * 选择器空态：没有找到应用 + 副文案。 */
@Composable
private fun LocalPickerEmpty(noAppsAtAll: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.app_manager_no_apps),
                style = textStyles.body1,
                color = colorScheme.onSurface,
            )
            if (noAppsAtAll) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.app_manager_no_apps_subtitle),
                    style = textStyles.footnote1,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/**
 * 本机枚举超时未回：标题 + 副文案 + 重试按钮。
 *
 * 命名 state + retry：后台那次枚举不取消（同步 Binder 调用，取消唤不醒），所以重试的动作
 * 是「再发起一次」。
 */
@Composable
private fun LocalPickerSlow(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.app_manager_local_load_timeout),
                style = textStyles.body1,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = stringResource(R.string.app_manager_retry),
                onClick = onRetry,
            )
        }
    }
}

/** 本机应用图标：直接取自本机系统；非位图图标手工渲染成位图。 */
@Composable
private fun LocalAppIcon(packageName: String, epoch: Int) {
    // 只读内存缓存：图标读取是跨进程 Binder 调用，由 [AppManagerViewModel]
    // 在 IO 线程预热进 LRU；代次变化（预热完成）时按 remember 键重取，
    // 未命中先出占位图。组合里不做任何跨进程调用。
    val icon = remember(packageName, epoch) { DeviceAppRepository.localIcon(packageName) }
    // 垫色只给占位图标用：surfaceContainerHighest 与行底本就不同色（静态下浅 #E8E8E8 / 深 #2D2D2D），
    // 莫奈配色把差值放大后更显眼。adaptive 图标的位图四角是透明的，垫色会顺着图标轮廓透出一圈色边，
    // 所以图标到位后不垫任何底色
    val plateColor = if (icon == null) colorScheme.surfaceContainerHighest else Color.Transparent
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(plateColor),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Phone,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun CenteredLoading(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(size = 32.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                style = textStyles.body1,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun PermissionHint(onGoToSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_manager_local_permission_hint),
            style = textStyles.body1,
            color = colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(text = stringResource(R.string.app_manager_local_go_to_settings), onClick = onGoToSettings)
    }
}
