package com.cyos.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cyosplay.player.cyosplay

/**
 * Foreground [MediaSessionService] (`foregroundServiceType="mediaPlayback"`) that keeps
 * background audio alive across Android 12–16 with system notification, CPU wake-lock,
 * and lock-screen playback controls when the user enables Background Audio mode.
 */
@OptIn(UnstableApi::class)
class CyosPlaybackService : MediaSessionService() {

    private var serviceMediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        ensureServiceSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_BACKGROUND_AUDIO) {
            postFallbackForegroundBeforeStopIfNeeded()
            stopSelf()
            return START_NOT_STICKY
        }
        val session = ensureServiceSession()
        val player = CyosPlayerHolder.currentPlayerOrNull()
        val canStayAlive = session != null &&
            player != null &&
            CyosPlayerHolder.isBackgroundAudioActive() &&
            runCatching { player.isPlaying || player.playWhenReady }.getOrDefault(false)
        if (!canStayAlive) {
            // Android 8–16 throws ForegroundServiceDidNotStartInTimeException if startForegroundService()
            // is stopped without posting a foreground notification first.
            postFallbackForegroundBeforeStopIfNeeded()
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun postFallbackForegroundBeforeStopIfNeeded() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                val channel = NotificationChannel(
                    FALLBACK_CHANNEL_ID,
                    "Background Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
            val notification: Notification = NotificationCompat.Builder(this, FALLBACK_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("CyosPlay")
                .setContentText("Stopping background audio")
                .setSilent(true)
                .setOngoing(false)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    FALLBACK_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(FALLBACK_NOTIFICATION_ID, notification)
            }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun ensureServiceSession(): MediaSession? {
        val player = CyosPlayerHolder.currentPlayerOrNull()
        if (player == null) {
            return null
        }
        CyosFastPlayer.setWakeModeForBackgroundAudio(player, true)
        val existing = serviceMediaSession
        if (existing != null && existing.player === player) {
            return existing
        }
        // Release any standalone non-service session before binding to the MediaSessionService
        CyosMediaSessionController.release()
        existing?.let { runCatching { it.release() } }

        val launchIntent = Intent(this, cyosplay::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val created = runCatching {
            MediaSession.Builder(this, player)
                .setId(SERVICE_SESSION_ID)
                .setSessionActivity(sessionActivityIntent)
                .build()
        }.getOrNull()
        serviceMediaSession = created
        CyosMediaSessionController.registerExternalServiceSession(created, player)
        return created
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return ensureServiceSession()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = serviceMediaSession?.player
        if (player == null || !player.playWhenReady || !CyosPlayerHolder.isBackgroundAudioActive()) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        val session = serviceMediaSession
        serviceMediaSession = null
        CyosMediaSessionController.unregisterExternalServiceSession(session)
        session?.let { runCatching { it.release() } }

        // Release CPU wake lock and restore standalone MediaSession for foreground headset/Bluetooth controls
        val survivingPlayer = CyosPlayerHolder.currentPlayerOrNull()
        if (survivingPlayer != null) {
            CyosFastPlayer.setWakeModeForBackgroundAudio(survivingPlayer, false)
            CyosMediaSessionController.attachToPlayer(applicationContext, survivingPlayer)
        }
        super.onDestroy()
    }

    companion object {
        const val SERVICE_SESSION_ID = "cyosplay_bg_media_session"
        const val ACTION_START_BACKGROUND_AUDIO = "com.cyosplay.player.ACTION_START_BACKGROUND_AUDIO"
        const val ACTION_STOP_BACKGROUND_AUDIO = "com.cyosplay.player.ACTION_STOP_BACKGROUND_AUDIO"
        private const val FALLBACK_CHANNEL_ID = "cyosplay_bg_audio_fallback"
        private const val FALLBACK_NOTIFICATION_ID = 40991

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun startForBackgroundAudio(context: Context): Boolean {
            val appCtx = context.applicationContext ?: context
            val player = CyosPlayerHolder.currentPlayerOrNull() ?: return false
            val isActivePlayback = CyosPlayerHolder.isBackgroundAudioActive() &&
                runCatching { player.isPlaying || player.playWhenReady }.getOrDefault(false)
            if (!isActivePlayback) return false
            CyosFastPlayer.setWakeModeForBackgroundAudio(player, true)
            val intent = Intent(appCtx, CyosPlaybackService::class.java).apply {
                action = ACTION_START_BACKGROUND_AUDIO
            }
            return runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
                true
            }.getOrDefault(false)
        }

        fun stopBackgroundService(context: Context) {
            val appCtx = context.applicationContext ?: context
            CyosFastPlayer.setWakeModeForBackgroundAudio(CyosPlayerHolder.currentPlayerOrNull(), false)
            runCatching {
                appCtx.stopService(Intent(appCtx, CyosPlaybackService::class.java))
            }
        }
    }
}
