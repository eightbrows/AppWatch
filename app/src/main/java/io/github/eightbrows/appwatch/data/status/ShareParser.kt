package io.github.eightbrows.appwatch.data.status

// 注意アプリの事前登録（仕様書 6章）で、入力や共有の文面からパッケージ名・アプリ名を取り出す。
// Android を使わない純粋な関数だけを置く。

object ShareParser {

    /** パッケージ名の形：英字で始まり、ドットで区切られた2つ以上の部分。各部分も英字で始まる。 */
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    /** Google Play の URL（http/https、play.google.com）と market:// の URL。 */
    private val PLAY_URL = Regex("https?://play\\.google\\.com/store/apps/details\\?\\S+", RegexOption.IGNORE_CASE)
    private val MARKET_URL = Regex("market://details\\?\\S+", RegexOption.IGNORE_CASE)

    /**
     * 名前を囲むかぎかっこ・引用符の組。共有の文面で、アプリ名はこのどれかで囲まれている。
     * 半角の「'」は英語の「Tom's」などと区別できないので使わない。
     */
    private val QUOTES = listOf("「" to "」", "『" to "』", "“" to "”", "\"" to "\"", "‘" to "’")

    fun isValidPackageName(text: String): Boolean = PACKAGE.matches(text)

    /** 文面の中の Google Play（または market://）の URL。なければ null。 */
    fun findUrl(text: String): String? = (PLAY_URL.find(text) ?: MARKET_URL.find(text))?.value

    /**
     * 入力や共有の文面からパッケージ名を取り出す（仕様書 6章の3つの形）。
     * - `https://play.google.com/store/apps/details?id=…`（文面の途中にあってもよい。他の項目が付いていてもよい）
     * - `market://details?id=…`
     * - パッケージ名だけ
     * 取り出せない、または形が正しくなければ null。
     */
    fun extractPackageName(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        findUrl(trimmed)?.let { url -> return idParameter(url)?.takeIf(::isValidPackageName) }
        return trimmed.takeIf(::isValidPackageName)
    }

    /** URL の「?」の後ろから id の値を取り出す。 */
    private fun idParameter(url: String): String? {
        val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        return query.split('&')
            .firstOrNull { it.startsWith("id=") }
            ?.removePrefix("id=")
            // 文の終わりの句読点などが続いていても、パッケージ名に使える文字だけを取る
            ?.takeWhile { it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '_' }
            ?.trimEnd('.')
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * 共有の文面からアプリ名を取り出す。
     * Google Play の共有の文面は「"アプリ名" をチェック https://play.google.com/…」のように、URL の前にかぎかっこや
     * 引用符で囲んだアプリ名が入る形を想定している。囲まれた部分がなければ、件名（EXTRA_SUBJECT）が URL や
     * パッケージ名でなければ使う。どちらもなければ null（手入力）。
     */
    fun extractAppName(text: String, subject: String? = null): String? {
        val url = findUrl(text)
        val beforeUrl = if (url != null) text.substringBefore(url) else text
        quoted(beforeUrl)?.let { return it }
        val cleanSubject = subject?.trim().orEmpty()
        if (cleanSubject.isNotEmpty() && findUrl(cleanSubject) == null && !isValidPackageName(cleanSubject)) {
            return quoted(cleanSubject) ?: cleanSubject.takeIf { it.length <= MAX_NAME_LENGTH }
        }
        return null
    }

    /** かぎかっこ・引用符で囲まれた最初の部分（空や長すぎるものは除く）。 */
    private fun quoted(text: String): String? {
        var best: Pair<Int, String>? = null
        for ((open, close) in QUOTES) {
            val start = text.indexOf(open)
            if (start < 0) continue
            val end = text.indexOf(close, start + open.length)
            if (end < 0) continue
            val inner = text.substring(start + open.length, end).trim()
            if (inner.isEmpty() || inner.length > MAX_NAME_LENGTH) continue
            if (best == null || start < best.first) best = start to inner
        }
        return best?.second
    }

    private const val MAX_NAME_LENGTH = 100
}
