package io.github.eightbrows.appwatch.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {

    @Test
    fun navigateAndBack() {
        val navigator = Navigator(listOf(Screen.TOP))
        assertFalse(navigator.canGoBack)

        navigator.navigate(Screen.SETTINGS_MENU)
        assertEquals(Screen.SETTINGS_MENU, navigator.current)
        assertTrue(navigator.canGoBack)

        assertTrue(navigator.back())
        assertEquals(Screen.TOP, navigator.current)
        assertFalse(navigator.back())
        assertEquals(Screen.TOP, navigator.current)
    }

    @Test
    fun navigatingToCurrentScreen_doesNotStackTwice() {
        val navigator = Navigator(listOf(Screen.TOP))
        navigator.navigate(Screen.SETTINGS_MENU)
        navigator.navigate(Screen.SETTINGS_MENU)
        navigator.back()
        assertEquals(Screen.TOP, navigator.current)
    }
}
