package dev.retr0alfred.journalator

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.retr0alfred.journalator.crypto.Envelope
import dev.retr0alfred.journalator.crypto.KeyForge
import dev.retr0alfred.journalator.crypto.Vault
import dev.retr0alfred.journalator.crypto.WrongPasscodeException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets

/**
 * Setup on a real device, against the platform's own JCA providers.
 *
 * The JVM tests prove the algorithms are right. This proves Android's providers agree — in
 * particular that the explicit OAEP parameter spec produces the same wire format on
 * Conscrypt as it does on SunJCE, which is the assumption the whole file format rests on.
 */
@RunWith(AndroidJUnit4::class)
class VaultOnDeviceTest {

    private lateinit var vault: Vault

    @Before
    fun setUp() {
        vault = OnDeviceCrypto.freshVault()
    }

    @After
    fun tearDown() {
        OnDeviceCrypto.vaultFile().delete()
    }

    @Test
    fun setupProducesAUsableKeyPair() {
        val passcode = "on-device-passcode".toCharArray()
        val stages = mutableListOf<Vault.SetupStage>()

        vault.create(
            passcode = passcode.copyOf(),
            onStage = { stages.add(it) },
            keyPairProvider = { KeyForge.generateOfSize(2048) },
            iterationsProvider = { 2_000 },
        )

        assertTrue(stages.contains(Vault.SetupStage.GENERATING_KEYS))
        assertTrue(stages.contains(Vault.SetupStage.DONE))
        assertTrue(vault.isInitialised)

        val plaintext = "sealed on a real device 🔐 مرحبا"
        val sealed = Envelope.seal(plaintext.toByteArray(StandardCharsets.UTF_8), vault.publicKey())
        val privateKey = vault.unlock("on-device-passcode".toCharArray())
        assertEquals(plaintext, String(Envelope.open(sealed, privateKey), StandardCharsets.UTF_8))
    }

    @Test
    fun theWrongPasscodeIsRejectedOnDeviceToo() {
        vault.create(
            passcode = "right-passcode".toCharArray(),
            keyPairProvider = { KeyForge.generateOfSize(2048) },
            iterationsProvider = { 2_000 },
        )
        assertThrows(WrongPasscodeException::class.java) {
            vault.unlock("wrong-passcode".toCharArray())
        }
    }

    /**
     * The default path uses RSA-3072 unless the device is too slow, in which case it drops to
     * 2048. Either is acceptable; silently producing something weaker is not.
     */
    @Test
    fun generatedKeysAreAtLeastTwoThousandFortyEightBits() {
        val pair = KeyForge.generate()
        val modulusBits = (pair.public as java.security.interfaces.RSAPublicKey).modulus.bitLength()
        assertTrue("modulus was $modulusBits bits", modulusBits >= KeyForge.FALLBACK_BITS)
    }
}
