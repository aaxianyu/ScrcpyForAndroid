package io.github.miuzarte.scrcpyforandroid.appmanager.domain

/**
 * `pm install` 的会话命令与降级序列，以及安装结果判定。
 *
 * 分包是一个安装单位：全部路径要一次提交，只装 base 会以 `INSTALL_FAILED_MISSING_SPLIT` 失败
 * （实测）。会话形态先 create 再逐个 write、最后 commit，文件全部写进同一会话才提交；
 * 没有会话命令或会话中途失败时，退到「一条 `pm install` 带全部路径」的降级序列。
 *
 * 判定只看文本标记：shell 的 stdout 与 stderr 合流、拿不到退出状态，而失败原因可能与成功字样
 * 出现在同一个字符串里。「到底装上了没有」由调用方回读 `pm path` 盖章。
 */
object InstallCommands {

    /** 会话创建：后续 write/commit 都挂在这个会话上。 */
    fun createCommand(): String = "pm install-create -r -t --user 0"

    /** 往会话里写一个 APK。会话里的 split 名与远端路径分开给：设备按名字认 base（须叫 `base.apk`）。 */
    fun writeCommand(sessionId: Int, splitName: String, remotePath: String): String =
        "pm install-write $sessionId '$splitName' '$remotePath'"

    fun commitCommand(sessionId: Int): String = "pm install-commit $sessionId"

    /** 写失败要显式丢弃会话，否则残留会话会占着包管理器直到超时。 */
    fun abandonCommand(sessionId: Int): String = "pm install-abandon $sessionId"

    /** 从 `Success: created install session [123]` 取会话号；取不到就不能继续会话安装。 */
    fun parseSessionId(output: String): Int? {
        if (!output.contains("Success")) return null
        val start = output.indexOf('[')
        if (start < 0) return null
        val end = output.indexOf(']', start + 1)
        if (end < 0) return null
        return output.substring(start + 1, end).trim().toIntOrNull()
    }

    /** 会话不可用时的降级序列：一条命令提交全部路径，参数逐级放宽。 */
    fun candidates(remotePaths: List<String>): List<String> {
        require(remotePaths.isNotEmpty()) { "install paths must not be empty" }
        val quoted = remotePaths.joinToString(" ") { "'$it'" }
        return listOf(
            "pm install -r -t --user 0 $quoted",
            "pm install -r --user 0 $quoted",
            "pm install -r $quoted",
        )
    }

    /**
     * 成功判定：出现 `Success` 且没有任何失败标记。
     * 失败原因可能紧跟在成功字样之后，只找 `Success` 会把这类输出当成装上了。
     */
    fun isSuccess(output: String): Boolean =
        output.contains("Success") && FAILURE_MARKERS.none { output.contains(it) }

    /** 远端临时文件清理：无论成败都逐个删除。 */
    fun cleanupCommand(remotePaths: List<String>): String {
        val quoted = remotePaths.joinToString(" ") { "'$it'" }
        return "rm -f $quoted"
    }

    private val FAILURE_MARKERS = listOf("Failure", "Error", "Exception")
}
