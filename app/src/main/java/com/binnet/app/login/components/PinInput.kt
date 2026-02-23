package com.binnet.app.login.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.binnet.app.ui.theme.PinDotEmpty
import com.binnet.app.ui.theme.PinDotFilled

/**
 * PinDots - Visual representation of PIN input using dots
 * @param pinLength Number of digits entered
 * @param maxLength Maximum number of digits
 * @param modifier Modifier for styling
 */
@Composable
fun PinDots(
    pinLength: Int,
    maxLength: Int = 4,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(maxLength) { index ->
            val isFilled = index < pinLength
            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1.1f else 1f,
                animationSpec = tween(durationMillis = 100),
                label = "dot_scale"
            )
            val color by animateColorAsState(
                targetValue = if (isFilled) PinDotFilled else PinDotEmpty,
                animationSpec = tween(durationMillis = 150),
                label = "dot_color"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(20.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * NumericKeypad - Number pad for PIN input
 * @param onDigitClick Callback when a digit is pressed
 * @param onDeleteClick Callback when delete is pressed
 * @param onBiometricClick Optional callback for biometric button
 * @param showBiometric Whether to show biometric button
 * @param enabled Whether the keypad is enabled
 */
@Composable
fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onBiometricClick: (() -> Unit)? = null,
    showBiometric: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: 1, 2, 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            KeypadButton(text = "1", onClick = { onDigitClick("1") }, enabled = enabled)
            KeypadButton(text = "2", onClick = { onDigitClick("2") }, enabled = enabled)
            KeypadButton(text = "3", onClick = { onDigitClick("3") }, enabled = enabled)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 2: 4, 5, 6
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            KeypadButton(text = "4", onClick = { onDigitClick("4") }, enabled = enabled)
            KeypadButton(text = "5", onClick = { onDigitClick("5") }, enabled = enabled)
            KeypadButton(text = "6", onClick = { onDigitClick("6") }, enabled = enabled)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 3: 7, 8, 9
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            KeypadButton(text = "7", onClick = { onDigitClick("7") }, enabled = enabled)
            KeypadButton(text = "8", onClick = { onDigitClick("8") }, enabled = enabled)
            KeypadButton(text = "9", onClick = { onDigitClick("9") }, enabled = enabled)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 4: Biometric/Empty, 0, Delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (showBiometric && onBiometricClick != null) {
                KeypadButton(
                    text = "",
                    onClick = onBiometricClick,
                    enabled = enabled,
                    isIcon = true,
                    iconRes = "fingerprint"
                )
            } else {
                Spacer(modifier = Modifier.width(72.dp))
            }

            KeypadButton(text = "0", onClick = { onDigitClick("0") }, enabled = enabled)

            KeypadButton(
                text = "",
                onClick = onDeleteClick,
                enabled = enabled && pinLength > 0,
                isIcon = true,
                iconRes = "backspace"
            )
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isIcon: Boolean = false,
    iconRes: String = ""
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.surface else Color.LightGray,
        label = "button_bg"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (enabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else Color.LightGray,
                shape = RoundedCornerShape(36.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(72.dp)
        ) {
            if (isIcon) {
                // Show icon based on iconRes
                Text(
                    text = when (iconRes) {
                        "backspace" -> "⌫"
                        "fingerprint" -> "👆"
                        else -> ""
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private val pinLength: Int
    get() = 0 // Placeholder - will be passed from parent
