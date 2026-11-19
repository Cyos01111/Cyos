package com.cyosplay.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cyosplay.player.R
import com.cyosplay.player.data.model.AppFontMode

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val RobotoNumbersFontFamily = FontFamily(
    Font(R.font.roboto_medium_numbers, FontWeight.Medium),
    Font(R.font.roboto_medium_numbers, FontWeight.Bold)
)

val PoppinsFontFamily = FontFamily(
    Font(R.font.poppins, FontWeight.Normal),
    Font(R.font.poppins, FontWeight.Medium),
    Font(R.font.poppins, FontWeight.SemiBold),
    Font(R.font.poppins, FontWeight.Bold)
)

val MontserratFontFamily = FontFamily(
    Font(R.font.montserrat, FontWeight.Normal),
    Font(R.font.montserrat, FontWeight.Medium),
    Font(R.font.montserrat, FontWeight.SemiBold),
    Font(R.font.montserrat, FontWeight.Bold)
)

val LatoFontFamily = FontFamily(
    Font(R.font.lato, FontWeight.Normal),
    Font(R.font.lato, FontWeight.Medium),
    Font(R.font.lato, FontWeight.SemiBold),
    Font(R.font.lato, FontWeight.Bold)
)

val NunitoFontFamily = FontFamily(
    Font(R.font.nunito, FontWeight.Normal),
    Font(R.font.nunito, FontWeight.Medium),
    Font(R.font.nunito, FontWeight.SemiBold),
    Font(R.font.nunito, FontWeight.Bold)
)

val FiraCodeFontFamily = FontFamily(
    Font(R.font.fira_code, FontWeight.Normal),
    Font(R.font.fira_code, FontWeight.Medium),
    Font(R.font.fira_code, FontWeight.SemiBold),
    Font(R.font.fira_code, FontWeight.Bold)
)

fun buildCyosTypography(fontMode: AppFontMode = AppFontMode.SYSTEM_DEFAULT, scaleMultiplier: Float = 1.0f): Typography {
    val bodyFamily = when (fontMode) {
        AppFontMode.CINEMA_SANS -> PlusJakartaFontFamily
        AppFontMode.MODERN_INTER -> InterFontFamily
        AppFontMode.TECHNICAL_MONO -> RobotoNumbersFontFamily
        AppFontMode.GEOMETRIC_POPPINS -> PoppinsFontFamily
        AppFontMode.CINEMA_MONTSERRAT -> MontserratFontFamily
        AppFontMode.HUMANIST_LATO -> LatoFontFamily
        AppFontMode.ROUNDED_NUNITO -> NunitoFontFamily
        AppFontMode.DEVELOPER_FIRA_CODE -> FiraCodeFontFamily
        AppFontMode.SYSTEM_DEFAULT -> FontFamily.Default
    }
    val smallLabelFamily = when (fontMode) {
        AppFontMode.SYSTEM_DEFAULT -> FontFamily.Default
        AppFontMode.DEVELOPER_FIRA_CODE -> FiraCodeFontFamily
        else -> RobotoNumbersFontFamily
    }
    val s = scaleMultiplier.coerceIn(0.80f, 1.35f)
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (28 * s).sp,
            lineHeight = (34 * s).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (22 * s).sp,
            lineHeight = (28 * s).sp
        ),
        titleLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (18 * s).sp,
            lineHeight = (24 * s).sp
        ),
        titleMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15 * s).sp,
            lineHeight = (21 * s).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (15 * s).sp,
            lineHeight = (22 * s).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (13 * s).sp,
            lineHeight = (19 * s).sp
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13 * s).sp,
            lineHeight = (18 * s).sp
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * s).sp,
            lineHeight = (15 * s).sp
        ),
        labelSmall = TextStyle(
            fontFamily = smallLabelFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (10 * s).sp,
            lineHeight = (14 * s).sp
        )
    )
}
