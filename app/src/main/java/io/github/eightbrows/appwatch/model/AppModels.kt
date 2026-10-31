package io.github.eightbrows.appwatch.model

import java.time.LocalDateTime

/** 種類（仕様書 4章）。 */
enum class AppKind { USER_INSTALLED, PREINSTALLED, SYSTEM }

/** 入手元（仕様書 4章）。 */
enum class InstallSource { GOOGLE_PLAY, OTHER, UNKNOWN }

/** 警告（仕様書 4章）。自動で判定する。 */
enum class AppWarning { NOT_FROM_PLAY, UNKNOWN_SOURCE, DEBUG_SIGNATURE, SIGNATURE_CHANGED, REINSTALLED }

/** 確認状態（仕様書 4章）。管理者が設定する。 */
enum class ReviewStatus { UNCONFIRMED, OK, CAUTION }

/** 使い方（仕様書 3章）。 */
enum class UsageMode { MONITOR, VIEW_ONLY }

/** 権限・設定の状態。設定メニューの「権限」に表示する。 */
enum class PermissionState { GRANTED, CONFIGURED, NOT_CONFIGURED }

/**
 * 画面に出す1件のアプリ。
 * 今はダミーデータから作る。後のステップで、端末から取得した情報とデータベースから作る。
 */
data class AppSummary(
    val packageName: String,
    val name: String,
    val versionName: String,
    val kind: AppKind,
    val source: InstallSource,
    val warnings: Set<AppWarning>,
    val reviewStatus: ReviewStatus,
    val adminMemo: String?,
    val installedAt: LocalDateTime,
    /** 新しく入ったアプリ（トップでカード表示する）かどうか。判定の決まりは後のステップで決める。 */
    val isNew: Boolean,
    /** 本アプリ自身かどうか。名前の横に「本アプリ」を付ける。 */
    val isSelf: Boolean = false,
)
