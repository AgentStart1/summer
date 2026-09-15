package com.storytellerf.summet.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = SummerGreenDark,
  onPrimary = Color(0xFF003826),
  primaryContainer = SummerGreenContainerDark,
  onPrimaryContainer = Color(0xFF8FF8C9),
  secondary = SummerMintDark,
  tertiary = SummerBlueDark,
  background = SummerBackgroundDark,
  onBackground = Color(0xFFE0E4E1),
  surface = SummerBackgroundDark,
  onSurface = Color(0xFFE0E4E1),
  surfaceVariant = Color(0xFF3F4943),
  onSurfaceVariant = Color(0xFFBFC9C1),
  surfaceDim = Color(0xFF101512),
  surfaceBright = Color(0xFF363B38),
  surfaceContainerLowest = Color(0xFF0B100D),
  surfaceContainerLow = Color(0xFF191D1A),
  surfaceContainer = Color(0xFF1D211E),
  surfaceContainerHigh = Color(0xFF272B28),
  surfaceContainerHighest = Color(0xFF323633),
  outline = Color(0xFF89938C),
)

private val LightColorScheme = lightColorScheme(
  primary = SummerGreen,
  onPrimary = Color.White,
  primaryContainer = SummerGreenContainer,
  onPrimaryContainer = Color(0xFF002116),
  secondary = Color(0xFF4D6358),
  onSecondary = Color.White,
  secondaryContainer = SummerMint,
  onSecondaryContainer = Color(0xFF092017),
  tertiary = SummerBlue,
  background = SummerBackground,
  onBackground = Color(0xFF191C1A),
  surface = SummerBackground,
  onSurface = Color(0xFF191C1A),
  surfaceVariant = Color(0xFFDDE5DF),
  onSurfaceVariant = Color(0xFF414944),
  surfaceDim = Color(0xFFD8DBD8),
  surfaceBright = Color(0xFFF7FAF7),
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFF1F4F1),
  surfaceContainer = Color(0xFFEBEFEC),
  surfaceContainerHigh = Color(0xFFE5E9E6),
  surfaceContainerHighest = Color(0xFFDFE3E0),
  outline = Color(0xFF717972),
)

@Composable
fun SummerAppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
