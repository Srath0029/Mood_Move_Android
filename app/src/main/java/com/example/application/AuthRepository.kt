package com.example.application

import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authentication + initial profile persistence.
 *
 * Responsibility
 * - Creates a Firebase Auth user (email/password).
 * - Updates the Auth displayName for quick UI use.
 * - Persists a profile document at `users/{uid}` (merge semantics).
 *
 * Data minimization
 * - Only store fields you actually need for features.
 * - Treat age/height/weight/DOB as **sensitive**; avoid exposing them in any
 *   shared or public collection. If you later implement sharing, create a
 *   sanitized view/collection without PII.
 *
 * Firestore rules (not implemented here; required at backend)
 * - Only the authenticated owner should read/write `users/{uid}`.
 * - Deny public reads of sensitive fields.
 *
 * Lookup convenience (optional future enhancement)
 * - Consider also writing a lower-cased email field (e.g., `email_lower`) to
 *   enable case-insensitive queries when implementing "check your friend by email".
 */
object AuthRepository {

    /** Conveniently get the current uid (return null if not logged in) */

    fun currentUserId(): String? = Firebase.auth.currentUser?.uid
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    // New: Login status
    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    /**
     * Registers a user and saves their profile to Firestore.
     *
     * Flow
     * 1) Create user in Firebase Auth with email/password.
     * 2) Update display name in Firebase Auth (for UI header, etc.).
     * 3) Save profile into `users/{uid}` using merge (idempotent on re-runs).
     *
     * @param name Display name to set on the Auth user and in profile.
     * @param email User email for sign-in and profile.
     * @param password Plain password (sent to Firebase Auth SDK).
     * @param age Optional age (sensitive; keep private).
     * @param gender Optional gender label.
     * @param heightCm Optional height in centimeters (sensitive; keep private).
     * @param weightKg Optional weight in kilograms (sensitive; keep private).
     * @param dobMillis Optional date of birth in epoch millis (sensitive; keep private).
     *
     * @return [Result] wrapping Unit on success or the underlying exception on failure.
     */
    init {
        // Listening for Firebase login/logout
        auth.addAuthStateListener { fa ->
            _isLoggedIn.value = (fa.currentUser != null)
        }
    }
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
        //  Create user in Firebase Auth
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val user = authResult.user ?: error("No FirebaseUser returned")
        val uid = user.uid

        //  Update display name (KTX DSL)
        val profile = userProfileChangeRequest { displayName = name }
        user.updateProfile(profile).await()

        //  Persist profile into Firestore: users/{uid}
        //    NOTE: Keep this document private via Firestore Security Rules.
        val doc = mapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            // If you plan to allow email-lookup, also store "email_lower" = email.lowercase()
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
            .set(doc, SetOptions.merge()) // merge so re-runs don't clobber other fields
            .await()
    }
}
