package com.example.application

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Central registry of all app navigation destinations.
 *
 * Why centralize:
 * - Keeps routes stable and discoverable in one place (prevents hard-coded strings).
 * - Allows UI (e.g., BottomBar) to map over a single source of truth.
 * - Eases deep-linking: other layers can reference [route] safely.
 *
 * @property route Unique route string used by NavHost and deep links.
 * @property label Short, user-visible label (e.g., bottom bar text).
 * @property icon  Material icon to represent this destination in UI.
 * @property showInBottomBar Whether it should appear in the bottom navigation.
 *
 * NOTE: Icons are placeholders chosen for quick prototyping. Swap to more
 * semantically precise icons if desired (e.g., History/Insights/Analytics).
 */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val showInBottomBar: Boolean,
) {
    // ---- Authentication (not part of bottom bar) ----
    /** Sign-in screen (navigated to on app start if unauthenticated). */
    LOGIN("login", "Login", Icons.Filled.CheckCircle, false),

    /** Sign-up form for new users. */
    REGISTER("register", "Register", Icons.Filled.AddCircle, false),

    /** Forgot-password flow (email reset). */
    FORGOT("forgot", "Forgot", Icons.Filled.LockOpen, false),

    // ---- Main tabs (appear in bottom bar) ----
    /** Landing dashboard with quick actions and summaries. */
    HOME("home", "Home", Icons.Filled.Home, true),

    /** Daily log form (date/type/duration/mood/intensity). */
    LOG("log", "Log", Icons.Filled.Edit, true),

    /** Past entries with filters/actions (edit/delete), export options, etc. */
    HISTORY("history", "History", Icons.Filled.Build, true),

    /** Weekly trends/charts and tips derived from stored data. */
    INSIGHTS("insights", "Insights", Icons.Filled.ShoppingCart, true),

    // ---- Settings (navigable, but not a bottom tab) ----
    /** Profile summary, reminders, background updates, and app preferences. */
    SETTINGS("settings", "Settings", Icons.Filled.Settings, false);
}
