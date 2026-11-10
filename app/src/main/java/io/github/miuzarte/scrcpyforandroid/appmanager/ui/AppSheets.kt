package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppAction
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.ui.ActionSheetDialog
import io.github.miuzarte.scrcpyforandroid.ui.ActionSheetItem
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup

/**
 * 应用操作面板。
 *
 * 标题 = 应用名，动作为通宽文本行（无图标、无选中标记）。
 * 「打开」对已停用应用置灰且不响应点击、不给任何提示（角标已提供解释）。
 *
 * 点任一动作先记下动作并收起面板，调用方在面板收起时执行下一步。
 */
@Composable
fun AppActionSheet(
    visible: Boolean,
    app: DeviceApp?,
    onDismissRequest: () -> Unit,
    onActionPicked: (AppAction) -> Unit,
    /** 该应用此刻有进程在跑；null = 运行态未知 */
    running: Boolean? = null,
) {
    if (app == null) return
    // 强制停止只在「确实有进程在跑」时可用：已停用一律置灰（停用优先）；
    // 运行态未知时不置灰——拿不到运行状态就没依据禁用，否则一次 ps -A 读失败
    // 会让全列表的强制停止都点不动。置灰而不隐藏，菜单位置保持稳定
    val forceStopEnabled = app.isEnabled && running != false
    ActionSheetDialog(
        visible = visible,
        title = app.displayLabel,
        onDismissRequest = onDismissRequest,
        actions = listOf(
            ActionSheetItem(stringResource(R.string.app_manager_open), app.isEnabled) {
                onActionPicked(AppAction.OPEN)
            },
            ActionSheetItem(stringResource(R.string.app_manager_force_stop), forceStopEnabled) {
                onActionPicked(AppAction.FORCE_STOP)
            },
            ActionSheetItem(
                stringResource(
                    if (app.isEnabled) R.string.app_manager_disable else R.string.app_manager_enable,
                ),
            ) {
                onActionPicked(if (app.isEnabled) AppAction.DISABLE else AppAction.ENABLE)
            },
            ActionSheetItem(stringResource(R.string.app_manager_clear_data)) {
                onActionPicked(AppAction.CLEAR_DATA)
            },
            ActionSheetItem(stringResource(R.string.app_manager_export_apk)) {
                onActionPicked(AppAction.EXPORT_APK)
            },
            ActionSheetItem(stringResource(R.string.app_manager_uninstall)) {
                onActionPicked(AppAction.UNINSTALL)
            },
        ),
    )
}

/**
 * 安装来源面板：miuix 窗口对话框，
 * 标题「安装APK」、描述说明选择来源，「文件管理 / 已安装」两个动作按钮。
 */
@Composable
fun InstallSourceSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onPickFiles: () -> Unit,
    onPickLocalApps: () -> Unit,
) {
    OverlayDialog(
        show = visible,
        title = stringResource(R.string.app_manager_install_source_title),
        summary = stringResource(R.string.app_manager_install_source_desc),
        onDismissRequest = onDismissRequest,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            TextButton(
                text = stringResource(R.string.app_manager_install_source_file),
                onClick = onPickFiles,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.app_manager_install_source_local),
                onClick = onPickLocalApps,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 显示选项：下拉偏好浮层，可勾选项。
 *
 * `collapseOnSelection = false`：点击不收起、可连续勾选。
 *
 * 第三项「隐藏已停用应用」只对应用管理页有意义（本机应用选择器没有启用/停用概念），
 * 由 [showHideDisabled] 控制；默认关闭，本机选择器不传即不受影响。
 */
@Composable
fun DisplayOptionsMenu(
    visible: Boolean,
    hideVersion: Boolean,
    hideApkSize: Boolean,
    onToggleHideVersion: () -> Unit,
    onToggleHideApkSize: () -> Unit,
    onDismissRequest: () -> Unit,
    hideVersionLabelResId: Int = R.string.app_manager_hide_version,
    showHideApkSize: Boolean = true,
    hideDisabled: Boolean = false,
    onToggleHideDisabled: () -> Unit = {},
    showHideDisabled: Boolean = false,
) {
    val entry = DropdownEntry(
        items = buildList {
            add(
                DropdownItem(
                    text = stringResource(hideVersionLabelResId),
                    selected = hideVersion,
                    onClick = onToggleHideVersion,
                ),
            )
            if (showHideApkSize) {
                add(
                    DropdownItem(
                        text = stringResource(R.string.app_manager_hide_apk_size),
                        selected = hideApkSize,
                        onClick = onToggleHideApkSize,
                    ),
                )
            }
            if (showHideDisabled) {
                add(
                    DropdownItem(
                        text = stringResource(R.string.app_manager_hide_disabled),
                        selected = hideDisabled,
                        onClick = onToggleHideDisabled,
                    ),
                )
            }
        },
    )
    OverlayDropdownPopup(
        entries = listOf(entry),
        show = visible,
        onDismiss = onDismissRequest,
        onDismissFinished = onDismissRequest,
        maxHeight = null,
        dropdownColors = DropdownDefaults.dropdownColors(),
        renderInRootScaffold = true,
        collapseOnSelection = false,
    )
}
