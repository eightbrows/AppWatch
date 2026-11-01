package io.github.eightbrows.appwatch.ui.top

import io.github.eightbrows.appwatch.data.dummy.DummyData
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TopListsTest {

    private fun app(
        pkg: String,
        status: ReviewStatus = ReviewStatus.UNCONFIRMED,
        warnings: Set<AppWarning> = emptySet(),
        isNew: Boolean = false,
        day: Int = 1,
        kind: AppKind = AppKind.USER_INSTALLED,
    ) = AppSummary(
        packageName = pkg,
        name = pkg,
        versionName = "1.0",
        kind = kind,
        source = InstallSource.GOOGLE_PLAY,
        warnings = warnings,
        reviewStatus = status,
        adminMemo = null,
        installedAt = LocalDateTime.of(2026, 10, day, 0, 0),
        isNew = isNew,
    )

    @Test
    fun cards_areOrderedCautionThenWarningThenOthers() {
        val lists = TopLists.from(
            listOf(
                app("normal", isNew = true, day = 10),
                app("warning", warnings = setOf(AppWarning.DEBUG_SIGNATURE), isNew = true, day = 5),
                app("caution", status = ReviewStatus.CAUTION, isNew = true, day = 1),
            ),
        )
        assertEquals(listOf("caution", "warning", "normal"), lists.cards.map { it.packageName })
    }

    @Test
    fun cautionApps_areShownAsCardsEvenIfNotNew() {
        val lists = TopLists.from(listOf(app("old_caution", status = ReviewStatus.CAUTION, isNew = false)))
        assertEquals(listOf("old_caution"), lists.cards.map { it.packageName })
        assertTrue(lists.unconfirmed.isEmpty())
    }

    @Test
    fun unconfirmed_excludesCardsAndConfirmedApps_newestFirst() {
        val lists = TopLists.from(
            listOf(
                app("new_one", isNew = true),
                app("ok_one", status = ReviewStatus.OK),
                app("older", day = 2),
                app("newer", day = 20),
            ),
        )
        assertEquals(listOf("newer", "older"), lists.unconfirmed.map { it.packageName })
    }

    @Test
    fun all_containsEveryApp_cautionFirst_okLast() {
        val lists = TopLists.from(
            listOf(app("b_ok", status = ReviewStatus.OK), app("a_unconfirmed"), app("c_caution", status = ReviewStatus.CAUTION)),
        )
        assertEquals(listOf("c_caution", "a_unconfirmed", "b_ok"), lists.all.map { it.packageName })
    }

    @Test
    fun unconfirmed_isOnlyUserInstalledApps() {
        // 仕様書 6章：プリインストールとシステムは、未確認でも「まだ確認していないアプリ」に出さない
        val lists = TopLists.from(
            listOf(
                app("user"),
                app("pre", kind = AppKind.PREINSTALLED),
                app("sys", kind = AppKind.SYSTEM),
            ),
        )
        assertEquals(listOf("user"), lists.unconfirmed.map { it.packageName })
        assertEquals(3, lists.all.size)
    }

    @Test
    fun newApps_areCardsRegardlessOfKind_andOkAppsAreNot() {
        val lists = TopLists.from(
            listOf(
                app("new_pre", isNew = true, kind = AppKind.PREINSTALLED),
                app("old_ok", status = ReviewStatus.OK),
            ),
        )
        assertEquals(listOf("new_pre"), lists.cards.map { it.packageName })
        assertTrue(lists.unconfirmed.isEmpty())
    }

    @Test
    fun all_isOrderedByStatusThenKindThenName() {
        val lists = TopLists.from(
            listOf(
                app("b_sys", kind = AppKind.SYSTEM),
                app("c_user"),
                app("a_pre", kind = AppKind.PREINSTALLED),
                app("z_caution", status = ReviewStatus.CAUTION, kind = AppKind.SYSTEM),
            ),
        )
        assertEquals(listOf("z_caution", "c_user", "a_pre", "b_sys"), lists.all.map { it.packageName })
    }

    @Test
    fun signatureChangedApp_isCardUntilOk() {
        // 仕様書 6章：署名が変わって「未確認」に戻ったアプリは、新しくなくてもカード（オレンジ枠）
        val sig = app("sig", warnings = setOf(AppWarning.SIGNATURE_CHANGED))
        val lists = TopLists.from(listOf(sig))
        assertEquals(listOf("sig"), lists.cards.map { it.packageName })
        assertTrue(lists.unconfirmed.isEmpty())
        // 「問題なし」なら（警告は消える決まりだが、残っていても）カードにしない
        val ok = TopLists.from(listOf(sig.copy(reviewStatus = ReviewStatus.OK)))
        assertTrue(ok.cards.isEmpty())
    }

    @Test
    fun dummyData_matchesMockup() {
        // 画面サンプル 01_top.png と同じく、新しく入ったアプリは3件（要注意・警告あり・普通の順）
        val lists = TopLists.from(DummyData.apps)
        assertEquals(
            listOf("com.example.qrreader", "com.example.cleanerpro", "jp.example.tenkinavi"),
            lists.cards.map { it.packageName },
        )
        assertEquals(DummyData.apps.size, lists.all.size)
        assertEquals(1, DummyData.apps.count { it.isSelf })
    }
}
