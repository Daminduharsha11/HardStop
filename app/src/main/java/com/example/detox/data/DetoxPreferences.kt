package com.example.detox.data

import android.content.Context
import android.content.SharedPreferences

class DetoxPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aegis_detox_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_ACTIVE = "key_is_active"
        private const val KEY_END_TIMESTAMP = "key_end_timestamp"
        private const val KEY_LOCKED_PACKAGES = "key_locked_packages"
    }

    fun saveSession(packages: Set<String>, durationMinutes: Int) {
        val endTimestamp = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, true)
            .putLong(KEY_END_TIMESTAMP, endTimestamp)
            .putStringSet(KEY_LOCKED_PACKAGES, packages)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, false)
            .putLong(KEY_END_TIMESTAMP, 0L)
            .putStringSet(KEY_LOCKED_PACKAGES, emptySet())
            .apply()
    }

    fun isSessionActive(): Boolean = prefs.getBoolean(KEY_IS_ACTIVE, false)

    fun getEndTimestamp(): Long = prefs.getLong(KEY_END_TIMESTAMP, 0L)

    fun getLockedPackages(): Set<String> =
        prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()

    fun getRemainingMillis(): Long {
        val end = getEndTimestamp()
        val now = System.currentTimeMillis()
        val remaining = end - now
        return if (remaining > 0) remaining else 0L
    }
}
