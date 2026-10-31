package io.github.eightbrows.appwatch.data.apps

import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.InstalledApp
import io.github.eightbrows.appwatch.model.ReviewStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 端末の情報から画面用の AppSummary を作る。
 * 確認状態と管理者メモはまだ保存していないので、すべて「未確認」・メモなし。
 * 「新しく入ったアプリ」も記録がないと決められないので false（次のステップで記録から決める）。
 */
fun InstalledApp.toSummary(zone: ZoneId = ZoneId.systemDefault()): AppSummary = AppSummary(
    packageName = packageName,
    name = name,
    versionName = versionName.orEmpty(),
    kind = kind,
    source = source,
    warnings = warnings,
    reviewStatus = ReviewStatus.UNCONFIRMED,
    adminMemo = null,
    installedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(firstInstallTime), zone),
    isNew = false,
    isSelf = isSelf,
    isEnabled = isEnabled,
)
