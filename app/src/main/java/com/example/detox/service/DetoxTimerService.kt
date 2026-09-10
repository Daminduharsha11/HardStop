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

        private const val REQUEST_CODE_NIGHT = 1001
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
                Log.d(TAG, "Exact alarm rule monitoring initialized.")
                checkNightAndHourlyRules()
            }
            ACTION_EVALUATE_RULES -> {
                checkNightAndHourlyRules()
            }
            else -> {
                if (prefs.isDetoxActive()) {
                    lockedPackages = prefs.getLockedPackages()
                    startCountdown(prefs.getDetoxEndTime() - System.currentTimeMillis())
                } else {
                    checkNightAndHourlyRules()
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

    private fun scheduleNextNightAlarm() {
        val nightApps = prefs.getNightApps()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DetoxTimerService::class.java).apply {
            action = ACTION_EVALUATE_RULES
        }
        val pendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_NIGHT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (nightApps.isEmpty()) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val now = System.currentTimeMillis()
        val (startHour, startMin) = prefs.getNightStart()
        val (endHour, endMin) = prefs.getNightEnd()

        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (startCal.timeInMillis <= now) {
            startCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (endCal.timeInMillis <= now) {
            endCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val nextTransitionTime = minOf(startCal.timeInMillis, endCal.timeInMillis)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTransitionTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextTransitionTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled exact night transition alarm at $nextTransitionTime")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact night alarm", e)
        }
    }

    private fun cancelNextNightAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DetoxTimerService::class.java).apply {
            action = ACTION_EVALUATE_RULES
        }
        val pendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_NIGHT,
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

        val targetTime = if (earliestExpiry <= now) now + 1000L else earliestExpiry

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    targetTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled exact unblock alarm for earliest expiry at $targetTime")
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
            checkNightBlockRules()
            checkHourlyAllowanceRules()
        } catch (e: Exception) {
            Log.e(TAG, "Exception encountered while evaluating rules", e)
        }
    }

    private fun checkNightBlockRules() {
        val nightActive = prefs.isNightBlockActiveNow()
        val nightApps = prefs.getNightApps()

        Log.d(TAG, "Evaluating Night Rules -> Active: $nightActive | Apps: ${nightApps.size}")

        if (nightApps.isNotEmpty()) {
            if (nightActive) {
                shizukuEngine.suspendPackages(nightApps)
            } else if (!prefs.isDetoxActive()) {
                shizukuEngine.unsuspendPackages(nightApps)
            }
        }

        // Schedule the next exact transition boundary for night mode
        scheduleNextNightAlarm()
    }

    private fun checkHourlyAllowanceRules() {
        val hourlyApps = prefs.getHourlyApps()
        if (hourlyApps.isEmpty()) {
            cancelNextUnblockAlarm()
            return
        }

        val now = System.currentTimeMillis()
        val windowMs = prefs.getUsageWindowMins() * 60_000L
        val allowanceMins = prefs.getAllowanceMins()
        val blockMap = prefs.getHourlyBlockUntilMap()

        Log.d(TAG, "Evaluating Hourly Rules -> Apps: ${hourlyApps.size}")

        // Step 1: Release apps whose fixed reset window has elapsed
        val toUnblock = mutableSetOf<String>()
        val stillBlocked = mutableSetOf<String>()

        hourlyApps.forEach { pkg ->
            val until = blockMap[pkg]
            when {
                until == null -> {} 
                now >= until -> { toUnblock.add(pkg); blockMap.remove(pkg) }
                else -> stillBlocked.add(pkg)
            }
        }

        if (toUnblock.isNotEmpty() && !prefs.isDetoxActive()) {
            Log.d(TAG, "Reset window elapsed, unsuspending: $toUnblock")
            shizukuEngine.unsuspendPackages(toUnblock)
        }

        // Step 2: Check live usage for apps not currently blocked and NOT just unblocked
        val toCheck = (hourlyApps - stillBlocked) - toUnblock
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
                    blockMap[pkg] = now + windowMs 
                }
            }

            if (newlyExceeded.isNotEmpty()) {
                Log.d(TAG, "Limit hit, suspending: $newlyExceeded")
                shizukuEngine.suspendPackages(newlyExceeded)
            }
        }

        prefs.setHourlyBlockUntilMap(blockMap)

        // Schedule/recalculate exact alarm for the next upcoming unblock
        scheduleNextUnblockAlarm()
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
        cancelNextNightAlarm()
        cancelNextUnblockAlarm()
        isRunning = false
        super.onDestroy()
    }
}