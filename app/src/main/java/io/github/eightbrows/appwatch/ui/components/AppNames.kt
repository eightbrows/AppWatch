package io.github.eightbrows.appwatch.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.eightbrows.appwatch.R

/**
 * 画面に出すアプリ名。本アプリ自身は、記録した名前ではなく今の言語の名前（「アプリ見守当番」／「AppWatch」）にする。
 * 言語を変えるとすぐ変わる。履歴・書き出しの名前は記録したまま（ここは画面の表示だけ）。
 */
@Composable
fun shownAppName(name: String, isSelf: Boolean): String =
    if (isSelf) stringResource(R.string.app_name) else name

/** [shownAppName] の、文字を先に取っておく形（一覧の吹き出しなど、まとめて作るとき用）。 */
fun shownAppName(name: String, isSelf: Boolean, selfName: String): String = if (isSelf) selfName else name
