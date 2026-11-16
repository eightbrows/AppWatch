package io.github.eightbrows.appwatch.model

import java.time.LocalDateTime

/** 種類（仕様書 4章）。 */
enum class AppKind { USER_INSTALLED, PREINSTALLED, SYSTEM }

/** 入手元（仕様書 4章）。 */
enum class InstallSource { GOOGLE_PLAY, OTHER, UNKNOWN }

/** 警告（仕様書 4章）。自動で判定する。 */
enum class AppWarning {
    NOT_FROM_PLAY, UNKNOWN_SOURCE, DEBUG_SIGNATURE, SIGNATURE_CHANGED, REINSTALLED;

    /** 入手元にかかわる警告か（「後から導入」のアプリにだけ付ける）。 */
    val isSource: Boolean get() = this == NOT_FROM_PLAY || this == UNKNOWN_SOURCE
}

/** 確認状態（仕様書 4章）。管理者が設定する。 */
enum class ReviewStatus { UNCONFIRMED, OK, CAUTION }

/** 権限・設定の状態。設定メニューの「権限」に表示する。 */
enum class PermissionState { GRANTED, CONFIGURED, NOT_CONFIGURED }

/**
 * 端末から取得し、種類・入手元・警告を判定したアプリ1件。
 * 時刻はエポックミリ秒（Android の PackageInfo と同じ）。
 */
data class InstalledApp(
    val packageName: String,
    val name: String,
    val versionName: String?,
    val versionCode: Long,
    val kind: AppKind,
    val source: InstallSource,
    /** インストールしたアプリ（Google Play ストアなど）。分からなければ null。 */
    val installerPackage: String?,
    /** 入手のきっかけになったアプリ（Android 11 以上のみ。例：ブラウザ）。分からなければ null。 */
    val initiatingPackage: String?,
    val warnings: Set<AppWarning>,
    /** 今の署名の証明書（SHA-256）。「署名が変わった」の判定に使う予定。 */
    val signerSha256: List<String>,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isEnabled: Boolean,
    val isSelf: Boolean,
)

/**
 * 画面に出す1件のアプリ。
 * 端末の情報（InstalledApp）と、管理者の設定（確認状態・管理者メモ。今はまだない）から作る。
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
    /** 新しく入ったアプリ（最初のチェックより後に追加され、問題なしになっていない）。記録ができるまでは常に false。 */
    val isNew: Boolean,
    /** 本アプリ自身かどうか。名前の横に「本アプリ」を付ける。 */
    val isSelf: Boolean = false,
    /** 有効かどうか。無効なら「無効」の札を付け、カード・行を薄くする（トップ）。 */
    val isEnabled: Boolean = true,
)

/**
 * 「要注意」の件数の決まり（仕様書 6章）。トップの「要注意」・ウィジェットの赤・設定メニューの件数などで数える。
 * 要注意でも、今無効になっているアプリは対応が済んだものとして数えない（確認状態は「要注意」のまま）。
 * 種類（後から導入・プリインストール・システム）では区別しない。
 */
object CautionRule {
    fun counts(status: ReviewStatus, isEnabled: Boolean): Boolean = status == ReviewStatus.CAUTION && isEnabled

    /** 要注意で無効のアプリ（対応が済んだもの）。 */
    fun isHandled(status: ReviewStatus, isEnabled: Boolean): Boolean = status == ReviewStatus.CAUTION && !isEnabled
}

/** アプリを消すときに開く画面（仕様書 6章）。 */
enum class RemoveAction {
    /** Android のアンインストール画面 */
    UNINSTALL,

    /** Android のアプリ情報（削除できないプリインストール・システムのアプリ。無効化はそこから行う） */
    APP_INFO,
}

/**
 * システムの印があるアプリは、利用者が削除できない（更新の取り消しだけになる）。
 * そのためプリインストールとシステムは、どちらもアプリ情報へ案内する（種類で区別しない。ステップ32まではシステムはボタンなしだった）。
 */
val AppSummary.removeAction: RemoveAction
    get() = when (kind) {
        AppKind.USER_INSTALLED -> RemoveAction.UNINSTALL
        AppKind.PREINSTALLED, AppKind.SYSTEM -> RemoveAction.APP_INFO
    }

/**
 * トップのカード・一覧の行のボタン。無効のアプリは削除ではなく Android のアプリ情報を開く（そこから有効に戻せる。種類で区別しない）。
 * 無効でなければ [removeAction] と同じ。
 */
val AppSummary.topAction: RemoveAction
    get() = if (!isEnabled) RemoveAction.APP_INFO else removeAction
