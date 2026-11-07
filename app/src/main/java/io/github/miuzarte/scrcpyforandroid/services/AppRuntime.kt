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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    var snackbarHostState: SnackbarHostState?
        get() = synchronized(snackbarHostStateLock) {
            snackbarHostStateStack.lastOrNull()
        }
        set(value) {
            synchronized(snackbarHostStateLock) {
                snackbarHostStateStack.clear()
                if (value != null) snackbarHostStateStack.add(value)
            }
        }

    private val snackbarScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun registerSnackbarHostState(hostState: SnackbarHostState): () -> Unit {
        synchronized(snackbarHostStateLock) {
            snackbarHostStateStack.add(hostState)
        }
        return {
            synchronized(snackbarHostStateLock) {
                snackbarHostStateStack.remove(hostState)
            }
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
    ) = snackbarHostState?.let {
        snackbarScope.launch {
            if (dismissNewest) snackbarDismissNewest()
            it.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = withDismissAction,
                duration = duration ?: snackbarDuration,
            ).let { result -> onResult?.invoke(result) }
        }
    }

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

    // application context 不经过 Activity 的 base context,
    // 这里按当前应用内语言包装, 使 snackbar 等全局文案跟随应用内语言
    fun stringResource(@StringRes resId: Int) = AppLocale.localizedContext(appContext).getString(resId)
    fun stringResource(@StringRes resId: Int, vararg args: Any) =
        AppLocale.localizedContext(appContext).getString(resId, *args)
}
