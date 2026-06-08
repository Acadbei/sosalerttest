package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(primary = PrimaryRed, secondary = SecondaryRed, tertiary = EmergencyRed)

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryRed,
    secondary = SecondaryRed,
    tertiary = EmergencyRed,
    background = SoftGrayBackground,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),  // Deep Slate ink text
    onSurface = Color(0xFF0F172A),     // Deep Slate ink text
    onSurfaceVariant = Color(0xFF475569) // Slate grey text
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  // Enforce consistent highly-polished, high-visibility layout with great contrast
  val colorScheme = LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
