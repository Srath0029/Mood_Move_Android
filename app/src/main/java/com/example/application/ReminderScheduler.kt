package com.example.application

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

enum class ReminderType(val requestCode: Int) { HYDRATION(1000), MEDICATION(1001) }

object ReminderScheduler {

    private fun pending(context: Context, type: ReminderType): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).putExtra("type", type.name)
        return PendingIntent.getBroadcast(
            context, type.requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerAt(hour24: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** Inexact daily (battery-friendly), e.g. hydration */
    fun scheduleInexactDaily(context: Context, hour24: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextTriggerAt(hour24, minute)
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, pending(context, ReminderType.HYDRATION)
        )
    }

    /** Exact daily (time-sensitive), e.g. medication */
    fun scheduleExactDaily(context: Context, hour24: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextTriggerAt(hour24, minute)
        val pi = pending(context, ReminderType.MEDICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, at, pi)
        }
        // Optionally also set a repeating one for subsequent days:
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at + AlarmManager.INTERVAL_DAY,
            AlarmManager.INTERVAL_DAY, pi)
    }

    fun cancel(context: Context, type: ReminderType) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, type))
    }
}
