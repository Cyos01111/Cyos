package com.cyosplay.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cyosplay.player.data.model.AdaptiveTextScaleMode
import com.cyosplay.player.data.model.AppFontMode
import com.cyosplay.player.data.model.AppThemeMode

private val AmoledDarkScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF003B47),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = NeonViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2B1C58),
    onSecondaryContainer = Color(0xFFE5DEFF),
    tertiary = EmeraldVault,
    background = ObsidianBlack,
    onBackground = Color(0xFFF2F5FA),
    surface = CinemaSurfaceDark,
    onSurface = Color(0xFFF2F5FA),
    surfaceVariant = CinemaCardDark,
    onSurfaceVariant = Color(0xFFB4BFD3),
    error = CrimsonAlert
)

private val PureAmoledPitchBlackScheme = AmoledDarkScheme.copy(
    background = Color.Black,
    surface = Color(0xFF05070B),
    surfaceVariant = Color(0xFF0E121B)
)

private val DaylightLightScheme = lightColorScheme(
    primary = DaylightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7EFFF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = DaylightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4DFFF),
    onSecondaryContainer = Color(0xFF190866),
    tertiary = Color(0xFF007B43),
    background = DaylightBackground,
    onBackground = Color(0xFF141821),
    surface = DaylightSurface,
    onSurface = Color(0xFF141821),
    surfaceVariant = DaylightCard,
    onSurfaceVariant = Color(0xFF475267),
    error = Color(0xFFD32F2F)
)

private val CyberNeonScheme = AmoledDarkScheme.copy(
    primary = CyberNeonPrimary,
    secondary = CyberNeonSecondary,
    background = Color(0xFF040914),
    surface = CyberNeonSurface,
    surfaceVariant = CyberNeonCard
)

private val RoyalAmethystScheme = AmoledDarkScheme.copy(
    primary = RoyalAmethystPrimary,
    secondary = RoyalAmethystSecondary,
    background = Color(0xFF0A0614),
    surface = RoyalAmethystSurface,
    surfaceVariant = RoyalAmethystCard
)

private val EmeraldMatrixScheme = AmoledDarkScheme.copy(
    primary = EmeraldMatrixPrimary,
    secondary = EmeraldMatrixSecondary,
    background = Color(0xFF030B07),
    surface = EmeraldMatrixSurface,
    surfaceVariant = EmeraldMatrixCard
)

private val CrimsonVelvetScheme = AmoledDarkScheme.copy(
    primary = CrimsonVelvetPrimary,
    secondary = CrimsonVelvetSecondary,
    background = Color(0xFF0E0507),
    surface = CrimsonVelvetSurface,
    surfaceVariant = CrimsonVelvetCard
)

private val TitaniumSlateScheme = AmoledDarkScheme.copy(
    primary = TitaniumSlatePrimary,
    secondary = TitaniumSlateSecondary,
    background = Color(0xFF0B0E13),
    surface = TitaniumSlateSurface,
    surfaceVariant = TitaniumSlateCard
)

private val SolarGoldScheme = AmoledDarkScheme.copy(
    primary = SolarGoldPrimary,
    secondary = SolarGoldSecondary,
    background = Color(0xFF0E0B04),
    surface = SolarGoldSurface,
    surfaceVariant = SolarGoldCard
)

@Composable
fun CyosPlayTheme(
    themeMode: AppThemeMode = AppThemeMode.AMOLED_BLACK,
    fontMode: AppFontMode = AppFontMode.SYSTEM_DEFAULT,
    textScaleMode: AdaptiveTextScaleMode = AdaptiveTextScaleMode.COMPACT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        AppThemeMode.DARK_CINEMA -> AmoledDarkScheme
        AppThemeMode.AMOLED_BLACK -> PureAmoledPitchBlackScheme
        AppThemeMode.LIGHT_DAYLIGHT -> DaylightLightScheme
        AppThemeMode.SYSTEM_AUTO -> if (systemDark) AmoledDarkScheme else DaylightLightScheme
        AppThemeMode.CYBER_NEON_BLUE -> CyberNeonScheme
        AppThemeMode.ROYAL_AMETHYST -> RoyalAmethystScheme
        AppThemeMode.EMERALD_MATRIX -> EmeraldMatrixScheme
        AppThemeMode.CRIMSON_VELVET -> CrimsonVelvetScheme
        AppThemeMode.TITANIUM_SLATE -> TitaniumSlateScheme
        AppThemeMode.SOLAR_GOLD -> SolarGoldScheme
    }
    val typography = buildCyosTypography(fontMode, textScaleMode.scaleMultiplier)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
