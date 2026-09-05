package com.example.detox.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.detox.data.DetoxPreferences
import com.example.detox.engine.ShizukuPackageEngine
import com.example.detox.service.DetoxTimerService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            val prefs = DetoxPreferences(context)
            if (prefs.isSessionActive()) {
                val remainingMs = prefs.getRemainingMillis()
                val lockedPackages = prefs.getLockedPackages()

                if (remainingMs > 0 && lockedPackages.isNotEmpty()) {
                    // Re-enforce app suspension state on boot
                    lockedPackages.forEach { pkg ->
                        ShizukuPackageEngine.setPackageSuspended(pkg, true)
                    }

                    // Calculate remaining minutes and re-start service
                    val remainingMinutes = ((remainingMs / 1000) / 60).toInt().coerceAtLeast(1)
                    DetoxTimerService.startService(
                        context,
                        ArrayList(lockedPackages),
                        remainingMinutes
                    )
                } else {
                    // Session expired while device was turned off
                    lockedPackages.forEach { pkg ->
                        ShizukuPackageEngine.setPackageSuspended(pkg, false)
                    }
                    prefs.clearSession()
                }
            }
        }
    }
}
