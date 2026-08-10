package dev.retr0alfred.journalator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The lazy-sealing rule, and the date arithmetic underneath it.
 *
 * Dates are where journalling apps quietly go wrong: a user flies east, or the clocks go
 * back, and suddenly there are two entries for one day or a hole where a day should be.
 */
class SealingTest {

    @Test
    fun `a draft from yesterday seals`() {
        assertTrue(
            Sealing.shouldSeal(
                draftDate = LocalDate.of(2026, 8, 9),
                today = LocalDate.of(2026, 8, 10),
            )
        )
    }

    @Test
    fun `a draft from today does not seal`() {
        val today = LocalDate.of(2026, 8, 10)
        assertFalse(Sealing.shouldSeal(draftDate = today, today = today))
    }

    @Test
    fun `a draft from months ago still seals`() {
        assertTrue(
            Sealing.shouldSeal(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 8, 10))
        )
    }

    @Test
    fun `a draft dated in the future waits rather than sealing`() {
        val today = LocalDate.of(2026, 8, 10)
        val tomorrow = today.plusDays(1)
        assertFalse(Sealing.shouldSeal(tomorrow, today))
        assertTrue(Sealing.isFuture(tomorrow, today))
    }

    @Test
    fun `an entry written at one minute to midnight belongs to that day`() {
        val zone = ZoneId.of("Europe/London")
        val instant = LocalDateTime.of(2026, 8, 10, 23, 59).atZone(zone)
        assertEquals(LocalDate.of(2026, 8, 10), instant.toLocalDate())
        assertFalse(Sealing.shouldSeal(instant.toLocalDate(), LocalDate.of(2026, 8, 10)))
    }

    /**
     * Flying Auckland to Los Angeles crosses the date line westward and repeats a calendar
     * day. The draft's own date is still "today" on landing, so it must not seal and must not
     * create a second row for the same day.
     */
    @Test
    fun `crossing the date line westward does not seal the current draft`() {
        val departure = LocalDateTime.of(2026, 8, 11, 7, 0).atZone(ZoneId.of("Pacific/Auckland"))
        val arrival = departure.withZoneSameInstant(ZoneId.of("America/Los_Angeles"))

        val draftDate = departure.toLocalDate()
        val todayOnArrival = arrival.toLocalDate()

        assertEquals(LocalDate.of(2026, 8, 11), draftDate)
        assertEquals(LocalDate.of(2026, 8, 10), todayOnArrival)
        assertFalse(Sealing.shouldSeal(draftDate, todayOnArrival))
        assertTrue(Sealing.isFuture(draftDate, todayOnArrival))
    }

    /** Eastward is the other risk: a day can be skipped entirely, and that is fine. */
    @Test
    fun `crossing the date line eastward seals the draft and leaves a gap`() {
        val departure = LocalDateTime.of(2026, 8, 10, 22, 0).atZone(ZoneId.of("America/Los_Angeles"))
        val arrival = departure.plusHours(13).withZoneSameInstant(ZoneId.of("Pacific/Auckland"))
        assertTrue(Sealing.shouldSeal(departure.toLocalDate(), arrival.toLocalDate()))
    }

    @Test
    fun `a spring-forward DST transition does not change which day it is`() {
        val zone = ZoneId.of("America/New_York")
        val beforeJump = LocalDateTime.of(2026, 3, 8, 1, 30).atZone(zone)
        val afterJump = beforeJump.plusHours(1)
        assertEquals(beforeJump.toLocalDate(), afterJump.toLocalDate())
        assertFalse(Sealing.shouldSeal(beforeJump.toLocalDate(), afterJump.toLocalDate()))
    }

    @Test
    fun `a autumn fall-back repeated hour stays on the same day`() {
        val zone = ZoneId.of("Europe/Berlin")
        val first = LocalDateTime.of(2026, 10, 25, 2, 30).atZone(zone)
        val second = first.plusHours(1)
        assertEquals(first.toLocalDate(), second.toLocalDate())
    }

    @Test
    fun `date keys are ISO and sort chronologically as text`() {
        val keys = listOf(
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2025, 12, 31),
            LocalDate.of(2026, 10, 9),
        ).map(Sealing::key)

        assertEquals(listOf("2026-01-02", "2025-12-31", "2026-10-09"), keys)
        assertEquals(listOf("2025-12-31", "2026-01-02", "2026-10-09"), keys.sorted())
        assertEquals(LocalDate.of(2026, 1, 2), Sealing.parse("2026-01-02"))
    }

    @Test
    fun `word counting handles runs of whitespace and non-latin scripts`() {
        assertEquals(0, Sealing.wordCount(""))
        assertEquals(0, Sealing.wordCount("   \n\t  "))
        assertEquals(3, Sealing.wordCount("one two three"))
        assertEquals(3, Sealing.wordCount("  one \n\n two \t three  "))
        assertEquals(2, Sealing.wordCount("مرحبا بالعالم"))
        assertEquals(1, Sealing.wordCount("今日はいい天気です"))
    }

    @Test
    fun `preview takes the first non-blank line and truncates politely`() {
        assertEquals("first line", Sealing.preview("\n\n  first line  \nsecond"))
        val long = "x".repeat(400)
        val preview = Sealing.preview(long, maxChars = 20)
        assertEquals(20, preview.length)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun `day number counts from the first entry and is zero padded`() {
        val first = LocalDate.of(2026, 1, 1)
        assertEquals("0001", Sealing.dayNumber(first, first))
        assertEquals("0002", Sealing.dayNumber(first, first.plusDays(1)))
        assertEquals("0412", Sealing.dayNumber(first, first.plusDays(411)))
        assertEquals("0001", Sealing.dayNumber(null, first))
        // A first entry in the future cannot produce a zero or negative day.
        assertEquals("0001", Sealing.dayNumber(first.plusDays(10), first))
    }
}
