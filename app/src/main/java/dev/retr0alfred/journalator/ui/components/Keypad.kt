package dev.retr0alfred.journalator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.R
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import dev.retr0alfred.journalator.ui.theme.Motion

/**
 * The unlock keypad.
 *
 * Every key is at least 56 dp, comfortably over the 48 dp floor, because this is the one
 * control people use while walking. Keys carry an explicit `contentDescription` and the
 * `Button` role, so TalkBack reads "Digit 4, button" rather than a bare number.
 */
@Composable
fun Keypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { digit ->
                    KeypadKey(
                        label = digit.toString(),
                        description = stringResource(R.string.cd_keypad_digit, digit.toString()),
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KeypadKey(
                label = "×",
                description = stringResource(R.string.cd_keypad_clear),
                enabled = enabled,
                accent = JournalatorTheme.colors.muted,
                modifier = Modifier.weight(1f),
                onClick = onClear,
            )
            KeypadKey(
                label = "0",
                description = stringResource(R.string.cd_keypad_digit, "0"),
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onDigit('0') },
            )
            KeypadKey(
                label = "⌫",
                description = stringResource(R.string.cd_keypad_delete),
                enabled = enabled,
                accent = JournalatorTheme.colors.muted,
                modifier = Modifier.weight(1f),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = JournalatorTheme.colors.onSurface,
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
            .defaultMinSize(minHeight = 56.dp)
            .focusRing(interactionSource)
            .border(1.dp, JournalatorTheme.colors.muted.copy(alpha = 0.45f))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        JText(
            text = label,
            style = JournalatorTheme.type.keypad,
            color = if (enabled) accent else JournalatorTheme.colors.muted,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * The filled/empty dots above the keypad.
 *
 * Colour is never the only signal: the spoken description states the count outright, so a
 * user who cannot see the dots still knows how many digits are in.
 */
@Composable
fun PasscodeDots(
    entered: Int,
    minimum: Int,
    modifier: Modifier = Modifier,
) {
    val colors = JournalatorTheme.colors
    val description =
        pluralStringResource(R.plurals.cd_passcode_filled, minimum, entered, minimum)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val slots = maxOf(minimum, entered)
        repeat(slots) { index ->
            Box(
                modifier = Modifier
                    .size(if (index < entered) 12.dp else 8.dp)
                    .border(
                        width = if (index < entered) 6.dp else 1.dp,
                        color = if (index < entered) colors.primary else colors.muted,
                    )
            )
        }
    }
}
