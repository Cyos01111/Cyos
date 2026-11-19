package com.cyosplay.player.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyosplay.player.data.model.VideoFolder
import com.cyosplay.player.data.model.VideoItem
import com.cyosplay.player.ui.theme.AmberGold
import com.cyosplay.player.ui.theme.ElectricCyan
import com.cyosplay.player.ui.theme.EmeraldVault
import com.cyosplay.player.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * Memory-bounded LruCache for decoded real video frame thumbnails.
 */
object VideoFrameThumbnailCache {
    private val cache = object : LruCache<String, ImageBitmap>(32) {}

    @Synchronized
    fun get(path: String): ImageBitmap? = cache.get(path)

    @Synchronized
    fun put(path: String, bitmap: ImageBitmap) {
        cache.put(path, bitmap)
    }

    fun extractVideoFrameBitmap(path: String, targetWidthPx: Int = 280, targetHeightPx: Int = 158): ImageBitmap? {
        get(path)?.let { return it }
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() <= 512L) {
            return null
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frameTimeUs = if (durationMs > 4_000L) 2_000_000L else (durationMs * 250L).coerceAtLeast(0L)
            val rawBitmap: Bitmap? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    frameTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidthPx,
                    targetHeightPx
                )
            } else {
                retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            val rgb565Bitmap = rawBitmap?.let { src ->
                if (src.config != Bitmap.Config.RGB_565) {
                    val converted = src.copy(Bitmap.Config.RGB_565, false)
                    if (converted != null && converted !== src) {
                        src.recycle()
                    }
                    converted ?: src
                } else {
                    src
                }
            }
            rgb565Bitmap?.asImageBitmap()?.also { img ->
                put(path, img)
            }
        } catch (_: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
        }
    }
}

/**
 * Renders a clean folder icon container without any video thumbnails or video icons (Item 24).
 */
@Composable
fun FolderVideoCollagePreview(
    folder: VideoFolder,
    showCollagePreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF0A101D))
            .border(1.dp, ElectricCyan.copy(alpha = 0.28f), shape)
            .testTag("folder_collage_${folder.id}"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = folder.name,
            tint = ElectricCyan,
            modifier = Modifier.size(30.dp)
        )
    }
}

/**
 * Renders a video item's picture preview thumbnail with real frame extraction + rich cinema frame
 * visual rendering, duration pill, format badge, and play affordance.
 */
@Composable
fun VideoItemPictureThumbnail(
    video: VideoItem,
    showPicturePreview: Boolean = true,
    showDurationBadge: Boolean = true,
    showFormatOverlay: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF0B1322))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
            .testTag("video_picture_${video.id}"),
        contentAlignment = Alignment.Center
    ) {
        if (showPicturePreview) {
            SingleVideoFrameCanvasOrBitmap(
                video = video,
                modifier = Modifier.fillMaxSize(),
                compactQuadrant = false
            )
        }

        // Subtle Play Circle Button in center
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(Color.Black.copy(alpha = 0.52f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play ${video.title}",
                tint = ElectricCyan,
                modifier = Modifier.size(16.dp)
            )
        }

        if (showFormatOverlay) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(bottomEnd = 6.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = video.formatBadge,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = AmberGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        if (showDurationBadge) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(topStart = 6.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = Formatters.formatDuration(video.durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun SingleVideoFrameCanvasOrBitmap(
    video: VideoItem,
    modifier: Modifier = Modifier,
    compactQuadrant: Boolean = false
) {
    val context = LocalContext.current
    var frameBitmap by remember(video.path) {
        mutableStateOf(VideoFrameThumbnailCache.get(video.path))
    }

    LaunchedEffect(video.path) {
        if (frameBitmap == null) {
            val extracted = withContext(Dispatchers.IO) {
                VideoFrameThumbnailCache.extractVideoFrameBitmap(video.path)
            }
            if (extracted != null) {
                frameBitmap = extracted
            }
        }
    }

    val bmp = frameBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        CinemaFramePreviewArtwork(
            video = video,
            compactQuadrant = compactQuadrant,
            modifier = modifier
        )
    }
}

/**
 * High-contrast procedural cinema video frame preview when the video file is synthetic or
 * while hardware frame extraction is in progress. Generates a distinctive scene per video
 * (skyline/horizon glow, cinema widescreen bars, lens flare orb, and waveform/frame lines).
 */
@Composable
private fun CinemaFramePreviewArtwork(
    video: VideoItem,
    compactQuadrant: Boolean,
    modifier: Modifier = Modifier
) {
    val hash = remember(video.path, video.title) {
        abs((video.path.hashCode() * 31) xor video.title.hashCode())
    }
    val paletteIndex = hash % 5
    val (skyTop, skyMid, horizonGlow, ridgeColor) = remember(paletteIndex) {
        when (paletteIndex) {
            0 -> listOf(
                Color(0xFF071A2E),
                Color(0xFF0F385A),
                Color(0xFF00E5FF),
                Color(0xFF06101E)
            )
            1 -> listOf(
                Color(0xFF1E1035),
                Color(0xFF3E1F68),
                Color(0xFFFFB300),
                Color(0xFF0E071B)
            )
            2 -> listOf(
                Color(0xFF082420),
                Color(0xFF114B43),
                Color(0xFF00E676),
                Color(0xFF051412)
            )
            3 -> listOf(
                Color(0xFF281219),
                Color(0xFF591F2E),
                Color(0xFFFF5252),
                Color(0xFF14080C)
            )
            else -> listOf(
                Color(0xFF0E1C36),
                Color(0xFF1E3C72),
                Color(0xFF64B5F6),
                Color(0xFF080F1E)
            )
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Sky gradient background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(skyTop, skyMid, ridgeColor),
                startY = 0f,
                endY = h
            ),
            size = Size(w, h)
        )

        // Glowing sun/moon cinema orb
        val orbX = w * (0.28f + ((hash % 45) / 100f))
        val orbY = h * 0.38f
        val orbRadius = minOf(w, h) * (if (compactQuadrant) 0.28f else 0.24f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    horizonGlow.copy(alpha = 0.72f),
                    horizonGlow.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = Offset(orbX, orbY),
                radius = orbRadius * 1.8f
            ),
            radius = orbRadius * 1.8f,
            center = Offset(orbX, orbY)
        )
        drawCircle(
            color = horizonGlow.copy(alpha = 0.85f),
            radius = orbRadius * 0.55f,
            center = Offset(orbX, orbY)
        )

        // Mountain/Cinema silhouette ridge
        val peak1X = w * (0.22f + ((hash % 17) / 100f))
        val peak1Y = h * 0.52f
        val peak2X = w * (0.68f - ((hash % 19) / 100f))
        val peak2Y = h * 0.46f

        val silhouettePath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.68f)
            lineTo(peak1X, peak1Y)
            lineTo(w * 0.48f, h * 0.66f)
            lineTo(peak2X, peak2Y)
            lineTo(w, h * 0.62f)
            lineTo(w, h)
            close()
        }
        drawPath(
            path = silhouettePath,
            color = ridgeColor.copy(alpha = 0.92f)
        )

        // Subtle cinema letterbox frame lines
        if (!compactQuadrant) {
            val barH = (h * 0.08f).coerceAtLeast(2f)
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, 0f),
                size = Size(w, barH)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, h - barH),
                size = Size(w, barH)
            )
            // Subtle progress/timecode accent bar
            drawRoundRect(
                color = horizonGlow.copy(alpha = 0.65f),
                topLeft = Offset(w * 0.08f, h - barH * 1.8f),
                size = Size(w * 0.36f, 2.dp.toPx()),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}
