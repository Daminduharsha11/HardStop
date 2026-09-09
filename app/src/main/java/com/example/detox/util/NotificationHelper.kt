package com.example.detox.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val WARNING_CHANNEL_ID = "AegisDetoxWarningChannel"
    private const val WARNING_NOTIFICATION_ID = 999

    fun showWarningNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // For Android 8.0+, configure a high-importance channel with sound/vibration for pop-up banners
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WARNING_CHANNEL_ID,
                "Aegis Warning Alerts",
                NotificationManager.IMPORTANCE_HIGH // REQUIRED for heads-up pop-up banners
            ).apply {
                description = "Warning alerts before apps are suspended"
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
            }
            notificationManager.createNotificationChannel(channel) // Fixed capitalization here
        }

        val notification = NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH) 
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(WARNING_NOTIFICATION_ID, notification)
    }
}