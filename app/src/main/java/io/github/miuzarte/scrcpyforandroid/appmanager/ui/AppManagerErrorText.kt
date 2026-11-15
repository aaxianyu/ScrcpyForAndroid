package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.appmanager.data.AppManagerError
import io.github.miuzarte.scrcpyforandroid.appmanager.data.DeviceAppRepository
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime

/**
 * 应用管理类操作失败时给用户的一句话。
 *
 * 回调可能运行在组合之外（协程回调），统一走 [AppRuntime.stringResource] 按应用内语言取文案，
 * 不依赖组合上下文。已识别的导出失败各有专句；其余设备原因把原始码（如 `INSTALL_FAILED_*`）
 * 附在「设备拒绝」后面——它是安装失败时唯一的诊断依据，为此接受中英混排。没有原始码时只留
 * 本地化结论。
 */
internal fun appManagerErrorText(error: AppManagerError): String = when (error) {
    is AppManagerError.ChannelOccupied ->
        AppRuntime.stringResource(R.string.app_manager_adb_channel_occupied)

    is AppManagerError.Device -> when (error.reason) {
        DeviceAppRepository.EXPORT_NOT_PUBLISHED ->
            AppRuntime.stringResource(R.string.app_manager_export_not_published)

        DeviceAppRepository.EXPORT_NO_TARGET ->
            AppRuntime.stringResource(R.string.app_manager_export_no_target)

        // 未识别的设备原因带上原始码：安装失败等场景里 INSTALL_FAILED_* 是唯一的诊断依据
        else -> if (error.reason.isBlank()) {
            AppRuntime.stringResource(R.string.app_manager_device_refused)
        } else {
            AppRuntime.stringResource(R.string.app_manager_device_refused_reason, error.reason)
        }
    }

    AppManagerError.NotConnected ->
        AppRuntime.stringResource(R.string.app_manager_not_connected)

    AppManagerError.Slow ->
        AppRuntime.stringResource(R.string.app_manager_load_timeout, DeviceAppRepository.LOAD_TIMEOUT_MS / 1000)
}
