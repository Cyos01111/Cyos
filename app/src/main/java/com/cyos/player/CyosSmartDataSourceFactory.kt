package com.cyos.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource

/**
 * Direct, zero-cache DataSource.Factory that reads local device media (file://, content://,
 * android.resource://) and network streams directly without writing to or indexing a disk cache.
 * Eliminates background disk I/O, cache SQLite locks, and extra RAM allocations.
 */
@OptIn(UnstableApi::class)
class CyosSmartDataSourceFactory(
    context: Context
) : DataSource.Factory {

    private val appContext: Context = context.applicationContext ?: context
    private val directFactory: DefaultDataSource.Factory = DefaultDataSource.Factory(appContext)

    override fun createDataSource(): DataSource = directFactory.createDataSource()
}
