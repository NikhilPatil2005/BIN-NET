package com.binnet.app.login.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

class SimCardManager(private val context: Context) {

    companion object {
        private const val TAG = "SimCardManager"
    }

    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private val subscriptionManager: SubscriptionManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        } else {
            null
        }
    }

    fun getSimCardStatus(): SimCardStatus {
        if (!hasPhoneStatePermission()) {
            return SimCardStatus.PERMISSION_REQUIRED
        }

        return try {
            when (telephonyManager.simState) {
                TelephonyManager.SIM_STATE_READY -> {
                    val operatorName = telephonyManager.simOperatorName
                    val operatorCode = telephonyManager.simOperator
                    
                    if (operatorName.isNotEmpty() || operatorCode.isNotEmpty()) {
                        SimCardStatus.AVAILABLE(
                            operatorName = operatorName.ifEmpty { "Unknown Operator" },
                            operatorCode = operatorCode,
                            isRoaming = telephonyManager.isNetworkRoaming,
                            subscriptionId = getDefaultSubscriptionId()
                        )
                    } else {
                        SimCardStatus.AVAILABLE(
                            operatorName = "Unknown",
                            operatorCode = "",
                            isRoaming = false,
                            subscriptionId = getDefaultSubscriptionId()
                        )
                    }
                }
                TelephonyManager.SIM_STATE_ABSENT -> SimCardStatus.NOT_AVAILABLE
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> SimCardStatus.LOCKED
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> SimCardStatus.LOCKED
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> SimCardStatus.LOCKED
                TelephonyManager.SIM_STATE_PERM_DISABLED -> SimCardStatus.DISABLED
                10 -> SimCardStatus.ERROR
                else -> SimCardStatus.UNKNOWN
            }
        } catch (e: Exception) {
            SimCardStatus.ERROR
        }
    }

    fun getAvailableSimCards(): List<SimCardInfo> {
        val simCards = mutableListOf<SimCardInfo>()
        
        if (!hasPhoneStatePermission()) {
            return simCards
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && subscriptionManager != null) {
                val activeSubscriptions = subscriptionManager!!.activeSubscriptionInfoList
                if (activeSubscriptions != null) {
                    for (subscription in activeSubscriptions) {
                        simCards.add(
                            SimCardInfo(
                                subscriptionId = subscription.subscriptionId,
                                displayName = subscription.displayName?.toString() ?: "SIM ${subscription.subscriptionId}",
                                carrierName = subscription.carrierName?.toString() ?: "Unknown",
                                iccId = subscription.iccId ?: "",
                                slotIndex = subscription.simSlotIndex,
                                isDefault = subscription.subscriptionId == getDefaultSubscriptionId()
                            )
                        )
                    }
                }
            } else {
                val status = getSimCardStatus()
                if (status is SimCardStatus.AVAILABLE) {
                    simCards.add(
                        SimCardInfo(
                            subscriptionId = status.subscriptionId,
                            displayName = "SIM 1",
                            carrierName = status.operatorName,
                            iccId = "",
                            slotIndex = 0,
                            isDefault = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available SIM cards", e)
        }

        return simCards
    }

    private fun getDefaultSubscriptionId(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SubscriptionManager.getDefaultSubscriptionId()
        } else {
            0
        }
    }

    fun isSimMatching(registeredSimId: String?): Boolean {
        if (registeredSimId.isNullOrEmpty()) {
            return true
        }

        val currentSimId = getDefaultSubscriptionId().toString()
        return currentSimId == registeredSimId
    }

    fun getSimInfoById(subscriptionId: Int): SimCardInfo? {
        return getAvailableSimCards().find { it.subscriptionId == subscriptionId }
    }

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getPhoneNumber(): String? {
        if (!hasPhoneStatePermission()) return null

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

    fun getSimSubscriberId(): String? {
        if (!hasPhoneStatePermission()) return null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.subscriberId
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    fun getCurrentSubscriptionId(): Int {
        return getDefaultSubscriptionId()
    }
}

sealed class SimCardStatus {
    data class AVAILABLE(
        val operatorName: String,
        val operatorCode: String,
        val isRoaming: Boolean,
        val subscriptionId: Int
    ) : SimCardStatus()

    data object NOT_AVAILABLE : SimCardStatus()
    data object LOCKED : SimCardStatus()
    data object DISABLED : SimCardStatus()
    data object ERROR : SimCardStatus()
    data object UNKNOWN : SimCardStatus()
    data object PERMISSION_REQUIRED : SimCardStatus()
}

data class SimCardInfo(
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val iccId: String,
    val slotIndex: Int,
    val isDefault: Boolean
)
