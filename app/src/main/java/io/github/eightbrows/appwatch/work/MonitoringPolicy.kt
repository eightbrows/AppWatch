package io.github.eightbrows.appwatch.work

import android.content.Context
import io.github.eightbrows.appwatch.data.settings.SetupState
import io.github.eightbrows.appwatch.widget.AppWatchWidget
import io.github.eightbrows.appwatch.widget.WidgetPlacement

/** 見守りの仕組み（定期チェックとウィジェット）をどうするか。 */
data class MonitoringDecision(
    /** 定期チェックを登録する間隔（分）。登録を外すときは null */
    val scheduleIntervalMinutes: Int?,
    /** ウィジェットの受け取りを有効にするか */
    val widgetEnabled: Boolean,
)

/**
 * 初回設定の状態・使い方・チェック間隔から、見守りの仕組みをどうするかを決める（Android を使わない）。
 * - 見守る端末（初回設定が完了）：定期チェックをその間隔で登録、ウィジェットを使う
 * - 初回設定の前：定期チェックの登録を外す。ウィジェットは「初期設定を依頼」を出すために使う
 * - 閲覧専用：定期チェックの登録を外し、ウィジェットも使わない（記録は消さない）
 */
object MonitoringPolicy {
    fun decide(setup: SetupState, intervalMinutes: Int): MonitoringDecision = MonitoringDecision(
        scheduleIntervalMinutes = if (setup.isMonitoring) intervalMinutes else null,
        widgetEnabled = setup.usesWidget,
    )
}

/** 決めた内容を端末に当てはめる。 */
object MonitoringEffects {
    /**
     * @param uniqueName 定期チェックのジョブの名前（テストでは本物の登録に触れないよう別の名前を渡す）
     */
    suspend fun apply(
        context: Context,
        decision: MonitoringDecision,
        uniqueName: String = CheckScheduler.UNIQUE_WORK_NAME,
    ) {
        val interval = decision.scheduleIntervalMinutes
        if (interval != null) {
            CheckScheduler.schedule(context, interval, uniqueName)
        } else {
            CheckScheduler.cancel(context, uniqueName)
        }
        WidgetPlacement.setEnabled(context, decision.widgetEnabled)
        if (decision.widgetEnabled) AppWatchWidget.updateAll(context)
    }
}
