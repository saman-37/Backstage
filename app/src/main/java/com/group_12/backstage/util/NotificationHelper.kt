package com.group_12.backstage.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
//import androidx.privacysandbox.tools.core.generator.build
import com.group_12.backstage.R
import com.group_12.backstage.MainActivity

object NotificationHelper {

    private const val LOCATION_CHANNEL_ID = "location_updates_channel"
    private const val LOCATION_NOTIFICATION_ID = 1001

    // 1. ADD NEW CONSTANTS FOR MESSAGE NOTIFICATIONS
    private const val MESSAGE_CHANNEL_ID = "new_message_channel"
    private const val MESSAGE_NOTIFICATION_ID = 1002 // Use a different ID


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

    private fun ensureMessageChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use a higher importance for messages so they pop up
            val channel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "New Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows a notification when you receive a new message."
            }

            val manager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }


    /** Show a brief notification while we save the user's location. */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
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

    /**
     * Shows a notification for a new chat message.
     *
     * @param context The application context.
     * @param senderName The name of the person who sent the message.
     * @param messageText The content of the message.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showNewMessageNotification(context: Context, senderName: String, messageText: String) {
        // Ensure the channel exists before we try to use it
        ensureMessageChannel(context)

        // Create an intent that will open the app when the notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_message_notification) // A good practice is to have a monochrome status bar icon
            .setContentTitle("New message from $senderName")
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Ensures the notification pops up
            .setContentIntent(pendingIntent) // The action to perform when tapped
            .setAutoCancel(true) // The notification disappears after being tapped
            .build()

        // You must check for permission before notifying, although the calling site should also handle this.
        // The @RequiresPermission annotation helps, but this is a runtime safeguard.
        NotificationManagerCompat.from(context).notify(MESSAGE_NOTIFICATION_ID, notification)
    }

}