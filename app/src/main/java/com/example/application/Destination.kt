package com.example.application

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destination
 *
 * Centralized list of all navigation targets (screens) in the app.
 * Each entry provides:
 * - [route]: unique string used by NavHost to navigate.
 * - [label]: short label used for UI (e.g., bottom bar text).
 * - [icon]: Material icon associated with the destination.
 * - [showInBottomBar]: whether this destination should appear in the bottom navigation.
 */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val showInBottomBar: Boolean,
) {
    // Authentication screens (not shown in bottom bar)
    LOGIN("login", "Login", Icons.Filled.CheckCircle, false),      // Sign-in screen
    REGISTER("register", "Register", Icons.Filled.AddCircle, false),// Sign-up form
    FORGOT("forgot", "Forgot", Icons.Filled.LockOpen, false),       // Forgot-password flow

    // Main tabs (shown in the bottom navigation)
    HOME("home", "Home", Icons.Filled.Home, true),                  // Landing dashboard with quick actions
    LOG("log", "Log", Icons.Filled.Edit, true),                     // Daily log form (date/type/duration/mood/intensity)
    HISTORY("history", "History", Icons.Filled.Build, true),        // Past entries list with filters and actions
    INSIGHTS("insights", "Insights", Icons.Filled.ShoppingCart, true), // Weekly trends/charts and tips

    // App settings (opened from the top app-bar action, not a bottom tab)
    SETTINGS("settings", "Settings", Icons.Filled.Settings, false); // Profile summary, reminders, background updates
}
