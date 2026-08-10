package dev.retr0alfred.journalator.crypto

import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.AEADBadTagException

class WrongPasscodeException : Exception("The passcode did not open the vault")

class VaultNotInitialisedException : IllegalStateException("The vault has not been created yet")

/**
 * The passcode-guarded root of the key hierarchy.
 *
 * Pure JVM on purpose — no Android imports — so the whole thing is exercised by fast unit
 * tests on the CI machine rather than only on an emulator.
 */
class Vault(private val store: VaultConfigStore) {

    val isInitialised: Boolean get() = store.exists()

    fun config(): VaultConfig {
        if (!store.exists()) throw VaultNotInitialisedException()
        return store.read()
    }

    fun publicKey(): PublicKey = KeyForge.publicKeyFromDer(config().publicKeyDer)

    /**
     * First-launch setup. Generates the key pair, stretches the passcode, and wraps the
     * private key. [onStage] reports progress so the UI can show the assembling-mark
     * animation instead of a frozen screen.
     */
    fun create(
        passcode: CharArray,
        onStage: (SetupStage) -> Unit = {},
        keyPairProvider: () -> KeyPair = { KeyForge.generate() },
        iterationsProvider: () -> Int = { Pbkdf2.calibrateIterations() },
    ): VaultConfig {
        check(!store.exists()) { "The vault already exists; refusing to overwrite it" }

        onStage(SetupStage.CALIBRATING)
        val iterations = iterationsProvider()

        onStage(SetupStage.GENERATING_KEYS)
        val keyPair = keyPairProvider()

        onStage(SetupStage.WRAPPING)
        val salt = AesGcm.randomBytes(Pbkdf2.SALT_BYTES)
        val kek = Pbkdf2.deriveKey(passcode, salt, iterations)
        val pkcs8 = keyPair.private.encoded
        try {
            val box = AesGcm.encrypt(kek, pkcs8)
            val config = VaultConfig(
                publicKeyDer = keyPair.public.encoded,
                kdfSalt = salt,
                kdfIterations = iterations,
                wrappedPrivateKeyIv = box.iv,
                wrappedPrivateKey = box.ciphertext,
            )
            store.write(config)
            onStage(SetupStage.DONE)
            return config
        } finally {
            SecureMemory.wipeAll(kek, pkcs8)
        }
    }

    /**
     * Verification is implicit: if the GCM tag checks out the passcode was right, and if it
     * does not, it was wrong. There is no stored hash to steal and no partial plaintext to
     * leak — `doFinal` either returns the whole private key or throws.
     */
    fun unlock(passcode: CharArray): PrivateKey {
        val config = config()
        return unlock(config, passcode)
    }

    fun unlock(config: VaultConfig, passcode: CharArray): PrivateKey {
        val kek = Pbkdf2.deriveKey(passcode, config.kdfSalt, config.kdfIterations)
        var pkcs8: ByteArray? = null
        try {
            pkcs8 = AesGcm.decrypt(kek, config.wrappedPrivateKeyIv, config.wrappedPrivateKey)
            return KeyForge.privateKeyFromPkcs8(pkcs8)
        } catch (e: AEADBadTagException) {
            throw WrongPasscodeException()
        } catch (e: javax.crypto.BadPaddingException) {
            // Some providers surface a bad GCM tag as the plain superclass.
            throw WrongPasscodeException()
        } finally {
            SecureMemory.wipeAll(kek, pkcs8)
        }
    }

    /**
     * Re-wraps the private key under a new passcode.
     *
     * Note what does *not* happen here: no entry is touched. Entries are wrapped to the
     * public key, which never changes, so changing the passcode is O(1) whether the archive
     * holds ten days or ten thousand. Re-encrypting the corpus would be slow, would risk
     * losing data halfway through, and would buy nothing.
     */
    fun changePasscode(
        currentPasscode: CharArray,
        newPasscode: CharArray,
        iterationsProvider: () -> Int = { Pbkdf2.calibrateIterations() },
    ): VaultConfig {
        val config = config()
        val privateKey = unlock(config, currentPasscode)
        val pkcs8 = privateKey.encoded
        val iterations = iterationsProvider()
        val salt = AesGcm.randomBytes(Pbkdf2.SALT_BYTES)
        val kek = Pbkdf2.deriveKey(newPasscode, salt, iterations)
        try {
            val box = AesGcm.encrypt(kek, pkcs8)
            // A passcode change invalidates the biometric copy: it wrapped the same key, but
            // leaving it in place would let the old enrolment outlive the passcode rotation.
            val updated = config.copy(
                kdfSalt = salt,
                kdfIterations = iterations,
                wrappedPrivateKeyIv = box.iv,
                wrappedPrivateKey = box.ciphertext,
                biometricIv = null,
                biometricBlob = null,
            )
            store.write(updated)
            return updated
        } finally {
            SecureMemory.wipeAll(kek, pkcs8)
        }
    }

    /** Hands back the raw PKCS#8 so the biometric layer can wrap a second copy of it. */
    fun exportPrivateKeyMaterial(passcode: CharArray): ByteArray {
        val config = config()
        val kek = Pbkdf2.deriveKey(passcode, config.kdfSalt, config.kdfIterations)
        try {
            return AesGcm.decrypt(kek, config.wrappedPrivateKeyIv, config.wrappedPrivateKey)
        } catch (e: AEADBadTagException) {
            throw WrongPasscodeException()
        } finally {
            SecureMemory.wipe(kek)
        }
    }

    fun attachBiometricBlob(iv: ByteArray, blob: ByteArray) {
        store.write(config().copy(biometricIv = iv, biometricBlob = blob))
    }

    fun clearBiometricBlob() {
        store.write(config().copy(biometricIv = null, biometricBlob = null))
    }

    fun destroy() = store.delete()

    enum class SetupStage { CALIBRATING, GENERATING_KEYS, WRAPPING, DONE }
}
