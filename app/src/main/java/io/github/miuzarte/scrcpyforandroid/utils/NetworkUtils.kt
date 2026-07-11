package io.github.miuzarte.scrcpyforandroid.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * 网络工具类：获取本机IP地址、复制到剪贴板等
 */
object NetworkUtils {

    data class IpInfo(
        val address: String,
        val isIpv6: Boolean,
        val interfaceName: String,
    )

    /**
     * 获取本机所有IP地址（IPv4和IPv6）
     */
    fun getLocalIpAddresses(): List<IpInfo> {
        val result = mutableListOf<IpInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                // 跳过回环接口和未启用的接口
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    // 跳过回环地址和链路本地地址
                    if (addr.isLoopbackAddress) continue
                    if (addr.isLinkLocalAddress) continue

                    val isIpv6 = addr is Inet6Address
                    val ipStr = addr.hostAddress?.let {
                        // IPv6去除scope id (%eth0等)
                        if (isIpv6) it.substringBefore("%") else it
                    } ?: continue

                    result.add(
                        IpInfo(
                            address = ipStr,
                            isIpv6 = isIpv6,
                            interfaceName = networkInterface.displayName ?: networkInterface.name,
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // 忽略异常
        }
        return result
    }

    /**
     * 获取本机IPv4地址列表
     */
    fun getLocalIpv4Addresses(): List<IpInfo> =
        getLocalIpAddresses().filter { !it.isIpv6 }

    /**
     * 获取本机IPv6地址列表
     */
    fun getLocalIpv6Addresses(): List<IpInfo> =
        getLocalIpAddresses().filter { it.isIpv6 }

    /**
     * 复制文本到剪贴板
     */
    fun copyToClipboard(context: Context, text: String, label: String = "IP") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    /**
     * 从IP地址提取子网前缀（如 192.168.1.xxx -> 192.168.1）
     */
    fun extractSubnetPrefix(ip: String): String? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }
}
