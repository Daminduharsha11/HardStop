package com.aegis.hardstop.service

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aegis.hardstop.data.DetoxPreferences
import com.aegis.hardstop.engine.ShizukuPackageEngine
import kotlinx.coroutines.*
import java.util.Calendar

open class DetoxTimerService : Service() {

    private var countDownTimer: CountDownTimer? = null

    private var lockedPackages: Set<String> = emptySet()
    private lateinit var prefs: DetoxPreferences
    private lateinit var shizukuEngine: ShizukuPackageEngine

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var periodicJob: Job? = null

    companion object {
        private const val TAG = "DetoxTimerService"
        const val CHANNEL_ID = "AegisDetoxTimerChannel"
        const val NOTIFICATION_ID = 888

        const val ACTION_START = "ACTION_START"
        const val ACTION_EVALUATE_RULES = "ACTION_EVALUATE_RULES"
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val EXTRA_PACKAGES = "EXTRA_PACKAGES"
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"
        const val ACTION_STOP_MONITORING = "ACTION_STOP_MONITORING"
        const val ACTION_STOP_NIGHT_MONITORING = "ACTION_STOP_NIGHT_MONITORING"
        const val ACTION_START_HOURLY_MONITORING = "ACTION_START_HOURLY_MONITORING"
        const val ACTION_START_NIGHT_MONITORING = "ACTION_START_NIGHT_MONITORING"
        const val ACTION_STOP_ALL_BACKGROUND = "ACTION_STOP_ALL_BACKGROUND"

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
            val prefs = DetoxPreferences(context)
            if (!prefs.isHeadlessServiceEnabled() && !prefs.isDetoxActive() && !prefs.isHourlyMonitoringActive() && !prefs.isNightMonitoringActive()) {
                Log.d(TAG, "evaluateRules: Headless service disabled and no active sessions. Skipping.")
                return
            }
            val intent = Intent(context, DetoxTimerService::class.java).apply {
                action = ACTION_EVALUATE_RULES
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service for rule evaluation", e)
            }
        }

        fun stopAllBackgroundWork(context: Context) {
            val intent = Intent(context, DetoxTimerService::class.java).apply {
                action = ACTION_STOP_ALL_BACKGROUND
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send stop all background work action", e)
            }
        }

        fun startNightMonitoring(context: Context) {
            val intent = Intent(context, DetoxTimerService::class.java).apply {
                action = ACTION_START_NIGHT_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startHourlyMonitoring(context: Context) {
            val intent = Intent(context, DetoxTimerService::class.java).apply {
                action = ACTION_START_HOURLY_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startMonitoring(context: Context) {
            evaluateRules(context)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, DetoxTimerService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopNightMonitoring(context: Context) {
            val intent = Intent(context, DetoxTimerService::class.java).apply {
                action = ACTION_STOP_NIGHT_MONITORING
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

        // Shizuku Crash & Restart Recovery Hook
        shizukuEngine.onBinderStateChangeListener = { isAlive ->
            if (isAlive) {
                Log.i(TAG, "Shizuku reconnected in background service! Re-enforcing rules.")
                checkNightAndHourlyRules()
            } else {
                Log.w(TAG, "Shizuku binder died in background service. Waiting for recovery.")
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        val notification = buildNotification("HardStop Active")
        startForegroundServiceInternal(notification)

        when (action) {
            ACTION_STOP_ALL_BACKGROUND -> {
                Log.d(TAG, "ACTION_STOP_ALL_BACKGROUND: Stopping all background alarms and service.")
                cancelNextNightAlarm()
                cancelNextUnblockAlarm()
                periodicJob?.cancel()
                if (!prefs.isDetoxActive()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }

            ACTION_START_NIGHT_MONITORING -> {
                Log.d(TAG, "Night block monitoring initialized.")
                prefs.setNightMonitoringActive(true)
                checkNightAndHourlyRules()
                updateServiceNotification()
                startPeriodicRuleChecking()
            }

            ACTION_START_HOURLY_MONITORING -> {
                Log.d(TAG, "Hourly monitoring initialized.")
                prefs.setHourlyMonitoringActive(true)
                checkNightAndHourlyRules()
                updateServiceNotification()
                startPeriodicRuleChecking()
            }

            ACTION_STOP_MONITORING -> {
                Log.d(TAG, "Hourly monitoring stop requested.")
                // If locked state is active, enforce next-cycle deferral
                if (prefs.isHourlyLockedState()) {
                    Log.i(TAG, "Locked state active! Marking pending stop for next cycle.")
                    prefs.setHourlyPendingStopNextCycle(true)
                    updateNotification("Aegis Detox: Stop requested (will end at next cycle)")
                } else {
                    prefs.setHourlyMonitoringActive(false)
                    prefs.clearHourlyLockedState()
                    val hourlyApps = prefs.getHourlyApps()
                    if (hourlyApps.isNotEmpty()) {
                        shizukuEngine.unsuspendPackages(hourlyApps)
                    }
                    prefs.setHourlyBlockUntilMap(emptyMap())
                    prefs.setHourlyCycleStartMap(emptyMap())
                    cancelNextUnblockAlarm()
                }
                if (!shouldKeepRunning()) {
                    periodicJob?.cancel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    updateServiceNotification()
                }
            }

            ACTION_STOP_NIGHT_MONITORING -> {
                Log.d(TAG, "Night block monitoring stop requested.")
                if (prefs.isNightLockedState() && prefs.isNightBlockActiveNow()) {
                    Log.i(TAG, "Scheduled locked state active! Marking pending stop for next window end.")
                    prefs.setNightPendingStopNextCycle(true)
                    updateNotification("Aegis Detox: Stop requested (will end when block finishes)")
                } else {
                    prefs.setNightMonitoringActive(false)
                    prefs.setNightLockedState(false)
                    val nightApps = prefs.getNightApps()
                    if (nightApps.isNotEmpty() && !prefs.isDetoxActive()) {
                        shizukuEngine.unsuspendPackages(nightApps)
                    }
                    cancelNextNightAlarm()
                }
                if (!shouldKeepRunning()) {
                    periodicJob?.cancel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    updateServiceNotification()
                }
            }

            ACTION_EVALUATE_RULES -> {
                checkNightAndHourlyRules()
                updateServiceNotification()
                if (shouldKeepRunning()) {
                    startPeriodicRuleChecking()
                } else {
                    periodicJob?.cancel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }

            else -> {
                if (prefs.isDetoxActive()) {
                    lockedPackages = prefs.getLockedPackages()
                    startCountdown(prefs.getDetoxEndTime() - System.currentTimeMillis())
                } else if (shouldKeepRunning()) {
                    checkNightAndHourlyRules()
                    updateServiceNotification()
                    startPeriodicRuleChecking()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        return START_STICKY
    }

    private fun shouldKeepRunning(): Boolean {
        return prefs.isDetoxActive() ||
               prefs.isHourlyMonitoringActive() ||
               prefs.isNightMonitoringActive() ||
               prefs.isHeadlessServiceEnabled()
    }

    private fun updateServiceNotification() {
        if (prefs.isDetoxActive()) return
        val text = when {
            prefs.isNightBlockActiveNow() && prefs.isHourlyMonitoringActive() -> "Scheduled Block & Hourly Limits Active"
            prefs.isNightBlockActiveNow() -> "Scheduled Night Block Active"
            prefs.isHourlyMonitoringActive() -> "Hourly App Usage Limits Active"
            prefs.isNightMonitoringActive() -> "Scheduled Monitoring Active (Standby)"
            prefs.isHeadlessServiceEnabled() -> "Headless Rule Protection Active"
            else -> "HardStop Protection Active"
        }
        updateNotification(text)
    }

    private fun startPeriodicRuleChecking() {
        if (periodicJob?.isActive == true) return
        periodicJob = serviceScope.launch {
            while (isActive) {
                delay(30_000L)
                if (shouldKeepRunning()) {
                    checkNightAndHourlyRules()
                    updateServiceNotification()
                } else {
                    break
                }
            }
        }
    }

    private fun startForegroundServiceInternal(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
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

        if (nightApps.isEmpty() || !prefs.isNightMonitoringActive()) {
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
            if (prefs.isNightMonitoringActive()) {
                checkNightBlockRules()
            }
            if (prefs.isHourlyMonitoringActive()) {
                checkHourlyAllowanceRules()
            }
            if (prefs.isHostsBlockingEnabled()) {
                shizukuEngine.applyHostsDomainBlock(prefs.getBlockedDomains(), true)
            }
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
            } else {
                // Window has ended! Check if user requested a pending stop
                if (prefs.isNightPendingStopNextCycle()) {
                    Log.i(TAG, "Night block ended and pending stop was requested. Turning off scheduled monitoring.")
                    prefs.setNightMonitoringActive(false)
                    prefs.setNightPendingStopNextCycle(false)
                    prefs.setNightLockedState(false)
                }

                if (!prefs.isDetoxActive()) {
                    shizukuEngine.unsuspendPackages(nightApps)
                }
            }
        }

        // Schedule next transition boundary
        scheduleNextNightAlarm()
    }

    private fun getForegroundTimeMs(pkg: String, startTime: Long, endTime: Long): Long {
        val usageManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L
        val events = usageManager.queryEvents(startTime, endTime)
        var totalTime = 0L
        var lastResumeTime = -1L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != pkg) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeTime = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (lastResumeTime != -1L) {
                        val resumeClamped = maxOf(lastResumeTime, startTime)
                        val pauseClamped = minOf(event.timeStamp, endTime)
                        if (pauseClamped > resumeClamped) {
                            totalTime += (pauseClamped - resumeClamped)
                        }
                        lastResumeTime = -1L
                    }
                }
            }
        }

        // App is still open right now — count up to "endTime"
        if (lastResumeTime != -1L) {
            val resumeClamped = maxOf(lastResumeTime, startTime)
            if (endTime > resumeClamped) {
                totalTime += (endTime - resumeClamped)
            }
        }

        return totalTime
    }

    private fun checkHourlyAllowanceRules() {
        val hourlyApps = prefs.getHourlyApps()
        if (hourlyApps.isEmpty() || !prefs.isHourlyMonitoringActive()) {
            cancelNextUnblockAlarm()
            return
        }

        val now = System.currentTimeMillis()
        val windowMs = prefs.getUsageWindowMins() * 60_000L
        val allowanceMins = prefs.getAllowanceMins()
        val blockMap = prefs.getHourlyBlockUntilMap()
        val cycleStartMap = prefs.getHourlyCycleStartMap()

        val toUnblock = mutableSetOf<String>()
        val newlyExceeded = mutableSetOf<String>()
        val currentlyBlocked = mutableSetOf<String>()
        var cycleResetOccurred = false

        hourlyApps.forEach { pkg ->
            var cycleStart = cycleStartMap[pkg]

            // Deterministic reset: ONLY after full windowMs has elapsed since cycleStart was established
            if (cycleStart == null) {
                // First initialization of cycleStart for this app — NOT a cycle reset!
                cycleStart = now
                cycleStartMap[pkg] = now
            } else if (now - cycleStart >= windowMs) {
                // Genuine cycle reset after full windowMs duration
                cycleResetOccurred = true
                cycleStart = now
                cycleStartMap[pkg] = now
                if (blockMap.remove(pkg) != null) {
                    toUnblock.add(pkg)
                }
            }

            // Check if app is in active blocked state for this cycle
            val blockedUntil = blockMap[pkg]
            if (blockedUntil != null && now < blockedUntil) {
                currentlyBlocked.add(pkg)
                return@forEach
            }

            // Usage counted safely using precise event parsing within window
            val usageMs = getForegroundTimeMs(pkg, cycleStart, now)
            val usageMins = usageMs / (1000 * 60)

            Log.d(TAG, "App $pkg usage: ${usageMins}m / limit ${allowanceMins}m (cycle started $cycleStart)")

            if (usageMins >= allowanceMins) {
                newlyExceeded.add(pkg)
                blockMap[pkg] = cycleStart + windowMs
            }
        }

        // Check if locked state pending stop was requested and cycle reset happened
        if (cycleResetOccurred && prefs.isHourlyPendingStopNextCycle()) {
            Log.i(TAG, "Cycle reset reached with pending stop requested! Disabling usage limit.")
            prefs.setHourlyMonitoringActive(false)
            prefs.clearHourlyLockedState()
            prefs.setHourlyPendingStopNextCycle(false)

            val allHourly = prefs.getHourlyApps()
            if (allHourly.isNotEmpty()) {
                shizukuEngine.unsuspendPackages(allHourly)
            }
            prefs.setHourlyBlockUntilMap(emptyMap())
            prefs.setHourlyCycleStartMap(emptyMap())
            cancelNextUnblockAlarm()
            updateNotification("Usage Limit ended at cycle boundary as requested.")
            return
        }

        if (toUnblock.isNotEmpty() && !prefs.isDetoxActive()) {
            Log.d(TAG, "Cycle reset, unsuspending: $toUnblock")
            shizukuEngine.unsuspendPackages(toUnblock)
        }

        // SHIZUKU CRASH & RESTART RESILIENT SUSPENSION:
        val needSuspension = mutableSetOf<String>()
        needSuspension.addAll(newlyExceeded)

        currentlyBlocked.forEach { pkg ->
            if (!shizukuEngine.isPackageSuspended(pkg)) {
                Log.w(TAG, "App $pkg is marked blocked until ${blockMap[pkg]} but is NOT suspended (Shizuku crash/restart recovery). Re-suspending.")
                needSuspension.add(pkg)
            }
        }

        if (needSuspension.isNotEmpty()) {
            Log.d(TAG, "Enforcing suspension for ${needSuspension.size} apps: $needSuspension")
            shizukuEngine.suspendPackages(needSuspension) { successful, failed ->
                if (failed.isNotEmpty()) {
                    Log.w(TAG, "${failed.size} apps failed suspension during limit trigger (Shizuku crash/restart). Engine will auto-retry upon binder reconnect.")
                }
            }
        }

        prefs.setHourlyBlockUntilMap(blockMap)
        prefs.setHourlyCycleStartMap(cycleStartMap)
        scheduleNextUnblockAlarm()
    }

    private fun scheduleNextUnblockAlarm() {
        val blockMap = prefs.getHourlyBlockUntilMap()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DetoxTimerService::class.java).apply {
            action = ACTION_EVALUATE_RULES
        }
        val pendingIntent = PendingIntent.getService(
            this, REQUEST_CODE_UNBLOCK, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hourlyApps = prefs.getHourlyApps()
        if (hourlyApps.isEmpty() || !prefs.isHourlyMonitoringActive()) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val now = System.currentTimeMillis()
        val allowanceMs = prefs.getAllowanceMins() * 60_000L
        val cycleStartMap = prefs.getHourlyCycleStartMap()
        var minNextWakeup = Long.MAX_VALUE

        hourlyApps.forEach { pkg ->
            val blockedUntil = blockMap[pkg]
            if (blockedUntil != null && blockedUntil > now) {
                // App is currently BLOCKED (suspended) -> usage is 0 while suspended, target exact unblock timestamp
                minNextWakeup = minOf(minNextWakeup, blockedUntil)
            } else {
                // App is UNBLOCKED -> active usage tracking phase
                val cycleStart = cycleStartMap[pkg] ?: now
                val usageMs = getForegroundTimeMs(pkg, cycleStart, now)
                val remainingMs = maxOf(0L, allowanceMs - usageMs)

                if (remainingMs > 0) {
                    // Monitor active usage dynamically: check every 60s or when allowance runs out
                    val nextCheck = now + minOf(60_000L, remainingMs)
                    minNextWakeup = minOf(minNextWakeup, nextCheck)
                } else {
                    // Allowance exhausted, trigger block evaluation immediately
                    minNextWakeup = minOf(minNextWakeup, now + 1000L)
                }
            }
        }

        val targetTime = if (minNextWakeup != Long.MAX_VALUE) minNextWakeup else now + 60_000L

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, targetTime, pendingIntent)
            }
            Log.d(TAG, "Scheduled next hourly rule evaluation at $targetTime (in ${(targetTime - now) / 1000}s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact unblock alarm", e)
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
        serviceScope.cancel()
        periodicJob?.cancel()
        shizukuEngine.release()
        countDownTimer?.cancel()
        cancelNextNightAlarm()
        cancelNextUnblockAlarm()
        isRunning = false
        super.onDestroy()
    }
}
