package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class CommandBookmark(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val command: String,
)

class CommandBookmarkStore(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "command_bookmarks.json")

    private val _bookmarks = MutableStateFlow<List<CommandBookmark>>(emptyList())
    val bookmarks: StateFlow<List<CommandBookmark>> = _bookmarks.asStateFlow()

    suspend fun load() {
        withContext(Dispatchers.IO) {
            val list = runCatching {
                val text = file.readText(Charsets.UTF_8)
                val array = JSONArray(text)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    CommandBookmark(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        command = obj.getString("command"),
                    )
                }
            }.getOrDefault(emptyList())
            _bookmarks.value = list
        }
    }

    private suspend fun persist(bookmarks: List<CommandBookmark>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val array = JSONArray()
                bookmarks.forEach { b ->
                    array.put(JSONObject().apply {
                        put("id", b.id)
                        put("name", b.name)
                        put("command", b.command)
                    })
                }
                file.writeText(array.toString(2), Charsets.UTF_8)
            }
        }
    }

    suspend fun add(bookmark: CommandBookmark) {
        val updated = _bookmarks.value + bookmark
        _bookmarks.value = updated
        persist(updated)
    }

    suspend fun update(id: String, name: String, command: String) {
        val updated = _bookmarks.value.map {
            if (it.id == id) it.copy(name = name, command = command) else it
        }
        _bookmarks.value = updated
        persist(updated)
    }

    suspend fun remove(id: String) {
        val updated = _bookmarks.value.filter { it.id != id }
        _bookmarks.value = updated
        persist(updated)
    }
}
