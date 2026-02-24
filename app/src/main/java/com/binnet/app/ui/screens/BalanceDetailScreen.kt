package com.binnet.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.binnet.app.dashboard.viewmodel.PinVerificationState
import com.binnet.app.data.local.entity.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BalanceDetailScreen - Secure in-app balance check flow
 * 
 * Features:
 * - Bank Name and Account Last 4 Digits display
 * - "View Balance" button
 * - PIN verification bottom sheet
 * - Background USSD execution (no dialer)
 * - Balance display with "Updated just now"
 * - Recent Transactions list with "See All"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceDetailScreen(
    viewModel: BalanceViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onSeeAllTransactionsClick: () -> Unit = {}
) {
    val balanceState by viewModel.balanceState.collectAsState()
    val pinVerificationState by viewModel.pinVerificationState.collectAsState()
    val currentBank by viewModel.currentBank.collectAsState()
    val balanceData by viewModel.balanceData.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()

    var showPinSheet by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    // Navigate to balance check after PIN success
    LaunchedEffect(pinVerificationState) {
        if (pinVerificationState is PinVerificationState.Success) {
            showPinSheet = false
            enteredPin = ""
            viewModel.resetPinVerification()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Header
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

            // Bank Info Card
            item {
                BankInfoCard(bankInfo = currentBank)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Balance Display or View Balance Button
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
                    ViewBalanceButton(
                        onClick = { showPinSheet = true }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Recent Transactions Section
            item {
                RecentTransactionsSection(
                    transactions = recentTransactions,
                    onSeeAllClick = onSeeAllTransactionsClick
                )
            }
        }

        // Error Snackbar (if any)
        if (balanceState is BalanceState.Error) {
            ErrorCard(
                message = (balanceState as BalanceState.Error).message,
                onRetry = { showPinSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }

    // PIN Verification Bottom Sheet
    if (showPinSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showPinSheet = false
                enteredPin = ""
                viewModel.resetPinVerification()
            },
            sheetState = sheetState
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
}

@Composable
private fun BankInfoCard(bankInfo: BankInfo?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bankInfo?.name ?: "Loading...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Account •••• ${bankInfo?.accountLast4 ?: "****"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
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
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "View Balance",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (balanceState) {
                is BalanceState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Fetching balance...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                    Text(
                        text = "Please wait",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                is BalanceState.Success -> {
                    Text(
                        text = "Available Balance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = balanceState.balance,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Updated just now",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh")
                    }
                }
                is BalanceState.Error -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Error fetching balance",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Red
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun RecentTransactionsSection(
    transactions: List<TransactionEntity>,
    onSeeAllClick: () -> Unit
) {
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
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(onClick = onSeeAllClick) {
                    Text("See All")
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recent transactions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                transactions.forEachIndexed { index, transaction ->
                    TransactionItem(transaction = transaction)
                    if (index < transactions.size - 1) {
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color.Gray.copy(alpha = 0.2f)
                        )
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
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isCredit) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                ),
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
            Text(
                text = transaction.receiverName ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatDate(transaction.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        Text(
            text = "₹${transaction.amount ?: "0.00"}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isCredit) Color(0xFFE53935) else Color(0xFF43A047)
        )
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = Color(0xFFE53935)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE53935),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun PinVerificationContent(
    pinVerificationState: PinVerificationState,
    enteredPin: String,
    onPinChange: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onDismiss: () -> Unit,
    showPin: Boolean,
    onTogglePinVisibility: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enter App PIN",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Verify your identity to check balance",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = enteredPin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) onPinChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("4-digit PIN") },
            placeholder = { Text("••••") },
            singleLine = true,
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(onClick = onTogglePinVisibility) {
                    Icon(
                        imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPin) "Hide PIN" else "Show PIN"
                    )
                }
            }
        )

        // Error message
        when (val state = pinVerificationState) {
            is PinVerificationState.InvalidPin -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Incorrect PIN. ${state.remainingAttempts} attempts remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE53935)
                )
            }
            is PinVerificationState.LockedOut -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Too many attempts. Try again in ${state.secondsRemaining} seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE53935)
                )
            }
            is PinVerificationState.NoPinSet -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No PIN set. Please set up PIN first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE53935)
                )
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onVerifyClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = enteredPin.length == 4 && pinVerificationState !is PinVerificationState.Verifying,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (pinVerificationState is PinVerificationState.Verifying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Verify & Check Balance")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "Unknown date"
    }
}
