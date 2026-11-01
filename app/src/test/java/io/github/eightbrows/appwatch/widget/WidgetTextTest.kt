package io.github.eightbrows.appwatch.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetTextTest {

    private val wj = '⁠'

    @Test
    fun keepWords_allowsBreakOnlyAtMarks() {
        val result = WidgetText.keepWords("設定の確認が|必要です")
        assertFalse('|' in result)
        // 「が」と「必」の間（印の所）だけ改行しない印がない
        assertEquals("設${wj}定${wj}の${wj}確${wj}認${wj}が" + "必${wj}要${wj}で${wj}す", result)
        assertEquals("設定の確認が必要です", result.replace(wj.toString(), ""))
    }

    @Test
    fun keepWords_keepsCountTogether() {
        val result = WidgetText.keepWords("確認が必要な|アプリ 12件")
        // 「アプリ 12件」の中では改行しない（空白の前後も含む）
        assertEquals("ア${wj}プ${wj}リ${wj} ${wj}1${wj}2${wj}件", result.substringAfter("な"))
    }

    @Test
    fun keepWords_handlesThreeParts() {
        val result = WidgetText.keepWords("管理者に|初期設定を|依頼して下さい")
        assertEquals("管理者に初期設定を依頼して下さい", result.replace(wj.toString(), ""))
        assertEquals(2, result.windowed(2).count { it[0] != wj && it[1] != wj }) // 改行できる所は2か所
    }

    @Test
    fun keepWords_leavesTextWithoutMarksAsIs() {
        assertEquals("Check stopped", WidgetText.keepWords("Check stopped"))
    }
}
