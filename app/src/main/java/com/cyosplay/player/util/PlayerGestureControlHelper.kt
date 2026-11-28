package com.cyosplay.player.util

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

enum class VideoGestureZone {
    LEFT_BRIGHTNESS,
    RIGHT_VOLUME,
    OUTSIDE_VIDEO_AREA
}

object PlayerGestureControlHelper {

    const val DEFAULT_PLAYER_BRIGHTNESS = 0.65f
    const val MIN_PLAYER_BRIGHTNESS = 0.02f
    const val MAX_PLAYER_BRIGHTNESS = 1.0f

    const val DEFAULT_PLAYER_VOLUME = 0.75f
    const val MIN_PLAYER_VOLUME = 0.0f
    const val MAX_PLAYER_VOLUME = 1.0f

    const val DEFAULT_MIN_SWIPE_DISTANCE_DP = 8f
    const val OVERLAY_AUTO_HIDE_DELAY_MS = 1000L
    const val PREFERENCE_SAVE_DEBOUNCE_MS = 250L

    private const val ACTIVE_MIN_X_FRACTION = 0.05f
    private const val ACTIVE_MAX_X_FRACTION = 0.95f
    private const val ACTIVE_MIN_Y_FRACTION = 0.12f
    private const val ACTIVE_MAX_Y_FRACTION = 0.86f
    private const val DIRECTION_DOMINANCE_RATIO = 1.15f

    fun sanitizeBrightness(
        value: Float?,
        defaultValue: Float = DEFAULT_PLAYER_BRIGHTNESS
    ): Float {
        val fallback = defaultValue.coerceIn(MIN_PLAYER_BRIGHTNESS, MAX_PLAYER_BRIGHTNESS)
        if (value == null || !value.isFinite()) return fallback
        return value.coerceIn(MIN_PLAYER_BRIGHTNESS, MAX_PLAYER_BRIGHTNESS)
    }

    fun sanitizeVolume(
        value: Float?,
        defaultValue: Float = DEFAULT_PLAYER_VOLUME
    ): Float {
        val fallback = defaultValue.coerceIn(MIN_PLAYER_VOLUME, MAX_PLAYER_VOLUME)
        if (value == null || !value.isFinite()) return fallback
        return value.coerceIn(MIN_PLAYER_VOLUME, MAX_PLAYER_VOLUME)
    }

    /**
     * Determines whether a touch coordinate lies inside the active video playback surface
     * (excluding system bar edges, top toolbar, and bottom playback controls) and divides
     * the active area into Left Half (Brightness) and Right Half (Volume).
     */
    fun resolveGestureZone(
        touchX: Float,
        touchY: Float,
        containerWidth: Float,
        containerHeight: Float
    ): VideoGestureZone {
        if (containerWidth <= 0f || containerHeight <= 0f) {
            return VideoGestureZone.OUTSIDE_VIDEO_AREA
        }
        val minX = containerWidth * ACTIVE_MIN_X_FRACTION
        val maxX = containerWidth * ACTIVE_MAX_X_FRACTION
        val minY = containerHeight * ACTIVE_MIN_Y_FRACTION
        val maxY = containerHeight * ACTIVE_MAX_Y_FRACTION

        if (touchX !in minX..maxX || touchY !in minY..maxY) {
            return VideoGestureZone.OUTSIDE_VIDEO_AREA
        }
        return if (touchX < containerWidth * 0.5f) {
            VideoGestureZone.LEFT_BRIGHTNESS
        } else {
            VideoGestureZone.RIGHT_VOLUME
        }
    }

    /**
     * Verifies whether a brightness or volume swipe gesture is allowed to start or continue
     * based on current player state. Strictly blocks vertical gestures during or right after
     * pinch-zoom, horizontal seeking, hold-skip, or double-tap skip.
     */
    fun canHandlePlaybackVerticalGesture(
        isPlaying: Boolean,
        isControlsLocked: Boolean,
        isInPipMode: Boolean,
        activePointerCount: Int,
        isHoldFastForwardActive: Boolean,
        isHorizontalSeekingActive: Boolean,
        isPinchZoomActive: Boolean = false,
        recentlyPinched: Boolean = false,
        isDoubleTapSkipActive: Boolean = false
    ): Boolean {
        // Allow vertical brightness & volume gestures both while playing and while paused
        return !isControlsLocked &&
            !isInPipMode &&
            activePointerCount == 1 &&
            !isHoldFastForwardActive &&
            !isHorizontalSeekingActive &&
            !isPinchZoomActive &&
            !recentlyPinched &&
            !isDoubleTapSkipActive
    }

    /**
     * Verifies whether a horizontal sliding skip (scrub) gesture is allowed to start or continue.
     * Strictly isolated from pinch-zoom, vertical brightness, vertical volume, hold-skip, and tap-skip.
     */
    fun canHandlePlaybackHorizontalSeek(
        isControlsLocked: Boolean,
        isInPipMode: Boolean,
        activePointerCount: Int,
        isHoldFastForwardActive: Boolean,
        isVerticalBrightnessActive: Boolean = false,
        isVerticalVolumeActive: Boolean = false,
        isVerticalGestureActive: Boolean = false,
        isPinchZoomActive: Boolean = false,
        recentlyPinched: Boolean = false,
        isDoubleTapSkipActive: Boolean = false
    ): Boolean {
        return !isControlsLocked &&
            !isInPipMode &&
            activePointerCount == 1 &&
            !isHoldFastForwardActive &&
            !isVerticalBrightnessActive &&
            !isVerticalVolumeActive &&
            !isVerticalGestureActive &&
            !isPinchZoomActive &&
            !recentlyPinched &&
            !isDoubleTapSkipActive
    }

    /**
     * Verifies whether a stationary long-press Hold Skip (2x fast-forward with 1x..5x horizontal
     * speed tuning) is allowed to start, including smoothly while Screen Lock is active
     * (`allowWhenLocked = true`). Strictly blocked if pinch-zoom, multi-touch, vertical
     * brightness/volume, horizontal seek, or tap-skip is active.
     */
    fun canStartHoldFastForward(
        isPlaying: Boolean = true,
        isControlsLocked: Boolean,
        isInPipMode: Boolean,
        activePointerCount: Int,
        isVerticalGestureActive: Boolean,
        isHorizontalSeekingActive: Boolean,
        isPinchZoomActive: Boolean,
        recentlyPinched: Boolean,
        isDoubleTapSkipActive: Boolean = false,
        allowWhenLocked: Boolean = true
    ): Boolean {
        return isPlaying &&
            (allowWhenLocked || !isControlsLocked) &&
            !isInPipMode &&
            activePointerCount <= 1 &&
            !isVerticalGestureActive &&
            !isHorizontalSeekingActive &&
            !isPinchZoomActive &&
            !recentlyPinched &&
            !isDoubleTapSkipActive
    }

    /**
     * Computes a discrete 1.0x..5.0x hold-skip speed multiplier from accumulated horizontal slide distance
     * with a calibrated deadzone and hysteresis so minor thumb tremor never thrashes playback speed
     * or wastes CPU/battery while holding.
     */
    fun computeHoldSkipSpeedStep(
        startHoldSpeed: Float,
        totalDxPx: Float,
        containerWidthPx: Float,
        currentHoldSpeed: Float = startHoldSpeed
    ): Float {
        val clampedStart = startHoldSpeed.coerceIn(1.0f, 5.0f).roundToInt().toFloat()
        val clampedCurrent = currentHoldSpeed.coerceIn(1.0f, 5.0f).roundToInt().toFloat()
        if (!totalDxPx.isFinite() || !containerWidthPx.isFinite() || containerWidthPx <= 0f) {
            return clampedCurrent
        }
        val stepWidthPx = (containerWidthPx / 5.5f).coerceAtLeast(48f)
        val deadzonePx = stepWidthPx * 0.22f
        if (abs(totalDxPx) < deadzonePx && clampedCurrent == clampedStart) {
            return clampedStart
        }
        val rawContinuous = (clampedStart + (totalDxPx / stepWidthPx)).coerceIn(1.0f, 5.0f)
        return if (abs(rawContinuous - clampedCurrent) >= 0.62f) {
            rawContinuous.roundToInt().coerceIn(1, 5).toFloat()
        } else {
            clampedCurrent
        }
    }

    /**
     * Returns true only when the requested playback speed differs meaningfully from the player's
     * current speed, preventing redundant audio-pipeline reconfigurations and CPU wakeups.
     */
    fun shouldDispatchPlaybackSpeedChange(
        currentPlayerSpeed: Float,
        targetSpeed: Float
    ): Boolean {
        if (!currentPlayerSpeed.isFinite() || !targetSpeed.isFinite()) return true
        return abs(currentPlayerSpeed - targetSpeed) >= 0.01f
    }

    /**
     * Verifies whether a Double-Tap Skip (±10s) gesture is allowed to execute.
     * Supports smooth double-tap ±10s seek even when Screen Lock is enabled (`allowWhenLocked = true`),
     * while staying strictly blocked if pinch-zoom, multi-touch, vertical brightness/volume,
     * horizontal sliding skip, or hold-skip is active.
     */
    fun canHandleDoubleTapSkip(
        isControlsLocked: Boolean,
        isInPipMode: Boolean,
        activePointerCount: Int,
        isPinchZoomActive: Boolean,
        recentlyPinched: Boolean,
        isVerticalGestureActive: Boolean,
        isHorizontalSeekingActive: Boolean,
        isHoldFastForwardActive: Boolean,
        allowWhenLocked: Boolean = true
    ): Boolean {
        return (allowWhenLocked || !isControlsLocked) &&
            !isInPipMode &&
            activePointerCount <= 1 &&
            !isPinchZoomActive &&
            !recentlyPinched &&
            !isVerticalGestureActive &&
            !isHorizontalSeekingActive &&
            !isHoldFastForwardActive
    }

    data class DoubleTapSkipCalculation(
        val targetPositionMs: Long,
        val cumulativeDeltaMs: Long,
        val shouldDispatchPlayerSeek: Boolean
    )

    /**
     * Computes a smooth, low-CPU/low-battery cumulative double-tap seek target (±10s per double-tap,
     * accumulating across consecutive double-taps in the same direction within 680ms) and throttles
     * hardware decoder seek dispatches to 150ms intervals.
     */
    fun computeDoubleTapCumulativeSkip(
        currentPositionMs: Long,
        totalDurationMs: Long,
        isLeftHalf: Boolean,
        previousDoubleTapElapsedMs: Long,
        previousHardwareSeekElapsedMs: Long,
        nowElapsedMs: Long,
        previousCumulativeDeltaMs: Long,
        stepDurationMs: Long = 10_000L
    ): DoubleTapSkipCalculation {
        val safeDuration = totalDurationMs.coerceAtLeast(1_000L)
        val safeCurrent = currentPositionMs.coerceIn(0L, safeDuration)
        val signedStep = if (isLeftHalf) -stepDurationMs else stepDurationMs
        val isContinuingStreak = previousDoubleTapElapsedMs > 0L &&
            (nowElapsedMs - previousDoubleTapElapsedMs) in 0L..680L &&
            ((previousCumulativeDeltaMs < 0L && signedStep < 0L) ||
                (previousCumulativeDeltaMs > 0L && signedStep > 0L))
        val updatedCumulativeDelta = if (isContinuingStreak) {
            previousCumulativeDeltaMs + signedStep
        } else {
            signedStep
        }
        val targetPosition = (safeCurrent + signedStep).coerceIn(0L, safeDuration)
        val shouldDispatch = previousHardwareSeekElapsedMs <= 0L ||
            (nowElapsedMs - previousHardwareSeekElapsedMs) >= 150L
        return DoubleTapSkipCalculation(
            targetPositionMs = targetPosition,
            cumulativeDeltaMs = updatedCumulativeDelta,
            shouldDispatchPlayerSeek = shouldDispatch
        )
    }

    /**
     * Guarantees brightness remains completely unchanged when zooming or performing non-brightness gestures:
     * Whenever multi-touch (`activePointerCount > 1`), `isPinchZoomActive`, `recentlyPinched`,
     * or another non-brightness gesture (`isNonBrightnessGestureActive`) is detected,
     * returns the pre-gesture brightness snapshot untouched.
     */
    fun resolveBrightnessDuringZoomOrMultiTouch(
        currentBrightness: Float,
        preZoomBrightnessSnapshot: Float,
        activePointerCount: Int,
        isPinchZoomActive: Boolean,
        recentlyPinched: Boolean,
        isNonBrightnessGestureActive: Boolean = false
    ): Float {
        return if (activePointerCount > 1 || isPinchZoomActive || recentlyPinched || isNonBrightnessGestureActive) {
            sanitizeBrightness(preZoomBrightnessSnapshot)
        } else {
            sanitizeBrightness(currentBrightness)
        }
    }

    /**
     * Guarantees volume remains completely unchanged during pinch-zoom, brightness swipe,
     * horizontal sliding skip, hold skip, or tap skip.
     */
    fun resolveVolumeDuringNonVolumeGesture(
        currentVolume: Float,
        preGestureVolumeSnapshot: Float,
        activePointerCount: Int,
        isPinchZoomActive: Boolean,
        recentlyPinched: Boolean = false,
        isNonVolumeGestureActive: Boolean = false,
        isBrightnessActive: Boolean = false,
        isHorizontalSeekActive: Boolean = false,
        isHoldFastForwardActive: Boolean = false
    ): Float {
        return if (activePointerCount > 1 ||
            isPinchZoomActive ||
            recentlyPinched ||
            isNonVolumeGestureActive ||
            isBrightnessActive ||
            isHorizontalSeekActive ||
            isHoldFastForwardActive
        ) {
            sanitizeVolume(preGestureVolumeSnapshot)
        } else {
            sanitizeVolume(currentVolume)
        }
    }

    /**
     * Checks whether the accumulated drag vector qualifies as an intentional vertical swipe
     * rather than a tap or horizontal seek gesture.
     */
    fun isVerticalSwipeGesture(
        totalDx: Float,
        totalDy: Float,
        minSwipeDistancePx: Float
    ): Boolean {
        val threshold = minSwipeDistancePx.coerceAtLeast(1f)
        return abs(totalDy) >= threshold && abs(totalDy) > abs(totalDx) * DIRECTION_DOMINANCE_RATIO
    }

    /**
     * Checks whether the accumulated drag vector qualifies as an intentional horizontal seek
     * rather than a tap or vertical brightness/volume swipe.
     */
    fun isHorizontalSeekGesture(
        totalDx: Float,
        totalDy: Float,
        minSwipeDistancePx: Float
    ): Boolean {
        val threshold = minSwipeDistancePx.coerceAtLeast(1f)
        return abs(totalDx) >= threshold && abs(totalDx) > abs(totalDy) * DIRECTION_DOMINANCE_RATIO
    }

    /**
     * Computes the updated brightness level for a vertical drag delta.
     * Swiping up (negative dragDeltaY) increases brightness; swiping down (positive dragDeltaY) decreases it.
     */
    fun computeUpdatedBrightness(
        currentBrightness: Float,
        dragDeltaY: Float,
        containerHeightPx: Float
    ): Float {
        val safeCurrent = sanitizeBrightness(currentBrightness)
        if (!dragDeltaY.isFinite() || containerHeightPx <= 0f) return safeCurrent
        val activeHeight = (containerHeightPx * 0.64f).coerceAtLeast(120f)
        val deltaFraction = (-dragDeltaY / activeHeight).coerceIn(-0.20f, 0.20f)
        return sanitizeBrightness(safeCurrent + deltaFraction)
    }

    /**
     * Computes the updated audio volume level for a vertical drag delta.
     * Swiping up (negative dragDeltaY) increases volume; swiping down (positive dragDeltaY) decreases it.
     */
    fun computeUpdatedVolume(
        currentVolume: Float,
        dragDeltaY: Float,
        containerHeightPx: Float
    ): Float {
        val safeCurrent = sanitizeVolume(currentVolume)
        if (!dragDeltaY.isFinite() || containerHeightPx <= 0f) return safeCurrent
        val activeHeight = (containerHeightPx * 0.64f).coerceAtLeast(120f)
        val deltaFraction = (-dragDeltaY / activeHeight).coerceIn(-0.20f, 0.20f)
        return sanitizeVolume(safeCurrent + deltaFraction)
    }

    /**
     * Applies in-app window brightness directly to the Activity window without modifying
     * the device's persistent system-wide brightness setting, skipping redundant WindowManager
     * layout updates when the effective brightness change is below 0.004f.
     */
    fun applyWindowBrightness(
        activity: Activity?,
        brightnessFraction: Float,
        batterySaverEnabled: Boolean = false
    ): Float {
        val sanitized = sanitizeBrightness(brightnessFraction)
        val effective = if (batterySaverEnabled) {
            sanitized.coerceAtMost(0.65f)
        } else {
            sanitized
        }
        val window = activity?.window ?: return effective
        val params = window.attributes
        if (abs(params.screenBrightness - effective) >= 0.004f) {
            params.screenBrightness = effective
            window.attributes = params
        }
        return effective
    }

    /**
     * Restores the Activity window brightness attribute to its original state when exiting the player.
     */
    fun restoreWindowBrightness(
        activity: Activity?,
        initialWindowBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    ) {
        val window = activity?.window ?: return
        val params = window.attributes
        params.screenBrightness = initialWindowBrightness
        window.attributes = params
    }

    /**
     * Synchronizes the player volume fraction (0.0..1.0) with Android's AudioManager STREAM_MUSIC
     * without showing the system volume UI panel (flags = 0), skipping redundant IPC calls if the
     * stream volume index is already at the target step.
     */
    fun syncVolumeWithAudioManager(
        context: Context,
        volumeFraction: Float,
        isMuted: Boolean = false
    ): Int {
        val safeFraction = if (isMuted) 0f else sanitizeVolume(volumeFraction)
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return 0
            if (audioManager.isVolumeFixed) {
                return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val targetIndex = if (safeFraction <= 0.005f) {
                0
            } else {
                (safeFraction * maxVolume).roundToInt().coerceIn(1, maxVolume)
            }
            val currentIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentIndex != targetIndex) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
            }
            targetIndex
        } catch (_: SecurityException) {
            // Respect Do Not Disturb / platform policy restrictions gracefully
            0
        } catch (_: Exception) {
            0
        }
    }

    fun formatBrightnessPercentage(brightnessFraction: Float): Int {
        return (sanitizeBrightness(brightnessFraction) * 100f).roundToInt().coerceIn(2, 100)
    }

    fun formatVolumePercentage(volumeFraction: Float, isMuted: Boolean = false): Int {
        if (isMuted) return 0
        return (sanitizeVolume(volumeFraction) * 100f).roundToInt().coerceIn(0, 100)
    }

    data class IntroCreditWindowInfo(
        val isInIntroWindow: Boolean,
        val isInCreditsWindow: Boolean,
        val introSkipTargetMs: Long,
        val creditsSkipTargetMs: Long
    )

    data class ChapterSegment(
        val title: String,
        val startMs: Long,
        val endMs: Long
    )

    /**
     * Evaluates whether the current playback position is inside an Intro or Credits segment
     * strictly using real embedded or sidecar chapter metadata. Never fabricates arbitrary
     * intro or credits timestamps when chapter metadata is absent.
     */
    fun resolveIntroCreditWindow(
        totalDurationMs: Long,
        currentPositionMs: Long,
        chapters: List<ChapterSegment> = emptyList()
    ): IntroCreditWindowInfo {
        if (totalDurationMs <= 0L || currentPositionMs < 0L || chapters.isEmpty()) {
            return IntroCreditWindowInfo(
                isInIntroWindow = false,
                isInCreditsWindow = false,
                introSkipTargetMs = 0L,
                creditsSkipTargetMs = totalDurationMs.coerceAtLeast(0L)
            )
        }
        val safePos = currentPositionMs.coerceIn(0L, totalDurationMs)
        val activeChapter = chapters.firstOrNull { ch ->
            safePos >= ch.startMs && safePos < ch.endMs && ch.endMs > ch.startMs
        } ?: return IntroCreditWindowInfo(
            isInIntroWindow = false,
            isInCreditsWindow = false,
            introSkipTargetMs = 0L,
            creditsSkipTargetMs = totalDurationMs.coerceAtLeast(0L)
        )

        val lowerTitle = activeChapter.title.lowercase(java.util.Locale.US)
        val isIntroChapter = lowerTitle.contains("intro") ||
            lowerTitle.contains("opening") ||
            lowerTitle.contains("recap") ||
            lowerTitle.contains("prologue") ||
            lowerTitle == "op"
        val isCreditsChapter = lowerTitle.contains("credit") ||
            lowerTitle.contains("ending") ||
            lowerTitle.contains("outro") ||
            lowerTitle.contains("epilogue") ||
            lowerTitle == "ed"

        val skipTarget = activeChapter.endMs.coerceIn(0L, totalDurationMs)
        return IntroCreditWindowInfo(
            isInIntroWindow = isIntroChapter && skipTarget > safePos,
            isInCreditsWindow = isCreditsChapter && skipTarget > safePos,
            introSkipTargetMs = if (isIntroChapter) skipTarget else 0L,
            creditsSkipTargetMs = if (isCreditsChapter) skipTarget else totalDurationMs.coerceAtLeast(0L)
        )
    }

    const val HIGH_VOLUME_BRIGHTNESS_THRESHOLD = 0.85f
    const val SAFE_LOWERED_VOLUME_BRIGHTNESS = 0.50f
    const val HIGH_VOLUME_AND_BRIGHTNESS_WARNING_TEXT =
        "High volume and screen brightness detected (≥85%). Tap Lower Both to drop both to 50% for eye/ear comfort and battery savings."

    data class AutoSafeVolumeBrightnessResult(
        val triggeredWarningAndAuto50: Boolean,
        val safeVolume: Float,
        val safeBrightness: Float,
        val autoLoweredToSafe50: Boolean = false
    ) {
        val volumeFraction: Float get() = safeVolume
        val brightnessFraction: Float get() = safeBrightness
    }

    /**
     * Returns true when BOTH player volume AND screen brightness reach or exceed
     * the 85% threshold (0.85f) and audio is not muted.
     */
    fun isHighVolumeAndBrightnessDetected(
        volumeFraction: Float,
        brightnessFraction: Float,
        isMuted: Boolean = false
    ): Boolean {
        if (isMuted) return false
        val safeVol = sanitizeVolume(volumeFraction)
        val safeBri = sanitizeBrightness(brightnessFraction)
        return safeVol >= HIGH_VOLUME_BRIGHTNESS_THRESHOLD && safeBri >= HIGH_VOLUME_BRIGHTNESS_THRESHOLD
    }

    /**
     * Non-destructive safety check: detects when both volume and screen brightness
     * reach or exceed 85% (0.85f) so the UI displays a warning banner with a "Lower Both"
     * button, without forcibly snapping the user's sliders down.
     */
    fun enforceAutoSafeVolumeAndBrightness(
        volumeFraction: Float,
        brightnessFraction: Float,
        isMuted: Boolean = false
    ): AutoSafeVolumeBrightnessResult {
        val safeVol = sanitizeVolume(volumeFraction)
        val safeBri = sanitizeBrightness(brightnessFraction)
        val exceeded = isHighVolumeAndBrightnessDetected(safeVol, safeBri, isMuted)
        return AutoSafeVolumeBrightnessResult(
            triggeredWarningAndAuto50 = exceeded,
            safeVolume = safeVol,
            safeBrightness = safeBri,
            autoLoweredToSafe50 = false
        )
    }
}
