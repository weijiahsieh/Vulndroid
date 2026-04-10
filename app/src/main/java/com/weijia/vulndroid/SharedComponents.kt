package com.weijia.vulndroid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weijia.vulndroid.ui.theme.AccentAmber
import com.weijia.vulndroid.ui.theme.AccentBlue
import com.weijia.vulndroid.ui.theme.AccentGreen
import com.weijia.vulndroid.ui.theme.AccentRed
import com.weijia.vulndroid.ui.theme.NavyBorder
import com.weijia.vulndroid.ui.theme.NavyDark
import com.weijia.vulndroid.ui.theme.NavySurface
import com.weijia.vulndroid.ui.theme.TextMuted
import com.weijia.vulndroid.ui.theme.TextPrimary
import com.weijia.vulndroid.ui.theme.TextSecond

// ── Screen scaffold with back-arrow toolbar ───────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VulnScreen(
    title: String,
    owaspTag: String,
    owaspColor: Color = AccentAmber,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(owaspTag, color = owaspColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecond)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1220))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            content = content
        )
    }
}

// ── Section label ─────────────────────────────────────────────────────────────
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp)
    )
}

// ── Dark monospace code card ──────────────────────────────────────────────────
@Composable
fun CodeCard(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = TextSecond,
    bgColor: Color = NavySurface
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, NavyBorder, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 17.sp
        )
    }
}

// ── Danger / warning info box ─────────────────────────────────────────────────
@Composable
fun DangerBox(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    titleColor: Color = AccentRed,
    bgColor: Color = Color(0xFF1A0A0A),
    borderColor: Color = Color(0xFF3F1010)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text(title, color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = TextSecond, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

// ── Success / secure tip box ──────────────────────────────────────────────────
@Composable
fun SecureBox(
    body: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0D2818), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF134D32), RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text("✅  Secure Implementation", color = AccentGreen,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Color(0xFF6EE7B7), fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
    }
}

// ── adb command hint ──────────────────────────────────────────────────────────
@Composable
fun AdbHint(command: String, modifier: Modifier = Modifier) {
    Text(
        text = "$ $command",
        color = AccentBlue,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier.padding(top = 4.dp, bottom = 16.dp)
    )
}

// ── Vuln tag chip ─────────────────────────────────────────────────────────────
@Composable
fun VulnTag(label: String, color: Color = AccentRed) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

// ── Full-width primary button ─────────────────────────────────────────────────
@Composable
fun VulnButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AccentRed,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ── Result row card ───────────────────────────────────────────────────────────
@Composable
fun ResultRow(
    title: String,
    content: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (highlight) Color(0xFF1A0A0A) else NavySurface,
                RoundedCornerShape(6.dp)
            )
            .border(1.dp, if (highlight) AccentRed.copy(0.3f) else NavyBorder, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Text(title, color = if (highlight) AccentRed else TextPrimary,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(content, color = TextSecond, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
    }
}
