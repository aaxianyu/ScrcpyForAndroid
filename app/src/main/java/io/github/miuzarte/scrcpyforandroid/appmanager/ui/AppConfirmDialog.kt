package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 二次确认对话框。
 *
 * 标题随上下文（应用操作类「应用管理」/ 安装类「安装 APK」），副文案为操作描述
 * （单个应用带应用名，批量带数量）；两个等宽按钮横排：取消 / 确认（确认为主色）；
 * 确认后对话框先收起再执行操作。
 */
@Composable
fun AppConfirmDialog(
    visible: Boolean,
    title: String,
    summary: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!visible) return
    OverlayDialog(
        show = true,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
    ) {
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
                text = stringResource(R.string.button_confirm),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
