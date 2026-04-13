package com.weijia.vulndroid

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
import androidx.compose.ui.unit.dp
import com.weijia.vulndroid.data.local.VulnDroidDatabase
import com.weijia.vulndroid.ui.theme.AccentAmber
import com.weijia.vulndroid.ui.theme.AccentRed
import com.weijia.vulndroid.ui.theme.AdbHint
import com.weijia.vulndroid.ui.theme.CodeCard
import com.weijia.vulndroid.ui.theme.DangerBox
import com.weijia.vulndroid.ui.theme.NavyBorder
import com.weijia.vulndroid.ui.theme.ResultRow
import com.weijia.vulndroid.ui.theme.SectionLabel
import com.weijia.vulndroid.ui.theme.TextMuted
import com.weijia.vulndroid.ui.theme.TextPrimary
import com.weijia.vulndroid.ui.theme.VulnButton
import com.weijia.vulndroid.ui.theme.VulnDroidTheme
import com.weijia.vulndroid.ui.theme.VulnScreen

/**
 * SqlInjectionActivity — Jetpack Compose
 * ========================================
 * M4 Live SQL injection demo — type attack payloads, see generated SQL,
 *      watch the full database dump appear in the results list.
 *
 * Attack payloads to try:
 *   ' OR '1'='1          → dump all users
 *   admin' --            → comment out WHERE clause
 *   ' OR 1=1 --          → variant dump
 */
class SqlInjectionActivity : ComponentActivity() {

    private lateinit var db: VulnDroidDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = VulnDroidDatabase(this)

        setContent {
            VulnDroidTheme {
                SqlInjectionScreen(
                    onBack = ::finish,
                    search = { query -> db.searchUsers(query) }
                )
            }
        }
    }
}

@Composable
fun SqlInjectionScreen(
    onBack: () -> Unit,
    search: (String) -> List<Map<String, String>>
) {
    var input by remember { mutableStateOf("john") }
    var lastQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }

    // Run initial benign search
    LaunchedEffect(Unit) {
        try { results = search("john"); lastQuery = "SELECT * FROM users WHERE username LIKE '%john%'" }
        catch (e: Exception) { errorMsg = e.message ?: "Error" }
    }

    VulnScreen(
        title = "SQL Injection", owaspTag = "M4 — OWASP Mobile Top 10",
        owaspColor = AccentRed, onBack = onBack
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {

            // Attack payloads hint
            DangerBox(
                title = "💉  Attack Payloads to Try",
                body = "Dump all users:\n  ' OR '1'='1\n\n" +
                        "Auth bypass (in Login screen):\n  admin' --\n\n" +
                        "UNION dump:\n  ' UNION SELECT * FROM notes --"
            )

            SectionLabel("User Search (SQL Injectable)")
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text("Search username…") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentRed, unfocusedBorderColor = NavyBorder,
                    focusedLabelColor = AccentRed, unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            VulnButton(text = "Search (Vulnerable)", color = AccentRed, onClick = {
                errorMsg = ""
                lastQuery = "SELECT * FROM users WHERE username LIKE '%$input%'"
                try {
                    results = search(input)
                } catch (e: Exception) {
                    errorMsg = "SQL ERROR (schema revealed): ${e.message}"
                    results = emptyList()
                }
            })

            SectionLabel("Generated SQL Query")
            CodeCard(
                text = if (errorMsg.isNotEmpty()) errorMsg else lastQuery,
                textColor = if (errorMsg.isNotEmpty()) AccentRed else AccentAmber
            )

            SectionLabel("Results — ${results.size} row(s) returned")
            results.forEachIndexed { i, row ->
                val isSensitive =
                    row["is_admin"] == "1" || !row["credit_card_last4"].isNullOrEmpty()
                ResultRow(
                    title = "Row ${i + 1}: ${row["username"] ?: "?"}",
                    content = row.entries.joinToString("\n") { (k, v) ->
                        val flag = if (k == "password" || k == "credit_card_last4") "🔴 " else "   "
                        "$flag$k = $v"
                    },
                    highlight = isSensitive
                )
                Spacer(Modifier.height(6.dp))
            }

            AdbHint("adb shell content query --uri content://com.vulndroid.provider/users")
            Spacer(Modifier.height(32.dp))
        }
    }
}
