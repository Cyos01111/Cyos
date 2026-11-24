package com.cyosplay.player

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyosplay.player.data.model.HoldClipboardOperation
import com.cyosplay.player.data.model.NavDestination
import com.cyosplay.player.ui.Screen
import com.cyosplay.player.ui.VideoPlayerUiState
import com.cyosplay.player.ui.VideoPlayerViewModel
import com.cyosplay.player.ui.components.CopyMoveConfirmationDialog
import com.cyosplay.player.ui.components.DeleteMediaConfirmationDialog
import com.cyosplay.player.ui.components.HoldMediaOptionsBottomSheet
import com.cyosplay.player.ui.components.MasterPasswordLockDialog
import com.cyosplay.player.ui.components.MiniPlayerFloatingBar
import com.cyosplay.player.ui.components.MoveOrCopyDestinationDialog
import com.cyosplay.player.ui.components.VideoExactDetailsSheet
import com.cyosplay.player.ui.components.VideoPropertiesSheet
import com.cyosplay.player.ui.components.VisualComfortBottomSheet
import com.cyosplay.player.ui.components.VisualComfortScreenOverlay
import com.cyosplay.player.ui.screens.FolderDetailScreen
import com.cyosplay.player.ui.screens.FolderListScreen
import com.cyosplay.player.ui.screens.MediaManagerScreen
import com.cyosplay.player.ui.screens.PlaylistsScreen
import com.cyosplay.player.ui.screens.RecycleBinScreen
import com.cyosplay.player.ui.screens.SecurityQuestionScreen
import com.cyosplay.player.ui.screens.SettingsScreen
import com.cyosplay.player.ui.screens.SubtitleStudioScreen
import com.cyosplay.player.ui.screens.VaultScreen
import com.cyosplay.player.ui.screens.VideoPlayerScreen
import com.cyosplay.player.ui.theme.CyosPlayTheme
import com.cyosplay.player.util.MemoryManager
import kotlinx.coroutines.delay

open class MainActivity : FragmentActivity() {

    private val viewModel: VideoPlayerViewModel by viewModels()

    private var pendingVideoUri: Uri? = null
    private var pendingTargetFolder: String? = null
    private var pendingMoveUri: Uri?
        get() = pendingVideoUri
        set(value) { pendingVideoUri = value }
    private var pendingTargetRelativePath: String?
        get() = pendingTargetFolder
        set(value) { pendingTargetFolder = value }
    private var pendingDeleteVideoUri: Uri? = null

    private val deleteVideoLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Complete any Android 10 (API 29) RecoverableSecurityException pending deletes
            com.cyosplay.player.util.MediaStoreFileHelper.completePendingRecoverableDeletes(this)
            val deletedUri = pendingDeleteVideoUri
            pendingDeleteVideoUri = null
            if (viewModel.uiState.value.pendingRecycleBinDeleteIds.isNotEmpty()) {
                viewModel.onRecycleBinMediaStoreDeleteConfirmed()
            } else if (deletedUri != null) {
                viewModel.onMediaStoreVideoDeleted(deletedUri)
            } else {
                viewModel.confirmDeleteVideos(permanentZeroTraceShred = true)
            }
        } else {
            // The user clicked Cancel/Deny on the system dialog — keep media entry and show message
            com.cyosplay.player.util.MediaStoreFileHelper.clearPendingRecoverableDeletes()
            pendingDeleteVideoUri = null
            viewModel.onDeletePermissionDenied()
        }
    }

    val videoDeleteController = com.cyos.player.CyosVideoDeleteController(
        activity = this,
        exoPlayerProvider = { com.cyos.player.CyosPlayerMediaStoreDeleter.activeExoPlayerInstance },
        onVideosDeletedFromMediaStore = { deletedUris, deletedPaths ->
            viewModel.onVideosDeletedFromMediaStore(deletedUris, deletedPaths)
        },
        onDeleteCancelledOrFailed = { _ ->
            viewModel.onDeletePermissionDenied()
        }
    )

    fun onDeleteVideoClicked(videoUri: Uri, videoPath: String) {
        lifecycleScope.launch {
            videoDeleteController.deleteVideos(
                videoUris = listOf(videoUri),
                filePaths = listOf(videoPath)
            )
        }
    }

    fun deleteVideoOnPlayer(videoUri: Uri) {
        pendingDeleteVideoUri = videoUri
        viewModel.releasePlayerHandleIfMatching(videoUri)
        com.cyosplay.player.util.deleteVideoFromMediaStore(
            context = this,
            videoUri = videoUri,
            intentSenderLauncher = deleteVideoLauncher,
            onImmediateDeleteSuccess = {
                pendingDeleteVideoUri = null
                viewModel.onMediaStoreVideoDeleted(videoUri)
            },
            onDeleteFailedOrDenied = {
                pendingDeleteVideoUri = null
                viewModel.onDeletePermissionDenied()
            }
        )
    }

    fun deleteVideoById(videoId: Long) {
        val videoUri = com.cyosplay.player.util.buildVideoContentUri(videoId)
        deleteVideoOnPlayer(videoUri)
    }

    // 1. Register once in your Activity to handle Android 10/11+ write permission
    private val moveVideoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = pendingMoveUri ?: viewModel.uiState.value.pendingVideoUri ?: return@registerForActivityResult
            val folder = pendingTargetRelativePath ?: viewModel.uiState.value.pendingTargetFolder ?: return@registerForActivityResult
            pendingMoveUri = null
            pendingTargetRelativePath = null
            moveVideoOnPlayer(uri, folder)
        } else {
            pendingMoveUri = null
            pendingTargetRelativePath = null
            viewModel.onMoveVideoPermissionResult(false)
        }
    }

    // 2. Call this when the user moves a video to another folder
    fun moveVideoOnPlayer(videoUri: Uri, targetFolderRelativePath: String) {
        lifecycleScope.launch {
            // Stop player if the video being moved is currently loaded in ExoPlayer or Mini-Player
            viewModel.releasePlayerHandleIfMatching(videoUri)

            // Move the video in MediaStore / storage
            when (
                val result = viewModel.moveVideoViaVideoPlayerFileMover(
                    videoUri = videoUri,
                    targetFolderRelativePath = targetFolderRelativePath
                )
            ) {
                is com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.Success -> {
                    // Handled by moveVideoViaVideoPlayerFileMover (relinks state + immediate removal + refreshLibrary)
                }
                is com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.NeedsPermission -> {
                    // Ask user for permission BEFORE copying (prevents duplicate video in old folder)
                    pendingMoveUri = result.videoUri
                    pendingTargetRelativePath = result.targetRelativePath
                    moveVideoPermissionLauncher.launch(
                        IntentSenderRequest.Builder(result.intentSender).build()
                    )
                }
                is com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.Error -> {
                    // Error banner already updated in ViewModel
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.cyosplay.player.util.CryptoSecurityManager.enforcePermanentNonDebugMode(this)
        enableEdgeToEdge()

        // Stealth Playback Privacy: Prevent recent-apps screenshots & screen recording of what video is played
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        handleIncomingVideoIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Toggle FLAG_SECURE dynamically based on Stealth Playback or Private Vault session
            LaunchedEffect(uiState.stealthPlaybackEnabled, uiState.currentNavTab, uiState.currentScreen) {
                val shouldSecureWindow = uiState.stealthPlaybackEnabled ||
                    uiState.currentNavTab == NavDestination.VAULT ||
                    uiState.currentScreen is Screen.Player
                if (shouldSecureWindow) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Request version-appropriate Media permissions gracefully on launch (Android 7 to Android 17 / API 24-37)
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionsMap ->
                if (permissionsMap.values.any { it }) {
                    viewModel.startObservingMediaStoreVideos()
                    viewModel.refreshLibrary()
                }
            }

            LaunchedEffect(Unit) {
                val permissionsToRequest = com.cyosplay.player.util.AndroidVersionCompatHelper
                    .resolveRuntimeMediaPermissions(Build.VERSION.SDK_INT)
                try {
                    permissionLauncher.launch(permissionsToRequest)
                } catch (_: Exception) {
                    // Ignore in headless Robolectric tests
                }
            }

            CyosPlayTheme(
                themeMode = uiState.themeMode,
                fontMode = uiState.fontMode,
                textScaleMode = uiState.textScaleMode
            ) {
                CyosPlayRootApp(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingVideoIntent(intent)
    }

    private fun handleIncomingVideoIntent(incomingIntent: android.content.Intent?) {
        if (incomingIntent == null) return
        val action = incomingIntent.action
        val dataUri = incomingIntent.data ?: incomingIntent.clipData?.getItemAt(0)?.uri
        if ((action == android.content.Intent.ACTION_VIEW || action == android.content.Intent.ACTION_SEND) && dataUri != null) {
            viewModel.importAndPlayLocalVideoUri(dataUri, saveFolderInApp = false)
        }
    }

    /**
     * Enters Picture-in-Picture (PiP) mode so the user can continue watching the video
     * in a floating window while using other applications.
     */
    fun enterVideoPipMode(videoWidth: Int = 1920, videoHeight: Int = 1080): Boolean {
        if (!com.cyosplay.player.util.AndroidVersionCompatHelper.canEnterPictureInPicture(Build.VERSION.SDK_INT)) {
            return false
        }
        return try {
            val (num, den) = com.cyosplay.player.util.AndroidVersionCompatHelper
                .clampPipAspectRatioRational(videoWidth, videoHeight)
            val rational = Rational(num, den)
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
            if (com.cyosplay.player.util.AndroidVersionCompatHelper.supportsSeamlessAutoPip(Build.VERSION.SDK_INT)) {
                builder.setAutoEnterEnabled(viewModel.uiState.value.autoPipEnabled)
                builder.setSeamlessResizeEnabled(true)
            }
            val entered = enterPictureInPictureMode(builder.build())
            if (entered) {
                viewModel.setInPipMode(true)
            }
            entered
        } catch (_: Throwable) {
            false
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = viewModel.uiState.value
        if (state.currentScreen is Screen.Player && state.isPlayerCurrentlyPlaying && state.autoPipEnabled) {
            enterVideoPipMode(state.activeVideoWidth, state.activeVideoHeight)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setInPipMode(isInPictureInPictureMode)
    }

    override fun onStart() {
        super.onStart()
        viewModel.startObservingMediaStoreVideos()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopObservingMediaStoreVideos()
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        if (!inPip) {
            viewModel.onAppBackgrounded()
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            MemoryManager.performEmergencyMemoryTrim(this)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        MemoryManager.performEmergencyMemoryTrim(this)
    }
}

class cyosplay : MainActivity()

@Composable
fun CyosPlayRootApp(
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isFullScreenPlayer = uiState.currentScreen is Screen.Player

    val mediaStoreDeleteLauncher = com.cyosplay.player.util.MediaStoreFileHelper.rememberMediaStoreDeleteLauncher(
        onDeleteSuccess = {
            if (viewModel.uiState.value.pendingRecycleBinDeleteIds.isNotEmpty()) {
                viewModel.onRecycleBinMediaStoreDeleteConfirmed()
            } else {
                viewModel.confirmDeleteVideos(permanentZeroTraceShred = true)
            }
        },
        onDeleteCancelled = {
            viewModel.onDeletePermissionDenied()
        }
    )

    val composeMoveVideoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onMoveVideoPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.pendingDeleteIntentSender) {
        val pendingSender = uiState.pendingDeleteIntentSender
        if (pendingSender != null) {
            try {
                mediaStoreDeleteLauncher.launch(
                    IntentSenderRequest.Builder(pendingSender).build()
                )
            } catch (_: Exception) {
                viewModel.onDeletePermissionDenied()
            }
        }
    }

    LaunchedEffect(uiState.pendingMovePermissionRequest) {
        val pendingReq = uiState.pendingMovePermissionRequest
        if (pendingReq != null) {
            try {
                composeMoveVideoPermissionLauncher.launch(
                    IntentSenderRequest.Builder(pendingReq.intentSender).build()
                )
            } catch (_: Exception) {
                viewModel.onMoveVideoPermissionResult(false)
            }
        }
    }

    // Auto-clear status toast banner after 3.5 seconds
    LaunchedEffect(uiState.statusBannerMessage) {
        if (uiState.statusBannerMessage != null) {
            delay(3500L)
            viewModel.clearStatusBanner()
        }
    }

    // Post-first-frame cold-launch ExoPlayer warm-up strictly on 3 GB+ RAM devices (1-2 GB stays lazy)
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        viewModel.warmUpPlayerAfterFirstFrame()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!isFullScreenPlayer && uiState.currentScreen == Screen.Home) {
                    NavigationBar(modifier = Modifier.testTag("main_bottom_navigation")) {
                        NavDestination.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = uiState.currentNavTab == tab,
                                onClick = { viewModel.selectNavTab(tab) },
                                icon = {
                                    val icon = when (tab) {
                                        NavDestination.VIDEOS -> Icons.Default.VideoLibrary
                                        NavDestination.FOLDERS -> Icons.Default.Folder
                                        NavDestination.PLAYLISTS -> Icons.AutoMirrored.Filled.PlaylistPlay
                                        NavDestination.VAULT -> Icons.Default.Lock
                                        NavDestination.SETTINGS -> Icons.Default.Settings
                                    }
                                    com.cyosplay.player.ui.components.NotificationDotAnchor(
                                        showDot = tab == NavDestination.FOLDERS && uiState.hasNewFolders,
                                        dotSize = 8.dp,
                                        dotOffsetX = 3.dp,
                                        dotOffsetY = (-3).dp,
                                        dotTag = "folders_tab_new_dot",
                                        dotContentDescription = "New video folders available"
                                    ) {
                                        Icon(imageVector = icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isFullScreenPlayer) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
            ) {
                when (val screen = uiState.currentScreen) {
                    is Screen.Home -> {
                        when (uiState.currentNavTab) {
                            NavDestination.VIDEOS, NavDestination.FOLDERS -> {
                                FolderListScreen(
                                    uiState = uiState,
                                    viewModel = viewModel
                                )
                            }
                            NavDestination.PLAYLISTS -> {
                                PlaylistsScreen(
                                    uiState = uiState,
                                    viewModel = viewModel
                                )
                            }
                            NavDestination.VAULT -> {
                                VaultScreen(
                                    uiState = uiState,
                                    viewModel = viewModel
                                )
                            }
                            NavDestination.SETTINGS -> {
                                SettingsScreen(
                                    uiState = uiState,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                    is Screen.FolderDetail -> {
                        FolderDetailScreen(
                            folderId = screen.folderId,
                            folderName = screen.folderName,
                            folderPath = screen.folderPath,
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    is Screen.Player -> {
                        VideoPlayerScreen(
                            video = screen.video,
                            playlistQueue = screen.playlistQueue,
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    is Screen.RecycleBin -> {
                        RecycleBinScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    is Screen.MediaManager -> {
                        MediaManagerScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    is Screen.SubtitleStudio -> {
                        SubtitleStudioScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    is Screen.SecurityQuestionSetup -> {
                        SecurityQuestionScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }

                // Global Hold (Long-Press) Options Bottom Sheet: Copy, Move, Delete, Move to Vault, Exact Details
                if (uiState.showHoldActionSheet && uiState.holdContextVideos.isNotEmpty()) {
                    HoldMediaOptionsBottomSheet(
                        title = uiState.holdContextTitle,
                        targetVideos = uiState.holdContextVideos,
                        onCopyClick = { viewModel.startCopyOrMoveFlow(HoldClipboardOperation.COPY) },
                        onMoveClick = { viewModel.startCopyOrMoveFlow(HoldClipboardOperation.MOVE) },
                        onDeleteClick = { viewModel.openDeleteOrRecycleDialog() },
                        onMoveToRecycleBinClick = { viewModel.moveHeldVideosToRecycleBin() },
                        onMoveToVaultClick = { viewModel.moveVideosDirectlyToPrivateVault() },
                        onExactDetailsClick = if (uiState.holdContextVideos.size == 1) {
                            { viewModel.inspectVideoProperties(uiState.holdContextVideos.first()) }
                        } else null,
                        onDismiss = { viewModel.dismissHoldActionSheet() }
                    )
                }

                // Global Exact Video Properties Sheet & Technical Inspector
                uiState.inspectedVideoDetails?.let { properties ->
                    VideoPropertiesSheet(
                        properties = properties,
                        onDismiss = { viewModel.dismissVideoProperties() },
                        onToggleLock = { viewModel.lockDataCodesWithMasterPassword() },
                        onRequestUnlockDialog = { viewModel.toggleMasterPasswordLockDialog(true) }
                    )
                }

                // Master Password Unlock Dialog
                if (uiState.showMasterPasswordLockDialog) {
                    MasterPasswordLockDialog(
                        isOpen = true,
                        errorMessage = uiState.masterPasswordLockErrorMessage,
                        onDismiss = { viewModel.toggleMasterPasswordLockDialog(false) },
                        onUnlock = { candidatePassword ->
                            viewModel.unlockDataCodesWithMasterPassword(candidatePassword)
                        }
                    )
                }

                // Global Destination Picker Dialog for Copy & Move (Any Folder, SD Card, or Private Vault)
                if (uiState.showDestinationPickerDialog) {
                    MoveOrCopyDestinationDialog(
                        operation = uiState.pendingHoldOperation,
                        targetVideosCount = uiState.holdContextVideos.size,
                        destinations = uiState.availableMoveDestinations,
                        isSdCardDetected = uiState.isSdCardDetected,
                        sdCardStatusLabel = uiState.sdCardStatusLabel,
                        onConfirmDestination = { destination, customSubfolder ->
                            viewModel.executeCopyOrMoveToDestination(destination, customSubfolder)
                        },
                        onStageToClipboardInstead = {
                            viewModel.stageVideosToClipboard(uiState.pendingHoldOperation)
                        },
                        onDismiss = { viewModel.dismissDestinationPickerDialog() }
                    )
                }

                // Global Copy / Move Permanent Confirmation Dialog
                uiState.pendingTransferOperation?.let { pendingRequest ->
                    CopyMoveConfirmationDialog(
                        pendingRequest = pendingRequest,
                        onConfirm = { viewModel.confirmPendingTransferOperation() },
                        onDismiss = { viewModel.dismissPendingTransferOperation() }
                    )
                }

                // Global Delete / Zero-Trace Shred / Recycle Bin Confirmation Dialog
                if (uiState.showDeleteConfirmDialog) {
                    DeleteMediaConfirmationDialog(
                        targetVideos = uiState.holdContextVideos,
                        selectedFoldersCount = uiState.selectedFolderPaths.size,
                        onConfirmDelete = { shredPermanently ->
                            if (!shredPermanently) {
                                viewModel.moveHeldVideosToRecycleBin()
                            } else {
                                val targetUris = viewModel.collectAllTargetDeleteUris()
                                com.cyosplay.player.util.MediaStoreFileHelper.deleteMediaFiles(
                                    context = context,
                                    uris = targetUris,
                                    intentSenderLauncher = mediaStoreDeleteLauncher,
                                    onDeleteSuccess = {
                                        viewModel.confirmDeleteVideos(true)
                                    },
                                    onDeleteCancelled = {
                                        viewModel.onDeletePermissionDenied()
                                    }
                                )
                            }
                        },
                        onDismiss = { viewModel.dismissDeleteConfirmDialog() }
                    )
                }

                // Visual Comfort, Unified Night Mode & Battery Saver Bottom Sheet
                if (uiState.showVisualComfortSheet) {
                    VisualComfortBottomSheet(
                        nightModeEnabled = uiState.nightModeEnabled,
                        nightModeAudioEnabled = uiState.nightModeAudioEnabled,
                        eyeComfortEnabled = uiState.eyeComfortEnabled,
                        warmthIntensity = uiState.eyeComfortWarmth,
                        extraDimIntensity = uiState.extraDimIntensity,
                        batterySaverEnabled = uiState.batterySaverModeEnabled,
                        onToggleNightMode = { viewModel.setNightModeEnabled(it) },
                        onToggleNightModeAudio = { viewModel.toggleNightModeAudio() },
                        onToggleEyeComfort = { viewModel.setEyeComfortEnabled(it) },
                        onWarmthChange = { viewModel.setEyeComfortWarmth(it) },
                        onExtraDimChange = { viewModel.setExtraDimIntensity(it) },
                        onToggleBatterySaver = { viewModel.setBatterySaverModeEnabled(it) },
                        onDismiss = { viewModel.toggleVisualComfortSheet(false) }
                    )
                }

                // Status Feedback Banner
                uiState.statusBannerMessage?.let { msg ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .testTag("status_feedback_snackbar"),
                        action = {
                            TextButton(onClick = { viewModel.clearStatusBanner() }) {
                                Text("OK")
                            }
                        }
                    ) {
                        Text(msg)
                    }
                }
            }
        }

        // Item 5: Freely Draggable Floating Mini-Player Overlay + Quick Position Presets
        if (!isFullScreenPlayer) {
            uiState.miniPlayerVideo?.let { miniVideo ->
                val folderKey = miniVideo.folderPath.ifBlank { miniVideo.folderId }
                val savedVol = viewModel.getPlayerVolumeForVideo(miniVideo.path, folderKey)
                val bottomNavOffset = if (uiState.currentScreen == Screen.Home) 84.dp else 16.dp
                MiniPlayerFloatingBar(
                    video = miniVideo,
                    isPlaying = uiState.isMiniPlayerPlaying,
                    positionMs = uiState.miniPlayerPositionMs,
                    durationMs = uiState.miniPlayerDurationMs,
                    volumeFraction = savedVol,
                    isMuted = uiState.isAudioMuted,
                    sizeMode = uiState.miniPlayerSizeMode,
                    positionPreset = uiState.miniPlayerPositionPreset,
                    forceHardwareAccelerationHighBitrate = uiState.forceHardwareAccelerationHighBitrate,
                    onTogglePlayPause = { viewModel.toggleMiniPlayerPlayPause() },
                    onSkipDeltaMs = { delta -> viewModel.skipMiniPlayer(delta) },
                    onSeekToMs = { pos -> viewModel.seekMiniPlayerTo(pos) },
                    onProgressTick = { pos, dur -> viewModel.updateMiniPlayerProgress(pos, dur) },
                    onToggleSizeMode = { viewModel.toggleMiniPlayerSizeMode() },
                    onSelectPositionPreset = { preset -> viewModel.setMiniPlayerPositionPreset(preset) },
                    onCyclePositionPreset = { viewModel.cycleMiniPlayerPositionPreset() },
                    onDragOffsetDelta = { dx, dy ->
                        viewModel.dragMiniPlayerByDelta(dx, dy)
                    },
                    onDragFinished = { viewModel.persistMiniPlayerOffset() },
                    onResetPosition = { viewModel.resetMiniPlayerOffset() },
                    onExpand = { viewModel.expandMiniPlayer() },
                    onClose = { viewModel.closeMiniPlayer() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomNavOffset)
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                x = kotlin.math.round(uiState.miniPlayerOffsetX).toInt(),
                                y = kotlin.math.round(uiState.miniPlayerOffsetY).toInt()
                            )
                        }
                )
            }
        }

        // Global Eye Comfort & Extra Dim Overlay
        VisualComfortScreenOverlay(
            eyeComfortEnabled = uiState.eyeComfortEnabled,
            nightModeEnabled = uiState.nightModeEnabled,
            warmthIntensity = uiState.nightShieldWarmthLevel.coerceAtLeast(uiState.eyeComfortWarmth),
            extraDimIntensity = uiState.extraDimIntensity
        )
    }
}
