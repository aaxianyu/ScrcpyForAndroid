package io.github.miuzarte.scrcpyforandroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBarState

/**
 * 页面级顶栏滚动行为：状态随页面组合创建、随页面组合销毁。
 *
 * - 每页一份实例，页面之间不互相携带收起偏移：从可滚动页退出再进入，标题按展开态起算；
 * - 状态不进存档（区别于 miuix 的 `rememberTopAppBarState`），页面重建时不带回收起值，
 *   首帧即展开，不会先呈现收起态再过渡回展开态；
 * - 收起 / 展开仍由该页列表挂上的 `nestedScrollConnection` 驱动，与页面同生共死。
 *
 * `heightOffsetLimit` 用 miuix 的同一哨兵值，真正的收起距离由 TopAppBar 在布局时写入。
 */
@Composable
fun rememberPageScrollBehavior(): ScrollBehavior = MiuixScrollBehavior(
    state = remember { TopAppBarState(-Float.MAX_VALUE, 0f, 0f) },
)
