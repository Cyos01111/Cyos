package com.cyos.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Direct, zero-cache DataSource.Factory:
 * - `cyosvault://` URIs are routed through [VaultChunkedAesGcmDataSource] for instant,
 *   zero-plaintext, RAM-only chunked AES-256-GCM decryption.
 * - Local URIs (`content://`, `file://`, `android.resource://`) are routed directly through
 *   Media3's [DefaultDataSource] (`ContentDataSource` / `FileDataSource`), completely bypassing
 *   SimpleCache, SQLite locks, and any disk cache I/O.
 * - Remote HTTPS streams use a tuned keep-alive [DefaultHttpDataSource.Factory] with cross-protocol
 *   redirects disabled (`allowCrossProtocolRedirects = false`) to block HTTPS→HTTP downgrades.
 */
@OptIn(UnstableApi::class)
class CyosSmartDataSourceFactory(
    context: Context
) : DataSource.Factory {

    private val appContext: Context = context.applicationContext ?: context

    private val httpDataSourceFactory: DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(ALLOW_CROSS_PROTOCOL_REDIRECTS)
            .setKeepPostFor302Redirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Connection" to "keep-alive",
                    "Accept-Encoding" to "identity"
                )
            )

    private val directFactory: DefaultDataSource.Factory =
        DefaultDataSource.Factory(appContext, httpDataSourceFactory)

    private val vaultFactory: VaultChunkedAesGcmDataSource.Factory =
        VaultChunkedAesGcmDataSource.Factory(appContext)

    override fun createDataSource(): DataSource = RoutingDataSource(
        defaultDataSource = directFactory.createDataSource(),
        vaultDataSource = vaultFactory.createDataSource()
    )

    private class RoutingDataSource(
        private val defaultDataSource: DataSource,
        private val vaultDataSource: DataSource
    ) : DataSource {
        private var activeSource: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            defaultDataSource.addTransferListener(transferListener)
            vaultDataSource.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val chosen = if (VaultChunkedAesGcmDataSource.isVaultStreamUri(dataSpec.uri)) {
                vaultDataSource
            } else {
                defaultDataSource
            }
            activeSource = chosen
            return chosen.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return activeSource?.read(buffer, offset, length) ?: -1
        }

        override fun getUri(): Uri? = activeSource?.uri

        override fun getResponseHeaders(): Map<String, List<String>> {
            return activeSource?.responseHeaders ?: emptyMap()
        }

        override fun close() {
            val current = activeSource
            activeSource = null
            current?.close()
        }
    }

    companion object {
        const val HTTP_CONNECT_TIMEOUT_MS: Int = 5_000
        const val HTTP_READ_TIMEOUT_MS: Int = 8_000
        const val ALLOW_CROSS_PROTOCOL_REDIRECTS: Boolean = false
        const val USER_AGENT: String = "CyosPlay/1.08rv9 (Android; Media3)"
    }
}
