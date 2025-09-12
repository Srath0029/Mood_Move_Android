package com.example.application

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp

/**
 * LoginScreen
 *
 * Simple sign-in form with:
 * - Email input
 * - Password input with show/hide toggle
 * - Inline error message when required fields are empty
 * - Links to "Forgot password?" and "Register"
 *
 * @param onLogin          callback invoked with (email, password) when Sign in is pressed
 * @param onGoRegister     navigate to the Register screen
 * @param onForgotPassword navigate to the Forgot Password screen
 */
@Composable
fun LoginScreen(
    onLogin: (email: String, password: String) -> Unit = { _, _ -> },
    onGoRegister: () -> Unit = {},
    onForgotPassword: () -> Unit = {}
) {
    // Form state
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Controls whether the password text is visible
    var showPwd by rememberSaveable { mutableStateOf(false) }

    // One-line error message shown below inputs (e.g., required fields)
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text("Login", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // Email input
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                error = null // clear previous error while typing
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Password input with show/hide trailing icon
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                error = null // clear previous error while typing
            },
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
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Inline error text (shown only when error is set)
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))

        // Sign in button: validates non-empty fields, then invokes onLogin
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    error = "Email and password are required."
                } else {
                    onLogin(email.trim(), password)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sign in") }

        // Link row: left = Forgot password, right = Register
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onForgotPassword) {
                Text("Forgot password?")
            }
            TextButton(onClick = onGoRegister) {
                Text("No account? Register")
            }
        }
    }
}
