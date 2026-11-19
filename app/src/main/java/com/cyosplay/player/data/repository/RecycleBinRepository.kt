package com.cyosplay.player.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.cyosplay.player.data.model.RecycleBinItem
import com.cyosplay.player.data.model.VideoItem
import com.cyosplay.player.util.CryptoSecurityManager
import com.cyosplay.player.util.MediaStoreFileHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.crypto.SecretKey

class RecycleBinRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cyosplay_recycle_bin_v2", Context.MODE_PRIVATE)
    private val trashDir = File(appContext.filesDir, "encrypted_recycle_bin").apply { mkdirs() }

    private val rotationListener = CryptoSecurityManager.KeyRotationListener { oldKey, newKey ->
        reEncryptRecycleBin(oldKey, newKey)
    }

    init {
        cleanupInterruptedStagingFiles()
        CryptoSecurityManager.registerRotationListener(rotationListener)
    }

    @Synchronized
    private fun cleanupInterruptedStagingFiles() {
        trashDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp")) {
                CryptoSecurityManager.shredFileZeroTrace(file)
            }
        }
    }

    @Synchronized
    private fun reEncryptRecycleBin(oldKey: SecretKey, newKey: SecretKey) {
        val raw = prefs.getString(KEY_RECYCLE_ITEMS_ENC, null) ?: return
        val plain = CryptoSecurityManager.decryptStringWithKey(raw, oldKey) ?: return
        val items = parseRecycleBinJson(plain)
        for (item in items) {
            val trashFile = File(item.trashFilePath)
            if (trashFile.exists() && trashFile.isFile) {
                try {
                    CryptoSecurityManager.reEncryptFileOnDisk(trashFile, oldKey, newKey)
                } catch (_: Exception) {
                    // Keep existing file intact if interrupted
                }
            }
        }
        val cipher = CryptoSecurityManager.encryptStringWithKey(plain, newKey)
        prefs.edit().putString(KEY_RECYCLE_ITEMS_ENC, cipher).commit()
    }

    fun getRecycleBinItems(): List<RecycleBinItem> {
        val raw = prefs.getString(KEY_RECYCLE_ITEMS_ENC, null) ?: return emptyList()
        val json = CryptoSecurityManager.decryptString(appContext, raw) ?: return emptyList()
        return parseRecycleBinJson(json)
    }

    private fun parseRecycleBinJson(json: String): List<RecycleBinItem> {
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        RecycleBinItem(
                            recycleId = obj.optString("recycleId"),
                            originalVideoId = obj.optLong("originalVideoId"),
                            title = obj.optString("title"),
                            originalPath = obj.optString("originalPath"),
                            originalUriString = obj.optString("originalUriString"),
                            trashFilePath = obj.optString("trashFilePath"),
                            folderName = obj.optString("folderName"),
                            sizeBytes = obj.optLong("sizeBytes"),
                            durationMs = obj.optLong("durationMs"),
                            width = obj.optInt("width", 1920),
                            height = obj.optInt("height", 1080),
                            deletedAtTimestampMs = obj.optLong("deletedAtTimestampMs")
                        )
                    )
                }
            }.sortedByDescending { it.deletedAtTimestampMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Encrypts and moves a video to the Recycle Bin using streaming encryption and integrity verification.
     * Never reports success or shreds the source file unless the encrypted archive is verified intact.
     */
    @Synchronized
    fun moveVideoToRecycleBin(video: VideoItem): RecycleBinItem? {
        val recycleId = UUID.randomUUID().toString()
        val trashFile = File(trashDir, "$recycleId.trash")
        val sourceFile = File(video.path)

        var stagedUriFile: File? = null
        val readableSourceFile: File? = when {
            sourceFile.exists() && sourceFile.isFile && sourceFile.length() > 0L -> sourceFile
            else -> {
                try {
                    val scheme = video.uri.scheme?.lowercase()
                    if (scheme == "content") {
                        val tempInput = File(trashDir, "stage_in_$recycleId.tmp")
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

        val encrypted = CryptoSecurityManager.encryptFileToDisk(appContext, readableSourceFile, trashFile)
        val verified = encrypted &&
            trashFile.exists() &&
            trashFile.length() > 0L &&
            CryptoSecurityManager.verifyEncryptedFileIntegrity(appContext, trashFile)

        stagedUriFile?.let { CryptoSecurityManager.shredFileZeroTrace(it) }

        if (!verified) {
            if (trashFile.exists()) {
                CryptoSecurityManager.shredFileZeroTrace(trashFile)
            }
            return null
        }

        val resolvedMediaUri = if (video.uri.scheme.equals("content", ignoreCase = true)) {
            video.uri
        } else {
            MediaStoreFileHelper.queryExistingMediaStoreUri(appContext.contentResolver, video.path)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && resolvedMediaUri != null) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_TRASHED, 1)
                }
                appContext.contentResolver.update(resolvedMediaUri, values, null, null)
            }
        }

        val item = RecycleBinItem(
            recycleId = recycleId,
            originalVideoId = video.id,
            title = video.title,
            originalPath = video.path,
            originalUriString = (resolvedMediaUri ?: video.uri).toString(),
            trashFilePath = trashFile.absolutePath,
            folderName = video.folderName,
            sizeBytes = video.sizeBytes,
            durationMs = video.durationMs,
            width = video.width,
            height = video.height,
            deletedAtTimestampMs = System.currentTimeMillis()
        )
        val updated = getRecycleBinItems().toMutableList().apply { add(0, item) }
        saveRecycleBinItems(updated)
        return item
    }

    /**
     * Resolves all underlying MediaStore or file URIs for the specified Recycle Bin items
     * (or all items if [recycleIds] is empty) so Android 10–17 system authorization can be requested when needed.
     */
    fun resolveTargetUrisForRecycleBinItems(recycleIds: Set<String> = emptySet()): List<Uri> {
        val items = getRecycleBinItems().let { all ->
            if (recycleIds.isEmpty()) all else all.filter { it.recycleId in recycleIds }
        }
        return items.mapNotNull { item ->
            val parsedUri = item.originalUriString.takeIf { it.isNotBlank() }?.let {
                runCatching { Uri.parse(it) }.getOrNull()
            }
            when {
                parsedUri != null && parsedUri.scheme.equals("content", ignoreCase = true) -> parsedUri
                item.originalPath.isNotBlank() -> {
                    MediaStoreFileHelper.queryExistingMediaStoreUri(appContext.contentResolver, item.originalPath)
                        ?: File(item.originalPath).takeIf { it.exists() }?.let { Uri.fromFile(it) }
                }
                else -> null
            }
        }.distinct()
    }

    /**
     * Restores an encrypted Recycle Bin item back to its original path (or fallback restored_videos directory).
     * Fails safely when decryption fails: keeps the encrypted trash file and Recycle Bin entry intact and returns null.
     */
    @Synchronized
    fun restoreFromRecycleBin(recycleId: String): RecycleBinItem? {
        val current = getRecycleBinItems().toMutableList()
        val target = current.find { it.recycleId == recycleId } ?: return null
        val trashFile = File(target.trashFilePath)
        if (!trashFile.exists() || !trashFile.isFile || trashFile.length() <= 0L) {
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val origUri = target.originalUriString.takeIf { it.isNotBlank() }?.let {
                runCatching { Uri.parse(it) }.getOrNull()
            } ?: MediaStoreFileHelper.queryExistingMediaStoreUri(appContext.contentResolver, target.originalPath)
            if (origUri != null && origUri.scheme.equals("content", ignoreCase = true)) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_TRASHED, 0)
                    }
                    val extras = Bundle().apply {
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    }
                    appContext.contentResolver.update(origUri, values, extras)
                }
            }
        }

        val ext = File(target.originalPath).extension.ifBlank { "mp4" }
        val targetRestoreFile = File(target.originalPath).let { orig ->
            val parent = orig.parentFile
            if (parent != null && (parent.exists() || parent.mkdirs()) && parent.canWrite()) {
                orig
            } else {
                val fallbackDir = File(appContext.filesDir, "restored_videos").apply { mkdirs() }
                File(fallbackDir, "${target.title}.$ext")
            }
        }

        val decryptedOk = CryptoSecurityManager.decryptFileFromDisk(appContext, trashFile, targetRestoreFile)
        if (!decryptedOk || !targetRestoreFile.exists() || targetRestoreFile.length() <= 0L) {
            // Fail safely: do NOT shred trashFile and do NOT remove target from Recycle Bin catalog
            return null
        }

        CryptoSecurityManager.shredFileZeroTrace(trashFile)
        MediaScannerConnection.scanFile(appContext, arrayOf(targetRestoreFile.absolutePath), null, null)
        current.removeAll { it.recycleId == recycleId }
        saveRecycleBinItems(current)
        return target.copy(originalPath = targetRestoreFile.absolutePath)
    }

    /**
     * Permanently deletes a Recycle Bin item across Android 7 to Android 17 (API 24–37):
     * 1. Deletes the original MediaStore/SAF entry (including IS_TRASHED = 1) if present.
     * 2. Deletes/shreds the original physical file at [RecycleBinItem.originalPath] if present.
     * 3. Verifies via [MediaStoreFileHelper.isMediaActuallyDeleted] that the original file and MediaStore row are gone.
     * 4. Only after verification succeeds, shreds the private Recycle Bin archive and removes the database entry.
     */
    @Synchronized
    fun shredRecycleItemPermanently(recycleId: String): Boolean {
        val current = getRecycleBinItems().toMutableList()
        val target = current.find { it.recycleId == recycleId } ?: return false

        val origPath = target.originalPath.trim()
        val origUri = target.originalUriString.takeIf { it.isNotBlank() }?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        } ?: if (origPath.isNotEmpty()) {
            MediaStoreFileHelper.queryExistingMediaStoreUri(appContext.contentResolver, origPath)
        } else {
            null
        }

        // Step 1: Delete from SAF or MediaStore (including IS_TRASHED = 1 on Android 11–17)
        if (origUri != null && origUri.scheme.equals("content", ignoreCase = true)) {
            runCatching {
                if (DocumentsContract.isDocumentUri(appContext, origUri)) {
                    DocumentsContract.deleteDocument(appContext.contentResolver, origUri)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val delArgs = Bundle().apply {
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    }
                    appContext.contentResolver.delete(origUri, delArgs)
                } else {
                    appContext.contentResolver.delete(origUri, null, null)
                }
            }
        }

        // Step 2: Delete physical original file if it still exists on device storage
        if (origPath.isNotEmpty()) {
            val origFile = File(origPath)
            if (origFile.exists()) {
                CryptoSecurityManager.shredFileZeroTrace(origFile)
                if (origFile.exists()) {
                    runCatching { origFile.delete() }
                }
            }
        }

        // Step 3: Verify that the original physical file and MediaStore record are genuinely gone
        val actuallyDeleted = MediaStoreFileHelper.isMediaActuallyDeleted(
            context = appContext,
            uri = origUri,
            filePath = origPath.ifEmpty { null }
        )
        if (!actuallyDeleted || (origPath.isNotEmpty() && File(origPath).exists())) {
            // Do NOT remove Recycle Bin entry or shred backup if physical deletion failed or permission was denied
            return false
        }

        // Step 4: Shred private Recycle Bin archive and remove database record
        val trashFile = File(target.trashFilePath)
        if (trashFile.exists()) {
            CryptoSecurityManager.shredFileZeroTrace(trashFile)
            if (trashFile.exists()) {
                runCatching { trashFile.delete() }
            }
        }
        if (origPath.isNotEmpty()) {
            MediaScannerConnection.scanFile(appContext, arrayOf(origPath), null, null)
        }
        current.removeAll { it.recycleId == recycleId }
        saveRecycleBinItems(current)
        return true
    }

    /**
     * Permanently deletes all items in the Recycle Bin, removing only those items whose physical
     * and MediaStore deletion succeeded and keeping any unauthorized/failed items intact.
     */
    @Synchronized
    fun emptyRecycleBinZeroTrace(): Int {
        val current = getRecycleBinItems()
        var deletedCount = 0
        for (item in current) {
            if (shredRecycleItemPermanently(item.recycleId)) {
                deletedCount++
            }
        }
        return deletedCount
    }

    private fun saveRecycleBinItems(items: List<RecycleBinItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("recycleId", item.recycleId)
                    put("originalVideoId", item.originalVideoId)
                    put("title", item.title)
                    put("originalPath", item.originalPath)
                    put("originalUriString", item.originalUriString)
                    put("trashFilePath", item.trashFilePath)
                    put("folderName", item.folderName)
                    put("sizeBytes", item.sizeBytes)
                    put("durationMs", item.durationMs)
                    put("width", item.width)
                    put("height", item.height)
                    put("deletedAtTimestampMs", item.deletedAtTimestampMs)
                }
            )
        }
        val cipher = CryptoSecurityManager.encryptString(appContext, arr.toString())
        prefs.edit().putString(KEY_RECYCLE_ITEMS_ENC, cipher).commit()
    }

    companion object {
        private const val KEY_RECYCLE_ITEMS_ENC = "recycle_items_aes_gcm"
    }
}
