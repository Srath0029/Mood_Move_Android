package com.example.application

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavigationBar() {
    val navController = rememberNavController()
    val BrandPurple = Color(0xFFB39DDB)
    val BrandOnPurple = Color.White
    val BrandOnPurpleFaded = Color.White.copy(alpha = 0.80f)
    val BrandIndicator = Color.White.copy(alpha = 0.20f)

    val bottomItems = listOf(
        Destination.HOME, Destination.LOG, Destination.HISTORY, Destination.INSIGHTS
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation Bar Demo App", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Destination.SETTINGS.route) {
                            launchSingleTop = true
                        }
                    }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandPurple,
                    titleContentColor = BrandOnPurple
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.padding(bottom = 20.dp),
                containerColor = BrandPurple
            ) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                bottomItems.forEach { dest ->
                    NavigationBarItem(
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.LOGIN.route)    { LoginScreen() }
            composable(Destination.HOME.route)     { HomeScreen() }
            composable(Destination.LOG.route)      { LogScreen() }
            composable(Destination.HISTORY.route)  { HistoryScreen() }
            composable(Destination.INSIGHTS.route) { InsightsScreen() }
            composable(Destination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
