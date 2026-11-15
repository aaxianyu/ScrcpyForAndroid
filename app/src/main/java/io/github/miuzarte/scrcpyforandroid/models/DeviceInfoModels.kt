package io.github.miuzarte.scrcpyforandroid.models

import java.util.Locale
import kotlin.math.hypot

/**
 * 设备信息页的纯解析层：输入是 shell 的原始输出，输出是可空值，不碰资源与界面文案。
 * 解析不了的字段一律给 null，由界面统一落到「未知」。
 */

/** `/proc/meminfo` 与 `df -k` 的单位都是 kB。 */
internal data class MemSnapshot(
    val totalKb: Long? = null,
    val availableKb: Long? = null,
)

internal fun parseMemSnapshot(output: String): MemSnapshot = MemSnapshot(
    totalKb = memInfoValue(output, "MemTotal"),
    availableKb = memInfoValue(output, "MemAvailable"),
)

private fun memInfoValue(output: String, key: String): Long? =
    output.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key:") }
        ?.substringAfter(':')
        ?.trim()
        ?.substringBefore(' ')
        ?.toLongOrNull()

/** `dumpsys battery` 的原始字段；status 取 Android 的 BatteryManager 常量，文案由界面决定。 */
internal data class BatterySnapshot(
    val level: Int? = null,
    val status: Int? = null,
    val voltageMv: Int? = null,
    val temperatureTenthsC: Int? = null,
)

internal fun parseBatterySnapshot(output: String): BatterySnapshot = BatterySnapshot(
    level = batteryValue(output, "level"),
    status = batteryValue(output, "status"),
    voltageMv = batteryValue(output, "voltage"),
    temperatureTenthsC = batteryValue(output, "temperature"),
)

private fun batteryValue(output: String, key: String): Int? =
    output.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key:") }
        ?.substringAfter(':')
        ?.trim()
        ?.toIntOrNull()

// `Temperature{mValue=57.6, mType=0, mName=CPU0, mStatus=0}`，mType 各家不一样，不参与匹配
private val CpuTemperaturePattern =
    Regex("""mValue=([0-9]+(?:\.[0-9]+)?),\s*mType=\d+,\s*mName=CPU\d*""")

/** `dumpsys thermalservice` 里所有 CPU* 传感器中最高的那一个。 */
internal fun parseMaxCpuTemperature(output: String): Float? =
    CpuTemperaturePattern.findAll(output)
        .mapNotNull { it.groupValues[1].toFloatOrNull() }
        .maxOrNull()

internal data class DiskSnapshot(
    val totalKb: Long? = null,
    val usedKb: Long? = null,
    val availableKb: Long? = null,
)

/** `df -k /data`：跳过表头，取第一条字段数够得着的行。挂载点带空格也不影响。 */
internal fun parseDfSnapshot(output: String): DiskSnapshot {
    val rows = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.drop(1)
    for (row in rows) {
        val columns = row.split(Regex("""\s+"""))
        if (columns.size < 4) continue
        val total = columns[1].toLongOrNull() ?: continue
        val used = columns[2].toLongOrNull() ?: continue
        val available = columns[3].toLongOrNull() ?: continue
        return DiskSnapshot(total, used, available)
    }
    return DiskSnapshot()
}

/** `dumpsys display` 的 mBaseDisplayInfo 行：面板真实像素、物理 DPI 与它给出的两轴 PPI。 */
internal data class DisplaySnapshot(
    val densityDpi: Int? = null,
    val ppiX: Float? = null,
    val ppiY: Float? = null,
    val realWidthPx: Int? = null,
    val realHeightPx: Int? = null,
    val refreshRates: List<Int> = emptyList(),
)

private val DisplayDensityPattern = Regex("""density (\d+) \(([0-9.]+) x ([0-9.]+)\) dpi""")
private val DisplayRealSizePattern = Regex("""real (\d+) x (\d+)""")
// 面板模式里的 fps 常带小数（60.000004），取整后再去重排序
private val DisplayFpsPattern = Regex("""fps=([0-9]+(?:\.[0-9]+)?)""")

internal fun parseDisplaySnapshot(output: String): DisplaySnapshot {
    val density = DisplayDensityPattern.find(output)
    val realSize = DisplayRealSizePattern.find(output)
    return DisplaySnapshot(
        densityDpi = density?.groupValues?.get(1)?.toIntOrNull(),
        ppiX = density?.groupValues?.get(2)?.toFloatOrNull(),
        ppiY = density?.groupValues?.get(3)?.toFloatOrNull(),
        realWidthPx = realSize?.groupValues?.get(1)?.toIntOrNull(),
        realHeightPx = realSize?.groupValues?.get(2)?.toIntOrNull(),
        refreshRates = DisplayFpsPattern.findAll(output)
            .mapNotNull { it.groupValues[1].toDoubleOrNull()?.toInt() }
            .distinct()
            .sorted()
            .toList(),
    )
}

/** `ip -f inet addr`：排除回环，保留全局地址。 */
internal fun parseIpv4Addresses(output: String): List<String> = parseAddresses(output, "inet")

/** `ip -f inet6 addr`：排除回环与链路本地，保留全局地址。 */
internal fun parseIpv6Addresses(output: String): List<String> = parseAddresses(output, "inet6")

private fun parseAddresses(output: String, prefix: String): List<String> {
    val pattern = Regex("""^\s*$prefix\s+([0-9a-fA-F:.]+)/\d+(.*)$""")
    return output.lineSequence()
        .mapNotNull { line ->
            val match = pattern.find(line) ?: return@mapNotNull null
            val address = match.groupValues[1]
            val trailing = match.groupValues[2]
            val loopback = address.startsWith("127.") || address == "::1"
            if (loopback || address.startsWith("fe80:") || "scope link" in trailing) null else address
        }
        .distinct()
        .toList()
}

internal fun parseUptimeSeconds(output: String): Long? =
    output.trim().substringBefore(' ').toDoubleOrNull()?.toLong()

internal data class Uptime(val days: Long, val hours: Long, val minutes: Long)

internal fun uptimeOf(totalSeconds: Long): Uptime =
    Uptime(
        days = totalSeconds / 86_400,
        hours = totalSeconds % 86_400 / 3_600,
        minutes = totalSeconds % 3_600 / 60,
    )

private val WifiSsidPattern = Regex("""(?:^|[\s,])SSID:\s*(?:"([^"]*)"|([^,\n]*))""", RegexOption.IGNORE_CASE)

/** `cmd wifi status`：开关关闭时输出里没有 SSID；带引号与不带引号两种形态都收。 */
internal fun parseWifiSsid(output: String): String? {
    val match = WifiSsidPattern.find(output) ?: return null
    val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
    if (raw.isEmpty()) return null
    if (raw.equals("<unknown ssid>", ignoreCase = true)) return null
    if (raw.equals("unknown", ignoreCase = true)) return null
    return raw
}

/** 1024 进制、默认一位小数；内存这类需要两位小数的取值由调用方指定。 */
internal fun gibText(bytes: Long, decimals: Int = 1): String =
    String.format(Locale.US, "%.${decimals}f GiB", bytes / 1024.0 / 1024.0 / 1024.0)

internal fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

/** `wm` 的 `720x1600` → `720 × 1600`。 */
internal fun pixelsText(wmValue: String?): String? =
    wmValue?.split('x')?.takeIf { it.size == 2 }?.joinToString(" × ")

/** 屏幕对角线英寸数：优先用出厂横纵 PPI 分别折算，缺 PPI 时退回逻辑密度。 */
internal fun screenInches(widthPx: Int, heightPx: Int, ppiX: Float?, ppiY: Float?, densityDpi: Int?): Double? {
    if (widthPx <= 0 || heightPx <= 0) return null
    if (ppiX != null && ppiY != null && ppiX > 0f && ppiY > 0f) {
        return hypot(widthPx / ppiX.toDouble(), heightPx / ppiY.toDouble())
    }
    val dpi = densityDpi?.takeIf { it > 0 } ?: return null
    return hypot(widthPx.toDouble(), heightPx.toDouble()) / dpi
}

/** 电池温度以 0.1℃ 为单位；整度省略小数，其余保留一位。 */
internal fun tenthsCelsiusText(tenths: Int?): String? {
    if (tenths == null) return null
    val spec = if (tenths % 10 == 0) "%.0f" else "%.1f"
    return String.format(Locale.US, spec, tenths / 10.0)
}

/** 双卡运营商会给出逗号分隔的多值，个别 ROM 还会塞进占位值，统一清洗成可显示文本。 */
internal fun parseCarrier(output: String): String? =
    output.split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
        .distinct()
        .joinToString(" / ")
        .takeIf { it.isNotEmpty() }

/** 网络类型同样可能带逗号占位值（如 `NR_SA,Unknown`）。 */
internal fun parseNetworkType(output: String): String? = parseCarrier(output)

/** 电池电流：sysfs 或 dumpsys 的裸数字，单位 µA，放电为负。 */
internal fun parseBatteryCurrent(output: String): Long? {
    output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        line.toLongOrNull()?.let { return it }
    }
    return Regex("""-?[0-9]+""").find(output)?.value?.toLongOrNull()
}