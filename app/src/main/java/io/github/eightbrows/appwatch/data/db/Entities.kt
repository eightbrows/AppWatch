package io.github.eightbrows.appwatch.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.data.record.StatusChangeReason
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus

// データベースの表（仕様書 8章）。すべての表に「データの番号」（dataset_id）を持たせ、
// この端末のデータと取り込んだデータを1つのデータベースに並べて持つ。
// 時刻はエポックミリ秒。列挙型は名前の文字列で保存する。

/** データのまとまりの種類。 */
enum class DatasetKind { LOCAL, IMPORTED }

/** dataset：データのまとまり。この端末の番号（UUID）は初回起動時に作り、端末の識別番号を兼ねる。 */
@Entity(tableName = "dataset")
data class DatasetEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: DatasetKind,
    @ColumnInfo(name = "device_model") val deviceModel: String,
    @ColumnInfo(name = "os_version") val osVersion: String,
    @ColumnInfo(name = "sdk_int") val sdkInt: Int,
    @ColumnInfo(name = "app_version") val appVersion: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "exported_at") val exportedAt: Long? = null,
    @ColumnInfo(name = "imported_at") val importedAt: Long? = null,
)

private const val DATASET_FK = "dataset_id"

/** baseline_app：基準（最初のチェックの全件。初期化時に取り直す）。 */
@Entity(
    tableName = "baseline_app",
    primaryKeys = ["dataset_id", "package_name"],
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = [DATASET_FK],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BaselineAppEntity(
    @ColumnInfo(name = "dataset_id") val datasetId: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "version_name") val versionName: String?,
    @ColumnInfo(name = "version_code") val versionCode: Long,
    @ColumnInfo(name = "kind") val kind: AppKind,
    @ColumnInfo(name = "source") val source: InstallSource,
    @ColumnInfo(name = "installer_package") val installerPackage: String?,
    @ColumnInfo(name = "initiating_package") val initiatingPackage: String?,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "warnings") val warnings: Set<AppWarning>,
    /** アプリの同一性（作り手）を比べるための署名の証明書（SHA-256） */
    @ColumnInfo(name = "signer_sha256") val signerSha256: List<String>,
    @ColumnInfo(name = "first_install_time") val firstInstallTime: Long,
    @ColumnInfo(name = "last_update_time") val lastUpdateTime: Long,
)

/** app_current：最新の状態。端末から消えたアプリも is_present = 0 で残す。 */
@Entity(
    tableName = "app_current",
    primaryKeys = ["dataset_id", "package_name"],
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = [DATASET_FK],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AppCurrentEntity(
    @ColumnInfo(name = "dataset_id") val datasetId: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "version_name") val versionName: String?,
    @ColumnInfo(name = "version_code") val versionCode: Long,
    @ColumnInfo(name = "kind") val kind: AppKind,
    @ColumnInfo(name = "source") val source: InstallSource,
    @ColumnInfo(name = "installer_package") val installerPackage: String?,
    @ColumnInfo(name = "initiating_package") val initiatingPackage: String?,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    /** 端末の情報だけで決まる警告。署名が変わった・再インストールは下の日時の列で表す */
    @ColumnInfo(name = "warnings") val warnings: Set<AppWarning>,
    /** アプリの同一性（作り手）を比べるための署名の証明書（SHA-256） */
    @ColumnInfo(name = "signer_sha256") val signerSha256: List<String>,
    @ColumnInfo(name = "first_install_time") val firstInstallTime: Long,
    @ColumnInfo(name = "last_update_time") val lastUpdateTime: Long,
    @ColumnInfo(name = "is_present") val isPresent: Boolean,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    /** 最初のチェックより後に追加された時刻。基準のアプリは null */
    @ColumnInfo(name = "added_at") val addedAt: Long?,
    @ColumnInfo(name = "removed_at") val removedAt: Long?,
    @ColumnInfo(name = "reinstalled_at") val reinstalledAt: Long?,
    @ColumnInfo(name = "signature_changed_at") val signatureChangedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** app_event：変化の記録（追記のみ）。 */
@Entity(
    tableName = "app_event",
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = [DATASET_FK],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["dataset_id", "occurred_at"]),
        Index(value = ["dataset_id", "package_name"]),
    ],
)
data class AppEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "dataset_id") val datasetId: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "type") val type: ChangeType,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "name") val name: String,
    // ここから下が「詳細」。種類によって使う列が違う
    @ColumnInfo(name = "version_name") val versionName: String?,
    @ColumnInfo(name = "version_code") val versionCode: Long?,
    @ColumnInfo(name = "old_version_name") val oldVersionName: String?,
    @ColumnInfo(name = "old_version_code") val oldVersionCode: Long?,
    @ColumnInfo(name = "source") val source: InstallSource?,
    @ColumnInfo(name = "is_reinstall") val isReinstall: Boolean,
    @ColumnInfo(name = "old_status") val oldStatus: ReviewStatus?,
    @ColumnInfo(name = "new_status") val newStatus: ReviewStatus?,
    @ColumnInfo(name = "reason") val reason: StatusChangeReason?,
    @ColumnInfo(name = "old_admin_memo") val oldAdminMemo: String?,
    @ColumnInfo(name = "new_admin_memo") val newAdminMemo: String?,
    /** 「問題なし」にしたときに消した警告の時刻（元に戻すときに戻す） */
    @ColumnInfo(name = "old_reinstalled_at") val oldReinstalledAt: Long?,
    @ColumnInfo(name = "old_signature_changed_at") val oldSignatureChangedAt: Long?,
    @ColumnInfo(name = "operation_id") val operationId: String?,
)

/** app_policy：管理者の設定。アンインストール後も残す。 */
@Entity(
    tableName = "app_policy",
    primaryKeys = ["dataset_id", "package_name"],
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = [DATASET_FK],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AppPolicyEntity(
    @ColumnInfo(name = "dataset_id") val datasetId: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "review_status") val reviewStatus: ReviewStatus,
    @ColumnInfo(name = "admin_memo") val adminMemo: String?,
    @ColumnInfo(name = "is_pre_registered") val isPreRegistered: Boolean,
    @ColumnInfo(name = "provisional_name") val provisionalName: String?,
    @ColumnInfo(name = "status_reason") val statusReason: StatusChangeReason?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** scan_daily：チェックの実行記録（日ごと）。日付は端末の時間帯での「YYYY-MM-DD」。 */
@Entity(
    tableName = "scan_daily",
    primaryKeys = ["dataset_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = [DATASET_FK],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ScanDailyEntity(
    @ColumnInfo(name = "dataset_id") val datasetId: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "count") val count: Int,
    @ColumnInfo(name = "last_at") val lastAt: Long,
)
