package com.weijia.vulndroid

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weijia.vulndroid.ui.theme.AccentAmber
import com.weijia.vulndroid.ui.theme.AccentRed
import com.weijia.vulndroid.ui.theme.NavyBorder
import com.weijia.vulndroid.ui.theme.TextMuted
import com.weijia.vulndroid.ui.theme.TextPrimary
import com.weijia.vulndroid.ui.theme.VulnDroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NetworkActivity — Jetpack Compose
 * ====================================
 * [M5] Makes live insecure HTTP call — disabled SSL validation, cleartext traffic
 * [M1] API key shown in request display — hardcoded in InsecureApiClient
 * [M6] Full request/response body logged to Logcat
 */
class NetworkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VulnDroidTheme { NetworkScreen(onBack = ::finish) } }
    }
}

@Composable
fun NetworkScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var userId by remember { mutableStateOf("1") }
    var requestText by remember { mutableStateOf("Tap Fetch to see the insecure request") }
    var responseText by remember { mutableStateOf("Response will appear here\n\nMonitor with:\nadb logcat | grep VulnDroid_API") }
    var isLoading by remember { mutableStateOf(false) }

    VulnScreen(title = "Insecure Network", owaspTag = "M5 — OWASP Mobile Top 10",
        owaspColor = AccentRed, onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {

            // SSL status card
            DangerBox(
                title = "🔴  SSL VALIDATION: DISABLED",
                body  = "• All certificates accepted including self-signed\n" +
                        "• User-installed CA certs trusted (Burp Suite works instantly)\n" +
                        "• HTTP cleartext traffic explicitly permitted\n" +
                        "• Hostname verification disabled"
            )

            SectionLabel("Make Insecure API Call")
            OutlinedTextField(
                value = userId, onValueChange = { userId = it },
                label = { Text("User ID to fetch") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentRed, unfocusedBorderColor = NavyBorder,
                    focusedLabelColor = AccentRed, unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            VulnButton(
                text = if (isLoading) "Fetching..." else "Fetch Profile (HTTP — Interceptable)",
                color = AccentRed, enabled = !isLoading,
                onClick = {
                    isLoading = true
                    requestText = buildString {
                        appendLine("GET http://api.vulndroid-backend.com/v1/users/$userId")
                        appendLine("X-API-Key: sk-prod-9f8a2b1c3d4e5f6g7h8i9j0k1l2m3n4  [M1]")
                        appendLine("Authorization: Bearer sk-prod-9f8a...")
                        appendLine()
                        appendLine("⚠ Sent over unencrypted HTTP")
                        appendLine("⚠ SSL certificate validation DISABLED")
                    }
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            try { InsecureApiClient.fetchUserProfile(userId) }
                            catch (e: Exception) {
                                "Network error (no real server in demo): ${e.message}\n\n" +
                                "In a real deployment Burp Suite would capture\n" +
                                "the auth token and API key in plaintext."
                            }
                        }
                        responseText = "Response (also in Logcat):\n\n$result"
                        Log.d("VulnDroid_Network", "Response for user $userId: $result")
                        isLoading = false
                    }
                }
            )

            SectionLabel("Request Sent")
            CodeCard(text = requestText, textColor = AccentAmber)

            SectionLabel("Response / Logcat Output")
            CodeCard(text = responseText)
            AdbHint("adb logcat | grep VulnDroid_API")

            // Burp setup guide
            SectionLabel("Burp Suite Intercept Setup")
            SecureBox(
                body = "1. Burp Suite → Proxy → 127.0.0.1:8080\n" +
                       "2. adb reverse tcp:8080 tcp:8080\n" +
                       "3. Emulator: Settings → WiFi → Proxy\n" +
                       "   Host: 127.0.0.1  Port: 8080\n" +
                       "4. No CA cert needed — trust-all accepts Burp's cert\n" +
                       "5. All traffic visible in Burp HTTP History tab"
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
