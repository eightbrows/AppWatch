package io.github.eightbrows.appwatch.data.status

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 「最近の更新」の見出しの組（今日・昨日・2〜6日前は日ごと・7日以上前は月ごと）。 */
sealed interface UpdateGroup {
    data object Today : UpdateGroup
    data object Yesterday : UpdateGroup
    data class Day(val date: LocalDate) : UpdateGroup
    data class Month(val month: YearMonth) : UpdateGroup
}

/**
 * 確認状態の画面の「最近の更新」のタブ（仕様書 6章）。Android を使わない純粋な関数だけを置く。
 * 更新の日時は、Android の「最終更新日時」（lastUpdateTime）と「最初に入った日時」（firstInstallTime）。
 */
object RecentUpdates {

    /** 日ごとの見出しにする範囲（2〜6日前）。これより前は月ごと。 */
    const val LAST_DAY_GROUP = 6L

    /**
     * 「最近の更新」に出すアプリか：導入済で、最終更新日時が最初に入った日時より後（1回でも更新された）。
     * 未導入のアプリと、日時の分からないアプリ（0 以下・なし）は出さない。
     */
    fun isUpdated(entry: AppStatusEntry): Boolean {
        val first = entry.firstInstallTime ?: return false
        val last = entry.lastUpdateTime ?: return false
        return entry.isPresent && first > 0 && last > first
    }

    /** データに更新の日時があるか（導入済のアプリに、日時の分かるものが1つでもあるか）。 */
    fun hasTimes(entries: List<AppStatusEntry>): Boolean =
        entries.any { it.isPresent && (it.firstInstallTime ?: 0) > 0 && (it.lastUpdateTime ?: 0) > 0 }

    /** 並び：最終更新日時の新しい順。同じ時刻ならアプリ名の順。 */
    val ORDER: Comparator<AppStatusEntry> = compareByDescending<AppStatusEntry> { it.lastUpdateTime ?: 0 }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.packageName }

    /** [entries]（絞り込み・検索の後の行）から、更新のあったアプリを新しい順に並べる。 */
    fun list(entries: List<AppStatusEntry>): List<AppStatusEntry> = entries.filter(::isUpdated).sortedWith(ORDER)

    /** 更新の日時が入る組。暦の日で数える（[today] より先の日時は「今日」に入れる）。 */
    fun groupOf(timeMillis: Long, today: LocalDate, zone: ZoneId): UpdateGroup {
        val date = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days <= 0 -> UpdateGroup.Today
            days == 1L -> UpdateGroup.Yesterday
            days <= LAST_DAY_GROUP -> UpdateGroup.Day(date)
            else -> UpdateGroup.Month(YearMonth.from(date))
        }
    }

    /**
     * 見出しの組に分ける。[sorted] は [list] で並べたもの（新しい順なので、同じ組は続いて並ぶ）。
     * 0件の組は入らない。組の中の順は [sorted] のまま。
     */
    fun sections(sorted: List<AppStatusEntry>, today: LocalDate, zone: ZoneId): List<Pair<UpdateGroup, List<AppStatusEntry>>> {
        val result = ArrayList<Pair<UpdateGroup, MutableList<AppStatusEntry>>>()
        for (entry in sorted) {
            val group = groupOf(entry.lastUpdateTime ?: continue, today, zone)
            val last = result.lastOrNull()
            if (last != null && last.first == group) last.second.add(entry) else result.add(group to mutableListOf(entry))
        }
        return result
    }
}
