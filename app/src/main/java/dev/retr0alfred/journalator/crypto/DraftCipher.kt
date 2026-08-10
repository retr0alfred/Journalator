package dev.retr0alfred.journalator.crypto

/**
 * Protects today's unfinished entry.
 *
 * This is the one deliberate hole in "reading needs the passcode": you must be able to keep
 * adding to today's page all day without unlocking anything. The hole is made as small as
 * possible — it covers exactly one day, and the key is bound to this device's security
 * hardware, so the draft is readable by this app on this phone and nowhere else.
 */
interface DraftCipher {

    fun encrypt(plaintext: ByteArray): AesGcm.Box

    /** @throws DraftUnreadableException when the hardware key is gone or the blob is damaged. */
    fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray

    /** Drops the hardware key. The current draft becomes permanently unreadable. */
    fun destroyKey()
}

class DraftUnreadableException(message: String, cause: Throwable? = null) : Exception(message, cause)
