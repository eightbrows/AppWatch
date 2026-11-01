package io.github.eightbrows.appwatch.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

class JsonStreamTest {

    private fun write(block: JsonOut.() -> Unit): String = StringWriter().also { JsonOut(it).block() }.toString()

    @Test
    fun 物と配列を書く() {
        val text = write {
            beginObject()
            name("a").value(1L)
            name("b").beginArray().value("x").value(true).nullValue().endArray()
            name("c").beginObject().endObject()
            name("d").value(null as String?)
            endObject()
        }
        assertEquals("""{"a":1,"b":["x",true,null],"c":{},"d":null}""", text)
    }

    @Test
    fun 特別な文字を書いて読む() {
        val original = "引用符\" 逆斜線\\ 改行\n タブ\t 制御\u0001 絵文字😀 行区切り  /"
        val text = write { beginArray().value(original).endArray() }
        val json = JsonIn(StringReader(text))
        json.beginArray()
        assertEquals(original, json.nextString())
        json.endArray()
        assertEquals(JsonIn.Token.END_DOCUMENT, json.peek())
    }

    @Test
    fun 読む_種類と値() {
        val json = JsonIn(StringReader(""" { "s" : "文字A" , "n":-9223372036854775808, "t":true,"f":false,"z":null,"arr":[ ], "o":{"x":[1,{"y":2}]} } """))
        json.beginObject()
        assertEquals("s", json.nextName())
        assertEquals("文字A", json.nextString())
        assertEquals("n", json.nextName())
        assertEquals(Long.MIN_VALUE, json.nextLong())
        assertEquals("t", json.nextName())
        assertTrue(json.nextBoolean())
        assertEquals("f", json.nextName())
        assertFalse(json.nextBoolean())
        assertEquals("z", json.nextName())
        assertNull(json.nextStringOrNull())
        assertEquals("arr", json.nextName())
        json.beginArray()
        assertFalse(json.hasNext())
        json.endArray()
        assertEquals("o", json.nextName())
        json.skipValue() // 入れ子を読み飛ばす
        assertFalse(json.hasNext())
        json.endObject()
        assertEquals(JsonIn.Token.END_DOCUMENT, json.peek())
    }

    @Test
    fun 形が正しくなければ例外() {
        val broken = listOf(
            """{"a":1""", // 閉じていない
            """{"a" 1}""", // 「:」がない
            """{"a":1 "b":2}""", // 区切りがない
            """["x" "y"]""",
            """{"a":"閉じていない}""",
            """{"a":tru}""",
            """{"a":1}x""", // 余分な文字
            """{"a":"\q"}""", // 知らない「\」
            "PK\u0003\u0004", // JSON でない
        )
        for (text in broken) {
            assertThrows(text, JsonFormatException::class.java) {
                val json = JsonIn(StringReader(text))
                while (true) {
                    when (json.peek()) {
                        JsonIn.Token.END_DOCUMENT -> break
                        JsonIn.Token.BEGIN_OBJECT -> json.beginObject()
                        JsonIn.Token.END_OBJECT -> json.endObject()
                        JsonIn.Token.BEGIN_ARRAY -> json.beginArray()
                        JsonIn.Token.END_ARRAY -> json.endArray()
                        JsonIn.Token.NAME -> json.nextName()
                        JsonIn.Token.STRING -> json.nextString()
                        JsonIn.Token.NUMBER -> json.nextLong()
                        JsonIn.Token.BOOLEAN -> json.nextBoolean()
                        JsonIn.Token.NULL -> json.nextNull()
                    }
                }
            }
        }
    }

    @Test
    fun 種類が違えば例外() {
        val json = JsonIn(StringReader("""{"a":"x"}"""))
        json.beginObject()
        json.nextName()
        assertThrows(JsonFormatException::class.java) { json.nextLong() }
    }
}
