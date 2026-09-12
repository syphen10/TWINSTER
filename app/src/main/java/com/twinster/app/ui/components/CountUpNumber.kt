package com.twinster.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinster.app.ui.theme.CabinetGroteskFamily

@Composable
fun CountUpNumber(
    targetValue: Int,
    modifier: Modifier = Modifier,
    suffix: String = "",
    style: TextStyle = TextStyle(fontFamily = CabinetGroteskFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp)
) {
    var start by remember { mutableIntStateOf(0) }
    val animated by animateIntAsState(
        targetValue = targetValue,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "countUp"
    )
    androidx.compose.runtime.LaunchedEffect(targetValue) { start = targetValue }
    Text(text = "$animated$suffix", style = style, modifier = modifier)
}

/**
 * Stat-reveal variant of [CountUpNumber] for one-off score moments (Obscurity, etc.) — a thin arc
 * draws in behind the number in step with the count, instead of the digits appearing with nothing
 * else happening. [fraction] should be the score normalized to 0f..1f (e.g. score/100).
 */
@Composable
fun CountUpNumberWithRing(
    targetValue: Int,
    fraction: Float,
    modifier: Modifier = Modifier,
    ringDiameter: androidx.compose.ui.unit.Dp = 108.dp,
    ringColors: List<Color> = listOf(com.twinster.app.ui.theme.TwinsterGreen, com.twinster.app.ui.theme.TwinsterCyan),
    suffix: String = "",
    style: TextStyle = TextStyle(fontFamily = CabinetGroteskFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp)
) {
    val animated by animateIntAsState(
        targetValue = targetValue,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "countUpRing"
    )
    val sweep by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "ringSweep"
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(ringDiameter)) {
        Canvas(modifier = Modifier.size(ringDiameter)) {
            val strokeWidth = size.minDimension * 0.07f
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(ringColors),
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(text = "$animated$suffix", style = style)
    }
}
