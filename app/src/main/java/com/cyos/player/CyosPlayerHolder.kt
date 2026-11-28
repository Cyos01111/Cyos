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

    private val attachedClients = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var pipActive: Boolean = false

    @Volatile
    private var backgroundAudioActive: Boolean = false

    @Volatile
    private var activeDownmixAudioProcessor: Eac3DownmixAudioProcessor = Eac3DownmixAudioProcessor()

    val sharedDownmixAudioProcessor: Eac3DownmixAudioProcessor
        get() = activeDownmixAudioProcessor

    fun markPlayerAttached() {
        attachedClients.incrementAndGet()
    }

    fun markPlayerDetached() {
        attachedClients.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    fun setPipActive(active: Boolean) {
        pipActive = active
    }

    fun setBackgroundAudioActive(active: Boolean) {
        backgroundAudioActive = active
    }

    fun isBackgroundAudioActive(): Boolean = backgroundAudioActive

    fun lastCodecProfileKey(): String = lastCodecKey

    fun buildCodecProfileKey(
        decoderModeName: String,
        preferSoftwareDecoder: Boolean,
        forceLowRamMode: Boolean,
        batterySaverEnabled: Boolean
    ): String {
        return "$decoderModeName:$preferSoftwareDecoder:$forceLowRamMode:$batterySaverEnabled"
    }

    fun isActivelyInUse(): Boolean {
        if (attachedClients.get() > 0 || pipActive) return true
        val p = pooledPlayer ?: return false
        return backgroundAudioActive && runCatching { p.isPlaying || p.playWhenReady }.getOrDefault(false)
    }

    /**
     * Pre-warms the singleton ExoPlayer asynchronously after the first Compose frame draws,
     * strictly gated to 3 GB+ RAM devices so 1 GB–2 GB devices stay zero-overhead until tap.
     */
    @Synchronized
    fun warmUpAfterFirstFrame(
        context: Context,
        mediaCodecSelector: MediaCodecSelector = NextRenderersFactory.createXvidAwareSelector(),
        extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
        codecProfileKey: String = buildCodecProfileKey("AUTO_HW_PLUS", false, false, false),
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
                downmixAudioProcessor = activeDownmixAudioProcessor,
                isPooled = true
            )
        }

        releaseInternal()

        val freshDownmixProcessor = Eac3DownmixAudioProcessor()
        val loadControl = CyosFastPlayer.createLoadControl(
            context = appContext,
            forceLowRamMode = forceLowRamMode,
            batterySaverEnabled = batterySaverEnabled,
            isNetworkStream = isNetworkStream
        )
        val renderersFactory = CyosFastPlayer.createRenderersFactory(
            context = appContext,
            customAudioProcessors = arrayOf(freshDownmixProcessor),
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

        activeDownmixAudioProcessor = freshDownmixProcessor
        pooledPlayer = newPlayer
        lastCodecKey = codecProfileKey
        lastNetworkMode = isNetworkStream
        CyosMediaSessionController.attachToPlayer(appContext, newPlayer)

        return PooledPlayerSession(
            player = newPlayer,
            downmixAudioProcessor = freshDownmixProcessor,
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
        // Never destroy the player while a player screen, mini-player, PiP, or background audio is actively attached.
        if (isActivelyInUse()) return
        if (CyosFastPlayer.isLowRamDevice(context) ||
            trimLevel >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            trimLevel >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            trimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            releaseInternal()
        }
    }

    @Synchronized
    fun releaseIfIdle() {
        if (isActivelyInUse()) return
        releaseInternal()
    }

    @Synchronized
    fun currentPlayerOrNull(): ExoPlayer? = pooledPlayer

    @Synchronized
    fun release() {
        attachedClients.set(0)
        pipActive = false
        backgroundAudioActive = false
        releaseInternal()
    }

    private fun releaseInternal() {
        lastPreparedUri = null
        lastPreparedPositionMs = 0L
        lastCodecKey = ""
        lastNetworkMode = false
        CyosMediaSessionController.release()
        try {
            pooledPlayer?.release()
        } catch (_: Throwable) {
        }
        pooledPlayer = null
    }
}
