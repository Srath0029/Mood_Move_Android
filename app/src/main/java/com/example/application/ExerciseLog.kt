package com.example.application

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * ExerciseLog
 *
 * Immutable model for a single exercise entry stored in Firestore.
 *
 * Storage & access
 * - Intended for `logs` (owner-scoped). If creating a shared/public view,
 *   write a sanitized copy that excludes identifiers and precise location.
 *
 * Timestamps
 * - [submittedAt] is set by Firestore via @ServerTimestamp on create.
 *
 * Privacy
 * - [userEmail], [userId] are identifiers; keep reads owner-only.
 * - [location] is sensitive; avoid exposing outside the owner context.
 *
 * Units & formats
 * - [dateMillis]: UTC epoch millis for the activity date (midnight-normalized if applicable).
 * - [startTime]/[endTime]: 24-hour "HH:mm" string if used in UI.
 * - [temperatureCelsius]: ambient temperature in °C when recorded (nullable).
 *
 * Defaults
 * - Strings default to empty, numbers to zero; nullable fields omitted when unknown.
 *
 * @property submittedAt Firestore server time when the entry was saved.
 * @property userEmail   Email of the owner (identifier; optional).
 * @property userId      UID of the owner in Firebase Auth.
 * @property dateMillis  Activity date in epoch millis.
 * @property startTime   Start time, "HH:mm".
 * @property endTime     End time, "HH:mm".
 * @property exerciseType Activity type label (e.g., "Running").
 * @property moodScore    Self-reported mood score (0–10 or chosen scale).
 * @property intensity    Intensity label (e.g., "Low", "Medium", "High").
 * @property location     Optional location (lat/lng).
 * @property temperatureCelsius Optional ambient temperature (°C).
 */
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
