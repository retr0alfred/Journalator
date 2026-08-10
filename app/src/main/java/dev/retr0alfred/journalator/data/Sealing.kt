package dev.retr0alfred.journalator.data

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The rules for turning yesterday's draft into a sealed entry.
 *
 * Sealing happens lazily — on app start and on resume — rather than on a scheduled job.
 *
 * Why: `WorkManager` and `AlarmManager` both mean a background wakeup that Doze can defer
 * for hours, a battery cost the user did not ask for, and a failure mode that is invisible
 * when it happens. The lazy rule cannot silently fail: if the app runs at all, the draft
 * seals, and if the app never runs, the draft is exactly where the user left it.
 */
object Sealing {

    /** A draft seals the moment the local calendar day it belongs to is no longer today. */
    fun shouldSeal(draftDate: LocalDate, today: LocalDate): Boolean = draftDate.isBefore(today)

    /**
     * Guards against a draft dated in the future, which a user can produce by setting the
     * clock forward and back. It is not sealed — it simply waits for its day to pass.
     */
    fun isFuture(draftDate: LocalDate, today: LocalDate): Boolean = draftDate.isAfter(today)

    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone)

    /** ISO-8601 `yyyy-MM-dd`, which sorts lexicographically and never depends on a locale. */
    fun key(date: LocalDate): String = date.toString()

    fun parse(key: String): LocalDate = LocalDate.parse(key)

    /**
     * Word count for the footer and the optional heat-map. Splits on Unicode whitespace, so
     * it behaves the same for Arabic, Japanese punctuation and English prose. Scripts without
     * spaces are counted as one word per run, which is honest about what this number means.
     */
    fun wordCount(text: String): Int {
        var count = 0
        var inWord = false
        for (c in text) {
            if (c.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }

    /** First non-blank line, trimmed, for the archive list preview. */
    fun preview(text: String, maxChars: Int = 120): String {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (line.length <= maxChars) line else line.take(maxChars - 1).trimEnd() + "…"
    }

    /** "DAY 0412" — days elapsed since the first entry, 1-based, zero-padded to four. */
    fun dayNumber(first: LocalDate?, today: LocalDate): String {
        val ordinal = if (first == null) 1L else java.time.temporal.ChronoUnit.DAYS
            .between(first, today) + 1
        return String.format(Locale.US, "%04d", ordinal.coerceAtLeast(1L))
    }
}
