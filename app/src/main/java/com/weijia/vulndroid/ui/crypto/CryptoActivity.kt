package com.weijia.vulndroid.ui.crypto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weijia.vulndroid.utils.InsecureCryptoUtil
import com.weijia.vulndroid.ui.theme.AccentAmber
import com.weijia.vulndroid.ui.theme.AccentRed
import com.weijia.vulndroid.ui.theme.AdbHint
import com.weijia.vulndroid.ui.theme.CodeCard
import com.weijia.vulndroid.ui.theme.DangerBox
import com.weijia.vulndroid.ui.theme.NavyBorder
import com.weijia.vulndroid.ui.theme.SectionLabel
import com.weijia.vulndroid.ui.theme.SecureBox
import com.weijia.vulndroid.ui.theme.TextMuted
import com.weijia.vulndroid.ui.theme.TextPrimary
import com.weijia.vulndroid.ui.theme.VulnButton
import com.weijia.vulndroid.ui.theme.VulnDroidTheme
import com.weijia.vulndroid.ui.theme.VulnScreen

/**
 * CryptoActivity — Jetpack Compose
 * ==================================
 * M10 Interactive demo of broken cryptographic implementations:
 *   • MD5 password hashing — no salt, no work factor, rainbow-table crackable
 *   • AES/ECB mode — deterministic, identical plaintext → identical ciphertext
 *   • Hardcoded key in InsecureCryptoUtil.kt — visible in decompiled APK
 */
class CryptoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VulnDroidTheme { CryptoScreen(onBack = ::finish) } }
    }
}

@Composable
fun CryptoScreen(onBack: () -> Unit) {
    var passwordInput by remember { mutableStateOf("password123") }
    var md5Result by remember { mutableStateOf("") }
    var crackNote by remember { mutableStateOf("") }

    var ecbInput by remember { mutableStateOf("password123password123") }
    var ecbResult by remember { mutableStateOf("") }

    VulnScreen(
        title = "Weak Cryptography", owaspTag = "M10 — OWASP Mobile Top 10",
        owaspColor = AccentRed, onBack = onBack
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {

            // ── MD5 section ───────────────────────────────────────────────────
            SectionLabel("MD5 Password Hashing (Broken)")
            DangerBox(
                title = "Why MD5 is broken for passwords",
                body = "• No salt → identical passwords = identical hashes\n" +
                        "• No work factor → GPU = 10 billion hashes/sec\n" +
                        "• Rainbow tables cover virtually all common passwords\n" +
                        "• CWE-916: Insufficient Password Hashing Iterations"
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = passwordInput, onValueChange = { passwordInput = it },
                label = { Text("Enter password to hash with MD5") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentRed, unfocusedBorderColor = NavyBorder,
                    focusedLabelColor = AccentRed, unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            VulnButton(text = "Hash with MD5 (Insecure)", color = AccentRed, onClick = {
                val hash = InsecureCryptoUtil.hashPasswordMD5(passwordInput)
                md5Result = "MD5(\"$passwordInput\")\n= $hash"
                crackNote = when (passwordInput) {
                    "password123", "admin", "123456", "qwerty" ->
                        "⚡ Cracked instantly at crackstation.net (< 1 second)"

                    else ->
                        "Submit to crackstation.net — likely cracked instantly"
                }
            })
            if (md5Result.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                CodeCard(text = md5Result, textColor = AccentRed)
                Spacer(Modifier.height(4.dp))
                Text(
                    crackNote,
                    color = AccentAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            AdbHint("crackstation.net → paste hash → cracked in < 1 second")

            // ── AES/ECB section ───────────────────────────────────────────────
            SectionLabel("AES/ECB Mode Encryption (Broken)")
            DangerBox(
                title = "Why AES/ECB is broken",
                body = "• Each 16-byte block encrypted independently with same key\n" +
                        "• Identical plaintext blocks → identical ciphertext blocks\n" +
                        "• Patterns in plaintext visible in ciphertext (ECB Penguin)\n" +
                        "• Hardcoded key: \"vulndroid2024key\" visible in decompiled APK"
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ecbInput, onValueChange = { ecbInput = it },
                label = { Text("Enter text to encrypt with AES/ECB") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentRed, unfocusedBorderColor = NavyBorder,
                    focusedLabelColor = AccentRed, unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            VulnButton(text = "Encrypt with AES/ECB (Insecure)", color = AccentRed, onClick = {
                val c1 = InsecureCryptoUtil.encryptECB(ecbInput)
                val c2 = InsecureCryptoUtil.encryptECB(ecbInput)
                ecbResult = buildString {
                    appendLine("Input: \"$ecbInput\"")
                    appendLine()
                    appendLine("Encrypt #1:")
                    appendLine(c1)
                    appendLine()
                    appendLine("Encrypt #2:")
                    appendLine(c2)
                    appendLine()
                    if (c1 == c2) appendLine("⚠ IDENTICAL OUTPUT — ECB is deterministic!\nSame plaintext → same ciphertext → patterns leaked")
                    appendLine()
                    appendLine("Key: vulndroid2024key (hardcoded)")
                }
            })
            if (ecbResult.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                CodeCard(text = ecbResult, textColor = AccentRed)
            }

            Spacer(Modifier.height(16.dp))

            // Secure remediation
            SecureBox(
                body = "Passwords:   Argon2 / bcrypt / scrypt\n" +
                        "Symmetric:   AES/GCM with random IV per encrypt\n" +
                        "Hashing:     SHA-256 or SHA-3 minimum\n" +
                        "Key storage: Android Keystore (hardware-backed)\n" +
                        "Key derivation: PBKDF2 with 600,000+ iterations"
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
