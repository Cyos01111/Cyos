package com.cyos.player

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MediaStore-synchronized video deletion helper for Cyos Player (MX Player style).
 *
 * Ensures that deleting a video in Cyos Player:
 * 1. Releases any active ExoPlayer playback handle on the target video before deletion.
 * 2. Resolves raw file paths (/storage/emulated/0/...) into MediaStore.Video.Media content:// URIs.
 * 3. Deletes both the physical video file and its MediaStore.Video.Media row across:
 *    - Android 11+ (API 30+): MediaStore.createDeleteRequest() (or silent delete when MANAGE_MEDIA is granted on API 31+)
 *    - Android 10 (API 29): ContentResolver.delete() with RecoverableSecurityException fallback
 *    - Android 9 & below (API <= 28): ContentResolver.delete() + File.delete() + MediaScannerConnection.scanFile()
 * 4. Deletes matching external subtitle files (.srt, .ass, .ssa, .vtt, .sub) in the same folder if requested.
 */
object CyosPlayerMediaStoreDeleter {

    @Volatile
    var activeExoPlayerInstance: ExoPlayer? = null

    sealed class DeleteResult {
        data class Success(val deletedUris: List<Uri>) : DeleteResult()
        data class NeedsSystemConfirmation(
            val intentSender: IntentSender,
            val pendingUris: List<Uri>,
            val pendingFilePaths: List<String>
        ) : DeleteResult()
        data class Error(val message: String, val throwable: Throwable? = null) : DeleteResult()
    }

    /**
     * Checks whether Cyos Player has the special MANAGE_MEDIA permission on Android 12+ (API 31+),
     * which allows MediaStore.createDeleteRequest() to delete videos immediately without a system popup
     * (just like MX Player when granted Media Management access).
     */
    fun canDeleteSilentlyWithoutSystemPopup(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaStore.canManageMedia(context)
        } else {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        }
    }

    /**
     * Opens the Android 12+ (API 31+) "Media management apps" settings screen for Cyos Player
     * so the user can grant one-tap silent MediaStore deletion.
     */
    fun requestManageMediaPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !MediaStore.canManageMedia(context)) {
            val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Releases the file lock if any of the target videos are currently loaded in Cyos Player's ExoPlayer.
     * Must be called on the main thread before invoking deleteVideos().
     */
    fun releaseActivePlayerHandleIfNeeded(
        exoPlayer: ExoPlayer?,
        targetUris: List<Uri>,
        targetFilePaths: List<String> = emptyList()
    ) {
        if (exoPlayer == null) return
        val currentItem = exoPlayer.currentMediaItem
        val currentUri = currentItem?.localConfiguration?.uri
        val isPlayingTarget = (currentUri != null && targetUris.contains(currentUri)) ||
            (currentUri?.path != null && targetFilePaths.any { it == currentUri.path })

        if (isPlayingTarget) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    /**
     * Converts a raw video file path (e.g. /storage/emulated/0/Movies/sample.mp4) into its
     * canonical MediaStore.Video.Media content:// URI so ContentResolver can remove it from MediaStore.
     */
    fun resolveVideoContentUri(context: Context, fileOrUri: String): Uri? {
        if (fileOrUri.startsWith("content://")) {
            return Uri.parse(fileOrUri)
        }
        val cleanPath = if (fileOrUri.startsWith("file://")) {
            Uri.parse(fileOrUri).path ?: fileOrUri.removePrefix("file://")
        } else {
            fileOrUri
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Video.Media._ID)
        val selection = "${MediaStore.Video.Media.DATA} = ?"
        val selectionArgs = arrayOf(cleanPath)

        return try {
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val id = cursor.getLong(idCol)
                    ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Deletes one or more videos from both device storage and Android MediaStore.
     *
     * @param context Application or Activity context
     * @param videoUris Content URIs from MediaStore.Video.Media (if known)
     * @param filePaths Optional raw file paths (used as fallback or to trigger MediaScannerConnection)
     * @param deleteAssociatedSubtitles If true, also deletes matching .srt/.ass/.ssa/.vtt/.sub files
     */
    suspend fun deleteVideos(
        context: Context,
        videoUris: List<Uri>,
        filePaths: List<String> = emptyList(),
        deleteAssociatedSubtitles: Boolean = true
    ): DeleteResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1. Resolve all paths to canonical MediaStore content:// URIs
        val resolvedUris = mutableListOf<Uri>()
        resolvedUris.addAll(videoUris.filter { it.scheme == ContentResolver.SCHEME_CONTENT })

        for (path in filePaths) {
            val resolved = resolveVideoContentUri(context, path)
            if (resolved != null && !resolvedUris.contains(resolved)) {
                resolvedUris.add(resolved)
            }
        }

        // Also resolve any file:// URIs passed in videoUris
        val allFilePaths = filePaths.toMutableList()
        for (uri in videoUris) {
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    if (!allFilePaths.contains(path)) {
                        allFilePaths.add(path)
                    }
                    val resolved = resolveVideoContentUri(context, path)
                    if (resolved != null && !resolvedUris.contains(resolved)) {
                        resolvedUris.add(resolved)
                    }
                }
            }
        }

        // Lookup file paths from MediaStore if not passed explicitly (so we can scan & clean subtitles)
        for (uri in resolvedUris) {
            queryFilePathFromUri(resolver, uri)?.let { path ->
                if (!allFilePaths.contains(path)) {
                    allFilePaths.add(path)
                }
            }
        }

        try {
            // 2. Android 11+ (API 30+): Use MediaStore.createDeleteRequest for atomic MediaStore + disk removal
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && resolvedUris.isNotEmpty()) {
                // First try direct ContentResolver.delete() in case the app owns the file
                val unownedUris = mutableListOf<Uri>()
                val immediatelyDeletedUris = mutableListOf<Uri>()

                for (uri in resolvedUris) {
                    try {
                        val rows = resolver.delete(uri, null, null)
                        if (rows > 0) {
                            immediatelyDeletedUris.add(uri)
                        } else {
                            // Check if a local file exists and can be deleted directly by app
                            val localPath = queryFilePathFromUri(resolver, uri)
                                ?: allFilePaths.firstOrNull { File(it).exists() }
                            if (localPath != null && File(localPath).exists() && File(localPath).delete()) {
                                immediatelyDeletedUris.add(uri)
                            } else {
                                unownedUris.add(uri)
                            }
                        }
                    } catch (secEx: SecurityException) {
                        unownedUris.add(uri)
                    }
                }

                if (unownedUris.isEmpty()) {
                    for (path in allFilePaths) {
                        val f = File(path)
                        if (f.exists()) f.delete()
                    }
                    cleanupSubtitlesAndScan(context, allFilePaths, deleteAssociatedSubtitles)
                    return@withContext DeleteResult.Success(immediatelyDeletedUris)
                }

                val pendingIntent = MediaStore.createDeleteRequest(resolver, unownedUris)
                return@withContext DeleteResult.NeedsSystemConfirmation(
                    intentSender = pendingIntent.intentSender,
                    pendingUris = unownedUris,
                    pendingFilePaths = allFilePaths
                )
            }

            // 3. Android 10 (API 29) and Android 9 & below (API <= 28)
            val deletedUris = mutableListOf<Uri>()
            for (uri in resolvedUris) {
                try {
                    val rows = resolver.delete(uri, null, null)
                    if (rows > 0) {
                        deletedUris.add(uri)
                    }
                } catch (securityException: SecurityException) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        val recoverable = securityException as? RecoverableSecurityException
                            ?: return@withContext DeleteResult.Error(
                                "SecurityException deleting $uri",
                                securityException
                            )
                        return@withContext DeleteResult.NeedsSystemConfirmation(
                            intentSender = recoverable.userAction.actionIntent.intentSender,
                            pendingUris = listOf(uri),
                            pendingFilePaths = allFilePaths
                        )
                    } else {
                        return@withContext DeleteResult.Error(
                            "Permission denied deleting $uri",
                            securityException
                        )
                    }
                }
            }

            // 4. Fallback for legacy paths or unindexed video files + MediaScanner sync
            for (path in allFilePaths) {
                val file = File(path)
                if (file.exists()) {
                    if (file.delete()) {
                        val fileUri = Uri.fromFile(file)
                        if (!deletedUris.contains(fileUri)) {
                            deletedUris.add(fileUri)
                        }
                    }
                }
                // Also remove any lingering row in MediaStore.Files / MediaStore.Video
                runCatching {
                    resolver.delete(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Video.Media.DATA} = ?",
                        arrayOf(path)
                    )
                }
            }

            cleanupSubtitlesAndScan(context, allFilePaths, deleteAssociatedSubtitles)
            return@withContext DeleteResult.Success(deletedUris)
        } catch (e: Exception) {
            return@withContext DeleteResult.Error(
                message = e.localizedMessage ?: "Failed to delete video from MediaStore",
                throwable = e
            )
        }
    }

    /**
     * Completes post-confirmation cleanup after the user approves the Android 10/11+ system dialog.
     * On Android 10 (API 29), approving RecoverableSecurityException only grants permission, so
     * ContentResolver.delete() must be called again. On Android 11+ (API 30+), createDeleteRequest()
     * already deleted the video, so we only clean up subtitles and notify MediaScanner.
     */
    suspend fun onSystemConfirmationApproved(
        context: Context,
        pendingUris: List<Uri>,
        pendingFilePaths: List<String>,
        deleteAssociatedSubtitles: Boolean = true
    ): DeleteResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            for (uri in pendingUris) {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
        for (path in pendingFilePaths) {
            runCatching {
                val f = File(path)
                if (f.exists()) f.delete()
            }
        }
        cleanupSubtitlesAndScan(context, pendingFilePaths, deleteAssociatedSubtitles)
        DeleteResult.Success(pendingUris)
    }

    private fun queryFilePathFromUri(resolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }

    private fun cleanupSubtitlesAndScan(
        context: Context,
        videoFilePaths: List<String>,
        deleteAssociatedSubtitles: Boolean
    ) {
        if (videoFilePaths.isEmpty()) return
        val pathsToScan = mutableListOf<String>()

        for (videoPath in videoFilePaths) {
            pathsToScan.add(videoPath)
            if (deleteAssociatedSubtitles) {
                val videoFile = File(videoPath)
                val parentDir = videoFile.parentFile
                val baseName = videoFile.nameWithoutExtension
                if (parentDir != null && parentDir.exists()) {
                    val subtitleExtensions = listOf("srt", "ass", "ssa", "vtt", "sub")
                    for (ext in subtitleExtensions) {
                        val subFile = File(parentDir, "$baseName.$ext")
                        if (subFile.exists() && subFile.delete()) {
                            pathsToScan.add(subFile.absolutePath)
                        }
                    }
                    // Also clean up language-tagged sidecar subtitles (e.g. movie.en.srt, movie.ja.ass)
                    parentDir.listFiles()?.forEach { candidate ->
                        if (candidate.isFile) {
                            val ext = candidate.extension.lowercase()
                            val nameNoExt = candidate.nameWithoutExtension
                            if (ext in subtitleExtensions &&
                                (nameNoExt.equals(baseName, ignoreCase = true) ||
                                    nameNoExt.startsWith("$baseName.", ignoreCase = true))
                            ) {
                                if (candidate.delete()) {
                                    pathsToScan.add(candidate.absolutePath)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Notify MediaScannerConnection so external gallery/video apps evict any cached paths immediately
        runCatching {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                pathsToScan.toTypedArray(),
                null,
                null
            )
        }
    }
}

/**
 * Drop-in Activity/Fragment controller for Cyos Player that wires ActivityResultLauncher
 * to CyosPlayerMediaStoreDeleter and triggers a UI list refresh callback on completion.
 */
class CyosVideoDeleteController(
    private val activity: ComponentActivity,
    private val exoPlayerProvider: () -> ExoPlayer? = { null },
    private val onVideosDeletedFromMediaStore: (deletedUris: List<Uri>, deletedPaths: List<String>) -> Unit,
    private val onDeleteCancelledOrFailed: (reason: String) -> Unit = {}
) {
    private var pendingUris: List<Uri> = emptyList()
    private var pendingPaths: List<String> = emptyList()

    private val confirmationLauncher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uris = pendingUris
                val paths = pendingPaths
                pendingUris = emptyList()
                pendingPaths = emptyList()

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    for (uri in uris) {
                        runCatching { activity.contentResolver.delete(uri, null, null) }
                    }
                }
                for (path in paths) {
                    runCatching {
                        val f = File(path)
                        if (f.exists()) f.delete()
                    }
                }
                if (paths.isNotEmpty()) {
                    runCatching {
                        MediaScannerConnection.scanFile(
                            activity.applicationContext,
                            paths.toTypedArray(),
                            null,
                            null
                        )
                    }
                }
                onVideosDeletedFromMediaStore(uris, paths)
            } else {
                pendingUris = emptyList()
                pendingPaths = emptyList()
                onDeleteCancelledOrFailed("Video deletion cancelled by user")
            }
        }

    /**
     * Call this when the user taps Delete on a video in Cyos Player.
     */
    suspend fun deleteVideos(
        videoUris: List<Uri>,
        filePaths: List<String> = emptyList(),
        deleteAssociatedSubtitles: Boolean = true
    ) {
        withContext(Dispatchers.Main) {
            CyosPlayerMediaStoreDeleter.releaseActivePlayerHandleIfNeeded(
                exoPlayer = exoPlayerProvider(),
                targetUris = videoUris,
                targetFilePaths = filePaths
            )
        }

        when (
            val result = CyosPlayerMediaStoreDeleter.deleteVideos(
                context = activity,
                videoUris = videoUris,
                filePaths = filePaths,
                deleteAssociatedSubtitles = deleteAssociatedSubtitles
            )
        ) {
            is CyosPlayerMediaStoreDeleter.DeleteResult.Success -> {
                withContext(Dispatchers.Main) {
                    onVideosDeletedFromMediaStore(result.deletedUris, filePaths)
                }
            }
            is CyosPlayerMediaStoreDeleter.DeleteResult.NeedsSystemConfirmation -> {
                pendingUris = result.pendingUris
                pendingPaths = result.pendingFilePaths
                withContext(Dispatchers.Main) {
                    confirmationLauncher.launch(
                        IntentSenderRequest.Builder(result.intentSender).build()
                    )
                }
            }
            is CyosPlayerMediaStoreDeleter.DeleteResult.Error -> {
                withContext(Dispatchers.Main) {
                    onDeleteCancelledOrFailed(result.message)
                }
            }
        }
    }
}
