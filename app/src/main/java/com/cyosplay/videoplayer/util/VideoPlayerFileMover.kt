package com.cyosplay.videoplayer.util

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Video Player File Mover (Tailored for CyosPlay / Android Video Players)
 *
 * Fixes the bug where moving a video leaves the original video inside the source folder
 * in both MediaStore.Video.Media (BUCKET_ID / folder list) and the player's UI state.
 *
 * Official Android References:
 * - Shared Storage & MediaStore: https://developer.android.com/training/data-storage/shared/media
 * - MediaStore.Video.Media: https://developer.android.com/reference/android/provider/MediaStore.Video.Media
 * - MediaStore.MediaColumns: https://developer.android.com/reference/android/provider/MediaStore.MediaColumns
 * - ContentResolver: https://developer.android.com/reference/android/content/ContentResolver
 * - MediaScannerConnection: https://developer.android.com/reference/android/media/MediaScannerConnection
 */
object VideoPlayerFileMover {

    data class VideoItem(
        val id: Long,
        val uri: Uri,
        val title: String,
        val folderName: String,
        val relativePath: String,
        val filePath: String? = null
    )

    sealed class MoveVideoResult {
        data class Success(
            val movedUri: Uri,
            val newRelativePath: String,
            val newFilePath: String? = null
        ) : MoveVideoResult()
        data class NeedsPermission(
            val intentSender: IntentSender,
            val videoUri: Uri,
            val targetRelativePath: String
        ) : MoveVideoResult()
        data class Error(val message: String, val throwable: Throwable? = null) : MoveVideoResult()
    }

    /**
     * Converts a MediaStore video ID into a canonical `content://media/external/video/media/<id>` URI.
     */
    fun buildVideoContentUri(videoId: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId)

    /**
     * Resolves a raw File into its canonical MediaStore.Video.Media content:// Uri.
     */
    fun getVideoContentUriFromFile(context: Context, file: File): Uri? =
        getVideoContentUriFromPath(context, file.absolutePath)

    /**
     * Queries MediaStore for the updated DATA (absolute path) and RELATIVE_PATH of a moved video URI.
     */
    @Suppress("DEPRECATION")
    fun queryMovedVideoMetadata(context: Context, uri: Uri): Pair<String?, String?> {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return null to null)
            return file.absolutePath to (file.parentFile?.name?.let { "$it/" })
        }
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            arrayOf(MediaStore.MediaColumns.DATA)
        }
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val relIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else {
                        -1
                    }
                    val newPath = if (dataIdx != -1) cursor.getString(dataIdx) else null
                    val newRel = if (relIdx != -1) cursor.getString(relIdx) else null
                    newPath to newRel
                } else {
                    null to null
                }
            } ?: (null to null)
        } catch (_: Throwable) {
            null to null
        }
    }

    /**
     * Resolves a raw file path (/storage/emulated/0/Movies/A/video.mp4) into its
     * canonical MediaStore content:// Uri (Video, Audio, or Files) so MediaStore tracks the move.
     */
    fun getVideoContentUriFromPath(context: Context, filePath: String): Uri? {
        val cleanPath = filePath.trim()
        if (cleanPath.isEmpty()) return null
        val collections = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external")
        )
        for (collection in collections) {
            try {
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DATA} = ?",
                    arrayOf(cleanPath),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        return ContentUris.withAppendedId(collection, id)
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    /**
     * Checks if any of the target media files require user write authorization on Android 10–17
     * (MediaStore.createWriteRequest on API 30–37, or RecoverableSecurityException on API 29)
     * before moving them, returning a single batch IntentSender if needed.
     */
    fun prepareBatchMoveWriteRequest(
        context: Context,
        urisAndPaths: List<Pair<Uri?, String>>
    ): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val urisNeedingWrite = mutableListOf<Uri>()

        for ((rawUri, filePath) in urisAndPaths) {
            val srcFile = File(filePath)
            // If direct filesystem write/rename is already permitted on this file, no dialog is needed
            if (srcFile.exists() && srcFile.canWrite() && srcFile.parentFile?.canWrite() == true) {
                continue
            }
            val contentUri = when {
                rawUri != null && rawUri.scheme.equals("content", ignoreCase = true) -> rawUri
                filePath.isNotBlank() -> getVideoContentUriFromPath(context, filePath)
                else -> null
            } ?: continue

            try {
                // Probe write access with a no-op update or openFileDescriptor("rw")
                resolver.openFileDescriptor(contentUri, "rw")?.use { }
            } catch (securityEx: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    urisNeedingWrite.add(contentUri)
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    val rse = securityEx as? RecoverableSecurityException
                    if (rse != null) {
                        return rse.userAction.actionIntent.intentSender
                    }
                }
            } catch (_: Throwable) {
            }
        }

        if (urisNeedingWrite.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching {
                MediaStore.createWriteRequest(resolver, urisNeedingWrite.distinct()).intentSender
            }.getOrNull()
        }
        return null
    }

    /**
     * Moves a video file to a destination folder and ensures the original folder entry is removed.
     *
     * IMPORTANT FOR VIDEO PLAYERS:
     * Before calling this function, if the video being moved is currently loaded in ExoPlayer / MediaPlayer,
     * call `exoPlayer.stop()` or `exoPlayer.clearMediaItems()` first so no open file descriptor blocks deletion.
     *
     * @param context Application or Activity context
     * @param videoUri Content URI from MediaStore.Video.Media (or file:// URI fallback)
     * @param targetFolderRelativePath e.g. "Movies/Action" or "DCIM/Camera/Saved"
     * @param legacySourcePath Optional absolute path for API <= 28 fallback
     */
    suspend fun moveVideoFile(
        context: Context,
        videoUri: Uri,
        targetFolderRelativePath: String,
        legacySourcePath: String? = null
    ): MoveVideoResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val normalizedRelPath = normalizeVideoRelativePath(targetFolderRelativePath)

        val trimmedTarget = targetFolderRelativePath.trim()
        val isDirectAbsoluteFolder = trimmedTarget.startsWith("/") &&
            !trimmedTarget.startsWith("/storage/emulated/0/", ignoreCase = true) &&
            !trimmedTarget.startsWith("/DCIM", ignoreCase = true) &&
            !trimmedTarget.startsWith("/Movies", ignoreCase = true) &&
            !trimmedTarget.startsWith("/Pictures", ignoreCase = true) &&
            !trimmedTarget.startsWith("/Download", ignoreCase = true)
        val directOrNormalizedPath = if (isDirectAbsoluteFolder) trimmedTarget else normalizedRelPath

        try {
            // Resolve file:// URI to a MediaStore content:// URI when moving within shared storage on Android 10+
            val resolvedContentUri: Uri? = when {
                videoUri.scheme.equals("content", ignoreCase = true) -> videoUri
                !isDirectAbsoluteFolder && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val rawPath = videoUri.path ?: legacySourcePath
                    if (!rawPath.isNullOrBlank()) getVideoContentUriFromPath(context, rawPath) else null
                }
                else -> null
            }

            if (resolvedContentUri == null || isDirectAbsoluteFolder) {
                val rawPath = videoUri.path ?: legacySourcePath
                    ?: return@withContext MoveVideoResult.Error("Source video path not found")
                return@withContext moveDirectFileOrLegacyVideo(context, File(rawPath), directOrNormalizedPath)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Step 1: In-place MediaStore RELATIVE_PATH update (moves file on disk & updates BUCKET_ID atomically)
                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedRelPath)
                }

                val rowsUpdated = try {
                    resolver.update(resolvedContentUri, updateValues, null, null)
                } catch (securityEx: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val writeRequest = MediaStore.createWriteRequest(resolver, listOf(resolvedContentUri))
                        return@withContext MoveVideoResult.NeedsPermission(
                            intentSender = writeRequest.intentSender,
                            videoUri = resolvedContentUri,
                            targetRelativePath = normalizedRelPath
                        )
                    }
                    val rse = securityEx as? RecoverableSecurityException
                    if (rse != null) {
                        return@withContext MoveVideoResult.NeedsPermission(
                            intentSender = rse.userAction.actionIntent.intentSender,
                            videoUri = resolvedContentUri,
                            targetRelativePath = normalizedRelPath
                        )
                    }
                    throw securityEx
                }

                if (rowsUpdated > 0) {
                    val (queriedPath, queriedRel) = queryMovedVideoMetadata(context, resolvedContentUri)
                    // If the old physical file still exists at legacySourcePath and differs from queriedPath, remove it
                    if (!legacySourcePath.isNullOrBlank() &&
                        !queriedPath.isNullOrBlank() &&
                        !legacySourcePath.equals(queriedPath, ignoreCase = false)
                    ) {
                        val oldFile = File(legacySourcePath)
                        if (oldFile.exists()) {
                            runCatching { oldFile.delete() }
                        }
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(legacySourcePath, queriedPath),
                            null,
                            null
                        )
                    }
                    return@withContext MoveVideoResult.Success(
                        movedUri = resolvedContentUri,
                        newRelativePath = queriedRel ?: normalizedRelPath,
                        newFilePath = queriedPath
                    )
                }

                // Step 2: Fallback if in-place update returned 0 rows (e.g. cross-volume or OEM MediaProvider quirk):
                // Try direct verified move first if local path is accessible, otherwise verified MediaStore stream copy + delete.
                if (legacySourcePath != null && File(legacySourcePath).exists()) {
                    val directMove = moveDirectFileOrLegacyVideo(context, File(legacySourcePath), directOrNormalizedPath)
                    if (directMove is MoveVideoResult.Success) {
                        return@withContext directMove
                    }
                }
                return@withContext copyVideoThenDeleteSourceQ(context, resolvedContentUri, normalizedRelPath)
            } else {
                // Step 3: Android 9 (API 28) and below — move file on disk, delete old MediaStore row, scan both paths
                return@withContext moveLegacyVideoAndScan(context, resolvedContentUri, normalizedRelPath, legacySourcePath)
            }
        } catch (e: Throwable) {
            MoveVideoResult.Error(e.message ?: "Failed to move video", e)
        }
    }

    /**
     * Moves [source] to [newPath] and makes sure the original is gone (TRUE MOVE, NEVER COPY):
     * - Throws IllegalArgumentException("Source file not found") if !source.exists()
     * - Throws IllegalStateException("A file already exists at the destination") if File(newPath).exists()
     * - Creates parent directory recursively
     * - Tries source.renameTo(destFile) first (atomic move on same volume, removes the original)
     * - On cross-volume failure (e.g. internal storage -> SD card):
     *   1. Copies original file to destination
     *   2. Verifies destination file exists
     *   3. Verifies copied file's size and contents (SHA-256 header/tail integrity)
     *   4. Only after verification, deletes the original file
     *   5. Confirms original file no longer exists; if deleting the original fails, rolls back by deleting
     *      the copied file so there is zero duplicate copy, and rethrows.
     */
    fun moveFile(
        source: File,
        newPath: String,
        simulateRenameFailure: Boolean = false,
        simulateSourceDeleteFailure: Boolean = false
    ): File {
        if (!source.exists()) {
            throw IllegalArgumentException("Source file not found")
        }
        val destFile = File(newPath)
        if (destFile.exists()) {
            throw IllegalStateException("A file already exists at the destination")
        }
        destFile.parentFile?.mkdirs()

        try {
            if (simulateRenameFailure) {
                throw java.io.IOException("Simulated cross-volume rename failure")
            }
            val expectedLen = source.length()
            val renamed = source.renameTo(destFile)
            if (!renamed || !destFile.exists() || source.exists() || destFile.length() != expectedLen) {
                throw java.io.IOException("Rename failed across volumes")
            }
            return destFile
        } catch (_: Exception) {
            // 1. Copy original file to destination folder
            val copied = source.copyTo(destFile, overwrite = false)
            try {
                // 2 & 3. Verify destination file exists and matches source file's size and contents
                if (!copied.exists() ||
                    !com.cyosplay.player.util.CryptoSecurityManager.verifyCopiedFileIntegrity(source, copied)
                ) {
                    throw java.io.IOException("Copied file failed size or content integrity verification")
                }
                if (simulateSourceDeleteFailure) {
                    throw java.io.IOException("Simulated failure deleting source file")
                }
                // 4. Only after verification, delete the original file
                val deleted = source.delete()
                // 5. Confirm the original file no longer exists
                if (!deleted || source.exists()) {
                    throw java.io.IOException("Failed to delete original source file")
                }
            } catch (deleteError: Throwable) {
                try {
                    copied.delete() // roll back so there is no duplicate
                } catch (_: Throwable) {}
                throw deleteError // report the failure instead of hiding it
            }
            return copied
        }
    }

    /**
     * Direct file move helper when a local File reference is provided (or when target is an absolute path).
     */
    private fun moveDirectFileOrLegacyVideo(
        context: Context,
        sourceFile: File,
        normalizedRelPath: String
    ): MoveVideoResult {
        if (!sourceFile.exists()) {
            return MoveVideoResult.Error("Source file not found")
        }
        val sourceAbsPath = sourceFile.absolutePath
        val destDir = if (normalizedRelPath.startsWith("/")) {
            File(normalizedRelPath)
        } else {
            File(Environment.getExternalStorageDirectory(), normalizedRelPath)
        }
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, sourceFile.name)
        if (sourceAbsPath == destFile.absolutePath) {
            return MoveVideoResult.Success(Uri.fromFile(destFile), normalizedRelPath, destFile.absolutePath)
        }
        if (destFile.exists()) {
            return MoveVideoResult.Error("A file already exists at the destination")
        }

        val movedFile = try {
            moveFile(sourceFile, destFile.absolutePath)
        } catch (e: Throwable) {
            return MoveVideoResult.Error(e.message ?: "Move failed", e)
        }

        // Confirm original file no longer exists
        if (sourceFile.exists()) {
            runCatching { movedFile.delete() }
            return MoveVideoResult.Error("Original file could not be removed from source folder")
        }

        try {
            context.contentResolver.delete(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Video.Media.DATA} = ?",
                arrayOf(sourceAbsPath)
            )
            context.contentResolver.delete(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(sourceAbsPath)
            )
        } catch (_: Throwable) {
        }

        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(sourceAbsPath, movedFile.absolutePath),
                arrayOf("video/*", "video/*"),
                null
            )
        } catch (_: Throwable) {
        }

        val resolvedUri = getVideoContentUriFromPath(context, movedFile.absolutePath) ?: Uri.fromFile(movedFile)
        return MoveVideoResult.Success(
            movedUri = resolvedUri,
            newRelativePath = normalizedRelPath,
            newFilePath = movedFile.absolutePath
        )
    }

    /**
     * Android 10+ fallback: Copies the video into the destination RELATIVE_PATH, verifies size/integrity,
     * and deletes the original URI and file so it never remains in the old folder.
     * If deleting the source fails or requires permission, rolls back the destination copy so no duplicate exists.
     */
    @Suppress("DEPRECATION")
    private fun copyVideoThenDeleteSourceQ(
        context: Context,
        sourceUri: Uri,
        normalizedRelPath: String
    ): MoveVideoResult {
        val resolver = context.contentResolver
        var displayName = "video_${System.currentTimeMillis()}.mp4"
        var mimeType = "video/mp4"
        var rawDataPath: String? = null
        var expectedSize = -1L

        resolver.query(
            sourceUri,
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: displayName
                if (mimeIdx != -1) mimeType = cursor.getString(mimeIdx) ?: mimeType
                if (dataIdx != -1) rawDataPath = cursor.getString(dataIdx)
                if (sizeIdx != -1) expectedSize = cursor.getLong(sizeIdx)
            }
        }

        val insertValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedRelPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val newVideoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, insertValues)
            ?: return MoveVideoResult.Error("Could not create destination MediaStore entry")

        try {
            var copiedBytes = 0L
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(newVideoUri)?.use { output ->
                    copiedBytes = input.copyTo(output, bufferSize = 256 * 1024)
                    output.flush()
                } ?: throw IllegalStateException("Failed to open output stream for $newVideoUri")
            } ?: throw IllegalStateException("Failed to open input stream for $sourceUri")

            if (expectedSize > 0L && copiedBytes != expectedSize) {
                throw IllegalStateException("Copied byte count ($copiedBytes) did not match source size ($expectedSize)")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalizeValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(newVideoUri, finalizeValues, null, null)
            }

            // Delete original MediaStore entry AND physical file so it disappears from the old folder
            val deletedRows = try {
                resolver.delete(sourceUri, null, null)
            } catch (securityEx: SecurityException) {
                // Roll back the destination copy so no duplicate exists before requesting permission
                runCatching { resolver.delete(newVideoUri, null, null) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val writeRequest = MediaStore.createWriteRequest(resolver, listOf(sourceUri))
                    return MoveVideoResult.NeedsPermission(
                        intentSender = writeRequest.intentSender,
                        videoUri = sourceUri,
                        targetRelativePath = normalizedRelPath
                    )
                }
                val rse = securityEx as? RecoverableSecurityException
                if (rse != null) {
                    return MoveVideoResult.NeedsPermission(
                        intentSender = rse.userAction.actionIntent.intentSender,
                        videoUri = sourceUri,
                        targetRelativePath = normalizedRelPath
                    )
                }
                throw securityEx
            }

            if (deletedRows == 0 && !rawDataPath.isNullOrBlank()) {
                val oldFile = File(rawDataPath!!)
                if (oldFile.exists() && !oldFile.delete() && oldFile.exists()) {
                    // Roll back destination copy so no duplicate remains
                    runCatching { resolver.delete(newVideoUri, null, null) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val writeRequest = MediaStore.createWriteRequest(resolver, listOf(sourceUri))
                        return MoveVideoResult.NeedsPermission(
                            intentSender = writeRequest.intentSender,
                            videoUri = sourceUri,
                            targetRelativePath = normalizedRelPath
                        )
                    }
                    return MoveVideoResult.Error("Could not remove original file from source folder")
                }
                MediaScannerConnection.scanFile(context, arrayOf(rawDataPath!!), null, null)
            }

            val (queriedPath, queriedRel) = queryMovedVideoMetadata(context, newVideoUri)
            return MoveVideoResult.Success(
                movedUri = newVideoUri,
                newRelativePath = queriedRel ?: normalizedRelPath,
                newFilePath = queriedPath
            )
        } catch (e: Throwable) {
            // Rollback incomplete destination copy
            runCatching { resolver.delete(newVideoUri, null, null) }
            throw e
        }
    }

    /**
     * Android 9 and below: Moves the physical File, deletes the stale MediaStore row for the old path,
     * and scans both old and new paths so MediaStore updates BUCKET_DISPLAY_NAME / BUCKET_ID immediately.
     */
    @Suppress("DEPRECATION")
    private fun moveLegacyVideoAndScan(
        context: Context,
        sourceUri: Uri,
        normalizedRelPath: String,
        legacySourcePath: String?
    ): MoveVideoResult {
        val resolver = context.contentResolver
        val sourcePath = legacySourcePath ?: run {
            var path: String? = null
            resolver.query(
                sourceUri,
                arrayOf(MediaStore.Video.Media.DATA),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    path = c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
                }
            }
            path
        } ?: return MoveVideoResult.Error("Source video file path not found")

        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            return MoveVideoResult.Error("Source video file does not exist: $sourcePath")
        }

        val destDir = File(Environment.getExternalStorageDirectory(), normalizedRelPath)
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, sourceFile.name)

        // Use verified moveFile (tries atomic rename first, then verified copy + source delete with rollback)
        val movedFile = try {
            moveFile(sourceFile, destFile.absolutePath)
        } catch (e: Throwable) {
            return MoveVideoResult.Error(e.message ?: "Failed to move video from source folder", e)
        }

        if (sourceFile.exists() || !movedFile.exists()) {
            return MoveVideoResult.Error("Failed to delete original video from source folder")
        }

        // Update MediaStore DATA column in-place if possible, otherwise remove stale row and scan
        val updatedRows = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DATA, movedFile.absolutePath)
            }
            resolver.update(sourceUri, values, null, null)
        }.getOrDefault(0)

        if (updatedRows == 0) {
            runCatching {
                resolver.delete(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Video.Media.DATA} = ?",
                    arrayOf(sourceFile.absolutePath)
                )
            }
        }

        // Scan both old and new paths so MediaStore indexes the new folder immediately
        MediaScannerConnection.scanFile(
            context,
            arrayOf(sourceFile.absolutePath, movedFile.absolutePath),
            arrayOf("video/mp4", "video/mp4"),
            null
        )

        val newUri = getVideoContentUriFromPath(context, movedFile.absolutePath) ?: Uri.fromFile(movedFile)
        return MoveVideoResult.Success(
            movedUri = newUri,
            newRelativePath = normalizedRelPath,
            newFilePath = movedFile.absolutePath
        )
    }

    /**
     * Ensures RELATIVE_PATH is in valid Android MediaStore format (e.g. "Movies/MyFolder/").
     */
    fun normalizeVideoRelativePath(folderPath: String): String {
        val trimmed = folderPath.trim()
        if (trimmed.startsWith("/storage/emulated/0/")) {
            val rel = trimmed.removePrefix("/storage/emulated/0/").trim('/')
            return if (rel.isEmpty()) "${Environment.DIRECTORY_MOVIES}/" else "$rel/"
        }
        val clean = trimmed.trim('/')
        if (clean.isEmpty()) return "${Environment.DIRECTORY_MOVIES}/"
        val validRoots = listOf(
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_DOWNLOADS
        )
        val hasValidRoot = validRoots.any { clean.startsWith(it, ignoreCase = true) }
        return if (hasValidRoot) "$clean/" else "${Environment.DIRECTORY_MOVIES}/$clean/"
    }
}
