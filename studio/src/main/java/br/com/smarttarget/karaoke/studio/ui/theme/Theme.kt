package br.com.smarttarget.karaoke.studio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = CrimsonPrimaryDark,
  onPrimary = CrimsonOnPrimaryDark,
  primaryContainer = CrimsonPrimaryContainerDark,
  onPrimaryContainer = CrimsonOnPrimaryContainerDark,
  secondary = AmberSecondaryDark,
  onSecondary = AmberOnSecondaryDark,
  secondaryContainer = AmberSecondaryContainerDark,
  onSecondaryContainer = AmberOnSecondaryContainerDark,
  background = DarkBackground,
  onBackground = DarkOnBackground,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
  primary = CrimsonPrimaryLight,
  onPrimary = CrimsonOnPrimaryLight,
  primaryContainer = CrimsonPrimaryContainerLight,
  onPrimaryContainer = CrimsonOnPrimaryContainerLight,
  secondary = AmberSecondaryLight,
  onSecondary = AmberOnSecondaryLight,
  secondaryContainer = AmberSecondaryContainerLight,
  onSecondaryContainer = AmberOnSecondaryContainerLight,
  background = LightBackground,
  onBackground = LightOnBackground,
  surface = LightSurface,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = LightOnSurfaceVariant
)

@Composable
fun KaraokeStudioTheme(
  themeMode: String = "system", // "system", "dark", "light"
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) {
  val isDark = when (themeMode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
  }

  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    isDark -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
