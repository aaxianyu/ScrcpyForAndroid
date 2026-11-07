package io.github.miuzarte.scrcpyforandroid.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbMdnsDiscoverer
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 发现局域网内可连接的 ADB 设备。
 *
 * 三段式：
 * 1. 5555 快扫：全段只探 5555，让常用端口设备最先出现。
 * 2. 深扫：对已拿到 IP 的主机全端口扫描（1024..65535），DEEP_WORKERS 个 worker 并行扫不同主机。
 * 3. 主机发现（与 1/2 并行）：广播 + ARP + mDNS，找出 5555 不通但开了别的 ADB 端口的设备。
 *
 * 主机发现刻意不依赖 5555 能否连通——只开自定义端口的设备会被漏掉。
 */
object LanDeviceScanner {

    data class ScannedDevice(
        val ip: String,
        val port: Int,
        val isLocalDevice: Boolean,
    )

    sealed interface Stage {
        /** 阶段一：全段快扫 5555。total 固定 254×子网数，可定比例 */
        data class Sweeping(val scanned: Int, val total: Int) : Stage

        /** 阶段二：深扫存活主机的其他端口。progress 是已探测端口的占比 0..100 */
        data class DeepScanning(val progress: Int) : Stage

        data object Done : Stage
    }

    // adb 协议常量（adb/adb.h）：int 按小端序写出即 ASCII 字面
    private const val A_CNXN = 0x4e584e43
    private const val A_AUTH = 0x48545541
    private const val A_VERSION = 0x01000001
    private const val MAX_PAYLOAD = 4096

    private const val DEFAULT_ADB_PORT = 5555
    private const val HOST_RANGE = 254

    // 阶段一：主机发现
    private const val DISCOVERY_PORT = 1900
    private const val BROADCAST_TIMEOUT_MS = 1500
    private const val ARP_SETTLE_MS = 500L
    private const val MDNS_TIMEOUT_MS = 2500L
    private const val ZERO_MAC = "00:00:00:00:00:00"

    // 阶段二：深扫
    private const val PORT_MIN = 1024
    private const val PORT_MAX = 65535
    // 单地址探测最坏耗时 = CONNECT_TIMEOUT_MS + HANDSHAKE_READ_TIMEOUT_MS
    // 实测存在把 SYN 静默丢弃（不回 RST）的设备：闭端口要等满 connect 超时，
    // 因此 connect 超时不能压得太低，否则丢包设备会被误判为全部离线而漏扫
    private const val CONNECT_TIMEOUT_MS = 300
    private const val HANDSHAKE_READ_TIMEOUT_MS = 150
    // 阶段零直探放宽超时：目标少（≤16 个）且并行，慢一点无所谓；
    // 热点客户端首包要带 ARP 解析，实测 RTT 能到 200ms+，300/150ms 会把活设备误判成不在线
    private const val STAGE0_CONNECT_TIMEOUT_MS = 1500
    private const val STAGE0_HANDSHAKE_READ_TIMEOUT_MS = 500
    private const val SWEEP_CONCURRENCY = 128
    // 优先扫完的端口上限，剩下的一次性扫
    private const val PRIORITY_PORT_MAX = 10000
    // 深扫 worker 数：固定 6 台主机并行，不动态扩。
    // 线程池/信号量统一收口在途 socket 数，worker 只决定「同时扫几台主机」
    private const val DEEP_WORKERS = 6
    // 单台主机一次铺开的端口数
    private const val DEEP_BATCH = 128
    // 全局在途探测上限：所有 worker 共享，不能让「worker × 每台并发」硬相乘
    // （6×128=768 会撞 fd 上限），由信号量统一收口
    private const val GLOBAL_CONCURRENCY = 384

    // 最近扫描到的地址缓存。再次扫描时先直探这些地址，命中就不用等快扫/深扫扫到它
    private const val CACHE_PREFS = "lan_scan_cache"
    private const val KEY_RECENT = "recent_devices"
    private const val CACHE_MAX = 16
    private const val CACHE_DELIMITER = "\n"

    suspend fun scan(
        onStage: ((Stage) -> Unit)? = null,
        onDeviceFound: ((ScannedDevice) -> Unit)? = null,
    ): List<ScannedDevice> {
        // 专用线程池：Dispatchers.IO 的并行度是 max(64, CPU 核数)，这设备 8 核只有 64，
        // 直接用 IO 调度器写 128 并发实际只有 64 个探测在跑。
        // 线程池生命周期跟着本次扫描走，取消/结束即 shutdown，不长期占用。
        val probeExecutor = Executors.newFixedThreadPool(GLOBAL_CONCURRENCY) { r ->
            Thread(r, "lan-scan-probe").apply { isDaemon = true }
        }
        return withContext(probeExecutor.asCoroutineDispatcher()) {
            try {
                scanInternal(onStage, onDeviceFound)
            } finally {
                probeExecutor.shutdownNow()
            }
        }
    }

    private suspend fun scanInternal(
        onStage: ((Stage) -> Unit)?,
        onDeviceFound: ((ScannedDevice) -> Unit)?,
    ): List<ScannedDevice> = coroutineScope {
        // 只枚举一次：本机标注与子网提取必须基于同一份快照，两次独立枚举之间
        // 网络状态若变化（WiFi 重连 / VPN 断开），两者会不一致。
        val ipv4Addresses = NetworkUtils.getLocalIpv4Addresses()
        val localAddresses = ipv4Addresses.mapTo(mutableSetOf()) { it.address }
        val subnets = ipv4Addresses.mapNotNull { NetworkUtils.extractSubnetPrefix(it.address) }.distinct()

        // isLocalDevice 必须在这里判定：扫描与 mDNS 发出的回调一律不带标注，
        // 直接透传的话 UI 的增量列表里就永远不会有「本机」。
        // 每台主机只经 reportFirst 报一次，results 不会重复
        val results = ConcurrentHashMap.newKeySet<ScannedDevice>()
        fun report(device: ScannedDevice) {
            val annotated = device.copy(isLocalDevice = device.ip in localAddresses)
            results.add(annotated)
            onDeviceFound?.invoke(annotated)
        }

        // VPN 的 tun 会接管局域网路由（连目标设备的连接也被劫持进隧道），
        // 只有把 socket 绑定到 wifi/以太网网络才能绕开代理直连真实设备；
        // 拿不到对应网络（无 wifi）时回退默认路由
        val bypassNetwork = wifiNetwork()

        val hostsEnqueued = AtomicInteger(0)
        // 深扫进度按端口计而不是按主机计：活主机只有几台时，主机粒度会让百分比
        // 长时间停在 0% 再逐台跳变；端口粒度随探测平滑增长。
        // 每台主机无论扫完还是提前命中端口，完成时都把整个端口区间记满
        val portsDone = AtomicInteger(0)
        val knownHosts = ConcurrentHashMap.newKeySet<String>()
        // 已拿到一个 ADB 端口的主机。每台主机有一个能用的端口就够了，找到即停，
        // 不再继续探测同一台主机的其他端口；同时这三个来源（快扫 / mDNS / 深扫）
        // 只接受第一个结果，后到的同主机端口一律丢弃
        val satisfiedHosts = ConcurrentHashMap.newKeySet<String>()
        val hostQueue = Channel<String>(Channel.UNLIMITED)
        val permits = Semaphore(GLOBAL_CONCURRENCY)

        fun reportFirst(device: ScannedDevice) {
            if (satisfiedHosts.add(device.ip)) {
                report(device)
                cacheRecentDevice(device.ip, device.port)
            }
        }

        suspend fun enqueueHost(ip: String) {
            if (!knownHosts.add(ip)) return
            // 已经有端口的主机不用再深扫
            if (ip in satisfiedHosts) return
            hostsEnqueued.incrementAndGet()
            hostQueue.send(ip)
        }

        // ---------- 阶段零：直探上次扫到的地址 ----------
        // 这些是上次确认过的 ADB 端点，再扫一遍它们比等快扫扫到更快
        val recent = readRecentDevices()
        if (recent.isNotEmpty()) {
            coroutineScope {
                recent.map { (ip, port) ->
                    async {
                        if (isAdbEndpointSlow(ip, port, bypassNetwork) == ProbeOutcome.ADB) {
                            reportFirst(ScannedDevice(ip, port, isLocalDevice = false))
                        }
                    }
                }.awaitAll()
            }
        }

        // ---------- 阶段一：5555 快扫 ----------
        // 全段 254 个地址只探 5555，带握手验证：常用端口设备最先出现，顺带判定主机存活。
        val sweepTotal = subnets.size * HOST_RANGE
        val sweepScanned = AtomicInteger(0)
        subnets.map { subnet ->
            async {
                (1..HOST_RANGE).chunked(SWEEP_CONCURRENCY).forEach { batch ->
                    coroutineScope {
                        batch.map { hostIndex ->
                            async {
                                val ip = "$subnet.$hostIndex"
                                val outcome = isAdbEndpoint(ip, DEFAULT_ADB_PORT, bypassNetwork)
                                if (outcome == ProbeOutcome.ADB) {
                                    reportFirst(ScannedDevice(ip, DEFAULT_ADB_PORT, isLocalDevice = false))
                                }
                                // 只有 TCP 层面确认存活的主机（回了 RST 或 connect 成功）才入队；
                                // 静默丢 SYN 的主机 TCP 判不出存活，交给 ARP 发现阶段补。
                                // 不能无条件入队：一段里大半是死主机，拖去扫 6 万端口毫无意义
                                if (outcome != ProbeOutcome.NO_RESPONSE) enqueueHost(ip)
                                val done = sweepScanned.incrementAndGet()
                                onStage?.invoke(Stage.Sweeping(done, sweepTotal))
                            }
                        }.awaitAll()
                    }
                }
            }
        }.awaitAll()

        // ---------- 阶段二：worker 深扫（先启动，边扫边等新主机入队）----------
        // 优先扫 1 万以内：手动指定的 adbd 端口几乎都在这个区间；
        // 无线调试的随机高位端口由 mDNS 直接拿到，不靠深扫
        val portRange = (PORT_MIN..PRIORITY_PORT_MAX).toList() +
            ((PRIORITY_PORT_MAX + 1)..PORT_MAX).toList()
        // 深扫进度 = 已探测端口数 / (已入队主机数 × 全端口区间)。入队数在发现阶段
        // 会持续增长，所以这个值会回退；这是真实的剩余工作量，不是 UI 卡住
        fun deepProgress(): Int {
            val total = hostsEnqueued.get() * portRange.size
            return (portsDone.get() * 100L / total).coerceIn(0, 100).toInt()
        }
        // 百分比没变就不发：一批 128 个端口的探测完成得很快，逐批发会刷爆主线程
        var lastDeepPct = -1
        fun emitDeepProgress() {
            val pct = deepProgress()
            if (pct != lastDeepPct) {
                lastDeepPct = pct
                onStage?.invoke(Stage.DeepScanning(pct))
            }
        }
        val deepJobs = (1..DEEP_WORKERS).map {
            async {
                for (host in hostQueue) {
                    // 排队期间被 mDNS 抢先满足的主机不用再扫
                    if (host in satisfiedHosts) {
                        portsDone.addAndGet(portRange.size)
                        continue
                    }
                    emitDeepProgress()
                    scanHostPorts(
                        host, portRange, bypassNetwork, permits,
                        satisfiedHosts, portsDone, { emitDeepProgress() }, ::reportFirst,
                    )
                    emitDeepProgress()
                }
            }
        }

        // ---------- 主机发现（与深扫并行，不是串行在前面）----------
        // UDP 广播 + ARP 表 + mDNS，找出快扫没确认存活、但实际在线的设备。
        // 静默丢 SYN 的主机（某些防火墙）快扫判不出存活，ARP 表里有它，就靠这里补进队列。
        // 本机不在 ARP 表里，单独加入队列。
        localAddresses.forEach { enqueueHost(it) }

        val discoveryJob = async(Dispatchers.IO) {
            subnets.forEach { subnet -> sendDiscoveryBroadcast(subnet, bypassNetwork) }
            // 广播包发完不等回复：目的是让网段内设备回包，回包会把它的 MAC 写进
            // 本机 ARP 表，主机清单从 ARP 表读。这里等的是 ARP 表沉淀
            delay(ARP_SETTLE_MS)
            // 先收进列表再入队：readArpHosts 的回调是普通 lambda，不能直接调 suspend 的入队
            val arpHosts = mutableListOf<String>()
            subnets.forEach { subnet -> readArpHosts(subnet) { arpHosts.add(it) } }
            arpHosts.forEach { enqueueHost(it) }
        }
        val mdnsJob = async(Dispatchers.IO) { scanMdns { reportFirst(it) } }

        discoveryJob.join()
        mdnsJob.join()
        // 发现阶段结束、队列里也不再进新主机，worker 消费完自然退出
        hostQueue.close()
        deepJobs.awaitAll()

        onStage?.invoke(Stage.Done)

        results.toList()
    }

    private suspend fun scanHostPorts(
        host: String,
        portRange: List<Int>,
        network: Network?,
        permits: Semaphore,
        satisfiedHosts: MutableSet<String>,
        portsDone: AtomicInteger,
        onBatchDone: () -> Unit,
        report: (ScannedDevice) -> Unit,
    ) {
        // 提前命中端口就停扫的主机，剩余未探测的端口一次性记满，进度不留尾巴
        var probed = 0
        fun creditRest() {
            portsDone.addAndGet(portRange.size - probed)
            onBatchDone()
        }
        portRange.chunked(DEEP_BATCH).forEach { batch ->
            // 深扫进行中可能被别的来源抢先满足（例如 mDNS），已拿到端口就不必继续
            if (host in satisfiedHosts) {
                creditRest()
                return@scanHostPorts
            }

            val outcomes = coroutineScope {
                batch.map { port ->
                    async {
                        permits.withPermit { isAdbEndpoint(host, port, network) }
                    }
                }.awaitAll()
            }
            probed += batch.size
            portsDone.addAndGet(batch.size)
            onBatchDone()

            // 一台主机拿到一个能用的端口就够了，找到立即停止该主机的扫描
            val hitIndex = outcomes.indexOfFirst { it == ProbeOutcome.ADB }
            if (hitIndex >= 0) {
                report(ScannedDevice(host, batch[hitIndex], isLocalDevice = false))
                creditRest()
                return@scanHostPorts
            }
        }
    }

    // 快扫 / mDNS / 深扫 / 阶段零会并发命中，读改写必须整体加锁，否则后写覆盖前写丢条目
    private val cacheLock = Any()

    // 缓存只存「ip:port」文本，最近命中的放最前，重复的提上来，超过 CACHE_MAX 截断
    private fun cacheRecentDevice(ip: String, port: Int) = runCatching {
        synchronized(cacheLock) {
            val entry = "$ip:$port"
            val prefs = AppRuntime.context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            val current = prefs.getString(KEY_RECENT, "").orEmpty()
                .split(CACHE_DELIMITER).filter { it.isNotBlank() }.toMutableList()
            current.remove(entry)
            current.add(0, entry)
            prefs.edit().putString(KEY_RECENT, current.take(CACHE_MAX).joinToString(CACHE_DELIMITER)).apply()
        }
    }.getOrNull()

    private fun readRecentDevices(): List<Pair<String, Int>> = runCatching {
        AppRuntime.context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECENT, "").orEmpty()
            .split(CACHE_DELIMITER).filter { it.isNotBlank() }.mapNotNull { entry ->
                val colon = entry.lastIndexOf(':')
                if (colon < 0) return@mapNotNull null
                val port = entry.substring(colon + 1).toIntOrNull() ?: return@mapNotNull null
                entry.substring(0, colon) to port
            }
    }.getOrNull().orEmpty()

    private fun wifiNetwork(): Network? = runCatching {
        val cm = AppRuntime.context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }.getOrNull()

    private fun sendDiscoveryBroadcast(subnetPrefix: String, network: Network?) = runCatching {
        // 绑定到 wifi 网络发包，否则广播会进 VPN 隧道
        val socket = DatagramSocket()
        socket.use {
            if (network != null) network.bindSocket(it)
            it.soTimeout = BROADCAST_TIMEOUT_MS
            it.broadcast = true
            val data = "M-SEARCH * HTTP/1.1".toByteArray()
            val address = InetAddress.getByName("$subnetPrefix.255")
            it.send(DatagramPacket(data, data.size, address, DISCOVERY_PORT))
        }
    }.getOrNull()

    // /proc/net/arp 全局可读。flags=0x0 且 MAC 为 00:00... 的表项是解析失败的历史记录，不是存活主机
    private fun readArpHosts(subnetPrefix: String, onHost: (String) -> Unit) = runCatching {
        File("/proc/net/arp").bufferedReader().use { reader ->
            reader.lineSequence().drop(1).forEach { line ->
                val columns = line.trim().split(Regex("\\s+"))
                val ip = columns.getOrNull(0) ?: return@forEach
                val mac = columns.getOrNull(3) ?: return@forEach
                if (ip.startsWith("$subnetPrefix.") && mac.isNotBlank() && mac != ZERO_MAC) {
                    onHost(ip)
                }
            }
        }
    }.getOrNull()

    private suspend fun scanMdns(onDeviceFound: ((ScannedDevice) -> Unit)?): List<ScannedDevice> {
        val result = runCatching {
            AdbMdnsDiscoverer.discoverConnectServices(MDNS_TIMEOUT_MS) { host, port ->
                onDeviceFound?.invoke(ScannedDevice(ip = host, port = port, isLocalDevice = false))
            }
        }
        // runCatching 无差别吞 Throwable，CancellationException 必须补抛，否则
        // discoverConnectServices 的分片可取消等待白做（取消信号被吞，协程停不下来）
        result.onFailure { if (it is CancellationException) throw it }
        return result.getOrDefault(emptyList())
            .map { (host, port) -> ScannedDevice(ip = host, port = port, isLocalDevice = false) }
    }

    // 单次探测结果：
    // ADB         —— 握手魔数匹配，确认是 adbd
    // OTHER       —— 有 RST（端口没开）或 connect 成功但不是 ADB，两种都说明主机活着
    // NO_RESPONSE —— connect 超时（防火墙静默丢包/主机不在线）或 fake TCP 假连接
    private enum class ProbeOutcome { ADB, OTHER, NO_RESPONSE }

    // 阶段零用放宽的超时（见 STAGE0_* 常量），其余阶段保持紧凑超时
    private fun isAdbEndpointSlow(ip: String, port: Int, network: Network?): ProbeOutcome =
        runCatching { probeSocket(ip, port, network, STAGE0_CONNECT_TIMEOUT_MS, STAGE0_HANDSHAKE_READ_TIMEOUT_MS) }
            .getOrDefault(ProbeOutcome.NO_RESPONSE)

    // 两段验证，缺一不可：
    // 1) TCP 三次握手成功只说明「有东西 accept 了」——tun 模式代理（Clash 等）会对
    //    任意 IP 的连接立即本地 accept（fake TCP），只判握手会把全段地址都当成设备；
    // 2) ADB 是主机先说话的协议：先发 CNXN 握手包，真 adbd 会回 A_CNXN（已授权）或
    //    A_AUTH（待 RSA 授权）；fake 代理不会按协议应答。以响应魔数判定真假。
    private fun isAdbEndpoint(ip: String, port: Int, network: Network?): ProbeOutcome =
        runCatching { probeSocket(ip, port, network) }.getOrDefault(ProbeOutcome.NO_RESPONSE)

    private fun probeSocket(
        ip: String,
        port: Int,
        network: Network?,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        handshakeReadTimeoutMs: Int = HANDSHAKE_READ_TIMEOUT_MS,
    ): ProbeOutcome {
        val socket = if (network != null) network.socketFactory.createSocket() else Socket()
        return socket.use {
            try {
                socket.connect(InetSocketAddress(ip, port), connectTimeoutMs)
            } catch (e: ConnectException) {
                // ECONNREFUSED：主机活着，端口没开。这是 RST，不算病态
                return ProbeOutcome.OTHER
            }
            socket.soTimeout = handshakeReadTimeoutMs
            val payload = "host::features=".toByteArray(Charsets.US_ASCII)
            val out = ByteBuffer.allocate(24 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            out.putInt(A_CNXN).putInt(A_VERSION).putInt(MAX_PAYLOAD)
                .putInt(payload.size).putInt(crc32(payload))
                .putInt(A_CNXN.inv()).put(payload)
            socket.getOutputStream().let { it.write(out.array()); it.flush() }
            val magic = ByteArray(4)
            val input = socket.getInputStream()
            var read = 0
            while (read < magic.size) {
                val n = input.read(magic, read, magic.size - read)
                if (n < 0) return ProbeOutcome.OTHER
                read += n
            }
            // 能按协议读回魔数，说明对端是真 adbd，不是 fake 代理
            val response = ByteBuffer.wrap(magic).order(ByteOrder.LITTLE_ENDIAN).int
            if (response == A_CNXN || response == A_AUTH) ProbeOutcome.ADB else ProbeOutcome.OTHER
        }
    }

    private fun crc32(data: ByteArray): Int {
        val crc = java.util.zip.CRC32().apply { update(data, 0, data.size) }
        return crc.value.toInt()
    }
}
