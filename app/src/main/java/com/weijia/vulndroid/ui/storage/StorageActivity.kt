package com.weijia.vulndroid.ui.storage

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.weijia.vulndroid.data.local.VulnDroidDatabase
import com.weijia.vulndroid.ui.theme.AccentAmber
import com.weijia.vulndroid.ui.theme.AccentRed
import com.weijia.vulndroid.ui.theme.AdbHint
import com.weijia.vulndroid.ui.theme.CodeCard
import com.weijia.vulndroid.ui.theme.NavyBorder
import com.weijia.vulndroid.ui.theme.NavySurface
import com.weijia.vulndroid.ui.theme.ResultRow
import com.weijia.vulndroid.ui.theme.SectionLabel
import com.weijia.vulndroid.ui.theme.TextMuted
import com.weijia.vulndroid.ui.theme.TextPrimary
import com.weijia.vulndroid.ui.theme.VulnButton
import com.weijia.vulndroid.ui.theme.VulnDroidTheme
import com.weijia.vulndroid.ui.theme.VulnScreen
import java.io.File

/**
 * StorageActivity — Jetpack Compose
 * ====================================
 * M9 Displays live SharedPreferences content including the plaintext password
 * M4 Notes saved via raw string SQL — injection possible in note content
 * M9 External storage log reader — shows credentials written to /sdcard/
 * M6 All data also logged to Logcat
 */
class StorageActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var db: VulnDroidDatabase
    private val TAG = "VulnDroid_Storage"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        db = VulnDroidDatabase(this)

        setContent {
            VulnDroidTheme {
                StorageScreen(
                    prefsData = readPrefs(),
                    loadNotes = { loadNotes() },
                    saveNote = { note -> saveNote(note) },
                    externalLog = readExternalLog()
                )
            }
        }
    }

    private fun readPrefs(): String {
        val all = prefs.all
        if (all.isEmpty()) return "(No session data — log in first)"
        return all.entries.joinToString("\n") { (k, v) ->
            val flag = if (k.contains("password", true)) "🔴 " else "   "
            "$flag$k = $v"
        }.also { Log.d(TAG, "Prefs dump:\n$it") }
    }

    private fun loadNotes(): List<String> {
        val username = prefs.getString("username", "unknown") ?: "unknown"
        return db.getNotesForUser(username)
    }

    private fun saveNote(note: String) {
        val username = prefs.getString("username", "unknown") ?: "unknown"
        // [M4] VULNERABILITY: raw SQL insert — try note: '); DROP TABLE notes; --
        val sql = "INSERT INTO notes (owner_username, content) VALUES ('$username', '$note')"
        Log.d(TAG, "Note insert SQL: $sql")
        db.writableDatabase.execSQL(sql)
    }

    private fun readExternalLog(): String {
        val f = File(Environment.getExternalStorageDirectory(), "vulndroid_debug.log")
        return if (f.exists()) f.readText().ifEmpty { "(Log file empty — log in first)" }
        else "(Log file not yet created)\nPath: ${f.absolutePath}\n\nLog in to generate it."
    }
}

@Composable
fun StorageScreen(
    prefsData: String,
    loadNotes: () -> List<String>,
    saveNote: (String) -> Unit,
    externalLog: String
) {
    val context = LocalContext.current
    var noteInput by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(loadNotes()) }

    VulnScreen(
        title = "Insecure Storage",
        owaspTag = "M9 — OWASP Mobile Top 10",
        onBack = { (context as? ComponentActivity)?.finish() }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {

            // SharedPreferences section
            SectionLabel("SharedPreferences Contents")
            CodeCard(text = prefsData, textColor = AccentAmber)
            AdbHint("adb shell run-as com.vulndroid cat shared_prefs/user_session.xml")

            // SQLite notes section
            SectionLabel("Unencrypted Notes Database (M9 + M4)")
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("Enter note (try: '); DROP TABLE notes; --") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentAmber, unfocusedBorderColor = NavyBorder,
                    focusedLabelColor = AccentAmber, unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            VulnButton(text = "Save Note (Insecurely)", color = AccentAmber, onClick = {
                if (noteInput.isNotEmpty()) {
                    saveNote(noteInput)
                    notes = loadNotes()
                    noteInput = ""
                    Toast.makeText(context, "Saved unencrypted to SQLite", Toast.LENGTH_SHORT)
                        .show()
                }
            })
            Spacer(Modifier.height(10.dp))
            notes.forEachIndexed { i, note ->
                ResultRow(title = "Note #${i + 1} — plaintext in vulndroid.db", content = note)
                Spacer(Modifier.height(6.dp))
            }
            AdbHint("adb shell run-as com.vulndroid sqlite3 databases/vulndroid.db 'SELECT * FROM notes'")

            // External log section
            SectionLabel("External Storage Log (M9 + M6)")
            CodeCard(
                text = externalLog, textColor = AccentRed.copy(alpha = 0.9f),
                bgColor = NavySurface.copy(alpha = 0.7f)
            )
            AdbHint("adb shell cat /sdcard/vulndroid_debug.log")

            Spacer(Modifier.height(32.dp))
        }
    }
}
