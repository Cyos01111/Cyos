package com.cyos.player

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
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
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.cyosplay.player.XvidSupportExtractorsFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

@OptIn(UnstableApi::class)
object CyosFastPlayer {

    const val BUFFER_FOR_PLAYBACK_MS: Int = 600
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS: Int = 1_500
    const val BACK_BUFFER_DURATION_MS: Int = 15_000
    const val LOW_RAM_MIN_BUFFER_MS: Int = 10_000
    const val LOW_RAM_MAX_BUFFER_MS: Int = 20_000
    const val STANDARD_MIN_BUFFER_MS: Int = 15_000
    const val STANDARD_MAX_BUFFER_MS: Int = 35_000
    const val LOW_RAM_TARGET_BUFFER_BYTES: Int = 16 * 1024 * 1024
    const val NETWORK_INITIAL_VIDEO_BITRATE: Int = 4_000_000
    const val INITIAL_BANDWIDTH_ESTIMATE_BPS: Long = 1_500_000L

    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return am.isLowRamDevice || memInfo.lowMemory
    }

    fun isNetworkStreamUri(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https" || scheme == "rtsp" || scheme == "rtmp"
    }

    fun createLoadControl(
        context: Context,
        forceLowRamMode: Boolean = false
    ): DefaultLoadControl {
        val isLowRam = forceLowRamMode || isLowRamDevice(context)
        val builder = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 64 * 1024))
            .setBufferDurationsMs(
                if (isLowRam) LOW_RAM_MIN_BUFFER_MS else STANDARD_MIN_BUFFER_MS,
                if (isLowRam) LOW_RAM_MAX_BUFFER_MS else STANDARD_MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setBackBuffer(BACK_BUFFER_DURATION_MS, true)
            .setPrioritizeTimeOverSizeThresholds(true)
        if (isLowRam) {
            builder.setTargetBufferBytes(LOW_RAM_TARGET_BUFFER_BYTES)
        } else {
            builder.setTargetBufferBytes(C.LENGTH_UNSET)
        }
        return builder.build()
    }

    fun createRenderersFactory(
        context: Context,
        customAudioProcessors: Array<AudioProcessor> = emptyArray(),
        mediaCodecSelector: MediaCodecSelector = MediaCodecSelector.DEFAULT,
        extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
    ): DefaultRenderersFactory {
        val appContext = context.applicationContext ?: context
        val factory = NextRenderersFactory(appContext, customAudioProcessors)
        factory.forceEnableMediaCodecAsynchronousQueueing()
        factory.setEnableDecoderFallback(true)
        factory.setMediaCodecSelector(mediaCodecSelector)
        factory.setExtensionRendererMode(extensionRendererMode)
        return factory
    }

    fun createTrackSelector(
        context: Context,
        capNetworkStartupBitrate: Boolean = false,
        subtitlesEnabledByUser: Boolean = false
    ): DefaultTrackSelector {
        val appContext = context.applicationContext ?: context
        return DefaultTrackSelector(appContext).apply {
            val paramsBuilder = buildUponParameters()
                .setMaxVideoSize(15360, 8640)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setExceedAudioConstraintsIfNecessary(true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabledByUser)
            if (capNetworkStartupBitrate) {
                // Avoid stalling on a 4K/1080p first segment on slow network connections;
                // ABR ramps up automatically once playback is rolling.
                paramsBuilder.setMaxVideoBitrate(NETWORK_INITIAL_VIDEO_BITRATE)
            }
            parameters = paramsBuilder.build()
        }
    }

    fun applyBitratePolicyForUri(player: ExoPlayer, uri: Uri?) {
        val selector = player.trackSelector as? DefaultTrackSelector ?: return
        val isNetwork = isNetworkStreamUri(uri)
        val paramsBuilder = selector.buildUponParameters()
            .setMaxVideoSize(15360, 8640)
            .setExceedVideoConstraintsIfNecessary(true)
            .setExceedRendererCapabilitiesIfNecessary(true)
            .setExceedAudioConstraintsIfNecessary(true)
        if (isNetwork) {
            paramsBuilder.setMaxVideoBitrate(NETWORK_INITIAL_VIDEO_BITRATE)
        } else {
            paramsBuilder.setMaxVideoBitrate(Int.MAX_VALUE)
        }
        selector.parameters = paramsBuilder.build()
    }

    fun createPreloadBuilder(
        context: Context,
        statusControl: TargetPreloadStatusControl<Int>,
        smartDataSourceFactory: DataSource.Factory = CyosSmartDataSourceFactory(context),
        loadControl: LoadControl = createLoadControl(context),
        renderersFactory: DefaultRenderersFactory = createRenderersFactory(context),
        capNetworkStartupBitrate: Boolean = false,
        subtitlesEnabledByUser: Boolean = false
    ): DefaultPreloadManager.Builder {
        val appContext = context.applicationContext ?: context
        val bandwidthMeter = DefaultBandwidthMeter.Builder(appContext)
            .setInitialBitrateEstimate(INITIAL_BANDWIDTH_ESTIMATE_BPS)
            .build()

        return DefaultPreloadManager.Builder(appContext, statusControl)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelectorFactory(
                TrackSelector.Factory { ctx ->
                    createTrackSelector(
                        context = ctx,
                        capNetworkStartupBitrate = capNetworkStartupBitrate,
                        subtitlesEnabledByUser = subtitlesEnabledByUser
                    )
                }
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    smartDataSourceFactory,
                    XvidSupportExtractorsFactory()
                )
            )
    }

    fun createWarmExoPlayerBuilder(context: Context): ExoPlayer.Builder {
        val appContext = context.applicationContext ?: context
        return ExoPlayer.Builder(appContext)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setWakeMode(C.WAKE_MODE_NONE)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
    }

    fun playOnTap(
        player: ExoPlayer,
        preloadManager: DefaultPreloadManager,
        mediaItem: MediaItem,
        resumeMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        val itemUri = mediaItem.localConfiguration?.uri
        applyBitratePolicyForUri(player, itemUri)
        val preloadedSource = runCatching { preloadManager.getMediaSource(mediaItem) }.getOrNull()
        if (preloadedSource != null) {
            if (resumeMs > 0L) {
                player.setMediaSource(preloadedSource, resumeMs)
            } else {
                player.setMediaSource(preloadedSource)
            }
        } else {
            if (resumeMs > 0L) {
                player.setMediaItem(mediaItem, resumeMs)
            } else {
                player.setMediaItem(mediaItem)
            }
        }
        player.playWhenReady = autoPlay
        player.prepare()
    }
}
