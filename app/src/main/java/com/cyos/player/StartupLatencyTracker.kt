package com.cyos.player

import android.net.Uri
import android.os.SystemClock

/**
 * Zero-log, in-memory monotonic startup latency tracker using [SystemClock.elapsedRealtime].
 * Captures every playback launch milestone without emitting any android.util.Log or console
 * output, keeping CyosPlay's permanent non-debug and zero-logging policy intact.
 */
object StartupLatencyTracker {

    data class StartupSnapshot(
        val uriString: String = "",
        val isNetworkStream: Boolean = false,
        val tapElapsedMs: Long = 0L,
        val navStartElapsedMs: Long = 0L,
        val playerAcquireElapsedMs: Long = 0L,
        val setMediaItemElapsedMs: Long = 0L,
        val prepareElapsedMs: Long = 0L,
        val surfaceAttachedElapsedMs: Long = 0L,
        val stateReadyElapsedMs: Long = 0L,
        val firstFrameElapsedMs: Long = 0L
    ) {
        val tapToPrepareMs: Long
            get() = deltaOrZero(tapElapsedMs, prepareElapsedMs)

        val tapToNavStartMs: Long
            get() = deltaOrZero(tapElapsedMs, navStartElapsedMs)

        val tapToSurfaceAttachedMs: Long
            get() = deltaOrZero(tapElapsedMs, surfaceAttachedElapsedMs)

        val tapToStateReadyMs: Long
            get() = deltaOrZero(tapElapsedMs, stateReadyElapsedMs)

        val tapToFirstFrameMs: Long
            get() = deltaOrZero(tapElapsedMs, firstFrameElapsedMs)

        val prepareToFirstFrameMs: Long
            get() = deltaOrZero(prepareElapsedMs, firstFrameElapsedMs)

        private fun deltaOrZero(start: Long, end: Long): Long {
            return if (start > 0L && end >= start) end - start else 0L
        }
    }

    @Volatile
    private var current: StartupSnapshot = StartupSnapshot()

    @Volatile
    private var lastCompleted: StartupSnapshot? = null

    fun markVideoTap(uri: Uri?, nowMs: Long = SystemClock.elapsedRealtime()) {
        val scheme = uri?.scheme?.lowercase()
        val isNetwork = scheme == "http" || scheme == "https" || scheme == "rtsp" || scheme == "rtmp"
        current = StartupSnapshot(
            uriString = uri?.toString().orEmpty(),
            isNetworkStream = isNetwork,
            tapElapsedMs = nowMs
        )
    }

    fun markNavigationStart(nowMs: Long = SystemClock.elapsedRealtime()) {
        val snap = current
        if (snap.tapElapsedMs > 0L && snap.navStartElapsedMs == 0L) {
            current = snap.copy(navStartElapsedMs = nowMs)
        }
    }

    fun markPlayerAcquired(nowMs: Long = SystemClock.elapsedRealtime()) {
        val snap = current
        if (snap.tapElapsedMs > 0L && snap.playerAcquireElapsedMs == 0L) {
            current = snap.copy(playerAcquireElapsedMs = nowMs)
        }
    }

    fun markSetMediaItem(nowMs: Long = SystemClock.elapsedRealtime()) {
        val snap = current
        if (snap.tapElapsedMs > 0L && snap.setMediaItemElapsedMs == 0L) {
            current = snap.copy(setMediaItemElapsedMs = nowMs)
        }
    }

    fun markPrepareCalled(nowMs: Long = SystemClock.elapsedRealtime()) {
        val snap = current
        if (snap.tapElapsedMs > 0L && snap.prepareElapsedMs == 0L) {
            current = snap.copy(prepareElapsedMs = nowMs)
        }
    }

    fun markSurfaceAttached(nowMs: Long = SystemClock.elapsedRealtime()) {
        val snap = current
        if (snap.tapElapsedMs > 0L && snap.surfaceAttachedElapsedMs == 0L) {
            current = snap.copy(surfaceAttachedElapsedMs = nowMs)
        }
    }

    fun markStateReady(nowMs: Long = SystemClock.elapsedRealtime()) {
        val snap = current
        if (snap.tapElapsedMs > 0L && snap.stateReadyElapsedMs == 0L) {
            current = snap.copy(stateReadyElapsedMs = nowMs)
        }
    }

    fun markFirstFrameRendered(nowMs: Long = SystemClock.elapsedRealtime()): StartupSnapshot {
        val snap = current
        val completed = if (snap.tapElapsedMs > 0L && snap.firstFrameElapsedMs == 0L) {
            snap.copy(firstFrameElapsedMs = nowMs)
        } else {
            snap
        }
        current = completed
        if (completed.firstFrameElapsedMs > 0L) {
            lastCompleted = completed
        }
        return completed
    }

    fun currentSnapshot(): StartupSnapshot = current

    fun lastCompletedSnapshot(): StartupSnapshot? = lastCompleted

    fun reset() {
        current = StartupSnapshot()
        lastCompleted = null
    }
}
