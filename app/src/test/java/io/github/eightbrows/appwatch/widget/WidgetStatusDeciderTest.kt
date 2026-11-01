package io.github.eightbrows.appwatch.widget

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.AppWarning
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
    )

    @Test
    fun countApps_matchesTopScreen() {
        val apps = listOf(
            app("caution", status = ReviewStatus.CAUTION),
            app("new", isNew = true),
            app("unconfirmed"),
            app("ok", status = ReviewStatus.OK),
            app("pre", kind = AppKind.PREINSTALLED), // プリインストールは数えない
            app("sig", warnings = setOf(AppWarning.SIGNATURE_CHANGED)), // 署名が変わったアプリはカード
        )
        assertEquals(1 to 3, WidgetStatusDecider.countApps(apps))
    }
}
