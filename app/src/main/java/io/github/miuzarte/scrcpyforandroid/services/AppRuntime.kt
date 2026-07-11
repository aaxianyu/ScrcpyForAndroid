package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.annotation.StringRes
import io.github.miuzarte.scrcpyforandroid.MainActivity
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbMdnsDiscoverer
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import java.util.Locale

// 用于不同 activity 之间传递实例
object AppRuntime {
    private lateinit var appContext: Context

    fun init(context: Context) {
        val baseContext = context.applicationContext
        refreshAppContext(baseContext)
        AdbMdnsDiscoverer.init(appContext)
    }

    /**
     * 根据当前语言设置刷新 appContext。
     * 语言切换时调用，使全局 snackbar 等使用新语言的字符串。
     */
    fun refreshAppContext(baseContext: Context) {
        val languageTag = MainActivity.getAppLanguageTag(baseContext)
        appContext = if (languageTag.isNotEmpty()) {
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(Locale.forLanguageTag(languageTag))
            baseContext.createConfigurationContext(config)
        } else {
            baseContext
        }
    }

    val context: Context
        get() = appContext

    var scrcpy: Scrcpy? = null
    var currentConnectionTarget: ConnectionTarget? = null
    var currentConnectedDevice: ConnectedDeviceInfo? = null

    // 当前设备使用的 profile ID（session 级，脱离快捷设备独立运作）
    val currentConnectionProfileId = kotlinx.coroutines.flow.MutableStateFlow("global")

    // 标记是否修改了受控端分辨率，停止投屏/断开时需要还原
    @Volatile
    var resolutionModified: Boolean = false

    /**
     * 还原受控端设备分辨率（如果之前修改过）
     * 用于：停止投屏、断开连接时调用
     */
    fun restoreRemoteResolutionIfNeeded() {
        if (!resolutionModified) return
        resolutionModified = false
        snackbarScope.launch(Dispatchers.IO) {
            runCatching {
                Log.d("AppRuntime", "Restoring remote device resolution to default")
                NativeAdbService.shell("wm size reset")
                snackbar(R.string.vm_resolution_restored)
            }.onFailure { e ->
                Log.w("AppRuntime", "Failed to restore remote device resolution", e)
            }
        }
    }

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

    private fun getSnackbarDuration(): SnackbarDuration {
        val durationMs = Storage.appSettings.bundleState.value.snackbarDurationMs
        return SnackbarDuration.Custom(durationMs.toLong())
    }

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
                duration = duration ?: getSnackbarDuration(),
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

    fun stringResource(@StringRes resId: Int) = appContext.getString(resId)
    fun stringResource(@StringRes resId: Int, vararg args: Any) = appContext.getString(resId, *args)
}
