package com.example.application

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*

/**
 * BottomNavigationBar
 *
 * A single-activity scaffold that:
 * - Hosts a Navigation Bar (bottom) for the four main tabs.
 * - Shows a TopAppBar with a dynamic title and a Settings action.
 * - Wires the NavHost with all routes (auth + main + settings + forgot).
 * - Observes WorkManager to display a one-line snackbar when background work succeeds.
 *
 * @param startRoute initial route for the NavHost (defaults to Home).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavigationBar(startRoute: String = Destination.HOME.route) {
    // NavController and current route
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Brand colors used across TopAppBar and NavigationBar
    val BrandPurple = Color(0xFFB39DDB)
    val BrandOnPurple = Color.White
    val BrandOnPurpleFaded = Color.White.copy(alpha = 0.80f)
    val BrandIndicator = Color.White.copy(alpha = 0.20f)

    // Bottom tabs to show (Settings is accessed from the top-right action)
    val bottomItems = listOf(
        Destination.HOME,
        Destination.LOG,
        Destination.HISTORY,
        Destination.INSIGHTS
    )

    // --- WorkManager snackbar: show "Data refreshed" when a periodic worker completes ---
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val workInfos by remember {
        androidx.work.WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(ContextIngestWorker.UNIQUE_NAME)
    }.observeAsState(initial = emptyList())
    var lastShownId by remember { mutableStateOf<java.util.UUID?>(null) }
    LaunchedEffect(workInfos) {
        val done = workInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.SUCCEEDED }
        if (done != null && done.id != lastShownId) {
            lastShownId = done.id
            snackbarHostState.showSnackbar("Data refreshed")
        }
    }
    // -------------------------------------------------------------------------------

    // Helper: navigate to a route without duplicating destinations on the back stack
    fun navigateSingleTopTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // App bar title changes based on the current route
    val title = when (currentRoute) {
        Destination.HOME.route     -> "Home"
        Destination.LOG.route      -> "Log"
        Destination.HISTORY.route  -> "History"
        Destination.INSIGHTS.route -> "Insights"
        Destination.LOGIN.route    -> "Login"
        Destination.REGISTER.route -> "Register"
        Destination.SETTINGS.route -> "Settings"
        else                       -> "Navigation Bar Demo App"
    }

    // Main scaffold: top app bar, bottom nav bar, snackbar host, and the NavHost content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                actions = {
                    // Open Settings from the app bar action
                    IconButton(onClick = { navigateSingleTopTo(Destination.SETTINGS.route) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandPurple,
                    titleContentColor = BrandOnPurple
                )
            )
        },
        bottomBar = {
            // Always show the bottom bar (including on auth screens for this prototype)
            NavigationBar(containerColor = BrandPurple) {
                bottomItems.forEach { dest ->
                    NavigationBarItem(
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        selected = currentRoute == dest.route,
                        onClick = { navigateSingleTopTo(dest.route) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandOnPurple,
                            selectedTextColor = BrandOnPurple,
                            unselectedIconColor = BrandOnPurpleFaded,
                            unselectedTextColor = BrandOnPurpleFaded,
                            indicatorColor = BrandIndicator
                        )
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        // NavHost: defines all routes in the app
        NavHost(
            navController = navController,
            startDestination = startRoute,   // change to LOGIN to start on the login screen
            modifier = Modifier.padding(padding)
        ) {
            // --- Auth routes ---
            composable(Destination.LOGIN.route) {
                // Login screen: links to Register and Forgot Password
                LoginScreen(
                    onGoRegister = { navigateSingleTopTo(Destination.REGISTER.route) },
                    onForgotPassword = { navigateSingleTopTo(Destination.FORGOT.route) }
                )
            }
            composable(Destination.REGISTER.route) {
                // Register screen: go back to Login explicitly (not just pop)
                RegisterScreen(
                    onGoLogin = {
                        navController.navigate(Destination.LOGIN.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Destination.FORGOT.route) {
                // Forgot Password: after submit or link, navigate back to Login
                ForgotPasswordScreen(
                    onSubmit = { email, code, newPwd ->
                        navigateSingleTopTo(Destination.LOGIN.route)
                    },
                    onBackToLogin = { navigateSingleTopTo(Destination.LOGIN.route) }
                )
            }

            // --- Settings route (declared once, with handlers) ---
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    onLogout = {
                        // Clear the back stack so Back won't return to protected screens
                        navController.navigate(Destination.LOGIN.route) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onGoLogin = {
                        // Simple navigation back to Login (keep state)
                        navigateSingleTopTo(Destination.LOGIN.route)
                    },
                    onSavePrefs = { _, _ -> /* optional persistence (DataStore/Room) */ }
                )
            }

            // --- Main tabs ---
            composable(Destination.HOME.route) {
                HomeScreen(
                    onQuickLog   = { navigateSingleTopTo(Destination.LOG.route) },
                    onGoHistory  = { navigateSingleTopTo(Destination.HISTORY.route) },
                    onGoInsights = { navigateSingleTopTo(Destination.INSIGHTS.route) },
                    onGoSettings = { navigateSingleTopTo(Destination.SETTINGS.route) }
                )
            }
            composable(Destination.LOG.route)      { LogScreen() }
            composable(Destination.HISTORY.route)  { HistoryScreen() }
            composable(Destination.INSIGHTS.route) { InsightsScreen() }
        }
    }
}
