// HistoryViewModel.kt
package com.example.application

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * UI state for the History screen.
 *
 * @property isLoading True while data is being fetched or the listener is attaching.
 * @property logs      View-ready rows mapped from Firestore documents.
 * @property error     Terminal or transient error message, if any.
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val logs: List<LogEntry> = emptyList(),
    val error: String? = null
)

/**
 * HistoryViewModel
 *
 * Responsibilities
 * - Observe FirebaseAuth to react to sign-in/sign-out and (re)attach a Firestore listener.
 * - Stream user-scoped logs from Firestore and expose them as [HistoryUiState].
 * - Provide basic mutations (delete/update) and duration calculation.
 * - Release listeners in [onCleared] to avoid leaks.
 */
class HistoryViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var logsListener: ListenerRegistration? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    init {
        // Defer data loading to auth state; attach on creation so the VM reacts immediately.
        createAuthStateListener()
    }

    /**
     * Subscribes to FirebaseAuth state changes and starts/stops the logs listener accordingly.
     * Keeps exactly one active Firestore listener scoped to the current user.
     */
    private fun createAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d("HistoryViewModel", "Auth state changed: User logged in (${user.uid})")
                startLogsListener(user.uid)
            } else {
                Log.d("HistoryViewModel", "Auth state changed: User logged out")
                stopLogsListener()
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    /**
     * Starts a Firestore snapshot listener for the given user.
     * Removes any existing listener before attaching a new one.
     */
    private fun startLogsListener(userId: String) {
        // Prevent duplicate listeners when auth state changes quickly.
        logsListener?.remove()

        _uiState.update { it.copy(isLoading = true) }

        logsListener = db.collection("logs")
            .whereEqualTo("userId", userId)
            .orderBy("dateMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("HistoryViewModel", "Listen failed.", e)
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load data.") }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val logEntries = snapshot.documents.mapNotNull { doc ->
                        val exerciseLog = doc.toObject(ExerciseLog::class.java)
                        if (exerciseLog == null) return@mapNotNull null
                        val minutes = calculateDuration(exerciseLog.startTime, exerciseLog.endTime)
                        LogEntry(
                            id = doc.id,
                            dateMillis = exerciseLog.dateMillis ?: 0L,
                            mood = exerciseLog.moodScore,
                            type = exerciseLog.exerciseType,
                            minutes = minutes,
                            startTime = exerciseLog.startTime ?: "",
                            endTime = exerciseLog.endTime ?: ""
                        )
                    }
                    _uiState.update { it.copy(isLoading = false, logs = logEntries) }
                }
            }
    }

    /**
     * Stops the Firestore snapshot listener and clears the list in UI state.
     * Called when the user signs out or when the VM needs to reset.
     */
    private fun stopLogsListener() {
        logsListener?.remove()
        _uiState.update { HistoryUiState(isLoading = false, logs = emptyList()) }
    }

    /**
     * Deletes a log document by id.
     */
    fun deleteLog(logId: String) {
        db.collection("logs").document(logId).delete()
            .addOnSuccessListener { Log.d("HistoryViewModel", "DocumentSnapshot successfully deleted!") }
            .addOnFailureListener { e -> Log.w("HistoryViewModel", "Error deleting document", e) }
    }

    /**
     * Updates selected fields on a log document.
     */
    fun updateLog(
        logId: String,
        newDateMillis: Long,
        newMood: Int,
        newType: String,
        newStartTime: String,
        newEndTime: String
    ) {
        val updates = mapOf(
            "dateMillis" to newDateMillis,
            "moodScore" to newMood,
            "exerciseType" to newType,
            "startTime" to newStartTime,
            "endTime" to newEndTime
        )

        db.collection("logs").document(logId).update(updates)
            .addOnSuccessListener { Log.d("HistoryViewModel", "DocumentSnapshot successfully updated!") }
            .addOnFailureListener { e -> Log.w("HistoryViewModel", "Error updating document", e) }
    }

    /**
     * Converts "HH:mm" strings to minutes between start and end.
     * Returns 0 on parse errors or null inputs.
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
     * Lifecycle cleanup: remove listeners to avoid memory leaks.
     */
    override fun onCleared() {
        super.onCleared()
        authStateListener?.let { auth.removeAuthStateListener(it) }
        logsListener?.remove()
        Log.d("HistoryViewModel", "ViewModel cleared and listeners removed.")
    }
}
