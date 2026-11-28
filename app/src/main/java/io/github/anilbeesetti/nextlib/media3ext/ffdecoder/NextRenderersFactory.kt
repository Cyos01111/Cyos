package io.github.anilbeesetti.nextlib.media3ext.ffdecoder

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Hardware-first Media3 RenderersFactory (`EXTENSION_RENDERER_MODE_ON`) using Android OEM & Media3
 * codecs (without bundled native FFmpeg `.so` binaries), gating asynchronous MediaCodec queueing
 * strictly to Android 12+ (API 31+) to avoid frame freezes on older Android 7–11 SoCs, and
 * prioritizing software decoders strictly for legacy MPEG-4 Part 2 / Xvid / DivX packed B-frame
 * streams (`video/mp4v-es`) with automatic decoder fallback.
 */
@OptIn(UnstableApi::class)
open class NextRenderersFactory(
    context: Context,
    private val customAudioProcessors: Array<AudioProcessor> = emptyArray()
) : DefaultRenderersFactory(context.applicationContext ?: context) {

    init {
        setEnableDecoderFallback(true)
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
        setMediaCodecSelector(createXvidAwareSelector(MediaCodecSelector.DEFAULT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            forceEnableMediaCodecAsynchronousQueueing()
        }
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        val builder = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        if (customAudioProcessors.isNotEmpty()) {
            builder.setAudioProcessors(customAudioProcessors)
        }
        return builder.build()
    }

    companion object {
        fun createXvidAwareSelector(
            delegateSelector: MediaCodecSelector = MediaCodecSelector.DEFAULT
        ): MediaCodecSelector {
            return MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val decoders = delegateSelector.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
                if (mimeType == MimeTypes.VIDEO_MP4V ||
                    mimeType.equals("video/divx", ignoreCase = true) ||
                    mimeType.equals("video/xvid", ignoreCase = true)
                ) {
                    // Put software decoders (c2.android.mpeg4.decoder / OMX.google.mpeg4.decoder) first
                    // so packed B-frames (DivX503b1393p / MPEG-4 ASP Level 5) never fail or render black.
                    decoders.sortedBy { info: MediaCodecInfo -> info.hardwareAccelerated }
                } else {
                    // Keep hardware decoders first for H.264 / HEVC / VP9 / AV1 / AAC / Opus.
                    decoders.sortedByDescending { info: MediaCodecInfo -> info.hardwareAccelerated }
                }
            }
        }
    }
}
