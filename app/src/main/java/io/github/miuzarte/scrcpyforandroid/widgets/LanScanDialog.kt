package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.utils.LanDeviceScanner
import io.github.miuzarte.scrcpyforandroid.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LanScanDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onDeviceSelected: (ip: String, port: Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(254) }
    val scannedDevices = remember { mutableStateListOf<LanDeviceScanner.ScannedDevice>() }
    var scanError by remember { mutableStateOf<String?>(null) }

    val noInterfaceText = stringResource(R.string.lan_scan_no_interface)
    val scanFailedText = stringResource(R.string.lan_scan_failed)

    LaunchedEffect(showDialog) {
        if (!showDialog) return@LaunchedEffect
        isScanning = true
        scanError = null
        scannedDevices.clear()
        scope.launch(Dispatchers.IO) {
            try {
                val localIps = NetworkUtils.getLocalIpv4Addresses()
                if (localIps.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        scanError = noInterfaceText
                        isScanning = false
                    }
                    return@launch
                }

                localIps.forEach { ipInfo ->
                    val subnet = NetworkUtils.extractSubnetPrefix(ipInfo.address)
                    if (subnet != null) {
                        withContext(Dispatchers.Main) {
                            scanProgress = 0
                        }
                        LanDeviceScanner.scanSubnet(
                            subnetPrefix = subnet,
                            onProgress = { current, total ->
                                scope.launch(Dispatchers.Main) {
                                    scanProgress = current
                                    scanTotal = total
                                }
                            },
                            onDeviceFound = { device ->
                                scope.launch(Dispatchers.Main) {
                                    scannedDevices.add(device)
                                }
                            }
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    isScanning = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    scanError = e.message ?: scanFailedText
                    isScanning = false
                }
            }
        }
    }

    val scanningText = stringResource(R.string.lan_scan_scanning, scanProgress, scanTotal)

    OverlayDialog(
        show = showDialog,
        title = stringResource(R.string.lan_scan_title),
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            when {
                isScanning && scannedDevices.isEmpty() -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = scanningText,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                scanError != null -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.lan_scan_failed),
                            style = MiuixTheme.textStyles.title3,
                            color = MiuixTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = scanError ?: stringResource(R.string.lan_scan_unknown_error),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismissRequest) {
                            Text(stringResource(R.string.cd_close))
                        }
                    }
                }

                scannedDevices.isEmpty() && !isScanning -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.lan_scan_no_devices),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.lan_scan_no_devices_hint),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismissRequest) {
                            Text(stringResource(R.string.cd_close))
                        }
                    }
                }

                else -> {
                    SmallTitle(text = stringResource(R.string.lan_scan_found_devices, scannedDevices.size))

                    if (isScanning) {
                        Text(
                            text = scanningText,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    scannedDevices.sortedBy { it.ip }.forEach { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${device.ip}:${device.port}",
                                        style = MiuixTheme.textStyles.title3,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                    if (device.isLocalDevice) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.lan_scan_local_device),
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                TextButton(
                                    text = stringResource(R.string.button_connect),
                                    onClick = {
                                        onDeviceSelected(device.ip, device.port)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
