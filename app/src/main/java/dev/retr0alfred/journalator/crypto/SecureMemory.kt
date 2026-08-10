package dev.retr0alfred.journalator.crypto

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Helpers for handling secrets as arrays rather than [String].
 *
 * A `String` is immutable and interned by the JVM: once a passcode lands in one there is
 * no way to erase it, and it can sit in the heap until a GC that may never come before a
 * memory dump. Every secret in Journalator lives in a `CharArray` or `ByteArray` that is
 * zero-filled in a `finally` block.
 */
object SecureMemory {

    fun wipe(bytes: ByteArray?) {
        if (bytes != null) Arrays.fill(bytes, 0)
    }

    fun wipe(chars: CharArray?) {
        if (chars != null) Arrays.fill(chars, '\u0000')
    }

    fun wipeAll(vararg arrays: ByteArray?) = arrays.forEach { wipe(it) }

    /**
     * UTF-8 encodes [chars] without ever materialising a `String`. The intermediate
     * [ByteBuffer] is zeroed before it is released.
     */
    fun toUtf8(chars: CharArray): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = encoder.encode(charBuffer)
        val out = ByteArray(byteBuffer.remaining())
        byteBuffer.get(out)
        if (byteBuffer.hasArray()) Arrays.fill(byteBuffer.array(), 0)
        return out
    }

    /** Runs [block] with [secret] and wipes it afterwards, whatever happens. */
    inline fun <T> useAndWipe(secret: ByteArray, block: (ByteArray) -> T): T =
        try {
            block(secret)
        } finally {
            wipe(secret)
        }

    /** Constant-time comparison, so a wrong guess leaks nothing through timing. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    fun contentEquals(a: CharArray, b: CharArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
