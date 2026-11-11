package io.github.eightbrows.appwatch.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** ウィジェットの状態ごとの色と印（ステップ20、仕様書 6章のウィジェットの表）。 */
class WidgetLookTest {

    @Test
    fun tones() {
        assertEquals(WidgetTone.RED, WidgetLook.toneOf(WidgetStatus.CAUTION_APPS))
        assertEquals(WidgetTone.GRAY, WidgetLook.toneOf(WidgetStatus.CHECK_STOPPED))
        assertEquals(WidgetTone.ORANGE, WidgetLook.toneOf(WidgetStatus.NEEDS_REVIEW))
        assertEquals(WidgetTone.ORANGE, WidgetLook.toneOf(WidgetStatus.SETTINGS_NEEDED))
        assertEquals(WidgetTone.BLUE, WidgetLook.toneOf(WidgetStatus.SETUP_REQUIRED))
        assertEquals(WidgetTone.GREEN, WidgetLook.toneOf(WidgetStatus.ALL_CLEAR))
    }

    @Test
    fun marks() {
        assertEquals(WidgetMark.EXCLAMATION, WidgetLook.markOf(WidgetStatus.CAUTION_APPS))
        assertEquals(WidgetMark.PAUSE, WidgetLook.markOf(WidgetStatus.CHECK_STOPPED))
        assertEquals(WidgetMark.QUESTION, WidgetLook.markOf(WidgetStatus.NEEDS_REVIEW))
        assertEquals(WidgetMark.GEAR, WidgetLook.markOf(WidgetStatus.SETTINGS_NEEDED))
        assertEquals(WidgetMark.INFO, WidgetLook.markOf(WidgetStatus.SETUP_REQUIRED))
        assertEquals(WidgetMark.CHECK, WidgetLook.markOf(WidgetStatus.ALL_CLEAR))
    }

    @Test
    fun everyStatus_hasItsOwnMark_andOnlyOrangeIsShared() {
        assertEquals(WidgetStatus.entries.size, WidgetStatus.entries.map { WidgetLook.markOf(it) }.toSet().size)
        val byTone = WidgetStatus.entries.groupBy { WidgetLook.toneOf(it) }
        assertEquals(setOf(WidgetTone.ORANGE), byTone.filterValues { it.size > 1 }.keys)
        assertEquals(WidgetTone.entries.toSet(), byTone.keys)
    }
}
