package dev.retr0alfred.journalator.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The motion budget, in one place so it can be audited at a glance.
 *
 * Nothing here exceeds 420 ms, and the only thing that reaches 420 ms is the seal — the one
 * moment the whole app stays quiet for. Everything else is under a quarter of a second,
 * because a journalling app that makes you wait for its own animations is a toy.
 *
 * When [enabled] is false — the in-app Reduce Motion switch, or a system animator scale of
 * zero — every duration collapses to zero and the same transitions become instant swaps.
 * The reduce-motion path is not a separate code path; it is the same code with [duration]
 * returning 0, which is why it cannot drift out of sync with the animated one.
 */
@Immutable
data class Motion(val enabled: Boolean) {

    fun duration(millis: Int): Int = if (enabled) millis else 0

    fun <T> tween(millis: Int, easing: Easing = FastOutSlowInEasing) =
        androidx.compose.animation.core.tween<T>(durationMillis = duration(millis), easing = easing)

    companion object {
        const val SCREEN_TRANSITION = 180
        const val BUTTON_PRESS = 90
        const val HEADER_REVEAL = 220
        const val UNLOCK_SUCCESS = 240
        const val UNLOCK_FAILURE = 160
        const val SEAL = 420

        val ScreenEasing: Easing = FastOutSlowInEasing
        val PressEasing: Easing = LinearOutSlowInEasing

        /** Stamp lands hard, tear drags, stack settles. Not a stock curve. */
        val SealEasing: Easing = CubicBezierEasing(0.16f, 0.9f, 0.2f, 1f)
    }
}

val LocalMotion = staticCompositionLocalOf { Motion(enabled = true) }

/** Convenience for the common "fade this in over the screen-transition duration" case. */
fun Motion.screenTween() = tween<Float>(Motion.SCREEN_TRANSITION, Motion.ScreenEasing)
