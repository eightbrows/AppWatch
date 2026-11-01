package io.github.eightbrows.appwatch.data.record

import io.github.eightbrows.appwatch.model.InstalledApp
import io.github.eightbrows.appwatch.model.ReviewStatus

/**
 * 前回の記録と今回の取得を比べて、変化を作る（仕様書 5章）。Android と Room を使わない純粋な関数だけを置く。
 */
object ChangeDetector {

    /** 最初のチェック：全件を基準にする。変化は記録しない。 */
    fun baseline(scanned: List<InstalledApp>, now: Long): List<TrackedApp> =
        scanned.map { it.toTracked(firstSeenAt = now, addedAt = null, now = now) }

    /**
     * 2回目以降のチェック。
     * @param previous app_current の全行（端末にないアプリも含む）
     * @param scanned 今回、端末から取得したアプリ
     * @param policies 管理者の設定（パッケージ名 → 設定）
     */
    fun diff(
        previous: Collection<TrackedApp>,
        scanned: List<InstalledApp>,
        policies: Map<String, PolicyState>,
        now: Long,
    ): DiffResult {
        val previousByPackage = previous.associateBy { it.packageName }
        val scannedPackages = scanned.mapTo(HashSet()) { it.packageName }
        val changedApps = mutableListOf<TrackedApp>()
        val changes = mutableListOf<AppChange>()
        val changedPolicies = mutableListOf<PolicyState>()

        for (app in scanned) {
            val before = previousByPackage[app.packageName]
            when {
                // 初めて見つけたアプリ
                before == null -> {
                    changedApps += app.toTracked(firstSeenAt = now, addedAt = now, now = now)
                    changes += app.change(ChangeType.ADDED, now)
                }

                // 以前に削除したアプリが再び入った
                !before.isPresent -> {
                    val signatureChanged = signerChanged(before.signerSha256, app.signerSha256)
                    changedApps += app.toTracked(
                        firstSeenAt = before.firstSeenAt,
                        addedAt = now,
                        now = now,
                        reinstalledAt = now,
                        signatureChangedAt = if (signatureChanged) now else before.signatureChangedAt,
                    )
                    changes += app.change(ChangeType.ADDED, now).copy(isReinstall = true)
                    if (signatureChanged) {
                        changes += app.change(ChangeType.SIGNATURE_CHANGED, now)
                        resetStatusForSignatureChange(app, policies, now)?.let { (policy, change) ->
                            changedPolicies += policy
                            changes += change
                        }
                    }
                }

                // 前回も今回も端末にある
                else -> compareInstalled(before, app, policies, now, changedApps, changes, changedPolicies)
            }
        }

        // 端末から消えたアプリ（記録は「端末にない」として残す）
        for (before in previous) {
            if (before.isPresent && before.packageName !in scannedPackages) {
                changedApps += before.copy(isPresent = false, removedAt = now, updatedAt = now)
                changes += AppChange(
                    type = ChangeType.REMOVED,
                    packageName = before.packageName,
                    name = before.name,
                    occurredAt = now,
                    versionName = before.versionName,
                    versionCode = before.versionCode,
                    source = before.source,
                )
            }
        }

        return DiffResult(
            changedApps = changedApps,
            changes = changes.sortedWith(compareBy<AppChange> { it.type.order() }.thenBy { it.packageName }),
            changedPolicies = changedPolicies,
        )
    }

    private fun compareInstalled(
        before: TrackedApp,
        app: InstalledApp,
        policies: Map<String, PolicyState>,
        now: Long,
        changedApps: MutableList<TrackedApp>,
        changes: MutableList<AppChange>,
        changedPolicies: MutableList<PolicyState>,
    ) {
        // チェックとチェックの間に、削除してすぐ入れ直された（インストール日時が新しくなった）
        val quicklyReinstalled = app.firstInstallTime > before.firstInstallTime && before.firstInstallTime > 0
        val signatureChanged = signerChanged(before.signerSha256, app.signerSha256)

        val after = app.toTracked(
            firstSeenAt = before.firstSeenAt,
            addedAt = if (quicklyReinstalled) now else before.addedAt,
            now = now,
            reinstalledAt = if (quicklyReinstalled) now else before.reinstalledAt,
            signatureChangedAt = if (signatureChanged) now else before.signatureChangedAt,
        )

        if (quicklyReinstalled) {
            changes += app.change(ChangeType.ADDED, now).copy(isReinstall = true)
        } else if (app.versionCode != before.versionCode || app.versionName != before.versionName) {
            changes += app.change(ChangeType.UPDATED, now).copy(
                oldVersionName = before.versionName,
                oldVersionCode = before.versionCode,
            )
        }
        if (app.isEnabled != before.isEnabled) {
            changes += app.change(if (app.isEnabled) ChangeType.ENABLED else ChangeType.DISABLED, now)
        }
        if (signatureChanged) {
            changes += app.change(ChangeType.SIGNATURE_CHANGED, now)
            resetStatusForSignatureChange(app, policies, now)?.let { (policy, change) ->
                changedPolicies += policy
                changes += change
            }
        }

        // 名前・入手元などが変わっただけのときは、変化は記録せず最新の状態だけ直す
        if (after.copy(updatedAt = before.updatedAt) != before) changedApps += after
    }

    /** 署名が変わったアプリの「問題なし」を「未確認」に戻す（仕様書 4章）。要注意・未確認はそのまま。 */
    private fun resetStatusForSignatureChange(
        app: InstalledApp,
        policies: Map<String, PolicyState>,
        now: Long,
    ): Pair<PolicyState, AppChange>? {
        val policy = policies[app.packageName] ?: return null
        if (policy.status != ReviewStatus.OK) return null
        val reset = policy.copy(
            status = ReviewStatus.UNCONFIRMED,
            statusReason = StatusChangeReason.SIGNATURE_CHANGED,
            updatedAt = now,
        )
        val change = AppChange(
            type = ChangeType.STATUS_CHANGED,
            packageName = app.packageName,
            name = app.name,
            occurredAt = now,
            oldStatus = ReviewStatus.OK,
            newStatus = ReviewStatus.UNCONFIRMED,
            reason = StatusChangeReason.SIGNATURE_CHANGED,
        )
        return reset to change
    }

    /** 署名が変わったか。どちらかが取れていない（空）ときは、分からないので「変わっていない」とする。 */
    internal fun signerChanged(before: List<String>, after: List<String>): Boolean =
        before.isNotEmpty() && after.isNotEmpty() && before.toSet() != after.toSet()

    private fun InstalledApp.toTracked(
        firstSeenAt: Long,
        addedAt: Long?,
        now: Long,
        reinstalledAt: Long? = null,
        signatureChangedAt: Long? = null,
    ) = TrackedApp(
        packageName = packageName,
        name = name,
        versionName = versionName,
        versionCode = versionCode,
        kind = kind,
        source = source,
        installerPackage = installerPackage,
        initiatingPackage = initiatingPackage,
        isEnabled = isEnabled,
        staticWarnings = warnings,
        signerSha256 = signerSha256,
        firstInstallTime = firstInstallTime,
        lastUpdateTime = lastUpdateTime,
        isPresent = true,
        firstSeenAt = firstSeenAt,
        addedAt = addedAt,
        removedAt = null,
        reinstalledAt = reinstalledAt,
        signatureChangedAt = signatureChangedAt,
        updatedAt = now,
    )

    private fun InstalledApp.change(type: ChangeType, now: Long) = AppChange(
        type = type,
        packageName = packageName,
        name = name,
        occurredAt = now,
        versionName = versionName,
        versionCode = versionCode,
        source = source,
    )

    /** 同じ時刻の変化の並び（履歴で読みやすい順）。 */
    private fun ChangeType.order(): Int = when (this) {
        ChangeType.RESET -> 0
        ChangeType.REMOVED -> 1
        ChangeType.ADDED -> 2
        ChangeType.UPDATED -> 3
        ChangeType.ENABLED, ChangeType.DISABLED -> 4
        ChangeType.SIGNATURE_CHANGED -> 5
        ChangeType.STATUS_CHANGED -> 6
        ChangeType.MEMO_CHANGED -> 7
    }
}
