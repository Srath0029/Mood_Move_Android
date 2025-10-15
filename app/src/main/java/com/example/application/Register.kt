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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegister: (
        name: String, email: String, password: String,
        age: Int?, gender: String, heightCm: Int?, weightKg: Int?, dobMillis: Long?
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onGoLogin: () -> Unit = {},
    onRegistered: () -> Unit = {} // will run AFTER we show the snackbar (~1.2s)
) {
    // ---------------- Required credential fields ----------------
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPwd by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }

    // ---------------- Optional profile fields ----------------
    var ageText by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("Female") }
    var heightText by rememberSaveable { mutableStateOf("") }
    var weightText by rememberSaveable { mutableStateOf("") }
    val genderOptions = listOf("Female", "Male", "Other")
    var genderExpanded by remember { mutableStateOf(false) }

    // DOB
    var dobOpen by remember { mutableStateOf(false) }
    var dobMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val dobState = rememberDatePickerState(initialSelectedDateMillis = dobMillis)
    val dateFmt = remember {
        java.time.format.DateTimeFormatter.ofPattern(
            "yyyy-MM-dd", java.util.Locale.getDefault()
        )
    }
    fun fmt(ms: Long?) = ms?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
    } ?: "Not set"

    // ---------------- Validation helpers ----------------
    var attemptedSubmit by rememberSaveable { mutableStateOf(false) }
    fun shouldShow(showWhenTyped: Boolean, value: String) =
        attemptedSubmit || (showWhenTyped && value.isNotEmpty())
    fun validEmail(s: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
    fun validPassword(s: String) = s.length >= 8 && s.any(Char::isDigit)
    fun parseIntOrNullInRange(txt: String, min: Int, max: Int): Pair<Int?, String?> {
        if (txt.isBlank()) return null to null
        val v = txt.toIntOrNull() ?: return null to "Numbers only"
        if (v !in min..max) return null to "Must be $min–$max"
        return v to null
    }

    val nameError    = if (shouldShow(true, name) && name.isBlank()) "Name is required" else null
    val emailError   = if (email.isNotEmpty() && !validEmail(email)) "Invalid email address" else null
    val pwdError     = if (password.isNotEmpty() && !validPassword(password)) "Min 8 chars incl. a number" else null
    val confirmError = if (confirm.isNotEmpty() && confirm != password) "Passwords do not match" else null

    val (ageVal, ageErr)       = parseIntOrNullInRange(ageText, 5, 120)
    val (heightVal, heightErr) = parseIntOrNullInRange(heightText, 80, 250)
    val (weightVal, weightErr) = parseIntOrNullInRange(weightText, 20, 250)

    // ---------------- State: loading / errors / snackbar ----------------
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }

    fun clearAllFields() {
        name = ""; email = ""; password = ""; confirm = ""
        ageText = ""; gender = "Female"; heightText = ""; weightText = ""
        dobMillis = null; dobOpen = false; genderExpanded = false
        attemptedSubmit = false; showPwd = false; showConfirm = false
    }

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
            Text("Register", style = MaterialTheme.typography.headlineSmall)

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (errorMsg != null) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }

            // Full name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full name") },
                singleLine = true,
                isError = nameError != null,
                supportingText = { if (nameError != null) Text(nameError) },
                modifier = Modifier.fillMaxWidth()
            )

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                isError = emailError != null,
                supportingText = { Text(emailError ?: "Use a valid email (e.g., name@domain.com)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Password
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
                supportingText = { Text(pwdError ?: "At least 8 characters and include a number") },
                modifier = Modifier.fillMaxWidth()
            )

            // Confirm password
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

            // Age + Gender
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it.filter(Char::isDigit) },
                    label = { Text("Age") },
                    singleLine = true,
                    isError = ageErr != null,
                    supportingText = { if (ageErr != null) Text(ageErr) },
                    modifier = Modifier.weight(1f)
                )
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

            // Height + Weight
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

            // DOB actions
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { dobOpen = true }, modifier = Modifier.weight(1f)) {
                    Text("Date of birth: ${fmt(dobMillis)}")
                }
                OutlinedButton(onClick = { dobMillis = null }, modifier = Modifier.weight(1f)) {
                    Text("Clear DOB")
                }
            }

            // Submit
            Button(
                onClick = {
                    attemptedSubmit = true
                    errorMsg = null

                    val requiredOk = nameError == null && emailError == null &&
                            pwdError == null && confirmError == null &&
                            name.isNotBlank() && email.isNotBlank() &&
                            password.isNotBlank() && confirm.isNotBlank()
                    val optionalOk = listOf(ageErr, heightErr, weightErr).all { it == null }

                    if (requiredOk && optionalOk) {
                        onRegister(
                            name.trim(), email.trim(), password,
                            ageVal, gender, heightVal, weightVal, dobMillis
                        )
                        loading = true
                        scope.launch {
                            val result = AuthRepository.registerAndSave(
                                name.trim(), email.trim(), password,
                                ageVal, gender, heightVal, weightVal, dobMillis
                            )
                            loading = false
                            result.onSuccess {
                                clearAllFields()
                                // show GREEN snackbar, then navigate after a short delay
                                snack.showSnackbar("Successful Registration")
                                delay(1200)
                                onRegistered()
                            }.onFailure { e ->
                                errorMsg = e.localizedMessage ?: "Registration failed"
                            }
                        }
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
    }

    // DOB picker
    if (dobOpen) {
        DatePickerDialog(
            onDismissRequest = { dobOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    dobMillis = dobState.selectedDateMillis
                    dobOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { dobOpen = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = dobState, showModeToggle = true) }
    }
}
