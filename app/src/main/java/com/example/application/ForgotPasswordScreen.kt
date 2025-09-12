package com.example.application

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp

/**
 * ForgotPasswordScreen
 *
 * UI for resetting a password via email + verification code.
 * The form collects:
 *  - Email address (validated)
 *  - 6-digit verification code (numeric)
 *  - New password (>= 8 chars and includes a digit)
 *  - Confirm new password (must match)
 *
 * @param onSubmit            invoked when all fields are valid and user taps Submit
 * @param onBackToLogin       invoked when user taps "Back to Login"
 */
@Composable
fun ForgotPasswordScreen(
    onSubmit: (email: String, code: String, newPassword: String) -> Unit = { _, _, _ -> },
    onBackToLogin: () -> Unit = {}
) {
    // ---------- Form state ----------
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }           // 6 digits
    var newPwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }     // toggle new password visibility
    var showConfirm by remember { mutableStateOf(false) } // toggle confirm visibility
    var attempted by remember { mutableStateOf(false) }   // set after first submit attempt

    // ---------- Validation helpers ----------
    fun isEmailValid(s: String) =
        android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
    fun isPwdValid(s: String) = s.length >= 8 && s.any(Char::isDigit)
    fun isCodeValid(s: String) = s.length == 6 && s.all(Char::isDigit)

    // ---------- Validity flags ----------
    val emailValid   = isEmailValid(email)
    val codeValid    = isCodeValid(code)
    val newPwdValid  = isPwdValid(newPwd)
    val confirmValid = confirm == newPwd && newPwdValid

    // Show errors early: after first submit or once user has typed in a field
    fun showErr(typed: Boolean) = attempted || typed

    // ---------- Error states for inline messages ----------
    val emailErr   = showErr(email.isNotEmpty())   && !emailValid
    val codeErr    = showErr(code.isNotEmpty())    && !codeValid
    val newPwdErr  = showErr(newPwd.isNotEmpty())  && !newPwdValid
    val confirmErr = showErr(confirm.isNotEmpty()) && !confirmValid

    // Entire form valid only if all fields pass validation
    val formValid = emailValid && codeValid && newPwdValid && confirmValid

    // ---------- Layout ----------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // allow content to scroll under smaller screens/keyboard
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Forgot Password", style = MaterialTheme.typography.headlineSmall)

        // Email input with inline validation
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            isError = emailErr,
            supportingText = {
                Text(if (emailErr) "Please enter a valid email" else " ")
            },
            modifier = Modifier.fillMaxWidth()
        )

        // 6-digit verification code (numeric only)
        OutlinedTextField(
            value = code,
            onValueChange = { input -> code = input.filter(Char::isDigit).take(6) },
            label = { Text("Verification code") },
            singleLine = true,
            isError = codeErr,
            supportingText = {
                Text(if (codeErr) "Enter the 6-digit code" else "${code.length}/6 digits")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // New password with show/hide toggle and strength rule
        OutlinedTextField(
            value = newPwd,
            onValueChange = { newPwd = it },
            label = { Text("New password") },
            singleLine = true,
            visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showNew = !showNew }) {
                    Icon(
                        if (showNew) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showNew) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            isError = newPwdErr,
            supportingText = {
                Text(if (newPwdErr) "Min 8 characters and include a number" else "At least 8 chars + a number")
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Confirm password with show/hide toggle and match rule
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm new password") },
            singleLine = true,
            visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showConfirm = !showConfirm }) {
                    Icon(
                        if (showConfirm) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showConfirm) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            isError = confirmErr,
            supportingText = {
                Text(if (confirmErr) "Passwords do not match" else " ")
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Submit only when form is valid; sets 'attempted' to reveal errors if pressed too early
        Button(
            enabled = formValid,
            onClick = {
                attempted = true
                if (formValid) onSubmit(email.trim(), code.trim(), newPwd)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Submit") }

        // Optional back link to return to the Login screen
        TextButton(onClick = onBackToLogin, modifier = Modifier.align(Alignment.End)) {
            Text("Back to Login")
        }
    }
}
