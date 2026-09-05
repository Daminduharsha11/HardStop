package com.example.detox.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.detox.engine.ShizukuPackageEngine

class DetoxTimerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var lockedPackages: ArrayList<String> = arrayListOf()

    companion object {
        const val CHANNEL_ID = "AegisDetoxTimerChannel"
        const val NOTIFICATION_ID = 888
        const val ACTION_START = "ACTION_START"
        const val EXTRA_PACKAGES = "EXTRA_PACKAGES"
        const val EXTRA_DURATION_MINUTES = "EXTRA_DURATION_MINUTES"

        var remainingSeconds = 0L
            private set
        var isRunning = false
            private set

        fun startService(context: Context, packages: ArrayList<String>, minutes: Int) {
            val intent = Intent(context, DetoxTimerService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_PACKAGES, packages)
                putExtra(EXTRA_DURATION_MINUTES, minutes)
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
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val pkgs = intent.getStringArrayListExtra(EXTRA_PACKAGES) ?: arrayListOf()
            val minutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 15)
            lockedPackages = pkgs

            val durationMs = minutes * 60 * 1000L
            startCountdown(durationMs)
        }
        return START_STICKY
    }

    private fun startCountdown(durationMs: Long) {
        countDownTimer?.cancel()
        isRunning = true

        startForeground(NOTIFICATION_ID, buildNotification("Lock session starting..."))

        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = millisUntilFinished / 1000
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                val timeStr = String.format("%02d:%02d", mins, secs)
                
                updateNotification("Focus Mode Active: $timeStr remaining")
            }

            override fun onFinish() {
                unlockAllPackages()
                isRunning = false
                remainingSeconds = 0
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
    }

    private fun unlockAllPackages() {
        lockedPackages.forEach { pkg ->
            ShizukuPackageEngine.setPackageSuspended(pkg, false)
        }
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
