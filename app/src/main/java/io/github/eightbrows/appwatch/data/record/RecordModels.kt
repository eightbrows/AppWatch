package io.github.eightbrows.appwatch.data.record

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus

// 記録で使う形。Room や Android の型を含まないので、比較の処理（ChangeDetector）を単体テストできる。
// 時刻はすべてエポックミリ秒。

/**
 * 記録している1件のアプリの状態（app_current の1行に相当）。
 * 端末から消えたアプリも [isPresent] = false で残す（再インストールを見分けるため）。
 */
data class TrackedApp(
    val packageName: String,
    val name: String,
    val versionName: String?,
    val versionCode: Long,
    val kind: AppKind,
    val source: InstallSource,
    val installerPackage: String?,
    val initiatingPackage: String?,
    val isEnabled: Boolean,
    /** 端末の情報だけで決まる警告（Google Play 以外・入手元が分からない・開発用の署名） */
    val staticWarnings: Set<AppWarning>,
    /** 今の署名の証明書（SHA-256）。作り手が変わっていないかを比べる */
    val signerSha256: List<String>,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isPresent: Boolean,
    /** 記録で初めて見つけた時刻（基準なら最初のチェックの時刻） */
    val firstSeenAt: Long,
    /** 最初のチェックより後に追加された時刻（再インストールでは入れ直した時刻）。基準のアプリは null */
    val addedAt: Long?,
    /** 端末から消えたのを見つけた時刻。端末にあれば null */
    val removedAt: Long?,
    /** 以前に削除したアプリが再び入ったのを見つけた時刻 */
    val reinstalledAt: Long?,
    /** 署名が変わったのを見つけた時刻 */
    val signatureChangedAt: Long?,
    /** この行の内容が最後に変わった時刻 */
    val updatedAt: Long,
) {
    /** 画面に出す警告。仕様書 4章のとおり「後から導入」のアプリにだけ付ける。 */
    val warnings: Set<AppWarning>
        get() = if (kind != AppKind.USER_INSTALLED) {
            emptySet()
        } else {
            buildSet {
                addAll(staticWarnings)
                if (reinstalledAt != null) add(AppWarning.REINSTALLED)
                if (signatureChangedAt != null) add(AppWarning.SIGNATURE_CHANGED)
            }
        }

    /** 最初のチェックより後に入ったアプリか（「新しく入ったアプリ」の判定に使う） */
    val isAddedAfterBaseline: Boolean get() = addedAt != null
}

/**
 * 変化の種類（仕様書 5章）。確認状態・管理者メモの変更と事前登録も「元に戻す」のために変化として記録する（仕様書 8章）。
 */
enum class ChangeType {
    ADDED, REMOVED, UPDATED, ENABLED, DISABLED, SIGNATURE_CHANGED, RESET, STATUS_CHANGED, MEMO_CHANGED,

    /** 注意アプリの事前登録（確認状態・管理者メモ・仮のアプリ名をまとめて変える） */
    PRE_REGISTERED,

    /** 事前登録の解除（事前登録の印・確認状態・管理者メモ・仮のアプリ名を外す） */
    UNREGISTERED,
}

/** 確認状態が変わった理由。 */
enum class StatusChangeReason {
    /** 署名が変わったため「問題なし」を「未確認」に戻した */
    SIGNATURE_CHANGED,

    /** 管理者が変更した */
    ADMIN,

    /** 「元に戻す」で取り消した */
    UNDO,
}

/** 変化1件（app_event の1行に相当）。 */
data class AppChange(
    val type: ChangeType,
    val packageName: String,
    val name: String,
    val occurredAt: Long,
    /** 追加・削除ではそのときのバージョン、更新では新しいバージョン */
    val versionName: String? = null,
    val versionCode: Long? = null,
    /** 更新の前のバージョン */
    val oldVersionName: String? = null,
    val oldVersionCode: Long? = null,
    /** そのときの入手元 */
    val source: InstallSource? = null,
    /** 追加のうち、以前に削除したアプリが再び入ったもの */
    val isReinstall: Boolean = false,
    /** 確認状態の変更（STATUS_CHANGED）の前後と理由 */
    val oldStatus: ReviewStatus? = null,
    val newStatus: ReviewStatus? = null,
    val reason: StatusChangeReason? = null,
    /** 管理者メモの変更（MEMO_CHANGED）の前後 */
    val oldAdminMemo: String? = null,
    val newAdminMemo: String? = null,
    /**
     * 「問題なし」にしたときに消した「以前に削除したアプリ」「署名が変わった」の警告の時刻（STATUS_CHANGED）。
     * 「元に戻す」で警告を戻すのに使う。消していなければ null
     */
    val oldReinstalledAt: Long? = null,
    val oldSignatureChangedAt: Long? = null,
    /** 事前登録・解除（PRE_REGISTERED・UNREGISTERED）の前の事前登録の印と、前後の仮のアプリ名 */
    val oldPreRegistered: Boolean? = null,
    val oldProvisionalName: String? = null,
    val newProvisionalName: String? = null,
    /** まとめて行った操作の番号（「元に戻す」で一括操作を1回として扱うため）。なければ null */
    val operationId: String? = null,
)

/** 管理者の設定（app_policy の1行に相当）。アンインストール後も残す。 */
data class PolicyState(
    val packageName: String,
    val status: ReviewStatus,
    val adminMemo: String?,
    val isPreRegistered: Boolean,
    /** 事前登録のときの仮のアプリ名（実際に入ったら端末上の名前を使う） */
    val provisionalName: String?,
    /** 確認状態が最後に変わった理由（署名が変わって「未確認」に戻した、など） */
    val statusReason: StatusChangeReason?,
    val updatedAt: Long,
)

/** 前回の記録と今回の取得を比べた結果。 */
data class DiffResult(
    /** 内容が変わった行だけ（追加・削除を含む） */
    val changedApps: List<TrackedApp>,
    val changes: List<AppChange>,
    /** 内容が変わった管理者の設定だけ */
    val changedPolicies: List<PolicyState>,
)
