package com.example.application

/*
 * RegisterScreen – User registration form (Email/Password) with optional profile fields.
 *
 * PURPOSE
 * - Collect required credentials (name, email, password) to create an account.
 * - Collect optional profile fields (age, gender, height, weight, DOB).
 * - Delegate actual registration + persistence to AuthRepository.registerAndSave().
 *
 * PRIVACY & DATA CLASSIFICATION
 * - age, height, weight, DOB 属于潜在敏感数据，仅用于个性化/统计；
 *   若日后实现“可共享的数据”，请只共享**去标识化且非敏感**字段。
 * - 任何对外共享（朋友可见/公共流）应**剥离 PII**（如 email、DOB 精确时间、定位等）。
 *
 * SECURITY / RULES
 * - Firebase / Firestore 的读写权限必须由 Security Rules 保证（客户端校验仅用于 UX）。
 * - 密码规则（本地）仅做基本检查；实际强度与重复账号处理依赖后端/Firebase。
 *
 * UX NOTES
 * - 行内 supportingText 给出即时校验反馈；Snack 用于成功场景。
 * - Age 与 DOB 的不一致会给“软警告”（amber），不阻断提交。
 */

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
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * Registration screen (Jetpack Compose).
 *
 * Collects required & optional fields, performs lightweight client-side validation,
 * then calls [AuthRepository.registerAndSave] to create the account and persist profile.
 *
 * Navigation contract:
 * - On success, shows a green snackbar and then invokes [onRegistered] (~1.2s later).
 * - [onGoLogin] navigates back to login screen.
 *
 * Validation:
 * - Email: Android pattern check
 * - Password: >= 8 chars and at least 1 digit (basic)
 * - Optional numeric ranges: Age 5–120, Height 80–250 cm, Weight 20–250 kg
 * - Soft warning if Age and DOB-derived age mismatch (does not block submit)
 *
 * Accessibility:
 * - Uses supportingText for field-level hints/errors to aid screen readers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegister: (
        name: String, email: String, password: String,
        age: Int?, gender: String, heightCm: Int?, weightKg: Int?, dobMillis: Long?
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onGoLogin: () -> Unit = {},
    onRegistered: () -> Unit = {} // invoked AFTER snackbar (~1.2s)
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

    // ---------------- Date of Birth (DOB) picker state ----------------
    var dobOpen by remember { mutableStateOf(false) }
    var dobMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val dobState = rememberDatePickerState(initialSelectedDateMillis = dobMillis)

    // Local date formatter for the DOB button label
    val dateFmt = remember {
        java.time.format.DateTimeFormatter.ofPattern(
            "yyyy-MM-dd", java.util.Locale.getDefault()
        )
    }
    // Format helper for nullable millis
    fun fmt(ms: Long?) = ms?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
    } ?: "Not set"

    // ---------------- Validation helpers ----------------
    var attemptedSubmit by rememberSaveable { mutableStateOf(false) }
    fun shouldShow(showWhenTyped: Boolean, value: String) =
        attemptedSubmit || (showWhenTyped && value.isNotEmpty())

    // Basic syntactic checks – semantic/security checks happen server-side
    fun validEmail(s: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
    fun validPassword(s: String) = s.length >= 8 && s.any(Char::isDigit)

    /**
     * Parse an Int from text and validate inclusive range.
     * Returns Pair(valueOrNull, errorMessageOrNull).
     */
    fun parseIntOrNullInRange(txt: String, min: Int, max: Int): Pair<Int?, String?> {
        if (txt.isBlank()) return null to null
        val v = txt.toIntOrNull() ?: return null to "Numbers only"
        if (v !in min..max) return null to "Must be $min–$max"
        return v to null
    }

    // Required field errors (only show when typed to avoid red wall on empty form)
    val nameError    = if (shouldShow(true, name) && name.isBlank()) "Name is required" else null
    val emailError   = if (email.isNotEmpty() && !validEmail(email)) "Invalid email address" else null
    val pwdError     = if (password.isNotEmpty() && !validPassword(password)) "Min 8 chars incl. a number" else null
    val confirmError = if (confirm.isNotEmpty() && confirm != password) "Passwords do not match" else null

    // Optional numeric fields: range validation only
    val (ageVal, ageErr)       = parseIntOrNullInRange(ageText, 5, 120)
    val (heightVal, heightErr) = parseIntOrNullInRange(heightText, 80, 250)
    val (weightVal, weightErr) = parseIntOrNullInRange(weightText, 20, 250)

    // ---- Age–DOB soft warning (non-blocking) ----
    val calculatedAge: Int? = remember(dobMillis) {
        dobMillis?.let {
            val dob = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            val today = LocalDate.now(ZoneId.systemDefault())
            Period.between(dob, today).years
        }
    }
    val showAgeDobWarning = (ageText.isNotBlank() || attemptedSubmit) &&
            ageVal != null && calculatedAge != null && ageVal != calculatedAge
    val WarningColor = Color(0xFFFFA000) // amber

    // ---------------- State: loading / errors / snackbar ----------------
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }

    // Small helper to reset form after successful registration
    fun clearAllFields() {
        name = ""; email = ""; password = ""; confirm = ""
        ageText = ""; gender = "Female"; heightText = ""; weightText = ""
        dobMillis = null; dobOpen = false; genderExpanded = false
        attemptedSubmit = false; showPwd = false; showConfirm = false
    }

    Scaffold(
        snackbarHost = {
            // Success feedback uses a green snackbar; validation errors are inline
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

            // ---- Full name ----
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full name") },
                singleLine = true,
                isError = nameError != null,
                supportingText = { if (nameError != null) Text(nameError) },
                modifier = Modifier.fillMaxWidth()
            )

            // ---- Email ----
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                isError = emailError != null,
                // Always show hint to guide toward valid patterns
                supportingText = { Text(emailError ?: "Use a valid email (e.g., name@domain.com)") },
                modifier = Modifier.fillMaxWidth()
            )

            // ---- Password ----
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

            // ---- Confirm password ----
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

            // ---- Age + Gender ----
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it.filter(Char::isDigit) }, // input sanitation
                    label = { Text("Age") },
                    singleLine = true,
                    isError = ageErr != null, // range/format errors only
                    supportingText = {
                        when {
                            ageErr != null -> Text(ageErr)
                            // soft warning: advise correction but do not block
                            showAgeDobWarning -> Text(
                                "Age does not match DOB (should be $calculatedAge)",
                                color = WarningColor
                            )
                            else -> {}
                        }
                    },
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

            // ---- Height + Weight ----
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter(Char::isDigit) }, // input sanitation
                    label = { Text("Height (cm)") },
                    singleLine = true,
                    isError = heightErr != null,
                    supportingText = { if (heightErr != null) Text(heightErr) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter(Char::isDigit) }, // input sanitation
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    isError = weightErr != null,
                    supportingText = { if (weightErr != null) Text(weightErr) },
                    modifier = Modifier.weight(1f)
                )
            }

            // ---- DOB actions ----
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { dobOpen = true }, modifier = Modifier.weight(1f)) {
                    Text("Date of birth: ${fmt(dobMillis)}")
                }
                OutlinedButton(onClick = { dobMillis = null }, modifier = Modifier.weight(1f)) {
                    Text("Clear DOB")
                }
            }

            // ---- Submit ----
            Button(
                onClick = {
                    attemptedSubmit = true
                    errorMsg = null

                    // Required fields must be valid and non-empty to proceed
                    val requiredOk = nameError == null && emailError == null &&
                            pwdError == null && confirmError == null &&
                            name.isNotBlank() && email.isNotBlank() &&
                            password.isNotBlank() && confirm.isNotBlank()

                    // Optional numeric fields either blank or within range
                    val optionalOk = listOf(ageErr, heightErr, weightErr).all { it == null }

                    if (requiredOk && optionalOk) {
                        // Let caller hook in (analytics, side-effects) without blocking UI
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
                                snack.showSnackbar("Successful Registration")
                                // Allow users to see the snackbar before navigation
                                delay(1200)
                                onRegistered()
                            }.onFailure { e ->
                                // Collision -> email already registered (guide to login/reset)
                                errorMsg = when (e) {
                                    is FirebaseAuthUserCollisionException ->
                                        "This email is already registered. Try signing in or reset your password."
                                    else ->
                                        e.localizedMessage ?: "Registration failed"
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create account") }

            // ---- Link back to Login ----
            TextButton(onClick = onGoLogin, modifier = Modifier.align(Alignment.End)) {
                Text("Already have an account? Sign in")
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // ---- DOB picker dialog (lazy) ----
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
