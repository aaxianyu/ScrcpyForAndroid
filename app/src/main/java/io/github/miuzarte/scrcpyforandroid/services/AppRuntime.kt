package io.github.miuzarte.scrcpyforandroid.services

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import io.github.miuzarte.scrcpyforandroid.i18n.AppLocale
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbMdnsDiscoverer
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.IdentityHashMap
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

// 用于不同 activity 之间传递实例
object AppRuntime {
    private lateinit var appContext: Context

    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        registerForegroundCallbacks()
        AdbMdnsDiscoverer.init(appContext)
        initialized = true
    }

    private var startedActivityCount = 0
    private val _isAppForeground = MutableStateFlow(false)

    /** 进程是否处于前台 (任一 Activity 处于 started 状态) */
    val isAppForeground: StateFlow<Boolean> = _isAppForeground.asStateFlow()

    private fun registerForegroundCallbacks() {
        (appContext as? Application)?.registerActivityLifecycleCallbacks(
            object: Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivityCount++
                    _isAppForeground.value = true
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                    _isAppForeground.value = startedActivityCount > 0
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    val context: Context
        get() = appContext

    var scrcpy: Scrcpy? = null
        private set
    var currentConnectionTarget: ConnectionTarget? = null
    var currentConnectedDevice: ConnectedDeviceInfo? = null

    // 当前设备使用的 profile ID (session 级, 脱离快捷设备独立运作)
    val currentConnectionProfileId = MutableStateFlow("global")

    private val sessionLock = Any()
    private var sessionServices: DeviceConnectionServices? = null

    /**
     * 进程级会话: [Scrcpy] 与连接服务只创建一次, Activity 重建 (切语言 / 深浅色 / 系统回收) 时复用
     *
     * 配置变化不要重建实例, 改 [Scrcpy.sessionConfig] 即可, 下一次 start() 生效
     */
    internal fun obtainSession(sessionConfig: Scrcpy.SessionConfig): Session {
        synchronized(sessionLock) {
            val existingScrcpy = scrcpy
            val existingServices = sessionServices
            if (existingScrcpy != null && existingServices != null) {
                return Session(existingScrcpy, existingServices)
            }

            val createdScrcpy = Scrcpy(
                appContext = appContext,
                initialSessionConfig = sessionConfig,
            )
            val adbCoordinator = DeviceAdbConnectionCoordinator()
            val connectionStateStore = ConnectionStateStore()
            val connectionController = ConnectionController(
                scrcpy = createdScrcpy,
                stateStore = connectionStateStore,
                adbCoordinator = adbCoordinator,
            )
            val autoReconnectManager = DeviceAdbAutoReconnectManager(
                controller = connectionController,
                stateStore = connectionStateStore,
            )
            val services = DeviceConnectionServices(
                adbCoordinator = adbCoordinator,
                connectionStateStore = connectionStateStore,
                connectionController = connectionController,
                autoReconnectManager = autoReconnectManager,
            )
            scrcpy = createdScrcpy
            sessionServices = services
            return Session(createdScrcpy, services)
        }
    }

    /**
     * 收尾会话
     *
     * 只应在 MainActivity 真正退出 (isFinishing) 时调用;
     * Activity 重建 (配置变更) 不能走这里, 否则连接状态与自动重连会被重置
     */
    internal fun releaseSession() {
        synchronized(sessionLock) {
            sessionServices?.autoReconnectManager?.close()
            AppScreenOn.release()
            runCatching { AdbKeepAliveService.stop(appContext) }
            sessionServices = null
            scrcpy = null
            currentConnectionTarget = null
            currentConnectedDevice = null
        }
    }

    internal class Session(
        val scrcpy: Scrcpy,
        val services: DeviceConnectionServices,
    )

    private val snackbarHostStateLock = Any()
    private val snackbarHostStateStack = mutableListOf<SnackbarHostState>()
    // 导航切页时可能同时存在同一页的两份组合（其中一份不参与绘制），栈顶不一定是屏幕上那份。
    // 记下哪些宿主真正完成了布局，取「栈中最后一个已布局的宿主」：切页后旧组合不再布局，
    // 屏幕上那份仍会持续布局，而新进入的页面登记得更晚。
    private val snackbarHostStateLaidOut =
        Collections.newSetFromMap(IdentityHashMap<SnackbarHostState, Boolean>())
    // 切页时栈会增删，位置不稳定；按登记先后给每份宿主编号，用来判断哪份是后来者
    private var hostSeq = 0L
    private val hostSeqByIdentity = IdentityHashMap<SnackbarHostState, Long>()

    var snackbarHostState: SnackbarHostState?
        get() = synchronized(snackbarHostStateLock) {
            snackbarHostStateStack.lastOrNull { snackbarHostStateLaidOut.contains(it) }
                ?: snackbarHostStateStack.lastOrNull()
        }
        set(value) {
            synchronized(snackbarHostStateLock) {
                snackbarHostStateStack.clear()
                snackbarHostStateLaidOut.clear()
                if (value != null) snackbarHostStateStack.add(value)
            }
        }

    private val snackbarScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun registerSnackbarHostState(hostState: SnackbarHostState): () -> Unit {
        synchronized(snackbarHostStateLock) {
            snackbarHostStateStack.add(hostState)
            hostSeqByIdentity[hostState] = ++hostSeq
        }
        return {
            synchronized(snackbarHostStateLock) {
                snackbarHostStateStack.remove(hostState)
                snackbarHostStateLaidOut.remove(hostState)
                hostSeqByIdentity.remove(hostState)
            }
        }
    }

    /** 宿主每次完成布局时调用：记下它已经在绘制。 */
    fun markSnackbarHostStateLaidOut(hostState: SnackbarHostState) {
        synchronized(snackbarHostStateLock) {
            if (snackbarHostStateStack.contains(hostState)) {
                snackbarHostStateLaidOut.add(hostState)
            }
        }
    }

    internal fun isHostRegistered(hostState: SnackbarHostState): Boolean =
        synchronized(snackbarHostStateLock) { snackbarHostStateStack.contains(hostState) }

    private fun hostSeqOf(hostState: SnackbarHostState): Long? =
        synchronized(snackbarHostStateLock) { hostSeqByIdentity[hostState] }

    /** 比 [seq] 更早登记、仍在绘制的宿主：退出后接管屏幕的那一页。 */
    private fun laidOutHostBefore(seq: Long): SnackbarHostState? =
        synchronized(snackbarHostStateLock) {
            snackbarHostStateStack.lastOrNull { host ->
                (hostSeqByIdentity[host] ?: Long.MAX_VALUE) < seq && snackbarHostStateLaidOut.contains(host)
            }
        }

    /**
     * 转场最长等这么多帧（约 2s）；仍无人接手就退回普通发送。
     */
    private const val SNACKBAR_HANDOFF_MAX_FRAMES = 120

    /**
     * 页面随后会退出：等接手的页面登记好提示宿主再发，提示才会落在留在屏幕上的页面。
     *
     * 转场期间旧页面还在栈里，直接发送会被它收走，随它一起消失；转场中新登记的宿主
     * 可能属于不在绘制的组合，所以只认登记在退出者之前、且仍在布局的那份。
     */
    internal fun snackbarAfterPageExit(
        message: String,
        duration: SnackbarDuration? = null,
    ) {
        val exiting = snackbarHostState
        snackbarScope.launch {
            val exitingSeq = exiting?.let(::hostSeqOf) ?: 0L
            var frames = 0
            while (frames < SNACKBAR_HANDOFF_MAX_FRAMES) {
                val host = laidOutHostBefore(exitingSeq)
                if (host != null) {
                    showOnHost(
                        host = host,
                        message = message,
                        actionLabel = null,
                        withDismissAction = true,
                        duration = duration,
                        onResult = null,
                        dismissNewest = false,
                    )
                    return@launch
                }
                awaitFrame()
                frames++
            }
            snackbar(message, duration = duration)
        }
    }

    suspend fun snackbarDismissNewest() = snackbarHostState?.newestSnackbarData()?.dismiss()

    /** 调用方未显式指定时长时，跟随「设置 → 杂项」的 Snackbar 显示时长。 */
    val snackbarDuration: SnackbarDuration
        get() = SnackbarDuration.Custom(
            Storage.appSettings.bundleState.value.snackbarDurationMs.coerceAtLeast(1).toLong(),
        )

    fun snackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = true,
        duration: SnackbarDuration? = null,
        onResult: ((SnackbarResult) -> Unit)? = null,
        dismissNewest: Boolean = false,
    ) = snackbarHostState?.let { host ->
        showOnHost(host, message, actionLabel, withDismissAction, duration, onResult, dismissNewest)
    }

    /**
     * 指定宿主发送：页面自己持有 [SnackbarHostState] 时用它，省去从宿主栈里猜哪份组合在绘制。
     */
    fun snackbar(
        host: SnackbarHostState,
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = true,
        duration: SnackbarDuration? = null,
        onResult: ((SnackbarResult) -> Unit)? = null,
        dismissNewest: Boolean = false,
    ) = showOnHost(host, message, actionLabel, withDismissAction, duration, onResult, dismissNewest)

    fun snackbar(
        @StringRes messageResId: Int,
        @StringRes actionLabelResId: Int? = null,
        withDismissAction: Boolean = true,
        duration: SnackbarDuration? = null,
        onResult: ((SnackbarResult) -> Unit)? = null,
        dismissNewest: Boolean = false,
    ) = snackbar(
        message = stringResource(messageResId),
        actionLabel = actionLabelResId?.let(::stringResource),
        withDismissAction = withDismissAction,
        duration = duration,
        onResult = onResult,
        dismissNewest = dismissNewest,
    )

    fun snackbar(
        host: SnackbarHostState,
        @StringRes messageResId: Int,
        @StringRes actionLabelResId: Int? = null,
        withDismissAction: Boolean = true,
        duration: SnackbarDuration? = null,
        onResult: ((SnackbarResult) -> Unit)? = null,
        dismissNewest: Boolean = false,
    ) = snackbar(
        host = host,
        message = stringResource(messageResId),
        actionLabel = actionLabelResId?.let(::stringResource),
        withDismissAction = withDismissAction,
        duration = duration,
        onResult = onResult,
        dismissNewest = dismissNewest,
    )

    fun snackbar(
        @StringRes messageResId: Int,
        vararg args: Any,
        @StringRes actionLabelResId: Int? = null,
        withDismissAction: Boolean = true,
        duration: SnackbarDuration? = null,
        onResult: ((SnackbarResult) -> Unit)? = null,
        dismissNewest: Boolean = false,
    ) = snackbar(
        message = stringResource(messageResId, *args),
        actionLabel = actionLabelResId?.let(::stringResource),
        withDismissAction = withDismissAction,
        duration = duration,
        onResult = onResult,
        dismissNewest = dismissNewest,
    )

    fun snackbar(
        host: SnackbarHostState,
        @StringRes messageResId: Int,
        vararg args: Any,
        @StringRes actionLabelResId: Int? = null,
        withDismissAction: Boolean = true,
        duration: SnackbarDuration? = null,
        onResult: ((SnackbarResult) -> Unit)? = null,
        dismissNewest: Boolean = false,
    ) = snackbar(
        host = host,
        message = stringResource(messageResId, *args),
        actionLabel = actionLabelResId?.let(::stringResource),
        withDismissAction = withDismissAction,
        duration = duration,
        onResult = onResult,
        dismissNewest = dismissNewest,
    )

    private fun showOnHost(
        host: SnackbarHostState,
        message: String,
        actionLabel: String?,
        withDismissAction: Boolean,
        duration: SnackbarDuration?,
        onResult: ((SnackbarResult) -> Unit)?,
        dismissNewest: Boolean,
    ) {
        snackbarScope.launch {
            // 宿主已随页面退场注销：此时 showSnackbar 会一直等一个不会出现的消费方，
            // 回调与动作都拿不到，所以先确认宿主仍在使用
            if (!isHostRegistered(host)) {
                onResult?.invoke(SnackbarResult.Dismissed)
                return@launch
            }
            if (dismissNewest) host.newestSnackbarData()?.dismiss()
            val result = host.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = withDismissAction,
                duration = duration ?: snackbarDuration,
            )
            onResult?.invoke(result)
        }
    }

    // application context 不经过 Activity 的 base context,
    // 这里按当前应用内语言包装, 使 snackbar 等全局文案跟随应用内语言
    fun stringResource(@StringRes resId: Int) = AppLocale.localizedContext(appContext).getString(resId)
    fun stringResource(@StringRes resId: Int, vararg args: Any) =
        AppLocale.localizedContext(appContext).getString(resId, *args)
}
