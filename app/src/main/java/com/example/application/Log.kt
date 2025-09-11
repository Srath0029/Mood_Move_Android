package com.example.application

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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    // ----- Constants from your spec -----
    val moodOptions = listOf("Very happy", "Happy", "Normal", "Sad", "Very sad")
    val intensityOptions = listOf("High", "Medium", "Low")
    val exerciseTypes = listOf("Walk", "Run", "Cycling", "Yoga", "Strength", "Stretching")
    val durations = listOf("10 min", "20 min", "30 min", "45 min", "60 min")

    // ----- State -----
    var openDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(System.currentTimeMillis()) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)

    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf(exerciseTypes.first()) }

    var durationExpanded by remember { mutableStateOf(false) }
    var selectedDuration by rememberSaveable { mutableStateOf(durations[2]) } // default 30 min

    var selectedMood by rememberSaveable { mutableStateOf(moodOptions[2]) } // Normal
    var selectedIntensity by rememberSaveable { mutableStateOf(intensityOptions[1]) } // Medium




    // Format date helper
    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
    }

    fun formatDate(millis: Long?): String =
        millis?.let { ms ->
            Instant.ofEpochMilli(ms)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(formatter)
        } ?: "Select date"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Daily Log", style = MaterialTheme.typography.headlineSmall)

        // ---- Date (Material 3 DatePicker) ----
        OutlinedTextField(
            value = formatDate(selectedDateMillis),
            onValueChange = {},
            readOnly = true,
            label = { Text("Date") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = openDatePicker)
            }
        )
        Button(onClick = { openDatePicker = true }) { Text("Pick a date") }

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
                DatePicker(state = dateState, showModeToggle = true)
            }
        }

        // ---- Exercise: Type (Dropdown) ----
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
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) }
            )

            // ⬇️ Replace ExposedDropdownMenu with DropdownMenu
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


        // ---- Exercise: Duration (Dropdown) ----
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
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) }
            )

            // ⬇️ Replace ExposedDropdownMenu with DropdownMenu
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


        // ---- Mood (Radio buttons) ----
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

        // ---- Exercise intensity (Radio buttons) ----
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

        // ---- Submit ----
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                // TODO: Save to Room; trigger insights/notifications later.
                // This page satisfies: DatePicker + Dropdown + RadioButton per A2.
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save log")
        }
        Spacer(Modifier.height(12.dp))
    }
}
