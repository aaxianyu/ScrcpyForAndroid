package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceConnectionType
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.concurrent.Executors

/** 链路抖动时第二次探测前的等待, 两次都失败才判定断开 */
private const val KEEPALIVE_RETRY_DELAY_MS = 600L

internal class DeviceAdbBackgroundRunner: Closeable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "device-adb-monitor").apply { isDaemon = true }
    }
    private val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

    suspend fun runKeepAliveLoop(
        sessionState: () -> DeviceAdbSessionState,
        isForeground: () -> Boolean,
        intervalMs: Long,
        keepAliveCheck: suspend (host: String, port: Int) -> Boolean,
        reconnect: suspend (host: String, port: Int) -> Unit,
        onReconnectSuccess: suspend (host: String, port: Int) -> Unit,
        onReconnectFailure: suspend (Throwable) -> Unit,
        shouldAutoReconnect: () -> Boolean = { true },
        shouldRetryAfterFailure: () -> Boolean = { false },
        retryIntervalMs: Long = 2_000L,
    ) = withContext(dispatcher) {
        val target = sessionState().currentTarget ?: return@withContext
        val host = target.host
        val port = target.port

        while (sessionState().isConnected && sessionState().currentTarget == target) {
            if (!isForeground()) {
                delay(intervalMs)
                continue
            }

            delay(intervalMs)
            val alive = try {
                keepAliveCheck(host, port)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
            currentCoroutineContext().ensureActive()
            if (alive) continue
            // 单次超时可能只是链路抖动, 再探一次仍失败才判定断开
            delay(KEEPALIVE_RETRY_DELAY_MS)
            val aliveAfterRetry = try {
                keepAliveCheck(host, port)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
            currentCoroutineContext().ensureActive()
            if (aliveAfterRetry) continue
            if (!shouldAutoReconnect()) break

            try {
                reconnect(host, port)
                withContext(Dispatchers.Main) {
                    onReconnectSuccess(host, port)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.Main) {
                    onReconnectFailure(error)
                }
                if (!shouldRetryAfterFailure()) break
                delay(retryIntervalMs)
            }
        }
    }

    suspend fun runAutoReconnectLoop(
        isConnected: () -> Boolean,
        isForeground: () -> Boolean,
        isAutoReconnectEnabled: () -> Boolean,
        isBusy: () -> Boolean,
        isAdbConnecting: () -> Boolean,
        hasActiveSession: () -> Boolean,
        savedShortcuts: () -> List<DeviceShortcut>,
        isBlacklisted: (String) -> Boolean,
        probeTcpReachable: suspend (host: String, port: Int) -> Boolean,
        discoverConnectService: suspend () -> Pair<String, Int>?,
        onMdnsPortChanged: suspend (host: String, oldPort: Int, newPort: Int) -> Unit,
        connectKnownShortcut: suspend (DeviceShortcut, ConnectionTarget) -> Boolean,
        connectDiscoveredShortcut: suspend (
            host: String,
            port: Int,
            shortcut: DeviceShortcut,
        ) -> Boolean,
        retryIntervalMs: Long,
    ) = withContext(dispatcher) {
        val quickConnectTriedOnce = mutableSetOf<String>()
        while (!isConnected() && isAutoReconnectEnabled()) {
            if (!isForeground() || isBusy() || isAdbConnecting() || hasActiveSession()) {
                delay(retryIntervalMs)
                continue
            }

            val quickCandidates = savedShortcuts()
            if (quickCandidates.isNotEmpty()) {
                for (device in quickCandidates) {
                    if (isConnected() || isAdbConnecting()) break
                    for (addr in device.addresses) {
                        currentCoroutineContext().ensureActive()
                        if (isConnected() || isAdbConnecting()) break
                        val target = ConnectionTarget.unmarshalFrom(addr) ?: continue
                        // 跳过 USB 地址 (自动重连不支持 USB 设备的 TCP 连接)
                        if (target.connectionType == DeviceConnectionType.USB) continue
                        if (isBlacklisted(target.host)) continue
                        val targetKey = "${target.host}:${target.port}"
                        if (quickConnectTriedOnce.contains(targetKey)) continue
                        if (!probeTcpReachable(target.host, target.port)) continue
                        currentCoroutineContext().ensureActive()
                        quickConnectTriedOnce += targetKey
                        if (connectKnownShortcut(device, target)) break
                    }
                }
                if (isConnected()) break
            }

            currentCoroutineContext().ensureActive()
            val discovered = discoverConnectService()
            currentCoroutineContext().ensureActive()
            if (discovered == null) {
                delay(retryIntervalMs)
                continue
            }

            val (discoveredHost, discoveredPort) = discovered
            if (isBlacklisted(discoveredHost)) {
                delay(retryIntervalMs)
                continue
            }

            val knownDevice = savedShortcuts().firstOrNull { it.matchesHost(discoveredHost) }
            if (knownDevice == null) {
                delay(retryIntervalMs)
                continue
            }

            // 旧端口就记录在命中该 host 的条目上, 不能去别的条目里找
            val recordedPort = knownDevice.addresses.firstNotNullOfOrNull { addr ->
                val target = ConnectionTarget.unmarshalFrom(addr)
                if (target != null &&
                    target.host == discoveredHost &&
                    target.connectionType == DeviceConnectionType.LAN
                ) target.port
                else null
            }
            if (recordedPort != null && recordedPort != discoveredPort) {
                withContext(Dispatchers.Main) {
                    onMdnsPortChanged(discoveredHost, recordedPort, discoveredPort)
                }
            }

            if (isConnected() || isAdbConnecting()) {
                delay(retryIntervalMs)
                continue
            }

            connectDiscoveredShortcut(discoveredHost, discoveredPort, knownDevice)
            delay(retryIntervalMs)
        }
    }

    override fun close() {
        dispatcher.close()
        executor.shutdownNow()
    }
}
