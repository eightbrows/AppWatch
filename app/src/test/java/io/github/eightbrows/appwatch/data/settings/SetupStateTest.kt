package io.github.eightbrows.appwatch.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStateTest {

    @Test
    fun notCompleted_goesToSetup() {
        val state = SetupState(completed = false)
        assertEquals(StartDestination.SETUP, state.startDestination)
    }

    @Test
    fun completed_goesToTop() {
        val state = SetupState(completed = true)
        assertEquals(StartDestination.TOP, state.startDestination)
    }

    @Test
    fun setupIsNotCompleted_whenNothingIsSaved() {
        assertFalse(SettingsRepository.SETUP_COMPLETED_IF_UNSET)
        assertFalse(SetupState.fromStored(completed = SettingsRepository.SETUP_COMPLETED_IF_UNSET, legacyUsageMode = null).completed)
    }

    @Test
    fun 前の版で見守る端末だった端末は_初回設定が終わったまま() {
        assertTrue(SetupState.fromStored(completed = true, legacyUsageMode = "MONITOR").completed)
        assertTrue(SetupState.fromStored(completed = true, legacyUsageMode = null).completed)
    }

    @Test
    fun 前の版で閲覧専用だった端末は_初回設定から始める() {
        val state = SetupState.fromStored(completed = true, legacyUsageMode = SetupState.LEGACY_VIEW_ONLY)
        assertFalse(state.completed)
        assertEquals(StartDestination.SETUP, state.startDestination)
        assertEquals("VIEW_ONLY", SetupState.LEGACY_VIEW_ONLY) // 前の版で保存していた値と同じ
    }

    @Test
    fun 初回設定の途中だった端末は_そのまま未完了() {
        assertFalse(SetupState.fromStored(completed = false, legacyUsageMode = "MONITOR").completed)
        assertFalse(SetupState.fromStored(completed = false, legacyUsageMode = SetupState.LEGACY_VIEW_ONLY).completed)
    }
}
