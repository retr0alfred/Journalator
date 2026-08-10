package dev.retr0alfred.journalator.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2-HMAC-SHA256, implemented directly on top of [Mac].
 *
 * Why not `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`: that algorithm only
 * arrived on Android at API 26. Journalator supports API 24, where the platform offers
 * PBKDF2 with HMAC-SHA1 and nothing better. `Mac.getInstance("HmacSHA256")` has existed
 * since API 1, so deriving the KDF from it is both available everywhere and bit-identical
 * on the JVM, which is what lets the unit tests verify it against the published vectors.
 */
object Pbkdf2 {

    const val HASH_LENGTH_BYTES = 32
    private const val HMAC = "HmacSHA256"

    /** Minimum iteration count. Never go below this, whatever a device benchmarks at. */
    const val MIN_ITERATIONS = 210_000

    /** Upper bound, so a fast phone does not saddle the user with a multi-second unlock. */
    const val MAX_ITERATIONS = 600_000

    const val SALT_BYTES = 32

    const val KEK_BYTES = 32

    /**
     * @param password wiped by the caller, never by this function.
     * @return a freshly allocated key of [keyBytes] bytes; the caller owns and wipes it.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyBytes: Int = KEK_BYTES,
    ): ByteArray {
        require(iterations > 0) { "iterations must be positive" }
        require(keyBytes > 0) { "keyBytes must be positive" }
        val passwordBytes = SecureMemory.toUtf8(password)
        try {
            return deriveKeyFromBytes(passwordBytes, salt, iterations, keyBytes)
        } finally {
            SecureMemory.wipe(passwordBytes)
        }
    }

    fun deriveKeyFromBytes(
        passwordBytes: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyBytes: Int,
    ): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(passwordBytes, HMAC))

        val output = ByteArray(keyBytes)
        val blockCount = (keyBytes + HASH_LENGTH_BYTES - 1) / HASH_LENGTH_BYTES
        val accumulator = ByteArray(HASH_LENGTH_BYTES)
        var written = 0

        for (block in 1..blockCount) {
            mac.reset()
            mac.update(salt)
            mac.update(byteArrayOf(
                (block ushr 24).toByte(),
                (block ushr 16).toByte(),
                (block ushr 8).toByte(),
                block.toByte(),
            ))
            var u = mac.doFinal()
            System.arraycopy(u, 0, accumulator, 0, HASH_LENGTH_BYTES)

            for (round in 2..iterations) {
                mac.reset()
                u = mac.doFinal(u)
                for (i in 0 until HASH_LENGTH_BYTES) {
                    accumulator[i] = (accumulator[i].toInt() xor u[i].toInt()).toByte()
                }
            }
            SecureMemory.wipe(u)

            val take = minOf(HASH_LENGTH_BYTES, keyBytes - written)
            System.arraycopy(accumulator, 0, output, written, take)
            written += take
        }
        SecureMemory.wipe(accumulator)
        return output
    }

    /**
     * Picks an iteration count that costs roughly [targetMillis] on *this* device, clamped
     * to [MIN_ITERATIONS]…[MAX_ITERATIONS].
     *
     * A fixed constant is the wrong answer on a ten-year horizon: it is punishing on a 2018
     * budget phone and insultingly cheap on a 2030 one. Measuring a small sample and scaling
     * keeps the unlock cost roughly constant in seconds, which is the thing the user feels
     * and the thing an attacker pays.
     */
    fun calibrateIterations(
        targetMillis: Long = 650L,
        sampleIterations: Int = 20_000,
        nanoTime: () -> Long = System::nanoTime,
    ): Int {
        val probeSalt = ByteArray(SALT_BYTES) { it.toByte() }
        val probePassword = ByteArray(16) { (it * 7).toByte() }
        val start = nanoTime()
        SecureMemory.wipe(
            deriveKeyFromBytes(probePassword, probeSalt, sampleIterations, KEK_BYTES)
        )
        val elapsedNanos = (nanoTime() - start).coerceAtLeast(1L)
        val nanosPerIteration = elapsedNanos.toDouble() / sampleIterations
        val target = targetMillis * 1_000_000.0
        val scaled = (target / nanosPerIteration).toLong()
        return scaled.coerceIn(MIN_ITERATIONS.toLong(), MAX_ITERATIONS.toLong()).toInt()
    }
}
