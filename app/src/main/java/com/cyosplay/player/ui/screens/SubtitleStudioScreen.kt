package com.cyosplay.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyosplay.player.data.model.SubtitleColorOption
import com.cyosplay.player.data.model.SubtitleEdgeStyle
import com.cyosplay.player.data.model.SubtitleFontSize
import com.cyosplay.player.data.model.SubtitleVerticalPreset
import com.cyosplay.player.ui.VideoPlayerUiState
import com.cyosplay.player.ui.VideoPlayerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitleStudioScreen(
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.navigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Subtitle Style Studio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Icon-Only Subtitle Style Preview Box (No hardcoded sample text)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(Color(0xFF0B132B), RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = if (uiState.subtitleEdgeStyle == SubtitleEdgeStyle.OUTLINE_BOX) {
                    Color(uiState.subtitleOutlineColor.hexColor).copy(alpha = 0.78f)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = "Subtitle Style Preview",
                    tint = Color(uiState.subtitleColor.hexColor),
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .size((uiState.subtitleFontSize.spValue + 10f).dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Bold Font Size: ${uiState.subtitleCustomSp.toInt()} sp (12sp - 64sp • Extra-Bold Active)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.decreaseSubtitleFontSizeManual() }) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease 2sp")
            }
            Slider(
                value = uiState.subtitleCustomSp.coerceIn(12f, 64f),
                onValueChange = { viewModel.setSubtitleCustomSp(it) },
                valueRange = 12f..64f,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.increaseSubtitleFontSizeManual() }) {
                Icon(Icons.Default.Add, contentDescription = "Increase 2sp")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleFontSize.entries.forEach { size ->
                FilterChip(
                    selected = uiState.subtitleFontSize == size,
                    onClick = { viewModel.setSubtitleFontSize(size) },
                    label = { Text(size.label, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleColorOption.entries.forEach { color ->
                FilterChip(
                    selected = uiState.subtitleColor == color,
                    onClick = { viewModel.setSubtitleColor(color) },
                    label = { Text(color.label, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Edge & Contrast Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleEdgeStyle.entries.forEach { style ->
                FilterChip(
                    selected = uiState.subtitleEdgeStyle == style,
                    onClick = { viewModel.setSubtitleEdgeStyle(style) },
                    label = { Text(style.label, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Vertical Position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleVerticalPreset.entries.forEach { preset ->
                FilterChip(
                    selected = uiState.subtitleVerticalPreset == preset,
                    onClick = { viewModel.setSubtitleVerticalPreset(preset) },
                    label = { Text(preset.label, fontWeight = FontWeight.Bold) }
                )
            }
        }
    }
}
