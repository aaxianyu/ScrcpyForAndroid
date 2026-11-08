package io.github.miuzarte.scrcpyforandroid.appmanager.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 批量 ZIP 的组装契约。
 *
 * 这里守的是导出链路上最容易静默出错的一段：压缩包里绝不能出现半截条目，
 * 中断不能被当成部分成功，临时文件不能留在磁盘上。
 */
class ZipExportAssemblerTest {

    private lateinit var tempDir: File

    private fun newTempDir() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "zipassembler-${System.nanoTime()}")
        tempDir.mkdirs()
    }

    private fun part(pkg: String, bytes: Long) = ZipExportPart("$pkg.apk", pkg, bytes)

    private fun source(
        pkg: String,
        bytes: Long,
        parts: List<ZipExportPart> = listOf(part(pkg, bytes)),
        error: String? = null,
        grouped: Boolean = false,
    ) = ZipExportSource(pkg, "Label-$pkg", bytes, parts, error, grouped)

    /** 假的 fetch：写出指定字节数，分块回调进度。 */
    private fun writingFetch(
        payload: Map<String, ByteArray>,
        chunk: Long = 4L,
    ): suspend (ZipExportPart, java.io.OutputStream, (Long) -> Unit) -> Unit = { part, dest, onBytes ->
        val bytes = payload.getValue(part.remotePath)
        var written = 0L
        while (written < bytes.size) {
            val n = minOf(chunk, bytes.size - written).toInt()
            dest.write(bytes, written.toInt(), n)
            written += n
            onBytes(n.toLong())
        }
    }

    private fun run(
        sources: List<ZipExportSource>,
        fetch: suspend (ZipExportPart, java.io.OutputStream, (Long) -> Unit) -> Unit,
        ensureActive: () -> Unit = {},
        onProgress: (Long, Long, Int, Int, String) -> Unit = { _, _, _, _, _ -> },
    ): Pair<ZipExportTally, List<String>> = runBlocking {
        val out = ByteArrayOutputStream()
        val tally = java.util.zip.ZipOutputStream(out).use { zip ->
            assembleZip(sources, zip, tempDir, fetch, onProgress, ensureActive)
        }
        tally to entriesOf(out.toByteArray()).map { it.first }
    }

    private fun bytes(size: Int, seed: Int = 0) = ByteArray(size) { (it + seed).toByte() }

    /** 一个 zip 的全部条目，按（条目名，内容）列出。 */
    private fun entriesOf(archive: ByteArray): List<Pair<String, ByteArray>> = buildList {
        ZipInputStream(ByteArrayInputStream(archive)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                add(entry.name to zin.readBytes())
            }
        }
    }

    // ---------------------------------------------------------------- 全部成功

    @Test
    fun `every package lands as one entry named after its package`() {
        newTempDir()
        val (tally, names) = run(
            listOf(source("com.a", 10L, grouped = true), source("com.b", 20L, grouped = true)),
            writingFetch(mapOf("com.a" to bytes(10, 1), "com.b" to bytes(20, 2))),
        )
        assertEquals(listOf("com.a.apk", "com.b.apk"), names)
        assertEquals(2, tally.successCount)
        assertEquals(0, tally.failureCount)
        assertEquals(30L, tally.totalBytes)
        assertTrue(tally.keepTarget)
    }

    @Test
    fun `entry content is the fetched bytes untouched`() {
        newTempDir()
        val payload = mapOf("com.a" to bytes(10, 7))
        val out = ByteArrayOutputStream()
        runBlocking {
            java.util.zip.ZipOutputStream(out).use { zip ->
                assembleZip(
                    listOf(source("com.a", 10L)),
                    zip,
                    tempDir,
                    writingFetch(payload),
                    { _, _, _, _, _ -> },
                    {},
                )
            }
        }
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zin ->
            zin.nextEntry
            assertTrue(payload.getValue("com.a").contentEquals(zin.readBytes()))
        }
    }

    @Test
    fun `progress advances while the bytes are still being pulled`() {
        newTempDir()
        // 边拉边报：部件还在拉取时就要出进度，不能等整份搬完才动
        val seen = mutableListOf<Long>()
        runBlocking {
            java.util.zip.ZipOutputStream(ByteArrayOutputStream()).use { zip ->
                assembleZip(
                    listOf(source("com.a", 12L)),
                    zip,
                    tempDir,
                    writingFetch(mapOf("com.a" to bytes(12, 1)), chunk = 4L),
                    { done, _, _, _, _ -> seen.add(done) },
                    {},
                )
            }
        }
        assertEquals("进度按分块推进，不只报一次终值", listOf(0L, 4L, 8L, 12L), seen.distinct())
    }

    @Test
    fun `a grouped batch wraps a split package in an inner apks and keeps a plain one flat`() {
        newTempDir()
        // 批量结构：普通应用一条 <包名>.apk；分包应用一条 <包名>.apks，内层才是 base + 各分包
        val splitParts = listOf(
            ZipExportPart("base.apk", "com.b/base", 10L),
            ZipExportPart("split_config.zh.apk", "com.b/zh", 4L),
            ZipExportPart("split_config.arm64_v8a.apk", "com.b/arm64", 6L),
        )
        val payload = mapOf(
            "com.a" to bytes(8, 1),
            "com.b/base" to bytes(10, 2),
            "com.b/zh" to bytes(4, 3),
            "com.b/arm64" to bytes(6, 4),
        )
        val sources = listOf(
            source("com.a", 8L, parts = listOf(ZipExportPart("base.apk", "com.a", 8L)), grouped = true),
            source("com.b", 20L, parts = splitParts, grouped = true),
        )
        val out = ByteArrayOutputStream()
        val tally = runBlocking {
            java.util.zip.ZipOutputStream(out).use { zip ->
                assembleZip(sources, zip, tempDir, writingFetch(payload), { _, _, _, _, _ -> }, {})
            }
        }
        val outer = entriesOf(out.toByteArray())
        assertEquals(listOf("com.a.apk", "com.b.apks"), outer.map { it.first })
        assertEquals(payload.getValue("com.a").toList(), outer[0].second.toList())
        val inner = entriesOf(outer[1].second)
        assertEquals(
            listOf("base.apk", "split_config.zh.apk", "split_config.arm64_v8a.apk"),
            inner.map { it.first },
        )
        assertEquals("内层字节合计等于该应用全部部件之和", 20, inner.sumOf { it.second.size })
        assertEquals(28L, tally.totalBytes)
        assertEquals(28L, tally.doneBytes)
        assertEquals(2, tally.successCount)
        assertTrue("整批跑完不留临时文件", tempDir.listFiles().orEmpty().isEmpty())
    }

    // ---------------------------------------------------------------- 失败原子性

    @Test
    fun `a package whose byte count does not match discards the whole archive`() {
        newTempDir()
        // 声明 20 字节却只拉到 10 字节：条目已经边拉边写进去，回滚不掉，只能整份作废
        val (tally, names) = run(
            listOf(source("com.a", 20L), source("com.b", 20L)),
            writingFetch(mapOf("com.a" to bytes(10, 1), "com.b" to bytes(20, 2))),
        )
        assertEquals(listOf("com.a.apk", "com.b.apk"), names)
        assertEquals(1, tally.successCount)
        assertEquals(1, tally.failureCount)
        assertEquals("半截条目回滚不掉，整份产物必须作废", false, tally.keepTarget)
        assertTrue("长度不符的原因要留下来", tally.lastError!!.contains("size mismatch"))
    }

    @Test
    fun `a package with an unknown remote size is written without a length check`() {
        newTempDir()
        // expectedBytes = 0 表示没查到远端大小，此时不做校验
        val (tally, names) = run(
            listOf(source("com.a", 0L)),
            writingFetch(mapOf("com.a" to bytes(7, 3))),
        )
        assertEquals(listOf("com.a.apk"), names)
        assertEquals(1, tally.successCount)
        assertEquals(0, tally.failureCount)
    }

    @Test
    fun `a split package lands as one entry per part`() {
        newTempDir()
        // 分包应用：每个部件一个条目，条目名取文件名、与远端路径无关
        val parts = listOf(
            ZipExportPart("com.a.apk", "com.a/base", 10L),
            ZipExportPart("com.a.split_zh.apk", "com.a/zh", 4L),
        )
        val (tally, names) = run(
            listOf(source("com.a", 14L, parts)),
            writingFetch(mapOf("com.a/base" to bytes(10, 1), "com.a/zh" to bytes(4, 2))),
        )
        assertEquals(listOf("com.a.apk", "com.a.split_zh.apk"), names)
        assertEquals(1, tally.successCount)
        assertEquals(0, tally.failureCount)
        assertEquals(14L, tally.doneBytes)
    }

    @Test
    fun `a package whose apk paths could not be resolved is counted as a failure`() {
        newTempDir()
        // 连 pm path 都取不到：没有部件可拉，按失败记账，且不写条目
        val (tally, names) = run(
            listOf(source("com.a", 0L, parts = emptyList(), error = "apk path not found")),
            writingFetch(emptyMap()),
        )
        assertEquals(emptyList<String>(), names)
        assertEquals(0, tally.successCount)
        assertEquals(1, tally.failureCount)
        assertEquals("apk path not found", tally.lastError)
    }

    @Test
    fun `a transport failure is counted and the rest of the batch still lands`() {
        newTempDir()
        val fetch: suspend (ZipExportPart, java.io.OutputStream, (Long) -> Unit) -> Unit = { part, dest, onBytes ->
            if (part.remotePath == "com.b") throw java.io.IOException("device went away")
            writingFetch(mapOf("com.a" to bytes(10, 1)))(part, dest, onBytes)
        }
        val (tally, names) = run(listOf(source("com.a", 10L), source("com.b", 10L)), fetch)
        // 流式写入回滚不掉：失败那包的条目已经开了半截，只能整份作废
        assertEquals(listOf("com.a.apk", "com.b.apk"), names)
        assertEquals(1, tally.successCount)
        assertEquals(1, tally.failureCount)
        assertEquals("device went away", tally.lastError)
        assertFalse("有半截条目就该作废整份产物", tally.keepTarget)
    }

    @Test
    fun `partial success keeps the target but a total failure does not`() {
        newTempDir()
        val (allBad, _) = run(
            listOf(source("com.a", 10L, parts = emptyList(), error = "apk path not found")),
            writingFetch(emptyMap()),
        )
        assertFalse("一个都没成时撤回落盘条目", allBad.keepTarget)

        val (someOk, _) = run(
            listOf(source("com.a", 4L), source("com.b", 10L, parts = emptyList(), error = "apk path not found")),
            writingFetch(mapOf("com.a" to bytes(4))),
        )
        assertTrue("部分成功保留产物", someOk.keepTarget)
    }

    // ---------------------------------------------------------------- 中断

    @Test
    fun `cancellation propagates and is never treated as partial success`() {
        newTempDir()
        var calls = 0
        val ensureActive = {
            calls++
            if (calls > 3) throw CancellationException("user aborted")
        }
        var caught = false
        try {
            run(
                listOf(source("com.a", 0L), source("com.b", 0L), source("com.c", 0L)),
                writingFetch(mapOf("com.a" to bytes(4), "com.b" to bytes(4), "com.c" to bytes(4))),
                ensureActive = ensureActive,
            )
        } catch (e: CancellationException) {
            caught = true
        }
        assertTrue("中断必须向上传播，不能被当成普通失败吞掉", caught)
        assertTrue("中断后磁盘上不留临时文件", tempDir.listFiles().orEmpty().isEmpty())
    }

    // ---------------------------------------------------------------- 临时文件

    @Test
    fun `no temporary file survives a run that fails`() {
        newTempDir()
        val failing: suspend (ZipExportPart, java.io.OutputStream, (Long) -> Unit) -> Unit = { _, dest, _ ->
            dest.write(bytes(3))
            throw java.io.IOException("mid-pull failure")
        }
        run(listOf(source("com.a", 10L)), failing)
        assertEquals(emptyList<String>(), tempDir.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `the previous package's temp file is gone before the next one starts`() {
        newTempDir()
        val presentAtStart = mutableListOf<List<String>>()
        val fetch: suspend (ZipExportPart, java.io.OutputStream, (Long) -> Unit) -> Unit = { part, dest, onBytes ->
            // 在本包写入之前记录目录内容
            presentAtStart += tempDir.listFiles().orEmpty().map { it.name }
            writingFetch(mapOf("com.a" to bytes(6), "com.b" to bytes(6)))(part, dest, onBytes)
        }
        run(listOf(source("com.a", 6L), source("com.b", 6L)), fetch)
        // 两包的 fetch 入口处目录都是空的：上一包的临时文件已在 finally 里删掉，不累积
        assertEquals(listOf(emptyList<String>(), emptyList<String>()), presentAtStart)
        assertTrue("整批跑完目录是空的", tempDir.listFiles().orEmpty().isEmpty())
    }

    // ---------------------------------------------------------------- 进度单调

    @Test
    fun `a failed package still advances the denominator so progress never stalls`() {
        newTempDir()
        val reported = mutableListOf<Long>()
        val failing: suspend (ZipExportPart, java.io.OutputStream, (Long) -> Unit) -> Unit = { part, dest, onBytes ->
            if (part.remotePath == "com.a") {
                onBytes(50L)
                throw java.io.IOException("died at 50")
            }
            writingFetch(mapOf("com.b" to bytes(100)))(part, dest, onBytes)
        }
        val (tally, _) = run(
            listOf(source("com.a", 50L), source("com.b", 100L)),
            failing,
            onProgress = { done, _, _, _, _ -> reported += done },
        )
        assertEquals(150L, tally.totalBytes)
        // 失败项按其应有字节数补齐，分母走完
        assertEquals(150L, tally.doneBytes)
        assertEquals(150L, reported.last())
    }

    @Test
    fun `reported progress never goes backwards across packages`() {
        newTempDir()
        val reported = mutableListOf<Long>()
        val payload = mapOf("com.big" to bytes(300), "com.small" to bytes(4))
        run(
            listOf(source("com.big", 300L), source("com.small", 4L)),
            writingFetch(payload, chunk = 64L),
            onProgress = { done, _, _, _, _ -> reported += done },
        )
        assertEquals("进度必须单调不减", reported.sorted(), reported)
        assertEquals(304L, reported.last())
    }

    @Test
    fun `progress carries the one-based index and the batch size`() {
        newTempDir()
        val frames = mutableListOf<Triple<Int, Int, String>>()
        run(
            listOf(source("com.a", 4L), source("com.b", 4L)),
            writingFetch(mapOf("com.a" to bytes(4), "com.b" to bytes(4))),
            onProgress = { _, _, index, count, label -> frames += Triple(index, count, label) },
        )
        assertEquals(listOf(Triple(1, 2, "Label-com.a"), Triple(2, 2, "Label-com.b")), frames.distinct())
    }

    // ---------------------------------------------------------------- 写条目阶段失败

    /** 可开关的写失败流：模拟磁盘满 / 写失败，开关由测试精确控制抛错时机。 */
    private class GatedOutputStream : java.io.OutputStream() {
        var failing = false
        override fun write(b: Int) {
            if (failing) throw java.io.IOException("disk full")
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (failing) throw java.io.IOException("disk full")
        }
    }

    @Test
    fun `entry write failure condemns the archive even when another package succeeded`() {
        newTempDir()
        val payload = mapOf("com.a" to bytes(10, 1), "com.b" to bytes(20, 2))
        val gate = GatedOutputStream()
        // 第一包整包写成功，第二包开条目时开始写失败：这正是最危险的组合——成功数 > 0，
        // 压缩包里却已经躺着一个开过头、写不完的条目
        val fetch: suspend (ZipExportPart, java.io.OutputStream, (Long) -> Unit) -> Unit = { part, dest, onBytes ->
            if (part.remotePath == "com.b") gate.failing = true
            val data = payload.getValue(part.remotePath)
            dest.write(data)
            onBytes(data.size.toLong())
        }
        val zip = java.util.zip.ZipOutputStream(gate)
        val tally = runBlocking {
            try {
                assembleZip(
                    listOf(source("com.a", 10L), source("com.b", 20L)),
                    zip,
                    tempDir,
                    fetch,
                    onProgress = { _, _, _, _, _ -> },
                    ensureActive = {},
                )
            } finally {
                // 中央目录同样写不出去，收尾异常不参与断言
                runCatching { zip.close() }
            }
        }
        assertEquals(1, tally.successCount)
        assertEquals(1, tally.failureCount)
        assertTrue(tally.entryWriteFailed)
        assertFalse("写条目失败过就不能保留产物", tally.keepTarget)
    }

    // ---------------------------------------------------------------- 空批

    @Test
    fun `an empty batch produces an empty archive and keeps nothing`() {
        newTempDir()
        val (tally, names) = run(emptyList(), writingFetch(emptyMap()))
        assertEquals(emptyList<String>(), names)
        assertEquals(0, tally.successCount)
        assertEquals(0L, tally.totalBytes)
        assertFalse(tally.keepTarget)
    }
}
