package io.github.eightbrows.appwatch.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DatasetDao {
    @Query("SELECT * FROM dataset WHERE kind = 'LOCAL' LIMIT 1")
    suspend fun getLocal(): DatasetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(dataset: DatasetEntity)

    @Query("UPDATE dataset SET app_version = :appVersion, os_version = :osVersion, sdk_int = :sdkInt WHERE id = :id")
    suspend fun updateEnvironment(id: String, appVersion: String, osVersion: String, sdkInt: Int)
}

@Dao
interface AppRecordDao {
    @Query("SELECT COUNT(*) FROM baseline_app WHERE dataset_id = :datasetId")
    suspend fun baselineCount(datasetId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(apps: List<BaselineAppEntity>)

    @Query("SELECT * FROM baseline_app WHERE dataset_id = :datasetId")
    suspend fun getBaseline(datasetId: String): List<BaselineAppEntity>

    @Query("SELECT * FROM app_current WHERE dataset_id = :datasetId")
    suspend fun getCurrent(datasetId: String): List<AppCurrentEntity>

    @Query("SELECT * FROM app_current WHERE dataset_id = :datasetId AND is_present = 1")
    fun observePresent(datasetId: String): Flow<List<AppCurrentEntity>>

    @Upsert
    suspend fun upsertCurrent(apps: List<AppCurrentEntity>)

    @Insert
    suspend fun insertEvents(events: List<AppEventEntity>)

    /** 「以前に削除したアプリ」「署名が変わった」の警告を消す（「問題なし」にしたとき）。変化の記録は残す。 */
    @Query(
        "UPDATE app_current SET reinstalled_at = NULL, signature_changed_at = NULL, updated_at = :now " +
            "WHERE dataset_id = :datasetId AND package_name IN (:packageNames)",
    )
    suspend fun clearRecordedWarnings(datasetId: String, packageNames: List<String>, now: Long)

    /** 「問題なし」を取り消したときに、消した警告を戻す。 */
    @Query(
        "UPDATE app_current SET reinstalled_at = :reinstalledAt, signature_changed_at = :signatureChangedAt, " +
            "updated_at = :now WHERE dataset_id = :datasetId AND package_name = :packageName",
    )
    suspend fun restoreRecordedWarnings(
        datasetId: String,
        packageName: String,
        reinstalledAt: Long?,
        signatureChangedAt: Long?,
        now: Long,
    )

    /** 端末にないアプリも含めた全行（アプリの確認状態の画面）。 */
    @Query("SELECT * FROM app_current WHERE dataset_id = :datasetId")
    fun observeCurrent(datasetId: String): Flow<List<AppCurrentEntity>>

    @Query("SELECT * FROM app_current WHERE dataset_id = :datasetId AND package_name = :packageName")
    fun observeCurrentOne(datasetId: String, packageName: String): Flow<AppCurrentEntity?>

    @Query("SELECT * FROM app_event WHERE dataset_id = :datasetId ORDER BY occurred_at, id")
    suspend fun getEvents(datasetId: String): List<AppEventEntity>

    /** 1つのアプリの変化（古い順）。アプリの詳細の「これまでの経緯」。 */
    @Query("SELECT * FROM app_event WHERE dataset_id = :datasetId AND package_name = :packageName ORDER BY occurred_at, id")
    fun observeEventsFor(datasetId: String, packageName: String): Flow<List<AppEventEntity>>

    /**
     * 端末の変化（履歴と比較）。確認状態・管理者メモ・事前登録の記録は読まない。
     * 種類の一覧は HistoryRules.deviceChangeTypes と同じ（画面に出す前に、そちらでも絞り込む）。
     */
    @Query(
        "SELECT * FROM app_event WHERE dataset_id = :datasetId AND type IN " +
            "('ADDED', 'REMOVED', 'UPDATED', 'ENABLED', 'DISABLED', 'SIGNATURE_CHANGED', 'RESET') ORDER BY occurred_at, id",
    )
    fun observeDeviceEvents(datasetId: String): Flow<List<AppEventEntity>>

    /** 事前登録・解除の記録（事前登録の一覧に登録した日を出す）。 */
    @Query("SELECT * FROM app_event WHERE dataset_id = :datasetId AND type IN ('PRE_REGISTERED', 'UNREGISTERED') ORDER BY occurred_at, id")
    fun observeRegistrationEvents(datasetId: String): Flow<List<AppEventEntity>>

    /** 1回の操作で記録した変化（「元に戻す」）。 */
    @Query("SELECT * FROM app_event WHERE dataset_id = :datasetId AND operation_id = :operationId ORDER BY id")
    suspend fun getEventsByOperation(datasetId: String, operationId: String): List<AppEventEntity>
}

@Dao
interface PolicyDao {
    @Query("SELECT * FROM app_policy WHERE dataset_id = :datasetId")
    suspend fun getAll(datasetId: String): List<AppPolicyEntity>

    @Query("SELECT * FROM app_policy WHERE dataset_id = :datasetId")
    fun observeAll(datasetId: String): Flow<List<AppPolicyEntity>>

    @Query("SELECT * FROM app_policy WHERE dataset_id = :datasetId AND package_name = :packageName")
    suspend fun get(datasetId: String, packageName: String): AppPolicyEntity?

    @Query("SELECT * FROM app_policy WHERE dataset_id = :datasetId AND package_name = :packageName")
    fun observe(datasetId: String, packageName: String): Flow<AppPolicyEntity?>

    @Upsert
    suspend fun upsert(policies: List<AppPolicyEntity>)
}

@Dao
interface ScanDailyDao {
    @Query("SELECT * FROM scan_daily WHERE dataset_id = :datasetId AND date = :date")
    suspend fun get(datasetId: String, date: String): ScanDailyEntity?

    @Upsert
    suspend fun upsert(day: ScanDailyEntity)

    @Query("SELECT MAX(last_at) FROM scan_daily WHERE dataset_id = :datasetId")
    suspend fun lastCheckAt(datasetId: String): Long?

    @Query("SELECT MAX(last_at) FROM scan_daily WHERE dataset_id = :datasetId")
    fun observeLastCheckAt(datasetId: String): Flow<Long?>

    @Query("SELECT * FROM scan_daily WHERE dataset_id = :datasetId ORDER BY date")
    suspend fun getAll(datasetId: String): List<ScanDailyEntity>

    @Query("SELECT * FROM scan_daily WHERE dataset_id = :datasetId ORDER BY date")
    fun observeAll(datasetId: String): Flow<List<ScanDailyEntity>>
}

/** データ1件ごとの数（取り込んだデータの「要注意 n件」）。 */
data class DatasetCount(
    @ColumnInfo(name = "dataset_id") val datasetId: String,
    @ColumnInfo(name = "count") val count: Int,
)

/** 書き出し・取り込み・復元（仕様書 7章）で、1つのデータの中身をまとめて読み書きする。 */
@Dao
interface TransferDao {
    @Query("SELECT * FROM dataset WHERE id = :id")
    suspend fun getDataset(id: String): DatasetEntity?

    @Query("SELECT * FROM dataset WHERE kind = 'IMPORTED' ORDER BY imported_at DESC")
    fun observeImported(): Flow<List<DatasetEntity>>

    @Query("SELECT * FROM dataset WHERE kind = 'IMPORTED' ORDER BY imported_at DESC")
    suspend fun getImported(): List<DatasetEntity>

    /** データごとの、端末にあるアプリのうち「要注意」の数。 */
    @Query(
        "SELECT p.dataset_id AS dataset_id, COUNT(*) AS count FROM app_policy p " +
            "JOIN app_current c ON c.dataset_id = p.dataset_id AND c.package_name = p.package_name " +
            "WHERE p.review_status = 'CAUTION' AND c.is_present = 1 GROUP BY p.dataset_id",
    )
    fun observeCautionCounts(): Flow<List<DatasetCount>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDataset(dataset: DatasetEntity)

    @Upsert
    suspend fun upsertDataset(dataset: DatasetEntity)

    @Query("UPDATE dataset SET name = :name WHERE id = :id")
    suspend fun renameDataset(id: String, name: String)

    @Query("DELETE FROM dataset WHERE id = :id")
    suspend fun deleteDatasetRow(id: String)

    @Query("DELETE FROM baseline_app WHERE dataset_id = :id")
    suspend fun deleteBaseline(id: String)

    @Query("DELETE FROM app_current WHERE dataset_id = :id")
    suspend fun deleteCurrent(id: String)

    @Query("DELETE FROM app_event WHERE dataset_id = :id")
    suspend fun deleteEvents(id: String)

    @Query("DELETE FROM app_policy WHERE dataset_id = :id")
    suspend fun deletePolicies(id: String)

    @Query("DELETE FROM scan_daily WHERE dataset_id = :id")
    suspend fun deleteScanDays(id: String)

    // 同じ行が2つあれば失敗させる（取り込みの途中で失敗したら、まとめて元に戻す）
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBaseline(rows: List<BaselineAppEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCurrent(rows: List<AppCurrentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvents(rows: List<AppEventEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPolicies(rows: List<AppPolicyEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScanDays(rows: List<ScanDailyEntity>)

    @Query("SELECT * FROM baseline_app WHERE dataset_id = :id ORDER BY package_name")
    suspend fun getBaseline(id: String): List<BaselineAppEntity>

    @Query("SELECT * FROM app_current WHERE dataset_id = :id ORDER BY package_name")
    suspend fun getCurrent(id: String): List<AppCurrentEntity>

    @Query("SELECT * FROM app_event WHERE dataset_id = :id ORDER BY occurred_at, id")
    suspend fun getEvents(id: String): List<AppEventEntity>

    @Query("SELECT * FROM app_policy WHERE dataset_id = :id ORDER BY package_name")
    suspend fun getPolicies(id: String): List<AppPolicyEntity>

    @Query("SELECT * FROM scan_daily WHERE dataset_id = :id ORDER BY date")
    suspend fun getScanDays(id: String): List<ScanDailyEntity>
}
