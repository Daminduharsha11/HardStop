package com.example.detox.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.Calendar

class DetoxPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aegis_detox_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DETOX_END_TIME = "detox_end_time"
        private const val KEY_LOCKED_PACKAGES = "locked_packages"

        private const val KEY_HOURLY_APPS = "hourly_apps"
        private const val KEY_USAGE_WINDOW_MINS = "usage_window_mins"
        private const val KEY_ALLOWANCE_MINS = "allowance_mins"
        private const val KEY_HOURLY_DAYS = "hourly_days"

        private const val KEY_NIGHT_APPS = "night_apps"
        private const val KEY_NIGHT_START_HOUR = "night_start_hour"
        private const val KEY_NIGHT_START_MIN = "night_start_min"
        private const val KEY_NIGHT_END_HOUR = "night_end_hour"
        private const val KEY_NIGHT_END_MIN = "night_end_min"
        private const val KEY_NIGHT_DAYS = "night_days"
    }

    // --- Active Lock Helpers ---
    fun isDetoxActive(): Boolean = System.currentTimeMillis() < getDetoxEndTime()
    fun getDetoxEndTime(): Long = prefs.getLong(KEY_DETOX_END_TIME, 0L)
    fun getLockedPackages(): Set<String> = prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()

    fun startDetoxSession(packages: Set<String>, durationMs: Long): Boolean {
        val newEndTime = System.currentTimeMillis() + durationMs
        if (isDetoxActive() && newEndTime < getDetoxEndTime()) return false
        prefs.edit().putLong(KEY_DETOX_END_TIME, newEndTime).putStringSet(KEY_LOCKED_PACKAGES, packages).apply()
        return true
    }

    fun clearDetoxSession() {
        prefs.edit().remove(KEY_DETOX_END_TIME).remove(KEY_LOCKED_PACKAGES).apply()
    }

    // --- Hourly Limit Helpers ---
    fun getHourlyApps(): Set<String> = prefs.getStringSet(KEY_HOURLY_APPS, emptySet()) ?: emptySet()
    fun setHourlyApps(apps: Set<String>) = prefs.edit().putStringSet(KEY_HOURLY_APPS, apps).apply()

    fun getUsageWindowMins(): Int = prefs.getInt(KEY_USAGE_WINDOW_MINS, 60)
    fun setUsageWindowMins(mins: Int) = prefs.edit().putInt(KEY_USAGE_WINDOW_MINS, mins).apply()

    fun getAllowanceMins(): Int = prefs.getInt(KEY_ALLOWANCE_MINS, 15)
    fun setAllowanceMins(mins: Int) = prefs.edit().putInt(KEY_ALLOWANCE_MINS, mins).apply()

    fun getHourlyDays(): Set<Int> = decodeIntSet(prefs.getString(KEY_HOURLY_DAYS, "[1,2,3,4,5,6,7]"))
    fun setHourlyDays(days: Set<Int>) = prefs.edit().putString(KEY_HOURLY_DAYS, encodeIntSet(days)).apply()

    // --- Night Block Helpers ---
    fun getNightApps(): Set<String> = prefs.getStringSet(KEY_NIGHT_APPS, emptySet()) ?: emptySet()
    fun setNightApps(apps: Set<String>) = prefs.edit().putStringSet(KEY_NIGHT_APPS, apps).apply()

    fun getNightStart(): Pair<Int, Int> = Pair(prefs.getInt(KEY_NIGHT_START_HOUR, 22), prefs.getInt(KEY_NIGHT_START_MIN, 0))
    fun setNightStart(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_NIGHT_START_HOUR, hour).putInt(KEY_NIGHT_START_MIN, minute).apply()
    }

    fun getNightEnd(): Pair<Int, Int> = Pair(prefs.getInt(KEY_NIGHT_END_HOUR, 6), prefs.getInt(KEY_NIGHT_END_MIN, 0))
    fun setNightEnd(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_NIGHT_END_HOUR, hour).putInt(KEY_NIGHT_END_MIN, minute).apply()
    }

    fun getNightDays(): Set<Int> = decodeIntSet(prefs.getString(KEY_NIGHT_DAYS, "[1,2,3,4,5,6,7]"))
    fun setNightDays(days: Set<Int>) = prefs.edit().putString(KEY_NIGHT_DAYS, encodeIntSet(days)).apply()

    // Combined apps lock during night window (Hourly Apps + Night Apps)
    fun getAllNightLockedApps(): Set<String> = getNightApps() + getHourlyApps()

    // Evaluates whether current time falls inside scheduled Night Block window
    fun isNightBlockActiveNow(): Boolean {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        if (!getNightDays().contains(currentDay)) return false

        val currentMinOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val (startHour, startMin) = getNightStart()
        val (endHour, endMin) = getNightEnd()

        val startTotalMin = startHour * 60 + startMin
        val endTotalMin = endHour * 60 + endMin

        return if (startTotalMin < endTotalMin) {
            currentMinOfDay in startTotalMin until endTotalMin
        } else {
            // Overnight lock (e.g., 22:00 to 06:00)
            currentMinOfDay >= startTotalMin || currentMinOfDay < endTotalMin
        }
    }

    private fun encodeIntSet(set: Set<Int>): String {
        val array = JSONArray()
        set.forEach { array.put(it) }
        return array.toString()
    }

    private fun decodeIntSet(json: String?): Set<Int> {
        if (json.isNullOrEmpty()) return setOf(1, 2, 3, 4, 5, 6, 7)
        val result = mutableSetOf<Int>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            result.add(array.getInt(i))
        }
        return result
    }
}