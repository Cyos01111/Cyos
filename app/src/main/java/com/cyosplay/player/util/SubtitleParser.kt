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

    const val MAX_SUBTITLE_BYTES: Int = 10 * 1024 * 1024

    data class SubtitleEncodingOption(
        val id: String,
        val label: String
    )

    val SUPPORTED_SUBTITLE_ENCODINGS: List<SubtitleEncodingOption> = listOf(
        SubtitleEncodingOption("AUTO", "Auto-Detect"),
        SubtitleEncodingOption("UTF-8", "UTF-8"),
        SubtitleEncodingOption("windows-1252", "Western (Win-1252)"),
        SubtitleEncodingOption("windows-1251", "Cyrillic (Win-1251)"),
        SubtitleEncodingOption("windows-1256", "Arabic (Win-1256)"),
        SubtitleEncodingOption("windows-1250", "Central EU (Win-1250)"),
        SubtitleEncodingOption("windows-1254", "Turkish (Win-1254)"),
        SubtitleEncodingOption("windows-1253", "Greek (Win-1253)"),
        SubtitleEncodingOption("windows-1255", "Hebrew (Win-1255)"),
        SubtitleEncodingOption("ISO-8859-1", "Latin-1 (ISO-8859-1)"),
        SubtitleEncodingOption("Shift_JIS", "Japanese (Shift_JIS)"),
        SubtitleEncodingOption("EUC-KR", "Korean (EUC-KR)"),
        SubtitleEncodingOption("GB18030", "Chinese (GB18030)"),
        SubtitleEncodingOption("Big5", "Trad. Chinese (Big5)"),
        SubtitleEncodingOption("TIS-620", "Thai (TIS-620)"),
        SubtitleEncodingOption("KOI8-R", "Russian (KOI8-R)")
    )

    /**
     * Decodes raw subtitle bytes using optional manual charset override, BOM detection
     * (UTF-8, UTF-16LE, UTF-16BE, UTF-32), strict UTF-8 validation, and heuristic scoring
     * across regional single-byte and CJK charsets so European, Cyrillic, Arabic, and CJK
     * subtitles never mis-decode as garbled text.
     */
    fun decodeSubtitleBytesMultiLanguage(
        bytes: ByteArray,
        preferredEncoding: String = "AUTO",
        localeHint: java.util.Locale = java.util.Locale.getDefault()
    ): String {
        if (bytes.isEmpty()) return ""
        val trimmedPref = preferredEncoding.trim()
        if (trimmedPref.isNotEmpty() && !trimmedPref.equals("AUTO", ignoreCase = true)) {
            if (Charset.isSupported(trimmedPref)) {
                val cs = Charset.forName(trimmedPref)
                val decoded = runCatching {
                    val startOffset = if (
                        trimmedPref.equals("UTF-8", ignoreCase = true) &&
                        bytes.size >= 3 &&
                        bytes[0] == 0xEF.toByte() &&
                        bytes[1] == 0xBB.toByte() &&
                        bytes[2] == 0xBF.toByte()
                    ) 3 else 0
                    val decoder = cs.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    decoder.decode(ByteBuffer.wrap(bytes, startOffset, bytes.size - startOffset)).toString()
                }.getOrNull()
                if (decoded != null) return decoded
            }
        }

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
            // Fall through to heuristic charset scoring
        }

        val candidates = listOf(
            "windows-1252",
            "windows-1251",
            "windows-1256",
            "windows-1250",
            "windows-1254",
            "windows-1253",
            "windows-1255",
            "windows-1258",
            "Shift_JIS",
            "EUC-KR",
            "GB18030",
            "Big5",
            "TIS-620",
            "KOI8-R",
            "ISO-8859-1"
        )

        val sampleBytes = if (bytes.size > 65_536) bytes.copyOf(65_536) else bytes
        val localeLang = localeHint.language.lowercase()

        var bestCharset: Charset? = null
        var bestScore = Int.MIN_VALUE

        for (name in candidates) {
            if (!Charset.isSupported(name)) continue
            val cs = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            val decodedSample = try {
                val decoder = cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                decoder.decode(ByteBuffer.wrap(sampleBytes)).toString()
            } catch (_: Exception) {
                continue
            }
            if (decodedSample.isBlank()) continue
            val score = scoreDecodedSubtitleCandidate(name, decodedSample, localeLang)
            if (score > bestScore) {
                bestScore = score
                bestCharset = cs
            }
        }

        if (bestCharset != null) {
            return runCatching {
                val decoder = bestCharset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                decoder.decode(ByteBuffer.wrap(bytes)).toString()
            }.getOrElse { String(bytes, Charsets.UTF_8) }
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun scoreDecodedSubtitleCandidate(
        charsetName: String,
        text: String,
        localeLang: String
    ): Int {
        var score = 0
        var nonAsciiCount = 0
        var controlOrReplacementCount = 0
        var cyrillicUpper = 0
        var cyrillicLower = 0
        var cyrillicMidWordUpper = 0
        var arabicCoreLetters = 0
        var arabicDiacriticsOrNoise = 0
        var latinExtended = 0
        var latinConsecutiveRun = 0
        var latinLongAccentRuns = 0
        var cjkIdeographs = 0
        var cjkPunctuation = 0
        var cjkWordSpaces = 0
        var japaneseKana = 0
        var koreanHangul = 0
        var hangulWordSpaces = 0
        var greekLetters = 0
        var hebrewLetters = 0
        var thaiLetters = 0
        var nonAsciiAlphaRun = 0
        var longNonAsciiAlphaRuns = 0
        var mixedWordScriptSwitches = 0
        var prevKind = 0 // 0=sep, 1=asciiLetter, 2=latinExt, 3=cyrillic, 4=arabic, 5=cjk, 6=other, 7=hangul

        for (i in text.indices) {
            val ch = text[i]
            val code = ch.code

            if (ch == '\uFFFD' ||
                (code in 0x00..0x08) ||
                code == 0x0B ||
                code == 0x0C ||
                (code in 0x0E..0x1F) ||
                (code in 0x7F..0x9F) ||
                code == 0x200E ||
                code == 0x200F
            ) {
                controlOrReplacementCount++
                prevKind = 0
                latinConsecutiveRun = 0
                nonAsciiAlphaRun = 0
                continue
            }

            if (code < 0x80) {
                if (ch == ' ') {
                    if (prevKind == 7) hangulWordSpaces++
                    else if (prevKind == 5) cjkWordSpaces++
                }
                latinConsecutiveRun = 0
                nonAsciiAlphaRun = 0
                val isAsciiLetter = (code in 'A'.code..'Z'.code) || (code in 'a'.code..'z'.code)
                if (isAsciiLetter) {
                    if (prevKind == 3 || prevKind == 4) {
                        mixedWordScriptSwitches++
                    }
                    prevKind = 1
                } else {
                    prevKind = 0
                }
                continue
            }

            nonAsciiCount++
            when {
                // Hiragana / Katakana
                code in 0x3040..0x30FF -> {
                    japaneseKana++
                    latinConsecutiveRun = 0
                    nonAsciiAlphaRun = 0
                    prevKind = 5
                }
                // Hangul Syllables
                code in 0xAC00..0xD7AF -> {
                    koreanHangul++
                    latinConsecutiveRun = 0
                    nonAsciiAlphaRun = 0
                    prevKind = 7
                }
                // Common CJK Unified Ideographs
                code in 0x4E00..0x9FFF -> {
                    cjkIdeographs++
                    latinConsecutiveRun = 0
                    nonAsciiAlphaRun = 0
                    prevKind = 5
                }
                // CJK Symbols, Punctuation & Fullwidth Forms (e.g., ，。！？：《》)
                code in 0x3000..0x303F || code in 0xFF01..0xFF60 -> {
                    cjkPunctuation++
                    latinConsecutiveRun = 0
                    nonAsciiAlphaRun = 0
                    prevKind = 0
                }
                // Cyrillic (Russian/Ukrainian/Bulgarian)
                code in 0x0410..0x042F || code == 0x0401 -> {
                    cyrillicUpper++
                    if (prevKind == 3) {
                        cyrillicMidWordUpper++
                    }
                    if (prevKind == 1) mixedWordScriptSwitches++
                    nonAsciiAlphaRun++
                    if (nonAsciiAlphaRun > 16) longNonAsciiAlphaRuns++
                    latinConsecutiveRun = 0
                    prevKind = 3
                }
                code in 0x0430..0x044F || code == 0x0451 -> {
                    cyrillicLower++
                    if (prevKind == 1) mixedWordScriptSwitches++
                    nonAsciiAlphaRun++
                    if (nonAsciiAlphaRun > 16) longNonAsciiAlphaRuns++
                    latinConsecutiveRun = 0
                    prevKind = 3
                }
                // Core Arabic letters (Alef through Yeh + Persian/Urdu extensions)
                code in 0x0621..0x063A || code in 0x0641..0x064A || code in 0x067E..0x06CC -> {
                    arabicCoreLetters++
                    if (prevKind == 1) mixedWordScriptSwitches++
                    nonAsciiAlphaRun++
                    if (nonAsciiAlphaRun > 16) longNonAsciiAlphaRuns++
                    latinConsecutiveRun = 0
                    prevKind = 4
                }
                // Arabic diacritics (harakat) - rare in bulk unless mis-decoded
                code in 0x064B..0x065F -> {
                    arabicDiacriticsOrNoise++
                    nonAsciiAlphaRun++
                    if (nonAsciiAlphaRun > 16) longNonAsciiAlphaRuns++
                    latinConsecutiveRun = 0
                    prevKind = 4
                }
                // Greek
                code in 0x0386..0x03CE -> {
                    greekLetters++
                    if (prevKind == 1) mixedWordScriptSwitches++
                    nonAsciiAlphaRun++
                    if (nonAsciiAlphaRun > 16) longNonAsciiAlphaRuns++
                    latinConsecutiveRun = 0
                    prevKind = 6
                }
                // Hebrew
                code in 0x05D0..0x05EA -> {
                    hebrewLetters++
                    if (prevKind == 1) mixedWordScriptSwitches++
                    nonAsciiAlphaRun++
                    if (nonAsciiAlphaRun > 16) longNonAsciiAlphaRuns++
                    latinConsecutiveRun = 0
                    prevKind = 6
                }
                // Thai
                code in 0x0E01..0x0E5B -> {
                    thaiLetters++
                    latinConsecutiveRun = 0
                    nonAsciiAlphaRun = 0
                    prevKind = 6
                }
                // Latin-1 Supplement / Latin Extended letters (Spanish/French/German/Central EU/Turkish)
                code in 0x00C0..0x024F || code == 0x00A1 || code == 0x00BF -> {
                    latinExtended++
                    if (code in 0x00C0..0x024F) {
                        latinConsecutiveRun++
                        if (latinConsecutiveRun >= 3) {
                            latinLongAccentRuns++
                        }
                    } else {
                        latinConsecutiveRun = 0
                    }
                    nonAsciiAlphaRun = 0
                    prevKind = 2
                }
                // Common typographic punctuation (smart quotes, em-dash, ellipsis, euro)
                code in 0x2013..0x2026 || code == 0x20AC -> {
                    score += 3
                    latinConsecutiveRun = 0
                    nonAsciiAlphaRun = 0
                    prevKind = 0
                }
                // Halfwidth katakana flood or private-use / obscure symbols
                code in 0xFF61..0xFF9F || code in 0xE000..0xF8FF -> {
                    score -= 10
                    latinConsecutiveRun = 0
                    nonAsciiAlphaRun = 0
                    prevKind = 6
                }
                else -> {
                    score -= 2
                    latinConsecutiveRun = 0
                    nonAsciiAlphaRun = 0
                    prevKind = 6
                }
            }
        }

        // Strong penalties for control characters, replacement characters, and mixed scripts inside words
        score -= controlOrReplacementCount * 40
        score -= mixedWordScriptSwitches * 18

        // Reward coherent scripts (2-byte CJK encodings consume 2 bytes per glyph)
        when (charsetName) {
            "Shift_JIS" -> {
                score += japaneseKana * 24 + cjkIdeographs * 16 + cjkPunctuation * 14
                if (japaneseKana == 0 && cjkIdeographs < 4) score -= 40
            }
            "EUC-KR" -> {
                score += koreanHangul * 18 + hangulWordSpaces * 14 + cjkPunctuation * 6
                // Modern Korean subtitles rarely interleave archaic Hanja with Hangul
                score -= cjkIdeographs * 14
                if (koreanHangul >= 8 && hangulWordSpaces == 0) score -= 45
                if (koreanHangul == 0) score -= 50
            }
            "GB18030", "Big5" -> {
                score += cjkIdeographs * 18 + cjkPunctuation * 14
                score -= cjkWordSpaces * 18
                if (charsetName == "GB18030") score += 6
                if (cjkIdeographs < 3) score -= 50
            }
            "windows-1251", "KOI8-R" -> {
                score += cyrillicLower * 10 + cyrillicUpper * 5
                score -= cyrillicMidWordUpper * 25
                score -= longNonAsciiAlphaRuns * 18
                if (cyrillicLower == 0 && cyrillicUpper == 0) score -= 30
                // Natural Russian prose has far more lowercase than uppercase Cyrillic
                if (cyrillicUpper > cyrillicLower && cyrillicUpper > 4) score -= (cyrillicUpper - cyrillicLower) * 12
            }
            "windows-1256" -> {
                score += arabicCoreLetters * 10
                score -= arabicDiacriticsOrNoise * 16
                score -= latinExtended * 12
                score -= longNonAsciiAlphaRuns * 18
                if (arabicCoreLetters == 0) score -= 35
            }
            "windows-1253" -> {
                score += greekLetters * 10
                score -= longNonAsciiAlphaRuns * 18
                if (greekLetters == 0) score -= 35
            }
            "windows-1255" -> {
                score += hebrewLetters * 10
                score -= longNonAsciiAlphaRuns * 18
                if (hebrewLetters == 0) score -= 35
            }
            "TIS-620" -> {
                score += thaiLetters * 10
                if (thaiLetters == 0) score -= 35
            }
            "windows-1252", "ISO-8859-1", "windows-1250", "windows-1254", "windows-1258" -> {
                score += latinExtended * 8
                // Real Western/Central European words rarely have 3+ consecutive accented Latin letters
                score -= latinLongAccentRuns * 35
                if (charsetName == "windows-1252") score += 4 // Prefer Win-1252 over ISO-8859-1 on tie
            }
        }

        // Modest locale tie-breaker so device language breaks near-ties without overriding clear scripts
        val localeMatches = when (charsetName) {
            "windows-1252", "ISO-8859-1" -> localeLang in setOf("en", "es", "fr", "de", "it", "pt", "nl", "sv", "no", "da", "fi", "id", "ms")
            "windows-1251", "KOI8-R" -> localeLang in setOf("ru", "uk", "bg", "sr", "kk", "be")
            "windows-1256" -> localeLang in setOf("ar", "fa", "ur")
            "windows-1250" -> localeLang in setOf("pl", "cs", "sk", "hu", "ro", "hr", "sl")
            "windows-1254" -> localeLang in setOf("tr", "az")
            "windows-1253" -> localeLang == "el"
            "windows-1255" -> localeLang in setOf("he", "iw")
            "windows-1258" -> localeLang == "vi"
            "TIS-620" -> localeLang == "th"
            "Shift_JIS" -> localeLang == "ja"
            "EUC-KR" -> localeLang == "ko"
            "GB18030", "Big5" -> localeLang == "zh"
            else -> false
        }
        if (localeMatches && nonAsciiCount > 0) {
            score += 12
        }

        return score
    }

    /**
     * Reads up to [maxBytes] from [inputStream] to prevent OOM on corrupt or oversized files.
     */
    fun readBoundedBytes(
        inputStream: java.io.InputStream,
        maxBytes: Int = MAX_SUBTITLE_BYTES
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var remaining = maxBytes
        while (remaining > 0) {
            val read = inputStream.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }

    /**
     * Reads and decodes a subtitle [java.io.InputStream] using bounded read (max 10 MB),
     * multi-language BOM & heuristic charset detection (or manual override), and parses all cues.
     */
    fun parseSubtitleStream(
        inputStream: java.io.InputStream,
        preferredEncoding: String = "AUTO"
    ): List<SubtitleCueItem> {
        val bytes = readBoundedBytes(inputStream, MAX_SUBTITLE_BYTES)
        val decoded = decodeSubtitleBytesMultiLanguage(bytes, preferredEncoding = preferredEncoding)
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

    /**
     * Computes the earliest cue transition timestamp (either the end of an active cue + 1ms,
     * or the start of the next upcoming cue) strictly greater than [adjustedPositionMs].
     * Returns null when no further cue transitions exist after [adjustedPositionMs].
     */
    fun findNextSubtitleEventTimeMs(
        cues: List<SubtitleCueItem>,
        adjustedPositionMs: Long
    ): Long? {
        if (cues.isEmpty()) return null
        if (adjustedPositionMs < cues.first().startTimeMs) {
            return cues.first().startTimeMs
        }

        var low = 0
        var high = cues.lastIndex
        var candidateIdx = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startTimeMs <= adjustedPositionMs) {
                candidateIdx = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        var earliestEventMs = Long.MAX_VALUE
        if (candidateIdx >= 0) {
            val windowStart = (candidateIdx - 8).coerceAtLeast(0)
            for (i in windowStart..candidateIdx) {
                val cue = cues[i]
                if (cue.endTimeMs >= adjustedPositionMs) {
                    val endEvent = cue.endTimeMs + 1L
                    if (endEvent < earliestEventMs) {
                        earliestEventMs = endEvent
                    }
                }
            }
        }

        val nextStartIdx = candidateIdx + 1
        if (nextStartIdx in cues.indices) {
            val nextStart = cues[nextStartIdx].startTimeMs
            if (nextStart > adjustedPositionMs && nextStart < earliestEventMs) {
                earliestEventMs = nextStart
            }
        }

        return if (earliestEventMs == Long.MAX_VALUE) null else earliestEventMs
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
