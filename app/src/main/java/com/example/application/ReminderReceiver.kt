package com.example.application

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        val rawType = intent.getStringExtra("type") ?: "HYDRATION"
        val isMedication = rawType.startsWith("MEDICATION")
        val isHydration = rawType.startsWith("HYDRATION")
        val channelId = "reminders_high"

        //  Ensure a HIGH-importance channel (heads-up)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Hydration/Medication reminders"
                        enableLights(true)
                        lightColor = Color.MAGENTA
                        enableVibration(true)
                    }
                )
            }
        }

        // Deep-link to LOG screen
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", Destination.LOG.route)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            when {
                isMedication -> 201
                else -> 200
            },
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val (title, text) = when {
            isMedication -> "Medication time" to "Please take your medication."
            isHydration  -> "Hydration reminder" to "Have a glass of water."
            else         -> "Reminder" to "Don’t forget your task."
        }

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPi)
            .build()

        val nm = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) {
            Log.w("ReminderReceiver", "Notifications are disabled for the app; skipping notify()")
            return
        }

        // Stable IDs per type (helps avoid overwriting)
        val notificationId = when (rawType) {
            "MEDICATION_EXACT"  -> 101
            "MEDICATION_REPEAT" -> 102
            "HYDRATION_EXACT"   -> 111
            "HYDRATION_REPEAT"  -> 112
            else                -> if (isMedication) 101 else 111
        }

        try {
            Log.d("ReminderReceiver", "Posting notification type=$rawType title=$title id=$notificationId")
            nm.notify(notificationId, notif)
        } catch (se: SecurityException) {
            // If POST_NOTIFICATIONS not granted at runtime, avoid crashing
            Log.e("ReminderReceiver", "Failed to post notification (permission?): ${se.localizedMessage}")
        }
    }
}
