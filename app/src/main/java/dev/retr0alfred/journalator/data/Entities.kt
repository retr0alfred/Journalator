package dev.retr0alfred.journalator.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A sealed day.
 *
 * The primary key is the local calendar date, which is what enforces "one entry per day" —
 * not a uniqueness check in code that could be raced or forgotten.
 *
 * [zoneId] records the zone the day was written in. Without it, flying from Auckland to
 * Los Angeles would either duplicate a day or skip one; with it, the app can always explain
 * which local day a row belongs to.
 *
 * [contentLength] and [mood] are the only fields that can be stored in the clear, and only
 * when the user opts into the calendar heat-map. Left off — the default — they are null in
 * this row and live encrypted inside [ciphertext] instead.
 */
@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "zoneId") val zoneId: String,
    @ColumnInfo(name = "createdAtEpoch") val createdAtEpoch: Long,
    @ColumnInfo(name = "sealedAtEpoch") val sealedAtEpoch: Long,
    @ColumnInfo(name = "wrappedKey", typeAffinity = ColumnInfo.BLOB) val wrappedKey: ByteArray,
    @ColumnInfo(name = "iv", typeAffinity = ColumnInfo.BLOB) val iv: ByteArray,
    @ColumnInfo(name = "ciphertext", typeAffinity = ColumnInfo.BLOB) val ciphertext: ByteArray,
    @ColumnInfo(name = "contentLength") val contentLength: Int?,
    @ColumnInfo(name = "mood") val mood: Int?,
) {
    override fun equals(other: Any?): Boolean =
        other is EntryEntity &&
            date == other.date &&
            zoneId == other.zoneId &&
            createdAtEpoch == other.createdAtEpoch &&
            sealedAtEpoch == other.sealedAtEpoch &&
            wrappedKey.contentEquals(other.wrappedKey) &&
            iv.contentEquals(other.iv) &&
            ciphertext.contentEquals(other.ciphertext) &&
            contentLength == other.contentLength &&
            mood == other.mood

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + sealedAtEpoch.hashCode()
        return result
    }
}

/**
 * Today's work in progress. Exactly one row ever exists, pinned by [SINGLETON_ID].
 *
 * Encrypted with a hardware-backed AndroidKeyStore key rather than the envelope, because
 * this is the one thing the app must be able to re-read without a passcode. That key never
 * leaves the device's security hardware, so a copied database file is still noise.
 */
@Entity(tableName = "draft")
data class DraftEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "zoneId") val zoneId: String,
    @ColumnInfo(name = "keystoreIv", typeAffinity = ColumnInfo.BLOB) val keystoreIv: ByteArray,
    @ColumnInfo(name = "ciphertext", typeAffinity = ColumnInfo.BLOB) val ciphertext: ByteArray,
    @ColumnInfo(name = "updatedAtEpoch") val updatedAtEpoch: Long,
    @ColumnInfo(name = "mood") val mood: Int?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }

    override fun equals(other: Any?): Boolean =
        other is DraftEntity &&
            id == other.id &&
            date == other.date &&
            zoneId == other.zoneId &&
            keystoreIv.contentEquals(other.keystoreIv) &&
            ciphertext.contentEquals(other.ciphertext) &&
            updatedAtEpoch == other.updatedAtEpoch &&
            mood == other.mood

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + date.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

/** What the archive list can show without holding the private key. */
data class EntryStub(
    val date: String,
    val sealedAtEpoch: Long,
    val contentLength: Int?,
    val mood: Int?,
)
