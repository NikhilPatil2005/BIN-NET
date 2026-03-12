package com.binnet.app.login.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        private const val KEY_MOBILE_NUMBER = "mobile_number"
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
            .putString(KEY_MOBILE_NUMBER, mobileNumber)
            .apply()
    }

    /**
     * Get stored mobile number hash
     */
    fun getMobileHash(): String? {
        return sharedPreferences.getString(KEY_MOBILE_HASH, null)
    }

    /**
     * Get stored mobile number
     */
    fun getStoredMobileNumber(): String? {
        return sharedPreferences.getString(KEY_MOBILE_NUMBER, null)
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
        val otp = generateOtp()
        
        // Fetch numbers on this device
        val deviceNumbers = mutableListOf<String>()
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val subManager = context.getSystemService(android.telephony.SubscriptionManager::class.java)
                val subs = subManager?.activeSubscriptionInfoList
                subs?.forEach { info ->
                    info.number?.let { if (it.isNotBlank()) deviceNumbers.add(it.takeLast(10)) }
                }
            }
            val telManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            telManager.line1Number?.let { if (it.isNotBlank()) deviceNumbers.add(it.takeLast(10)) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not fetch device numbers", e)
        }

        val isNumberOnDevice = deviceNumbers.isEmpty() || deviceNumbers.contains(mobileNumber.takeLast(10))

        // Show realistic Heads-Up Notification to simulate an SMS arriving ONLY if the number is on this device
        if (isNumberOnDevice) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val channelId = "otp_channel"
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        channelId, 
                        "OTP SMS Simulation", 
                        android.app.NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Simulates incoming SMS messages for OTPs"
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }
                
                val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("New message")
                    .setContentText("Your BIN-NET verification OTP is $otp")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH) // Heads-up wrapper
                    .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    
                notificationManager.notify(1001, builder.build())
                Log.d(TAG, "Notification pushed to simulate SMS OTP")
            } catch(e: Exception) {
                Log.e(TAG, "Failed to show OTP notification", e)
            }
        } else {
            Log.d(TAG, "Number $mobileNumber is not on this device. Bypassing local notification simulation.")
        }

        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                android.telephony.SmsManager.getDefault()
            }
            
            val sentIntent = PendingIntent.getBroadcast(
                context, 0, Intent("SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val deliveredIntent = PendingIntent.getBroadcast(
                context, 0, Intent("SMS_DELIVERED"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            smsManager?.sendTextMessage(mobileNumber, null, "Your BIN-NET verification OTP is $otp", sentIntent, deliveredIntent)
            Log.d(TAG, "Actual SMS sent to: $mobileNumber")
        } catch(e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
        }
        
        Log.d(TAG, "OTP requested for mobile: $mobileNumber")
        
        return OtpRequestResult.SENT
    }

    /**
     * Request OTP for bank verification
     * Generates OTP for demo purposes
     */
    fun requestBankOtp(): OtpRequestResult {
        // Generate OTP
        val otp = generateOtp()

        // Show realistic Heads-Up Notification to simulate an SMS arriving
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "otp_channel"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId, 
                    "OTP SMS Simulation", 
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Simulates incoming SMS messages for OTPs"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("New message")
                .setContentText("Bank OTP requested. Your BIN-NET OTP is $otp")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                
            notificationManager.notify(1002, builder.build())
            Log.d(TAG, "Bank OTP notification pushed to simulate SMS OTP")
        } catch(e: Exception) {
            Log.e(TAG, "Failed to show Bank OTP notification", e)
        }
        
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
