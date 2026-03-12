package com.binnet.app.login.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binnet.app.R
import com.binnet.app.login.components.NumericKeypad
import com.binnet.app.login.components.PinDots
import com.binnet.app.login.util.SimCardStatus
import com.binnet.app.login.viewmodel.LoginUiState
import com.binnet.app.login.viewmodel.PinViewModel

/**
 * PinSetupScreen - Main login screen with PIN setup and entry
 * Handles SIM card detection, PIN setup, and PIN entry flows
 */
@Composable
fun PinSetupScreen(
    viewModel: PinViewModel = viewModel(),
    onLoginSuccess: () -> Unit = {}
) {
    // Call checkInitialState when the screen is first displayed
    LaunchedEffect(Unit) {
        viewModel.checkInitialState()
    }

    val uiState by viewModel.uiState.collectAsState()
    val pin by viewModel.pin.collectAsState()
    val confirmPin by viewModel.confirmPin.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val simCardStatus by viewModel.simCardStatus.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }



    // Show error messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // Navigate on success - using key to prevent multiple calls
    var hasNavigated by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success && !hasNavigated) {
            hasNavigated = true
            onLoginSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (uiState) {
                is LoginUiState.Loading -> {
                    LoadingContent()
                }

                is LoginUiState.DeviceLockPrompt -> {
                    DeviceLockPromptContent(
                        onSuccess = { viewModel.onDeviceLockSuccess() },
                        onError = { viewModel.setErrorMessage("Device lock authentication failed or unavailable.") }
                    )
                }

                is LoginUiState.PinSetup -> {
                    PinSetupContent(
                        pin = pin,
                        onPinDigitClick = { viewModel.updatePin(it) },
                        onPinDeleteClick = { viewModel.deletePinDigit() },
                        onNextClick = { viewModel.proceedToConfirmPin() }
                    )
                }

                is LoginUiState.PinConfirm -> {
                    PinConfirmContent(
                        pin = pin,
                        confirmPin = confirmPin,
                        onConfirmPinDigitClick = { viewModel.updateConfirmPin(it) },
                        onConfirmPinDeleteClick = { viewModel.deleteConfirmPinDigit() },
                        onBackClick = { viewModel.navigateBackToSetup() },
                        onConfirmClick = { viewModel.confirmPinAndSetup() }
                    )
                }

                is LoginUiState.PinEntry -> {
                    PinEntryContent(
                        pin = pin,
                        onPinDigitClick = { viewModel.updatePin(it) },
                        onPinDeleteClick = { viewModel.deletePinDigit() },
                        onForgotPinClick = { viewModel.initiateForgotPin() }
                    )
                }

                is LoginUiState.ForgotPinOtpEntry -> {
                    ForgotPinOtpEntryContent(
                        otp = viewModel.otp.collectAsState().value,
                        message = (uiState as LoginUiState.ForgotPinOtpEntry).message,
                        onOtpDigitClick = { viewModel.updateOtp(it) },
                        onOtpDeleteClick = { viewModel.deleteOtpDigit() },
                        onCancelClick = { viewModel.cancelForgotPin() }
                    )
                }

                is LoginUiState.ForgotPinNewPin -> {
                    PinSetupContent(
                        pin = pin,
                        title = "Set New PIN",
                        subtitle = "Create a new 4-digit PIN",
                        onPinDigitClick = { viewModel.updatePin(it) },
                        onPinDeleteClick = { viewModel.deletePinDigit() },
                        onNextClick = { viewModel.proceedToForgotPinConfirm() }
                    )
                }

                is LoginUiState.ForgotPinConfirmPin -> {
                    PinConfirmContent(
                        pin = pin,
                        confirmPin = confirmPin,
                        title = "Confirm New PIN",
                        subtitle = "Re-enter your new PIN to confirm",
                        onConfirmPinDigitClick = { viewModel.updateConfirmPin(it) },
                        onConfirmPinDeleteClick = { viewModel.deleteConfirmPinDigit() },
                        onBackClick = { viewModel.cancelForgotPin() },
                        onConfirmClick = { viewModel.proceedToForgotPinFinalConfirm() }
                    )
                }

                is LoginUiState.ForgotPinFinalConfirm -> {
                    ForgotPinFinalConfirmContent(
                        onConfirmClick = { viewModel.completeForgotPin() },
                        onCancelClick = { viewModel.cancelForgotPin() }
                    )
                }

                is LoginUiState.Success -> {
                    SuccessContent()
                }

                is LoginUiState.Error -> {
                    ErrorContent(message = (uiState as LoginUiState.Error).message)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun DeviceLockPromptContent(
    onSuccess: () -> Unit,
    onError: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyguardManager = context.getSystemService(android.app.KeyguardManager::class.java)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onSuccess()
        } else {
            onError()
        }
    }

    LaunchedEffect(Unit) {
        if (keyguardManager?.isDeviceSecure == true) {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent("Unlock BIN-NET", "Confirm your screen lock to access the app.")
            if (intent != null) {
                launcher.launch(intent)
            } else {
                onError()
            }
        } else {
            onError()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PinSetupContent(
    pin: String,
    title: String = stringResource(R.string.setup_pin_title),
    subtitle: String = stringResource(R.string.setup_pin_subtitle),
    onPinDigitClick: (String) -> Unit,
    onPinDeleteClick: () -> Unit,
    onNextClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        PinDots(
            pinLength = pin.length,
            maxLength = 4
        )

        Spacer(modifier = Modifier.weight(1f))

        NumericKeypad(
            onDigitClick = onPinDigitClick,
            onDeleteClick = onPinDeleteClick,
            enabled = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNextClick,
            enabled = pin.length == 4,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = stringResource(R.string.continue_button),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PinConfirmContent(
    pin: String,
    confirmPin: String,
    title: String = stringResource(R.string.confirm_pin_title),
    subtitle: String = stringResource(R.string.confirm_pin_subtitle),
    onConfirmPinDigitClick: (String) -> Unit,
    onConfirmPinDeleteClick: () -> Unit,
    onBackClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        PinDots(
            pinLength = confirmPin.length,
            maxLength = 4
        )

        Spacer(modifier = Modifier.weight(1f))

        NumericKeypad(
            onDigitClick = onConfirmPinDigitClick,
            onDeleteClick = onConfirmPinDeleteClick,
            enabled = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onConfirmClick,
            enabled = confirmPin.length == 4,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = stringResource(R.string.next_button),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Go Back",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PinEntryContent(
    pin: String,
    onPinDigitClick: (String) -> Unit,
    onPinDeleteClick: () -> Unit,
    onForgotPinClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.enter_pin_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.enter_pin_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        PinDots(
            pinLength = pin.length,
            maxLength = 4
        )

        Spacer(modifier = Modifier.weight(1f))

        NumericKeypad(
            onDigitClick = onPinDigitClick,
            onDeleteClick = onPinDeleteClick,
            showBiometric = false,
            enabled = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        androidx.compose.material3.TextButton(
            onClick = onForgotPinClick
        ) {
            Text(
                text = "Forgot PIN?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SuccessContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.pin_set_success),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
    }
}



@Composable
private fun ForgotPinOtpEntryContent(
    otp: String,
    message: String,
    onOtpDigitClick: (String) -> Unit,
    onOtpDeleteClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Reset PIN",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        PinDots(
            pinLength = otp.length,
            maxLength = 6
        )

        Spacer(modifier = Modifier.weight(1f))

        NumericKeypad(
            onDigitClick = onOtpDigitClick,
            onDeleteClick = onOtpDeleteClick,
            enabled = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCancelClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ForgotPinFinalConfirmContent(
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Change PIN?",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Are you sure you want to change your PIN? You will need to use this new PIN for future logins.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onConfirmClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Yes, Change PIN",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCancelClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

