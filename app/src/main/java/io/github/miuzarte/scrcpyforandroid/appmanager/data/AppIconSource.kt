package io.github.miuzarte.scrcpyforandroid.appmanager.data

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 面板图标来源：应用清单面板（最近任务 / 全部应用）按需读图标、补拉缺失的。
 *
 * 读与落盘都走应用管理域那一份 [DeviceAppCache]（同一个内存 LRU + 同一份设备目录），
 * 面板与应用管理页不会各拉一遍图标，也不会各存一份。
 *
 * 读分两级：界面组合阶段只走 [memoryIcon]（内存 LRU，主线程零 IO、零解码），未命中先显示
 * 占位图，由行内 LaunchedEffect 走 [loadIcon]（IO 解码）替换；落盘补拉走 [ensure]，
 * 分块渐进——文字列表先出，图标逐块出现。
 *
 * 读、写都只认「当前连接解析出的稳定设备键」（[DeviceIdentity.keyFor]）：拿不到（断开或连接锁
 * 被占用）就读不到图标、也不写缓存——宁可这次显示占位，也不让快照回退键把图标串到
 * 上一台设备的目录。
 */
object AppIconSource {

    // 懒初始化：AppRuntime.context 在 MainActivity.onCreate 才可用（同 DeviceAppRepository）
    private val cache: DeviceAppCache get() = DeviceAppRepository.cache
    private val probeClient by lazy { AppProbeClient(AppRuntime.context) }

    /** 组合阶段读内存：只命中 LRU，绝不做文件 IO 与解码（未命中显示占位图，异步补）。 */
    fun memoryIcon(packageName: String): ImageBitmap? {
        val key = DeviceIdentity.keyFor(NativeAdbService.connectedAddress()) ?: return null
        return cache.memoryIcon(key, packageName)
    }

    /** 异步读图标（IO 线程）：内存未命中时读该设备的图标文件并解码；损坏文件按缺失处理。 */
    suspend fun loadIcon(packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = DeviceIdentity.keyFor(NativeAdbService.connectedAddress()) ?: return@withContext null
        cache.icon(key, packageName)
    }

    /**
     * 补齐面板缺失的图标（IO 线程、分块渐进）：每写完一块调用一次 [onProgress]，界面按块
     * 刷新；某块失败即停，已写下的块不受影响，失败块下次打开/刷新再补。返回本次写下的图标数。
     *
     * 设备键严格取当前连接解析出的稳定键：拿不到（未连接或锁被占用）就跳过写缓存——宁可这次不拉，
     * 也不把图标写进可能过期的旧设备目录。
     */
    suspend fun ensure(packages: Collection<String>, onProgress: (() -> Unit)? = null): Int =
        withContext(Dispatchers.IO) {
            if (packages.isEmpty()) return@withContext 0
            val key = DeviceIdentity.keyFor(NativeAdbService.connectedAddress()) ?: return@withContext 0
            val missing = packages.filter { !cache.hasIcon(key, it) }
            if (missing.isEmpty()) return@withContext 0
            var fetched = 0
            probeClient.loadAppMetaProgressive(missing) { icons, suffix ->
                // 探测与落盘之间连接可能已切换：对不上就停，防止把新设备的图标写进旧目录
                if (DeviceIdentity.keyFor(NativeAdbService.connectedAddress()) != key) {
                    Log.w(TAG, "ensure: device changed mid-fetch, stop")
                    return@loadAppMetaProgressive false
                }
                val written = runCatching { cache.writeIcons(key, icons, suffix) }
                    .onFailure { Log.w(TAG, "ensure: write icons failed", it) }
                    .isSuccess
                if (!written) return@loadAppMetaProgressive false
                fetched += icons.size
                onProgress?.invoke()
                true
            }
            if (fetched > 0) Log.d(TAG, "ensure: fetched $fetched/${missing.size} icons")
            fetched
        }

    private const val TAG = "AppIconSource"
}
