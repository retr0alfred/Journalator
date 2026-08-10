package dev.retr0alfred.journalator.backup

/**
 * A ~200 line JSON reader/writer for exactly the shape the backup format uses.
 *
 * Why not `org.json` or a serialisation library: `org.json` is stubbed out in JVM unit
 * tests, which would push the backup round-trip test onto an emulator, and a serialisation
 * plugin is one more thing that has to keep working for the next decade. The backup format
 * is frozen and tiny; a hand-written reader for it is auditable in one sitting and cannot
 * be broken by someone else's release.
 */
sealed class JsonValue {
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data object Null : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue()

    fun asObject(): Obj = this as? Obj ?: throw JsonException("Expected an object")
    fun asArray(): Arr = this as? Arr ?: throw JsonException("Expected an array")

    fun string(key: String): String =
        (asObject().fields[key] as? Str)?.value ?: throw JsonException("Missing text field '$key'")

    fun long(key: String): Long =
        (asObject().fields[key] as? Num)?.value?.toLong()
            ?: throw JsonException("Missing number field '$key'")

    fun intOrNull(key: String): Int? = when (val v = asObject().fields[key]) {
        null, is Null -> null
        is Num -> v.value.toInt()
        else -> throw JsonException("Field '$key' is not a number")
    }
}

class JsonException(message: String) : Exception(message)

object Json {

    fun write(value: JsonValue): String = StringBuilder().also { writeTo(value, it) }.toString()

    private fun writeTo(value: JsonValue, out: StringBuilder) {
        when (value) {
            is JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            is JsonValue.Num -> {
                val d = value.value
                if (d == d.toLong().toDouble()) out.append(d.toLong()) else out.append(d)
            }
            is JsonValue.Str -> writeString(value.value, out)
            is JsonValue.Arr -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    writeTo(item, out)
                }
                out.append(']')
            }
            is JsonValue.Obj -> {
                out.append('{')
                var first = true
                for ((key, item) in value.fields) {
                    if (!first) out.append(',')
                    first = false
                    writeString(key, out)
                    out.append(':')
                    writeTo(item, out)
                }
                out.append('}')
            }
        }
    }

    private fun writeString(text: String, out: StringBuilder) {
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                // Escape control characters and the line/paragraph separators, but pass every
                // other code unit straight through: emoji and RTL text must survive verbatim.
                c < ' ' || c == '\u2028' || c == '\u2029' ->
                    out.append("\\u").append(String.format(java.util.Locale.US, "%04x", c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonException("Trailing characters after the JSON value")
        return value
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun atEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (atEnd()) throw JsonException("Unexpected end of JSON")
            return when (val c = text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                'n' -> literal("null", JsonValue.Null)
                else -> if (c == '-' || c.isDigit()) parseNumber()
                else throw JsonException("Unexpected character '$c' at $index")
            }
        }

        private fun literal(word: String, value: JsonValue): JsonValue {
            if (!text.startsWith(word, index)) throw JsonException("Bad literal at $index")
            index += word.length
            return value
        }

        private fun parseObject(): JsonValue {
            expect('{')
            val fields = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') { index++; return JsonValue.Obj(fields) }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                fields[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> { index++; return JsonValue.Obj(fields) }
                    else -> throw JsonException("Expected ',' or '}' at $index")
                }
            }
        }

        private fun parseArray(): JsonValue {
            expect('[')
            val items = ArrayList<JsonValue>()
            skipWhitespace()
            if (peek() == ']') { index++; return JsonValue.Arr(items) }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> { index++; return JsonValue.Arr(items) }
                    else -> throw JsonException("Expected ',' or ']' at $index")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("Unterminated string")
                when (val c = text[index++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("Unterminated escape")
                        when (val esc = text[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) throw JsonException("Short \\u escape")
                                val hex = text.substring(index, index + 4)
                                index += 4
                                out.append(hex.toInt(16).toChar())
                            }
                            else -> throw JsonException("Unknown escape '\\$esc'")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun parseNumber(): JsonValue {
            val start = index
            if (peek() == '-') index++
            while (!atEnd() && (text[index].isDigit() || text[index] in ".eE+-")) index++
            val slice = text.substring(start, index)
            return JsonValue.Num(slice.toDoubleOrNull() ?: throw JsonException("Bad number '$slice'"))
        }

        private fun peek(): Char = if (atEnd()) '\u0000' else text[index]

        private fun expect(c: Char) {
            skipWhitespace()
            if (atEnd() || text[index] != c) throw JsonException("Expected '$c' at $index")
            index++
        }
    }
}
