package io.github.miuzarte.scrcpyforandroid.appmanager.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 探测通道的串行闸。
 *
 * 探测用的 dex 本地缓存文件名与设备端路径都是固定的（`appprobe.dex` /
 * `/data/local/tmp/scrcpy_app_probe.dex`）：两次探测并行会互相覆盖，A 的收尾清理还会
 * 删掉 B 正在执行的 dex。锁放在进程级单例上——[AppProbeClient] 由多个页面各自创建。
 */
internal object ProbeSerialization {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
