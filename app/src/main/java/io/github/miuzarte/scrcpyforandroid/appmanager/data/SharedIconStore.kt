package io.github.miuzarte.scrcpyforandroid.appmanager.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

import java.io.File
import java.security.MessageDigest

/**
 * 跨设备共享的图标库：同一应用在不同设备上装的是同一个包，图标也相同，没必要每台设备各存一份。
 *
 * 键是「包名@版本名」——版本变了图标可能变，旧条目按年龄与容量淘汰；条目名取键的 SHA-1 前 16 位，
 * 避免包名里的 `.` 与 `@` 直接做文件名，旁边挂一份 json 存回原始键。
 *
 * 单独放在 `cache/appmanager-shared`：设备目录的清理按「非当前设备」整目录删，共享库若在同一层会被连坐。
 */
internal object SharedIconStore {

    private const val TAG = "SharedIconStore"

    private const val MAX_BYTES = 12L * 1024 * 1024
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /** 独立于设备图标的 LRU：设备侧 512 条不动，这里只留 128 条。 */
    private const val MEMORY_ENTRIES = 128

    private const val MAX_EDGE_PX = 192
    private const val WEBP_QUALITY = 80

    private val memory = object : android.util.LruCache<String, ImageBitmap>(MEMORY_ENTRIES) {}

    /** 本进程已写过的键：同键只写一次，避免每次解码都重新编码落盘。 */
    private val written = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * 编码落盘放单线程队列：预热时一次会来上百张图，直接在调用线程编码会和渲染抢核。
     * 队列满了就丢弃——图标本来就能重建，不值得为它拖慢界面。
     */
    private val worker = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())
    private val queue = Channel<() -> Unit>(capacity = 64)

    @Volatile
    private var writesSinceEvict = 0

    init {
        worker.launch {
            for (task in queue) {
                task()
                if (++writesSinceEvict >= 32) {
                    writesSinceEvict = 0
                    evict()
                }
            }
        }
    }

    private fun root(): File = File(AppRuntime.context.cacheDir, "appmanager-shared")

    private fun keyOf(packageName: String, versionName: String) = "$packageName@$versionName"

    private fun baseName(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    private fun iconFile(key: String) = File(root(), baseName(key) + ".webp")

    private fun metaFile(key: String) = File(root(), baseName(key) + ".json")

    /** 组合阶段只读内存，不做 IO。 */
    fun memoryIcon(packageName: String, versionName: String): ImageBitmap? =
        memory.get(keyOf(packageName, versionName))

    /** 读共享条目：内存 → 文件；文件损坏当作没有并删掉。 */
    fun icon(packageName: String, versionName: String): ImageBitmap? {
        val key = keyOf(packageName, versionName)
        memory.get(key)?.let { return it }
        val file = iconFile(key)
        if (!file.isFile) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
        if (bitmap == null) {
            runCatching { file.delete() }
            return null
        }
        return bitmap.asImageBitmap().also {
            memory.put(key, it)
            runCatching { file.setLastModified(System.currentTimeMillis()) }
        }
    }

    /**
     * 写一张图标进共享库：本进程同键只写一次；键已存在就只续期 mtime。
     * 长边超过 192px 时先等比缩小，落盘 WebP 质量 80。
     */
    fun put(packageName: String, versionName: String, bitmap: ImageBitmap) {
        if (packageName.isBlank() || versionName.isBlank()) return
        val key = keyOf(packageName, versionName)
        val file = iconFile(key)
        if (file.isFile) {
            memory.put(key, bitmap)
            runCatching { file.setLastModified(System.currentTimeMillis()) }
            return
        }
        if (!written.add(key)) return
        val queued = queue.trySend {
            runCatching {
                root().mkdirs()
                val scaled = bitmap.scaledForStore()
                file.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, out)
                }
                metaFile(key).writeText("""{"key":"$key"}""")
            }.onFailure { Log.w(TAG, "write shared icon failed: $key", it) }
        }
        if (!queued.isSuccess) written.remove(key)
        memory.put(key, bitmap)
    }

    /** 先按 7 天年龄清，再按 mtime 从旧到新清到 12 MB 以内。 */
    fun evict() {
        val dir = root()
        val icons = dir.listFiles { f -> f.isFile && f.name.endsWith(".webp") } ?: return
        val now = System.currentTimeMillis()
        icons.forEach { file ->
            if (now - file.lastModified() <= MAX_AGE_MS) return@forEach
            runCatching { file.delete() }
            runCatching { sidecarOf(dir, file).delete() }
        }
        val remaining = dir.listFiles() ?: return
        var total = remaining.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        remaining.filter { it.isFile && it.name.endsWith(".webp") }
            .sortedBy { it.lastModified() }
            .forEach { file ->
                if (total <= MAX_BYTES) return@forEach
                total -= file.length()
                runCatching { file.delete() }
                runCatching { sidecarOf(dir, file).delete() }
            }
    }

    private fun sidecarOf(dir: File, icon: File) =
        File(dir, icon.name.removeSuffix(".webp") + ".json")

    private fun ImageBitmap.scaledForStore(): Bitmap {
        val source = asAndroidBitmap()
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= MAX_EDGE_PX) return source
        val scale = MAX_EDGE_PX.toFloat() / longEdge
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}