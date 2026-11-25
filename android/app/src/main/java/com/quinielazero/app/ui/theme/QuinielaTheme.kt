package com.quinielazero.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quinielazero.app.R
import com.quinielazero.app.core.model.ThemeMode

val ObsidianBg = Color(0xFF0A0A0F)
val ObsidianSurface = Color(0xFF151826)
val ObsidianSurfaceAlt = Color(0xFF1E2336)
val ObsidianBorder = Color(0xFF333B59)

val GoldBright = Color(0xFFFFD700)
val AmberWarm = Color(0xFFFFA500)
val HotCoral = Color(0xFFFF6B3D)
val CyanNeon = Color(0xFF00E5FF)
val VioletNeon = Color(0xFF8A2BE2)

val IvoryBg = Color(0xFFF7F6F2)
val IvorySurface = Color(0xFFFFFFFF)
val IvoryBorder = Color(0xFFD6D0C0)
val GoldDeepLight = Color(0xFF8F5F00)

val VerifiedGreen = Color(0xFF22C55E)
val PendingAmber = Color(0xFFF59E0B)
val AlertRed = Color(0xFFEF4444)

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

private val DarkColors = darkColorScheme(
    primary = GoldBright,
    onPrimary = Color(0xFF141100),
    secondary = CyanNeon,
    onSecondary = Color(0xFF001A1F),
    tertiary = VioletNeon,
    background = ObsidianBg,
    onBackground = Color(0xFFFFFFFF),
    surface = ObsidianSurface,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = ObsidianSurfaceAlt,
    onSurfaceVariant = Color(0xFFC8D0E4),
    outline = ObsidianBorder
)

private val LightColors = lightColorScheme(
    primary = GoldDeepLight,
    onPrimary = Color.White,
    secondary = Color(0xFF006B7A),
    onSecondary = Color.White,
    tertiary = Color(0xFF6A1B9A),
    background = IvoryBg,
    onBackground = Color(0xFF11131A),
    surface = IvorySurface,
    onSurface = Color(0xFF11131A),
    surfaceVariant = Color(0xFFEBE6D8),
    onSurfaceVariant = Color(0xFF454B5E),
    outline = IvoryBorder
)

val QuinielaTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

val NumericDisplayStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    fontFeatureSettings = "tnum"
)

val NumericCellStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    fontFeatureSettings = "tnum"
)

@Composable
fun QuinielaZeroTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = QuinielaTypography,
        content = content
    )
}
