package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import android.util.Base64
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.services.AppIconCache
import io.github.miuzarte.scrcpyforandroid.utils.AppSortUtils
import io.github.miuzarte.scrcpyforandroid.utils.appIconRounded
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import java.util.Locale

data class AppListEntry(
    val key: String,
    val title: String,
    val summary: String? = null,
    val system: Boolean? = null,
    val favorite: Boolean = false,
    val iconBase64: String? = null,
    val onClick: () -> Unit,
    val onToggleFavorite: (() -> Unit)? = null,
)

@Composable
fun AppListBottomSheet(
    show: Boolean,
    title: String,
    loadingText: String,
    emptyText: String,
    searchHint: String,
    entries: List<AppListEntry>,
    refreshBusy: Boolean,
    onDismissRequest: () -> Unit,
    onRefresh: () -> Unit,
    onFetchIcons: (suspend (Set<String>) -> Map<String, String>)? = null,
) {
    var searchQuery by remember(show) { mutableStateOf("") }
    var iconMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val pkgKeys = remember(entries) { entries.map { it.key }.toSet() }

    LaunchedEffect(show, pkgKeys) {
        if (show && onFetchIcons != null && pkgKeys.isNotEmpty()) {
            val cached = pkgKeys
                .mapNotNull { pkg ->
                    runCatching { AppIconCache.getIconBase64(pkg) }.getOrNull()?.let { pkg to it }
                }
                .toMap()
            val missing = pkgKeys - cached.keys
            val fetched = if (missing.isNotEmpty()) {
                runCatching {
                    withContext(Dispatchers.IO) { onFetchIcons(missing) }
                }.getOrElse { emptyMap() }.filterValues { it.isNotBlank() }
            } else {
                emptyMap()
            }
            fetched.forEach { (pkg, b64) ->
                runCatching { AppIconCache.putIcon(pkg, b64) }
            }
            iconMap = cached + fetched
        }
        if (!show) {
            iconMap = emptyMap()
        }
    }

    val enrichedEntries = remember(entries, iconMap) {
        entries.map { entry ->
            val icon = iconMap[entry.key]
            if (icon != null && entry.iconBase64 == null) entry.copy(iconBase64 = icon)
            else entry
        }
    }

    val filteredAndSorted = remember(enrichedEntries, searchQuery) {
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        val filtered = if (query.isBlank()) enrichedEntries else enrichedEntries.filter { entry ->
            entry.title.lowercase(Locale.ROOT).contains(query) ||
                entry.summary?.lowercase(Locale.ROOT)?.contains(query) == true ||
                entry.key.lowercase(Locale.ROOT).contains(query)
        }
        val (favorites, others) = filtered.partition { it.favorite }
        favorites.sortedBy { AppSortUtils.sortKey(it.title, it.key) } + others.sortedBy { AppSortUtils.sortKey(it.title, it.key) }
    }

    OverlayBottomSheet(
        show = show,
        title = title,
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(
                onClick = { if (!refreshBusy) onRefresh() },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh, title),
                )
            }
        },
    ) {
        when {
            enrichedEntries.isEmpty() && refreshBusy -> {
                Text(
                    text = loadingText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiSpacing.Large),
                    textAlign = TextAlign.Center,
                )
            }

            enrichedEntries.isEmpty() -> {
                Text(
                    text = emptyText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiSpacing.Large),
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = searchHint,
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = MiuixIcons.Basic.Search,
                            contentDescription = searchHint,
                            tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.SearchCleanup,
                                    contentDescription = stringResource(R.string.cd_clear),
                                    tint = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                                )
                            }
                        }
                    } else null,
                )
                if (filteredAndSorted.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .padding(bottom = UiSpacing.SheetBottom),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Text(
                            text = stringResource(R.string.bottomsheet_no_search_result),
                            modifier = Modifier.padding(top = 16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                    ) {
                        items(items = filteredAndSorted, key = { it.key }) { entry ->
                            AppListBottomSheetItem(
                                entry = entry,
                                spinnerColors = DropdownDefaults.dropdownColors(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListBottomSheetItem(
    entry: AppListEntry,
    spinnerColors: DropdownColors,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(spinnerColors.containerColor)
            .combinedClickable(
                onClick = entry.onClick,
                onLongClick = entry.onToggleFavorite,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconBitmap = remember(entry.iconBase64) {
                entry.iconBase64?.let { b64 ->
                    try {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (_: Exception) { null }
                }
            }
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = entry.title.ifBlank { entry.summary ?: "" },
                    modifier = Modifier
                        .size(26.dp)
                        .appIconRounded(26.dp),
                )
            } else {
                Icon(
                    imageVector =
                        if (entry.system == true) Icons.Rounded.Android
                        else MiuixIcons.Store,
                    contentDescription = entry.title.ifBlank { entry.summary ?: "" },
                    modifier = Modifier
                        .sizeIn(minWidth = 26.dp, minHeight = 26.dp),
                )
            }
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                Text(
                    text = entry.title,
                    fontSize = textStyles.body1.fontSize,
                    color = spinnerColors.contentColor,
                )
                entry.summary?.let {
                    Text(
                        text = it,
                        fontSize = textStyles.body2.fontSize,
                        color = spinnerColors.summaryColor,
                    )
                }
            }
        }
        if (entry.onToggleFavorite != null) {
            IconButton(
                onClick = entry.onToggleFavorite,
            ) {
                Icon(
                    imageVector = if (entry.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = if (entry.favorite) stringResource(R.string.bottomsheet_remove_favorite) else stringResource(R.string.bottomsheet_add_favorite),
                    modifier = Modifier.sizeIn(minWidth = 26.dp, minHeight = 26.dp),
                )
            }
        }
    }
}
