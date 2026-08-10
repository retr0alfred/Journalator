package dev.retr0alfred.journalator.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Generates the vault key pair, once, at first launch.
 *
 * RSA and not X25519: JCA's XDH only exists from API 31. RSA-OAEP works identically from
 * API 24 to whatever Android ships in 2035, needs no native library, and is a wire format
 * that will still be readable long after this app stops being built.
 *
 * The key pair is generated in software, deliberately *not* in the AndroidKeyStore. A
 * keystore key is protected by the OS and the device; this private key has to be protected
 * by something the user knows, so that a stolen, unlocked-once phone still cannot be made
 * to reveal the archive.
 */
object KeyForge {

    const val ALGORITHM = "RSA"
    const val PREFERRED_BITS = 3072
    const val FALLBACK_BITS = 2048

    /** Above this, fall back to RSA-2048 rather than make the user stare at a spinner. */
    const val GENERATION_BUDGET_MILLIS = 8_000L

    /**
     * Empirically RSA-3072 costs roughly three to four times RSA-2048 on the same hardware.
     * Timing a 2048 generation first gives an honest estimate without the risk of starting a
     * 3072 generation that cannot be cancelled once it is under way.
     */
    private const val COST_RATIO_3072_OVER_2048 = 3.5

    /**
     * @return the strongest key pair this device can produce inside the budget. The 2048-bit
     * probe is reused as the result when 3072 would blow the budget, so no work is wasted.
     */
    fun generate(nanoTime: () -> Long = System::nanoTime): KeyPair {
        val start = nanoTime()
        val probe = generateOfSize(FALLBACK_BITS)
        val probeMillis = (nanoTime() - start) / 1_000_000.0
        val projectedMillis = probeMillis * COST_RATIO_3072_OVER_2048
        return if (projectedMillis <= GENERATION_BUDGET_MILLIS) {
            generateOfSize(PREFERRED_BITS)
        } else {
            probe
        }
    }

    fun generateOfSize(bits: Int): KeyPair {
        val generator = KeyPairGenerator.getInstance(ALGORITHM)
        generator.initialize(bits, SecureRandom())
        return generator.generateKeyPair()
    }

    fun publicKeyFromDer(der: ByteArray): PublicKey =
        KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(der))

    fun privateKeyFromPkcs8(pkcs8: ByteArray): PrivateKey =
        KeyFactory.getInstance(ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(pkcs8))
}
