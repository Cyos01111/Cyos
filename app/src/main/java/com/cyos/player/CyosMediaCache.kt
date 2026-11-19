package com.cyos.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object CyosMediaCache {
    const val MAX_CACHE_BYTES: Long = 200L * 1024L * 1024L // 200 MB LRU cache

    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return CyosCacheProvider.getOrCreate(context.applicationContext ?: context)
    }

    fun buildCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val appContext = context.applicationContext ?: context
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)

        val upstreamFactory = DefaultDataSource.Factory(appContext, httpFactory)

        return CacheDataSource.Factory()
            .setCache(getCache(appContext))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun releaseCache() {
        CyosCacheProvider.release()
    }
}
