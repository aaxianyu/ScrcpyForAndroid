package io.github.miuzarte.scrcpyforandroid.appmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRunningTest {

    // ------------------------------------------------------------ 角标互斥

    @Test
    fun `disabled wins over running`() {
        assertEquals(AppBadge.Disabled, appBadge(isEnabled = false, isRunning = true))
    }

    @Test
    fun `running shows when enabled and running`() {
        assertEquals(AppBadge.Running, appBadge(isEnabled = true, isRunning = true))
    }

    @Test
    fun `enabled and idle shows nothing`() {
        assertEquals(AppBadge.None, appBadge(isEnabled = true, isRunning = false))
    }

    @Test
    fun `disabled and idle shows disabled`() {
        assertEquals(AppBadge.Disabled, appBadge(isEnabled = false, isRunning = false))
    }

    // ------------------------------------------------------------ ps -A 解析

    /** 真机 `ps -A` 的前几行形状（XT2251-1 / Android 14）。 */
    private val realSample = """
        USER           PID  PPID     VSZ    RSS WCHAN            ADDR S NAME
        root             1     0 10813440  3412 0                   0 S init
        u0_a123       1234   567 12345678 98765 0                   0 S com.example.app
        u0_a123       1235   567 12345678 98765 0                   0 S com.example.app:remote
        u0_a124       4321   567 12345678 54321 0                   0 S com.other.app
    """.trimIndent()

    @Test
    fun `collects package names from ps output`() {
        val packages = runningPackagesFromPs(realSample)
        assertEquals(setOf("init", "com.example.app", "com.other.app"), packages)
    }

    @Test
    fun `sub process folds into its package`() {
        val packages = runningPackagesFromPs(realSample)
        // com.example.app:remote 已归到 com.example.app，不会单独出现
        assertEquals(true, packages?.contains("com.example.app"))
        assertEquals(false, packages?.contains("com.example.app:remote"))
    }

    @Test
    fun `empty output means no running packages not unknown`() {
        // 表头在、只是没有数据行：确实没有应用在跑（空集合 ≠ null）
        assertEquals(emptySet<String>(), runningPackagesFromPs("USER PID PPID VSZ RSS WCHAN ADDR S NAME"))
    }

    @Test
    fun `missing header returns null`() {
        // 读不出进程表：返回 null，界面据此不置灰强制停止
        assertNull(runningPackagesFromPs("this is not a process table"))
        assertNull(runningPackagesFromPs(""))
    }

    @Test
    fun `header without name column returns null`() {
        assertNull(runningPackagesFromPs("USER PID PPID VSZ RSS WCHAN ADDR S"))
    }

    @Test
    fun `repeated header row is skipped`() {
        val output = """
            USER PID PPID VSZ RSS WCHAN ADDR S NAME
            USER PID PPID VSZ RSS WCHAN ADDR S NAME
            u0_a1 100 1 1 1 0 0 S com.a
        """.trimIndent()
        assertEquals(setOf("com.a"), runningPackagesFromPs(output))
    }

    @Test
    fun `blank lines and non numeric pid rows are skipped`() {
        val output = "USER PID PPID VSZ RSS WCHAN ADDR S NAME\n\n\n   \nu0_a1 100 1 1 1 0 0 S com.a\nbroken row here"
        assertEquals(setOf("com.a"), runningPackagesFromPs(output))
    }

    @Test
    fun `short rows do not throw`() {
        // 输出被截断或长进程名换行时会出现只有一两格的短行：必须跳过而不是越界崩掉
        val output = "USER PID PPID VSZ RSS WCHAN ADDR S NAME\nu0_a1\nu0_a2 200\nu0_a3 300 1 1 1 0 0 S com.a"
        assertEquals(setOf("com.a"), runningPackagesFromPs(output))
    }

    @Test
    fun `name with spaces keeps whole name`() {
        // NAME 是最后一列，进程名里可能带空格
        val output = "USER PID PPID VSZ RSS WCHAN ADDR S NAME\nu0_a1 100 1 1 1 0 0 S some app name"
        assertEquals(setOf("some app name"), runningPackagesFromPs(output))
    }
}
