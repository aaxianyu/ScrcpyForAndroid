package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.AppManagerMotion
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.MaskedTopBarAction
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.rememberAnimationsDisabled
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.rememberGlassHighlight
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.scaledBy
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.models.BatterySnapshot
import io.github.miuzarte.scrcpyforandroid.models.DiskSnapshot
import io.github.miuzarte.scrcpyforandroid.models.DisplaySnapshot
import io.github.miuzarte.scrcpyforandroid.models.MemSnapshot
import io.github.miuzarte.scrcpyforandroid.models.WmSetting
import io.github.miuzarte.scrcpyforandroid.models.gibText
import io.github.miuzarte.scrcpyforandroid.models.oneDecimal
import io.github.miuzarte.scrcpyforandroid.models.parseBatteryCurrent
import io.github.miuzarte.scrcpyforandroid.models.parseBatterySnapshot
import io.github.miuzarte.scrcpyforandroid.models.parseCarrier
import io.github.miuzarte.scrcpyforandroid.models.parseDfSnapshot
import io.github.miuzarte.scrcpyforandroid.models.parseDisplaySnapshot
import io.github.miuzarte.scrcpyforandroid.models.parseIpv4Addresses
import io.github.miuzarte.scrcpyforandroid.models.parseIpv6Addresses
import io.github.miuzarte.scrcpyforandroid.models.parseMaxCpuTemperature
import io.github.miuzarte.scrcpyforandroid.models.parseMemSnapshot
import io.github.miuzarte.scrcpyforandroid.models.parseNetworkType
import io.github.miuzarte.scrcpyforandroid.models.parseUptimeSeconds
import io.github.miuzarte.scrcpyforandroid.models.parseWifiSsid
import io.github.miuzarte.scrcpyforandroid.models.parseWmSetting
import io.github.miuzarte.scrcpyforandroid.models.pixelsText
import io.github.miuzarte.scrcpyforandroid.models.screenInches
import io.github.miuzarte.scrcpyforandroid.models.tenthsCelsiusText
import io.github.miuzarte.scrcpyforandroid.models.uptimeOf
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalBlurMode
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.SkeletonBar
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.ui.rememberPageScrollBehavior
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.util.Locale

/** 实时字段的刷新节拍；静态字段只在进页面与回前台时重读。 */
private const val LIVE_REFRESH_INTERVAL_MS = 2_000L

/** 静态批一次往返的上限；超时按连接失败处理，不让页面停在骨架。 */
private const val STATIC_BATCH_TIMEOUT_MS = 8_000L

/** 实时批的上限；超时按掉线处理并停止轮询。 */
private const val LIVE_BATCH_TIMEOUT_MS = 4_000L

/** 一次批量读到全部静态字段；列顺序与 [collectStatic] 里的命令顺序一一对应。 */
private data class StaticSnapshot(
    val model: String? = null,
    val androidVersion: String? = null,
    val apiLevel: Int? = null,
    val productDevice: String? = null,
    val serial: String? = null,
    val securityPatch: String? = null,
    val fingerprint: String? = null,
    val instructionSets: List<String> = emptyList(),
    val socModel: String? = null,
    val cores: Int? = null,
    val physicalSize: WmSetting? = null,
    val density: WmSetting? = null,
    val display: DisplaySnapshot = DisplaySnapshot(),
    val disk: DiskSnapshot = DiskSnapshot(),
    val selinux: String? = null,
    val wifiSsid: String? = null,
)

/** 每 2s 重读的一部分字段；设备掉线时整批失败，页面只把这些位置转成未知。 */
private data class LiveSnapshot(
    val mem: MemSnapshot = MemSnapshot(),
    val battery: BatterySnapshot = BatterySnapshot(),
    val cpuTemperature: Float? = null,
    val uptimeSeconds: Long? = null,
    val ipv4: List<String> = emptyList(),
    val ipv6: List<String> = emptyList(),
    val networkType: String? = null,
    val carrier: String? = null,
    val currentMicroAmps: Long? = null,
)

private data class DeviceInfoState(
    val loading: Boolean = true,
    val static: StaticSnapshot? = null,
    val live: LiveSnapshot? = null,
)

private suspend fun collectStatic(): StaticSnapshot {
    val output = NativeAdbService.shellBatch(timeoutMs = STATIC_BATCH_TIMEOUT_MS) {
        command("getprop ro.product.model")
        command("getprop ro.product.manufacturer")
        command("getprop ro.build.version.release")
        command("getprop ro.build.version.sdk")
        command("getprop ro.product.device")
        command("getprop ro.serialno")
        command("getprop ro.build.version.security_patch")
        command("getprop ro.build.fingerprint")
        command("getprop ro.product.cpu.abilist")
        command("getprop ro.soc.model")
        command("getprop ro.board.platform")
        command("getprop ro.hardware")
        command("nproc")
        command("wm size")
        command("wm density")
        command("dumpsys display | grep -m1 mBaseDisplayInfo")
        command("df -k /data")
        command("getenforce")
        command("cmd wifi status")
    }

    fun valueAt(index: Int): String? =
        output.getOrNull(index)?.trim().orEmpty().takeIf { it.isNotEmpty() }

    val manufacturer = valueAt(1)
    return StaticSnapshot(
        model = listOfNotNull(manufacturer, valueAt(0)).joinToString(" ").ifBlank { null },
        androidVersion = valueAt(2),
        apiLevel = valueAt(3)?.toIntOrNull(),
        productDevice = valueAt(4),
        serial = valueAt(5),
        securityPatch = valueAt(6),
        fingerprint = valueAt(7),
        instructionSets = valueAt(8)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
        socModel = valueAt(9) ?: valueAt(10) ?: valueAt(11),
        cores = valueAt(12)?.toIntOrNull(),
        physicalSize = valueAt(13)?.let { parseWmSetting(it, "size") },
        density = valueAt(14)?.let { parseWmSetting(it, "density") },
        display = parseDisplaySnapshot(valueAt(15).orEmpty()),
        disk = parseDfSnapshot(valueAt(16).orEmpty()),
        selinux = valueAt(17),
        wifiSsid = parseWifiSsid(valueAt(18).orEmpty()),
    )
}

private suspend fun collectLive(): LiveSnapshot {
    val output = NativeAdbService.shellBatch(timeoutMs = LIVE_BATCH_TIMEOUT_MS) {
        command("cat /proc/uptime")
        command("grep -E 'MemTotal|MemAvailable' /proc/meminfo")
        command("dumpsys battery | grep -E 'level:|status:|voltage:|temperature:'")
        command("dumpsys thermalservice | grep mName=CPU")
        command("ip -f inet addr")
        command("ip -f inet6 addr")
        command("getprop gsm.network.type")
        command("getprop gsm.operator.alpha")
        command("cat /sys/class/power_supply/battery/current_now 2>/dev/null; dumpsys battery | grep -i 'current now'")
    }

    fun valueAt(index: Int): String = output.getOrNull(index).orEmpty()
    return LiveSnapshot(
        mem = parseMemSnapshot(valueAt(1)),
        battery = parseBatterySnapshot(valueAt(2)),
        cpuTemperature = parseMaxCpuTemperature(valueAt(3)),
        uptimeSeconds = parseUptimeSeconds(valueAt(0)),
        ipv4 = parseIpv4Addresses(valueAt(4)),
        ipv6 = parseIpv6Addresses(valueAt(5)),
        networkType = parseNetworkType(valueAt(6)),
        carrier = parseCarrier(valueAt(7)),
        currentMicroAmps = parseBatteryCurrent(valueAt(8)),
    )
}

/**
 * 设备信息整页：首卡是面板概览，其余按「身份 / 处理器 / 网络 / 电池 / 其他」分卡。
 *
 * 字段分两批：静态批一次批量 shell 读全，进页面与回前台各读一次；实时批只读内存、电池、
 * 温度、地址与开机时长，前台每 2s 一次。单条命令失败只让对应字段落到「未知」；整批失败视为
 * 连接断开，给一次提示条并把实时字段转未知，不做整页报错与重试。
 */
@Composable
internal fun DeviceInfoScreen() {
    val navigator = LocalRootNavigator.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = rememberPageScrollBehavior()
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    val pageScroll = rememberScrollState()
    val density = LocalDensity.current
    val bundle by appSettings.bundleState.collectAsState()
    val blurMode = LocalBlurMode.current
    val topBarGlass = blurBackdrop != null &&
        blurMode == AppSettings.BlurMode.PROGRESSIVE &&
        bundle.floatingBottomBarBlur
    val maskEnabled = blurBackdrop != null && blurMode == AppSettings.BlurMode.PROGRESSIVE
    val maskAlpha = remember { Animatable(0f) }
    val topBarHighlight = rememberGlassHighlight()
    val animationsDisabled = rememberAnimationsDisabled()

    LaunchedEffect(maskEnabled, pageScroll) {
        if (!maskEnabled) {
            maskAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        snapshotFlow { with(density) { (pageScroll.value / 48.dp.toPx()).coerceIn(0f, 1f) } }
            .collectLatest {
                maskAlpha.animateTo(
                    targetValue = it,
                    animationSpec = tween(
                        durationMillis = AppManagerMotion.MASK_FADE_MS.scaledBy(animationsDisabled),
                        easing = LinearEasing,
                    ),
                )
            }
    }

    var headerHeightPx by remember { mutableStateOf(0) }
    val titleCollapse by remember {
        derivedStateOf {
            if (headerHeightPx <= 0) 0f else (pageScroll.value / (headerHeightPx * 0.7f)).coerceIn(0f, 1f)
        }
    }
    val titleVisible by remember {
        derivedStateOf { headerHeightPx > 0 && pageScroll.value >= headerHeightPx * 0.7f }
    }
    val title = stringResource(R.string.tools_device_info)
    val scope = rememberCoroutineScope()
    val snackHost = remember { SnackbarHostState() }
    var state by remember { mutableStateOf(DeviceInfoState()) }
    val currentState by rememberUpdatedState(state)
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var notifiedDisconnect by remember { mutableStateOf(false) }

    DisposableEffect(snackHost) {
        val unregister = AppRuntime.registerSnackbarHostState(snackHost)
        onDispose(unregister)
    }

    var pollingEnabled by remember { mutableStateOf(true) }

    fun notifyDisconnected() {
        if (notifiedDisconnect) return
        notifiedDisconnect = true
        AppRuntime.snackbar(snackHost, R.string.device_info_disconnected)
    }

    suspend fun refresh(showLoading: Boolean) {
        if (showLoading) state = state.copy(loading = true)
        val static = withContext(Dispatchers.IO) {
            try {
                collectStatic()
            } catch (_: TimeoutCancellationException) {
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        }
        if (static == null) {
            // 整批失败：字段落到「未知」，保留卡片结构；异常原文不展示给用户。
            state = state.copy(loading = false, static = StaticSnapshot(), live = null)
            pollingEnabled = false
            notifyDisconnected()
            return
        }
        val live = withContext(Dispatchers.IO) {
            try {
                collectLive()
            } catch (_: TimeoutCancellationException) {
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        }
        state = state.copy(loading = false, static = static, live = live)
        if (live == null) {
            pollingEnabled = false
            notifyDisconnected()
        } else {
            notifiedDisconnect = false
        }
    }

    LaunchedEffect(Unit) {
        refresh(showLoading = true)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    pollingEnabled = true
                    if (!currentState.loading) scope.launch { refresh(showLoading = false) }
                }

                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumed, pollingEnabled, state.loading) {
        if (!resumed || !pollingEnabled || state.loading) return@LaunchedEffect
        while (true) {
            delay(LIVE_REFRESH_INTERVAL_MS)
            val live = withContext(Dispatchers.IO) {
                try {
                    collectLive()
                } catch (_: TimeoutCancellationException) {
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                }
            }
            if (live == null) {
                // 掉线后停止轮询，等重新进页或回前台再试，避免每 2s 重复打一轮失败命令。
                state = state.copy(live = null)
                pollingEnabled = false
                notifyDisconnected()
                return@LaunchedEffect
            }
            state = state.copy(live = live)
            notifiedDisconnect = false
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(
                snackHost,
                modifier = Modifier.onGloballyPositioned { AppRuntime.markSnackbarHostStateLaidOut(snackHost) },
            )
        },
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                Box {
                    SmallTopAppBar(
                        title = "",
                        scrollBehavior = scrollBehavior,
                        color = if (blurActive) Color.Transparent else colorScheme.surface,
                        defaultWindowInsetsPadding = false,
                        navigationIcon = {
                            MaskedTopBarAction(
                                icon = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.cd_back),
                                onClick = {
                                    haptic.contextClick()
                                    navigator.pop()
                                },
                                maskAlpha = { maskAlpha.value },
                                glassActive = topBarGlass,
                                backdrop = blurBackdrop,
                                highlight = topBarHighlight,
                            )
                        },
                    )
                    DetailCollapsedTitle(title = title, visible = titleVisible)
                }
            }
        },
    ) { pagePadding ->
        Box(modifier = if (blurActive) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(pageScroll)
                    .padding(
                        start = UiSpacing.PageHorizontal,
                        end = UiSpacing.PageHorizontal,
                        top = pagePadding.calculateTopPadding() + UiSpacing.MediumLarge,
                        bottom = pagePadding.calculateBottomPadding() + UiSpacing.PageBottom,
                    ),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.MediumLarge),
            ) {
                Text(
                    text = title,
                    fontSize = MiuixTheme.textStyles.title1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    modifier = Modifier
                        // 与卡片内文字（卡片左右内边距）对齐，而不是卡片外缘。
                        .padding(start = UiSpacing.Large)
                        .onGloballyPositioned { headerHeightPx = it.size.height }
                        .graphicsLayer { alpha = 1f - titleCollapse },
                )
                val static = state.static
                if (static == null) {
                    LoadingCards()
                } else {
                    DeviceInfoCards(static = static, live = state.live)
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCards(static: StaticSnapshot, live: LiveSnapshot?) {
    val unknown = stringResource(R.string.device_info_unknown)

    val physicalSizeValue = static.physicalSize?.physical
    val physicalSizeText = pixelsText(physicalSizeValue)
    val currentSize = static.physicalSize?.let { it.override ?: it.physical }
    val currentSizeText = pixelsText(currentSize)
    val factorySizeText = if (currentSize != null && physicalSizeValue != null &&
        currentSize != physicalSizeValue && physicalSizeText != null
    ) {
        stringResource(R.string.device_info_factory, physicalSizeText)
    } else {
        null
    }
    val currentSizeDisplay = if (currentSizeText != null && factorySizeText != null) {
        stringResource(R.string.device_info_resolution_current, currentSizeText)
    } else {
        currentSizeText
    }

    val currentDensity = static.density?.let { it.override?.toIntOrNull() ?: it.physical.toIntOrNull() }
    val physicalDensity = static.density?.physical?.toIntOrNull()
    val factoryDensityText = if (currentDensity != null && physicalDensity != null && currentDensity != physicalDensity) {
        stringResource(R.string.device_info_factory_dpi, physicalDensity)
    } else {
        null
    }

    // 折叠屏可能从不同面板分别给出分辨率和 PPI；优先用 mBaseDisplayInfo 同一条记录里的真实像素，
    // 保证像素与 PPI 同源；PPI 缺失时退回同一条记录里的 density。
    val panelWidthPx = static.display.realWidthPx
        ?: static.physicalSize?.physical?.substringBefore('x')?.toIntOrNull()
    val panelHeightPx = static.display.realHeightPx
        ?: static.physicalSize?.physical?.substringAfter('x')?.toIntOrNull()
    val inches = if (panelWidthPx != null && panelHeightPx != null) {
        screenInches(panelWidthPx, panelHeightPx, static.display.ppiX, static.display.ppiY, static.display.densityDpi)
    } else {
        null
    }
    val screenSizeText = inches?.let { stringResource(R.string.device_info_inches, String.format(Locale.US, "%.2f", it)) }

    val totalMemBytes = live?.mem?.totalKb?.times(1024)
    val availableMemBytes = live?.mem?.availableKb?.times(1024)
    val usedMemBytes = if (totalMemBytes != null && availableMemBytes != null) totalMemBytes - availableMemBytes else null
    val ramText = totalMemBytes?.let { gibText(it, decimals = 2) }
    val ramProgress = if (totalMemBytes != null && totalMemBytes > 0 && usedMemBytes != null) {
        usedMemBytes.toFloat() / totalMemBytes
    } else {
        null
    }
    val ramDetail = availableMemBytes?.let {
        stringResource(R.string.device_info_available, gibText(it, decimals = 2))
    }

    val totalDiskBytes = static.disk.totalKb?.times(1024)
    val usedDiskBytes = static.disk.usedKb?.times(1024)
    val availableDiskBytes = static.disk.availableKb?.times(1024)
    val storageText = totalDiskBytes?.let { gibText(it) }
    val storageProgress = if (totalDiskBytes != null && totalDiskBytes > 0 && usedDiskBytes != null) {
        usedDiskBytes.toFloat() / totalDiskBytes
    } else {
        null
    }
    val storageDetail = availableDiskBytes?.let {
        stringResource(R.string.device_info_available, gibText(it))
    }

    val processorText = listOfNotNull(
        static.socModel,
        static.cores?.let { stringResource(R.string.device_info_core_count, it) },
    ).takeIf { it.isNotEmpty() }?.joinToString(" | ")

    val refreshRateText = static.display.refreshRates
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" / ")
        ?.let { stringResource(R.string.device_info_hz, it) }

    val ppiText = if (static.display.ppiX != null && static.display.ppiY != null) {
        stringResource(
            R.string.device_info_ppi_value,
            oneDecimal(static.display.ppiX.toDouble()),
            oneDecimal(static.display.ppiY.toDouble()),
        )
    } else {
        null
    }

    // abilist 的首项是 aarch64、armeabi-v7a 这类 ABI 名，按前缀归并成架构名。
    val cpuArch = static.instructionSets.firstOrNull()?.let { abi ->
        when {
            abi.startsWith("arm64", ignoreCase = true) || abi.equals("aarch64", ignoreCase = true) -> "AArch64"
            abi.startsWith("armeabi", ignoreCase = true) || abi.startsWith("armv", ignoreCase = true) -> "ARM"
            abi.startsWith("x86_64", ignoreCase = true) -> "x86-64"
            abi.startsWith("x86", ignoreCase = true) -> "x86"
            abi.startsWith("mips", ignoreCase = true) -> "MIPS"
            else -> abi
        }
    }

    val batteryStatusText = when (live?.battery?.status) {
        2 -> stringResource(R.string.device_info_battery_charging)
        3 -> stringResource(R.string.device_info_battery_discharging)
        4 -> stringResource(R.string.device_info_battery_not_charging)
        5 -> stringResource(R.string.device_info_battery_full)
        else -> null
    }

    // 充电功率并入电池状态行；双电芯机型按单路电流估算，可能与标称值有出入。
    val chargingPowerText = live?.let { snapshot ->
        val voltageMv = snapshot.battery.voltageMv
        val current = snapshot.currentMicroAmps
        if (snapshot.battery.status == 2 && voltageMv != null && current != null && current != 0L) {
            val watts = voltageMv.toDouble() * abs(current).toDouble() / 1_000_000_000.0
            if (watts > 0.0) stringResource(R.string.device_info_watt, oneDecimal(watts)) else null
        } else {
            null
        }
    }
    val batteryStatusValue = when {
        batteryStatusText == null -> null
        chargingPowerText != null -> "$batteryStatusText | $chargingPowerText"
        else -> batteryStatusText
    }

    val selinuxText = when (static.selinux?.lowercase(Locale.US)) {
        "enforcing" -> stringResource(R.string.device_info_selinux_enforcing)
        "permissive" -> stringResource(R.string.device_info_selinux_permissive)
        else -> static.selinux
    }

    val uptimeText = live?.uptimeSeconds?.let { total ->
        val parts = uptimeOf(total)
        when {
            parts.days > 0 -> stringResource(R.string.device_info_uptime_days, parts.days, parts.hours, parts.minutes)
            parts.hours > 0 -> stringResource(R.string.device_info_uptime_hours, parts.hours, parts.minutes)
            else -> stringResource(R.string.device_info_uptime_minutes, parts.minutes)
        }
    }

    val ipv4Text = live?.ipv4?.takeIf { it.isNotEmpty() }?.joinToString("\n")
    val ipv6Text = live?.ipv6?.takeIf { it.isNotEmpty() }?.joinToString("\n")

    InfoCard(spacing = UiSpacing.MediumLarge) {
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Large)) {
            StatCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.device_description_model,
                label = stringResource(R.string.device_info_device_model),
                value = static.model,
            )
            StatCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.device_description_android,
                label = stringResource(R.string.device_info_android_version),
                value = static.androidVersion,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Large)) {
            StatCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.device_description_resolution,
                label = stringResource(R.string.device_info_resolution),
                value = currentSizeDisplay,
                subline = factorySizeText,
            )
            StatCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.device_description_screen,
                label = stringResource(R.string.device_info_screen_size),
                value = screenSizeText,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Large)) {
            StatCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.device_description_ram,
                label = stringResource(R.string.device_info_ram),
                value = ramText,
                progress = ramProgress,
                detail = ramDetail,
            )
            StatCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.device_description_storage,
                label = stringResource(R.string.device_info_storage),
                value = storageText,
                progress = storageProgress,
                detail = storageDetail,
            )
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.device_description_cpu),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(UiSpacing.Small))
                Text(
                    text = stringResource(R.string.device_info_cpu_model),
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(UiSpacing.Small))
            Text(
                text = processorText ?: unknown,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (processorText == null) colorScheme.onSurfaceVariantSummary else colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    InfoCard {
        InfoRow(
            label = stringResource(R.string.device_info_density),
            value = currentDensity?.let {
                if (factoryDensityText != null) stringResource(R.string.device_info_dpi_current, it)
                else stringResource(R.string.device_info_dpi, it)
            },
            subline = factoryDensityText,
        )
        InfoRow(label = stringResource(R.string.device_info_ppi), value = ppiText)
        InfoRow(label = stringResource(R.string.device_info_refresh_rate), value = refreshRateText)
    }

    InfoCard {
        InfoRow(label = stringResource(R.string.device_info_product_device), value = static.productDevice)
        InfoRow(label = stringResource(R.string.device_info_serial), value = static.serial)
        InfoRow(
            label = stringResource(R.string.device_info_api_level),
            value = static.apiLevel?.toString(),
        )
        InfoRow(label = stringResource(R.string.device_info_security_patch), value = static.securityPatch)
        InfoRow(
            label = stringResource(R.string.device_info_fingerprint),
            value = static.fingerprint,
            multiline = true,
        )
    }

    InfoCard {
        InfoRow(label = stringResource(R.string.device_info_cpu_arch), value = cpuArch)
        InfoRow(
            label = stringResource(R.string.device_info_cpu_isa),
            value = static.instructionSets.takeIf { it.isNotEmpty() }?.joinToString(" / "),
            multiline = true,
        )
        InfoRow(
            label = stringResource(R.string.device_info_cpu_temperature),
            value = live?.cpuTemperature?.let {
                stringResource(R.string.device_info_celsius, oneDecimal(it.toDouble()))
            },
        )
    }

    InfoCard {
        InfoRow(label = stringResource(R.string.device_info_network_type), value = live?.networkType)
        InfoRow(label = stringResource(R.string.device_info_carrier), value = live?.carrier)
        InfoRow(label = stringResource(R.string.device_info_ssid), value = static.wifiSsid)
        InfoRow(
            label = stringResource(R.string.device_info_ipv4),
            value = ipv4Text,
            multiline = true,
            copyText = ipv4Text,
        )
        InfoRow(
            label = stringResource(R.string.device_info_ipv6),
            value = ipv6Text,
            multiline = true,
            copyText = ipv6Text,
        )
    }

    InfoCard {
        InfoRow(
            label = stringResource(R.string.device_info_battery_level),
            value = live?.battery?.level?.let { stringResource(R.string.device_info_percent, it) },
        )
        InfoRow(label = stringResource(R.string.device_info_battery_status), value = batteryStatusValue)
        InfoRow(
            label = stringResource(R.string.device_info_battery_voltage),
            value = live?.battery?.voltageMv?.let { stringResource(R.string.device_info_millivolt, it) },
        )
        InfoRow(
            label = stringResource(R.string.device_info_battery_temperature),
            value = tenthsCelsiusText(live?.battery?.temperatureTenthsC)
                ?.let { stringResource(R.string.device_info_celsius, it) },
        )
    }

    InfoCard {
        InfoRow(label = stringResource(R.string.device_info_uptime), value = uptimeText)
        InfoRow(label = stringResource(R.string.device_info_selinux), value = selinuxText)
    }
}

@Composable
private fun InfoCard(spacing: Dp = UiSpacing.Small, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = UiSpacing.Large, vertical = UiSpacing.ContentVertical),
        colors = CardDefaults.defaultColors(color = colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

@Composable
private fun StatCell(
    modifier: Modifier = Modifier,
    iconRes: Int?,
    label: String,
    value: String?,
    subline: String? = null,
    progress: Float? = null,
    detail: String? = null,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(UiSpacing.Small))
            }
            Text(
                text = label,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(UiSpacing.Small))
        Text(
            text = value ?: stringResource(R.string.device_info_unknown),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (value == null) colorScheme.onSurfaceVariantSummary else colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (progress != null) {
            Spacer(Modifier.height(UiSpacing.Small))
            LinearProgressIndicator(
                progress = progress,
                height = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (detail != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        if (subline != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subline,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String?,
    subline: String? = null,
    multiline: Boolean = false,
    copyText: String? = null,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val copyLabel = stringResource(R.string.device_info_copy)
    val onCopy: (() -> Unit)? = copyText?.let { text ->
        val action: () -> Unit = {
            haptic.contextClick()
            LocalInputService.setClipboardText(context, text)
            AppRuntime.snackbar(R.string.device_info_copied, label)
        }
        action
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onCopy != null) Modifier.clip(RoundedCornerShape(8.dp)).copyableText(onCopy) else Modifier)
            .padding(vertical = UiSpacing.Small),
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
        ) {
            Text(
                text = value ?: stringResource(R.string.device_info_unknown),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
                maxLines = if (multiline) 6 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (onCopy != null) {
                Spacer(Modifier.width(UiSpacing.Small))
                Icon(
                    imageVector = MiuixIcons.Copy,
                    contentDescription = copyLabel,
                    modifier = Modifier.size(12.dp),
                    tint = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (subline != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subline,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun Modifier.copyableText(onCopy: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return pressable(interactionSource = interactionSource, indication = SinkFeedback())
        .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onCopy)
}

@Composable
private fun LoadingCards() {
    InfoCard(spacing = UiSpacing.MediumLarge) {
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Large)) {
            SkeletonStatCell(
                iconRes = R.drawable.device_description_model,
                label = stringResource(R.string.device_info_device_model),
                valueWidth = 120.dp,
            )
            SkeletonStatCell(
                iconRes = R.drawable.device_description_android,
                label = stringResource(R.string.device_info_android_version),
                valueWidth = 36.dp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Large)) {
            SkeletonStatCell(
                iconRes = R.drawable.device_description_resolution,
                label = stringResource(R.string.device_info_resolution),
                valueWidth = 112.dp,
            )
            SkeletonStatCell(
                iconRes = R.drawable.device_description_screen,
                label = stringResource(R.string.device_info_screen_size),
                valueWidth = 72.dp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.Large)) {
            SkeletonStatCell(
                iconRes = R.drawable.device_description_ram,
                label = stringResource(R.string.device_info_ram),
                valueWidth = 64.dp,
                progress = true,
            )
            SkeletonStatCell(
                iconRes = R.drawable.device_description_storage,
                label = stringResource(R.string.device_info_storage),
                valueWidth = 64.dp,
                progress = true,
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            StatLabel(iconRes = R.drawable.device_description_cpu, label = stringResource(R.string.device_info_cpu_model))
            Spacer(Modifier.height(UiSpacing.Small))
            SkeletonBar(width = 160.dp, height = 19.dp)
        }
    }
    InfoCard {
        InfoRowSkeleton(stringResource(R.string.device_info_density))
        InfoRowSkeleton(stringResource(R.string.device_info_ppi), valueWidth = 120.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_refresh_rate), valueWidth = 140.dp)
    }
    InfoCard {
        InfoRowSkeleton(stringResource(R.string.device_info_product_device))
        InfoRowSkeleton(stringResource(R.string.device_info_serial), valueWidth = 110.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_api_level), valueWidth = 28.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_security_patch), valueWidth = 96.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_fingerprint), valueLines = 2)
    }
    InfoCard {
        InfoRowSkeleton(stringResource(R.string.device_info_cpu_arch), valueWidth = 72.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_cpu_isa), valueLines = 2)
        InfoRowSkeleton(stringResource(R.string.device_info_cpu_temperature), valueWidth = 48.dp)
    }
    InfoCard {
        InfoRowSkeleton(stringResource(R.string.device_info_network_type), valueWidth = 56.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_carrier), valueWidth = 72.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_ssid), valueWidth = 40.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_ipv4), valueLines = 2, valueWidth = 100.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_ipv6), valueLines = 3, valueWidth = 100.dp)
    }
    InfoCard {
        InfoRowSkeleton(stringResource(R.string.device_info_battery_level), valueWidth = 36.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_battery_status), valueWidth = 56.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_battery_voltage), valueWidth = 60.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_battery_temperature), valueWidth = 40.dp)
    }
    InfoCard {
        InfoRowSkeleton(stringResource(R.string.device_info_uptime), valueWidth = 140.dp)
        InfoRowSkeleton(stringResource(R.string.device_info_selinux), valueWidth = 36.dp)
    }
}

/** 首卡内的图标+标签行；骨架与真实卡片共用，仅取值位置换成占位条。 */
@Composable
private fun StatLabel(iconRes: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(UiSpacing.Small))
        Text(
            text = label,
            fontSize = 13.sp,
            color = colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.SkeletonStatCell(
    iconRes: Int,
    label: String,
    valueWidth: Dp = 120.dp,
    progress: Boolean = false,
) {
    Column(modifier = Modifier.weight(1f)) {
        StatLabel(iconRes = iconRes, label = label)
        Spacer(Modifier.height(UiSpacing.Small))
        if (progress) {
            // 值、进度条与明细合成一块等高的占位区，不出现长短不一的三条细条。
            SkeletonBar(height = 48.dp)
        } else {
            SkeletonBar(width = valueWidth, height = 19.dp)
        }
    }
}

@Composable
private fun InfoRowSkeleton(label: String, valueLines: Int = 1, valueWidth: Dp = 150.dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UiSpacing.Small),
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        repeat(valueLines) { index ->
            if (index > 0) Spacer(Modifier.height(2.dp))
            SkeletonBar(width = if (index == 0) valueWidth else 120.dp, height = 15.dp)
        }
    }
}

