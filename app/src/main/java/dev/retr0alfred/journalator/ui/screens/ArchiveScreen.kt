package dev.retr0alfred.journalator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.R
import dev.retr0alfred.journalator.data.DecryptedEntry
import dev.retr0alfred.journalator.ui.ArchiveState
import dev.retr0alfred.journalator.ui.ArchiveTab
import dev.retr0alfred.journalator.ui.components.GhostButton
import dev.retr0alfred.journalator.ui.components.HalftoneBand
import dev.retr0alfred.journalator.ui.components.PaperBlock
import dev.retr0alfred.journalator.ui.components.StencilHeader
import dev.retr0alfred.journalator.ui.format.formatFullDate
import dev.retr0alfred.journalator.ui.format.formatMediumDate
import dev.retr0alfred.journalator.ui.format.formatMonthYear
import dev.retr0alfred.journalator.ui.components.JTextField
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * The archive: reachable only after an unlock, and emptied from memory the moment it locks.
 *
 * Entries are read-only here and there is no edit affordance anywhere on the screen. That is
 * a design position, not an omission — a journal you can quietly rewrite is a journal that
 * stops being evidence of anything.
 */
@Composable
fun ArchiveScreen(
    state: ArchiveState,
    onTabChange: (ArchiveTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onYearChange: (Int) -> Unit,
    onOpenEntry: (LocalDate) -> Unit,
    onCloseEntry: () -> Unit,
    onLock: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = state.open
    if (open != null) {
        EntryReader(entry = open, onClose = onCloseEntry, modifier = modifier)
        return
    }

    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StencilHeader(text = stringResource(R.string.archive_title), modifier = Modifier.weight(1f))
            GhostButton(
                label = stringResource(R.string.archive_lock_now),
                description = stringResource(R.string.cd_archive_lock),
                onClick = onLock,
                accent = colors.warning,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            GhostButton(
                label = stringResource(R.string.archive_tab_list),
                onClick = { onTabChange(ArchiveTab.LIST) },
                accent = if (state.tab == ArchiveTab.LIST) colors.primary else colors.muted,
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                label = stringResource(R.string.archive_tab_calendar),
                onClick = { onTabChange(ArchiveTab.CALENDAR) },
                accent = if (state.tab == ArchiveTab.CALENDAR) colors.primary else colors.muted,
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                label = stringResource(R.string.write_settings),
                description = stringResource(R.string.cd_open_settings),
                onClick = onOpenSettings,
                accent = colors.muted,
            )
        }

        Spacer(Modifier.height(12.dp))
        HalftoneBand(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        state.error?.let { detail ->
            JText(
                text = stringResource(R.string.archive_decrypt_failed, detail),
                style = type.body,
                color = colors.warning,
            )
            Spacer(Modifier.height(12.dp))
        }

        when (state.tab) {
            ArchiveTab.LIST -> EntryList(
                state = state,
                onQueryChange = onQueryChange,
                onOpenEntry = onOpenEntry,
                modifier = Modifier.weight(1f),
            )

            ArchiveTab.CALENDAR -> CalendarGrid(
                writtenDates = state.writtenDates,
                year = state.year,
                onYearChange = onYearChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EntryList(
    state: ArchiveState,
    onQueryChange: (String) -> Unit,
    onOpenEntry: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    val visible = state.visible

    Column(modifier = modifier) {
        JTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.archive_search_hint),
        )
        Spacer(Modifier.height(6.dp))
        JText(
            text = stringResource(R.string.archive_search_scope),
            style = type.meta,
            color = colors.muted,
        )
        Spacer(Modifier.height(12.dp))

        when {
            state.entries.isEmpty() -> JText(
                text = stringResource(R.string.archive_empty),
                style = type.body,
                color = colors.muted,
            )

            visible.isEmpty() -> JText(
                text = stringResource(R.string.archive_search_none, state.query),
                style = type.body,
                color = colors.muted,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = visible, key = { it.date.toString() }) { entry ->
                    EntryRow(entry = entry, onClick = { onOpenEntry(entry.date) })
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: DecryptedEntry, onClick: () -> Unit) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    val dateLabel = formatMediumDate(entry.date)
    val words = entry.wordCount
    val wordsLabel = pluralStringResource(R.plurals.word_count, words, words)
    val description = stringResource(R.string.cd_archive_entry, dateLabel, wordsLabel)

    PaperBlock(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        seed = entry.date.dayOfYear,
        tapeTop = false,
        contentPadding = 16.dp,
    ) {
        Column {
            JText(text = dateLabel, style = type.displaySmall, color = colors.accentOnPaper)
            Spacer(Modifier.height(6.dp))
            JText(
                text = entry.preview,
                style = type.body,
                color = colors.onPaper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            JText(text = wordsLabel, style = type.meta, color = colors.onPaper.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun EntryReader(
    entry: DecryptedEntry,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StencilHeader(
                text = formatFullDate(entry.date),
                style = type.displaySmall,
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                label = stringResource(R.string.action_close),
                description = stringResource(R.string.cd_close),
                onClick = onClose,
                accent = colors.muted,
            )
        }
        Spacer(Modifier.height(12.dp))
        PaperBlock(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            seed = entry.date.dayOfYear,
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                JText(text = entry.text, style = type.body, color = colors.onPaper)
                Spacer(Modifier.height(16.dp))
                JText(
                    text = stringResource(R.string.archive_entry_immutable),
                    style = type.meta,
                    color = colors.onPaper.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * The year grid.
 *
 * By default a day is only marked written or not written. Shading by word count needs the
 * counts stored unencrypted, which is a real disclosure — how much you wrote on which day is
 * itself information — so it is opt-in, and the setting says so in plain words.
 */
@Composable
private fun CalendarGrid(
    writtenDates: Set<LocalDate>,
    year: Int,
    onYearChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GhostButton(
                label = "<",
                description = stringResource(R.string.cd_calendar_prev),
                onClick = { onYearChange(year - 1) },
                accent = colors.muted,
            )
            JText(
                text = stringResource(R.string.archive_year, year),
                style = type.display,
                color = colors.onSurface,
            )
            GhostButton(
                label = ">",
                description = stringResource(R.string.cd_calendar_next),
                onClick = { onYearChange(year + 1) },
                accent = colors.muted,
            )
        }
        Spacer(Modifier.height(12.dp))

        for (month in 1..12) {
            MonthBlock(year = year, month = month, writtenDates = writtenDates)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MonthBlock(
    year: Int,
    month: Int,
    writtenDates: Set<LocalDate>,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    val days = remember(year, month) { YearMonth.of(year, month).lengthOfMonth() }

    Column(modifier = Modifier.fillMaxWidth()) {
        JText(text = formatMonthYear(year, month), style = type.header, color = colors.secondary)
        Spacer(Modifier.height(6.dp))
        var day = 1
        while (day <= days) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) {
                    if (day <= days) {
                        DayCell(
                            date = LocalDate.of(year, month, day),
                            written = LocalDate.of(year, month, day) in writtenDates,
                            modifier = Modifier.weight(1f),
                        )
                        day++
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    written: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = JournalatorTheme.colors
    val label = formatMediumDate(date)
    val description = stringResource(
        if (written) R.string.cd_calendar_day_written else R.string.cd_calendar_day_blank,
        label,
    )

    // Deliberately not tappable. Seven columns cannot give every cell a 48 dp target on a
    // narrow phone, and a grid of sub-minimum touch targets fails the accessibility bar. The
    // calendar is a picture of the year; the list next door is how you open a day.
    Box(
        modifier = modifier
            .heightIn(min = 22.dp)
            .aspectRatio(1f)
            .background(if (written) colors.primary else colors.surfaceRaised)
            .border(1.dp, colors.muted.copy(alpha = 0.35f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        JText(
            text = date.dayOfMonth.toString(),
            style = JournalatorTheme.type.meta,
            color = if (written) colors.onPrimary else colors.muted,
        )
    }
}
