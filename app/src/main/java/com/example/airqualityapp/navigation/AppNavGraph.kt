package com.example.airqualityapp.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.airqualityapp.ui.admin.*
import com.example.airqualityapp.ui.auth.LoginScreen
import com.example.airqualityapp.ui.auth.SignupScreen
import com.example.airqualityapp.ui.citizen.*
import com.example.airqualityapp.ui.help.HelpScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    context: Context
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Auth screens
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { role ->
                    if (role == "admin") {
                        navController.navigate(Screen.AdminDashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.CitizenHome.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                onSignupClick = {
                    navController.navigate(Screen.Signup.route)
                }
            )
        }

        composable(Screen.Signup.route) {
            SignupScreen(
                onSignupSuccess = { role ->
                    if (role == "admin") {
                        navController.navigate(Screen.AdminDashboard.route) {
                            popUpTo(Screen.Signup.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.CitizenHome.route) {
                            popUpTo(Screen.Signup.route) { inclusive = true }
                        }
                    }
                },
                onLoginClick = {
                    navController.popBackStack()
                }
            )
        }

        // Citizen screens
        composable(Screen.CitizenHome.route) {
            CitizenHomeScreen(
                context = context,
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToManualInput = { navController.navigate(Screen.ManualInput.route) },
                onNavigateToHelp = { navController.navigate(Screen.Help.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToProfile = { navController.navigate(Screen.CitizenProfile.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ManualInput.route) {
            ManualInputScreen(
                context = context,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                context = context,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.CitizenProfile.route) {
            CitizenProfileScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Admin screens
        composable(Screen.AdminDashboard.route) {
            AdminDashboardScreen(
                onNavigateToUsers = { navController.navigate(Screen.AdminUsers.route) },
                onNavigateToThresholds = { navController.navigate(Screen.AdminThresholds.route) },
                onNavigateToRecommendations = { navController.navigate(Screen.AdminRecommendations.route) },
                onNavigateToDatasets = { navController.navigate(Screen.AdminDatasets.route) },
                onNavigateToFeedback = { navController.navigate(Screen.AdminFeedback.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.AdminUsers.route) {
            AdminUsersScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AdminThresholds.route) {
            AdminThresholdsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AdminRecommendations.route) {
            AdminRecommendationsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AdminDatasets.route) {
            AdminDatasetsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AdminFeedback.route) {
            AdminFeedbackScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

