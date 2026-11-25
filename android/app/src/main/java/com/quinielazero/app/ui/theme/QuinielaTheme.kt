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

val ObsidianBg = Color(0xFF04100B)
val ObsidianSurface = Color(0xFF0B1F16)
val ObsidianSurfaceAlt = Color(0xFF133124)
val ObsidianBorder = Color(0xFF286146)

val GoldBright = Color(0xFFFFD700)
val GoldLightSpecular = Color(0xFFFFF4B8)
val GoldRichDeep = Color(0xFFD48800)
val AmberWarm = Color(0xFFFFA500)
val EmeraldNeon = Color(0xFF14EB8E)
val EmeraldDeep = Color(0xFF0B462E)
val HotCoral = Color(0xFFFF6B3D)
val CyanNeon = Color(0xFF14EB8E)
val VioletNeon = Color(0xFF8A2BE2)

val IvoryBg = Color(0xFFF1F7F4)
val IvorySurface = Color(0xFFFFFFFF)
val IvoryBorder = Color(0xFFB7D2C3)
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
    secondary = EmeraldNeon,
    onSecondary = Color(0xFF001F12),
    tertiary = AmberWarm,
    background = ObsidianBg,
    onBackground = Color(0xFFFFFFFF),
    surface = ObsidianSurface,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = ObsidianSurfaceAlt,
    onSurfaceVariant = Color(0xFFCDE2D6),
    outline = ObsidianBorder
)

private val LightColors = lightColorScheme(
    primary = GoldDeepLight,
    onPrimary = Color.White,
    secondary = Color(0xFF0A6E44),
    onSecondary = Color.White,
    tertiary = Color(0xFF0F766E),
    background = IvoryBg,
    onBackground = Color(0xFF0D1D15),
    surface = IvorySurface,
    onSurface = Color(0xFF0D1D15),
    surfaceVariant = Color(0xFFE0EDE6),
    onSurfaceVariant = Color(0xFF395246),
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
