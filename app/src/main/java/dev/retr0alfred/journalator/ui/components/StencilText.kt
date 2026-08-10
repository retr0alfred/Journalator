package dev.retr0alfred.journalator.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import dev.retr0alfred.journalator.ui.theme.Motion

/**
 * A stencil header that types itself in, character by character, over 220 ms.
 *
 * Applied to headers only, never to body text: watching your own paragraph appear letter by
 * letter would be intolerable, and a screen reader would be read a moving target. The
 * semantics node always carries the finished string, so TalkBack announces the header once,
 * complete, regardless of what the pixels are doing.
 */
@Composable
fun StencilHeader(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = JournalatorTheme.type.display,
    color: Color = JournalatorTheme.colors.onSurface,
    reveal: Boolean = true,
    isHeading: Boolean = true,
) {
    val motion = JournalatorTheme.motion
    val upper = remember(text) { text.uppercase() }

    var started by remember(upper) { mutableStateOf(false) }
    LaunchedEffect(upper) { started = true }

    val shown by animateIntAsState(
        targetValue = if (started || !reveal) upper.length else 0,
        animationSpec = motion.tween(Motion.HEADER_REVEAL),
        label = "reveal",
    )

    val visible = if (!reveal || !motion.enabled) upper else upper.take(shown)

    JText(
        text = visible,
        style = style,
        color = color,
        modifier = modifier.semantics {
            contentDescription = upper
            if (isHeading) heading()
        },
    )
}

/** A label whose spoken text differs from what is drawn — dates, counters, glyph rows. */
@Composable
fun LabelledText(
    text: String,
    spoken: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    JText(
        text = text,
        style = style,
        color = color,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}
