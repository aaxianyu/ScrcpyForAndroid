package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.storage.CommandBookmark
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun CommandBookmarkBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onInputCommand: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bookmarks by Storage.commandBookmarks.bookmarks.collectAsState()

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editBookmark by rememberSaveable { mutableStateOf<CommandBookmark?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf<CommandBookmark?>(null) }

    val asBundle by Storage.appSettings.bundleState.collectAsState()
    val autoEnter = asBundle.terminalBookmarkAutoEnter

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.bookmark_title),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.bookmark_create),
                )
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.PageHorizontal),
        ) {
            SwitchPreference(
                title = "自动回车执行",
                checked = autoEnter,
                onCheckedChange = {
                    scope.launch {
                        Storage.appSettings.updateBundle { bundle ->
                            bundle.copy(terminalBookmarkAutoEnter = !autoEnter)
                        }
                    }
                },
            )
        }
        if (bookmarks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(2f / 3f),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
                contentPadding = PaddingValues(vertical = UiSpacing.PageVertical),
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.PageHorizontal),
                        pressFeedbackType = PressFeedbackType.Sink,
                        showIndication = true,
                        onClick = {
                            onInputCommand(if (autoEnter) "${bookmark.command}\n" else bookmark.command)
                            onDismissRequest()
                        },
                        onLongPress = {
                            editBookmark = bookmark
                        },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = bookmark.name,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = bookmark.command,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        BookmarkEditDialog(
            bookmark = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, command ->
                scope.launch {
                    Storage.commandBookmarks.add(CommandBookmark(name = name, command = command))
                }
                showCreateDialog = false
            },
        )
    }

    editBookmark?.let { bm ->
        BookmarkEditDialog(
            bookmark = bm,
            onDismiss = { editBookmark = null },
            onConfirm = { name, command ->
                scope.launch {
                    Storage.commandBookmarks.update(bm.id, name, command)
                }
                editBookmark = null
            },
            onDelete = {
                showDeleteConfirm = bm
                editBookmark = null
            },
        )
    }

    showDeleteConfirm?.let { bm ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.bookmark_delete),
            onDismissRequest = { showDeleteConfirm = null },
        ) {
            Column(modifier = Modifier.padding(UiSpacing.Large)) {
                Text(
                    text = bm.name,
                    style = MiuixTheme.textStyles.body1,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(
                        text = stringResource(R.string.button_cancel),
                        onClick = { showDeleteConfirm = null },
                    )
                    TextButton(
                        text = stringResource(R.string.button_delete),
                        onClick = {
                            scope.launch {
                                Storage.commandBookmarks.remove(bm.id)
                            }
                            showDeleteConfirm = null
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkEditDialog(
    bookmark: CommandBookmark?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, command: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var nameInput by rememberSaveable(bookmark?.id) {
        mutableStateOf(bookmark?.name ?: "")
    }
    var commandInput by rememberSaveable(bookmark?.id) {
        mutableStateOf(bookmark?.command ?: "")
    }

    val isEditing = bookmark != null
    val title = if (isEditing) stringResource(R.string.bookmark_edit)
    else stringResource(R.string.bookmark_create)

    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.padding(UiSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            SuperTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = stringResource(R.string.bookmark_hint_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SuperTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                label = stringResource(R.string.bookmark_hint_command),
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (onDelete != null) {
                    TextButton(
                        text = stringResource(R.string.bookmark_delete),
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismiss,
                )
                TextButton(
                    text = stringResource(R.string.button_done),
                    onClick = {
                        if (nameInput.isBlank() || commandInput.isBlank()) return@TextButton
                        onConfirm(nameInput.trim(), commandInput.trim())
                    },
                    enabled = nameInput.isNotBlank() && commandInput.isNotBlank(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
