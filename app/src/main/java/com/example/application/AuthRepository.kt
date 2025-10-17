package com.example.application

import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * AuthRepository
 *
 * Purpose
 * - Wraps Firebase Authentication for email/password sign-up.
 * - Persists the initial user profile to Firestore at `users/{uid}`.
 * - Exposes a simple auth state (`isLoggedIn`) for UI.
 *
 * Security & data
 * - Store only fields required by features.
 * - Age/height/weight/DOB are owner-only fields; secure them via Firestore rules.
 */
object AuthRepository {

    /** Returns the current user's uid or null if signed out. */
    fun currentUserId(): String? = Firebase.auth.currentUser?.uid

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    /** Auth session state for UI observation. */
    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        // Keep session state in sync with FirebaseAuth.
        auth.addAuthStateListener { fa ->
            _isLoggedIn.value = (fa.currentUser != null)
        }
    }

    /**
     * Registers a new user and writes their profile document.
     *
     * Steps
     * 1) Create the Auth user with email/password.
     * 2) Set `displayName` in Firebase Auth.
     * 3) Upsert `users/{uid}` (merge semantics).
     *
     * @param name      Display name for Auth and Firestore.
     * @param email     Sign-in email, also stored in profile.
     * @param password  Plain password passed to Firebase Auth SDK.
     * @param age       Optional age.
     * @param gender    Optional gender label.
     * @param heightCm  Optional height in centimeters.
     * @param weightKg  Optional weight in kilograms.
     * @param dobMillis Optional date of birth (epoch millis).
     * @return          Result<Unit> with success or the thrown exception.
     */
    suspend fun registerAndSave(
        name: String,
        email: String,
        password: String,
        age: Int?,
        gender: String,
        heightCm: Int?,
        weightKg: Int?,
        dobMillis: Long?
    ): Result<Unit> = runCatching {
        // Create user
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val user = authResult.user ?: error("No FirebaseUser returned")
        val uid = user.uid

        // Set displayName
        val profile = userProfileChangeRequest { displayName = name }
        user.updateProfile(profile).await()

        // Write profile
        val doc = mapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "age" to age,
            "gender" to gender,
            "heightCm" to heightCm,
            "weightKg" to weightKg,
            "dobMillis" to dobMillis,
            "createdAt" to FieldValue.serverTimestamp(),
            "role" to "User"
        )

        db.collection("users")
            .document(uid)
            .set(doc, SetOptions.merge())
            .await()
    }
}
