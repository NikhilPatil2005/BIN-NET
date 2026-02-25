package com.binnet.app.dashboard.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binnet.app.data.local.BinnetDatabase
import com.binnet.app.data.local.entity.TransactionEntity
import com.binnet.app.dashboard.util.UssdManager
import com.binnet.app.login.util.BankPreferencesManager
import com.binnet.app.login.util.PinManager
import com.binnet.app.login.util.PinValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BalanceViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    
    private val pinManager = PinManager(context)
    private val bankPreferencesManager = BankPreferencesManager(context)
    private val ussdManager = UssdManager(context)
    private val database = BinnetDatabase.getDatabase(context)
    private val transactionDao = database.transactionDao()

    private val _balanceState = MutableStateFlow<BalanceState>(BalanceState.Idle)
    val balanceState: StateFlow<BalanceState> = _balanceState.asStateFlow()

    private val _pinVerificationState = MutableStateFlow<PinVerificationStatus>(PinVerificationStatus.Idle)
    val pinVerificationState: StateFlow<PinVerificationStatus> = _pinVerificationState.asStateFlow()

    private val _currentBank = MutableStateFlow<BankInfo?>(null)
    val currentBank: StateFlow<BankInfo?> = _currentBank.asStateFlow()

    private val _recentTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val recentTransactions: StateFlow<List<TransactionEntity>> = _recentTransactions.asStateFlow()

    private val _balanceData = MutableStateFlow<BalanceData?>(null)
    val balanceData: StateFlow<BalanceData?> = _balanceData.asStateFlow()

    init {
        loadBankInfo()
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
            _currentBank.value = BankInfo(
                name = "HDFC Bank",
                accountLast4 = "1234",
                upiId = "hdfc@upi"
            )
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            _pinVerificationState.value = PinVerificationStatus.Verifying
            
            when (val result = pinManager.validatePin(pin)) {
                is PinValidationResult.SUCCESS -> {
                    _pinVerificationState.value = PinVerificationStatus.Success
                    checkBalance()
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

    fun checkBalance() {
        viewModelScope.launch {
            _balanceState.value = BalanceState.Loading
            
            try {
                val bankCode = bankPreferencesManager.getBankUssdCode()
                
                if (bankCode == null) {
                    _balanceState.value = BalanceState.Error("No bank linked. Please link your bank account first.")
                    return@launch
                }

                val ussdCode = ussdManager.buildUssdString(bankCode)
                Log.d(TAG, "Executing USSD with bank code: $bankCode, USSD: $ussdCode")
                
                ussdManager.executeUssdForBank(bankCode, object : UssdManager.UssdCallback {
                    override fun onSuccess(response: String) {
                        processUssdResponse(response, bankCode)
                    }
                    
                    override fun onError(error: String) {
                        _balanceState.value = BalanceState.Error(error)
                    }
                })
            } catch (e: Exception) {
                _balanceState.value = BalanceState.Error("Failed to check balance: ${e.message}")
            }
        }
    }

    private fun processUssdResponse(response: String, bankCode: String) {
        viewModelScope.launch {
            val extractedBalance = ussdManager.extractBalance(response)
            val bankName = bankPreferencesManager.getSelectedBankName() 
                ?: ussdManager.getBankNameFromCode(bankCode)
            val accountLast4 = ussdManager.extractAccountLast4(response) 
                ?: bankPreferencesManager.getAccountLast4() 
                ?: "****"

            if (extractedBalance != null) {
                if (bankPreferencesManager.getAccountLast4() == null && accountLast4 != "****") {
                    bankPreferencesManager.saveBankSelection(
                        bankName = bankName,
                        bankUssdCode = bankCode,
                        registeredSimId = bankPreferencesManager.getRegisteredSimId() ?: "",
                        accountLast4 = accountLast4
                    )
                }

                _currentBank.value = BankInfo(
                    name = bankName,
                    accountLast4 = accountLast4,
                    upiId = "${bankName.lowercase().replace(" ", "")}@upi"
                )

                _balanceData.value = BalanceData(
                    balance = extractedBalance,
                    bankName = bankName,
                    accountLast4 = accountLast4,
                    lastUpdated = System.currentTimeMillis(),
                    rawResponse = response
                )
                _balanceState.value = BalanceState.Success(extractedBalance)
                
                loadRecentTransactions()
            } else {
                _balanceState.value = BalanceState.Success("₹0.00")
            }
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
    }

    companion object {
        private const val TAG = "BalanceViewModel"
    }
}

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
