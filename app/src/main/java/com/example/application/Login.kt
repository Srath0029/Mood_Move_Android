package com.example.application

import android.content.Context
import android.credentials.GetCredentialException
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


private const val WEB_CLIENT_ID = "626852858933-ljvsadsirer77dpdts9jltdd8hbu4asm.apps.googleusercontent.com"

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
fun LoginScreen(
    onLogin: (email: String, password: String) -> Unit = { _, _ -> }, // legacy (unused)
    onGoRegister: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
    onLoggedIn: () -> Unit = {} // navigate after success
) {
    // Form state
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPwd by rememberSaveable { mutableStateOf(false) }

    // UI state
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    Scaffold(
        snackbarHost = {
            SnackbarHost(snack) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                )
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Login", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPwd = !showPwd }) {
                        Icon(
                            if (showPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPwd) "Hide password" else "Show password"
                        )
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            if (loading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(16.dp))

            // ===== Email/Password Sign-in =====
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        error = "Email and password are required."
                        return@Button
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            Firebase.auth
                                .signInWithEmailAndPassword(email.trim(), password)
                                .await()
                            snack.showSnackbar("Signed in successfully")
                            delay(800)
                            onLoggedIn()
                        } catch (e: Exception) {
                            error = when (e) {
                                is FirebaseAuthInvalidUserException ->
                                    "No account found for this email."
                                is FirebaseAuthInvalidCredentialsException ->
                                    "Invalid email or password."
                                else -> e.localizedMessage ?: "Sign-in failed."
                            }
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sign in") }

            Spacer(Modifier.height(10.dp))

            // ===== Google Sign-in (Credential Manager + Firebase) =====
            OutlinedButton(
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val idToken = getGoogleIdToken(ctx)
                            if (idToken == null) {
                                error = "No Google account/credential found. Add a Google account on this device and try again."
                            } else {
                                val credential = GoogleAuthProvider.getCredential(idToken, null)
                                val result = Firebase.auth.signInWithCredential(credential).await()
                                val user = result.user

                                user?.let {
                                    val doc = mapOf(
                                        "uid" to it.uid,
                                        "name" to (it.displayName ?: ""),
                                        "email" to (it.email ?: ""),
                                        "role" to "User",
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )
                                    Firebase.firestore.collection("users")
                                        .document(it.uid)
                                        .set(doc, SetOptions.merge())
                                        .await()
                                }

                                snack.showSnackbar("Signed in with Google")
                                delay(800)
                                onLoggedIn()
                            }
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "Google sign-in failed."
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Continue with Google") }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onForgotPassword) { Text("Forgot password?") }
                TextButton(onClick = onGoRegister) { Text("No account? Register") }
            }
        }
    }
}

/** Credential Manager: get Google ID Token (tries authorized accounts, falls back to any account). */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Throws(Exception::class)
suspend fun getGoogleIdToken(context: Context): String? {
    val cm = CredentialManager.create(context)
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(WEB_CLIENT_ID)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()

    return try {
        val result = cm.getCredential(context, request)
        val cred = result.credential
        if (cred is CustomCredential &&
            cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(cred.data).idToken
        } else {
            Log.w("GSI", "No Google ID token credential returned: ${cred::class.java.name}")
            null
        }
    } catch (e: androidx.credentials.exceptions.GetCredentialException) {

        val reason = when (e) {
            is androidx.credentials.exceptions.NoCredentialException -> "NO_CREDENTIAL"
            is androidx.credentials.exceptions.GetCredentialProviderConfigurationException -> "PROVIDER_CONFIG_ERROR"
            is androidx.credentials.exceptions.GetCredentialInterruptedException -> "INTERRUPTED"
            is androidx.credentials.exceptions.GetCredentialUnknownException -> "UNKNOWN"
            else -> e::class.simpleName ?: "GetCredentialException"
        }
        Log.e("GSI", "GetCredential failed: $reason - ${e.message}", e)
        null
    } catch (e: Exception) {
        Log.e("GSI", "Unexpected error: ${e.message}", e)
        null
    }

}