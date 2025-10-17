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

// InsightsScreen 的 UI 状态
data class InsightsUiState(
    val isLoading: Boolean = true,
    val insights: List<DayInsight> = emptyList(),
    val error: String? = null
)

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

    private fun createAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // 用户登录后，开始监听最近七天的数据
                Log.d("InsightsViewModel", "Auth state changed: User logged in (${user.uid})")
                fetchLastSevenDaysInsights(user.uid)
            } else {
                // 用户退出后，清空数据
                Log.d("InsightsViewModel", "Auth state changed: User logged out")
                stopListenerAndClearUI()
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    private fun fetchLastSevenDaysInsights(userId: String) {
        stopListenerAndClearUI() // 开始前先停止旧的监听
        _uiState.update { it.copy(isLoading = true) }

        // 1. 计算七天前的起始时间戳
        val sevenDaysAgo = LocalDate.now().minusDays(6)
        val startTimestamp = sevenDaysAgo.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 2. 查询该用户从七天前到现在的全部日志
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
                    // 3. 将原始日志处理成每日洞察数据
                    val dailyInsights = processLogsIntoDailyInsights(logs)
                    _uiState.update { it.copy(isLoading = false, insights = dailyInsights) }
                }
            }
    }

    private fun processLogsIntoDailyInsights(logs: List<ExerciseLog>): List<DayInsight> {
        val groupedByDate: Map<LocalDate, List<ExerciseLog>> = logs.groupBy {
            Instant.ofEpochMilli(it.dateMillis ?: 0).atZone(ZoneId.systemDefault()).toLocalDate()
        }

        val today = LocalDate.now()
        val result = mutableListOf<DayInsight>()

        // 遍历最近七天的每一天
        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            val logsForDay = groupedByDate[date]

            if (logsForDay.isNullOrEmpty()) {
                // 如果当天没有日志，添加一条默认数据
                result.add(DayInsight(date, mood = 0, minutes = 0, tempC = 0))
            } else {
                // 如果当天有日志，计算平均值和总和
                val totalMinutes = logsForDay.sumOf { calculateDuration(it.startTime, it.endTime) }
                val averageMood = logsForDay.map { it.moodScore }.average().roundToInt()

                // --- 已修正：使用正确的字段名 temperatureCelsius ---
                val tempsWithData: List<Double> = logsForDay.mapNotNull { it.temperatureCelsius }
                val averageTemp = if (tempsWithData.isNotEmpty()) tempsWithData.average().roundToInt() else 0

                result.add(DayInsight(date, mood = averageMood, minutes = totalMinutes, tempC = averageTemp))
            }
        }
        return result
    }

    private fun stopListenerAndClearUI() {
        logsListener?.remove()
        _uiState.update { InsightsUiState() } // 重置UI状态
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

    override fun onCleared() {
        super.onCleared()
        authStateListener?.let { auth.removeAuthStateListener(it) }
        logsListener?.remove()
        Log.d("InsightsViewModel", "ViewModel cleared and listeners removed.")
    }
}