package com.binnet.app.login.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binnet.app.R
import com.binnet.app.login.util.Bank
import com.binnet.app.login.util.IndianBanks
import com.binnet.app.login.viewmodel.OnboardingUiState
import com.binnet.app.login.viewmodel.OnboardingViewModel

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(),
    onNavigateToPinSetup: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val mobileNumber by viewModel.mobileNumber.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val suggestedPhoneNumbers by viewModel.suggestedPhoneNumbers.collectAsState()
    val userEmails by viewModel.userEmails.collectAsState()
    val selectedEmail by viewModel.selectedEmail.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is OnboardingUiState.Done) {
            val usePin = (uiState as OnboardingUiState.Done).usePin
            if (usePin) {
                onNavigateToPinSetup()
            } else {
                onNavigateToDashboard()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF1A1A1A) // Dark theme background matching GPay
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Crossfade(
                targetState = uiState,
                animationSpec = tween(500),
                label = "onboarding_crossfade"
            ) { state ->
                when (state) {
                    is OnboardingUiState.Loading -> LoadingScreen()
                    is OnboardingUiState.NotificationPermission -> NotificationPermissionScreen(
                        onAllow = { viewModel.onNotificationPermissionHandled() },
                        onDeny = { viewModel.onNotificationPermissionHandled() }
                    )
                    is OnboardingUiState.MobileInput -> MobileInputScreen(
                        mobileNumber = mobileNumber,
                        suggestedNumbers = suggestedPhoneNumbers,
                        onNumberChange = viewModel::updateMobileNumber,
                        onContinue = viewModel::submitMobileNumber,
                        onPermissionsGranted = viewModel::fetchPhoneNumbers
                    )
                    is OnboardingUiState.AccountSelection -> AccountSelectionScreen(
                        mobileNumber = mobileNumber,
                        emails = userEmails,
                        selectedEmail = selectedEmail,
                        onEmailSelect = viewModel::selectEmail,
                        onAccept = viewModel::onAccountSelected,
                        onPermissionsGranted = viewModel::fetchEmails
                    )
                    is OnboardingUiState.OtpVerification -> OtpVerificationScreen(
                        mobileNumber = mobileNumber,
                        onVerify = viewModel::verifyOtp
                    )
                    is OnboardingUiState.VerifyingMobile -> VerifyingMobileScreen(
                        mobileNumber = mobileNumber
                    )
                    is OnboardingUiState.AddBankIntro -> AddBankIntroScreen(
                        onAddBank = viewModel::onAddBankAccountClicked,
                        onSkip = viewModel::onSkipBankAdded
                    )
                    is OnboardingUiState.BankSelection -> BankSelectionScreenList(
                        onBankSelected = viewModel::onBankSelected
                    )
                    is OnboardingUiState.BankPermissions -> BankPermissionsScreen(
                        onAllow = { viewModel.onBankPermissionsHandled(true) },
                        onDeny = { viewModel.onBankPermissionsHandled(false) }
                    )
                    is OnboardingUiState.VerifyingBank -> VerifyingBankScreen(
                        bank = state.bank,
                        mobileNumber = mobileNumber
                    )
                    is OnboardingUiState.SecuritySelection -> SecuritySelectionScreen(
                        onContinue = viewModel::onSecuritySelected
                    )
                    is OnboardingUiState.Done, is OnboardingUiState.Error -> {
                        // Handled by LaunchedEffects
                    }
                }
            }
        }
    }
}

// 1. Notification Permission Screen
@Composable
private fun NotificationPermissionScreen(onAllow: () -> Unit, onDeny: () -> Unit) {
    // In a real app we would use Accompanist Permissions or ActivityResultContracts
    // For now, this is a visual replica
    
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onAllow() else onDeny()
    }
    
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Person, // Mock icon
                    contentDescription = null,
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Allow BIN-NET PAY to send you notifications?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onAllow()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC1E8FF)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ALLOW", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onDeny,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC1E8FF)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("DON'T ALLOW", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 2. Mobile Input Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileInputScreen(
    mobileNumber: String,
    suggestedNumbers: List<String>,
    onNumberChange: (String) -> Unit,
    onContinue: () -> Unit,
    onPermissionsGranted: () -> Unit
) {
    val permissions = mutableListOf<String>()
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
    }
    permissions.add(Manifest.permission.READ_PHONE_STATE)
    permissions.add(Manifest.permission.SEND_SMS) // Required to dispatch verification texts
    
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> 
        if (grants.values.any { it }) {
            onPermissionsGranted()
        }
    }
    
    LaunchedEffect(Unit) {
        launcher.launch(permissions.toTypedArray())
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Welcome to BIN-NET PAY",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Enter your phone number",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.LightGray
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = mobileNumber,
            onValueChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) onNumberChange(it) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.White),
            leadingIcon = {
                Text("🇮🇳 +91", color = Color.White, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.titleLarge)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8AB4F8),
                unfocusedBorderColor = Color.DarkGray,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(8.dp)
        )
        
        if (suggestedNumbers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Suggested Numbers:", color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestedNumbers.forEach { number -> 
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNumberChange(number) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
                    ) {
                        Text(
                            text = "+91 $number", 
                            color = Color.White, 
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onContinue,
            enabled = mobileNumber.length == 10,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF8AB4F8),
                disabledContainerColor = Color(0xFF8AB4F8).copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Continue", color = if (mobileNumber.length == 10) Color(0xFF1A1A1A) else Color.Gray)
        }
    }
}

// 3. Account Selection
@Composable
private fun AccountSelectionScreen(
    mobileNumber: String,
    emails: List<String>,
    selectedEmail: String?,
    onEmailSelect: (String) -> Unit,
    onAccept: () -> Unit,
    onPermissionsGranted: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> 
        if (isGranted) onPermissionsGranted()
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (accountName != null) {
                onEmailSelect(accountName)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.GET_ACCOUNTS)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        IconButton(onClick = { /* Back */ }) {
            Icon(Icons.Default.Check, contentDescription = "Back", tint = Color.White) // use descriptive icons
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Choose an account",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            text = "This is how people on BIN-NET PAY will see you",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFBA68C8)),
                contentAlignment = Alignment.Center
            ) {
                Text("U", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("USER", color = Color.White, style = MaterialTheme.typography.titleMedium)
                
                Text(
                    text = selectedEmail ?: "Tap to choose email", 
                    color = Color(0xFF8AB4F8), 
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { 
                        val intent = android.accounts.AccountManager.newChooseAccountIntent(
                            null, null, arrayOf("com.google"), false, null, null, null, null
                        )
                        accountPickerLauncher.launch(intent)
                    }.padding(vertical = 4.dp)
                )

                Text("+91 $mobileNumber", color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "By continuing you agree to the BIN-NET PAY Terms...",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onAccept,
            enabled = selectedEmail != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF8AB4F8),
                disabledContainerColor = Color(0xFF8AB4F8).copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Accept and continue", color = if (selectedEmail != null) Color(0xFF1A1A1A) else Color.Gray)
        }
    }
}

// 3.5. OTP Verification form
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtpVerificationScreen(
    mobileNumber: String,
    onVerify: (String) -> Unit
) {
    var otp by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        IconButton(onClick = { /* Back */ }) {
            Icon(Icons.Default.Check, contentDescription = "Back", tint = Color.White)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Verify your number",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            text = "Enter the 6-digit OTP sent to +91 $mobileNumber",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.LightGray
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = otp,
            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) otp = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.White, textAlign = TextAlign.Center),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8AB4F8),
                unfocusedBorderColor = Color.DarkGray,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(8.dp)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { onVerify(otp) },
            enabled = otp.length == 6,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF8AB4F8),
                disabledContainerColor = Color(0xFF8AB4F8).copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Verify OTP", color = if (otp.length == 6) Color(0xFF1A1A1A) else Color.Gray)
        }
    }
}

// 4. Verifying Mobile
@Composable
private fun VerifyingMobileScreen(mobileNumber: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFF8AB4F8),
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Verifying +91 $mobileNumber",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

// 5. Add Bank Intro
@Composable
private fun AddBankIntroScreen(onAddBank: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        IconButton(onClick = onSkip) {
            Icon(Icons.Default.Check, contentDescription = "Close", tint = Color.White)
        }
        
        Text(
            text = "Add your bank account",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Mock graphics
        Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.DarkGray, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF8AB4F8), modifier = Modifier.size(100.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Send and receive money securely on BIN-NET PAY. On continuing, you will select your bank and may need to set a UPI PIN.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.LightGray
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        val context = LocalContext.current
        Text(
            text = "Please keep VoLTE enabled for seamless offline payments. Tap here to open Network Settings.",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF8AB4F8),
            modifier = Modifier.clickable {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS))
            }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onAddBank,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8AB4F8)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Add bank account", color = Color(0xFF1A1A1A))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8AB4F8)),
            border = BorderStroke(1.dp, Color(0xFF8AB4F8)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("I'll add later")
        }
    }
}

// 6. Bank Selection List
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankSelectionScreenList(onBankSelected: (Bank) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredBanks = IndianBanks.banks.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search banks", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
        )
        
        Text(
            text = "Add bank account",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredBanks, key = { it.ussdCode }) { bank ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onBankSelected(bank) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalance, null, tint = Color.Black)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(bank.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

// 7. Bank Permissions Screen
@Composable
private fun BankPermissionsScreen(onAllow: () -> Unit, onDeny: () -> Unit) {
    // Shows standard system mock popups overlay
    val phonePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val smsPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = it.all { perm -> perm.value }
        if (granted) onAllow() else onDeny()
    }
    
    // Auto launch permissions in real app, here we just show visual
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Allow BIN-NET PAY to make and manage phone calls?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        smsPermLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC1E8FF)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ALLOW", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onDeny,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC1E8FF)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("DON'T ALLOW", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 8. Verifying Bank
@Composable
private fun VerifyingBankScreen(bank: Bank, mobileNumber: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color(0xFF8AB4F8), strokeWidth = 4.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Sending SMS from +91 $mobileNumber",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = "Finding bank accounts with ${bank.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )
    }
}

// 9. Security Selection
@Composable
private fun SecuritySelectionScreen(onContinue: (Boolean) -> Unit) {
    var usePin by remember { mutableStateOf<Boolean?>(null) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Text(
            text = "Secure your app so only you can access it",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(80.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().clickable { usePin = false }
                .border(
                    width = 2.dp,
                    color = if (usePin == false) Color(0xFF8AB4F8) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use your screen lock", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Use your existing PIN, pattern, face ID, or fingerprint", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                    Text("Works offline", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                RadioButton(
                    selected = usePin == false,
                    onClick = { usePin = false },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF8AB4F8), unselectedColor = Color.Gray)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().clickable { usePin = true }
                .border(
                    width = 2.dp,
                    color = if (usePin == true) Color(0xFF8AB4F8) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use a 4-digit Google PIN", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Create a PIN so only you can pay with your phone", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                    Text("Needs internet connection", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                RadioButton(
                    selected = usePin == true,
                    onClick = { usePin = true },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF8AB4F8), unselectedColor = Color.Gray)
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { usePin?.let { onContinue(it) } },
            enabled = usePin != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8AB4F8), disabledContainerColor = Color(0xFF8AB4F8).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Continue", color = if (usePin != null) Color(0xFF1A1A1A) else Color.Gray)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFF8AB4F8))
    }
}
