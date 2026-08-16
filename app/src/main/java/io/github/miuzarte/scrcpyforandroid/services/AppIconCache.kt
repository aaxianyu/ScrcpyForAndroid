package io.github.miuzarte.scrcpyforandroid.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 本地持久化应用图标缓存。
 *
 * 目录结构：cacheDir/app_icons/<deviceKey>/<pkg>.png（128px 缩略 PNG）+ apps_meta.json（列表元数据）。
 * deviceKey 由连接成功后设置（序列号，空则 IP:端口）。
 * 读取优先走内存 LruCache，其次读 PNG 文件并编码为 base64 返回（与现有渲染路径兼容）。
 */
object AppIconCache {

    private const val ICON_DIR_NAME = "app_icons"
    private const val META_FILE_NAME = "apps_meta.json"
    private const val THUMB_SIZE = 128
    private const val BASE64_CACHE_SIZE = 512

    private val json = Json { ignoreUnknownKeys = true }

    private var currentDeviceKey: String = ""

    private val base64Cache = object : LruCache<String, String>(BASE64_CACHE_SIZE) {}

    @Serializable
    data class AppMeta(
        val packageName: String,
        val label: String,
        val system: Boolean,
    )

    fun setDeviceKey(deviceKey: String) {
        val key = deviceKey.ifBlank { "unknown" }
        if (currentDeviceKey != key) {
            currentDeviceKey = key
            base64Cache.evictAll()
        }
    }

    fun getDeviceKey(): String = currentDeviceKey

    private fun deviceDir(): File? {
        val ctx = AppRuntime.context ?: return null
        val dir = File(File(ctx.cacheDir, ICON_DIR_NAME), currentDeviceKey)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun iconFile(pkg: String): File? {
        val dir = deviceDir() ?: return null
        return File(dir, "$pkg.png")
    }

    /** 读取缓存图标（内存 → 文件），返回 base64，无缓存返回 null。 */
    fun getIconBase64(pkg: String): String? {
        base64Cache.get(pkg)?.let { return it }
        val file = iconFile(pkg) ?: return null
        if (!file.exists()) return null
        return runCatching {
            val b64 = Base64.encodeToString(file.readBytes(), Base64.DEFAULT)
            base64Cache.put(pkg, b64)
            b64
        }.getOrNull()
    }

    /** 写入图标缓存：base64 解码 → 缩放 128px → PNG 落盘。base64 为 null/空时删除旧缓存。 */
    fun putIcon(pkg: String, base64: String?) {
        if (base64.isNullOrBlank()) {
            iconFile(pkg)?.delete()
            base64Cache.remove(pkg)
            return
        }
        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            val scaled = if (src.width > THUMB_SIZE || src.height > THUMB_SIZE) {
                val scale = THUMB_SIZE.toFloat() / maxOf(src.width, src.height)
                Bitmap.createScaledBitmap(
                    src,
                    (src.width * scale).toInt().coerceAtLeast(1),
                    (src.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                src
            }
            val file = iconFile(pkg) ?: return
            file.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (scaled !== src) scaled.recycle()
            if (src !== scaled) src.recycle()
            base64Cache.remove(pkg)
        }
    }

    /** 读取缓存的列表元数据（label/system），无缓存返回 null。 */
    fun getAppsMeta(): List<AppMeta>? {
        val dir = deviceDir() ?: return null
        val file = File(dir, META_FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<List<AppMeta>>(file.readText())
        }.getOrNull()
    }

    /** 写入列表元数据。 */
    fun putAppsMeta(apps: List<AppMeta>) {
        val dir = deviceDir() ?: return
        runCatching {
            File(dir, META_FILE_NAME).writeText(json.encodeToString(apps))
        }
    }

    /** 批量移除指定包的图标与元数据（卸载后调用）。 */
    fun removeApps(pkgs: Set<String>) {
        if (pkgs.isEmpty()) return
        pkgs.forEach { pkg ->
            iconFile(pkg)?.delete()
            base64Cache.remove(pkg)
        }
        getAppsMeta()?.let { meta ->
            putAppsMeta(meta.filter { it.packageName !in pkgs })
        }
    }

    /** 清空当前设备缓存。 */
    fun clear() {
        deviceDir()?.deleteRecursively()
        base64Cache.evictAll()
    }
}
