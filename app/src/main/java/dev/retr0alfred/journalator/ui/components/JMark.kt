package dev.retr0alfred.journalator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.R
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import kotlin.math.abs

/**
 * The mark: an original stencil "J", cut into four plates with bridges between them, printed
 * with a deliberate registration error so the magenta and cyan plates do not quite line up.
 *
 * It is drawn from geometry rather than shipped as artwork, and it borrows nothing from
 * Ubisoft or Watch Dogs — same visual language, entirely different glyph.
 */
private val PLATES = listOf(
    Rect(40f, 26f, 78f, 38f),   // top bar
    Rect(60f, 41f, 72f, 64f),   // stem, bridged away from the bar
    Rect(36f, 55f, 48f, 66f),   // hook riser
    Rect(36f, 68f, 72f, 80f),   // hook foot
)

private const val VIEWPORT = 108f

@Composable
fun JMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    /** Registration error between the plates, in viewport units. 0 is perfectly sharp. */
    split: Float = 2.5f,
    /** 0 = fully scattered fragments, 1 = assembled. */
    assembly: Float = 1f,
) {
    val colors = JournalatorTheme.colors
    val description = stringResource(R.string.cd_app_mark)
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
    ) {
        drawMark(colors.secondary, colors.primary, split, assembly)
    }
}

/**
 * The key-generation state. The only looping animation in the app, and it exists because
 * generating a 3072-bit key takes seconds and a frozen screen reads as a crash.
 */
@Composable
fun AssemblingMark(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val motion = JournalatorTheme.motion
    if (!motion.enabled) {
        JMark(modifier = modifier, size = size, split = 2.5f, assembly = 1f)
        return
    }
    val transition = rememberInfiniteTransition(label = "assemble")
    val assembly by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "assembly",
    )
    JMark(modifier = modifier, size = size, split = 2.5f + (1f - assembly) * 6f, assembly = assembly)
}

private fun DrawScope.drawMark(cyan: Color, magenta: Color, split: Float, assembly: Float) {
    val scale = size.minDimension / VIEWPORT
    val eased = assembly.coerceIn(0f, 1f)

    fun plateOffset(index: Int): Offset {
        // Fragments come in from alternating corners; deterministic, so it never jitters.
        val scatter = (1f - eased) * 26f
        val dx = if (index % 2 == 0) -scatter else scatter
        val dy = if (index < 2) -scatter else scatter
        return Offset(dx, dy)
    }

    fun drawPlates(color: Color, dx: Float, dy: Float, alpha: Float) {
        PLATES.forEachIndexed { index, rect ->
            val offset = plateOffset(index)
            drawRect(
                color = color,
                topLeft = Offset(
                    (rect.left + dx + offset.x) * scale,
                    (rect.top + dy + offset.y) * scale,
                ),
                size = Size(rect.width * scale, rect.height * scale),
                alpha = alpha,
            )
        }
    }

    if (abs(split) > 0.01f) {
        drawPlates(cyan, -split, split, 0.85f)
    }
    drawPlates(magenta, 0f, 0f, 1f)
}
