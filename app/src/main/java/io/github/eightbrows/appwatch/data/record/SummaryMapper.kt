package io.github.eightbrows.appwatch.data.record

import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.ReviewStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 記録（最新の状態）と管理者の設定から、画面用の AppSummary を作る。
 * - 確認状態はパッケージ名で保存しているので、再インストールされても以前の確認状態で出る
 * - 新しく入ったアプリ：最初のチェックより後に追加され、確認状態が「問題なし」でないアプリ（仕様書 6章）
 */
fun TrackedApp.toSummary(
    policy: PolicyState?,
    selfPackageName: String,
    zone: ZoneId = ZoneId.systemDefault(),
): AppSummary {
    val status = policy?.status ?: ReviewStatus.UNCONFIRMED
    return AppSummary(
        packageName = packageName,
        name = name,
        versionName = versionName.orEmpty(),
        kind = kind,
        source = source,
        warnings = warnings,
        reviewStatus = status,
        adminMemo = policy?.adminMemo,
        installedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(firstInstallTime), zone),
        isNew = isAddedAfterBaseline && status != ReviewStatus.OK,
        isSelf = packageName == selfPackageName,
        isEnabled = isEnabled,
    )
}
