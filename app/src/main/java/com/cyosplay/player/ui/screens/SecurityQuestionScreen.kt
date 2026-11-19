package com.cyosplay.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cyosplay.player.ui.VideoPlayerUiState
import com.cyosplay.player.ui.VideoPlayerViewModel
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityQuestionScreen(
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateBack() }

    var question by remember { mutableStateOf(uiState.securityQuestionText) }
    var answer by remember { mutableStateOf("") }
    var showPrimaryAnswer by remember { mutableStateOf(false) }

    var secondaryQuestion by remember { mutableStateOf(uiState.secondarySecurityQuestionText) }
    var secondaryAnswer by remember { mutableStateOf("") }
    var showSecondaryAnswer by remember { mutableStateOf(false) }
    var showRecoveryQuestionsSingleList by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("security_question_screen")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.navigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Encrypted Recovery Questions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Item 8: Recovery questions set in popup — when tapped then show it
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showRecoveryQuestionsSingleList = true }
                .testTag("security_recovery_questions_single_place_toggle")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "All Recovery Questions (Tap to Open Popup)",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showRecoveryQuestionsSingleList) {
            AlertDialog(
                onDismissRequest = { showRecoveryQuestionsSingleList = false },
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
                            text = "Tap any recovery question below to set it as your primary recovery question:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.presetRecoveryQuestions.forEachIndexed { idx, preset ->
                                FilterChip(
                                    selected = question == preset,
                                    onClick = {
                                        question = preset
                                        showRecoveryQuestionsSingleList = false
                                    },
                                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("preset_recovery_question_$idx")
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRecoveryQuestionsSingleList = false }) {
                        Text("Close")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Primary Security Question") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("primary_security_question_input")
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Primary Recovery Answer") },
            visualTransformation = if (showPrimaryAnswer) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { showPrimaryAnswer = !showPrimaryAnswer },
                    modifier = Modifier.testTag("toggle_primary_answer_visibility")
                ) {
                    Icon(
                        imageVector = if (showPrimaryAnswer) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPrimaryAnswer) "Hide Answer" else "Show Answer"
                    )
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("primary_security_answer_input")
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Secondary Backup Recovery Question (Optional)",
            style = MaterialTheme.typography.titleSmall,
            color = EmeraldVault,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = secondaryQuestion,
            onValueChange = { secondaryQuestion = it },
            label = { Text("Secondary Security Question") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("secondary_security_question_input")
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = secondaryAnswer,
            onValueChange = { secondaryAnswer = it },
            label = { Text("Secondary Recovery Answer") },
            visualTransformation = if (showSecondaryAnswer) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { showSecondaryAnswer = !showSecondaryAnswer },
                    modifier = Modifier.testTag("toggle_secondary_answer_visibility")
                ) {
                    Icon(
                        imageVector = if (showSecondaryAnswer) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showSecondaryAnswer) "Hide Answer" else "Show Answer"
                    )
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("secondary_security_answer_input")
        )

        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = {
                if (question.isNotBlank() && answer.isNotBlank()) {
                    viewModel.saveSecurityQuestion(question, answer)
                    if (secondaryQuestion.isNotBlank() && secondaryAnswer.isNotBlank()) {
                        viewModel.saveSecondarySecurityQuestion(secondaryQuestion, secondaryAnswer)
                    }
                    viewModel.navigateBack()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_recovery_questions_button")
        ) {
            Text("Encrypt & Save Recovery Credentials")
        }
    }
}
