package com.example.detox.security

import android.content.Context
import android.os.SystemClock
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class SecureLockManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "aegis_secure_lock_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SKEY_REQUEST,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_LOCK_END_ELAPSED_TIME = "key_lock_end_elapsed_time"
        private const val KEY_LOCKED_PACKAGES = "key_locked_packages"
        private const val KEY_SESSION_ACTIVE = "key_session_active"
    }

    /**
     * Saves lock state using SystemClock.elapsedRealtime() which is immune to system clock changes.
     */
    fun saveLockSession(packages: Set<String>, durationMinutes: Int) {
        val durationMs = durationMinutes * 60 * 1000L
        val targetEndTime = SystemClock.elapsedRealtime() + durationMs

        val jsonArray = JSONArray(packages)

        prefs.edit()
            .putLong(KEY_LOCK_END_ELAPSED_TIME, targetEndTime)
            .putString(KEY_LOCKED_PACKAGES, jsonArray.toString())
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .apply()
    }

    fun isSessionActive(): Boolean {
        if (!prefs.getBoolean(KEY_SESSION_ACTIVE, false)) return false
        val targetEndTime = prefs.getLong(KEY_LOCK_END_ELAPSED_TIME, 0L)
        val currentTime = SystemClock.elapsedRealtime()
        
        // If current elapsed time is past target end time, session has expired
        return currentTime < targetEndTime
    }

    fun getRemainingTimeMs(): Long {
        if (!isSessionActive()) return 0L
        val targetEndTime = prefs.getLong(KEY_LOCK_END_ELAPSED_TIME, 0L)
        val remaining = targetEndTime - SystemClock.elapsedRealtime()
        return if (remaining > 0) remaining else 0L
    }

    fun getLockedPackages(): Set<String> {
        val jsonStr = prefs.getString(KEY_LOCKED_PACKAGES, "[]") ?: "[]"
        val array = JSONArray(jsonStr)
        val set = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            set.add(array.getString(i))
        }
        return set
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
