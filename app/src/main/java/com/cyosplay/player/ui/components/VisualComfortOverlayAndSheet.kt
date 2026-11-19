package com.cyosplay.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Non-interactive screen overlay that applies Unified Night Mode / Eye Comfort amber warmth
 * and Sub-Backlight Extra Dimming for late-night viewing without eye strain.
 */
@Composable
fun VisualComfortScreenOverlay(
    eyeComfortEnabled: Boolean,
    nightModeEnabled: Boolean = false,
    warmthIntensity: Float = 0.35f,
    extraDimIntensity: Float = 0f,
    modifier: Modifier = Modifier
) {
    val activeWarmth = if (eyeComfortEnabled || nightModeEnabled) warmthIntensity.coerceAtLeast(0.05f) else 0f
    if (activeWarmth <= 0.01f && extraDimIntensity <= 0.01f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("visual_comfort_screen_overlay")
    ) {
        if (activeWarmth > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFF9800).copy(alpha = (activeWarmth * 0.35f).coerceIn(0f, 0.45f)))
            )
        }
        if (extraDimIntensity > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (extraDimIntensity * 0.68f).coerceIn(0f, 0.74f)))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualComfortBottomSheet(
    nightModeEnabled: Boolean = false,
    nightModeAudioEnabled: Boolean = false,
    eyeComfortEnabled: Boolean,
    warmthIntensity: Float,
    extraDimIntensity: Float,
    batterySaverEnabled: Boolean,
    onToggleNightMode: (Boolean) -> Unit = {},
    onToggleNightModeAudio: () -> Unit = {},
    onToggleEyeComfort: (Boolean) -> Unit,
    onWarmthChange: (Float) -> Unit,
    onExtraDimChange: (Float) -> Unit,
    onToggleBatterySaver: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("visual_comfort_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Unified Night Mode & Eye Comfort",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Warm blue-light filter + sub-zero dimming + dynamic audio compression",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Master Unified Night Mode (Warm Blue-Light + Dynamic Audio Compression)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Unified Night Mode (Visual + Audio)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Enables warm amber blue-light filter, extra night dimming & dynamic audio range compression together",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = nightModeEnabled,
                    onCheckedChange = onToggleNightMode,
                    modifier = Modifier.testTag("night_mode_master_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Night Audio Dynamic Compression",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Boosts whispered dialogue while softening loud explosions for late-night viewing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = nightModeAudioEnabled,
                    onCheckedChange = { onToggleNightModeAudio() },
                    modifier = Modifier.testTag("night_mode_audio_drc_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Warm Blue-Light Eye Filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Warm amber cinema tint that reduces blue-light eye strain",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = eyeComfortEnabled,
                    onCheckedChange = onToggleEyeComfort,
                    modifier = Modifier.testTag("eye_comfort_filter_switch")
                )
            }

            if (eyeComfortEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = kotlin.math.round(warmthIntensity * 100f).toInt() in 10..12,
                        onClick = { onWarmthChange(0.11f) },
                        label = { Text("Night (11%)") }
                    )
                    FilterChip(
                        selected = warmthIntensity in 0.25f..0.36f,
                        onClick = { onWarmthChange(0.28f) },
                        label = { Text("Soft (28%)") }
                    )
                    FilterChip(
                        selected = warmthIntensity in 0.37f..0.58f,
                        onClick = { onWarmthChange(0.48f) },
                        label = { Text("Cinema (48%)") }
                    )
                    FilterChip(
                        selected = warmthIntensity > 0.58f,
                        onClick = { onWarmthChange(0.72f) },
                        label = { Text("Deep (72%)") }
                    )
                }
                Text(
                    text = "Warmth Intensity: ${kotlin.math.round(warmthIntensity * 100f).toInt()}%",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = warmthIntensity,
                    onValueChange = onWarmthChange,
                    valueRange = 0.05f..0.85f
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Extra Night Dimmer: ${kotlin.math.round(extraDimIntensity * 100f).toInt()}%",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = extraDimIntensity,
                onValueChange = onExtraDimChange,
                valueRange = 0f..0.75f
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Battery Saver Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Reduces UI refresh rate during playback & optimizes background tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = batterySaverEnabled,
                    onCheckedChange = onToggleBatterySaver
                )
            }
        }
    }
}

