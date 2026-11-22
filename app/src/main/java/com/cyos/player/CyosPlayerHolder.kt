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

/**
 * Lightweight, cache-free and preloader-free ExoPlayer session manager.
 * Eliminates background preloading queues and immediately clears idle media buffers on detach
 * to maintain flat RAM usage and minimal battery consumption.
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

    val sharedDownmixAudioProcessor: Eac3DownmixAudioProcessor = Eac3DownmixAudioProcessor()

    @Synchronized
    fun getOrCreateSession(
        context: Context,
        mediaCodecSelector: MediaCodecSelector = MediaCodecSelector.DEFAULT,
        extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
        codecProfileKey: String = "DEFAULT:$extensionRendererMode",
        subtitlesEnabledByUser: Boolean = false,
        forceLowRamMode: Boolean = false,
        batterySaverEnabled: Boolean = false
    ): PooledPlayerSession {
        val appContext = context.applicationContext ?: context
        val isLowRam = forceLowRamMode || CyosFastPlayer.isLowRamDevice(appContext)
        val shouldPool = !isLowRam

        val existingPlayer = pooledPlayer
        if (shouldPool &&
            existingPlayer != null &&
            lastCodecKey == codecProfileKey
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
            batterySaverEnabled = batterySaverEnabled
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
            capNetworkStartupBitrate = false,
            subtitlesEnabledByUser = subtitlesEnabledByUser
        )

        if (shouldPool) {
            pooledPlayer = newPlayer
            lastCodecKey = codecProfileKey
        }

        return PooledPlayerSession(
            player = newPlayer,
            downmixAudioProcessor = sharedDownmixAudioProcessor,
            isPooled = shouldPool
        )
    }

    @Synchronized
    fun prepareOnTap(
        context: Context,
        mediaItem: MediaItem,
        resumeMs: Long = 0L,
        codecProfileKey: String = "DEFAULT:${DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON}",
        batterySaverEnabled: Boolean = false
    ): Boolean {
        val appContext = context.applicationContext ?: context
        if (CyosFastPlayer.isLowRamDevice(appContext)) {
            return false
        }
        return runCatching {
            val session = getOrCreateSession(
                context = appContext,
                codecProfileKey = codecProfileKey,
                batterySaverEnabled = batterySaverEnabled
            )
            if (!session.isPooled) return@runCatching false

            CyosFastPlayer.playOnTap(
                player = session.player,
                mediaItem = mediaItem,
                resumeMs = resumeMs,
                autoPlay = true
            )
            lastPreparedUri = mediaItem.localConfiguration?.uri
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
        try {
            pooledPlayer?.release()
        } catch (_: Throwable) {
        }
        pooledPlayer = null
    }
}
