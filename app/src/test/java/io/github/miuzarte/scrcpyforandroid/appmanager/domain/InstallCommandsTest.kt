package io.github.miuzarte.scrcpyforandroid.appmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallCommandsTest {

    @Test
    fun `candidates degrade from user zero to plain install`() {
        val candidates = InstallCommands.candidates(listOf("/data/local/tmp/a.apk"))
        assertEquals(3, candidates.size)
        assertTrue(candidates[0].startsWith("pm install -r -t --user 0 "))
        assertTrue(candidates[1].startsWith("pm install -r --user 0 "))
        assertTrue(candidates[2].startsWith("pm install -r "))
        assertFalse(candidates[2].contains("--user"))
    }

    @Test
    fun `split apks are submitted in one command`() {
        val candidates = InstallCommands.candidates(
            listOf("/data/local/tmp/a.apk", "/data/local/tmp/b.apk", "/data/local/tmp/c.apk"),
        )
        candidates.forEach { command ->
            assertTrue(command.contains("'/data/local/tmp/a.apk'"))
            assertTrue(command.contains("'/data/local/tmp/b.apk'"))
            assertTrue(command.contains("'/data/local/tmp/c.apk'"))
        }
    }

    @Test
    fun `success requires the success marker`() {
        assertTrue(InstallCommands.isSuccess("Success\n"))
        assertFalse(InstallCommands.isSuccess("Failure [INSTALL_FAILED_INVALID_APK]"))
    }

    @Test
    fun `a failure marker next to the success marker is not success`() {
        assertFalse(InstallCommands.isSuccess("Success\nFailure [INSTALL_FAILED_MISSING_SPLIT]"))
        assertFalse(
            InstallCommands.isSuccess(
                "Success\njava.lang.IllegalArgumentException: Error: Failed to parse APK file",
            ),
        )
    }

    @Test
    fun `session id comes from the create output`() {
        assertEquals(
            437942325,
            InstallCommands.parseSessionId("Success: created install session [437942325]"),
        )
        assertNull(InstallCommands.parseSessionId("Failure [INSTALL_FAILED_INVALID_APK]"))
    }

    @Test
    fun `session commands address one session`() {
        assertEquals("pm install-create -r -t --user 0", InstallCommands.createCommand())
        // 会话名由分组给出：底包叫 base.apk（设备靠它认底包），远端路径可以带任务标记
        assertEquals(
            "pm install-write 42 'base.apk' '/data/local/tmp/1a2b3c-base.apk'",
            InstallCommands.writeCommand(42, "base.apk", "/data/local/tmp/1a2b3c-base.apk"),
        )
        assertEquals("pm install-commit 42", InstallCommands.commitCommand(42))
        assertEquals("pm install-abandon 42", InstallCommands.abandonCommand(42))
    }

    @Test
    fun `cleanup removes every remote path`() {
        val cleanup = InstallCommands.cleanupCommand(listOf("/tmp/a.apk", "/tmp/b.apk"))
        assertEquals("rm -f '/tmp/a.apk' '/tmp/b.apk'", cleanup)
    }
}
