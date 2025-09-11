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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavigationBar() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Brand colors
    val BrandPurple = Color(0xFFB39DDB)
    val BrandOnPurple = Color.White
    val BrandOnPurpleFaded = Color.White.copy(alpha = 0.80f)
    val BrandIndicator = Color.White.copy(alpha = 0.20f)

    // Bottom items (no Settings here)
    val bottomItems = listOf(
        Destination.HOME,
        Destination.LOG,
        Destination.HISTORY,
        Destination.INSIGHTS
    )

    // --- WorkManager snackbar ---
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
    // ----------------------------

    // Helper to navigate without duplicating destinations
    fun navigateSingleTopTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Dynamic title
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                actions = {
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
            // ALWAYS show bottom bar (even on Login/Register)
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
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,   // change to LOGIN if you prefer to start there
            modifier = Modifier.padding(padding)
        ) {
            // Auth
            composable(Destination.LOGIN.route) {
                LoginScreen(
                    onGoRegister = { navigateSingleTopTo(Destination.REGISTER.route) }
                )
            }
            composable(Destination.REGISTER.route) {
                RegisterScreen(onGoLogin = { navController.popBackStack() })
            }

            // Settings – declare ONCE, with handlers
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    onLogout = {
                        // Go to Login and clear back stack so Back won’t return to Home
                        navController.navigate(Destination.LOGIN.route) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onGoLogin = {
                        // Simple navigate to Login (keeps back stack)
                        navigateSingleTopTo(Destination.LOGIN.route)
                    },
                    onSavePrefs = { _, _ -> /* optional: persist to DataStore/Room */ }
                )
            }

            // Main tabs
            composable(Destination.HOME.route)     { HomeScreen() }
            composable(Destination.LOG.route)      { LogScreen() }
            composable(Destination.HISTORY.route)  { HistoryScreen() }
            composable(Destination.INSIGHTS.route) { InsightsScreen() }
        }
    }
}
