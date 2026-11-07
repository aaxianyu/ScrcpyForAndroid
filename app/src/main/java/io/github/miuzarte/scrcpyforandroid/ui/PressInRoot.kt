// Copyright 2026, ScrcpyForAndroid contributors
// SPDX-License-Identifier: Apache-2.0

package io.github.miuzarte.scrcpyforandroid.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * 上报手指落点的 root 坐标（搜索浮层圆形揭示的圆心 = pressInRoot）。
 *
 * 放在被按组件的外层 modifier 上：内部手势（按压反馈 / 点击）先收到事件，本节点只读取
 * down 不消费，因此不影响原有交互。
 */
@Composable
fun rememberPressInRootModifier(onPress: (Offset) -> Unit): Modifier {
    val rootPosition = remember { mutableStateOf(Offset.Zero) }
    val currentOnPress by rememberUpdatedState(onPress)
    return Modifier
        .onGloballyPositioned { coordinates ->
            rootPosition.value = coordinates.positionInRoot()
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                currentOnPress(rootPosition.value + down.position)
            }
        }
}
