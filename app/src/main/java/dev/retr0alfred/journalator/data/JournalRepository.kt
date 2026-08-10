package dev.retr0alfred.journalator.data

import dev.retr0alfred.journalator.backup.BackupEntry
import dev.retr0alfred.journalator.crypto.DraftCipher
import dev.retr0alfred.journalator.crypto.DraftUnreadableException
import dev.retr0alfred.journalator.crypto.Envelope
import dev.retr0alfred.journalator.crypto.SecureMemory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.PublicKey
import java.time.LocalDate
import java.time.ZoneId

/** Today's page, as the write screen needs it. */
data class Draft(
    val date: LocalDate,
    val text: String,
    val mood: Int?,
    val updatedAtEpoch: Long,
)

/** A past day, decrypted in memory while the archive is unlocked. */
data class DecryptedEntry(
    val date: LocalDate,
    val text: String,
    val mood: Int?,
    val sealedAtEpoch: Long,
) {
    val wordCount: Int get() = Sealing.wordCount(text)
    val preview: String get() = Sealing.preview(text)
}

/**
 * Everything that touches the database, in one place, off the main thread.
 *
 * The repository never holds a private key. Callers pass one in for the duration of a read
 * and the view model drops it on lock, so there is no long-lived field holding the keys to
 * the archive.
 */
class JournalRepository(
    private val entryDao: EntryDao,
    private val draftDao: DraftDao,
    private val draftCipher: DraftCipher,
    private val io: CoroutineDispatcher,
) {

    fun observeStubs(): Flow<List<EntryStub>> = entryDao.observeStubs()

    suspend fun sealedDates(): Set<LocalDate> = withContext(io) {
        entryDao.allDates().mapNotNull { runCatching { Sealing.parse(it) }.getOrNull() }.toSet()
    }

    suspend fun entryCount(): Int = withContext(io) { entryDao.count() }

    suspend fun firstEntryDate(): LocalDate? = withContext(io) {
        entryDao.allDates().minOrNull()?.let { runCatching { Sealing.parse(it) }.getOrNull() }
    }

    // ------------------------------------------------------------------ draft

    /**
     * @return today's draft, or null when there is none. A draft that cannot be decrypted —
     * the hardware key was wiped by a factory reset of the keystore, say — is reported as
     * absent rather than crashing the write screen, and its unreadable row is shredded.
     */
    suspend fun loadDraft(): Draft? = withContext(io) {
        val row = draftDao.load() ?: return@withContext null
        var plaintext: ByteArray? = null
        try {
            plaintext = draftCipher.decrypt(row.keystoreIv, row.ciphertext)
            Draft(
                date = Sealing.parse(row.date),
                text = String(plaintext, StandardCharsets.UTF_8),
                mood = row.mood,
                updatedAtEpoch = row.updatedAtEpoch,
            )
        } catch (e: DraftUnreadableException) {
            shredDraft(row.ciphertext.size)
            null
        } finally {
            SecureMemory.wipe(plaintext)
        }
    }

    suspend fun saveDraft(
        date: LocalDate,
        zone: ZoneId,
        text: String,
        mood: Int?,
        nowEpochMillis: Long,
    ) = withContext(io) {
        var plaintext: ByteArray? = null
        try {
            plaintext = text.toByteArray(StandardCharsets.UTF_8)
            val box = draftCipher.encrypt(plaintext)
            draftDao.save(
                DraftEntity(
                    date = Sealing.key(date),
                    zoneId = zone.id,
                    keystoreIv = box.iv,
                    ciphertext = box.ciphertext,
                    updatedAtEpoch = nowEpochMillis,
                    mood = mood,
                )
            )
        } finally {
            SecureMemory.wipe(plaintext)
        }
    }

    private suspend fun shredDraft(byteLength: Int) {
        draftDao.shred(ByteArray(byteLength.coerceAtLeast(1)))
    }

    // ------------------------------------------------------------------ sealing

    /**
     * The lazy seal. Called on every start and every resume.
     *
     * @return the date that was sealed, or null when there was nothing to do.
     */
    suspend fun sealStaleDraft(
        today: LocalDate,
        publicKey: PublicKey,
        storeMetadataInClear: Boolean,
        nowEpochMillis: Long,
    ): LocalDate? = withContext(io) {
        val row = draftDao.load() ?: return@withContext null
        val draftDate = runCatching { Sealing.parse(row.date) }.getOrNull()
            ?: run { draftDao.clear(); return@withContext null }
        if (!Sealing.shouldSeal(draftDate, today)) return@withContext null

        var plaintext: ByteArray? = null
        try {
            plaintext = draftCipher.decrypt(row.keystoreIv, row.ciphertext)
            val text = String(plaintext, StandardCharsets.UTF_8)
            if (text.isBlank()) {
                // An empty day is not a day. Do not litter the archive with blank pages.
                shredDraft(row.ciphertext.size)
                return@withContext null
            }
            writeSealed(
                date = draftDate,
                zone = runCatching { ZoneId.of(row.zoneId) }.getOrDefault(ZoneId.systemDefault()),
                text = text,
                mood = row.mood,
                publicKey = publicKey,
                storeMetadataInClear = storeMetadataInClear,
                createdAtEpoch = row.updatedAtEpoch,
                sealedAtEpoch = nowEpochMillis,
                replaceExisting = false,
            )
            shredDraft(row.ciphertext.size)
            draftDate
        } catch (e: DraftUnreadableException) {
            shredDraft(row.ciphertext.size)
            null
        } finally {
            SecureMemory.wipe(plaintext)
        }
    }

    /** Seals today on demand, from the SEAL ENTRY button. */
    suspend fun sealToday(
        today: LocalDate,
        zone: ZoneId,
        text: String,
        mood: Int?,
        publicKey: PublicKey,
        storeMetadataInClear: Boolean,
        nowEpochMillis: Long,
    ) = withContext(io) {
        writeSealed(
            date = today,
            zone = zone,
            text = text,
            mood = mood,
            publicKey = publicKey,
            storeMetadataInClear = storeMetadataInClear,
            createdAtEpoch = nowEpochMillis,
            sealedAtEpoch = nowEpochMillis,
            replaceExisting = false,
        )
        val existing = draftDao.load()
        if (existing != null) shredDraft(existing.ciphertext.size)
    }

    private suspend fun writeSealed(
        date: LocalDate,
        zone: ZoneId,
        text: String,
        mood: Int?,
        publicKey: PublicKey,
        storeMetadataInClear: Boolean,
        createdAtEpoch: Long,
        sealedAtEpoch: Long,
        replaceExisting: Boolean,
    ) {
        var plaintext: ByteArray? = null
        try {
            plaintext = text.toByteArray(StandardCharsets.UTF_8)
            val sealed = Envelope.seal(plaintext, publicKey)
            val entity = EntryEntity(
                date = Sealing.key(date),
                zoneId = zone.id,
                createdAtEpoch = createdAtEpoch,
                sealedAtEpoch = sealedAtEpoch,
                wrappedKey = sealed.wrappedKey,
                iv = sealed.iv,
                ciphertext = sealed.ciphertext,
                contentLength = if (storeMetadataInClear) Sealing.wordCount(text) else null,
                mood = if (storeMetadataInClear) mood else null,
            )
            if (replaceExisting) entryDao.upsert(entity) else entryDao.insertIfAbsent(entity)
        } finally {
            SecureMemory.wipe(plaintext)
        }
    }

    suspend fun hasSealedToday(today: LocalDate): Boolean = withContext(io) {
        entryDao.byDate(Sealing.key(today)) != null
    }

    // ------------------------------------------------------------------ reading

    suspend fun decryptAll(privateKey: PrivateKey): List<DecryptedEntry> = withContext(io) {
        entryDao.all().map { it.decrypt(privateKey) }
    }

    suspend fun decrypt(date: LocalDate, privateKey: PrivateKey): DecryptedEntry? =
        withContext(io) { entryDao.byDate(Sealing.key(date))?.decrypt(privateKey) }

    private fun EntryEntity.decrypt(privateKey: PrivateKey): DecryptedEntry {
        var plaintext: ByteArray? = null
        try {
            plaintext = Envelope.open(Envelope.Sealed(wrappedKey, iv, ciphertext), privateKey)
            return DecryptedEntry(
                date = Sealing.parse(date),
                text = String(plaintext, StandardCharsets.UTF_8),
                mood = mood,
                sealedAtEpoch = sealedAtEpoch,
            )
        } finally {
            SecureMemory.wipe(plaintext)
        }
    }

    // ------------------------------------------------------------------ backup

    suspend fun exportAll(privateKey: PrivateKey): List<BackupEntry> = withContext(io) {
        entryDao.all().map { row ->
            val decrypted = row.decrypt(privateKey)
            BackupEntry(
                date = row.date,
                zoneId = row.zoneId,
                createdAtEpoch = row.createdAtEpoch,
                sealedAtEpoch = row.sealedAtEpoch,
                text = decrypted.text,
                mood = row.mood ?: decrypted.mood,
                contentLength = row.contentLength,
            )
        }
    }

    /** @return how many of [entries] collide with a day that already exists here. */
    suspend fun countCollisions(entries: List<BackupEntry>): Int = withContext(io) {
        val existing = entryDao.allDates().toHashSet()
        entries.count { it.date in existing }
    }

    suspend fun importAll(
        entries: List<BackupEntry>,
        publicKey: PublicKey,
        overwriteExisting: Boolean,
        storeMetadataInClear: Boolean,
    ): Int = withContext(io) {
        var written = 0
        for (entry in entries) {
            val date = runCatching { Sealing.parse(entry.date) }.getOrNull() ?: continue
            val zone = runCatching { ZoneId.of(entry.zoneId) }.getOrDefault(ZoneId.systemDefault())
            val existed = entryDao.byDate(entry.date) != null
            if (existed && !overwriteExisting) continue
            writeSealed(
                date = date,
                zone = zone,
                text = entry.text,
                mood = entry.mood,
                publicKey = publicKey,
                storeMetadataInClear = storeMetadataInClear,
                createdAtEpoch = entry.createdAtEpoch,
                sealedAtEpoch = entry.sealedAtEpoch,
                replaceExisting = true,
            )
            written++
        }
        written
    }

    // ------------------------------------------------------------------ destruction

    suspend fun wipeEverything() = withContext(io) {
        entryDao.deleteAll()
        val row = draftDao.load()
        if (row != null) shredDraft(row.ciphertext.size)
        draftDao.clear()
        draftCipher.destroyKey()
    }
}
