package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.util.UUID

/**
 * 终端命令书签：名称 + 命令，整份列表存成 DataStore 里的一条 JSON 字符串。
 *
 * 解析按条目容错：单条缺字段/字段为空/类型不对时跳过该条，不重置整份列表——
 * 书签是用户手写内容，一条坏数据不该带走其余条目；重复 id 只保留首条（列表 key 冲突防护）。
 */
class CommandBookmarks(context: Context): Settings(context, "CommandBookmarks") {
    companion object {
        private val BOOKMARKS_JSON = Pair(
            stringPreferencesKey("bookmarks_json"),
            "",
        )
    }

    data class Bookmark(
        val id: String,
        val name: String,
        val command: String,
    )

    data class State(
        val bookmarks: List<Bookmark>,
    )

    val state: StateFlow<State> = createBundleState(::stateFromPreferences)

    private fun stateFromPreferences(preferences: Preferences): State =
        State(decodeCommandBookmarks(preferences.read(BOOKMARKS_JSON)))

    /** 读-改-写都在 dataStore.edit 事务内完成：DataStore 串行执行事务，连续操作不会丢更新。 */
    private suspend fun mutateBookmarks(transform: (List<Bookmark>) -> List<Bookmark>) {
        dataStore.edit { preferences ->
            val current = decodeCommandBookmarks(preferences.read(BOOKMARKS_JSON))
            preferences[BOOKMARKS_JSON.key] = encodeCommandBookmarks(transform(current))
        }
    }

    suspend fun add(name: String, command: String): Bookmark {
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            name = name,
            command = command,
        )
        mutateBookmarks { it + bookmark }
        return bookmark
    }

    suspend fun update(id: String, name: String, command: String) {
        mutateBookmarks { bookmarks ->
            bookmarks.map {
                if (it.id == id) it.copy(name = name, command = command) else it
            }
        }
    }

    suspend fun remove(id: String) {
        mutateBookmarks { bookmarks -> bookmarks.filterNot { it.id == id } }
    }
}

/** 落盘用的存储形态：只保留三个业务字段。 */
@Serializable
private data class StoredBookmark(
    val id: String = "",
    val name: String = "",
    val command: String = "",
)

private val commandBookmarksJson = Json { ignoreUnknownKeys = true }

internal fun encodeCommandBookmarks(bookmarks: List<CommandBookmarks.Bookmark>): String =
    commandBookmarksJson.encodeToString(
        bookmarks.map { bookmark -> StoredBookmark(bookmark.id, bookmark.name, bookmark.command) },
    )

/** 解析书签列表：整段不可解析返回空表；单条无效跳过，重复 id 保留首条，字段值去首尾空白。 */
internal fun decodeCommandBookmarks(raw: String): List<CommandBookmarks.Bookmark> {
    if (raw.isBlank()) return emptyList()
    val array = runCatching { commandBookmarksJson.parseToJsonElement(raw) }.getOrNull() as? JsonArray
        ?: return emptyList()
    val seenIds = HashSet<String>()
    return array.mapNotNull { element ->
        val stored = runCatching {
            commandBookmarksJson.decodeFromJsonElement(StoredBookmark.serializer(), element)
        }.getOrNull() ?: return@mapNotNull null
        val id = stored.id.trim()
        val name = stored.name.trim()
        val command = stored.command.trim()
        if (id.isEmpty() || name.isEmpty() || command.isEmpty() || !seenIds.add(id)) null
        else CommandBookmarks.Bookmark(id = id, name = name, command = command)
    }
}

/**
 * 拼出送入终端的文本：开启自动回车时在末尾补一个 CR。
 *
 * 用 CR 而不是 LF：键盘 Enter 经 KeyHandler 发出的就是 CR；行模式下 ICRNL 会把它转成换行，
 * raw 模式（交互式程序）下也只有 CR 等同回车。
 */
internal fun commandForInput(command: String, autoEnter: Boolean): String =
    if (autoEnter) "$command\r" else command
