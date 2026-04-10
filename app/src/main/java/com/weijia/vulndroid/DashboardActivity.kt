package com.weijia.vulndroid

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weijia.vulndroid.ui.theme.NavyBorder
import com.weijia.vulndroid.ui.theme.NavyDark
import com.weijia.vulndroid.ui.theme.NavySurface
import com.weijia.vulndroid.ui.theme.TextMuted
import com.weijia.vulndroid.ui.theme.TextPrimary
import com.weijia.vulndroid.ui.theme.TextSecond
import com.weijia.vulndroid.ui.theme.VulnDroidTheme

/**
 * DashboardActivity — Jetpack Compose
 * =====================================
 * [M3] No server-side session validation on resume — is_logged_in read from
 *      SharedPreferences without checking token expiry or server state.
 * [M8] Misconfiguration banner reflects real AndroidManifest.xml settings.
 */
class DashboardActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        // [M3] No token expiry check — session lives forever
        val username = intent.getStringExtra("username") ?: prefs.getString("username", "unknown") ?: "unknown"

        setContent {
            VulnDroidTheme {
                DashboardScreen(
                    username = username,
                    onStorage = { startActivity(Intent(this, StorageActivity::class.java)) },
                    onNetwork = { startActivity(Intent(this, NetworkActivity::class.java)) },
                    onCrypto = { startActivity(Intent(this, CryptoActivity::class.java)) },
                    onSqlInject = { startActivity(Intent(this, SqlInjectionActivity::class.java)) },
                    onDeepLink = {
                        startActivity(
                            Intent(
                                this,
                                VulnerableDeepLinkActivity::class.java
                            )
                        )
                    },
                    onLogout = {
                        prefs.edit().clear().apply()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(
    username: String,
    onStorage: () -> Unit, onNetwork: () -> Unit, onCrypto: () -> Unit,
    onSqlInject: () -> Unit, onDeepLink: () -> Unit, onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(NavyDark)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF0D1220)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("🔓 VulnDroid", color = TextPrimary, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Logged in as: $username", color = TextMuted, fontSize = 12.sp)
            }
            TextButton(onClick = onLogout) {
                Text("Log out", color = TextSecond, fontSize = 12.sp)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text("VULNERABILITY MODULES", color = TextMuted, fontSize = 10.sp,
                letterSpacing = 1.2.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 12.dp))

            val modules = listOf(
                Triple("💾  Insecure Storage",     "M9 · SharedPrefs · SQLite · External log", onStorage),
                Triple("🌐  Insecure Network",      "M5 · Cleartext HTTP · SSL bypass · MITM",  onNetwork),
                Triple("🔐  Weak Cryptography",     "M10 · MD5 · AES-ECB · Fixed IV",            onCrypto),
                Triple("💉  SQL Injection",          "M4 · User search dump · Auth bypass",       onSqlInject),
                Triple("🚪  Exported Activity",     "M8 · Deep link bypass — no auth required",  onDeepLink),
            )

            modules.forEach { (title, sub, onClick) ->
                ModuleCard(title = title, subtitle = sub, onClick = onClick)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))

            // Active misconfigs banner
            Column(
                Modifier.fillMaxWidth()
                    .background(Color(0xFF1A0A0A), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF3F1010), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text("⚠  Active Misconfigurations (M7 / M8)",
                    color = Color(0xFFF87171), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "• android:debuggable=\"true\"\n" +
                    "• android:allowBackup=\"true\"\n" +
                    "• VulnerableDeepLinkActivity exported (no permission)\n" +
                    "• UserDataProvider exported (no readPermission)\n" +
                    "• TokenRefreshReceiver exported (no permission)",
                    color = TextSecond, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun ModuleCard(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(NavySurface, RoundedCornerShape(10.dp))
            .border(1.dp, NavyBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Text("›", color = NavyBorder, fontSize = 22.sp)
    }
}
