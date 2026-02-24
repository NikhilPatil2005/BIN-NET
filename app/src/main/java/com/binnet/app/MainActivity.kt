package com.binnet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binnet.app.ui.navigation.BINNETNavigation
import com.binnet.app.ui.screens.PermissionScreen
import com.binnet.app.ui.theme.BINNETTheme
import com.binnet.app.ui.viewmodel.PermissionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BINNETTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BINNETApp()
                }
            }
        }
    }
}

@Composable
fun BINNETApp() {
    val viewModel: PermissionViewModel = viewModel()
    val permissionState by viewModel.permissionState.collectAsState()
    
    var hasPermissions by remember { mutableStateOf(false) }
    var hasCheckedPermissions by remember { mutableStateOf(false) }
    
    // Permission launcher for multiple permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        // Update permission state after user responds
        val allGranted = permissionsResult.values.all { it }
        val essentialGranted = PermissionViewModel.ESSENTIAL_PERMISSIONS.all { permission ->
            permissionsResult[permission] == true
        }
        
        if (essentialGranted) {
            hasPermissions = true
        }
        hasCheckedPermissions = true
    }
    
    // Check initial permissions
    LaunchedEffect(Unit) {
        // Request permissions on first launch
        permissionLauncher.launch(PermissionViewModel.REQUIRED_PERMISSIONS)
    }
    
    // Show permission screen until essential permissions are granted
    if (!hasPermissions || !hasCheckedPermissions) {
        PermissionScreen(
            onPermissionsGranted = {
                hasPermissions = true
                hasCheckedPermissions = true
            },
            viewModel = viewModel
        )
    } else {
        // Main app navigation
        BINNETNavigation()
    }
}
