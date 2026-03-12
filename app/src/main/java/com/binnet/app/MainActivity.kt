package com.binnet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.binnet.app.ui.navigation.BINNETNavigation
import com.binnet.app.ui.theme.BINNETTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val pinManager = com.binnet.app.login.util.PinManager(this)
        val startDestination = if (pinManager.isOnboardingCompleted()) {
            com.binnet.app.ui.navigation.Screen.Login.route
        } else {
            com.binnet.app.ui.navigation.Screen.Onboarding.route
        }

        setContent {
            BINNETTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BINNETNavigation(startDestination = startDestination)
                }
            }
        }
    }
}
