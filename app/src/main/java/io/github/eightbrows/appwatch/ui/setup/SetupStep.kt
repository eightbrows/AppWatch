package io.github.eightbrows.appwatch.ui.setup

/** 初回設定の画面の並び（仕様書 6章）。Android を使わないので単体テストできる。 */
enum class SetupStep {
    /** 1 見守りを続けるための設定（上に「アプリ見守当番へようこそ」の見出し） */
    SETTINGS,

    /** 2 最初のチェック */
    FIRST_CHECK,

    /** 3 ウィジェットの追加 */
    WIDGET,
    ;

    /** 「初回設定 n / 3」の n（1から）。 */
    val number: Int get() = ordinal + 1

    /** 次の画面。最後の画面なら null（完了）。 */
    val next: SetupStep? get() = entries.getOrNull(ordinal + 1)

    /** 前の画面。最初の画面なら null（戻るとアプリを閉じる）。 */
    val previous: SetupStep? get() = entries.getOrNull(ordinal - 1)

    companion object {
        val FIRST: SetupStep = SETTINGS

        /** 画面の数。 */
        val COUNT: Int get() = entries.size
    }
}
