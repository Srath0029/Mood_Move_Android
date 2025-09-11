package com.example.application

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},              // call nav to LOGIN when pressed
    onSavePrefs: (Boolean, Boolean) -> Unit = { _, _ -> } // persist later (DataStore/Room)
) {
    var hydration by remember { mutableStateOf(true) }
    var medication by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // Reminder toggles
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

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSavePrefs(hydration, medication) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save preferences") }

        Divider()

        // Logout
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Log out") }
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
