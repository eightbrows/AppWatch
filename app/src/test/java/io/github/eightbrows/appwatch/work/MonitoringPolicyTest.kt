package io.github.eightbrows.appwatch.work

import io.github.eightbrows.appwatch.data.settings.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitoringPolicyTest {

    private val completed = SetupState(completed = true)
    private val beforeSetup = SetupState(completed = false)

    @Test
    fun completed_schedulesWithInterval() {
        assertEquals(MonitoringDecision(30), MonitoringPolicy.decide(completed, 30))
        assertEquals(MonitoringDecision(720), MonitoringPolicy.decide(completed, 720))
        assertEquals(MonitoringDecision(1440), MonitoringPolicy.decide(completed, 1440))
    }

    @Test
    fun changingInterval_reschedulesWithNewInterval() {
        assertEquals(180, MonitoringPolicy.decide(completed, 180).scheduleIntervalMinutes)
    }

    @Test
    fun beforeSetup_cancelsCheck() {
        assertNull(MonitoringPolicy.decide(beforeSetup, 720).scheduleIntervalMinutes)
    }

    @Test
    fun legacyViewOnlyDevice_cancelsCheck_untilSetupIsDone() {
        // 前の版で閲覧専用だった端末：更新後は初回設定の前と同じ。初回設定を終えたら、その時点の間隔で登録する
        val legacy = SetupState.fromStored(completed = true, legacyUsageMode = SetupState.LEGACY_VIEW_ONLY)
        assertNull(MonitoringPolicy.decide(legacy, 720).scheduleIntervalMinutes)
        assertEquals(720, MonitoringPolicy.decide(SetupState.fromStored(completed = true, legacyUsageMode = null), 720).scheduleIntervalMinutes)
    }
}
