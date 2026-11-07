package io.github.miuzarte.scrcpyforandroid.util

import android.util.Log
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PanelUtils"

/**
 * 展开通知栏 / 快捷设置面板（预览卡场景，没有 scrcpy 会话时走这条路）。
 *
 * 这里刻意只用 statusbar 命令、不模拟下拉手势：模拟触摸会被前台应用当成真实操作，
 * 可能误触发应用内的滑动手势，副作用不可控。
 */
suspend fun expandSettingsPanel() = withContext(Dispatchers.IO) {
    runCatching { NativeAdbService.shell("cmd statusbar expand-settings") }
        .onFailure { Log.w(TAG, "statusbar command failed", it) }
}
