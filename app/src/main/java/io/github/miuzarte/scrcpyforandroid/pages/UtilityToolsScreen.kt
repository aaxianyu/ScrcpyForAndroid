package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.SheetActionRow
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.dialogTitleColor
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.io.File
import java.io.IOException
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.miuzarte.scrcpyforandroid.appmanager.data.AppProbeClient
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.DisplayOptionsMenu
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.RefreshState
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.extended.Tune
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.squircle.squircleClip
import androidx.compose.foundation.background
import kotlinx.coroutines.Job

private const val REMOTE_SHOT_DIR = "/data/local/tmp"
private const val SCREENSHOT_DIR = "screenshots"
private const val DENSITY_MIN = 80
private const val DENSITY_MAX = 640
private const val RES_MIN = 320
private const val RES_MAX = 4320
private const val ADB_TCP_DEFAULT_PORT = 5555
private const val ADB_TCP_PORT_MIN = 1024
private const val ADB_TCP_PORT_MAX = 65535
// 重启/关机必然切断连接：发出命令后最多等这么久就必须让用户去看设备，回读永远等不到有效结果
private const val REBOOT_RESULT_WAIT_MS = 3_000L


private data class ActivatableApp(
    val name: String,
    val packageName: String,
    val startupScripts: List<String>,
)

// 激活失败时把最后一条 shell 输出带给用户
private class ActivationFailure(output: String) : Exception(output)

// 各应用的激活入口脚本路径为公开约定，按包名探测是否已安装
private val KNOWN_ACTIVATABLES = listOf(
    ActivatableApp(
        name = "Shizuku",
        packageName = "moe.shizuku.privileged.api",
        startupScripts = listOf(
            "sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh",
            "APK=\$(pm path moe.shizuku.privileged.api | head -1 | cut -d: -f2); \$(dirname \$APK)/lib/arm64/libshizuku.so",
        ),
    ),
    ActivatableApp("冰箱", "com.catchingnow.icebox",
        listOf("sh /sdcard/Android/data/com.catchingnow.icebox/files/start.sh")),
    ActivatableApp("黑域", "me.piebridge.brevent",
        listOf("sh /data/data/me.piebridge.brevent/brevent.sh")),
    ActivatableApp("小黑屋", "web1n.stopapp",
        listOf("sh /storage/emulated/0/Android/data/web1n.stopapp/files/starter.sh")),
)

private fun extractDensity(shellOutput: String, kind: String): String {
    val target = "$kind density:"
    return shellOutput.lineSequence()
        .firstOrNull { it.startsWith(target) }
        ?.removePrefix(target)?.trim().orEmpty()
}

private fun extractDisplaySize(shellOutput: String, kind: String): String {
    val target = "$kind size:"
    return shellOutput.lineSequence()
        .firstOrNull { it.startsWith(target) }
        ?.removePrefix(target)?.trim().orEmpty()
}

// 从 shell 输出中取以「key:」开头的那一行的值
private fun lineValue(shellOutput: String, key: String): String {
    val target = "$key:"
    return shellOutput.lineSequence()
        .firstOrNull { it.trim().startsWith(target) }
        ?.trim()?.removePrefix(target)?.trim().orEmpty()
}

private sealed interface SpecsState {
    data object Loading : SpecsState
    data class Failed(val reason: String) : SpecsState
    data class Ready(val specs: List<DeviceSpec>) : SpecsState
}

private class DeviceSpec(
    val labelRes: Int,
    val value: String,
)

// 合并为一条 shell 命令取全部属性，避免多次往返
private const val PROP_BUNDLE_COMMAND =
    "getprop ro.product.model; getprop ro.product.manufacturer; getprop ro.build.version.release; " +
        "getprop ro.build.version.sdk; getprop ro.product.cpu.abi; getprop ro.hardware; getprop ro.serialno"

private suspend fun collectDeviceSpecs(
    charging: String,
    full: String,
    discharging: String,
): List<DeviceSpec> = withContext(Dispatchers.IO) {
    NativeAdbService.ensureConnectionResponsive()
    val props = NativeAdbService.shell(PROP_BUNDLE_COMMAND)
        .lineSequence()
        .map { it.trim() }
        .toList()

    // 各项独立命令并行拉取，async 继承 IO 调度器才真正并发
    coroutineScope {
        listOf(
            async {
                DeviceSpec(R.string.tools_info_model, "${props.getOrElse(1) { "" }} ${props.getOrElse(0) { "" }}".trim())
            },
            async {
                val version = props.getOrElse(2) { "" }
                val sdk = props.getOrElse(3) { "" }
                DeviceSpec(
                    R.string.tools_info_android_version,
                    if (sdk.isNotBlank()) "$version (SDK $sdk)" else version,
                )
            },
            async {
                val cpu = NativeAdbService.shell("cat /proc/cpuinfo")
                val hardware = lineValue(cpu, "Hardware").ifBlank { props.getOrElse(5) { "" } }
                DeviceSpec(R.string.tools_info_cpu_model, hardware)
            },
            async { DeviceSpec(R.string.tools_info_cpu_abi, props.getOrElse(4) { "" }) },
            async {
                val mem = NativeAdbService.shell("cat /proc/meminfo")
                val total = lineValue(mem, "MemTotal").replace("kB", "").trim().toLongOrNull() ?: 0L
                val avail = lineValue(mem, "MemAvailable").replace("kB", "").trim().toLongOrNull() ?: 0L
                DeviceSpec(R.string.tools_info_ram, if (total > 0L) "${readableSize(avail)} / ${readableSize(total)}" else "unknown")
            },
            async {
                val size = NativeAdbService.shell("wm size")
                DeviceSpec(R.string.tools_info_screen, extractDisplaySize(size, "Physical"))
            },
            async {
                val density = NativeAdbService.shell("wm density")
                DeviceSpec(R.string.tools_info_density, extractDensity(density, "Physical"))
            },
            async { DeviceSpec(R.string.tools_info_serial, props.getOrElse(6) { "" }) },
            async {
                val battery = NativeAdbService.shell("dumpsys battery")
                val level = lineValue(battery, "level")
                val status = when (lineValue(battery, "status")) {
                    "2" -> charging
                    "5" -> full
                    else -> discharging
                }
                val tenths = lineValue(battery, "temperature").toIntOrNull() ?: 0
                DeviceSpec(R.string.tools_info_battery, "$level% ($status) ${tenths / 10}.${tenths % 10}°C")
            },
            async {
                val df = NativeAdbService.shell("df -h /data")
                val row = df.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.elementAtOrNull(1)
                val columns = row?.split(Regex("\\s+")).orEmpty()
                DeviceSpec(
                    R.string.tools_info_storage,
                    if (columns.size > 3) "${columns[3]} / ${columns[1]}" else "unknown",
                )
            },
        ).awaitAll()
    }.filter { it.value.isNotBlank() && it.value != "unknown" }
}

private fun readableSize(kilobytes: Long): String =
    if (kilobytes >= 1048576L) "%.1f GB".format(kilobytes / 1048576.0)
    else "%.1f MB".format(kilobytes / 1024.0)

@Composable
internal fun UtilityToolsScreen(
    scrollBehavior: ScrollBehavior,
    switchAdbPort: suspend (Int) -> Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val navigator = LocalRootNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null

    // NavDisplay 里本页在 Home 之上渲染，Home 的 snackbar 宿主被本页遮挡，
    // 必须自注册一个，否则全部提示渲染在页面背后
    val snackHostState = remember { SnackbarHostState() }
    DisposableEffect(snackHostState) {
        val unregister = AppRuntime.registerSnackbarHostState(snackHostState)
        onDispose(unregister)
    }

    // SAF 选择器弹出时 Activity 可能被系统回收，rememberSaveable 才能在重建后捞回待保存的临时文件
    var pendingShot by rememberSaveable { mutableStateOf<File?>(null) }
    var showRebootDialog by remember { mutableStateOf(false) }
    // 弹窗列表里选中了哪个模式，等待二次确认；null 表示没有待确认的操作
    var pendingReboot by remember { mutableStateOf<RebootMode?>(null) }
    // 目标设备 SDK：null 表示还没查过或查询失败，此时软重启一律不展示
    var targetSdk by remember { mutableStateOf<Int?>(null) }
    var densitySheetOpen by remember { mutableStateOf(false) }
    var stockDensity by remember { mutableStateOf("") }
    var activeDensity by remember { mutableStateOf("") }
    var resolutionSheetOpen by remember { mutableStateOf(false) }
    var stockResolution by remember { mutableStateOf("") }
    var activeResolution by remember { mutableStateOf("") }
    var adbOverTcpOn by remember { mutableStateOf(false) }
    var activeTcpPort by remember { mutableStateOf("") }
    var adbOverTcpBusy by remember { mutableStateOf(false) }
    var portSheetOpen by remember { mutableStateOf(false) }
    var activationSheetOpen by remember { mutableStateOf(false) }
    var detectedActivatables by remember { mutableStateOf<List<ActivatableApp>>(emptyList()) }
    var activationLoading by remember { mutableStateOf(false) }
    var activationBusy by remember { mutableStateOf(false) }
    var activationDetectFailed by remember { mutableStateOf(false) }
    var specsSheetOpen by remember { mutableStateOf(false) }
    var processSheetOpen by remember { mutableStateOf(false) }
    var processes by remember { mutableStateOf<List<DeviceProcess>>(emptyList()) }
    var processLoading by remember { mutableStateOf(false) }
    // 接给 PullToRefresh 的 isRefreshing：指示器「保持展开到数据回来」由它承重。与
    // processLoading 分开——首屏加载也走 loadProcesses，但那时不该给指示器任何动作。
    // 置位时机是关键：不能等 onRefresh 才置（组件自己那层作用域会先重组，读到的参数还是
    // 上一拍的 false，当场把刷新判成「已结束」，用户看到两段式）；而是拉过阈值就预置位
    // （ThresholdReached 时置 true），给父链足够时间把 true 组合进组件，松手后组件走
    // 「加入进行中的刷新」。回拉到阈值下、松手未触发、刷新收尾时撤销。见 ProcessSheet
    // 里的状态观察与 loadProcesses 的注释。
    var processRefreshing by remember { mutableStateOf(false) }
    // 本次下拉周期是否已经起过加载。组件跳过 onRefresh（预置位赶上了组合）时由状态观察起，
    // 没跳过时 onRefresh 兜底起，两个入口都要过它防重。每个下拉周期在 ThresholdReached 归零。
    var processPullLoadStarted by remember { mutableStateOf(false) }
    // 本次下拉周期的「取消态」：拉过阈值后又被拖回阈值下（含拖回起点之上、状态直接落 Idle）
    // 时置位。挡住一条边界竞态——取消的拖拽仍可能让组件因组合滞后误展开一次（showRefreshing
    // 读到的是尚未更新的 true），此时不得再起加载；下一次 ThresholdReached 归零。
    var processPullCancelled by remember { mutableStateOf(false) }
    // 最近一次加载失败的文案；非 null 时弹层显示失败占位，不显示「没有正在运行的应用」
    var processLoadFailed by remember { mutableStateOf<String?>(null) }
    // 当前这次进程列表加载的协程：新的加载先把上一次取消，避免慢的旧请求盖掉新的结果
    var processLoadJob by remember { mutableStateOf<Job?>(null) }
    // 正在结束进程的行：命令加回读约两秒，这期间按钮置灰防连点
    var stoppingPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 系统应用默认展示：进程管理器本就该看得见它们占了多少内存
    var hideSystemProcesses by rememberSaveable { mutableStateOf(false) }

    val saveShotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri: Uri? ->
        val local = pendingShot
        pendingShot = null
        if (uri == null || local == null) {
            // 用户放弃保存或没有待处理截图：清理缓存
            local?.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                resolver.openOutputStream(uri)?.use { os ->
                    local.inputStream().use { input -> input.copyTo(os) }
                } ?: throw IOException(resolver.toString())
                local.delete()
            }
                .onSuccess { AppRuntime.snackbar(R.string.tools_screenshot_saved, local.name) }
                .onFailure {
                    local.delete()
                    AppRuntime.snackbar(R.string.tools_screenshot_failed, it.message ?: "")
                }
        }
    }

    fun enableAdbOverTcp(port: Int) {
        if (adbOverTcpBusy) return
        scope.launch {
            if (!NativeAdbService.isConnected()) {
                AppRuntime.snackbar(R.string.tools_not_connected)
                return@launch
            }
            adbOverTcpBusy = true
            val outcome = runCatching { switchAdbPort(port) }
            adbOverTcpBusy = false
            outcome.onSuccess { switched ->
                if (switched) {
                    adbOverTcpOn = true
                    activeTcpPort = port.toString()
                    AppRuntime.snackbar(R.string.tools_wireless_adb_on, port as Any)
                } else {
                    AppRuntime.snackbar(R.string.tools_wireless_adb_failed, AppRuntime.stringResource(R.string.tools_apply_not_effective))
                }
            }.onFailure {
                if (it is CancellationException) throw it
                if (it is AdbPortReconnectFailure) {
                    adbOverTcpOn = true
                    activeTcpPort = port.toString()
                    AppRuntime.snackbar(R.string.tools_wireless_adb_reconnect_failed, port, it.message ?: "")
                } else {
                    AppRuntime.snackbar(R.string.tools_wireless_adb_failed, it.message ?: "")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            // 属性可能残留禁用哨兵值或异常文本：只有合法端口号才视为已启用
            val port = runCatching {
                NativeAdbService.shell("getprop service.adb.tcp.port").trim()
            }.getOrDefault("")
            val valid = port.toIntOrNull() in 1024..65535
            adbOverTcpOn = valid
            activeTcpPort = if (valid) port else ""
        }
    }

    fun openSpecsSheet() {
        scope.launch {
            if (!NativeAdbService.isConnected()) {
                AppRuntime.snackbar(R.string.tools_not_connected)
                return@launch
            }
            specsSheetOpen = true
        }
    }

    /**
     * 拉一次进程列表。
     *
     * 五次 shell：`ps -A` 取进程，`pm list packages -s` 与 `-3` 取系统 / 第三方包集合，
     * 探测程序按包名回传显示名与图标。归类靠那两个集合的归属而不是进程名的形态——设备上
     * 跑着的大多数进程是内核线程与 HAL 服务，它们同样带点号，靠名字筛会把一堆杀不掉的东西
     * 列进来。
     *
     * 取名与图标失败不算这次加载失败：列表本身已经拿到，回落到显示包名即可。
     *
     * 「读不出来」和「真的没有」必须分开，`ps -A` 与 `pm list packages` 两条命令各自校验：
     * 前者表头认不出、后者一条 `package:` 行都没有，都按加载失败处理并单独报错。放任它们
     * 变成空集合的话，界面会显示「没有正在运行的应用」，而实际是这次读取没成——那是本次
     * 审核里最容易被当成正常结果的一种失败。
     *
     * 失败时保留上一次成功的列表并置 [processLoadFailed]，让弹层显示失败占位而不是空态；
     * 没有上一次结果时才真的落到「读不出来」的占位上。
     *
     * 上一次加载先取消掉：连点下拉刷新会同时跑两个协程，谁后返回谁写列表，慢的那次可能是
     * 旧请求，回来就把新结果盖掉了；`processLoading` 也会被先完成的那个提前置回 false。
     *
     * [fromPull] 区分「下拉刷新」与「首屏加载」，两处必须分开：
     *
     * 其一，指示器接线。只有下拉触发的加载才接 PullToRefresh 的 isRefreshing，首屏加载
     * 不接——isRefreshing 抬起会让组件程序化展开指示器，首屏不该冒「正在刷新」。指示器
     * 保持到数据真正回来：期间组件在 Refreshing 态会消费全部嵌套滚动（列表暂时锁住），
     * 这是「正在刷新… → 刷新完成」如实反馈的代价。
     *
     * 其二，两段式的绕法。在 onRefresh 里同步置位治不了它：组件自身作用域先因 refreshState
     * 写入重组，那一刻读到的 isRefreshing 参数还是上一拍的 false，当场把刷新判成「已结束」
     * （收起、RefreshComplete）；等父链把 true 组合进来（实测晚 ~130ms）状态已回 Idle，
     * 又被程序化展开——两段就是这么来的。问题不在写入时刻，在参数到达时刻。所以真置位靠
     * 拉过阈值时的**预置位**（见 ProcessSheet 的状态观察）：拖拽期提前把 true 组合进去，
     * 松手时组件走「加入进行中的刷新」，跳过 onRefresh，加载由状态观察起；这里的同步置位
     * 只当兜底（快速甩拉、预置位没赶上组合时 onRefresh 仍会被调用）。两个起加载的入口
     * 用 [processPullLoadStarted] 防重；取消态（[processPullCancelled]）期内不起加载——
     * 拖回起点之上时组件可能因组合滞后误展开一次，展开自行收尾，但绝不附带真实刷新
     * （见 onPullStateChanged 的注释）。
     *
     * 其三，下拉刷新跳过 meta 探测（[fetchProcessMeta] 每次都要推 dex 分批跑探测，是 3.3 秒
     * 里的大头），按包名复用上一轮列表里的标签与图标；新出现的包没有旧值，回落显示包名。
     * 刷新加载因此降到一秒上下，锁窗口与文件管理的刷新同级。
     *
     * 刷新途中被新一轮加载取消（关浮层再打开）时旧协程走不到收尾，指示器的归零由新一轮
     * 在同一步同步兜底，不留卡住的指示器。
     */
    fun loadProcesses(fromPull: Boolean = false) {
        processLoadJob?.cancel()
        // 同步置位，覆盖 onRefresh 兜底路径（预置位没赶上组合时）与非下拉路径的清场：
        // 首屏加载置回 false，浮层重新打开时顺带清掉上一轮可能残留的预置位。预置位已
        // 置 true 的路径这里重复置位不产生额外重组。
        processRefreshing = fromPull
        if (fromPull) processPullLoadStarted = true
        processLoadJob = scope.launch {
            processLoading = true
            processLoadFailed = null
            // 上一轮的标签/图标：下拉刷新跳过 meta 探测（见 KDoc），按包名复用。快照在
            // 协程起点（主线程）取一次，协程体内只读，避免跨线程读组合状态。
            val reuseMeta = if (fromPull) processes.associateBy { it.packageName } else emptyMap()
            runCatching {
                withContext(Dispatchers.IO) {
                    val raw = NativeAdbService.shell("ps -A")
                    if (!hasProcessTableHeader(raw)) {
                        throw ProcessListUnsupportedException()
                    }
                    val systemOut = NativeAdbService.shell("pm list packages -s")
                    val thirdPartyOut = NativeAdbService.shell("pm list packages -3")
                    if (!hasPackageListLines(systemOut) || !hasPackageListLines(thirdPartyOut)) {
                        throw PackageListUnavailableException()
                    }
                    val rows = parseProcessRows(raw)
                    val classified = classifyProcesses(
                        rows = rows,
                        systemPackages = parsePackageSet(systemOut),
                        thirdPartyPackages = parsePackageSet(thirdPartyOut),
                    )
                    val meta = if (fromPull) {
                        null
                    } else {
                        runCatching { fetchProcessMeta(classified.map { it.packageName }) }.getOrNull()
                    }
                    classified.map { entry ->
                        val reused = reuseMeta[entry.packageName]
                        entry.copy(
                            label = meta?.labels?.get(entry.packageName) ?: reused?.label,
                            icon = meta?.icons?.get(entry.packageName) ?: reused?.icon,
                        )
                    }
                }
            }.onSuccess {
                processes = it
            }
                .onFailure { error ->
                    // 取消不是失败：连点刷新时上一个协程被取消，走到这里报一条错误是假的
                    if (error is CancellationException) throw error
                    val message = when (error) {
                        is ProcessListUnsupportedException ->
                            AppRuntime.stringResource(R.string.tools_process_ps_unsupported)
                        is PackageListUnavailableException ->
                            AppRuntime.stringResource(R.string.tools_process_pm_unsupported)
                        else -> AppRuntime.stringResource(
                            R.string.tools_process_failed,
                            error.message ?: "",
                        )
                    }
                    processLoadFailed = message
                    AppRuntime.snackbar(message)
                }
            processLoading = false
            processRefreshing = false
        }
    }

    /**
     * 结束一个包的全部进程，结果按停止前后的 PID 比对给（判定逻辑见 `forceStopProcess`）。
     *
     * 失败时把命令自己的输出带出去：shell v1 没有退出码，系统包被拒时只有输出里有话可说。
     * 成功后就地摘掉那一行，不再整表重拉：判定已经确认旧进程没了，重拉一次要一秒多，
     * 这一秒里按钮已经恢复成「结束」而那一行还杵在列表里，用户只会再点一次。
     *
     * 「已结束」与「已结束，但系统重新启动了它」共用摘行：这一行代表用户要结束的那个进程，
     * 旧进程确认消失后它就该离开列表；被系统保活拉回来的新进程由提示条说明，
     * 否则用户看着行还在会以为没杀掉、再点一次。
     *
     * 回读不可读时既不报成功也不摘行，另给一条「无法确认」的文案：设备在那一刻读不到进程
     * 表，说「已结束」是说谎。
     */
    fun stopProcess(process: DeviceProcess) {
        val name = process.label ?: process.packageName
        // 点击即刻反馈靠按钮本身：force-stop 加回读要两秒左右，这期间按钮换成进度圈并
        // 禁用，不接第二次点击。不再另发一条「正在结束」的提示条——浮层里的提示条会盖住
        // 列表，两秒后又被结果那条顶掉，净效果是糊住半屏列表。
        if (process.packageName in stoppingPackages) return
        stoppingPackages = stoppingPackages + process.packageName
        scope.launch {
            try {
                val outcome = runCatching {
                    withContext(Dispatchers.IO) {
                        forceStopProcess(process.packageName) { NativeAdbService.shell(it) }
                    }
                }
                outcome.onSuccess { result ->
                    when {
                        result.readbackUnavailable ->
                            AppRuntime.snackbar(R.string.tools_process_stop_unknown, name)

                        result.gone -> {
                            AppRuntime.snackbar(
                                if (result.restarted) R.string.tools_process_stopped_restarted
                                else R.string.tools_process_stopped,
                                name,
                            )
                            processes = processes.filterNot { it.packageName == process.packageName }
                        }

                        result.reason.isBlank() ->
                            // 系统包被拒时命令不吐任何东西，只报包名：没有原因可带，
                            // 带一个空冒号出去比不带更像出错了
                            AppRuntime.snackbar(R.string.tools_process_stop_failed, name)

                        else ->
                            AppRuntime.snackbar(R.string.tools_process_stop_failed_reason, name, result.reason)
                    }
                }.onFailure { error ->
                    // 取消不是失败：离开本页时 scope 被取消，走到这条会播一条不存在的
                    // 「未能结束」。与 loadProcesses 同一口径，直接重抛。
                    if (error is CancellationException) throw error
                    AppRuntime.snackbar(
                        R.string.tools_process_stop_failed_reason,
                        name,
                        error.message ?: "",
                    )
                }
            } finally {
                // 无论成败都解锁这一行：命令异常或协程取消的路径也会走到这里，
                // 否则按钮会永久置灰
                stoppingPackages = stoppingPackages - process.packageName
            }
        }
    }


    fun openActivationSheet() {
        if (activationBusy) return
        scope.launch {
            if (!NativeAdbService.isConnected()) {
                AppRuntime.snackbar(R.string.tools_not_connected)
                return@launch
            }
            activationLoading = true
            activationSheetOpen = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val installed = NativeAdbService.shell("pm list packages")
                        .lineSequence()
                        .map { it.removePrefix("package:").trim() }
                        .toSet()
                    detectedActivatables = KNOWN_ACTIVATABLES.filter { it.packageName in installed }
                }
            }.onFailure {
                activationDetectFailed = true
                AppRuntime.snackbar(R.string.tools_activate_detect_failed, it.message ?: "")
            }.onSuccess {
                activationDetectFailed = false
            }
            activationLoading = false
        }
    }

    fun activateApps(apps: List<ActivatableApp>) {
        // 立即关闭弹窗，执行结果用 snackbar 报告，避免脚本卡住时用户被困在弹窗里
        activationSheetOpen = false
        scope.launch {
            activationBusy = true
            for (app in apps) {
                AppRuntime.snackbar(R.string.tools_activate_executing, app.name)
                val outcome = runCatching {
                    withContext(Dispatchers.IO) {
                        val attempt = tryStartupScripts(app.startupScripts) { NativeAdbService.shell(it) }
                        if (attempt.exitCode != null && attempt.exitCode != 0) {
                            throw ActivationFailure("exit=${attempt.exitCode}: ${attempt.output}")
                        }
                        // 脚本零退出或状态未知都不能证明常驻服务已启动。
                        attempt
                    }
                }.onFailure { AppRuntime.snackbar(R.string.tools_activate_failed, app.name, it.message ?: "") }
                outcome.onSuccess { attempt ->
                    if (attempt.exitCode == null) {
                        // 未收到退出标记，不能确认脚本是否执行完毕。
                        AppRuntime.snackbar(R.string.tools_activate_script_run, app.name)
                    } else {
                        AppRuntime.snackbar(R.string.tools_activate_success, app.name)
                    }
                }
            }
            activationBusy = false
            activationSheetOpen = false
        }
    }

    val tools = listOf(
            ToolItem(
                icon = Icons.Rounded.Apps,
                titleResId = R.string.tools_app_manager,
                descResId = R.string.tools_app_manager_desc,
                onClick = {
                    scope.launch {
                        if (!NativeAdbService.isConnected()) {
                            AppRuntime.snackbar(R.string.tools_not_connected)
                            return@launch
                        }
                        navigator.push(RootScreen.AppManager)
                    }
                },
            ),
            ToolItem(
                icon = Icons.Rounded.CameraAlt,
                titleResId = R.string.tools_screenshot,
                descResId = R.string.tools_screenshot_desc,
                onClick = {
                    scope.launch {
                        if (!NativeAdbService.isConnected()) {
                            AppRuntime.snackbar(R.string.tools_not_connected)
                            return@launch
                        }
                        AppRuntime.snackbar(R.string.tools_screenshot_executing)
                        val stamp = "img_${System.currentTimeMillis()}"
                        val remote = File(REMOTE_SHOT_DIR, "$stamp.png").path
                        val outcome = runCatching {
                            withContext(Dispatchers.IO) {
                                NativeAdbService.shell("screencap -p $remote")
                                val bytes = NativeAdbService.pull(remote)
                                // 写进专用子目录：file_paths.xml 只声明该目录，
                                // FileProvider 的可共享范围不扩大到整个 cache
                                val shotDir = File(context.cacheDir, SCREENSHOT_DIR).apply { mkdirs() }
                                File(shotDir, "$stamp.png").apply { writeBytes(bytes) }
                            }
                        }
                        // 无论成功失败都清理设备端临时文件
                        runCatching {
                            withContext(Dispatchers.IO) { NativeAdbService.shell("rm -f $remote") }
                        }
                        outcome
                            .onSuccess { pendingShot = it }
                            .onFailure { AppRuntime.snackbar(R.string.tools_screenshot_failed, it.message ?: "") }
                    }
                },
            ),
            ToolItem(
                icon = Icons.Rounded.RestartAlt,
                titleResId = R.string.tools_advanced_reboot,
                descResId = R.string.tools_advanced_reboot_desc,
                onClick = {
                    scope.launch {
                        if (!NativeAdbService.isConnected()) {
                            AppRuntime.snackbar(R.string.tools_not_connected)
                            return@launch
                        }
                        // 用户态软重启要求 Android 11+：开列表前查一次目标 SDK 并缓存，
                        // 查询失败按不支持处理，老设备不会看到一个点了无效的选项
                        if (targetSdk == null) {
                            targetSdk = runCatching {
                                withContext(Dispatchers.IO) {
                                    NativeAdbService.shell("getprop ro.build.version.sdk")
                                        .trim().toIntOrNull()
                                }
                            }.getOrNull()
                        }
                        showRebootDialog = true
                    }
                },
            ),
            ToolItem(
                icon = Icons.Rounded.Visibility,
                titleResId = R.string.tools_screen_density,
                descResId = R.string.tools_screen_density_desc,
                onClick = {
                    scope.launch {
                        if (!NativeAdbService.isConnected()) {
                            AppRuntime.snackbar(R.string.tools_not_connected)
                            return@launch
                        }
                        // 取值失败就不开弹窗，避免用户对着空表单操作
                        val loaded = runCatching {
                            withContext(Dispatchers.IO) {
                                val output = NativeAdbService.shell("wm density")
                                val stock = extractDensity(output, "Physical")
                                val override = extractDensity(output, "Override")
                                stockDensity = stock
                                activeDensity = override.ifBlank { stock }
                            }
                        }.onFailure { AppRuntime.snackbar(R.string.tools_density_read_failed, it.message ?: "") }
                        if (loaded.isSuccess) densitySheetOpen = true
                    }
                },
            ),
            ToolItem(
                icon = Icons.Rounded.AspectRatio,
                titleResId = R.string.tools_screen_resolution,
                descResId = R.string.tools_screen_resolution_desc,
                onClick = {
                    scope.launch {
                        if (!NativeAdbService.isConnected()) {
                            AppRuntime.snackbar(R.string.tools_not_connected)
                            return@launch
                        }
                        val loaded = runCatching {
                            withContext(Dispatchers.IO) {
                                val output = NativeAdbService.shell("wm size")
                                val stock = extractDisplaySize(output, "Physical")
                                val override = extractDisplaySize(output, "Override")
                                stockResolution = stock
                                activeResolution = override.ifBlank { stock }
                            }
                        }.onFailure { AppRuntime.snackbar(R.string.tools_resolution_read_failed, it.message ?: "") }
                        if (loaded.isSuccess) resolutionSheetOpen = true
                    }
                },
            ),
            ToolItem(
                icon = Icons.Rounded.Wifi,
                titleResId = R.string.tools_wireless_adb,
                descResId = R.string.tools_wireless_adb_desc,
                onClick = {
                    if (!adbOverTcpBusy) {
                        scope.launch {
                            if (!NativeAdbService.isConnected()) {
                                AppRuntime.snackbar(R.string.tools_not_connected)
                                return@launch
                            }
                            portSheetOpen = true
                        }
                    }
                },
            ),
            ToolItem(
                icon = Icons.Rounded.Verified,
                titleResId = R.string.tools_activate_app,
                descResId = R.string.tools_activate_app_desc,
                onClick = { openActivationSheet() },
            ),
            ToolItem(
                icon = Icons.Rounded.PhoneAndroid,
                titleResId = R.string.tools_device_info,
                descResId = R.string.tools_device_info_desc,
                onClick = { openSpecsSheet() },
            ),
            ToolItem(
                icon = Icons.Rounded.Memory,
                titleResId = R.string.tools_process_manager,
                descResId = R.string.tools_process_manager_desc,
                onClick = {
                    scope.launch {
                        if (!NativeAdbService.isConnected()) {
                            AppRuntime.snackbar(R.string.tools_not_connected)
                            return@launch
                        }
                        processSheetOpen = true
                        loadProcesses()
                    }
                },
            ),
        )

    fun reboot(mode: RebootMode) {
        scope.launch {
            if (!NativeAdbService.isConnected()) {
                AppRuntime.snackbar(R.string.tools_not_connected)
                return@launch
            }
            showRebootDialog = false
            pendingReboot = null
            // 标题是资源 ID，直接当格式化参数会印出整数；必须先解析成本地化文案
            AppRuntime.snackbar(R.string.tools_reboot_executing, AppRuntime.stringResource(mode.titleResId))
            runCatching {
                NativeAdbService.shell(mode.shellCommand, REBOOT_RESULT_WAIT_MS)
            }.onFailure { AppRuntime.snackbar(R.string.tools_reboot_unconfirmed, AppRuntime.stringResource(mode.titleResId)) }
        }
    }

    fun applyDensity(value: String) {
        scope.launch {
            densitySheetOpen = false
            // shell 传输成功不代表命令生效：设完回读，Override 对得上才认成功
            runCatching {
                withContext(Dispatchers.IO) {
                    NativeAdbService.shell("wm density $value")
                    parseWmSetting(NativeAdbService.shell("wm density"), "density")?.override == value
                }
            }.onSuccess { effective ->
                if (effective) {
                    activeDensity = value
                    AppRuntime.snackbar(R.string.tools_density_applied, value)
                } else {
                    AppRuntime.snackbar(R.string.tools_density_failed, AppRuntime.stringResource(R.string.tools_apply_not_effective))
                }
            }.onFailure { AppRuntime.snackbar(R.string.tools_density_failed, it.message ?: "") }
        }
    }

    fun restoreDensity() {
        scope.launch {
            densitySheetOpen = false
            // reset 生效的判据：回读有效（Physical 在场且格式合法）且 Override 行消失
            runCatching {
                withContext(Dispatchers.IO) {
                    NativeAdbService.shell("wm density reset")
                    isWmRestored(NativeAdbService.shell("wm density"), "density")
                        ?: throw IOException(AppRuntime.stringResource(R.string.tools_readback_unconfirmed))
                }
            }.onSuccess { effective ->
                if (effective) {
                    activeDensity = stockDensity
                    AppRuntime.snackbar(R.string.tools_density_restored, stockDensity)
                } else {
                    AppRuntime.snackbar(R.string.tools_density_failed, AppRuntime.stringResource(R.string.tools_apply_not_effective))
                }
            }.onFailure { AppRuntime.snackbar(R.string.tools_density_failed, it.message ?: "") }
        }
    }

    fun applyResolution(width: String, height: String) {
        scope.launch {
            resolutionSheetOpen = false
            runCatching {
                withContext(Dispatchers.IO) {
                    NativeAdbService.shell("wm size ${width}x$height")
                    parseWmSetting(NativeAdbService.shell("wm size"), "size")?.override == "${width}x$height"
                }
            }.onSuccess { effective ->
                if (effective) {
                    activeResolution = "${width}x$height"
                    AppRuntime.snackbar(R.string.tools_resolution_applied, width, height)
                } else {
                    AppRuntime.snackbar(R.string.tools_resolution_failed, AppRuntime.stringResource(R.string.tools_apply_not_effective))
                }
            }.onFailure { AppRuntime.snackbar(R.string.tools_resolution_failed, it.message ?: "") }
        }
    }

    fun restoreResolution() {
        scope.launch {
            resolutionSheetOpen = false
            // reset 生效的判据：回读有效（Physical 在场且格式合法）且 Override 行消失
            runCatching {
                withContext(Dispatchers.IO) {
                    NativeAdbService.shell("wm size reset")
                    isWmRestored(NativeAdbService.shell("wm size"), "size")
                        ?: throw IOException(AppRuntime.stringResource(R.string.tools_readback_unconfirmed))
                }
            }.onSuccess { effective ->
                if (effective) {
                    activeResolution = stockResolution
                    AppRuntime.snackbar(R.string.tools_resolution_restored, stockResolution)
                } else {
                    AppRuntime.snackbar(R.string.tools_resolution_failed, AppRuntime.stringResource(R.string.tools_apply_not_effective))
                }
            }.onFailure { AppRuntime.snackbar(R.string.tools_resolution_failed, it.message ?: "") }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackHostState) },
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = stringResource(R.string.tools_title),
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptic.contextClick()
                                navigator.pop()
                            },
                        ) {
                            Icon(
                                // 与应用管理页左上角同一枚 miuix 箭头
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { pagePadding ->
        Box(
            modifier =
                if (blurActive) Modifier.layerBackdrop(blurBackdrop)
                else Modifier,
        ) {
            LazyColumn(
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = UiSpacing.PageBottom,
            ) {
                item {
                    Card {
                        Column {
                            tools.forEach { tool ->
                                ArrowPreference(
                                    title = stringResource(tool.titleResId),
                                    summary =
                                        if (tool.titleResId == R.string.tools_wireless_adb && adbOverTcpOn) {
                                            stringResource(R.string.tools_wireless_adb_active, activeTcpPort)
                                        } else {
                                            stringResource(tool.descResId)
                                        },
                                    startAction = {
                                        Icon(
                                            imageVector = tool.icon,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                        )
                                    },
                                    onClick = {
                                        haptic.contextClick()
                                        tool.onClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // 对话框必须位于 Scaffold 内部: OverlayDialog 默认 renderInRootScaffold=true,
            // 在 Scaffold 外部时会落入 LocalDialogStates 的默认空列表, 没有任何 MiuixPopupHost 渲染它
            val shot = pendingShot
    if (shot != null) {
        ScreenshotResultDialog(
            fileName = shot.name,
            onCancel = {
                shot.delete()
                pendingShot = null
            },
            onSave = { saveShotLauncher.launch(shot.name) },
            onOpen = {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shot,
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/png")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                    .onFailure { AppRuntime.snackbar(R.string.tools_screenshot_failed, it.message ?: "") }
                pendingShot = null
            },
        )
    }

    if (showRebootDialog) {
        RebootDialog(
            showSoftReboot = (targetSdk ?: 0) >= 30,
            onDismiss = { showRebootDialog = false },
            // 先收起义确认弹窗，确认后再真正执行
            onReboot = {
                showRebootDialog = false
                pendingReboot = it
            },
        )
    }

    pendingReboot?.let { mode ->
        RebootConfirmDialog(
            mode = mode,
            // 取消确认回到动作列表，让用户改选别的模式而不是从头再来
            onDismiss = {
                pendingReboot = null
                showRebootDialog = true
            },
            onConfirm = { reboot(mode) },
        )
    }

    if (densitySheetOpen) {
        DensitySheet(
            stockDensity = stockDensity,
            activeDensity = activeDensity,
            onDismiss = { densitySheetOpen = false },
            onApply = ::applyDensity,
            onRestore = ::restoreDensity,
        )
    }

    if (resolutionSheetOpen) {
        ResolutionSheet(
            stockResolution = stockResolution,
            activeResolution = activeResolution,
            onDismiss = { resolutionSheetOpen = false },
            onApply = ::applyResolution,
            onRestore = ::restoreResolution,
        )
    }

    if (portSheetOpen) {
        PortSheet(
            activePort = activeTcpPort,
            busy = adbOverTcpBusy,
            onDismiss = { portSheetOpen = false },
            onApply = {
                portSheetOpen = false
                enableAdbOverTcp(it)
            },
        )
    }

    if (activationSheetOpen) {
        ActivationSheet(
            apps = detectedActivatables,
            loading = activationLoading,
            detectFailed = activationDetectFailed,
            onDismiss = { activationSheetOpen = false },
            onActivate = ::activateApps,
        )
    }

    DeviceSpecsSheet(
        show = specsSheetOpen,
        onDismissRequest = { specsSheetOpen = false },
    )


    ProcessSheet(
        show = processSheetOpen,
        processes = processes,
        loading = processLoading,
        refreshing = processRefreshing,
        loadFailed = processLoadFailed,
        hideSystem = hideSystemProcesses,
        onToggleHideSystem = { hideSystemProcesses = !hideSystemProcesses },
        stoppingPackages = stoppingPackages,
        // 兜底入口：预置位没赶上组合（快速甩拉）时组件仍会调 onRefresh，加载从这里起
        onRefresh = { loadProcesses(fromPull = true) },
        // 「预置位」握手（两段式的根因与机理见 loadProcesses 的注释）：
        onPullStateChanged = { state ->
            when (state) {
                // 拉过阈值就先置位：拖拽期给父链足够时间把 true 组合进组件，
                // 松手后组件才会走「加入进行中的刷新」，而不是当场判「已结束」
                RefreshState.ThresholdReached -> {
                    processRefreshing = true
                    processPullLoadStarted = false
                    processPullCancelled = false
                }

                // 组件跳过了 onRefresh（预置位赶上了）→ 加载由这里起；
                // 走了 onRefresh 兜底的话已经置位过，不重复起。
                // 取消态（拉过又拖回）不在此起加载：拖回起点之上时组件可能因组合滞后
                // 误展开一次，展开会自行被撤销的 false 收尾，但绝不能附带一次真实刷新。
                RefreshState.Refreshing ->
                    if (!processPullLoadStarted && !processPullCancelled) loadProcesses(fromPull = true)

                // 组件没等数据回来就先收了指示器（兜底路径的假完成）：撤销预置位，
                // 让这次刷新就地收尾，加载继续在后台跑完，不叠出第二段展开
                RefreshState.RefreshComplete -> if (processLoading) processRefreshing = false

                // 回拉到阈值下：撤销预置位并进入取消态（未过阈值松手也走这里）
                RefreshState.Pulling -> {
                    processRefreshing = false
                    processPullCancelled = true
                }

                // 拖回起点之上会直接落 Idle（组件在 dragOffset ≤ 0 时写回 Idle，此时手指未松）：
                // 同样撤销预置位 + 取消态；刷新正常收尾后的 Idle 写这两个值是无副作用的重置
                RefreshState.Idle -> {
                    processRefreshing = false
                    processPullCancelled = true
                }
            }
        },
        onStop = ::stopProcess,
        onDismissRequest = { processSheetOpen = false },
    )
        }
    }
}

@Composable
private fun PortSheet(
    activePort: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    // 空输入回退默认端口
    val resolvedPort = input.ifBlank { ADB_TCP_DEFAULT_PORT.toString() }
    val portValid = resolvedPort.toIntOrNull()?.let { it in ADB_TCP_PORT_MIN..ADB_TCP_PORT_MAX } ?: false

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_wireless_adb),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
            if (activePort.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_wireless_adb_active, activePort),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = stringResource(R.string.tools_wireless_adb_port_hint, ADB_TCP_PORT_MIN, ADB_TCP_PORT_MAX),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            SuperTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit) },
                label = stringResource(R.string.tools_wireless_adb_port_label, ADB_TCP_DEFAULT_PORT),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    onClick = { onApply(resolvedPort.toInt()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = portValid && !busy,
                )
            }
        }
    }
}

@Composable
private fun ActivationSheet(
    apps: List<ActivatableApp>,
    loading: Boolean,
    detectFailed: Boolean,
    onDismiss: () -> Unit,
    onActivate: (List<ActivatableApp>) -> Unit,
) {
    val selection = remember { mutableStateListOf<ActivatableApp>() }
    val haptic = LocalHapticFeedback.current

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_activate_app),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
            when {
                loading -> {
                    Text(
                        text = stringResource(R.string.tools_activate_detecting),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }

                detectFailed -> {
                    Text(
                        text = stringResource(R.string.tools_activate_detect_failed_hint),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }

                apps.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.tools_activate_none),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.tools_activate_hint),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    apps.forEach { app ->
                        // 整行可点 + Sink 按压反馈；复选框只显示状态，避免行与框各切一次
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            pressFeedbackType = PressFeedbackType.Sink,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            haptic.contextClick()
                                            if (app in selection) selection.remove(app) else selection.add(app)
                                        },
                                    )
                                    .padding(UiSpacing.PageItem),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
                            ) {
                                Text(
                                    text = app.name,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Checkbox(
                                    state = if (app in selection) ToggleableState.On else ToggleableState.Off,
                                    onClick = null,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    onClick = { onActivate(selection.toList()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = selection.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun DensitySheet(
    stockDensity: String,
    activeDensity: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onRestore: () -> Unit,
) {
    var input by remember { mutableStateOf("") }

    // 极端密度值会让设备 UI 几乎不可用，限制在常见安全区间
    val inSafeRange = input.toIntOrNull()?.let { it in DENSITY_MIN..DENSITY_MAX } ?: false

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_screen_density),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
            if (stockDensity.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_density_factory, stockDensity),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            if (activeDensity.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_density_now, activeDensity),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            SuperTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit) },
                label = stringResource(R.string.tools_density_hint, DENSITY_MIN, DENSITY_MAX),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.tools_density_restore),
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    // 统一走规范化值，避免前导零输入与回读不一致
                    onClick = { onApply(normalizeToolValue(input, DENSITY_MIN..DENSITY_MAX) ?: return@TextButton) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = inSafeRange,
                )
            }
        }
    }
}

@Composable
private fun ResolutionSheet(
    stockResolution: String,
    activeResolution: String,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit,
    onRestore: () -> Unit,
) {
    var widthInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }

    fun withinBounds(value: String) =
        value.toIntOrNull()?.let { it in RES_MIN..RES_MAX } ?: false

    val submittable = withinBounds(widthInput) && withinBounds(heightInput)

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_screen_resolution),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
            if (stockResolution.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_resolution_factory, stockResolution),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            if (activeResolution.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tools_resolution_now, activeResolution),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                SuperTextField(
                    value = widthInput,
                    onValueChange = { widthInput = it.filter(Char::isDigit) },
                    label = stringResource(
                        R.string.tools_resolution_width,
                        stockResolution.substringBefore('x')
                            .ifBlank { stringResource(R.string.tools_value_unknown) },
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                SuperTextField(
                    value = heightInput,
                    onValueChange = { heightInput = it.filter(Char::isDigit) },
                    label = stringResource(
                        R.string.tools_resolution_height,
                        stockResolution.substringAfter('x')
                            .ifBlank { stringResource(R.string.tools_value_unknown) },
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.tools_resolution_restore),
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    // 统一走规范化值，避免前导零输入与回读不一致
                    onClick = {
                        val w = normalizeToolValue(widthInput, RES_MIN..RES_MAX) ?: return@TextButton
                        val h = normalizeToolValue(heightInput, RES_MIN..RES_MAX) ?: return@TextButton
                        onApply(w, h)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = submittable,
                )
            }
        }
    }
}

@Composable
private fun ScreenshotResultDialog(
    fileName: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_screenshot),
        summary = fileName,
        onDismissRequest = onCancel,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.tools_screenshot_save),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.tools_screenshot_open),
                onClick = onOpen,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

private data class ToolItem(
    val icon: ImageVector,
    val titleResId: Int,
    val descResId: Int,
    val onClick: () -> Unit,
)

private enum class RebootMode(val shellCommand: String) {
    Normal("reboot"),
    // 用户态软重启只重启 Android 运行时，需 Android 11+ 且 init 支持
    Soft("setprop sys.powerctl reboot,userspace"),
    Shutdown("setprop sys.powerctl shutdown"),
    Recovery("reboot recovery"),
    Fastboot("reboot bootloader"),
}

@Composable
private fun RebootDialog(
    showSoftReboot: Boolean,
    onDismiss: () -> Unit,
    onReboot: (RebootMode) -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_advanced_reboot),
        titleColor = dialogTitleColor(),
        insideMargin = DpSize(0.dp, 24.dp),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
    ) {
        Column(
            modifier = Modifier,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // 软重启由调用方按目标设备版本决定，其余选项所有设备都支持
            RebootMode.entries.filter { it != RebootMode.Soft || showSoftReboot }.forEach { mode ->
                SheetActionRow(
                    label = stringResource(mode.titleResId),
                    enabled = true,
                    onClick = { onReboot(mode) },
                )
            }
            Spacer(Modifier.height(16.dp))
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textStyle = textStyles.button.copy(fontWeight = FontWeight.Medium),
            )
            // 面板底部留白由 dialog 的垂直 insideMargin（24dp）给出
        }
    }
}

// 高级重启的每一步都会立即生效，列表里点完直接执行没有反悔机会，所以再加一道确认
@Composable
private fun RebootConfirmDialog(
    mode: RebootMode,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_reboot_confirm_title),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium)) {
            Text(
                text = stringResource(R.string.tools_reboot_confirm, stringResource(mode.titleResId)),
                color = colorScheme.onSurfaceVariantSummary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val RebootMode.titleResId: Int
    get() = when (this) {
        RebootMode.Normal -> R.string.tools_reboot_normal
        RebootMode.Soft -> R.string.tools_reboot_soft
        RebootMode.Shutdown -> R.string.tools_reboot_shutdown
        RebootMode.Recovery -> R.string.tools_reboot_recovery
        RebootMode.Fastboot -> R.string.tools_reboot_fastboot
    }

@Composable
private fun DeviceSpecsSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    var state by remember(show) { mutableStateOf<SpecsState>(SpecsState.Loading) }
    val haptic = LocalHapticFeedback.current

    val charging = stringResource(R.string.tools_info_battery_charging)
    val full = stringResource(R.string.tools_info_battery_full)
    val discharging = stringResource(R.string.tools_info_battery_discharging)

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        state = SpecsState.Loading
        state = runCatching {
            collectDeviceSpecs(charging, full, discharging)
        }.fold(
            onSuccess = { SpecsState.Ready(it) },
            onFailure = { SpecsState.Failed(it.message ?: "") },
        )
    }

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.tools_device_info),
        startAction = {
            // 返回箭头，与应用管理「从本机应用安装」面板左上角同款
            IconButton(
                onClick = {
                    haptic.contextClick()
                    onDismissRequest()
                },
                modifier = Modifier.offset(y = UiSpacing.SheetHeaderIconOpticalOffset),
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        },
        onDismissRequest = onDismissRequest,
        defaultWindowInsetsPadding = false,
    ) {
        when (val current = state) {
            SpecsState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = UiSpacing.PageBottom * 2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tools_info_loading),
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            is SpecsState.Failed -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = UiSpacing.PageBottom),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tools_info_failed, current.reason),
                        color = colorScheme.error,
                        fontSize = 14.sp,
                    )
                }
            }

            is SpecsState.Ready -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(UiSpacing.ContentVertical)) {
                        current.specs.forEach { spec ->
                            SpecRow(label = stringResource(spec.labelRes), value = spec.value)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
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
            color = colorScheme.onSurfaceVariantSummary,
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

@Composable
private fun ProcessSheet(
    show: Boolean,
    processes: List<DeviceProcess>,
    loading: Boolean,
    refreshing: Boolean,
    loadFailed: String?,
    hideSystem: Boolean,
    onToggleHideSystem: () -> Unit,
    stoppingPackages: Set<String>,
    onRefresh: () -> Unit,
    /** 组件刷新状态机的原始信号，交给上层做「预置位」握手（机理见上层 loadProcesses 的注释） */
    onPullStateChanged: (RefreshState) -> Unit,
    onStop: (DeviceProcess) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    var tuneOpen by remember { mutableStateOf(false) }
    // 显示选项菜单不随弹层可见期自动收敛：菜单开着时关闭进程管理弹层，popup 会留在
    // 根 scaffold 上（需再点一次才消失）。可见期结束就把菜单收回。
    LaunchedEffect(show) {
        if (!show) tuneOpen = false
    }
    val visible = remember(processes, hideSystem) {
        if (hideSystem) processes.filterNot { it.isSystem } else processes
    }

    // 提示条宿主的创建与注册放在下面 OverlayBottomSheet 的内容 lambda 里，与它的渲染期
    // 严格同长。写在函数体里是错的：本函数被本页无条件调用，注册会活到本页销毁，而宿主
    // 只在浮层可见期才渲染——AppRuntime 取的是最后注册的那个，于是浮层关着时，本页所有
    // 提示条（截图保存、分辨率应用结果、无线调试…）都会被塞进一个不渲染的宿主而看不见。
    // 放在 lambda 里，miuix 只在可见期组合内容，注册与注销就跟着渲染期走。

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.tools_process_manager),
        onDismissRequest = onDismissRequest,
        startAction = {
            IconButton(
                onClick = {
                    haptic.contextClick()
                    onDismissRequest()
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        },
        endAction = {
            // 「隐藏系统应用」收在选单里，与「从本机应用安装」同一套 DisplayOptionsMenu
            Box {
                IconButton(
                    onClick = {
                        haptic.contextClick()
                        tuneOpen = true
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Tune,
                        contentDescription = stringResource(R.string.tools_process_display_options),
                    )
                }
                DisplayOptionsMenu(
                    visible = tuneOpen,
                    hideVersion = hideSystem,
                    hideApkSize = false,
                    onToggleHideVersion = onToggleHideSystem,
                    onToggleHideApkSize = {},
                    hideVersionLabelResId = R.string.tools_process_hide_system,
                    showHideApkSize = false,
                    onDismissRequest = { tuneOpen = false },
                )
            }
        },
    ) {
        // 提示条宿主：与本 lambda 同生命周期，miuix 只在浮层可见期组合这里
        val sheetSnackHostState = remember { SnackbarHostState() }
        DisposableEffect(sheetSnackHostState) {
            val unregister = AppRuntime.registerSnackbarHostState(sheetSnackHostState)
            onDispose(unregister)
        }

        // 组件状态机的每次变化都交给上层：拉过阈值预置位、进入 Refreshing 起加载、
        // 回拉/收尾撤销预置位（防重与假完成处理见上层 onPullStateChanged）。
        // 用 rememberUpdatedState 而不是把参数当 key：lambda 每次组合都是新实例，
        // 当 key 会让这个 effect 每次重组都重启、把当前状态重复喂给上层一次。
        val currentOnPullStateChanged by rememberUpdatedState(onPullStateChanged)
        LaunchedEffect(pullToRefreshState) {
            snapshotFlow { pullToRefreshState.refreshState }
                .collect { currentOnPullStateChanged(it) }
        }

        // 不额外挂手势消费器：浮层列表挂在自己的 LazyColumn 上，没有连回本页 Scaffold 的
        // nestedScroll 连接（那个连接已被摘掉），所以列表怎么滚都碰不到本页的 scrollBehavior，
        // 标题不会跟着折叠
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PROCESS_LIST_MAX_HEIGHT),
            ) {
                PullToRefresh(
                    // 指示器保持到数据回来，走完「正在刷新… → 刷新完成」两态；置位时机
                    // 提前到拉过阈值（见上层 onPullStateChanged 的预置位），松手后不会
                    // 出现假完成。首屏加载不给置位，不会程序化展开指示器
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    pullToRefreshState = pullToRefreshState,
                    refreshTexts = listOf(
                        stringResource(R.string.tools_process_pull_to_refresh),
                        stringResource(R.string.tools_process_release_to_refresh),
                        stringResource(R.string.tools_process_refreshing),
                        stringResource(R.string.tools_process_refresh_done),
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 用 androidx 原生的 LazyColumn，与「从本机应用安装」一致：scaffolds 那个包装版带
                    // clearFocusOnTap 的 pointerInput 与 fillMaxSize，放在浮层里会让触摸漏到后面的页面
                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        // 只留顶部内边距：底部留白会让最后一行永远差一截够不到屏幕底边，
                        // 看上去像弹窗浮在屏幕上方
                        contentPadding = PaddingValues(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Tiny),
                    ) {
                        if (loading && visible.isEmpty()) {
                            item {
                                ProcessPlaceholder(
                                    text = stringResource(R.string.tools_process_loading),
                                    spinning = true,
                                )
                            }
                        } else if (loadFailed != null && processes.isEmpty()) {
                            // 读不出来就说读不出来，绝不落到「没有正在运行的应用」：
                            // 那句会让用户以为设备上什么都没跑，而实际是这次读取没成。
                            // 限定在「列表里没有任何可用数据」时才接管：失败只是更早一次
                            // 加载的历史（processLoadFailed 要到下一次加载才开始才清），
                            // 列表里还有数据时它不该压过空态/过滤态文案（R4.3）
                            item {
                                ProcessPlaceholder(text = loadFailed)
                            }
                        } else if (visible.isEmpty()) {
                            item {
                                ProcessPlaceholder(
                                    text = if (processes.isEmpty()) {
                                        stringResource(R.string.tools_process_empty)
                                    } else {
                                        // 列表非空而过滤后为空 = 全部被「隐藏系统应用」滤掉了，
                                        // 与「设备上没应用在跑」不是一回事，文案要分开
                                        stringResource(R.string.tools_process_empty_filtered)
                                    },
                                )
                            }
                        } else {
                            items(items = visible, key = { it.packageName }) { process ->
                                ProcessRow(
                                    process = process,
                                    stopping = process.packageName in stoppingPackages,
                                    onStop = {
                                        haptic.contextClick()
                                        onStop(process)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // 提示条贴在浮层内容底部，盖在列表之上
            SnackbarHost(
                state = sheetSnackHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        // 底部不加留白：列表本身要能一直滚到屏幕底边，否则最后一行与屏幕之间会空一段
    }
}

/**
 * 列表区限高：不设限时下拉刷新头会把整个 BottomSheet 内容一起顶起来。
 *
 * 与「从本机应用安装」取同一个值，两处的弹层高度观感才对得上。
 */
private val PROCESS_LIST_MAX_HEIGHT = 400.dp

@Composable
private fun ProcessRow(
    process: DeviceProcess,
    /** 命令在途：按钮换成进度并禁用，防止用户以为没点上而连点 */
    stopping: Boolean,
    onStop: () -> Unit,
) {
    // 行布局与「从本机应用安装」同款：Row + clip 自己画底色，不套 Card。
    // Card 自带按压层，在浮层列表里实测会把手势漏给后面的页面
    val rowColor = colorScheme.surfaceContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(rowColor),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
    ) {
        ProcessIconImage(icon = process.icon, contentDescription = process.label ?: process.packageName)
        Column(modifier = Modifier.weight(1f)) {
            // 主行：应用名 + 系统角标；取不到名字时退到只给包名。
            // 名字用 fill = false 的 weight：名字过长时压缩的是名字，角标不会被挤出可视范围
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.Tiny),
            ) {
                Text(
                    text = process.label ?: process.packageName,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (process.isSystem) {
                    // 用 primary 而不是摘要灰：角标跟着应用名走，灰字跟下面两行连成一片，
                    // 扫一眼分不清「系统」是名字的一部分还是另一类信息
                    Text(
                        text = stringResource(R.string.tools_process_system_badge),
                        fontSize = 11.sp,
                        color = colorScheme.primary,
                    )
                }
            }
            // 有名字才补包名那行，没有名字时主行已经是包名了，再补一行是重复
            if (process.label != null) {
                Text(
                    text = process.packageName,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(
                    R.string.tools_process_row_detail,
                    readableSize(process.rssKb),
                    process.pid,
                ),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 系统应用也给按钮：不少系统包（应用商店一类）确实停得掉，shell 身份对它们有权限。
        // 真停不掉的按下去会走失败那条提示，如实报「未能结束 X」，不假装成功
        if (stopping) {
            CircularProgressIndicator(size = 20.dp)
        } else {
            TextButton(
                text = stringResource(R.string.tools_process_stop),
                onClick = onStop,
            )
        }
    }
}

@Composable
private fun ProcessIconImage(icon: ProcessIcon?, contentDescription: String) {
    val bitmap = remember(icon) {
        icon?.let { runCatching { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size) }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            // squircle 裁切与应用管理页同款：同一批图标，两处显示的形状要对得上
            modifier = Modifier
                .size(PROCESS_ICON_SIZE)
                .squircleClip(cornerRadius = PROCESS_ICON_CORNER_RADIUS),
        )
    } else {
        Box(
            modifier = Modifier.size(PROCESS_ICON_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Memory,
                contentDescription = contentDescription,
                tint = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private val PROCESS_ICON_SIZE = 40.dp

/** 图标圆角与应用管理页一致，同一批图标两处形状才对得上。 */
private val PROCESS_ICON_CORNER_RADIUS = 8.dp

/** 加载 / 空态的占位高度：与「从本机应用安装」取同一个值，两处的空态观感才对得上。 */
private val PROCESS_PLACEHOLDER_HEIGHT = 120.dp

/**
 * 列表加载中 / 空态的占位：文字居中，加载中带一个转圈图标。
 *
 * 高度固定，否则居中会随内容高度漂。转圈用 miuix 的，与「从本机应用安装」里那个加载态
 * 同一套组件。
 */
@Composable
private fun ProcessPlaceholder(text: String, spinning: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROCESS_PLACEHOLDER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (spinning) {
                CircularProgressIndicator(size = 32.dp)
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = text,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 按包名向设备侧要显示名与图标。
 *
 * 探测程序按包名过滤后只回这几项，不把整台设备的清单搬回来——图标本来就占输出的大头，
 * 而这里只关心正在跑的那几十个包。取名失败不算列表失败：调用方拿 null 回落显示包名。
 */
private suspend fun fetchProcessMeta(packages: List<String>): ProcessMeta? {
    if (packages.isEmpty()) return null
    return when (val result = AppProbeClient(AppRuntime.context).loadAppMeta(packages)) {
        is AppProbeClient.ProbeResult.Success -> ProcessMeta(
            labels = parseProcessLabels(result.apps),
            icons = parseProcessIcons(result.icons, result.iconSuffix),
        )

        else -> null
    }
}

private data class ProcessMeta(
    val labels: Map<String, String>,
    val icons: Map<String, ProcessIcon>,
)
