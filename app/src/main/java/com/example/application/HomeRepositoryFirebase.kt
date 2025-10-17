package com.example.application

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import com.google.firebase.firestore.Query

/**
 * HomeRepositoryFirebase
 *
 * Purpose
 * - Read the current user's recent logs from Firestore and project them into
 *   daily aggregates used by the Home dashboard (see [HomeDay]).
 * - Load a small public activity feed for the carousel component.
 *
 * Collections & fields (expected)
 * - `logs`: dateMillis (Long), moodScore (Int), temperatureCelsius (Double),
 *           startTime (String "HH:mm"), endTime (String "HH:mm"), userId (String),
 *           submittedAt (ServerTimestamp).
 * - `users`: userId/uid (String), name (String).
 *
 * Time
 * - All date comparisons use the device time zone via [zone].
 */

/**
 * PublicActivity
 *
 * Lightweight projection for the public carousel.
 *
 * @property userName      Display name of the author.
 * @property activityType  Activity label from the log entry.
 * @property submittedAt   Server timestamp when the log was saved.
 */
data class PublicActivity(
    val userName: String,
    val activityType: String,
    val submittedAt: Date
)

class HomeRepositoryFirebase {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val zone = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Loads logs for the past 7 days (inclusive of today) for [userId] and maps
     * them to [HomeDay] rows. Only days with at least one log are returned.
     *
     * Query window
     * - start: today - 6 days at local midnight
     * - end (exclusive): tomorrow at local midnight
     *
     * @return ascending list by [HomeDay.date].
     */
    suspend fun loadWeek(userId: String): List<HomeDay> {
        val today = LocalDate.now()
        val start = today.minusDays(6) // Include today and the previous 6 days
        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillisExclusive = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        // Query logs of the current user within the 7-day range
        val snap = db.collection("logs")
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("dateMillis", startMillis)
            .whereLessThan("dateMillis", endMillisExclusive)
            .get()
            .await()

        val list = snap.documents.mapNotNull { doc ->
            val dateMillis = doc.getLong("dateMillis") ?: return@mapNotNull null
            val mood = (doc.getLong("moodScore") ?: 0L).toInt()
            val temp = (doc.getDouble("temperatureCelsius") ?: 0.0)
            val startStr = doc.getString("startTime") ?: ""
            val endStr = doc.getString("endTime") ?: ""

            val date = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()

            // Duration in minutes, or 0 if invalid/missing.
            val minutes = parseMinutes(startStr, endStr)

            HomeDay(
                date = date,
                mood = mood,
                minutes = minutes,
                tempC = temp.toInt()
            )
        }

        // Only days with entries; sort ascending by date.
        return list.sortedBy { it.date }
    }

    /**
     * Loads a short list of recent public activities (excluding the current user).
     * Results are ordered by `submittedAt` descending and capped at 10.
     *
     * Note:
     * - Requires a corresponding `users` lookup to resolve display names.
     *
     * @return list for the Home carousel; empty when signed out or no data.
     */
    suspend fun loadPublicActivities(): List<PublicActivity> {
        val currentUid = auth.currentUser?.uid ?: return emptyList()
        val activities = mutableListOf<PublicActivity>()

        val logsSnapshot = db.collection("logs")
            .whereNotEqualTo("userId", currentUid)
            .orderBy("userId") // Firestore requires ordering when using whereNotEqualTo
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .await()

        val logs = logsSnapshot.toObjects(ExerciseLog::class.java)

        val userIds = logs.mapNotNull { it.userId }.distinct()
        if (userIds.isEmpty()) return emptyList()

        val usersSnapshot = db.collection("users")
            .whereIn("userId", userIds)
            .get()
            .await()

        val userNamesMap = usersSnapshot.documents.associate { doc ->
            doc.getString("userId") to doc.getString("name")
        }

        logs.forEach { log ->
            val userName = userNamesMap[log.userId] ?: "Someone"
            if (log.submittedAt != null) {
                activities.add(
                    PublicActivity(
                        userName = userName,
                        activityType = log.exerciseType,
                        submittedAt = log.submittedAt!!
                    )
                )
            }
        }

        return activities
    }

    /**
     * Parses two "HH:mm" strings and returns a non-negative duration in minutes.
     * Returns 0 when inputs are missing or cannot be parsed.
     */
    private fun parseMinutes(start: String, end: String): Int {
        return try {
            val s = java.time.LocalTime.parse(start, timeFmt)
            val e = java.time.LocalTime.parse(end, timeFmt)
            val diff = java.time.Duration.between(s, e).toMinutes()
            if (diff > 0) diff.toInt() else 0
        } catch (_: Exception) {
            0
        }
    }
}
