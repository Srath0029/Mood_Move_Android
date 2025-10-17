package com.example.application

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * Types of reminders and their unique PendingIntent request codes.
 *
 * Rationale:
 * - Each type has a distinct request code to prevent PendingIntent collisions
 *   so exact and repeating alarms can coexist without overwriting each other.
 */
enum class ReminderType(val requestCode: Int) {
    HYDRATION_EXACT(1000),       // one-shot exact (next occurrence)
    MEDICATION_EXACT(1001),      // one-shot exact (next occurrence)
    MEDICATION_REPEAT(1002),     // daily inexact repeating (medication)
    HYDRATION_REPEAT(1003)       // daily inexact repeating (hydration)
}

/**
 * Schedules, repeats, and cancels hydration/medication reminders using [AlarmManager].
 *
 * Design:
 * - For each category we schedule:
 *   1) A one-shot EXACT alarm for the next occurrence (so today's reminder is precise).
 *   2) An INEXACT daily repeating alarm for subsequent days (battery-friendly).
 *
 * API level notes:
 * - On Android 12 (API 31)+, exact alarms may be gated by AppOps (SCHEDULE_EXACT_ALARM).
 *   We guard with [AlarmManager.canScheduleExactAlarms()] and fall back to inexact.
 *
 * Persistence:
 * - setInexactRepeating survives reboots on some OEMs, but **do not rely on it**.
 *   For robust behavior, reschedule on BOOT_COMPLETED (via a receiver) in your app.
 *
 * DST/clock changes:
 * - Inexact repeating does not guarantee a fixed wall-clock time around DST shifts.
 *   If you need strict wall-clock times, consider chaining daily one-shot exact alarms.
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    /**
     * Creates a broadcast [PendingIntent] for the given [ReminderType].
     *
     * Why immutable:
     * - FLAG_IMMUTABLE prevents the intent from being altered by other apps.
     * - FLAG_UPDATE_CURRENT updates extras when rescheduling the same requestCode.
     */
    private fun pending(context: Context, type: ReminderType): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).putExtra("type", type.name)
        return PendingIntent.getBroadcast(
            context, type.requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Computes the next trigger time (in millis) for a given [hour24]:[minute]
     * on the device's current timezone. If the time has already passed today,
     * rolls forward to the same time tomorrow.
     */
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

    /** ---------------- Hydration: exact once + daily inexact thereafter ---------------- */

    /**
     * Schedules a hydration reminder:
     * - An exact one-shot at the next occurrence (today or tomorrow),
     * - Plus an inexact repeating alarm every day for subsequent reminders.
     *
     * Battery policy:
     * - If exact alarms are not allowed (API 31+), logs and continues with inexact only.
     */
    fun scheduleInexactDaily(context: Context, hour24: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextTriggerAt(hour24, minute)

        var exactOk = true
        // On Android 12+ the app may not be allowed to post exact alarms.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            exactOk = false
            Log.w(TAG, "Hydration exact not allowed; falling back to inexact only.")
        } else {
            // Try to schedule today's exact heads-up reminder for better UX.
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

        // Always schedule the daily inexact repeating for follow-up days using a distinct PI.
        val piRepeat = pending(context, ReminderType.HYDRATION_REPEAT)
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            at + AlarmManager.INTERVAL_DAY,   // first repeat is "tomorrow" at around the same time
            AlarmManager.INTERVAL_DAY,
            piRepeat
        )
        Log.d(TAG, "Hydration REPEAT first at=${at + AlarmManager.INTERVAL_DAY} (inexact)")
    }

    /** ---------------- Medication: exact once + daily inexact thereafter ---------------- */

    /**
     * Schedules a medication reminder:
     * - An exact one-shot at the next occurrence (today or tomorrow),
     * - Plus an inexact repeating alarm every day.
     *
     * @return true if the exact alarm was scheduled, false if exact is disallowed or failed.
     */
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

        // Inexact repeating for ongoing daily reminders; uses a distinct request code.
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

    /** ---------------- Cancel helpers ---------------- */

    /**
     * Cancels both exact and repeating alarms for the provided [type] category.
     *
     * Why both:
     * - Each category uses two different PendingIntents (EXACT + REPEAT).
     *   We cancel the pair to fully stop the reminder stream.
     */
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

