package com.cyosplay.player.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.media3.ui.PlayerView
import com.cyosplay.player.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

data class ScreenshotCaptureResult(
    val success: Boolean,
    val fileName: String = "",
    val savedUriString: String = "",
    val message: String = "",
    val displayPath: String = if (fileName.isNotBlank()) "Pictures/CyosPlay/$fileName" else ""
)

/**
 * Captures real video frame screenshots and persists them into the public Pictures/CyosPlay
 * gallery folder. Uses PixelCopy on the live SurfaceView when available on API 26+, falling
 * back to MediaMetadataRetriever on Dispatchers.IO. Never writes a synthetic placeholder image
 * when frame capture fails.
 */
object VideoScreenshotCapture {

    private const val MAX_SCREENSHOT_DIMENSION = 1920

    /**
     * Async screenshot capture that prefers live PixelCopy from [playerView]'s SurfaceView on API 26+,
     * falling back to MediaMetadataRetriever on Dispatchers.IO, and writes JPEG output off the Main thread.
     */
    suspend fun captureFromSurfaceOrRetriever(
        context: Context,
        playerView: PlayerView?,
        video: VideoItem,
        positionMs: Long
    ): ScreenshotCaptureResult {
        val liveSurfaceBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playerView != null) {
            captureViaPixelCopy(playerView)
        } else {
            null
        }
        return withContext(Dispatchers.IO) {
            val bitmap = liveSurfaceBitmap ?: extractBoundedVideoFrame(context, video, positionMs)
            if (bitmap == null) {
                return@withContext ScreenshotCaptureResult(
                    success = false,
                    message = "Unable to capture frame from this video."
                )
            }
            try {
                saveBitmapToGallery(context, video, bitmap)
            } finally {
                runCatching {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    /**
     * Synchronous capture helper kept for compatibility with non-UI/test callers; runs retriever +
     * MediaStore JPEG save without fabricating a fake placeholder image on failure.
     */
    fun captureCurrentFrameToMediaStore(
        context: Context,
        video: VideoItem,
        positionMs: Long
    ): ScreenshotCaptureResult = captureAndSave(context, video, positionMs)

    fun captureAndSave(
        context: Context,
        video: VideoItem,
        positionMs: Long
    ): ScreenshotCaptureResult {
        val bitmap = extractBoundedVideoFrame(context, video, positionMs)
            ?: return ScreenshotCaptureResult(
                success = false,
                message = "Unable to capture frame from this video."
            )
        return try {
            saveBitmapToGallery(context, video, bitmap)
        } finally {
            runCatching {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private suspend fun captureViaPixelCopy(playerView: PlayerView): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val surfaceView = playerView.videoSurfaceView as? SurfaceView ?: return null
        val surface = surfaceView.holder?.surface ?: return null
        if (!surface.isValid || surfaceView.width <= 0 || surfaceView.height <= 0) return null

        val targetWidth = surfaceView.width.coerceAtMost(MAX_SCREENSHOT_DIMENSION)
        val scale = targetWidth.toFloat() / surfaceView.width.toFloat()
        val targetHeight = (surfaceView.height * scale).toInt().coerceIn(1, MAX_SCREENSHOT_DIMENSION)
        val bitmap = runCatching {
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null

        val handlerThread = HandlerThread("CyosPixelCopy").apply { start() }
        val handler = Handler(handlerThread.looper)

        return try {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation {
                    runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
                    runCatching { handlerThread.quitSafely() }
                }
                try {
                    PixelCopy.request(
                        surfaceView,
                        bitmap,
                        { copyResult ->
                            runCatching { handlerThread.quitSafely() }
                            if (copyResult == PixelCopy.SUCCESS) {
                                if (cont.isActive) cont.resume(bitmap)
                            } else {
                                runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                        handler
                    )
                } catch (_: Throwable) {
                    runCatching { handlerThread.quitSafely() }
                    runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
                    if (cont.isActive) cont.resume(null)
                }
            }
        } catch (_: Throwable) {
            runCatching { handlerThread.quitSafely() }
            runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
            null
        }
    }

    private fun saveBitmapToGallery(
        context: Context,
        video: VideoItem,
        bitmap: Bitmap
    ): ScreenshotCaptureResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitizedTitle = video.title
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            .take(36)
            .ifBlank { "CyosVideo" }
        val fileName = "CyosPlay_${sanitizedTitle}_$timestamp.jpg"

        return try {
            val savedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/CyosPlay"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri.toString()
                } else {
                    saveToAppPicturesFallback(context, fileName, bitmap)
                }
            } else {
                saveToAppPicturesFallback(context, fileName, bitmap)
            }

            ScreenshotCaptureResult(
                success = savedUri.isNotBlank(),
                fileName = fileName,
                savedUriString = savedUri,
                message = if (savedUri.isNotBlank()) {
                    "Screenshot saved to Pictures/CyosPlay/$fileName"
                } else {
                    "Could not write screenshot file."
                }
            )
        } catch (e: Throwable) {
            ScreenshotCaptureResult(
                success = false,
                fileName = fileName,
                message = "Screenshot failed: ${e.localizedMessage ?: "Storage error"}"
            )
        }
    }

    private fun extractBoundedVideoFrame(
        context: Context,
        video: VideoItem,
        positionMs: Long
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            var sourceSet = false
            val parsedUri = video.uri
            if (parsedUri.scheme == "content" || parsedUri.scheme == "file") {
                sourceSet = runCatching {
                    retriever.setDataSource(context, parsedUri)
                    true
                }.getOrDefault(false)
            }
            if (!sourceSet && video.path.isNotBlank()) {
                val file = File(video.path)
                if (file.exists() && file.canRead()) {
                    sourceSet = runCatching {
                        retriever.setDataSource(file.absolutePath)
                        true
                    }.getOrDefault(false)
                }
            }
            if (!sourceSet) return null

            val timeUs = positionMs.coerceAtLeast(0L) * 1000L
            val rawFrame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    MAX_SCREENSHOT_DIMENSION,
                    MAX_SCREENSHOT_DIMENSION
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: return null

            if (rawFrame.width <= MAX_SCREENSHOT_DIMENSION && rawFrame.height <= MAX_SCREENSHOT_DIMENSION) {
                rawFrame
            } else {
                val scale = minOf(
                    MAX_SCREENSHOT_DIMENSION.toFloat() / rawFrame.width.toFloat(),
                    MAX_SCREENSHOT_DIMENSION.toFloat() / rawFrame.height.toFloat()
                )
                val scaledW = (rawFrame.width * scale).toInt().coerceAtLeast(1)
                val scaledH = (rawFrame.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(rawFrame, scaledW, scaledH, true)
                if (scaled !== rawFrame) rawFrame.recycle()
                scaled
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun saveToAppPicturesFallback(
        context: Context,
        fileName: String,
        bitmap: Bitmap
    ): String {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            "CyosPlay"
        )
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, fileName)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
        }
        return Uri.fromFile(outFile).toString()
    }
}
