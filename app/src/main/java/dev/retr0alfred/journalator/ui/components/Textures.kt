package dev.retr0alfred.journalator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.R
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import dev.retr0alfred.journalator.ui.theme.Tokens

/**
 * Texture is what sells this aesthetic, and texture is also where Compose performance goes
 * to die. The rules followed here, without exception:
 *
 *  - one small tileable bitmap, uploaded once, drawn as a repeating shader;
 *  - scanlines as a 1×4 generated tile rather than several hundred `drawLine` calls;
 *  - halftone dots as vector geometry, never a bitmap;
 *  - no blur, no shadow layers, no per-frame shader, nothing animated.
 *
 * Everything below is static. A frame that draws all of it costs three rectangles.
 */

@Composable
fun rememberGrainBrush(): Brush {
    val grain: ImageBitmap = ImageBitmap.imageResource(R.drawable.tex_grain)
    return remember(grain) {
        ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
    }
}

/**
 * A 1×4 tile: three transparent rows and one barely-there dark row. Tiled, it is a scanline
 * field for the cost of a single texture lookup.
 */
@Composable
fun rememberScanlineBrush(): Brush {
    val tile = remember {
        ImageBitmap(1, 4).also { bitmap ->
            val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
            // Opaque here; the alpha that makes it subtle is applied at draw time, so the
            // strength lives with the caller instead of being baked into the texture.
            val paint = androidx.compose.ui.graphics.Paint().apply { color = Color.Black }
            canvas.drawRect(0f, 0f, 1f, 1f, paint)
        }
    }
    return remember(tile) {
        ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    }
}

/** The wall: flat asphalt, grain over it, scanlines on top. Drawn once per frame, statically. */
@Composable
fun Modifier.concreteWall(
    grainAlpha: Float = 0.07f,
    scanlineAlpha: Float = 0.04f,
): Modifier {
    val surface = JournalatorTheme.colors.surface
    val grain = rememberGrainBrush()
    val scanlines = rememberScanlineBrush()
    return this.drawBehind {
        drawRect(surface)
        drawRect(brush = grain, alpha = grainAlpha, blendMode = BlendMode.Overlay)
        drawRect(brush = scanlines, alpha = scanlineAlpha)
    }
}

/** Grain over an already-painted surface, for paper blocks that need a printed feel. */
@Composable
fun Modifier.paperGrain(alpha: Float = 0.05f): Modifier {
    val grain = rememberGrainBrush()
    return this.drawBehind {
        drawRect(brush = grain, alpha = alpha, blendMode = BlendMode.Multiply)
    }
}

/**
 * A band of halftone dots, as vector geometry. Used as a rule between sections — the
 * printed-poster equivalent of a horizontal line.
 */
@Composable
fun HalftoneBand(
    modifier: Modifier = Modifier,
    color: Color = JournalatorTheme.colors.muted,
    height: Dp = 10.dp,
    dotSpacing: Dp = 6.dp,
) {
    Canvas(modifier = modifier.height(if (height < 4.dp) 4.dp else height)) {
        drawHalftone(color, dotSpacing.toPx())
    }
}

private fun DrawScope.drawHalftone(color: Color, spacingPx: Float) {
    if (spacingPx <= 0f) return
    val rows = (size.height / spacingPx).toInt().coerceAtLeast(1)
    val columns = (size.width / spacingPx).toInt().coerceAtLeast(1)
    for (row in 0..rows) {
        // Rows fade out downwards, which is what makes it read as a printed gradient rather
        // than a grid of dots.
        val fade = 1f - (row.toFloat() / (rows + 1))
        val radius = spacingPx * 0.22f * fade
        if (radius <= 0.2f) continue
        val offsetX = if (row % 2 == 0) 0f else spacingPx / 2f
        for (column in 0..columns) {
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(offsetX + column * spacingPx, row * spacingPx),
                alpha = 0.55f * fade,
            )
        }
    }
}

/** A strip of tape. Static, slightly off-square, because tape never goes on straight. */
@Composable
fun TapeStrip(
    modifier: Modifier = Modifier,
    color: Color = Tokens.Tape,
    skew: Float = 0.06f,
) {
    Canvas(modifier = modifier) {
        val path = androidx.compose.ui.graphics.Path().apply {
            val lift = size.height * skew
            moveTo(0f, lift)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - lift)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color)
    }
}
