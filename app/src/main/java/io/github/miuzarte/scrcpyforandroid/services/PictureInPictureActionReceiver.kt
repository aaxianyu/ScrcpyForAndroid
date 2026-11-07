package io.github.miuzarte.scrcpyforandroid.services

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.R
import kotlinx.coroutines.runBlocking

// not working in MIUI
class PictureInPictureActionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STOP_SCRCPY) return
        val pendingResult = goAsync()
        Thread {
            try {
                val appContext = context.applicationContext
                AppRuntime.init(appContext)
                runBlocking {
                    // PiP 的停止按钮也是手动停止, 设备显示要在连接还在时一并还原
                    AppRuntime.scrcpy?.stop()
                    runCatching { DisplayOverride.restore() }.onFailure { error ->
                        EventLogger.logEvent(
                            R.string.vm_display_restore_failed,
                            error.message ?: error.javaClass.simpleName,
                            level = Log.WARN,
                            error = error,
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_STOP_SCRCPY =
            "${BuildConfig.APPLICATION_ID}.action.STOP_SCRCPY_FROM_PIP"
        private const val REQUEST_CODE_STOP_SCRCPY = 1

        fun createIntentFilter(): IntentFilter = IntentFilter(ACTION_STOP_SCRCPY)

        fun createPendingIntent(context: Context): PendingIntent {
            val intent = Intent(ACTION_STOP_SCRCPY).setPackage(context.packageName)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_STOP_SCRCPY,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
