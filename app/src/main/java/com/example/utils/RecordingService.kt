package com.example.utils

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
import androidx.core.app.NotificationCompat
import com.example.BinotApplication
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * This service does NOT own its own MediaRecorder. [AudioRecorderManager] is already an
 * app-wide singleton (see AppContainer), so recording itself keeps running exactly as
 * before regardless of whether this service exists. This service's only two jobs are:
 *  1) Hold an active foreground service of type "microphone" while recording is in
 *     progress, which is what lets Android keep giving the app mic access once the
 *     screen turns off or the app is no longer in the foreground.
 *  2) Show a persistent notification with the live elapsed recording time, so people
 *     know Binot is still recording for them in the background.
 *
 * It's started when a recording begins (only if the "Record in background" toggle is
 * on) and stops itself automatically once AudioRecorderManager reports recording has
 * stopped, so it never lingers.
 */
class RecordingService : Service() {

    companion object {
        private const val CHANNEL_ID = "binot_recording_channel"
        private const val NOTIFICATION_ID = 4821
        const val ACTION_START = "com.example.action.START_BACKGROUND_RECORDING"
        const val ACTION_STOP = "com.example.action.STOP_BACKGROUND_RECORDING"

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    private var watcherJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var elapsedSeconds = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfCleanly()
                return START_NOT_STICKY
            }
            else -> beginWatching()
        }
        return START_NOT_STICKY
    }

    private fun beginWatching() {
        if (watcherJob != null) return // already running

        elapsedSeconds = 0
        startForeground(NOTIFICATION_ID, buildNotification(elapsedSeconds), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)

        val audioRecorderManager = (applicationContext as BinotApplication).container.audioRecorderManager

        watcherJob = serviceScope.launch {
            while (true) {
                delay(1000)
                if (!audioRecorderManager.isRecording.value) {
                    // Recording was stopped from the app itself (or paused indefinitely) — clean up.
                    stopSelfCleanly()
                    break
                }
                elapsedSeconds += 1
                updateNotification(elapsedSeconds)
            }
        }
    }

    private fun stopSelfCleanly() {
        watcherJob?.cancel()
        watcherJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows recording progress while Binot records with the screen off or the app in the background."
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(seconds: Int): Notification {
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Recording in background")
            .setContentText("Binot is still recording — ${formatTime(seconds)} elapsed. Tap to return.")
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(seconds: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(seconds))
    }

    private fun formatTime(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

    override fun onDestroy() {
        super.onDestroy()
        watcherJob?.cancel()
        watcherJob = null
    }
}
