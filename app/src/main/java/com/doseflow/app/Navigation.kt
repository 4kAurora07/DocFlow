package com.doseflow.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.doseflow.app.data.DoseFlowRepository
import com.doseflow.app.ui.adherence.AdherenceScreen
import com.doseflow.app.ui.medicine.AddEditMedicineScreen
import com.doseflow.app.ui.medicine.MedicineListScreen
import com.doseflow.app.ui.onboarding.OnboardingScreen
import com.doseflow.app.ui.schedule.ScheduleEditorScreen
import com.doseflow.app.ui.settings.SettingsScreen
import com.doseflow.app.ui.timeline.TimelineScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Timeline : Screen("timeline", "Today", Icons.Default.DateRange)
    data object Medicines : Screen("medicines", "Medicines", Icons.AutoMirrored.Filled.List)
    data object Adherence : Screen("adherence", "Adherence", Icons.Default.Check)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val repository = remember { DoseFlowRepository(context) }
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        Screen.Timeline,
        Screen.Medicines,
        Screen.Adherence,
        Screen.Settings
    )

    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    val startDestination = if (repository.isOnboardingCompleted()) {
        Screen.Timeline.route
    } else {
        "onboarding"
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(imageVector = screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Screen.Timeline.route) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    onAddFirstMedicine = {
                        navController.navigate("add_edit_medicine/new") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Timeline.route) {
                TimelineScreen(
                    onNavigateToAddMedicine = {
                        navController.navigate("add_edit_medicine/new")
                    }
                )
            }

            composable(Screen.Medicines.route) {
                MedicineListScreen(
                    onNavigateToAddMedicine = {
                        navController.navigate("add_edit_medicine/new")
                    },
                    onNavigateToEditMedicine = { medicineId ->
                        navController.navigate("add_edit_medicine/$medicineId")
                    },
                    onNavigateToScheduleEditor = { medicineId ->
                        navController.navigate("schedule_editor/$medicineId")
                    }
                )
            }

            composable(
                route = "add_edit_medicine/{medicineId}",
                arguments = listOf(navArgument("medicineId") { type = NavType.StringType })
            ) { backStackEntry ->
                val medicineId = backStackEntry.arguments?.getString("medicineId")
                AddEditMedicineScreen(
                    medicineId = medicineId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScheduleEditor = { medId ->
                        navController.navigate("schedule_editor/$medId") {
                            popUpTo("add_edit_medicine/$medId") { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = "schedule_editor/{medicineId}",
                arguments = listOf(navArgument("medicineId") { type = NavType.StringType })
            ) { backStackEntry ->
                val medicineId = backStackEntry.arguments?.getString("medicineId") ?: ""
                ScheduleEditorScreen(
                    medicineId = medicineId,
                    onNavigateBack = { navController.popBackStack() },
                    onScheduleSaved = {
                        navController.navigate(Screen.Timeline.route) {
                            popUpTo(Screen.Timeline.route) { inclusive = false }
                        }
                    }
                )
            }

            composable(Screen.Adherence.route) {
                AdherenceScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
