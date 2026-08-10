package dev.retr0alfred.journalator.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with a fresh 12-byte IV per operation and a 128-bit tag.
 *
 * The authentication tag is appended to the ciphertext by the JCE, which is why the data
 * model stores `{iv, ciphertext}` rather than `{iv, ciphertext, tag}` as three columns —
 * the tag is the last 16 bytes of `ciphertext`. Splitting them would only create a way to
 * reassemble them wrongly.
 */
object AesGcm {

    const val IV_BYTES = 12
    const val TAG_BITS = 128
    const val TAG_BYTES = TAG_BITS / 8
    const val KEY_BYTES = 32

    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val random: SecureRandom by lazy { SecureRandom() }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    fun randomKey(): ByteArray = randomBytes(KEY_BYTES)

    fun randomIv(): ByteArray = randomBytes(IV_BYTES)

    data class Box(val iv: ByteArray, val ciphertext: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Box && iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)

        override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()
    }

    fun encrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray? = null): Box {
        require(key.size == KEY_BYTES) { "AES-256 needs a 32-byte key" }
        val iv = randomIv()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, iv),
        )
        associatedData?.let { cipher.updateAAD(it) }
        return Box(iv, cipher.doFinal(plaintext))
    }

    /** @throws javax.crypto.AEADBadTagException when the key, IV, AAD or ciphertext is wrong. */
    fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        associatedData: ByteArray? = null,
    ): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 needs a 32-byte key" }
        require(iv.size == IV_BYTES) { "GCM needs a 12-byte IV" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, iv),
        )
        associatedData?.let { cipher.updateAAD(it) }
        return cipher.doFinal(ciphertext)
    }

    fun decrypt(key: ByteArray, box: Box, associatedData: ByteArray? = null): ByteArray =
        decrypt(key, box.iv, box.ciphertext, associatedData)
}
