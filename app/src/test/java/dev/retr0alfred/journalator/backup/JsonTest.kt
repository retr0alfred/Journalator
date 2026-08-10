package dev.retr0alfred.journalator.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonTest {

    private fun roundTrip(value: JsonValue): JsonValue = Json.parse(Json.write(value))

    @Test
    fun `strings survive escaping and unescaping`() {
        val awkward = listOf(
            "",
            "plain",
            "quote \" backslash \\ slash /",
            "newline \n carriage \r tab \t backspace \b formfeed \u000C",
            "control \u0001\u001F",
            "separators \u2028 \u2029",
            "emoji 🔐 family 👨‍👩‍👧‍👦",
            "rtl مرحبا بالعالم שלום",
            "z".repeat(10_000),
        )
        awkward.forEach { sample ->
            assertEquals(sample, (roundTrip(JsonValue.Str(sample)) as JsonValue.Str).value)
        }
    }

    @Test
    fun `numbers round-trip as whole values`() {
        assertEquals(0L, (roundTrip(JsonValue.Num(0.0)) as JsonValue.Num).value.toLong())
        assertEquals(
            1_770_000_000_000L,
            (roundTrip(JsonValue.Num(1_770_000_000_000.0)) as JsonValue.Num).value.toLong(),
        )
        assertEquals(-7L, (roundTrip(JsonValue.Num(-7.0)) as JsonValue.Num).value.toLong())
    }

    @Test
    fun `nested objects and arrays round-trip`() {
        val value = JsonValue.Obj(
            linkedMapOf(
                "list" to JsonValue.Arr(
                    listOf(
                        JsonValue.Obj(linkedMapOf("a" to JsonValue.Num(1.0))),
                        JsonValue.Null,
                        JsonValue.Bool(true),
                        JsonValue.Str("x"),
                    )
                ),
                "empty" to JsonValue.Arr(emptyList()),
                "emptyObj" to JsonValue.Obj(emptyMap()),
            )
        )
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun `whitespace between tokens is tolerated`() {
        val parsed = Json.parse("  {\n \"a\" : [ 1 , 2 ]\t}\n ")
        assertEquals(2, (parsed.asObject().fields["a"] as JsonValue.Arr).items.size)
    }

    @Test
    fun `malformed input is rejected rather than guessed at`() {
        listOf(
            "{",
            "[1,]",
            "{\"a\"}",
            "{\"a\":}",
            "\"unterminated",
            "tru",
            "{} trailing",
            "{\"a\":1} {\"b\":2}",
        ).forEach { bad ->
            assertThrows("should reject: $bad", JsonException::class.java) { Json.parse(bad) }
        }
    }

    @Test
    fun `typed accessors report a helpful failure`() {
        val obj = Json.parse("{\"text\":\"hi\",\"n\":5,\"nothing\":null}")
        assertEquals("hi", obj.string("text"))
        assertEquals(5L, obj.long("n"))
        assertEquals(5, obj.intOrNull("n"))
        assertEquals(null, obj.intOrNull("nothing"))
        assertEquals(null, obj.intOrNull("absent"))
        assertThrows(JsonException::class.java) { obj.string("absent") }
        assertThrows(JsonException::class.java) { obj.long("text") }
    }
}
