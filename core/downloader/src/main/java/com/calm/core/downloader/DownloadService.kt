package com.calm.core.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 前台服务 + 常驻通知（对应设计方案 DL-009）。 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var controller: DownloadController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("准备下载…", 0, 0, 0))
        scope.launch {
            controller.progress.collectLatest { map ->
                val active = map.values
                val speed = active.sumOf { it.speed }
                val first = active.firstOrNull()
                val text = if (active.isEmpty()) "下载服务运行中" else "${active.size} 个任务 · ${Fmt.speed(speed)}"
                val p = first
                updateNotification(text, p?.downloaded ?: 0, p?.total ?: 0, if (active.isEmpty()) 0 else 1)
            }
        }
        scope.launch {
            controller.events.collectLatest { msg ->
                if (msg != null) {
                    notifyDone(msg)
                    controller.consumeEvent()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "下载", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val done = NotificationChannel(CHANNEL_DONE, "下载完成", NotificationManager.IMPORTANCE_DEFAULT)
        getSystemService(NotificationManager::class.java).createNotificationChannel(done)
    }

    private fun buildNotification(text: String, downloaded: Long, total: Long, ongoing: Int): Notification {
        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Calm 下载")
            .setContentText(text)
            .setOngoing(ongoing > 0)
            .setOnlyAlertOnce(true)
        if (total > 0 && downloaded > 0) {
            builder.setProgress(100, (downloaded * 100 / total).toInt(), false)
        } else if (ongoing > 0) {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun updateNotification(text: String, downloaded: Long, total: Long, ongoing: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text, downloaded, total, ongoing))
    }

    private fun notifyDone(message: String) {
        getSystemService(NotificationManager::class.java).notify(
            (System.currentTimeMillis() and 0xFFFF).toInt(),
            androidx.core.app.NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Calm 下载")
                .setContentText(message)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL_ID = "calm_download"
        private const val CHANNEL_DONE = "calm_download_done"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }
    }
}

object Fmt {
    fun bytes(v: Long): String = when {
        v < 1024 -> "$v B"
        v < 1024 * 1024 -> "%.1f KB".format(v / 1024.0)
        v < 1024L * 1024 * 1024 -> "%.1f MB".format(v / 1048576.0)
        else -> "%.2f GB".format(v / 1073741824.0)
    }

    fun speed(bytesPerSec: Long): String = "${bytes(bytesPerSec)}/s"
}
