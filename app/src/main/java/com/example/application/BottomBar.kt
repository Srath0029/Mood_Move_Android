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
fun BottomNavigationBar(startRoute: String = Destination.HOME.route) {
    // NavController and current route
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Brand colors
    val BrandPurple = Color(0xFFB39DDB)
    val BrandOnPurple = Color.White
    val BrandOnPurpleFaded = Color.White.copy(alpha = 0.80f)
    val BrandIndicator = Color.White.copy(alpha = 0.20f)

    // Bottom tabs
    val bottomItems = listOf(
        Destination.HOME,
        Destination.LOG,
        Destination.HISTORY,
        Destination.INSIGHTS
    )

    // --- WorkManager snackbar: "Data refreshed" ---
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
    // ------------------------------------------------

    fun navigateSingleTopTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

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
            startDestination = startRoute,
            modifier = Modifier.padding(padding)
        ) {
            // --- Auth routes ---
            composable(Destination.LOGIN.route) {
                LoginScreen(
                    onGoRegister = { navigateSingleTopTo(Destination.REGISTER.route) },
                    onForgotPassword = { navigateSingleTopTo(Destination.FORGOT.route) },
                    onLoggedIn = {
                        // Go to Settings after successful sign-in and remove Login from back stack
                        navController.navigate(Destination.SETTINGS.route) {
                            popUpTo(Destination.LOGIN.route) { inclusive = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Destination.REGISTER.route) {
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
                ForgotPasswordScreen(
                    onSubmit = { _, _, _ ->
                        navigateSingleTopTo(Destination.LOGIN.route)
                    },
                    onBackToLogin = { navigateSingleTopTo(Destination.LOGIN.route) }
                )
            }

            // --- Settings route ---
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    onLogout = {
                        navController.navigate(Destination.LOGIN.route) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onGoLogin = { navigateSingleTopTo(Destination.LOGIN.route) },
                    onSavePrefs = { _, _ -> /* optional persistence */ }
                )
            }

            // --- Main tabs ---
            composable(Destination.HOME.route)     { HomeScreen() }
            composable(Destination.LOG.route)      { LogScreen() }
            composable(Destination.HISTORY.route)  { HistoryScreen() }
            composable(Destination.INSIGHTS.route) { InsightsScreen() }
        }
    }
}
