package com.cyosplay.player.data.model

import android.net.Uri

enum class NavDestination(val label: String) {
    FOLDERS("Folders"),
    VIDEOS("Videos"),
    PLAYLISTS("Playlists"),
    VAULT("Vault"),
    SETTINGS("Settings")
}

enum class VideoSortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Largest Size"),
    SIZE_ASC("Smallest Size"),
    DURATION_DESC("Longest Duration"),
    DURATION_ASC("Shortest Duration"),
    RESOLUTION_DESC("Highest Resolution"),
    FORMAT_ASC("Format / Codec (A-Z)")
}

enum class FolderSortOption(val label: String) {
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    NAME_ASC("Folder Name (A-Z)"),
    NAME_DESC("Folder Name (Z-A)"),
    COUNT_DESC("Most Videos"),
    COUNT_ASC("Fewest Videos"),
    SIZE_DESC("Largest Total Size"),
    SIZE_ASC("Smallest Total Size"),
    RECENT_DESC("Recently Updated"),
    RECENT_ASC("Oldest Updated")
}

enum class BrowserViewMode(val label: String, val shortLabel: String) {
    LIST("List View", "List"),
    GRID("Grid View", "Grid"),
    COMPACT("Compact Rows", "Compact"),
    DETAILED_CARDS("Detailed Cards", "Detailed"),
    GALLERY_CARDS("Gallery Showcase", "Gallery"),
    CINEMA_STRIP("Cinema Widescreen", "Cinema"),
    MINIMAL_LIST("Minimal Clean", "Minimal")
}

enum class SortDirection(val label: String) {
    DESCENDING("Descending"),
    ASCENDING("Ascending")
}

enum class BrowserLayoutColumns(val label: String, val columnsCount: Int) {
    AUTO("Adaptive Auto", 0),
    ONE_COLUMN("1 Wide Column", 1),
    TWO_COLUMNS("2 Columns", 2),
    THREE_COLUMNS("3 Columns", 3),
    FOUR_COLUMNS("4 Dense Columns", 4)
}

data class FolderBrowserConfig(
    val viewMode: BrowserViewMode = BrowserViewMode.LIST,
    val sortOption: VideoSortOption = VideoSortOption.DATE_DESC,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
    val layoutColumns: BrowserLayoutColumns = BrowserLayoutColumns.AUTO,
    val thumbnailRatio: BrowserThumbnailRatio = BrowserThumbnailRatio.WIDESCREEN_16_9,
    val lastScrollIndex: Int = 0,
    val lastScrollOffset: Int = 0
)

enum class BrowserThumbnailRatio(val label: String, val aspectRatio: Float) {
    WIDESCREEN_16_9("16:9 Widescreen", 16f / 9f),
    CINEMA_21_9("21:9 Ultrawide", 21f / 9f),
    CLASSIC_4_3("4:3 Classic TV", 4f / 3f),
    SQUARE_1_1("1:1 Square", 1f)
}

enum class AdvancedCodecFilter(val label: String) {
    ALL("All Codecs & Formats"),
    MATROSKA_WEBM("MKV / WebM High-Bitrate Container"),
    H264_AVC("H.264 / AVC / MPEG-4 part 10 / MP4"),
    HEVC_X265("H.265 / HEVC 10-Bit"),
    AV1_VP9("AV1 / VP9 Next-Gen"),
    MPEG4_XVID("Video Codec MPEG-4 part 2 / ASP / AAC / LC / 44100 HZ")
}

enum class ResolutionFilterTier(val label: String) {
    ALL("All 55+ Resolutions (160×90 → 480p → 1080p → 4K → 8K → 16K)"),
    UHD_8K_4K("16K / 8K / 6K / 5K / 4K UHD (≥2160p)"),
    QHD_2K_3K("3K / 2K QHD (2048×1152 → 3200×2400)"),
    FHD_1080P("1080p / 1200p Full HD (1920×1080 / 1920×1200 / 1920×800)"),
    HD_720P("720p / WXGA / SXGA (1024×768 → 1680×1050)"),
    SD_LEGACY("480p / 360p / 240p / 144p / SD (160×90 → 960×640)")
}

enum class BrowserControlTab(val label: String) {
    VIEW_MODE("View Mode"),
    SORT("Sort"),
    LAYOUT("Layout"),
    ADVANCED("Advanced"),
    FIELDS("Fields")
}

data class BrowserVisibleFields(
    val showVideoPicturePreview: Boolean = true,
    val showFolderCollagePreview: Boolean = true,
    val showResolutionBadge: Boolean = true,
    val showContainerBadge: Boolean = true,
    val showCodecBadges: Boolean = true,
    val showBitrateAndFps: Boolean = true,
    val showFileSize: Boolean = true,
    val showDuration: Boolean = true,
    val showFolderPath: Boolean = true,
    val showMatroskaEbmlTag: Boolean = true,
    val showEncryptedDatacodeBadge: Boolean = true
) {
    val activeCount: Int
        get() = listOf(
            showVideoPicturePreview,
            showFolderCollagePreview,
            showResolutionBadge,
            showContainerBadge,
            showCodecBadges,
            showBitrateAndFps,
            showFileSize,
            showDuration,
            showFolderPath,
            showMatroskaEbmlTag,
            showEncryptedDatacodeBadge
        ).count { it }
}

enum class FolderExplorerMode(val label: String) {
    FLAT_FOLDERS("Media Folders"),
    DIRECTORY_TREE("SD Hierarchy")
}

enum class StorageVolumeFilter(val label: String) {
    ALL_STORAGE("All Storage"),
    INTERNAL_ONLY("Internal Storage"),
    SD_CARD_ONLY("SD Card")
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit Screen"),
    FILL("Fill / Crop"),
    STRETCH("Stretch 16:9"),
    ORIGINAL("100% Original")
}

enum class RepeatMode(val label: String) {
    NONE("No Repeat"),
    REPEAT_ONE("Repeat Current"),
    REPEAT_ALL("Loop Folder")
}

enum class PlayerOrientationMode(val label: String) {
    AUTO_VIDEO("Smart Auto"),
    LANDSCAPE_SENSOR("Landscape"),
    PORTRAIT("Portrait"),
    SYSTEM_UNLOCKED("System Sensor")
}

enum class DecoderEngineMode(val label: String, val badge: String) {
    HW("HW Hardware Decoder", "HW"),
    HW_PLUS("HW+ Enhanced Decoder (Auto-Fallback)", "HW+"),
    AUTO_HW_PLUS("HW+ Auto (Fallback SW)", "HW+"),
    SW("SW Software Decoder", "SW"),
    SW_PLUS("SW+ Multi-Core Software Decoder", "SW+"),
    SOFTWARE_MULTI_CORE("SW+ Multi-Core Decoder", "SW+");

    /**
     * Implements the 4-stage master decoder fallback chain:
     * HW -> HW+ -> SW+ -> SW -> null (exhausted / unreadable).
     */
    fun nextFallbackMode(): DecoderEngineMode? = when (this) {
        HW -> HW_PLUS
        HW_PLUS, AUTO_HW_PLUS -> SW_PLUS
        SW_PLUS, SOFTWARE_MULTI_CORE -> SW
        SW -> null
    }
}

enum class PlayerMediaErrorType {
    NONE,
    NETWORK_DISCONNECT,
    CORRUPTED_OR_UNREADABLE
}

enum class MiniPlayerPositionPreset(
    val label: String,
    val shortLabel: String,
    val defaultOffsetX: Float,
    val defaultOffsetY: Float
) {
    BOTTOM_CENTER("Bottom Center", "Bottom", 0f, 0f),
    BOTTOM_LEFT("Bottom Left", "B-Left", -220f, 0f),
    BOTTOM_RIGHT("Bottom Right", "B-Right", 220f, 0f),
    CENTER("Center Screen", "Center", 0f, -420f),
    TOP_LEFT("Top Left", "T-Left", -220f, -820f),
    TOP_RIGHT("Top Right", "T-Right", 220f, -820f),
    CUSTOM("Custom Free Position", "Custom", 0f, 0f)
}

enum class SmartEnhancementMode(
    val label: String,
    val description: String,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val brightnessOffset: Float = 0f
) {
    OFF("Original", "Zero post-processing", 1.0f, 1.0f, 0f),
    CINEMA_HDR("HDR+ Dynamic", "Deep contrast, HDR tone mapping & shadow detail", 1.18f, 1.22f, 4f),
    SHARP_DETAIL("4K Crisp", "Enhanced micro-contrast & edge clarity for 720p/1080p", 1.24f, 1.12f, 2f),
    ULTRA_8K_CLARITY("8K Ultra Sharp", "Maximum clarity & dynamic luminance for 4K/8K", 1.30f, 1.18f, 3f),
    VIVID_AMOLED("Vivid AMOLED", "Rich color vibrancy & punchy highlights", 1.15f, 1.38f, 2f),
    NIGHT_VISION("Night Boost", "Lifts dark shadow details for low-light scenes", 1.12f, 1.08f, 22f),
    ECO_BATTERY("Battery Saver", "Reduced refresh rate & power-capped luminance", 0.96f, 0.95f, -6f)
}

enum class SleepTimerOption(val label: String, val minutes: Int) {
    OFF("Off", 0),
    MIN_15("15 min", 15),
    MIN_30("30 min", 30),
    MIN_45("45 min", 45),
    MIN_60("60 min", 60),
    END_OF_VIDEO("End of Video", -1)
}

enum class SubtitleFontSize(val label: String, val spValue: Float, val scaleFactor: Float) {
    SMALL("Small", 18f, 0.78f),
    NORMAL("Medium", 22f, 1.00f),
    LARGE("Large", 26f, 1.22f),
    EXTRA_LARGE("Extra Large XL", 32f, 1.45f),
    ULTRA_XXL("Ultra XXL", 38f, 1.72f);

    /**
     * Computes responsive subtitle font size following `clamp(1.25rem, 2.5vw, 2rem)`
     * (20sp..32sp base clamped by 2.5% of viewport width) scaled by the user's size preset,
     * or custom manual sp setting up to 64sp.
     */
    fun computeResponsiveSp(viewportWidthDp: Float, customSpOverride: Float? = null): Float {
        if (customSpOverride != null) {
            return customSpOverride.coerceIn(12f, 64f)
        }
        val baseClamped = if (viewportWidthDp > 0f) {
            (viewportWidthDp * 0.025f).coerceIn(20f, 32f)
        } else {
            20f
        }
        return (baseClamped * scaleFactor).coerceIn(15f, 64f)
    }
}

enum class SubtitleColorOption(val label: String, val hexColor: Long) {
    WHITE("Pure White", 0xFFFFFFFF),
    YELLOW("Warm Yellow", 0xFFFFEB3B),
    CYAN("Aqua Cyan", 0xFF00E5FF),
    GREEN("Soft Mint", 0xFF69F0AE),
    AMBER_GOLD("Amber Gold", 0xFFFFB74D),
    CORAL_ROSE("Coral Rose", 0xFFFF6E7F),
    LAVENDER("Lavender", 0xFFD1C4E9),
    SKY_BLUE("Sky Blue", 0xFF81D4FA)
}

enum class SubtitleOutlineColorOption(val label: String, val hexColor: Long) {
    BLACK("Deep Black", 0xFF000000),
    NAVY("Dark Navy", 0xFF0D1B2A),
    CHARCOAL("Charcoal", 0xFF263238)
}

enum class SubtitleEdgeStyle(val label: String) {
    DROP_SHADOW("Drop Shadow"),
    OUTLINE_BOX("Translucent Pill"),
    HIGH_CONTRAST("Bold Outline")
}

enum class SubtitleVerticalPreset(val label: String, val bottomPaddingDp: Int) {
    LOW("Bottom Edge", 18),
    STANDARD("Standard", 42),
    RAISED("Raised Above HUD", 76)
}

enum class AppThemeMode(val label: String) {
    DARK_CINEMA("Obsidian Dark"),
    AMOLED_BLACK("Pure AMOLED Black"),
    LIGHT_DAYLIGHT("Daylight Clean"),
    SYSTEM_AUTO("Mobile Default"),
    CYBER_NEON_BLUE("Cyber Neon Blue"),
    ROYAL_AMETHYST("Royal Amethyst"),
    EMERALD_MATRIX("Emerald Matrix"),
    CRIMSON_VELVET("Crimson Velvet"),
    TITANIUM_SLATE("Titanium Slate"),
    SOLAR_GOLD("Solar Gold")
}

enum class AppFontMode(val label: String) {
    SYSTEM_DEFAULT("Mobile Default"),
    CINEMA_SANS("Plus Jakarta Sans"),
    MODERN_INTER("Inter Precision"),
    TECHNICAL_MONO("Roboto Technical"),
    GEOMETRIC_POPPINS("Poppins Geometric"),
    CINEMA_MONTSERRAT("Montserrat Cinema"),
    HUMANIST_LATO("Lato Humanist"),
    ROUNDED_NUNITO("Nunito Rounded"),
    DEVELOPER_FIRA_CODE("Fira Code Mono")
}

enum class AdaptiveTextScaleMode(val label: String, val scaleMultiplier: Float) {
    ULTRA_COMPACT("Ultra Compact (85%)", 0.85f),
    COMPACT("Compact (90%)", 0.90f),
    NORMAL("Mobile Default (100%)", 1.00f),
    LARGE("Comfort Large (112%)", 1.12f),
    EXTRA_LARGE("Max Readability (124%)", 1.24f),
    CINEMA_BOLD("Cinema XL (130%)", 1.30f)
}

enum class MediaManagerFilter(val label: String) {
    ALL("All Videos"),
    LARGE_FILES("Large (> 100 MB)"),
    ULTRA_HD("8K / 4K / 1080p"),
    UNWATCHED("Unwatched"),
    DUPLICATES("Potential Duplicates")
}

enum class HudType {
    BRIGHTNESS,
    VOLUME,
    SEEK
}

enum class RippleSide {
    LEFT,
    RIGHT
}

enum class VaultAuthState {
    UNINITIALIZED_NO_PIN,
    LOCKED,
    UNLOCKED_REAL,
    UNLOCKED_DECOY
}

enum class HoldClipboardOperation {
    COPY,
    MOVE
}

data class VideoClipboardState(
    val sourceVideos: List<VideoItem>,
    val operation: HoldClipboardOperation,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class MoveDestinationOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val targetPath: String,
    val isPrivateVault: Boolean = false,
    val isRemovableSd: Boolean = false,
    val videoCount: Int = 0
)

data class PendingTransferRequest(
    val operation: HoldClipboardOperation,
    val destination: MoveDestinationOption,
    val customNewFolderName: String = "",
    val targetVideos: List<VideoItem> = emptyList()
) {
    val isMove: Boolean get() = operation == HoldClipboardOperation.MOVE
}

data class VideoItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val uri: Uri,
    val path: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateModifiedSeconds: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val folderId: String,
    val folderName: String,
    val folderPath: String,
    val isRemovableStorage: Boolean = false,
    val audioCodecHint: String = "AAC 2.0",
    val videoCodecHint: String = "H.264",
    val sampleAspectRatio: String = "1:1",
    val displayAspectRatio: String = "",
    val isNewVideo: Boolean = false
) {
    val resolutionBadge: String
        get() = when {
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
            maxOf(width, height) >= 15000 || minOf(width, height) >= 8400 -> "16K"
            maxOf(width, height) >= 7600 || minOf(width, height) >= 4300 -> "8K"
            maxOf(width, height) in 6000..7599 || minOf(width, height) in 4000..4299 -> "6K"
            maxOf(width, height) in 5000..5999 || minOf(width, height) in 2700..3999 -> "5K"
            maxOf(width, height) >= 3800 || minOf(width, height) >= 2100 -> "4K"
            maxOf(width, height) in 3100..3799 -> "3K"
            maxOf(width, height) >= 2000 -> "2K"
            maxOf(width, height) >= 1900 -> "1080P"
            maxOf(width, height) >= 1260 -> "720P"
            (maxOf(width, height) in 640..1152 && minOf(width, height) in 400..864) || minOf(width, height) == 480 -> "480P"
            maxOf(width, height) >= 700 && minOf(width, height) >= 390 -> "396P"
            maxOf(width, height) >= 600 && minOf(width, height) >= 350 -> "360P"
            maxOf(width, height) >= 300 && minOf(width, height) >= 220 -> "240P"
            maxOf(width, height) >= 240 && minOf(width, height) >= 140 -> "144P"
            else -> "480P"
        }

    val isSupportedResolutionSpec: Boolean
        get() = Companion.isSupportedVideoResolution(width, height)

    companion object {
        val SUPPORTED_VIDEO_RESOLUTIONS: Set<Pair<Int, Int>> = setOf(
            160 to 90,
            160 to 120,
            176 to 144,
            240 to 160,
            256 to 144,
            320 to 180,
            320 to 240,
            352 to 240,
            352 to 288,
            400 to 225,
            400 to 240,
            426 to 240,
            480 to 270,
            480 to 320,
            480 to 360,
            495 to 396,
            512 to 384,
            536 to 396,
            640 to 360,
            640 to 400,
            640 to 480,
            704 to 396,
            704 to 480,
            704 to 576,
            720 to 404,
            720 to 480,
            720 to 540,
            720 to 576,
            768 to 480,
            800 to 450,
            800 to 600,
            848 to 480,
            854 to 480,
            880 to 495,
            960 to 540,
            960 to 640,
            1024 to 432,
            1024 to 576,
            1024 to 600,
            1024 to 768,
            1152 to 864,
            1280 to 534,
            1280 to 544,
            1280 to 640,
            1280 to 720,
            1280 to 800,
            1280 to 960,
            1280 to 1024,
            1360 to 768,
            1366 to 768,
            1400 to 1050,
            1440 to 900,
            1440 to 1080,
            1600 to 900,
            1600 to 1200,
            1680 to 1050,
            1920 to 800,
            1920 to 816,
            1920 to 960,
            1920 to 1040,
            1920 to 1080,
            1920 to 1200,
            2048 to 858,
            2048 to 1080,
            2048 to 1152,
            2048 to 1536,
            2560 to 1070,
            2560 to 1080,
            2560 to 1440,
            2560 to 1600,
            2560 to 2048,
            2880 to 1620,
            3072 to 1728,
            3200 to 1800,
            3200 to 2048,
            3200 to 2400,
            3440 to 1440,
            3840 to 1080,
            3840 to 1600,
            3840 to 1608,
            3840 to 1634,
            3840 to 1920,
            3840 to 2160,
            3840 to 2400,
            4096 to 1716,
            4096 to 2160,
            4096 to 2304,
            4096 to 3072,
            5120 to 1440,
            5120 to 2160,
            5120 to 2880,
            5120 to 3200,
            5120 to 4096,
            6016 to 3384,
            6144 to 3160,
            6144 to 3456,
            6400 to 4096,
            6400 to 4800,
            7680 to 2160,
            7680 to 4320,
            7680 to 4800,
            8192 to 4320,
            10240 to 4320,
            12288 to 6480,
            15360 to 8640
        )

        fun isSupportedVideoResolution(width: Int, height: Int): Boolean {
            if (width <= 0 || height <= 0) return false
            if (SUPPORTED_VIDEO_RESOLUTIONS.contains(width to height) ||
                SUPPORTED_VIDEO_RESOLUTIONS.contains(height to width)
            ) {
                return true
            }
            return maxOf(width, height) in 90..15360 && minOf(width, height) in 90..8640
        }
    }

    val formatBadge: String
        get() {
            val rawName = displayName.ifBlank { path.substringAfterLast('/') }
            val ext = rawName.substringAfterLast('.', "").trim().uppercase()
            if (ext.isNotEmpty() && ext.length <= 6) {
                return ext
            }
            val mimeSub = mimeType.substringAfter('/', "").substringBefore(';').trim().uppercase()
            return when {
                mimeSub.contains("MATROSKA") -> "MKV"
                mimeSub.contains("QUICKTIME") -> "MOV"
                mimeSub.contains("MSVIDEO") -> "AVI"
                mimeSub.contains("MS-WMV") -> "WMV"
                mimeSub.contains("MS-ASF") -> "ASF"
                mimeSub.contains("MP2T") -> "TS"
                mimeSub.contains("MPEG") -> "MPG"
                mimeSub.contains("FLV") -> "FLV"
                mimeSub.contains("WEBM") -> "WEBM"
                mimeSub.contains("OGG") -> "OGV"
                mimeSub.contains("MXF") -> "MXF"
                mimeSub.contains("3GPP2") -> "3G2"
                mimeSub.contains("3GPP") -> "3GP"
                mimeSub.contains("M4V") -> "M4V"
                mimeSub.contains("AV01") || mimeSub.contains("AV1") -> "AV1"
                mimeSub.contains("VP9") || mimeSub.contains("VP09") -> "VP9"
                mimeSub.contains("HEVC") || mimeSub.contains("DOLBY-VISION") -> "HEVC"
                mimeSub.isNotEmpty() && mimeSub != "*" -> mimeSub.take(6)
                else -> "MP4"
            }
        }

    val exactResolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else "1920x1080"

    val aspectRatioLabel: String
        get() {
            if (displayAspectRatio.isNotBlank()) return displayAspectRatio
            if (sampleAspectRatio.isNotBlank() && sampleAspectRatio != "1:1" && sampleAspectRatio != "0:1") {
                return com.cyosplay.player.util.FfmpegMatroskaCodecRegistry.calculateDisplayAspectRatio(
                    width = width,
                    height = height,
                    sampleAspectRatio = sampleAspectRatio
                )
            }
            if (width <= 0 || height <= 0) return "16:9"
            fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
            val divisor = gcd(width, height).coerceAtLeast(1)
            val rw = width / divisor
            val rh = height / divisor
            return if (rw <= 64 && rh <= 64) {
                "$rw:$rh"
            } else {
                String.format(java.util.Locale.US, "%.2f:1", width.toDouble() / height.toDouble())
            }
        }

    val estimatedBitrateBps: Long
        get() {
            val safeDurSec = (durationMs / 1000L).coerceAtLeast(1L)
            return ((sizeBytes * 8L) / safeDurSec).coerceAtLeast(128_000L)
        }

    val estimatedBitrateLabel: String
        get() {
            val bps = estimatedBitrateBps
            return if (bps >= 1_000_000L) {
                String.format(java.util.Locale.US, "%.1f Mbps", bps / 1_000_000.0)
            } else {
                "${(bps / 1000L).coerceAtLeast(1L)} kbps"
            }
        }

    val inlineTechnicalSummary: String
        get() = "$exactResolutionLabel ($aspectRatioLabel) • $estimatedBitrateLabel"

    val matroskaEbmlBadge: String
        get() {
            val lower = displayName.lowercase(java.util.Locale.US)
            return if (formatBadge in setOf("MKV", "WEBM", "MK3D", "MKS") || lower.contains("x26.mkv") || lower.contains("matroska")) {
                "Matroska (.mkv) (matroska,webm ,mkvmerge v56.1.0) • Matroska/WebM • Lavf62.3.100 • Lavf60.3.100 • libebml v1.4.2 + libmatroska v1.6.4 • libebml v14.2 + libmatroska v1.6.4 • libebml v1.4.7 + libmatroska v1.7.2 • Encoding SW • Encodina SW • H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 • Video Codec MPEG-4 part 2 • ASP • MPEG-4 Part 2 (XVID) (Advanced Simple Profile with packed B-frames) • Video Codec: H.264 / AVC (avc1) • libx264, yuv420p, -movflags +faststart • Profile: Main Profile (avc1.4d401e) or High Profile (avc1.64001e) at Level 3.0 / 3.1 • Chroma Subsampling / Bit Depth: YUV 4:2:0, 8-bit • HE-AAC • AAC • LC • AAC LC (44.1 kHz, Stereo) • Profile: AAC-LC (Low Complexity, mp4a.40.2) • 44100 HZ (8,000 Hz – 384,000 Hz) • Web/Stream Frame Dimensions: 360p (640×360) / SD (704×396, 536×396 or 640×480 depending on platform transcode) • FFmpeg extension • MediaInfo / FFprobe"
            } else {
                "$formatBadge • Matroska (.mkv) (matroska,webm ,mkvmerge v56.1.0) • Lavf62.3.100 • Lavf60.3.100 • libebml v1.4.2 + libmatroska v1.6.4 • libebml v14.2 + libmatroska v1.6.4 • Encoding SW • Encodina SW • H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 • Video Codec MPEG-4 part 2 • ASP • MPEG-4 Part 2 (XVID) (Advanced Simple Profile with packed B-frames) • Video Codec: H.264 / AVC (avc1) • libx264, yuv420p, -movflags +faststart • Profile: Main Profile (avc1.4d401e) or High Profile (avc1.64001e) at Level 3.0 / 3.1 • Chroma Subsampling / Bit Depth: YUV 4:2:0, 8-bit • HE-AAC • AAC • LC • AAC LC (44.1 kHz, Stereo) • Profile: AAC-LC (Low Complexity, mp4a.40.2) • 44100 HZ (8,000 Hz – 384,000 Hz) • Web/Stream Frame Dimensions: 360p (640×360) / SD (704×396, 536×396 or 640×480 depending on platform transcode) • FFmpeg extension • MediaInfo / FFprobe"
            }
        }

    val containerFormatDetail: String
        get() = matroskaEbmlBadge
}

/**
 * Comprehensive exact technical metadata for a video file, displayed in the
 * Video Properties inspector sheet, fully encrypted and lock-protected with AES-256-GCM.
 */
data class VideoProperties(
    val videoId: Long,
    val title: String,
    val fileName: String,
    val exactFilePath: String,
    val folderPath: String,
    val containerFormat: String,
    val mimeType: String,
    val exactSizeBytes: Long,
    val formattedSizeWithExactBytes: String,
    val durationMs: Long,
    val formattedDurationExact: String,
    val width: Int,
    val height: Int,
    val exactDimensionsLabel: String,
    val aspectRatioLabel: String,
    val pixelCountMegapixels: String,
    val rotationDegrees: Int,
    val videoCodecProfile: String,
    val hdrColorDynamicRange: String,
    val frameRateFpsLabel: String,
    val videoBitrateLabel: String,
    val audioCodecChannelsLabel: String,
    val audioSampleRateLabel: String,
    val storageTypeLabel: String,
    val dateModifiedFormatted: String,
    val sha256FingerprintPrefix: String,
    val encryptionProtectionBadge: String = "AES-256-GCM + Master Password Locked",
    val matroskaMuxingEngine: String = "Matroska (.mkv) (matroska,webm ,mkvmerge v56.1.0) • Lavf62.3.100 • Lavf60.3.100 • libebml v1.4.2 + libmatroska v1.6.4 • libebml v14.2 + libmatroska v1.6.4 • libebml v1.4.7 + libmatroska v1.7.2",
    val ffmpegCodecDescriptor: String = "DEV.LS h264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (avc1.4d401e / avc1.64001e • libx264, yuv420p, -movflags +faststart) / MPEG-4 Part 2 (XVID 0x44495658) (Advanced Simple Profile Level 5 with packed B-frames) • FFmpeg extension",
    val softwareEncodingDecodingEngine: String = "MediaCodec + FFmpeg extension (Lavf62.3.100 • libavcodec 60.31.102 • libx264 / libx265 / libaom-av1 / libxvid • MediaInfo / FFprobe)",
    val supportedResolutionsSummary: String = "All Resolutions Supported (144p → 495×396 → 512×384 → 536×396 → 640×360 → 640×480 → 704×396 → 720×404 → 720×540 → 720×576 → 720p → 1920×800 → 1080p → 2K → 4K UHD → 8K UHD)",
    val ffmpegLibraryVersionsSummary: String = "Lavf62.3.100 • Lavf60.3.100 • libavutil 58.29.100 • libavcodec 60.31.102 • libavformat 62.3.100 • libswscale 7.5.100 • libswresample 4.12.100 • libebml v1.4.2 + libmatroska v1.6.4 • libebml v14.2 + libmatroska v1.6.4 • mkvmerge v56.1.0 ('My Friend') 64-bit • FFmpeg extension",
    val encryptedDatacodeCipherToken: String = "",
    val fullSha256Checksum: String = "",
    val lastPlaybackPositionMs: Long = 0L,
    val formattedLastPlaybackPosition: String = "00:00:00.000 (0.0%)",
    val dateCreatedFormatted: String = "",
    val audioBitrateLabel: String = "192 kbps (192,000 bps)",
    val isDataCodesLocked: Boolean = false,
    val masterPasswordLockBadge: String = "Locked with Master Password (AES-256-GCM)",
    val chromaSubsamplingBitDepth: String = "YUV 4:2:0, 8-bit (yuv420p)",
    val avcProfileLevelLabel: String = "Main Profile (avc1.4d401e) or High Profile (avc1.64001e) at Level 3.0 / 3.1 • H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 • libx264, yuv420p, -movflags +faststart",
    val aacProfileLabel: String = "HE-AAC & AAC-LC (Low Complexity, mp4a.40.2) • AAC LC (44.1 kHz, Stereo) • 44100 HZ (8,000 Hz – 384,000 Hz)",
    val webStreamFrameDimensionsLabel: String = "360p (640×360) / SD (704×396, 536×396 or 640×480 depending on platform transcode)",
    val mediaInfoFfprobeSummary: String = ""
) {
    val canonicalPath: String get() = exactFilePath
}

typealias VideoTechnicalDetails = VideoProperties

data class VideoFolder(
    val id: String,
    val name: String,
    val path: String,
    val videos: List<VideoItem>,
    val isRemovableStorage: Boolean = false,
    val hasNewVideo: Boolean = false,
    val newVideoCount: Int = 0,
    val bucketId: String = id,
    val isNew: Boolean = false
) {
    val videoCount: Int get() = videos.size
    val totalSizeBytes: Long get() = videos.sumOf { it.sizeBytes }
    val latestModifiedSeconds: Long get() = videos.maxOfOrNull { it.dateModifiedSeconds } ?: 0L
}

data class StorageVolumeInfo(
    val id: String,
    val name: String,
    val rootPath: String,
    val isRemovable: Boolean,
    val totalBytes: Long,
    val availableBytes: Long
)

data class StorageSubfolderItem(
    val name: String,
    val path: String,
    val directVideoCount: Int,
    val totalRecursiveVideoCount: Int,
    val totalSizeBytes: Long
)

data class StorageDirectoryNode(
    val currentPath: String,
    val displayName: String,
    val parentPath: String?,
    val subfolders: List<StorageSubfolderItem>,
    val directVideos: List<VideoItem>
)

data class VaultVideoEntry(
    val vaultId: String,
    val originalTitle: String,
    val originalPath: String,
    val encryptedFilePath: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val addedTimestampMs: Long,
    val isDecoyEntry: Boolean = false
)

data class RecycleBinItem(
    val recycleId: String,
    val originalVideoId: Long,
    val title: String,
    val originalPath: String,
    val originalUriString: String,
    val trashFilePath: String,
    val folderName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val deletedAtTimestampMs: Long
)

data class Playlist(
    val id: String,
    val name: String,
    val description: String,
    val videoPaths: List<String>,
    val createdAtMs: Long
)

data class PlaybackProgress(
    val videoPath: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long
) {
    val fraction: Float
        get() = if (durationMs > 0L) {
            (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val codecBadge: String,
    val channelCount: Int,
    val sampleRate: Int,
    val isSelected: Boolean
) {
    val sampleRateHz: Int
        get() = sampleRate
}

/**
 * Per-video remembered audio track selection persisted across sessions.
 */
data class SavedAudioTrackSelection(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String,
    val codecBadge: String = "",
    val isMuted: Boolean = false
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    val isExternalSrt: Boolean = false
)

data class SubtitleCueItem(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

data class ExternalSubtitleTrack(
    val id: String,
    val label: String,
    val language: String = "en",
    val uriString: String,
    val cues: List<SubtitleCueItem> = emptyList(),
    val isSelected: Boolean = false
)

data class ActiveSubtitleDisplayItem(
    val trackId: String,
    val trackLabel: String,
    val cueText: String,
    val colorHex: Long = 0xFFFFFFFF
)

/**
 * Live 4K/8K playback telemetry snapshot displayed in the toggleable top-left HUD badge.
 */
data class PlaybackTelemetrySnapshot(
    val currentFps: Float = 60.0f,
    val targetFps: Float = 60.0f,
    val decoderTypeBadge: String = "HW+",
    val decoderCodecName: String = "MediaCodec HW",
    val bufferedAheadMs: Long = 12_500L,
    val bufferHealthPercent: Int = 100,
    val bufferStatusLabel: String = "READY",
    val droppedFrames: Int = 0,
    val resolutionLabel: String = "4K UHD"
)

data class FileTransferProgress(
    val operation: HoldClipboardOperation,
    val completedFiles: Int,
    val totalFiles: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedMbPerSec: Float,
    val currentFileName: String,
    val destinationFolderName: String,
    val isFinished: Boolean = false
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) {
            (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (totalFiles > 0) {
            (completedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
        } else 0f
}





