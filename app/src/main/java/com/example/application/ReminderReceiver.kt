package com.example.application

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: "HYDRATION"
        val channelId = "reminders"

        // 1) Ensure channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Hydration/Medication reminders"
                        enableLights(true)
                        lightColor = Color.MAGENTA
                    }
                )
            }
        }

        // 2) Deep-link to LOG screen
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", Destination.LOG.route)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            if (type == "MEDICATION") 201 else 200,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3) Build and show notification
        val title = if (type == "MEDICATION") "Medication time" else "Hydration reminder"
        val text  = if (type == "MEDICATION") "Please take your medication." else "Have a glass of water."

        val notif = NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        NotificationManagerCompat.from(context)
            .notify(if (type == "MEDICATION") 101 else 100, notif)
    }
}
