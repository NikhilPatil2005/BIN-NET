package com.binnet.app.login.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binnet.app.login.util.OtpManager
import com.binnet.app.login.util.OtpVerificationResult
import com.binnet.app.login.util.PinManager
import com.binnet.app.login.util.PinValidationResult
import com.binnet.app.login.util.SimCardManager
import com.binnet.app.login.util.SimCardStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PinViewModel - ViewModel for Login Module
 * Handles PIN setup, validation, Mobile Verification and SIM card detection
 * 
 * Fixed: Safe initialization to prevent crashes during ViewModel creation
 */
class PinViewModel(application: Application) : AndroidViewModel(application) {

    private val pinManager = PinManager(application)
    private val simCardManager = SimCardManager(application)
    private val otpManager = OtpManager(application)

    companion object {
        private const val TAG = "PinViewModel"
    }

    // UI State - Initialize with Loading state
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // PIN input state
    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _confirmPin = MutableStateFlow("")
    val confirmPin: StateFlow<String> = _confirmPin.asStateFlow()

    // Mobile Verification state
    private val _mobileNumber = MutableStateFlow("")
    val mobileNumber: StateFlow<String> = _mobileNumber.asStateFlow()

    private val _otp = MutableStateFlow("")
    val otp: StateFlow<String> = _otp.asStateFlow()

    // Error messages
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // SIM card status - Initialize as null (unknown)
    private val _simCardStatus = MutableStateFlow<SimCardStatus?>(null)
    val simCardStatus: StateFlow<SimCardStatus?> = _simCardStatus.asStateFlow()

    // Flag to prevent multiple initialization calls
    private var isInitialized = false

    init {
        // IMMEDIATE synchronous initialization - NO coroutines in init block
        // This prevents any crashes during ViewModel instantiation
        _uiState.value = LoginUiState.Loading
        isInitialized = true
        // Note: Actual SIM/PIN check will be done via checkInitialState() 
        // which is called from the UI after the ViewModel is created
    }

    /**
     * Check initial state - whether PIN is set and SIM is available
     */
    fun checkInitialState() {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            // Check SIM card status
            val simStatus = simCardManager.getSimCardStatus()
            _simCardStatus.value = simStatus

            // Determine initial state based on PIN, SIM, and OTP verification
            val authMode = pinManager.getAuthMode()
            when {
                authMode == com.binnet.app.login.util.AuthMode.DEVICE_LOCK -> {
                    _uiState.value = LoginUiState.DeviceLockPrompt
                }
                authMode == com.binnet.app.login.util.AuthMode.APP_PIN && !pinManager.isPinSet() -> {
                    _uiState.value = LoginUiState.PinSetup
                }
                authMode == com.binnet.app.login.util.AuthMode.APP_PIN -> {
                    _uiState.value = LoginUiState.PinEntry
                }
                else -> {
                    _uiState.value = LoginUiState.PinSetup
                }
            }
        }
    }



    /**
     * Update OTP input
     */
    fun updateOtp(digit: String) {
        if (_otp.value.length < 6) {
            _otp.value += digit
            _errorMessage.value = null
            
            // Auto-validate when 6 digits entered
            if (_otp.value.length == 6) {
                if (_uiState.value is LoginUiState.ForgotPinOtpEntry) {
                    verifyForgotPinOtp()
                }
            }
        }
    }

    /**
     * Delete last OTP digit
     */
    fun deleteOtpDigit() {
        if (_otp.value.isNotEmpty()) {
            _otp.value = _otp.value.dropLast(1)
            _errorMessage.value = null
        }
    }



    /**
     * Initiate Forgot PIN flow
     */
    fun initiateForgotPin() {
        val mobile = otpManager.getStoredMobileNumber()
        if (mobile != null) {
            viewModelScope.launch {
                _uiState.value = LoginUiState.Loading
                otpManager.requestMobileOtp(mobile)
                _otp.value = ""
                _pin.value = ""
                _confirmPin.value = ""
                _uiState.value = LoginUiState.ForgotPinOtpEntry("OTP sent to $mobile to reset your PIN")
            }
        } else {
            // fallback if somehow mobile number is not saved
            _errorMessage.value = "Registered mobile number not found. Please verify again."
            _uiState.value = LoginUiState.PinEntry
        }
    }

    /**
     * Verify the entered forgot PIN OTP
     */
    fun verifyForgotPinOtp() {
        if (_otp.value.length != 6) {
            _errorMessage.value = "Enter a 6-digit OTP"
            return
        }
        
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            when(val result = otpManager.verifyOtp(_otp.value)) {
                is OtpVerificationResult.SUCCESS -> {
                    _uiState.value = LoginUiState.ForgotPinNewPin
                }
                is OtpVerificationResult.INVALID -> {
                    _errorMessage.value = "Invalid OTP. Please try again."
                    _otp.value = ""
                    val mobile = otpManager.getStoredMobileNumber() ?: ""
                    _uiState.value = LoginUiState.ForgotPinOtpEntry("OTP sent to $mobile to reset your PIN")
                }
                is OtpVerificationResult.EXPIRED -> {
                    _errorMessage.value = "OTP Expired. Please request a new one."
                    _otp.value = ""
                    _uiState.value = LoginUiState.PinEntry
                }
            }
        }
    }

    /**
     * Proceed from Forgot PIN New PIN to confirm PIN
     */
    fun proceedToForgotPinConfirm() {
        if (_pin.value.length == 4) {
            _uiState.value = LoginUiState.ForgotPinConfirmPin
        } else {
            _errorMessage.value = "Please enter a 4-digit PIN"
        }
    }

    /**
     * Proceed to final confirmation
     */
    fun proceedToForgotPinFinalConfirm() {
        if (_pin.value.length != 4 || _confirmPin.value.length != 4) {
            _errorMessage.value = "Please complete PIN entry"
            return
        }
        if (_pin.value != _confirmPin.value) {
            _errorMessage.value = "PINs do not match"
            _confirmPin.value = ""
            return
        }
        _uiState.value = LoginUiState.ForgotPinFinalConfirm
    }

    /**
     * Complete Forgot PIN flow
     */
    fun completeForgotPin() {
        viewModelScope.launch {
            val success = pinManager.setPin(_pin.value)
            if (success) {
                _uiState.value = LoginUiState.Success
            } else {
                _errorMessage.value = "Failed to save new PIN. Please try again."
            }
        }
    }

    /**
     * Cancel Forgot PIN flow
     */
    fun cancelForgotPin() {
        _pin.value = ""
        _confirmPin.value = ""
        _otp.value = ""
        _uiState.value = LoginUiState.PinEntry
    }

    /**
     * Update PIN input
     */
    fun updatePin(digit: String) {
        if (_pin.value.length < 4) {
            _pin.value += digit
            _errorMessage.value = null

            // Auto-validate when 4 digits entered in entry mode
            if (_pin.value.length == 4 && _uiState.value == LoginUiState.PinEntry) {
                validatePin()
            }
        }
    }

    /**
     * Delete last PIN digit
     */
    fun deletePinDigit() {
        if (_pin.value.isNotEmpty()) {
            _pin.value = _pin.value.dropLast(1)
        }
    }

    /**
     * Clear PIN
     */
    fun clearPin() {
        _pin.value = ""
        _confirmPin.value = ""
        _errorMessage.value = null
    }

    /**
     * Update confirm PIN input
     */
    fun updateConfirmPin(digit: String) {
        if (_confirmPin.value.length < 4) {
            _confirmPin.value += digit
            _errorMessage.value = null
        }
    }

    /**
     * Delete last confirm PIN digit
     */
    fun deleteConfirmPinDigit() {
        if (_confirmPin.value.isNotEmpty()) {
            _confirmPin.value = _confirmPin.value.dropLast(1)
        }
    }

    /**
     * Proceed from PIN setup to confirm PIN
     */
    fun proceedToConfirmPin() {
        if (_pin.value.length == 4) {
            _uiState.value = LoginUiState.PinConfirm
        } else {
            _errorMessage.value = "Please enter a 4-digit PIN"
        }
    }

    /**
     * Confirm and save PIN
     */
    fun confirmPinAndSetup() {
        viewModelScope.launch {
            if (_pin.value.length != 4) {
                _errorMessage.value = "Please enter a 4-digit PIN"
                return@launch
            }

            if (_confirmPin.value.length != 4) {
                _errorMessage.value = "Please confirm your PIN"
                return@launch
            }

            if (_pin.value != _confirmPin.value) {
                _errorMessage.value = "PINs do not match"
                _confirmPin.value = ""
                return@launch
            }

            // Save the PIN
            val success = pinManager.setPin(_pin.value)
            if (success) {
                _uiState.value = LoginUiState.Success
            } else {
                _errorMessage.value = "Failed to save PIN. Please try again."
            }
        }
    }

    /**
     * Validate PIN during login
     */
    private fun validatePin() {
        viewModelScope.launch {
            val result = pinManager.validatePin(_pin.value)

            when (result) {
                is PinValidationResult.SUCCESS -> {
                    _uiState.value = LoginUiState.Success
                }
                is PinValidationResult.INVALID_PIN -> {
                    _errorMessage.value = "Incorrect PIN. ${result.remainingAttempts} attempts remaining"
                    _pin.value = ""
                }
                is PinValidationResult.LOCKED_OUT -> {
                    val seconds = (result.remainingTimeMs / 1000).toInt()
                    _errorMessage.value = "Too many attempts. Try again in $seconds seconds"
                    _pin.value = ""
                }
                is PinValidationResult.NO_PIN_SET -> {
                    _uiState.value = LoginUiState.PinSetup
                }
            }
        }
    }

    /**
     * Navigate back from confirm to setup
     */
    fun navigateBackToSetup() {
        _confirmPin.value = ""
        _uiState.value = LoginUiState.PinSetup
    }

    /**
     * Check if biometric is available (for future use)
     */
    fun isBiometricAvailable(): Boolean {
        // Will be implemented with BiometricPrompt
        return false
    }

    fun onDeviceLockSuccess() {
        _uiState.value = LoginUiState.Success
    }

    fun setErrorMessage(msg: String) {
        _errorMessage.value = msg
    }
}

/**
 * UI State for Login Module
 */
sealed class LoginUiState {
    data object Loading : LoginUiState()
    data object DeviceLockPrompt : LoginUiState()
    data object PinSetup : LoginUiState()
    data object PinConfirm : LoginUiState()
    data object PinEntry : LoginUiState()
    data class ForgotPinOtpEntry(val message: String) : LoginUiState()
    data object ForgotPinNewPin : LoginUiState()
    data object ForgotPinConfirmPin : LoginUiState()
    data object ForgotPinFinalConfirm : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
