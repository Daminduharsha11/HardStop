package com.example.detox.service

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.detox.data.DetoxPreferences
import com.example.detox.engine.ShizukuPackageEngine
import java.util.Calendar

class DetoxTimerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var enforcementHandler = Handler(Looper.getMainLooper())
    private var enforcementRunnable: Runnable? = null

    private var lockedPackages: Set<String> = emptySet()
    private lateinit var prefs: DetoxPreferences
    private lateinit var shizukuEngine: ShizukuPackageEngine

    companion object {
        const val CHANNEL_ID = "AegisDetoxTimerChannel"
        const val NOTIFICATION_ID = 888

        const val ACTION_START = "ACTION_START"
        const val ACTION_EVALUATE_RULES = "ACTION_EVALUATE_RULES"
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

        fun evaluateRules(context: Context) {
            val intent = Intent(context, DetoxTimerService::class.java).apply {
                action = ACTION_EVALUATE_RULES
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
        shizukuEngine = ShizukuPackageEngine(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        val notification = buildNotification("Aegis Detox Active")
        startForegroundServiceInternal(notification)

        when (action) {
            ACTION_START -> {
                val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
                val pkgsList = intent.getStringArrayListExtra(EXTRA_PACKAGES)

                lockedPackages = if (!pkgsList.isNullOrEmpty()) {
                    pkgsList.toSet()
                } else {
                    prefs.getLockedPackages()
                }

                val remainingMs = if (durationMs > 0) durationMs else (prefs.getDetoxEndTime() - System.currentTimeMillis())

                if (remainingMs > 0) {
                    suspendAllPackages()
                    startCountdown(remainingMs)
                } else {
                    unlockAllPackages()
                    stopSelf()
                }
            }
            ACTION_EVALUATE_RULES -> {
                startBackgroundRuleMonitoring()
            }
            else -> {
                if (prefs.isDetoxActive()) {
                    lockedPackages = prefs.getLockedPackages()
                    startCountdown(prefs.getDetoxEndTime() - System.currentTimeMillis())
                } else {
                    startBackgroundRuleMonitoring()
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundServiceInternal(notification: Notification) {
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
    }

    private fun startCountdown(durationMs: Long) {
        countDownTimer?.cancel()
        isRunning = true

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

    private fun startBackgroundRuleMonitoring() {
        enforcementRunnable?.let { enforcementHandler.removeCallbacks(it) }

        enforcementRunnable = object : Runnable {
            override fun run() {
                checkNightAndHourlyRules()
                enforcementHandler.postDelayed(this, 30000) // Check every 30 seconds
            }
        }
        enforcementHandler.post(enforcementRunnable!!)
    }

    private fun checkNightAndHourlyRules() {
        val nightActive = prefs.isNightBlockActiveNow()
        val nightApps = prefs.getNightApps()
        val hourlyApps = prefs.getHourlyApps()

        // 1. Night Block Evaluation
        if (nightActive && nightApps.isNotEmpty()) {
            shizukuEngine.suspendPackages(nightApps)
        } else if (!nightActive && nightApps.isNotEmpty() && !prefs.isDetoxActive()) {
            shizukuEngine.unsuspendPackages(nightApps)
        }

        // 2. Hourly Allowance Evaluation
        if (hourlyApps.isNotEmpty()) {
            val usageManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            val windowMins = prefs.getUsageWindowMins()
            val allowanceMins = prefs.getAllowanceMins()

            calendar.add(Calendar.MINUTE, -windowMins)
            val startTime = calendar.timeInMillis

            val stats = usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (!stats.isNullOrEmpty()) {
                val appUsageMap = stats.associate { it.packageName to it.totalTimeInForeground }
                val exceededApps = mutableSetOf<String>()
                val allowedApps = mutableSetOf<String>()

                hourlyApps.forEach { pkg ->
                    val usageMs = appUsageMap[pkg] ?: 0L
                    val usageMins = usageMs / (1000 * 60)

                    if (usageMins >= allowanceMins) {
                        exceededApps.add(pkg)
                    } else {
                        allowedApps.add(pkg)
                    }
                }

                if (exceededApps.isNotEmpty()) {
                    shizukuEngine.suspendPackages(exceededApps)
                }
                if (allowedApps.isNotEmpty() && !nightActive && !prefs.isDetoxActive()) {
                    shizukuEngine.unsuspendPackages(allowedApps)
                }
            }
        }
    }

    private fun suspendAllPackages() {
        if (lockedPackages.isNotEmpty()) {
            shizukuEngine.suspendPackages(lockedPackages)
        }
    }

    private fun unlockAllPackages() {
        if (lockedPackages.isNotEmpty()) {
            shizukuEngine.unsuspendPackages(lockedPackages)
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
        enforcementRunnable?.let { enforcementHandler.removeCallbacks(it) }
        isRunning = false
        super.onDestroy()
    }
}