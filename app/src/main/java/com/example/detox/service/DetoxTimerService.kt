package com.example.detox.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.detox.data.DetoxPreferences
import com.example.detox.engine.ShizukuPackageEngine

class DetoxTimerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var lockedPackages: Set<String> = emptySet()
    private lateinit var prefs: DetoxPreferences

    companion object {
        const val CHANNEL_ID = "AegisDetoxTimerChannel"
        const val NOTIFICATION_ID = 888
        const val ACTION_START = "ACTION_START"
        const val EXTRA_PACKAGES = "EXTRA_PACKAGES"
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"

        var remainingSeconds = 0L
            private set
        var isRunning = false
            private set

        fun startService(context: Context, packages: ArrayList<String>, durationMs: Long) {
            val intent = Intent(context, DetoxTimerService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_PACKAGES, packages)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = DetoxPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, 0L) ?: 0L
        val pkgsList = intent?.getStringArrayListExtra(EXTRA_PACKAGES)

        // Sync with preferences
        if (pkgsList != null && pkgsList.isNotEmpty()) {
            lockedPackages = pkgsList.toSet()
        } else {
            lockedPackages = prefs.getLockedPackages()
        }

        val remainingMs = if (durationMs > 0) durationMs else (prefs.getDetoxEndTime() - System.currentTimeMillis())

        if (remainingMs > 0) {
            startCountdown(remainingMs)
        } else {
            unlockAllPackages()
            stopSelf()
        }

        return START_STICKY
    }

    private fun startCountdown(durationMs: Long) {
        countDownTimer?.cancel()
        isRunning = true

        val notification = buildNotification("Lock session starting...")

        // Android 14+ Foreground Service Start Compliance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = millisUntilFinished / 1000
                val hours = remainingSeconds / 3600
                val mins = (remainingSeconds % 3600) / 60
                val secs = remainingSeconds % 60

                val timeStr = if (hours > 0) {
                    String.format("%02d:%02d:%02d", hours, mins, secs)
                } else {
                    String.format("%02d:%02d", mins, secs)
                }

                updateNotification("Focus Mode Active: $timeStr remaining")
            }

            override fun onFinish() {
                unlockAllPackages()
                isRunning = false
                remainingSeconds = 0
                prefs.clearDetoxSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
    }

    private fun unlockAllPackages() {
        if (lockedPackages.isNotEmpty()) {
            ShizukuPackageEngine.unsuspendPackages(lockedPackages)
        }
        prefs.clearDetoxSession()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aegis Detox Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aegis Focus Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        countDownTimer?.cancel()
        isRunning = false
        super.onDestroy()
    }
}
