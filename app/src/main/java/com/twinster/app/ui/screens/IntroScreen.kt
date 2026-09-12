package com.twinster.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinster.app.ui.theme.AnimatedMoodBackground
import com.twinster.app.ui.theme.CabinetGroteskFamily
import com.twinster.app.ui.theme.TwinsterGreen
import com.twinster.app.ui.theme.TwinsterPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private const val BAR_RISE_MS = 900
private const val BAR_RISE_DELAY_MS = 100
private const val WORDMARK_DELAY_MS = 650
private const val WORDMARK_LETTER_MS = 420
private const val WORDMARK_STAGGER_MS = 35
private const val TOTAL_DURATION_MS = 2200L
private const val EQ_BAR_COUNT = 9

/**
 * Branded launch animation shown once per cold start, after the system SplashScreen hands off and
 * before the nav graph's real start destination. A row of equalizer bars (echoing the app's
 * green/pink twin palette) rises up and pulses like a live meter, then the "Twinster" wordmark
 * fades and scales in with a per-letter stagger over the same animated hue-drifting background
 * used elsewhere in the app. Auto-advances after ~2.2s; tapping skips early.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val barRiseProgress = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkScale = remember { Animatable(0.85f) }
    var finished by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "introEq")
    val eqPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "introEqPhase"
    )

    fun finish() {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        launch {
            delay(BAR_RISE_DELAY_MS.toLong())
            barRiseProgress.animateTo(1f, animationSpec = tween(BAR_RISE_MS, easing = FastOutSlowInEasing))
        }
        launch {
            delay(WORDMARK_DELAY_MS.toLong())
            launch { wordmarkAlpha.animateTo(1f, animationSpec = tween(WORDMARK_LETTER_MS, easing = LinearEasing)) }
            wordmarkScale.animateTo(1f, animationSpec = tween(WORDMARK_LETTER_MS, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
        }
        delay(TOTAL_DURATION_MS)
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { finish() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedMoodBackground(baseHue = 285f, saturation = 0.55f)

        // An equalizer-style row of bars rises up and settles into a live pulse behind the wordmark.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rise = barRiseProgress.value
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val maxBarHeight = size.minDimension * 0.22f
            val barWidth = size.minDimension * 0.028f
            val spacing = size.minDimension * 0.05f
            val totalWidth = (EQ_BAR_COUNT - 1) * spacing
            val startX = centerX - totalWidth / 2f

            for (i in 0 until EQ_BAR_COUNT) {
                val phase = i * 0.85f
                val wave = (sin(eqPhase + phase) + 1f) / 2f
                val heightFraction = (0.28f + 0.72f * wave) * rise
                val barHeight = maxBarHeight * heightFraction
                val x = startX + i * spacing
                val tint = lerp(TwinsterGreen, TwinsterPink, i / (EQ_BAR_COUNT - 1).toFloat())

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(tint.copy(alpha = 0.95f), tint.copy(alpha = 0.35f))
                    ),
                    topLeft = Offset(x - barWidth / 2f, centerY - barHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                )
            }
        }

        StaggeredWordmark(alpha = wordmarkAlpha.value, scale = wordmarkScale.value)
    }
}

@Composable
private fun StaggeredWordmark(alpha: Float, scale: Float) {
    val word = "Twinster"
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        word.forEachIndexed { index, char ->
            val letterProgress = remember(index) { Animatable(0f) }
            LaunchedEffect(alpha > 0f) {
                if (alpha > 0f) {
                    delay((index * WORDMARK_STAGGER_MS).toLong())
                    letterProgress.animateTo(1f, animationSpec = tween(260, easing = FastOutSlowInEasing))
                }
            }
            Text(
                text = char.toString(),
                fontFamily = CabinetGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
                letterSpacing = (-0.5).sp,
                color = Color.White,
                modifier = Modifier
                    .offset(y = ((1f - letterProgress.value) * 10).dp)
                    .graphicsLayer { this.alpha = letterProgress.value }
            )
        }
    }
}
