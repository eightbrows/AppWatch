package io.github.eightbrows.appwatch.ui.top

import io.github.eightbrows.appwatch.data.dummy.DummyData
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.model.topAction
import io.github.eightbrows.appwatch.model.RemoveAction
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
        enabled: Boolean = true,
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
        isEnabled = enabled,
    )

    @Test
    fun disabledCautionApps_areNotCards_andEnabledOnesAre() {
        // 要注意で無効のアプリは対応が済んだものとして、カードに出さない（種類で区別しない）。「すべて」には出る
        val apps = AppKind.entries.flatMap { kind ->
            listOf(
                app("off_${kind.name}", status = ReviewStatus.CAUTION, kind = kind, enabled = false, isNew = true),
                app("on_${kind.name}", status = ReviewStatus.CAUTION, kind = kind),
            )
        }
        val lists = TopLists.from(apps)
        assertEquals(AppKind.entries.map { "on_${it.name}" }.toSet(), lists.cards.map { it.packageName }.toSet())
        assertTrue(lists.unconfirmed.isEmpty())
        assertEquals(apps.size, lists.all.size)
        // 署名が変わった警告があっても、要注意で無効ならカードに出さない
        val signature = app("sig", status = ReviewStatus.CAUTION, warnings = setOf(AppWarning.SIGNATURE_CHANGED), enabled = false)
        assertTrue(TopLists.from(listOf(signature)).cards.isEmpty())
        // 要注意でない無効のアプリは今までどおり（新しく入ったアプリならカードに出る）
        val newDisabled = app("new_off", isNew = true, enabled = false)
        assertEquals(listOf("new_off"), TopLists.from(listOf(newDisabled)).cards.map { it.packageName })
    }

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
    fun all_isOrderedByStatusThenEnabledThenName() {
        // 種類では並べない（ステップ29）。確認状態 → 有効が先・無効は組の一番下 → 名前
        val lists = TopLists.from(
            listOf(
                app("b_sys", kind = AppKind.SYSTEM),
                app("c_user"),
                app("a_pre", kind = AppKind.PREINSTALLED),
                app("z_caution", status = ReviewStatus.CAUTION, kind = AppKind.SYSTEM),
            ),
        )
        assertEquals(listOf("z_caution", "a_pre", "b_sys", "c_user"), lists.all.map { it.packageName })
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

    @Test
    fun needsReviewSections_sumToTabCount_andEmptyGroupsAreLeftOut() {
        val lists = TopLists.from(
            listOf(
                app("caution", status = ReviewStatus.CAUTION),
                app("new", isNew = true),
                app("sig", warnings = setOf(AppWarning.SIGNATURE_CHANGED), kind = AppKind.PREINSTALLED),
                app("unconfirmed"),
                app("ok", status = ReviewStatus.OK),
            ),
        )
        assertEquals(listOf("caution"), lists.cautionCards.map { it.packageName })
        assertEquals(setOf("new", "sig"), lists.changedCards.map { it.packageName }.toSet())
        assertEquals(listOf("unconfirmed"), lists.unconfirmed.map { it.packageName })
        // 3つの組の合計がタブの「確認が必要 n」
        assertEquals(4, lists.needsReviewCount)
        assertEquals(lists.needsReviewCount, lists.cautionCards.size + lists.changedCards.size + lists.unconfirmed.size)
        // 要注意がなければ、その組は空（見出しごと出さない）
        val noCaution = TopLists.from(listOf(app("new", isNew = true)))
        assertTrue(noCaution.cautionCards.isEmpty())
        assertEquals(1, noCaution.changedCards.size)
        assertEquals(1, noCaution.needsReviewCount)
        // 検索の後も、合計はタブの数と同じ
        val filtered = lists.filtered("caution")
        assertEquals(1, filtered.needsReviewCount)
        assertEquals(1, filtered.cautionCards.size + filtered.changedCards.size + filtered.unconfirmed.size)
    }

    @Test
    fun allSections_sumToTabCount_disabledAppsGoLast_andEmptyGroupsAreLeftOut() {
        val lists = TopLists.from(
            listOf(
                app("a_caution_off", status = ReviewStatus.CAUTION, enabled = false),
                app("z_caution", status = ReviewStatus.CAUTION),
                app("b_un", kind = AppKind.PREINSTALLED),
                app("a_un_off", enabled = false),
                app("c_un"),
                app("b_ok_off", status = ReviewStatus.OK, enabled = false, kind = AppKind.SYSTEM),
                app("a_ok", status = ReviewStatus.OK),
            ),
        )
        val sections = lists.allSections
        assertEquals(listOf(ReviewStatus.CAUTION, ReviewStatus.UNCONFIRMED, ReviewStatus.OK), sections.map { it.first })
        // 無効のアプリは組の一番下（名前が先でも）
        assertEquals(listOf("z_caution", "a_caution_off"), sections[0].second.map { it.packageName })
        assertEquals(listOf("b_un", "c_un", "a_un_off"), sections[1].second.map { it.packageName })
        assertEquals(listOf("a_ok", "b_ok_off"), sections[2].second.map { it.packageName })
        // 合計はタブの「すべて n」
        assertEquals(lists.all.size, sections.sumOf { it.second.size })
        assertEquals(lists.all, sections.flatMap { it.second })
        // 検索の後も合計が同じ。0件の組は入れない
        val found = lists.filtered("_ok")
        assertEquals(listOf(ReviewStatus.OK), found.allSections.map { it.first })
        assertEquals(found.all.size, found.allSections.sumOf { it.second.size })
        assertTrue(TopLists.from(emptyList()).allSections.isEmpty())
    }

    @Test
    fun disabledApps_openAppInfoInsteadOfDelete() {
        assertEquals(RemoveAction.APP_INFO, app("u", enabled = false).topAction)
        assertEquals(RemoveAction.APP_INFO, app("s", enabled = false, kind = AppKind.SYSTEM).topAction)
        assertEquals(RemoveAction.UNINSTALL, app("u2").topAction)
        assertEquals(RemoveAction.NONE, app("s2", kind = AppKind.SYSTEM).topAction)
    }
}
