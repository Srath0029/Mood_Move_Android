package com.example.application

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ---------- Simple TimePicker button using platform dialog ---------- */
@Composable
private fun TimePickerButton(
    label: String,
    enabled: Boolean,
    hour: Int,
    minute: Int,
    onPicked: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            android.app.TimePickerDialog(
                context,
                { _, h, m -> onPicked(h, m) },
                hour, minute, /* is24Hour */ true
            ).show()
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth()
    ) {
        Text("$label: %02d:%02d".format(hour, minute))
    }
}

/* ---------- Profile data ---------- */
data class UserProfile(
    val name: String = "",
    val age: Int? = null,
    val gender: String = "",
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val dobMillis: Long? = null
)

private fun demoProfile(): UserProfile =
    UserProfile(
        name = "Yifan Wang",
        age = 22,
        gender = "Female",
        heightCm = 168,
        weightKg = 58,
        dobMillis = LocalDate.of(2003, 5, 14)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

private fun UserProfile.isEmpty(): Boolean =
    name.isBlank() && age == null && gender.isBlank() &&
            heightCm == null && weightKg == null && dobMillis == null

/* ---------- Settings screen ---------- */
@Composable
fun SettingsScreen(
    profile: UserProfile = UserProfile(),
    onLogout: () -> Unit = {},
    onGoLogin: () -> Unit = {},
    onSavePrefs: (Boolean, Boolean) -> Unit = { _, _ -> }
) {
    // Profile formatting
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()) }
    fun formatDob(ms: Long?) =
        ms?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt) }
            ?: "Not set"

    // Show demo profile if none provided
    val shown = remember(profile) { if (profile.isEmpty()) demoProfile() else profile }

    // Preferences + reminder times
    val ctx = LocalContext.current
    var hydration by remember { mutableStateOf(true) }
    var medication by remember { mutableStateOf(false) }
    var bgUpdates by remember { mutableStateOf(true) }           // ← NEW: WorkManager switch state

    var hydrationHour by remember { mutableStateOf(9) }
    var hydrationMinute by remember { mutableStateOf(0) }
    var medicationHour by remember { mutableStateOf(20) }
    var medicationMinute by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // ---- Read-only Profile ----
        Text("Profile", style = MaterialTheme.typography.titleMedium)
        ProfileRow("Full name", shown.name.ifBlank { "—" })
        ProfileRow("Age", shown.age?.toString() ?: "—")
        ProfileRow("Gender", shown.gender.ifBlank { "—" })
        ProfileRow("Height", shown.heightCm?.let { "$it cm" } ?: "—")
        ProfileRow("Weight", shown.weightKg?.let { "$it kg" } ?: "—")
        ProfileRow("Date of birth", formatDob(shown.dobMillis))

        Divider()

        // ---- Preferences ----
        Text("Preferences", style = MaterialTheme.typography.titleMedium)

        // Hydration (inexact alarm)
        SettingSwitchRow("Hydration reminders", hydration) { hydration = it }
        TimePickerButton(
            label = "Hydration time",
            enabled = hydration,
            hour = hydrationHour,
            minute = hydrationMinute,
            onPicked = { h, m -> hydrationHour = h; hydrationMinute = m }
        )

        // Medication (exact alarm)
        SettingSwitchRow("Medication reminders (exact)", medication) { medication = it }
        TimePickerButton(
            label = "Medication time",
            enabled = medication,
            hour = medicationHour,
            minute = medicationMinute,
            onPicked = { h, m -> medicationHour = h; medicationMinute = m }
        )

        // ← NEW: Background updates (WorkManager)
        SettingSwitchRow(
            title = "Background updates (WorkManager)",
            checked = bgUpdates,
            onCheckedChange = { checked ->
                bgUpdates = checked
                if (checked) ContextIngestWorker.enqueue(ctx)
                else ContextIngestWorker.cancel(ctx)
            }
        )

        // Save preferences + schedule/cancel alarms
        Button(
            onClick = {
                onSavePrefs(hydration, medication)

                if (hydration)
                    ReminderScheduler.scheduleInexactDaily(ctx, hydrationHour, hydrationMinute)
                else
                    ReminderScheduler.cancel(ctx, ReminderType.HYDRATION)

                if (medication)
                    ReminderScheduler.scheduleExactDaily(ctx, medicationHour, medicationMinute)
                else
                    ReminderScheduler.cancel(ctx, ReminderType.MEDICATION)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save preferences") }

        Divider()

        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
        Button(onClick = onGoLogin, modifier = Modifier.fillMaxWidth()) { Text("Go to Login") }
    }
}


/* ---------- Small UI helpers ---------- */
@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
