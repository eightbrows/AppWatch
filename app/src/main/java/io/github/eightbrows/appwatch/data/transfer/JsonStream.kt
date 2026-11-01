package io.github.eightbrows.appwatch.data.transfer

import java.io.IOException
import java.io.Reader
import java.io.Writer

// 書き出しファイルの JSON を、順に流して読み書きする小さな仕組み（部品は使わない）。
// Android 標準の android.util.JsonReader／JsonWriter は単体テスト（JVM）では動かないため、
// 同じ使い方の最小限のものをここに置く。記録が多くても、全体を一度に文字にしない。

/** JSON の形が正しくない。 */
class JsonFormatException(message: String) : IOException(message)

/** JSON を順に書く。 */
class JsonOut(private val out: Writer) {

    /** 入れ物ごとに「まだ1つも書いていないか」。 */
    private val first = ArrayDeque<Boolean>()
    private var afterName = false

    fun beginObject(): JsonOut = open('{')
    fun endObject(): JsonOut = close('}')
    fun beginArray(): JsonOut = open('[')
    fun endArray(): JsonOut = close(']')

    fun name(name: String): JsonOut {
        separate()
        writeString(name)
        out.write(':'.code)
        afterName = true
        return this
    }

    fun value(value: String?): JsonOut {
        if (value == null) return nullValue()
        separate()
        writeString(value)
        return this
    }

    fun value(value: Long?): JsonOut {
        if (value == null) return nullValue()
        separate()
        out.write(value.toString())
        return this
    }

    fun value(value: Int?): JsonOut = value(value?.toLong())

    fun value(value: Boolean?): JsonOut {
        if (value == null) return nullValue()
        separate()
        out.write(if (value) "true" else "false")
        return this
    }

    fun nullValue(): JsonOut {
        separate()
        out.write("null")
        return this
    }

    fun flush() = out.flush()

    private fun open(c: Char): JsonOut {
        separate()
        out.write(c.code)
        first.addLast(true)
        return this
    }

    private fun close(c: Char): JsonOut {
        check(first.isNotEmpty()) { "閉じる入れ物がありません" }
        first.removeLast()
        out.write(c.code)
        return this
    }

    /** 値の前の区切り（「,」）。名前の直後は区切らない。 */
    private fun separate() {
        if (afterName) {
            afterName = false
            return
        }
        if (first.isEmpty()) return
        if (first.last()) first[first.lastIndex] = false else out.write(','.code)
    }

    private fun writeString(s: String) {
        out.write('"'.code)
        for (c in s) {
            when {
                c == '"' -> out.write("\\\"")
                c == '\\' -> out.write("\\\\")
                c == '\n' -> out.write("\\n")
                c == '\r' -> out.write("\\r")
                c == '\t' -> out.write("\\t")
                c < ' ' || c == ' ' || c == ' ' -> out.write("\\u%04x".format(c.code))
                else -> out.write(c.code)
            }
        }
        out.write('"'.code)
    }
}

/** JSON を順に読む（使い方は android.util.JsonReader と同じ）。 */
class JsonIn(private val reader: Reader) {

    enum class Token { BEGIN_OBJECT, END_OBJECT, BEGIN_ARRAY, END_ARRAY, NAME, STRING, NUMBER, BOOLEAN, NULL, END_DOCUMENT }

    private enum class Scope { EMPTY_ARRAY, NONEMPTY_ARRAY, EMPTY_OBJECT, DANGLING_NAME, NONEMPTY_OBJECT, DOCUMENT_START, DOCUMENT_END }

    private val stack = ArrayDeque<Scope>().apply { addLast(Scope.DOCUMENT_START) }
    private var peeked: Token? = null
    private var pushedBack: Int = NONE

    fun peek(): Token {
        peeked?.let { return it }
        val token = readToken()
        peeked = token
        return token
    }

    fun hasNext(): Boolean = peek().let { it != Token.END_OBJECT && it != Token.END_ARRAY && it != Token.END_DOCUMENT }

    fun beginObject() {
        expect(Token.BEGIN_OBJECT)
        stack.addLast(Scope.EMPTY_OBJECT)
    }

    fun endObject() {
        expect(Token.END_OBJECT)
        stack.removeLast()
    }

    fun beginArray() {
        expect(Token.BEGIN_ARRAY)
        stack.addLast(Scope.EMPTY_ARRAY)
    }

    fun endArray() {
        expect(Token.END_ARRAY)
        stack.removeLast()
    }

    fun nextName(): String {
        expect(Token.NAME)
        return readStringBody()
    }

    fun nextString(): String {
        val token = peek()
        if (token != Token.STRING) throw JsonFormatException("文字が来るはずが $token")
        peeked = null
        return readStringBody()
    }

    /** 文字か null。 */
    fun nextStringOrNull(): String? = if (peek() == Token.NULL) {
        nextNull()
        null
    } else {
        nextString()
    }

    fun nextLong(): Long {
        val token = peek()
        if (token != Token.NUMBER) throw JsonFormatException("数が来るはずが $token")
        peeked = null
        val text = readLiteral()
        return text.toLongOrNull() ?: throw JsonFormatException("整数ではありません: $text")
    }

    fun nextLongOrNull(): Long? = if (peek() == Token.NULL) {
        nextNull()
        null
    } else {
        nextLong()
    }

    fun nextInt(): Int {
        val value = nextLong()
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) throw JsonFormatException("大きすぎる数: $value")
        return value.toInt()
    }

    fun nextBoolean(): Boolean {
        val token = peek()
        if (token != Token.BOOLEAN) throw JsonFormatException("true か false が来るはずが $token")
        peeked = null
        return when (val text = readLiteral()) {
            "true" -> true
            "false" -> false
            else -> throw JsonFormatException("true か false ではありません: $text")
        }
    }

    fun nextBooleanOrNull(): Boolean? = if (peek() == Token.NULL) {
        nextNull()
        null
    } else {
        nextBoolean()
    }

    fun nextNull() {
        val token = peek()
        if (token != Token.NULL) throw JsonFormatException("null が来るはずが $token")
        peeked = null
        if (readLiteral() != "null") throw JsonFormatException("null ではありません")
    }

    /** 知らない項目の値を読み飛ばす（新しい形式で項目が増えても読めるように）。 */
    fun skipValue() {
        when (peek()) {
            Token.BEGIN_OBJECT -> {
                beginObject()
                while (hasNext()) {
                    nextName()
                    skipValue()
                }
                endObject()
            }
            Token.BEGIN_ARRAY -> {
                beginArray()
                while (hasNext()) skipValue()
                endArray()
            }
            Token.STRING -> nextString()
            Token.NUMBER -> readLiteral().also { peeked = null }
            Token.BOOLEAN -> nextBoolean()
            Token.NULL -> nextNull()
            else -> throw JsonFormatException("読み飛ばせない位置です: ${peek()}")
        }
    }

    private fun expect(token: Token) {
        val actual = peek()
        if (actual != token) throw JsonFormatException("$token が来るはずが $actual")
        peeked = null
    }

    /** 次の記号を決める。区切り（「,」「:」）はここで読む。 */
    private fun readToken(): Token {
        when (stack.last()) {
            Scope.EMPTY_ARRAY -> {
                stack[stack.lastIndex] = Scope.NONEMPTY_ARRAY
                val c = nextNonSpace()
                if (c == ']'.code) return Token.END_ARRAY
                pushBack(c)
            }
            Scope.NONEMPTY_ARRAY -> when (nextNonSpace()) {
                ']'.code -> return Token.END_ARRAY
                ','.code -> Unit
                else -> throw JsonFormatException("配列の区切りがありません")
            }
            Scope.EMPTY_OBJECT, Scope.NONEMPTY_OBJECT -> {
                val wasEmpty = stack.last() == Scope.EMPTY_OBJECT
                stack[stack.lastIndex] = Scope.DANGLING_NAME
                var c = nextNonSpace()
                if (c == '}'.code) return Token.END_OBJECT
                if (!wasEmpty) {
                    if (c != ','.code) throw JsonFormatException("項目の区切りがありません")
                    c = nextNonSpace()
                }
                if (c != '"'.code) throw JsonFormatException("項目の名前がありません")
                return Token.NAME
            }
            Scope.DANGLING_NAME -> {
                stack[stack.lastIndex] = Scope.NONEMPTY_OBJECT
                if (nextNonSpace() != ':'.code) throw JsonFormatException("「:」がありません")
            }
            Scope.DOCUMENT_START -> stack[stack.lastIndex] = Scope.DOCUMENT_END
            Scope.DOCUMENT_END -> {
                val c = nextNonSpace()
                if (c == NONE) return Token.END_DOCUMENT
                throw JsonFormatException("最後の後に余分な文字があります")
            }
        }
        return when (val c = nextNonSpace()) {
            '{'.code -> Token.BEGIN_OBJECT
            '['.code -> Token.BEGIN_ARRAY
            '"'.code -> Token.STRING
            't'.code, 'f'.code -> Token.BOOLEAN.also { pushBack(c) }
            'n'.code -> Token.NULL.also { pushBack(c) }
            NONE -> throw JsonFormatException("途中で終わっています")
            else -> if (c == '-'.code || c in '0'.code..'9'.code) {
                Token.NUMBER.also { pushBack(c) }
            } else {
                throw JsonFormatException("JSON ではありません")
            }
        }
    }

    /** 「"」の後から、閉じる「"」までを読む。 */
    private fun readStringBody(): String {
        val sb = StringBuilder()
        while (true) {
            val c = read()
            when {
                c == NONE -> throw JsonFormatException("文字が閉じていません")
                c == '"'.code -> return sb.toString()
                c == '\\'.code -> sb.append(readEscape())
                c < 0x20 -> throw JsonFormatException("文字の中に制御文字があります")
                else -> sb.append(c.toChar())
            }
        }
    }

    private fun readEscape(): Char = when (val c = read()) {
        '"'.code -> '"'
        '\\'.code -> '\\'
        '/'.code -> '/'
        'b'.code -> '\b'
        'f'.code -> '\u000C'
        'n'.code -> '\n'
        'r'.code -> '\r'
        't'.code -> '\t'
        'u'.code -> {
            var code = 0
            repeat(4) {
                val d = Character.digit(read(), 16)
                if (d < 0) throw JsonFormatException("\\u の後が16進数ではありません")
                code = code * 16 + d
            }
            code.toChar()
        }
        else -> throw JsonFormatException("知らない \\ の使い方: ${if (c == NONE) "終わり" else c.toChar().toString()}")
    }

    /** 数・true・false・null を読む（区切りの前まで）。 */
    private fun readLiteral(): String {
        val sb = StringBuilder()
        while (true) {
            val c = read()
            if (c == NONE || c == ','.code || c == '}'.code || c == ']'.code || c == ':'.code || isSpace(c)) {
                if (c != NONE) pushBack(c)
                break
            }
            sb.append(c.toChar())
            if (sb.length > 64) throw JsonFormatException("値が長すぎます")
        }
        if (sb.isEmpty()) throw JsonFormatException("値がありません")
        return sb.toString()
    }

    private fun nextNonSpace(): Int {
        while (true) {
            val c = read()
            if (!isSpace(c)) return c
        }
    }

    private fun isSpace(c: Int) = c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code

    private fun read(): Int {
        if (pushedBack != NONE) return pushedBack.also { pushedBack = NONE }
        return reader.read()
    }

    private fun pushBack(c: Int) {
        pushedBack = c
    }

    private companion object {
        const val NONE = -1
    }
}
