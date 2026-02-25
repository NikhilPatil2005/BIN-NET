package com.binnet.app.login.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
 * Handles PIN setup, validation, and SIM card detection
 * 
 * Fixed: Safe initialization to prevent crashes during ViewModel creation
 */
class PinViewModel(application: Application) : AndroidViewModel(application) {

    private val pinManager = PinManager(application)
    private val simCardManager = SimCardManager(application)

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
     * Determines the initial UI state based on SIM and PIN status
     */
    private fun determineInitialState(simStatus: SimCardStatus): LoginUiState {
        return when {
            !simCardManager.hasPhoneStatePermission() -> {
                LoginUiState.PermissionRequired
            }
            simStatus is SimCardStatus.NOT_AVAILABLE || simStatus is SimCardStatus.ERROR -> {
                LoginUiState.SimNotAvailable
            }
            simStatus is SimCardStatus.PERMISSION_REQUIRED -> {
                LoginUiState.PermissionRequired
            }
            !pinManager.isPinSet() -> {
                LoginUiState.PinSetup
            }
            else -> {
                LoginUiState.PinEntry
            }
        }
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

            // Determine initial state based on PIN and SIM
            when {
                !simCardManager.hasPhoneStatePermission() -> {
                    _uiState.value = LoginUiState.PermissionRequired
                }
                simStatus is SimCardStatus.NOT_AVAILABLE || simStatus is SimCardStatus.ERROR -> {
                    _uiState.value = LoginUiState.SimNotAvailable
                }
                !pinManager.isPinSet() -> {
                    _uiState.value = LoginUiState.PinSetup
                }
                else -> {
                    _uiState.value = LoginUiState.PinEntry
                }
            }
        }
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

    /**
     * Permission granted callback
     */
    fun onPermissionGranted() {
        checkInitialState()
    }

    /**
     * Permission denied callback
     */
    fun onPermissionDenied() {
        _uiState.value = LoginUiState.PermissionRequired
    }
}

/**
 * UI State for Login Module
 */
sealed class LoginUiState {
    data object Loading : LoginUiState()
    data object PermissionRequired : LoginUiState()
    data object SimNotAvailable : LoginUiState()
    data object PinSetup : LoginUiState()
    data object PinConfirm : LoginUiState()
    data object PinEntry : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
