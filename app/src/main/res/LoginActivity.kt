package com.vulndroid.ui.login

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.vulndroid.R
import com.vulndroid.databinding.ActivityLoginBinding
import com.vulndroid.ui.dashboard.DashboardActivity
import com.vulndroid.util.InsecureCryptoUtil

/**
 * LoginActivity — VulnDroid
 * ==========================
 * SECURITY FINDINGS IN THIS FILE:
 *
 * [M3] Insecure Authentication — Client-side auth bypass
 *      The hardcoded admin backdoor (line ~70) bypasses server authentication entirely.
 *      Local credential comparison means an attacker can patch the APK to always return true.
 *
 * [M6] Inadequate Privacy Controls — Credentials logged to Logcat
 *      Username and password are written to Android system logs (line ~85).
 *      Any app with READ_LOGS permission (or via adb) can read these.
 *      Attack: `adb logcat | grep -i "vulndroid"`
 *
 * [M9] Insecure Data Storage — Password stored in SharedPreferences
 *      The actual plaintext password is written to SharedPreferences (line ~95).
 *      File location: /data/data/com.vulndroid/shared_prefs/user_session.xml
 *      Attack: `adb shell run-as com.vulndroid cat shared_prefs/user_session.xml`
 *              (works on debug builds / rooted devices)
 *
 * [M1] Improper Credential Usage — Hardcoded admin backdoor
 *      Admin credentials checked client-side from string resources.
 *      Visible in decompiled APK without any obfuscation of the logic.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: SharedPreferences

    // [M9] VULNERABILITY: Using default SharedPreferences with MODE_PRIVATE
    // Still insecure on rooted/debug devices — plaintext XML file on disk
    companion object {
        private const val TAG = "VulnDroid_Login"
        private const val PREFS_NAME = "user_session"
        // [M1] VULNERABILITY: Hardcoded credentials in source code
        // These are also in strings.xml — double exposure
        private const val BACKDOOR_USER = "admin@vulndroid.com"
        private const val BACKDOOR_PASS = "Admin@123!"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // [M3] VULNERABILITY: Auto-login from stored credentials without token validation
        // If credentials are in SharedPrefs, skip login entirely — no server check
        val savedUser = prefs.getString("username", null)
        val savedPass = prefs.getString("password", null)
        if (savedUser != null && savedPass != null) {
            // [M3] VULNERABILITY: No check if token is expired or revoked
            navigateToDashboard(savedUser)
            return
        }

        binding.loginButton.setOnClickListener {
            val username = binding.usernameInput.text.toString()
            val password = binding.passwordInput.text.toString()
            attemptLogin(username, password)
        }
    }

    private fun attemptLogin(username: String, password: String) {

        // [M6] VULNERABILITY: Credentials logged to Logcat
        // Any app with READ_LOGS or adb access can read this
        // Attack: adb logcat | grep -i "credentials"
        Log.d(TAG, "Login attempt — username: $username, password: $password")
        Log.i(TAG, "User credentials: $username / $password") // Even worse — INFO level stays longer

        // [M4] VULNERABILITY: No input validation
        // username and password are passed directly to the API and to the local DB query
        // without any sanitization — enables SQL injection in the local DB layer

        // [M3] VULNERABILITY: Client-side backdoor — auth bypass without server call
        // An attacker who decompiles the APK sees this check and can use the backdoor,
        // OR can patch the smali to always jump to navigateToDashboard()
        if (username == BACKDOOR_USER && password == BACKDOOR_PASS) {
            Log.w(TAG, "Admin backdoor used — bypassing server auth")
            saveCredentialsAndLogin(username, password, isAdmin = true)
            return
        }

        // Simulate server authentication (would be an API call in real app)
        // For demo: any non-empty credentials succeed (simulates weak server-side auth)
        if (username.isNotEmpty() && password.isNotEmpty()) {
            saveCredentialsAndLogin(username, password, isAdmin = false)
        } else {
            binding.errorText.text = "Please enter credentials"
        }
    }

    private fun saveCredentialsAndLogin(username: String, password: String, isAdmin: Boolean) {
        // [M9] VULNERABILITY: Storing plaintext password in SharedPreferences
        // File: /data/data/com.vulndroid/shared_prefs/user_session.xml
        // Extraction: adb shell run-as com.vulndroid cat shared_prefs/user_session.xml
        // On rooted device: adb shell cat /data/data/com.vulndroid/shared_prefs/user_session.xml
        prefs.edit()
            .putString("username", username)
            .putString("password", password)       // [M9] VULNERABILITY: plaintext password
            .putString("auth_token", generateFakeToken(username))
            .putBoolean("is_admin", isAdmin)
            .putBoolean("is_logged_in", true)
            // [M9] VULNERABILITY: No expiry on the stored session
            .apply()

        // [M6] VULNERABILITY: Sensitive data written to external storage log file
        // Any app with READ_EXTERNAL_STORAGE can read this
        writeToInsecureLog("User logged in: $username | admin: $isAdmin | pass: $password")

        navigateToDashboard(username)
    }

    private fun generateFakeToken(username: String): String {
        // [M10] VULNERABILITY: Token is just base64(username:timestamp) — not a real JWT
        // Predictable and forgeable — no signature
        val raw = "$username:${System.currentTimeMillis()}"
        return android.util.Base64.encodeToString(raw.toByteArray(), android.util.Base64.NO_WRAP)
    }

    private fun writeToInsecureLog(message: String) {
        // [M9] VULNERABILITY: Writing sensitive data to external storage
        // Path: /sdcard/vulndroid_debug.log — world-readable
        try {
            val logFile = java.io.File(
                android.os.Environment.getExternalStorageDirectory(),
                "vulndroid_debug.log"
            )
            logFile.appendText("${java.util.Date()}: $message\n")
        } catch (e: Exception) {
            Log.e(TAG, "Log write failed", e)
        }
    }

    private fun navigateToDashboard(username: String) {
        startActivity(Intent(this, DashboardActivity::class.java).apply {
            putExtra("username", username)
        })
        finish()
    }
}
