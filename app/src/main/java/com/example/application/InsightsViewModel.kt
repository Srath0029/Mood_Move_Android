package com.example.application

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * UI state for the Insights screen.
 *
 * @property isLoading True while data is being fetched or a listener is attaching.
 * @property insights  Seven-day series used by the charts and stats.
 * @property error     Non-null when an error occurs while loading.
 */
data class InsightsUiState(
    val isLoading: Boolean = true,
    val insights: List<DayInsight> = emptyList(),
    val error: String? = null
)

/**
 * InsightsViewModel
 *
 * Responsibilities
 * - React to FirebaseAuth state changes and (re)attach a Firestore listener.
 * - Fetch logs for the last seven days and aggregate them into [DayInsight].
 * - Expose data as lifecycle-friendly state via [InsightsUiState].
 * - Release listeners in [onCleared] to avoid leaks.
 */
class InsightsViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private var logsListener: ListenerRegistration? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    init {
        createAuthStateListener()
    }

    /**
     * Subscribes to FirebaseAuth changes; loads data on sign-in, clears on sign-out.
     */
    private fun createAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d("InsightsViewModel", "Auth state changed: User logged in (${user.uid})")
                fetchLastSevenDaysInsights(user.uid)
            } else {
                Log.d("InsightsViewModel", "Auth state changed: User logged out")
                stopListenerAndClearUI()
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    /**
     * Attaches a Firestore listener for the last seven days of logs for [userId].
     * Replaces any existing listener and drives the UI to a loading state first.
     */
    private fun fetchLastSevenDaysInsights(userId: String) {
        stopListenerAndClearUI() // Ensure the old listener is removed before attaching a new one
        _uiState.update { it.copy(isLoading = true) }

        // Window: from local midnight 6 days ago up to now (inclusive)
        val sevenDaysAgo = LocalDate.now().minusDays(6)
        val startTimestamp = sevenDaysAgo.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        logsListener = db.collection("logs")
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("dateMillis", startTimestamp)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("InsightsViewModel", "Listen failed.", e)
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load insights.") }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val logs = snapshot.toObjects(ExerciseLog::class.java)
                    // Map raw logs to seven daily points for the charts
                    val dailyInsights = processLogsIntoDailyInsights(logs)
                    _uiState.update { it.copy(isLoading = false, insights = dailyInsights) }
                }
            }
    }

    /**
     * Aggregates raw logs into a fixed seven-day series (today back to -6 days).
     * Empty days are represented with zeros for chart continuity.
     */
    private fun processLogsIntoDailyInsights(logs: List<ExerciseLog>): List<DayInsight> {
        val groupedByDate: Map<LocalDate, List<ExerciseLog>> = logs.groupBy {
            Instant.ofEpochMilli(it.dateMillis ?: 0).atZone(ZoneId.systemDefault()).toLocalDate()
        }

        val today = LocalDate.now()
        val result = mutableListOf<DayInsight>()

        // Walk back 6..0 days to fill seven points in order
        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            val logsForDay = groupedByDate[date]

            if (logsForDay.isNullOrEmpty()) {
                result.add(DayInsight(date, mood = 0, minutes = 0, tempC = 0))
            } else {
                val totalMinutes = logsForDay.sumOf { calculateDuration(it.startTime, it.endTime) }
                val averageMood = logsForDay.map { it.moodScore }.average().roundToInt()
                val tempsWithData: List<Double> = logsForDay.mapNotNull { it.temperatureCelsius }
                val averageTemp = if (tempsWithData.isNotEmpty()) tempsWithData.average().roundToInt() else 0

                result.add(DayInsight(date, mood = averageMood, minutes = totalMinutes, tempC = averageTemp))
            }
        }
        return result
    }

    /**
     * Removes the Firestore listener and resets the UI to its initial state.
     */
    private fun stopListenerAndClearUI() {
        logsListener?.remove()
        _uiState.update { InsightsUiState() }
    }

    /**
     * Parses two "HH:mm" strings and returns a non-negative duration (minutes).
     * Returns 0 on null inputs or parse errors.
     */
    private fun calculateDuration(startTimeStr: String?, endTimeStr: String?): Int {
        if (startTimeStr == null || endTimeStr == null) return 0
        return try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val startTime = LocalTime.parse(startTimeStr, formatter)
            val endTime = LocalTime.parse(endTimeStr, formatter)
            Duration.between(startTime, endTime).toMinutes().toInt()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Lifecycle cleanup: detach listeners to prevent leaks.
     */
    override fun onCleared() {
        super.onCleared()
        authStateListener?.let { auth.removeAuthStateListener(it) }
        logsListener?.remove()
        Log.d("InsightsViewModel", "ViewModel cleared and listeners removed.")
    }
}
