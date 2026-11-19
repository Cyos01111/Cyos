package com.cyosplay.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlin.math.abs

@OptIn(UnstableApi::class)
class XvidSupportExtractorsFactory : ExtractorsFactory {
    private val subtitleParserFactory = DefaultSubtitleParserFactory()
    private val defaultFactory = DefaultExtractorsFactory()
        .setSubtitleParserFactory(subtitleParserFactory)
        .experimentalSetTextTrackTranscodingEnabled(true)
        .setConstantBitrateSeekingEnabled(true)
        .setConstantBitrateSeekingAlwaysEnabled(true)
        .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
        .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )

    override fun createExtractors(): Array<Extractor> {
        return defaultFactory.createExtractors().map { extractor ->
            if (extractor is MatroskaExtractor) {
                // MUST pass DefaultSubtitleParserFactory(), NEVER SubtitleParser.Factory.UNSUPPORTED
                XvidMatroskaExtractor(
                    MatroskaExtractor(subtitleParserFactory)
                )
            } else {
                extractor
            }
        }.toTypedArray()
    }

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>
    ): Array<Extractor> {
        return defaultFactory.createExtractors(uri, responseHeaders).map { extractor ->
            if (extractor is MatroskaExtractor) {
                // MUST pass DefaultSubtitleParserFactory(), NEVER SubtitleParser.Factory.UNSUPPORTED
                XvidMatroskaExtractor(
                    MatroskaExtractor(subtitleParserFactory)
                )
            } else {
                extractor
            }
        }.toTypedArray()
    }
}

@OptIn(UnstableApi::class)
class XvidMatroskaExtractor(
    private val delegate: MatroskaExtractor = MatroskaExtractor(
        DefaultSubtitleParserFactory()
    )
) : Extractor {

    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun init(output: ExtractorOutput) {
        delegate.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput {
                val originalTrack = delegate.let { output.track(id, type) }
                return if (type == C.TRACK_TYPE_VIDEO) {
                    XvidFixingTrackOutput(originalTrack)
                } else {
                    originalTrack
                }
            }

            override fun endTracks() = output.endTracks()
            override fun seekMap(seekMap: SeekMap) = output.seekMap(seekMap)
        })
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int =
        delegate.read(input, seekPosition)

    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

    override fun release() = delegate.release()
}

@OptIn(UnstableApi::class)
class XvidFixingTrackOutput(
    private val delegate: TrackOutput
) : TrackOutput {

    private var pendingUnknownVideoFormat: Format? = null
    private var formatDispatched = false
    private var sampleBuffer: ByteArray? = null
    private var bufferedBytes = 0

    private fun ensureSampleBuffer(): ByteArray {
        var buf = sampleBuffer
        if (buf == null) {
            buf = ByteArray(256 * 1024)
            sampleBuffer = buf
        }
        return buf
    }

    override fun format(format: Format) {
        // MatroskaExtractor sets XVID (V_MS/VFW/FOURCC 0x44495658) to video/x-unknown or video/divx,
        // or emits video/mp4v-es with empty initializationData when VOL header is in the first keyframe.
        val isUnknownOrDivx = format.sampleMimeType == MimeTypes.VIDEO_UNKNOWN ||
            format.sampleMimeType == MimeTypes.VIDEO_DIVX
        val isMp4vMissingCsd = format.sampleMimeType == MimeTypes.VIDEO_MP4V &&
            format.initializationData.isEmpty()

        if (isUnknownOrDivx || isMp4vMissingCsd) {
            pendingUnknownVideoFormat = format
        } else {
            formatDispatched = true
            sampleBuffer = null
            bufferedBytes = 0
            delegate.format(normalizeAnamorphicFormat(format))
        }
    }

    override fun sampleData(
        input: androidx.media3.common.DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int
    ): Int {
        if (formatDispatched) {
            return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart)
        }
        val buf = ensureSampleBuffer()
        val bytesToRead = minOf(length, buf.size - bufferedBytes)
        val bytesRead = input.read(buf, bufferedBytes, bytesToRead)
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            if (allowEndOfInput) return C.RESULT_END_OF_INPUT
            throw java.io.EOFException()
        }
        bufferedBytes += bytesRead
        return bytesRead
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        if (formatDispatched) {
            delegate.sampleData(data, length, sampleDataPart)
            return
        }
        val buf = ensureSampleBuffer()
        val bytesToCopy = minOf(length, buf.size - bufferedBytes)
        data.readBytes(buf, bufferedBytes, bytesToCopy)
        bufferedBytes += bytesToCopy
        if (length > bytesToCopy) {
            data.skipBytes(length - bytesToCopy)
        }
    }

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?
    ) {
        if (!formatDispatched && pendingUnknownVideoFormat != null) {
            val baseFormat = pendingUnknownVideoFormat!!
            val buf = ensureSampleBuffer()
            // Extract MPEG-4 Visual configuration (00 00 01 B0 ... up to first VOP 00 00 01 B6)
            val validSize = minOf(size, bufferedBytes)
            val csd0 = extractMpeg4Csd0(buf, validSize)
            val normalizedPar = resolveNormalizedPixelAspectRatio(baseFormat)
            val fixedFormat = baseFormat.buildUpon()
                .setSampleMimeType(MimeTypes.VIDEO_MP4V) // "video/mp4v-es"
                .setCodecs("mp4v.20.5") // MPEG-4 ASP Level 5 (DivX503b1393p / XVID packed B-frames)
                .setPixelWidthHeightRatio(normalizedPar)
                .apply {
                    if (csd0 != null) {
                        setInitializationData(listOf(csd0))
                    } else if (baseFormat.initializationData.isNotEmpty()) {
                        setInitializationData(baseFormat.initializationData)
                    }
                }
                .build()

            delegate.format(fixedFormat)
            formatDispatched = true

            // Flush the buffered first frame to the underlying TrackOutput and free temporary buffer
            delegate.sampleData(ParsableByteArray(buf, validSize), validSize)
            bufferedBytes = 0
            sampleBuffer = null
            pendingUnknownVideoFormat = null
            delegate.sampleMetadata(timeUs, flags, validSize, 0, cryptoData)
            return
        }

        delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData)
    }

    companion object {
        /**
         * Finds the MPEG-4 Visual Object Sequence / VOL header (00 00 01 B0 / 00 00 01 20)
         * up to the start of the first Video Object Plane (00 00 01 B6) so MediaCodec receives csd-0.
         */
        fun extractMpeg4Csd0(data: ByteArray, length: Int = data.size): ByteArray? {
            val safeLen = minOf(length, data.size)
            if (safeLen < 4) return null
            var vopStart = -1
            for (i in 0 until safeLen - 3) {
                if (data[i] == 0.toByte() &&
                    data[i + 1] == 0.toByte() &&
                    data[i + 2] == 1.toByte() &&
                    data[i + 3] == 0xB6.toByte() // VOP start code
                ) {
                    vopStart = i
                    break
                }
            }
            return if (vopStart > 0) data.copyOfRange(0, vopStart) else null
        }

        /**
         * Fixes non-square pixel aspect ratio (e.g. 704x396 with SAR 396:563 -> 4:3 display
         * or preserves container DAR) so anamorphic SD/HD Matroska streams render without distortion.
         */
        fun resolveNormalizedPixelAspectRatio(format: Format): Float {
            val currentPar = format.pixelWidthHeightRatio
            if (format.width == 704 && format.height == 396) {
                // 704x396 is 16:9 storage; when Matroska DisplayWidth=495x396 (SAR 396:563, ~0.70337f),
                // map to 4:3 display (PAR = (4/3) / (704/396) = 0.75f), or when unspecified (1.0f) map to 396/563f.
                if (abs(currentPar - (396f / 563f)) < 0.02f) {
                    return 0.75f
                }
                if (currentPar <= 0f || abs(currentPar - 1.0f) < 0.01f) {
                    return 396f / 563f
                }
            }
            return if (currentPar > 0.05f && currentPar.isFinite()) currentPar else 1.0f
        }

        private fun normalizeAnamorphicFormat(format: Format): Format {
            val normalizedPar = resolveNormalizedPixelAspectRatio(format)
            return if (abs(normalizedPar - format.pixelWidthHeightRatio) > 0.001f) {
                format.buildUpon().setPixelWidthHeightRatio(normalizedPar).build()
            } else {
                format
            }
        }
    }
}

/**
 * Creates a MediaCodecSelector that prioritizes software MPEG-4 Part 2 decoders
 * (`c2.android.mpeg4.decoder` / `OMX.google.mpeg4.decoder`) for `video/mp4v-es`
 * to prevent black screens on streams with packed B-frames (`DivX503b1393p` / `XVID` `0x44495658`).
 */
@OptIn(UnstableApi::class)
fun createXvidAwareMediaCodecSelector(
    fallbackSelector: MediaCodecSelector = MediaCodecSelector.DEFAULT,
    preferSoftwareForAllCodecs: Boolean = false
): MediaCodecSelector {
    return MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val decoders = fallbackSelector.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder
        )
        if (preferSoftwareForAllCodecs ||
            mimeType == MimeTypes.VIDEO_MP4V ||
            mimeType.contains("mp4v", ignoreCase = true) ||
            mimeType.contains("xvid", ignoreCase = true) ||
            mimeType.contains("divx", ignoreCase = true)
        ) {
            // Put software decoders (c2.android.mpeg4.decoder / OMX.google.mpeg4.decoder) first
            decoders.sortedBy { info: MediaCodecInfo -> info.hardwareAccelerated }
        } else {
            decoders
        }
    }
}

@OptIn(UnstableApi::class)
fun buildCyosExoPlayer(
    context: Context,
    customAudioProcessors: Array<AudioProcessor> = emptyArray(),
    baseCodecSelector: MediaCodecSelector = MediaCodecSelector.DEFAULT,
    trackSelector: TrackSelector? = null,
    loadControl: LoadControl? = null,
    extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
    decoderEngineMode: com.cyosplay.player.data.model.DecoderEngineMode = com.cyosplay.player.data.model.DecoderEngineMode.HW_PLUS
): ExoPlayer {
    val appCtx = context.applicationContext ?: context
    val isSwEngine = decoderEngineMode == com.cyosplay.player.data.model.DecoderEngineMode.SW ||
        decoderEngineMode == com.cyosplay.player.data.model.DecoderEngineMode.SW_PLUS ||
        decoderEngineMode == com.cyosplay.player.data.model.DecoderEngineMode.SOFTWARE_MULTI_CORE

    // 1. Prefer software decoder for SW/SW+ or MPEG-4 Part 2 (Xvid) to avoid hardware B-frame black screen
    val xvidAwareSelector = createXvidAwareMediaCodecSelector(
        fallbackSelector = baseCodecSelector,
        preferSoftwareForAllCodecs = isSwEngine
    )

    // 2. Use NextRenderersFactory to register FFmpeg/software video & audio decoders + fallback
    val renderersFactory = NextRenderersFactory(appCtx, customAudioProcessors)
        .setMediaCodecSelector(xvidAwareSelector)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(extensionRendererMode)

    // 3. Use CyosMediaCache + XvidSupportExtractorsFactory for cached local/HTTP playback & XVID csd-0 recovery
    val mediaSourceFactory = DefaultMediaSourceFactory(
        com.cyos.player.CyosMediaCache.buildCacheDataSourceFactory(appCtx),
        XvidSupportExtractorsFactory()
    )

    val builder = ExoPlayer.Builder(appCtx)
        .setRenderersFactory(renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setSeekParameters(SeekParameters.CLOSEST_SYNC)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)

    if (trackSelector != null) {
        builder.setTrackSelector(trackSelector)
    }
    if (loadControl != null) {
        builder.setLoadControl(loadControl)
    }

    return builder.build().apply {
        videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
    }
}

@OptIn(UnstableApi::class)
fun skipForward10s(player: ExoPlayer): Long = com.cyos.player.CyosFastPlayerManager.skipForward10s(player)

@OptIn(UnstableApi::class)
fun skipBackward10s(player: ExoPlayer): Long = com.cyos.player.CyosFastPlayerManager.skipBackward10s(player)

@OptIn(UnstableApi::class)
fun attachExternalSubtitle(
    context: Context,
    player: ExoPlayer,
    videoUri: Uri,
    subtitleUri: Uri
) {
    val currentPosition = player.currentPosition.coerceAtLeast(0L)
    val playWhenReady = player.playWhenReady

    val mimeType = resolveSubtitleMimeType(context, subtitleUri)

    val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
        .setMimeType(mimeType)
        .setLanguage("en")
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT)
        .build()

    val currentItem = player.currentMediaItem
    val existingSubs = currentItem?.localConfiguration?.subtitleConfigurations.orEmpty()

    val newMediaItem = (currentItem?.buildUpon() ?: MediaItem.Builder().setUri(videoUri))
        .setSubtitleConfigurations(existingSubs + subtitleConfig)
        .build()

    // Ensure text tracks are not disabled in TrackSelectionParameters
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setPreferredTextLanguage("en")
        .build()

    player.setMediaItem(newMediaItem, currentPosition)
    player.prepare()
    player.playWhenReady = playWhenReady
}

fun resolveSubtitleMimeType(context: Context, uri: Uri): String {
    // 1. Check display name from ContentResolver (critical for content:// SAF URIs)
    var fileName: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        fileName = cursor.getString(0)
                    }
                }
        } catch (_: Exception) {
        }
    }
    val name = (fileName ?: uri.lastPathSegment ?: uri.toString()).lowercase()

    return when {
        name.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP // "application/x-subrip"
        name.endsWith(".vtt") -> MimeTypes.TEXT_VTT // "text/vtt"
        name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA // "text/x-ssa"
        name.endsWith(".ttml") || name.endsWith(".xml") || name.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP // Default fallback to SRT
    }
}

@OptIn(UnstableApi::class)
fun isSubtitleOrTextTrackPlaybackError(
    player: ExoPlayer?,
    error: PlaybackException
): Boolean {
    val exoError = error as? ExoPlaybackException
    val isTextRendererError = try {
        exoError?.rendererIndex != null &&
            exoError.rendererIndex >= 0 &&
            player != null &&
            player.getRendererType(exoError.rendererIndex) == C.TRACK_TYPE_TEXT
    } catch (_: Exception) {
        false
    }
    if (isTextRendererError) return true

    val causeTrace = error.cause?.stackTraceToString().orEmpty()
    val causeMessage = error.cause?.message.orEmpty()
    val errorMessage = error.message.orEmpty()
    val causeClassName = error.cause?.javaClass?.name.orEmpty()
    return causeTrace.contains("extractor.text") ||
        causeTrace.contains("androidx.media3.extractor.text") ||
        causeClassName.contains("SubtitleDecoderException", ignoreCase = true) ||
        causeClassName.contains("SubripParser", ignoreCase = true) ||
        causeMessage.contains("Legacy decoding is disabled", ignoreCase = true) ||
        errorMessage.contains("Legacy decoding is disabled", ignoreCase = true) ||
        (causeClassName.contains("ParserException") && causeTrace.contains("text."))
}

@OptIn(UnstableApi::class)
fun handleNonFatalSubtitleErrorIfApplicable(
    context: Context,
    player: ExoPlayer,
    error: PlaybackException,
    onSubtitleDisabled: (() -> Unit)? = null
): Boolean {
    if (!isSubtitleOrTextTrackPlaybackError(player, error)) {
        return false
    }

    try {
        Toast.makeText(
            context,
            "Subtitle file is unsupported or corrupted. Continuing video without subtitles.",
            Toast.LENGTH_SHORT
        ).show()
    } catch (_: Exception) {
    }

    // Disable text track and resume video at the exact same position
    val pos = player.currentPosition.coerceAtLeast(0L)
    val item = player.currentMediaItem
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .build()

    if (item != null) {
        // Strip broken external subtitles if external subtitle loading failed
        val cleanItem = item.buildUpon()
            .setSubtitleConfigurations(emptyList())
            .build()
        player.setMediaItem(cleanItem, pos)
        player.prepare()
        player.play()
    }
    onSubtitleDisabled?.invoke()
    return true
}


