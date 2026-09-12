package com.twinster.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.twinster.app.ui.theme.TwinsterGreen
import com.twinster.app.ui.theme.TwinsterPink

/**
 * Glassmorphic replacement for a stock CircularProgressIndicator — a rotating gradient ring in the
 * app's own green/pink palette, consistent with AnimatedMoodBackground, instead of a plain Material
 * spinner sitting on the dark background.
 */
@Composable
fun TwinsterLoadingRing(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "loadingRing")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "ringAngle"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(700, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "ringPulse"
    )

    Canvas(modifier = modifier.size(size)) {
        rotate(degrees = angle) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        TwinsterGreen.copy(alpha = 0f),
                        TwinsterGreen.copy(alpha = pulse),
                        TwinsterPink.copy(alpha = pulse),
                        TwinsterGreen.copy(alpha = 0f)
                    )
                ),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = size.toPx() * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

/** Full-screen centered loading state for data-fetch waits (Local Library scan, etc.). */
@Composable
fun TwinsterLoadingScreen(label: String? = null, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TwinsterLoadingRing()
            if (label != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
