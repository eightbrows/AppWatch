package io.github.eightbrows.appwatch.work

import io.github.eightbrows.appwatch.data.settings.SetupState
import io.github.eightbrows.appwatch.model.UsageMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringPolicyTest {

    private val monitor = SetupState(completed = true, usageMode = UsageMode.MONITOR)
    private val viewOnly = SetupState(completed = true, usageMode = UsageMode.VIEW_ONLY)
    private val beforeSetup = SetupState(completed = false, usageMode = null)

    @Test
    fun monitor_schedulesWithInterval_andUsesWidget() {
        assertEquals(MonitoringDecision(30, widgetEnabled = true), MonitoringPolicy.decide(monitor, 30))
    }

    @Test
    fun changingInterval_reschedulesWithNewInterval() {
        assertEquals(180, MonitoringPolicy.decide(monitor, 180).scheduleIntervalMinutes)
    }

    @Test
    fun viewOnly_cancelsCheck_andDisablesWidget() {
        assertEquals(MonitoringDecision(null, widgetEnabled = false), MonitoringPolicy.decide(viewOnly, 30))
    }

    @Test
    fun beforeSetup_cancelsCheck_butKeepsWidgetForSetupRequest() {
        assertEquals(MonitoringDecision(null, widgetEnabled = true), MonitoringPolicy.decide(beforeSetup, 30))
    }

    @Test
    fun switchingViewOnlyToMonitor_restartsSetup_thenSchedulesOnCompletion() {
        // 設定メニューで見守る端末に戻した直後：初回設定は未完了（登録は外したまま、ウィジェットは有効に戻る）
        val switching = SetupState(completed = false, usageMode = UsageMode.MONITOR)
        assertEquals(MonitoringDecision(null, widgetEnabled = true), MonitoringPolicy.decide(switching, 60))
        // 初回設定を完了したら、その時点の間隔で登録する
        assertEquals(MonitoringDecision(60, widgetEnabled = true), MonitoringPolicy.decide(monitor, 60))
    }
}
