package com.cyosplay.player.data.repository

import android.content.Context
import com.cyosplay.player.data.model.Playlist
import com.cyosplay.player.util.CryptoSecurityManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.crypto.SecretKey

class PlaylistRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cyosplay_playlists_store_v2", Context.MODE_PRIVATE)

    private val rotationListener = CryptoSecurityManager.KeyRotationListener { oldKey, newKey ->
        reEncryptStore(oldKey, newKey)
    }

    init {
        CryptoSecurityManager.registerRotationListener(rotationListener)
    }

    @Synchronized
    private fun reEncryptStore(oldKey: SecretKey, newKey: SecretKey) {
        val editor = prefs.edit()
        for (key in listOf(KEY_PLAYLISTS_ENC, KEY_FAVORITES_ENC)) {
            val raw = prefs.getString(key, null)
            if (raw != null && CryptoSecurityManager.isEncryptedString(raw)) {
                val plain = CryptoSecurityManager.decryptStringWithKey(raw, oldKey)
                if (plain != null) {
                    editor.putString(key, CryptoSecurityManager.encryptStringWithKey(plain, newKey))
                }
            }
        }
        editor.commit()
    }

    fun getPlaylists(): List<Playlist> {
        val raw = prefs.getString(KEY_PLAYLISTS_ENC, null) ?: return emptyList()
        val json = CryptoSecurityManager.decryptString(appContext, raw) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pathsArr = obj.optJSONArray("videoPaths") ?: JSONArray()
                    val paths = mutableListOf<String>()
                    for (j in 0 until pathsArr.length()) {
                        val p = pathsArr.optString(j)
                        if (p.isNotBlank()) paths.add(p)
                    }
                    add(
                        Playlist(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            description = obj.optString("description", ""),
                            videoPaths = paths,
                            createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun createPlaylist(name: String, description: String = "", initialPaths: List<String> = emptyList()): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim(),
            videoPaths = initialPaths.distinct(),
            createdAtMs = System.currentTimeMillis()
        )
        val updated = getPlaylists().toMutableList().apply { add(0, playlist) }
        savePlaylists(updated)
        return playlist
    }

    fun deletePlaylist(playlistId: String) {
        val updated = getPlaylists().filterNot { it.id == playlistId }
        savePlaylists(updated)
    }

    fun addVideosToPlaylist(playlistId: String, videoPaths: List<String>) {
        val updated = getPlaylists().map { pl ->
            if (pl.id == playlistId) {
                pl.copy(videoPaths = (pl.videoPaths + videoPaths).distinct())
            } else {
                pl
            }
        }
        savePlaylists(updated)
    }

    fun removeVideoFromPlaylist(playlistId: String, videoPath: String) {
        val updated = getPlaylists().map { pl ->
            if (pl.id == playlistId) {
                pl.copy(videoPaths = pl.videoPaths.filterNot { it == videoPath })
            } else {
                pl
            }
        }
        savePlaylists(updated)
    }

    private fun savePlaylists(playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { pl ->
            val pathsArr = JSONArray()
            pl.videoPaths.forEach { pathsArr.put(it) }
            arr.put(
                JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("description", pl.description)
                    put("videoPaths", pathsArr)
                    put("createdAtMs", pl.createdAtMs)
                }
            )
        }
        val cipher = CryptoSecurityManager.encryptString(appContext, arr.toString())
        prefs.edit().putString(KEY_PLAYLISTS_ENC, cipher).commit()
    }

    fun getFavoriteVideoPaths(): Set<String> {
        val raw = prefs.getString(KEY_FAVORITES_ENC, null) ?: return emptySet()
        val json = CryptoSecurityManager.decryptString(appContext, raw) ?: return emptySet()
        return try {
            val arr = JSONArray(json)
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

    fun toggleFavorite(videoPath: String): Set<String> {
        val current = getFavoriteVideoPaths().toMutableSet()
        if (current.contains(videoPath)) {
            current.remove(videoPath)
        } else {
            current.add(videoPath)
        }
        val arr = JSONArray()
        current.forEach { arr.put(it) }
        val cipher = CryptoSecurityManager.encryptString(appContext, arr.toString())
        prefs.edit().putString(KEY_FAVORITES_ENC, cipher).commit()
        return current
    }

    /**
     * Relinks a moved video's path in both Favorites and all Playlists so moving a video
     * never loses its favorite status or playlist entries.
     */
    fun relinkMovedVideoReferences(oldPath: String, newPath: String) {
        val cleanOld = oldPath.trim()
        val cleanNew = newPath.trim()
        if (cleanOld.isEmpty() || cleanNew.isEmpty() || cleanOld.equals(cleanNew, ignoreCase = true)) return

        val favs = getFavoriteVideoPaths().toMutableSet()
        if (favs.removeAll { it.trim().equals(cleanOld, ignoreCase = true) }) {
            favs.add(cleanNew)
            val arr = JSONArray()
            favs.forEach { arr.put(it) }
            val cipher = CryptoSecurityManager.encryptString(appContext, arr.toString())
            prefs.edit().putString(KEY_FAVORITES_ENC, cipher).commit()
        }

        val currentPlaylists = getPlaylists()
        var playlistsChanged = false
        val updatedPlaylists = currentPlaylists.map { pl ->
            var entryChanged = false
            val newPaths = pl.videoPaths.map { p ->
                if (p.trim().equals(cleanOld, ignoreCase = true)) {
                    entryChanged = true
                    cleanNew
                } else {
                    p
                }
            }.distinct()
            if (entryChanged) {
                playlistsChanged = true
                pl.copy(videoPaths = newPaths)
            } else {
                pl
            }
        }
        if (playlistsChanged) {
            savePlaylists(updatedPlaylists)
        }
    }

    fun relinkMovedVideoPath(oldPath: String, newPath: String) {
        relinkMovedVideoReferences(oldPath, newPath)
    }

    /**
     * Removes deleted video paths from both Favorites and all Playlists so deleted videos
     * never remain as broken references.
     */
    fun removeDeletedVideoPaths(deletedPaths: Set<String>) {
        val normalized = deletedPaths.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (normalized.isEmpty()) return

        val favs = getFavoriteVideoPaths().toMutableSet()
        if (favs.removeAll { fav -> normalized.any { it.equals(fav.trim(), ignoreCase = true) } }) {
            val arr = JSONArray()
            favs.forEach { arr.put(it) }
            val cipher = CryptoSecurityManager.encryptString(appContext, arr.toString())
            prefs.edit().putString(KEY_FAVORITES_ENC, cipher).commit()
        }

        val currentPlaylists = getPlaylists()
        var playlistsChanged = false
        val updatedPlaylists = currentPlaylists.map { pl ->
            val filtered = pl.videoPaths.filterNot { p ->
                normalized.any { it.equals(p.trim(), ignoreCase = true) }
            }
            if (filtered.size != pl.videoPaths.size) {
                playlistsChanged = true
                pl.copy(videoPaths = filtered)
            } else {
                pl
            }
        }
        if (playlistsChanged) {
            savePlaylists(updatedPlaylists)
        }
    }

    companion object {
        private const val KEY_PLAYLISTS_ENC = "playlists_aes_gcm"
        private const val KEY_FAVORITES_ENC = "favorites_aes_gcm"
    }
}
