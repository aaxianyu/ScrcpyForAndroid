package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.ui.DialogHeading
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 二次确认对话框。
 *
 * 标题与说明走 [DialogHeading]（标题四行截断；说明换行后靠左）；两个等宽按钮横排：
 * 左取消，右执行，按钮文字写明动作本身（卸载 / 强行停止…），破坏性动作取错误色。
 * 确认后对话框先收起再执行操作。
 */
@Composable
fun AppConfirmDialog(
    visible: Boolean,
    title: String,
    summary: String,
    confirmLabel: String,
    dangerous: Boolean = false,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!visible) return
    OverlayDialog(
        show = true,
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DialogHeading(title = title, summary = summary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = if (dangerous) {
                        ButtonDefaults.textButtonColors(textColor = colorScheme.error)
                    } else {
                        ButtonDefaults.textButtonColorsPrimary()
                    },
                )
            }
        }
    }
}
