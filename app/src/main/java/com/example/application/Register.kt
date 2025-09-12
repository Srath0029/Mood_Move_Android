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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId

/**
 * RegisterScreen
 *
 * Sign-up form that collects basic credentials and optional profile fields.
 * Features:
 *  - Password + confirm with show/hide toggles
 *  - Gender dropdown, numeric ranges for age/height/weight
 *  - Date of Birth picker
 *  - Early validation with inline messages and gated submit
 *
 * @param onRegister callback invoked when validation passes (returns all collected values)
 * @param onGoLogin  navigate back to the Login screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegister: (
        name: String, email: String, password: String,
        age: Int?, gender: String, heightCm: Int?, weightKg: Int?, dobMillis: Long?
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onGoLogin: () -> Unit = {}
) {
    // ---------------- Required credential fields ----------------
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    // Show/hide toggles for password fields
    var showPwd by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }

    // ---------------- Optional profile fields ----------------
    var ageText by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("Female") }
    var heightText by rememberSaveable { mutableStateOf("") }
    var weightText by rememberSaveable { mutableStateOf("") }

    // Gender dropdown state
    val genderOptions = listOf("Female", "Male", "Other")
    var genderExpanded by remember { mutableStateOf(false) }

    // Date of birth picker state
    var dobOpen by remember { mutableStateOf(false) }
    var dobMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val dobState = rememberDatePickerState(initialSelectedDateMillis = dobMillis)
    val dateFmt = remember {
        java.time.format.DateTimeFormatter.ofPattern(
            "yyyy-MM-dd",
            java.util.Locale.getDefault()
        )
    }
    // Formats the DOB epoch millis for display
    fun fmt(ms: Long?) = ms?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
    } ?: "Not set"

    // ---------------- Early validation helpers ----------------
    var attemptedSubmit by rememberSaveable { mutableStateOf(false) }
    // Decide when to show an error: after submit or once the field has any input
    fun shouldShow(showWhenTyped: Boolean, value: String) =
        attemptedSubmit || (showWhenTyped && value.isNotEmpty())

    // Basic validators
    fun validEmail(s: String) =
        android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
    fun validPassword(s: String) =
        s.length >= 8 && s.any(Char::isDigit)

    // Parse optional numeric field and validate range
    fun parseIntOrNullInRange(txt: String, min: Int, max: Int): Pair<Int?, String?> {
        if (txt.isBlank()) return null to null
        val v = txt.toIntOrNull() ?: return null to "Numbers only"
        if (v !in min..max) return null to "Must be $min–$max"
        return v to null
    }

    // Field error messages (null means no error)
    val nameError    = if (shouldShow(true, name) && name.isBlank()) "Name is required" else null
    val emailError   = if (email.isNotEmpty() && !validEmail(email)) "Invalid email address" else null
    val pwdError     = if (password.isNotEmpty() && !validPassword(password)) "Min 8 chars incl. a number" else null
    val confirmError = if (confirm.isNotEmpty() && confirm != password) "Passwords do not match" else null

    // Optional numeric validations
    val (ageVal, ageErr)       = parseIntOrNullInRange(ageText, 5, 120)
    val (heightVal, heightErr) = parseIntOrNullInRange(heightText, 80, 250)
    val (weightVal, weightErr) = parseIntOrNullInRange(weightText, 20, 250)

    // ---------------- Layout: vertical form ----------------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // allows scrolling on small screens
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Register", style = MaterialTheme.typography.headlineSmall)

        // Full name (required)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full name") },
            singleLine = true,
            isError = nameError != null,
            supportingText = { if (nameError != null) Text(nameError) },
            modifier = Modifier.fillMaxWidth()
        )

        // Email (required)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            isError = emailError != null,
            supportingText = {
                Text(emailError ?: "Use a valid email (e.g., name@domain.com)")
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Password (required) with show/hide toggle
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
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
                imeAction = ImeAction.Next
            ),
            isError = pwdError != null,
            supportingText = {
                Text(pwdError ?: "At least 8 characters and include a number")
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Confirm password (required) with show/hide toggle
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm password") },
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
                imeAction = ImeAction.Next
            ),
            isError = confirmError != null,
            supportingText = { if (confirmError != null) Text(confirmError) },
            modifier = Modifier.fillMaxWidth()
        )

        // Age + Gender row (logically related fields grouped on one row)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Age (optional; numeric with range)
            OutlinedTextField(
                value = ageText,
                onValueChange = { ageText = it.filter(Char::isDigit) },
                label = { Text("Age") },
                singleLine = true,
                isError = ageErr != null,
                supportingText = { if (ageErr != null) Text(ageErr) },
                modifier = Modifier.weight(1f)
            )

            // Gender (dropdown) — controlled choice to reduce errors
            Column(Modifier.weight(2f)) {
                ExposedDropdownMenuBox(
                    expanded = genderExpanded,
                    onExpandedChange = { genderExpanded = !genderExpanded }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = true,
                        value = gender,
                        onValueChange = {},
                        label = { Text("Gender") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded)
                        }
                    )
                    DropdownMenu(
                        expanded = genderExpanded,
                        onDismissRequest = { genderExpanded = false }
                    ) {
                        genderOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    gender = option
                                    genderExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Height + Weight row (optional; numeric with range)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it.filter(Char::isDigit) },
                label = { Text("Height (cm)") },
                singleLine = true,
                isError = heightErr != null,
                supportingText = { if (heightErr != null) Text(heightErr) },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it.filter(Char::isDigit) },
                label = { Text("Weight (kg)") },
                singleLine = true,
                isError = weightErr != null,
                supportingText = { if (weightErr != null) Text(weightErr) },
                modifier = Modifier.weight(1f)
            )
        }

        // Date of Birth actions
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { dobOpen = true }, modifier = Modifier.weight(1f)) {
                Text("Date of birth: ${fmt(dobMillis)}")
            }
            OutlinedButton(onClick = { dobMillis = null }, modifier = Modifier.weight(1f)) {
                Text("Clear DOB")
            }
        }

        // Submit: validate required + optional ranges before invoking callback
        Button(
            onClick = {
                attemptedSubmit = true
                val requiredOk = nameError == null && emailError == null &&
                        pwdError == null && confirmError == null &&
                        name.isNotBlank() && email.isNotBlank() &&
                        password.isNotBlank() && confirm.isNotBlank()
                val optionalOk = listOf(ageErr, heightErr, weightErr).all { it == null }
                if (requiredOk && optionalOk) {
                    onRegister(
                        name.trim(), email.trim(), password,
                        ageVal, gender,
                        heightVal, weightVal, dobMillis
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create account") }

        // Link back to Login
        TextButton(onClick = onGoLogin, modifier = Modifier.align(Alignment.End)) {
            Text("Already have an account? Sign in")
        }

        Spacer(Modifier.height(8.dp))
    }

    // Date of Birth: modal date picker
    if (dobOpen) {
        DatePickerDialog(
            onDismissRequest = { dobOpen = false },
            confirmButton = {
                TextButton(onClick = { dobMillis = dobState.selectedDateMillis; dobOpen = false }) {
                    Text("OK")
                }
            },
            dismissButton = { TextButton(onClick = { dobOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = dobState, showModeToggle = true) }
    }
}
