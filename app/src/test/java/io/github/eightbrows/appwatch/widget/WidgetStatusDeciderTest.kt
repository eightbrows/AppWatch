package io.github.eightbrows.appwatch.widget

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.CautionRule
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WidgetStatusDeciderTest {

    private val now = 100_000_000L
    private val minute = 60_000L

    /** 何も問題がない状態。各テストで1つずつ崩す。 */
    private val clear = WidgetInput(
        cautionCount = 0,
        needsReviewCount = 0,
        lastCheckAt = now - 10 * minute,
        now = now,
        checkIntervalMinutes = 30,
        deviceSettingsOk = true,
        setupCompleted = true,
    )

    private fun decide(input: WidgetInput) = WidgetStatusDecider.decide(input)

    // ---- 1つだけ当てはまるとき ----

    @Test
    fun nothingWrong_isAllClear() {
        val state = decide(clear)
        assertEquals(WidgetStatus.ALL_CLEAR, state.status)
        assertEquals(0, state.count)
        assertEquals(clear.lastCheckAt, state.lastCheckAt)
    }

    @Test
    fun cautionApps_showCount() {
        val state = decide(clear.copy(cautionCount = 2))
        assertEquals(WidgetStatus.CAUTION_APPS, state.status)
        assertEquals(2, state.count)
    }

    @Test
    fun needsReview_showCount() {
        val state = decide(clear.copy(needsReviewCount = 3))
        assertEquals(WidgetStatus.NEEDS_REVIEW, state.status)
        assertEquals(3, state.count)
    }

    @Test
    fun deviceSettingsOff_isSettingsNeeded() {
        assertEquals(WidgetStatus.SETTINGS_NEEDED, decide(clear.copy(deviceSettingsOk = false)).status)
    }

    @Test
    fun setupNotDone_isSetupRequired() {
        assertEquals(WidgetStatus.SETUP_REQUIRED, decide(clear.copy(setupCompleted = false)).status)
    }

    // ---- 優先度の順 ----

    @Test
    fun setupNotDone_overridesEverything() {
        // 初回設定の前はチェックを行わないので、他の状態より優先して初期設定の依頼を出す
        val everything = clear.copy(
            cautionCount = 1,
            needsReviewCount = 1,
            lastCheckAt = now - 200 * minute,
            deviceSettingsOk = false,
            setupCompleted = false,
        )
        assertEquals(WidgetStatus.SETUP_REQUIRED, decide(everything).status)
        assertEquals(0, decide(everything).count)
    }

    @Test
    fun priority_followsSpecOrder_afterSetup() {
        val everything = clear.copy(
            cautionCount = 1,
            needsReviewCount = 1,
            lastCheckAt = now - 200 * minute,
            deviceSettingsOk = false,
        )
        assertEquals(WidgetStatus.CAUTION_APPS, decide(everything).status)
        val noCaution = everything.copy(cautionCount = 0)
        assertEquals(WidgetStatus.CHECK_STOPPED, decide(noCaution).status)
        val notStopped = noCaution.copy(lastCheckAt = now)
        assertEquals(WidgetStatus.NEEDS_REVIEW, decide(notStopped).status)
        val noReview = notStopped.copy(needsReviewCount = 0)
        assertEquals(WidgetStatus.SETTINGS_NEEDED, decide(noReview).status)
        assertEquals(WidgetStatus.ALL_CLEAR, decide(noReview.copy(deviceSettingsOk = true)).status)
    }

    // ---- 停止の判定 ----

    @Test
    fun stopped_whenOlderThanThreeIntervals() {
        // 30分間隔なら、90分より前で停止
        assertFalse(WidgetStatusDecider.isStopped(now - 90 * minute, now, 30))
        assertTrue(WidgetStatusDecider.isStopped(now - 90 * minute - 1, now, 30))
        // 6時間間隔なら、18時間より前で停止
        assertFalse(WidgetStatusDecider.isStopped(now - 17 * 60 * minute, now, 360))
        assertTrue(WidgetStatusDecider.isStopped(now - 19 * 60 * minute, now, 360))
        // 12時間ごと：36時間を過ぎたら停止
        assertFalse(WidgetStatusDecider.isStopped(now - 36 * 60 * minute, now, 720))
        assertTrue(WidgetStatusDecider.isStopped(now - 36 * 60 * minute - 1, now, 720))
        // 24時間ごと：3日を過ぎたら停止（2日前のチェックはまだ停止ではない）
        assertFalse(WidgetStatusDecider.isStopped(now - 48 * 60 * minute, now, 1440))
        assertFalse(WidgetStatusDecider.isStopped(now - 72 * 60 * minute, now, 1440))
        assertTrue(WidgetStatusDecider.isStopped(now - 72 * 60 * minute - 1, now, 1440))
    }

    @Test
    fun neverChecked_isNotStopped() {
        assertFalse(WidgetStatusDecider.isStopped(null, now, 30))
        assertEquals(WidgetStatus.ALL_CLEAR, decide(clear.copy(lastCheckAt = null)).status)
    }

    @Test
    fun stoppedState_hasNoCount() {
        val state = decide(clear.copy(lastCheckAt = now - 100 * minute, needsReviewCount = 4))
        assertEquals(WidgetStatus.CHECK_STOPPED, state.status)
        assertEquals(0, state.count)
    }

    // ---- 件数の数え方（トップと同じ決まり） ----

    private fun app(
        pkg: String,
        status: ReviewStatus = ReviewStatus.UNCONFIRMED,
        isNew: Boolean = false,
        kind: AppKind = AppKind.USER_INSTALLED,
        warnings: Set<AppWarning> = emptySet(),
        enabled: Boolean = true,
    ) = AppSummary(
        packageName = pkg,
        name = pkg,
        versionName = "1",
        kind = kind,
        source = InstallSource.GOOGLE_PLAY,
        warnings = warnings,
        reviewStatus = status,
        adminMemo = null,
        installedAt = LocalDateTime.of(2026, 10, 1, 0, 0),
        isNew = isNew,
        isEnabled = enabled,
    )

    @Test
    fun countApps_matchesTopScreen() {
        val apps = listOf(
            app("caution", status = ReviewStatus.CAUTION),
            app("new", isNew = true),
            app("unconfirmed"),
            app("ok", status = ReviewStatus.OK),
            app("pre", kind = AppKind.PREINSTALLED), // 未確認なら、プリインストールも数える（種類で区別しない）
            app("sys", kind = AppKind.SYSTEM), // システムも数える
            app("pre_off", kind = AppKind.PREINSTALLED, enabled = false), // 未確認で無効も数える
            app("sys_ok", kind = AppKind.SYSTEM, status = ReviewStatus.OK),
            app("sig", warnings = setOf(AppWarning.SIGNATURE_CHANGED)), // 署名が変わったアプリはカード
        )
        // トップの「確認が必要」のタブの数（要注意 1 + それ以外 6）と同じ
        assertEquals(1 to 6, WidgetStatusDecider.countApps(apps))
        assertEquals(7, io.github.eightbrows.appwatch.ui.top.TopLists.from(apps).needsReviewCount)
    }

    @Test
    fun countApps_disabledCautionIsNotCounted_forEveryKind() {
        // 要注意で無効のアプリは、ウィジェットの「要注意」（赤）に数えない。有効なら数える。種類で区別しない
        for (kind in AppKind.entries) {
            val disabled = listOf(app("c", status = ReviewStatus.CAUTION, kind = kind, enabled = false))
            assertEquals("$kind", 0 to 0, WidgetStatusDecider.countApps(disabled))
            val enabled = listOf(app("c", status = ReviewStatus.CAUTION, kind = kind, enabled = true))
            assertEquals("$kind", 1 to 0, WidgetStatusDecider.countApps(enabled))
        }
        // 無効にした要注意のアプリしかなければ、赤（要注意）にならない
        val (caution, review) = WidgetStatusDecider.countApps(
            listOf(app("c", status = ReviewStatus.CAUTION, enabled = false), app("new", isNew = true)),
        )
        val state = WidgetStatusDecider.decide(
            WidgetInput(
                setupCompleted = true, cautionCount = caution, needsReviewCount = review,
                lastCheckAt = 0L, now = 0L, checkIntervalMinutes = 720, deviceSettingsOk = true,
            ),
        )
        assertEquals(WidgetStatus.NEEDS_REVIEW, state.status)
        assertEquals(1, state.count)
    }

    @Test
    fun cautionRule_isTheSameEverywhere() {
        assertEquals(true, CautionRule.counts(ReviewStatus.CAUTION, isEnabled = true))
        assertEquals(false, CautionRule.counts(ReviewStatus.CAUTION, isEnabled = false))
        assertEquals(false, CautionRule.counts(ReviewStatus.OK, isEnabled = true))
        assertEquals(true, CautionRule.isHandled(ReviewStatus.CAUTION, isEnabled = false))
        assertEquals(false, CautionRule.isHandled(ReviewStatus.UNCONFIRMED, isEnabled = false))
    }
}
