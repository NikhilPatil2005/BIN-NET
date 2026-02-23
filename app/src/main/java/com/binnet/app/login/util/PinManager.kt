package com.binnet.app.login.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * PinManager - Handles secure storage and validation of user's 4-digit PIN
 * Uses EncryptedSharedPreferences for offline-first secure storage
 */
class PinManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "binnet_secure_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_IS_PIN_SET = "is_pin_set"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_TIME = "lockout_time"
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30000L // 30 seconds
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Checks if a PIN has been set up
     */
    fun isPinSet(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_PIN_SET, false)
    }

    /**
     * Sets up a new PIN (hashes it before storing for security)
     * @return true if PIN was set successfully
     */
    fun setPin(pin: String): Boolean {
        return try {
            val hashedPin = hashPin(pin)
            sharedPreferences.edit()
                .putString(KEY_PIN_HASH, hashedPin)
                .putBoolean(KEY_IS_PIN_SET, true)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates the entered PIN against the stored hash
     * @return PinValidationResult indicating success or failure reason
     */
    fun validatePin(pin: String): PinValidationResult {
        // Check if locked out
        val lockoutTime = sharedPreferences.getLong(KEY_LOCKOUT_TIME, 0)
        if (lockoutTime > 0) {
            val remainingTime = lockoutTime + LOCKOUT_DURATION_MS - System.currentTimeMillis()
            if (remainingTime > 0) {
                return PinValidationResult.LOCKED_OUT(remainingTime)
            } else {
                // Lockout expired, reset
                resetFailedAttempts()
            }
        }

        val storedHash = sharedPreferences.getString(KEY_PIN_HASH, null)
        if (storedHash == null) {
            return PinValidationResult.NO_PIN_SET
        }

        val enteredHash = hashPin(pin)
        return if (storedHash == enteredHash) {
            resetFailedAttempts()
            PinValidationResult.SUCCESS
        } else {
            val failedAttempts = incrementFailedAttempts()
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                initiateLockout()
                PinValidationResult.LOCKED_OUT(LOCKOUT_DURATION_MS)
            } else {
                PinValidationResult.INVALID_PIN(MAX_FAILED_ATTEMPTS - failedAttempts)
            }
        }
    }

    /**
     * Gets the number of remaining PIN attempts
     */
    fun getRemainingAttempts(): Int {
        val failedAttempts = sharedPreferences.getInt(KEY_FAILED_ATTEMPTS, 0)
        return MAX_FAILED_ATTEMPTS - failedAttempts
    }

    /**
     * Resets the PIN (for testing or account recovery)
     */
    fun resetPin() {
        sharedPreferences.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_IS_PIN_SET, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_TIME, 0)
            .apply()
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun incrementFailedAttempts(): Int {
        val current = sharedPreferences.getInt(KEY_FAILED_ATTEMPTS, 0)
        val new = current + 1
        sharedPreferences.edit().putInt(KEY_FAILED_ATTEMPTS, new).apply()
        return new
    }

    private fun resetFailedAttempts() {
        sharedPreferences.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_TIME, 0)
            .apply()
    }

    private fun initiateLockout() {
        sharedPreferences.edit()
            .putLong(KEY_LOCKOUT_TIME, System.currentTimeMillis())
            .apply()
    }
}

/**
 * Result of PIN validation
 */
sealed class PinValidationResult {
    data object SUCCESS : PinValidationResult()
    data object NO_PIN_SET : PinValidationResult()
    data class INVALID_PIN(val remainingAttempts: Int) : PinValidationResult()
    data class LOCKED_OUT(val remainingTimeMs: Long) : PinValidationResult()
}
