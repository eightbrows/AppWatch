package io.github.eightbrows.appwatch.data.settings

import io.github.eightbrows.appwatch.model.UsageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStateTest {

    @Test
    fun notCompleted_goesToSetup_withoutMonitoring() {
        for (mode in listOf(null, UsageMode.MONITOR, UsageMode.VIEW_ONLY)) {
            val state = SetupState(completed = false, usageMode = mode)
            assertEquals(StartDestination.SETUP, state.startDestination)
            assertFalse(state.isMonitoring) // 初回設定が終わるまでチェックしない
        }
    }

    @Test
    fun completedMonitor_goesToTop_andMonitors() {
        val state = SetupState(completed = true, usageMode = UsageMode.MONITOR)
        assertEquals(StartDestination.TOP, state.startDestination)
        assertTrue(state.isMonitoring)
        assertTrue(state.usesWidget)
    }

    @Test
    fun completedViewOnly_goesToImportedData_withoutMonitoringOrWidget() {
        val state = SetupState(completed = true, usageMode = UsageMode.VIEW_ONLY)
        assertEquals(StartDestination.IMPORTED_DATA, state.startDestination)
        assertFalse(state.isMonitoring)
        assertFalse(state.usesWidget)
    }

    @Test
    fun completedWithoutMode_isTreatedAsMonitor() {
        val state = SetupState(completed = true, usageMode = null)
        assertEquals(StartDestination.TOP, state.startDestination)
        assertTrue(state.isMonitoring)
    }

    @Test
    fun beforeSetup_widgetIsUsed_toAskForSetup() {
        assertTrue(SetupState(completed = false, usageMode = null).usesWidget)
    }

    @Test
    fun setupIsNotCompleted_whenNothingIsSaved() {
        assertFalse(SettingsRepository.SETUP_COMPLETED_IF_UNSET)
    }
}
