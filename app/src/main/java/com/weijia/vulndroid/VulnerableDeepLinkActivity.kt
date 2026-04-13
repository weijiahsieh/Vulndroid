package com.weijia.vulndroid

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weijia.vulndroid.ui.theme.AccentAmber
import com.weijia.vulndroid.ui.theme.AccentRed
import com.weijia.vulndroid.ui.theme.DangerBox
import com.weijia.vulndroid.ui.theme.NavyBorder
import com.weijia.vulndroid.ui.theme.TextMuted
import com.weijia.vulndroid.ui.theme.TextPrimary
import com.weijia.vulndroid.ui.theme.VulnButton
import com.weijia.vulndroid.ui.theme.VulnDroidTheme

/**
 * VulnerableDeepLinkActivity — Jetpack Compose
 * ==============================================
 * M8 Exported Activity with no permission — reachable by any app or adb.
 *
 * Attack:
 *   adb shell am start -n com.vulndroid/.ui.VulnerableDeepLinkActivity
 *
 * This bypasses login entirely — the password reset form loads with zero auth.
 * In a real app this could trigger server-side account takeover.
 */
class VulnerableDeepLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // M8 VULNERABILITY: No authentication check here
        // Secure: verify the caller has an active session before rendering
        setContent { VulnDroidTheme { DeepLinkScreen(onBack = ::finish) } }
    }
}

@Composable
fun DeepLinkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("⚠", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("M8: Exported Activity", color = TextPrimary,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        DangerBox(
            title = "Reached WITHOUT Authentication",
            body = "This screen is reachable by any app on the device " +
                    "or directly via adb — no login required.\n\n" +
                    "Attack command:\n" +
                    "adb shell am start \\\n" +
                    "  -n com.vulndroid/.ui.VulnerableDeepLinkActivity"
        )

        Spacer(Modifier.height(24.dp))
        Text("Password Reset (No Auth Required)", color = AccentAmber,
            fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email to reset") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentRed, unfocusedBorderColor = NavyBorder,
                focusedLabelColor = AccentRed, unfocusedLabelColor = TextMuted,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(Modifier.height(12.dp))
        VulnButton(text = "Reset Password (No Auth)", color = AccentRed, onClick = {
            // M3 No authentication, no rate limiting, no CSRF token
            Toast.makeText(
                context,
                "Reset triggered for: $email — with zero authentication",
                Toast.LENGTH_LONG
            ).show()
        })
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text("← Back", color = TextMuted)
        }
    }
}
