package com.binnet.app.dashboard.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binnet.app.data.local.BinnetDatabase
import com.binnet.app.dashboard.model.Contact
import com.binnet.app.dashboard.util.ContactSearchManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * DashboardViewModel - ViewModel for Dashboard screen
 * Handles:
 * - Smart Search: Real-time contact search using ContentResolver (background thread)
 * - Recent Section: Last 5 unique people from transaction history (Room DB)
 * - READ_CONTACTS permission handling
 * - USSD balance check
 * - VoLTE status check
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val TAG = "DashboardViewModel"
    
    // Database reference
    private val database = BinnetDatabase.getDatabase(context)
    private val transactionDao = database.transactionDao()

    // Contact Search Manager - handles ContentResolver queries on IO thread
    private val contactSearchManager = ContactSearchManager(context)

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Contact>>(emptyList())
    val searchResults: StateFlow<List<Contact>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Recent receivers from transaction history (for "Recent" section)
    private val _recentReceivers = MutableStateFlow<List<Contact>>(emptyList())
    val recentReceivers: StateFlow<List<Contact>> = _recentReceivers.asStateFlow()

    // Device contacts (for People section when no transaction history)
    private val _deviceContacts = MutableStateFlow<List<Contact>>(emptyList())
    val deviceContacts: StateFlow<List<Contact>> = _deviceContacts.asStateFlow()

    private val _balanceCheckStatus = MutableStateFlow<BalanceCheckStatus>(BalanceCheckStatus.Idle)
    val balanceCheckStatus: StateFlow<BalanceCheckStatus> = _balanceCheckStatus.asStateFlow()

    // Permission states
    private val _hasContactsPermission = MutableStateFlow(false)
    val hasContactsPermission: StateFlow<Boolean> = _hasContactsPermission.asStateFlow()

    // VoLTE status
    private val _volteStatus = MutableStateFlow<VolteStatus>(VolteStatus.Unknown)
    val volteStatus: StateFlow<VolteStatus> = _volteStatus.asStateFlow()

    // Show VoLTE tip on dashboard
    private val _showVolteTip = MutableStateFlow(false)
    val showVolteTip: StateFlow<Boolean> = _showVolteTip.asStateFlow()

    init {
        // Load recent receivers from transactions on init
        loadRecentReceivers()
        // Check VoLTE status on init
        checkVoLTEStatus()
    }

    /**
     * Check VoLTE status on the device
     * Uses reflection to check if VoLTE is available (API 21+)
     */
    fun checkVoLTEStatus() {
        viewModelScope.launch {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                
                val isVoLteAvailable = try {
                    // Use reflection to call isVoLteSupported (API 21+)
                    val method = TelephonyManager::class.java.getMethod("isVoLteSupported")
                    method.invoke(telephonyManager) as Boolean
                } catch (e: Exception) {
                    // Fallback: check if network type is LTE
                    try {
                        val networkType = telephonyManager.dataNetworkType
                        // NETWORK_TYPE_LTE = 13, NETWORK_TYPE_LTE_CA = 20
                        networkType == 13 || networkType == 20
                    } catch (ex: Exception) {
                        Log.e(TAG, "Fallback VoLTE check failed", ex)
                        false
                    }
                }
                
                _volteStatus.value = if (isVoLteAvailable) {
                    VolteStatus.Available
                } else {
                    VolteStatus.Unavailable
                }
                
                // Show tip if VoLTE is not available
                _showVolteTip.value = !isVoLteAvailable
            } catch (e: Exception) {
                Log.e(TAG, "Error checking VoLTE status", e)
                _volteStatus.value = VolteStatus.Unknown
            }
        }
    }

    /**
     * Open mobile network settings to enable VoLTE
     */
    fun openMobileNetworkSettings() {
        try {
            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open settings", e)
            }
        }
    }

    /**
     * Dismiss VoLTE tip
     */
    fun dismissVolteTip() {
        _showVolteTip.value = false
    }

    /**
     * Set contacts permission status and load data accordingly
     */
    fun setContactsPermissionGranted(granted: Boolean) {
        _hasContactsPermission.value = granted
        if (granted) {
            loadRecentReceivers()
        }
    }

    /**
     * Load recent receivers from Room database transaction history
     * Shows last 5 unique people to whom user sent money successfully
     */
    fun loadRecentReceivers() {
        viewModelScope.launch {
            try {
                transactionDao.getUniqueReceivers().collect { receivers ->
                    val recentList = receivers.take(5).map { receiver ->
                        Contact(
                            id = receiver.receiverUpiId,
                            name = receiver.receiverName,
                            phoneNumber = receiver.receiverUpiId,
                            phone = receiver.receiverUpiId
                        )
                    }
                    _recentReceivers.value = recentList
                    
                    // If no transaction history, load device contacts as fallback
                    if (recentList.isEmpty() && _hasContactsPermission.value) {
                        loadDeviceContacts()
                    }
                }
            } catch (e: Exception) {
                // Handle error - load device contacts as fallback
                if (_hasContactsPermission.value) {
                    loadDeviceContacts()
                }
            }
        }
    }

    /**
     * Load device contacts for People section fallback
     */
    private fun loadDeviceContacts() {
        viewModelScope.launch {
            try {
                // Get first contacts from search (acts as recent contacts)
                val contacts = contactSearchManager.searchContacts("")
                _deviceContacts.value = contacts.take(5)
            } catch (e: Exception) {
                _deviceContacts.value = emptyList()
            }
        }
    }

    /**
     * Smart search - searches contacts by both name and phone number
     * Uses ContactSearchManager which runs on Dispatchers.IO
     * @param query - search query (name or phone number)
     */
    fun searchContacts(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                if (query.isNotBlank()) {
                    // Use ContactSearchManager - handles both name and phone search
                    val results = contactSearchManager.searchContacts(query)
                    _searchResults.value = results
                } else {
                    _searchResults.value = emptyList()
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            }
            _isSearching.value = false
        }
    }

    /**
     * Update search query - triggers debounced search from UI
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clear search results
     */
    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    /**
     * Triggers USSD code to check bank balance
     * Uses the *99# protocol for balance inquiry
     */
    fun checkBankBalance(context: Context) {
        viewModelScope.launch {
            _balanceCheckStatus.value = BalanceCheckStatus.Loading
            try {
                val ussdCode = "*99*3#"
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$ussdCode")
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                _balanceCheckStatus.value = BalanceCheckStatus.Initiated(ussdCode)
            } catch (e: Exception) {
                _balanceCheckStatus.value = BalanceCheckStatus.Error(e.message ?: "Failed to check balance")
            }
        }
    }

    fun resetBalanceStatus() {
        _balanceCheckStatus.value = BalanceCheckStatus.Idle
    }

    // Navigation actions
    fun onScanQRClick() {
        // Navigate to QR Scanner
    }

    fun onPayContactsClick() {
        // Navigate to contacts payment
    }

    fun onBankTransferClick() {
        // Navigate to bank transfer
    }

    fun onSelfTransferClick() {
        // Navigate to self transfer
    }

    fun onCheckCibilScoreClick() {
        // Navigate to CIBIL score check
    }

    fun onTransactionHistoryClick() {
        // Navigate to transaction history
    }

    fun onProfileClick() {
        // Navigate to profile
    }
}

/**
 * Sealed class representing balance check status
 */
sealed class BalanceCheckStatus {
    data object Idle : BalanceCheckStatus()
    data object Loading : BalanceCheckStatus()
    data class Initiated(val ussdCode: String) : BalanceCheckStatus()
    data class Error(val message: String) : BalanceCheckStatus()
}

/**
 * Sealed class representing VoLTE status
 */
sealed class VolteStatus {
    data object Unknown : VolteStatus()
    data object Available : VolteStatus()
    data object Unavailable : VolteStatus()
}
