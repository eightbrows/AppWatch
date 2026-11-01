package io.github.eightbrows.appwatch.widget

/** ウィジェットの文字の扱い。Android を使わない純粋な関数だけを置く。 */
object WidgetText {

    /** 改行してよい所の印（strings.xml の見出しに入れる）。 */
    const val BREAK_MARK: Char = '|'

    /** 改行しない印（WORD JOINER）。目には見えない。 */
    private const val WORD_JOINER = "⁠"

    /**
     * 見出しが2行になるときに、言葉の途中で改行しないようにする（仕様書 6章）。
     * 「|」で区切った各部分の文字の間に改行しない印を入れ、「|」は消す。改行は「|」の所でだけ起きる。
     * 例：「設定の確認が|必要です」→「設定の確認が」と「必要です」の間でだけ改行できる。
     * 「|」がなければ、全体が1つの言葉として扱われる（英語訳では「|」を使わず、普通の空白で改行してよい）。
     */
    fun keepWords(text: String): String {
        if (BREAK_MARK !in text) return text
        return text.split(BREAK_MARK).joinToString(separator = "") { part -> glue(part) }
    }

    /** 文字（コードポイント）の間に改行しない印を入れる。 */
    private fun glue(part: String): String {
        val codePoints = part.codePoints().toArray()
        return codePoints.joinToString(separator = WORD_JOINER) { String(Character.toChars(it)) }
    }
}
