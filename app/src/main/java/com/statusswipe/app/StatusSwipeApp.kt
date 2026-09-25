package com.statusswipe.app

import android.app.Application
import com.topjohnwu.superuser.Shell

/**
 * Application class that initializes the root shell configuration.
 * libsu requires early initialization before any Shell operations.
 */
class StatusSwipeApp : Application() {

    companion object {
        init {
            // Configure the default shell builder before any Shell operations
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(10) // 10 second timeout
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
    }
}
