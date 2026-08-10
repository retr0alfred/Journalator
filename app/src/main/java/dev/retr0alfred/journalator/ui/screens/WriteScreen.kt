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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.R
import dev.retr0alfred.journalator.ui.WriteState
import dev.retr0alfred.journalator.ui.components.GhostButton
import dev.retr0alfred.journalator.ui.components.HalftoneBand
import dev.retr0alfred.journalator.ui.components.MinimumTouchTarget
import dev.retr0alfred.journalator.ui.components.PaperBlock
import dev.retr0alfred.journalator.ui.components.PrimaryButton
import dev.retr0alfred.journalator.ui.components.StencilHeader
import dev.retr0alfred.journalator.ui.format.formatFullDate
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme

/**
 * Home. The app always opens here, and writing here never asks for a passcode.
 *
 * The keyboard is deliberately not raised on open. A journal that shoves a keyboard in your
 * face the moment you unlock the phone is asking you to perform; leaving the page still for
 * a second and letting one tap start the typing is the calmer default, and it costs the user
 * exactly one tap on the days they did want to type immediately.
 */
@Composable
fun WriteScreen(
    state: WriteState,
    onTextChanged: (String) -> Unit,
    onMoodChanged: (Int?) -> Unit,
    onRequestSeal: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Header(state = state)

        Spacer(Modifier.height(12.dp))
        HalftoneBand(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (state.sealedToday) {
                SealedToday()
            } else {
                PaperBlock(
                    modifier = Modifier.fillMaxSize(),
                    seed = state.date.dayOfYear,
                    tapeTop = true,
                ) {
                    val description = stringResource(R.string.cd_write_field)
                    BasicTextField(
                        value = state.text,
                        onValueChange = onTextChanged,
                        textStyle = type.body.copy(color = colors.onPaper),
                        cursorBrush = SolidColor(colors.accentOnPaper),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .semantics { contentDescription = description },
                        decorationBox = { inner ->
                            if (state.text.isEmpty()) {
                                JText(
                                    text = stringResource(R.string.write_hint),
                                    style = type.body,
                                    color = colors.onPaper.copy(alpha = 0.45f),
                                )
                            }
                            inner()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!state.sealedToday) {
            MoodRow(selected = state.mood, onSelect = onMoodChanged)
            Spacer(Modifier.height(12.dp))
        }

        state.error?.let { detail ->
            JText(
                text = stringResource(R.string.write_save_failed, detail),
                style = type.body,
                color = colors.warning,
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GhostButton(
                label = stringResource(R.string.write_open_archive),
                description = stringResource(R.string.cd_open_archive),
                onClick = onOpenArchive,
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                label = stringResource(R.string.write_settings),
                description = stringResource(R.string.cd_open_settings),
                onClick = onOpenSettings,
                accent = colors.muted,
                modifier = Modifier.weight(1f),
            )
        }

        if (!state.sealedToday) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                label = stringResource(R.string.write_seal),
                description = stringResource(R.string.cd_seal_button),
                onClick = onRequestSeal,
                enabled = state.canSeal,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Header(state: WriteState) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    val words = state.wordCount

    Column {
        StencilHeader(text = formatFullDate(state.date))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            JText(
                text = stringResource(R.string.write_day_counter, state.dayNumber),
                style = type.meta,
                color = colors.secondary,
            )
            Spacer(Modifier.width(14.dp))
            JText(
                text = pluralStringResource(R.plurals.word_count, words, words),
                style = type.meta,
                color = colors.muted,
            )
            Spacer(Modifier.width(14.dp))
            JText(
                text = stringResource(
                    if (state.saving) R.string.write_saving else R.string.write_saved
                ),
                style = type.meta,
                color = if (state.saving) colors.warning else colors.muted,
            )
        }
    }
}

@Composable
private fun SealedToday() {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    PaperBlock(modifier = Modifier.fillMaxSize(), seed = 3, tapeTop = true) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .rotate(-8f)
                    .background(colors.accentOnPaper)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                JText(
                    text = stringResource(R.string.write_sealed_stamp),
                    style = type.display,
                    color = colors.paper,
                )
            }
            Spacer(Modifier.height(20.dp))
            JText(
                text = stringResource(R.string.write_sealed_body),
                style = type.body,
                color = colors.onPaper,
            )
        }
    }
}

/**
 * Five glyphs, no faces. A mood picker made of cartoon expressions tells you how you are
 * supposed to feel about your own day; five abstract marks let you decide what they mean.
 * TalkBack gets a word for each, because a glyph alone is not an accessible label.
 */
@Composable
private fun MoodRow(selected: Int?, onSelect: (Int?) -> Unit) {
    val colors = JournalatorTheme.colors
    val glyphs = remember { listOf("—", "\\", "|", "/", "≡") }
    val labels = listOf(
        R.string.mood_1, R.string.mood_2, R.string.mood_3, R.string.mood_4, R.string.mood_5,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JText(
            text = stringResource(R.string.mood_label),
            style = JournalatorTheme.type.meta,
            color = colors.muted,
        )
        Spacer(Modifier.width(4.dp))
        glyphs.forEachIndexed { index, glyph ->
            val value = index + 1
            val isSelected = selected == value
            val name = stringResource(labels[index])
            val description = stringResource(
                if (isSelected) R.string.cd_mood_selected else R.string.cd_mood_option,
                name,
            )
            Box(
                modifier = Modifier
                    .size(MinimumTouchTarget)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(if (isSelected) null else value) },
                    )
                    .background(if (isSelected) colors.primary else colors.surfaceRaised)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                JText(
                    text = glyph,
                    style = JournalatorTheme.type.displaySmall,
                    color = if (isSelected) colors.onPrimary else colors.onSurface,
                )
            }
        }
    }
}
