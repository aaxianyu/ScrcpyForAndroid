package io.github.miuzarte.scrcpyforandroid.password

import android.view.KeyEvent
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object InjectionController {
    /**
     * 文本注入完毕后、补发回车之前留出的缓冲。
     *
     * 控制通道本身保证 injectText 先于 injectKeycode 送达服务端, 但字符事件经
     * InputManager 投递到系统输入队列后, 由输入框/输入法异步消费。若立刻追加回车,
     * 存在"回车先于文本落定"的竞态(锁屏 PIN 走输入法处理时尤其明显)。
     * 留一小段缓冲让文本先被消费。经验值: 50ms 在部分设备/输入法上不够整串落定,
     * 出现「回车先于文本」「字符没进完回车已到」两种竞态, 提到 300ms 后稳定。
     */
    private const val ENTER_DELAY_MS = 300L

    suspend fun inject(password: CharArray, autoEnter: Boolean = false) {
        try {
            withContext(Dispatchers.IO) {
                val scrcpy = AppRuntime.scrcpy ?: return@withContext
                // String 一旦生成便无法擦除(不可变对象), 因此只让可擦除的明文数组存活到
                // 这一刻: 生成字符串后立刻清零, 使后续 delay 与补发回车期间不再持有明文副本。
                val text = String(password)
                password.fill('\u0000')
                scrcpy.injectText(text)
                if (!autoEnter) return@withContext

                delay(ENTER_DELAY_MS)
                scrcpy.injectKeycode(
                    action = KeyEvent.ACTION_DOWN,
                    keycode = KeyEvent.KEYCODE_ENTER,
                )
                scrcpy.injectKeycode(
                    action = KeyEvent.ACTION_UP,
                    keycode = KeyEvent.KEYCODE_ENTER,
                )
            }
        } finally {
            password.fill('\u0000')
        }
    }
}
