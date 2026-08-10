package dev.retr0alfred.journalator.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class JrnlxArchiveTest {

    private val iterations = 1_000

    private fun sampleCorpus() = listOf(
        BackupEntry(
            date = "2026-08-10",
            zoneId = "Europe/London",
            createdAtEpoch = 1_770_000_000_000,
            sealedAtEpoch = 1_770_086_400_000,
            text = "A perfectly ordinary Monday.",
            mood = 3,
            contentLength = 4,
        ),
        BackupEntry(
            date = "2026-08-11",
            zoneId = "Pacific/Auckland",
            createdAtEpoch = 1_770_100_000_000,
            sealedAtEpoch = 1_770_186_400_000,
            text = "emoji 🔐, rtl مرحبا, quotes \"x\" and a backslash \\ plus\nnewlines\ttabs",
            mood = null,
            contentLength = null,
        ),
        BackupEntry(
            date = "2026-08-12",
            zoneId = "UTC",
            createdAtEpoch = 1,
            sealedAtEpoch = 2,
            text = "y".repeat(50_000),
            mood = 5,
            contentLength = 1,
        ),
    )

    private fun roundTrip(entries: List<BackupEntry>, passphrase: String): List<BackupEntry> {
        val out = ByteArrayOutputStream()
        JrnlxArchive.write(out, entries, passphrase.toCharArray(), iterations)
        return JrnlxArchive.read(ByteArrayInputStream(out.toByteArray()), passphrase.toCharArray())
    }

    @Test
    fun `export then import produces an identical corpus`() {
        val original = sampleCorpus()
        assertEquals(original, roundTrip(original, "backup-passphrase"))
    }

    @Test
    fun `an empty corpus round-trips`() {
        assertEquals(emptyList<BackupEntry>(), roundTrip(emptyList(), "nothing-here"))
    }

    @Test
    fun `the archive begins with the magic bytes and reveals no plaintext`() {
        val out = ByteArrayOutputStream()
        JrnlxArchive.write(out, sampleCorpus(), "passphrase".toCharArray(), iterations)
        val bytes = out.toByteArray()

        assertTrue(bytes.copyOfRange(0, 6).contentEquals(JrnlxArchive.MAGIC))
        val asText = String(bytes, StandardCharsets.ISO_8859_1)
        assertFalse(asText.contains("perfectly ordinary Monday"))
        assertFalse(asText.contains("Europe/London"))
    }

    @Test
    fun `the wrong passphrase is rejected cleanly`() {
        val out = ByteArrayOutputStream()
        JrnlxArchive.write(out, sampleCorpus(), "right".toCharArray(), iterations)
        assertThrows(BackupPassphraseException::class.java) {
            JrnlxArchive.read(ByteArrayInputStream(out.toByteArray()), "wrong".toCharArray())
        }
    }

    @Test
    fun `a file that is not a backup fails with a readable message`() {
        val notABackup = "just some text in a file".toByteArray()
        val error = assertThrows(BackupCorruptException::class.java) {
            JrnlxArchive.read(ByteArrayInputStream(notABackup), "anything".toCharArray())
        }
        assertTrue(error.message!!.contains("not a Journalator backup"))
    }

    @Test
    fun `a truncated backup fails rather than returning partial data`() {
        val out = ByteArrayOutputStream()
        JrnlxArchive.write(out, sampleCorpus(), "passphrase".toCharArray(), iterations)
        val truncated = out.toByteArray().copyOfRange(0, out.size() / 2)
        assertThrows(BackupCorruptException::class.java) {
            JrnlxArchive.read(ByteArrayInputStream(truncated), "passphrase".toCharArray())
        }
    }

    @Test
    fun `flipping a ciphertext byte is detected by the GCM tag`() {
        val out = ByteArrayOutputStream()
        JrnlxArchive.write(out, sampleCorpus(), "passphrase".toCharArray(), iterations)
        val bytes = out.toByteArray()
        bytes[bytes.size - 20] = (bytes[bytes.size - 20] + 1).toByte()
        assertThrows(BackupPassphraseException::class.java) {
            JrnlxArchive.read(ByteArrayInputStream(bytes), "passphrase".toCharArray())
        }
    }

    /**
     * The header is authenticated as associated data. Rewriting the iteration count to one —
     * the obvious way to make an offline attack on the passphrase cheap — has to fail.
     */
    @Test
    fun `tampering with the KDF iteration count invalidates the archive`() {
        val out = ByteArrayOutputStream()
        JrnlxArchive.write(out, sampleCorpus(), "passphrase".toCharArray(), 50_000)
        val bytes = out.toByteArray()
        // magic(6) + version(1), then the four-byte iteration count.
        bytes[7] = 0; bytes[8] = 0; bytes[9] = 0; bytes[10] = 1
        assertThrows(BackupPassphraseException::class.java) {
            JrnlxArchive.read(ByteArrayInputStream(bytes), "passphrase".toCharArray())
        }
    }

    @Test
    fun `two exports of the same corpus differ on disk`() {
        val corpus = sampleCorpus()
        val first = ByteArrayOutputStream().also {
            JrnlxArchive.write(it, corpus, "same".toCharArray(), iterations)
        }.toByteArray()
        val second = ByteArrayOutputStream().also {
            JrnlxArchive.write(it, corpus, "same".toCharArray(), iterations)
        }.toByteArray()
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun `the JSON body survives every character class we care about`() {
        val corpus = sampleCorpus()
        val decoded = JrnlxArchive.decodeEntries(JrnlxArchive.encodeEntries(corpus))
        assertEquals(corpus, decoded)
    }
}
