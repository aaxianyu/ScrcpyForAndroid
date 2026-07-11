package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.utils.NetworkUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LocalIpScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(topAppBarState)

    val allIps = remember { NetworkUtils.getLocalIpAddresses() }
    val ipv4List = allIps.filter { !it.isIpv6 }
    val ipv6List = allIps.filter { it.isIpv6 }

    val tapToCopyText = stringResource(R.string.local_ip_tap_to_copy)
    val interfaceText = stringResource(R.string.local_ip_interface)

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.local_ip_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState),
        ) {
            if (ipv4List.isNotEmpty()) {
                SmallTitle(text = stringResource(R.string.local_ip_ipv4_section))
                ipv4List.forEach { ipInfo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        pressFeedbackType = PressFeedbackType.Sink,
                        showIndication = true,
                        onClick = {
                            NetworkUtils.copyToClipboard(context, ipInfo.address, "IPv4")
                        },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = ipInfo.address,
                                style = MiuixTheme.textStyles.title3,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = interfaceText.format(ipInfo.interfaceName),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tapToCopyText,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (ipv6List.isNotEmpty()) {
                SmallTitle(text = stringResource(R.string.local_ip_ipv6_section))
                ipv6List.forEach { ipInfo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        pressFeedbackType = PressFeedbackType.Sink,
                        showIndication = true,
                        onClick = {
                            NetworkUtils.copyToClipboard(context, ipInfo.address, "IPv6")
                        },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = ipInfo.address,
                                style = MiuixTheme.textStyles.title3,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = interfaceText.format(ipInfo.interfaceName),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tapToCopyText,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (allIps.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.local_ip_no_interface),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.local_ip_check_network),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
