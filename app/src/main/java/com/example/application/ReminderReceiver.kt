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

/**
 * Receives reminder alarms (hydration/medication) and posts a heads-up notification.
 *
 * Behavior
 * - Reads an Intent extra "type" (e.g., "HYDRATION_EXACT", "MEDICATION_REPEAT").
 * - Ensures a high-importance notification channel exists.
 * - Posts a notification that deep-links to the LOG screen in [MainActivity].
 *
 * Permissions
 * - On Android 13+ (API 33+), requires POST_NOTIFICATIONS at runtime.
 * - Method is annotated with [RequiresPermission] to highlight the requirement.
 *
 * Deep link
 * - Adds extra "route" with [Destination.LOG.route] so MainActivity can navigate directly.
 *
 * Stability
 * - Notification IDs are stable per type to avoid overwriting unrelated reminders.
 */
class ReminderReceiver : BroadcastReceiver() {

    /**
     * Handles the incoming broadcast and displays a heads-up notification if allowed.
     *
     * Contract
     * - Intent extra "type": String.
     *   Known prefixes: "MEDICATION", "HYDRATION".
     *   Known full values: "MEDICATION_EXACT", "MEDICATION_REPEAT",
     *                      "HYDRATION_EXACT",  "HYDRATION_REPEAT".
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        val rawType = intent.getStringExtra("type") ?: "HYDRATION"
        val isMedication = rawType.startsWith("MEDICATION")
        val isHydration = rawType.startsWith("HYDRATION")
        val channelId = "reminders_high"

        // Ensure a HIGH-importance channel so notifications can show as heads-up.
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

        // Build a deep link into the app's LOG screen.
        // FLAG_ACTIVITY_CLEAR_TOP + NEW_TASK keeps navigation predictable when launched from a notification.
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", Destination.LOG.route)
        }

        // Use distinct requestCodes per category to avoid PendingIntent collisions.
        // FLAG_IMMUTABLE is required on Android 12+ when the intent is not meant to be modified.
        val contentPi = PendingIntent.getActivity(
            context,
            when {
                isMedication -> 201
                else -> 200
            },
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Human-readable title/body per category. Keep text short for heads-up UX.
        val (title, text) = when {
            isMedication -> "Medication time" to "Please take your medication."
            isHydration  -> "Hydration reminder" to "Have a glass of water."
            else         -> "Reminder" to "Don’t forget your task."
        }

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder) // TODO: replace with app icon for production
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Heads-up on pre-O; O+ uses channel importance
            .setContentIntent(contentPi)
            .build()

        val nm = NotificationManagerCompat.from(context)

        // If notifications are disabled for the app (especially on API 33+), skip posting gracefully.
        if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) {
            Log.w("ReminderReceiver", "Notifications are disabled for the app; skipping notify()")
            return
        }

        // Stable IDs per type so different streams don't overwrite each other.
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
            // If POST_NOTIFICATIONS is not granted, avoid crashing and log for diagnostics.
            Log.e("ReminderReceiver", "Failed to post notification (permission?): ${se.localizedMessage}")
        }
    }
}

