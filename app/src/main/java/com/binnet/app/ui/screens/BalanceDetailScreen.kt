package com.binnet.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binnet.app.dashboard.viewmodel.BalanceState
import com.binnet.app.dashboard.viewmodel.BalanceViewModel
import com.binnet.app.dashboard.viewmodel.BankInfo
import com.binnet.app.dashboard.viewmodel.OtpState
import com.binnet.app.dashboard.viewmodel.PermissionStatus
import com.binnet.app.dashboard.viewmodel.PinVerificationStatus
import com.binnet.app.data.local.entity.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceDetailScreen(
    viewModel: BalanceViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onSeeAllTransactionsClick: () -> Unit = {},
    onBalanceSuccess: () -> Unit = {}
) {
    val balanceState by viewModel.balanceState.collectAsState()
    val pinVerificationState by viewModel.pinVerificationState.collectAsState()
    val otpState by viewModel.otpVerificationState.collectAsState()
    val currentBank by viewModel.currentBank.collectAsState()
    val balanceData by viewModel.balanceData.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val isCommunicating by viewModel.isCommunicating.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val mobileNumber by viewModel.mobileNumber.collectAsState()

    val context = LocalContext.current

    var showPinSheet by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var showOtpSheet by remember { mutableStateOf(false) }
    var enteredOtp by remember { mutableStateOf("") }
    var currentStep by remember { mutableIntStateOf(0) } // 0 = PIN, 1 = Mobile OTP, 2 = Bank OTP

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.checkPermissions()
    }

    // Use rememberCoroutineScope for sheet state to prevent crashes
    val pinSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val otpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Navigate to success screen after balance check succeeds
    LaunchedEffect(balanceState) {
        if (balanceState is BalanceState.Success) {
            kotlinx.coroutines.delay(500)
            onBalanceSuccess()
        }
    }

    // Handle PIN success
    LaunchedEffect(pinVerificationState) {
        if (pinVerificationState is PinVerificationStatus.Success) {
            showPinSheet = false
            enteredPin = ""
            viewModel.resetPinVerification()
            
            // Check if OTP is needed
            when (val otp = otpState) {
                is OtpState.VerificationRequired -> {
                    showOtpSheet = true
                    currentStep = 1 // Start with mobile OTP
                }
                is OtpState.Verified -> {
                    // Already verified, balance check will proceed automatically
                }
                else -> {
                    showOtpSheet = true
                    currentStep = 1
                }
            }
        }
    }

    // Handle OTP state changes
    LaunchedEffect(otpState) {
        when (otpState) {
            is OtpState.Verified -> {
                showOtpSheet = false
                enteredOtp = ""
                viewModel.resetOtpVerification()
            }
            is OtpState.Error -> {
                // Stay on OTP sheet, show error
            }
            is OtpState.InvalidOtp -> {
                // Stay on OTP sheet, show error
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Check Balance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Show permission card if needed
            if (permissionStatus !is PermissionStatus.Granted) {
                item {
                    PermissionRequiredCard(
                        permissionStatus = permissionStatus,
                        onRequestPermissions = {
                            val permissions = viewModel.getMissingPermissions()
                            permissionLauncher.launch(permissions.toTypedArray())
                        },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Bank Account Details Card - Always visible
            item {
                BankAccountDetailsCard(
                    bankInfo = currentBank,
                    balanceData = balanceData
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                AnimatedVisibility(
                    visible = balanceState is BalanceState.Success || balanceState is BalanceState.Loading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    BalanceDisplayCard(
                        balanceState = balanceState,
                        balanceData = balanceData,
                        onRefresh = { viewModel.refreshBalance() }
                    )
                }

                AnimatedVisibility(
                    visible = balanceState !is BalanceState.Success && balanceState !is BalanceState.Loading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column {
                        ViewBalanceButton(
                            onClick = { showPinSheet = true }
                        )
                        
                        if (permissionStatus !is PermissionStatus.Granted) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Grant permissions to check balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Red,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                RecentTransactionsSection(
                    transactions = recentTransactions,
                    onSeeAllClick = onSeeAllTransactionsClick
                )
            }
        }

        if (balanceState is BalanceState.Error) {
            val errorMessage = (balanceState as BalanceState.Error).message
            val isPermissionError = errorMessage.contains("permission", ignoreCase = true)
            
            ErrorCard(
                message = errorMessage,
                onRetry = { 
                    if (isPermissionError) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } else {
                        showPinSheet = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }

    // PIN Verification Sheet
    if (showPinSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showPinSheet = false
                enteredPin = ""
                viewModel.resetPinVerification()
            },
            sheetState = pinSheetState
        ) {
            PinVerificationContent(
                pinVerificationState = pinVerificationState,
                enteredPin = enteredPin,
                onPinChange = { enteredPin = it },
                onVerifyClick = { viewModel.verifyPin(enteredPin) },
                onDismiss = { 
                    showPinSheet = false
                    enteredPin = ""
                    viewModel.resetPinVerification()
                },
                showPin = showPin,
                onTogglePinVisibility = { showPin = !showPin }
            )
        }
    }

    // OTP Verification Sheet
    if (showOtpSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showOtpSheet = false
                enteredOtp = ""
                viewModel.resetOtpVerification()
            },
            sheetState = otpSheetState
        ) {
            OtpVerificationContent(
                otpState = otpState,
                currentStep = currentStep,
                mobileNumber = mobileNumber,
                onRequestMobileOtp = { viewModel.requestMobileOtp() },
                onRequestBankOtp = { viewModel.requestBankOtp() },
                enteredOtp = enteredOtp,
                onOtpChange = { enteredOtp = it },
                onVerifyOtp = { viewModel.verifyOtp(enteredOtp) },
                onSkip = { viewModel.skipOtpVerification() },
                onDismiss = { 
                    showOtpSheet = false
                    enteredOtp = ""
                    viewModel.resetOtpVerification()
                },
                onStepChange = { currentStep = it }
            )
        }
    }
}

/**
 * Bank Account Details Card - Shows bank info and account details
 */
@Composable
private fun BankAccountDetailsCard(
    bankInfo: BankInfo?,
    balanceData: com.binnet.app.dashboard.viewmodel.BalanceData?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bankInfo?.name ?: "Loading...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Bank Account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                if (balanceData != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            
            // Account Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(
                    icon = Icons.Default.CreditCard,
                    label = "Account Number",
                    value = "XXXX XXXX XXXX ${bankInfo?.accountLast4 ?: "****"}"
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(
                    icon = Icons.Default.AccountBalanceWallet,
                    label = "UPI ID",
                    value = bankInfo?.upiId ?: "Not available"
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(
                    icon = Icons.Default.Security,
                    label = "Status",
                    value = if (balanceData != null) "Verified & Active" else "Pending Verification",
                    valueColor = if (balanceData != null) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
            }
        }
    }
}

@Composable
private fun DetailItem(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = valueColor
            )
        }
    }
}

@Composable
private fun PermissionRequiredCard(
    permissionStatus: PermissionStatus,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val message = when (permissionStatus) {
                is PermissionStatus.MissingBoth -> "Phone State and Call permissions are required to check your bank balance."
                is PermissionStatus.MissingPhoneState -> "Phone State permission is required to check your bank balance."
                is PermissionStatus.MissingCallPhone -> "Call Phone permission is required to check your bank balance."
                else -> "Permissions are required to check your bank balance."
            }
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF795548),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Phone, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Permissions")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings", color = Color(0xFF795548))
            }
        }
    }
}

@Composable
private fun ViewBalanceButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "View Balance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BalanceDisplayCard(
    balanceState: BalanceState,
    balanceData: com.binnet.app.dashboard.viewmodel.BalanceData?,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (balanceState) {
                is BalanceState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Communicating with your Bank's server...", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    Text(text = "Please wait", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                is BalanceState.Success -> {
                    Text(text = "Available Balance", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = balanceState.balance,
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 50.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Updated just now", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh")
                    }
                }
                is BalanceState.Error -> {
                    Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Error fetching balance", style = MaterialTheme.typography.bodyLarge, color = Color.Red)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun RecentTransactionsSection(transactions: List<TransactionEntity>, onSeeAllClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Recent Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onSeeAllClick) {
                    Text("See All")
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (transactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Savings, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "No recent transactions", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                }
            } else {
                transactions.forEachIndexed { index, transaction ->
                    TransactionItem(transaction = transaction)
                    if (index < transactions.size - 1) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(transaction: TransactionEntity) {
    val amountValue = transaction.amount?.toString()?.toDoubleOrNull() ?: 0.0
    val isCredit = amountValue > 0
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isCredit) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCredit) Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                contentDescription = null,
                tint = if (isCredit) Color(0xFFE53935) else Color(0xFF43A047),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = transaction.receiverName ?: "Unknown", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = formatDate(transaction.timestamp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text(text = "₹${transaction.amount ?: "0.00"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (isCredit) Color(0xFFE53935) else Color(0xFF43A047))
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = Color(0xFFE53935))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE53935), modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun PinVerificationContent(
    pinVerificationState: PinVerificationStatus,
    enteredPin: String,
    onPinChange: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onDismiss: () -> Unit,
    showPin: Boolean,
    onTogglePinVisibility: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Enter App PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Verify your identity to check balance", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = enteredPin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) onPinChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("4-digit PIN") },
            placeholder = { Text("••••") },
            singleLine = true,
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = onTogglePinVisibility) {
                    Icon(imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showPin) "Hide PIN" else "Show PIN")
                }
            }
        )

        when (val state = pinVerificationState) {
            is PinVerificationStatus.InvalidPin -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Incorrect PIN. ${state.remainingAttempts} attempts remaining", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
            }
            is PinVerificationStatus.LockedOut -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Too many attempts. Try again in ${state.secondsRemaining} seconds", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
            }
            is PinVerificationStatus.NoPinSet -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "No PIN set. Please set up PIN first.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onVerifyClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = enteredPin.length == 4 && pinVerificationState !is PinVerificationStatus.Verifying,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (pinVerificationState is PinVerificationStatus.Verifying) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Verify & Check Balance")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun OtpVerificationContent(
    otpState: OtpState,
    currentStep: Int,
    mobileNumber: String?,
    onRequestMobileOtp: () -> Unit,
    onRequestBankOtp: () -> Unit,
    enteredOtp: String,
    onOtpChange: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    onStepChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Step indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            StepIndicator(
                step = 1,
                label = "Mobile",
                isActive = currentStep == 1,
                isCompleted = currentStep > 1
            )
            Spacer(modifier = Modifier.width(16.dp))
            StepIndicator(
                step = 2,
                label = "Bank",
                isActive = currentStep == 2,
                isCompleted = currentStep > 2
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        when (currentStep) {
            1 -> {
                // Mobile OTP Step
                Text(text = "Verify Mobile Number", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                val displayNumber = mobileNumber?.let { 
                    if (it.length > 4) "XXXXXX${it.takeLast(4)}" else it 
                } ?: "your mobile number"
                
                Text(
                    text = "OTP will be sent to $displayNumber",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                when (otpState) {
                    is OtpState.SendingOtp -> {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Sending OTP...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    is OtpState.OtpSent -> {
                        // Show message and demo OTP
                        Text(text = otpState.message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50))
                        
                        // Show demo OTP prominently if available
                        otpState.demoOtp?.let { demoOtp ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "DEMO OTP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF43A047)
                                    )
                                    Text(
                                        text = demoOtp,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF2E7D32),
                                        letterSpacing = 8.sp
                                    )
                                    Text(
                                        text = "Enter this OTP to verify",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF43A047)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = enteredOtp,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onOtpChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Enter 6-digit OTP") },
                            placeholder = { Text("------") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
                        )
                        
                        when (otpState) {
                            is OtpState.InvalidOtp -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = otpState.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
                            }
                            is OtpState.Error -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = otpState.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
                            }
                            else -> {}
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = onVerifyOtp,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = enteredOtp.length == 6 && otpState !is OtpState.Verifying,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (otpState is OtpState.Verifying) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Verify OTP")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedButton(
                            onClick = { 
                                onStepChange(2)
                                onRequestBankOtp()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Skip & Verify Bank Instead")
                        }
                    }
                    else -> {
                        Button(
                            onClick = onRequestMobileOtp,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Send OTP to Mobile")
                        }
                    }
                }
            }
            2 -> {
                // Bank OTP Step
                Text(text = "Verify Bank Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "OTP will be sent to your bank's registered mobile number",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                when (otpState) {
                    is OtpState.SendingOtp -> {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Sending OTP...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    is OtpState.OtpSent -> {
                        // Show message and demo OTP
                        Text(text = otpState.message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50))
                        
                        // Show demo OTP prominently if available
                        otpState.demoOtp?.let { demoOtp ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "DEMO OTP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF43A047)
                                    )
                                    Text(
                                        text = demoOtp,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF2E7D32),
                                        letterSpacing = 8.sp
                                    )
                                    Text(
                                        text = "Enter this OTP to verify",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF43A047)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = enteredOtp,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onOtpChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Enter 6-digit OTP") },
                            placeholder = { Text("------") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
                        )
                        
                        when (otpState) {
                            is OtpState.InvalidOtp -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = otpState.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
                            }
                            is OtpState.Error -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = otpState.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
                            }
                            else -> {}
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = onVerifyOtp,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = enteredOtp.length == 6 && otpState !is OtpState.Verifying,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (otpState is OtpState.Verifying) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Verify Bank OTP")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Skip (Demo Mode)")
                        }
                    }
                    else -> {
                        Button(
                            onClick = onRequestBankOtp,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Send OTP to Bank")
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StepIndicator(
    step: Int,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isCompleted -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                    isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else -> Color.Gray.copy(alpha = 0.1f)
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> Color(0xFF4CAF50)
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> Color.Gray
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = step.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive || isCompleted) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}

private fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) { "Unknown date" }
}
