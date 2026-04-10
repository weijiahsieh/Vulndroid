package com.weijia.vulndroid

import android.app.Application
import android.util.Log

/**
 * VulnDroidApp — Application class
 * ==================================
 * [M7] VULNERABILITY: StrictMode deliberately disabled.
 * StrictMode in debug builds catches common security mistakes at runtime —
 * unencrypted disk I/O, cleartext network traffic, leaked SQLite cursors.
 * Disabling it hides these warnings from the developer.
 *
 * [M6] VULNERABILITY: Global uncaught exception handler logs full stack
 * traces including file paths, class names, and internal state — useful
 * for attackers doing reconnaissance via Logcat.
 *
 * A secure application would:
 * - Enable StrictMode in debug builds
 * - Use a crash reporting SDK (Crashlytics) instead of logging to Logcat
 * - Scrub sensitive data from crash reports before upload
 */
class VulnDroidApp : Application() {

    companion object {
        private const val TAG = "VulnDroid_App"
    }

    override fun onCreate() {
        super.onCreate()

        // [M7] VULNERABILITY: StrictMode disabled — hides security violations at runtime
        // Secure: enable in debug builds to catch disk/network issues early
        // android.os.StrictMode.setThreadPolicy(...)  ← intentionally NOT set
        // android.os.StrictMode.setVmPolicy(...)      ← intentionally NOT set

        // [M6] VULNERABILITY: Full exception details logged to Logcat
        // Reveals internal class structure, file paths, and state to anyone running adb logcat
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread: ${thread.name}")
            Log.e(TAG, "Exception: ${throwable.javaClass.name}: ${throwable.message}")
            Log.e(TAG, "Stack trace:", throwable)
            // No sanitization — full internal details exposed
        }

        Log.d(TAG, "VulnDroid started — debuggable=${BuildConfig.DEBUG}")
        Log.d(TAG, "Package: ${packageName} | Version: ${BuildConfig.VERSION_NAME}")
    }
}
