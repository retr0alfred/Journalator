package dev.retr0alfred.journalator.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The database lives in app-internal storage (`/data/data/<pkg>/databases`), which no file
 * manager and no other app can enumerate on an unrooted device. Its contents are ciphertext
 * regardless, so even a raw disk image of the partition gives up nothing.
 *
 * Version 1, and it is intended to stay version 1 forever. The exported schema in
 * `app/schemas` is the proof of that promise: if a field ever changes, the exported JSON
 * changes with it and the diff is impossible to miss in review.
 */
@Database(
    entities = [EntryEntity::class, DraftEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class JournalDatabase : RoomDatabase() {

    abstract fun entryDao(): EntryDao

    abstract fun draftDao(): DraftDao

    companion object {
        const val NAME = "journalator.db"

        fun build(context: Context): JournalDatabase =
            Room.databaseBuilder(context.applicationContext, JournalDatabase::class.java, NAME)
                // No fallbackToDestructiveMigration: a schema surprise must fail loudly rather
                // than silently delete somebody's decade of entries.
                .build()
    }
}
