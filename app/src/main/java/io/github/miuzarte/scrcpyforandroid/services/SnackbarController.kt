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
    /** 绑到另一份宿主：页面想把自己的 SnackbarHost 作为落点时用它，其余默认值保持不变。 */
    fun withHost(host: SnackbarHostState): SnackbarController = SnackbarController(
        scope = scope,
        hostState = host,
        defaultActionLabel = defaultActionLabel,
        defaultWithDismissAction = defaultWithDismissAction,
        defaultDuration = defaultDuration,
    )

    fun show(
        message: String,
        actionLabel: String? = defaultActionLabel,
        withDismissAction: Boolean = defaultWithDismissAction ?: true,
        duration: SnackbarDuration? = defaultDuration,
        scope: CoroutineScope = this.scope,
        onAction: (() -> Unit)? = null,
    ) {
        scope.launch {
            // 发给控制器绑定的宿主：页面各自渲染自己的 SnackbarHost，消息留在本页；
            // 二级页通过 withHost 绑定自己的宿主，不依赖全局宿主栈猜页面
            val host = hostState
            // 页面销毁后宿主已注销：此时 showSnackbar 会一直挂起到页面重建，直接丢弃这条
            if (!AppRuntime.isHostRegistered(host)) return@launch
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
