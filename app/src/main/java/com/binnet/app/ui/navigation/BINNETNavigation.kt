package com.binnet.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.binnet.app.login.screens.PinSetupScreen
import com.binnet.app.ui.screens.DashboardScreen

/**
 * BINNETNavigation - Handles navigation between screens
 * Uses Jetpack Compose Navigation
 */
@Composable
fun BINNETNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            PinSetupScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen()
        }
    }
}

/**
 * Screen routes for navigation
 */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Payment : Screen("payment")
    data object History : Screen("history")
    data object QRCode : Screen("qrcode")
}
