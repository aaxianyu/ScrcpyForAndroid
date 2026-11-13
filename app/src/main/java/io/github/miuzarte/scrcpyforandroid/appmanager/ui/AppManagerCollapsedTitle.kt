package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 标题起点 = 顶栏水平 padding 16dp + 返回钮 40dp，即返回钮右缘。 */
private val TitleStart = 56.dp
private val RefreshIconGap = 8.dp

/**
 * 刷新转圈图标尺寸：展开态在导航侧，折叠态在标准形态的标题右侧，两处必须同值，
 * 否则同一个图标会在展开 / 折叠间变大变小。
 *
 * 取 20dp 而不是动作钮的 24dp：`MiuixIcons.Refresh` 的圆圈几乎占满图形自身边界，
 * 24dp 画出来的视觉体量大于同为 24dp 的搜索 / 显示选项，摆在 18sp 标题旁会盖过文字。
 */
internal val AppManagerRefreshIconSize = 20.dp

/**
 * 顶栏折叠态标题：标准形态靠左固定在返回钮右缘（刷新图标随标题在右），悬浮形态居中（不带图标，
 * 图标由导航侧绘制在返回钮右侧，标题因此不随刷新态位移）。
 *
 * miuix `TopAppBar` 的标题只收字符串、固定 20sp，两种形态的字号与图标尺寸无法统一；
 * 这一层叠在 `TopAppBar` 之上接管折叠态标题，两种形态共用同一套字号与图标尺寸。
 *
 * - 展开态大标题仍由 `TopAppBar` 自己负责；[collapsedFraction] 是折叠进度，0 = 展开，1 = 折叠完成；
 * - 出现阈值与淡入淡出参数与 miuix 折叠态小标题一致（进度过 1/3 出现，弹簧走 folme），
 *   是 `submodule/miuix/.../basic/TopAppBar.kt:672-696` 的逐行复刻，升级 miuix 时需同步；
 * - 不自带背景、不挂点击与指针输入：触摸穿透到下方的返回钮与动作钮。
 *
 * @param centered true = 悬浮形态，只画居中标题，刷新图标留给导航侧；false = 标准形态，标题靠左且带右侧图标
 * @param actionsWidth 标准形态右侧动作区总宽（含右缘 padding），标题区到此为止；居中形态不使用
 * @param refreshRotation 转圈角度回调，只能在绘制阶段读取（组合期读会让整个顶栏每帧重组）
 */
@Composable
fun AppManagerCollapsedTitle(
    title: String,
    centered: Boolean,
    actionsWidth: Dp,
    collapsedFraction: () -> Float,
    refreshing: Boolean,
    refreshRotation: () -> Float,
    modifier: Modifier = Modifier,
) {
    val currentTitle by rememberUpdatedState(title)
    val currentRefreshing by rememberUpdatedState(refreshing)
    val currentFraction by rememberUpdatedState(collapsedFraction)
    // 由 Boolean 触发而非逐帧读进度：弹簧只在翻转时重发，滚动期间不产生动画重启
    val visible by remember { derivedStateOf { currentFraction() * 3f >= 1f } }
    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    val translationY = remember { Animatable(if (visible) 0f else 20f) }
    LaunchedEffect(visible) {
        if (visible) {
            val showSpec = folmeSpring<Float>(damping = 1.0f, response = 0.3f)
            launch { alpha.animateTo(1f, showSpec) }
            launch { translationY.animateTo(0f, showSpec) }
        } else {
            val hideSpec = folmeSpring<Float>(damping = 1.0f, response = 0.15f)
            launch { alpha.animateTo(0f, hideSpec) }
            launch { translationY.animateTo(20f, hideSpec) }
        }
    }

    val barModifier = modifier
        .fillMaxWidth()
        // 与 TopAppBar 相同的安全区叠加：左右让开刘海与导航栏，顶部让开状态栏
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        .height(TopAppBarDefaults.CollapsedHeight)
    val rowModifier = Modifier.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
    }

    if (centered) {
        // 居中形态：两侧留同样宽度，居中位置即屏幕中线，与 miuix 折叠态标题的居中位置一致
        Box(
            modifier = barModifier.padding(horizontal = TitleStart),
            contentAlignment = Alignment.Center,
        ) {
            Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                // 居中形态不带转圈图标：图标画在导航侧（返回钮右侧），标题才能钉住屏幕中线。
                // 否则「正在刷新」会被图标+间距推得比「应用管理」偏左约半个图标宽，切换时左右跳。
                CollapsedTitleContent(currentTitle, false, refreshRotation)
            }
        }
    } else {
        Box(
            modifier = barModifier.padding(start = TitleStart, end = actionsWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = rowModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CollapsedTitleContent(currentTitle, currentRefreshing, refreshRotation)
            }
        }
    }
}

@Composable
private fun RowScope.CollapsedTitleContent(
    title: String,
    refreshing: Boolean,
    refreshRotation: () -> Float,
) {
    Text(
        text = title,
        // 用 title4（18sp）而非 miuix 顶栏折叠态的 title3（20sp）：20sp 的字面高度高于
        // 24dp 动作图标（图标图形自带留白，实际着色只有 17dp 上下），标题比按钮更抢眼
        fontSize = MiuixTheme.textStyles.title4.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        // 文字过长时截断，刷新图标仍完整保留
        modifier = Modifier.weight(1f, fill = false),
    )
    if (refreshing) {
        Spacer(Modifier.width(RefreshIconGap))
        Icon(
            imageVector = MiuixIcons.Refresh,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(AppManagerRefreshIconSize)
                .graphicsLayer { rotationZ = refreshRotation() },
        )
    }
}