package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.models.ScrcpyOptions.NewDisplay
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.models.parseWmSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 投屏期间对被控机 `wm size` / `wm density` 的覆盖记录，供会话启停与 PiP 停止等入口共同使用。
 *
 * `original` 是该段改写前设备上的覆盖值（空串代表本来没有覆盖值）；`written` 是本次写下的值 ——
 * 读回的覆盖值与 `written` 相同时，说明那次读到的其实是本应用写下的东西，不能当作原值。
 * 记录归属改写时的设备身份: 同一台设备换地址时按写下的覆盖值沿用记录, 换成别的设备则整条作废。
 */
object DisplayOverride {

    private data class Segment(val original: String, val written: String)

    // 记录的读写跨 shell 挂起点, 由它串行化, 避免还原期间被改写抹掉记录
    private val mutex = Mutex()

    @Volatile
    private var target: String? = null

    @Volatile
    private var size: Segment? = null

    @Volatile
    private var density: Segment? = null

    suspend fun apply(value: String) = mutex.withLock { applyLocked(value) }

    private suspend fun applyLocked(value: String) = withContext(Dispatchers.IO) {
        // 读不出设备身份就无法把记录绑到设备上, 抛给调用方记日志
        val deviceId = NativeAdbService.connectedAddress()
            ?: throw IllegalStateException("device address unavailable")
        if (target != deviceId) {
            // 地址变了未必是另一台设备: 设备上的覆盖值还是我们写下的那条就沿用记录,
            // 否则才作废, 交给本次重新读状态
            if (target == null || !matchesWrittenDevice()) {
                size = null
                density = null
            }
            target = deviceId
        }
        val (width, height, dpi) = NewDisplay.parseFrom(value)
        if (width != null && height != null) {
            val written = "${width}x$height"
            if (prepare("size", written, size) { size = it }) {
                NativeAdbService.shell("wm size $written")
            }
        }
        if (dpi != null) {
            val written = dpi.toString()
            if (prepare("density", written, density) { density = it }) {
                NativeAdbService.shell("wm density $written")
            }
        }
    }

    suspend fun restore() = mutex.withLock { restoreLocked() }

    private suspend fun restoreLocked() = withContext(Dispatchers.IO) {
        val sizeBackup = size
        val densityBackup = density
        if (sizeBackup == null && densityBackup == null) {
            clear()
            return@withContext
        }
        // 读不出身份就保留记录; 地址对不上时, 设备上的覆盖值仍是写下的那条才认作同一台继续还原
        val currentId = NativeAdbService.connectedAddress() ?: return@withContext
        if (target != null && target != currentId && !matchesWrittenDevice()) return@withContext
        if (sizeBackup != null) {
            NativeAdbService.shell(
                if (sizeBackup.original.isBlank()) "wm size reset" else "wm size ${sizeBackup.original}",
            )
        }
        if (densityBackup != null) {
            NativeAdbService.shell(
                if (densityBackup.original.isBlank()) "wm density reset" else "wm density ${densityBackup.original}",
            )
        }
        // 命令抛错时保留记录, 下一次还原仍能把原值写回去
        clear()
    }

    /** 读不出当前状态时返回 false —— 调用点据此跳过本段改写, 不留下记录。 */
    private suspend fun prepare(field: String, written: String, current: Segment?, assign: (Segment) -> Unit): Boolean {
        if (current != null) {
            // 已有记录只刷新本次写下的值: 原值不变, 但身份启发式靠 written 比对
            assign(Segment(current.original, written))
            return true
        }
        val state = parseWmSetting(NativeAdbService.shell("wm $field"), field) ?: return false
        assign(Segment(if (state.override == written) "" else state.override ?: "", written))
        return true
    }

    // 地址变了(USB ↔ LAN、无线调试端口变化)但设备上的覆盖值正是我们写下的那条: 认作同一台设备
    private suspend fun matchesWrittenDevice(): Boolean {
        val sizeWritten = size?.written
        if (sizeWritten != null && readOverride("size") == sizeWritten) return true
        val densityWritten = density?.written
        if (densityWritten != null && readOverride("density") == densityWritten) return true
        return false
    }

    private suspend fun readOverride(field: String): String? =
        parseWmSetting(NativeAdbService.shell("wm $field"), field)?.override

    private fun clear() {
        size = null
        density = null
        target = null
    }
}
