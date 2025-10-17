package com.example.application

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ---------- Simple TimePicker button using platform dialog ---------- */
/**
 * Small wrapper that shows the platform TimePicker dialog and returns the picked time.
 *
 * Why a dialog instead of inline pickers:
 * - Lightweight, consistent UX with system time selection.
 * - Keeps Settings screen compact.
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
            android.app.TimePickerDialog(
                context, { _, h, m -> onPicked(h, m) }, hour, minute, true
            ).show()
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth()
    ) { Text("$label: %02d:%02d".format(hour, minute)) }
}

/* ---------- Profile data ---------- */
/**
 * Minimal profile snapshot for display in Settings.
 *
 * NOTE:
 * - Treat DOB and body metrics as sensitive. Do not share publicly or to other users
 *   without explicit consent and proper sanitization.
 */
data class UserProfile(
    val name: String = "",
    val age: Int? = null,
    val gender: String = "",
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val dobMillis: Long? = null
)

/* ---------- Settings screen ---------- */
/**
 * Settings screen for:
 * - Viewing read-only user profile fields.
 * - Toggling hydration/medication reminders and background updates.
 * - Picking reminder times.
 * - Saving preferences to Firestore and scheduling alarms locally.
 * - Logging out.
 *
 * Side effects:
 * - Loads/saves `users/{uid}` document in Firestore (merge).
 * - Schedules/cancels alarms via [ReminderScheduler].
 * - Starts/stops background work via [ContextIngestWorker].
 *
 * Permissions:
 * - On API 33+, requests POST_NOTIFICATIONS at runtime before saving preferences.
 * - On API 31+, directs user to the system screen for SCHEDULE_EXACT_ALARM if needed.
 */
@Composable
fun SettingsScreen(
    profile: UserProfile = UserProfile(),
    onLogout: () -> Unit = {},
    onGoLogin: () -> Unit = {},
    onSavePrefs: (Boolean, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Format DOB for display.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isFineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val isCoarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (isFineLocationGranted || isCoarseLocationGranted) {
            scope.launch { snack.showSnackbar("Location permission granted. You can now enable background updates.") }
        } else {
            scope.launch { snack.showSnackbar("Location permission denied. Background updates cannot be enabled.") }
        }
    }

    // Format DOB
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()) }
    fun formatDob(ms: Long?) =
        ms?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt) } ?: "—"

    // Logged-in flag drives whether we load, show, and allow saving.
    val loggedIn by AuthRepository.isLoggedIn.collectAsState(initial = false)

    // Working copy of the profile for display (read-only here).
    var shown by remember(profile) { mutableStateOf(profile) }

    // --- Runtime permission launcher (Android 13+ notifications) ---
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { snack.showSnackbar(if (granted) "Notifications enabled" else "Notifications permission denied") }
    }

    /**
     * Ensure POST_NOTIFICATIONS on API 33+ before we schedule notifications.
     * We call this just-in-time on Save to keep first-run friction low.
     */
    fun ensureNotificationPermission(onDenied: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                onDenied()
            }
        }
    }

    // Preferences + reminder times (local UI state with sensible defaults).
    var hydration by remember { mutableStateOf(true) }
    var medication by remember { mutableStateOf(false) }
    var bgUpdates by remember { mutableStateOf(true) }

    var hydrationHour by remember { mutableStateOf(9) }
    var hydrationMinute by remember { mutableStateOf(0) }
    var medicationHour by remember { mutableStateOf(20) }
    var medicationMinute by remember { mutableStateOf(0) }

    // Explicit defaults for quick restore on logout.
    val DEFAULT_HYDRATION_HOUR = 9
    val DEFAULT_HYDRATION_MINUTE = 0
    val DEFAULT_MEDICATION_HOUR = 20
    val DEFAULT_MEDICATION_MINUTE = 0

    // Load Firestore profile + saved prefs once the user is logged in.
    LaunchedEffect(loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        val uid = Firebase.auth.currentUser?.uid ?: return@LaunchedEffect
        try {
            val snap = Firebase.firestore.collection("users").document(uid).get().await()
            if (snap.exists()) {
                // Populate profile display.
                shown = UserProfile(
                    name = snap.getString("name") ?: "",
                    age = snap.getLong("age")?.toInt(),
                    gender = snap.getString("gender") ?: "",
                    heightCm = snap.getLong("heightCm")?.toInt(),
                    weightKg = snap.getLong("weightKg")?.toInt(),
                    dobMillis = snap.getLong("dobMillis")
                )
                // Load saved preferences if available (otherwise keep local defaults).
                hydration = snap.getBoolean("hydrationEnabled") ?: hydration
                hydrationHour = snap.getLong("hydrationHour")?.toInt() ?: hydrationHour
                hydrationMinute = snap.getLong("hydrationMinute")?.toInt() ?: hydrationMinute

                medication = snap.getBoolean("medicationEnabled") ?: medication
                medicationHour = snap.getLong("medicationHour")?.toInt() ?: medicationHour
                medicationMinute = snap.getLong("medicationMinute")?.toInt() ?: medicationMinute

                bgUpdates = snap.getBoolean("bgUpdates") ?: bgUpdates

                snack.showSnackbar("Profile & preferences loaded")
            }
        } catch (e: Exception) {
            snack.showSnackbar(e.localizedMessage ?: "Failed to load profile/prefs")
        }
    }

    // Exact-alarm capabilities helper for API 31+.
    fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(AlarmManager::class.java)
        return am.canScheduleExactAlarms()
    }
    fun requestExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            context.startActivity(intent)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)

            // ---- Profile (read-only) ----
            Text("Profile", style = MaterialTheme.typography.titleMedium)
            if (!loggedIn) {
                Text(
                    "You are logged out. Profile and preferences are disabled.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            ProfileRow("Full name", shown.name.ifBlank { "—" })
            ProfileRow("Age", shown.age?.toString() ?: "—")
            ProfileRow("Gender", shown.gender.ifBlank { "—" })
            ProfileRow("Height", shown.heightCm?.let { "$it cm" } ?: "—")
            ProfileRow("Weight", shown.weightKg?.let { "$it kg" } ?: "—")
            ProfileRow("Date of birth", formatDob(shown.dobMillis))

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            // ---- Preferences (reminders + background updates) ----
            Text("Preferences", style = MaterialTheme.typography.titleMedium)

            SettingSwitchRow("Hydration reminders", hydration, enabled = loggedIn) { hydration = it }
            TimePickerButton(
                label = "Hydration time",
                enabled = loggedIn && hydration,
                hour = hydrationHour,
                minute = hydrationMinute,
                onPicked = { h, m -> hydrationHour = h; hydrationMinute = m }
            )

            SettingSwitchRow("Medication reminders (exact)", medication, enabled = loggedIn) { medication = it }
            TimePickerButton(
                label = "Medication time",
                enabled = loggedIn && medication,
                hour = medicationHour,
                minute = medicationMinute,
                onPicked = { h, m -> medicationHour = h; medicationMinute = m }
            )

            SettingSwitchRow(
                title = "Background updates (WorkManager)",
                checked = bgUpdates,
                enabled = loggedIn,
                onCheckedChange = { checked ->
                    if (!loggedIn) return@SettingSwitchRow

                    if (checked) {
                        //  Check permissions
                        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasFineLocationPermission || hasCoarseLocationPermission) {
                            bgUpdates = true
                            ContextIngestWorker.enqueue(context)
                            scope.launch { snack.showSnackbar("Background updates enabled.") }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    } else {
                        // --- User wants to close the task ---
                        bgUpdates = false
                        ContextIngestWorker.cancel(context)
                        scope.launch { snack.showSnackbar("Background updates disabled.") }
                    }
                }
            )

            // ---- Save: request permissions → schedule/cancel → persist to Firestore ----
            Button(
                enabled = loggedIn,
                onClick = {
                    // Ask for notifications on first Save (API 33+).
                    var aborted = false
                    ensureNotificationPermission { aborted = true }
                    if (aborted && Build.VERSION.SDK_INT >= 33) {
                        scope.launch { snack.showSnackbar("Please allow notifications, then tap Save again.") }
                        return@Button
                    }

                    // For exact alarms, send user to system settings if not allowed.
                    if (medication && !canScheduleExact()) {
                        requestExactAlarmSettings()
                        scope.launch { snack.showSnackbar("Enable 'Alarms & reminders' permission, then tap Save again.") }
                        return@Button
                    }

                    // Schedule / cancel local alarms (idempotent).
                    onSavePrefs(hydration, medication)

                    if (hydration)
                        ReminderScheduler.scheduleInexactDaily(context, hydrationHour, hydrationMinute)
                    else {
                        ReminderScheduler.cancel(context, ReminderType.HYDRATION_EXACT)
                        ReminderScheduler.cancel(context, ReminderType.HYDRATION_REPEAT)
                    }

                    if (medication) {
                        val ok = ReminderScheduler.scheduleExactDaily(context, medicationHour, medicationMinute)
                        if (!ok) {
                            scope.launch { snack.showSnackbar("Exact alarms not allowed. Enable in system settings.") }
                            return@Button
                        }
                    } else {
                        ReminderScheduler.cancel(context, ReminderType.MEDICATION_EXACT)
                        ReminderScheduler.cancel(context, ReminderType.MEDICATION_REPEAT)
                    }

                    // Persist preferences to Firestore (merge into users/{uid}).
                    val uid = Firebase.auth.currentUser?.uid
                    if (uid == null) {
                        scope.launch { snack.showSnackbar("Not signed in — preferences not saved to cloud.") }
                        return@Button
                    }

                    val doc = hashMapOf(
                        "hydrationEnabled" to hydration,
                        "hydrationHour" to hydrationHour,
                        "hydrationMinute" to hydrationMinute,
                        "medicationEnabled" to medication,
                        "medicationHour" to medicationHour,
                        "medicationMinute" to medicationMinute,
                        "bgUpdates" to bgUpdates,
                        "prefsUpdatedAt" to FieldValue.serverTimestamp()
                    )

                    scope.launch {
                        try {
                            Firebase.firestore.collection("users")
                                .document(uid)
                                .set(doc, SetOptions.merge())
                                .await()
                            snack.showSnackbar("Preferences saved")
                        } catch (e: Exception) {
                            snack.showSnackbar(e.localizedMessage ?: "Failed to save preferences")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save preferences") }

            Divider()

            // ---- Account actions ----
            OutlinedButton(
                onClick = {
                    // Sign out locally (UI remains on this screen).
                    try { Firebase.auth.signOut() } catch (_: Exception) {}

                    // Cancel background work/alarms to avoid stray notifications.
                    ContextIngestWorker.cancel(context)
                    ReminderScheduler.cancel(context, ReminderType.HYDRATION_EXACT)
                    ReminderScheduler.cancel(context, ReminderType.HYDRATION_REPEAT)
                    ReminderScheduler.cancel(context, ReminderType.MEDICATION_EXACT)
                    ReminderScheduler.cancel(context, ReminderType.MEDICATION_REPEAT)

                    // Clear profile & disable controls.
                    shown = UserProfile()

                    // Restore UI defaults immediately for a clean post-logout state.
                    hydration = true
                    hydrationHour = DEFAULT_HYDRATION_HOUR
                    hydrationMinute = DEFAULT_HYDRATION_MINUTE

                    medication = false
                    medicationHour = DEFAULT_MEDICATION_HOUR
                    medicationMinute = DEFAULT_MEDICATION_MINUTE

                    bgUpdates = true

                    scope.launch { snack.showSnackbar("Signed out — defaults restored") }
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Log out") }

            Button(onClick = onGoLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Go to Login")
            }
        }
    }
}

/* ---------- Small UI helpers ---------- */
/**
 * Two-column row for a label/value pair in the profile section.
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
 * A labeled switch with enabled state support, used for preferences toggles.
 */
@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}