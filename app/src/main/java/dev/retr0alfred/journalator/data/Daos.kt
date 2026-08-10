package dev.retr0alfred.journalator.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT date, sealedAtEpoch, contentLength, mood FROM entries ORDER BY date DESC")
    fun observeStubs(): Flow<List<EntryStub>>

    @Query("SELECT date FROM entries")
    suspend fun allDates(): List<String>

    @Query("SELECT * FROM entries ORDER BY date DESC")
    suspend fun all(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE date = :date")
    suspend fun byDate(date: String): EntryEntity?

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: EntryEntity): Long

    @Query("DELETE FROM entries")
    suspend fun deleteAll()
}

@Dao
interface DraftDao {

    @Query("SELECT * FROM draft WHERE id = :id")
    suspend fun load(id: Int = DraftEntity.SINGLETON_ID): DraftEntity?

    @Query("SELECT * FROM draft WHERE id = :id")
    fun observe(id: Int = DraftEntity.SINGLETON_ID): Flow<DraftEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(draft: DraftEntity)

    @Query("DELETE FROM draft")
    suspend fun clear()

    /**
     * Overwrites the draft row's ciphertext with zeroes before deleting it.
     *
     * SQLite's `DELETE` only unlinks a row; the bytes stay in the page until something reuses
     * it. Writing zeroes over them first means a sealed day's plaintext-adjacent draft does
     * not linger in a free page for months.
     */
    @Transaction
    suspend fun shred(zeroes: ByteArray) {
        overwrite(zeroes)
        clear()
    }

    @Query("UPDATE draft SET ciphertext = :zeroes, keystoreIv = :zeroes WHERE id = 0")
    suspend fun overwrite(zeroes: ByteArray)
}
