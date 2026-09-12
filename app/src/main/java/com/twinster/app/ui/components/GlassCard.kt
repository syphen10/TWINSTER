package com.twinster.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * [Primary] is for the single standout surface on a screen (the archetype reveal) — a larger
 * radius, stronger glow and brighter frost so it reads as the headline card. [Standard] is the
 * default for ordinary content cards. [Utility] is for lower-emphasis, denser surfaces (feedback
 * reports, list-style content) — a tighter radius and flatter, less glowing frost so not every
 * card on screen reads as an identical rectangle.
 */
enum class GlassCardVariant { Primary, Standard, Utility }

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White,
    variant: GlassCardVariant = GlassCardVariant.Standard,
    content: @Composable ColumnScope.() -> Unit
) {
    val cornerRadius = when (variant) {
        GlassCardVariant.Primary -> 34.dp
        GlassCardVariant.Standard -> 26.dp
        GlassCardVariant.Utility -> 18.dp
    }
    val elevation = when (variant) {
        GlassCardVariant.Primary -> 32.dp
        GlassCardVariant.Standard -> 22.dp
        GlassCardVariant.Utility -> 12.dp
    }
    val topAlpha = when (variant) {
        GlassCardVariant.Primary -> 0.14f
        GlassCardVariant.Standard -> 0.10f
        GlassCardVariant.Utility -> 0.07f
    }
    val bottomAlpha = when (variant) {
        GlassCardVariant.Primary -> 0.05f
        GlassCardVariant.Standard -> 0.04f
        GlassCardVariant.Utility -> 0.03f
    }
    val borderAlpha = when (variant) {
        GlassCardVariant.Primary -> 0.26f
        GlassCardVariant.Standard -> 0.18f
        GlassCardVariant.Utility -> 0.12f
    }
    val shape = RoundedCornerShape(cornerRadius)

    Column(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, ambientColor = accentColor.copy(alpha = 0.35f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = topAlpha), Color.White.copy(alpha = bottomAlpha))
                )
            )
            .border(1.dp, Color.White.copy(alpha = borderAlpha), shape)
            .fillMaxSize()
            .padding(PaddingValues(24.dp)),
        content = content
    )
}
