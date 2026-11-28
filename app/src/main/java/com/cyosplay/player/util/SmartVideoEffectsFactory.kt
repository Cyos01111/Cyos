package com.cyosplay.player.util

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import com.cyosplay.player.data.model.SmartEnhancementMode

/**
 * Builds real GPU-accelerated Media3 OpenGL [Effect] pipelines for [SmartEnhancementMode]
 * applied directly via `ExoPlayer.setVideoEffects(...)`.
 *
 * Works natively on hardware `SurfaceView` across Android 7–16 (API 24–36) in a single-pass
 * 4×4 column-major RGBA shader matrix multiply combining contrast, Rec.709 saturation, and
 * normalized brightness offset. Returns an empty list for [SmartEnhancementMode.OFF] so
 * standard playback incurs zero GPU post-processing overhead.
 */
@OptIn(UnstableApi::class)
object SmartVideoEffectsFactory {

    fun createEffectsForMode(
        mode: SmartEnhancementMode,
        clarityBoost: Float = 0f,
        contrastDelta: Float = 0f,
        saturationDelta: Float = 0f
    ): List<Effect> {
        val safeClarity = clarityBoost.coerceIn(0f, 1f)
        val safeContrastDelta = contrastDelta.coerceIn(-0.4f, 0.4f)
        val safeSatDelta = saturationDelta.coerceIn(-0.5f, 0.5f)
        val hasCustomAdjustment = safeClarity > 0.005f ||
            kotlin.math.abs(safeContrastDelta) > 0.005f ||
            kotlin.math.abs(safeSatDelta) > 0.005f
        if (mode == SmartEnhancementMode.OFF && !hasCustomAdjustment) return emptyList()
        return listOf(
            SmartEnhancementRgbMatrix(
                mode = mode,
                clarityBoost = safeClarity,
                contrastDelta = safeContrastDelta,
                saturationDelta = safeSatDelta
            )
        )
    }

    /**
     * Computes a 4×4 column-major OpenGL RGBA transformation matrix for [mode] combined with
     * optional fine-grained [clarityBoost], [contrastDelta], and [saturationDelta] controls.
     */
    fun buildColorMatrix4x4(
        mode: SmartEnhancementMode,
        clarityBoost: Float = 0f,
        contrastDelta: Float = 0f,
        saturationDelta: Float = 0f
    ): FloatArray {
        val safeClarity = clarityBoost.coerceIn(0f, 1f)
        // Clarity boost introduces crisp micro-contrast punch and subtle vibrance lift without clipping highlights
        val c = (mode.contrast + contrastDelta + (safeClarity * 0.24f)).coerceIn(0.60f, 1.85f)
        val s = (mode.saturation + saturationDelta + (safeClarity * 0.14f)).coerceIn(0.40f, 1.85f)
        val b = (mode.brightnessOffset + (safeClarity * 4.5f)) / 255f

        // Rec.709 luma weights for saturation matrix
        val lumR = 0.2126f
        val lumG = 0.7152f
        val lumB = 0.0722f
        val invS = 1f - s

        val sr = invS * lumR
        val sg = invS * lumG
        val sb = invS * lumB

        // Combine contrast scale around 0.5 mid-gray + brightness offset
        val t = (0.5f * (1f - c)) + b

        // Row-major logical terms:
        // R' = c * ((sr + s)*R + sg*G + sb*B) + t
        // G' = c * (sr*R + (sg + s)*G + sb*B) + t
        // B' = c * (sr*R + sg*G + (sb + s)*B) + t
        // Stored in OpenGL column-major order (4 columns x 4 rows):
        return floatArrayOf(
            // Column 0 (R input)
            c * (sr + s), c * sr, c * sr, 0f,
            // Column 1 (G input)
            c * sg, c * (sg + s), c * sg, 0f,
            // Column 2 (B input)
            c * sb, c * sb, c * (sb + s), 0f,
            // Column 3 (Translation)
            t, t, t, 1f
        )
    }

    class SmartEnhancementRgbMatrix(
        val mode: SmartEnhancementMode,
        val clarityBoost: Float = 0f,
        val contrastDelta: Float = 0f,
        val saturationDelta: Float = 0f
    ) : RgbMatrix {
        private val matrix: FloatArray = buildColorMatrix4x4(
            mode = mode,
            clarityBoost = clarityBoost,
            contrastDelta = contrastDelta,
            saturationDelta = saturationDelta
        )

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix

        override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
            return mode == SmartEnhancementMode.OFF &&
                clarityBoost <= 0.005f &&
                kotlin.math.abs(contrastDelta) <= 0.005f &&
                kotlin.math.abs(saturationDelta) <= 0.005f
        }
    }
}
