package io.github.eightbrows.appwatch.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** つかんで動かせるスクロールバーの位置・長さの計算。 */
class ScrollbarMathTest {
    private val track = 1000f
    private val min = 44f

    @Test
    fun `一覧が約2画面に収まる時は出さない`() {
        assertFalse(ScrollbarMath.shouldShow(total = 16, visibleItems = 8f))
        assertFalse(ScrollbarMath.shouldShow(total = 10, visibleItems = 8f))
        assertFalse(ScrollbarMath.shouldShow(total = 0, visibleItems = 0f))
        assertTrue(ScrollbarMath.shouldShow(total = 17, visibleItems = 8f))
        assertTrue(ScrollbarMath.shouldShow(total = 223, visibleItems = 8f))
    }

    @Test
    fun `長さは見えている割合`() {
        assertEquals(100f, ScrollbarMath.thumbLength(track, total = 100, visibleItems = 10f, minLength = min), 0.01f)
        assertEquals(250f, ScrollbarMath.thumbLength(track, total = 40, visibleItems = 10f, minLength = min), 0.01f)
    }

    @Test
    fun `長さは最低 44dp`() {
        // 223件のうち8件 → 割合どおりだと 35.9 なので 44 にのばす
        assertEquals(44f, ScrollbarMath.thumbLength(track, total = 223, visibleItems = 8f, minLength = min), 0.01f)
        // 溝が最低より短い時は溝の長さまで
        assertEquals(30f, ScrollbarMath.thumbLength(30f, total = 223, visibleItems = 8f, minLength = min), 0.01f)
    }

    @Test
    fun `一番上ではつまみも一番上`() {
        val p = ScrollbarMath.progress(firstExact = 0f, total = 223, visibleItems = 8f, atTop = true, atBottom = false)
        assertEquals(0f, p, 0f)
        assertEquals(0f, ScrollbarMath.thumbTop(p, track, 44f), 0f)
        // 着いた印があれば、少し残っていても 0
        assertEquals(0f, ScrollbarMath.progress(0.4f, 223, 8f, atTop = true, atBottom = false), 0f)
    }

    @Test
    fun `一番下ではつまみの下端が溝の下端に届く`() {
        val thumb = ScrollbarMath.thumbLength(track, 223, 8f, min)
        val p = ScrollbarMath.progress(firstExact = 214.6f, total = 223, visibleItems = 8f, atTop = false, atBottom = true)
        assertEquals(1f, p, 0f)
        assertEquals(track, ScrollbarMath.thumbTop(p, track, thumb) + thumb, 0.01f)
    }

    @Test
    fun `途中では一番上の項目の番号の割合`() {
        // 割合どおりの長さの時は「番号 ÷ 全部の数」と同じ
        val thumb = ScrollbarMath.thumbLength(track, 100, 10f, min)
        val p = ScrollbarMath.progress(firstExact = 45f, total = 100, visibleItems = 10f, atTop = false, atBottom = false)
        assertEquals(0.5f, p, 0.0001f)
        assertEquals(450f, ScrollbarMath.thumbTop(p, track, thumb), 0.01f)
        // 最低の長さにのばした時は、残りの範囲で同じ割合
        val p2 = ScrollbarMath.progress(firstExact = 107.5f, total = 223, visibleItems = 8f, atTop = false, atBottom = false)
        assertEquals(0.5f, p2, 0.0001f)
        assertEquals(478f, ScrollbarMath.thumbTop(p2, track, 44f), 0.01f)
    }

    @Test
    fun `項目の高さが違っても、番号が進めばつまみは下にだけ動く`() {
        var last = -1f
        for (i in 0..215) {
            val top = ScrollbarMath.thumbTop(ScrollbarMath.progress(i.toFloat(), 223, 8f, i == 0, false), track, 44f)
            assertTrue(top >= last)
            last = top
        }
    }

    @Test
    fun `つまみを動かした位置から、一覧の一番上に出す項目`() {
        assertEquals(0, ScrollbarMath.targetIndex(0f, track, 44f, total = 223, visibleItems = 8f))
        // 一番下まで動かすと最後の項目（一覧は一番下に着く）
        assertEquals(222, ScrollbarMath.targetIndex(956f, track, 44f, total = 223, visibleItems = 8f))
        assertEquals(222, ScrollbarMath.targetIndex(2000f, track, 44f, total = 223, visibleItems = 8f))
        // 真ん中
        assertEquals(107, ScrollbarMath.targetIndex(478f, track, 44f, total = 223, visibleItems = 8f))
        // 上にはみ出しても 0
        assertEquals(0, ScrollbarMath.targetIndex(-50f, track, 44f, total = 223, visibleItems = 8f))
    }
}
