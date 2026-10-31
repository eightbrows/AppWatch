package io.github.eightbrows.appwatch.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    private val android9 = 28
    private val android10 = 29

    @Test
    fun lightAndDark_ignoreSystem() {
        assertFalse(ThemeMode.LIGHT.isDark(systemIsDark = true, sdkInt = android10))
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = false, sdkInt = android10))
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = false, sdkInt = android9))
    }

    @Test
    fun system_followsDeviceOnAndroid10AndLater() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemIsDark = true, sdkInt = android10))
        assertFalse(ThemeMode.SYSTEM.isDark(systemIsDark = false, sdkInt = android10))
    }

    @Test
    fun system_isLightOnAndroid9AndEarlier() {
        assertFalse(ThemeMode.SYSTEM.isDark(systemIsDark = true, sdkInt = android9))
        assertFalse(ThemeMode.SYSTEM.isDark(systemIsDark = true, sdkInt = 26))
    }
}
