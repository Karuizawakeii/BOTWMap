package com.botwmap.app.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = OnPrimaryGreen,
    primaryContainer = PrimaryContainerGreen,
    onPrimaryContainer = OnPrimaryContainerGreen,
    secondary = SecondaryTeal,
    secondaryContainer = SecondaryContainerTeal,
    onSecondaryContainer = OnSecondaryContainerTeal,
    tertiary = TertiaryAmber,
    tertiaryContainer = TertiaryContainerAmber,
    onTertiaryContainer = OnTertiaryContainerAmber,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimaryGreen,
    onPrimary = DarkOnPrimaryGreen,
    primaryContainer = DarkPrimaryContainerGreen,
    onPrimaryContainer = DarkOnPrimaryContainerGreen,
    secondary = DarkSecondaryTeal,
    secondaryContainer = DarkSecondaryContainerTeal,
    onSecondaryContainer = DarkOnSecondaryContainerTeal,
    tertiary = DarkTertiaryAmber,
    tertiaryContainer = DarkTertiaryContainerAmber,
    onTertiaryContainer = DarkOnTertiaryContainerAmber,
)

@Composable
fun BotwMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 动态色彩：Android 12+ 可用，设置页后续可开关
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BotwTypography,
        content = content,
    )
}
