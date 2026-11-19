package com.cyosplay.player.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {

    fun formatDuration(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(safeMs)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatRemainingDuration(positionMs: Long, durationMs: Long): String {
        val remaining = (durationMs - positionMs).coerceAtLeast(0L)
        return "-" + formatDuration(remaining)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatDate(timestampSecondsOrMillis: Long): String {
        if (timestampSecondsOrMillis <= 0L) return "Unknown date"
        val millis = if (timestampSecondsOrMillis < 10_000_000_000L) {
            timestampSecondsOrMillis * 1000L
        } else {
            timestampSecondsOrMillis
        }
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        return sdf.format(Date(millis))
    }

    fun formatResolutionBadge(width: Int, height: Int): String {
        val maxDim = maxOf(width, height)
        val minDim = minOf(width, height)
        return when {
            (width == 1920 && height == 800) || (width == 800 && height == 1920) -> "1920×800"
            (width == 704 && height == 396) || (width == 396 && height == 704) -> "704×396"
            (width == 495 && height == 396) || (width == 396 && height == 495) -> "495×396"
            (width == 536 && height == 396) || (width == 396 && height == 536) -> "536×396"
            (width == 512 && height == 384) || (width == 384 && height == 512) -> "512×384"
            (width == 720 && height == 540) || (width == 540 && height == 720) -> "720×540"
            (width == 640 && height == 480) || (width == 480 && height == 640) -> "640×480"
            (width == 720 && height == 404) || (width == 404 && height == 720) -> "720×404"
            (width == 640 && height == 360) || (width == 360 && height == 640) -> "360p (640×360)"
            (width == 720 && height == 576) || (width == 576 && height == 720) -> "720×576"
            maxDim >= 15000 || minDim >= 8400 -> "16K UHD"
            maxDim >= 7600 || minDim >= 4200 -> "8K UHD"
            maxDim >= 5000 || minDim >= 2800 -> "5K UHD"
            maxDim >= 3800 || minDim >= 2100 -> "4K UHD"
            maxDim >= 2500 || minDim >= 1400 -> "2K QHD"
            maxDim >= 1900 || minDim >= 1060 -> "1080P FHD"
            maxDim >= 1260 || minDim >= 700 -> "720P HD"
            maxDim >= 950 && minDim >= 530 -> "540P qHD"
            maxDim >= 840 && minDim >= 470 -> "480P SD"
            maxDim >= 700 && minDim >= 390 -> "396P WSD"
            maxDim >= 600 && minDim >= 350 -> "360P"
            maxDim >= 400 && minDim >= 230 -> "240P"
            maxDim >= 240 && minDim >= 140 -> "144P"
            maxDim > 0 -> "${minDim}P"
            else -> "HD"
        }
    }
}
