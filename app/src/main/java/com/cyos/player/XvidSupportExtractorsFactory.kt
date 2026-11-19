package com.cyos.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Package-level aliases and delegating helpers in `com.cyos.player` for Media3 1.4+
 * Xvid Matroska extraction with DefaultSubtitleParserFactory, external subtitle attachment
 * via MediaItem.SubtitleConfiguration, and non-fatal subtitle error recovery.
 */
@OptIn(UnstableApi::class)
typealias XvidSupportExtractorsFactory = com.cyosplay.player.XvidSupportExtractorsFactory

@OptIn(UnstableApi::class)
typealias XvidMatroskaExtractor = com.cyosplay.player.XvidMatroskaExtractor

@OptIn(UnstableApi::class)
typealias XvidFixingTrackOutput = com.cyosplay.player.XvidFixingTrackOutput

@OptIn(UnstableApi::class)
fun attachExternalSubtitle(
    context: Context,
    player: ExoPlayer,
    videoUri: Uri,
    subtitleUri: Uri
) {
    com.cyosplay.player.attachExternalSubtitle(
        context = context,
        player = player,
        videoUri = videoUri,
        subtitleUri = subtitleUri
    )
}

fun resolveSubtitleMimeType(context: Context, uri: Uri): String {
    return com.cyosplay.player.resolveSubtitleMimeType(context, uri)
}

@OptIn(UnstableApi::class)
fun handleNonFatalSubtitleErrorIfApplicable(
    context: Context,
    player: ExoPlayer,
    error: PlaybackException,
    onSubtitleDisabled: (() -> Unit)? = null
): Boolean {
    return com.cyosplay.player.handleNonFatalSubtitleErrorIfApplicable(
        context = context,
        player = player,
        error = error,
        onSubtitleDisabled = onSubtitleDisabled
    )
}
