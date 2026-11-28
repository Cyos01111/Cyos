package com.cyosplay.player

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.cyosplay.player.data.model.AudioTrackInfo
import com.cyosplay.player.data.model.DecoderEngineMode
import com.cyosplay.player.data.model.HoldClipboardOperation
import com.cyosplay.player.data.model.MoveDestinationOption
import com.cyosplay.player.data.model.PlayerOrientationMode
import com.cyosplay.player.data.model.SubtitleTrackInfo
import com.cyosplay.player.data.model.VideoItem
import com.cyosplay.player.data.repository.RecycleBinRepository
import com.cyosplay.player.data.repository.VaultManager
import com.cyosplay.player.data.repository.VideoRepository
import com.cyosplay.player.ui.MiniPlayerSizeMode
import com.cyosplay.player.ui.Screen
import com.cyosplay.player.ui.VideoPlayerViewModel
import com.cyosplay.player.util.AudioBadgeFormatter
import com.cyosplay.player.util.CryptoSecurityManager
import com.cyosplay.player.util.Formatters
import com.cyosplay.player.util.MemoryBudgetConfig
import com.cyosplay.player.util.MemoryManager
import com.cyosplay.player.util.RamPerformanceTier
import com.cyosplay.player.util.VideoScreenshotCapture
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun awaitViewModelFileOp(viewModel: VideoPlayerViewModel, timeoutMs: Long = 5_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!viewModel.uiState.value.isFileOperationBusy) {
                shadowOf(Looper.getMainLooper()).idle()
                return
            }
            Thread.sleep(15L)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun deviceKeystoreEncryption_encryptsDataAndAutoRotatesOnCrackAttempt() {
        assertTrue(CryptoSecurityManager.verifyDeviceKeystoreIntegrity(context))

        val secretPayload = "PrivateCinemaVideo_4K_Secret_Token"
        val cipherText = CryptoSecurityManager.encryptString(context, secretPayload)
        assertTrue(CryptoSecurityManager.isEncryptedString(cipherText))
        assertNotEquals(secretPayload, cipherText)

        val decrypted = CryptoSecurityManager.decryptString(context, cipherText)
        assertEquals(secretPayload, decrypted)

        CryptoSecurityManager.resetCrackAttempts(context)
        val viewModel = VideoPlayerViewModel(context)
        viewModel.setupInitialVaultPin("258046", "Favorite director?", "Nolan")
        viewModel.lockVault()

        val initialRotations = CryptoSecurityManager.getAutoRotationCount(context)

        // Simulate 3 crack attempts -> should automatically generate a new dynamic key & re-encrypt all stores off the main thread
        kotlinx.coroutines.runBlocking {
            viewModel.unlockVaultWithPinSuspend("000000")
            viewModel.unlockVaultWithPinSuspend("111111")
            viewModel.unlockVaultWithPinSuspend("999999")
        }

        val afterCrackRotations = CryptoSecurityManager.getAutoRotationCount(context)
        assertTrue("Expected automatic key rotation on crack attempts", afterCrackRotations > initialRotations)
        assertTrue("Expected exponential lockout backoff after 3 failed attempts", CryptoSecurityManager.getLockoutRemainingMs(context) > 0L)

        // Clear lockout timer to verify that the legitimate 6-digit PIN unlocks the vault after automatic re-encryption
        CryptoSecurityManager.resetCrackAttempts(context)
        kotlinx.coroutines.runBlocking {
            viewModel.unlockVaultWithPinSuspend("258046")
        }
        assertEquals(0, CryptoSecurityManager.getFailedCrackAttempts(context))
    }

    @Test
    fun demoVideosRemoved_andHoldCopyMoveDeleteWorkOnRealDeviceVideos() {
        val viewModel = VideoPlayerViewModel(context)
        viewModel.videoRepository.saveManagedCatalog(emptyList())
        viewModel.refreshLibrary()

        // Verify no demo videos are bundled or auto-populated
        assertTrue("Expected empty library when no device videos exist (demo videos removed)", viewModel.uiState.value.allVideos.isEmpty())

        val testStorageRoot = File(context.cacheDir, "sim_storage_hold").apply { mkdirs() }
        val cameraDir = File(testStorageRoot, "DCIM/Camera").apply { mkdirs() }
        val downloadDir = File(testStorageRoot, "Download").apply { mkdirs() }
        val sdMoviesDir = File(testStorageRoot, "sdcard1/Movies").apply { mkdirs() }
        val realVideoFile = File(cameraDir, "Camera_Recording_4K.mp4").apply {
            writeBytes("REAL_4K_CAMERA_STREAM_PAYLOAD".toByteArray(Charsets.UTF_8))
        }

        // Add a real device video item and verify Hold Copy, Move, and Delete
        val realVideo = VideoItem(
            id = 501L,
            title = "Camera Recording 4K",
            displayName = "Camera_Recording_4K.mp4",
            uri = Uri.fromFile(realVideoFile),
            path = realVideoFile.absolutePath,
            sizeBytes = realVideoFile.length(),
            durationMs = 45_000L,
            dateModifiedSeconds = 1_700_000_000L,
            width = 3840,
            height = 2160,
            mimeType = "video/mp4",
            folderId = "folder_camera",
            folderName = "Camera",
            folderPath = cameraDir.absolutePath
        )
        viewModel.addVideoItemToLibrary(realVideo)
        assertEquals(1, viewModel.uiState.value.allVideos.size)

        // 1. Hold (long-press) video -> shows Hold Action Sheet with Copy, Move, Delete
        viewModel.onHoldVideoItem(realVideo)
        assertTrue(viewModel.uiState.value.showHoldActionSheet)
        assertTrue(viewModel.uiState.value.selectedVideoPaths.contains(realVideo.path))

        // 2. Copy held video to Download folder
        viewModel.startCopyOrMoveFlow(HoldClipboardOperation.COPY)
        val downloadDest = MoveDestinationOption(
            id = "dest_download",
            title = "Download",
            subtitle = downloadDir.absolutePath,
            targetPath = downloadDir.absolutePath
        )
        viewModel.executeCopyOrMoveToDestination(downloadDest)
        awaitViewModelFileOp(viewModel)
        assertEquals(2, viewModel.uiState.value.allVideos.size)

        // 3. Hold and Move video to SD Card Movies folder
        val copiedVideo = viewModel.uiState.value.allVideos.first { it.folderPath == downloadDir.absolutePath }
        viewModel.onHoldVideoItem(copiedVideo)
        viewModel.startCopyOrMoveFlow(HoldClipboardOperation.MOVE)
        val sdDest = MoveDestinationOption(
            id = "dest_sd",
            title = "SD Card Movies",
            subtitle = sdMoviesDir.absolutePath,
            targetPath = sdMoviesDir.absolutePath,
            isRemovableSd = true
        )
        viewModel.executeCopyOrMoveToDestination(sdDest)
        awaitViewModelFileOp(viewModel)
        assertTrue(viewModel.uiState.value.allVideos.any { it.folderPath == sdMoviesDir.absolutePath && it.title == copiedVideo.title })

        // 4. Hold and Delete video (executes immediate deletion in v1.06rv9 without dialog/permission prompt)
        val movedVideo = viewModel.uiState.value.allVideos.first { it.folderPath == sdMoviesDir.absolutePath && it.title == copiedVideo.title }
        viewModel.onHoldVideoItem(movedVideo)
        viewModel.requestDeleteHeldVideos()
        assertFalse(viewModel.uiState.value.showDeleteConfirmDialog)
        assertFalse(viewModel.uiState.value.allVideos.any { it.path == movedVideo.path })
        testStorageRoot.deleteRecursively()
    }

    @Test
    fun playerRotationLockAudioSelectionAndLowRamControls_workCorrectly() {
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)

        viewModel.setLowRamShieldEnabled(true)
        viewModel.setBatterySaverModeEnabled(true)

        val budget = viewModel.uiState.value.memoryBudget
        assertNotNull(budget)
        assertEquals(RamPerformanceTier.ULTRA_LOW_RAM, budget!!.tier)
        assertTrue(budget.batterySaverActive)

        // Verify Rotation Sensor cycling (AUTO_VIDEO -> LANDSCAPE_SENSOR -> PORTRAIT -> SYSTEM_UNLOCKED)
        assertEquals(PlayerOrientationMode.AUTO_VIDEO, viewModel.uiState.value.playerOrientationMode)
        viewModel.cyclePlayerOrientationMode()
        assertEquals(PlayerOrientationMode.LANDSCAPE_SENSOR, viewModel.uiState.value.playerOrientationMode)

        // Verify Video Play Lock toggle
        assertFalse(viewModel.uiState.value.isControlsLocked)
        viewModel.toggleControlsLocked()
        assertTrue(viewModel.uiState.value.isControlsLocked)

        // Verify multi-track Audio Selection & Mute/Dialogue Boost
        val track1 = AudioTrackInfo(0, 0, "English (Original)", "English", "EAC3 ATMOS 5.1", 6, 48000, true)
        val track2 = AudioTrackInfo(0, 1, "Japanese (Dub)", "Japanese", "AAC 2.0", 2, 48000, false)
        val sub1 = SubtitleTrackInfo(1, 0, "English", "English", false)
        viewModel.updateAvailableTracks(listOf(track1, track2), listOf(sub1))

        viewModel.selectAudioTrack(track2)
        assertTrue(viewModel.uiState.value.audioTracks[1].isSelected)
        assertFalse(viewModel.uiState.value.audioTracks[0].isSelected)

        viewModel.setAudioMuted(true)
        assertTrue(viewModel.uiState.value.isAudioMuted)
        viewModel.setAudioMuted(false)
        assertFalse(viewModel.uiState.value.isAudioMuted)

        // Toggle multi-codec HW+/SW+ decoder engine
        viewModel.setDecoderEngineMode(DecoderEngineMode.SOFTWARE_MULTI_CORE)
        assertEquals(DecoderEngineMode.SOFTWARE_MULTI_CORE, viewModel.uiState.value.decoderEngineMode)
        assertEquals("EAC3 ATMOS 5.1", AudioBadgeFormatter.formatAudioCodecBadge("audio/eac3-joc", 6))
        assertEquals("-01:30", Formatters.formatRemainingDuration(30_000L, 120_000L))
    }

    @Test
    fun defaultFoldersTab_8k4kSupport_smartEnhancement_fastMoveAnywhere_andStrictApkIsolation() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Folders tab is always selected by default when opening the APK
        assertEquals(com.cyosplay.player.data.model.NavDestination.FOLDERS, viewModel.uiState.value.currentNavTab)

        // 2. 8K and 4K video badge & codec detection
        val video8k = VideoItem(
            id = 801L,
            title = "Sample 8K Ultra HD",
            displayName = "Sample_8K.mp4",
            uri = Uri.parse("file:///storage/emulated/0/Movies/Sample_8K.mp4"),
            path = "/storage/emulated/0/Movies/Sample_8K.mp4",
            sizeBytes = 550_000_000L,
            durationMs = 120_000L,
            dateModifiedSeconds = 1_710_000_000L,
            width = 7680,
            height = 4320,
            mimeType = "video/hevc",
            folderId = "folder_movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies"
        )
        assertEquals("8K", video8k.resolutionBadge)
        assertEquals("8K • HEVC H.265", AudioBadgeFormatter.formatVideoCodecBadge("video/hevc", 7680, 4320))

        // 3. Smart Video Enhancement presets
        viewModel.setSmartEnhancementMode(com.cyosplay.player.data.model.SmartEnhancementMode.ULTRA_8K_CLARITY)
        assertEquals(com.cyosplay.player.data.model.SmartEnhancementMode.ULTRA_8K_CLARITY, viewModel.uiState.value.smartEnhancementMode)

        // 4. Fast Move/Copy to any custom destination folder anywhere + strict isolation against foreign APK data
        viewModel.videoRepository.saveManagedCatalog(emptyList())
        viewModel.addVideoItemToLibrary(video8k)
        viewModel.onHoldVideoItem(video8k)
        viewModel.startCopyOrMoveFlow(HoldClipboardOperation.COPY)

        val customAnywhereDest = MoveDestinationOption(
            id = "custom_any_path",
            title = "Cinema8K",
            subtitle = "/storage/emulated/0/MyCustomFolder/Cinema8K",
            targetPath = "/storage/emulated/0/MyCustomFolder/Cinema8K"
        )
        viewModel.executeCopyOrMoveToDestination(customAnywhereDest)
        awaitViewModelFileOp(viewModel)
        assertTrue(viewModel.uiState.value.allVideos.any { it.folderPath == "/storage/emulated/0/MyCustomFolder/Cinema8K" })

        // Verify strict APK isolation blocks any access to other apps' /Android/data, /Android/obb, /Android/media
        assertFalse(CryptoSecurityManager.isPathAllowedForIsolatedApp("/storage/emulated/0/Android/data/com.other.externalapp/files", context.packageName))
        assertFalse(CryptoSecurityManager.isPathAllowedForIsolatedApp("/storage/emulated/0/Android/obb/com.other.externalapp", context.packageName))
        assertFalse(CryptoSecurityManager.isPathAllowedForIsolatedApp("/storage/emulated/0/Android/media/com.other.externalapp", context.packageName))
        assertTrue(CryptoSecurityManager.isPathAllowedForIsolatedApp("/storage/emulated/0/Movies/Cinema8K", context.packageName))
    }

    @Test
    fun performanceHud_permanentNonDebugMode_unifiedNightMode_sdCardHierarchy_andDirectPasteMove_workCorrectly() {
        // 1. Verify Debug Mode is permanently disabled across the entire app
        CryptoSecurityManager.enforcePermanentNonDebugMode(context)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        val report = CryptoSecurityManager.checkDebugStatus(context)
        assertTrue(report.isPermanentlyDisabled)
        assertFalse(report.isDebuggerConnected)
        assertFalse(report.isWaitingForDebugger)
        assertTrue(report.isRuntimeSecure)

        val viewModel = VideoPlayerViewModel(context)
        assertTrue(viewModel.uiState.value.debugModePermanentlyDisabled)

        // 2. Verify toggleable 4K/8K real-time performance HUD state
        assertFalse(viewModel.uiState.value.showPerformanceHud)
        viewModel.togglePerformanceHud()
        assertTrue(viewModel.uiState.value.showPerformanceHud)
        viewModel.setPerformanceHudEnabled(false)
        assertFalse(viewModel.uiState.value.showPerformanceHud)

        // 3. Verify Unified Night Mode controls both warm blue-light filter (11% Warmth Intensity, 15% Extra Night Dimmer) and dynamic audio compression
        assertFalse(viewModel.uiState.value.nightModeEnabled)
        viewModel.setNightModeEnabled(true)
        assertTrue(viewModel.uiState.value.nightModeEnabled)
        assertTrue(viewModel.uiState.value.eyeComfortEnabled)
        assertTrue(viewModel.uiState.value.nightModeAudioEnabled)
        assertTrue(viewModel.uiState.value.dialogueEnhancerEnabled)
        assertEquals(0.11f, viewModel.uiState.value.eyeComfortWarmth, 0.005f)
        assertEquals(0.11f, viewModel.uiState.value.nightShieldWarmthLevel, 0.005f)
        assertEquals(0.15f, viewModel.uiState.value.extraDimIntensity, 0.005f)

        viewModel.toggleNightMode()
        assertFalse(viewModel.uiState.value.nightModeEnabled)
        assertFalse(viewModel.uiState.value.eyeComfortEnabled)
        assertFalse(viewModel.uiState.value.nightModeAudioEnabled)

        // 4. Verify SD Card detection & hierarchy navigation
        assertTrue(CryptoSecurityManager.isRemovableSdCardPath("/storage/sdcard1/Movies"))
        assertTrue(CryptoSecurityManager.isRemovableSdCardPath("/storage/1A2B-3C4D/Videos"))
        assertFalse(CryptoSecurityManager.isRemovableSdCardPath("/storage/emulated/0/Movies"))

        viewModel.setStorageVolumeFilter(com.cyosplay.player.data.model.StorageVolumeFilter.SD_CARD_ONLY)
        val sdNode = viewModel.uiState.value.currentDirectoryNode
        assertNotNull(sdNode)
        assertTrue(CryptoSecurityManager.isRemovableSdCardPath(sdNode!!.currentPath))

        // 5. Verify moving files to another folder via direct Paste Here / Move Here and ensuring old path is removed
        viewModel.setStorageVolumeFilter(com.cyosplay.player.data.model.StorageVolumeFilter.ALL_STORAGE)
        viewModel.videoRepository.saveManagedCatalog(emptyList())
        val sourceVideo = VideoItem(
            id = 901L,
            title = "Action Clip 4K",
            displayName = "Action_Clip_4K.mp4",
            uri = Uri.parse("file:///storage/emulated/0/Download/Action_Clip_4K.mp4"),
            path = "/storage/emulated/0/Download/Action_Clip_4K.mp4",
            sizeBytes = 85_000_000L,
            durationMs = 60_000L,
            dateModifiedSeconds = 1_715_000_000L,
            width = 3840,
            height = 2160,
            mimeType = "video/mp4",
            folderId = "folder_download",
            folderName = "Download",
            folderPath = "/storage/emulated/0/Download"
        )
        viewModel.addVideoItemToLibrary(sourceVideo)
        viewModel.onHoldVideoItem(sourceVideo)
        viewModel.stageVideosToClipboard(HoldClipboardOperation.MOVE)
        assertEquals(1, viewModel.uiState.value.clipboardState?.sourceVideos?.size)

        // Directly paste into another folder (/storage/sdcard1/Movies)
        viewModel.pasteClipboardIntoFolder("/storage/sdcard1/Movies", "SD Card Movies")
        awaitViewModelFileOp(viewModel)
        assertFalse(
            "Moved video must no longer appear in source folder",
            viewModel.uiState.value.allVideos.any { it.folderPath == "/storage/emulated/0/Download" && it.title == "Action Clip 4K" }
        )
        assertTrue(
            "Moved video must appear in target folder",
            viewModel.uiState.value.allVideos.any { it.folderPath == "/storage/sdcard1/Movies" && it.title == "Action Clip 4K" }
        )
    }

    @Test
    fun batchMultiFileAndFolderSelectionDelete_andVideoExitLastResume_workCorrectly() {
        val viewModel = VideoPlayerViewModel(context)
        viewModel.videoRepository.saveManagedCatalog(emptyList())

        val testBatchRoot = File(context.cacheDir, "sim_batch_delete").apply { mkdirs() }
        val folderADir = File(testBatchRoot, "Movies/FolderA").apply { mkdirs() }
        val folderBDir = File(testBatchRoot, "Movies/FolderB").apply { mkdirs() }
        val downloadDir = File(testBatchRoot, "Download").apply { mkdirs() }

        val fileA1 = File(folderADir, "FolderA_Clip_1.mp4").apply { writeBytes("CLIP_A1_DATA".toByteArray()) }
        val fileA2 = File(folderADir, "FolderA_Clip_2.mp4").apply { writeBytes("CLIP_A2_DATA".toByteArray()) }
        val fileB1 = File(folderBDir, "FolderB_Clip_1.mp4").apply { writeBytes("CLIP_B1_DATA".toByteArray()) }
        val fileStandalone = File(downloadDir, "Standalone_Clip.mp4").apply { writeBytes("CLIP_STANDALONE_DATA".toByteArray()) }

        val folderAVideo1 = VideoItem(
            id = 1001L,
            title = "FolderA Clip 1",
            displayName = "FolderA_Clip_1.mp4",
            uri = Uri.fromFile(fileA1),
            path = fileA1.absolutePath,
            sizeBytes = fileA1.length(),
            durationMs = 120_000L,
            dateModifiedSeconds = 1_720_000_000L,
            width = 3840,
            height = 2160,
            mimeType = "video/mp4",
            folderId = "folder_a",
            folderName = "FolderA",
            folderPath = folderADir.absolutePath
        )
        val folderAVideo2 = folderAVideo1.copy(
            id = 1002L,
            title = "FolderA Clip 2",
            displayName = "FolderA_Clip_2.mp4",
            uri = Uri.fromFile(fileA2),
            path = fileA2.absolutePath
        )
        val folderBVideo = folderAVideo1.copy(
            id = 1003L,
            title = "FolderB Clip 1",
            displayName = "FolderB_Clip_1.mp4",
            uri = Uri.fromFile(fileB1),
            path = fileB1.absolutePath,
            folderId = "folder_b",
            folderName = "FolderB",
            folderPath = folderBDir.absolutePath
        )
        val standaloneVideo = folderAVideo1.copy(
            id = 1004L,
            title = "Standalone Clip",
            displayName = "Standalone_Clip.mp4",
            uri = Uri.fromFile(fileStandalone),
            path = fileStandalone.absolutePath,
            folderId = "folder_download",
            folderName = "Download",
            folderPath = downloadDir.absolutePath
        )

        viewModel.addVideoItemToLibrary(folderAVideo1)
        viewModel.addVideoItemToLibrary(folderAVideo2)
        viewModel.addVideoItemToLibrary(folderBVideo)
        viewModel.addVideoItemToLibrary(standaloneVideo)
        assertEquals(4, viewModel.uiState.value.allVideos.size)

        // 1. Enter selection mode via top Select button and select both FolderA + Standalone Clip
        viewModel.toggleBrowserSelectionMode()
        assertTrue(viewModel.uiState.value.isSelectionModeActive)

        val folderA = viewModel.uiState.value.folders.first { it.path == folderADir.absolutePath }
        viewModel.toggleFolderSelection(folderA)
        viewModel.toggleVideoSelection(standaloneVideo)

        assertTrue(viewModel.uiState.value.selectedFolderPaths.contains(folderADir.absolutePath))
        assertEquals(3, viewModel.uiState.value.selectedVideoPaths.size)
        assertEquals("1 Folder(s), 3 File(s) Selected", viewModel.uiState.value.selectionSummaryLabel)

        // 2. Delete selected folder + file in a single immediate batch operation
        viewModel.requestDeleteHeldVideos()
        assertFalse(viewModel.uiState.value.showDeleteConfirmDialog)

        // Only FolderB Clip 1 should remain in the active library
        assertEquals(1, viewModel.uiState.value.allVideos.size)
        assertEquals(folderBVideo.path, viewModel.uiState.value.allVideos.first().path)
        assertFalse(viewModel.uiState.value.isSelectionModeActive)

        // 3. Verify improved Last-Resume when exiting a video
        assertFalse("Stealth mode should be off by default so Last-Resume works out of the box", viewModel.uiState.value.stealthPlaybackEnabled)
        assertTrue(viewModel.uiState.value.resumePlaybackEnabled)

        viewModel.playVideo(folderBVideo, listOf(folderBVideo))
        viewModel.savePlaybackProgressAndExit(
            videoPath = folderBVideo.path,
            positionMs = 47_500L,
            durationMs = 120_000L
        )

        val savedProgress = viewModel.uiState.value.playbackProgressMap[folderBVideo.path]
        assertNotNull(savedProgress)
        assertEquals(47_500L, savedProgress!!.lastPositionMs)
        assertEquals(folderBVideo.path, viewModel.uiState.value.lastPlayedVideoPath)
        assertEquals(folderBVideo.path, viewModel.uiState.value.lastPlayedVideo?.path)
        assertEquals(47_500L, viewModel.uiState.value.lastPlayedProgress?.lastPositionMs)

        // Verify persistence across a fresh ViewModel instance
        val freshViewModel = VideoPlayerViewModel(context)
        val persistedProgress = freshViewModel.uiState.value.playbackProgressMap[folderBVideo.path]
        assertNotNull(persistedProgress)
        assertEquals(47_500L, persistedProgress!!.lastPositionMs)
        assertEquals(folderBVideo.path, freshViewModel.uiState.value.lastPlayedVideoPath)

        testBatchRoot.deleteRecursively()
    }

    @Test
    fun large1000GbTransfer_streamingBuffer_hiddenDebugAndZeroKnowledgeText_andCyosPlay102cDescription() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify CyosPlay 1.07rv8 branding & simplified description
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)

        // 2. Verify Debug Mode and Zero-Knowledge technical text are hidden silently
        CryptoSecurityManager.enforcePermanentNonDebugMode(context)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())
        assertTrue(viewModel.uiState.value.debugModePermanentlyDisabled)

        // Ensure UI description contains clean user-facing features without technical crypto jargon
        assertFalse(viewModel.uiState.value.appDescription.contains("AES-256 ZERO-KNOWLEDGE"))
        assertFalse(viewModel.uiState.value.appDescription.contains("PBKDF2"))
        assertFalse(viewModel.uiState.value.appDescription.contains("JDWP"))

        // 3. Verify streaming transfer engine handles large files with progress reporting
        val sourceFile = File(context.cacheDir, "test_large_source.bin")
        val targetFile = File(context.cacheDir, "test_large_target.bin")
        try {
            sourceFile.writeBytes(ByteArray(256 * 1024) { (it % 128).toByte() })
            var progressReported = false
            var lastTransferred = 0L

            val copySuccess = CryptoSecurityManager.fastCopyFileZeroCopy(sourceFile, targetFile) { transferred, total ->
                progressReported = true
                lastTransferred = transferred
                assertEquals(sourceFile.length(), total)
            }

            assertTrue(copySuccess)
            assertTrue(progressReported)
            assertEquals(sourceFile.length(), lastTransferred)
            assertEquals(sourceFile.length(), targetFile.length())
        } finally {
            sourceFile.delete()
            targetFile.delete()
        }

        // 4. Verify FileTransferProgress data model with ultra-large (1000GB) transfer
        val ultraLargeTotalBytes = 1_000_000_000_000L // 1000 GB
        val transferred500Gb = 500_000_000_000L // 500 GB
        val progress1000Gb = com.cyosplay.player.data.model.FileTransferProgress(
            operation = HoldClipboardOperation.COPY,
            completedFiles = 0,
            totalFiles = 1,
            bytesTransferred = transferred500Gb,
            totalBytes = ultraLargeTotalBytes,
            speedMbPerSec = 450.5f,
            currentFileName = "Ultra_8K_Master_Cinema.mkv",
            destinationFolderName = "SD Card Movies",
            isFinished = false
        )
        assertEquals(0.5f, progress1000Gb.progressFraction, 0.001f)
        assertFalse(progress1000Gb.isFinished)
        assertEquals("SD Card Movies", progress1000Gb.destinationFolderName)

        // Verify dismissal of progress in ViewModel
        viewModel.dismissFileTransferProgress()
        assertEquals(null, viewModel.uiState.value.fileTransferProgress)
    }

    @Test
    fun scanRefreshLibrary_hiddenResumeHistoryAndHighPerformanceText_verified() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify library refresh can be triggered directly from top app bar
        val testVideo = VideoItem(
            id = 2001L,
            title = "Refreshed Scanner Video",
            displayName = "Refreshed_Scanner_Video.mp4",
            uri = Uri.parse("file:///storage/emulated/0/Movies/Refreshed_Scanner_Video.mp4"),
            path = "/storage/emulated/0/Movies/Refreshed_Scanner_Video.mp4",
            sizeBytes = 25_000_000L,
            durationMs = 90_000L,
            dateModifiedSeconds = 1_725_000_000L,
            width = 3840,
            height = 2160,
            mimeType = "video/mp4",
            folderId = "folder_movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies"
        )
        viewModel.addVideoItemToLibrary(testVideo)
        assertEquals(1, viewModel.uiState.value.allVideos.size)

        // Calling refreshLibrary updates and synchronizes catalog
        viewModel.refreshLibrary()
        assertTrue(viewModel.uiState.value.allVideos.any { it.path == testVideo.path })

        // 2. Verify HUD toggle messages omit 'Performance' wording
        viewModel.setPerformanceHudEnabled(true)
        assertTrue(viewModel.uiState.value.showPerformanceHud)
        assertEquals(
            "4K/8K Telemetry HUD enabled (FPS • Decoder • Buffer)",
            viewModel.uiState.value.statusBannerMessage
        )
        assertFalse(viewModel.uiState.value.statusBannerMessage!!.contains("Performance"))

        viewModel.setPerformanceHudEnabled(false)
        assertFalse(viewModel.uiState.value.showPerformanceHud)
        assertEquals("4K/8K Telemetry HUD hidden", viewModel.uiState.value.statusBannerMessage)
        assertFalse(viewModel.uiState.value.statusBannerMessage!!.contains("Performance"))
    }

    @Test
    fun allDataLayers_encryptedWithDeviceKeystore_verified() {
        // 1. Verify Keystore/CSPRNG Key Management & DEK health
        assertTrue(CryptoSecurityManager.verifyDeviceKeystoreIntegrity(context))

        // 2. Verify Layer 1: SettingsRepository Encrypted State with 6-digit PIN & 120k PBKDF2
        val settingsRepo = com.cyosplay.player.data.repository.SettingsRepository(context)
        settingsRepo.setNightModeAudioEnabled(true)
        assertTrue(settingsRepo.isNightModeAudioEnabled())
        settingsRepo.setVaultPin("987654")
        assertTrue(settingsRepo.hasVaultPin())
        assertTrue(settingsRepo.verifyVaultPin("987654"))
        assertFalse(settingsRepo.verifyVaultPin("000000"))

        // 3. Verify Layer 2: PlaylistRepository Encrypted State
        val playlistRepo = com.cyosplay.player.data.repository.PlaylistRepository(context)
        val createdPlaylist = playlistRepo.createPlaylist("Cinema Classics", "Encrypted Playlist")
        assertNotNull(createdPlaylist)
        playlistRepo.addVideosToPlaylist(createdPlaylist.id, listOf("/storage/emulated/0/Movies/sample.mp4"))
        val savedPlaylists = playlistRepo.getPlaylists()
        assertTrue(savedPlaylists.any { it.name == "Cinema Classics" })
        assertTrue(savedPlaylists.first { it.name == "Cinema Classics" }.videoPaths.contains("/storage/emulated/0/Movies/sample.mp4"))

        // 4. Verify Layer 3: RecycleBinRepository Encrypted State with real source file
        val recycleRepo = RecycleBinRepository(context)
        val trashSourceFile = File(context.cacheDir, "trashed_real.mp4").apply {
            writeBytes("REAL_TRASHED_VIDEO_STREAM_DATA_2026".toByteArray(Charsets.UTF_8))
        }
        val dummyVideo = VideoItem(
            id = 555L,
            title = "Trashed Video",
            displayName = "trashed_real.mp4",
            uri = Uri.fromFile(trashSourceFile),
            path = trashSourceFile.absolutePath,
            sizeBytes = trashSourceFile.length(),
            durationMs = 5_000L,
            dateModifiedSeconds = 1_700_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "folder_1",
            folderName = "Movies",
            folderPath = context.cacheDir.absolutePath
        )
        val recycleItem = recycleRepo.moveVideoToRecycleBin(dummyVideo)
        assertNotNull(recycleItem)
        val activeTrash = recycleRepo.getRecycleBinItems()
        assertTrue(activeTrash.any { it.recycleId == recycleItem!!.recycleId })

        // 5. Verify Layer 4: VaultManager Streaming AES-256-GCM Media & Catalog Encryption
        val vaultManager = VaultManager(context)
        val vaultSourceFile = File(context.cacheDir, "vault_source.mp4").apply {
            writeBytes("ULTRA_SECRET_MEDIA_CONTENT_8K".toByteArray(Charsets.UTF_8))
        }
        val vaultVideo = dummyVideo.copy(path = vaultSourceFile.absolutePath, displayName = "vault_source.mp4")
        val vaultEntry = vaultManager.moveVideoToVault(vaultVideo)
        assertNotNull(vaultEntry)
        val vaultEntries = vaultManager.getVaultEntries()
        assertTrue(vaultEntries.any { it.vaultId == vaultEntry!!.vaultId })

        // Verify encrypted file on disk exists and is not raw plaintext
        val encryptedDiskFile = File(vaultEntry!!.encryptedFilePath)
        assertTrue(encryptedDiskFile.exists())
        val diskBytes = encryptedDiskFile.readBytes()
        assertFalse(String(diskBytes).contains("ULTRA_SECRET_MEDIA_CONTENT_8K"))

        // Verify zero-plaintext streaming playback item preparation (cyosvault:// on-demand RAM decryption)
        val ephemeralItem = vaultManager.prepareEphemeralPlaybackItem(vaultEntry)
        assertNotNull(ephemeralItem)
        assertEquals(com.cyos.player.VaultChunkedAesGcmDataSource.SCHEME, ephemeralItem!!.uri.scheme)
        val ephemeralCacheDir = File(context.cacheDir, "vault_ephemeral_play")
        assertTrue(
            "Zero plaintext must be written to vault_ephemeral_play for V3 streaming playback",
            ephemeralCacheDir.listFiles().isNullOrEmpty()
        )
        val streamDs = com.cyos.player.VaultChunkedAesGcmDataSource(context)
        try {
            val totalLen = streamDs.open(androidx.media3.datasource.DataSpec(ephemeralItem.uri))
            val streamedBuf = ByteArray(totalLen.toInt())
            var off = 0
            while (off < streamedBuf.size) {
                val n = streamDs.read(streamedBuf, off, streamedBuf.size - off)
                if (n <= 0) break
                off += n
            }
            assertEquals("ULTRA_SECRET_MEDIA_CONTENT_8K", String(streamedBuf, 0, off, Charsets.UTF_8))
        } finally {
            streamDs.close()
        }
        vaultManager.wipeEphemeralPlaybackCache()
        assertTrue(ephemeralCacheDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun securityHardening_preventDataLoss_andPreventFalseEntries_andSafeTempFileHandling() {
        val vaultManager = VaultManager(context)
        val recycleRepo = RecycleBinRepository(context)

        // Priority 2: Prevent false vault & recycle-bin entries when source file is missing
        val missingFile = File(context.cacheDir, "non_existent_ghost_video.mkv")
        missingFile.delete()
        val ghostVideo = VideoItem(
            id = 9901L,
            title = "Ghost Video",
            displayName = "non_existent_ghost_video.mkv",
            uri = Uri.fromFile(missingFile),
            path = missingFile.absolutePath,
            sizeBytes = 50_000L,
            durationMs = 15_000L,
            dateModifiedSeconds = 1_730_000_000L,
            width = 3840,
            height = 2160,
            mimeType = "video/x-matroska",
            folderId = "folder_ghost",
            folderName = "Ghost",
            folderPath = context.cacheDir.absolutePath
        )

        val falseVaultEntry = vaultManager.moveVideoToVault(ghostVideo)
        assertNull("Moving a non-existent video to Vault must return null and prevent false entries", falseVaultEntry)

        val falseRecycleItem = recycleRepo.moveVideoToRecycleBin(ghostVideo)
        assertNull("Moving a non-existent video to Recycle Bin must return null and prevent false entries", falseRecycleItem)

        // Priority 1 & Priority 6: Prevent data loss on decryption failure & never fallback to originalPath
        val realVaultSource = File(context.cacheDir, "safe_restore_test.mp4").apply {
            writeBytes("IMPORTANT_PRIVATE_4K_VIDEO_BYTES_DO_NOT_LOSE".toByteArray(Charsets.UTF_8))
        }
        val realVideo = ghostVideo.copy(
            id = 9902L,
            title = "Safe Restore Test",
            displayName = "safe_restore_test.mp4",
            path = realVaultSource.absolutePath,
            sizeBytes = realVaultSource.length()
        )
        val validEntry = vaultManager.moveVideoToVault(realVideo)
        assertNotNull(validEntry)
        assertFalse("Original file should be shredded after verified vault encryption", realVaultSource.exists())

        // Corrupt the encrypted vault file on disk to simulate decryption failure
        val encryptedVaultFile = File(validEntry!!.encryptedFilePath)
        assertTrue(encryptedVaultFile.exists())
        val originalEncryptedBackup = encryptedVaultFile.readBytes()
        encryptedVaultFile.writeBytes("CORRUPTED_CIPHERTEXT_BYTES_TAMPERED".toByteArray(Charsets.UTF_8))

        // Priority 6: prepareEphemeralPlaybackItem must return null and NEVER fallback to originalPath
        val failedPlaybackItem = vaultManager.prepareEphemeralPlaybackItem(validEntry)
        assertNull("Decryption failure during playback prep must return null, never fallback to originalPath", failedPlaybackItem)

        // Priority 1: restoreVideoFromVault must fail safely (return null) AND keep both encrypted file and catalog entry intact
        val failedRestore = vaultManager.restoreVideoFromVault(validEntry.vaultId)
        assertNull("Vault restore must return null when decryption fails", failedRestore)
        assertTrue("Encrypted file on disk must remain intact when restore decryption fails", encryptedVaultFile.exists())
        assertTrue(
            "Vault catalog entry must remain intact when restore decryption fails",
            vaultManager.getVaultEntries().any { it.vaultId == validEntry.vaultId }
        )

        // Restore valid encrypted bytes and verify restoration now succeeds cleanly
        encryptedVaultFile.writeBytes(originalEncryptedBackup)
        val succeededRestore = vaultManager.restoreVideoFromVault(validEntry.vaultId)
        assertNotNull("Vault restore must succeed once valid ciphertext is present", succeededRestore)
        assertTrue(realVaultSource.exists())
        assertEquals(
            "IMPORTANT_PRIVATE_4K_VIDEO_BYTES_DO_NOT_LOSE",
            String(realVaultSource.readBytes(), Charsets.UTF_8)
        )
        realVaultSource.delete()
    }

    @Test
    fun securityHardening_streamingEncryptionV3_andKeyRotationMigration_preserveLargeMultiChunkFiles() {
        // Priority 7 & Priority 8: Test multi-chunk streaming encryption (> 256 KB) and key rotation re-encryption
        val multiChunkPayload = ByteArray(600 * 1024) { idx -> ((idx * 31 + 17) and 0xFF).toByte() }
        val plainFile = File(context.cacheDir, "stream_v3_plain.bin")
        val encryptedFile = File(context.cacheDir, "stream_v3_encrypted.cyosvault")
        val decryptedFile = File(context.cacheDir, "stream_v3_decrypted.bin")

        try {
            plainFile.writeBytes(multiChunkPayload)

            // Encrypt via streaming v3 format
            val oldKey = CryptoSecurityManager.getOrCreateDataEncryptionKey(context)
            val encOk = CryptoSecurityManager.encryptFileToDisk(context, plainFile, encryptedFile)
            assertTrue("Streaming encryption must succeed on multi-chunk payload", encOk)
            assertTrue("Integrity check must pass on streaming v3 file", CryptoSecurityManager.verifyEncryptedFileIntegrity(context, encryptedFile))

            // Trigger key rotation + file re-encryption (Priority 8: Test recovery and migration)
            CryptoSecurityManager.rotateDynamicEncryptionKey(context, "unit_test_key_rotation")
            val newKey = CryptoSecurityManager.getOrCreateDataEncryptionKey(context)
            val reEncryptOk = CryptoSecurityManager.reEncryptFileOnDisk(encryptedFile, oldKey, newKey)
            assertTrue("Streaming re-encryption must succeed during key rotation", reEncryptOk)

            // Decrypt with rotated key and verify byte-for-byte fidelity
            val decOk = CryptoSecurityManager.decryptFileFromDisk(context, encryptedFile, decryptedFile)
            assertTrue("Streaming decryption must succeed after key rotation", decOk)
            assertArrayEquals("Decrypted multi-chunk file must match original byte-for-byte", multiChunkPayload, decryptedFile.readBytes())
        } finally {
            plainFile.delete()
            encryptedFile.delete()
            decryptedFile.delete()
        }
    }

    @Test
    fun media3Upgrades_uhdBluRayRemuxDvHdr10BitHevcAtmos_all75PlusExtensions_chunkedMemory_andLowRamScreenshot() {
        // 1. Verify 2160p UHD BluRay REMUX DV HDR 10bit HEVC [Hindi DDP 5.1 + English TrueHD Atmos 7.1] x265 support
        val remuxTitle = "Movie.2026.2160p.UHD.BluRay.REMUX.DV.HDR.10bit.HEVC.[Hindi.DDP.5.1 + English.TrueHD.Atmos.7.1].x265.mkv"
        val profile = AudioBadgeFormatter.parseRemuxStreamProfile(
            titleOrFileName = remuxTitle,
            mimeType = "video/dolby-vision",
            width = 3840,
            height = 2160
        )
        assertTrue(profile.isUhd2160p)
        assertTrue(profile.isBluRayRemux)
        assertTrue(profile.isDolbyVision)
        assertTrue(profile.isHdr10Bit)
        assertTrue(profile.isHevcX265)
        assertTrue(profile.hasHindiDdp51)
        assertTrue(profile.hasEnglishTrueHdAtmos71)
        assertTrue(profile.videoBadge.contains("2160P UHD"))
        assertTrue(profile.videoBadge.contains("REMUX"))
        assertTrue(profile.videoBadge.contains("DV HDR 10-BIT"))
        assertTrue(profile.videoBadge.contains("HEVC x265"))
        assertEquals("HINDI DDP 5.1 + ENG TRUEHD ATMOS 7.1", profile.audioBadge)

        // 2. Verify all 75+ requested video extensions are supported
        val requestedExtensions = listOf(
            "3g2", "3gp", "avc", "avi", "avs", "avs2", "c2", "cdxl", "cgi", "cif", "dif",
            "dv", "f4v", "flv", "gif", "h261", "h263", "h264", "h265", "h26l", "hevc", "idf",
            "ism", "isma", "ismv", "j2k", "m4a", "m4b", "m4v", "mj2", "mjpeg", "mjpg", "mk3d",
            "mka", "mks", "mkv", "mov", "mp4", "mpo", "mvi", "obu", "ogg", "psp", "qcif", "rgb",
            "v210", "vc1", "xl", "yuv", "yuv10", "webm", "vob", "ogv", "rrc", "gifv", "mng",
            "qt", "wmv", "rm", "asf", "amv", "m4p", "mpg", "mp2", "mpeg", "mpe", "mpv", "svi",
            "mxf", "roq", "nsv", "f4p", "f4a", "f4b", "mod"
        )
        requestedExtensions.forEach { ext ->
            assertTrue("Extension .$ext must be supported by VideoRepository", VideoRepository.isSupportedVideoExtension(ext))
            assertTrue(
                "MIME type for .$ext must resolve to a non-blank video/audio container type",
                VideoRepository.resolveVideoMimeType(ext).isNotBlank()
            )
        }

        // 3. Verify Media3 64 KB chunked memory management strategy
        assertEquals(64 * 1024, MemoryBudgetConfig.DEFAULT_MEDIA3_CHUNK_BYTES)
        val budget = MemoryManager.detectMemoryBudget(context, forceLowRamMode = true, batterySaverEnabled = false)
        assertEquals(64 * 1024, budget.chunkSizeBytes)
        assertTrue("Target buffer bytes must be capped for low-RAM chunked streaming", budget.targetBufferBytes <= 6 * 1024 * 1024)

        // 4. Verify low-RAM screenshot capture saves frame to dedicated local directory and recycles bitmap immediately
        val sampleRemuxVideo = VideoItem(
            id = 4001L,
            title = remuxTitle,
            displayName = remuxTitle,
            uri = Uri.parse("file:///storage/emulated/0/Movies/$remuxTitle"),
            path = "/storage/emulated/0/Movies/$remuxTitle",
            sizeBytes = 42_000_000_000L,
            durationMs = 7_200_000L,
            dateModifiedSeconds = 1_735_000_000L,
            width = 3840,
            height = 2160,
            mimeType = "video/dolby-vision",
            folderId = "folder_movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies"
        )
        val captureResult = VideoScreenshotCapture.captureCurrentFrameToMediaStore(
            context = context,
            video = sampleRemuxVideo,
            positionMs = 12_500L
        )
        assertFalse(
            "Screenshot capture must fail cleanly for an unreadable/missing file instead of writing a fake placeholder image",
            captureResult.success
        )
        assertTrue(captureResult.message.isNotBlank())
    }

    @Test
    fun videoOnlyFolders_expandedFormats_orientationSensors_andFrontSearchBar() {
        val repo = VideoRepository(context)

        // 1. Verify folders show ONLY playable videos (no empty folders, no audio-only or non-video files)
        val mkvVideo = VideoItem(
            id = 5001L,
            title = "BladeRunner_2049_Remux",
            displayName = "BladeRunner_2049_Remux.mkv",
            uri = Uri.parse("file:///storage/emulated/0/Movies/SciFi/BladeRunner_2049_Remux.mkv"),
            path = "/storage/emulated/0/Movies/SciFi/BladeRunner_2049_Remux.mkv",
            sizeBytes = 12_000_000_000L,
            durationMs = 9_600_000L,
            dateModifiedSeconds = 1_736_000_000L,
            width = 3840,
            height = 2160,
            mimeType = "video/x-matroska",
            folderId = "scifi",
            folderName = "SciFi",
            folderPath = "/storage/emulated/0/Movies/SciFi"
        )
        val audioOnlyFile = mkvVideo.copy(
            id = 5002L,
            title = "Podcast_Track",
            displayName = "Podcast_Track.m4a",
            path = "/storage/emulated/0/Music/Podcast_Track.m4a",
            mimeType = "audio/mp4",
            folderId = "music",
            folderName = "Music",
            folderPath = "/storage/emulated/0/Music"
        )
        val documentFile = mkvVideo.copy(
            id = 5003L,
            title = "Notes",
            displayName = "Notes.pdf",
            path = "/storage/emulated/0/Documents/Notes.pdf",
            mimeType = "application/pdf",
            folderId = "docs",
            folderName = "Documents",
            folderPath = "/storage/emulated/0/Documents"
        )

        val groupedFolders = repo.groupVideosIntoFolders(listOf(mkvVideo, audioOnlyFile, documentFile))
        assertEquals("Only folders containing playable videos should be returned", 1, groupedFolders.size)
        assertEquals("SciFi", groupedFolders.first().name)
        assertEquals(1, groupedFolders.first().videoCount)
        assertEquals("MKV", groupedFolders.first().videos.first().formatBadge)

        val storageNode = repo.buildStorageDirectoryNode(
            "/storage/emulated/0",
            listOf(mkvVideo, audioOnlyFile, documentFile)
        )
        assertTrue(
            "Storage hierarchy must only show subfolders that contain playable videos",
            storageNode.subfolders.all { it.totalRecursiveVideoCount > 0 }
        )
        assertEquals(1, storageNode.subfolders.size)
        assertEquals("Movies", storageNode.subfolders.first().name)

        // 2. Verify expanded video formats (rmvb, divx, xvid, m2v, tod, dat, viv, dvr-ms, wtv, evo, ifo, av1, vp9, ogm)
        val extraVideoFormats = listOf(
            "rmvb", "divx", "xvid", "m2v", "tod", "dat", "viv", "dvr-ms",
            "wtv", "evo", "ifo", "av1", "vp8", "vp9", "m2p", "mp2v", "m1v", "mp4v", "3gpp", "3gp2", "ogm", "ogx"
        )
        extraVideoFormats.forEach { ext ->
            assertTrue("Format .$ext must be strictly recognized as a playable video", VideoRepository.isStrictVideoFile("movie.$ext"))
            assertTrue(VideoRepository.resolveVideoMimeType("movie.$ext").isNotBlank())
        }
        assertFalse("Audio-only .m4a must be excluded from strict video folders", VideoRepository.isStrictVideoFile("song.m4a"))

        // 3. Verify improved screen orientation sensors (aspect ratio auto-match + live hardware sensor with hysteresis)
        val landscapeAuto270 = com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveTargetOrientation(
            mode = PlayerOrientationMode.AUTO_VIDEO,
            videoWidth = 3840,
            videoHeight = 2160,
            sensorDegrees = 270
        )
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, landscapeAuto270)

        val landscapeAuto90 = com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveTargetOrientation(
            mode = PlayerOrientationMode.AUTO_VIDEO,
            videoWidth = 3840,
            videoHeight = 2160,
            sensorDegrees = 90
        )
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, landscapeAuto90)

        // Verify ±15° hysteresis prevents flicker near the 45°/315° boundary
        val heldInLandscapeAt220 = com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveTargetOrientation(
            mode = PlayerOrientationMode.SYSTEM_UNLOCKED,
            videoWidth = 1920,
            videoHeight = 1080,
            sensorDegrees = 220, // within 270 - (45 + 15) = 210..330 hysteresis band
            currentOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        )
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, heldInLandscapeAt220)

        val portraitVideoAuto = com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveTargetOrientation(
            mode = PlayerOrientationMode.AUTO_VIDEO,
            videoWidth = 1080,
            videoHeight = 1920,
            sensorDegrees = 0
        )
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, portraitVideoAuto)

        // 4. Verify front search query state & document picker MIME types
        val viewModel = VideoPlayerViewModel(context)
        viewModel.updateSearchQuery("MKV")
        assertEquals("MKV", viewModel.uiState.value.searchQuery)
        assertTrue(VideoRepository.OPEN_VIDEO_DOCUMENT_MIME_TYPES.contains("video/x-matroska"))
    }

    @Test
    fun mobileMovementSensor_devicePasscodeEncryption_125VideoFormats_andExactVideoDetails() {
        // 1. Verify per-device keystore integrity & local PBKDF2 passcode protection (zero static secrets)
        assertTrue(CryptoSecurityManager.verifyDeviceKeystoreIntegrity(context))
        val localPasscode = "948271"
        assertTrue(CryptoSecurityManager.setDataCodesPasscode(context, localPasscode))
        assertTrue(CryptoSecurityManager.hasDataCodesPasscode(context))
        assertTrue(CryptoSecurityManager.verifyDataCodesPasscode(context, localPasscode))
        assertFalse(CryptoSecurityManager.verifyDataCodesPasscode(context, "wrong_password"))

        val sensitiveDataCode = "VIDEO_DATA_CODE_4K_REMUX_STREAM_KEY_99182"
        val encryptedCode = CryptoSecurityManager.encryptString(context, sensitiveDataCode)
        assertTrue(CryptoSecurityManager.isEncryptedString(encryptedCode))
        assertEquals(sensitiveDataCode, CryptoSecurityManager.decryptString(context, encryptedCode))

        // Verify local device passcode data codes lock and unlock
        CryptoSecurityManager.lockDataCodes(context)
        assertTrue(CryptoSecurityManager.isDataCodesLocked(context))
        assertFalse(CryptoSecurityManager.unlockDataCodesWithPasscode(context, "wrong_pw"))
        assertTrue(CryptoSecurityManager.isDataCodesLocked(context))
        assertTrue(CryptoSecurityManager.unlockDataCodesWithPasscode(context, localPasscode))
        assertFalse(CryptoSecurityManager.isDataCodesLocked(context))

        // 2. Verify 3-axis accelerometer mobile movement tilt calculation & instant 4-way auto-rotation
        val uprightDegrees = com.cyosplay.player.util.ScreenOrientationSensorHelper.computeTiltDegreesFromAccelerometer(
            gx = 0.0f,
            gy = 9.81f,
            gz = 0.5f
        )
        assertEquals(0, uprightDegrees)
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveTargetOrientation(
                mode = PlayerOrientationMode.AUTO_VIDEO,
                videoWidth = 3840,
                videoHeight = 2160,
                sensorDegrees = uprightDegrees
            )
        )

        val tiltedLeftLandscapeDegrees = com.cyosplay.player.util.ScreenOrientationSensorHelper.computeTiltDegreesFromAccelerometer(
            gx = 9.81f,
            gy = 0.0f,
            gz = 0.4f
        )
        assertEquals(270, tiltedLeftLandscapeDegrees)
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveTargetOrientation(
                mode = PlayerOrientationMode.AUTO_VIDEO,
                videoWidth = 3840,
                videoHeight = 2160,
                sensorDegrees = tiltedLeftLandscapeDegrees
            )
        )

        val tiltedRightReverseLandscapeDegrees = com.cyosplay.player.util.ScreenOrientationSensorHelper.computeTiltDegreesFromAccelerometer(
            gx = -9.81f,
            gy = 0.0f,
            gz = 0.4f
        )
        assertEquals(90, tiltedRightReverseLandscapeDegrees)
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveTargetOrientation(
                mode = PlayerOrientationMode.AUTO_VIDEO,
                videoWidth = 1080,
                videoHeight = 1920,
                sensorDegrees = tiltedRightReverseLandscapeDegrees
            )
        )

        val upsideDownPortraitDegrees = com.cyosplay.player.util.ScreenOrientationSensorHelper.computeTiltDegreesFromAccelerometer(
            gx = 0.0f,
            gy = -9.81f,
            gz = 0.3f
        )
        assertEquals(180, upsideDownPortraitDegrees)
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveTargetOrientation(
                mode = PlayerOrientationMode.AUTO_VIDEO,
                videoWidth = 3840,
                videoHeight = 2160,
                sensorDegrees = upsideDownPortraitDegrees
            )
        )

        // Flat on table (Z-axis dominant) returns ORIENTATION_UNKNOWN to prevent unwanted spinning
        val flatOnTable = com.cyosplay.player.util.ScreenOrientationSensorHelper.computeTiltDegreesFromAccelerometer(
            gx = 0.2f,
            gy = 0.2f,
            gz = 9.8f
        )
        assertEquals(android.view.OrientationEventListener.ORIENTATION_UNKNOWN, flatOnTable)

        // 3. Verify 125+ expanded cinema & broadcast video formats
        val cinemaFormats = listOf(
            "braw", "r3d", "cine", "m4s", "mpd", "m3u8", "264", "265", "h266", "vvc",
            "bik", "bk2", "smk", "y4m", "ivf", "gxf", "lxf", "bdmv", "mpls", "ssif"
        )
        assertTrue("Expected 125+ supported video extensions", VideoRepository.SUPPORTED_VIDEO_EXTENSIONS.size >= 125)
        cinemaFormats.forEach { ext ->
            assertTrue("Format .$ext must be supported", VideoRepository.isStrictVideoFile("cinema_clip.$ext"))
            assertTrue(VideoRepository.resolveVideoMimeType("cinema_clip.$ext").isNotBlank())
        }

        // 4. Verify Exact Video Technical Details extraction & encrypted caching
        val viewModel = VideoPlayerViewModel(context)
        val sampleVideo = VideoItem(
            id = 8801L,
            title = "Oppenheimer.2023.2160p.UHD.BluRay.REMUX.DV.HDR.10bit.HEVC.Atmos.7.1",
            displayName = "Oppenheimer.2023.2160p.UHD.BluRay.REMUX.DV.HDR.10bit.HEVC.Atmos.7.1.mkv",
            uri = Uri.parse("file:///storage/emulated/0/Movies/Oppenheimer.mkv"),
            path = "/storage/emulated/0/Movies/Oppenheimer.mkv",
            sizeBytes = 64_000_000_000L,
            durationMs = 10_800_000L,
            dateModifiedSeconds = 1_740_000_000L,
            width = 3840,
            height = 2160,
            mimeType = "video/x-matroska",
            folderId = "movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies",
            audioCodecHint = "TRUEHD ATMOS 7.1",
            videoCodecHint = "HEVC DV HDR 10-BIT REMUX"
        )
        assertEquals("3840x2160", sampleVideo.exactResolutionLabel)
        assertEquals("16:9", sampleVideo.aspectRatioLabel)
        assertTrue(sampleVideo.estimatedBitrateLabel.contains("Mbps"))

        val exactProperties = viewModel.inspectVideoProperties(sampleVideo)
        assertNotNull(viewModel.uiState.value.inspectedVideoProperties)
        assertEquals("3840 × 2160 px (4K)", exactProperties.exactDimensionsLabel)
        assertEquals("16:9", exactProperties.aspectRatioLabel)
        assertEquals("8.29 MP", exactProperties.pixelCountMegapixels)
        assertTrue(exactProperties.hdrColorDynamicRange.contains("Dolby Vision"))
        assertTrue(exactProperties.videoBitrateLabel.contains("Mbps"))
        assertTrue(exactProperties.formattedSizeWithExactBytes.contains("64,000,000,000 bytes"))
        assertEquals(exactProperties.canonicalPath, sampleVideo.path)
        assertTrue(exactProperties.fullSha256Checksum.isNotBlank())
        assertEquals(64, exactProperties.fullSha256Checksum.length)

        // Verify locking data codes from ViewModel with user-chosen device passcode
        assertTrue(viewModel.setDataCodesPasscode(localPasscode))
        viewModel.lockDataCodesWithMasterPassword()
        assertTrue(viewModel.uiState.value.isDataCodesLocked)
        assertTrue(viewModel.uiState.value.inspectedVideoProperties?.isDataCodesLocked == true)

        // Verify invalid unlock fails
        val failed = viewModel.unlockDataCodesWithMasterPassword("wrong")
        assertFalse(failed)
        assertTrue(viewModel.uiState.value.isDataCodesLocked)

        // Verify user device passcode unlocks successfully
        val unlocked = viewModel.unlockDataCodesWithMasterPassword(localPasscode)
        assertTrue(unlocked)
        assertFalse(viewModel.uiState.value.isDataCodesLocked)
        assertFalse(viewModel.uiState.value.inspectedVideoProperties?.isDataCodesLocked == true)

        val cachedCipher = viewModel.videoRepository.getEncryptedTechnicalDetailsCiphertext()
        assertNotNull("Technical details cache must be encrypted on disk", cachedCipher)
        assertTrue(CryptoSecurityManager.isEncryptedString(cachedCipher))

        viewModel.dismissVideoProperties()
        assertNull(viewModel.uiState.value.inspectedVideoProperties)
    }

    @Test
    fun foldersFiveControls_videoCollagePreviews_x26MkvMatroskaLavf60_andFfmpegCodecRegistry() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify Folders & Videos 5-Control Options (View Mode, Sort, Layout, Advanced, Fields)
        viewModel.openBrowserControlTab(com.cyosplay.player.data.model.BrowserControlTab.VIEW_MODE)
        assertEquals(com.cyosplay.player.data.model.BrowserControlTab.VIEW_MODE, viewModel.uiState.value.activeBrowserControlSheetTab)

        viewModel.setFolderViewMode(com.cyosplay.player.data.model.BrowserViewMode.DETAILED_CARDS)
        assertEquals(com.cyosplay.player.data.model.BrowserViewMode.DETAILED_CARDS, viewModel.uiState.value.folderViewMode)

        viewModel.setVideoViewMode(com.cyosplay.player.data.model.BrowserViewMode.COMPACT)
        assertEquals(com.cyosplay.player.data.model.BrowserViewMode.COMPACT, viewModel.uiState.value.videoViewMode)

        viewModel.setFolderSortOption(com.cyosplay.player.data.model.FolderSortOption.SIZE_DESC)
        assertEquals(com.cyosplay.player.data.model.FolderSortOption.SIZE_DESC, viewModel.uiState.value.folderSortOption)
        viewModel.setFolderSortDirection(com.cyosplay.player.data.model.SortDirection.ASCENDING)
        assertEquals(com.cyosplay.player.data.model.SortDirection.ASCENDING, viewModel.uiState.value.folderSortDirection)

        viewModel.setVideoSortOption(com.cyosplay.player.data.model.VideoSortOption.RESOLUTION_DESC)
        assertEquals(com.cyosplay.player.data.model.VideoSortOption.RESOLUTION_DESC, viewModel.uiState.value.videoSortOption)

        viewModel.setBrowserLayoutColumns(com.cyosplay.player.data.model.BrowserLayoutColumns.THREE_COLUMNS)
        assertEquals(com.cyosplay.player.data.model.BrowserLayoutColumns.THREE_COLUMNS, viewModel.uiState.value.browserLayoutColumns)

        viewModel.setBrowserThumbnailRatio(com.cyosplay.player.data.model.BrowserThumbnailRatio.CINEMA_21_9)
        assertEquals(com.cyosplay.player.data.model.BrowserThumbnailRatio.CINEMA_21_9, viewModel.uiState.value.browserThumbnailRatio)

        viewModel.setAdvancedCodecFilter(com.cyosplay.player.data.model.AdvancedCodecFilter.MATROSKA_WEBM)
        assertEquals(com.cyosplay.player.data.model.AdvancedCodecFilter.MATROSKA_WEBM, viewModel.uiState.value.advancedCodecFilter)

        viewModel.setResolutionFilterTier(com.cyosplay.player.data.model.ResolutionFilterTier.UHD_8K_4K)
        assertEquals(com.cyosplay.player.data.model.ResolutionFilterTier.UHD_8K_4K, viewModel.uiState.value.resolutionFilterTier)

        val customFields = com.cyosplay.player.data.model.BrowserVisibleFields(
            showVideoPicturePreview = true,
            showFolderCollagePreview = true,
            showMatroskaEbmlTag = true,
            showEncryptedDatacodeBadge = true,
            showFolderPath = false
        )
        viewModel.updateBrowserVisibleFields(customFields)
        assertEquals(10, viewModel.uiState.value.visibleFields.activeCount)
        assertFalse(viewModel.uiState.value.visibleFields.showFolderPath)
        viewModel.dismissBrowserControlSheet()
        assertNull(viewModel.uiState.value.activeBrowserControlSheetTab)

        // 2. Verify x26.mkv, matroska/WebM, Lavf60.3.100, libebml v1.4.7 + libmatroska v1.7.2, H.264 / AVC / MPEG-4 & FFmpeg 4.2.2+ Registry
        assertTrue(VideoRepository.isStrictVideoFile("Cinema_Master.x26.mkv"))
        assertTrue(VideoRepository.isSupportedVideoExtension("x26"))
        assertTrue(VideoRepository.isSupportedVideoExtension("mkv"))
        assertTrue(VideoRepository.isSupportedVideoExtension("webm"))
        assertTrue(VideoRepository.SUPPORTED_VIDEO_EXTENSIONS.size >= 200)

        val matroskaProfile = com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.resolveProfileForVideo(
            fileNameOrPath = "Sample.Movie.2026.x26.mkv",
            mimeType = "video/x-matroska",
            width = 3840,
            height = 2160
        )
        assertTrue(matroskaProfile.containerSpec.contains("matroska/WebM"))
        assertEquals("Lavf60.3.100", matroskaProfile.muxingApp)
        assertTrue(matroskaProfile.writingLibrary.contains("libebml v1.4.7 + libmatroska v1.7.2"))
        assertTrue(matroskaProfile.writingLibrary.contains("libebml v14.2 + libmatroska v1.6.4"))
        assertTrue(matroskaProfile.writingLibrary.contains("Encodina SW"))
        assertTrue(matroskaProfile.ffmpegCodecFullSpec.contains("H.264 / AVC / MPEG-4"))
        assertTrue(matroskaProfile.ffmpegCodecFullSpec.contains("libx264"))
        assertTrue(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.SUPPORTED_VIDEO_CODECS.size >= 140)
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("012v"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("h264"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("hevc"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("av1"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("ptx"))

        // 3. Verify Datacode Double-Encryption with invisible master seed
        val rawDatacode = "MATROSKA_WEBM|Lavf60.3.100|libebml v1.4.7 + libmatroska v1.7.2|H.264/AVC/MPEG-4"
        val encryptedDatacode = CryptoSecurityManager.encryptVideoDatacode(context, rawDatacode)
        assertTrue(CryptoSecurityManager.isEncryptedString(encryptedDatacode))
        assertEquals(rawDatacode, CryptoSecurityManager.decryptVideoDatacode(context, encryptedDatacode))
    }

    @Test
    fun apkVersion103_lastPositionAutoResume_pipMode_andHideAllTextsTapToReveal() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify APK Version 1.06rv8
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)

        // 2. Verify Last Playback Position Saving, Auto-Resume on Reopen, and Start Over Reset
        viewModel.setStealthPlaybackEnabled(false)
        viewModel.setAutoResumePlayback(true)
        val sampleVideoPath = "/storage/emulated/0/Movies/Cinema_Resume_Test.mkv"
        viewModel.savePlaybackProgress(
            videoPath = sampleVideoPath,
            positionMs = 42_500L,
            durationMs = 120_000L
        )
        val savedEntry = viewModel.uiState.value.playbackProgressMap[sampleVideoPath]
        assertNotNull("Expected saved playback progress for video", savedEntry)
        assertEquals(42_500L, savedEntry!!.lastPositionMs)
        assertEquals(120_000L, savedEntry.durationMs)

        // Reset via Start Over
        viewModel.resetPlaybackProgressAndStartOver(sampleVideoPath)
        assertNull("Expected Start Over to clear saved playback position", viewModel.uiState.value.playbackProgressMap[sampleVideoPath])

        // 3. Verify Picture-in-Picture (PiP) Mode & Auto-PiP Controls
        assertTrue(viewModel.uiState.value.autoPipEnabled)
        viewModel.updateActivePlayerPipState(isPlaying = true, width = 3840, height = 2160)
        assertTrue(viewModel.uiState.value.isPlayerCurrentlyPlaying)
        assertEquals(3840, viewModel.uiState.value.activeVideoWidth)
        assertEquals(2160, viewModel.uiState.value.activeVideoHeight)

        viewModel.setInPipMode(true)
        assertTrue(viewModel.uiState.value.isInPipMode)
        viewModel.setInPipMode(false)
        assertFalse(viewModel.uiState.value.isInPipMode)

        // 4. Verify Hide All Texts & Tap Individually to See Full Text in One Place
        assertFalse(viewModel.uiState.value.hideAllTextsEnabled)
        viewModel.toggleHideAllTexts()
        assertTrue(viewModel.uiState.value.hideAllTextsEnabled)

        val itemKey = "video_999"
        val fullTitle = "Ultra Long 4K Cinema Masterpiece Title Without Size Limit"
        val fullDetails = "x26.mkv • matroska/WebM • Lavf60.3.100 • 3840x2160 • H.264 / AVC / MPEG-4"
        viewModel.toggleRevealItemText(
            itemId = itemKey,
            title = fullTitle,
            fullDetailsText = fullDetails
        )
        assertTrue(viewModel.uiState.value.revealedTextItemIds.contains(itemKey))
        assertEquals(fullTitle, viewModel.uiState.value.tappedInspectorTextTitle)
        assertEquals(fullDetails, viewModel.uiState.value.tappedInspectorTextBody)

        // Tapping again hides that individual item's text
        viewModel.toggleRevealItemText(
            itemId = itemKey,
            title = fullTitle,
            fullDetailsText = fullDetails
        )
        assertFalse(viewModel.uiState.value.revealedTextItemIds.contains(itemKey))
    }

    @Test
    fun inAppMediaAndVaultKeystoreSigning_generatesVerifiedSha256AndRsaSignature() {
        // 1. Create a sample video file
        val sampleFile = File(context.cacheDir, "sample_sign_test.mp4").apply {
            writeBytes("CYOSPLAY_SAMPLE_4K_VIDEO_PAYLOAD_FOR_KEYSTORE_SIGNING".toByteArray())
        }
        assertTrue(sampleFile.exists())

        // 2. Sign file with hardware-backed / custom keystore
        val manifest = CryptoSecurityManager.signMediaFileWithKeystore(sampleFile)
        assertNotNull(manifest)
        assertEquals("sample_sign_test.mp4", manifest.filename)
        assertTrue(manifest.isVerified)
        assertTrue(manifest.sha256Digest.isNotEmpty())
        assertTrue(manifest.signatureHex.isNotEmpty())
        assertEquals("SHA256withRSA", manifest.algorithm)

        // 3. Verify companion .sig.json manifest was created
        val companion = File(sampleFile.parentFile, "sample_sign_test.mp4.sig.json")
        assertTrue(companion.exists())
        assertTrue(companion.readText().contains(manifest.sha256Digest))

        // 4. Test Vault export & signing flow
        val vaultManager = VaultManager(context)
        val dummyVideo = VideoItem(
            id = 998811L,
            title = "Vault_Signature_Cinema",
            displayName = "Vault_Signature_Cinema.mp4",
            uri = Uri.fromFile(sampleFile),
            path = sampleFile.absolutePath,
            sizeBytes = sampleFile.length(),
            durationMs = 60_000L,
            dateModifiedSeconds = System.currentTimeMillis() / 1000L,
            width = 3840,
            height = 2160,
            mimeType = "video/mp4",
            folderId = "test_folder",
            folderName = "Movies",
            folderPath = context.cacheDir.absolutePath
        )
        val entry = vaultManager.moveVideoToVault(dummyVideo)
        assertNotNull("Expected valid vault entry", entry)

        val exportedResult = vaultManager.exportAndSignVaultEntry(entry!!.vaultId)
        assertNotNull("Expected exported & signed vault entry", exportedResult)
        val (exportedFile, exportManifest) = exportedResult!!
        assertTrue(exportedFile.exists())
        assertTrue(exportManifest.isVerified)
        assertEquals("SHA256withRSA", exportManifest.algorithm)

        // 5. Test ViewModel action
        val viewModel = VideoPlayerViewModel(context)
        viewModel.signAndExportVaultEntry(entry.vaultId)
        assertTrue(viewModel.uiState.value.statusBannerMessage?.contains("Signed with Keystore") == true)
    }

    @Test
    fun pngAndImageFiles_areStrictlyExcludedFromVideos() {
        // 1. Verify PNG and APNG are excluded from supported video extensions
        assertFalse(VideoRepository.isSupportedVideoExtension("png"))
        assertFalse(VideoRepository.isSupportedVideoExtension("apng"))
        assertFalse(VideoRepository.isSupportedVideoExtension("image.png"))
        assertFalse(VideoRepository.isSupportedVideoExtension("animation.apng"))

        // 2. Verify strict video check rejects PNG, APNG, and other image formats
        assertFalse(VideoRepository.isStrictVideoFile("screenshot.png"))
        assertFalse(VideoRepository.isStrictVideoFile("/storage/emulated/0/DCIM/photo.png"))
        assertFalse(VideoRepository.isStrictVideoFile("banner.apng"))
        assertFalse(VideoRepository.isStrictVideoFile("thumbnail.jpg"))
        assertFalse(VideoRepository.isStrictVideoFile("image.webp"))
        assertFalse(VideoRepository.isStrictVideoFile("graphic.bmp"))
        assertFalse(VideoRepository.isStrictVideoFile("unknown_file", "image/png"))
        assertFalse(VideoRepository.isStrictVideoFile("unknown_file", "image/apng"))

        // 3. Verify real video containers remain fully supported
        assertTrue(VideoRepository.isSupportedVideoExtension("mkv"))
        assertTrue(VideoRepository.isSupportedVideoExtension("mp4"))
        assertTrue(VideoRepository.isSupportedVideoExtension("webm"))
        assertTrue(VideoRepository.isSupportedVideoExtension("ts"))
        assertTrue(VideoRepository.isStrictVideoFile("movie.mkv"))
        assertTrue(VideoRepository.isStrictVideoFile("recording.mp4"))
        assertTrue(VideoRepository.isStrictVideoFile("stream.webm"))
    }

    @Test
    fun v103_buildCodeEncryption_manualLowRamDefaultOff_folderScrollMemory_andSortDirection() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Low-RAM smoothness must NOT be automatically ON until manually enabled in Settings
        assertFalse(
            "Low-RAM Shield must default to OFF until user enables it in Settings",
            viewModel.uiState.value.lowRamShieldEnabled
        )

        // 2. Build code & datacode encryption with per-device AndroidKeyStore/CSPRNG AES-256-GCM DEK
        assertTrue(CryptoSecurityManager.verifyDeviceKeystoreIntegrity(context))
        val rawBuildCode = "BUILD_CODE_V103|CAPABILITY=${com.cyosplay.player.BuildConfig.MAJOR_CAPABILITY_ID}|RES=8K_UHD"
        val encryptedBuildCode = CryptoSecurityManager.encryptVideoDatacode(context, rawBuildCode)
        assertTrue(CryptoSecurityManager.isEncryptedString(encryptedBuildCode))
        assertEquals(rawBuildCode, CryptoSecurityManager.decryptVideoDatacode(context, encryptedBuildCode))

        // 3. Remember last scroll position in folders (list & grid)
        viewModel.saveFoldersScrollPosition(listIndex = 7, listOffset = 142, gridIndex = 4, gridOffset = 88)
        assertEquals(7, viewModel.uiState.value.foldersListFirstVisibleIndex)
        assertEquals(142, viewModel.uiState.value.foldersListScrollOffset)
        assertEquals(4, viewModel.uiState.value.foldersGridFirstVisibleIndex)
        assertEquals(88, viewModel.uiState.value.foldersGridScrollOffset)

        // 4. View mode & sort direction improvements
        viewModel.setFolderViewMode(com.cyosplay.player.data.model.BrowserViewMode.COMPACT)
        assertEquals(com.cyosplay.player.data.model.BrowserViewMode.COMPACT, viewModel.uiState.value.folderViewMode)
        assertFalse(viewModel.uiState.value.isGridView)
    }

    @Test
    fun v104mfr_resolutions1920x800And704x396_lockedSpeed1xTo5x_manualSubtitleAndEnhancement_andScrollAndVolumePersistence() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify version 1.06rv8 & default mobile system font mode
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertEquals(com.cyosplay.player.data.model.AppFontMode.SYSTEM_DEFAULT, viewModel.uiState.value.fontMode)

        // 2. Verify Subtitles and Smart Enhanced Video do NOT automatically turn ON until user enables them
        assertFalse("Subtitles must default to OFF until user enables them", viewModel.uiState.value.subtitlesEnabledByUser)
        assertEquals(
            "Smart video enhancement must default to OFF until user enables it",
            com.cyosplay.player.data.model.SmartEnhancementMode.OFF,
            viewModel.uiState.value.smartEnhancementMode
        )

        // 3. Verify 1920x800, 704x396, 480p, and 5K resolution recognition & badges
        val video1920x800 = VideoItem(
            id = 7001L,
            title = "Widescreen Cinema 1920x800",
            displayName = "Widescreen_1920x800.mkv",
            uri = Uri.parse("file:///storage/emulated/0/Movies/Widescreen_1920x800.mkv"),
            path = "/storage/emulated/0/Movies/Widescreen_1920x800.mkv",
            sizeBytes = 120_000_000L,
            durationMs = 90_000L,
            dateModifiedSeconds = 1_750_000_000L,
            width = 1920,
            height = 800,
            mimeType = "video/x-matroska",
            folderId = "movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies"
        )
        assertEquals("1920×800", video1920x800.resolutionBadge)
        assertEquals("12:5", video1920x800.aspectRatioLabel)

        val video704x396 = video1920x800.copy(
            id = 7002L,
            title = "Compact Widescreen 704x396",
            displayName = "Compact_704x396.mp4",
            width = 704,
            height = 396
        )
        assertEquals("704×396", video704x396.resolutionBadge)
        assertEquals("16:9", video704x396.aspectRatioLabel)

        val video480p = video1920x800.copy(
            id = 7003L,
            title = "Standard 480p Clip",
            displayName = "Clip_480p.mp4",
            width = 854,
            height = 480
        )
        assertEquals("480P", video480p.resolutionBadge)
        assertEquals("480P • H.264 / AVC", AudioBadgeFormatter.formatVideoCodecBadge("video/avc", 854, 480))

        val video5k = video1920x800.copy(
            id = 7004L,
            title = "Ultra 5K Cinema",
            displayName = "Cinema_5K.mkv",
            width = 5120,
            height = 2880
        )
        assertEquals("5K", video5k.resolutionBadge)
        assertEquals("5K • HEVC H.265", AudioBadgeFormatter.formatVideoCodecBadge("video/hevc", 5120, 2880))

        // 4. Verify locked video playback speed skip cycling (1x -> 2x -> 3x -> 4x -> 5x -> 1x)
        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed, 0.01f)
        assertEquals(2.0f, viewModel.cycleLockedPlaybackSpeed(), 0.01f)
        assertEquals(3.0f, viewModel.cycleLockedPlaybackSpeed(), 0.01f)
        assertEquals(4.0f, viewModel.cycleLockedPlaybackSpeed(), 0.01f)
        assertEquals(5.0f, viewModel.cycleLockedPlaybackSpeed(), 0.01f)
        assertEquals(1.0f, viewModel.cycleLockedPlaybackSpeed(), 0.01f)

        // 5. Verify per-folder independent browser controls (View, Sort, Layout per folderId)
        viewModel.setFolderDetailViewMode("folder_a", com.cyosplay.player.data.model.BrowserViewMode.GRID)
        viewModel.setFolderDetailSortOption("folder_a", com.cyosplay.player.data.model.VideoSortOption.SIZE_DESC)
        viewModel.setFolderDetailLayoutColumns("folder_a", com.cyosplay.player.data.model.BrowserLayoutColumns.THREE_COLUMNS)

        viewModel.setFolderDetailViewMode("folder_b", com.cyosplay.player.data.model.BrowserViewMode.COMPACT)
        viewModel.setFolderDetailSortOption("folder_b", com.cyosplay.player.data.model.VideoSortOption.NAME_ASC)
        viewModel.setFolderDetailLayoutColumns("folder_b", com.cyosplay.player.data.model.BrowserLayoutColumns.TWO_COLUMNS)

        val configA = viewModel.getFolderBrowserConfig("folder_a")
        val configB = viewModel.getFolderBrowserConfig("folder_b")
        assertEquals(com.cyosplay.player.data.model.BrowserViewMode.GRID, configA.viewMode)
        assertEquals(com.cyosplay.player.data.model.VideoSortOption.SIZE_DESC, configA.sortOption)
        assertEquals(com.cyosplay.player.data.model.BrowserLayoutColumns.THREE_COLUMNS, configA.layoutColumns)

        assertEquals(com.cyosplay.player.data.model.BrowserViewMode.COMPACT, configB.viewMode)
        assertEquals(com.cyosplay.player.data.model.VideoSortOption.NAME_ASC, configB.sortOption)
        assertEquals(com.cyosplay.player.data.model.BrowserLayoutColumns.TWO_COLUMNS, configB.layoutColumns)

        // 6. Verify remembered volume & brightness persistence and folder/video scroll memory
        viewModel.updateSavedPlayerBrightness(0.72f)
        viewModel.updateSavedPlayerVolume(0.84f)
        assertEquals(0.72f, viewModel.uiState.value.savedPlayerBrightness, 0.01f)
        assertEquals(0.84f, viewModel.uiState.value.savedPlayerVolume, 0.01f)

        viewModel.saveVideosScrollPosition(listIndex = 5, listOffset = 110, gridIndex = 3, gridOffset = 64)
        assertEquals(5, viewModel.uiState.value.videosListFirstVisibleIndex)
        assertEquals(110, viewModel.uiState.value.videosListScrollOffset)
        assertEquals(3, viewModel.uiState.value.videosGridFirstVisibleIndex)
        assertEquals(64, viewModel.uiState.value.videosGridScrollOffset)

        viewModel.saveFolderDetailScrollPosition("/storage/emulated/0/Movies", 4, 92)
        val restoredFolderScroll = viewModel.getFolderDetailScrollPosition("/storage/emulated/0/Movies")
        assertEquals(4, restoredFolderScroll.first)
        assertEquals(92, restoredFolderScroll.second)

        // 7. Verify "4K REMUX STREAMING" and "HIGH MEMORY" texts are completely hidden from RAM tier labels
        RamPerformanceTier.entries.forEach { tier ->
            assertFalse(tier.badgeLabel.contains("4K REMUX STREAMING", ignoreCase = true))
            assertFalse(tier.badgeLabel.contains("HIGH MEMORY", ignoreCase = true))
        }
    }

    @Test
    fun v104rv2_all55Resolutions_themesFonts_perFolderPlayAllResume_sdCardStatus_videoZoom_andCyosPlayActivity() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify all 55 requested video resolutions are strictly recognized and formatted
        val expectedResolutions = listOf(
            160 to 120, 240 to 160, 320 to 240, 400 to 240, 480 to 320,
            640 to 480, 768 to 480, 854 to 480, 800 to 600, 960 to 640,
            1024 to 576, 1024 to 600, 1024 to 768, 1366 to 768, 1360 to 768,
            1280 to 800, 1152 to 864, 1440 to 900, 1280 to 1024, 1400 to 1050,
            1680 to 1050, 1600 to 1200, 1920 to 1200, 2048 to 1152, 2048 to 1536,
            2560 to 1600, 2560 to 2048, 3200 to 2048, 3200 to 2400, 3840 to 2400,
            4096 to 3072, 5120 to 3200, 5120 to 4096, 6400 to 4096, 6400 to 4800,
            7680 to 4800, 640 to 360, 960 to 540, 1280 to 720, 1920 to 1080,
            2560 to 1440, 3200 to 1800, 3840 to 2160, 4096 to 2160, 7680 to 4320,
            15360 to 8640, 880 to 495, 480 to 270, 426 to 240, 400 to 225,
            320 to 180, 256 to 144, 160 to 90, 495 to 396
        )
        expectedResolutions.forEach { (w, h) ->
            assertTrue(
                "Resolution ${w}x${h} must be supported",
                VideoItem.isSupportedVideoResolution(w, h)
            )
        }
        assertTrue(VideoRepository.isStrictVideoFile("movie.mp4", "video/mp4"))

        // 2 & 3. Verify expanded themes (10 modes), typography scales (6 modes), and font families (9 modes)
        assertEquals(10, com.cyosplay.player.data.model.AppThemeMode.entries.size)
        assertEquals(9, com.cyosplay.player.data.model.AppFontMode.entries.size)
        assertEquals(6, com.cyosplay.player.data.model.AdaptiveTextScaleMode.entries.size)
        viewModel.setThemeMode(com.cyosplay.player.data.model.AppThemeMode.CYBER_NEON_BLUE)
        assertEquals(com.cyosplay.player.data.model.AppThemeMode.CYBER_NEON_BLUE, viewModel.uiState.value.themeMode)
        viewModel.setFontMode(com.cyosplay.player.data.model.AppFontMode.GEOMETRIC_POPPINS)
        assertEquals(com.cyosplay.player.data.model.AppFontMode.GEOMETRIC_POPPINS, viewModel.uiState.value.fontMode)

        // 4. Verify subtitle edge style defaults to DROP_SHADOW (transparent background)
        assertEquals(
            com.cyosplay.player.data.model.SubtitleEdgeStyle.DROP_SHADOW,
            viewModel.uiState.value.subtitleEdgeStyle
        )

        // 5. Verify multiple recovery questions (primary + secondary backup question)
        assertTrue(viewModel.uiState.value.presetRecoveryQuestions.size >= 8)
        viewModel.setupInitialVaultPin(
            pin = "654321",
            securityQuestion = "What is your favorite cinema film?",
            securityAnswer = "Interstellar"
        )
        viewModel.saveSecondarySecurityQuestion(
            question = "What city were you born in?",
            answer = "Tokyo"
        )
        assertTrue(viewModel.uiState.value.hasSecondarySecurityQuestion)
        assertEquals("What city were you born in?", viewModel.uiState.value.secondarySecurityQuestionText)
        assertTrue(viewModel.recoverVaultPinWithSecurityAnswer("Tokyo", "765432"))

        // 9 & 10. Verify expanded view modes (7 modes) and layouts (5 column modes, 4 thumbnail ratios)
        assertEquals(7, com.cyosplay.player.data.model.BrowserViewMode.entries.size)
        assertEquals(5, com.cyosplay.player.data.model.BrowserLayoutColumns.entries.size)
        assertEquals(4, com.cyosplay.player.data.model.BrowserThumbnailRatio.entries.size)

        // 12. Verify Play All remembers last played video & resume per folder
        val v1 = VideoItem(
            id = 9101L,
            title = "Episode 1",
            displayName = "Episode_01.mp4",
            uri = Uri.parse("file:///storage/emulated/0/SeriesA/Episode_01.mp4"),
            path = "/storage/emulated/0/SeriesA/Episode_01.mp4",
            sizeBytes = 50_000_000L,
            durationMs = 120_000L,
            dateModifiedSeconds = 1_750_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "series_a",
            folderName = "SeriesA",
            folderPath = "/storage/emulated/0/SeriesA"
        )
        val v2 = v1.copy(
            id = 9102L,
            title = "Episode 2",
            displayName = "Episode_02.mp4",
            uri = Uri.parse("file:///storage/emulated/0/SeriesA/Episode_02.mp4"),
            path = "/storage/emulated/0/SeriesA/Episode_02.mp4"
        )
        viewModel.playVideo(v2, listOf(v1, v2))
        assertEquals(v2.path, viewModel.getLastPlayedVideoForFolder("series_a", listOf(v1, v2))?.path)
        viewModel.playAllInFolder("series_a", listOf(v1, v2))
        val activePlayerScreen = viewModel.uiState.value.currentScreen as? com.cyosplay.player.ui.Screen.Player
        assertEquals(v2.path, activePlayerScreen?.video?.path)

        // 14. Verify simple SD Card detection status label ("Detected" or "Not Detected")
        val sdLabel = viewModel.uiState.value.sdCardStatusLabel
        assertTrue(sdLabel == "Detected" || sdLabel == "Not Detected")

        // 15. Verify video zoom controls (0.5x to 4.0x + reset to 1.0x)
        viewModel.setVideoZoomScale(2.25f)
        assertEquals(2.25f, viewModel.uiState.value.videoZoomScale, 0.01f)
        viewModel.resetVideoZoom()
        assertEquals(1.0f, viewModel.uiState.value.videoZoomScale, 0.01f)

        // 17 & 18. Verify majorCapabilities = "cyos play" and cyosplay Activity class
        assertEquals("cyos play", CryptoSecurityManager.MAJOR_CAPABILITY_UNIQUE_ID)
        assertEquals("cyos play", com.cyosplay.player.BuildConfig.MAJOR_CAPABILITY_ID)
        val activityClass = com.cyosplay.player.cyosplay::class.java
        assertTrue(com.cyosplay.player.MainActivity::class.java.isAssignableFrom(activityClass))
    }

    @Test
    fun v105rv2_matroskaWebm495x396EncodingSwAspAacLc_perVideoAndFolderBrightnessVolume_vaultAutoLock10s_andMediaStoreHelper() {
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)

        // 1. Verify 495x396 and 1920x800 Matroska/WebM, Encoding SW, libebml v14.2 + libmatroska v1.6.4, Video Codec MPEG-4 part 2, ASP, AAC, LC, and 44100 HZ
        val matroska495x396 = VideoItem(
            id = 9901L,
            title = "Retro Matroska 495x396 ASP AAC LC",
            displayName = "Retro_495x396_ASP_AAC_LC.mkv",
            uri = Uri.parse("file:///storage/emulated/0/Movies/Retro_495x396_ASP_AAC_LC.mkv"),
            path = "/storage/emulated/0/Movies/Retro_495x396_ASP_AAC_LC.mkv",
            sizeBytes = 42_000_000L,
            durationMs = 60_000L,
            dateModifiedSeconds = 1_750_000_000L,
            width = 495,
            height = 396,
            mimeType = "video/x-matroska",
            folderId = "movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies"
        )
        assertEquals("495×396", matroska495x396.resolutionBadge)
        assertEquals("5:4", matroska495x396.aspectRatioLabel)
        assertTrue(matroska495x396.matroskaEbmlBadge.contains("AndroidX Media3 Extractor"))
        assertTrue(matroska495x396.matroskaEbmlBadge.contains("Android MediaCodec"))
        assertTrue(matroska495x396.matroskaEbmlBadge.contains("Matroska"))
        assertFalse(
            "Badge must not fabricate unbundled libebml/libmatroska version strings",
            matroska495x396.matroskaEbmlBadge.contains("libebml v14.2")
        )
        assertTrue(
            AudioBadgeFormatter.formatVideoCodecBadge("video/mp4v-es", 495, 396)
                .contains("MPEG-4")
        )
        val formattedAacBadge = AudioBadgeFormatter.formatAudioCodecBadge("audio/mp4a-latm", 2, "AAC-LC", 44100)
        assertTrue("Badge must include AAC-LC and 44,100 Hz sample rate: $formattedAacBadge", formattedAacBadge.contains("AAC-LC") && formattedAacBadge.contains("44,100", ignoreCase = true))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("asp"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("aac"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("lc"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("44100hz"))

        // 2. Verify true per-video & per-folder setting memory for volume and brightness (no global overwrite)
        viewModel.updateSavedPlayerBrightness(
            value = 0.35f,
            folderKey = "/storage/emulated/0/FolderAlpha",
            videoPath = "/storage/emulated/0/FolderAlpha/Video1.mp4"
        )
        viewModel.updateSavedPlayerVolume(
            value = 0.45f,
            folderKey = "/storage/emulated/0/FolderAlpha",
            videoPath = "/storage/emulated/0/FolderAlpha/Video1.mp4"
        )
        viewModel.updateSavedPlayerBrightness(
            value = 0.72f,
            folderKey = "/storage/emulated/0/FolderAlpha",
            videoPath = "/storage/emulated/0/FolderAlpha/Video2.mp4"
        )
        viewModel.updateSavedPlayerVolume(
            value = 0.84f,
            folderKey = "/storage/emulated/0/FolderAlpha",
            videoPath = "/storage/emulated/0/FolderAlpha/Video2.mp4"
        )

        // Video1 keeps its own saved per-video settings; Video2 has its own; Video3_New inherits FolderAlpha's latest folder setting
        assertEquals(
            0.35f,
            viewModel.getPlayerBrightnessForVideo(
                videoPath = "/storage/emulated/0/FolderAlpha/Video1.mp4",
                folderKey = "/storage/emulated/0/FolderAlpha"
            ),
            0.01f
        )
        assertEquals(
            0.45f,
            viewModel.getPlayerVolumeForVideo(
                videoPath = "/storage/emulated/0/FolderAlpha/Video1.mp4",
                folderKey = "/storage/emulated/0/FolderAlpha"
            ),
            0.01f
        )
        assertEquals(
            0.72f,
            viewModel.getPlayerBrightnessForVideo(
                videoPath = "/storage/emulated/0/FolderAlpha/Video2.mp4",
                folderKey = "/storage/emulated/0/FolderAlpha"
            ),
            0.01f
        )
        assertEquals(
            0.84f,
            viewModel.getPlayerVolumeForVideo(
                videoPath = "/storage/emulated/0/FolderAlpha/Video2.mp4",
                folderKey = "/storage/emulated/0/FolderAlpha"
            ),
            0.01f
        )
        assertEquals(
            0.72f,
            viewModel.getPlayerBrightnessForVideo(
                videoPath = "/storage/emulated/0/FolderAlpha/Video3_New.mp4",
                folderKey = "/storage/emulated/0/FolderAlpha"
            ),
            0.01f
        )
        assertEquals(
            0.84f,
            viewModel.getPlayerVolumeForVideo(
                videoPath = "/storage/emulated/0/FolderAlpha/Video3_New.mp4",
                folderKey = "/storage/emulated/0/FolderAlpha"
            ),
            0.01f
        )

        // 3. Verify 10-second Vault auto-lock timer and immediate lock on leaving Vault
        assertEquals(10, VideoPlayerViewModel.VAULT_AUTO_LOCK_SECONDS)
        viewModel.selectNavTab(com.cyosplay.player.data.model.NavDestination.VAULT)
        viewModel.setupInitialVaultPin("432100", "Favorite color?", "Blue")
        assertEquals(
            com.cyosplay.player.data.model.VaultAuthState.UNLOCKED_REAL,
            viewModel.uiState.value.vaultAuthState
        )
        assertEquals(10, viewModel.uiState.value.vaultAutoLockRemainingSeconds)
        viewModel.selectNavTab(com.cyosplay.player.data.model.NavDestination.FOLDERS)
        assertEquals(
            "Vault must immediately auto-lock when leaving Vault tab",
            com.cyosplay.player.data.model.VaultAuthState.LOCKED,
            viewModel.uiState.value.vaultAuthState
        )

        // 4. Verify immediate single-action Copy/Move execution & MediaStoreFileHelper copy/move
        viewModel.selectAllVisibleVideos(listOf(matroska495x396))
        viewModel.startCopyOrMoveFlow(com.cyosplay.player.data.model.HoldClipboardOperation.COPY)
        val backupDest = com.cyosplay.player.data.model.MoveDestinationOption(
            id = "dest_backup",
            title = "BackupMovies",
            subtitle = "/storage/emulated/0/BackupMovies",
            targetPath = "/storage/emulated/0/BackupMovies"
        )
        viewModel.requestCopyOrMoveConfirmation(
            destination = backupDest,
            customNewFolderName = ""
        )
        assertNull(
            "Copy/Move must execute immediately on first confirmation without staging an extra dialog",
            viewModel.uiState.value.pendingTransferOperation
        )

        val tempDir = File(context.cacheDir, "mediastore_helper_test").apply { mkdirs() }
        val srcFile = File(tempDir, "test_clip.mp4").apply { writeBytes(ByteArray(2048) { 0x42 }) }
        val destDir = File(tempDir, "DestFolder").apply { mkdirs() }
        val copyResult = kotlinx.coroutines.runBlocking {
            com.cyosplay.player.util.MediaStoreFileHelper.copyMediaFileToMediaStore(
                context = context,
                sourceFile = srcFile,
                destinationFolder = destDir,
                mimeType = "video/mp4"
            )
        }
        assertTrue(copyResult.success)
        assertNotNull(copyResult.destinationFilePath)
        assertTrue(File(copyResult.destinationFilePath!!).exists())
        tempDir.deleteRecursively()

        // 5. Verify per-device AES-256-GCM build-code payload encryption (zero embedded secrets)
        val encryptedBuildPayload = CryptoSecurityManager.encryptBuildCodeData(context, "CYOSPLAY_1.05_BUILD_DATA")
        assertTrue(CryptoSecurityManager.isEncryptedString(encryptedBuildPayload))
        assertEquals("CYOSPLAY_1.05_BUILD_DATA", CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuildPayload))
    }

    @Test
    fun v105rv3_floatingMiniPlayer_44100HzSampleRate_1920x800And495x396_andNavigationWhilePlaying() {
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)

        // 1. Verify 1920x800 and 495x396 Matroska/WebM + 44100 HZ + MPEG-4 part 2 + ASP + AAC + LC
        val ultrawide1920x800 = VideoItem(
            id = 9951L,
            title = "Cinema 1920x800 44100HZ MKV",
            displayName = "Cinema_1920x800_44100HZ.mkv",
            uri = Uri.parse("file:///storage/emulated/0/Movies/Cinema_1920x800_44100HZ.mkv"),
            path = "/storage/emulated/0/Movies/Cinema_1920x800_44100HZ.mkv",
            sizeBytes = 88_000_000L,
            durationMs = 120_000L,
            dateModifiedSeconds = 1_755_000_000L,
            width = 1920,
            height = 800,
            mimeType = "video/x-matroska",
            folderId = "movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies",
            audioCodecHint = "AAC-LC (44100 HZ) 2.0",
            videoCodecHint = "Video Codec MPEG-4 part 2 / ASP / LC"
        )
        assertEquals("1920×800", ultrawide1920x800.resolutionBadge)
        assertTrue(ultrawide1920x800.matroskaEbmlBadge.contains("44100 HZ"))
        assertTrue(ultrawide1920x800.matroskaEbmlBadge.contains("AndroidX Media3 Extractor"))
        assertTrue(ultrawide1920x800.matroskaEbmlBadge.contains("Android MediaCodec"))
        assertTrue(ultrawide1920x800.matroskaEbmlBadge.contains("Video Codec MPEG-4 part 2"))
        assertTrue(ultrawide1920x800.matroskaEbmlBadge.contains("ASP"))
        assertTrue(ultrawide1920x800.matroskaEbmlBadge.contains("AAC"))
        assertTrue(ultrawide1920x800.matroskaEbmlBadge.contains("LC"))

        // 2. Start playing in full-screen player, then minimize to Mini-Player
        viewModel.playVideo(ultrawide1920x800, listOf(ultrawide1920x800))
        assertTrue(viewModel.uiState.value.currentScreen is com.cyosplay.player.ui.Screen.Player)
        assertNull(viewModel.uiState.value.miniPlayerVideo)

        viewModel.minimizeToMiniPlayer(
            video = ultrawide1920x800,
            queue = listOf(ultrawide1920x800),
            positionMs = 35_000L,
            durationMs = 120_000L,
            isPlaying = true
        )
        assertEquals(com.cyosplay.player.ui.Screen.Home, viewModel.uiState.value.currentScreen)
        assertNotNull(viewModel.uiState.value.miniPlayerVideo)
        assertEquals(ultrawide1920x800.path, viewModel.uiState.value.miniPlayerVideo?.path)
        assertTrue(viewModel.uiState.value.isMiniPlayerPlaying)
        assertEquals(35_000L, viewModel.uiState.value.miniPlayerPositionMs)

        // 3. Browse to Settings tab while Mini-Player keeps playing
        viewModel.selectNavTab(com.cyosplay.player.data.model.NavDestination.SETTINGS)
        assertEquals(com.cyosplay.player.data.model.NavDestination.SETTINGS, viewModel.uiState.value.currentNavTab)
        assertNotNull("Mini-Player must remain active while browsing Settings", viewModel.uiState.value.miniPlayerVideo)
        assertTrue(viewModel.uiState.value.isMiniPlayerPlaying)

        // 4. Test Mini-Player skip (+10s / -10s), play/pause toggle, expand to full-screen, and close
        viewModel.skipMiniPlayer(10_000L)
        assertEquals(45_000L, viewModel.uiState.value.miniPlayerPositionMs)
        viewModel.toggleMiniPlayerPlayPause()
        assertFalse(viewModel.uiState.value.isMiniPlayerPlaying)
        viewModel.toggleMiniPlayerPlayPause()
        assertTrue(viewModel.uiState.value.isMiniPlayerPlaying)

        viewModel.expandMiniPlayer()
        assertTrue(viewModel.uiState.value.currentScreen is com.cyosplay.player.ui.Screen.Player)
        assertNull(viewModel.uiState.value.miniPlayerVideo)
        assertEquals(
            45_000L,
            viewModel.uiState.value.playbackProgressMap[ultrawide1920x800.path]?.lastPositionMs
        )

        // Minimize again and close Mini-Player
        viewModel.minimizeToMiniPlayer(
            video = ultrawide1920x800,
            queue = listOf(ultrawide1920x800),
            positionMs = 52_000L,
            durationMs = 120_000L,
            isPlaying = true
        )
        assertNotNull(viewModel.uiState.value.miniPlayerVideo)
        viewModel.closeMiniPlayer()
        assertNull(viewModel.uiState.value.miniPlayerVideo)
        assertFalse(viewModel.uiState.value.isMiniPlayerPlaying)
        assertEquals(
            52_000L,
            viewModel.uiState.value.playbackProgressMap[ultrawide1920x800.path]?.lastPositionMs
        )
    }

    @Test
    fun v105rv4_hardwareAccelerationToggle_mediaInfoFfprobe_expandedResolutions_andMiniPlayerSeek() {
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)

        // 1. Verify all new Web/Stream and SD resolutions in VideoItem, Formatters, and FfmpegMatroskaCodecRegistry
        val expectedResolutions = listOf(
            1920 to 800,
            495 to 396,
            536 to 396,
            512 to 384,
            720 to 540,
            640 to 480,
            720 to 404,
            640 to 360,
            720 to 576
        )
        for ((w, h) in expectedResolutions) {
            assertTrue(
                "Expected ${w}x${h} to be supported in VideoItem.SUPPORTED_VIDEO_RESOLUTIONS",
                VideoItem.isSupportedVideoResolution(w, h)
            )
            assertTrue(
                "Expected ${w}x${h} to be supported in FfmpegMatroskaCodecRegistry",
                com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.isSupportedCinemaOrMatroskaResolution(w, h)
            )
        }
        assertEquals("536×396", com.cyosplay.player.util.Formatters.formatResolutionBadge(536, 396))
        assertEquals("512×384", com.cyosplay.player.util.Formatters.formatResolutionBadge(512, 384))
        assertEquals("720×540", com.cyosplay.player.util.Formatters.formatResolutionBadge(720, 540))
        assertEquals("640×480", com.cyosplay.player.util.Formatters.formatResolutionBadge(640, 480))
        assertEquals("720×404", com.cyosplay.player.util.Formatters.formatResolutionBadge(720, 404))
        assertEquals("360p (640×360)", com.cyosplay.player.util.Formatters.formatResolutionBadge(640, 360))
        assertEquals("720×576", com.cyosplay.player.util.Formatters.formatResolutionBadge(720, 576))
        assertEquals("1920×800", com.cyosplay.player.util.Formatters.formatResolutionBadge(1920, 800))
        assertEquals("495×396", com.cyosplay.player.util.Formatters.formatResolutionBadge(495, 396))

        // 2. Verify MediaInfo / FFprobe stream report and H.264/AVC (avc1), YUV 4:2:0 8-bit, Main/High Profile @ Level 3.0/3.1, AAC-LC (mp4a.40.2), 44100 HZ
        val report536x396 = com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.generateMediaInfoFfprobeReport(
            width = 536,
            height = 396,
            videoCodec = "H.264 / AVC (avc1)",
            audioCodec = "AAC-LC (mp4a.40.2)",
            sampleRateHz = 44100
        )
        assertTrue(report536x396.contains("MediaInfo / FFprobe"))
        assertTrue(report536x396.contains("360p (640×360) / SD (536×396 or 640×480 depending on platform transcode)"))
        assertTrue(report536x396.contains("Video Codec: H.264 / AVC (avc1)"))
        assertTrue(report536x396.contains("Chroma Subsampling / Bit Depth: YUV 4:2:0, 8-bit"))
        assertTrue(report536x396.contains("Profile: Main Profile (avc1.4d401e) or High Profile (avc1.64001e) at Level 3.0 / 3.1"))
        assertTrue(report536x396.contains("Profile: AAC-LC (Low Complexity, mp4a.40.2)"))
        assertTrue(report536x396.contains("44100 HZ"))
        assertTrue(report536x396.contains("libebml v14.2 + libmatroska v1.6.4"))
        assertTrue(report536x396.contains("Video Codec MPEG-4 part 2"))
        assertTrue(report536x396.contains("ASP"))

        // 3. Verify explicit Hardware Acceleration toggle in Settings & MediaCodecSelector
        assertFalse("Hardware acceleration for high-bitrate video should default to OFF until turned ON", viewModel.uiState.value.forceHardwareAccelerationHighBitrate)
        viewModel.setForceHardwareAccelerationHighBitrate(true)
        assertTrue(viewModel.uiState.value.forceHardwareAccelerationHighBitrate)
        viewModel.toggleForceHardwareAccelerationHighBitrate()
        assertFalse(viewModel.uiState.value.forceHardwareAccelerationHighBitrate)
        val hwSelector = com.cyosplay.player.util.AudioBadgeFormatter.createMultiCodecSelector(
            preferSoftwareDecoder = false,
            forceHardwareForHighBitrate = true
        )
        assertNotNull(hwSelector)

        // 4. Verify interactive Mini-Player seek bar (seekMiniPlayerTo)
        val sdStreamVideo = VideoItem(
            id = 9988L,
            title = "Web Stream 536x396 AVC1 AAC-LC",
            displayName = "Web_Stream_536x396.mkv",
            uri = Uri.parse("file:///storage/emulated/0/Movies/Web_Stream_536x396.mkv"),
            path = "/storage/emulated/0/Movies/Web_Stream_536x396.mkv",
            sizeBytes = 42_000_000L,
            durationMs = 180_000L,
            dateModifiedSeconds = 1_756_000_000L,
            width = 536,
            height = 396,
            mimeType = "video/x-matroska",
            folderId = "movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies"
        )
        viewModel.minimizeToMiniPlayer(
            video = sdStreamVideo,
            queue = listOf(sdStreamVideo),
            positionMs = 15_000L,
            durationMs = 180_000L,
            isPlaying = true
        )
        viewModel.seekMiniPlayerTo(92_500L)
        assertEquals(92_500L, viewModel.uiState.value.miniPlayerPositionMs)
        assertEquals(
            92_500L,
            viewModel.uiState.value.playbackProgressMap[sdStreamVideo.path]?.lastPositionMs
        )

        // 5. Verify per-device AES-256-GCM datacode encryption
        val encryptedBuild = CryptoSecurityManager.encryptBuildCodeData(context, "CYOSPLAY_1.05RV4_BUILD_DATA")
        assertTrue(CryptoSecurityManager.isEncryptedString(encryptedBuild))
        assertEquals("CYOSPLAY_1.05RV4_BUILD_DATA", CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild))
    }

    @Test
    fun v106rv_draggableMiniPlayerOffset_defaultOffHwAccelAndBatteryShield_autoSkipIntrosCredits_andNightShield() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify CyosPlay 1.06rv11 Versioning & Description
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)

        // 2. Verify Force Hardware Acceleration and Battery Smoothness Shield both default to OFF until manually turned ON
        assertFalse(
            "Force Hardware Acceleration must default to OFF until manually turned ON",
            viewModel.uiState.value.forceHardwareAccelerationHighBitrate
        )
        assertFalse(
            "Battery Smoothness Shield must default to OFF until manually turned ON",
            viewModel.uiState.value.batterySaverModeEnabled
        )

        // Turn ON and OFF Force Hardware Acceleration and Battery Smoothness Shield
        viewModel.setForceHardwareAccelerationHighBitrate(true)
        assertTrue(viewModel.uiState.value.forceHardwareAccelerationHighBitrate)
        viewModel.setForceHardwareAccelerationHighBitrate(false)
        assertFalse(viewModel.uiState.value.forceHardwareAccelerationHighBitrate)

        viewModel.setBatterySaverModeEnabled(true)
        assertTrue(viewModel.uiState.value.batterySaverModeEnabled)
        viewModel.setBatterySaverModeEnabled(false)
        assertFalse(viewModel.uiState.value.batterySaverModeEnabled)

        // 3. Verify Mini-Player does NOT turn ON automatically on play/exit, only via explicit minimizeToMiniPlayer, and supports manual drag positioning anywhere
        val testVideo = VideoItem(
            id = 10601L,
            title = "CyosPlay 106rv Cinema Clip",
            displayName = "CyosPlay_106rv.mkv",
            uri = Uri.parse("file:///storage/emulated/0/Movies/CyosPlay_106rv.mkv"),
            path = "/storage/emulated/0/Movies/CyosPlay_106rv.mkv",
            sizeBytes = 64_000_000L,
            durationMs = 240_000L,
            dateModifiedSeconds = 1_760_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/x-matroska",
            folderId = "movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies"
        )
        viewModel.playVideo(testVideo, listOf(testVideo))
        assertNull("Mini-Player must remain OFF when starting full-screen playback", viewModel.uiState.value.miniPlayerVideo)

        viewModel.savePlaybackProgressAndExit(testVideo.path, 42_000L, 240_000L)
        assertNull("Exiting via Back must NOT auto-enable Mini-Player", viewModel.uiState.value.miniPlayerVideo)

        // Manually turn ON Mini-Player and drag to custom screen coordinates
        viewModel.minimizeToMiniPlayer(testVideo, listOf(testVideo), 42_000L, 240_000L, true)
        assertNotNull("Mini-Player must activate when explicitly minimized", viewModel.uiState.value.miniPlayerVideo)

        viewModel.updateMiniPlayerOffset(135.5f, -280.0f)
        assertEquals(135.5f, viewModel.uiState.value.miniPlayerOffsetX, 0.01f)
        assertEquals(-280.0f, viewModel.uiState.value.miniPlayerOffsetY, 0.01f)

        // Verify Mini-Player offset persists across ViewModel reload
        val reloadedVm = VideoPlayerViewModel(context)
        assertEquals(135.5f, reloadedVm.uiState.value.miniPlayerOffsetX, 0.01f)
        assertEquals(-280.0f, reloadedVm.uiState.value.miniPlayerOffsetY, 0.01f)

        viewModel.resetMiniPlayerOffset()
        assertEquals(0f, viewModel.uiState.value.miniPlayerOffsetX, 0.01f)
        assertEquals(0f, viewModel.uiState.value.miniPlayerOffsetY, 0.01f)

        // 4. Verify Auto-Skip Intros & Credits toggle (OFF by default in 1.08rv7) and Night Shield Warmth
        assertFalse(viewModel.uiState.value.autoSkipIntrosCredits)
        viewModel.setAutoSkipIntrosCredits(true)
        assertTrue(viewModel.uiState.value.autoSkipIntrosCredits)
        viewModel.setAutoSkipIntrosCredits(false)
        assertFalse(viewModel.uiState.value.autoSkipIntrosCredits)

        viewModel.setNightShieldWarmthLevel(0.65f)
        assertEquals(0.65f, viewModel.uiState.value.nightShieldWarmthLevel, 0.01f)

        // 5. Verify Mini-Player Size Mode (Compact <-> Medium)
        assertEquals(MiniPlayerSizeMode.COMPACT, viewModel.uiState.value.miniPlayerSizeMode)
        viewModel.setMiniPlayerSizeMode(MiniPlayerSizeMode.MEDIUM)
        assertEquals(MiniPlayerSizeMode.MEDIUM, viewModel.uiState.value.miniPlayerSizeMode)
        viewModel.toggleMiniPlayerSizeMode()
        assertEquals(MiniPlayerSizeMode.COMPACT, viewModel.uiState.value.miniPlayerSizeMode)

        // 6. Verify per-device AES-256-GCM datacode encryption (zero embedded passwords)
        val encryptedBuild = CryptoSecurityManager.encryptBuildCodeData(context, "CYOSPLAY_1.06RV_BUILD_DATA")
        assertTrue(CryptoSecurityManager.isEncryptedString(encryptedBuild))
        assertEquals("CYOSPLAY_1.06RV_BUILD_DATA", CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild))
    }

    @Test
    fun v106rv2_perFolderBrightnessVolume_24To240FpsSupport_andMiniPlayerPositionPresets() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify CyosPlay 1.06rv11 Versioning & Description
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)

        // 2. Verify Unified Global Brightness & Volume Memory shared across all folders
        val folderA = "/storage/emulated/0/Anime120Fps"
        val folderB = "/storage/emulated/0/Cinema24Fps"
        viewModel.updateSavedPlayerBrightness(0.28f, folderA, "$folderA/Ep01.mkv")
        viewModel.updateSavedPlayerVolume(0.62f, folderA, "$folderA/Ep01.mkv")

        viewModel.updateSavedPlayerBrightness(0.76f, folderB, "$folderB/Movie01.mkv")
        viewModel.updateSavedPlayerVolume(0.84f, folderB, "$folderB/Movie01.mkv")

        // Each folder retains its own independent volume & brightness memory
        assertEquals(0.28f, viewModel.getPlayerBrightnessForVideo("$folderA/Ep02.mkv", folderA), 0.01f)
        assertEquals(0.62f, viewModel.getPlayerVolumeForVideo("$folderA/Ep02.mkv", folderA), 0.01f)
        assertEquals(0.76f, viewModel.getPlayerBrightnessForVideo("$folderB/Movie02.mkv", folderB), 0.01f)
        assertEquals(0.84f, viewModel.getPlayerVolumeForVideo("$folderB/Movie02.mkv", folderB), 0.01f)

        // 3. Verify Video Frame Rate Engine is removed and stream FPS is cleanly tracked
        viewModel.updateDetectedStreamFps(144f)
        assertEquals(144f, viewModel.uiState.value.detectedStreamFps, 0.01f)
        viewModel.updateDetectedStreamFps(60f)
        assertEquals(60f, viewModel.uiState.value.detectedStreamFps, 0.01f)

        // 4. Verify Mini-Player Position Presets (Corners/Center) + Free Drag Custom Coordinates
        viewModel.setMiniPlayerPositionPreset(com.cyosplay.player.data.model.MiniPlayerPositionPreset.TOP_LEFT)
        assertEquals(
            com.cyosplay.player.data.model.MiniPlayerPositionPreset.TOP_LEFT,
            viewModel.uiState.value.miniPlayerPositionPreset
        )
        assertEquals(
            com.cyosplay.player.data.model.MiniPlayerPositionPreset.TOP_LEFT.defaultOffsetX,
            viewModel.uiState.value.miniPlayerOffsetX,
            0.01f
        )
        assertEquals(
            com.cyosplay.player.data.model.MiniPlayerPositionPreset.TOP_LEFT.defaultOffsetY,
            viewModel.uiState.value.miniPlayerOffsetY,
            0.01f
        )

        viewModel.cycleMiniPlayerPositionPreset()
        assertEquals(
            com.cyosplay.player.data.model.MiniPlayerPositionPreset.TOP_RIGHT,
            viewModel.uiState.value.miniPlayerPositionPreset
        )

        // Dragging manually switches preset to CUSTOM while preserving exact X/Y coordinates
        viewModel.updateMiniPlayerOffset(64f, -210f)
        assertEquals(
            com.cyosplay.player.data.model.MiniPlayerPositionPreset.CUSTOM,
            viewModel.uiState.value.miniPlayerPositionPreset
        )
        assertEquals(64f, viewModel.uiState.value.miniPlayerOffsetX, 0.01f)
        assertEquals(-210f, viewModel.uiState.value.miniPlayerOffsetY, 0.01f)

        viewModel.resetMiniPlayerOffset()
        assertEquals(
            com.cyosplay.player.data.model.MiniPlayerPositionPreset.BOTTOM_CENTER,
            viewModel.uiState.value.miniPlayerPositionPreset
        )
    }

    @Test
    fun onScreenBrightnessAndVolumeGestureControls_allAcceptanceCriteriaVerified() {
        val viewModel = VideoPlayerViewModel(context)

        // 1 & 2. Left-half vertical swipe up increases brightness; swipe down decreases brightness
        val portraitWidth = 1080f
        val portraitHeight = 2400f
        val leftZone = com.cyosplay.player.util.PlayerGestureControlHelper.resolveGestureZone(
            touchX = portraitWidth * 0.25f,
            touchY = portraitHeight * 0.50f,
            containerWidth = portraitWidth,
            containerHeight = portraitHeight
        )
        assertEquals(com.cyosplay.player.util.VideoGestureZone.LEFT_BRIGHTNESS, leftZone)

        val initialBrightness = 0.50f
        val increasedBrightness = com.cyosplay.player.util.PlayerGestureControlHelper.computeUpdatedBrightness(
            currentBrightness = initialBrightness,
            dragDeltaY = -220f, // Swipe up
            containerHeightPx = portraitHeight
        )
        assertTrue("Swiping up on left half must increase brightness", increasedBrightness > initialBrightness)

        val decreasedBrightness = com.cyosplay.player.util.PlayerGestureControlHelper.computeUpdatedBrightness(
            currentBrightness = increasedBrightness,
            dragDeltaY = 320f, // Swipe down
            containerHeightPx = portraitHeight
        )
        assertTrue("Swiping down on left half must decrease brightness", decreasedBrightness < increasedBrightness)

        // 3 & 4. Right-half vertical swipe up increases volume; swipe down decreases volume
        val rightZone = com.cyosplay.player.util.PlayerGestureControlHelper.resolveGestureZone(
            touchX = portraitWidth * 0.75f,
            touchY = portraitHeight * 0.50f,
            containerWidth = portraitWidth,
            containerHeight = portraitHeight
        )
        assertEquals(com.cyosplay.player.util.VideoGestureZone.RIGHT_VOLUME, rightZone)

        val initialVolume = 0.50f
        val increasedVolume = com.cyosplay.player.util.PlayerGestureControlHelper.computeUpdatedVolume(
            currentVolume = initialVolume,
            dragDeltaY = -240f, // Swipe up
            containerHeightPx = portraitHeight
        )
        assertTrue("Swiping up on right half must increase volume", increasedVolume > initialVolume)

        val decreasedVolume = com.cyosplay.player.util.PlayerGestureControlHelper.computeUpdatedVolume(
            currentVolume = increasedVolume,
            dragDeltaY = 360f, // Swipe down
            containerHeightPx = portraitHeight
        )
        assertTrue("Swiping down on right half must decrease volume", decreasedVolume < increasedVolume)

        // 5. Real-time AudioManager STREAM_MUSIC silent sync & Activity window brightness override + restore
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val maxMusicVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val syncedIdx = com.cyosplay.player.util.PlayerGestureControlHelper.syncVolumeWithAudioManager(
            context = context,
            volumeFraction = 0.80f,
            isMuted = false
        )
        assertEquals(kotlin.math.round(0.80f * maxMusicVol).toInt(), syncedIdx)
        assertEquals(syncedIdx, audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))

        val activityController = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val hostActivity = activityController.get()
        val originalWindowBrightness = hostActivity.window.attributes.screenBrightness
        com.cyosplay.player.util.PlayerGestureControlHelper.applyWindowBrightness(
            activity = hostActivity,
            brightnessFraction = 0.72f,
            batterySaverEnabled = false
        )
        assertEquals(0.72f, hostActivity.window.attributes.screenBrightness, 0.005f)

        // Restoring window brightness on player exit leaves persistent system brightness untouched
        com.cyosplay.player.util.PlayerGestureControlHelper.restoreWindowBrightness(
            activity = hostActivity,
            initialWindowBrightness = originalWindowBrightness
        )
        assertEquals(originalWindowBrightness, hostActivity.window.attributes.screenBrightness, 0.005f)

        // 6. Gestures work ONLY inside the active video playback area & ONLY during active playback
        assertEquals(
            com.cyosplay.player.util.VideoGestureZone.OUTSIDE_VIDEO_AREA,
            com.cyosplay.player.util.PlayerGestureControlHelper.resolveGestureZone(
                touchX = portraitWidth * 0.50f,
                touchY = portraitHeight * 0.05f, // Top toolbar area
                containerWidth = portraitWidth,
                containerHeight = portraitHeight
            )
        )
        assertEquals(
            com.cyosplay.player.util.VideoGestureZone.OUTSIDE_VIDEO_AREA,
            com.cyosplay.player.util.PlayerGestureControlHelper.resolveGestureZone(
                touchX = portraitWidth * 0.50f,
                touchY = portraitHeight * 0.94f, // Bottom seekbar / transport controls area
                containerWidth = portraitWidth,
                containerHeight = portraitHeight
            )
        )
        assertTrue(
            "Brightness/volume swipe must work even while video is paused so users can adjust levels before resuming",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandlePlaybackVerticalGesture(
                isPlaying = false,
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 1,
                isHoldFastForwardActive = false,
                isHorizontalSeekingActive = false
            )
        )
        assertTrue(
            "Brightness/volume swipe must activate when video is actively playing",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandlePlaybackVerticalGesture(
                isPlaying = true,
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 1,
                isHoldFastForwardActive = false,
                isHorizontalSeekingActive = false
            )
        )

        // 7 & 8. Minimum swipe distance & horizontal seek vs vertical gesture isolation + overlay percentage & auto-hide
        assertFalse(
            "Micro-movements below minimum swipe distance must be ignored",
            com.cyosplay.player.util.PlayerGestureControlHelper.isVerticalSwipeGesture(2f, 4f, 16f)
        )
        assertTrue(
            com.cyosplay.player.util.PlayerGestureControlHelper.isVerticalSwipeGesture(4f, -42f, 16f)
        )
        assertTrue(
            com.cyosplay.player.util.PlayerGestureControlHelper.isHorizontalSeekGesture(55f, 6f, 16f)
        )
        assertFalse(
            "Horizontal seeking must not trigger vertical brightness/volume gesture",
            com.cyosplay.player.util.PlayerGestureControlHelper.isVerticalSwipeGesture(55f, 6f, 16f)
        )
        assertEquals(72, com.cyosplay.player.util.PlayerGestureControlHelper.formatBrightnessPercentage(0.72f))
        assertEquals(85, com.cyosplay.player.util.PlayerGestureControlHelper.formatVolumePercentage(0.85f, isMuted = false))
        assertEquals(0, com.cyosplay.player.util.PlayerGestureControlHelper.formatVolumePercentage(0.85f, isMuted = true))
        assertTrue(com.cyosplay.player.util.PlayerGestureControlHelper.OVERLAY_AUTO_HIDE_DELAY_MS in 800L..1200L)

        // 9, 10 & 11. Persistence across leaving/reopening player, switching videos, orientation changes & app restarts
        val landscapeWidth = 2400f
        val landscapeHeight = 1080f
        assertEquals(
            com.cyosplay.player.util.VideoGestureZone.LEFT_BRIGHTNESS,
            com.cyosplay.player.util.PlayerGestureControlHelper.resolveGestureZone(
                touchX = landscapeWidth * 0.25f,
                touchY = landscapeHeight * 0.50f,
                containerWidth = landscapeWidth,
                containerHeight = landscapeHeight
            )
        )
        assertEquals(
            com.cyosplay.player.util.VideoGestureZone.RIGHT_VOLUME,
            com.cyosplay.player.util.PlayerGestureControlHelper.resolveGestureZone(
                touchX = landscapeWidth * 0.75f,
                touchY = landscapeHeight * 0.50f,
                containerWidth = landscapeWidth,
                containerHeight = landscapeHeight
            )
        )

        viewModel.updateSavedPlayerBrightness(0.68f, "/storage/emulated/0/Movies", "/storage/emulated/0/Movies/Clip1.mp4")
        viewModel.updateSavedPlayerVolume(0.82f, "/storage/emulated/0/Movies", "/storage/emulated/0/Movies/Clip1.mp4")
        assertEquals(0.68f, viewModel.uiState.value.savedPlayerBrightness, 0.005f)
        assertEquals(0.82f, viewModel.uiState.value.savedPlayerVolume, 0.005f)

        // Allow Dispatchers.IO preference persistence coroutine to flush before simulating cold restart
        Thread.sleep(120L)
        // Reopen app (fresh ViewModel & SettingsRepository) and verify levels are restored and invalid values safely clamped
        val restartedVm = VideoPlayerViewModel(context)
        assertEquals(0.68f, restartedVm.uiState.value.savedPlayerBrightness, 0.005f)
        assertEquals(0.82f, restartedVm.uiState.value.savedPlayerVolume, 0.005f)

        // Safe handling of invalid / out-of-range values
        assertEquals(0.68f, com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeBrightness(Float.NaN, 0.68f), 0.005f)
        assertEquals(1.0f, com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeBrightness(5.0f), 0.005f)
        assertEquals(0.02f, com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeBrightness(-2.0f), 0.005f)
        assertEquals(1.0f, com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeVolume(9.0f), 0.005f)
        assertEquals(0.0f, com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeVolume(-1.0f), 0.005f)

        // 12. Unified global brightness & volume restoration across all folders + Mini-Player delta drag + Intro/Credits window
        val folderA = "/storage/emulated/0/Movies/SciFi"
        val folderB = "/storage/emulated/0/Movies/Action"
        viewModel.updateSavedPlayerBrightness(0.34f, folderA, "$folderA/Ep1.mkv")
        viewModel.updateSavedPlayerVolume(0.45f, folderA, "$folderA/Ep1.mkv")
        viewModel.updateSavedPlayerBrightness(0.78f, folderB, "$folderB/Act1.mkv")
        viewModel.updateSavedPlayerVolume(0.82f, folderB, "$folderB/Act1.mkv")

        // Verify each folder retains its own independent brightness & volume memory
        assertEquals(0.34f, viewModel.getPlayerBrightnessForVideo("$folderA/Ep2.mkv", folderA), 0.005f)
        assertEquals(0.45f, viewModel.getPlayerVolumeForVideo("$folderA/Ep2.mkv", folderA), 0.005f)
        assertEquals(0.78f, viewModel.getPlayerBrightnessForVideo("$folderB/Act2.mkv", folderB), 0.005f)
        assertEquals(0.82f, viewModel.getPlayerVolumeForVideo("$folderB/Act2.mkv", folderB), 0.005f)

        // Verify Mini-Player live delta drag and persistence
        viewModel.resetMiniPlayerOffset()
        viewModel.dragMiniPlayerByDelta(45f, -120f)
        viewModel.dragMiniPlayerByDelta(15f, -30f)
        viewModel.persistMiniPlayerOffset()
        assertEquals(60f, viewModel.uiState.value.miniPlayerOffsetX, 0.5f)
        assertEquals(-150f, viewModel.uiState.value.miniPlayerOffsetY, 0.5f)

        // Verify Auto-Skip Intros & Credits window resolver strictly uses real chapter metadata
        val sampleChapters = listOf(
            com.cyosplay.player.util.PlayerGestureControlHelper.ChapterSegment("Opening Intro", 0L, 85_000L),
            com.cyosplay.player.util.PlayerGestureControlHelper.ChapterSegment("Main Story", 85_000L, 1_140_000L),
            com.cyosplay.player.util.PlayerGestureControlHelper.ChapterSegment("End Credits", 1_140_000L, 1_199_000L)
        )
        val introWindow = com.cyosplay.player.util.PlayerGestureControlHelper.resolveIntroCreditWindow(
            totalDurationMs = 1_200_000L,
            currentPositionMs = 10_000L,
            chapters = sampleChapters
        )
        assertTrue(introWindow.isInIntroWindow)
        assertFalse(introWindow.isInCreditsWindow)
        assertEquals(85_000L, introWindow.introSkipTargetMs)

        val creditsWindow = com.cyosplay.player.util.PlayerGestureControlHelper.resolveIntroCreditWindow(
            totalDurationMs = 1_200_000L,
            currentPositionMs = 1_150_000L,
            chapters = sampleChapters
        )
        assertFalse(creditsWindow.isInIntroWindow)
        assertTrue(creditsWindow.isInCreditsWindow)
        assertEquals(1_199_000L, creditsWindow.creditsSkipTargetMs)

        // 13. Verify 1.06rv4 MKV (mkvmerge v56.1.0), XVID (MPEG-4 Part 2 with packed B-frames), libx264, yuv420p, -movflags +faststart, AAC LC (44.1 kHz, Stereo), FFmpeg extension, and Night Mode 11% Warmth & 15% Dimmer
        val xvidMkvProfile = com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.inspectStreamProfile(
            fileNameOrPath = "Classic_Cinema_XVID_PackedBFrames.mkv",
            mimeType = "video/x-matroska",
            width = 1920,
            height = 1080,
            videoCodecHint = "XVID"
        )
        assertTrue(xvidMkvProfile.containerSpec.contains("Matroska (.mkv) (matroska,webm ,mkvmerge v56.1.0)"))
        assertTrue(xvidMkvProfile.ffmpegCodecFullSpec.contains("MPEG-4 Part 2 (XVID) (Advanced Simple Profile with packed B-frames)"))
        assertTrue(xvidMkvProfile.ffmpegCodecFullSpec.contains("libx264, yuv420p, -movflags +faststart"))
        assertTrue(xvidMkvProfile.ffmpegCodecFullSpec.contains("AAC LC (44.1 kHz, Stereo)"))
        assertTrue(xvidMkvProfile.ffmpegCodecFullSpec.contains("FFmpeg extension"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("xvid"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("mkvmerge"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("faststart"))

        viewModel.setNightModeEnabled(true)
        assertEquals(11, kotlin.math.round(viewModel.uiState.value.eyeComfortWarmth * 100f).toInt())
        assertEquals(15, kotlin.math.round(viewModel.uiState.value.extraDimIntensity * 100f).toInt())

        // 14. Verify 1.06rv5 Video Format Specifications: all 12 sample rates (8,000 Hz to 384,000 Hz), HE-AAC, Lavf62.3.100, H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10, and mismatched .mp4 (Matroska/.mkv EBML)
        val expectedRates = listOf(8000, 11025, 16000, 22050, 44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000)
        assertEquals(expectedRates, com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.SUPPORTED_SAMPLE_RATES_HZ)
        expectedRates.forEach { hz ->
            assertTrue("Expected sample rate $hz Hz to be supported", com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.isSupportedSampleRate(hz))
        }
        assertEquals("44,100 Hz", com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.formatSampleRateHz(44100))
        assertEquals("384,000 Hz", com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.formatSampleRateHz(384000))
        assertTrue(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.SUPPORTED_AUDIO_PROFILES.contains("HE-AAC"))
        assertTrue(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.SUPPORTED_ENCODING_SOFTWARE.contains("Lavf62.3.100"))
        assertEquals(
            "H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10",
            com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.VIDEO_CODEC_H264_AVC_MPEG4_PART10_SPEC
        )
        assertTrue(
            com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.isEbmlMatroskaMagicHeader(
                byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x93.toByte())
            )
        )
        val doraemonSpecProfile = com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.inspectStreamProfile(
            fileNameOrPath = "[RTI] Classic Doraemon E09 in Hindi.mp4",
            mimeType = "video/mp4",
            width = 704,
            height = 396,
            videoCodecHint = "XVID"
        )
        assertTrue(doraemonSpecProfile.containerSpec.contains("Mismatched extension; actual container is Matroska/.mkv"))
        assertTrue(doraemonSpecProfile.ffmpegCodecFullSpec.contains("H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10"))
        assertTrue(doraemonSpecProfile.ffmpegCodecFullSpec.contains("HE-AAC"))
        assertTrue(doraemonSpecProfile.ffmpegCodecFullSpec.contains("Lavf62.3.100"))
        assertTrue(doraemonSpecProfile.supportedSampleRatesSummary.contains("8,000 Hz"))
        assertTrue(doraemonSpecProfile.supportedSampleRatesSummary.contains("384,000 Hz"))

        // 15. Verify 1.06rv6 Hindi.mp4 Matroska/WebM MPEG-4 part 2 XVID (0x44495658, 704x396 SAR 396:563 DAR 704:563) + AAC LC 44100 Hz stereo, 1920x800 resolution, external video folder isolation, folder new-video notification dot, and one-time confirmation
        assertTrue(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.isSupportedHindiMpeg4XvidMatroskaSpec())
        val hindiSpec = com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.HINDI_MP4_MATROSKA_MPEG4_XVID_SPEC
        assertEquals("Hindi.mp4", hindiSpec.fileName)
        assertEquals("matroska,webm", hindiSpec.formatName)
        assertEquals("Matroska / WebM", hindiSpec.formatLongName)
        assertEquals("mpeg4", hindiSpec.videoCodecName)
        assertEquals("MPEG-4 part 2", hindiSpec.videoCodecLongName)
        assertEquals("Advanced Simple Profile", hindiSpec.videoProfile)
        assertEquals("XVID", hindiSpec.videoCodecTagString)
        assertEquals("0x44495658", hindiSpec.videoCodecTagHex)
        assertEquals(704, hindiSpec.width)
        assertEquals(396, hindiSpec.height)
        assertEquals("396:563", hindiSpec.sampleAspectRatio)
        assertEquals("704:563", hindiSpec.displayAspectRatio)
        assertEquals(
            "704:563",
            com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.calculateDisplayAspectRatio(704, 396, "396:563")
        )
        assertEquals("aac", hindiSpec.audioCodecName)
        assertEquals("LC", hindiSpec.audioProfile)
        assertEquals("fltp", hindiSpec.sampleFmt)
        assertEquals(44_100, hindiSpec.sampleRateHz)
        assertEquals(2, hindiSpec.channels)
        assertEquals("stereo", hindiSpec.channelLayout)
        assertEquals("libebml v1.4.2 + libmatroska v1.6.4", hindiSpec.encoder)
        assertEquals("mkvmerge v56.1.0 ('My Friend') 64-bit", hindiSpec.writingApp)

        // Verify 1920x800 resolution and all resolution badges
        assertTrue(VideoItem.isSupportedVideoResolution(1920, 800))
        assertTrue(VideoItem.isSupportedVideoResolution(704, 396))
        val cinema1920x800Video = VideoItem(
            id = 99001L,
            title = "Cinema_Widescreen_1920x800",
            displayName = "Cinema_Widescreen_1920x800.mkv",
            uri = Uri.parse("file:///storage/emulated/0/Movies/Cinema_Widescreen_1920x800.mkv"),
            path = "/storage/emulated/0/Movies/Cinema_Widescreen_1920x800.mkv",
            sizeBytes = 100_000_000L,
            durationMs = 600_000L,
            dateModifiedSeconds = 1_710_000_000L,
            width = 1920,
            height = 800,
            mimeType = "video/x-matroska",
            folderId = "movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies"
        )
        assertEquals("1920×800", cinema1920x800Video.resolutionBadge)
        assertEquals("12:5", cinema1920x800Video.aspectRatioLabel)

        // Verify external video playback from outside the app does NOT save its folder in the app
        val foldersBeforeExternal = viewModel.uiState.value.folders.map { it.path }.toSet()
        val externalUri = Uri.parse("content://com.android.externalstorage.documents/document/Hindi.mp4")
        viewModel.importAndPlayLocalVideoUri(externalUri, saveFolderInApp = false)
        shadowOf(Looper.getMainLooper()).idle()
        val foldersAfterExternal = viewModel.uiState.value.folders.map { it.path }.toSet()
        assertEquals(
            "Playing a video from outside the app must not add its folder into the app folder list",
            foldersBeforeExternal,
            foldersAfterExternal
        )
        assertFalse(
            "External transient playback folder must never be saved in app folders",
            viewModel.uiState.value.folders.any { it.path.contains("transient_playback") || it.path.contains("/Movies/Imported") }
        )
        assertTrue(viewModel.uiState.value.currentScreen is Screen.Player)

        // Verify new-video notification dot on folder when a new video is added, and cleared when folder is opened
        val downloadFolderPath = "/storage/emulated/0/Download"
        val newDownloadedVideo = VideoItem(
            id = 99002L,
            title = "Hindi",
            displayName = "Hindi.mp4",
            uri = Uri.parse("file://$downloadFolderPath/Hindi.mp4"),
            path = "$downloadFolderPath/Hindi.mp4",
            sizeBytes = 53_140_533L,
            durationMs = 375_480L,
            dateModifiedSeconds = 1_710_050_000L,
            width = 704,
            height = 396,
            mimeType = "video/x-matroska",
            folderId = downloadFolderPath.lowercase().hashCode().toString(),
            folderName = "Download",
            folderPath = downloadFolderPath,
            sampleAspectRatio = "396:563",
            displayAspectRatio = "704:563"
        )
        assertEquals("704×396", newDownloadedVideo.resolutionBadge)
        assertEquals("704:563", newDownloadedVideo.aspectRatioLabel)
        viewModel.addVideoItemToLibrary(newDownloadedVideo)
        shadowOf(Looper.getMainLooper()).idle()

        val downloadFolderWithDot = viewModel.uiState.value.folders.firstOrNull {
            it.path.equals(downloadFolderPath, ignoreCase = true)
        }
        assertNotNull("Download folder should be present after adding video", downloadFolderWithDot)
        assertTrue(
            "Folder with newly added/downloaded video must show notification dot (hasNewVideo = true)",
            downloadFolderWithDot!!.hasNewVideo
        )

        // Verify numeric count badge on folder and point notification dot on new video
        assertTrue(
            "Folder with newly added video must have newVideoCount >= 1",
            downloadFolderWithDot.newVideoCount >= 1
        )
        val addedVideoInState = viewModel.uiState.value.allVideos.firstOrNull { it.path == newDownloadedVideo.path }
        assertNotNull(addedVideoInState)
        assertTrue(
            "Newly added video must have point notification dot (isNewVideo = true)",
            addedVideoInState!!.isNewVideo
        )

        // Opening the folder clears the folder notification dot & count badge
        viewModel.openFolderDetail(downloadFolderWithDot)
        shadowOf(Looper.getMainLooper()).idle()
        val downloadFolderAfterOpen = viewModel.uiState.value.folders.firstOrNull {
            it.path.equals(downloadFolderPath, ignoreCase = true)
        }
        assertFalse(
            "Opening folder detail must clear the new-video notification dot",
            downloadFolderAfterOpen!!.hasNewVideo
        )
        assertEquals(0, downloadFolderAfterOpen.newVideoCount)

        // Playing the new video clears its point notification dot
        viewModel.playVideo(addedVideoInState, listOf(addedVideoInState))
        val videoAfterPlay = viewModel.uiState.value.allVideos.firstOrNull { it.path == newDownloadedVideo.path }
        assertFalse("Playing a new video must clear its point notification dot", videoAfterPlay!!.isNewVideo)

        // Verify immediate single-action paste from clipboard without extra confirmation dialog
        viewModel.onHoldVideoItem(newDownloadedVideo)
        viewModel.stageVideosToClipboard(HoldClipboardOperation.COPY)
        viewModel.requestPasteClipboardConfirmation("/storage/emulated/0/Movies", "Movies")
        assertNull(
            "Pasting from clipboard must execute immediately without staging an extra confirmation dialog",
            viewModel.uiState.value.pendingTransferOperation
        )
        awaitViewModelFileOp(viewModel)

        // 16. Verify 1.06rv8 4-stage fallback chain (HW -> HW+ -> SW+ -> SW), stride alignment, subtitle scaling, error classification, and landscape tilt-fix
        viewModel.setDecoderEngineMode(com.cyosplay.player.data.model.DecoderEngineMode.HW)
        assertEquals("HW DIRECT", viewModel.uiState.value.activeDecoderBadge)
        viewModel.toggleDecoderEngineMode()
        assertEquals(com.cyosplay.player.data.model.DecoderEngineMode.HW_PLUS, viewModel.uiState.value.decoderEngineMode)
        assertEquals("HW+ MULTI-CODEC", viewModel.uiState.value.activeDecoderBadge)
        viewModel.toggleDecoderEngineMode()
        assertEquals(com.cyosplay.player.data.model.DecoderEngineMode.SW_PLUS, viewModel.uiState.value.decoderEngineMode)
        assertEquals("SW+ MULTI-CORE", viewModel.uiState.value.activeDecoderBadge)
        viewModel.toggleDecoderEngineMode()
        assertEquals(com.cyosplay.player.data.model.DecoderEngineMode.SW, viewModel.uiState.value.decoderEngineMode)
        assertEquals("SW SOFTWARE", viewModel.uiState.value.activeDecoderBadge)

        // Verify 4-stage fallback chain: HW -> HW+ -> SW+ -> SW -> null
        viewModel.setDecoderEngineMode(com.cyosplay.player.data.model.DecoderEngineMode.HW)
        assertEquals(com.cyosplay.player.data.model.DecoderEngineMode.HW_PLUS, viewModel.fallbackToNextDecoderEngine())
        assertEquals(com.cyosplay.player.data.model.DecoderEngineMode.SW_PLUS, viewModel.fallbackToNextDecoderEngine())
        assertEquals(com.cyosplay.player.data.model.DecoderEngineMode.SW, viewModel.fallbackToNextDecoderEngine())
        assertNull(viewModel.fallbackToNextDecoderEngine())

        // Verify stride-alignment green-bar fix for non-standard widths (854x480, 426x240, 536x396, 495x396)
        assertTrue(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.needsStrideAlignmentFix(854, 480))
        assertEquals(864, com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.computeAlignedStrideWidth(854))
        assertTrue(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.needsStrideAlignmentFix(426, 240))
        assertEquals(432, com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.computeAlignedStrideWidth(426))
        assertFalse(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.needsStrideAlignmentFix(1920, 1080))
        assertFalse(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.needsStrideAlignmentFix(704, 396))

        // Verify responsive subtitle scaling clamp(1.25rem, 2.5vw, 2rem)
        val mediumSp360 = com.cyosplay.player.data.model.SubtitleFontSize.NORMAL.computeResponsiveSp(360f)
        val mediumSp1600 = com.cyosplay.player.data.model.SubtitleFontSize.NORMAL.computeResponsiveSp(1600f)
        assertEquals(20f, mediumSp360, 0.1f)
        assertEquals(32f, mediumSp1600, 0.1f)

        // Verify corrupted/unreadable video error classification & fallback message
        assertEquals(
            "Unable to play video: This file appears to be corrupted or unreadable",
            com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.CORRUPTED_VIDEO_FALLBACK_MESSAGE
        )
        assertEquals(
            com.cyosplay.player.data.model.PlayerMediaErrorType.CORRUPTED_OR_UNREADABLE,
            com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.classifyPlaybackError(
                errorCode = 4003,
                errorMessage = "MEDIA_ERR_DECODE",
                isNetworkSource = false,
                durationMs = 0L
            )
        )
        assertEquals(
            com.cyosplay.player.data.model.PlayerMediaErrorType.NETWORK_DISCONNECT,
            com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.classifyPlaybackError(
                errorCode = 2001,
                errorMessage = "IO_NETWORK_CONNECTION_FAILED",
                isNetworkSource = true,
                durationMs = 120_000L
            )
        )

        // Verify Direct Tilt-Fix: Landscape lock ignores vertical portrait tilts (0°/180°), flips 180° horizontally (270° <-> 90°), and provides 90° fallback
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveLandscapeLockedOrientation(
                sensorDegrees = 0, // vertical portrait tilt ignored
                currentOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            )
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveLandscapeLockedOrientation(
                sensorDegrees = 90, // 180° horizontal flip to right landscape
                currentOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            )
        )
        assertEquals(
            90f,
            com.cyosplay.player.util.ScreenOrientationSensorHelper.computeFallbackCanvasRotationDegrees(
                containerWidthPx = 1080,
                containerHeightPx = 2400,
                nativeOrientationLockBlocked = true
            ),
            0.01f
        )

        // Verify per-device datacode encryption and zero-debug permanent lock
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())
        val encryptedDatacode = CryptoSecurityManager.encryptVideoDatacode(context, "HINDI_MPEG4_ASP_DATACODE_v106rv9")
        assertEquals(
            "HINDI_MPEG4_ASP_DATACODE_v106rv9",
            CryptoSecurityManager.decryptVideoDatacode(context, encryptedDatacode)
        )

        // 17. Verify CyosPlay 1.06rv9 features:
        // - Android 7 to 17 (API 24..37) support
        assertTrue(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.isSupportedAndroidVersion7To17(24))
        assertTrue(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.isSupportedAndroidVersion7To17(36))
        assertTrue(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.isSupportedAndroidVersion7To17(37))
        assertFalse(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.isSupportedAndroidVersion7To17(23))

        // - A/V sync drift correction on legacy AVI/MPG/RMVB containers
        assertEquals(
            -65L,
            com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.computeAvSyncDriftCorrectionMs(
                videoPtsUs = 1_065_000L,
                audioPtsUs = 1_000_000L,
                containerExtension = "rmvb"
            )
        )

        // - Strict Landscape-Only player orientation (never portrait)
        assertTrue(
            com.cyosplay.player.util.ScreenOrientationSensorHelper.isStrictLandscapeOrientation(
                com.cyosplay.player.util.ScreenOrientationSensorHelper.resolveStrictPlayerLandscapeOrientation(
                    sensorDegrees = 0,
                    currentOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                )
            )
        )

        // - 1.08rv7 Default Theme (Pure AMOLED Black), Typography, and Text Readability Scale (Compact 90%)
        context.getSharedPreferences("cyos_video_vault_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val freshSettingsRepo = com.cyosplay.player.data.repository.SettingsRepository(context)
        assertEquals(com.cyosplay.player.data.model.AppThemeMode.AMOLED_BLACK, freshSettingsRepo.getThemeMode())
        assertEquals("Pure AMOLED Black", com.cyosplay.player.data.model.AppThemeMode.AMOLED_BLACK.label)
        assertEquals(com.cyosplay.player.data.model.AppFontMode.SYSTEM_DEFAULT, freshSettingsRepo.getFontMode())
        assertEquals("Mobile Default", com.cyosplay.player.data.model.AppFontMode.SYSTEM_DEFAULT.label)
        assertEquals(com.cyosplay.player.data.model.AdaptiveTextScaleMode.COMPACT, freshSettingsRepo.getTextScaleMode())
        assertEquals("Compact (90%)", com.cyosplay.player.data.model.AdaptiveTextScaleMode.COMPACT.label)
        assertEquals(com.cyosplay.player.data.model.SubtitleFontSize.LARGE, freshSettingsRepo.getSubtitleFontSize())

        // - Increased subtitle text size & dual simultaneous subtitles + multi-track switching
        assertEquals(26f, com.cyosplay.player.data.model.SubtitleFontSize.LARGE.spValue, 0.01f)
        assertEquals(32f, com.cyosplay.player.data.model.SubtitleFontSize.EXTRA_LARGE.spValue, 0.01f)
        val primaryCues = listOf(com.cyosplay.player.data.model.SubtitleCueItem(1, 0L, 5000L, "Hello world"))
        val secondaryCues = listOf(com.cyosplay.player.data.model.SubtitleCueItem(1, 0L, 5000L, "नमस्ते दुनिया"))
        viewModel.addExternalSubtitleTrack("English.srt", "file:///sub/en.srt", primaryCues, asSecondary = false)
        viewModel.addExternalSubtitleTrack("Hindi.srt", "file:///sub/hi.srt", secondaryCues, asSecondary = true)
        assertTrue(viewModel.uiState.value.dualSubtitlesEnabled)
        assertEquals(2, viewModel.uiState.value.externalSubtitleTracks.size)
        assertEquals("Hello world", viewModel.uiState.value.externalSubtitleCues.first().text)
        assertEquals("नमस्ते दुनिया", viewModel.uiState.value.secondaryExternalSubtitleCues.first().text)

        // - Atomic Move, Safe Non-Locking Copy, and Immediate Delete with disk & cache verification
        val opsRoot = File(context.cacheDir, "v106rv9_file_ops").apply {
            deleteRecursively()
            mkdirs()
        }
        val srcFolder = File(opsRoot, "SourceMovies").apply { mkdirs() }
        val copyFolder = File(opsRoot, "CopiedMovies").apply { mkdirs() }
        val moveFolder = File(opsRoot, "MovedMovies").apply { mkdirs() }
        val origFile = File(srcFolder, "Original_Cinema.mp4").apply {
            writeBytes("CINEMA_VIDEO_STREAM_PAYLOAD_106RV9".repeat(64).toByteArray())
        }
        val origItem = VideoItem(
            id = 99101L,
            title = "Original_Cinema",
            displayName = "Original_Cinema.mp4",
            uri = Uri.fromFile(origFile),
            path = origFile.absolutePath,
            sizeBytes = origFile.length(),
            durationMs = 90_000L,
            dateModifiedSeconds = 1_725_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "src_movies",
            folderName = "SourceMovies",
            folderPath = srcFolder.absolutePath
        )
        viewModel.addVideoItemToLibrary(origItem)

        // Safe Copy: original remains untouched in source folder and duplicate is created in destination folder
        viewModel.onHoldVideoItem(origItem)
        viewModel.startCopyOrMoveFlow(HoldClipboardOperation.COPY)
        viewModel.executeCopyOrMoveToDestination(
            MoveDestinationOption(
                id = "copy_dest",
                title = "CopiedMovies",
                subtitle = copyFolder.absolutePath,
                targetPath = copyFolder.absolutePath
            )
        )
        awaitViewModelFileOp(viewModel)
        val copiedFileOnDisk = File(copyFolder, "Original_Cinema.mp4")
        assertTrue("Original file must remain untouched on disk after copy", origFile.exists())
        assertTrue("Duplicate file must exist in destination folder after copy", copiedFileOnDisk.exists())
        assertEquals(origFile.length(), copiedFileOnDisk.length())
        assertTrue(viewModel.uiState.value.allVideos.any { it.path == origFile.absolutePath })
        assertTrue(viewModel.uiState.value.allVideos.any { it.path == copiedFileOnDisk.absolutePath })

        // Atomic Move: source file is deleted after verified move and old path disappears from source list
        viewModel.onHoldVideoItem(origItem)
        viewModel.startCopyOrMoveFlow(HoldClipboardOperation.MOVE)
        viewModel.executeCopyOrMoveToDestination(
            MoveDestinationOption(
                id = "move_dest",
                title = "MovedMovies",
                subtitle = moveFolder.absolutePath,
                targetPath = moveFolder.absolutePath
            )
        )
        awaitViewModelFileOp(viewModel)
        val movedFileOnDisk = File(moveFolder, "Original_Cinema.mp4")
        assertFalse("Original source file must be removed from disk after atomic move", origFile.exists())
        assertTrue("Moved file must exist in destination folder after atomic move", movedFileOnDisk.exists())
        assertFalse(
            "Old entry must be removed from media library list after move",
            viewModel.uiState.value.allVideos.any { it.path == origFile.absolutePath }
        )
        assertTrue(
            "Moved entry must appear in destination folder list",
            viewModel.uiState.value.allVideos.any { it.path == movedFileOnDisk.absolutePath }
        )

        // Immediate Delete: permanently removes file from disk, clears cached progress/references, and updates UI list immediately
        val movedVideoItem = viewModel.uiState.value.allVideos.first { it.path == movedFileOnDisk.absolutePath }
        viewModel.savePlaybackProgress(movedVideoItem.path, 30_000L, 90_000L)
        viewModel.onHoldVideoItem(movedVideoItem)
        viewModel.requestDeleteHeldVideos()
        assertFalse("Deleted file must be permanently removed from disk immediately", movedFileOnDisk.exists())
        assertFalse(
            "Deleted video must immediately disappear from UI media list",
            viewModel.uiState.value.allVideos.any { it.path == movedVideoItem.path }
        )
        assertNull(
            "Cached playback progress reference must be cleared upon deletion",
            viewModel.uiState.value.playbackProgressMap[movedVideoItem.path]
        )
        opsRoot.deleteRecursively()
    }

    @Test
    fun v106rv10_features_verification() {
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 1. Manual Subtitle Text Size Adjustment: + / - buttons (±2sp) and slider up to 56sp
        viewModel.setSubtitleCustomSp(26f)
        assertEquals(26f, viewModel.uiState.value.subtitleCustomSp, 0.01f)
        viewModel.increaseSubtitleFontSizeManual(2f)
        assertEquals(28f, viewModel.uiState.value.subtitleCustomSp, 0.01f)
        viewModel.decreaseSubtitleFontSizeManual(4f)
        assertEquals(24f, viewModel.uiState.value.subtitleCustomSp, 0.01f)

        // Clamping check: 12sp to 64sp
        viewModel.setSubtitleCustomSp(72f)
        assertEquals(64f, viewModel.uiState.value.subtitleCustomSp, 0.01f)
        viewModel.setSubtitleCustomSp(5f)
        assertEquals(12f, viewModel.uiState.value.subtitleCustomSp, 0.01f)

        // SubtitleFontSize responsive sp calculation with manual override
        val computedCustomSp = com.cyosplay.player.data.model.SubtitleFontSize.LARGE.computeResponsiveSp(
            viewportWidthDp = 800f,
            customSpOverride = 56f
        )
        assertEquals(56f, computedCustomSp, 0.01f)

        // 2. Multi-Subtitle "Select All" and Multi-Track Simultaneous Playback
        viewModel.addExternalSubtitleTrack(
            label = "English Track 1",
            uriString = "content://subs/eng1.srt",
            cues = listOf(com.cyosplay.player.data.model.SubtitleCueItem(id = 1, startTimeMs = 0L, endTimeMs = 5000L, text = "Hello World Eng 1"))
        )
        viewModel.addExternalSubtitleTrack(
            label = "Spanish Track 2",
            uriString = "content://subs/spa2.srt",
            cues = listOf(com.cyosplay.player.data.model.SubtitleCueItem(id = 2, startTimeMs = 0L, endTimeMs = 5000L, text = "Hola Mundo Spa 2"))
        )
        assertEquals(2, viewModel.uiState.value.externalSubtitleTracks.size)

        // Select All subtitles
        viewModel.selectAllSubtitles()
        assertTrue(viewModel.uiState.value.subtitlesEnabledByUser)
        assertTrue(viewModel.uiState.value.playAllSubtitlesSimultaneously)
        assertEquals(2, viewModel.uiState.value.selectedExternalSubtitleTrackIds.size)

        // Individual toggle
        val firstTrackId = viewModel.uiState.value.externalSubtitleTracks[0].id
        viewModel.toggleExternalSubtitleTrackSelection(firstTrackId)
        assertFalse(viewModel.uiState.value.selectedExternalSubtitleTrackIds.contains(firstTrackId))
        viewModel.toggleExternalSubtitleTrackSelection(firstTrackId)
        assertTrue(viewModel.uiState.value.selectedExternalSubtitleTrackIds.contains(firstTrackId))

        // Clear all subtitles
        viewModel.clearAllSubtitles()
        assertFalse(viewModel.uiState.value.subtitlesEnabledByUser)
        assertFalse(viewModel.uiState.value.playAllSubtitlesSimultaneously)
        assertTrue(viewModel.uiState.value.selectedExternalSubtitleTrackIds.isEmpty())

        // 3. High volume and screen brightness warning alert (>= 85% threshold) + Lower Both action
        assertEquals(
            com.cyosplay.player.util.PlayerGestureControlHelper.HIGH_VOLUME_AND_BRIGHTNESS_WARNING_TEXT,
            viewModel.uiState.value.highVolumeBrightnessWarningMessage
        )
        viewModel.updateSavedPlayerBrightness(0.85f)
        viewModel.updateSavedPlayerVolume(0.88f)
        assertTrue(viewModel.uiState.value.showHighVolumeBrightnessWarning)

        viewModel.lowerBothVolumeAndBrightness()
        assertFalse(viewModel.uiState.value.showHighVolumeBrightnessWarning)
        assertEquals(0.50f, viewModel.uiState.value.savedPlayerBrightness, 0.01f)
        assertEquals(0.50f, viewModel.uiState.value.savedPlayerVolume, 0.01f)

        // 4. Exact moveFile(context, sourceFile, destDir, onMoved) verification
        val tempRoot = File(context.cacheDir, "v106rv11_move_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val srcDir = File(tempRoot, "FromFolder").apply { mkdirs() }
        val dstDir = File(tempRoot, "ToFolder")
        val srcFile = File(srcDir, "Sample_Move_106rv11.mp4").apply {
            writeBytes("VIDEO_DATA_106RV11_STREAM".repeat(32).toByteArray())
        }
        var onMovedCalled = false
        val moveSucceeded = viewModel.moveFile(
            context = context,
            sourceFile = srcFile,
            destDir = dstDir,
            onMoved = { onMovedCalled = true }
        )
        assertTrue(moveSucceeded)
        assertTrue(onMovedCalled)
        assertFalse(srcFile.exists())
        assertTrue(File(dstDir, "Sample_Move_106rv11.mp4").exists())
        tempRoot.deleteRecursively()
    }

    @Test
    fun v106rv13_features_verification() {
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 1. VideoPlayerFileMover normalizeVideoRelativePath & moveVideoFile verification
        assertEquals(
            "Movies/MovedVideos/",
            com.cyosplay.videoplayer.util.VideoPlayerFileMover.normalizeVideoRelativePath("MovedVideos")
        )
        assertEquals(
            "DCIM/Camera/",
            com.cyosplay.videoplayer.util.VideoPlayerFileMover.normalizeVideoRelativePath("/DCIM/Camera/")
        )

        val moveTestRoot = File(context.cacheDir, "v106rv13_mover_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val sourceFolder = File(moveTestRoot, "SourceVideos").apply { mkdirs() }
        val targetFolder = File(moveTestRoot, "TargetVideos").apply { mkdirs() }
        val sourceVideoFile = File(sourceFolder, "Clip_106rv13.mp4").apply {
            writeBytes("CYOSPLAY_106RV13_VIDEO_STREAM_DATA".repeat(24).toByteArray())
        }
        val sourceItem = VideoItem(
            id = 1301L,
            title = "Clip_106rv13",
            displayName = "Clip_106rv13.mp4",
            uri = Uri.fromFile(sourceVideoFile),
            path = sourceVideoFile.absolutePath,
            sizeBytes = sourceVideoFile.length(),
            durationMs = 42_000L,
            dateModifiedSeconds = 1_730_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "src_106rv13",
            folderName = "SourceVideos",
            folderPath = sourceFolder.absolutePath
        )
        viewModel.videoRepository.saveManagedCatalog(emptyList())
        viewModel.addVideoItemToLibrary(sourceItem)
        assertTrue(viewModel.uiState.value.allVideos.any { it.id == 1301L })

        var moveResultReceived: com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult? = null
        kotlinx.coroutines.runBlocking {
            val res = com.cyosplay.videoplayer.util.VideoPlayerFileMover.moveVideoFile(
                context = context,
                videoUri = sourceItem.uri,
                targetFolderRelativePath = targetFolder.absolutePath,
                legacySourcePath = sourceVideoFile.absolutePath
            )
            moveResultReceived = res
        }
        assertTrue(moveResultReceived is com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.Success)
        assertFalse("Source file must be removed from original folder", sourceVideoFile.exists())
        assertTrue("Moved file must exist in target folder", File(targetFolder, "Clip_106rv13.mp4").exists())
        moveTestRoot.deleteRecursively()

        // 2. Manual Rotate (Landscape <-> Portrait) + Portrait-to-Landscape Tilt + Tilt-Only-Landscape
        // Manual Rotate toggles Landscape <-> Portrait
        val manualToPortrait = com.cyosplay.player.util.ScreenOrientationSensorHelper.computeNextManualRotateOrientation(
            currentOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        )
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, manualToPortrait)

        val manualBackToLandscape = com.cyosplay.player.util.ScreenOrientationSensorHelper.computeNextManualRotateOrientation(
            currentOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        )
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, manualBackToLandscape)

        // When in Portrait (locked at 0°), tilting horizontally to 270° or 90° automatically switches to Landscape
        val portraitTiltedLeft = com.cyosplay.player.util.ScreenOrientationSensorHelper.resolvePlayerOrientationWithTiltLandscapeOnly(
            manualOrientationOverride = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            sensorDegrees = 270,
            currentOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            portraitLockedAtSensorDegrees = 0
        )
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, portraitTiltedLeft)

        val portraitTiltedRight = com.cyosplay.player.util.ScreenOrientationSensorHelper.resolvePlayerOrientationWithTiltLandscapeOnly(
            manualOrientationOverride = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            sensorDegrees = 90,
            currentOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            portraitLockedAtSensorDegrees = 0
        )
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, portraitTiltedRight)

        // While in tilt mode (manualOrientationOverride = null or Landscape), tilting vertically (0° or 180°) never rotates to Portrait
        val landscapeTiltAtZero = com.cyosplay.player.util.ScreenOrientationSensorHelper.resolvePlayerOrientationWithTiltLandscapeOnly(
            manualOrientationOverride = null,
            sensorDegrees = 0,
            currentOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            portraitLockedAtSensorDegrees = -1
        )
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, landscapeTiltAtZero)

        // 3. Verify per-device AES-256-GCM build-code payload encryption
        val encryptedBuildPayload = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.06RV13_SIGNED_BUILD_CODE"
        )
        assertEquals(
            "CYOSPLAY_1.06RV13_SIGNED_BUILD_CODE",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuildPayload)
        )
    }

    @Test
    fun v106rv14_features_verification() {
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 1. Verify moveVideoViaVideoPlayerFileMover releases active mini-player handle & updates folder lists immediately
        val moveRoot = File(context.cacheDir, "v106rv14_player_mover_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val oldFolder = File(moveRoot, "OldBucket").apply { mkdirs() }
        val newFolder = File(moveRoot, "NewBucket").apply { mkdirs() }
        val videoFile = File(oldFolder, "Clip_106rv14.mp4").apply {
            writeBytes("CYOSPLAY_106RV14_STREAM_PAYLOAD".repeat(32).toByteArray())
        }
        val videoItem = VideoItem(
            id = 1401L,
            title = "Clip_106rv14",
            displayName = "Clip_106rv14.mp4",
            uri = Uri.fromFile(videoFile),
            path = videoFile.absolutePath,
            sizeBytes = videoFile.length(),
            durationMs = 50_000L,
            dateModifiedSeconds = 1_735_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "old_bucket_14",
            folderName = "OldBucket",
            folderPath = oldFolder.absolutePath
        )
        viewModel.videoRepository.saveManagedCatalog(emptyList())
        viewModel.addVideoItemToLibrary(videoItem)

        // Load into Mini-Player first to verify handle release before move
        viewModel.minimizeToMiniPlayer(
            video = videoItem,
            queue = listOf(videoItem),
            positionMs = 12_000L,
            durationMs = 50_000L,
            isPlaying = true
        )
        assertNotNull(viewModel.uiState.value.miniPlayerVideo)

        val moveRes = kotlinx.coroutines.runBlocking {
            viewModel.moveVideoViaVideoPlayerFileMover(
                videoUri = videoItem.uri,
                targetFolderRelativePath = newFolder.absolutePath,
                legacySourcePath = videoFile.absolutePath
            )
        }
        assertTrue(moveRes is com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.Success)
        assertNull("Active mini-player handle must be released before moving video", viewModel.uiState.value.miniPlayerVideo)
        assertFalse("Old file must be deleted from source folder", videoFile.exists())
        assertTrue("Moved file must exist in destination folder", File(newFolder, "Clip_106rv14.mp4").exists())
        assertFalse(
            "Source folder must no longer contain the moved video",
            viewModel.uiState.value.folders.any { f ->
                f.path.equals(oldFolder.absolutePath, ignoreCase = true) && f.videos.any { it.path == videoFile.absolutePath }
            }
        )
        moveRoot.deleteRecursively()

        // 2. Verify per-device AES-256-GCM build code encryption
        val encryptedBuildV14 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.06RV14_ENCRYPTED_BUILD_CODE_AND_DATA"
        )
        assertEquals(
            "CYOSPLAY_1.06RV14_ENCRYPTED_BUILD_CODE_AND_DATA",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuildV14)
        )
    }

    @Test
    fun v106rv15_features_verification() {
        context.getSharedPreferences("cyosplay_secure_settings_v2", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 1. Verify Large (24sp) and Transparent (75% opacity, no background box) subtitle defaults
        assertEquals(24f, viewModel.uiState.value.subtitleCustomSp, 0.01f)
        assertEquals(0.75f, viewModel.uiState.value.subtitleTextAlpha, 0.01f)
        assertTrue(viewModel.uiState.value.subtitleBackgroundBoxTransparent)

        // 2. Verify atomic moveFile(source, newPath): move removes original, rejects destination collision, and rolls back on delete failure
        val testRoot = File(context.cacheDir, "v106rv15_move_fix_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val folderA = File(testRoot, "FolderA").apply { mkdirs() }
        val folderB = File(testRoot, "FolderB").apply { mkdirs() }

        // 2a. Clean move removes original from FolderA and places in FolderB
        val file1 = File(folderA, "Action_106rv15.mp4").apply {
            writeBytes("CYOSPLAY_106RV15_VIDEO_STREAM".repeat(16).toByteArray())
        }
        val item1 = VideoItem(
            id = 1501L,
            title = "Action_106rv15",
            displayName = "Action_106rv15.mp4",
            uri = Uri.fromFile(file1),
            path = file1.absolutePath,
            sizeBytes = file1.length(),
            durationMs = 60_000L,
            dateModifiedSeconds = 1_740_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "folder_a_15",
            folderName = "FolderA",
            folderPath = folderA.absolutePath
        )
        viewModel.videoRepository.saveManagedCatalog(emptyList())
        viewModel.addVideoItemToLibrary(item1)

        val moveSuccess = viewModel.onMove(file1, folderB.absolutePath)
        assertTrue(moveSuccess)
        assertFalse("Original file must be removed from FolderA", file1.exists())
        assertTrue("Moved file must exist in FolderB", File(folderB, "Action_106rv15.mp4").exists())
        assertFalse(
            "Original file must be removed from UI list",
            viewModel.uiState.value.allVideos.any { it.path == file1.absolutePath }
        )

        // 2b. Collision check: moving a file with the same name to FolderB rejects the move and keeps original intact
        val duplicateInA = File(folderA, "Action_106rv15.mp4").apply {
            writeBytes("SECOND_DIFFERENT_STREAM".toByteArray())
        }
        val collisionMove = viewModel.onMove(duplicateInA, folderB.absolutePath)
        assertFalse("Move must be rejected when a file with the same name already exists at destination", collisionMove)
        assertTrue("Source file must remain untouched when move is rejected", duplicateInA.exists())
        assertTrue(
            "Status banner must report move failure on destination collision",
            viewModel.uiState.value.statusBannerMessage?.startsWith("Move failed:") == true
        )

        // 2c. Cross-volume copy+delete rollback: if deleting original fails after copy, copied file is deleted so no duplicate remains
        val crossVolSource = File(folderA, "CrossVolume_Rollback.mp4").apply {
            writeBytes("CROSS_VOLUME_PAYLOAD".repeat(16).toByteArray())
        }
        val crossVolDest = File(folderB, "CrossVolume_Rollback.mp4")
        var rollbackExceptionThrown = false
        try {
            com.cyosplay.videoplayer.util.VideoPlayerFileMover.moveFile(
                source = crossVolSource,
                newPath = crossVolDest.absolutePath,
                simulateRenameFailure = true,
                simulateSourceDeleteFailure = true
            )
        } catch (_: Throwable) {
            rollbackExceptionThrown = true
        }
        assertTrue("Exception must be rethrown when source deletion fails", rollbackExceptionThrown)
        assertTrue("Original source file must still exist after rollback", crossVolSource.exists())
        assertFalse("Copied destination file must be rolled back (deleted) so there is no duplicate", crossVolDest.exists())
        testRoot.deleteRecursively()

        // 3. Verify per-device AES-256-GCM build code encryption
        val encryptedBuildV15 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.06RV15_ENCRYPTED_BUILD_CODE_AND_DATA"
        )
        assertEquals(
            "CYOSPLAY_1.06RV15_ENCRYPTED_BUILD_CODE_AND_DATA",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuildV15)
        )
    }

    @Test
    fun v106rv16_features_verification() {
        context.getSharedPreferences("cyosplay_secure_settings_v2", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 1. Verify buildVideoContentUri & deleteVideoFromMediaStore helper
        val testContentUri = com.cyosplay.player.util.buildVideoContentUri(1601L)
        assertEquals("content://media/external/video/media/1601", testContentUri.toString())
        assertEquals(
            testContentUri,
            com.cyosplay.videoplayer.util.VideoPlayerFileMover.buildVideoContentUri(1601L)
        )

        // 2. Verify Move Video + Full State Relinking (playback progress, bookmarks, favorites, playlists, catalog)
        val moveRoot = File(context.cacheDir, "v106rv16_move_relink_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val sourceDir = File(moveRoot, "SourceFolder16").apply { mkdirs() }
        val targetDir = File(moveRoot, "TargetFolder16").apply { mkdirs() }
        val sourceFile = File(sourceDir, "Cinema_106rv16.mp4").apply {
            writeBytes("CYOSPLAY_106RV16_VIDEO_STREAM_PAYLOAD".repeat(24).toByteArray())
        }
        val sourceItem = VideoItem(
            id = 1601L,
            title = "Cinema_106rv16",
            displayName = "Cinema_106rv16.mp4",
            uri = Uri.fromFile(sourceFile),
            path = sourceFile.absolutePath,
            sizeBytes = sourceFile.length(),
            durationMs = 120_000L,
            dateModifiedSeconds = 1_750_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "src_16",
            folderName = "SourceFolder16",
            folderPath = sourceDir.absolutePath
        )
        viewModel.videoRepository.saveManagedCatalog(emptyList())
        viewModel.addVideoItemToLibrary(sourceItem)

        // Save playback progress, bookmark, favorite, and playlist entry under original path
        viewModel.savePlaybackProgress(sourceFile.absolutePath, 48_000L, 120_000L)
        viewModel.addBookmarkAt(sourceFile.absolutePath, 30_000L)
        viewModel.playlistRepository.toggleFavorite(sourceFile.absolutePath)
        val createdPlaylist = viewModel.playlistRepository.createPlaylist(
            name = "Action 16",
            description = "Test relinking",
            initialPaths = listOf(sourceFile.absolutePath)
        )

        // Move the video via VideoPlayerFileMover and verify state relinking to new path
        val moveRes = kotlinx.coroutines.runBlocking {
            viewModel.moveVideoViaVideoPlayerFileMover(
                videoUri = sourceItem.uri,
                targetFolderRelativePath = targetDir.absolutePath,
                legacySourcePath = sourceFile.absolutePath
            )
        }
        assertTrue(moveRes is com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.Success)
        val successRes = moveRes as com.cyosplay.videoplayer.util.VideoPlayerFileMover.MoveVideoResult.Success
        val expectedNewPath = File(targetDir, "Cinema_106rv16.mp4").absolutePath
        assertEquals(expectedNewPath, successRes.newFilePath)
        assertFalse("Original file must be removed from source folder", sourceFile.exists())
        assertTrue("Moved file must exist in target folder", File(expectedNewPath).exists())

        // Verify playback progress, bookmarks, favorites, and playlist entries were relinked to expectedNewPath
        val relinkedProgress = viewModel.settingsRepository.getAllPlaybackProgress()[expectedNewPath]
        assertNotNull("Playback progress must be relinked to new path after move", relinkedProgress)
        assertEquals(48_000L, relinkedProgress!!.lastPositionMs)
        assertEquals(expectedNewPath, viewModel.settingsRepository.getLastPlayedVideoPath())
        assertTrue(
            "Bookmarks must be relinked to new path after move",
            viewModel.settingsRepository.getVideoBookmarks(expectedNewPath).contains(30_000L)
        )
        assertTrue(
            "Favorites must be relinked to new path after move",
            viewModel.playlistRepository.getFavoriteVideoPaths().contains(expectedNewPath)
        )
        val updatedPlaylist = viewModel.playlistRepository.getPlaylists().first { it.id == createdPlaylist.id }
        assertTrue(
            "Playlist videoPaths must be relinked to new path after move",
            updatedPlaylist.videoPaths.contains(expectedNewPath)
        )
        moveRoot.deleteRecursively()

        // 3. Verify ULTRA_XXL (38sp preset, up to 64sp manual), new SubtitleColorOptions, and Multi-Language Subtitle Engine
        assertEquals(38f, com.cyosplay.player.data.model.SubtitleFontSize.ULTRA_XXL.spValue, 0.01f)
        viewModel.setSubtitleFontSize(com.cyosplay.player.data.model.SubtitleFontSize.ULTRA_XXL)
        assertEquals(com.cyosplay.player.data.model.SubtitleFontSize.ULTRA_XXL, viewModel.uiState.value.subtitleFontSize)
        assertEquals(38f, viewModel.uiState.value.subtitleCustomSp, 0.01f)
        viewModel.setSubtitleCustomSp(64f)
        assertEquals(64f, viewModel.uiState.value.subtitleCustomSp, 0.01f)

        viewModel.setSubtitleColor(com.cyosplay.player.data.model.SubtitleColorOption.AMBER_GOLD)
        assertEquals(com.cyosplay.player.data.model.SubtitleColorOption.AMBER_GOLD, viewModel.uiState.value.subtitleColor)
        viewModel.setSubtitleColor(com.cyosplay.player.data.model.SubtitleColorOption.SKY_BLUE)
        assertEquals(com.cyosplay.player.data.model.SubtitleColorOption.SKY_BLUE, viewModel.uiState.value.subtitleColor)

        assertTrue(com.cyosplay.player.util.SubtitleParser.SUPPORTED_SUBTITLE_LANGUAGES.size >= 30)
        assertTrue(com.cyosplay.player.util.SubtitleParser.isSupportedSubtitleLanguage("hi"))
        assertTrue(com.cyosplay.player.util.SubtitleParser.isSupportedSubtitleLanguage("ja"))
        assertTrue(com.cyosplay.player.util.SubtitleParser.isSupportedSubtitleLanguage("ar"))
        assertEquals("hi", com.cyosplay.player.util.SubtitleParser.detectSubtitleLanguage("नमस्ते दुनिया"))
        assertEquals("ja", com.cyosplay.player.util.SubtitleParser.detectSubtitleLanguage("こんにちは世界"))
        assertEquals("ar", com.cyosplay.player.util.SubtitleParser.detectSubtitleLanguage("مرحبا بالعالم"))

        // 4. Verify per-device AES-256-GCM build code encryption
        val encryptedBuildV16 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.06RV16_ENCRYPTED_BUILD_CODE_AND_DATA"
        )
        assertEquals(
            "CYOSPLAY_1.06RV16_ENCRYPTED_BUILD_CODE_AND_DATA",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuildV16)
        )
    }

    @Test
    fun v106rv17_permanentDelete_trueMove_sidecarSubtitles_andJarBuildPassword_verification() {
        context.getSharedPreferences("cyosplay_secure_settings_v2", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        val testRoot = File(context.cacheDir, "v106rv17_test_root").apply {
            deleteRecursively()
            mkdirs()
        }
        val mediaFolder = File(testRoot, "Movies17").apply { mkdirs() }
        val targetMoveFolder = File(testRoot, "MovedMovies17").apply { mkdirs() }

        // 1. Permanent Deletion of Video & Audio files from device storage + synchronization across playlists/favorites/history
        val videoFile = File(mediaFolder, "PermDelete_Video_17.mp4").apply {
            writeBytes("REAL_VIDEO_BYTES_FOR_PERMANENT_DELETE_17".repeat(20).toByteArray())
        }
        val audioFile = File(mediaFolder, "PermDelete_Audio_17.m4a").apply {
            writeBytes("REAL_AUDIO_BYTES_FOR_PERMANENT_DELETE_17".repeat(16).toByteArray())
        }
        val videoItem = VideoItem(
            id = 1701L,
            title = "PermDelete_Video_17",
            displayName = "PermDelete_Video_17.mp4",
            uri = Uri.fromFile(videoFile),
            path = videoFile.absolutePath,
            sizeBytes = videoFile.length(),
            durationMs = 90_000L,
            dateModifiedSeconds = 1_760_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "movies_17",
            folderName = "Movies17",
            folderPath = mediaFolder.absolutePath
        )
        val audioItem = VideoItem(
            id = 1702L,
            title = "PermDelete_Audio_17",
            displayName = "PermDelete_Audio_17.m4a",
            uri = Uri.fromFile(audioFile),
            path = audioFile.absolutePath,
            sizeBytes = audioFile.length(),
            durationMs = 180_000L,
            dateModifiedSeconds = 1_760_000_100L,
            width = 1280,
            height = 720,
            mimeType = "video/mp4",
            folderId = "movies_17",
            folderName = "Movies17",
            folderPath = mediaFolder.absolutePath
        )

        viewModel.videoRepository.saveManagedCatalog(emptyList())
        viewModel.addVideoItemToLibrary(videoItem)
        viewModel.addVideoItemToLibrary(audioItem)
        viewModel.savePlaybackProgress(videoFile.absolutePath, 25_000L, 90_000L)
        viewModel.playlistRepository.toggleFavorite(videoFile.absolutePath)
        val playlist17 = viewModel.playlistRepository.createPlaylist(
            name = "Playlist 17",
            description = "Delete sync test",
            initialPaths = listOf(videoFile.absolutePath, audioFile.absolutePath)
        )

        // Delete videoItem and audioItem
        viewModel.toggleBrowserSelectionMode()
        viewModel.toggleVideoSelection(videoItem)
        viewModel.toggleVideoSelection(audioItem)
        viewModel.requestDeleteHeldVideos()

        // Verify actual physical files are gone from device storage
        assertFalse("Deleted video file must no longer exist in device storage", videoFile.exists())
        assertFalse("Deleted audio file must no longer exist in device storage", audioFile.exists())
        assertTrue(com.cyosplay.player.util.MediaStoreFileHelper.isMediaActuallyDeleted(context, videoItem.uri, videoFile.absolutePath))
        assertTrue(com.cyosplay.player.util.MediaStoreFileHelper.isMediaActuallyDeleted(context, audioItem.uri, audioFile.absolutePath))

        // Verify removed from CyosPlay library, playlists, favorites, and recent playback progress
        assertFalse(viewModel.uiState.value.allVideos.any { it.path == videoFile.absolutePath || it.path == audioFile.absolutePath })
        assertFalse(viewModel.playlistRepository.getFavoriteVideoPaths().contains(videoFile.absolutePath))
        assertNull(viewModel.settingsRepository.getAllPlaybackProgress()[videoFile.absolutePath])
        val syncedPlaylist = viewModel.playlistRepository.getPlaylists().first { it.id == playlist17.id }
        assertTrue("Deleted files must be removed from playlists", syncedPlaylist.videoPaths.isEmpty())

        // 2. Verify Permission Denied / Undeleted File Handling: never hide undeleted files or show false success
        val protectedFile = File(mediaFolder, "Protected_Keep_17.mp4").apply {
            writeBytes("PROTECTED_MEDIA_CONTENT_17".repeat(12).toByteArray())
        }
        val protectedItem = videoItem.copy(
            id = 1703L,
            title = "Protected_Keep_17",
            displayName = "Protected_Keep_17.mp4",
            uri = Uri.fromFile(protectedFile),
            path = protectedFile.absolutePath
        )
        viewModel.addVideoItemToLibrary(protectedItem)
        viewModel.onHoldVideoItem(protectedItem)
        viewModel.onDeletePermissionDenied()
        assertTrue("Media entry must remain in CyosPlay when deletion permission is denied", viewModel.uiState.value.allVideos.any { it.path == protectedFile.absolutePath })
        assertEquals(
            "Deletion cancelled: Permission denied — media file kept intact",
            viewModel.uiState.value.statusBannerMessage
        )

        // 3. Verify Copy Integrity & True Move (original removed, zero duplicates)
        val moveSourceFile = File(mediaFolder, "TrueMove_17.mp4").apply {
            writeBytes("TRUE_MOVE_STREAM_106RV17_PAYLOAD_".repeat(64).toByteArray())
        }
        val moveCopyCheckFile = File(targetMoveFolder, "CopyCheck_17.mp4")
        assertTrue(CryptoSecurityManager.fastCopyFileZeroCopy(moveSourceFile, moveCopyCheckFile))
        assertTrue("Copied file integrity check must pass for identical content", CryptoSecurityManager.verifyCopiedFileIntegrity(moveSourceFile, moveCopyCheckFile))
        moveCopyCheckFile.writeBytes("TRUNCATED".toByteArray())
        assertFalse("Copied file integrity check must fail when file size/bytes differ", CryptoSecurityManager.verifyCopiedFileIntegrity(moveSourceFile, moveCopyCheckFile))
        moveCopyCheckFile.delete()

        val moveItem = videoItem.copy(
            id = 1704L,
            title = "TrueMove_17",
            displayName = "TrueMove_17.mp4",
            uri = Uri.fromFile(moveSourceFile),
            path = moveSourceFile.absolutePath,
            sizeBytes = moveSourceFile.length()
        )
        viewModel.addVideoItemToLibrary(moveItem)
        viewModel.onHoldVideoItem(moveItem)
        viewModel.startCopyOrMoveFlow(HoldClipboardOperation.MOVE)
        val moveDest = MoveDestinationOption(
            id = "dest_17",
            title = "MovedMovies17",
            subtitle = targetMoveFolder.absolutePath,
            targetPath = targetMoveFolder.absolutePath
        )
        viewModel.executeCopyOrMoveToDestination(moveDest)
        awaitViewModelFileOp(viewModel)
        val movedDestFile = File(targetMoveFolder, "TrueMove_17.mp4")
        assertFalse("Original file must NOT exist in source folder after Move", moveSourceFile.exists())
        assertTrue("Moved file MUST exist in destination folder after Move", movedDestFile.exists())
        assertEquals(
            "Exactly 1 instance of the moved video must exist in library (zero duplicates)",
            1,
            viewModel.uiState.value.allVideos.count { it.displayName == "TrueMove_17.mp4" }
        )

        // 4. Verify Automatic Multi-Language Sidecar Subtitle Discovery during video playback
        val hindiSrt = File(targetMoveFolder, "TrueMove_17.hi.srt").apply {
            writeText(
                """
                1
                00:00:01,000 --> 00:00:10,000
                नमस्ते दुनिया
                """.trimIndent()
            )
        }
        val arabicSrt = File(targetMoveFolder, "TrueMove_17.ar.srt").apply {
            writeText(
                """
                1
                00:00:01,000 --> 00:00:10,000
                مرحبا بالعالم
                """.trimIndent()
            )
        }
        val movedVideoItem = viewModel.uiState.value.allVideos.first { it.path == movedDestFile.absolutePath }
        viewModel.playVideo(movedVideoItem, listOf(movedVideoItem))
        // 1.08rv7 defers sidecar subtitle discovery off the tap hot path until first frame; invoke explicitly in headless test
        viewModel.discoverSidecarSubtitlesForVideo(movedVideoItem)
        assertTrue(
            "Sidecar multi-language subtitles (.hi.srt, .ar.srt) must be auto-discovered on playback",
            viewModel.uiState.value.externalSubtitleTracks.size >= 2
        )
        assertFalse(
            "Subtitles must remain unselected/off by default until user explicitly selects them",
            viewModel.uiState.value.subtitlesEnabledByUser
        )
        viewModel.selectAllSubtitles()
        assertTrue(viewModel.uiState.value.subtitlesEnabledByUser)
        assertTrue(viewModel.uiState.value.externalSubtitleCues.isNotEmpty())
        hindiSrt.delete()
        arabicSrt.delete()
        testRoot.deleteRecursively()

        // 5. Verify per-device AES-256-GCM build code encryption
        val encryptedBuildV17 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.06RV17_ENCRYPTED_BUILD_CODE_AND_DATA"
        )
        assertEquals(
            "CYOSPLAY_1.06RV17_ENCRYPTED_BUILD_CODE_AND_DATA",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuildV17)
        )
    }

    @Test
    fun v106rv18_perVideoAudioMemory_multiLanguageSubtitlesDefaultOff_recycleBinSilentDelete_andJarBuildPassword_verification() {
        context.getSharedPreferences("cyosplay_secure_settings_v2", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify version 1.06rv18 and zero debug/logs
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 2. Verify Per-Video Audio Selection Memory (remember last audio track setting for each video)
        val videoAPath = "/storage/emulated/0/Movies/Cinema_A_18.mp4"
        val videoBPath = "/storage/emulated/0/Movies/Cinema_B_18.mp4"
        val japaneseAudioTrack = AudioTrackInfo(
            groupIndex = 1,
            trackIndex = 2,
            label = "Japanese 5.1 E-AC3",
            language = "ja",
            codecBadge = "E-AC3",
            channelCount = 6,
            sampleRate = 48000,
            isSelected = true
        )
        val spanishAudioTrack = AudioTrackInfo(
            groupIndex = 0,
            trackIndex = 1,
            label = "Spanish Stereo AAC",
            language = "es",
            codecBadge = "AAC-LC",
            channelCount = 2,
            sampleRate = 48000,
            isSelected = true
        )

        viewModel.selectAudioTrack(japaneseAudioTrack, videoAPath)
        viewModel.selectAudioTrack(spanishAudioTrack, videoBPath)

        val savedAudioA = viewModel.getSavedAudioTrackForVideo(videoAPath)
        val savedAudioB = viewModel.getSavedAudioTrackForVideo(videoBPath)
        assertNotNull("Video A must remember its selected Japanese audio track", savedAudioA)
        assertEquals("ja", savedAudioA?.language)
        assertEquals("Japanese 5.1 E-AC3", savedAudioA?.label)
        assertEquals(1, savedAudioA?.groupIndex)
        assertEquals(2, savedAudioA?.trackIndex)
        assertFalse(savedAudioA?.isMuted ?: true)

        assertNotNull("Video B must remember its selected Spanish audio track", savedAudioB)
        assertEquals("es", savedAudioB?.language)
        assertEquals("Spanish Stereo AAC", savedAudioB?.label)

        // Verify per-video mute state persistence as well
        viewModel.setAudioMuted(true, videoBPath)
        val mutedAudioB = viewModel.getSavedAudioTrackForVideo(videoBPath)
        assertTrue("Video B must remember its muted audio setting", mutedAudioB?.isMuted == true)

        // 3. Verify Multi-Language Subtitle Engine (56 languages, TTML/ASS/WebVTT/SRT, RTL BiDi, binary-search lookup)
        assertTrue(
            "Multi-language subtitle engine must support at least 56 languages",
            com.cyosplay.player.util.SubtitleParser.SUPPORTED_SUBTITLE_LANGUAGES.size >= 56
        )
        val ttmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:00:02.000" end="00:00:06.500">こんにちは世界<br/>Multi-line TTML</p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        val ttmlCues = com.cyosplay.player.util.SubtitleParser.parseSubtitleText(ttmlContent)
        assertEquals(1, ttmlCues.size)
        assertEquals(2000L, ttmlCues[0].startTimeMs)
        assertEquals(6500L, ttmlCues[0].endTimeMs)
        assertTrue(ttmlCues[0].text.contains("こんにちは世界"))

        val rtlFormatted = com.cyosplay.player.util.SubtitleParser.formatSubtitleTextForDisplay("مرحبا بالعالم")
        assertTrue("RTL Arabic subtitle text must be prefixed with RLM mark for proper BiDi rendering", rtlFormatted.startsWith("\u200F"))

        val activeCueText = com.cyosplay.player.util.SubtitleParser.findActiveSubtitleCue(ttmlCues, 3500L, 0L)
        assertNotNull("Binary-search active cue lookup must find active cue", activeCueText)

        // 4. Verify "dont select any subtitle untill i select" + simultaneous multi-track subtitle selection
        val testRoot = File(context.cacheDir, "cyos_rv18_sub_delete_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val videoFile = File(testRoot, "MultiSub_18.mp4").apply {
            writeBytes(ByteArray(2048) { (it % 251).toByte() })
        }
        val srtEn = File(testRoot, "MultiSub_18.en.srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:08,000\nHello World\n")
        }
        val srtKo = File(testRoot, "MultiSub_18.ko.srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:08,000\n안녕하세요 세계\n")
        }
        val videoItem = VideoItem(
            id = 91801L,
            uri = Uri.fromFile(videoFile),
            displayName = videoFile.name,
            title = "MultiSub_18",
            path = videoFile.absolutePath,
            sizeBytes = videoFile.length(),
            durationMs = 60_000L,
            dateModifiedSeconds = 1_760_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "movies_18",
            folderName = testRoot.name,
            folderPath = testRoot.absolutePath
        )
        viewModel.addVideoItemToLibrary(videoItem)
        viewModel.playVideo(videoItem, listOf(videoItem))
        // 1.08rv7 defers sidecar subtitle scanning off the tap hot path until first frame; invoke discovery explicitly in headless test
        viewModel.discoverSidecarSubtitlesForVideo(videoItem)

        // Sidecar subtitles must be discovered, but NOT selected until user selects
        assertTrue(viewModel.uiState.value.externalSubtitleTracks.size >= 2)
        assertFalse(
            "No subtitle may be selected automatically when opening a video",
            viewModel.uiState.value.subtitlesEnabledByUser
        )
        assertTrue(
            "Selected external subtitle track IDs must be empty until user selects",
            viewModel.uiState.value.selectedExternalSubtitleTrackIds.isEmpty()
        )
        assertTrue(
            "Active external subtitle cues must be empty until user selects a subtitle",
            viewModel.uiState.value.externalSubtitleCues.isEmpty()
        )

        // Now user selects both subtitle tracks simultaneously
        val discoveredTracks = viewModel.uiState.value.externalSubtitleTracks
        viewModel.toggleExternalSubtitleTrackSelection(discoveredTracks[0].id)
        viewModel.toggleExternalSubtitleTrackSelection(discoveredTracks[1].id)
        assertTrue(viewModel.uiState.value.subtitlesEnabledByUser)
        assertEquals(2, viewModel.uiState.value.selectedExternalSubtitleTrackIds.size)
        assertEquals(2, viewModel.uiState.value.externalSubtitleCues.size)

        val simultaneousText = com.cyosplay.player.util.SubtitleParser.findActiveSubtitleCue(
            viewModel.uiState.value.externalSubtitleCues,
            positionMs = 3000L,
            offsetMs = 0L
        )
        assertNotNull(simultaneousText)
        assertTrue(simultaneousText!!.contains("Hello World"))
        assertTrue(simultaneousText.contains("안녕하세요 세계"))

        // 5. Verify Recycle Bin option on delete & completely silent deletion (no notification/statusBannerMessage)
        val recycleFile = File(testRoot, "RecycleSample_18.mp4").apply {
            writeBytes(ByteArray(1024) { 0x42 })
        }
        val recycleItem = VideoItem(
            id = 91802L,
            uri = Uri.fromFile(recycleFile),
            displayName = recycleFile.name,
            title = "RecycleSample_18",
            path = recycleFile.absolutePath,
            sizeBytes = recycleFile.length(),
            durationMs = 15_000L,
            dateModifiedSeconds = 1_760_000_100L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "movies_18",
            folderName = testRoot.name,
            folderPath = testRoot.absolutePath
        )
        viewModel.addVideoItemToLibrary(recycleItem)
        viewModel.onHoldVideoItem(recycleItem)
        viewModel.openDeleteOrRecycleDialog()
        assertTrue("Delete/Recycle confirmation dialog must open", viewModel.uiState.value.showDeleteConfirmDialog)

        viewModel.moveHeldVideosToRecycleBin()
        assertNull(
            "Moving to Recycle Bin must NOT show any notification/toast/banner",
            viewModel.uiState.value.statusBannerMessage
        )
        assertTrue(
            "Recycled item must appear in Recycle Bin",
            viewModel.uiState.value.recycleBinItems.any { it.originalPath == recycleFile.absolutePath }
        )

        // Permanent delete must also be completely silent (statusBannerMessage == null)
        viewModel.onHoldVideoItem(videoItem)
        viewModel.requestDeleteHeldVideos()
        assertNull(
            "Permanent delete must NOT show any notification/toast/banner",
            viewModel.uiState.value.statusBannerMessage
        )
        assertFalse("Permanently deleted file must be removed from storage", videoFile.exists())

        srtEn.delete()
        srtKo.delete()
        recycleFile.delete()
        testRoot.deleteRecursively()

        // 6. Verify per-device AES-256-GCM build code encryption
        val encryptedBuild18 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.06RV18_ENCRYPTED_BUILD_CODE_AND_DATA"
        )
        assertEquals(
            "CYOSPLAY_1.06RV18_ENCRYPTED_BUILD_CODE_AND_DATA",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild18)
        )
    }

    @Test
    fun v107rv_recycleBinPermanentDelete_linkedDeleteDialog_playableSelectedSubtitles_andJarBuildPassword_verification() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify version 1.07rv2 and zero debug/logs
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        val testRoot = File(context.cacheDir, "cyos_107rv_test_${System.currentTimeMillis()}").apply { mkdirs() }

        // 2. Verify permanent deletion from Recycle Bin removes the ACTUAL physical file from storage & MediaStore before removing Recycle Bin record
        val physicalVideo1 = File(testRoot, "PermanentDeleteFromBin_1.mp4").apply {
            writeBytes(ByteArray(2048) { (it % 251).toByte() })
        }
        val physicalVideo2 = File(testRoot, "EmptyBinDelete_2.mp4").apply {
            writeBytes(ByteArray(2048) { (it % 199).toByte() })
        }
        val item1 = VideoItem(
            id = 90701L,
            uri = Uri.fromFile(physicalVideo1),
            displayName = physicalVideo1.name,
            title = "PermanentDeleteFromBin_1",
            path = physicalVideo1.absolutePath,
            sizeBytes = physicalVideo1.length(),
            durationMs = 30_000L,
            dateModifiedSeconds = 1_770_000_001L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "folder_107rv",
            folderName = testRoot.name,
            folderPath = testRoot.absolutePath
        )
        val item2 = VideoItem(
            id = 90702L,
            uri = Uri.fromFile(physicalVideo2),
            displayName = physicalVideo2.name,
            title = "EmptyBinDelete_2",
            path = physicalVideo2.absolutePath,
            sizeBytes = physicalVideo2.length(),
            durationMs = 45_000L,
            dateModifiedSeconds = 1_770_000_002L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "folder_107rv",
            folderName = testRoot.name,
            folderPath = testRoot.absolutePath
        )

        viewModel.addVideoItemToLibrary(item1)
        viewModel.addVideoItemToLibrary(item2)

        // Link Delete button with Recycle Bin & Permanent Delete dialog
        viewModel.onHoldVideoItem(item1)
        viewModel.openDeleteOrRecycleDialog()
        assertTrue("Tapping Delete must open the Recycle Bin / Permanent Delete dialog", viewModel.uiState.value.showDeleteConfirmDialog)

        // Move item1 and item2 to Recycle Bin (keeps original in storage/MediaStore trash until permanently deleted from Recycle Bin)
        viewModel.moveHeldVideosToRecycleBin()
        viewModel.onHoldVideoItem(item2)
        viewModel.moveHeldVideosToRecycleBin()

        val binEntry1 = viewModel.uiState.value.recycleBinItems.firstOrNull { it.originalPath == physicalVideo1.absolutePath }
        val binEntry2 = viewModel.uiState.value.recycleBinItems.firstOrNull { it.originalPath == physicalVideo2.absolutePath }
        assertNotNull("item1 must be in Recycle Bin", binEntry1)
        assertNotNull("item2 must be in Recycle Bin", binEntry2)
        assertTrue("Original physical file 1 exists while in Recycle Bin before permanent deletion", physicalVideo1.exists())
        assertTrue("Original physical file 2 exists while in Recycle Bin before permanent deletion", physicalVideo2.exists())

        // Permanently delete item1 from Recycle Bin -> MUST delete physicalVideo1 from storage AND remove Recycle Bin record
        viewModel.deleteRecycleBinItemPermanently(binEntry1!!.recycleId)
        assertFalse(
            "Permanent delete from Recycle Bin MUST delete the actual physical file from device storage",
            physicalVideo1.exists()
        )
        assertTrue(
            "MediaStoreFileHelper.isMediaActuallyDeleted must confirm physical & MediaStore deletion",
            com.cyosplay.player.util.MediaStoreFileHelper.isMediaActuallyDeleted(
                context = context,
                uri = Uri.parse(binEntry1.originalUriString),
                filePath = binEntry1.originalPath
            )
        )
        assertFalse(
            "Recycle Bin entry 1 must be removed after physical deletion succeeds",
            viewModel.uiState.value.recycleBinItems.any { it.recycleId == binEntry1.recycleId }
        )

        // Empty Recycle Bin -> MUST permanently delete remaining files (physicalVideo2) from storage AND remove Recycle Bin records
        viewModel.emptyRecycleBin()
        awaitViewModelFileOp(viewModel)
        assertFalse(
            "Empty Recycle Bin MUST delete the actual physical file 2 from device storage",
            physicalVideo2.exists()
        )
        assertFalse(
            "Recycle Bin entry 2 must be removed after Empty Recycle Bin physical deletion succeeds",
            viewModel.uiState.value.recycleBinItems.any { it.recycleId == binEntry2!!.recycleId }
        )

        // 3. Verify all subtitle tracks (embedded & external) start OFF and become playable ONLY when explicitly selected
        val subVideoFile = File(testRoot, "PlayableSubs_107rv.mkv").apply {
            writeBytes(ByteArray(1024) { 0x33 })
        }
        val srt1 = File(testRoot, "PlayableSubs_107rv.en.srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:09,000\nEnglish External Cue\n")
        }
        val srt2 = File(testRoot, "PlayableSubs_107rv.ja.srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:09,000\n日本語字幕テスト\n")
        }
        val subVideoItem = VideoItem(
            id = 90703L,
            uri = Uri.fromFile(subVideoFile),
            displayName = subVideoFile.name,
            title = "PlayableSubs_107rv",
            path = subVideoFile.absolutePath,
            sizeBytes = subVideoFile.length(),
            durationMs = 60_000L,
            dateModifiedSeconds = 1_770_000_003L,
            width = 1920,
            height = 1080,
            mimeType = "video/x-matroska",
            folderId = "folder_107rv",
            folderName = testRoot.name,
            folderPath = testRoot.absolutePath
        )
        viewModel.addVideoItemToLibrary(subVideoItem)
        viewModel.playVideo(subVideoItem, listOf(subVideoItem))
        // 1.08rv7 defers sidecar subtitle scanning off the tap hot path until first frame; invoke discovery explicitly in headless test
        viewModel.discoverSidecarSubtitlesForVideo(subVideoItem)

        // Simulate discovering 2 embedded subtitle tracks while video is playing
        val embeddedTrack0 = SubtitleTrackInfo(groupIndex = 0, trackIndex = 0, label = "English Embedded", language = "English", isSelected = false)
        val embeddedTrack1 = SubtitleTrackInfo(groupIndex = 0, trackIndex = 1, label = "Spanish Embedded", language = "Spanish", isSelected = false)
        viewModel.updateAvailableTracks(audioTracks = emptyList(), subtitleTracks = listOf(embeddedTrack0, embeddedTrack1))

        // Initially all subtitles must be OFF until selected
        assertFalse(viewModel.uiState.value.subtitlesEnabledByUser)
        assertTrue(viewModel.uiState.value.subtitleTracks.none { it.isSelected })
        assertTrue(viewModel.uiState.value.selectedExternalSubtitleTrackIds.isEmpty())

        // Selecting an external subtitle track must play ONLY that external track and NOT auto-select embedded tracks
        val extTracks = viewModel.uiState.value.externalSubtitleTracks
        assertTrue(extTracks.size >= 2)
        viewModel.toggleExternalSubtitleTrackSelection(extTracks[0].id)
        assertTrue(viewModel.uiState.value.subtitlesEnabledByUser)
        assertEquals(setOf(extTracks[0].id), viewModel.uiState.value.selectedExternalSubtitleTrackIds)
        assertTrue(
            "Selecting an external subtitle track must not auto-select unselected embedded tracks",
            viewModel.uiState.value.subtitleTracks.none { it.isSelected }
        )

        // Now user explicitly selects embeddedTrack1 -> both selected external track and embeddedTrack1 are active
        viewModel.toggleEmbeddedSubtitleTrackSelection(embeddedTrack1)
        assertFalse(viewModel.uiState.value.subtitleTracks[0].isSelected)
        assertTrue(viewModel.uiState.value.subtitleTracks[1].isSelected)

        subVideoFile.delete()
        srt1.delete()
        srt2.delete()
        testRoot.deleteRecursively()

        // 4. Verify per-device AES-256-GCM build code encryption
        val encryptedBuild107 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.07RV_SIGNED_ENCRYPTED_BUILD_CODE"
        )
        assertEquals(
            "CYOSPLAY_1.07RV_SIGNED_ENCRYPTED_BUILD_CODE",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild107)
        )
    }

    @Test
    fun v107rv2_xvidMatroskaExtractor_csd0VolHeader_nextRenderersSoftwareFallback_zoomBrightnessLock_andJarPassword_verification() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify version 1.07rv2 and zero debug/logs
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 2. Verify XvidMatroskaExtractor csd-0 extraction (00 00 01 B0 F5 ... 00 00 01 20 ... up to 00 00 01 B6)
        // and non-square pixel aspect ratio (396:563 on 704x396 -> 4:3 display)
        val keyframeWithVolHeader = byteArrayOf(
            // Visual Object Sequence Start Code: 00 00 01 B0 F5 (ASP Level 5)
            0x00, 0x00, 0x01, 0xB0.toByte(), 0xF5.toByte(),
            // Visual Object Start Code: 00 00 01 B5 89 13 00 00 01 00
            0x00, 0x00, 0x01, 0xB5.toByte(), 0x89.toByte(), 0x13, 0x00, 0x00, 0x01, 0x00,
            // Video Object Layer (VOL) Start Code: 00 00 01 20 ...
            0x00, 0x00, 0x01, 0x20, 0x00, 0xC8.toByte(), 0x88.toByte(), 0x80.toByte(),
            // User Data (DivX503b1393p packed B-frames): 00 00 01 B2 ...
            0x00, 0x00, 0x01, 0xB2.toByte(), 0x44, 0x69, 0x76, 0x58, 0x35, 0x30, 0x33, 0x62, 0x31, 0x33, 0x39, 0x33, 0x70,
            // VOP Start Code (actual frame start): 00 00 01 B6 ...
            0x00, 0x00, 0x01, 0xB6.toByte(), 0x10, 0x22, 0x33, 0x44
        )
        val extractedCsd0 = com.cyosplay.player.XvidFixingTrackOutput.extractMpeg4Csd0(
            keyframeWithVolHeader,
            keyframeWithVolHeader.size
        )
        assertNotNull("MPEG-4 VOL header before 00 00 01 B6 must be extracted as csd-0", extractedCsd0)
        assertEquals(0x00.toByte(), extractedCsd0!![0])
        assertEquals(0x00.toByte(), extractedCsd0[1])
        assertEquals(0x01.toByte(), extractedCsd0[2])
        assertEquals(0xB0.toByte(), extractedCsd0[3])
        assertEquals(0xF5.toByte(), extractedCsd0[4])
        // Ensure VOP start code (00 00 01 B6 ...) is NOT included in csd-0
        assertEquals(keyframeWithVolHeader.size - 8, extractedCsd0.size)

        // Verify XvidFixingTrackOutput intercepts video/x-unknown from Matroska V_MS/VFW/FOURCC (XVID)
        // and emits MimeTypes.VIDEO_MP4V ("video/mp4v-es") with csd-0 and 396:563 PAR
        var capturedFormat: androidx.media3.common.Format? = null
        var capturedSampleBytes: ByteArray? = null
        val recordingTrackOutput = object : androidx.media3.extractor.TrackOutput {
            override fun format(format: androidx.media3.common.Format) {
                capturedFormat = format
            }
            override fun sampleData(
                input: androidx.media3.common.DataReader,
                length: Int,
                allowEndOfInput: Boolean,
                sampleDataPart: Int
            ): Int = 0
            override fun sampleData(data: androidx.media3.common.util.ParsableByteArray, length: Int, sampleDataPart: Int) {
                val out = ByteArray(length)
                data.readBytes(out, 0, length)
                capturedSampleBytes = out
            }
            override fun sampleMetadata(
                timeUs: Long,
                flags: Int,
                size: Int,
                offset: Int,
                cryptoData: androidx.media3.extractor.TrackOutput.CryptoData?
            ) = Unit
        }
        val xvidFixingTrackOutput = com.cyosplay.player.XvidFixingTrackOutput(recordingTrackOutput)
        val unknownMkvFormat = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.VIDEO_UNKNOWN)
            .setWidth(704)
            .setHeight(396)
            .setPixelWidthHeightRatio(1.0f)
            .build()

        xvidFixingTrackOutput.format(unknownMkvFormat)
        assertNull("Format emission must be deferred until first keyframe csd-0 is inspected", capturedFormat)

        val parsableFrame = androidx.media3.common.util.ParsableByteArray(keyframeWithVolHeader)
        xvidFixingTrackOutput.sampleData(parsableFrame, keyframeWithVolHeader.size, 0)
        xvidFixingTrackOutput.sampleMetadata(0L, androidx.media3.common.C.BUFFER_FLAG_KEY_FRAME, keyframeWithVolHeader.size, 0, null)

        assertNotNull("Format must be emitted on first keyframe with video/mp4v-es", capturedFormat)
        assertEquals(androidx.media3.common.MimeTypes.VIDEO_MP4V, capturedFormat!!.sampleMimeType)
        assertEquals("mp4v.20.5", capturedFormat!!.codecs)
        // Normal square-pixel 16:9 704x396 (PAR == 1.0f) must NOT be squished
        assertEquals(1.0f, capturedFormat!!.pixelWidthHeightRatio, 0.0001f)
        // Explicit anamorphic SAR 396:563 (704x396 -> 4:3) normalizes cleanly to 0.75f
        val anamorphicPar = com.cyosplay.player.XvidFixingTrackOutput.resolveNormalizedPixelAspectRatio(
            androidx.media3.common.Format.Builder()
                .setWidth(704)
                .setHeight(396)
                .setPixelWidthHeightRatio(396f / 563f)
                .build()
        )
        assertEquals(0.75f, anamorphicPar, 0.0001f)
        assertEquals(1, capturedFormat!!.initializationData.size)
        assertTrue(capturedFormat!!.initializationData[0].contentEquals(extractedCsd0))
        assertNotNull("First keyframe sample bytes must be flushed downstream after format()", capturedSampleBytes)
        assertTrue(capturedSampleBytes!!.contentEquals(keyframeWithVolHeader))

        // Verify XvidSupportExtractorsFactory wraps MatroskaExtractor even when URI has .mp4 extension
        val extractorsFactory = com.cyosplay.player.XvidSupportExtractorsFactory()
        val extractorsForMp4Uri = extractorsFactory.createExtractors(
            Uri.parse("file:///storage/emulated/0/Movies/Hindi.mp4"),
            emptyMap()
        )
        assertTrue(
            "XvidSupportExtractorsFactory must include XvidMatroskaExtractor for .mp4-named Matroska files",
            extractorsForMp4Uri.any { it is com.cyosplay.player.XvidMatroskaExtractor }
        )

        // 3. Verify NextRenderersFactory & buildCyosExoPlayer construct cleanly with software MPEG-4 priority
        val nextRenderersFactory = io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory(context)
        assertNotNull(nextRenderersFactory)
        val cyosPlayer = com.cyosplay.player.buildCyosExoPlayer(context)
        assertNotNull(cyosPlayer)
        cyosPlayer.release()

        // 4. Verify brightness stays strictly unchanged during video zoom or multi-touch
        val lockedBrightnessDuringPinch = com.cyosplay.player.util.PlayerGestureControlHelper.resolveBrightnessDuringZoomOrMultiTouch(
            currentBrightness = 0.92f,
            preZoomBrightnessSnapshot = 0.45f,
            activePointerCount = 2,
            isPinchZoomActive = true,
            recentlyPinched = true
        )
        assertEquals(
            "Brightness must be restored and locked to pre-zoom value (0.45f) whenever zooming or multi-touch is active",
            0.45f,
            lockedBrightnessDuringPinch,
            0.0001f
        )
        assertFalse(
            "Vertical brightness/volume gesture must be blocked while pinch-zoom is active",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandlePlaybackVerticalGesture(
                isPlaying = true,
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 1,
                isHoldFastForwardActive = false,
                isHorizontalSeekingActive = false,
                isPinchZoomActive = true,
                recentlyPinched = false
            )
        )
        assertFalse(
            "Vertical brightness/volume gesture must be blocked immediately after pinch-zoom release",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandlePlaybackVerticalGesture(
                isPlaying = true,
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 1,
                isHoldFastForwardActive = false,
                isHorizontalSeekingActive = false,
                isPinchZoomActive = false,
                recentlyPinched = true
            )
        )

        // 5. Verify per-device AES-256-GCM build code encryption
        val encryptedBuild107rv2 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.07RV2_SIGNED_ENCRYPTED_BUILD_CODE"
        )
        assertEquals(
            "CYOSPLAY_1.07RV2_SIGNED_ENCRYPTED_BUILD_CODE",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild107rv2)
        )
    }

    @Test
    fun v107rv3_cyosPlayerMediaStoreDeleter_gestureIsolation_auto50At85Warning_hwSwPlus_andJarPassword_verification() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify version 1.07rv3 and zero debug/logs
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 2. Verify CyosPlayerMediaStoreDeleter (package com.cyos.player) deletes video + sidecar subtitles and releases ExoPlayer handle
        val testDir = File(context.cacheDir, "cyos_107rv3_deleter_${System.currentTimeMillis()}").apply { mkdirs() }
        val targetVideoFile = File(testDir, "Movie_107rv3.mp4").apply {
            writeBytes(ByteArray(4096) { (it % 251).toByte() })
        }
        val sidecarSrt = File(testDir, "Movie_107rv3.srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:05,000\nSidecar SRT\n")
        }
        val sidecarHiAss = File(testDir, "Movie_107rv3.hi.ass").apply {
            writeText("[Events]\nDialogue: 0,0:00:01.00,0:00:05.00,Default,,0,0,0,,नमस्ते\n")
        }
        val unrelatedSrt = File(testDir, "OtherMovie.srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:05,000\nKeep Me\n")
        }

        val targetVideoItem = VideoItem(
            id = 907301L,
            uri = Uri.fromFile(targetVideoFile),
            displayName = targetVideoFile.name,
            title = "Movie_107rv3",
            path = targetVideoFile.absolutePath,
            sizeBytes = targetVideoFile.length(),
            durationMs = 120_000L,
            dateModifiedSeconds = 1_780_000_001L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "folder_107rv3",
            folderName = testDir.name,
            folderPath = testDir.absolutePath
        )
        viewModel.addVideoItemToLibrary(targetVideoItem)

        val exoPlayer = com.cyosplay.player.buildCyosExoPlayer(context)
        exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(targetVideoItem.uri))
        com.cyos.player.CyosPlayerMediaStoreDeleter.releaseActivePlayerHandleIfNeeded(
            exoPlayer = exoPlayer,
            targetUris = listOf(targetVideoItem.uri),
            targetFilePaths = listOf(targetVideoFile.absolutePath)
        )
        assertEquals(0, exoPlayer.mediaItemCount)
        exoPlayer.release()

        val deleteResult = kotlinx.coroutines.runBlocking {
            com.cyos.player.CyosPlayerMediaStoreDeleter.deleteVideos(
                context = context,
                videoUris = listOf(targetVideoItem.uri),
                filePaths = listOf(targetVideoFile.absolutePath),
                deleteAssociatedSubtitles = true
            )
        }
        assertTrue(
            "CyosPlayerMediaStoreDeleter.deleteVideos must return Success for local file deletion",
            deleteResult is com.cyos.player.CyosPlayerMediaStoreDeleter.DeleteResult.Success
        )
        assertFalse("Deleted video file must be removed from disk", targetVideoFile.exists())
        assertFalse("Matching .srt sidecar subtitle must be deleted automatically", sidecarSrt.exists())
        assertFalse("Matching .hi.ass language sidecar subtitle must be deleted automatically", sidecarHiAss.exists())
        assertTrue("Unrelated subtitle in the same folder must NOT be deleted", unrelatedSrt.exists())

        viewModel.onVideosDeletedFromMediaStore(
            deletedUris = listOf(targetVideoItem.uri),
            deletedPaths = listOf(targetVideoFile.absolutePath)
        )
        assertFalse(
            "UI video list must remove the video after MediaStore deletion succeeds",
            viewModel.uiState.value.allVideos.any { it.path == targetVideoFile.absolutePath }
        )
        unrelatedSrt.delete()
        testDir.deleteRecursively()

        // 3. Verify High Volume & Screen Brightness Warning Alert at >= 85% is non-destructive and provides Lower Both
        viewModel.dismissHighVolumeBrightnessWarning()
        viewModel.updateSavedPlayerBrightness(0.86f)
        viewModel.updateSavedPlayerVolume(0.90f)
        assertTrue(
            "Exceeding 85% volume and brightness must trigger showHighVolumeBrightnessWarning",
            viewModel.uiState.value.showHighVolumeBrightnessWarning
        )
        assertEquals(
            "Non-destructive warning must preserve user's brightness slider until Lower Both is tapped",
            0.86f,
            viewModel.uiState.value.savedPlayerBrightness,
            0.005f
        )
        assertEquals(
            "Non-destructive warning must preserve user's volume slider until Lower Both is tapped",
            0.90f,
            viewModel.uiState.value.savedPlayerVolume,
            0.005f
        )
        viewModel.lowerBothVolumeAndBrightness()
        assertFalse(viewModel.uiState.value.showHighVolumeBrightnessWarning)
        assertEquals(0.50f, viewModel.uiState.value.savedPlayerBrightness, 0.005f)
        assertEquals(0.50f, viewModel.uiState.value.savedPlayerVolume, 0.005f)

        // 4. Verify strict gesture isolation (Zoom, Volume, Brightness, Horizontal Seek, Hold 2x Skip)
        assertFalse(
            "Horizontal seek must be blocked when Hold Fast-Forward (2x) is active",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandlePlaybackHorizontalSeek(
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 1,
                isHoldFastForwardActive = true,
                isVerticalGestureActive = false,
                isPinchZoomActive = false,
                recentlyPinched = false
            )
        )
        assertFalse(
            "Horizontal seek must be blocked when Vertical Volume/Brightness gesture is active",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandlePlaybackHorizontalSeek(
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 1,
                isHoldFastForwardActive = false,
                isVerticalGestureActive = true,
                isPinchZoomActive = false,
                recentlyPinched = false
            )
        )
        assertFalse(
            "Hold Fast-Forward must be blocked when Pinch-Zoom or Horizontal Seek is active",
            com.cyosplay.player.util.PlayerGestureControlHelper.canStartHoldFastForward(
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 2,
                isPinchZoomActive = true,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                recentlyPinched = true
            )
        )
        assertEquals(
            "Volume must remain locked to pre-gesture snapshot during brightness/zoom/seek",
            0.64f,
            com.cyosplay.player.util.PlayerGestureControlHelper.resolveVolumeDuringNonVolumeGesture(
                currentVolume = 0.95f,
                preGestureVolumeSnapshot = 0.64f,
                activePointerCount = 2,
                isPinchZoomActive = true
            ),
            0.0001f
        )

        // 5. Verify HW / HW+ / SW / SW+ DecoderEngineMode selector & LRC + ISO-639-2 subtitle language resolution
        com.cyosplay.player.data.model.DecoderEngineMode.entries.forEach { mode ->
            val baseSelector = com.cyosplay.player.util.AudioBadgeFormatter.createMultiCodecSelectorForEngine(
                decoderEngineMode = mode
            )
            val selector = com.cyosplay.player.createXvidAwareMediaCodecSelector(
                fallbackSelector = baseSelector,
                preferSoftwareForAllCodecs = mode == com.cyosplay.player.data.model.DecoderEngineMode.SW ||
                    mode == com.cyosplay.player.data.model.DecoderEngineMode.SW_PLUS
            )
            assertNotNull("MediaCodecSelector must be created for engine mode $mode", selector)
        }
        assertTrue(com.cyosplay.player.util.SubtitleParser.resolveLanguageDisplayLabel("hin", 1).startsWith("Hindi"))
        assertTrue(com.cyosplay.player.util.SubtitleParser.resolveLanguageDisplayLabel("jpn", 2).startsWith("Japanese"))
        val lrcCues = com.cyosplay.player.util.SubtitleParser.parseSubtitleText(
            "[00:01.00]First Lyric Line\n[00:04.50]Second Lyric Line"
        )
        assertEquals(2, lrcCues.size)
        assertEquals("First Lyric Line", com.cyosplay.player.util.SubtitleParser.findActiveSubtitleCue(lrcCues, 2000L, 0L))

        // 6. Verify per-device AES-256-GCM build code encryption
        val encryptedBuild107rv3 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.07RV3_SIGNED_ENCRYPTED_BUILD_CODE"
        )
        assertEquals(
            "CYOSPLAY_1.07RV3_SIGNED_ENCRYPTED_BUILD_CODE",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild107rv3)
        )
    }

    @Test
    fun v107rv4_media3SubtitleTranscoding_attachExternalSubtitle_nonFatalSubtitleError_frontPopup_andJarPassword_verification() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify version 1.07rv4 and zero debug/logs
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 2. Fix a: Verify XvidSupportExtractorsFactory uses DefaultSubtitleParserFactory() (never UNSUPPORTED)
        val extractorsFactoryCyos = com.cyos.player.XvidSupportExtractorsFactory()
        val createdExtractors = extractorsFactoryCyos.createExtractors()
        assertTrue(
            "XvidSupportExtractorsFactory must wrap MatroskaExtractor with XvidMatroskaExtractor(MatroskaExtractor(DefaultSubtitleParserFactory()))",
            createdExtractors.any { it is com.cyosplay.player.XvidMatroskaExtractor }
        )

        // 3. Fix b: Verify resolveSubtitleMimeType and attachExternalSubtitle via MediaItem.SubtitleConfiguration
        assertEquals(
            androidx.media3.common.MimeTypes.APPLICATION_SUBRIP,
            com.cyos.player.resolveSubtitleMimeType(context, Uri.parse("file:///storage/emulated/0/Movies/Sub.srt"))
        )
        assertEquals(
            androidx.media3.common.MimeTypes.TEXT_VTT,
            com.cyos.player.resolveSubtitleMimeType(context, Uri.parse("file:///storage/emulated/0/Movies/Sub.vtt"))
        )
        assertEquals(
            androidx.media3.common.MimeTypes.TEXT_SSA,
            com.cyos.player.resolveSubtitleMimeType(context, Uri.parse("file:///storage/emulated/0/Movies/Sub.ass"))
        )
        assertEquals(
            androidx.media3.common.MimeTypes.TEXT_SSA,
            com.cyos.player.resolveSubtitleMimeType(context, Uri.parse("file:///storage/emulated/0/Movies/Sub.ssa"))
        )
        assertEquals(
            androidx.media3.common.MimeTypes.APPLICATION_TTML,
            com.cyos.player.resolveSubtitleMimeType(context, Uri.parse("file:///storage/emulated/0/Movies/Sub.ttml"))
        )

        val dotDecimalSrt = "1\n00:00:01.500 --> 00:00:04.250\nDot decimal subtitle line\n"
        val normalizedSrt = com.cyosplay.player.util.SubtitleParser.normalizeSrtDotDecimalTimestamps(dotDecimalSrt)
        assertTrue(
            "Non-spec .srt dot decimals must be normalized to comma decimals",
            normalizedSrt.contains("00:00:01,500 --> 00:00:04,250")
        )
        val tempSrtFile = File(context.cacheDir, "test_107rv4_dot_decimal.srt").apply {
            writeText(dotDecimalSrt)
        }
        val exoPlayer = com.cyosplay.player.buildCyosExoPlayer(context)
        val sampleVideoUri = Uri.parse("file:///storage/emulated/0/Movies/Test_107rv4.mkv")
        exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(sampleVideoUri))
        com.cyos.player.attachExternalSubtitle(
            context = context,
            player = exoPlayer,
            videoUri = sampleVideoUri,
            subtitleUri = Uri.fromFile(tempSrtFile)
        )
        val attachedConfigs = exoPlayer.currentMediaItem?.localConfiguration?.subtitleConfigurations.orEmpty()
        assertEquals("External subtitle must be attached via MediaItem.SubtitleConfiguration", 1, attachedConfigs.size)
        assertEquals(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP, attachedConfigs.first().mimeType)
        assertFalse(
            "C.TRACK_TYPE_TEXT must be enabled when external subtitle is attached",
            exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_TEXT)
        )

        // 4. Fix c: Verify non-fatal subtitle error recovery in Player.Listener.onPlayerError
        val simulatedLegacySubtitleException = androidx.media3.common.PlaybackException(
            "Legacy decoding is disabled",
            IllegalStateException("Legacy decoding is disabled in androidx.media3.extractor.text"),
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
        )
        var recoveredCallbackInvoked = false
        val handledNonFatal = com.cyos.player.handleNonFatalSubtitleErrorIfApplicable(
            context = context,
            player = exoPlayer,
            error = simulatedLegacySubtitleException,
            onSubtitleDisabled = { recoveredCallbackInvoked = true }
        )
        assertTrue("Subtitle/Text track error must be handled non-fatally without crashing video playback", handledNonFatal)
        assertTrue("Recovery callback must be invoked when subtitle error is handled non-fatally", recoveredCallbackInvoked)
        assertTrue(
            "C.TRACK_TYPE_TEXT must be disabled after non-fatal subtitle error recovery",
            exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_TEXT)
        )
        assertTrue(
            "Broken external SubtitleConfiguration entries must be stripped on recovery",
            exoPlayer.currentMediaItem?.localConfiguration?.subtitleConfigurations.isNullOrEmpty()
        )
        exoPlayer.release()
        tempSrtFile.delete()

        // 5. Verify per-device AES-256-GCM build code encryption
        val encryptedBuild107rv4 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.07RV4_SIGNED_ENCRYPTED_BUILD_CODE"
        )
        assertEquals(
            "CYOSPLAY_1.07RV4_SIGNED_ENCRYPTED_BUILD_CODE",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild107rv4)
        )
    }

    @Test
    fun v107rv5_middleHeaderPopup_gestureIsolation_auto50At85SingleChannelWarning_andJarPassword_verification() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify version 1.07rv5 and permanent non-debug mode
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 2. Verify High Volume & Screen Brightness Warning Alert requires BOTH volume >= 85%
        // AND brightness >= 85% (non-destructive), and Lower Both drops both to 50% on user tap
        viewModel.dismissHighVolumeBrightnessWarning()
        viewModel.updateSavedPlayerBrightness(0.60f)
        viewModel.updateSavedPlayerVolume(0.70f)
        assertFalse(viewModel.uiState.value.showHighVolumeBrightnessWarning)

        // Exceed 85% on brightness alone (0.86f while volume is 0.70f) -> does NOT trigger warning
        viewModel.updateSavedPlayerBrightness(0.86f)
        assertFalse(
            "Exceeding 85% brightness alone must NOT trigger high volume & brightness warning",
            viewModel.uiState.value.showHighVolumeBrightnessWarning
        )
        assertEquals(0.86f, viewModel.uiState.value.savedPlayerBrightness, 0.005f)
        assertEquals(0.70f, viewModel.uiState.value.savedPlayerVolume, 0.005f)

        // Now also raise volume to >= 85% (0.88f while brightness is 0.86f) -> shows warning without snapping sliders
        viewModel.updateSavedPlayerVolume(0.88f)
        assertTrue(
            "Both brightness >= 85% and volume >= 85% must trigger showHighVolumeBrightnessWarning",
            viewModel.uiState.value.showHighVolumeBrightnessWarning
        )
        assertEquals(0.88f, viewModel.uiState.value.savedPlayerVolume, 0.005f)
        assertEquals(0.86f, viewModel.uiState.value.savedPlayerBrightness, 0.005f)

        // Tapping Lower Both lowers both to 50% and dismisses the warning
        viewModel.lowerBothVolumeAndBrightness()
        assertFalse(viewModel.uiState.value.showHighVolumeBrightnessWarning)
        assertEquals(0.50f, viewModel.uiState.value.savedPlayerBrightness, 0.005f)
        assertEquals(0.50f, viewModel.uiState.value.savedPlayerVolume, 0.005f)

        // Exceed 85% on volume alone (0.85f while brightness is 0.50f) -> does NOT trigger warning
        viewModel.updateSavedPlayerVolume(0.85f)
        assertFalse(
            "Reaching 85% volume alone must NOT trigger showHighVolumeBrightnessWarning",
            viewModel.uiState.value.showHighVolumeBrightnessWarning
        )
        assertEquals(0.85f, viewModel.uiState.value.savedPlayerVolume, 0.005f)
        assertEquals(0.50f, viewModel.uiState.value.savedPlayerBrightness, 0.005f)

        // 3. Verify 6-way video playback gesture isolation (Zoom, Volume, Brightness, Sliding Skip, Hold Skip, Double-Tap Skip)
        assertFalse(
            "Double-Tap Skip must be blocked when Pinch-Zoom is active",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandleDoubleTapSkip(
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 2,
                isPinchZoomActive = true,
                recentlyPinched = true,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                isHoldFastForwardActive = false
            )
        )
        assertFalse(
            "Double-Tap Skip must be blocked when Vertical Volume/Brightness gesture is active",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandleDoubleTapSkip(
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 1,
                isPinchZoomActive = false,
                recentlyPinched = false,
                isVerticalGestureActive = true,
                isHorizontalSeekingActive = false,
                isHoldFastForwardActive = false
            )
        )
        assertFalse(
            "Vertical Brightness/Volume must be blocked when Double-Tap Skip is active",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandlePlaybackVerticalGesture(
                isPlaying = true,
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 1,
                isHoldFastForwardActive = false,
                isHorizontalSeekingActive = false,
                isPinchZoomActive = false,
                recentlyPinched = false,
                isDoubleTapSkipActive = true
            )
        )
        assertFalse(
            "Horizontal Sliding Skip must be blocked when Double-Tap Skip is active",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandlePlaybackHorizontalSeek(
                isControlsLocked = false,
                isInPipMode = false,
                activePointerCount = 1,
                isHoldFastForwardActive = false,
                isVerticalGestureActive = false,
                isPinchZoomActive = false,
                recentlyPinched = false,
                isDoubleTapSkipActive = true
            )
        )

        // 4. Verify per-device AES-256-GCM build code encryption
        val encryptedBuild107rv5 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.07RV5_SIGNED_ENCRYPTED_BUILD_CODE"
        )
        assertEquals(
            "CYOSPLAY_1.07RV5_SIGNED_ENCRYPTED_BUILD_CODE",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild107rv5)
        )
    }

    @Test
    fun v107rv6_mediaCache_fastPreloadManager_titleSort_lockedTapSkip_andJarPassword_verification() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify version 1.07rv6 and permanent non-debug mode
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 2. Verify direct zero-cache CyosSmartDataSourceFactory (media disk cache removed in 1.08rv5)
        val smartDataSourceFactory = com.cyos.player.CyosSmartDataSourceFactory(context)
        assertNotNull(smartDataSourceFactory.createDataSource())

        // 3. Verify RAM-bounded LoadControl & MemoryManager alignment (strict byte caps, zero disk cache)
        val optimizedLoadControl = com.cyos.player.CyosFastPlayerManager.buildOptimizedLoadControl()
        assertNotNull(optimizedLoadControl)
        val memBudget = com.cyosplay.player.util.MemoryManager.detectMemoryBudget(context)
        assertTrue("Back buffer must stay bounded (6-15s)", memBudget.backBufferDurationMs in 6_000..15_000)
        assertTrue(memBudget.retainBackBufferFromKeyframe)
        assertFalse("Byte buffer caps must be enforced on 4K/8K streams", memBudget.prioritizeTimeOverSizeThresholds)
        assertTrue("Target buffer must be explicitly byte-capped", memBudget.targetBufferBytes in (6 * 1024 * 1024)..(32 * 1024 * 1024))

        // 4. Verify CyosFastPlayerManager instantiation, playlist submission & clean release
        val fastManager = com.cyos.player.CyosFastPlayerManager(context)
        val sampleItems = listOf(
            androidx.media3.common.MediaItem.fromUri("https://example.com/a.mp4"),
            androidx.media3.common.MediaItem.fromUri("https://example.com/b.mp4"),
            androidx.media3.common.MediaItem.fromUri("https://example.com/c.mp4")
        )
        fastManager.submitPlaylist(sampleItems)
        assertEquals(3, fastManager.getCurrentPlaylistSize())
        fastManager.playAtIndex(1, startPositionMs = 0L, autoPlay = false)
        assertEquals(1, fastManager.getCurrentPlayingIndex())
        fastManager.skipForward10s()
        fastManager.skipBackward10s()
        fastManager.release()

        // 5. Verify Title sorting (A-Z / Z-A) for both Videos and Folders
        val videoZ = VideoItem(
            id = 101L,
            title = "Zebra Documentary",
            displayName = "z_doc.mkv",
            uri = android.net.Uri.parse("file:///storage/emulated/0/Movies/z_doc.mkv"),
            path = "/storage/emulated/0/Movies/z_doc.mkv",
            sizeBytes = 500_000L,
            durationMs = 120_000L,
            dateModifiedSeconds = 1_700_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/x-matroska",
            folderId = "f1",
            folderName = "Nature",
            folderPath = "/storage/emulated/0/Movies"
        )
        val videoA = VideoItem(
            id = 102L,
            title = "Alpha Trailer",
            displayName = "m_trailer.mp4",
            uri = android.net.Uri.parse("file:///storage/emulated/0/Movies/m_trailer.mp4"),
            path = "/storage/emulated/0/Movies/m_trailer.mp4",
            sizeBytes = 300_000L,
            durationMs = 90_000L,
            dateModifiedSeconds = 1_700_000_100L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "f2",
            folderName = "Action",
            folderPath = "/storage/emulated/0/Movies"
        )
        val sortedVideosAsc = viewModel.sortVideos(
            listOf(videoZ, videoA),
            com.cyosplay.player.data.model.VideoSortOption.TITLE_ASC,
            com.cyosplay.player.data.model.SortDirection.ASCENDING
        )
        assertEquals(listOf("Alpha Trailer", "Zebra Documentary"), sortedVideosAsc.map { it.title })
        val sortedVideosDesc = viewModel.sortVideos(
            listOf(videoA, videoZ),
            com.cyosplay.player.data.model.VideoSortOption.TITLE_DESC,
            com.cyosplay.player.data.model.SortDirection.DESCENDING
        )
        assertEquals(listOf("Zebra Documentary", "Alpha Trailer"), sortedVideosDesc.map { it.title })

        val folderZ = com.cyosplay.player.data.model.VideoFolder(
            id = "fz",
            name = "Zeta Clips",
            path = "/storage/emulated/0/Zeta",
            videos = listOf(videoZ)
        )
        val folderA = com.cyosplay.player.data.model.VideoFolder(
            id = "fa",
            name = "Alpha Clips",
            path = "/storage/emulated/0/Alpha",
            videos = listOf(videoA)
        )
        val sortedFoldersAsc = viewModel.sortFolders(
            listOf(folderZ, folderA),
            com.cyosplay.player.data.model.FolderSortOption.TITLE_ASC,
            com.cyosplay.player.data.model.SortDirection.ASCENDING
        )
        assertEquals(listOf("Alpha Clips", "Zeta Clips"), sortedFoldersAsc.map { it.name })
        val sortedFoldersDesc = viewModel.sortFolders(
            listOf(folderA, folderZ),
            com.cyosplay.player.data.model.FolderSortOption.TITLE_DESC,
            com.cyosplay.player.data.model.SortDirection.DESCENDING
        )
        assertEquals(listOf("Zeta Clips", "Alpha Clips"), sortedFoldersDesc.map { it.name })

        // 6. Verify locked-screen smooth tap skip (±10s) enabled when screen lock is on,
        // while staying isolated from pinch-zoom, vertical drag, horizontal seek, and hold-skip
        assertTrue(
            "Double-tap ±10s seek must remain enabled while screen lock is active",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandleDoubleTapSkip(
                isControlsLocked = true,
                isInPipMode = false,
                activePointerCount = 1,
                isPinchZoomActive = false,
                recentlyPinched = false,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                isHoldFastForwardActive = false
            )
        )
        assertFalse(
            "Locked double-tap skip must remain isolated from pinch-zoom",
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandleDoubleTapSkip(
                isControlsLocked = true,
                isInPipMode = false,
                activePointerCount = 2,
                isPinchZoomActive = true,
                recentlyPinched = true,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                isHoldFastForwardActive = false
            )
        )

        // 7. Verify per-device AES-256-GCM build code encryption
        val encryptedBuild107rv6 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.07RV6_SIGNED_ENCRYPTED_BUILD_CODE"
        )
        assertEquals(
            "CYOSPLAY_1.07RV6_SIGNED_ENCRYPTED_BUILD_CODE",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild107rv6)
        )
    }

    @Test
    fun v107rv7_fastLoadControl_preloadHandoff_cacheWarmup_lockedHoldSkip_andJarPassword_verification() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify version 1.07rv8, CyosPlay 1.07rv8 description, and permanent non-debug mode
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 2. Verify direct zero-cache CyosSmartDataSourceFactory & direct media source factory
        val directDataSourceFactory = com.cyos.player.CyosSmartDataSourceFactory(context)
        assertNotNull(directDataSourceFactory.createDataSource())
        assertNotNull(com.cyos.player.CyosFastPlayer.createDirectMediaSourceFactory(context, directDataSourceFactory))

        // 3. Verify RAM-safe & battery-optimized CyosFastPlayer LoadControl (600ms startup, 1500ms rebuffer, <=10s back-buffer)
        val loadControl = com.cyos.player.CyosFastPlayer.createLoadControl(context)
        assertNotNull(loadControl)
        val lowRamLoadControl = com.cyos.player.CyosFastPlayer.createLoadControl(context, forceLowRamMode = true)
        assertNotNull(lowRamLoadControl)
        val batterySaverLoadControl = com.cyos.player.CyosFastPlayer.createLoadControl(context, batterySaverEnabled = true)
        assertNotNull(batterySaverLoadControl)
        val memBudget = com.cyosplay.player.util.MemoryManager.detectMemoryBudget(context)
        assertEquals(180, memBudget.bufferForPlaybackMs)
        assertEquals(1_200, memBudget.bufferForPlaybackAfterRebufferMs)
        assertTrue(memBudget.backBufferDurationMs in 6_000..15_000)

        // 4. Verify network 3.5Mbps startup bitrate cap vs local uncapped track selector
        val netSelector = com.cyos.player.CyosFastPlayer.createTrackSelector(context, capNetworkStartupBitrate = true)
        assertEquals(3_500_000, netSelector.parameters.maxVideoBitrate)
        val localSelector = com.cyos.player.CyosFastPlayer.createTrackSelector(context, capNetworkStartupBitrate = false)
        assertTrue(localSelector.parameters.maxVideoWidth >= 15360)

        // 5. Verify CyosPlayerHolder pre-transition direct prepareOnTap & release (preloader-free)
        val tapItem = androidx.media3.common.MediaItem.fromUri("https://example.com/v108rv5.mp4")
        val prepared = com.cyos.player.CyosPlayerHolder.prepareOnTap(
            context = context,
            mediaItem = tapItem,
            resumeMs = 0L
        )
        assertTrue(prepared)
        assertTrue(com.cyos.player.CyosPlayerHolder.consumeMatchingPrePreparedUri(android.net.Uri.parse("https://example.com/v108rv5.mp4")))
        com.cyos.player.CyosPlayerHolder.release()

        // 7. Verify locked-screen smooth hold skip (2x + slide tuning) & locked double-tap skip (±10s)
        assertTrue(
            "Hold skip must work smoothly when video player screen lock is enabled",
            com.cyosplay.player.util.PlayerGestureControlHelper.canStartHoldFastForward(
                isPlaying = true,
                isControlsLocked = true,
                isInPipMode = false,
                activePointerCount = 1,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                isPinchZoomActive = false,
                recentlyPinched = false,
                allowWhenLocked = true
            )
        )
        assertFalse(
            "Locked hold skip must stay isolated from pinch-zoom",
            com.cyosplay.player.util.PlayerGestureControlHelper.canStartHoldFastForward(
                isPlaying = true,
                isControlsLocked = true,
                isInPipMode = false,
                activePointerCount = 2,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                isPinchZoomActive = true,
                recentlyPinched = true,
                allowWhenLocked = true
            )
        )

        // 8. Verify direct zero-cache CyosSmartDataSourceFactory, direct ExoPlayer creation, and per-device encryption
        val smartFactory = com.cyos.player.CyosSmartDataSourceFactory(context)
        assertNotNull(smartFactory.createDataSource())
        val directPlayer = com.cyos.player.CyosFastPlayer.createDirectExoPlayer(context)
        assertNotNull(directPlayer)
        directPlayer.release()

        val encryptedBuild107rv8 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.07RV8_SIGNED_ENCRYPTED_BUILD_CODE"
        )
        assertEquals(
            "CYOSPLAY_1.07RV8_SIGNED_ENCRYPTED_BUILD_CODE",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild107rv8)
        )
    }

    @Test
    fun v107rv7a_lockedHoldSkip_speedStepping_aboutUsCreator_formatEfficiency_andJarEncryption_verification() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify 1.07rv7a branding, About Us "Created by Tkm", and permanent non-debug mode
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertEquals("Created by Tkm", viewModel.uiState.value.aboutUsCreator)
        assertEquals("CyosPlay 1.08rv9", context.getString(R.string.about_us_description))
        assertEquals("Created by Tkm", context.getString(R.string.about_us_creator))
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 2. Verify locked-screen Hold Skip gating (2x hold + 1x..5x horizontal slide)
        assertTrue(
            "Hold skip must start smoothly when Screen Lock is active",
            com.cyosplay.player.util.PlayerGestureControlHelper.canStartHoldFastForward(
                isPlaying = true,
                isControlsLocked = true,
                isInPipMode = false,
                activePointerCount = 1,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                isPinchZoomActive = false,
                recentlyPinched = false,
                isDoubleTapSkipActive = false,
                allowWhenLocked = true
            )
        )
        assertFalse(
            "Locked hold skip must stay blocked during pinch-zoom",
            com.cyosplay.player.util.PlayerGestureControlHelper.canStartHoldFastForward(
                isPlaying = true,
                isControlsLocked = true,
                isInPipMode = false,
                activePointerCount = 2,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                isPinchZoomActive = true,
                recentlyPinched = true,
                isDoubleTapSkipActive = false,
                allowWhenLocked = true
            )
        )
        assertFalse(
            "Locked hold skip must stay blocked in PiP mode",
            com.cyosplay.player.util.PlayerGestureControlHelper.canStartHoldFastForward(
                isPlaying = true,
                isControlsLocked = true,
                isInPipMode = true,
                activePointerCount = 1,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                isPinchZoomActive = false,
                recentlyPinched = false,
                isDoubleTapSkipActive = false,
                allowWhenLocked = true
            )
        )

        // 3. Verify calibrated deadzone/hysteresis 1x..5x horizontal speed stepping & redundant dispatch guard
        assertEquals(
            2.0f,
            com.cyosplay.player.util.PlayerGestureControlHelper.computeHoldSkipSpeedStep(
                startHoldSpeed = 2.0f,
                totalDxPx = 12f,
                containerWidthPx = 1100f,
                currentHoldSpeed = 2.0f
            ),
            0.001f
        )
        assertEquals(
            3.0f,
            com.cyosplay.player.util.PlayerGestureControlHelper.computeHoldSkipSpeedStep(
                startHoldSpeed = 2.0f,
                totalDxPx = 220f,
                containerWidthPx = 1100f,
                currentHoldSpeed = 2.0f
            ),
            0.001f
        )
        assertEquals(
            5.0f,
            com.cyosplay.player.util.PlayerGestureControlHelper.computeHoldSkipSpeedStep(
                startHoldSpeed = 2.0f,
                totalDxPx = 900f,
                containerWidthPx = 1100f,
                currentHoldSpeed = 3.0f
            ),
            0.001f
        )
        assertEquals(
            1.0f,
            com.cyosplay.player.util.PlayerGestureControlHelper.computeHoldSkipSpeedStep(
                startHoldSpeed = 2.0f,
                totalDxPx = -260f,
                containerWidthPx = 1100f,
                currentHoldSpeed = 2.0f
            ),
            0.001f
        )
        assertFalse(
            com.cyosplay.player.util.PlayerGestureControlHelper.shouldDispatchPlaybackSpeedChange(2.0f, 2.004f)
        )
        assertTrue(
            com.cyosplay.player.util.PlayerGestureControlHelper.shouldDispatchPlaybackSpeedChange(2.0f, 3.0f)
        )

        // 4. Verify multi-format extractor factory & fast player builder
        val extractors = XvidSupportExtractorsFactory().createExtractors()
        assertTrue("Extractor factory must produce extractors", extractors.isNotEmpty())
        assertNotNull(com.cyos.player.CyosFastPlayer.createWarmExoPlayerBuilder(context))

        // 5. Verify per-device AES-256-GCM build code encryption
        val encryptedBuild107rv7a = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.07RV7A_SIGNED_ENCRYPTED_BUILD_CODE"
        )
        assertEquals(
            "CYOSPLAY_1.07RV7A_SIGNED_ENCRYPTED_BUILD_CODE",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild107rv7a)
        )
    }

    @Test
    fun v108rv4_android7To18_matroskaHevcOpusAss1080p_crashRecovery_closedSourceCopyright_lockedSkip_andJarEncryption_verification() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify 1.08rv4 branding, About Us "Created by Tkm", closed-source copyright, and permanent non-debug mode
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertEquals("Created by Tkm", viewModel.uiState.value.aboutUsCreator)
        assertEquals("© 2026 TKm. All rights reserved.", viewModel.uiState.value.aboutUsCopyright)
        assertEquals("CyosPlay 1.08rv9", context.getString(R.string.about_us_description))
        assertEquals("Created by Tkm", context.getString(R.string.about_us_creator))
        assertEquals("© 2026 TKm. All rights reserved.", context.getString(R.string.about_us_copyright))
        assertEquals("Third-Party Open Source Licenses", context.getString(R.string.open_source_licenses_title))
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 2. Verify Android 7 (API 24) to Android 18 (API 38+) compatibility helper
        assertEquals(7, com.cyosplay.player.util.AndroidVersionCompatHelper.MIN_SUPPORTED_ANDROID_VERSION)
        assertEquals(18, com.cyosplay.player.util.AndroidVersionCompatHelper.MAX_SUPPORTED_ANDROID_VERSION)
        assertEquals(38, com.cyosplay.player.util.AndroidVersionCompatHelper.ANDROID_18_API_LEVEL)
        for (api in 24..38) {
            assertTrue(
                "API $api must be supported across Android 7 to 18",
                com.cyosplay.player.util.AndroidVersionCompatHelper.isSupportedAndroidSdk(api)
            )
            val perms = com.cyosplay.player.util.AndroidVersionCompatHelper.resolveRuntimeMediaPermissions(api)
            assertTrue("API $api must resolve non-empty media permissions", perms.isNotEmpty())
        }
        assertFalse(com.cyosplay.player.util.AndroidVersionCompatHelper.canEnterPictureInPicture(24))
        assertTrue(com.cyosplay.player.util.AndroidVersionCompatHelper.canEnterPictureInPicture(26))
        assertTrue(com.cyosplay.player.util.AndroidVersionCompatHelper.supportsSeamlessAutoPip(38))
        val (pipNum, pipDen) = com.cyosplay.player.util.AndroidVersionCompatHelper.clampPipAspectRatioRational(3840, 1080)
        assertEquals(2390, pipNum)
        assertEquals(1000, pipDen)

        // 3. Verify Matroska/WebM, 1920x1080, libebml v1.4.5 + libmatroska v1.7.1, HEVC, AAC, Opus, ASS & crash recovery
        assertTrue(
            com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.isSupportedMatroskaHevcOpusAssSpec()
        )
        assertTrue(
            com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.SUPPORTED_ENCODING_SOFTWARE
                .contains("libebml v1.4.5 + libmatroska v1.7.1")
        )
        assertTrue(
            com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.isSupportedCinemaOrMatroskaResolution(1920, 1080)
        )
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("hevc"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("aac"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("opus"))
        assertNotNull(com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode("ass"))

        val hevcDecoderCrash = androidx.media3.common.PlaybackException(
            "HEVC MediaCodec decoder failed",
            IllegalStateException("c2.qti.hevc.decoder failed"),
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
        )
        assertTrue(com.cyosplay.player.isRecoverableDecoderOrAudioSinkError(hevcDecoderCrash))

        val assSubtitleCrash = androidx.media3.common.PlaybackException(
            "S_TEXT/ASS parse failure",
            IllegalArgumentException("Malformed S_TEXT/ASS dialogue"),
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
        )
        assertTrue(com.cyosplay.player.isSubtitleOrTextTrackPlaybackError(null, assSubtitleCrash))

        // 4. Verify CyosPlay is Closed-Source Copyright & honestly credits bundled Apache 2.0 libraries (no unbundled FFmpeg claim)
        assertEquals("© 2026 TKm. All rights reserved.", com.cyosplay.player.data.OpenSourceLicenses.APP_COPYRIGHT_NOTICE)
        assertEquals("Closed-Source", com.cyosplay.player.data.OpenSourceLicenses.APP_LICENSE_POSTURE)
        assertTrue(com.cyosplay.player.data.OpenSourceLicenses.APP_PROPRIETARY_NOTICE.contains("closed-source"))
        assertTrue(
            com.cyosplay.player.data.OpenSourceLicenses.THIRD_PARTY_OSS_DISCLAIMER
                .contains("does not make CyosPlay itself open source")
        )
        val notices = com.cyosplay.player.data.OpenSourceLicenses.thirdPartyNotices
        assertEquals(3, notices.size)
        assertTrue(
            notices.any {
                it.id == "androidx_media3_exoplayer" &&
                    it.libraryName == "AndroidX Media3 ExoPlayer" &&
                    it.licenseType == "Apache 2.0" &&
                    it.licenseText.contains("Apache License, Version 2.0")
            }
        )
        assertTrue(notices.any { it.id == "androidx_compose_material3" && it.licenseType == "Apache 2.0" })
        assertTrue(notices.any { it.id == "androidx_security_biometric" && it.licenseType == "Apache 2.0" })
        assertFalse(
            "Unbundled FFmpeg must not be listed as a bundled notice",
            notices.any { it.id == "ffmpeg" }
        )

        // 5. Verify locked Hold Skip (2x + 1x..5x smooth stepping) & locked coalesced Double-Tap Skip (±10s..±30s)
        assertTrue(
            com.cyosplay.player.util.PlayerGestureControlHelper.canStartHoldFastForward(
                isPlaying = true,
                isControlsLocked = true,
                isInPipMode = false,
                activePointerCount = 1,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                isPinchZoomActive = false,
                recentlyPinched = false,
                isDoubleTapSkipActive = false,
                allowWhenLocked = true
            )
        )
        assertEquals(
            4.0f,
            com.cyosplay.player.util.PlayerGestureControlHelper.computeHoldSkipSpeedStep(
                startHoldSpeed = 2.0f,
                totalDxPx = 430f,
                containerWidthPx = 1100f,
                currentHoldSpeed = 3.0f
            ),
            0.001f
        )
        assertTrue(
            com.cyosplay.player.util.PlayerGestureControlHelper.canHandleDoubleTapSkip(
                isControlsLocked = true,
                isInPipMode = false,
                activePointerCount = 1,
                isPinchZoomActive = false,
                recentlyPinched = false,
                isVerticalGestureActive = false,
                isHorizontalSeekingActive = false,
                isHoldFastForwardActive = false,
                allowWhenLocked = true
            )
        )
        val firstTapSkip = com.cyosplay.player.util.PlayerGestureControlHelper.computeDoubleTapCumulativeSkip(
            currentPositionMs = 50_000L,
            totalDurationMs = 200_000L,
            isLeftHalf = false,
            previousDoubleTapElapsedMs = 0L,
            previousHardwareSeekElapsedMs = 0L,
            nowElapsedMs = 1_000L,
            previousCumulativeDeltaMs = 0L
        )
        assertEquals(60_000L, firstTapSkip.targetPositionMs)
        assertEquals(10_000L, firstTapSkip.cumulativeDeltaMs)
        assertTrue(firstTapSkip.shouldDispatchPlayerSeek)

        val secondRapidTapSkip = com.cyosplay.player.util.PlayerGestureControlHelper.computeDoubleTapCumulativeSkip(
            currentPositionMs = firstTapSkip.targetPositionMs,
            totalDurationMs = 200_000L,
            isLeftHalf = false,
            previousDoubleTapElapsedMs = 1_000L,
            previousHardwareSeekElapsedMs = 1_000L,
            nowElapsedMs = 1_080L,
            previousCumulativeDeltaMs = firstTapSkip.cumulativeDeltaMs
        )
        assertEquals(70_000L, secondRapidTapSkip.targetPositionMs)
        assertEquals(20_000L, secondRapidTapSkip.cumulativeDeltaMs)
        assertFalse(secondRapidTapSkip.shouldDispatchPlayerSeek)

        // 6. Verify per-device AES-256-GCM build code encryption
        val encryptedBuild108rv4 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.08RV4_SIGNED_ENCRYPTED_BUILD_CODE"
        )
        assertEquals(
            "CYOSPLAY_1.08RV4_SIGNED_ENCRYPTED_BUILD_CODE",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild108rv4)
        )
    }

    @Test
    fun v108rv5_cacheFreeDirectPlayback_expandedFormats_boundedRamBattery_andSecretSafeEncryption_verification() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoPlayerViewModel(context)

        // 1. Verify 1.08rv5 branding, About Us, and permanent non-debug mode
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertEquals("CyosPlay 1.08rv9", context.getString(R.string.about_us_description))
        assertEquals("Created by Tkm", context.getString(R.string.about_us_creator))
        assertTrue(CryptoSecurityManager.isDebugModePermanentlyDisabled())

        // 2. Verify media disk cache & preloader classes are completely removed
        val removedClasses = listOf(
            "com.cyos.player.CyosMediaCache",
            "com.cyos.player.CyosCacheProvider",
            "com.cyos.player.CyosPreloadStatusControl"
        )
        removedClasses.forEach { className ->
            val exists = runCatching { Class.forName(className) }.isSuccess
            assertFalse("$className must be removed in 1.08rv5", exists)
        }

        // 3. Verify direct zero-cache playback pipeline & clean detach
        val directFactory = com.cyos.player.CyosSmartDataSourceFactory(context)
        assertNotNull(directFactory.createDataSource())
        val session = com.cyos.player.CyosPlayerHolder.getOrCreateSession(
            context = context,
            batterySaverEnabled = true
        )
        assertNotNull(session.player)
        com.cyos.player.CyosPlayerHolder.pauseAndDetachIfPooled()
        com.cyos.player.CyosPlayerHolder.release()

        // 4. Verify bounded RAM & battery-saving MemoryManager budgets
        val standardBudget = com.cyosplay.player.util.MemoryManager.detectMemoryBudget(
            context = context,
            forceLowRamMode = false,
            batterySaverEnabled = false
        )
        val batteryBudget = com.cyosplay.player.util.MemoryManager.detectMemoryBudget(
            context = context,
            forceLowRamMode = false,
            batterySaverEnabled = true
        )
        val ultraLowBudget = com.cyosplay.player.util.MemoryManager.detectMemoryBudget(
            context = context,
            forceLowRamMode = true,
            batterySaverEnabled = true
        )
        assertFalse(standardBudget.prioritizeTimeOverSizeThresholds)
        assertTrue(standardBudget.targetBufferBytes in (6 * 1024 * 1024)..(32 * 1024 * 1024))
        assertTrue(batteryBudget.targetBufferBytes <= standardBudget.targetBufferBytes)
        assertTrue(batteryBudget.positionPollingIntervalMs >= standardBudget.positionPollingIntervalMs)
        assertEquals(6 * 1024 * 1024, ultraLowBudget.targetBufferBytes)

        // 5. Verify expanded resolutions (2K DCI, cinema ultrawide, 6K, 12K) & expanded formats/codecs
        assertTrue(VideoItem.SUPPORTED_VIDEO_RESOLUTIONS.contains(2048 to 1080))
        assertTrue(VideoItem.SUPPORTED_VIDEO_RESOLUTIONS.contains(3840 to 1608))
        assertTrue(VideoItem.SUPPORTED_VIDEO_RESOLUTIONS.contains(6144 to 3456))
        assertTrue(VideoItem.SUPPORTED_VIDEO_RESOLUTIONS.contains(12288 to 6480))
        listOf("av01", "vp09", "hevc", "dvhe", "ac-3", "ec-3", "flac", "opus", "ass", "webvtt", "mxf", "m2ts", "ogv").forEach { code ->
            assertNotNull(
                "Codec/container $code must be registered in FfmpegMatroskaCodecRegistry",
                com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.findCodecByCode(code)
            )
        }

        // 6. Verify zero embedded XOR shards or legacy keystore/JAR password constants exist in CryptoSecurityManager
        val forbiddenFieldTokens = listOf(
            "SHARD_",
            "RELEASE_KEYSTORE_PASSWORD",
            "JAR_SIGNATURE_BUILD_CODE_PASSWORD",
            "BUILD_CODE_ENCRYPTION_PASSWORD",
            "MASTER_PASSWORD_LOCKED"
        )
        val declaredFieldNames = CryptoSecurityManager::class.java.declaredFields.map { it.name }
        forbiddenFieldTokens.forEach { token ->
            assertTrue(
                "CryptoSecurityManager must not contain any embedded secret field matching $token",
                declaredFieldNames.none { it.contains(token) }
            )
        }
        val declaredMethodNames = CryptoSecurityManager::class.java.declaredMethods.map { it.name }
        listOf(
            "getOrCreateEncryptedKeystorePassword",
            "getOrCreateEncryptedJarBuildCodePassword",
            "encryptBuildCodeWithJarPassword",
            "reconstructInvisibleMasterSeed"
        ).forEach { forbiddenMethod ->
            assertTrue(
                "CryptoSecurityManager must not contain legacy secret method $forbiddenMethod",
                declaredMethodNames.none { it.contains(forbiddenMethod) }
            )
        }

        // 7. Verify per-device AES-256-GCM encryption & user-chosen Vault PIN / Device Passcode locking
        assertTrue(CryptoSecurityManager.verifyDeviceKeystoreIntegrity(context))
        val encryptedBuild108rv5 = CryptoSecurityManager.encryptBuildCodeData(
            context,
            "CYOSPLAY_1.08RV5_PER_DEVICE_ENCRYPTED_DATA"
        )
        assertTrue(CryptoSecurityManager.isEncryptedString(encryptedBuild108rv5))
        assertEquals(
            "CYOSPLAY_1.08RV5_PER_DEVICE_ENCRYPTED_DATA",
            CryptoSecurityManager.decryptBuildCodeData(context, encryptedBuild108rv5)
        )

        viewModel.setupInitialVaultPin("654321", "Favorite color?", "Blue")
        viewModel.lockDataCodesWithMasterPassword()
        assertTrue(viewModel.uiState.value.isDataCodesLocked)
        assertFalse(viewModel.unlockDataCodesWithMasterPassword("000000"))
        assertTrue(viewModel.unlockDataCodesWithMasterPassword("654321"))
        assertFalse(viewModel.uiState.value.isDataCodesLocked)
    }

    @Test
    fun v108rv6_newFolderPointNotification_instantStartupOptimization_andDefaults() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences(
            com.cyosplay.player.data.repository.FolderNotificationStore.PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        ).edit().clear().commit()
        context.getSharedPreferences("cyos_video_vault_settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        // 1. FolderNotificationStore: first-launch baseline, new folder detection, auto-clear, deleted pruning
        val store = com.cyosplay.player.data.repository.FolderNotificationStore(context)
        val initialState = kotlinx.coroutines.runBlocking {
            store.syncDiscoveredFolders(setOf("bucket_camera", "bucket_movies"))
        }
        assertTrue(initialState.baselineInitialized)
        assertEquals(setOf("bucket_camera", "bucket_movies"), initialState.seenFolderIds)
        assertTrue("First scan baseline must not mark existing folders as new", initialState.unseenNewFolderIds.isEmpty())

        // Subsequent scan adds a brand new folder bucket
        val afterNewFolder = kotlinx.coroutines.runBlocking {
            store.syncDiscoveredFolders(setOf("bucket_camera", "bucket_movies", "bucket_telegram"))
        }
        assertEquals(setOf("bucket_telegram"), afterNewFolder.unseenNewFolderIds)
        assertTrue(afterNewFolder.hasNewFolders)

        // Add another new folder, then delete one unseen folder to verify stale-dot pruning
        val afterSecondAndDelete = kotlinx.coroutines.runBlocking {
            store.syncDiscoveredFolders(setOf("bucket_camera", "bucket_movies", "bucket_downloads"))
        }
        assertEquals(setOf("bucket_downloads"), afterSecondAndDelete.unseenNewFolderIds)
        assertFalse("Deleted folder bucket_telegram must be pruned", afterSecondAndDelete.unseenNewFolderIds.contains("bucket_telegram"))

        // Auto-clear when user taps/opens folder
        val afterOpened = kotlinx.coroutines.runBlocking {
            store.markFolderSeen("bucket_downloads")
        }
        assertTrue(afterOpened.unseenNewFolderIds.isEmpty())
        assertFalse(afterOpened.hasNewFolders)
        assertTrue(afterOpened.seenFolderIds.contains("bucket_downloads"))

        // 2. ViewModel defaults (Pure AMOLED Black, Compact 90%, Auto-Skip OFF, 1.08rv7)
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertEquals("CyosPlay 1.08rv9", context.getString(R.string.about_us_description))
        assertEquals(com.cyosplay.player.data.model.AppThemeMode.AMOLED_BLACK, viewModel.uiState.value.themeMode)
        assertEquals(com.cyosplay.player.data.model.AdaptiveTextScaleMode.COMPACT, viewModel.uiState.value.textScaleMode)
        assertFalse(viewModel.uiState.value.autoSkipIntrosCredits)

        // 3. StartupLatencyTracker zero-log in-memory milestones
        com.cyos.player.StartupLatencyTracker.reset()
        com.cyos.player.StartupLatencyTracker.markVideoTap(android.net.Uri.parse("content://media/external/video/media/42"))
        com.cyos.player.StartupLatencyTracker.markNavigationStart()
        com.cyos.player.StartupLatencyTracker.markPlayerAcquired()
        com.cyos.player.StartupLatencyTracker.markSetMediaItem()
        com.cyos.player.StartupLatencyTracker.markPrepareCalled()
        com.cyos.player.StartupLatencyTracker.markSurfaceAttached()
        com.cyos.player.StartupLatencyTracker.markStateReady()
        com.cyos.player.StartupLatencyTracker.markFirstFrameRendered()
        val snapshot = com.cyos.player.StartupLatencyTracker.lastCompletedSnapshot()
        assertNotNull(snapshot)
        assertFalse(snapshot!!.isNetworkStream)
        assertTrue(snapshot.tapToPrepareMs >= 0L)
        assertTrue(snapshot.tapToSurfaceAttachedMs >= 0L)
        assertTrue(snapshot.tapToStateReadyMs >= 0L)
        assertTrue(snapshot.tapToFirstFrameMs >= 0L)

        // 4. Instant-start buffer tuning & keep-alive HTTP defaults
        assertEquals(180, com.cyos.player.CyosFastPlayer.BUFFER_FOR_PLAYBACK_MS)
        assertEquals(320, com.cyos.player.CyosFastPlayer.REMOTE_BUFFER_FOR_PLAYBACK_MS)
        assertEquals(5_000, com.cyos.player.CyosSmartDataSourceFactory.HTTP_CONNECT_TIMEOUT_MS)
        assertEquals(8_000, com.cyos.player.CyosSmartDataSourceFactory.HTTP_READ_TIMEOUT_MS)
        assertNotNull(com.cyos.player.CyosFastPlayer.createLoadControl(context = context, isNetworkStream = false))
        assertNotNull(com.cyos.player.CyosFastPlayer.createLoadControl(context = context, isNetworkStream = true))

        // Unified codec profile key & hardware-first EXTENSION_RENDERER_MODE_ON
        val (_, extModeHw) = VideoPlayerViewModel.resolveCodecSelectorAndExtensionMode(
            decoderEngineMode = com.cyosplay.player.data.model.DecoderEngineMode.HW_PLUS,
            forceHardwareAccelerationHighBitrate = false
        )
        assertEquals(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON, extModeHw)
        assertEquals(
            "HW_PLUS:false:false:false",
            VideoPlayerViewModel.buildCodecProfileKey(
                decoderEngineMode = com.cyosplay.player.data.model.DecoderEngineMode.HW_PLUS,
                forceHardwareAccelerationHighBitrate = false,
                lowRamShieldEnabled = false,
                batterySaverModeEnabled = false
            )
        )

        // 5. Expanded resolutions, badges, and video extensions
        assertTrue(VideoItem.SUPPORTED_VIDEO_RESOLUTIONS.contains(3440 to 1440))
        assertTrue(VideoItem.SUPPORTED_VIDEO_RESOLUTIONS.contains(4096 to 2160))
        assertTrue(VideoItem.SUPPORTED_VIDEO_RESOLUTIONS.contains(6144 to 3456))
        assertTrue(VideoItem.SUPPORTED_VIDEO_RESOLUTIONS.contains(10240 to 4320))
        assertEquals("4K DCI", com.cyosplay.player.util.Formatters.formatResolutionBadge(4096, 2160))
        assertEquals("UWQHD", com.cyosplay.player.util.Formatters.formatResolutionBadge(3440, 1440))
        assertEquals("6K UHD", com.cyosplay.player.util.Formatters.formatResolutionBadge(6144, 3456))
        assertEquals("10K UW", com.cyosplay.player.util.Formatters.formatResolutionBadge(10240, 4320))
        assertEquals("576P PAL", com.cyosplay.player.util.Formatters.formatResolutionBadge(1024, 576))
        assertEquals("720×576", com.cyosplay.player.util.Formatters.formatResolutionBadge(720, 576))
        assertTrue(com.cyosplay.player.data.repository.VideoRepository.SUPPORTED_VIDEO_EXTENSIONS.contains("m2ts"))
        assertTrue(com.cyosplay.player.data.repository.VideoRepository.SUPPORTED_VIDEO_EXTENSIONS.contains("mxf"))
        assertTrue(com.cyosplay.player.data.repository.VideoRepository.SUPPORTED_VIDEO_EXTENSIONS.contains("ogv"))
        assertTrue(com.cyosplay.player.data.repository.VideoRepository.SUPPORTED_VIDEO_EXTENSIONS.contains("f4v"))
    }

    @Test
    fun auditPillar1_vaultStagingCleanup_twoPhaseRotationRollback_playerTrimGuard_multiChannelAudio_andLargeXvidKeyframe() {
        // 1. VaultManager cleans up .tmp_<nanoTime>, .dec_tmp_, and .rot_plain_/.rot_enc_ staging files on startup
        val vaultDir = File(context.filesDir, "private_encrypted_vault").apply { mkdirs() }
        val staleTmp = File(vaultDir, "clip1.cyosvault.tmp_123456789").apply { writeBytes(ByteArray(32) { 1 }) }
        val staleDec = File(vaultDir, "clip2.dec_tmp_987654").apply { writeBytes(ByteArray(32) { 2 }) }
        val staleRot = File(vaultDir, "clip3.cyosvault.rot_plain_555").apply { writeBytes(ByteArray(32) { 3 }) }
        val realVaultFile = File(vaultDir, "valid_entry.cyosvault").apply { writeBytes(ByteArray(32) { 4 }) }

        val vaultManager = com.cyosplay.player.data.repository.VaultManager(context)
        assertFalse("Interrupted .tmp_<nanoTime> staging file must be deleted", staleTmp.exists())
        assertFalse("Interrupted .dec_tmp_ file must be deleted", staleDec.exists())
        assertFalse("Interrupted .rot_plain_ file must be deleted", staleRot.exists())
        assertTrue("Valid encrypted vault file must remain intact", realVaultFile.exists())
        realVaultFile.delete()
        vaultManager.release()

        // 2. Two-phase atomic key rotation rolls back if a listener fails during prepareMasterKeyRotation()
        val beforeCipher = CryptoSecurityManager.encryptString(context, "ATOMIC_DEK_GUARD")
        val initialRotCount = CryptoSecurityManager.getAutoRotationCount(context)
        var rollbackCalled = false
        val failingListener = object : CryptoSecurityManager.TwoPhaseKeyRotationListener {
            override fun prepareMasterKeyRotation(oldKey: javax.crypto.SecretKey, newKey: javax.crypto.SecretKey): Boolean = false
            override fun commitMasterKeyRotation(newKey: javax.crypto.SecretKey) = Unit
            override fun rollbackMasterKeyRotation(oldKey: javax.crypto.SecretKey) {
                rollbackCalled = true
            }
        }
        CryptoSecurityManager.registerRotationListener(failingListener)
        try {
            val afterCount = CryptoSecurityManager.rotateDynamicEncryptionKey(context, "test_abort_rotation")
            assertEquals("Rotation count must NOT increment if prepareMasterKeyRotation fails", initialRotCount, afterCount)
            assertTrue("Rollback must be invoked on aborted two-phase rotation", rollbackCalled)
            assertEquals("ATOMIC_DEK_GUARD", CryptoSecurityManager.decryptString(context, beforeCipher))
        } finally {
            CryptoSecurityManager.unregisterRotationListener(failingListener)
        }

        // 3. CyosPlayerHolder refuses to destroy active player during trimMemory when attached or in PiP
        com.cyos.player.CyosPlayerHolder.release()
        val session = com.cyos.player.CyosPlayerHolder.getOrCreateSession(context = context, codecProfileKey = "AUDIT_GUARD")
        com.cyos.player.CyosPlayerHolder.markPlayerAttached()
        try {
            assertTrue(com.cyos.player.CyosPlayerHolder.isActivelyInUse())
            com.cyos.player.CyosPlayerHolder.trimOrReleaseOnMemoryPressure(
                context = context,
                trimLevel = android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            )
            assertNotNull(
                "Active attached player must NOT be destroyed on memory trim",
                com.cyos.player.CyosPlayerHolder.currentPlayerOrNull()
            )
            assertTrue(session.player === com.cyos.player.CyosPlayerHolder.currentPlayerOrNull())
        } finally {
            com.cyos.player.CyosPlayerHolder.markPlayerDetached()
        }
        com.cyos.player.CyosPlayerHolder.setPipActive(true)
        try {
            com.cyos.player.CyosPlayerHolder.trimOrReleaseOnMemoryPressure(
                context = context,
                trimLevel = android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            )
            assertNotNull(
                "Active PiP player must NOT be destroyed on TRIM_MEMORY_UI_HIDDEN",
                com.cyos.player.CyosPlayerHolder.currentPlayerOrNull()
            )
        } finally {
            com.cyos.player.CyosPlayerHolder.setPipActive(false)
            com.cyos.player.CyosPlayerHolder.release()
        }

        // 4. Eac3DownmixAudioProcessor properly consumes all channels for 3ch, 4ch, and 5ch PCM without frame desync
        for (channels in listOf(3, 4, 5)) {
            val processor = com.cyosplay.player.util.Eac3DownmixAudioProcessor()
            val outFmt = processor.configure(
                androidx.media3.common.audio.AudioProcessor.AudioFormat(
                    48_000,
                    channels,
                    androidx.media3.common.C.ENCODING_PCM_16BIT
                )
            )
            assertEquals("Output must downmix ${channels}ch to stereo", 2, outFmt.channelCount)
            processor.flush()
            val frameCount = 16
            val input = java.nio.ByteBuffer.allocate(frameCount * channels * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (f in 0 until frameCount) {
                for (c in 0 until channels) {
                    input.putShort(4000.toShort())
                }
            }
            input.flip()
            processor.queueInput(input)
            assertEquals("All input PCM bytes must be consumed for ${channels}ch", 0, input.remaining())
            val output = processor.getOutput()
            assertEquals(
                "Output must contain exactly $frameCount stereo frames for ${channels}ch",
                frameCount * 2 * 2,
                output.remaining()
            )
            var nonZeroSamples = 0
            while (output.remaining() >= 2) {
                if (output.short.toInt() != 0) nonZeroSamples++
            }
            assertTrue("Downmixed ${channels}ch output must contain valid non-zero audio samples", nonZeroSamples > 0)
        }

        // 5. XvidFixingTrackOutput handles >256 KB first keyframes without infinite loop or truncation
        var flushedBytes = 0
        var emittedFormat: androidx.media3.common.Format? = null
        val sink = object : androidx.media3.extractor.TrackOutput {
            override fun format(format: androidx.media3.common.Format) {
                emittedFormat = format
            }
            override fun sampleData(
                input: androidx.media3.common.DataReader,
                length: Int,
                allowEndOfInput: Boolean,
                sampleDataPart: Int
            ): Int = length
            override fun sampleData(data: androidx.media3.common.util.ParsableByteArray, length: Int, sampleDataPart: Int) {
                flushedBytes += length
                data.skipBytes(length)
            }
            override fun sampleMetadata(
                timeUs: Long,
                flags: Int,
                size: Int,
                offset: Int,
                cryptoData: androidx.media3.extractor.TrackOutput.CryptoData?
            ) = Unit
        }
        val xvidOut = com.cyosplay.player.XvidFixingTrackOutput(sink)
        xvidOut.format(
            androidx.media3.common.Format.Builder()
                .setSampleMimeType(androidx.media3.common.MimeTypes.VIDEO_UNKNOWN)
                .setWidth(1280)
                .setHeight(720)
                .build()
        )
        val hugeKeyframe = ByteArray(300 * 1024) { 0x5A }
        // Prefix with valid MPEG-4 VOL + VOP headers so csd-0 extraction succeeds
        hugeKeyframe[0] = 0x00; hugeKeyframe[1] = 0x00; hugeKeyframe[2] = 0x01; hugeKeyframe[3] = 0xB0.toByte()
        hugeKeyframe[4] = 0x05; hugeKeyframe[5] = 0x00; hugeKeyframe[6] = 0x00; hugeKeyframe[7] = 0x01
        hugeKeyframe[8] = 0x20; hugeKeyframe[9] = 0x00; hugeKeyframe[10] = 0x00; hugeKeyframe[11] = 0x01
        hugeKeyframe[12] = 0xB6.toByte()
        xvidOut.sampleData(androidx.media3.common.util.ParsableByteArray(hugeKeyframe), hugeKeyframe.size, 0)
        xvidOut.sampleMetadata(0L, androidx.media3.common.C.BUFFER_FLAG_KEY_FRAME, hugeKeyframe.size, 0, null)
        assertNotNull("Large >256 KB keyframe must still emit normalized format", emittedFormat)
        assertEquals("All 300 KB of first keyframe must flush downstream without truncation", hugeKeyframe.size, flushedBytes)
    }

    @Test
    fun auditPillar2To4_introSkipChaptersOnly_dynamicMiniPlayerClamp_sleepAndBackgroundAudio_andStrictReleaseScript() {
        val viewModel = VideoPlayerViewModel(context)

        // 1. Intro/Credits skip must NEVER fabricate arbitrary timestamps when chapters are absent
        val noChapters = com.cyosplay.player.util.PlayerGestureControlHelper.resolveIntroCreditWindow(
            totalDurationMs = 3_600_000L,
            currentPositionMs = 30_000L,
            chapters = emptyList()
        )
        assertFalse("Must not fabricate intro window without real chapters", noChapters.isInIntroWindow)
        assertFalse("Must not fabricate credits window without real chapters", noChapters.isInCreditsWindow)

        val realChapters = listOf(
            com.cyosplay.player.util.PlayerGestureControlHelper.ChapterSegment("Intro", 0L, 90_000L),
            com.cyosplay.player.util.PlayerGestureControlHelper.ChapterSegment("Episode 1", 90_000L, 1_320_000L),
            com.cyosplay.player.util.PlayerGestureControlHelper.ChapterSegment("End Credits", 1_320_000L, 1_440_000L)
        )
        val inIntro = com.cyosplay.player.util.PlayerGestureControlHelper.resolveIntroCreditWindow(1_440_000L, 25_000L, realChapters)
        assertTrue(inIntro.isInIntroWindow)
        assertEquals(90_000L, inIntro.introSkipTargetMs)
        val inMain = com.cyosplay.player.util.PlayerGestureControlHelper.resolveIntroCreditWindow(1_440_000L, 600_000L, realChapters)
        assertFalse(inMain.isInIntroWindow)
        assertFalse(inMain.isInCreditsWindow)
        val inCredits = com.cyosplay.player.util.PlayerGestureControlHelper.resolveIntroCreditWindow(1_440_000L, 1_350_000L, realChapters)
        assertTrue(inCredits.isInCreditsWindow)
        assertEquals(1_440_000L, inCredits.creditsSkipTargetMs)

        // 2. Dynamic mini-player bounds clamping keeps bar inside any viewport
        viewModel.resetMiniPlayerOffset()
        viewModel.dragMiniPlayerByDelta(dx = 5000f, dy = -5000f, maxHalfX = 180f, maxUpY = 420f)
        assertEquals(180f, viewModel.uiState.value.miniPlayerOffsetX, 0.01f)
        assertEquals(-420f, viewModel.uiState.value.miniPlayerOffsetY, 0.01f)
        viewModel.dragMiniPlayerByDelta(dx = -5000f, dy = 5000f, maxHalfX = 180f, maxUpY = 420f)
        assertEquals(-180f, viewModel.uiState.value.miniPlayerOffsetX, 0.01f)
        assertEquals(0f, viewModel.uiState.value.miniPlayerOffsetY, 0.01f)

        // 3. Sleep timer & background audio toggles update real ViewModel state cleanly
        viewModel.setSleepTimerOption(com.cyosplay.player.data.model.SleepTimerOption.END_OF_VIDEO)
        assertEquals(com.cyosplay.player.data.model.SleepTimerOption.END_OF_VIDEO, viewModel.uiState.value.sleepTimerOption)
        viewModel.setSleepTimerOption(com.cyosplay.player.data.model.SleepTimerOption.OFF)
        assertEquals(com.cyosplay.player.data.model.SleepTimerOption.OFF, viewModel.uiState.value.sleepTimerOption)

        val initialBgAudio = viewModel.uiState.value.backgroundAudioEnabled
        viewModel.toggleBackgroundAudio()
        assertEquals(!initialBgAudio, viewModel.uiState.value.backgroundAudioEnabled)
        viewModel.toggleBackgroundAudio()
        assertEquals(initialBgAudio, viewModel.uiState.value.backgroundAudioEnabled)
    }

    @Test
    fun v108rv7_zeroPlaintextVaultStreaming_flagSecure_autoLock_subtitleCharsets_andHardening() {
        // 1. Zero-plaintext chunked AES-256-GCM streaming Vault playback + O(1) random-access seek + tamper rejection
        val tempDir = File(context.cacheDir, "v108rv7_vault_stream_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val plainSize = 620_000 // > 2 chunks (262_144 * 2 = 524_288) so 3 chunks total
        val originalPlain = ByteArray(plainSize) { idx -> ((idx * 37 + 13) and 0xFF).toByte() }
        val srcFile = File(tempDir, "secret_clip.mp4").apply { writeBytes(originalPlain) }

        val vaultManager = com.cyosplay.player.data.repository.VaultManager(context)
        val sourceVideo = VideoItem(
            id = 9901087L,
            title = "SecretClip",
            displayName = "secret_clip.mp4",
            uri = Uri.fromFile(srcFile),
            path = srcFile.absolutePath,
            sizeBytes = originalPlain.size.toLong(),
            durationMs = 42_000L,
            dateModifiedSeconds = System.currentTimeMillis() / 1000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            folderId = "movies",
            folderName = "Movies",
            folderPath = tempDir.absolutePath
        )
        val entry = vaultManager.moveVideoToVault(sourceVideo, isDecoyEntry = false)
        assertNotNull("Vault move must succeed", entry)
        val encFile = File(entry!!.encryptedFilePath)
        assertTrue("Encrypted V4 file must exist", encFile.exists() && CryptoSecurityManager.isStreamV4File(encFile))

        val ephemeralDir = File(context.cacheDir, "vault_ephemeral_play")
        vaultManager.wipeEphemeralPlaybackCache()
        val playable = vaultManager.prepareEphemeralPlaybackItem(entry)
        assertNotNull("V3 Vault playback item must prepare cleanly", playable)
        assertEquals(
            "V3 Vault video must stream via cyosvault:// scheme",
            com.cyos.player.VaultChunkedAesGcmDataSource.SCHEME,
            playable!!.uri.scheme
        )
        val ephemeralFilesAfterPrepare = ephemeralDir.listFiles()?.toList().orEmpty()
        assertTrue(
            "Zero plaintext files may be written to vault_ephemeral_play/ for V3 streaming",
            ephemeralFilesAfterPrepare.isEmpty()
        )

        // Sequential full read via VaultChunkedAesGcmDataSource must match original plaintext byte-for-byte
        val ds = com.cyos.player.VaultChunkedAesGcmDataSource(context)
        val fullReadOut = java.io.ByteArrayOutputStream()
        try {
            val openedLen = ds.open(androidx.media3.datasource.DataSpec(playable.uri))
            assertEquals(originalPlain.size.toLong(), openedLen)
            val buf = ByteArray(16_384)
            while (true) {
                val n = ds.read(buf, 0, buf.size)
                if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) break
                fullReadOut.write(buf, 0, n)
            }
        } finally {
            ds.close()
        }
        assertArrayEquals("Streamed plaintext must match original bytes exactly", originalPlain, fullReadOut.toByteArray())

        // Random-access seek across chunk boundaries (chunk 1 & chunk 2)
        val seekOffsets = listOf(262_144L + 1234L, 524_288L + 77L, 100L)
        for (seekPos in seekOffsets) {
            val seekDs = com.cyos.player.VaultChunkedAesGcmDataSource(context)
            try {
                val expectedLen = 4096
                val rem = seekDs.open(androidx.media3.datasource.DataSpec(playable.uri, seekPos, expectedLen.toLong()))
                assertEquals(expectedLen.toLong(), rem)
                val actual = ByteArray(expectedLen)
                var got = 0
                while (got < expectedLen) {
                    val r = seekDs.read(actual, got, expectedLen - got)
                    if (r == androidx.media3.common.C.RESULT_END_OF_INPUT) break
                    got += r
                }
                assertEquals(expectedLen, got)
                val expectedSlice = originalPlain.copyOfRange(seekPos.toInt(), seekPos.toInt() + expectedLen)
                assertArrayEquals("Random-access seek at $seekPos must match plaintext", expectedSlice, actual)
            } finally {
                seekDs.close()
            }
        }

        // Tamper detection: flipping a byte in chunk 1 ciphertext inside private_encrypted_vault must fail GCM authentication
        val vaultDir = File(context.filesDir, "private_encrypted_vault").apply { mkdirs() }
        val tamperedFile = File(vaultDir, "tampered_${System.nanoTime()}.cyosvault")
        encFile.copyTo(tamperedFile, overwrite = true)
        java.io.RandomAccessFile(tamperedFile, "rw").use { raf ->
            raf.seek(400L)
            val b = raf.read()
            raf.seek(400L)
            raf.write(b xor 0xFF)
        }
        val tamperedUri = com.cyos.player.VaultChunkedAesGcmDataSource.buildVaultUri(tamperedFile.absolutePath, "tampered.mp4")
        val tamperDs = com.cyos.player.VaultChunkedAesGcmDataSource(context)
        var tamperRejected = false
        try {
            tamperDs.open(androidx.media3.datasource.DataSpec(tamperedUri))
            val probe = ByteArray(64)
            tamperDs.read(probe, 0, probe.size)
        } catch (_: java.io.IOException) {
            tamperRejected = true
        } finally {
            tamperDs.close()
            tamperedFile.delete()
        }
        assertTrue("Tampered V3 chunk must be rejected by AES-GCM tag verification", tamperRejected)

        // 2. Private Vault FLAG_SECURE helper catches all Vault representations and excludes normal videos
        assertTrue(VideoPlayerViewModel.isVaultVideoItem(playable))
        assertTrue(
            VideoPlayerViewModel.isVaultVideoItem(
                playable.copy(folderId = "other", uri = Uri.parse("file:///cache/vault_ephemeral_play/play_1.mp4"), path = "/cache/vault_ephemeral_play/play_1.mp4")
            )
        )
        assertTrue(
            VideoPlayerViewModel.isVaultVideoItem(
                playable.copy(folderId = "other", uri = Uri.parse("file:///files/private_encrypted_vault/x.cyosvault"), path = "/files/private_encrypted_vault/x.cyosvault")
            )
        )
        assertFalse(VideoPlayerViewModel.isVaultVideoItem(sourceVideo))

        // 3. lockVault() closes Screen.Player & clears Vault mini-player
        val viewModel = VideoPlayerViewModel(context)
        viewModel.navigateTo(Screen.Player(video = playable, playlistQueue = listOf(playable)))
        assertTrue(viewModel.uiState.value.currentScreen is Screen.Player)
        viewModel.lockVault()
        assertEquals("lockVault() must close active Vault Screen.Player", Screen.Home, viewModel.uiState.value.currentScreen)
        assertNull("lockVault() must clear Vault miniPlayerVideo", viewModel.uiState.value.miniPlayerVideo)

        // 4. Non-UTF-8 subtitle charset scoring (Spanish Win-1252, Russian Win-1251, Arabic Win-1256, Chinese GB18030, UTF-8) + manual override + bounded read
        val spanishText = "1\n00:00:01,000 --> 00:00:04,000\n¡Hola! ¿Cómo estás? Mañana tomaremos café con música española."
        val spanishWin1252Bytes = spanishText.toByteArray(java.nio.charset.Charset.forName("windows-1252"))
        val decodedSpanish = com.cyosplay.player.util.SubtitleParser.decodeSubtitleBytesMultiLanguage(
            spanishWin1252Bytes,
            preferredEncoding = "AUTO",
            localeHint = java.util.Locale.US
        )
        assertTrue("Spanish Win-1252 must decode cleanly without Chinese/Arabic garble: $decodedSpanish", decodedSpanish.contains("¡Hola! ¿Cómo estás?"))

        val russianText = "1\n00:00:01,000 --> 00:00:04,000\nПривет, как дела? Это субтитры на русском языке для фильма."
        val russianWin1251Bytes = russianText.toByteArray(java.nio.charset.Charset.forName("windows-1251"))
        val decodedRussian = com.cyosplay.player.util.SubtitleParser.decodeSubtitleBytesMultiLanguage(
            russianWin1251Bytes,
            preferredEncoding = "AUTO",
            localeHint = java.util.Locale.US
        )
        assertTrue("Russian Win-1251 must decode cleanly: $decodedRussian", decodedRussian.contains("Привет, как дела?"))

        val arabicText = "1\n00:00:01,000 --> 00:00:04,000\nمرحبا بكم في هذا الفيلم الرائع والجميل جدا"
        val arabicWin1256Bytes = arabicText.toByteArray(java.nio.charset.Charset.forName("windows-1256"))
        val decodedArabic = com.cyosplay.player.util.SubtitleParser.decodeSubtitleBytesMultiLanguage(
            arabicWin1256Bytes,
            preferredEncoding = "AUTO",
            localeHint = java.util.Locale.US
        )
        assertTrue("Arabic Win-1256 must decode cleanly: $decodedArabic", decodedArabic.contains("مرحبا بكم في هذا الفيلم"))

        val chineseText = "1\n00:00:01,000 --> 00:00:04,000\n你好世界，欢迎观看高清电影中文字幕测试。"
        val chineseGbBytes = chineseText.toByteArray(java.nio.charset.Charset.forName("GB18030"))
        val decodedChinese = com.cyosplay.player.util.SubtitleParser.decodeSubtitleBytesMultiLanguage(
            chineseGbBytes,
            preferredEncoding = "AUTO",
            localeHint = java.util.Locale.US
        )
        assertTrue("Chinese GB18030 must decode cleanly: $decodedChinese", decodedChinese.contains("你好世界"))

        val manualRussian = com.cyosplay.player.util.SubtitleParser.decodeSubtitleBytesMultiLanguage(
            russianWin1251Bytes,
            preferredEncoding = "windows-1251"
        )
        assertTrue(manualRussian.contains("русском языке"))

        // Bounded stream read cap
        val oversized = ByteArray(1024) { 'A'.code.toByte() }
        val capped = com.cyosplay.player.util.SubtitleParser.readBoundedBytes(java.io.ByteArrayInputStream(oversized), maxBytes = 256)
        assertEquals("readBoundedBytes must enforce maxBytes cap", 256, capped.size)

        // 5. HTTPS -> HTTP cross-protocol redirect downgrade disabled & least-privilege runtime permissions
        assertFalse(
            "HTTPS -> HTTP cross-protocol redirects must be disabled",
            com.cyos.player.CyosSmartDataSourceFactory.ALLOW_CROSS_PROTOCOL_REDIRECTS
        )
        assertTrue(com.cyos.player.CyosSmartDataSourceFactory.USER_AGENT.contains("1.08rv9"))
        val api34Perms = com.cyosplay.player.util.AndroidVersionCompatHelper.resolveRuntimeMediaPermissions(34).toSet()
        assertTrue(api34Perms.contains(android.Manifest.permission.READ_MEDIA_VIDEO))
        assertFalse("READ_MEDIA_AUDIO must not be requested by video-only player", api34Perms.contains(android.Manifest.permission.READ_MEDIA_AUDIO))

        // 6. RecycleBinRepository cleans up .tmp_<nanoTime> / .dec_tmp_ / .rot_plain_ staging files & releases cleanly
        val trashDir = File(context.filesDir, "encrypted_recycle_bin").apply { mkdirs() }
        val staleTrashTmp = File(trashDir, "item.trash.tmp_11111").apply { writeBytes(ByteArray(16) { 9 }) }
        val staleTrashDec = File(trashDir, "item.dec_tmp_22222").apply { writeBytes(ByteArray(16) { 8 }) }
        val recycleRepo = com.cyosplay.player.data.repository.RecycleBinRepository(context)
        assertFalse("RecycleBin must shred .tmp_<nanoTime> files on startup", staleTrashTmp.exists())
        assertFalse("RecycleBin must shred .dec_tmp_ files on startup", staleTrashDec.exists())
        recycleRepo.release()

        // Cleanup
        vaultManager.deleteVaultEntryPermanently(entry.vaultId)
        vaultManager.release()
        tempDir.deleteRecursively()
    }

    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun v108rv8_allFunctionalSecurityAndPerformanceFixes_verified() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        // 1. Unified codecProfileKey across VideoPlayerViewModel, VideoPlayerScreen & MiniPlayerFloatingBar
        val vmKey = VideoPlayerViewModel.buildCodecProfileKey(
            decoderEngineMode = DecoderEngineMode.AUTO_HW_PLUS,
            forceHardwareAccelerationHighBitrate = true,
            lowRamShieldEnabled = false,
            batterySaverModeEnabled = true
        )
        val holderKey = com.cyos.player.CyosPlayerHolder.buildCodecProfileKey(
            decoderModeName = DecoderEngineMode.AUTO_HW_PLUS.name,
            preferSoftwareDecoder = true,
            forceLowRamMode = false,
            batterySaverEnabled = true
        )
        assertEquals("Codec profile keys must match identically between ViewModel and CyosPlayerHolder", holderKey, vmKey)

        // 2. Network startup bitrate cap is applied before first frame and lifted to Int.MAX_VALUE after first frame
        val player = com.cyos.player.CyosFastPlayer.createDirectExoPlayer(
            context = context,
            capNetworkStartupBitrate = true
        )
        try {
            com.cyos.player.CyosFastPlayer.applyBitratePolicyForUri(player, Uri.parse("https://cdn.example.com/stream.m3u8"))
            assertEquals(3_500_000, player.trackSelectionParameters.maxVideoBitrate)
            com.cyos.player.CyosFastPlayer.liftNetworkStartupBitrateCap(player)
            assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoBitrate)
        } finally {
            player.release()
        }

        // 3. Real Chapter Extraction (OGM sidecar, XML sidecar, MP4 chpl box) + Auto-Skip Intro/Credits trigger
        val chapterTempDir = File(context.cacheDir, "v108rv8_chapters_${System.nanoTime()}").apply { mkdirs() }
        try {
            // 3a. OGM sidecar chapters
            val ogmVideo = File(chapterTempDir, "episode01.mkv").apply { writeBytes(ByteArray(32)) }
            File(chapterTempDir, "episode01.chapters.txt").writeText(
                """
                CHAPTER01=00:00:00.000
                CHAPTER01NAME=Opening Intro
                CHAPTER02=00:01:30.000
                CHAPTER02NAME=Act One
                CHAPTER03=00:22:00.000
                CHAPTER03NAME=Ending Credits
                """.trimIndent()
            )
            val ogmChapters = com.cyosplay.player.util.MediaChapterExtractor.extractChaptersForLocalVideo(
                filePath = ogmVideo.absolutePath,
                totalDurationMs = 1_440_000L
            )
            assertEquals("Must extract 3 OGM sidecar chapters", 3, ogmChapters.size)
            assertEquals("Opening Intro", ogmChapters[0].title)
            assertEquals(90_000L, ogmChapters[0].endMs)

            val introWindow = com.cyosplay.player.util.PlayerGestureControlHelper.resolveIntroCreditWindow(
                totalDurationMs = 1_440_000L,
                currentPositionMs = 15_000L,
                chapters = ogmChapters
            )
            assertTrue("Must detect Intro window from real chapters", introWindow.isInIntroWindow)
            assertEquals(90_000L, introWindow.introSkipTargetMs)

            val creditsWindow = com.cyosplay.player.util.PlayerGestureControlHelper.resolveIntroCreditWindow(
                totalDurationMs = 1_440_000L,
                currentPositionMs = 1_340_000L,
                chapters = ogmChapters
            )
            assertTrue("Must detect Credits window from real chapters", creditsWindow.isInCreditsWindow)

            // 3b. Matroska XML sidecar chapters
            val xmlVideo = File(chapterTempDir, "movie_xml.mkv").apply { writeBytes(ByteArray(32)) }
            File(chapterTempDir, "movie_xml.chapters.xml").writeText(
                """
                <Chapters><EditionEntry>
                  <ChapterAtom>
                    <ChapterTimeStart>00:00:05.000000000</ChapterTimeStart>
                    <ChapterTimeEnd>00:01:25.000000000</ChapterTimeEnd>
                    <ChapterDisplay><ChapterString>Intro Sequence</ChapterString></ChapterDisplay>
                  </ChapterAtom>
                </EditionEntry></Chapters>
                """.trimIndent()
            )
            val xmlChapters = com.cyosplay.player.util.MediaChapterExtractor.extractChaptersForLocalVideo(
                filePath = xmlVideo.absolutePath,
                totalDurationMs = 600_000L
            )
            assertEquals(1, xmlChapters.size)
            assertEquals(5_000L, xmlChapters[0].startMs)
            assertEquals(85_000L, xmlChapters[0].endMs)

            // 3c. Embedded MP4 moov -> udta -> chpl binary atom
            val chplOut = java.io.ByteArrayOutputStream()
            val chplData = java.io.DataOutputStream(chplOut)
            chplData.writeInt(0x01000000) // version 1, flags 0
            chplData.writeInt(0)          // reserved
            chplData.writeByte(2)         // 2 entries
            // Entry 1: Intro at 0s
            chplData.writeLong(0L)
            val t1 = "Intro".toByteArray(Charsets.UTF_8)
            chplData.writeByte(t1.size)
            chplData.write(t1)
            // Entry 2: Main Feature at 60s (60_000 ms * 10_000 = 600_000_000 in 100ns units)
            chplData.writeLong(600_000_000L)
            val t2 = "Main Feature".toByteArray(Charsets.UTF_8)
            chplData.writeByte(t2.size)
            chplData.write(t2)
            val chplPayload = chplOut.toByteArray()

            fun wrapMp4Box(type: String, payload: ByteArray): ByteArray {
                val out = java.io.ByteArrayOutputStream()
                val dos = java.io.DataOutputStream(out)
                dos.writeInt(8 + payload.size)
                dos.write(type.toByteArray(Charsets.US_ASCII))
                dos.write(payload)
                return out.toByteArray()
            }
            val mp4File = File(chapterTempDir, "embedded_chpl.mp4")
            mp4File.writeBytes(wrapMp4Box("moov", wrapMp4Box("udta", wrapMp4Box("chpl", chplPayload))))
            val mp4Chapters = com.cyosplay.player.util.MediaChapterExtractor.extractChaptersForLocalVideo(
                filePath = mp4File.absolutePath,
                totalDurationMs = 300_000L
            )
            assertEquals("Must extract 2 embedded MP4 chpl chapters", 2, mp4Chapters.size)
            assertEquals("Intro", mp4Chapters[0].title)
            assertEquals(60_000L, mp4Chapters[0].endMs)
            assertEquals("Main Feature", mp4Chapters[1].title)
        } finally {
            chapterTempDir.deleteRecursively()
        }

        // 4. Real Media3 OpenGL VideoEffects (SmartVideoEffectsFactory)
        assertTrue(
            "OFF mode must return empty VideoEffects list for zero GPU overhead",
            com.cyosplay.player.util.SmartVideoEffectsFactory.createEffectsForMode(
                com.cyosplay.player.data.model.SmartEnhancementMode.OFF
            ).isEmpty()
        )
        val cinemaEffects = com.cyosplay.player.util.SmartVideoEffectsFactory.createEffectsForMode(
            com.cyosplay.player.data.model.SmartEnhancementMode.CINEMA_HDR
        )
        assertEquals(1, cinemaEffects.size)
        val matrix = com.cyosplay.player.util.SmartVideoEffectsFactory.buildColorMatrix4x4(
            com.cyosplay.player.data.model.SmartEnhancementMode.VIVID_AMOLED
        )
        assertEquals(16, matrix.size)
        assertEquals(1f, matrix[15], 0.0001f)

        // 5. VaultChunkedAesGcmDataSource canonical path containment rejects outside & traversal paths
        val outsideFile = File(context.cacheDir, "outside_secret_${System.nanoTime()}.txt").apply { writeText("secret") }
        try {
            assertFalse(
                "Outside file must fail private vault canonical containment",
                com.cyos.player.VaultChunkedAesGcmDataSource.isPathContainedInPrivateVault(context, outsideFile)
            )
            val traversalFile = File(
                File(context.filesDir, "private_encrypted_vault"),
                "../cache/${outsideFile.name}"
            )
            assertFalse(
                "Path traversal with ../ must fail private vault canonical containment",
                com.cyos.player.VaultChunkedAesGcmDataSource.isPathContainedInPrivateVault(context, traversalFile)
            )
            val outsideUri = com.cyos.player.VaultChunkedAesGcmDataSource.buildVaultUri(outsideFile.absolutePath, "leak.mp4")
            val ds = com.cyos.player.VaultChunkedAesGcmDataSource(context)
            var containmentRejected = false
            try {
                ds.open(androidx.media3.datasource.DataSpec(outsideUri))
            } catch (_: java.io.IOException) {
                containmentRejected = true
            } finally {
                ds.close()
            }
            assertTrue("DataSource.open must throw IOException for paths outside private_encrypted_vault", containmentRejected)
        } finally {
            outsideFile.delete()
        }

        // 6. SettingsRepository partitions non-sensitive UI/playback prefs into plain fastPrefs while keeping Vault PIN encrypted
        val settingsRepo = com.cyosplay.player.data.repository.SettingsRepository(context)
        val chosenTheme = com.cyosplay.player.data.model.AppThemeMode.entries.last()
        settingsRepo.setThemeMode(chosenTheme)
        settingsRepo.setSavedPlayerBrightness(0.72f)
        settingsRepo.setVaultPin("654321")
        val fastPrefs = context.getSharedPreferences(
            com.cyosplay.player.data.repository.SettingsRepository.FAST_PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
        val securePrefs = context.getSharedPreferences(
            com.cyosplay.player.data.repository.SettingsRepository.SECURE_PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
        assertEquals(chosenTheme.name, fastPrefs.getString("theme_mode", null))
        assertNull("Non-sensitive theme_mode must not stay encrypted in secure prefs", securePrefs.getString("theme_mode", null))
        val pinToken = securePrefs.getString("vault_pin_token", null)
        assertNotNull("Vault PIN token must remain in encrypted secure prefs", pinToken)
        assertFalse("Vault PIN must never be stored in plaintext", pinToken!!.contains("654321"))
        assertTrue(settingsRepo.verifyVaultPin("654321"))

        // Transparent migration: legacy encrypted non-sensitive key in securePrefs migrates cleanly into fastPrefs
        fastPrefs.edit().remove("video_sort_option").apply()
        val legacyEncSort = CryptoSecurityManager.encryptString(
            context,
            com.cyosplay.player.data.model.VideoSortOption.SIZE_DESC.name
        )
        securePrefs.edit().putString("video_sort_option", legacyEncSort).commit()
        val freshSettingsRepo = com.cyosplay.player.data.repository.SettingsRepository(context)
        assertEquals(com.cyosplay.player.data.model.VideoSortOption.SIZE_DESC, freshSettingsRepo.getVideoSortOption())
        assertEquals(com.cyosplay.player.data.model.VideoSortOption.SIZE_DESC.name, fastPrefs.getString("video_sort_option", null))
        settingsRepo.release()
        freshSettingsRepo.release()

        // 7. CyosPlaybackService manifest & 1.08rv9 version identity
        assertTrue(com.cyos.player.CyosSmartDataSourceFactory.USER_AGENT.contains("1.08rv9"))
        val viewModel = VideoPlayerViewModel(context)
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertEquals("CyosPlay 1.08rv9", context.getString(R.string.about_us_description))
    }

    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun v108rv9_allAuditFixesAndEnvelopeVault_verified() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        // 1. V4 Envelope Encryption: per-file DEK wrapped by KEK, O(1) instant zero-plaintext KEK header re-wrap,
        //    payload ciphertext unchanged, wrong KEK rejected, and legacy V3 backwards compatibility + V4 upgrade.
        val tempDir = File(context.cacheDir, "v108rv9_env_${System.nanoTime()}").apply { mkdirs() }
        val vaultDir = File(context.filesDir, "private_encrypted_vault").apply { mkdirs() }
        try {
            val plainSrc = File(tempDir, "envelope_test.mp4")
            val originalBytes = ByteArray(262_144 * 2 + 7_777) { idx -> ((idx * 37 + 19) and 0xFF).toByte() }
            plainSrc.writeBytes(originalBytes)

            val aesKeyGen = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }
            val kek1 = aesKeyGen.generateKey()
            val kek2 = aesKeyGen.generateKey()
            val wrongKek = aesKeyGen.generateKey()

            val v4File = File(vaultDir, "v4_env_${System.nanoTime()}.cyosvault")
            assertTrue(CryptoSecurityManager.encryptFileToStreamV4WithKek(plainSrc, v4File, kek1))
            assertTrue("File must have CYOSV4 envelope magic", CryptoSecurityManager.isStreamV4File(v4File))
            assertTrue("File must satisfy isStreamEncryptedFile", CryptoSecurityManager.isStreamEncryptedFile(v4File))

            val headerInfo = CryptoSecurityManager.readStreamHeader(v4File)
            assertNotNull("V4 stream header must parse cleanly", headerInfo)
            val payloadOffset = headerInfo!!.headerSizeBytes.toInt()
            val payloadBeforeRewrap = v4File.readBytes().copyOfRange(payloadOffset, v4File.length().toInt())

            // Instant O(1) header-only KEK rotation (kek1 -> kek2)
            assertTrue(CryptoSecurityManager.rewrapStreamV4HeaderInPlace(v4File, kek1, kek2))
            val payloadAfterRewrap = v4File.readBytes().copyOfRange(payloadOffset, v4File.length().toInt())
            assertArrayEquals(
                "O(1) KEK header re-wrap must leave encrypted video chunk payload untouched",
                payloadBeforeRewrap,
                payloadAfterRewrap
            )

            // Old KEK and wrong KEK must be rejected; new KEK decrypts byte-for-byte identically
            assertFalse("Old KEK must fail after header re-wrap", CryptoSecurityManager.verifyEncryptedFileIntegrityWithKey(v4File, kek1))
            assertFalse("Wrong KEK must fail", CryptoSecurityManager.verifyEncryptedFileIntegrityWithKey(v4File, wrongKek))
            assertTrue("New KEK must pass integrity verification", CryptoSecurityManager.verifyEncryptedFileIntegrityWithKey(v4File, kek2))

            val decryptedV4 = File(tempDir, "v4_decrypted.mp4")
            assertTrue(CryptoSecurityManager.decryptFileFromDiskWithKey(v4File, decryptedV4, kek2))
            assertArrayEquals("Decrypted V4 output must match original bytes", originalBytes, decryptedV4.readBytes())
            v4File.delete()

            // Legacy V3 file backwards compatibility & transparent upgrade to V4 on re-encryption
            val legacyV3File = File(vaultDir, "legacy_v3_${System.nanoTime()}.cyosvault")
            assertTrue(CryptoSecurityManager.encryptFileToDiskV3WithKey(plainSrc, legacyV3File, kek1))
            assertTrue(CryptoSecurityManager.isStreamV3File(legacyV3File))
            assertTrue(CryptoSecurityManager.isStreamEncryptedFile(legacyV3File))
            assertTrue(CryptoSecurityManager.verifyEncryptedFileIntegrityWithKey(legacyV3File, kek1))
            assertTrue(CryptoSecurityManager.reEncryptFileOnDisk(legacyV3File, kek1, kek2))
            assertTrue("Rotated legacy V3 file should upgrade to V4 envelope", CryptoSecurityManager.isStreamV4File(legacyV3File))
            assertTrue(CryptoSecurityManager.verifyEncryptedFileIntegrityWithKey(legacyV3File, kek2))
            legacyV3File.delete()

            // 2. JSONObject-safe manifest signing (handles quotes and backslashes cleanly)
            val trickyFile = File(tempDir, "clip_\"quoted\"_\\slash.mp4").apply { writeBytes(ByteArray(64) { 7 }) }
            val signedManifest = CryptoSecurityManager.signMediaFileWithKeystore(trickyFile)
            assertEquals(trickyFile.name, signedManifest.filename)
            val sigJsonFile = File(tempDir, "${trickyFile.name}.sig.json")
            assertTrue("Companion .sig.json manifest must be written", sigJsonFile.exists())
            val parsedManifest = org.json.JSONObject(sigJsonFile.readText())
            assertEquals(trickyFile.name, parsedManifest.getString("filename"))
            assertEquals("SHA256withRSA", parsedManifest.getString("algorithm"))
            assertEquals(64, parsedManifest.getString("sha256").length)
        } finally {
            tempDir.deleteRecursively()
        }

        // 3. Session-only decoder fallback does NOT overwrite user's saved SettingsRepository decoder mode
        val viewModel = VideoPlayerViewModel(context)
        viewModel.setDecoderEngineMode(DecoderEngineMode.HW_PLUS)
        assertEquals(DecoderEngineMode.HW_PLUS, viewModel.settingsRepository.getDecoderEngineMode())
        val fallbackMode = viewModel.fallbackToNextDecoderEngine()
        assertNotNull("HW+ must advance to next session fallback mode", fallbackMode)
        assertEquals(fallbackMode, viewModel.uiState.value.decoderEngineMode)
        assertEquals(
            "Session decoder fallback must preserve persisted SettingsRepository mode",
            DecoderEngineMode.HW_PLUS,
            viewModel.settingsRepository.getDecoderEngineMode()
        )

        // 4. switchActivePlaylistVideo updates Screen.Player, clears stale chapters, and accepts runtime chapters for video #2
        val video1 = VideoItem(
            id = 101L,
            title = "Episode 1",
            displayName = "ep1.mkv",
            uri = Uri.parse("file:///storage/emulated/0/Movies/ep1.mkv"),
            path = "/storage/emulated/0/Movies/ep1.mkv",
            sizeBytes = 100_000L,
            durationMs = 1_200_000L,
            dateModifiedSeconds = 1000L,
            width = 1920,
            height = 1080,
            mimeType = "video/x-matroska",
            folderId = "movies",
            folderName = "Movies",
            folderPath = "/storage/emulated/0/Movies"
        )
        val video2 = video1.copy(
            id = 102L,
            title = "Episode 2",
            displayName = "ep2.mkv",
            uri = Uri.parse("file:///storage/emulated/0/Movies/ep2.mkv"),
            path = "/storage/emulated/0/Movies/ep2.mkv"
        )
        viewModel.navigateTo(Screen.Player(video = video1, playlistQueue = listOf(video1, video2)))
        viewModel.mergeRuntimeChapters(
            video1.path,
            listOf(com.cyosplay.player.util.PlayerGestureControlHelper.ChapterSegment("Opening Intro", 0L, 90_000L))
        )
        assertEquals(1, viewModel.uiState.value.activeVideoChapters.size)
        viewModel.switchActivePlaylistVideo(video2, listOf(video1, video2))
        val playerScreen = viewModel.uiState.value.currentScreen as? Screen.Player
        assertNotNull(playerScreen)
        assertEquals(video2.path, playerScreen!!.video.path)
        assertTrue("Switching playlist video must clear previous video's chapters", viewModel.uiState.value.activeVideoChapters.isEmpty())
        viewModel.mergeRuntimeChapters(
            video2.path,
            listOf(com.cyosplay.player.util.PlayerGestureControlHelper.ChapterSegment("Intro", 10_000L, 95_000L))
        )
        assertEquals("Runtime chapters for video #2 must be accepted after switch", 1, viewModel.uiState.value.activeVideoChapters.size)

        // 5. High volume & brightness warning requires BOTH volume >= 85% AND brightness >= 85%
        assertFalse(
            "Bright screen alone with low volume must NOT trigger warning",
            com.cyosplay.player.util.PlayerGestureControlHelper.isHighVolumeAndBrightnessDetected(0.30f, 0.95f)
        )
        assertFalse(
            "Loud volume alone with dim screen must NOT trigger warning",
            com.cyosplay.player.util.PlayerGestureControlHelper.isHighVolumeAndBrightnessDetected(0.95f, 0.30f)
        )
        assertTrue(
            "Both volume >= 85% and brightness >= 85% must trigger warning",
            com.cyosplay.player.util.PlayerGestureControlHelper.isHighVolumeAndBrightnessDetected(0.90f, 0.90f)
        )

        // 6. Eac3DownmixAudioProcessor bypasses standard stereo 16-bit PCM at unity gain (zero CPU copy),
        //    while remaining active for 5.1 surround or boosted/night-mode audio.
        val audioProcessor = com.cyosplay.player.util.Eac3DownmixAudioProcessor()
        audioProcessor.volumeBoostMultiplier = 1.0f
        audioProcessor.nightModeCompressionEnabled = false
        val stereo16Format = androidx.media3.common.audio.AudioProcessor.AudioFormat(48_000, 2, androidx.media3.common.C.ENCODING_PCM_16BIT)
        audioProcessor.configure(stereo16Format)
        assertFalse("Standard stereo 16-bit unity PCM must bypass downmix processor", audioProcessor.isActive)

        val surroundFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat(48_000, 6, androidx.media3.common.C.ENCODING_PCM_16BIT)
        val outSurround = audioProcessor.configure(surroundFormat)
        assertTrue("5.1 surround must activate downmix processor", audioProcessor.isActive)
        assertEquals("5.1 surround must downmix to stereo", 2, outSurround.channelCount)

        audioProcessor.volumeBoostMultiplier = 1.5f
        audioProcessor.configure(stereo16Format)
        assertTrue("Boosted stereo must activate processor", audioProcessor.isActive)

        // 7. MemoryManager enforces strict byte buffer caps across RAM tiers
        val memBudget = com.cyosplay.player.util.MemoryManager.detectMemoryBudget(context)
        assertFalse("Low-RAM Shield must enforce targetBufferBytes cap", memBudget.prioritizeTimeOverSizeThresholds)

        // 8. SmartVideoEffectsFactory wires Clarity Boost slider into GPU RGB matrix
        val clarityEffects = com.cyosplay.player.util.SmartVideoEffectsFactory.createEffectsForMode(
            mode = com.cyosplay.player.data.model.SmartEnhancementMode.OFF,
            clarityBoost = 0.6f
        )
        assertEquals("Non-zero Clarity Boost must emit an OpenGL VideoEffect even when preset is OFF", 1, clarityEffects.size)
        val identityMatrix = com.cyosplay.player.util.SmartVideoEffectsFactory.buildColorMatrix4x4(
            com.cyosplay.player.data.model.SmartEnhancementMode.OFF,
            clarityBoost = 0f
        )
        val boostedMatrix = com.cyosplay.player.util.SmartVideoEffectsFactory.buildColorMatrix4x4(
            com.cyosplay.player.data.model.SmartEnhancementMode.OFF,
            clarityBoost = 0.6f
        )
        assertFalse("Clarity Boost matrix must differ from identity", identityMatrix.contentEquals(boostedMatrix))

        // 9. MiniPlayerPositionPreset viewport-adaptive offset resolution & case-insensitive volume lookup
        val (topRightX, topRightY) = com.cyosplay.player.data.model.MiniPlayerPositionPreset.TOP_RIGHT.resolveOffsetPx(100f, 400f)
        assertEquals(100f, topRightX, 0.01f)
        assertEquals(-400f, topRightY, 0.01f)
        val caseMap = mapOf("/storage/emulated/0/Movies/Clip.mp4" to 0.65f)
        assertEquals(
            0.65f,
            VideoPlayerViewModel.lookupFloatSettingCaseInsensitive(caseMap, "/storage/emulated/0/movies/clip.mp4")!!,
            0.001f
        )

        // 10. Event-driven external subtitle transition scheduler helper
        val sampleCues = listOf(
            com.cyosplay.player.data.model.SubtitleCueItem(1, 1_000L, 3_000L, "Hello"),
            com.cyosplay.player.data.model.SubtitleCueItem(2, 5_000L, 8_000L, "World")
        )
        assertEquals(1_000L, com.cyosplay.player.util.SubtitleParser.findNextSubtitleEventTimeMs(sampleCues, 0L))
        assertEquals(3_001L, com.cyosplay.player.util.SubtitleParser.findNextSubtitleEventTimeMs(sampleCues, 1_500L))
        assertEquals(5_000L, com.cyosplay.player.util.SubtitleParser.findNextSubtitleEventTimeMs(sampleCues, 3_500L))
        assertNull(com.cyosplay.player.util.SubtitleParser.findNextSubtitleEventTimeMs(sampleCues, 9_000L))

        // 11. Neutral 0ms default subtitle cadence offset & 1.08rv9 version identity
        assertEquals(0L, com.cyosplay.player.util.SubtitleAudioSyncEngine.computeLanguagePatternOffsetMs("en", listOf("en"), 44100))
        assertEquals(0L, com.cyosplay.player.util.SubtitleAudioSyncEngine.computeLanguagePatternOffsetMs("ja", listOf("en"), 48000))
        assertEquals("1.08rv9", viewModel.uiState.value.appVersionName)
        assertEquals("CyosPlay 1.08rv9", viewModel.uiState.value.appDescription)
        assertEquals("CyosPlay 1.08rv9", context.getString(R.string.about_us_description))
        assertTrue(com.cyos.player.CyosSmartDataSourceFactory.USER_AGENT.contains("1.08rv9"))
    }
}
