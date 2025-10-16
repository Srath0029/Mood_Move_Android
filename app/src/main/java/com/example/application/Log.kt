package com.example.application

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.AlertDialog

/**
 * LogScreen
 *
 * Daily logging form that captures:
 *  - Date (DatePicker)
 *  - Exercise type (dropdown)
 *  - Duration (dropdown)
 *  - Mood (radio group)
 *  - Intensity (radio group)
 *
 *  Database
 *   - email
 *   - Date
 *   - Exercise type
 *   - Duration
 *   - Mood （very happy 5）
 *   - Intensity
 *   - Location
 *   - Temp
 * Includes early validation with inline error messages and a snackbar on success.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    // Snackbar host for lightweight success feedback.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Global flag to show validation errors (set after the first submit attempt).
    var showErrors by rememberSaveable { mutableStateOf(false) }

    // Date state: initialize as "not selected" so required validation can trigger.
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var openDatePicker by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDateMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis <= System.currentTimeMillis()
            }
        }
    )

    // Exercise type dropdown state.
    val exerciseTypes = listOf("Walk", "Run", "Cycling", "Yoga", "Strength", "Stretching")
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf("") }

    // Duration dropdown state.
    val durations = listOf("10 min", "20 min", "30 min", "45 min", "60 min")
    var durationExpanded by remember { mutableStateOf(false) }
    var selectedDuration by rememberSaveable { mutableStateOf("") }

    // Mood radio group state.
    val moodOptions = listOf("Very happy", "Happy", "Normal", "Sad", "Very sad")
    var selectedMood by rememberSaveable { mutableStateOf<String?>(null) }

    // Intensity radio group state.
    val intensityOptions = listOf("High", "Medium", "Low")
    var selectedIntensity by rememberSaveable { mutableStateOf<String?>(null) }

    // Date formatter used to render the selected day in the text field.
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()) }
    fun formatDate(millis: Long?): String =
        millis?.let { ms ->
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
        } ?: "Select date"

    // Field-level error flags for inline validation messages.
    val dateError = showErrors && selectedDateMillis == null
    val typeError = showErrors && selectedType.isBlank()
    val durationError = showErrors && selectedDuration.isBlank()
    val moodError = showErrors && selectedMood == null
    val intensityError = showErrors && selectedIntensity == null

    // Used to control the display status of the submit success dialog box
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Clear the filled content after successful submission
    fun resetForm() {
        selectedDateMillis = null
        selectedType = ""
        selectedDuration = ""
        selectedMood = null
        selectedIntensity = null
        showErrors = false
    }

    // Submit handler: toggles validation and shows snackbar if all required fields are set.
    fun submit() {
        showErrors = true
        val hasError =
            selectedDateMillis == null ||
                    selectedType.isBlank() ||
                    selectedDuration.isBlank() ||
                    selectedMood == null ||
                    selectedIntensity == null

        if (!hasError) {
            // TODO: persist to Room; potentially trigger insights/notifications afterwards.
            showSuccessDialog = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()) // Allow scroll on smaller screens/with keyboard.
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Daily Log", style = MaterialTheme.typography.headlineSmall)

            /* ---------------- Date ---------------- */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        Log.d("Solution", "Box wrapper clicked, opening date picker...")
                        openDatePicker = true
                    }
            ) {
                OutlinedTextField(
                    value = formatDate(selectedDateMillis),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Date") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Select Date"
                        )
                    },
                    isError = dateError,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = if (dateError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }

            if (dateError) {
                Text(
                    "Please select a date",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (openDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { openDatePicker = false },
                    confirmButton = {
                        Button(onClick = {
                            selectedDateMillis = dateState.selectedDateMillis
                            openDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        Button(onClick = { openDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = dateState,
                        showModeToggle = true
                        )
                }
            }

            /* ---------------- Exercise Type ---------------- */
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = !typeExpanded }
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = selectedType,
                    onValueChange = {},
                    label = { Text("Exercise type") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                    },
                    isError = typeError,
                    placeholder = { Text("Please choose") }
                )
                DropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    exerciseTypes.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedType = option
                                typeExpanded = false
                            }
                        )
                    }
                }
            }
            if (typeError) {
                Text(
                    "Please choose an exercise type",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            /* ---------------- Duration ---------------- */
            ExposedDropdownMenuBox(
                expanded = durationExpanded,
                onExpandedChange = { durationExpanded = !durationExpanded }
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = selectedDuration,
                    onValueChange = {},
                    label = { Text("Duration") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded)
                    },
                    isError = durationError,
                    placeholder = { Text("Please choose") }
                )
                DropdownMenu(
                    expanded = durationExpanded,
                    onDismissRequest = { durationExpanded = false }
                ) {
                    durations.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedDuration = option
                                durationExpanded = false
                            }
                        )
                    }
                }
            }
            if (durationError) {
                Text(
                    "Please choose a duration",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            /* ---------------- Mood ---------------- */
            Text("Mood", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                moodOptions.forEach { mood ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (selectedMood == mood),
                            onClick = { selectedMood = mood }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(mood)
                    }
                }
            }
            if (moodError) {
                Text(
                    "Please select your mood",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            /* ---------------- Intensity ---------------- */
            Text("Exercise intensity", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                intensityOptions.forEach { level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (selectedIntensity == level),
                            onClick = { selectedIntensity = level }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(level)
                    }
                }
            }
            if (intensityError) {
                Text(
                    "Please select intensity",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            /* ---------------- Submit ---------------- */
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save log") }

            Spacer(Modifier.height(12.dp))

            if (showSuccessDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showSuccessDialog = false
                        resetForm()
                    },
                    title = { Text("Congratulations") },
                    text = { Text("Saved successfully") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSuccessDialog = false
                                resetForm()
                            }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}


