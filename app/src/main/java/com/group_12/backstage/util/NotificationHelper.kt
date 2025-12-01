package com.group_12.backstage.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.group_12.backstage.R

object NotificationHelper {

    private const val LOCATION_CHANNEL_ID = "location_updates_channel"
    private const val LOCATION_NOTIFICATION_ID = 1001

    private fun ensureLocationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LOCATION_CHANNEL_ID,
                "Location updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown briefly while your city/country are being saved."
            }

            val manager: NotificationManager? =
                context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /** Show a brief notification while we save the user's location. */
    fun showLocationSavingNotification(context: Context) {
        ensureLocationChannel(context)

        val notification = NotificationCompat.Builder(context, LOCATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle("Using your location")
            .setContentText("Backstage is saving your city & country to personalize events.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)          // looks “active” while we work
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(LOCATION_NOTIFICATION_ID, notification)
    }

    /** Remove the notification once the work is done / failed. */
    fun hideLocationSavingNotification(context: Context) {
        NotificationManagerCompat.from(context)
            .cancel(LOCATION_NOTIFICATION_ID)
    }
}