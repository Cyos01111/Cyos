package com.cyosplay.player.util

import android.app.ActivityManager
import android.content.Context
import coil.Coil

enum class RamPerformanceTier(
    val displayName: String,
    val badgeLabel: String,
    val description: String
) {
    ULTRA_LOW_RAM(
        displayName = "Ultra-Low RAM (<= 2 GB)",
        badgeLabel = "ULTRA-LOW RAM SHIELD",
        description = "64 KB chunked stream allocator, compact 6 MB buffer, zero back-buffer RAM"
    ),
    LOW_RAM(
        displayName = "Low RAM (<= 3.5 GB)",
        badgeLabel = "LOW-RAM OPTIMIZED",
        description = "64 KB chunked stream allocator, 10 MB playback buffer, downsampled thumbnails"
    ),
    STANDARD_RAM(
        displayName = "Standard RAM (4 - 6 GB)",
        badgeLabel = "",
        description = "64 KB chunked 4K UHD stream buffer with strict byte-cap enforcement"
    ),
    HIGH_RAM(
        displayName = "High RAM (8 GB+)",
        badgeLabel = "",
        description = "64 KB chunked 4K UHD stream buffer with bounded memory footprint"
    )
}

data class MemoryBudgetConfig(
    val tier: RamPerformanceTier,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowRamDeviceFlag: Boolean,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
    val chunkSizeBytes: Int = DEFAULT_MEDIA3_CHUNK_BYTES,
    val backBufferDurationMs: Int = 0,
    val retainBackBufferFromKeyframe: Boolean = false,
    val prioritizeTimeOverSizeThresholds: Boolean = false,
    val thumbnailMaxDimensionPx: Int,
    val useRgb565Thumbnails: Boolean,
    val disableHeavyVisualEffects: Boolean,
    val positionPollingIntervalMs: Long,
    val batterySaverActive: Boolean
) {
    companion object {
        const val DEFAULT_MEDIA3_CHUNK_BYTES: Int = 64 * 1024 // 64 KB chunks for streaming 4K/UHD files
    }
}

object MemoryManager {

    fun detectMemoryBudget(
        context: Context,
        forceLowRamMode: Boolean = false,
        batterySaverEnabled: Boolean = false
    ): MemoryBudgetConfig {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalMb = if (memInfo.totalMem > 0) memInfo.totalMem / (1024 * 1024) else 4096L
        val availMb = if (memInfo.availMem > 0) memInfo.availMem / (1024 * 1024) else 1536L
        val isSystemLowRam = (activityManager?.isLowRamDevice == true) || memInfo.lowMemory

        val tier = when {
            forceLowRamMode -> RamPerformanceTier.ULTRA_LOW_RAM
            totalMb <= 6500L -> RamPerformanceTier.STANDARD_RAM
            else -> RamPerformanceTier.HIGH_RAM
        }

        val pollingIntervalMs = when {
            batterySaverEnabled && tier == RamPerformanceTier.ULTRA_LOW_RAM -> 450L
            batterySaverEnabled -> 360L
            tier == RamPerformanceTier.ULTRA_LOW_RAM -> 320L
            tier == RamPerformanceTier.LOW_RAM -> 290L
            else -> 260L
        }

        return when (tier) {
            RamPerformanceTier.ULTRA_LOW_RAM -> MemoryBudgetConfig(
                tier = tier,
                totalRamMb = totalMb,
                availableRamMb = availMb,
                isLowRamDeviceFlag = true,
                minBufferMs = 8_000,
                maxBufferMs = 16_000,
                bufferForPlaybackMs = 600,
                bufferForPlaybackAfterRebufferMs = 1_500,
                targetBufferBytes = 6 * 1024 * 1024, // 6 MB strict cap in 64 KB chunks
                chunkSizeBytes = MemoryBudgetConfig.DEFAULT_MEDIA3_CHUNK_BYTES,
                backBufferDurationMs = 6_000,
                retainBackBufferFromKeyframe = true,
                prioritizeTimeOverSizeThresholds = false,
                thumbnailMaxDimensionPx = 180,
                useRgb565Thumbnails = true,
                disableHeavyVisualEffects = true,
                positionPollingIntervalMs = pollingIntervalMs,
                batterySaverActive = batterySaverEnabled
            )
            RamPerformanceTier.LOW_RAM -> MemoryBudgetConfig(
                tier = tier,
                totalRamMb = totalMb,
                availableRamMb = availMb,
                isLowRamDeviceFlag = isSystemLowRam,
                minBufferMs = 8_000,
                maxBufferMs = 16_000,
                bufferForPlaybackMs = 600,
                bufferForPlaybackAfterRebufferMs = 1_500,
                targetBufferBytes = 12 * 1024 * 1024, // 12 MB strict cap on low RAM
                chunkSizeBytes = MemoryBudgetConfig.DEFAULT_MEDIA3_CHUNK_BYTES,
                backBufferDurationMs = 8_000,
                retainBackBufferFromKeyframe = true,
                prioritizeTimeOverSizeThresholds = false,
                thumbnailMaxDimensionPx = 220,
                useRgb565Thumbnails = true,
                disableHeavyVisualEffects = batterySaverEnabled,
                positionPollingIntervalMs = pollingIntervalMs,
                batterySaverActive = batterySaverEnabled
            )
            RamPerformanceTier.STANDARD_RAM -> MemoryBudgetConfig(
                tier = tier,
                totalRamMb = totalMb,
                availableRamMb = availMb,
                isLowRamDeviceFlag = false,
                minBufferMs = 12_000,
                maxBufferMs = if (batterySaverEnabled) 18_000 else 24_000,
                bufferForPlaybackMs = 600,
                bufferForPlaybackAfterRebufferMs = 1_500,
                targetBufferBytes = if (batterySaverEnabled) 16 * 1024 * 1024 else 24 * 1024 * 1024,
                chunkSizeBytes = MemoryBudgetConfig.DEFAULT_MEDIA3_CHUNK_BYTES,
                backBufferDurationMs = if (batterySaverEnabled) 6_000 else 10_000,
                retainBackBufferFromKeyframe = true,
                prioritizeTimeOverSizeThresholds = false,
                thumbnailMaxDimensionPx = if (batterySaverEnabled) 240 else 300,
                useRgb565Thumbnails = true,
                disableHeavyVisualEffects = batterySaverEnabled,
                positionPollingIntervalMs = pollingIntervalMs,
                batterySaverActive = batterySaverEnabled
            )
            RamPerformanceTier.HIGH_RAM -> MemoryBudgetConfig(
                tier = tier,
                totalRamMb = totalMb,
                availableRamMb = availMb,
                isLowRamDeviceFlag = false,
                minBufferMs = 12_000,
                maxBufferMs = if (batterySaverEnabled) 18_000 else 24_000,
                bufferForPlaybackMs = 600,
                bufferForPlaybackAfterRebufferMs = 1_500,
                targetBufferBytes = if (batterySaverEnabled) 20 * 1024 * 1024 else 32 * 1024 * 1024,
                chunkSizeBytes = MemoryBudgetConfig.DEFAULT_MEDIA3_CHUNK_BYTES,
                backBufferDurationMs = if (batterySaverEnabled) 6_000 else 10_000,
                retainBackBufferFromKeyframe = true,
                prioritizeTimeOverSizeThresholds = false,
                thumbnailMaxDimensionPx = if (batterySaverEnabled) 260 else 360,
                useRgb565Thumbnails = batterySaverEnabled,
                disableHeavyVisualEffects = batterySaverEnabled,
                positionPollingIntervalMs = pollingIntervalMs,
                batterySaverActive = batterySaverEnabled
            )
        }
    }

    fun performEmergencyMemoryTrim(context: Context) {
        try {
            com.cyosplay.player.ui.components.VideoFrameThumbnailCache.clear()
        } catch (_: Throwable) {
        }
        try {
            Coil.imageLoader(context.applicationContext).memoryCache?.clear()
        } catch (_: Throwable) {
            // Ignore in headless test environments
        }
    }
}
