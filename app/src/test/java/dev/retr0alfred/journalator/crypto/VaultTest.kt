package dev.retr0alfred.journalator.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets
import java.security.KeyPair

/**
 * The tests the whole design rests on.
 *
 * Section 11 of the build spec says not to move past the crypto layer until the round-trip
 * and wrong-passcode tests pass, which is why these run on the JVM rather than an emulator:
 * they have to be fast enough that nobody is tempted to skip them.
 */
class VaultTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var vault: Vault
    private lateinit var keyPair: KeyPair

    /** RSA-2048 rather than 3072 purely so the suite stays quick; the code path is identical. */
    private val testKeyPair: () -> KeyPair get() = { keyPair }

    /** A deliberately tiny iteration count. The KDF itself is verified in [Pbkdf2Test]. */
    private val fastIterations: () -> Int get() = { 1_000 }

    @Before
    fun setUp() {
        vault = Vault(VaultConfigStore(folder.newFile("vault.cfg").also { it.delete() }))
        keyPair = KeyForge.generateOfSize(2048)
    }

    private fun create(passcode: String) = vault.create(
        passcode = passcode.toCharArray(),
        keyPairProvider = testKeyPair,
        iterationsProvider = fastIterations,
    )

    @Test
    fun `seal and open returns the exact original text`() {
        create("correct horse battery")
        val key = vault.unlock("correct horse battery".toCharArray())

        val samples = listOf(
            "plain ascii",
            "emoji: 🔐🧷🗝️ and a family 👨‍👩‍👧‍👦",
            "rtl: مرحبا بالعالم — שלום עולם",
            "mixed newlines\r\nand\ttabs",
            "\u2028line separator\u2029paragraph separator",
            "x".repeat(50_000),
        )

        for (sample in samples) {
            val sealed = Envelope.seal(sample.toByteArray(StandardCharsets.UTF_8), keyPair.public)
            val opened = String(Envelope.open(sealed, key), StandardCharsets.UTF_8)
            assertEquals(sample, opened)
        }
    }

    @Test
    fun `sealing the same text twice produces different ciphertext`() {
        val a = Envelope.seal("same".toByteArray(), keyPair.public)
        val b = Envelope.seal("same".toByteArray(), keyPair.public)
        assertFalse(a.ciphertext.contentEquals(b.ciphertext))
        assertFalse(a.iv.contentEquals(b.iv))
        assertFalse(a.wrappedKey.contentEquals(b.wrappedKey))
    }

    @Test
    fun `a wrong passcode throws and yields no plaintext`() {
        create("the-right-one")
        assertThrows(WrongPasscodeException::class.java) {
            vault.unlock("the-wrong-one".toCharArray())
        }
    }

    @Test
    fun `a passcode that differs by one character is still rejected`() {
        create("123456")
        assertThrows(WrongPasscodeException::class.java) {
            vault.unlock("123457".toCharArray())
        }
    }

    @Test
    fun `no password hash or verifier is written to the config file`() {
        val config = create("a-memorable-passphrase")
        val encoded = VaultConfigStore.encode(config)
        val asText = String(encoded, StandardCharsets.ISO_8859_1)
        assertFalse(asText.contains("a-memorable-passphrase"))
        // The wrapped key must not accidentally be the plaintext PKCS#8.
        assertFalse(config.wrappedPrivateKey.contentEquals(keyPair.private.encoded))
    }

    @Test
    fun `changing the passcode leaves every existing entry readable`() {
        create("first-passcode")

        val entries = (1..5).map { "entry number $it" }
        val sealed = entries.map { Envelope.seal(it.toByteArray(), keyPair.public) }

        vault.changePasscode(
            currentPasscode = "first-passcode".toCharArray(),
            newPasscode = "second-passcode".toCharArray(),
            iterationsProvider = fastIterations,
        )

        assertThrows(WrongPasscodeException::class.java) {
            vault.unlock("first-passcode".toCharArray())
        }

        val key = vault.unlock("second-passcode".toCharArray())
        sealed.forEachIndexed { index, payload ->
            assertEquals(entries[index], String(Envelope.open(payload, key)))
        }
    }

    @Test
    fun `changing the passcode does not touch the public key`() {
        val before = create("one-two-three-four")
        val after = vault.changePasscode(
            "one-two-three-four".toCharArray(),
            "four-three-two-one".toCharArray(),
            fastIterations,
        )
        assertArrayEquals(before.publicKeyDer, after.publicKeyDer)
        assertNotEquals(
            before.kdfSalt.toList(),
            after.kdfSalt.toList(),
        )
    }

    @Test
    fun `changing the passcode drops any biometric copy`() {
        create("original-passcode")
        vault.attachBiometricBlob(ByteArray(12), ByteArray(64) { 9 })
        assertTrue(vault.config().hasBiometric)

        vault.changePasscode(
            "original-passcode".toCharArray(),
            "replacement-passcode".toCharArray(),
            fastIterations,
        )
        assertFalse(vault.config().hasBiometric)
    }

    @Test
    fun `the config round-trips through its binary encoding`() {
        val config = create("round-trip-me")
        val decoded = VaultConfigStore.decode(VaultConfigStore.encode(config))
        assertEquals(config, decoded)
    }

    @Test
    fun `a damaged config file fails loudly rather than silently`() {
        val config = create("damage-me")
        val bytes = VaultConfigStore.encode(config)
        bytes[2] = 0
        assertThrows(VaultCorruptException::class.java) { VaultConfigStore.decode(bytes) }
    }

    @Test
    fun `creating a vault twice is refused`() {
        create("only-once")
        assertThrows(IllegalStateException::class.java) { create("again") }
    }

    @Test
    fun `an uninitialised vault reports itself as such`() {
        assertFalse(vault.isInitialised)
        assertThrows(VaultNotInitialisedException::class.java) { vault.config() }
    }

    @Test
    fun `exported private key material matches the generated key`() {
        create("export-me-please")
        val exported = vault.exportPrivateKeyMaterial("export-me-please".toCharArray())
        assertArrayEquals(keyPair.private.encoded, exported)
    }
}
