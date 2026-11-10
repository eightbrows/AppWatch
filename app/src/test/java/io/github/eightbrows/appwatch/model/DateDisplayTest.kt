package io.github.eightbrows.appwatch.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** 日付の曜日と「何日前」（ステップ18）。書式は strings.xml の本物を読んで確かめる。 */
class DateDisplayTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val utc = ZoneId.of("UTC")

    private fun millis(text: String, zone: ZoneId = tokyo): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun patterns(folder: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/strings.xml"))
        val list = doc.documentElement.getElementsByTagName("string")
        return (0 until list.length).map { list.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    private val english by lazy { patterns("values") }
    private val japanese by lazy { patterns("values-ja") }

    // ---- 何日前 ----

    @Test
    fun sameDay_isToday() {
        val now = millis("2026-11-10T21:00")
        assertEquals(DaysAgo.Today, DateDisplay.daysAgo(millis("2026-11-10T00:01"), now, tokyo))
        assertEquals(DaysAgo.Today, DateDisplay.daysAgo(millis("2026-11-10T20:59"), now, tokyo))
    }

    @Test
    fun previousCalendarDay_isYesterday_evenIfOnlyMinutesAgo() {
        // 時刻ではなく暦の日で数える：昨夜 23:50 は、10分前でも「昨日」
        val now = millis("2026-11-11T00:00")
        assertEquals(DaysAgo.Yesterday, DateDisplay.daysAgo(millis("2026-11-10T23:50"), now, tokyo))
        // 前の日の朝でも「昨日」（24時間より前でも2日前にしない）
        assertEquals(DaysAgo.Yesterday, DateDisplay.daysAgo(millis("2026-11-10T00:01"), millis("2026-11-11T23:59"), tokyo))
    }

    @Test
    fun olderDays_areCounted() {
        val now = millis("2026-11-10T07:02")
        assertEquals(DaysAgo.Days(2), DateDisplay.daysAgo(millis("2026-11-08T21:16"), now, tokyo))
        assertEquals(DaysAgo.Days(21), DateDisplay.daysAgo(millis("2026-10-20T09:05"), now, tokyo))
    }

    @Test
    fun acrossYears() {
        assertEquals(DaysAgo.Yesterday, DateDisplay.daysAgo(millis("2025-12-31T22:00"), millis("2026-01-01T08:00"), tokyo))
        assertEquals(DaysAgo.Days(433), DateDisplay.daysAgo(millis("2025-09-03T10:00"), millis("2026-11-10T10:00"), tokyo))
        assertFalse(DateDisplay.isSameYear(millis("2025-12-31T22:00"), millis("2026-01-01T08:00"), tokyo))
        assertTrue(DateDisplay.isSameYear(millis("2026-01-01T00:00"), millis("2026-12-31T23:59"), tokyo))
    }

    @Test
    fun timeZoneBoundary_usesTheDevicesZone() {
        // 同じ2つの時刻でも、東京では日をまたぎ（昨日）、UTC では同じ日（今日）
        val time = millis("2026-11-10T23:30", tokyo) // = 14:30 UTC
        val now = millis("2026-11-11T00:10", tokyo) // = 15:10 UTC
        assertEquals(DaysAgo.Yesterday, DateDisplay.daysAgo(time, now, tokyo))
        assertEquals(DaysAgo.Today, DateDisplay.daysAgo(time, now, utc))
    }

    @Test
    fun futureTime_isToday() {
        // 時計のずれで先の日時になっても、「-1日前」にはしない
        assertEquals(DaysAgo.Today, DateDisplay.daysAgo(millis("2026-11-12T09:00"), millis("2026-11-11T09:00"), tokyo))
    }

    // ---- 曜日の形（strings.xml の書式） ----

    private fun format(time: String, now: String, p: Map<String, String>, key: String, locale: Locale) =
        DateDisplay.format(millis(time), millis(now), tokyo, p.getValue(key), p.getValue("${key.replace("_time", "")}_with_year"), locale)

    @Test
    fun japanese_weekdayInParentheses() {
        val now = "2026-11-10T09:00"
        assertEquals("11月8日(日) 21:16", format("2026-11-08T21:16", now, japanese, "format_date_time", Locale.JAPANESE))
        assertEquals("11/8(日) 21:16", format("2026-11-08T21:16", now, japanese, "format_list_date_time", Locale.JAPANESE))
        // 違う年は年を出し、時刻は出さない
        assertEquals("2025/9/3(水)", format("2025-09-03T10:00", now, japanese, "format_list_date_time", Locale.JAPANESE))
        assertEquals("2025年9月3日(水)", format("2025-09-03T10:00", now, japanese, "format_date_time", Locale.JAPANESE))
    }

    @Test
    fun english_weekdayAndMonthFromLocale() {
        val now = "2026-11-10T09:00"
        assertEquals("Sun, Nov 8, 21:16", format("2026-11-08T21:16", now, english, "format_date_time", Locale.ENGLISH))
        assertEquals("Sun, Nov 8, 21:16", format("2026-11-08T21:16", now, english, "format_list_date_time", Locale.ENGLISH))
        assertEquals("Wed, Sep 3, 2025", format("2025-09-03T10:00", now, english, "format_list_date_time", Locale.ENGLISH))
    }

    @Test
    fun shortAndWidgetFormats_haveWeekday() {
        val time = LocalDateTime.parse("2026-11-08T21:16")
        fun fmt(p: String, locale: Locale) = java.time.format.DateTimeFormatter.ofPattern(p, locale).format(time)
        assertEquals("11/8(日)", fmt(japanese.getValue("format_short_date"), Locale.JAPANESE))
        assertEquals("Sun 11/8", fmt(english.getValue("format_short_date"), Locale.ENGLISH))
        assertEquals("11/8(日) 21:16", fmt(japanese.getValue("format_widget_date_time"), Locale.JAPANESE))
        assertEquals("Sun 11/8 21:16", fmt(english.getValue("format_widget_date_time"), Locale.ENGLISH))
    }
}
