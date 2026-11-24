package com.cyosplay.player.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cyosplay.player.data.model.AdaptiveTextScaleMode
import com.cyosplay.player.data.model.AdvancedCodecFilter
import com.cyosplay.player.data.model.AppFontMode
import com.cyosplay.player.data.model.AppThemeMode
import com.cyosplay.player.data.model.BrowserLayoutColumns
import com.cyosplay.player.data.model.BrowserThumbnailRatio
import com.cyosplay.player.data.model.BrowserViewMode
import com.cyosplay.player.data.model.BrowserVisibleFields
import com.cyosplay.player.data.model.DecoderEngineMode
import com.cyosplay.player.data.model.FolderBrowserConfig
import com.cyosplay.player.data.model.FolderExplorerMode
import com.cyosplay.player.data.model.FolderSortOption
import com.cyosplay.player.data.model.PlaybackProgress
import com.cyosplay.player.data.model.PlayerOrientationMode
import com.cyosplay.player.data.model.RepeatMode
import com.cyosplay.player.data.model.ResolutionFilterTier
import com.cyosplay.player.data.model.SavedAudioTrackSelection
import com.cyosplay.player.data.model.SmartEnhancementMode
import com.cyosplay.player.data.model.SortDirection
import com.cyosplay.player.data.model.StorageVolumeFilter
import com.cyosplay.player.data.model.SubtitleColorOption
import com.cyosplay.player.data.model.SubtitleEdgeStyle
import com.cyosplay.player.data.model.SubtitleFontSize
import com.cyosplay.player.data.model.SubtitleOutlineColorOption
import com.cyosplay.player.data.model.SubtitleVerticalPreset
import com.cyosplay.player.data.model.MiniPlayerPositionPreset
import com.cyosplay.player.data.model.VideoSortOption
import com.cyosplay.player.util.CryptoSecurityManager
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

class SettingsRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("cyosplay_secure_settings_v2", Context.MODE_PRIVATE)

    private val keyRotationListener = CryptoSecurityManager.KeyRotationListener { oldKey, newKey ->
        reEncryptAllStoredSettings(oldKey, newKey)
    }

    init {
        CryptoSecurityManager.registerRotationListener(keyRotationListener)
    }

    private fun putEncrypted(key: String, plainValue: String) {
        val cipher = CryptoSecurityManager.encryptString(appContext, plainValue)
        prefs.edit().putString(key, cipher).commit()
    }

    private fun getDecrypted(key: String, defaultValue: String = ""): String {
        val raw = prefs.getString(key, null) ?: return defaultValue
        return if (CryptoSecurityManager.isEncryptedString(raw)) {
            CryptoSecurityManager.decryptString(appContext, raw) ?: defaultValue
        } else {
            // Migrate legacy plain value to encrypted storage automatically
            putEncrypted(key, raw)
            raw
        }
    }

    @Synchronized
    private fun reEncryptAllStoredSettings(oldKey: SecretKey, newKey: SecretKey) {
        val allEntries = prefs.all
        val editor = prefs.edit()
        for ((k, v) in allEntries) {
            if (v is String && CryptoSecurityManager.isEncryptedString(v)) {
                val plain = CryptoSecurityManager.decryptStringWithKey(v, oldKey)
                if (plain != null) {
                    editor.putString(k, CryptoSecurityManager.encryptStringWithKey(plain, newKey))
                }
            }
        }
        editor.commit()
    }

    // --- Vault PIN & Decoy PIN & Security Question (Encrypted PBKDF2 + AES-256-GCM) ---

    fun hasVaultPin(): Boolean {
        return getDecrypted(KEY_VAULT_PIN_TOKEN).isNotBlank()
    }

    fun setVaultPin(pin: String) {
        val token = CryptoSecurityManager.createEncryptedSecretHash(appContext, pin)
        prefs.edit().putString(KEY_VAULT_PIN_TOKEN, token).commit()
    }

    fun verifyVaultPin(candidatePin: String): Boolean {
        val token = prefs.getString(KEY_VAULT_PIN_TOKEN, null) ?: return false
        return CryptoSecurityManager.verifyEncryptedSecretHash(appContext, candidatePin, token)
    }

    fun setDecoyVaultPin(decoyPin: String) {
        if (decoyPin.isBlank()) {
            prefs.edit().remove(KEY_DECOY_PIN_TOKEN).commit()
            return
        }
        val token = CryptoSecurityManager.createEncryptedSecretHash(appContext, decoyPin)
        prefs.edit().putString(KEY_DECOY_PIN_TOKEN, token).commit()
    }

    fun hasDecoyVaultPin(): Boolean {
        return !prefs.getString(KEY_DECOY_PIN_TOKEN, null).isNullOrBlank()
    }

    fun verifyDecoyVaultPin(candidatePin: String): Boolean {
        val token = prefs.getString(KEY_DECOY_PIN_TOKEN, null) ?: return false
        return CryptoSecurityManager.verifyEncryptedSecretHash(appContext, candidatePin, token)
    }

    fun saveSecurityQuestion(question: String, answer: String) {
        putEncrypted(KEY_SECURITY_QUESTION, question.trim())
        val answerToken = CryptoSecurityManager.createEncryptedSecretHash(
            appContext,
            answer.trim().lowercase()
        )
        prefs.edit().putString(KEY_SECURITY_ANSWER_TOKEN, answerToken).commit()
    }

    fun saveSecondarySecurityQuestion(question: String, answer: String) {
        if (answer.isBlank()) return
        putEncrypted(KEY_SECURITY_QUESTION_2, question.trim())
        val answerToken = CryptoSecurityManager.createEncryptedSecretHash(
            appContext,
            answer.trim().lowercase()
        )
        prefs.edit().putString(KEY_SECURITY_ANSWER_TOKEN_2, answerToken).commit()
    }

    fun getSecurityQuestion(): String {
        return getDecrypted(KEY_SECURITY_QUESTION, PRESET_RECOVERY_QUESTIONS.first())
    }

    fun getSecondarySecurityQuestion(): String {
        return getDecrypted(KEY_SECURITY_QUESTION_2, PRESET_RECOVERY_QUESTIONS[1])
    }

    fun hasSecurityQuestion(): Boolean {
        return !prefs.getString(KEY_SECURITY_ANSWER_TOKEN, null).isNullOrBlank()
    }

    fun hasSecondarySecurityQuestion(): Boolean {
        return !prefs.getString(KEY_SECURITY_ANSWER_TOKEN_2, null).isNullOrBlank()
    }

    fun verifySecurityAnswer(candidateAnswer: String): Boolean {
        val normalized = candidateAnswer.trim().lowercase()
        if (normalized.isEmpty()) return false
        val token1 = prefs.getString(KEY_SECURITY_ANSWER_TOKEN, null)
        if (!token1.isNullOrBlank() &&
            CryptoSecurityManager.verifyEncryptedSecretHash(appContext, normalized, token1)
        ) {
            return true
        }
        val token2 = prefs.getString(KEY_SECURITY_ANSWER_TOKEN_2, null)
        if (!token2.isNullOrBlank() &&
            CryptoSecurityManager.verifyEncryptedSecretHash(appContext, normalized, token2)
        ) {
            return true
        }
        return false
    }

    // --- Stealth Playback Privacy & Anti-Crack Settings ---

    fun isStealthPlaybackEnabled(): Boolean =
        getDecrypted(KEY_STEALTH_PLAYBACK, "false").toBoolean()

    fun setStealthPlaybackEnabled(enabled: Boolean) {
        putEncrypted(KEY_STEALTH_PLAYBACK, enabled.toString())
    }

    fun isAutoLockVaultOnBackground(): Boolean =
        getDecrypted(KEY_AUTO_LOCK_VAULT, "true").toBoolean()

    fun setAutoLockVaultOnBackground(enabled: Boolean) {
        putEncrypted(KEY_AUTO_LOCK_VAULT, enabled.toString())
    }

    fun isBiometricVaultEnabled(): Boolean =
        getDecrypted(KEY_BIOMETRIC_VAULT, "true").toBoolean()

    fun setBiometricVaultEnabled(enabled: Boolean) {
        putEncrypted(KEY_BIOMETRIC_VAULT, enabled.toString())
    }

    // --- Low-RAM Performance, Battery Saver & Multi-Codec Decoder Settings ---

    fun isLowRamShieldEnabled(): Boolean =
        getDecrypted(KEY_LOW_RAM_SHIELD, "false").toBoolean()

    fun setLowRamShieldEnabled(enabled: Boolean) {
        putEncrypted(KEY_LOW_RAM_SHIELD, enabled.toString())
    }

    fun isBatterySaverModeEnabled(): Boolean =
        getDecrypted(KEY_BATTERY_SAVER_MODE, "false").toBoolean()

    fun setBatterySaverModeEnabled(enabled: Boolean) {
        putEncrypted(KEY_BATTERY_SAVER_MODE, enabled.toString())
    }

    fun getDecoderEngineMode(): DecoderEngineMode {
        val raw = getDecrypted(KEY_DECODER_ENGINE_MODE, DecoderEngineMode.AUTO_HW_PLUS.name)
        return DecoderEngineMode.entries.find { it.name == raw } ?: DecoderEngineMode.AUTO_HW_PLUS
    }

    fun setDecoderEngineMode(mode: DecoderEngineMode) {
        putEncrypted(KEY_DECODER_ENGINE_MODE, mode.name)
    }

    // --- Playback Progress & Bookmarks (Encrypted) ---

    fun savePlaybackProgress(videoPath: String, positionMs: Long, durationMs: Long) {
        if (videoPath.isBlank()) return
        if (isStealthPlaybackEnabled()) {
            // In Stealth Playback mode, do not persist trace of what video was played to disk
            return
        }
        val safeDuration = durationMs.coerceAtLeast(1000L)
        val safePosition = positionMs.coerceIn(0L, safeDuration)
        val map = getAllPlaybackProgress().toMutableMap()
        map[videoPath] = PlaybackProgress(
            videoPath = videoPath,
            lastPositionMs = safePosition,
            durationMs = safeDuration,
            updatedAtMs = System.currentTimeMillis()
        )
        val arr = JSONArray()
        map.values.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("path", item.videoPath)
                    put("pos", item.lastPositionMs)
                    put("dur", item.durationMs)
                    put("ts", item.updatedAtMs)
                }
            )
        }
        putEncrypted(KEY_PLAYBACK_PROGRESS_JSON, arr.toString())
        putEncrypted(KEY_LAST_PLAYED_VIDEO_PATH, videoPath)
    }

    fun getPlaybackProgressForVideo(videoPath: String): PlaybackProgress? {
        if (videoPath.isBlank()) return null
        return getAllPlaybackProgress()[videoPath]
    }

    fun getLastPlayedVideoPath(): String? {
        if (isStealthPlaybackEnabled()) return null
        val path = getDecrypted(KEY_LAST_PLAYED_VIDEO_PATH, "").trim()
        return path.ifBlank { null }
    }

    fun setLastPlayedVideoPath(videoPath: String?) {
        if (videoPath.isNullOrBlank()) {
            prefs.edit().remove(KEY_LAST_PLAYED_VIDEO_PATH).commit()
        } else if (!isStealthPlaybackEnabled()) {
            putEncrypted(KEY_LAST_PLAYED_VIDEO_PATH, videoPath)
        }
    }

    fun saveLastPlayedVideoForFolder(folderId: String, videoPath: String) {
        if (folderId.isBlank() || videoPath.isBlank() || isStealthPlaybackEnabled()) return
        val current = getAllFolderLastPlayedVideos().toMutableMap()
        current[folderId] = videoPath
        val obj = JSONObject()
        current.forEach { (id, path) -> obj.put(id, path) }
        putEncrypted(KEY_FOLDER_LAST_PLAYED_JSON, obj.toString())
    }

    fun getLastPlayedVideoForFolder(folderId: String): String? {
        if (folderId.isBlank() || isStealthPlaybackEnabled()) return null
        return getAllFolderLastPlayedVideos()[folderId]?.takeIf { it.isNotBlank() }
    }

    fun getAllFolderLastPlayedVideos(): Map<String, String> {
        if (isStealthPlaybackEnabled()) return emptyMap()
        val raw = getDecrypted(KEY_FOLDER_LAST_PLAYED_JSON, "{}")
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "")
                if (k.isNotBlank() && v.isNotBlank()) {
                    map[k] = v
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getAllPlaybackProgress(): Map<String, PlaybackProgress> {
        val json = getDecrypted(KEY_PLAYBACK_PROGRESS_JSON, "[]")
        return try {
            val arr = JSONArray(json)
            val result = mutableMapOf<String, PlaybackProgress>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("path")
                if (path.isNotBlank()) {
                    result[path] = PlaybackProgress(
                        videoPath = path,
                        lastPositionMs = obj.optLong("pos", 0L),
                        durationMs = obj.optLong("dur", 0L),
                        updatedAtMs = obj.optLong("ts", 0L)
                    )
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun clearAllPlaybackHistoryAndTraces() {
        prefs.edit()
            .remove(KEY_PLAYBACK_PROGRESS_JSON)
            .remove(KEY_LAST_PLAYED_VIDEO_PATH)
            .remove(KEY_FOLDER_LAST_PLAYED_JSON)
            .remove(KEY_BOOKMARKS_JSON)
            .commit()
    }

    fun getVideoBookmarks(videoPath: String): List<Long> {
        val all = getAllBookmarksMap()
        return all[videoPath] ?: emptyList()
    }

    fun addVideoBookmark(videoPath: String, timestampMs: Long): List<Long> {
        val all = getAllBookmarksMap().toMutableMap()
        val updated = ((all[videoPath] ?: emptyList()) + timestampMs).distinct().sorted()
        all[videoPath] = updated
        saveAllBookmarksMap(all)
        return updated
    }

    fun removeVideoBookmark(videoPath: String, timestampMs: Long): List<Long> {
        val all = getAllBookmarksMap().toMutableMap()
        val updated = (all[videoPath] ?: emptyList()).filterNot { kotlin.math.abs(it - timestampMs) < 500L }
        all[videoPath] = updated
        saveAllBookmarksMap(all)
        return updated
    }

    private fun getAllBookmarksMap(): Map<String, List<Long>> {
        val json = getDecrypted(KEY_BOOKMARKS_JSON, "{}")
        return try {
            val root = JSONObject(json)
            val map = mutableMapOf<String, List<Long>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = root.optJSONArray(k) ?: continue
                val list = mutableListOf<Long>()
                for (i in 0 until arr.length()) {
                    list.add(arr.optLong(i))
                }
                map[k] = list
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveAllBookmarksMap(map: Map<String, List<Long>>) {
        val root = JSONObject()
        for ((k, list) in map) {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            root.put(k, arr)
        }
        putEncrypted(KEY_BOOKMARKS_JSON, root.toString())
    }

    // --- Hidden Folders (Encrypted) ---

    fun getHiddenFolderPaths(): Set<String> {
        val raw = getDecrypted(KEY_HIDDEN_FOLDERS_JSON, "[]")
        return try {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i)
                    if (p.isNotBlank()) add(p)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setHiddenFolderPaths(paths: Set<String>) {
        val arr = JSONArray()
        paths.forEach { arr.put(it) }
        putEncrypted(KEY_HIDDEN_FOLDERS_JSON, arr.toString())
    }

    // --- Visual, Audio, Subtitle & Sorting Preferences (Encrypted) ---

    fun getThemeMode(): AppThemeMode {
        val name = getDecrypted(KEY_THEME_MODE, AppThemeMode.AMOLED_BLACK.name)
        return AppThemeMode.entries.find { it.name == name } ?: AppThemeMode.AMOLED_BLACK
    }

    fun setThemeMode(mode: AppThemeMode) = putEncrypted(KEY_THEME_MODE, mode.name)

    fun getFontMode(): AppFontMode {
        val name = getDecrypted(KEY_FONT_MODE, AppFontMode.SYSTEM_DEFAULT.name)
        return AppFontMode.entries.find { it.name == name } ?: AppFontMode.SYSTEM_DEFAULT
    }

    fun setFontMode(mode: AppFontMode) = putEncrypted(KEY_FONT_MODE, mode.name)

    fun getTextScaleMode(): AdaptiveTextScaleMode {
        val name = getDecrypted(KEY_TEXT_SCALE_MODE, AdaptiveTextScaleMode.COMPACT.name)
        return AdaptiveTextScaleMode.entries.find { it.name == name } ?: AdaptiveTextScaleMode.COMPACT
    }

    fun setTextScaleMode(mode: AdaptiveTextScaleMode) = putEncrypted(KEY_TEXT_SCALE_MODE, mode.name)

    fun getVideoSortOption(): VideoSortOption {
        val name = getDecrypted(KEY_VIDEO_SORT, VideoSortOption.DATE_DESC.name)
        return VideoSortOption.entries.find { it.name == name } ?: VideoSortOption.DATE_DESC
    }

    fun setVideoSortOption(option: VideoSortOption) = putEncrypted(KEY_VIDEO_SORT, option.name)

    fun getFolderSortOption(): FolderSortOption {
        val name = getDecrypted(KEY_FOLDER_SORT, FolderSortOption.NAME_ASC.name)
        return FolderSortOption.entries.find { it.name == name } ?: FolderSortOption.NAME_ASC
    }

    fun setFolderSortOption(option: FolderSortOption) = putEncrypted(KEY_FOLDER_SORT, option.name)

    fun getFolderExplorerMode(): FolderExplorerMode {
        val name = getDecrypted(KEY_FOLDER_EXPLORER_MODE, FolderExplorerMode.FLAT_FOLDERS.name)
        return FolderExplorerMode.entries.find { it.name == name } ?: FolderExplorerMode.FLAT_FOLDERS
    }

    fun setFolderExplorerMode(mode: FolderExplorerMode) = putEncrypted(KEY_FOLDER_EXPLORER_MODE, mode.name)

    fun getStorageVolumeFilter(): StorageVolumeFilter {
        val name = getDecrypted(KEY_STORAGE_FILTER, StorageVolumeFilter.ALL_STORAGE.name)
        return StorageVolumeFilter.entries.find { it.name == name } ?: StorageVolumeFilter.ALL_STORAGE
    }

    fun setStorageVolumeFilter(filter: StorageVolumeFilter) = putEncrypted(KEY_STORAGE_FILTER, filter.name)

    fun getRepeatMode(): RepeatMode {
        val name = getDecrypted(KEY_REPEAT_MODE, RepeatMode.NONE.name)
        return RepeatMode.entries.find { it.name == name } ?: RepeatMode.NONE
    }

    fun setRepeatMode(mode: RepeatMode) = putEncrypted(KEY_REPEAT_MODE, mode.name)

    fun getPlayerOrientationMode(): PlayerOrientationMode {
        val name = getDecrypted(KEY_ORIENTATION_MODE, PlayerOrientationMode.AUTO_VIDEO.name)
        return PlayerOrientationMode.entries.find { it.name == name } ?: PlayerOrientationMode.AUTO_VIDEO
    }

    fun setPlayerOrientationMode(mode: PlayerOrientationMode) = putEncrypted(KEY_ORIENTATION_MODE, mode.name)

    fun getSmartEnhancementMode(): SmartEnhancementMode {
        val name = getDecrypted(KEY_SMART_ENHANCEMENT, SmartEnhancementMode.OFF.name)
        return SmartEnhancementMode.entries.find { it.name == name } ?: SmartEnhancementMode.OFF
    }

    fun setSmartEnhancementMode(mode: SmartEnhancementMode) = putEncrypted(KEY_SMART_ENHANCEMENT, mode.name)

    fun isSubtitlesEnabledByUser(): Boolean =
        getDecrypted(KEY_SUBTITLES_ENABLED_BY_USER, "false").toBoolean()

    fun setSubtitlesEnabledByUser(enabled: Boolean) =
        putEncrypted(KEY_SUBTITLES_ENABLED_BY_USER, enabled.toString())

    fun getSavedPlayerBrightness(): Float =
        com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeBrightness(
            getDecrypted(KEY_SAVED_PLAYER_BRIGHTNESS, "0.65").toFloatOrNull()
        )

    fun setSavedPlayerBrightness(value: Float) =
        putEncrypted(
            KEY_SAVED_PLAYER_BRIGHTNESS,
            com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeBrightness(value).toString()
        )

    fun getSavedPlayerVolume(): Float =
        com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeVolume(
            getDecrypted(KEY_SAVED_PLAYER_VOLUME, "0.75").toFloatOrNull()
        )

    fun setSavedPlayerVolume(value: Float) =
        putEncrypted(
            KEY_SAVED_PLAYER_VOLUME,
            com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeVolume(value).toString()
        )

    fun getFolderBrightnessMap(): Map<String, Float> {
        val raw = getDecrypted(KEY_FOLDER_BRIGHTNESS_MAP_JSON, "{}")
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Float>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeBrightness(
                    obj.optDouble(k, 0.65).toFloat()
                )
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getSavedPlayerBrightnessForFolder(folderKey: String): Float {
        return getSavedPlayerBrightness()
    }

    fun setSavedPlayerBrightnessForFolder(folderKey: String, value: Float) {
        val clamped = com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeBrightness(value)
        setSavedPlayerBrightness(clamped)
        val normalized = folderKey.trim().trimEnd('/')
        val map = getFolderBrightnessMap().toMutableMap()
        map.keys.toList().forEach { k -> map[k] = clamped }
        if (normalized.isNotBlank()) {
            map[normalized] = clamped
        }
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.toDouble()) }
        putEncrypted(KEY_FOLDER_BRIGHTNESS_MAP_JSON, obj.toString())

        val vMap = getVideoBrightnessMap().toMutableMap()
        if (vMap.isNotEmpty()) {
            vMap.keys.toList().forEach { k -> vMap[k] = clamped }
            val vObj = JSONObject()
            vMap.forEach { (k, v) -> vObj.put(k, v.toDouble()) }
            putEncrypted(KEY_VIDEO_BRIGHTNESS_MAP_JSON, vObj.toString())
        }
    }

    fun getFolderVolumeMap(): Map<String, Float> {
        val raw = getDecrypted(KEY_FOLDER_VOLUME_MAP_JSON, "{}")
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Float>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeVolume(
                    obj.optDouble(k, 0.75).toFloat()
                )
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getSavedPlayerVolumeForFolder(folderKey: String): Float {
        return getSavedPlayerVolume()
    }

    fun setSavedPlayerVolumeForFolder(folderKey: String, value: Float) {
        val clamped = com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeVolume(value)
        setSavedPlayerVolume(clamped)
        val normalized = folderKey.trim().trimEnd('/')
        val map = getFolderVolumeMap().toMutableMap()
        map.keys.toList().forEach { k -> map[k] = clamped }
        if (normalized.isNotBlank()) {
            map[normalized] = clamped
        }
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.toDouble()) }
        putEncrypted(KEY_FOLDER_VOLUME_MAP_JSON, obj.toString())

        val vMap = getVideoVolumeMap().toMutableMap()
        if (vMap.isNotEmpty()) {
            vMap.keys.toList().forEach { k -> vMap[k] = clamped }
            val vObj = JSONObject()
            vMap.forEach { (k, v) -> vObj.put(k, v.toDouble()) }
            putEncrypted(KEY_VIDEO_VOLUME_MAP_JSON, vObj.toString())
        }
    }

    fun getVideoBrightnessMap(): Map<String, Float> {
        val raw = getDecrypted(KEY_VIDEO_BRIGHTNESS_MAP_JSON, "{}")
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Float>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeBrightness(
                    obj.optDouble(k, 0.65).toFloat()
                )
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getSavedPlayerBrightnessForVideo(videoPath: String, folderKey: String = ""): Float {
        return getSavedPlayerBrightness()
    }

    fun setSavedPlayerBrightnessForVideo(videoPath: String, folderKey: String = "", value: Float) {
        val clamped = com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeBrightness(value)
        val normFolder = folderKey.trim().trimEnd('/')
        val parentDir = videoPath.trim().substringBeforeLast('/', "").trim().trimEnd('/')
        setSavedPlayerBrightnessForFolder(normFolder.ifBlank { parentDir }, clamped)
        if (normFolder.isNotEmpty() && parentDir.isNotEmpty() && normFolder != parentDir) {
            setSavedPlayerBrightnessForFolder(parentDir, clamped)
        }
        val normVideo = videoPath.trim()
        if (normVideo.isBlank()) return
        val map = getVideoBrightnessMap().toMutableMap()
        map.keys.toList().forEach { k -> map[k] = clamped }
        map[normVideo] = clamped
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.toDouble()) }
        putEncrypted(KEY_VIDEO_BRIGHTNESS_MAP_JSON, obj.toString())
    }

    fun getVideoVolumeMap(): Map<String, Float> {
        val raw = getDecrypted(KEY_VIDEO_VOLUME_MAP_JSON, "{}")
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Float>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeVolume(
                    obj.optDouble(k, 0.75).toFloat()
                )
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getSavedPlayerVolumeForVideo(videoPath: String, folderKey: String = ""): Float {
        return getSavedPlayerVolume()
    }

    fun setSavedPlayerVolumeForVideo(videoPath: String, folderKey: String = "", value: Float) {
        val clamped = com.cyosplay.player.util.PlayerGestureControlHelper.sanitizeVolume(value)
        val normFolder = folderKey.trim().trimEnd('/')
        val parentDir = videoPath.trim().substringBeforeLast('/', "").trim().trimEnd('/')
        setSavedPlayerVolumeForFolder(normFolder.ifBlank { parentDir }, clamped)
        if (normFolder.isNotEmpty() && parentDir.isNotEmpty() && normFolder != parentDir) {
            setSavedPlayerVolumeForFolder(parentDir, clamped)
        }
        val normVideo = videoPath.trim()
        if (normVideo.isBlank()) return
        val map = getVideoVolumeMap().toMutableMap()
        map.keys.toList().forEach { k -> map[k] = clamped }
        map[normVideo] = clamped
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.toDouble()) }
        putEncrypted(KEY_VIDEO_VOLUME_MAP_JSON, obj.toString())
    }

    fun getFoldersScrollIndex(): Int =
        getDecrypted(KEY_FOLDERS_SCROLL_INDEX, "0").toIntOrNull()?.coerceAtLeast(0) ?: 0

    fun getFoldersScrollOffset(): Int =
        getDecrypted(KEY_FOLDERS_SCROLL_OFFSET, "0").toIntOrNull()?.coerceAtLeast(0) ?: 0

    fun saveFoldersScrollState(index: Int, offset: Int) {
        putEncrypted(KEY_FOLDERS_SCROLL_INDEX, index.coerceAtLeast(0).toString())
        putEncrypted(KEY_FOLDERS_SCROLL_OFFSET, offset.coerceAtLeast(0).toString())
    }

    fun getVideosScrollIndex(): Int =
        getDecrypted(KEY_VIDEOS_SCROLL_INDEX, "0").toIntOrNull()?.coerceAtLeast(0) ?: 0

    fun getVideosScrollOffset(): Int =
        getDecrypted(KEY_VIDEOS_SCROLL_OFFSET, "0").toIntOrNull()?.coerceAtLeast(0) ?: 0

    fun saveVideosScrollState(index: Int, offset: Int) {
        putEncrypted(KEY_VIDEOS_SCROLL_INDEX, index.coerceAtLeast(0).toString())
        putEncrypted(KEY_VIDEOS_SCROLL_OFFSET, offset.coerceAtLeast(0).toString())
    }

    fun getFolderDetailScrollState(folderId: String): Pair<Int, Int> {
        if (folderId.isBlank()) return 0 to 0
        val raw = getDecrypted(KEY_FOLDER_DETAIL_SCROLL_JSON, "{}")
        return try {
            val obj = JSONObject(raw)
            val entry = obj.optJSONObject(folderId) ?: return 0 to 0
            val idx = entry.optInt("idx", 0).coerceAtLeast(0)
            val off = entry.optInt("off", 0).coerceAtLeast(0)
            idx to off
        } catch (_: Exception) {
            0 to 0
        }
    }

    fun saveFolderDetailScrollState(folderId: String, index: Int, offset: Int) {
        if (folderId.isBlank()) return
        val raw = getDecrypted(KEY_FOLDER_DETAIL_SCROLL_JSON, "{}")
        val obj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
        obj.put(
            folderId,
            JSONObject().apply {
                put("idx", index.coerceAtLeast(0))
                put("off", offset.coerceAtLeast(0))
            }
        )
        putEncrypted(KEY_FOLDER_DETAIL_SCROLL_JSON, obj.toString())
    }

    fun isEyeComfortEnabled(): Boolean = getDecrypted(KEY_EYE_COMFORT_ENABLED, "false").toBoolean()
    fun setEyeComfortEnabled(enabled: Boolean) = putEncrypted(KEY_EYE_COMFORT_ENABLED, enabled.toString())

    fun getEyeComfortWarmth(): Float = getDecrypted(KEY_EYE_COMFORT_WARMTH, "0.35").toFloatOrNull() ?: 0.35f
    fun setEyeComfortWarmth(warmth: Float) = putEncrypted(KEY_EYE_COMFORT_WARMTH, warmth.coerceIn(0f, 0.85f).toString())

    fun getExtraDimIntensity(): Float = getDecrypted(KEY_EXTRA_DIM, "0.0").toFloatOrNull() ?: 0f
    fun setExtraDimIntensity(dim: Float) = putEncrypted(KEY_EXTRA_DIM, dim.coerceIn(0f, 0.75f).toString())

    fun isNightModeEnabled(): Boolean = getDecrypted(KEY_UNIFIED_NIGHT_MODE, "false").toBoolean()
    fun setNightModeEnabled(enabled: Boolean) = putEncrypted(KEY_UNIFIED_NIGHT_MODE, enabled.toString())

    fun isNightModeAudioEnabled(): Boolean = getDecrypted(KEY_NIGHT_MODE_AUDIO, "false").toBoolean()
    fun setNightModeAudioEnabled(enabled: Boolean) = putEncrypted(KEY_NIGHT_MODE_AUDIO, enabled.toString())

    fun isPerformanceHudEnabled(): Boolean = getDecrypted(KEY_PERFORMANCE_HUD_ENABLED, "false").toBoolean()
    fun setPerformanceHudEnabled(enabled: Boolean) = putEncrypted(KEY_PERFORMANCE_HUD_ENABLED, enabled.toString())

    fun getSubtitleFontSize(): SubtitleFontSize {
        val name = getDecrypted(KEY_SUB_FONT_SIZE, SubtitleFontSize.LARGE.name)
        return SubtitleFontSize.entries.find { it.name == name } ?: SubtitleFontSize.LARGE
    }

    fun setSubtitleFontSize(size: SubtitleFontSize) {
        putEncrypted(KEY_SUB_FONT_SIZE, size.name)
        setSubtitleCustomSp(size.spValue)
    }

    fun getSubtitleCustomSp(): Float {
        val raw = getDecrypted(KEY_SUB_CUSTOM_SP, "24.0")
        return raw.toFloatOrNull()?.coerceIn(12f, 64f) ?: 24f
    }

    fun setSubtitleCustomSp(sp: Float) {
        val clamped = sp.coerceIn(12f, 64f)
        putEncrypted(KEY_SUB_CUSTOM_SP, clamped.toString())
    }

    fun getSubtitleColor(): SubtitleColorOption {
        val name = getDecrypted(KEY_SUB_COLOR, SubtitleColorOption.WHITE.name)
        return SubtitleColorOption.entries.find { it.name == name } ?: SubtitleColorOption.WHITE
    }

    fun setSubtitleColor(color: SubtitleColorOption) = putEncrypted(KEY_SUB_COLOR, color.name)

    fun getSubtitleOutlineColor(): SubtitleOutlineColorOption {
        val name = getDecrypted(KEY_SUB_OUTLINE_COLOR, SubtitleOutlineColorOption.BLACK.name)
        return SubtitleOutlineColorOption.entries.find { it.name == name } ?: SubtitleOutlineColorOption.BLACK
    }

    fun setSubtitleOutlineColor(color: SubtitleOutlineColorOption) = putEncrypted(KEY_SUB_OUTLINE_COLOR, color.name)

    fun getSubtitleEdgeStyle(): SubtitleEdgeStyle {
        val name = getDecrypted(KEY_SUB_EDGE_STYLE, SubtitleEdgeStyle.DROP_SHADOW.name)
        return SubtitleEdgeStyle.entries.find { it.name == name } ?: SubtitleEdgeStyle.DROP_SHADOW
    }

    fun setSubtitleEdgeStyle(style: SubtitleEdgeStyle) = putEncrypted(KEY_SUB_EDGE_STYLE, style.name)

    fun getSubtitleVerticalPreset(): SubtitleVerticalPreset {
        val name = getDecrypted(KEY_SUB_VERTICAL_PRESET, SubtitleVerticalPreset.STANDARD.name)
        return SubtitleVerticalPreset.entries.find { it.name == name } ?: SubtitleVerticalPreset.STANDARD
    }

    fun setSubtitleVerticalPreset(preset: SubtitleVerticalPreset) = putEncrypted(KEY_SUB_VERTICAL_PRESET, preset.name)

    fun isResumePlaybackEnabled(): Boolean = getDecrypted(KEY_RESUME_PLAYBACK, "true").toBoolean()
    fun setResumePlaybackEnabled(enabled: Boolean) = putEncrypted(KEY_RESUME_PLAYBACK, enabled.toString())

    fun isAutoPlayNextEnabled(): Boolean = getDecrypted(KEY_AUTO_PLAY_NEXT, "true").toBoolean()
    fun setAutoPlayNextEnabled(enabled: Boolean) = putEncrypted(KEY_AUTO_PLAY_NEXT, enabled.toString())

    // --- Folders & Videos View Mode, Sort, Layout, Advanced & Fields (Encrypted) ---

    fun getFolderBrowserViewMode(): BrowserViewMode {
        val name = getDecrypted(KEY_FOLDER_VIEW_MODE, BrowserViewMode.LIST.name)
        return BrowserViewMode.entries.find { it.name == name } ?: BrowserViewMode.LIST
    }

    fun setFolderBrowserViewMode(mode: BrowserViewMode) = putEncrypted(KEY_FOLDER_VIEW_MODE, mode.name)

    fun getVideoBrowserViewMode(): BrowserViewMode {
        val name = getDecrypted(KEY_VIDEO_VIEW_MODE, BrowserViewMode.LIST.name)
        return BrowserViewMode.entries.find { it.name == name } ?: BrowserViewMode.LIST
    }

    fun setVideoBrowserViewMode(mode: BrowserViewMode) = putEncrypted(KEY_VIDEO_VIEW_MODE, mode.name)

    fun getFolderSortDirection(): SortDirection {
        val name = getDecrypted(KEY_FOLDER_SORT_DIR, SortDirection.ASCENDING.name)
        return SortDirection.entries.find { it.name == name } ?: SortDirection.ASCENDING
    }

    fun setFolderSortDirection(dir: SortDirection) = putEncrypted(KEY_FOLDER_SORT_DIR, dir.name)

    fun getVideoSortDirection(): SortDirection {
        val name = getDecrypted(KEY_VIDEO_SORT_DIR, SortDirection.DESCENDING.name)
        return SortDirection.entries.find { it.name == name } ?: SortDirection.DESCENDING
    }

    fun setVideoSortDirection(dir: SortDirection) = putEncrypted(KEY_VIDEO_SORT_DIR, dir.name)

    fun getBrowserLayoutColumns(): BrowserLayoutColumns {
        val name = getDecrypted(KEY_LAYOUT_COLUMNS, BrowserLayoutColumns.AUTO.name)
        return BrowserLayoutColumns.entries.find { it.name == name } ?: BrowserLayoutColumns.AUTO
    }

    fun setBrowserLayoutColumns(columns: BrowserLayoutColumns) = putEncrypted(KEY_LAYOUT_COLUMNS, columns.name)

    fun getBrowserThumbnailRatio(): BrowserThumbnailRatio {
        val name = getDecrypted(KEY_LAYOUT_THUMBNAIL_RATIO, BrowserThumbnailRatio.WIDESCREEN_16_9.name)
        return BrowserThumbnailRatio.entries.find { it.name == name } ?: BrowserThumbnailRatio.WIDESCREEN_16_9
    }

    fun setBrowserThumbnailRatio(ratio: BrowserThumbnailRatio) = putEncrypted(KEY_LAYOUT_THUMBNAIL_RATIO, ratio.name)

    fun getAdvancedCodecFilter(): AdvancedCodecFilter {
        val name = getDecrypted(KEY_ADVANCED_CODEC_FILTER, AdvancedCodecFilter.ALL.name)
        return AdvancedCodecFilter.entries.find { it.name == name } ?: AdvancedCodecFilter.ALL
    }

    fun setAdvancedCodecFilter(filter: AdvancedCodecFilter) = putEncrypted(KEY_ADVANCED_CODEC_FILTER, filter.name)

    fun getResolutionFilterTier(): ResolutionFilterTier {
        val name = getDecrypted(KEY_ADVANCED_RESOLUTION_FILTER, ResolutionFilterTier.ALL.name)
        return ResolutionFilterTier.entries.find { it.name == name } ?: ResolutionFilterTier.ALL
    }

    fun setResolutionFilterTier(tier: ResolutionFilterTier) = putEncrypted(KEY_ADVANCED_RESOLUTION_FILTER, tier.name)

    fun isIncludeSubfoldersRecursive(): Boolean =
        getDecrypted(KEY_ADVANCED_RECURSIVE_SUBFOLDERS, "false").toBoolean()

    fun setIncludeSubfoldersRecursive(enabled: Boolean) =
        putEncrypted(KEY_ADVANCED_RECURSIVE_SUBFOLDERS, enabled.toString())

    fun getFolderBrowserConfigs(): Map<String, FolderBrowserConfig> {
        val raw = getDecrypted(KEY_FOLDER_BROWSER_CONFIGS_JSON, "")
        if (raw.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, FolderBrowserConfig>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val folderId = keys.next()
                val item = obj.optJSONObject(folderId) ?: continue
                val viewName = item.optString("viewMode", BrowserViewMode.LIST.name)
                val sortName = item.optString("sortOption", VideoSortOption.DATE_DESC.name)
                val dirName = item.optString("sortDirection", SortDirection.DESCENDING.name)
                val colName = item.optString("layoutColumns", BrowserLayoutColumns.AUTO.name)
                val ratioName = item.optString("thumbnailRatio", BrowserThumbnailRatio.WIDESCREEN_16_9.name)
                val scrollIdx = item.optInt("lastScrollIndex", 0).coerceAtLeast(0)
                val scrollOff = item.optInt("lastScrollOffset", 0).coerceAtLeast(0)
                result[folderId] = FolderBrowserConfig(
                    viewMode = BrowserViewMode.entries.find { it.name == viewName } ?: BrowserViewMode.LIST,
                    sortOption = VideoSortOption.entries.find { it.name == sortName } ?: VideoSortOption.DATE_DESC,
                    sortDirection = SortDirection.entries.find { it.name == dirName } ?: SortDirection.DESCENDING,
                    layoutColumns = BrowserLayoutColumns.entries.find { it.name == colName } ?: BrowserLayoutColumns.AUTO,
                    thumbnailRatio = BrowserThumbnailRatio.entries.find { it.name == ratioName } ?: BrowserThumbnailRatio.WIDESCREEN_16_9,
                    lastScrollIndex = scrollIdx,
                    lastScrollOffset = scrollOff
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveFolderBrowserConfig(folderId: String, config: FolderBrowserConfig) {
        if (folderId.isBlank()) return
        val current = getFolderBrowserConfigs().toMutableMap()
        current[folderId] = config
        val root = JSONObject()
        current.forEach { (id, cfg) ->
            root.put(
                id,
                JSONObject().apply {
                    put("viewMode", cfg.viewMode.name)
                    put("sortOption", cfg.sortOption.name)
                    put("sortDirection", cfg.sortDirection.name)
                    put("layoutColumns", cfg.layoutColumns.name)
                    put("thumbnailRatio", cfg.thumbnailRatio.name)
                    put("lastScrollIndex", cfg.lastScrollIndex)
                    put("lastScrollOffset", cfg.lastScrollOffset)
                }
            )
        }
        putEncrypted(KEY_FOLDER_BROWSER_CONFIGS_JSON, root.toString())
    }

    fun getBrowserVisibleFields(): BrowserVisibleFields {
        val raw = getDecrypted(KEY_BROWSER_VISIBLE_FIELDS_JSON, "")
        if (raw.isBlank()) return BrowserVisibleFields()
        return try {
            val obj = JSONObject(raw)
            BrowserVisibleFields(
                showVideoPicturePreview = obj.optBoolean("showVideoPicturePreview", true),
                showFolderCollagePreview = obj.optBoolean("showFolderCollagePreview", true),
                showResolutionBadge = obj.optBoolean("showResolutionBadge", true),
                showContainerBadge = obj.optBoolean("showContainerBadge", true),
                showCodecBadges = obj.optBoolean("showCodecBadges", true),
                showBitrateAndFps = obj.optBoolean("showBitrateAndFps", true),
                showFileSize = obj.optBoolean("showFileSize", true),
                showDuration = obj.optBoolean("showDuration", true),
                showFolderPath = obj.optBoolean("showFolderPath", true),
                showMatroskaEbmlTag = obj.optBoolean("showMatroskaEbmlTag", true),
                showEncryptedDatacodeBadge = obj.optBoolean("showEncryptedDatacodeBadge", true)
            )
        } catch (_: Exception) {
            BrowserVisibleFields()
        }
    }

    fun setBrowserVisibleFields(fields: BrowserVisibleFields) {
        val obj = JSONObject().apply {
            put("showVideoPicturePreview", fields.showVideoPicturePreview)
            put("showFolderCollagePreview", fields.showFolderCollagePreview)
            put("showResolutionBadge", fields.showResolutionBadge)
            put("showContainerBadge", fields.showContainerBadge)
            put("showCodecBadges", fields.showCodecBadges)
            put("showBitrateAndFps", fields.showBitrateAndFps)
            put("showFileSize", fields.showFileSize)
            put("showDuration", fields.showDuration)
            put("showFolderPath", fields.showFolderPath)
            put("showMatroskaEbmlTag", fields.showMatroskaEbmlTag)
            put("showEncryptedDatacodeBadge", fields.showEncryptedDatacodeBadge)
        }
        putEncrypted(KEY_BROWSER_VISIBLE_FIELDS_JSON, obj.toString())
    }

    fun isHideAllTextsEnabled(): Boolean {
        return getDecrypted(KEY_HIDE_ALL_TEXTS_MODE, "false").toBooleanStrictOrNull() ?: false
    }

    fun setHideAllTextsEnabled(enabled: Boolean) {
        putEncrypted(KEY_HIDE_ALL_TEXTS_MODE, enabled.toString())
    }

    fun isAutoPipEnabled(): Boolean {
        return getDecrypted(KEY_AUTO_PIP_ON_HOME, "true").toBooleanStrictOrNull() ?: true
    }

    fun setAutoPipEnabled(enabled: Boolean) {
        putEncrypted(KEY_AUTO_PIP_ON_HOME, enabled.toString())
    }

    fun isForceHardwareAccelerationHighBitrateEnabled(): Boolean {
        return getDecrypted(KEY_FORCE_HW_ACCEL_HIGH_BITRATE, "false").toBooleanStrictOrNull() ?: false
    }

    fun setForceHardwareAccelerationHighBitrateEnabled(enabled: Boolean) {
        putEncrypted(KEY_FORCE_HW_ACCEL_HIGH_BITRATE, enabled.toString())
    }

    fun isAutoSkipIntrosCreditsEnabled(): Boolean {
        return getDecrypted(KEY_AUTO_SKIP_INTROS_CREDITS, "false").toBooleanStrictOrNull() ?: false
    }

    fun setAutoSkipIntrosCreditsEnabled(enabled: Boolean) {
        putEncrypted(KEY_AUTO_SKIP_INTROS_CREDITS, enabled.toString())
    }

    fun getNightShieldWarmthLevel(): Float {
        return getDecrypted(KEY_NIGHT_SHIELD_WARMTH, "0.38").toFloatOrNull() ?: 0.38f
    }

    fun setNightShieldWarmthLevel(level: Float) {
        val clamped = level.coerceIn(0.1f, 0.85f)
        putEncrypted(KEY_NIGHT_SHIELD_WARMTH, clamped.toString())
    }

    fun getMiniPlayerOffsetX(): Float {
        return getDecrypted(KEY_MINI_PLAYER_OFFSET_X, "0.0").toFloatOrNull() ?: 0f
    }

    fun setMiniPlayerOffsetX(offsetX: Float) {
        putEncrypted(KEY_MINI_PLAYER_OFFSET_X, offsetX.toString())
    }

    fun getMiniPlayerOffsetY(): Float {
        return getDecrypted(KEY_MINI_PLAYER_OFFSET_Y, "0.0").toFloatOrNull() ?: 0f
    }

    fun setMiniPlayerOffsetY(offsetY: Float) {
        putEncrypted(KEY_MINI_PLAYER_OFFSET_Y, offsetY.toString())
    }

    fun getMiniPlayerPositionPreset(): MiniPlayerPositionPreset {
        val name = getDecrypted(KEY_MINI_PLAYER_POSITION_PRESET, MiniPlayerPositionPreset.BOTTOM_CENTER.name)
        return MiniPlayerPositionPreset.entries.find { it.name == name } ?: MiniPlayerPositionPreset.BOTTOM_CENTER
    }

    fun setMiniPlayerPositionPreset(preset: MiniPlayerPositionPreset) {
        putEncrypted(KEY_MINI_PLAYER_POSITION_PRESET, preset.name)
    }

    fun clearPlaybackProgressForVideo(videoPath: String) {
        if (videoPath.isBlank()) return
        val current = getAllPlaybackProgress().toMutableMap()
        current.remove(videoPath)
        val arr = JSONArray()
        current.values.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("path", item.videoPath)
                    put("pos", item.lastPositionMs)
                    put("dur", item.durationMs)
                    put("ts", item.updatedAtMs)
                }
            )
        }
        putEncrypted(KEY_PLAYBACK_PROGRESS_JSON, arr.toString())
    }

    fun clearCachedReferencesForVideoPaths(videoPaths: Set<String>) {
        if (videoPaths.isEmpty()) return
        val normalizedTargets = videoPaths.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (normalizedTargets.isEmpty()) return

        // 1. Remove playback progress entries
        val currentProgress = getAllPlaybackProgress().toMutableMap()
        val removedProgress = currentProgress.keys.removeAll { key ->
            normalizedTargets.any { it.equals(key.trim(), ignoreCase = true) }
        }
        if (removedProgress) {
            val arr = JSONArray()
            currentProgress.values.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("path", item.videoPath)
                        put("pos", item.lastPositionMs)
                        put("dur", item.durationMs)
                        put("ts", item.updatedAtMs)
                    }
                )
            }
            putEncrypted(KEY_PLAYBACK_PROGRESS_JSON, arr.toString())
        }

        // 2. Clear lastPlayedVideoPath if it matches any removed path
        val lastPath = getLastPlayedVideoPath()
        if (lastPath != null && normalizedTargets.any { it.equals(lastPath.trim(), ignoreCase = true) }) {
            setLastPlayedVideoPath(null)
        }

        // 3. Remove folder last-played video references pointing to deleted/moved paths
        val folderLastMap = getAllFolderLastPlayedVideos().toMutableMap()
        val removedFolderLast = folderLastMap.entries.removeAll { (_, vPath) ->
            normalizedTargets.any { it.equals(vPath.trim(), ignoreCase = true) }
        }
        if (removedFolderLast) {
            val obj = JSONObject()
            folderLastMap.forEach { (id, path) -> obj.put(id, path) }
            putEncrypted(KEY_FOLDER_LAST_PLAYED_JSON, obj.toString())
        }

        // 4. Remove bookmarks for deleted paths
        val bookmarks = getAllBookmarksMap().toMutableMap()
        if (bookmarks.keys.removeAll { key -> normalizedTargets.any { it.equals(key.trim(), ignoreCase = true) } }) {
            saveAllBookmarksMap(bookmarks)
        }

        // 5. Remove new-video dot paths
        val newPaths = getNewVideoPaths().toMutableSet()
        if (newPaths.removeAll { p -> normalizedTargets.any { it.equals(p.trim(), ignoreCase = true) } }) {
            val arr = JSONArray()
            newPaths.forEach { arr.put(it) }
            putEncrypted(KEY_NEW_VIDEO_PATHS_JSON, arr.toString())
        }

        // 6. Remove saved per-video audio track selections for deleted paths
        val audioMap = getAllSavedAudioTracks().toMutableMap()
        if (audioMap.keys.removeAll { key -> normalizedTargets.any { it.equals(key.trim(), ignoreCase = true) } }) {
            saveAllAudioTracksMap(audioMap)
        }
    }

    fun getAllSavedAudioTracks(): Map<String, SavedAudioTrackSelection> {
        val raw = getDecrypted(KEY_VIDEO_AUDIO_TRACK_MAP_JSON, "{}")
        if (raw.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, SavedAudioTrackSelection>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val path = keys.next().trim()
                if (path.isNotEmpty()) {
                    val item = obj.optJSONObject(path) ?: continue
                    result[path] = SavedAudioTrackSelection(
                        groupIndex = item.optInt("groupIndex", 0),
                        trackIndex = item.optInt("trackIndex", 0),
                        language = item.optString("language", ""),
                        label = item.optString("label", ""),
                        codecBadge = item.optString("codecBadge", ""),
                        isMuted = item.optBoolean("isMuted", false)
                    )
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getSavedAudioTrackForVideo(videoPath: String): SavedAudioTrackSelection? {
        val clean = videoPath.trim()
        if (clean.isEmpty()) return null
        val map = getAllSavedAudioTracks()
        return map[clean] ?: map.entries.firstOrNull { it.key.equals(clean, ignoreCase = true) }?.value
    }

    fun setSavedAudioTrackForVideo(videoPath: String, selection: SavedAudioTrackSelection) {
        val clean = videoPath.trim()
        if (clean.isEmpty()) return
        val map = getAllSavedAudioTracks().toMutableMap()
        val existingKey = map.keys.firstOrNull { it.equals(clean, ignoreCase = true) }
        if (existingKey != null && existingKey != clean) {
            map.remove(existingKey)
        }
        map[clean] = selection
        saveAllAudioTracksMap(map)
    }

    private fun saveAllAudioTracksMap(map: Map<String, SavedAudioTrackSelection>) {
        val obj = JSONObject()
        map.forEach { (path, sel) ->
            if (path.isNotBlank()) {
                obj.put(
                    path,
                    JSONObject().apply {
                        put("groupIndex", sel.groupIndex)
                        put("trackIndex", sel.trackIndex)
                        put("language", sel.language)
                        put("label", sel.label)
                        put("codecBadge", sel.codecBadge)
                        put("isMuted", sel.isMuted)
                    }
                )
            }
        }
        putEncrypted(KEY_VIDEO_AUDIO_TRACK_MAP_JSON, obj.toString())
    }

    /**
     * Relinks all saved video state (playback progress, lastPlayedVideoPath, folderLastPlayedMap,
     * bookmarks, videoBrightnessMap, videoVolumeMap, and newVideoPaths) from [oldPath] to [newPath]
     * when a video is moved to another folder.
     */
    fun relinkMovedVideoReferences(
        oldPath: String,
        newPath: String,
        newFolderId: String? = null,
        newFolderPath: String? = null
    ) {
        val cleanOld = oldPath.trim()
        val cleanNew = newPath.trim()
        if (cleanOld.isEmpty() || cleanNew.isEmpty() || cleanOld.equals(cleanNew, ignoreCase = true)) return

        // 1. Relink playback progress
        val currentProgress = getAllPlaybackProgress().toMutableMap()
        val matchingKey = currentProgress.keys.firstOrNull { it.trim().equals(cleanOld, ignoreCase = true) }
        if (matchingKey != null) {
            val oldEntry = currentProgress.remove(matchingKey)
            if (oldEntry != null) {
                currentProgress[cleanNew] = oldEntry.copy(videoPath = cleanNew)
            }
            val arr = JSONArray()
            currentProgress.values.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("path", item.videoPath)
                        put("pos", item.lastPositionMs)
                        put("dur", item.durationMs)
                        put("ts", item.updatedAtMs)
                    }
                )
            }
            putEncrypted(KEY_PLAYBACK_PROGRESS_JSON, arr.toString())
        }

        // 2. Relink lastPlayedVideoPath
        val lastPath = getLastPlayedVideoPath()
        if (lastPath != null && lastPath.trim().equals(cleanOld, ignoreCase = true)) {
            setLastPlayedVideoPath(cleanNew)
        }

        // 3. Relink folder last-played references
        val folderLastMap = getAllFolderLastPlayedVideos().toMutableMap()
        var folderMapChanged = false
        val oldParentDir = cleanOld.substringBeforeLast('/', "").trim()
        val newParentDir = newFolderPath?.trim()?.trimEnd('/')?.ifEmpty { null }
            ?: cleanNew.substringBeforeLast('/', "").trim()
        folderLastMap.entries.toList().forEach { (folderKey, vPath) ->
            if (vPath.trim().equals(cleanOld, ignoreCase = true)) {
                if (oldParentDir.isNotEmpty() && folderKey.equals(oldParentDir, ignoreCase = true)) {
                    folderLastMap.remove(folderKey)
                } else {
                    folderLastMap[folderKey] = cleanNew
                }
                folderMapChanged = true
            }
        }
        if (newParentDir.isNotEmpty() && folderMapChanged) {
            folderLastMap[newParentDir] = cleanNew
            folderLastMap[newFolderId?.takeIf { it.isNotBlank() } ?: newParentDir.lowercase().hashCode().toString()] = cleanNew
        }
        if (folderMapChanged) {
            val obj = JSONObject()
            folderLastMap.forEach { (id, path) -> obj.put(id, path) }
            putEncrypted(KEY_FOLDER_LAST_PLAYED_JSON, obj.toString())
        }

        // 4. Relink bookmarks
        val bookmarks = getAllBookmarksMap().toMutableMap()
        val bmKey = bookmarks.keys.firstOrNull { it.trim().equals(cleanOld, ignoreCase = true) }
        if (bmKey != null) {
            val savedMarks = bookmarks.remove(bmKey)
            if (savedMarks != null) {
                bookmarks[cleanNew] = savedMarks
            }
            saveAllBookmarksMap(bookmarks)
        }

        // 5. Relink per-video brightness & volume maps
        val brightMap = getVideoBrightnessMap().toMutableMap()
        val bKey = brightMap.keys.firstOrNull { it.trim().equals(cleanOld, ignoreCase = true) }
        if (bKey != null) {
            val bVal = brightMap.remove(bKey)
            if (bVal != null) brightMap[cleanNew] = bVal
            val obj = JSONObject()
            brightMap.forEach { (k, v) -> obj.put(k, v.toDouble()) }
            putEncrypted(KEY_VIDEO_BRIGHTNESS_MAP_JSON, obj.toString())
        }

        val volMap = getVideoVolumeMap().toMutableMap()
        val vKey = volMap.keys.firstOrNull { it.trim().equals(cleanOld, ignoreCase = true) }
        if (vKey != null) {
            val vVal = volMap.remove(vKey)
            if (vVal != null) volMap[cleanNew] = vVal
            val obj = JSONObject()
            volMap.forEach { (k, v) -> obj.put(k, v.toDouble()) }
            putEncrypted(KEY_VIDEO_VOLUME_MAP_JSON, obj.toString())
        }

        // 6. Relink new-video dot path
        val newPaths = getNewVideoPaths().toMutableSet()
        if (newPaths.removeAll { it.trim().equals(cleanOld, ignoreCase = true) }) {
            newPaths.add(cleanNew)
            val arr = JSONArray()
            newPaths.forEach { arr.put(it) }
            putEncrypted(KEY_NEW_VIDEO_PATHS_JSON, arr.toString())
        }

        // 7. Relink per-video saved audio track selection
        val audioMap = getAllSavedAudioTracks().toMutableMap()
        val aKey = audioMap.keys.firstOrNull { it.trim().equals(cleanOld, ignoreCase = true) }
        if (aKey != null) {
            val savedAudio = audioMap.remove(aKey)
            if (savedAudio != null) {
                audioMap[cleanNew] = savedAudio
                saveAllAudioTracksMap(audioMap)
            }
        }
    }

    fun getFoldersWithNewVideos(): Set<String> {
        val raw = getDecrypted(KEY_FOLDERS_WITH_NEW_VIDEOS_JSON, "[]")
        return try {
            val arr = JSONArray(raw)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val key = arr.optString(i).trim().trimEnd('/')
                if (key.isNotEmpty()) {
                    set.add(key)
                }
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun markFolderHasNewVideo(folderPathOrId: String) {
        val normalized = folderPathOrId.trim().trimEnd('/')
        if (normalized.isEmpty()) return
        val updated = getFoldersWithNewVideos().toMutableSet()
        updated.add(normalized)
        val arr = JSONArray()
        updated.forEach { arr.put(it) }
        putEncrypted(KEY_FOLDERS_WITH_NEW_VIDEOS_JSON, arr.toString())
    }

    fun clearFolderNewVideoIndicator(folderPathOrId: String) {
        val normalized = folderPathOrId.trim().trimEnd('/')
        if (normalized.isEmpty()) return
        val updated = getFoldersWithNewVideos().toMutableSet()
        updated.removeAll { it.equals(normalized, ignoreCase = true) }
        val arr = JSONArray()
        updated.forEach { arr.put(it) }
        putEncrypted(KEY_FOLDERS_WITH_NEW_VIDEOS_JSON, arr.toString())
        clearFolderNewVideoCount(normalized)
    }

    fun getNewVideoPaths(): Set<String> {
        val raw = getDecrypted(KEY_NEW_VIDEO_PATHS_JSON, "[]")
        return try {
            val arr = JSONArray(raw)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val p = arr.optString(i).trim()
                if (p.isNotEmpty()) {
                    set.add(p)
                }
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun markVideoPathAsNew(videoPath: String) {
        val norm = videoPath.trim()
        if (norm.isEmpty()) return
        val updated = getNewVideoPaths().toMutableSet()
        updated.add(norm)
        val arr = JSONArray()
        updated.forEach { arr.put(it) }
        putEncrypted(KEY_NEW_VIDEO_PATHS_JSON, arr.toString())
    }

    fun clearNewVideoPath(videoPath: String) {
        val norm = videoPath.trim()
        if (norm.isEmpty()) return
        val updated = getNewVideoPaths().toMutableSet()
        updated.removeAll { it.equals(norm, ignoreCase = true) }
        val arr = JSONArray()
        updated.forEach { arr.put(it) }
        putEncrypted(KEY_NEW_VIDEO_PATHS_JSON, arr.toString())
    }

    fun getFolderNewVideoCounts(): Map<String, Int> {
        val raw = getDecrypted(KEY_FOLDER_NEW_VIDEO_COUNTS_JSON, "{}")
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next().trim().trimEnd('/')
                val count = obj.optInt(k, 0)
                if (k.isNotEmpty() && count > 0) {
                    map[k] = count
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun setFolderNewVideoCount(folderPathOrId: String, count: Int) {
        val norm = folderPathOrId.trim().trimEnd('/')
        if (norm.isEmpty()) return
        val map = getFolderNewVideoCounts().toMutableMap()
        if (count <= 0) {
            map.keys.removeAll { it.equals(norm, ignoreCase = true) }
        } else {
            map[norm] = count
        }
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        putEncrypted(KEY_FOLDER_NEW_VIDEO_COUNTS_JSON, obj.toString())
    }

    fun clearFolderNewVideoCount(folderPathOrId: String) {
        setFolderNewVideoCount(folderPathOrId, 0)
    }

    fun getSeenFolderVideoCounts(): Map<String, Int> {
        val raw = getDecrypted(KEY_SEEN_FOLDER_VIDEO_COUNTS_JSON, "{}")
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optInt(k, 0)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveSeenFolderVideoCounts(counts: Map<String, Int>) {
        val obj = JSONObject()
        counts.forEach { (k, v) ->
            val key = k.trim().trimEnd('/')
            if (key.isNotEmpty()) {
                obj.put(key, v)
            }
        }
        putEncrypted(KEY_SEEN_FOLDER_VIDEO_COUNTS_JSON, obj.toString())
    }

    companion object {
        private const val KEY_VAULT_PIN_TOKEN = "vault_pin_token"
        private const val KEY_DECOY_PIN_TOKEN = "decoy_pin_token"
        private const val KEY_SECURITY_QUESTION = "security_question_enc"
        private const val KEY_SECURITY_ANSWER_TOKEN = "security_answer_token"
        private const val KEY_STEALTH_PLAYBACK = "stealth_playback_enabled"
        private const val KEY_AUTO_LOCK_VAULT = "auto_lock_vault_bg"
        private const val KEY_BIOMETRIC_VAULT = "biometric_vault_enabled"
        private const val KEY_LOW_RAM_SHIELD = "low_ram_shield_enabled"
        private const val KEY_BATTERY_SAVER_MODE = "battery_saver_mode_enabled_v106rv2"
        private const val KEY_DECODER_ENGINE_MODE = "decoder_engine_mode"
        private const val KEY_FORCE_HW_ACCEL_HIGH_BITRATE = "force_hw_accel_high_bitrate_v106rv2"
        private const val KEY_AUTO_SKIP_INTROS_CREDITS = "auto_skip_intros_credits_v106rv2"
        private const val KEY_NIGHT_SHIELD_WARMTH = "night_shield_warmth_v106rv2"
        private const val KEY_MINI_PLAYER_OFFSET_X = "mini_player_offset_x_v106rv2"
        private const val KEY_MINI_PLAYER_OFFSET_Y = "mini_player_offset_y_v106rv2"
        private const val KEY_MINI_PLAYER_POSITION_PRESET = "mini_player_position_preset_v106rv2"
        private const val KEY_PLAYBACK_PROGRESS_JSON = "playback_progress_json"
        private const val KEY_LAST_PLAYED_VIDEO_PATH = "last_played_video_path_enc"
        private const val KEY_BOOKMARKS_JSON = "bookmarks_json"
        private const val KEY_HIDDEN_FOLDERS_JSON = "hidden_folders_json"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FONT_MODE = "font_mode_v104"
        private const val KEY_TEXT_SCALE_MODE = "text_scale_mode"
        private const val KEY_VIDEO_SORT = "video_sort_option"
        private const val KEY_FOLDER_SORT = "folder_sort_option"
        private const val KEY_FOLDER_EXPLORER_MODE = "folder_explorer_mode"
        private const val KEY_STORAGE_FILTER = "storage_volume_filter"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_ORIENTATION_MODE = "orientation_mode"
        private const val KEY_SMART_ENHANCEMENT = "smart_enhancement_mode_v104"
        private const val KEY_SUBTITLES_ENABLED_BY_USER = "subtitles_enabled_by_user_v104"
        private const val KEY_SAVED_PLAYER_BRIGHTNESS = "saved_player_brightness_v104"
        private const val KEY_SAVED_PLAYER_VOLUME = "saved_player_volume_v104"
        private const val KEY_FOLDER_BRIGHTNESS_MAP_JSON = "folder_brightness_map_json_v105"
        private const val KEY_FOLDER_VOLUME_MAP_JSON = "folder_volume_map_json_v105"
        private const val KEY_VIDEO_BRIGHTNESS_MAP_JSON = "video_brightness_map_json_v105rv2"
        private const val KEY_VIDEO_VOLUME_MAP_JSON = "video_volume_map_json_v105rv2"
        private const val KEY_FOLDERS_SCROLL_INDEX = "folders_scroll_index_v104"
        private const val KEY_FOLDERS_SCROLL_OFFSET = "folders_scroll_offset_v104"
        private const val KEY_VIDEOS_SCROLL_INDEX = "videos_scroll_index_v104"
        private const val KEY_VIDEOS_SCROLL_OFFSET = "videos_scroll_offset_v104"
        private const val KEY_FOLDER_DETAIL_SCROLL_JSON = "folder_detail_scroll_json_v104"
        private const val KEY_EYE_COMFORT_ENABLED = "eye_comfort_enabled"
        private const val KEY_EYE_COMFORT_WARMTH = "eye_comfort_warmth"
        private const val KEY_EXTRA_DIM = "extra_dim_intensity"
        private const val KEY_UNIFIED_NIGHT_MODE = "unified_night_mode_enabled"
        private const val KEY_NIGHT_MODE_AUDIO = "night_mode_audio_drc_enabled"
        private const val KEY_PERFORMANCE_HUD_ENABLED = "performance_hud_4k8k_enabled"
        private const val KEY_SUB_FONT_SIZE = "sub_font_size"
        private const val KEY_SUB_CUSTOM_SP = "sub_custom_sp_v10"
        private const val KEY_SUB_COLOR = "sub_color"
        private const val KEY_SUB_OUTLINE_COLOR = "sub_outline_color"
        private const val KEY_SUB_EDGE_STYLE = "sub_edge_style"
        private const val KEY_SUB_VERTICAL_PRESET = "sub_vertical_preset"
        private const val KEY_RESUME_PLAYBACK = "resume_playback"
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_FOLDER_VIEW_MODE = "folder_browser_view_mode"
        private const val KEY_VIDEO_VIEW_MODE = "video_browser_view_mode"
        private const val KEY_FOLDER_SORT_DIR = "folder_sort_direction"
        private const val KEY_VIDEO_SORT_DIR = "video_sort_direction"
        private const val KEY_LAYOUT_COLUMNS = "browser_layout_columns"
        private const val KEY_LAYOUT_THUMBNAIL_RATIO = "browser_layout_thumbnail_ratio"
        private const val KEY_ADVANCED_CODEC_FILTER = "advanced_codec_filter"
        private const val KEY_ADVANCED_RESOLUTION_FILTER = "advanced_resolution_filter"
        private const val KEY_ADVANCED_RECURSIVE_SUBFOLDERS = "advanced_recursive_subfolders"
        private const val KEY_BROWSER_VISIBLE_FIELDS_JSON = "browser_visible_fields_json"
        private const val KEY_HIDE_ALL_TEXTS_MODE = "hide_all_texts_tap_individual_mode"
        private const val KEY_AUTO_PIP_ON_HOME = "auto_pip_on_home_enabled"
        private const val KEY_FOLDER_BROWSER_CONFIGS_JSON = "per_folder_browser_configs_json_v104rv"
        private const val KEY_FOLDER_LAST_PLAYED_JSON = "per_folder_last_played_video_json_v104rv2"
        private const val KEY_SECURITY_QUESTION_2 = "security_question_2_enc"
        private const val KEY_SECURITY_ANSWER_TOKEN_2 = "security_answer_token_2"
        private const val KEY_FOLDERS_WITH_NEW_VIDEOS_JSON = "folders_with_new_videos_json_v106rv6"
        private const val KEY_SEEN_FOLDER_VIDEO_COUNTS_JSON = "seen_folder_video_counts_json_v106rv6"
        private const val KEY_NEW_VIDEO_PATHS_JSON = "new_video_paths_json_v106rv8"
        private const val KEY_FOLDER_NEW_VIDEO_COUNTS_JSON = "folder_new_video_counts_json_v106rv8"
        private const val KEY_VIDEO_AUDIO_TRACK_MAP_JSON = "video_audio_track_map_json_v106rv18"

        val PRESET_RECOVERY_QUESTIONS: List<String> = listOf(
            "What is your favorite cinema movie?",
            "What was the name of your first pet?",
            "In what city were you born?",
            "What is your favorite Childhood video game?",
            "What was the model of your first phone?",
            "What is your mother's maiden name?",
            "What is the name of your elementary school?",
            "What is your favorite book or director?"
        )
    }
}

