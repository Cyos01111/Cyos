package com.cyos.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * Manages the Media3 [MediaSession] attached to CyosPlay's shared [ExoPlayer],
 * coordinating seamlessly between foreground playback and [CyosPlaybackService]
 * when Background Audio mode is active so Bluetooth, headset, car, and lock-screen
 * controls stay active without duplicate session ID collisions.
 */
@OptIn(UnstableApi::class)
object CyosMediaSessionController {

    private const val SESSION_ID = "cyosplay_media_session"

    @Volatile
    private var activeSession: MediaSession? = null

    @Volatile
    private var boundPlayer: Player? = null

    @Volatile
    private var isOwnedByService: Boolean = false

    @Synchronized
    fun attachToPlayer(context: Context, player: Player): MediaSession? {
        if (boundPlayer === player && activeSession != null) {
            return activeSession
        }
        if (isOwnedByService && activeSession != null) {
            // CyosPlaybackService owns the session lifecycle while background audio is running
            return activeSession
        }
        release()
        val appContext = context.applicationContext ?: context
        val created = runCatching {
            MediaSession.Builder(appContext, player)
                .setId(SESSION_ID)
                .build()
        }.getOrNull()
        activeSession = created
        boundPlayer = if (created != null) player else null
        isOwnedByService = false
        return created
    }

    @Synchronized
    fun registerExternalServiceSession(session: MediaSession?, player: Player?) {
        if (session != null && player != null) {
            activeSession = session
            boundPlayer = player
            isOwnedByService = true
        }
    }

    @Synchronized
    fun unregisterExternalServiceSession(session: MediaSession?) {
        if (isOwnedByService && (session == null || activeSession === session)) {
            activeSession = null
            boundPlayer = null
            isOwnedByService = false
        }
    }

    @Synchronized
    fun activeSessionOrNull(): MediaSession? = activeSession

    @Synchronized
    fun isAttached(): Boolean = activeSession != null && boundPlayer != null

    @Synchronized
    fun release() {
        val session = activeSession
        val ownedByService = isOwnedByService
        activeSession = null
        boundPlayer = null
        isOwnedByService = false
        if (session != null && !ownedByService) {
            runCatching { session.release() }
        }
    }
}
