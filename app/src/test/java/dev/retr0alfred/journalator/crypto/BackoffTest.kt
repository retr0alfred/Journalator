package dev.retr0alfred.journalator.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackoffTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the first four attempts cost nothing`() {
        var state = FailureState()
        repeat(4) { state = Backoff.onFailure(state, NOW) }
        assertEquals(4, state.consecutiveFailures)
        assertFalse(state.isLockedOut(NOW))
        assertEquals(0, Backoff.attemptsBeforeDelay(state))
    }

    @Test
    fun `the schedule escalates and then caps at thirty minutes`() {
        val expected = listOf(
            5 to 5_000L,
            6 to 15_000L,
            7 to 60_000L,
            8 to 300_000L,
            9 to 1_800_000L,
            10 to 1_800_000L,
            50 to 1_800_000L,
        )
        expected.forEach { (failures, delay) ->
            assertEquals(
                "failure #$failures",
                delay,
                Backoff.delayForFailureCount(failures),
            )
        }
    }

    @Test
    fun `a lockout expires exactly when it should`() {
        var state = FailureState()
        repeat(5) { state = Backoff.onFailure(state, NOW) }
        assertTrue(state.isLockedOut(NOW))
        assertTrue(state.isLockedOut(NOW + 4_999))
        assertFalse(state.isLockedOut(NOW + 5_000))
        assertEquals(5_000L, state.remainingMillis(NOW))
    }

    @Test
    fun `a successful unlock resets the counter`() {
        var state = FailureState()
        repeat(7) { state = Backoff.onFailure(state, NOW) }
        state = Backoff.onSuccess()
        assertEquals(0, state.consecutiveFailures)
        assertFalse(state.isLockedOut(NOW))
        assertEquals(Backoff.FREE_ATTEMPTS, Backoff.attemptsBeforeDelay(state))
    }

    /**
     * The point of persisting the state: force-stopping the app must not hand an attacker a
     * fresh set of free attempts. A new store over the same file is exactly what a restarted
     * process sees.
     */
    @Test
    fun `the lockout survives simulated process death`() {
        val file = File(folder.root, "failures.bin")
        var state = FailureState()
        repeat(6) { state = Backoff.onFailure(state, NOW) }
        FailureStateStore(file).write(state)

        val afterRestart = FailureStateStore(file).read()
        assertEquals(6, afterRestart.consecutiveFailures)
        assertEquals(15_000L, afterRestart.remainingMillis(NOW))
        assertTrue(afterRestart.isLockedOut(NOW))
    }

    @Test
    fun `a missing or damaged counter file reads as a clean slate`() {
        val missing = File(folder.root, "absent.bin")
        assertEquals(FailureState(), FailureStateStore(missing).read())

        val damaged = File(folder.root, "damaged.bin")
        damaged.writeBytes(byteArrayOf(9, 9, 9))
        assertEquals(FailureState(), FailureStateStore(damaged).read())
    }

    @Test
    fun `clearing removes the file`() {
        val file = File(folder.root, "failures.bin")
        val store = FailureStateStore(file)
        store.write(FailureState(3, NOW))
        store.clear()
        assertFalse(file.exists())
        assertEquals(FailureState(), store.read())
    }

    private companion object {
        /** A fixed instant, so the tests never depend on the clock. */
        const val NOW = 1_770_000_000_000L
    }
}
