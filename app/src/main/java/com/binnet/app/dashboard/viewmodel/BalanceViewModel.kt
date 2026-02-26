package com.binnet.app.dashboard.viewmodel

import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binnet.app.data.local.BinnetDatabase
import com.binnet.app.data.local.entity.TransactionEntity
import com.binnet.app.dashboard.util.UniversalUssdService
import com.binnet.app.login.util.BankPreferencesManager
import com.binnet.app.login.util.OtpManager
import com.binnet.app.login.util.OtpVerificationResult
import com.binnet.app.login.util.PinManager
import com.binnet.app.login.util.PinValidationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * BalanceViewModel - Handles balance checking using Universal USSD (*99#)
 * With OTP verification for bank and mobile number
 */
class BalanceViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    
    private val pinManager = PinManager(context)
    private val bankPreferencesManager = BankPreferencesManager(context)
    private val otpManager = OtpManager(context)
    
    // Universal USSD Service - works with ANY bank
    private val universalUssdService = UniversalUssdService(context)
    
    private val database = BinnetDatabase.getDatabase(context)
    private val transactionDao = database.transactionDao()

    // UI States
    private val _balanceState = MutableStateFlow<BalanceState>(BalanceState.Idle)
    val balanceState: StateFlow<BalanceState> = _balanceState.asStateFlow()

    private val _pinVerificationState = MutableStateFlow<PinVerificationStatus>(PinVerificationStatus.Idle)
    val pinVerificationState: StateFlow<PinVerificationStatus> = _pinVerificationState.asStateFlow()

    private val _otpVerificationState = MutableStateFlow<OtpState>(OtpState.Idle)
    val otpVerificationState: StateFlow<OtpState> = _otpVerificationState.asStateFlow()

    private val _currentBank = MutableStateFlow<BankInfo?>(null)
    val currentBank: StateFlow<BankInfo?> = _currentBank.asStateFlow()

    private val _recentTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val recentTransactions: StateFlow<List<TransactionEntity>> = _recentTransactions.asStateFlow()

    private val _balanceData = MutableStateFlow<BalanceData?>(null)
    val balanceData: StateFlow<BalanceData?> = _balanceData.asStateFlow()

    // Communication status for UI
    private val _isCommunicating = MutableStateFlow(false)
    val isCommunicating: StateFlow<Boolean> = _isCommunicating.asStateFlow()

    // Permission status for UI
    private val _permissionStatus = MutableStateFlow<PermissionStatus>(PermissionStatus.Unknown)
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    // Mobile number from SIM
    private val _mobileNumber = MutableStateFlow<String?>(null)
    val mobileNumber: StateFlow<String?> = _mobileNumber.asStateFlow()

    init {
        loadBankInfo()
        checkPermissions()
        loadMobileNumber()
    }

    private fun loadBankInfo() {
        val bankName = bankPreferencesManager.getSelectedBankName()
        val accountLast4 = bankPreferencesManager.getAccountLast4() ?: "****"
        
        if (bankName != null) {
            _currentBank.value = BankInfo(
                name = bankName,
                accountLast4 = accountLast4,
                upiId = "${bankName.lowercase().replace(" ", "")}@upi"
            )
        } else {
            // Detect bank from SIM carrier
            val simInfo = universalUssdService.getDefaultSim()
            val detectedBank = simInfo?.carrierName ?: "Unknown Bank"
            
            _currentBank.value = BankInfo(
                name = detectedBank,
                accountLast4 = "****",
                upiId = "${detectedBank.lowercase().replace(" ", "")}@upi"
            )
        }
    }

    /**
     * Check and update permission status
     */
    fun checkPermissions() {
        val status = universalUssdService.hasRequiredPermissions()
        _permissionStatus.value = when (status) {
            UniversalUssdService.PermissionStatus.GRANTED -> PermissionStatus.Granted
            UniversalUssdService.PermissionStatus.MISSING_BOTH -> PermissionStatus.MissingBoth
            UniversalUssdService.PermissionStatus.MISSING_PHONE_STATE -> PermissionStatus.MissingPhoneState
            UniversalUssdService.PermissionStatus.MISSING_CALL_PHONE -> PermissionStatus.MissingCallPhone
        }
    }

    /**
     * Get detailed permission error message
     */
    fun getPermissionErrorMessage(): String {
        return universalUssdService.getPermissionErrorMessage()
    }

    /**
     * Get list of missing permissions
     */
    fun getMissingPermissions(): List<String> {
        return universalUssdService.getMissingPermissions()
    }

    /**
     * Load mobile number from SIM
     */
    private fun loadMobileNumber() {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val phoneNumber = telephonyManager.line1Number
            _mobileNumber.value = phoneNumber
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number", e)
            _mobileNumber.value = null
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            _pinVerificationState.value = PinVerificationStatus.Verifying
            
            when (val result = pinManager.validatePin(pin)) {
                is PinValidationResult.SUCCESS -> {
                    _pinVerificationState.value = PinVerificationStatus.Success
                    // Check if OTP verification is needed
                    if (otpManager.isMobileVerified() && otpManager.isBankVerified()) {
                        // Already verified, proceed to check balance
                        checkBalance()
                    } else {
                        // Need OTP verification
                        _otpVerificationState.value = OtpState.VerificationRequired
                    }
                }
                is PinValidationResult.INVALID_PIN -> {
                    _pinVerificationState.value = PinVerificationStatus.InvalidPin(result.remainingAttempts)
                }
                is PinValidationResult.LOCKED_OUT -> {
                    _pinVerificationState.value = PinVerificationStatus.LockedOut(
                        (result.remainingTimeMs / 1000).toInt()
                    )
                }
                is PinValidationResult.NO_PIN_SET -> {
                    _pinVerificationState.value = PinVerificationStatus.NoPinSet
                }
            }
        }
    }

    fun resetPinVerification() {
        _pinVerificationState.value = PinVerificationStatus.Idle
    }

    /**
     * Request OTP for mobile verification
     */
    fun requestMobileOtp() {
        viewModelScope.launch {
            _otpVerificationState.value = OtpState.SendingOtp
            
            val mobileNum = _mobileNumber.value
            if (mobileNum != null) {
                otpManager.requestMobileOtp(mobileNum)
                // Get the demo OTP to show in UI
                val demoOtp = otpManager.getLastDemoOtp()
                val message = if (demoOtp != null) {
                    "Demo OTP: $demoOtp (valid for 5 min)"
                } else {
                    "Mobile OTP sent to ${maskMobileNumber(mobileNum)}"
                }
                _otpVerificationState.value = OtpState.OtpSent(message, demoOtp)
            } else {
                _otpVerificationState.value = OtpState.Error("Could not get mobile number from SIM")
            }
        }
    }

    /**
     * Request OTP for bank verification
     */
    fun requestBankOtp() {
        viewModelScope.launch {
            _otpVerificationState.value = OtpState.SendingOtp
            otpManager.requestBankOtp()
            // Get the demo OTP to show in UI
            val demoOtp = otpManager.getLastDemoOtp()
            val message = if (demoOtp != null) {
                "Demo OTP: $demoOtp (valid for 5 min)"
            } else {
                "Bank OTP sent to your registered mobile number"
            }
            _otpVerificationState.value = OtpState.OtpSent(message, demoOtp)
        }
    }

    /**
     * Verify the entered OTP
     */
    fun verifyOtp(otp: String) {
        viewModelScope.launch {
            _otpVerificationState.value = OtpState.Verifying
            
            delay(500) // Small delay for UX
            
            when (val result = otpManager.verifyOtp(otp)) {
                is OtpVerificationResult.SUCCESS -> {
                    // OTP verified - now check if we need both verifications
                    if (!otpManager.isMobileVerified()) {
                        otpManager.setMobileVerified(true)
                    }
                    if (!otpManager.isBankVerified()) {
                        otpManager.setBankVerified(true)
                    }
                    
                    _otpVerificationState.value = OtpState.Verified
                    // Proceed to check balance
                    checkBalance()
                }
                is OtpVerificationResult.INVALID -> {
                    _otpVerificationState.value = OtpState.InvalidOtp("Invalid OTP. Please try again.")
                }
                is OtpVerificationResult.EXPIRED -> {
                    _otpVerificationState.value = OtpState.Error("OTP expired. Please request a new one.")
                }
            }
        }
    }

    /**
     * Reset OTP verification state
     */
    fun resetOtpVerification() {
        _otpVerificationState.value = OtpState.Idle
    }

    /**
     * Skip OTP verification (for demo purposes)
     */
    fun skipOtpVerification() {
        viewModelScope.launch {
            // For demo, mark as verified
            otpManager.setMobileVerified(true)
            otpManager.setBankVerified(true)
            _otpVerificationState.value = OtpState.Verified
            checkBalance()
        }
    }

    /**
     * Check balance using Universal USSD (*99#)
     * Works for ANY bank linked to the mobile number
     */
    fun checkBalance() {
        viewModelScope.launch {
            _balanceState.value = BalanceState.Loading
            _isCommunicating.value = true
            
            try {
                // Check permissions first using the new detailed status
                val permStatus = universalUssdService.hasRequiredPermissions()
                if (permStatus != UniversalUssdService.PermissionStatus.GRANTED) {
                    _balanceState.value = BalanceState.Error(
                        universalUssdService.getPermissionErrorMessage()
                    )
                    _isCommunicating.value = false
                    return@launch
                }

                // Use Universal USSD - works with ANY bank
                Log.d(TAG, "Executing Universal USSD balance check (*99*1*1#)")
                
                universalUssdService.checkBalanceUniversal(object : UniversalUssdService.UssdCallback {
                    override fun onUssdResponse(response: UniversalUssdService.UssdResponse) {
                        _isCommunicating.value = false
                        processUssdResponse(response)
                    }
                })
            } catch (e: Exception) {
                _isCommunicating.value = false
                _balanceState.value = BalanceState.Error("Failed to check balance: ${e.message}")
            }
        }
    }

    /**
     * Process the universal USSD response
     */
    private fun processUssdResponse(response: UniversalUssdService.UssdResponse) {
        viewModelScope.launch {
            if (!response.success) {
                _balanceState.value = BalanceState.Error(
                    response.errorMessage ?: "Failed to get balance"
                )
                return@launch
            }

            val extractedBalance = response.balance
            val bankName = response.bankName ?: bankPreferencesManager.getSelectedBankName() ?: "Unknown Bank"
            val accountLast4 = response.accountLast4 ?: bankPreferencesManager.getAccountLast4() ?: "****"

            // Save bank info if not already saved
            if (bankPreferencesManager.getSelectedBankName() == null) {
                bankPreferencesManager.saveBankSelection(
                    bankName = bankName,
                    bankUssdCode = "99", // Universal code
                    registeredSimId = universalUssdService.getDefaultSim()?.subscriptionId?.toString() ?: "",
                    accountLast4 = accountLast4
                )
            }

            // Update current bank info dynamically
            _currentBank.value = BankInfo(
                name = bankName,
                accountLast4 = accountLast4,
                upiId = "${bankName.lowercase().replace(" ", "")}@upi"
            )

            // Update balance data
            _balanceData.value = BalanceData(
                balance = extractedBalance ?: "₹0.00",
                bankName = bankName,
                accountLast4 = accountLast4,
                lastUpdated = System.currentTimeMillis(),
                rawResponse = response.rawResponse
            )
            
            _balanceState.value = BalanceState.Success(extractedBalance ?: "₹0.00")
            
            // Load recent transactions
            loadRecentTransactions()
        }
    }

    private fun loadRecentTransactions() {
        viewModelScope.launch {
            try {
                transactionDao.getRecentTransactions().collect { transactions ->
                    _recentTransactions.value = transactions.take(5)
                }
            } catch (e: Exception) {
                _recentTransactions.value = emptyList()
            }
        }
    }

    fun refreshBalance() {
        checkBalance()
    }

    fun clearBalance() {
        _balanceState.value = BalanceState.Idle
        _balanceData.value = null
        _pinVerificationState.value = PinVerificationStatus.Idle
        _otpVerificationState.value = OtpState.Idle
        _isCommunicating.value = false
    }

    /**
     * Get available SIM cards for multi-SIM support
     */
    fun getAvailableSims(): List<UniversalUssdService.SimInfo> {
        return universalUssdService.getAvailableSims()
    }

    /**
     * Check if USSD request is in progress
     */
    fun isRequestInProgress(): Boolean {
        return universalUssdService.isRequestInProgress()
    }

    /**
     * Cancel ongoing USSD request
     */
    fun cancelRequest() {
        universalUssdService.cancelRequest()
        _isCommunicating.value = false
        _balanceState.value = BalanceState.Idle
    }

    private fun maskMobileNumber(number: String): String {
        return if (number.length > 4) {
            "XXXXXX${number.takeLast(4)}"
        } else {
            number
        }
    }

    companion object {
        private const val TAG = "BalanceViewModel"
    }
}

// State classes
sealed class BalanceState {
    data object Idle : BalanceState()
    data object Loading : BalanceState()
    data class Success(val balance: String) : BalanceState()
    data class Error(val message: String) : BalanceState()
}

sealed class PinVerificationStatus {
    data object Idle : PinVerificationStatus()
    data object Verifying : PinVerificationStatus()
    data object Success : PinVerificationStatus()
    data class InvalidPin(val remainingAttempts: Int) : PinVerificationStatus()
    data class LockedOut(val secondsRemaining: Int) : PinVerificationStatus()
    data object NoPinSet : PinVerificationStatus()
}

/**
 * OTP Verification State
 */
sealed class OtpState {
    data object Idle : OtpState()
    data object VerificationRequired : OtpState()
    data object SendingOtp : OtpState()
    data class OtpSent(val message: String, val demoOtp: String? = null) : OtpState()
    data object Verifying : OtpState()
    data object Verified : OtpState()
    data class InvalidOtp(val message: String) : OtpState()
    data class Error(val message: String) : OtpState()
}

/**
 * Permission Status for UI
 */
sealed class PermissionStatus {
    data object Unknown : PermissionStatus()
    data object Granted : PermissionStatus()
    data object MissingBoth : PermissionStatus()
    data object MissingPhoneState : PermissionStatus()
    data object MissingCallPhone : PermissionStatus()
}

data class BankInfo(
    val name: String,
    val accountLast4: String,
    val upiId: String
)

data class BalanceData(
    val balance: String,
    val bankName: String,
    val accountLast4: String = "",
    val lastUpdated: Long,
    val rawResponse: String = ""
)
