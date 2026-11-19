package com.cyos.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import kotlin.math.abs

@OptIn(UnstableApi::class)
class CyosPreloadStatusControl(
    var currentPlayingIndex: Int = 0,
    var isLowRamDevice: Boolean = false
) : TargetPreloadStatusControl<Int> {

    override fun getTargetPreloadStatus(rankingData: Int): DefaultPreloadManager.Status? {
        val distance = rankingData - currentPlayingIndex
        return when {
            distance == 1 -> specifiedRangeLoaded(
                if (isLowRamDevice) LOW_RAM_PRELOAD_NEXT_DURATION_MS else PRELOAD_NEXT_DURATION_MS
            )
            distance == -1 -> specifiedRangeLoaded(PRELOAD_PREV_DURATION_MS)
            abs(distance) <= 2 -> TRACKS_SELECTED
            !isLowRamDevice && abs(distance) <= 4 -> SOURCE_PREPARED
            else -> null
        }
    }

    companion object {
        const val PRELOAD_NEXT_DURATION_MS: Long = 2_000L
        const val LOW_RAM_PRELOAD_NEXT_DURATION_MS: Long = 1_200L
        const val PRELOAD_PREV_DURATION_MS: Long = 1_000L

        val TRACKS_SELECTED: DefaultPreloadManager.Status = DefaultPreloadManager.Status(
            DefaultPreloadManager.Status.STAGE_TRACKS_SELECTED
        )

        val SOURCE_PREPARED: DefaultPreloadManager.Status = DefaultPreloadManager.Status(
            DefaultPreloadManager.Status.STAGE_SOURCE_PREPARED
        )

        fun specifiedRangeLoaded(durationMs: Long): DefaultPreloadManager.Status {
            return DefaultPreloadManager.Status(
                DefaultPreloadManager.Status.STAGE_LOADED_FOR_DURATION_MS,
                durationMs.coerceAtLeast(1_000L)
            )
        }
    }
}
