package com.cyosplay.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.cyosplay.player.data.model.VaultAuthState
import com.cyosplay.player.data.model.VaultVideoEntry
import com.cyosplay.player.ui.VideoPlayerUiState
import com.cyosplay.player.ui.VideoPlayerViewModel
import com.cyosplay.player.ui.theme.CrimsonAlert
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault
import com.cyosplay.player.util.BiometricAuthHelper
import com.cyosplay.player.util.Formatters

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var pinInput by remember { mutableStateOf("") }
    var showUnlockPin by remember { mutableStateOf(false) }

    var setupPinInput by remember { mutableStateOf("") }
    var showSetupPin by remember { mutableStateOf(false) }
    var setupQuestionInput by remember { mutableStateOf("What is your favorite cinema film?") }
    var setupAnswerInput by remember { mutableStateOf("") }
    var showSetupAnswer by remember { mutableStateOf(false) }
    var showSetupRecoveryQuestionsList by remember { mutableStateOf(false) }

    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showDialogRecoveryQuestionsList by remember { mutableStateOf(false) }
    var recoveryAnswer by remember { mutableStateOf("") }
    var showRecoveryAnswer by remember { mutableStateOf(false) }
    var recoveryNewPin by remember { mutableStateOf("") }
    var showRecoveryNewPin by remember { mutableStateOf(false) }
    var showImportFromLibraryDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (uiState.vaultAuthState == VaultAuthState.UNLOCKED_REAL ||
                uiState.vaultAuthState == VaultAuthState.UNLOCKED_DECOY
            ) {
                viewModel.lockVault()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(uiState.vaultAuthState) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        viewModel.notifyVaultUserInteraction()
                    }
                }
            }
            .padding(16.dp)
            .testTag("vault_screen")
    ) {
        // Vault Header with Zero-Knowledge & Anti-Crack Status + 10s Auto-Lock Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(EmeraldVault.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Private Vault",
                        tint = EmeraldVault
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Private Cinema Vault",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Biometric & PIN Protected • 10s Auto-Lock Security",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldVault
                    )
                }
            }

            if (uiState.vaultAuthState == VaultAuthState.UNLOCKED_REAL ||
                uiState.vaultAuthState == VaultAuthState.UNLOCKED_DECOY
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = ElectricCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("vault_auto_lock_timer_badge")
                    ) {
                        Text(
                            text = "Auto-Lock ${uiState.vaultAutoLockRemainingSeconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.lockVault() },
                        modifier = Modifier.testTag("lock_vault_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lock")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (uiState.vaultAuthState) {
            VaultAuthState.UNINITIALIZED_NO_PIN -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Set Up Private Vault PIN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your videos are kept strictly private on your device and protected by your PIN and biometric authentication.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = setupPinInput,
                            onValueChange = { setupPinInput = it.filter { ch -> ch.isDigit() }.take(8) },
                            label = { Text("Create 6-8 Digit Vault PIN") },
                            visualTransformation = if (showSetupPin) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showSetupPin = !showSetupPin },
                                    modifier = Modifier.testTag("vault_setup_pin_show_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (showSetupPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showSetupPin) "Hide PIN" else "Show PIN"
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_setup_pin_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Item 8: Recovery questions set in popup — when tapped then show it
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSetupRecoveryQuestionsList = true }
                                .testTag("recovery_questions_single_place_toggle")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recovery Questions (Tap to Open Popup)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = setupQuestionInput,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        if (showSetupRecoveryQuestionsList) {
                            AlertDialog(
                                onDismissRequest = { showSetupRecoveryQuestionsList = false },
                                modifier = Modifier.testTag("recovery_questions_popup_dialog"),
                                title = {
                                    Text(
                                        text = "Recovery Questions",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Select a preset recovery question or enter your own custom question:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            uiState.presetRecoveryQuestions.forEachIndexed { idx, preset ->
                                                FilterChip(
                                                    selected = setupQuestionInput == preset,
                                                    onClick = {
                                                        setupQuestionInput = preset
                                                        showSetupRecoveryQuestionsList = false
                                                    },
                                                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.testTag("vault_preset_question_$idx")
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = setupQuestionInput,
                                            onValueChange = { setupQuestionInput = it },
                                            label = { Text("Encrypted Recovery Question") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(onClick = { showSetupRecoveryQuestionsList = false }) {
                                        Text("Done")
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = setupAnswerInput,
                            onValueChange = { setupAnswerInput = it },
                            label = { Text("Recovery Answer") },
                            visualTransformation = if (showSetupAnswer) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showSetupAnswer = !showSetupAnswer },
                                    modifier = Modifier.testTag("vault_setup_answer_show_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (showSetupAnswer) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showSetupAnswer) "Hide Answer" else "Show Answer"
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_setup_answer_input")
                        )

                        uiState.vaultErrorMessage?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = err, color = CrimsonAlert, style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                viewModel.setupInitialVaultPin(
                                    pin = setupPinInput,
                                    securityQuestion = setupQuestionInput,
                                    securityAnswer = setupAnswerInput
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_create_pin_button")
                        ) {
                            Icon(Icons.Default.EnhancedEncryption, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Activate Private Vault")
                        }
                    }
                }
            }

            VaultAuthState.LOCKED -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Private Vault Locked",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enter your PIN or use biometrics to unlock your private media.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it },
                            label = { Text("Enter Vault PIN") },
                            visualTransformation = if (showUnlockPin) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showUnlockPin = !showUnlockPin },
                                    modifier = Modifier.testTag("vault_unlock_pin_show_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (showUnlockPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showUnlockPin) "Hide PIN" else "Show PIN"
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_unlock_pin_input")
                        )

                        uiState.vaultErrorMessage?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = err,
                                color = CrimsonAlert,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.testTag("vault_error_message_text")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.unlockVaultWithPin(pinInput)
                                    pinInput = ""
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vault_unlock_button")
                            ) {
                                Text("Unlock Vault")
                            }

                            if (uiState.biometricVaultEnabled && activity != null) {
                                OutlinedButton(
                                    onClick = {
                                        BiometricAuthHelper.showBiometricPrompt(
                                            activity = activity,
                                            onSuccess = { viewModel.onBiometricUnlockSuccess() },
                                            onError = {}
                                        )
                                    },
                                    modifier = Modifier.testTag("vault_biometric_button")
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = "Biometric Unlock")
                                }
                            }
                        }

                        if (uiState.hasSecurityQuestion) {
                            TextButton(
                                onClick = { showRecoveryDialog = true },
                                modifier = Modifier.testTag("vault_forgot_pin_button")
                            ) {
                                Text("Forgot PIN? Recover via Encrypted Security Question")
                            }
                        }
                    }
                }
            }

            VaultAuthState.UNLOCKED_REAL, VaultAuthState.UNLOCKED_DECOY -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.vaultAuthState == VaultAuthState.UNLOCKED_DECOY) {
                            "Vault Media (Decoy Session)"
                        } else {
                            "Encrypted Vault Videos (${uiState.vaultEntries.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showImportFromLibraryDialog = true },
                        modifier = Modifier.testTag("vault_import_video_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Move Video to Vault")
                    }
                }

                uiState.vaultErrorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = CrimsonAlert,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag("vault_unlocked_error_text")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.vaultEntries.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = EmeraldVault,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No videos in Private Vault yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hold any video in your library and tap Move -> Private Vault, or tap 'Move Video to Vault' above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.vaultEntries, key = { it.vaultId }) { entry ->
                            VaultEntryCard(
                                entry = entry,
                                onPlay = { viewModel.playVaultEntry(entry) },
                                onRestore = { viewModel.restoreVaultEntry(entry.vaultId) },
                                onShred = { viewModel.shredVaultEntry(entry.vaultId) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text("Encrypted PIN Recovery") },
            text = {
                Column {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDialogRecoveryQuestionsList = !showDialogRecoveryQuestionsList }
                            .testTag("recovery_dialog_questions_toggle")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Recovery Questions (Tap to ${if (showDialogRecoveryQuestionsList) "Hide" else "Show"})",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            if (showDialogRecoveryQuestionsList) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Primary: ${uiState.securityQuestionText}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (uiState.hasSecondarySecurityQuestion) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Backup: ${uiState.secondarySecurityQuestionText}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldVault
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = recoveryAnswer,
                        onValueChange = { recoveryAnswer = it },
                        label = { Text("Your Secret Answer") },
                        visualTransformation = if (showRecoveryAnswer) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showRecoveryAnswer = !showRecoveryAnswer },
                                modifier = Modifier.testTag("vault_recovery_answer_show_toggle")
                            ) {
                                Icon(
                                    imageVector = if (showRecoveryAnswer) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showRecoveryAnswer) "Hide Answer" else "Show Answer"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = recoveryNewPin,
                        onValueChange = { recoveryNewPin = it.filter { ch -> ch.isDigit() }.take(8) },
                        label = { Text("New 6-8 Digit Vault PIN") },
                        visualTransformation = if (showRecoveryNewPin) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showRecoveryNewPin = !showRecoveryNewPin },
                                modifier = Modifier.testTag("vault_recovery_pin_show_toggle")
                            ) {
                                Icon(
                                    imageVector = if (showRecoveryNewPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showRecoveryNewPin) "Hide PIN" else "Show PIN"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.recoverVaultPinWithSecurityAnswer(recoveryAnswer, recoveryNewPin)) {
                            showRecoveryDialog = false
                            recoveryAnswer = ""
                            recoveryNewPin = ""
                        }
                    }
                ) {
                    Text("Reset & Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showImportFromLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showImportFromLibraryDialog = false },
            title = { Text("Encrypt & Move Video to Vault") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.allVideos, key = { it.path }) { video ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(Formatters.formatFileSize(video.sizeBytes), style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = {
                                        viewModel.moveVideosDirectlyToPrivateVault(listOf(video))
                                        showImportFromLibraryDialog = false
                                    }
                                ) {
                                    Text("Encrypt")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportFromLibraryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun VaultEntryCard(
    entry: VaultVideoEntry,
    onPlay: () -> Unit,
    onRestore: () -> Unit,
    onShred: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.originalTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatters.formatFileSize(entry.sizeBytes)} • ${Formatters.formatDuration(entry.durationMs)} • AES-256-GCM",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldVault
                )
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play encrypted video", tint = ElectricCyan)
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.Restore, contentDescription = "Restore from Vault")
            }
            IconButton(onClick = onShred) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Zero-trace shred", tint = CrimsonAlert)
            }
        }
    }
}
