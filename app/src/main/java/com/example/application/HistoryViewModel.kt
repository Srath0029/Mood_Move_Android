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
        // 2. ViewModel 创建时，不再直接加载数据，而是开始监听用户的登录状态
        createAuthStateListener()
    }

    private fun createAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // 如果用户登录了，就开始监听该用户的数据库记录
                Log.d("HistoryViewModel", "Auth state changed: User logged in (${user.uid})")
                startLogsListener(user.uid)
            } else {
                // 如果用户退出了，就停止所有监听并清空UI
                Log.d("HistoryViewModel", "Auth state changed: User logged out")
                stopLogsListener()
            }
        }
        // 将监听器附加到 auth 实例上
        auth.addAuthStateListener(authStateListener!!)
    }

    private fun startLogsListener(userId: String) {
        // 在启动新监听之前，确保旧的监听已经被移除，防止重复监听
        logsListener?.remove()

        _uiState.update { it.copy(isLoading = true) } // 开始加载数据，显示加载动画

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
        // 停止数据库监听
        logsListener?.remove()
        // 将UI状态重置为“未登录”状态
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

    // 当 ViewModel 被销毁时，这个方法会被自动调用
    override fun onCleared() {
        super.onCleared()
        // 必须在这里移除所有监听器，防止内存泄漏
        authStateListener?.let { auth.removeAuthStateListener(it) }
        logsListener?.remove()
        Log.d("HistoryViewModel", "ViewModel cleared and listeners removed.")
    }
}