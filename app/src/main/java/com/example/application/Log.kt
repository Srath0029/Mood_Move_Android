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




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    // ---- 成功反馈用的 Snackbar ----
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ---- 字段 & 校验状态 ----
    var showErrors by rememberSaveable { mutableStateOf(false) }

    // 为了能触发“必选校验”，把初始值设为“未选择”
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
    var openDatePicker by remember { mutableStateOf(false) }

    val exerciseTypes = listOf("Walk", "Run", "Cycling", "Yoga", "Strength", "Stretching")
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf("") }

    val durations = listOf("10 min", "20 min", "30 min", "45 min", "60 min")
    var durationExpanded by remember { mutableStateOf(false) }
    var selectedDuration by rememberSaveable { mutableStateOf("") }

    val moodOptions = listOf("Very happy", "Happy", "Normal", "Sad", "Very sad")
    var selectedMood by rememberSaveable { mutableStateOf<String?>(null) }

    val intensityOptions = listOf("High", "Medium", "Low")
    var selectedIntensity by rememberSaveable { mutableStateOf<String?>(null) }

    // ---- 日期格式化 ----
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()) }
    fun formatDate(millis: Long?): String =
        millis?.let { ms ->
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
        } ?: "Select date"

    // ---- 错误状态（内联提示专用）----
    val dateError = showErrors && selectedDateMillis == null
    val typeError = showErrors && selectedType.isBlank()
    val durationError = showErrors && selectedDuration.isBlank()
    val moodError = showErrors && selectedMood == null
    val intensityError = showErrors && selectedIntensity == null

    // ---- 保存提交 ----
    fun submit() {
        showErrors = true
        val hasError = selectedDateMillis == null ||
                selectedType.isBlank() ||
                selectedDuration.isBlank() ||
                selectedMood == null ||
                selectedIntensity == null

        if (!hasError) {
            // TODO: Save to Room; trigger insights/notifications later.
            scope.launch { snackbarHostState.showSnackbar("Log saved") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Daily Log", style = MaterialTheme.typography.headlineSmall)

            /* ---- Date ---- */
            OutlinedTextField(
                value = formatDate(selectedDateMillis),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = openDatePicker) },
                isError = dateError
            )
            if (dateError) {
                Text("Please select a date", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { openDatePicker = true }) { Text("Pick a date") }

            if (openDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { openDatePicker = false },
                    confirmButton = {
                        Button(onClick = {
                            selectedDateMillis = dateState.selectedDateMillis
                            openDatePicker = false
                            // 纠正后立即隐藏该字段错误
                            if (selectedDateMillis != null) showErrors = showErrors // 触发重组即可
                        }) { Text("OK") }
                    },
                    dismissButton = { Button(onClick = { openDatePicker = false }) { Text("Cancel") } }
                ) { DatePicker(state = dateState, showModeToggle = true) }
            }

            /* ---- Exercise Type ---- */
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
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    isError = typeError,
                    placeholder = { Text("Please choose") }
                )
                DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    exerciseTypes.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { selectedType = option; typeExpanded = false }
                        )
                    }
                }
            }
            if (typeError) {
                Text("Please choose an exercise type", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            /* ---- Duration ---- */
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
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                    isError = durationError,
                    placeholder = { Text("Please choose") }
                )
                DropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                    durations.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { selectedDuration = option; durationExpanded = false }
                        )
                    }
                }
            }
            if (durationError) {
                Text("Please choose a duration", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            /* ---- Mood ---- */
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
                Text("Please select your mood", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            /* ---- Intensity ---- */
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
                Text("Please select intensity", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            /* ---- Submit ---- */
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save log") }

            Spacer(Modifier.height(12.dp))
        }
    }
}
