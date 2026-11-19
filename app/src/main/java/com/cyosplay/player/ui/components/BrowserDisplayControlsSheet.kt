package com.cyosplay.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyosplay.player.data.model.AdvancedCodecFilter
import com.cyosplay.player.data.model.BrowserControlTab
import com.cyosplay.player.data.model.BrowserLayoutColumns
import com.cyosplay.player.data.model.BrowserThumbnailRatio
import com.cyosplay.player.data.model.BrowserViewMode
import com.cyosplay.player.data.model.BrowserVisibleFields
import com.cyosplay.player.data.model.DecoderEngineMode
import com.cyosplay.player.data.model.FolderExplorerMode
import com.cyosplay.player.data.model.FolderSortOption
import com.cyosplay.player.data.model.ResolutionFilterTier
import com.cyosplay.player.data.model.SortDirection
import com.cyosplay.player.data.model.VideoSortOption
import com.cyosplay.player.ui.theme.AmberGold
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault

/**
 * Action bar displaying the 5 quick controls in Folders & Videos screens:
 * - View Mode
 * - Sort
 * - Layout
 * - Advanced
 * - Fields
 */
@Composable
fun BrowserFiveControlChipsBar(
    currentViewMode: BrowserViewMode,
    sortSummaryLabel: String,
    layoutColumns: BrowserLayoutColumns,
    advancedCodecFilter: AdvancedCodecFilter,
    visibleFields: BrowserVisibleFields,
    onOpenControlTab: (BrowserControlTab) -> Unit,
    onQuickCycleViewMode: () -> Unit,
    hideAllTextsEnabled: Boolean = false,
    onToggleHideAllTexts: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("browser_five_controls_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggleHideAllTexts != null) {
            ControlActionChip(
                icon = if (hideAllTextsEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                title = if (hideAllTextsEnabled) "Texts Hidden" else "Hide Texts",
                badge = if (hideAllTextsEnabled) "TAP TO SEE" else "VISIBLE",
                isActive = hideAllTextsEnabled,
                accentColor = AmberGold,
                testTag = "control_chip_hide_all_texts",
                onClick = onToggleHideAllTexts
            )
        }
        ControlActionChip(
            icon = if (currentViewMode == BrowserViewMode.GRID) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
            title = "View Mode",
            badge = currentViewMode.shortLabel,
            isActive = currentViewMode != BrowserViewMode.LIST,
            accentColor = ElectricCyan,
            testTag = "control_chip_view_mode",
            onClick = { onOpenControlTab(BrowserControlTab.VIEW_MODE) }
        )
        ControlActionChip(
            icon = Icons.AutoMirrored.Filled.Sort,
            title = "Sort",
            badge = sortSummaryLabel,
            isActive = true,
            accentColor = AmberGold,
            testTag = "control_chip_sort",
            onClick = { onOpenControlTab(BrowserControlTab.SORT) }
        )
        ControlActionChip(
            icon = Icons.Default.DashboardCustomize,
            title = "Layout",
            badge = layoutColumns.label,
            isActive = layoutColumns != BrowserLayoutColumns.AUTO,
            accentColor = ElectricCyan,
            testTag = "control_chip_layout",
            onClick = { onOpenControlTab(BrowserControlTab.LAYOUT) }
        )
        ControlActionChip(
            icon = Icons.Default.SettingsSuggest,
            title = "Advanced",
            badge = if (advancedCodecFilter == AdvancedCodecFilter.ALL) "Engine" else advancedCodecFilter.name.take(6),
            isActive = advancedCodecFilter != AdvancedCodecFilter.ALL,
            accentColor = EmeraldVault,
            testTag = "control_chip_advanced",
            onClick = { onOpenControlTab(BrowserControlTab.ADVANCED) }
        )
        ControlActionChip(
            icon = Icons.Default.Checklist,
            title = "Fields",
            badge = "${visibleFields.activeCount}",
            isActive = true,
            accentColor = ElectricCyan,
            testTag = "control_chip_fields",
            onClick = { onOpenControlTab(BrowserControlTab.FIELDS) }
        )
    }
}

@Composable
private fun ControlActionChip(
    icon: ImageVector,
    title: String,
    badge: String,
    isActive: Boolean,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (isActive) {
            accentColor.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$title ($badge)",
                tint = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Interactive Display & Folder Settings Dialog covering View Mode, Sort, Layout, Advanced, and Fields.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowserDisplaySettingsDialog(
    initialTab: BrowserControlTab,
    isFoldersContext: Boolean,
    folderViewMode: BrowserViewMode,
    videoViewMode: BrowserViewMode,
    folderSortOption: FolderSortOption,
    videoSortOption: VideoSortOption,
    folderSortDirection: SortDirection,
    videoSortDirection: SortDirection,
    layoutColumns: BrowserLayoutColumns,
    thumbnailRatio: BrowserThumbnailRatio,
    folderExplorerMode: FolderExplorerMode,
    decoderEngineMode: DecoderEngineMode,
    advancedCodecFilter: AdvancedCodecFilter,
    resolutionFilterTier: ResolutionFilterTier,
    includeSubfoldersRecursive: Boolean,
    visibleFields: BrowserVisibleFields,
    onSelectFolderViewMode: (BrowserViewMode) -> Unit,
    onSelectVideoViewMode: (BrowserViewMode) -> Unit,
    onSelectFolderSortOption: (FolderSortOption) -> Unit,
    onSelectVideoSortOption: (VideoSortOption) -> Unit,
    onSelectFolderSortDirection: (SortDirection) -> Unit,
    onSelectVideoSortDirection: (SortDirection) -> Unit,
    onSelectLayoutColumns: (BrowserLayoutColumns) -> Unit,
    onSelectThumbnailRatio: (BrowserThumbnailRatio) -> Unit,
    onSelectFolderExplorerMode: (FolderExplorerMode) -> Unit,
    onSelectDecoderEngineMode: (DecoderEngineMode) -> Unit,
    onSelectAdvancedCodecFilter: (AdvancedCodecFilter) -> Unit,
    onSelectResolutionFilterTier: (ResolutionFilterTier) -> Unit,
    onToggleIncludeSubfoldersRecursive: (Boolean) -> Unit,
    onUpdateVisibleFields: (BrowserVisibleFields) -> Unit,
    onOpenFfmpegCodecInspector: () -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        modifier = Modifier.testTag("browser_display_settings_dialog"),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Folder & Video Browser Controls",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 5 Tab Switcher Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BrowserControlTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        FilterChip(
                            selected = selected,
                            onClick = { activeTab = tab },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                selectedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("settings_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeTab) {
                    BrowserControlTab.VIEW_MODE -> {
                        item {
                            Text(
                                text = "FOLDERS VIEW MODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BrowserViewMode.entries.forEach { mode ->
                                    val selected = folderViewMode == mode
                                    OptionSelectionRow(
                                        title = mode.label,
                                        subtitle = when (mode) {
                                            BrowserViewMode.LIST -> "Standard folder cards with clean folder icon"
                                            BrowserViewMode.GRID -> "Multi-column folder tiles with clean folder icon"
                                            BrowserViewMode.COMPACT -> "High-density folder rows for large libraries"
                                            BrowserViewMode.DETAILED_CARDS -> "Detailed folder cards with storage & size details"
                                            BrowserViewMode.GALLERY_CARDS -> "Large showcase gallery cards for folders"
                                            BrowserViewMode.CINEMA_STRIP -> "Widescreen cinema strip rows for folders"
                                            BrowserViewMode.MINIMAL_LIST -> "Ultra-clean file & folder name list"
                                        },
                                        selected = selected,
                                        testTag = "folder_view_mode_${mode.name.lowercase()}",
                                        onClick = { onSelectFolderViewMode(mode) }
                                    )
                                }
                            }
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "VIDEOS VIEW MODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BrowserViewMode.entries.forEach { mode ->
                                    val selected = videoViewMode == mode
                                    OptionSelectionRow(
                                        title = mode.label,
                                        subtitle = when (mode) {
                                            BrowserViewMode.LIST -> "Standard row with video thumbnail & file name"
                                            BrowserViewMode.GRID -> "Multi-column thumbnail grid with file name"
                                            BrowserViewMode.COMPACT -> "Compact list with mini thumbnail & file name"
                                            BrowserViewMode.DETAILED_CARDS -> "Large preview card with video thumbnail & file name"
                                            BrowserViewMode.GALLERY_CARDS -> "2-column widescreen gallery cards with file name"
                                            BrowserViewMode.CINEMA_STRIP -> "21:9 cinema strip preview with file name"
                                            BrowserViewMode.MINIMAL_LIST -> "Minimal fast list with thumbnail & file name"
                                        },
                                        selected = selected,
                                        testTag = "video_view_mode_${mode.name.lowercase()}",
                                        onClick = { onSelectVideoViewMode(mode) }
                                    )
                                }
                            }
                        }
                    }

                    BrowserControlTab.SORT -> {
                        item {
                            Text(
                                text = "FOLDER SORT ORDER",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SortDirection.entries.forEach { dir ->
                                    FilterChip(
                                        selected = folderSortDirection == dir,
                                        onClick = { onSelectFolderSortDirection(dir) },
                                        label = { Text(dir.label) },
                                        modifier = Modifier.testTag("folder_sort_dir_${dir.name.lowercase()}")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FolderSortOption.entries.forEach { opt ->
                                    OptionSelectionRow(
                                        title = opt.label,
                                        subtitle = null,
                                        selected = folderSortOption == opt,
                                        testTag = "folder_sort_opt_${opt.name.lowercase()}",
                                        onClick = { onSelectFolderSortOption(opt) }
                                    )
                                }
                            }
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "VIDEO SORT ORDER",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SortDirection.entries.forEach { dir ->
                                    FilterChip(
                                        selected = videoSortDirection == dir,
                                        onClick = { onSelectVideoSortDirection(dir) },
                                        label = { Text(dir.label) },
                                        modifier = Modifier.testTag("video_sort_dir_${dir.name.lowercase()}")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                VideoSortOption.entries.forEach { opt ->
                                    OptionSelectionRow(
                                        title = opt.label,
                                        subtitle = null,
                                        selected = videoSortOption == opt,
                                        testTag = "video_sort_opt_${opt.name.lowercase()}",
                                        onClick = { onSelectVideoSortOption(opt) }
                                    )
                                }
                            }
                        }
                    }

                    BrowserControlTab.LAYOUT -> {
                        item {
                            Text(
                                text = "GRID & CARD COLUMNS",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BrowserLayoutColumns.entries.forEach { col ->
                                    OptionSelectionRow(
                                        title = col.label,
                                        subtitle = when (col) {
                                            BrowserLayoutColumns.AUTO -> "Automatically adapts to phone portrait/landscape & tablet width"
                                            BrowserLayoutColumns.ONE_COLUMN -> "1 full-width cinema showcase column"
                                            BrowserLayoutColumns.TWO_COLUMNS -> "2 balanced columns"
                                            BrowserLayoutColumns.THREE_COLUMNS -> "3 dense gallery columns"
                                            BrowserLayoutColumns.FOUR_COLUMNS -> "4 ultra-dense wall columns"
                                        },
                                        selected = layoutColumns == col,
                                        testTag = "layout_col_${col.name.lowercase()}",
                                        onClick = { onSelectLayoutColumns(col) }
                                    )
                                }
                            }
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "VIDEO PICTURE THUMBNAIL ASPECT RATIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BrowserThumbnailRatio.entries.forEach { ratio ->
                                    OptionSelectionRow(
                                        title = ratio.label,
                                        subtitle = null,
                                        selected = thumbnailRatio == ratio,
                                        testTag = "layout_ratio_${ratio.name.lowercase()}",
                                        onClick = { onSelectThumbnailRatio(ratio) }
                                    )
                                }
                            }
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "FOLDER STRUCTURE LAYOUT",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldVault,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FolderExplorerMode.entries.forEach { mode ->
                                    OptionSelectionRow(
                                        title = mode.label,
                                        subtitle = if (mode == FolderExplorerMode.FLAT_FOLDERS) {
                                            "Group all video folders with clean folder icons"
                                        } else {
                                            "Navigate full storage directory hierarchy"
                                        },
                                        selected = folderExplorerMode == mode,
                                        testTag = "layout_explorer_${mode.name.lowercase()}",
                                        onClick = { onSelectFolderExplorerMode(mode) }
                                    )
                                }
                            }
                        }
                    }

                    BrowserControlTab.ADVANCED -> {
                        item {
                            Text(
                                text = "HARDWARE / SOFTWARE DECODING & ENCODING ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DecoderEngineMode.entries.forEach { mode ->
                                    OptionSelectionRow(
                                        title = mode.label,
                                        subtitle = when (mode) {
                                            DecoderEngineMode.HW -> "Direct GPU hardware decoder for maximum power savings"
                                            DecoderEngineMode.HW_PLUS, DecoderEngineMode.AUTO_HW_PLUS -> "Hardware MediaCodec with automatic software fallback for Xvid & complex streams"
                                            DecoderEngineMode.SW -> "Standard software decoding for broad legacy codec compatibility"
                                            DecoderEngineMode.SW_PLUS, DecoderEngineMode.SOFTWARE_MULTI_CORE -> "Force multi-core software decoding for 10-bit, 4K/8K, XVID & MKV streams"
                                        },
                                        selected = decoderEngineMode == mode,
                                        testTag = "advanced_decoder_${mode.name.lowercase()}",
                                        onClick = { onSelectDecoderEngineMode(mode) }
                                    )
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable { onToggleIncludeSubfoldersRecursive(!includeSubfoldersRecursive) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Include Recursive Video Subfolders",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Aggregate videos from nested directories",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = includeSubfoldersRecursive,
                                    onCheckedChange = onToggleIncludeSubfoldersRecursive
                                )
                            }
                        }
                    }

                    BrowserControlTab.FIELDS -> {
                        item {
                            Text(
                                text = "VISIBLE METADATA & PICTURE PREVIEW FIELDS",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FieldToggleRow(
                                title = "Show Video Picture Preview (Thumbnails)",
                                subtitle = "Render extracted video frame pictures on video items",
                                checked = visibleFields.showVideoPicturePreview,
                                testTag = "field_toggle_video_picture",
                                onCheckedChange = {
                                    onUpdateVisibleFields(visibleFields.copy(showVideoPicturePreview = it))
                                }
                            )
                            FieldToggleRow(
                                title = "Resolution & Aspect Ratio Badge",
                                subtitle = "Show 8K / 4K / 1080P and exact pixel dimensions (e.g. 3840x2160 16:9)",
                                checked = visibleFields.showResolutionBadge,
                                testTag = "field_toggle_resolution",
                                onCheckedChange = {
                                    onUpdateVisibleFields(visibleFields.copy(showResolutionBadge = it))
                                }
                            )
                            FieldToggleRow(
                                title = "Container Format Badge (MKV / MP4 / WebM)",
                                subtitle = "Display container format extension badge",
                                checked = visibleFields.showContainerBadge,
                                testTag = "field_toggle_container",
                                onCheckedChange = {
                                    onUpdateVisibleFields(visibleFields.copy(showContainerBadge = it))
                                }
                            )
                            FieldToggleRow(
                                title = "Video & Audio Codec Badges",
                                subtitle = "Show H.264 / AVC, HEVC, Dolby Vision, TrueHD Atmos, DDP 5.1",
                                checked = visibleFields.showCodecBadges,
                                testTag = "field_toggle_codecs",
                                onCheckedChange = {
                                    onUpdateVisibleFields(visibleFields.copy(showCodecBadges = it))
                                }
                            )
                            FieldToggleRow(
                                title = "Bitrate (Mbps / kbps)",
                                subtitle = "Display stream bitrate calculation on video cards",
                                checked = visibleFields.showBitrateAndFps,
                                testTag = "field_toggle_bitrate",
                                onCheckedChange = {
                                    onUpdateVisibleFields(visibleFields.copy(showBitrateAndFps = it))
                                }
                            )
                            FieldToggleRow(
                                title = "Exact File Size",
                                subtitle = "Show formatted file size on folders and videos",
                                checked = visibleFields.showFileSize,
                                testTag = "field_toggle_size",
                                onCheckedChange = {
                                    onUpdateVisibleFields(visibleFields.copy(showFileSize = it))
                                }
                            )
                            FieldToggleRow(
                                title = "Video Duration",
                                subtitle = "Show playback duration on thumbnail and metadata row",
                                checked = visibleFields.showDuration,
                                testTag = "field_toggle_duration",
                                onCheckedChange = {
                                    onUpdateVisibleFields(visibleFields.copy(showDuration = it))
                                }
                            )
                            FieldToggleRow(
                                title = "Folder Path",
                                subtitle = "Show storage directory path on folder cards",
                                checked = visibleFields.showFolderPath,
                                testTag = "field_toggle_folder_path",
                                onCheckedChange = {
                                    onUpdateVisibleFields(visibleFields.copy(showFolderPath = it))
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_browser_display_settings_button")
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            if (activeTab == BrowserControlTab.FIELDS) {
                TextButton(
                    onClick = { onUpdateVisibleFields(BrowserVisibleFields()) },
                    modifier = Modifier.testTag("reset_fields_button")
                ) {
                    Text("Select All Fields")
                }
            }
        }
    )
}

@Composable
private fun OptionSelectionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) {
            ElectricCyan.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FieldToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Encrypted Codec Registry Dialog (raw FFmpeg & Matroska strings hidden; displays AES-256-GCM status).
 */
@Composable
fun FfmpegCodecRegistryInspectorDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ffmpeg_codec_inspector_dialog"),
        title = {
            Column {
                Text(
                    text = "Multi-Codec Engine",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ZERO-KNOWLEDGE ENCRYPTED CODEC ENGINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "All hardware and multi-core software decoder profiles are active and protected with AES-256-GCM build encryption.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_ffmpeg_inspector_button")
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * Single-Place Full Text Inspector Banner:
 * When "Hide All Texts" is enabled and the user taps an individual folder or video item's text badge,
 * all of that item's text is displayed together in one dedicated place without any truncation or size restriction.
 */
@Composable
fun SinglePlaceInspectorTextCard(
    title: String,
    fullBodyText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("single_place_inspector_text_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1322)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "TEXT SET IN ONE PLACE (UNTRUNCATED)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmberGold
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_single_place_text_button")
                ) {
                    Text("Hide Text", color = ElectricCyan)
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Visible,
                modifier = Modifier.testTag("single_place_text_title")
            )
            Text(
                text = fullBodyText,
                style = MaterialTheme.typography.bodySmall,
                color = ElectricCyan,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Visible,
                modifier = Modifier.testTag("single_place_text_body")
            )
        }
    }
}

/**
 * Individual Tap-to-Reveal Text Box used inside Folder cards and Video cards when "Hide All Texts" is active.
 */
@Composable
fun TapToSeeSinglePlaceTextBox(
    itemId: String,
    isRevealed: Boolean,
    primaryTitle: String,
    consolidatedDetailsText: String,
    onTapToggleReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isRevealed) {
        Surface(
            color = AmberGold.copy(alpha = 0.16f),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onTapToggleReveal)
                .testTag("tap_to_see_text_pill_$itemId")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Tap to see text",
                    tint = AmberGold,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Tap to See Text",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AmberGold
                )
            }
        }
    } else {
        Surface(
            color = Color(0xFF0E1729),
            shape = RoundedCornerShape(10.dp),
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onTapToggleReveal)
                .testTag("revealed_single_place_text_$itemId")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Tap to hide text",
                        tint = AmberGold,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "ALL TEXT IN ONE PLACE (TAP TO HIDE)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmberGold
                    )
                }
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Visible
                )
                Text(
                    text = consolidatedDetailsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricCyan,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}
