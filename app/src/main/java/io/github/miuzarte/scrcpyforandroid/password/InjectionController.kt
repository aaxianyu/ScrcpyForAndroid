package io.github.miuzarte.scrcpyforandroid.password

import android.view.KeyEvent
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object InjectionController {
    suspend fun inject(password: CharArray, autoEnter: Boolean = false) {
        try {
            withContext(Dispatchers.IO) {
                AppRuntime.scrcpy?.injectText(String(password))
                if (autoEnter) {
                    delay(50)
                    AppRuntime.scrcpy?.injectKeycode(action = 0, keycode = KeyEvent.KEYCODE_ENTER)
                    AppRuntime.scrcpy?.injectKeycode(action = 1, keycode = KeyEvent.KEYCODE_ENTER)
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }
}
