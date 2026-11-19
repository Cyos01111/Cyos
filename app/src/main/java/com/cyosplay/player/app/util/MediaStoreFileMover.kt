package com.cyosplay.player.app.util

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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utility to move media files between folders while ensuring the file
 * is removed from the original folder in both MediaStore and the app UI.
 *
 * Reference documentation:
 * - Scoped Storage & MediaStore: https://developer.android.com/training/data-storage/shared/media
 * - MediaStore.MediaColumns: https://developer.android.com/reference/android/provider/MediaStore.MediaColumns
 * - ContentResolver: https://developer.android.com/reference/android/content/ContentResolver
 * - MediaScannerConnection: https://developer.android.com/reference/android/media/MediaScannerConnection
 */
object MediaStoreFileMover {

    sealed class MoveResult {
        data object Success : MoveResult()
        data class NeedsPermission(val intentSender: IntentSender) : MoveResult()
        data class Error(val throwable: Throwable) : MoveResult()
    }

    /**
     * Moves a media file to a new folder and removes it from the original folder.
     *
     * @param context Application or Activity context
     * @param sourceUri The MediaStore content:// Uri of the file to move
     * @param relativeDestFolder Destination relative path (e.g., "Pictures/MyAlbum" or "DCIM/Moved")
     */
    suspend fun moveMediaFile(
        context: Context,
        sourceUri: Uri,
        relativeDestFolder: String
    ): MoveResult = withContext(Dispatchers.IO) {
        val cleanDest = relativeDestFolder.trim('/') + "/"

        // Android 10+ (API 29+): Move in-place by updating RELATIVE_PATH.
        // This moves the physical file AND updates the existing MediaStore row,
        // so no duplicate remains in the original folder.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sourceUri.scheme == "content") {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, cleanDest)
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                val updatedRows = context.contentResolver.update(sourceUri, values, null, null)
                if (updatedRows > 0) {
                    return@withContext MoveResult.Success
                }
            } catch (securityEx: SecurityException) {
                // If the file was created by another app, request write permission from the user
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val writeRequest = MediaStore.createWriteRequest(
                        context.contentResolver,
                        listOf(sourceUri)
                    )
                    return@withContext MoveResult.NeedsPermission(writeRequest.intentSender)
                } else if (securityEx is RecoverableSecurityException) {
                    return@withContext MoveResult.NeedsPermission(
                        securityEx.userAction.actionIntent.intentSender
                    )
                } else {
                    return@withContext MoveResult.Error(securityEx)
                }
            } catch (e: Exception) {
                return@withContext MoveResult.Error(e)
            }
        }

        // Android 9 and below (API <= 28) or file:// fallback: Move on disk and scan BOTH old and new paths
        // so MediaStore deletes the old folder entry and indexes the new one.
        return@withContext moveLegacyFile(context, sourceUri, cleanDest)
    }

    @Suppress("DEPRECATION")
    private fun moveLegacyFile(
        context: Context,
        sourceUri: Uri,
        cleanDest: String
    ): MoveResult {
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME)
            var oldPath: String? = null
            var displayName: String? = null

            if (sourceUri.scheme == "file") {
                oldPath = sourceUri.path
                displayName = oldPath?.let { File(it).name }
            } else {
                context.contentResolver.query(sourceUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        oldPath = cursor.getString(0)
                        displayName = cursor.getString(1)
                    }
                }
                if (oldPath == null && sourceUri.path != null) {
                    val candidate = File(sourceUri.path!!)
                    if (candidate.exists()) {
                        oldPath = candidate.absolutePath
                        displayName = candidate.name
                    }
                }
            }

            val sourceFile = oldPath?.let { File(it) }
                ?: return MoveResult.Error(IllegalStateException("Could not resolve source file path"))
            val destDir = if (File("/" + cleanDest.trimEnd('/')).isAbsolute && File("/" + cleanDest.trimEnd('/')).parentFile?.exists() == true) {
                File("/" + cleanDest.trimEnd('/')).apply { mkdirs() }
            } else {
                File(Environment.getExternalStorageDirectory(), cleanDest).apply { mkdirs() }
            }
            val destFile = File(destDir, displayName ?: sourceFile.name)

            val movedFile = com.cyosplay.videoplayer.util.VideoPlayerFileMover.moveFile(
                source = sourceFile,
                newPath = destFile.absolutePath
            )

            // Delete the stale MediaStore row for the old path and scan both paths
            try {
                if (sourceUri.scheme == "content") {
                    context.contentResolver.delete(sourceUri, null, null)
                }
            } catch (_: Exception) {}
            MediaScannerConnection.scanFile(
                context,
                arrayOf(sourceFile.absolutePath, movedFile.absolutePath),
                null,
                null
            )

            return MoveResult.Success
        } catch (e: Throwable) {
            return MoveResult.Error(e)
        }
    }

    /**
     * Resolves a relative MediaStore destination folder (e.g. "Movies/Action" or "DCIM/Moved")
     * from an absolute folder path when inside external storage.
     */
    @Suppress("DEPRECATION")
    fun toRelativeDestinationFolder(absoluteDestPath: String): String {
        val extRoot = try {
            Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        } catch (_: Exception) {
            "/storage/emulated/0"
        }
        val normalized = absoluteDestPath.trim()
        return when {
            normalized.startsWith("$extRoot/") -> normalized.removePrefix("$extRoot/").trim('/')
            normalized.startsWith("/storage/emulated/0/") -> normalized.removePrefix("/storage/emulated/0/").trim('/')
            else -> normalized.trim('/')
        }
    }
}
