package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.PinChangeViewModel
import com.example.ui.components.PinStrengthMeter
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.util.PinStrength
import com.example.util.PinStrengthAnalyzer

@Composable
fun PinChangeScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    pinChangeViewModel: PinChangeViewModel = viewModel()
) {
    val currentPin by pinChangeViewModel.currentPin.collectAsState()
    val newPin by pinChangeViewModel.newPin.collectAsState()
    val confirmPin by pinChangeViewModel.confirmPin.collectAsState()
    val isLoading by pinChangeViewModel.isLoading.collectAsState()
    val errorMessage by pinChangeViewModel.errorMessage.collectAsState()
    val successMessage by pinChangeViewModel.successMessage.collectAsState()
    val recoveryKey by pinChangeViewModel.recoveryKey.collectAsState()
    val isFormValid by pinChangeViewModel.isFormValid.collectAsState()
    val developerPinHash by pinChangeViewModel.developerPinHash.collectAsState()

    var currentPinVisible by remember { mutableStateOf(false) }
    var newPinVisible by remember { mutableStateOf(false) }
    var confirmPinVisible by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BentoBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoCardBg)
                        .border(1.dp, BentoBorder, CircleShape)
                        .testTag("pin_change_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Back",
                        tint = BentoTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "CHANGE DEVELOPER PIN",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BentoTextPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            if (recoveryKey != null) {
                // Success screen & Recovery key copy box
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(BentoCardBg)
                        .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Success",
                        tint = BentoGreenPrimary,
                        modifier = Modifier.size(64.dp)
                    )

                    Text(
                        text = "NEW PIN CONFIGURED SECURELY!",
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Your developer recovery key has been re-generated for this new PIN.",
                        color = BentoTextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    // Recovery key display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BentoHeroCardBg)
                            .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "NEW RECOVERY KEY",
                                color = BentoTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = recoveryKey ?: "",
                                color = BentoGreenPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.testTag("new_recovery_key_text")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        recoveryKey?.let {
                                            clipboardManager.setText(AnnotatedString(it))
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy key",
                                    tint = BentoGreenPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Copy recovery key",
                                    color = BentoGreenPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "WARNING: Write this key down. It is the only way to recover access and reset the PIN if you forget it.",
                        color = BentoRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            pinChangeViewModel.resetForm()
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pin_change_done_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("I HAVE SECURED THIS KEY", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // PIN change inputs card
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(BentoCardBg)
                        .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "SECURITY AUTHENTICATION",
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary,
                        fontSize = 14.sp
                    )

                    if (developerPinHash == null) {
                        // Tip about default PIN
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoHeroCardBg)
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = BentoGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Temporary PIN '000000' is currently active. Please set a custom PIN.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 1. Current PIN Field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Current PIN",
                            color = BentoTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = currentPin,
                            onValueChange = { pinChangeViewModel.onCurrentPinChanged(it) },
                            placeholder = { Text("Enter current PIN (Default: 000000)") },
                            visualTransformation = if (currentPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { currentPinVisible = !currentPinVisible }) {
                                    Icon(
                                        imageVector = if (currentPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_change_current_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BentoGreenPrimary,
                                unfocusedBorderColor = BentoBorder,
                                focusedContainerColor = BentoBackground,
                                unfocusedContainerColor = BentoBackground
                            )
                        )
                    }

                    // 2. New PIN Field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "New PIN (4-6 digits)",
                            color = BentoTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { pinChangeViewModel.onNewPinChanged(it) },
                            placeholder = { Text("Enter a secure numeric PIN") },
                            visualTransformation = if (newPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { newPinVisible = !newPinVisible }) {
                                    Icon(
                                        imageVector = if (newPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_change_new_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BentoGreenPrimary,
                                unfocusedBorderColor = BentoBorder,
                                focusedContainerColor = BentoBackground,
                                unfocusedContainerColor = BentoBackground
                            )
                        )
                    }

                    // Real-time strength meter component
                    PinStrengthMeter(pin = newPin)

                    // 3. Confirm PIN Field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Confirm New PIN",
                            color = BentoTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { pinChangeViewModel.onConfirmPinChanged(it) },
                            placeholder = { Text("Re-enter new PIN") },
                            visualTransformation = if (confirmPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { confirmPinVisible = !confirmPinVisible }) {
                                    Icon(
                                        imageVector = if (confirmPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_change_confirm_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BentoGreenPrimary,
                                unfocusedBorderColor = BentoBorder,
                                focusedContainerColor = BentoBackground,
                                unfocusedContainerColor = BentoBackground
                            )
                        )

                        // Confirmation mismatch warning helper
                        if (confirmPin.isNotEmpty() && newPin != confirmPin) {
                            Text(
                                text = "PIN confirmations must match.",
                                color = BentoRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = BentoRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_change_error_text")
                        )
                    }

                    if (successMessage != null) {
                        Text(
                            text = successMessage ?: "",
                            color = BentoGreenPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            pinChangeViewModel.executePinChange { key ->
                                // Success trigger
                            }
                        },
                        enabled = isFormValid && !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pin_change_submit_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BentoGreenPrimary,
                            disabledContainerColor = BentoBorder.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = BentoBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("CHANGE SECURITY PIN", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
