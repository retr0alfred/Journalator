package dev.retr0alfred.journalator.crypto

import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * The hybrid envelope that makes "write without a passcode, read only with one" possible.
 *
 * A single symmetric key cannot express that rule: anything that can encrypt today can
 * decrypt yesterday. So each sealed day gets a throwaway AES-256 content key, the entry is
 * encrypted under it, and the content key is wrapped to the vault's RSA public key. Sealing
 * therefore needs only public material — which the app can hold in the clear all day.
 * Opening needs the private key, and the private key is locked behind the passcode.
 */
object Envelope {

    /**
     * `RSA/ECB/OAEPPadding` plus an explicit [OAEPParameterSpec] rather than the shorthand
     * `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`. The shorthand is honoured inconsistently:
     * several providers read it as SHA-256 for the label digest but leave MGF1 on SHA-1.
     * Naming both digests makes the wire format identical on every provider and every
     * Android version, which matters for a file format that must still open in ten years.
     */
    private fun oaepParams() = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA256,
        PSource.PSpecified.DEFAULT,
    )

    private const val TRANSFORMATION = "RSA/ECB/OAEPPadding"

    data class Sealed(
        val wrappedKey: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is Sealed &&
                wrappedKey.contentEquals(other.wrappedKey) &&
                iv.contentEquals(other.iv) &&
                ciphertext.contentEquals(other.ciphertext)

        override fun hashCode(): Int {
            var result = wrappedKey.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            return result
        }
    }

    fun seal(plaintext: ByteArray, publicKey: PublicKey): Sealed {
        val contentKey = AesGcm.randomKey()
        try {
            val box = AesGcm.encrypt(contentKey, plaintext)
            val wrapper = Cipher.getInstance(TRANSFORMATION)
            wrapper.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams())
            return Sealed(wrapper.doFinal(contentKey), box.iv, box.ciphertext)
        } finally {
            SecureMemory.wipe(contentKey)
        }
    }

    fun open(sealed: Sealed, privateKey: PrivateKey): ByteArray {
        val unwrapper = Cipher.getInstance(TRANSFORMATION)
        unwrapper.init(Cipher.DECRYPT_MODE, privateKey, oaepParams())
        val contentKey = unwrapper.doFinal(sealed.wrappedKey)
        try {
            return AesGcm.decrypt(contentKey, sealed.iv, sealed.ciphertext)
        } finally {
            SecureMemory.wipe(contentKey)
        }
    }
}
