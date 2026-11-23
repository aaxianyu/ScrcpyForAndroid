package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.ui.DialogHeading
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup

/**
 * 安装来源面板：miuix 弹窗（OverlayDialog），
 * 标题「安装应用」、说明选择来源，「选择文件 / 选择应用」两个动作按钮。
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
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DialogHeading(
                title = stringResource(R.string.app_manager_install_source_title),
                summary = stringResource(R.string.app_manager_install_source_desc),
            )
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
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
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

/**
 * 应用管理顶栏「更多」：筛选项作为子菜单原地展开（可连续勾选），应用对比跳转独立页面。
 */
@Composable
fun AppManagerMoreMenu(
    visible: Boolean,
    hideVersion: Boolean,
    hideApkSize: Boolean,
    hideDisabled: Boolean,
    onToggleHideVersion: () -> Unit,
    onToggleHideApkSize: () -> Unit,
    onToggleHideDisabled: () -> Unit,
    onCompare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val filterItems = listOf(
        DropdownItem(
            text = stringResource(R.string.app_manager_hide_version),
            selected = hideVersion,
            onClick = onToggleHideVersion,
        ),
        DropdownItem(
            text = stringResource(R.string.app_manager_hide_apk_size),
            selected = hideApkSize,
            onClick = onToggleHideApkSize,
        ),
        DropdownItem(
            text = stringResource(R.string.app_manager_hide_disabled),
            selected = hideDisabled,
            onClick = onToggleHideDisabled,
        ),
    )
    val entry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = stringResource(R.string.app_manager_filter_display),
                children = filterItems,
            ),
            DropdownItem(
                text = stringResource(R.string.app_compare_title),
                onClick = onCompare,
            ),
        ),
    )
    OverlayCascadingListPopup(
        show = visible,
        entries = listOf(entry),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        maxHeight = null,
        dropdownColors = DropdownDefaults.dropdownColors(),
        renderInRootScaffold = true,
        collapseOnSelection = false,
        enableWindowDim = true,
    )
}
