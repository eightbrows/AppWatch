package io.github.eightbrows.appwatch.data.system

import io.github.eightbrows.appwatch.widget.WidgetStatusDecider
import java.time.LocalDate

/** チェックの実行記録の1日分（scan_daily の1行）。 */
data class ScanDay(
    /** 端末の時間帯での日付（YYYY-MM-DD） */
    val date: String,
    val count: Int,
    val lastAt: Long,
)

/** 動作チェックの材料。Android の型を含まないので単体テストできる。 */
data class HealthInput(
    val batteryOptimizationExcluded: Boolean,
    /** 未使用アプリの一時停止からの除外（Android 10 以下は仕組みがないので true） */
    val unusedAppRestrictionsExcluded: Boolean,
    val periodicCheckRegistered: Boolean,
    val lastCheckAt: Long?,
    val now: Long,
    val checkIntervalMinutes: Int,
)

/** 動作チェックの結果。 */
data class HealthResult(
    /** 「すべて正常」なら true、「確認が必要」なら false */
    val allOk: Boolean,
    /** ウィジェットの「停止」と同じ条件に当たるか */
    val stopped: Boolean,
)

/**
 * 動作チェック（仕様書 6章「設定メニュー」）。Android を使わない純粋な関数だけを置く。
 * 判定はウィジェットの「設定の確認が必要です」「チェックが停止しています」と同じ条件を使う。
 */
object HealthCheck {

    /** 「すべて正常」は、2つの設定が済んでいて、定期チェックが登録されていて、「停止」の条件に当たらないとき。 */
    fun evaluate(input: HealthInput): HealthResult {
        val stopped = WidgetStatusDecider.isStopped(input.lastCheckAt, input.now, input.checkIntervalMinutes)
        val allOk = input.batteryOptimizationExcluded &&
            input.unusedAppRestrictionsExcluded &&
            input.periodicCheckRegistered &&
            !stopped
        return HealthResult(allOk = allOk, stopped = stopped)
    }

    /** 直近 [days] 日（今日を含む）のチェック回数の合計。日付が読めない行は数えない。 */
    fun checksInLastDays(scanDays: List<ScanDay>, today: LocalDate, days: Int = 7): Int {
        val from = today.minusDays((days - 1).toLong())
        return scanDays.sumOf { day ->
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
            if (date != null && !date.isBefore(from) && !date.isAfter(today)) day.count else 0
        }
    }
}
