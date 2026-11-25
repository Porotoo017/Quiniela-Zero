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
val ObsidianSurface = Color(0xFF131520)
val ObsidianSurfaceAlt = Color(0xFF1B1F30)
val ObsidianBorder = Color(0xFF2B314A)

val GoldBright = Color(0xFFFFD700)
val AmberWarm = Color(0xFFFFA500)
val CyanNeon = Color(0xFF00E5FF)
val VioletNeon = Color(0xFF8A2BE2)

val IvoryBg = Color(0xFFF7F6F2)
val IvorySurface = Color(0xFFFFFFFF)
val IvoryBorder = Color(0xFFE2DED2)
val GoldDeepLight = Color(0xFF9C6900)

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
    onBackground = Color(0xFFF4F6FB),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF4F6FB),
    surfaceVariant = ObsidianSurfaceAlt,
    onSurfaceVariant = Color(0xFF9BA3B8),
    outline = ObsidianBorder
)

private val LightColors = lightColorScheme(
    primary = GoldDeepLight,
    onPrimary = Color.White,
    secondary = Color(0xFF007A8C),
    onSecondary = Color.White,
    tertiary = Color(0xFF6A1B9A),
    background = IvoryBg,
    onBackground = Color(0xFF161822),
    surface = IvorySurface,
    onSurface = Color(0xFF161822),
    surfaceVariant = Color(0xFFECE9DF),
    onSurfaceVariant = Color(0xFF555B6E),
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
