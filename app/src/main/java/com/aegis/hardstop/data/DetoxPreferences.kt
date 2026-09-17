package com.aegis.hardstop.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class DetoxPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aegis_detox_prefs", Context.MODE_PRIVATE)

    companion object {
        // Active Lock Keys
        private const val KEY_DETOX_END_TIME = "detox_end_time"
        private const val KEY_LOCKED_PACKAGES = "locked_packages"

        // Hourly Limit Keys
        private const val KEY_HOURLY_APPS = "hourly_apps"
        private const val KEY_USAGE_WINDOW_MINS = "usage_window_mins"
        private const val KEY_ALLOWANCE_MINS = "allowance_mins"
        private const val KEY_HOURLY_DAYS = "hourly_days"
        private const val KEY_HOURLY_BLOCK_MAP = "hourly_block_until_map"
        private const val KEY_HOURLY_CYCLE_START_MAP = "hourly_cycle_start_map"
        private const val KEY_HOURLY_MONITORING_ENABLED = "hourly_monitoring_enabled"

        // Hourly Locked State Keys
        private const val KEY_HOURLY_LOCKED_STATE = "hourly_locked_state"
        private const val KEY_HOURLY_LOCKED_UNTIL_TIME = "hourly_locked_until_time"
        private const val KEY_HOURLY_LOCKED_CYCLES = "hourly_locked_cycles"
        private const val KEY_HOURLY_PENDING_STOP_NEXT_CYCLE = "hourly_pending_stop_next_cycle"

        // Night Block Keys
        private const val KEY_NIGHT_APPS = "night_apps"
        private const val KEY_NIGHT_START_HOUR = "night_start_hour"
        private const val KEY_NIGHT_START_MIN = "night_start_min"
        private const val KEY_NIGHT_END_HOUR = "night_end_hour"
        private const val KEY_NIGHT_END_MIN = "night_end_min"
        private const val KEY_NIGHT_DAYS = "night_days"
        private const val KEY_NIGHT_MONITORING_ENABLED = "night_monitoring_enabled"

        // Night Locked State Keys
        private const val KEY_NIGHT_LOCKED_STATE = "night_locked_state"
        private const val KEY_NIGHT_PENDING_STOP_NEXT_CYCLE = "night_pending_stop_next_cycle"

        // Settings Keys
        private const val KEY_START_ON_BOOT = "start_on_boot"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_HOSTS_BLOCKING_ENABLED = "hosts_blocking_enabled"
        private const val KEY_BLOCKED_DOMAINS = "blocked_domains"
        private const val KEY_HMAC_SPOOFING_ENABLED = "hmac_spoofing_enabled"
        private const val KEY_HEADLESS_SERVICE_ENABLED = "headless_service_enabled"
    }

    fun getHourlyCycleStartMap(): MutableMap<String, Long> {
        val raw = prefs.getString(KEY_HOURLY_CYCLE_START_MAP, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Long>()
            obj.keys().forEach { key -> map[key] = obj.getLong(key) }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun setHourlyCycleStartMap(map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_HOURLY_CYCLE_START_MAP, obj.toString()).apply()
    }

    // --- Active Lock / Session Helpers ---
    fun isDetoxActive(): Boolean = System.currentTimeMillis() < getDetoxEndTime()

    fun getDetoxEndTime(): Long = prefs.getLong(KEY_DETOX_END_TIME, 0L)

    fun getLockedPackages(): Set<String> =
        prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()

    fun startDetoxSession(packages: Set<String>, durationMs: Long): Boolean {
        val newEndTime = System.currentTimeMillis() + durationMs
        if (isDetoxActive() && newEndTime < getDetoxEndTime()) return false

        prefs.edit()
            .putLong(KEY_DETOX_END_TIME, newEndTime)
            .putStringSet(KEY_LOCKED_PACKAGES, packages)
            .apply()
        return true
    }

    fun clearDetoxSession() {
        prefs.edit()
            .remove(KEY_DETOX_END_TIME)
            .remove(KEY_LOCKED_PACKAGES)
            .apply()
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

    fun isHourlyMonitoringActive(): Boolean = prefs.getBoolean(KEY_HOURLY_MONITORING_ENABLED, false)
    fun setHourlyMonitoringActive(enabled: Boolean) = prefs.edit().putBoolean(KEY_HOURLY_MONITORING_ENABLED, enabled).apply()

    // --- Hourly Locked State (Tamper-Proof & Multi-Cycle Lock) ---
    fun isHourlyLockedState(): Boolean {
        val enabled = prefs.getBoolean(KEY_HOURLY_LOCKED_STATE, false)
        if (!enabled) return false
        val untilTime = getHourlyLockedUntilTime()
        if (untilTime > 0 && System.currentTimeMillis() >= untilTime) {
            // Expired naturally
            clearHourlyLockedState()
            return false
        }
        return true
    }

    fun getHourlyLockedUntilTime(): Long = prefs.getLong(KEY_HOURLY_LOCKED_UNTIL_TIME, 0L)
    fun getHourlyLockedCycles(): Int = prefs.getInt(KEY_HOURLY_LOCKED_CYCLES, 1)

    fun enableHourlyLockedState(cycles: Int, untilTimestamp: Long) {
        prefs.edit()
            .putBoolean(KEY_HOURLY_LOCKED_STATE, true)
            .putInt(KEY_HOURLY_LOCKED_CYCLES, cycles)
            .putLong(KEY_HOURLY_LOCKED_UNTIL_TIME, untilTimestamp)
            .putBoolean(KEY_HOURLY_PENDING_STOP_NEXT_CYCLE, false)
            .apply()
    }

    fun clearHourlyLockedState() {
        prefs.edit()
            .putBoolean(KEY_HOURLY_LOCKED_STATE, false)
            .remove(KEY_HOURLY_LOCKED_UNTIL_TIME)
            .remove(KEY_HOURLY_LOCKED_CYCLES)
            .putBoolean(KEY_HOURLY_PENDING_STOP_NEXT_CYCLE, false)
            .apply()
    }

    fun isHourlyPendingStopNextCycle(): Boolean =
        prefs.getBoolean(KEY_HOURLY_PENDING_STOP_NEXT_CYCLE, false)

    fun setHourlyPendingStopNextCycle(pending: Boolean) {
        prefs.edit().putBoolean(KEY_HOURLY_PENDING_STOP_NEXT_CYCLE, pending).apply()
    }

    // --- Hourly Block State (per-app "blockedUntil" timestamps) ---
    fun getHourlyBlockUntilMap(): MutableMap<String, Long> {
        val raw = prefs.getString(KEY_HOURLY_BLOCK_MAP, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Long>()
            obj.keys().forEach { key -> map[key] = obj.getLong(key) }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun setHourlyBlockUntilMap(map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_HOURLY_BLOCK_MAP, obj.toString()).apply()
    }

    // --- Night Block Helpers ---
    fun getNightApps(): Set<String> = prefs.getStringSet(KEY_NIGHT_APPS, emptySet()) ?: emptySet()
    fun setNightApps(apps: Set<String>) = prefs.edit().putStringSet(KEY_NIGHT_APPS, apps).apply()

    fun isNightMonitoringActive(): Boolean = prefs.getBoolean(KEY_NIGHT_MONITORING_ENABLED, false)
    fun setNightMonitoringActive(enabled: Boolean) = prefs.edit().putBoolean(KEY_NIGHT_MONITORING_ENABLED, enabled).apply()

    // --- Night Locked State Helpers ---
    fun isNightLockedState(): Boolean = prefs.getBoolean(KEY_NIGHT_LOCKED_STATE, false)

    fun setNightLockedState(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT_LOCKED_STATE, enabled).apply()
        if (!enabled) {
            setNightPendingStopNextCycle(false)
        }
    }

    fun isNightPendingStopNextCycle(): Boolean =
        prefs.getBoolean(KEY_NIGHT_PENDING_STOP_NEXT_CYCLE, false)

    fun setNightPendingStopNextCycle(pending: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT_PENDING_STOP_NEXT_CYCLE, pending).apply()
    }

    fun getNightStart(): Pair<Int, Int> = Pair(
        prefs.getInt(KEY_NIGHT_START_HOUR, 22),
        prefs.getInt(KEY_NIGHT_START_MIN, 0)
    )

    fun setNightStart(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_NIGHT_START_HOUR, hour)
            .putInt(KEY_NIGHT_START_MIN, minute)
            .apply()
    }

    fun getNightEnd(): Pair<Int, Int> = Pair(
        prefs.getInt(KEY_NIGHT_END_HOUR, 6),
        prefs.getInt(KEY_NIGHT_END_MIN, 0)
    )

    fun setNightEnd(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_NIGHT_END_HOUR, hour)
            .putInt(KEY_NIGHT_END_MIN, minute)
            .apply()
    }

    fun getNightDays(): Set<Int> = decodeIntSet(prefs.getString(KEY_NIGHT_DAYS, "[1,2,3,4,5,6,7]"))
    fun setNightDays(days: Set<Int>) = prefs.edit().putString(KEY_NIGHT_DAYS, encodeIntSet(days)).apply()

    // --- App Settings Helpers ---
    fun getStartOnBoot(): Boolean = prefs.getBoolean(KEY_START_ON_BOOT, false)
    fun setStartOnBoot(enabled: Boolean) = prefs.edit().putBoolean(KEY_START_ON_BOOT, enabled).apply()

    // Theme modes: 0 = System, 1 = Light, 2 = Dark, 3 = Pure Black
    fun getThemeMode(): Int = prefs.getInt(KEY_THEME_MODE, 0)
    fun setThemeMode(mode: Int) = prefs.edit().putInt(KEY_THEME_MODE, mode).apply()

    // --- Experimental Settings Helpers ---
    fun isHostsBlockingEnabled(): Boolean = prefs.getBoolean(KEY_HOSTS_BLOCKING_ENABLED, false)
    fun setHostsBlockingEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_HOSTS_BLOCKING_ENABLED, enabled).apply()

    fun getBlockedDomains(): Set<String> = prefs.getStringSet(KEY_BLOCKED_DOMAINS, setOf("facebook.com", "instagram.com", "tiktok.com", "twitter.com", "x.com")) ?: emptySet()
    fun setBlockedDomains(domains: Set<String>) = prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, domains).apply()

    fun isHmacSpoofingEnabled(): Boolean = prefs.getBoolean(KEY_HMAC_SPOOFING_ENABLED, false)
    fun setHmacSpoofingEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_HMAC_SPOOFING_ENABLED, enabled).apply()

    fun isHeadlessServiceEnabled(): Boolean = prefs.getBoolean(KEY_HEADLESS_SERVICE_ENABLED, true)
    fun setHeadlessServiceEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_HEADLESS_SERVICE_ENABLED, enabled).apply()

    // --- Export & HMAC Signature Logic ---
    fun exportDataPayload(): String {
        val json = JSONObject()
        json.put("app", "HardStop(Aegis)")
        json.put("version", "2.4.0")
        json.put("exportTimeMs", System.currentTimeMillis())

        val config = JSONObject()
        config.put("themeMode", getThemeMode())
        config.put("startOnBoot", getStartOnBoot())
        config.put("headlessService", isHeadlessServiceEnabled())
        config.put("usageWindowMins", getUsageWindowMins())
        config.put("allowanceMins", getAllowanceMins())
        config.put("hourlyApps", JSONArray(getHourlyApps()))
        config.put("nightApps", JSONArray(getNightApps()))

        val nightStartObj = JSONObject()
        val (nsH, nsM) = getNightStart()
        nightStartObj.put("hour", nsH)
        nightStartObj.put("minute", nsM)
        config.put("nightStart", nightStartObj)

        val nightEndObj = JSONObject()
        val (neH, neM) = getNightEnd()
        nightEndObj.put("hour", neH)
        nightEndObj.put("minute", neM)
        config.put("nightEnd", nightEndObj)

        config.put("hostsBlockingEnabled", isHostsBlockingEnabled())
        config.put("blockedDomains", JSONArray(getBlockedDomains()))
        config.put("hmacSpoofingEnabled", isHmacSpoofingEnabled())

        json.put("config", config)

        val dataString = config.toString()
        val spoofed = isHmacSpoofingEnabled()

        val signature = if (spoofed) {
            "SPOOFED_HMAC_SHA256_0x" + java.lang.Long.toHexString(System.nanoTime()).uppercase() + "DEADBEEF4145474953"
        } else {
            calculateHmacSha256(dataString, "AegisHardStopSecretKey2026")
        }

        json.put("hmacSignature", signature)
        json.put("isHmacSpoofed", spoofed)

        return json.toString(2)
    }

    private fun calculateHmacSha256(data: String, key: String): String {
        return try {
            val secretKey = javax.crypto.spec.SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "ERROR_COMPUTING_HMAC"
        }
    }

    // --- Combined Lock & Active Window Evaluation ---
    fun getAllNightLockedApps(): Set<String> = getNightApps() + getHourlyApps()

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
            currentMinOfDay >= startTotalMin || currentMinOfDay < endTotalMin
        }
    }

    // --- Serialization Helpers ---
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
