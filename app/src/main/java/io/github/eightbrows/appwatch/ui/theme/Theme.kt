package io.github.eightbrows.appwatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Material 3 の色の役割をすべてアプリの色（画面サンプルの背景・青）で埋める。
// 埋めないと、ダイアログ（surfaceContainerHigh）・メニュー・お知らせ（inverseSurface）などが
// Material 3 の標準の紫がかった色になるため。
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F8),
    onPrimaryContainer = Color(0xFF0B2A5E),
    inversePrimary = DarkPrimary,
    secondary = LightPrimary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EAF4),
    onSecondaryContainer = Color(0xFF16305C),
    tertiary = LightPrimary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE6F8),
    onTertiaryContainer = Color(0xFF0B2A5E),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightOutlineVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    inverseSurface = Color(0xFF2B2A28),
    inverseOnSurface = Color(0xFFF2F0EB),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color.Black,
    surfaceBright = LightSurface,
    surfaceDim = Color(0xFFE6E3DD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF9F6),
    surfaceContainer = Color(0xFFF7F6F2),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEFEDE8),
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF0B1F44),
    primaryContainer = Color(0xFF24395E),
    onPrimaryContainer = Color(0xFFDCE6F8),
    inversePrimary = LightPrimary,
    secondary = DarkPrimary,
    onSecondary = Color(0xFF0B1F44),
    secondaryContainer = Color(0xFF2A3446),
    onSecondaryContainer = Color(0xFFDCE6F8),
    tertiary = DarkPrimary,
    onTertiary = Color(0xFF0B1F44),
    tertiaryContainer = Color(0xFF24395E),
    onTertiaryContainer = Color(0xFFDCE6F8),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkOutlineVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = Color(0xFFEDEAE5),
    inverseOnSurface = Color(0xFF1C1B1A),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color.Black,
    surfaceBright = Color(0xFF3A3836),
    surfaceDim = DarkBackground,
    surfaceContainerLowest = Color(0xFF0F0F0F),
    surfaceContainerLow = Color(0xFF1C1B1A),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = Color(0xFF2C2A28),
    surfaceContainerHighest = Color(0xFF363432),
)

private val LocalAppWatchColors = staticCompositionLocalOf { LightAppWatchColors }

/**
 * アプリ全体のテーマ。画面サンプルの見た目にそろえるため、端末の壁紙に合わせた色（ダイナミックカラー）は使わない。
 */
@Composable
fun AppWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppWatchColors provides if (darkTheme) DarkAppWatchColors else LightAppWatchColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** このアプリ用の色の入口。`AppWatchTheme.colors.danger` のように使う。 */
object AppWatchTheme {
    val colors: AppWatchColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppWatchColors.current
}
