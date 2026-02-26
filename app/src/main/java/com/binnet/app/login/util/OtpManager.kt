package com.binnet.app.login.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Random

/**
 * OtpManager - Handles OTP generation and verification for bank & mobile number verification
 * Used as an additional security layer before checking bank balance
 */
class OtpManager(private val context: Context) {

    companion object {
        private const val TAG = "OtpManager"
        private const val PREFS_NAME = "binnet_otp_prefs"
        private const val KEY_MOBILE_HASH = "mobile_hash"
        private const val KEY_BANK_VERIFIED = "bank_verified"
        private const val KEY_MOBILE_VERIFIED = "mobile_verified"
        private const val KEY_OTP_EXPIRY = "otp_expiry"
        private const val KEY_GENERATED_OTP = "generated_otp"
        private const val KEY_LAST_OTP = "last_demo_otp"
        private const val KEY_LAST_OTP_TIME = "last_otp_time"
        private const val OTP_LENGTH = 6
        private const val OTP_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val sharedPreferences: SharedPreferences

    init {
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
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Generate a 6-digit OTP
     * For demo purposes, stores the OTP so it can be retrieved and displayed
     */
    fun generateOtp(): String {
        val random = Random()
        val otp = String.format("%06d", random.nextInt(1000000))
        
        // Store OTP with expiry time (for demo purposes)
        sharedPreferences.edit()
            .putString(KEY_GENERATED_OTP, otp)
            .putLong(KEY_OTP_EXPIRY, System.currentTimeMillis() + OTP_VALIDITY_MS)
            .putString(KEY_LAST_OTP, otp)
            .putLong(KEY_LAST_OTP_TIME, System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "Generated OTP: $otp")
        return otp
    }

    /**
     * Get the last generated OTP for demo purposes
     * This allows the user to see the OTP in the UI
     */
    fun getLastDemoOtp(): String? {
        val lastOtpTime = sharedPreferences.getLong(KEY_LAST_OTP_TIME, 0)
        val expiryTime = 5 * 60 * 1000L // 5 minutes
        
        // Check if OTP is still valid
        return if (System.currentTimeMillis() - lastOtpTime < expiryTime) {
            sharedPreferences.getString(KEY_LAST_OTP, null)
        } else {
            null
        }
    }

    /**
     * Verify the entered OTP
     */
    fun verifyOtp(enteredOtp: String): OtpVerificationResult {
        val storedOtp = sharedPreferences.getString(KEY_GENERATED_OTP, null)
        val expiryTime = sharedPreferences.getLong(KEY_OTP_EXPIRY, 0)
        
        // Check if OTP has expired
        if (System.currentTimeMillis() > expiryTime) {
            return OtpVerificationResult.EXPIRED
        }
        
        // Check if OTP matches
        return if (storedOtp != null && storedOtp == enteredOtp) {
            // Clear OTP after successful verification
            sharedPreferences.edit()
                .remove(KEY_GENERATED_OTP)
                .remove(KEY_OTP_EXPIRY)
                .apply()
            OtpVerificationResult.SUCCESS
        } else {
            OtpVerificationResult.INVALID
        }
    }

    /**
     * Check if mobile number has been verified
     */
    fun isMobileVerified(): Boolean {
        return sharedPreferences.getBoolean(KEY_MOBILE_VERIFIED, false)
    }

    /**
     * Check if bank has been verified
     */
    fun isBankVerified(): Boolean {
        return sharedPreferences.getBoolean(KEY_BANK_VERIFIED, false)
    }

    /**
     * Mark mobile number as verified
     */
    fun setMobileVerified(verified: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_MOBILE_VERIFIED, verified)
            .apply()
    }

    /**
     * Mark bank as verified
     */
    fun setBankVerified(verified: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_BANK_VERIFIED, verified)
            .apply()
    }

    /**
     * Store mobile number hash for verification
     */
    fun storeMobileHash(mobileNumber: String) {
        val hash = mobileNumber.hashCode().toString()
        sharedPreferences.edit()
            .putString(KEY_MOBILE_HASH, hash)
            .apply()
    }

    /**
     * Get stored mobile number hash
     */
    fun getMobileHash(): String? {
        return sharedPreferences.getString(KEY_MOBILE_HASH, null)
    }

    /**
     * Reset all verification status
     */
    fun resetVerification() {
        sharedPreferences.edit()
            .putBoolean(KEY_MOBILE_VERIFIED, false)
            .putBoolean(KEY_BANK_VERIFIED, false)
            .remove(KEY_GENERATED_OTP)
            .remove(KEY_OTP_EXPIRY)
            .apply()
    }

    /**
     * Request OTP for mobile verification
     * Generates OTP for demo purposes
     */
    fun requestMobileOtp(mobileNumber: String): OtpRequestResult {
        // Store mobile number hash for verification
        storeMobileHash(mobileNumber)
        
        // Generate OTP
        generateOtp()
        
        Log.d(TAG, "OTP requested for mobile: $mobileNumber")
        
        return OtpRequestResult.SENT
    }

    /**
     * Request OTP for bank verification
     * Generates OTP for demo purposes
     */
    fun requestBankOtp(): OtpRequestResult {
        // Generate OTP
        generateOtp()
        
        Log.d(TAG, "Bank OTP requested")
        
        return OtpRequestResult.SENT
    }
}

/**
 * Result of OTP verification
 */
sealed class OtpVerificationResult {
    data object SUCCESS : OtpVerificationResult()
    data object INVALID : OtpVerificationResult()
    data object EXPIRED : OtpVerificationResult()
}

/**
 * Result of OTP request
 */
sealed class OtpRequestResult {
    data object SENT : OtpRequestResult()
    data object FAILED : OtpRequestResult()
    data object ALREADY_VERIFIED : OtpRequestResult()
}
