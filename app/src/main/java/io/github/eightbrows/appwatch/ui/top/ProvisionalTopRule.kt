package io.github.eightbrows.appwatch.ui.top

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary

/**
 * 【仮の決まり・ステップ2のみ】トップの「確認が必要なアプリ」の振り分け。
 *
 * まだ記録（最初のチェック）も確認状態もないため、「新しく入った」「未確認」を本来の意味で決められない。
 * そこで、次の仮の決まりで振り分ける。
 * - カード：警告のある「後から導入」のアプリ
 * - まだ確認していないアプリ：それ以外の「後から導入」のアプリ
 * - プリインストールとシステムのアプリは「すべて」にだけ出す
 *
 * 次のステップで記録と確認状態ができたら、このファイルを削除し、呼び出し元を TopLists.from に置き換える。
 */
object ProvisionalTopRule {

    fun lists(apps: List<AppSummary>): TopLists {
        val userInstalled = apps.filter { it.kind == AppKind.USER_INSTALLED }
        val (withWarnings, withoutWarnings) = userInstalled.partition { it.warnings.isNotEmpty() }
        return TopLists(
            cards = withWarnings.sortedWith(TopLists.CardOrder),
            unconfirmed = withoutWarnings.sortedByDescending { it.installedAt },
            all = apps.sortedWith(TopLists.AllOrder),
        )
    }
}
