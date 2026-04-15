package com.weijia.vulndroid.ui.login

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weijia.vulndroid.ui.dashboard.DashboardActivity
import com.weijia.vulndroid.ui.theme.AccentAmber
import com.weijia.vulndroid.ui.theme.AccentBlue
import com.weijia.vulndroid.ui.theme.AccentRed
import com.weijia.vulndroid.ui.theme.NavyBorder
import com.weijia.vulndroid.ui.theme.NavyDark
import com.weijia.vulndroid.ui.theme.NavySurface
import com.weijia.vulndroid.ui.theme.TextMuted
import com.weijia.vulndroid.ui.theme.TextPrimary
import com.weijia.vulndroid.ui.theme.TextSecond
import com.weijia.vulndroid.ui.theme.VulnDroidTheme
import com.weijia.vulndroid.ui.theme.VulnTag
import java.io.File
import java.util.Date
import androidx.core.content.edit
import com.weijia.vulndroid.data.local.VulnDroidDatabase
import com.weijia.vulndroid.ui.theme.AccentGreen
import com.weijia.vulndroid.utils.InsecureCryptoUtil

/**
 * LoginActivity — Jetpack Compose
 * ================================
 * Authentication now performs a REAL database lookup so that:
 *  - Wrong credentials  → rejected with "Invalid credentials" error
 *  - Correct credentials → accepted (john.doe / password123)
 *  - SQL injection       → M3, M4 bypasses the password check entirely
 *  - Hardcoded backdoor  → M1 bypasses the database lookup entirely
 *
 * M1 Hardcoded admin backdoor in companion object — visible after apktool decompile
 * M3 Client-side auth bypass — no real server validation
 * M6 Username + password logged to Logcat via Log.d
 * M9 Plaintext password stored in SharedPreferences
 *
 * UI and logic are co-located in the same file — idiomatic Compose pattern.
 */
class LoginActivity : ComponentActivity() {

    companion object {
        private const val TAG = "VulnDroid_Login"
        // M1 VULNERABILITY: Hardcoded credentials visible in decompiled APK
        private const val BACKDOOR_USER = "admin@vulndroid.com"
        private const val BACKDOOR_PASS = "Admin@123!"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var db: VulnDroidDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        db = VulnDroidDatabase(this)

        // M3 VULNERABILITY: Auto-login from stored credentials with no server
        // token validation — session never expires, no revocation check
        val savedUser = prefs.getString("username", null)
        val savedPass = prefs.getString("password", null)
        if (savedUser != null && savedPass != null) {
            navigateToDashboard(savedUser); return
        }

        setContent {
            VulnDroidTheme {
                LoginScreen(onLogin = ::attemptLogin)
            }
        }
    }

    /**
     * Typed result so the UI can surface the right message per failure mode.
     */
    sealed class LoginResult {
        data class Success(val username: String, val isAdmin: Boolean) : LoginResult()
        object InvalidCredentials : LoginResult()
        object EmptyFields : LoginResult()
    }

    /**
     * Auth flow — three separate paths, each demonstrating a different vulnerability:
     *
     * Path A — M1 Hardcoded backdoor:
     *   Checked FIRST, before the database is queried.
     *   An attacker who decompiles the APK reads BACKDOOR_USER / BACKDOOR_PASS
     *   directly from the source and logs in without ever touching the DB.
     *
     * Path B — M3, M4 SQL injection bypass:
     *   username = "admin' --"  →  password check commented out by --
     *   getUserByCredentials() builds the query via string concatenation,
     *   so the injection lands inside a real database query and succeeds.
     *
     * Path C — Legitimate login (what a secure app would do exclusively):
     *   john.doe / password123 → MD5 hash matches → succeeds
     *   john.doe / wrongpass   → MD5 hash mismatches → null returned → rejected
     */


    private fun attemptLogin(username: String, password: String): LoginResult {
        if (username.isEmpty() || password.isEmpty()) return LoginResult.EmptyFields

        // M6 VULNERABILITY: Plaintext credentials written to Logcat
        // Any app with READ_LOGS or adb access can read: adb logcat | grep VulnDroid_Login
        Log.d(TAG, "Login attempt — username: $username, password: $password")

        // ── Path A:[M1 Hardcoded backdoor ──────────────────────────────────
        // Checked before the DB so the backdoor works even if the DB is wiped.
        if (username == BACKDOOR_USER && password == BACKDOOR_PASS) {
            Log.w(TAG, "[M1] Admin backdoor used — bypassing database lookup entirely")
            return LoginResult.Success(username = BACKDOOR_USER, isAdmin = true)
        }

        // ── Path B + C: Database lookup (vulnerable to SQL injection) ─────────
        // M10 Password hashed with MD5 before lookup — broken hash algorithm.
        // M4 The hash is concatenated into a raw SQL string inside getUserByCredentials().
        //
        // For a legitimate user: MD5("password123") must match the DB value.
        // For a SQL injection:   the -- operator comments out the password check,
        //                        so the hash value is irrelevant — any password works.
        val hashedPassword = InsecureCryptoUtil.hashPasswordMD5(password)
        Log.d(TAG, "Auth query with MD5 hash: $hashedPassword")  // M6 hash logged

        val user = db.getUserByCredentials(username, hashedPassword)

        return if (user != null) {
            val isAdmin = user[VulnDroidDatabase.COL_IS_ADMIN] == "1"
            Log.d(TAG, "Login success — user: $username, admin: $isAdmin")  // M6
            LoginResult.Success(username = user[VulnDroidDatabase.COL_USERNAME] ?: username, isAdmin = isAdmin)
        } else {
            Log.d(TAG, "Login failed — no matching record for username: $username")
            LoginResult.InvalidCredentials
        }
    }

    internal fun handleLoginResult(result: LoginResult, onError: (String) -> Unit) {
        when (result) {
            is LoginResult.Success   -> saveAndLogin(result.username, result.isAdmin)
            is LoginResult.InvalidCredentials -> onError("Invalid credentials")
            is LoginResult.EmptyFields        -> onError("Please enter credentials")
        }
    }

    private fun saveAndLogin(username: String, isAdmin: Boolean) {
        // M10 VULNERABILITY: Weak token — base64(username:timestamp), not a signed JWT
        val token = Base64.encodeToString(
            "$username:${System.currentTimeMillis()}".toByteArray(), Base64.NO_WRAP
        )

        // M9 VULNERABILITY: Credentials and plaintext password stored in SharedPreferences.
        // SharedPreferences writes an unencrypted XML file to internal storage.
        // On a debug build, no root is needed to read it:
        //   adb shell run-as com.vulndroid cat shared_prefs/user_session.xml
        //
        // Note: we store the password field from SharedPreferences for the M9 demo
        // in StorageActivity. In a real app you should NEVER store the password at
        // all after login — only the server-issued session token.
        val rawPassword = prefs.getString("pending_password", "") ?: ""
        prefs.edit {
            putString("username", username)
                .putString("password", rawPassword)    // M9 plaintext password — never do this
                .putString("auth_token", token)        // M9 weak predictable token — no expiry
                .putBoolean("is_admin", isAdmin)
                .putBoolean("is_logged_in", true)
                .remove("pending_password")
        }

        // M9 VULNERABILITY: Sensitive data also written to world-readable external storage.
        // Readable by any app with READ_EXTERNAL_STORAGE permission.
        //   adb shell cat /sdcard/vulndroid_debug.log
        try {
            val f = File(
                Environment.getExternalStorageDirectory(),
                "vulndroid_debug.log"
            )
            f.appendText("${Date()}: login user=$username admin=$isAdmin token=$token\n")
        } catch (_: Exception) {}

        navigateToDashboard(username)
    }


    private fun navigateToDashboard(username: String) {
        startActivity(Intent(this, DashboardActivity::class.java).putExtra("username", username))
        finish()
    }
}

@Composable
fun LoginScreen(onLogin: (String, String) -> LoginActivity.LoginResult) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Retrieve the activity so handleLoginResult can be called
    val activity = androidx.compose.ui.platform.LocalContext.current as LoginActivity

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentBlue, unfocusedBorderColor = NavyBorder,
        focusedLabelColor = AccentBlue, unfocusedLabelColor = TextMuted,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentBlue
    )

    fun handleSignIn() {
        if (username.isEmpty() || password.isEmpty()) {
            error = "Please enter credentials"; return
        }
        isLoading = true
        error = ""
        // [M9] Temporarily stash the plaintext password so saveAndLogin can write it
        // to SharedPreferences for the Demo 2 extraction demonstration.
        activity.getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
            .edit { putString("pending_password", password) }

        val result = onLogin(username, password)
        isLoading = false
        activity.handleLoginResult(result, onError = { error = it })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        // Logo
        Text("🔓", fontSize = 60.sp)
        Spacer(Modifier.height(8.dp))
        Text("VulnDroid", fontSize = 30.sp, fontWeight = FontWeight.Bold,
            color = TextPrimary, fontFamily = FontFamily.Monospace)
        Text("Deliberately Vulnerable App", fontSize = 12.sp,
            color = AccentRed, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(24.dp))

        // Warning banner
        Box(
            Modifier.fillMaxWidth()
                .background(Color(0xFF1F2937), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                "⚠ Educational Use Only — Intentional Security Vulnerabilities",
                color = AccentAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(28.dp))

        // Username field
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = "" },
            label = { Text("Email address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            isError = error.isNotEmpty(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(Modifier.height(12.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = "" },
            label = { Text("Password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = error.isNotEmpty(),
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show", color = TextMuted, fontSize = 12.sp)
                }
            },
            colors = fieldColors,
            shape = RoundedCornerShape(8.dp)
        )

        // Error message — shown for both empty fields and invalid credentials
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✕", color = AccentRed, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(error, color = AccentRed, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Sign In button
        Button(
            onClick = { handleSignIn() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White, strokeWidth = 2.dp
                )
            } else {
                Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        // Demo credentials panel — three distinct paths clearly labelled
        Text(
            "DEMO CREDENTIALS",
            color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier.fillMaxWidth()
                .background(NavySurface, RoundedCornerShape(8.dp))
                .border(1.dp, NavyBorder, RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            // Legitimate login — proves the system actually validates
            VulnTag("✓  Legitimate Login", color = AccentGreen)
            Spacer(Modifier.height(4.dp))
            Text(
                "Username: john.doe\nPassword: password123",
                color = TextSecond, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, lineHeight = 17.sp
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = NavyBorder)

            // SQL injection bypass
            VulnTag("[M3][M4] SQL Injection Auth Bypass")
            Spacer(Modifier.height(4.dp))
            Text(
                "Username: admin' --\nPassword: anything",
                color = TextSecond, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, lineHeight = 17.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "→ Logs in as admin without knowing the password",
                color = AccentAmber, fontSize = 10.sp, fontFamily = FontFamily.Monospace
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = NavyBorder)

            // Hardcoded backdoor
            VulnTag("[M1] Hardcoded Admin Backdoor", color = AccentRed)
            Spacer(Modifier.height(4.dp))
            Text(
                "Username: admin@vulndroid.com\nPassword: Admin@123!",
                color = TextSecond, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, lineHeight = 17.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "→ Bypasses database lookup entirely — embedded in APK",
                color = AccentRed.copy(alpha = 0.8f), fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}
