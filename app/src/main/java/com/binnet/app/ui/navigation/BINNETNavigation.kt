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
import com.binnet.app.ui.screens.PayContactScreen
import com.binnet.app.ui.screens.SuccessScreen
import com.binnet.app.login.screens.OnboardingScreen

@Composable
fun BINNETNavigation(startDestination: String = Screen.Onboarding.route) {
    val navController = rememberNavController()
    
    // Get context from the navigation composable
    val context = LocalContext.current
    
    // Create BankPreferencesManager using application context
    val bankPreferencesManager = remember(context) { 
        BankPreferencesManager(context.applicationContext) 
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onNavigateToPinSetup = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            PinSetupScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
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
                onPayContactsClick = {
                    navController.navigate(Screen.PayContact.route)
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
        composable(Screen.PayContact.route) {
            PayContactScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object BankSelection : Screen("bank_selection")
    data object Dashboard : Screen("dashboard")
    data object BalanceDetail : Screen("balance_detail")
    data object PayContact : Screen("pay_contact")
    data object Success : Screen("success")
    data object Payment : Screen("payment")
    data object History : Screen("history")
    data object QRCode : Screen("qrcode")
}
