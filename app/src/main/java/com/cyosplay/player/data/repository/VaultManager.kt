package com.cyosplay.player.data.repository

import android.content.Context
import android.net.Uri
import com.cyosplay.player.data.model.VaultVideoEntry
import com.cyosplay.player.data.model.VideoItem
import com.cyosplay.player.util.CryptoSecurityManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.crypto.SecretKey

class VaultManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cyosplay_vault_store_v2", Context.MODE_PRIVATE)
    private val vaultDir: File = File(appContext.filesDir, "private_encrypted_vault").apply { mkdirs() }
    private val tempPlaybackDir: File = File(appContext.cacheDir, "vault_ephemeral_play").apply { mkdirs() }

    private val rotationListener = CryptoSecurityManager.KeyRotationListener { oldKey, newKey ->
        reEncryptAllVaultEntries(oldKey, newKey)
    }

    init {
        // Clean up any leftover ephemeral decrypted files or interrupted .tmp staging files on startup
        wipeEphemeralPlaybackCache()
        cleanupInterruptedStagingFiles()
        CryptoSecurityManager.registerRotationListener(rotationListener)
    }

    @Synchronized
    private fun cleanupInterruptedStagingFiles() {
        vaultDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp")) {
                CryptoSecurityManager.shredFileZeroTrace(file)
            }
        }
    }

    @Synchronized
    private fun reEncryptAllVaultEntries(oldKey: SecretKey, newKey: SecretKey) {
        val raw = prefs.getString(KEY_VAULT_ENTRIES_ENC, null) ?: return
        val decryptedJson = CryptoSecurityManager.decryptStringWithKey(raw, oldKey) ?: return
        val entries = parseEntriesJson(decryptedJson)
        for (entry in entries) {
            val encFile = File(entry.encryptedFilePath)
            if (encFile.exists() && encFile.isFile) {
                try {
                    CryptoSecurityManager.reEncryptFileOnDisk(encFile, oldKey, newKey)
                } catch (_: Exception) {
                    // Keep existing file intact if interrupted
                }
            }
        }
        val newCipher = CryptoSecurityManager.encryptStringWithKey(decryptedJson, newKey)
        prefs.edit().putString(KEY_VAULT_ENTRIES_ENC, newCipher).commit()
    }

    fun getVaultEntries(isDecoySession: Boolean = false): List<VaultVideoEntry> {
        val all = loadAllVaultEntriesInternal()
        return all.filter { it.isDecoyEntry == isDecoySession }
            .sortedByDescending { it.addedTimestampMs }
    }

    /**
     * Encrypts and moves a VideoItem into the Private Vault using chunked AEAD streaming,
     * verifies the encrypted payload integrity on disk, and only then shreds the source file.
     * Returns null if the source video does not exist or if encryption/verification fails,
     * preventing false vault entries and preventing data loss.
     */
    @Synchronized
    fun moveVideoToVault(video: VideoItem, isDecoyEntry: Boolean = false): VaultVideoEntry? {
        val vaultId = UUID.randomUUID().toString()
        val encryptedFile = File(vaultDir, "$vaultId.cyosvault")
        val sourceFile = File(video.path)

        var stagedUriFile: File? = null
        val readableSourceFile: File? = when {
            sourceFile.exists() && sourceFile.isFile && sourceFile.length() > 0L -> sourceFile
            else -> {
                // Attempt to read from ContentResolver if video is backed by a content:// URI
                try {
                    val scheme = video.uri.scheme?.lowercase()
                    if (scheme == "content") {
                        val tempInput = File(tempPlaybackDir, "stage_in_$vaultId.tmp")
                        val copied = appContext.contentResolver.openInputStream(video.uri)?.use { input ->
                            FileOutputStream(tempInput).use { output ->
                                input.copyTo(output, 64 * 1024)
                            }
                        } ?: 0L
                        if (copied > 0L && tempInput.exists() && tempInput.length() > 0L) {
                            stagedUriFile = tempInput
                            tempInput
                        } else {
                            CryptoSecurityManager.shredFileZeroTrace(tempInput)
                            null
                        }
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }

        if (readableSourceFile == null || !readableSourceFile.exists() || readableSourceFile.length() <= 0L) {
            stagedUriFile?.let { CryptoSecurityManager.shredFileZeroTrace(it) }
            return null
        }

        val actualSizeBytes = readableSourceFile.length().coerceAtLeast(video.sizeBytes)
        val encryptedOk = CryptoSecurityManager.encryptFileToDisk(appContext, readableSourceFile, encryptedFile)
        val verifiedOk = encryptedOk &&
            encryptedFile.exists() &&
            encryptedFile.length() > 0L &&
            CryptoSecurityManager.verifyEncryptedFileIntegrity(appContext, encryptedFile)

        stagedUriFile?.let { CryptoSecurityManager.shredFileZeroTrace(it) }

        if (!verifiedOk) {
            if (encryptedFile.exists()) {
                CryptoSecurityManager.shredFileZeroTrace(encryptedFile)
            }
            return null
        }

        // Only shred the original file after the encrypted vault file has been verified intact
        if (sourceFile.exists() && sourceFile.isFile) {
            CryptoSecurityManager.shredFileZeroTrace(sourceFile)
        }

        val entry = VaultVideoEntry(
            vaultId = vaultId,
            originalTitle = video.title,
            originalPath = video.path,
            encryptedFilePath = encryptedFile.absolutePath,
            sizeBytes = actualSizeBytes,
            durationMs = video.durationMs,
            width = video.width,
            height = video.height,
            addedTimestampMs = System.currentTimeMillis(),
            isDecoyEntry = isDecoyEntry
        )
        val updated = loadAllVaultEntriesInternal().toMutableList().apply { add(0, entry) }
        saveAllVaultEntriesInternal(updated)
        return entry
    }

    /**
     * Restores an encrypted Vault entry back to its original path (or fallback restored_videos directory).
     * Fails safely when decryption fails: keeps the encrypted file and vault catalog entry intact and returns null.
     */
    @Synchronized
    fun restoreVideoFromVault(vaultId: String): File? {
        val entries = loadAllVaultEntriesInternal().toMutableList()
        val target = entries.find { it.vaultId == vaultId } ?: return null
        val encFile = File(target.encryptedFilePath)
        if (!encFile.exists() || !encFile.isFile || encFile.length() <= 0L) {
            return null
        }

        val ext = File(target.originalPath).extension.ifBlank { "mp4" }
        val restoredFile = File(target.originalPath).let { orig ->
            val parent = orig.parentFile
            if (parent != null && (parent.exists() || parent.mkdirs()) && parent.canWrite()) {
                orig
            } else {
                val fallbackDir = File(appContext.filesDir, "restored_videos").apply { mkdirs() }
                File(fallbackDir, "${target.originalTitle}.$ext")
            }
        }

        val decryptedOk = CryptoSecurityManager.decryptFileFromDisk(appContext, encFile, restoredFile)
        if (!decryptedOk || !restoredFile.exists() || restoredFile.length() <= 0L) {
            // Fail safely: do NOT shred encFile and do NOT remove entry from Vault catalog
            return null
        }

        CryptoSecurityManager.shredFileZeroTrace(encFile)
        entries.removeAll { it.vaultId == vaultId }
        saveAllVaultEntriesInternal(entries)
        return restoredFile
    }

    /**
     * Permanently shreds a Vault entry with multi-pass zero-trace overwrite.
     */
    @Synchronized
    fun deleteVaultEntryPermanently(vaultId: String): Boolean {
        val entries = loadAllVaultEntriesInternal().toMutableList()
        val target = entries.find { it.vaultId == vaultId } ?: return false
        val encFile = File(target.encryptedFilePath)
        if (encFile.exists()) {
            CryptoSecurityManager.shredFileZeroTrace(encFile)
        }
        entries.removeAll { it.vaultId == vaultId }
        saveAllVaultEntriesInternal(entries)
        return true
    }

    /**
     * Decrypts a Vault video into an ephemeral sandboxed playback file that is shredded when playback ends.
     * Never falls back to the unencrypted originalPath if decryption fails; returns null instead.
     */
    @Synchronized
    fun prepareEphemeralPlaybackItem(entry: VaultVideoEntry): VideoItem? {
        val encFile = File(entry.encryptedFilePath)
        if (!encFile.exists() || !encFile.isFile || encFile.length() <= 0L) {
            return null
        }
        val ext = File(entry.originalPath).extension.ifBlank { "mp4" }
        val tempFile = File(tempPlaybackDir, "play_${entry.vaultId}.$ext")
        val decryptedOk = CryptoSecurityManager.decryptFileFromDisk(appContext, encFile, tempFile)
        if (!decryptedOk || !tempFile.exists() || tempFile.length() <= 0L) {
            if (tempFile.exists()) {
                CryptoSecurityManager.shredFileZeroTrace(tempFile)
            }
            return null
        }

        val uri = Uri.fromFile(tempFile)
        return VideoItem(
            id = entry.vaultId.hashCode().toLong(),
            title = entry.originalTitle,
            displayName = "${entry.originalTitle}.$ext",
            uri = uri,
            path = tempFile.absolutePath,
            sizeBytes = entry.sizeBytes,
            durationMs = entry.durationMs,
            dateModifiedSeconds = entry.addedTimestampMs / 1000L,
            width = entry.width,
            height = entry.height,
            mimeType = VideoRepository.resolveVideoMimeType(ext),
            folderId = "private_vault",
            folderName = "Private Vault",
            folderPath = vaultDir.absolutePath
        )
    }

    @Synchronized
    fun wipeEphemeralPlaybackCache() {
        tempPlaybackDir.listFiles()?.forEach { file ->
            CryptoSecurityManager.shredFileZeroTrace(file)
        }
    }

    private fun loadAllVaultEntriesInternal(): List<VaultVideoEntry> {
        val raw = prefs.getString(KEY_VAULT_ENTRIES_ENC, null) ?: return emptyList()
        val json = CryptoSecurityManager.decryptString(appContext, raw) ?: return emptyList()
        return parseEntriesJson(json)
    }

    private fun parseEntriesJson(json: String): List<VaultVideoEntry> {
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        VaultVideoEntry(
                            vaultId = obj.optString("vaultId"),
                            originalTitle = obj.optString("originalTitle"),
                            originalPath = obj.optString("originalPath"),
                            encryptedFilePath = obj.optString("encryptedFilePath"),
                            sizeBytes = obj.optLong("sizeBytes"),
                            durationMs = obj.optLong("durationMs"),
                            width = obj.optInt("width", 1920),
                            height = obj.optInt("height", 1080),
                            addedTimestampMs = obj.optLong("addedTimestampMs"),
                            isDecoyEntry = obj.optBoolean("isDecoyEntry", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Exports and signs a Vault video entry with the hardware-backed Keystore.
     * Decrypts the video into the signed_exports folder, computes its SHA-256 digest,
     * signs it with the Keystore RSA key, and writes a companion .sig.json manifest.
     */
    @Synchronized
    fun exportAndSignVaultEntry(vaultId: String): Pair<File, CryptoSecurityManager.ExportSignatureManifest>? {
        val entries = loadAllVaultEntriesInternal()
        val target = entries.find { it.vaultId == vaultId } ?: return null
        val encFile = File(target.encryptedFilePath)
        if (!encFile.exists() || encFile.length() <= 0L) return null

        val ext = File(target.originalPath).extension.ifBlank { "mp4" }
        val exportDir = File(appContext.filesDir, "signed_exports").apply { mkdirs() }
        val exportedFile = File(exportDir, "${target.originalTitle}.$ext")

        val decryptedOk = CryptoSecurityManager.decryptFileFromDisk(appContext, encFile, exportedFile)
        if (!decryptedOk || !exportedFile.exists() || exportedFile.length() <= 0L) {
            if (exportedFile.exists()) exportedFile.delete()
            return null
        }

        val manifest = CryptoSecurityManager.signMediaFileWithKeystore(exportedFile)
        return Pair(exportedFile, manifest)
    }

    private fun saveAllVaultEntriesInternal(entries: List<VaultVideoEntry>) {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("vaultId", entry.vaultId)
                    put("originalTitle", entry.originalTitle)
                    put("originalPath", entry.originalPath)
                    put("encryptedFilePath", entry.encryptedFilePath)
                    put("sizeBytes", entry.sizeBytes)
                    put("durationMs", entry.durationMs)
                    put("width", entry.width)
                    put("height", entry.height)
                    put("addedTimestampMs", entry.addedTimestampMs)
                    put("isDecoyEntry", entry.isDecoyEntry)
                }
            )
        }
        val cipher = CryptoSecurityManager.encryptString(appContext, arr.toString())
        prefs.edit().putString(KEY_VAULT_ENTRIES_ENC, cipher).commit()
    }

    companion object {
        private const val KEY_VAULT_ENTRIES_ENC = "vault_entries_aes_gcm"
    }
}
