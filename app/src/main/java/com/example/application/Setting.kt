package com.example.application

import androidx.compose.foundation.layout.*
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding

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

@Composable
fun SettingsScreen(
    profile: UserProfile = UserProfile(),     // pass real data if you have it
    onLogout: () -> Unit = {},
    onGoLogin: () -> Unit = {},
    onSavePrefs: (Boolean, Boolean) -> Unit = { _, _ -> }
) {
    val shownProfile = remember(profile) { if (profile.isEmpty()) demoProfile() else profile }

    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()) }
    fun formatDob(ms: Long?): String =
        ms?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .toLocalDate().format(dateFmt)
        } ?: "Not set"

    // Preferences (original)
    var hydration by remember { mutableStateOf(true) }
    var medication by remember { mutableStateOf(false) }
    var bgUpdates by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // 使整页可滚动
            .navigationBarsPadding()               // 避免被系统/底部栏遮挡（可选但推荐）
            .imePadding()                          // 弹出键盘时自动上移（可选）
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // ---- Read-only Profile ----
        Text("Profile", style = MaterialTheme.typography.titleMedium)
        ProfileRow("Full name", shownProfile.name.ifBlank { "—" })
        ProfileRow("Age", shownProfile.age?.toString() ?: "—")
        ProfileRow("Gender", shownProfile.gender.ifBlank { "—" })
        ProfileRow("Height", shownProfile.heightCm?.let { "$it cm" } ?: "—")
        ProfileRow("Weight", shownProfile.weightKg?.let { "$it kg" } ?: "—")
        ProfileRow("Date of birth", formatDob(shownProfile.dobMillis))

        Divider()

        // ---- Original settings (after profile) ----
        Text("Preferences", style = MaterialTheme.typography.titleMedium)
        SettingSwitchRow(
            title = "Hydration reminders",
            checked = hydration,
            onCheckedChange = { hydration = it }
        )
        SettingSwitchRow(
            title = "Medication reminders",
            checked = medication,
            onCheckedChange = { medication = it }
        )
        SettingSwitchRow(
            title = "Background updates (WorkManager)",
            checked = bgUpdates,
            onCheckedChange = { checked ->
                bgUpdates = checked
                if (checked) ContextIngestWorker.enqueue(context)
                else ContextIngestWorker.cancel(context)
            }
        )

        Button(
            onClick = { onSavePrefs(hydration, medication) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save preferences") }

        Divider()

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Log out") }

        Button(
            onClick = onGoLogin,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Go to Login") }
    }
}

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
