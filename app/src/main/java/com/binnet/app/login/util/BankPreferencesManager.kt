package com.binnet.app.login.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * BankPreferencesManager - Handles secure storage of bank selection
 * Uses EncryptedSharedPreferences to store:
 * - selected_bank_name
 * - bank securely_ussd_code (2-digit NUUP code)
 * - registered_sim_id (subscription ID of the SIM linked to bank)
 * Fixed: Added fallback to regular SharedPreferences if encrypted version fails
 */
class BankPreferencesManager(private val context: Context) {

    companion object {
        private const val TAG = "BankPrefsManager"
        private const val PREFS_NAME = "binnet_bank_prefs"
        private const val PREFS_NAME_FALLBACK = "binnet_bank_prefs_basic"
        
        // Preference keys
        private const val KEY_SELECTED_BANK_NAME = "selected_bank_name"
        private const val KEY_BANK_USSD_CODE = "bank_ussd_code"
        private const val KEY_REGISTERED_SIM_ID = "registered_sim_id"
        private const val KEY_IS_BANK_LINKED = "is_bank_linked"
        private const val KEY_ACCOUNT_LAST_4 = "account_last_4"
    }

    private val sharedPreferences: SharedPreferences

    init {
        // Try to use EncryptedSharedPreferences, but fall back to regular SharedPreferences
        // if there's an encryption error
        sharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, using fallback", e)
            // Clear corrupted prefs and use regular SharedPreferences as fallback
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    /**
     * Check if a bank has been linked
     */
    fun isBankLinked(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_BANK_LINKED, false)
    }

    /**
     * Save bank selection
     */
    fun saveBankSelection(
        bankName: String,
        bankUssdCode: String,
        registeredSimId: String,
        accountLast4: String = ""
    ) {
        sharedPreferences.edit()
            .putString(KEY_SELECTED_BANK_NAME, bankName)
            .putString(KEY_BANK_USSD_CODE, bankUssdCode)
            .putString(KEY_REGISTERED_SIM_ID, registeredSimId)
            .putString(KEY_ACCOUNT_LAST_4, accountLast4)
            .putBoolean(KEY_IS_BANK_LINKED, true)
            .apply()
    }

    /**
     * Get the selected bank name
     */
    fun getSelectedBankName(): String? {
        return sharedPreferences.getString(KEY_SELECTED_BANK_NAME, null)
    }

    /**
     * Get the bank's USSD code (2-digit NUUP code)
     */
    fun getBankUssdCode(): String? {
        return sharedPreferences.getString(KEY_BANK_USSD_CODE, null)
    }

    /**
     * Get the registered SIM subscription ID
     */
    fun getRegisteredSimId(): String? {
        return sharedPreferences.getString(KEY_REGISTERED_SIM_ID, null)
    }

    /**
     * Get the account last 4 digits
     */
    fun getAccountLast4(): String? {
        return sharedPreferences.getString(KEY_ACCOUNT_LAST_4, null)
    }

    /**
     * Clear bank selection (for testing or re-linking)
     */
    fun clearBankSelection() {
        sharedPreferences.edit()
            .remove(KEY_SELECTED_BANK_NAME)
            .remove(KEY_BANK_USSD_CODE)
            .remove(KEY_REGISTERED_SIM_ID)
            .remove(KEY_ACCOUNT_LAST_4)
            .putBoolean(KEY_IS_BANK_LINKED, false)
            .apply()
    }

    /**
     * Build the dynamic USSD string for balance check
     * Format: *99*[BankCode]*1#
     */
    fun buildUssdString(): String? {
        val bankCode = getBankUssdCode() ?: return null
        return "*99*$bankCode*1#"
    }
}

/**
 * Data class representing a bank
 */
data class Bank(
    val name: String,
    val ussdCode: String,
    val logoResId: Int? = null
)

/**
 * List of major Indian banks with their NUUP codes
 * NUUP (National Unified USSD Platform) codes
 */
object IndianBanks {
    val banks = listOf(
        Bank("State Bank of India", "41"),      // SBI
        Bank("HDFC Bank", "43"),                  // HDFC
        Bank("ICICI Bank", "42"),                 // ICICI
        Bank("Axis Bank", "45"),                  // Axis
        Bank("Punjab National Bank", "62"),       // PNB - Changed from 42 to 62
        Bank("Bank of Baroda", "46"),             // BOB
        Bank("Canara Bank", "47"),                // Canara
        Bank("Union Bank of India", "48"),        // Union Bank
        Bank("IDBI Bank", "49"),                  // IDBI
        Bank("Bank of India", "50"),             // BOI
        Bank("Indian Bank", "51"),               // Indian Bank
        Bank("Central Bank of India", "52"),     // CBI
        Bank("Indian Overseas Bank", "53"),      // IOB
        Bank("UCO Bank", "54"),                   // UCO Bank
        Bank("Bank of Maharashtra", "55"),        // BOM
        Bank("Yes Bank", "56"),                   // Yes Bank
        Bank("IndusInd Bank", "57"),              // IndusInd
        Bank("Kotak Mahindra Bank", "58"),       // Kotak
        Bank("Federal Bank", "59"),               // Federal
        Bank("South Indian Bank", "60")          // SIB
    )

    fun getBankByName(name: String): Bank? {
        return banks.find { it.name.equals(name, ignoreCase = true) }
    }

    fun getBankByCode(code: String): Bank? {
        return banks.find { it.ussdCode == code }
    }
}
