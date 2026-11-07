package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.runtime.staticCompositionLocalOf

class RootNavigator(
    val push: (RootScreen) -> Unit,
    val pop: () -> Unit,
    /**
     * F7「返回设备页」：清掉导航栈中间层直到只剩根，并瞬时切到「设备」Tab。
     *
     * 与 [pop] 的区别：这条路径要求无转场卡顿（瞬时收敛、无平滑滚动），
     * 由 MainScreen 临时切换转场实现。
     */
    val backToDevicesTab: () -> Unit = {},
)

val LocalRootNavigator = staticCompositionLocalOf<RootNavigator> {
    error("No RootNavigator provided")
}
