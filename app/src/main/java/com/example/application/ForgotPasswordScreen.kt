package com.example.application

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ForgotPasswordScreen (email-only reset).
 *
 * - Validates email.
 * - Sends reset email with sendPasswordResetEmail (no user-enumeration).
 *
 * NOTE: onSubmit kept for backward compatibility but not used.
 */
@Composable
fun ForgotPasswordScreen(
    onSubmit: (email: String, code: String, newPassword: String) -> Unit = { _, _, _ -> }, // unused
    onBackToLogin: () -> Unit = {}
) {
    // ---- Form state ----
    var email by rememberSaveable { mutableStateOf("") }

    // ---- Validation ----
    fun isEmailValid(s: String) =
        android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
    val emailErr = email.isNotEmpty() && !isEmailValid(email)
    val formValid = isEmailValid(email)

    // ---- UI state ----
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snack) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF2E7D32), // green
                    contentColor = Color.White
                )
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Forgot Password", style = MaterialTheme.typography.headlineSmall)

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (errorMsg != null) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMsg = null },
                label = { Text("Email") },
                singleLine = true,
                isError = emailErr,
                supportingText = { Text(if (emailErr) "Please enter a valid email" else " ") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Send reset email
            Button(
                enabled = formValid && !loading,
                onClick = {
                    loading = true
                    errorMsg = null
                    scope.launch {
                        try {
                            Firebase.auth
                                .sendPasswordResetEmail(email.trim())
                                .await()

                            // Neutral message to avoid user-enumeration
                            snack.showSnackbar("If an account exists for this email, a reset link has been sent.")
                            delay(1200)
                            onBackToLogin()
                        } catch (e: Exception) {
                            errorMsg = e.localizedMessage ?: "Failed to send reset email."
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send reset email") }

            TextButton(onClick = onBackToLogin, modifier = Modifier.align(Alignment.End)) {
                Text("Back to Login")
            }
        }
    }
}
