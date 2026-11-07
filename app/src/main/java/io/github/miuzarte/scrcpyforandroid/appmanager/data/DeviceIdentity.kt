package io.github.miuzarte.scrcpyforandroid.appmanager.data

import android.util.Log
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 设备身份解析：把「这次连接用的地址」换算成稳定的缓存键。
 *
 * 地址（`host:port` / `usb:<id>`）会随网络与传输方式变化，拿它当缓存目录名时，同一台设备
 * 换个地址就会另开一个目录、旧目录再也没人读。这里按下面的顺序取一个更稳定的标识：
 *
 * 1. 对照表里该地址上次解析出的键（最常见的快速路径，不产生 shell 往返）；
 * 2. `getprop ro.serialno`（USB 与无线同一个值，恢复出厂也不变）；
 * 3. `settings get secure android_id`（恢复出厂会变，作为次选）；
 * 4. 都没有就退回地址本身。
 *
 * 键形如 `serial:<值>` / `aid:<值>` / `<host>:<port>`；USB 地址本身带 deviceId，直接沿用。
 */
internal object DeviceIdentity {

    private const val TAG = "DeviceIdentity"

    /** 这些值说明 ROM 没给真序列号，当作拿不到。 */
    private val BAD_SERIALS = setOf("", "unknown", "0", "00000000", "0123456789abcdef", "null")

    private const val STORE_FILE = "identity.json"

    private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loaded = false

    private val byAddress = HashMap<String, String>()

    private val refreshed = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun storeFile(): File = File(AppRuntime.context.filesDir, "appmanager/$STORE_FILE")

    @Volatile
    private var loadScheduled = false

    /** 预热：在 IO 线程读一次对照表，避免组合期（主线程）读盘。 */
    fun warm() {
        if (loaded || loadScheduled) return
        loadScheduled = true
        sharedScope.launch(Dispatchers.IO) { ensureLoaded() }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val file = storeFile()
            if (!file.isFile) return@runCatching
            val json = JSONObject(file.readText())
            json.keys().forEach { address ->
                val key = json.optString(address, address)
                // 早期版本会写下「自己映射自己」的条目，读的时候丢掉
                if (key.isNotBlank() && key != address) byAddress[address] = key
            }
        }.onFailure { Log.w(TAG, "identity store unreadable", it) }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            val dir = storeFile().parentFile?.apply { mkdirs() } ?: return@runCatching
            val json = JSONObject()
            byAddress.forEach { (address, key) -> json.put(address, key) }
            File(dir, STORE_FILE).writeText(json.toString())
        }.onFailure { Log.w(TAG, "identity store unwritable", it) }
    }

    /**
     * 取「已知的稳定键」：只在对照表已加载时命中；未加载就先用地址，并把读盘排到 IO 线程。
     * 这个方法会在组合期被调，不能做文件 IO。
     */
    fun cachedKey(address: String): String {
        if (!loaded) {
            warm()
            return address
        }
        return synchronized(this) { byAddress[address] } ?: address
    }

    /**
     * 解析并记住该地址对应的稳定键；解析出新键时把旧地址目录里的缓存搬过去，避免重拉图标。
     * 每个地址每进程只解析一次（失败也不反复试）。
     */
    suspend fun refresh(address: String): String = withContext(Dispatchers.IO) {
        ensureLoaded()
        if (address.startsWith("usb:") || address.startsWith("serial:") || address.startsWith("aid:")) {
            address
        } else if (!refreshed.add(address)) {
            cachedKey(address)
        } else {
            val resolved = resolve(address)
            if (resolved == null) {
                Log.w(TAG, "no stable key for $address, falling back to address")
                address
            } else {
                val changed = synchronized(this@DeviceIdentity) {
                    if (byAddress[address] == resolved) {
                        false
                    } else {
                        byAddress[address] = resolved
                        persist()
                        true
                    }
                }
                // 只在映射刚建立/变更时迁移一次；本次未搬完的图标由设备侧按需拉取补齐
                if (changed || byAddress[address] != address) {
                    runCatching { DeviceAppRepository.migrateDeviceCache(address, resolved) }
                        .onFailure { Log.w(TAG, "cache migration failed: $address -> $resolved", it) }
                }
                resolved
            }
        }
    }

    /** 当前连接的稳定键；没连接时为 null。写读缓存都必须经它，别直接用地址。 */
    fun keyFor(address: String?): String? = address?.let { cachedKey(it) }

    private suspend fun resolve(address: String): String? {
        serialOf()?.let { return "serial:$it" }
        androidIdOf()?.let { return "aid:$it" }
        return null
    }

    private suspend fun serialOf(): String? = runCatching {
        NativeAdbService.shell("getprop ro.serialno").trim().lowercase()
    }.onFailure { Log.w(TAG, "read ro.serialno failed", it) }
        .getOrNull()?.takeIf { it.isNotBlank() && it !in BAD_SERIALS && !it.all { c -> c == '0' } }

    private suspend fun androidIdOf(): String? = runCatching {
        NativeAdbService.shell("settings get secure android_id").trim().lowercase()
    }.onFailure { Log.w(TAG, "read android_id failed", it) }
        .getOrNull()?.takeIf { it.isNotBlank() && it !in BAD_SERIALS && !it.all { c -> c == '0' } }
}