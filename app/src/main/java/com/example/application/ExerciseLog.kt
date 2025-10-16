// ExerciseLog.kt
package com.example.application

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ExerciseLog(
    @ServerTimestamp
    val submittedAt: Date? = null,
    val userEmail: String? = null,
    val userId: String? = null,


    val dateMillis: Long? = null,
    val startTime: String? = null,
    val endTime: String? = null,

    val exerciseType: String = "",
    val moodScore: Int = 0,
    val intensity: String = "",

    val location: GeoPoint? = null,
    val temperatureCelsius: Double? = null
)