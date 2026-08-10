package dev.retr0alfred.journalator.crypto

import kotlin.math.ln
import kotlin.math.pow

/**
 * A blunt, honest strength estimate.
 *
 * Deliberately not a coloured bar: a bar tells the user nothing they can act on. This
 * reports the one number that matters — roughly how long an offline attacker with the
 * config file would need — and names the specific problem when there is one.
 */
object PasscodeStrength {

    const val MIN_PIN_LENGTH = 6
    const val MIN_PASSPHRASE_LENGTH = 8

    enum class Verdict { TOO_SHORT, PREDICTABLE, WEAK, OK }

    data class Result(
        val verdict: Verdict,
        val magnitude: Magnitude,
    )

    /** Order-of-magnitude buckets for the estimated offline cracking time. */
    enum class Magnitude { MINUTES, HOURS, DAYS, MONTHS, YEARS, CENTURIES }

    fun evaluate(passcode: CharArray, numericOnly: Boolean, kdfIterations: Int): Result {
        val minimum = if (numericOnly) MIN_PIN_LENGTH else MIN_PASSPHRASE_LENGTH
        if (passcode.size < minimum) return Result(Verdict.TOO_SHORT, Magnitude.MINUTES)
        if (isPredictable(passcode)) return Result(Verdict.PREDICTABLE, Magnitude.MINUTES)

        val bits = entropyBits(passcode)
        val magnitude = magnitudeFor(bits, kdfIterations)
        val verdict = when (magnitude) {
            Magnitude.MINUTES, Magnitude.HOURS -> Verdict.WEAK
            else -> Verdict.OK
        }
        return Result(verdict, magnitude)
    }

    /** Repeated characters, runs, and the classic keypad walks. */
    fun isPredictable(passcode: CharArray): Boolean {
        if (passcode.isEmpty()) return true
        if (passcode.all { it == passcode[0] }) return true

        var ascending = true
        var descending = true
        for (i in 1 until passcode.size) {
            val delta = passcode[i].code - passcode[i - 1].code
            if (delta != 1) ascending = false
            if (delta != -1) descending = false
        }
        if (ascending || descending) return true

        // A two-character alternation (1212…, abab…) is barely better than a repeat.
        if (passcode.size >= 4) {
            var alternating = true
            for (i in 2 until passcode.size) {
                if (passcode[i] != passcode[i - 2]) { alternating = false; break }
            }
            if (alternating) return true
        }
        return false
    }

    /** log2(alphabet) * length, with the alphabet inferred from what was actually typed. */
    fun entropyBits(passcode: CharArray): Double {
        var digits = false
        var lower = false
        var upper = false
        var symbols = false
        for (c in passcode) {
            when {
                c.isDigit() -> digits = true
                c.isLowerCase() -> lower = true
                c.isUpperCase() -> upper = true
                else -> symbols = true
            }
        }
        var alphabet = 0
        if (digits) alphabet += 10
        if (lower) alphabet += 26
        if (upper) alphabet += 26
        if (symbols) alphabet += 33
        if (alphabet == 0) alphabet = 10
        return passcode.size * (ln(alphabet.toDouble()) / ln(2.0))
    }

    /**
     * Assumes a well-funded attacker doing ten million PBKDF2-HMAC-SHA256 blocks per second
     * on GPUs, divided by the iteration count. Pessimistic by design.
     */
    fun magnitudeFor(entropyBits: Double, kdfIterations: Int): Magnitude {
        val guessesPerSecond = 10_000_000_000.0 / kdfIterations.coerceAtLeast(1)
        val guesses = 2.0.pow(entropyBits.coerceAtMost(128.0)) / 2.0
        val seconds = guesses / guessesPerSecond
        return when {
            seconds < 3_600 -> Magnitude.MINUTES
            seconds < 86_400 -> Magnitude.HOURS
            seconds < 2_592_000 -> Magnitude.DAYS
            seconds < 31_536_000 -> Magnitude.MONTHS
            seconds < 3_153_600_000 -> Magnitude.YEARS
            else -> Magnitude.CENTURIES
        }
    }
}
