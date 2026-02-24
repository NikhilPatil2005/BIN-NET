package com.binnet.app.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PermissionViewModel - Handles multi-permission requests and VoLTE check
 * Manages:
 * - READ_CONTACTS (for searching/paying people)
 * - CAMERA (for scanning UPI QR codes)
 * - CALL_PHONE & READ_PHONE_STATE (for USSD transactions)
 * - SEND_SMS & RECEIVE_SMS (for offline transaction alerts)
 * - VoLTE status check
 */
class PermissionViewModel : ViewModel() {

    companion object {
        private const val TAG = "PermissionViewModel"
        
        // All required permissions for BIN-NET
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        
        // Essential permissions (app cannot function without these)
        val ESSENTIAL_PERMISSIONS = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE
        )
        
        // Optional permissions (app can work without these but with limited features)
        val OPTIONAL_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
    }

    // Permission states
    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.Idle)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    // VoLTE status
    private val _volteStatus = MutableStateFlow<VolteStatus>(VolteStatus.Unknown)
    val volteStatus: StateFlow<VolteStatus> = _volteStatus.asStateFlow()

    // Network status
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Unknown)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    /**
     * Check which permissions are already granted
     */
    fun checkPermissions(context: Context): Map<String, Boolean> {
        return REQUIRED_PERMISSIONS.associate { permission ->
            val isGranted = ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
            permission to isGranted
        }
    }

    /**
     * Check if essential permissions are granted
     */
    fun hasEssentialPermissions(context: Context): Boolean {
        return ESSENTIAL_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get list of denied permissions
     */
    fun getDeniedPermissions(context: Context): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Update permission state after user responds to permission request
     */
    fun updatePermissionState(context: Context) {
        viewModelScope.launch {
            val hasEssential = hasEssentialPermissions(context)
            val hasAll = hasAllPermissions(context)
            val deniedPermissions = getDeniedPermissions(context)
            
            _permissionState.value = when {
                hasAll -> PermissionState.AllGranted
                hasEssential -> PermissionState.PartialGranted(deniedPermissions)
                else -> PermissionState.Denied(deniedPermissions)
            }
        }
    }

    /**
     * Check VoLTE status on the device
     * Uses reflection to check if VoLTE is available (API 21+)
     */
    fun checkVolteStatus(context: Context) {
        viewModelScope.launch {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                
                val isVoLteAvailable = try {
                    // Use reflection to call isVoLteSupported (API 21+)
                    val method = TelephonyManager::class.java.getMethod("isVoLteSupported")
                    method.invoke(telephonyManager) as Boolean
                } catch (e: Exception) {
                    // Fallback: check if network type is LTE
                    try {
                        val networkType = telephonyManager.dataNetworkType
                        // NETWORK_TYPE_LTE = 13, NETWORK_TYPE_LTE_CA = 20
                        networkType == 13 || networkType == 20
                    } catch (ex: Exception) {
                        Log.e(TAG, "Fallback VoLTE check failed", ex)
                        false
                    }
                }
                
                _volteStatus.value = if (isVoLteAvailable) {
                    VolteStatus.Available
                } else {
                    VolteStatus.Unavailable
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking VoLTE status", e)
                _volteStatus.value = VolteStatus.Unknown
            }
        }
    }

    /**
     * Check network connectivity
     */
    fun checkNetworkStatus(context: Context) {
        viewModelScope.launch {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                
                _networkStatus.value = when {
                    capabilities == null -> NetworkStatus.Disconnected
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                            NetworkStatus.Connected
                        } else {
                            NetworkStatus.ConnectedNoInternet
                        }
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.Connected
                    else -> NetworkStatus.Connected
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking network status", e)
                _networkStatus.value = NetworkStatus.Unknown
            }
        }
    }

    /**
     * Get intent to open mobile network settings
     */
    fun getMobileNetworkSettingsIntent(): Intent {
        return Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Get intent to open app settings (for manual permission grant)
     */
    fun getAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Reset permission state
     */
    fun resetPermissionState() {
        _permissionState.value = PermissionState.Idle
    }
}

/**
 * Permission state sealed class
 */
sealed class PermissionState {
    data object Idle : PermissionState()
    data object AllGranted : PermissionState()
    data class PartialGranted(val deniedPermissions: List<String>) : PermissionState()
    data class Denied(val deniedPermissions: List<String>) : PermissionState()
}

/**
 * VoLTE status sealed class
 */
sealed class VolteStatus {
    data object Unknown : VolteStatus()
    data object Available : VolteStatus()
    data object Unavailable : VolteStatus()
}

/**
 * Network status sealed class
 */
sealed class NetworkStatus {
    data object Unknown : NetworkStatus()
    data object Connected : NetworkStatus()
    data object ConnectedNoInternet : NetworkStatus()
    data object Disconnected : NetworkStatus()
}
