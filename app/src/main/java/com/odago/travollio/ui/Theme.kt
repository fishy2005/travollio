package com.odago.travollio.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odago.travollio.R

// ─────────────────────────────────────────────────────────────────────────────
// Travollio design system — "Terracotta Desert v2"
//
// Editorial warmth on a modern grid. One accent (terracotta), a warm neutral
// ramp (sand → charcoal), layered surfaces, and a serif display that gives the
// app a travel-magazine voice. Inter speaks, Playfair headlines, Cinzel stamps.
//
// Light:  canvas #F3EEE4  surface #FFFDF7  accent #C05A2A  ink #2C2318
// Dark:   canvas #191510  surface #262019  accent #E08A52  ink #F5F0E6
// ─────────────────────────────────────────────────────────────────────────────

// ── Palette (dark) ──────────────────────────────────────────────────────────
private val AccentDark = Color(0xFFE08A52)
private val AccentDeepDark = Color(0xFFF2BE8E)          // pale terracotta text/icons on dark
private val AccentTopDark = Color(0xFFEA9760)           // gradient top
private val AccentBaseDark = Color(0xFFB05627)          // gradient base
private val AccentTintDark = Color(0xFF3B2415)
private val SageDark = Color(0xFF9DBE8C)
private val SageTintDark = Color(0xFF22301C)
private val BlushDark = Color(0xFFE08972)
private val BlushTintDark = Color(0xFF331E17)
private val InkPrimaryDark = Color(0xFFF5F0E6)
private val InkSecondaryDark = Color(0xFFC2B6A3)
private val InkTertiaryDark = Color(0xFF8C8578)
private val HairlineDark = Color(0xFF3B342B)
private val RingTrackDark = Color(0xFF443628)
private val SurfaceSolidDark = Color(0xFF262019)        // cards
private val SurfaceGlassDark = Color(0xF0262019)
private val SurfacePanelDark = Color(0xF7221D16)        // sheets, docks, composers
private val GlassBorderDark = Color(0xFF3B342B)
private val ScrimDark = Color(0xE6000000)
val CanvasBgDark = Color(0xFF191510)
private val CanvasBgLight = Color(0xFFF3EEE4)

// ── Palette (light) ──────────────────────────────────────────────────────────
private val AccentLight = Color(0xFFC05A2A)             // burnt terracotta
private val AccentDeepLight = Color(0xFF9C4520)         // deep terracotta for text/icons
private val AccentTopLight = Color(0xFFD06837)
private val AccentBaseLight = Color(0xFFA54718)
private val AccentTintLight = Color(0xFFF6E3D3)
private val SageLight = Color(0xFF6E8A4E)
private val SageTintLight = Color(0xFFE6EBD8)
private val BlushLight = Color(0xFFB5472F)
private val BlushTintLight = Color(0xFFF6DFD8)
private val InkPrimaryLight = Color(0xFF2C2318)
private val InkSecondaryLight = Color(0xFF776C5D)
private val InkTertiaryLight = Color(0xFFA69C8E)
private val HairlineLight = Color(0xFFE4D8C3)
private val RingTrackLight = Color(0xFFEADDC9)
private val SurfaceSolidLight = Color(0xFFFFFDF7)
private val SurfaceGlassLight = Color(0xFAFFFDF7)
private val SurfacePanelLight = Color(0xFFFFFDF9)
private val GlassBorderLight = Color(0xFFE7DBC7)
private val ScrimLight = Color(0x59000000)

// Legacy aliases kept for API compat.
val CanvasBg = CanvasBgDark

// Soft layered shadows (subtle, warm): no harsh whites in light mode.
val SoftShadowA = Color(0x2E000000)
val SoftShadowB = Color(0x1A000000)

// Item kind → warm desert-tone glyphs (each kind its own calm hue on its tile).
val KindColor: Map<String, Color> = mapOf(
    "transit" to Color(0xFFC75B2B), // terracotta
    "food" to Color(0xFF96823B),    // sand olive
    "sight" to Color(0xFFB5823D),   // dune amber
    "hotel" to Color(0xFF8571A8),   // dusk violet
    "errand" to Color(0xFFA6885F)   // sand
)

val Sand = Color(0xFFD9C7A8)
val Lavender = Color(0xFF98A6D8)

// ── Theme-aware composition locals ──────────────────────────────────────────
val LocalAccent = staticCompositionLocalOf { AccentDark }
val LocalAccentDeep = staticCompositionLocalOf { AccentDeepDark }
val LocalAccentTop = staticCompositionLocalOf { AccentTopDark }
val LocalAccentBase = staticCompositionLocalOf { AccentBaseDark }
val LocalAccentTint = staticCompositionLocalOf { AccentTintDark }
val LocalSage = staticCompositionLocalOf { SageDark }
val LocalSageTint = staticCompositionLocalOf { SageTintDark }
val LocalBlush = staticCompositionLocalOf { BlushDark }
val LocalBlushTint = staticCompositionLocalOf { BlushTintDark }
val LocalInkPrimary = staticCompositionLocalOf { InkPrimaryDark }
val LocalInkSecondary = staticCompositionLocalOf { InkSecondaryDark }
val LocalInkTertiary = staticCompositionLocalOf { InkTertiaryDark }
val LocalHairline = staticCompositionLocalOf { HairlineDark }
val LocalRingTrack = staticCompositionLocalOf { RingTrackDark }
val LocalSurfaceSolid = staticCompositionLocalOf { SurfaceSolidDark }
val LocalSurfaceGlass = staticCompositionLocalOf { SurfaceGlassDark }
val LocalSurfacePanel = staticCompositionLocalOf { SurfacePanelDark }
val LocalGlassBorder = staticCompositionLocalOf { GlassBorderDark }
val LocalScrim = staticCompositionLocalOf { ScrimDark }

// Theme-aware accessors — resolve to the active palette. Composable-only.
val Accent: Color @Composable get() = LocalAccent.current
val AccentDeep: Color @Composable get() = LocalAccentDeep.current
val AccentTop: Color @Composable get() = LocalAccentTop.current
val AccentBase: Color @Composable get() = LocalAccentBase.current
val AccentTint: Color @Composable get() = LocalAccentTint.current
val Sage: Color @Composable get() = LocalSage.current
val SageTint: Color @Composable get() = LocalSageTint.current
val Blush: Color @Composable get() = LocalBlush.current
val BlushTint: Color @Composable get() = LocalBlushTint.current
val InkPrimary: Color @Composable get() = LocalInkPrimary.current
val InkSecondary: Color @Composable get() = LocalInkSecondary.current
val InkTertiary: Color @Composable get() = LocalInkTertiary.current
val Hairline: Color @Composable get() = LocalHairline.current
val RingTrack: Color @Composable get() = LocalRingTrack.current
val SurfaceSolid: Color @Composable get() = LocalSurfaceSolid.current
val SurfaceGlass: Color @Composable get() = LocalSurfaceGlass.current
val GlassBorder: Color @Composable get() = LocalGlassBorder.current
val Scrim: Color @Composable get() = LocalScrim.current

/** The brand gradient (accent → deeper accent), theme-aware. */
@Composable
fun accentBrush(): Brush = Brush.verticalGradient(listOf(AccentTop, AccentBase))

/** Theme-aware surface/text colors so glass, cards and text adapt together. */
@Immutable
data class TvColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val hairline: Color,
    val scrim: Color,
    val glassSurface: Color, // frosted card fill
    val glassPanel: Color,   // sheet / nav / hero panel fill
    val glassBorder: Color,  // hairline border on frosted panels
    val ringTrack: Color
)

val TvColorsDark = TvColors(
    textPrimary = InkPrimaryDark,
    textSecondary = InkSecondaryDark,
    textTertiary = InkTertiaryDark,
    hairline = HairlineDark,
    scrim = ScrimDark,
    glassSurface = SurfaceGlassDark,
    glassPanel = SurfacePanelDark,
    glassBorder = GlassBorderDark,
    ringTrack = RingTrackDark
)

val TvColorsLight = TvColors(
    textPrimary = InkPrimaryLight,
    textSecondary = InkSecondaryLight,
    textTertiary = InkTertiaryLight,
    hairline = HairlineLight,
    scrim = ScrimLight,
    glassSurface = SurfaceGlassLight,
    glassPanel = SurfacePanelLight,
    glassBorder = GlassBorderLight,
    ringTrack = RingTrackLight
)

val LocalTvColors = staticCompositionLocalOf { TvColorsDark }

/** Theme-aware colors: `MaterialTheme.tvColors.textSecondary` etc. */
val MaterialTheme.tvColors: TvColors
    @Composable get() = LocalTvColors.current

// ── Fonts ─────────────────────────────────────────────────────────────────────
// Voice of the brand: Playfair Display headlines (editorial), Inter for UI/body,
// Cinzel for the tiny letter-spaced eyebrow "stamps".
val Playfair = FontFamily(
    Font(R.font.playfair_600, FontWeight.SemiBold),
    Font(R.font.playfair_700, FontWeight.Bold)
)
val Cinzel = FontFamily(
    Font(R.font.cinzel_600, FontWeight.SemiBold),
    Font(R.font.cinzel_700, FontWeight.Bold)
)
val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold)
)

/** Cinzel eyebrow — always pass text.uppercase(). Section eyebrows, hero metas. */
val Overline = TextStyle(
    fontFamily = Cinzel,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.5.sp,
    lineHeight = 15.sp,
    letterSpacing = 2.4.sp
)

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF2E1A0C),
    primaryContainer = AccentTintDark,
    onPrimaryContainer = AccentDeepDark,
    secondary = SageDark,
    onSecondary = Color(0xFF1E2414),
    secondaryContainer = SageTintDark,
    onSecondaryContainer = Color(0xFFDCEBD0),
    tertiary = Sand,
    onTertiary = Color(0xFF241E10),
    background = CanvasBgDark,
    onBackground = InkPrimaryDark,
    surface = SurfaceSolidDark,
    onSurface = InkPrimaryDark,
    surfaceVariant = Color(0xFF33291F),
    onSurfaceVariant = InkSecondaryDark,
    outline = HairlineDark,
    outlineVariant = HairlineDark,
    error = BlushDark,
    onError = Color(0xFF2E110C),
    errorContainer = BlushTintDark,
    onErrorContainer = Color(0xFFF3C7BE),
    surfaceTint = AccentDark,
    scrim = ScrimDark
)

private val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = AccentTintLight,
    onPrimaryContainer = Color(0xFF5C2A10),
    secondary = SageLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SageTintLight,
    onSecondaryContainer = Color(0xFF2A3A12),
    tertiary = Sand,
    onTertiary = Color(0xFF241E10),
    background = CanvasBgLight,
    onBackground = InkPrimaryLight,
    surface = SurfaceSolidLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = Color(0xFFF0E6D4),
    onSurfaceVariant = InkSecondaryLight,
    outline = HairlineLight,
    outlineVariant = HairlineLight,
    error = BlushLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = BlushTintLight,
    onErrorContainer = Color(0xFF410E0B),
    surfaceTint = AccentLight,
    scrim = ScrimLight
)

// Typography: Playfair carries the editorial voice at display sizes, Inter does
// all the work below. Tight tracking on big text, airy line-heights on body.
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp),
    displayMedium = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 31.sp, lineHeight = 37.sp, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun TravollioTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTvColors provides (if (darkTheme) TvColorsDark else TvColorsLight),
        LocalAccent provides (if (darkTheme) AccentDark else AccentLight),
        LocalAccentDeep provides (if (darkTheme) AccentDeepDark else AccentDeepLight),
        LocalAccentTop provides (if (darkTheme) AccentTopDark else AccentTopLight),
        LocalAccentBase provides (if (darkTheme) AccentBaseDark else AccentBaseLight),
        LocalAccentTint provides (if (darkTheme) AccentTintDark else AccentTintLight),
        LocalSage provides (if (darkTheme) SageDark else SageLight),
        LocalSageTint provides (if (darkTheme) SageTintDark else SageTintLight),
        LocalBlush provides (if (darkTheme) BlushDark else BlushLight),
        LocalBlushTint provides (if (darkTheme) BlushTintDark else BlushTintLight),
        LocalInkPrimary provides (if (darkTheme) InkPrimaryDark else InkPrimaryLight),
        LocalInkSecondary provides (if (darkTheme) InkSecondaryDark else InkSecondaryLight),
        LocalInkTertiary provides (if (darkTheme) InkTertiaryDark else InkTertiaryLight),
        LocalHairline provides (if (darkTheme) HairlineDark else HairlineLight),
        LocalRingTrack provides (if (darkTheme) RingTrackDark else RingTrackLight),
        LocalSurfaceSolid provides (if (darkTheme) SurfaceSolidDark else SurfaceSolidLight),
        LocalSurfaceGlass provides (if (darkTheme) SurfaceGlassDark else SurfaceGlassLight),
        LocalGlassBorder provides (if (darkTheme) GlassBorderDark else GlassBorderLight),
        LocalScrim provides (if (darkTheme) ScrimDark else ScrimLight),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
