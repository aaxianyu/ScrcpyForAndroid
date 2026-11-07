package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceConnectionType
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.nativecore.UsbAdbSession
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions

internal data class ConnectionDisconnectResult(
    val clearedTarget: ConnectionTarget?,
)

internal data class ConnectedAdbResult(
    val target: ConnectionTarget,
    val info: ConnectedDeviceInfo,
    val scrcpyProfileId: String,
)

internal data class StopScrcpyResult(
    val disconnectedAdb: Boolean,
    val clearedTarget: ConnectionTarget?,
)

internal class ConnectionController(
    private val scrcpy: Scrcpy,
    private val stateStore: ConnectionStateStore,
    private val adbCoordinator: DeviceAdbConnectionCoordinator = DeviceAdbConnectionCoordinator(),
) {
    val state: ConnectionState
        get() = stateStore.state.value

    suspend fun disconnectAdbConnection(
        clearQuickOnlineForTarget: ConnectionTarget? = state.adbSession.currentTarget,
        cause: DisconnectCause = DisconnectCause.Unknown,
        statusLine: String = "Disconnected",
    ): ConnectionDisconnectResult {
        stateStore.markDisconnected(cause = cause, statusLine = statusLine)
        AppRuntime.currentConnectionTarget = null
        AppRuntime.currentConnectedDevice = null
        AppRuntime.currentConnectionProfileId.value = "global"
        runCatching { scrcpy.stop() }
        runCatching { adbCoordinator.disconnect() }
        // 兜底释放 USB 隧道 (无隧道时为 no-op, 幂等)
        runCatching { UsbAdbSession.disconnect() }
        AppScreenOn.release()
        runCatching { AdbKeepAliveService.stop(AppRuntime.context) }
        return ConnectionDisconnectResult(clearedTarget = clearQuickOnlineForTarget)
    }

    suspend fun disconnectCurrentTargetBeforeConnecting(
        newHost: String,
        newPort: Int,
    ): ConnectionTarget? {
        val current = state.adbSession.currentTarget ?: return null
        if (!state.adbSession.isConnected) return null
        if (current.host == newHost && current.port == newPort) return null

        disconnectAdbConnection(
            clearQuickOnlineForTarget = current,
            cause = DisconnectCause.SwitchTarget,
        )
        return current
    }

    fun applyConnectedDeviceCapabilities(sdkInt: Int) {
        stateStore.updateSession {
            it.copy(
                audioForwardingSupported = sdkInt !in 0..<30,
                cameraMirroringSupported = sdkInt !in 0..<31,
            )
        }
    }

    suspend fun connectWithTimeout(host: String, port: Int, timeoutMs: Long) {
        adbCoordinator.connectWithTimeout(host, port, timeoutMs)
    }

    suspend fun connectAddresses(
        addresses: List<String>,
        timeoutMs: Long,
        probeTimeoutMs: Int,
    ): ConnectionTarget {
        return adbCoordinator.connectFirstReachable(addresses, timeoutMs, probeTimeoutMs)
    }

    suspend fun disconnectCurrentTargetBeforeConnectingAny(
        addresses: List<String>,
    ): ConnectionTarget? {
        val current = state.adbSession.currentTarget ?: return null
        if (!state.adbSession.isConnected) return null
        if (addresses.any { addr ->
                val ct = ConnectionTarget.unmarshalFrom(addr)
                ct != null && ct.host == current.host && ct.port == current.port
            }) return null

        disconnectAdbConnection(
            clearQuickOnlineForTarget = current,
            cause = DisconnectCause.SwitchTarget,
        )
        return current
    }

    suspend fun keepAliveCheck(timeoutMs: Long): Boolean {
        // 真实链路探测: 标志位查询无法发现 TCP 半开假死连接
        return adbCoordinator.probeConnection(timeoutMs)
    }

    suspend fun probeTcpReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return adbCoordinator.probeTcpReachable(host, port, timeoutMs)
    }

    suspend fun runAutoAdbConnect(
        host: String,
        port: Int,
        timeoutMs: Long,
    ): Boolean {
        return runCatching {
            connectWithTimeout(host, port, timeoutMs)
            true
        }.getOrElse { error ->
            stateStore.update {
                it.copy(
                    disconnectCause = DisconnectCause.AutoReconnectFailed,
                    lastError = error.message,
                )
            }
            false
        }
    }

    suspend fun handleAdbConnected(
        host: String,
        port: Int,
        scrcpyProfileId: String = ScrcpyOptions.GLOBAL_PROFILE_ID,
        deviceId: Int? = null,
        connectionType: DeviceConnectionType = DeviceConnectionType.LAN,
    ): ConnectedAdbResult {
        val target = ConnectionTarget(host, port, deviceId, connectionType)
        // 先作废按设备缓存的清单（应用 / 最近任务 / 编码器…）再广播连接状态：观察者
        // （自动刷新等）看到新状态时缓存已经清完，不会先拉一遍新数据又被清掉；
        // 同一地址重连保留缓存
        scrcpy.listings.bindDevice(
            NativeAdbService.connectedAddress()
                ?: if (connectionType == DeviceConnectionType.USB) "usb:$deviceId" else "$host:$port",
        )
        stateStore.markConnected(target = target, scrcpyProfileId = scrcpyProfileId)
        AppRuntime.currentConnectionTarget = target
        AppRuntime.currentConnectionProfileId.value = scrcpyProfileId

        val info = adbCoordinator.fetchConnectedDeviceInfo(host, port)
        stateStore.updateSession {
            it.copy(connectedDeviceLabel = info.model)
        }
        AppRuntime.currentConnectedDevice = info
        applyConnectedDeviceCapabilities(info.sdkInt)
        if (AdbKeepAliveService.isEnabled()) {
            runCatching { AdbKeepAliveService.start(AppRuntime.context, info.model) }
        }
        return ConnectedAdbResult(
            target = target,
            info = info,
            scrcpyProfileId = scrcpyProfileId,
        )
    }

    fun syncConnectedScrcpyProfileId(profileId: String) {
        stateStore.updateSession {
            it.copy(connectedScrcpyProfileId = profileId)
        }
    }

    fun updateQuickConnected(quickConnected: Boolean) {
        stateStore.updateSession {
            it.copy(isQuickConnected = quickConnected)
        }
    }

    fun updateStatusLine(statusLine: String) {
        stateStore.updateSession {
            it.copy(statusLine = statusLine)
        }
    }

    fun markConnectionFailed(error: Throwable) {
        stateStore.markConnectionFailed(error.message)
    }

    fun markKeepAliveReconnectSuccess(host: String, port: Int) {
        stateStore.update {
            it.copy(
                adbSession = it.adbSession.copy(
                    isConnected = true,
                    statusLine = "$host:$port",
                ),
                disconnectCause = null,
                lastError = null,
            )
        }
        // 保活服务若被系统停掉, 重连成功后补起
        if (AdbKeepAliveService.isEnabled()) {
            runCatching {
                AdbKeepAliveService.start(
                    AppRuntime.context,
                    AppRuntime.currentConnectedDevice?.model.orEmpty(),
                )
            }
        }
    }

    fun markScrcpyStarted() {
        stateStore.updateSession {
            it.copy(statusLine = "scrcpy running")
        }
    }

    suspend fun stopScrcpySession(killAdbOnClose: Boolean): StopScrcpyResult {
        scrcpy.stop()
        if (killAdbOnClose) {
            val disconnected = disconnectAdbConnection(
                clearQuickOnlineForTarget = state.adbSession.currentTarget,
                cause = DisconnectCause.KillAdbOnClose,
            )
            return StopScrcpyResult(
                disconnectedAdb = true,
                clearedTarget = disconnected.clearedTarget,
            )
        }

        AppScreenOn.release()
        val target = state.adbSession.currentTarget
        if (target != null) {
            updateStatusLine("${target.host}:${target.port}")
        }
        return StopScrcpyResult(
            disconnectedAdb = false,
            clearedTarget = null,
        )
    }
}
