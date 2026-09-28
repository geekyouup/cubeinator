package com.example.cubeinator.theme

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

private val ConsumerGreenColorScheme =
  lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    secondary = SkyAccent,
    onSecondary = Color.White,
    tertiary = AmberAccent,
    onTertiary = Color.White,
    background = Color(0xFF10B981),
    onBackground = EmeraldDeep,
    surface = MintSurface,
    onSurface = EmeraldDeep,
    surfaceVariant = MintStage,
    onSurfaceVariant = EmeraldDark,
  )

private val DarkGreenColorScheme =
  darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    secondary = SkyAccent,
    onSecondary = Color.White,
    tertiary = AmberAccent,
    onTertiary = Color.White,
    background = Color(0xFF059669),
    onBackground = Color.White,
    surface = MintSurface,
    onSurface = EmeraldDeep,
  )

@Composable
fun CubeinatorTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic wallpaper color override so our pleasant green consumer theme always shines
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> ConsumerGreenColorScheme
      else -> ConsumerGreenColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

