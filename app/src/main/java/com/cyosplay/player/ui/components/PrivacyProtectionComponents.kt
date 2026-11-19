package com.cyosplay.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyosplay.player.ui.theme.AmberGold
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault

/**
 * Compact status strip showing active Stealth Playback Privacy,
 * AES-256-GCM Zero-Knowledge Encryption status, Low-RAM Shield tier, and Battery Saver Mode.
 */
@Composable
fun PrivacyAndPerformanceStatusStrip(
    stealthPlaybackEnabled: Boolean,
    lowRamBadge: String,
    batterySaverEnabled: Boolean,
    autoKeyRotationCount: Int,
    decoderBadge: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("privacy_performance_status_strip"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (stealthPlaybackEnabled) {
            StatusChip(
                icon = Icons.Default.VisibilityOff,
                label = "STEALTH PRIVACY",
                tint = EmeraldVault
            )
        }
        if (batterySaverEnabled) {
            StatusChip(
                icon = Icons.Default.BatteryChargingFull,
                label = "ULTRA BATTERY SAVER",
                tint = AmberGold
            )
        } else if (lowRamBadge.isNotBlank() &&
            !lowRamBadge.contains("HIGH MEMORY", ignoreCase = true) &&
            !lowRamBadge.contains("STANDARD MEMORY", ignoreCase = true)
        ) {
            StatusChip(
                icon = Icons.Default.Memory,
                label = lowRamBadge,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
