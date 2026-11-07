package io.github.miuzarte.scrcpyforandroid.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.miuzarte.scrcpyforandroid.MainActivity
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ADB 会话保活服务
 *
 * 开启后台保活后, ADB 连接建立时把进程提升为前台服务: 系统文件选择器、
 * 设置等界面盖住应用时, 定时探测与链路保持运行, 返回界面时连接仍然可用。
 */
class AdbKeepAliveService : Service() {

    private var lastLabel: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_DEVICE_LABEL).orEmpty()
            .ifBlank { lastLabel.orEmpty() }
            .ifBlank { AppRuntime.stringResource(R.string.adb_keepalive_connected) }
        lastLabel = label
        // 渠道名随应用语言变化, 每次启动都按当前语言重建 (同名渠道会更新显示名)
        ensureNotificationChannel()
        try {
            val notification = buildNotification(label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            active.value = true
        } catch (error: Exception) {
            Log.w(TAG, "startForeground failed", error)
            EventLogger.logEvent(
                R.string.adb_keepalive_start_failed,
                level = Log.WARN,
                error = error,
            )
            active.value = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        active.value = false
        super.onDestroy()
    }

    private fun buildNotification(label: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_adb)
            .setContentTitle(AppRuntime.stringResource(R.string.adb_keepalive_title))
            .setContentText(label)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent())
            .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            AppRuntime.stringResource(R.string.adb_keepalive_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AdbKeepAliveService"
        internal const val CHANNEL_ID = "adb_keep_alive"
        private const val NOTIFICATION_ID = 0x4ADB
        private const val EXTRA_DEVICE_LABEL = "device_label"

        private val active = MutableStateFlow(false)

        /**
         * 服务是否在运行: 保活中的进程不受缓存冻结影响, 探测可以继续
         */
        val isActive: StateFlow<Boolean> = active.asStateFlow()

        /**
         * 用户是否开启后台保活
         */
        fun isEnabled(): Boolean = appSettings.bundleState.value.keepAlive

        /**
         * 启动保活, 重复调用只会刷新通知内容
         */
        fun start(context: Context, deviceLabel: String) {
            val intent = Intent(context, AdbKeepAliveService::class.java)
                .putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: Exception) {
                Log.w(TAG, "startForegroundService failed", error)
                EventLogger.logEvent(
                    R.string.adb_keepalive_start_failed,
                    level = Log.WARN,
                    error = error,
                )
            }
        }

        /**
         * 通知权限授予后重新投递通知, 已在运行时才生效
         */
        fun refresh(context: Context) {
            if (!active.value) return
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AdbKeepAliveService::class.java),
                )
            } catch (error: Exception) {
                Log.w(TAG, "refresh failed", error)
            }
        }

        /**
         * 停止保活, 幂等
         */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AdbKeepAliveService::class.java))
            } catch (error: Exception) {
                Log.w(TAG, "stopService failed", error)
            }
        }
    }
}
