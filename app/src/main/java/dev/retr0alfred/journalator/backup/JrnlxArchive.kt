package dev.retr0alfred.journalator.backup

import dev.retr0alfred.journalator.crypto.AesGcm
import dev.retr0alfred.journalator.crypto.Pbkdf2
import dev.retr0alfred.journalator.crypto.SecureMemory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException

/** One journal day, in the clear, on its way into or out of an encrypted archive. */
data class BackupEntry(
    val date: String,
    val zoneId: String,
    val createdAtEpoch: Long,
    val sealedAtEpoch: Long,
    val text: String,
    val mood: Int?,
    val contentLength: Int?,
)

class BackupCorruptException(message: String, cause: Throwable? = null) : IOException(message, cause)

class BackupPassphraseException : Exception("That passphrase does not open this archive")

/**
 * The `.jrnlx` container — the only file Journalator ever puts into shared storage, and the
 * user's entire backup story.
 *
 * Layout, all big-endian:
 *
 * ```
 * "JRNLX1"      6 bytes, magic
 * version       1 byte
 * iterations    4 bytes, PBKDF2 rounds
 * salt          4-byte length + bytes
 * iv            4-byte length + 12 bytes
 * ciphertext    4-byte length + AES-256-GCM(JSON array of entries)
 * ```
 *
 * Everything before the ciphertext is fed to GCM as associated data, so an attacker cannot
 * quietly drop the iteration count to one and brute-force the passphrase cheaply — altering
 * any header byte makes the tag check fail.
 */
object JrnlxArchive {

    val MAGIC: ByteArray = "JRNLX1".toByteArray(StandardCharsets.US_ASCII)
    const val FORMAT_VERSION = 1
    const val FILE_EXTENSION = "jrnlx"
    const val MIME_TYPE = "application/octet-stream"

    fun write(
        output: OutputStream,
        entries: List<BackupEntry>,
        passphrase: CharArray,
        iterations: Int = Pbkdf2.MIN_ITERATIONS,
    ) {
        val salt = AesGcm.randomBytes(Pbkdf2.SALT_BYTES)
        val key = Pbkdf2.deriveKey(passphrase, salt, iterations)
        var plaintext: ByteArray? = null
        try {
            plaintext = encodeEntries(entries).toByteArray(StandardCharsets.UTF_8)
            val header = header(iterations, salt)
            val iv = AesGcm.randomIv()
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(key, "AES"),
                javax.crypto.spec.GCMParameterSpec(AesGcm.TAG_BITS, iv),
            )
            cipher.updateAAD(header)
            val ciphertext = cipher.doFinal(plaintext)

            DataOutputStream(output).use { data ->
                data.write(header)
                data.writeInt(iv.size)
                data.write(iv)
                data.writeInt(ciphertext.size)
                data.write(ciphertext)
                data.flush()
            }
        } finally {
            SecureMemory.wipeAll(key, plaintext)
        }
    }

    fun read(input: InputStream, passphrase: CharArray): List<BackupEntry> {
        val bytes = input.readBytes()
        if (bytes.size < MAGIC.size + 1) throw BackupCorruptException("File is too small to be a backup")

        val iterations: Int
        val salt: ByteArray
        val iv: ByteArray
        val ciphertext: ByteArray
        val header: ByteArray
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                val magic = ByteArray(MAGIC.size)
                data.readFully(magic)
                if (!magic.contentEquals(MAGIC)) {
                    throw BackupCorruptException("This file is not a Journalator backup")
                }
                val version = data.readUnsignedByte()
                if (version != FORMAT_VERSION) {
                    throw BackupCorruptException("Backup format version $version is not supported")
                }
                iterations = data.readInt()
                if (iterations < 1 || iterations > 10_000_000) {
                    throw BackupCorruptException("Backup declares an implausible iteration count")
                }
                salt = readBlob(data)
                iv = readBlob(data)
                ciphertext = readBlob(data)
            }
            header = header(iterations, salt)
        } catch (e: BackupCorruptException) {
            throw e
        } catch (e: Exception) {
            throw BackupCorruptException("Backup file is damaged", e)
        }

        val key = Pbkdf2.deriveKey(passphrase, salt, iterations)
        var plaintext: ByteArray? = null
        try {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(key, "AES"),
                javax.crypto.spec.GCMParameterSpec(AesGcm.TAG_BITS, iv),
            )
            cipher.updateAAD(header)
            plaintext = cipher.doFinal(ciphertext)
            return decodeEntries(String(plaintext, StandardCharsets.UTF_8))
        } catch (e: AEADBadTagException) {
            throw BackupPassphraseException()
        } catch (e: javax.crypto.BadPaddingException) {
            throw BackupPassphraseException()
        } catch (e: JsonException) {
            throw BackupCorruptException("Backup contents are damaged: ${e.message}", e)
        } finally {
            SecureMemory.wipeAll(key, plaintext)
        }
    }

    fun encodeEntries(entries: List<BackupEntry>): String = Json.write(
        JsonValue.Obj(
            linkedMapOf(
                "format" to JsonValue.Str("journalator"),
                "version" to JsonValue.Num(FORMAT_VERSION.toDouble()),
                "entries" to JsonValue.Arr(entries.map { it.toJson() }),
            )
        )
    )

    fun decodeEntries(text: String): List<BackupEntry> {
        val root = Json.parse(text).asObject()
        val entries = root.fields["entries"]?.asArray()
            ?: throw JsonException("Backup has no entries array")
        return entries.items.map { it.toBackupEntry() }
    }

    private fun header(iterations: Int, salt: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write(MAGIC)
            data.writeByte(FORMAT_VERSION)
            data.writeInt(iterations)
            data.writeInt(salt.size)
            data.write(salt)
        }
        return out.toByteArray()
    }

    private fun readBlob(data: DataInputStream): ByteArray {
        val size = data.readInt()
        if (size < 0 || size > 64 shl 20) {
            throw BackupCorruptException("Backup declares an impossible field length")
        }
        val blob = ByteArray(size)
        data.readFully(blob)
        return blob
    }

    private fun BackupEntry.toJson(): JsonValue = JsonValue.Obj(
        linkedMapOf(
            "date" to JsonValue.Str(date),
            "zone" to JsonValue.Str(zoneId),
            "created" to JsonValue.Num(createdAtEpoch.toDouble()),
            "sealed" to JsonValue.Num(sealedAtEpoch.toDouble()),
            "text" to JsonValue.Str(text),
            "mood" to (mood?.let { JsonValue.Num(it.toDouble()) } ?: JsonValue.Null),
            "length" to (contentLength?.let { JsonValue.Num(it.toDouble()) } ?: JsonValue.Null),
        )
    )

    private fun JsonValue.toBackupEntry(): BackupEntry = BackupEntry(
        date = string("date"),
        zoneId = string("zone"),
        createdAtEpoch = long("created"),
        sealedAtEpoch = long("sealed"),
        text = string("text"),
        mood = intOrNull("mood"),
        contentLength = intOrNull("length"),
    )
}
