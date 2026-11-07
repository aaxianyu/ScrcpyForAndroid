package io.github.miuzarte.scrcpyforandroid.appmanager.data

/**
 * 应用管理域的错误分类。
 *
 * 失败原因直接透传设备输出（去首尾空白），唯一需要归一的是 `ADB not connected`。
 *
 * 这句话本身说明不了原因：真机实测两种成因给出同一句话——被控设备的 adbd 同时只服务
 * 一个客户端，电脑端 scrcpy/adb 接上时是它；而链路自己断掉（拔网线、对端重启、
 * 无线切换）也是它。所以归一要看**传输层此刻是否还活着**：
 *
 * - 传输层活着：TCP 会话还在，是设备在拒绝这条通道——归一为 [AppManagerError.ChannelOccupied]，
 *   用专用中文文案。
 * - 传输层已死：链路本身没了，原因无从判断，交给既有的自动重连。归一为
 *   [AppManagerError.NotConnected]，界面显示「未连接」而不是替用户编一个占用人。
 *
 * 判据由调用方传入（[NativeAdbService.isTransportAlive]），分类本身是纯函数，可直接单测。
 */
object AdbErrors {

    const val ADB_NOT_CONNECTED = "ADB not connected"

    /** 设备是否在说「通道没有连上」。这句在两种成因下都会出现，原因要靠 [classify] 的第二个判据区分。 */
    fun isChannelNotConnected(output: String): Boolean =
        output.contains(ADB_NOT_CONNECTED, ignoreCase = true)

    /**
     * 把设备输出归一成 [AppManagerError]。
     *
     * @param rawReason 设备输出或异常消息，原样透传（去首尾空白）。
     * @param transportAlive 失败发生时传输层是否还活着。`false` 时不把 `ADB not connected`
     *   判成通道被占用——链路已断时说不清是谁占了通道。
     */
    fun classify(rawReason: String, transportAlive: Boolean): AppManagerError {
        val reason = rawReason.trim()
        return when {
            !isChannelNotConnected(reason) -> AppManagerError.Device(reason.ifEmpty { "unknown error" })
            transportAlive -> AppManagerError.ChannelOccupied(reason)
            else -> AppManagerError.NotConnected
        }
    }
}

sealed interface AppManagerError {
    /** 连接通道不可用。 */
    data object NotConnected : AppManagerError

    /** ADB 通道被外部客户端占用（专用中文文案，不透传英文）。 */
    data class ChannelOccupied(val raw: String) : AppManagerError

    /** 设备侧返回的失败原因（已去首尾空白）。 */
    data class Device(val reason: String) : AppManagerError

    /**
     * 加载超过 [DeviceAppRepository.LOAD_TIMEOUT_MS] 仍没回来。
     *
     * 探测是一条 `app_process` 命令，跑多久没有中间输出可看，卡住时界面会一直转圈。
     * 这不是「失败」——后台那次加载仍在跑，数据到齐会照常覆盖这个状态；它只是告诉用户
     * 「别再干等了」。
     */
    data object Slow : AppManagerError
}