package dev.retr0alfred.journalator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.retr0alfred.journalator.crypto.KeystoreDraftCipher
import dev.retr0alfred.journalator.crypto.Vault
import dev.retr0alfred.journalator.crypto.VaultConfigStore
import dev.retr0alfred.journalator.data.JournalDatabase
import dev.retr0alfred.journalator.data.JournalRepository
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Shared plumbing for the instrumented tests.
 *
 * These tests deliberately use the real AndroidKeyStore, the real JCA providers and a real
 * on-disk Room database rather than fakes. The guarantees being checked — that the hardware
 * key works, that the database file holds no plaintext — are only meaningful against the
 * real thing.
 */
object OnDeviceCrypto {

    fun context(): Context = ApplicationProvider.getApplicationContext()

    fun vaultFile(): File = File(context().filesDir, "test-vault.cfg")

    fun freshVault(): Vault {
        vaultFile().delete()
        File(vaultFile().parentFile, vaultFile().name + ".tmp").delete()
        return Vault(VaultConfigStore(vaultFile()))
    }

    fun databaseFile(): File = context().getDatabasePath(JournalDatabase.NAME)

    fun freshDatabase(): JournalDatabase {
        context().deleteDatabase(JournalDatabase.NAME)
        return JournalDatabase.build(context())
    }

    fun repositoryFor(database: JournalDatabase, alias: String): JournalRepository =
        JournalRepository(
            entryDao = database.entryDao(),
            draftDao = database.draftDao(),
            draftCipher = KeystoreDraftCipher(context().packageManager, alias),
            io = Dispatchers.IO,
        )

    /** Room buffers writes in the WAL; a checkpoint forces them into the main database file. */
    fun checkpoint(database: JournalDatabase) {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
    }

    fun allDatabaseBytes(): ByteArray {
        val base = databaseFile()
        val parts = listOf(base, File(base.path + "-wal"), File(base.path + "-shm"), File(base.path + "-journal"))
        return parts.filter { it.exists() }.fold(ByteArray(0)) { acc, file -> acc + file.readBytes() }
    }
}
