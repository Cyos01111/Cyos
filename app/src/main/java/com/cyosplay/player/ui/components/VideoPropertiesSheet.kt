package com.cyosplay.player.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyosplay.player.data.model.VideoProperties
import com.cyosplay.player.ui.theme.AmberGold
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault

/**
 * Comprehensive Properties Inspector for a video item.
 * Displays all exact technical specifications, duration in ms, exact file bytes,
 * audio/video streams, and AES-256-GCM encrypted data codes without any lock button.
 */
@Composable
fun VideoPropertiesSheet(
    properties: VideoProperties,
    onDismiss: () -> Unit,
    onToggleLock: (() -> Unit)? = null,
    onRequestUnlockDialog: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        modifier = Modifier.testTag("video_properties_dialog"),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Properties Icon",
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Properties",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("properties_dialog_title")
                        )
                    }

                    // AES-256-GCM header badge hidden per user privacy request
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = properties.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("properties_file_name")
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .testTag("properties_scroll_column"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. GENERAL PROPERTIES
                item {
                    PropertySectionCard(
                        sectionTitle = "GENERAL FILE & STORAGE PROPERTIES",
                        accentColor = ElectricCyan,
                        rows = listOf(
                            "File Name" to properties.fileName,
                            "Exact Canonical Path" to properties.exactFilePath,
                            "Exact File Size" to properties.formattedSizeWithExactBytes,
                            "Exact Duration" to properties.formattedDurationExact,
                            "Storage Volume" to properties.storageTypeLabel,
                            "Date Modified" to properties.dateModifiedFormatted,
                            "Date Created" to properties.dateCreatedFormatted.ifBlank { properties.dateModifiedFormatted }
                        )
                    )
                }

                // 2. VIDEO STREAM & DISPLAY GEOMETRY
                item {
                    PropertySectionCard(
                        sectionTitle = "VIDEO STREAM & DISPLAY GEOMETRY",
                        accentColor = ElectricCyan,
                        rows = listOf(
                            "Exact Resolution" to properties.exactDimensionsLabel,
                            "Aspect Ratio & Pixels" to "${properties.aspectRatioLabel} (${properties.pixelCountMegapixels})",
                            "Video Codec & Profile" to properties.videoCodecProfile,
                            "HDR & Color Dynamic Range" to properties.hdrColorDynamicRange,
                            "Frame Rate (FPS)" to properties.frameRateFpsLabel,
                            "Video Bitrate" to properties.videoBitrateLabel,
                            "Orientation Metadata" to "${properties.rotationDegrees}°"
                        )
                    )
                }

                // 3. AUDIO STREAM & CHANNELS
                item {
                    PropertySectionCard(
                        sectionTitle = "AUDIO STREAM & CHANNELS",
                        accentColor = EmeraldVault,
                        rows = listOf(
                            "Audio Codec & Channels" to properties.audioCodecChannelsLabel,
                            "Audio Sample Rate" to properties.audioSampleRateLabel,
                            "Audio Bitrate" to properties.audioBitrateLabel
                        )
                    )
                }

                // 4. PLAYBACK STATE & LAST RESUME POSITION
                item {
                    PropertySectionCard(
                        sectionTitle = "PLAYBACK STATE & RESUME POSITION",
                        accentColor = AmberGold,
                        rows = listOf(
                            "Last Playback Position" to properties.formattedLastPlaybackPosition,
                            "Auto-Resume State" to "Automatic resume from saved timestamp active"
                        )
                    )
                }


                // 6. SUPPORTED RESOLUTION PROFILE
                item {
                    PropertySectionCard(
                        sectionTitle = "",
                        accentColor = EmeraldVault,
                        rows = listOf(
                            "Supported Resolutions" to properties.supportedResolutionsSummary
                        )
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy All Properties Button
                OutlinedButton(
                    onClick = {
                        val report = formatPropertiesTextReport(properties)
                        clipboardManager.setText(AnnotatedString(report))
                        Toast.makeText(context, "Properties copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("copy_properties_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_properties_button")
                ) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun MasterPasswordLockDialog(
    isOpen: Boolean,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onUnlock: (password: String) -> Unit
) {
    if (!isOpen) return

    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("master_password_lock_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = AmberGold,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Security Verification",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("master_password_dialog_title")
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "All video data codes and stream profiles are encrypted with AES-256-GCM.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Master Password") },
                    placeholder = { Text("Enter master password") },
                    singleLine = true,
                    isError = !errorMessage.isNullOrBlank(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("master_password_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberGold,
                        focusedLabelColor = AmberGold
                    )
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("master_password_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUnlock(passwordInput) },
                enabled = passwordInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                modifier = Modifier.testTag("confirm_master_password_button")
            ) {
                Text("Verify", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_master_password_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PropertySectionCard(
    sectionTitle: String,
    accentColor: Color,
    rows: List<Pair<String, String>>
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (sectionTitle.isNotBlank()) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            rows.forEach { (label, value) ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = if (label.contains("SHA") || label.contains("Token") || label.contains("Path")) FontFamily.Monospace else FontFamily.Default,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats a clean, text-based technical properties manifest for copying to clipboard.
 */
fun formatPropertiesTextReport(props: VideoProperties): String {
    return buildString {
        appendLine("=== VIDEO PROPERTIES & TECHNICAL SPECIFICATIONS ===")
        appendLine("File Name: ${props.fileName}")
        appendLine("Canonical Path: ${props.exactFilePath}")
        appendLine("Exact File Size: ${props.formattedSizeWithExactBytes}")
        appendLine("Exact Duration: ${props.formattedDurationExact}")
        appendLine("Resolution: ${props.exactDimensionsLabel}")
        appendLine("Aspect Ratio: ${props.aspectRatioLabel} (${props.pixelCountMegapixels})")
        appendLine("Video Codec: ${props.videoCodecProfile}")
        appendLine("AVC Profile/Level: ${props.avcProfileLevelLabel}")
        appendLine("Chroma Subsampling / Bit Depth: ${props.chromaSubsamplingBitDepth}")
        appendLine("Web/Stream Frame Dimensions: ${props.webStreamFrameDimensionsLabel}")
        appendLine("Frame Rate: ${props.frameRateFpsLabel}")
        appendLine("Video Bitrate: ${props.videoBitrateLabel}")
        appendLine("HDR / Color: ${props.hdrColorDynamicRange}")
        appendLine("Audio Stream: ${props.audioCodecChannelsLabel} • ${props.aacProfileLabel} • ${props.audioSampleRateLabel} • ${props.audioBitrateLabel}")
        appendLine("Last Resume Position: ${props.formattedLastPlaybackPosition}")
        appendLine("Storage Partition: ${props.storageTypeLabel}")
        appendLine("Date Modified: ${props.dateModifiedFormatted}")
        appendLine("Date Created: ${props.dateCreatedFormatted}")
        appendLine("==================================================")
    }
}
