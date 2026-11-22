package com.cyosplay.player

import android.app.Application
import com.cyos.player.CyosPlayerHolder
import com.cyosplay.player.util.CryptoSecurityManager
import com.cyosplay.player.util.MemoryManager
import java.io.File

class CyosPlayApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CryptoSecurityManager.enforcePermanentNonDebugMode(this)
        purgeLegacyMediaDiskCacheAsync()
    }

    /**
     * One-time cleanup of any legacy disk cache directory left over from earlier versions.
     * Runs quietly off the main thread without initializing any media disk cache.
     */
    private fun purgeLegacyMediaDiskCacheAsync() {
        val legacyCacheDir = File(cacheDir, "cyos_media_cache")
        if (!legacyCacheDir.exists()) return
        Thread(
            {
                runCatching { legacyCacheDir.deleteRecursively() }
            },
            "cyos-legacy-cache-cleanup"
        ).apply {
            isDaemon = true
            start()
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CyosPlayerHolder.trimOrReleaseOnMemoryPressure(this, level)
        MemoryManager.performEmergencyMemoryTrim(this)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        CyosPlayerHolder.release()
        MemoryManager.performEmergencyMemoryTrim(this)
    }
}
