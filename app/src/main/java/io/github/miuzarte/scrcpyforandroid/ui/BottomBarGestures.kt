package io.github.miuzarte.scrcpyforandroid.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 底栏区域吞掉滑动手势。
 *
 * NavDisplay 的滑动返回覆盖整页，落在底栏上的横滑会被识别成返回手势、直接退出当前页面。
 * 底栏里的子组件（胶囊拖拽、圆钮按压、动作项）在 Main 遍先于本节点收到事件，按下与之后
 * 每一帧的移动都在这里标记为已消费，导航层便认定手势已被处理；子组件里的点击与长按照常生效。
 */
internal fun Modifier.consumeBottomBarSwipes(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
                if (change.pressed) change.consume()
            }
            if (event.changes.none { it.pressed }) break
        }
    }
}
