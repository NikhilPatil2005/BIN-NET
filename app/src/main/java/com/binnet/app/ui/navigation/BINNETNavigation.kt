package com.binnet.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.binnet.app.login.screens.PinSetupScreen
import com.binnet.app.ui.screens.BalanceDetailScreen
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
            DashboardScreen(
                onCheckBalanceClick = {
                    navController.navigate(Screen.BalanceDetail.route)
                }
            )
        }

        composable(Screen.BalanceDetail.route) {
            BalanceDetailScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onSeeAllTransactionsClick = {
                    navController.navigate(Screen.History.route)
                }
            )
        }

        composable(Screen.History.route) {
            // TODO: Add HistoryScreen
            DashboardScreen()
        }

        composable(Screen.Payment.route) {
            // TODO: Add PaymentScreen
            DashboardScreen()
        }

        composable(Screen.QRCode.route) {
            // TODO: Add QRCodeScreen
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
    data object BalanceDetail : Screen("balance_detail")
    data object Payment : Screen("payment")
    data object History : Screen("history")
    data object QRCode : Screen("qrcode")
}
