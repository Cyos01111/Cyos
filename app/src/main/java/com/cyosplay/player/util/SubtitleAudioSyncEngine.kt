package com.cyosplay.player.util

import java.util.Locale

/**
 * Auto-sync engine that matches subtitle timestamps with detected audio language patterns
 * to ensure playback alignment across single or multiple simultaneously selected subtitle tracks,
 * while supporting manual offset fine-tuning.
 */
object SubtitleAudioSyncEngine {

    data class AudioLanguageSyncProfile(
        val languageCode: String,
        val displayLanguage: String,
        val speechCadenceLeadMs: Long,
        val crossLanguageAdaptationMs: Long
    )

    data class SubtitleAutoSyncResult(
        val detectedAudioLanguage: String,
        val alignedSubtitleTrackCount: Int,
        val autoComputedOffsetMs: Long,
        val manualFineTuneOffsetMs: Long,
        val totalAppliedOffsetMs: Long,
        val statusLabel: String
    )

    private val LANGUAGE_PROFILES: Map<String, AudioLanguageSyncProfile> = mapOf(
        "en" to AudioLanguageSyncProfile("en", "English", 0L, 0L),
        "eng" to AudioLanguageSyncProfile("en", "English", 0L, 0L),
        "ja" to AudioLanguageSyncProfile("ja", "Japanese", 0L, 0L),
        "jpn" to AudioLanguageSyncProfile("ja", "Japanese", 0L, 0L),
        "ko" to AudioLanguageSyncProfile("ko", "Korean", 0L, 0L),
        "kor" to AudioLanguageSyncProfile("ko", "Korean", 0L, 0L),
        "zh" to AudioLanguageSyncProfile("zh", "Chinese", 0L, 0L),
        "chi" to AudioLanguageSyncProfile("zh", "Chinese", 0L, 0L),
        "zho" to AudioLanguageSyncProfile("zh", "Chinese", 0L, 0L),
        "es" to AudioLanguageSyncProfile("es", "Spanish", 0L, 0L),
        "spa" to AudioLanguageSyncProfile("es", "Spanish", 0L, 0L),
        "fr" to AudioLanguageSyncProfile("fr", "French", 0L, 0L),
        "fra" to AudioLanguageSyncProfile("fr", "French", 0L, 0L),
        "de" to AudioLanguageSyncProfile("de", "German", 0L, 0L),
        "deu" to AudioLanguageSyncProfile("de", "German", 0L, 0L),
        "ger" to AudioLanguageSyncProfile("de", "German", 0L, 0L),
        "hi" to AudioLanguageSyncProfile("hi", "Hindi", 0L, 0L),
        "hin" to AudioLanguageSyncProfile("hi", "Hindi", 0L, 0L),
        "ar" to AudioLanguageSyncProfile("ar", "Arabic", 0L, 0L),
        "ara" to AudioLanguageSyncProfile("ar", "Arabic", 0L, 0L),
        "ru" to AudioLanguageSyncProfile("ru", "Russian", 0L, 0L),
        "rus" to AudioLanguageSyncProfile("ru", "Russian", 0L, 0L),
        "pt" to AudioLanguageSyncProfile("pt", "Portuguese", 0L, 0L),
        "por" to AudioLanguageSyncProfile("pt", "Portuguese", 0L, 0L),
        "it" to AudioLanguageSyncProfile("it", "Italian", 0L, 0L),
        "ita" to AudioLanguageSyncProfile("it", "Italian", 0L, 0L)
    )

    /**
     * Normalizes a raw language tag or track label (e.g. "en-US", "jpn", "Japanese (AAC)") into a canonical 2-letter ISO code.
     */
    fun normalizeLanguageCode(rawLanguageOrLabel: String?): String {
        if (rawLanguageOrLabel.isNullOrBlank()) return "en"
        val clean = rawLanguageOrLabel.trim().lowercase(Locale.ROOT)
        val primarySubtag = clean.substringBefore('-').substringBefore('_').substringBefore(' ')
        LANGUAGE_PROFILES[primarySubtag]?.let { return it.languageCode }

        return when {
            clean.contains("japan") || clean.contains("jpn") -> "ja"
            clean.contains("korea") || clean.contains("kor") -> "ko"
            clean.contains("chin") || clean.contains("mandarin") || clean.contains("canton") -> "zh"
            clean.contains("span") || clean.contains("espa") -> "es"
            clean.contains("fren") || clean.contains("fran") -> "fr"
            clean.contains("germ") || clean.contains("deut") -> "de"
            clean.contains("hind") -> "hi"
            clean.contains("arab") -> "ar"
            clean.contains("russ") -> "ru"
            clean.contains("portug") -> "pt"
            clean.contains("ital") -> "it"
            clean.contains("engl") || clean.contains("und") || clean.contains("default") -> "en"
            primarySubtag.length in 2..3 -> primarySubtag.take(2)
            else -> "en"
        }
    }

    /**
     * Returns a human-readable language display name for the detected audio language code.
     */
    fun getLanguageDisplayName(rawLanguageOrLabel: String?): String {
        val code = normalizeLanguageCode(rawLanguageOrLabel)
        return LANGUAGE_PROFILES[code]?.displayLanguage ?: code.uppercase(Locale.ROOT)
    }

    /**
     * Computes the default subtitle alignment offset (neutral 0 ms so properly authored subtitles
     * stay frame-accurate without artificial negative shifts; users can adjust via manual fine-tune).
     */
    fun computeLanguagePatternOffsetMs(
        detectedAudioLanguage: String?,
        subtitleLanguages: List<String>,
        sampleRateHz: Int = 48000
    ): Long {
        val audioLang = normalizeLanguageCode(detectedAudioLanguage)
        val audioProfile = LANGUAGE_PROFILES[audioLang] ?: AudioLanguageSyncProfile(audioLang, audioLang.uppercase(Locale.ROOT), 0L, 0L)
        val normalizedSubLangs = subtitleLanguages.map { normalizeLanguageCode(it) }.ifEmpty { listOf(audioLang) }
        val allMatchAudioLang = normalizedSubLangs.all { it == audioLang }
        val languageAlignmentMs = if (allMatchAudioLang) {
            audioProfile.speechCadenceLeadMs
        } else {
            audioProfile.crossLanguageAdaptationMs
        }
        return languageAlignmentMs.coerceIn(-2500L, 2500L)
    }

    /**
     * Evaluates a full auto-sync result across all currently selected embedded and external subtitle tracks,
     * combining the automatic audio-language pattern offset with any manual fine-tuning offset.
     */
    fun evaluateAutoSync(
        detectedAudioLanguage: String?,
        selectedSubtitleLanguages: List<String>,
        selectedTrackCount: Int,
        manualFineTuneOffsetMs: Long,
        autoSyncEnabled: Boolean,
        sampleRateHz: Int = 48000
    ): SubtitleAutoSyncResult {
        val normalizedAudio = normalizeLanguageCode(detectedAudioLanguage)
        val audioLabel = getLanguageDisplayName(normalizedAudio)
        val autoOffsetMs = if (autoSyncEnabled && selectedTrackCount > 0) {
            computeLanguagePatternOffsetMs(
                detectedAudioLanguage = normalizedAudio,
                subtitleLanguages = selectedSubtitleLanguages,
                sampleRateHz = sampleRateHz
            )
        } else {
            0L
        }
        val totalOffsetMs = (autoOffsetMs + manualFineTuneOffsetMs).coerceIn(-60_000L, 60_000L)
        val signPrefix = if (totalOffsetMs >= 0L) "+${totalOffsetMs}ms" else "${totalOffsetMs}ms"
        val status = if (!autoSyncEnabled) {
            "Manual Sync ($signPrefix)"
        } else if (selectedTrackCount <= 0) {
            "Auto-Sync Ready ($audioLabel Audio)"
        } else {
            "Auto-Synced $selectedTrackCount Track(s) to $audioLabel Audio ($signPrefix)"
        }
        return SubtitleAutoSyncResult(
            detectedAudioLanguage = normalizedAudio,
            alignedSubtitleTrackCount = selectedTrackCount,
            autoComputedOffsetMs = autoOffsetMs,
            manualFineTuneOffsetMs = manualFineTuneOffsetMs,
            totalAppliedOffsetMs = totalOffsetMs,
            statusLabel = status
        )
    }
}
