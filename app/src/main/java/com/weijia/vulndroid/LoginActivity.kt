package com.weijia.vulndroid

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
import java.io.File
import java.util.Date

/**
 * LoginActivity — Jetpack Compose
 * ================================
 * [M1] Hardcoded admin backdoor in companion object — visible after apktool decompile
 * [M3] Client-side auth bypass — no real server validation
 * [M6] Username + password logged to Logcat via Log.d
 * [M9] Plaintext password stored in SharedPreferences
 *
 * UI and logic are co-located in the same file — idiomatic Compose pattern.
 */
class LoginActivity : ComponentActivity() {

    companion object {
        private const val TAG = "VulnDroid_Login"
        // [M1] VULNERABILITY: Hardcoded credentials visible in decompiled APK
        private const val BACKDOOR_USER = "admin@vulndroid.com"
        private const val BACKDOOR_PASS = "Admin@123!"
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("user_session", MODE_PRIVATE)

        // [M3] Auto-login from stored credentials — no server token validation
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

    private fun attemptLogin(username: String, password: String) {
        // [M6] VULNERABILITY: Credentials written to system Logcat
        Log.d(TAG, "Login attempt — username: $username, password: $password")

        // [M3] VULNERABILITY: Hardcoded backdoor bypasses server auth entirely
        if (username == BACKDOOR_USER && password == BACKDOOR_PASS) {
            Log.w(TAG, "Admin backdoor used")
            saveAndLogin(username, password, isAdmin = true); return
        }
        if (username.isNotEmpty() && password.isNotEmpty()) {
            saveAndLogin(username, password, isAdmin = false)
        }
    }

    private fun saveAndLogin(username: String, password: String, isAdmin: Boolean) {
        val token = Base64.encodeToString(
            "$username:${System.currentTimeMillis()}".toByteArray(), Base64.NO_WRAP
        )
        // [M9] VULNERABILITY: Plaintext password stored in SharedPreferences
        // Extract: adb shell run-as com.vulndroid cat shared_prefs/user_session.xml
        prefs.edit()
            .putString("username", username)
            .putString("password", password)   // ← plaintext password — never do this
            .putString("auth_token", token)
            .putBoolean("is_admin", isAdmin)
            .putBoolean("is_logged_in", true)
            .apply()
        // [M9] Also write to world-readable external storage
        try {
            val f = File(Environment.getExternalStorageDirectory(), "vulndroid_debug.log")
            f.appendText("${Date()}: login user=$username pass=$password\n")
        } catch (_: Exception) {}
        navigateToDashboard(username)
    }

    private fun navigateToDashboard(username: String) {
        startActivity(Intent(this, DashboardActivity::class.java).putExtra("username", username))
        finish()
    }
}

@Composable
fun LoginScreen(onLogin: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentBlue, unfocusedBorderColor = NavyBorder,
        focusedLabelColor = AccentBlue, unfocusedLabelColor = TextMuted,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentBlue
    )

    Column(
        modifier = Modifier.fillMaxSize().background(NavyDark)
            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        Text("🔓", fontSize = 60.sp)
        Spacer(Modifier.height(8.dp))
        Text("VulnDroid", fontSize = 30.sp, fontWeight = FontWeight.Bold,
            color = TextPrimary, fontFamily = FontFamily.Monospace)
        Text("Deliberately Vulnerable App", fontSize = 12.sp,
            color = AccentRed, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(24.dp))
        // Warning banner
        Box(Modifier.fillMaxWidth().background(Color(0xFF1F2937), RoundedCornerShape(8.dp)).padding(12.dp)) {
            Text("⚠ Educational Use Only — Intentional Security Vulnerabilities",
                color = AccentAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(value = username, onValueChange = { username = it; error = "" },
            label = { Text("Email address") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(8.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it; error = "" },
            label = { Text("Password") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show", color = TextMuted, fontSize = 12.sp)
                }
            },
            colors = fieldColors, shape = RoundedCornerShape(8.dp))
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = AccentRed, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (username.isEmpty() || password.isEmpty()) error = "Please enter credentials"
            else onLogin(username, password)
        }, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(8.dp)) {
            Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(32.dp))
        Text("DEMO CREDENTIALS", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().background(NavySurface, RoundedCornerShape(8.dp))
            .border(1.dp, NavyBorder, RoundedCornerShape(8.dp)).padding(14.dp)) {
            VulnTag("[M3] SQL Injection Auth Bypass")
            Spacer(Modifier.height(4.dp))
            Text("Username: admin' --\nPassword: anything", color = TextSecond,
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = NavyBorder)
            VulnTag("[M1] Hardcoded Admin Backdoor", color = AccentRed)
            Spacer(Modifier.height(4.dp))
            Text("Username: admin@vulndroid.com\nPassword: Admin@123!", color = TextSecond,
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(40.dp))
    }
}
