package io.github.miuzarte.scrcpyforandroid.appmanager.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// 清单新增字段（splitCount）后旧缓存会把分包当单包，靠版本号让旧清单直接作废
private const val CACHE_SCHEMA_VERSION = 1

/**
     * 图标与列表元数据缓存。
 *
 * - 按设备标识分目录隔离，切换设备不串数据；
 * - 图标为探测输出的原始字节（不二次重编码，渲染时解码），按包名索引；落盘后缀由探测端
 *   报回的实际编码决定（WebP / PNG），读取与清理认全部已知后缀；
 * - 内存缓存为 512 条上限的 LRU，写入文件后主动淘汰内存项；
 * - 只在冷启动（列表为空）时读取；卸载成功后删除该包图标与元数据；
 * - 面板按需拉取时只增写图标文件（[writeIcons]），元数据与孤儿清理仍由全量
 *   [write] 维护（收尾职责集中在一处）。
 */
class DeviceAppCache(context: Context) {

    @Serializable
    private data class CachedApp(
        val packageName: String,
        val label: String,
        val versionName: String,
        val apkSizeBytes: Long,
        val isSystem: Boolean,
        val isEnabled: Boolean,
        /** 分包数量；旧缓存没有这个字段时按 0 处理，即单包。 */
        val splitCount: Int = 0,
    )

    @Serializable
    private data class CacheFile(
        val device: String,
        val apps: List<CachedApp>,
        /** 清单结构版本；缺字段按 0 读，与当前版本不等即整份作废重拉。 */
        val schema: Int = 0,
    )

    private val cacheRoot = File(context.cacheDir, "appmanager")
    private val json = Json { ignoreUnknownKeys = true }

    private val memoryCache = object : android.util.LruCache<String, ImageBitmap>(MEMORY_CACHE_MAX_ENTRIES) {}

    /** 「设备键/包名」→ 版本名：共享图标库的键要它；主线程读、IO 线程写，用并发表。 */
    private val versionsByName = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun versionOf(deviceKey: String, packageName: String): String? =
        versionsByName["$deviceKey/$packageName"]
    // 副索引：icon/warmIcon 放入后记录 key，使 write() 只能淘汰本设备下已消失的条目
    private val memoryKeys = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    // 访问标记不需要每次重组都写盘：一个进程内每台设备写一次就够
    private val touchedKeys = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun deviceDir(deviceKey: String): File =
        File(cacheRoot, sanitizeKey(deviceKey))

    private fun iconsDir(deviceKey: String): File =
        File(deviceDir(deviceKey), "icons")

    /** 写路径专用：目录不存在就先建；读路径（[read] / [icon] / [hasIcon]）一律不建目录。 */
    private fun writableDeviceDir(deviceKey: String): File =
        deviceDir(deviceKey).apply { mkdirs() }

    private fun writableIconsDir(deviceKey: String): File =
        iconsDir(deviceKey).apply { mkdirs() }

    private fun metaFile(deviceKey: String): File =
        File(deviceDir(deviceKey), "meta.json")

    /** 冷启动读取：仅元数据。图标不预读字节，按需走 [icon] 的文件路径解码。 */
    fun read(deviceKey: String): CacheSnapshot? {
        val file = metaFile(deviceKey)
        if (!file.exists()) return null
        val parsed = runCatching { json.decodeFromString<CacheFile>(file.readText()) }.getOrNull()
            ?: return null
        if (parsed.device != deviceKey || parsed.schema != CACHE_SCHEMA_VERSION) return null
        parsed.apps.forEach { versionsByName["$deviceKey/${it.packageName}"] = it.versionName }
        return CacheSnapshot(
            apps = parsed.apps.map {
                DeviceApp(
                    packageName = it.packageName,
                    label = it.label,
                    versionName = it.versionName,
                    apkSizeBytes = it.apkSizeBytes,
                    isSystem = it.isSystem,
                    isEnabled = it.isEnabled,
                    splitCount = it.splitCount,
                )
            },
        )
    }

    /** 每次成功拉到列表后全量写入（图标 + 元数据）。 */
    fun write(deviceKey: String, apps: List<DeviceApp>, iconBytes: Map<String, ByteArray>, iconSuffix: String) {
        val dir = writableDeviceDir(deviceKey)
        val iconsDir = writableIconsDir(deviceKey)
        iconBytes.forEach { (packageName, bytes) ->
            writeIconFile(iconsDir, packageName, bytes, iconSuffix)
        }
        val file = File(dir, "meta.json")
        file.writeText(
            json.encodeToString(
                CacheFile(
                    device = deviceKey,
                    schema = CACHE_SCHEMA_VERSION,
                    apps = apps.map {
                        CachedApp(
                            packageName = it.packageName,
                            label = it.label,
                            versionName = it.versionName,
                            apkSizeBytes = it.apkSizeBytes,
                            isSystem = it.isSystem,
                            isEnabled = it.isEnabled,
                            splitCount = it.splitCount,
                        )
                    },
                ),
            ),
        )
        // memoryKeys 跟踪表定期与真实 LRU 对齐：容量淘汰发生时内部会静默摘除，故写入前先用 snapshot 校正
        // filter/遍历必须与写入共用同一把锁：synchronizedSet 只同步单个方法，迭代期间别处 add 会抛 CME
        val presentKeys = memoryCache.snapshot().keys
        memoryKeys.retainAll(presentKeys)
        // 扫掉本设备目录里已不存在的图标文件：本页卸载走 removePackage 精确删，但设备上
        // 自行卸载、系统更新移除等外部变化只能靠这次全量写入收敛，否则图标目录只增不减
        apps.forEach { versionsByName["$deviceKey/${it.packageName}"] = it.versionName }
        IconFiles.pruneOrphans(iconsDir, apps.mapTo(HashSet<String>()) { it.packageName })
        // 非当前设备的目录按年龄收掉；当前设备键换过（地址 → 序列号）后，旧键目录会自然闲置并被回收
        pruneStaleDevices(deviceKey)
        // 中断写入留下的临时文件也在收尾时收敛（进程被杀时 finally 跑不到）
        IconFiles.pruneStaleTemp(iconsDir)
        // 每次刷新时只移除本设备下不再出现的图标内存项，未消失项保留，避免主线程再次解码
        val wantedKeys = apps.mapTo(HashSet<String>()) { memoryKey(deviceKey, it.packageName) }
        val toDrop = synchronized(memoryKeys) { memoryKeys.filter { it.startsWith("$deviceKey/") && it !in wantedKeys } }
        for (key in toDrop) {
            memoryCache.remove(key)
            memoryKeys.remove(key)
        }
    }

    /** 卸载成功：删除该包图标与元数据，避免下次冷启动又看到已卸载应用。 */
    fun removePackage(deviceKey: String, packageName: String) {
        IconFiles.delete(iconsDir(deviceKey), packageName)
        val key = memoryKey(deviceKey, packageName)
        memoryCache.remove(key)
        memoryKeys.remove(key)
        val file = metaFile(deviceKey)
        if (!file.exists()) return
        val parsed = runCatching { json.decodeFromString<CacheFile>(file.readText()) }.getOrNull()
            ?: return
        val remaining = parsed.apps.filterNot { it.packageName == packageName }
        file.writeText(json.encodeToString(parsed.copy(apps = remaining)))
    }

    /** 图标解码：内存 LRU 优先，未命中走设备目录的图标文件（采样解码）；损坏文件删掉当缺失。 */
    fun icon(deviceKey: String, packageName: String): ImageBitmap? {
        val key = memoryKey(deviceKey, packageName)
        memoryCache.get(key)?.let { return it }
        val file = IconFiles.find(iconsDir(deviceKey), packageName)
        if (file == null) {
            // 本设备还没拉到图标：共享库里其它设备存过同一版本的话直接借用
            return versionOf(deviceKey, packageName)?.let { SharedIconStore.icon(packageName, it) }
        }
        val bitmap = decodeSampled(file)
        if (bitmap == null) {
            // 截断/损坏文件：删掉当作缺失，不然 hasIcon 一直为真、界面永远停在占位图
            runCatching { file.delete() }
            return null
        }
        return bitmap.asImageBitmap().also {
            memoryCache.put(key, it)
            memoryKeys.add(key)
            versionOf(deviceKey, packageName)?.let { version -> SharedIconStore.put(packageName, version, it) }
        }
    }

    /** 组合阶段只读内存 LRU：不做文件 IO 与解码（那些留给 IO 线程的 [icon] / [warmIcon]）。 */
    fun memoryIcon(deviceKey: String, packageName: String): ImageBitmap? =
        memoryCache.get(memoryKey(deviceKey, packageName))

    /** 图标是否已有：内存 LRU 或设备图标文件命中其一即算有。按需拉取前用它跳过已缓存的包。 */
    fun hasIcon(deviceKey: String, packageName: String): Boolean =
        memoryCache.get(memoryKey(deviceKey, packageName)) != null ||
            IconFiles.find(iconsDir(deviceKey), packageName) != null

    /**
     * 只写图标文件：面板按需拉取的落盘路径。
     *
     * 不写 meta.json、不清孤儿——收尾清理是全量写入（[write]）的职责：面板拿到的就是
     * 设备当前清单里的包，不会写出孤儿；元数据也只由全量写入维护。
     */
    fun writeIcons(deviceKey: String, iconBytes: Map<String, ByteArray>, iconSuffix: String) {
        val dir = iconsDir(deviceKey)
        iconBytes.forEach { (packageName, bytes) -> writeIconFile(dir, packageName, bytes, iconSuffix) }
    }

    /** 逐个图标写盘：单个失败（磁盘满 / 只读）只丢这一个并记日志，不中断整批、不抛出。 */
    private fun writeIconFile(dir: File, packageName: String, bytes: ByteArray, suffix: String) {
        runCatching { IconFiles.write(dir, packageName, bytes, suffix) }
            .onSuccess { written ->
                if (!written) Log.w(TAG, "write icon skipped (rename failed): $packageName")
            }
            .onFailure { Log.w(TAG, "write icon failed: $packageName", it) }
    }

    /**
     * 收掉其它设备留下的目录。
     *
     * 目录名是解析出的设备键；只有解析失败退回地址时，换地址才会开新目录、旧目录再没人读；
     * 不按年龄收就会一直留着。年龄口径见 [lastUsedAt]。
     */
    fun pruneStaleDevices(currentKey: String, maxAgeMillis: Long = STALE_DEVICE_AGE_MILLIS) {
        val keep = sanitizeKey(currentKey)
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        cacheRoot.listFiles()?.forEach { dir ->
            if (!dir.isDirectory || dir.name == keep) return@forEach
            if (lastUsedAt(dir) < cutoff) runCatching { dir.deleteRecursively() }
        }
    }

    /**
     * 记一次「这台设备刚被访问」。
     *
     * 目录 mtime 只在目录里增删文件时变，光访问设备、只读缓存都不动它；按 mtime 收会把
     * 「天天连、但列表没变化」的设备当成闲置设备删掉。所以访问时落一个标记文件。
     * 每进程每台设备只写一次，避免每次重组都产生磁盘写入。
     */
    suspend fun touchDevice(deviceKey: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 冷启动首次进入时目录可能还没建立：这时不能记账，否则目录建好后也不会再来写标记
        if (deviceKey in touchedKeys) return@withContext
        val dir = deviceDir(deviceKey)
        if (!dir.isDirectory) return@withContext
        runCatching { File(dir, LAST_USED_FILE).writeBytes(ByteArray(0)) }
            .onSuccess { touchedKeys.add(deviceKey) }
    }

    /** 目录年龄口径：访问标记优先，没有标记才退回目录 mtime。 */
    private fun lastUsedAt(dir: File): Long {
        val marker = File(dir, LAST_USED_FILE)
        val marked = if (marker.isFile) marker.lastModified() else 0L
        return maxOf(marked, dir.lastModified())
    }

    /**
     * 把旧键目录里的缓存搬到新键目录：设备身份从地址改成序列号后，不搬就要重拉全部图标。
     * 目标目录已有 meta.json 时不搬，避免覆盖新键自己的数据。
     */
    fun migrateDevice(fromKey: String, toKey: String) {
        val from = deviceDir(fromKey)
        if (!from.isDirectory) return
        val to = writableDeviceDir(toKey)
        // 逐文件补齐而不是整目录 copyRecursively：后者中途抛错会停在半路，且目标已有同名文件时整段失败
        val fromIcons = File(from, "icons")
        fromIcons.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val dest = File(File(to, "icons"), file.name)
            if (dest.exists()) return@forEach
            runCatching { file.copyTo(dest, overwrite = false) }
                .onFailure { Log.w(TAG, "migrate icon failed: ${file.name}", it) }
        }
        val metaSrc = File(from, "meta.json")
        val metaDest = File(to, "meta.json")
        if (metaSrc.isFile && !metaDest.exists()) {
            runCatching {
                // device 字段还是旧键，不改写的话下次进入会被判成「别的设备的清单」而整份丢弃
                val text = metaSrc.readText().replace("\"device\":\"$fromKey\"", "\"device\":\"$toKey\"")
                metaDest.writeText(text)
            }.onFailure { Log.w(TAG, "migrate meta failed", it) }
        }
    }

    /** 切设备时清空该设备的 LRU：缓存按设备目录隔离，内存 LRU 中也不应保留旧设备位图。 */
    fun evictDevice(deviceKey: String) {
        val prefix = "$deviceKey/"
        val toDrop = synchronized(memoryKeys) { memoryKeys.filter { it.startsWith(prefix) } }
        for (key in toDrop) {
            memoryCache.remove(key)
            memoryKeys.remove(key)
        }
    }

    /**
     * 后台预热图标：主线程滑动时每遇到一个没进 LRU 的行都要
     * 「读文件 + 解码图标」，这是超快速滑动掉帧的主因。列表就绪后在 IO 线程
     * 把整份图标灌进 LRU，主线程之后只做 map 命中。命中即返回，不重复解码。
     */
    fun warmIcon(deviceKey: String, packageName: String): ImageBitmap? {
        val key = memoryKey(deviceKey, packageName)
        memoryCache.get(key)?.let { return it }
        val file = IconFiles.find(iconsDir(deviceKey), packageName) ?: return null
        val bitmap = decodeSampled(file) ?: return null
        return bitmap.asImageBitmap().also {
            memoryCache.put(key, it)
            memoryKeys.add(key)
            versionOf(deviceKey, packageName)?.let { version -> SharedIconStore.put(packageName, version, it) }
        }
    }

    /** 走文件路径解码：不把整份图标字节先读进 byte[] 再复制一次（主线程滑动的额外开销）。 */
    private fun decodeSampled(file: File): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) },
        )
    }

    data class CacheSnapshot(
        val apps: List<DeviceApp>,
    )

    /**
     * 纯逻辑部分（不碰 Context / 文件系统 / 位图），单测直接覆盖。
     *
     * 这两处是缓存正确性的承重点：设备目录名来自外部连接串，内存 key 决定跨设备会不会
     * 串图标。放在伴生对象里是为了让测试不必构造 Context。图标文件名与后缀的规则在
     * [IconFiles]，那边不依赖任何 Android 类型，可测得更深。
     */
    internal companion object {
        const val MEMORY_CACHE_MAX_ENTRIES = 512

        private const val TAG = "DeviceAppCache"
        /** 别的设备目录留多久：够久到「切回去还想看图标」不成立，又不会长期堆积。 */
        private const val STALE_DEVICE_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
        /** 访问标记文件名：mtime 即「最近一次连上这台设备」。 */
        private const val LAST_USED_FILE = ".last_used"

        /** 行内图标显示尺寸 40dp；解码按这个量级采样即可（440dpi 上约 110px）。 */
        const val TARGET_ICON_PX = 128

        fun sampleSizeFor(width: Int, height: Int): Int {
            if (width <= 0 || height <= 0) return 1
            var sample = 1
            while (width / (sample * 2) >= TARGET_ICON_PX && height / (sample * 2) >= TARGET_ICON_PX) {
                sample *= 2
            }
            return sample
        }

        /**
         * 设备标识落到目录名：只保留字母数字与 `- _ .`，其余一律换成 `_`。
         *
         * 纯点串（`.` / `..`）逐字符都合法，但落到目录上会变成上级引用，与空串同样兜底成
         * `unknown`——设备标识来自 host:port 与 usb:id，正常不会出现这类取值。
         */
        fun sanitizeKey(deviceKey: String): String {
            val mapped = deviceKey
                .map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.') it else '_' }
                .joinToString("")
            return if (mapped.isEmpty() || mapped.all { it == '.' }) "unknown" else mapped
        }

        /**
         * 内存 LRU 的 key：必须带设备标识。
         *
         * 只用包名做 key 时，换设备后旧设备的图标会串到新设备（同包名直接命中旧图标），
         * 新设备特有的包名又会在图标文件落盘前取不到图。
         */
        fun memoryKey(deviceKey: String, packageName: String): String = "$deviceKey/$packageName"
    }
}
