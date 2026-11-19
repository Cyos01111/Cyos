package com.cyosplay.player.util

import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import com.cyosplay.player.data.model.PlayerOrientationMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Hardware orientation & 3-axis mobile movement sensor calculator with ±15° angular hysteresis
 * deadzone and fast 120ms debounce to support instant 4-way auto-rotation (`0°` Portrait,
 * `90°` Reverse Landscape, `180°` Reverse Portrait, `270°` Landscape) as the phone tilts,
 * while preventing flat-on-table Z-axis flicker.
 */
object ScreenOrientationSensorHelper {

    const val HYSTERESIS_DEGREES: Int = 15
    const val DEBOUNCE_MS: Long = 120L

    /**
     * Computes the physical device tilt angle in degrees (0..359) from a 3-axis accelerometer
     * or gravity sensor vector `(gx, gy, gz)` in m/s², matching Android's `OrientationEventListener`
     * coordinate convention:
     * - `0°`: Upright Portrait (`gy` dominant positive)
     * - `90°`: Reverse Landscape / tilted right (`gx` dominant negative)
     * - `180°`: Reverse Portrait / upside down (`gy` dominant negative)
     * - `270°`: Standard Landscape / tilted left (`gx` dominant positive)
     * Returns [OrientationEventListener.ORIENTATION_UNKNOWN] when the device is lying nearly flat
     * on a table (Z-axis inclination > ~72°) to avoid random rotation spin.
     */
    fun computeTiltDegreesFromAccelerometer(gx: Float, gy: Float, gz: Float): Int {
        val magnitude = sqrt((gx * gx + gy * gy + gz * gz).toDouble())
        if (magnitude < 1.5) {
            return OrientationEventListener.ORIENTATION_UNKNOWN
        }
        val xyMagnitude = sqrt((gx * gx + gy * gy).toDouble())
        // If phone is nearly flat on a surface (horizontal plane projection < 28% of gravity), keep current orientation
        if (xyMagnitude / magnitude < 0.28) {
            return OrientationEventListener.ORIENTATION_UNKNOWN
        }
        // Standard Android sensor frame: when upright portrait, gy ~ +9.8, gx ~ 0 -> 0°
        // When rotated left into standard landscape, gx ~ +9.8, gy ~ 0 -> 270°
        // When rotated right into reverse landscape, gx ~ -9.8, gy ~ 0 -> 90°
        val angleRad = atan2(-gx.toDouble(), gy.toDouble())
        val rawDegrees = Math.toDegrees(angleRad).roundToInt()
        return ((rawDegrees % 360) + 360) % 360
    }

    /**
     * Maps raw physical sensor degrees (0..359, or [OrientationEventListener.ORIENTATION_UNKNOWN])
     * to a target [ActivityInfo] screen orientation constant, applying [HYSTERESIS_DEGREES]
     * around the 45°/135°/225°/315° quadrant boundaries so slight hand tilt does not flicker.
     */
    fun mapSensorDegreesToOrientation(
        degrees: Int,
        currentOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        landscapeOnly: Boolean = false,
        portraitOnly: Boolean = false
    ): Int {
        if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN || degrees !in 0..359) {
            return when {
                landscapeOnly -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                portraitOnly -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                currentOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> currentOrientation
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        val normalized = ((degrees % 360) + 360) % 360

        if (landscapeOnly) {
            // 270° is standard landscape (top of device rotated left); 90° is reverse landscape
            return when {
                normalized in (225 + HYSTERESIS_DEGREES)..(315 - HYSTERESIS_DEGREES) ->
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                normalized in (45 + HYSTERESIS_DEGREES)..(135 - HYSTERESIS_DEGREES) ->
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                // Inside hysteresis transition zone: keep current landscape side if already in one
                currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                    currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ->
                    currentOrientation
                normalized in 180..359 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
        }

        if (portraitOnly) {
            // 0° is standard upright portrait; 180° is reverse portrait
            return when {
                normalized >= (315 + HYSTERESIS_DEGREES) || normalized <= (45 - HYSTERESIS_DEGREES) ->
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                normalized in (135 + HYSTERESIS_DEGREES)..(225 - HYSTERESIS_DEGREES) ->
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
                    currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT ->
                    currentOrientation
                normalized in 90..269 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }

        // Full 4-quadrant sensor with ±15° hysteresis around 45°, 135°, 225°, 315°
        if (currentOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            val centerAngle = when (currentOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> 0
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> 90
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> 180
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> 270
                else -> -1
            }
            if (centerAngle >= 0 && angularDistance(normalized, centerAngle) <= (45 + HYSTERESIS_DEGREES)) {
                return currentOrientation
            }
        }

        return when {
            normalized >= 315 || normalized < 45 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            normalized in 45..134 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            normalized in 135..224 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    /**
     * Combines the user's selected [PlayerOrientationMode], the active video's real aspect ratio
     * ([videoWidth] x [videoHeight]), and live physical sensor degrees from mobile movement
     * to determine the target [ActivityInfo] screen orientation.
     *
     * When `sensorDegrees` is known, [PlayerOrientationMode.AUTO_VIDEO] responds immediately to
     * 4-way physical phone tilt (Portrait 0°, Reverse Landscape 90°, Reverse Portrait 180°,
     * Landscape 270°). When `sensorDegrees` is unknown (e.g., flat on table), it defaults to
     * matching the video's aspect ratio.
     */
    fun resolveTargetOrientation(
        mode: PlayerOrientationMode,
        videoWidth: Int,
        videoHeight: Int,
        sensorDegrees: Int = OrientationEventListener.ORIENTATION_UNKNOWN,
        currentOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ): Int {
        val isLandscapeVideo = videoWidth <= 0 || videoHeight <= 0 || videoWidth >= videoHeight
        return when (mode) {
            PlayerOrientationMode.AUTO_VIDEO -> {
                if (sensorDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    if (isLandscapeVideo) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }
                } else {
                    mapSensorDegreesToOrientation(
                        degrees = sensorDegrees,
                        currentOrientation = currentOrientation
                    )
                }
            }
            PlayerOrientationMode.LANDSCAPE_SENSOR -> {
                if (sensorDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    mapSensorDegreesToOrientation(
                        degrees = sensorDegrees,
                        currentOrientation = currentOrientation,
                        landscapeOnly = true
                    )
                }
            }
            PlayerOrientationMode.PORTRAIT -> {
                if (sensorDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    mapSensorDegreesToOrientation(
                        degrees = sensorDegrees,
                        currentOrientation = currentOrientation,
                        portraitOnly = true
                    )
                }
            }
            PlayerOrientationMode.SYSTEM_UNLOCKED -> {
                if (sensorDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                } else {
                    mapSensorDegreesToOrientation(
                        degrees = sensorDegrees,
                        currentOrientation = currentOrientation
                    )
                }
            }
        }
    }

    /**
     * Direct Tilt-Fix Landscape Lock:
     * Locks the video player strictly to landscape, ignores all vertical accelerometer / portrait tilt
     * events (`0°` upright portrait and `180°` upside-down portrait), and allows smooth 180° horizontal
     * flipping between standard left landscape (`270°` -> [ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE])
     * and right reverse landscape (`90°` -> [ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE]).
     */
    fun resolveLandscapeLockedOrientation(
        sensorDegrees: Int,
        currentOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    ): Int {
        val currentLandscape = if (
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ) {
            currentOrientation
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        if (sensorDegrees == OrientationEventListener.ORIENTATION_UNKNOWN || sensorDegrees !in 0..359) {
            return currentLandscape
        }

        val normalized = ((sensorDegrees % 360) + 360) % 360
        // Ignore vertical portrait tilt zones (0° ± 30° and 180° ± 30°) completely
        val isVerticalPortraitTilt = normalized <= 30 || normalized >= 330 || normalized in 150..210
        if (isVerticalPortraitTilt) {
            return currentLandscape
        }

        // Flip 180° horizontally when device is tilted clearly into 90° (Reverse Landscape) or 270° (Landscape)
        return when {
            normalized in (90 - (45 - HYSTERESIS_DEGREES))..(90 + (45 - HYSTERESIS_DEGREES)) ->
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            normalized in (270 - (45 - HYSTERESIS_DEGREES))..(270 + (45 - HYSTERESIS_DEGREES)) ->
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> currentLandscape
        }
    }

    /**
     * Returns a 90° rotation fallback if the host window or system blocks native orientation locking
     * and keeps the container in portrait (`containerWidth < containerHeight`) during landscape playback.
     */
    fun computeFallbackCanvasRotationDegrees(
        containerWidthPx: Int,
        containerHeightPx: Int,
        nativeOrientationLockBlocked: Boolean = false
    ): Float {
        if (!nativeOrientationLockBlocked) return 0f
        return if (containerWidthPx > 0 && containerHeightPx > containerWidthPx) 90f else 0f
    }

    fun isStrictLandscapeOrientation(orientation: Int): Boolean {
        return orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ||
            orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun isManualPortraitOrLandscapeOrientation(orientation: Int): Boolean {
        return isStrictLandscapeOrientation(orientation) ||
            orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
            orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    /**
     * Manual rotation button toggle: switches explicitly between Landscape and Portrait.
     * - If currently in any Landscape orientation (or unspecified), switches to [ActivityInfo.SCREEN_ORIENTATION_PORTRAIT].
     * - If currently in Portrait, switches to [ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE].
     */
    fun computeNextManualRotateOrientation(currentOrientation: Int): Int {
        return if (
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        ) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    fun isHorizontalLandscapeTiltDegrees(sensorDegrees: Int): Boolean {
        if (sensorDegrees == OrientationEventListener.ORIENTATION_UNKNOWN || sensorDegrees !in 0..359) {
            return false
        }
        val normalized = ((sensorDegrees % 360) + 360) % 360
        return normalized in (90 - (45 - HYSTERESIS_DEGREES))..(90 + (45 - HYSTERESIS_DEGREES)) ||
            normalized in (270 - (45 - HYSTERESIS_DEGREES))..(270 + (45 - HYSTERESIS_DEGREES))
    }

    /**
     * Resolves player orientation where:
     * 1. Manual rotation toggles between Portrait ([ActivityInfo.SCREEN_ORIENTATION_PORTRAIT]) and Landscape ([ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE]).
     * 2. When in Portrait, tilting the device into a horizontal landscape angle (`60°..120°` or `240°..300°`)
     *    automatically tilts the player into Landscape ([ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE] or [ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE]).
     * 3. While in tilt / landscape mode, physical device tilt (`sensorDegrees`) ONLY rotates between Landscape orientations
     *    ([ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE] and [ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE])
     *    and NEVER auto-rotates into Portrait when tilted upright (`0°` or `180°`).
     */
    fun resolvePlayerOrientationWithTiltLandscapeOnly(
        manualOrientationOverride: Int?,
        sensorDegrees: Int,
        currentOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        portraitLockedAtSensorDegrees: Int = OrientationEventListener.ORIENTATION_UNKNOWN
    ): Int {
        if (manualOrientationOverride == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
            manualOrientationOverride == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        ) {
            if (sensorDegrees != OrientationEventListener.ORIENTATION_UNKNOWN && sensorDegrees in 0..359) {
                val normalized = ((sensorDegrees % 360) + 360) % 360
                val movedFromLockAngle = portraitLockedAtSensorDegrees == OrientationEventListener.ORIENTATION_UNKNOWN ||
                    angularDistance(normalized, portraitLockedAtSensorDegrees) >= 45
                if (movedFromLockAngle) {
                    if (normalized in (90 - (45 - HYSTERESIS_DEGREES))..(90 + (45 - HYSTERESIS_DEGREES))) {
                        return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                    if (normalized in (270 - (45 - HYSTERESIS_DEGREES))..(270 + (45 - HYSTERESIS_DEGREES))) {
                        return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
            }
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (manualOrientationOverride != null && isStrictLandscapeOrientation(manualOrientationOverride)) {
            if (sensorDegrees != OrientationEventListener.ORIENTATION_UNKNOWN && sensorDegrees in 0..359) {
                val normalized = ((sensorDegrees % 360) + 360) % 360
                if (normalized in (90 - (45 - HYSTERESIS_DEGREES))..(90 + (45 - HYSTERESIS_DEGREES))) {
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
                if (normalized in (270 - (45 - HYSTERESIS_DEGREES))..(270 + (45 - HYSTERESIS_DEGREES))) {
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
            return manualOrientationOverride
        }
        return resolveLandscapeLockedOrientation(
            sensorDegrees = sensorDegrees,
            currentOrientation = currentOrientation
        )
    }

    fun resolveStrictPlayerLandscapeOrientation(
        sensorDegrees: Int,
        currentOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    ): Int {
        return resolveLandscapeLockedOrientation(
            sensorDegrees = sensorDegrees,
            currentOrientation = currentOrientation
        )
    }

    fun angularDistance(angleA: Int, angleB: Int): Int {
        val diff = abs(((angleA % 360) + 360) % 360 - ((angleB % 360) + 360) % 360)
        return minOf(diff, 360 - diff)
    }
}
