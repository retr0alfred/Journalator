package dev.retr0alfred.journalator

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.retr0alfred.journalator.crypto.KeyForge
import dev.retr0alfred.journalator.data.JournalDatabase
import dev.retr0alfred.journalator.data.JournalRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId

/**
 * The test that *is* the "a file manager cannot read this" promise.
 *
 * A sentinel string is written into an entry and into a draft, the database is flushed to
 * disk, and every byte of every database file is searched for it. If this test ever fails,
 * the app is leaking plaintext to storage no matter what the architecture diagram says.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSecrecyTest {

    private lateinit var database: JournalDatabase
    private lateinit var repository: JournalRepository

    private val keyPair = KeyForge.generateOfSize(2048)
    private val zone: ZoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        database = OnDeviceCrypto.freshDatabase()
        repository = OnDeviceCrypto.repositoryFor(database, "journalator.test.draft")
    }

    @After
    fun tearDown() {
        database.close()
        OnDeviceCrypto.context().deleteDatabase(JournalDatabase.NAME)
    }

    @Test
    fun noSentinelFromASealedEntryAppearsInTheDatabaseFile() = runBlocking {
        val sentinel = "SENTINEL-c0ffee-DO-NOT-LEAK-6f6f"
        repository.sealToday(
            today = LocalDate.of(2026, 8, 10),
            zone = zone,
            text = "Today I wrote the word $sentinel into my journal.",
            mood = 2,
            publicKey = keyPair.public,
            storeMetadataInClear = false,
            nowEpochMillis = 1_770_000_000_000,
        )
        OnDeviceCrypto.checkpoint(database)

        val raw = OnDeviceCrypto.allDatabaseBytes()
        assertTrue("database file was empty", raw.isNotEmpty())
        assertFalse(
            "the sentinel string was found in the raw database bytes",
            raw.containsAscii(sentinel),
        )
    }

    @Test
    fun noSentinelFromTheDraftAppearsInTheDatabaseFile() = runBlocking {
        val sentinel = "DRAFT-SENTINEL-deadbeef-1234"
        repository.saveDraft(
            date = LocalDate.of(2026, 8, 10),
            zone = zone,
            text = "An unfinished thought containing $sentinel.",
            mood = null,
            nowEpochMillis = 1_770_000_000_000,
        )
        OnDeviceCrypto.checkpoint(database)

        val raw = OnDeviceCrypto.allDatabaseBytes()
        assertFalse(
            "the draft sentinel was found in the raw database bytes",
            raw.containsAscii(sentinel),
        )
    }

    @Test
    fun theDatabaseLivesInAppInternalStorage() {
        val path = OnDeviceCrypto.databaseFile().absolutePath
        val internal = OnDeviceCrypto.context().filesDir.parentFile!!.absolutePath
        assertTrue("database was at $path", path.startsWith(internal))
        assertFalse(path.contains("/sdcard"))
        assertFalse(path.contains("/storage/emulated"))
    }

    @Test
    fun aDraftSurvivesTheDatabaseBeingClosedAndReopened() = runBlocking {
        val text = "half a sentence that must not be lost"
        repository.saveDraft(
            date = LocalDate.of(2026, 8, 10),
            zone = zone,
            text = text,
            mood = 4,
            nowEpochMillis = 1_770_000_000_000,
        )
        database.close()

        // A cold start after a process kill: new database handle, same files, same keystore key.
        database = JournalDatabase.build(OnDeviceCrypto.context())
        repository = OnDeviceCrypto.repositoryFor(database, "journalator.test.draft")

        val reloaded = repository.loadDraft()
        assertNotNull(reloaded)
        assertEquals(text, reloaded!!.text)
        assertEquals(4, reloaded.mood)
    }

    @Test
    fun aDraftFromYesterdaySealsAndADraftFromTodayDoesNot() = runBlocking {
        val yesterday = LocalDate.of(2026, 8, 9)
        val today = LocalDate.of(2026, 8, 10)

        repository.saveDraft(yesterday, zone, "yesterday's words", 1, 1_770_000_000_000)
        val sealedDate = repository.sealStaleDraft(
            today = today,
            publicKey = keyPair.public,
            storeMetadataInClear = false,
            nowEpochMillis = 1_770_086_400_000,
        )
        assertEquals(yesterday, sealedDate)
        assertNull("the draft should be gone after sealing", repository.loadDraft())
        assertEquals(1, repository.entryCount())

        repository.saveDraft(today, zone, "today's words", null, 1_770_086_400_000)
        assertNull(
            "today's draft must not seal",
            repository.sealStaleDraft(today, keyPair.public, false, 1_770_086_400_001),
        )
        assertNotNull(repository.loadDraft())
    }

    @Test
    fun sealedEntriesDecryptBackToExactlyWhatWasWritten() = runBlocking {
        val text = "emoji 🔐 rtl مرحبا and 50k of padding " + "x".repeat(50_000)
        repository.sealToday(
            today = LocalDate.of(2026, 8, 10),
            zone = zone,
            text = text,
            mood = null,
            publicKey = keyPair.public,
            storeMetadataInClear = false,
            nowEpochMillis = 1_770_000_000_000,
        )
        val decrypted = repository.decrypt(LocalDate.of(2026, 8, 10), keyPair.private)
        assertNotNull(decrypted)
        assertEquals(text, decrypted!!.text)
    }

    private fun ByteArray.containsAscii(needle: String): Boolean {
        val target = needle.toByteArray(StandardCharsets.US_ASCII)
        if (target.isEmpty() || size < target.size) return false
        outer@ for (start in 0..size - target.size) {
            for (i in target.indices) {
                if (this[start + i] != target[i]) continue@outer
            }
            return true
        }
        return false
    }
}
