package com.binnet.app.login.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * SimCardManager - Utility class to detect SIM card availability
 * This is crucial for BIN-NET as it relies on device-bound SIM authentication
 */
class SimCardManager(private val context: Context) {

    /**
     * Checks if the device has a SIM card available
     * @return SimCardStatus indicating the SIM card state
     */
    fun getSimCardStatus(): SimCardStatus {
        // Check for permission first
        if (!hasPhoneStatePermission()) {
            return SimCardStatus.PERMISSION_REQUIRED
        }

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return try {
            when (telephonyManager.simState) {
                TelephonyManager.SIM_STATE_READY -> {
                    // SIM is ready, get operator info
                    val operatorName = telephonyManager.simOperatorName
                    val operatorCode = telephonyManager.simOperator
                    
                    if (operatorName.isNotEmpty() || operatorCode.isNotEmpty()) {
                        SimCardStatus.AVAILABLE(
                            operatorName = operatorName.ifEmpty { "Unknown Operator" },
                            operatorCode = operatorCode,
                            isRoaming = telephonyManager.isNetworkRoaming
                        )
                    } else {
                        SimCardStatus.AVAILABLE(
                            operatorName = "Unknown",
                            operatorCode = "",
                            isRoaming = false
                        )
                    }
                }
                TelephonyManager.SIM_STATE_ABSENT -> SimCardStatus.NOT_AVAILABLE
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> SimCardStatus.LOCKED
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> SimCardStatus.LOCKED
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> SimCardStatus.LOCKED
                TelephonyManager.SIM_STATE_PERM_DISABLED -> SimCardStatus.DISABLED
                // SIM_STATE_ERROR = 10, not available on all API levels
                10 -> SimCardStatus.ERROR
                else -> SimCardStatus.UNKNOWN
            }
        } catch (e: Exception) {
            SimCardStatus.ERROR
        }
    }

    /**
     * Checks if READ_PHONE_STATE permission is granted
     */
    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Gets the phone number if available (may not be available on all devices/carriers)
     */
    fun getPhoneNumber(): String? {
        if (!hasPhoneStatePermission()) return null

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                telephonyManager.line1Number
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Gets the SIM card subscriber ID (IMSI) as an alternative identifier
     * Note: iccId was deprecated and removed, using subscriberId as fallback
     */
    fun getSimSubscriberId(): String? {
        if (!hasPhoneStatePermission()) return null

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.subscriberId
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Sealed class representing SIM card status
 */
sealed class SimCardStatus {
    data class AVAILABLE(
        val operatorName: String,
        val operatorCode: String,
        val isRoaming: Boolean
    ) : SimCardStatus()

    data object NOT_AVAILABLE : SimCardStatus()
    data object LOCKED : SimCardStatus()
    data object DISABLED : SimCardStatus()
    data object ERROR : SimCardStatus()
    data object UNKNOWN : SimCardStatus()
    data object PERMISSION_REQUIRED : SimCardStatus()
}
