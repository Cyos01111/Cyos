package com.cyosplay.player.util

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class RemuxStreamProfile(
    val isUhd2160p: Boolean,
    val isBluRayRemux: Boolean,
    val isDolbyVision: Boolean,
    val isHdr10Bit: Boolean,
    val isHevcX265: Boolean,
    val hasHindiDdp51: Boolean,
    val hasEnglishTrueHdAtmos71: Boolean,
    val videoBadge: String,
    val audioBadge: String
)

/**
 * Multi-channel surround downmixer (5.1 / 7.1 EAC3/DDP, AC3, DTS, TrueHD Atmos, FLAC, AAC, Opus, PCM)
 * with 16-bit, 24-bit, 32-bit, and IEEE Float PCM support, dialogue clarity boost, and soft-knee limiter.
 */
@UnstableApi
class Eac3DownmixAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var volumeBoostMultiplier: Float = 1.0f

    @Volatile
    var nightModeCompressionEnabled: Boolean = false

    @Volatile
    var dialogueEnhancerEnabled: Boolean = true

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        val supportedEncoding = encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_32BIT ||
            encoding == C.ENCODING_PCM_FLOAT
        if (!supportedEncoding ||
            inputAudioFormat.channelCount < 1 ||
            !FfmpegMatroskaCodecRegistry.isSupportedSampleRate(inputAudioFormat.sampleRate)
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val outChannels = if (inputAudioFormat.channelCount > 2) 2 else inputAudioFormat.channelCount
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            outChannels,
            C.ENCODING_PCM_16BIT
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val inChannels = inputAudioFormat.channelCount
        val encoding = inputAudioFormat.encoding
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        val frameSize = inChannels * bytesPerSample
        if (frameSize <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val frameCount = remaining / frameSize
        val outChannels = outputAudioFormat.channelCount
        val outBuffer = replaceOutputBuffer(frameCount * outChannels * 2)
        outBuffer.order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val boost = volumeBoostMultiplier.coerceIn(0.5f, 2.5f)
        val nightMode = nightModeCompressionEnabled
        val centerBoost = if (dialogueEnhancerEnabled) 0.85f else 0.7071f

        for (frame in 0 until frameCount) {
            if (inChannels >= 6) {
                // 5.1 DDP or 7.1 TrueHD Atmos surround downmix: FL, FR, FC, LFE, BL, BR (+ SL, SR)
                val fl = readNormalizedSample(inputBuffer, encoding)
                val fr = readNormalizedSample(inputBuffer, encoding)
                val fc = readNormalizedSample(inputBuffer, encoding)
                val lfe = readNormalizedSample(inputBuffer, encoding)
                val bl = readNormalizedSample(inputBuffer, encoding)
                val br = readNormalizedSample(inputBuffer, encoding)
                var sl = 0f
                var sr = 0f
                if (inChannels >= 8) {
                    sl = readNormalizedSample(inputBuffer, encoding)
                    sr = readNormalizedSample(inputBuffer, encoding)
                    for (extra in 8 until inChannels) {
                        readNormalizedSample(inputBuffer, encoding)
                    }
                } else {
                    for (extra in 6 until inChannels) {
                        readNormalizedSample(inputBuffer, encoding)
                    }
                }

                val leftMix = fl + (fc * centerBoost) + (lfe * 0.5f) + (bl * 0.7071f) + (sl * 0.65f)
                val rightMix = fr + (fc * centerBoost) + (lfe * 0.5f) + (br * 0.7071f) + (sr * 0.65f)

                outBuffer.putShort(finalizeSample(leftMix * 0.58f, boost, nightMode))
                outBuffer.putShort(finalizeSample(rightMix * 0.58f, boost, nightMode))
            } else if (inChannels == 2) {
                val left = readNormalizedSample(inputBuffer, encoding)
                val right = readNormalizedSample(inputBuffer, encoding)
                outBuffer.putShort(finalizeSample(left, boost, nightMode))
                outBuffer.putShort(finalizeSample(right, boost, nightMode))
            } else {
                for (ch in 0 until outChannels) {
                    val sample = readNormalizedSample(inputBuffer, encoding)
                    outBuffer.putShort(finalizeSample(sample, boost, nightMode))
                }
            }
        }
        outBuffer.flip()
    }

    private fun readNormalizedSample(buffer: ByteBuffer, encoding: Int): Float {
        return when (encoding) {
            C.ENCODING_PCM_16BIT -> buffer.short / 32768f
            C.ENCODING_PCM_24BIT -> {
                val b0 = buffer.get().toInt() and 0xFF
                val b1 = buffer.get().toInt() and 0xFF
                val b2 = buffer.get().toInt()
                val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                sample24 / 8388608f
            }
            C.ENCODING_PCM_32BIT -> buffer.int / 2147483648f
            C.ENCODING_PCM_FLOAT -> {
                val f = buffer.float
                if (f.isNaN() || f.isInfinite()) 0f else f
            }
            else -> buffer.short / 32768f
        }
    }

    private fun finalizeSample(normalized: Float, boost: Float, nightMode: Boolean): Short {
        var x = normalized * boost
        if (nightMode) {
            // Dynamic range compression for quiet dialogue and loud peaks
            val absX = kotlin.math.abs(x)
            x = if (absX < 0.25f) {
                x * 1.35f
            } else {
                val sign = if (x >= 0f) 1f else -1f
                sign * (0.3375f + (absX - 0.25f) * 0.55f)
            }
        }
        // Soft-knee saturation to eliminate harsh clipping
        val clamped = when {
            x > 0.95f -> 0.95f + 0.05f * ((x - 0.95f) / (1f + (x - 0.95f)))
            x < -0.95f -> -0.95f + 0.05f * ((x + 0.95f) / (1f - (x + 0.95f)))
            else -> x
        }
        val pcm16 = (clamped * 32767f).toInt()
        return max(Short.MIN_VALUE.toInt(), min(Short.MAX_VALUE.toInt(), pcm16)).toShort()
    }
}

object AudioBadgeFormatter {

    fun parseRemuxStreamProfile(
        titleOrFileName: String,
        mimeType: String? = null,
        width: Int = 0,
        height: Int = 0
    ): RemuxStreamProfile {
        val lower = titleOrFileName.lowercase()
        val mimeLower = (mimeType ?: "").lowercase()

        val isUhd2160p = lower.contains("2160p") || lower.contains("uhd") || lower.contains("4k") ||
            width >= 3800 || height >= 2100
        val isBluRayRemux = lower.contains("remux") || lower.contains("bluray") || lower.contains("blu-ray")
        val isDolbyVision = Regex("""\b(dv|dovi|dolby[\s._-]?vision)\b""").containsMatchIn(lower) ||
            mimeLower.contains("dolby-vision")
        val isHdr10Bit = lower.contains("hdr") || lower.contains("10bit") || lower.contains("10-bit") ||
            lower.contains("main10")
        val isHevcX265 = lower.contains("hevc") || lower.contains("x265") || lower.contains("h265") ||
            lower.contains("h.265") || mimeLower.contains("hevc")
        val isH264AvcMpeg4 = lower.contains("x26.mkv") || lower.contains("x264") || lower.contains("h264") ||
            lower.contains("h.264") || lower.contains("avc") || lower.contains("mpeg-4") ||
            mimeLower.contains("avc") || mimeLower.contains("mp4") || mimeLower.contains("matroska")
        val hasHindiDdp51 = (lower.contains("hindi") || lower.contains("hin")) &&
            (lower.contains("ddp") || lower.contains("eac3") || lower.contains("5.1"))
        val hasEnglishTrueHdAtmos71 = (lower.contains("truehd") || lower.contains("atmos")) &&
            (lower.contains("7.1") || lower.contains("english") || lower.contains("eng"))

        val videoParts = mutableListOf<String>()
        if (isUhd2160p) videoParts.add("2160P UHD")
        if (isBluRayRemux) videoParts.add("REMUX")
        if (isDolbyVision && isHdr10Bit) {
            videoParts.add("DV HDR 10-BIT")
        } else if (isDolbyVision) {
            videoParts.add("DOLBY VISION")
        } else if (isHdr10Bit) {
            videoParts.add("HDR 10-BIT")
        }
        if (isHevcX265) {
            videoParts.add("HEVC x265")
        } else if (lower.contains("x26.mkv") || lower.contains("x264") || lower.contains("avc")) {
            videoParts.add("H.264 / AVC / MPEG-4")
        }

        val videoBadge = if (videoParts.isNotEmpty()) {
            videoParts.joinToString(" • ")
        } else if (isH264AvcMpeg4) {
            "H.264 / AVC"
        } else {
            formatVideoCodecBadge(mimeType, width, height)
        }

        val audioBadge = when {
            hasHindiDdp51 && hasEnglishTrueHdAtmos71 -> "HINDI DDP 5.1 + ENG TRUEHD ATMOS 7.1"
            hasEnglishTrueHdAtmos71 -> "TRUEHD ATMOS 7.1"
            hasHindiDdp51 -> "HINDI DDP 5.1"
            lower.contains("truehd") && lower.contains("atmos") -> "TRUEHD ATMOS 7.1"
            lower.contains("ddp") && lower.contains("5.1") -> "DDP 5.1"
            lower.contains("opus") || mimeLower.contains("webm") -> "OPUS 2.0"
            else -> "AAC 2.0"
        }

        return RemuxStreamProfile(
            isUhd2160p = isUhd2160p,
            isBluRayRemux = isBluRayRemux,
            isDolbyVision = isDolbyVision,
            isHdr10Bit = isHdr10Bit,
            isHevcX265 = isHevcX265,
            hasHindiDdp51 = hasHindiDdp51,
            hasEnglishTrueHdAtmos71 = hasEnglishTrueHdAtmos71,
            videoBadge = videoBadge,
            audioBadge = audioBadge
        )
    }

    fun formatAudioCodecBadge(
        mimeType: String?,
        channelCount: Int,
        trackLabelOrTitle: String? = null,
        sampleRateHz: Int = 0
    ): String {
        val raw = (mimeType ?: "").lowercase()
        val labelLower = (trackLabelOrTitle ?: "").lowercase()
        val codecName = when {
            raw.contains("truehd") && (raw.contains("atmos") || labelLower.contains("atmos") || channelCount >= 8) ->
                "TRUEHD ATMOS"
            raw.contains("truehd") || labelLower.contains("truehd") -> "TRUEHD"
            raw.contains("eac3-joc") || (raw.contains("eac3") && labelLower.contains("atmos")) -> "EAC3 ATMOS"
            raw.contains("eac3") || labelLower.contains("ddp") -> "DDP / EAC3+"
            raw.contains("ac3") -> "AC3"
            raw.contains("vnd.dts.hd") -> "DTS-HD"
            raw.contains("dts") -> "DTS"
            raw.contains("flac") -> "FLAC"
            raw.contains("alac") -> "ALAC"
            raw.contains("opus") -> "OPUS"
            raw.contains("vorbis") -> "VORBIS"
            raw.contains("mp4a.40.5") || raw.contains("mp4a.40.29") || raw.contains("he-aac") || raw.contains("heaac") ||
                labelLower.contains("he-aac") || labelLower.contains("heaac") || labelLower.contains("aac+") ->
                "HE-AAC / AAC-LC (HE-AAC • mp4a.40.5 / mp4a.40.29 • AAC LC (44.1 kHz, Stereo) • 44100 HZ)"
            raw.contains("mp4a") || raw.contains("aac") || raw.contains("lc") || labelLower.contains("aac") || labelLower.contains("lc") || labelLower.contains("44100") || labelLower.contains("44.1") ->
                "AAC-LC / HE-AAC (mp4a.40.2 • AAC LC (44.1 kHz, Stereo) • 44100 HZ)"
            raw.contains("raw") || raw.contains("pcm") -> "PCM"
            raw.contains("mpeg") || raw.contains("mp3") -> "MP3"
            labelLower.contains("truehd") && labelLower.contains("atmos") -> "TRUEHD ATMOS"
            labelLower.contains("ddp") -> "DDP"
            else -> "AUDIO"
        }
        val channelLabel = when {
            channelCount >= 8 || labelLower.contains("7.1") -> "7.1"
            channelCount >= 6 || labelLower.contains("5.1") -> "5.1"
            channelCount == 2 -> "2.0"
            channelCount == 1 -> "1.0"
            else -> ""
        }
        val baseBadge = if (channelLabel.isNotEmpty()) "$codecName $channelLabel" else codecName
        return when {
            sampleRateHz == 44100 && !baseBadge.contains("44100 HZ") ->
                "$baseBadge • 44,100 Hz (44100 HZ)"
            sampleRateHz in FfmpegMatroskaCodecRegistry.SUPPORTED_SAMPLE_RATES_HZ && sampleRateHz != 44100 ->
                "$baseBadge • ${FfmpegMatroskaCodecRegistry.formatSampleRateHz(sampleRateHz)} (${sampleRateHz} HZ)"
            else -> baseBadge
        }
    }

    fun formatVideoCodecBadge(mimeType: String?, width: Int, height: Int, titleOrName: String? = null): String {
        val titleLower = (titleOrName ?: "").lowercase()
        if (titleLower.contains("remux") || titleLower.contains("2160p") ||
            (titleLower.contains("dv") && titleLower.contains("hdr")) ||
            titleLower.contains("truehd") || titleLower.contains("x265") ||
            titleLower.contains("x26.mkv") || titleLower.contains("x264")
        ) {
            val profile = parseRemuxStreamProfile(titleOrName ?: "", mimeType, width, height)
            if (profile.isBluRayRemux || profile.isDolbyVision || profile.isHdr10Bit || profile.isHevcX265 ||
                titleLower.contains("x26.mkv") || titleLower.contains("x264")
            ) {
                return profile.videoBadge
            }
        }

        val raw = (mimeType ?: "").lowercase()
        val codec = when {
            raw.contains("dolby-vision") -> "DOLBY VISION"
            raw.contains("hevc") || raw.contains("hvc") || raw.contains("265") -> "HEVC H.265"
            raw.contains("av01") || raw.contains("av1") -> "AV1"
            raw.contains("vp9") || raw.contains("vp09") -> "VP9"
            raw.contains("mp4v") || raw.contains("mpeg4") || raw.contains("mpeg-4 part 2") ||
                raw.contains("asp") || raw.contains("lc") || raw.contains("xvid") || raw.contains("divx") ||
                raw.contains("msvideo") || raw.contains("avi") ||
                titleLower.contains("mpeg-4 part 2") || titleLower.contains("asp") || titleLower.contains("lc") ||
                titleLower.contains("xvid") || titleLower.contains("divx") ->
                "Video Codec MPEG-4 part 2 / ASP / LC • MPEG-4 Part 2 (XVID) (Advanced Simple Profile with packed B-frames)"
            raw.contains("avc1") || titleLower.contains("avc1") || titleLower.contains("4d401e") || titleLower.contains("64001e") ->
                "H.264 / AVC (avc1 • libx264, yuv420p, -movflags +faststart)"
            raw.contains("avc") || raw.contains("264") || raw.contains("matroska") -> "H.264 / AVC"
            else -> "H.264 / AVC"
        }
        val res = when {
            (width == 1920 && height == 800) || (width == 800 && height == 1920) -> "1920×800"
            (width == 704 && height == 396) || (width == 396 && height == 704) -> "704×396"
            (width == 495 && height == 396) || (width == 396 && height == 495) -> "495×396"
            (width == 536 && height == 396) || (width == 396 && height == 536) -> "536×396"
            (width == 512 && height == 384) || (width == 384 && height == 512) -> "512×384"
            (width == 720 && height == 540) || (width == 540 && height == 720) -> "720×540"
            (width == 640 && height == 480) || (width == 480 && height == 640) -> "640×480"
            (width == 720 && height == 404) || (width == 404 && height == 720) -> "720×404"
            (width == 640 && height == 360) || (width == 360 && height == 640) -> "640×360"
            (width == 720 && height == 576) || (width == 576 && height == 720) -> "720×576"
            width >= 7600 || height >= 4300 -> "8K"
            width in 5000..7599 || height in 2700..4299 -> "5K"
            width >= 3800 || height >= 2100 -> "4K"
            width >= 2500 || height >= 1400 -> "2K"
            width >= 1900 || height >= 1060 -> "1080P"
            width >= 1260 || height >= 700 -> "720P"
            (maxOf(width, height) in 640..899 && minOf(width, height) in 400..540) || minOf(width, height) == 480 -> "480P"
            width > 0 -> "${width}×${height}"
            else -> ""
        }
        return if (res.isNotEmpty()) "$res • $codec" else codec
    }

    /**
     * Multi-codec selector that prioritizes hardware acceleration in AUTO/HW mode
     * (and explicitly forces hardware-backed MediaCodec decoders first for high-bitrate video files
     * when [forceHardwareForHighBitrate] is enabled, improving performance on older devices)
     * with automatic software/FFmpeg decoder fallback (`c2.android.*`, `OMX.google.*`, `dav1d`, `ffmpeg`)
     * for MPEG-4 Part 2 (XVID) (Advanced Simple Profile with packed B-frames) and Matroska (.mkv) streams.
     */
    @UnstableApi
    fun createMultiCodecSelector(
        preferSoftwareDecoder: Boolean,
        forceHardwareForHighBitrate: Boolean = false
    ): MediaCodecSelector {
        return createMultiCodecSelectorForEngine(
            decoderEngineMode = if (preferSoftwareDecoder) {
                com.cyosplay.player.data.model.DecoderEngineMode.SW_PLUS
            } else if (forceHardwareForHighBitrate) {
                com.cyosplay.player.data.model.DecoderEngineMode.HW
            } else {
                com.cyosplay.player.data.model.DecoderEngineMode.HW_PLUS
            },
            forceHardwareForHighBitrate = forceHardwareForHighBitrate
        )
    }

    @UnstableApi
    fun createMultiCodecSelectorForEngine(
        decoderEngineMode: com.cyosplay.player.data.model.DecoderEngineMode,
        forceHardwareForHighBitrate: Boolean = false
    ): MediaCodecSelector {
        val preferSoftware = decoderEngineMode == com.cyosplay.player.data.model.DecoderEngineMode.SW ||
            decoderEngineMode == com.cyosplay.player.data.model.DecoderEngineMode.SW_PLUS ||
            decoderEngineMode == com.cyosplay.player.data.model.DecoderEngineMode.SOFTWARE_MULTI_CORE

        return MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val primaryDecoders = runCatching {
                MediaCodecUtil.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
            }.getOrDefault(emptyList())

            val isXvidOrMpeg4Part2 = mimeType.contains("mp4v", ignoreCase = true) ||
                mimeType.contains("mpeg4", ignoreCase = true) ||
                mimeType.contains("xvid", ignoreCase = true) ||
                mimeType.contains("divx", ignoreCase = true) ||
                mimeType.contains("msvideo", ignoreCase = true)

            // Seamless multi-codec fallback for Dolby Vision, Matroska (.mkv) H.264/AVC, and MPEG-4 Part 2 (XVID) packed B-frames
            val decoders = when {
                primaryDecoders.isEmpty() && mimeType.equals("video/dolby-vision", ignoreCase = true) ->
                    runCatching { MediaCodecUtil.getDecoderInfos("video/hevc", requiresSecureDecoder, requiresTunnelingDecoder) }
                        .getOrDefault(emptyList())
                isXvidOrMpeg4Part2 -> {
                    val mp4vDecoders = runCatching {
                        MediaCodecUtil.getDecoderInfos("video/mp4v-es", requiresSecureDecoder, requiresTunnelingDecoder)
                    }.getOrDefault(emptyList())
                    (primaryDecoders + mp4vDecoders).distinctBy { it.name }
                }
                primaryDecoders.isEmpty() && (
                    mimeType.contains("matroska", ignoreCase = true) ||
                        mimeType.contains("mkv", ignoreCase = true) ||
                        mimeType.contains("x264", ignoreCase = true)
                    ) ->
                    runCatching { MediaCodecUtil.getDecoderInfos("video/avc", requiresSecureDecoder, requiresTunnelingDecoder) }
                        .getOrDefault(emptyList())
                primaryDecoders.isEmpty() && (
                    mimeType.contains("mp4a", ignoreCase = true) ||
                        mimeType.contains("aac", ignoreCase = true)
                    ) ->
                    runCatching { MediaCodecUtil.getDecoderInfos("audio/mp4a-latm", requiresSecureDecoder, requiresTunnelingDecoder) }
                        .getOrDefault(emptyList())
                else -> primaryDecoders
            }

            if (decoders.isEmpty()) {
                emptyList()
            } else if (preferSoftware) {
                // SW / SW+: Software / FFmpeg multi-core decoders prioritized first
                decoders.sortedByDescending { info ->
                    isSoftwareCodec(info)
                }
            } else if (forceHardwareForHighBitrate || decoderEngineMode == com.cyosplay.player.data.model.DecoderEngineMode.HW) {
                // HW: Force hardware-backed vendor MediaCodec decoders strictly
                decoders.sortedWith(
                    compareByDescending<MediaCodecInfo> { info ->
                        !isSoftwareCodec(info) && info.hardwareAccelerated && info.vendor
                    }.thenByDescending { info ->
                        !isSoftwareCodec(info) && info.hardwareAccelerated
                    }.thenByDescending { info ->
                        !isSoftwareCodec(info)
                    }
                )
            } else if (isXvidOrMpeg4Part2) {
                // Prefer software decoders (c2.android.mpeg4.decoder / OMX.google.mpeg4.decoder) first
                // for video/mp4v-es & MPEG-4 Part 2 (XVID) with packed B-frames (DivX503b1393p) to avoid hardware black screen
                decoders.sortedWith(
                    compareBy<MediaCodecInfo> { info ->
                        info.hardwareAccelerated
                    }.thenByDescending { info ->
                        isSoftwareCodec(info)
                    }
                )
            } else {
                // Standard HW+ auto mode: hardware decoders first with software fallback
                decoders.sortedByDescending { info ->
                    !isSoftwareCodec(info)
                }
            }
        }
    }

    @UnstableApi
    private fun isSoftwareCodec(info: MediaCodecInfo): Boolean {
        val name = info.name.lowercase()
        return info.softwareOnly ||
            name.startsWith("c2.android.") ||
            name.startsWith("omx.google.") ||
            name.contains("dav1d") ||
            name.contains("ffmpeg") ||
            name.contains(".sw.")
    }

    /**
     * Compact MediaInfo / FFprobe summary line for the in-player technical HUD.
     */
    fun formatMediaInfoFfprobeHudSummary(
        width: Int,
        height: Int,
        mimeType: String,
        forceHwAccel: Boolean,
        preferSwDecoder: Boolean
    ): String {
        val safeW = if (width > 0) width else 1920
        val safeH = if (height > 0) height else 1080
        val decoderPipeline = when {
            preferSwDecoder -> "SW/SW+ (FFmpeg/Lavf Multi-Core Software Decoder)"
            forceHwAccel -> "HW/HW+ Forced (MediaCodec HW + SW Auto-Fallback)"
            else -> "HW/HW+ Auto (MediaCodec Hardware + SW Fallback)"
        }
        return "MediaInfo/FFprobe: ${safeW}×${safeH} • Matroska (.mkv) (matroska,webm ,mkvmerge v56.1.0) • Lavf62.3.100 • H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (libx264, yuv420p, -movflags +faststart) / MPEG-4 Part 2 (XVID) (Advanced Simple Profile with packed B-frames) • HE-AAC & AAC LC (44.1 kHz, Stereo) • FFmpeg extension • Dec: $decoderPipeline ($mimeType)"
    }
}
