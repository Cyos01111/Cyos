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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.cyosplay.player.XvidSupportExtractorsFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * Direct, cache-free and preloader-free ExoPlayer factory tuned for stable CPU/RAM usage
 * and minimal battery drain across Android 7–18.
 */
@OptIn(UnstableApi::class)
object CyosFastPlayer {

    const val BUFFER_FOR_PLAYBACK_MS: Int = 600
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS: Int = 1_500
    const val BACK_BUFFER_DURATION_MS: Int = 10_000
    const val LOW_RAM_MIN_BUFFER_MS: Int = 8_000
    const val LOW_RAM_MAX_BUFFER_MS: Int = 16_000
    const val STANDARD_MIN_BUFFER_MS: Int = 12_000
    const val STANDARD_MAX_BUFFER_MS: Int = 24_000
    const val LOW_RAM_TARGET_BUFFER_BYTES: Int = 10 * 1024 * 1024
    const val STANDARD_TARGET_BUFFER_BYTES: Int = 24 * 1024 * 1024
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
        forceLowRamMode: Boolean = false,
        batterySaverEnabled: Boolean = false
    ): DefaultLoadControl {
        val isLowRam = forceLowRamMode || isLowRamDevice(context)
        val minBuf = if (isLowRam) LOW_RAM_MIN_BUFFER_MS else STANDARD_MIN_BUFFER_MS
        val maxBuf = when {
            isLowRam -> LOW_RAM_MAX_BUFFER_MS
            batterySaverEnabled -> 18_000
            else -> STANDARD_MAX_BUFFER_MS
        }
        val targetBytes = when {
            isLowRam -> LOW_RAM_TARGET_BUFFER_BYTES
            batterySaverEnabled -> 16 * 1024 * 1024
            else -> STANDARD_TARGET_BUFFER_BYTES
        }
        return DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 64 * 1024))
            .setBufferDurationsMs(
                minBuf,
                maxBuf,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setBackBuffer(if (isLowRam || batterySaverEnabled) 6_000 else BACK_BUFFER_DURATION_MS, true)
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
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

    fun createDirectMediaSourceFactory(
        context: Context,
        dataSourceFactory: DataSource.Factory = CyosSmartDataSourceFactory(context)
    ): DefaultMediaSourceFactory {
        return DefaultMediaSourceFactory(
            dataSourceFactory,
            XvidSupportExtractorsFactory()
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

    fun createDirectExoPlayer(
        context: Context,
        loadControl: LoadControl = createLoadControl(context),
        renderersFactory: DefaultRenderersFactory = createRenderersFactory(context),
        dataSourceFactory: DataSource.Factory = CyosSmartDataSourceFactory(context),
        capNetworkStartupBitrate: Boolean = false,
        subtitlesEnabledByUser: Boolean = false
    ): ExoPlayer {
        val appContext = context.applicationContext ?: context
        val bandwidthMeter = DefaultBandwidthMeter.Builder(appContext)
            .setInitialBitrateEstimate(INITIAL_BANDWIDTH_ESTIMATE_BPS)
            .build()
        val trackSelector = createTrackSelector(
            context = appContext,
            capNetworkStartupBitrate = capNetworkStartupBitrate,
            subtitlesEnabledByUser = subtitlesEnabledByUser
        )
        return createWarmExoPlayerBuilder(appContext)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(createDirectMediaSourceFactory(appContext, dataSourceFactory))
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
    }

    fun playOnTap(
        player: ExoPlayer,
        mediaItem: MediaItem,
        resumeMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        val itemUri = mediaItem.localConfiguration?.uri
        applyBitratePolicyForUri(player, itemUri)
        if (resumeMs > 0L) {
            player.setMediaItem(mediaItem, resumeMs)
        } else {
            player.setMediaItem(mediaItem)
        }
        player.playWhenReady = autoPlay
        player.prepare()
    }
}
