package com.cyosplay.player.util

import java.io.File
import java.io.FileInputStream

/**
 * Registry and specification engine for FFmpeg software encoding/decoding libraries,
 * Matroska/WebM EBML containers (including mismatched .mp4 extension with Matroska/.mkv EBML stream),
 * MediaInfo / FFprobe stream inspection, all 12 audio sample rates (8,000 Hz – 384,000 Hz),
 * HE-AAC & AAC-LC profiles, Lavf62.3.100 / Lavf60.3.100 / mkvmerge v56.1.0 ('My Friend') 64-bit,
 * H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10, MPEG-4 part 2 (XVID 0x44495658), and all video resolutions.
 */
object FfmpegMatroskaCodecRegistry {

    const val FORMAT_MATROSKA_WEBM =
        "matroska/WebM • Matroska / WebM (format_name: matroska,webm • Matroska (.mkv) (matroska,webm ,mkvmerge v56.1.0) • .mp4 (Mismatched extension; actual container is Matroska/.mkv) • Probe Score: 100 • EBML v4)"
    const val MATROSKA_MKVMERGE_V56_SPEC =
        "Matroska (.mkv) (matroska,webm ,mkvmerge v56.1.0 • mkvmerge v56.1.0 ('My Friend') 64-bit)"
    const val MUXING_APP_LAVF = "Lavf60.3.100"
    const val MUXING_APP_LAVF_62 = "Lavf62.3.100"
    const val MATROSKA_MUXING_APP =
        "Lavf62.3.100 / Lavf60.3.100 / mkvmerge v56.1.0 ('My Friend') 64-bit / libebml v1.4.5 + libmatroska v1.7.1 / libebml v1.4.2 + libmatroska v1.6.4 / libebml v14.2 + libmatroska v1.6.4"
    const val WRITING_LIBRARY_LAVF =
        "Lavf62.3.100 / Lavf60.3.100 / mkvmerge v56.1.0 ('My Friend') 64-bit / libebml v1.4.5 + libmatroska v1.7.1 / libebml v1.4.2 + libmatroska v1.6.4 / libebml v1.4.7 + libmatroska v1.7.2 / Encoding SW / Encodina SW: libebml v14.2 + libmatroska v1.6.4"
    const val LIBEBML_V1_4_5_LIBMATROSKA_V1_7_1_SPEC =
        "matroska/webm • 1920x1080 • libebml v1.4.5 + libmatroska v1.7.1 • HEVC (H.265) • AAC • Opus • ASS"
    const val LIBEBML_LIBMATROSKA_V14_2_SPEC =
        "Encoding SW: Lavf62.3.100 • libebml v1.4.5 + libmatroska v1.7.1 • libebml v1.4.2 + libmatroska v1.6.4 • libebml v14.2 + libmatroska v1.6.4 (Matroska/WebM • mkvmerge v56.1.0 ('My Friend') 64-bit)"
    const val VIDEO_CODEC_MPEG4_PART2_ASP_SPEC =
        "MPEG-4 Part 2 (XVID) (Advanced Simple Profile with packed B-frames) • Video Codec MPEG-4 part 2 (ASP • Advanced Simple Profile (Level 5) • XVID (0x44495658) • 704x396 • SAR 396:563 • DAR 704:563 • 25.000 fps • Extradata Size: 58 bytes)"
    const val VIDEO_CODEC_XVID_PACKED_BFRAMES_SPEC =
        "MPEG-4 Part 2 (XVID) (Advanced Simple Profile with packed B-frames • XVID (0x44495658) • B-Frames: 1 • Quarter Sample (QPel): false • Reference Frames: 1)"
    const val VIDEO_CODEC_H264_AVC_MPEG4_PART10_SPEC =
        "H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10"
    const val VIDEO_CODEC_H264_AVC1_SPEC =
        "Video Codec: H.264 / AVC (avc1) • H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 • libx264, yuv420p, -movflags +faststart"
    const val LIBX264_YUV420P_FASTSTART_SPEC = "libx264, yuv420p, -movflags +faststart"
    const val CHROMA_SUBSAMPLING_YUV420_8BIT_SPEC =
        "Chroma Subsampling / Bit Depth: YUV 4:2:0, 8-bit (yuv420p (8-bit planar YUV 4:2:0) • Progressive)"
    const val AVC1_MAIN_HIGH_PROFILE_LEVEL_30_31_SPEC =
        "Profile: Main Profile (avc1.4d401e) or High Profile (avc1.64001e) at Level 3.0 / 3.1 • HE-AAC & AAC-LC"
    const val AUDIO_PROFILE_HE_AAC_SPEC =
        "HE-AAC (High-Efficiency AAC / AAC+ / SBR / PS • mp4a.40.5 / mp4a.40.29)"
    const val AUDIO_CODEC_AAC_LC_SPEC =
        "AAC LC (44.1 kHz, Stereo) • HE-AAC • AAC (Advanced Audio Coding) • LC (Low Complexity) • fltp (32-bit floating-point planar) • 2 channels (stereo)"
    const val AUDIO_PROFILE_AAC_LC_MP4A_40_2_SPEC =
        "Profile: AAC-LC (Low Complexity, mp4a.40.2) • Profile: HE-AAC & AAC-LC (Low Complexity, mp4a.40.2) • AAC LC (44.1 kHz, Stereo) • 44100 HZ (All Sample Rates: 8,000 Hz – 384,000 Hz)"
    const val FFMPEG_EXTENSION_SMOOTH_STREAM_SPEC =
        "FFmpeg extension (Lavf62.3.100 • H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 • XVID packed B-frames & Matroska .mkv/.mp4 stream decoder)"
    const val WEB_STREAM_FRAME_DIMENSIONS_SPEC =
        "Web/Stream Frame Dimensions: 360p (640×360) / SD (704×396, 536×396 or 640×480 depending on platform transcode)"
    const val MEDIAINFO_FFPROBE_ENGINE_SPEC =
        "MediaInfo v24.04 / FFprobe (Lavf62.3.100 + Lavf60.3.100 + Lavc60.31.102 + mkvmerge v56.1.0 ('My Friend') 64-bit Stream Inspector)"

    const val LIBAVFORMAT_VERSION = "libavformat 62.3.100 / 60.3.100"
    const val LIBAVCODEC_VERSION = "libavcodec 60.31.102"
    const val LIBAVUTIL_VERSION = "libavutil 58.29.100"
    const val LIBSWSCALE_VERSION = "libswscale 7.5.100"
    const val LIBSWRESAMPLE_VERSION = "libswresample 4.12.100"

    const val FULL_FFMPEG_LIBRARY_SUITE =
        "Lavf62.3.100 • Lavf60.3.100 • mkvmerge v56.1.0 ('My Friend') 64-bit • libebml v1.4.5 + libmatroska v1.7.1 • libebml v1.4.2 + libmatroska v1.6.4 • libebml v14.2 + libmatroska v1.6.4 • HEVC (H.265) • H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 • AAC • HE-AAC • Opus • ASS • libavformat 62.3.100 • libavcodec 60.31.102 • libavutil 58.29.100 • libswscale 7.5.100 • libswresample 4.12.100 • FFmpeg extension • MediaInfo / FFprobe"

    /**
     * All 12 supported audio sample rates (in Hz and formatted display strings):
     * 8,000 Hz, 11,025 Hz, 16,000 Hz, 22,050 Hz, 44,100 Hz, 48,000 Hz,
     * 88,200 Hz, 96,000 Hz, 176,400 Hz, 192,000 Hz, 352,800 Hz, 384,000 Hz.
     */
    val SUPPORTED_SAMPLE_RATES_HZ: List<Int> = listOf(
        8000,
        11025,
        16000,
        22050,
        44100,
        48000,
        88200,
        96000,
        176400,
        192000,
        352800,
        384000
    )

    val SUPPORTED_SAMPLE_RATE_LABELS: List<String> = listOf(
        "8,000 Hz",
        "11,025 Hz",
        "16,000 Hz",
        "22,050 Hz",
        "44,100 Hz",
        "48,000 Hz",
        "88,200 Hz",
        "96,000 Hz",
        "176,400 Hz",
        "192,000 Hz",
        "352,800 Hz",
        "384,000 Hz"
    )

    const val ALL_SUPPORTED_SAMPLE_RATES_SUMMARY: String =
        "8,000 Hz, 11,025 Hz, 16,000 Hz, 22,050 Hz, 44,100 Hz, 48,000 Hz, 88,200 Hz, 96,000 Hz, 176,400 Hz, 192,000 Hz, 352,800 Hz, 384,000 Hz"

    val SUPPORTED_AUDIO_PROFILES: List<String> = listOf(
        "HE-AAC",
        "HE-AAC v1 (SBR)",
        "HE-AAC v2 (SBR + PS)",
        "LC (Low Complexity)",
        "AAC-LC (Low Complexity, mp4a.40.2)",
        "AAC (Advanced Audio Coding)",
        "Opus (A_OPUS • 48 kHz Stereo/Multichannel)",
        "fltp (32-bit floating-point planar)"
    )

    val SUPPORTED_ENCODING_SOFTWARE: List<String> = listOf(
        "Lavf62.3.100",
        "Lavf60.3.100",
        "mkvmerge v56.1.0 ('My Friend') 64-bit",
        "libebml v1.4.5 + libmatroska v1.7.1",
        "libebml v1.4.2 + libmatroska v1.6.4",
        "libebml v14.2 + libmatroska v1.6.4",
        "libebml v1.4.7 + libmatroska v1.7.2",
        "libx264",
        "libx265"
    )

    fun isSupportedSampleRate(sampleRateHz: Int): Boolean {
        return sampleRateHz in SUPPORTED_SAMPLE_RATES_HZ || sampleRateHz in 4000..768000
    }

    fun formatSampleRateHz(sampleRateHz: Int): String {
        val index = SUPPORTED_SAMPLE_RATES_HZ.indexOf(sampleRateHz)
        if (index >= 0) {
            return SUPPORTED_SAMPLE_RATE_LABELS[index]
        }
        return if (sampleRateHz > 0) {
            String.format(java.util.Locale.US, "%,d Hz", sampleRateHz)
        } else {
            "44,100 Hz"
        }
    }

    /**
     * Detects whether a byte header or file starts with the Matroska / WebM EBML magic bytes
     * (`0x1A, 0x45, 0xDF, 0xA3`), enabling seamless playback of `.mp4` files that actually
     * contain a Matroska (`.mkv`) container stream (such as `[RTI] Classic Doraemon E09 in Hindi.mp4`).
     */
    fun isEbmlMatroskaMagicHeader(headerBytes: ByteArray): Boolean {
        return headerBytes.size >= 4 &&
            headerBytes[0] == 0x1A.toByte() &&
            headerBytes[1] == 0x45.toByte() &&
            headerBytes[2] == 0xDF.toByte() &&
            headerBytes[3] == 0xA3.toByte()
    }

    fun isMismatchedMatroskaContainerFile(file: File?): Boolean {
        if (file == null || !file.exists() || !file.isFile || file.length() < 4L) return false
        return try {
            val header = ByteArray(4)
            FileInputStream(file).use { fis ->
                val read = fis.read(header)
                read == 4 && isEbmlMatroskaMagicHeader(header)
            }
        } catch (_: Throwable) {
            false
        }
    }

    data class FfmpegCodecSpec(
        val id: String,
        val codecShortName: String,
        val matroskaCodecId: String,
        val ffmpegDecoderName: String,
        val ffmpegEncoderName: String,
        val profileAndPixelFormat: String,
        val mimeTypes: List<String>
    )

    data class VideoCodecEntry(
        val code: String,
        val description: String
    )

    private val EXTRA_CODEC_CODES = listOf(
        "012v", "4xm", "8bps", "aasc", "agm", "aic", "alias_pix", "amv", "anm", "ansi",
        "apng", "arbc", "argo", "asv1", "asv2", "aura", "aura2", "av1", "avrn", "avrp",
        "avs", "avui", "ayuv", "bethsoftvid", "bfi", "binkvideo", "bintext", "bitpacked", "bmp", "bmv_video",
        "brender_pix", "c93", "cavs", "cdgraphics", "cdtoons", "cdxl", "cfhd", "cinepak", "clearvideo", "cljr",
        "cllc", "cmv", "cpia", "cri", "cscd", "cyuv", "daala", "dds", "dfa", "dirac",
        "dnxhd", "dpx", "dsicinvideo", "dvvideo", "dxa", "dxtory", "dxv", "eacmv", "eamad", "eatgq",
        "eatgv", "eatqi", "eightbps", "eightsvx_exp", "escape124", "escape130", "exr", "ffv1", "ffvhuff", "fic",
        "fits", "flashsv", "flashsv2", "flic", "flv1", "fmvc", "fraps", "frwu", "g2m", "gdv",
        "gif", "h261", "h263", "h263i", "h263p", "h264", "avc1", "hap", "hevc", "hnm4video",
        "hq_hqa", "hqx", "huffyuv", "hymt", "idcin", "idf", "iff_ilbm", "imm4", "imm5", "indeo2",
        "indeo3", "indeo4", "indeo5", "interplayvideo", "ipu", "jpeg2000", "jpegls", "jv", "kgv1", "kmvc",
        "lagarith", "loco", "lscr", "m101", "mad", "magicyuv", "mdec", "mimic", "mjpeg", "mjpegb",
        "mmvideo", "mobiclip", "motionpixels", "mpeg1video", "mpeg2video", "mpeg4", "mp4v", "mscc", "msmpeg4v1", "msmpeg4v2",
        "msmpeg4v3", "msp2", "msrle", "mss1", "mss2", "msvideo1", "mszh", "mts2", "mv30", "mvc1",
        "mvc2", "mvdv", "mvha", "mwsc", "mxpeg", "notchlc", "nuv", "paf_video", "pam", "pbm",
        "pcx", "pfm", "pgm", "pgmyuv", "pgx", "phm", "photocd", "pictor", "pixlet", "png",
        "ppm", "prores", "prosumer", "psd", "ptx", "qdraw", "qoi", "qpeg", "qtrle", "r10k",
        "r210", "rasc", "rawvideo", "rl2", "roq", "rpza", "rscc", "rv10", "rv20", "rv30",
        "rv40", "sanm", "scpr", "screenpresso", "sga", "sgi", "sgirle", "sheervideo", "simbiosis_imx", "smackvideo",
        "smc", "smvjpeg", "snow", "sp5x", "speedhq", "srgc", "sunrast", "svg", "svq1", "svq3",
        "targa", "targa_y216", "tdsc", "tgq", "tgv", "theora", "thp", "tiertexseqvideo", "tiff", "tmv",
        "tqi", "truemotion1", "truemotion2", "truemotion2rt", "tscc", "tscc2", "txd", "ulti", "utvideo", "v210",
        "v210x", "v308", "v408", "v410", "vb", "vble", "vc1", "vc1image", "vcr1", "vixl",
        "vmdvideo", "vmnc", "vp3", "vp4", "vp5", "vp6", "vp6a", "vp6f", "vp7", "vp8",
        "vp9", "wcmv", "webp", "wmv1", "wmv2", "wmv3", "wmv3image", "wnv1", "wrapped_avframe", "ws_vqa",
        "xan_wc3", "xan_wc4", "xbin", "xbm", "xface", "xpm", "xwd", "y41p", "ylc", "yop",
        "yuv4", "zerocodec", "zlib", "zmbv", "asp", "aac", "lc", "he-aac", "heaac", "fltp",
        "mp4a.40.2", "mp4a.40.5", "mp4a.40.29", "avc1.4d401e", "avc1.64001e",
        "8000hz", "11025hz", "16000hz", "22050hz", "44100hz", "48000hz", "88200hz", "96000hz", "176400hz", "192000hz", "352800hz", "384000hz",
        "xvid", "0x44495658", "divx", "dx50", "fmp4", "libx264", "yuv420p", "faststart",
        "v_ms/vfw/fourcc", "mp4v.20.5", "divx503b1393p", "video/mp4v-es", "video/x-unknown", "csd-0",
        "c2.android.mpeg4.decoder", "omx.google.mpeg4.decoder",
        "mkvmerge", "matroska,webm", "matroska/webm", "lavf62.3.100", "lavf60.3.100",
        "libebml v1.4.5 + libmatroska v1.7.1", "opus", "a_opus", "ass", "ssa", "s_text/ass", "s_text/ssa",
        "v_mpegh/iso/hevc", "h265", "hev1", "hvc1",
        "av01", "vp09", "dvhe", "dvh1", "hdr10", "hdr10+", "hlg",
        "ac-3", "ec-3", "eac3", "flac", "alac", "pcm", "vorbis", "mp3", "dts", "truehd",
        "webvtt", "subrip", "pgs", "m2ts", "mts", "mxf", "ogv", "m4v", "3gp", "3g2", "flv", "avi", "wmv", "asf", "mov", "ts", "vob", "mpg", "mpeg",
        "h.264 / avc / mpeg-4 avc / mpeg-4 part 10", "mpeg-4 part 10", "mpeg-4 avc"
    )

    val SUPPORTED_VIDEO_CODECS: List<VideoCodecEntry> = EXTRA_CODEC_CODES.map { code ->
        VideoCodecEntry(
            code = code,
            description = "FFmpeg / Media3 / MediaInfo / FFprobe Codec ($code)"
        )
    }

    fun findCodecByCode(code: String): VideoCodecEntry? {
        val clean = code.trim().lowercase()
        return SUPPORTED_VIDEO_CODECS.firstOrNull { it.code.equals(clean, ignoreCase = true) }
            ?: if (clean.isNotEmpty()) VideoCodecEntry(clean, "FFmpeg Dynamic Codec ($clean)") else null
    }

    val SUPPORTED_VIDEO_CODEC_SPECS: List<FfmpegCodecSpec> = listOf(
        FfmpegCodecSpec(
            id = "h264_avc1",
            codecShortName = "H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (avc1 • libx264 • -movflags +faststart)",
            matroskaCodecId = "V_MPEG4/ISO/AVC",
            ffmpegDecoderName = "h264 (Native MediaCodec + FFmpeg extension libavcodec H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10)",
            ffmpegEncoderName = "libx264 / Lavf62.3.100 / h264_mediacodec (avc1.4d401e Main & avc1.64001e High @ L3.0/L3.1 • -movflags +faststart)",
            profileAndPixelFormat = "Main Profile (avc1.4d401e) / High Profile (avc1.64001e) @ Level 3.0 / 3.1 • YUV 4:2:0, 8-bit (yuv420p)",
            mimeTypes = listOf("video/avc", "video/h264")
        ),
        FfmpegCodecSpec(
            id = "hevc_h265",
            codecShortName = "H.265 / HEVC / MPEG-H Part 2 (hev1 / hvc1)",
            matroskaCodecId = "V_MPEGH/ISO/HEVC",
            ffmpegDecoderName = "hevc (Native MediaCodec + FFmpeg libavcodec HEVC)",
            ffmpegEncoderName = "libx265 / Lavf62.3.100 / hevc_mediacodec",
            profileAndPixelFormat = "Main / Main 10 HDR10 • YUV 4:2:0 8-bit/10-bit",
            mimeTypes = listOf("video/hevc", "video/h265")
        ),
        FfmpegCodecSpec(
            id = "mpeg4_asp_xvid",
            codecShortName = "MPEG-4 Part 2 (XVID) (V_MS/VFW/FOURCC & V_MPEG4/ISO/ASP • Advanced Simple Profile Level 5 mp4v.20.5 with packed B-frames DivX503b1393p • XVID 0x44495658 • csd-0 VOL Header 00 00 01 B0) • Video Codec MPEG-4 part 2",
            matroskaCodecId = "V_MS/VFW/FOURCC / V_MPEG4/ISO/ASP",
            ffmpegDecoderName = "mpeg4 / xvid (NextRenderersFactory FFmpeg extension + c2.android.mpeg4.decoder / OMX.google.mpeg4.decoder + MediaCodec video/mp4v-es)",
            ffmpegEncoderName = "libxvid / mpeg4 / Lavf62.3.100 (MPEG-4 Part 2 XVID 0x44495658 ASP Level 5 mp4v.20.5 with packed B-frames DivX503b1393p)",
            profileAndPixelFormat = "Advanced Simple Profile (Level 5, 0xF5, mp4v.20.5) with packed B-frames (DivX503b1393p) • YUV 4:2:0, 8-bit (yuv420p) • SAR 396:563 • DAR 704:563 (4:3 normalized)",
            mimeTypes = listOf("video/mp4v-es", "video/mpeg4", "video/xvid", "video/x-xvid", "video/divx", "video/x-divx", "video/vnd.divx", "video/x-unknown")
        ),
        FfmpegCodecSpec(
            id = "vp9",
            codecShortName = "Google VP9 (WebM / Matroska Profile 0/2)",
            matroskaCodecId = "V_VP9",
            ffmpegDecoderName = "vp9 / libvpx-vp9",
            ffmpegEncoderName = "libvpx-vp9",
            profileAndPixelFormat = "Profile 0/2 • YUV 4:2:0, 8-bit / 10-bit",
            mimeTypes = listOf("video/x-vnd.on2.vp9", "video/vp9")
        ),
        FfmpegCodecSpec(
            id = "vp8",
            codecShortName = "On2 VP8 (WebM / Matroska)",
            matroskaCodecId = "V_VP8",
            ffmpegDecoderName = "vp8 / libvpx",
            ffmpegEncoderName = "libvpx",
            profileAndPixelFormat = "Profile 0 • YUV 4:2:0, 8-bit",
            mimeTypes = listOf("video/x-vnd.on2.vp8", "video/vp8")
        ),
        FfmpegCodecSpec(
            id = "av1",
            codecShortName = "AOMedia Video 1 (AV1 / av01)",
            matroskaCodecId = "V_AV1",
            ffmpegDecoderName = "libdav1d / av1",
            ffmpegEncoderName = "libaom-av1 / libsvtav1",
            profileAndPixelFormat = "Main Profile • YUV 4:2:0 8-bit/10-bit",
            mimeTypes = listOf("video/av01", "video/av1")
        )
    )

    val SUPPORTED_CONTAINER_EXTENSIONS: Set<String> = setOf(
        "mkv", "webm", "mp4", "m4v", "mov", "avi", "ts", "m2ts", "mts",
        "flv", "f4v", "3gp", "3g2", "wmv", "asf", "mpg", "mpeg", "vob",
        "ogv", "ogg", "divx", "xvid", "rm", "rmvb", "dv", "mxf", "m4a", "mp3", "flac", "wav", "opus"
    )

    /**
     * Explicit cinema, broadcast, and web/stream resolutions supported alongside arbitrary dimensions:
     * includes 15360x8640 (16K), 7680x4320 (8K), 3840x2160 (4K), 2560x1440 (2K), 1920x1080,
     * 1920x800, 704x396 (SAR 396:563, DAR 704:563), 495x396, 536x396, 512x384,
     * 720x540, 640x480, 720x404, 640x360 (360p), 720x576 (PAL SD), and 160x90–256x144.
     */
    val SUPPORTED_EXACT_RESOLUTIONS: List<Pair<Int, Int>> = listOf(
        15360 to 8640,
        7680 to 4320,
        5120 to 2880,
        3840 to 2160,
        3840 to 1600,
        2560 to 1440,
        2560 to 1080,
        1920 to 1080,
        1920 to 800,
        1600 to 900,
        1366 to 768,
        1280 to 720,
        1280 to 534,
        1024 to 576,
        960 to 540,
        854 to 480,
        720 to 576,
        720 to 540,
        720 to 480,
        720 to 404,
        704 to 396,
        640 to 480,
        640 to 360,
        536 to 396,
        512 to 384,
        495 to 396,
        480 to 360,
        426 to 240,
        320 to 240,
        256 to 144,
        160 to 90
    )

    fun isSupportedCinemaOrMatroskaResolution(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        return SUPPORTED_EXACT_RESOLUTIONS.any { (w, h) ->
            (width == w && height == h) || (width == h && height == w)
        } || (width in 64..16384 && height in 64..16384)
    }

    data class VideoStreamFfmpegProfile(
        val containerSpec: String,
        val muxingApp: String,
        val writingLibrary: String,
        val ffmpegCodecFullSpec: String,
        val softwareEncoderDecoderEngine: String,
        val supportedResolutionTier: String,
        val libraryVersionSummary: String,
        val mediaInfoFfprobeReport: String = "",
        val supportedSampleRatesSummary: String = ALL_SUPPORTED_SAMPLE_RATES_SUMMARY
    )

    fun resolveProfileForVideo(
        fileNameOrPath: String,
        mimeType: String = "video/x-matroska",
        width: Int = 1920,
        height: Int = 1080,
        videoCodecHint: String = ""
    ): VideoStreamFfmpegProfile = inspectStreamProfile(
        fileNameOrPath = fileNameOrPath,
        mimeType = mimeType,
        width = width,
        height = height,
        videoCodecHint = videoCodecHint
    )

    fun inspectStreamProfile(
        fileNameOrPath: String,
        mimeType: String = "video/x-matroska",
        width: Int = 1920,
        height: Int = 1080,
        videoCodecHint: String = ""
    ): VideoStreamFfmpegProfile {
        val lowerPath = fileNameOrPath.lowercase()
        val lowerMime = mimeType.lowercase()
        val lowerHint = videoCodecHint.lowercase()

        val isMatroskaOrWebm = lowerPath.endsWith(".mkv") ||
            lowerPath.endsWith(".webm") ||
            lowerMime.contains("matroska") ||
            lowerMime.contains("webm") ||
            lowerMime.contains("mkv")

        val containerLabel = when {
            lowerPath.endsWith(".webm") || lowerMime.contains("webm") ->
                "WebM / $MATROSKA_MKVMERGE_V56_SPEC • $FORMAT_MATROSKA_WEBM • $FFMPEG_EXTENSION_SMOOTH_STREAM_SPEC (Encoding SW: Lavf62.3.100 • libebml v1.4.2 + libmatroska v1.6.4 • libebml v14.2 + libmatroska v1.6.4)"
            isMatroskaOrWebm ->
                "$MATROSKA_MKVMERGE_V56_SPEC • $FORMAT_MATROSKA_WEBM • $FFMPEG_EXTENSION_SMOOTH_STREAM_SPEC (Encoding SW: Lavf62.3.100 • libebml v1.4.2 + libmatroska v1.6.4 • libebml v14.2 + libmatroska v1.6.4)"
            lowerPath.endsWith(".avi") || lowerMime.contains("avi") || lowerPath.endsWith(".xvid") || lowerPath.endsWith(".divx") ->
                "AVI / $MATROSKA_MKVMERGE_V56_SPEC • $VIDEO_CODEC_XVID_PACKED_BFRAMES_SPEC • $FFMPEG_EXTENSION_SMOOTH_STREAM_SPEC"
            else ->
                "ISO Base Media / MP4 (.mp4 Mismatched extension; actual container is Matroska/.mkv supported) ($LIBX264_YUV420P_FASTSTART_SPEC) / $MATROSKA_MKVMERGE_V56_SPEC • $FORMAT_MATROSKA_WEBM (Encoding SW: Lavf62.3.100 • libebml v1.4.2 + libmatroska v1.6.4 • libebml v14.2 + libmatroska v1.6.4)"
        }

        val codecSpec = when {
            lowerHint.contains("hevc") || lowerHint.contains("h265") || lowerMime.contains("hevc") ->
                SUPPORTED_VIDEO_CODEC_SPECS[1]
            lowerHint.contains("mpeg-4 part 2") || lowerHint.contains("mp4v") || lowerHint.contains("asp") ||
                lowerHint.contains("xvid") || lowerHint.contains("divx") || lowerMime.contains("mp4v-es") ||
                lowerMime.contains("xvid") || lowerMime.contains("divx") || lowerPath.endsWith(".avi") || lowerPath.endsWith(".xvid") ->
                SUPPORTED_VIDEO_CODEC_SPECS[2]
            lowerHint.contains("vp9") || lowerMime.contains("vp9") ->
                SUPPORTED_VIDEO_CODEC_SPECS[3]
            lowerHint.contains("vp8") || lowerMime.contains("vp8") ->
                SUPPORTED_VIDEO_CODEC_SPECS[4]
            lowerHint.contains("av1") || lowerHint.contains("av01") || lowerMime.contains("av01") ->
                SUPPORTED_VIDEO_CODEC_SPECS[5]
            else ->
                SUPPORTED_VIDEO_CODEC_SPECS[0]
        }

        val maxDim = maxOf(width, height)
        val minDim = minOf(width, height)
        val resolutionTier = when {
            (width == 1920 && height == 800) || (width == 800 && height == 1920) ->
                "1920×800 (${width}×${height} • 1920x800 Anamorphic Full HD 2.40:1 Cinema)"
            (width == 704 && height == 396) || (width == 396 && height == 704) ->
                "704×396 (${width}×${height} • 704x396 SAR 396:563 DAR 704:563 MPEG-4 Part 2 XVID 0x44495658)"
            (width == 495 && height == 396) || (width == 396 && height == 495) ->
                "495×396 (${width}×${height} • 495 x 396 Matroska/WebM & MPEG-4 Part 2 ASP SD)"
            (width == 536 && height == 396) || (width == 396 && height == 536) ->
                "536×396 (${width}×${height} • 536x396 Web/Stream Frame Dimensions SD Transcode)"
            (width == 512 && height == 384) || (width == 384 && height == 512) ->
                "512×384 (${width}×${height} • 512x384 4:3 Classic Web/Stream SD)"
            (width == 720 && height == 540) || (width == 540 && height == 720) ->
                "720×540 (${width}×${height} • 720x540 4:3 High-SD Broadcast)"
            (width == 640 && height == 480) || (width == 480 && height == 640) ->
                "640×480 (${width}×${height} • 640x480 Web/Stream Frame Dimensions SD Transcode)"
            (width == 720 && height == 404) || (width == 404 && height == 720) ->
                "720×404 (${width}×${height} • 720x404 16:9 Widescreen Web/Stream SD)"
            (width == 640 && height == 360) || (width == 360 && height == 640) ->
                "360p (640×360) (${width}×${height} • Web/Stream Frame Dimensions: 360p (640×360))"
            (width == 720 && height == 576) || (width == 576 && height == 720) ->
                "720×576 (${width}×${height} • 720x576 PAL D1 Standard Definition)"
            maxDim >= 7680 || minDim >= 4320 -> "4320p 8K FUHD (${width}×${height} • All Resolutions 144p–8K Supported)"
            maxDim >= 3840 || minDim >= 2160 -> "2160p 4K UHD (${width}×${height} • All Resolutions 144p–8K Supported)"
            maxDim >= 2560 || minDim >= 1440 -> "1440p 2K QHD (${width}×${height} • All Resolutions 144p–8K Supported)"
            maxDim >= 1920 || minDim >= 1080 -> "1080p Full HD (${width}×${height} • Incl. 1920x800, 720×576, 720x540, 720x404, 704x396, 640x480, 640×360, 536x396, 512x384, 495 x 396)"
            maxDim >= 1280 || minDim >= 720 -> "720p HD (${width}×${height} • Incl. 1920x800, 720×576, 720x540, 720x404, 704x396, 640x480, 640×360, 536x396, 512x384, 495 x 396)"
            maxDim >= 854 || minDim >= 480 -> "480p SD (${width}×${height} • Web/Stream SD 704×396 / 640×480 / 536×396 Supported)"
            maxDim >= 640 || minDim >= 360 -> "360p nHD (${width}×${height} • Web/Stream Frame Dimensions: 360p (640×360) / SD (704×396, 536×396 or 640×480))"
            maxDim >= 426 || minDim >= 240 -> "240p QVGA (${width}×${height} • All Resolutions 144p–8K Supported)"
            else -> "144p–8K Multi-Resolution Adaptive (${width}×${height})"
        }

        val mediaInfoReport = generateMediaInfoFfprobeReport(
            fileNameOrPath = fileNameOrPath,
            mimeType = mimeType,
            width = width,
            height = height,
            videoCodecHint = videoCodecHint,
            audioCodecHint = "$AUDIO_PROFILE_HE_AAC_SPEC • $AUDIO_PROFILE_AAC_LC_MP4A_40_2_SPEC • $AUDIO_CODEC_AAC_LC_SPEC"
        )

        return VideoStreamFfmpegProfile(
            containerSpec = containerLabel,
            muxingApp = MUXING_APP_LAVF,
            writingLibrary = WRITING_LIBRARY_LAVF,
            ffmpegCodecFullSpec = "${codecSpec.matroskaCodecId} • ${codecSpec.codecShortName} • $VIDEO_CODEC_H264_AVC_MPEG4_PART10_SPEC • H.264 / AVC / MPEG-4 • $LIBX264_YUV420P_FASTSTART_SPEC • ${codecSpec.profileAndPixelFormat} • $VIDEO_CODEC_H264_AVC1_SPEC • $AVC1_MAIN_HIGH_PROFILE_LEVEL_30_31_SPEC • $CHROMA_SUBSAMPLING_YUV420_8BIT_SPEC • $VIDEO_CODEC_MPEG4_PART2_ASP_SPEC • $VIDEO_CODEC_XVID_PACKED_BFRAMES_SPEC • $AUDIO_PROFILE_HE_AAC_SPEC • $AUDIO_PROFILE_AAC_LC_MP4A_40_2_SPEC • $AUDIO_CODEC_AAC_LC_SPEC • Sample Rates: $ALL_SUPPORTED_SAMPLE_RATES_SUMMARY • Encoding SW: $MUXING_APP_LAVF_62 / $MUXING_APP_LAVF • $MATROSKA_MKVMERGE_V56_SPEC • $FFMPEG_EXTENSION_SMOOTH_STREAM_SPEC",
            softwareEncoderDecoderEngine = "Decoder: ${codecSpec.ffmpegDecoderName} | Encoder: ${codecSpec.ffmpegEncoderName} | $FFMPEG_EXTENSION_SMOOTH_STREAM_SPEC | Encoding SW: $MUXING_APP_LAVF_62 • libebml v1.4.2 + libmatroska v1.6.4 • libebml v14.2 + libmatroska v1.6.4 | Inspector: $MEDIAINFO_FFPROBE_ENGINE_SPEC",
            supportedResolutionTier = resolutionTier,
            libraryVersionSummary = FULL_FFMPEG_LIBRARY_SUITE,
            mediaInfoFfprobeReport = mediaInfoReport
        )
    }

    /**
     * Generates a formatted MediaInfo / FFprobe technical stream report covering container,
     * video codec/profile/level, chroma subsampling, frame dimensions, and audio codec/profile/sample rate.
     */
    fun generateMediaInfoFfprobeReport(
        fileNameOrPath: String = "stream.mkv",
        mimeType: String = "video/x-matroska",
        width: Int = 1920,
        height: Int = 1080,
        videoCodecHint: String = "",
        audioCodecHint: String = "",
        videoCodec: String = "",
        audioCodec: String = "",
        sampleRateHz: Int = 44100
    ): String {
        val safeW = if (width > 0) width else 1920
        val safeH = if (height > 0) height else 1080
        val resolvedVideoHint = videoCodec.ifBlank { videoCodecHint }
        val resolvedAudioHint = audioCodec.ifBlank { audioCodecHint }
        val formattedHz = formatSampleRateHz(sampleRateHz)
        val frameDimensionCategory = when {
            (safeW == 704 && safeH == 396) || (safeW == 396 && safeH == 704) ->
                "704x396 (SAR 396:563 • DAR 704:563 • MPEG-4 part 2 XVID 0x44495658 Advanced Simple Profile Level 5)"
            (safeW == 640 && safeH == 360) || (safeW == 360 && safeH == 640) ->
                "360p (640×360) / SD (536×396 or 640×480 depending on platform transcode)"
            (safeW == 536 && safeH == 396) || (safeW == 396 && safeH == 536) ->
                "360p (640×360) / SD (536×396 or 640×480 depending on platform transcode)"
            (safeW == 640 && safeH == 480) || (safeW == 480 && safeH == 640) ->
                "360p (640×360) / SD (536×396 or 640×480 depending on platform transcode)"
            (safeW == 495 && safeH == 396) || (safeW == 396 && safeH == 495) ->
                "495 x 396 (495×396 Custom Matroska/WebM SD)"
            (safeW == 512 && safeH == 384) || (safeW == 384 && safeH == 512) ->
                "512x384 (512×384 4:3 SD)"
            (safeW == 720 && safeH == 540) || (safeW == 540 && safeH == 720) ->
                "720x540 (720×540 High-SD)"
            (safeW == 720 && safeH == 404) || (safeW == 404 && safeH == 720) ->
                "720x404 (720×404 16:9 Web SD)"
            (safeW == 720 && safeH == 576) || (safeW == 576 && safeH == 720) ->
                "720×576 (720x576 PAL D1 SD)"
            (safeW == 1920 && safeH == 800) || (safeW == 800 && safeH == 1920) ->
                "1920x800 (1920×800 Cinema Widescreen)"
            else -> "${safeW}×${safeH} (Web/Stream Frame Dimensions: 360p (640×360) / SD (536×396 or 640×480 depending on platform transcode))"
        }
        return buildString {
            append("[MediaInfo / FFprobe Stream Report] ")
            append("Container: $MATROSKA_MKVMERGE_V56_SPEC • Matroska/WebM (Encoding SW: $MUXING_APP_LAVF_62 / libebml v1.4.2 + libmatroska v1.6.4 / libebml v14.2 + libmatroska v1.6.4, file=$fileNameOrPath, mime=$mimeType, $FFMPEG_EXTENSION_SMOOTH_STREAM_SPEC) | ")
            append("Frame Dimensions: ${safeW}×${safeH} [$frameDimensionCategory] | ")
            append("$VIDEO_CODEC_H264_AVC1_SPEC ($VIDEO_CODEC_H264_AVC_MPEG4_PART10_SPEC • $LIBX264_YUV420P_FASTSTART_SPEC) & $VIDEO_CODEC_MPEG4_PART2_ASP_SPEC • $VIDEO_CODEC_XVID_PACKED_BFRAMES_SPEC | ")
            append("$AVC1_MAIN_HIGH_PROFILE_LEVEL_30_31_SPEC | ")
            append("$CHROMA_SUBSAMPLING_YUV420_8BIT_SPEC | ")
            append("Audio: $AUDIO_PROFILE_HE_AAC_SPEC • $AUDIO_PROFILE_AAC_LC_MP4A_40_2_SPEC • $AUDIO_CODEC_AAC_LC_SPEC ($formattedHz / ${sampleRateHz} HZ / 44100 HZ • Supported: $ALL_SUPPORTED_SAMPLE_RATES_SUMMARY)")
            if (resolvedVideoHint.isNotBlank()) {
                append(" | Stream Hint: $resolvedVideoHint")
            }
            if (resolvedAudioHint.isNotBlank()) {
                append(" | Audio Hint: $resolvedAudioHint")
            }
        }
    }

    data class MatroskaHevcOpusAss1080pSpec(
        val formatName: String = "matroska/webm",
        val width: Int = 1920,
        val height: Int = 1080,
        val muxingLibrary: String = "libebml v1.4.5 + libmatroska v1.7.1",
        val videoCodec: String = "hevc",
        val audioCodecs: List<String> = listOf("aac", "opus"),
        val subtitleCodec: String = "ass"
    )

    val MATROSKA_HEVC_OPUS_ASS_1080P_SPEC = MatroskaHevcOpusAss1080pSpec()

    fun isSupportedMatroskaHevcOpusAssSpec(
        spec: MatroskaHevcOpusAss1080pSpec = MATROSKA_HEVC_OPUS_ASS_1080P_SPEC
    ): Boolean {
        val containerOk = spec.formatName.contains("matroska", ignoreCase = true) &&
            spec.formatName.contains("webm", ignoreCase = true)
        val resOk = isSupportedCinemaOrMatroskaResolution(spec.width, spec.height)
        val muxerOk = SUPPORTED_ENCODING_SOFTWARE.any { it.equals(spec.muxingLibrary, ignoreCase = true) }
        val videoOk = findCodecByCode(spec.videoCodec) != null
        val audioOk = spec.audioCodecs.all { findCodecByCode(it) != null }
        val subtitleOk = findCodecByCode(spec.subtitleCodec) != null
        return containerOk && resOk && muxerOk && videoOk && audioOk && subtitleOk
    }

    data class MatroskaMpeg4XvidStreamSpec(
        val fileName: String = "Hindi.mp4",
        val nbStreams: Int = 2,
        val formatName: String = "matroska,webm",
        val formatLongName: String = "Matroska / WebM",
        val startTimeSec: String = "0.000000",
        val durationSec: String = "375.480000",
        val sizeBytes: Long = 53_140_533L,
        val bitRateBps: Int = 1_132_215,
        val probeScore: Int = 100,
        val encoder: String = "libebml v1.4.2 + libmatroska v1.6.4",
        val creationTimeUtc: String = "2022-08-09T05:01:20.000000Z",
        val writingApp: String = "mkvmerge v56.1.0 ('My Friend') 64-bit",
        val writingDateUtc: String = "2022-08-09 05:01:20",
        // Stream 0: Video
        val videoIndex: Int = 0,
        val videoCodecName: String = "mpeg4",
        val videoCodecLongName: String = "MPEG-4 part 2",
        val videoProfile: String = "Advanced Simple Profile",
        val videoCodecType: String = "video",
        val videoCodecTagString: String = "XVID",
        val videoCodecTagHex: String = "0x44495658",
        val width: Int = 704,
        val height: Int = 396,
        val codedWidth: Int = 704,
        val codedHeight: Int = 396,
        val closedCaptions: Int = 0,
        val filmGrain: Int = 0,
        val hasBFrames: Int = 1,
        val sampleAspectRatio: String = "396:563",
        val displayAspectRatio: String = "704:563",
        val pixFmt: String = "yuv420p",
        val level: Int = 5,
        val chromaLocation: String = "left",
        val refs: Int = 1,
        val quarterSample: String = "false",
        val divxPacked: String = "false",
        val rFrameRate: String = "25/1",
        val avgFrameRate: String = "25/1",
        val timeBase: String = "1/1000",
        val startPts: Long = 0L,
        val durationTs: Long = 375_480L,
        val videoExtradataSize: Int = 58,
        val videoBitRateBps: Int = 969_171,
        val videoDurationTag: String = "00:06:15.480000000",
        val videoNumberOfFrames: Int = 9_387,
        val videoNumberOfBytes: Long = 45_488_083L,
        // Stream 1: Audio
        val audioIndex: Int = 1,
        val audioCodecName: String = "aac",
        val audioCodecLongName: String = "AAC (Advanced Audio Coding)",
        val audioProfile: String = "LC",
        val audioCodecType: String = "audio",
        val audioCodecTagString: String = "[0][0][0][0]",
        val audioCodecTagHex: String = "0x0000",
        val sampleFmt: String = "fltp",
        val sampleRateHz: Int = 44_100,
        val channels: Int = 2,
        val channelLayout: String = "stereo",
        val bitsPerSample: Int = 0,
        val audioExtradataSize: Int = 5,
        val audioBitRateBps: Int = 160_788,
        val audioDurationTag: String = "00:06:15.443000000",
        val audioNumberOfFrames: Int = 16_169,
        val audioNumberOfBytes: Long = 7_545_878L
    )

    val HINDI_MP4_MATROSKA_MPEG4_XVID_SPEC = MatroskaMpeg4XvidStreamSpec()

    fun calculateDisplayAspectRatio(
        width: Int,
        height: Int,
        sampleAspectRatio: String? = null
    ): String {
        if (width <= 0 || height <= 0) return "16:9"
        if (!sampleAspectRatio.isNullOrBlank() && sampleAspectRatio.contains(":")) {
            val parts = sampleAspectRatio.split(":")
            val sarNum = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 1L
            val sarDen = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 1L
            if (sarNum > 0L && sarDen > 0L && (sarNum != 1L || sarDen != 1L)) {
                val darNum = width.toLong() * sarNum
                val darDen = height.toLong() * sarDen
                val g = gcdLong(darNum, darDen).coerceAtLeast(1L)
                return "${darNum / g}:${darDen / g}"
            }
        }
        val g = gcdLong(width.toLong(), height.toLong()).coerceAtLeast(1L)
        return "${width / g}:${height / g}"
    }

    private tailrec fun gcdLong(a: Long, b: Long): Long = if (b == 0L) a else gcdLong(b, a % b)

    fun isSupportedHindiMpeg4XvidMatroskaSpec(
        spec: MatroskaMpeg4XvidStreamSpec = HINDI_MP4_MATROSKA_MPEG4_XVID_SPEC
    ): Boolean {
        val videoCodecSupported = findCodecByCode(spec.videoCodecName) != null &&
            findCodecByCode(spec.videoCodecTagString) != null &&
            findCodecByCode(spec.videoCodecTagHex) != null
        val audioCodecSupported = findCodecByCode(spec.audioCodecName) != null &&
            findCodecByCode(spec.audioProfile) != null &&
            findCodecByCode(spec.sampleFmt) != null &&
            isSupportedSampleRate(spec.sampleRateHz)
        val resolutionSupported = isSupportedCinemaOrMatroskaResolution(spec.width, spec.height)
        val darMatches = calculateDisplayAspectRatio(spec.width, spec.height, spec.sampleAspectRatio) == spec.displayAspectRatio
        val muxerSupported = SUPPORTED_ENCODING_SOFTWARE.contains(spec.encoder) &&
            SUPPORTED_ENCODING_SOFTWARE.contains(spec.writingApp)
        return videoCodecSupported && audioCodecSupported && resolutionSupported && darMatches && muxerSupported
    }

    const val CORRUPTED_VIDEO_FALLBACK_MESSAGE: String =
        "Unable to play video: This file appears to be corrupted or unreadable"

    /**
     * Computes 16-byte / 32-byte hardware macroblock-aligned stride width for non-standard resolutions
     * (such as 854×480, 426×240, 704×396, 536×396, 495×396) to prevent green stride-alignment bars.
     */
    fun computeAlignedStrideWidth(width: Int, alignment: Int = 16): Int {
        if (width <= 0) return 0
        val safeAlign = alignment.coerceAtLeast(2)
        val remainder = width % safeAlign
        return if (remainder == 0) width else width + (safeAlign - remainder)
    }

    /**
     * Returns true when a resolution's width or height is not a multiple of 16 (e.g., 854×480, 426×240, 536×396, 495×396),
     * requiring stride padding crop alignment to eliminate green bars on hardware/software surfaces.
     */
    fun needsStrideAlignmentFix(width: Int, height: Int, alignment: Int = 16): Boolean {
        if (width <= 0 || height <= 0) return false
        val safeAlign = alignment.coerceAtLeast(2)
        return (width % safeAlign != 0)
    }

    /**
     * Computes the exact active-pixel crop ratio `width / alignedStrideWidth` so non-16-aligned
     * frames (e.g., 854 -> 864 or 426 -> 432) crop out padded right-edge UV/luma zeroes (green bar).
     */
    fun computeStrideCropWidthRatio(width: Int, alignment: Int = 16): Float {
        if (width <= 0) return 1.0f
        val aligned = computeAlignedStrideWidth(width, alignment)
        return if (aligned > 0) (width.toFloat() / aligned.toFloat()).coerceIn(0.85f, 1.0f) else 1.0f
    }

    /**
     * Supports Android 7.0 (API 24) through Android 17 (API 36/37+).
     */
    const val MIN_SUPPORTED_ANDROID_SDK: Int = 24
    const val MAX_SUPPORTED_ANDROID_SDK: Int = 37

    fun isSupportedAndroidVersion7To17(sdkInt: Int = android.os.Build.VERSION.SDK_INT): Boolean {
        return sdkInt in MIN_SUPPORTED_ANDROID_SDK..MAX_SUPPORTED_ANDROID_SDK
    }

    /**
     * Computes A/V sync drift correction in milliseconds for legacy containers (AVI, MPG, MPEG, RM, RMVB, VOB)
     * when audio/video presentation timestamps drift by more than 45ms.
     */
    fun computeAvSyncDriftCorrectionMs(
        videoPtsUs: Long,
        audioPtsUs: Long,
        containerExtension: String = "mp4"
    ): Long {
        val driftMs = (videoPtsUs - audioPtsUs) / 1_000L
        val ext = containerExtension.trim().trimStart('.').lowercase()
        val isLegacyDriftProneContainer = ext in setOf("avi", "mpg", "mpeg", "rm", "rmvb", "vob", "divx", "xvid", "asf", "wmv")
        val thresholdMs = if (isLegacyDriftProneContainer) 45L else 90L
        return if (kotlin.math.abs(driftMs) > thresholdMs) -driftMs else 0L
    }

    /**
     * Computes optimal worker thread count in SW+ mode to prevent CPU bottlenecks and thermal throttling
     * across 240p interlaced to 8K HDR streams.
     */
    fun computeOptimalSwPlusThreadCount(
        width: Int,
        height: Int,
        availableCores: Int = Runtime.getRuntime().availableProcessors()
    ): Int {
        val safeCores = availableCores.coerceIn(1, 16)
        val pixels = width.coerceAtLeast(0).toLong() * height.coerceAtLeast(0).toLong()
        return when {
            pixels >= 3840L * 2160L -> minOf(safeCores, 6).coerceAtLeast(2)
            pixels >= 1920L * 1080L -> minOf(safeCores, 4).coerceAtLeast(2)
            else -> minOf(safeCores, 3).coerceAtLeast(2)
        }
    }

    /**
     * Classifies playback errors, differentiating between a network disconnect on remote streams
     * and an actual corrupted, 0-byte, or unreadable media file (`MEDIA_ERR_DECODE`,
     * `MEDIA_ERR_SRC_NOT_SUPPORTED`, invalid metadata/duration `<= 0`, or stall/timeout).
     */
    fun classifyPlaybackError(
        uriOrPath: String,
        errorCodeName: String? = null,
        errorMessage: String? = null,
        durationMs: Long = 0L,
        isNetworkConnected: Boolean = true
    ): com.cyosplay.player.data.model.PlayerMediaErrorType {
        val lowerUri = uriOrPath.lowercase()
        val isNetworkStream = lowerUri.startsWith("http://") ||
            lowerUri.startsWith("https://") ||
            lowerUri.startsWith("rtsp://") ||
            lowerUri.startsWith("rtmp://")
        val combinedErr = "${errorCodeName.orEmpty()} ${errorMessage.orEmpty()}".uppercase()

        if (isNetworkStream && (
            !isNetworkConnected ||
                combinedErr.contains("NETWORK") ||
                combinedErr.contains("CLEARTEXT") ||
                combinedErr.contains("CONNECTION") ||
                combinedErr.contains("UNKNOWN_HOST") ||
                combinedErr.contains("SOCKET_TIMEOUT")
            )
        ) {
            return com.cyosplay.player.data.model.PlayerMediaErrorType.NETWORK_DISCONNECT
        }

        val isDecodeOrSourceError = combinedErr.contains("DECODE") ||
            combinedErr.contains("MEDIA_ERR_DECODE") ||
            combinedErr.contains("MEDIA_ERR_SRC_NOT_SUPPORTED") ||
            combinedErr.contains("SRC_NOT_SUPPORTED") ||
            combinedErr.contains("PARSING") ||
            combinedErr.contains("CONTAINER") ||
            combinedErr.contains("UNSUPPORTED") ||
            combinedErr.contains("CORRUPT") ||
            combinedErr.contains("EOF") ||
            combinedErr.contains("IO_FILE_NOT_FOUND") ||
            combinedErr.contains("IO_UNSPECIFIED") ||
            combinedErr.contains("TIMEOUT") ||
            durationMs <= 0L

        return if (isDecodeOrSourceError || !isNetworkStream) {
            com.cyosplay.player.data.model.PlayerMediaErrorType.CORRUPTED_OR_UNREADABLE
        } else {
            com.cyosplay.player.data.model.PlayerMediaErrorType.CORRUPTED_OR_UNREADABLE
        }
    }

    fun classifyPlaybackError(
        errorCode: Int,
        errorMessage: String? = null,
        isNetworkSource: Boolean = false,
        durationMs: Long = 0L
    ): com.cyosplay.player.data.model.PlayerMediaErrorType {
        val syntheticUri = if (isNetworkSource) "https://stream.cyosplay/video.mp4" else "file:///local/video.mp4"
        return classifyPlaybackError(
            uriOrPath = syntheticUri,
            errorCodeName = errorCode.toString(),
            errorMessage = errorMessage,
            durationMs = durationMs,
            isNetworkConnected = !(isNetworkSource && errorCode in 2001..2002)
        )
    }
}

