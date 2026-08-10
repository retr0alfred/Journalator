package dev.retr0alfred.journalator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme

/**
 * A single-line field, styled by hand rather than inherited from Material.
 *
 * Built on `BasicTextField` so that nothing of Material's own chrome — the container tint,
 * the floating label, the indicator line — can bring a default colour into a screen that is
 * supposed to look like it was printed.
 */
@Composable
fun JTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String = label,
    password: Boolean = false,
    numeric: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
) {
    val colors = JournalatorTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = JournalatorTheme.type.body.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            interactionSource = interactionSource,
            visualTransformation = if (password) PasswordVisualTransformation()
            else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    numeric -> KeyboardType.NumberPassword
                    password -> KeyboardType.Password
                    else -> KeyboardType.Text
                },
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onGo = { onImeAction() },
                onNext = { onImeAction() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinimumTouchTarget)
                .focusRing(interactionSource)
                .border(1.dp, colors.muted.copy(alpha = 0.6f))
                .background(colors.surfaceRaised)
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .semantics { contentDescription = description },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    JText(
                        text = label,
                        style = JournalatorTheme.type.body,
                        color = colors.muted,
                    )
                }
                inner()
            },
        )
    }
}
