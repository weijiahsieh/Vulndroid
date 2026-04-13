package com.vulndroid.vulnerabilities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

/**
 * TokenRefreshReceiver — M8: Security Misconfiguration
 * =====================================================
 * Exported BroadcastReceiver with no permission requirement.
 *
 * Any app on the device can trigger this receiver:
 *   val intent = Intent("com.vulndroid.TOKEN_REFRESH")
 *   context.sendBroadcast(intent)
 *
 * Via ADB:
 *   adb shell am broadcast -a com.vulndroid.TOKEN_REFRESH
 *
 * IMPACT:
 * 1. Attacker triggers token refresh — new token is generated and logged to Logcat
 * 2. Attacker reads the new token from Logcat (or from SharedPreferences on debug build)
 * 3. Attacker uses the new token to make authenticated API calls
 *
 * In a more complex app, this could also be used to:
 * - Force logout (by corrupting the stored token)
 * - Trigger server-side operations if the refresh notifies a backend
 *
 * REMEDIATION:
 *   android:exported="false" — use LocalBroadcastManager for internal broadcasts
 *   OR add: android:permission="com.vulndroid.permission.TOKEN_REFRESH"
 *   (a custom signature-level permission that only VulnDroid can grant)
 */
class TokenRefreshReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "VulnDroid_Receiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.vulndroid.TOKEN_REFRESH") return

        // [M8] VULNERABILITY: No check that the sender is our own app
        // Any sender can reach this code path
        Log.w(TAG, "Token refresh triggered — caller package: ${intent.`package` ?: "unknown"}")

        val prefs: SharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val username = prefs.getString("username", null)

        if (username == null) {
            Log.d(TAG, "No active session — refresh ignored")
            return
        }

        // Generate a new fake token
        val newToken = android.util.Base64.encodeToString(
            "$username:refreshed:${System.currentTimeMillis()}".toByteArray(),
            android.util.Base64.NO_WRAP
        )

        // [M9] VULNERABILITY: New token written to SharedPreferences
        prefs.edit().putString("auth_token", newToken).apply()

        // [M6] VULNERABILITY: New token logged to Logcat — readable via adb
        Log.d(TAG, "New auth token generated: $newToken")
        Log.i(TAG, "Token refreshed for user: $username | token: $newToken")
    }
}
