package io.github.miuzarte.scrcpyforandroid.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

/**
 * 弹窗标题与说明。
 *
 * 组件库把标题与说明固定成整宽居中、标题不截断，这里自绘以取它的排版规范：
 * 标题最多 [TITLE_MAX_LINES] 行，超出省略，始终居中；说明只有一行时居中，
 * 换行后整段靠左（最长的一行撑满宽度，其余行贴着它的起点）。
 *
 * 说明的对齐由量出的行数决定，不靠 onTextLayout 回填：后者会先按居中画一帧，
 * 长说明在弹出瞬间有一次可见的位移。
 */
private const val TITLE_MAX_LINES = 4

@Composable
fun DialogHeading(
    title: String,
    summary: String? = null,
) {
    // 整段自己是一个布局节点：调用方的 Column 间距只作用一次，
    // 不会插到标题与说明之间
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            fontSize = textStyles.title4.fontSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            color = colorScheme.onBackground,
        )
        if (summary.isNullOrEmpty()) return@Column
        val summaryStyle = textStyles.body1
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.roundToPx() }
            // 系统字号变化时行数会变，缓存要跟着密度缩放比走
            val lineCount = remember(summary, summaryStyle, widthPx, density.fontScale) {
                measurer.measure(
                    text = AnnotatedString(summary),
                    style = summaryStyle,
                    constraints = Constraints(maxWidth = widthPx),
                ).lineCount
            }
            Text(
                text = summary,
                modifier = Modifier.fillMaxWidth(),
                style = summaryStyle,
                color = colorScheme.onSurfaceSecondary,
                textAlign = if (lineCount > 1) TextAlign.Start else TextAlign.Center,
            )
        }
    }
}
