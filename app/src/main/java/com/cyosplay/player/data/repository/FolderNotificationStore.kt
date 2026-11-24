package com.cyosplay.player.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight, IO-only persistent store for "New Video Folder" dot badge tracking
 * keyed by MediaStore.Video.Media.BUCKET_ID.
 *
 * Behavior:
 * - First scan with permission granted establishes the baseline: all currently existing
 *   BUCKET_IDs are recorded in [seenFolderIds] so old folders never show notification dots.
 * - Subsequent scans add any BUCKET_ID not in [seenFolderIds] to [unseenNewFolderIds].
 * - Deleted BUCKET_IDs are pruned from [unseenNewFolderIds] on every scan so stale dots never remain.
 * - Opening a folder immediately clears its BUCKET_ID from [unseenNewFolderIds], adds it to
 *   [seenFolderIds], and persists asynchronously on [Dispatchers.IO].
 */
class FolderNotificationStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    prefsName: String = PREFS_NAME
) {

    data class FolderNotificationState(
        val seenFolderIds: Set<String> = emptySet(),
        val unseenNewFolderIds: Set<String> = emptySet(),
        val baselineInitialized: Boolean = false
    ) {
        val hasNewFolders: Boolean
            get() = unseenNewFolderIds.isNotEmpty()
    }

    private val appContext = context.applicationContext ?: context
    private val prefs by lazy {
        appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }
    private val lock = Any()

    suspend fun readState(): FolderNotificationState = withContext(ioDispatcher) {
        synchronized(lock) {
            readStateLocked()
        }
    }

    suspend fun syncDiscoveredFolders(
        currentBucketIds: Set<String>,
        hasStoragePermission: Boolean = true
    ): FolderNotificationState = withContext(ioDispatcher) {
        synchronized(lock) {
            val cleanCurrent = currentBucketIds
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            val previous = readStateLocked()

            if (!previous.baselineInitialized) {
                // Do not finalize an empty baseline before media permission is granted.
                if (!hasStoragePermission && cleanCurrent.isEmpty()) {
                    return@synchronized previous
                }
                val initialState = FolderNotificationState(
                    seenFolderIds = cleanCurrent,
                    unseenNewFolderIds = emptySet(),
                    baselineInitialized = true
                )
                writeStateLocked(initialState)
                return@synchronized initialState
            }

            val newlyDiscovered = cleanCurrent.filterNot { it in previous.seenFolderIds }
            // Keep only folders that still exist on device (prunes deleted BUCKET_IDs).
            val updatedUnseen = (previous.unseenNewFolderIds + newlyDiscovered)
                .filter { it in cleanCurrent }
                .toSet()

            val updatedState = FolderNotificationState(
                seenFolderIds = previous.seenFolderIds,
                unseenNewFolderIds = updatedUnseen,
                baselineInitialized = true
            )

            if (updatedState != previous) {
                writeStateLocked(updatedState)
            }
            updatedState
        }
    }

    suspend fun markFolderSeen(bucketId: String): FolderNotificationState = withContext(ioDispatcher) {
        val cleanId = bucketId.trim()
        synchronized(lock) {
            val previous = readStateLocked()
            if (cleanId.isEmpty()) return@synchronized previous

            val updatedSeen = previous.seenFolderIds + cleanId
            val updatedUnseen = previous.unseenNewFolderIds - cleanId
            val updatedState = FolderNotificationState(
                seenFolderIds = updatedSeen,
                unseenNewFolderIds = updatedUnseen,
                baselineInitialized = true
            )
            if (updatedState != previous) {
                writeStateLocked(updatedState)
            }
            updatedState
        }
    }

    suspend fun markMultipleFoldersSeen(bucketIds: Collection<String>): FolderNotificationState = withContext(ioDispatcher) {
        val cleanIds = bucketIds.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        synchronized(lock) {
            val previous = readStateLocked()
            if (cleanIds.isEmpty()) return@synchronized previous

            val updatedState = FolderNotificationState(
                seenFolderIds = previous.seenFolderIds + cleanIds,
                unseenNewFolderIds = previous.unseenNewFolderIds - cleanIds,
                baselineInitialized = true
            )
            if (updatedState != previous) {
                writeStateLocked(updatedState)
            }
            updatedState
        }
    }

    private fun readStateLocked(): FolderNotificationState {
        val baseline = prefs.getBoolean(KEY_BASELINE_INITIALIZED, false)
        val seen = prefs.getStringSet(KEY_SEEN_FOLDER_IDS, emptySet())?.toSet() ?: emptySet()
        val unseen = prefs.getStringSet(KEY_UNSEEN_NEW_FOLDER_IDS, emptySet())?.toSet() ?: emptySet()
        return FolderNotificationState(
            seenFolderIds = seen,
            unseenNewFolderIds = unseen,
            baselineInitialized = baseline
        )
    }

    private fun writeStateLocked(state: FolderNotificationState) {
        prefs.edit()
            .putBoolean(KEY_BASELINE_INITIALIZED, state.baselineInitialized)
            .putStringSet(KEY_SEEN_FOLDER_IDS, state.seenFolderIds.toSet())
            .putStringSet(KEY_UNSEEN_NEW_FOLDER_IDS, state.unseenNewFolderIds.toSet())
            .apply()
    }

    companion object {
        const val PREFS_NAME = "cyosplay_folder_notification_store_v1"
        private const val KEY_BASELINE_INITIALIZED = "baseline_initialized"
        private const val KEY_SEEN_FOLDER_IDS = "seen_folder_ids"
        private const val KEY_UNSEEN_NEW_FOLDER_IDS = "unseen_new_folder_ids"
    }
}
