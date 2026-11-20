package com.cyosplay.player.util

import android.Manifest
import android.os.Build

/**
 * Centralizes runtime compatibility across Android 7.0 Nougat (API 24) through Android 18 (API 38+),
 * covering tiered media/storage permissions, Picture-in-Picture (PiP) capabilities and safe aspect-ratio
 * clamping, and GPU RenderEffect / hardware-decoder guards.
 */
object AndroidVersionCompatHelper {

    const val MIN_SUPPORTED_ANDROID_VERSION: Int = 7
    const val MAX_SUPPORTED_ANDROID_VERSION: Int = 18
    const val MIN_SUPPORTED_API_LEVEL: Int = Build.VERSION_CODES.N // API 24 (Android 7.0)
    const val ANDROID_16_API_LEVEL: Int = 36
    const val ANDROID_17_API_LEVEL: Int = 37
    const val ANDROID_18_API_LEVEL: Int = 38

    fun isSupportedAndroidSdk(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return sdkInt >= MIN_SUPPORTED_API_LEVEL
    }

    /**
     * Resolves the exact runtime media/storage permission array for any Android OS release
     * from Android 7.0 (API 24) through Android 18 (API 38+):
     * - Android 7.0–9.0 (API 24–28): READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE
     * - Android 10–12L (API 29–32): READ_EXTERNAL_STORAGE (Scoped Storage)
     * - Android 13 (API 33): READ_MEDIA_VIDEO + READ_MEDIA_AUDIO
     * - Android 14–18+ (API 34–38+): READ_MEDIA_VIDEO + READ_MEDIA_AUDIO + READ_MEDIA_VISUAL_USER_SELECTED
     */
    fun resolveRuntimeMediaPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        return when {
            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            sdkInt <= Build.VERSION_CODES.P -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun canEnterPictureInPicture(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return sdkInt >= Build.VERSION_CODES.O
    }

    fun supportsSeamlessAutoPip(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return sdkInt >= Build.VERSION_CODES.S
    }

    fun supportsHardwareRenderEffect(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return sdkInt >= Build.VERSION_CODES.S
    }

    /**
     * Computes a safe (numerator, denominator) pair clamped within Android's mandatory PiP window
     * bounds (0.42f..2.39f) so ultrawide or vertical videos never throw IllegalArgumentException
     * on Android 8.0 through Android 17.
     */
    fun clampPipAspectRatioRational(videoWidth: Int, videoHeight: Int): Pair<Int, Int> {
        val safeW = videoWidth.coerceAtLeast(16)
        val safeH = videoHeight.coerceAtLeast(9)
        val ratioFloat = (safeW.toFloat() / safeH.toFloat()).coerceIn(0.42f, 2.39f)
        val numerator = (ratioFloat * 1000f).toInt().coerceIn(420, 2390)
        return numerator to 1000
    }
}
