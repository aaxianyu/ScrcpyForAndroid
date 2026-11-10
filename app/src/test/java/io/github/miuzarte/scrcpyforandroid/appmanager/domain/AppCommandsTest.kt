package io.github.miuzarte.scrcpyforandroid.appmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCommandsTest {

    private val pkg = "com.example.app"

    @Test
    fun `commands quote the package name`() {
        assertEquals("am force-stop '$pkg'", AppCommands.forceStop(pkg))
        assertEquals("pm disable-user '$pkg'", AppCommands.disable(pkg))
        assertEquals("pm enable '$pkg'", AppCommands.enable(pkg))
        assertEquals("pm clear '$pkg'", AppCommands.clearData(pkg))
        assertEquals("pm uninstall --user 0 '$pkg'", AppCommands.uninstall(pkg))
        assertEquals("pm path '$pkg'", AppCommands.apkPath(pkg))
    }

    @Test
    fun `open and export have no shell command`() {
        assertEquals(null, AppCommands.command(AppAction.OPEN, pkg))
        assertEquals(null, AppCommands.command(AppAction.EXPORT_APK, pkg))
        assertEquals(AppCommands.uninstall(pkg), AppCommands.command(AppAction.UNINSTALL, pkg))
    }

    @Test
    fun `force stop always counts as delivered`() {
        // am force-stop 无 stdout 可依赖，命令完成即视为已下发
        assertTrue(AppCommands.isSuccess(AppAction.FORCE_STOP, pkg, ""))
    }

    @Test
    fun `disable and enable require the state echo`() {
        assertTrue(
            AppCommands.isSuccess(
                AppAction.DISABLE,
                pkg,
                "Package $pkg new state: disabled",
            ),
        )
        assertFalse(AppCommands.isSuccess(AppAction.DISABLE, pkg, "Security exception"))
        assertTrue(
            AppCommands.isSuccess(
                AppAction.ENABLE,
                pkg,
                "Package $pkg new state: enabled",
            ),
        )
        assertFalse(AppCommands.isSuccess(AppAction.ENABLE, pkg, "Package $pkg new state: disabled"))
    }

    @Test
    fun `clear data and uninstall require success marker`() {
        assertTrue(AppCommands.isSuccess(AppAction.CLEAR_DATA, pkg, "Success"))
        assertFalse(AppCommands.isSuccess(AppAction.CLEAR_DATA, pkg, "Failed"))
        assertTrue(AppCommands.isSuccess(AppAction.UNINSTALL, pkg, "Success"))
        assertFalse(AppCommands.isSuccess(AppAction.UNINSTALL, pkg, "Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]"))
    }

    @Test
    fun `pending release matches the spec table`() {
        assertEquals(PendingRelease.ON_REFRESH, AppAction.UNINSTALL.pendingRelease)
        assertEquals(PendingRelease.ON_REFRESH, AppAction.DISABLE.pendingRelease)
        assertEquals(PendingRelease.ON_REFRESH, AppAction.ENABLE.pendingRelease)
        assertEquals(PendingRelease.ON_COMMAND, AppAction.FORCE_STOP.pendingRelease)
        assertEquals(PendingRelease.ON_COMMAND, AppAction.CLEAR_DATA.pendingRelease)
        assertEquals(PendingRelease.ON_COMMAND, AppAction.OPEN.pendingRelease)
    }

    @Test
    fun `list effects match the spec table`() {
        assertEquals(ListEffect.REMOVE_ROW, AppAction.UNINSTALL.listEffect)
        // 停用后应用仍在设备上，只是不可用：行留在列表里翻成「已停用」，不摘行
        assertEquals(ListEffect.FLIP_ENABLED, AppAction.DISABLE.listEffect)
        assertEquals(ListEffect.FLIP_ENABLED, AppAction.ENABLE.listEffect)
        // 强制停止没有回显可判：重拉一次运行集合来定「运行中」标签
        assertEquals(ListEffect.REFRESH_RUNNING, AppAction.FORCE_STOP.listEffect)
        assertEquals(ListEffect.NONE, AppAction.CLEAR_DATA.listEffect)
        // 打开不走 shell 命令，运行态重拉在 executeSingle 的 OPEN 分支里做
        assertEquals(ListEffect.NONE, AppAction.OPEN.listEffect)
        assertEquals(ListEffect.NONE, AppAction.EXPORT_APK.listEffect)
    }
}
