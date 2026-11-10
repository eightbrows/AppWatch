package io.github.eightbrows.appwatch.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupStepTest {

    @Test
    fun 三つの画面の並び() {
        assertEquals(listOf(SetupStep.SETTINGS, SetupStep.FIRST_CHECK, SetupStep.WIDGET), SetupStep.entries)
        assertEquals(3, SetupStep.COUNT)
        assertEquals(SetupStep.SETTINGS, SetupStep.FIRST)
        assertEquals(listOf(1, 2, 3), SetupStep.entries.map { it.number })
    }

    @Test
    fun 次へと戻る() {
        assertEquals(SetupStep.FIRST_CHECK, SetupStep.SETTINGS.next)
        assertEquals(SetupStep.WIDGET, SetupStep.FIRST_CHECK.next)
        assertNull(SetupStep.WIDGET.next) // 最後の画面の次は完了

        assertNull(SetupStep.SETTINGS.previous) // 最初の画面で戻るとアプリを閉じる
        assertEquals(SetupStep.SETTINGS, SetupStep.FIRST_CHECK.previous)
        assertEquals(SetupStep.FIRST_CHECK, SetupStep.WIDGET.previous)
    }
}
