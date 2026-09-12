package com.twinster.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A single breathing glow anchored near the top, plus a dimmer static-position accent for
 * balance — deliberately NOT two same-size blobs orbiting a full circle (the classic "AI mesh
 * gradient" look). Both colors are always a blend of the two brand colors; baseHue only shifts
 * which one leans dominant per user/profile.
 */
@Composable
fun AnimatedMoodBackground(
    baseHue: Float,
    saturation: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 70000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val mixA = ((baseHue % 360f) / 360f).coerceIn(0f, 1f)
    val intensity = saturation.coerceIn(0.3f, 0.6f)
    val glowColor = lerp(TwinsterBlack, lerp(TwinsterGreen, TwinsterPink, mixA), intensity)
    val accentColor = lerp(TwinsterBlack, lerp(TwinsterGreen, TwinsterPink, 1f - mixA), intensity * 0.75f)
    val grainShader = rememberGrainTileShader()

    Canvas(modifier = modifier.fillMaxSize().background(TwinsterBlack)) {
        drawRect(color = TwinsterBlack)

        // Primary glow drifts gently within a small radius near the top — a light source that
        // breathes, not a shape sweeping the whole screen.
        val driftRadians = Math.toRadians(drift.toDouble())
        val driftRadius = size.minDimension * 0.05f
        val anchor = Offset(
            x = size.width * 0.5f + (driftRadius * cos(driftRadians)).toFloat(),
            y = size.height * 0.3f + (driftRadius * sin(driftRadians)).toFloat()
        )
        val glowRadius = size.maxDimension * 0.62f * breathe
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to glowColor,
                    0.35f to glowColor.copy(alpha = 0.45f),
                    0.7f to glowColor.copy(alpha = 0.12f),
                    1f to glowColor.copy(alpha = 0f)
                ),
                center = anchor,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = anchor
        )

        // A dimmer, fixed-position accent low in the opposite corner for depth/balance — no
        // motion beyond the shared breathing pulse.
        val accentAnchor = Offset(size.width * 0.12f, size.height * 0.88f)
        val accentRadius = size.maxDimension * 0.5f * breathe
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to accentColor,
                    0.5f to accentColor.copy(alpha = 0.28f),
                    1f to accentColor.copy(alpha = 0f)
                ),
                center = accentAnchor,
                radius = accentRadius
            ),
            radius = accentRadius,
            center = accentAnchor
        )

        // Soft vignette for depth — a considered, widely-used technique rather than a gimmick.
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to Color.Transparent, 1f to TwinsterBlack.copy(alpha = 0.32f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.maxDimension * 0.75f
            )
        )

        // Fine grain, tiled at native pixel density (not stretched) so it reads as a subtle
        // texture rather than blocky static.
        drawRect(brush = ShaderBrush(grainShader), alpha = 0.035f)
    }
}

/** A small noise tile repeated via a native BitmapShader, so grain stays fine regardless of screen size. */
@Composable
private fun rememberGrainTileShader(): Shader = remember {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val random = Random(42)
    for (x in 0 until size) {
        for (y in 0 until size) {
            val v = random.nextInt(256)
            bitmap.setPixel(x, y, android.graphics.Color.argb(255, v, v, v))
        }
    }
    BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

fun hueForGenre(genre: String): Float {
    val hash = genre.lowercase().hashCode()
    return ((hash % 360) + 360).toFloat() % 360f
}
