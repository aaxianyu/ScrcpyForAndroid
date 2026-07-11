package io.github.miuzarte.scrcpyforandroid.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * 局域网设备扫描器
 * 采用多线程TCP连接扫描，检测开启ADB调试端口的设备
 */
object LanDeviceScanner {

    data class ScannedDevice(
        val ip: String,
        val port: Int,
        val isLocalDevice: Boolean,
    )

    private const val DEFAULT_ADB_PORT = 5555
    private const val CONNECT_TIMEOUT_MS = 300
    private const val MAX_THREADS = 512

    /**
     * 扫描局域网内开启ADB端口的设备
     * @param subnetPrefix 子网前缀，如 "192.168.1"
     * @param port 要扫描的端口，默认5555
     * @param onProgress 扫描进度回调 (current, total)
     * @param onDeviceFound 发现设备时的实时回调，每发现一个设备立即调用
     * @return 发现的设备列表
     */
    suspend fun scanSubnet(
        subnetPrefix: String,
        port: Int = DEFAULT_ADB_PORT,
        onProgress: ((Int, Int) -> Unit)? = null,
        onDeviceFound: ((ScannedDevice) -> Unit)? = null,
    ): List<ScannedDevice> = withContext(Dispatchers.IO) {
        val results = ConcurrentLinkedQueue<ScannedDevice>()
        val total = 254
        val progressCounter = AtomicInteger(0)
        val localIps = NetworkUtils.getLocalIpv4Addresses().map { it.address }.toSet()

        // 分批处理，每批最多 MAX_THREADS 个
        val batchSize = MAX_THREADS
        val ipRange = (1..254).toList()

        ipRange.chunked(batchSize).forEach { batch ->
            coroutineScope {
                batch.map { hostId ->
                    async {
                        val ip = "$subnetPrefix.$hostId"
                        val isOpen = try {
                            withTimeoutOrNull(CONNECT_TIMEOUT_MS.toLong()) {
                                Socket().use { socket ->
                                    socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                                    true
                                }
                            } == true
                        } catch (_: Exception) {
                            false
                        }

                        if (isOpen) {
                            val device = ScannedDevice(
                                ip = ip,
                                port = port,
                                isLocalDevice = localIps.contains(ip),
                            )
                            results.add(device)
                            // 实时回调通知UI
                            onDeviceFound?.invoke(device)
                        }

                        val current = progressCounter.incrementAndGet()
                        onProgress?.invoke(current, total)
                    }
                }.awaitAll()
            }
        }

        results.sortedBy { it.ip }
    }

    /**
     * 扫描所有本地网络接口对应的子网
     * @param port 要扫描的端口
     * @param onProgress 扫描进度回调
     * @param onDeviceFound 发现设备时的实时回调
     * @return 所有发现的设备列表
     */
    suspend fun scanAllLocalNetworks(
        port: Int = DEFAULT_ADB_PORT,
        onProgress: ((Int, Int) -> Unit)? = null,
        onDeviceFound: ((ScannedDevice) -> Unit)? = null,
    ): List<ScannedDevice> = withContext(Dispatchers.IO) {
        val localIps = NetworkUtils.getLocalIpv4Addresses()
        val allResults = mutableListOf<ScannedDevice>()

        localIps.forEach { ipInfo ->
            val subnet = NetworkUtils.extractSubnetPrefix(ipInfo.address)
            if (subnet != null) {
                val devices = scanSubnet(subnet, port, onProgress, onDeviceFound)
                allResults.addAll(devices)
            }
        }

        // 去重并按IP排序
        allResults.distinctBy { it.ip }.sortedBy { it.ip }
    }
}
