package com.cyosplay.player.util

import com.cyosplay.player.data.model.SubtitleCueItem
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class SubtitleLanguageSpec(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val isRtl: Boolean = false
)

object SubtitleParser {

    /**
     * Multi-language subtitle catalog supporting 55+ global languages, scripts, and RTL/LTR directions.
     */
    val SUPPORTED_SUBTITLE_LANGUAGES: List<SubtitleLanguageSpec> = listOf(
        SubtitleLanguageSpec("en", "English", "English"),
        SubtitleLanguageSpec("es", "Spanish", "Español"),
        SubtitleLanguageSpec("fr", "French", "Français"),
        SubtitleLanguageSpec("de", "German", "Deutsch"),
        SubtitleLanguageSpec("it", "Italian", "Italiano"),
        SubtitleLanguageSpec("pt", "Portuguese", "Português"),
        SubtitleLanguageSpec("ru", "Russian", "Русский"),
        SubtitleLanguageSpec("uk", "Ukrainian", "Українська"),
        SubtitleLanguageSpec("bg", "Bulgarian", "Български"),
        SubtitleLanguageSpec("sr", "Serbian", "Српски"),
        SubtitleLanguageSpec("ja", "Japanese", "日本語"),
        SubtitleLanguageSpec("ko", "Korean", "한국어"),
        SubtitleLanguageSpec("zh", "Chinese (Simplified)", "简体中文"),
        SubtitleLanguageSpec("zh-TW", "Chinese (Traditional)", "繁體中文"),
        SubtitleLanguageSpec("ar", "Arabic", "العربية", isRtl = true),
        SubtitleLanguageSpec("hi", "Hindi", "हिन्दी"),
        SubtitleLanguageSpec("bn", "Bengali", "বাংলা"),
        SubtitleLanguageSpec("ur", "Urdu", "اردو", isRtl = true),
        SubtitleLanguageSpec("fa", "Persian", "فارسی", isRtl = true),
        SubtitleLanguageSpec("he", "Hebrew", "עברית", isRtl = true),
        SubtitleLanguageSpec("tr", "Turkish", "Türkçe"),
        SubtitleLanguageSpec("vi", "Vietnamese", "Tiếng Việt"),
        SubtitleLanguageSpec("th", "Thai", "ไทย"),
        SubtitleLanguageSpec("lo", "Lao", "ລາວ"),
        SubtitleLanguageSpec("my", "Burmese", "မြန်မာ"),
        SubtitleLanguageSpec("km", "Khmer", "ខ្មែរ"),
        SubtitleLanguageSpec("id", "Indonesian", "Bahasa Indonesia"),
        SubtitleLanguageSpec("ms", "Malay", "Bahasa Melayu"),
        SubtitleLanguageSpec("tl", "Tagalog / Filipino", "Filipino"),
        SubtitleLanguageSpec("pl", "Polish", "Polski"),
        SubtitleLanguageSpec("nl", "Dutch", "Nederlands"),
        SubtitleLanguageSpec("sv", "Swedish", "Svenska"),
        SubtitleLanguageSpec("no", "Norwegian", "Norsk"),
        SubtitleLanguageSpec("da", "Danish", "Dansk"),
        SubtitleLanguageSpec("fi", "Finnish", "Suomi"),
        SubtitleLanguageSpec("el", "Greek", "Ελληνικά"),
        SubtitleLanguageSpec("cs", "Czech", "Čeština"),
        SubtitleLanguageSpec("sk", "Slovak", "Slovenčina"),
        SubtitleLanguageSpec("ro", "Romanian", "Română"),
        SubtitleLanguageSpec("hu", "Hungarian", "Magyar"),
        SubtitleLanguageSpec("ta", "Tamil", "தமிழ்"),
        SubtitleLanguageSpec("te", "Telugu", "తెలుగు"),
        SubtitleLanguageSpec("mr", "Marathi", "मराठी"),
        SubtitleLanguageSpec("gu", "Gujarati", "ગુજરાતી"),
        SubtitleLanguageSpec("pa", "Punjabi", "ਪੰਜਾਬੀ"),
        SubtitleLanguageSpec("ml", "Malayalam", "മലയാളം"),
        SubtitleLanguageSpec("kn", "Kannada", "ಕನ್ನಡ"),
        SubtitleLanguageSpec("si", "Sinhala", "සිංහල"),
        SubtitleLanguageSpec("ne", "Nepali", "नेपाली"),
        SubtitleLanguageSpec("sw", "Swahili", "Kiswahili"),
        SubtitleLanguageSpec("am", "Amharic", "አማርኛ"),
        SubtitleLanguageSpec("az", "Azerbaijani", "Azərbaycan"),
        SubtitleLanguageSpec("uz", "Uzbek", "Oʻzbek"),
        SubtitleLanguageSpec("kk", "Kazakh", "Қазақ тілі"),
        SubtitleLanguageSpec("ka", "Georgian", "ქართული"),
        SubtitleLanguageSpec("hy", "Armenian", "Հայերեն")
    )

    private val TIME_ARROW_REGEX = Regex(
        """(?:(\d{1,3}):)?(\d{1,2}):(\d{2})[.,:](\d{1,3})\s*-->\s*(?:(\d{1,3}):)?(\d{1,2}):(\d{2})[.,:](\d{1,3})"""
    )

    private val ASS_DIALOGUE_REGEX = Regex(
        """^(?:Dialogue|Comment)\s*:\s*[^,]*,\s*(\d{1,2}):(\d{2}):(\d{2})[.,:](\d{1,3})\s*,\s*(\d{1,2}):(\d{2}):(\d{2})[.,:](\d{1,3})\s*,([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),(.*)$""",
        RegexOption.IGNORE_CASE
    )

    private val SUBVIEWER_TIME_REGEX = Regex(
        """^(\d{1,2}):(\d{2}):(\d{2})[.,:](\d{1,3})\s*,\s*(\d{1,2}):(\d{2}):(\d{2})[.,:](\d{1,3})$"""
    )

    private val MICRODVD_LINE_REGEX = Regex(
        """^\{(\d+)\}\{(\d+)\}(.+)$"""
    )

    private val TTML_P_REGEX = Regex(
        """<p[^>]*\bbegin\s*=\s*["']([^"']+)["'][^>]*\bend\s*=\s*["']([^"']+)["'][^>]*>(.*?)</p>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val HTML_TAG_REGEX = Regex("""<[^>]*>""")
    private val ASS_OVERRIDE_TAG_REGEX = Regex("""\{[^}]*\}""")
    private val LRC_LINE_REGEX = Regex("""^\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\](.+)$""")
    private val SRT_DOT_DECIMAL_TIMESTAMP_REGEX = Regex(
        """(\b\d{1,2}:\d{2}:\d{2})\.(\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2})\.(\d{1,3}\b)"""
    )

    /**
     * Normalizes non-spec .srt timestamps that use dot decimals (00:00:01.500 --> 00:00:04.250)
     * into standard SubRip comma decimals (00:00:01,500 --> 00:00:04,250) so Media3 SubripParser
     * never throws a ParserException (Media3 Issue #3045).
     */
    fun normalizeSrtDotDecimalTimestamps(rawText: String): String {
        if (rawText.isEmpty() || !rawText.contains("-->")) return rawText
        return SRT_DOT_DECIMAL_TIMESTAMP_REGEX.replace(rawText) { match ->
            "${match.groupValues[1]},${match.groupValues[2]},${match.groupValues[3]}"
        }
    }

    private val ISO_639_2_TO_1_MAP: Map<String, String> = mapOf(
        "eng" to "en", "spa" to "es", "fra" to "fr", "fre" to "fr", "deu" to "de", "ger" to "de",
        "ita" to "it", "por" to "pt", "rus" to "ru", "ukr" to "uk", "bul" to "bg", "srp" to "sr",
        "jpn" to "ja", "kor" to "ko", "zho" to "zh", "chi" to "zh", "cmn" to "zh", "yue" to "zh-TW",
        "ara" to "ar", "hin" to "hi", "ben" to "bn", "urd" to "ur", "fas" to "fa", "per" to "fa",
        "heb" to "he", "tur" to "tr", "vie" to "vi", "tha" to "th", "lao" to "lo", "mya" to "my",
        "bur" to "my", "khm" to "km", "ind" to "id", "msa" to "ms", "may" to "ms", "tgl" to "tl",
        "fil" to "tl", "pol" to "pl", "nld" to "nl", "dut" to "nl", "swe" to "sv", "nor" to "no",
        "dan" to "da", "fin" to "fi", "ell" to "el", "gre" to "el", "ces" to "cs", "cze" to "cs",
        "slk" to "sk", "slo" to "sk", "ron" to "ro", "rum" to "ro", "hun" to "hu", "tam" to "ta",
        "tel" to "te", "mar" to "mr", "guj" to "gu", "pan" to "pa", "mal" to "ml", "kan" to "kn",
        "sin" to "si", "nep" to "ne", "swa" to "sw", "amh" to "am", "aze" to "az", "uzb" to "uz",
        "kaz" to "kk", "kat" to "ka", "geo" to "ka", "hye" to "hy", "arm" to "hy"
    )

    /**
     * Resolves any ISO-639-1, ISO-639-2/T, ISO-639-2/B, or BCP-47 language tag into a human-friendly
     * multi-language display name (including native script when distinct).
     */
    fun resolveLanguageDisplayLabel(languageCodeOrTag: String?, fallbackTrackNumber: Int = 1): String {
        val raw = languageCodeOrTag?.trim().orEmpty()
        if (raw.isEmpty() || raw.equals("und", ignoreCase = true) || raw.equals("unknown", ignoreCase = true)) {
            return "Subtitle $fallbackTrackNumber"
        }
        val norm = raw.replace('_', '-').lowercase()
        val primarySubtag = norm.substringBefore('-')
        val mappedCode = ISO_639_2_TO_1_MAP[primarySubtag] ?: primarySubtag
        val spec = SUPPORTED_SUBTITLE_LANGUAGES.firstOrNull {
            it.code.equals(norm, ignoreCase = true) ||
                it.code.equals(mappedCode, ignoreCase = true) ||
                it.englishName.equals(raw, ignoreCase = true) ||
                it.nativeName.equals(raw, ignoreCase = true)
        }
        if (spec != null) {
            return if (spec.nativeName.equals(spec.englishName, ignoreCase = true)) {
                spec.englishName
            } else {
                "${spec.englishName} (${spec.nativeName})"
            }
        }
        val localeDisplay = java.util.Locale.forLanguageTag(raw).displayLanguage
        return if (localeDisplay.isNotBlank() && !localeDisplay.equals(raw, ignoreCase = true)) {
            localeDisplay
        } else {
            raw.uppercase()
        }
    }

    fun resolveLanguageDisplayLabel(rawLanguageCode: String?, fallbackIndex: Int, unusedFlag: Boolean = true): String {
        return resolveLanguageDisplayLabel(languageCodeOrTag = rawLanguageCode, fallbackTrackNumber = fallbackIndex)
    }

    /**
     * Checks whether a given ISO language code or display name is supported by the multi-language engine.
     */
    fun isSupportedSubtitleLanguage(languageCodeOrName: String): Boolean {
        val norm = languageCodeOrName.trim().lowercase()
        if (norm.isEmpty()) return false
        val mapped = ISO_639_2_TO_1_MAP[norm.substringBefore('-').substringBefore('_')] ?: norm
        return SUPPORTED_SUBTITLE_LANGUAGES.any {
            it.code.lowercase() == norm ||
                it.code.lowercase() == mapped ||
                norm.startsWith("${it.code.lowercase()}-") ||
                norm.startsWith("${it.code.lowercase()}_") ||
                it.englishName.lowercase().contains(norm) ||
                it.nativeName.lowercase().contains(norm)
        }
    }

    /**
     * Automatically detects the primary script/language family of subtitle text,
     * supporting Latin, Cyrillic, Arabic/Persian/Urdu, Devanagari, Bengali, Tamil, Telugu,
     * Gujarati, Gurmukhi, Malayalam, Kannada, Sinhala, CJK, Hangul, Hiragana/Katakana,
     * Hebrew, Thai, Lao, Myanmar, Khmer, Greek, Georgian, Armenian, and Ethiopic.
     */
    fun detectSubtitleLanguage(sampleText: String): String {
        if (sampleText.isBlank()) return "en"
        for (ch in sampleText) {
            val code = ch.code
            when {
                code in 0x3040..0x30FF -> return "ja" // Hiragana / Katakana
                code in 0xAC00..0xD7AF || code in 0x1100..0x11FF -> return "ko" // Hangul
                code in 0x4E00..0x9FFF -> return "zh" // CJK Unified Ideographs
                code in 0x0600..0x06FF || code in 0x0750..0x077F -> return "ar" // Arabic / Persian / Urdu
                code in 0x0590..0x05FF -> return "he" // Hebrew
                code in 0x0900..0x097F -> return "hi" // Devanagari (Hindi / Marathi / Nepali)
                code in 0x0980..0x09FF -> return "bn" // Bengali
                code in 0x0A00..0x0A7F -> return "pa" // Gurmukhi (Punjabi)
                code in 0x0A80..0x0AFF -> return "gu" // Gujarati
                code in 0x0B80..0x0BFF -> return "ta" // Tamil
                code in 0x0C00..0x0C7F -> return "te" // Telugu
                code in 0x0C80..0x0CFF -> return "kn" // Kannada
                code in 0x0D00..0x0D7F -> return "ml" // Malayalam
                code in 0x0D80..0x0DFF -> return "si" // Sinhala
                code in 0x0E00..0x0E7F -> return "th" // Thai
                code in 0x0E80..0x0EFF -> return "lo" // Lao
                code in 0x1000..0x109F -> return "my" // Myanmar / Burmese
                code in 0x1780..0x17FF -> return "km" // Khmer
                code in 0x1200..0x137F -> return "am" // Ethiopic / Amharic
                code in 0x10A0..0x10FF -> return "ka" // Georgian
                code in 0x0530..0x058F -> return "hy" // Armenian
                code in 0x0400..0x04FF -> return "ru" // Cyrillic
                code in 0x0370..0x03FF -> return "el" // Greek
            }
        }
        return "en"
    }

    /**
     * Checks whether the text contains Right-to-Left (RTL) characters (Arabic, Hebrew, Persian, Urdu, Syriac, Thaana).
     */
    fun isRtlText(text: String): Boolean {
        for (ch in text) {
            val dir = Character.getDirectionality(ch)
            if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Decodes HTML entities and formats multi-language subtitle text (including RTL/LTR BiDi alignment)
     * so punctuation, numbers, and mixed scripts display cleanly during video playback.
     */
    fun formatSubtitleTextForDisplay(rawText: String): String {
        if (rawText.isBlank()) return ""
        val cleaned = rawText
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
            .replace(HTML_TAG_REGEX, "")
            .replace(ASS_OVERRIDE_TAG_REGEX, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

        if (cleaned.isEmpty()) return ""
        return if (isRtlText(cleaned)) {
            cleaned.lines().joinToString("\n") { line ->
                if (isRtlText(line) && !line.startsWith("\u200F")) {
                    "\u200F$line\u200F"
                } else {
                    line
                }
            }
        } else {
            cleaned
        }
    }

    /**
     * Decodes raw subtitle bytes using BOM detection (UTF-8, UTF-16LE, UTF-16BE, UTF-32)
     * followed by strict UTF-8 validation and automatic multi-language charset fallback
     * (GB18030, Big5, Shift_JIS, EUC-KR, Windows-1256 Arabic, Windows-1251 Cyrillic,
     * Windows-1255 Hebrew, Windows-1254 Turkish, Windows-1253 Greek, Windows-1250 Central European,
     * Windows-1258 Vietnamese, TIS-620 Thai, KOI8-R, Windows-1252, ISO-8859-1).
     */
    fun decodeSubtitleBytesMultiLanguage(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 4 &&
            bytes[0] == 0x00.toByte() &&
            bytes[1] == 0x00.toByte() &&
            bytes[2] == 0xFE.toByte() &&
            bytes[3] == 0xFF.toByte()
        ) {
            return runCatching {
                String(bytes, 4, bytes.size - 4, Charset.forName("UTF-32BE"))
            }.getOrElse { String(bytes, Charsets.UTF_8) }
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte() &&
            bytes[2] == 0x00.toByte() &&
            bytes[3] == 0x00.toByte()
        ) {
            return runCatching {
                String(bytes, 4, bytes.size - 4, Charset.forName("UTF-32LE"))
            }.getOrElse { String(bytes, Charsets.UTF_8) }
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()
        ) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()
        ) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        // Strict UTF-8 check first
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            // Fallback to regional multi-language charsets
        }

        val fallbackCharsets = listOf(
            "GB18030",
            "Big5",
            "Shift_JIS",
            "EUC-KR",
            "windows-1256",
            "windows-1251",
            "windows-1255",
            "windows-1254",
            "windows-1253",
            "windows-1250",
            "windows-1258",
            "TIS-620",
            "KOI8-R",
            "windows-1252",
            "ISO-8859-1"
        )
        for (name in fallbackCharsets) {
            try {
                if (Charset.isSupported(name)) {
                    val cs = Charset.forName(name)
                    val decoder = cs.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                    val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
                    if (decoded.isNotBlank()) return decoded
                }
            } catch (_: Exception) {
                // Try next charset
            }
        }
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Reads and decodes a subtitle [java.io.InputStream] using multi-language BOM & charset auto-detection
     * and parses all cues.
     */
    fun parseSubtitleStream(inputStream: java.io.InputStream): List<SubtitleCueItem> {
        val bytes = inputStream.readBytes()
        val decoded = decodeSubtitleBytesMultiLanguage(bytes)
        return parseSubtitleText(decoded)
    }

    /**
     * Parses SRT/VTT/ASS/TTML/SUB content into sorted [SubtitleCueItem] cues.
     */
    fun parseSrt(rawContent: String): List<SubtitleCueItem> = parseSubtitleText(rawContent)

    /**
     * Parses SRT, WebVTT, ASS/SSA, SubViewer (.sub), MicroDVD ({frame}{frame}), or TTML/DFXP
     * subtitle text into sorted [SubtitleCueItem] cues.
     */
    fun parseSubtitleText(rawContent: String): List<SubtitleCueItem> {
        if (rawContent.isBlank()) return emptyList()
        val normalized = rawContent
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val cues = mutableListOf<SubtitleCueItem>()
        var nextId = 1

        // 1. Check TTML / DFXP XML format (<p begin="..." end="...">...</p>)
        if (normalized.contains("<tt", ignoreCase = true) || normalized.contains("<p ", ignoreCase = true)) {
            val ttmlMatches = TTML_P_REGEX.findAll(normalized).toList()
            if (ttmlMatches.isNotEmpty()) {
                for (match in ttmlMatches) {
                    val startMs = parseFlexibleTimestampMs(match.groupValues[1])
                    val endMs = parseFlexibleTimestampMs(match.groupValues[2])
                    val rawBody = match.groupValues[3]
                        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                    val text = formatSubtitleTextForDisplay(rawBody)
                    if (endMs > startMs && text.isNotEmpty()) {
                        cues.add(
                            SubtitleCueItem(
                                id = nextId++,
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                text = text
                            )
                        )
                    }
                }
                if (cues.isNotEmpty()) {
                    return cues.sortedBy { it.startTimeMs }
                }
            }
        }

        // 2. Check ASS / SSA Dialogue lines
        val lines = normalized.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                val assMatch = ASS_DIALOGUE_REGEX.find(trimmed)
                if (assMatch != null) {
                    val startMs = toTimestampMs(
                        hours = assMatch.groupValues[1].toLongOrNull() ?: 0L,
                        minutes = assMatch.groupValues[2].toLongOrNull() ?: 0L,
                        seconds = assMatch.groupValues[3].toLongOrNull() ?: 0L,
                        fractionRaw = assMatch.groupValues[4]
                    )
                    val endMs = toTimestampMs(
                        hours = assMatch.groupValues[5].toLongOrNull() ?: 0L,
                        minutes = assMatch.groupValues[6].toLongOrNull() ?: 0L,
                        seconds = assMatch.groupValues[7].toLongOrNull() ?: 0L,
                        fractionRaw = assMatch.groupValues[8]
                    )
                    val cleanText = formatSubtitleTextForDisplay(assMatch.groupValues[15])
                    if (endMs > startMs && cleanText.isNotEmpty()) {
                        cues.add(
                            SubtitleCueItem(
                                id = nextId++,
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                text = cleanText
                            )
                        )
                    }
                }
            } else if (trimmed.startsWith("{")) {
                // MicroDVD {startFrame}{endFrame}Text (default 23.976 fps)
                val microMatch = MICRODVD_LINE_REGEX.find(trimmed)
                if (microMatch != null) {
                    val startFrame = microMatch.groupValues[1].toLongOrNull() ?: 0L
                    val endFrame = microMatch.groupValues[2].toLongOrNull() ?: 0L
                    val startMs = (startFrame * 1000.0 / 23.976).toLong()
                    val endMs = (endFrame * 1000.0 / 23.976).toLong()
                    val cleanText = formatSubtitleTextForDisplay(microMatch.groupValues[3].replace("|", "\n"))
                    if (endMs > startMs && cleanText.isNotEmpty()) {
                        cues.add(
                            SubtitleCueItem(
                                id = nextId++,
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                text = cleanText
                            )
                        )
                    }
                }
            } else if (trimmed.startsWith("[")) {
                // LRC [mm:ss.xx]Text
                val lrcMatch = LRC_LINE_REGEX.find(trimmed)
                if (lrcMatch != null) {
                    val m = lrcMatch.groupValues[1].toLongOrNull() ?: 0L
                    val s = lrcMatch.groupValues[2].toLongOrNull() ?: 0L
                    val frac = lrcMatch.groupValues[3]
                    val startMs = toTimestampMs(0L, m, s, frac)
                    val endMs = startMs + 4000L
                    val cleanText = formatSubtitleTextForDisplay(lrcMatch.groupValues[4])
                    if (cleanText.isNotEmpty()) {
                        if (cues.isNotEmpty() && cues.last().endTimeMs > startMs) {
                            val prev = cues.removeAt(cues.lastIndex)
                            if (startMs > prev.startTimeMs) {
                                cues.add(prev.copy(endTimeMs = startMs))
                            }
                        }
                        cues.add(
                            SubtitleCueItem(
                                id = nextId++,
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                text = cleanText
                            )
                        )
                    }
                }
            }
        }
        if (cues.isNotEmpty()) {
            return cues.sortedBy { it.startTimeMs }
        }

        // 3. Parse standard SRT, WebVTT, and SubViewer blocks
        val blocks = normalized.split(Regex("\n\\s*\n"))
        for (block in blocks) {
            val blockLines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (blockLines.isEmpty()) continue
            if (blockLines[0].startsWith("WEBVTT", ignoreCase = true) ||
                blockLines[0].startsWith("NOTE", ignoreCase = true) ||
                blockLines[0].startsWith("STYLE", ignoreCase = true)
            ) {
                if (blockLines.size == 1 || !blockLines.any { it.contains("-->") }) continue
            }

            var timeLineIndex = -1
            var startMs = 0L
            var endMs = 0L

            for (i in blockLines.indices) {
                val arrowMatch = TIME_ARROW_REGEX.find(blockLines[i])
                if (arrowMatch != null) {
                    timeLineIndex = i
                    startMs = toTimestampMs(
                        hours = arrowMatch.groupValues[1].toLongOrNull() ?: 0L,
                        minutes = arrowMatch.groupValues[2].toLongOrNull() ?: 0L,
                        seconds = arrowMatch.groupValues[3].toLongOrNull() ?: 0L,
                        fractionRaw = arrowMatch.groupValues[4]
                    )
                    endMs = toTimestampMs(
                        hours = arrowMatch.groupValues[5].toLongOrNull() ?: 0L,
                        minutes = arrowMatch.groupValues[6].toLongOrNull() ?: 0L,
                        seconds = arrowMatch.groupValues[7].toLongOrNull() ?: 0L,
                        fractionRaw = arrowMatch.groupValues[8]
                    )
                    break
                }
                val subViewerMatch = SUBVIEWER_TIME_REGEX.find(blockLines[i])
                if (subViewerMatch != null) {
                    timeLineIndex = i
                    startMs = toTimestampMs(
                        hours = subViewerMatch.groupValues[1].toLongOrNull() ?: 0L,
                        minutes = subViewerMatch.groupValues[2].toLongOrNull() ?: 0L,
                        seconds = subViewerMatch.groupValues[3].toLongOrNull() ?: 0L,
                        fractionRaw = subViewerMatch.groupValues[4]
                    )
                    endMs = toTimestampMs(
                        hours = subViewerMatch.groupValues[5].toLongOrNull() ?: 0L,
                        minutes = subViewerMatch.groupValues[6].toLongOrNull() ?: 0L,
                        seconds = subViewerMatch.groupValues[7].toLongOrNull() ?: 0L,
                        fractionRaw = subViewerMatch.groupValues[8]
                    )
                    break
                }
            }

            if (timeLineIndex == -1 || timeLineIndex >= blockLines.lastIndex) continue
            if (endMs <= startMs) continue

            val rawTextLines = blockLines.subList(timeLineIndex + 1, blockLines.size)
                .joinToString("\n")
                .replace("[br]", "\n", ignoreCase = true)
            val textLines = formatSubtitleTextForDisplay(rawTextLines)

            if (textLines.isNotBlank()) {
                cues.add(
                    SubtitleCueItem(
                        id = nextId++,
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        text = textLines
                    )
                )
            }
        }

        return cues.sortedBy { it.startTimeMs }
    }

    /**
     * Finds the active subtitle cue(s) at [adjustedPositionMs] using fast binary search + nearby window scan
     * so overlapping cues within the same track are combined seamlessly with minimal CPU usage.
     */
    fun findActiveSubtitleCue(
        cues: List<SubtitleCueItem>,
        positionMs: Long,
        offsetMs: Long = 0L
    ): String? {
        if (cues.isEmpty()) return null
        val adjusted = positionMs - offsetMs
        if (adjusted < cues.first().startTimeMs) return null

        // Binary search for the last cue whose startTimeMs <= adjusted
        var low = 0
        var high = cues.lastIndex
        var candidateIdx = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startTimeMs <= adjusted) {
                candidateIdx = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (candidateIdx == -1) return null

        // Scan a small window around candidateIdx to catch overlapping cues (e.g. dual lines in ASS/SRT)
        val windowStart = (candidateIdx - 8).coerceAtLeast(0)
        var firstMatch: SubtitleCueItem? = null
        var combinedLines: MutableList<String>? = null

        for (i in windowStart..candidateIdx) {
            val cue = cues[i]
            if (adjusted in cue.startTimeMs..cue.endTimeMs) {
                if (firstMatch == null) {
                    firstMatch = cue
                } else {
                    if (combinedLines == null) {
                        combinedLines = mutableListOf(firstMatch.text)
                    }
                    if (!combinedLines.contains(cue.text)) {
                        combinedLines.add(cue.text)
                    }
                }
            }
        }

        if (firstMatch == null) return null
        val rawText = if (combinedLines == null) {
            firstMatch.text
        } else {
            combinedLines.joinToString("\n")
        }
        return if (!rawText.contains('<') && !rawText.contains('{') && !rawText.contains('&') && !rawText.contains("\\N")) {
            rawText
        } else {
            formatSubtitleTextForDisplay(rawText)
        }
    }

    private fun parseFlexibleTimestampMs(raw: String): Long {
        val clean = raw.trim()
        if (clean.endsWith("s", ignoreCase = true)) {
            val sec = clean.dropLast(1).toDoubleOrNull() ?: return 0L
            return (sec * 1000.0).toLong()
        }
        val parts = clean.split(":")
        return when (parts.size) {
            3 -> {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val secParts = parts[2].split('.', ',')
                val s = secParts[0].toLongOrNull() ?: 0L
                val frac = secParts.getOrNull(1).orEmpty()
                toTimestampMs(h, m, s, frac)
            }
            2 -> {
                val m = parts[0].toLongOrNull() ?: 0L
                val secParts = parts[1].split('.', ',')
                val s = secParts[0].toLongOrNull() ?: 0L
                val frac = secParts.getOrNull(1).orEmpty()
                toTimestampMs(0L, m, s, frac)
            }
            else -> 0L
        }
    }

    private fun toTimestampMs(
        hours: Long,
        minutes: Long,
        seconds: Long,
        fractionRaw: String
    ): Long {
        val millis = when (fractionRaw.length) {
            0 -> 0L
            1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
            2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
            else -> fractionRaw.take(3).toLongOrNull() ?: 0L
        }
        return (((hours * 60L + minutes) * 60L) + seconds) * 1000L + millis
    }
}
