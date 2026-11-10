package io.github.eightbrows.appwatch.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 「何日前」。時刻ではなく、端末の時間帯の暦の日で数える。 */
sealed interface DaysAgo {
    /** 同じ日（先の日時も、時計のずれとして「今日」にする） */
    data object Today : DaysAgo

    /** 前の日 */
    data object Yesterday : DaysAgo

    /** 2日以上前 */
    data class Days(val count: Long) : DaysAgo
}

/** 画面に出す日付（曜日と何日前）の決まり。Android を使わない純粋な関数。 */
object DateDisplay {

    private fun dateOf(timeMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()

    /** 暦の日でいくつ前か（同じ日なら 0、前の日なら 1）。 */
    fun daysBetween(timeMillis: Long, nowMillis: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(dateOf(timeMillis, zone), dateOf(nowMillis, zone))

    fun daysAgo(timeMillis: Long, nowMillis: Long, zone: ZoneId): DaysAgo =
        when (val days = daysBetween(timeMillis, nowMillis, zone)) {
            in Long.MIN_VALUE..0L -> DaysAgo.Today
            1L -> DaysAgo.Yesterday
            else -> DaysAgo.Days(days)
        }

    /** 今と同じ年か（違う年なら年も出す）。 */
    fun isSameYear(timeMillis: Long, nowMillis: Long, zone: ZoneId): Boolean =
        dateOf(timeMillis, zone).year == dateOf(nowMillis, zone).year

    /**
     * 曜日付きの日時。今年なら [pattern]、違う年なら [withYearPattern]（strings.xml の書式）。
     * 曜日・月名は [locale] から作る（自前で持たない）。
     */
    fun format(
        timeMillis: Long,
        nowMillis: Long,
        zone: ZoneId,
        pattern: String,
        withYearPattern: String,
        locale: Locale,
    ): String {
        val chosen = if (isSameYear(timeMillis, nowMillis, zone)) pattern else withYearPattern
        return DateTimeFormatter.ofPattern(chosen, locale).format(Instant.ofEpochMilli(timeMillis).atZone(zone))
    }
}
