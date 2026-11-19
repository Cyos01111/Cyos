package com.cyos.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener

/**
 * Smart DataSource.Factory that routes local device URIs (file://, content://, android.resource://)
 * directly through a zero-copy DefaultDataSource for lowest CPU/battery overhead, while routing
 * network streams (http://, https://) through the shared 200 MiB LRU CacheDataSource.Factory.
 */
@OptIn(UnstableApi::class)
class CyosSmartDataSourceFactory(
    context: Context
) : DataSource.Factory {

    private val appContext: Context = context.applicationContext ?: context
    private val localFactory: DefaultDataSource.Factory = DefaultDataSource.Factory(appContext)
    private val cachedNetworkFactory: DataSource.Factory by lazy {
        CyosMediaCache.buildCacheDataSourceFactory(appContext)
    }

    override fun createDataSource(): DataSource {
        return CyosSmartRoutingDataSource(
            localSourceProvider = { localFactory.createDataSource() },
            cachedNetworkSourceProvider = { cachedNetworkFactory.createDataSource() }
        )
    }
}

@OptIn(UnstableApi::class)
private class CyosSmartRoutingDataSource(
    private val localSourceProvider: () -> DataSource,
    private val cachedNetworkSourceProvider: () -> DataSource
) : DataSource {

    private var activeDataSource: DataSource? = null
    private val transferListeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        activeDataSource?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme?.lowercase()
        val isNetwork = scheme == "http" || scheme == "https"
        val selected = if (isNetwork) {
            cachedNetworkSourceProvider()
        } else {
            localSourceProvider()
        }
        transferListeners.forEach { selected.addTransferListener(it) }
        activeDataSource = selected
        return selected.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val current = activeDataSource ?: return C.RESULT_END_OF_INPUT
        return current.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = activeDataSource?.uri

    override fun getResponseHeaders(): Map<String, List<String>> {
        return activeDataSource?.responseHeaders ?: emptyMap()
    }

    override fun close() {
        try {
            activeDataSource?.close()
        } finally {
            activeDataSource = null
        }
    }
}
