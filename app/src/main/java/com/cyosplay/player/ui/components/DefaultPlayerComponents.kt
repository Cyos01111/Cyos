package com.cyosplay.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.FitScreen
import com.cyosplay.player.ui.MiniPlayerSizeMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyosplay.player.data.model.HoldClipboardOperation
import com.cyosplay.player.data.model.MoveDestinationOption
import com.cyosplay.player.data.model.PlaybackProgress
import com.cyosplay.player.data.model.VideoClipboardState
import com.cyosplay.player.data.model.VideoItem
import com.cyosplay.player.data.model.VideoTechnicalDetails
import com.cyosplay.player.ui.theme.AmberGold
import com.cyosplay.player.ui.theme.CrimsonAlert
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault
import com.cyosplay.player.util.Formatters

/**
 * Top contextual action bar shown whenever one or more videos/folders are held (selected).
 * Provides one-tap Delete, Move, Copy, Select All, and Cancel actions.
 */
@Composable
fun HoldSelectionActionBar(
    selectedCount: Int,
    onCopyClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onClearSelection: () -> Unit,
    selectionLabel: String = "$selectedCount Selected",
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("hold_selection_action_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.testTag("clear_selection_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit selection mode"
                    )
                }
                Text(
                    text = selectionLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("selection_bar_summary_text")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onCopyClick,
                    modifier = Modifier.testTag("bar_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy selected videos",
                        tint = ElectricCyan
                    )
                }
                IconButton(
                    onClick = onMoveClick,
                    modifier = Modifier.testTag("bar_move_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = "Move selected videos",
                        tint = EmeraldVault
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("bar_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete selected videos",
                        tint = CrimsonAlert
                    )
                }
                IconButton(
                    onClick = onSelectAllClick,
                    modifier = Modifier.testTag("bar_select_all_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = "Select all videos"
                    )
                }
            }
        }
    }
}

/**
 * Bottom Action Sheet displayed immediately when holding (long-pressing) a video or folder.
 * Exposes prominent Delete, Move, Copy, and Move to Private Vault actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldMediaOptionsBottomSheet(
    title: String,
    targetVideos: List<VideoItem>,
    onCopyClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveToRecycleBinClick: () -> Unit = {},
    onMoveToVaultClick: () -> Unit,
    onExactDetailsClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val totalSize = remember(targetVideos) { targetVideos.sumOf { it.sizeBytes } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("hold_options_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${targetVideos.size} item(s) • ${Formatters.formatFileSize(totalSize)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Hold Action Pills: Copy, Move, Recycle Bin, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HoldQuickActionPill(
                    label = "Copy",
                    icon = Icons.Default.ContentCopy,
                    accentColor = ElectricCyan,
                    testTag = "hold_option_copy",
                    onClick = onCopyClick,
                    modifier = Modifier.weight(1f)
                )
                HoldQuickActionPill(
                    label = "Move",
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    accentColor = EmeraldVault,
                    testTag = "hold_option_move",
                    onClick = onMoveClick,
                    modifier = Modifier.weight(1f)
                )
                HoldQuickActionPill(
                    label = "Recycle Bin",
                    icon = Icons.Default.Replay10,
                    accentColor = AmberGold,
                    testTag = "hold_option_recycle_bin",
                    onClick = onMoveToRecycleBinClick,
                    modifier = Modifier.weight(1f)
                )
                HoldQuickActionPill(
                    label = "Delete",
                    icon = Icons.Default.Delete,
                    accentColor = CrimsonAlert,
                    testTag = "hold_option_delete",
                    onClick = onDeleteClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))

            // Detailed Action Rows
            HoldSheetRowItem(
                icon = Icons.Default.ContentCopy,
                title = "Copy to Folder or SD Card",
                subtitle = "Duplicate selected media into any folder or external storage",
                tint = ElectricCyan,
                testTag = "hold_row_copy",
                onClick = onCopyClick
            )
            HoldSheetRowItem(
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                title = "Move to Folder, SD Card, or Vault",
                subtitle = "Transfer media & zero-trace shred original source file",
                tint = EmeraldVault,
                testTag = "hold_row_move",
                onClick = onMoveClick
            )
            HoldSheetRowItem(
                icon = Icons.Default.EnhancedEncryption,
                title = "Move to Private Vault",
                subtitle = "Protect media with secure PIN & biometric lock",
                tint = MaterialTheme.colorScheme.secondary,
                testTag = "hold_row_move_vault",
                onClick = onMoveToVaultClick
            )
            HoldSheetRowItem(
                icon = Icons.Default.Replay10,
                title = "Move to Recycle Bin",
                subtitle = "Safely move to Encrypted Recycle Bin for easy restore later",
                tint = AmberGold,
                testTag = "hold_row_recycle_bin",
                onClick = onMoveToRecycleBinClick
            )
            if (onExactDetailsClick != null) {
                HoldSheetRowItem(
                    icon = Icons.Default.Info,
                    title = "Exact File & Codec Details",
                    subtitle = "Inspect container, resolution, codec, bitrate & file path",
                    tint = ElectricCyan,
                    testTag = "hold_option_exact_details",
                    onClick = onExactDetailsClick
                )
            }
            HoldSheetRowItem(
                icon = Icons.Default.DeleteForever,
                title = "Delete / Zero-Trace Shred",
                subtitle = "Send to Encrypted Recycle Bin or shred permanently",
                tint = CrimsonAlert,
                testTag = "hold_row_delete",
                onClick = onDeleteClick
            )
        }
    }
}

@Composable
private fun HoldQuickActionPill(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = accentColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HoldSheetRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tint.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Dialog for choosing where to Move or Copy held videos:
 * - Private Encrypted Vault (for Move)
 * - Any Internal Storage folder
 * - Any SD Card folder
 * - Or a newly created subfolder
 */
@Composable
fun MoveOrCopyDestinationDialog(
    operation: HoldClipboardOperation,
    targetVideosCount: Int,
    destinations: List<MoveDestinationOption>,
    isSdCardDetected: Boolean = false,
    sdCardStatusLabel: String = "Not Detected",
    onConfirmDestination: (MoveDestinationOption, String) -> Unit,
    onStageToClipboardInstead: () -> Unit,
    onDismiss: () -> Unit
) {
    val isMove = operation == HoldClipboardOperation.MOVE
    val visibleDestinations = remember(destinations, isMove) {
        if (isMove) destinations else destinations.filterNot { it.isPrivateVault }
    }
    var selectedDest by remember(visibleDestinations) {
        mutableStateOf(visibleDestinations.firstOrNull())
    }
    var createNewSubfolder by remember { mutableStateOf(false) }
    var customSubfolderName by remember { mutableStateOf("") }
    var browseAnyFolderMode by remember { mutableStateOf(false) }
    var currentBrowsePath by remember { mutableStateOf("/storage/emulated/0") }

    val browsedSubfolders = remember(currentBrowsePath, browseAnyFolderMode) {
        if (!browseAnyFolderMode) {
            emptyList()
        } else {
            try {
                val dir = java.io.File(currentBrowsePath)
                dir.listFiles()
                    ?.filter { child ->
                        child.isDirectory &&
                            !child.name.startsWith(".") &&
                            !child.name.equals("Android", ignoreCase = true) &&
                            com.cyosplay.player.util.CryptoSecurityManager.isPathAllowedForIsolatedApp(child.absolutePath)
                    }
                    ?.sortedBy { it.name.lowercase() }
                    ?.map { it.name to it.absolutePath }
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("move_copy_destination_dialog"),
        title = {
            Column {
                Text(
                    text = if (isMove) {
                        "Fast Move $targetVideosCount Video(s) Anywhere"
                    } else {
                        "Fast Copy $targetVideosCount Video(s) Anywhere"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zero-copy NIO engine • Permanent MediaStore & device storage sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = if (isSdCardDetected) EmeraldVault.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("destination_sd_card_status_row")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = "SD Card Detection Status",
                            tint = if (isSdCardDetected) EmeraldVault else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SD Card: $sdCardStatusLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSdCardDetected) EmeraldVault else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Mode Switcher: Preset Folders vs Browse Any Device Folder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (!browseAnyFolderMode) ElectricCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { browseAnyFolderMode = false }
                            .testTag("dest_mode_presets")
                    ) {
                        Text(
                            text = "Quick Folders",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (!browseAnyFolderMode) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                        )
                    }
                    Surface(
                        color = if (browseAnyFolderMode) ElectricCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { browseAnyFolderMode = true }
                            .testTag("dest_mode_browse_anywhere")
                    ) {
                        Text(
                            text = "Browse Anywhere",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (browseAnyFolderMode) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!browseAnyFolderMode) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 230.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(visibleDestinations, key = { it.id }) { dest ->
                            val isSelected = selectedDest?.id == dest.id
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDest = dest }
                                    .testTag("dest_item_${dest.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when {
                                            dest.isPrivateVault -> Icons.Default.Lock
                                            dest.isRemovableSd -> Icons.Default.SdStorage
                                            else -> Icons.Default.Folder
                                        },
                                        contentDescription = dest.title,
                                        tint = when {
                                            dest.isPrivateVault -> EmeraldVault
                                            dest.isRemovableSd -> ElectricCyan
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dest.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = dest.subtitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = if (isMove) EmeraldVault.copy(alpha = 0.22f) else ElectricCyan.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedDest = dest
                                                onConfirmDestination(
                                                    dest,
                                                    if (createNewSubfolder) customSubfolderName else ""
                                                )
                                            }
                                            .testTag("one_tap_move_copy_${dest.id}")
                                    ) {
                                        Text(
                                            text = if (isMove) "Move" else "Copy",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isMove) EmeraldVault else ElectricCyan,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Browse Any Folder on Device Storage + Custom Path Input
                    OutlinedTextField(
                        value = currentBrowsePath,
                        onValueChange = { newPath ->
                            currentBrowsePath = newPath
                            selectedDest = MoveDestinationOption(
                                id = "custom_any_path",
                                title = newPath.trimEnd('/').substringAfterLast('/').ifBlank { "Storage" },
                                subtitle = newPath,
                                targetPath = newPath,
                                isPrivateVault = false,
                                isRemovableSd = newPath.contains("sdcard1", ignoreCase = true)
                            )
                        },
                        label = { Text("Destination Folder Path (Anywhere)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_anywhere_path_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val parent = currentBrowsePath.trimEnd('/').substringBeforeLast('/', "/storage/emulated/0")
                                    .ifBlank { "/storage/emulated/0" }
                                currentBrowsePath = parent
                                selectedDest = MoveDestinationOption(
                                    id = "custom_any_path",
                                    title = parent.substringAfterLast('/').ifBlank { "Storage" },
                                    subtitle = parent,
                                    targetPath = parent
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Up Folder (..)")
                        }
                        OutlinedButton(
                            onClick = {
                                val nextRoot = if (currentBrowsePath.startsWith("/storage/sdcard1")) {
                                    "/storage/emulated/0"
                                } else {
                                    "/storage/sdcard1"
                                }
                                currentBrowsePath = nextRoot
                                selectedDest = MoveDestinationOption(
                                    id = "custom_any_path",
                                    title = nextRoot.substringAfterLast('/'),
                                    subtitle = nextRoot,
                                    targetPath = nextRoot,
                                    isRemovableSd = nextRoot.contains("sdcard1")
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (currentBrowsePath.startsWith("/storage/sdcard1")) "Internal" else "SD Card")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (browsedSubfolders.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(browsedSubfolders, key = { it.second }) { (name, path) ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentBrowsePath = path
                                            selectedDest = MoveDestinationOption(
                                                id = "custom_any_path",
                                                title = name,
                                                subtitle = path,
                                                targetPath = path,
                                                isRemovableSd = path.contains("sdcard1")
                                            )
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = name,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedDest?.isPrivateVault != true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { createNewSubfolder = !createNewSubfolder },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = createNewSubfolder,
                            onCheckedChange = { createNewSubfolder = it }
                        )
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Create new subfolder inside destination",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (createNewSubfolder) {
                        OutlinedTextField(
                            value = customSubfolderName,
                            onValueChange = { customSubfolderName = it },
                            label = { Text("New Folder Name or Full Path") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_subfolder_input")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val effectiveDest = if (browseAnyFolderMode) {
                        MoveDestinationOption(
                            id = "custom_any_path",
                            title = currentBrowsePath.trimEnd('/').substringAfterLast('/').ifBlank { "Movies" },
                            subtitle = currentBrowsePath,
                            targetPath = currentBrowsePath,
                            isPrivateVault = false,
                            isRemovableSd = currentBrowsePath.contains("sdcard1", ignoreCase = true)
                        )
                    } else {
                        selectedDest
                    }
                    effectiveDest?.let { dest ->
                        onConfirmDestination(
                            dest,
                            if (createNewSubfolder) customSubfolderName else ""
                        )
                    }
                },
                modifier = Modifier.testTag("confirm_move_copy_button")
            ) {
                Text(if (isMove) "Move Here" else "Copy Here")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onStageToClipboardInstead,
                    modifier = Modifier.testTag("browse_to_paste_button")
                ) {
                    Text("Browse Folder")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

/**
 * Confirmation dialog shown after selecting a destination folder for Copy or Move,
 * confirming permanent Copy/Move in the Android MediaStore and device storage.
 */
@Composable
fun CopyMoveConfirmationDialog(
    pendingRequest: com.cyosplay.player.data.model.PendingTransferRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isMove = pendingRequest.operation == HoldClipboardOperation.MOVE
    val count = pendingRequest.targetVideos.size
    val targetFolderLabel = if (pendingRequest.customNewFolderName.isNotBlank()) {
        "${pendingRequest.destination.title}/${pendingRequest.customNewFolderName}"
    } else {
        pendingRequest.destination.title
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("copy_move_confirmation_dialog"),
        title = {
            Text(
                text = if (isMove) {
                    "Permanently Move $count Video(s)?"
                } else {
                    "Permanently Copy $count Video(s)?"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isMove) {
                        "Confirming will permanently move $count video(s) to \"$targetFolderLabel\" in both Android MediaStore and device/SD storage."
                    } else {
                        "Confirming will permanently copy $count video(s) to \"$targetFolderLabel\" in both Android MediaStore and device/SD storage."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Destination Path: ${pendingRequest.destination.targetPath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_permanent_copy_move_button")
            ) {
                Text(if (isMove) "Confirm Move" else "Confirm Copy")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_permanent_copy_move_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for confirming deletion of held/selected videos (either to the Encrypted Recycle Bin
 * or permanent Zero-Trace Shred from Android MediaStore and device storage).
 */
@Composable
fun DeleteMediaConfirmationDialog(
    targetVideos: List<VideoItem>,
    selectedFoldersCount: Int = 0,
    onConfirmDelete: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var shredZeroTrace by remember { mutableStateOf(true) }
    val titleText = when {
        selectedFoldersCount > 0 && targetVideos.isNotEmpty() ->
            "Permanently Delete $selectedFoldersCount Folder(s) & ${targetVideos.size} Video(s)?"
        selectedFoldersCount > 0 ->
            "Permanently Delete $selectedFoldersCount Selected Folder(s)?"
        else ->
            "Permanently Delete ${targetVideos.size} Video(s)?"
    }
    val bodyText = when {
        selectedFoldersCount > 0 && targetVideos.isNotEmpty() ->
            "Confirming will permanently delete the $selectedFoldersCount selected folder(s) and ${targetVideos.size} video file(s) from the Android MediaStore and device or SD storage:"
        selectedFoldersCount > 0 ->
            "Confirming will permanently delete the $selectedFoldersCount selected folder(s) from the Android MediaStore and device or SD storage:"
        targetVideos.size == 1 ->
            "Confirming will permanently delete \"${targetVideos.first().title}\" from the Android MediaStore and device or SD storage:"
        else ->
            "Confirming will permanently delete the ${targetVideos.size} selected video files from the Android MediaStore and device or SD storage:"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete_confirmation_dialog"),
        title = {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { shredZeroTrace = !shredZeroTrace }
                        .padding(10.dp)
                        .testTag("zero_trace_shred_checkbox_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = shredZeroTrace,
                        onCheckedChange = { shredZeroTrace = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Permanently Delete from MediaStore & Device / SD Card",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CrimsonAlert
                        )
                        Text(
                            text = "Permanently remove from Android MediaStore, internal storage, or SD card with zero-trace shred.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onConfirmDelete(false) },
                    modifier = Modifier.testTag("confirm_move_to_recycle_bin_button")
                ) {
                    Text(
                        text = "Recycle Bin",
                        color = AmberGold,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { onConfirmDelete(shredZeroTrace) },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text(
                        text = if (shredZeroTrace) "Delete Permanently" else "Move to Recycle Bin",
                        color = Color.White
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Floating banner when videos are staged in clipboard for Copy/Move into the currently opened folder.
 */
@Composable
fun ClipboardPasteBanner(
    clipboardState: VideoClipboardState,
    destinationFolderName: String,
    onPasteHere: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMove = clipboardState.operation == HoldClipboardOperation.MOVE
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("clipboard_paste_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${clipboardState.sourceVideos.size} video(s) ready to ${if (isMove) "Move" else "Copy"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Destination: $destinationFolderName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPasteHere,
                    modifier = Modifier.testTag("paste_here_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isMove) "Move Here" else "Paste Copy")
                }
                IconButton(onClick = onCancel) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel clipboard")
                }
            }
        }
    }
}

/**
 * Continue Watching / Last-Resume banner displayed in the file & folder browser
 * whenever the user exits a video with saved playback progress.
 */
@Composable
fun ContinueWatchingResumeBanner(
    video: VideoItem,
    progress: PlaybackProgress,
    onResumeClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = progress.durationMs.coerceAtLeast(video.durationMs.coerceAtLeast(1000L))
    val fraction = (progress.lastPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0.02f, 1f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onResumeClick() }
            .testTag("continue_watching_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.20f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "LAST RESUME",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${Formatters.formatDuration(progress.lastPositionMs)} / ${Formatters.formatDuration(safeDuration)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("continue_watching_timestamp_text")
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${video.folderName} • ${video.resolutionBadge}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onResumeClick,
                        modifier = Modifier.testTag("continue_watching_resume_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume video",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume")
                    }
                    IconButton(
                        onClick = onDismissClick,
                        modifier = Modifier.testTag("continue_watching_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss resume banner"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                color = ElectricCyan,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * Real-time progress banner displayed during large (up to 1000GB) file Move/Copy operations.
 */
@Composable
fun LargeFileTransferProgressBanner(
    progress: com.cyosplay.player.data.model.FileTransferProgress,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMove = progress.operation == com.cyosplay.player.data.model.HoldClipboardOperation.MOVE
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("large_file_transfer_progress_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (isMove) Icons.AutoMirrored.Filled.DriveFileMove else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = if (isMove) EmeraldVault else ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMove) "Moving to ${progress.destinationFolderName}..." else "Copying to ${progress.destinationFolderName}...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = progress.currentFileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (progress.speedMbPerSec > 0.1f) {
                    Surface(
                        color = ElectricCyan.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${progress.speedMbPerSec.toInt()} MB/s",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.progressFraction },
                color = if (isMove) EmeraldVault else ElectricCyan,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${Formatters.formatFileSize(progress.bytesTransferred)} / ${Formatters.formatFileSize(progress.totalBytes)} (${progress.completedFiles}/${progress.totalFiles} files)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onDismiss,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Modal dialog displaying exact technical specifications for a video:
 * exact pixel dimensions, aspect ratio, megapixels, container & MIME type, video codec profile,
 * HDR/Dolby Vision dynamic range, frame rate (FPS), exact bitrate (Mbps & bps), audio codec/channels,
 * sample rate, exact byte size, millisecond duration, storage path, and SHA-256 / AES-256-GCM status.
 */
@Composable
fun VideoExactDetailsSheet(
    details: VideoTechnicalDetails,
    onDismiss: () -> Unit,
    onToggleLock: (() -> Unit)? = null,
    onRequestUnlockDialog: (() -> Unit)? = null
) {
    VideoPropertiesSheet(
        properties = details,
        onDismiss = onDismiss,
        onToggleLock = onToggleLock,
        onRequestUnlockDialog = onRequestUnlockDialog
    )
}

@Composable
private fun TechnicalDetailSectionCard(
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
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            rows.forEach { (label, value) ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Floating Mini-Player view displayed above the bottom navigation tabs so users can keep
 * audio or video playing while browsing their library, folders, playlists, vault, or app settings.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MiniPlayerFloatingBar(
    video: VideoItem,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volumeFraction: Float,
    isMuted: Boolean,
    sizeMode: MiniPlayerSizeMode = MiniPlayerSizeMode.COMPACT,
    positionPreset: com.cyosplay.player.data.model.MiniPlayerPositionPreset = com.cyosplay.player.data.model.MiniPlayerPositionPreset.BOTTOM_CENTER,
    forceHardwareAccelerationHighBitrate: Boolean = false,
    decoderEngineMode: com.cyosplay.player.data.model.DecoderEngineMode = com.cyosplay.player.data.model.DecoderEngineMode.AUTO_HW_PLUS,
    lowRamShieldEnabled: Boolean = false,
    batterySaverModeEnabled: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onSkipDeltaMs: (Long) -> Unit,
    onSeekToMs: (Long) -> Unit = {},
    onProgressTick: (positionMs: Long, durationMs: Long) -> Unit,
    onToggleSizeMode: () -> Unit = {},
    onSelectPositionPreset: (com.cyosplay.player.data.model.MiniPlayerPositionPreset) -> Unit = {},
    onCyclePositionPreset: () -> Unit = {},
    onDragOffsetDelta: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onDragFinished: () -> Unit = {},
    onResetPosition: () -> Unit = {},
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onPlaybackStateSynced: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnDragOffsetDelta by androidx.compose.runtime.rememberUpdatedState(onDragOffsetDelta)
    val currentOnDragFinished by androidx.compose.runtime.rememberUpdatedState(onDragFinished)
    val latestOnPlaybackStateSynced by androidx.compose.runtime.rememberUpdatedState(onPlaybackStateSynced)
    val latestIsPlaying by androidx.compose.runtime.rememberUpdatedState(isPlaying)
    val safeDuration = durationMs.coerceAtLeast(video.durationMs.coerceAtLeast(1000L))
    val safePosition = positionMs.coerceIn(0L, safeDuration)
    var isScrubbingMiniSeek by remember { mutableStateOf(false) }
    var scrubSliderFraction by remember { mutableFloatStateOf(0f) }
    val progressFraction = if (isScrubbingMiniSeek) {
        scrubSliderFraction.coerceIn(0f, 1f)
    } else {
        (safePosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val codecProfileKey = remember(
        decoderEngineMode,
        forceHardwareAccelerationHighBitrate,
        lowRamShieldEnabled,
        batterySaverModeEnabled
    ) {
        com.cyosplay.player.ui.VideoPlayerViewModel.buildCodecProfileKey(
            decoderEngineMode = decoderEngineMode,
            forceHardwareAccelerationHighBitrate = forceHardwareAccelerationHighBitrate,
            lowRamShieldEnabled = lowRamShieldEnabled,
            batterySaverModeEnabled = batterySaverModeEnabled
        )
    }

    val miniPlayer = remember(context, codecProfileKey, video.uri) {
        runCatching {
            val livePlayer = com.cyos.player.CyosPlayerHolder.currentPlayerOrNull()
            if (livePlayer != null && com.cyos.player.CyosPlayerHolder.lastCodecProfileKey() == codecProfileKey) {
                livePlayer
            } else {
                val (codecSelector, extMode) = com.cyosplay.player.ui.VideoPlayerViewModel
                    .resolveCodecSelectorAndExtensionMode(
                        decoderEngineMode = decoderEngineMode,
                        forceHardwareAccelerationHighBitrate = forceHardwareAccelerationHighBitrate
                    )
                com.cyos.player.CyosPlayerHolder.getOrCreateSession(
                    context = context,
                    mediaCodecSelector = codecSelector,
                    extensionRendererMode = extMode,
                    codecProfileKey = codecProfileKey,
                    subtitlesEnabledByUser = false,
                    forceLowRamMode = lowRamShieldEnabled,
                    batterySaverEnabled = batterySaverModeEnabled,
                    isNetworkStream = com.cyos.player.CyosFastPlayer.isNetworkStreamUri(video.uri)
                ).player
            }
        }.getOrNull()
    }

    LaunchedEffect(miniPlayer, video.uri, video.path) {
        val player = miniPlayer ?: return@LaunchedEffect
        val targetUri = video.uri
        val currentUri = runCatching {
            player.currentMediaItem?.localConfiguration?.uri?.toString()
        }.getOrNull()
        val isAlreadyLoaded = currentUri == targetUri.toString() &&
            player.playbackState != androidx.media3.common.Player.STATE_IDLE
        if (!isAlreadyLoaded) {
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(targetUri))
            player.prepare()
            if (safePosition > 0L) {
                player.seekTo(safePosition)
            }
        }
        player.volume = if (isMuted) 0f else 1.0f
        player.playWhenReady = isPlaying
        if (isPlaying && !player.isPlaying) {
            player.play()
        } else if (!isPlaying && player.isPlaying) {
            player.pause()
        }
    }

    LaunchedEffect(miniPlayer, isPlaying) {
        val player = miniPlayer ?: return@LaunchedEffect
        player.playWhenReady = isPlaying
        if (isPlaying) {
            if (!player.isPlaying) player.play()
        } else {
            if (player.isPlaying) player.pause()
        }
    }

    LaunchedEffect(miniPlayer, isMuted) {
        // Use AudioManager STREAM_MUSIC as single volume control; ExoPlayer only handles mute (0f vs 1f)
        miniPlayer?.volume = if (isMuted) 0f else 1.0f
    }

    val latestOnProgressTick by rememberUpdatedState(onProgressTick)
    LaunchedEffect(miniPlayer, isPlaying, isScrubbingMiniSeek) {
        val player = miniPlayer ?: return@LaunchedEffect
        while (isPlaying && !isScrubbingMiniSeek) {
            // 1200ms low-CPU / low-battery cadence while browsing library or settings
            kotlinx.coroutines.delay(1200L)
            val curPos = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
            val curDur = runCatching { player.duration.takeIf { it > 0L } }.getOrNull() ?: safeDuration
            if (curPos > 0L) {
                latestOnProgressTick(curPos, curDur)
            }
        }
    }

    DisposableEffect(miniPlayer) {
        com.cyos.player.CyosPlayerHolder.markPlayerAttached()
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playingNow: Boolean) {
                val player = miniPlayer ?: return
                val state = player.playbackState
                if (state == androidx.media3.common.Player.STATE_READY ||
                    state == androidx.media3.common.Player.STATE_ENDED
                ) {
                    if (playingNow != latestIsPlaying) {
                        latestOnPlaybackStateSynced(playingNow)
                    }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && latestIsPlaying) {
                    latestOnPlaybackStateSynced(false)
                } else if (playWhenReady && !latestIsPlaying) {
                    latestOnPlaybackStateSynced(true)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    latestOnPlaybackStateSynced(false)
                    val curDur = runCatching { miniPlayer?.duration?.takeIf { it > 0L } }.getOrNull() ?: safeDuration
                    latestOnProgressTick(curDur, curDur)
                }
            }
        }
        miniPlayer?.addListener(listener)
        onDispose {
            miniPlayer?.removeListener(listener)
            // Do NOT release the shared pooled CyosPlayerHolder instance here so expanding to fullscreen is instant and gapless.
            com.cyos.player.CyosPlayerHolder.markPlayerDetached()
        }
    }

    val displayPositionMs = if (isScrubbingMiniSeek) {
        (scrubSliderFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
    } else {
        safePosition
    }

    val boundedWidthModifier = if (sizeMode == MiniPlayerSizeMode.MEDIUM) {
        Modifier.widthIn(min = 280.dp, max = 340.dp)
    } else {
        Modifier.widthIn(min = 260.dp, max = 320.dp)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = modifier
            .then(boundedWidthModifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { currentOnDragFinished() },
                    onDragCancel = { currentOnDragFinished() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDragOffsetDelta(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .testTag("mini_player_floating_bar")
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            // Top Handle for Resetting Mini-Player Position (parent Card handles dragging cleanly)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onResetPosition)
                    .padding(vertical = 4.dp)
                    .testTag("mini_player_drag_handle"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ElectricCyan.copy(alpha = 0.65f))
                )
            }
            if (sizeMode == MiniPlayerSizeMode.MEDIUM) {
                // ================= MEDIUM SCREEN MODE =================
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("mini_player_title_text")
                        )
                        Text(
                            text = "${Formatters.formatDuration(displayPositionMs)} / ${Formatters.formatDuration(safeDuration)} • ${video.videoCodecHint}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("mini_player_subtitle_text")
                        )
                    }

                    // Mode Toggle (Medium <-> Compact)
                    IconButton(
                        onClick = onToggleSizeMode,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("mini_player_size_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Switch to Compact mode",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Expand to Fullscreen
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("mini_player_expand_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Expand to full screen player",
                            tint = EmeraldVault,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Close Mini Player
                    IconButton(
                        onClick = {
                            runCatching { miniPlayer?.pause() }
                            com.cyos.player.CyosPlayerHolder.pauseAndDetachIfPooled()
                            onClose()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("mini_player_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close mini-player",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Medium 16:9 Video Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .clickable(onClick = onExpand)
                        .testTag("mini_player_video_preview"),
                    contentAlignment = Alignment.Center
                ) {
                    if (miniPlayer != null) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                val texturePlayerView = runCatching {
                                    android.view.LayoutInflater.from(ctx).inflate(
                                        com.cyosplay.player.R.layout.view_mini_texture_player,
                                        null,
                                        false
                                    ) as? androidx.media3.ui.PlayerView
                                }.getOrNull() ?: androidx.media3.ui.PlayerView(ctx)
                                texturePlayerView.apply {
                                    useController = false
                                    player = miniPlayer
                                }
                            },
                            update = { view ->
                                if (view.player !== miniPlayer) {
                                    view.player = miniPlayer
                                }
                            },
                            onRelease = { view ->
                                view.player = null
                            },
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = video.resolutionBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Transport Controls Row in Medium Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val target = (safePosition - 10_000L).coerceIn(0L, safeDuration)
                                miniPlayer?.seekTo(target)
                                onSkipDeltaMs(-10_000L)
                            },
                            modifier = Modifier.size(36.dp).testTag("mini_player_rewind_10_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10 seconds",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(38.dp).testTag("mini_player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = ElectricCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val target = (safePosition + 10_000L).coerceIn(0L, safeDuration)
                                miniPlayer?.seekTo(target)
                                onSkipDeltaMs(10_000L)
                            },
                            modifier = Modifier.size(36.dp).testTag("mini_player_forward_10_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10 seconds",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = "${Formatters.formatDuration(displayPositionMs)} / ${Formatters.formatDuration(safeDuration)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Quick Position Preset Selector Row (Viewport-Adaptive Dock Targets)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quickPresets = remember {
                        listOf(
                            com.cyosplay.player.data.model.MiniPlayerPositionPreset.BOTTOM_LEFT,
                            com.cyosplay.player.data.model.MiniPlayerPositionPreset.BOTTOM_CENTER,
                            com.cyosplay.player.data.model.MiniPlayerPositionPreset.BOTTOM_RIGHT,
                            com.cyosplay.player.data.model.MiniPlayerPositionPreset.TOP_LEFT,
                            com.cyosplay.player.data.model.MiniPlayerPositionPreset.TOP_RIGHT
                        )
                    }
                    quickPresets.forEach { preset ->
                        val isSelected = positionPreset == preset
                        Surface(
                            color = if (isSelected) ElectricCyan.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectPositionPreset(preset) }
                                .testTag("mini_player_preset_${preset.name.lowercase()}")
                        ) {
                            Text(
                                text = preset.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

            } else {
                // ================= COMPACT MINI-BAR MODE =================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExpand)
                        .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Live Video Preview Box (compact 16:9 frame)
                    Box(
                        modifier = Modifier
                            .width(88.dp)
                            .height(50.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .testTag("mini_player_video_preview"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (miniPlayer != null) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    val texturePlayerView = runCatching {
                                        android.view.LayoutInflater.from(ctx).inflate(
                                            com.cyosplay.player.R.layout.view_mini_texture_player,
                                            null,
                                            false
                                        ) as? androidx.media3.ui.PlayerView
                                    }.getOrNull() ?: androidx.media3.ui.PlayerView(ctx)
                                    texturePlayerView.apply {
                                        useController = false
                                        player = miniPlayer
                                    }
                                },
                                update = { view ->
                                    if (view.player !== miniPlayer) {
                                        view.player = miniPlayer
                                    }
                                },
                                onRelease = { view ->
                                    view.player = null
                                },
                                modifier = Modifier.matchParentSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Surface(
                            color = Color.Black.copy(alpha = 0.68f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                        ) {
                            Text(
                                text = video.resolutionBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("mini_player_title_text")
                        )
                        Text(
                            text = "${Formatters.formatDuration(displayPositionMs)} / ${Formatters.formatDuration(safeDuration)} • ${video.videoCodecHint}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("mini_player_subtitle_text")
                        )
                    }

                    IconButton(
                        onClick = {
                            val target = (safePosition - 10_000L).coerceIn(0L, safeDuration)
                            miniPlayer?.seekTo(target)
                            onSkipDeltaMs(-10_000L)
                        },
                        modifier = Modifier.size(34.dp).testTag("mini_player_rewind_10_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(36.dp).testTag("mini_player_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause mini-player" else "Play mini-player",
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val target = (safePosition + 10_000L).coerceIn(0L, safeDuration)
                            miniPlayer?.seekTo(target)
                            onSkipDeltaMs(10_000L)
                        },
                        modifier = Modifier.size(34.dp).testTag("mini_player_forward_10_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onCyclePositionPreset,
                        modifier = Modifier.size(34.dp).testTag("mini_player_position_preset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenWith,
                            contentDescription = "Cycle mini-player dock position (${positionPreset.label})",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleSizeMode,
                        modifier = Modifier.size(34.dp).testTag("mini_player_size_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Switch to Medium mode",
                            tint = ElectricCyan,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(34.dp).testTag("mini_player_expand_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Expand to full screen player",
                            tint = EmeraldVault,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            runCatching { miniPlayer?.pause() }
                            com.cyos.player.CyosPlayerHolder.pauseAndDetachIfPooled()
                            onClose()
                        },
                        modifier = Modifier.size(34.dp).testTag("mini_player_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close mini-player",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Interactive Seek Slider in Docked Mini/Medium Player Bar
            Slider(
                value = progressFraction,
                onValueChange = { fraction ->
                    isScrubbingMiniSeek = true
                    scrubSliderFraction = fraction.coerceIn(0f, 1f)
                },
                onValueChangeFinished = {
                    val targetMs = (scrubSliderFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                    isScrubbingMiniSeek = false
                    miniPlayer?.seekTo(targetMs)
                    onSeekToMs(targetMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .padding(horizontal = 6.dp)
                    .testTag("mini_player_seek_slider")
            )
        }
    }
}



