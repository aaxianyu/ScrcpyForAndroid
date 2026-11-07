package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.SizeFormat
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

/** 实测色值：版本号弱化色与包名行摘要色（深色主题真机采样）。 */
private val VersionWeakColor = Color(0xFF8E93A9)
private val SummaryColor = Color(0xFF929292)

/**
 * 行结构：图标 + 应用名(+版本号) / 包名(+大小) / 行尾定宽槽。
 * 两行的宽度优先级都是「尾巴上的信息完整优先，前面名字让位」，见
 * [AppNameVersionRow] 与 [PackageNameSizeRow]。
 */
@Composable
fun AppRow(
    app: DeviceApp,
    showVersion: Boolean,
    showApkSize: Boolean,
    batchMode: Boolean,
    selected: Boolean,
    pending: Boolean,
    iconProvider: (String) -> ImageBitmap?,
    /** 图标版本号：变化时重新取图标（列表内容没变但图标刚落盘也要刷新） */
    iconEpoch: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 搜索结果为单选态：不挂长按语义、不长按进多选。 */
    allowLongPress: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val longPressLabel = stringResource(R.string.app_manager_long_press_multi_select)
    val longPressEnabled = allowLongPress && !pending && !batchMode
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppRowHeight)
            .pressable(
                interactionSource = interactionSource,
                indication = SinkFeedback(),
                enabled = !pending,
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !pending,
                onClick = onClick,
                onLongClick = if (longPressEnabled) onLongClick else null,
            )
            .semantics {
                role = Role.Button
                if (pending) disabled()
                if (longPressEnabled) onLongClick(label = longPressLabel) { true }
                onClick(label = null) { true }
            }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            app = app,
            iconProvider = iconProvider,
            iconEpoch = iconEpoch,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            val showVersionLine = showVersion && app.versionName.isNotBlank()
            val showSizeLine = showApkSize && app.apkSizeBytes > 0
            if (!showVersionLine && !showSizeLine) {
                // 两个信息全被隐藏：保留两行排版，行高不变
                AppNameVersionRow(
                    name = app.displayLabel,
                    disabled = !app.isEnabled,
                )
                PackageNameSizeRow(
                    leadingText = app.packageName,
                    sizeText = null,
                )
            } else {
                // 版本+大小迁至第三行；「三行」下每行字号适当缩小
                AppNameVersionRow(
                    name = app.displayLabel,
                    disabled = !app.isEnabled,
                    compact = true,
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
                        if (app.isSplit) size + "·" + stringResource(R.string.app_manager_split_badge) else size
                    } else {
                        null
                    },
                    compact = true,
                )
            }
        }
        AppRowTrailingSlot(
            batchMode = batchMode,
            selected = selected,
            pending = pending,
        )
    }
}

/**
 * 第一行：app 名 + 版本号 + 「已停用」标签。
 *
 * 优先级：**版本号 ≈ 标签 > app 名**。放不下时优先压缩 app 名（省略号截断）——
 * 版本号与「已停用」标签按自身宽度占位，是硬宽度。用户要求「先保证版本号与
 * APK 大小的显示，再到应用名和应用包名」，两行都照这个来。
 *
 * 用自定义 Layout 是因为 Row 的测量顺序（非加权子项先按缩约束测量）无法表达
 * 「按后半部分固定宽度反推前半部分可用宽度」。
 */
@Composable
internal fun AppNameVersionRow(
    name: String,
    disabled: Boolean,
    compact: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            fontSize = if (compact) textStyles.body2.fontSize else textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (disabled) {
            Spacer(Modifier.width(6.dp))
            DisabledBadge()
        }
    }
}

/**
 * 第二行：包名 + 分割线 + 安装包大小。
 *
 * 与第一行（[AppNameVersionRow]）同一套优先级：**大小是硬宽度、必须完整显示**，放不下时
 * 只压缩包名——包名末尾带上省略号，再接分割线与大小。本机应用选择器与设备侧列表共用。
 *
 * 用自定义 Layout 是因为 Row 无法表达「按后半部分固定宽度反推前半部分可用宽度」。
 */
@Composable
internal fun PackageNameSizeRow(
    leadingText: String,
    sizeText: String?,
    compact: Boolean = false,
) {
    val separator = " | "
    val fontSize = if (compact) 11.sp else 12.sp
    // 同 AppNameVersionRow：fill = false 才能让「 | 大小」紧跟在包名 / 版本号后面，
    // 而不是被前段文字顶到行尾
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = leadingText,
            color = SummaryColor,
            fontSize = fontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (sizeText != null) {
            Text(
                text = separator,
                color = SummaryColor,
                fontSize = fontSize,
                maxLines = 1,
            )
            Text(
                text = sizeText,
                color = SummaryColor,
                fontSize = fontSize,
                maxLines = 1,
            )
        }
    }
}

/**
 * 第三行：版本号 +（可选）APK 大小。布局同 [PackageNameSizeRow]（后段硬宽度优先展示）。
 *
 * 两个值都可空：版本这块首段和大小尾段均按自身宽度占位，版本过长时像包名一样省略。
 * 只有一项要展示时也独立占一行。
 */
@Composable
internal fun VersionSizeLine(
    version: String?,
    sizeText: String?,
    compact: Boolean = false,
) {
    // sizeText 独用一行时（版本被隐藏），优先保住「大小」整体展示：直接作为 PackageNameSizeRow 的首段，尾段完整展示
    val first = when {
        version != null -> version
        else -> sizeText ?: return
    }
    PackageNameSizeRow(
        leadingText = first,
        sizeText = if (version != null) sizeText else null,
        compact = compact,
    )
}

@Composable
private fun AppIcon(
    app: DeviceApp,
    iconProvider: (String) -> ImageBitmap?,
    iconEpoch: Int,
) {
    // 读取只看「已应用版本」：正在看的行不随图标逐块到达而跳变；行被回收时才推进（见 IconApplyGate）
    val appliedEpoch = IconApplyGate.appliedFor(app.packageName)
    // 已显示图标的行按「已应用版本」冻结；还没有图标的行必须随数据版本刷新，否则首屏永远停在占位
    val icon = remember(app.packageName, appliedEpoch) { iconProvider(app.packageName) }
        ?: remember(app.packageName, "placeholder", iconEpoch) { iconProvider(app.packageName) }
    val latestEpoch = rememberUpdatedState(iconEpoch)
    DisposableEffect(app.packageName) {
        onDispose { IconApplyGate.onRowGone(app.packageName, latestEpoch.value) }
    }
    Box(
        modifier = Modifier
            .size(AppIconSize)
            // squircle 裁切（圆角设置关闭时库内自动回退圆角矩形）；有图标时不垫底
            .squircleClip(cornerRadius = AppIconCornerRadius),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = stringResource(R.string.app_manager_cd_app_icon),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            // 取不到图标才垫底：系统应用=机器人、用户应用=商店，secondaryVariant + 22dp 字形
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .squircleSurface(color = colorScheme.secondaryVariant, cornerRadius = AppIconCornerRadius),
                contentAlignment = Alignment.Center,
            ) {
                val placeholder: ImageVector =
                    if (app.isSystem) Icons.Rounded.Android else MiuixIcons.Store
                Icon(
                    imageVector = placeholder,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = colorScheme.onSecondaryVariant,
                )
            }
        }
    }
}

@Composable
private fun DisabledBadge() {
    Text(
        text = stringResource(R.string.app_manager_disabled),
        color = colorScheme.onSurfaceVariantSummary,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colorScheme.surfaceContainerHighest)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * 行尾 28dp 定宽槽：箭头 / 勾选框 / 转圈指示器三者互斥、同槽居中，
 * 保证进出批量模式时中间列宽度不变、版本号不位移。
 */
@Composable
private fun AppRowTrailingSlot(
    batchMode: Boolean,
    selected: Boolean,
    pending: Boolean,
) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .fillMaxHeight()
            // 4dp 右内距：转圈与卡片右缘留空
            .padding(end = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // 显式 size：组件库会用自带 size（默认 30dp）覆盖 modifier 的 size，
            // 不传就会画一个 30dp 的圈塞在 28dp 槽里，偏大又显歪
            pending -> CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                size = 26.dp,
                strokeWidth = 2.dp,
            )

            batchMode -> Checkbox(
                state = if (selected) ToggleableState.On else ToggleableState.Off,
                onClick = null,
                modifier = Modifier.size(26.dp),
            )

            else -> Icon(
                // miuix 列表行尾箭头规范（Dropdown.kt:200 / DropdownDefaults.ChevronSize）：
                // Basic.ArrowRight，10×16dp，摘要色
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .width(10.dp)
                    .height(16.dp),
                tint = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 应用图标槽位与 squircle 圆角（40dp 槽、8dp 圆角）。 */
private val AppIconSize = 40.dp
private val AppIconCornerRadius = 8.dp

/**
 * 行的最小高度。
 *
 * 是 [heightIn] 的下限而不是恒定高度：应用名 `maxLines = 2`，排到两行时该行会高于这个值。
 * 快速滚动滑块的高度标定正是按这个前提逐行实测的，别把它当成定高来反推行高。
 */
val AppRowHeight = 75.6.dp

/** 节末留白的高度；列表每节末尾放一个同高的 Spacer，索引条按它算前缀高度。 */
val AppSectionGap = 8.dp

/**
 * 字母头 item 的整项高度：单行字母 + 上下内边距 + 节首那块间隔。
 *
 * 索引条在量到真实头高之前用它兜底。头 78 px、行 189 px（2.5 倍密度）：拿行高兜底时每个头
 * 差 111 px，一屏十来个头就是上千像素，可滚像素整段偏大。
 */
@Composable
fun appSectionHeaderHeight(): Dp {
    val style = textStyles.subtitle
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        val linePx = measurer.measure(
            text = AnnotatedString("A"),
            style = style,
            maxLines = 1,
        ).size.height
        with(density) { linePx.toDp() + 4.dp + 2.dp + AppSectionGap }
    }
}
