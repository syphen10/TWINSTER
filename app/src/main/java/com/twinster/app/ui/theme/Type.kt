@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.twinster.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.twinster.app.R

/**
 * Cabinet Grotesk (display/heading) + Public Sans (body/label) — both bundled as static variable-
 * font .ttf resources under res/font/ (no Downloadable Fonts API dependency on Play Services).
 * Weights are pulled via FontVariation on the single variable font file rather than shipping a
 * separate static file per weight. Cabinet Grotesk has a distinctive high-waisted, slightly
 * quirky grotesk character that reads as a deliberate pick rather than the ubiquitous Space
 * Grotesk/Inter pairing; Public Sans (also OFL, from the US Web Design System) is a plainer,
 * more neutral humanist sans than Inter for body copy so it doesn't compete with the display face.
 */
val CabinetGroteskFamily = FontFamily(
    Font(R.font.cabinet_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.cabinet_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.cabinet_grotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.cabinet_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(800)))
)

val PublicSansFamily = FontFamily(
    Font(R.font.public_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.public_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.public_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.public_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/**
 * Full Material3 type scale for Twinster. Display/headline/title tiers use Cabinet Grotesk for a
 * distinctive, energetic feel (archetype names, big numbers, screen headers); body/label tiers use
 * Public Sans, weighted up slightly from Material defaults so copy stays legible over the animated,
 * glowing gradient background instead of reading as thin/washed out.
 */
val TwinsterTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = CabinetGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = CabinetGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.3).sp
    ),
    displaySmall = TextStyle(
        fontFamily = CabinetGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = CabinetGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = CabinetGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = CabinetGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = CabinetGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = CabinetGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontFamily = CabinetGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PublicSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PublicSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PublicSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PublicSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PublicSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PublicSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    )
)
