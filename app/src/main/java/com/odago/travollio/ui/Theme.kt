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
// Travollio design system — "Teal Safari" v0.3.5 (2026-08-26)
//
// A warm, outdoorsy travel look: deep safari-teal accent, cream-sand surfaces,
// soft layered cards, rounded everything, modern sans-serif (Inter) throughout.
// Single accent + thoughtful neutrals, so every screen reads calm and premium.
//
// Every token is exposed through composition locals as a theme-aware
// `@Composable getter` (same architecture as before). Components keep writing
// `Color = InkPrimary` / `tint = AccentDeep` and get the right value for
// whichever mode is active — no per-screen theme branching.
//
// Light palette:  canvas #EFE9DD (warm cream)  surface #FAF7F0  accent #17777D
//                 text #20343A (teal-charcoal)  hairline #DAD4C6
// Dark palette:   canvas #0E1617 (teal-forest) surface #16211F  accent #4DB4AF
//                 text #EDF4F1                  hairline #23312E
//   ─────────────────────────────────────────────────────────────────────────────

// ── Palette (dark) ──────────────────────────────────────────────────────────
private val AccentDark = Color(0xFF4DB4AF)            // safari teal, readable on dark
private val AccentDeepDark = Color(0xFF9BDFDA)        // pale teal for text/icons on dark
private val AccentTintDark = Color(0xFF1A312F)        // deep teal container
private val SageDark = Color(0xFF7FC39A)              // warm green (on-pace)
private val SageTintDark = Color(0xFF183027)
private val BlushDark = Color(0xFFE08972)             // warm coral (hot / error)
private val BlushTintDark = Color(0xFF331E17)
private val InkPrimaryDark = Color(0xFFEDF4F1)
private val InkSecondaryDark = Color(0xFFA9BCB6)
private val InkTertiaryDark = Color(0xFF6C7E79)
private val HairlineDark = Color(0xFF23312E)
private val RingTrackDark = Color(0xFF24423E)
private val SurfaceSolidDark = Color(0xFF16211F)
private val SurfaceGlassDark = Color(0xE616211F)
private val SurfacePanelDark = Color(0xF216211F)
private val GlassBorderDark = Color(0xFF23312E)
private val ScrimDark = Color(0xE6000000)
val CanvasBgDark = Color(0xFF0E1617)
private val CanvasBgLight = Color(0xFFEFE9DD)

// ── Palette (light) ──────────────────────────────────────────────────────────
private val AccentLight = Color(0xFF17777D)           // safari teal
private val AccentDeepLight = Color(0xFF0E555B)       // deep teal for text/icons on cream
private val AccentTintLight = Color(0xFFDCEAE5)       // soft teal container
private val SageLight = Color(0xFF4C8A6A)
private val SageTintLight = Color(0xFFE0ECE3)
private val BlushLight = Color(0xFFC05B43)
private val BlushTintLight = Color(0xFFF6DFD8)
private val InkPrimaryLight = Color(0xFF20343A)       // teal-charcoal
private val InkSecondaryLight = Color(0xFF5E6B70)
private val InkTertiaryLight = Color(0xFF94A0A5)
private val HairlineLight = Color(0xFFDAD4C6)         // warm sand hairline
private val RingTrackLight = Color(0xFFE4DED0)
private val SurfaceSolidLight = Color(0xFFFAF7F0)
private val SurfaceGlassLight = Color(0xF7F8F4EC)     // frosted warm cream
private val SurfacePanelLight = Color(0xFAF6F2E9)
private val GlassBorderLight = Color(0xFFE0D9CB)
private val ScrimLight = Color(0x59000000)

// Legacy aliases kept for API compat.
val CanvasBg = CanvasBgDark

// Soft layered shadows (subtle, warm): no harsh whites in light mode.
val SoftShadowA = Color(0x33000000)
val SoftShadowB = Color(0x1F000000)

// Item kind → muted safari-tone glyphs (each kind its own calm hue on its tile).
val KindColor: Map<String, Color> = mapOf(
    "transit" to Color(0xFF3E99A0), // teal
    "food" to Color(0xFF5E9A6E),    // green
    "sight" to Color(0xFF6E8FA8),   // blue-grey
    "hotel" to Color(0xFF7E7FA0),   // violet
    "errand" to Color(0xFF9A7F5E)   // sand
)

val Sand = Color(0xFFD9C7A8)
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

// Theme-aware accessors — resolve to the active palette. Composable-only.
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
// Note: SurfacePanel intentionally omitted from the accessor list (kept internal).

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
// Modern travel app: Inter everywhere. Retained Playfair/Cinzel for any legacy
// reference, but the active typography below is unified on Inter.
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
    onPrimary = Color(0xFF062E2B),
    primaryContainer = AccentTintDark,
    onPrimaryContainer = AccentDeepDark,
    secondary = SageDark,
    onSecondary = Color(0xFF102118),
    secondaryContainer = SageTintDark,
    onSecondaryContainer = Color(0xFFC9EBD7),
    tertiary = Sand,
    onTertiary = Color(0xFF241E10),
    background = CanvasBgDark,
    onBackground = InkPrimaryDark,
    surface = SurfaceSolidDark,
    onSurface = InkPrimaryDark,
    surfaceVariant = Color(0xFF1C2A27),
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
    onPrimaryContainer = Color(0xFF0C3E42),
    secondary = SageLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SageTintLight,
    onSecondaryContainer = Color(0xFF12360C),
    tertiary = Sand,
    onTertiary = Color(0xFF241E10),
    background = CanvasBgLight,
    onBackground = InkPrimaryLight,
    surface = SurfaceSolidLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = Color(0xFFEFEAE0),
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

// Modern travel typography: Inter for every role. Display/headline set bold with
// tight tracking for a confident, editorial-but-not-serious look.
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1.0).sp),
    displayMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
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
