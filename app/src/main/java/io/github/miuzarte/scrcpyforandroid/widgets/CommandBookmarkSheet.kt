package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.storage.CommandBookmarks
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import io.github.miuzarte.scrcpyforandroid.storage.commandForInput
import io.github.miuzarte.scrcpyforandroid.ui.confirm
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 终端命令书签面板。
 *
 * 列表项单击把命令送入终端输入（「自动回车执行」开启时补一个回车直接执行）；
 * 行尾提供「删除」「编辑」按钮，右上角「+」新建；编辑弹窗只有「取消」「完成」。
 */
@Composable
fun CommandBookmarkSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onInputCommand: (String) -> Unit,
) {
    // 落盘用不随面板组合取消的作用域：点「完成」后立刻离开终端页也能写完
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val haptic = LocalHapticFeedback.current
    val bookmarkState by Storage.commandBookmarks.state.collectAsState()

    // 自动回车落设置项: 杀掉进程重进后仍保持上次的选择
    val asBundle by Storage.appSettings.bundleState.collectAsState()
    val autoEnter = asBundle.bookmarkAutoEnter
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }

    // 目标条目可能已被并发删掉：悬挂的 id 清掉，避免弹窗用旧数据再次出现
    LaunchedEffect(editingId, deletingId, bookmarkState.bookmarks) {
        val ids = bookmarkState.bookmarks.map { it.id }.toSet()
        if (editingId != null && editingId !in ids) editingId = null
        if (deletingId != null && deletingId !in ids) deletingId = null
    }

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.bookmark_title),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        startAction = {
            IconButton(
                onClick = {
                    haptic.contextClick()
                    onDismissRequest()
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    haptic.contextClick()
                    creating = true
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = stringResource(R.string.bookmark_create),
                )
            }
        },
    ) {
        SectionSmallTitle(stringResource(R.string.bookmark_section_auto_enter))
        // 卡片底色与面板背景拉开一层，开关与书签列表各成一块
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
        ) {
            SwitchPreference(
                title = stringResource(R.string.bookmark_auto_enter),
                summary = stringResource(R.string.bookmark_auto_enter_summary),
                checked = autoEnter,
                onCheckedChange = { enabled ->
                    taskScope.launch {
                        Storage.appSettings.saveBundle(asBundle.copy(bookmarkAutoEnter = enabled))
                    }
                },
            )
        }

        SectionSmallTitle(stringResource(R.string.bookmark_section_list))

        // 空状态与列表共用同一条高度下限: 单条时两态同高, 超过下限才长高, 到上限内部滚动
        val panelMinHeight = LocalConfiguration.current.screenHeightDp.dp * 0.4f
        val panelMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 2f / 3f

        if (bookmarkState.bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = panelMinHeight, max = panelMaxHeight),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.bookmark_empty),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bookmark_empty_hint),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        } else {
            val textDelete = stringResource(R.string.bookmark_delete)
            val textEdit = stringResource(R.string.bookmark_edit)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = panelMinHeight, max = panelMaxHeight),
                contentPadding = PaddingValues(bottom = UiSpacing.PageVertical),
            ) {
                item {
                    // 全部书签共用一张卡，卡即分组容器
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        bookmarkState.bookmarks.forEach { bookmark ->
                            // 书名 headline1/Medium、命令 body2，样式取库令牌；命令最多 2 行，长命令不撑高整行
                            BasicComponent(
                                onClick = {
                                    onInputCommand(commandForInput(bookmark.command, autoEnter))
                                    onDismissRequest()
                                },
                                endActions = {
                                    // 行尾圆形操作钮，样式与局域网扫描弹层共用 RowActionButton
                                    RowActionButton(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = textDelete,
                                        onClick = { deletingId = bookmark.id },
                                    )
                                    Spacer(modifier = Modifier.width(UiSpacing.Small))
                                    RowActionButton(
                                        imageVector = MiuixIcons.Edit,
                                        contentDescription = textEdit,
                                        onClick = { editingId = bookmark.id },
                                    )
                                },
                            ) {
                                Text(
                                    text = bookmark.name,
                                    style = MiuixTheme.textStyles.headline1,
                                    fontWeight = FontWeight.Medium,
                                    color = BasicComponentDefaults.titleColor().color,
                                )
                                Text(
                                    text = bookmark.command,
                                    style = MiuixTheme.textStyles.body2,
                                    color = BasicComponentDefaults.summaryColor().color,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        BookmarkEditDialog(
            bookmark = null,
            onDismiss = { creating = false },
            onConfirm = { name, command ->
                taskScope.launch { Storage.commandBookmarks.add(name, command) }
                creating = false
            },
        )
    }

    val editing = bookmarkState.bookmarks.firstOrNull { it.id == editingId }
    if (editing != null) {
        BookmarkEditDialog(
            bookmark = editing,
            onDismiss = { editingId = null },
            onConfirm = { name, command ->
                taskScope.launch { Storage.commandBookmarks.update(editing.id, name, command) }
                editingId = null
            },
        )
    }

    val deleting = bookmarkState.bookmarks.firstOrNull { it.id == deletingId }
    if (deleting != null) {
        BookmarkDeleteDialog(
            bookmark = deleting,
            onDismiss = { deletingId = null },
            onConfirm = {
                taskScope.launch { Storage.commandBookmarks.remove(deleting.id) }
                deletingId = null
            },
        )
    }
}

@Composable
private fun BookmarkEditDialog(
    bookmark: CommandBookmarks.Bookmark?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, command: String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var nameInput by rememberSaveable(bookmark?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(bookmark?.name.orEmpty()))
    }
    var commandInput by rememberSaveable(bookmark?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(bookmark?.command.orEmpty()))
    }
    val canConfirm = nameInput.text.isNotBlank() && commandInput.text.isNotBlank()

    OverlayDialog(
        show = true,
        title = stringResource(
            if (bookmark != null) R.string.bookmark_edit else R.string.bookmark_create,
        ),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical)) {
            // label 留作浮动标签：输入后缩到 10dp 并上浮，与锁屏密码输入框一致
            TextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = stringResource(R.string.bookmark_hint_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                label = stringResource(R.string.bookmark_hint_command),
                // 多行命令预览用：最多 15 行，再多在输入框内滚动，避免弹窗超出屏幕
                maxLines = 15,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.PageItem),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = {
                        haptic.contextClick()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_done),
                    onClick = {
                        if (!canConfirm) return@TextButton
                        haptic.confirm()
                        onConfirm(nameInput.text.trim(), commandInput.text.trim())
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun BookmarkDeleteDialog(
    bookmark: CommandBookmarks.Bookmark,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    OverlayDialog(
        show = true,
        title = stringResource(R.string.bookmark_delete),
        // 描述走 summary：组件库按标题样式居中排版，与标题同一条中轴
        summary = stringResource(R.string.bookmark_delete_confirm, bookmark.name),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.PageItem),
        ) {
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = {
                    haptic.contextClick()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            )
            // 底色与取消一致，只把文字染红：删除是破坏性动作，靠字色区分而不换按钮样式
            TextButton(
                text = stringResource(R.string.bookmark_delete),
                onClick = {
                    haptic.confirm()
                    onConfirm()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.error,
                ),
            )
        }
    }
}
