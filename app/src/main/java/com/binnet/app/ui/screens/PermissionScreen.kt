package com.binnet.app.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binnet.app.ui.viewmodel.PermissionState
import com.binnet.app.ui.viewmodel.PermissionViewModel

/**
 * PermissionScreen - Handles multi-permission request on app startup
 * Uses rememberMultiplePermissionsState for handling multiple permissions
 */
@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit,
    viewModel: PermissionViewModel = viewModel()
) {
    val context = LocalContext.current
    val permissionState by viewModel.permissionState.collectAsState()
    val volteStatus by viewModel.volteStatus.collectAsState()
    
    var showPermissionDialog by remember { mutableStateOf(true) }
    var showVoLTETip by remember { mutableStateOf(false) }
    
    // Check permissions on first composition
    LaunchedEffect(Unit) {
        viewModel.updatePermissionState(context)
        viewModel.checkVolteStatus(context)
    }
    
    // Handle permission state changes
    LaunchedEffect(permissionState) {
        when (permissionState) {
            is PermissionState.AllGranted -> {
                // Check VoLTE after permissions are granted
                viewModel.checkVolteStatus(context)
                if (volteStatus is com.binnet.app.ui.viewmodel.VolteStatus.Unavailable) {
                    showVoLTETip = true
                } else {
                    onPermissionsGranted()
                }
            }
            is PermissionState.PartialGranted -> {
                // Some permissions denied but essential ones granted - can continue
                val denied = (permissionState as PermissionState.PartialGranted).deniedPermissions
                if (denied.contains(android.Manifest.permission.READ_CONTACTS) || 
                    denied.contains(android.Manifest.permission.CAMERA)) {
                    showPermissionDialog = true
                } else {
                    // Essential permissions granted, show optional permission dialog
                    showPermissionDialog = true
                }
            }
            is PermissionState.Denied -> {
                showPermissionDialog = true
            }
            else -> {
                showPermissionDialog = true
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Permission Icons
            PermissionIcons()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title
            Text(
                text = "Welcome to BIN-NET",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "To provide the best offline payment experience, we need a few permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Permission List
            PermissionList()
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Grant Permissions Button
            Button(
                onClick = {
                    showPermissionDialog = true
                },
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text("Grant Permissions")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Skip Button (for essential permissions only)
            TextButton(
                onClick = {
                    // Check if essential permissions are granted
                    if (viewModel.hasEssentialPermissions(context)) {
                        onPermissionsGranted()
                    }
                }
            ) {
                Text("Skip (Limited Features)")
            }
        }
        
        // Permission Dialog
        if (showPermissionDialog) {
            PermissionDialog(
                onDismiss = { showPermissionDialog = false },
                onConfirm = {
                    showPermissionDialog = false
                    // The actual permission request will be handled by the caller
                },
                onOpenSettings = {
                    showPermissionDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }
        
        // VoLTE Tip Dialog
        if (showVoLTETip) {
            VoLTETipDialog(
                onDismiss = {
                    showVoLTETip = false
                    onPermissionsGranted()
                },
                onOpenSettings = {
                    showVoLTETip = false
                    try {
                        val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to general settings
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        context.startActivity(intent)
                    }
                }
            )
        }
    }
}

@Composable
private fun PermissionIcons() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Contacts,
            contentDescription = "Contacts",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Default.Camera,
            contentDescription = "Camera",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = "Phone",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Default.Message,
            contentDescription = "SMS",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PermissionList() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        PermissionItem(
            icon = Icons.Default.Contacts,
            title = "Contacts Access",
            description = "Search and pay your contacts easily"
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionItem(
            icon = Icons.Default.Camera,
            title = "Camera Access",
            description = "Scan UPI QR codes for payments"
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionItem(
            icon = Icons.Default.Phone,
            title = "Phone & Calls",
            description = "Enable USSD transactions"
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionItem(
            icon = Icons.Default.Message,
            title = "SMS Access",
            description = "Receive offline transaction alerts"
        )
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Permissions Required")
        },
        text = {
            Text(
                "BIN-NET needs the following permissions to function properly:\n\n" +
                "• Contacts - Search and pay people\n" +
                "• Camera - Scan QR codes\n" +
                "• Phone - USSD transactions\n" +
                "• SMS - Transaction alerts\n\n" +
                "You can manage these in Settings anytime."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Grant All")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        }
    )
}

@Composable
private fun VoLTETipDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = {
            Text("Optimization Tip ⚡")
        },
        text = {
            Text(
                "Enable VoLTE in your phone settings for 2x faster offline payments!\n\n" +
                "VoLTE (Voice over LTE) ensures stable connectivity for USSD transactions."
            )
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text("Enable VoLTE")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}
