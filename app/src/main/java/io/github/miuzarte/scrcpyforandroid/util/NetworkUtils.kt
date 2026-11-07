package io.github.miuzarte.scrcpyforandroid.util

import java.net.Inet6Address
import java.net.NetworkInterface

object NetworkUtils {

    // 匹配常见虚拟/隧道接口名（tun0、tap、ppp0、wg0、ipsec、部分 VPN 接口 displayName）
    private val VIRTUAL_INTERFACE_REGEX = Regex("tun|tap|ppp|wg|ipsec|vpn")

    data class IpInfo(
        val address: String,
        val isIpv6: Boolean,
        val interfaceName: String,
    )

    fun getLocalIpAddresses(): List<IpInfo> {
        val result = mutableListOf<IpInfo>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
            ?: return result

        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue
            // VPN/隧道虚拟接口（tun/tap/ppp/wg/ipsec 等）必须跳过：其网段不是真实局域网，
            // 且 tun 模式代理会对任意 IP 的连接立即应答，扫描会产生大量假设备
            val ifName = (
                networkInterface.name + " " + (networkInterface.displayName ?: "")
            ).lowercase()
            if (VIRTUAL_INTERFACE_REGEX.containsMatchIn(ifName)) continue

            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address.isLoopbackAddress || address.isLinkLocalAddress) continue

                val isIpv6 = address is Inet6Address
                val literal = address.hostAddress ?: continue
                result += IpInfo(
                    // IPv6 的 hostAddress 带 scope id（如 fe80::1%wlan0），带着它没法当连接地址用
                    address = if (isIpv6) literal.substringBefore('%') else literal,
                    isIpv6 = isIpv6,
                    interfaceName = networkInterface.displayName ?: networkInterface.name,
                )
            }
        }
        return result
    }

    /**
     * 本机 IPv4 地址，只保留 RFC1918 私有段。
     *
     * 蜂窝网络常用 100.64/10 这类 CGNAT 段，VPN 也可能给出公网地址，都不该拿去扫局域网。
     */
    fun getLocalIpv4Addresses(): List<IpInfo> =
        getLocalIpAddresses().filter { !it.isIpv6 && it.address.isRfc1918() }

    private fun String.isRfc1918(): Boolean {
        val octets = split('.').map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return when (octets[0]) {
            10 -> true
            172 -> octets[1] in 16..31
            192 -> octets[1] == 168
            else -> false
        }
    }

    /**
     * 取出 /24 子网前缀，如 `192.168.1.37` → `192.168.1`。非 IPv4 返回 null。
     */
    fun extractSubnetPrefix(ip: String): String? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }
}
