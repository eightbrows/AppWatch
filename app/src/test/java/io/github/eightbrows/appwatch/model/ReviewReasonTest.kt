package io.github.eightbrows.appwatch.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 確認が必要になった理由の札：出す理由の選び方と、優先の順。 */
class ReviewReasonTest {

    private fun app(status: ReviewStatus, warnings: Set<AppWarning>) = AppSummary(
        packageName = "com.example.app",
        name = "App",
        versionName = "1.0",
        kind = AppKind.USER_INSTALLED,
        source = InstallSource.OTHER,
        warnings = warnings,
        reviewStatus = status,
        adminMemo = null,
        installedAt = LocalDateTime.of(2026, 11, 1, 0, 0),
        isNew = true,
    )

    @Test
    fun allReasons_areInPriorityOrder() {
        val reasons = ReviewReason.of(AppWarning.entries.toSet(), ReviewStatus.CAUTION)
        assertEquals(
            listOf(
                ReviewReason.CAUTION,
                ReviewReason.REINSTALLED,
                ReviewReason.SIGNATURE_CHANGED,
                ReviewReason.DEBUG_SIGNATURE,
                ReviewReason.NOT_FROM_PLAY,
                ReviewReason.UNKNOWN_SOURCE,
            ),
            reasons,
        )
    }

    @Test
    fun order_doesNotDependOnInputOrder() {
        val reasons = ReviewReason.of(
            linkedSetOf(AppWarning.NOT_FROM_PLAY, AppWarning.DEBUG_SIGNATURE, AppWarning.REINSTALLED),
            ReviewStatus.UNCONFIRMED,
        )
        assertEquals(listOf(ReviewReason.REINSTALLED, ReviewReason.DEBUG_SIGNATURE, ReviewReason.NOT_FROM_PLAY), reasons)
    }

    @Test
    fun caution_isAddedOnlyForCautionStatus_andOnlyWhenAsked() {
        assertEquals(listOf(ReviewReason.CAUTION), ReviewReason.of(emptySet(), ReviewStatus.CAUTION))
        assertTrue(ReviewReason.of(emptySet(), ReviewStatus.UNCONFIRMED).isEmpty())
        assertEquals(
            listOf(ReviewReason.NOT_FROM_PLAY),
            ReviewReason.of(setOf(AppWarning.NOT_FROM_PLAY), ReviewStatus.CAUTION, includeCaution = false),
        )
    }

    @Test
    fun top_showsNoReasonsForOkApps() {
        assertTrue(ReviewReason.forTop(app(ReviewStatus.OK, setOf(AppWarning.NOT_FROM_PLAY))).isEmpty())
        assertEquals(
            listOf(ReviewReason.NOT_FROM_PLAY),
            ReviewReason.forTop(app(ReviewStatus.UNCONFIRMED, setOf(AppWarning.NOT_FROM_PLAY))),
        )
        assertEquals(
            listOf(ReviewReason.CAUTION, ReviewReason.REINSTALLED),
            ReviewReason.forTop(app(ReviewStatus.CAUTION, setOf(AppWarning.REINSTALLED))),
        )
    }

    @Test
    fun colorsAndSourceFlags() {
        assertTrue(ReviewReason.CAUTION.isCaution)
        ReviewReason.entries.filter { it != ReviewReason.CAUTION }.forEach { assertFalse(it.isCaution) }
        assertEquals(
            setOf(ReviewReason.NOT_FROM_PLAY, ReviewReason.UNKNOWN_SOURCE),
            ReviewReason.entries.filter { it.isSource }.toSet(),
        )
    }

    @Test
    fun self_showsNoWarningReasons() {
        val warnings = setOf(AppWarning.UNKNOWN_SOURCE, AppWarning.DEBUG_SIGNATURE, AppWarning.NOT_FROM_PLAY)
        assertTrue(ReviewReason.of(warnings, ReviewStatus.UNCONFIRMED, isSelf = true).isEmpty())
        assertTrue(ReviewReason.of(warnings, ReviewStatus.OK, includeCaution = false, isSelf = true).isEmpty())
        // トップ：問題なしでなくても、本アプリには札を出さない
        assertTrue(ReviewReason.forTop(app(ReviewStatus.UNCONFIRMED, warnings).copy(isSelf = true)).isEmpty())
        // 同じ警告でも、本アプリでなければ札を出す
        assertEquals(3, ReviewReason.forTop(app(ReviewStatus.UNCONFIRMED, warnings)).size)
    }

    @Test
    fun hidesSourceText_onlyWhenASourceReasonIsShown() {
        assertTrue(ReviewReason.hidesSourceText(listOf(ReviewReason.UNKNOWN_SOURCE)))
        assertTrue(ReviewReason.hidesSourceText(listOf(ReviewReason.DEBUG_SIGNATURE, ReviewReason.NOT_FROM_PLAY)))
        assertFalse(ReviewReason.hidesSourceText(listOf(ReviewReason.DEBUG_SIGNATURE, ReviewReason.REINSTALLED)))
        assertFalse(ReviewReason.hidesSourceText(emptyList()))
        // 本アプリ自身は札を出さないので、入手元の文はそのまま
        val self = ReviewReason.of(setOf(AppWarning.UNKNOWN_SOURCE), ReviewStatus.OK, includeCaution = false, isSelf = true)
        assertFalse(ReviewReason.hidesSourceText(self))
    }

    @Test
    fun explanation_listsAllSixInFixedOrder_andChecksOnlyTheApplyingOnes() {
        val items = ReviewReason.explanationItems(listOf(ReviewReason.REINSTALLED, ReviewReason.UNKNOWN_SOURCE))
        assertEquals(
            listOf(
                ReviewReason.NOT_FROM_PLAY,
                ReviewReason.UNKNOWN_SOURCE,
                ReviewReason.DEBUG_SIGNATURE,
                ReviewReason.SIGNATURE_CHANGED,
                ReviewReason.REINSTALLED,
                ReviewReason.CAUTION,
            ),
            items.map { it.first },
        )
        assertEquals(listOf(false, true, false, false, true, false), items.map { it.second })
        // 要注意だけのアプリ
        assertEquals(
            listOf(ReviewReason.CAUTION),
            ReviewReason.explanationItems(ReviewReason.of(emptySet(), ReviewStatus.CAUTION)).filter { it.second }.map { it.first },
        )
        // 当てはまるものがなくても6つ出す
        assertTrue(ReviewReason.explanationItems(emptyList()).none { it.second })
        assertEquals(ReviewReason.entries.toSet(), ReviewReason.EXPLANATION_ORDER.toSet())
    }

    @Test
    fun everyWarning_hasAReason() {
        AppWarning.entries.forEach { warning ->
            assertEquals(1, ReviewReason.of(setOf(warning), ReviewStatus.UNCONFIRMED).size)
        }
    }
}
