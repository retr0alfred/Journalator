package dev.retr0alfred.journalator.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import dev.retr0alfred.journalator.R
import dev.retr0alfred.journalator.crypto.PasscodeStrength
import dev.retr0alfred.journalator.ui.PasscodeMode
import dev.retr0alfred.journalator.ui.UnlockFailure
import dev.retr0alfred.journalator.ui.UnlockState
import dev.retr0alfred.journalator.ui.components.ConvergingMark
import dev.retr0alfred.journalator.ui.components.GhostButton
import dev.retr0alfred.journalator.ui.components.JTextField
import dev.retr0alfred.journalator.ui.components.Keypad
import dev.retr0alfred.journalator.ui.components.PasscodeDots
import dev.retr0alfred.journalator.ui.components.PrimaryButton
import dev.retr0alfred.journalator.ui.components.StencilHeader
import dev.retr0alfred.journalator.ui.components.glitchShatter
import dev.retr0alfred.journalator.ui.format.formatDuration
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import dev.retr0alfred.journalator.ui.theme.Motion

/**
 * The only door into the archive.
 *
 * Failure is signalled three ways at once — the words "Wrong passcode", a haptic thud, and
 * the glitch. Colour is never carrying the message on its own, which is what keeps this
 * usable for a colour-blind user and under a reduce-motion setting.
 */
@Composable
fun UnlockScreen(
    state: UnlockState,
    onModeChange: (PasscodeMode) -> Unit,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onPassphrase: (CharArray) -> Unit,
    onSubmit: () -> Unit,
    onBiometric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    val view = LocalView.current
    val motion = JournalatorTheme.motion

    val converge by animateFloatAsState(
        targetValue = state.successProgress,
        animationSpec = motion.tween(Motion.UNLOCK_SUCCESS),
        label = "converge",
    )

    LaunchedEffect(state.glitchTrigger) {
        if (state.glitchTrigger > 0) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    LaunchedEffect(state.successProgress) {
        if (state.successProgress >= 1f) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .glitchShatter(state.glitchTrigger),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConvergingMark(progress = converge)
        Spacer(Modifier.height(20.dp))
        StencilHeader(text = stringResource(R.string.unlock_title))
        Spacer(Modifier.height(10.dp))
        JText(
            text = stringResource(R.string.unlock_body),
            style = type.body,
            color = colors.muted,
        )
        Spacer(Modifier.height(24.dp))

        when (state.mode) {
            PasscodeMode.PIN -> {
                PasscodeDots(
                    entered = state.enteredLength,
                    minimum = PasscodeStrength.MIN_PIN_LENGTH,
                )
                Spacer(Modifier.height(20.dp))
                Keypad(
                    onDigit = onDigit,
                    onDelete = onDelete,
                    onClear = onClear,
                    enabled = !state.lockedOut && !state.busy,
                )
            }

            PasscodeMode.PASSPHRASE -> {
                var value by remember { mutableStateOf("") }
                JTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        onPassphrase(it.toCharArray())
                    },
                    label = stringResource(R.string.unlock_field),
                    password = true,
                    enabled = !state.lockedOut && !state.busy,
                    onImeAction = onSubmit,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        FailureMessage(state = state)
        Spacer(Modifier.height(16.dp))

        PrimaryButton(
            label = stringResource(R.string.unlock_action),
            onClick = onSubmit,
            enabled = !state.lockedOut && !state.busy && state.enteredLength > 0,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GhostButton(
                label = stringResource(
                    if (state.mode == PasscodeMode.PIN) R.string.unlock_switch_to_passphrase
                    else R.string.unlock_switch_to_pin
                ),
                onClick = {
                    onModeChange(
                        if (state.mode == PasscodeMode.PIN) PasscodeMode.PASSPHRASE
                        else PasscodeMode.PIN
                    )
                },
                accent = colors.muted,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.biometricOffered) {
                GhostButton(
                    label = stringResource(R.string.unlock_use_biometric),
                    onClick = onBiometric,
                    enabled = !state.lockedOut,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FailureMessage(state: UnlockState) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type

    when {
        state.lockedOut -> JText(
            text = stringResource(
                R.string.unlock_locked_out,
                formatDuration(state.lockedRemainingMillis),
            ),
            style = type.bodyStrong,
            color = colors.warning,
        )

        state.failure == UnlockFailure.WIPED -> JText(
            text = stringResource(R.string.unlock_wiped),
            style = type.bodyStrong,
            color = colors.warning,
        )

        state.failure == UnlockFailure.BIOMETRIC_FAILED -> JText(
            text = stringResource(
                R.string.unlock_biometric_failed,
                state.failureDetail.orEmpty(),
            ),
            style = type.body,
            color = colors.warning,
        )

        state.failure == UnlockFailure.WRONG_PASSCODE -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            JText(
                text = stringResource(R.string.unlock_wrong),
                style = type.bodyStrong,
                color = colors.warning,
            )
            if (state.attemptsLeft in 1..3) {
                Spacer(Modifier.height(4.dp))
                JText(
                    text = pluralStringResource(
                        R.plurals.unlock_attempts_before_delay,
                        state.attemptsLeft,
                        state.attemptsLeft,
                    ),
                    style = type.meta,
                    color = colors.muted,
                )
            }
        }

        else -> Spacer(Modifier.height(0.dp))
    }
}
