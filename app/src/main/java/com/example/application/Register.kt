package com.example.application

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding

import java.time.format.DateTimeFormatter



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegister: (
        name: String, email: String, password: String,
        age: Int?, gender: String, heightCm: Int?, weightKg: Int?, dobMillis: Long?
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onGoLogin: () -> Unit = {}
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    var ageText by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("Female") }
    var heightText by rememberSaveable { mutableStateOf("") }
    var weightText by rememberSaveable { mutableStateOf("") }

    val genderOptions = listOf("Female", "Male", "Other")
    var genderExpanded by remember { mutableStateOf(false) }


    var dobOpen by remember { mutableStateOf(false) }
    var dobMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val dobState = rememberDatePickerState(initialSelectedDateMillis = dobMillis)
    val dateFmt = remember {
        java.time.format.DateTimeFormatter.ofPattern(
            "yyyy-MM-dd",
            java.util.Locale.getDefault()
        )
    }
    fun fmt(ms: Long?) = ms?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
    } ?: "Not set"

    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // ← enable scroll
            .navigationBarsPadding()               // ← avoid being hidden by system bars
            .imePadding()                          // ← lift content when keyboard shows
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Register", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = name, onValueChange = { name = it; error = null },
            label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email, onValueChange = { email = it; error = null },
            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it; error = null },
            label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it; error = null },
            label = { Text("Confirm password") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Age (unchanged)
            OutlinedTextField(
                value = ageText,
                onValueChange = { ageText = it.filter(Char::isDigit); error = null },
                label = { Text("Age") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            // Gender dropdown
            Column(Modifier.weight(2f)) {
                ExposedDropdownMenuBox(
                    expanded = genderExpanded,
                    onExpandedChange = { genderExpanded = !genderExpanded }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor()         // anchor the menu to this field
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
                                    error = null
                                }
                            )
                        }
                    }
                }
            }
        }


        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it.filter(Char::isDigit); error = null },
                label = { Text("Height (cm)") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it.filter(Char::isDigit); error = null },
                label = { Text("Weight (kg)") }, singleLine = true, modifier = Modifier.weight(1f)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { dobOpen = true }, modifier = Modifier.weight(1f)) {
                Text("Date of birth: ${fmt(dobMillis)}")
            }
            OutlinedButton(onClick = { dobMillis = null }, modifier = Modifier.weight(1f)) {
                Text("Clear DOB")
            }
        }

        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

        Button(
            onClick = {
                when {
                    name.isBlank() || email.isBlank() || password.isBlank() || confirm.isBlank() ->
                        error = "All fields are required."
                    password != confirm ->
                        error = "Passwords do not match."
                    else ->
                        onRegister(
                            name.trim(), email.trim(), password,
                            ageText.toIntOrNull(), gender,
                            heightText.toIntOrNull(), weightText.toIntOrNull(), dobMillis
                        )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create account") }

        TextButton(onClick = onGoLogin, modifier = Modifier.align(Alignment.End)) {
            Text("Already have an account? Sign in")
        }

        Spacer(Modifier.height(8.dp)) // small bottom breathing space
    }

    if (dobOpen) {
        DatePickerDialog(
            onDismissRequest = { dobOpen = false },
            confirmButton = {
                TextButton(onClick = { dobMillis = dobState.selectedDateMillis; dobOpen = false }) {
                    Text("OK")
                }
            },
            dismissButton = { TextButton(onClick = { dobOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = dobState, showModeToggle = true) }
    }
}

