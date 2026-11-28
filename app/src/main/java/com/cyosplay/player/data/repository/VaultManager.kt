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

import android.content.IntentSender
import android.os.Build
import com.cyosplay.player.util.MediaStoreFileHelper

class VaultManager(private val context: Context) {

    data class VaultMoveResult(
        val entry: VaultVideoEntry?,
        val pendingSourceDeleteIntentSender: IntentSender? = null,
        val pendingSourceDeleteUri: Uri? = null,
        val pendingSourceDeletePath: String? = null
    )

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cyosplay_vault_store_v2", Context.MODE_PRIVATE)
    private val vaultDir: File = File(appContext.filesDir, "private_encrypted_vault").apply { mkdirs() }
    private val tempPlaybackDir: File = File(appContext.cacheDir, "vault_ephemeral_play").apply { mkdirs() }

    private var stagedRotationPairs: List<Pair<File, File>> = emptyList()
    private var stagedHeaderRewraps: List<Pair<File, ByteArray>> = emptyList()
    private var stagedCatalogJson: String? = null

    private val rotationListener = object : CryptoSecurityManager.TwoPhaseKeyRotationListener {
        override fun prepareMasterKeyRotation(oldKey: SecretKey, newKey: SecretKey): Boolean {
            return prepareReEncryptAllVaultEntries(oldKey, newKey)
        }

        override fun commitMasterKeyRotation(newKey: SecretKey) {
            commitReEncryptAllVaultEntries(newKey)
        }

        override fun rollbackMasterKeyRotation(oldKey: SecretKey) {
            rollbackReEncryptAllVaultEntries()
        }
    }

    init {
        // Clean up any leftover ephemeral decrypted files or interrupted staging files on startup
        wipeEphemeralPlaybackCache()
        cleanupInterruptedStagingFiles()
        CryptoSecurityManager.registerRotationListener(rotationListener)
    }

    fun release() {
        CryptoSecurityManager.unregisterRotationListener(rotationListener)
    }

    @Synchronized
    private fun cleanupInterruptedStagingFiles() {
        vaultDir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.endsWith(ROTATION_STAGE_SUFFIX)) {
                val targetName = name.removeSuffix(ROTATION_STAGE_SUFFIX)
                val targetFile = File(vaultDir, targetName)
                val stageValidUnderCurrentKey = runCatching {
                    CryptoSecurityManager.verifyEncryptedFileIntegrity(appContext, file)
                }.getOrDefault(false)
                if (stageValidUnderCurrentKey) {
                    if (targetFile.exists()) targetFile.delete()
                    if (!file.renameTo(targetFile)) {
                        runCatching {
                            file.copyTo(targetFile, overwrite = true)
                            CryptoSecurityManager.shredFileZeroTrace(file)
                        }
                    }
                } else {
                    CryptoSecurityManager.shredFileZeroTrace(file)
                }
            } else if (
                name.endsWith(".tmp") ||
                name.contains(".tmp_") ||
                name.contains(".dec_tmp_") ||
                name.contains(".rot_plain_") ||
                name.contains(".rot_enc_")
            ) {
                CryptoSecurityManager.shredFileZeroTrace(file)
            }
        }
    }

    @Synchronized
    private fun prepareReEncryptAllVaultEntries(oldKey: SecretKey, newKey: SecretKey): Boolean {
        val raw = prefs.getString(KEY_VAULT_ENTRIES_ENC, null) ?: return true
        val decryptedJson = CryptoSecurityManager.decryptStringWithKey(raw, oldKey) ?: return false
        val entries = parseEntriesJson(decryptedJson)
        val stagedPairs = mutableListOf<Pair<File, File>>()
        val stagedRewraps = mutableListOf<Pair<File, ByteArray>>()

        for (entry in entries) {
            val encFile = File(entry.encryptedFilePath)
            if (encFile.exists() && encFile.isFile) {
                if (CryptoSecurityManager.isStreamV4File(encFile)) {
                    // O(1) instant zero-plaintext envelope re-wrap in RAM; zero disk writes until commit
                    val preparedHeader = CryptoSecurityManager.prepareStreamV4HeaderRewrap(encFile, oldKey, newKey)
                    if (preparedHeader == null) {
                        stagedRewraps.forEach { (_, buf) -> buf.fill(0) }
                        stagedPairs.forEach { (_, s) -> CryptoSecurityManager.shredFileZeroTrace(s) }
                        return false
                    }
                    stagedRewraps.add(encFile to preparedHeader)
                } else {
                    // Legacy V3/V2 file: stream-upgrade to V4 envelope format safely
                    val stageFile = File(encFile.parentFile ?: vaultDir, "${encFile.name}$ROTATION_STAGE_SUFFIX")
                    val tempPlain = File(encFile.parentFile ?: vaultDir, "${encFile.name}.rot_plain_${System.nanoTime()}")
                    val ok = try {
                        val decOk = CryptoSecurityManager.decryptFileFromDiskWithKey(encFile, tempPlain, oldKey)
                        val encOk = decOk && CryptoSecurityManager.encryptFileToStreamV4WithKek(tempPlain, stageFile, newKey)
                        CryptoSecurityManager.shredFileZeroTrace(tempPlain)
                        encOk
                    } catch (_: Exception) {
                        CryptoSecurityManager.shredFileZeroTrace(tempPlain)
                        false
                    }
                    if (!ok) {
                        CryptoSecurityManager.shredFileZeroTrace(stageFile)
                        stagedRewraps.forEach { (_, buf) -> buf.fill(0) }
                        stagedPairs.forEach { (_, s) -> CryptoSecurityManager.shredFileZeroTrace(s) }
                        return false
                    }
                    stagedPairs.add(encFile to stageFile)
                }
            }
        }
        stagedRotationPairs = stagedPairs
        stagedHeaderRewraps = stagedRewraps
        stagedCatalogJson = decryptedJson
        return true
    }

    @Synchronized
    private fun commitReEncryptAllVaultEntries(newKey: SecretKey) {
        for ((targetFile, headerPayload) in stagedHeaderRewraps) {
            runCatching {
                CryptoSecurityManager.commitStreamV4HeaderRewrap(targetFile, headerPayload)
            }
            headerPayload.fill(0)
        }
        for ((targetFile, stageFile) in stagedRotationPairs) {
            if (stageFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                if (!stageFile.renameTo(targetFile)) {
                    runCatching {
                        stageFile.copyTo(targetFile, overwrite = true)
                        CryptoSecurityManager.shredFileZeroTrace(stageFile)
                    }
                }
            }
        }
        stagedCatalogJson?.let { json ->
            val newCipher = CryptoSecurityManager.encryptStringWithKey(json, newKey)
            prefs.edit().putString(KEY_VAULT_ENTRIES_ENC, newCipher).commit()
        }
        stagedHeaderRewraps = emptyList()
        stagedRotationPairs = emptyList()
        stagedCatalogJson = null
    }

    @Synchronized
    private fun rollbackReEncryptAllVaultEntries() {
        stagedHeaderRewraps.forEach { (_, buf) -> buf.fill(0) }
        stagedRotationPairs.forEach { (_, stageFile) ->
            if (stageFile.exists()) CryptoSecurityManager.shredFileZeroTrace(stageFile)
        }
        stagedHeaderRewraps = emptyList()
        stagedRotationPairs = emptyList()
        stagedCatalogJson = null
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
        return moveVideoToVaultWithDeleteRequest(video, isDecoyEntry).entry
    }

    /**
     * Encrypts and moves a VideoItem into the Private Vault using single-pass verified chunked AEAD streaming.
     * On Android 10+ (Scoped Storage), if direct shredding cannot delete the unencrypted source original,
     * returns a MediaStore delete IntentSender so the UI can request system-approved gallery removal.
     */
    @Synchronized
    fun moveVideoToVaultWithDeleteRequest(video: VideoItem, isDecoyEntry: Boolean = false): VaultMoveResult {
        val vaultId = UUID.randomUUID().toString()
        val encryptedFile = File(vaultDir, "$vaultId.cyosvault")
        val sourceFile = File(video.path)

        var stagedUriFile: File? = null
        val readableSourceFile: File? = when {
            sourceFile.exists() && sourceFile.isFile && sourceFile.length() > 0L -> sourceFile
            else -> {
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
            return VaultMoveResult(entry = null)
        }

        val actualSizeBytes = readableSourceFile.length().coerceAtLeast(video.sizeBytes)
        // encryptFileToDisk already performs a full AEAD integrity verification pass on the staging file
        val encryptedOk = CryptoSecurityManager.encryptFileToDisk(appContext, readableSourceFile, encryptedFile) &&
            encryptedFile.exists() &&
            encryptedFile.length() > 0L

        stagedUriFile?.let { CryptoSecurityManager.shredFileZeroTrace(it) }

        if (!encryptedOk) {
            if (encryptedFile.exists()) {
                CryptoSecurityManager.shredFileZeroTrace(encryptedFile)
            }
            return VaultMoveResult(entry = null)
        }

        // Delete the unencrypted source original; on Android 10+ Scoped Storage fall back to MediaStore delete request
        var pendingIntentSender: IntentSender? = null
        var pendingDeleteUri: Uri? = null
        val directDeleted = if (sourceFile.exists() && sourceFile.isFile) {
            CryptoSecurityManager.shredFileZeroTrace(sourceFile)
            !sourceFile.exists()
        } else {
            false
        }

        if (!directDeleted) {
            val contentUri = if (video.uri.scheme.equals("content", ignoreCase = true)) {
                video.uri
            } else {
                MediaStoreFileHelper.queryExistingMediaStoreUri(appContext.contentResolver, video.path)
            }
            if (contentUri != null) {
                val mediaDeleteOutcome = runCatching {
                    val deletedRows = appContext.contentResolver.delete(contentUri, null, null)
                    deletedRows > 0
                }.getOrElse { err ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pendingIntentSender = runCatching {
                            android.provider.MediaStore.createDeleteRequest(
                                appContext.contentResolver,
                                listOf(contentUri)
                            ).intentSender
                        }.getOrNull()
                        pendingDeleteUri = contentUri
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        err is android.app.RecoverableSecurityException
                    ) {
                        pendingIntentSender = err.userAction.actionIntent.intentSender
                        pendingDeleteUri = contentUri
                    }
                    false
                }
                if (!mediaDeleteOutcome && pendingIntentSender == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pendingIntentSender = runCatching {
                        android.provider.MediaStore.createDeleteRequest(
                            appContext.contentResolver,
                            listOf(contentUri)
                        ).intentSender
                    }.getOrNull()
                    pendingDeleteUri = contentUri
                }
            }
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
        return VaultMoveResult(
            entry = entry,
            pendingSourceDeleteIntentSender = pendingIntentSender,
            pendingSourceDeleteUri = pendingDeleteUri,
            pendingSourceDeletePath = if (pendingIntentSender != null) video.path else null
        )
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
     * Prepares a Private Vault video for playback.
     * - For V3 chunked AES-256-GCM files: streams directly in RAM via [com.cyos.player.VaultChunkedAesGcmDataSource]
     *   (`cyosvault://`), providing instant seekable playback with ZERO plaintext ever written to disk.
     * - For legacy V2 files: falls back to an ephemeral sandboxed playback file that is shredded when playback ends.
     * Never falls back to the unencrypted originalPath if verification/decryption fails; returns null instead.
     */
    @Synchronized
    fun prepareEphemeralPlaybackItem(entry: VaultVideoEntry): VideoItem? {
        val encFile = File(entry.encryptedFilePath)
        if (!encFile.exists() || !encFile.isFile || encFile.length() <= 0L) {
            return null
        }
        val ext = File(entry.originalPath).extension.ifBlank { "mp4" }

        // Zero-plaintext chunked streaming path for V4 Envelope and legacy V3 AES-256-GCM files
        if (CryptoSecurityManager.isStreamEncryptedFile(encFile)) {
            val header = CryptoSecurityManager.readStreamHeader(encFile) ?: return null
            if (header.totalPlainLength <= 0L) return null
            val streamUri = com.cyos.player.VaultChunkedAesGcmDataSource.buildVaultUri(
                encryptedFilePath = encFile.absolutePath,
                displayFileName = "${entry.originalTitle}.$ext"
            )
            // Verify chunk 0 authenticates cleanly in RAM via the streaming DataSource before launching playback
            val probeOk = runCatching {
                val ds = com.cyos.player.VaultChunkedAesGcmDataSource(appContext)
                try {
                    val spec = androidx.media3.datasource.DataSpec(streamUri)
                    val totalLen = ds.open(spec)
                    if (totalLen <= 0L) return@runCatching false
                    val probe = ByteArray(1)
                    val read = ds.read(probe, 0, 1)
                    probe.fill(0)
                    read == 1
                } finally {
                    runCatching { ds.close() }
                }
            }.getOrDefault(false)
            if (!probeOk) return null

            return VideoItem(
                id = entry.vaultId.hashCode().toLong(),
                title = entry.originalTitle,
                displayName = "${entry.originalTitle}.$ext",
                uri = streamUri,
                path = encFile.absolutePath,
                sizeBytes = header.totalPlainLength.coerceAtLeast(entry.sizeBytes),
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

        // Legacy V2 fallback (decrypt to ephemeral sandboxed cache)
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
        private const val ROTATION_STAGE_SUFFIX = ".rot_stage"
    }
}
