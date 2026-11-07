package io.github.miuzarte.scrcpyforandroid.appmanager.data

import java.io.File
import java.util.zip.ZipOutputStream

/**
 * 一个待打包的 APK 部件：zip 内的条目名、远端路径、远端字节数。
 *
 * split 应用有多个部件（base.apk 与各 split_*.apk），它们必须一起进同一个产物，
 * 用户解压后才能一次装齐。
 */
data class ZipExportPart(
    val entryName: String,
    val remotePath: String,
    /** 远端文件的字节数；0 表示未取到，此时不做长度校验。 */
    val expectedBytes: Long,
)

/**
 * 一个待导出的应用。
 *
 * [expectedBytes] 是该应用全部部件之和；0 表示没取到，此时整批进度按不确定处理。
 * 部件清单见 [parts]；连路径都没取到时 [parts] 为空、原因在 [error]。
 */
data class ZipExportSource(
    val packageName: String,
    val label: String,
    val expectedBytes: Long,
    val parts: List<ZipExportPart>,
    val error: String? = null,
    /**
     * 批量导出里每个应用单独一层：单部件落成 `<包名>.apk`，多部件套一个内层 `<包名>.apks`。
     *
     * 必须分层的原因：各应用的 `base.apk` 与 `split_config.*.apk` 文件名完全一样，平铺会互相顶掉。
     */
    val grouped: Boolean = false,
)

/** 一次批量导出的成败计数与字节进度。 */
class ZipExportTally {
    var successCount: Int = 0
        private set
    var failureCount: Int = 0
        private set
    var lastError: String? = null
        private set

    /** 整批的字节分母：各包 [ZipExportSource.expectedBytes] 之和。 */
    var totalBytes: Long = 0L
        private set

    /** 已完成字节（分母口径，含失败项按其应有字节数推进）。 */
    var doneBytes: Long = 0L
        private set

    /** 已上报字节的高水位：任一分支算出的值低于它时沿用它，保证进度只增不减。 */
    var reportedBytes: Long = 0L
        private set

    /** 用户中断过。中断时整份产物作废，不作为部分成功保留。 */
    var cancelled: Boolean = false
        private set

    /**
     * 条目写入阶段失败过（从 `putNextEntry` 到 `closeEntry` 之间抛错）。
     *
     * 这种失败无法回滚：条目长度已经定死在中央目录之前的位置，补写或截断都会产出一个
     * 看似正常实则损坏的 APK。整份产物因此按作废处理。
     */
    var entryWriteFailed: Boolean = false
        private set

    /** 收尾策略：条目写入失败 / 用户中断 / 一个条目都没成 → 撤回落盘条目；部分成功 → 保留。 */
    val keepTarget: Boolean
        get() = !cancelled && !entryWriteFailed && successCount > 0

    internal fun recordSuccess() {
        successCount++
    }

    internal fun recordFailure(reason: String) {
        failureCount++
        if (reason.isNotEmpty()) lastError = reason
    }

    /** 记一次字节推进，返回应上报的值（高水位）。 */
    internal fun advance(candidate: Long): Long {
        doneBytes = candidate
        reportedBytes = maxOf(reportedBytes, candidate)
        return reportedBytes
    }

    internal fun markCancelled() {
        cancelled = true
    }

    internal fun markEntryWriteFailed() {
        entryWriteFailed = true
    }

    internal fun initTotal(value: Long) {
        totalBytes = value
    }
}

/**
 * 批量 ZIP 的组装核心：逐部件边拉边写条目，字节数不等就抛。
 *
 * 分组来源（[ZipExportSource.grouped]）的多个部件先在临时文件里拼成内层 zip，再整份作为外层
 * 的一个条目写进去：内层 zip 的中央目录写完，才算得上一份完整条目。
 *
 * 这里只碰 [File] 与 [ZipOutputStream]，不引用任何 Android 类型，因此可在纯 JVM 下
 * 完整覆盖。外部提供三件事：把某部件拉进条目的输出流（[fetch]，挂起因为传输层是挂起调用）、
 * 每一步的进度上报（[onProgress]），以及取消检查（[ensureActive]，抛
 * [kotlinx.coroutines.CancellationException] 表示中断）。
 *
 * 条目一开写就回滚不掉：先落临时文件、搬完再写条目的写法，会在搬运那几秒里没有进度可报
 * （253.9 MB 的 base 实测停住数秒），所以拉取直接写进条目。产物因此按整份成败：长度不符
 * 或写条目失败（磁盘满等）都留下半截条目，只能按作废处理（见 [ZipExportTally.keepTarget]），
 * 调用方据此撤下落盘条目，不会把损坏的压缩包交给用户。
 */
suspend fun assembleZip(
    sources: List<ZipExportSource>,
    zip: ZipOutputStream,
    tempDir: File,
    fetch: suspend (part: ZipExportPart, dest: java.io.OutputStream, onBytes: (Long) -> Unit) -> Unit,
    onProgress: (doneBytes: Long, totalBytes: Long, index: Int, count: Int, label: String) -> Unit,
    ensureActive: () -> Unit,
): ZipExportTally {
    val tally = ZipExportTally()
    tally.initTotal(sources.sumOf { it.expectedBytes })
    tempDir.mkdirs()

    sources.forEachIndexed { index, source ->
        ensureActive()
        onProgress(tally.reportedBytes, tally.totalBytes, index + 1, sources.size, source.label)

        // 本包的起始偏移：失败时也要按它推进分母，否则总进度停在中段
        val base = tally.doneBytes
        val unresolved = source.error ?: if (source.parts.isEmpty()) NO_APK_PART else null
        if (unresolved != null) {
            tally.recordFailure(unresolved)
            onProgress(tally.advance(base + source.expectedBytes), tally.totalBytes, index + 1, sources.size, source.label)
            return@forEachIndexed
        }
        try {
            var pulled = 0L
            // 内层 zip 的文件名固定，只有真要套内层时才创建它
            val nestedFile = File(tempDir, "${source.packageName}.apks")
            val nested = if (source.grouped && source.parts.size > 1) {
                java.util.zip.ZipOutputStream(java.io.FileOutputStream(nestedFile))
            } else {
                null
            }
            try {
                source.parts.forEach { part ->
                    // 分组来源只有一个部件时例外：部件自带的条目名是远端文件名，看不出属于哪个应用
                    val entryName =
                        if (nested == null && source.grouped) "${source.packageName}.apk" else part.entryName
                    val target = nested ?: zip
                    // 拉取直接写进条目：先落临时文件再搬一次，搬运那几秒里没有任何进度可报。条目一开写
                    // 就回滚不掉，所以这一段抛错（长度不符、磁盘满等）按整份产物作废，由调用方撤回落盘条目
                    try {
                        target.putNextEntry(java.util.zip.ZipEntry(entryName))
                        val metered = MeteredOutputStream(target, part.expectedBytes)
                        try {
                            fetch(part, metered) { len ->
                                ensureActive()
                                pulled += len
                                onProgress(tally.advance(base + pulled), tally.totalBytes, index + 1, sources.size, source.label)
                            }
                        } finally {
                            target.closeEntry()
                        }
                        if (part.expectedBytes > 0L && metered.bytes != part.expectedBytes) {
                            throw java.io.IOException("size mismatch: got ${metered.bytes}, want ${part.expectedBytes}")
                        }
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (write: Exception) {
                        tally.markEntryWriteFailed()
                        throw write
                    }
                }
                if (nested != null) {
                    nested.close()
                    try {
                        zip.putNextEntry(java.util.zip.ZipEntry("${source.packageName}.apks"))
                        nestedFile.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (write: Exception) {
                        tally.markEntryWriteFailed()
                        throw write
                    }
                }
            } finally {
                // 出错路径上也要把内层 zip 关掉（成功路径已关过，重复关闭是空操作），临时文件一起清
                if (nested != null) {
                    runCatching { nested.close() }
                    nestedFile.delete()
                }
            }
            tally.recordSuccess()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            tally.markCancelled()
            throw cancel
        } catch (error: Exception) {
            tally.recordFailure(error.message ?: error.javaClass.simpleName)
            // 失败项的分母也推进，避免总进度停在中段
            onProgress(tally.advance(base + source.expectedBytes), tally.totalBytes, index + 1, sources.size, source.label)
        }
    }
    return tally
}

/**
 * 边写边计数的包装流：字节数交给进度，同时用来核对部件是否与声明大小一致。
 *
 * 声明大小已知却已经写超时立刻抛——拉到 1.5 GB 才发现对不上，等于白等一整轮传输。
 * [close] 是空操作：底下的 zip 条目由 assembleZip 收尾，跟着关会把整份压缩包结束掉。
 */
private class MeteredOutputStream(
    private val target: java.io.OutputStream,
    private val expected: Long,
) : java.io.OutputStream() {

    var bytes = 0L
        private set

    private fun account(count: Int) {
        bytes += count
        if (expected > 0L && bytes > expected) {
            throw java.io.IOException("size mismatch: got more than $expected")
        }
    }

    override fun write(b: Int) {
        target.write(b)
        account(1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        target.write(b, off, len)
        account(len)
    }

    override fun flush() {
        target.flush()
    }

    override fun close() {
        // 空操作：见类注释
    }
}

private const val NO_APK_PART = "no apk part"
