package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.util.LanDeviceScanner
import io.github.miuzarte.scrcpyforandroid.util.NetworkUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

/**
 * 打开即开始扫描。点设备行直接连接；点行尾「填入」只把地址填回输入框。
 */
@Composable
fun LanScanDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onDeviceConnect: (ip: String, port: Int) -> Unit,
    onDeviceFill: (ip: String, port: Int) -> Unit,
    onDismissFinished: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var scanJob by remember { mutableStateOf<Job?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    // 扫描阶段。null 表示还没开始或刚清空
    var stage by remember { mutableStateOf<LanDeviceScanner.Stage?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    // 同一 IP 的多个端口合并成一行：点整行直接连接，点行尾「填入」只把地址填回输入框
    val devices = remember { mutableStateListOf<LanDeviceScanner.ScannedDevice>() }

    val noInterfaceText = stringResource(R.string.lan_scan_no_interface)
    val fillLabel = stringResource(R.string.lan_scan_fill)

    LaunchedEffect(showDialog) {
        // 关弹窗或重开前先掐掉上一轮，否则新旧两轮会同时往 hosts 里塞，结果重复、进度跳变
        scanJob?.cancel()
        scanJob = null
        if (!showDialog) return@LaunchedEffect

        isScanning = true
        scanError = null
        stage = null
        devices.clear()

        if (NetworkUtils.getLocalIpv4Addresses().isEmpty()) {
            scanError = noInterfaceText
            isScanning = false
            return@LaunchedEffect
        }

        scanJob = scope.launch {
            // 取消不会中断 mDNS 那段阻塞等待，其回调仍会触发；所以回主线程后要再确认这一轮
            // 还没被取消，否则上一轮的结果会混进新一轮的列表。
            val job = coroutineContext[Job]
            try {
                LanDeviceScanner.scan(
                    onStage = { st ->
                        scope.launch(Dispatchers.Main) {
                            if (job?.isActive == true) stage = st
                        }
                    },
                    onDeviceFound = { device ->
                        scope.launch(Dispatchers.Main) {
                            if (job?.isActive != true) return@launch
                            // 扫描器保证每台主机只报一个端口，这里只挡界面重开时的残留
                            if (devices.none { it.ip == device.ip }) devices.add(device)
                        }
                    },
                )
                stage = LanDeviceScanner.Stage.Done
                isScanning = false
            } catch (e: CancellationException) {
                // 取消必须上抛。被取消那一轮可能正卡在 mDNS 的阻塞等待里，信号要等它回到
                // 挂起点才生效，那时新一轮已经开跑，吞掉这里会把新轮的 isScanning 清成 false。
                throw e
            } catch (e: Exception) {
                scanError = e.message
                isScanning = false
            }
        }
    }

    OverlayDialog(
        show = showDialog,
        title = stringResource(R.string.lan_scan_title),
        insideMargin = DpSize(10.dp, 24.dp),
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
        ) {
            when {
                scanError != null -> CenteredMessage(
                    title = stringResource(R.string.lan_scan_failed),
                    titleColor = MiuixTheme.colorScheme.error,
                    detail = scanError,
                )

                else -> {
                    // 进度块钉在弹窗顶部不随列表滚动；设备列表在自己的滚动区内
                    if (isScanning) {
                        ScanProgress(
                            stage = stage,
                            trailing = if (devices.isEmpty()) null
                            else stringResource(R.string.lan_scan_found_devices, devices.size),
                        )
                    }
                    if (devices.isEmpty() && !isScanning) {
                        CenteredMessage(
                            title = stringResource(R.string.lan_scan_no_devices),
                            detail = stringResource(R.string.lan_scan_no_devices_hint),
                        )
                    } else if (devices.isNotEmpty()) {
                        // 扫描中计数已在进度行右侧，这里不再重复显示
                        if (!isScanning) {
                            SectionSmallTitle(
                                text = stringResource(R.string.lan_scan_found_devices, devices.size),
                            )
                        }
                        // 对话框本身 wrap-content，列表必须有高度上限否则整窗被撑满；
                        // 上限按屏高比例取，小屏/大字体下仍留出进度块与标题的空间
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.45f)
                                // 进度行贴着列表太近：在滚动区外留一段固定空隙
                                .padding(top = UiSpacing.MediumLarge)
                                .verticalScroll(scrollState),
                        ) {
                            // 全部设备共用一张卡，行就是卡内的一条，融合成一张列表（同命令书签弹窗）
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.defaultColors(
                                    color = MiuixTheme.colorScheme.secondaryContainer,
                                ),
                            ) {
                                // 按 IP 四段数值排序：字典序会把 192.168.102.10 排到 192.168.102.2 前面
                                devices.sortedWith(
                                    compareBy(
                                        { ipOctet(it.ip, 0) }, { ipOctet(it.ip, 1) },
                                        { ipOctet(it.ip, 2) }, { ipOctet(it.ip, 3) },
                                    ),
                                ).forEach { device ->
                                    BasicComponent(
                                        title = "${device.ip}:${device.port}",
                                        summary = if (device.isLocalDevice) {
                                            stringResource(R.string.lan_scan_local_device)
                                        } else {
                                            null
                                        },
                                        onClick = { onDeviceConnect(device.ip, device.port) },
                                        onClickLabel = stringResource(R.string.button_connect),
                                        role = Role.Button,
                                        endActions = {
                                            // 行尾「填入」：只写回输入框不发起连接。
                                            // 圆钮样式与配色见 RowActionButton，与命令书签弹层的行尾钮共用
                                            RowActionButton(
                                                imageVector = MiuixIcons.Edit,
                                                contentDescription = fillLabel,
                                                onClick = { onDeviceFill(device.ip, device.port) },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(UiSpacing.Small))
            // 扫描要跑满一轮，给一个显式退出入口
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                textStyle = textStyles.button.copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun ScanProgress(stage: LanDeviceScanner.Stage?, trailing: String?) {
    val text = when (stage) {
        is LanDeviceScanner.Stage.Sweeping -> stringResource(R.string.lan_scan_sweeping)
        is LanDeviceScanner.Stage.DeepScanning ->
            stringResource(R.string.lan_scan_deep, stage.progress)
        else -> stringResource(R.string.lan_scan_starting)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiSpacing.Large),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text(
            text = text,
            style = textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = trailing,
                style = textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private fun ipOctet(ip: String, index: Int): Int = ip.split('.').getOrNull(index)?.toIntOrNull() ?: -1

@Composable
private fun CenteredMessage(
    title: String,
    onClose: (() -> Unit)? = null,
    detail: String? = null,
    titleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
) {
    Spacer(Modifier.height(UiSpacing.Large))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
    ) {
        Text(text = title, color = titleColor)
        detail?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        onClose?.let { dismiss ->
            Button(
                onClick = dismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.button_close))
            }
        }
    }
}
