package io.github.eightbrows.appwatch.data.status

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSearch
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** 確認状態の画面の「最近の更新」のタブ：対象・並び・日付の組分け・絞り込みと検索、本アプリの名前の検索。 */
class RecentUpdatesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")
    private val today: LocalDate = LocalDate.of(2026, 11, 16)

    private fun at(month: Int, day: Int, hour: Int = 12, minute: Int = 0, year: Int = 2026): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val installed = at(1, 1, year = 2025)

    private fun entry(
        pkg: String,
        updated: Long?,
        name: String = pkg,
        first: Long? = installed,
        present: Boolean = true,
        kind: AppKind? = AppKind.USER_INSTALLED,
        isSelf: Boolean = false,
    ) = AppStatusEntry(
        packageName = pkg, name = name, versionName = "1.0", kind = kind,
        source = if (kind == AppKind.USER_INSTALLED) InstallSource.GOOGLE_PLAY else null,
        installerPackage = null, initiatingPackage = null, warnings = emptySet(),
        status = ReviewStatus.OK, statusReason = null, adminMemo = null,
        isPresent = present, isPreRegistered = false, isEnabled = true, isSelf = isSelf,
        firstInstallTime = first, lastUpdateTime = updated,
        removedAt = null, reinstalledAt = null, signatureChangedAt = null,
    )

    // ---- 対象 ----

    @Test
    fun `更新のあったアプリだけを出す`() {
        assertTrue(RecentUpdates.isUpdated(entry("updated", at(11, 15))))
        // 最終更新日時が最初に入った日時と同じ（一度も更新されていない）
        assertFalse(RecentUpdates.isUpdated(entry("never", installed)))
        // 最終更新日時の方が前
        assertFalse(RecentUpdates.isUpdated(entry("older", installed - 1)))
    }

    @Test
    fun `未導入のアプリと、日時の分からないアプリは出さない`() {
        assertFalse(RecentUpdates.isUpdated(entry("removed", at(11, 15), present = false)))
        assertFalse(RecentUpdates.isUpdated(entry("no_last", null)))
        assertFalse(RecentUpdates.isUpdated(entry("no_first", at(11, 15), first = null)))
        assertFalse(RecentUpdates.isUpdated(entry("zero_first", at(11, 15), first = 0)))
        assertFalse(RecentUpdates.isUpdated(entry("zero_both", 0, first = 0)))
    }

    @Test
    fun `データに更新の日時があるか`() {
        assertTrue(RecentUpdates.hasTimes(listOf(entry("a", installed))))
        assertFalse(RecentUpdates.hasTimes(listOf(entry("a", 0, first = 0), entry("b", null, first = null))))
        // 未導入のアプリの日時は数えない
        assertFalse(RecentUpdates.hasTimes(listOf(entry("removed", at(11, 15), present = false))))
        assertFalse(RecentUpdates.hasTimes(emptyList()))
    }

    // ---- 並び ----

    @Test
    fun `最終更新日時の新しい順、同じ時刻ならアプリ名の順`() {
        val entries = listOf(
            entry("1", at(11, 10), name = "Old"),
            entry("2", at(11, 15, 9), name = "beta"),
            entry("3", at(11, 16, 8), name = "Newest"),
            entry("4", at(11, 15, 9), name = "Alpha"),
            entry("5", installed, name = "Never"),
        )
        assertEquals(listOf("3", "4", "2", "1"), RecentUpdates.list(entries).map { it.packageName })
    }

    // ---- 日付の組 ----

    @Test
    fun `今日・昨日・日ごと・月ごとに分ける`() {
        assertEquals(UpdateGroup.Today, RecentUpdates.groupOf(at(11, 16, 0, 0), today, zone))
        assertEquals(UpdateGroup.Today, RecentUpdates.groupOf(at(11, 16, 23, 59), today, zone))
        assertEquals(UpdateGroup.Yesterday, RecentUpdates.groupOf(at(11, 15, 23, 59), today, zone))
        assertEquals(UpdateGroup.Yesterday, RecentUpdates.groupOf(at(11, 15, 0, 0), today, zone))
        // 2〜6日前は日ごと
        assertEquals(UpdateGroup.Day(LocalDate.of(2026, 11, 14)), RecentUpdates.groupOf(at(11, 14, 23, 59), today, zone))
        assertEquals(UpdateGroup.Day(LocalDate.of(2026, 11, 10)), RecentUpdates.groupOf(at(11, 10, 0, 0), today, zone))
        // 7日以上前は月ごと（同じ月でも）
        assertEquals(UpdateGroup.Month(YearMonth.of(2026, 11)), RecentUpdates.groupOf(at(11, 9, 23, 59), today, zone))
        assertEquals(UpdateGroup.Month(YearMonth.of(2026, 10)), RecentUpdates.groupOf(at(10, 31), today, zone))
        assertEquals(UpdateGroup.Month(YearMonth.of(2025, 12)), RecentUpdates.groupOf(at(12, 31, year = 2025), today, zone))
    }

    @Test
    fun `今日より先の日時は今日に入れる`() {
        assertEquals(UpdateGroup.Today, RecentUpdates.groupOf(at(11, 17, 3), today, zone))
    }

    @Test
    fun `日ごとの組は月をまたいでも日ごと`() {
        val nov3 = LocalDate.of(2026, 11, 3)
        assertEquals(UpdateGroup.Day(LocalDate.of(2026, 10, 30)), RecentUpdates.groupOf(at(10, 30), nov3, zone))
        assertEquals(UpdateGroup.Month(YearMonth.of(2026, 10)), RecentUpdates.groupOf(at(10, 27), nov3, zone))
    }

    @Test
    fun `見出しの組は新しい順で、0件の組はなく、件数の合計は一覧と同じ`() {
        val list = RecentUpdates.list(
            listOf(
                entry("today1", at(11, 16, 9)),
                entry("today2", at(11, 16, 7)),
                entry("yesterday", at(11, 15, 20)),
                entry("day14", at(11, 14, 10)),
                entry("day11a", at(11, 11, 10)),
                entry("day11b", at(11, 11, 9)),
                entry("nov", at(11, 2)),
                entry("oct1", at(10, 20)),
                entry("oct2", at(10, 1)),
                entry("never", installed),
            ),
        )
        val sections = RecentUpdates.sections(list, today, zone)
        assertEquals(
            listOf(
                UpdateGroup.Today to listOf("today1", "today2"),
                UpdateGroup.Yesterday to listOf("yesterday"),
                UpdateGroup.Day(LocalDate.of(2026, 11, 14)) to listOf("day14"),
                UpdateGroup.Day(LocalDate.of(2026, 11, 11)) to listOf("day11a", "day11b"),
                UpdateGroup.Month(YearMonth.of(2026, 11)) to listOf("nov"),
                UpdateGroup.Month(YearMonth.of(2026, 10)) to listOf("oct1", "oct2"),
            ),
            sections.map { (group, entries) -> group to entries.map { it.packageName } },
        )
        assertEquals(list.size, sections.sumOf { it.second.size })
        assertEquals(list, sections.flatMap { it.second })
    }

    @Test
    fun `更新がなければ組もない`() {
        assertTrue(RecentUpdates.sections(RecentUpdates.list(listOf(entry("never", installed))), today, zone).isEmpty())
    }

    // ---- 絞り込みと検索 ----

    @Test
    fun `絞り込みと検索が最近の更新にも効く`() {
        val entries = listOf(
            entry("com.example.user", at(11, 15), name = "User App"),
            entry("com.example.system", at(11, 16), name = "System App", kind = AppKind.SYSTEM),
            entry("com.example.pre", at(11, 14), name = "Preinstalled", kind = AppKind.PREINSTALLED),
        )
        val all = FilterItem.entries.toSet()
        fun recent(enabled: Set<FilterItem>, query: String) =
            RecentUpdates.list(AppStatusRules.visible(entries, enabled, query)).map { it.packageName }

        assertEquals(listOf("com.example.system", "com.example.user", "com.example.pre"), recent(all, ""))
        // 初期値ではシステムが外れる
        assertEquals(listOf("com.example.user", "com.example.pre"), recent(StatusFilter.DEFAULT_ENABLED, ""))
        // 検索
        assertEquals(listOf("com.example.pre"), recent(all, "preinst"))
        assertEquals(emptyList<String>(), recent(StatusFilter.DEFAULT_ENABLED, "system"))
    }

    // ---- 本アプリの名前の検索 ----

    @Test
    fun `本アプリは今の言語の名前でも検索に当たる`() {
        // 記録した名前は英語、今の言語は日本語
        val self = entry("io.github.eightbrows.appwatch", at(11, 15), name = "AppWatch", isSelf = true)
        val other = entry("com.example.other", at(11, 15), name = "Other")
        assertTrue(AppStatusRules.matchesQuery(self, "見守", selfName = "アプリ見守当番"))
        // 記録した名前・パッケージ名でも今までどおり当たる
        assertTrue(AppStatusRules.matchesQuery(self, "appwatch", selfName = "アプリ見守当番"))
        assertTrue(AppStatusRules.matchesQuery(self, "eightbrows", selfName = "アプリ見守当番"))
        // ほかのアプリには効かない
        assertFalse(AppStatusRules.matchesQuery(other, "見守", selfName = "アプリ見守当番"))
        // 今の言語の名前を渡さなければ今までどおり
        assertFalse(AppStatusRules.matchesQuery(self, "見守"))
        assertEquals(
            listOf("io.github.eightbrows.appwatch"),
            AppStatusRules.visible(listOf(self, other), FilterItem.entries.toSet(), "アプリ見守", "アプリ見守当番").map { it.packageName },
        )
    }

    @Test
    fun `検索のもう1つの名前は大文字・小文字を区別しない`() {
        assertTrue(AppSearch.matches("アプリ見守当番", "io.github.eightbrows.appwatch", "APPW", extraName = "AppWatch"))
        assertTrue(AppSearch.matches("アプリ見守当番", "pkg", " watch ", extraName = "AppWatch"))
        assertFalse(AppSearch.matches("アプリ見守当番", "pkg", "watch", extraName = null))
    }
}
