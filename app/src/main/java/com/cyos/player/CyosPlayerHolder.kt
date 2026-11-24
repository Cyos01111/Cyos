package com.cyos.player

import android.content.ComponentCallbacks2
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.cyosplay.player.util.Eac3DownmixAudioProcessor
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * Lightweight, cache-free and preloader-free singleton ExoPlayer session manager.
 * - Supports post-first-frame cold-launch warm-up strictly on 3 GB+ RAM devices.
 * - Starts playback immediately on tap across all RAM tiers with unified codec profile keys
 *   so VideoPlayerScreen reuses the exact warm ExoPlayer without recreating decoders or surfaces.
 * - Clears idle media items on detach and releases aggressively on background/memory trim.
 */
@OptIn(UnstableApi::class)
object CyosPlayerHolder {

    data class PooledPlayerSession(
        val player: ExoPlayer,
        val downmixAudioProcessor: Eac3DownmixAudioProcessor,
        val isPooled: Boolean
    )

    @Volatile
    private var pooledPlayer: ExoPlayer? = null

    @Volatile
    private var lastPreparedUri: Uri? = null

    @Volatile
    private var lastPreparedPositionMs: Long = 0L

    @Volatile
    private var lastCodecKey: String = ""

    @Volatile
    private var lastNetworkMode: Boolean = false

    val sharedDownmixAudioProcessor: Eac3DownmixAudioProcessor = Eac3DownmixAudioProcessor()

    /**
     * Pre-warms the singleton ExoPlayer asynchronously after the first Compose frame draws,
     * strictly gated to 3 GB+ RAM devices so 1 GB–2 GB devices stay zero-overhead until tap.
     */
    @Synchronized
    fun warmUpAfterFirstFrame(
        context: Context,
        mediaCodecSelector: MediaCodecSelector = NextRenderersFactory.createXvidAwareSelector(),
        extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
        codecProfileKey: String = "AUTO_HW_PLUS:false:false:false",
        batterySaverEnabled: Boolean = false
    ): Boolean {
        val appContext = context.applicationContext ?: context
        if (pooledPlayer != null) return true
        if (!CyosFastPlayer.isEligibleForColdLaunchWarmUp(appContext)) {
            return false
        }
        return runCatching {
            getOrCreateSession(
                context = appContext,
                mediaCodecSelector = mediaCodecSelector,
                extensionRendererMode = extensionRendererMode,
                codecProfileKey = codecProfileKey,
                subtitlesEnabledByUser = false,
                forceLowRamMode = false,
                batterySaverEnabled = batterySaverEnabled,
                isNetworkStream = false
            )
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun getOrCreateSession(
        context: Context,
        mediaCodecSelector: MediaCodecSelector = NextRenderersFactory.createXvidAwareSelector(),
        extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
        codecProfileKey: String = "DEFAULT:$extensionRendererMode",
        subtitlesEnabledByUser: Boolean = false,
        forceLowRamMode: Boolean = false,
        batterySaverEnabled: Boolean = false,
        isNetworkStream: Boolean = false
    ): PooledPlayerSession {
        val appContext = context.applicationContext ?: context
        val existingPlayer = pooledPlayer
        if (existingPlayer != null &&
            lastCodecKey == codecProfileKey &&
            lastNetworkMode == isNetworkStream
        ) {
            return PooledPlayerSession(
                player = existingPlayer,
                downmixAudioProcessor = sharedDownmixAudioProcessor,
                isPooled = true
            )
        }

        releaseInternal()

        val loadControl = CyosFastPlayer.createLoadControl(
            context = appContext,
            forceLowRamMode = forceLowRamMode,
            batterySaverEnabled = batterySaverEnabled,
            isNetworkStream = isNetworkStream
        )
        val renderersFactory = CyosFastPlayer.createRenderersFactory(
            context = appContext,
            customAudioProcessors = arrayOf(sharedDownmixAudioProcessor),
            mediaCodecSelector = mediaCodecSelector,
            extensionRendererMode = extensionRendererMode
        )

        val newPlayer = CyosFastPlayer.createDirectExoPlayer(
            context = appContext,
            loadControl = loadControl,
            renderersFactory = renderersFactory,
            capNetworkStartupBitrate = isNetworkStream,
            subtitlesEnabledByUser = subtitlesEnabledByUser
        )

        pooledPlayer = newPlayer
        lastCodecKey = codecProfileKey
        lastNetworkMode = isNetworkStream

        return PooledPlayerSession(
            player = newPlayer,
            downmixAudioProcessor = sharedDownmixAudioProcessor,
            isPooled = true
        )
    }

    @Synchronized
    fun prepareOnTap(
        context: Context,
        mediaItem: MediaItem,
        resumeMs: Long = 0L,
        codecProfileKey: String = "DEFAULT:${DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON}",
        mediaCodecSelector: MediaCodecSelector = NextRenderersFactory.createXvidAwareSelector(),
        extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
        forceLowRamMode: Boolean = false,
        batterySaverEnabled: Boolean = false
    ): Boolean {
        val appContext = context.applicationContext ?: context
        val itemUri = mediaItem.localConfiguration?.uri
        val isNetwork = CyosFastPlayer.isNetworkStreamUri(itemUri)
        return runCatching {
            val session = getOrCreateSession(
                context = appContext,
                mediaCodecSelector = mediaCodecSelector,
                extensionRendererMode = extensionRendererMode,
                codecProfileKey = codecProfileKey,
                subtitlesEnabledByUser = false,
                forceLowRamMode = forceLowRamMode,
                batterySaverEnabled = batterySaverEnabled,
                isNetworkStream = isNetwork
            )
            StartupLatencyTracker.markPlayerAcquired()

            CyosFastPlayer.playOnTap(
                player = session.player,
                mediaItem = mediaItem,
                resumeMs = resumeMs,
                autoPlay = true
            )
            lastPreparedUri = itemUri
            lastPreparedPositionMs = resumeMs
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun consumeMatchingPrePreparedUri(uri: Uri?): Boolean {
        if (uri == null || lastPreparedUri == null) return false
        val matched = uri == lastPreparedUri
        if (matched) {
            lastPreparedUri = null
        }
        return matched
    }

    @Synchronized
    fun pauseAndDetachIfPooled() {
        val p = pooledPlayer ?: return
        runCatching {
            p.playWhenReady = false
            p.pause()
            p.stop()
            p.clearMediaItems()
        }
        lastPreparedUri = null
    }

    @Suppress("DEPRECATION")
    @Synchronized
    fun trimOrReleaseOnMemoryPressure(context: Context, trimLevel: Int) {
        if (CyosFastPlayer.isLowRamDevice(context) ||
            trimLevel >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            trimLevel >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            trimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            release()
        }
    }

    @Synchronized
    fun currentPlayerOrNull(): ExoPlayer? = pooledPlayer

    @Synchronized
    fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        lastPreparedUri = null
        lastPreparedPositionMs = 0L
        lastCodecKey = ""
        lastNetworkMode = false
        try {
            pooledPlayer?.release()
        } catch (_: Throwable) {
        }
        pooledPlayer = null
    }
}
