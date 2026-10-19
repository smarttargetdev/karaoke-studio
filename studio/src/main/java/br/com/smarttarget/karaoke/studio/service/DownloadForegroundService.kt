package br.com.smarttarget.karaoke.studio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import br.com.smarttarget.karaoke.studio.MainActivity
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {

  private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
  private var wakeLock: PowerManager.WakeLock? = null
  private lateinit var notificationManager: NotificationManager
  private var previousActiveCount = 0

  override fun onCreate() {
    super.onCreate()
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    createNotificationChannels()

    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KaraokeStudio:DownloadWakeLock")
    wakeLock?.acquire(10 * 60 * 1000L)

    startForeground(NOTIFICATION_ID_ONGOING, buildOngoingNotification("Iniciando fila de downloads...", 0, 100))
    observeDownloads()
  }

  private fun observeDownloads() {
    val database = AppDatabase.getDatabase(this)
    serviceScope.launch {
      database.downloadDao().getActiveDownloads().collectLatest { activeList ->
        val activeCount = activeList.size

        if (activeCount > 0) {
          val first = activeList.first()
          val percent = (first.progress * 100).toInt()
          val statusText = when (first.status) {
            "DOWNLOADING" -> "Baixando: ${first.title} ($percent%)"
            "CONVERTING_MP3" -> "Convertendo para MP3: ${first.title}"
            "SAVING_METADATA" -> "Salvando metadados e tags..."
            else -> "Processando: ${first.title}"
          }

          val notif = buildOngoingNotification(statusText, percent, 100)
          notificationManager.notify(NOTIFICATION_ID_ONGOING, notif)
        } else if (previousActiveCount > 0 && activeCount == 0) {
          sendCompletionNotification("Downloads concluídos!", "Suas faixas e vídeos foram salvos com sucesso.")
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        }

        previousActiveCount = activeCount
      }
    }
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val ongoingChannel = NotificationChannel(
        CHANNEL_ID_ONGOING,
        "Downloads Ativos",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Progresso em tempo real de downloads em segundo plano"
      }

      val completeChannel = NotificationChannel(
        CHANNEL_ID_COMPLETE,
        "Downloads Concluídos",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "Notificação ao concluir downloads de vídeos e áudios"
      }

      notificationManager.createNotificationChannel(ongoingChannel)
      notificationManager.createNotificationChannel(completeChannel)
    }
  }

  private fun buildOngoingNotification(contentText: String, progress: Int, max: Int): android.app.Notification {
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this, 0, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID_ONGOING)
      .setSmallIcon(R.drawable.ic_download)
      .setContentTitle("Karaoke Studio - Download em 2º plano")
      .setContentText(contentText)
      .setProgress(max, progress, false)
      .setOngoing(true)
      .setContentIntent(pendingIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun sendCompletionNotification(title: String, message: String) {
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this, 1, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_ID_COMPLETE)
      .setSmallIcon(R.drawable.ic_check_circle)
      .setContentTitle(title)
      .setContentText(message)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()

    notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
      }
    } catch (_: Exception) {}
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val CHANNEL_ID_ONGOING = "karaoke_studio_ongoing_channel"
    const val CHANNEL_ID_COMPLETE = "karaoke_studio_complete_channel"
    const val NOTIFICATION_ID_ONGOING = 1001
    const val NOTIFICATION_ID_COMPLETE = 1002

    fun startService(context: Context) {
      val intent = Intent(context, DownloadForegroundService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stopService(context: Context) {
      val intent = Intent(context, DownloadForegroundService::class.java)
      context.stopService(intent)
    }
  }
}
