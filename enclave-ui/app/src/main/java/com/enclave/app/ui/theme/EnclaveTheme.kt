package com.enclave.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ─── Enclave Core Palette ───────────────────────────────────────────────
val RoseAccent = Color(0xFFE598A7)
val RoseDeep = Color(0xFFD4607A)
val CharcoalText = Color(0xFF2A1B1D)

// Legacy constants for backward compatibility
val BlushBackground = Color(0xFFFFF5F6)
val BlushSent = Color(0xFFFCE2E6)
val BlushReceived = Color(0xFFE8D4D6)
val LilacBubble = Color(0xFFF5EAFF)
val LilacBubbleEnd = Color(0xFFECD5FF)

val LightColors = lightColorScheme(
    primary = RoseAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE2E6),
    onPrimaryContainer = RoseDeep,
    secondary = RoseDeep,
    onSecondary = Color.White,
    background = Color(0xFFFFF5F6),
    onBackground = CharcoalText,
    surface = Color.White,
    onSurface = CharcoalText,
    surfaceVariant = Color(0xFFFFF0F2),
    onSurfaceVariant = CharcoalText
)

val DarkColors = LightColors // Force light mode always

// ─── Enclave Typography Families ──────────────────────────────────────────────
val PlayfairFont = FontFamily.Serif
val InterFont = FontFamily.SansSerif

val FluidShapes = Shapes(
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(36.dp)
)

@Composable
fun EnclaveTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Force light signature colors
    val colorScheme = LightColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = FluidShapes,
        content = content
    )
}
