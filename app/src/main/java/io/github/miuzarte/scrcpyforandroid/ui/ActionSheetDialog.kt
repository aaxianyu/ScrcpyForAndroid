package io.github.miuzarte.scrcpyforandroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

/** 通宽文本动作行菜单的一项。 */
internal data class ActionSheetItem(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * 通宽文本动作行菜单：overlay dialog，手机上贴底呈现、宽屏居中缩放；无拖拽把手，
 * 关闭靠点外面、返回键或底部「取消」。
 *
 * 面板参数：`insideMargin = DpSize(0.dp, 24.dp)` —— 水平内缩 0，动作行铺满弹窗宽度，
 * 按压背景与弹窗等宽；垂直 24dp 同时充当顶部说明行与底部取消按钮的外留白。
 *
 * 标题不传给组件库（库内标题字号固定为 title4），而是自绘一行居中的次要说明，默认
 * `footnote1`；`maxLines = 2` 容纳较长的应用名。
 *
 * 末行为通宽「取消」，与二次确认框同一组件、同一默认规格：圆角、字重、高度都取库默认；
 * 点击等价于点外面或返回键。
 *
 * 行序与行的可用性由调用方给出的 [actions] 决定；点任一动作后由调用方决定后续。
 *
 * 关闭请求可能在退场开始与结束时各回调一次，调用方按幂等处理。
 */
@Composable
internal fun ActionSheetDialog(
    title: String,
    actions: List<ActionSheetItem>,
    onDismissRequest: () -> Unit,
    visible: Boolean = true,
    titleStyle: TextStyle = textStyles.footnote1,
) {
    OverlayDialog(
        show = visible,
        insideMargin = DpSize(0.dp, 24.dp),
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    style = titleStyle,
                    color = colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actions.forEach { action ->
                SheetActionRow(
                    label = action.label,
                    enabled = action.enabled,
                    onClick = action.onClick,
                )
            }
            Spacer(Modifier.height(18.dp))
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        }
    }
}
