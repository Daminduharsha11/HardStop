package com.aegis.hardstop.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aegis.hardstop.data.DetoxPreferences
import com.aegis.hardstop.service.DetoxTimerService
import java.util.ArrayList

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val preferences = DetoxPreferences(context)
            if (preferences.getStartOnBoot()) {
                // Resume ongoing active timer lock if not expired
                if (preferences.isDetoxActive()) {
                    val remainingMs = preferences.getDetoxEndTime() - System.currentTimeMillis()
                    if (remainingMs > 0) {
                        val packages = ArrayList<String>(preferences.getLockedPackages())
                        DetoxTimerService.startService(context, packages, remainingMs)
                    }
                }

                // Resume monitoring for hourly or night rules if active
                if (preferences.isHourlyMonitoringActive() || preferences.isNightMonitoringActive()) {
                    DetoxTimerService.startMonitoring(context)
                }
            }
        }
    }
}
