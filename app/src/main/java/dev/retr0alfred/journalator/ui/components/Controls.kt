package dev.retr0alfred.journalator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import dev.retr0alfred.journalator.ui.theme.Motion

/** The platform minimum, and the floor for every interactive element in this app. */
val MinimumTouchTarget: Dp = 48.dp

/**
 * A visible 2 dp cyan outline whenever the element holds keyboard or D-pad focus.
 *
 * Compose gives no focus indicator by default, which makes an app unusable with a keyboard
 * or a TV remote and fails WCAG 2.4.7 outright. Every control in this app routes through
 * here.
 */
@Composable
fun Modifier.focusRing(interactionSource: MutableInteractionSource): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val ring = JournalatorTheme.colors.focusRing
    return this.border(
        BorderStroke(if (focused) 2.dp else 0.dp, if (focused) ring else Color.Transparent),
        RectangleShape,
    )
}

/**
 * The one loud button. Magenta plate, stencil type, 0.97 press scale over 90 ms.
 *
 * @param description spoken by TalkBack in place of the label when the label alone is not
 * enough to know what will happen.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    containerColor: Color = JournalatorTheme.colors.primary,
    contentColor: Color = JournalatorTheme.colors.onPrimary,
) {
    val motion = JournalatorTheme.motion
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = motion.tween(Motion.BUTTON_PRESS, Motion.PressEasing),
        label = "press",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = MinimumTouchTarget)
            .focusRing(interactionSource)
            .background(if (enabled) containerColor else JournalatorTheme.colors.surfaceRaised)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .then(
                if (description != null) Modifier.semantics { contentDescription = description }
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        JText(
            text = label,
            style = JournalatorTheme.type.button,
            color = if (enabled) contentColor else JournalatorTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier = if (description != null) Modifier.clearAndSetSemantics { } else Modifier,
        )
    }
}

/** A secondary action: outline only, so it never competes with the magenta plate. */
@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    accent: Color = JournalatorTheme.colors.secondary,
) {
    val motion = JournalatorTheme.motion
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = motion.tween(Motion.BUTTON_PRESS, Motion.PressEasing),
        label = "press",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = MinimumTouchTarget)
            .focusRing(interactionSource)
            .border(BorderStroke(1.dp, if (enabled) accent else JournalatorTheme.colors.muted))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(
                if (description != null) Modifier.semantics { contentDescription = description }
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        JText(
            text = label,
            style = JournalatorTheme.type.button,
            color = if (enabled) accent else JournalatorTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier = if (description != null) Modifier.clearAndSetSemantics { } else Modifier,
        )
    }
}
