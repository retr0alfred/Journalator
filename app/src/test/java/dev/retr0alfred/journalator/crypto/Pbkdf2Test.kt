package dev.retr0alfred.journalator.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Journalator implements PBKDF2-HMAC-SHA256 itself, because Android only gained
 * `PBKDF2WithHmacSHA256` at API 26 and this app supports API 24. An implementation that is
 * not tested against published vectors is just a plausible-looking hash function, so these
 * are the standard PBKDF2-HMAC-SHA256 test vectors, verified byte for byte.
 */
class Pbkdf2Test {

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun derive(password: String, salt: String, iterations: Int, length: Int): String =
        hex(
            Pbkdf2.deriveKeyFromBytes(
                password.toByteArray(StandardCharsets.UTF_8),
                salt.toByteArray(StandardCharsets.UTF_8),
                iterations,
                length,
            )
        )

    @Test
    fun `vector with one iteration`() {
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            derive("password", "salt", 1, 32),
        )
    }

    @Test
    fun `vector with two iterations`() {
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            derive("password", "salt", 2, 32),
        )
    }

    @Test
    fun `vector with four thousand ninety six iterations`() {
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            derive("password", "salt", 4096, 32),
        )
    }

    /** Exercises the multi-block path: 40 bytes needs two HMAC blocks, not one. */
    @Test
    fun `vector spanning more than one hash block`() {
        assertEquals(
            "348c89dbcbd32b2f32d814b8116e84cf2b17347ebc1800181c4e2a1fb8dd53e1c635518c7dac47e9",
            derive("passwordPASSWORDpassword", "saltSALTsaltSALTsaltSALTsaltSALTsalt", 4096, 40),
        )
    }

    @Test
    fun `deriveKey from CharArray matches deriveKey from bytes`() {
        val salt = ByteArray(32) { it.toByte() }
        val fromChars = Pbkdf2.deriveKey("hunter2hunter2".toCharArray(), salt, 1_000)
        val fromBytes = Pbkdf2.deriveKeyFromBytes(
            "hunter2hunter2".toByteArray(StandardCharsets.UTF_8), salt, 1_000, 32,
        )
        assertTrue(fromChars.contentEquals(fromBytes))
    }

    @Test
    fun `non-ascii passphrases are encoded as UTF-8`() {
        val salt = ByteArray(32) { 7 }
        val emojiPhrase = "паспорт-🔐-كلمة"
        val fromChars = Pbkdf2.deriveKey(emojiPhrase.toCharArray(), salt, 500)
        val fromBytes = Pbkdf2.deriveKeyFromBytes(
            emojiPhrase.toByteArray(StandardCharsets.UTF_8), salt, 500, 32,
        )
        assertTrue(fromChars.contentEquals(fromBytes))
    }

    @Test
    fun `calibration stays inside the configured floor and ceiling`() {
        val iterations = Pbkdf2.calibrateIterations(targetMillis = 1, sampleIterations = 200)
        assertTrue(iterations >= Pbkdf2.MIN_ITERATIONS)

        val huge = Pbkdf2.calibrateIterations(targetMillis = 10_000_000, sampleIterations = 200)
        assertTrue(huge <= Pbkdf2.MAX_ITERATIONS)
    }
}
