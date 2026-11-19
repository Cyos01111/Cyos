package com.cyos.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.cyosplay.player.XvidSupportExtractorsFactory

@OptIn(UnstableApi::class)
class CyosFastPlayerManager(
    context: Context,
    customAudioProcessors: Array<AudioProcessor> = emptyArray(),
    mediaCodecSelector: MediaCodecSelector = MediaCodecSelector.DEFAULT,
    extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
    loadControlOverride: LoadControl? = null,
    subtitlesEnabledByUser: Boolean = false
) {
    private val appContext: Context = context.applicationContext ?: context
    private var currentItems: List<MediaItem> = emptyList()

    val preloadStatusControl = CyosPreloadStatusControl(
        currentPlayingIndex = 0,
        isLowRamDevice = CyosFastPlayer.isLowRamDevice(appContext)
    )

    val mediaSourceFactory: DefaultMediaSourceFactory = DefaultMediaSourceFactory(
        CyosMediaCache.buildCacheDataSourceFactory(appContext),
        XvidSupportExtractorsFactory()
    )

    val renderersFactory: DefaultRenderersFactory = CyosFastPlayer.createRenderersFactory(
        context = appContext,
        customAudioProcessors = customAudioProcessors,
        mediaCodecSelector = mediaCodecSelector,
        extensionRendererMode = extensionRendererMode
    )

    val loadControl: LoadControl = loadControlOverride ?: CyosFastPlayer.createLoadControl(appContext)

    val preloadManager: DefaultPreloadManager
    val player: ExoPlayer

    init {
        val preloadBuilder = CyosFastPlayer.createPreloadBuilder(
            context = appContext,
            statusControl = preloadStatusControl,
            loadControl = loadControl,
            renderersFactory = renderersFactory,
            capNetworkStartupBitrate = false,
            subtitlesEnabledByUser = subtitlesEnabledByUser
        )

        preloadManager = preloadBuilder.build()

        player = preloadBuilder.buildExoPlayer(
            CyosFastPlayer.createWarmExoPlayerBuilder(appContext)
        ).apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
        }
    }

    fun submitPlaylist(items: List<MediaItem>) {
        currentItems = items
        preloadManager.reset()
        items.forEachIndexed { index, mediaItem ->
            preloadManager.add(mediaItem, index)
        }
        preloadManager.invalidate()
    }

    fun playAtIndex(
        index: Int,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        if (index !in currentItems.indices) return
        preloadStatusControl.currentPlayingIndex = index
        preloadManager.setCurrentPlayingIndex(index)
        preloadManager.invalidate()

        CyosFastPlayer.playOnTap(
            player = player,
            preloadManager = preloadManager,
            mediaItem = currentItems[index],
            resumeMs = startPositionMs,
            autoPlay = autoPlay
        )
    }

    fun updateCurrentPlayingIndex(index: Int) {
        if (index !in currentItems.indices) return
        preloadStatusControl.currentPlayingIndex = index
        preloadManager.setCurrentPlayingIndex(index)
        preloadManager.invalidate()
    }

    fun getCurrentPlaylistSize(): Int = currentItems.size

    fun getCurrentPlayingIndex(): Int = preloadStatusControl.currentPlayingIndex

    fun skipForward10s(): Long = skipForward10s(player)

    fun skipBackward10s(): Long = skipBackward10s(player)

    fun release() {
        try {
            preloadManager.release()
        } catch (_: Throwable) {
        }
        try {
            player.release()
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val PRELOAD_NEXT_DURATION_MS: Long = CyosPreloadStatusControl.PRELOAD_NEXT_DURATION_MS
        const val MIN_BUFFER_MS: Int = CyosFastPlayer.STANDARD_MIN_BUFFER_MS
        const val MAX_BUFFER_MS: Int = CyosFastPlayer.STANDARD_MAX_BUFFER_MS
        const val BUFFER_FOR_PLAYBACK_MS: Int = CyosFastPlayer.BUFFER_FOR_PLAYBACK_MS
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS: Int = CyosFastPlayer.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        const val BACK_BUFFER_DURATION_MS: Int = CyosFastPlayer.BACK_BUFFER_DURATION_MS
        const val SKIP_INTERVAL_MS: Long = 10_000L

        fun buildOptimizedLoadControl(
            chunkSizeBytes: Int = 64 * 1024,
            targetBufferBytes: Int = C.LENGTH_UNSET,
            minBufferMs: Int = MIN_BUFFER_MS,
            maxBufferMs: Int = MAX_BUFFER_MS,
            bufferForPlaybackMs: Int = BUFFER_FOR_PLAYBACK_MS,
            bufferForPlaybackAfterRebufferMs: Int = BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            backBufferDurationMs: Int = BACK_BUFFER_DURATION_MS,
            retainBackBufferFromKeyframe: Boolean = true,
            prioritizeTimeOverSizeThresholds: Boolean = true
        ): DefaultLoadControl {
            val builder = DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, chunkSizeBytes))
                .setBufferDurationsMs(
                    minBufferMs,
                    maxBufferMs,
                    bufferForPlaybackMs,
                    bufferForPlaybackAfterRebufferMs
                )
                .setBackBuffer(backBufferDurationMs, retainBackBufferFromKeyframe)
                .setPrioritizeTimeOverSizeThresholds(prioritizeTimeOverSizeThresholds)
            if (targetBufferBytes > 0) {
                builder.setTargetBufferBytes(targetBufferBytes)
            }
            return builder.build()
        }

        fun skipForward10s(player: ExoPlayer): Long {
            val duration = player.duration
            val current = player.currentPosition.coerceAtLeast(0L)
            val rawTarget = current + SKIP_INTERVAL_MS
            val target = if (duration > 0L) rawTarget.coerceAtMost(duration) else rawTarget
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            player.seekTo(target)
            return target
        }

        fun skipBackward10s(player: ExoPlayer): Long {
            val current = player.currentPosition.coerceAtLeast(0L)
            val target = (current - SKIP_INTERVAL_MS).coerceAtLeast(0L)
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            player.seekTo(target)
            return target
        }

        fun seekWithClosestSync(player: ExoPlayer, positionMs: Long): Long {
            val duration = player.duration
            val target = if (duration > 0L) {
                positionMs.coerceIn(0L, duration)
            } else {
                positionMs.coerceAtLeast(0L)
            }
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            player.seekTo(target)
            return target
        }
    }
}
