package com.cyos.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
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
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.cyosplay.player.XvidSupportExtractorsFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlin.math.abs

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
    private var currentPlayingIndex: Int = 0
    private var currentItems: List<MediaItem> = emptyList()

    private val preloadStatusControl = TargetPreloadStatusControl<Int> { rankingData ->
        val distance = abs(rankingData - currentPlayingIndex)
        when (distance) {
            1 -> DefaultPreloadManager.Status(
                DefaultPreloadManager.Status.STAGE_LOADED_FOR_DURATION_MS,
                PRELOAD_NEXT_DURATION_MS
            )
            2 -> DefaultPreloadManager.Status(
                DefaultPreloadManager.Status.STAGE_TRACKS_SELECTED
            )
            else -> null
        }
    }

    val mediaSourceFactory: DefaultMediaSourceFactory = DefaultMediaSourceFactory(
        CyosMediaCache.buildCacheDataSourceFactory(appContext),
        XvidSupportExtractorsFactory()
    )

    val renderersFactory: DefaultRenderersFactory = NextRenderersFactory(
        appContext,
        customAudioProcessors
    )
        .setMediaCodecSelector(mediaCodecSelector)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(extensionRendererMode)

    val loadControl: LoadControl = loadControlOverride ?: buildOptimizedLoadControl()

    val preloadManager: DefaultPreloadManager
    val player: ExoPlayer

    init {
        val preloadBuilder = DefaultPreloadManager.Builder(appContext, preloadStatusControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelectorFactory(
                TrackSelector.Factory { ctx ->
                    DefaultTrackSelector(ctx).apply {
                        setParameters(
                            buildUponParameters()
                                .setMaxVideoSize(15360, 8640)
                                .setExceedVideoConstraintsIfNecessary(true)
                                .setExceedRendererCapabilitiesIfNecessary(true)
                                .setExceedAudioConstraintsIfNecessary(true)
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabledByUser)
                        )
                    }
                }
            )

        preloadManager = preloadBuilder.build()

        val exoBuilder = ExoPlayer.Builder(appContext)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)

        player = preloadBuilder.buildExoPlayer(exoBuilder).apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
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
        currentPlayingIndex = index
        preloadManager.setCurrentPlayingIndex(index)
        preloadManager.invalidate()

        val item = currentItems[index]
        val preloadedSource = preloadManager.getMediaSource(item)
        if (preloadedSource != null) {
            if (startPositionMs > 0L) {
                player.setMediaSource(preloadedSource, startPositionMs)
            } else {
                player.setMediaSource(preloadedSource)
            }
        } else {
            if (startPositionMs > 0L) {
                player.setMediaItem(item, startPositionMs)
            } else {
                player.setMediaItem(item)
            }
        }
        player.prepare()
        player.playWhenReady = autoPlay
        if (autoPlay) {
            player.play()
        }
    }

    fun updateCurrentPlayingIndex(index: Int) {
        if (index !in currentItems.indices) return
        currentPlayingIndex = index
        preloadManager.setCurrentPlayingIndex(index)
        preloadManager.invalidate()
    }

    fun getCurrentPlaylistSize(): Int = currentItems.size

    fun getCurrentPlayingIndex(): Int = currentPlayingIndex

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
        const val PRELOAD_NEXT_DURATION_MS: Long = 3_000L
        const val MIN_BUFFER_MS: Int = 5_000
        const val MAX_BUFFER_MS: Int = 15_000
        const val BUFFER_FOR_PLAYBACK_MS: Int = 350
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS: Int = 1_000
        const val BACK_BUFFER_DURATION_MS: Int = 15_000
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
