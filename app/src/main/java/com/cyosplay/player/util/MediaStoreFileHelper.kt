package com.cyosplay.player.util

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Production-ready Android MediaStore Helper (API 24 to API 34+) for:
 * 1. Deleting Media Files (Images & Videos) from MediaStore & device/SD storage with:
 *    - Direct ContentResolver deletion on API 24–28 (and app-owned files on API 29+)
 *    - RecoverableSecurityException IntentSender handling on Android 10 (API 29)
 *    - MediaStore.createDeleteRequest batch PendingIntent handling on Android 11+ (API 30–34+)
 *    - Raw file path (String) -> MediaStore content:// Uri resolution
 *    - Activity, Fragment, and Jetpack Compose ActivityResultLauncher<IntentSenderRequest> registration
 * 2. Copying and Moving Media Files (Images & Videos) with Android 10+ Scoped Storage
 *    (ContentResolver + IS_PENDING + RELATIVE_PATH) and MediaScannerConnection so changes
 *    immediately appear in the Android system Gallery app.
 */
/**
 * Builds a valid MediaStore video content:// Uri from a numeric MediaStore video _ID:
 * content://media/external/video/media/<videoId>
 */
fun buildVideoContentUri(videoId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId)

fun buildAudioContentUri(audioId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId)

/**
 * Deletes a video or audio file from MediaStore across all Android versions (Android 7 to Android 17 / API 24–37):
 * - Android 11–17 (API 30–37): MediaStore.createDeleteRequest(contentResolver, listOf(resolvedUri))
 * - Android 10 (API 29): contentResolver.delete() catching RecoverableSecurityException and re-executing delete on RESULT_OK
 * - Android 7–9 (API 24–28): Direct contentResolver.delete() + physical File.delete() + MediaScannerConnection refresh
 */
fun deleteVideoFromMediaStore(
    context: Context,
    videoUri: Uri,
    intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>,
    onImmediateDeleteSuccess: (() -> Unit)? = null,
    onDeleteFailedOrDenied: (() -> Unit)? = null
) {
    val contentResolver: ContentResolver = context.contentResolver
    val rawPathFromUri = if (videoUri.scheme.equals("file", ignoreCase = true)) {
        videoUri.path
    } else {
        MediaStoreFileHelper.queryFilePathFromContentUri(contentResolver, videoUri)
    }

    val resolvedUri = if (videoUri.scheme.equals("file", ignoreCase = true)) {
        if (!rawPathFromUri.isNullOrBlank()) {
            MediaStoreFileHelper.queryExistingMediaStoreUri(contentResolver, rawPathFromUri) ?: videoUri
        } else {
            videoUri
        }
    } else {
        videoUri
    }

    // Android 11 (API 30) through Android 17 (API 37): Use createDeleteRequest for a seamless system prompt
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (resolvedUri.scheme.equals("file", ignoreCase = true)) {
            val rawPath = resolvedUri.path
            if (!rawPath.isNullOrBlank()) {
                val file = File(rawPath)
                if (!file.exists() || file.delete()) {
                    MediaScannerConnection.scanFile(context, arrayOf(rawPath), null, null)
                    onImmediateDeleteSuccess?.invoke()
                } else {
                    onDeleteFailedOrDenied?.invoke()
                }
            } else {
                onDeleteFailedOrDenied?.invoke()
            }
            return
        }
        // First attempt direct delete if app owns the MediaStore entry or has write access
        val deletedDirectly = try {
            val rows = contentResolver.delete(resolvedUri, null, null)
            if (rows > 0) {
                if (!rawPathFromUri.isNullOrBlank()) {
                    val f = File(rawPathFromUri)
                    if (f.exists()) f.delete()
                    MediaScannerConnection.scanFile(context, arrayOf(rawPathFromUri), null, null)
                }
                true
            } else {
                false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }

        if (deletedDirectly) {
            onImmediateDeleteSuccess?.invoke()
            return
        }

        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(resolvedUri))
        val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        intentSenderLauncher.launch(request)
    } else {
        // Android 10 (API 29) and Android 7–9 (API 24–28)
        try {
            var deleted = false
            if (!resolvedUri.scheme.equals("file", ignoreCase = true)) {
                val rowsDeleted = contentResolver.delete(resolvedUri, null, null)
                if (rowsDeleted > 0) {
                    deleted = true
                }
            }
            if (!rawPathFromUri.isNullOrBlank()) {
                val physicalFile = File(rawPathFromUri)
                if (physicalFile.exists() && physicalFile.delete()) {
                    deleted = true
                }
                if (!physicalFile.exists()) {
                    MediaScannerConnection.scanFile(context, arrayOf(rawPathFromUri), null, null)
                }
            }
            if (deleted) {
                onImmediateDeleteSuccess?.invoke()
            } else {
                onDeleteFailedOrDenied?.invoke()
            }
        } catch (securityException: SecurityException) {
            // On Android 10, if our app doesn't own the media, catch RecoverableSecurityException
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                val recoverableException = securityException as? RecoverableSecurityException
                if (recoverableException != null) {
                    MediaStoreFileHelper.recordPendingRecoverableDelete(resolvedUri, rawPathFromUri)
                    val intentSender = recoverableException.userAction.actionIntent.intentSender
                    val request = IntentSenderRequest.Builder(intentSender).build()
                    intentSenderLauncher.launch(request)
                } else {
                    onDeleteFailedOrDenied?.invoke()
                }
            } else {
                onDeleteFailedOrDenied?.invoke()
            }
        }
    }
}

object MediaStoreFileHelper {

    private val pendingRecoverableDeleteUris = java.util.concurrent.CopyOnWriteArrayList<Uri>()
    private val pendingRecoverableDeletePaths = java.util.concurrent.CopyOnWriteArrayList<String>()

    fun recordPendingRecoverableDelete(uri: Uri?, filePath: String?) {
        if (uri != null && !uri.scheme.equals("file", ignoreCase = true)) {
            if (!pendingRecoverableDeleteUris.contains(uri)) {
                pendingRecoverableDeleteUris.add(uri)
            }
        }
        if (!filePath.isNullOrBlank()) {
            if (!pendingRecoverableDeletePaths.contains(filePath)) {
                pendingRecoverableDeletePaths.add(filePath)
            }
        }
    }

    fun clearPendingRecoverableDeletes() {
        pendingRecoverableDeleteUris.clear()
        pendingRecoverableDeletePaths.clear()
    }

    /**
     * Completes any pending Android 10 (API 29) RecoverableSecurityException deletions after the user
     * grants consent in the system confirmation dialog (RESULT_OK), and refreshes MediaScannerConnection.
     */
    fun completePendingRecoverableDeletes(context: Context): Int {
        var completed = 0
        val resolver = context.contentResolver
        val uris = pendingRecoverableDeleteUris.toList()
        val paths = pendingRecoverableDeletePaths.toList()
        pendingRecoverableDeleteUris.clear()
        pendingRecoverableDeletePaths.clear()

        for (uri in uris) {
            runCatching {
                val rows = resolver.delete(uri, null, null)
                if (rows > 0) completed++
            }
        }
        for (path in paths) {
            runCatching {
                val f = File(path)
                if (f.exists() && f.delete()) {
                    completed++
                }
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
        }
        return completed
    }

    /**
     * Verifies whether a media item has genuinely been deleted from both device storage and MediaStore.
     * Prevents false-positive "Deleted" messages when a file still exists on disk.
     */
    @Suppress("DEPRECATION")
    fun isMediaActuallyDeleted(
        context: Context,
        uri: Uri?,
        filePath: String?
    ): Boolean {
        val cleanPath = filePath?.trim()?.takeIf { it.isNotEmpty() }
            ?: (if (uri?.scheme.equals("file", ignoreCase = true)) uri?.path else null)

        if (!cleanPath.isNullOrBlank()) {
            val physicalFile = File(cleanPath)
            if (physicalFile.exists()) {
                // Attempt one final cleanup if permission was just granted by the system dialog
                val deletedNow = runCatching { physicalFile.delete() }.getOrDefault(false)
                if (!deletedNow && physicalFile.exists()) {
                    return false
                }
                MediaScannerConnection.scanFile(context, arrayOf(cleanPath), null, null)
            }
        }

        if (uri != null && uri.scheme.equals("content", ignoreCase = true)) {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            }
            val stillInMediaStore = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val queryArgs = Bundle().apply {
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                    }
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns._ID),
                        queryArgs,
                        null
                    )?.use { cursor ->
                        cursor.moveToFirst()
                    } ?: false
                } else {
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns._ID),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        cursor.moveToFirst()
                    } ?: false
                }
            } catch (_: Throwable) {
                false
            }
            if (stillInMediaStore) {
                // If user just granted permission on API 29/30+, try deleting the row (including IS_TRASHED=1) one final time
                val rows = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val delArgs = Bundle().apply {
                            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                        }
                        context.contentResolver.delete(uri, delArgs)
                    } else {
                        context.contentResolver.delete(uri, null, null)
                    }
                }.getOrDefault(0)
                if (rows <= 0) {
                    val stillExistsAfterRetry = try {
                        context.contentResolver.query(
                            uri,
                            arrayOf(MediaStore.MediaColumns._ID),
                            null,
                            null,
                            null
                        )?.use { it.moveToFirst() } ?: false
                    } catch (_: Throwable) {
                        false
                    }
                    if (stillExistsAfterRetry) return false
                }
            }
        }

        if (!cleanPath.isNullOrBlank()) {
            val residualUri = queryExistingMediaStoreUri(context.contentResolver, cleanPath)
            if (residualUri != null) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val delArgs = Bundle().apply {
                            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                        }
                        context.contentResolver.delete(residualUri, delArgs)
                    } else {
                        context.contentResolver.delete(residualUri, null, null)
                    }
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(cleanPath), null, null)
        }
        return true
    }

    fun buildVideoContentUri(videoId: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId)

    fun buildAudioContentUri(audioId: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId)

    fun deleteVideoFromMediaStore(
        context: Context,
        videoUri: Uri,
        intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onImmediateDeleteSuccess: (() -> Unit)? = null
    ) = com.cyosplay.player.util.deleteVideoFromMediaStore(
        context = context,
        videoUri = videoUri,
        intentSenderLauncher = intentSenderLauncher,
        onImmediateDeleteSuccess = onImmediateDeleteSuccess
    )

    sealed class DeleteResult {
        data class Deleted(val deletedCount: Int) : DeleteResult()
        data class RequiresUserConfirmation(val intentSenderRequest: IntentSenderRequest) : DeleteResult()
        data class Failed(val reason: String) : DeleteResult()
    }

    data class TransferResult(
        val success: Boolean,
        val destinationUri: Uri?,
        val destinationFilePath: String?
    )

    /**
     * Queries the absolute file path (MediaColumns.DATA) from a MediaStore content:// Uri if available.
     */
    @Suppress("DEPRECATION")
    fun queryFilePathFromContentUri(
        contentResolver: ContentResolver,
        contentUri: Uri
    ): String? {
        if (contentUri.scheme.equals("file", ignoreCase = true)) {
            return contentUri.path
        }
        if (!contentUri.scheme.equals("content", ignoreCase = true)) {
            return null
        }
        return try {
            contentResolver.query(
                contentUri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (idx != -1) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Converts a raw File path (String) into a MediaStore `content://` Uri for Videos, Audio, or Images.
     * Queries MediaStore.Video, MediaStore.Audio, MediaStore.Images, and MediaStore.Files; if not yet indexed
     * and the file exists on disk, scans the file via MediaScannerConnection so a valid content:// Uri is returned.
     */
    @Suppress("DEPRECATION")
    suspend fun getMediaStoreUriFromFilePath(
        context: Context,
        filePath: String
    ): Uri? = withContext(Dispatchers.IO) {
        val cleanPath = filePath.trim()
        if (cleanPath.isEmpty()) return@withContext null

        queryExistingMediaStoreUri(context.contentResolver, cleanPath)?.let { existingUri ->
            return@withContext existingUri
        }

        val file = File(cleanPath)
        if (!file.exists()) return@withContext null

        val scannedUri = scanFileBlocking(context, cleanPath, resolveMimeTypeForFile(file))
        scannedUri ?: queryExistingMediaStoreUri(context.contentResolver, cleanPath)
    }

    /**
     * Synchronous lookup of a raw File path (String) to a MediaStore `content://` Uri across
     * Video, Audio, Images, and Files tables.
     */
    @Suppress("DEPRECATION")
    fun queryExistingMediaStoreUri(
        contentResolver: ContentResolver,
        filePath: String
    ): Uri? {
        val cleanPath = filePath.trim()
        if (cleanPath.isEmpty()) return null

        val collections: List<Uri> = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external")
        )

        for (collection in collections) {
            runCatching {
                contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DATA} = ?",
                    arrayOf(cleanPath),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val id = cursor.getLong(idIndex)
                        return ContentUris.withAppendedId(collection, id)
                    }
                }
            }
            // Also check IS_TRASHED = 1 on Android 11+ (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    val queryArgs = Bundle().apply {
                        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.DATA} = ?")
                        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(cleanPath))
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                    }
                    contentResolver.query(
                        collection,
                        arrayOf(MediaStore.MediaColumns._ID),
                        queryArgs,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                            val id = cursor.getLong(idIndex)
                            return ContentUris.withAppendedId(collection, id)
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Deletes a single Uri from MediaStore across API 24 to API 37.
     */
    fun deleteMediaFile(
        context: Context,
        uri: Uri,
        intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>? = null,
        onDeleteSuccess: (() -> Unit)? = null,
        onDeleteCancelled: (() -> Unit)? = null
    ): DeleteResult {
        return deleteMediaFiles(
            context = context,
            uris = listOf(uri),
            intentSenderLauncher = intentSenderLauncher,
            onDeleteSuccess = onDeleteSuccess,
            onDeleteCancelled = onDeleteCancelled
        )
    }

    /**
     * Deletes a List<Uri> (Videos, Audio, Images) from MediaStore & device storage across Android 7 to Android 17 (API 24–37):
     * - Resolves any file:// URI to its canonical MediaStore content:// URI before requesting deletion
     * - Attempts direct deletion via ContentResolver.delete(...) and File.delete()
     * - Handles RecoverableSecurityException on Android 10 (API 29) and records pending URIs for completion
     * - Uses MediaStore.createDeleteRequest(contentResolver, uris) on Android 11–17 (API 30–37)
     * - Never invokes onDeleteSuccess unless actual deletion from storage succeeded
     */
    fun deleteMediaFiles(
        context: Context,
        uris: List<Uri>,
        intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>? = null,
        onDeleteSuccess: (() -> Unit)? = null,
        onDeleteCancelled: (() -> Unit)? = null
    ): DeleteResult {
        val validUris = uris.distinct()
        if (validUris.isEmpty()) {
            return DeleteResult.Failed("No media files specified for deletion")
        }

        val contentResolver = context.contentResolver
        var deletedCount = 0
        val pendingUrisRequiringConsent = mutableListOf<Uri>()
        clearPendingRecoverableDeletes()

        for (uri in validUris) {
            val rawPath: String? = if (uri.scheme.equals("file", ignoreCase = true)) {
                uri.path
            } else {
                queryFilePathFromContentUri(contentResolver, uri)
            }

            val resolvedMediaUri: Uri? = if (uri.scheme.equals("content", ignoreCase = true)) {
                uri
            } else if (!rawPath.isNullOrBlank()) {
                queryExistingMediaStoreUri(contentResolver, rawPath)
            } else {
                null
            }

            try {
                var removed = false
                if (resolvedMediaUri != null) {
                    if (DocumentsContract.isDocumentUri(context, resolvedMediaUri)) {
                        if (runCatching { DocumentsContract.deleteDocument(contentResolver, resolvedMediaUri) }.getOrDefault(false)) {
                            removed = true
                        }
                    }
                    if (!removed) {
                        val rows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val delArgs = Bundle().apply {
                                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                            }
                            runCatching { contentResolver.delete(resolvedMediaUri, delArgs) }
                                .getOrElse { contentResolver.delete(resolvedMediaUri, null, null) }
                        } else {
                            contentResolver.delete(resolvedMediaUri, null, null)
                        }
                        if (rows > 0) {
                            removed = true
                        }
                    }
                }

                if (!rawPath.isNullOrBlank()) {
                    val file = File(rawPath)
                    if (file.exists()) {
                        if (file.delete() || !file.exists()) {
                            removed = true
                        }
                    } else if (removed) {
                        // Already removed by ContentResolver.delete
                        removed = true
                    }
                    if (!file.exists()) {
                        MediaScannerConnection.scanFile(context, arrayOf(rawPath), null, null)
                    } else {
                        // Physical file still exists on disk!
                        removed = false
                    }
                }

                if (removed) {
                    deletedCount++
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && resolvedMediaUri != null) {
                    pendingUrisRequiringConsent.add(resolvedMediaUri)
                }
            } catch (securityException: SecurityException) {
                val consentUri = resolvedMediaUri ?: (if (uri.scheme.equals("content", ignoreCase = true)) uri else null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && intentSenderLauncher != null && consentUri != null) {
                    pendingUrisRequiringConsent.add(consentUri)
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                    securityException is RecoverableSecurityException &&
                    intentSenderLauncher != null
                ) {
                    recordPendingRecoverableDelete(consentUri ?: uri, rawPath)
                    val intentSender = securityException.userAction.actionIntent.intentSender
                    val request = IntentSenderRequest.Builder(intentSender).build()
                    intentSenderLauncher.launch(request)
                    return DeleteResult.RequiresUserConfirmation(request)
                } else {
                    if (!rawPath.isNullOrBlank()) {
                        val fallbackFile = File(rawPath)
                        if (fallbackFile.exists() && fallbackFile.delete() && !fallbackFile.exists()) {
                            MediaScannerConnection.scanFile(context, arrayOf(rawPath), null, null)
                            deletedCount++
                        }
                    }
                }
            } catch (_: Throwable) {
                if (!rawPath.isNullOrBlank()) {
                    val fallbackFile = File(rawPath)
                    if (fallbackFile.exists() && fallbackFile.delete() && !fallbackFile.exists()) {
                        MediaScannerConnection.scanFile(context, arrayOf(rawPath), null, null)
                        deletedCount++
                    }
                }
            }
        }

        if (pendingUrisRequiringConsent.isNotEmpty() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            intentSenderLauncher != null
        ) {
            return try {
                val pendingIntent = MediaStore.createDeleteRequest(
                    contentResolver,
                    pendingUrisRequiringConsent.distinct()
                )
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                intentSenderLauncher.launch(request)
                DeleteResult.RequiresUserConfirmation(request)
            } catch (t: Throwable) {
                onDeleteCancelled?.invoke()
                DeleteResult.Failed(t.message ?: "Failed to create delete request")
            }
        }

        return if (deletedCount > 0) {
            onDeleteSuccess?.invoke()
            DeleteResult.Deleted(deletedCount)
        } else {
            onDeleteCancelled?.invoke()
            DeleteResult.Failed("Permission denied or media file could not be deleted from storage")
        }
    }

    /**
     * Helper to initialize ActivityResultLauncher<IntentSenderRequest> inside a ComponentActivity
     * (e.g. MainActivity / AppCompatActivity) with callbacks for onDeleteSuccess and onDeleteCancelled.
     */
    @android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
    fun registerMediaStoreDeleteLauncher(
        activity: ComponentActivity,
        onDeleteSuccess: () -> Unit,
        onDeleteCancelled: () -> Unit
    ): ActivityResultLauncher<IntentSenderRequest> {
        return activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                completePendingRecoverableDeletes(activity)
                onDeleteSuccess()
            } else {
                clearPendingRecoverableDeletes()
                onDeleteCancelled()
            }
        }
    }

    /**
     * Helper to initialize ActivityResultLauncher<IntentSenderRequest> inside a Fragment
     * or any ActivityResultCaller with callbacks for onDeleteSuccess and onDeleteCancelled.
     */
    @android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
    fun registerMediaStoreDeleteLauncher(
        caller: ActivityResultCaller,
        onDeleteSuccess: () -> Unit,
        onDeleteCancelled: () -> Unit
    ): ActivityResultLauncher<IntentSenderRequest> {
        return caller.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                onDeleteSuccess()
            } else {
                onDeleteCancelled()
            }
        }
    }

    /**
     * Jetpack Compose helper to initialize ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>
     * with callbacks for onDeleteSuccess and onDeleteCancelled.
     */
    @Composable
    fun rememberMediaStoreDeleteLauncher(
        onDeleteSuccess: () -> Unit,
        onDeleteCancelled: () -> Unit
    ): ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult> {
        val context = androidx.compose.ui.platform.LocalContext.current
        return rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                completePendingRecoverableDeletes(context)
                onDeleteSuccess()
            } else {
                clearPendingRecoverableDeletes()
                onDeleteCancelled()
            }
        }
    }

    /**
     * Copies a media file (Image or Video) so the new copy immediately updates in the device's
     * MediaStore and appears in the system Gallery app across Android 7 through Android 14+ (Scoped Storage).
     */
    @Suppress("DEPRECATION")
    suspend fun copyMediaFileToMediaStore(
        context: Context,
        sourceFile: File,
        destinationFolder: File,
        desiredDisplayName: String = sourceFile.name,
        mimeType: String? = null
    ): TransferResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || !sourceFile.isFile) {
            return@withContext TransferResult(false, null, null)
        }

        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs()
        }

        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: resolveMimeTypeForFile(sourceFile)
        val isImage = resolvedMime.startsWith("image/", ignoreCase = true)
        val targetFile = resolveUniqueTargetFile(destinationFolder, desiredDisplayName)
        val contentResolver = context.contentResolver

        // Copy physical file bytes using zero-copy NIO FileChannel.transferTo (with 256 KB fallback) when destination path is writable
        var fileCopiedDirectly = false
        runCatching {
            fileCopiedDirectly = CryptoSecurityManager.fastCopyFileZeroCopy(sourceFile, targetFile)
        }

        val collectionUri = if (isImage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        val nowSeconds = System.currentTimeMillis() / 1000L

        if (fileCopiedDirectly) {
            val scannedUri = scanFileBlocking(context, targetFile.absolutePath, resolvedMime)
            if (scannedUri != null) {
                return@withContext TransferResult(
                    success = true,
                    destinationUri = scannedUri,
                    destinationFilePath = targetFile.absolutePath
                )
            }

            // Fallback explicit ContentResolver insert if MediaScanner did not return a Uri
            val existingUri = queryExistingMediaStoreUri(contentResolver, targetFile.absolutePath)
            if (existingUri != null) {
                return@withContext TransferResult(
                    success = true,
                    destinationUri = existingUri,
                    destinationFilePath = targetFile.absolutePath
                )
            }
        }

        // Scoped Storage ContentResolver stream insertion (Android 10+ IS_PENDING + RELATIVE_PATH)
        val relativePath = computeScopedRelativePath(destinationFolder, isImage)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, targetFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime)
            put(MediaStore.MediaColumns.DATE_ADDED, nowSeconds)
            put(MediaStore.MediaColumns.DATE_MODIFIED, nowSeconds)
            put(MediaStore.MediaColumns.SIZE, sourceFile.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
            }
        }

        val insertedUri = runCatching { contentResolver.insert(collectionUri, values) }.getOrNull()
        if (insertedUri != null) {
            val streamSuccess = runCatching {
                if (!fileCopiedDirectly) {
                    contentResolver.openOutputStream(insertedUri)?.use { output ->
                        FileInputStream(sourceFile).use { input ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    } ?: false
                } else {
                    true
                }
            }.isSuccess

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                runCatching { contentResolver.update(insertedUri, completeValues, null, null) }
            }

            if (streamSuccess) {
                scanFileBlocking(context, targetFile.absolutePath, resolvedMime)
                return@withContext TransferResult(
                    success = true,
                    destinationUri = insertedUri,
                    destinationFilePath = targetFile.absolutePath
                )
            } else {
                runCatching { contentResolver.delete(insertedUri, null, null) }
            }
        }

        if (fileCopiedDirectly) {
            return@withContext TransferResult(
                success = true,
                destinationUri = Uri.fromFile(targetFile),
                destinationFilePath = targetFile.absolutePath
            )
        }

        TransferResult(false, null, null)
    }

    /**
     * Moves a media file (Image or Video) so the move immediately updates in the device's
     * MediaStore and system Gallery app (attempts instant atomic rename on the same volume
     * before falling back to zero-copy NIO transfer, then updates MediaStore and disk).
     */
    @Suppress("DEPRECATION")
    suspend fun moveMediaFileInMediaStore(
        context: Context,
        sourceFile: File,
        destinationFolder: File,
        desiredDisplayName: String = sourceFile.name,
        mimeType: String? = null
    ): TransferResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || !sourceFile.isFile) {
            return@withContext TransferResult(false, null, null)
        }

        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs()
        }

        val sourcePath = sourceFile.absolutePath
        val sourceMediaUri = queryExistingMediaStoreUri(context.contentResolver, sourcePath)
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: resolveMimeTypeForFile(sourceFile)

        // Fast-path: attempt instant atomic file rename when source and destination are on the same filesystem
        val atomicTargetFile = resolveUniqueTargetFile(destinationFolder, desiredDisplayName)
        val renamedAtomically = runCatching {
            sourceFile.renameTo(atomicTargetFile) && atomicTargetFile.exists()
        }.getOrDefault(false)

        if (renamedAtomically) {
            if (sourceMediaUri != null) {
                runCatching { context.contentResolver.delete(sourceMediaUri, null, null) }
            }
            val scannedUri = scanFileBlocking(context, atomicTargetFile.absolutePath, resolvedMime)
                ?: queryExistingMediaStoreUri(context.contentResolver, atomicTargetFile.absolutePath)
                ?: Uri.fromFile(atomicTargetFile)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(sourcePath, atomicTargetFile.absolutePath),
                arrayOf(resolvedMime, resolvedMime),
                null
            )
            return@withContext TransferResult(
                success = true,
                destinationUri = scannedUri,
                destinationFilePath = atomicTargetFile.absolutePath
            )
        }

        val copyResult = copyMediaFileToMediaStore(
            context = context,
            sourceFile = sourceFile,
            destinationFolder = destinationFolder,
            desiredDisplayName = desiredDisplayName,
            mimeType = mimeType
        )

        if (!copyResult.success) {
            return@withContext TransferResult(false, null, null)
        }

        val destPath = copyResult.destinationFilePath
        if (!destPath.isNullOrBlank()) {
            val destFile = File(destPath)
            if (!CryptoSecurityManager.verifyCopiedFileIntegrity(sourceFile, destFile)) {
                runCatching { destFile.delete() }
                copyResult.destinationUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                return@withContext TransferResult(false, null, null)
            }
        }

        // Remove original MediaStore record and physical file so Gallery shows only the moved file
        if (sourceMediaUri != null && sourceMediaUri != copyResult.destinationUri) {
            runCatching { context.contentResolver.delete(sourceMediaUri, null, null) }
        }
        runCatching {
            if (sourceFile.exists()) {
                sourceFile.delete()
            }
        }
        // Confirm the original file no longer exists; if it still exists, roll back the copy so no duplicate remains
        if (sourceFile.exists()) {
            destPath?.let { runCatching { File(it).delete() } }
            copyResult.destinationUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            return@withContext TransferResult(false, null, null)
        }

        // Notify MediaScanner that the old path was removed and the new path exists
        val pathsToScan = listOfNotNull(sourcePath, copyResult.destinationFilePath).toTypedArray()
        MediaScannerConnection.scanFile(
            context,
            pathsToScan,
            Array(pathsToScan.size) { resolvedMime },
            null
        )

        copyResult
    }

    private suspend fun scanFileBlocking(
        context: Context,
        filePath: String,
        mimeType: String?
    ): Uri? = kotlinx.coroutines.withTimeoutOrNull(1_500L) {
        suspendCancellableCoroutine { continuation ->
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    if (mimeType != null) arrayOf(mimeType) else null
                ) { _, uri ->
                    if (continuation.isActive) {
                        continuation.resume(uri)
                    }
                }
            } catch (_: Throwable) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    private fun resolveUniqueTargetFile(destinationFolder: File, desiredName: String): File {
        var candidate = File(destinationFolder, desiredName)
        if (!candidate.exists()) return candidate
        val dotIndex = desiredName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
        val ext = if (dotIndex > 0) desiredName.substring(dotIndex) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(destinationFolder, "${baseName}_$counter$ext")
            counter++
        }
        return candidate
    }

    private fun computeScopedRelativePath(destinationFolder: File, isImage: Boolean): String {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val fullDest = destinationFolder.absolutePath.trimEnd('/')
        if (fullDest.startsWith("$rootPath/")) {
            val rel = fullDest.removePrefix("$rootPath/").trim('/')
            if (rel.isNotEmpty()) {
                val topDir = rel.substringBefore('/')
                val allowedPublicRoots = setOf(
                    Environment.DIRECTORY_MOVIES,
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_DOWNLOADS
                )
                if (allowedPublicRoots.any { it.equals(topDir, ignoreCase = true) }) {
                    return "$rel/"
                }
                val defaultRoot = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                return "$defaultRoot/${destinationFolder.name}/"
            }
        }
        val defaultRoot = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        return "$defaultRoot/${destinationFolder.name}/"
    }

    fun resolveMimeTypeForFile(file: File): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return when (ext) {
            "mkv", "mk3d", "mks", "x26" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp4", "m4v" -> "video/mp4"
            "avi", "divx", "xvid" -> "video/x-msvideo"
            "mov", "qt" -> "video/quicktime"
            "ts", "m2ts", "mts" -> "video/mp2t"
            "flv", "f4v" -> "video/x-flv"
            "3gp", "3gpp" -> "video/3gpp"
            "3g2", "3gp2" -> "video/3gpp2"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/x-wav"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "oga", "opus" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heif"
            else -> "video/mp4"
        }
    }
}

typealias CyosPlayerMediaStoreDeleter = com.cyos.player.CyosPlayerMediaStoreDeleter
typealias CyosVideoDeleteController = com.cyos.player.CyosVideoDeleteController

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun buildCyosExoPlayer(context: Context): androidx.media3.exoplayer.ExoPlayer {
    return com.cyosplay.player.buildCyosExoPlayer(context)
}


