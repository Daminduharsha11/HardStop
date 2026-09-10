package com.example.detox.service

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.detox.data.DetoxPreferences
import com.example.detox.engine.ShizukuPackageEngine
import java.util.Calendar

class DetoxTimerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    
    private var lockedPackages: Set<String> = emptySet()
    private lateinit var prefs: DetoxPreferences
    private lateinit var shizukuEngine: ShizukuPackageEngine

    companion object {
        private const val TAG = "DetoxTimerService"
        const val CHANNEL_ID = "AegisDetoxTimerChannel"
        const val NOTIFICATION_ID = 888

        const val ACTION_START = "ACTION_START"
        const val ACTION_EVALUATE_RULES = "ACTION_EVALUATE_RULES"
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val EXTRA_PACKAGES = "EXTRA_PACKAGES"
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"
        
        private const val REQUEST_CODE_EVALUATE = 1001
        private const val REQUEST_CODE_UNBLOCK = 1002

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

        fun startMonitoring(context: Context) {
            val intent = Intent(context, DetoxTimerService::class.java).apply {
                action = ACTION_START_MONITORING
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
            ACTION_START_MONITORING -> {
                // Starts background timer loop without forcing an immediate blocking evaluation
                Log.d(TAG, "Background rule monitoring started.")
                startBackgroundRuleMonitoring()
            }
            ACTION_EVALUATE_RULES -> {
                checkNightAndHourlyRules()
                startBackgroundRuleMonitoring()
            }
            else -> {
                if (prefs.isDetoxActive()) {
                    lockedPackages = prefs.getLockedPackages()
                    startCountdown(prefs.getDetoxEndTime() - System.currentTimeMillis())
                } else {
                    checkNightAndHourlyRules()
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
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DetoxTimerService::class.java).apply {
            action = ACTION_EVALUATE_RULES
        }
        
        val pendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_EVALUATE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMillis = 30 * 1000L
        val triggerTime = System.currentTimeMillis() + intervalMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule background rule monitoring alarm", e)
        }
    }

    private fun cancelBackgroundRuleMonitoring() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DetoxTimerService::class.java).apply {
            action = ACTION_EVALUATE_RULES
        }
        val pendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_EVALUATE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun scheduleNextUnblockAlarm() {
        val blockMap = prefs.getHourlyBlockUntilMap()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DetoxTimerService::class.java).apply {
            action = ACTION_EVALUATE_RULES
        }
        val pendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_UNBLOCK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (blockMap.isEmpty()) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val earliestExpiry = blockMap.values.minOrNull() ?: return
        val now = System.currentTimeMillis()

        if (earliestExpiry <= now) {
            alarmManager.cancel(pendingIntent)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    earliestExpiry,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    earliestExpiry,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled exact unblock alarm for earliest expiry at $earliestExpiry")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact unblock alarm", e)
        }
    }

    private fun cancelNextUnblockAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DetoxTimerService::class.java).apply {
            action = ACTION_EVALUATE_RULES
        }
        val pendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_UNBLOCK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun checkNightAndHourlyRules() {
        try {
            val nightActive = prefs.isNightBlockActiveNow()
            val nightApps = prefs.getNightApps()
            val hourlyApps = prefs.getHourlyApps()

            Log.d(TAG, "Evaluating rules -> Night Active: $nightActive | Night Apps: ${nightApps.size} | Hourly Apps: ${hourlyApps.size}")

            // 1. Night Block Evaluation
            if (nightActive && nightApps.isNotEmpty()) {
                shizukuEngine.suspendPackages(nightApps)
            } else if (!nightActive && nightApps.isNotEmpty() && !prefs.isDetoxActive()) {
                shizukuEngine.unsuspendPackages(nightApps)
            }

            // 2. Hourly Allowance Evaluation
            // Fixed-window model: once an app exceeds its allowance, we store an explicit
            // "blockedUntil" timestamp and release it purely based on that timestamp elapsing.
            // This does NOT depend on UsageStatsManager returning non-empty data, which is
            // what caused apps to stay suspended forever before (statsMap.isNullOrEmpty()
            // was silently skipping the unsuspend path).
            if (hourlyApps.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val windowMs = prefs.getUsageWindowMins() * 60_000L
                val allowanceMins = prefs.getAllowanceMins()
                val blockMap = prefs.getHourlyBlockUntilMap()

                // Step 1: release any app whose fixed reset window has actually elapsed.
                val toUnblock = mutableSetOf<String>()
                val stillBlocked = mutableSetOf<String>()
                hourlyApps.forEach { pkg ->
                    val until = blockMap[pkg]
                    when {
                        until == null -> {} // never blocked, falls through to usage check below
                        now >= until -> { toUnblock.add(pkg); blockMap.remove(pkg) }
                        else -> stillBlocked.add(pkg)
                    }
                }
                if (toUnblock.isNotEmpty() && !nightActive && !prefs.isDetoxActive()) {
                    Log.d(TAG, "Reset window elapsed, unsuspending: $toUnblock")
                    shizukuEngine.unsuspendPackages(toUnblock)
                }

                // Step 2: for apps NOT currently in a block window, check live usage.
                val toCheck = hourlyApps - stillBlocked
                if (toCheck.isNotEmpty()) {
                    val usageManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                    val statsMap = usageManager.queryAndAggregateUsageStats(now - windowMs, now)

                    val newlyExceeded = mutableSetOf<String>()
                    toCheck.forEach { pkg ->
                        val usageMs = statsMap?.get(pkg)?.totalTimeInForeground ?: 0L
                        val usageMins = usageMs / (1000 * 60)

                        Log.d(TAG, "App $pkg usage: ${usageMins}m / limit ${allowanceMins}m")

                        if (usageMins >= allowanceMins) {
                            newlyExceeded.add(pkg)
                            blockMap[pkg] = now + windowMs // fixed reset window starts NOW
                        }
                    }

                    if (newlyExceeded.isNotEmpty()) {
                        Log.d(TAG, "Limit hit, suspending: $newlyExceeded")
                        shizukuEngine.suspendPackages(newlyExceeded)
                    }
                }

                prefs.setHourlyBlockUntilMap(blockMap)
            }

            // Schedule/recalculate the exact unblock alarm for the soonest expiry
            scheduleNextUnblockAlarm()
        } catch (e: Exception) {
            Log.e(TAG, "Exception encountered while evaluating night/hourly rules", e)
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
        cancelBackgroundRuleMonitoring()
        cancelNextUnblockAlarm()
        isRunning = false
        super.onDestroy()
    }
}