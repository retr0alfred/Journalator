package dev.retr0alfred.journalator.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.retr0alfred.journalator.R

/**
 * Two families, three weights, and that is the whole cap.
 *
 * Chakra Petch Bold is the condensed industrial face used for dates, headers and the seal
 * button — uppercase, tight, and used sparingly so it stays loud. Space Mono carries every
 * word the user actually writes; it had to be pleasant for four hundred words, not just for
 * four, which is why it beat the narrower stencil monospaces.
 *
 * Both are SIL Open Font License. The licence files are committed under `licenses/`.
 */
val DisplayFamily = FontFamily(Font(R.font.chakra_petch_bold, FontWeight.Bold))

val MonoFamily = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

@Immutable
data class JournalatorTypography(
    val display: TextStyle,
    val displaySmall: TextStyle,
    val header: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val meta: TextStyle,
    val button: TextStyle,
    val keypad: TextStyle,
    val link: TextStyle,
)

/**
 * @param scale the in-app text-size preference. It multiplies on top of, and never replaces,
 * the system font scale — a user at 200% who also picks Large gets both.
 */
fun journalatorTypography(scale: Float = 1f) = JournalatorTypography(
    display = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (30 * scale).sp,
        lineHeight = (34 * scale).sp,
        letterSpacing = (-0.01).em,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (20 * scale).sp,
        lineHeight = (24 * scale).sp,
        letterSpacing = 0.02.em,
    ),
    header = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (15 * scale).sp,
        lineHeight = (20 * scale).sp,
        letterSpacing = 0.08.em,
    ),
    body = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (16 * scale).sp,
        // Generous leading: monospace at a tight line height is exhausting to read in bulk.
        lineHeight = (26 * scale).sp,
    ),
    bodyStrong = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (16 * scale).sp,
        lineHeight = (26 * scale).sp,
    ),
    meta = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (12 * scale).sp,
        lineHeight = (16 * scale).sp,
        letterSpacing = 0.04.em,
    ),
    button = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (16 * scale).sp,
        lineHeight = (20 * scale).sp,
        letterSpacing = 0.12.em,
    ),
    keypad = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (26 * scale).sp,
        lineHeight = (30 * scale).sp,
    ),
    link = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (14 * scale).sp,
        lineHeight = (20 * scale).sp,
        textDecoration = TextDecoration.Underline,
    ),
)

val LocalJournalatorTypography = staticCompositionLocalOf { journalatorTypography() }
