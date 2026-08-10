package dev.retr0alfred.journalator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.R
import dev.retr0alfred.journalator.crypto.PasscodeStrength
import dev.retr0alfred.journalator.ui.PasscodeMode
import dev.retr0alfred.journalator.ui.SetupState
import dev.retr0alfred.journalator.ui.SetupStep
import dev.retr0alfred.journalator.ui.components.AssemblingMark
import dev.retr0alfred.journalator.ui.components.GhostButton
import dev.retr0alfred.journalator.ui.components.HalftoneBand
import dev.retr0alfred.journalator.ui.components.JMark
import dev.retr0alfred.journalator.ui.components.JTextField
import dev.retr0alfred.journalator.ui.components.Keypad
import dev.retr0alfred.journalator.ui.components.PasscodeDots
import dev.retr0alfred.journalator.ui.components.PrimaryButton
import dev.retr0alfred.journalator.ui.components.StencilHeader
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme

/**
 * First launch, three steps, one screen each.
 *
 * The copy does not soften the trade. "There is no reset and no recovery" is the literal
 * truth of the design, and a user who does not understand it before they start writing will
 * find out at the worst possible moment. Hence the explicit acknowledgement tick.
 */
@Composable
fun SetupScreen(
    state: SetupState,
    onModeChange: (PasscodeMode) -> Unit,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onPassphrase: (CharArray) -> Unit,
    onAcknowledge: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onBackToChoose: () -> Unit,
    onConfirmDigit: (Char) -> Unit,
    onConfirmDelete: () -> Unit,
    onConfirmClear: () -> Unit,
    onConfirmPassphrase: (CharArray) -> Unit,
    onComplete: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        ProgressRail(step = state.step)
        Spacer(Modifier.height(20.dp))

        when (state.step) {
            SetupStep.CHOOSE -> ChooseStep(
                state = state,
                onModeChange = onModeChange,
                onDigit = onDigit,
                onDelete = onDelete,
                onClear = onClear,
                onPassphrase = onPassphrase,
                onAcknowledge = onAcknowledge,
                onContinue = onContinue,
            )

            SetupStep.CONFIRM -> ConfirmStep(
                state = state,
                onDigit = onConfirmDigit,
                onDelete = onConfirmDelete,
                onClear = onConfirmClear,
                onPassphrase = onConfirmPassphrase,
                onBack = onBackToChoose,
                onComplete = onComplete,
            )

            SetupStep.GENERATING -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StencilHeader(text = stringResource(R.string.setup_generating_title))
                Spacer(Modifier.height(16.dp))
                JText(
                    text = stringResource(R.string.setup_generating_body),
                    style = type.body,
                    color = colors.muted,
                )
                Spacer(Modifier.height(32.dp))
                AssemblingMark()
            }

            SetupStep.DONE -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                JMark(size = 96.dp, split = 2.5f)
                Spacer(Modifier.height(24.dp))
                StencilHeader(text = stringResource(R.string.setup_done_title))
                Spacer(Modifier.height(12.dp))
                JText(
                    text = stringResource(R.string.setup_done_body),
                    style = type.body,
                    color = colors.muted,
                )
                Spacer(Modifier.height(28.dp))
                PrimaryButton(
                    label = stringResource(R.string.setup_start_writing),
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        state.error?.let { detail ->
            Spacer(Modifier.height(16.dp))
            JText(
                text = stringResource(R.string.setup_error, detail),
                style = type.body,
                color = colors.warning,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProgressRail(step: SetupStep) {
    val colors = JournalatorTheme.colors
    val index = when (step) {
        SetupStep.CHOOSE -> 1
        SetupStep.CONFIRM -> 2
        else -> 3
    }
    val description = stringResource(R.string.cd_setup_progress, index, 3)
    Column(modifier = Modifier.semantics { contentDescription = description }) {
        JText(
            text = stringResource(R.string.setup_step_of, index, 3),
            style = JournalatorTheme.type.meta,
            color = colors.muted,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(if (i < index) colors.primary else colors.surfaceRaised)
                )
            }
        }
    }
}

@Composable
private fun ChooseStep(
    state: SetupState,
    onModeChange: (PasscodeMode) -> Unit,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onPassphrase: (CharArray) -> Unit,
    onAcknowledge: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type

    StencilHeader(text = stringResource(R.string.setup_choose_title))
    Spacer(Modifier.height(12.dp))
    JText(text = stringResource(R.string.setup_choose_body), style = type.body, color = colors.onSurface)
    Spacer(Modifier.height(20.dp))

    ModeSwitch(mode = state.mode, onModeChange = onModeChange)
    Spacer(Modifier.height(20.dp))

    PasscodeEntry(
        mode = state.mode,
        enteredLength = state.enteredLength,
        minimum = minimumFor(state.mode),
        onDigit = onDigit,
        onDelete = onDelete,
        onClear = onClear,
        onPassphrase = onPassphrase,
        fieldLabel = stringResource(R.string.setup_field_passcode),
    )

    Spacer(Modifier.height(12.dp))
    JText(
        text = strengthMessage(state.strength, state.mode),
        style = type.meta,
        color = strengthColor(state.strength),
    )

    Spacer(Modifier.height(20.dp))
    HalftoneBand(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = state.acknowledged,
                role = Role.Checkbox,
                onValueChange = onAcknowledge,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = state.acknowledged,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.primary,
                uncheckedColor = colors.muted,
                checkmarkColor = colors.onPrimary,
            ),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        JText(text = stringResource(R.string.setup_ack), style = type.body, color = colors.onSurface)
    }

    Spacer(Modifier.height(20.dp))
    PrimaryButton(
        label = stringResource(R.string.action_continue),
        onClick = onContinue,
        enabled = state.canContinueFromChoose,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConfirmStep(
    state: SetupState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onPassphrase: (CharArray) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type

    StencilHeader(text = stringResource(R.string.setup_confirm_title))
    Spacer(Modifier.height(12.dp))
    JText(text = stringResource(R.string.setup_confirm_body), style = type.body, color = colors.onSurface)
    Spacer(Modifier.height(20.dp))

    PasscodeEntry(
        mode = state.mode,
        enteredLength = state.confirmLength,
        minimum = minimumFor(state.mode),
        onDigit = onDigit,
        onDelete = onDelete,
        onClear = onClear,
        onPassphrase = onPassphrase,
        fieldLabel = stringResource(R.string.setup_field_confirm),
    )

    if (state.mismatch) {
        Spacer(Modifier.height(12.dp))
        JText(
            text = stringResource(R.string.setup_mismatch),
            style = type.body,
            color = colors.warning,
        )
    }

    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        GhostButton(
            label = stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            label = stringResource(R.string.action_continue),
            onClick = onComplete,
            enabled = state.canConfirm,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeSwitch(mode: PasscodeMode, onModeChange: (PasscodeMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GhostButton(
            label = stringResource(R.string.setup_mode_pin),
            onClick = { onModeChange(PasscodeMode.PIN) },
            accent = if (mode == PasscodeMode.PIN) JournalatorTheme.colors.primary
            else JournalatorTheme.colors.muted,
            modifier = Modifier.weight(1f),
        )
        GhostButton(
            label = stringResource(R.string.setup_mode_passphrase),
            onClick = { onModeChange(PasscodeMode.PASSPHRASE) },
            accent = if (mode == PasscodeMode.PASSPHRASE) JournalatorTheme.colors.primary
            else JournalatorTheme.colors.muted,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * PIN entry runs entirely through a `CharArray` in the view model — no `String` ever holds
 * the digits. Passphrase entry cannot make that promise: Android's IME hands text to the app
 * as immutable `String`s, so the last few characters live in the heap until GC regardless of
 * what this app does. That limitation is documented in `docs/SECURITY.md` rather than
 * papered over.
 */
@Composable
private fun PasscodeEntry(
    mode: PasscodeMode,
    enteredLength: Int,
    minimum: Int,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onPassphrase: (CharArray) -> Unit,
    fieldLabel: String,
) {
    when (mode) {
        PasscodeMode.PIN -> {
            PasscodeDots(
                entered = enteredLength,
                minimum = minimum,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            Keypad(onDigit = onDigit, onDelete = onDelete, onClear = onClear)
        }

        PasscodeMode.PASSPHRASE -> {
            var value by remember { mutableStateOf("") }
            JTextField(
                value = value,
                onValueChange = {
                    value = it
                    onPassphrase(it.toCharArray())
                },
                label = fieldLabel,
                password = true,
            )
        }
    }
}

private fun minimumFor(mode: PasscodeMode) = when (mode) {
    PasscodeMode.PIN -> PasscodeStrength.MIN_PIN_LENGTH
    PasscodeMode.PASSPHRASE -> PasscodeStrength.MIN_PASSPHRASE_LENGTH
}

@Composable
private fun strengthMessage(result: PasscodeStrength.Result?, mode: PasscodeMode): String =
    when {
        result == null -> stringResource(
            if (mode == PasscodeMode.PIN) R.string.setup_pin_hint else R.string.setup_passphrase_hint
        )

        result.verdict == PasscodeStrength.Verdict.TOO_SHORT -> stringResource(
            if (mode == PasscodeMode.PIN) R.string.setup_strength_short_pin
            else R.string.setup_strength_short_phrase
        )

        result.verdict == PasscodeStrength.Verdict.PREDICTABLE ->
            stringResource(R.string.setup_strength_repeated)

        result.verdict == PasscodeStrength.Verdict.WEAK ->
            stringResource(R.string.setup_strength_weak)

        else -> stringResource(R.string.setup_strength_ok, magnitudeLabel(result.magnitude))
    }

@Composable
private fun magnitudeLabel(magnitude: PasscodeStrength.Magnitude): String = stringResource(
    when (magnitude) {
        PasscodeStrength.Magnitude.MINUTES -> R.string.strength_minutes
        PasscodeStrength.Magnitude.HOURS -> R.string.strength_hours
        PasscodeStrength.Magnitude.DAYS -> R.string.strength_days
        PasscodeStrength.Magnitude.MONTHS -> R.string.strength_months
        PasscodeStrength.Magnitude.YEARS -> R.string.strength_years
        PasscodeStrength.Magnitude.CENTURIES -> R.string.strength_centuries
    }
)

@Composable
private fun strengthColor(result: PasscodeStrength.Result?) = when (result?.verdict) {
    null -> JournalatorTheme.colors.muted
    PasscodeStrength.Verdict.OK -> JournalatorTheme.colors.secondary
    else -> JournalatorTheme.colors.warning
}
