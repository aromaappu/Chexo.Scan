package com.chexo.scan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.chexo.scan.SettingsData

@Immutable
data class AppColors(
    val primary: Color = AppPrimary,
    val primaryDark: Color = AppPrimaryDark,
    val background: Color = AppBackground,
    val surface: Color = AppSurface,
    val surfaceVariant: Color = AppSurfaceVariant,
    val onPrimary: Color = AppOnPrimary,
    val onBackground: Color = AppOnBackground,
    val onSurfaceSecondary: Color = AppOnSurfaceSecondary,
    val gray: Color = AppGray,
    val error: Color = AppError,
    val link: Color = AppLink,
    val warning: Color = AppWarning,
    val warningText: Color = AppWarningText,
    val isDark: Boolean = false
)

val LightAppColors = AppColors()
val DarkAppColors = AppColors(
    primary = AppPrimary,
    primaryDark = AppPrimaryDark,
    background = AppBackgroundDark,
    surface = AppSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark,
    onPrimary = AppOnPrimaryDark,
    onBackground = AppOnBackgroundDark,
    onSurfaceSecondary = AppOnSurfaceSecondaryDark,
    gray = AppGrayDark,
    error = AppErrorDark,
    link = AppLinkDark,
    warning = AppWarningDark,
    warningText = AppWarningTextDark,
    isDark = true
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

private val LightColorScheme = lightColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    background = AppBackground,
    surface = AppSurface,
    onBackground = AppOnBackground,
    error = AppError,
    secondary = AppPrimaryDark,
    tertiary = AppLink
)

private val DarkColorScheme = darkColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    background = AppBackgroundDark,
    surface = AppSurfaceDark,
    onBackground = AppOnBackgroundDark,
    error = AppErrorDark,
    secondary = AppPrimaryDarkDark,
    tertiary = AppLinkDark
)

var themeChangeCounter = mutableStateOf(0)

@Composable
fun ChexoScaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val counter = themeChangeCounter.value

    val savedTheme = SettingsData.getTheme(context)

    val isDark = when (savedTheme) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val appColors = if (isDark) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
