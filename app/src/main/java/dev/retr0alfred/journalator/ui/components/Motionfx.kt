package dev.retr0alfred.journalator.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import dev.retr0alfred.journalator.ui.theme.Motion
import dev.retr0alfred.journalator.ui.theme.Tokens

/**
 * Horizontal glitch shatter for a wrong passcode: 160 ms, four hard sideways jumps, plus a
 * pair of misregistered colour bars torn across the middle.
 *
 * Runs off [trigger]; incrementing it replays the effect. Under reduce motion it does
 * nothing at all, and the failure is carried entirely by the error text and the haptic —
 * which is why the message can never be "colour only".
 */
@Composable
fun Modifier.glitchShatter(trigger: Int): Modifier {
    val motion = JournalatorTheme.motion
    val shift = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger == 0 || !motion.enabled) return@LaunchedEffect
        shift.snapTo(0f)
        shift.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = Motion.UNLOCK_FAILURE
                0f at 0
                -14f at 30
                11f at 65
                -6f at 100
                2f at 130
                0f at Motion.UNLOCK_FAILURE
            },
        )
    }

    val magenta = Tokens.Magenta
    val cyan = Tokens.Cyan
    val amount = shift.value

    return this
        .graphicsLayer { translationX = amount }
        .drawWithContent {
            drawContent()
            if (amount == 0f) return@drawWithContent
            val band = size.height * 0.18f
            drawRect(
                color = magenta,
                topLeft = Offset(amount * 1.6f, size.height * 0.36f),
                size = Size(size.width, band * 0.25f),
                alpha = 0.55f,
            )
            drawRect(
                color = cyan,
                topLeft = Offset(-amount * 1.9f, size.height * 0.58f),
                size = Size(size.width, band * 0.18f),
                alpha = 0.5f,
            )
        }
}

/**
 * Unlock success: the registration error converges to sharp over 240 ms.
 *
 * @param progress 0 while locked, animated to 1 by the caller on success.
 */
@Composable
fun ConvergingMark(progress: Float, modifier: Modifier = Modifier) {
    JMark(modifier = modifier, split = (1f - progress.coerceIn(0f, 1f)) * 10f + 2.5f)
}

/**
 * The signature moment: 420 ms, stamp then tear then stack.
 *
 * This is the only animation in the app over 250 ms and the only place `--acid` appears.
 * Everything else was kept quiet so that this one lands. It is purely decorative — the seal
 * itself has already been written to the database before this plays — so an interruption,
 * a rotation or a process death loses nothing but the flourish.
 */
@Composable
fun SealOverlay(
    visible: Boolean,
    label: String,
    onFinished: () -> Unit,
) {
    val motion = JournalatorTheme.motion
    val progress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(motion.duration(Motion.SEAL), easing = Motion.SealEasing))
        onFinished()
    }

    if (!visible) return

    val t = progress.value
    // Three beats inside one curve: the stamp lands, the page tears, the stack takes it.
    val stampScale = if (t < 0.35f) 2.2f - (t / 0.35f) * 1.2f else 1f
    val tear = ((t - 0.35f) / 0.30f).coerceIn(0f, 1f)
    val stack = ((t - 0.65f) / 0.35f).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = stampScale
                    scaleY = stampScale
                    rotationZ = -6f + tear * 3f
                    translationY = stack * -120f
                    alpha = 1f - stack * 0.9f
                }
        ) {
            // A dark stamp plate, not a paper one: acid on paper measures 1.0:1 and would be
            // invisible. Acid on concrete is 16.6:1, and a rubber stamp is dark anyway.
            PaperBlock(
                seed = 7,
                tapeTop = false,
                contentPadding = 18.dp,
                background = JournalatorTheme.colors.surface,
            ) {
                JText(
                    text = label,
                    style = JournalatorTheme.type.display,
                    color = JournalatorTheme.colors.seal,
                )
            }
        }
    }
}
