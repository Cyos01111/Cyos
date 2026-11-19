package com.cyosplay.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyosplay.player.data.model.HoldClipboardOperation
import com.cyosplay.player.data.model.MediaManagerFilter
import com.cyosplay.player.ui.VideoPlayerUiState
import com.cyosplay.player.ui.VideoPlayerViewModel
import com.cyosplay.player.ui.components.HoldSelectionActionBar

@Composable
fun MediaManagerScreen(
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateBack() }

    val filteredVideos = remember(uiState.allVideos, uiState.mediaManagerFilter, uiState.playbackProgressMap) {
        when (uiState.mediaManagerFilter) {
            MediaManagerFilter.ALL -> uiState.allVideos
            MediaManagerFilter.LARGE_FILES -> uiState.allVideos.filter { it.sizeBytes >= 100L * 1024 * 1024 }
            MediaManagerFilter.ULTRA_HD -> uiState.allVideos.filter { maxOf(it.width, it.height) >= 1900 }
            MediaManagerFilter.UNWATCHED -> uiState.allVideos.filter { !uiState.playbackProgressMap.containsKey(it.path) }
            MediaManagerFilter.DUPLICATES -> {
                val counts = uiState.allVideos.groupingBy { it.sizeBytes }.eachCount()
                uiState.allVideos.filter { (counts[it.sizeBytes] ?: 0) > 1 }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("media_manager_screen")
    ) {
        if (uiState.isSelectionModeActive) {
            HoldSelectionActionBar(
                selectedCount = uiState.selectedVideoPaths.size,
                onCopyClick = { viewModel.startCopyOrMoveFlow(HoldClipboardOperation.COPY) },
                onMoveClick = { viewModel.startCopyOrMoveFlow(HoldClipboardOperation.MOVE) },
                onDeleteClick = { viewModel.openDeleteOrRecycleDialog() },
                onMoreOptionsClick = { viewModel.openHoldActionSheetForCurrentSelection() },
                onSelectAllClick = { viewModel.selectAllVisibleVideos(filteredVideos) },
                onClearSelection = { viewModel.clearSelection() }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "Smart Media & Storage Manager",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hold any video to Copy, Move, or Delete",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MediaManagerFilter.entries.forEach { filter ->
                FilterChip(
                    selected = uiState.mediaManagerFilter == filter,
                    onClick = { viewModel.setMediaManagerFilter(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredVideos, key = { it.path }) { video ->
                VideoListItemRow(
                    video = video,
                    isSelected = uiState.selectedVideoPaths.contains(video.path),
                    isSelectionMode = uiState.isSelectionModeActive,
                    isFavorite = uiState.favoriteVideoPaths.contains(video.path),
                    progressFraction = 0f,
                    onClick = {
                        if (uiState.isSelectionModeActive) {
                            viewModel.toggleVideoSelection(video)
                        } else {
                            viewModel.playVideo(video, filteredVideos)
                        }
                    },
                    onLongPress = { viewModel.onHoldVideoItem(video) },
                    onFavoriteToggle = { viewModel.toggleFavoriteVideo(video.path) }
                )
            }
        }
    }
}
