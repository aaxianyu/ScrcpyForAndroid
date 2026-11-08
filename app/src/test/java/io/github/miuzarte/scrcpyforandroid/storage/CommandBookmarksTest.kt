package io.github.miuzarte.scrcpyforandroid.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandBookmarksTest {

    @Test
    fun bookmarksRoundTripPreservesOrderAndContent() {
        val bookmarks = listOf(
            CommandBookmarks.Bookmark(id = "a", name = "列出文件", command = "ls -al"),
            CommandBookmarks.Bookmark(id = "b", name = "quotes", command = "echo \"a\\b\" | grep a # 注释"),
            CommandBookmarks.Bookmark(id = "c", name = "newline", command = "printf '1\\n2\\n'"),
        )
        assertEquals(bookmarks, decodeCommandBookmarks(encodeCommandBookmarks(bookmarks)))
    }

    @Test
    fun blankOrMalformedPayloadDecodesToEmpty() {
        for (raw in listOf("", "   ", "not json", "{\"id\":\"1\"}", "null", "42")) {
            assertEquals(raw, emptyList<CommandBookmarks.Bookmark>(), decodeCommandBookmarks(raw))
        }
    }

    @Test
    fun invalidEntriesAreSkippedWithoutDroppingValidOnes() {
        val raw = """
            [
                {"id":"1","name":"ok","command":"whoami"},
                {"name":"missing id","command":"ls"},
                {"id":"2","command":"pwd"},
                {"id":"3","name":"blank command","command":"   "},
                "not an object",
                {"id":"4","name":"keep","command":"id","extra":"ignored"}
            ]
        """.trimIndent()
        assertEquals(
            listOf(
                CommandBookmarks.Bookmark(id = "1", name = "ok", command = "whoami"),
                CommandBookmarks.Bookmark(id = "4", name = "keep", command = "id"),
            ),
            decodeCommandBookmarks(raw),
        )
    }

    @Test
    fun fieldValuesAreTrimmedOnDecode() {
        val raw = """[{"id":" 1 ","name":" name ","command":" cmd "}]"""
        assertEquals(
            listOf(CommandBookmarks.Bookmark(id = "1", name = "name", command = "cmd")),
            decodeCommandBookmarks(raw),
        )
    }

    @Test
    fun encodeOfEmptyListIsEmptyArray() {
        assertEquals("[]", encodeCommandBookmarks(emptyList()))
    }

    @Test
    fun duplicateIdsKeepFirstEntryOnly() {
        val raw = """
            [
                {"id":"1","name":"first","command":"whoami"},
                {"id":"1","name":"second","command":"id"},
                {"id":"2","name":"other","command":"pwd"}
            ]
        """.trimIndent()
        assertEquals(
            listOf(
                CommandBookmarks.Bookmark(id = "1", name = "first", command = "whoami"),
                CommandBookmarks.Bookmark(id = "2", name = "other", command = "pwd"),
            ),
            decodeCommandBookmarks(raw),
        )
    }

    @Test
    fun roundTripPreservesLongTextAndSurrogatePairs() {
        val bookmarks = listOf(
            CommandBookmarks.Bookmark(id = "long", name = "长文本", command = "echo " + "x".repeat(4096)),
            CommandBookmarks.Bookmark(
                id = "emoji",
                name = "表情 🚀📦",
                command = "echo '👨👩👧 👩🏽‍🚀 𝄞 𝐀 𠮷'",
            ),
        )
        assertEquals(bookmarks, decodeCommandBookmarks(encodeCommandBookmarks(bookmarks)))
    }

    @Test
    fun commandForInputKeepsTextUnlessAutoEnter() {
        val command = "wm size"
        assertEquals(command, commandForInput(command, autoEnter = false))
        assertEquals("$command\r", commandForInput(command, autoEnter = true))
    }
}
