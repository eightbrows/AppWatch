package io.github.eightbrows.appwatch.model

/** アプリの検索（トップと確認状態の画面で共通）。Android を使わない純粋な関数。 */
object AppSearch {

    /** アプリ名・パッケージ名の部分一致（大文字・小文字を区別しない）。前後の空白は無視し、空なら全部に当たる。 */
    fun matches(name: String, packageName: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return name.contains(q, ignoreCase = true) || packageName.contains(q, ignoreCase = true)
    }

    /** パッケージ名を「.」の後で折り返しやすくする（「.」の後に幅のない空白を入れる）。表示だけに使う。 */
    fun breakablePackageName(packageName: String): String = packageName.replace(".", ".​")
}
