package dev.retr0alfred.journalator.crypto

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [DraftCipher] backed by the AndroidKeyStore.
 *
 * `setUserAuthenticationRequired(false)` is intentional and is the entire point: writing must
 * not demand a passcode. `setRandomizedEncryptionRequired(true)` forces the platform to pick
 * the IV, so a caller cannot accidentally reuse one — which for GCM would be catastrophic.
 *
 * StrongBox is used when the device has it. It is not required: most phones do not have a
 * dedicated security chip, and refusing to run on them would be theatre rather than security.
 */
class KeystoreDraftCipher(
    private val packageManager: PackageManager,
    private val alias: String = DEFAULT_ALIAS,
) : DraftCipher {

    override fun encrypt(plaintext: ByteArray): AesGcm.Box {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        return AesGcm.Box(cipher.iv.copyOf(), cipher.doFinal(plaintext))
    }

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        try {
            val key = existingKey() ?: throw DraftUnreadableException(
                "The device key that protected today's draft is no longer present"
            )
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AesGcm.TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        } catch (e: DraftUnreadableException) {
            throw e
        } catch (e: GeneralSecurityException) {
            throw DraftUnreadableException("Today's draft could not be decrypted", e)
        }
    }

    override fun destroyKey() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existingKey(): SecretKey? =
        keyStore().getKey(alias, null) as? SecretKey

    private fun loadOrCreateKey(): SecretKey = existingKey() ?: createKey()

    private fun createKey(): SecretKey {
        val strongBoxAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (strongBoxAvailable) {
            try {
                return generate(strongBox = true)
            } catch (e: Exception) {
                // StrongBoxUnavailableException on devices whose feature flag lies (it is a
                // ProviderException, not a GeneralSecurityException), plus the provider errors
                // thrown by StrongBox implementations that reject 256-bit AES. Caught broadly
                // and by supertype so the class is never named on API 24-27, where it does not
                // exist. The TEE path below is always a valid fallback.
            }
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AesGcm.KEY_BYTES * 8)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)

        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(builder.build())
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "journalator.draft.v1"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
