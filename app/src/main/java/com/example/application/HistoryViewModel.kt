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

data class HistoryUiState(
    val isLoading: Boolean = true,
    val logs: List<LogEntry> = emptyList(),
    val error: String? = null
)

class HistoryViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()


    private var logsListener: ListenerRegistration? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    init {
        // 2. When ViewModel is created, it no longer loads data directly, but starts monitoring the user's login status
        createAuthStateListener()
    }

    private fun createAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // If the user is logged in, start monitoring the user's database records
                Log.d("HistoryViewModel", "Auth state changed: User logged in (${user.uid})")
                startLogsListener(user.uid)
            } else {
                // If the user exits, stop all monitoring and clear the UI
                Log.d("HistoryViewModel", "Auth state changed: User logged out")
                stopLogsListener()
            }
        }
        // Attach the listener to the auth instance
        auth.addAuthStateListener(authStateListener!!)
    }

    private fun startLogsListener(userId: String) {
        // Before starting a new monitor, make sure the old monitor has been removed to prevent duplicate monitoring
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

    private fun stopLogsListener() {
        // Stop database monitoring
        logsListener?.remove()
        // Reset the UI state to "not logged in"
        _uiState.update { HistoryUiState(isLoading = false, logs = emptyList()) }
    }

    fun deleteLog(logId: String) {
        db.collection("logs").document(logId).delete()
            .addOnSuccessListener { Log.d("HistoryViewModel", "DocumentSnapshot successfully deleted!") }
            .addOnFailureListener { e -> Log.w("HistoryViewModel", "Error deleting document", e) }
    }

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

    // This method will be called automatically when the ViewModel is destroyed
    override fun onCleared() {
        super.onCleared()
        // All listeners must be removed here to prevent memory leaks
        authStateListener?.let { auth.removeAuthStateListener(it) }
        logsListener?.remove()
        Log.d("HistoryViewModel", "ViewModel cleared and listeners removed.")
    }
}