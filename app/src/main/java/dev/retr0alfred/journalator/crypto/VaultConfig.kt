package dev.retr0alfred.journalator.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * The vault header. Everything in here is either public or useless without the passcode:
 *
 *  - [publicKeyDer]  public by definition; it only ever encrypts.
 *  - [kdfSalt], [kdfIterations]  public KDF parameters. Knowing them buys an attacker
 *    nothing they could not have guessed; not storing them would make the file unopenable.
 *  - [wrappedPrivateKey]  the PKCS#8 private key under AES-256-GCM, keyed by PBKDF2 over
 *    the passcode. This is the one thing that matters, and it is unreadable without it.
 *  - [biometricBlob]  an optional second wrapping of the same private key, this time under
 *    a hardware key that demands a fingerprint. Optional, and never the only copy.
 *
 * No password hash and no verifier string is stored. Verification is the GCM tag check on
 * [wrappedPrivateKey]: a wrong passcode produces `AEADBadTagException` and nothing else.
 */
data class VaultConfig(
    val version: Int = CURRENT_VERSION,
    val publicKeyDer: ByteArray,
    val kdfSalt: ByteArray,
    val kdfIterations: Int,
    val wrappedPrivateKeyIv: ByteArray,
    val wrappedPrivateKey: ByteArray,
    val biometricIv: ByteArray? = null,
    val biometricBlob: ByteArray? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
        val MAGIC = byteArrayOf('J'.code.toByte(), 'R'.code.toByte(), 'N'.code.toByte(),
            'L'.code.toByte(), 'C'.code.toByte(), 'F'.code.toByte(), 'G'.code.toByte())
    }

    val hasBiometric: Boolean get() = biometricIv != null && biometricBlob != null

    override fun equals(other: Any?): Boolean =
        other is VaultConfig &&
            version == other.version &&
            publicKeyDer.contentEquals(other.publicKeyDer) &&
            kdfSalt.contentEquals(other.kdfSalt) &&
            kdfIterations == other.kdfIterations &&
            wrappedPrivateKeyIv.contentEquals(other.wrappedPrivateKeyIv) &&
            wrappedPrivateKey.contentEquals(other.wrappedPrivateKey) &&
            (biometricIv ?: ByteArray(0)).contentEquals(other.biometricIv ?: ByteArray(0)) &&
            (biometricBlob ?: ByteArray(0)).contentEquals(other.biometricBlob ?: ByteArray(0))

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + publicKeyDer.contentHashCode()
        result = 31 * result + kdfSalt.contentHashCode()
        result = 31 * result + kdfIterations
        result = 31 * result + wrappedPrivateKeyIv.contentHashCode()
        result = 31 * result + wrappedPrivateKey.contentHashCode()
        return result
    }
}

class VaultCorruptException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Reads and writes [VaultConfig] as a small, explicit binary record.
 *
 * Hand-rolled rather than JSON or a serialisation library: the format has to be readable by
 * a future maintainer with nothing but a hex editor, and it must not depend on a library
 * whose behaviour could drift. Writes go to a temporary file and are renamed into place, so
 * a crash mid-write leaves the previous config intact rather than a half-written one.
 */
class VaultConfigStore(private val file: File) {

    fun exists(): Boolean = file.exists() && file.length() > 0

    fun read(): VaultConfig {
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
            throw VaultCorruptException("Vault header could not be read", e)
        }
        return decode(bytes)
    }

    fun write(config: VaultConfig) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(encode(config))
        if (!temp.renameTo(file)) {
            // renameTo refuses to clobber on some filesystems; fall back to replace-in-place.
            file.delete()
            if (!temp.renameTo(file)) {
                throw VaultCorruptException("Vault header could not be written")
            }
        }
    }

    fun delete() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    companion object {

        fun encode(config: VaultConfig): ByteArray {
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { data ->
                data.write(VaultConfig.MAGIC)
                data.writeInt(config.version)
                data.writeInt(config.kdfIterations)
                data.writeBlob(config.kdfSalt)
                data.writeBlob(config.publicKeyDer)
                data.writeBlob(config.wrappedPrivateKeyIv)
                data.writeBlob(config.wrappedPrivateKey)
                data.writeBlob(config.biometricIv ?: ByteArray(0))
                data.writeBlob(config.biometricBlob ?: ByteArray(0))
            }
            return out.toByteArray()
        }

        fun decode(bytes: ByteArray): VaultConfig {
            try {
                DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                    val magic = ByteArray(VaultConfig.MAGIC.size)
                    data.readFully(magic)
                    if (!magic.contentEquals(VaultConfig.MAGIC)) {
                        throw VaultCorruptException("Vault header has the wrong magic bytes")
                    }
                    val version = data.readInt()
                    if (version != VaultConfig.CURRENT_VERSION) {
                        throw VaultCorruptException("Unsupported vault header version $version")
                    }
                    val iterations = data.readInt()
                    val salt = data.readBlob()
                    val publicKeyDer = data.readBlob()
                    val wrappedIv = data.readBlob()
                    val wrapped = data.readBlob()
                    val biometricIv = data.readBlob().takeIf { it.isNotEmpty() }
                    val biometricBlob = data.readBlob().takeIf { it.isNotEmpty() }
                    return VaultConfig(
                        version = version,
                        publicKeyDer = publicKeyDer,
                        kdfSalt = salt,
                        kdfIterations = iterations,
                        wrappedPrivateKeyIv = wrappedIv,
                        wrappedPrivateKey = wrapped,
                        biometricIv = biometricIv,
                        biometricBlob = biometricBlob,
                    )
                }
            } catch (e: VaultCorruptException) {
                throw e
            } catch (e: Exception) {
                throw VaultCorruptException("Vault header is damaged", e)
            }
        }

        private fun DataOutputStream.writeBlob(blob: ByteArray) {
            writeInt(blob.size)
            write(blob)
        }

        private fun DataInputStream.readBlob(): ByteArray {
            val size = readInt()
            if (size < 0 || size > 1 shl 20) {
                throw VaultCorruptException("Vault header declares an impossible field length")
            }
            val blob = ByteArray(size)
            readFully(blob)
            return blob
        }
    }
}
