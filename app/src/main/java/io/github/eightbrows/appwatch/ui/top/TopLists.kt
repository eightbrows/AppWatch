package io.github.eightbrows.appwatch.ui.top

import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.ReviewStatus

/** トップ画面に出す一覧。画面の部品から切り離して、並び順を単体テストできるようにしている。 */
data class TopLists(
    /** 大きなカードで出すアプリ。要注意 → 警告あり → その他、の順。 */
    val cards: List<AppSummary>,
    /** 「まだ確認していないアプリ」の一覧。 */
    val unconfirmed: List<AppSummary>,
    /** 「すべて」で出す一覧。 */
    val all: List<AppSummary>,
) {
    companion object {
        /**
         * 本来の決まり（仕様書 6章）。記録と確認状態ができたら、仮の決まり（ProvisionalTopRule）からこちらに切り替える。
         * - カード：新しく入ったアプリと、要注意のアプリ
         * - まだ確認していないアプリ：カード以外の未確認のアプリ
         */
        fun from(apps: List<AppSummary>): TopLists {
            val cards = apps
                .filter { it.isNew || it.reviewStatus == ReviewStatus.CAUTION }
                .sortedWith(CardOrder)
            val cardPackages = cards.mapTo(HashSet()) { it.packageName }
            val unconfirmed = apps
                .filter { it.reviewStatus == ReviewStatus.UNCONFIRMED && it.packageName !in cardPackages }
                .sortedByDescending { it.installedAt }
            return TopLists(cards = cards, unconfirmed = unconfirmed, all = apps.sortedWith(AllOrder))
        }

        /** カードの並び：要注意 → 警告あり → その他。同じ中では新しく入った順。 */
        internal val CardOrder: Comparator<AppSummary> =
            compareBy<AppSummary> { cardRank(it) }.thenByDescending { it.installedAt }

        /** 「すべて」の並び：確認状態（要注意 → 未確認 → 問題なし）→ 種類（後から導入 → プリイン → システム）→ 名前。 */
        internal val AllOrder: Comparator<AppSummary> =
            compareBy<AppSummary> { statusRank(it.reviewStatus) }
                .thenBy { it.kind.ordinal } // AppKind の宣言順（後から導入 → プリイン → システム）
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

        private fun cardRank(app: AppSummary): Int = when {
            app.reviewStatus == ReviewStatus.CAUTION -> 0
            app.warnings.isNotEmpty() -> 1
            else -> 2
        }

        private fun statusRank(status: ReviewStatus): Int = when (status) {
            ReviewStatus.CAUTION -> 0
            ReviewStatus.UNCONFIRMED -> 1
            ReviewStatus.OK -> 2
        }
    }
}
