package io.github.miuzarte.scrcpyforandroid.appmanager.data

import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppAction
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppCommands
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppOrdering
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.BatchTally
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.ListEffect
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.LocalApp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.models.DeviceConnectionType
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipOutputStream

/**
 * 应用管理域的页面数据层（进程作用域）。
 *
 * 状态在进程内常驻：离开页面不取消在途操作，回到页面看到的是
 * 刷新后的列表；切设备时按设备标识重置，不串数据。
 */
object DeviceAppRepository {

    // 进程级单例依赖 AppRuntime.context（MainActivity.onCreate 已初始化）。
    // 面板图标来源（AppIconSource）共用这一份：同一个 LRU、同一份设备目录，图标只拉一遍
    internal val cache by lazy { DeviceAppCache(AppRuntime.context) }
    private val probeClient by lazy { AppProbeClient(AppRuntime.context) }
    private val installer by lazy { ApkInstaller() }
    private val localAppRepository by lazy { LocalAppRepository(AppRuntime.context) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _apps = MutableStateFlow<List<DeviceApp>>(emptyList())
    val apps: StateFlow<List<DeviceApp>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    private val _refreshing = MutableStateFlow(false)
    private val _notConnected = MutableStateFlow(false)
    private val _error = MutableStateFlow<AppManagerError?>(null)
    private val _pendingPackages = MutableStateFlow<Set<String>>(emptySet())
    private val _installing = MutableStateFlow(false)
    private val _loadingLocalApps = MutableStateFlow(false)

    /** 图标版本号：图标落盘后 +1，用于在列表内容没变时也推一次 UI 重组 */
    private val _iconEpoch = MutableStateFlow(0)

    /** 安装 / 提取的传输进度（进度弹窗唯一数据源；空闲时为 inactive）。 */
    private val _transfer = MutableStateFlow(TransferProgress())
    val transfer: StateFlow<TransferProgress> = _transfer.asStateFlow()

    /** 进行中的传输任务，供「中断」取消。 */
    private var transferJob: Job? = null

    /**
     * 起一个新任务并接管 [transferJob]。
     *
     * 新旧任务共用 `appmanager-install` 暂存目录：旧任务退场时会删掉整个目录并改写进度字段，
     * 所以新任务等它收尾后再开始。旧任务卡在阻塞的 shell 调用里时最多等
     * [TRANSFER_HANDOVER_TIMEOUT_MS]，超时照常开始，免得「中断」之后再也起不来。
     */
    private inline fun launchTransfer(crossinline block: suspend CoroutineScope.() -> Unit) {
        val previous = transferJob
        previous?.cancel()
        transferJob = scope.launch {
            withTimeoutOrNull(TRANSFER_HANDOVER_TIMEOUT_MS) { previous?.join() }
            block()
        }
    }

    /** 进行中的图标预热任务，换设备 / 重载时取消。 */
    private var iconWarmJob: Job? = null

    // 主线程写（onEnter 切设备）、IO 线程读（load 提交判定），必须可见
    @Volatile
    private var deviceKey: String? = null

    private val baseState = combine(
        _apps,
        _loading,
        _refreshing,
        _notConnected,
        _error,
    ) { apps, loading, refreshing, notConnected, error ->
        AppListUiState(
            apps = apps,
            isLoading = loading,
            isRefreshing = refreshing,
            notConnected = notConnected,
            error = error,
        )
    }

    private val baseStateWithPending =
        combine(baseState, _pendingPackages) { state, pending ->
            state.copy(pendingPackages = pending)
        }

    val uiState: StateFlow<AppListUiState> =
        combine(baseStateWithPending, _installing, _loadingLocalApps, _iconEpoch) {
            state, installing, loadingLocal, iconEpoch ->
            state.copy(installing = installing, loadingLocalApps = loadingLocal, iconEpoch = iconEpoch)
        }.stateIn(scope, SharingStarted.Eagerly, AppListUiState())

    // ---------------------------------------------------------------- 读取

    /** 设备身份从地址换成稳定键时，把旧目录的图标与元数据搬过去。 */
    internal fun migrateDeviceCache(fromKey: String, toKey: String) = cache.migrateDevice(fromKey, toKey)

    /**
     * 进入页面：切设备或列表为空才加载；已有数据直接呈现。
     * 冷启动且有缓存时先用缓存渲染，再等真实数据替换。
     */
    fun onEnter(deviceKey: String?) {
        val address = deviceKey ?: UNKNOWN_DEVICE
        // 地址会随网络变化，缓存目录用解析出的稳定键；未解析过时这里先返回地址本身
        val key = DeviceIdentity.cachedKey(address)
        // 访问即续期：设备连上但列表没变化时，目录 mtime 不会变，只能靠这个标记保命
        // 首次遇到这个地址时解析一次；解析出的键与当前不同就回主线程按新键重进一次
        scope.launch {
            cache.touchDevice(key)
            val resolved = DeviceIdentity.refresh(address)
            if (resolved != key) withContext(Dispatchers.Main.immediate) { onEnter(resolved) }
        }
        if (this.deviceKey != key) {
            val oldKey = this.deviceKey
            this.deviceKey = key
            iconWarmJob?.cancel()
            if (oldKey != null) cache.evictDevice(oldKey)
            _apps.value = emptyList()
            _error.value = null
            _notConnected.value = false
            _pendingPackages.value = emptySet()
            // 切设备同时作废在途刷新：它拉的是上一台设备的清单，标志得立刻收掉——
            // 光等它跑完会让新设备的页面顶上上一台设备的刷新进度，而 ADB 通道排队时
            // 那可能是一整轮加载的时间。作废代次后它回来时不会碰这个标志
            refreshGeneration++
            _refreshing.value = false
            // 同步置位：load() 在 IO 协程里才置位，那之前列表为空 + 未加载会闪一帧「没有应用」
            _loading.value = true
            scope.launch { load(withCache = true) }
            return
        }
        if (shouldAutoLoadOnEnter(
                appsEmpty = _apps.value.isEmpty(),
                error = _error.value,
                notConnected = _notConnected.value,
                loading = _loading.value,
                lastCompletedKey = lastCompletedKey == key,
            )
        ) {
            _loading.value = true
            scope.launch { load(withCache = true) }
        }
    }

    /**
     * 自动重载准入（onEnter 的 else 分支）：返回 true 表示应当发起一次新的加载。
     * 规则：列表空 + 无错误 + 已连接 + 不正在加载 + 同 key 尚无成功终态。
     * 任一不满足即跳过——防空列表 / 错误状态 / 在途状态下的循环重载。
     */
    internal fun shouldAutoLoadOnEnter(
        appsEmpty: Boolean,
        error: AppManagerError?,
        notConnected: Boolean,
        loading: Boolean,
        lastCompletedKey: Boolean,
    ): Boolean = appsEmpty && error == null && !notConnected && !loading && !lastCompletedKey

    /**
     * 提交判定：本次加载还能否写回，以及写回时用哪个设备 key（null = 丢弃）。
     *
     * 每个挂起点返回后都要重新问一次。两个判据缺一不可：只看代次，切设备时旧协程仍会
     * 过关并按新 deviceKey 落盘，把旧清单写进新设备目录；只看设备，同一设备上的并发
     * 加载仍会互相覆盖。传进来的设备 key 是发起时定下的那份快照，调用方因此全程不重读
     * 全局 `deviceKey`。
     */
    internal fun commitKey(
        generation: Int,
        latestGeneration: Int,
        loadKey: String,
        currentDeviceKey: String?,
    ): String? {
        val normalized = currentDeviceKey ?: UNKNOWN_DEVICE
        return if (generation == latestGeneration && loadKey == normalized) loadKey else null
    }

    /**
     * 下拉刷新：不走缓存中转，直接从设备拉新数据。
     *
     * 不去重：停用 / 启用后的对齐刷新也走这里，去重会把后一次吞掉——正在刷新时再点
     * 启用，唯一那次加载读到的是启用之前的真值，界面就停在旧状态。并发由 [load] 的
     * 代次守卫收口。
     *
     * 「正在刷新」由代次收口：重叠刷新时先跑完的那次不负责清标志，否则进度会在后一次
     * 仍在拉数据时就消失。
     */
    fun refresh() {
        val generation = ++refreshGeneration
        _refreshing.value = true
        scope.launch {
            load(withCache = false)
            if (generation == refreshGeneration) _refreshing.value = false
        }
    }

    private var refreshGeneration = 0

    /**
     * 加载代次：每次发起加载 +1，只有最新代次的结果能写回。
     *
     * 停用后立刻启用会并发两次加载，而 ADB 通道是排队的——先发起的那次不一定先完成，
     * 后完成的一次会把先完成的结果盖掉，界面就停在旧状态。按代次丢弃过期结果，
     * 保证「最后一次操作对应的设备真值」赢。
     *
     * 每个挂起点之后都要重新校验（[load] 里 `disabledPackages()` 之后是必须的那一次），
     * 一次只能护住相邻两步。配合 [load] 里发起时定下的设备 key 快照，落盘与写回因此
     * 只会发生在「仍是最新代次 + 仍是本次设备」的组合上。
     */
    private var loadGeneration = 0

    /** 最近一次 load 到终态时的设备 key：用于 onEnter 不在空列表 / 错误态反复自动重载。 */
    private var lastCompletedKey: String? = null

    private suspend fun load(withCache: Boolean) {
        val generation = ++loadGeneration
        // 本次加载的设备 key 在发起时就定下：加载期间可能切设备，其后所有读缓存 / 落盘
        // 一律用这份快照，不重读全局 deviceKey——重读会把旧设备的清单写进新设备目录
        val key = deviceKey ?: UNKNOWN_DEVICE
        _loading.value = true
        _notConnected.value = false
        _error.value = null
        // 看门狗：探测是一条 app_process 命令，跑多久都没有中间输出可看，卡住时界面会
        // 一直停在「正在加载」。到点就把转圈换成明确提示，别让用户干等。后台那次加载
        // 不取消——它仍在跑，真回来了会照常把这条提示覆盖掉。
        // 判定用 [commitKey] 而不是单看代次：切设备后旧设备那条看门狗照样到点，
        // 只判代次会替新设备弹出上一台的慢提示
        val watchdog = scope.launch {
            delay(LOAD_TIMEOUT_MS)
            if (commitKey(generation, loadGeneration, key, deviceKey) == null) return@launch
            Log.w(TAG, "load: still running after ${LOAD_TIMEOUT_MS}ms — surfacing hint")
            _loading.value = false
            _error.value = AppManagerError.Slow
        }
        try {
            loadInner(withCache, generation, key)
        } finally {
            watchdog.cancel()
        }
    }

    private suspend fun loadInner(withCache: Boolean, generation: Int, key: String) {
        val current = { generation == loadGeneration }
        if (withCache && _apps.value.isEmpty()) {
            val snapshot = cache.read(key)
            if (snapshot != null && _apps.value.isEmpty() && current()) {
                _apps.value = AppOrdering.sorted(snapshot.apps)
                warmIcons(key, snapshot.apps.map { it.packageName })
            }
        }
        // 两段式：先拉「不要图标」的元数据（快，几百个应用也就一次探测），列表立刻可用；
        // 图标在列表落定后由 [fillIconsInBackground] 分块补，文字界面不等图片
        when (val result = probeClient.loadApps(metaOnly = true)) {
            is AppProbeClient.ProbeResult.NotConnected -> {
                Log.w(TAG, "load: device not connected")
                if (current()) _notConnected.value = true
            }

            is AppProbeClient.ProbeResult.Failed -> {
                Log.w(TAG, "load: probe failed: ${result.error}")
                if (current()) _error.value = result.error
            }

            is AppProbeClient.ProbeResult.Success -> {
                if (commitKey(generation, loadGeneration, key, deviceKey) == null) {
                    // 过期结果：期间又发起过更新的加载，这一次读到的是旧真值，丢弃
                    Log.d(TAG, "load: drop stale result gen=$generation latest=$loadGeneration")
                    return
                }
                val disabled = probeClient.disabledPackages()
                // 二次校验：上一步是挂起的 ADB 调用，期间可能切了设备或发起了更新的加载。
                // 不校验就往下走，旧清单会按新 deviceKey 落盘并覆盖正确缓存
                if (commitKey(generation, loadGeneration, key, deviceKey) == null) {
                    Log.d(TAG, "load: drop stale result before commit gen=$generation")
                    return
                }
                val merged = result.apps.map { app ->
                    if (app.packageName in disabled) app.copy(isEnabled = false) else app
                }
                val sorted = AppOrdering.sorted(merged)
                // 元数据先行：只写清单（emptyMap = 不动图标文件），列表立刻可用
                cache.write(key, sorted, emptyMap(), result.iconSuffix)
                // cache.write 是同步写盘（数百条元数据 + 孤儿清理），期间主线程可能已切设备并
                // 清空列表。再校验一次：旧设备的缓存已经正确落到它自己的目录，只是不能再把它
                // 的清单写进当前列表——否则新设备这次加载失败时，界面会停在上一台设备的列表上
                if (commitKey(generation, loadGeneration, key, deviceKey) == null) {
                    Log.d(TAG, "load: commit superseded while writing cache gen=$generation")
                    return
                }
                _apps.value = sorted
                // 图标预热：滑动中新入视口的行不必在主线程解码（只预热磁盘上已有的）
                warmIcons(key, sorted.map { it.packageName })
                // 图标后台补齐：分块拉、逐块落盘 + 版本号刷新，四五百个应用也能逐块见到图标
                scope.launch { fillIconsInBackground(key, generation, sorted) }
                Log.d(TAG, "load: ${_apps.value.size} apps, ${disabled.size} disabled")
            }
        }
        if (current()) {
            lastCompletedKey = key
            _loading.value = false
        }
    }

    /**
     * 图标后台补齐（在文字列表已经可用之后跑）。
     *
     * 分块拉取：每块落盘 + 就地预热 + 版本号 +1，界面上的图标逐块出现；某块失败即停，
     * 已落盘的块不受影响，下次刷新再补。每块前核验代次 / 设备键与真实连接：期间切设备或
     * 发起更新的加载就停，不会把 A 设备的图标写进 B 设备的目录。
     */
    private suspend fun fillIconsInBackground(key: String, generation: Int, apps: List<DeviceApp>) {
        val missing = apps.map { it.packageName }.filter { !cache.hasIcon(key, it) }
        if (missing.isEmpty()) return
        probeClient.loadAppMetaProgressive(missing) { icons, suffix ->
            if (commitKey(generation, loadGeneration, key, deviceKey) == null) {
                return@loadAppMetaProgressive false
            }
            if (DeviceIdentity.keyFor(NativeAdbService.connectedAddress()) != key) return@loadAppMetaProgressive false
            cache.writeIcons(key, icons, suffix)
            // 落盘的图标就地预热（本协程在 IO 上）：版本号 +1 后行内只查内存 LRU，
            // 不在主线程解码
            for (packageName in icons.keys) {
                cache.warmIcon(key, packageName)
            }
            _iconEpoch.value = _iconEpoch.value + 1
            true
        }
    }

    fun icon(packageName: String): ImageBitmap? {
        // 读路径同样只认真实连接：快照回退键可能指向上一台设备，宁可占位也不串图
        val key = DeviceIdentity.keyFor(NativeAdbService.connectedAddress()) ?: return null
        return cache.icon(key, packageName)
    }

    /** 本机应用图标（选择器行内只读缓存；解码见 [LocalAppRepository.warmIcons]）。 */
    fun localIcon(packageName: String): ImageBitmap? = localAppRepository.icon(packageName)

    /** 预热本机应用图标（IO 线程，见 [LocalAppRepository.warmIcon]）。 */
    fun warmIcon(packageName: String): ImageBitmap? = localAppRepository.warmIcon(packageName)

    /**
     * 后台预热图标。
     *
     * 图标解码全部发生在 IO 预热阶段（行内 `remember` 只做 LRU 快照查找），滑动中每个
     * 新入视口的行主线程零解码，超快速滑动不掉帧。列表一就绪就在 IO 线程把整份
     * 图标灌进 LRU（512 条上限，命中即跳过），主线程之后只剩 map 查找。
     * 换设备 / 重新加载时取消上一轮预热，避免旧设备的解码继续占 CPU。
     */
    private fun warmIcons(deviceKey: String, packages: Collection<String>) {
        iconWarmJob?.cancel()
        iconWarmJob = scope.launch {
            for (packageName in packages) {
                ensureActive()
                cache.warmIcon(deviceKey, packageName)
            }
        }
    }

    // 见 refresh()：操作后的对齐刷新不能被去重守卫吞掉

    // ---------------------------------------------------------------- 写操作

    fun performAction(
        action: AppAction,
        app: DeviceApp,
        onResult: (ActionResult) -> Unit,
    ) {
        scope.launch {
            val result = executeSingle(action, app)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    private suspend fun executeSingle(action: AppAction, app: DeviceApp): ActionResult {
        if (action == AppAction.OPEN) {
            return runCatching { NativeAdbService.startApp(app.packageName) }
                .fold(
                    onSuccess = { ActionResult.Success(action, app) },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        ActionResult.Failure(
                            action,
                            app,
                            AdbErrors.classify(
                                error.message ?: "",
                                NativeAdbService.isTransportAlive(),
                            ),
                        )
                    },
                )
        }
        val command = AppCommands.command(action, app.packageName)
            ?: return ActionResult.Failure(action, app, AppManagerError.Device("unsupported action"))
        addPending(app.packageName)
        val output = runCatching { NativeAdbService.shell(command) }
        if (output.isFailure) {
            removePending(app.packageName)
            val error = output.exceptionOrNull()
            if (error is CancellationException) throw error
            return ActionResult.Failure(
                action,
                app,
                AdbErrors.classify(
                    error?.message ?: "",
                    NativeAdbService.isTransportAlive(),
                ),
            )
        }
        val text = output.getOrThrow()
        if (!AppCommands.isSuccess(action, app.packageName, text)) {
            removePending(app.packageName)
            return ActionResult.Failure(
                action,
                app,
                AdbErrors.classify(text, NativeAdbService.isTransportAlive()),
            )
        }
        when (action.listEffect) {
            ListEffect.REMOVE_ROW -> {
                removeApp(app.packageName)
                cache.removePackage(deviceKey ?: UNKNOWN_DEVICE, app.packageName)
                removePending(app.packageName)
            }

            ListEffect.FLIP_ENABLED -> {
                flipEnabled(app.packageName)
                // 停用/启用：本地即时翻状态，再由一次列表刷新对齐设备真值。
                // 在途标记必须在这里就摘掉：否则行尾转圈永不消失，该行也无法点击/长按
                removePending(app.packageName)
                refresh()
            }

            ListEffect.NONE -> {
                removePending(app.packageName)
            }
        }
        return ActionResult.Success(action, app)
    }

    /**
     * 批量执行：同一动作对多个包顺序执行，逐包记录成败；
     * 执行完毕后一律刷新列表（与成败无关），然后退出批量模式由 UI 决定。
     */
    fun performBatch(
        action: AppAction,
        packages: List<String>,
        onFinished: (BatchOutcome) -> Unit,
    ) {
        // 空选中集不执行：否则会白跑一次列表刷新并弹出「0 个成功」的误导提示。
        // 正常路径下底栏图标已置灰点不到，这里是兜底
        if (packages.isEmpty()) return
        scope.launch {
            addPending(packages)
            val tally = BatchTally()
            for (packageName in packages) {
                val command = AppCommands.command(action, packageName) ?: continue
                val output = runCatching { NativeAdbService.shell(command) }
                val text = output.getOrNull()
                if (text != null && AppCommands.isSuccess(action, packageName, text)) {
                    tally.recordSuccess()
                } else {
                    tally.recordFailure(text ?: output.exceptionOrNull()?.message)
                }
            }
            load(withCache = false)
            removePending(packages)
            val outcome = BatchOutcome(
                action = action,
                successCount = tally.successCount,
                failureCount = tally.failureCount,
                lastError = tally.lastError,
            )
            withContext(Dispatchers.Main) { onFinished(outcome) }
        }
    }

    // ---------------------------------------------------------------- 安装

    /** 本机应用安装：逐个推送 base + split，一次性提交安装。 */
    fun installLocalApps(apps: List<LocalApp>, onFinished: (InstallOutcome) -> Unit) {
        // 空列表也要回一次结果：静默 return 会让用户点完确认后什么都没有——
        // 既没有进度弹窗也没有提示，看起来就是「装了但失败了」
        if (apps.isEmpty()) {
            reportNothingToInstall(onFinished)
            return
        }
        launchTransfer transfer@{
            _installing.value = true
            // 每个应用的 base + split 字节数与整批总字节；总分母一次算定，中途不变
            resetScratchDirs()
            val appBytes = apps.map { app ->
                app.apkPaths.sumOf { path -> File(path).length().coerceAtLeast(0L) }
            }
            appBytes.forEachIndexed { index, bytes ->
                if (bytes <= 0L) {
                    android.util.Log.w(
                        "DeviceAppRepository",
                        "install: ${apps[index].packageName} reports no readable bytes (${apps[index].apkPaths})",
                    )
                }
            }
            val totalBytes = appBytes.sum()
            var doneBytes = 0L
            val tally = BatchTally()
            var cancelled = false
            try {
                apps.forEachIndexed { index, app ->
                    ensureActive()
                    // 基线 = 该应用之前所有应用的字节数。
                    // ApkInstaller 的 onProgress 只在本应用内部累计（pushedBytes 声明在
                    // install 函数里，每个应用从 0 数起），不补上基线的话每换一个应用
                    // 进度条就归零重来——批量导出那条是累加的，两边必须一致
                    val base = appBytes.take(index).sum()
                    _transfer.value = TransferProgress(
                        active = true,
                        label = app.label,
                        doneBytes = doneBytes,
                        totalBytes = totalBytes,
                        index = index + 1,
                        count = apps.size,
                    )
                    // 本机列表同样是缓存：应用在选择器打开后被卸载时 apk 路径已不存在，
                    // ApkInstaller 会在推送前抛错。转成一条能读的失败原因，别让异常冲出协程
                    // ——那样回调不触发、界面什么都不显示
                    val outcome = try {
                        installer.install(
                            app.apkPaths,
                            expectedPackage = app.packageName,
                            onProgress = { pushed, _ ->
                                doneBytes = base + pushed
                                _transfer.value = _transfer.value.copy(
                                    doneBytes = doneBytes,
                                    totalBytes = maxOf(_transfer.value.totalBytes, doneBytes),
                                )
                            },
                            onCommitting = {
                                _transfer.value = _transfer.value.copy(
                                    label = app.label,
                                    totalBytes = 0L,
                                    phase = TransferPhase.COMMIT,
                                )
                            },
                        )
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        ApkInstaller.InstallResult.Failure(
                            AppRuntime.stringResource(R.string.app_manager_install_package_gone),
                        )
                    }
                    when (outcome) {
                        is ApkInstaller.InstallResult.Success -> tally.recordSuccess()
                        is ApkInstaller.InstallResult.Failure -> tally.recordFailure(outcome.reason)
                    }
                }
            } catch (cancel: CancellationException) {
                // 中断：cancelable flag 标记，finally 里不重出「已安装」口径
                cancelled = true
                throw cancel
            } finally {
                withContext(NonCancellable) {
                    // 先解除忙碌态再刷新：刷新可能很慢，甚至因刚被中断的传输而失败。
                    // 顺序反了的话 _installing 永远停在 true，顶栏按钮 / 批量栏一直禁用
                    _installing.value = false
                    // 中断不应再弹「已安装 0 个」——弹窗已单独提示已中断
                    // 回调抛错也要继续收尾：_transfer 停在活跃态会让进度弹窗关不掉、刷新也不跑
                    if (!cancelled) {
                        val outcome = InstallOutcome(tally.successCount, tally.failureCount, tally.lastError)
                        withContext(Dispatchers.Main) { runCatching { onFinished(outcome) } }
                    }
                    // 顺序不能调：刷新提到回调之前，提示就得等刷新，中间空一段
                    _transfer.value = TransferProgress()
                    runCatching { load(withCache = false) }
                }
            }
        }
    }

    /** 文件安装：系统文件选择器选中的 APK，逐个推送安装。 */
    /** 清掉上一次操作可能没走到 finally 的临时目录：残留最多活到下一次操作。 */
    private fun resetScratchDirs() {
        for (name in SCRATCH_DIRS) runCatching { File(AppRuntime.context.cacheDir, name).deleteRecursively() }
    }

    private val SCRATCH_DIRS = listOf("appmanager-install", "appmanager-export")

    /** 选择器给的输入字节数：没有它，拷贝阶段的分母是 0，进度条会退化成「传输中…」。 */
    private fun documentSize(uri: android.net.Uri): Long = runCatching {
        AppRuntime.context.contentResolver
            .query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) cursor.getLong(column) else 0L
            } ?: 0L
    }.getOrDefault(0L)

    /** 归档清单里的包名；清单读不出时为 null，容器分组据此判该组装不了。 */
    private fun archiveInfo(basePath: String): String? = runCatching {
        AppRuntime.context.packageManager.getPackageArchiveInfo(basePath, 0)?.packageName
    }.getOrNull()

    /**
     * 装完后提示条里的应用名。
     *
     * 设备清单里已有的包（更新、重装）取它的本地化名；新装的应用清单还没刷新，
     * 从本批 base 的归档资源取，取不到则退回条目名。
     */
    private fun installedLabel(packageName: String, basePath: String?, entryName: String): String =
        _apps.value.firstOrNull { it.packageName == packageName }?.displayLabel
            ?: basePath?.let { archiveLabel(it) }
            ?: entryName

    private fun archiveLabel(basePath: String): String? = runCatching {
        val manager = AppRuntime.context.packageManager
        val info = manager.getPackageArchiveInfo(basePath, 0)
        val application = info?.applicationInfo ?: return@runCatching null
        // 归档里的资源要挂上源目录才读得到，否则 loadLabel 拿到的是清单里的类名
        application.sourceDir = application.sourceDir ?: basePath
        application.publicSourceDir = application.sourceDir
        manager.getApplicationLabel(application).toString().takeIf { it.isNotBlank() }
    }.getOrNull()

    fun installDocuments(
        uris: List<android.net.Uri>,
        displayNames: List<String>,
        onFinished: (InstallOutcome) -> Unit,
    ) {
        if (uris.isEmpty()) {
            reportNothingToInstall(onFinished)
            return
        }
        launchTransfer transfer@{
            _installing.value = true
            resetScratchDirs()
            val tempDir = File(AppRuntime.context.cacheDir, "appmanager-install").apply { mkdirs() }
            val tally = BatchTally()
            // 已完成文件的字节和：文件大小只有落地后才知道，所以分母只能边做边长，
            // 但 doneBytes 必须是整批累加的，否则每换一个文件进度条就归零
            var doneBytes = 0L
            var lastLabel: String? = null
            var cancelled = false
            try {
                uris.forEachIndexed { index, uri ->
                    ensureActive()
                    val name = displayNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "install_$index.apk"
                    // 拷贝阶段一律不确定态：容器大小与装包阶段的分母（各部件字节和）不是一个量纲，
                    // 中途换基准会让用户看到「同一个进度条换了总数」。拿到部件字节和之前不给总数
                    _transfer.value = TransferProgress(
                        active = true,
                        label = name,
                        doneBytes = doneBytes,
                        index = index + 1,
                        count = uris.size,
                        phase = TransferPhase.PREPARE,
                    )
                    // 单个 .apk 直接把流推给设备，进度按条目名报；成败以设备输出为准
                    if (!InstallGrouping.isContainer(name) && name.lowercase().endsWith(".apk")) {
                        val total = documentSize(uri)
                        if (total <= 0L) {
                            Log.w(
                                "DeviceAppRepository",
                                "install: $name reports no readable size, falling back to pushed bytes",
                            )
                        }
                        // 条目报不出大小时分母留 0，进度条走不确定态
                        _transfer.value = _transfer.value.copy(
                            totalBytes = if (total > 0L) doneBytes + total else 0L,
                            phase = TransferPhase.TRANSFER,
                        )
                        val outcome = try {
                            val input = AppRuntime.context.contentResolver.openInputStream(uri)
                                ?: throw java.io.IOException("cannot read $name")
                            input.use { stream ->
                                installer.installStream(
                                    input = stream,
                                    totalBytes = total,
                                    onProgress = { pushed, _ ->
                                        val done = doneBytes + pushed
                                        _transfer.value = _transfer.value.copy(
                                            doneBytes = done,
                                            totalBytes = maxOf(_transfer.value.totalBytes, done),
                                        )
                                    },
                                    onCommitting = {
                                        _transfer.value = _transfer.value.copy(
                                            totalBytes = 0L,
                                            phase = TransferPhase.COMMIT,
                                        )
                                    },
                                )
                            }
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (error: Exception) {
                            ApkInstaller.InstallResult.Failure(error.message ?: error.javaClass.simpleName)
                        }
                        when (outcome) {
                            is ApkInstaller.InstallResult.Success -> tally.recordSuccess()
                            is ApkInstaller.InstallResult.Failure -> tally.recordFailure(outcome.reason)
                        }
                        doneBytes += total
                        lastLabel = null
                        return@forEachIndexed
                    }
                    val tempFile = runCatching {
                        AppRuntime.context.contentResolver.openInputStream(uri)?.use { input ->
                            copyToTempFile(input, tempDir, name)
                        }
                    }.getOrNull()
                    if (tempFile == null) {
                        tally.recordFailure("cannot read $name")
                        return@forEachIndexed
                    }
                    val groups = try {
                        unpackInstallables(tempFile, File(tempDir, "unpacked-$index"), name)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        tally.recordFailure(
                            if (error is NoInstallableApk) {
                                AppRuntime.stringResource(R.string.app_manager_install_no_apk)
                            } else {
                                error.message ?: error.javaClass.simpleName
                            },
                        )
                        tempFile.delete()
                        return@forEachIndexed
                    }
                    val total = groups.sumOf { group -> group.apkPaths.sumOf { File(it).length().coerceAtLeast(0L) } }
                    if (total <= 0L) {
                        android.util.Log.w(
                            "DeviceAppRepository",
                            "install: $name reports no readable bytes, falling back to pushed bytes",
                        )
                    }
                    // 分母 = 已完成字节 + 当前容器内各安装包的字节和；
                    // doneBytes 整批累加，进度条不会每换一个文件就归零
                    _transfer.value = _transfer.value.copy(
                        totalBytes = doneBytes + total,
                        phase = TransferPhase.TRANSFER,
                    )
                    for (group in groups) {
                        ensureActive()
                        // 包名取自本组 base 的归档清单：读不到就判这组失败，因为回读 `pm path`
                        // 是容器安装成败的第二道判据
                        val base = group.apkPaths.firstOrNull()
                        val packageName = base?.let { archiveInfo(it) }
                        if (packageName == null) {
                            tally.recordFailure(AppRuntime.stringResource(R.string.app_manager_install_unreadable_package))
                            doneBytes += group.apkPaths.sumOf { File(it).length().coerceAtLeast(0L) }
                            continue
                        }
                        val outcome = installer.install(
                            group.apkPaths,
                            expectedPackage = packageName,
                            onProgress = { pushed, _ ->
                                val done = doneBytes + pushed
                                _transfer.value = _transfer.value.copy(
                                    doneBytes = done,
                                    totalBytes = maxOf(_transfer.value.totalBytes, done),
                                )
                            },
                            onCommitting = {
                                _transfer.value = _transfer.value.copy(
                                    label = installedLabel(packageName, base, name),
                                    totalBytes = 0L,
                                    phase = TransferPhase.COMMIT,
                                )
                            },
                        )
                        when (outcome) {
                            is ApkInstaller.InstallResult.Success -> tally.recordSuccess()
                            is ApkInstaller.InstallResult.Failure -> tally.recordFailure(outcome.reason)
                        }
                        // 标签按本组收尾：一次选多个容器安装时各报各的应用名
                        lastLabel = if (outcome is ApkInstaller.InstallResult.Success) {
                            installedLabel(packageName, base, name)
                        } else {
                            null
                        }
                        doneBytes += group.apkPaths.sumOf { File(it).length().coerceAtLeast(0L) }
                    }
                    tempFile.delete()
                }
            } catch (cancel: CancellationException) {
                cancelled = true
                throw cancel
            } finally {
                withContext(NonCancellable) {
                    tempDir.deleteRecursively()
                    _installing.value = false
                    if (!cancelled) {
                        val outcome = InstallOutcome(tally.successCount, tally.failureCount, tally.lastError, label = lastLabel)
                        withContext(Dispatchers.Main) { runCatching { onFinished(outcome) } }
                    }
                    _transfer.value = TransferProgress()
                    runCatching { load(withCache = false) }
                }
            }
        }
    }

    private class NoInstallableApk : IllegalStateException()

    /**
     * 安装输入可能是容器：`.apks`（本应用导出分包应用的形态）或批量导出的 `.zip`。
     * 分包必须与 base 一起提交，不同应用又不能混在一次安装会话里，所以按应用摊成若干组，
     * 容器里的安装包落到临时子目录（随 tempDir 一起删）。
     */
    private fun unpackInstallables(file: File, dir: File, name: String): List<InstallGroup> {
        if (!InstallGrouping.isContainer(name)) return listOf(InstallGroup(listOf(file.path)))
        dir.mkdirs()
        val singles = mutableListOf<String>()
        val groups = mutableListOf<InstallGroup>()
        // 每个内层容器单独一层子目录：里面同样可能叫 base.apk，摊平会互相顶掉
        var nested = 0
        java.util.zip.ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val entryName = File(entry.name).name
                val entryLower = entryName.lowercase()
                if (!entryLower.endsWith(".apk") && !InstallGrouping.isContainer(entryName)) continue
                val staged = File(dir, entryName)
                staged.outputStream().use { output -> zip.copyTo(output) }
                if (InstallGrouping.isContainer(entryName)) {
                    groups += unpackInstallables(staged, File(dir, "nested-$nested"), entryName)
                    nested++
                } else {
                    singles += staged.path
                }
            }
        }
        if (singles.isEmpty() && groups.isEmpty()) throw NoInstallableApk()
        // base + split 必须进同一个会话；不同应用不能混在一次里，所以按容器内容分组
        return InstallGrouping.group(singles) + groups
    }

    /**
     * 空列表的兜底：回一次失败结果，不让调用方静默无反馈。
     *
     * 两个安装入口（本机应用 / 文件选择）都可能拿到空列表：调用方在「确认」和
     * 「执行」之间隔了一个对话框，中途会话状态（比如选择集）被清空就会变空。
     * 此时若直接 return，用户点了确认后什么都没有——没进度也没提示。
     */
    private fun reportNothingToInstall(onFinished: (InstallOutcome) -> Unit) {
        scope.launch {
            withContext(Dispatchers.Main) {
                onFinished(
                    InstallOutcome(
                        successCount = 0,
                        failureCount = 1,
                        lastError = AppRuntime.stringResource(R.string.app_manager_install_nothing),
                    ),
                )
            }
        }
    }

    /** 没有任何可写的落盘目标：与传输失败区分开，界面按「打不开落盘目标」报。 */
    private class ExportTargetMissing : Exception()

    /**
     * 导出产物写到哪。
     *
     * - Android 10 及以上：条目由本仓库插进系统 Download/Scrcpy 目录（`MediaStore.Downloads`），
     *   拷贝期间挂 `IS_PENDING` 对其它应用不可见；收尾时按结果「撤下 pending 让它可见 /
     *   直接 delete」。自己插入的条目删得掉，所以中断与失败不留半个文件。
     * - 更早的版本没有分区插入（`minSdk 26`），由调用方先弹保存框、把 SAF 的流传进来；
     *   那条路的 uri 不支持删除，中断会留下调用方选的位置上的空文件，因此只在旧系统上用。
     */
    private interface ExportSink {
        val stream: java.io.OutputStream

        /**
         * 传输收尾：[complete] 为 true 表示内容完整。
         *
         * 完整时返回的是「条目对其它应用可见」：撤下 pending 命中即真，未命中则按回读的
         * 条目状态判定——`IS_PENDING` 为 0 且文件描述符的实际大小 > 0 才算可见，判据不
         * 成立时条目已被删掉（删除结果不参与判定）。不完整时只删条目，返回值不代表可见
         * 性。调用方把 false 当「条目对其它应用不可见」处理；SAF 那条路既撤回不了也查不到
         * 结果，见 [legacySink]。
         */
        fun finish(complete: Boolean): Boolean

        /**
         * 落盘条目的 uri：界面据它拉起「打开方式」，或交给本机安装器（单 `.apk`）；
         * 取不到时为 null。
         *
         * SAF 那条路拿不到可交付的 uri 之外的凭证，由调用方的保存框给出；拿不到时为 null，
         * 界面据此不给动作按钮。
         */
        val uri: android.net.Uri?

        /**
         * 条目所在目录的 uri：单个 `.apk` 的产物没有可用 uri 时，界面用它打开所在目录，
         * 用户在文件页面里点开产物。
         *
         * 拿不到时为 null（目录未知），界面据此不给动作按钮。
         */
        val folderUri: android.net.Uri?
    }

    /** 系统是否支持直接写 Download/Scrcpy 目录（Android 10+）。 */
    fun canPublishToDownloads(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    /**
     * 在 Download/Scrcpy 目录插入一个待写入条目并打开它的输出流。
     *
     * 插不进去（无权限 / 存储不可用）时返回 null，调用方退回保存框那条路。
     */
    private fun openDownloadsSink(fileName: String, mimeType: String): ExportSink? {
        if (!canPublishToDownloads()) return null
        val resolver = AppRuntime.context.contentResolver
        val entryUri = runCatching {
            resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, EXPORT_RELATIVE_PATH)
                    // 传输期间对其它应用不可见，内容完整了再撤下
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                },
            )
        }.getOrNull() ?: return null
        val stream = runCatching { resolver.openOutputStream(entryUri) }.getOrNull()
        if (stream == null) {
            runCatching { resolver.delete(entryUri, null, null) }
            return null
        }
        return object : ExportSink {
            override val stream: java.io.OutputStream = stream
            override val uri: android.net.Uri = entryUri
            // 条目由本应用插进 Download/Scrcpy：目录 uri 用条目实际落到的卷与同一段相对路径拼出
            override val folderUri: android.net.Uri = android.provider.DocumentsContract.buildDocumentUri(
                EXTERNAL_STORAGE_AUTHORITY,
                "${documentVolumeId(resolver, entryUri)}:$EXPORT_RELATIVE_PATH",
            )
            override fun finish(complete: Boolean): Boolean {
                if (!complete) {
                    runCatching { resolver.delete(entryUri, null, null) }
                    return true
                }
                // 影响行数只说明这次 update 动了几行，答不了「条目是否对外可见」：流关闭时
                // ROM 可能已自行撤下 pending，provider 也可能忙。未命中时回读条目自身状态，
                // 字节与可见性都在就按出货算；回读抛错视同判据不成立，一并
                // 走删条目 + 失败——不回读就删会删掉一份已经写完的产物
                val rows = runCatching {
                    resolver.update(
                        entryUri,
                        android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                        },
                        null,
                        null,
                    )
                }.getOrNull()
                if (rows != null && rows > 0) return true
                val published = runCatching {
                    // 条目状态只问 IS_PENDING: 这条 ROM 的 downloads 集合不认 size 列
                    // (实测报 Invalid column size), 字节数改用文件描述符的实际大小
                    val visible = resolver.query(
                        entryUri,
                        arrayOf(android.provider.MediaStore.Downloads.IS_PENDING),
                        null,
                        null,
                    )?.use { cursor ->
                        // 列序号按名字现取: provider 在前面多返回一列时, 字面量 0 会读到别的列
                        // 取不到这一列算「无法确认已发布」: 走到回读的前提是 update 没命中,
                        // 此时再没有 pending 的证据, 就不能把条目当成交付出去
                        val pendingColumn =
                            cursor.getColumnIndex(android.provider.MediaStore.Downloads.IS_PENDING)
                        cursor.moveToFirst() &&
                            (pendingColumn >= 0 && !cursor.isNull(pendingColumn) && cursor.getInt(pendingColumn) == 0)
                    } ?: false
                    visible && resolver.openFileDescriptor(entryUri, "r")?.use { it.statSize > 0L } == true
                }.getOrDefault(false)
                if (published) return true
                runCatching { resolver.delete(entryUri, null, null) }
                return false
            }
        }
    }

    /**
     * 旧系统路径：调用方给的 SAF 流，关掉即可。
     *
     * 落盘失败时连条目一起删：保存框返回时文件已经在用户选的目录里，写了一半的产物
     * 留着会被当成能装的文件。删不掉（provider 不认）只能按已处置收尾——[ExportSink.finish]
     * 在这里恒返回 true，落盘可见性无从校验。
     */
    private fun legacySink(
        stream: java.io.OutputStream,
        entryUri: android.net.Uri?,
    ): ExportSink = object : ExportSink {
        override val stream: java.io.OutputStream = stream
        override val uri: android.net.Uri? = entryUri
        override val folderUri: android.net.Uri? = parentFolderUri(entryUri)
        override fun finish(complete: Boolean): Boolean {
            runCatching { stream.close() }
            if (!complete && entryUri != null) {
                runCatching {
                    android.provider.DocumentsContract.deleteDocument(
                        AppRuntime.context.contentResolver,
                        entryUri,
                    )
                }
            }
            return true
        }
    }

    /** 系统「文件」页面的 provider authority（Download 目录挂在它的卷下）。 */
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * 条目所在存储卷在文件页面里的卷名。
     *
     * MediaStore 把内置存储叫 `external_primary`，文件页面里叫 `primary`；采用存储
     * （adoptable storage）下本函数返回 MediaStore 的卷名，文档 id 的卷段须与文件页面
     * 使用同一拼写才落得到目录。查询失败按内置存储算。
     */
    private fun documentVolumeId(
        resolver: android.content.ContentResolver,
        entryUri: android.net.Uri,
    ): String {
        val volumeName = runCatching {
            resolver.query(
                entryUri,
                arrayOf(android.provider.MediaStore.MediaColumns.VOLUME_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                // 列序号按名字现取: provider 可能在前面多返回几列
                val column = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.VOLUME_NAME)
                if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
            }
        }.getOrNull()
        return if (volumeName.isNullOrEmpty() || volumeName == "external_primary") {
            "primary"
        } else {
            volumeName
        }
    }

    /**
     * 条目的父目录：SAF 那条路的目录由用户的保存框选，只能按文档 id 反推。
     *
     * 文档 id 以 `/` 分层是本地文件页面的形态，去掉最后一段就是父目录；不分层的 provider
     * 与其根上的条目都算目录未知。
     */
    private fun parentFolderUri(entryUri: android.net.Uri?): android.net.Uri? {
        val authority = entryUri?.authority ?: return null
        val documentId = runCatching {
            android.provider.DocumentsContract.getDocumentId(entryUri)
        }.getOrNull() ?: return null
        val parentId = documentId.substringBeforeLast('/')
        if (parentId == documentId) return null
        return runCatching {
            android.provider.DocumentsContract.buildDocumentUri(authority, parentId)
        }.getOrNull()
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val ZIP_MIME_TYPE = "application/zip"

    /** 分包应用整组导出的容器仍是 zip，靠扩展名让分包安装器认出来。 */
    private const val APKS_MIME_TYPE = "application/vnd.android.apk-set"

    /**
     * 导出产物的落盘相对路径（`Download/Scrcpy`）：站在系统下载目录下的子目录里，
     * 不跟用户自己下的文件混在一起。
     *
     * 只能是字面量：`const val` 的初值不能取 Java 静态字段（`Environment.DIRECTORY_DOWNLOADS`），
     * 而 MediaStore 要的就是相对路径字符串。
     */
    private const val EXPORT_RELATIVE_PATH = "Download/Scrcpy"

    internal const val EXPORT_NOT_PUBLISHED = "export output was not published"

    internal const val EXPORT_NO_TARGET = "no writable export target"

    /**
     * 导出产物的形态：单个包给 `.apk`，带 split 分包的整组给 `.apks`（容器仍是
     * zip，靠扩展名被分包安装器认下），批量给一个 `.zip`。
     */
    enum class ExportKind(val extension: String, val mimeType: String) {
        APK("apk", APK_MIME_TYPE),
        APK_SET("apks", APKS_MIME_TYPE),
        ARCHIVE("zip", ZIP_MIME_TYPE),
    }

    /**
     * 按安装包件数定产物形态：一个包落成 `.apk`，带 split 的整组落成 `.apks`。
     *
     * 导出落盘与 Android 9 及以下的保存框都用它取名，两处不会给出不同扩展名。
     */
    internal fun exportKindOf(partCount: Int): ExportKind =
        if (partCount > 1) ExportKind.APK_SET else ExportKind.APK

    /**
     * 导出前问设备：这个应用此刻有几个安装包。
     *
     * 保存框的扩展名与 MIME 在弹框时就定了，而列表里的分包标记可能过期（应用在列表加载
     * 之后被更新成带分包、或被卸载），所以按 `pm path` 现查一次。查询失败先报出来，
     * 省掉用户挑一次目录。
     *
     * 超时走 [NativeAdbService.shell] 的线程中断兜底，链路上读不动时也能收口。
     * 页面离场取消这次查询不算失败：取消继续上抛，调用方的协程随之结束。
     */
    suspend fun resolveExportKind(packageName: String): ExportKindResult = try {
        ExportKindResult.Ready(
            exportKindOf(remoteApkPaths(packageName, timeoutMs = EXPORT_KIND_TIMEOUT_MS).size),
        )
    } catch (cancelled: CancellationException) {
        if (cancelled !is TimeoutCancellationException) throw cancelled
        ExportKindResult.TimedOut
    } catch (error: Throwable) {
        ExportKindResult.Unavailable(
            AdbErrors.classify(error.message.orEmpty(), NativeAdbService.isTransportAlive()),
        )
    }

    /** 形态查询的结局：拿到形态、等了 [EXPORT_KIND_TIMEOUT_MS] 仍没回话，或归好类的失败。 */
    sealed interface ExportKindResult {
        data class Ready(val kind: ExportKind) : ExportKindResult

        /** 查询超时：设备一句话都没回。 */
        data object TimedOut : ExportKindResult

        /** 链路或设备原因，已由 [AdbErrors] 归类。 */
        data class Unavailable(val reason: AppManagerError) : ExportKindResult
    }

    /**
     * 导出单个应用：形态见 [exportKindOf]；带 split 时整组打进一个容器——只导 base 的
     * 产物装不上（缺分包）。
     *
     * @param legacyStream Android 9 及以下的落盘流（由调用方的保存框给出）；新系统传 null。
     * @param legacyUri 该流对应的 SAF 条目，导出成功后界面据它拉起「打开方式」（单 `.apk`
     * 交给本机安装器）。拿不到传 null。
     * 落盘前就失败时，这个条目（保存框已经建好的空文件）连同流一起由本函数收掉。
     * @param expectedKind 保存框弹出时定下的形态；落盘时比对，不一致就不写名实不符的文件。
     */
    fun exportApk(
        app: DeviceApp,
        legacyStream: java.io.OutputStream? = null,
        legacyUri: android.net.Uri? = null,
        expectedKind: ExportKind? = null,
        onResult: (ExportResult) -> Unit,
    ) {
        launchTransfer transfer@{
            var sink: ExportSink? = null
            var keepTarget = false
            var kind = ExportKind.APK
            var lastError: String? = null
            val result = runCatching {
                // 取全部路径与逐路径大小要多次 adb 往返: 先把弹窗放进不确定态,
                // 免得这段停在确定态的 0
                _transfer.value = TransferProgress(
                    active = true,
                    exporting = true,
                    label = app.displayLabel,
                    index = 1,
                    count = 1,
                    phase = TransferPhase.PREPARE,
                )
                val parts = exportParts(app.packageName)
                kind = exportKindOf(parts.size)
                // 扩展名在弹框时就定了：用户挑目录期间形态变了就别写名实不符的文件
                if (expectedKind != null && expectedKind != kind) {
                    error(AppRuntime.stringResource(R.string.app_manager_export_kind_changed))
                }
                sink = openDownloadsSink(
                    fileName = exportFileName(app.displayLabel, app.packageName, kind.extension),
                    mimeType = kind.mimeType,
                ) ?: legacyStream?.let { legacySink(it, legacyUri) }
                val target = sink ?: throw ExportTargetMissing()
                if (kind == ExportKind.APK) {
                    val part = parts.first()
                    // 分母用远端这个 APK 的真实字节数；只有单包时它才等于列表第三行那个大小
                    _transfer.value = _transfer.value.copy(
                        totalBytes = part.expectedBytes,
                        phase = TransferPhase.TRANSFER,
                    )
                    // 拉取阶段：字节数累计在本地，回调给的是增量
                    var pulled = 0L
                    target.stream.use { output ->
                        NativeAdbService.pull(part.remotePath, output) { len ->
                            ensureActive()
                            pulled += len
                            _transfer.value = _transfer.value.copy(doneBytes = pulled)
                        }
                    }
                    // 校验：与远端 stat 的字节数逐字节对齐
                    if (part.expectedBytes > 0L && pulled != part.expectedBytes) {
                        throw java.io.IOException("size mismatch: got $pulled, want ${part.expectedBytes}")
                    }
                    keepTarget = true
                } else {
                    // 分包应用：整组打进一个 zip，分母是全部部件之和（与列表那个大小同口径）
                    val source = ZipExportSource(
                        packageName = app.packageName,
                        label = app.displayLabel,
                        expectedBytes = parts.sumOf { it.expectedBytes },
                        parts = parts,
                    )
                    val sizeUnknown = source.expectedBytes == 0L
                    resetScratchDirs()
                val tempDir = File(AppRuntime.context.cacheDir, "appmanager-export").apply { mkdirs() }
                    try {
                        target.stream.buffered().use { rawOut ->
                            ZipOutputStream(rawOut).use { zip ->
                                val tally = assembleZip(
                                    sources = listOf(source),
                                    zip = zip,
                                    tempDir = tempDir,
                                    fetch = ::pullPart,
                                    onProgress = { done, total, index, count, label ->
                                        _transfer.value = TransferProgress(
                                            active = true,
                                            exporting = true,
                                            label = label,
                                            doneBytes = done,
                                            totalBytes = if (sizeUnknown) 0L else total,
                                            index = index,
                                            count = count,
                                            phase = TransferPhase.TRANSFER,
                                        )
                                    },
                                    ensureActive = { ensureActive() },
                                )
                                keepTarget = tally.keepTarget
                                lastError = tally.lastError
                            }
                        }
                    } finally {
                        tempDir.listFiles()?.forEach { file -> runCatching { file.delete() } }
                    }
                    // 一个条目都没成：组装层已把条目当废件，按失败上报真实原因
                    if (!keepTarget) throw java.io.IOException(lastError ?: "export failed")
                }
            }
            // 内容完整才让条目对外可见；中断与失败直接删掉自己插的那一条
            val published = sink?.finish(result.isSuccess && keepTarget) ?: false
            // 还没走到 sink 就失败（形态对不上、路径都取不到）时，保存框给的流与它建的条目归我方收
            if (sink == null) {
                runCatching { legacyStream?.close() }
                // 保存框返回时系统已经把条目建好了；一个字节没写的空壳留在用户选的目录里
                runCatching {
                    legacyUri?.let {
                        android.provider.DocumentsContract.deleteDocument(AppRuntime.context.contentResolver, it)
                    }
                }
            }
            withContext(NonCancellable) {
                _transfer.value = TransferProgress()
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            // 条目没能对外可见等于没落盘：按失败报，免得提示指向一个别人看不见的文件
                            if (published) {
                                onResult(
                                    ExportResult.Success(
                                        uri = sink.uri,
                                        kind = kind,
                                        folderUri = sink.folderUri,
                                    ),
                                )
                            } else {
                                onResult(
                                    ExportResult.Failure(
                                        AppManagerError.Device(EXPORT_NOT_PUBLISHED),
                                    ),
                                )
                            }
                        },
                        onFailure = { error ->
                            // 用户中断：弹窗已收起并另弹 aborted 提示；导出视为取消而非失败。
                            if (error !is CancellationException) {
                                onResult(
                                    ExportResult.Failure(
                                        // 没有落盘目标不是设备出问题，别把它报成 ADB 错误
                                        if (error is ExportTargetMissing) {
                                            AppManagerError.Device(EXPORT_NO_TARGET)
                                        } else {
                                            AdbErrors.classify(
                                                error.message ?: "",
                                                NativeAdbService.isTransportAlive(),
                                            )
                                        },
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    /**
     * 单个应用的全部安装包路径：`pm path` 的每一行——base.apk 在前，随后是各 split 分包。
     *
     * 只取首行就等于把带分包的应用导成一个装不上的半份产物（装上会报
     * INSTALL_FAILED_MISSING_SPLIT），所以这里一条都不许丢。
     */
    private suspend fun remoteApkPaths(packageName: String, timeoutMs: Long = 0): List<String> {
        val pathOutput = NativeAdbService.shell(AppCommands.apkPath(packageName), timeoutMs)
        val paths = pathOutput.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .toList()
        // 列表可能是缓存：应用在列表加载后被卸载时这里拿不到任何路径。
        // 这条消息会一路透传成导出失败原因，所以写成人能读的话
        if (paths.isEmpty()) error(AppRuntime.stringResource(R.string.app_manager_export_target_gone))
        return paths
    }

    /** 远端文件的字节数；取不到时返回 0（调用方按「大小未知」显示不确定态进度）。 */
    private suspend fun remoteFileSize(remotePath: String): Long {
        val output = runCatching { NativeAdbService.shell(AppCommands.fileSize(remotePath)) }.getOrNull()
            ?: return 0L
        return output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.toLongOrNull() ?: 0L
    }

    /**
     * 单包导出的部件清单：条目名取远端文件名本身（base.apk、split_config.*.apk），
     * 解压出来就是这个应用的一整组安装包。
     *
     * 逐路径 stat 是有意的：分母要跟列表第三行那个大小（base + 全部 split）同口径，
     * 用单个 stat 会算小，进度条提前走满。
     */
    private suspend fun exportParts(packageName: String): List<ZipExportPart> =
        remoteApkPaths(packageName).map { remotePath ->
            ZipExportPart(
                entryName = remotePath.substringAfterLast('/'),
                remotePath = remotePath,
                expectedBytes = remoteFileSize(remotePath),
            )
        }

    /** 把某个部件直接拉进目标流（单包是落盘流，多部件是 zip 条目），按增量回调进度。 */
    private suspend fun pullPart(part: ZipExportPart, dest: OutputStream, onBytes: (Long) -> Unit) {
        NativeAdbService.pull(part.remotePath, dest) { len -> onBytes(len) }
    }

    /**
     * 把本地文件拷到目标流，按已拷字节回调进度。
     *
     * 每拷一块检查一次活性：中断在拷贝阶段生效，不必等整个文件拷完。
     */
    private suspend fun copyWithProgress(
        source: File,
        target: OutputStream,
        onCopied: (Long) -> Unit,
    ) {
        source.inputStream().buffered().use { input ->
            val buffer = ByteArray(COPY_CHUNK_BYTES)
            var copied = 0L
            while (true) {
                // 本函数不在 launch 的作用域里，活性检查取协程上下文
                kotlin.coroutines.coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read <= 0) break
                target.write(buffer, 0, read)
                copied += read
                onCopied(copied)
            }
            target.flush()
        }
    }

    /**
     * 批量导出应用：多个应用的安装包打成一个 ZIP 直接写进落盘目标。
     *
     * - 整批分母先按远端各包全部安装包（base + 各 split 分包）的真实字节数求和
     *   （`pm path` + 逐路径 `stat -c %s`），逐部件 pull 的累计字节按同一口径推进，进度条
     *   全程单调走到 100%，与列表里显示的包大小同口径；批里任一包的大小未知（0）时整批
     *   按未知处理，弹窗显示未知大小；
     * - 每个包先落临时文件、按远端字节数校验通过再写进 ZIP：只有 base 的应用落成
     *   `<包名>.apk`，带分包的落成 `<包名>.apks`（内层是 base.apk 与各 split_*.apk）；单包
     *   失败或中断不会在 ZIP 里留下截断条目；
     * - 落盘条目撤不撤看两条判据：组装层——用户中断、条目写入阶段失败（整份产物按作废
     *   处理）、一个条目都没成；落盘可见性——收尾时撤下 pending 没命中、回读条目自身
     *   （`IS_PENDING` 为 0 且文件描述符的实际大小 > 0）也不成立。两条之外还有组装之外的
     *   IO 失败（写中央目录、flush、取远端大小）兜底撤下。都没触发才保留，里面是那几份
     *   完整的 APK。
     *
     * @param legacyStream Android 9 及以下的落盘流（由调用方的保存框给出）；新系统传 null。
     * @param legacyUri 该流对应的 SAF 条目，导出成功后界面据它拉起「打开」；拿不到传 null。
     */
    fun exportAppsAsZip(
        apps: List<DeviceApp>,
        legacyStream: OutputStream? = null,
        legacyUri: android.net.Uri? = null,
        onFinished: (ExportOutcome) -> Unit,
    ) {
        if (apps.isEmpty()) return
        launchTransfer transfer@{
            // 字节进度与成败计数由 assembleZip 维护（进度只增不减的高水位也在那里）
            var successCount = 0
            var failureCount = 0
            var lastError: String? = null
            var cancelled = false
            var keepTarget = false
            val sink = openDownloadsSink("scrcpy-apps-${System.currentTimeMillis()}.zip", ZIP_MIME_TYPE)
                ?: legacyStream?.let { legacySink(it, legacyUri) }
            if (sink == null) {
                Log.w(TAG, "exportAppsAsZip: $EXPORT_NO_TARGET")
                withContext(Dispatchers.Main) {
                    // 没有可写的落盘目标等于一个都没导出，别让它吃 archived 的默认值
                    onFinished(ExportOutcome(0, apps.size, EXPORT_NO_TARGET, archived = false))
                }
                return@transfer
            }
            resetScratchDirs()
                val tempDir = File(AppRuntime.context.cacheDir, "appmanager-export").apply { mkdirs() }
            try {
                // 逐包取路径与大小要多次 adb 往返: 先把弹窗放进不确定态, 别让这段没有提示。
                _transfer.value = TransferProgress(
                    active = true,
                    exporting = true,
                    label = apps.first().displayLabel,
                    index = 1,
                    count = apps.size,
                )
                // 整批分母：逐包取远端全部 APK（base + 各分包）的字节数之和
                val sources = apps.map { app ->
                    val parts = runCatching { exportParts(app.packageName) }.getOrNull()
                    ZipExportSource(
                        packageName = app.packageName,
                        label = app.displayLabel,
                        // 逐路径 stat 拿不到就按未知算: 分母小于实际时分数会超过 1 被夹在 1f 上,
                        // 进度条提前走满; 分包应用只算 base 更是等于把列表那个大小当成假的
                        expectedBytes = parts
                            ?.takeIf { partsList -> partsList.all { it.expectedBytes > 0L } }
                            ?.sumOf { it.expectedBytes }
                            ?: 0L,
                        parts = parts.orEmpty(),
                        error = if (parts == null) AppRuntime.stringResource(R.string.app_manager_export_target_gone) else null,
                        grouped = true,
                    )
                }
                // 批里有一个未知（0）就整批按未知上报: 0 是与 ZipExportAssembler 共用的「未取到」
                // 语义, 分母按已知项求和会让分数超过 1、被 fraction 夹在 1f 上(进度条提前走满);
                // 来源上的 0 另有「不做长度校验」的含义, 未知只体现在上报的分母上
                val batchSizeUnknown = sources.any { it.expectedBytes == 0L }
                sink.stream.buffered().use { rawOut ->
                    ZipOutputStream(rawOut).use { zip ->
                        // 组装交给纯逻辑：先落临时文件、字节数对齐后才写条目，逐包清临时文件
                        val tally = assembleZip(
                            sources = sources,
                            zip = zip,
                            tempDir = tempDir,
                            fetch = ::pullPart,
                            onProgress = { done, total, index, count, label ->
                                _transfer.value = TransferProgress(
                                    active = true,
                                    exporting = true,
                                    label = label,
                                    doneBytes = done,
                                    totalBytes = if (batchSizeUnknown) 0L else total,
                                    index = index,
                                    count = count,
                                    phase = TransferPhase.TRANSFER,
                                )
                            },
                            ensureActive = { ensureActive() },
                        )
                        successCount = tally.successCount
                        failureCount = tally.failureCount
                        lastError = tally.lastError
                        cancelled = tally.cancelled
                        keepTarget = tally.keepTarget
                    }
                }
            } catch (cancel: CancellationException) {
                cancelled = true
            } catch (error: Exception) {
                // 逃到这里的是组装之外的 IO：写中央目录（zip.close）、flush、收尾，或取远端
                // 大小。产物一定不完整，撤回落盘条目——半份 ZIP 比没有更糟。
                // 计数保留真值：它们答的是「哪些包拉成功过」，与「产物留不留」是两个问题，
                // ExportOutcome.archived 单独把「产物作废」告诉界面，两者不互相顶替
                keepTarget = false
                lastError = error.message ?: error.javaClass.simpleName
                Log.w(TAG, "exportAppsAsZip: archive write failed", error)
            } finally {
                // 逐包临时文件在这里清掉
                tempDir.listFiles()?.forEach { file -> runCatching { file.delete() } }
                val published = sink.finish(keepTarget)
                if (keepTarget && !published) {
                    // 按整批未导回报：计数不再当作成果
                    keepTarget = false
                    lastError = lastError ?: EXPORT_NOT_PUBLISHED
                    Log.w(TAG, "exportAppsAsZip: $EXPORT_NOT_PUBLISHED")
                }
                withContext(NonCancellable) {
                    _transfer.value = TransferProgress()
                    // 中断：导出视为取消；弹窗本身已提示已中断，不重复弹出总结口径
                    if (!cancelled) {
                        val outcome = ExportOutcome(
                            successCount = successCount,
                            failureCount = failureCount,
                            lastError = lastError,
                            archived = keepTarget,
                            // 产物已撤销时条目连文件都不在了，别把 uri 交出去
                            uri = if (keepTarget) sink.uri else null,
                        )
                        withContext(Dispatchers.Main) { onFinished(outcome) }
                    }
                }
            }
        }
    }

    /** 中断进行中的安装 / 提取（进度弹窗的「中断」）。 */
    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        _transfer.value = TransferProgress()
        // 立即解除忙碌态：被取消的协程尾部还要跑一次列表刷新，那次可能很慢甚至失败，
        // 不能拿它当解锁条件——否则顶栏安装按钮 / 批量栏会一直卡在禁用状态
        _installing.value = false
    }

    /** 本机应用枚举（供选择器使用）。 */
    fun loadLocalApps(onResult: (LocalAppRepository.Result) -> Unit) {
        // 同步置位：协程里才置位会让选择器先闪一帧空列表
        _loadingLocalApps.value = true
        val job = scope.launch {
            val result = withContext(Dispatchers.IO) { localAppRepository.loadUserApps() }
            _loadingLocalApps.value = false
            withContext(Dispatchers.Main) { onResult(result) }
        }
        // 看门狗：枚举是同步函数（逐包 Binder 读 PackageInfo + 文件长度），不响应取消。
        // 真卡住时协程挂在阻塞调用里，取消唤不醒它，界面会一直转圈。到点只收掉 busy
        // 标志并回报超时，不动后台那次枚举——它仍会跑完，回来时照常覆盖这个结果
        scope.launch {
            delay(LOCAL_LOAD_TIMEOUT_MS)
            if (job.isCompleted) return@launch
            Log.w(TAG, "loadLocalApps: still running after ${LOCAL_LOAD_TIMEOUT_MS}ms")
            _loadingLocalApps.value = false
            withContext(Dispatchers.Main) { onResult(LocalAppRepository.Result.Slow) }
        }
    }

    // ---------------------------------------------------------------- 内部

    private fun addPending(packageName: String) {
        _pendingPackages.value = _pendingPackages.value + packageName
    }

    private fun addPending(packageNames: List<String>) {
        _pendingPackages.value = _pendingPackages.value + packageNames
    }

    private fun removePending(packageName: String) {
        _pendingPackages.value = _pendingPackages.value - packageName
    }

    private fun removePending(packageNames: List<String>) {
        _pendingPackages.value = _pendingPackages.value - packageNames.toSet()
    }

    private fun removeApp(packageName: String) {
        _apps.value = _apps.value.filterNot { it.packageName == packageName }
    }

    private fun flipEnabled(packageName: String) {
        _apps.value = _apps.value.map { app ->
            if (app.packageName == packageName) app.copy(isEnabled = !app.isEnabled) else app
        }
    }

    private const val UNKNOWN_DEVICE = "unknown"
    private const val TAG = "DeviceAppRepository"

    /**
     * 加载看门狗时长。
     *
     * 正常路径（723 个应用、走 app 自研 ADB）实测约 12 秒，给到 30 秒有 2.5 倍余量。
     * 探测自身有 30 秒超时，但那条路径在对端切断连接后可能卡在阻塞读上唤不醒，
     * 所以这里再兜一层：到点只把界面从「转圈」换成明确提示，不动后台那次加载。
     */
    internal const val LOAD_TIMEOUT_MS = 30_000L

    /**
     * 本机应用枚举的看门狗时长。
     *
     * 实测 89 包枚举约 30~90ms，给到 15 秒有极大余量；设得比设备侧短，因为这条路上用户
     * 能看到的只有「选择器转圈」，转久了没有任何可操作项。
     */
    internal const val LOCAL_LOAD_TIMEOUT_MS = 15_000L

    /**
     * 导出前查询产物形态的时长。
     *
     * 只有一次 `pm path`，正常几十毫秒；设上限是为了让「点了导出没反应」有个尽头。
     */
    internal const val EXPORT_KIND_TIMEOUT_MS = 8_000L

    /** 临时文件 → 目标流每次拷走的字节数（256 KiB：够大不必频繁回调，够小中断响应及时）。 */
    private const val COPY_CHUNK_BYTES = 256 * 1024

    sealed interface ActionResult {
        data class Success(val action: AppAction, val app: DeviceApp) : ActionResult
        data class Failure(
            val action: AppAction,
            val app: DeviceApp,
            val error: AppManagerError,
        ) : ActionResult
    }

    data class BatchOutcome(
        val action: AppAction,
        val successCount: Int,
        val failureCount: Int,
        val lastError: String?,
    )

    data class InstallOutcome(
        val successCount: Int,
        val failureCount: Int,
        val lastError: String?,
        val label: String? = null,
    )

    /**
     * 批量导出的收尾口径。
 *
     * [archived] = false 表示产物已被撤回落盘目标，**一个字节都没留给用户**——收尾阶段
     * 写中央目录 / flush 失败就属于这种。它与 [successCount] 是两件事：计数答「哪些包
     * 拉成功过」，即使产物作废也保持真值；界面按 [archived] 决定报计数还是报整批未导出。
     */
    data class ExportOutcome(
        val successCount: Int,
        val failureCount: Int,
        val lastError: String?,
        /** false = 产物已撤销，界面按「整批未导出」报，不报计数 */
        val archived: Boolean = true,
        /** 产物的落盘条目，界面据它拉起「打开」；产物已撤销或拿不到条目时为 null。 */
        val uri: android.net.Uri? = null,
    )

    sealed interface ExportResult {
        /**
         * [uri] 是落盘条目的 uri（SAF 那条路由调用方给出）；[kind] 是产物形态，界面据此
         * 换动作与 MIME。
         *
         * [folderUri] 是条目所在目录：安装失败时界面可退回打开它。
         */
        data class Success(
            val uri: android.net.Uri?,
            val kind: ExportKind,
            val folderUri: android.net.Uri?,
        ) : ExportResult

        data class Failure(val error: AppManagerError) : ExportResult
    }
}

/**
 * 进度弹窗的阶段。PREPARE = 取路径/大小、拷贝容器（不可量化）；TRANSFER = 推/拉字节；
 * COMMIT = 设备端会话提交与回读盖章（不可量化）。
 */
enum class TransferPhase { PREPARE, TRANSFER, COMMIT }

/** 安装 / 提取的传输进度（进度弹窗唯一数据源）。 */
data class TransferProgress(
    val active: Boolean = false,
    /** true = 提取 APK；false = 安装 APK（只影响弹窗标题）。 */
    val exporting: Boolean = false,
    /** 当前项：本机应用标签 / APK 文件名。 */
    val label: String = "",
    val doneBytes: Long = 0L,
    /** 0 表示未知总量（此时进度条走不确定态）。 */
    val totalBytes: Long = 0L,
    /** 第几项（1 起）。 */
    val index: Int = 0,
    val count: Int = 0,
    /** 弹窗阶段：标题按它取词；左格只在不可量化的 COMMIT 段给一句说明。 */
    val phase: TransferPhase = TransferPhase.TRANSFER,
) {
    val fraction: Float?
        get() = if (totalBytes <= 0L) null else (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

/**
 * 当前连接的设备标识（缓存隔离键）。
 *
 * 优先取真实 ADB 连接的地址：AppRuntime.currentConnectionTarget 是连接时写下的快照，
 * 切设备后可能仍是旧值——按它做 key 会把新设备的图标 / 列表写进旧设备目录，
 * 于是新设备一个图标都读不到。真实连接拿不到时才回退快照。
 */
fun currentDeviceKey(): String {
    NativeAdbService.connectedAddress()?.let { return DeviceIdentity.cachedKey(it) }
    val target = AppRuntime.currentConnectionTarget ?: return "unknown"
    // 回退快照 key 必须与 connectedAddress() 同形：它 USB 下给 "usb:<deviceId>"、LAN 下给
    // "host:port"。形态不一致时 ADB 锁被占用的那段时间会算出另一个目录，同一台设备出现两份缓存。
    val raw = if (target.connectionType == DeviceConnectionType.USB && target.deviceId != null)
        "usb:${target.deviceId}"
    else if (target.host.startsWith("usb:")) target.host
    else "${target.host}:${target.port}"
    return DeviceIdentity.cachedKey(raw)
}

/** 等上一个传输任务退场（它要删暂存目录、改写进度）的上限；卡在阻塞 shell 里时按超时放行。 */
private const val TRANSFER_HANDOVER_TIMEOUT_MS = 15_000L
