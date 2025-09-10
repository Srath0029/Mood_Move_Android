package com.example.application

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier


@Composable
fun LoginScreen()    {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Che1ck", style = MaterialTheme.typography.headlineMedium)
    }
}