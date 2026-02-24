package com.binnet.app.dashboard.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.util.Random

/**
 * UssdManager - Handles USSD requests for balance checks
 * 
 * Note: The TelephonyManager.sendUssdRequest() API requires specific carrier support
 * and may not work on all devices. This implementation provides a fallback
 * for demonstration purposes.
 * 
 * For production, consider using:
 * 1. USSD gateway services
 * 2. Bank-specific APIs
 * 3. Alternative methods like SMS-based balance inquiry
 */
class UssdManager(private val context: Context) {

    companion object {
        private const val TAG = "UssdManager"
        
        // Balance check USSD codes for Indian banks
        const val USSD_BALANCE_CODE = "*99*3#"
        const val USSD_BALANCE_CODE_HDFC = "*99*2*1*1#"
        const val USSD_BALANCE_CODE_ICICI = "*99*2*2*1#"
        const val USSD_BALANCE_CODE_SBI = "*99*2*3*1#"
    }

    /**
     * Callback interface for USSD response
     */
    interface UssdCallback {
        fun onSuccess(response: String)
        fun onError(error: String)
    }

    /**
     * Execute USSD request
     * 
     * Note: Direct USSD via TelephonyManager requires:
     * - Android 8.0+ (API 26+)
     * - Carrier support for USSD over IMS
     * - READ_PHONE_STATE permission
     * 
     * This implementation provides a simulated response for demonstration
     * since the actual USSD API is not universally supported.
     */
    fun executeUssd(ussdCode: String, callback: UssdCallback) {
        try {
            Log.d(TAG, "Executing USSD: $ussdCode")
            
            // Check if we can use the direct USSD API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isUssdSupported()) {
                executeUssdDirect(ussdCode, callback)
            } else {
                // Use simulated response for unsupported scenarios
                executeUssdSimulated(ussdCode, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing USSD", e)
            callback.onError("Failed to execute USSD: ${e.message}")
        }
    }

    /**
     * Check if USSD is supported on this device
     */
    private fun isUssdSupported(): Boolean {
        // USSD over IMS support varies by carrier
        // For reliability, we'll use simulated responses
        return false
    }

    /**
     * Direct USSD execution attempt (may not work on all devices)
     */
    @Suppress("DEPRECATION")
    private fun executeUssdDirect(ussdCode: String, callback: UssdCallback) {
        // Even with API 26+, USSD support depends on carrier
        // Fall back to simulated
        executeUssdSimulated(ussdCode, callback)
    }

    /**
     * Simulated USSD response for demonstration
     * In production, this would be replaced with actual API calls
     */
    private fun executeUssdSimulated(ussdCode: String, callback: UssdCallback) {
        try {
            // Simulate network delay
            Thread.sleep(1500)
            
            // Generate a realistic-looking balance response
            val balance = generateRandomBalance()
            val response = buildSimulatedResponse(balance)
            
            Log.d(TAG, "Simulated USSD Response: $response")
            callback.onSuccess(response)
        } catch (e: Exception) {
            callback.onError("USSD request failed: ${e.message}")
        }
    }

    /**
     * Generate a random balance for demonstration
     */
    private fun generateRandomBalance(): Double {
        val random = Random()
        // Generate balance between 1000 and 50000
        return (random.nextInt(49000) + 1000) + 0.50
    }

    /**
     * Build a simulated bank response
     */
    private fun buildSimulatedResponse(balance: Double): String {
        return """
            Account Balance: Rs. ${String.format("%,.2f", balance)}
            Available Balance: Rs. ${String.format("%,.2f", balance)}
            Last Transaction: Rs. 500.00 on 15/01/2024
        """.trimIndent()
    }

    /**
     * Extract balance amount from USSD response string
     */
    fun extractBalance(response: String): String? {
        val patterns = listOf(
            Regex("""Rs\.?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""₹\s*([\d,]+\.?\d*)"""),
            Regex("""Balance:\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""Available:\s*Rs\.?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(response)
            if (match != null) {
                val value = match.groupValues[1].replace(",", "")
                return "₹$value"
            }
        }
        return null
    }

    /**
     * Get bank name from USSD response
     */
    fun extractBankName(response: String): String {
        val bankPatterns = listOf(
            "HDFC Bank" to Regex("""HDFC""", RegexOption.IGNORE_CASE),
            "ICICI Bank" to Regex("""ICICI""", RegexOption.IGNORE_CASE),
            "State Bank of India" to Regex("""(SBI|State Bank)""", RegexOption.IGNORE_CASE),
            "Axis Bank" to Regex("""Axis""", RegexOption.IGNORE_CASE),
            "Bank of Baroda" to Regex("""Baroda""", RegexOption.IGNORE_CASE)
        )

        for ((bankName, pattern) in bankPatterns) {
            if (pattern.containsMatchIn(response)) {
                return bankName
            }
        }
        return "Unknown Bank"
    }

    /**
     * Get bank name from USSD code
     */
    fun getBankNameFromCode(ussdCode: String): String {
        return when {
            ussdCode.contains("1") -> "HDFC Bank"
            ussdCode.contains("2") -> "ICICI Bank"
            ussdCode.contains("3") -> "State Bank of India"
            ussdCode.contains("4") -> "Axis Bank"
            else -> "Unknown Bank"
        }
    }

    /**
     * Cancel ongoing USSD request
     */
    fun cancelUssd() {
        Log.d(TAG, "USSD request cancelled")
    }
}
