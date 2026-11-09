package io.github.eightbrows.appwatch.widget

import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.ui.top.TopLists

/** ウィジェットに出す状態（仕様書 6章の表の順＝優先度の高い順）。 */
enum class WidgetStatus {
    /** 1. 要注意のアプリ n件（赤） */
    CAUTION_APPS,

    /** 2. チェックが停止しています（オレンジ） */
    CHECK_STOPPED,

    /** 3. 確認が必要なアプリ n件（オレンジ） */
    NEEDS_REVIEW,

    /** 4. 設定の確認が必要です（オレンジ） */
    SETTINGS_NEEDED,

    /** 5. 管理者に初期設定を依頼して下さい（灰） */
    SETUP_REQUIRED,

    /** 6. 確認が必要なアプリは0件（緑） */
    ALL_CLEAR,

    /**
     * 閲覧専用の端末です（灰）。閲覧専用にするとウィジェットの受け取りを止めるが、ホーム画面から取り除けない端末があるため、
     * 止める前にこの表示にして、ウィジェットの削除を案内する（仕様書 6章「使い方の切り替え」）。最終チェックの行は出さない。
     */
    VIEW_ONLY,
}

/** 状態を決めるための材料。Android の型を含まないので単体テストできる。 */
data class WidgetInput(
    val cautionCount: Int,
    /** トップの「確認が必要なアプリ」に出ている件数（要注意を除く） */
    val needsReviewCount: Int,
    val lastCheckAt: Long?,
    val now: Long,
    val checkIntervalMinutes: Int,
    /** 電池の最適化の除外・未使用アプリの一時停止の除外が、どちらも設定済みか */
    val deviceSettingsOk: Boolean,
    val setupCompleted: Boolean,
    /** 閲覧専用の端末か（ほかのどの状態よりも優先する） */
    val viewOnly: Boolean = false,
)

/** ウィジェットに出す内容。 */
data class WidgetState(
    val status: WidgetStatus,
    /** 見出しの「n件」。件数を出さない状態では 0 */
    val count: Int,
    val lastCheckAt: Long?,
    /** ウィジェットの文字の言語（アプリで選んだ言語。「システムに従う」なら空）。変わったら描き直す */
    val languageTag: String = "",
)

object WidgetStatusDecider {

    /**
     * 優先度の高い順に、当てはまる最初の1つを選ぶ。
     * 閲覧専用の端末では、ほかの状態より優先して「閲覧専用の端末です」を出す。
     * 初回設定が終わっていないときは、チェックを行わない（仕様書 6章）ので他の状態は判定できず、
     * 常に「管理者に初期設定を依頼して下さい」を出す。
     */
    fun decide(input: WidgetInput): WidgetState {
        val status = when {
            input.viewOnly -> WidgetStatus.VIEW_ONLY
            !input.setupCompleted -> WidgetStatus.SETUP_REQUIRED
            input.cautionCount > 0 -> WidgetStatus.CAUTION_APPS
            isStopped(input.lastCheckAt, input.now, input.checkIntervalMinutes) -> WidgetStatus.CHECK_STOPPED
            input.needsReviewCount > 0 -> WidgetStatus.NEEDS_REVIEW
            !input.deviceSettingsOk -> WidgetStatus.SETTINGS_NEEDED
            else -> WidgetStatus.ALL_CLEAR
        }
        val count = when (status) {
            WidgetStatus.CAUTION_APPS -> input.cautionCount
            WidgetStatus.NEEDS_REVIEW -> input.needsReviewCount
            else -> 0
        }
        return WidgetState(status = status, count = count, lastCheckAt = input.lastCheckAt)
    }

    /**
     * 「停止」は、最終チェックがチェック間隔の3倍より前のとき（仕様書 6章）。
     * まだ一度もチェックしていない（null）ときは、比べるものがないので停止とはしない。
     */
    fun isStopped(lastCheckAt: Long?, now: Long, checkIntervalMinutes: Int): Boolean {
        if (lastCheckAt == null) return false
        return now - lastCheckAt > STOPPED_FACTOR * checkIntervalMinutes * MINUTE_MILLIS
    }

    /** トップの振り分けと同じ決まりで、要注意の件数と「確認が必要なアプリ」の件数を数える。 */
    fun countApps(presentApps: List<AppSummary>): Pair<Int, Int> {
        val lists = TopLists.from(presentApps)
        val caution = lists.cards.count { it.reviewStatus == ReviewStatus.CAUTION }
        val needsReview = lists.cards.size - caution + lists.unconfirmed.size
        return caution to needsReview
    }

    private const val STOPPED_FACTOR = 3
    private const val MINUTE_MILLIS = 60_000L
}
