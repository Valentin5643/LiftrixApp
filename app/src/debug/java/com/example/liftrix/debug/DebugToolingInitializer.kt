package com.example.liftrix.debug

import android.app.Application
import android.os.StrictMode
import com.example.liftrix.BuildConfig
import timber.log.Timber

object DebugToolingInitializer {
    fun initialize(application: Application) {
        if (!BuildConfig.ENABLE_DEBUG_TOOLS || !BuildConfig.ENABLE_STRICT_MODE) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )

        Timber.d("Debug tooling initialized for ${application.packageName}")
    }
}
