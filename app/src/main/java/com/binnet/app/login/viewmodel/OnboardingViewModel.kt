package com.binnet.app.login.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binnet.app.login.util.Bank
import com.binnet.app.login.util.BankPreferencesManager
import com.binnet.app.login.util.OtpManager
import com.binnet.app.login.util.SimCardManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OnboardingUiState {
    data object Loading : OnboardingUiState()
    data object NotificationPermission : OnboardingUiState()
    data object MobileInput : OnboardingUiState()
    data object AccountSelection : OnboardingUiState()
    data object OtpVerification : OnboardingUiState()
    data object VerifyingMobile : OnboardingUiState()
    data object AddBankIntro : OnboardingUiState()
    data object BankSelection : OnboardingUiState()
    data class BankPermissions(val bank: Bank) : OnboardingUiState()
    data class VerifyingBank(val bank: Bank) : OnboardingUiState()
    data object SecuritySelection : OnboardingUiState()
    data class Done(val usePin: Boolean) : OnboardingUiState()
    data class Error(val message: String) : OnboardingUiState()
}

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val otpManager = OtpManager(application)
    private val simCardManager = SimCardManager(application)
    private val bankPreferencesManager = BankPreferencesManager(application)

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.NotificationPermission)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _mobileNumber = MutableStateFlow("")
    val mobileNumber: StateFlow<String> = _mobileNumber.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _suggestedPhoneNumbers = MutableStateFlow<List<String>>(emptyList())
    val suggestedPhoneNumbers: StateFlow<List<String>> = _suggestedPhoneNumbers.asStateFlow()

    private val _userEmails = MutableStateFlow<List<String>>(emptyList())
    val userEmails: StateFlow<List<String>> = _userEmails.asStateFlow()

    private val _selectedEmail = MutableStateFlow<String?>(null)
    val selectedEmail: StateFlow<String?> = _selectedEmail.asStateFlow()

    private var selectedBank: Bank? = null

    init {
        // Optionally detect phone number here if possible
        _uiState.value = OnboardingUiState.NotificationPermission
    }

    fun onNotificationPermissionHandled() {
        _uiState.value = OnboardingUiState.MobileInput
        fetchPhoneNumbers()
    }

    fun fetchPhoneNumbers() {
        try {
            val subscriptionManager = getApplication<Application>().getSystemService(android.telephony.SubscriptionManager::class.java)
            val subs = subscriptionManager?.activeSubscriptionInfoList
            val numbers = subs?.mapNotNull { it.number }?.filter { it.isNotBlank() } ?: emptyList()
            _suggestedPhoneNumbers.value = numbers.map { it.takeLast(10) }.distinct()
        } catch (e: SecurityException) {
            // Permission not granted yet
        }
    }

    fun fetchEmails() {
        try {
            val accountManager = android.accounts.AccountManager.get(getApplication())
            val accounts = accountManager.getAccountsByType("com.google")
            val emails = accounts.map { it.name }.distinct()
            _userEmails.value = emails
            if (emails.isNotEmpty() && _selectedEmail.value == null) {
                _selectedEmail.value = emails.first()
            }
        } catch (e: SecurityException) {
            // GET_ACCOUNTS not granted yet
        }
    }

    fun selectEmail(email: String) {
        _selectedEmail.value = email
    }

    fun updateMobileNumber(number: String) {
        _mobileNumber.value = number
        _errorMessage.value = null
    }

    fun submitMobileNumber() {
        if (_mobileNumber.value.length < 10) {
            _errorMessage.value = "Enter a valid mobile number"
            return
        }
        _uiState.value = OnboardingUiState.AccountSelection
    }

    fun onAccountSelected() {
        _uiState.value = OnboardingUiState.VerifyingMobile
        
        viewModelScope.launch {
            otpManager.requestMobileOtp(_mobileNumber.value)
            _uiState.value = OnboardingUiState.OtpVerification
        }
    }

    fun verifyOtp(otp: String) {
        viewModelScope.launch {
            val result = otpManager.verifyOtp(otp)
            if (result == com.binnet.app.login.util.OtpVerificationResult.SUCCESS) {
                otpManager.setMobileVerified(true)
                _uiState.value = OnboardingUiState.AddBankIntro
            } else {
                _errorMessage.value = "Invalid or expired OTP."
            }
        }
    }

    fun onAddBankAccountClicked() {
        _uiState.value = OnboardingUiState.BankSelection
    }

    fun onSkipBankAdded() {
        _uiState.value = OnboardingUiState.SecuritySelection
    }

    fun onBankSelected(bank: Bank) {
        selectedBank = bank
        _uiState.value = OnboardingUiState.BankPermissions(bank)
    }

    fun onBankPermissionsHandled(granted: Boolean) {
        val bank = selectedBank ?: return
        if (granted) {
            _uiState.value = OnboardingUiState.VerifyingBank(bank)
            
            viewModelScope.launch {
                // Simulate bank verification delay
                delay(2500)
                
                // Store bank selection securely
                bankPreferencesManager.saveBankSelection(
                    bankName = bank.name,
                    bankUssdCode = bank.ussdCode,
                    registeredSimId = "sim_1", // Mocked or fetch from SimCardManager
                    accountLast4 = "" // Let USSD provide real account or fallback to ****
                )
                
                _uiState.value = OnboardingUiState.SecuritySelection
            }
        } else {
            // Permission denied, go back to bank list or show error
            _uiState.value = OnboardingUiState.BankSelection
            _errorMessage.value = "Phone and SMS permissions are required to verify your bank securely."
        }
    }

    fun onSecuritySelected(usePin: Boolean) {
        val pinManager = com.binnet.app.login.util.PinManager(getApplication())
        val authMode = if (usePin) com.binnet.app.login.util.AuthMode.APP_PIN else com.binnet.app.login.util.AuthMode.DEVICE_LOCK
        pinManager.setAuthMode(authMode)
        pinManager.setOnboardingCompleted(true)
        _uiState.value = OnboardingUiState.Done(usePin)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
