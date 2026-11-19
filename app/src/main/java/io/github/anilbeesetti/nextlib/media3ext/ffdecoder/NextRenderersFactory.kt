package io.github.anilbeesetti.nextlib.media3ext.ffdecoder

import android.content.Context
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
 * Media3 RenderersFactory that registers software video and audio decoders (including
 * MPEG-4 Part 2 / XVID packed B-frame software decoding via c2.android.mpeg4.decoder /
 * OMX.google.mpeg4.decoder and custom AudioProcessor pipelines) with automatic decoder fallback.
 */
@OptIn(UnstableApi::class)
open class NextRenderersFactory(
    context: Context,
    private val customAudioProcessors: Array<AudioProcessor> = emptyArray()
) : DefaultRenderersFactory(context.applicationContext) {

    init {
        setEnableDecoderFallback(true)
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
        setMediaCodecSelector(createXvidAwareSelector(MediaCodecSelector.DEFAULT))
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
                    // so packed B-frames (DivX503b1393p / MPEG-4 ASP Level 5) never fail or render a black screen.
                    decoders.sortedBy { info: MediaCodecInfo -> info.hardwareAccelerated }
                } else {
                    decoders
                }
            }
        }
    }
}
