package io.github.eightbrows.appwatch.data.record

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.InstalledApp
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeDetectorTest {

    private val t0 = 1_000_000L // 最初のチェック
    private val t1 = 2_000_000L // 2回目
    private val t2 = 3_000_000L // 3回目

    private fun installed(
        pkg: String,
        versionCode: Long = 1,
        versionName: String = "1.0",
        enabled: Boolean = true,
        signer: String = "aaaa",
        kind: AppKind = AppKind.USER_INSTALLED,
        firstInstallTime: Long = 100,
        name: String = pkg,
        warnings: Set<AppWarning> = emptySet(),
    ) = InstalledApp(
        packageName = pkg,
        name = name,
        versionName = versionName,
        versionCode = versionCode,
        kind = kind,
        source = InstallSource.GOOGLE_PLAY,
        installerPackage = "com.android.vending",
        initiatingPackage = null,
        warnings = warnings,
        signerSha256 = if (signer.isEmpty()) emptyList() else listOf(signer),
        firstInstallTime = firstInstallTime,
        lastUpdateTime = firstInstallTime,
        isEnabled = enabled,
        isSelf = false,
    )

    private fun policy(pkg: String, status: ReviewStatus) = PolicyState(
        packageName = pkg,
        status = status,
        adminMemo = "memo",
        isPreRegistered = false,
        provisionalName = null,
        statusReason = null,
        updatedAt = 0,
    )

    /** 基準を作ってから、diff を1回行う。 */
    private fun afterBaseline(
        baseline: List<InstalledApp>,
        next: List<InstalledApp>,
        policies: Map<String, PolicyState> = emptyMap(),
    ): DiffResult = ChangeDetector.diff(ChangeDetector.baseline(baseline, t0), next, policies, t1)

    /** 前回の記録に diff の結果を当てはめる（データベースへの書き込みと同じ）。 */
    private fun apply(previous: List<TrackedApp>, result: DiffResult): List<TrackedApp> {
        val byPackage = previous.associateBy { it.packageName }.toMutableMap()
        result.changedApps.forEach { byPackage[it.packageName] = it }
        return byPackage.values.toList()
    }

    // ---- 最初のチェック ----

    @Test
    fun baseline_savesAllAppsAsPresent_withoutAddedAt() {
        val apps = ChangeDetector.baseline(listOf(installed("a"), installed("b")), t0)
        assertEquals(listOf("a", "b"), apps.map { it.packageName })
        assertTrue(apps.all { it.isPresent && it.addedAt == null && it.firstSeenAt == t0 })
        assertTrue(apps.none { it.isAddedAfterBaseline })
    }

    @Test
    fun noChange_producesNothing() {
        val result = afterBaseline(listOf(installed("a")), listOf(installed("a")))
        assertTrue(result.changes.isEmpty())
        assertTrue(result.changedApps.isEmpty())
        assertTrue(result.changedPolicies.isEmpty())
    }

    // ---- 追加・削除・再インストール ----

    @Test
    fun newApp_isAdded_andMarkedAsAddedAfterBaseline() {
        val result = afterBaseline(listOf(installed("a")), listOf(installed("a"), installed("new", versionName = "2.0")))
        val change = result.changes.single()
        assertEquals(ChangeType.ADDED, change.type)
        assertEquals("new", change.packageName)
        assertEquals("2.0", change.versionName)
        assertFalse(change.isReinstall)
        val app = result.changedApps.single()
        assertEquals(t1, app.addedAt)
        assertTrue(app.isAddedAfterBaseline)
        assertTrue(app.warnings.isEmpty())
    }

    @Test
    fun missingApp_isRemoved_andKeptAsNotPresent() {
        val result = afterBaseline(listOf(installed("a", versionName = "3.1")), emptyList())
        val change = result.changes.single()
        assertEquals(ChangeType.REMOVED, change.type)
        assertEquals("3.1", change.versionName) // 削除時のバージョン
        val app = result.changedApps.single()
        assertFalse(app.isPresent)
        assertEquals(t1, app.removedAt)
    }

    @Test
    fun alreadyRemovedApp_producesNoMoreChanges() {
        val first = ChangeDetector.baseline(listOf(installed("a")), t0)
        val removed = apply(first, ChangeDetector.diff(first, emptyList(), emptyMap(), t1))
        val again = ChangeDetector.diff(removed, emptyList(), emptyMap(), t2)
        assertTrue(again.changes.isEmpty())
        assertTrue(again.changedApps.isEmpty())
    }

    @Test
    fun reinstalledApp_isAddedWithReinstallFlag_andWarning() {
        val first = ChangeDetector.baseline(listOf(installed("qr")), t0)
        val removed = apply(first, ChangeDetector.diff(first, emptyList(), emptyMap(), t1))
        val result = ChangeDetector.diff(removed, listOf(installed("qr", firstInstallTime = 500)), emptyMap(), t2)

        val change = result.changes.single()
        assertEquals(ChangeType.ADDED, change.type)
        assertTrue(change.isReinstall)
        val app = result.changedApps.single()
        assertTrue(app.isPresent)
        assertNull(app.removedAt)
        assertEquals(t2, app.addedAt)
        assertEquals(t2, app.reinstalledAt)
        assertEquals(t0, app.firstSeenAt)
        assertEquals(setOf(AppWarning.REINSTALLED), app.warnings)
    }

    @Test
    fun reinstalledApp_keepsPolicyUntouched_whenSignatureIsSame() {
        val first = ChangeDetector.baseline(listOf(installed("qr")), t0)
        val removed = apply(first, ChangeDetector.diff(first, emptyList(), emptyMap(), t1))
        val policies = mapOf("qr" to policy("qr", ReviewStatus.CAUTION))
        val result = ChangeDetector.diff(removed, listOf(installed("qr")), policies, t2)
        assertTrue(result.changedPolicies.isEmpty())
    }

    @Test
    fun reinstalledApp_withDifferentSigner_isAlsoSignatureChanged() {
        val first = ChangeDetector.baseline(listOf(installed("bank")), t0)
        val removed = apply(first, ChangeDetector.diff(first, emptyList(), emptyMap(), t1))
        val policies = mapOf("bank" to policy("bank", ReviewStatus.OK))
        val result = ChangeDetector.diff(removed, listOf(installed("bank", signer = "bbbb")), policies, t2)

        assertEquals(
            listOf(ChangeType.ADDED, ChangeType.SIGNATURE_CHANGED, ChangeType.STATUS_CHANGED),
            result.changes.map { it.type },
        )
        assertEquals(ReviewStatus.UNCONFIRMED, result.changedPolicies.single().status)
        assertEquals(setOf(AppWarning.REINSTALLED, AppWarning.SIGNATURE_CHANGED), result.changedApps.single().warnings)
    }

    @Test
    fun quickReinstallBetweenChecks_isDetectedByInstallTime() {
        val result = afterBaseline(
            listOf(installed("a", firstInstallTime = 100)),
            listOf(installed("a", firstInstallTime = 900)),
        )
        val change = result.changes.single()
        assertEquals(ChangeType.ADDED, change.type)
        assertTrue(change.isReinstall)
        assertEquals(t1, result.changedApps.single().reinstalledAt)
        assertEquals(t1, result.changedApps.single().addedAt)
    }

    // ---- 更新・有効化・無効化 ----

    @Test
    fun versionChange_isUpdated_withOldAndNewVersion_andPolicyKept() {
        val policies = mapOf("a" to policy("a", ReviewStatus.OK))
        val result = afterBaseline(
            listOf(installed("a", versionCode = 1, versionName = "1.0")),
            listOf(installed("a", versionCode = 2, versionName = "1.1")),
            policies,
        )
        val change = result.changes.single()
        assertEquals(ChangeType.UPDATED, change.type)
        assertEquals("1.0", change.oldVersionName)
        assertEquals("1.1", change.versionName)
        assertEquals(1L, change.oldVersionCode)
        assertEquals(2L, change.versionCode)
        // アップデートでは確認状態を変えない（仕様書 4章）
        assertTrue(result.changedPolicies.isEmpty())
        assertNull(result.changedApps.single().addedAt)
    }

    @Test
    fun enabledChange_isDisabledThenEnabled() {
        val first = ChangeDetector.baseline(listOf(installed("a")), t0)
        val disabled = ChangeDetector.diff(first, listOf(installed("a", enabled = false)), emptyMap(), t1)
        assertEquals(ChangeType.DISABLED, disabled.changes.single().type)
        val enabled = ChangeDetector.diff(apply(first, disabled), listOf(installed("a")), emptyMap(), t2)
        assertEquals(ChangeType.ENABLED, enabled.changes.single().type)
    }

    @Test
    fun nameChangeOnly_updatesRow_withoutChange() {
        val result = afterBaseline(listOf(installed("a", name = "Old")), listOf(installed("a", name = "New")))
        assertTrue(result.changes.isEmpty())
        assertEquals("New", result.changedApps.single().name)
        assertEquals(t1, result.changedApps.single().updatedAt)
    }

    // ---- 署名が変わった ----

    @Test
    fun signatureChange_resetsOkToUnconfirmed_withReason() {
        val policies = mapOf("a" to policy("a", ReviewStatus.OK))
        val result = afterBaseline(listOf(installed("a")), listOf(installed("a", signer = "bbbb")), policies)

        assertEquals(listOf(ChangeType.SIGNATURE_CHANGED, ChangeType.STATUS_CHANGED), result.changes.map { it.type })
        val statusChange = result.changes.last()
        assertEquals(ReviewStatus.OK, statusChange.oldStatus)
        assertEquals(ReviewStatus.UNCONFIRMED, statusChange.newStatus)
        assertEquals(StatusChangeReason.SIGNATURE_CHANGED, statusChange.reason)

        val policy = result.changedPolicies.single()
        assertEquals(ReviewStatus.UNCONFIRMED, policy.status)
        assertEquals(StatusChangeReason.SIGNATURE_CHANGED, policy.statusReason)
        assertEquals("memo", policy.adminMemo) // メモは残す
        assertEquals(t1, result.changedApps.single().signatureChangedAt)
        assertEquals(setOf(AppWarning.SIGNATURE_CHANGED), result.changedApps.single().warnings)
    }

    @Test
    fun signatureChange_keepsCautionAndUnconfirmed() {
        val caution = afterBaseline(
            listOf(installed("a")),
            listOf(installed("a", signer = "bbbb")),
            mapOf("a" to policy("a", ReviewStatus.CAUTION)),
        )
        assertTrue(caution.changedPolicies.isEmpty())
        val noPolicy = afterBaseline(listOf(installed("a")), listOf(installed("a", signer = "bbbb")))
        assertTrue(noPolicy.changedPolicies.isEmpty())
        assertEquals(ChangeType.SIGNATURE_CHANGED, noPolicy.changes.single().type)
    }

    @Test
    fun unknownSigner_isNotTreatedAsChange() {
        assertFalse(ChangeDetector.signerChanged(listOf("aaaa"), emptyList()))
        assertFalse(ChangeDetector.signerChanged(emptyList(), listOf("aaaa")))
        assertFalse(ChangeDetector.signerChanged(listOf("a", "b"), listOf("b", "a")))
        assertTrue(ChangeDetector.signerChanged(listOf("a"), listOf("b")))
        val result = afterBaseline(listOf(installed("a")), listOf(installed("a", signer = "")))
        assertTrue(result.changes.isEmpty())
    }

    // ---- その他 ----

    @Test
    fun warnings_areOnlyForUserInstalledApps() {
        val first = ChangeDetector.baseline(listOf(installed("pre", kind = AppKind.PREINSTALLED)), t0)
        val result = ChangeDetector.diff(first, listOf(installed("pre", kind = AppKind.PREINSTALLED, signer = "bbbb")), emptyMap(), t1)
        assertEquals(ChangeType.SIGNATURE_CHANGED, result.changes.single().type) // 変化は記録する
        assertTrue(result.changedApps.single().warnings.isEmpty()) // 警告は付けない
    }

    @Test
    fun changes_areOrderedForReading() {
        val result = afterBaseline(
            listOf(installed("gone"), installed("upd", versionCode = 1), installed("dis")),
            listOf(installed("upd", versionCode = 2), installed("dis", enabled = false), installed("new")),
        )
        assertEquals(
            listOf(ChangeType.REMOVED, ChangeType.ADDED, ChangeType.UPDATED, ChangeType.DISABLED),
            result.changes.map { it.type },
        )
        assertTrue(result.changes.all { it.occurredAt == t1 })
    }
}
