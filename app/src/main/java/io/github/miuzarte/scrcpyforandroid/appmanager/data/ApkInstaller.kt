package io.github.miuzarte.scrcpyforandroid.appmanager.data

import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppCommands
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.InstallCommands
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * APK 安装封装。
 *
 * 逐个 APK 推送到被控设备临时目录（split 分包全部推送），再走会话安装
 * （`pm install-create` → 逐个 `install-write` → `install-commit`）一次提交全部路径；
 * 设备没有会话命令时退到「一条 `pm install` 带全部路径」的降级序列。
 * 临时文件无论成败都逐个 `rm -f` 清理。
 *
 * 成败判定分两种：知道包名时（本机应用安装、容器安装）装完回读 `pm path`，包在才算成功；
 * 包名未知时（单个 apk 直接推流）只看设备输出里的 `Success`。
 */
class ApkInstaller {

    suspend fun install(
        apkPaths: List<String>,
        expectedPackage: String? = null,
        onProgress: ((pushedBytes: Long, totalBytes: Long) -> Unit)? = null,
        onCommitting: (() -> Unit)? = null,
    ): InstallResult = withContext(Dispatchers.IO) {
        if (apkPaths.isEmpty()) return@withContext InstallResult.Failure("no apk to install")
        // 会话里的 split 名决定谁是 base：分组把底包排在首位，它必须叫 base.apk；
        // 远端路径带上本次任务标记，与其他并发任务的同名文件分开
        val token = newToken()
        val taken = mutableSetOf(BASE_APK_NAME)
        val targets = apkPaths.mapIndexed { index, localPath ->
            val name = if (index == 0) BASE_APK_NAME else sessionName(localPath, index, taken)
            RemoteTarget(remotePath = "$REMOTE_DIR/$token-$name", sessionName = name)
        }
        val totalBytes = apkPaths.sumOf { File(it).length().coerceAtLeast(0L) }
        if (totalBytes <= 0L) {
            android.util.Log.w("ApkInstaller", "install: no readable bytes for $apkPaths, falling back to pushed bytes")
        }
        var pushedBytes = 0L
        val job = coroutineContext[Job]
        try {
            apkPaths.forEachIndexed { index, localPath ->
                val file = File(localPath)
                require(file.exists()) { "apk not found: $localPath" }
                file.inputStream().use { input ->
                    NativeAdbService.push(input, targets[index].remotePath) { len ->
                        // 分片边界检查一次：进行中的 64 KB 写不会被打断
                        job?.ensureActive()
                        pushedBytes += len
                        onProgress?.invoke(pushedBytes, maxOf(totalBytes, pushedBytes))
                    }
                }
            }
            onProgress?.invoke(pushedBytes, maxOf(totalBytes, pushedBytes))
            onCommitting?.invoke()
            verdict(expectedPackage, submit(targets))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            InstallResult.Failure(error.message ?: error.javaClass.simpleName)
        } finally {
            withContext(NonCancellable) {
                runCatching {
                    NativeAdbService.shell(
                        InstallCommands.cleanupCommand(targets.map { it.remotePath }),
                        CLEANUP_TIMEOUT_MS,
                    )
                }
            }
        }
    }

    /**
     * 单个 apk 从调用方的输入流直接推给设备。
     *
     * 流安装读不到归档清单，也就拿不到包名，成败以设备输出为准。
     * [totalBytes] 是界面算进度用的分母，未知时传 0。
     */
    suspend fun installStream(
        input: InputStream,
        totalBytes: Long,
        onProgress: ((pushedBytes: Long, totalBytes: Long) -> Unit)? = null,
        onCommitting: (() -> Unit)? = null,
    ): InstallResult = withContext(Dispatchers.IO) {
        val target = RemoteTarget(
            remotePath = "$REMOTE_DIR/${newToken()}-$BASE_APK_NAME",
            sessionName = BASE_APK_NAME,
        )
        var pushedBytes = 0L
        val job = coroutineContext[Job]
        try {
            NativeAdbService.push(input, target.remotePath) { len ->
                // 分片边界检查一次：进行中的 64 KB 写不会被打断
                job?.ensureActive()
                pushedBytes += len
                onProgress?.invoke(pushedBytes, maxOf(totalBytes, pushedBytes))
            }
            onProgress?.invoke(pushedBytes, maxOf(totalBytes, pushedBytes))
            onCommitting?.invoke()
            verdict(null, submit(listOf(target)))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            InstallResult.Failure(error.message ?: error.javaClass.simpleName)
        } finally {
            withContext(NonCancellable) {
                runCatching {
                    NativeAdbService.shell(InstallCommands.cleanupCommand(listOf(target.remotePath)), CLEANUP_TIMEOUT_MS)
                }
            }
        }
    }

    /** 会话优先；会话这次没成（或压根没有该命令）再走降级序列，返回最后一次输出。 */
    private suspend fun submit(targets: List<RemoteTarget>): String {
        val remotePaths = targets.map { it.remotePath }
        val session = try {
            installInSession(targets)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            null
        }
        if (session != null && InstallCommands.isSuccess(session)) return session
        var lastOutput = session.orEmpty()
        for (command in InstallCommands.candidates(remotePaths)) {
            val output = NativeAdbService.shell(command, SHELL_TIMEOUT_MS)
            if (InstallCommands.isSuccess(output)) return output
            lastOutput = output
        }
        return lastOutput
    }

    /** create → write ×N → commit；写失败就丢弃会话并把那一步的输出交回去。 */
    private suspend fun installInSession(targets: List<RemoteTarget>): String {
        val create = NativeAdbService.shell(InstallCommands.createCommand(), SHELL_TIMEOUT_MS)
        val sessionId = InstallCommands.parseSessionId(create) ?: return create
        var output = create
        for (target in targets) {
            output = NativeAdbService.shell(
                InstallCommands.writeCommand(sessionId, target.sessionName, target.remotePath),
                SHELL_TIMEOUT_MS,
            )
            if (!InstallCommands.isSuccess(output)) {
                runCatching { NativeAdbService.shell(InstallCommands.abandonCommand(sessionId), SHELL_TIMEOUT_MS) }
                return output
            }
        }
        return NativeAdbService.shell(InstallCommands.commitCommand(sessionId), SHELL_TIMEOUT_MS)
    }

    /**
     * 定成败：知道包名时回读 `pm path` 与文本判定都要为真；包名未知时只有文本判定。
     * 回读本身失败（链路异常）时按文本判定算。
     */
    private suspend fun verdict(expectedPackage: String?, output: String): InstallResult {
        val textSaysSuccess = InstallCommands.isSuccess(output)
        if (expectedPackage == null) {
            return if (textSaysSuccess) InstallResult.Success else InstallResult.Failure(failureReason(output))
        }
        val installed = try {
            isInstalled(expectedPackage)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            null
        }
        if (textSaysSuccess && installed != false) return InstallResult.Success
        return InstallResult.Failure(
            if (textSaysSuccess) {
                "install did not take effect: $expectedPackage"
            } else {
                failureReason(output)
            },
        )
    }

    private fun failureReason(output: String): String = output.trim().ifEmpty { "install failed" }

    /** 回读口径与卸载复核一致：`pm path <包名>` 有输出才算在设备上。 */
    private suspend fun isInstalled(packageName: String): Boolean =
        NativeAdbService.shell(AppCommands.apkPath(packageName), SHELL_TIMEOUT_MS)
            .lineSequence()
            .any { it.trim().startsWith("package:") }

    /** 同一批里重名的部件（不同目录的同名 apk）要错开，否则后一次 install-write 覆盖前一次。 */
    private fun sessionName(localPath: String, index: Int, taken: MutableSet<String>): String {
        val name = File(localPath).name.takeIf { it.endsWith(".apk") } ?: "split_$index.apk"
        if (taken.add(name)) return name
        val alt = "split_${index}_$name"
        taken.add(alt)
        return alt
    }

    private fun newToken(): String = "${System.currentTimeMillis().toString(16)}${nextToken.incrementAndGet().toString(16)}"

    private val nextToken = java.util.concurrent.atomic.AtomicInteger(0)

    /** 一次推送的远端路径与它在会话里的 split 名。 */
    private data class RemoteTarget(val remotePath: String, val sessionName: String)

    sealed interface InstallResult {
        data object Success : InstallResult
        data class Failure(val reason: String) : InstallResult
    }

    private companion object {
        const val REMOTE_DIR = "/data/local/tmp"

        /** 会话里 base 分包的固定名：install-write 的 split 名就是靠它认出 base。 */
        const val BASE_APK_NAME = "base.apk"
        /** 会话提交、降级安装与回读都要等目标机；目标机重启或 adb 掉线时这些 shell 会一直不返回，所以给上限。 */
        const val SHELL_TIMEOUT_MS = 120_000L
        /** 清理只是 `rm -f`，超过这个时间说明链路已经不可用。 */
        const val CLEANUP_TIMEOUT_MS = 15_000L
    }
}

/** 供文件安装链路复用：把文档流落地成本地临时文件再走同一套安装机器。 */
internal fun copyToTempFile(input: InputStream, directory: File, name: String): File {
    directory.mkdirs()
    val target = File(directory, name)
    input.use { source ->
        target.outputStream().use { output -> source.copyTo(output) }
    }
    return target
}
