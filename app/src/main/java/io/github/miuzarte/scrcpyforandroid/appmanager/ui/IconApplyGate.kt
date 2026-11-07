package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import androidx.compose.runtime.mutableStateMapOf

/**
 * 图标替换的「已应用版本」闸门。
 *
 * 图标是后台逐块补的，列表数据版本（`iconEpoch`）一变，正在看的那些行如果立刻换图，会整屏跳一下。
 * 这里把「数据版本」和「这一行已经应用到哪一版」分开：行还在组合里就不推进，等它被回收
 * （离开组合，即完全滚出视口连同预取范围）时再推进，下次重新进入才用新图标。
 */
internal object IconApplyGate {

    private val applied = mutableStateMapOf<String, Int>()

    fun appliedFor(packageName: String): Int = applied[packageName] ?: 0

    /** 行被回收时调用：把该包已应用版本推到当前数据版本。 */
    fun onRowGone(packageName: String, dataEpoch: Int) {
        if ((applied[packageName] ?: 0) < dataEpoch) applied[packageName] = dataEpoch
    }
}