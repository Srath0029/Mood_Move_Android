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
/**
 * Small helper that shows the platform TimePicker dialog and writes the result
 * back via [onPicked]. Used for hydration/medication reminder times.
 *
 * @param label    text shown on the button
 * @param enabled  whether the button is clickable/active
 * @param hour     current hour (24h)
 * @param minute   current minute
 * @param onPicked callback with (hour, minute) when a time is chosen
 * @param modifier layout modifier for the button
 */
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
            // Use platform dialog to avoid Material3 dependency/version issues.
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
/**
 * Simple read-only profile model displayed at the top of Settings.
 */
data class UserProfile(
    val name: String = "",
    val age: Int? = null,
    val gender: String = "",
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val dobMillis: Long? = null
)

/**
 * Demo profile used when no profile is provided.
 */
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

/**
 * Returns true if all fields are effectively empty/absent.
 */
private fun UserProfile.isEmpty(): Boolean =
    name.isBlank() && age == null && gender.isBlank() &&
            heightCm == null && weightKg == null && dobMillis == null

/* ---------- Settings screen ---------- */
/**
 * Settings screen that shows:
 *  - Read-only Profile summary (name/age/gender/height/weight/DOB)
 *  - Preferences: hydration + medication reminders (with time pickers)
 *  - Background updates toggle (WorkManager)
 *  - Save preferences (schedules/cancels alarms)
 *  - Logout / Go to Login actions
 *
 * Note: Alarm scheduling is delegated to [ReminderScheduler];
 *       WorkManager start/stop uses [ContextIngestWorker].
 */
@Composable
fun SettingsScreen(
    profile: UserProfile = UserProfile(),
    onLogout: () -> Unit = {},
    onGoLogin: () -> Unit = {},
    onSavePrefs: (Boolean, Boolean) -> Unit = { _, _ -> }
) {
    // Date formatting for DOB row
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()) }
    fun formatDob(ms: Long?) =
        ms?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt) }
            ?: "Not set"

    // Use demo profile if nothing was provided
    val shown = remember(profile) { if (profile.isEmpty()) demoProfile() else profile }

    // Preferences and reminder times (local screen state)
    val ctx = LocalContext.current
    var hydration by remember { mutableStateOf(true) }
    var medication by remember { mutableStateOf(false) }
    var bgUpdates by remember { mutableStateOf(true) }           // WorkManager on/off

    var hydrationHour by remember { mutableStateOf(9) }
    var hydrationMinute by remember { mutableStateOf(0) }
    var medicationHour by remember { mutableStateOf(20) }
    var medicationMinute by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // Allow scrolling on smaller screens
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

        // ---- Preferences (reminders + background updates) ----
        Text("Preferences", style = MaterialTheme.typography.titleMedium)

        // Hydration reminder (inexact alarm)
        SettingSwitchRow("Hydration reminders", hydration) { hydration = it }
        TimePickerButton(
            label = "Hydration time",
            enabled = hydration,
            hour = hydrationHour,
            minute = hydrationMinute,
            onPicked = { h, m -> hydrationHour = h; hydrationMinute = m }
        )

        // Medication reminder (exact alarm)
        SettingSwitchRow("Medication reminders (exact)", medication) { medication = it }
        TimePickerButton(
            label = "Medication time",
            enabled = medication,
            hour = medicationHour,
            minute = medicationMinute,
            onPicked = { h, m -> medicationHour = h; medicationMinute = m }
        )

        // Background updates: start/stop WorkManager immediately on toggle
        SettingSwitchRow(
            title = "Background updates (WorkManager)",
            checked = bgUpdates,
            onCheckedChange = { checked ->
                bgUpdates = checked
                if (checked) ContextIngestWorker.enqueue(ctx)
                else ContextIngestWorker.cancel(ctx)
            }
        )

        // Persist preferences and (re)schedule/cancel alarms accordingly
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

        // Account actions
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
        Button(onClick = onGoLogin, modifier = Modifier.fillMaxWidth()) { Text("Go to Login") }
    }
}

/* ---------- Small UI helpers ---------- */
/**
 * Two-column row used to render a single profile attribute/value.
 */
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

/**
 * Labeled switch row used for preference items in Settings.
 */
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
