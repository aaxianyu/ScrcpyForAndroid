package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * 授权后补读的轮数与间隔。
 *
 * 系统把「读取应用列表」权限写下去，与枚举真能读到全量列表之间有一段时差：读一次
 * 常常还是只能枚举到自身，所以按次数补齐，读到列表就停。
 */
internal const val LOCAL_PERMISSION_RETRY_ATTEMPTS = 5
internal const val LOCAL_PERMISSION_RETRY_DELAY_MS = 800L

/** 本应用详情页：读取应用列表权限的授予入口。 */
internal fun localReadPermissionSettingsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
