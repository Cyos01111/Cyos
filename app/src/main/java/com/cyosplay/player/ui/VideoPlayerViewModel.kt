package com.cyosplay.player.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileOutputStream
import com.cyosplay.player.data.model.AdaptiveTextScaleMode
import com.cyosplay.player.data.model.AdvancedCodecFilter
import com.cyosplay.player.data.model.AppFontMode
import com.cyosplay.player.data.model.AppThemeMode
import com.cyosplay.player.data.model.AspectRatioMode
import com.cyosplay.player.data.model.AudioTrackInfo
import com.cyosplay.player.data.model.BrowserControlTab
import com.cyosplay.player.data.model.BrowserLayoutColumns
import com.cyosplay.player.data.model.BrowserThumbnailRatio
import com.cyosplay.player.data.model.BrowserViewMode
import com.cyosplay.player.data.model.BrowserVisibleFields
import com.cyosplay.player.data.model.DecoderEngineMode
import com.cyosplay.player.data.model.FolderBrowserConfig
import com.cyosplay.player.data.model.FolderExplorerMode
import com.cyosplay.player.data.model.FolderSortOption
import com.cyosplay.player.data.model.HoldClipboardOperation
import com.cyosplay.player.data.model.MediaManagerFilter
import com.cyosplay.player.data.model.MoveDestinationOption
import com.cyosplay.player.data.model.NavDestination
import com.cyosplay.player.data.model.PendingTransferRequest
import com.cyosplay.player.data.model.PlaybackProgress
import com.cyosplay.player.data.model.PlayerOrientationMode
import com.cyosplay.player.data.model.Playlist
import com.cyosplay.player.data.model.RecycleBinItem
import com.cyosplay.player.data.model.RepeatMode
import com.cyosplay.player.data.model.ResolutionFilterTier
import com.cyosplay.player.data.model.SleepTimerOption
import com.cyosplay.player.data.model.SmartEnhancementMode
import com.cyosplay.player.data.model.SortDirection
import com.cyosplay.player.data.model.StorageDirectoryNode
import com.cyosplay.player.data.model.StorageVolumeFilter
import com.cyosplay.player.data.model.StorageVolumeInfo
import com.cyosplay.player.data.model.SubtitleColorOption
import com.cyosplay.player.data.model.SubtitleCueItem
import com.cyosplay.player.data.model.SubtitleEdgeStyle
import com.cyosplay.player.data.model.SubtitleFontSize
import com.cyosplay.player.data.model.SubtitleOutlineColorOption
import com.cyosplay.player.data.model.SubtitleTrackInfo
import com.cyosplay.player.data.model.SubtitleVerticalPreset
import com.cyosplay.player.data.model.VaultAuthState
import com.cyosplay.player.data.model.VaultVideoEntry
import com.cyosplay.player.data.model.VideoClipboardState
import com.cyosplay.player.data.model.VideoFolder
import com.cyosplay.player.data.model.VideoItem
import com.cyosplay.player.data.model.VideoSortOption
import com.cyosplay.player.data.model.VideoProperties
import com.cyosplay.player.data.model.VideoTechnicalDetails
import com.cyosplay.player.data.model.MiniPlayerPositionPreset
import com.cyosplay.player.data.repository.FolderNotificationStore
import com.cyosplay.player.data.repository.PlaylistRepository
import com.cyosplay.player.data.repository.RecycleBinRepository
import com.cyosplay.player.data.repository.SettingsRepository
import com.cyosplay.player.data.repository.VaultManager
import com.cyosplay.player.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cyosplay.player.util.CryptoSecurityManager
import com.cyosplay.player.util.MemoryBudgetConfig
import com.cyosplay.player.util.MemoryManager
import com.cyosplay.player.util.ScreenshotCaptureResult
import com.cyosplay.player.util.SubtitleParser
import com.cyosplay.player.util.VideoScreenshotCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class Screen {
    data object Home : Screen()
    data class FolderDetail(val folderId: String, val folderName: String, val folderPath: String) : Screen()
    data class Player(val video: VideoItem, val playlistQueue: List<VideoItem> = emptyList()) : Screen()
    data object RecycleBin : Screen()
    data object MediaManager : Screen()
    data object SubtitleStudio : Screen()
    data object SecurityQuestionSetup : Screen()
}

enum class MiniPlayerSizeMode {
    COMPACT,
    MEDIUM
}

data class VideoPlayerUiState(
    val appVersionName: String = "1.08rv9",
    val appDescription: String = "CyosPlay 1.08rv9",
    val aboutUsCreator: String = "Created by Tkm",
    val aboutUsCopyright: String = "© 2026 TKm. All rights reserved.",
    val currentNavTab: NavDestination = NavDestination.FOLDERS,
    val currentScreen: Screen = Screen.Home,
    val isLoading: Boolean = false,
    val allVideos: List<VideoItem> = emptyList(),
    val folders: List<VideoFolder> = emptyList(),
    val unseenNewFolderIds: Set<String> = emptySet(),
    val hasNewFolders: Boolean = false,
    val foldersWithNewVideos: Set<String> = emptySet(),
    val folderNewVideoCounts: Map<String, Int> = emptyMap(),
    val newVideoPaths: Set<String> = emptySet(),
    val storageVolumes: List<StorageVolumeInfo> = emptyList(),
    val isSdCardDetected: Boolean = false,
    val sdCardStatusLabel: String = "Not Detected",
    val currentDirectoryNode: StorageDirectoryNode? = null,
    val searchQuery: String = "",
    val isGridView: Boolean = false,
    val folderViewMode: BrowserViewMode = BrowserViewMode.LIST,
    val videoViewMode: BrowserViewMode = BrowserViewMode.LIST,
    val videoSortOption: VideoSortOption = VideoSortOption.DATE_DESC,
    val folderSortOption: FolderSortOption = FolderSortOption.NAME_ASC,
    val folderSortDirection: SortDirection = SortDirection.ASCENDING,
    val videoSortDirection: SortDirection = SortDirection.DESCENDING,
    val browserLayoutColumns: BrowserLayoutColumns = BrowserLayoutColumns.AUTO,
    val browserThumbnailRatio: BrowserThumbnailRatio = BrowserThumbnailRatio.WIDESCREEN_16_9,
    val advancedCodecFilter: AdvancedCodecFilter = AdvancedCodecFilter.ALL,
    val resolutionFilterTier: ResolutionFilterTier = ResolutionFilterTier.ALL,
    val includeSubfoldersRecursive: Boolean = false,
    val visibleFields: BrowserVisibleFields = BrowserVisibleFields(),
    val folderBrowserConfigMap: Map<String, FolderBrowserConfig> = emptyMap(),
    val activeBrowserControlSheetTab: BrowserControlTab? = null,
    val showFfmpegCodecInspectorDialog: Boolean = false,
    val folderExplorerMode: FolderExplorerMode = FolderExplorerMode.FLAT_FOLDERS,
    val storageVolumeFilter: StorageVolumeFilter = StorageVolumeFilter.ALL_STORAGE,
    val mediaManagerFilter: MediaManagerFilter = MediaManagerFilter.ALL,

    // Hold (Long-Press) & Browser Multi-Selection Mode, Bottom Sheet, Move/Copy & Delete State
    val isBrowserSelectionMode: Boolean = false,
    val selectedFolderPaths: Set<String> = emptySet(),
    val selectedVideoPaths: Set<String> = emptySet(),
    val holdContextVideos: List<VideoItem> = emptyList(),
    val holdContextTitle: String = "",
    val showHoldActionSheet: Boolean = false,
    val showDestinationPickerDialog: Boolean = false,
    val pendingHoldOperation: HoldClipboardOperation = HoldClipboardOperation.MOVE,
    val availableMoveDestinations: List<MoveDestinationOption> = emptyList(),
    val showDeleteConfirmDialog: Boolean = false,
    val pendingTransferOperation: PendingTransferRequest? = null,
    val clipboardState: VideoClipboardState? = null,
    val statusBannerMessage: String? = null,

    // Playlists, Favorites, Recycle Bin & Last-Resume Playback Progress
    val playlists: List<Playlist> = emptyList(),
    val favoriteVideoPaths: Set<String> = emptySet(),
    val recycleBinItems: List<RecycleBinItem> = emptyList(),
    val playbackProgressMap: Map<String, PlaybackProgress> = emptyMap(),
    val lastPlayedVideoPath: String? = null,
    val folderLastPlayedMap: Map<String, String> = emptyMap(),

    // Private Encrypted Vault & Zero-Knowledge Anti-Crack State
    val vaultAuthState: VaultAuthState = VaultAuthState.UNINITIALIZED_NO_PIN,
    val vaultEntries: List<VaultVideoEntry> = emptyList(),
    val vaultErrorMessage: String? = null,
    val hasSecurityQuestion: Boolean = false,
    val securityQuestionText: String = "",
    val hasSecondarySecurityQuestion: Boolean = false,
    val secondarySecurityQuestionText: String = "",
    val presetRecoveryQuestions: List<String> = SettingsRepository.PRESET_RECOVERY_QUESTIONS,
    val hasDecoyPin: Boolean = false,
    val biometricVaultEnabled: Boolean = true,
    val autoLockVaultOnBackground: Boolean = true,
    val vaultAutoLockRemainingSeconds: Int = 10,
    val stealthPlaybackEnabled: Boolean = false,
    val autoKeyRotationCount: Int = 0,
    val failedCrackAttempts: Int = 0,
    val invisibleSeedVerified: Boolean = true,
    val debugModePermanentlyDisabled: Boolean = true,

    // Low-RAM Performance, Battery Saver, 4K/8K HUD & Multi-Codec Decoder State
    val lowRamShieldEnabled: Boolean = false,
    val batterySaverModeEnabled: Boolean = false,
    val memoryBudget: MemoryBudgetConfig? = null,
    val decoderEngineMode: DecoderEngineMode = DecoderEngineMode.AUTO_HW_PLUS,
    val forceHardwareAccelerationHighBitrate: Boolean = false,
    val activeDecoderBadge: String = "HW+ MULTI-CODEC",
    val showPerformanceHud: Boolean = false,

    // Player & Audio / Subtitle / Visual Comfort / Unified Night Mode State
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val playerOrientationMode: PlayerOrientationMode = PlayerOrientationMode.AUTO_VIDEO,
    val smartEnhancementMode: SmartEnhancementMode = SmartEnhancementMode.OFF,
    val subtitlesEnabledByUser: Boolean = false,
    val savedPlayerBrightness: Float = 0.65f,
    val savedPlayerVolume: Float = 0.75f,
    val folderBrightnessMap: Map<String, Float> = emptyMap(),
    val folderVolumeMap: Map<String, Float> = emptyMap(),
    val videoBrightnessMap: Map<String, Float> = emptyMap(),
    val videoVolumeMap: Map<String, Float> = emptyMap(),
    val videoAudioTrackMap: Map<String, com.cyosplay.player.data.model.SavedAudioTrackSelection> = emptyMap(),
    val playbackSpeed: Float = 1.0f,
    val volumeBoostMultiplier: Float = 1.0f,
    val nightModeEnabled: Boolean = false,
    val nightModeAudioEnabled: Boolean = false,
    val dialogueEnhancerEnabled: Boolean = true,
    val isAudioMuted: Boolean = false,
    val backgroundAudioEnabled: Boolean = false,
    val sleepTimerOption: SleepTimerOption = SleepTimerOption.OFF,
    val isControlsLocked: Boolean = false,
    val abRepeatStartMs: Long? = null,
    val abRepeatEndMs: Long? = null,
    val activeVideoBookmarks: List<Long> = emptyList(),
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val externalSubtitleCues: List<SubtitleCueItem> = emptyList(),
    val secondaryExternalSubtitleCues: List<SubtitleCueItem> = emptyList(),
    val externalSubtitleTracks: List<com.cyosplay.player.data.model.ExternalSubtitleTrack> = emptyList(),
    val activeExternalSubtitleTrackIndex: Int = 0,
    val secondarySubtitleTrackIndex: Int = -1,
    val dualSubtitlesEnabled: Boolean = false,
    val subtitleSyncOffsetMs: Long = 0L,
    val subtitleFontSize: SubtitleFontSize = SubtitleFontSize.LARGE,
    val subtitleCustomSp: Float = 24f,
    val subtitleTextAlpha: Float = 0.75f,
    val subtitleBackgroundBoxTransparent: Boolean = true,
    val selectedExternalSubtitleTrackIds: Set<String> = emptySet(),
    val playAllSubtitlesSimultaneously: Boolean = false,
    val subtitleColor: SubtitleColorOption = SubtitleColorOption.WHITE,
    val subtitleOutlineColor: SubtitleOutlineColorOption = SubtitleOutlineColorOption.BLACK,
    val subtitleEdgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.DROP_SHADOW,
    val subtitleVerticalPreset: SubtitleVerticalPreset = SubtitleVerticalPreset.STANDARD,
    val videoZoomScale: Float = 1.0f,
    val eyeComfortEnabled: Boolean = false,
    val eyeComfortWarmth: Float = 0.35f,
    val extraDimIntensity: Float = 0f,
    val showVisualComfortSheet: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.AMOLED_BLACK,
    val fontMode: AppFontMode = AppFontMode.SYSTEM_DEFAULT,
    val textScaleMode: AdaptiveTextScaleMode = AdaptiveTextScaleMode.COMPACT,
    val resumePlaybackEnabled: Boolean = true,
    val autoPlayNextEnabled: Boolean = true,
    val hiddenFolderPaths: Set<String> = emptySet(),
    val fileTransferProgress: com.cyosplay.player.data.model.FileTransferProgress? = null,
    val inspectedVideoDetails: VideoTechnicalDetails? = null,
    val isDataCodesLocked: Boolean = false,
    val showMasterPasswordLockDialog: Boolean = false,
    val masterPasswordLockErrorMessage: String? = null,

    // v1.04mfr Features: Hide All Texts, PiP Mode, Custom Keystore, Persisted Scroll States
    val hideAllTextsEnabled: Boolean = false,
    val revealedTextItemIds: Set<String> = emptySet(),
    val tappedInspectorTextTitle: String? = null,
    val tappedInspectorTextBody: String? = null,
    val isInPipMode: Boolean = false,
    val autoPipEnabled: Boolean = true,
    val isPlayerCurrentlyPlaying: Boolean = false,
    val activeVideoWidth: Int = 1920,
    val activeVideoHeight: Int = 1080,
    val foldersListFirstVisibleIndex: Int = 0,
    val foldersListScrollOffset: Int = 0,
    val foldersGridFirstVisibleIndex: Int = 0,
    val foldersGridScrollOffset: Int = 0,
    val videosListFirstVisibleIndex: Int = 0,
    val videosListScrollOffset: Int = 0,
    val videosGridFirstVisibleIndex: Int = 0,
    val videosGridScrollOffset: Int = 0,
    val folderDetailScrollMap: Map<String, Pair<Int, Int>> = emptyMap(),

    // v1.06rv2 Floating Draggable Mini-Player, Position Presets, 24-240+ FPS & Comfort State
    val miniPlayerVideo: VideoItem? = null,
    val miniPlayerQueue: List<VideoItem> = emptyList(),
    val isMiniPlayerPlaying: Boolean = false,
    val miniPlayerPositionMs: Long = 0L,
    val miniPlayerDurationMs: Long = 0L,
    val miniPlayerOffsetX: Float = 0f,
    val miniPlayerOffsetY: Float = 0f,
    val miniPlayerPositionPreset: MiniPlayerPositionPreset = MiniPlayerPositionPreset.BOTTOM_CENTER,
    val detectedStreamFps: Float = 60f,
    val autoSkipIntrosCredits: Boolean = false,
    val nightShieldWarmthLevel: Float = 0.38f,
    val miniPlayerSizeMode: MiniPlayerSizeMode = MiniPlayerSizeMode.COMPACT,
    val showHighVolumeBrightnessWarning: Boolean = false,
    val highVolumeBrightnessWarningMessage: String = com.cyosplay.player.util.PlayerGestureControlHelper.HIGH_VOLUME_AND_BRIGHTNESS_WARNING_TEXT,
    val isSubtitleAudioAutoSyncEnabled: Boolean = true,
    val detectedAudioLanguageCode: String = "en",
    val subtitleAutoSyncComputedOffsetMs: Long = 0L,
    val subtitleManualFineTuneOffsetMs: Long = 0L,
    val subtitleAutoSyncStatusLabel: String = "Auto-Sync Ready (English Audio)",
    val pendingVideoUri: Uri? = null,
    val pendingTargetFolder: String? = null,
    val pendingMovePermissionRequest: com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.NeedsPermission? = null,
    val pendingBatchMoveDestination: MoveDestinationOption? = null,
    val pendingBatchMoveCustomSubfolder: String = "",
    val pendingDeleteIntentSender: android.content.IntentSender? = null,
    val pendingRecycleBinDeleteIds: Set<String> = emptySet(),
    val subtitleEncodingOverride: String = "AUTO",
    val activeVideoChapters: List<com.cyosplay.player.util.PlayerGestureControlHelper.ChapterSegment> = emptyList(),
    val isFileOperationBusy: Boolean = false,
    val fileOperationStatusText: String? = null
) {
    val inspectedVideoProperties: VideoProperties?
        get() = inspectedVideoDetails

    val autoResumePlayback: Boolean
        get() = resumePlaybackEnabled

    val isSelectionModeActive: Boolean
        get() = isBrowserSelectionMode || selectedVideoPaths.isNotEmpty() || selectedFolderPaths.isNotEmpty()

    val selectionSummaryLabel: String
        get() {
            val fCount = selectedFolderPaths.size
            val vCount = selectedVideoPaths.size
            return when {
                fCount > 0 && vCount > 0 -> "$fCount Folder(s), $vCount File(s) Selected"
                fCount > 0 -> "$fCount Folder(s) Selected"
                vCount > 0 -> "$vCount File(s) Selected"
                else -> "Select Files or Folders"
            }
        }

    val lastPlayedVideo: VideoItem?
        get() {
            if (stealthPlaybackEnabled || !resumePlaybackEnabled) return null
            val targetPath = lastPlayedVideoPath
                ?: playbackProgressMap.values.maxByOrNull { it.updatedAtMs }?.videoPath
                ?: return null
            return allVideos.firstOrNull { it.path == targetPath }
        }

    val lastPlayedProgress: PlaybackProgress?
        get() {
            val video = lastPlayedVideo ?: return null
            return playbackProgressMap[video.path]?.takeIf { it.lastPositionMs > 0L }
        }
}

class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val settingsRepository = SettingsRepository(appContext)
    val videoRepository = VideoRepository(appContext)
    val folderNotificationStore = FolderNotificationStore(appContext)
    val vaultManager = VaultManager(appContext)
    val playlistRepository = PlaylistRepository(appContext)
    val recycleBinRepository = RecycleBinRepository(appContext)

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private val navigationBackStack = ArrayDeque<Screen>()
    private var vaultAutoLockJob: kotlinx.coroutines.Job? = null
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var mediaStoreVideoObserver: android.database.ContentObserver? = null
    private var deferredPostFrameJob: kotlinx.coroutines.Job? = null
    private var activeExoPlayerRef: java.lang.ref.WeakReference<androidx.media3.exoplayer.ExoPlayer>? = null

    fun registerActiveExoPlayer(player: androidx.media3.exoplayer.ExoPlayer) {
        activeExoPlayerRef = java.lang.ref.WeakReference(player)
    }

    fun unregisterActiveExoPlayer(player: androidx.media3.exoplayer.ExoPlayer) {
        if (activeExoPlayerRef?.get() === player) {
            activeExoPlayerRef = null
        }
    }

    fun releasePlayerHandleIfMatching(videoUri: Uri, videoPath: String? = null) {
        val activeExo = activeExoPlayerRef?.get()
        if (activeExo != null) {
            val loadedUri = activeExo.currentMediaItem?.localConfiguration?.uri
            val loadedPath = loadedUri?.path
            val targetPath = videoPath ?: videoUri.path
            if (loadedUri == videoUri || (loadedPath != null && targetPath != null && loadedPath.equals(targetPath, ignoreCase = true))) {
                activeExo.stop()
                activeExo.clearMediaItems()
            }
        }
        val activeMini = _uiState.value.miniPlayerVideo
        val matchPath = videoPath ?: videoUri.path
        if (activeMini != null && (activeMini.uri == videoUri || (matchPath != null && activeMini.path.equals(matchPath, ignoreCase = true)))) {
            closeMiniPlayer()
        }
    }

    companion object {
        const val VAULT_AUTO_LOCK_SECONDS = 10
        const val VAULT_AUTO_LOCK_TIMEOUT_MS = 10_000L

        fun lookupFloatSettingCaseInsensitive(map: Map<String, Float>, key: String): Float? {
            val trimmed = key.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            map[trimmed]?.let { return it }
            return map.entries.firstOrNull {
                it.key.trim().trimEnd('/').equals(trimmed, ignoreCase = true)
            }?.value
        }

        fun isVaultVideoItem(video: VideoItem?): Boolean {
            if (video == null) return false
            if (video.folderId.equals("private_vault", ignoreCase = true)) return true
            if (video.uri.scheme.equals(com.cyos.player.VaultChunkedAesGcmDataSource.SCHEME, ignoreCase = true)) {
                return true
            }
            val p = video.path.lowercase()
            val fp = video.folderPath.lowercase()
            val u = video.uri.toString().lowercase()
            return p.contains("vault_ephemeral_play") ||
                p.contains("private_encrypted_vault") ||
                p.contains("cyos_vault") ||
                p.endsWith(".cyosvault") ||
                fp.contains("private_encrypted_vault") ||
                fp.contains("vault_ephemeral_play") ||
                u.contains("vault_ephemeral_play") ||
                u.contains("private_encrypted_vault") ||
                u.contains(".cyosvault")
        }

        fun buildCodecProfileKey(
            decoderEngineMode: DecoderEngineMode,
            forceHardwareAccelerationHighBitrate: Boolean,
            lowRamShieldEnabled: Boolean,
            batterySaverModeEnabled: Boolean
        ): String {
            return com.cyos.player.CyosPlayerHolder.buildCodecProfileKey(
                decoderModeName = decoderEngineMode.name,
                preferSoftwareDecoder = forceHardwareAccelerationHighBitrate,
                forceLowRamMode = lowRamShieldEnabled,
                batterySaverEnabled = batterySaverModeEnabled
            )
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun resolveCodecSelectorAndExtensionMode(
            decoderEngineMode: DecoderEngineMode,
            forceHardwareAccelerationHighBitrate: Boolean
        ): Pair<androidx.media3.exoplayer.mediacodec.MediaCodecSelector, Int> {
            val isSwEngine = decoderEngineMode == DecoderEngineMode.SW ||
                decoderEngineMode == DecoderEngineMode.SW_PLUS ||
                decoderEngineMode == DecoderEngineMode.SOFTWARE_MULTI_CORE
            val preferSw = isSwEngine && !forceHardwareAccelerationHighBitrate
            val selector = com.cyosplay.player.createXvidAwareMediaCodecSelector(
                fallbackSelector = com.cyosplay.player.util.AudioBadgeFormatter.createMultiCodecSelectorForEngine(
                    decoderEngineMode = decoderEngineMode,
                    forceHardwareForHighBitrate = forceHardwareAccelerationHighBitrate
                ),
                preferSoftwareForAllCodecs = preferSw
            )
            // Keep hardware-first EXTENSION_RENDERER_MODE_ON unless user explicitly forced SW/SW+.
            val extMode = if (preferSw) {
                androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            } else {
                androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }
            return selector to extMode
        }
    }

    /**
     * Registers a lifecycle-aware debounced ContentObserver on MediaStore.Video.Media.EXTERNAL_CONTENT_URI
     * so new video folders are detected live without polling or thrashing low-RAM devices.
     */
    fun startObservingMediaStoreVideos() {
        if (mediaStoreVideoObserver != null) return
        mediaStoreVideoObserver = videoRepository.registerDebouncedVideoObserver(
            scope = viewModelScope,
            debounceMs = 400L
        ) {
            refreshLibrary()
        }
    }

    fun stopObservingMediaStoreVideos() {
        videoRepository.unregisterVideoObserver(mediaStoreVideoObserver)
        mediaStoreVideoObserver = null
    }

    /**
     * Post-first-frame cold-launch ExoPlayer warm-up strictly gated to 3 GB+ RAM devices.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun warmUpPlayerAfterFirstFrame() {
        val snap = _uiState.value
        if (snap.lowRamShieldEnabled || snap.currentScreen is Screen.Player) return
        val (selector, extMode) = resolveCodecSelectorAndExtensionMode(
            decoderEngineMode = snap.decoderEngineMode,
            forceHardwareAccelerationHighBitrate = snap.forceHardwareAccelerationHighBitrate
        )
        val profileKey = buildCodecProfileKey(
            decoderEngineMode = snap.decoderEngineMode,
            forceHardwareAccelerationHighBitrate = snap.forceHardwareAccelerationHighBitrate,
            lowRamShieldEnabled = snap.lowRamShieldEnabled,
            batterySaverModeEnabled = snap.batterySaverModeEnabled
        )
        com.cyos.player.CyosPlayerHolder.warmUpAfterFirstFrame(
            context = appContext,
            mediaCodecSelector = selector,
            extensionRendererMode = extMode,
            codecProfileKey = profileKey,
            batterySaverEnabled = snap.batterySaverModeEnabled
        )
    }

    private val recentlyClearedFolderBucketIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val recentlyClearedFolderPathKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Immediately clears the "New Video Folder" dot badge for [bucketId] in memory and
     * persists the seen state asynchronously on Dispatchers.IO.
     */
    fun onFolderOpened(bucketId: String) {
        val cleanId = bucketId.trim()
        if (cleanId.isEmpty()) return
        recentlyClearedFolderBucketIds.add(cleanId)
        _uiState.update { state ->
            val updatedUnseen = state.unseenNewFolderIds - cleanId
            state.copy(
                unseenNewFolderIds = updatedUnseen,
                hasNewFolders = updatedUnseen.isNotEmpty(),
                folders = state.folders.map { folder ->
                    if (folder.bucketId == cleanId || folder.id == cleanId) {
                        folder.copy(isNew = false)
                    } else {
                        folder
                    }
                }
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            folderNotificationStore.markFolderSeen(cleanId)
        }
    }

    init {
        CryptoSecurityManager.enforcePermanentNonDebugMode(appContext)
        loadInitialState()
        // Move Keystore unwrap & integrity round-trip off the main thread so cold startup stays jank-free
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CryptoSecurityManager.getOrCreateDataEncryptionKey(appContext)
                val verified = CryptoSecurityManager.verifyDeviceKeystoreIntegrity(appContext)
                val rotations = CryptoSecurityManager.getAutoRotationCount(appContext)
                val cracks = CryptoSecurityManager.getFailedCrackAttempts(appContext)
                val locked = CryptoSecurityManager.isDataCodesLocked(appContext)
                _uiState.update { state ->
                    state.copy(
                        invisibleSeedVerified = verified,
                        autoKeyRotationCount = rotations,
                        failedCrackAttempts = cracks,
                        isDataCodesLocked = locked
                    )
                }
            }
        }
    }

    fun loadInitialState() {
        val lowRamShield = settingsRepository.isLowRamShieldEnabled()
        val batterySaver = settingsRepository.isBatterySaverModeEnabled()
        val budget = MemoryManager.detectMemoryBudget(
            context = appContext,
            forceLowRamMode = lowRamShield,
            batterySaverEnabled = batterySaver
        )
        val decoderMode = settingsRepository.getDecoderEngineMode()
        val hasPin = settingsRepository.hasVaultPin()
        val vaultState = if (hasPin) VaultAuthState.LOCKED else VaultAuthState.UNINITIALIZED_NO_PIN
        val unifiedNight = settingsRepository.isNightModeEnabled()
        val eyeComfort = settingsRepository.isEyeComfortEnabled() || unifiedNight
        val nightAudio = settingsRepository.isNightModeAudioEnabled() || unifiedNight
        val folderViewMode = settingsRepository.getFolderBrowserViewMode()
        val videoViewMode = settingsRepository.getVideoBrowserViewMode()
        val savedFoldersIdx = settingsRepository.getFoldersScrollIndex()
        val savedFoldersOff = settingsRepository.getFoldersScrollOffset()
        val savedVideosIdx = settingsRepository.getVideosScrollIndex()
        val savedVideosOff = settingsRepository.getVideosScrollOffset()

        _uiState.update { state ->
            state.copy(
                folderViewMode = folderViewMode,
                videoViewMode = videoViewMode,
                isGridView = videoViewMode == BrowserViewMode.GRID || folderViewMode == BrowserViewMode.GRID,
                videoSortOption = settingsRepository.getVideoSortOption(),
                folderSortOption = settingsRepository.getFolderSortOption(),
                folderSortDirection = settingsRepository.getFolderSortDirection(),
                videoSortDirection = settingsRepository.getVideoSortDirection(),
                browserLayoutColumns = settingsRepository.getBrowserLayoutColumns(),
                browserThumbnailRatio = settingsRepository.getBrowserThumbnailRatio(),
                advancedCodecFilter = settingsRepository.getAdvancedCodecFilter(),
                resolutionFilterTier = settingsRepository.getResolutionFilterTier(),
                includeSubfoldersRecursive = settingsRepository.isIncludeSubfoldersRecursive(),
                visibleFields = settingsRepository.getBrowserVisibleFields(),
                folderBrowserConfigMap = settingsRepository.getFolderBrowserConfigs(),
                folderExplorerMode = settingsRepository.getFolderExplorerMode(),
                storageVolumeFilter = settingsRepository.getStorageVolumeFilter(),
                repeatMode = settingsRepository.getRepeatMode(),
                playerOrientationMode = settingsRepository.getPlayerOrientationMode(),
                smartEnhancementMode = if (batterySaver) SmartEnhancementMode.ECO_BATTERY else settingsRepository.getSmartEnhancementMode(),
                subtitlesEnabledByUser = false,
                savedPlayerBrightness = settingsRepository.getSavedPlayerBrightness(),
                savedPlayerVolume = settingsRepository.getSavedPlayerVolume(),
                folderBrightnessMap = settingsRepository.getFolderBrightnessMap(),
                folderVolumeMap = settingsRepository.getFolderVolumeMap(),
                videoBrightnessMap = settingsRepository.getVideoBrightnessMap(),
                videoVolumeMap = settingsRepository.getVideoVolumeMap(),
                videoAudioTrackMap = settingsRepository.getAllSavedAudioTracks(),
                lowRamShieldEnabled = lowRamShield,
                batterySaverModeEnabled = batterySaver,
                memoryBudget = budget,
                decoderEngineMode = decoderMode,
                forceHardwareAccelerationHighBitrate = settingsRepository.isForceHardwareAccelerationHighBitrateEnabled(),
                activeDecoderBadge = if (decoderMode == DecoderEngineMode.SOFTWARE_MULTI_CORE) "SW+ MULTI-CORE" else "HW+ MULTI-CODEC",
                showPerformanceHud = settingsRepository.isPerformanceHudEnabled(),
                stealthPlaybackEnabled = settingsRepository.isStealthPlaybackEnabled(),
                biometricVaultEnabled = settingsRepository.isBiometricVaultEnabled(),
                autoLockVaultOnBackground = settingsRepository.isAutoLockVaultOnBackground(),
                vaultAuthState = vaultState,
                hasSecurityQuestion = settingsRepository.hasSecurityQuestion(),
                securityQuestionText = settingsRepository.getSecurityQuestion(),
                hasSecondarySecurityQuestion = settingsRepository.hasSecondarySecurityQuestion(),
                secondarySecurityQuestionText = settingsRepository.getSecondarySecurityQuestion(),
                hasDecoyPin = settingsRepository.hasDecoyVaultPin(),
                autoKeyRotationCount = CryptoSecurityManager.getAutoRotationCount(appContext),
                failedCrackAttempts = CryptoSecurityManager.getFailedCrackAttempts(appContext),
                invisibleSeedVerified = true,
                debugModePermanentlyDisabled = CryptoSecurityManager.isDebugModePermanentlyDisabled(),
                subtitleFontSize = settingsRepository.getSubtitleFontSize(),
                subtitleCustomSp = settingsRepository.getSubtitleCustomSp(),
                subtitleColor = settingsRepository.getSubtitleColor(),
                subtitleOutlineColor = settingsRepository.getSubtitleOutlineColor(),
                subtitleEdgeStyle = settingsRepository.getSubtitleEdgeStyle(),
                subtitleVerticalPreset = settingsRepository.getSubtitleVerticalPreset(),
                nightModeEnabled = unifiedNight,
                nightModeAudioEnabled = nightAudio,
                eyeComfortEnabled = eyeComfort,
                eyeComfortWarmth = settingsRepository.getEyeComfortWarmth(),
                extraDimIntensity = settingsRepository.getExtraDimIntensity(),
                themeMode = settingsRepository.getThemeMode(),
                fontMode = settingsRepository.getFontMode(),
                textScaleMode = settingsRepository.getTextScaleMode(),
                resumePlaybackEnabled = settingsRepository.isResumePlaybackEnabled(),
                autoPlayNextEnabled = settingsRepository.isAutoPlayNextEnabled(),
                hiddenFolderPaths = settingsRepository.getHiddenFolderPaths(),
                playbackProgressMap = settingsRepository.getAllPlaybackProgress(),
                lastPlayedVideoPath = settingsRepository.getLastPlayedVideoPath(),
                folderLastPlayedMap = settingsRepository.getAllFolderLastPlayedVideos(),
                hideAllTextsEnabled = settingsRepository.isHideAllTextsEnabled(),
                autoPipEnabled = settingsRepository.isAutoPipEnabled(),
                isDataCodesLocked = CryptoSecurityManager.isDataCodesLocked(appContext),
                foldersListFirstVisibleIndex = savedFoldersIdx,
                foldersListScrollOffset = savedFoldersOff,
                foldersGridFirstVisibleIndex = savedFoldersIdx,
                foldersGridScrollOffset = savedFoldersOff,
                videosListFirstVisibleIndex = savedVideosIdx,
                videosListScrollOffset = savedVideosOff,
                videosGridFirstVisibleIndex = savedVideosIdx,
                videosGridScrollOffset = savedVideosOff,
                miniPlayerOffsetX = settingsRepository.getMiniPlayerOffsetX(),
                miniPlayerOffsetY = settingsRepository.getMiniPlayerOffsetY(),
                miniPlayerPositionPreset = settingsRepository.getMiniPlayerPositionPreset(),
                autoSkipIntrosCredits = settingsRepository.isAutoSkipIntrosCreditsEnabled(),
                nightShieldWarmthLevel = settingsRepository.getNightShieldWarmthLevel(),
                foldersWithNewVideos = settingsRepository.getFoldersWithNewVideos(),
                folderNewVideoCounts = settingsRepository.getFolderNewVideoCounts(),
                newVideoPaths = settingsRepository.getNewVideoPaths()
            )
        }
        refreshLibrary()
    }

    private var refreshLibraryJob: kotlinx.coroutines.Job? = null

    fun refreshLibrary() {
        refreshLibraryJob?.cancel()
        refreshLibraryJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val videoSortOpt = _uiState.value.videoSortOption
            val videoSortDir = _uiState.value.videoSortDirection
            val folderSortOpt = _uiState.value.folderSortOption
            val folderSortDir = _uiState.value.folderSortDirection
            val currentStorageFilter = _uiState.value.storageVolumeFilter
            val existingDirPath = _uiState.value.currentDirectoryNode?.currentPath

            val scanResult = withContext(Dispatchers.IO) {
                val hiddenFolders = settingsRepository.getHiddenFolderPaths()
                val recyclePaths = recycleBinRepository.getRecycleBinItems().map { it.originalPath }.toSet()
                val vaultPaths = vaultManager.getVaultEntries(false).map { it.originalPath }.toSet() +
                    vaultManager.getVaultEntries(true).map { it.originalPath }.toSet()

                val rawVideos = videoRepository.scanDeviceVideos(excludedPaths = recyclePaths + vaultPaths)
                    .filterNot { video -> hiddenFolders.contains(video.folderPath) }

                val baseSortedVideos = sortVideos(rawVideos, videoSortOpt, videoSortDir)
                val seenCounts = settingsRepository.getSeenFolderVideoCounts().toMutableMap()
                val activeNewFolders = settingsRepository.getFoldersWithNewVideos().toMutableSet()
                val folderNewCounts = settingsRepository.getFolderNewVideoCounts().toMutableMap()
                val newVideoPathSet = settingsRepository.getNewVideoPaths().toMutableSet()

                val rawFolders = videoRepository.groupVideosIntoFolders(baseSortedVideos)
                val currentBucketIds = rawFolders.map { it.bucketId }.toSet()
                val hasPermissionOrItems = videoRepository.hasVideoStoragePermission() || currentBucketIds.isNotEmpty()
                val notificationState = folderNotificationStore.syncDiscoveredFolders(
                    currentBucketIds = currentBucketIds,
                    hasStoragePermission = hasPermissionOrItems
                )

                rawFolders.forEach { folder ->
                    val normPath = folder.path.trim().trimEnd('/')
                    val normKey = normPath.lowercase()
                    val previousCount = seenCounts[normPath]
                    if (previousCount != null && folder.videoCount > previousCount) {
                        val addedCount = (folder.videoCount - previousCount).coerceAtLeast(1)
                        activeNewFolders.add(normPath)
                        settingsRepository.markFolderHasNewVideo(normPath)
                        val updatedFolderNewCount = (folderNewCounts[normKey] ?: 0).coerceAtLeast(addedCount)
                        folderNewCounts[normKey] = updatedFolderNewCount
                        settingsRepository.setFolderNewVideoCount(normPath, updatedFolderNewCount)
                        folder.videos
                            .sortedByDescending { it.dateModifiedSeconds }
                            .take(addedCount)
                            .forEach { newVid ->
                                newVideoPathSet.add(newVid.path.trim())
                                settingsRepository.markVideoPathAsNew(newVid.path)
                            }
                    }
                    seenCounts[normPath] = folder.videoCount
                }
                settingsRepository.saveSeenFolderVideoCounts(seenCounts)

                val sortedVideos = baseSortedVideos.map { video ->
                    val isNew = newVideoPathSet.any { it.equals(video.path.trim(), ignoreCase = true) }
                    if (video.isNewVideo == isNew) video else video.copy(isNewVideo = isNew)
                }

                val groupedFolders = sortFolders(
                    rawFolders.map { folder ->
                        val normPath = folder.path.trim().trimEnd('/')
                        val normKey = normPath.lowercase()
                        val folderVideosWithNewFlag = folder.videos.map { v ->
                            val isNew = newVideoPathSet.any { it.equals(v.path.trim(), ignoreCase = true) }
                            if (v.isNewVideo == isNew) v else v.copy(isNewVideo = isNew)
                        }
                        val newInFolderCount = folderVideosWithNewFlag.count { it.isNewVideo }
                        val persistedCount = folderNewCounts[normKey] ?: folderNewCounts[folder.id.lowercase()] ?: 0
                        val isClearedByUser = recentlyClearedFolderPathKeys.contains(normKey) ||
                            recentlyClearedFolderPathKeys.contains(folder.id.lowercase())
                        val hasNew = if (isClearedByUser) {
                            false
                        } else {
                            activeNewFolders.any {
                                it.equals(normPath, ignoreCase = true) || it.equals(folder.id, ignoreCase = true)
                            } || newInFolderCount > 0 || persistedCount > 0
                        }
                        val badgeCount = maxOf(newInFolderCount, persistedCount, if (hasNew) 1 else 0)
                        val isUnseenNewBucket = folder.bucketId in notificationState.unseenNewFolderIds &&
                            folder.bucketId !in recentlyClearedFolderBucketIds
                        folder.copy(
                            videos = folderVideosWithNewFlag,
                            hasNewVideo = hasNew,
                            newVideoCount = if (hasNew) badgeCount else 0,
                            isNew = isUnseenNewBucket
                        )
                    },
                    folderSortOpt,
                    folderSortDir
                )
                val volumes = videoRepository.detectStorageVolumes()
                val sdDetected = volumes.any { it.isRemovable }
                val sdStatusLabel = if (sdDetected) "Detected" else "Not Detected"
                val defaultRootForFilter = if (currentStorageFilter == StorageVolumeFilter.SD_CARD_ONLY) {
                    volumes.firstOrNull { it.isRemovable }?.rootPath ?: "/storage/sdcard1"
                } else {
                    "/storage/emulated/0"
                }
                val currentRootPath = existingDirPath ?: defaultRootForFilter
                val dirNode = videoRepository.buildStorageDirectoryNode(currentRootPath, sortedVideos)
                val destinations = videoRepository.getAvailableMoveDestinations(groupedFolders)
                val progressMap = settingsRepository.getAllPlaybackProgress()
                val savedLastPath = settingsRepository.getLastPlayedVideoPath()
                    ?: progressMap.values.maxByOrNull { it.updatedAtMs }?.videoPath
                val refreshedCounts = settingsRepository.getFolderNewVideoCounts()
                val playlists = playlistRepository.getPlaylists()
                val favorites = playlistRepository.getFavoriteVideoPaths()
                val recycleItems = recycleBinRepository.getRecycleBinItems()
                val folderLastMap = settingsRepository.getAllFolderLastPlayedVideos()
                val rotationCount = CryptoSecurityManager.getAutoRotationCount(appContext)
                val crackAttempts = CryptoSecurityManager.getFailedCrackAttempts(appContext)

                object {
                    val sortedVideos = sortedVideos
                    val groupedFolders = groupedFolders
                    val notificationState = notificationState
                    val activeNewFolders = activeNewFolders
                    val refreshedCounts = refreshedCounts
                    val newVideoPathSet = newVideoPathSet
                    val volumes = volumes
                    val sdDetected = sdDetected
                    val sdStatusLabel = sdStatusLabel
                    val dirNode = dirNode
                    val destinations = destinations
                    val playlists = playlists
                    val favorites = favorites
                    val recycleItems = recycleItems
                    val progressMap = progressMap
                    val savedLastPath = savedLastPath
                    val folderLastMap = folderLastMap
                    val rotationCount = rotationCount
                    val crackAttempts = crackAttempts
                }
            }

            _uiState.update { state ->
                val effectiveUnseenBuckets = scanResult.notificationState.unseenNewFolderIds - recentlyClearedFolderBucketIds
                val effectiveFolders = scanResult.groupedFolders.map { folder ->
                    val normKey = folder.path.trim().trimEnd('/').lowercase()
                    val clearedFolder = recentlyClearedFolderPathKeys.contains(normKey) ||
                        recentlyClearedFolderPathKeys.contains(folder.id.lowercase())
                    val clearedBucket = folder.bucketId in recentlyClearedFolderBucketIds
                    if (clearedFolder || clearedBucket) {
                        folder.copy(
                            hasNewVideo = if (clearedFolder) false else folder.hasNewVideo,
                            newVideoCount = if (clearedFolder) 0 else folder.newVideoCount,
                            isNew = if (clearedBucket) false else folder.isNew
                        )
                    } else {
                        folder
                    }
                }
                state.copy(
                    isLoading = false,
                    allVideos = scanResult.sortedVideos,
                    folders = effectiveFolders,
                    unseenNewFolderIds = effectiveUnseenBuckets,
                    hasNewFolders = effectiveUnseenBuckets.isNotEmpty(),
                    foldersWithNewVideos = scanResult.activeNewFolders,
                    folderNewVideoCounts = scanResult.refreshedCounts,
                    newVideoPaths = scanResult.newVideoPathSet,
                    storageVolumes = scanResult.volumes,
                    isSdCardDetected = scanResult.sdDetected,
                    sdCardStatusLabel = scanResult.sdStatusLabel,
                    currentDirectoryNode = scanResult.dirNode,
                    availableMoveDestinations = scanResult.destinations,
                    playlists = scanResult.playlists,
                    favoriteVideoPaths = scanResult.favorites,
                    recycleBinItems = scanResult.recycleItems,
                    playbackProgressMap = scanResult.progressMap,
                    lastPlayedVideoPath = scanResult.savedLastPath,
                    folderLastPlayedMap = scanResult.folderLastMap,
                    autoKeyRotationCount = scanResult.rotationCount,
                    failedCrackAttempts = scanResult.crackAttempts
                )
            }
        }
    }

    /**
     * Plays a video URI opened from outside the app (or via document picker).
     * By default (`saveFolderInApp = false`), external video playback is strictly isolated
     * and does NOT save or add the external video's folder into the app's folder list.
     */
    fun importAndPlayLocalVideoUri(uri: Uri, saveFolderInApp: Boolean = false) {
        val imported = videoRepository.importLocalVideoUri(uri, persistInCatalog = saveFolderInApp)
        if (imported != null) {
            if (saveFolderInApp) {
                settingsRepository.markVideoPathAsNew(imported.path)
                val normFolder = imported.folderPath.trim().trimEnd('/').lowercase()
                val nextCount = (settingsRepository.getFolderNewVideoCounts()[normFolder] ?: 0) + 1
                settingsRepository.setFolderNewVideoCount(imported.folderPath, nextCount)
                settingsRepository.markFolderHasNewVideo(imported.folderPath)
                refreshLibrary()
            }
            playVideo(imported, listOf(imported))
        } else {
            _uiState.update { it.copy(statusBannerMessage = "Unable to open selected video file") }
        }
    }

    fun addVideoItemToLibrary(video: VideoItem) {
        val current = videoRepository.getOrCreateManagedCatalog().toMutableList()
        current.removeAll { it.path == video.path }
        val flaggedVideo = video.copy(isNewVideo = true)
        current.add(0, flaggedVideo)
        videoRepository.saveManagedCatalog(current)
        settingsRepository.markVideoPathAsNew(video.path)
        val normFolder = video.folderPath.trim().trimEnd('/').lowercase()
        recentlyClearedFolderPathKeys.remove(normFolder)
        recentlyClearedFolderPathKeys.remove(video.folderId.lowercase())
        val nextCount = (settingsRepository.getFolderNewVideoCounts()[normFolder] ?: 0) + 1
        settingsRepository.setFolderNewVideoCount(video.folderPath, nextCount)
        settingsRepository.markFolderHasNewVideo(video.folderPath)

        // Apply optimistic in-memory UI update immediately before background IO reconciliation
        _uiState.update { state ->
            val updatedVideos = sortVideos(
                (listOf(flaggedVideo) + state.allVideos.filterNot { it.path.equals(video.path, ignoreCase = true) }),
                state.videoSortOption,
                state.videoSortDirection
            )
            val rawFolders = videoRepository.groupVideosIntoFolders(updatedVideos)
            val updatedFolders = sortFolders(
                rawFolders.map { folder ->
                    val fNorm = folder.path.trim().trimEnd('/').lowercase()
                    val isTargetFolder = fNorm == normFolder
                    val newInFolder = folder.videos.count { it.isNewVideo }
                    val hasNew = isTargetFolder || folder.hasNewVideo || newInFolder > 0
                    val count = if (isTargetFolder) maxOf(nextCount, newInFolder, 1) else maxOf(folder.newVideoCount, newInFolder)
                    folder.copy(
                        hasNewVideo = hasNew,
                        newVideoCount = if (hasNew) count else 0,
                        isNew = folder.bucketId in state.unseenNewFolderIds
                    )
                },
                state.folderSortOption,
                state.folderSortDirection
            )
            state.copy(
                allVideos = updatedVideos,
                folders = updatedFolders,
                newVideoPaths = state.newVideoPaths + video.path.trim()
            )
        }
        refreshLibrary()
    }

    fun markVideoAsNew(videoPath: String) {
        val trimmed = videoPath.trim()
        if (trimmed.isBlank()) return
        settingsRepository.markVideoPathAsNew(trimmed)
        val updatedPaths = settingsRepository.getNewVideoPaths()
        _uiState.update { state ->
            state.copy(
                newVideoPaths = updatedPaths,
                allVideos = state.allVideos.map { v ->
                    if (v.path.trim().equals(trimmed, ignoreCase = true)) v.copy(isNewVideo = true) else v
                },
                folders = state.folders.map { f ->
                    val updatedFolderVideos = f.videos.map { v ->
                        if (v.path.trim().equals(trimmed, ignoreCase = true)) v.copy(isNewVideo = true) else v
                    }
                    val newCount = updatedFolderVideos.count { it.isNewVideo }.coerceAtLeast(f.newVideoCount)
                    f.copy(
                        videos = updatedFolderVideos,
                        hasNewVideo = f.hasNewVideo || updatedFolderVideos.any { it.isNewVideo },
                        newVideoCount = if (f.hasNewVideo || updatedFolderVideos.any { it.isNewVideo }) newCount.coerceAtLeast(1) else 0
                    )
                }
            )
        }
    }

    fun clearNewVideoDot(videoPath: String) {
        val trimmed = videoPath.trim()
        if (trimmed.isBlank()) return
        settingsRepository.clearNewVideoPath(trimmed)
        val updatedPaths = settingsRepository.getNewVideoPaths()
        _uiState.update { state ->
            state.copy(
                newVideoPaths = updatedPaths,
                allVideos = state.allVideos.map { v ->
                    if (v.path.trim().equals(trimmed, ignoreCase = true)) v.copy(isNewVideo = false) else v
                },
                folders = state.folders.map { f ->
                    val updatedFolderVideos = f.videos.map { v ->
                        if (v.path.trim().equals(trimmed, ignoreCase = true)) v.copy(isNewVideo = false) else v
                    }
                    f.copy(videos = updatedFolderVideos)
                }
            )
        }
    }

    fun markFolderHasNewVideo(folderPathOrId: String, addedCount: Int = 1) {
        val normalized = folderPathOrId.trim().trimEnd('/')
        if (normalized.isBlank()) return
        val safeCount = addedCount.coerceAtLeast(1)
        settingsRepository.markFolderHasNewVideo(normalized)
        settingsRepository.setFolderNewVideoCount(normalized, safeCount)
        val updatedSet = settingsRepository.getFoldersWithNewVideos()
        val updatedCounts = settingsRepository.getFolderNewVideoCounts()
        _uiState.update { state ->
            state.copy(
                foldersWithNewVideos = updatedSet,
                folderNewVideoCounts = updatedCounts,
                folders = state.folders.map { folder ->
                    val normFolder = folder.path.trim().trimEnd('/')
                    if (normFolder.equals(normalized, ignoreCase = true) || folder.id.equals(normalized, ignoreCase = true)) {
                        folder.copy(hasNewVideo = true, newVideoCount = maxOf(folder.newVideoCount, safeCount))
                    } else {
                        folder
                    }
                }
            )
        }
    }

    fun clearFolderNewVideoIndicator(folderPathOrId: String) {
        val normalized = folderPathOrId.trim().trimEnd('/')
        if (normalized.isBlank()) return
        recentlyClearedFolderPathKeys.add(normalized.lowercase())
        settingsRepository.clearFolderNewVideoIndicator(normalized)
        settingsRepository.clearFolderNewVideoCount(normalized)
        val updatedSet = settingsRepository.getFoldersWithNewVideos()
        val updatedCounts = settingsRepository.getFolderNewVideoCounts()
        _uiState.update { state ->
            state.copy(
                foldersWithNewVideos = updatedSet,
                folderNewVideoCounts = updatedCounts,
                folders = state.folders.map { folder ->
                    val normFolder = folder.path.trim().trimEnd('/')
                    if (normFolder.equals(normalized, ignoreCase = true) || folder.id.equals(normalized, ignoreCase = true)) {
                        folder.copy(hasNewVideo = false, newVideoCount = 0)
                    } else {
                        folder
                    }
                }
            )
        }
    }

    // --- Navigation ---

    fun selectNavTab(tab: NavDestination) {
        if (_uiState.value.currentScreen is Screen.Player) {
            vaultManager.wipeEphemeralPlaybackCache()
        }
        if (_uiState.value.currentNavTab == NavDestination.VAULT && tab != NavDestination.VAULT) {
            if (_uiState.value.vaultAuthState == VaultAuthState.UNLOCKED_REAL ||
                _uiState.value.vaultAuthState == VaultAuthState.UNLOCKED_DECOY
            ) {
                lockVault()
            }
        }
        navigationBackStack.clear()
        _uiState.update {
            it.copy(
                currentNavTab = tab,
                currentScreen = Screen.Home,
                isBrowserSelectionMode = false,
                selectedFolderPaths = emptySet(),
                selectedVideoPaths = emptySet(),
                showHoldActionSheet = false
            )
        }
    }

    fun navigateTo(screen: Screen) {
        val current = _uiState.value.currentScreen
        if (current != screen) {
            navigationBackStack.addLast(current)
        }
        _uiState.update {
            it.copy(
                currentScreen = screen,
                activeVideoBookmarks = if (screen is Screen.Player) it.activeVideoBookmarks else emptyList(),
                showHoldActionSheet = false
            )
        }
        if (screen is Screen.Player) {
            val targetPath = screen.video.path
            viewModelScope.launch(Dispatchers.IO) {
                val bookmarks = settingsRepository.getVideoBookmarks(targetPath)
                _uiState.update { state ->
                    if ((state.currentScreen as? Screen.Player)?.video?.path == targetPath) {
                        state.copy(activeVideoBookmarks = bookmarks)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun navigateBack(): Boolean {
        if (_uiState.value.isSelectionModeActive) {
            clearSelection()
            return true
        }
        val current = _uiState.value.currentScreen
        if (current is Screen.Player && isVaultVideoItem(current.video)) {
            viewModelScope.launch(Dispatchers.IO) {
                vaultManager.wipeEphemeralPlaybackCache()
            }
            notifyVaultUserInteraction()
        } else if (current is Screen.Player) {
            viewModelScope.launch(Dispatchers.IO) {
                vaultManager.wipeEphemeralPlaybackCache()
            }
        }
        val repoProgress = settingsRepository.getAllPlaybackProgress()
        val mergedProgress = buildMap {
            putAll(repoProgress)
            _uiState.value.playbackProgressMap.forEach { (path, inMemEntry) ->
                val existing = this[path]
                if (existing == null || inMemEntry.updatedAtMs >= existing.updatedAtMs) {
                    put(path, inMemEntry)
                }
            }
        }
        val updatedLastPath = _uiState.value.lastPlayedVideoPath
            ?: settingsRepository.getLastPlayedVideoPath()
        return if (navigationBackStack.isNotEmpty()) {
            val prev = navigationBackStack.removeLast()
            _uiState.update {
                it.copy(
                    currentScreen = prev,
                    playbackProgressMap = mergedProgress,
                    lastPlayedVideoPath = updatedLastPath
                )
            }
            true
        } else if (current != Screen.Home) {
            _uiState.update {
                it.copy(
                    currentScreen = Screen.Home,
                    playbackProgressMap = mergedProgress,
                    lastPlayedVideoPath = updatedLastPath
                )
            }
            true
        } else {
            false
        }
    }

    /**
     * Zero-I/O hot path on video tap:
     * 1. Records monotonic tap timestamp in StartupLatencyTracker (zero logs).
     * 2. Reads resume position, brightness, volume & saved audio strictly from in-memory StateFlow maps.
     * 3. Starts warm ExoPlayer immediately (playWhenReady = true, setMediaItem, prepare) BEFORE Compose transition.
     * 4. Navigates to Screen.Player immediately; all sidecar subtitle discovery and preference writes
     *    are deferred off the main thread onto Dispatchers.IO.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun playVideo(
        video: VideoItem,
        queue: List<VideoItem> = _uiState.value.allVideos,
        skipPlayerPrepare: Boolean = false
    ) {
        com.cyos.player.StartupLatencyTracker.markVideoTap(video.uri)
        val stateSnap = _uiState.value

        // Resolve brightness, volume, audio & resume purely from in-memory StateFlow caches (zero disk I/O)
        val folderKey = video.folderPath.trim().trimEnd('/')
            .ifBlank { video.path.substringBeforeLast('/', "").trim().trimEnd('/') }
            .ifBlank { video.folderId.trim() }
        val normalizedVideoKey = video.path.trim()
        val restoredBrightness = lookupFloatSettingCaseInsensitive(stateSnap.videoBrightnessMap, normalizedVideoKey)
            ?: lookupFloatSettingCaseInsensitive(stateSnap.folderBrightnessMap, folderKey)
            ?: stateSnap.savedPlayerBrightness
        val restoredVolume = lookupFloatSettingCaseInsensitive(stateSnap.videoVolumeMap, normalizedVideoKey)
            ?: lookupFloatSettingCaseInsensitive(stateSnap.folderVolumeMap, folderKey)
            ?: stateSnap.savedPlayerVolume
        val savedAudioSelection = stateSnap.videoAudioTrackMap[normalizedVideoKey]
            ?: stateSnap.videoAudioTrackMap.entries.firstOrNull { it.key.equals(normalizedVideoKey, ignoreCase = true) }?.value

        val savedProgress = stateSnap.playbackProgressMap[video.path]
        val savedPos = savedProgress?.lastPositionMs ?: 0L
        val dur = video.durationMs.coerceAtLeast(savedProgress?.durationMs ?: 1000L)
        val resumeMs = if (stateSnap.resumePlaybackEnabled && !stateSnap.stealthPlaybackEnabled && savedPos > 0L) {
            if (dur > 5000L && savedPos >= dur - 2000L) 0L else savedPos
        } else {
            0L
        }

        if (!skipPlayerPrepare) {
            // Start playback on the warm reused ExoPlayer BEFORE Compose navigation animation runs,
            // using the exact same codecProfileKey and Xvid-aware selector as VideoPlayerScreen.
            val codecProfileKey = buildCodecProfileKey(
                decoderEngineMode = stateSnap.decoderEngineMode,
                forceHardwareAccelerationHighBitrate = stateSnap.forceHardwareAccelerationHighBitrate,
                lowRamShieldEnabled = stateSnap.lowRamShieldEnabled,
                batterySaverModeEnabled = stateSnap.batterySaverModeEnabled
            )
            val (codecSelector, extMode) = resolveCodecSelectorAndExtensionMode(
                decoderEngineMode = stateSnap.decoderEngineMode,
                forceHardwareAccelerationHighBitrate = stateSnap.forceHardwareAccelerationHighBitrate
            )
            com.cyos.player.CyosPlayerHolder.prepareOnTap(
                context = getApplication(),
                mediaItem = androidx.media3.common.MediaItem.fromUri(video.uri),
                resumeMs = resumeMs,
                codecProfileKey = codecProfileKey,
                mediaCodecSelector = codecSelector,
                extensionRendererMode = extMode,
                forceLowRamMode = stateSnap.lowRamShieldEnabled,
                batterySaverEnabled = stateSnap.batterySaverModeEnabled
            )
        }

        val updatedFolderLastPlayed = if (stateSnap.stealthPlaybackEnabled) {
            stateSnap.folderLastPlayedMap
        } else {
            buildMap {
                putAll(stateSnap.folderLastPlayedMap)
                if (video.folderId.isNotBlank()) put(video.folderId, video.path)
                if (video.folderPath.isNotBlank()) put(video.folderPath, video.path)
            }
        }
        val trimmedPath = video.path.trim()
        val updatedNewVideoPaths = stateSnap.newVideoPaths.filterNot { it.equals(trimmedPath, ignoreCase = true) }.toSet()

        _uiState.update { state ->
            state.copy(
                lastPlayedVideoPath = if (state.stealthPlaybackEnabled) null else video.path,
                folderLastPlayedMap = updatedFolderLastPlayed,
                newVideoPaths = updatedNewVideoPaths,
                allVideos = if (video.isNewVideo) {
                    state.allVideos.map { v ->
                        if (v.path.trim().equals(trimmedPath, ignoreCase = true)) v.copy(isNewVideo = false) else v
                    }
                } else {
                    state.allVideos
                },
                savedPlayerBrightness = restoredBrightness,
                savedPlayerVolume = restoredVolume,
                isAudioMuted = savedAudioSelection?.isMuted ?: false,
                externalSubtitleTracks = emptyList(),
                externalSubtitleCues = emptyList(),
                secondaryExternalSubtitleCues = emptyList(),
                selectedExternalSubtitleTrackIds = emptySet(),
                playAllSubtitlesSimultaneously = false,
                subtitleTracks = state.subtitleTracks.map { it.copy(isSelected = false) },
                subtitlesEnabledByUser = false,
                activeVideoChapters = emptyList(),
                miniPlayerVideo = null,
                isMiniPlayerPlaying = false
            )
        }

        com.cyos.player.StartupLatencyTracker.markNavigationStart()
        navigateTo(Screen.Player(video = video, playlistQueue = queue))

        // Persist lightweight metadata asynchronously on Dispatchers.IO so zero disk I/O blocks video start
        val stealth = stateSnap.stealthPlaybackEnabled
        viewModelScope.launch(Dispatchers.IO) {
            if (trimmedPath.isNotEmpty()) {
                settingsRepository.clearNewVideoPath(trimmedPath)
            }
            settingsRepository.setSubtitlesEnabledByUser(false)
            if (!stealth) {
                settingsRepository.setLastPlayedVideoPath(video.path)
                if (video.folderId.isNotBlank()) {
                    settingsRepository.saveLastPlayedVideoForFolder(video.folderId, video.path)
                }
                if (video.folderPath.isNotBlank()) {
                    settingsRepository.saveLastPlayedVideoForFolder(video.folderPath, video.path)
                }
            }
        }
    }

    /**
     * Updates the active video inside [Screen.Player] when switching Next/Previous in a playlist
     * without pushing duplicate back-stack entries, clearing stale chapters/external subtitles and
     * loading the new video's saved brightness & audio metadata so Auto-Skip, Audio Tracks,
     * FLAG_SECURE, and PiP checks always inspect the live video.
     */
    fun switchActivePlaylistVideo(
        nextVideo: VideoItem,
        playlistQueue: List<VideoItem> = (uiState.value.currentScreen as? Screen.Player)?.playlistQueue
            ?: _uiState.value.allVideos
    ) {
        val stateSnap = _uiState.value
        val folderKey = nextVideo.folderPath.trim().trimEnd('/')
            .ifBlank { nextVideo.path.substringBeforeLast('/', "").trim().trimEnd('/') }
            .ifBlank { nextVideo.folderId.trim() }
        val normalizedVideoKey = nextVideo.path.trim()
        val restoredBrightness = lookupFloatSettingCaseInsensitive(stateSnap.videoBrightnessMap, normalizedVideoKey)
            ?: lookupFloatSettingCaseInsensitive(stateSnap.folderBrightnessMap, folderKey)
            ?: stateSnap.savedPlayerBrightness
        val restoredVolume = lookupFloatSettingCaseInsensitive(stateSnap.videoVolumeMap, normalizedVideoKey)
            ?: lookupFloatSettingCaseInsensitive(stateSnap.folderVolumeMap, folderKey)
            ?: stateSnap.savedPlayerVolume
        val savedAudioSelection = stateSnap.videoAudioTrackMap[normalizedVideoKey]
            ?: stateSnap.videoAudioTrackMap.entries.firstOrNull { it.key.equals(normalizedVideoKey, ignoreCase = true) }?.value

        val updatedFolderLastPlayed = if (stateSnap.stealthPlaybackEnabled) {
            stateSnap.folderLastPlayedMap
        } else {
            buildMap {
                putAll(stateSnap.folderLastPlayedMap)
                if (nextVideo.folderId.isNotBlank()) put(nextVideo.folderId, nextVideo.path)
                if (nextVideo.folderPath.isNotBlank()) put(nextVideo.folderPath, nextVideo.path)
            }
        }
        val trimmedPath = nextVideo.path.trim()
        val updatedNewVideoPaths = stateSnap.newVideoPaths.filterNot { it.equals(trimmedPath, ignoreCase = true) }.toSet()

        _uiState.update { state ->
            state.copy(
                currentScreen = Screen.Player(
                    video = nextVideo,
                    playlistQueue = playlistQueue.ifEmpty { listOf(nextVideo) }
                ),
                lastPlayedVideoPath = if (state.stealthPlaybackEnabled) null else nextVideo.path,
                folderLastPlayedMap = updatedFolderLastPlayed,
                newVideoPaths = updatedNewVideoPaths,
                allVideos = if (nextVideo.isNewVideo) {
                    state.allVideos.map { v ->
                        if (v.path.trim().equals(trimmedPath, ignoreCase = true)) v.copy(isNewVideo = false) else v
                    }
                } else {
                    state.allVideos
                },
                savedPlayerBrightness = restoredBrightness,
                savedPlayerVolume = restoredVolume,
                isAudioMuted = savedAudioSelection?.isMuted ?: false,
                audioTracks = emptyList(),
                externalSubtitleTracks = emptyList(),
                externalSubtitleCues = emptyList(),
                secondaryExternalSubtitleCues = emptyList(),
                selectedExternalSubtitleTrackIds = emptySet(),
                playAllSubtitlesSimultaneously = false,
                subtitleTracks = emptyList(),
                subtitlesEnabledByUser = false,
                activeVideoChapters = emptyList()
            )
        }

        val stealth = stateSnap.stealthPlaybackEnabled
        viewModelScope.launch(Dispatchers.IO) {
            if (trimmedPath.isNotEmpty()) {
                settingsRepository.clearNewVideoPath(trimmedPath)
            }
            settingsRepository.setSubtitlesEnabledByUser(false)
            if (!stealth) {
                settingsRepository.setLastPlayedVideoPath(nextVideo.path)
                if (nextVideo.folderId.isNotBlank()) {
                    settingsRepository.saveLastPlayedVideoForFolder(nextVideo.folderId, nextVideo.path)
                }
                if (nextVideo.folderPath.isNotBlank()) {
                    settingsRepository.saveLastPlayedVideoForFolder(nextVideo.folderPath, nextVideo.path)
                }
            }
        }
    }

    /**
     * Runs non-critical post-first-frame work (sidecar subtitle scan + parsing & folder last-played sync)
     * strictly on Dispatchers.IO AFTER Player.Listener.onRenderedFirstFrame fires.
     */
    fun onPlayerFirstFrameRendered(video: VideoItem) {
        deferredPostFrameJob?.cancel()
        deferredPostFrameJob = viewModelScope.launch(Dispatchers.IO) {
            discoverSidecarSubtitlesForVideo(video)
            // Extract real embedded MKV/MP4 or sidecar chapter markers for Auto-Skip Intros & Credits
            val extractedChapters = runCatching {
                com.cyosplay.player.util.MediaChapterExtractor.extractChaptersForLocalVideo(
                    filePath = video.path,
                    totalDurationMs = video.durationMs
                )
            }.getOrDefault(emptyList())
            if (extractedChapters.isNotEmpty()) {
                mergeRuntimeChapters(video.path, extractedChapters)
            }
            if (!_uiState.value.stealthPlaybackEnabled && video.folderId.isNotBlank()) {
                settingsRepository.saveLastPlayedVideoForFolder(video.folderId, video.path)
            }
        }
    }

    /**
     * Merges extracted container/sidecar/ID3 chapter markers into UI state if [videoPath]
     * is still the active playing video (deduplicating by start timestamp).
     */
    fun mergeRuntimeChapters(
        videoPath: String,
        chapters: List<com.cyosplay.player.util.PlayerGestureControlHelper.ChapterSegment>
    ) {
        if (chapters.isEmpty()) return
        val normalizedTarget = videoPath.trim()
        _uiState.update { state ->
            val currentVideo = (state.currentScreen as? Screen.Player)?.video ?: state.miniPlayerVideo
            if (currentVideo != null && normalizedTarget.isNotEmpty() &&
                !currentVideo.path.trim().equals(normalizedTarget, ignoreCase = true)
            ) {
                return@update state
            }
            val merged = (state.activeVideoChapters + chapters)
                .distinctBy { it.startMs to it.title.lowercase() }
                .sortedBy { it.startMs }
            state.copy(activeVideoChapters = merged)
        }
    }

    /**
     * Automatically scans the video's folder for matching sidecar subtitle files in any language
     * (e.g., Movie.srt, Movie.en.srt, Movie.hi.srt, Movie.ar.srt, Movie.vtt, Movie.ass, Movie.ssa).
     * Discovers and lists them ready for selection without auto-selecting any subtitle until the user selects one.
     */
    fun discoverSidecarSubtitlesForVideo(video: VideoItem): List<com.cyosplay.player.data.model.ExternalSubtitleTrack> {
        val videoFile = File(video.path)
        val parentDir = videoFile.parentFile ?: return emptyList()
        if (!parentDir.exists() || !parentDir.isDirectory) return emptyList()
        val baseName = videoFile.nameWithoutExtension.trim()
        if (baseName.isEmpty()) return emptyList()
        val supportedExts = setOf("srt", "vtt", "ass", "ssa", "sub", "ttml", "dfxp")
        val tracks = mutableListOf<com.cyosplay.player.data.model.ExternalSubtitleTrack>()
        runCatching {
            val candidates = parentDir.listFiles()?.filter { candidate ->
                if (!candidate.isFile) return@filter false
                val ext = candidate.extension.lowercase()
                if (ext !in supportedExts) return@filter false
                val candNoExt = candidate.nameWithoutExtension
                candNoExt.equals(baseName, ignoreCase = true) ||
                    candNoExt.startsWith("$baseName.", ignoreCase = true) ||
                    candNoExt.startsWith("${baseName}_", ignoreCase = true)
            }?.sortedBy { it.name } ?: emptyList()

            candidates.forEachIndexed { idx, subFile ->
                runCatching {
                    val rawBytes = subFile.readBytes()
                    val decoded = SubtitleParser.decodeSubtitleBytesMultiLanguage(rawBytes)
                    val cues = SubtitleParser.parseSubtitleText(decoded)
                    if (cues.isNotEmpty()) {
                        val langSuffix = subFile.nameWithoutExtension
                            .removePrefix(baseName)
                            .trimStart('.', '_', '-', ' ')
                            .ifBlank { SubtitleParser.detectSubtitleLanguage(decoded) }
                        val inferredLang = com.cyosplay.player.util.SubtitleAudioSyncEngine.normalizeLanguageCode(langSuffix)
                        tracks.add(
                            com.cyosplay.player.data.model.ExternalSubtitleTrack(
                                id = "sidecar_${idx}_${subFile.absolutePath.hashCode()}",
                                label = subFile.name,
                                language = inferredLang,
                                uriString = Uri.fromFile(subFile).toString(),
                                cues = cues,
                                isSelected = false
                            )
                        )
                    }
                }
            }
        }
        if (tracks.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(
                    externalSubtitleTracks = tracks,
                    externalSubtitleCues = emptyList(),
                    selectedExternalSubtitleTrackIds = emptySet(),
                    subtitlesEnabledByUser = false
                )
            }
        }
        return tracks
    }

    /**
     * Minimizes the active full-screen player into the floating Mini-Player bar above the bottom tabs
     * so audio/video playback continues while browsing the library or app settings.
     */
    fun minimizeToMiniPlayer(
        video: VideoItem,
        queue: List<VideoItem> = _uiState.value.allVideos,
        positionMs: Long = 0L,
        durationMs: Long = video.durationMs,
        isPlaying: Boolean = true
    ) {
        val safeDur = durationMs.coerceAtLeast(video.durationMs.coerceAtLeast(1000L))
        val safePos = positionMs.coerceIn(0L, safeDur)
        savePlaybackProgress(video.path, safePos, safeDur)
        _uiState.update {
            it.copy(
                miniPlayerVideo = video,
                miniPlayerQueue = queue.ifEmpty { listOf(video) },
                isMiniPlayerPlaying = isPlaying,
                miniPlayerPositionMs = safePos,
                miniPlayerDurationMs = safeDur
            )
        }
        if (_uiState.value.currentScreen is Screen.Player) {
            navigateBack()
        }
    }

    /**
     * Expands the floating Mini-Player back into the full-screen video player gaplessly when
     * the shared ExoPlayer already holds [miniPlayerVideo], avoiding any re-buffer or backward jump.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun expandMiniPlayer() {
        val state = _uiState.value
        val video = state.miniPlayerVideo ?: return
        val queue = state.miniPlayerQueue.ifEmpty { listOf(video) }
        val livePlayer = com.cyos.player.CyosPlayerHolder.currentPlayerOrNull()
        val liveUri = runCatching { livePlayer?.currentMediaItem?.localConfiguration?.uri?.toString() }.getOrNull()
        val isGaplessHandover = livePlayer != null &&
            liveUri == video.uri.toString() &&
            runCatching { livePlayer.playbackState != androidx.media3.common.Player.STATE_IDLE }.getOrDefault(false)

        val liveDur = if (isGaplessHandover) {
            runCatching { livePlayer!!.duration.takeIf { it > 0L } }.getOrNull()
        } else {
            null
        }
        val livePos = if (isGaplessHandover) {
            runCatching { livePlayer!!.currentPosition.takeIf { it > 0L } }.getOrNull()
        } else {
            null
        }
        val safeDur = (liveDur ?: state.miniPlayerDurationMs).coerceAtLeast(video.durationMs.coerceAtLeast(1000L))
        val safePos = (livePos ?: state.miniPlayerPositionMs).coerceIn(0L, safeDur)
        savePlaybackProgress(video.path, safePos, safeDur)
        _uiState.update {
            it.copy(
                miniPlayerVideo = null,
                isMiniPlayerPlaying = false
            )
        }
        playVideo(video, queue, skipPlayerPrepare = isGaplessHandover)
    }

    fun syncMiniPlayerPlayingState(playing: Boolean) {
        val state = _uiState.value
        if (state.miniPlayerVideo == null || state.isMiniPlayerPlaying == playing) return
        _uiState.update { it.copy(isMiniPlayerPlaying = playing) }
    }

    fun toggleMiniPlayerPlayPause() {
        val state = _uiState.value
        val video = state.miniPlayerVideo ?: return
        val nextPlaying = !state.isMiniPlayerPlaying
        if (!nextPlaying) {
            savePlaybackProgress(
                video.path,
                state.miniPlayerPositionMs,
                state.miniPlayerDurationMs.coerceAtLeast(video.durationMs.coerceAtLeast(1000L))
            )
        }
        _uiState.update {
            it.copy(isMiniPlayerPlaying = nextPlaying)
        }
    }

    fun skipMiniPlayer(deltaMs: Long) {
        val state = _uiState.value
        val video = state.miniPlayerVideo ?: return
        val safeDur = state.miniPlayerDurationMs.coerceAtLeast(video.durationMs.coerceAtLeast(1000L))
        val updatedPos = (state.miniPlayerPositionMs + deltaMs).coerceIn(0L, safeDur)
        savePlaybackProgress(video.path, updatedPos, safeDur)
        _uiState.update {
            it.copy(
                miniPlayerPositionMs = updatedPos,
                miniPlayerDurationMs = safeDur
            )
        }
    }

    fun seekMiniPlayerTo(positionMs: Long) {
        val state = _uiState.value
        val video = state.miniPlayerVideo ?: return
        val safeDur = state.miniPlayerDurationMs.coerceAtLeast(video.durationMs.coerceAtLeast(1000L))
        val safePos = positionMs.coerceIn(0L, safeDur)
        savePlaybackProgress(video.path, safePos, safeDur)
        _uiState.update {
            it.copy(
                miniPlayerPositionMs = safePos,
                miniPlayerDurationMs = safeDur
            )
        }
    }

    fun updateMiniPlayerProgress(positionMs: Long, durationMs: Long) {
        val state = _uiState.value
        val video = state.miniPlayerVideo ?: return
        val safeDur = durationMs.coerceAtLeast(video.durationMs.coerceAtLeast(1000L))
        val safePos = positionMs.coerceIn(0L, safeDur)
        _uiState.update {
            it.copy(
                miniPlayerPositionMs = safePos,
                miniPlayerDurationMs = safeDur
            )
        }
    }

    fun closeMiniPlayer() {
        val state = _uiState.value
        state.miniPlayerVideo?.let { video ->
            val safeDur = state.miniPlayerDurationMs.coerceAtLeast(video.durationMs.coerceAtLeast(1000L))
            savePlaybackProgress(video.path, state.miniPlayerPositionMs.coerceIn(0L, safeDur), safeDur)
        }
        _uiState.update {
            it.copy(
                miniPlayerVideo = null,
                miniPlayerQueue = emptyList(),
                isMiniPlayerPlaying = false,
                miniPlayerPositionMs = 0L,
                miniPlayerDurationMs = 0L
            )
        }
    }

    fun dragMiniPlayerByDelta(
        dx: Float,
        dy: Float,
        maxHalfX: Float = 2500f,
        maxUpY: Float = 2500f
    ) {
        if (!dx.isFinite() || !dy.isFinite()) return
        val safeMaxX = maxHalfX.coerceAtLeast(0f)
        val safeMaxUpY = maxUpY.coerceAtLeast(0f)
        val clampedX = (_uiState.value.miniPlayerOffsetX + dx).coerceIn(-safeMaxX, safeMaxX)
        val clampedY = (_uiState.value.miniPlayerOffsetY + dy).coerceIn(-safeMaxUpY, 0f)
        val preset = if (kotlin.math.abs(clampedX) < 1f && kotlin.math.abs(clampedY) < 1f) {
            MiniPlayerPositionPreset.BOTTOM_CENTER
        } else {
            MiniPlayerPositionPreset.CUSTOM
        }
        _uiState.update {
            it.copy(
                miniPlayerOffsetX = clampedX,
                miniPlayerOffsetY = clampedY,
                miniPlayerPositionPreset = preset
            )
        }
    }

    fun persistMiniPlayerOffset() {
        val state = _uiState.value
        settingsRepository.setMiniPlayerOffsetX(state.miniPlayerOffsetX)
        settingsRepository.setMiniPlayerOffsetY(state.miniPlayerOffsetY)
        settingsRepository.setMiniPlayerPositionPreset(state.miniPlayerPositionPreset)
    }

    fun updateMiniPlayerOffset(offsetX: Float, offsetY: Float) {
        val clampedX = offsetX.coerceIn(-2500f, 2500f)
        val clampedY = offsetY.coerceIn(-2500f, 2500f)
        val preset = if (kotlin.math.abs(clampedX) < 1f && kotlin.math.abs(clampedY) < 1f) {
            MiniPlayerPositionPreset.BOTTOM_CENTER
        } else {
            MiniPlayerPositionPreset.CUSTOM
        }
        settingsRepository.setMiniPlayerOffsetX(clampedX)
        settingsRepository.setMiniPlayerOffsetY(clampedY)
        settingsRepository.setMiniPlayerPositionPreset(preset)
        _uiState.update {
            it.copy(
                miniPlayerOffsetX = clampedX,
                miniPlayerOffsetY = clampedY,
                miniPlayerPositionPreset = preset
            )
        }
    }

    fun setMiniPlayerPositionPreset(preset: MiniPlayerPositionPreset) {
        val targetX = preset.defaultOffsetX.coerceIn(-2500f, 2500f)
        val targetY = preset.defaultOffsetY.coerceIn(-2500f, 2500f)
        settingsRepository.setMiniPlayerPositionPreset(preset)
        settingsRepository.setMiniPlayerOffsetX(targetX)
        settingsRepository.setMiniPlayerOffsetY(targetY)
        _uiState.update {
            it.copy(
                miniPlayerPositionPreset = preset,
                miniPlayerOffsetX = targetX,
                miniPlayerOffsetY = targetY
            )
        }
    }

    fun cycleMiniPlayerPositionPreset(): MiniPlayerPositionPreset {
        val selectable = listOf(
            MiniPlayerPositionPreset.BOTTOM_CENTER,
            MiniPlayerPositionPreset.BOTTOM_LEFT,
            MiniPlayerPositionPreset.BOTTOM_RIGHT,
            MiniPlayerPositionPreset.CENTER,
            MiniPlayerPositionPreset.TOP_LEFT,
            MiniPlayerPositionPreset.TOP_RIGHT
        )
        val currentIdx = selectable.indexOf(_uiState.value.miniPlayerPositionPreset)
        val next = selectable[(currentIdx + 1) % selectable.size]
        setMiniPlayerPositionPreset(next)
        return next
    }

    fun resetMiniPlayerOffset() {
        setMiniPlayerPositionPreset(MiniPlayerPositionPreset.BOTTOM_CENTER)
    }

    fun updateDetectedStreamFps(fps: Float) {
        val safeFps = if (fps > 1f) fps.coerceIn(15f, 300f) else 60f
        _uiState.update { it.copy(detectedStreamFps = safeFps) }
    }

    /**
     * Item 12: Resolves the last watched video for a specific folder (or global video list) so Play All
     * resumes directly from the last watched video and timestamp in that folder.
     */
    fun getLastPlayedVideoForFolder(folderIdOrPath: String, folderVideos: List<VideoItem>): VideoItem? {
        if (folderVideos.isEmpty()) return null
        val state = _uiState.value
        if (!state.stealthPlaybackEnabled && state.resumePlaybackEnabled) {
            val savedFolderVideoPath = settingsRepository.getLastPlayedVideoForFolder(folderIdOrPath)
                ?: state.folderLastPlayedMap[folderIdOrPath.trim().trimEnd('/').lowercase()]
            if (!savedFolderVideoPath.isNullOrBlank()) {
                folderVideos.firstOrNull { it.path == savedFolderVideoPath }?.let { return it }
            }
            // Fallback: pick the most recently progressed video inside this folder
            val progressMap = state.playbackProgressMap
            val mostRecentInFolder = folderVideos
                .mapNotNull { v -> progressMap[v.path]?.let { prog -> v to prog } }
                .maxByOrNull { (_, prog) -> prog.updatedAtMs }
                ?.first
            if (mostRecentInFolder != null) return mostRecentInFolder
        }
        return folderVideos.first()
    }

    /**
     * Item 12: Starts Play All for a specific folder (or global videos), resuming directly from
     * the last watched video and saved timestamp in that folder.
     */
    fun playAllInFolder(folderIdOrPath: String, folderVideos: List<VideoItem>) {
        if (folderVideos.isEmpty()) return
        val targetVideo = getLastPlayedVideoForFolder(folderIdOrPath, folderVideos) ?: folderVideos.first()
        playVideo(targetVideo, folderVideos)
    }

    fun openFolderDetail(folder: VideoFolder) {
        onFolderOpened(folder.bucketId)
        clearFolderNewVideoIndicator(folder.path)
        clearFolderNewVideoIndicator(folder.id)
        navigateTo(
            Screen.FolderDetail(
                folderId = folder.id,
                folderName = folder.name,
                folderPath = folder.path
            )
        )
    }

    // --- Hold (Long-Press) & Browser Multi-File/Multi-Folder Selection Mode ---

    fun toggleBrowserSelectionMode() {
        val entering = !_uiState.value.isSelectionModeActive
        if (entering) {
            _uiState.update {
                it.copy(
                    isBrowserSelectionMode = true,
                    showHoldActionSheet = false
                )
            }
        } else {
            clearSelection()
        }
    }

    fun enterBrowserSelectionMode() {
        _uiState.update {
            it.copy(
                isBrowserSelectionMode = true,
                showHoldActionSheet = false
            )
        }
    }

    /**
     * Triggered when the user holds (long-presses) a video card or row.
     * Activates selection mode and opens the Hold Options Bottom Sheet (Copy, Move, Delete, Vault).
     */
    fun onHoldVideoItem(video: VideoItem) {
        val currentSelected = _uiState.value.selectedVideoPaths.toMutableSet()
        currentSelected.add(video.path)
        val contextList = _uiState.value.allVideos.filter { currentSelected.contains(it.path) }
            .ifEmpty { listOf(video) }
        val title = if (contextList.size == 1 && _uiState.value.selectedFolderPaths.isEmpty()) {
            video.title
        } else {
            "${contextList.size} Video(s) Selected"
        }
        _uiState.update {
            it.copy(
                isBrowserSelectionMode = true,
                selectedVideoPaths = currentSelected,
                holdContextVideos = contextList,
                holdContextTitle = title,
                showHoldActionSheet = true,
                availableMoveDestinations = videoRepository.getAvailableMoveDestinations(it.folders)
            )
        }
    }

    /**
     * Triggered when the user holds (long-presses) a folder card or row.
     * Selects the folder and all videos inside it, activating multi-selection mode.
     */
    fun onHoldFolderItem(folder: VideoFolder) {
        val normFolder = folder.path.trim().trimEnd('/')
        val updatedFolders = _uiState.value.selectedFolderPaths.toMutableSet().apply { add(normFolder) }
        val updatedVideos = _uiState.value.selectedVideoPaths.toMutableSet().apply {
            addAll(folder.videos.map { it.path })
        }
        val contextList = _uiState.value.allVideos.filter { updatedVideos.contains(it.path) }
            .ifEmpty { folder.videos }
        val title = if (updatedFolders.size == 1 && updatedVideos.size == folder.videos.size) {
            "${folder.name} (${folder.videoCount} videos)"
        } else {
            "${updatedFolders.size} Folder(s), ${contextList.size} Video(s)"
        }
        _uiState.update {
            it.copy(
                isBrowserSelectionMode = true,
                selectedFolderPaths = updatedFolders,
                selectedVideoPaths = updatedVideos,
                holdContextVideos = contextList,
                holdContextTitle = title,
                showHoldActionSheet = true,
                availableMoveDestinations = videoRepository.getAvailableMoveDestinations(it.folders)
            )
        }
    }

    fun toggleVideoSelection(video: VideoItem) {
        val updatedVideos = _uiState.value.selectedVideoPaths.toMutableSet()
        if (updatedVideos.contains(video.path)) {
            updatedVideos.remove(video.path)
        } else {
            updatedVideos.add(video.path)
        }
        val selectedVideos = _uiState.value.allVideos.filter { updatedVideos.contains(it.path) }
        _uiState.update {
            it.copy(
                isBrowserSelectionMode = true,
                selectedVideoPaths = updatedVideos,
                holdContextVideos = selectedVideos,
                holdContextTitle = "${selectedVideos.size} Selected"
            )
        }
    }

    fun toggleFolderSelection(folder: VideoFolder) {
        val normFolder = folder.path.trim().trimEnd('/')
        val updatedFolders = _uiState.value.selectedFolderPaths.toMutableSet()
        val updatedVideos = _uiState.value.selectedVideoPaths.toMutableSet()
        val folderVideoPaths = folder.videos.map { it.path }.toSet()

        val isCurrentlySelected = updatedFolders.any { it.equals(normFolder, ignoreCase = true) }
        if (isCurrentlySelected) {
            updatedFolders.removeAll { it.equals(normFolder, ignoreCase = true) }
            updatedVideos.removeAll(folderVideoPaths)
        } else {
            updatedFolders.add(normFolder)
            updatedVideos.addAll(folderVideoPaths)
        }
        val selectedVideos = _uiState.value.allVideos.filter { updatedVideos.contains(it.path) }
        _uiState.update {
            it.copy(
                isBrowserSelectionMode = true,
                selectedFolderPaths = updatedFolders,
                selectedVideoPaths = updatedVideos,
                holdContextVideos = selectedVideos,
                holdContextTitle = "${updatedFolders.size} Folder(s), ${selectedVideos.size} File(s)"
            )
        }
    }

    fun toggleSubfolderPathSelection(subfolderPath: String) {
        val normFolder = subfolderPath.trim().trimEnd('/')
        val updatedFolders = _uiState.value.selectedFolderPaths.toMutableSet()
        val updatedVideos = _uiState.value.selectedVideoPaths.toMutableSet()
        val matchingVideos = _uiState.value.allVideos.filter { video ->
            val vDir = video.folderPath.trim().trimEnd('/')
            vDir.equals(normFolder, ignoreCase = true) || vDir.startsWith("$normFolder/", ignoreCase = true)
        }
        val matchingPaths = matchingVideos.map { it.path }.toSet()

        val isCurrentlySelected = updatedFolders.any { it.equals(normFolder, ignoreCase = true) }
        if (isCurrentlySelected) {
            updatedFolders.removeAll { it.equals(normFolder, ignoreCase = true) }
            updatedVideos.removeAll(matchingPaths)
        } else {
            updatedFolders.add(normFolder)
            updatedVideos.addAll(matchingPaths)
        }
        val selectedVideos = _uiState.value.allVideos.filter { updatedVideos.contains(it.path) }
        _uiState.update {
            it.copy(
                isBrowserSelectionMode = true,
                selectedFolderPaths = updatedFolders,
                selectedVideoPaths = updatedVideos,
                holdContextVideos = selectedVideos,
                holdContextTitle = "${updatedFolders.size} Folder(s), ${selectedVideos.size} File(s)"
            )
        }
    }

    fun selectAllVisibleVideos(visibleVideos: List<VideoItem>) {
        val paths = visibleVideos.map { it.path }.toSet()
        _uiState.update {
            it.copy(
                isBrowserSelectionMode = true,
                selectedVideoPaths = paths,
                holdContextVideos = visibleVideos,
                holdContextTitle = "${visibleVideos.size} Selected"
            )
        }
    }

    fun selectAllVisibleFoldersAndVideos(
        visibleFolders: List<VideoFolder>,
        visibleVideos: List<VideoItem>,
        visibleSubfolderPaths: List<String> = emptyList()
    ) {
        val folderPaths = (visibleFolders.map { it.path.trim().trimEnd('/') } +
            visibleSubfolderPaths.map { it.trim().trimEnd('/') }).filter { it.isNotBlank() }.toSet()
        val videoPaths = (visibleVideos.map { it.path } +
            visibleFolders.flatMap { f -> f.videos.map { it.path } }).toSet()
        val matchedVideos = _uiState.value.allVideos.filter { videoPaths.contains(it.path) }
            .ifEmpty { visibleVideos }
        _uiState.update {
            it.copy(
                isBrowserSelectionMode = true,
                selectedFolderPaths = folderPaths,
                selectedVideoPaths = videoPaths,
                holdContextVideos = matchedVideos,
                holdContextTitle = "${folderPaths.size} Folder(s), ${matchedVideos.size} File(s)"
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                isBrowserSelectionMode = false,
                selectedFolderPaths = emptySet(),
                selectedVideoPaths = emptySet(),
                holdContextVideos = emptyList(),
                showHoldActionSheet = false
            )
        }
    }

    fun dismissHoldActionSheet() {
        _uiState.update { it.copy(showHoldActionSheet = false) }
    }

    fun inspectVideoDetails(video: VideoItem): VideoTechnicalDetails {
        val details = videoRepository.extractExactVideoProperties(video)
        _uiState.update {
            it.copy(
                inspectedVideoDetails = details,
                showHoldActionSheet = false
            )
        }
        return details
    }

    fun inspectVideoProperties(video: VideoItem): VideoProperties {
        return inspectVideoDetails(video)
    }

    fun dismissVideoDetails() {
        _uiState.update { it.copy(inspectedVideoDetails = null) }
    }

    fun dismissVideoProperties() {
        dismissVideoDetails()
    }

    fun toggleMasterPasswordLockDialog(show: Boolean) {
        _uiState.update { it.copy(showMasterPasswordLockDialog = show, masterPasswordLockErrorMessage = null) }
    }

    fun setDataCodesPasscode(newPasscode: String): Boolean {
        return CryptoSecurityManager.setDataCodesPasscode(getApplication(), newPasscode)
    }

    fun unlockDataCodesWithMasterPassword(passwordAttempt: String): Boolean {
        val app = getApplication<Application>()
        val hasVaultPin = settingsRepository.hasVaultPin()
        val success = CryptoSecurityManager.unlockDataCodesWithPasscode(
            context = app,
            candidatePasscode = passwordAttempt,
            vaultPinVerifier = if (hasVaultPin) { candidate -> settingsRepository.verifyVaultPin(candidate) } else null
        )
        if (success) {
            _uiState.update {
                it.copy(
                    isDataCodesLocked = false,
                    showMasterPasswordLockDialog = false,
                    masterPasswordLockErrorMessage = null,
                    statusBannerMessage = "Passcode Verified. Data Codes & Properties Unlocked (AES-256-GCM)."
                )
            }
            _uiState.value.inspectedVideoDetails?.let { current ->
                _uiState.update { state ->
                    state.copy(inspectedVideoDetails = current.copy(isDataCodesLocked = false))
                }
            }
        } else {
            _uiState.update {
                it.copy(masterPasswordLockErrorMessage = "Incorrect passcode. Codes remain locked & encrypted.")
            }
        }
        return success
    }

    fun lockDataCodesWithMasterPassword() {
        CryptoSecurityManager.lockDataCodes(getApplication())
        _uiState.update {
            it.copy(
                isDataCodesLocked = true,
                statusBannerMessage = "Data Codes & Properties Locked (AES-256-GCM)."
            )
        }
        _uiState.value.inspectedVideoDetails?.let { current ->
            _uiState.update { state ->
                state.copy(inspectedVideoDetails = current.copy(isDataCodesLocked = true))
            }
        }
    }

    fun openHoldActionSheetForCurrentSelection() {
        val selected = getActiveHoldTargetVideos()
        if (selected.isEmpty() && _uiState.value.selectedFolderPaths.isEmpty()) return
        val title = _uiState.value.selectionSummaryLabel
        _uiState.update {
            it.copy(
                holdContextVideos = selected,
                holdContextTitle = title,
                showHoldActionSheet = true,
                availableMoveDestinations = videoRepository.getAvailableMoveDestinations(it.folders)
            )
        }
    }

    private fun getActiveHoldTargetVideos(): List<VideoItem> {
        val state = _uiState.value
        val normFolders = state.selectedFolderPaths.map { it.trim().trimEnd('/') }
        val poolByPath = LinkedHashMap<String, VideoItem>()
        state.allVideos.forEach { poolByPath[it.path] = it }
        videoRepository.getOrCreateManagedCatalog().forEach {
            if (!poolByPath.containsKey(it.path)) {
                poolByPath[it.path] = it
            }
        }
        state.holdContextVideos.forEach {
            if (!poolByPath.containsKey(it.path)) {
                poolByPath[it.path] = it
            }
        }
        state.selectedVideoPaths.forEach { selPath ->
            if (!poolByPath.containsKey(selPath)) {
                val f = File(selPath)
                val parentPath = f.parentFile?.absolutePath ?: ""
                poolByPath[selPath] = VideoItem(
                    id = selPath.hashCode().toLong(),
                    title = f.nameWithoutExtension.ifBlank { f.name },
                    displayName = f.name,
                    uri = Uri.fromFile(f),
                    path = selPath,
                    sizeBytes = if (f.exists()) f.length() else 0L,
                    durationMs = 0L,
                    dateModifiedSeconds = if (f.exists()) f.lastModified() / 1000L else 0L,
                    width = 0,
                    height = 0,
                    mimeType = "video/mp4",
                    folderId = parentPath.lowercase().hashCode().toString(),
                    folderName = f.parentFile?.name ?: "",
                    folderPath = parentPath
                )
            }
        }
        val fromSelectedPathsAndFolders = poolByPath.values.filter { video ->
            val vDir = video.folderPath.trim().trimEnd('/')
            state.selectedVideoPaths.contains(video.path) ||
                normFolders.any { sel -> vDir.equals(sel, ignoreCase = true) || vDir.startsWith("$sel/", ignoreCase = true) }
        }
        if (fromSelectedPathsAndFolders.isNotEmpty()) return fromSelectedPathsAndFolders
        return state.holdContextVideos
    }

    /**
     * Opens the Destination Picker modal for either Copy or Move.
     */
    fun startCopyOrMoveFlow(operation: HoldClipboardOperation) {
        val targets = getActiveHoldTargetVideos()
        if (targets.isEmpty()) return
        _uiState.update {
            it.copy(
                pendingHoldOperation = operation,
                holdContextVideos = targets,
                showHoldActionSheet = false,
                showDestinationPickerDialog = true,
                availableMoveDestinations = videoRepository.getAvailableMoveDestinations(it.folders)
            )
        }
    }

    fun dismissDestinationPickerDialog() {
        _uiState.update { it.copy(showDestinationPickerDialog = false) }
    }

    /**
     * Stages or immediately executes a Copy or Move destination request.
     * In v1.06rv8, file management actions execute immediately on the first destination confirmation
     * without extra confirmation dialogs.
     */
    fun requestCopyOrMoveConfirmation(
        destination: MoveDestinationOption,
        customNewFolderName: String = ""
    ) {
        val state = _uiState.value
        val targets = getActiveHoldTargetVideos().ifEmpty {
            state.clipboardState?.sourceVideos ?: emptyList()
        }
        if (targets.isEmpty()) return
        if (destination.isPrivateVault) {
            moveVideosDirectlyToPrivateVault(targets)
            return
        }
        executeCopyOrMoveToDestination(
            destination = destination,
            customNewFolderName = customNewFolderName.trim()
        )
    }

    fun confirmPendingTransferOperation() {
        val pending = _uiState.value.pendingTransferOperation ?: return
        _uiState.update {
            it.copy(
                pendingHoldOperation = pending.operation,
                holdContextVideos = pending.targetVideos,
                pendingTransferOperation = null
            )
        }
        executeCopyOrMoveToDestination(
            destination = pending.destination,
            customNewFolderName = pending.customNewFolderName
        )
    }

    fun dismissPendingTransferOperation() {
        _uiState.update { it.copy(pendingTransferOperation = null) }
    }

    /**
     * Stages the selected videos into the floating clipboard so the user can also browse to any folder
     * and tap "Paste Here".
     */
    fun stageVideosToClipboard(operation: HoldClipboardOperation) {
        val targets = getActiveHoldTargetVideos()
        if (targets.isEmpty()) return
        val actionWord = if (operation == HoldClipboardOperation.COPY) "Copied" else "Ready to move"
        _uiState.update {
            it.copy(
                clipboardState = VideoClipboardState(sourceVideos = targets, operation = operation),
                isBrowserSelectionMode = false,
                selectedFolderPaths = emptySet(),
                selectedVideoPaths = emptySet(),
                holdContextVideos = emptyList(),
                showHoldActionSheet = false,
                showDestinationPickerDialog = false,
                statusBannerMessage = "$actionWord ${targets.size} video(s) • Open any folder to Paste"
            )
        }
    }

    fun clearClipboard() {
        _uiState.update { it.copy(clipboardState = null) }
    }

    /**
     * Executes Copy or Move directly to the chosen destination (any Internal folder, SD Card folder,
     * custom new folder, or the encrypted Private Vault).
     */
    fun executeCopyOrMoveToDestination(
        destination: MoveDestinationOption,
        customNewFolderName: String = "",
        skipWritePermissionCheck: Boolean = false
    ) {
        val state = _uiState.value
        val targets = getActiveHoldTargetVideos().ifEmpty {
            state.clipboardState?.sourceVideos ?: emptyList()
        }
        if (targets.isEmpty()) return
        val operation = state.pendingHoldOperation

        if (destination.isPrivateVault) {
            moveVideosDirectlyToPrivateVault(targets)
            return
        }

        val finalFolderPath: String
        val finalFolderName: String
        val trimmedInput = customNewFolderName.trim()
        if (trimmedInput.startsWith("/")) {
            val sanitizedPath = trimmedInput.trimEnd('/').ifEmpty { "/storage/emulated/0/Movies" }
            if (!CryptoSecurityManager.isPathAllowedForIsolatedApp(sanitizedPath, appContext.packageName)) {
                _uiState.update {
                    it.copy(statusBannerMessage = "Strict Isolation: Access to external app directories is prohibited")
                }
                return
            }
            finalFolderPath = sanitizedPath
            finalFolderName = sanitizedPath.substringAfterLast('/').ifBlank { "CustomFolder" }
        } else if (trimmedInput.isNotBlank()) {
            val cleanName = trimmedInput.replace('/', '_')
            finalFolderPath = "${destination.targetPath.trimEnd('/')}/$cleanName"
            finalFolderName = cleanName
        } else {
            finalFolderPath = destination.targetPath
            finalFolderName = destination.title
        }

        if (!CryptoSecurityManager.isPathAllowedForIsolatedApp(finalFolderPath, appContext.packageName)) {
            _uiState.update {
                it.copy(statusBannerMessage = "Strict Isolation: Access to external app directories is prohibited")
            }
            return
        }

        if (operation == HoldClipboardOperation.MOVE && !skipWritePermissionCheck) {
            val writeConsentIntentSender = com.cyosplay.videoplayer.util.VideoPlayerFileMover.prepareBatchMoveWriteRequest(
                context = appContext,
                urisAndPaths = targets.map { it.uri to it.path }
            )
            if (writeConsentIntentSender != null) {
                val firstTarget = targets.first()
                _uiState.update {
                    it.copy(
                        showDestinationPickerDialog = false,
                        showHoldActionSheet = false,
                        pendingBatchMoveDestination = destination,
                        pendingBatchMoveCustomSubfolder = customNewFolderName,
                        pendingVideoUri = firstTarget.uri,
                        pendingTargetFolder = finalFolderPath,
                        pendingMovePermissionRequest = com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.NeedsPermission(
                            intentSender = writeConsentIntentSender,
                            videoUri = firstTarget.uri,
                            targetRelativePath = finalFolderPath
                        )
                    )
                }
                return
            }
        }

        val startNs = System.nanoTime()
        val totalBytes = targets.sumOf { it.sizeBytes.coerceAtLeast(1L) }

        if (operation != HoldClipboardOperation.COPY) {
            targets.forEach { target ->
                releasePlayerHandleIfMatching(target.uri, target.path)
            }
        }

        refreshLibraryJob?.cancel()
        _uiState.update {
            it.copy(
                isFileOperationBusy = true,
                fileOperationStatusText = if (operation == HoldClipboardOperation.COPY) {
                    "Copying ${targets.size} video(s) to $finalFolderName…"
                } else {
                    "Moving ${targets.size} video(s) to $finalFolderName…"
                },
                fileTransferProgress = com.cyosplay.player.data.model.FileTransferProgress(
                    operation = operation,
                    completedFiles = 0,
                    totalFiles = targets.size,
                    bytesTransferred = 0L,
                    totalBytes = totalBytes,
                    speedMbPerSec = 0f,
                    currentFileName = targets.firstOrNull()?.displayName ?: "",
                    destinationFolderName = finalFolderName
                )
            )
        }

        val isRemovableSdDest = destination.isRemovableSd || CryptoSecurityManager.isRemovableSdCardPath(finalFolderPath)

        viewModelScope.launch {
            if (operation == HoldClipboardOperation.COPY) {
                val copied = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val result = videoRepository.copyVideosToFolder(
                        videosToCopy = targets,
                        targetFolderPath = finalFolderPath,
                        targetFolderName = finalFolderName,
                        isRemovableSd = isRemovableSdDest
                    ) { completed, total, bytesDone, bytesTotal, speed, curName ->
                        _uiState.update {
                            it.copy(
                                fileTransferProgress = com.cyosplay.player.data.model.FileTransferProgress(
                                    operation = operation,
                                    completedFiles = completed,
                                    totalFiles = total,
                                    bytesTransferred = bytesDone,
                                    totalBytes = bytesTotal,
                                    speedMbPerSec = speed,
                                    currentFileName = curName,
                                    destinationFolderName = finalFolderName
                                )
                            )
                        }
                    }
                    if (result.isNotEmpty()) {
                        result.forEach { settingsRepository.markVideoPathAsNew(it.path) }
                        val normFolder = finalFolderPath.trim().trimEnd('/').lowercase()
                        val nextCount = (settingsRepository.getFolderNewVideoCounts()[normFolder] ?: 0) + result.size
                        settingsRepository.setFolderNewVideoCount(finalFolderPath, nextCount)
                        settingsRepository.markFolderHasNewVideo(finalFolderPath)
                    }
                    result
                }
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                _uiState.update { curState ->
                    val flaggedCopied = copied.map { it.copy(isNewVideo = true) }
                    val copiedPaths = flaggedCopied.map { it.path.lowercase() }.toSet()
                    val mergedVideos = sortVideos(
                        flaggedCopied + curState.allVideos.filterNot { it.path.lowercase() in copiedPaths },
                        curState.videoSortOption,
                        curState.videoSortDirection
                    )
                    val mergedFolders = sortFolders(
                        videoRepository.groupVideosIntoFolders(mergedVideos).map { folder ->
                            val newInFolder = folder.videos.count { it.isNewVideo }
                            val hasNew = folder.hasNewVideo || newInFolder > 0
                            folder.copy(
                                hasNewVideo = hasNew,
                                newVideoCount = if (hasNew) maxOf(folder.newVideoCount, newInFolder, 1) else 0,
                                isNew = folder.bucketId in curState.unseenNewFolderIds
                            )
                        },
                        curState.folderSortOption,
                        curState.folderSortDirection
                    )
                    curState.copy(
                        allVideos = mergedVideos,
                        folders = mergedFolders,
                        isBrowserSelectionMode = false,
                        selectedFolderPaths = emptySet(),
                        selectedVideoPaths = emptySet(),
                        holdContextVideos = emptyList(),
                        showDestinationPickerDialog = false,
                        showHoldActionSheet = false,
                        pendingTransferOperation = null,
                        clipboardState = null,
                        fileTransferProgress = null,
                        isFileOperationBusy = false,
                        fileOperationStatusText = null,
                        statusBannerMessage = "Fast-copied ${copied.size} video(s) to $finalFolderName (${elapsedMs}ms)"
                    )
                }
            } else {
                var moveErrorMessage: String? = null
                val moved = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val result = videoRepository.moveVideosToFolder(
                        videosToMove = targets,
                        targetFolderPath = finalFolderPath,
                        targetFolderName = finalFolderName,
                        isRemovableSd = isRemovableSdDest,
                        onMovedItem = { removedSource, addedDest ->
                            _uiState.update { curState ->
                                val updatedList = curState.allVideos.toMutableList()
                                val pos = updatedList.indexOfFirst {
                                    it.id == removedSource.id || it.path.equals(removedSource.path, ignoreCase = true)
                                }
                                if (pos >= 0) {
                                    updatedList.removeAt(pos)
                                }
                                updatedList.removeAll {
                                    it.id == removedSource.id || it.path.equals(removedSource.path, ignoreCase = true)
                                }
                                updatedList.add(0, addedDest)

                                val updatedFolders = curState.folders.mapNotNull { folder ->
                                    if (folder.path.equals(removedSource.folderPath, ignoreCase = true) || folder.id == removedSource.folderId) {
                                        val remaining = folder.videos.toMutableList()
                                        val fPos = remaining.indexOfFirst {
                                            it.id == removedSource.id || it.path.equals(removedSource.path, ignoreCase = true)
                                        }
                                        if (fPos >= 0) {
                                            remaining.removeAt(fPos)
                                        }
                                        remaining.removeAll {
                                            it.id == removedSource.id || it.path.equals(removedSource.path, ignoreCase = true)
                                        }
                                        if (remaining.isEmpty()) null else folder.copy(videos = remaining)
                                    } else {
                                        folder
                                    }
                                }
                                curState.copy(
                                    allVideos = updatedList,
                                    folders = updatedFolders
                                )
                            }
                        },
                        onError = { err ->
                            moveErrorMessage = err
                        }
                    ) { completed, total, bytesDone, bytesTotal, speed, curName ->
                        _uiState.update {
                            it.copy(
                                fileTransferProgress = com.cyosplay.player.data.model.FileTransferProgress(
                                    operation = operation,
                                    completedFiles = completed,
                                    totalFiles = total,
                                    bytesTransferred = bytesDone,
                                    totalBytes = bytesTotal,
                                    speedMbPerSec = speed,
                                    currentFileName = curName,
                                    destinationFolderName = finalFolderName
                                )
                            )
                        }
                    }
                    if (result.isNotEmpty()) {
                        result.forEach { movedItem ->
                            val oldSource = targets.firstOrNull {
                                it.id == movedItem.id || it.displayName.equals(movedItem.displayName, ignoreCase = true)
                            }
                            if (oldSource != null) {
                                settingsRepository.relinkMovedVideoReferences(oldSource.path, movedItem.path)
                                playlistRepository.relinkMovedVideoReferences(oldSource.path, movedItem.path)
                            }
                            settingsRepository.markVideoPathAsNew(movedItem.path)
                        }
                        val normFolder = finalFolderPath.trim().trimEnd('/').lowercase()
                        val nextCount = (settingsRepository.getFolderNewVideoCounts()[normFolder] ?: 0) + result.size
                        settingsRepository.setFolderNewVideoCount(finalFolderPath, nextCount)
                        settingsRepository.markFolderHasNewVideo(finalFolderPath)
                    }
                    result
                }
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                val statusMsg = when {
                    moved.isEmpty() && moveErrorMessage != null -> "Move failed: $moveErrorMessage"
                    moved.isEmpty() -> "Move failed: Could not move media file to destination"
                    moveErrorMessage != null -> "Moved ${moved.size} video(s) to $finalFolderName • Move failed for duplicate: $moveErrorMessage"
                    else -> "Fast-moved ${moved.size} video(s) to $finalFolderName (${elapsedMs}ms)"
                }
                _uiState.update {
                    it.copy(
                        isBrowserSelectionMode = false,
                        selectedFolderPaths = emptySet(),
                        selectedVideoPaths = emptySet(),
                        holdContextVideos = emptyList(),
                        showDestinationPickerDialog = false,
                        showHoldActionSheet = false,
                        pendingTransferOperation = null,
                        pendingBatchMoveDestination = null,
                        pendingBatchMoveCustomSubfolder = "",
                        pendingMovePermissionRequest = null,
                        clipboardState = null,
                        fileTransferProgress = null,
                        isFileOperationBusy = false,
                        fileOperationStatusText = null,
                        statusBannerMessage = statusMsg
                    )
                }
            }
            refreshLibrary()
        }
    }

    /**
     * Exact ViewModel equivalent of the user's onMove(File file, String destDir):
     * - Calls VideoPlayerFileMover.moveFile(file, newPath)
     * - Removes the file from the ORIGINAL list and folder list immediately
     * - On error (such as destination file already existing), sets statusBannerMessage = "Move failed: $e"
     */
    fun onMove(file: File, destDir: String): Boolean {
        return try {
            releasePlayerHandleIfMatching(Uri.fromFile(file), file.absolutePath)
            val destFolder = File(destDir)
            val newPath = File(destFolder, file.name).absolutePath
            val movedFile = com.cyosplay.videoplayer.util.VideoPlayerFileMover.moveFile(file, newPath)
            val resolvedUri = com.cyosplay.videoplayer.util.VideoPlayerFileMover.getVideoContentUriFromPath(
                appContext,
                movedFile.absolutePath
            ) ?: Uri.fromFile(movedFile)
            val currentCatalog = videoRepository.getOrCreateManagedCatalog().toMutableList()
            val updatedCatalog = currentCatalog.map { item ->
                if (item.path.equals(file.absolutePath, ignoreCase = true)) {
                    item.copy(
                        uri = resolvedUri,
                        path = movedFile.absolutePath,
                        folderId = destFolder.absolutePath.lowercase().hashCode().toString(),
                        folderName = destFolder.name.ifBlank { "Moved" },
                        folderPath = destFolder.absolutePath
                    )
                } else {
                    item
                }
            }
            videoRepository.saveManagedCatalog(updatedCatalog)
            settingsRepository.relinkMovedVideoReferences(file.absolutePath, movedFile.absolutePath)
            playlistRepository.relinkMovedVideoReferences(file.absolutePath, movedFile.absolutePath)
            _uiState.update { state ->
                val remainingVideos = state.allVideos.filterNot {
                    it.path.equals(file.absolutePath, ignoreCase = true)
                }
                val remainingFolders = state.folders.mapNotNull { folder ->
                    val folderVids = folder.videos.filterNot {
                        it.path.equals(file.absolutePath, ignoreCase = true)
                    }
                    if (folderVids.isEmpty()) null else folder.copy(videos = folderVids)
                }
                state.copy(
                    allVideos = remainingVideos,
                    folders = remainingFolders,
                    statusBannerMessage = "Moved ${movedFile.name} to ${destFolder.name}"
                )
            }
            refreshLibrary()
            true
        } catch (e: Throwable) {
            _uiState.update { state ->
                state.copy(statusBannerMessage = "Move failed: ${e.message}")
            }
            false
        }
    }

    fun moveFile(
        context: android.content.Context = appContext,
        sourceFile: File,
        destDir: File,
        onMoved: () -> Unit
    ): Boolean {
        return videoRepository.moveFile(
            context = context,
            sourceFile = sourceFile,
            destDir = destDir,
            onMoved = {
                _uiState.update { state ->
                    val updatedList = state.allVideos.filterNot {
                        it.path.equals(sourceFile.absolutePath, ignoreCase = true)
                    }
                    val updatedFolders = state.folders.mapNotNull { folder ->
                        val remaining = folder.videos.filterNot {
                            it.path.equals(sourceFile.absolutePath, ignoreCase = true)
                        }
                        if (remaining.isEmpty()) null else folder.copy(videos = remaining)
                    }
                    state.copy(
                        allVideos = updatedList,
                        folders = updatedFolders
                    )
                }
                onMoved()
            }
        )
    }

    suspend fun moveMediaFileViaMediaStore(
        sourceUri: Uri,
        relativeDestFolder: String,
        onMoved: () -> Unit = {}
    ): com.cyosplay.player.app.util.MediaStoreFileMover.MoveResult {
        val result = com.cyosplay.player.app.util.MediaStoreFileMover.moveMediaFile(
            context = appContext,
            sourceUri = sourceUri,
            relativeDestFolder = relativeDestFolder
        )
        if (result is com.cyosplay.player.app.util.MediaStoreFileMover.MoveResult.Success) {
            _uiState.update { state ->
                val updatedList = state.allVideos.toMutableList()
                val pos = updatedList.indexOfFirst {
                    it.uri == sourceUri || it.path.equals(sourceUri.path, ignoreCase = true)
                }
                if (pos >= 0) {
                    updatedList.removeAt(pos)
                }
                state.copy(allVideos = updatedList)
            }
            onMoved()
        }
        return result
    }

    suspend fun moveVideoViaVideoPlayerFileMover(
        videoUri: Uri,
        targetFolderRelativePath: String,
        legacySourcePath: String? = null,
        onMoved: (com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.Success) -> Unit = {}
    ): com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult {
        // 1. Stop player if the video being moved is currently loaded in ExoPlayer or Mini-Player
        releasePlayerHandleIfMatching(videoUri, legacySourcePath)

        // 2. Move the video in MediaStore / storage
        val result = com.cyosplay.videoplayer.util.VideoPlayerFileMover.moveVideoFile(
            context = appContext,
            videoUri = videoUri,
            targetFolderRelativePath = targetFolderRelativePath,
            legacySourcePath = legacySourcePath
        )

        when (result) {
            is com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.Success -> {
                val matchPath = legacySourcePath ?: videoUri.path
                val currentCatalog = videoRepository.getOrCreateManagedCatalog().toMutableList()
                val newPath = result.newFilePath ?: result.movedUri.path ?: result.newRelativePath.trimEnd('/')
                val newFolderFile = File(newPath).let {
                    if (it.extension.isNotBlank() || it.isFile) it.parentFile ?: it else File(targetFolderRelativePath)
                }
                val newFolderPath = newFolderFile.absolutePath
                val newFolderName = newFolderFile.name.ifBlank { "Moved" }
                var resolvedMovedPath: String? = result.newFilePath
                var oldMatchedPath: String? = matchPath
                val updatedCatalog = currentCatalog.map { existing ->
                    val isMovedItem = existing.uri == videoUri ||
                        (matchPath != null && existing.path.equals(matchPath, ignoreCase = true))
                    if (isMovedItem) {
                        oldMatchedPath = existing.path
                        val destFile = when {
                            !result.newFilePath.isNullOrBlank() -> File(result.newFilePath)
                            File(newPath).exists() && File(newPath).isFile -> File(newPath)
                            else -> File(newFolderFile, existing.displayName)
                        }
                        resolvedMovedPath = destFile.absolutePath
                        existing.copy(
                            uri = result.movedUri,
                            path = destFile.absolutePath,
                            folderPath = newFolderPath,
                            folderName = newFolderName,
                            folderId = newFolderPath.lowercase().hashCode().toString()
                        )
                    } else {
                        existing
                    }
                }
                videoRepository.saveManagedCatalog(updatedCatalog)
                val finalOldPath = oldMatchedPath
                val finalNewPath = resolvedMovedPath
                if (!finalOldPath.isNullOrBlank() && !finalNewPath.isNullOrBlank()) {
                    settingsRepository.relinkMovedVideoReferences(finalOldPath, finalNewPath)
                    playlistRepository.relinkMovedVideoReferences(finalOldPath, finalNewPath)
                }

                // 3. Remove from the current folder's UI list immediately
                _uiState.update { state ->
                    val updatedList = state.allVideos.filterNot {
                        it.uri == videoUri || (matchPath != null && it.path.equals(matchPath, ignoreCase = true))
                    }
                    val updatedFolders = state.folders.mapNotNull { folder ->
                        val remaining = folder.videos.filterNot {
                            it.uri == videoUri || (matchPath != null && it.path.equals(matchPath, ignoreCase = true))
                        }
                        if (remaining.isEmpty()) null else folder.copy(videos = remaining)
                    }
                    state.copy(
                        allVideos = updatedList,
                        folders = updatedFolders,
                        pendingVideoUri = null,
                        pendingTargetFolder = null,
                        pendingMovePermissionRequest = null
                    )
                }
                onMoved(result)
                // 4. Refresh the folder list and video counts from MediaStore
                refreshLibrary()
            }
            is com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.NeedsPermission -> {
                _uiState.update { state ->
                    state.copy(
                        pendingVideoUri = result.videoUri,
                        pendingTargetFolder = result.targetRelativePath,
                        pendingMovePermissionRequest = result
                    )
                }
            }
            is com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.Error -> {
                _uiState.update { state ->
                    state.copy(
                        pendingVideoUri = null,
                        pendingTargetFolder = null,
                        pendingMovePermissionRequest = null,
                        statusBannerMessage = "Could not move video: ${result.throwable?.localizedMessage ?: result.message}"
                    )
                }
            }
        }
        return result
    }

    fun moveVideoOnPlayer(
        videoUri: Uri,
        targetFolderRelativePath: String,
        legacySourcePath: String? = null
    ) {
        viewModelScope.launch {
            moveVideoViaVideoPlayerFileMover(
                videoUri = videoUri,
                targetFolderRelativePath = targetFolderRelativePath,
                legacySourcePath = legacySourcePath
            )
        }
    }

    fun onMoveVideoPermissionResult(granted: Boolean) {
        val batchDest = _uiState.value.pendingBatchMoveDestination
        val batchCustom = _uiState.value.pendingBatchMoveCustomSubfolder
        val uri = _uiState.value.pendingVideoUri
        val folder = _uiState.value.pendingTargetFolder
        _uiState.update {
            it.copy(
                pendingVideoUri = null,
                pendingTargetFolder = null,
                pendingMovePermissionRequest = null,
                pendingBatchMoveDestination = null,
                pendingBatchMoveCustomSubfolder = ""
            )
        }
        if (granted) {
            if (batchDest != null) {
                executeCopyOrMoveToDestination(
                    destination = batchDest,
                    customNewFolderName = batchCustom,
                    skipWritePermissionCheck = true
                )
            } else if (uri != null && folder != null) {
                moveVideoOnPlayer(uri, folder)
            }
        } else {
            _uiState.update {
                it.copy(statusBannerMessage = "Move cancelled: Permission denied — original file kept intact")
            }
        }
    }

    fun dismissFileTransferProgress() {
        _uiState.update { it.copy(fileTransferProgress = null) }
    }

    /**
     * Executes clipboard paste immediately on first action in v1.06rv8 without extra confirmation dialogs.
     */
    fun requestPasteClipboardConfirmation(
        targetFolderPath: String,
        targetFolderName: String,
        isRemovableSd: Boolean = false
    ) {
        pasteClipboardIntoFolder(
            targetFolderPath = targetFolderPath,
            targetFolderName = targetFolderName,
            isRemovableSd = isRemovableSd
        )
    }

    fun pasteClipboardIntoFolder(targetFolderPath: String, targetFolderName: String, isRemovableSd: Boolean = false) {
        val state = _uiState.value
        val clip = state.clipboardState
        val sourceVideos = clip?.sourceVideos?.ifEmpty { null }
            ?: getActiveHoldTargetVideos()
        if (sourceVideos.isEmpty()) return
        val op = clip?.operation ?: state.pendingHoldOperation
        val dest = MoveDestinationOption(
            id = "paste_target_${targetFolderPath.hashCode()}",
            title = targetFolderName,
            subtitle = targetFolderPath,
            targetPath = targetFolderPath,
            isPrivateVault = false,
            isRemovableSd = isRemovableSd || CryptoSecurityManager.isRemovableSdCardPath(targetFolderPath)
        )
        _uiState.update {
            it.copy(
                holdContextVideos = sourceVideos,
                pendingHoldOperation = op
            )
        }
        executeCopyOrMoveToDestination(dest)
    }

    fun moveVideosDirectlyToPrivateVault(videos: List<VideoItem> = getActiveHoldTargetVideos()) {
        if (videos.isEmpty()) return
        val isDecoy = _uiState.value.vaultAuthState == VaultAuthState.UNLOCKED_DECOY
        videos.forEach { releasePlayerHandleIfMatching(it.uri, it.path) }
        refreshLibraryJob?.cancel()
        _uiState.update {
            it.copy(
                isFileOperationBusy = true,
                fileOperationStatusText = "Encrypting ${videos.size} video(s) into Private Vault…"
            )
        }
        viewModelScope.launch {
            val moveResults = kotlinx.coroutines.withContext(Dispatchers.IO) {
                videos.map { video ->
                    video to vaultManager.moveVideoToVaultWithDeleteRequest(video, isDecoyEntry = isDecoy)
                }
            }
            val succeededPaths = moveResults
                .filter { (_, result) -> result.entry != null }
                .map { (video, _) -> video.path }
                .toSet()
            val urisNeedingScopedDelete = moveResults
                .mapNotNull { (_, result) -> result.pendingSourceDeleteUri }
                .distinct()

            var pendingDeleteSender: android.content.IntentSender? = moveResults
                .firstNotNullOfOrNull { (_, result) -> result.pendingSourceDeleteIntentSender }
            if (pendingDeleteSender == null && urisNeedingScopedDelete.isNotEmpty() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                pendingDeleteSender = runCatching {
                    android.provider.MediaStore.createDeleteRequest(
                        appContext.contentResolver,
                        urisNeedingScopedDelete
                    ).intentSender
                }.getOrNull()
            }

            if (succeededPaths.isNotEmpty()) {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    videoRepository.removeVideosFromCatalog(succeededPaths, shredFilesOnDisk = false)
                }
            }
            val updatedEntries = kotlinx.coroutines.withContext(Dispatchers.IO) {
                vaultManager.getVaultEntries(isDecoy)
            }
            val failedCount = videos.size - succeededPaths.size
            val statusMsg = when {
                succeededPaths.isEmpty() -> "Vault encryption failed: Source video file missing or unreadable"
                pendingDeleteSender != null -> "Encrypted ${succeededPaths.size} video(s) to Vault — confirm deleting the unencrypted original from gallery"
                failedCount > 0 -> "Encrypted ${succeededPaths.size} video(s) to Vault ($failedCount skipped: source missing)"
                else -> "Encrypted & moved ${succeededPaths.size} video(s) to Private Vault"
            }
            _uiState.update {
                it.copy(
                    selectedVideoPaths = emptySet(),
                    holdContextVideos = if (pendingDeleteSender != null) {
                        videos.filter { v -> v.path in succeededPaths }
                    } else {
                        emptyList()
                    },
                    showHoldActionSheet = false,
                    showDestinationPickerDialog = false,
                    pendingDeleteIntentSender = pendingDeleteSender ?: it.pendingDeleteIntentSender,
                    vaultEntries = updatedEntries,
                    vaultErrorMessage = if (succeededPaths.isEmpty()) statusMsg else null,
                    isFileOperationBusy = false,
                    fileOperationStatusText = null,
                    statusBannerMessage = statusMsg
                )
            }
            refreshLibrary()
        }
    }

    fun collectAllTargetDeleteUris(): List<Uri> {
        val targets = getActiveHoldTargetVideos()
        val uris = mutableListOf<Uri>()
        targets.forEach { video ->
            val resolved = if (video.uri.scheme.equals("content", ignoreCase = true)) {
                video.uri
            } else {
                com.cyosplay.player.util.MediaStoreFileHelper.queryExistingMediaStoreUri(
                    appContext.contentResolver,
                    video.path
                ) ?: video.uri
            }
            uris.add(resolved)
        }
        return uris.distinct()
    }

    /**
     * Deletes the held/selected videos and/or folders permanently from device storage & MediaStore.
     * On Android 10–17, if any selected file is owned by another app and requires system authorization
     * (MediaStore.createDeleteRequest on API 30–37 or RecoverableSecurityException on API 29),
     * populates pendingDeleteIntentSender so the system confirmation prompt is launched.
     */
    fun requestDeleteHeldVideos() {
        val targets = getActiveHoldTargetVideos()
        val folderPaths = _uiState.value.selectedFolderPaths
        if (targets.isEmpty() && folderPaths.isEmpty()) return

        targets.forEach { target ->
            releasePlayerHandleIfMatching(target.uri, target.path)
        }

        // Probe if any target requires Android 10–17 MediaStore user confirmation
        val contentUrisRequiringConsent = mutableListOf<Uri>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            for (video in targets) {
                val file = File(video.path)
                val resolvedContentUri = if (video.uri.scheme.equals("content", ignoreCase = true)) {
                    video.uri
                } else {
                    com.cyosplay.player.util.MediaStoreFileHelper.queryExistingMediaStoreUri(
                        appContext.contentResolver,
                        video.path
                    )
                }
                if (resolvedContentUri != null) {
                    try {
                        val rows = appContext.contentResolver.delete(resolvedContentUri, null, null)
                        if (rows > 0) {
                            if (file.exists()) file.delete()
                        } else if (file.exists() && !file.delete() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            contentUrisRequiringConsent.add(resolvedContentUri)
                        }
                    } catch (secEx: SecurityException) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            contentUrisRequiringConsent.add(resolvedContentUri)
                        } else if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q &&
                            secEx is android.app.RecoverableSecurityException
                        ) {
                            com.cyosplay.player.util.MediaStoreFileHelper.recordPendingRecoverableDelete(
                                resolvedContentUri,
                                video.path
                            )
                            _uiState.update {
                                it.copy(
                                    holdContextVideos = targets,
                                    showHoldActionSheet = false,
                                    showDeleteConfirmDialog = false,
                                    pendingDeleteIntentSender = secEx.userAction.actionIntent.intentSender
                                )
                            }
                            return
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        if (contentUrisRequiringConsent.isNotEmpty() &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        ) {
            val intentSender = runCatching {
                android.provider.MediaStore.createDeleteRequest(
                    appContext.contentResolver,
                    contentUrisRequiringConsent.distinct()
                ).intentSender
            }.getOrNull()
            if (intentSender != null) {
                _uiState.update {
                    it.copy(
                        holdContextVideos = targets,
                        showHoldActionSheet = false,
                        showDeleteConfirmDialog = false,
                        pendingDeleteIntentSender = intentSender
                    )
                }
                return
            }
        }

        _uiState.update {
            it.copy(
                holdContextVideos = targets,
                showHoldActionSheet = false,
                showDeleteConfirmDialog = false,
                pendingDeleteIntentSender = null,
                statusBannerMessage = null
            )
        }
        confirmDeleteVideos(permanentZeroTraceShred = true)
    }

    /**
     * Opens the Delete / Recycle Bin options dialog for the currently selected or held videos
     * so the user can choose between moving to Recycle Bin or deleting permanently, with zero post-delete notifications.
     */
    fun openDeleteOrRecycleDialog() {
        val targets = getActiveHoldTargetVideos()
        if (targets.isEmpty() && _uiState.value.selectedFolderPaths.isEmpty()) return
        _uiState.update {
            it.copy(
                holdContextVideos = targets,
                showHoldActionSheet = false,
                showDeleteConfirmDialog = true,
                statusBannerMessage = null
            )
        }
    }

    /**
     * Moves the currently held or selected videos into the Encrypted Recycle Bin silently (no toast/snackbar notification).
     */
    fun moveHeldVideosToRecycleBin() {
        confirmDeleteVideos(permanentZeroTraceShred = false)
    }

    fun clearPendingDeleteIntentSender() {
        _uiState.update { it.copy(pendingDeleteIntentSender = null) }
    }

    fun onDeletePermissionDenied() {
        com.cyosplay.player.util.MediaStoreFileHelper.clearPendingRecoverableDeletes()
        _uiState.update {
            it.copy(
                pendingDeleteIntentSender = null,
                pendingRecycleBinDeleteIds = emptySet(),
                showDeleteConfirmDialog = false,
                showHoldActionSheet = false,
                statusBannerMessage = "Deletion cancelled: Permission denied — media file kept intact"
            )
        }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update {
            it.copy(
                showDeleteConfirmDialog = false,
                pendingDeleteIntentSender = null
            )
        }
    }

    /**
     * Called when a video URI has been confirmed deleted from MediaStore (either immediately on API <= 29
     * or via the system delete confirmation dialog on Android 10 / 11+).
     * Verifies the file is genuinely deleted from storage before purging from the catalog, favorites, playlists, and UI state.
     * Performs deletion silently without any post-delete notification.
     */
    fun onMediaStoreVideoDeleted(videoUri: Uri) {
        releasePlayerHandleIfMatching(videoUri)
        com.cyosplay.player.util.MediaStoreFileHelper.completePendingRecoverableDeletes(appContext)
        val rawPath = if (videoUri.scheme.equals("file", ignoreCase = true)) {
            videoUri.path
        } else {
            com.cyosplay.player.util.MediaStoreFileHelper.queryFilePathFromContentUri(
                appContext.contentResolver,
                videoUri
            ) ?: videoUri.path
        }
        val matchedItems = (_uiState.value.allVideos + videoRepository.getOrCreateManagedCatalog())
            .filter { item ->
                item.uri == videoUri ||
                    (!rawPath.isNullOrBlank() && item.path.equals(rawPath, ignoreCase = true))
            }
        val candidatePaths = buildSet {
            if (!rawPath.isNullOrBlank()) add(rawPath)
            matchedItems.forEach { add(it.path) }
        }
        val verifiedDeletedPaths = if (candidatePaths.isNotEmpty()) {
            videoRepository.removeVideosFromCatalog(candidatePaths, shredFilesOnDisk = true)
        } else {
            emptySet()
        }

        val actuallyDeleted = com.cyosplay.player.util.MediaStoreFileHelper.isMediaActuallyDeleted(
            context = appContext,
            uri = videoUri,
            filePath = rawPath ?: matchedItems.firstOrNull()?.path
        )

        if (!actuallyDeleted && verifiedDeletedPaths.isEmpty() && candidatePaths.any { File(it).exists() }) {
            _uiState.update { state ->
                state.copy(
                    showDeleteConfirmDialog = false,
                    showHoldActionSheet = false,
                    pendingDeleteIntentSender = null,
                    statusBannerMessage = null
                )
            }
            return
        }

        if (verifiedDeletedPaths.isNotEmpty()) {
            settingsRepository.clearCachedReferencesForVideoPaths(verifiedDeletedPaths)
            playlistRepository.removeDeletedVideoPaths(verifiedDeletedPaths)
        }
        _uiState.update { state ->
            val updatedVideos = state.allVideos.filterNot { item ->
                (actuallyDeleted && item.uri == videoUri) ||
                    verifiedDeletedPaths.any { it.equals(item.path, ignoreCase = true) }
            }
            val updatedFolders = state.folders.mapNotNull { folder ->
                val remaining = folder.videos.filterNot { item ->
                    (actuallyDeleted && item.uri == videoUri) ||
                        verifiedDeletedPaths.any { it.equals(item.path, ignoreCase = true) }
                }
                if (remaining.isEmpty()) null else folder.copy(videos = remaining)
            }
            state.copy(
                allVideos = updatedVideos,
                folders = updatedFolders,
                playlists = playlistRepository.getPlaylists(),
                favoriteVideoPaths = playlistRepository.getFavoriteVideoPaths(),
                playbackProgressMap = settingsRepository.getAllPlaybackProgress(),
                showDeleteConfirmDialog = false,
                showHoldActionSheet = false,
                pendingDeleteIntentSender = null,
                statusBannerMessage = null
            )
        }
        refreshLibrary()
    }

    /**
     * Callback invoked by CyosVideoDeleteController once MediaStore confirms deletion of one or more videos
     * and their matching external subtitle files.
     */
    fun onVideosDeletedFromMediaStore(deletedUris: List<Uri>, deletedPaths: List<String>) {
        deletedUris.forEach { uri ->
            releasePlayerHandleIfMatching(uri)
        }
        deletedPaths.forEach { path ->
            if (path.isNotBlank()) {
                releasePlayerHandleIfMatching(Uri.fromFile(File(path)), path)
            }
        }
        val matchedItems = (_uiState.value.allVideos + videoRepository.getOrCreateManagedCatalog())
            .filter { item ->
                deletedUris.contains(item.uri) ||
                    deletedPaths.any { it.equals(item.path, ignoreCase = true) }
            }
        val candidatePaths = buildSet {
            deletedPaths.forEach { if (it.isNotBlank()) add(it) }
            matchedItems.forEach { add(it.path) }
        }
        val verifiedDeletedPaths = if (candidatePaths.isNotEmpty()) {
            videoRepository.removeVideosFromCatalog(candidatePaths, shredFilesOnDisk = true)
        } else {
            emptySet()
        }
        val allRemovedPaths = candidatePaths + verifiedDeletedPaths
        if (allRemovedPaths.isNotEmpty()) {
            settingsRepository.clearCachedReferencesForVideoPaths(allRemovedPaths)
            playlistRepository.removeDeletedVideoPaths(allRemovedPaths)
        }
        _uiState.update { state ->
            val updatedVideos = state.allVideos.filterNot { item ->
                deletedUris.contains(item.uri) ||
                    allRemovedPaths.any { it.equals(item.path, ignoreCase = true) }
            }
            val updatedFolders = state.folders.mapNotNull { folder ->
                val remaining = folder.videos.filterNot { item ->
                    deletedUris.contains(item.uri) ||
                        allRemovedPaths.any { it.equals(item.path, ignoreCase = true) }
                }
                if (remaining.isEmpty()) null else folder.copy(videos = remaining)
            }
            state.copy(
                allVideos = updatedVideos,
                folders = updatedFolders,
                playlists = playlistRepository.getPlaylists(),
                favoriteVideoPaths = playlistRepository.getFavoriteVideoPaths(),
                playbackProgressMap = settingsRepository.getAllPlaybackProgress(),
                showDeleteConfirmDialog = false,
                showHoldActionSheet = false,
                pendingDeleteIntentSender = null,
                statusBannerMessage = null
            )
        }
        refreshLibrary()
    }

    /**
     * Executes batch deletion of all selected files and folders (either moving to Recycle Bin or permanent shred):
     * Permanently removes the file from storage, verifies actual deletion, removes associated cached
     * thumbnails, progress, playlists, and subtitle references, and immediately updates the UI state
     * silently without displaying any post-delete notification banner.
     */
    fun confirmDeleteVideos(permanentZeroTraceShred: Boolean = true) {
        val targets = getActiveHoldTargetVideos()
        val folderPaths = _uiState.value.selectedFolderPaths
        if (targets.isEmpty() && folderPaths.isEmpty()) return
        targets.forEach { target ->
            releasePlayerHandleIfMatching(target.uri, target.path)
        }
        com.cyosplay.player.util.MediaStoreFileHelper.completePendingRecoverableDeletes(appContext)
        val paths = targets.map { it.path }.toSet()

        if (!permanentZeroTraceShred) {
            val folderCandidates = if (folderPaths.isNotEmpty()) {
                val normFolders = folderPaths.map { it.trim().trimEnd('/').lowercase() }.toSet()
                (_uiState.value.allVideos + videoRepository.getOrCreateManagedCatalog())
                    .filter { video ->
                        val vFolder = video.folderPath.trim().trimEnd('/').lowercase()
                        val vParent = File(video.path).parent?.trim()?.trimEnd('/')?.lowercase().orEmpty()
                        vFolder in normFolders || vParent in normFolders ||
                            normFolders.any { nf -> vFolder.startsWith("$nf/") || vParent.startsWith("$nf/") }
                    }
            } else {
                emptyList()
            }
            val allRecycleTargets = (targets + folderCandidates).distinctBy { it.path.lowercase() }
            val movedPaths = mutableSetOf<String>()
            allRecycleTargets.forEach { video ->
                releasePlayerHandleIfMatching(video.uri, video.path)
                val item = recycleBinRepository.moveVideoToRecycleBin(video)
                if (item != null) {
                    movedPaths.add(video.path)
                }
            }
            if (movedPaths.isNotEmpty() || folderPaths.isNotEmpty()) {
                videoRepository.removeFoldersAndVideosFromCatalog(
                    folderPaths = folderPaths,
                    videoPaths = movedPaths,
                    shredFilesOnDisk = false
                )
            }
            if (movedPaths.isNotEmpty()) {
                settingsRepository.clearCachedReferencesForVideoPaths(movedPaths)
                playlistRepository.removeDeletedVideoPaths(movedPaths)
            }
            _uiState.update { state ->
                val updatedVideos = state.allVideos.filterNot { item ->
                    movedPaths.any { it.equals(item.path, ignoreCase = true) }
                }
                val updatedFolders = state.folders.mapNotNull { folder ->
                    val remaining = folder.videos.filterNot { item ->
                        movedPaths.any { it.equals(item.path, ignoreCase = true) }
                    }
                    if (remaining.isEmpty()) null else folder.copy(videos = remaining)
                }
                state.copy(
                    allVideos = updatedVideos,
                    folders = updatedFolders,
                    playlists = playlistRepository.getPlaylists(),
                    favoriteVideoPaths = playlistRepository.getFavoriteVideoPaths(),
                    playbackProgressMap = settingsRepository.getAllPlaybackProgress(),
                    recycleBinItems = recycleBinRepository.getRecycleBinItems(),
                    isBrowserSelectionMode = false,
                    selectedFolderPaths = emptySet(),
                    selectedVideoPaths = emptySet(),
                    holdContextVideos = emptyList(),
                    showDeleteConfirmDialog = false,
                    showHoldActionSheet = false,
                    pendingDeleteIntentSender = null,
                    pendingRecycleBinDeleteIds = emptySet(),
                    statusBannerMessage = null
                )
            }
            refreshLibrary()
            return
        }

        // Also attempt MediaStore deletion for any content:// URIs owned by the app or on API <= 29
        targets.forEach { target ->
            if (target.uri.scheme.equals("content", ignoreCase = true)) {
                runCatching { appContext.contentResolver.delete(target.uri, null, null) }
            }
        }

        val verifiedDeletedPaths = videoRepository.removeFoldersAndVideosFromCatalog(
            folderPaths = folderPaths,
            videoPaths = paths,
            shredFilesOnDisk = true
        )

        // If none of the target files could be deleted from storage and they still exist on disk, do NOT hide them
        if (verifiedDeletedPaths.isEmpty() && paths.isNotEmpty() && paths.any { File(it).exists() }) {
            _uiState.update {
                it.copy(
                    showDeleteConfirmDialog = false,
                    showHoldActionSheet = false,
                    pendingDeleteIntentSender = null,
                    statusBannerMessage = null
                )
            }
            return
        }

        if (verifiedDeletedPaths.isNotEmpty()) {
            settingsRepository.clearCachedReferencesForVideoPaths(verifiedDeletedPaths)
            playlistRepository.removeDeletedVideoPaths(verifiedDeletedPaths)
        }

        _uiState.update { state ->
            val updatedVideos = state.allVideos.filterNot { item ->
                verifiedDeletedPaths.any { it.equals(item.path, ignoreCase = true) }
            }
            val updatedFolders = state.folders.mapNotNull { folder ->
                val remaining = folder.videos.filterNot { item ->
                    verifiedDeletedPaths.any { it.equals(item.path, ignoreCase = true) }
                }
                if (remaining.isEmpty()) null else folder.copy(videos = remaining)
            }
            state.copy(
                allVideos = updatedVideos,
                folders = updatedFolders,
                playlists = playlistRepository.getPlaylists(),
                favoriteVideoPaths = playlistRepository.getFavoriteVideoPaths(),
                playbackProgressMap = settingsRepository.getAllPlaybackProgress(),
                isBrowserSelectionMode = false,
                selectedFolderPaths = emptySet(),
                selectedVideoPaths = emptySet(),
                holdContextVideos = emptyList(),
                showDeleteConfirmDialog = false,
                showHoldActionSheet = false,
                pendingDeleteIntentSender = null,
                statusBannerMessage = null
            )
        }
        refreshLibrary()
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    // --- Recycle Bin Actions ---

    fun restoreRecycleBinItem(recycleId: String) {
        refreshLibraryJob?.cancel()
        _uiState.update {
            it.copy(
                isFileOperationBusy = true,
                fileOperationStatusText = "Restoring video from Recycle Bin…"
            )
        }
        viewModelScope.launch {
            val restored = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val res = recycleBinRepository.restoreFromRecycleBin(recycleId)
                if (res != null) {
                    val ext = java.io.File(res.originalPath).extension.ifBlank { "mp4" }
                    val currentCatalog = videoRepository.getOrCreateManagedCatalog().toMutableList()
                    if (currentCatalog.none { it.path == res.originalPath }) {
                        currentCatalog.add(
                            0,
                            VideoItem(
                                id = res.originalVideoId,
                                title = res.title,
                                displayName = "${res.title}.$ext",
                                uri = Uri.parse(res.originalUriString.ifBlank { "file://${res.originalPath}" }),
                                path = res.originalPath,
                                sizeBytes = res.sizeBytes,
                                durationMs = res.durationMs,
                                dateModifiedSeconds = System.currentTimeMillis() / 1000L,
                                width = res.width,
                                height = res.height,
                                mimeType = VideoRepository.resolveVideoMimeType(ext),
                                folderId = res.folderName.lowercase().hashCode().toString(),
                                folderName = res.folderName,
                                folderPath = res.originalPath.substringBeforeLast('/', "/storage/emulated/0/Movies")
                            )
                        )
                        videoRepository.saveManagedCatalog(currentCatalog)
                    }
                }
                res
            }
            if (restored != null) {
                _uiState.update {
                    it.copy(
                        isFileOperationBusy = false,
                        fileOperationStatusText = null,
                        statusBannerMessage = "Restored \"${restored.title}\""
                    )
                }
                refreshLibrary()
            } else {
                val remaining = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    recycleBinRepository.getRecycleBinItems()
                }
                _uiState.update {
                    it.copy(
                        recycleBinItems = remaining,
                        isFileOperationBusy = false,
                        fileOperationStatusText = null,
                        statusBannerMessage = "Decryption failed: Encrypted recycle bin item kept intact to prevent data loss"
                    )
                }
            }
        }
    }

    fun deleteRecycleBinItemPermanently(recycleId: String) {
        val targetItem = recycleBinRepository.getRecycleBinItems().find { it.recycleId == recycleId } ?: return
        val targetUri = targetItem.originalUriString.takeIf { it.isNotBlank() }?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }
        if (targetUri != null) {
            releasePlayerHandleIfMatching(targetUri, targetItem.originalPath)
        }
        com.cyosplay.player.util.MediaStoreFileHelper.completePendingRecoverableDeletes(appContext)

        val deletedImmediately = recycleBinRepository.shredRecycleItemPermanently(recycleId)
        if (deletedImmediately) {
            val pathSet = setOf(targetItem.originalPath)
            videoRepository.removeVideosFromCatalog(pathSet, shredFilesOnDisk = true)
            settingsRepository.clearCachedReferencesForVideoPaths(pathSet)
            playlistRepository.removeDeletedVideoPaths(pathSet)
            _uiState.update { state ->
                state.copy(
                    recycleBinItems = recycleBinRepository.getRecycleBinItems(),
                    allVideos = state.allVideos.filterNot { it.path.equals(targetItem.originalPath, ignoreCase = true) },
                    playlists = playlistRepository.getPlaylists(),
                    favoriteVideoPaths = playlistRepository.getFavoriteVideoPaths(),
                    playbackProgressMap = settingsRepository.getAllPlaybackProgress(),
                    pendingRecycleBinDeleteIds = emptySet(),
                    pendingDeleteIntentSender = null,
                    statusBannerMessage = null
                )
            }
            refreshLibrary()
            return
        }

        // If the underlying file is owned by another app on Android 11–17, request system delete authorization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contentUris = recycleBinRepository.resolveTargetUrisForRecycleBinItems(setOf(recycleId))
                .filter { it.scheme.equals("content", ignoreCase = true) }
            if (contentUris.isNotEmpty()) {
                runCatching {
                    val pendingIntent = MediaStore.createDeleteRequest(appContext.contentResolver, contentUris)
                    _uiState.update {
                        it.copy(
                            pendingRecycleBinDeleteIds = setOf(recycleId),
                            pendingDeleteIntentSender = pendingIntent.intentSender,
                            statusBannerMessage = null
                        )
                    }
                    return
                }
            }
        }

        _uiState.update {
            it.copy(
                recycleBinItems = recycleBinRepository.getRecycleBinItems(),
                statusBannerMessage = null
            )
        }
    }

    fun emptyRecycleBin() {
        val currentItems = recycleBinRepository.getRecycleBinItems()
        if (currentItems.isEmpty()) return
        currentItems.forEach { item ->
            val uri = item.originalUriString.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
            if (uri != null) {
                releasePlayerHandleIfMatching(uri, item.originalPath)
            }
        }
        refreshLibraryJob?.cancel()
        _uiState.update {
            it.copy(
                isFileOperationBusy = true,
                fileOperationStatusText = "Shredding Recycle Bin (${currentItems.size} item(s))…"
            )
        }
        viewModelScope.launch {
            val beforePaths = currentItems.map { it.originalPath }.toSet()
            val outcome = kotlinx.coroutines.withContext(Dispatchers.IO) {
                com.cyosplay.player.util.MediaStoreFileHelper.completePendingRecoverableDeletes(appContext)
                recycleBinRepository.emptyRecycleBinZeroTrace()
                val remainingItems = recycleBinRepository.getRecycleBinItems()
                val remainingPaths = remainingItems.map { it.originalPath }.toSet()
                val verifiedDeletedPaths = beforePaths - remainingPaths

                if (verifiedDeletedPaths.isNotEmpty()) {
                    videoRepository.removeVideosFromCatalog(verifiedDeletedPaths, shredFilesOnDisk = true)
                    settingsRepository.clearCachedReferencesForVideoPaths(verifiedDeletedPaths)
                    playlistRepository.removeDeletedVideoPaths(verifiedDeletedPaths)
                }
                Triple(remainingItems, verifiedDeletedPaths, playlistRepository.getPlaylists())
            }
            val remainingItems = outcome.first
            val verifiedDeletedPaths = outcome.second
            val updatedPlaylists = outcome.third

            if (remainingItems.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val remainingIds = remainingItems.map { it.recycleId }.toSet()
                val contentUris = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    recycleBinRepository.resolveTargetUrisForRecycleBinItems(remainingIds)
                        .filter { it.scheme.equals("content", ignoreCase = true) }
                }
                if (contentUris.isNotEmpty()) {
                    runCatching {
                        val pendingIntent = MediaStore.createDeleteRequest(appContext.contentResolver, contentUris)
                        _uiState.update { state ->
                            state.copy(
                                recycleBinItems = remainingItems,
                                allVideos = state.allVideos.filterNot { item ->
                                    verifiedDeletedPaths.any { it.equals(item.path, ignoreCase = true) }
                                },
                                pendingRecycleBinDeleteIds = remainingIds,
                                pendingDeleteIntentSender = pendingIntent.intentSender,
                                isFileOperationBusy = false,
                                fileOperationStatusText = null,
                                statusBannerMessage = null
                            )
                        }
                        return@launch
                    }
                }
            }

            val updatedFavs = kotlinx.coroutines.withContext(Dispatchers.IO) {
                playlistRepository.getFavoriteVideoPaths()
            }
            val updatedProgress = kotlinx.coroutines.withContext(Dispatchers.IO) {
                settingsRepository.getAllPlaybackProgress()
            }

            _uiState.update { state ->
                state.copy(
                    recycleBinItems = remainingItems,
                    allVideos = state.allVideos.filterNot { item ->
                        verifiedDeletedPaths.any { it.equals(item.path, ignoreCase = true) }
                    },
                    playlists = updatedPlaylists,
                    favoriteVideoPaths = updatedFavs,
                    playbackProgressMap = updatedProgress,
                    pendingRecycleBinDeleteIds = emptySet(),
                    pendingDeleteIntentSender = null,
                    isFileOperationBusy = false,
                    fileOperationStatusText = null,
                    statusBannerMessage = null
                )
            }
            refreshLibrary()
        }
    }

    /**
     * Completes pending Recycle Bin permanent deletions after the user grants Android system
     * authorization (MediaStore.createDeleteRequest / RecoverableSecurityException RESULT_OK).
     */
    fun onRecycleBinMediaStoreDeleteConfirmed() {
        val pendingIds = _uiState.value.pendingRecycleBinDeleteIds
        if (pendingIds.isEmpty()) return
        com.cyosplay.player.util.MediaStoreFileHelper.completePendingRecoverableDeletes(appContext)
        val itemsBefore = recycleBinRepository.getRecycleBinItems().filter { it.recycleId in pendingIds }
        val deletedPaths = mutableSetOf<String>()
        for (item in itemsBefore) {
            if (recycleBinRepository.shredRecycleItemPermanently(item.recycleId)) {
                deletedPaths.add(item.originalPath)
            }
        }
        if (deletedPaths.isNotEmpty()) {
            videoRepository.removeVideosFromCatalog(deletedPaths, shredFilesOnDisk = true)
            settingsRepository.clearCachedReferencesForVideoPaths(deletedPaths)
            playlistRepository.removeDeletedVideoPaths(deletedPaths)
        }
        _uiState.update { state ->
            state.copy(
                recycleBinItems = recycleBinRepository.getRecycleBinItems(),
                allVideos = state.allVideos.filterNot { item ->
                    deletedPaths.any { it.equals(item.path, ignoreCase = true) }
                },
                playlists = playlistRepository.getPlaylists(),
                favoriteVideoPaths = playlistRepository.getFavoriteVideoPaths(),
                playbackProgressMap = settingsRepository.getAllPlaybackProgress(),
                pendingRecycleBinDeleteIds = emptySet(),
                pendingDeleteIntentSender = null,
                statusBannerMessage = null
            )
        }
        refreshLibrary()
    }

    // --- Private Vault & Zero-Knowledge Anti-Crack Security ---

    fun setupInitialVaultPin(
        pin: String,
        securityQuestion: String,
        securityAnswer: String,
        secondaryQuestion: String = "",
        secondaryAnswer: String = ""
    ) {
        val trimmedPin = pin.trim()
        if (!CryptoSecurityManager.isValidVaultPinFormat(trimmedPin)) {
            _uiState.update { it.copy(vaultErrorMessage = "PIN must be 6 to 8 digits") }
            return
        }
        settingsRepository.setVaultPin(trimmedPin)
        if (securityQuestion.isNotBlank() && securityAnswer.isNotBlank()) {
            settingsRepository.saveSecurityQuestion(securityQuestion, securityAnswer)
        }
        if (secondaryQuestion.isNotBlank() && secondaryAnswer.isNotBlank()) {
            settingsRepository.saveSecondarySecurityQuestion(secondaryQuestion, secondaryAnswer)
        }
        CryptoSecurityManager.resetCrackAttempts(appContext)
        _uiState.update {
            it.copy(
                vaultAuthState = VaultAuthState.UNLOCKED_REAL,
                vaultEntries = vaultManager.getVaultEntries(isDecoySession = false),
                vaultErrorMessage = null,
                hasSecurityQuestion = settingsRepository.hasSecurityQuestion(),
                securityQuestionText = settingsRepository.getSecurityQuestion(),
                hasSecondarySecurityQuestion = settingsRepository.hasSecondarySecurityQuestion(),
                secondarySecurityQuestionText = settingsRepository.getSecondarySecurityQuestion(),
                failedCrackAttempts = 0,
                vaultAutoLockRemainingSeconds = VAULT_AUTO_LOCK_SECONDS
            )
        }
        startOrResetVaultAutoLockTimer()
    }

    /**
     * Verifies the Vault PIN (and performs any 3rd-attempt atomic key rotation) strictly on Dispatchers.IO
     * so PBKDF2 hashing and multi-GB Vault re-encryption never block the Main UI thread.
     */
    fun unlockVaultWithPin(pin: String): kotlinx.coroutines.Job {
        val candidate = pin.trim()
        return viewModelScope.launch {
            unlockVaultWithPinSuspend(candidate)
        }
    }

    suspend fun unlockVaultWithPinSuspend(pin: String) {
        val trimmed = pin.trim()
        val remainingLockoutMs = kotlinx.coroutines.withContext(Dispatchers.IO) {
            CryptoSecurityManager.getLockoutRemainingMs(appContext)
        }
        if (remainingLockoutMs > 0L) {
            val sec = ((remainingLockoutMs + 999L) / 1000L).coerceAtLeast(1L)
            _uiState.update {
                it.copy(
                    vaultErrorMessage = "Vault locked due to failed attempts. Exponential backoff active (${sec}s remaining)."
                )
            }
            return
        }

        val realMatched = kotlinx.coroutines.withContext(Dispatchers.IO) {
            settingsRepository.verifyVaultPin(trimmed)
        }
        if (realMatched) {
            val entries = kotlinx.coroutines.withContext(Dispatchers.IO) {
                CryptoSecurityManager.resetCrackAttempts(appContext)
                vaultManager.getVaultEntries(isDecoySession = false)
            }
            _uiState.update {
                it.copy(
                    vaultAuthState = VaultAuthState.UNLOCKED_REAL,
                    vaultEntries = entries,
                    vaultErrorMessage = null,
                    failedCrackAttempts = 0,
                    vaultAutoLockRemainingSeconds = VAULT_AUTO_LOCK_SECONDS
                )
            }
            startOrResetVaultAutoLockTimer()
            return
        }

        val decoyMatched = kotlinx.coroutines.withContext(Dispatchers.IO) {
            settingsRepository.verifyDecoyVaultPin(trimmed)
        }
        if (decoyMatched) {
            val entries = kotlinx.coroutines.withContext(Dispatchers.IO) {
                CryptoSecurityManager.resetCrackAttempts(appContext)
                vaultManager.getVaultEntries(isDecoySession = true)
            }
            _uiState.update {
                it.copy(
                    vaultAuthState = VaultAuthState.UNLOCKED_DECOY,
                    vaultEntries = entries,
                    vaultErrorMessage = null,
                    failedCrackAttempts = 0,
                    vaultAutoLockRemainingSeconds = VAULT_AUTO_LOCK_SECONDS
                )
            }
            startOrResetVaultAutoLockTimer()
            return
        }

        // Crack attempt detected! Automatically rotate encryption password & apply exponential lockout backoff on IO
        val (rotated, attempts, rotations, backoffSec) = kotlinx.coroutines.withContext(Dispatchers.IO) {
            val didRotate = CryptoSecurityManager.recordCrackAttemptAndAutoRotate(appContext, threshold = 3)
            val att = CryptoSecurityManager.getFailedCrackAttempts(appContext)
            val rot = CryptoSecurityManager.getAutoRotationCount(appContext)
            val backoff = ((CryptoSecurityManager.getLockoutRemainingMs(appContext) + 999L) / 1000L)
            arrayOf(didRotate, att, rot, backoff)
        }
        val didRotate = rotated as Boolean
        val attemptCount = attempts as Int
        val rotationCount = rotations as Int
        val backoffSeconds = backoffSec as Long
        val backoffSuffix = if (backoffSeconds > 0L) " • Locked for ${backoffSeconds}s" else ""
        val errorMsg = if (didRotate) {
            "Crack attempt blocked! Dynamic encryption password auto-rotated & all vault/app data re-encrypted (Rotation #$rotationCount)$backoffSuffix."
        } else {
            "Invalid PIN ($attemptCount/3 before automatic key rotation & re-encryption)$backoffSuffix."
        }
        _uiState.update {
            it.copy(
                vaultErrorMessage = errorMsg,
                failedCrackAttempts = attemptCount,
                autoKeyRotationCount = rotationCount
            )
        }
    }

    fun onBiometricUnlockSuccess() {
        CryptoSecurityManager.resetCrackAttempts(appContext)
        _uiState.update {
            it.copy(
                vaultAuthState = VaultAuthState.UNLOCKED_REAL,
                vaultEntries = vaultManager.getVaultEntries(isDecoySession = false),
                vaultErrorMessage = null,
                failedCrackAttempts = 0,
                vaultAutoLockRemainingSeconds = VAULT_AUTO_LOCK_SECONDS
            )
        }
        startOrResetVaultAutoLockTimer()
    }

    fun notifyVaultUserInteraction() {
        val state = _uiState.value.vaultAuthState
        if (state == VaultAuthState.UNLOCKED_REAL || state == VaultAuthState.UNLOCKED_DECOY) {
            _uiState.update { it.copy(vaultAutoLockRemainingSeconds = VAULT_AUTO_LOCK_SECONDS) }
            startOrResetVaultAutoLockTimer()
        }
    }

    private fun startOrResetVaultAutoLockTimer() {
        vaultAutoLockJob?.cancel()
        vaultAutoLockJob = viewModelScope.launch {
            var remaining = VAULT_AUTO_LOCK_SECONDS
            while (remaining > 0) {
                kotlinx.coroutines.delay(1_000L)
                val snapshot = _uiState.value
                val currentAuth = snapshot.vaultAuthState
                if (currentAuth != VaultAuthState.UNLOCKED_REAL && currentAuth != VaultAuthState.UNLOCKED_DECOY) {
                    return@launch
                }
                // Pause the inactivity auto-lock countdown while a Private Vault video is actively playing in the foreground
                val activeScreen = snapshot.currentScreen
                val isPlayingVaultInForeground =
                    (activeScreen is Screen.Player && isVaultVideoItem(activeScreen.video)) ||
                    (snapshot.miniPlayerVideo != null && isVaultVideoItem(snapshot.miniPlayerVideo) && snapshot.isMiniPlayerPlaying)
                if (isPlayingVaultInForeground) {
                    remaining = VAULT_AUTO_LOCK_SECONDS
                    if (snapshot.vaultAutoLockRemainingSeconds != VAULT_AUTO_LOCK_SECONDS) {
                        _uiState.update { it.copy(vaultAutoLockRemainingSeconds = VAULT_AUTO_LOCK_SECONDS) }
                    }
                    continue
                }
                remaining--
                _uiState.update { it.copy(vaultAutoLockRemainingSeconds = remaining) }
            }
            val finalSnapshot = _uiState.value
            val finalAuth = finalSnapshot.vaultAuthState
            val stillPlayingVault =
                (finalSnapshot.currentScreen is Screen.Player && isVaultVideoItem(finalSnapshot.currentScreen.video)) ||
                (finalSnapshot.miniPlayerVideo != null && isVaultVideoItem(finalSnapshot.miniPlayerVideo) && finalSnapshot.isMiniPlayerPlaying)
            if (!stillPlayingVault && (finalAuth == VaultAuthState.UNLOCKED_REAL || finalAuth == VaultAuthState.UNLOCKED_DECOY)) {
                lockVault()
            }
        }
    }

    fun lockVault() {
        vaultAutoLockJob?.cancel()
        vaultAutoLockJob = null

        val snapshot = _uiState.value
        val activePlayerIsVault = snapshot.currentScreen is Screen.Player && isVaultVideoItem(snapshot.currentScreen.video)
        val activeMiniIsVault = snapshot.miniPlayerVideo != null && isVaultVideoItem(snapshot.miniPlayerVideo)

        // Stop any active Vault playback and close Screen.Player / mini-player BEFORE shredding ephemeral cache
        if (activePlayerIsVault || activeMiniIsVault) {
            runCatching {
                activeExoPlayerRef?.get()?.let { player ->
                    player.stop()
                    player.clearMediaItems()
                }
            }
            runCatching {
                com.cyos.player.CyosPlayerHolder.currentPlayerOrNull()?.let { holderPlayer ->
                    holderPlayer.stop()
                    holderPlayer.clearMediaItems()
                }
            }
            com.cyos.player.CyosPlayerHolder.setBackgroundAudioActive(false)
            com.cyos.player.CyosPlaybackService.stopBackgroundService(appContext)
            navigationBackStack.removeAll { it is Screen.Player && isVaultVideoItem(it.video) }
        }

        vaultManager.wipeEphemeralPlaybackCache()
        val hasPin = settingsRepository.hasVaultPin()
        _uiState.update { state ->
            val nextScreen = if (state.currentScreen is Screen.Player && isVaultVideoItem(state.currentScreen.video)) {
                Screen.Home
            } else {
                state.currentScreen
            }
            val nextMiniVideo = if (state.miniPlayerVideo != null && isVaultVideoItem(state.miniPlayerVideo)) {
                null
            } else {
                state.miniPlayerVideo
            }
            state.copy(
                currentScreen = nextScreen,
                miniPlayerVideo = nextMiniVideo,
                isMiniPlayerPlaying = if (nextMiniVideo == null) false else state.isMiniPlayerPlaying,
                isPlayerCurrentlyPlaying = if (activePlayerIsVault) false else state.isPlayerCurrentlyPlaying,
                vaultAuthState = if (hasPin) VaultAuthState.LOCKED else VaultAuthState.UNINITIALIZED_NO_PIN,
                vaultEntries = emptyList(),
                vaultErrorMessage = null,
                vaultAutoLockRemainingSeconds = VAULT_AUTO_LOCK_SECONDS
            )
        }
    }

    fun recoverVaultPinWithSecurityAnswer(answer: String, newPin: String): Boolean {
        val remainingLockoutMs = CryptoSecurityManager.getLockoutRemainingMs(appContext)
        if (remainingLockoutMs > 0L) {
            val sec = ((remainingLockoutMs + 999L) / 1000L).coerceAtLeast(1L)
            _uiState.update {
                it.copy(
                    vaultErrorMessage = "Recovery locked due to failed attempts. Try again in ${sec}s."
                )
            }
            return false
        }

        val trimmedNewPin = newPin.trim()
        if (!CryptoSecurityManager.isValidVaultPinFormat(trimmedNewPin)) {
            _uiState.update {
                it.copy(vaultErrorMessage = "New Vault PIN must be 6 to 8 digits.")
            }
            return false
        }

        if (!settingsRepository.verifySecurityAnswer(answer)) {
            val rotated = CryptoSecurityManager.recordCrackAttemptAndAutoRotate(appContext, threshold = 3)
            val backoffSec = ((CryptoSecurityManager.getLockoutRemainingMs(appContext) + 999L) / 1000L)
            val backoffSuffix = if (backoffSec > 0L) " (Locked for ${backoffSec}s)" else ""
            _uiState.update {
                it.copy(
                    vaultErrorMessage = if (rotated) {
                        "Invalid recovery answer! Anti-crack defense rotated dynamic encryption key$backoffSuffix."
                    } else {
                        "Incorrect recovery answer$backoffSuffix."
                    },
                    autoKeyRotationCount = CryptoSecurityManager.getAutoRotationCount(appContext),
                    failedCrackAttempts = CryptoSecurityManager.getFailedCrackAttempts(appContext)
                )
            }
            return false
        }
        settingsRepository.setVaultPin(trimmedNewPin)
        CryptoSecurityManager.resetCrackAttempts(appContext)
        _uiState.update {
            it.copy(
                vaultAuthState = VaultAuthState.UNLOCKED_REAL,
                vaultEntries = vaultManager.getVaultEntries(false),
                vaultErrorMessage = null,
                failedCrackAttempts = 0
            )
        }
        return true
    }

    fun setDecoyPin(decoyPin: String) {
        val trimmed = decoyPin.trim()
        if (trimmed.isNotEmpty() && !CryptoSecurityManager.isValidVaultPinFormat(trimmed)) {
            _uiState.update {
                it.copy(statusBannerMessage = "Decoy PIN must be 6 to 8 digits")
            }
            return
        }
        settingsRepository.setDecoyVaultPin(trimmed)
        _uiState.update {
            it.copy(
                hasDecoyPin = settingsRepository.hasDecoyVaultPin(),
                statusBannerMessage = if (trimmed.isBlank()) "Decoy PIN cleared" else "Decoy Vault PIN encrypted & armed"
            )
        }
    }

    fun saveSecurityQuestion(
        question: String,
        answer: String,
        secondaryQuestion: String = "",
        secondaryAnswer: String = ""
    ) {
        settingsRepository.saveSecurityQuestion(question, answer)
        if (secondaryQuestion.isNotBlank() && secondaryAnswer.isNotBlank()) {
            settingsRepository.saveSecondarySecurityQuestion(secondaryQuestion, secondaryAnswer)
        }
        _uiState.update {
            it.copy(
                hasSecurityQuestion = true,
                securityQuestionText = question.trim(),
                hasSecondarySecurityQuestion = settingsRepository.hasSecondarySecurityQuestion(),
                secondarySecurityQuestionText = settingsRepository.getSecondarySecurityQuestion(),
                statusBannerMessage = "Encrypted recovery questions updated"
            )
        }
    }

    fun saveSecondarySecurityQuestion(question: String, answer: String) {
        if (question.isNotBlank() && answer.isNotBlank()) {
            settingsRepository.saveSecondarySecurityQuestion(question, answer)
            _uiState.update {
                it.copy(
                    hasSecondarySecurityQuestion = settingsRepository.hasSecondarySecurityQuestion(),
                    secondarySecurityQuestionText = settingsRepository.getSecondarySecurityQuestion(),
                    statusBannerMessage = "Secondary recovery question updated"
                )
            }
        }
    }

    fun triggerImmediateEncryptedKeyRotation() {
        _uiState.update {
            it.copy(
                isFileOperationBusy = true,
                fileOperationStatusText = "Rotating AES-256 encryption keys…"
            )
        }
        viewModelScope.launch {
            val newCount = kotlinx.coroutines.withContext(Dispatchers.IO) {
                CryptoSecurityManager.rotateDynamicEncryptionKey(appContext, "user_or_security_trigger")
            }
            _uiState.update {
                it.copy(
                    autoKeyRotationCount = newCount,
                    isFileOperationBusy = false,
                    fileOperationStatusText = null,
                    statusBannerMessage = "Dynamic encryption key rotated (#$newCount) • All data & credentials re-encrypted"
                )
            }
        }
    }

    fun playVaultEntry(entry: VaultVideoEntry) {
        _uiState.update {
            it.copy(
                isFileOperationBusy = true,
                fileOperationStatusText = "Opening encrypted video…"
            )
        }
        viewModelScope.launch {
            val playable = kotlinx.coroutines.withContext(Dispatchers.IO) {
                vaultManager.prepareEphemeralPlaybackItem(entry)
            }
            if (playable != null) {
                _uiState.update {
                    it.copy(
                        isFileOperationBusy = false,
                        fileOperationStatusText = null
                    )
                }
                navigateTo(Screen.Player(video = playable, playlistQueue = listOf(playable)))
            } else {
                val errMsg = "Decryption failed: Encrypted vault file kept intact; playback aborted for security"
                _uiState.update {
                    it.copy(
                        isFileOperationBusy = false,
                        fileOperationStatusText = null,
                        vaultErrorMessage = errMsg,
                        statusBannerMessage = errMsg
                    )
                }
            }
        }
    }

    fun restoreVaultEntry(vaultId: String) {
        val isDecoy = _uiState.value.vaultAuthState == VaultAuthState.UNLOCKED_DECOY
        refreshLibraryJob?.cancel()
        _uiState.update {
            it.copy(
                isFileOperationBusy = true,
                fileOperationStatusText = "Restoring encrypted video from Vault…"
            )
        }
        viewModelScope.launch {
            val (restoredFile, updatedEntries) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val targetEntry = vaultManager.getVaultEntries(false).find { it.vaultId == vaultId }
                    ?: vaultManager.getVaultEntries(true).find { it.vaultId == vaultId }
                val restored = vaultManager.restoreVideoFromVault(vaultId)
                if (restored != null && targetEntry != null) {
                    val ext = restored.extension.ifBlank { "mp4" }
                    val currentCatalog = videoRepository.getOrCreateManagedCatalog().toMutableList()
                    if (currentCatalog.none { it.path == restored.absolutePath }) {
                        currentCatalog.add(
                            0,
                            VideoItem(
                                id = restored.absolutePath.hashCode().toLong(),
                                title = targetEntry.originalTitle,
                                displayName = "${targetEntry.originalTitle}.$ext",
                                uri = Uri.fromFile(restored),
                                path = restored.absolutePath,
                                sizeBytes = restored.length().coerceAtLeast(targetEntry.sizeBytes),
                                durationMs = targetEntry.durationMs,
                                dateModifiedSeconds = System.currentTimeMillis() / 1000L,
                                width = targetEntry.width,
                                height = targetEntry.height,
                                mimeType = VideoRepository.resolveVideoMimeType(ext),
                                folderId = (restored.parent ?: "/storage/emulated/0/Movies").lowercase().hashCode().toString(),
                                folderName = restored.parentFile?.name ?: "Movies",
                                folderPath = restored.parent ?: "/storage/emulated/0/Movies"
                            )
                        )
                        videoRepository.saveManagedCatalog(currentCatalog)
                    }
                }
                restored to vaultManager.getVaultEntries(isDecoy)
            }
            if (restoredFile != null) {
                _uiState.update {
                    it.copy(
                        vaultEntries = updatedEntries,
                        vaultErrorMessage = null,
                        isFileOperationBusy = false,
                        fileOperationStatusText = null,
                        statusBannerMessage = "Restored video from Vault"
                    )
                }
                refreshLibrary()
            } else {
                val failMsg = "Decryption failed: Encrypted vault file kept intact to prevent data loss"
                _uiState.update {
                    it.copy(
                        vaultEntries = updatedEntries,
                        vaultErrorMessage = failMsg,
                        isFileOperationBusy = false,
                        fileOperationStatusText = null,
                        statusBannerMessage = failMsg
                    )
                }
            }
        }
    }

    fun shredVaultEntry(vaultId: String) {
        val isDecoy = _uiState.value.vaultAuthState == VaultAuthState.UNLOCKED_DECOY
        _uiState.update {
            it.copy(
                isFileOperationBusy = true,
                fileOperationStatusText = "Shredding encrypted Vault video…"
            )
        }
        viewModelScope.launch {
            val updatedEntries = kotlinx.coroutines.withContext(Dispatchers.IO) {
                vaultManager.deleteVaultEntryPermanently(vaultId)
                vaultManager.getVaultEntries(isDecoy)
            }
            _uiState.update {
                it.copy(
                    vaultEntries = updatedEntries,
                    isFileOperationBusy = false,
                    fileOperationStatusText = null,
                    statusBannerMessage = "Vault video permanently shredded with zero trace"
                )
            }
        }
    }

    suspend fun captureVideoScreenshotAsync(
        video: VideoItem,
        positionMs: Long,
        playerView: androidx.media3.ui.PlayerView? = null
    ): ScreenshotCaptureResult {
        val result = VideoScreenshotCapture.captureFromSurfaceOrRetriever(
            context = appContext,
            playerView = playerView,
            video = video,
            positionMs = positionMs
        )
        _uiState.update {
            it.copy(
                statusBannerMessage = if (result.success) {
                    "Screenshot saved to ${result.displayPath}"
                } else {
                    result.message.ifBlank { "Unable to capture screenshot" }
                }
            )
        }
        return result
    }

    fun captureVideoScreenshot(video: VideoItem, positionMs: Long): ScreenshotCaptureResult {
        val result = VideoScreenshotCapture.captureCurrentFrameToMediaStore(
            context = appContext,
            video = video,
            positionMs = positionMs
        )
        _uiState.update {
            it.copy(
                statusBannerMessage = if (result.success) {
                    "Screenshot saved to ${result.displayPath}"
                } else {
                    result.message.ifBlank { "Unable to capture screenshot" }
                }
            )
        }
        return result
    }

    fun showStatusMessage(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    override fun onCleared() {
        stopObservingMediaStoreVideos()
        deferredPostFrameJob?.cancel()
        vaultAutoLockJob?.cancel()
        sleepTimerJob?.cancel()
        progressSaveJob?.cancel()
        com.cyos.player.CyosPlayerHolder.setBackgroundAudioActive(false)
        settingsRepository.release()
        videoRepository.release()
        vaultManager.release()
        recycleBinRepository.release()
        playlistRepository.release()
        vaultManager.wipeEphemeralPlaybackCache()
        super.onCleared()
    }

    // --- Low-RAM Shield, Battery Saver Mode & Multi-Codec Decoder Controls ---

    fun setLowRamShieldEnabled(enabled: Boolean) {
        settingsRepository.setLowRamShieldEnabled(enabled)
        val budget = MemoryManager.detectMemoryBudget(
            context = appContext,
            forceLowRamMode = enabled,
            batterySaverEnabled = _uiState.value.batterySaverModeEnabled
        )
        if (enabled) {
            MemoryManager.performEmergencyMemoryTrim(appContext)
        }
        _uiState.update {
            it.copy(
                lowRamShieldEnabled = enabled,
                memoryBudget = budget,
                statusBannerMessage = if (enabled) {
                    "Low-RAM Shield enabled (${budget.tier.badgeLabel})"
                } else {
                    "Standard memory budget active"
                }
            )
        }
    }

    fun setBatterySaverModeEnabled(enabled: Boolean) {
        settingsRepository.setBatterySaverModeEnabled(enabled)
        val budget = MemoryManager.detectMemoryBudget(
            context = appContext,
            forceLowRamMode = _uiState.value.lowRamShieldEnabled,
            batterySaverEnabled = enabled
        )
        val enhancement = if (enabled) {
            SmartEnhancementMode.ECO_BATTERY
        } else {
            SmartEnhancementMode.OFF
        }
        settingsRepository.setSmartEnhancementMode(enhancement)
        if (enabled) {
            MemoryManager.performEmergencyMemoryTrim(appContext)
        }
        _uiState.update {
            it.copy(
                batterySaverModeEnabled = enabled,
                memoryBudget = budget,
                smartEnhancementMode = enhancement,
                statusBannerMessage = if (enabled) {
                    "Battery Smoothness Shield active • Optimized frame pacing, CPU & battery"
                } else {
                    "Battery Smoothness Shield off • Standard playback restored"
                }
            )
        }
    }

    fun setDecoderEngineMode(mode: DecoderEngineMode) {
        settingsRepository.setDecoderEngineMode(mode)
        val badge = when (mode) {
            DecoderEngineMode.HW -> "HW DIRECT"
            DecoderEngineMode.HW_PLUS, DecoderEngineMode.AUTO_HW_PLUS -> "HW+ MULTI-CODEC"
            DecoderEngineMode.SW -> "SW SOFTWARE"
            DecoderEngineMode.SW_PLUS, DecoderEngineMode.SOFTWARE_MULTI_CORE -> "SW+ MULTI-CORE"
        }
        _uiState.update {
            it.copy(
                decoderEngineMode = mode,
                activeDecoderBadge = badge,
                statusBannerMessage = "Switched video/audio decoder to ${mode.label}"
            )
        }
    }

    fun toggleDecoderEngineMode() {
        val next = when (_uiState.value.decoderEngineMode) {
            DecoderEngineMode.HW -> DecoderEngineMode.HW_PLUS
            DecoderEngineMode.HW_PLUS, DecoderEngineMode.AUTO_HW_PLUS -> DecoderEngineMode.SW_PLUS
            DecoderEngineMode.SW_PLUS, DecoderEngineMode.SOFTWARE_MULTI_CORE -> DecoderEngineMode.SW
            DecoderEngineMode.SW -> DecoderEngineMode.HW
        }
        setDecoderEngineMode(next)
    }

    /**
     * Advances the decoder engine along the 4-stage fallback chain (HW -> HW+ -> SW+ -> SW)
     * strictly for the current playback session WITHOUT overwriting the user's persisted
     * SettingsRepository decoder preference.
     */
    fun fallbackToNextDecoderEngine(): DecoderEngineMode? {
        val next = _uiState.value.decoderEngineMode.nextFallbackMode() ?: return null
        _uiState.update {
            it.copy(
                decoderEngineMode = next,
                activeDecoderBadge = next.badge,
                statusBannerMessage = "Session fallback to ${next.label} (saved setting preserved)"
            )
        }
        return next
    }

    fun setForceHardwareAccelerationHighBitrate(enabled: Boolean) {
        settingsRepository.setForceHardwareAccelerationHighBitrateEnabled(enabled)
        val updatedMode = if (enabled) {
            settingsRepository.setDecoderEngineMode(DecoderEngineMode.AUTO_HW_PLUS)
            DecoderEngineMode.AUTO_HW_PLUS
        } else {
            _uiState.value.decoderEngineMode
        }
        _uiState.update {
            it.copy(
                forceHardwareAccelerationHighBitrate = enabled,
                decoderEngineMode = updatedMode,
                activeDecoderBadge = if (enabled) {
                    "HW+ FORCED (HIGH-BITRATE)"
                } else if (updatedMode == DecoderEngineMode.SOFTWARE_MULTI_CORE) {
                    "SW+ MULTI-CORE"
                } else {
                    "HW+ MULTI-CODEC"
                },
                statusBannerMessage = if (enabled) {
                    "Hardware Acceleration forced for high-bitrate video (MediaCodec HW + SW fallback)"
                } else {
                    "Explicit high-bitrate hardware acceleration lock disabled"
                }
            )
        }
    }

    fun toggleForceHardwareAccelerationHighBitrate() {
        setForceHardwareAccelerationHighBitrate(!_uiState.value.forceHardwareAccelerationHighBitrate)
    }

    fun setStealthPlaybackEnabled(enabled: Boolean) {
        settingsRepository.setStealthPlaybackEnabled(enabled)
        if (enabled) {
            settingsRepository.clearAllPlaybackHistoryAndTraces()
        }
        _uiState.update {
            it.copy(
                stealthPlaybackEnabled = enabled,
                playbackProgressMap = settingsRepository.getAllPlaybackProgress(),
                statusBannerMessage = if (enabled) {
                    "Stealth Playback ON • Zero playback trace or history recorded"
                } else {
                    "Stealth Playback OFF • Resume positions enabled"
                }
            )
        }
    }

    fun wipeAllPlaybackTracesNow() {
        settingsRepository.clearAllPlaybackHistoryAndTraces()
        vaultManager.wipeEphemeralPlaybackCache()
        _uiState.update {
            it.copy(
                playbackProgressMap = emptyMap(),
                activeVideoBookmarks = emptyList(),
                statusBannerMessage = "All playback history, progress & temp traces wiped"
            )
        }
    }

    // --- Search, Sort, View & Explorer Controls ---

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun saveFoldersScrollPosition(isGrid: Boolean, firstVisibleIndex: Int, scrollOffset: Int) {
        val safeIdx = firstVisibleIndex.coerceAtLeast(0)
        val safeOff = scrollOffset.coerceAtLeast(0)
        settingsRepository.saveFoldersScrollState(safeIdx, safeOff)
        _uiState.update { state ->
            if (isGrid) {
                state.copy(
                    foldersGridFirstVisibleIndex = safeIdx,
                    foldersGridScrollOffset = safeOff
                )
            } else {
                state.copy(
                    foldersListFirstVisibleIndex = safeIdx,
                    foldersListScrollOffset = safeOff
                )
            }
        }
    }

    fun saveFoldersScrollPosition(
        listIndex: Int,
        listOffset: Int,
        gridIndex: Int = _uiState.value.foldersGridFirstVisibleIndex,
        gridOffset: Int = _uiState.value.foldersGridScrollOffset
    ) {
        val safeIdx = listIndex.coerceAtLeast(0)
        val safeOff = listOffset.coerceAtLeast(0)
        settingsRepository.saveFoldersScrollState(safeIdx, safeOff)
        _uiState.update { state ->
            state.copy(
                foldersListFirstVisibleIndex = safeIdx,
                foldersListScrollOffset = safeOff,
                foldersGridFirstVisibleIndex = gridIndex.coerceAtLeast(0),
                foldersGridScrollOffset = gridOffset.coerceAtLeast(0)
            )
        }
    }

    fun saveVideosScrollPosition(isGrid: Boolean, firstVisibleIndex: Int, scrollOffset: Int) {
        val safeIdx = firstVisibleIndex.coerceAtLeast(0)
        val safeOff = scrollOffset.coerceAtLeast(0)
        settingsRepository.saveVideosScrollState(safeIdx, safeOff)
        _uiState.update { state ->
            if (isGrid) {
                state.copy(
                    videosGridFirstVisibleIndex = safeIdx,
                    videosGridScrollOffset = safeOff
                )
            } else {
                state.copy(
                    videosListFirstVisibleIndex = safeIdx,
                    videosListScrollOffset = safeOff
                )
            }
        }
    }

    fun saveVideosScrollPosition(
        listIndex: Int,
        listOffset: Int,
        gridIndex: Int = _uiState.value.videosGridFirstVisibleIndex,
        gridOffset: Int = _uiState.value.videosGridScrollOffset
    ) {
        val safeIdx = listIndex.coerceAtLeast(0)
        val safeOff = listOffset.coerceAtLeast(0)
        settingsRepository.saveVideosScrollState(safeIdx, safeOff)
        _uiState.update { state ->
            state.copy(
                videosListFirstVisibleIndex = safeIdx,
                videosListScrollOffset = safeOff,
                videosGridFirstVisibleIndex = gridIndex.coerceAtLeast(0),
                videosGridScrollOffset = gridOffset.coerceAtLeast(0)
            )
        }
    }

    fun getFolderDetailScrollPosition(folderId: String): Pair<Int, Int> {
        _uiState.value.folderDetailScrollMap[folderId]?.let { return it }
        val cfg = _uiState.value.folderBrowserConfigMap[folderId]
        if (cfg != null && (cfg.lastScrollIndex > 0 || cfg.lastScrollOffset > 0)) {
            return cfg.lastScrollIndex to cfg.lastScrollOffset
        }
        val persisted = settingsRepository.getFolderDetailScrollState(folderId)
        _uiState.update { state ->
            state.copy(folderDetailScrollMap = state.folderDetailScrollMap + (folderId to persisted))
        }
        return persisted
    }

    fun saveFolderDetailScrollPosition(folderId: String, firstVisibleIndex: Int, scrollOffset: Int) {
        if (folderId.isBlank()) return
        val safePair = firstVisibleIndex.coerceAtLeast(0) to scrollOffset.coerceAtLeast(0)
        settingsRepository.saveFolderDetailScrollState(folderId, safePair.first, safePair.second)
        val updatedConfig = getFolderBrowserConfig(folderId).copy(
            lastScrollIndex = safePair.first,
            lastScrollOffset = safePair.second
        )
        settingsRepository.saveFolderBrowserConfig(folderId, updatedConfig)
        _uiState.update { state ->
            val updatedMap = state.folderBrowserConfigMap.toMutableMap().apply {
                put(folderId, updatedConfig)
            }
            state.copy(
                folderDetailScrollMap = state.folderDetailScrollMap + (folderId to safePair),
                folderBrowserConfigMap = updatedMap
            )
        }
    }

    fun toggleGridView() {
        val state = _uiState.value
        val activeIsFolders = state.currentNavTab == NavDestination.FOLDERS && state.currentScreen is Screen.Home
        val currentMode = if (activeIsFolders) state.folderViewMode else state.videoViewMode
        val nextGrid = currentMode != BrowserViewMode.GRID
        val nextMode = if (nextGrid) BrowserViewMode.GRID else BrowserViewMode.LIST
        if (activeIsFolders) {
            settingsRepository.setFolderBrowserViewMode(nextMode)
            _uiState.update {
                it.copy(
                    isGridView = nextGrid,
                    folderViewMode = nextMode
                )
            }
        } else {
            settingsRepository.setVideoBrowserViewMode(nextMode)
            _uiState.update {
                it.copy(
                    isGridView = nextGrid,
                    videoViewMode = nextMode
                )
            }
        }
    }

    fun openBrowserControlTab(tab: BrowserControlTab) {
        _uiState.update { it.copy(activeBrowserControlSheetTab = tab) }
    }

    fun dismissBrowserControlSheet() {
        _uiState.update { it.copy(activeBrowserControlSheetTab = null) }
    }

    fun openFfmpegCodecInspector() {
        _uiState.update {
            it.copy(
                activeBrowserControlSheetTab = null,
                showFfmpegCodecInspectorDialog = true
            )
        }
    }

    fun dismissFfmpegCodecInspector() {
        _uiState.update { it.copy(showFfmpegCodecInspectorDialog = false) }
    }

    fun setFolderViewMode(mode: BrowserViewMode) {
        settingsRepository.setFolderBrowserViewMode(mode)
        _uiState.update {
            it.copy(
                folderViewMode = mode,
                isGridView = mode == BrowserViewMode.GRID
            )
        }
    }

    fun cycleFolderViewMode() {
        val modes = BrowserViewMode.entries
        val next = modes[(_uiState.value.folderViewMode.ordinal + 1) % modes.size]
        setFolderViewMode(next)
    }

    fun getFolderBrowserConfig(folderId: String): FolderBrowserConfig {
        val state = _uiState.value
        return state.folderBrowserConfigMap[folderId] ?: FolderBrowserConfig(
            viewMode = state.videoViewMode,
            sortOption = state.videoSortOption,
            sortDirection = state.videoSortDirection,
            layoutColumns = state.browserLayoutColumns,
            thumbnailRatio = state.browserThumbnailRatio
        )
    }

    fun setFolderDetailViewMode(folderId: String, mode: BrowserViewMode) {
        val currentConfig = getFolderBrowserConfig(folderId)
        val updatedConfig = currentConfig.copy(viewMode = mode)
        settingsRepository.saveFolderBrowserConfig(folderId, updatedConfig)
        _uiState.update { state ->
            val updatedMap = state.folderBrowserConfigMap.toMutableMap().apply {
                put(folderId, updatedConfig)
            }
            state.copy(folderBrowserConfigMap = updatedMap)
        }
    }

    fun cycleFolderDetailViewMode(folderId: String) {
        val currentConfig = getFolderBrowserConfig(folderId)
        val modes = BrowserViewMode.entries
        val next = modes[(currentConfig.viewMode.ordinal + 1) % modes.size]
        setFolderDetailViewMode(folderId, next)
    }

    fun setFolderDetailSortOption(folderId: String, option: VideoSortOption) {
        val currentConfig = getFolderBrowserConfig(folderId)
        val dir = when (option) {
            VideoSortOption.DATE_ASC, VideoSortOption.TITLE_ASC, VideoSortOption.NAME_ASC, VideoSortOption.SIZE_ASC, VideoSortOption.DURATION_ASC, VideoSortOption.FORMAT_ASC -> SortDirection.ASCENDING
            else -> SortDirection.DESCENDING
        }
        val updatedConfig = currentConfig.copy(sortOption = option, sortDirection = dir)
        settingsRepository.saveFolderBrowserConfig(folderId, updatedConfig)
        _uiState.update { state ->
            val updatedMap = state.folderBrowserConfigMap.toMutableMap().apply {
                put(folderId, updatedConfig)
            }
            state.copy(folderBrowserConfigMap = updatedMap)
        }
    }

    fun setFolderDetailSortDirection(folderId: String, direction: SortDirection) {
        val currentConfig = getFolderBrowserConfig(folderId)
        val updatedConfig = currentConfig.copy(sortDirection = direction)
        settingsRepository.saveFolderBrowserConfig(folderId, updatedConfig)
        _uiState.update { state ->
            val updatedMap = state.folderBrowserConfigMap.toMutableMap().apply {
                put(folderId, updatedConfig)
            }
            state.copy(folderBrowserConfigMap = updatedMap)
        }
    }

    fun setFolderDetailLayoutColumns(folderId: String, columns: BrowserLayoutColumns) {
        val currentConfig = getFolderBrowserConfig(folderId)
        val updatedConfig = currentConfig.copy(layoutColumns = columns)
        settingsRepository.saveFolderBrowserConfig(folderId, updatedConfig)
        _uiState.update { state ->
            val updatedMap = state.folderBrowserConfigMap.toMutableMap().apply {
                put(folderId, updatedConfig)
            }
            state.copy(folderBrowserConfigMap = updatedMap)
        }
    }

    fun setFolderDetailThumbnailRatio(folderId: String, ratio: BrowserThumbnailRatio) {
        val currentConfig = getFolderBrowserConfig(folderId)
        val updatedConfig = currentConfig.copy(thumbnailRatio = ratio)
        settingsRepository.saveFolderBrowserConfig(folderId, updatedConfig)
        _uiState.update { state ->
            val updatedMap = state.folderBrowserConfigMap.toMutableMap().apply {
                put(folderId, updatedConfig)
            }
            state.copy(folderBrowserConfigMap = updatedMap)
        }
    }

    fun setVideoViewMode(mode: BrowserViewMode) {
        val currentScreen = _uiState.value.currentScreen
        if (currentScreen is Screen.FolderDetail) {
            setFolderDetailViewMode(currentScreen.folderId, mode)
            return
        }
        settingsRepository.setVideoBrowserViewMode(mode)
        _uiState.update {
            it.copy(
                videoViewMode = mode,
                isGridView = mode == BrowserViewMode.GRID
            )
        }
    }

    fun cycleVideoViewMode() {
        val currentScreen = _uiState.value.currentScreen
        if (currentScreen is Screen.FolderDetail) {
            cycleFolderDetailViewMode(currentScreen.folderId)
            return
        }
        val modes = BrowserViewMode.entries
        val next = modes[(_uiState.value.videoViewMode.ordinal + 1) % modes.size]
        setVideoViewMode(next)
    }

    fun setVideoSortOption(option: VideoSortOption) {
        val currentScreen = _uiState.value.currentScreen
        if (currentScreen is Screen.FolderDetail) {
            setFolderDetailSortOption(currentScreen.folderId, option)
            return
        }
        settingsRepository.setVideoSortOption(option)
        val dir = when (option) {
            VideoSortOption.DATE_ASC, VideoSortOption.TITLE_ASC, VideoSortOption.NAME_ASC, VideoSortOption.SIZE_ASC, VideoSortOption.DURATION_ASC, VideoSortOption.FORMAT_ASC -> SortDirection.ASCENDING
            else -> SortDirection.DESCENDING
        }
        settingsRepository.setVideoSortDirection(dir)
        _uiState.update { state ->
            val sorted = sortVideos(state.allVideos, option, dir)
            val updatedFolders = sortFolders(
                videoRepository.groupVideosIntoFolders(sorted),
                state.folderSortOption,
                state.folderSortDirection
            )
            state.copy(
                videoSortOption = option,
                videoSortDirection = dir,
                allVideos = sorted,
                folders = updatedFolders
            )
        }
    }

    fun setVideoSortDirection(direction: SortDirection) {
        val currentScreen = _uiState.value.currentScreen
        if (currentScreen is Screen.FolderDetail) {
            setFolderDetailSortDirection(currentScreen.folderId, direction)
            return
        }
        settingsRepository.setVideoSortDirection(direction)
        _uiState.update { state ->
            val sorted = sortVideos(state.allVideos, state.videoSortOption, direction)
            val updatedFolders = sortFolders(
                videoRepository.groupVideosIntoFolders(sorted),
                state.folderSortOption,
                state.folderSortDirection
            )
            state.copy(
                videoSortDirection = direction,
                allVideos = sorted,
                folders = updatedFolders
            )
        }
    }

    fun setFolderSortOption(option: FolderSortOption) {
        settingsRepository.setFolderSortOption(option)
        val dir = when (option) {
            FolderSortOption.TITLE_ASC, FolderSortOption.NAME_ASC, FolderSortOption.COUNT_ASC, FolderSortOption.SIZE_ASC, FolderSortOption.RECENT_ASC -> SortDirection.ASCENDING
            else -> SortDirection.DESCENDING
        }
        settingsRepository.setFolderSortDirection(dir)
        _uiState.update { state ->
            val sorted = sortFolders(state.folders, option, dir)
            state.copy(folderSortOption = option, folderSortDirection = dir, folders = sorted)
        }
    }

    fun setFolderSortDirection(direction: SortDirection) {
        settingsRepository.setFolderSortDirection(direction)
        _uiState.update { state ->
            val sorted = sortFolders(state.folders, state.folderSortOption, direction)
            state.copy(folderSortDirection = direction, folders = sorted)
        }
    }

    fun setBrowserLayoutColumns(columns: BrowserLayoutColumns) {
        val currentScreen = _uiState.value.currentScreen
        if (currentScreen is Screen.FolderDetail) {
            setFolderDetailLayoutColumns(currentScreen.folderId, columns)
            return
        }
        settingsRepository.setBrowserLayoutColumns(columns)
        _uiState.update { it.copy(browserLayoutColumns = columns) }
    }

    fun setBrowserThumbnailRatio(ratio: BrowserThumbnailRatio) {
        val currentScreen = _uiState.value.currentScreen
        if (currentScreen is Screen.FolderDetail) {
            setFolderDetailThumbnailRatio(currentScreen.folderId, ratio)
        }
        settingsRepository.setBrowserThumbnailRatio(ratio)
        _uiState.update { it.copy(browserThumbnailRatio = ratio) }
    }

    fun setAdvancedCodecFilter(filter: AdvancedCodecFilter) {
        settingsRepository.setAdvancedCodecFilter(filter)
        _uiState.update { it.copy(advancedCodecFilter = filter) }
    }

    fun setResolutionFilterTier(tier: ResolutionFilterTier) {
        settingsRepository.setResolutionFilterTier(tier)
        _uiState.update { it.copy(resolutionFilterTier = tier) }
    }

    fun setIncludeSubfoldersRecursive(enabled: Boolean) {
        settingsRepository.setIncludeSubfoldersRecursive(enabled)
        _uiState.update { it.copy(includeSubfoldersRecursive = enabled) }
    }

    fun updateBrowserVisibleFields(fields: BrowserVisibleFields) {
        settingsRepository.setBrowserVisibleFields(fields)
        _uiState.update { it.copy(visibleFields = fields) }
    }

    fun setFolderExplorerMode(mode: FolderExplorerMode) {
        settingsRepository.setFolderExplorerMode(mode)
        _uiState.update { it.copy(folderExplorerMode = mode) }
    }

    fun setStorageVolumeFilter(filter: StorageVolumeFilter) {
        settingsRepository.setStorageVolumeFilter(filter)
        val volumes = _uiState.value.storageVolumes.ifEmpty { videoRepository.detectStorageVolumes() }
        val targetRootPath = when (filter) {
            StorageVolumeFilter.SD_CARD_ONLY -> {
                volumes.firstOrNull { it.isRemovable }?.rootPath ?: "/storage/sdcard1"
            }
            StorageVolumeFilter.INTERNAL_ONLY -> "/storage/emulated/0"
            StorageVolumeFilter.ALL_STORAGE -> {
                _uiState.value.currentDirectoryNode?.currentPath ?: "/storage/emulated/0"
            }
        }
        val updatedNode = videoRepository.buildStorageDirectoryNode(targetRootPath, _uiState.value.allVideos)
        _uiState.update {
            it.copy(
                storageVolumeFilter = filter,
                currentDirectoryNode = updatedNode
            )
        }
    }

    fun openStorageDirectoryPath(path: String) {
        val normalized = path.trim().trimEnd('/').ifEmpty { "/storage/emulated/0" }
        val node = videoRepository.buildStorageDirectoryNode(normalized, _uiState.value.allVideos)
        _uiState.update { it.copy(currentDirectoryNode = node) }
    }

    fun setMediaManagerFilter(filter: MediaManagerFilter) {
        _uiState.update { it.copy(mediaManagerFilter = filter) }
    }

    // --- Playlists & Favorites ---

    fun createPlaylist(name: String, description: String = "", initialPaths: List<String> = emptyList()) {
        if (name.isBlank()) return
        playlistRepository.createPlaylist(name, description, initialPaths)
        _uiState.update {
            it.copy(
                playlists = playlistRepository.getPlaylists(),
                statusBannerMessage = "Playlist \"${name.trim()}\" encrypted & saved"
            )
        }
    }

    fun deletePlaylist(playlistId: String) {
        playlistRepository.deletePlaylist(playlistId)
        _uiState.update { it.copy(playlists = playlistRepository.getPlaylists()) }
    }

    fun addVideosToPlaylist(playlistId: String, videoPaths: List<String>) {
        playlistRepository.addVideosToPlaylist(playlistId, videoPaths)
        _uiState.update {
            it.copy(
                playlists = playlistRepository.getPlaylists(),
                statusBannerMessage = "Added ${videoPaths.size} video(s) to playlist"
            )
        }
    }

    fun toggleFavoriteVideo(videoPath: String) {
        val updated = playlistRepository.toggleFavorite(videoPath)
        _uiState.update { it.copy(favoriteVideoPaths = updated) }
    }

    // --- Player Runtime Controls ---

    fun cycleAspectRatioMode() {
        val modes = AspectRatioMode.entries
        val next = modes[(_uiState.value.aspectRatioMode.ordinal + 1) % modes.size]
        _uiState.update { it.copy(aspectRatioMode = next) }
    }

    fun cycleRepeatMode() {
        val modes = RepeatMode.entries
        val next = modes[(_uiState.value.repeatMode.ordinal + 1) % modes.size]
        settingsRepository.setRepeatMode(next)
        _uiState.update { it.copy(repeatMode = next) }
    }

    fun cyclePlayerOrientationMode() {
        val modes = PlayerOrientationMode.entries
        val next = modes[(_uiState.value.playerOrientationMode.ordinal + 1) % modes.size]
        settingsRepository.setPlayerOrientationMode(next)
        _uiState.update { it.copy(playerOrientationMode = next) }
    }

    fun setSmartEnhancementMode(mode: SmartEnhancementMode) {
        settingsRepository.setSmartEnhancementMode(mode)
        val batterySaver = mode == SmartEnhancementMode.ECO_BATTERY
        settingsRepository.setBatterySaverModeEnabled(batterySaver)
        val budget = MemoryManager.detectMemoryBudget(
            context = appContext,
            forceLowRamMode = _uiState.value.lowRamShieldEnabled,
            batterySaverEnabled = batterySaver
        )
        _uiState.update {
            it.copy(
                smartEnhancementMode = mode,
                batterySaverModeEnabled = batterySaver,
                memoryBudget = budget,
                statusBannerMessage = "Smart Video Enhancement: ${mode.label}"
            )
        }
    }

    fun cycleSmartEnhancementMode() {
        val modes = SmartEnhancementMode.entries
        val next = modes[(_uiState.value.smartEnhancementMode.ordinal + 1) % modes.size]
        setSmartEnhancementMode(next)
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed.coerceIn(0.25f, 5.0f)) }
    }

    fun setVideoZoomScale(scale: Float) {
        val clamped = scale.coerceIn(0.5f, 4.0f)
        _uiState.update { it.copy(videoZoomScale = clamped) }
    }

    fun resetVideoZoom() {
        _uiState.update { it.copy(videoZoomScale = 1.0f) }
    }

    fun cycleLockedPlaybackSpeed(): Float {
        val lockedSpeeds = listOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        val current = _uiState.value.playbackSpeed
        val currentIdx = lockedSpeeds.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val nextSpeed = if (currentIdx >= 0) {
            lockedSpeeds[(currentIdx + 1) % lockedSpeeds.size]
        } else {
            2.0f
        }
        setPlaybackSpeed(nextSpeed)
        return nextSpeed
    }

    fun getPlayerBrightnessForFolder(folderKey: String): Float {
        return settingsRepository.getSavedPlayerBrightnessForFolder(folderKey)
    }

    fun getPlayerVolumeForFolder(folderKey: String): Float {
        val state = _uiState.value
        val normFolder = folderKey.trim().trimEnd('/')
        if (normFolder.isNotEmpty()) {
            lookupFloatSettingCaseInsensitive(state.folderVolumeMap, normFolder)?.let { return it }
        }
        return settingsRepository.getSavedPlayerVolumeForFolder(folderKey)
    }

    fun getPlayerBrightnessForVideo(videoPath: String, folderKey: String = ""): Float {
        val state = _uiState.value
        val normVideo = videoPath.trim()
        val normFolder = folderKey.trim().trimEnd('/')
        if (normVideo.isNotEmpty()) {
            lookupFloatSettingCaseInsensitive(state.videoBrightnessMap, normVideo)?.let { return it }
        }
        if (normFolder.isNotEmpty()) {
            lookupFloatSettingCaseInsensitive(state.folderBrightnessMap, normFolder)?.let { return it }
        }
        return settingsRepository.getSavedPlayerBrightnessForVideo(videoPath, folderKey)
    }

    fun getPlayerVolumeForVideo(videoPath: String, folderKey: String = ""): Float {
        val state = _uiState.value
        val normVideo = videoPath.trim()
        val normFolder = folderKey.trim().trimEnd('/')
        if (normVideo.isNotEmpty()) {
            lookupFloatSettingCaseInsensitive(state.videoVolumeMap, normVideo)?.let { return it }
        }
        if (normFolder.isNotEmpty()) {
            lookupFloatSettingCaseInsensitive(state.folderVolumeMap, normFolder)?.let { return it }
        }
        return settingsRepository.getSavedPlayerVolumeForVideo(videoPath, folderKey)
    }

    fun updateSavedPlayerBrightness(
        value: Float,
        folderKey: String? = null,
        videoPath: String? = null
    ) {
        val clamped = com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeBrightness(
            value,
            _uiState.value.savedPlayerBrightness
        )
        val safetyCheck = com.cyosplay.player.util.PlayerGestureControlHelper.enforceAutoSafeVolumeAndBrightness(
            volumeFraction = _uiState.value.savedPlayerVolume,
            brightnessFraction = clamped,
            isMuted = _uiState.value.isAudioMuted
        )
        val effectiveBri = safetyCheck.safeBrightness
        val normVideo = videoPath?.trim().orEmpty()
        val normFolder = folderKey?.trim()?.trimEnd('/').orEmpty()

        _uiState.update { state ->
            val nextVideoMap = if (normVideo.isNotEmpty()) {
                state.videoBrightnessMap + (normVideo to effectiveBri)
            } else {
                state.videoBrightnessMap
            }
            val nextFolderMap = if (normFolder.isNotEmpty()) {
                state.folderBrightnessMap + (normFolder to effectiveBri)
            } else {
                state.folderBrightnessMap
            }
            state.copy(
                savedPlayerBrightness = effectiveBri,
                folderBrightnessMap = nextFolderMap,
                videoBrightnessMap = nextVideoMap,
                showHighVolumeBrightnessWarning = safetyCheck.triggeredWarningAndAuto50 || state.showHighVolumeBrightnessWarning
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (normVideo.isNotEmpty()) {
                settingsRepository.setSavedPlayerBrightnessForVideo(normVideo, normFolder, effectiveBri)
            } else if (normFolder.isNotEmpty()) {
                settingsRepository.setSavedPlayerBrightnessForFolder(normFolder, effectiveBri)
            } else {
                settingsRepository.setSavedPlayerBrightness(effectiveBri)
            }
        }
    }

    fun updateSavedPlayerVolume(
        value: Float,
        folderKey: String? = null,
        videoPath: String? = null
    ) {
        val clamped = com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeVolume(
            value,
            _uiState.value.savedPlayerVolume
        )
        val safetyCheck = com.cyosplay.player.util.PlayerGestureControlHelper.enforceAutoSafeVolumeAndBrightness(
            volumeFraction = clamped,
            brightnessFraction = _uiState.value.savedPlayerBrightness,
            isMuted = _uiState.value.isAudioMuted
        )
        val effectiveVol = safetyCheck.safeVolume
        val normVideo = videoPath?.trim().orEmpty()
        val normFolder = folderKey?.trim()?.trimEnd('/').orEmpty()

        _uiState.update { state ->
            val nextVideoMap = if (normVideo.isNotEmpty()) {
                state.videoVolumeMap + (normVideo to effectiveVol)
            } else {
                state.videoVolumeMap
            }
            val nextFolderMap = if (normFolder.isNotEmpty()) {
                state.folderVolumeMap + (normFolder to effectiveVol)
            } else {
                state.folderVolumeMap
            }
            state.copy(
                savedPlayerVolume = effectiveVol,
                folderVolumeMap = nextFolderMap,
                videoVolumeMap = nextVideoMap,
                showHighVolumeBrightnessWarning = safetyCheck.triggeredWarningAndAuto50 || state.showHighVolumeBrightnessWarning
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (normVideo.isNotEmpty()) {
                settingsRepository.setSavedPlayerVolumeForVideo(normVideo, normFolder, effectiveVol)
            } else if (normFolder.isNotEmpty()) {
                settingsRepository.setSavedPlayerVolumeForFolder(normFolder, effectiveVol)
            } else {
                settingsRepository.setSavedPlayerVolume(effectiveVol)
            }
        }
    }

    fun dismissHighVolumeBrightnessWarning() {
        _uiState.update { it.copy(showHighVolumeBrightnessWarning = false) }
    }

    fun lowerBothVolumeAndBrightness(
        folderKey: String? = null,
        videoPath: String? = null
    ) {
        val safeTarget = com.cyosplay.player.util.PlayerGestureControlHelper.SAFE_LOWERED_VOLUME_BRIGHTNESS
        updateSavedPlayerBrightness(safeTarget, folderKey, videoPath)
        updateSavedPlayerVolume(safeTarget, folderKey, videoPath)
        _uiState.update {
            it.copy(
                savedPlayerBrightness = safeTarget,
                savedPlayerVolume = safeTarget,
                showHighVolumeBrightnessWarning = false,
                statusBannerMessage = "Lowered volume & screen brightness to 50%"
            )
        }
    }

    fun setSubtitlesEnabledByUser(enabled: Boolean) {
        settingsRepository.setSubtitlesEnabledByUser(enabled)
        _uiState.update { state ->
            state.copy(
                subtitlesEnabledByUser = enabled,
                subtitleTracks = if (!enabled) {
                    state.subtitleTracks.map { it.copy(isSelected = false) }
                } else {
                    state.subtitleTracks
                }
            )
        }
    }

    fun setVolumeBoostMultiplier(boost: Float) {
        _uiState.update { it.copy(volumeBoostMultiplier = boost.coerceIn(1.0f, 2.0f)) }
    }

    fun setPerformanceHudEnabled(enabled: Boolean) {
        settingsRepository.setPerformanceHudEnabled(enabled)
        _uiState.update {
            it.copy(
                showPerformanceHud = enabled,
                statusBannerMessage = if (enabled) {
                    "4K/8K Telemetry HUD enabled (FPS • Decoder • Buffer)"
                } else {
                    "4K/8K Telemetry HUD hidden"
                }
            )
        }
    }

    fun togglePerformanceHud() {
        setPerformanceHudEnabled(!_uiState.value.showPerformanceHud)
    }

    /**
     * Unified Night Mode: controls both the warm amber blue-light filter (+ extra night dimmer)
     * and dynamic audio range compression (boosting quiet dialogue while taming loud peaks).
     */
    fun setNightModeEnabled(enabled: Boolean) {
        settingsRepository.setNightModeEnabled(enabled)
        settingsRepository.setEyeComfortEnabled(enabled)
        settingsRepository.setNightModeAudioEnabled(enabled)
        val targetWarmth = if (enabled) {
            0.11f
        } else {
            _uiState.value.eyeComfortWarmth
        }
        val targetDim = if (enabled) {
            0.15f
        } else {
            0f
        }
        settingsRepository.setEyeComfortWarmth(targetWarmth)
        settingsRepository.setExtraDimIntensity(targetDim)
        _uiState.update {
            it.copy(
                nightModeEnabled = enabled,
                eyeComfortEnabled = enabled,
                eyeComfortWarmth = targetWarmth,
                nightShieldWarmthLevel = targetWarmth,
                extraDimIntensity = targetDim,
                nightModeAudioEnabled = enabled,
                dialogueEnhancerEnabled = if (enabled) true else it.dialogueEnhancerEnabled,
                statusBannerMessage = if (enabled) {
                    "Night Mode ON • Warmth Intensity: 11%, Extra Night Dimmer: 15%"
                } else {
                    "Night Mode OFF • Standard visuals & audio restored"
                }
            )
        }
    }

    fun toggleNightMode() {
        setNightModeEnabled(!_uiState.value.nightModeEnabled)
    }

    fun setAutoSkipIntrosCredits(enabled: Boolean) {
        settingsRepository.setAutoSkipIntrosCreditsEnabled(enabled)
        _uiState.update { it.copy(autoSkipIntrosCredits = enabled) }
    }

    fun setNightShieldWarmthLevel(level: Float) {
        val clamped = level.coerceIn(0.1f, 0.85f)
        settingsRepository.setNightShieldWarmthLevel(clamped)
        settingsRepository.setEyeComfortWarmth(clamped)
        _uiState.update { it.copy(nightShieldWarmthLevel = clamped, eyeComfortWarmth = clamped) }
    }

    fun setMiniPlayerSizeMode(mode: MiniPlayerSizeMode) {
        _uiState.update { it.copy(miniPlayerSizeMode = mode) }
    }

    fun toggleMiniPlayerSizeMode() {
        _uiState.update {
            it.copy(
                miniPlayerSizeMode = if (it.miniPlayerSizeMode == MiniPlayerSizeMode.COMPACT)
                    MiniPlayerSizeMode.MEDIUM
                else
                    MiniPlayerSizeMode.COMPACT
            )
        }
    }

    fun toggleNightModeAudio() {
        val next = !_uiState.value.nightModeAudioEnabled
        settingsRepository.setNightModeAudioEnabled(next)
        _uiState.update {
            it.copy(
                nightModeAudioEnabled = next,
                nightModeEnabled = next && it.eyeComfortEnabled
            )
        }
    }

    fun toggleDialogueEnhancer() {
        _uiState.update { it.copy(dialogueEnhancerEnabled = !it.dialogueEnhancerEnabled) }
    }

    fun getSavedAudioTrackForVideo(videoPath: String): com.cyosplay.player.data.model.SavedAudioTrackSelection? {
        return settingsRepository.getSavedAudioTrackForVideo(videoPath)
    }

    fun setAudioMuted(muted: Boolean, videoPath: String? = null) {
        val activePath = videoPath?.takeIf { it.isNotBlank() }
            ?: (_uiState.value.currentScreen as? Screen.Player)?.video?.path
            ?: _uiState.value.lastPlayedVideoPath
        if (!activePath.isNullOrBlank()) {
            val currentSaved = settingsRepository.getSavedAudioTrackForVideo(activePath)
            val currentSelectedTrack = _uiState.value.audioTracks.firstOrNull { it.isSelected }
                ?: _uiState.value.audioTracks.firstOrNull()
            val updatedSaved = com.cyosplay.player.data.model.SavedAudioTrackSelection(
                groupIndex = currentSaved?.groupIndex ?: currentSelectedTrack?.groupIndex ?: 0,
                trackIndex = currentSaved?.trackIndex ?: currentSelectedTrack?.trackIndex ?: 0,
                language = currentSaved?.language ?: currentSelectedTrack?.language ?: "",
                label = currentSaved?.label ?: currentSelectedTrack?.label ?: "",
                codecBadge = currentSaved?.codecBadge ?: currentSelectedTrack?.codecBadge ?: "",
                isMuted = muted
            )
            settingsRepository.setSavedAudioTrackForVideo(activePath, updatedSaved)
        }
        _uiState.update { state ->
            state.copy(
                isAudioMuted = muted,
                audioTracks = state.audioTracks.map { it.copy(isSelected = if (muted) false else it.isSelected) },
                videoAudioTrackMap = settingsRepository.getAllSavedAudioTracks(),
                statusBannerMessage = if (muted) "Audio track muted" else "Audio track restored"
            )
        }
    }

    fun selectAudioTrack(selected: AudioTrackInfo, videoPath: String? = null) {
        val activePath = videoPath?.takeIf { it.isNotBlank() }
            ?: (_uiState.value.currentScreen as? Screen.Player)?.video?.path
            ?: _uiState.value.lastPlayedVideoPath
        if (!activePath.isNullOrBlank()) {
            settingsRepository.setSavedAudioTrackForVideo(
                activePath,
                com.cyosplay.player.data.model.SavedAudioTrackSelection(
                    groupIndex = selected.groupIndex,
                    trackIndex = selected.trackIndex,
                    language = selected.language,
                    label = selected.label,
                    codecBadge = selected.codecBadge,
                    isMuted = false
                )
            )
        }
        _uiState.update { state ->
            applyAutoSubtitleSyncState(
                state.copy(
                    isAudioMuted = false,
                    audioTracks = state.audioTracks.map { track ->
                        track.copy(isSelected = track.groupIndex == selected.groupIndex && track.trackIndex == selected.trackIndex)
                    },
                    videoAudioTrackMap = settingsRepository.getAllSavedAudioTracks(),
                    statusBannerMessage = "Audio switched to ${selected.label} (${selected.codecBadge})"
                )
            )
        }
    }

    fun selectSubtitleTrack(selected: SubtitleTrackInfo?) {
        if (selected == null) {
            settingsRepository.setSubtitlesEnabledByUser(false)
            _uiState.update { state ->
                applyAutoSubtitleSyncState(
                    state.copy(
                        subtitlesEnabledByUser = false,
                        playAllSubtitlesSimultaneously = false,
                        selectedExternalSubtitleTrackIds = emptySet(),
                        externalSubtitleCues = emptyList(),
                        secondaryExternalSubtitleCues = emptyList(),
                        subtitleTracks = state.subtitleTracks.map { it.copy(isSelected = false) },
                        statusBannerMessage = "Subtitles disabled"
                    )
                )
            }
            return
        }
        settingsRepository.setSubtitlesEnabledByUser(true)
        _uiState.update { state ->
            val updatedSubtitles = state.subtitleTracks.map { track ->
                track.copy(
                    isSelected = track.groupIndex == selected.groupIndex &&
                        track.trackIndex == selected.trackIndex
                )
            }
            applyAutoSubtitleSyncState(
                state.copy(
                    subtitlesEnabledByUser = true,
                    subtitleTracks = updatedSubtitles,
                    statusBannerMessage = "Subtitle switched to ${selected.label}"
                )
            )
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun toggleBackgroundAudio() {
        val nextEnabled = !_uiState.value.backgroundAudioEnabled
        com.cyos.player.CyosPlayerHolder.setBackgroundAudioActive(nextEnabled)
        com.cyos.player.CyosFastPlayer.setWakeModeForBackgroundAudio(
            com.cyos.player.CyosPlayerHolder.currentPlayerOrNull(),
            nextEnabled
        )
        if (!nextEnabled) {
            com.cyos.player.CyosPlaybackService.stopBackgroundService(appContext)
        }
        _uiState.update {
            it.copy(
                backgroundAudioEnabled = nextEnabled,
                statusBannerMessage = if (nextEnabled) {
                    "Background Audio enabled"
                } else {
                    "Background Audio disabled"
                }
            )
        }
    }

    fun setSleepTimerOption(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update {
            it.copy(
                sleepTimerOption = option,
                statusBannerMessage = when (option) {
                    SleepTimerOption.OFF -> "Sleep timer off"
                    SleepTimerOption.END_OF_VIDEO -> "Sleep timer: Pause at end of video"
                    else -> "Sleep timer set for ${option.label}"
                }
            )
        }
        if (option.minutes > 0) {
            val durationMs = option.minutes.toLong() * 60_000L
            sleepTimerJob = viewModelScope.launch {
                kotlinx.coroutines.delay(durationMs)
                triggerSleepTimerPause()
            }
        }
    }

    fun onSleepTimerEndOfVideoTriggered() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update {
            it.copy(
                sleepTimerOption = SleepTimerOption.OFF,
                isPlayerCurrentlyPlaying = false,
                isMiniPlayerPlaying = false,
                statusBannerMessage = "Sleep timer paused playback at end of video"
            )
        }
    }

    private fun triggerSleepTimerPause() {
        runCatching {
            activeExoPlayerRef?.get()?.let { player ->
                player.playWhenReady = false
                player.pause()
            }
        }
        _uiState.update {
            it.copy(
                sleepTimerOption = SleepTimerOption.OFF,
                isPlayerCurrentlyPlaying = false,
                isMiniPlayerPlaying = false,
                statusBannerMessage = "Sleep timer finished — playback paused"
            )
        }
    }

    fun toggleControlsLocked() {
        _uiState.update { it.copy(isControlsLocked = !it.isControlsLocked) }
    }

    fun setAbRepeatMarker(currentPosMs: Long) {
        val state = _uiState.value
        when {
            state.abRepeatStartMs == null -> {
                _uiState.update { it.copy(abRepeatStartMs = currentPosMs, abRepeatEndMs = null) }
            }
            state.abRepeatEndMs == null && currentPosMs > state.abRepeatStartMs + 500L -> {
                _uiState.update { it.copy(abRepeatEndMs = currentPosMs) }
            }
            else -> {
                _uiState.update { it.copy(abRepeatStartMs = null, abRepeatEndMs = null) }
            }
        }
    }

    fun clearAbRepeat() {
        _uiState.update { it.copy(abRepeatStartMs = null, abRepeatEndMs = null) }
    }

    private var progressSaveJob: kotlinx.coroutines.Job? = null

    fun savePlaybackProgress(videoPath: String, positionMs: Long, durationMs: Long) {
        val isStealth = _uiState.value.stealthPlaybackEnabled
        val matchedVideo = _uiState.value.allVideos.firstOrNull { it.path == videoPath }
        val folderId = matchedVideo?.folderId ?: ""
        val folderPath = matchedVideo?.folderPath ?: videoPath.substringBeforeLast('/', "")
        val nowMs = System.currentTimeMillis()
        val entry = PlaybackProgress(
            videoPath = videoPath,
            lastPositionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAtMs = nowMs
        )
        if (!isStealth) {
            _uiState.update { state ->
                val updatedProgressMap = state.playbackProgressMap.toMutableMap().apply {
                    put(videoPath, entry)
                }
                val updatedFolderMap = state.folderLastPlayedMap.toMutableMap().apply {
                    if (folderId.isNotBlank()) put(folderId.trim().trimEnd('/').lowercase(), videoPath)
                    if (folderPath.isNotBlank()) put(folderPath.trim().trimEnd('/').lowercase(), videoPath)
                }
                state.copy(
                    playbackProgressMap = updatedProgressMap,
                    lastPlayedVideoPath = videoPath,
                    folderLastPlayedMap = updatedFolderMap
                )
            }
        }
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.savePlaybackProgress(videoPath, positionMs, durationMs)
            if (!isStealth) {
                if (folderId.isNotBlank()) {
                    settingsRepository.saveLastPlayedVideoForFolder(folderId, videoPath)
                }
                if (folderPath.isNotBlank()) {
                    settingsRepository.saveLastPlayedVideoForFolder(folderPath, videoPath)
                }
            }
        }
    }

    /**
     * Persists the exact playback position to the cached SettingsRepository (via non-blocking apply())
     * and immediately exits the player back to the browser so resume state is guaranteed consistent.
     */
    fun savePlaybackProgressAndExit(videoPath: String, positionMs: Long, durationMs: Long) {
        progressSaveJob?.cancel()
        val isStealth = _uiState.value.stealthPlaybackEnabled
        val matchedVideo = _uiState.value.allVideos.firstOrNull { it.path == videoPath }
        val folderId = matchedVideo?.folderId ?: ""
        val folderPath = matchedVideo?.folderPath ?: videoPath.substringBeforeLast('/', "")
        val nowMs = System.currentTimeMillis()
        val entry = PlaybackProgress(
            videoPath = videoPath,
            lastPositionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAtMs = nowMs
        )
        settingsRepository.savePlaybackProgress(videoPath, positionMs, durationMs)
        if (!isStealth) {
            if (folderId.isNotBlank()) {
                settingsRepository.saveLastPlayedVideoForFolder(folderId, videoPath)
            }
            if (folderPath.isNotBlank()) {
                settingsRepository.saveLastPlayedVideoForFolder(folderPath, videoPath)
            }
            _uiState.update { state ->
                val updatedProgressMap = state.playbackProgressMap.toMutableMap().apply {
                    put(videoPath, entry)
                }
                val updatedFolderMap = state.folderLastPlayedMap.toMutableMap().apply {
                    if (folderId.isNotBlank()) put(folderId.trim().trimEnd('/').lowercase(), videoPath)
                    if (folderPath.isNotBlank()) put(folderPath.trim().trimEnd('/').lowercase(), videoPath)
                }
                state.copy(
                    playbackProgressMap = updatedProgressMap,
                    lastPlayedVideoPath = videoPath,
                    folderLastPlayedMap = updatedFolderMap
                )
            }
        }
        navigateBack()
    }

    fun dismissContinueWatchingBanner() {
        settingsRepository.setLastPlayedVideoPath(null)
        _uiState.update { it.copy(lastPlayedVideoPath = null) }
    }

    fun addBookmarkAt(videoPath: String, positionMs: Long) {
        val updated = settingsRepository.addVideoBookmark(videoPath, positionMs)
        _uiState.update { it.copy(activeVideoBookmarks = updated) }
    }

    fun removeBookmarkAt(videoPath: String, positionMs: Long) {
        val updated = settingsRepository.removeVideoBookmark(videoPath, positionMs)
        _uiState.update { it.copy(activeVideoBookmarks = updated) }
    }

    private fun applyAutoSubtitleSyncState(state: VideoPlayerUiState): VideoPlayerUiState {
        val selectedAudio = state.audioTracks.firstOrNull { it.isSelected } ?: state.audioTracks.firstOrNull()
        val detectedAudioLang = com.cyosplay.player.util.SubtitleAudioSyncEngine.normalizeLanguageCode(
            selectedAudio?.language?.ifBlank { selectedAudio.label } ?: state.detectedAudioLanguageCode
        )
        val embeddedSelected = state.subtitleTracks.filter { it.isSelected }
        val externalSelected = state.externalSubtitleTracks.filter { it.id in state.selectedExternalSubtitleTrackIds }
        val selectedLangs = buildList {
            embeddedSelected.forEach { add(it.language.ifBlank { it.label }) }
            externalSelected.forEach { add(it.language.ifBlank { it.label }) }
        }
        val totalSelectedCount = embeddedSelected.size + externalSelected.size
        val sampleRate = selectedAudio?.sampleRateHz?.takeIf { it > 0 } ?: 48000
        val syncResult = com.cyosplay.player.util.SubtitleAudioSyncEngine.evaluateAutoSync(
            detectedAudioLanguage = detectedAudioLang,
            selectedSubtitleLanguages = selectedLangs,
            selectedTrackCount = totalSelectedCount,
            manualFineTuneOffsetMs = state.subtitleManualFineTuneOffsetMs,
            autoSyncEnabled = state.isSubtitleAudioAutoSyncEnabled,
            sampleRateHz = sampleRate
        )
        return state.copy(
            detectedAudioLanguageCode = syncResult.detectedAudioLanguage,
            subtitleAutoSyncComputedOffsetMs = syncResult.autoComputedOffsetMs,
            subtitleSyncOffsetMs = syncResult.totalAppliedOffsetMs,
            subtitleAutoSyncStatusLabel = syncResult.statusLabel
        )
    }

    fun setSubtitleAudioAutoSyncEnabled(enabled: Boolean) {
        _uiState.update { state ->
            applyAutoSubtitleSyncState(state.copy(isSubtitleAudioAutoSyncEnabled = enabled))
        }
    }

    fun toggleSubtitleAudioAutoSync() {
        setSubtitleAudioAutoSyncEnabled(!_uiState.value.isSubtitleAudioAutoSyncEnabled)
    }

    fun autoAlignSubtitlesWithAudioLanguage(detectedAudioLanguage: String) {
        val normalized = com.cyosplay.player.util.SubtitleAudioSyncEngine.normalizeLanguageCode(detectedAudioLanguage)
        _uiState.update { state ->
            applyAutoSubtitleSyncState(
                state.copy(
                    detectedAudioLanguageCode = normalized,
                    isSubtitleAudioAutoSyncEnabled = true
                )
            )
        }
    }

    fun updateAvailableTracks(
        audioTracks: List<AudioTrackInfo>,
        subtitleTracks: List<SubtitleTrackInfo>,
        activeDecoderLabel: String? = null
    ) {
        val activeVideoPath = (_uiState.value.currentScreen as? Screen.Player)?.video?.path
            ?: _uiState.value.lastPlayedVideoPath
        val savedAudio = if (!activeVideoPath.isNullOrBlank()) {
            settingsRepository.getSavedAudioTrackForVideo(activeVideoPath)
        } else {
            null
        }
        _uiState.update { state ->
            val effectiveAudio = if (savedAudio != null && audioTracks.isNotEmpty()) {
                val matched = audioTracks.firstOrNull {
                    it.groupIndex == savedAudio.groupIndex && it.trackIndex == savedAudio.trackIndex
                } ?: audioTracks.firstOrNull {
                    savedAudio.language.isNotBlank() && it.language.equals(savedAudio.language, ignoreCase = true)
                } ?: audioTracks.firstOrNull {
                    savedAudio.label.isNotBlank() && it.label.equals(savedAudio.label, ignoreCase = true)
                }
                if (matched != null) {
                    audioTracks.map { track ->
                        track.copy(
                            isSelected = !savedAudio.isMuted &&
                                track.groupIndex == matched.groupIndex &&
                                track.trackIndex == matched.trackIndex
                        )
                    }
                } else {
                    audioTracks
                }
            } else {
                audioTracks
            }
            val effectiveSubtitles = if (state.subtitlesEnabledByUser) {
                val prevSelectedKeys = state.subtitleTracks
                    .filter { it.isSelected }
                    .map { "${it.groupIndex}_${it.trackIndex}" }
                    .toSet()
                if (state.playAllSubtitlesSimultaneously) {
                    subtitleTracks.map { it.copy(isSelected = true) }
                } else if (prevSelectedKeys.isNotEmpty()) {
                    subtitleTracks.map {
                        it.copy(isSelected = "${it.groupIndex}_${it.trackIndex}" in prevSelectedKeys)
                    }
                } else {
                    subtitleTracks
                }
            } else {
                subtitleTracks.map { it.copy(isSelected = false) }
            }
            applyAutoSubtitleSyncState(
                state.copy(
                    isAudioMuted = savedAudio?.isMuted ?: state.isAudioMuted,
                    audioTracks = effectiveAudio,
                    subtitleTracks = effectiveSubtitles,
                    activeDecoderBadge = activeDecoderLabel ?: state.activeDecoderBadge
                )
            )
        }
    }

    fun setSubtitleEncodingOverride(encoding: String) {
        val clean = encoding.trim().ifBlank { "AUTO" }
        _uiState.update { it.copy(subtitleEncodingOverride = clean) }
        val existingTracks = _uiState.value.externalSubtitleTracks
        if (existingTracks.none { it.uriString.isNotBlank() }) {
            _uiState.update {
                it.copy(statusBannerMessage = "Subtitle encoding set to $clean")
            }
            return
        }
        viewModelScope.launch {
            val reparsedTracks = kotlinx.coroutines.withContext(Dispatchers.IO) {
                existingTracks.map { track ->
                    if (track.uriString.isBlank()) {
                        track
                    } else {
                        val parsed = runCatching {
                            val parsedUri = Uri.parse(track.uriString)
                            appContext.contentResolver.openInputStream(parsedUri)?.use { stream ->
                                SubtitleParser.parseSubtitleStream(stream, preferredEncoding = clean)
                            }
                        }.getOrNull()
                        if (parsed != null && parsed.isNotEmpty()) track.copy(cues = parsed) else track
                    }
                }
            }
            _uiState.update { state ->
                val primaryCues = reparsedTracks.getOrNull(state.activeExternalSubtitleTrackIndex)?.cues
                    ?: state.externalSubtitleCues
                val secondaryCues = if (state.secondarySubtitleTrackIndex >= 0) {
                    reparsedTracks.getOrNull(state.secondarySubtitleTrackIndex)?.cues
                        ?: state.secondaryExternalSubtitleCues
                } else {
                    state.secondaryExternalSubtitleCues
                }
                applyAutoSubtitleSyncState(
                    state.copy(
                        externalSubtitleTracks = reparsedTracks,
                        externalSubtitleCues = primaryCues,
                        secondaryExternalSubtitleCues = secondaryCues,
                        statusBannerMessage = "Subtitle encoding switched to $clean"
                    )
                )
            }
        }
    }

    fun loadExternalSubtitleUri(uri: Uri, asSecondary: Boolean = false) {
        val encoding = _uiState.value.subtitleEncodingOverride
        viewModelScope.launch {
            try {
                val (cues, resolvedDisplayName) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val parsedCues = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        SubtitleParser.parseSubtitleStream(stream, preferredEncoding = encoding)
                    } ?: emptyList()
                    val queriedName = runCatching {
                        appContext.contentResolver.query(
                            uri,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0 && cursor.moveToFirst()) {
                                cursor.getString(idx)?.trim()?.takeIf { it.isNotBlank() }
                            } else {
                                null
                            }
                        }
                    }.getOrNull()
                    parsedCues to queriedName
                }
                val fallbackName = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringAfterLast(':')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && !it.all { ch -> ch.isDigit() } }
                val trackLabel = resolvedDisplayName
                    ?: fallbackName
                    ?: "External Subtitle ${_uiState.value.externalSubtitleTracks.size + 1}"
                addExternalSubtitleTrack(
                    label = trackLabel,
                    uriString = uri.toString(),
                    cues = cues,
                    asSecondary = asSecondary
                )
            } catch (_: Exception) {
                _uiState.update { it.copy(statusBannerMessage = "Could not parse subtitle file") }
            }
        }
    }

    /**
     * Adds an external or parsed subtitle track to the multi-subtitle track list and supports
     * switching or simultaneous dual-subtitle display.
     */
    fun addExternalSubtitleTrack(
        label: String,
        uriString: String = "",
        cues: List<SubtitleCueItem>,
        asSecondary: Boolean = false
    ) {
        settingsRepository.setSubtitlesEnabledByUser(true)
        _uiState.update { state ->
            val inferredLang = com.cyosplay.player.util.SubtitleAudioSyncEngine.normalizeLanguageCode(label)
            val newTrack = com.cyosplay.player.data.model.ExternalSubtitleTrack(
                id = "sub_track_${state.externalSubtitleTracks.size}_${label.hashCode()}",
                label = label,
                language = inferredLang,
                uriString = uriString,
                cues = cues,
                isSelected = true
            )
            val updatedTracks = state.externalSubtitleTracks + newTrack
            val newIndex = updatedTracks.lastIndex
            val updatedSelectedIds = state.selectedExternalSubtitleTrackIds + newTrack.id
            val nextState = if (asSecondary || (state.dualSubtitlesEnabled && state.externalSubtitleCues.isNotEmpty())) {
                state.copy(
                    subtitlesEnabledByUser = true,
                    externalSubtitleTracks = updatedTracks,
                    selectedExternalSubtitleTrackIds = updatedSelectedIds,
                    secondarySubtitleTrackIndex = newIndex,
                    secondaryExternalSubtitleCues = cues,
                    dualSubtitlesEnabled = true,
                    statusBannerMessage = "Loaded secondary subtitle: $label (${cues.size} cues)"
                )
            } else {
                state.copy(
                    subtitlesEnabledByUser = true,
                    externalSubtitleTracks = updatedTracks,
                    selectedExternalSubtitleTrackIds = updatedSelectedIds,
                    activeExternalSubtitleTrackIndex = newIndex,
                    externalSubtitleCues = cues,
                    statusBannerMessage = "Loaded subtitle: $label (${cues.size} cues)"
                )
            }
            applyAutoSubtitleSyncState(nextState)
        }
    }

    fun selectExternalSubtitleTrack(index: Int) {
        _uiState.update { state ->
            val track = state.externalSubtitleTracks.getOrNull(index) ?: return@update state
            settingsRepository.setSubtitlesEnabledByUser(true)
            applyAutoSubtitleSyncState(
                state.copy(
                    subtitlesEnabledByUser = true,
                    activeExternalSubtitleTrackIndex = index,
                    selectedExternalSubtitleTrackIds = state.selectedExternalSubtitleTrackIds + track.id,
                    externalSubtitleCues = track.cues,
                    statusBannerMessage = "Primary subtitle switched to ${track.label}"
                )
            )
        }
    }

    fun selectSecondarySubtitleTrack(index: Int) {
        _uiState.update { state ->
            if (index < 0) {
                applyAutoSubtitleSyncState(
                    state.copy(
                        secondarySubtitleTrackIndex = -1,
                        secondaryExternalSubtitleCues = emptyList(),
                        dualSubtitlesEnabled = false,
                        statusBannerMessage = "Secondary subtitle disabled"
                    )
                )
            } else {
                val track = state.externalSubtitleTracks.getOrNull(index) ?: return@update state
                settingsRepository.setSubtitlesEnabledByUser(true)
                applyAutoSubtitleSyncState(
                    state.copy(
                        subtitlesEnabledByUser = true,
                        secondarySubtitleTrackIndex = index,
                        selectedExternalSubtitleTrackIds = state.selectedExternalSubtitleTrackIds + track.id,
                        secondaryExternalSubtitleCues = track.cues,
                        dualSubtitlesEnabled = true,
                        statusBannerMessage = "Dual subtitle enabled: ${track.label}"
                    )
                )
            }
        }
    }

    fun setDualSubtitlesEnabled(enabled: Boolean) {
        _uiState.update { state ->
            val secondaryIdx = if (enabled && state.secondarySubtitleTrackIndex < 0 && state.externalSubtitleTracks.size > 1) {
                state.externalSubtitleTracks.indices.firstOrNull { it != state.activeExternalSubtitleTrackIndex } ?: -1
            } else {
                state.secondarySubtitleTrackIndex
            }
            val secondaryCues = if (enabled && secondaryIdx >= 0) {
                state.externalSubtitleTracks.getOrNull(secondaryIdx)?.cues ?: state.secondaryExternalSubtitleCues
            } else if (enabled) {
                state.secondaryExternalSubtitleCues
            } else {
                state.secondaryExternalSubtitleCues
            }
            applyAutoSubtitleSyncState(
                state.copy(
                    dualSubtitlesEnabled = enabled,
                    secondarySubtitleTrackIndex = secondaryIdx,
                    secondaryExternalSubtitleCues = secondaryCues
                )
            )
        }
    }

    fun toggleDualSubtitles() {
        setDualSubtitlesEnabled(!_uiState.value.dualSubtitlesEnabled)
    }

    fun adjustSubtitleSyncOffset(deltaMs: Long) {
        _uiState.update { state ->
            val nextManual = (state.subtitleManualFineTuneOffsetMs + deltaMs).coerceIn(-60_000L, 60_000L)
            applyAutoSubtitleSyncState(state.copy(subtitleManualFineTuneOffsetMs = nextManual))
        }
    }

    fun resetSubtitleSyncOffset() {
        _uiState.update { state ->
            applyAutoSubtitleSyncState(state.copy(subtitleManualFineTuneOffsetMs = 0L))
        }
    }

    // --- Subtitle Studio & Visual Comfort Controls ---

    fun setSubtitleCustomSp(sp: Float) {
        val clamped = sp.coerceIn(12f, 64f)
        settingsRepository.setSubtitleCustomSp(clamped)
        _uiState.update { it.copy(subtitleCustomSp = clamped) }
    }

    fun increaseSubtitleFontSizeManual(stepSp: Float = 2f) {
        val next = (_uiState.value.subtitleCustomSp + stepSp).coerceIn(12f, 64f)
        setSubtitleCustomSp(next)
    }

    fun decreaseSubtitleFontSizeManual(stepSp: Float = 2f) {
        val next = (_uiState.value.subtitleCustomSp - stepSp).coerceIn(12f, 64f)
        setSubtitleCustomSp(next)
    }

    fun selectAllSubtitles() {
        settingsRepository.setSubtitlesEnabledByUser(true)
        _uiState.update { state ->
            val allExternalIds = state.externalSubtitleTracks.map { it.id }.toSet()
            val updatedExternalTracks = state.externalSubtitleTracks.map { it.copy(isSelected = true) }
            val mergedCues = if (updatedExternalTracks.isNotEmpty()) {
                updatedExternalTracks.flatMap { it.cues }.sortedBy { it.startTimeMs }
            } else {
                state.externalSubtitleCues
            }
            val secondaryCues = updatedExternalTracks.getOrNull(1)?.cues ?: state.secondaryExternalSubtitleCues
            applyAutoSubtitleSyncState(
                state.copy(
                    subtitlesEnabledByUser = true,
                    playAllSubtitlesSimultaneously = true,
                    selectedExternalSubtitleTrackIds = allExternalIds,
                    externalSubtitleTracks = updatedExternalTracks,
                    externalSubtitleCues = mergedCues,
                    secondaryExternalSubtitleCues = secondaryCues,
                    dualSubtitlesEnabled = updatedExternalTracks.size > 1 || state.dualSubtitlesEnabled,
                    subtitleTracks = state.subtitleTracks.map { it.copy(isSelected = true) },
                    statusBannerMessage = "Playing all subtitles simultaneously (${state.subtitleTracks.size + allExternalIds.size} tracks)"
                )
            )
        }
    }

    fun clearAllSubtitles() {
        settingsRepository.setSubtitlesEnabledByUser(false)
        _uiState.update { state ->
            applyAutoSubtitleSyncState(
                state.copy(
                    subtitlesEnabledByUser = false,
                    playAllSubtitlesSimultaneously = false,
                    selectedExternalSubtitleTrackIds = emptySet(),
                    externalSubtitleTracks = state.externalSubtitleTracks.map { it.copy(isSelected = false) },
                    subtitleTracks = state.subtitleTracks.map { it.copy(isSelected = false) },
                    externalSubtitleCues = emptyList(),
                    secondaryExternalSubtitleCues = emptyList(),
                    dualSubtitlesEnabled = false,
                    statusBannerMessage = "All subtitles disabled"
                )
            )
        }
    }

    fun toggleExternalSubtitleTrackSelection(trackId: String) {
        _uiState.update { state ->
            val currentSelected = state.selectedExternalSubtitleTrackIds.toMutableSet()
            if (currentSelected.contains(trackId)) {
                currentSelected.remove(trackId)
            } else {
                currentSelected.add(trackId)
            }
            val updatedExternalTracks = state.externalSubtitleTracks.map {
                it.copy(isSelected = it.id in currentSelected)
            }
            val hasAny = currentSelected.isNotEmpty() || state.subtitleTracks.any { it.isSelected }
            settingsRepository.setSubtitlesEnabledByUser(hasAny)
            val selectedTracksList = updatedExternalTracks.filter { it.isSelected }
            val mergedCues = selectedTracksList.flatMap { it.cues }.sortedBy { it.startTimeMs }
            val secondTrack = selectedTracksList.getOrNull(1)
            applyAutoSubtitleSyncState(
                state.copy(
                    subtitlesEnabledByUser = hasAny,
                    selectedExternalSubtitleTrackIds = currentSelected,
                    externalSubtitleTracks = updatedExternalTracks,
                    playAllSubtitlesSimultaneously = hasAny &&
                        currentSelected.size == state.externalSubtitleTracks.size &&
                        (state.subtitleTracks.isEmpty() || state.subtitleTracks.all { it.isSelected }),
                    externalSubtitleCues = mergedCues,
                    secondaryExternalSubtitleCues = secondTrack?.cues ?: emptyList(),
                    dualSubtitlesEnabled = selectedTracksList.size > 1
                )
            )
        }
    }

    fun toggleEmbeddedSubtitleTrackSelection(track: SubtitleTrackInfo) {
        _uiState.update { state ->
            val updated = state.subtitleTracks.map { t ->
                if (t.groupIndex == track.groupIndex && t.trackIndex == track.trackIndex) {
                    t.copy(isSelected = !t.isSelected)
                } else {
                    t
                }
            }
            val hasAny = updated.any { it.isSelected } || state.selectedExternalSubtitleTrackIds.isNotEmpty()
            settingsRepository.setSubtitlesEnabledByUser(hasAny)
            applyAutoSubtitleSyncState(
                state.copy(
                    subtitlesEnabledByUser = hasAny,
                    subtitleTracks = updated,
                    playAllSubtitlesSimultaneously = hasAny &&
                        updated.all { it.isSelected } &&
                        (state.externalSubtitleTracks.isEmpty() || state.selectedExternalSubtitleTrackIds.size == state.externalSubtitleTracks.size)
                )
            )
        }
    }

    fun setSubtitleFontSize(size: SubtitleFontSize) {
        settingsRepository.setSubtitleFontSize(size)
        _uiState.update { it.copy(subtitleFontSize = size, subtitleCustomSp = size.spValue) }
    }

    fun setSubtitleColor(color: SubtitleColorOption) {
        settingsRepository.setSubtitleColor(color)
        _uiState.update { it.copy(subtitleColor = color) }
    }

    fun setSubtitleOutlineColor(color: SubtitleOutlineColorOption) {
        settingsRepository.setSubtitleOutlineColor(color)
        _uiState.update { it.copy(subtitleOutlineColor = color) }
    }

    fun setSubtitleEdgeStyle(style: SubtitleEdgeStyle) {
        settingsRepository.setSubtitleEdgeStyle(style)
        _uiState.update { it.copy(subtitleEdgeStyle = style) }
    }

    fun setSubtitleVerticalPreset(preset: SubtitleVerticalPreset) {
        settingsRepository.setSubtitleVerticalPreset(preset)
        _uiState.update { it.copy(subtitleVerticalPreset = preset) }
    }

    fun setEyeComfortEnabled(enabled: Boolean) {
        settingsRepository.setEyeComfortEnabled(enabled)
        val unified = enabled && _uiState.value.nightModeAudioEnabled
        settingsRepository.setNightModeEnabled(unified)
        val targetWarmth = if (enabled) 0.11f else _uiState.value.eyeComfortWarmth
        val targetDim = if (enabled && unified) 0.15f else _uiState.value.extraDimIntensity
        if (enabled) {
            settingsRepository.setEyeComfortWarmth(targetWarmth)
            if (unified) settingsRepository.setExtraDimIntensity(targetDim)
        }
        _uiState.update {
            it.copy(
                eyeComfortEnabled = enabled,
                nightModeEnabled = unified,
                eyeComfortWarmth = if (enabled) targetWarmth else it.eyeComfortWarmth,
                nightShieldWarmthLevel = if (enabled) targetWarmth else it.nightShieldWarmthLevel,
                extraDimIntensity = if (enabled && unified) targetDim else it.extraDimIntensity
            )
        }
    }

    fun setEyeComfortWarmth(warmth: Float) {
        settingsRepository.setEyeComfortWarmth(warmth)
        _uiState.update { it.copy(eyeComfortWarmth = warmth) }
    }

    fun setExtraDimIntensity(dim: Float) {
        settingsRepository.setExtraDimIntensity(dim)
        _uiState.update { it.copy(extraDimIntensity = dim) }
    }

    fun toggleVisualComfortSheet(show: Boolean) {
        _uiState.update { it.copy(showVisualComfortSheet = show) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        settingsRepository.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setFontMode(mode: AppFontMode) {
        settingsRepository.setFontMode(mode)
        _uiState.update { it.copy(fontMode = mode) }
    }

    fun setTextScaleMode(mode: AdaptiveTextScaleMode) {
        settingsRepository.setTextScaleMode(mode)
        _uiState.update { it.copy(textScaleMode = mode) }
    }

    fun setBiometricVaultEnabled(enabled: Boolean) {
        settingsRepository.setBiometricVaultEnabled(enabled)
        _uiState.update { it.copy(biometricVaultEnabled = enabled) }
    }

    fun setAutoLockVaultOnBackground(enabled: Boolean) {
        settingsRepository.setAutoLockVaultOnBackground(enabled)
        _uiState.update { it.copy(autoLockVaultOnBackground = enabled) }
    }

    fun setResumePlaybackEnabled(enabled: Boolean) {
        settingsRepository.setResumePlaybackEnabled(enabled)
        _uiState.update { it.copy(resumePlaybackEnabled = enabled) }
    }

    fun setAutoPlayNextEnabled(enabled: Boolean) {
        settingsRepository.setAutoPlayNextEnabled(enabled)
        _uiState.update { it.copy(autoPlayNextEnabled = enabled) }
    }

    /**
     * Resets the saved playback position for a video back to 00:00 ("Start Over").
     */
    fun resetPlaybackProgressAndStartOver(videoPath: String) {
        progressSaveJob?.cancel()
        settingsRepository.clearPlaybackProgressForVideo(videoPath)
        val updatedMap = settingsRepository.getAllPlaybackProgress() - videoPath
        _uiState.update {
            it.copy(
                playbackProgressMap = updatedMap,
                statusBannerMessage = "Playback restarted from 00:00"
            )
        }
    }

    /**
     * Global toggle to hide all texts across folders & videos until tapped individually.
     */
    fun setHideAllTextsEnabled(enabled: Boolean) {
        settingsRepository.setHideAllTextsEnabled(enabled)
        _uiState.update {
            it.copy(
                hideAllTextsEnabled = enabled,
                revealedTextItemIds = if (enabled) emptySet() else it.revealedTextItemIds,
                tappedInspectorTextTitle = if (enabled) it.tappedInspectorTextTitle else null,
                tappedInspectorTextBody = if (enabled) it.tappedInspectorTextBody else null,
                statusBannerMessage = if (enabled) {
                    "All texts hidden • Tap any item's text badge to see full text in one place"
                } else {
                    "All texts visible"
                }
            )
        }
    }

    fun toggleHideAllTexts() {
        setHideAllTextsEnabled(!_uiState.value.hideAllTextsEnabled)
    }

    fun setAutoResumePlayback(enabled: Boolean) {
        setResumePlaybackEnabled(enabled)
    }

    /**
     * Toggles individual text visibility in place for a specific folder or video item, and
     * sets its complete unclipped text in the single-place text inspector box.
     */
    fun toggleRevealItemText(itemId: String, title: String = "", fullDetailsText: String = "") {
        val currentSet = _uiState.value.revealedTextItemIds.toMutableSet()
        val nowRevealed = if (currentSet.contains(itemId)) {
            currentSet.remove(itemId)
            false
        } else {
            currentSet.add(itemId)
            true
        }
        _uiState.update {
            it.copy(
                revealedTextItemIds = currentSet,
                tappedInspectorTextTitle = if (nowRevealed && title.isNotBlank()) title else if (currentSet.isEmpty()) null else it.tappedInspectorTextTitle,
                tappedInspectorTextBody = if (nowRevealed && fullDetailsText.isNotBlank()) fullDetailsText else if (currentSet.isEmpty()) null else it.tappedInspectorTextBody
            )
        }
    }

    fun clearSinglePlaceTextInspector() {
        _uiState.update {
            it.copy(
                tappedInspectorTextTitle = null,
                tappedInspectorTextBody = null
            )
        }
    }

    // --- Picture-in-Picture (PiP) Mode Controls ---

    fun setAutoPipEnabled(enabled: Boolean) {
        settingsRepository.setAutoPipEnabled(enabled)
        _uiState.update {
            it.copy(
                autoPipEnabled = enabled,
                statusBannerMessage = if (enabled) {
                    "Auto Picture-in-Picture on Home press enabled"
                } else {
                    "Auto Picture-in-Picture disabled (Use PiP button in player)"
                }
            )
        }
    }

    fun setInPipMode(inPip: Boolean) {
        com.cyos.player.CyosPlayerHolder.setPipActive(inPip)
        _uiState.update {
            it.copy(isInPipMode = inPip)
        }
    }

    fun updateActivePlayerPipState(isPlaying: Boolean, width: Int, height: Int) {
        _uiState.update {
            it.copy(
                isPlayerCurrentlyPlaying = isPlaying,
                activeVideoWidth = width.coerceAtLeast(16),
                activeVideoHeight = height.coerceAtLeast(9)
            )
        }
    }

    // --- In-App Media & Vault Keystore Signing ---

    fun signAndExportVaultEntry(vaultId: String) {
        viewModelScope.launch {
            val result = vaultManager.exportAndSignVaultEntry(vaultId)
            if (result != null) {
                val (_, manifest) = result
                _uiState.update {
                    it.copy(
                        statusBannerMessage = "Exported & Signed with Keystore: ${manifest.filename} (SHA-256 Verified)"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(statusBannerMessage = "Failed to export & sign vault entry")
                }
            }
        }
    }

    fun signAndExportMediaFile(video: VideoItem) {
        viewModelScope.launch {
            val file = File(video.path)
            if (file.exists() && file.isFile) {
                CryptoSecurityManager.signMediaFileWithKeystore(file)
                _uiState.update {
                    it.copy(
                        statusBannerMessage = "Signed with Keystore: ${video.title} (SHA-256 Verified)"
                    )
                }
            } else {
                val exportDir = File(appContext.filesDir, "signed_exports").apply { mkdirs() }
                val ext = video.displayName.substringAfterLast('.', "mp4")
                val staged = File(exportDir, "${video.title}.$ext")
                try {
                    appContext.contentResolver.openInputStream(video.uri)?.use { input ->
                        FileOutputStream(staged).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (staged.exists() && staged.length() > 0L) {
                        CryptoSecurityManager.signMediaFileWithKeystore(staged)
                        _uiState.update {
                            it.copy(
                                statusBannerMessage = "Signed with Keystore: ${video.title} (SHA-256 Verified)"
                            )
                        }
                    }
                } catch (_: Exception) {
                    _uiState.update {
                        it.copy(statusBannerMessage = "Unable to sign media file")
                    }
                }
            }
        }
    }

    fun signAndExportHeldMedia() {
        val targets = _uiState.value.holdContextVideos
        _uiState.update { it.copy(showHoldActionSheet = false) }
        if (targets.isEmpty()) return
        targets.forEach { video ->
            signAndExportMediaFile(video)
        }
    }

    fun toggleHideFolder(folderPath: String) {
        val updated = _uiState.value.hiddenFolderPaths.toMutableSet()
        if (updated.contains(folderPath)) {
            updated.remove(folderPath)
        } else {
            updated.add(folderPath)
        }
        settingsRepository.setHiddenFolderPaths(updated)
        _uiState.update { it.copy(hiddenFolderPaths = updated) }
        refreshLibrary()
    }

    fun onAppBackgrounded() {
        if (_uiState.value.autoLockVaultOnBackground && _uiState.value.vaultAuthState != VaultAuthState.UNINITIALIZED_NO_PIN) {
            lockVault()
        }
        val state = _uiState.value
        val activeVideo = (state.currentScreen as? Screen.Player)?.video ?: state.miniPlayerVideo
        val isPrivateOrStealth = state.stealthPlaybackEnabled || isVaultVideoItem(activeVideo)
        val activePlayer = activeExoPlayerRef?.get() ?: com.cyos.player.CyosPlayerHolder.currentPlayerOrNull()
        val isPlaybackRunning = runCatching { activePlayer?.isPlaying == true || activePlayer?.playWhenReady == true }
            .getOrDefault(false)

        if (state.backgroundAudioEnabled && !state.isInPipMode && !isPrivateOrStealth && isPlaybackRunning) {
            com.cyos.player.CyosPlayerHolder.setBackgroundAudioActive(true)
            com.cyos.player.CyosPlaybackService.startForBackgroundAudio(appContext)
        } else if (!state.backgroundAudioEnabled && !state.isInPipMode) {
            com.cyos.player.CyosPlaybackService.stopBackgroundService(appContext)
            runCatching {
                activePlayer?.let { player ->
                    if (player.isPlaying) {
                        player.playWhenReady = false
                        player.pause()
                    }
                }
            }
            if (state.isMiniPlayerPlaying) {
                _uiState.update { it.copy(isMiniPlayerPlaying = false) }
            }
        } else if (isPrivateOrStealth) {
            com.cyos.player.CyosPlaybackService.stopBackgroundService(appContext)
        }
    }

    fun matchesAdvancedVideoFilters(video: VideoItem): Boolean {
        val state = _uiState.value
        val matchesCodec = when (state.advancedCodecFilter) {
            AdvancedCodecFilter.ALL -> true
            AdvancedCodecFilter.MATROSKA_WEBM -> {
                video.formatBadge.contains("MKV", ignoreCase = true) ||
                    video.formatBadge.contains("WEBM", ignoreCase = true) ||
                    video.formatBadge.contains("MK3D", ignoreCase = true) ||
                    video.formatBadge.contains("MKS", ignoreCase = true) ||
                    video.formatBadge.contains("X26", ignoreCase = true) ||
                    video.matroskaEbmlBadge.contains("Matroska", ignoreCase = true)
            }
            AdvancedCodecFilter.H264_AVC -> {
                video.videoCodecHint.contains("H.264", ignoreCase = true) ||
                    video.videoCodecHint.contains("AVC", ignoreCase = true) ||
                    video.videoCodecHint.contains("MPEG-4", ignoreCase = true) ||
                    video.formatBadge.contains("MP4", ignoreCase = true) ||
                    video.formatBadge.contains("X26", ignoreCase = true)
            }
            AdvancedCodecFilter.HEVC_X265 -> {
                video.videoCodecHint.contains("HEVC", ignoreCase = true) ||
                    video.videoCodecHint.contains("H.265", ignoreCase = true) ||
                    video.videoCodecHint.contains("x265", ignoreCase = true)
            }
            AdvancedCodecFilter.AV1_VP9 -> {
                video.videoCodecHint.contains("AV1", ignoreCase = true) ||
                    video.videoCodecHint.contains("VP9", ignoreCase = true) ||
                    video.videoCodecHint.contains("VP8", ignoreCase = true)
            }
            AdvancedCodecFilter.MPEG4_XVID -> {
                video.videoCodecHint.contains("MPEG-4", ignoreCase = true) ||
                    video.videoCodecHint.contains("ASP", ignoreCase = true) ||
                    video.videoCodecHint.contains("LC", ignoreCase = true) ||
                    video.audioCodecHint.contains("AAC", ignoreCase = true) ||
                    video.audioCodecHint.contains("44100", ignoreCase = true) ||
                    video.matroskaEbmlBadge.contains("44100 HZ", ignoreCase = true) ||
                    video.videoCodecHint.contains("Xvid", ignoreCase = true) ||
                    video.videoCodecHint.contains("ProRes", ignoreCase = true) ||
                    video.videoCodecHint.contains("FFV1", ignoreCase = true) ||
                    video.formatBadge.contains("AVI", ignoreCase = true)
            }
        }

        val maxDim = maxOf(video.width, video.height)
        val matchesRes = when (state.resolutionFilterTier) {
            ResolutionFilterTier.ALL -> true
            ResolutionFilterTier.UHD_8K_4K -> maxDim >= 3800 || video.resolutionBadge.contains("16K", ignoreCase = true) || video.resolutionBadge.contains("8K", ignoreCase = true) || video.resolutionBadge.contains("6K", ignoreCase = true) || video.resolutionBadge.contains("5K", ignoreCase = true) || video.resolutionBadge.contains("4K", ignoreCase = true)
            ResolutionFilterTier.QHD_2K_3K -> maxDim >= 2048 || video.resolutionBadge.contains("3K", ignoreCase = true) || video.resolutionBadge.contains("2K", ignoreCase = true) || video.resolutionBadge.contains("4K", ignoreCase = true) || video.resolutionBadge.contains("5K", ignoreCase = true) || video.resolutionBadge.contains("6K", ignoreCase = true) || video.resolutionBadge.contains("8K", ignoreCase = true) || video.resolutionBadge.contains("16K", ignoreCase = true)
            ResolutionFilterTier.FHD_1080P -> maxDim >= 1900 || video.resolutionBadge.contains("1920", ignoreCase = true) || video.resolutionBadge.contains("1080", ignoreCase = true) || video.resolutionBadge.contains("2K", ignoreCase = true) || video.resolutionBadge.contains("3K", ignoreCase = true) || video.resolutionBadge.contains("4K", ignoreCase = true) || video.resolutionBadge.contains("5K", ignoreCase = true) || video.resolutionBadge.contains("6K", ignoreCase = true) || video.resolutionBadge.contains("8K", ignoreCase = true) || video.resolutionBadge.contains("16K", ignoreCase = true)
            ResolutionFilterTier.HD_720P -> maxDim >= 1260 || video.resolutionBadge.contains("720", ignoreCase = true) || video.resolutionBadge.contains("1920", ignoreCase = true) || video.resolutionBadge.contains("1080", ignoreCase = true) || video.resolutionBadge.contains("2K", ignoreCase = true) || video.resolutionBadge.contains("3K", ignoreCase = true) || video.resolutionBadge.contains("4K", ignoreCase = true) || video.resolutionBadge.contains("5K", ignoreCase = true) || video.resolutionBadge.contains("6K", ignoreCase = true) || video.resolutionBadge.contains("8K", ignoreCase = true) || video.resolutionBadge.contains("16K", ignoreCase = true)
            ResolutionFilterTier.SD_LEGACY -> maxDim in 1..1259 || video.resolutionBadge.contains("704", ignoreCase = true) || video.resolutionBadge.contains("396", ignoreCase = true) || video.resolutionBadge.contains("480", ignoreCase = true) || video.resolutionBadge.contains("360", ignoreCase = true) || video.resolutionBadge.contains("240", ignoreCase = true) || video.resolutionBadge.contains("144", ignoreCase = true) || video.resolutionBadge.contains("SD", ignoreCase = true)
        }

        return matchesCodec && matchesRes
    }

    fun sortVideos(
        videos: List<VideoItem>,
        option: VideoSortOption,
        direction: SortDirection? = null
    ): List<VideoItem> {
        val baseSorted = when (option) {
            VideoSortOption.DATE_DESC -> videos.sortedWith(
                compareByDescending<VideoItem> { it.dateModifiedSeconds }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            VideoSortOption.DATE_ASC -> videos.sortedWith(
                compareBy<VideoItem> { it.dateModifiedSeconds }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            VideoSortOption.TITLE_ASC -> videos.sortedWith(
                compareBy<VideoItem> { it.title.ifBlank { it.displayName }.trim().lowercase() }
                    .thenBy { it.displayName.lowercase() }
                    .thenBy { it.id }
            )
            VideoSortOption.TITLE_DESC -> videos.sortedWith(
                compareByDescending<VideoItem> { it.title.ifBlank { it.displayName }.trim().lowercase() }
                    .thenByDescending { it.displayName.lowercase() }
                    .thenBy { it.id }
            )
            VideoSortOption.NAME_ASC -> videos.sortedWith(
                compareBy<VideoItem> { it.title.lowercase() }
                    .thenBy { it.displayName.lowercase() }
                    .thenBy { it.id }
            )
            VideoSortOption.NAME_DESC -> videos.sortedWith(
                compareByDescending<VideoItem> { it.title.lowercase() }
                    .thenByDescending { it.displayName.lowercase() }
                    .thenBy { it.id }
            )
            VideoSortOption.SIZE_DESC -> videos.sortedWith(
                compareByDescending<VideoItem> { it.sizeBytes }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            VideoSortOption.SIZE_ASC -> videos.sortedWith(
                compareBy<VideoItem> { it.sizeBytes }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            VideoSortOption.DURATION_DESC -> videos.sortedWith(
                compareByDescending<VideoItem> { it.durationMs }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            VideoSortOption.DURATION_ASC -> videos.sortedWith(
                compareBy<VideoItem> { it.durationMs }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            VideoSortOption.RESOLUTION_DESC -> videos.sortedWith(
                compareByDescending<VideoItem> { it.width.toLong() * it.height.toLong() }
                    .thenByDescending { it.sizeBytes }
                    .thenBy { it.title.lowercase() }
            )
            VideoSortOption.FORMAT_ASC -> videos.sortedWith(
                compareBy<VideoItem> { it.formatBadge.lowercase() }
                    .thenBy { it.videoCodecHint.lowercase() }
                    .thenBy { it.title.lowercase() }
            )
        }
        val isNaturallyAsc = option in setOf(
            VideoSortOption.DATE_ASC,
            VideoSortOption.TITLE_ASC,
            VideoSortOption.NAME_ASC,
            VideoSortOption.SIZE_ASC,
            VideoSortOption.DURATION_ASC,
            VideoSortOption.FORMAT_ASC
        )
        return when (direction) {
            null -> baseSorted
            SortDirection.ASCENDING -> if (isNaturallyAsc) baseSorted else baseSorted.reversed()
            SortDirection.DESCENDING -> if (!isNaturallyAsc) baseSorted else baseSorted.reversed()
        }
    }

    fun sortFolders(
        folders: List<VideoFolder>,
        option: FolderSortOption,
        direction: SortDirection? = null
    ): List<VideoFolder> {
        val baseSorted = when (option) {
            FolderSortOption.TITLE_ASC -> folders.sortedWith(compareBy<VideoFolder> { it.name.trim().lowercase() }.thenBy { it.path.lowercase() })
            FolderSortOption.TITLE_DESC -> folders.sortedWith(compareByDescending<VideoFolder> { it.name.trim().lowercase() }.thenByDescending { it.path.lowercase() })
            FolderSortOption.NAME_ASC -> folders.sortedWith(compareBy<VideoFolder> { it.name.lowercase() }.thenBy { it.path.lowercase() })
            FolderSortOption.NAME_DESC -> folders.sortedWith(compareByDescending<VideoFolder> { it.name.lowercase() }.thenByDescending { it.path.lowercase() })
            FolderSortOption.COUNT_DESC -> folders.sortedWith(compareByDescending<VideoFolder> { it.videoCount }.thenBy { it.name.lowercase() })
            FolderSortOption.COUNT_ASC -> folders.sortedWith(compareBy<VideoFolder> { it.videoCount }.thenBy { it.name.lowercase() })
            FolderSortOption.SIZE_DESC -> folders.sortedWith(compareByDescending<VideoFolder> { it.totalSizeBytes }.thenBy { it.name.lowercase() })
            FolderSortOption.SIZE_ASC -> folders.sortedWith(compareBy<VideoFolder> { it.totalSizeBytes }.thenBy { it.name.lowercase() })
            FolderSortOption.RECENT_DESC -> folders.sortedWith(compareByDescending<VideoFolder> { it.latestModifiedSeconds }.thenBy { it.name.lowercase() })
            FolderSortOption.RECENT_ASC -> folders.sortedWith(compareBy<VideoFolder> { it.latestModifiedSeconds }.thenBy { it.name.lowercase() })
        }
        val isNaturallyAsc = option in setOf(
            FolderSortOption.TITLE_ASC,
            FolderSortOption.NAME_ASC,
            FolderSortOption.COUNT_ASC,
            FolderSortOption.SIZE_ASC,
            FolderSortOption.RECENT_ASC
        )
        return when (direction) {
            null -> baseSorted
            SortDirection.ASCENDING -> if (isNaturallyAsc) baseSorted else baseSorted.reversed()
            SortDirection.DESCENDING -> if (!isNaturallyAsc) baseSorted else baseSorted.reversed()
        }
    }
}


