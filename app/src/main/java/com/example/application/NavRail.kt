//package com.example.application
//
//import androidx.compose.foundation.layout.padding
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Menu
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.Modifier
//import androidx.navigation.NavGraph.Companion.findStartDestination
//import androidx.navigation.compose.*
//import kotlinx.coroutines.launch
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun ExpandedModalNavigationRail() {
//    val navController = rememberNavController()
//    val drawerState = rememberDrawerState(DrawerValue.Closed)
//    val scope = rememberCoroutineScope()
//
//    val BrandPurple = Color(0xFFB39DDB)
//    val BrandOnPurple = Color.White
//    val BrandOnPurpleFaded = Color.White.copy(alpha = 0.80f)
//    val BrandIndicator = Color.White.copy(alpha = 0.20f)
//
//    val railItems = listOf(
//        Destination.LOGIN,
//        Destination.HOME,
//        Destination.LOG,
//        Destination.HISTORY,
//        Destination.INSIGHTS,
//        Destination.SETTINGS
//    )
//
//    ModalNavigationDrawer(
//        drawerState = drawerState,
//        drawerContent = {
//            NavigationRail(containerColor = BrandPurple, contentColor = BrandOnPurple) {
//                NavigationRailItem(
//                    selected = false,
//                    onClick = { scope.launch { drawerState.close() } },
//                    icon = {
//                        Icon(
//                            painter = painterResource(id = R.drawable.menu_open_24dp),
//                            contentDescription = "Close"
//                        )
//                    },
//                    colors = NavigationRailItemDefaults.colors(
//                        selectedIconColor = BrandOnPurple,
//                        selectedTextColor = BrandOnPurple,
//                        unselectedIconColor = BrandOnPurpleFaded,
//                        unselectedTextColor = BrandOnPurpleFaded,
//                        indicatorColor = BrandIndicator
//                    )
//                )
//                val backStackEntry by navController.currentBackStackEntryAsState()
//                val currentRoute = backStackEntry?.destination?.route
//
//                railItems.forEach { dest ->
//                    NavigationRailItem(
//                        selected = currentRoute == dest.route,
//                        onClick = {
//                            navController.navigate(dest.route) {
//                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
//                                launchSingleTop = true
//                                restoreState = true
//                            }
//                            scope.launch { drawerState.close() }
//                        },
//                        icon = { Icon(dest.icon, contentDescription = dest.label) },
//                        label = { Text(dest.label) },
//                        colors = NavigationRailItemDefaults.colors(
//                            selectedIconColor = BrandOnPurple,
//                            selectedTextColor = BrandOnPurple,
//                            unselectedIconColor = BrandOnPurpleFaded,
//                            unselectedTextColor = BrandOnPurpleFaded,
//                            indicatorColor = BrandIndicator
//                        )
//                    )
//                }
//            }
//        }
//    ) {
//        Scaffold(
//            topBar = {
//                TopAppBar(
//                    title = { Text("Navigation Rail Demo App") },
//                    navigationIcon = {
//                        IconButton(onClick = {
//                            scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() }
//                        }) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
//                    },
//                    colors = TopAppBarDefaults.topAppBarColors(
//                        containerColor = BrandPurple,
//                        titleContentColor = BrandOnPurple,
//                        navigationIconContentColor = BrandOnPurple
//                    )
//                )
//            }
//        ) { padding ->
//            NavHost(
//                navController = navController,
//                startDestination = Destination.HOME.route,
//                modifier = Modifier.padding(padding)
//            ) {
//                composable(Destination.LOGIN.route)    { LoginScreen() }
//                composable(Destination.HOME.route)     { HomeScreen() }
//                composable(Destination.LOG.route)      { LogScreen() }
//                composable(Destination.HISTORY.route)  { HistoryScreen() }
//                composable(Destination.INSIGHTS.route) { InsightsScreen() }
////                composable(Destination.SETTINGS.route) { SettingsScreen() }
//            }
//        }
//    }
//}
