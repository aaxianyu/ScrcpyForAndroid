package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppSearch
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import kotlinx.coroutines.launch

/**
 * 全屏搜索浮层。
 *
 * - 整屏不透明页面底色，圆形揭示展开 / 从搜索按钮位置回收（圆心 = 开搜瞬间锁定的位置）；
 * - 下层页面不缩放不位移；浮层空白处吞掉点击，不穿透到背后列表；
 * - 结果每行一个灰色圆角卡片（无字母分组、无索引条、无长按）；
 * - 点结果行直接弹操作面板，浮层不收回（由调用方把面板叠在浮层之上）；
 * - 关闭即清空搜索词；系统「移除动画」开启时无过场直接显隐。
 */
@Composable
fun SearchOverlay(
    visible: Boolean,
    revealCenterRoot: Offset,
    apps: List<DeviceApp>,
    showVersion: Boolean,
    showApkSize: Boolean,
    pendingPackages: Set<String>,
    iconProvider: (String) -> ImageBitmap?,
    iconEpoch: Int,
    animationsDisabled: Boolean,
    onDismissRequest: () -> Unit,
    onExitFinished: () -> Unit,
    onAppClick: (DeviceApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rendered by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val reveal = remember { Animatable(if (visible) 1f else 0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            rendered = true
            reveal.snapTo(0f)
            // 进页时先以合态绘制一帧，再展开
            withFrameNanos { }
            expanded = true
            val duration = if (animationsDisabled) 0 else AppManagerMotion.REVEAL_DURATION_MS
            reveal.animateTo(1f, tween(duration, easing = AppManagerMotion.RevealEasing))
        } else if (rendered) {
            val duration = if (animationsDisabled) 0 else AppManagerMotion.REVEAL_DURATION_MS
            reveal.animateTo(0f, tween(duration, easing = AppManagerMotion.RevealEasing))
            query = ""
            expanded = false
            rendered = false
            onExitFinished()
        }
    }

    if (!rendered) return

    val results = remember(apps, query) { AppSearch.filter(apps, query) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                val radius = reveal.value * maxRevealRadius(
                    center = revealCenterRoot,
                    size = size,
                )
                if (radius <= 0f) return@drawWithContent
                val path = Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            center = Offset(
                                revealCenterRoot.x.coerceIn(0f, size.width),
                                revealCenterRoot.y.coerceIn(0f, size.height),
                            ),
                            radius = radius,
                        ),
                    )
                }
                clipPath(path) { this@drawWithContent.drawContent() }
            }
            .background(colorScheme.surface)
            // 浮层空白处吞掉点击，不穿透到背后列表
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 浮层铺满全屏，内容避让状态栏、输入法与导航条
                .windowInsetsPadding(
                    WindowInsets.statusBars
                        .union(WindowInsets.ime)
                        .union(WindowInsets.navigationBars),
                ),
        ) {
            Spacer(Modifier.height(12.dp))
            SearchFieldRow(
                expanded = expanded,
                query = query,
                onQueryChange = { query = it },
                onExpandedChange = { expanded = it },
                onCancel = {
                    scope.launch {
                        // 点「取消」一次生效：清空搜索词并圆形回收
                        query = ""
                        onDismissRequest()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            when {
                query.isBlank() -> SearchHint()
                results.isEmpty() -> SearchNoResult()
                else -> SearchResults(
                    results = results,
                    showVersion = showVersion,
                    showApkSize = showApkSize,
                    pendingPackages = pendingPackages,
                    iconProvider = iconProvider,
                    iconEpoch = iconEpoch,
                    onAppClick = onAppClick,
                )
            }
        }
    }
}

@Composable
private fun SearchFieldRow(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = {},
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.app_manager_search_hint),
            // 显式指定容器色：默认 surfaceContainerHigh 与深色主题的页面/弹层底色同色，
            // 会看不出输入框边界（实测 #2D2D2D）
            color = colorScheme.surfaceContainerHighest,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            // 「取消」触摸区不小于 48dp（视觉尺寸不变）
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancel,
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.app_manager_search_cancel),
                    fontSize = 17.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<DeviceApp>,
    showVersion: Boolean,
    showApkSize: Boolean,
    pendingPackages: Set<String>,
    iconProvider: (String) -> ImageBitmap?,
    iconEpoch: Int,
    onAppClick: (DeviceApp) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results.size) { index ->
            val app = results[index]
            Card(cornerRadius = 16.dp) {
                AppRow(
                    app = app,
                    showVersion = showVersion,
                    showApkSize = showApkSize,
                    batchMode = false,
                    selected = false,
                    pending = app.packageName in pendingPackages,
                    iconProvider = iconProvider,
                    iconEpoch = iconEpoch,
                    allowLongPress = false,
                    onClick = { onAppClick(app) },
                    onLongClick = {},
                )
            }
        }
    }
}

@Composable
private fun SearchHint() {
    CenteredColumn {
        Text(
            text = stringResource(R.string.app_manager_search_hint),
            style = textStyles.body1,
            color = colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_manager_search_empty_hint),
            style = textStyles.footnote1,
            color = colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchNoResult() {
    CenteredColumn {
        Text(
            text = stringResource(R.string.app_manager_no_apps),
            style = textStyles.body1,
            color = colorScheme.onSurface,
        )
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** 圆心到四个屏幕角距离的最大值。 */
internal fun maxRevealRadius(center: Offset, size: Size): Float {
    if (size.width <= 0f || size.height <= 0f) return 0f
    val corners = listOf(
        Offset(0f, 0f),
        Offset(size.width, 0f),
        Offset(0f, size.height),
        Offset(size.width, size.height),
    )
    return corners.maxOf { (it - center).getDistance() }
}
