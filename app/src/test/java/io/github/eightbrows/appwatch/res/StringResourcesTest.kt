package io.github.eightbrows.appwatch.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 英語（values）と日本語（values-ja）の文字列をそろえる確かめ（ステップ12）。
 * - 名前の一覧が同じ（片方にしかない文字列がない）
 * - 書式の引数（%1$s・%2$d など）の番号と種類が同じ（複数形はどの数の形でも同じ）
 * - 英語の見出しに、日本語用の改行の印「|」を入れていない
 * res のファイルを直接読むので、Android を使わずに確かめられる（作業のフォルダーは app モジュール）。
 */
class StringResourcesTest {

    /** 1つの文字列の中身。複数形は数の形ごとの文、文字列の配列は要素ごとの文。 */
    private data class Entry(val kind: String, val texts: Map<String, String>)

    private fun load(folder: String): Map<String, Entry> {
        val file = File("src/main/res/$folder/strings.xml")
        assertTrue("見つかりません: ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = LinkedHashMap<String, Entry>()
        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            val texts = when (node.tagName) {
                "string" -> mapOf("" to node.textContent)
                "plurals" -> elements(node, "item").associate { it.getAttribute("quantity") to it.textContent }
                "string-array" -> elements(node, "item").mapIndexed { index, item -> index.toString() to item.textContent }.toMap()
                else -> continue
            }
            result[name] = Entry(node.tagName, texts)
        }
        return result
    }

    private fun elements(parent: Element, tag: String): List<Element> {
        val list = parent.getElementsByTagName(tag)
        return (0 until list.length).map { list.item(it) as Element }
    }

    /** 書式の引数（番号と種類）。%% は数えない。 */
    private fun formatArgs(text: String): Set<String> =
        Regex("""%([0-9]+)[$]([sd])""").findAll(text.replace("%%", "")).map { "${it.groupValues[1]}${it.groupValues[2]}" }.toSet()

    private val english by lazy { load("values") }
    private val japanese by lazy { load("values-ja") }

    @Test
    fun 名前の一覧が同じ() {
        val onlyEnglish = english.keys - japanese.keys
        val onlyJapanese = japanese.keys - english.keys
        assertTrue("values にしかない: $onlyEnglish", onlyEnglish.isEmpty())
        assertTrue("values-ja にしかない: $onlyJapanese", onlyJapanese.isEmpty())
        english.forEach { (name, entry) -> assertEquals("種類が違う: $name", entry.kind, japanese.getValue(name).kind) }
    }

    @Test
    fun 書式の引数の番号と種類が同じ() {
        val mismatches = english.mapNotNull { (name, en) ->
            val ja = japanese[name] ?: return@mapNotNull null
            val enArgs = en.texts.values.map(::formatArgs).toSet()
            val jaArgs = ja.texts.values.map(::formatArgs).toSet()
            // 複数形の中では、どの数の形でも引数が同じ。英語と日本語でも同じ
            if (enArgs.size == 1 && jaArgs.size == 1 && enArgs == jaArgs) null else "$name: en=$enArgs ja=$jaArgs"
        }
        assertTrue("書式の引数が違う: $mismatches", mismatches.isEmpty())
    }

    @Test
    fun 英語の複数形はoneとotherがある() {
        val missing = english.filter { it.value.kind == "plurals" && !it.value.texts.keys.containsAll(listOf("one", "other")) }.keys
        assertTrue("one／other がない: $missing", missing.isEmpty())
    }

    @Test
    fun 英語の文字列は日本語ではない() {
        val japaneseText = Regex("[\u3040-\u30FF\u4E00-\u9FFF]")
        val left = english.filter { (_, entry) -> entry.texts.values.any { japaneseText.containsMatchIn(it) } }.keys
        assertTrue("英語になっていない: $left", left.isEmpty())
    }

    @Test
    fun 英語の見出しに改行の印を入れていない() {
        val withMark = english.filter { (name, entry) -> name.startsWith("widget_") && entry.texts.values.any { '|' in it } }.keys
        assertTrue("「|」がある: $withMark", withMark.isEmpty())
    }

    /**
     * Android が表示する文字に近い形にする：\" \' \n   の escape を戻し、escape されていない「"」（文字列を囲む印）は消す。
     */
    private fun displayed(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' && i + 1 < raw.length -> {
                    when (val next = raw[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> {
                            sb.append(raw.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(next)
                    }
                    i += 2
                    continue
                }
                c == '"' -> Unit // escape されていない引用符は、文字列を囲む印なので表示されない
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    @Test
    fun 引用符とかぎかっこの対応() {
        val problems = mutableListOf<String>()
        english.forEach { (name, entry) ->
            entry.texts.values.map(::displayed).forEach { text ->
                if (text.count { it == '"' } % 2 != 0) problems += "en $name: $text"
                if (text.count { it == '“' } != text.count { it == '”' }) problems += "en $name: $text"
            }
        }
        japanese.forEach { (name, entry) ->
            entry.texts.values.map(::displayed).forEach { text ->
                if (text.count { it == '「' } != text.count { it == '」' }) problems += "ja $name: $text"
                if (text.count { it == '『' } != text.count { it == '』' }) problems += "ja $name: $text"
                if (text.count { it == '（' } != text.count { it == '）' }) problems += "ja $name: $text"
            }
        }
        assertTrue("引用符・かっこが対になっていない: $problems", problems.isEmpty())
        // 例：英語の終わりの引用符が表示に残ること
        assertEquals("Set %1\$s to \"%2\$s\"", displayed(english.getValue("notice_saved_status").texts.getValue("")))
        assertEquals(" · ", displayed(english.getValue("history_share_count_separator").texts.getValue("")))
    }

    @Test
    fun 書式の引数を読み取れる() {
        assertEquals(setOf("1s", "2d"), formatArgs("%1\$s を %2\$d 件"))
        assertEquals(emptySet<String>(), formatArgs("100%% 完了"))
        assertEquals(setOf("1d"), english.getValue("count_apps").texts.values.map(::formatArgs).toSet().single())
    }

    @Test
    fun アプリ名() {
        assertEquals("AppWatch", english.getValue("app_name").texts[""])
        assertEquals("アプリ見守当番", japanese.getValue("app_name").texts[""])
    }
}
