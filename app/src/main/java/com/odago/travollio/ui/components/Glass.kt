package com.odago.travollio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsRailwayFilled
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.AccentTint
import com.odago.travollio.ui.Backdrop
import com.odago.travollio.ui.DesignTokens
import com.odago.travollio.ui.GlassBorder
import com.odago.travollio.ui.Hairline
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.InkTertiary
import com.odago.travollio.ui.KindColor
import com.odago.travollio.ui.SoftShadowA
import com.odago.travollio.ui.SoftShadowB
import com.odago.travollio.ui.SurfaceGlass
import com.odago.travollio.ui.tvColors

/**
 * Frosted card: translucent theme surface + hairline border + two-layer soft
 * shadow. Adapts to light & dark (use MaterialTheme.tvColors).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    fill: Color? = null, // default: theme-aware glass surface
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val base = modifier
        .shadow(elevation = 10.dp, shape = shape, ambientColor = SoftShadowA, spotColor = SoftShadowB)
        .shadow(elevation = 2.dp, shape = shape, ambientColor = SoftShadowA, spotColor = SoftShadowB)
    val fillColor = fill ?: MaterialTheme.tvColors.glassSurface
    val border = BorderStroke(0.5.dp, MaterialTheme.tvColors.glassBorder)
    val inner: @Composable () -> Unit = {
        Box(Modifier.padding(contentPadding)) { content() }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = fillColor, border = border, modifier = base) { inner() }
    } else {
        Surface(shape = shape, color = fillColor, border = border, modifier = base) { inner() }
    }
}

/** Uppercase-eyebrow section heading, iOS settings style. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.tvColors.textTertiary,
        modifier = modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

/**
 * Selectable pill chip with animated fill. Selected = muted accent tint;
 * unselected = theme glass with hairline. Used for day selectors and filters.
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
    val bg by animateColorAsState(
        targetValue = if (selected) color.copy(alpha = 0.16f) else MaterialTheme.tvColors.glassSurface,
        animationSpec = springSnappy(),
        label = "pillBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) color else MaterialTheme.tvColors.textTertiary,
        animationSpec = springSnappy(),
        label = "pillFg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(0.5.dp, if (selected) color.copy(alpha = 0.5f) else MaterialTheme.tvColors.hairline),
        modifier = modifier.pressScale()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.7f)
                )
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
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
 *  Rendered as a teal-gradient "hero" so the AI's presence reads as a first-class,
 *  branded surface rather than a plain tinted box. */
@Composable
fun AiSummaryCard(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp), ambientColor = SoftShadowA, spotColor = SoftShadowB)
    ) {
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF2A9A9F), Color(0xFF0F5D62))))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFCFF4EE),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        "AI SUMMARY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFBCE9E2)
                    )
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Award-grade glass system — full-screen backdrop, badges, chips, the animated
// completion check, duotone icon containers, inline media and the floating
// mini-map. All theme-aware and built on the DesignTokens above.
// ─────────────────────────────────────────────────────────────────────────────

/** A point in a normalized (0..1) coordinate space used by the mini-map route. */
data class RoutePoint(val x: Float, val y: Float)

/** Blend two colors by [fraction] (0..1) — dependency-free color mixer. */
private fun mix(a: Color, b: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = a.red * (1 - f) + b.red * f,
        green = a.green * (1 - f) + b.green * f,
        blue = a.blue * (1 - f) + b.blue * f,
        alpha = a.alpha * (1 - f) + b.alpha * f
    )
}

/**
 * Full-screen blurred "landscape" wall (sky → sun → horizon → mountains → ground)
 * with a high-contrast un-blurred scrim laid over the top, so frosted cards and
 * text stay legible (WCAG AA) in both light and dark mode. Draws the sun/haze and
 * mountain silhouettes on a single Canvas so the glow is pixel-accurate.
 */
@Composable
fun ScenicBackdrop(modifier: Modifier = Modifier) {
    val g = Backdrop
    Box(modifier = modifier.fillMaxSize()) {
        // The blurred landscape layer (sky wash + sun/haze glows + mountain ridges).
        Box(
            Modifier
                .fillMaxSize()
                .blur(DesignTokens.backdropBlur)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(g.sky, g.horizon, g.ground)))
            )
            Canvas(Modifier.fillMaxSize()) {
                // Sun glow — warm orb, top-right.
                drawCircle(
                    brush = Brush.radialGradient(listOf(g.sun, g.sun.copy(alpha = 0.35f), Color.Transparent)),
                    radius = size.width * 0.42f,
                    center = Offset(size.width * 0.80f, size.height * 0.12f)
                )
                // Reflective haze, bottom-left.
                drawCircle(
                    brush = Brush.radialGradient(listOf(g.horizon.copy(alpha = 0.45f), Color.Transparent)),
                    radius = size.width * 0.52f,
                    center = Offset(size.width * 0.06f, size.height * 0.92f)
                )
                val baseY = size.height * 0.80f
                val ridge = Path().apply {
                    moveTo(0f, baseY + 20f)
                    lineTo(size.width * 0.22f, baseY - 30f)
                    lineTo(size.width * 0.40f, baseY + 4f)
                    lineTo(size.width * 0.62f, baseY - 46f)
                    lineTo(size.width * 0.86f, baseY - 6f)
                    lineTo(size.width, baseY + 26f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(ridge, color = g.horizon.copy(alpha = 0.30f))
                val ridge2 = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width * 0.30f, baseY + 34f)
                    lineTo(size.width * 0.58f, baseY + 16f)
                    lineTo(size.width, baseY + 56f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(ridge2, color = g.ground.copy(alpha = 0.55f))
            }
        }
        // Legibility scrim — kept crisp (not blurred), laid over the whole scene.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(g.scrimTop, g.scrimBottom)))
        )
    }
}

/** Glassmorphic pill badge, e.g. "TRIP TO ASIA · ENDED", with a status dot + hairline stroke. */
@Composable
fun StatusBadge(text: String, modifier: Modifier = Modifier, dotColor: Color = Accent) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusPill),
        color = MaterialTheme.tvColors.glassSurface,
        border = BorderStroke(0.5.dp, MaterialTheme.tvColors.glassBorder),
        modifier = modifier.pressScale()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(Modifier.size(6.dp).background(dotColor, CircleShape))
            Spacer(Modifier.size(6.dp))
            Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkPrimary)
        }
    }
}

/** Compact time pill with an integrated clock icon, e.g. "22:00". */
@Composable
fun TimePill(time: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusPill),
        color = AccentTint,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Filled.AccessTime, contentDescription = null, tint = AccentDeep, modifier = Modifier.size(13.dp))
            Spacer(Modifier.size(4.dp))
            Text(time, style = MaterialTheme.typography.labelMedium, color = AccentDeep)
        }
    }
}

/** Secondary metadata subtitle — location pin + recommendation text (e.g. "Highly rated for coffee"). */
@Composable
fun MetadataChip(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.Place) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = AccentDeep, modifier = Modifier.size(12.dp))
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = InkSecondary, maxLines = 1)
    }
}

/**
 * Interactive completion checkbox (right edge of an itinerary card). An empty
 * hairline ring springs into a filled acarnt circle with a bouncy white check —
 * the classic iOS "marked complete" micro-interaction.
 */
@Composable
fun CompletionCheck(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val shape = CircleShape
    val bg by animateColorAsState(
        targetValue = if (checked) Accent else MaterialTheme.tvColors.glassSurface,
        animationSpec = springSnappy(),
        label = "checkBg"
    )
    val border by animateColorAsState(
        targetValue = if (checked) Accent.copy(alpha = 0.55f) else MaterialTheme.tvColors.hairline,
        animationSpec = springSnappy(),
        label = "checkBorder"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.3f,
        animationSpec = springGentle(),
        label = "checkScale"
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "checkAlpha"
    )
    Box(
        modifier = modifier
            .size(size)
            .shadow(if (checked) 6.dp else 0.dp, shape, ambientColor = SoftShadowA, spotColor = SoftShadowB)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = if (checked) "Completed" else "Mark complete",
            tint = Color.White,
            modifier = Modifier
                .size(size * 0.62f)
                .graphicsLayer { scaleX = checkScale; scaleY = checkScale; alpha = checkAlpha }
        )
    }
}

/**
 * 3D/duotone rounded icon container per event category (dining, photo tours,
 * hotel checkout, transport...). A glossy vertical gradient + hairline rim +
 * soft lift shadow reads as a tactile, "3D" tile rather than a flat glyph.
 */
@Composable
fun DuoIconContainer(kind: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val base = KindColor[kind] ?: MaterialTheme.tvColors.textTertiary
    val top = mix(Color.White, base, 0.30f)
    val bottom = mix(base, Color.Black, 0.32f)
    val glyph = mix(base, Color.Black, 0.24f)
    val rim = mix(Color.White, base, 0.58f)
    val shape = RoundedCornerShape(DesignTokens.radiusTile)
    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 7.dp, shape = shape, ambientColor = SoftShadowA, spotColor = SoftShadowB)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, base, bottom)))
            .border(0.6.dp, rim, shape)
    ) {
        // glossy top highlight — the "3D" catch-light
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.34f), Color.Transparent)))
        )
        Icon(
            kindIcon(kind),
            contentDescription = kind,
            tint = glyph,
            modifier = Modifier.align(Alignment.Center).size(size * 0.5f)
        )
    }
}

/**
 * Inline 1:1 rounded media thumbnail. When [filePath] points to a decoded image
 * it renders the photo (Crop); otherwise it falls back to a kind-tinted "media"
 * tile so a card always has a balanced visual anchor.
 */
@Composable
fun MediaThumb(
    filePath: String?,
    kind: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    onTap: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(DesignTokens.radiusMedia)
    val clickableMod = modifier
        .size(size)
        .shadow(6.dp, shape, ambientColor = SoftShadowA, spotColor = SoftShadowB)
        .clip(shape)
        .clickable(enabled = onTap != null, onClick = { onTap?.invoke() })
    if (filePath != null) {
        var bitmap by remember(filePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(filePath) {
            bitmap = runCatching { android.graphics.BitmapFactory.decodeFile(filePath) }.getOrNull()
        }
        Box(clickableMod) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: MediaTileFallback(kind)
        }
    } else {
        Box(clickableMod) { MediaTileFallback(kind) }
    }
}

@Composable
private fun MediaTileFallback(kind: String) {
    val base = KindColor[kind] ?: Accent
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(mix(Color.White, base, 0.35f), mix(base, Color.Black, 0.18f))))
    ) {
        Icon(
            kindIcon(kind),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.align(Alignment.Center).fillMaxSize(0.5f)
        )
    }
}

/**
 * Fingerprint-style route path through the given points. Smooths through the
 * midpoints so the dashed line reads like a hand-drawn journey, not a polyline.
 */
private fun buildRoutePath(pts: List<Offset>): Path {
    val p = Path()
    if (pts.isEmpty()) return p
    p.moveTo(pts.first().x, pts.first().y)
    for (i in 1..pts.lastIndex) {
        val prev = pts[i - 1]
        val cur = pts[i]
        val mid = Offset((prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
        p.quadraticTo(prev.x, prev.y, mid.x, mid.y)
    }
    p.lineTo(pts.last().x, pts.last().y)
    return p
}

/** Floating glassmorphic mini-map: a frosted tile drawing a dashed route vector
 *  path between location pins, with soft "water / park" landmass tint washes. */
@Composable
fun MiniMapWidget(
    points: List<RoutePoint>,
    pins: List<RoutePoint>,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(DesignTokens.radiusMap)
    val accent = Accent
    val accentDeep = AccentDeep
    val accentTint = AccentTint
    Surface(
        shape = shape,
        color = MaterialTheme.tvColors.glassPanel,
        border = BorderStroke(0.5.dp, MaterialTheme.tvColors.glassBorder),
        modifier = modifier.shadow(elevation = 10.dp, shape = shape, ambientColor = SoftShadowA, spotColor = SoftShadowB)
    ) {
        Box(Modifier.padding(10.dp)) {
            Canvas(Modifier.size(96.dp)) {
                val n = size.minDimension
                // landmass / water washes
                drawCircle(
                    brush = Brush.radialGradient(listOf(accentTint.copy(alpha = 0.85f), Color.Transparent)),
                    radius = n * 0.34f,
                    center = Offset(n * 0.30f, n * 0.72f)
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent)),
                    radius = n * 0.42f,
                    center = Offset(n * 0.78f, n * 0.40f)
                )
                // dashed route with a soft glow underlay
                val pts = points.map { Offset(size.width * it.x, size.height * it.y) }
                if (pts.size >= 2) {
                    val route = buildRoutePath(pts)
                    drawPath(route, color = accent.copy(alpha = 0.22f), style = Stroke(width = n * 0.10f))
                    drawPath(
                        route,
                        color = accent,
                        style = Stroke(
                            width = n * 0.045f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(n * 0.10f, n * 0.08f))
                        )
                    )
                }
                // location pins (first pin = active endpoint)
                pins.forEachIndexed { i, rp ->
                    val c = Offset(size.width * rp.x, size.height * rp.y)
                    drawCircle(color = Color.White, radius = n * 0.085f, center = c)
                    drawCircle(color = if (i == 0) accentDeep else mix(accent, Color.White, 0.2f), radius = n * 0.055f, center = c)
                    drawCircle(color = Color.White, radius = n * 0.022f, center = c)
                }
            }
        }
    }
}
