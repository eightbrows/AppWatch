package io.github.eightbrows.appwatch.ui.top

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSearch
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.CautionRule
import io.github.eightbrows.appwatch.model.AppWarning
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
    /**
     * 検索で絞った一覧（アプリ名・パッケージ名の部分一致、大文字・小文字を区別しない）。
     * どの一覧にも同じように効かせ、並び順は変えない。空の検索なら、そのまま返す。
     */
    fun filtered(query: String): TopLists {
        if (query.isBlank()) return this
        val match = { app: AppSummary -> AppSearch.matches(app.name, app.packageName, query) }
        return TopLists(cards = cards.filter(match), unconfirmed = unconfirmed.filter(match), all = all.filter(match))
    }

    /** カードのうち要注意のもの（「要注意のアプリ」の見出しの下）。 */
    val cautionCards: List<AppSummary> get() = cards.filter { it.reviewStatus == ReviewStatus.CAUTION }

    /** カードのうち要注意以外のもの（新しく入った・署名が変わった・再導入など。「新しく入った・変わったアプリ」の見出しの下）。 */
    val changedCards: List<AppSummary> get() = cards.filter { it.reviewStatus != ReviewStatus.CAUTION }

    /** 「確認が必要」のタブの件数（3つの組の合計）。 */
    val needsReviewCount: Int get() = cards.size + unconfirmed.size

    companion object {
        /**
         * トップの振り分け（仕様書 6章）。[apps] には端末にあるアプリだけを渡す。
         * - カード：新しく入ったアプリ（最初のチェックより後に追加され、問題なしでない）、要注意のアプリ、
         *   署名が変わったアプリ（「問題なし」になるまで。オレンジ枠）
         * - まだ確認していないアプリ：確認状態が「未確認」の「後から導入」のアプリのうち、カードに出ていないもの
         */
        fun from(apps: List<AppSummary>): TopLists {
            val cards = apps.filter { isCard(it) }.sortedWith(CardOrder)
            val cardPackages = cards.mapTo(HashSet()) { it.packageName }
            val unconfirmed = apps
                .filter {
                    it.reviewStatus == ReviewStatus.UNCONFIRMED &&
                        it.kind == AppKind.USER_INSTALLED &&
                        it.packageName !in cardPackages
                }
                .sortedByDescending { it.installedAt }
            return TopLists(cards = cards, unconfirmed = unconfirmed, all = apps.sortedWith(AllOrder))
        }

        /**
         * カードで出すか。署名が変わった警告は「問題なし」にした時点で消える（仕様書 4章）ので、
         * 警告が付いている間は「問題なし」になっていない。
         * 要注意で無効のアプリは対応が済んだものとして、カードに出さない（「すべて」には出る）。
         */
        private fun isCard(app: AppSummary): Boolean =
            !CautionRule.isHandled(app.reviewStatus, app.isEnabled) && (
                app.isNew ||
                    app.reviewStatus == ReviewStatus.CAUTION ||
                    (AppWarning.SIGNATURE_CHANGED in app.warnings && app.reviewStatus != ReviewStatus.OK)
                )

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
