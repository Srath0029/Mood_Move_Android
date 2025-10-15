package com.example.application

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

enum class ReminderType(val requestCode: Int) {
    HYDRATION_EXACT(1000),       // one-shot exact (next occurrence)
    MEDICATION_EXACT(1001),      // one-shot exact (next occurrence)
    MEDICATION_REPEAT(1002),     // daily inexact repeating (medication)
    HYDRATION_REPEAT(1003)       // daily inexact repeating (hydration)
}

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

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

    /** HYDRATION */
    fun scheduleInexactDaily(context: Context, hour24: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextTriggerAt(hour24, minute)


        var exactOk = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            exactOk = false
            Log.w(TAG, "Hydration exact not allowed; falling back to inexact only.")
        } else {
            try {
                val piExact = pending(context, ReminderType.HYDRATION_EXACT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, piExact)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, at, piExact)
                }
                Log.d(TAG, "Hydration EXACT scheduled at=$at")
            } catch (se: SecurityException) {
                exactOk = false
                Log.e(TAG, "Hydration exact denied: ${se.localizedMessage}")
            }
        }

        // Always schedule the daily inexact repeat for subsequent days (separate PI!)
        val piRepeat = pending(context, ReminderType.HYDRATION_REPEAT)
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            at + AlarmManager.INTERVAL_DAY,   // first repeat = tomorrow
            AlarmManager.INTERVAL_DAY,
            piRepeat
        )
        Log.d(TAG, "Hydration REPEAT first at=${at + AlarmManager.INTERVAL_DAY} (inexact)")
    }

    /** MEDICATION */
    fun scheduleExactDaily(context: Context, hour24: Int, minute: Int): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextTriggerAt(hour24, minute)

        var exactOk = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            Log.w(TAG, "Medication exact not allowed.")
            exactOk = false
        } else {
            try {
                val piExact = pending(context, ReminderType.MEDICATION_EXACT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, piExact)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, at, piExact)
                }
                Log.d(TAG, "Medication EXACT scheduled at=$at")
            } catch (se: SecurityException) {
                Log.e(TAG, "Medication exact denied: ${se.localizedMessage}")
                exactOk = false
            }
        }

        val piRepeat = pending(context, ReminderType.MEDICATION_REPEAT)
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            at + AlarmManager.INTERVAL_DAY,
            AlarmManager.INTERVAL_DAY,
            piRepeat
        )
        Log.d(TAG, "Medication REPEAT first at=${at + AlarmManager.INTERVAL_DAY} (inexact)")

        return exactOk
    }

    fun cancel(context: Context, type: ReminderType) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        when (type) {
            ReminderType.HYDRATION_EXACT, ReminderType.HYDRATION_REPEAT -> {
                am.cancel(pending(context, ReminderType.HYDRATION_EXACT))
                am.cancel(pending(context, ReminderType.HYDRATION_REPEAT))
            }
            ReminderType.MEDICATION_EXACT, ReminderType.MEDICATION_REPEAT -> {
                am.cancel(pending(context, ReminderType.MEDICATION_EXACT))
                am.cancel(pending(context, ReminderType.MEDICATION_REPEAT))
            }
        }
        Log.d(TAG, "Cancelled for $type")
    }
}
