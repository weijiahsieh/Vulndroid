package com.weijia.vulndroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand colours ─────────────────────────────────────────────────────────────
val NavyDark    = Color(0xFF0F172A)
val NavySurface = Color(0xFF1E293B)
val NavyBorder  = Color(0xFF334155)
val TextPrimary = Color(0xFFE2E8F0)
val TextSecond  = Color(0xFF94A3B8)
val TextMuted   = Color(0xFF64748B)
val AccentBlue  = Color(0xFF0EA5E9)
val AccentGreen = Color(0xFF10B981)
val AccentAmber = Color(0xFFF59E0B)
val AccentRed   = Color(0xFFEF4444)
val AccentOrange= Color(0xFFF97316)

private val VulnDroidColors = darkColorScheme(
    primary          = AccentBlue,
    onPrimary        = Color.White,
    secondary        = Color(0xFF6366F1),
    onSecondary      = Color.White,
    background       = NavyDark,
    onBackground     = TextPrimary,
    surface          = NavySurface,
    onSurface        = TextPrimary,
    surfaceVariant   = Color(0xFF0D1220),
    onSurfaceVariant = TextSecond,
    error            = AccentRed,
    onError          = Color.White,
    outline          = NavyBorder,
)

@Composable
fun VulnDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VulnDroidColors,
        content = content
    )
}
