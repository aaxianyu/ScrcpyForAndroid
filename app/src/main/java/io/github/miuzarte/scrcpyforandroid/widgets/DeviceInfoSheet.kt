package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DeviceInfoSheet(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    data class DeviceInfo(
        val model: String = "",
        val manufacturer: String = "",
        val androidVersion: String = "",
        val sdkLevel: String = "",
        val batteryLevel: String = "",
        val batteryStatus: String = "",
        val batteryTemp: String = "",
        val storageTotal: String = "",
        val storageFree: String = "",
        val screenResolution: String = "",
        val cpuAbi: String = "",
        val cpuModel: String = "",
        val ram: String = "",
        val density: String = "",
        val serial: String = "",
        val loading: Boolean = true,
        val error: String? = null,
    )

    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }
    val loading = deviceInfo.loading

    val batteryCharging = stringResource(R.string.appmgr_info_battery_charging)
    val batteryFull = stringResource(R.string.appmgr_info_battery_full)
    val batteryDischarging = stringResource(R.string.appmgr_info_battery_discharging)

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        val info = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                NativeAdbService.ensureConnectionResponsive()

                // 合并 7 个 getprop 为一条命令（用 ||| 分隔输出）
                val getPropResult = NativeAdbService.shell(
                    "echo \"|||model=\$(getprop ro.product.model)\";" +
                    "echo \"|||manufacturer=\$(getprop ro.product.manufacturer)\";" +
                    "echo \"|||androidVersion=\$(getprop ro.build.version.release)\";" +
                    "echo \"|||sdkLevel=\$(getprop ro.build.version.sdk)\";" +
                    "echo \"|||cpuAbi=\$(getprop ro.product.cpu.abi)\";" +
                    "echo \"|||hardware=\$(getprop ro.hardware)\";" +
                    "echo \"|||serial=\$(getprop ro.serialno)\""
                )
                val propMap = getPropResult.lineSequence()
                    .filter { it.startsWith("|||") }
                    .mapNotNull { line ->
                        val content = line.removePrefix("|||")
                        val eqIdx = content.indexOf('=')
                        if (eqIdx > 0) content.substring(0, eqIdx) to content.substring(eqIdx + 1).trim()
                        else null
                    }.toMap()
                val model = propMap["model"] ?: ""
                val manufacturer = propMap["manufacturer"] ?: ""
                val androidVersion = propMap["androidVersion"] ?: ""
                val sdkLevel = propMap["sdkLevel"] ?: ""
                val cpuAbi = propMap["cpuAbi"] ?: ""
                val hardwareProp = propMap["hardware"] ?: ""
                val serial = propMap["serial"] ?: ""

                // 6 个独立 ADB 命令并行执行
                coroutineScope {
                    val batteryDeferred = async {
                        val batteryOutput = NativeAdbService.shell("dumpsys battery")
                        val batteryLevel = batteryOutput.lineSequence()
                            .firstOrNull { it.trim().startsWith("level:") }
                            ?.substringAfter(":")?.trim() ?: ""
                        val batteryStatusRaw = batteryOutput.lineSequence()
                            .firstOrNull { it.trim().startsWith("status:") }
                            ?.substringAfter(":")?.trim() ?: ""
                        val batteryStatus = when (batteryStatusRaw) {
                            "2" -> batteryCharging
                            "5" -> batteryFull
                            else -> batteryDischarging
                        }
                        val batteryTempRaw = batteryOutput.lineSequence()
                            .firstOrNull { it.trim().startsWith("temperature:") }
                            ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                        val batteryTemp = "${batteryTempRaw / 10}.${batteryTempRaw % 10}°C"
                        Triple(batteryLevel, batteryStatus, batteryTemp)
                    }

                    val storageDeferred = async {
                        val storageOutput = NativeAdbService.shell("df -h /data 2>/dev/null")
                        val storageLines = storageOutput.lineSequence().filter { it.isNotBlank() }
                        val dataLine = storageLines.elementAtOrNull(1)?.trim()
                            ?.split("\\s+".toRegex())
                        val storageTotal = dataLine?.getOrNull(1) ?: ""
                        val storageFree = dataLine?.getOrNull(3) ?: ""
                        Pair(storageTotal, storageFree)
                    }

                    val screenDeferred = async {
                        val screenOutput = NativeAdbService.shell("wm size")
                        screenOutput.lineSequence()
                            .firstOrNull { it.startsWith("Physical size:") }
                            ?.substringAfter(":")?.trim() ?: ""
                    }

                    val cpuModelDeferred = async {
                        try {
                            val cpuInfo = NativeAdbService.shell("cat /proc/cpuinfo")
                            val hardware = cpuInfo.lineSequence()
                                .firstOrNull { it.startsWith("Hardware") }
                                ?.substringAfter(":")?.trim()
                            hardware ?: hardwareProp
                        } catch (_: Exception) { hardwareProp }
                    }

                    val ramDeferred = async {
                        try {
                            val memInfo = NativeAdbService.shell("cat /proc/meminfo")
                            val parseKb = { prefix: String ->
                                memInfo.lineSequence()
                                    .firstOrNull { it.startsWith(prefix) }
                                    ?.substringAfter(":")?.trim()
                                    ?.replace(" kB", "")?.trim()
                                    ?.toLongOrNull() ?: 0L
                            }
                            val memTotalKb = parseKb("MemTotal:")
                            val memAvailKb = parseKb("MemAvailable:")
                            fun formatGb(kb: Long) = if (kb >= 1048576) String.format("%.1f GB", kb / 1048576.0) else String.format("%.1f MB", kb / 1024.0)
                            if (memTotalKb > 0) "${formatGb(memAvailKb)} / ${formatGb(memTotalKb)}" else ""
                        } catch (_: Exception) { "" }
                    }

                    val densityDeferred = async {
                        try {
                            NativeAdbService.shell("wm density").lineSequence()
                                .firstOrNull { it.startsWith("Physical density:") }
                                ?.substringAfter(":")?.trim() ?: ""
                        } catch (_: Exception) { "" }
                    }

                    // 等待所有结果
                    val (batteryLevel, batteryStatus, batteryTemp) = batteryDeferred.await()
                    val (storageTotal, storageFree) = storageDeferred.await()
                    val screenResolution = screenDeferred.await()
                    val cpuModel = cpuModelDeferred.await()
                    val ram = ramDeferred.await()
                    val density = densityDeferred.await()

                    DeviceInfo(
                        model = model,
                        manufacturer = manufacturer,
                        androidVersion = androidVersion,
                        sdkLevel = sdkLevel,
                        batteryLevel = batteryLevel,
                        batteryStatus = batteryStatus,
                        batteryTemp = batteryTemp,
                        storageTotal = storageTotal,
                        storageFree = storageFree,
                        screenResolution = screenResolution,
                        cpuAbi = cpuAbi,
                        cpuModel = cpuModel,
                        ram = ram,
                        density = density,
                        serial = serial,
                        loading = false,
                    )
                }
            }
        } catch (e: Exception) {
            DeviceInfo(loading = false, error = e.message ?: "获取设备信息失败")
        }
        deviceInfo = info
    }

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.appmgr_device_info),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.appmgr_device_info_loading),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else if (deviceInfo.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = deviceInfo.error!!,
                        color = MiuixTheme.colorScheme.error,
                        fontSize = 14.sp,
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(UiSpacing.ContentVertical)) {
                        if (deviceInfo.manufacturer.isNotBlank() || deviceInfo.model.isNotBlank()) {
                            DeviceInfoRow(
                                label = stringResource(R.string.appmgr_info_model),
                                value = if (deviceInfo.manufacturer.isNotBlank() && deviceInfo.model.isNotBlank())
                                    "${deviceInfo.manufacturer} ${deviceInfo.model}"
                                else deviceInfo.manufacturer.ifBlank { deviceInfo.model },
                            )
                        }
                        if (deviceInfo.androidVersion.isNotBlank()) {
                            DeviceInfoRow(
                                label = stringResource(R.string.appmgr_info_android_version),
                                value = deviceInfo.androidVersion + if (deviceInfo.sdkLevel.isNotBlank()) " (SDK ${deviceInfo.sdkLevel})" else "",
                            )
                        }
                        if (deviceInfo.cpuModel.isNotBlank()) {
                            DeviceInfoRow(label = stringResource(R.string.appmgr_info_cpu_model), value = deviceInfo.cpuModel)
                        }
                        if (deviceInfo.cpuAbi.isNotBlank()) {
                            DeviceInfoRow(label = stringResource(R.string.appmgr_info_cpu), value = deviceInfo.cpuAbi)
                        }
                        if (deviceInfo.ram.isNotBlank()) {
                            DeviceInfoRow(label = stringResource(R.string.appmgr_info_ram), value = deviceInfo.ram)
                        }
                        if (deviceInfo.screenResolution.isNotBlank()) {
                            DeviceInfoRow(label = stringResource(R.string.appmgr_info_screen), value = deviceInfo.screenResolution)
                        }
                        if (deviceInfo.density.isNotBlank()) {
                            DeviceInfoRow(label = stringResource(R.string.appmgr_info_density), value = deviceInfo.density)
                        }
                        if (deviceInfo.serial.isNotBlank()) {
                            DeviceInfoRow(label = stringResource(R.string.appmgr_info_serial), value = deviceInfo.serial)
                        }
                        if (deviceInfo.batteryLevel.isNotBlank()) {
                            DeviceInfoRow(
                                label = stringResource(R.string.appmgr_info_battery),
                                value = "${deviceInfo.batteryLevel}% (${deviceInfo.batteryStatus}) ${deviceInfo.batteryTemp}",
                            )
                        }
                        if (deviceInfo.storageTotal.isNotBlank()) {
                            DeviceInfoRow(
                                label = stringResource(R.string.appmgr_info_storage),
                                value = "${deviceInfo.storageFree} / ${deviceInfo.storageTotal}",
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }
}

@Composable
private fun DeviceInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}