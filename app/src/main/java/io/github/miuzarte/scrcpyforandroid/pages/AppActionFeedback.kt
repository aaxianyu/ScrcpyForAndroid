package io.github.miuzarte.scrcpyforandroid.pages

import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.data.AppManagerError
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.AppAction
import io.github.miuzarte.scrcpyforandroid.appmanager.domain.DeviceApp
import io.github.miuzarte.scrcpyforandroid.appmanager.ui.appManagerErrorText
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime

/**
 * 单包动作的成功文案。
 *
 * 回调可能运行在组合之外（协程回调），统一走 [AppRuntime.stringResource]
 * 按应用内语言取文案，不依赖组合上下文。
 */
internal fun appActionSuccessText(action: AppAction, app: DeviceApp): String = when (action) {
    AppAction.OPEN -> AppRuntime.stringResource(R.string.app_manager_opening, app.displayLabel)
    AppAction.FORCE_STOP -> AppRuntime.stringResource(R.string.app_manager_stopped, app.displayLabel)
    AppAction.CLEAR_DATA ->
        AppRuntime.stringResource(R.string.app_manager_data_cleared, app.displayLabel)

    AppAction.DISABLE -> AppRuntime.stringResource(R.string.app_manager_disabled_done, app.displayLabel)
    AppAction.ENABLE -> AppRuntime.stringResource(R.string.app_manager_enabled_done, app.displayLabel)
    AppAction.UNINSTALL -> AppRuntime.stringResource(R.string.app_manager_uninstalled, app.displayLabel)
    AppAction.EXPORT_APK -> AppRuntime.stringResource(R.string.app_manager_exported, app.displayLabel)
}

/** 单包动作的失败文案，按动作分派。 */
internal fun appActionFailureText(action: AppAction, app: DeviceApp, error: AppManagerError): String =
    when (action) {
        AppAction.OPEN -> AppRuntime.stringResource(
            R.string.app_manager_open_failed,
            app.displayLabel,
            appManagerErrorText(error),
        )

        AppAction.EXPORT_APK ->
            AppRuntime.stringResource(R.string.app_manager_export_failed, appManagerErrorText(error))

        AppAction.UNINSTALL ->
            AppRuntime.stringResource(R.string.app_manager_uninstall_failed, appManagerErrorText(error))

        AppAction.DISABLE ->
            AppRuntime.stringResource(R.string.app_manager_disable_failed, appManagerErrorText(error))

        AppAction.ENABLE ->
            AppRuntime.stringResource(R.string.app_manager_enable_failed, appManagerErrorText(error))

        AppAction.FORCE_STOP ->
            AppRuntime.stringResource(R.string.app_manager_force_stop_failed, appManagerErrorText(error))

        AppAction.CLEAR_DATA ->
            AppRuntime.stringResource(R.string.app_manager_clear_data_failed, appManagerErrorText(error))
    }
