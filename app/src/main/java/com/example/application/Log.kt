package com.example.application

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.Json
import java.time.LocalDate

private enum class TimePickerTarget { START, END }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    // --- State and context ---
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showErrors by rememberSaveable { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // --- Form status ---
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var openDatePicker by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDateMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
                val dateToCheck = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                return !dateToCheck.isAfter(today)
            }
        }
    )
    val exerciseTypes = listOf("Walk", "Run", "Cycling", "Yoga", "Strength", "Stretching")
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf("") }
    var selectedStartTime by rememberSaveable { mutableStateOf<LocalTime?>(null) }
    var selectedEndTime by rememberSaveable { mutableStateOf<LocalTime?>(null) }
    var timePickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }
    val moodOptions = listOf("Very happy", "Happy", "Normal", "Sad", "Very sad")
    var selectedMood by rememberSaveable { mutableStateOf<String?>(null) }
    val intensityOptions = listOf("High", "Medium", "Low")
    var selectedIntensity by rememberSaveable { mutableStateOf<String?>(null) }

    // --- Location service client ---
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // --- Permission request launcher ---
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
            ) {
                // Permission is granted, prompt the user to click the submit button again
                scope.launch { snackbarHostState.showSnackbar("Permission granted, please submit again.") }
            } else {
                // Permission denied
                scope.launch { snackbarHostState.showSnackbar("Requires location permission to record.") }
            }
        }
    )

    // --- Logical function ---
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()) }
    fun formatDate(millis: Long?): String =
        millis?.let { ms ->
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
        } ?: "Select date"

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    fun formatTime(time: LocalTime?): String = time?.format(timeFormatter) ?: "Select time"

    // --- Field level error status flag ---
    val dateError = showErrors && selectedDateMillis == null
    val typeError = showErrors && selectedType.isBlank()
    val moodError = showErrors && selectedMood == null
    val intensityError = showErrors && selectedIntensity == null
    val startTimeError = showErrors && selectedStartTime == null

    // These two local variables are to solve Kotlin's smart conversion problem
    val localStartTime = selectedStartTime
    val localEndTime = selectedEndTime

    val endTimeError = showErrors && (
            localEndTime == null ||
                    (localStartTime != null && !localEndTime.isAfter(localStartTime)) ||
                    (localStartTime != null && localEndTime.isAfter(localStartTime.plusHours(1)))
            )

    fun moodToScore(mood: String?): Int {
        return when (mood) {
            "Very happy" -> 5
            "Happy" -> 4
            "Normal" -> 3
            "Sad" -> 2
            "Very sad" -> 1
            else -> 0
        }
    }
    fun resetForm() {
        selectedDateMillis = null
        selectedType = ""
        selectedStartTime = null
        selectedEndTime = null
        selectedMood = null
        selectedIntensity = null
        showErrors = false
    }

    fun submit() {
        scope.launch {
            // 1. Form Validation
            showErrors = true
            val localStartTime = selectedStartTime
            val localEndTime = selectedEndTime
            val hasFormError = selectedDateMillis == null || selectedType.isBlank() ||
                    localStartTime == null || localEndTime == null || selectedMood == null ||
                    selectedIntensity == null || (localStartTime != null && !localEndTime.isAfter(localStartTime)) ||
                    (localStartTime != null && localEndTime.isAfter(localStartTime.plusHours(1)))
            if (hasFormError) return@launch

            isSubmitting = true

            try {
                // 2. Check and request location permission
                if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    locationPermissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                    isSubmitting = false
                    return@launch
                }

                // 3. Get the location
                val location = fusedLocationClient.lastLocation.await()
                val geoPoint = if (location != null) GeoPoint(location.latitude, location.longitude) else null

                // 4. Get temperature
                val temperature = if (location != null && selectedDateMillis != null && selectedStartTime != null) {
                    getTemperature(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        dateMillis = selectedDateMillis!!,
                        startTime = selectedStartTime!!
                    )
                } else {
                    null
                }

                // 5. Get the current user's Email
                val currentUser = Firebase.auth.currentUser
                val userEmail = currentUser?.email
                val userId = currentUser?.uid

                // 6. Construct data object
                val logEntry = ExerciseLog(
                    userEmail = userEmail,
                    userId = userId,
                    dateMillis = selectedDateMillis,
                    startTime = selectedStartTime?.format(timeFormatter),
                    endTime = selectedEndTime?.format(timeFormatter),
                    exerciseType = selectedType,
                    moodScore = moodToScore(selectedMood),
                    intensity = selectedIntensity ?: "",
                    location = geoPoint,
                    temperatureCelsius = temperature
                )

                // 7. Save to Firestore
                Firebase.firestore.collection("logs").add(logEntry).await()

                // 8. Display a pop-up window after success
                showSuccessDialog = true

            } catch (e: Exception) {
                Log.e("SubmitError", "Failed to submit log", e)
                snackbarHostState.showSnackbar("error: ${e.message}")
            } finally {
                isSubmitting = false
            }
        }
    }

    // --- UI ---
    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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

            /* ---------------- Start and End Time ---------------- */
            val isEndTimeEnabled = selectedStartTime != null

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { timePickerTarget = TimePickerTarget.START }
                ) {
                    OutlinedTextField(
                        value = formatTime(selectedStartTime),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Start time") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = startTimeError,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = if (startTimeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }

                // End Time Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = isEndTimeEnabled) {
                            timePickerTarget = TimePickerTarget.END
                        }
                ) {
                    OutlinedTextField(
                        value = formatTime(selectedEndTime),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("End time") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = endTimeError,
                        colors = if (isEndTimeEnabled) {
                            OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = if (endTimeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            OutlinedTextFieldDefaults.colors()
                        }
                    )
                }
            }

            if (startTimeError) {
                Text(
                    "Please select a start time",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (endTimeError) {
                Text(
                    "End time must be after start time and within 1 hour",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // --- Time Picker Dialog Logic ---
            if (timePickerTarget != null) {
                val isStartTimePicker = timePickerTarget == TimePickerTarget.START
                val timeState = rememberTimePickerState(
                    initialHour = if (isStartTimePicker) selectedStartTime?.hour ?: LocalTime.now().hour else selectedEndTime?.hour ?: LocalTime.now().hour,
                    initialMinute = if (isStartTimePicker) selectedStartTime?.minute ?: LocalTime.now().minute else selectedEndTime?.minute ?: LocalTime.now().minute,
                    is24Hour = true
                )
                // 1. 修改状态变量，使其能保存具体的错误信息
                var showTimePickerError by remember { mutableStateOf(false) }
                var timePickerErrorMessage by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { timePickerTarget = null },
                    title = {
                        Text(if (isStartTimePicker) "Select Start Time" else "Select End Time")
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TimePicker(state = timeState)

                            // 3. 更新UI以显示错误信息
                            if (showTimePickerError) {
                                Text(
                                    modifier = Modifier.padding(top = 8.dp),
                                    text = timePickerErrorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val selectedTime = LocalTime.of(timeState.hour, timeState.minute)

                            if (isStartTimePicker) {
                                val today = LocalDate.now(ZoneId.systemDefault())
                                val selectedDate = selectedDateMillis?.let {
                                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                }

                                if (selectedDate == today && selectedTime.isAfter(LocalTime.now(ZoneId.systemDefault()))) {
                                    timePickerErrorMessage = "Start time cannot be in the future for today's date."
                                    showTimePickerError = true
                                } else {
                                    showTimePickerError = false
                                    selectedStartTime = selectedTime
                                    timePickerTarget = null
                                }
                            } else {
                                val startTime = selectedStartTime
                                if (startTime != null) {
                                    val isValid = selectedTime.isAfter(startTime) && !selectedTime.isAfter(startTime.plusHours(1))
                                    if (isValid) {
                                        showTimePickerError = false
                                        selectedEndTime = selectedTime
                                        timePickerTarget = null
                                    } else {
                                        timePickerErrorMessage = "End time must be after start time and within 1 hour."
                                        showTimePickerError = true
                                    }
                                }
                            }
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        Button(onClick = { timePickerTarget = null }) { Text("Cancel") }
                    }
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
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save log")
                }
            }

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


// --- Weather API call function ---

private val ktorClient = HttpClient(Android) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
}

@Serializable
private data class HistoricalWeatherResponse(val data: List<HistoricalData>)

@Serializable
private data class HistoricalData(val temp: Double)
private suspend fun getTemperature(
    latitude: Double,
    longitude: Double,
    dateMillis: Long,
    startTime: LocalTime
): Double? {
    val selectedDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val selectedDateTime = selectedDate.atTime(startTime)
    val timestamp = selectedDateTime.atZone(ZoneId.systemDefault()).toEpochSecond()

    val apiKey = "197d431fa550c96a045c38749be83926"
    val url = "https://api.openweathermap.org/data/3.0/onecall/timemachine?lat=$latitude&lon=$longitude&dt=$timestamp&appid=$apiKey&units=metric"

    return try {
        val response: HistoricalWeatherResponse = ktorClient.get(url).body()
        response.data.firstOrNull()?.temp
    } catch (e: Exception) {
        Log.e("WeatherApi", "Failed to obtain historical temperature", e)
        null
    }
}