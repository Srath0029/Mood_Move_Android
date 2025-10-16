package com.example.application

import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object AuthRepository {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

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
        // 1) Create user in Firebase Auth
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val user = authResult.user ?: error("No FirebaseUser returned")
        val uid = user.uid

        // 2) Update display name (KTX DSL)
        val profile = userProfileChangeRequest { displayName = name }
        user.updateProfile(profile).await()

        // 3) Persist profile into Firestore: users/{uid}
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
