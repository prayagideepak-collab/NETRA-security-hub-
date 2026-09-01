package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoAmber
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.delay
import com.example.util.PinStrength
import com.example.util.PinStrengthAnalyzer

@Composable
fun SettingsScreen(
    monitorThermal: Boolean,
    monitorWeather: Boolean,
    notifyWeather: Boolean,
    announceWeather: Boolean,
    monitorLight: Boolean,
    notifyLight: Boolean,
    announceLight: Boolean,
    monitorBluetooth: Boolean,
    notifyBluetooth: Boolean,
    announceBluetooth: Boolean,
    monitorLocation: Boolean,
    notifyLocation: Boolean,
    announceLocation: Boolean,
    monitorMagnetic: Boolean,
    notifyMagnetic: Boolean,
    announceMagnetic: Boolean,
    monitorProximity: Boolean,
    notifyProximity: Boolean,
    announceProximity: Boolean,
    onToggleThermal: (Boolean) -> Unit,
    onToggleWeather: (Boolean) -> Unit,
    onToggleNotifyWeather: (Boolean) -> Unit,
    onToggleAnnounceWeather: (Boolean) -> Unit,
    onToggleLight: (Boolean) -> Unit,
    onToggleNotifyLight: (Boolean) -> Unit,
    onToggleAnnounceLight: (Boolean) -> Unit,
    onToggleBluetooth: (Boolean) -> Unit,
    onToggleNotifyBluetooth: (Boolean) -> Unit,
    onToggleAnnounceBluetooth: (Boolean) -> Unit,
    onToggleLocation: (Boolean) -> Unit,
    onToggleNotifyLocation: (Boolean) -> Unit,
    onToggleAnnounceLocation: (Boolean) -> Unit,
    onToggleMagnetic: (Boolean) -> Unit,
    onToggleNotifyMagnetic: (Boolean) -> Unit,
    onToggleAnnounceMagnetic: (Boolean) -> Unit,
    onToggleProximity: (Boolean) -> Unit,
    onToggleNotifyProximity: (Boolean) -> Unit,
    onToggleAnnounceProximity: (Boolean) -> Unit,
    refreshIntervalMs: Int,
    thermalThresholdC: Int,
    encryptionEnabled: Boolean,
    travelMode: String,
    isDeveloperMode: Boolean,
    isDeveloperAuthenticated: Boolean,
    lockoutUntil: Long,
    developerPinHash: String?,
    developerPinStrength: String?,
    developerPinChangedDate: String?,
    developerPinFailedAttempts: Int,
    developerPinRecoveryKey: String?,
    onChangeRefreshInterval: (Int) -> Unit,
    onChangeThermalThreshold: (Int) -> Unit,
    onToggleEncryption: (Boolean) -> Unit,
    onToggleDeveloperMode: (Boolean) -> Unit,
    onAuthenticateDeveloper: (String) -> Boolean,
    onChangeDeveloperPin: (String, String) -> Boolean,
    onResetDeveloperPin: (String) -> Boolean,
    onLockDeveloperMode: () -> Unit,
    onNavigateToPinChange: () -> Unit,
    onExportLogs: ((Boolean, String) -> Unit) -> Unit,
    onOpenAuditScreen: () -> Unit,
    onTravelModeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var exportFeedback by remember { mutableStateOf<String?>(null) }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogMode by remember { mutableStateOf("ENTER_PIN") } // "ENTER_PIN", "FORCE_CHANGE_PIN", "CHANGE_PIN", "RECOVER_PIN", "SHOW_RECOVERY_KEY"
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    // Change PIN Fields
    var currentPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var changeError by remember { mutableStateOf<String?>(null) }

    // Recovery Key Field
    var recoveryKeyInput by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }

    // Real-time lockout checking
    var lockoutSecondsLeft by remember { mutableStateOf(0L) }

    LaunchedEffect(lockoutUntil) {
        while (true) {
            val left = (lockoutUntil - System.currentTimeMillis()) / 1000
            lockoutSecondsLeft = if (left > 0) left else 0
            delay(1000)
        }
    }

    LaunchedEffect(isDeveloperMode, isDeveloperAuthenticated) {
        if (isDeveloperMode && !isDeveloperAuthenticated) {
            showPinDialog = true
            pinDialogMode = "ENTER_PIN"
            enteredPin = ""
            pinError = null
        } else if (!isDeveloperMode) {
            showPinDialog = false
        }
    }

    if (showPinDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                onToggleDeveloperMode(false)
                showPinDialog = false
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(BentoBackground)
                    .border(2.dp, BentoBorder, RoundedCornerShape(28.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (pinDialogMode) {
                        "ENTER_PIN" -> {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = BentoGreenPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "DEVELOPER PIN REQUIRED",
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Enter security PIN to authorize Developer Mode session.",
                                color = BentoTextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            // PIN dots display
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                val displayLength = 6
                                for (i in 0 until displayLength) {
                                    val isFilled = i < enteredPin.length
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isFilled) BentoGreenPrimary else BentoCardBg
                                            )
                                            .border(1.dp, BentoBorder, CircleShape)
                                    )
                                }
                            }

                            if (pinError != null) {
                                Text(
                                    text = pinError ?: "",
                                    color = BentoRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (lockoutSecondsLeft > 0) {
                                Text(
                                    text = "Locked out! Try again in $lockoutSecondsLeft seconds.",
                                    color = BentoRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Digital Keypad
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                val rows = listOf(
                                    listOf("1", "2", "3"),
                                    listOf("4", "5", "6"),
                                    listOf("7", "8", "9"),
                                    listOf("Clear", "0", "Back")
                                )
                                for (row in rows) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        for (item in row) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(54.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(if (lockoutSecondsLeft <= 0) BentoCardBg else BentoCardBg.copy(alpha = 0.5f))
                                                    .clickable(enabled = lockoutSecondsLeft <= 0) {
                                                        when (item) {
                                                            "Clear" -> {
                                                                enteredPin = ""
                                                                pinError = null
                                                            }
                                                            "Back" -> {
                                                                if (enteredPin.isNotEmpty()) {
                                                                    enteredPin = enteredPin.dropLast(1)
                                                                    pinError = null
                                                                }
                                                            }
                                                            else -> {
                                                                if (enteredPin.length < 6) {
                                                                    enteredPin += item
                                                                    pinError = null
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .testTag("pin_keypad_$item")
                                            ) {
                                                when (item) {
                                                    "Back" -> Icon(
                                                        imageVector = Icons.Default.Backspace,
                                                        contentDescription = "Backspace",
                                                        tint = if (lockoutSecondsLeft <= 0) BentoTextPrimary else BentoTextMuted
                                                    )
                                                    "Clear" -> Text(
                                                        text = "C",
                                                        color = if (lockoutSecondsLeft <= 0) BentoTextPrimary else BentoTextMuted,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 18.sp
                                                    )
                                                    else -> Text(
                                                        text = item,
                                                        color = if (lockoutSecondsLeft <= 0) BentoTextPrimary else BentoTextMuted,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 20.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    if (enteredPin.length in 4..6) {
                                        val success = onAuthenticateDeveloper(enteredPin)
                                        if (success) {
                                            if (developerPinHash.isNullOrEmpty()) {
                                                pinDialogMode = "FORCE_CHANGE_PIN"
                                                currentPinInput = "000000"
                                                newPinInput = ""
                                                confirmPinInput = ""
                                                changeError = null
                                            } else {
                                                showPinDialog = false
                                            }
                                        } else {
                                            enteredPin = ""
                                            pinError = "Incorrect security PIN. Please try again."
                                        }
                                    } else {
                                        pinError = "PIN must be between 4 and 6 digits."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("verify_pin_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary),
                                shape = RoundedCornerShape(16.dp),
                                enabled = lockoutSecondsLeft <= 0 && enteredPin.length in 4..6
                            ) {
                                Text("VERIFY PIN", fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Forgot PIN?",
                                    color = BentoGreenPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable {
                                            pinDialogMode = "RECOVER_PIN"
                                            recoveryKeyInput = ""
                                            recoveryError = null
                                        }
                                        .padding(8.dp)
                                )

                                Text(
                                    text = "Cancel",
                                    color = BentoTextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable {
                                            onToggleDeveloperMode(false)
                                            showPinDialog = false
                                        }
                                        .padding(8.dp)
                                )
                            }
                        }

                        "FORCE_CHANGE_PIN", "CHANGE_PIN" -> {
                            val isForce = pinDialogMode == "FORCE_CHANGE_PIN"
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = BentoGreenPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (isForce) "FORCE PIN CHANGE" else "CHANGE SECURITY PIN",
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (isForce) {
                                    "The default PIN '000000' is temporary. You must change it before continuing."
                                } else {
                                    "Create a secure 4-6 digit numeric PIN."
                                },
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            if (!isForce) {
                                OutlinedTextField(
                                    value = currentPinInput,
                                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 6) currentPinInput = it },
                                    label = { Text("Current PIN", fontSize = 12.sp) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth().testTag("current_pin_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            OutlinedTextField(
                                value = newPinInput,
                                onValueChange = {
                                    if (it.all { c -> c.isDigit() } && it.length <= 6) {
                                        newPinInput = it
                                        changeError = null
                                    }
                                },
                                label = { Text("New PIN (4-6 digits)", fontSize = 12.sp) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("new_pin_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = confirmPinInput,
                                onValueChange = {
                                    if (it.all { c -> c.isDigit() } && it.length <= 6) {
                                        confirmPinInput = it
                                        changeError = null
                                    }
                                },
                                label = { Text("Confirm New PIN", fontSize = 12.sp) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("confirm_pin_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (newPinInput.isNotEmpty()) {
                                val strength = PinStrengthAnalyzer.analyze(newPinInput)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "PIN Strength:",
                                        fontSize = 11.sp,
                                        color = BentoTextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(strength.color.copy(alpha = 0.2f))
                                            .border(1.dp, strength.color, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = strength.displayName,
                                            color = strength.color,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (strength == PinStrength.WEAK) {
                                    Text(
                                        text = "Warning: Weak PIN patterns (repeated, sequential, common combos) are discouraged.",
                                        color = BentoRed,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (changeError != null) {
                                Text(
                                    text = changeError ?: "",
                                    color = BentoRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Button(
                                onClick = {
                                    if (newPinInput.length !in 4..6) {
                                        changeError = "New PIN must be between 4 and 6 digits."
                                    } else if (newPinInput != confirmPinInput) {
                                        changeError = "PIN confirmation does not match."
                                    } else if (newPinInput == currentPinInput) {
                                        changeError = "New PIN cannot be identical to current PIN."
                                    } else {
                                        val success = onChangeDeveloperPin(currentPinInput, newPinInput)
                                        if (success) {
                                            pinDialogMode = "SHOW_RECOVERY_KEY"
                                        } else {
                                            changeError = "Incorrect current PIN. Please check and try again."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_pin_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary),
                                shape = RoundedCornerShape(16.dp),
                                enabled = newPinInput.length in 4..6 && confirmPinInput.length in 4..6
                            ) {
                                Text("SAVE AND SECURE PIN", fontWeight = FontWeight.Bold)
                            }

                            if (!isForce) {
                                Text(
                                    text = "Cancel",
                                    color = BentoTextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable {
                                            showPinDialog = false
                                        }
                                        .padding(8.dp)
                                )
                            } else {
                                Text(
                                    text = "Exit Developer Mode",
                                    color = BentoRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            onToggleDeveloperMode(false)
                                            showPinDialog = false
                                        }
                                        .padding(8.dp)
                                )
                            }
                        }

                        "SHOW_RECOVERY_KEY" -> {
                            val clipboardManager = LocalClipboardManager.current
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = BentoGreenPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "PIN SECURED!",
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Your developer recovery key has been generated.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BentoHeroCardBg)
                                    .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "YOUR RECOVERY KEY",
                                        color = BentoTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = developerPinRecoveryKey ?: "GENERATING...",
                                        color = BentoGreenPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.testTag("recovery_key_display")
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                developerPinRecoveryKey?.let {
                                                    clipboardManager.setText(AnnotatedString(it))
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy key",
                                            tint = BentoGreenPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Copy to clipboard",
                                            color = BentoGreenPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "WARNING: Write this key down or save it in a password manager. This is the only way to recover access if you forget your PIN.",
                                color = BentoTextSecondary,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = {
                                    showPinDialog = false
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("confirm_saved_key_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("I HAVE SECURED THIS KEY", fontWeight = FontWeight.Bold)
                            }
                        }

                        "RECOVER_PIN" -> {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = null,
                                tint = BentoAmber,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "RECOVER DEVELOPER PIN",
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Enter your 12-character recovery key to reset the PIN back to the default '000000'.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )

                            OutlinedTextField(
                                value = recoveryKeyInput,
                                onValueChange = {
                                    recoveryKeyInput = it.uppercase()
                                    recoveryError = null
                                },
                                label = { Text("Recovery Key (XXXX-XXXX-XXXX)", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth().testTag("recovery_key_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (recoveryError != null) {
                                Text(
                                    text = recoveryError ?: "",
                                    color = BentoRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Button(
                                onClick = {
                                    val success = onResetDeveloperPin(recoveryKeyInput)
                                    if (success) {
                                        pinDialogMode = "ENTER_PIN"
                                        enteredPin = ""
                                        pinError = "PIN Reset Successful! Enter the default PIN '000000' to set your new custom PIN."
                                    } else {
                                        recoveryError = "Invalid recovery key. Please check and try again."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("submit_recovery_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = BentoAmber),
                                shape = RoundedCornerShape(16.dp),
                                enabled = recoveryKeyInput.replace("-", "").length == 12
                            ) {
                                Text("RESET DEVELOPER PIN", fontWeight = FontWeight.Bold)
                            }

                            Text(
                                text = "Cancel",
                                color = BentoTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable {
                                        pinDialogMode = "ENTER_PIN"
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Service Control Center (Phase 1)
        item {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = "SERVICE CONTROL CENTER",
                    color = BentoGreenPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Optional Monitoring Services",
                    color = BentoTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }
        }
        
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ServiceStatusRow("Weather Monitoring", monitorWeather, onToggleWeather, notifyWeather, announceWeather, onToggleNotifyWeather, onToggleAnnounceWeather)
                ServiceStatusRow("Magnetic Monitoring", monitorMagnetic, onToggleMagnetic, notifyMagnetic, announceMagnetic, onToggleNotifyMagnetic, onToggleAnnounceMagnetic)
                ServiceStatusRow("Light Sensor Monitoring", monitorLight, onToggleLight, notifyLight, announceLight, onToggleNotifyLight, onToggleAnnounceLight)
                ServiceStatusRow("Bluetooth Monitoring", monitorBluetooth, onToggleBluetooth, notifyBluetooth, announceBluetooth, onToggleNotifyBluetooth, onToggleAnnounceBluetooth)
                ServiceStatusRow("Location Monitoring", monitorLocation, onToggleLocation, notifyLocation, announceLocation, onToggleNotifyLocation, onToggleAnnounceLocation)
                ServiceStatusRow("Proximity Monitoring", monitorProximity, onToggleProximity, notifyProximity, announceProximity, onToggleNotifyProximity, onToggleAnnounceProximity)
            }
        }

        // Developer Mode Toggle
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = BentoGreenPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DEVELOPER MODE",
                            color = BentoTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Access advanced sensor diagnostics and real-time logs.",
                            color = BentoTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isDeveloperMode,
                        onCheckedChange = { onToggleDeveloperMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BentoGreenVibrant,
                            checkedTrackColor = BentoGreenPrimary.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // Developer Security Status (Section 10)
        if (isDeveloperMode && isDeveloperAuthenticated) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(BentoCardBg)
                        .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                        .padding(20.dp)
                        .testTag("developer_security_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = BentoGreenPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "DEVELOPER SECURITY",
                                color = BentoTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Custom Divider
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BentoBorder.copy(alpha = 0.5f)))

                        // PIN Status
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "PIN Status:", color = BentoTextSecondary, fontSize = 12.sp)
                            Text(
                                text = if (developerPinHash.isNullOrEmpty()) "Default (000000)" else "Configured",
                                color = if (developerPinHash.isNullOrEmpty()) BentoRed else BentoGreenPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        // PIN Strength
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "PIN Strength:", color = BentoTextSecondary, fontSize = 12.sp)
                            val strText = developerPinStrength ?: "Weak"
                            val strColor = when (strText) {
                                "Strong" -> BentoGreenVibrant
                                "Medium" -> BentoAmber
                                else -> BentoRed
                            }
                            Text(
                                text = strText,
                                color = strColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        // Last Changed Date
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Last Changed:", color = BentoTextSecondary, fontSize = 12.sp)
                            Text(
                                text = developerPinChangedDate ?: "Never (Setup required)",
                                color = BentoTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }

                        // Failed Attempts
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Failed Attempts:", color = BentoTextSecondary, fontSize = 12.sp)
                            Text(
                                text = "$developerPinFailedAttempts",
                                color = if (developerPinFailedAttempts > 0) BentoRed else BentoTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }

                        // Custom Divider
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BentoBorder.copy(alpha = 0.5f)))

                        // Control buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    onNavigateToPinChange()
                                },
                                modifier = Modifier.weight(1f).height(40.dp).testTag("change_pin_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("CHANGE PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onLockDeveloperMode()
                                },
                                modifier = Modifier.weight(1f).height(40.dp).testTag("lock_session_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = BentoTextSecondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("LOCK SESSION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Location Permission & Weather Synchronization Card (Settings -> Permissions -> Location)
        item {
            val context = LocalContext.current
            val hasLocationPermission = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val permissionState = if (hasLocationPermission) "Always Allow / Allow While Using App" else "Denied"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = BentoGreenPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SETTINGS → PERMISSIONS → LOCATION",
                                color = BentoGreenPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Permission State: $permissionState",
                                color = BentoTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasLocationPermission) "Weather sync active every 5 min. Temperature monitored continuously." else "Denied: Weather synchronization stopped. Temp monitoring continues using device sensors only. No repeated popups.",
                                color = if (hasLocationPermission) BentoGreenPrimary else BentoRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoHeroCardBg),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Text(text = "Manage Location Permission in Settings", color = BentoTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Header Title
        item {
            Column {
                Text(
                    text = "SYSTEM PREFERENCES & SECURITY",
                    color = BentoGreenPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "DataStore & Encryption Engine",
                    color = BentoTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }
        }

        // 24/7 Always-On & Auto-Relaunch Status Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoHeroCardBg)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Power, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "24/7 CONTINUOUS SYSTEM RUNTIME",
                                color = BentoTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Keep Screen On is active to maintain continuous sensor collection. System automatically launches on device boot/power failure recovery.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Disable Battery Optimization & Background Readiness Card
        item {
            val context = LocalContext.current
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            val pm = remember { context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager }

            var isIgnoringBatteryOpt by remember {
                mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
            }

            // Automatic permission recheck when returning from Android Settings screen
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val currentGranted = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                        if (currentGranted != isIgnoringBatteryOpt) {
                            isIgnoringBatteryOpt = currentGranted
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val canExactAlarms = remember {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val am = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                    am?.canScheduleExactAlarms() ?: true
                } else true
            }

            val notifGranted = remember {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true
            }

            val manufacturer = remember { android.os.Build.MANUFACTURER.orEmpty() }
            val strictOems = remember { listOf("xiaomi", "redmi", "poco", "vivo", "oppo", "realme", "huawei", "meizu", "oneplus") }
            val isStrictOem = remember(manufacturer) { strictOems.any { manufacturer.lowercase().contains(it) } }

            var score = 0
            if (isIgnoringBatteryOpt) score += 45 else score += 10
            if (canExactAlarms) score += 25 else score += 10
            if (notifGranted) score += 20 else score += 5
            if (!isStrictOem) score += 10 else score += 5

            val (statusText, statusBgColor) = when {
                isIgnoringBatteryOpt && score >= 85 -> Pair("🟢 ENABLED (100% OPTIMAL)", BentoGreenPrimary)
                score >= 65 -> Pair("🟡 RECOMMENDED (ACTION ADVISED)", BentoAmber)
                else -> Pair("🔴 REQUIRED (RESTRICTED)", BentoRed)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoHeroCardBg)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Power, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BATTERY OPTIMIZATION & READINESS",
                                color = BentoTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ensures 24/7 background sensor monitoring, watchdog recovery, and safety alerts run uninterrupted.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Status Badge & Readiness Score
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusBgColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusBgColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusBgColor
                            )
                            Text(
                                text = "Score: $score%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BentoTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Capability Breakdown
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoHeroCardBg)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Battery Exemption:", fontSize = 11.sp, color = BentoTextSecondary)
                            Text(
                                if (isIgnoringBatteryOpt) "🟢 Unrestricted (Granted)" else "🔴 Restricted (Action Required)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isIgnoringBatteryOpt) BentoGreenPrimary else BentoRed
                            )
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Alarm Execution Capability:", fontSize = 11.sp, color = BentoTextSecondary)
                            Text(
                                if (canExactAlarms) "🟢 Active" else "🟡 Limited",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (canExactAlarms) BentoGreenPrimary else BentoAmber
                            )
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Notification Delivery:", fontSize = 11.sp, color = BentoTextSecondary)
                            Text(
                                if (notifGranted) "🟢 Active" else "🟡 Limited",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (notifGranted) BentoGreenPrimary else BentoAmber
                            )
                        }
                        if (isStrictOem) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ OEM Notice ($manufacturer): This device applies custom OEM background rules. Ensure 'Autostart' and 'Unrestricted Battery' are enabled in System Settings.",
                                fontSize = 10.sp,
                                color = BentoAmber,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        // System Self-Audit & Health Monitor Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoHeroCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .clickable { onOpenAuditScreen() }
                    .padding(20.dp)
                    .testTag("open_self_audit_card")
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoCardBg)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SYSTEM SELF-AUDIT & SERVICE HEALTH",
                                color = BentoTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Run full system diagnostics on core services, sensor nodes, background listeners, and view historical health reports.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BentoTextMuted)
                    }
                }
            }
        }

        // 1. Export Logs Bento Card (Phase 12 Secure Backup)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoHeroCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoCardBg)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SECURE ENCRYPTED LOG EXPORT",
                                color = BentoTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Export event logs to local encrypted JSON backup file.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            onExportLogs { success, message ->
                                exportFeedback = message
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary, contentColor = Color.White),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_logs_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Encrypted JSON Backup", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    if (exportFeedback != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = exportFeedback!!,
                            color = BentoGreenVibrant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 1.5 Manual Travel Mode Override Bento Card (Phase 2)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoHeroCardBg)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MANUAL TRAVEL MODE OVERRIDE",
                                color = BentoTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Manually override IDDE travel detection. Lock system tracking to specific transportation modes to suspend or force warnings.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modes = listOf(
                            "AUTO" to "Auto Detect",
                            "DRIVING" to "Driving",
                            "PASSENGER" to "Passenger",
                            "TRAIN" to "Train",
                            "BUS" to "Bus"
                        )
                        modes.forEach { (key, label) ->
                            val isSelected = travelMode == key
                            Button(
                                onClick = { onTravelModeChange(key) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) BentoGreenPrimary else BentoHeroCardBg,
                                    contentColor = if (isSelected) Color.White else BentoTextPrimary
                                ),
                                shape = CircleShape,
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("travel_mode_btn_${key.lowercase()}")
                            ) {
                                Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Sensor Monitoring Toggles
        item {
            Text(
                text = "SENSOR SUBSYSTEM MONITORING TOGGLES",
                color = BentoTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingSwitchRow(
                    title = "Thermal Subsystem Monitoring",
                    subtitle = "Track battery temperature and heat dissipation sensors",
                    checked = monitorThermal,
                    onCheckedChange = onToggleThermal,
                    icon = Icons.Default.Sensors
                )
                SettingSwitchRow(
                    title = "Geomagnetic Field Monitoring",
                    subtitle = "Monitor magnetic anomaly spikes and orientation",
                    checked = monitorMagnetic,
                    onCheckedChange = onToggleMagnetic,
                    icon = Icons.Default.Security
                )
                SettingSwitchRow(
                    title = "Proximity & Enclosure Detection",
                    subtitle = "Confirm pocket enclosure and ambient light states",
                    checked = monitorProximity,
                    onCheckedChange = onToggleProximity,
                    icon = Icons.Default.Settings
                )
            }
        }

        // 3. Refresh Interval & Battery Savings
        item {
            Text(
                text = "REFRESH INTERVAL & BATTERY OPTIMIZATION",
                color = BentoTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = BentoGreenPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sensor Polling Cadence: ${refreshIntervalMs}ms",
                            color = BentoTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lower intervals increase real-time precision. Higher intervals conserve device battery.",
                        color = BentoTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IntervalButton("250ms", refreshIntervalMs == 250) { onChangeRefreshInterval(250) }
                        IntervalButton("500ms", refreshIntervalMs == 500) { onChangeRefreshInterval(500) }
                        IntervalButton("1000ms", refreshIntervalMs == 1000) { onChangeRefreshInterval(1000) }
                        IntervalButton("2000ms", refreshIntervalMs == 2000) { onChangeRefreshInterval(2000) }
                    }
                }
            }
        }

        // 3.1 Thermal Alert Threshold Selection
        item {
            Text(
                text = "THERMAL PROTECTION",
                color = BentoTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = BentoRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Critical Temperature Threshold",
                            color = BentoTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Voice announcements will only occur when the device temperature reaches or exceeds the selected threshold. Temperatures below the selected limit will continue to be monitored silently.",
                        color = BentoTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IntervalButton("30°C", thermalThresholdC == 30) { onChangeThermalThreshold(30) }
                        IntervalButton("35°C", thermalThresholdC == 35) { onChangeThermalThreshold(35) }
                        IntervalButton("40°C", thermalThresholdC == 40) { onChangeThermalThreshold(40) }
                        IntervalButton("45°C (Default)", thermalThresholdC == 45) { onChangeThermalThreshold(45) }
                    }
                }
            }
        }

        // 4. Database Security & Encryption Settings
        item {
            Text(
                text = "DATABASE ENCRYPTION & INTEGRITY",
                color = BentoTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            SettingSwitchRow(
                title = "AES-256 Local Log Encryption",
                subtitle = "Encrypt Room database event logs at rest",
                checked = encryptionEnabled,
                onCheckedChange = onToggleEncryption,
                icon = Icons.Default.Lock
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BentoHeroCardBg)
                    .padding(8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = BentoTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = BentoTextSecondary,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BentoGreenPrimary,
                    checkedTrackColor = BentoHeroCardBg
                )
            )
        }
    }
}
@Composable
fun ServiceStatusRow(
    title: String,
    serviceEnabled: Boolean,
    onToggleService: (Boolean) -> Unit,
    notify: Boolean,
    announce: Boolean,
    onToggleNotify: (Boolean) -> Unit,
    onToggleAnnounce: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = BentoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Switch(
                checked = serviceEnabled,
                onCheckedChange = onToggleService,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BentoGreenVibrant,
                    checkedTrackColor = BentoGreenPrimary.copy(alpha = 0.3f)
                )
            )
        }
        if (serviceEnabled) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (notify) Icons.Default.Notifications else Icons.Default.NotificationsOff, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(checked = notify, onCheckedChange = onToggleNotify, modifier = Modifier.scale(0.8f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (announce) Icons.Default.VolumeUp else Icons.Default.VolumeOff, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(checked = announce, onCheckedChange = onToggleAnnounce, modifier = Modifier.scale(0.8f))
                }
            }
        }
    }
}
@Composable
fun ServiceToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = BentoTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BentoGreenVibrant,
                checkedTrackColor = BentoGreenPrimary.copy(alpha = 0.3f)
            )
        )
    }
}
@Composable
fun IntervalButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tagLabel = label.substringBefore(" ")
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) BentoGreenPrimary else BentoHeroCardBg,
            contentColor = if (isSelected) Color.White else BentoTextPrimary
        ),
        shape = CircleShape,
        modifier = Modifier.height(36.dp).testTag("interval_button_$tagLabel")
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
