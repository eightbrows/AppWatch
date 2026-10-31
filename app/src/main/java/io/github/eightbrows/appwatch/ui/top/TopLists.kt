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
        fun from(apps: List<AppSummary>): TopLists {
            // 要注意のアプリは「最上位に赤枠で表示」（仕様書 4章）のため、新しく入ったかどうかに関係なくカードに出す
            val cards = apps
                .filter { it.isNew || it.reviewStatus == ReviewStatus.CAUTION }
                .sortedWith(compareBy<AppSummary> { cardRank(it) }.thenByDescending { it.installedAt })
            val cardPackages = cards.map { it.packageName }.toSet()
            val unconfirmed = apps
                .filter { it.reviewStatus == ReviewStatus.UNCONFIRMED && it.packageName !in cardPackages }
                .sortedByDescending { it.installedAt }
            val all = apps.sortedWith(
                compareBy<AppSummary> { statusRank(it.reviewStatus) }.thenBy { it.name },
            )
            return TopLists(cards = cards, unconfirmed = unconfirmed, all = all)
        }

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
