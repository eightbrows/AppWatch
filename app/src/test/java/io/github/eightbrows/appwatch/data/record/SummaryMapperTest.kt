package io.github.eightbrows.appwatch.data.record

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class SummaryMapperTest {

    private val self = "io.github.eightbrows.appwatch"

    private fun tracked(pkg: String = "a", addedAt: Long? = null, reinstalledAt: Long? = null) = TrackedApp(
        packageName = pkg,
        name = "A",
        versionName = null,
        versionCode = 1,
        kind = AppKind.USER_INSTALLED,
        source = InstallSource.OTHER,
        installerPackage = "x",
        initiatingPackage = null,
        isEnabled = false,
        staticWarnings = setOf(AppWarning.NOT_FROM_PLAY),
        signerSha256 = listOf("s"),
        firstInstallTime = 0,
        lastUpdateTime = 0,
        isPresent = true,
        firstSeenAt = 0,
        addedAt = addedAt,
        removedAt = null,
        reinstalledAt = reinstalledAt,
        signatureChangedAt = null,
        updatedAt = 0,
    )

    private fun policy(status: ReviewStatus, memo: String? = null) = PolicyState(
        packageName = "a",
        status = status,
        adminMemo = memo,
        isPreRegistered = false,
        provisionalName = null,
        statusReason = null,
        updatedAt = 0,
    )

    @Test
    fun withoutPolicy_isUnconfirmed() {
        val summary = tracked().toSummary(null, self, ZoneOffset.UTC)
        assertEquals(ReviewStatus.UNCONFIRMED, summary.reviewStatus)
        assertNull(summary.adminMemo)
        assertEquals("", summary.versionName)
        assertFalse(summary.isEnabled)
    }

    @Test
    fun isNew_whenAddedAfterBaseline_andNotOk() {
        assertTrue(tracked(addedAt = 5).toSummary(null, self).isNew)
        assertTrue(tracked(addedAt = 5).toSummary(policy(ReviewStatus.CAUTION), self).isNew)
        assertFalse(tracked(addedAt = 5).toSummary(policy(ReviewStatus.OK), self).isNew)
        assertFalse(tracked(addedAt = null).toSummary(null, self).isNew) // 基準のアプリ
    }

    @Test
    fun reinstalledApp_showsPreviousPolicy_andReinstallWarning() {
        val summary = tracked(addedAt = 9, reinstalledAt = 9).toSummary(policy(ReviewStatus.CAUTION, "削除が必要"), self)
        assertEquals(ReviewStatus.CAUTION, summary.reviewStatus)
        assertEquals("削除が必要", summary.adminMemo)
        assertEquals(setOf(AppWarning.NOT_FROM_PLAY, AppWarning.REINSTALLED), summary.warnings)
    }

    @Test
    fun selfApp_isLabelled() {
        assertTrue(tracked(pkg = self).toSummary(null, self).isSelf)
        assertFalse(tracked(pkg = "other").toSummary(null, self).isSelf)
    }
}
