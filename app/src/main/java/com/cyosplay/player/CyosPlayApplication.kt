package com.cyosplay.player

import android.app.Application
import com.cyos.player.CyosCacheProvider
import com.cyos.player.CyosPlayerHolder
import com.cyosplay.player.util.CryptoSecurityManager

class CyosPlayApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CryptoSecurityManager.enforcePermanentNonDebugMode(this)
        // Warm up the 200 MiB SimpleCache index off the main thread before any video is tapped
        CyosCacheProvider.warmUpAsync(this)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CyosPlayerHolder.trimOrReleaseOnMemoryPressure(this, level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        CyosPlayerHolder.release()
    }
}
