package com.twinster.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Scale-down-on-press feedback for primary CTAs, layered on top of (not replacing) the stock
 * Material ripple — share the same [interactionSource] with the Button/clickable so both react to
 * the same press. Reads as more deliberate than ripple alone without a gimmicky bounce.
 */
@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource, scaleDown: Float = 0.96f): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pressScale"
    )
}

/**
 * Fade + slight-rise entrance modifier for one item in a list/section, staggered by [index] so a
 * group of rows/cards cascades in instead of popping in all at once. [resetKey] restarts the
 * stagger — pass the current tab/selection so switching content re-plays the entrance.
 */
@Composable
fun entranceModifier(
    index: Int,
    resetKey: Any? = Unit,
    staggerMs: Int = 40,
    durationMs: Int = 220,
    riseDp: Float = 14f
): Modifier {
    var shown by remember(resetKey, index) { mutableStateOf(false) }
    LaunchedEffect(resetKey, index) {
        shown = false
        delay((index * staggerMs).toLong())
        shown = true
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(durationMs), label = "entranceAlpha")
    val riseY by animateFloatAsState(if (shown) 0f else riseDp, tween(durationMs), label = "entranceRise")
    return Modifier.graphicsLayer {
        this.alpha = alpha
        translationY = riseY
    }
}
