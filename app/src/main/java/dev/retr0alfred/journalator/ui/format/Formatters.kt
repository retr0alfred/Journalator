package dev.retr0alfred.journalator.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import dev.retr0alfred.journalator.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Every date and number the user sees goes through here.
 *
 * Nothing in the UI layer is allowed to write `"MMM dd, yyyy"`. A hardcoded pattern is
 * correct in exactly one locale and wrong everywhere else — wrong field order, wrong
 * separators, wrong calendar. `ofLocalizedDate` asks the platform what this user's locale
 * actually does, which is the only answer that stays right when someone changes their
 * language or moves country.
 */
@Composable
private fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val locales = configuration.locales
        if (locales.isEmpty) Locale.getDefault() else locales[0]
    }
}

@Composable
fun formatFullDate(date: LocalDate): String {
    val locale = currentLocale()
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }
    return remember(date, formatter) { date.format(formatter) }
}

@Composable
fun formatMediumDate(date: LocalDate): String {
    val locale = currentLocale()
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    return remember(date, formatter) { date.format(formatter) }
}

@Composable
fun formatMonthYear(year: Int, month: Int): String {
    val locale = currentLocale()
    return remember(year, month, locale) {
        LocalDate.of(year, month, 1)
            .format(DateTimeFormatter.ofPattern("LLLL", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}

@Composable
fun formatNumber(value: Int): String {
    val locale = currentLocale()
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    return remember(value, format) { format.format(value.toLong()) }
}

/** "1 min 5 s" style countdown for the lockout, always with a unit word from resources. */
@Composable
fun formatDuration(millis: Long): String {
    val totalSeconds = ((millis + 999) / 1000).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes == 0 -> stringResource(R.string.duration_seconds, seconds)
        seconds == 0 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_minutes_seconds, minutes, seconds)
    }
}
