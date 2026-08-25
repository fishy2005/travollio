package com.odago.travollio.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

// ─────────────────────────────────────────────────────────────────────────────
// Motion language: spring-physics everywhere (choreographer-driven → 60fps+).
// gentle  = soft, barely-there bounce (cards, rings, counters)
// snappy  = no overshoot, quick settle (presses, tab slides)
// ─────────────────────────────────────────────────────────────────────────────

fun <T> springGentle(): SpringSpec<T> =
    spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

fun <T> springSnappy(): SpringSpec<T> =
    spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

/**
 * iOS-style scale-on-press micro-interaction. Springs to 0.97 while pressed,
 * bounces back on release. Pass your own interactionSource to share ripple state.
 */
fun Modifier.pressScale(interactionSource: MutableInteractionSource? = null): Modifier = composed {
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = springSnappy(),
        label = "pressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
