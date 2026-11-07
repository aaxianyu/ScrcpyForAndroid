package io.github.miuzarte.scrcpyforandroid.services

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

class SnackbarController(
    private val scope: CoroutineScope,
    val hostState: SnackbarHostState,
    val defaultActionLabel: String? = null,
    val defaultWithDismissAction: Boolean? = null,
    val defaultDuration: SnackbarDuration? = null,
) {
    fun show(
        message: String,
        actionLabel: String? = defaultActionLabel,
        withDismissAction: Boolean = defaultWithDismissAction ?: true,
        duration: SnackbarDuration? = defaultDuration,
        scope: CoroutineScope = this.scope,
        onAction: (() -> Unit)? = null,
    ) {
        scope.launch {
            // 总是发给当前可见页面的宿主：否则在二级页（如应用管理）里 show 的消息
            // 会落在主页 Scaffold 的宿主上，回到主页才补现
            val host = AppRuntime.snackbarHostState ?: hostState
            val result = host.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = withDismissAction,
                duration = duration ?: AppRuntime.snackbarDuration,
            )
            // 只有用户真按了按钮才算：到期自动消失（Dismissed）不该触发回调
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalSnackbarController = staticCompositionLocalOf<SnackbarController> {
    error("No SnackbarController provided")
}
