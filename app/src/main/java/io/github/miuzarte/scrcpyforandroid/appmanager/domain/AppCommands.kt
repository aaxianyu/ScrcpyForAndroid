package io.github.miuzarte.scrcpyforandroid.appmanager.domain

/**
 * 应用管理域内的单应用操作。
 */
enum class AppAction {
    OPEN,
    FORCE_STOP,
    DISABLE,
    ENABLE,
    CLEAR_DATA,
    EXPORT_APK,
    UNINSTALL,
    ;

    /**
     * 行冻结语义：
     * - [PendingRelease.ON_REFRESH]：等一次完整列表刷新后解冻（卸载 / 停用 / 启用）
     * - [PendingRelease.ON_COMMAND]：命令返回即解冻（强制停止 / 清除数据 / 打开）
     */
    val pendingRelease: PendingRelease
        get() = when (this) {
            UNINSTALL, DISABLE, ENABLE -> PendingRelease.ON_REFRESH
            else -> PendingRelease.ON_COMMAND
        }

    /** 成功后的本地列表副作用。 */
    val listEffect: ListEffect
        get() = when (this) {
            UNINSTALL -> ListEffect.REMOVE_ROW
            DISABLE, ENABLE -> ListEffect.FLIP_ENABLED
            else -> ListEffect.NONE
        }
}

enum class PendingRelease { ON_REFRESH, ON_COMMAND }

enum class ListEffect { NONE, REMOVE_ROW, FLIP_ENABLED }

/** shell 参数单引号包裹（包名不含单引号，双写转义兜底）。 */
internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/**
 * 写操作的命令构造与成功判定。
 *
 * 打开走既有启动通道（[NativeAdbService][io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService.startApp]），
 * 导出 APK 先取 `pm path` 再拉取，二者不在此构造 shell。
 */
object AppCommands {

    fun forceStop(packageName: String): String = "am force-stop ${shellQuote(packageName)}"

    fun disable(packageName: String): String = "pm disable-user ${shellQuote(packageName)}"

    fun enable(packageName: String): String = "pm enable ${shellQuote(packageName)}"

    fun clearData(packageName: String): String = "pm clear ${shellQuote(packageName)}"

    fun uninstall(packageName: String): String = "pm uninstall --user 0 ${shellQuote(packageName)}"

    fun apkPath(packageName: String): String = "pm path ${shellQuote(packageName)}"

    /** 远端文件字节数（`stat -c %s`）；取不到时返回 null，调用方按「大小未知」处理。 */
    fun fileSize(remotePath: String): String = "stat -c %s ${shellQuote(remotePath)} 2>/dev/null"

    fun command(action: AppAction, packageName: String): String? = when (action) {
        AppAction.FORCE_STOP -> forceStop(packageName)
        AppAction.DISABLE -> disable(packageName)
        AppAction.ENABLE -> enable(packageName)
        AppAction.CLEAR_DATA -> clearData(packageName)
        AppAction.UNINSTALL -> uninstall(packageName)
        AppAction.OPEN, AppAction.EXPORT_APK -> null
    }

    /**
     * 成功判定：
     * - 强制停止无成功输出可判，命令完成即视为已下发；
     * - 停用 / 启用看 `new state:` 回显；卸载 / 清除数据看 `Success`。
     */
    fun isSuccess(action: AppAction, packageName: String, output: String): Boolean =
        when (action) {
            AppAction.FORCE_STOP -> true
            AppAction.DISABLE -> output.contains("Package $packageName new state: disabled")
            AppAction.ENABLE -> output.contains("Package $packageName new state: enabled")
            AppAction.CLEAR_DATA, AppAction.UNINSTALL -> output.contains("Success")
            AppAction.OPEN, AppAction.EXPORT_APK -> true
        }
}

/**
 * 批量执行的逐包计数。
 *
 * 必须逐包记录成败：聚合提示「已成功 %d 个，%d 个失败：%s」的成功数、失败数
 * 与最后一个失败原因都来自这里。
 */
class BatchTally {
    var successCount: Int = 0
        private set
    var failureCount: Int = 0
        private set
    var lastError: String? = null
        private set

    fun recordSuccess() {
        successCount++
    }

    fun recordFailure(error: String?) {
        failureCount++
        val message = error?.trim().orEmpty()
        if (message.isNotEmpty()) lastError = message
    }

    val total: Int get() = successCount + failureCount
}
