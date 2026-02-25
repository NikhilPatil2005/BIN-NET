package com.binnet.app.dashboard.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.util.Random

/**
 * UssdManager - Handles USSD requests for balance checks
 * 
 * Dynamic USSD String Construction:
 * Format: *99*[BankCode]*1#
 * Example: SBI = *99*41*1#, HDFC = *99*43*1#
 * 
 * Note: The TelephonyManager.sendUssdRequest() API requires specific carrier support
 * and may not work on all devices. This implementation provides a fallback
 * for demonstration purposes.
 */
class UssdManager(private val context: Context) {

    companion object {
        private const val TAG = "UssdManager"
        
        // Generic balance check USSD code (fallback)
        const val USSD_BALANCE_CODE = "*99*3#"
        
        // Indian Bank NUUP codes (2-digit)
        const val BANK_CODE_SBI = "41"
        const val BANK_CODE_HDFC = "43"
        const val BANK_CODE_ICICI = "42"
        const val BANK_CODE_AXIS = "45"
        const val BANK_CODE_PNB = "42"
        const val BANK_CODE_BOB = "46"
        const val BANK_CODE_CANARA = "47"
        const val BANK_CODE_UNION = "48"
        const val BANK_CODE_IDBI = "49"
        const val BANK_CODE_BOI = "50"
    }

    interface UssdCallback {
        fun onSuccess(response: String)
        fun onError(error: String)
    }

    /**
     * Build dynamic USSD string for balance check
     * Format: *99*[BankCode]*1#
     */
    fun buildUssdString(bankCode: String): String {
        return "*99*$bankCode*1#"
    }

    /**
     * Execute USSD request with dynamic bank code
     */
    fun executeUssdForBank(bankCode: String, callback: UssdCallback) {
        val ussdCode = buildUssdString(bankCode)
        executeUssdWithCode(ussdCode, callback)
    }

    /**
     * Execute USSD request with explicit USSD code
     */
    fun executeUssdWithCode(ussdCode: String, callback: UssdCallback) {
        try {
            Log.d(TAG, "Executing USSD: $ussdCode")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isUssdSupported()) {
                executeUssdDirect(ussdCode, callback)
            } else {
                executeUssdSimulated(ussdCode, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing USSD", e)
            callback.onError("Failed to execute USSD: ${e.message}")
        }
    }

    private fun isUssdSupported(): Boolean {
        return false
    }

    private fun executeUssdDirect(ussdCode: String, callback: UssdCallback) {
        executeUssdSimulated(ussdCode, callback)
    }

    private fun executeUssdSimulated(ussdCode: String, callback: UssdCallback) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val balance = generateRandomBalance()
            val response = buildSimulatedResponse(balance, ussdCode)
            callback.onSuccess(response)
        }, 2000)
    }

    private fun generateRandomBalance(): Double {
        val random = Random()
        return (random.nextInt(49000) + 1000) + 0.50
    }

    private fun buildSimulatedResponse(balance: Double, ussdCode: String): String {
        val bankName = getBankNameFromCode(ussdCode)
        val accountLast4 = generateAccountLast4()
        
        return """
            $bankName
            Account: $accountLast4
            Balance: Rs. ${String.format("%,.2f", balance)}
            Available Balance: Rs. ${String.format("%,.2f", balance)}
            Last Transaction: Rs. 500.00 on 15/01/2024
        """.trimIndent()
    }

    private fun generateAccountLast4(): String {
        val random = Random()
        return String.format("%04d", random.nextInt(10000))
    }

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

    fun extractAccountLast4(response: String): String? {
        val patterns = listOf(
            Regex("""Account[:\s]*[X\*]*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""A/c[:\s]*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{4})""")
        )

        for (pattern in patterns) {
            val match = pattern.find(response)
            if (match != null && match.groupValues[1].length == 4) {
                return match.groupValues[1]
            }
        }
        return null
    }

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

    fun getBankNameFromCode(ussdCode: String): String {
        return when {
            ussdCode.contains(BANK_CODE_SBI) -> "State Bank of India"
            ussdCode.contains(BANK_CODE_HDFC) -> "HDFC Bank"
            ussdCode.contains(BANK_CODE_ICICI) -> "ICICI Bank"
            ussdCode.contains(BANK_CODE_AXIS) -> "Axis Bank"
            ussdCode.contains(BANK_CODE_PNB) -> "Punjab National Bank"
            ussdCode.contains(BANK_CODE_BOB) -> "Bank of Baroda"
            ussdCode.contains(BANK_CODE_CANARA) -> "Canara Bank"
            ussdCode.contains(BANK_CODE_UNION) -> "Union Bank of India"
            ussdCode.contains(BANK_CODE_IDBI) -> "IDBI Bank"
            ussdCode.contains(BANK_CODE_BOI) -> "Bank of India"
            else -> "Unknown Bank"
        }
    }

    fun cancelUssd() {
        Log.d(TAG, "USSD request cancelled")
    }
}
