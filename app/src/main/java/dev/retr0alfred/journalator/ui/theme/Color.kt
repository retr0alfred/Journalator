package dev.retr0alfred.journalator.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * Street art, not a terminal. Black-plus-one-green is The Matrix; the reference here is
 * spray paint and wheat-paste over concrete, so the base is warm-dark asphalt and the
 * accents are the saturated magenta/cyan/yellow of a poster that has been out in the sun.
 *
 * Every pair used in the UI has been computed against WCAG, not eyeballed. The numbers in
 * the comments are the measured contrast ratios; the two "ink" variants exist precisely
 * because raw magenta and raw cyan fail on paper and passing matters more than purity.
 */
object Tokens {
    val Concrete = Color(0xFF0B0B0D)   // base surface: asphalt, deliberately not pure black
    val Concrete2 = Color(0xFF16161A)  // raised surface
    val Paper = Color(0xFFEDE9DF)      // pasted-poster blocks
    val Ink = Color(0xFF0B0B0D)        // type on paper — 16.3:1
    val Magenta = Color(0xFFFF2D6F)    // primary accent on concrete — 5.5:1
    val Cyan = Color(0xFF22E0FF)       // secondary on concrete — 12.3:1
    val Acid = Color(0xFFC6FF3D)       // once per screen, maximum: the seal
    val Amber = Color(0xFFFFA51F)      // warnings, lockout — 10.0:1 on concrete
    val Muted = Color(0xFF7A7A85)      // metadata — 4.6:1 on concrete, just clears body text

    /** Magenta is 3.0:1 on paper, which fails. This is the same hue at 6.6:1. */
    val MagentaInk = Color(0xFFA3003C)

    /** Cyan is 1.3:1 on paper, which fails badly. This is the same hue at 6.3:1. */
    val CyanInk = Color(0xFF0A5C6B)

    val Tape = Color(0x1FEDE9DF)
    val Scanline = Color(0x0A000000)
}

/**
 * Semantic colours. Composables use these, never [Tokens] directly, so the light variant
 * is a data change rather than a sweep through every screen.
 */
@Immutable
data class JournalatorColors(
    val surface: Color,
    val surfaceRaised: Color,
    val paper: Color,
    val onSurface: Color,
    val onPaper: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val accentOnPaper: Color,
    val linkOnPaper: Color,
    val seal: Color,
    val warning: Color,
    val muted: Color,
    val focusRing: Color,
    val isDark: Boolean,
)

/** Dark-first. This is the design. */
val DarkColors = JournalatorColors(
    surface = Tokens.Concrete,
    surfaceRaised = Tokens.Concrete2,
    paper = Tokens.Paper,
    onSurface = Tokens.Paper,
    onPaper = Tokens.Ink,
    primary = Tokens.Magenta,
    onPrimary = Tokens.Concrete,
    secondary = Tokens.Cyan,
    accentOnPaper = Tokens.MagentaInk,
    linkOnPaper = Tokens.CyanInk,
    seal = Tokens.Acid,
    warning = Tokens.Amber,
    muted = Tokens.Muted,
    focusRing = Tokens.Cyan,
    isDark = true,
)

/**
 * The light variant: paper takes over the whole wall instead of sitting on it. Not a
 * different design, the same one printed the other way round — and it must not be broken
 * just because the app is dark by default.
 */
val LightColors = JournalatorColors(
    surface = Tokens.Paper,
    surfaceRaised = Color(0xFFF7F4EC),
    paper = Color(0xFFFFFDF7),
    onSurface = Tokens.Ink,
    onPaper = Tokens.Ink,
    primary = Tokens.MagentaInk,
    onPrimary = Tokens.Paper,
    secondary = Tokens.CyanInk,
    accentOnPaper = Tokens.MagentaInk,
    linkOnPaper = Tokens.CyanInk,
    seal = Color(0xFF3F6B00),          // acid at 5.0:1 on paper; still the one loud moment
    warning = Color(0xFF8A4B00),       // amber at 6.1:1 on paper
    muted = Color(0xFF5A5A63),         // 6.4:1 on paper
    focusRing = Tokens.CyanInk,
    isDark = false,
)

val LocalJournalatorColors = staticCompositionLocalOf { DarkColors }
