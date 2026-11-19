package com.cyos.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
object CyosCacheProvider {
    const val MAX_CACHE_BYTES: Long = 200L * 1024L * 1024L // 200 MiB LRU cache

    @Volatile
    private var cache: SimpleCache? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun warmUpAsync(context: Context) {
        if (cache != null) return
        val appContext = context.applicationContext ?: context
        scope.launch {
            runCatching { getOrCreate(appContext) }
        }
    }

    fun getOrCreate(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File((context.applicationContext ?: context).cacheDir, "cyos_media_cache"),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context.applicationContext ?: context)
            ).also { cache = it }
        }
    }

    fun isInitialized(): Boolean = cache != null

    fun release() {
        synchronized(this) {
            try {
                cache?.release()
            } catch (_: Throwable) {
            } finally {
                cache = null
            }
        }
    }
}
