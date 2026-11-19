package com.cyosplay.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.cyosplay.player.data.model.AspectRatioMode
import com.cyosplay.player.data.model.AudioTrackInfo
import com.cyosplay.player.data.model.DecoderEngineMode
import com.cyosplay.player.data.model.HudType
import com.cyosplay.player.data.model.PlayerMediaErrorType
import com.cyosplay.player.data.model.PlayerOrientationMode
import com.cyosplay.player.data.model.SmartEnhancementMode
import com.cyosplay.player.data.model.SubtitleEdgeStyle
import com.cyosplay.player.data.model.SubtitleFontSize
import com.cyosplay.player.data.model.SubtitleTrackInfo
import com.cyosplay.player.data.model.VideoItem
import com.cyosplay.player.ui.Screen
import com.cyosplay.player.ui.VideoPlayerUiState
import com.cyosplay.player.ui.VideoPlayerViewModel
import com.cyosplay.player.ui.components.VisualComfortScreenOverlay
import com.cyosplay.player.ui.theme.AmberGold
import com.cyosplay.player.ui.theme.CrimsonAlert
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault
import com.cyosplay.player.ui.theme.RobotoNumbersFontFamily
import com.cyosplay.player.util.AudioBadgeFormatter
import com.cyosplay.player.util.Eac3DownmixAudioProcessor
import com.cyosplay.player.util.FfmpegMatroskaCodecRegistry
import com.cyosplay.player.util.Formatters
import com.cyosplay.player.util.MemoryManager
import com.cyosplay.player.util.PlayerGestureControlHelper
import com.cyosplay.player.util.ScreenOrientationSensorHelper
import com.cyosplay.player.util.SubtitleParser
import com.cyosplay.player.util.VideoGestureZone
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: VideoItem,
    playlistQueue: List<VideoItem>,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    var activeVideo by remember(video) { mutableStateOf(video) }
    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isImmersiveFullScreen by remember { mutableStateOf(true) }

    // Floating lock button visibility when controls are locked (auto-hides after 3 seconds)
    var lockedButtonVisible by remember { mutableStateOf(true) }

    // Hold-on-screen fast-forward speed state (1x to 5x hold & horizontal drag, works both when locked and unlocked)
    var isHold2xSpeedActive by remember { mutableStateOf(false) }
    var holdSpeedMultiplier by remember { mutableFloatStateOf(2.0f) }

    // Item 1: Track last user interaction timestamp for 3-second smooth fade-out transition of player controls
    var lastInteractionTimestampMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val registerUserInteraction = {
        lastInteractionTimestampMs = SystemClock.elapsedRealtime()
    }

    val initialResumePositionMs = remember(activeVideo) {
        val persistedProgress = uiState.playbackProgressMap[activeVideo.path]
            ?: viewModel.settingsRepository.getPlaybackProgressForVideo(activeVideo.path)
        val savedPos = persistedProgress?.lastPositionMs ?: 0L
        val dur = activeVideo.durationMs.coerceAtLeast(persistedProgress?.durationMs ?: 1000L)
        if (uiState.resumePlaybackEnabled && !uiState.stealthPlaybackEnabled && savedPos > 0L) {
            if (dur > 5000L && savedPos >= dur - 2000L) 0L else savedPos
        } else {
            0L
        }
    }

    var currentPositionMs by remember(activeVideo) {
        mutableLongStateOf(initialResumePositionMs)
    }
    var resumedFromTimestampMs by remember(activeVideo) {
        mutableLongStateOf(initialResumePositionMs)
    }
    // Item 5: Resume playback silently at saved position without any pop-up banner on the video screen
    var showResumeStartOverBanner by remember(activeVideo) {
        mutableStateOf(false)
    }
    var isPlayerTitleTextRevealed by remember(activeVideo, uiState.hideAllTextsEnabled) {
        mutableStateOf(false)
    }
    var totalDurationMs by remember(activeVideo) {
        mutableLongStateOf(activeVideo.durationMs.coerceAtLeast(1000L))
    }
    var isUserScrubbing by remember(activeVideo) { mutableStateOf(false) }
    var scrubPositionMs by remember(activeVideo) { mutableLongStateOf(0L) }
    var isMediaLoading by remember(activeVideo.path) { mutableStateOf(true) }
    var mediaErrorType by remember(activeVideo.path) { mutableStateOf(PlayerMediaErrorType.NONE) }
    var hasAppliedSavedAudioForVideo by remember(activeVideo.path) { mutableStateOf(false) }
    var liveEmbeddedCueTexts by remember(activeVideo.path) { mutableStateOf<List<String>>(emptyList()) }
    val configuration = LocalConfiguration.current
    val viewportWidthDp = configuration.screenWidthDp.toFloat().coerceAtLeast(320f)

    // Keep ViewModel informed of active playback state & video dimensions for Auto-PiP on Home press
    LaunchedEffect(isPlaying, activeVideo.width, activeVideo.height) {
        viewModel.updateActivePlayerPipState(
            isPlaying = isPlaying,
            width = activeVideo.width,
            height = activeVideo.height
        )
    }

    // Item 1: Automatically fade out player controls when no user interaction is detected for 3 seconds
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSmartEnhanceDialog by remember { mutableStateOf(false) }
    var showSpeedSkipPopupDialog by remember { mutableStateOf(false) }
    var showVideoZoomPopupDialog by remember { mutableStateOf(false) }
    var isScreenTouchSlideSkipping by remember { mutableStateOf(false) }
    var hasAutoSkippedIntroForCurrentVideo by remember(activeVideo.path) { mutableStateOf(false) }
    var hasAutoSkippedCreditsForCurrentVideo by remember(activeVideo.path) { mutableStateOf(false) }
    var lastDoubleTapSeekElapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(
        controlsVisible,
        lastInteractionTimestampMs,
        isPlaying,
        isUserScrubbing,
        isScreenTouchSlideSkipping,
        showAudioDialog,
        showSubtitleDialog,
        showSmartEnhanceDialog,
        showSpeedSkipPopupDialog,
        showVideoZoomPopupDialog
    ) {
        if (controlsVisible &&
            isPlaying &&
            !isUserScrubbing &&
            !isScreenTouchSlideSkipping &&
            !showAudioDialog &&
            !showSubtitleDialog &&
            !showSmartEnhanceDialog &&
            !showSpeedSkipPopupDialog &&
            !showVideoZoomPopupDialog
        ) {
            delay(3000L)
            if (SystemClock.elapsedRealtime() - lastInteractionTimestampMs >= 2950L &&
                !isUserScrubbing &&
                !isScreenTouchSlideSkipping &&
                !showAudioDialog &&
                !showSubtitleDialog &&
                !showSmartEnhanceDialog &&
                !showSpeedSkipPopupDialog &&
                !showVideoZoomPopupDialog
            ) {
                controlsVisible = false
            }
        }
    }

    // Item 4: Do NOT automatically turn ON Mini-Player on Back press; only turn ON when user taps Minimize button.
    BackHandler {
        if (uiState.isControlsLocked) {
            lockedButtonVisible = true
            registerUserInteraction()
        } else {
            viewModel.savePlaybackProgressAndExit(
                videoPath = activeVideo.path,
                positionMs = currentPositionMs,
                durationMs = totalDurationMs
            )
        }
    }

    // Gesture HUD state (Brightness, Volume, Seek, Rotation, FullScreen, Screenshot)
    val density = LocalDensity.current
    val minSwipeDistancePx = remember(density) {
        with(density) { PlayerGestureControlHelper.DEFAULT_MIN_SWIPE_DISTANCE_DP.dp.toPx() }
    }
    var activeHudType by remember { mutableStateOf<HudType?>(null) }
    var hudLabelText by remember { mutableStateOf("") }
    var hudValueFraction by remember { mutableFloatStateOf(0.5f) }
    var lastHudUpdateTimestampMs by remember { mutableLongStateOf(0L) }
    val initialWindowBrightness = remember {
        activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
    val activeFolderKey = remember(activeVideo.path, activeVideo.folderPath, activeVideo.folderId) {
        activeVideo.folderPath.trim().trimEnd('/')
            .ifBlank { activeVideo.path.substringBeforeLast('/', "").trim().trimEnd('/') }
            .ifBlank { activeVideo.folderId.trim() }
    }
    // Item 2: Remember last Volume and Brightness setting for each folder of videos (as well as per-video & global fallback)
    var screenBrightness by remember(activeVideo.path, activeFolderKey) {
        val folderOrVideoBrightness = viewModel.getPlayerBrightnessForVideo(activeVideo.path, activeFolderKey)
        mutableFloatStateOf(PlayerGestureControlHelper.sanitizeBrightness(folderOrVideoBrightness))
    }
    var gestureVolumeFraction by remember(activeVideo.path, activeFolderKey) {
        val folderOrVideoVolume = viewModel.getPlayerVolumeForVideo(activeVideo.path, activeFolderKey)
        mutableFloatStateOf(PlayerGestureControlHelper.sanitizeVolume(folderOrVideoVolume))
    }
    LaunchedEffect(activeVideo.path, activeFolderKey) {
        val restoredBrightness = PlayerGestureControlHelper.sanitizeBrightness(
            viewModel.getPlayerBrightnessForVideo(activeVideo.path, activeFolderKey)
        )
        val restoredVolume = PlayerGestureControlHelper.sanitizeVolume(
            viewModel.getPlayerVolumeForVideo(activeVideo.path, activeFolderKey)
        )
        screenBrightness = restoredBrightness
        gestureVolumeFraction = restoredVolume
        PlayerGestureControlHelper.applyWindowBrightness(
            activity = activity,
            brightnessFraction = restoredBrightness,
            batterySaverEnabled = uiState.batterySaverModeEnabled
        )
    }
    LaunchedEffect(uiState.savedPlayerBrightness) {
        val targetBrightness = PlayerGestureControlHelper.sanitizeBrightness(uiState.savedPlayerBrightness)
        if (kotlin.math.abs(targetBrightness - screenBrightness) > 0.005f) {
            screenBrightness = targetBrightness
        }
    }
    LaunchedEffect(uiState.savedPlayerVolume) {
        val targetVolume = PlayerGestureControlHelper.sanitizeVolume(uiState.savedPlayerVolume)
        if (kotlin.math.abs(targetVolume - gestureVolumeFraction) > 0.005f) {
            gestureVolumeFraction = targetVolume
        }
    }

    var highVolumeBrightnessAlertDismissedForSession by remember { mutableStateOf(false) }
    val isBothHighVolumeAndBrightness = PlayerGestureControlHelper.isHighVolumeAndBrightnessDetected(
        volumeFraction = gestureVolumeFraction,
        brightnessFraction = screenBrightness,
        isMuted = uiState.isAudioMuted
    )
    LaunchedEffect(isBothHighVolumeAndBrightness, uiState.showHighVolumeBrightnessWarning) {
        if (uiState.showHighVolumeBrightnessWarning || isBothHighVolumeAndBrightness) {
            highVolumeBrightnessAlertDismissedForSession = false
        }
    }
    val showHighVolumeAndBrightnessAlert = !uiState.isInPipMode &&
        !highVolumeBrightnessAlertDismissedForSession &&
        (isBothHighVolumeAndBrightness || uiState.showHighVolumeBrightnessWarning)

    LaunchedEffect(showHighVolumeAndBrightnessAlert) {
        if (showHighVolumeAndBrightnessAlert) {
            delay(6000L)
            highVolumeBrightnessAlertDismissedForSession = true
            viewModel.dismissHighVolumeBrightnessWarning()
        }
    }
    var zoomPanOffset by remember(activeVideo) { mutableStateOf(Offset.Zero) }
    var liveZoomScale by remember(activeVideo) { mutableFloatStateOf(uiState.videoZoomScale.coerceIn(0.5f, 4.0f)) }
    var livePanX by remember(activeVideo) { mutableFloatStateOf(0f) }
    var livePanY by remember(activeVideo) { mutableFloatStateOf(0f) }
    var activeGestureLock by remember { mutableStateOf(PlayerGestureLock.NONE) }
    var activePointerCount by remember { mutableIntStateOf(0) }
    var lastMultiTouchEndedElapsedMs by remember { mutableLongStateOf(0L) }
    var preZoomBrightnessSnapshot by remember(activeVideo.path, activeFolderKey) { mutableFloatStateOf(screenBrightness) }
    var preGestureVolumeSnapshot by remember(activeVideo.path, activeFolderKey) { mutableFloatStateOf(gestureVolumeFraction) }
    var activePlayerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var lastBoundSurfaceVideoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(uiState.videoZoomScale) {
        if (activeGestureLock != PlayerGestureLock.PINCH_ZOOM) {
            liveZoomScale = uiState.videoZoomScale.coerceIn(0.5f, 4.0f)
            if (liveZoomScale <= 1.01f) {
                livePanX = 0f
                livePanY = 0f
                zoomPanOffset = Offset.Zero
            }
        }
    }

    var customClarityBoost by remember { mutableFloatStateOf(0.35f) }
    var customContrastDelta by remember { mutableFloatStateOf(0f) }
    var customSaturationDelta by remember { mutableFloatStateOf(0f) }

    var pendingAttachedSubtitleUri by remember(activeVideo.path) { mutableStateOf<android.net.Uri?>(null) }

    val srtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingAttachedSubtitleUri = uri
            viewModel.loadExternalSubtitleUri(uri)
        }
    }

    // Auto-hide the floating lock button 3 seconds after locking or tapping the locked screen
    LaunchedEffect(uiState.isControlsLocked, lockedButtonVisible, lastInteractionTimestampMs) {
        if (uiState.isControlsLocked && lockedButtonVisible) {
            delay(3000L)
            lockedButtonVisible = false
        }
    }

    // Smooth Sensor Screen Tilt + Manual Rotation Lock Button (Items 2 & 3)
    var liveSensorDegrees by remember { mutableIntStateOf(OrientationEventListener.ORIENTATION_UNKNOWN) }
    var activeAppliedOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    var manualOrientationOverride by remember { mutableStateOf<Int?>(null) }
    var portraitLockedAtSensorDegrees by remember { mutableIntStateOf(OrientationEventListener.ORIENTATION_UNKNOWN) }

    LaunchedEffect(
        activity,
        uiState.playerOrientationMode,
        activeVideo.width,
        activeVideo.height,
        liveSensorDegrees,
        manualOrientationOverride,
        portraitLockedAtSensorDegrees,
        uiState.isControlsLocked
    ) {
        val hostActivity = activity ?: return@LaunchedEffect
        val targetOrientation = when {
            uiState.isControlsLocked && ScreenOrientationSensorHelper.isManualPortraitOrLandscapeOrientation(activeAppliedOrientation) -> activeAppliedOrientation
            else -> ScreenOrientationSensorHelper.resolvePlayerOrientationWithTiltLandscapeOnly(
                manualOrientationOverride = manualOrientationOverride,
                sensorDegrees = liveSensorDegrees,
                currentOrientation = activeAppliedOrientation,
                portraitLockedAtSensorDegrees = portraitLockedAtSensorDegrees
            )
        }
        if (manualOrientationOverride == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT &&
            ScreenOrientationSensorHelper.isStrictLandscapeOrientation(targetOrientation)
        ) {
            // When in Portrait and user tilts into Landscape, switch to Landscape and continue in tilt-only-landscape mode
            manualOrientationOverride = null
            portraitLockedAtSensorDegrees = OrientationEventListener.ORIENTATION_UNKNOWN
        }
        if (hostActivity.requestedOrientation != targetOrientation) {
            hostActivity.requestedOrientation = targetOrientation
            activeAppliedOrientation = targetOrientation
        }
    }

    DisposableEffect(context, activity, uiState.playerOrientationMode) {
        var lastSensorUpdateElapsedMs = 0L
        val orientationEventListener = object : OrientationEventListener(
            context,
            SensorManager.SENSOR_DELAY_UI
        ) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN || degrees !in 0..359) return
                val nowElapsed = SystemClock.elapsedRealtime()
                if (nowElapsed - lastSensorUpdateElapsedMs < ScreenOrientationSensorHelper.DEBOUNCE_MS) {
                    return
                }
                val prev = liveSensorDegrees
                if (prev == ORIENTATION_UNKNOWN ||
                    ScreenOrientationSensorHelper.angularDistance(degrees, prev) >= 12
                ) {
                    val normalized = ((degrees % 360) + 360) % 360
                    val isUprightPortraitAngle = normalized <= 30 || normalized >= 330
                    if (manualOrientationOverride == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && isUprightPortraitAngle) {
                        portraitLockedAtSensorDegrees = ORIENTATION_UNKNOWN
                    }
                    val isClearLandscapeTilt = ScreenOrientationSensorHelper.isHorizontalLandscapeTiltDegrees(degrees)
                    if (isClearLandscapeTilt) {
                        if (manualOrientationOverride == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                            val movedFromLock = portraitLockedAtSensorDegrees == ORIENTATION_UNKNOWN ||
                                ScreenOrientationSensorHelper.angularDistance(normalized, portraitLockedAtSensorDegrees) >= 45
                            if (movedFromLock) {
                                manualOrientationOverride = null
                                portraitLockedAtSensorDegrees = ORIENTATION_UNKNOWN
                            }
                        } else if (prev != ORIENTATION_UNKNOWN &&
                            ScreenOrientationSensorHelper.angularDistance(degrees, prev) >= 65
                        ) {
                            manualOrientationOverride = null
                        }
                    }
                    lastSensorUpdateElapsedMs = nowElapsed
                    liveSensorDegrees = degrees
                }
            }
        }

        try {
            if (orientationEventListener.canDetectOrientation()) {
                orientationEventListener.enable()
            }
        } catch (_: Exception) {
            // Sensor service unavailable in headless environments
        }
        onDispose {
            try {
                orientationEventListener.disable()
            } catch (_: Exception) {}
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Improved Full Screen Mode: Edge-to-Edge Immersive System Bars
    LaunchedEffect(activity, view, isImmersiveFullScreen, controlsVisible, uiState.isControlsLocked) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isImmersiveFullScreen || uiState.isControlsLocked || !controlsVisible) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Persist brightness & volume on video change and restore original window brightness on player exit
    DisposableEffect(activity, view) {
        onDispose {
            viewModel.updateSavedPlayerBrightness(screenBrightness, activeFolderKey, activeVideo.path)
            viewModel.updateSavedPlayerVolume(gestureVolumeFraction, activeFolderKey, activeVideo.path)
            val window = activity?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
            PlayerGestureControlHelper.restoreWindowBrightness(activity, initialWindowBrightness)
        }
    }

    // Downmix & Pre-Amp Audio Processor
    val downmixProcessor = remember {
        Eac3DownmixAudioProcessor().apply {
            volumeBoostMultiplier = uiState.volumeBoostMultiplier
            nightModeCompressionEnabled = uiState.nightModeAudioEnabled || uiState.nightModeEnabled
            dialogueEnhancerEnabled = uiState.dialogueEnhancerEnabled
        }
    }

    LaunchedEffect(
        uiState.volumeBoostMultiplier,
        uiState.nightModeAudioEnabled,
        uiState.nightModeEnabled,
        uiState.dialogueEnhancerEnabled
    ) {
        downmixProcessor.volumeBoostMultiplier = uiState.volumeBoostMultiplier
        downmixProcessor.nightModeCompressionEnabled = uiState.nightModeAudioEnabled || uiState.nightModeEnabled
        downmixProcessor.dialogueEnhancerEnabled = uiState.dialogueEnhancerEnabled
    }

    // Hold screen wake-lock ONLY while video is actively playing; release immediately on pause or exit
    DisposableEffect(activity, isPlaying) {
        val window = activity?.window
        if (isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Apply in-app window brightness override
    LaunchedEffect(
        uiState.batterySaverModeEnabled,
        screenBrightness
    ) {
        PlayerGestureControlHelper.applyWindowBrightness(
            activity = activity,
            brightnessFraction = screenBrightness,
            batterySaverEnabled = uiState.batterySaverModeEnabled
        )
    }

    val memoryBudget = remember(uiState.lowRamShieldEnabled, uiState.batterySaverModeEnabled) {
        uiState.memoryBudget ?: MemoryManager.detectMemoryBudget(
            context = context,
            forceLowRamMode = uiState.lowRamShieldEnabled,
            batterySaverEnabled = uiState.batterySaverModeEnabled
        )
    }

    val fastPlayerManager = remember(
        uiState.decoderEngineMode,
        uiState.forceHardwareAccelerationHighBitrate,
        memoryBudget.tier,
        memoryBudget.batterySaverActive
    ) {
        val preferSw = (uiState.decoderEngineMode == DecoderEngineMode.SW ||
            uiState.decoderEngineMode == DecoderEngineMode.SW_PLUS ||
            uiState.decoderEngineMode == DecoderEngineMode.SOFTWARE_MULTI_CORE) &&
            !uiState.forceHardwareAccelerationHighBitrate

        val isSwEngine = uiState.decoderEngineMode == DecoderEngineMode.SW ||
            uiState.decoderEngineMode == DecoderEngineMode.SW_PLUS ||
            uiState.decoderEngineMode == DecoderEngineMode.SOFTWARE_MULTI_CORE

        val codecSelector = com.cyosplay.player.createXvidAwareMediaCodecSelector(
            fallbackSelector = AudioBadgeFormatter.createMultiCodecSelectorForEngine(
                decoderEngineMode = uiState.decoderEngineMode,
                forceHardwareForHighBitrate = uiState.forceHardwareAccelerationHighBitrate
            ),
            preferSoftwareForAllCodecs = isSwEngine
        )

        val loadControl = com.cyos.player.CyosFastPlayerManager.buildOptimizedLoadControl(
            chunkSizeBytes = memoryBudget.chunkSizeBytes,
            targetBufferBytes = memoryBudget.targetBufferBytes,
            minBufferMs = memoryBudget.minBufferMs,
            maxBufferMs = memoryBudget.maxBufferMs,
            bufferForPlaybackMs = minOf(memoryBudget.bufferForPlaybackMs, 350),
            bufferForPlaybackAfterRebufferMs = minOf(memoryBudget.bufferForPlaybackAfterRebufferMs, 1_000),
            backBufferDurationMs = maxOf(memoryBudget.backBufferDurationMs, 15_000),
            retainBackBufferFromKeyframe = true,
            prioritizeTimeOverSizeThresholds = true
        )

        com.cyos.player.CyosFastPlayerManager(
            context = context,
            customAudioProcessors = arrayOf(downmixProcessor),
            mediaCodecSelector = codecSelector,
            extensionRendererMode = if (uiState.decoderEngineMode == DecoderEngineMode.HW && !preferSw) {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            } else {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            },
            loadControlOverride = loadControl,
            subtitlesEnabledByUser = uiState.subtitlesEnabledByUser
        )
    }

    LaunchedEffect(fastPlayerManager, playlistQueue) {
        if (playlistQueue.isNotEmpty()) {
            fastPlayerManager.submitPlaylist(playlistQueue.map { MediaItem.fromUri(it.uri) })
        }
    }

    val exoPlayer = remember(fastPlayerManager) {
        fastPlayerManager.player.apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val recoveredFromSubtitle = com.cyosplay.player.handleNonFatalSubtitleErrorIfApplicable(
                            context = context,
                            player = this@apply,
                            error = error
                        ) {
                            liveEmbeddedCueTexts = emptyList()
                            pendingAttachedSubtitleUri = null
                            viewModel.clearAllSubtitles()
                        }
                        if (recoveredFromSubtitle) {
                            isMediaLoading = false
                            mediaErrorType = PlayerMediaErrorType.NONE
                            return
                        }
                        if (uiState.forceHardwareAccelerationHighBitrate) {
                            viewModel.setForceHardwareAccelerationHighBitrate(false)
                        }
                        val nextFallback = viewModel.fallbackToNextDecoderEngine()
                        if (nextFallback == null) {
                            isMediaLoading = false
                            val isNetwork = activeVideo.uri.scheme?.startsWith("http", ignoreCase = true) == true
                            mediaErrorType = FfmpegMatroskaCodecRegistry.classifyPlaybackError(
                                errorCode = error.errorCode,
                                errorMessage = error.message,
                                isNetworkSource = isNetwork,
                                durationMs = totalDurationMs
                            )
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_BUFFERING) {
                            if (mediaErrorType == PlayerMediaErrorType.NONE) {
                                isMediaLoading = true
                            }
                        } else if (playbackState == Player.STATE_READY) {
                            isMediaLoading = false
                            mediaErrorType = PlayerMediaErrorType.NONE
                            val dur = duration
                            if (dur > 0L) {
                                totalDurationMs = dur
                            }
                            if (!isUserScrubbing && currentPosition >= 0L) {
                                currentPositionMs = currentPosition
                            }
                        } else if (playbackState == Player.STATE_ENDED) {
                            isMediaLoading = false
                            val idx = playlistQueue.indexOfFirst { it.path == activeVideo.path }
                            if (idx >= 0 && idx + 1 < playlistQueue.size) {
                                activeVideo = playlistQueue[idx + 1]
                                currentPositionMs = 0L
                                isPlaying = true
                                playWhenReady = true
                            } else {
                                seekTo(0L)
                                currentPositionMs = 0L
                                isPlaying = false
                                playWhenReady = false
                            }
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            activeVideo = activeVideo.copy(
                                width = videoSize.width,
                                height = videoSize.height
                            )
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        val discoveredAudio = mutableListOf<AudioTrackInfo>()
                        val discoveredSubtitles = mutableListOf<SubtitleTrackInfo>()
                        var detectedVideoMime: String? = null
                        var detectedVideoFps = 60f

                        tracks.groups.forEachIndexed { groupIndex, group ->
                            when (group.type) {
                                C.TRACK_TYPE_VIDEO -> {
                                    for (trackIndex in 0 until group.length) {
                                        if (group.isTrackSelected(trackIndex)) {
                                            val vFormat = group.getTrackFormat(trackIndex)
                                            detectedVideoMime = vFormat.sampleMimeType
                                            if (vFormat.frameRate > 0f) {
                                                detectedVideoFps = vFormat.frameRate.coerceIn(24f, 240f)
                                            }
                                        }
                                    }
                                }
                                C.TRACK_TYPE_AUDIO -> {
                                    for (trackIndex in 0 until group.length) {
                                        val format = group.getTrackFormat(trackIndex)
                                        val langCode = format.language ?: "und"
                                        val langDisplay = if (langCode != "und") {
                                            Locale.forLanguageTag(langCode).displayLanguage.ifBlank { langCode.uppercase() }
                                        } else {
                                            "Track ${discoveredAudio.size + 1}"
                                        }
                                        val customLabel = format.label?.takeIf { it.isNotBlank() }
                                        val fullLabel = if (customLabel != null && !customLabel.equals(langDisplay, ignoreCase = true)) {
                                            "$langDisplay ($customLabel)"
                                        } else {
                                            langDisplay
                                        }
                                        val channels = if (format.channelCount > 0) format.channelCount else 2
                                        val sampleRate = if (format.sampleRate > 0) format.sampleRate else 44100
                                        val codecBadge = AudioBadgeFormatter.formatAudioCodecBadge(
                                            format.sampleMimeType,
                                            channels,
                                            customLabel ?: activeVideo.displayName,
                                            sampleRate
                                        )
                                        discoveredAudio.add(
                                            AudioTrackInfo(
                                                groupIndex = groupIndex,
                                                trackIndex = trackIndex,
                                                label = fullLabel,
                                                language = langDisplay,
                                                codecBadge = codecBadge,
                                                channelCount = channels,
                                                sampleRate = sampleRate,
                                                isSelected = group.isTrackSelected(trackIndex)
                                            )
                                        )
                                    }
                                }
                                C.TRACK_TYPE_TEXT -> {
                                    for (trackIndex in 0 until group.length) {
                                        val format = group.getTrackFormat(trackIndex)
                                        val langDisplay = SubtitleParser.resolveLanguageDisplayLabel(
                                            rawLanguageCode = format.language,
                                            fallbackIndex = discoveredSubtitles.size + 1
                                        )
                                        val customLabel = format.label?.takeIf { it.isNotBlank() }
                                        val fullLabel = if (customLabel != null && !customLabel.equals(langDisplay, ignoreCase = true)) {
                                            "$langDisplay ($customLabel)"
                                        } else {
                                            langDisplay
                                        }
                                        val previouslySelected = uiState.subtitleTracks.any {
                                            it.groupIndex == groupIndex && it.trackIndex == trackIndex && it.isSelected
                                        }
                                        discoveredSubtitles.add(
                                            SubtitleTrackInfo(
                                                groupIndex = groupIndex,
                                                trackIndex = trackIndex,
                                                label = fullLabel,
                                                language = langDisplay,
                                                isSelected = uiState.subtitlesEnabledByUser && previouslySelected
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Item 1: Restore remembered per-video audio track selection when video tracks become ready
                        if (!hasAppliedSavedAudioForVideo && discoveredAudio.isNotEmpty()) {
                            hasAppliedSavedAudioForVideo = true
                            val savedAudio = viewModel.getSavedAudioTrackForVideo(activeVideo.path)
                            if (savedAudio != null) {
                                if (savedAudio.isMuted) {
                                    trackSelectionParameters = trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                                        .build()
                                    viewModel.setAudioMuted(true, activeVideo.path)
                                } else {
                                    val matchedTrack = discoveredAudio.firstOrNull {
                                        it.groupIndex == savedAudio.groupIndex && it.trackIndex == savedAudio.trackIndex
                                    } ?: discoveredAudio.firstOrNull {
                                        savedAudio.language.isNotBlank() && it.language.equals(savedAudio.language, ignoreCase = true)
                                    } ?: discoveredAudio.firstOrNull {
                                        savedAudio.label.isNotBlank() && it.label.equals(savedAudio.label, ignoreCase = true)
                                    }
                                    if (matchedTrack != null) {
                                        val groups = tracks.groups
                                        if (matchedTrack.groupIndex in groups.indices) {
                                            val audioGroup = groups[matchedTrack.groupIndex]
                                            if (matchedTrack.trackIndex in 0 until audioGroup.length) {
                                                trackSelectionParameters = trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(
                                                            audioGroup.mediaTrackGroup,
                                                            listOf(matchedTrack.trackIndex)
                                                        )
                                                    )
                                                    .build()
                                                for (i in discoveredAudio.indices) {
                                                    val item = discoveredAudio[i]
                                                    discoveredAudio[i] = item.copy(
                                                        isSelected = item.groupIndex == matchedTrack.groupIndex &&
                                                            item.trackIndex == matchedTrack.trackIndex
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val selectedAudioBadge = discoveredAudio.firstOrNull { it.isSelected }?.codecBadge
                        val updatedVideoBadge = AudioBadgeFormatter.formatVideoCodecBadge(
                            detectedVideoMime ?: activeVideo.mimeType,
                            activeVideo.width,
                            activeVideo.height,
                            activeVideo.displayName
                        )
                        activeVideo = activeVideo.copy(
                            audioCodecHint = selectedAudioBadge ?: activeVideo.audioCodecHint,
                            videoCodecHint = updatedVideoBadge
                        )
                        viewModel.updateDetectedStreamFps(detectedVideoFps)
                        viewModel.updateAvailableTracks(
                            audioTracks = discoveredAudio,
                            subtitleTracks = discoveredSubtitles
                        )
                    }

                    override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                        val formatted = cueGroup.cues.mapNotNull { cue ->
                            cue.text?.toString()?.let { SubtitleParser.formatSubtitleTextForDisplay(it) }?.takeIf { it.isNotBlank() }
                        }
                        liveEmbeddedCueTexts = formatted
                    }
                })
            }
    }

    LaunchedEffect(activeVideo.uri, fastPlayerManager) {
        viewModel.discoverSidecarSubtitlesForVideo(activeVideo)
        // Detach and re-attach surface before loading the next item to prevent MediaCodec.configure()
        // surface disconnect issues when switching between software-decoded (XVID/FFmpeg) and hardware-decoded videos
        activePlayerViewRef?.let { pv ->
            if (pv.player === exoPlayer) {
                pv.player = null
                pv.player = exoPlayer
            }
        }
        val queueIndex = playlistQueue.indexOfFirst { it.path == activeVideo.path }
        val startPos = if (currentPositionMs > 0L && !uiState.stealthPlaybackEnabled) currentPositionMs else 0L
        if (queueIndex >= 0 && fastPlayerManager.getCurrentPlaylistSize() == playlistQueue.size) {
            fastPlayerManager.playAtIndex(
                index = queueIndex,
                startPositionMs = startPos,
                autoPlay = isPlaying
            )
        } else {
            val item = MediaItem.fromUri(activeVideo.uri)
            if (startPos > 0L) {
                exoPlayer.setMediaItem(item, startPos)
            } else {
                exoPlayer.setMediaItem(item)
            }
            exoPlayer.prepare()
            exoPlayer.playWhenReady = isPlaying
        }
        if (!uiState.stealthPlaybackEnabled) {
            viewModel.settingsRepository.saveLastPlayedVideoForFolder(activeVideo.folderId, activeVideo.path)
        }
    }

    LaunchedEffect(exoPlayer, pendingAttachedSubtitleUri) {
        val subUri = pendingAttachedSubtitleUri ?: return@LaunchedEffect
        com.cyosplay.player.attachExternalSubtitle(
            context = context,
            player = exoPlayer,
            videoUri = activeVideo.uri,
            subtitleUri = subUri
        )
    }

    // Synchronize embedded C.TRACK_TYPE_TEXT track selection with ExoPlayer whenever subtitle state changes
    LaunchedEffect(exoPlayer, uiState.subtitlesEnabledByUser, uiState.subtitleTracks) {
        val selectedEmbeddedList = if (uiState.subtitlesEnabledByUser) {
            uiState.subtitleTracks.filter { it.isSelected }
        } else {
            emptyList()
        }
        if (selectedEmbeddedList.isEmpty()) {
            liveEmbeddedCueTexts = emptyList()
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
        } else {
            val groups = exoPlayer.currentTracks.groups
            val builder = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            selectedEmbeddedList.groupBy { it.groupIndex }.forEach { (groupIdx, tracksInGroup) ->
                if (groupIdx in groups.indices) {
                    val group = groups[groupIdx]
                    val validTrackIndices = tracksInGroup.map { it.trackIndex }.filter { it in 0 until group.length }
                    if (validTrackIndices.isNotEmpty()) {
                        builder.addOverride(
                            TrackSelectionOverride(
                                group.mediaTrackGroup,
                                validTrackIndices
                            )
                        )
                    }
                }
            }
            exoPlayer.trackSelectionParameters = builder.build()
        }
    }

    // Synchronize audio volume with both ExoPlayer and Android AudioManager STREAM_MUSIC silently (flags = 0)
    LaunchedEffect(exoPlayer, gestureVolumeFraction, uiState.isAudioMuted) {
        val safeVol = PlayerGestureControlHelper.sanitizeVolume(gestureVolumeFraction)
        exoPlayer.volume = if (uiState.isAudioMuted) 0f else safeVol
        PlayerGestureControlHelper.syncVolumeWithAudioManager(
            context = context,
            volumeFraction = safeVol,
            isMuted = uiState.isAudioMuted
        )
    }

    // Apply normal playbackSpeed (1x..5x) or Hold-to-Speed (1x..5x hold + horizontal drag)
    LaunchedEffect(uiState.playbackSpeed, isHold2xSpeedActive, holdSpeedMultiplier, exoPlayer) {
        val effectiveSpeed = if (isHold2xSpeedActive) {
            holdSpeedMultiplier.coerceIn(1.0f, 5.0f)
        } else {
            uiState.playbackSpeed.coerceIn(0.25f, 5.0f)
        }
        exoPlayer.setPlaybackSpeed(effectiveSpeed)
    }

    // CPU-optimized position & checkpoint loop (Items 22 & 23: pauses when video is paused; throttles when controls hidden)
    LaunchedEffect(
        exoPlayer,
        isPlaying,
        controlsVisible,
        uiState.isControlsLocked,
        isUserScrubbing,
        memoryBudget.positionPollingIntervalMs,
        uiState.abRepeatStartMs,
        uiState.abRepeatEndMs,
        uiState.stealthPlaybackEnabled
    ) {
        var lastCheckpointSavedMs = System.currentTimeMillis()
        while (isPlaying) {
            val dur = exoPlayer.duration
            if (dur > 0L) {
                totalDurationMs = dur
            }
            if (!isUserScrubbing && activeGestureLock != PlayerGestureLock.PINCH_ZOOM) {
                val pos = exoPlayer.currentPosition
                if (pos >= 0L && kotlin.math.abs(pos - currentPositionMs) >= 150L) {
                    currentPositionMs = pos
                }
            }

            // Periodic 3-second Last-Resume checkpoint (skipped in Stealth Playback - Item 5)
            val now = System.currentTimeMillis()
            if (!uiState.stealthPlaybackEnabled && now - lastCheckpointSavedMs >= 3000L && currentPositionMs > 0L) {
                viewModel.savePlaybackProgress(activeVideo.path, currentPositionMs, totalDurationMs)
                lastCheckpointSavedMs = now
            }

            // A-B Repeat loop check
            val abStart = uiState.abRepeatStartMs
            val abEnd = uiState.abRepeatEndMs
            if (abStart != null && abEnd != null && currentPositionMs >= abEnd) {
                exoPlayer.seekTo(abStart)
                currentPositionMs = abStart
            }

            val hasActiveSubtitles = uiState.subtitlesEnabledByUser && uiState.externalSubtitleCues.isNotEmpty()
            val hasAbLoop = uiState.abRepeatStartMs != null && uiState.abRepeatEndMs != null
            val isGestureBusy = isScreenTouchSlideSkipping || activeGestureLock != PlayerGestureLock.NONE
            val pollDelayMs = when {
                isGestureBusy -> 1000L
                uiState.batterySaverModeEnabled -> 500L // Battery Smoothness Shield power saving
                controlsVisible && !uiState.isControlsLocked -> minOf(320L, memoryBudget.positionPollingIntervalMs)
                hasActiveSubtitles || hasAbLoop -> 420L
                else -> maxOf(1600L, memoryBudget.positionPollingIntervalMs)
            }
            delay(pollDelayMs)
        }
    }

    // Automatically hide temporary Brightness, Volume, or Seek overlay ~1000ms after user stops adjusting
    val isActivelyAdjustingVertical =
        activeGestureLock == PlayerGestureLock.VERTICAL_BRIGHTNESS ||
            activeGestureLock == PlayerGestureLock.VERTICAL_VOLUME
    LaunchedEffect(activeHudType, hudLabelText, lastHudUpdateTimestampMs, isActivelyAdjustingVertical) {
        if (activeHudType != null && !isActivelyAdjustingVertical) {
            delay(PlayerGestureControlHelper.OVERLAY_AUTO_HIDE_DELAY_MS)
            val elapsed = SystemClock.elapsedRealtime() - lastHudUpdateTimestampMs
            if (elapsed >= PlayerGestureControlHelper.OVERLAY_AUTO_HIDE_DELAY_MS - 60L &&
                activeGestureLock != PlayerGestureLock.VERTICAL_BRIGHTNESS &&
                activeGestureLock != PlayerGestureLock.VERTICAL_VOLUME
            ) {
                activeHudType = null
            }
        }
    }

    DisposableEffect(fastPlayerManager, uiState.stealthPlaybackEnabled) {
        viewModel.registerActiveExoPlayer(exoPlayer)
        com.cyos.player.CyosPlayerMediaStoreDeleter.activeExoPlayerInstance = exoPlayer
        onDispose {
            if (com.cyos.player.CyosPlayerMediaStoreDeleter.activeExoPlayerInstance === exoPlayer) {
                com.cyos.player.CyosPlayerMediaStoreDeleter.activeExoPlayerInstance = null
            }
            viewModel.unregisterActiveExoPlayer(exoPlayer)
            if (!uiState.stealthPlaybackEnabled) {
                val finalPos = exoPlayer.currentPosition.coerceAtLeast(currentPositionMs)
                val finalDur = exoPlayer.duration.takeIf { it > 0L } ?: totalDurationMs
                viewModel.savePlaybackProgress(activeVideo.path, finalPos, finalDur)
            }
            fastPlayerManager.release()
        }
    }

    // Live External Subtitle Cue (Primary & Secondary Dual Subtitles)
    val currentSubtitleCue = remember(
        uiState.subtitlesEnabledByUser,
        uiState.externalSubtitleCues,
        currentPositionMs,
        uiState.subtitleSyncOffsetMs
    ) {
        if (!uiState.subtitlesEnabledByUser) {
            null
        } else {
            SubtitleParser.findActiveSubtitleCue(
                cues = uiState.externalSubtitleCues,
                positionMs = currentPositionMs,
                offsetMs = uiState.subtitleSyncOffsetMs
            )
        }
    }

    val currentSecondarySubtitleCue = remember(
        uiState.subtitlesEnabledByUser,
        uiState.dualSubtitlesEnabled,
        uiState.secondaryExternalSubtitleCues,
        currentPositionMs,
        uiState.subtitleSyncOffsetMs
    ) {
        if (!uiState.subtitlesEnabledByUser || !uiState.dualSubtitlesEnabled) {
            null
        } else {
            SubtitleParser.findActiveSubtitleCue(
                cues = uiState.secondaryExternalSubtitleCues,
                positionMs = currentPositionMs,
                offsetMs = uiState.subtitleSyncOffsetMs
            )
        }
    }

    // Item 2 & 6: Multi-subtitle playable all simultaneous cues (External + Embedded tracks working at the same time)
    val hasSelectedEmbeddedSubtitle = remember(uiState.subtitlesEnabledByUser, uiState.subtitleTracks) {
        uiState.subtitlesEnabledByUser && uiState.subtitleTracks.any { it.isSelected }
    }

    val activeMultiSubtitleCues = remember(
        uiState.subtitlesEnabledByUser,
        uiState.selectedExternalSubtitleTrackIds,
        uiState.externalSubtitleTracks,
        uiState.externalSubtitleCues,
        uiState.secondaryExternalSubtitleCues,
        uiState.dualSubtitlesEnabled,
        uiState.subtitleTracks,
        liveEmbeddedCueTexts,
        currentPositionMs,
        uiState.subtitleSyncOffsetMs
    ) {
        if (!uiState.subtitlesEnabledByUser) {
            emptyList<com.cyosplay.player.data.model.ActiveSubtitleDisplayItem>()
        } else {
            val tracksToPoll = if (uiState.selectedExternalSubtitleTrackIds.isNotEmpty()) {
                uiState.externalSubtitleTracks.filter { it.id in uiState.selectedExternalSubtitleTrackIds || it.isSelected }
            } else {
                uiState.externalSubtitleTracks.filter { it.isSelected }
            }

            val items = mutableListOf<com.cyosplay.player.data.model.ActiveSubtitleDisplayItem>()
            val colorPalette = listOf(
                uiState.subtitleColor.hexColor,
                0xFFFFD700L, // Gold
                0xFF00E5FFL, // Cyan
                0xFF69F0AEL, // Mint Green
                0xFFFF80ABL  // Soft Rose
            )
            tracksToPoll.forEachIndexed { index, track ->
                val cue = SubtitleParser.findActiveSubtitleCue(
                    cues = track.cues,
                    positionMs = currentPositionMs,
                    offsetMs = uiState.subtitleSyncOffsetMs
                )
                if (!cue.isNullOrBlank()) {
                    val color = colorPalette[index % colorPalette.size]
                    items.add(
                        com.cyosplay.player.data.model.ActiveSubtitleDisplayItem(
                            trackId = track.id,
                            trackLabel = track.label,
                            cueText = cue,
                            colorHex = color
                        )
                    )
                }
            }

            if (items.isEmpty() && uiState.externalSubtitleCues.isNotEmpty()) {
                val primaryCue = SubtitleParser.findActiveSubtitleCue(
                    cues = uiState.externalSubtitleCues,
                    positionMs = currentPositionMs,
                    offsetMs = uiState.subtitleSyncOffsetMs
                )
                if (!primaryCue.isNullOrBlank()) {
                    items.add(
                        com.cyosplay.player.data.model.ActiveSubtitleDisplayItem(
                            trackId = "primary_ext",
                            trackLabel = "Primary Subtitle",
                            cueText = primaryCue,
                            colorHex = uiState.subtitleColor.hexColor
                        )
                    )
                }
                if (uiState.dualSubtitlesEnabled && uiState.secondaryExternalSubtitleCues.isNotEmpty()) {
                    val secCue = SubtitleParser.findActiveSubtitleCue(
                        cues = uiState.secondaryExternalSubtitleCues,
                        positionMs = currentPositionMs,
                        offsetMs = uiState.subtitleSyncOffsetMs
                    )
                    if (!secCue.isNullOrBlank()) {
                        items.add(
                            com.cyosplay.player.data.model.ActiveSubtitleDisplayItem(
                                trackId = "secondary_ext",
                                trackLabel = "Secondary Subtitle",
                                cueText = secCue,
                                colorHex = 0xFFFFD700
                            )
                        )
                    }
                }
            }

            // Include active embedded subtitle cues simultaneously when external tracks are also active
            if (hasSelectedEmbeddedSubtitle && liveEmbeddedCueTexts.isNotEmpty() && items.isNotEmpty()) {
                val selectedEmbedded = uiState.subtitleTracks.filter { it.isSelected }
                liveEmbeddedCueTexts.forEachIndexed { embIdx, embText ->
                    if (items.none { it.cueText.equals(embText, ignoreCase = true) }) {
                        val embLabel = selectedEmbedded.getOrNull(embIdx)?.label ?: "Embedded Subtitle"
                        val color = colorPalette[(items.size + embIdx) % colorPalette.size]
                        items.add(
                            com.cyosplay.player.data.model.ActiveSubtitleDisplayItem(
                                trackId = "embedded_$embIdx",
                                trackLabel = embLabel,
                                cueText = embText,
                                colorHex = color
                            )
                        )
                    }
                }
            }
            items
        }
    }

    // Memoize RenderEffect outside AndroidView.update to eliminate per-frame allocations during zoom/skip
    val memoizedEnhancementRenderEffect = remember(
        uiState.smartEnhancementMode,
        customClarityBoost,
        customContrastDelta,
        customSaturationDelta
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mode = uiState.smartEnhancementMode
            if (mode == SmartEnhancementMode.OFF) {
                null
            } else {
                val c = (mode.contrast + customContrastDelta + (customClarityBoost * 0.15f)).coerceIn(0.8f, 1.75f)
                val s = (mode.saturation + customSaturationDelta).coerceIn(0.5f, 1.85f)
                val translate = (1f - c) * 128f + mode.brightnessOffset
                val contrastMatrix = ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, translate,
                        0f, c, 0f, 0f, translate,
                        0f, 0f, c, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                val satMatrix = ColorMatrix().apply { setSaturation(s) }
                contrastMatrix.preConcat(satMatrix)
                RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(contrastMatrix))
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Multi-pointer supervisor & 2-finger pinch-to-zoom engine:
            // Strictly activates ONLY when 2 or more fingers are pressed so single-finger
            // volume, brightness, horizontal skipping, and hold-skip never get intercepted.
            .pointerInput(uiState.isControlsLocked) {
                var lastZoomHudElapsedMs = 0L
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        activePointerCount = pressedCount
                        if (pressedCount == 1 && activeGestureLock != PlayerGestureLock.PINCH_ZOOM) {
                            if (activeGestureLock != PlayerGestureLock.VERTICAL_BRIGHTNESS) {
                                preZoomBrightnessSnapshot = screenBrightness
                            }
                            if (activeGestureLock != PlayerGestureLock.VERTICAL_VOLUME) {
                                preGestureVolumeSnapshot = gestureVolumeFraction
                            }
                        } else if (pressedCount >= 2 && !uiState.isControlsLocked) {
                            lastMultiTouchEndedElapsedMs = SystemClock.elapsedRealtime()
                            if (activeGestureLock != PlayerGestureLock.PINCH_ZOOM) {
                                activeGestureLock = PlayerGestureLock.PINCH_ZOOM
                                isHold2xSpeedActive = false
                                isScreenTouchSlideSkipping = false
                            }
                            // Strictly keep both brightness and volume unchanged during zoom: restore snapshots if first finger drifted
                            val lockedBrightness = PlayerGestureControlHelper.resolveBrightnessDuringZoomOrMultiTouch(
                                currentBrightness = screenBrightness,
                                preZoomBrightnessSnapshot = preZoomBrightnessSnapshot,
                                activePointerCount = pressedCount,
                                isPinchZoomActive = true,
                                recentlyPinched = true
                            )
                            if (kotlin.math.abs(screenBrightness - lockedBrightness) > 0.0005f) {
                                screenBrightness = lockedBrightness
                                PlayerGestureControlHelper.applyWindowBrightness(
                                    activity = activity,
                                    brightnessFraction = screenBrightness,
                                    batterySaverEnabled = uiState.batterySaverModeEnabled
                                )
                            }
                            val lockedVolume = PlayerGestureControlHelper.resolveVolumeDuringNonVolumeGesture(
                                currentVolume = gestureVolumeFraction,
                                preGestureVolumeSnapshot = preGestureVolumeSnapshot,
                                activePointerCount = pressedCount,
                                isPinchZoomActive = true,
                                isBrightnessActive = false,
                                isHorizontalSeekActive = false,
                                isHoldFastForwardActive = false
                            )
                            if (kotlin.math.abs(gestureVolumeFraction - lockedVolume) > 0.0005f) {
                                gestureVolumeFraction = lockedVolume
                                exoPlayer.volume = if (uiState.isAudioMuted) 0f else gestureVolumeFraction
                                PlayerGestureControlHelper.syncVolumeWithAudioManager(
                                    context = context,
                                    volumeFraction = gestureVolumeFraction,
                                    isMuted = uiState.isAudioMuted
                                )
                            }
                            if (activeHudType == HudType.BRIGHTNESS || activeHudType == HudType.VOLUME) {
                                activeHudType = null
                            }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (kotlin.math.abs(zoomChange - 1.0f) > 0.006f ||
                                kotlin.math.abs(panChange.x) > 0.8f ||
                                kotlin.math.abs(panChange.y) > 0.8f
                            ) {
                                registerUserInteraction()
                                val updatedScale = (liveZoomScale * zoomChange).coerceIn(0.5f, 4.0f)
                                if (kotlin.math.abs(updatedScale - liveZoomScale) >= 0.005f) {
                                    liveZoomScale = updatedScale
                                }
                                if (updatedScale > 1.01f) {
                                    val maxPanX = (size.width * (updatedScale - 1f)) / 2f
                                    val maxPanY = (size.height * (updatedScale - 1f)) / 2f
                                    livePanX = (livePanX + panChange.x).coerceIn(-maxPanX, maxPanX)
                                    livePanY = (livePanY + panChange.y).coerceIn(-maxPanY, maxPanY)
                                } else {
                                    livePanX = 0f
                                    livePanY = 0f
                                }
                                val nowElapsed = SystemClock.elapsedRealtime()
                                if (nowElapsed - lastZoomHudElapsedMs >= 160L) {
                                    lastZoomHudElapsedMs = nowElapsed
                                    lastHudUpdateTimestampMs = nowElapsed
                                    activeHudType = HudType.SEEK
                                    hudLabelText = "Zoom ${(updatedScale * 100f).roundToInt()}%"
                                }
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        } else if (pressedCount == 0) {
                            if (activeGestureLock == PlayerGestureLock.PINCH_ZOOM) {
                                lastMultiTouchEndedElapsedMs = SystemClock.elapsedRealtime()
                                screenBrightness = PlayerGestureControlHelper.resolveBrightnessDuringZoomOrMultiTouch(
                                    currentBrightness = screenBrightness,
                                    preZoomBrightnessSnapshot = preZoomBrightnessSnapshot,
                                    activePointerCount = 2,
                                    isPinchZoomActive = true,
                                    recentlyPinched = true
                                )
                                gestureVolumeFraction = PlayerGestureControlHelper.resolveVolumeDuringNonVolumeGesture(
                                    currentVolume = gestureVolumeFraction,
                                    preGestureVolumeSnapshot = preGestureVolumeSnapshot,
                                    activePointerCount = 2,
                                    isPinchZoomActive = true
                                )
                                PlayerGestureControlHelper.applyWindowBrightness(
                                    activity = activity,
                                    brightnessFraction = screenBrightness,
                                    batterySaverEnabled = uiState.batterySaverModeEnabled
                                )
                                viewModel.setVideoZoomScale(liveZoomScale)
                                zoomPanOffset = Offset(livePanX, livePanY)
                            }
                            if (isHold2xSpeedActive) {
                                isHold2xSpeedActive = false
                            }
                            isScreenTouchSlideSkipping = false
                            activeGestureLock = PlayerGestureLock.NONE
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(uiState.isControlsLocked) {
                detectTapGestures(
                    onPress = {
                        registerUserInteraction()
                        try {
                            tryAwaitRelease()
                        } finally {
                            if (isHold2xSpeedActive) {
                                isHold2xSpeedActive = false
                            }
                            if (activeGestureLock == PlayerGestureLock.HOLD_FAST_FORWARD) {
                                activeGestureLock = PlayerGestureLock.NONE
                            }
                        }
                    },
                    onLongPress = {
                        val nowElapsed = SystemClock.elapsedRealtime()
                        val recentlyPinched = nowElapsed - lastMultiTouchEndedElapsedMs < 320L
                        val recentlyDoubleTapped = nowElapsed - lastDoubleTapSeekElapsedMs < 280L
                        val canHoldSpeed = PlayerGestureControlHelper.canStartHoldFastForward(
                            isControlsLocked = uiState.isControlsLocked,
                            isInPipMode = uiState.isInPipMode,
                            activePointerCount = activePointerCount,
                            isPinchZoomActive = activeGestureLock == PlayerGestureLock.PINCH_ZOOM,
                            isVerticalGestureActive = activeGestureLock == PlayerGestureLock.VERTICAL_BRIGHTNESS ||
                                activeGestureLock == PlayerGestureLock.VERTICAL_VOLUME,
                            isHorizontalSeekingActive = isScreenTouchSlideSkipping ||
                                activeGestureLock == PlayerGestureLock.HORIZONTAL_SKIP,
                            recentlyPinched = recentlyPinched,
                            isDoubleTapSkipActive = recentlyDoubleTapped
                        )
                        if (canHoldSpeed && activeGestureLock == PlayerGestureLock.NONE) {
                            registerUserInteraction()
                            activeGestureLock = PlayerGestureLock.HOLD_FAST_FORWARD
                            isScreenTouchSlideSkipping = false
                            val initialHoldSpeed = maxOf(2.0f, uiState.playbackSpeed.coerceIn(1.0f, 5.0f))
                            holdSpeedMultiplier = initialHoldSpeed
                            isHold2xSpeedActive = true
                            if (!isPlaying) {
                                isPlaying = true
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                            }
                        }
                    },
                    onTap = {
                        val nowElapsed = SystemClock.elapsedRealtime()
                        val recentlyPinched = nowElapsed - lastMultiTouchEndedElapsedMs < 260L
                        val recentlyDoubleTapped = nowElapsed - lastDoubleTapSeekElapsedMs < 240L
                        if (!recentlyPinched &&
                            !recentlyDoubleTapped &&
                            activePointerCount <= 1 &&
                            activeGestureLock == PlayerGestureLock.NONE &&
                            !isHold2xSpeedActive &&
                            !isScreenTouchSlideSkipping
                        ) {
                            registerUserInteraction()
                            if (uiState.isControlsLocked) {
                                lockedButtonVisible = true
                            } else {
                                controlsVisible = !controlsVisible
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        val nowElapsed = SystemClock.elapsedRealtime()
                        val recentlyPinched = nowElapsed - lastMultiTouchEndedElapsedMs < 320L
                        val canDoubleTapSkip = PlayerGestureControlHelper.canHandleDoubleTapSkip(
                            isControlsLocked = uiState.isControlsLocked,
                            isInPipMode = uiState.isInPipMode,
                            activePointerCount = activePointerCount,
                            isPinchZoomActive = activeGestureLock == PlayerGestureLock.PINCH_ZOOM,
                            recentlyPinched = recentlyPinched,
                            isVerticalGestureActive = activeGestureLock == PlayerGestureLock.VERTICAL_BRIGHTNESS ||
                                activeGestureLock == PlayerGestureLock.VERTICAL_VOLUME,
                            isHorizontalSeekingActive = isScreenTouchSlideSkipping ||
                                activeGestureLock == PlayerGestureLock.HORIZONTAL_SKIP,
                            isHoldFastForwardActive = isHold2xSpeedActive ||
                                activeGestureLock == PlayerGestureLock.HOLD_FAST_FORWARD,
                            allowWhenLocked = true
                        )
                        if (canDoubleTapSkip && activeGestureLock == PlayerGestureLock.NONE) {
                            registerUserInteraction()
                            val isLeft = offset.x < size.width / 2f
                            // Debounce rapid double-tap seeks with CLOSEST_SYNC to prevent decoder queue spikes, CPU load & battery drain
                            val target = if (nowElapsed - lastDoubleTapSeekElapsedMs >= 160L) {
                                lastDoubleTapSeekElapsedMs = nowElapsed
                                exoPlayer.playWhenReady = true
                                val seeked = if (isLeft) {
                                    com.cyos.player.CyosFastPlayerManager.skipBackward10s(exoPlayer)
                                } else {
                                    com.cyos.player.CyosFastPlayerManager.skipForward10s(exoPlayer)
                                }
                                if (!exoPlayer.isPlaying) {
                                    exoPlayer.play()
                                }
                                seeked
                            } else {
                                val delta = if (isLeft) -10_000L else 10_000L
                                (currentPositionMs + delta).coerceIn(0L, totalDurationMs)
                            }
                            currentPositionMs = target
                            isPlaying = true
                            lastHudUpdateTimestampMs = nowElapsed
                            activeHudType = HudType.SEEK
                            hudLabelText = if (isLeft) "-10s • ${Formatters.formatDuration(target)}" else "+10s • ${Formatters.formatDuration(target)}"
                        } else if (uiState.isControlsLocked) {
                            lockedButtonVisible = true
                        }
                    }
                )
            }
            // Unified 1-finger directional drag controller with strict dominant-axis lock:
            // Isolates VERTICAL_BRIGHTNESS (left screen), VERTICAL_VOLUME (right screen),
            // HORIZONTAL_SKIP (horizontal slide seek), and HOLD_FAST_FORWARD (1x..5x speed)
            // so they never interact or interfere with each other.
            .pointerInput(uiState.isControlsLocked, uiState.isInPipMode, isPlaying, activeVideo.path, activeFolderKey, minSwipeDistancePx) {
                var startZone = VideoGestureZone.OUTSIDE_VIDEO_AREA
                var lockedGesture = PlayerGestureLock.NONE
                var totalDx = 0f
                var totalDy = 0f
                var lastPersistElapsedMs = 0L
                var startHoldSpeed = 2.0f
                var touchSlideStartPosMs = 0L
                var touchSlideAccumulatedMs = 0L
                var lastSeekDispatchElapsedMs = 0L
                var dragStartBrightnessSnapshot = screenBrightness
                var dragStartVolumeSnapshot = gestureVolumeFraction
                var dragStartElapsedMs = 0L

                fun finishSingleFingerDrag() {
                    val nowElapsed = SystemClock.elapsedRealtime()
                    val recentlyPinched = nowElapsed - lastMultiTouchEndedElapsedMs < 450L
                    when (lockedGesture) {
                        PlayerGestureLock.VERTICAL_BRIGHTNESS -> {
                            if (activePointerCount > 1 || activeGestureLock == PlayerGestureLock.PINCH_ZOOM || recentlyPinched) {
                                screenBrightness = dragStartBrightnessSnapshot
                                PlayerGestureControlHelper.applyWindowBrightness(
                                    activity = activity,
                                    brightnessFraction = screenBrightness,
                                    batterySaverEnabled = uiState.batterySaverModeEnabled
                                )
                            } else {
                                lastHudUpdateTimestampMs = nowElapsed
                                viewModel.updateSavedPlayerBrightness(screenBrightness, activeFolderKey, activeVideo.path)
                            }
                        }
                        PlayerGestureLock.VERTICAL_VOLUME -> {
                            if (activePointerCount > 1 || activeGestureLock == PlayerGestureLock.PINCH_ZOOM || recentlyPinched) {
                                gestureVolumeFraction = dragStartVolumeSnapshot
                                exoPlayer.volume = if (uiState.isAudioMuted) 0f else gestureVolumeFraction
                            } else {
                                lastHudUpdateTimestampMs = nowElapsed
                                viewModel.updateSavedPlayerVolume(gestureVolumeFraction, activeFolderKey, activeVideo.path)
                            }
                        }
                        PlayerGestureLock.HORIZONTAL_SKIP -> {
                            if (isScreenTouchSlideSkipping) {
                                val finalTarget = currentPositionMs.coerceIn(0L, totalDurationMs)
                                exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                                exoPlayer.seekTo(finalTarget)
                                lastHudUpdateTimestampMs = nowElapsed
                            }
                        }
                        else -> Unit
                    }
                    startZone = VideoGestureZone.OUTSIDE_VIDEO_AREA
                    totalDx = 0f
                    totalDy = 0f
                    isScreenTouchSlideSkipping = false
                    if (isHold2xSpeedActive) {
                        isHold2xSpeedActive = false
                    }
                    if (activeGestureLock == lockedGesture ||
                        activeGestureLock == PlayerGestureLock.HORIZONTAL_SKIP ||
                        activeGestureLock == PlayerGestureLock.HOLD_FAST_FORWARD
                    ) {
                        activeGestureLock = PlayerGestureLock.NONE
                    }
                    lockedGesture = PlayerGestureLock.NONE
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        val nowElapsed = SystemClock.elapsedRealtime()
                        val recentlyPinched = nowElapsed - lastMultiTouchEndedElapsedMs < 450L
                        totalDx = 0f
                        totalDy = 0f
                        touchSlideStartPosMs = currentPositionMs
                        touchSlideAccumulatedMs = 0L
                        lastSeekDispatchElapsedMs = 0L
                        startHoldSpeed = holdSpeedMultiplier
                        dragStartBrightnessSnapshot = screenBrightness
                        dragStartVolumeSnapshot = gestureVolumeFraction
                        dragStartElapsedMs = nowElapsed
                        if (activePointerCount <= 1 && !recentlyPinched && activeGestureLock != PlayerGestureLock.PINCH_ZOOM) {
                            preZoomBrightnessSnapshot = screenBrightness
                            preGestureVolumeSnapshot = gestureVolumeFraction
                        }
                        if (activePointerCount > 1 || recentlyPinched || activeGestureLock == PlayerGestureLock.PINCH_ZOOM) {
                            startZone = VideoGestureZone.OUTSIDE_VIDEO_AREA
                            lockedGesture = PlayerGestureLock.NONE
                        } else if (isHold2xSpeedActive || activeGestureLock == PlayerGestureLock.HOLD_FAST_FORWARD) {
                            lockedGesture = PlayerGestureLock.HOLD_FAST_FORWARD
                            activeGestureLock = PlayerGestureLock.HOLD_FAST_FORWARD
                            startZone = VideoGestureZone.OUTSIDE_VIDEO_AREA
                        } else if (uiState.isControlsLocked) {
                            lockedButtonVisible = true
                            startZone = VideoGestureZone.OUTSIDE_VIDEO_AREA
                            lockedGesture = PlayerGestureLock.NONE
                        } else {
                            lockedGesture = PlayerGestureLock.NONE
                            startZone = PlayerGestureControlHelper.resolveGestureZone(
                                touchX = offset.x,
                                touchY = offset.y,
                                containerWidth = size.width.toFloat(),
                                containerHeight = size.height.toFloat()
                            )
                        }
                    },
                    onDragEnd = { finishSingleFingerDrag() },
                    onDragCancel = { finishSingleFingerDrag() },
                    onDrag = { change, dragAmount ->
                        val nowElapsed = SystemClock.elapsedRealtime()
                        val recentlyPinched = nowElapsed - lastMultiTouchEndedElapsedMs < 450L
                        if (activePointerCount > 1 || activeGestureLock == PlayerGestureLock.PINCH_ZOOM || recentlyPinched) {
                            if (lockedGesture == PlayerGestureLock.VERTICAL_BRIGHTNESS ||
                                kotlin.math.abs(screenBrightness - dragStartBrightnessSnapshot) > 0.0005f
                            ) {
                                screenBrightness = PlayerGestureControlHelper.resolveBrightnessDuringZoomOrMultiTouch(
                                    currentBrightness = screenBrightness,
                                    preZoomBrightnessSnapshot = dragStartBrightnessSnapshot,
                                    activePointerCount = maxOf(activePointerCount, 2),
                                    isPinchZoomActive = true,
                                    recentlyPinched = true
                                )
                                PlayerGestureControlHelper.applyWindowBrightness(
                                    activity = activity,
                                    brightnessFraction = screenBrightness,
                                    batterySaverEnabled = uiState.batterySaverModeEnabled
                                )
                            }
                            if (lockedGesture == PlayerGestureLock.VERTICAL_VOLUME ||
                                kotlin.math.abs(gestureVolumeFraction - dragStartVolumeSnapshot) > 0.0005f
                            ) {
                                gestureVolumeFraction = PlayerGestureControlHelper.resolveVolumeDuringNonVolumeGesture(
                                    currentVolume = gestureVolumeFraction,
                                    preGestureVolumeSnapshot = dragStartVolumeSnapshot,
                                    activePointerCount = maxOf(activePointerCount, 2),
                                    isPinchZoomActive = true
                                )
                                exoPlayer.volume = if (uiState.isAudioMuted) 0f else gestureVolumeFraction
                            }
                            if (activeHudType == HudType.BRIGHTNESS || activeHudType == HudType.VOLUME) {
                                activeHudType = null
                            }
                            lockedGesture = PlayerGestureLock.NONE
                            startZone = VideoGestureZone.OUTSIDE_VIDEO_AREA
                            isScreenTouchSlideSkipping = false
                            return@detectDragGestures
                        }

                        if (isHold2xSpeedActive || lockedGesture == PlayerGestureLock.HOLD_FAST_FORWARD || activeGestureLock == PlayerGestureLock.HOLD_FAST_FORWARD) {
                            // Hold + slide horizontally controls x1..x5 speed ONLY (never triggers seek skipping, volume, or brightness)
                            lockedGesture = PlayerGestureLock.HOLD_FAST_FORWARD
                            activeGestureLock = PlayerGestureLock.HOLD_FAST_FORWARD
                            registerUserInteraction()
                            change.consume()
                            isScreenTouchSlideSkipping = false
                            screenBrightness = dragStartBrightnessSnapshot
                            gestureVolumeFraction = dragStartVolumeSnapshot
                            totalDx += dragAmount.x
                            val stepWidthPx = (size.width / 5f).coerceAtLeast(48f)
                            val stepDelta = (totalDx / stepWidthPx).roundToInt()
                            val targetSpeed = (startHoldSpeed + stepDelta).coerceIn(1.0f, 5.0f).roundToInt().toFloat()
                            if (targetSpeed != holdSpeedMultiplier) {
                                holdSpeedMultiplier = targetSpeed
                                viewModel.setPlaybackSpeed(targetSpeed)
                            }
                            return@detectDragGestures
                        }

                        if (uiState.isControlsLocked || startZone == VideoGestureZone.OUTSIDE_VIDEO_AREA) {
                            return@detectDragGestures
                        }

                        totalDx += dragAmount.x
                        totalDy += dragAmount.y

                        // Lock onto a single gesture direction once touch slop / minimum swipe distance is passed
                        if (lockedGesture == PlayerGestureLock.NONE) {
                            val recentlyDoubleTapped = nowElapsed - lastDoubleTapSeekElapsedMs < 260L
                            if (PlayerGestureControlHelper.isVerticalSwipeGesture(totalDx, totalDy, minSwipeDistancePx)) {
                                // Wait 85ms before locking onto brightness so a 2-finger pinch never alters brightness
                                if (startZone == VideoGestureZone.LEFT_BRIGHTNESS && nowElapsed - dragStartElapsedMs < 85L) {
                                    return@detectDragGestures
                                }
                                val canHandleVertical = PlayerGestureControlHelper.canHandlePlaybackVerticalGesture(
                                    isPlaying = isPlaying,
                                    isControlsLocked = uiState.isControlsLocked,
                                    isInPipMode = uiState.isInPipMode,
                                    activePointerCount = activePointerCount,
                                    isHoldFastForwardActive = isHold2xSpeedActive,
                                    isHorizontalSeekingActive = isScreenTouchSlideSkipping,
                                    isPinchZoomActive = activeGestureLock == PlayerGestureLock.PINCH_ZOOM,
                                    recentlyPinched = recentlyPinched,
                                    isDoubleTapSkipActive = recentlyDoubleTapped
                                )
                                if (!canHandleVertical) {
                                    return@detectDragGestures
                                }
                                lockedGesture = if (startZone == VideoGestureZone.LEFT_BRIGHTNESS) {
                                    PlayerGestureLock.VERTICAL_BRIGHTNESS
                                } else {
                                    PlayerGestureLock.VERTICAL_VOLUME
                                }
                                activeGestureLock = lockedGesture
                            } else if (PlayerGestureControlHelper.isHorizontalSeekGesture(totalDx, totalDy, minSwipeDistancePx)) {
                                val canHandleHorizontal = PlayerGestureControlHelper.canHandlePlaybackHorizontalSeek(
                                    isControlsLocked = uiState.isControlsLocked,
                                    isInPipMode = uiState.isInPipMode,
                                    activePointerCount = activePointerCount,
                                    isHoldFastForwardActive = isHold2xSpeedActive,
                                    isVerticalBrightnessActive = activeGestureLock == PlayerGestureLock.VERTICAL_BRIGHTNESS,
                                    isVerticalVolumeActive = activeGestureLock == PlayerGestureLock.VERTICAL_VOLUME,
                                    isVerticalGestureActive = activeGestureLock == PlayerGestureLock.VERTICAL_BRIGHTNESS ||
                                        activeGestureLock == PlayerGestureLock.VERTICAL_VOLUME,
                                    isPinchZoomActive = activeGestureLock == PlayerGestureLock.PINCH_ZOOM,
                                    recentlyPinched = recentlyPinched,
                                    isDoubleTapSkipActive = recentlyDoubleTapped
                                )
                                if (!canHandleHorizontal) {
                                    return@detectDragGestures
                                }
                                lockedGesture = PlayerGestureLock.HORIZONTAL_SKIP
                                activeGestureLock = PlayerGestureLock.HORIZONTAL_SKIP
                            } else {
                                return@detectDragGestures
                            }
                        }

                        registerUserInteraction()
                        change.consume()
                        lastHudUpdateTimestampMs = nowElapsed

                        when (lockedGesture) {
                            PlayerGestureLock.VERTICAL_BRIGHTNESS -> {
                                // Keep volume strictly untouched during brightness adjustment
                                gestureVolumeFraction = dragStartVolumeSnapshot
                                val rawBrightness = PlayerGestureControlHelper.computeUpdatedBrightness(
                                    currentBrightness = screenBrightness,
                                    dragDeltaY = dragAmount.y,
                                    containerHeightPx = size.height.toFloat()
                                )
                                val safetyResult = PlayerGestureControlHelper.enforceAutoSafeVolumeAndBrightness(
                                    volumeFraction = gestureVolumeFraction,
                                    brightnessFraction = rawBrightness,
                                    isMuted = uiState.isAudioMuted
                                )
                                screenBrightness = safetyResult.brightnessFraction
                                if (safetyResult.autoLoweredToSafe50) {
                                    gestureVolumeFraction = safetyResult.volumeFraction
                                    dragStartVolumeSnapshot = safetyResult.volumeFraction
                                    dragStartBrightnessSnapshot = safetyResult.brightnessFraction
                                    highVolumeBrightnessAlertDismissedForSession = false
                                    exoPlayer.volume = if (uiState.isAudioMuted) 0f else gestureVolumeFraction
                                    PlayerGestureControlHelper.syncVolumeWithAudioManager(
                                        context = context,
                                        volumeFraction = gestureVolumeFraction,
                                        isMuted = uiState.isAudioMuted
                                    )
                                    viewModel.updateSavedPlayerBrightness(rawBrightness, activeFolderKey, activeVideo.path)
                                }
                                PlayerGestureControlHelper.applyWindowBrightness(
                                    activity = activity,
                                    brightnessFraction = screenBrightness,
                                    batterySaverEnabled = uiState.batterySaverModeEnabled
                                )
                                if (!safetyResult.autoLoweredToSafe50 &&
                                    nowElapsed - lastPersistElapsedMs >= PlayerGestureControlHelper.PREFERENCE_SAVE_DEBOUNCE_MS
                                ) {
                                    lastPersistElapsedMs = nowElapsed
                                    viewModel.updateSavedPlayerBrightness(screenBrightness, activeFolderKey, activeVideo.path)
                                }
                                activeHudType = HudType.BRIGHTNESS
                                hudValueFraction = screenBrightness
                                val pct = PlayerGestureControlHelper.formatBrightnessPercentage(screenBrightness)
                                hudLabelText = "Brightness $pct%"
                            }
                            PlayerGestureLock.VERTICAL_VOLUME -> {
                                // Keep brightness strictly untouched during volume adjustment
                                screenBrightness = dragStartBrightnessSnapshot
                                val rawVolume = PlayerGestureControlHelper.computeUpdatedVolume(
                                    currentVolume = gestureVolumeFraction,
                                    dragDeltaY = dragAmount.y,
                                    containerHeightPx = size.height.toFloat()
                                )
                                if (uiState.isAudioMuted && dragAmount.y < 0f && rawVolume > 0.01f) {
                                    viewModel.setAudioMuted(false, activeVideo.path)
                                }
                                val safetyResult = PlayerGestureControlHelper.enforceAutoSafeVolumeAndBrightness(
                                    volumeFraction = rawVolume,
                                    brightnessFraction = screenBrightness,
                                    isMuted = uiState.isAudioMuted
                                )
                                gestureVolumeFraction = safetyResult.volumeFraction
                                if (safetyResult.autoLoweredToSafe50) {
                                    screenBrightness = safetyResult.brightnessFraction
                                    dragStartBrightnessSnapshot = safetyResult.brightnessFraction
                                    dragStartVolumeSnapshot = safetyResult.volumeFraction
                                    highVolumeBrightnessAlertDismissedForSession = false
                                    PlayerGestureControlHelper.applyWindowBrightness(
                                        activity = activity,
                                        brightnessFraction = screenBrightness,
                                        batterySaverEnabled = uiState.batterySaverModeEnabled
                                    )
                                    viewModel.updateSavedPlayerVolume(rawVolume, activeFolderKey, activeVideo.path)
                                }
                                exoPlayer.volume = if (uiState.isAudioMuted) 0f else gestureVolumeFraction
                                PlayerGestureControlHelper.syncVolumeWithAudioManager(
                                    context = context,
                                    volumeFraction = gestureVolumeFraction,
                                    isMuted = uiState.isAudioMuted
                                )
                                if (!safetyResult.autoLoweredToSafe50 &&
                                    nowElapsed - lastPersistElapsedMs >= PlayerGestureControlHelper.PREFERENCE_SAVE_DEBOUNCE_MS
                                ) {
                                    lastPersistElapsedMs = nowElapsed
                                    viewModel.updateSavedPlayerVolume(gestureVolumeFraction, activeFolderKey, activeVideo.path)
                                }
                                activeHudType = HudType.VOLUME
                                hudValueFraction = if (uiState.isAudioMuted) 0f else gestureVolumeFraction
                                val pct = PlayerGestureControlHelper.formatVolumePercentage(
                                    gestureVolumeFraction,
                                    uiState.isAudioMuted
                                )
                                val boostTag = if (uiState.volumeBoostMultiplier > 1.01f) {
                                    " (+${kotlin.math.round((uiState.volumeBoostMultiplier - 1f) * 100f).toInt()}%)"
                                } else {
                                    ""
                                }
                                hudLabelText = "Volume $pct%$boostTag"
                            }
                            PlayerGestureLock.HORIZONTAL_SKIP -> {
                                // Keep brightness and volume strictly untouched during horizontal seek skipping
                                screenBrightness = dragStartBrightnessSnapshot
                                gestureVolumeFraction = dragStartVolumeSnapshot
                                // Horizontal swipe skipping with 180ms keyframe seek throttle for low CPU, RAM & battery usage
                                isScreenTouchSlideSkipping = true
                                controlsVisible = false
                                val deltaMs = (dragAmount.x * 140f).toLong()
                                touchSlideAccumulatedMs += deltaMs
                                val target = (touchSlideStartPosMs + touchSlideAccumulatedMs).coerceIn(0L, totalDurationMs)
                                currentPositionMs = target
                                if (nowElapsed - lastSeekDispatchElapsedMs >= 180L) {
                                    lastSeekDispatchElapsedMs = nowElapsed
                                    exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                                    if (!isPlaying) {
                                        isPlaying = true
                                        exoPlayer.playWhenReady = true
                                    }
                                    exoPlayer.seekTo(target)
                                }
                                val diffSec = ((target - touchSlideStartPosMs) / 1000L).toInt()
                                val signPrefix = if (diffSec >= 0) "+${diffSec}s" else "${diffSec}s"
                                activeHudType = HudType.SEEK
                                hudLabelText = "$signPrefix • ${Formatters.formatDuration(target)} / ${Formatters.formatDuration(totalDurationMs)}"
                            }
                            else -> Unit
                        }
                    }
                )
            }
            .testTag("video_player_screen")
    ) {
        // Video Surface with surface_type="surface_view", Real-Time Smart Video Enhancement Engine & Low-CPU/Low-Battery GPU Pinch-to-Zoom
        AndroidView(
            factory = { ctx ->
                val inflatedView = runCatching {
                    android.view.LayoutInflater.from(ctx)
                        .inflate(com.cyosplay.player.R.layout.cyos_player_surface_view, null, false) as? PlayerView
                }.getOrNull() ?: PlayerView(ctx)
                inflatedView.apply {
                    useController = false
                    player = null
                    player = exoPlayer
                    lastBoundSurfaceVideoUri = activeVideo.uri
                    subtitleView?.visibility = if (hasSelectedEmbeddedSubtitle) View.VISIBLE else View.GONE
                    // Large 24sp text at 75% opacity (0xBFFFFFFF), no background box (Color.TRANSPARENT), drop shadow
                    subtitleView?.setFixedTextSize(
                        android.util.TypedValue.COMPLEX_UNIT_SP,
                        uiState.subtitleCustomSp.coerceAtLeast(24f)
                    )
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            0xBFFFFFFF.toInt(),
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            android.graphics.Color.BLACK,
                            android.graphics.Typeface.DEFAULT_BOLD
                        )
                    )
                    resizeMode = when (uiState.aspectRatioMode) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    }
                }
            },
            update = { playerView ->
                // Step 3: Handle surface switching when mixing software & hardware decoders
                if (playerView.player !== exoPlayer || lastBoundSurfaceVideoUri != activeVideo.uri) {
                    playerView.player = null
                    playerView.player = exoPlayer
                    lastBoundSurfaceVideoUri = activeVideo.uri
                }
                val showEmbeddedView = hasSelectedEmbeddedSubtitle && activeMultiSubtitleCues.isEmpty()
                val desiredVis = if (showEmbeddedView) View.VISIBLE else View.GONE
                if (playerView.subtitleView?.visibility != desiredVis) {
                    playerView.subtitleView?.visibility = desiredVis
                }
                playerView.subtitleView?.setFixedTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    uiState.subtitleCustomSp.coerceAtLeast(24f)
                )
                val targetResize = when (uiState.aspectRatioMode) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                }
                if (playerView.resizeMode != targetResize) {
                    playerView.resizeMode = targetResize
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    playerView.setRenderEffect(memoizedEnhancementRenderEffect)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val strideCropCompensation = if (
                        FfmpegMatroskaCodecRegistry.needsStrideAlignmentFix(activeVideo.width, activeVideo.height)
                    ) {
                        val ratio = FfmpegMatroskaCodecRegistry.computeStrideCropWidthRatio(activeVideo.width)
                        if (ratio in 0.90f..0.999f) (1f / ratio).coerceIn(1f, 1.04f) else 1f
                    } else {
                        1f
                    }
                    scaleX = liveZoomScale * strideCropCompensation
                    scaleY = liveZoomScale
                    translationX = if (liveZoomScale > 1.01f) livePanX else 0f
                    translationY = if (liveZoomScale > 1.01f) livePanY else 0f
                }
        )

        // Universal Smart Video Enhancement Visual Tone-Mapping Pass
        if (uiState.smartEnhancementMode != SmartEnhancementMode.OFF) {
            val toneOverlayColor = when (uiState.smartEnhancementMode) {
                SmartEnhancementMode.CINEMA_HDR -> ElectricCyan.copy(alpha = 0.03f)
                SmartEnhancementMode.ULTRA_8K_CLARITY, SmartEnhancementMode.SHARP_DETAIL -> Color.White.copy(alpha = 0.025f)
                SmartEnhancementMode.VIVID_AMOLED -> AmberGold.copy(alpha = 0.035f)
                SmartEnhancementMode.NIGHT_VISION -> Color.White.copy(alpha = 0.06f)
                SmartEnhancementMode.ECO_BATTERY -> Color.Black.copy(alpha = 0.14f)
                SmartEnhancementMode.OFF -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(toneOverlayColor)
            )
        }

        // Item 6: Night Shield Mode & Eye Comfort Overlay directly on in-player video surface
        VisualComfortScreenOverlay(
            eyeComfortEnabled = uiState.eyeComfortEnabled,
            nightModeEnabled = uiState.nightModeEnabled,
            warmthIntensity = uiState.nightShieldWarmthLevel.coerceAtLeast(uiState.eyeComfortWarmth),
            extraDimIntensity = uiState.extraDimIntensity
        )

        // Item 3: Live External Subtitle Overlay with responsive clamp(1.25rem, 2.5vw, 2rem) scaling,
        // high-contrast semi-transparent dark caption backdrop with drop shadow, clearance above the control bar,
        // and an on-screen subtitle size switcher (Small to Extra Large)
        val subtitleControlClearanceDp = if (controlsVisible && !uiState.isControlsLocked && !uiState.isInPipMode) {
            88
        } else {
            0
        }
        val responsiveSubtitleSp = uiState.subtitleFontSize.computeResponsiveSp(viewportWidthDp, customSpOverride = uiState.subtitleCustomSp)
        if (activeMultiSubtitleCues.isNotEmpty() || !currentSubtitleCue.isNullOrBlank() || !currentSecondarySubtitleCue.isNullOrBlank()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = (uiState.subtitleVerticalPreset.bottomPaddingDp + subtitleControlClearanceDp).dp,
                        start = 24.dp,
                        end = 24.dp
                    )
                    .testTag("player_subtitle_overlay_box")
            ) {
                if (controlsVisible && !uiState.isControlsLocked && !uiState.isInPipMode) {
                    Surface(
                        color = Color(0xCC0A0E17),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .testTag("onscreen_subtitle_size_switcher")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Manual Step Decrease Button
                            Text(
                                text = "[-]",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        registerUserInteraction()
                                        viewModel.decreaseSubtitleFontSizeManual()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("onscreen_sub_decrease_button")
                            )

                            // Manual Size Indicator
                            Text(
                                text = "${uiState.subtitleCustomSp.toInt()}sp",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberGold,
                                modifier = Modifier.testTag("onscreen_sub_custom_sp_text")
                            )

                            // Manual Step Increase Button
                            Text(
                                text = "[+]",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        registerUserInteraction()
                                        viewModel.increaseSubtitleFontSizeManual()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("onscreen_sub_increase_button")
                            )

                            SubtitleFontSize.entries.forEach { sizePreset ->
                                val isSelected = uiState.subtitleFontSize == sizePreset
                                Text(
                                    text = sizePreset.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) ElectricCyan else Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            registerUserInteraction()
                                            viewModel.setSubtitleFontSize(sizePreset)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .testTag("subtitle_size_option_${sizePreset.name.lowercase()}")
                                )
                            }

                            // Select All / Dual Subtitles Toggle Chip
                            val allOrDualActive = uiState.playAllSubtitlesSimultaneously || uiState.dualSubtitlesEnabled
                            Text(
                                text = if (uiState.playAllSubtitlesSimultaneously) "All Subs: ON" else if (uiState.dualSubtitlesEnabled) "Dual: ON" else "All Subs",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (allOrDualActive) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (allOrDualActive) AmberGold else Color.White.copy(alpha = 0.72f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        registerUserInteraction()
                                        if (uiState.playAllSubtitlesSimultaneously) {
                                            viewModel.clearAllSubtitles()
                                        } else {
                                            viewModel.selectAllSubtitles()
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("subtitle_dual_toggle_chip")
                            )
                        }
                    }
                }

                // Stacked Multi-Subtitle Cues Display — Large Extra-Bold 24sp+ text at 75% opacity, no background box
                if (activeMultiSubtitleCues.isNotEmpty()) {
                    activeMultiSubtitleCues.forEach { item ->
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("multi_subtitle_cue_${item.trackId}")
                        ) {
                            Text(
                                text = item.cueText,
                                style = TextStyle(
                                    fontSize = responsiveSubtitleSp.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(item.colorHex).copy(alpha = 0.75f),
                                    shadow = Shadow(
                                        color = Color.Black,
                                        offset = Offset(2f, 2f),
                                        blurRadius = 6f
                                    ),
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else {
                    if (!currentSecondarySubtitleCue.isNullOrBlank()) {
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("secondary_subtitle_cue_surface")
                        ) {
                            Text(
                                text = currentSecondarySubtitleCue,
                                style = TextStyle(
                                    fontSize = (responsiveSubtitleSp * 0.9f).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AmberGold.copy(alpha = 0.75f),
                                    shadow = Shadow(
                                        color = Color.Black,
                                        offset = Offset(2f, 2f),
                                        blurRadius = 6f
                                    ),
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }
                    if (!currentSubtitleCue.isNullOrBlank()) {
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = currentSubtitleCue,
                                style = TextStyle(
                                    fontSize = responsiveSubtitleSp.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(uiState.subtitleColor.hexColor).copy(alpha = 0.75f),
                                    shadow = Shadow(
                                        color = Color.Black,
                                        offset = Offset(2f, 2f),
                                        blurRadius = 6f
                                    ),
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Item 4: Corrupted / Unreadable Video Error-Handling Fallback Overlay
        if (mediaErrorType != PlayerMediaErrorType.NONE && !uiState.isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9070B12))
                    .padding(24.dp)
                    .testTag("corrupted_video_fallback_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xF2101726),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = CrimsonAlert.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(20.dp)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Corrupted or unreadable video warning",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (mediaErrorType == PlayerMediaErrorType.NETWORK_DISCONNECT) {
                                "Network connection lost while loading video stream."
                            } else {
                                FfmpegMatroskaCodecRegistry.CORRUPTED_VIDEO_FALLBACK_MESSAGE
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("corrupted_video_error_message")
                        )
                        Button(
                            onClick = {
                                registerUserInteraction()
                                mediaErrorType = PlayerMediaErrorType.NONE
                                isMediaLoading = true
                                viewModel.setDecoderEngineMode(DecoderEngineMode.HW_PLUS)
                                exoPlayer.setMediaItem(MediaItem.fromUri(activeVideo.uri))
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                                isPlaying = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color(0xFF071018)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("corrupted_video_retry_button")
                        ) {
                            Text(
                                text = "Retry",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        val isActivelySkipping = isScreenTouchSlideSkipping ||
            isHold2xSpeedActive ||
            activeGestureLock == PlayerGestureLock.PINCH_ZOOM ||
            activeHudType == HudType.SEEK

        // Item 2: Short & direct UI/UX warning alert when both high volume and screen brightness (>= 80%) are detected
        AnimatedVisibility(
            visible = showHighVolumeAndBrightnessAlert,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (controlsVisible && !uiState.isControlsLocked) 76.dp else 20.dp, start = 20.dp, end = 20.dp)
                .testTag("high_volume_brightness_warning_alert")
        ) {
            Surface(
                color = Color(0xE6141C2B),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .border(1.dp, AmberGold.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "High volume and screen brightness warning",
                        tint = AmberGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = PlayerGestureControlHelper.HIGH_VOLUME_AND_BRIGHTNESS_WARNING_TEXT,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.testTag("high_volume_brightness_warning_text")
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val volPct = PlayerGestureControlHelper.formatVolumePercentage(gestureVolumeFraction, uiState.isAudioMuted)
                            val briPct = PlayerGestureControlHelper.formatBrightnessPercentage(screenBrightness)
                            Text(
                                text = "Vol $volPct% • Brightness $briPct%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = RobotoNumbersFontFamily
                                ),
                                fontWeight = FontWeight.Bold,
                                color = AmberGold,
                                modifier = Modifier.testTag("high_vol_brightness_live_metrics_badge")
                            )
                        }
                    }
                    Button(
                        onClick = {
                            registerUserInteraction()
                            val safeLevel = PlayerGestureControlHelper.SAFE_LOWERED_VOLUME_BRIGHTNESS
                            screenBrightness = safeLevel
                            gestureVolumeFraction = safeLevel
                            PlayerGestureControlHelper.applyWindowBrightness(
                                activity = activity,
                                brightnessFraction = safeLevel,
                                batterySaverEnabled = uiState.batterySaverModeEnabled
                            )
                            exoPlayer.volume = if (uiState.isAudioMuted) 0f else safeLevel
                            PlayerGestureControlHelper.syncVolumeWithAudioManager(
                                context = context,
                                volumeFraction = safeLevel,
                                isMuted = uiState.isAudioMuted
                            )
                            highVolumeBrightnessAlertDismissedForSession = true
                            viewModel.lowerBothVolumeAndBrightness(activeFolderKey, activeVideo.path)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberGold,
                            contentColor = Color(0xFF0A0E17)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("high_vol_brightness_lower_both_button")
                    ) {
                        Text(
                            text = "Lower Both",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = {
                            registerUserInteraction()
                            highVolumeBrightnessAlertDismissedForSession = true
                            viewModel.dismissHighVolumeBrightnessWarning()
                        },
                        modifier = Modifier.testTag("high_vol_brightness_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Warning",
                            tint = Color.White.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }

        // Hold Speed indicator only when unlocked (no icon while skipping)
        AnimatedVisibility(
            visible = isHold2xSpeedActive && !uiState.isControlsLocked && !uiState.isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            Surface(
                color = Color(0x990A0E17),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(1.dp, AmberGold.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .testTag("player_hold_2x_speed_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val activeHoldInt = holdSpeedMultiplier.roundToInt().coerceIn(1, 5)
                    Text(
                        text = "${activeHoldInt}.0x Speed",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = RobotoNumbersFontFamily
                        ),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { step ->
                            Text(
                                text = "x$step",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = RobotoNumbersFontFamily
                                ),
                                fontWeight = if (step == activeHoldInt) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (step == activeHoldInt) AmberGold else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        registerUserInteraction()
                                        holdSpeedMultiplier = step.toFloat()
                                        viewModel.setPlaybackSpeed(step.toFloat())
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("hold_speed_step_x$step")
                            )
                        }
                    }
                }
            }
        }

        // Left-Side Temporary Brightness Overlay (visible ONLY while adjusting brightness; auto-hides after ~1000ms)
        AnimatedVisibility(
            visible = activeHudType == HudType.BRIGHTNESS && !uiState.isControlsLocked && !uiState.isInPipMode,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 280)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            Surface(
                color = Color(0xCC0A0E17),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .border(1.dp, AmberGold.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .testTag("player_brightness_side_overlay")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrightnessMedium,
                        contentDescription = "Brightness Indicator",
                        tint = AmberGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "${PlayerGestureControlHelper.formatBrightnessPercentage(screenBrightness)}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = RobotoNumbersFontFamily
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(124.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .testTag("player_hud_progress_bar"),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxSize(fraction = screenBrightness.coerceIn(0.02f, 1.0f))
                                .background(AmberGold, RoundedCornerShape(5.dp))
                                .testTag("player_brightness_progress_bar")
                        )
                    }
                }
            }
        }

        // Right-Side Temporary Volume Overlay (visible ONLY while adjusting volume; auto-hides after ~1000ms)
        AnimatedVisibility(
            visible = activeHudType == HudType.VOLUME && !uiState.isControlsLocked && !uiState.isInPipMode,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 280)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            Surface(
                color = Color(0xCC0A0E17),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .border(1.dp, ElectricCyan.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .testTag("player_volume_side_overlay")
            ) {
                val effectiveVol = if (uiState.isAudioMuted) 0f else gestureVolumeFraction.coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume Indicator",
                        tint = if (effectiveVol <= 0.005f) Color.White.copy(alpha = 0.5f) else ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "${PlayerGestureControlHelper.formatVolumePercentage(gestureVolumeFraction, uiState.isAudioMuted)}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = RobotoNumbersFontFamily
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(124.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .testTag("player_hud_progress_bar"),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxSize(fraction = effectiveVol)
                                .background(ElectricCyan, RoundedCornerShape(5.dp))
                                .testTag("player_volume_progress_bar")
                        )
                    }
                }
            }
        }

        // Center Gesture HUD Pill (Seek / Status) — visible on double-tap skip even when controls are locked
        AnimatedVisibility(
            visible = activeHudType == HudType.SEEK && !uiState.isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color(0xB80A0E17),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .testTag("player_center_hud_pill")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = hudLabelText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = RobotoNumbersFontFamily
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // AUTO-RESUME PLAYBACK POSITION + START OVER FLOATING CHIP
        AnimatedVisibility(
            visible = showResumeStartOverBanner && !uiState.isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (controlsVisible && !uiState.isControlsLocked) 104.dp else 20.dp)
        ) {
            Surface(
                color = Color(0xBF0A0E17),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .testTag("resume_start_over_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Resumed from ${Formatters.formatDuration(resumedFromTimestampMs)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = AmberGold.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                showResumeStartOverBanner = false
                                currentPositionMs = 0L
                                scrubPositionMs = 0L
                                exoPlayer.seekTo(0L)
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                                viewModel.resetPlaybackProgressAndStartOver(activeVideo.path)
                                activeHudType = HudType.SEEK
                                hudLabelText = "Started Over • 00:00"
                            }
                            .testTag("start_over_button")
                    ) {
                        Text(
                            text = "Start Over",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Item 7: Auto-Detect and Skip Intros & Credits floating button and auto-skip logic
        val introCreditInfo = PlayerGestureControlHelper.resolveIntroCreditWindow(
            totalDurationMs = totalDurationMs,
            currentPositionMs = currentPositionMs
        )
        val isIntroWindow = introCreditInfo.isInIntroWindow && !hasAutoSkippedIntroForCurrentVideo
        val isCreditsWindow = introCreditInfo.isInCreditsWindow && !hasAutoSkippedCreditsForCurrentVideo

        LaunchedEffect(currentPositionMs, totalDurationMs, uiState.autoSkipIntrosCredits, hasAutoSkippedIntroForCurrentVideo, hasAutoSkippedCreditsForCurrentVideo) {
            if (uiState.autoSkipIntrosCredits && !hasAutoSkippedIntroForCurrentVideo && isIntroWindow && currentPositionMs >= 2_000L) {
                hasAutoSkippedIntroForCurrentVideo = true
                val targetIntroEnd = introCreditInfo.introSkipTargetMs
                exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                exoPlayer.seekTo(targetIntroEnd)
                currentPositionMs = targetIntroEnd
                lastHudUpdateTimestampMs = SystemClock.elapsedRealtime()
                activeHudType = HudType.SEEK
                hudLabelText = "Intro Auto-Skipped • ${Formatters.formatDuration(targetIntroEnd)}"
            } else if (uiState.autoSkipIntrosCredits && !hasAutoSkippedCreditsForCurrentVideo && isCreditsWindow) {
                hasAutoSkippedCreditsForCurrentVideo = true
                val targetCreditEnd = introCreditInfo.creditsSkipTargetMs
                exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                exoPlayer.seekTo(targetCreditEnd)
                currentPositionMs = targetCreditEnd
                lastHudUpdateTimestampMs = SystemClock.elapsedRealtime()
                activeHudType = HudType.SEEK
                hudLabelText = "Credits Auto-Skipped"
            }
        }

        AnimatedVisibility(
            visible = (isIntroWindow || isCreditsWindow) && !uiState.isControlsLocked && !uiState.isInPipMode && !isActivelySkipping,
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (controlsVisible) 110.dp else 40.dp, end = 24.dp)
        ) {
            Surface(
                color = Color(0xCC0A0E17),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(1.5.dp, if (isIntroWindow) AmberGold else EmeraldVault, RoundedCornerShape(50))
                    .clickable {
                        registerUserInteraction()
                        if (isIntroWindow) {
                            val targetIntroEnd = introCreditInfo.introSkipTargetMs
                            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                            exoPlayer.seekTo(targetIntroEnd)
                            currentPositionMs = targetIntroEnd
                            hasAutoSkippedIntroForCurrentVideo = true
                            lastHudUpdateTimestampMs = SystemClock.elapsedRealtime()
                            activeHudType = HudType.SEEK
                            hudLabelText = "Skipped Intro • ${Formatters.formatDuration(targetIntroEnd)}"
                        } else {
                            val targetCreditEnd = introCreditInfo.creditsSkipTargetMs
                            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                            exoPlayer.seekTo(targetCreditEnd)
                            currentPositionMs = targetCreditEnd
                            hasAutoSkippedCreditsForCurrentVideo = true
                            lastHudUpdateTimestampMs = SystemClock.elapsedRealtime()
                            activeHudType = HudType.SEEK
                            hudLabelText = "Skipped Credits"
                        }
                    }
                    .testTag("floating_skip_intro_credits_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = if (isIntroWindow) AmberGold else EmeraldVault,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isIntroWindow) "Skip Intro" else "Skip Credits",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // =========================================================================
        // IMPROVED VIDEO PLAY LOCK + 1x..5x SPEED SKIP (Item 5):
        // When controls are locked, holding & sliding horizontally adjusts speed x1..x5,
        // and the speed popup option stays hidden until the user taps the Speed icon on the lock overlay.
        // =========================================================================
        AnimatedVisibility(
            visible = uiState.isControlsLocked && lockedButtonVisible && !isActivelySkipping && !uiState.isInPipMode,
            enter = fadeIn(animationSpec = tween(durationMillis = 250)),
            exit = fadeOut(animationSpec = tween(durationMillis = 350)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color(0x99101622),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, ElectricCyan, CircleShape)
                        .clickable {
                            registerUserInteraction()
                            showSpeedSkipPopupDialog = true
                        }
                        .testTag("locked_speed_popup_trigger_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Open Skip Speed x1-x5 Popup",
                            tint = if (uiState.playbackSpeed > 1.01f) AmberGold else ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Surface(
                    color = Color(0x99101622),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, AmberGold, CircleShape)
                        .clickable {
                            registerUserInteraction()
                            viewModel.toggleControlsLocked()
                            controlsVisible = true
                        }
                        .testTag("player_unlock_controls_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlock video controls",
                            tint = AmberGold,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Top & Bottom Player Controls Overlay — Item 5: Hide all icons while video skipping
        AnimatedVisibility(
            visible = controlsVisible && !isActivelySkipping && !uiState.isControlsLocked && !uiState.isInPipMode,
            enter = fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = fadeOut(animationSpec = tween(durationMillis = 450)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // =========================================================================
                // SEMI-TRANSPARENT TOP BAR WITH ICON-ONLY ACTION BUTTONS (Item 21)
                // =========================================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.56f),
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("player_top_bar")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val finalPos = exoPlayer.currentPosition.coerceAtLeast(currentPositionMs)
                                val finalDur = exoPlayer.duration.takeIf { it > 0L } ?: totalDurationMs
                                // Item 4: Do NOT auto-turn ON Mini-Player on Back button; save progress and exit cleanly
                                viewModel.savePlaybackProgressAndExit(
                                    videoPath = activeVideo.path,
                                    positionMs = finalPos,
                                    durationMs = finalDur
                                )
                            },
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        ) {
                            if (uiState.hideAllTextsEnabled && !isPlayerTitleTextRevealed) {
                                PlayerIconOnlyButton(
                                    icon = Icons.Default.Visibility,
                                    contentDescription = "Reveal video title",
                                    tint = ElectricCyan,
                                    testTag = "player_reveal_title_text_pill",
                                    onClick = { isPlayerTitleTextRevealed = true }
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .then(
                                            if (uiState.hideAllTextsEnabled) {
                                                Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0x990A0E17))
                                                    .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                                    .clickable { isPlayerTitleTextRevealed = false }
                                                    .padding(8.dp)
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    Text(
                                        text = activeVideo.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            shadow = Shadow(
                                                color = Color.Black,
                                                offset = Offset(1.5f, 1.5f),
                                                blurRadius = 4f
                                            )
                                        ),
                                        maxLines = if (uiState.hideAllTextsEnabled) Int.MAX_VALUE else 1,
                                        overflow = if (uiState.hideAllTextsEnabled) TextOverflow.Visible else TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("player_video_title_text")
                                    )
                                    Text(
                                        text = "${activeVideo.resolutionBadge} • ${activeVideo.videoCodecHint} • ${activeVideo.audioCodecHint}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("player_video_subtitle_badge")
                                    )
                                }
                            }
                        }
                        // Top Bar primary action icons: Zoom, Skip, PiP, Rotate, Minimize, Lock (Item 3)
                        IconButton(
                            onClick = {
                                registerUserInteraction()
                                showVideoZoomPopupDialog = true
                            },
                            modifier = Modifier.testTag("player_top_zoom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Video Zoom",
                                tint = if (uiState.videoZoomScale != 1.0f) AmberGold else ElectricCyan
                            )
                        }
                        IconButton(
                            onClick = {
                                registerUserInteraction()
                                showSpeedSkipPopupDialog = true
                            },
                            modifier = Modifier.testTag("player_top_skip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Skip Speed (x1-x5)",
                                tint = if (uiState.playbackSpeed > 1.01f) AmberGold else ElectricCyan
                            )
                        }
                        IconButton(
                            onClick = {
                                registerUserInteraction()
                                if (!uiState.stealthPlaybackEnabled) {
                                    viewModel.savePlaybackProgress(
                                        activeVideo.path,
                                        exoPlayer.currentPosition.coerceAtLeast(currentPositionMs),
                                        totalDurationMs
                                    )
                                }
                                val entered = (activity as? com.cyosplay.player.MainActivity)?.enterVideoPipMode(
                                    activeVideo.width,
                                    activeVideo.height
                                ) ?: false
                                if (!entered) {
                                    viewModel.setInPipMode(true)
                                }
                            },
                            modifier = Modifier.testTag("player_pip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture-in-Picture (PiP)",
                                tint = ElectricCyan
                            )
                        }
                        IconButton(
                            onClick = {
                                registerUserInteraction()
                                val nextManualOrientation = ScreenOrientationSensorHelper.computeNextManualRotateOrientation(
                                    activeAppliedOrientation
                                )
                                manualOrientationOverride = nextManualOrientation
                                portraitLockedAtSensorDegrees = if (nextManualOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                    liveSensorDegrees
                                } else {
                                    OrientationEventListener.ORIENTATION_UNKNOWN
                                }
                                activity?.requestedOrientation = nextManualOrientation
                                activeAppliedOrientation = nextManualOrientation
                                viewModel.cyclePlayerOrientationMode()
                                activeHudType = HudType.SEEK
                                hudLabelText = if (nextManualOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                    "Manual Rotate: Portrait (Tilt for Landscape)"
                                } else {
                                    "Manual Rotate: Landscape"
                                }
                            },
                            modifier = Modifier.testTag("player_orientation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Rotate Screen Manually (Landscape / Portrait)",
                                tint = if (manualOrientationOverride != null) AmberGold else ElectricCyan
                            )
                        }
                        IconButton(
                            onClick = {
                                val finalPos = exoPlayer.currentPosition.coerceAtLeast(currentPositionMs)
                                val finalDur = exoPlayer.duration.takeIf { it > 0L } ?: totalDurationMs
                                viewModel.minimizeToMiniPlayer(
                                    video = activeVideo,
                                    queue = playlistQueue,
                                    positionMs = finalPos,
                                    durationMs = finalDur,
                                    isPlaying = isPlaying
                                )
                            },
                            modifier = Modifier.testTag("player_minimize_miniplayer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Minimize,
                                contentDescription = "Minimize to Mini-Player",
                                tint = EmeraldVault
                            )
                        }
                        IconButton(
                            onClick = {
                                lockedButtonVisible = true
                                viewModel.toggleControlsLocked()
                            },
                            modifier = Modifier.testTag("player_lock_controls_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Lock controls",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    // All player top controls as semi-transparent icon buttons (PiP shown exclusively in top-right header bar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("player_top_chips_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerIconOnlyButton(
                            icon = Icons.Default.Minimize,
                            contentDescription = "Minimize to Mini-Player",
                            tint = EmeraldVault,
                            testTag = "player_minimize_miniplayer_chip",
                            onClick = {
                                val finalPos = exoPlayer.currentPosition.coerceAtLeast(currentPositionMs)
                                val finalDur = exoPlayer.duration.takeIf { it > 0L } ?: totalDurationMs
                                viewModel.minimizeToMiniPlayer(
                                    video = activeVideo,
                                    queue = playlistQueue,
                                    positionMs = finalPos,
                                    durationMs = finalDur,
                                    isPlaying = isPlaying
                                )
                            }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.Info,
                            contentDescription = "Properties",
                            tint = AmberGold,
                            testTag = "player_video_properties_chip",
                            onClick = { viewModel.inspectVideoProperties(activeVideo) }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.Memory,
                            contentDescription = "Toggle HW+/SW+ Decoder (${uiState.decoderEngineMode.badge})",
                            tint = ElectricCyan,
                            testTag = "player_decoder_toggle_chip",
                            onClick = { viewModel.toggleDecoderEngineMode() }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.Bedtime,
                            contentDescription = "Toggle Unified Night Mode",
                            tint = if (uiState.nightModeEnabled) AmberGold else Color.White,
                            testTag = "player_night_mode_chip",
                            onClick = { viewModel.toggleNightMode() }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.AutoAwesome,
                            contentDescription = "Smart Video Enhancement",
                            tint = if (uiState.smartEnhancementMode != SmartEnhancementMode.OFF) AmberGold else Color.White,
                            testTag = "player_smart_enhance_chip",
                            onClick = { showSmartEnhanceDialog = true }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.GraphicEq,
                            contentDescription = "Audio Tracks & Surround Decoder",
                            tint = EmeraldVault,
                            testTag = "player_audio_tracks_chip",
                            onClick = { showAudioDialog = true }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.Subtitles,
                            contentDescription = "Subtitles",
                            tint = if (hasSelectedEmbeddedSubtitle || uiState.externalSubtitleCues.isNotEmpty()) {
                                ElectricCyan
                            } else {
                                Color.White
                            },
                            testTag = "player_subtitle_tracks_chip",
                            onClick = { showSubtitleDialog = true }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.BatteryChargingFull,
                            contentDescription = "Battery Saver Mode",
                            tint = if (uiState.batterySaverModeEnabled) AmberGold else Color.White.copy(alpha = 0.85f),
                            testTag = "player_battery_saver_chip",
                            onClick = { viewModel.setBatterySaverModeEnabled(!uiState.batterySaverModeEnabled) }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.VisibilityOff,
                            contentDescription = "Stealth Mode",
                            tint = if (uiState.stealthPlaybackEnabled) EmeraldVault else Color.White.copy(alpha = 0.75f),
                            testTag = "player_stealth_chip",
                            onClick = { viewModel.setStealthPlaybackEnabled(!uiState.stealthPlaybackEnabled) }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.Speed,
                            contentDescription = "Playback Speed (${uiState.playbackSpeed}x)",
                            tint = if (uiState.playbackSpeed != 1.0f) AmberGold else Color.White,
                            testTag = "player_speed_chip",
                            onClick = {
                                registerUserInteraction()
                                showSpeedSkipPopupDialog = true
                            }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.PhotoCamera,
                            contentDescription = "Screenshot",
                            tint = ElectricCyan,
                            testTag = "player_screenshot_chip",
                            onClick = {
                                val res = viewModel.captureVideoScreenshot(activeVideo, currentPositionMs)
                                activeHudType = HudType.SEEK
                                hudLabelText = if (res.success) {
                                    "Saved Frame • Pictures/CyosPlay_Screenshots"
                                } else {
                                    "Screenshot Capture Failed"
                                }
                            }
                        )
                        PlayerIconOnlyButton(
                            icon = Icons.Default.BookmarkAdd,
                            contentDescription = "Add Bookmark",
                            tint = if (uiState.activeVideoBookmarks.isNotEmpty()) AmberGold else Color.White,
                            testTag = "player_bookmark_chip",
                            onClick = { viewModel.addBookmarkAt(activeVideo.path, currentPositionMs) }
                        )
                    }
                }

                // =========================================================================
                // SEMI-TRANSPARENT BOTTOM BAR WITH THIN PROGRESS BAR (Items 20 & 21)
                // =========================================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.34f),
                                    Color.Black.copy(alpha = 0.62f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("player_bottom_bar")
                ) {
                    val displayedPositionMs = if (isUserScrubbing) scrubPositionMs else currentPositionMs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_time_labels_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${Formatters.formatDuration(displayedPositionMs)} / ${Formatters.formatDuration(totalDurationMs)}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = RobotoNumbersFontFamily,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(
                                    color = Color.Black,
                                    offset = Offset(1f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            modifier = Modifier.testTag("player_elapsed_total_time_text")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.abRepeatStartMs != null) {
                                Surface(
                                    color = ElectricCyan.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (uiState.abRepeatEndMs != null) "A-B" else "A",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = Formatters.formatRemainingDuration(displayedPositionMs, totalDurationMs),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = RobotoNumbersFontFamily,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.testTag("player_remaining_time_text")
                            )
                        }
                    }

                    // Slider updates only the displayed timestamp while dragging, and seeks ONLY on release (CLOSEST_SYNC)
                    val progressFraction = (displayedPositionMs.toFloat() / totalDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    Slider(
                        value = progressFraction,
                        onValueChange = { fraction ->
                            registerUserInteraction()
                            val target = (fraction * totalDurationMs.coerceAtLeast(1L)).toLong().coerceIn(0L, totalDurationMs)
                            isUserScrubbing = true
                            scrubPositionMs = target
                        },
                        onValueChangeFinished = {
                            registerUserInteraction()
                            val target = scrubPositionMs.coerceIn(0L, totalDurationMs)
                            currentPositionMs = target
                            isUserScrubbing = false
                            isPlaying = true
                            exoPlayer.playWhenReady = true
                            com.cyos.player.CyosFastPlayerManager.seekWithClosestSync(exoPlayer, target)
                            exoPlayer.play()
                            if (!uiState.stealthPlaybackEnabled) {
                                viewModel.savePlaybackProgress(activeVideo.path, target, totalDurationMs)
                            }
                        },
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(ElectricCyan, CircleShape)
                            )
                        },
                        track = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(Color.White.copy(alpha = 0.24f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                        .height(2.dp)
                                        .background(ElectricCyan)
                                )
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .testTag("player_seek_slider")
                    )

                    // Transport & Full Screen / Rotation Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.setAbRepeatMarker(currentPositionMs) },
                            modifier = Modifier.testTag("player_ab_repeat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "A-B Repeat",
                                tint = if (uiState.abRepeatStartMs != null) ElectricCyan else Color.White
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (!uiState.stealthPlaybackEnabled) {
                                        viewModel.savePlaybackProgress(activeVideo.path, currentPositionMs, totalDurationMs)
                                    }
                                    val idx = playlistQueue.indexOfFirst { it.path == activeVideo.path }
                                    if (idx > 0) {
                                        activeVideo = playlistQueue[idx - 1]
                                        currentPositionMs = 0L
                                        isPlaying = true
                                        exoPlayer.playWhenReady = true
                                    } else {
                                        currentPositionMs = 0L
                                        isPlaying = true
                                        exoPlayer.playWhenReady = true
                                        exoPlayer.seekTo(0L)
                                        exoPlayer.play()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous video", tint = Color.White)
                            }

                            IconButton(
                                onClick = {
                                    registerUserInteraction()
                                    val nowElapsed = SystemClock.elapsedRealtime()
                                    val target = if (nowElapsed - lastDoubleTapSeekElapsedMs >= 120L) {
                                        lastDoubleTapSeekElapsedMs = nowElapsed
                                        exoPlayer.playWhenReady = true
                                        val seeked = com.cyos.player.CyosFastPlayerManager.skipBackward10s(exoPlayer)
                                        if (!exoPlayer.isPlaying) {
                                            exoPlayer.play()
                                        }
                                        seeked
                                    } else {
                                        (currentPositionMs - 10_000L).coerceAtLeast(0L)
                                    }
                                    currentPositionMs = target
                                    isPlaying = true
                                    lastHudUpdateTimestampMs = nowElapsed
                                    activeHudType = HudType.SEEK
                                    hudLabelText = "-10s • ${Formatters.formatDuration(target)}"
                                },
                                modifier = Modifier.testTag("player_rewind_10_button")
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10 seconds", tint = Color.White)
                            }

                            Surface(
                                color = ElectricCyan.copy(alpha = 0.88f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        isPlaying = !isPlaying
                                        exoPlayer.playWhenReady = isPlaying
                                        if (!isPlaying && !uiState.stealthPlaybackEnabled) {
                                            viewModel.savePlaybackProgress(
                                                activeVideo.path,
                                                exoPlayer.currentPosition.coerceAtLeast(currentPositionMs),
                                                totalDurationMs
                                            )
                                        }
                                    }
                                    .testTag("player_play_pause_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    registerUserInteraction()
                                    val nowElapsed = SystemClock.elapsedRealtime()
                                    val target = if (nowElapsed - lastDoubleTapSeekElapsedMs >= 120L) {
                                        lastDoubleTapSeekElapsedMs = nowElapsed
                                        exoPlayer.playWhenReady = true
                                        val seeked = com.cyos.player.CyosFastPlayerManager.skipForward10s(exoPlayer)
                                        if (!exoPlayer.isPlaying) {
                                            exoPlayer.play()
                                        }
                                        seeked
                                    } else {
                                        (currentPositionMs + 10_000L).coerceAtMost(totalDurationMs)
                                    }
                                    currentPositionMs = target
                                    isPlaying = true
                                    lastHudUpdateTimestampMs = nowElapsed
                                    activeHudType = HudType.SEEK
                                    hudLabelText = "+10s • ${Formatters.formatDuration(target)}"
                                },
                                modifier = Modifier.testTag("player_forward_10_button")
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White)
                            }

                            IconButton(
                                onClick = {
                                    if (!uiState.stealthPlaybackEnabled) {
                                        viewModel.savePlaybackProgress(activeVideo.path, currentPositionMs, totalDurationMs)
                                    }
                                    val idx = playlistQueue.indexOfFirst { it.path == activeVideo.path }
                                    if (idx >= 0 && idx + 1 < playlistQueue.size) {
                                        activeVideo = playlistQueue[idx + 1]
                                        currentPositionMs = 0L
                                        isPlaying = true
                                        exoPlayer.playWhenReady = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next video", tint = Color.White)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("player_bottom_compact_icon_group")
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.cycleAspectRatioMode()
                                    val modes = AspectRatioMode.entries
                                    val next = modes[(uiState.aspectRatioMode.ordinal + 1) % modes.size]
                                    activeHudType = HudType.SEEK
                                    hudLabelText = "Screen: ${next.label}"
                                },
                                modifier = Modifier.testTag("player_aspect_ratio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Cycle Aspect Ratio (${uiState.aspectRatioMode.label})",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    isImmersiveFullScreen = !isImmersiveFullScreen
                                    if (isImmersiveFullScreen) {
                                        controlsVisible = false
                                    }
                                    activeHudType = HudType.SEEK
                                    hudLabelText = if (isImmersiveFullScreen) "Full Screen Immersive" else "System Bars Visible"
                                },
                                modifier = Modifier.testTag("player_fullscreen_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isImmersiveFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Full Screen",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAudioDialog) {
        AudioTracksAndDecoderDialog(
            audioTracks = uiState.audioTracks,
            activeAudioCodecHint = activeVideo.audioCodecHint,
            decoderEngineMode = uiState.decoderEngineMode,
            volumeBoostMultiplier = uiState.volumeBoostMultiplier,
            nightModeAudioEnabled = uiState.nightModeAudioEnabled,
            dialogueEnhancerEnabled = uiState.dialogueEnhancerEnabled,
            isAudioMuted = uiState.isAudioMuted,
            onSelectAudioTrack = { selectedTrack ->
                val groups = exoPlayer.currentTracks.groups
                if (selectedTrack.groupIndex in groups.indices) {
                    val group = groups[selectedTrack.groupIndex]
                    if (selectedTrack.trackIndex in 0 until group.length) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setOverrideForType(
                                TrackSelectionOverride(
                                    group.mediaTrackGroup,
                                    listOf(selectedTrack.trackIndex)
                                )
                            )
                            .build()
                    }
                } else {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .build()
                }
                activeVideo = activeVideo.copy(audioCodecHint = selectedTrack.codecBadge)
                viewModel.selectAudioTrack(selectedTrack, activeVideo.path)
                showAudioDialog = false
            },
            onToggleMuteAudio = { mute ->
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, mute)
                    .build()
                viewModel.setAudioMuted(mute, activeVideo.path)
            },
            onToggleDecoderEngine = { viewModel.toggleDecoderEngineMode() },
            onVolumeBoostChange = { viewModel.setVolumeBoostMultiplier(it) },
            onToggleNightModeAudio = { viewModel.toggleNightModeAudio() },
            onToggleDialogueEnhancer = { viewModel.toggleDialogueEnhancer() },
            onDismiss = { showAudioDialog = false }
        )
    }

    if (showSubtitleDialog) {
        SubtitleTracksAndSyncDialog(
            subtitleTracks = uiState.subtitleTracks,
            externalSubtitleTracks = uiState.externalSubtitleTracks,
            selectedExternalSubtitleTrackIds = uiState.selectedExternalSubtitleTrackIds,
            playAllSubtitlesSimultaneously = uiState.playAllSubtitlesSimultaneously,
            subtitleCustomSp = uiState.subtitleCustomSp,
            isSubtitleAudioAutoSyncEnabled = uiState.isSubtitleAudioAutoSyncEnabled,
            subtitleAutoSyncStatusLabel = uiState.subtitleAutoSyncStatusLabel,
            onToggleSubtitleAudioAutoSync = { viewModel.toggleSubtitleAudioAutoSync() },
            onIncreaseFontSize = { viewModel.increaseSubtitleFontSizeManual() },
            onDecreaseFontSize = { viewModel.decreaseSubtitleFontSizeManual() },
            onFontSizeChange = { viewModel.setSubtitleCustomSp(it) },
            onSelectAllSubtitles = { viewModel.selectAllSubtitles() },
            onClearAllSubtitles = { viewModel.clearAllSubtitles() },
            onToggleExternalTrack = { viewModel.toggleExternalSubtitleTrackSelection(it) },
            onToggleEmbeddedTrack = { viewModel.toggleEmbeddedSubtitleTrackSelection(it) },
            syncOffsetMs = uiState.subtitleSyncOffsetMs,
            onSelectSubtitleTrack = { selectedSub ->
                if (selectedSub == null) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                } else {
                    val groups = exoPlayer.currentTracks.groups
                    if (selectedSub.groupIndex in groups.indices) {
                        val group = groups[selectedSub.groupIndex]
                        if (selectedSub.trackIndex in 0 until group.length) {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(
                                    TrackSelectionOverride(
                                        group.mediaTrackGroup,
                                        listOf(selectedSub.trackIndex)
                                    )
                                )
                                .build()
                        }
                    }
                }
                viewModel.selectSubtitleTrack(selectedSub)
                showSubtitleDialog = false
            },
            onAdjustOffsetMs = { viewModel.adjustSubtitleSyncOffset(it) },
            onResetOffset = { viewModel.resetSubtitleSyncOffset() },
            onLoadExternalSrt = {
                showSubtitleDialog = false
                srtPickerLauncher.launch("*/*")
            },
            onOpenSubtitleStudio = {
                showSubtitleDialog = false
                viewModel.navigateTo(Screen.SubtitleStudio)
            },
            onDismiss = { showSubtitleDialog = false }
        )
    }

    if (showSmartEnhanceDialog) {
        AlertDialog(
            onDismissRequest = { showSmartEnhanceDialog = false },
            containerColor = Color(0xC70E1420),
            modifier = Modifier.testTag("smart_enhancement_dialog"),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Smart Video Enhancement",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Real-time 4K/8K clarity, HDR tone mapping & AMOLED color engine:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SmartEnhancementMode.entries.forEach { mode ->
                        val selected = uiState.smartEnhancementMode == mode
                        Surface(
                            color = if (selected) {
                                ElectricCyan.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.setSmartEnhancementMode(mode)
                                }
                                .testTag("smart_enhance_mode_${mode.name}")
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
                                        text = mode.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberGold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "4K/8K Micro-Sharpness & Clarity Boost: ${(customClarityBoost * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = customClarityBoost,
                        onValueChange = { customClarityBoost = it },
                        valueRange = 0f..1f
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSmartEnhanceDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Items 4 & 5: Skip Speed (x1, x2, x3, x4, x5) Manual Control & Slide Popup Dialog
    if (showSpeedSkipPopupDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedSkipPopupDialog = false },
            containerColor = Color(0xDB0E1420),
            modifier = Modifier.testTag("player_speed_skip_popup_dialog"),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Skip Speed Control (x1 – x5)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Tap x1 to x5 or slide to control playback & hold-skip speed:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        (1..5).forEach { step ->
                            val isSelected = kotlin.math.abs(uiState.playbackSpeed - step.toFloat()) < 0.1f
                            Surface(
                                color = if (isSelected) AmberGold.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) AmberGold else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        registerUserInteraction()
                                        val nextSpeed = step.toFloat()
                                        holdSpeedMultiplier = nextSpeed
                                        viewModel.setPlaybackSpeed(nextSpeed)
                                        activeHudType = HudType.SEEK
                                        hudLabelText = "Skip Speed x$step"
                                    }
                                    .testTag("popup_speed_option_x$step")
                            ) {
                                Text(
                                    text = "x$step",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = RobotoNumbersFontFamily
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AmberGold else Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Manual Speed Slider: ${String.format(Locale.US, "%.2fx", uiState.playbackSpeed)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = uiState.playbackSpeed.coerceIn(1.0f, 5.0f),
                        onValueChange = { newSpeed ->
                            registerUserInteraction()
                            val rounded = ((newSpeed * 4f).roundToInt() / 4f).coerceIn(1.0f, 5.0f)
                            holdSpeedMultiplier = rounded
                            viewModel.setPlaybackSpeed(rounded)
                        },
                        valueRange = 1.0f..5.0f,
                        steps = 15,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("popup_speed_slider")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSpeedSkipPopupDialog = false },
                    modifier = Modifier.testTag("close_speed_popup_button")
                ) {
                    Text("Done")
                }
            }
        )
    }

    // Item 6: Video Zoom Popup Dialog (Zoom Out, Reset 100%, Zoom In, Presets & Slider all in popup)
    if (showVideoZoomPopupDialog) {
        AlertDialog(
            onDismissRequest = { showVideoZoomPopupDialog = false },
            containerColor = Color(0xDB0E1420),
            modifier = Modifier.testTag("player_zoom_popup_dialog"),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Video Zoom (${(uiState.videoZoomScale * 100f).roundToInt()}%)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                registerUserInteraction()
                                val nextZoom = (uiState.videoZoomScale - 0.25f).coerceIn(0.5f, 4.0f)
                                viewModel.setVideoZoomScale(nextZoom)
                                if (nextZoom <= 1.01f) zoomPanOffset = Offset.Zero
                                activeHudType = HudType.SEEK
                                hudLabelText = "Zoom ${(nextZoom * 100f).roundToInt()}%"
                            },
                            modifier = Modifier.testTag("player_zoom_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Zoom Out",
                                tint = ElectricCyan
                            )
                        }
                        Surface(
                            color = AmberGold.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    registerUserInteraction()
                                    viewModel.resetVideoZoom()
                                    zoomPanOffset = Offset.Zero
                                    activeHudType = HudType.SEEK
                                    hudLabelText = "Zoom 100%"
                                }
                                .testTag("player_zoom_reset_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CenterFocusStrong,
                                    contentDescription = "Reset Video Zoom",
                                    tint = AmberGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reset 100%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberGold
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                registerUserInteraction()
                                val nextZoom = (uiState.videoZoomScale + 0.25f).coerceIn(0.5f, 4.0f)
                                viewModel.setVideoZoomScale(nextZoom)
                                activeHudType = HudType.SEEK
                                hudLabelText = "Zoom ${(nextZoom * 100f).roundToInt()}%"
                            },
                            modifier = Modifier.testTag("player_zoom_in_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Zoom In",
                                tint = AmberGold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f).forEach { preset ->
                            val pct = (preset * 100f).roundToInt()
                            val selected = kotlin.math.abs(uiState.videoZoomScale - preset) < 0.05f
                            Surface(
                                color = if (selected) ElectricCyan.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        registerUserInteraction()
                                        viewModel.setVideoZoomScale(preset)
                                        if (preset <= 1.01f) zoomPanOffset = Offset.Zero
                                    }
                                    .testTag("popup_zoom_preset_$pct")
                            ) {
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) ElectricCyan else Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Slider(
                        value = uiState.videoZoomScale.coerceIn(0.5f, 4.0f),
                        onValueChange = { scale ->
                            registerUserInteraction()
                            viewModel.setVideoZoomScale(scale)
                            if (scale <= 1.01f) zoomPanOffset = Offset.Zero
                        },
                        valueRange = 0.5f..4.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("popup_zoom_slider")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showVideoZoomPopupDialog = false },
                    modifier = Modifier.testTag("close_zoom_popup_button")
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun PlayerIconOnlyButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0x88101622),
        shape = CircleShape,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(1.dp, tint.copy(alpha = 0.42f), CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private enum class PlayerGestureLock {
    NONE,
    PINCH_ZOOM,
    VERTICAL_BRIGHTNESS,
    VERTICAL_VOLUME,
    HORIZONTAL_SKIP,
    HOLD_FAST_FORWARD
}
