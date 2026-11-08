package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 松手收尾的会话准入。
 *
 * 收尾协程要等最后一次列表跳转落位才动 [FastScroller] 的拖动态，是异步的；这段窗口里
 * 用户完全可能再按一次滑块开新拖动。守的是「旧收尾不得改新拖动的状态」这一条不变式。
 */
class DragSettleAdmissionTest {

    @Test
    fun `the settle launched by the current drag is admitted`() {
        assertTrue(mayFinishDrag(currentSession = 7, ownSession = 7))
    }

    @Test
    fun `a settle left over from an earlier drag is rejected`() {
        // 拖动 A 松手后在 join 里等着，拖动 B 已经按下：会话号前进，A 的收尾必须自行放弃
        assertFalse(mayFinishDrag(currentSession = 8, ownSession = 7))
    }

    @Test
    fun `re-pressing the thumb mid-jump rejects the pending settle`() {
        var session = 0
        // A：按下 → 松手，收尾协程带着 1 号会话号去 join 跨多节的大跳转
        session++
        val pendingSettleSession = session
        // B：手指还按着滑块就又按下，会话号前进
        session++
        assertFalse(mayFinishDrag(currentSession = session, ownSession = pendingSettleSession))
        // B 自己松手时，它的收尾会话号与当前一致，照常收尾
        assertTrue(mayFinishDrag(currentSession = session, ownSession = session))
    }
}
