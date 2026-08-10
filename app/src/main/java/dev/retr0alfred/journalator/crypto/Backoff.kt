package dev.retr0alfred.journalator.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * How long the archive stays shut after a run of wrong passcodes.
 *
 * Persisted to disk, not held in memory, because a backoff that a force-stop resets is not a
 * backoff at all — it is a speed bump that an attacker clears by killing the app.
 */
data class FailureState(
    val consecutiveFailures: Int = 0,
    val lockedUntilEpochMillis: Long = 0L,
) {
    fun remainingMillis(nowEpochMillis: Long): Long =
        (lockedUntilEpochMillis - nowEpochMillis).coerceAtLeast(0L)

    fun isLockedOut(nowEpochMillis: Long): Boolean = remainingMillis(nowEpochMillis) > 0L
}

object Backoff {

    /** Attempts one to four are free; a typo should not cost the user a minute. */
    const val FREE_ATTEMPTS = 4

    /** Delay applied after failure number FREE_ATTEMPTS + index, capped at the last entry. */
    val DELAYS_MILLIS = longArrayOf(
        5_000L,      // 5th failure
        15_000L,     // 6th
        60_000L,     // 7th
        300_000L,    // 8th
        1_800_000L,  // 9th and every one after it
    )

    val MAX_DELAY_MILLIS: Long = DELAYS_MILLIS.last()

    fun delayForFailureCount(failureCount: Int): Long {
        if (failureCount <= FREE_ATTEMPTS) return 0L
        val index = (failureCount - FREE_ATTEMPTS - 1).coerceAtMost(DELAYS_MILLIS.lastIndex)
        return DELAYS_MILLIS[index]
    }

    fun onFailure(previous: FailureState, nowEpochMillis: Long): FailureState {
        val failures = previous.consecutiveFailures + 1
        val delay = delayForFailureCount(failures)
        return FailureState(
            consecutiveFailures = failures,
            lockedUntilEpochMillis = if (delay == 0L) 0L else nowEpochMillis + delay,
        )
    }

    fun onSuccess(): FailureState = FailureState()

    fun attemptsBeforeDelay(state: FailureState): Int =
        (FREE_ATTEMPTS - state.consecutiveFailures).coerceAtLeast(0)
}

/**
 * A three-field record in its own file. Separate from the vault header so a write during a
 * failed unlock can never damage the wrapped private key.
 */
class FailureStateStore(private val file: File) {

    fun read(): FailureState =
        if (!file.exists()) {
            FailureState()
        } else {
            try {
                DataInputStream(ByteArrayInputStream(file.readBytes())).use { data ->
                    val version = data.readInt()
                    if (version != VERSION) FailureState()
                    else FailureState(data.readInt(), data.readLong())
                }
            } catch (e: Exception) {
                // A damaged counter must fail closed-ish but usable: treat it as a clean slate
                // rather than bricking the app. The vault itself is unaffected either way.
                FailureState()
            }
        }

    fun write(state: FailureState) {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeInt(VERSION)
            data.writeInt(state.consecutiveFailures)
            data.writeLong(state.lockedUntilEpochMillis)
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(out.toByteArray())
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    private companion object {
        const val VERSION = 1
    }
}
