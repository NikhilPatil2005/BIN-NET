package com.binnet.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binnet.app.dashboard.model.Contact
import com.binnet.app.dashboard.viewmodel.BalanceCheckStatus
import com.binnet.app.dashboard.viewmodel.DashboardViewModel
import com.binnet.app.dashboard.viewmodel.VolteStatus
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onProfileClick: () -> Unit = {},
    onScanQRClick: () -> Unit = {},
    onPayContactsClick: () -> Unit = {},
    onBankTransferClick: () -> Unit = {},
    onSelfTransferClick: () -> Unit = {},
    onCheckCibilClick: () -> Unit = {},
    onTransactionHistoryClick: () -> Unit = {},
    onPayToContact: (Contact) -> Unit = {},
    onCheckBalanceClick: () -> Unit = {},
    onLinkBankClick: () -> Unit = {}
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val recentReceivers by viewModel.recentReceivers.collectAsState()
    val balanceStatus by viewModel.balanceCheckStatus.collectAsState()
    val hasContactsPermission by viewModel.hasContactsPermission.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val volteStatus by viewModel.volteStatus.collectAsState()
    val showVolteTip by viewModel.showVolteTip.collectAsState()
    
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    var showPermissionRationale by remember { mutableStateOf(false) }
    
    var debouncedQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedQuery = searchQuery
        if (searchQuery.isNotBlank()) {
            viewModel.searchContacts(searchQuery)
        } else {
            viewModel.clearSearchResults()
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setContactsPermissionGranted(isGranted)
        if (!isGranted) {
            showPermissionRationale = true
        }
    }
    
    LaunchedEffect(Unit) {
        val permission = Manifest.permission.READ_CONTACTS
        val hasPermission = ContextCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            viewModel.setContactsPermissionGranted(true)
            viewModel.loadRecentReceivers()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            SmartSearchBar(
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onSearchSubmit = {
                    keyboardController?.hide()
                    if (searchQuery.isNotBlank() && searchResults.isEmpty() && hasContactsPermission) {
                        val tempContact = Contact(
                            id = "temp_${System.currentTimeMillis()}",
                            name = "New Payee",
                            phoneNumber = searchQuery.replace(Regex("[^0-9]"), "")
                        )
                        onPayToContact(tempContact)
                    } else if (searchResults.isNotEmpty()) {
                        onPayToContact(searchResults.first())
                    }
                },
                onProfileClick = onProfileClick,
                hasPermission = hasContactsPermission,
                onGrantPermission = {
                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            )
        }

        // VoLTE Tip Card
        if (showVolteTip && volteStatus == VolteStatus.Unavailable) {
            item {
                VoLTEOptimizationCard(
                    onEnableClick = {
                        viewModel.openMobileNetworkSettings()
                    },
                    onDismiss = {
                        viewModel.dismissVolteTip()
                    }
                )
            }
        }

        if (searchQuery.isNotBlank() && hasContactsPermission) {
            item {
                SearchResultsSection(
                    results = searchResults,
                    query = searchQuery,
                    isSearching = isSearching,
                    onContactClick = { contact ->
                        keyboardController?.hide()
                        onPayToContact(contact)
                    }
                )
            }
        }

        if (showPermissionRationale) {
            item {
                PermissionRationaleCard(
                    onRequestPermission = {
                        showPermissionRationale = false
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                )
            }
        }

        item {
            MainActionsGrid(
                onScanQRClick = {
                    viewModel.onScanQRClick()
                    onScanQRClick()
                },
                onPayContactsClick = {
                    viewModel.onPayContactsClick()
                    onPayContactsClick()
                },
                onBankTransferClick = {
                    viewModel.onBankTransferClick()
                    onBankTransferClick()
                },
                onSelfTransferClick = {
                    viewModel.onSelfTransferClick()
                    onSelfTransferClick()
                }
            )
        }

        item {
            RecentSection(
                recentContacts = recentReceivers,
                onContactClick = { contact ->
                    onPayToContact(contact)
                }
            )
        }

        item {
            UtilitySection(
                onCheckCibilClick = {
                    viewModel.onCheckCibilScoreClick()
                    onCheckCibilClick()
                },
                onTransactionHistoryClick = {
                    viewModel.onTransactionHistoryClick()
                    onTransactionHistoryClick()
                },
                onCheckBalanceClick = onCheckBalanceClick,
                balanceStatus = balanceStatus
            )
        }
    }
}

@Composable
private fun VoLTEOptimizationCard(
    onEnableClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0) // Light orange background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF9800)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Optimization",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Optimization Tip ⚡",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
                Text(
                    text = "Enable VoLTE in your Phone Settings for 2x faster offline payments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF795548)
                )
            }
            
            Column {
                Button(
                    onClick = onEnableClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Enable",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Later",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF795548)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onProfileClick: () -> Unit,
    hasPermission: Boolean,
    onGrantPermission: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (hasPermission) "Search or enter number" else "Grant contacts permission",
                            color = Color.Gray
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.Gray
                        )
                    },
                    trailingIcon = {
                        if (!hasPermission) {
                            IconButton(onClick = onGrantPermission) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Grant Permission",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    enabled = hasPermission,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearchSubmit() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color.White.copy(alpha = 0.9f),
                        disabledBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.width(12.dp))

                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsSection(
    results: List<Contact>,
    query: String,
    isSearching: Boolean,
    onContactClick: (Contact) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (results.isEmpty()) {
                val normalizedQuery = query.replace(Regex("[^0-9]"), "")
                if (normalizedQuery.length >= 10) {
                    NewPayeeItem(
                        phoneNumber = normalizedQuery,
                        onClick = { onContactClick(Contact(id = "new", name = "New Payee", phoneNumber = normalizedQuery)) }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Enter a valid phone number",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                results.take(5).forEach { contact ->
                    SearchResultItem(
                        contact = contact,
                        onClick = { onContactClick(contact) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    contact: Contact,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.avatarInitials,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Pay",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NewPayeeItem(
    phoneNumber: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New Payee",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4CAF50)
                )
                Text(
                    text = phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Pay",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PermissionRationaleCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Contacts Access Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "BIN-NET needs access to your contacts to help you pay friends easily.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
private fun MainActionsGrid(
    onScanQRClick: () -> Unit,
    onPayContactsClick: () -> Unit,
    onBankTransferClick: () -> Unit,
    onSelfTransferClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Payments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { ActionItem(icon = Icons.Default.QrCodeScanner, label = "Scan any QR", onClick = onScanQRClick) }
                item { ActionItem(icon = Icons.Default.Send, label = "Pay Contacts", onClick = onPayContactsClick) }
                item { ActionItem(icon = Icons.Default.AccountBalance, label = "Bank Transfer", onClick = onBankTransferClick) }
                item { ActionItem(icon = Icons.Default.SwapHoriz, label = "Self Transfer", onClick = onSelfTransferClick) }
            }
        }
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecentSection(recentContacts: List<Contact>, onContactClick: (Contact) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (recentContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent payments.\nStart by paying someone!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(recentContacts.take(5)) { contact ->
                        RecentContactItem(contact = contact, onClick = { onContactClick(contact) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentContactItem(contact: Contact, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.avatarInitials,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = contact.name.split(" ").firstOrNull() ?: contact.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UtilitySection(
    onCheckCibilClick: () -> Unit,
    onTransactionHistoryClick: () -> Unit,
    onCheckBalanceClick: () -> Unit,
    balanceStatus: BalanceCheckStatus
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Utility & Finance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            UtilityButton(icon = Icons.Default.Star, label = "Check CIBIL Score", onClick = onCheckCibilClick)
            Spacer(modifier = Modifier.height(12.dp))
            UtilityButton(icon = Icons.Default.History, label = "Show Transaction History", onClick = onTransactionHistoryClick)
            Spacer(modifier = Modifier.height(12.dp))

            val buttonText = when (balanceStatus) {
                is BalanceCheckStatus.Loading -> "Checking..."
                is BalanceCheckStatus.Initiated -> "USSD: ${balanceStatus.ussdCode}"
                is BalanceCheckStatus.Error -> "Error - Try Again"
                else -> "Check Bank Balance"
            }

            UtilityButton(
                icon = Icons.Default.AccountBalance,
                label = buttonText,
                onClick = onCheckBalanceClick,
                isLoading = balanceStatus is BalanceCheckStatus.Loading
            )
        }
    }
}

@Composable
private fun UtilityButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Default.SyncAlt,
                    contentDescription = "Navigate",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}