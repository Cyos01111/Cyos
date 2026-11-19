package com.cyosplay.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyosplay.player.R
import com.cyosplay.player.data.model.BrowserLayoutColumns
import com.cyosplay.player.data.model.BrowserThumbnailRatio
import com.cyosplay.player.data.model.BrowserViewMode
import com.cyosplay.player.data.model.BrowserVisibleFields
import com.cyosplay.player.data.model.FolderExplorerMode
import com.cyosplay.player.data.model.FolderSortOption
import com.cyosplay.player.data.model.HoldClipboardOperation
import com.cyosplay.player.data.model.NavDestination
import com.cyosplay.player.data.model.StorageVolumeFilter
import com.cyosplay.player.data.model.SortDirection
import com.cyosplay.player.data.model.VideoFolder
import com.cyosplay.player.data.model.VideoItem
import com.cyosplay.player.data.model.VideoSortOption
import com.cyosplay.player.ui.Screen
import com.cyosplay.player.ui.VideoPlayerUiState
import com.cyosplay.player.ui.VideoPlayerViewModel
import com.cyosplay.player.ui.components.BrowserDisplaySettingsDialog
import com.cyosplay.player.ui.components.BrowserFiveControlChipsBar
import com.cyosplay.player.ui.components.ClipboardPasteBanner
import com.cyosplay.player.ui.components.ContinueWatchingResumeBanner
import com.cyosplay.player.ui.components.FfmpegCodecRegistryInspectorDialog
import com.cyosplay.player.ui.components.FolderVideoCollagePreview
import com.cyosplay.player.ui.components.HoldSelectionActionBar
import com.cyosplay.player.ui.components.LargeFileTransferProgressBanner
import com.cyosplay.player.ui.components.PrivacyAndPerformanceStatusStrip
import com.cyosplay.player.ui.components.SinglePlaceInspectorTextCard
import com.cyosplay.player.ui.components.TapToSeeSinglePlaceTextBox
import com.cyosplay.player.ui.components.VideoItemPictureThumbnail
import com.cyosplay.player.ui.theme.AmberGold
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault
import com.cyosplay.player.util.Formatters

import com.cyosplay.player.data.repository.VideoRepository
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

@Composable
fun FolderListScreen(
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val searchFocusRequester = remember { FocusRequester() }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTopMoreMenu by remember { mutableStateOf(false) }

    val foldersListState = rememberLazyListState(
        initialFirstVisibleItemIndex = uiState.foldersListFirstVisibleIndex,
        initialFirstVisibleItemScrollOffset = uiState.foldersListScrollOffset
    )
    val foldersGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = uiState.foldersGridFirstVisibleIndex,
        initialFirstVisibleItemScrollOffset = uiState.foldersGridScrollOffset
    )
    val videosListState = rememberLazyListState(
        initialFirstVisibleItemIndex = uiState.videosListFirstVisibleIndex,
        initialFirstVisibleItemScrollOffset = uiState.videosListScrollOffset
    )
    val videosGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = uiState.videosGridFirstVisibleIndex,
        initialFirstVisibleItemScrollOffset = uiState.videosGridScrollOffset
    )

    DisposableEffect(foldersListState, foldersGridState, videosListState, videosGridState) {
        onDispose {
            viewModel.saveFoldersScrollPosition(
                listIndex = foldersListState.firstVisibleItemIndex,
                listOffset = foldersListState.firstVisibleItemScrollOffset,
                gridIndex = foldersGridState.firstVisibleItemIndex,
                gridOffset = foldersGridState.firstVisibleItemScrollOffset
            )
            viewModel.saveVideosScrollPosition(
                listIndex = videosListState.firstVisibleItemIndex,
                listOffset = videosListState.firstVisibleItemScrollOffset,
                gridIndex = videosGridState.firstVisibleItemIndex,
                gridOffset = videosGridState.firstVisibleItemScrollOffset
            )
        }
    }

    val openLocalVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importAndPlayLocalVideoUri(uri, saveFolderInApp = false)
        }
    }

    val filteredVideos = remember(
        uiState.allVideos,
        uiState.searchQuery,
        uiState.storageVolumeFilter,
        uiState.advancedCodecFilter,
        uiState.resolutionFilterTier,
        uiState.videoSortOption,
        uiState.videoSortDirection
    ) {
        val q = uiState.searchQuery.trim()
        val matching = uiState.allVideos.filter { video ->
            val isStrictVideo = VideoRepository.isStrictVideoFile(
                video.displayName.ifBlank { video.path },
                video.mimeType
            )
            val matchesQuery = q.isEmpty() ||
                video.title.contains(q, ignoreCase = true) ||
                video.displayName.contains(q, ignoreCase = true) ||
                video.folderName.contains(q, ignoreCase = true) ||
                video.formatBadge.contains(q, ignoreCase = true) ||
                video.resolutionBadge.contains(q, ignoreCase = true) ||
                video.videoCodecHint.contains(q, ignoreCase = true) ||
                video.audioCodecHint.contains(q, ignoreCase = true) ||
                video.matroskaEbmlBadge.contains(q, ignoreCase = true)
            val matchesStorage = when (uiState.storageVolumeFilter) {
                StorageVolumeFilter.ALL_STORAGE -> true
                StorageVolumeFilter.INTERNAL_ONLY -> !video.isRemovableStorage
                StorageVolumeFilter.SD_CARD_ONLY -> video.isRemovableStorage
            }
            val matchesAdvanced = viewModel.matchesAdvancedVideoFilters(video)
            isStrictVideo && matchesQuery && matchesStorage && matchesAdvanced
        }
        viewModel.sortVideos(matching, uiState.videoSortOption, uiState.videoSortDirection)
    }

    val filteredFolders = remember(
        uiState.folders,
        uiState.searchQuery,
        uiState.storageVolumeFilter,
        uiState.advancedCodecFilter,
        uiState.resolutionFilterTier,
        uiState.folderSortOption,
        uiState.folderSortDirection
    ) {
        val q = uiState.searchQuery.trim()
        val matchingFolders = uiState.folders
            .mapNotNull { folder ->
                val strictVideosInFolder = folder.videos.filter { video ->
                    VideoRepository.isStrictVideoFile(
                        video.displayName.ifBlank { video.path },
                        video.mimeType
                    ) && viewModel.matchesAdvancedVideoFilters(video)
                }
                if (strictVideosInFolder.isEmpty()) null else folder.copy(videos = strictVideosInFolder)
            }
            .filter { folder ->
                val matchesQuery = q.isEmpty() ||
                    folder.name.contains(q, ignoreCase = true) ||
                    folder.path.contains(q, ignoreCase = true) ||
                    folder.videos.any { v ->
                        v.title.contains(q, ignoreCase = true) ||
                            v.displayName.contains(q, ignoreCase = true) ||
                            v.formatBadge.contains(q, ignoreCase = true) ||
                            v.videoCodecHint.contains(q, ignoreCase = true) ||
                            v.matroskaEbmlBadge.contains(q, ignoreCase = true)
                    }
                val matchesStorage = when (uiState.storageVolumeFilter) {
                    StorageVolumeFilter.ALL_STORAGE -> true
                    StorageVolumeFilter.INTERNAL_ONLY -> !folder.isRemovableStorage
                    StorageVolumeFilter.SD_CARD_ONLY -> folder.isRemovableStorage
                }
                folder.videoCount > 0 && matchesQuery && matchesStorage
            }
        viewModel.sortFolders(matchingFolders, uiState.folderSortOption, uiState.folderSortDirection)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Contextual Hold / Multi-Select Bar (when user holds or taps Select for multiple files or folders)
        if (uiState.isSelectionModeActive) {
            HoldSelectionActionBar(
                selectedCount = uiState.selectedFolderPaths.size + uiState.selectedVideoPaths.size,
                selectionLabel = uiState.selectionSummaryLabel,
                onCopyClick = { viewModel.startCopyOrMoveFlow(HoldClipboardOperation.COPY) },
                onMoveClick = { viewModel.startCopyOrMoveFlow(HoldClipboardOperation.MOVE) },
                onDeleteClick = { viewModel.openDeleteOrRecycleDialog() },
                onMoreOptionsClick = { viewModel.openHoldActionSheetForCurrentSelection() },
                onSelectAllClick = {
                    if (uiState.currentNavTab == NavDestination.FOLDERS) {
                        viewModel.selectAllVisibleFoldersAndVideos(filteredFolders, filteredVideos)
                    } else {
                        viewModel.selectAllVisibleVideos(filteredVideos)
                    }
                },
                onClearSelection = { viewModel.clearSelection() }
            )
        } else {
            // Standard Top App Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cyosplay_logo),
                        contentDescription = "CyosPlay Logo",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CyosPlay",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = ElectricCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "v${uiState.appVersionName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "CyosPlay 1.07rv6",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Item 2: Set front header icons in one place inside a compact popup when pressed
                val isCurrentlyGrid = if (uiState.currentNavTab == NavDestination.FOLDERS) {
                    uiState.folderViewMode == BrowserViewMode.GRID
                } else {
                    uiState.videoViewMode == BrowserViewMode.GRID
                }
                Box {
                    Surface(
                        color = ElectricCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showTopMoreMenu = true }
                            .testTag("top_more_options_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Open tools popup",
                                tint = ElectricCyan
                            )
                            Text(
                                text = "Tools",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showTopMoreMenu,
                        onDismissRequest = {
                            showTopMoreMenu = false
                            showSortMenu = false
                        }
                    ) {
                        // Compact quick-icons grid inside the popup so all front icons live in one place
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        showTopMoreMenu = false
                                        viewModel.refreshLibrary()
                                    },
                                    modifier = Modifier.testTag("home_rescan_videos_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh and rescan videos",
                                        tint = ElectricCyan
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showTopMoreMenu = false
                                        viewModel.toggleBrowserSelectionMode()
                                    },
                                    modifier = Modifier.testTag("top_multi_select_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = "Select multiple files or folders",
                                        tint = ElectricCyan
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showTopMoreMenu = false
                                        openLocalVideoLauncher.launch(VideoRepository.OPEN_VIDEO_DOCUMENT_MIME_TYPES)
                                    },
                                    modifier = Modifier.testTag("open_local_video_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Open Local Video File",
                                        tint = ElectricCyan
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.toggleNightMode() },
                                    modifier = Modifier.testTag("top_night_mode_toggle")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DarkMode,
                                        contentDescription = "Toggle Unified Night Mode",
                                        tint = if (uiState.nightModeEnabled) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.setBatterySaverModeEnabled(!uiState.batterySaverModeEnabled) },
                                    modifier = Modifier.testTag("top_battery_saver_toggle")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryChargingFull,
                                        contentDescription = "Toggle Battery Saver Mode",
                                        tint = if (uiState.batterySaverModeEnabled) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        showTopMoreMenu = false
                                        try {
                                            searchFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.testTag("toggle_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search media"
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.toggleGridView() },
                                    modifier = Modifier.testTag("toggle_grid_view_button")
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentlyGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                        contentDescription = "Toggle Grid or List view"
                                    )
                                }
                                IconButton(
                                    onClick = { showSortMenu = !showSortMenu },
                                    modifier = Modifier.testTag("sort_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Sort options",
                                        tint = if (showSortMenu) ElectricCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showTopMoreMenu = false
                                        viewModel.navigateTo(Screen.MediaManager)
                                    },
                                    modifier = Modifier.testTag("open_media_manager_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CleaningServices,
                                        contentDescription = "Media Manager"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showTopMoreMenu = false
                                        viewModel.navigateTo(Screen.RecycleBin)
                                    },
                                    modifier = Modifier.testTag("open_recycle_bin_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Recycle Bin"
                                    )
                                }
                            }
                        }

                        if (showSortMenu) {
                            if (uiState.currentNavTab == NavDestination.VIDEOS) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.videoSortDirection == SortDirection.ASCENDING) {
                                                "Direction: Ascending ↑ (Tap for ↓)"
                                            } else {
                                                "Direction: Descending ↓ (Tap for ↑)"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            color = ElectricCyan
                                        )
                                    },
                                    onClick = {
                                        val nextDir = if (uiState.videoSortDirection == SortDirection.ASCENDING) {
                                            SortDirection.DESCENDING
                                        } else {
                                            SortDirection.ASCENDING
                                        }
                                        viewModel.setVideoSortDirection(nextDir)
                                        showSortMenu = false
                                        showTopMoreMenu = false
                                    }
                                )
                                VideoSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("Sort: ${option.label}") },
                                        onClick = {
                                            viewModel.setVideoSortOption(option)
                                            showSortMenu = false
                                            showTopMoreMenu = false
                                        }
                                    )
                                }
                            } else {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.folderSortDirection == SortDirection.ASCENDING) {
                                                "Direction: Ascending ↑ (Tap for ↓)"
                                            } else {
                                                "Direction: Descending ↓ (Tap for ↑)"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            color = ElectricCyan
                                        )
                                    },
                                    onClick = {
                                        val nextDir = if (uiState.folderSortDirection == SortDirection.ASCENDING) {
                                            SortDirection.DESCENDING
                                        } else {
                                            SortDirection.ASCENDING
                                        }
                                        viewModel.setFolderSortDirection(nextDir)
                                        showSortMenu = false
                                        showTopMoreMenu = false
                                    }
                                )
                                FolderSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("Sort: ${option.label}") },
                                        onClick = {
                                            viewModel.setFolderSortOption(option)
                                            showSortMenu = false
                                            showTopMoreMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val topFoldersSelected = uiState.currentNavTab == NavDestination.FOLDERS
                            FilterChip(
                                selected = topFoldersSelected,
                                onClick = {
                                    viewModel.selectNavTab(NavDestination.FOLDERS)
                                    showTopMoreMenu = false
                                },
                                label = { Text("Folders (${filteredFolders.size})") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Folders",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            val topVideosSelected = uiState.currentNavTab == NavDestination.VIDEOS
                            FilterChip(
                                selected = topVideosSelected,
                                onClick = {
                                    viewModel.selectNavTab(NavDestination.VIDEOS)
                                    showTopMoreMenu = false
                                },
                                label = { Text("Videos (${filteredVideos.size})") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.VideoFile,
                                        contentDescription = "Videos",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (filteredVideos.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Play All Videos") },
                                onClick = {
                                    showTopMoreMenu = false
                                    viewModel.playAllInFolder("global_all_videos", filteredVideos)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rescan Storage") },
                            onClick = {
                                showTopMoreMenu = false
                                viewModel.refreshLibrary()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select Multiple") },
                            onClick = {
                                showTopMoreMenu = false
                                viewModel.toggleBrowserSelectionMode()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open Video File") },
                            onClick = {
                                showTopMoreMenu = false
                                openLocalVideoLauncher.launch(VideoRepository.OPEN_VIDEO_DOCUMENT_MIME_TYPES)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View Mode & Sort Controls") },
                            onClick = {
                                showTopMoreMenu = false
                                viewModel.openBrowserControlTab(com.cyosplay.player.data.model.BrowserControlTab.VIEW_MODE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Media Manager") },
                            onClick = {
                                showTopMoreMenu = false
                                viewModel.navigateTo(Screen.MediaManager)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Recycle Bin") },
                            onClick = {
                                showTopMoreMenu = false
                                viewModel.navigateTo(Screen.RecycleBin)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showTopMoreMenu = false
                                viewModel.selectNavTab(NavDestination.SETTINGS)
                            }
                        )
                    }
                }
            }
        }

        // Always-visible Front Search Bar at top of home screen (Item 11: "Search videos" only)
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("Search videos") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search videos and folders",
                    tint = ElectricCyan
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.updateSearchQuery("") },
                        modifier = Modifier.testTag("clear_search_query_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .focusRequester(searchFocusRequester)
                .testTag("media_search_input")
        )

        // Unified Front Screen Header Popup — moves Folders & Videos tabs inside the single front header popup
        // alongside Storage (All / Internal / SD Card), Folder Explorer Hierarchy, Play All, Status Badges, and 5 Browser Controls.
        var showMiddleHeaderPopup by remember { mutableStateOf(false) }
        val isFolderTab = uiState.currentNavTab == NavDestination.FOLDERS
        val activeSortArrow = if (isFolderTab) {
            if (uiState.folderSortDirection == SortDirection.ASCENDING) "↑" else "↓"
        } else {
            if (uiState.videoSortDirection == SortDirection.ASCENDING) "↑" else "↓"
        }
        val activeTabBadge = if (isFolderTab) {
            "Folders (${filteredFolders.size})"
        } else {
            "Videos (${filteredVideos.size})"
        }
        val activeViewLabel = if (isFolderTab) uiState.folderViewMode.shortLabel else uiState.videoViewMode.shortLabel
        val activeSortLabel = "${if (isFolderTab) uiState.folderSortOption.label else uiState.videoSortOption.label} $activeSortArrow"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showMiddleHeaderPopup = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isFolderTab) Icons.Default.Folder else Icons.Default.VideoFile,
                            contentDescription = activeTabBadge,
                            tint = ElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "$activeTabBadge • ${uiState.storageVolumeFilter.label} • $activeViewLabel • $activeSortLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                Surface(
                    color = ElectricCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showMiddleHeaderPopup = true }
                        .testTag("middle_screen_controls_popup_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Open unified front header popup",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Menu",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMiddleHeaderPopup,
                    onDismissRequest = { showMiddleHeaderPopup = false }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Browse Library (Folders / Videos)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upper_folders_videos_selector"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isFoldersSelected = uiState.currentNavTab == NavDestination.FOLDERS
                            Surface(
                                color = if (isFoldersSelected) ElectricCyan else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.selectNavTab(NavDestination.FOLDERS)
                                        showMiddleHeaderPopup = false
                                    }
                                    .testTag("upper_tab_folders")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Folders",
                                        tint = if (isFoldersSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Folders (${filteredFolders.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFoldersSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val isVideosSelected = uiState.currentNavTab == NavDestination.VIDEOS
                            Surface(
                                color = if (isVideosSelected) ElectricCyan else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.selectNavTab(NavDestination.VIDEOS)
                                        showMiddleHeaderPopup = false
                                    }
                                    .testTag("upper_tab_videos")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VideoFile,
                                        contentDescription = "Videos",
                                        tint = if (isVideosSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Videos (${filteredVideos.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isVideosSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Storage, Explorer & Quick Play",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StorageVolumeFilter.entries.forEach { filter ->
                                val isSelected = uiState.storageVolumeFilter == filter
                                Surface(
                                    color = if (isSelected) {
                                        ElectricCyan.copy(alpha = 0.20f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.setStorageVolumeFilter(filter)
                                            showMiddleHeaderPopup = false
                                        }
                                        .testTag("storage_filter_${filter.name}")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (filter) {
                                                StorageVolumeFilter.ALL_STORAGE -> Icons.Default.Folder
                                                StorageVolumeFilter.INTERNAL_ONLY -> Icons.Default.Storage
                                                StorageVolumeFilter.SD_CARD_ONLY -> Icons.Default.SdStorage
                                            },
                                            contentDescription = filter.label,
                                            tint = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (isFolderTab) {
                                FolderExplorerMode.entries.forEach { mode ->
                                    val isSelected = uiState.folderExplorerMode == mode
                                    Surface(
                                        color = if (isSelected) {
                                            AmberGold.copy(alpha = 0.20f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                viewModel.setFolderExplorerMode(mode)
                                                showMiddleHeaderPopup = false
                                            }
                                            .testTag("explorer_mode_${mode.name}")
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (mode == FolderExplorerMode.FLAT_FOLDERS) {
                                                    Icons.Default.GridView
                                                } else {
                                                    Icons.Default.FolderOpen
                                                },
                                                contentDescription = mode.label,
                                                tint = if (isSelected) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (filteredVideos.isNotEmpty()) {
                                Surface(
                                    color = EmeraldVault.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            showMiddleHeaderPopup = false
                                            viewModel.saveVideosScrollPosition(
                                                listIndex = videosListState.firstVisibleItemIndex,
                                                listOffset = videosListState.firstVisibleItemScrollOffset,
                                                gridIndex = videosGridState.firstVisibleItemIndex,
                                                gridOffset = videosGridState.firstVisibleItemScrollOffset
                                            )
                                            viewModel.playAllInFolder("global_all_videos", filteredVideos)
                                        }
                                        .testTag("play_all_videos_button")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play All",
                                            tint = EmeraldVault,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "View, Sort, Layout & Fields",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                        BrowserFiveControlChipsBar(
                            currentViewMode = if (isFolderTab) uiState.folderViewMode else uiState.videoViewMode,
                            sortSummaryLabel = activeSortLabel,
                            layoutColumns = uiState.browserLayoutColumns,
                            advancedCodecFilter = uiState.advancedCodecFilter,
                            visibleFields = uiState.visibleFields,
                            onOpenControlTab = { tab ->
                                showMiddleHeaderPopup = false
                                viewModel.openBrowserControlTab(tab)
                            },
                            onQuickCycleViewMode = {
                                if (isFolderTab) viewModel.cycleFolderViewMode() else viewModel.cycleVideoViewMode()
                            },
                            hideAllTextsEnabled = uiState.hideAllTextsEnabled,
                            onToggleHideAllTexts = {
                                viewModel.toggleHideAllTexts()
                            }
                        )

                        PrivacyAndPerformanceStatusStrip(
                            stealthPlaybackEnabled = uiState.stealthPlaybackEnabled,
                            lowRamBadge = uiState.memoryBudget?.tier?.badgeLabel ?: "LOW-RAM SHIELD",
                            batterySaverEnabled = uiState.batterySaverModeEnabled,
                            autoKeyRotationCount = uiState.autoKeyRotationCount,
                            decoderBadge = uiState.activeDecoderBadge,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }

        // Single-Place Text Inspector Card when user taps an individual item while Hide All Texts is active
        val inspectorTitle = uiState.tappedInspectorTextTitle
        val inspectorBody = uiState.tappedInspectorTextBody
        if (inspectorTitle != null && inspectorBody != null) {
            SinglePlaceInspectorTextCard(
                title = inspectorTitle,
                fullBodyText = inspectorBody,
                onDismiss = { viewModel.clearSinglePlaceTextInspector() }
            )
        }

        // Clipboard Paste Banner if user staged Copy/Move
        uiState.clipboardState?.let { clip ->
            val targetPath = uiState.currentDirectoryNode?.currentPath ?: "/storage/emulated/0/Movies"
            val targetName = uiState.currentDirectoryNode?.displayName ?: "Movies"
            ClipboardPasteBanner(
                clipboardState = clip,
                destinationFolderName = targetName,
                onPasteHere = {
                    viewModel.requestPasteClipboardConfirmation(
                        targetFolderPath = targetPath,
                        targetFolderName = targetName,
                        isRemovableSd = targetPath.contains("sdcard1")
                    )
                },
                onCancel = { viewModel.clearClipboard() }
            )
        }

        // Live 1000GB+ File Move/Copy Progress Banner
        uiState.fileTransferProgress?.let { progress ->
            LargeFileTransferProgressBanner(
                progress = progress,
                onDismiss = { viewModel.dismissFileTransferProgress() }
            )
        }

        val gridCells = remember(uiState.browserLayoutColumns, uiState.videoViewMode, uiState.folderViewMode, isFolderTab) {
            val activeViewMode = if (isFolderTab) uiState.folderViewMode else uiState.videoViewMode
            if (activeViewMode == BrowserViewMode.GALLERY_CARDS && uiState.browserLayoutColumns.columnsCount == 0) {
                GridCells.Fixed(2)
            } else {
                when (uiState.browserLayoutColumns.columnsCount) {
                    0 -> GridCells.Adaptive(minSize = 165.dp)
                    1 -> GridCells.Fixed(1)
                    2 -> GridCells.Fixed(2)
                    3 -> GridCells.Fixed(3)
                    else -> GridCells.Fixed(4)
                }
            }
        }

        // Main Content Area
        if (uiState.currentNavTab == NavDestination.VIDEOS) {
            if (filteredVideos.isEmpty()) {
                EmptyMediaLibraryState(
                    onRescanClick = { viewModel.refreshLibrary() },
                    onOpenVideoFileClick = { openLocalVideoLauncher.launch(VideoRepository.OPEN_VIDEO_DOCUMENT_MIME_TYPES) }
                )
            } else if (uiState.videoViewMode == BrowserViewMode.GRID || uiState.videoViewMode == BrowserViewMode.GALLERY_CARDS) {
                LazyVerticalGrid(
                    state = videosGridState,
                    columns = gridCells,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("videos_grid_container")
                ) {
                    items(filteredVideos, key = { it.path }) { video ->
                        val savedProgress = uiState.playbackProgressMap[video.path]
                        val videoItemId = "video_${video.id}"
                        val fullVideoText = remember(video, savedProgress) {
                            buildString {
                                append("${video.displayName} • ${video.formatBadge} (${video.containerFormatDetail})")
                                append(" • ${video.resolutionBadge} (${video.exactResolutionLabel}, ${video.aspectRatioLabel})")
                                append(" • Video: ${video.videoCodecHint} • Audio: ${video.audioCodecHint}")
                                append(" • ${video.estimatedBitrateLabel} • ${Formatters.formatFileSize(video.sizeBytes)}")
                                append(" • Duration: ${Formatters.formatDuration(video.durationMs)}")
                                if (savedProgress != null && savedProgress.lastPositionMs > 1_000L) {
                                    append(" • Saved Resume Position: ${Formatters.formatDuration(savedProgress.lastPositionMs)}")
                                }
                                append(" • Path: ${video.path}")
                            }
                        }
                        VideoGridItemCard(
                            video = video,
                            isSelected = uiState.selectedVideoPaths.contains(video.path),
                            isSelectionMode = uiState.isSelectionModeActive,
                            isFavorite = uiState.favoriteVideoPaths.contains(video.path),
                            progressFraction = savedProgress?.fraction ?: 0f,
                            resumePositionMs = savedProgress?.lastPositionMs ?: 0L,
                            thumbnailRatio = uiState.browserThumbnailRatio,
                            visibleFields = uiState.visibleFields,
                            hideAllTexts = uiState.hideAllTextsEnabled,
                            isTextRevealed = uiState.revealedTextItemIds.contains(videoItemId),
                            onTapRevealText = {
                                viewModel.toggleRevealItemText(
                                    itemId = videoItemId,
                                    title = video.title,
                                    fullDetailsText = fullVideoText
                                )
                            },
                            onClick = {
                                if (uiState.isSelectionModeActive) {
                                    viewModel.toggleVideoSelection(video)
                                } else {
                                    viewModel.saveVideosScrollPosition(
                                        listIndex = videosListState.firstVisibleItemIndex,
                                        listOffset = videosListState.firstVisibleItemScrollOffset,
                                        gridIndex = videosGridState.firstVisibleItemIndex,
                                        gridOffset = videosGridState.firstVisibleItemScrollOffset
                                    )
                                    viewModel.playVideo(video, filteredVideos)
                                }
                            },
                            onLongPress = { viewModel.onHoldVideoItem(video) },
                            onFavoriteToggle = { viewModel.toggleFavoriteVideo(video.path) },
                            onInfoClick = { viewModel.inspectVideoProperties(video) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = videosListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (uiState.videoViewMode == BrowserViewMode.COMPACT) 6.dp else 10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("videos_list_container")
                ) {
                    items(filteredVideos, key = { it.path }) { video ->
                        val savedProgress = uiState.playbackProgressMap[video.path]
                        val videoItemId = "video_${video.id}"
                        val fullVideoText = remember(video, savedProgress) {
                            buildString {
                                append("${video.displayName} • ${video.formatBadge} (${video.containerFormatDetail})")
                                append(" • ${video.resolutionBadge} (${video.exactResolutionLabel}, ${video.aspectRatioLabel})")
                                append(" • Video: ${video.videoCodecHint} • Audio: ${video.audioCodecHint}")
                                append(" • ${video.estimatedBitrateLabel} • ${Formatters.formatFileSize(video.sizeBytes)}")
                                append(" • Duration: ${Formatters.formatDuration(video.durationMs)}")
                                if (savedProgress != null && savedProgress.lastPositionMs > 1_000L) {
                                    append(" • Saved Resume Position: ${Formatters.formatDuration(savedProgress.lastPositionMs)}")
                                }
                                append(" • Path: ${video.path}")
                            }
                        }
                        VideoListItemRow(
                            video = video,
                            isSelected = uiState.selectedVideoPaths.contains(video.path),
                            isSelectionMode = uiState.isSelectionModeActive,
                            isFavorite = uiState.favoriteVideoPaths.contains(video.path),
                            progressFraction = savedProgress?.fraction ?: 0f,
                            resumePositionMs = savedProgress?.lastPositionMs ?: 0L,
                            viewMode = uiState.videoViewMode,
                            thumbnailRatio = uiState.browserThumbnailRatio,
                            visibleFields = uiState.visibleFields,
                            hideAllTexts = uiState.hideAllTextsEnabled,
                            isTextRevealed = uiState.revealedTextItemIds.contains(videoItemId),
                            onTapRevealText = {
                                viewModel.toggleRevealItemText(
                                    itemId = videoItemId,
                                    title = video.title,
                                    fullDetailsText = fullVideoText
                                )
                            },
                            onClick = {
                                if (uiState.isSelectionModeActive) {
                                    viewModel.toggleVideoSelection(video)
                                } else {
                                    viewModel.saveVideosScrollPosition(
                                        listIndex = videosListState.firstVisibleItemIndex,
                                        listOffset = videosListState.firstVisibleItemScrollOffset,
                                        gridIndex = videosGridState.firstVisibleItemIndex,
                                        gridOffset = videosGridState.firstVisibleItemScrollOffset
                                    )
                                    viewModel.playVideo(video, filteredVideos)
                                }
                            },
                            onLongPress = { viewModel.onHoldVideoItem(video) },
                            onFavoriteToggle = { viewModel.toggleFavoriteVideo(video.path) },
                            onInfoClick = { viewModel.inspectVideoProperties(video) }
                        )
                    }
                }
            }
        } else {
            // FOLDERS Tab (shows only folders containing videos + instant video search results when searching)
            if (uiState.folderExplorerMode == FolderExplorerMode.FLAT_FOLDERS) {
                val isSearching = uiState.searchQuery.isNotBlank()
                if (filteredFolders.isEmpty() && (!isSearching || filteredVideos.isEmpty())) {
                    EmptyMediaLibraryState(
                        onRescanClick = { viewModel.refreshLibrary() },
                        onOpenVideoFileClick = { openLocalVideoLauncher.launch(VideoRepository.OPEN_VIDEO_DOCUMENT_MIME_TYPES) }
                    )
                } else if ((uiState.folderViewMode == BrowserViewMode.GRID || uiState.folderViewMode == BrowserViewMode.GALLERY_CARDS) && !isSearching) {
                    LazyVerticalGrid(
                        state = foldersGridState,
                        columns = gridCells,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("folders_grid_container")
                    ) {
                        items(filteredFolders, key = { "folder_grid_${it.path}" }) { folder ->
                            val normalizedFolderPath = folder.path.trim().trimEnd('/')
                            val isFolderSelected = uiState.selectedFolderPaths.contains(normalizedFolderPath)
                            val folderItemId = "folder_${folder.id}"
                            val fullFolderText = remember(folder) {
                                "${folder.videoCount} video(s) • Total Size: ${Formatters.formatFileSize(folder.totalSizeBytes)} • Full Path: ${folder.path}"
                            }
                            FolderGridCard(
                                folder = folder,
                                isSelected = isFolderSelected,
                                isSelectionMode = uiState.isSelectionModeActive,
                                visibleFields = uiState.visibleFields,
                                hideAllTexts = uiState.hideAllTextsEnabled,
                                isTextRevealed = uiState.revealedTextItemIds.contains(folderItemId),
                                onTapRevealText = {
                                    viewModel.toggleRevealItemText(
                                        itemId = folderItemId,
                                        title = folder.name,
                                        fullDetailsText = fullFolderText
                                    )
                                },
                                onClick = {
                                    if (uiState.isSelectionModeActive) {
                                        viewModel.toggleFolderSelection(folder)
                                    } else {
                                        viewModel.saveFoldersScrollPosition(
                                            listIndex = foldersListState.firstVisibleItemIndex,
                                            listOffset = foldersListState.firstVisibleItemScrollOffset,
                                            gridIndex = foldersGridState.firstVisibleItemIndex,
                                            gridOffset = foldersGridState.firstVisibleItemScrollOffset
                                        )
                                        viewModel.openFolderDetail(folder)
                                    }
                                },
                                onLongPress = { viewModel.onHoldFolderItem(folder) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = foldersListState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (uiState.folderViewMode == BrowserViewMode.COMPACT) 6.dp else 10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("folders_list_container")
                    ) {
                        items(filteredFolders, key = { "folder_${it.path}" }) { folder ->
                            val clip = uiState.clipboardState
                            val normalizedFolderPath = folder.path.trim().trimEnd('/')
                            val isFolderSelected = uiState.selectedFolderPaths.contains(normalizedFolderPath)
                            val folderItemId = "folder_${folder.id}"
                            val fullFolderText = remember(folder) {
                                "${folder.videoCount} video(s) • Total Size: ${Formatters.formatFileSize(folder.totalSizeBytes)} • Full Path: ${folder.path}"
                            }
                            FolderCardRow(
                                folder = folder,
                                isSelected = isFolderSelected,
                                isSelectionMode = uiState.isSelectionModeActive,
                                viewMode = uiState.folderViewMode,
                                visibleFields = uiState.visibleFields,
                                hideAllTexts = uiState.hideAllTextsEnabled,
                                isTextRevealed = uiState.revealedTextItemIds.contains(folderItemId),
                                onTapRevealText = {
                                    viewModel.toggleRevealItemText(
                                        itemId = folderItemId,
                                        title = folder.name,
                                        fullDetailsText = fullFolderText
                                    )
                                },
                                onClick = {
                                    if (uiState.isSelectionModeActive) {
                                        viewModel.toggleFolderSelection(folder)
                                    } else {
                                        viewModel.saveFoldersScrollPosition(
                                            listIndex = foldersListState.firstVisibleItemIndex,
                                            listOffset = foldersListState.firstVisibleItemScrollOffset,
                                            gridIndex = foldersGridState.firstVisibleItemIndex,
                                            gridOffset = foldersGridState.firstVisibleItemScrollOffset
                                        )
                                        viewModel.openFolderDetail(folder)
                                    }
                                },
                                onLongPress = { viewModel.onHoldFolderItem(folder) },
                                showPasteHereAction = clip != null && !uiState.isSelectionModeActive,
                                isPasteMoveOperation = clip?.operation != HoldClipboardOperation.COPY,
                                onPasteHereClick = {
                                    viewModel.requestPasteClipboardConfirmation(
                                        targetFolderPath = folder.path,
                                        targetFolderName = folder.name,
                                        isRemovableSd = folder.isRemovableStorage
                                    )
                                }
                            )
                        }

                        if (isSearching && filteredVideos.isNotEmpty()) {
                            item(key = "search_matching_videos_header") {
                                Text(
                                    text = "Matching Videos (${filteredVideos.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan,
                                    modifier = Modifier
                                        .padding(top = 6.dp, bottom = 2.dp)
                                        .testTag("front_search_matching_videos_header")
                                )
                            }
                            items(filteredVideos, key = { "search_video_${it.path}" }) { video ->
                                val savedProgress = uiState.playbackProgressMap[video.path]
                                val videoItemId = "video_${video.id}"
                                val fullVideoText = remember(video, savedProgress) {
                                    buildString {
                                        append("${video.displayName} • ${video.formatBadge} (${video.containerFormatDetail})")
                                        append(" • ${video.resolutionBadge} (${video.exactResolutionLabel}, ${video.aspectRatioLabel})")
                                        append(" • Video: ${video.videoCodecHint} • Audio: ${video.audioCodecHint}")
                                        append(" • ${video.estimatedBitrateLabel} • ${Formatters.formatFileSize(video.sizeBytes)}")
                                        append(" • Duration: ${Formatters.formatDuration(video.durationMs)}")
                                        if (savedProgress != null && savedProgress.lastPositionMs > 1_000L) {
                                            append(" • Saved Resume Position: ${Formatters.formatDuration(savedProgress.lastPositionMs)}")
                                        }
                                        append(" • Path: ${video.path}")
                                    }
                                }
                                VideoListItemRow(
                                    video = video,
                                    isSelected = uiState.selectedVideoPaths.contains(video.path),
                                    isSelectionMode = uiState.isSelectionModeActive,
                                    isFavorite = uiState.favoriteVideoPaths.contains(video.path),
                                    progressFraction = savedProgress?.fraction ?: 0f,
                                    resumePositionMs = savedProgress?.lastPositionMs ?: 0L,
                                    viewMode = uiState.videoViewMode,
                                    thumbnailRatio = uiState.browserThumbnailRatio,
                                    visibleFields = uiState.visibleFields,
                                    hideAllTexts = uiState.hideAllTextsEnabled,
                                    isTextRevealed = uiState.revealedTextItemIds.contains(videoItemId),
                                    onTapRevealText = {
                                        viewModel.toggleRevealItemText(
                                            itemId = videoItemId,
                                            title = video.title,
                                            fullDetailsText = fullVideoText
                                        )
                                    },
                                    onClick = {
                                        if (uiState.isSelectionModeActive) {
                                            viewModel.toggleVideoSelection(video)
                                        } else {
                                            viewModel.playVideo(video, filteredVideos)
                                        }
                                    },
                                    onLongPress = { viewModel.onHoldVideoItem(video) },
                                    onFavoriteToggle = { viewModel.toggleFavoriteVideo(video.path) },
                                    onInfoClick = { viewModel.inspectVideoProperties(video) }
                                )
                            }
                        }
                    }
                }
            } else {
                StorageHierarchyTreeSection(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }

    uiState.activeBrowserControlSheetTab?.let { activeTab ->
        BrowserDisplaySettingsDialog(
            initialTab = activeTab,
            isFoldersContext = uiState.currentNavTab == NavDestination.FOLDERS,
            folderViewMode = uiState.folderViewMode,
            videoViewMode = uiState.videoViewMode,
            folderSortOption = uiState.folderSortOption,
            videoSortOption = uiState.videoSortOption,
            folderSortDirection = uiState.folderSortDirection,
            videoSortDirection = uiState.videoSortDirection,
            layoutColumns = uiState.browserLayoutColumns,
            thumbnailRatio = uiState.browserThumbnailRatio,
            folderExplorerMode = uiState.folderExplorerMode,
            decoderEngineMode = uiState.decoderEngineMode,
            advancedCodecFilter = uiState.advancedCodecFilter,
            resolutionFilterTier = uiState.resolutionFilterTier,
            includeSubfoldersRecursive = uiState.includeSubfoldersRecursive,
            visibleFields = uiState.visibleFields,
            onSelectFolderViewMode = { viewModel.setFolderViewMode(it) },
            onSelectVideoViewMode = { viewModel.setVideoViewMode(it) },
            onSelectFolderSortOption = { viewModel.setFolderSortOption(it) },
            onSelectVideoSortOption = { viewModel.setVideoSortOption(it) },
            onSelectFolderSortDirection = { viewModel.setFolderSortDirection(it) },
            onSelectVideoSortDirection = { viewModel.setVideoSortDirection(it) },
            onSelectLayoutColumns = { viewModel.setBrowserLayoutColumns(it) },
            onSelectThumbnailRatio = { viewModel.setBrowserThumbnailRatio(it) },
            onSelectFolderExplorerMode = { viewModel.setFolderExplorerMode(it) },
            onSelectDecoderEngineMode = { viewModel.setDecoderEngineMode(it) },
            onSelectAdvancedCodecFilter = { viewModel.setAdvancedCodecFilter(it) },
            onSelectResolutionFilterTier = { viewModel.setResolutionFilterTier(it) },
            onToggleIncludeSubfoldersRecursive = { viewModel.setIncludeSubfoldersRecursive(it) },
            onUpdateVisibleFields = { viewModel.updateBrowserVisibleFields(it) },
            onOpenFfmpegCodecInspector = { viewModel.openFfmpegCodecInspector() },
            onDismiss = { viewModel.dismissBrowserControlSheet() }
        )
    }

    if (uiState.showFfmpegCodecInspectorDialog) {
        FfmpegCodecRegistryInspectorDialog(
            onDismiss = { viewModel.dismissFfmpegCodecInspector() }
        )
    }
}

@Composable
private fun EmptyMediaLibraryState(
    onRescanClick: () -> Unit,
    onOpenVideoFileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("empty_media_library_card"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(ElectricCyan.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No Device Videos Found",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Grant media storage permission to scan local videos on your device, or open a video file directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRescanClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rescan_storage_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rescan Storage")
                    }
                    Button(
                        onClick = onOpenVideoFileClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("empty_state_open_video_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Video")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItemRow(
    video: VideoItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isFavorite: Boolean,
    progressFraction: Float,
    resumePositionMs: Long = 0L,
    viewMode: BrowserViewMode = BrowserViewMode.LIST,
    thumbnailRatio: BrowserThumbnailRatio = BrowserThumbnailRatio.WIDESCREEN_16_9,
    visibleFields: BrowserVisibleFields = BrowserVisibleFields(),
    hideAllTexts: Boolean = false,
    isTextRevealed: Boolean = false,
    onTapRevealText: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val isCompact = viewMode == BrowserViewMode.COMPACT || viewMode == BrowserViewMode.MINIMAL_LIST
    val isDetailed = viewMode == BrowserViewMode.DETAILED_CARDS || viewMode == BrowserViewMode.CINEMA_STRIP
    val thumbWidth = when {
        isCompact -> 84.dp
        viewMode == BrowserViewMode.CINEMA_STRIP -> 144.dp
        isDetailed -> 132.dp
        else -> 112.dp
    }
    val thumbHeight = when {
        isCompact -> 52.dp
        viewMode == BrowserViewMode.CINEMA_STRIP -> 64.dp
        thumbnailRatio == BrowserThumbnailRatio.SQUARE_1_1 -> thumbWidth
        thumbnailRatio == BrowserThumbnailRatio.CLASSIC_4_3 -> (thumbWidth.value * 0.75f).dp
        thumbnailRatio == BrowserThumbnailRatio.CINEMA_21_9 -> (thumbWidth.value * 0.48f).dp
        else -> (thumbWidth.value * 0.62f).dp
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, ElectricCyan, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            )
            .testTag("video_item_${video.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item 8: Show video thumbnail and file name only without extra badges
            Box(
                modifier = Modifier
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                VideoItemPictureThumbnail(
                    video = video,
                    showPicturePreview = visibleFields.showVideoPicturePreview,
                    showDurationBadge = false,
                    showFormatOverlay = false,
                    modifier = Modifier.fillMaxSize()
                )

                if (progressFraction > 0.02f) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = ElectricCyan,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Item 8 & Clarifying Answer 3: Show video file name only without extra badges
            Column(modifier = Modifier.weight(1f)) {
                val fileNameLabel = video.displayName.ifBlank { video.title }
                if (hideAllTexts) {
                    TapToSeeSinglePlaceTextBox(
                        itemId = "video_${video.id}",
                        isRevealed = isTextRevealed,
                        primaryTitle = fileNameLabel,
                        consolidatedDetailsText = fileNameLabel,
                        onTapToggleReveal = { onTapRevealText?.invoke() }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fileNameLabel,
                            style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("video_filename_text_${video.id}")
                        )
                        if (video.isNewVideo) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(ElectricCyan, CircleShape)
                                    .testTag("video_new_point_dot_${video.id}")
                            )
                        }
                    }
                }
            }

            // Selection Checkbox or Hold Actions Trigger
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(24.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onInfoClick != null) {
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { onInfoClick() }
                                .testTag("video_detail_button_${video.id}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Detail",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onLongPress,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("video_more_hold_options_${video.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Copy, Move, or Delete options"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridItemCard(
    video: VideoItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isFavorite: Boolean,
    progressFraction: Float,
    resumePositionMs: Long = 0L,
    thumbnailRatio: BrowserThumbnailRatio = BrowserThumbnailRatio.WIDESCREEN_16_9,
    visibleFields: BrowserVisibleFields = BrowserVisibleFields(),
    hideAllTexts: Boolean = false,
    isTextRevealed: Boolean = false,
    onTapRevealText: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onInfoClick: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val previewHeight = when (thumbnailRatio) {
        BrowserThumbnailRatio.SQUARE_1_1 -> 136.dp
        BrowserThumbnailRatio.CLASSIC_4_3 -> 118.dp
        BrowserThumbnailRatio.CINEMA_21_9 -> 84.dp
        else -> 104.dp
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            )
            .testTag("video_grid_item_${video.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                VideoItemPictureThumbnail(
                    video = video,
                    showPicturePreview = visibleFields.showVideoPicturePreview,
                    showDurationBadge = false,
                    showFormatOverlay = false,
                    modifier = Modifier.fillMaxSize()
                )
                if (progressFraction > 0.02f) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = ElectricCyan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val fileNameLabel = video.displayName.ifBlank { video.title }
            if (hideAllTexts) {
                TapToSeeSinglePlaceTextBox(
                    itemId = "video_grid_${video.id}",
                    isRevealed = isTextRevealed,
                    primaryTitle = fileNameLabel,
                    consolidatedDetailsText = fileNameLabel,
                    onTapToggleReveal = { onTapRevealText?.invoke() }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fileNameLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("video_grid_filename_text_${video.id}")
                    )
                    if (video.isNewVideo) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ElectricCyan, CircleShape)
                                .testTag("video_new_point_dot_${video.id}")
                        )
                    }
                    if (onInfoClick != null) {
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .clickable { onInfoClick() }
                                .testTag("video_grid_detail_button_${video.id}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Detail",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onLongPress,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Hold options",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderGridCard(
    folder: VideoFolder,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    visibleFields: BrowserVisibleFields = BrowserVisibleFields(),
    hideAllTexts: Boolean = false,
    isTextRevealed: Boolean = false,
    onTapRevealText: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, ElectricCyan, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            )
            .testTag("folder_grid_item_${folder.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            FolderVideoCollagePreview(
                folder = folder,
                showCollagePreview = visibleFields.showFolderCollagePreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (hideAllTexts) {
                val consolidatedFolderText = buildString {
                    append("${folder.videoCount} video(s)")
                    append(" • ${Formatters.formatFileSize(folder.totalSizeBytes)}")
                    val formats = folder.videos.map { it.formatBadge }.distinct().joinToString(", ")
                    if (formats.isNotEmpty()) append(" • Formats: $formats")
                    append(" • Path: ${folder.path}")
                }
                TapToSeeSinglePlaceTextBox(
                    itemId = "folder_grid_${folder.id}",
                    isRevealed = isTextRevealed,
                    primaryTitle = folder.name,
                    consolidatedDetailsText = consolidatedFolderText,
                    onTapToggleReveal = { onTapRevealText?.invoke() }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (folder.hasNewVideo || folder.newVideoCount > 0) {
                        val badgeCount = folder.newVideoCount.coerceAtLeast(1)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(ElectricCyan, CircleShape)
                                .testTag("folder_new_video_dot_${folder.id}")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = ElectricCyan,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("folder_new_video_count_badge_${folder.id}")
                        ) {
                            Text(
                                text = "$badgeCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (isSelectionMode) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Folder selected" else "Folder not selected",
                            tint = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = buildString {
                        append("${folder.videoCount} video(s)")
                        if (visibleFields.showFileSize) {
                            append(" • ${Formatters.formatFileSize(folder.totalSizeBytes)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberGold
                )
                if (visibleFields.showFolderPath) {
                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCardRow(
    folder: VideoFolder,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    viewMode: BrowserViewMode = BrowserViewMode.LIST,
    visibleFields: BrowserVisibleFields = BrowserVisibleFields(),
    hideAllTexts: Boolean = false,
    isTextRevealed: Boolean = false,
    onTapRevealText: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    showPasteHereAction: Boolean = false,
    isPasteMoveOperation: Boolean = true,
    onPasteHereClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val isCompact = viewMode == BrowserViewMode.COMPACT
    val isDetailed = viewMode == BrowserViewMode.DETAILED_CARDS
    val collageWidth = when {
        isCompact -> 62.dp
        isDetailed -> 96.dp
        else -> 78.dp
    }
    val collageHeight = when {
        isCompact -> 46.dp
        isDetailed -> 68.dp
        else -> 58.dp
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, ElectricCyan, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            )
            .testTag("folder_item_${folder.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 2x2 Video Frame Collage Preview inside Folder Card
            FolderVideoCollagePreview(
                folder = folder,
                showCollagePreview = visibleFields.showFolderCollagePreview,
                modifier = Modifier
                    .width(collageWidth)
                    .height(collageHeight)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (hideAllTexts) {
                    val consolidatedFolderText = buildString {
                        append("${folder.videoCount} video(s)")
                        append(" • ${Formatters.formatFileSize(folder.totalSizeBytes)}")
                        val formats = folder.videos.map { it.formatBadge }.distinct().joinToString(", ")
                        if (formats.isNotEmpty()) append(" • Formats: $formats")
                        append(" • Path: ${folder.path}")
                    }
                    TapToSeeSinglePlaceTextBox(
                        itemId = "folder_${folder.id}",
                        isRevealed = isTextRevealed,
                        primaryTitle = folder.name,
                        consolidatedDetailsText = consolidatedFolderText,
                        onTapToggleReveal = { onTapRevealText?.invoke() }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = folder.name,
                            style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (folder.hasNewVideo || folder.newVideoCount > 0) {
                            val badgeCount = folder.newVideoCount.coerceAtLeast(1)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(ElectricCyan, CircleShape)
                                    .testTag("folder_new_video_dot_${folder.id}")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = ElectricCyan,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("folder_new_video_count_badge_${folder.id}")
                            ) {
                                Text(
                                    text = "$badgeCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            append("${folder.videoCount} video(s)")
                            if (visibleFields.showFileSize) {
                                append(" • ${Formatters.formatFileSize(folder.totalSizeBytes)}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (visibleFields.showFolderPath && !isCompact) {
                        Text(
                            text = folder.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (showPasteHereAction && onPasteHereClick != null) {
                Button(
                    onClick = onPasteHereClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .testTag("folder_paste_here_${folder.id}")
                ) {
                    Icon(
                        imageVector = if (isPasteMoveOperation) Icons.AutoMirrored.Filled.DriveFileMove else Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPasteMoveOperation) "Move Here" else "Paste Here",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Folder selected" else "Folder not selected",
                    tint = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(24.dp)
                        .testTag("folder_select_checkbox_${folder.id}")
                )
            } else {
                IconButton(
                    onClick = onLongPress,
                    modifier = Modifier.testTag("folder_hold_options_${folder.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Folder Copy, Move, or Delete options"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageHierarchyTreeSection(
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel
) {
    val node = uiState.currentDirectoryNode ?: return
    val isCurrentPathRemovable = com.cyosplay.player.util.CryptoSecurityManager.isRemovableSdCardPath(node.currentPath)
    val clip = uiState.clipboardState

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("storage_hierarchy_tree_list")
    ) {
        // Storage Volume Selector Cards (Internal Storage & SD Card Storage)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.storageVolumes.forEach { vol ->
                    val isSelectedVol = node.currentPath.startsWith(vol.rootPath, ignoreCase = true)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelectedVol) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (isSelectedVol) {
                                    Modifier.border(1.5.dp, ElectricCyan, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { viewModel.openStorageDirectoryPath(vol.rootPath) }
                            .testTag("storage_volume_card_${vol.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (vol.isRemovable) Icons.Default.SdStorage else Icons.Default.Storage,
                                contentDescription = vol.name,
                                tint = if (vol.isRemovable) ElectricCyan else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vol.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${Formatters.formatFileSize(vol.availableBytes)} free",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Current Directory Breadcrumb Header + Direct Paste/Move Here Action
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("current_directory_breadcrumb_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCurrentPathRemovable) Icons.Default.SdStorage else Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = if (isCurrentPathRemovable) ElectricCyan else AmberGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = node.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = node.currentPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (clip != null) {
                        Button(
                            onClick = {
                                viewModel.requestPasteClipboardConfirmation(
                                    targetFolderPath = node.currentPath,
                                    targetFolderName = node.displayName,
                                    isRemovableSd = isCurrentPathRemovable
                                )
                            },
                            modifier = Modifier.testTag("hierarchy_paste_here_current_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (clip.operation == HoldClipboardOperation.MOVE) "Move Here" else "Paste Here")
                        }
                    }
                }
            }
        }

        if (node.parentPath != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openStorageDirectoryPath(node.parentPath) }
                        .testTag("hierarchy_up_parent_button")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Parent folder",
                            tint = ElectricCyan
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Up to Parent Directory (..)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = node.parentPath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        val q = uiState.searchQuery.trim()
        val videoOnlySubfolders = node.subfolders.filter { sub ->
            sub.totalRecursiveVideoCount > 0 && (
                q.isEmpty() ||
                    sub.name.contains(q, ignoreCase = true) ||
                    sub.path.contains(q, ignoreCase = true)
                )
        }
        val videoOnlyDirectVideos = node.directVideos.filter { video ->
            VideoRepository.isStrictVideoFile(
                video.displayName.ifBlank { video.path },
                video.mimeType
            ) && (
                q.isEmpty() ||
                    video.title.contains(q, ignoreCase = true) ||
                    video.displayName.contains(q, ignoreCase = true) ||
                    video.formatBadge.contains(q, ignoreCase = true) ||
                    video.videoCodecHint.contains(q, ignoreCase = true)
                )
        }

        items(videoOnlySubfolders, key = { it.path }) { sub ->
            val isSubSd = com.cyosplay.player.util.CryptoSecurityManager.isRemovableSdCardPath(sub.path)
            val normalizedSubPath = sub.path.trim().trimEnd('/')
            val isSubSelected = uiState.selectedFolderPaths.contains(normalizedSubPath)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSubSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSubSelected) {
                            Modifier.border(1.5.dp, ElectricCyan, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        }
                    )
                    .combinedClickable(
                        onClick = {
                            if (uiState.isSelectionModeActive) {
                                viewModel.toggleSubfolderPathSelection(sub.path)
                            } else {
                                viewModel.openStorageDirectoryPath(sub.path)
                            }
                        },
                        onLongClick = {
                            viewModel.toggleSubfolderPathSelection(sub.path)
                        }
                    )
                    .testTag("hierarchy_subfolder_${sub.name}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSubSd) Icons.Default.SdStorage else Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (isSubSd) ElectricCyan else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sub.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${sub.totalRecursiveVideoCount} videos • ${Formatters.formatFileSize(sub.totalSizeBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = sub.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (uiState.isSelectionModeActive) {
                        Icon(
                            imageVector = if (isSubSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSubSelected) "Subfolder selected" else "Subfolder not selected",
                            tint = if (isSubSelected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(24.dp)
                        )
                    } else if (clip != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.requestPasteClipboardConfirmation(
                                    targetFolderPath = sub.path,
                                    targetFolderName = sub.name,
                                    isRemovableSd = isSubSd
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("hierarchy_paste_into_${sub.name}")
                        ) {
                            Text(
                                text = if (clip.operation == HoldClipboardOperation.MOVE) "Move Here" else "Paste Here",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        items(videoOnlyDirectVideos, key = { it.path }) { video ->
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
                        viewModel.playVideo(video, videoOnlyDirectVideos)
                    }
                },
                onLongPress = { viewModel.onHoldVideoItem(video) },
                onFavoriteToggle = { viewModel.toggleFavoriteVideo(video.path) },
                onInfoClick = { viewModel.inspectVideoProperties(video) }
            )
        }
    }
}
