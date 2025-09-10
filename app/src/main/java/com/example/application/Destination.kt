package com.example.application

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val showInBottomBar: Boolean,
    val showInSideRail: Boolean
) {
    LOGIN("login", "Login", Icons.Filled.CheckCircle, false, true),
    HOME("home", "Home", Icons.Filled.Home, true, true),
    LOG("log", "Log", Icons.Filled.Edit, true, true),            // Log form
    HISTORY("history", "History", Icons.Filled.Build, true, true),
    INSIGHTS("insights", "Insights", Icons.Filled.ShoppingCart, true, true),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, false, true);
}
