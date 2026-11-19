package com.cyosplay.player.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyosplay.player.data.model.AdaptiveTextScaleMode
import com.cyosplay.player.data.model.AppFontMode
import com.cyosplay.player.data.model.AppThemeMode
import com.cyosplay.player.data.model.DecoderEngineMode
import com.cyosplay.player.ui.Screen
import com.cyosplay.player.ui.VideoPlayerUiState
import com.cyosplay.player.ui.VideoPlayerViewModel
import com.cyosplay.player.ui.theme.AmberGold
import com.cyosplay.player.ui.theme.CrimsonAlert
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    var showUnifiedThemeTypographyDialog by remember { mutableStateOf(false) }
    var wipeConfirmationStep by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_screen")
    ) {
        // App Version 1.03 Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_version_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CyosPlay 1.06rv17",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("settings_version_text")
                    )
                }
                Surface(
                    color = EmeraldVault.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "v${uiState.appVersionName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldVault,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 0: Playback & Interface Controls (Hide All Texts, PiP, Auto-Resume)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_playback_controls_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = EmeraldVault)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Playback & Display Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Hide All Texts & Tap Individually to Reveal in One Place
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide All Texts (Tap to See in One Place)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Hides folder and video labels until tapped individually; displays full untruncated text in one place without worrying about size",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.hideAllTextsEnabled,
                        onCheckedChange = { viewModel.setHideAllTextsEnabled(it) },
                        modifier = Modifier.testTag("switch_hide_all_texts")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Picture-in-Picture (PiP) Auto-Enter on Home Press
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Picture-in-Picture (PiP) Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Watch videos in a floating PiP window while using other apps (via PiP button or pressing Home)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.autoPipEnabled,
                        onCheckedChange = { viewModel.setAutoPipEnabled(it) },
                        modifier = Modifier.testTag("switch_auto_pip_mode")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Save Last Playback Position & Auto-Resume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save Last Position & Auto-Resume",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Automatically saves the last playback position for every video and resumes from that point when reopened",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.autoResumePlayback,
                        onCheckedChange = { viewModel.setAutoResumePlayback(it) },
                        modifier = Modifier.testTag("switch_auto_resume_playback")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 1: Low-RAM Engine, Battery Saver & Improved Video/Audio Decoders
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Low-RAM Smoothness, Battery Saver & Decoders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.lowRamShieldEnabled) {
                        uiState.memoryBudget?.tier?.description
                            ?: "Low-RAM smoothness active with RGB_565 thumbnails & compact buffers"
                    } else {
                        "Manual control: Low-RAM smoothness stays OFF until you turn it ON below"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Low-RAM Smoothness Shield", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Stays OFF by default until manually enabled; optimizes buffers & cache trimming when turned ON",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.lowRamShieldEnabled,
                        onCheckedChange = { viewModel.setLowRamShieldEnabled(it) },
                        modifier = Modifier.testTag("switch_low_ram_shield")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = AmberGold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Battery Smoothness Shield", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "OFF by default until manually turned ON. Stabilizes frame pacing, throttles background polling & reduces CPU/RAM power draw during video playback",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.batterySaverModeEnabled,
                        onCheckedChange = { viewModel.setBatterySaverModeEnabled(it) },
                        modifier = Modifier.testTag("switch_battery_saver_mode")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Video & Audio Multi-Codec Decoder Engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Supports HEVC/H.265 10-bit, AV1, VP9, Dolby Vision, EAC3 Atmos 5.1/7.1, AC3, DTS-HD, FLAC, Opus & 24/32-bit PCM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DecoderEngineMode.HW,
                        DecoderEngineMode.HW_PLUS,
                        DecoderEngineMode.SW,
                        DecoderEngineMode.SW_PLUS
                    ).forEach { mode ->
                        val isSelected = uiState.decoderEngineMode == mode ||
                            (mode == DecoderEngineMode.HW_PLUS && uiState.decoderEngineMode == DecoderEngineMode.AUTO_HW_PLUS) ||
                            (mode == DecoderEngineMode.SW_PLUS && uiState.decoderEngineMode == DecoderEngineMode.SOFTWARE_MULTI_CORE)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setDecoderEngineMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("decoder_mode_${mode.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Item 3: Explicit Force Hardware Acceleration Toggle (OFF by default until manually turned ON)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = ElectricCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Force Hardware Acceleration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "OFF by default until manually turned ON. Forces dedicated vendor hardware MediaCodec decoders for H.264/AVC, HEVC, AV1 & Matroska/WebM with instant software fallback",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.forceHardwareAccelerationHighBitrate,
                        onCheckedChange = { viewModel.setForceHardwareAccelerationHighBitrate(it) },
                        modifier = Modifier.testTag("switch_force_hw_acceleration")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Item 8: Auto-Skip Intros & Credits
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = AmberGold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto-Skip Intros & Credits",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Detects opening intros and ending credits with seamless auto-skip and floating on-screen Skip Intro / Skip Credits pill",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.autoSkipIntrosCredits,
                        onCheckedChange = { viewModel.setAutoSkipIntrosCredits(it) },
                        modifier = Modifier.testTag("switch_auto_skip_intros_credits")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Item 6: Night Shield Mode & Eye Comfort
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = AmberGold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Night Shield Mode & Eye Comfort",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Warm amber blue-light filter + sub-zero eye comfort dimmer + dynamic night audio compression",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.nightModeEnabled,
                        onCheckedChange = { viewModel.setNightModeEnabled(it) },
                        modifier = Modifier.testTag("settings_night_mode_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 2: Stealth Playback & Trace Controls (Vault & Playback Privacy header/subtitle fully hidden)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = EmeraldVault)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Stealth Playback",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Zero history, zero recent-apps screenshot (FLAG_SECURE), zero external media broadcasts, and automatic cache wipe on exit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.stealthPlaybackEnabled,
                        onCheckedChange = { viewModel.setStealthPlaybackEnabled(it) },
                        modifier = Modifier.testTag("switch_stealth_playback")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { wipeConfirmationStep = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("wipe_playback_traces_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonAlert)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wipe Traces")
                    }

                    OutlinedButton(
                        onClick = { viewModel.navigateTo(Screen.SecurityQuestionSetup) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Recovery Question")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 3: Unified Theme & Typography in One Place + Subtitle Studio
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = ElectricCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Theme & Typography",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.themeMode.label} • ${uiState.fontMode.label} • ${uiState.textScaleMode.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }
                    Button(
                        onClick = { showUnifiedThemeTypographyDialog = true },
                        modifier = Modifier.testTag("cinema_theme_typography_unified_button")
                    ) {
                        Text("Customize")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(Screen.SubtitleStudio) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Subtitle Studio")
                    }
                    OutlinedButton(
                        onClick = { viewModel.toggleVisualComfortSheet(true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Eye Comfort & Night Shield")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 4: About Us — CyosPlay 1.07rv6 Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("about_cyosplay_card")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = ElectricCyan
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "About Us",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "CyosPlay 1.07rv6",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("about_us_description_text")
                    )
                }
            }
        }
    }

    // Unified Theme & Typography Dialog (One Place Setting)
    if (showUnifiedThemeTypographyDialog) {
        AlertDialog(
            onDismissRequest = { showUnifiedThemeTypographyDialog = false },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            modifier = Modifier.testTag("cinema_theme_typography_dialog"),
            title = {
                Text(
                    text = "Theme & Typography",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("theme_mode_${mode.name}")
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Font Family",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFontMode.entries.forEach { font ->
                            FilterChip(
                                selected = uiState.fontMode == font,
                                onClick = { viewModel.setFontMode(font) },
                                label = { Text(font.label) },
                                modifier = Modifier.testTag("font_mode_${font.name}")
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Text Readability Scale",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdaptiveTextScaleMode.entries.forEach { scale ->
                            FilterChip(
                                selected = uiState.textScaleMode == scale,
                                onClick = { viewModel.setTextScaleMode(scale) },
                                label = { Text(scale.label) },
                                modifier = Modifier.testTag("text_scale_${scale.name}")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showUnifiedThemeTypographyDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 3-Step Confirmation Dialog for Wipe Traces
    if (wipeConfirmationStep in 1..3) {
        val stepTitle = when (wipeConfirmationStep) {
            1 -> "Wipe Traces — Confirmation (1/3)"
            2 -> "Wipe Traces — Confirmation (2/3)"
            else -> "Wipe Traces — Final Confirmation (3/3)"
        }
        val stepBody = when (wipeConfirmationStep) {
            1 -> "Step 1 of 3: Are you sure you want to wipe all playback history, resume positions, bookmarks, and temporary traces?"
            2 -> "Step 2 of 3: Please confirm again. Wiping traces will clear all saved video resume timestamps across all folders."
            else -> "Step 3 of 3 (Final Warning): This action cannot be undone. Tap below to permanently wipe all playback traces now."
        }
        val confirmButtonLabel = when (wipeConfirmationStep) {
            1 -> "Confirm (1/3)"
            2 -> "Confirm (2/3)"
            else -> "Wipe All Traces Now (3/3)"
        }

        AlertDialog(
            onDismissRequest = { wipeConfirmationStep = 0 },
            modifier = Modifier.testTag("wipe_traces_triple_confirm_dialog"),
            title = {
                Text(
                    text = stepTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stepBody)
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (wipeConfirmationStep < 3) {
                            wipeConfirmationStep += 1
                        } else {
                            wipeConfirmationStep = 0
                            viewModel.wipeAllPlaybackTracesNow()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (wipeConfirmationStep == 3) CrimsonAlert else AmberGold
                    ),
                    modifier = Modifier.testTag("wipe_traces_confirm_step_button")
                ) {
                    Text(
                        text = confirmButtonLabel,
                        color = if (wipeConfirmationStep == 3) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { wipeConfirmationStep = 0 },
                    modifier = Modifier.testTag("wipe_traces_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

