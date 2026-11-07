package io.github.miuzarte.scrcpyforandroid.nativecore

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Performs mDNS discovery for ADB TLS pairing/connect services on the local network.
 *
 * Uses Android's `NsdManager` to resolve services and returns a host:port pair
 * when a suitable service is found within the provided timeout.
 */
internal object AdbMdnsDiscoverer {

    private lateinit var nsdManager: NsdManager

    /**
     * Serializes discovery calls: NsdManager rejects a second concurrent discovery of the
     * same service type with `FAILURE_ALREADY_ACTIVE`. This is deliberately independent
     * from the ADB connection lock, so a long discovery window cannot stall connect or
     * keep-alive work on an already connected device.
     */
    private val discoveryLock = ReentrantLock()

    fun init(context: Context) {
        if (::nsdManager.isInitialized) return
        nsdManager = context.applicationContext.getSystemService(NsdManager::class.java)
    }

    /**
     * Discover a device that advertises the ADB connect service via mDNS.
     */
    fun discoverConnectService(
        timeoutMs: Long,
        includeLanDevices: Boolean,
        matchInstanceName: String? = null,
        matchHostAddress: String? = null,
    ): Pair<String, Int>? {
        return discoverService(
            TLS_CONNECT,
            timeoutMs,
            includeLanDevices,
            matchInstanceName,
            matchHostAddress,
        )
    }

    /**
     * Discover a device that advertises the ADB pairing service via mDNS.
     *
     * When [matchInstanceName] is set, only the service announced under exactly that
     * instance name is resolved. The QR pairing flow generates the name itself and the
     * device re-announces it verbatim, so an exact match identifies our own pairing
     * session instead of an unrelated device that happens to be pairing nearby.
     */
    fun discoverPairingService(
        timeoutMs: Long,
        includeLanDevices: Boolean,
        matchInstanceName: String? = null,
    ): Pair<String, Int>? {
        return discoverService(TLS_PAIRING, timeoutMs, includeLanDevices, matchInstanceName, null)
    }

    /**
     * Collect every device advertising the ADB connect service within [timeoutMs].
     *
     * 与 [discoverConnectService] 的差别是不等第一个结果就返回，而是在整个超时窗口内持续
     * 收集。Android 11+ 的无线调试用随机端口，扫不出固定端口，只能靠这里的 mDNS 广播。
     *
     * @param onFound 每解析出一台设备立即回调，供调用方增量展示。
     */
    @Suppress("DEPRECATION")
    suspend fun discoverConnectServices(
        timeoutMs: Long,
        onFound: ((host: String, port: Int) -> Unit)? = null,
    ): List<Pair<String, Int>> {
        check(::nsdManager.isInitialized) { "AdbMdnsDiscoverer is not initialized" }
        val found = LinkedHashSet<Pair<String, Int>>()
        val latch = CountDownLatch(1)

        // 部分 Android 版本同一时刻只允许一个 resolve 在飞，后来者直接吃
        // FAILURE_ALREADY_ACTIVE；这里排队串行处理（串行在所有版本上都正确）。
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        fun resolveNext() {
            val next = synchronized(pending) {
                val head = pending.removeFirstOrNull()
                if (head == null) resolving = false
                head
            } ?: return

            runCatching {
                nsdManager.resolveService(next, object: NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.v(TAG, "resolve failed: ${serviceInfo.serviceName}, error=$errorCode")
                        resolveNext()
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val hostAddress = resolvedHostAddress(serviceInfo)
                        // 只收 IPv4：调用方按 "ip:port" 拼串，v6 字面量不带方括号会被整段
                        // 当成 host 而丢掉端口（TCP 那条路径本来也是纯 v4）。另外
                        // hostAddresses 的顺序不保证，v6 可能排在最前。
                        if (!hostAddress.isNullOrBlank() && !hostAddress.contains(':')) {
                            // 同一 host:port 被再次解析成功时不再回调，避免 UI 出现重复卡片
                            val added = synchronized(found) { found.add(hostAddress to serviceInfo.port) }
                            if (added) onFound?.invoke(hostAddress, serviceInfo.port)
                        }
                        resolveNext()
                    }
                })
            }.onFailure { e ->
                Log.w(TAG, "resolveService failed for ${next.serviceName}", e)
                resolveNext()
            }
        }

        fun submit(serviceInfo: NsdServiceInfo) {
            val shouldStart = synchronized(pending) {
                pending.addLast(serviceInfo)
                if (resolving) false else { resolving = true; true }
            }
            if (shouldStart) resolveNext()
        }

        val discoveryListener = object: NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $serviceType, error=$errorCode")
                latch.countDown()
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                submit(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }

        // 与单设备发现路径（discoverService）共用 discoveryLock：NsdManager 不允许同一
        // service type 的两个并发发现，后来者直接吃 FAILURE_ALREADY_ACTIVE。后台自动
        // 连接（DeviceAdbBackgroundRunner）可能在扫描期间发起单设备发现，不加锁会让
        // 扫描的 mDNS 段整段落空。
        // 锁外先取 Job 引用：withLock 是 critical section，内部不能有挂起点，
        // 只能用非挂起的 isActive 判取消（取消时 break 出循环，finally 停掉发现）
        val job = currentCoroutineContext()[Job]
        discoveryLock.withLock {
            nsdManager.discoverServices(TLS_CONNECT, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            try {
                // 分片等待而不是一次 await(timeoutMs)：协程被取消时能立即从 await 返回、
                // 走 finally 停掉 NsdManager 发现；否则离开页面后仍会残留一个完整超时
                // 窗口的后台发现（最坏 12s），期间还占着锁阻塞其它发现。
                val chunkMs = 200L
                var remaining = timeoutMs
                while (remaining > 0 && job?.isActive != false) {
                    val step = minOf(chunkMs, remaining)
                    if (latch.await(step, TimeUnit.MILLISECONDS)) break
                    remaining -= step
                }
            } finally {
                runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            }
        }

        return synchronized(found) { found.toList() }
    }

    private fun discoverService(
        serviceType: String,
        timeoutMs: Long,
        includeLanDevices: Boolean,
        matchInstanceName: String?,
        matchHostAddress: String?,
    ): Pair<String, Int>? = discoveryLock.withLock {
        discoverServiceLocked(
            serviceType,
            timeoutMs,
            includeLanDevices,
            matchInstanceName,
            matchHostAddress,
        )
    }

    private fun discoverServiceLocked(
        serviceType: String,
        timeoutMs: Long,
        includeLanDevices: Boolean,
        matchInstanceName: String?,
        matchHostAddress: String?,
    ): Pair<String, Int>? {
        check(::nsdManager.isInitialized) { "AdbMdnsDiscoverer is not initialized" }
        val resultPort = AtomicInteger(-1)
        val resultHost = AtomicReference<String?>(null)
        val discoveryFinished = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val discoveryListener = object: NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.v(TAG, "discovery started: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $serviceType, error=$errorCode")
                latch.countDown()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.v(TAG, "discovery stopped: $serviceType")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "stop discovery failed: $serviceType, error=$errorCode")
            }

            @Suppress("DEPRECATION")
            @SuppressLint("NewApi")
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (discoveryFinished.get()) return
                // Filtering by instance name before resolving keeps discovery cheap and
                // avoids resolving (and later using) an unrelated device's service.
                if (matchInstanceName != null && serviceInfo.serviceName != matchInstanceName) {
                    return
                }
                Log.v(TAG, "service found: ${serviceInfo.serviceName}")
                val resolveListener = object: NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.v(TAG, "resolve failed: ${serviceInfo.serviceName}, error=$errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (discoveryFinished.get()) return
                        val hostAddress = resolvedHostAddress(serviceInfo) ?: return
                        if (hostAddress.isBlank()) return
                        if (matchHostAddress != null && hostAddress != matchHostAddress) return

                        if (!includeLanDevices) {
                            val isLocalHost = runCatching {
                                NetworkInterface.getNetworkInterfaces().asSequence().any { intf ->
                                    intf.inetAddresses.asSequence().any { addr ->
                                        addr.hostAddress == hostAddress
                                    }
                                }
                            }.getOrDefault(false)
                            if (!isLocalHost) return
                            if (!isPortOpened(serviceInfo.port)) return
                        }

                        if (resultPort.compareAndSet(-1, serviceInfo.port)) {
                            resultHost.set(hostAddress)
                            discoveryFinished.set(true)
                            latch.countDown()
                        }
                    }
                }
                runCatching {
                    nsdManager.resolveService(serviceInfo, resolveListener)
                }.onFailure { e ->
                    Log.w(TAG, "resolveService failed for ${serviceInfo.serviceName}", e)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.v(TAG, "service lost: ${serviceInfo.serviceName}")
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }

        val port = resultPort.get()
        val host = resultHost.get()
        return if (port > 0 && !host.isNullOrBlank()) host to port else null
    }

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private fun resolvedHostAddress(serviceInfo: NsdServiceInfo): String? {
        val addresses =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                serviceInfo.hostAddresses.filterNotNull()
            else
                listOfNotNull(serviceInfo.host)
        // IPv4 first: converting a link-local IPv6 address to a string drops its scope
        // id (so connecting fails), and the ADB tooling pairs over IPv4 only.
        return (addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull())
            ?.hostAddress
    }

    private fun isPortOpened(port: Int): Boolean = try {
        ServerSocket().use {
            it.bind(InetSocketAddress("127.0.0.1", port), 1)
            false
        }
    } catch (_: IOException) {
        true
    }

    private const val TAG = "AdbMdnsDiscoverer"
    private const val TLS_CONNECT = "_adb-tls-connect._tcp"
    private const val TLS_PAIRING = "_adb-tls-pairing._tcp"
}
