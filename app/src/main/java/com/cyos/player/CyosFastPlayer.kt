package com.cyos.player

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
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
 * Direct, cache-free and preloader-free ExoPlayer factory tuned for near-instant first frame,
 * low CPU/RAM footprint, and minimal battery drain across Android 7–18 (1 GB–12 GB RAM).
 */
@OptIn(UnstableApi::class)
object CyosFastPlayer {

    // Low-latency startup buffer targets for instant first frame
    const val BUFFER_FOR_PLAYBACK_MS: Int = 180
    const val REMOTE_BUFFER_FOR_PLAYBACK_MS: Int = 320
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS: Int = 1_200
    const val REMOTE_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS: Int = 1_500

    // RAM-tiered back-buffer & max-buffer caps (safe for 1 GB–12 GB RAM)
    const val LOW_RAM_BACK_BUFFER_MS: Int = 6_000
    const val BACK_BUFFER_DURATION_MS: Int = 10_000
    const val HIGH_RAM_BACK_BUFFER_MS: Int = 15_000

    const val LOW_RAM_MIN_BUFFER_MS: Int = 6_000
    const val LOW_RAM_MAX_BUFFER_MS: Int = 14_000
    const val STANDARD_MIN_BUFFER_MS: Int = 10_000
    const val STANDARD_MAX_BUFFER_MS: Int = 22_000
    const val HIGH_RAM_MAX_BUFFER_MS: Int = 28_000

    const val LOW_RAM_TARGET_BUFFER_BYTES: Int = 8 * 1024 * 1024
    const val STANDARD_TARGET_BUFFER_BYTES: Int = 20 * 1024 * 1024
    const val HIGH_RAM_TARGET_BUFFER_BYTES: Int = 30 * 1024 * 1024

    // Cold-launch player pre-warming threshold (3 GB+ RAM only)
    const val MIN_WARMUP_TOTAL_RAM_MB: Long = 3_072L

    // Fast initial playable stream selection for HTTP/HTTPS ABR
    const val NETWORK_INITIAL_VIDEO_BITRATE: Int = 3_500_000
    const val INITIAL_BANDWIDTH_ESTIMATE_BPS: Long = 2_000_000L

    fun getTotalRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 4_096L
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return if (memInfo.totalMem > 0L) memInfo.totalMem / (1024L * 1024L) else 4_096L
    }

    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = if (memInfo.totalMem > 0L) memInfo.totalMem / (1024L * 1024L) else 4_096L
        return am.isLowRamDevice || memInfo.lowMemory || totalMb < 2_400L
    }

    fun isEligibleForColdLaunchWarmUp(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = if (memInfo.totalMem > 0L) memInfo.totalMem / (1024L * 1024L) else 4_096L
        return !am.isLowRamDevice && !memInfo.lowMemory && totalMb >= MIN_WARMUP_TOTAL_RAM_MB
    }

    fun isNetworkStreamUri(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https" || scheme == "rtsp" || scheme == "rtmp"
    }

    fun resolveBufferForPlaybackMs(isNetworkStream: Boolean): Int {
        return if (isNetworkStream) REMOTE_BUFFER_FOR_PLAYBACK_MS else BUFFER_FOR_PLAYBACK_MS
    }

    fun createLoadControl(
        context: Context,
        forceLowRamMode: Boolean = false,
        batterySaverEnabled: Boolean = false,
        isNetworkStream: Boolean = false
    ): DefaultLoadControl {
        val totalMb = getTotalRamMb(context)
        val isLowRam = forceLowRamMode || isLowRamDevice(context)
        val isHighRam = !isLowRam && !batterySaverEnabled && totalMb >= 7_500L

        val minBuf = if (isLowRam) LOW_RAM_MIN_BUFFER_MS else STANDARD_MIN_BUFFER_MS
        val maxBuf = when {
            isLowRam -> LOW_RAM_MAX_BUFFER_MS
            batterySaverEnabled -> 16_000
            isHighRam -> HIGH_RAM_MAX_BUFFER_MS
            else -> STANDARD_MAX_BUFFER_MS
        }
        val playbackBuf = resolveBufferForPlaybackMs(isNetworkStream)
        val rebufferBuf = if (isNetworkStream) {
            REMOTE_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        } else {
            BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        }
        val backBuf = when {
            isLowRam || batterySaverEnabled -> LOW_RAM_BACK_BUFFER_MS
            isHighRam -> HIGH_RAM_BACK_BUFFER_MS
            else -> BACK_BUFFER_DURATION_MS
        }
        val targetBytes = when {
            isLowRam -> LOW_RAM_TARGET_BUFFER_BYTES
            batterySaverEnabled -> 14 * 1024 * 1024
            isHighRam -> HIGH_RAM_TARGET_BUFFER_BYTES
            else -> STANDARD_TARGET_BUFFER_BYTES
        }
        return DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 64 * 1024))
            .setBufferDurationsMs(
                minBuf,
                maxBuf,
                playbackBuf,
                rebufferBuf
            )
            .setBackBuffer(backBuf, true)
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
    }

    fun createRenderersFactory(
        context: Context,
        customAudioProcessors: Array<AudioProcessor> = emptyArray(),
        mediaCodecSelector: MediaCodecSelector = NextRenderersFactory.createXvidAwareSelector(),
        extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
    ): DefaultRenderersFactory {
        val appContext = context.applicationContext ?: context
        val factory = NextRenderersFactory(appContext, customAudioProcessors)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            factory.forceEnableMediaCodecAsynchronousQueueing()
        }
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

    /**
     * Lifts the initial 3.5 Mbps fast-start bitrate cap once the first video frame renders so
     * adaptive HTTP/HTTPS/RTSP streams can ramp freely up to 1080p60 / 4K / 8K quality.
     */
    fun liftNetworkStartupBitrateCap(player: ExoPlayer) {
        val selector = player.trackSelector as? DefaultTrackSelector ?: return
        if (selector.parameters.maxVideoBitrate == Int.MAX_VALUE) return
        selector.parameters = selector.buildUponParameters()
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .build()
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

    fun setWakeModeForBackgroundAudio(player: ExoPlayer?, enabled: Boolean) {
        if (player == null) return
        runCatching {
            player.setWakeMode(if (enabled) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NONE)
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
        StartupLatencyTracker.markSetMediaItem()
        if (resumeMs > 0L) {
            player.setMediaItem(mediaItem, resumeMs)
        } else {
            player.setMediaItem(mediaItem)
        }
        player.playWhenReady = autoPlay
        StartupLatencyTracker.markPrepareCalled()
        player.prepare()
    }
}
