package io.github.eightbrows.appwatch.data.dummy

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.PermissionState
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.model.UsageMode
import java.time.LocalDateTime

/**
 * 画面確認用のダミーデータ。ダミーはすべてここにまとめる。
 * 本物のデータ（アプリ一覧・データベース・設定値）ができたら、このファイルを参照している箇所を差し替えて削除する。
 */
object DummyData {

    val lastCheckAt: LocalDateTime = LocalDateTime.of(2026, 10, 11, 0, 0)

    val apps: List<AppSummary> = listOf(
        // 新しく入ったアプリ：要注意（再インストール）
        AppSummary(
            packageName = "com.example.qrreader",
            name = "QRリーダー",
            versionName = "3.1",
            kind = AppKind.USER_INSTALLED,
            source = InstallSource.GOOGLE_PLAY,
            warnings = setOf(AppWarning.REINSTALLED),
            reviewStatus = ReviewStatus.CAUTION,
            adminMemo = "広告が多く、勝手に課金画面に飛ぶアプリ。削除が必要。",
            installedAt = LocalDateTime.of(2026, 10, 6, 9, 12),
            isNew = true,
        ),
        // 新しく入ったアプリ：警告あり
        AppSummary(
            packageName = "com.example.cleanerpro",
            name = "クリーナー Pro",
            versionName = "1.0.3",
            kind = AppKind.USER_INSTALLED,
            source = InstallSource.OTHER,
            warnings = setOf(AppWarning.NOT_FROM_PLAY, AppWarning.DEBUG_SIGNATURE),
            reviewStatus = ReviewStatus.UNCONFIRMED,
            adminMemo = null,
            installedAt = LocalDateTime.of(2026, 10, 10, 21, 14),
            isNew = true,
        ),
        // 新しく入ったアプリ：普通
        AppSummary(
            packageName = "jp.example.tenkinavi",
            name = "天気ナビ",
            versionName = "4.2.0",
            kind = AppKind.USER_INSTALLED,
            source = InstallSource.GOOGLE_PLAY,
            warnings = emptySet(),
            reviewStatus = ReviewStatus.UNCONFIRMED,
            adminMemo = null,
            installedAt = LocalDateTime.of(2026, 10, 9, 8, 40),
            isNew = true,
        ),
        // まだ確認していないアプリ
        AppSummary(
            packageName = "com.example.recipebook",
            name = "かんたんレシピ",
            versionName = "2.8.1",
            kind = AppKind.USER_INSTALLED,
            source = InstallSource.GOOGLE_PLAY,
            warnings = emptySet(),
            reviewStatus = ReviewStatus.UNCONFIRMED,
            adminMemo = null,
            installedAt = LocalDateTime.of(2026, 9, 28, 14, 5),
            isNew = false,
        ),
        AppSummary(
            packageName = "com.example.walkcounter",
            name = "歩数計",
            versionName = "5.0",
            kind = AppKind.PREINSTALLED,
            source = InstallSource.UNKNOWN,
            warnings = setOf(AppWarning.UNKNOWN_SOURCE),
            reviewStatus = ReviewStatus.UNCONFIRMED,
            adminMemo = null,
            installedAt = LocalDateTime.of(2026, 4, 1, 10, 0),
            isNew = false,
        ),
        // 問題なし
        AppSummary(
            packageName = "jp.example.bankapp",
            name = "みなと銀行",
            versionName = "7.3.2",
            kind = AppKind.USER_INSTALLED,
            source = InstallSource.GOOGLE_PLAY,
            warnings = emptySet(),
            reviewStatus = ReviewStatus.OK,
            adminMemo = "年金の確認に使う。",
            installedAt = LocalDateTime.of(2026, 5, 20, 16, 30),
            isNew = false,
        ),
        AppSummary(
            packageName = "com.example.camera",
            name = "カメラ",
            versionName = "12.0",
            kind = AppKind.PREINSTALLED,
            source = InstallSource.UNKNOWN,
            warnings = emptySet(),
            reviewStatus = ReviewStatus.OK,
            adminMemo = null,
            installedAt = LocalDateTime.of(2026, 4, 1, 10, 0),
            isNew = false,
        ),
        AppSummary(
            packageName = "io.github.eightbrows.appwatch",
            name = "アプリ見守当番",
            versionName = "20261031-D01",
            kind = AppKind.USER_INSTALLED,
            source = InstallSource.OTHER,
            warnings = setOf(AppWarning.NOT_FROM_PLAY),
            reviewStatus = ReviewStatus.OK,
            adminMemo = null,
            installedAt = LocalDateTime.of(2026, 10, 1, 12, 0),
            isNew = false,
            isSelf = true,
        ),
        AppSummary(
            packageName = "com.android.systemui",
            name = "システム UI",
            versionName = "16",
            kind = AppKind.SYSTEM,
            source = InstallSource.UNKNOWN,
            warnings = emptySet(),
            reviewStatus = ReviewStatus.OK,
            adminMemo = null,
            installedAt = LocalDateTime.of(2026, 4, 1, 10, 0),
            isNew = false,
        ),
    )

    // ---- 設定メニュー ----

    val importedDataNames: List<String> = listOf("端末A", "端末B")
    val usageMode: UsageMode = UsageMode.MONITOR
    val checkIntervalMinutes: Int = 30
    val healthCheckAllOk: Boolean = true

    val permissionQueryAllPackages: PermissionState = PermissionState.GRANTED
    val permissionDeletePackages: PermissionState = PermissionState.GRANTED
    val batteryOptimizationExcluded: PermissionState = PermissionState.CONFIGURED
    val unusedAppRestrictionsExcluded: PermissionState = PermissionState.NOT_CONFIGURED
}
