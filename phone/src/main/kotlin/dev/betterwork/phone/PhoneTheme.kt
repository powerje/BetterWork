package dev.betterwork.phone

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.betterwork.data.AppPreferences
import dev.betterwork.data.Appearance

internal val LightColors =
    lightColorScheme(
        primary = Color(0xFF286442),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD4EBD9),
        onPrimaryContainer = Color(0xFF00391D),
        secondary = Color(0xFF505C53),
        secondaryContainer = Color(0xFFE0E7DE),
        onSecondaryContainer = Color(0xFF19211C),
        background = Color(0xFFFAF9F6),
        surface = Color(0xFFFAF9F6),
        onSurface = Color(0xFF19211C),
        onSurfaceVariant = Color(0xFF505C53),
        surfaceContainer = Color(0xFFEFF1EB),
        surfaceContainerLow = Color(0xFFF3F4EF),
        surfaceContainerHigh = Color(0xFFE7EBE3),
        surfaceVariant = Color(0xFFEFF1EB),
        outline = Color(0xFF737E75),
        outlineVariant = Color(0xFFC3CCC2),
        error = Color(0xFFBA1A1A),
    )
internal val DarkColors =
    darkColorScheme(
        primary = Color(0xFF9CD5AC),
        onPrimary = Color(0xFF00391D),
        primaryContainer = Color(0xFF194C30),
        onPrimaryContainer = Color(0xFFD4EBD9),
        secondary = Color(0xFFB8C5BA),
        secondaryContainer = Color(0xFF354439),
        onSecondaryContainer = Color(0xFFE3E9E1),
        background = Color(0xFF121512),
        surface = Color(0xFF121512),
        onSurface = Color(0xFFE3E9E1),
        onSurfaceVariant = Color(0xFFB8C5BA),
        surfaceContainer = Color(0xFF1D241F),
        surfaceContainerLow = Color(0xFF181E19),
        surfaceContainerHigh = Color(0xFF283129),
        surfaceVariant = Color(0xFF1D241F),
        outline = Color(0xFF89958B),
        outlineVariant = Color(0xFF404C43),
        error = Color(0xFFFFB4AB),
    )

internal val CountdownStyle =
    TextStyle(
        fontSize = 64.sp,
        lineHeight = 72.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )

@Composable
internal fun PhoneTheme(preferences: AppPreferences, content: @Composable () -> Unit) {
    val dark =
        when (preferences.appearance) {
            Appearance.SYSTEM -> isSystemInDarkTheme()
            Appearance.LIGHT -> false
            Appearance.DARK -> true
        }
    val context = LocalContext.current
    val colors =
        if (preferences.wallpaperColors && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) DarkColors else LightColors
    SideEffect {
        (context as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography =
            Typography(
                titleLarge =
                    TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
                titleMedium =
                    TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
            ),
        content = content,
    )
}
