// Copyright 2026, ScrcpyForAndroid contributors
// SPDX-License-Identifier: Apache-2.0

package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.data.TransferPhase
import io.github.miuzarte.scrcpyforandroid.appmanager.data.TransferProgress
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.SizeFormat
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

/** 数值列的最小宽度：done/total 位数在传输过程中会变，留位免得整行抖动。 */
private val TRANSFER_VALUE_MIN_WIDTH = 96.dp

/**
 * 安装 / 提取进度弹窗。
 *
 * - 模态盖住应用管理页：弹窗期间页面不可操作（`OverlayDialog` 遮罩 + 根 Scaffold 宿主）；
 * - 用户按返回 / 点空白 / 点「取消」都不直接关，先问一次「中断？」，确认才中断进程；
 * - `exporting` 只影响标题文案（正在安装 / 正在提取）。
 */

@Composable
fun TransferProgressDialog(
    progress: TransferProgress,
    exporting: Boolean,
    onAbortConfirm: () -> Unit,
) {
    if (!progress.active) return
    var abortConfirmVisible by remember { mutableStateOf(false) }
    // 弹窗内容在自己的重组作用域里：直接读形参只会拿到首次组合时的值，字节数变了也画不出来。
    // 经 State 读一次，外层每推一个新进度就失效一次，弹窗内的大小与进度条才会跟着走。
    val liveProgress by rememberUpdatedState(progress)

    OverlayDialog(
        show = true,
        // 标题说「现在在哪个阶段」，副标题（summary）说「在处理哪个应用」
        title = stringResource(
            when {
                exporting -> if (progress.phase == TransferPhase.PREPARE) {
                    R.string.app_manager_transfer_preparing
                } else {
                    R.string.app_manager_transfer_exporting
                }
                progress.phase == TransferPhase.PREPARE -> R.string.app_manager_transfer_preparing
                progress.phase == TransferPhase.COMMIT -> R.string.app_manager_transfer_installing
                else -> R.string.app_manager_transfer_transferring
            },
        ),
        summary = progress.label,
        // 不传 onDismissRequest：点空白 / 系统返回由下面按钮与确认框接管，
        // 避免误触直接丢掉整个传输进度
        onDismissRequest = { abortConfirmVisible = true },
        onDismissFinished = { abortConfirmVisible = true },
    ) {
        val p = liveProgress
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinearProgressIndicator(
                progress = p.fraction,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                // 批次计数占左、数值与阶段说明占右：右侧那一列的位置因此固定，
                // 「第 x 个，共 y 个」出现或消失都不会让「设备端处理中」左右移动
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (p.count > 1) {
                    Text(
                        text = stringResource(
                            R.string.app_manager_transfer_count,
                            p.index,
                            p.count,
                        ),
                        style = textStyles.footnote1,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.widthIn(min = TRANSFER_VALUE_MIN_WIDTH),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 设备端提交这段既不可量化、也说不清在干什么：用一句话占住数值列，
                    // 比留空更像「在动」
                    if (p.phase == TransferPhase.COMMIT) {
                        Text(
                            text = stringResource(R.string.app_manager_transfer_device_processing),
                            style = textStyles.footnote1,
                            color = colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    } else if (p.totalBytes > 0L) {
                        Text(
                            text = stringResource(
                                R.string.app_manager_transfer_progress,
                                SizeFormat.humanReadable(p.doneBytes),
                                SizeFormat.humanReadable(p.totalBytes),
                            ),
                            style = textStyles.footnote1,
                            color = colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                }
            }

            TextButton(
                text = stringResource(R.string.app_manager_transfer_cancel),
                onClick = { abortConfirmVisible = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (abortConfirmVisible) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.app_manager_transfer_abort_title),
            summary = stringResource(R.string.app_manager_transfer_abort_summary),
            onDismissRequest = { abortConfirmVisible = false },
            onDismissFinished = { abortConfirmVisible = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = { abortConfirmVisible = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.app_manager_transfer_abort_confirm),
                    onClick = {
                        abortConfirmVisible = false
                        onAbortConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
