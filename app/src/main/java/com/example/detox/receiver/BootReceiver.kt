package com.example.detox.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.detox.data.DetoxPreferences
import com.example.detox.engine.ShizukuPackageEngine
import com.example.detox.service.DetoxTimerService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            val prefs = DetoxPreferences(context)

            if (prefs.isDetoxActive()) {
                val lockedApps = prefs.getLockedPackages()
                val remainingMs = prefs.getDetoxEndTime() - System.currentTimeMillis()

                if (remainingMs > 0 && lockedApps.isNotEmpty()) {
                    // Re-suspend packages via Shizuku
                    if (ShizukuPackageEngine.isShizukuAvailable()) {
                        ShizukuPackageEngine.suspendPackages(lockedApps)
                    }

                    // Restart timer service with explicit ArrayList<String> type
                    val pkgList = ArrayList<String>(lockedApps)
                    val serviceIntent = Intent(context, DetoxTimerService::class.java).apply {
                        action = DetoxTimerService.ACTION_START
                        putStringArrayListExtra(DetoxTimerService.EXTRA_PACKAGES, pkgList)
                        putExtra(DetoxTimerService.EXTRA_DURATION_MS, remainingMs)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else {
                    prefs.clearDetoxSession()
                }
            }
        }
    }
}