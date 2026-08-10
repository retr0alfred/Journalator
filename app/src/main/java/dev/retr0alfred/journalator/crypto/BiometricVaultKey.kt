package dev.retr0alfred.journalator.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The optional biometric route into the archive.
 *
 * It stores a *second* wrapping of the same private key under an AndroidKeyStore key with
 * `setUserAuthenticationRequired(true)`, so the hardware will not release it without a
 * successful fingerprint or face match.
 *
 * The passcode copy always stays. Biometrics are a convenience, never the only key holder:
 * fingerprints get re-enrolled, sensors break, and a user locked out of their own decade of
 * writing by a cracked screen would be an unforgivable design.
 *
 * `setInvalidatedByBiometricEnrollment(true)` means adding a new fingerprint destroys this
 * key. That is the correct behaviour — otherwise someone who can add their own fingerprint
 * to an unlocked phone inherits the archive.
 */
class BiometricVaultKey(private val alias: String = DEFAULT_ALIAS) {

    fun isEnrolledKeyPresent(): Boolean = runCatching { existingKey() != null }.getOrDefault(false)

    /** A cipher to hand to `BiometricPrompt` for the enrolment (encrypt) direction. */
    fun encryptCipher(): Cipher {
        deleteKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, createKey())
        return cipher
    }

    /** A cipher to hand to `BiometricPrompt` for the unlock (decrypt) direction. */
    fun decryptCipher(iv: ByteArray): Cipher {
        val key = existingKey() ?: throw BiometricKeyUnavailableException(
            "Biometric unlock is not set up on this device"
        )
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AesGcm.TAG_BITS, iv))
        return cipher
    }

    fun deleteKey() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun createKey(): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AesGcm.KEY_BYTES * 8)
            .setUserAuthenticationRequired(true)
            .setRandomizedEncryptionRequired(true)

        // Available since API 24, which is this app's minimum, so no version guard.
        builder.setInvalidatedByBiometricEnrollment(true)

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(builder.build())
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "journalator.biometric.v1"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

class BiometricKeyUnavailableException(message: String) : Exception(message)
