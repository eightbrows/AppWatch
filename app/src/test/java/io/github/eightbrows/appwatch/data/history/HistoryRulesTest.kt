package io.github.eightbrows.appwatch.data.history

import io.github.eightbrows.appwatch.data.record.AppChange
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.data.record.StatusChangeReason
import io.github.eightbrows.appwatch.data.system.ScanDay
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryRulesTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.of(2026, 10, 11)

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun change(
        type: ChangeType,
        time: String,
        packageName: String = "jp.example.app",
        name: String = "アプリ",
        version: String? = "1.0",
        oldVersion: String? = null,
        source: InstallSource? = InstallSource.GOOGLE_PLAY,
        isReinstall: Boolean = false,
    ) = AppChange(
        type = type,
        packageName = packageName,
        name = name,
        occurredAt = at(time),
        versionName = version,
        versionCode = 1,
        oldVersionName = oldVersion,
        source = source,
        isReinstall = isReinstall,
    )

    private fun day(date: String, count: Int = 1) = ScanDay(date = date, count = count, lastAt = 0)

    private val range = DateRange(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 11))

    // ---- 期間の境目 ----

    @Test
    fun 比較元の0時ちょうどと比較先の235959は含み_翌日の0時は含まない() {
        assertTrue(HistoryRules.contains(range, at("2026-10-04T00:00:00"), zone))
        assertFalse(HistoryRules.contains(range, at("2026-10-03T23:59:59.999"), zone))
        assertTrue(HistoryRules.contains(range, at("2026-10-11T23:59:59"), zone))
        assertTrue(HistoryRules.contains(range, at("2026-10-11T23:59:59.999"), zone))
        assertFalse(HistoryRules.contains(range, at("2026-10-12T00:00:00"), zone))
    }

    @Test
    fun 境目の変化だけが一覧と件数に入る() {
        val changes = listOf(
            change(ChangeType.ADDED, "2026-10-03T23:59:59", packageName = "jp.example.before"),
            change(ChangeType.ADDED, "2026-10-04T00:00:00", packageName = "jp.example.first"),
            change(ChangeType.ADDED, "2026-10-11T23:59:59", packageName = "jp.example.last"),
            change(ChangeType.ADDED, "2026-10-12T00:00:00", packageName = "jp.example.after"),
        )
        val result = HistoryRules.build(changes, emptyList(), range, emptySet(), zone)
        assertEquals(listOf("jp.example.last", "jp.example.first"), result.rows.map { it.packageName })
        assertEquals(2, result.counts[HistoryKind.ADDED])
    }

    @Test
    fun 期間は端末の時間帯の日付で区切る() {
        // 同じ時刻でも、時間帯が違えば別の日になる
        val time = at("2026-10-12T08:00:00") // 東京の 10/12 8:00 = UTC の 10/11 23:00
        assertFalse(HistoryRules.contains(range, time, zone))
        assertTrue(HistoryRules.contains(range, time, ZoneId.of("UTC")))
    }

    // ---- すぐ選べる期間 ----

    @Test
    fun 直近7日と直近30日は今日を含む() {
        val first = LocalDate.of(2026, 8, 1)
        assertEquals(DateRange(LocalDate.of(2026, 10, 5), today), HistoryRules.quickRange(QuickRange.LAST_7_DAYS, first, today))
        assertEquals(DateRange(LocalDate.of(2026, 9, 12), today), HistoryRules.quickRange(QuickRange.LAST_30_DAYS, first, today))
        assertEquals(DateRange(first, today), HistoryRules.quickRange(QuickRange.ALL, first, today))
    }

    @Test
    fun 最初のチェックの日より前には広げない() {
        val first = LocalDate.of(2026, 10, 8)
        assertEquals(DateRange(first, today), HistoryRules.quickRange(QuickRange.LAST_7_DAYS, first, today))
        assertEquals(DateRange(first, today), HistoryRules.quickRange(QuickRange.LAST_30_DAYS, first, today))
        // 3つとも同じ期間になる（前回以降は出していない）
        assertEquals(setOf(QuickRange.LAST_7_DAYS, QuickRange.LAST_30_DAYS, QuickRange.ALL), HistoryRules.matchingQuick(DateRange(first, today), first, today))
    }

    @Test
    fun 今の期間と同じボタンだけを強調する() {
        val first = LocalDate.of(2026, 8, 1)
        assertEquals(setOf(QuickRange.LAST_7_DAYS), HistoryRules.matchingQuick(DateRange(LocalDate.of(2026, 10, 5), today), first, today))
        assertEquals(setOf(QuickRange.ALL), HistoryRules.matchingQuick(DateRange(first, today), first, today))
        assertEquals(emptySet<QuickRange>(), HistoryRules.matchingQuick(range, first, today))
    }

    @Test
    fun まだチェックがなければ今日だけ() {
        assertEquals(DateRange(today, today), HistoryRules.quickRange(QuickRange.ALL, null, today))
        assertEquals(DateRange(today, today), HistoryRules.quickRange(QuickRange.LAST_7_DAYS, null, today))
    }

    // ---- 選べる日 ----

    @Test
    fun チェックのない日は選べない() {
        val days = listOf(day("2026-10-01"), day("2026-10-02"), day("2026-10-05"), day("2026-10-06", count = 0))
        val checked = HistoryRules.checkedDates(days, today)
        assertTrue(HistoryRules.isSelectable(LocalDate.of(2026, 10, 1), checked, today))
        assertTrue(HistoryRules.isSelectable(LocalDate.of(2026, 10, 5), checked, today))
        assertFalse(HistoryRules.isSelectable(LocalDate.of(2026, 10, 3), checked, today)) // チェックがない
        assertFalse(HistoryRules.isSelectable(LocalDate.of(2026, 10, 6), checked, today)) // 回数が 0
        assertFalse(HistoryRules.isSelectable(LocalDate.of(2026, 9, 30), checked, today)) // 最初のチェックより前
        assertEquals(LocalDate.of(2026, 10, 1), HistoryRules.firstCheckDate(days, today))
    }

    @Test
    fun 今日より後の日は選べない() {
        val days = listOf(day("2026-10-10"), day("2026-10-12")) // 時計を戻した端末など
        val checked = HistoryRules.checkedDates(days, today)
        assertFalse(HistoryRules.isSelectable(LocalDate.of(2026, 10, 12), checked, today))
        assertTrue(HistoryRules.isSelectable(LocalDate.of(2026, 10, 10), checked, today))
    }

    @Test
    fun 選んだ期間を選べる範囲に収める() {
        val first = LocalDate.of(2026, 10, 5)
        assertEquals(DateRange(first, today), HistoryRules.clamp(DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1)), first, today))
    }

    // ---- 件数 ----

    @Test
    fun 有効化は無効化に数え_確認状態の変更などは数えない() {
        val changes = listOf(
            change(ChangeType.ADDED, "2026-10-05T10:00", packageName = "a.a"),
            change(ChangeType.REMOVED, "2026-10-05T11:00", packageName = "b.b"),
            change(ChangeType.UPDATED, "2026-10-06T10:00", packageName = "c.c", version = "2.0", oldVersion = "1.0"),
            change(ChangeType.DISABLED, "2026-10-07T10:00", packageName = "d.d"),
            change(ChangeType.ENABLED, "2026-10-08T10:00", packageName = "d.d"),
            change(ChangeType.SIGNATURE_CHANGED, "2026-10-09T10:00", packageName = "e.e"),
            // 端末の変化でないもの
            AppChange(
                type = ChangeType.STATUS_CHANGED, packageName = "a.a", name = "a", occurredAt = at("2026-10-05T12:00"),
                oldStatus = ReviewStatus.UNCONFIRMED, newStatus = ReviewStatus.OK, reason = StatusChangeReason.ADMIN,
            ),
            AppChange(type = ChangeType.MEMO_CHANGED, packageName = "a.a", name = "a", occurredAt = at("2026-10-05T12:00"), newAdminMemo = "メモ"),
            AppChange(type = ChangeType.PRE_REGISTERED, packageName = "x.x", name = "x", occurredAt = at("2026-10-05T12:00")),
            AppChange(type = ChangeType.UNREGISTERED, packageName = "x.x", name = "x", occurredAt = at("2026-10-06T12:00")),
            AppChange(
                type = ChangeType.STATUS_CHANGED, packageName = "a.a", name = "a", occurredAt = at("2026-10-05T12:01"),
                oldStatus = ReviewStatus.OK, newStatus = ReviewStatus.UNCONFIRMED, reason = StatusChangeReason.UNDO,
            ),
        )
        val result = HistoryRules.build(changes, emptyList(), range, emptySet(), zone)
        assertEquals(
            mapOf(
                HistoryKind.ADDED to 1,
                HistoryKind.REMOVED to 1,
                HistoryKind.UPDATED to 1,
                HistoryKind.DISABLED to 2,
                HistoryKind.SIGNATURE to 1,
            ),
            result.counts,
        )
        assertEquals(6, result.rows.size)
        assertTrue(result.rows.none { it.type == ChangeType.STATUS_CHANGED || it.type == ChangeType.MEMO_CHANGED })
    }

    @Test
    fun 件数が0の種類も入る() {
        val result = HistoryRules.build(listOf(change(ChangeType.ADDED, "2026-10-05T10:00")), emptyList(), range, emptySet(), zone)
        assertEquals(HistoryKind.entries.toSet(), result.counts.keys)
        assertEquals(0, result.counts[HistoryKind.SIGNATURE])
    }

    @Test
    fun 同じアプリが期間内に何度か変わればそれぞれ1行() {
        val changes = listOf(
            change(ChangeType.UPDATED, "2026-10-05T10:00", version = "1.1", oldVersion = "1.0"),
            change(ChangeType.UPDATED, "2026-10-07T10:00", version = "1.2", oldVersion = "1.1"),
            change(ChangeType.REMOVED, "2026-10-09T10:00", version = "1.2"),
        )
        val result = HistoryRules.build(changes, emptyList(), range, emptySet(), zone)
        assertEquals(listOf(ChangeType.REMOVED, ChangeType.UPDATED, ChangeType.UPDATED), result.rows.map { it.type })
        assertEquals(listOf("1.2", "1.2", "1.1"), result.rows.map { it.versionName })
    }

    @Test
    fun チェックの回数は期間内の日の合計() {
        val days = listOf(day("2026-10-03", 5), day("2026-10-04", 2), day("2026-10-11", 3), day("2026-10-12", 7))
        assertEquals(5, HistoryRules.build(emptyList(), days, range, emptySet(), zone).checkCount)
    }

    // ---- 種類の表示・非表示 ----

    @Test
    fun 非表示にした種類は一覧から外し_件数は残す() {
        val changes = listOf(
            change(ChangeType.ADDED, "2026-10-05T10:00", packageName = "a.a"),
            change(ChangeType.UPDATED, "2026-10-06T10:00", packageName = "c.c", version = "2.0", oldVersion = "1.0"),
            change(ChangeType.ENABLED, "2026-10-07T10:00", packageName = "d.d"),
        )
        val hideUpdated = HistoryRules.build(changes, emptyList(), range, setOf(HistoryKind.UPDATED), zone)
        assertEquals(listOf(ChangeType.ENABLED, ChangeType.ADDED), hideUpdated.rows.map { it.type })
        assertEquals(1, hideUpdated.counts[HistoryKind.UPDATED])
        // 無効化を非表示にすると、有効化も外れる
        val hideDisabled = HistoryRules.build(changes, emptyList(), range, setOf(HistoryKind.DISABLED), zone)
        assertEquals(listOf(ChangeType.UPDATED, ChangeType.ADDED), hideDisabled.rows.map { it.type })
    }

    @Test
    fun 初期化の行は件数ボタンに関係なく出す() {
        val changes = listOf(
            change(ChangeType.ADDED, "2026-10-05T10:00"),
            AppChange(type = ChangeType.RESET, packageName = "", name = "", occurredAt = at("2026-10-06T10:00")),
        )
        val result = HistoryRules.build(changes, emptyList(), range, HistoryKind.entries.toSet(), zone)
        assertEquals(listOf(ChangeType.RESET), result.rows.map { it.type })
        assertEquals(1, result.counts.values.sum()) // 追加の1件だけ（初期化は数えない）
        assertEquals(1, result.resetCount)
    }

    // ---- 変化がないときの案内 ----

    @Test
    fun 変化がないときの案内() {
        val days = listOf(day("2026-10-05", 4))
        assertEquals(HistoryEmpty.NO_CHANGES, HistoryRules.emptyReason(HistoryRules.build(emptyList(), days, range, emptySet(), zone)))
        assertEquals(HistoryEmpty.NO_CHECKS, HistoryRules.emptyReason(HistoryRules.build(emptyList(), emptyList(), range, emptySet(), zone)))
        val added = listOf(change(ChangeType.ADDED, "2026-10-05T10:00"))
        assertEquals(
            HistoryEmpty.ALL_HIDDEN,
            HistoryRules.emptyReason(HistoryRules.build(added, days, range, HistoryKind.entries.toSet(), zone)),
        )
        assertNull(HistoryRules.emptyReason(HistoryRules.build(added, days, range, emptySet(), zone)))
        // 確認状態の変更だけなら「変化はありません」
        val statusOnly = listOf(
            AppChange(type = ChangeType.STATUS_CHANGED, packageName = "a.a", name = "a", occurredAt = at("2026-10-05T12:00")),
        )
        assertEquals(HistoryEmpty.NO_CHANGES, HistoryRules.emptyReason(HistoryRules.build(statusOnly, days, range, emptySet(), zone)))
    }

    // ---- 共有の文字 ----

    private val texts = HistoryShareTexts(
        title = "アプリ見守当番 履歴と比較",
        periodFormat = "期間：%1\$s 〜 %2\$s",
        datePattern = "yyyy/MM/dd",
        countFormat = "%1\$s %2\$d",
        countSeparator = " ・ ",
        excludedFormat = "（%1\$sは除く）",
        kindJoiner = "・",
        kindLabels = mapOf(
            HistoryKind.ADDED to "追加",
            HistoryKind.REMOVED to "削除",
            HistoryKind.UPDATED to "更新",
            HistoryKind.DISABLED to "無効化",
            HistoryKind.SIGNATURE to "署名",
        ),
        typeLabels = mapOf(
            ChangeType.ADDED to "追加",
            ChangeType.REMOVED to "削除",
            ChangeType.UPDATED to "更新",
            ChangeType.ENABLED to "有効化",
            ChangeType.DISABLED to "無効化",
            ChangeType.SIGNATURE_CHANGED to "署名が変わった",
            ChangeType.RESET to "初期化",
        ),
        markFormat = "[%1\$s]",
        reinstallSuffix = "（再インストール）",
        versionFormat = "v%1\$s",
        updateVersionFormat = "v%1\$s → v%2\$s",
        removedVersionFormat = "v%1\$s（削除時）",
        packageFormat = "(%1\$s)",
        rowTimePattern = "MM/dd HH:mm",
        rowTimeWithYearPattern = "yyyy/MM/dd HH:mm",
        sourceLabels = mapOf(
            InstallSource.GOOGLE_PLAY to "Google Play",
            InstallSource.OTHER to "Google Play 以外",
            InstallSource.UNKNOWN to "入手元が分からない",
        ),
        signatureNote = "別の作り手のアプリに入れ替わった可能性",
        resetText = "記録を取り直しました",
    )

    @Test
    fun 共有の文字_画面サンプルの例() {
        val changes = listOf(
            change(ChangeType.ADDED, "2026-10-10T21:30", "jp.example.cleanerpro", "クリーナー Pro", "1.0.3", source = InstallSource.OTHER),
            change(ChangeType.ADDED, "2026-10-09T09:00", "jp.example.tenkinavi", "天気ナビ", "4.2.0"),
            change(ChangeType.UPDATED, "2026-10-08T09:00", "jp.example.news", "ニュースプラス", "5.1", oldVersion = "5.0"),
            change(ChangeType.REMOVED, "2026-10-05T14:30", "jp.example.kenshou", "懸賞チェッカー", "2.0.1"),
            change(ChangeType.SIGNATURE_CHANGED, "2026-10-04T22:00", "jp.example.kakeimemo", "家計メモ", "2.3.0"),
        )
        val result = HistoryRules.build(changes, emptyList(), range, setOf(HistoryKind.UPDATED), zone)
        val expected = listOf(
            "アプリ見守当番 履歴と比較",
            "期間：2026/10/04 〜 2026/10/11",
            "追加 2 ・ 削除 1 ・ 無効化 0 ・ 署名 1（更新は除く）",
            "[追加] クリーナー Pro v1.0.3 (jp.example.cleanerpro) 10/10 21:30 Google Play 以外",
            "[追加] 天気ナビ v4.2.0 (jp.example.tenkinavi) 10/09 09:00 Google Play",
            "[削除] 懸賞チェッカー v2.0.1（削除時） (jp.example.kenshou) 10/05 14:30",
            "[署名が変わった] 家計メモ v2.3.0 (jp.example.kakeimemo) 10/04 22:00 別の作り手のアプリに入れ替わった可能性",
        ).joinToString("\n")
        assertEquals(expected, HistoryRules.shareText(result, texts, zone))
    }

    @Test
    fun 共有の文字_更新_再インストール_有効化_複数の非表示() {
        val changes = listOf(
            change(ChangeType.UPDATED, "2026-10-08T09:05", "jp.example.news", "ニュースプラス", "5.1", oldVersion = "5.0"),
            change(ChangeType.ADDED, "2026-10-07T08:00", "jp.example.game", "ゲーム", "1.0", isReinstall = true),
            change(ChangeType.ENABLED, "2026-10-06T07:00", "jp.example.mail", "メール", "3.0"),
        )
        val all = HistoryRules.shareText(HistoryRules.build(changes, emptyList(), range, emptySet(), zone), texts, zone).lines()
        assertEquals("追加 1 ・ 削除 0 ・ 更新 1 ・ 無効化 1 ・ 署名 0", all[2])
        assertEquals("[更新] ニュースプラス v5.0 → v5.1 (jp.example.news) 10/08 09:05 Google Play", all[3])
        assertEquals("[追加（再インストール）] ゲーム v1.0 (jp.example.game) 10/07 08:00 Google Play", all[4])
        assertEquals("[有効化] メール v3.0 (jp.example.mail) 10/06 07:00", all[5])

        val hidden = setOf(HistoryKind.REMOVED, HistoryKind.SIGNATURE)
        val partial = HistoryRules.shareText(HistoryRules.build(changes, emptyList(), range, hidden, zone), texts, zone).lines()
        assertEquals("追加 1 ・ 更新 1 ・ 無効化 1（削除・署名は除く）", partial[2])
    }

    @Test
    fun 共有の文字_年をまたぐ期間は行の日時に年を付ける() {
        val yearRange = DateRange(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 2))
        val changes = listOf(change(ChangeType.ADDED, "2025-12-31T23:00", "a.b", "アプリ", "1.0"))
        val lines = HistoryRules.shareText(HistoryRules.build(changes, emptyList(), yearRange, emptySet(), zone), texts, zone).lines()
        assertEquals("期間：2025/12/30 〜 2026/01/02", lines[1])
        assertEquals("[追加] アプリ v1.0 (a.b) 2025/12/31 23:00 Google Play", lines[3])
    }

    @Test
    fun 共有の文字_初期化の行() {
        val changes = listOf(AppChange(type = ChangeType.RESET, packageName = "", name = "", occurredAt = at("2026-10-06T10:00")))
        val lines = HistoryRules.shareText(HistoryRules.build(changes, emptyList(), range, emptySet(), zone), texts, zone).lines()
        assertEquals("[初期化] 記録を取り直しました 10/06 10:00", lines[3])
    }

    // ---- 取り込んだデータ（書き出し日を今日とする）・前回以降 ----

    @Test
    fun 取り込んだデータは書き出し日を今日として数える() {
        val exportDate = LocalDate.of(2026, 10, 28)
        val clock = HistoryClock(today = exportDate, firstCheckDate = LocalDate.of(2026, 9, 1))
        assertEquals(DateRange(LocalDate.of(2026, 10, 22), exportDate), HistoryRules.quickRange(QuickRange.LAST_7_DAYS, clock))
        assertEquals(DateRange(LocalDate.of(2026, 9, 29), exportDate), HistoryRules.quickRange(QuickRange.LAST_30_DAYS, clock))
        // 書き出し日より後の日は選べない
        val checked = HistoryRules.checkedDates(listOf(day("2026-10-27"), day("2026-10-30")), exportDate)
        assertFalse(HistoryRules.isSelectable(LocalDate.of(2026, 10, 30), checked, exportDate))
    }

    @Test
    fun 前回以降は前回の書き出し日から今回の書き出し日まで() {
        val clock = HistoryClock(today = LocalDate.of(2026, 10, 28), firstCheckDate = LocalDate.of(2026, 9, 1), previousExportDate = LocalDate.of(2026, 10, 20))
        assertEquals(DateRange(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 28)), HistoryRules.quickRange(QuickRange.SINCE_LAST, clock))
        assertEquals(listOf(QuickRange.LAST_7_DAYS, QuickRange.LAST_30_DAYS, QuickRange.SINCE_LAST, QuickRange.ALL), clock.quickButtons)
        // 前回の書き出し日がなければ、ボタンを出さない
        assertEquals(listOf(QuickRange.LAST_7_DAYS, QuickRange.LAST_30_DAYS, QuickRange.ALL), clock.copy(previousExportDate = null).quickButtons)
    }

    // ---- 強調するボタン（1つだけ） ----

    @Test
    fun 最後に押したボタンだけを青くする() {
        // 最初のチェックが最近なので、直近7日・直近30日・全期間が同じ期間になる
        val clock = HistoryClock(today = today, firstCheckDate = LocalDate.of(2026, 10, 8))
        val range = HistoryRules.quickRange(QuickRange.LAST_30_DAYS, clock)
        assertEquals(QuickRange.LAST_30_DAYS, HistoryRules.highlighted(QuickRange.LAST_30_DAYS, range, clock))
        assertEquals(QuickRange.ALL, HistoryRules.highlighted(QuickRange.ALL, range, clock))
    }

    @Test
    fun カレンダーで選んだときは同じ期間のボタンのうち最も短いもの() {
        val clock = HistoryClock(today = today, firstCheckDate = LocalDate.of(2026, 10, 8))
        val picked = DateRange(LocalDate.of(2026, 10, 8), today)
        assertEquals(QuickRange.LAST_7_DAYS, HistoryRules.highlighted(null, picked, clock))
        val longClock = HistoryClock(today = today, firstCheckDate = LocalDate.of(2026, 9, 20))
        assertEquals(QuickRange.LAST_30_DAYS, HistoryRules.highlighted(null, DateRange(LocalDate.of(2026, 9, 20), today), longClock))
        // どのボタンとも違う期間なら、どれも青くしない
        assertNull(HistoryRules.highlighted(null, DateRange(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10)), longClock))
    }

    @Test
    fun 前回以降を押していても_出していなければ使わない() {
        val clock = HistoryClock(today = today, firstCheckDate = LocalDate.of(2026, 10, 8))
        val range = HistoryRules.quickRange(QuickRange.LAST_7_DAYS, clock)
        assertEquals(QuickRange.LAST_7_DAYS, HistoryRules.highlighted(QuickRange.SINCE_LAST, range, clock))
    }

    @Test
    fun 共有の文字_取り込んだデータは端末の行を入れる() {
        val result = HistoryRules.build(listOf(change(ChangeType.ADDED, "2026-10-05T10:00")), emptyList(), range, emptySet(), zone)
        val lines = HistoryRules.shareText(result, texts, zone, deviceLine = "端末：端末A（2026/10/28 21:00 時点）").lines()
        assertEquals("アプリ見守当番 履歴と比較", lines[0])
        assertEquals("端末：端末A（2026/10/28 21:00 時点）", lines[1])
        assertEquals("期間：2026/10/04 〜 2026/10/11", lines[2])
    }
}
