package com.cyosplay.player.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyosplay.player.data.model.AudioTrackInfo
import com.cyosplay.player.data.model.DecoderEngineMode
import com.cyosplay.player.data.model.SubtitleTrackInfo
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault

@Composable
fun AudioTracksAndDecoderDialog(
    audioTracks: List<AudioTrackInfo>,
    activeAudioCodecHint: String,
    decoderEngineMode: DecoderEngineMode,
    volumeBoostMultiplier: Float,
    nightModeAudioEnabled: Boolean,
    dialogueEnhancerEnabled: Boolean,
    isAudioMuted: Boolean,
    onSelectAudioTrack: (AudioTrackInfo) -> Unit,
    onToggleMuteAudio: (Boolean) -> Unit,
    onToggleDecoderEngine: () -> Unit,
    onVolumeBoostChange: (Float) -> Unit,
    onToggleNightModeAudio: () -> Unit,
    onToggleDialogueEnhancer: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayTracks = if (audioTracks.isNotEmpty()) {
        audioTracks
    } else {
        listOf(
            AudioTrackInfo(
                groupIndex = 0,
                trackIndex = 0,
                label = "Primary Audio Stream (Auto-Downmix)",
                language = "Default",
                codecBadge = activeAudioCodecHint,
                channelCount = 2,
                sampleRate = 48000,
                isSelected = !isAudioMuted
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xC70E1420),
        modifier = Modifier.testTag("audio_tracks_decoder_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ElectricCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Audio Selection & Surround Decoder", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("audio_tracks_scrollable_column")
            ) {
                // Multi-Codec Decoder Toggle Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDecoderEngine() }
                        .testTag("dialog_decoder_engine_card")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Decoder: ${decoderEngineMode.label}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "EAC3 Atmos 5.1/7.1 • AC3 • DTS-HD • FLAC • PCM • AAC",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onToggleDecoderEngine) {
                            Text(decoderEngineMode.badge)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Available Audio Tracks (${displayTracks.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    displayTracks.forEach { track ->
                        val selected = track.isSelected && !isAudioMuted
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAudioTrack(track) }
                                .testTag("audio_track_option_${track.groupIndex}_${track.trackIndex}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = EmeraldVault.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = track.codecBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldVault,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        val chDesc = when {
                                            track.channelCount >= 8 -> "7.1"
                                            track.channelCount >= 6 -> "5.1"
                                            track.channelCount == 2 -> "2.0"
                                            track.channelCount == 1 -> "1.0"
                                            else -> "${track.channelCount}ch"
                                        }
                                        val srKhz = (track.sampleRate.takeIf { it > 0 } ?: 48000) / 1000.0
                                        val srLabel = if (srKhz % 1.0 == 0.0) "${srKhz.toInt()} kHz" else String.format(java.util.Locale.US, "%.1f kHz", srKhz)
                                        Text(
                                            text = "$chDesc • $srLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (selected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected Audio Track",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAudioMuted) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleMuteAudio(!isAudioMuted) }
                            .testTag("audio_track_mute_option")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = null,
                                tint = if (isAudioMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAudioMuted) "Audio Muted (Tap to Unmute)" else "Mute Audio Stream",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isAudioMuted) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Muted",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Volume Pre-Amp Boost: ${(volumeBoostMultiplier * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = volumeBoostMultiplier,
                    onValueChange = onVolumeBoostChange,
                    valueRange = 1.0f..2.0f,
                    modifier = Modifier.testTag("audio_volume_boost_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Center Dialogue Clarity Boost", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Enhances voice frequencies during 5.1/7.1 downmix",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dialogueEnhancerEnabled,
                        onCheckedChange = { onToggleDialogueEnhancer() },
                        modifier = Modifier.testTag("dialogue_enhancer_switch")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Night Mode Loudness Guard", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Compresses sudden loud peaks & lifts whispers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = nightModeAudioEnabled,
                        onCheckedChange = { onToggleNightModeAudio() },
                        modifier = Modifier.testTag("night_mode_audio_switch")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun SubtitleTracksAndSyncDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    externalSubtitleTracks: List<com.cyosplay.player.data.model.ExternalSubtitleTrack> = emptyList(),
    selectedExternalSubtitleTrackIds: Set<String> = emptySet(),
    playAllSubtitlesSimultaneously: Boolean = false,
    subtitleCustomSp: Float = 26f,
    isSubtitleAudioAutoSyncEnabled: Boolean = true,
    subtitleAutoSyncStatusLabel: String = "Auto-Sync Ready (English Audio)",
    onToggleSubtitleAudioAutoSync: () -> Unit = {},
    onIncreaseFontSize: () -> Unit = {},
    onDecreaseFontSize: () -> Unit = {},
    onFontSizeChange: (Float) -> Unit = {},
    onSelectAllSubtitles: () -> Unit = {},
    onClearAllSubtitles: () -> Unit = {},
    onToggleExternalTrack: (String) -> Unit = {},
    onToggleEmbeddedTrack: (SubtitleTrackInfo) -> Unit = {},
    onSelectSubtitleTrack: (SubtitleTrackInfo?) -> Unit,
    syncOffsetMs: Long,
    onAdjustOffsetMs: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onLoadExternalSrt: () -> Unit,
    onOpenSubtitleStudio: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xC70E1420),
        modifier = Modifier.testTag("subtitle_tracks_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subtitles & Multi-Track Playback", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("subtitle_tracks_scrollable_column")
            ) {
                // Item 1: Manual Subtitle Text Size Adjustment (12sp to 56sp)
                Text(
                    text = "Manual Font Size: ${subtitleCustomSp.toInt()} sp (12sp - 56sp)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ElectricCyan
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDecreaseFontSize,
                        modifier = Modifier.testTag("subtitle_font_size_minus_button")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease 2sp")
                    }
                    Slider(
                        value = subtitleCustomSp,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..56f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subtitle_manual_font_size_slider")
                    )
                    IconButton(
                        onClick = onIncreaseFontSize,
                        modifier = Modifier.testTag("subtitle_font_size_plus_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase 2sp")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subtitle_audio_auto_sync_card")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Audio-Language Auto-Sync",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldVault
                                )
                                Text(
                                    text = subtitleAutoSyncStatusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("subtitle_auto_sync_status_text")
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = isSubtitleAudioAutoSyncEnabled,
                                onCheckedChange = { onToggleSubtitleAudioAutoSync() },
                                modifier = Modifier.testTag("subtitle_audio_auto_sync_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Fine-Tune Offset: ${if (syncOffsetMs >= 0) "+$syncOffsetMs" else "$syncOffsetMs"} ms",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onAdjustOffsetMs(-50L) },
                                    modifier = Modifier.testTag("subtitle_offset_minus_50_button")
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "-50ms")
                                }
                                IconButton(
                                    onClick = { onAdjustOffsetMs(50L) },
                                    modifier = Modifier.testTag("subtitle_offset_plus_50_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "+50ms")
                                }
                                TextButton(
                                    onClick = onResetOffset,
                                    modifier = Modifier.testTag("subtitle_offset_reset_button")
                                ) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                // Item 2: Select All Manually & Playable All Simultaneous Subtitles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onSelectAllSubtitles,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldVault),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subtitle_select_all_button")
                    ) {
                        Text("Select All (Play All)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onClearAllSubtitles,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subtitle_disable_all_button")
                    ) {
                        Text("Disable All", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onLoadExternalSrt, modifier = Modifier.weight(1f)) {
                        Text("Load .SRT/.VTT")
                    }
                    OutlinedButton(onClick = onOpenSubtitleStudio, modifier = Modifier.weight(1f)) {
                        Text("Style Studio")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select Subtitle Tracks (Stacked Playback)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // External Subtitle Tracks
                    if (externalSubtitleTracks.isNotEmpty()) {
                        Text(
                            text = "External Subtitle Files (${externalSubtitleTracks.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        externalSubtitleTracks.forEach { extTrack ->
                            val isChecked = extTrack.id in selectedExternalSubtitleTrackIds
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isChecked) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleExternalTrack(extTrack.id) }
                                    .testTag("external_subtitle_track_option_${extTrack.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { onToggleExternalTrack(extTrack.id) },
                                        modifier = Modifier.testTag("checkbox_external_sub_${extTrack.id}")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${extTrack.label} (${extTrack.cues.size} cues)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Embedded Video Tracks
                    if (subtitleTracks.isNotEmpty()) {
                        Text(
                            text = "Embedded Subtitle Streams (${subtitleTracks.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldVault,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        subtitleTracks.forEach { track ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (track.isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleEmbeddedTrack(track) }
                                    .testTag("embedded_subtitle_track_option_${track.groupIndex}_${track.trackIndex}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = track.isSelected,
                                        onCheckedChange = { onToggleEmbeddedTrack(track) },
                                        modifier = Modifier.testTag("checkbox_embedded_sub_${track.groupIndex}_${track.trackIndex}")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (track.isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Active", tint = ElectricCyan)
                                    }
                                }
                            }
                        }
                    }

                    if (externalSubtitleTracks.isEmpty() && subtitleTracks.isEmpty()) {
                        Text(
                            text = "No subtitle tracks found in container. Tap 'Load .SRT/.VTT' to add external subtitles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
