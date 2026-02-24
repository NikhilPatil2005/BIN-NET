package com.binnet.app.dashboard.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binnet.app.data.local.BinnetDatabase
import com.binnet.app.data.local.entity.TransactionEntity
import com.binnet.app.dashboard.util.UssdManager
import com.binnet.app.login.util.PinManager
import com.binnet.app.login.util.PinValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * BalanceViewModel - ViewModel for Balance Check flow
 * Handles:
 * - PIN verification before balance check
 * - Background USSD execution
 * - Balance extraction from response
 * - Transaction history for the bank
 */
class BalanceViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    
    // Managers
    private val pinManager = PinManager(context)
    private val ussdManager = UssdManager(context)
    private val database = BinnetDatabase.getDatabase(context)
    private val transactionDao = database.transactionDao()

    // Balance state
    private val _balanceState = MutableStateFlow<BalanceState>(BalanceState.Idle)
    val balanceState: StateFlow<BalanceState> = _balanceState.asStateFlow()

    // PIN verification state
    private val _pinVerificationState = MutableStateFlow<PinVerificationState>(PinVerificationState.Idle)
    val pinVerificationState: StateFlow<PinVerificationState> = _pinVerificationState.asStateFlow()

    // Current bank info (placeholder for multi-bank support)
    private val _currentBank = MutableStateFlow<BankInfo?>(null)
    val currentBank: StateFlow<BankInfo?> = _currentBank.asStateFlow()

    // Recent transactions for current bank
    private val _recentTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val recentTransactions: StateFlow<List<TransactionEntity>> = _recentTransactions.asStateFlow()

    // Balance data
    private val _balanceData = MutableStateFlow<BalanceData?>(null)
    val balanceData: StateFlow<BalanceData?> = _balanceData.asStateFlow()

    init {
        // Set default bank info (placeholder)
        _currentBank.value = BankInfo(
            name = "HDFC Bank",
            accountLast4 = "1234",
            upiId = "hdfc@upi"
        )
    }

    /**
     * Verify the user's PIN before allowing balance check
     */
    fun verifyPin(pin: String) {
        viewModelScope.launch {
            _pinVerificationState.value = PinVerificationState.Verifying
            
            when (val result = pinManager.validatePin(pin)) {
                is PinValidationResult.SUCCESS -> {
                    _pinVerificationState.value = PinVerificationState.Success
                    // Proceed to check balance
                    checkBalance()
                }
                is PinValidationResult.INVALID_PIN -> {
                    _pinVerificationState.value = PinVerificationState.InvalidPin(result.remainingAttempts)
                }
                is PinValidationResult.LOCKED_OUT -> {
                    val secondsRemaining = result.remainingTimeMs / 1000
                    _pinVerificationState.value = PinVerificationState.LockedOut(secondsRemaining.toInt())
                }
                is PinValidationResult.NO_PIN_SET -> {
                    _pinVerificationState.value = PinVerificationState.NoPinSet
                }
            }
        }
    }

    /**
     * Reset PIN verification state
     */
    fun resetPinVerification() {
        _pinVerificationState.value = PinVerificationState.Idle
    }

    /**
     * Execute USSD balance check in the background
     */
    fun checkBalance() {
        viewModelScope.launch {
            _balanceState.value = BalanceState.Loading
            
            ussdManager.executeUssd(UssdManager.USSD_BALANCE_CODE, object : UssdManager.UssdCallback {
                override fun onSuccess(response: String) {
                    processBalanceResponse(response)
                }

                override fun onError(error: String) {
                    _balanceState.value = BalanceState.Error(error)
                }
            })
        }
    }

    /**
     * Process the USSD response and extract balance
     */
    private fun processBalanceResponse(response: String) {
        viewModelScope.launch {
            val extractedBalance = ussdManager.extractBalance(response)
            val bankName = ussdManager.extractBankName(response)
            
            if (extractedBalance != null) {
                _balanceData.value = BalanceData(
                    balance = extractedBalance,
                    bankName = bankName,
                    lastUpdated = System.currentTimeMillis(),
                    rawResponse = response
                )
                _balanceState.value = BalanceState.Success(extractedBalance)
                
                // Load recent transactions for this bank
                loadRecentTransactions()
            } else {
                // Try to parse response even if extraction failed
                _balanceState.value = BalanceState.Success("₹0.00")
            }
        }
    }

    /**
     * Load recent transactions for display
     */
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

    /**
     * Refresh balance
     */
    fun refreshBalance() {
        checkBalance()
    }

    /**
     * Clear balance data (when leaving screen)
     */
    fun clearBalance() {
        _balanceState.value = BalanceState.Idle
        _balanceData.value = null
        _pinVerificationState.value = PinVerificationState.Idle
    }
}

/**
 * Balance state sealed class
 */
sealed class BalanceState {
    data object Idle : BalanceState()
    data object Loading : BalanceState()
    data class Success(val balance: String) : BalanceState()
    data class Error(val message: String) : BalanceState()
}

/**
 * PIN verification state sealed class
 */
sealed class PinVerificationState {
    data object Idle : PinVerificationState()
    data object Verifying : PinVerificationState()
    data object Success : PinVerificationState()
    data class InvalidPin(val remainingAttempts: Int) : PinVerificationState()
    data class LockedOut(val secondsRemaining: Int) : PinVerificationState()
    data object NoPinSet : PinVerificationState()
}

/**
 * Bank information data class
 */
data class BankInfo(
    val name: String,
    val accountLast4: String,
    val upiId: String
)

/**
 * Balance data class
 */
data class BalanceData(
    val balance: String,
    val bankName: String,
    val lastUpdated: Long,
    val rawResponse: String = ""
)
