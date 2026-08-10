package dev.retr0alfred.journalator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme

/**
 * A torn edge, generated rather than drawn.
 *
 * The jitter comes from a tiny linear congruential generator seeded by [seed], so a given
 * block tears the same way on every recomposition, every rotation and every process restart.
 * Random-per-frame edges would shimmer, and a bitmap of a torn edge would either pixelate or
 * cost more than the whole rest of the screen.
 */
class TornEdgeShape(
    private val seed: Int,
    private val tearDepth: Dp = 5.dp,
    private val teeth: Int = 26,
    private val tornTop: Boolean = true,
    private val tornBottom: Boolean = true,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val depth = with(density) { tearDepth.toPx() }
        val path = Path()
        var state = seed * 2654435761L.toInt() + 1

        fun next(): Float {
            // LCG constants from Numerical Recipes; adequate for jitter, and deterministic.
            state = state * 1664525 + 1013904223
            return ((state ushr 8) and 0xFFFF) / 65535f
        }

        val steps = teeth.coerceAtLeast(4)
        val stepWidth = size.width / steps

        path.moveTo(0f, if (tornTop) next() * depth else 0f)
        for (i in 1..steps) {
            val y = if (tornTop) next() * depth else 0f
            path.lineTo(i * stepWidth, y)
        }
        path.lineTo(size.width, size.height - if (tornBottom) next() * depth else 0f)
        for (i in steps - 1 downTo 0) {
            val y = size.height - if (tornBottom) next() * depth else 0f
            path.lineTo(i * stepWidth, y)
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is TornEdgeShape && other.seed == seed && other.teeth == teeth &&
            other.tearDepth == tearDepth && other.tornTop == tornTop &&
            other.tornBottom == tornBottom

    override fun hashCode(): Int = seed * 31 + teeth
}

/**
 * The reading and writing surface: a block of off-white paper pasted onto the concrete.
 *
 * Every long-form text in the app sits on one of these. That is the single most important
 * legibility decision in the design — light type on a dark wall is fine for a date and
 * hostile for four hundred words.
 */
@Composable
fun PaperBlock(
    modifier: Modifier = Modifier,
    seed: Int = 1,
    tapeTop: Boolean = true,
    contentPadding: Dp = 20.dp,
    background: Color = JournalatorTheme.colors.paper,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(TornEdgeShape(seed))
                .background(background)
                .paperGrain()
                .padding(contentPadding),
            content = content,
        )
        if (tapeTop) {
            // Deterministic nudge from the seed: the tape is crooked, but always crooked the
            // same way, so it never twitches between frames.
            val nudge = ((seed * 37) % 19) - 9
            TapeStrip(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = nudge.dp, y = (-8).dp)
                    .rotate(if (nudge % 2 == 0) -2f else 2f)
                    .width(96.dp)
                    .height(20.dp),
            )
        }
    }
}
