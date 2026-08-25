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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odago.travollio.R

// ─────────────────────────────────────────────────────────────────────────────
// Travollio design system — "Fable Mono" v0.3.1 (2026-08-24) + Light/Dark (2026-08-25)
//
// Fable 5 editorial LAYOUT (serif display headings, Cinzel overlines, timeline
// cards) over a BALANCED MONO palette with a single baby-blue accent.
//
// Every token is exposed through composition locals as a theme-aware
// `@Composable getter`. Components keep writing `Color = InkPrimary` or
// `tint = AccentDeep` and get the right value for whichever mode is active —
// no per-screen theme branching needed.
//
// Dark palette:   canvas #0B0D0F  surface #14161A  accent #7EC8E3 (baby blue)
// Light palette:  canvas #F4F6F8  surface #FFFFFF  accent #2F7DA8 (deeper blue
//                 so accent text/icons/buttons stay readable on white).
//   ─────────────────────────────────────────────────────────────────────────────

// ── Palette (dark) ──────────────────────────────────────────────────────────
private val AccentDark = Color(0xFF7EC8E3)
private val AccentDeepDark = Color(0xFFB7E0F4)
private val AccentTintDark = Color(0xFF1A2B33)
private val SageDark = Color(0xFF8FC6DC)
private val SageTintDark = Color(0xFF16262D)
private val BlushDark = Color(0xFFE08A7D)
private val BlushTintDark = Color(0xFF2A1B18)
private val InkPrimaryDark = Color(0xFFF2F5F7)
private val InkSecondaryDark = Color(0xFFA9B4BF)
private val InkTertiaryDark = Color(0xFF6C7783)
private val HairlineDark = Color(0xFF2C333A)
private val RingTrackDark = Color(0xFF2C333A)
private val SurfaceSolidDark = Color(0xFF14161A)
private val SurfaceGlassDark = Color(0xE614161A)
private val SurfacePanelDark = Color(0xF214161A)
private val GlassBorderDark = Color(0xFF2C333A)
private val ScrimDark = Color(0xE6000000)
val CanvasBgDark = Color(0xFF0B0D0F)
private val CanvasBgLight = Color(0xFFF4F6F8)

// ── Palette (light) ──────────────────────────────────────────────────────────
private val AccentLight = Color(0xFF2F7DA8)          // deeper blue, readable on white
private val AccentDeepLight = Color(0xFF1E5C7E)      // accent as text/icon color on light
private val AccentTintLight = Color(0xFFDCEDF6)      // light blue container
private val SageLight = Color(0xFF2F7DA8)
private val SageTintLight = Color(0xFFDCEAF4)
private val BlushLight = Color(0xFFC05B4B)
private val BlushTintLight = Color(0xFFF6DDD7)
private val InkPrimaryLight = Color(0xFF1B2129)
private val InkSecondaryLight = Color(0xFF566069)
private val InkTertiaryLight = Color(0xFF8A95A0)
private val HairlineLight = Color(0xFFD8DDE2)
private val RingTrackLight = Color(0xFFD8DDE2)
private val SurfaceSolidLight = Color(0xFFFFFFFF)
private val SurfaceGlassLight = Color(0xEFFFFFFF)
private val SurfacePanelLight = Color(0xFAFFFFFF)
private val GlassBorderLight = Color(0xFFD8DDE2)
private val ScrimLight = Color(0x59000000)

// Legacy aliases kept for API compat (dark values; always override toward a real
// palette via the theme-provided getters below).
val CanvasBg = CanvasBgDark

// Soft layered shadows (dark theme): subtle black glows, no harsh whites.
val SoftShadowA = Color(0x66000000)
val SoftShadowB = Color(0x33000000)

// Item kind → MONOCHROME greys (subtle variety; activity icons render as a
// single tonal hue on their tiles — works on dark and light backgrounds).
val KindColor: Map<String, Color> = mapOf(
    "transit" to Color(0xFFC3CBD4),
    "food" to Color(0xFFB7C0C9),
    "sight" to Color(0xFFA9B3BD),
    "hotel" to Color(0xFF9AA4AE),
    "errand" to Color(0xFF8B95A0)
)

val Sand = Color(0xFFAAB6C0)
val Lavender = Color(0xFF98A6D8)

// ── Theme-aware composition locals ──────────────────────────────────────────
val LocalAccent = staticCompositionLocalOf { AccentDark }
val LocalAccentDeep = staticCompositionLocalOf { AccentDeepDark }
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

// Theme-aware accessors — same names as prior fixed tokens, now resolving to the
// active palette. Use only inside @Composable (they read a composition local).
val Accent: Color @Composable get() = LocalAccent.current
val AccentDeep: Color @Composable get() = LocalAccentDeep.current
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

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF1A1408),
    primaryContainer = AccentTintDark,
    onPrimaryContainer = AccentDeepDark,
    secondary = SageDark,
    onSecondary = Color(0xFF141810),
    secondaryContainer = SageTintDark,
    onSecondaryContainer = Color(0xFFD6E2C8),
    tertiary = Sand,
    onTertiary = Color(0xFF1D190F),
    background = CanvasBgDark,
    onBackground = InkPrimaryDark,
    surface = SurfaceSolidDark,
    onSurface = InkPrimaryDark,
    surfaceVariant = Color(0xFF1C1C21),
    onSurfaceVariant = InkSecondaryDark,
    outline = HairlineDark,
    outlineVariant = HairlineDark,
    error = Color(0xFFD98A7D),
    onError = Color(0xFF2E110C),
    errorContainer = Color(0xFF4A2A24),
    onErrorContainer = Color(0xFFF3C7BE),
    surfaceTint = AccentDark,
    scrim = ScrimDark
)

private val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = AccentTintLight,
    onPrimaryContainer = Color(0xFF12303F),
    secondary = SageLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SageTintLight,
    onSecondaryContainer = Color(0xFF12303F),
    tertiary = Sand,
    onTertiary = Color(0xFF1D190F),
    background = CanvasBgLight,
    onBackground = InkPrimaryLight,
    surface = SurfaceSolidLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = Color(0xFFE8ECEF),
    onSurfaceVariant = InkSecondaryLight,
    outline = HairlineLight,
    outlineVariant = HairlineLight,
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surfaceTint = AccentLight,
    scrim = ScrimLight
)

// Editorial serif headings (Playfair) + Cinzel overlines + Inter body.
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.4).sp),
    displayMedium = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp),
    displaySmall = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    headlineLarge = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    headlineSmall = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TravollioTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTvColors provides (if (darkTheme) TvColorsDark else TvColorsLight),
        LocalAccent provides (if (darkTheme) AccentDark else AccentLight),
        LocalAccentDeep provides (if (darkTheme) AccentDeepDark else AccentDeepLight),
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
        LocalSurfacePanel provides (if (darkTheme) SurfacePanelDark else SurfacePanelLight),
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
