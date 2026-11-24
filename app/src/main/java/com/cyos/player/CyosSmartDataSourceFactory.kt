package com.cyos.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource

/**
 * Direct, zero-cache DataSource.Factory:
 * - Local URIs (`content://`, `file://`, `android.resource://`) are routed directly through
 *   Media3's [DefaultDataSource] (`ContentDataSource` / `FileDataSource`), completely bypassing
 *   SimpleCache, SQLite locks, and any disk cache I/O.
 * - Remote HTTP/HTTPS streams use a tuned keep-alive [DefaultHttpDataSource.Factory] with fast
 *   connect/read timeouts and cross-protocol redirects while adding zero extra APK size.
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
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Connection" to "keep-alive",
                    "Accept-Encoding" to "identity"
                )
            )

    private val directFactory: DefaultDataSource.Factory =
        DefaultDataSource.Factory(appContext, httpDataSourceFactory)

    override fun createDataSource(): DataSource = directFactory.createDataSource()

    companion object {
        const val HTTP_CONNECT_TIMEOUT_MS: Int = 5_000
        const val HTTP_READ_TIMEOUT_MS: Int = 8_000
        const val USER_AGENT: String = "CyosPlay/1.08rv6 (Android; Media3)"
    }
}
