package io.github.eightbrows.appwatch.ui.top

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSearch
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** トップの検索（すべての一覧に効かせる）。 */
class TopSearchTest {

    private fun app(
        pkg: String,
        name: String,
        status: ReviewStatus = ReviewStatus.UNCONFIRMED,
        warnings: Set<AppWarning> = emptySet(),
        isNew: Boolean = false,
        day: Int = 1,
    ) = AppSummary(
        packageName = pkg,
        name = name,
        versionName = "1.0",
        kind = AppKind.USER_INSTALLED,
        source = InstallSource.GOOGLE_PLAY,
        warnings = warnings,
        reviewStatus = status,
        adminMemo = null,
        installedAt = LocalDateTime.of(2026, 10, day, 0, 0),
        isNew = isNew,
    )

    private val lists = TopLists.from(
        listOf(
            app("com.example.qr", "QRコードリーダー", isNew = true, day = 3),
            app("com.example.weather", "お天気ナビ", status = ReviewStatus.CAUTION, day = 2),
            app("jp.example.cleaner", "スマホ掃除", day = 5),
            app("com.example.notes", "メモ帳", status = ReviewStatus.OK, day = 4),
        ),
    )

    @Test
    fun blankQuery_returnsSameLists() {
        assertSame(lists, lists.filtered(""))
        assertSame(lists, lists.filtered("   "))
    }

    @Test
    fun query_appliesToAllLists() {
        val result = lists.filtered("com.example")
        assertEquals(listOf("com.example.weather", "com.example.qr"), result.cards.map { it.packageName })
        assertTrue(result.unconfirmed.isEmpty())
        assertEquals(setOf("com.example.qr", "com.example.weather", "com.example.notes"), result.all.mapTo(HashSet()) { it.packageName })
    }

    @Test
    fun query_matchesNameOrPackage_ignoringCase() {
        assertEquals(listOf("com.example.qr"), lists.filtered("qrコード").all.map { it.packageName })
        assertEquals(listOf("jp.example.cleaner"), lists.filtered("JP.EXAMPLE").unconfirmed.map { it.packageName })
        assertEquals(listOf("jp.example.cleaner"), lists.filtered(" 掃除 ").all.map { it.packageName })
    }

    @Test
    fun selfApp_matchesCurrentLanguageName() {
        // 本アプリ：記録した名前は英語、今の言語は日本語
        val self = app("io.github.eightbrows.appwatch", "AppWatch", status = ReviewStatus.OK).copy(isSelf = true)
        val withSelf = TopLists.from(listOf(self, app("com.example.notes", "メモ帳", status = ReviewStatus.OK)))
        assertEquals(listOf("io.github.eightbrows.appwatch"), withSelf.filtered("見守", "アプリ見守当番").all.map { it.packageName })
        assertEquals(listOf("io.github.eightbrows.appwatch"), withSelf.filtered("appwatch", "アプリ見守当番").all.map { it.packageName })
        // 今の言語の名前を渡さなければ当たらない。ほかのアプリには効かない
        assertTrue(withSelf.filtered("見守").all.isEmpty())
        assertEquals(listOf("com.example.notes"), withSelf.filtered("メモ", "アプリ見守当番").all.map { it.packageName })
    }

    @Test
    fun query_keepsOrderOfEachList() {
        val result = lists.filtered("example")
        assertEquals(lists.cards, result.cards)
        assertEquals(lists.unconfirmed, result.unconfirmed)
        assertEquals(lists.all, result.all)
    }

    @Test
    fun noMatch_givesEmptyLists() {
        val result = lists.filtered("calendar")
        assertTrue(result.cards.isEmpty())
        assertTrue(result.unconfirmed.isEmpty())
        assertTrue(result.all.isEmpty())
    }

    @Test
    fun appSearch_matches() {
        assertTrue(AppSearch.matches("QRコードリーダー", "com.example.qr", "QR"))
        assertTrue(AppSearch.matches("QRコードリーダー", "com.example.qr", "Example.Qr"))
        assertTrue(AppSearch.matches("QRコードリーダー", "com.example.qr", ""))
        assertFalse(AppSearch.matches("QRコードリーダー", "com.example.qr", "weather"))
    }

    @Test
    fun breakablePackageName_addsBreakAfterEachDot_withoutChangingVisibleText() {
        val shown = AppSearch.breakablePackageName("com.example.qr")
        assertEquals("com.​example.​qr", shown)
        assertEquals("com.example.qr", shown.replace("​", ""))
    }
}
