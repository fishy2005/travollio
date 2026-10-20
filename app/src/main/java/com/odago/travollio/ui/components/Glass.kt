package com.odago.travollio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsRailwayFilled
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.KindColor
import com.odago.travollio.ui.SoftShadowA
import com.odago.travollio.ui.SoftShadowB
import com.odago.travollio.ui.tvColors

/**
 * Frosted card: translucent theme surface + hairline border + soft shadow.
 * Adapts to light & dark (use MaterialTheme.tvColors).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    fill: Color? = null, // default: theme-aware glass surface
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val base = modifier
        .shadow(elevation = 6.dp, shape = shape, ambientColor = SoftShadowA, spotColor = SoftShadowB)
        .shadow(elevation = 1.dp, shape = shape, ambientColor = SoftShadowA, spotColor = SoftShadowB)
    val fillColor = fill ?: MaterialTheme.tvColors.glassSurface
    val border = BorderStroke(0.75.dp, MaterialTheme.tvColors.glassBorder)
    val inner: @Composable () -> Unit = {
        Box(Modifier.padding(contentPadding)) { content() }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = fillColor, border = border, modifier = base) { inner() }
    } else {
        Surface(shape = shape, color = fillColor, border = border, modifier = base) { inner() }
    }
}

/** Cinzel eyebrow section heading — the app's "stamp" language. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = com.odago.travollio.ui.Overline,
        color = MaterialTheme.tvColors.textTertiary,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/**
 * Selectable pill chip: solid accent fill + light text when selected, quiet
 * glass otherwise. Used for day selectors and filters.
 */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    color: Color = Accent
) {
    val selectedBg = color
    val fg = MaterialTheme.colorScheme.onPrimary
    val bg by animateColorAsState(
        targetValue = if (selected) selectedBg else MaterialTheme.tvColors.glassSurface,
        animationSpec = springSnappy(),
        label = "pillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) fg else MaterialTheme.tvColors.textSecondary,
        animationSpec = springSnappy(),
        label = "pillFg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(
            0.75.dp,
            if (selected) Color(0x00000000) else MaterialTheme.tvColors.hairline
        ),
        modifier = modifier.pressScale()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}

/** Small colored dot — itinerary kind marker (transit / food / sight / hotel / errand). */
@Composable
fun KindDot(kind: String, modifier: Modifier = Modifier, size: Dp = 9.dp) {
    val color = KindColor[kind] ?: MaterialTheme.tvColors.textTertiary
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.25f), modifier = Modifier.size(size * 1.9f)) {}
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(size)) {}
    }
}

/** Monochrome vector glyph for an activity kind — clean, not emoji-like or "AI". */
fun kindIcon(kind: String): ImageVector = when (kind) {
    "transit" -> Icons.Filled.DirectionsRailwayFilled
    "hotel" -> Icons.Filled.Hotel
    "food" -> Icons.Filled.Restaurant
    "sight" -> Icons.Filled.PhotoCamera
    "errand" -> Icons.Filled.ShoppingBag
    else -> Icons.Filled.Star
}

/** Meta-style monochrome icon tile for an activity card. Dark rounded tile with a
 *  single-color (gold-tinted) glyph in the kind's hue. */
@Composable
fun KindTile(kind: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val color = KindColor[kind] ?: MaterialTheme.tvColors.textTertiary
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.28f)),
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                kindIcon(kind),
                contentDescription = kind,
                tint = color,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

/** AI-generated daily summary / note card (written by the agent's day.note tool).
 *  Rendered as a terracotta-gradient "hero" so the AI's presence reads as a first-class,
 *  branded surface rather than a plain tinted box. */
@Composable
fun AiSummaryCard(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(22.dp), ambientColor = SoftShadowA, spotColor = SoftShadowB)
    ) {
        Box(
            Modifier
                .background(Brush.verticalGradient(listOf(com.odago.travollio.ui.AccentTop, com.odago.travollio.ui.AccentBase)))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFF6DEC6),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(10.dp))
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        "PABLO'S NOTE",
                        style = com.odago.travollio.ui.Overline,
                        color = Color(0xFFF3E0D2)
                    )
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFDF7EF))
                }
            }
        }
    }
}

// ── Button language ──────────────────────────────────────────────────────────
// One geometry (52dp pill), three voices. All press-scale with a light tick.

/** Solid accent pill — the app's main action ("Generate", "Mark as done", "Save"). */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        onClick = {
            if (enabled) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
        },
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        modifier = modifier
            .height(52.dp)
            .pressScale()
            .shadow(6.dp, RoundedCornerShape(50), ambientColor = SoftShadowA, spotColor = SoftShadowB)
            .background(
                if (enabled) accentBrushLight() else SolidColor(MaterialTheme.tvColors.textTertiary.copy(alpha = 0.35f)),
                RoundedCornerShape(50)
            )
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 20.dp)
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = Color(0xFFFDF3E7), modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color(0xFFFDF3E7) else MaterialTheme.tvColors.textSecondary
            )
        }
    }
}

// Static accent brush for buttons (readable on both canvas shades).
private fun accentBrushLight(): Brush =
    Brush.verticalGradient(listOf(Color(0xFFC9663B), Color(0xFF9C4520)))

/** Quiet outlined pill — secondary actions that shouldn't shout. */
@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        onClick = {
            if (enabled) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
        },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.tvColors.glassSurface,
        border = BorderStroke(0.75.dp, MaterialTheme.tvColors.hairline),
        modifier = modifier.height(52.dp).pressScale()
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 20.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.tvColors.textPrimary else MaterialTheme.tvColors.textTertiary
            )
        }
    }
}

/** Accent-tinted pill — supportive actions ("Fill in what's missing"). */
@Composable
fun TonalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        onClick = {
            if (enabled) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
        },
        shape = RoundedCornerShape(50),
        color = com.odago.travollio.ui.AccentTint,
        modifier = modifier.height(52.dp).pressScale()
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 20.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = com.odago.travollio.ui.AccentDeep
            )
        }
    }
}
