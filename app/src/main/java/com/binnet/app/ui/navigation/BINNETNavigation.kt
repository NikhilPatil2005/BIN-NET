package com.binnet.app.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.binnet.app.login.util.BankPreferencesManager
import com.binnet.app.login.screens.PinSetupScreen
import com.binnet.app.ui.screens.BalanceDetailScreen
import com.binnet.app.ui.screens.BankSelectionScreen
import com.binnet.app.ui.screens.DashboardScreen
import com.binnet.app.ui.screens.SuccessScreen

@Composable
fun BINNETNavigation() {
    val navController = rememberNavController()
    
    // Get context from the navigation composable
    val context = LocalContext.current
    
    // Create BankPreferencesManager using application context
    val bankPreferencesManager = remember(context) { 
        BankPreferencesManager(context.applicationContext) 
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            PinSetupScreen(
                onLoginSuccess = {
                    // After PIN is verified, check if bank is linked
                    if (bankPreferencesManager.isBankLinked()) {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    } else {
                        // Navigate to bank selection first
                        navController.navigate(Screen.BankSelection.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.BankSelection.route) {
            BankSelectionScreen(
                bankPreferencesManager = bankPreferencesManager,
                onBankSelected = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.BankSelection.route) { inclusive = true }
                    }
                },
                onSkip = {
                    // Allow skipping but warn user
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.BankSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onCheckBalanceClick = {
                    navController.navigate(Screen.BalanceDetail.route)
                },
                onLinkBankClick = {
                    navController.navigate(Screen.BankSelection.route)
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
                },
                onBalanceSuccess = {
                    navController.navigate(Screen.Success.route)
                }
            )
        }

        composable(Screen.Success.route) {
            SuccessScreen(
                onDone = {
                    navController.popBackStack(Screen.Dashboard.route, false)
                }
            )
        }

        composable(Screen.History.route) {
            DashboardScreen()
        }

        composable(Screen.Payment.route) {
            DashboardScreen()
        }

        composable(Screen.QRCode.route) {
            DashboardScreen()
        }
    }
}

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object BankSelection : Screen("bank_selection")
    data object Dashboard : Screen("dashboard")
    data object BalanceDetail : Screen("balance_detail")
    data object Success : Screen("success")
    data object Payment : Screen("payment")
    data object History : Screen("history")
    data object QRCode : Screen("qrcode")
}
