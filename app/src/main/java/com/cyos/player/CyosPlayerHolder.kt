package com.cyos.player

import android.content.ComponentCallbacks2
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import com.cyosplay.player.util.Eac3DownmixAudioProcessor

@OptIn(UnstableApi::class)
object CyosPlayerHolder {

    data class PooledPlayerSession(
        val preloadManager: DefaultPreloadManager,
        val statusControl: CyosPreloadStatusControl,
        val player: ExoPlayer,
        val downmixAudioProcessor: Eac3DownmixAudioProcessor,
        val isPooled: Boolean
    )

    @Volatile
    private var preloadManager: DefaultPreloadManager? = null

    @Volatile
    private var statusControl: CyosPreloadStatusControl? = null

    @Volatile
    private var pooledPlayer: ExoPlayer? = null

    @Volatile
    private var lastPreparedUri: Uri? = null

    @Volatile
    private var lastPreparedPositionMs: Long = 0L

    @Volatile
    private var lastSubmittedUris: List<Uri> = emptyList()

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
        forceLowRamMode: Boolean = false
    ): PooledPlayerSession {
        val appContext = context.applicationContext ?: context
        val isLowRam = forceLowRamMode || CyosFastPlayer.isLowRamDevice(appContext)
        val shouldPool = !isLowRam

        val existingPlayer = pooledPlayer
        val existingPreload = preloadManager
        val existingControl = statusControl
        if (shouldPool &&
            existingPlayer != null &&
            existingPreload != null &&
            existingControl != null &&
            lastCodecKey == codecProfileKey
        ) {
            return PooledPlayerSession(
                preloadManager = existingPreload,
                statusControl = existingControl,
                player = existingPlayer,
                downmixAudioProcessor = sharedDownmixAudioProcessor,
                isPooled = true
            )
        }

        // Release any stale pool when codec config changes or switching into low-RAM mode
        releaseInternal()

        val control = CyosPreloadStatusControl(
            currentPlayingIndex = 0,
            isLowRamDevice = isLowRam
        )
        val loadControl = CyosFastPlayer.createLoadControl(
            context = appContext,
            forceLowRamMode = forceLowRamMode
        )
        val renderersFactory = CyosFastPlayer.createRenderersFactory(
            context = appContext,
            customAudioProcessors = arrayOf(sharedDownmixAudioProcessor),
            mediaCodecSelector = mediaCodecSelector,
            extensionRendererMode = extensionRendererMode
        )
        val builder = CyosFastPlayer.createPreloadBuilder(
            context = appContext,
            statusControl = control,
            loadControl = loadControl,
            renderersFactory = renderersFactory,
            capNetworkStartupBitrate = false,
            subtitlesEnabledByUser = subtitlesEnabledByUser
        )

        val newPreloadManager = builder.build()
        val newPlayer = builder.buildExoPlayer(
            CyosFastPlayer.createWarmExoPlayerBuilder(appContext)
        ).apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }

        if (shouldPool) {
            preloadManager = newPreloadManager
            statusControl = control
            pooledPlayer = newPlayer
            lastCodecKey = codecProfileKey
        }

        return PooledPlayerSession(
            preloadManager = newPreloadManager,
            statusControl = control,
            player = newPlayer,
            downmixAudioProcessor = sharedDownmixAudioProcessor,
            isPooled = shouldPool
        )
    }

    @Synchronized
    fun prepareOnTap(
        context: Context,
        mediaItem: MediaItem,
        playlist: List<MediaItem> = listOf(mediaItem),
        currentIndex: Int = 0,
        resumeMs: Long = 0L,
        codecProfileKey: String = "DEFAULT:${DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON}"
    ): Boolean {
        val appContext = context.applicationContext ?: context
        if (CyosFastPlayer.isLowRamDevice(appContext)) {
            return false
        }
        return runCatching {
            val session = getOrCreateSession(
                context = appContext,
                codecProfileKey = codecProfileKey
            )
            if (!session.isPooled) return@runCatching false

            val safePlaylist = if (playlist.isNotEmpty()) playlist else listOf(mediaItem)
            val safeIndex = currentIndex.coerceIn(0, safePlaylist.lastIndex)
            val uris = safePlaylist.mapNotNull { it.localConfiguration?.uri }
            if (uris != lastSubmittedUris) {
                session.preloadManager.reset()
                safePlaylist.forEachIndexed { idx, item ->
                    session.preloadManager.add(item, idx)
                }
                lastSubmittedUris = uris
            }
            session.statusControl.currentPlayingIndex = safeIndex
            session.preloadManager.setCurrentPlayingIndex(safeIndex)
            session.preloadManager.invalidate()

            CyosFastPlayer.playOnTap(
                player = session.player,
                preloadManager = session.preloadManager,
                mediaItem = safePlaylist[safeIndex],
                resumeMs = resumeMs,
                autoPlay = true
            )
            lastPreparedUri = safePlaylist[safeIndex].localConfiguration?.uri
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
        }
        lastPreparedUri = null
    }

    @Suppress("DEPRECATION")
    @Synchronized
    fun trimOrReleaseOnMemoryPressure(context: Context, trimLevel: Int) {
        if (CyosFastPlayer.isLowRamDevice(context) ||
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
        lastSubmittedUris = emptyList()
        lastCodecKey = ""
        try {
            preloadManager?.release()
        } catch (_: Throwable) {
        }
        preloadManager = null
        statusControl = null
        try {
            pooledPlayer?.release()
        } catch (_: Throwable) {
        }
        pooledPlayer = null
    }
}
