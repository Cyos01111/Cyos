package com.cyosplay.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyosplay.player.data.model.BrowserViewMode
import com.cyosplay.player.data.model.HoldClipboardOperation
import com.cyosplay.player.data.model.SortDirection
import com.cyosplay.player.data.model.VideoSortOption
import com.cyosplay.player.data.repository.VideoRepository
import com.cyosplay.player.ui.VideoPlayerUiState
import com.cyosplay.player.ui.VideoPlayerViewModel
import com.cyosplay.player.ui.components.BrowserDisplaySettingsDialog
import com.cyosplay.player.ui.components.BrowserFiveControlChipsBar
import com.cyosplay.player.ui.components.ClipboardPasteBanner
import com.cyosplay.player.ui.components.FfmpegCodecRegistryInspectorDialog
import com.cyosplay.player.ui.components.HoldSelectionActionBar
import com.cyosplay.player.ui.components.LargeFileTransferProgressBanner
import com.cyosplay.player.ui.components.SinglePlaceInspectorTextCard
import com.cyosplay.player.ui.components.TapToSeeSinglePlaceTextBox
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.util.Formatters

@Composable
fun FolderDetailScreen(
    folderId: String,
    folderName: String,
    folderPath: String,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    var localFolderSearchQuery by remember { mutableStateOf("") }
    var showMoreOptionsMenu by remember { mutableStateOf(false) }

    val savedScroll = remember(folderId) {
        viewModel.getFolderDetailScrollPosition(folderId)
    }
    val detailListState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScroll.first,
        initialFirstVisibleItemScrollOffset = savedScroll.second
    )
    val detailGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedScroll.first,
        initialFirstVisibleItemScrollOffset = savedScroll.second
    )

    val folderConfig = remember(
        folderId,
        uiState.folderBrowserConfigMap,
        uiState.videoViewMode,
        uiState.videoSortOption,
        uiState.videoSortDirection,
        uiState.browserLayoutColumns,
        uiState.browserThumbnailRatio
    ) {
        uiState.folderBrowserConfigMap[folderId] ?: com.cyosplay.player.data.model.FolderBrowserConfig(
            viewMode = uiState.videoViewMode,
            sortOption = uiState.videoSortOption,
            sortDirection = uiState.videoSortDirection,
            layoutColumns = uiState.browserLayoutColumns,
            thumbnailRatio = uiState.browserThumbnailRatio
        )
    }

    val isGridMode = folderConfig.viewMode == BrowserViewMode.GRID ||
        folderConfig.viewMode == BrowserViewMode.GALLERY_CARDS

    DisposableEffect(folderId, detailListState, detailGridState, isGridMode) {
        onDispose {
            val idx = if (isGridMode) {
                detailGridState.firstVisibleItemIndex
            } else {
                detailListState.firstVisibleItemIndex
            }
            val off = if (isGridMode) {
                detailGridState.firstVisibleItemScrollOffset
            } else {
                detailListState.firstVisibleItemScrollOffset
            }
            viewModel.saveFolderDetailScrollPosition(
                folderId = folderId,
                firstVisibleIndex = idx,
                scrollOffset = off
            )
        }
    }

    val normalizedFolderPath = remember(folderPath) { folderPath.trim().trimEnd('/') }
    val folderVideos = remember(
        uiState.allVideos,
        normalizedFolderPath,
        folderId,
        localFolderSearchQuery,
        uiState.advancedCodecFilter,
        uiState.resolutionFilterTier,
        folderConfig.sortOption,
        folderConfig.sortDirection
    ) {
        val q = localFolderSearchQuery.trim()
        val matching = uiState.allVideos.filter { video ->
            val vFolderPath = video.folderPath.trim().trimEnd('/')
            // Strictly show only this folder's own direct videos (never mix other folders' videos)
            val inFolder = if (normalizedFolderPath.isNotBlank()) {
                vFolderPath.equals(normalizedFolderPath, ignoreCase = true)
            } else {
                video.folderId == folderId
            }
            val isStrictVideo = VideoRepository.isStrictVideoFile(
                video.displayName.ifBlank { video.path },
                video.mimeType
            )
            val matchesSearch = q.isEmpty() ||
                video.title.contains(q, ignoreCase = true) ||
                video.displayName.contains(q, ignoreCase = true) ||
                video.formatBadge.contains(q, ignoreCase = true) ||
                video.videoCodecHint.contains(q, ignoreCase = true) ||
                video.audioCodecHint.contains(q, ignoreCase = true)
            val matchesAdvanced = viewModel.matchesAdvancedVideoFilters(video)
            inFolder && isStrictVideo && matchesSearch && matchesAdvanced
        }
        viewModel.sortVideos(matching, folderConfig.sortOption, folderConfig.sortDirection)
    }

    val gridCells = remember(folderConfig.layoutColumns, folderConfig.viewMode) {
        if (folderConfig.viewMode == BrowserViewMode.GALLERY_CARDS && folderConfig.layoutColumns.columnsCount == 0) {
            GridCells.Fixed(2)
        } else {
            when (folderConfig.layoutColumns.columnsCount) {
                0 -> GridCells.Adaptive(minSize = 165.dp)
                1 -> GridCells.Fixed(1)
                2 -> GridCells.Fixed(2)
                3 -> GridCells.Fixed(3)
                else -> GridCells.Fixed(4)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.isSelectionModeActive) {
            HoldSelectionActionBar(
                selectedCount = uiState.selectedFolderPaths.size + uiState.selectedVideoPaths.size,
                selectionLabel = uiState.selectionSummaryLabel,
                onCopyClick = { viewModel.startCopyOrMoveFlow(HoldClipboardOperation.COPY) },
                onMoveClick = { viewModel.startCopyOrMoveFlow(HoldClipboardOperation.MOVE) },
                onDeleteClick = { viewModel.openDeleteOrRecycleDialog() },
                onMoreOptionsClick = { viewModel.openHoldActionSheetForCurrentSelection() },
                onSelectAllClick = { viewModel.selectAllVisibleVideos(folderVideos) },
                onClearSelection = { viewModel.clearSelection() }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("folder_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (uiState.hideAllTextsEnabled) {
                            val headerItemId = "folder_detail_header_$folderId"
                            TapToSeeSinglePlaceTextBox(
                                itemId = headerItemId,
                                isRevealed = uiState.revealedTextItemIds.contains(headerItemId),
                                primaryTitle = folderName,
                                consolidatedDetailsText = "${folderVideos.size} video(s) • Full Path: $normalizedFolderPath",
                                onTapToggleReveal = {
                                    viewModel.toggleRevealItemText(
                                        itemId = headerItemId,
                                        title = folderName,
                                        fullDetailsText = "${folderVideos.size} video(s) • Full Path: $normalizedFolderPath"
                                    )
                                }
                            )
                        } else {
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${folderVideos.size} video(s) • $normalizedFolderPath",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.clipboardState?.let { clip ->
                        Button(
                            onClick = {
                                viewModel.requestPasteClipboardConfirmation(
                                    targetFolderPath = normalizedFolderPath,
                                    targetFolderName = folderName,
                                    isRemovableSd = com.cyosplay.player.util.CryptoSecurityManager.isRemovableSdCardPath(normalizedFolderPath)
                                )
                            },
                            modifier = Modifier.testTag("folder_detail_paste_here_button")
                        ) {
                            Text(if (clip.operation == HoldClipboardOperation.MOVE) "Move Here" else "Paste Here")
                        }
                    }
                    IconButton(
                        onClick = { viewModel.toggleBrowserSelectionMode() },
                        modifier = Modifier.testTag("folder_detail_select_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = "Select multiple files",
                            tint = ElectricCyan
                        )
                    }
                    if (folderVideos.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val idx = if (isGridMode) detailGridState.firstVisibleItemIndex else detailListState.firstVisibleItemIndex
                                val off = if (isGridMode) detailGridState.firstVisibleItemScrollOffset else detailListState.firstVisibleItemScrollOffset
                                viewModel.saveFolderDetailScrollPosition(
                                    folderId = folderId,
                                    firstVisibleIndex = idx,
                                    scrollOffset = off
                                )
                                viewModel.playAllInFolder(folderId, folderVideos)
                            },
                            modifier = Modifier.testTag("play_all_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play All",
                                tint = ElectricCyan
                            )
                        }
                    }
                    IconButton(
                        onClick = { showMoreOptionsMenu = true },
                        modifier = Modifier.testTag("folder_detail_more_options_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                        DropdownMenu(
                            expanded = showMoreOptionsMenu,
                            onDismissRequest = { showMoreOptionsMenu = false }
                        ) {
                            if (folderVideos.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Play All") },
                                    onClick = {
                                        showMoreOptionsMenu = false
                                        viewModel.playAllInFolder(folderId, folderVideos)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Select Multiple") },
                                onClick = {
                                    showMoreOptionsMenu = false
                                    viewModel.toggleBrowserSelectionMode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("View Mode") },
                                onClick = {
                                    showMoreOptionsMenu = false
                                    viewModel.openBrowserControlTab(com.cyosplay.player.data.model.BrowserControlTab.VIEW_MODE)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort") },
                                onClick = {
                                    showMoreOptionsMenu = false
                                    viewModel.openBrowserControlTab(com.cyosplay.player.data.model.BrowserControlTab.SORT)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Layout") },
                                onClick = {
                                    showMoreOptionsMenu = false
                                    viewModel.openBrowserControlTab(com.cyosplay.player.data.model.BrowserControlTab.LAYOUT)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Advanced") },
                                onClick = {
                                    showMoreOptionsMenu = false
                                    viewModel.openBrowserControlTab(com.cyosplay.player.data.model.BrowserControlTab.ADVANCED)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Fields") },
                                onClick = {
                                    showMoreOptionsMenu = false
                                    viewModel.openBrowserControlTab(com.cyosplay.player.data.model.BrowserControlTab.FIELDS)
                                }
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = localFolderSearchQuery,
            onValueChange = { localFolderSearchQuery = it },
            placeholder = { Text("Search videos") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search in folder",
                    tint = ElectricCyan
                )
            },
            trailingIcon = {
                if (localFolderSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { localFolderSearchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear folder search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("folder_detail_search_input")
        )

        // Unified Middle Header Popup inside Folder Detail: Per-Folder View Mode | Sort | Layout | Advanced | Fields
        var showFolderMiddlePopup by remember { mutableStateOf(false) }
        val sortDirArrow = if (folderConfig.sortDirection == SortDirection.ASCENDING) "↑" else "↓"
        val sortSummaryText = "${folderConfig.sortOption.label} $sortDirArrow"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "${folderConfig.viewMode.shortLabel} • $sortSummaryText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
            Box {
                Surface(
                    color = ElectricCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showFolderMiddlePopup = true }
                        .testTag("folder_detail_middle_controls_popup_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Open folder controls popup",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Controls",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }
                }
                DropdownMenu(
                    expanded = showFolderMiddlePopup,
                    onDismissRequest = { showFolderMiddlePopup = false }
                ) {
                    BrowserFiveControlChipsBar(
                        currentViewMode = folderConfig.viewMode,
                        sortSummaryLabel = sortSummaryText,
                        layoutColumns = folderConfig.layoutColumns,
                        advancedCodecFilter = uiState.advancedCodecFilter,
                        visibleFields = uiState.visibleFields,
                        onOpenControlTab = { tab ->
                            showFolderMiddlePopup = false
                            viewModel.openBrowserControlTab(tab)
                        },
                        onQuickCycleViewMode = { viewModel.cycleFolderDetailViewMode(folderId) },
                        hideAllTextsEnabled = uiState.hideAllTextsEnabled,
                        onToggleHideAllTexts = { viewModel.toggleHideAllTexts() }
                    )
                }
            }
        }

        val inspectorTitle = uiState.tappedInspectorTextTitle
        val inspectorBody = uiState.tappedInspectorTextBody
        if (inspectorTitle != null && inspectorBody != null) {
            SinglePlaceInspectorTextCard(
                title = inspectorTitle,
                fullBodyText = inspectorBody,
                onDismiss = { viewModel.clearSinglePlaceTextInspector() }
            )
        }

        uiState.clipboardState?.let { clip ->
            ClipboardPasteBanner(
                clipboardState = clip,
                destinationFolderName = folderName,
                onPasteHere = {
                    viewModel.requestPasteClipboardConfirmation(
                        targetFolderPath = normalizedFolderPath,
                        targetFolderName = folderName,
                        isRemovableSd = com.cyosplay.player.util.CryptoSecurityManager.isRemovableSdCardPath(normalizedFolderPath)
                    )
                },
                onCancel = { viewModel.clearClipboard() }
            )
        }

        uiState.fileTransferProgress?.let { progress ->
            LargeFileTransferProgressBanner(
                progress = progress,
                onDismiss = { viewModel.dismissFileTransferProgress() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (isGridMode) {
            LazyVerticalGrid(
                state = detailGridState,
                columns = gridCells,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("folder_detail_video_grid")
            ) {
                items(folderVideos, key = { it.path }) { video ->
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
                        thumbnailRatio = folderConfig.thumbnailRatio,
                        visibleFields = uiState.visibleFields,
                        hideAllTexts = uiState.hideAllTextsEnabled,
                        isTextRevealed = uiState.revealedTextItemIds.contains(videoItemId),
                        onTapRevealText = {
                            viewModel.toggleRevealItemText(
                                itemId = videoItemId,
                                title = video.displayName,
                                fullDetailsText = fullVideoText
                            )
                        },
                        onClick = {
                            if (uiState.isSelectionModeActive) {
                                viewModel.toggleVideoSelection(video)
                            } else {
                                viewModel.saveFolderDetailScrollPosition(
                                    folderId = folderId,
                                    firstVisibleIndex = detailGridState.firstVisibleItemIndex,
                                    scrollOffset = detailGridState.firstVisibleItemScrollOffset
                                )
                                viewModel.playVideo(video, folderVideos)
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
                state = detailListState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(
                    if (folderConfig.viewMode == BrowserViewMode.COMPACT || folderConfig.viewMode == BrowserViewMode.MINIMAL_LIST) 6.dp else 10.dp
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("folder_detail_video_list")
            ) {
                items(folderVideos, key = { it.path }) { video ->
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
                        viewMode = folderConfig.viewMode,
                        thumbnailRatio = folderConfig.thumbnailRatio,
                        visibleFields = uiState.visibleFields,
                        hideAllTexts = uiState.hideAllTextsEnabled,
                        isTextRevealed = uiState.revealedTextItemIds.contains(videoItemId),
                        onTapRevealText = {
                            viewModel.toggleRevealItemText(
                                itemId = videoItemId,
                                title = video.displayName,
                                fullDetailsText = fullVideoText
                            )
                        },
                        onClick = {
                            if (uiState.isSelectionModeActive) {
                                viewModel.toggleVideoSelection(video)
                            } else {
                                viewModel.saveFolderDetailScrollPosition(
                                    folderId = folderId,
                                    firstVisibleIndex = detailListState.firstVisibleItemIndex,
                                    scrollOffset = detailListState.firstVisibleItemScrollOffset
                                )
                                viewModel.playVideo(video, folderVideos)
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

    uiState.activeBrowserControlSheetTab?.let { activeTab ->
        BrowserDisplaySettingsDialog(
            initialTab = activeTab,
            isFoldersContext = false,
            folderViewMode = uiState.folderViewMode,
            videoViewMode = folderConfig.viewMode,
            folderSortOption = uiState.folderSortOption,
            videoSortOption = folderConfig.sortOption,
            folderSortDirection = uiState.folderSortDirection,
            videoSortDirection = folderConfig.sortDirection,
            layoutColumns = folderConfig.layoutColumns,
            thumbnailRatio = folderConfig.thumbnailRatio,
            folderExplorerMode = uiState.folderExplorerMode,
            decoderEngineMode = uiState.decoderEngineMode,
            advancedCodecFilter = uiState.advancedCodecFilter,
            resolutionFilterTier = uiState.resolutionFilterTier,
            includeSubfoldersRecursive = uiState.includeSubfoldersRecursive,
            visibleFields = uiState.visibleFields,
            onSelectFolderViewMode = { viewModel.setFolderViewMode(it) },
            onSelectVideoViewMode = { viewModel.setFolderDetailViewMode(folderId, it) },
            onSelectFolderSortOption = { viewModel.setFolderSortOption(it) },
            onSelectVideoSortOption = { viewModel.setFolderDetailSortOption(folderId, it) },
            onSelectFolderSortDirection = { viewModel.setFolderSortDirection(it) },
            onSelectVideoSortDirection = { viewModel.setFolderDetailSortDirection(folderId, it) },
            onSelectLayoutColumns = { viewModel.setFolderDetailLayoutColumns(folderId, it) },
            onSelectThumbnailRatio = { viewModel.setFolderDetailThumbnailRatio(folderId, it) },
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

