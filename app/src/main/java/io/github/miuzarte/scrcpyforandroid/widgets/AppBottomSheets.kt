@file:OptIn(ExperimentalScrollBarApi::class)

package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppOrdering
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppSortKey
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.ui.ImeSafeInputField
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

data class AppListEntry(
    val key: String,
    val title: String,
    val summary: String? = null,
    val system: Boolean? = null,
    /** 收藏态：列表按它置顶显示，并切换行尾星标样式 */
    val favorite: Boolean = false,
    /** LazyColumn 的身份 key；默认 [key]。同包多任务（最近任务）需要各自的 key 时另给 */
    val itemKey: String? = null,
    val onClick: () -> Unit,
    /** 非空时行尾展示收藏星标按钮；点击切换收藏状态 */
    val onToggleFavorite: (() -> Unit)? = null,
)

/**
 * 应用清单面板（最近任务 / 全部应用）。
 *
 * 打开时按 [onFetchIcons] 补齐条目缺失的图标（结果落进共享图标缓存），按 [iconProvider]
 * 渲染行内图标；列表按「收藏优先 + 应用名」排序。[searchHint] 非空时提供搜索：搜索框与
 * 应用管理「从本机应用安装」同款（胶囊输入框 + 框尾清空钮 + 聚焦浮出「取消」），按
 * 「名称 / 包名 / 键」过滤；为 null 的面板（最近任务）不提供搜索。
 */
@Composable
fun AppListBottomSheet(
    show: Boolean,
    title: String,
    loadingText: String,
    emptyText: String,
    /** 搜索框占位文案；null = 该面板不提供搜索（最近任务） */
    searchHint: String?,
    entries: List<AppListEntry>,
    refreshBusy: Boolean,
    /** 组合阶段的图标读取：只应命中内存缓存（未命中显示占位图） */
    iconProvider: (String) -> ImageBitmap?,
    /** 异步图标解码：iconProvider 未命中时在 IO 线程读图标文件解码 */
    iconLoader: suspend (String) -> ImageBitmap?,
    onDismissRequest: () -> Unit,
    onRefresh: () -> Unit,
    /** 列表刷新版本号：变化时重新补拉缺失图标（刷新按钮同样生效） */
    refreshToken: Int = 0,
    onFetchIcons: (suspend (List<String>, onProgress: () -> Unit) -> Unit)? = null,
) {
    // 查询态用 rememberSaveable：旋转 / 进程重建后查询词与输入态不丢
    var searchQuery by rememberSaveable(show) { mutableStateOf("") }
    var searchActive by rememberSaveable(show) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // 关闭时清焦点：否则面板重开还处于聚焦态，输入法会先弹出再被收起
    LaunchedEffect(show) {
        if (!show) focusManager.clearFocus()
    }
    // 图标版本号：每补齐一块 +1，条目按它重新向 iconProvider 取一次图标（未命中的走异步解码）
    var iconEpoch by remember(show) { mutableIntStateOf(0) }

    // 打开面板、条目 / 刷新版本变化时补齐图标。条目只传包名清单，不传位图：结果落进共享
    // 缓存后按版本号重取即可，面板自身不持有图标数据；分块进度逐次回调，图标逐块出现
    val entryKeys = entries.map { it.key }
    LaunchedEffect(show, entryKeys, refreshToken) {
        if (!show || onFetchIcons == null || entryKeys.isEmpty()) return@LaunchedEffect
        onFetchIcons(entryKeys) { iconEpoch++ }
        iconEpoch++
    }

    // 面板显示而列表为空时自动拉一次：覆盖「打开时就没数据」与「打开期间切设备、列表被
    // 清空」两种情况，不必等用户点刷新。拉回数据后重新武装；失败后保持空态不自动重试
    // （空设备上会形成死循环），由刷新按钮兜底
    var autoLoadDone by remember(show) { mutableStateOf(false) }
    LaunchedEffect(show, entries.isEmpty(), refreshBusy) {
        if (!show) {
            autoLoadDone = false
            return@LaunchedEffect
        }
        if (entries.isNotEmpty()) {
            autoLoadDone = false
            return@LaunchedEffect
        }
        if (!refreshBusy && !autoLoadDone) {
            autoLoadDone = true
            onRefresh()
        }
    }

    OverlayBottomSheet(
        show = show,
        title = title,
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(
                onClick = { if (!refreshBusy) onRefresh() },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh, title),
                )
            }
        },
    ) {
        when {
            entries.isEmpty() && refreshBusy -> {
                Text(
                    text = loadingText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiSpacing.Large),
                    textAlign = TextAlign.Center,
                )
            }

            entries.isEmpty() -> {
                Text(
                    text = emptyText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiSpacing.Large),
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                // 可见条目在内容 lambda 里算：面板隐藏时 miuix 不组合这里，不做关闭状态的白工
                val visibleEntries = remember(entries, searchQuery, searchHint) {
                    appSheetVisibleEntries(entries, if (searchHint != null) searchQuery else null)
                }
                // 收藏提示条宿主：与列表 / 无结果两种渲染同生命周期（AppRuntime 取最后注册的
                // 宿主），注册后提示条落进本面板底部渲染，而不是被面板挡在下面看不见
                val sheetSnackHostState = remember { SnackbarHostState() }
                DisposableEffect(sheetSnackHostState) {
                    val unregister = AppRuntime.registerSnackbarHostState(sheetSnackHostState)
                    onDispose(unregister)
                }
                Column {
                    if (searchHint != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // 横向 16dp 保持不动（图标—框头、星标—框尾的对齐基准）；纵向改为
                            // 上 0 下 16dp：标题→框 17.5dp、框→列表 16dp，与「从本机应用安装」
                            // 选择器的留白节奏一致
                            modifier = Modifier.padding(
                                start = UiSpacing.Large,
                                top = 0.dp,
                                end = UiSpacing.Large,
                                bottom = UiSpacing.Large,
                            ),
                        ) {
                            // 与应用管理「从本机应用安装」同款：胶囊输入框 + 前置搜索图标 + 框尾清空钮
                            ImeSafeInputField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = searchHint,
                                color = colorScheme.surfaceContainerHighest,
                                leadingIcon = {
                                    Icon(
                                        imageVector = MiuixIcons.Basic.Search,
                                        tint = colorScheme.onSurfaceContainerHigh,
                                        contentDescription = null,
                                    )
                                },
                                trailingIcon = {
                                    // 有文本才出现框尾清空钮（淡入淡出）。图标在槽内右对齐、槽高取
                                    // 输入框最小高度：「从本机应用安装」选择器同款位置（图标右缘距
                                    // 胶囊尾 16dp）且胶囊不被撑高；触摸区 48×45dp，带 Role.Button
                                    // 与 cd_clear 语义（TalkBack 可读可点）
                                    AnimatedVisibility(
                                        visible = searchQuery.isNotEmpty(),
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(48.dp)
                                                .height(SearchBarDefaults.InputFieldMinHeight)
                                                .clickable(
                                                    role = Role.Button,
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) { searchQuery = "" },
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Basic.SearchCleanup,
                                                tint = colorScheme.onSurfaceContainerHighest,
                                                contentDescription = stringResource(R.string.cd_clear),
                                            )
                                        }
                                    }
                                },
                                onFocusChanged = { focused ->
                                    // 聚焦搜索框 = 进入输入态：才浮出「取消」按钮（非默认状态）
                                    if (focused) searchActive = true
                                },
                                modifier = Modifier.weight(1f),
                            )
                            AnimatedVisibility(
                                visible = searchActive,
                                enter = expandHorizontally() +
                                    slideInHorizontally(initialOffsetX = { it }) +
                                    fadeIn(),
                                exit = shrinkHorizontally() +
                                    slideOutHorizontally(targetOffsetX = { it }) +
                                    fadeOut(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        // 与搜索框等高：浮出 / 收起不撑高搜索行，列表头不跳动
                                        .height(SearchBarDefaults.InputFieldMinHeight)
                                        .widthIn(min = 48.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            searchQuery = ""
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
                    }
                    if (searchHint != null && visibleEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(2f / 3f),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Text(
                                text = stringResource(R.string.bottomsheet_no_search_result),
                                modifier = Modifier.padding(top = UiSpacing.Large),
                                textAlign = TextAlign.Center,
                            )
                            // 同一宿主在无结果分支也渲染：切到「没有找到匹配的应用」时，
                            // 刚触发的收藏提示条不会消失
                            SnackbarHost(
                                state = sheetSnackHostState,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    } else {
                        val appListState = rememberLazyListState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(2f / 3f),
                        ) {
                            LazyColumn(
                                state = appListState,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                // 「所有应用」不带 item key：收藏切换会重排列表，带 key 时 LazyColumn
                                // 会「追着」被移动的条目改写滚动位置（取消置顶时视图跳到该应用的新
                                // 位置），无 key 则滚动位置按索引保持原样；最近任务不重排也不分区，
                                // 保留 key 让条目身份稳定
                                items(
                                    items = visibleEntries,
                                    key = if (searchHint == null) {
                                        { entry: AppListEntry -> entry.itemKey ?: entry.key }
                                    } else {
                                        null
                                    },
                                ) { entry ->
                                    AppListBottomSheetItem(
                                        entry = entry,
                                        iconProvider = iconProvider,
                                        iconLoader = iconLoader,
                                        iconEpoch = iconEpoch,
                                        spinnerColors = DropdownDefaults.dropdownColors(),
                                    )
                                }
                            }
                            // 右侧快速滚动条（miuix 组件）：拖动可快速跳转，松手 1 秒后淡出。
                            // 不额外内缩：thumb 贴面板右缘（右缘 ≈1004.5），与星标 glyph
                            // （右缘 959）之间留出约 13dp 空隙，避免与收藏星标视觉上挤在一起
                            VerticalScrollBar(
                                adapter = rememberScrollBarAdapter(appListState),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                            )
                            // 提示条贴在列表底部、盖在列表之上；宿主在列表 / 无结果下都存活
                            SnackbarHost(
                                state = sheetSnackHostState,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListBottomSheetItem(
    entry: AppListEntry,
    iconProvider: (String) -> ImageBitmap?,
    iconLoader: suspend (String) -> ImageBitmap?,
    iconEpoch: Int,
    spinnerColors: DropdownColors,
) {
    val snackbar = LocalSnackbarController.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(spinnerColors.containerColor)
            .clickable(onClick = entry.onClick)
            // 行尾 13dp = 20dp − 7dp：星标按钮盒内（40dp 盒放 26dp 图标）左右各留 7dp，
            // 让出这段后星标 glyph 右缘与框尾的距离，与左侧图标与框头的相对位置互为镜像
            .padding(start = 20.dp, top = 12.dp, end = 13.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSheetIcon(
                entry = entry,
                iconProvider = iconProvider,
                iconLoader = iconLoader,
                iconEpoch = iconEpoch,
            )
            Column(
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text(
                    text = entry.title,
                    fontSize = textStyles.body1.fontSize,
                    color = spinnerColors.contentColor,
                )
                entry.summary?.let {
                    Text(
                        text = it,
                        fontSize = textStyles.body2.fontSize,
                        color = spinnerColors.summaryColor,
                    )
                }
            }
        }
        entry.onToggleFavorite?.let { onToggle ->
            val displayName = entry.title.ifBlank { entry.summary ?: "" }
            val addedMessage = stringResource(R.string.bottomsheet_favorite_added, entry.title)
            val removedMessage = stringResource(R.string.bottomsheet_favorite_removed, entry.title)
            // 连点两次时 entry.favorite 还没随偏好刷新：按本地目标态先翻，文案与星标都不落后
            // 一拍；真实偏好送达后（entry.favorite 变化）以它为准
            var targetFavorite by remember(entry.key, entry.favorite) {
                mutableStateOf(entry.favorite)
            }
            IconButton(
                onClick = {
                    val next = !targetFavorite
                    targetFavorite = next
                    // 反馈：收藏 / 取消收藏的提示条（宿主在面板内，见列表区的 SnackbarHost）
                    snackbar.show(if (next) addedMessage else removedMessage)
                    onToggle()
                },
            ) {
                Icon(
                    imageVector = if (targetFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription =
                        stringResource(
                            if (targetFavorite) R.string.bottomsheet_remove_favorite
                            else R.string.bottomsheet_add_favorite,
                        ) + " $displayName",
                    modifier = Modifier.sizeIn(minWidth = 26.dp, minHeight = 26.dp),
                )
            }
        }
    }
}

@Composable
private fun AppSheetIcon(
    entry: AppListEntry,
    iconProvider: (String) -> ImageBitmap?,
    iconLoader: suspend (String) -> ImageBitmap?,
    iconEpoch: Int,
) {
    // 首帧只读内存 LRU（主线程零 IO、零解码）；未命中先占位，异步解码后替换。
    // iconEpoch 参与读取：补齐的图标落盘后也要重新取一次
    var icon by remember(entry.key, iconEpoch) { mutableStateOf(iconProvider(entry.key)) }
    LaunchedEffect(entry.key, iconEpoch) {
        if (icon == null) icon = iconLoader(entry.key)
    }
    // 委托属性读不出智能转换：先落成局部值再判空
    val loaded = icon
    Box(
        modifier = Modifier
            .size(AppSheetIconSize)
            // squircle 裁切（圆角设置关闭时库内自动回退圆角矩形）；无图标时不垫底
            .squircleClip(cornerRadius = AppSheetIconCornerRadius),
        contentAlignment = Alignment.Center,
    ) {
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = entry.title.ifBlank { entry.summary ?: "" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            // 取不到图标时的占位：系统应用=机器人、用户应用=商店（与原面板一致）
            Icon(
                imageVector = if (entry.system == true) Icons.Rounded.Android else MiuixIcons.Store,
                contentDescription = entry.title.ifBlank { entry.summary ?: "" },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * 面板的可见顺序：先按搜索词过滤（名称 / 包名 / 键，大小写不敏感），再「收藏优先 + 应用名排序」。
 *
 * 排序走 [AppOrdering]（设备页 / 应用管理 / 本机选择器共用同一把尺子），同一个应用在哪个
 * 界面都挨在一起；排序键先算一遍缓存起来，避免每比较一对重算拼音。
 */
internal fun orderedAppSheetEntries(entries: List<AppListEntry>, query: String): List<AppListEntry> {
    val normalized = query.trim().lowercase(Locale.ROOT)
    val filtered = if (normalized.isEmpty()) {
        entries
    } else {
        entries.filter { entry ->
            entry.title.lowercase(Locale.ROOT).contains(normalized) ||
                entry.summary?.lowercase(Locale.ROOT)?.contains(normalized) == true ||
                entry.key.lowercase(Locale.ROOT).contains(normalized)
        }
    }
    val keyed = filtered.map { entry -> entry to AppOrdering.sortKey(entry.title, entry.key) }
    val (favorites, others) = keyed.partition { it.first.favorite }

    fun sortedByAppName(items: List<Pair<AppListEntry, AppSortKey>>): List<AppListEntry> =
        items.sortedWith { a, b -> AppOrdering.compareKeys(a.second, b.second) }.map { it.first }

    return sortedByAppName(favorites) + sortedByAppName(others)
}

/**
 * 面板可见条目：可搜索面板（[query] 非 null）走过滤 + 收藏优先排序；最近任务
 * （query == null）保持传入顺序——它的语义是「最近使用」，排序 / 收藏分区都会破坏语义，
 * 且最近任务面板本就没有星标。
 */
internal fun appSheetVisibleEntries(entries: List<AppListEntry>, query: String?): List<AppListEntry> =
    if (query == null) entries else orderedAppSheetEntries(entries, query)

/** 面板行内图标槽位与 squircle 圆角（26dp 槽、6dp 圆角）。 */
private val AppSheetIconSize = 26.dp
private val AppSheetIconCornerRadius = 6.dp
