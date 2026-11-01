package io.github.eightbrows.appwatch.data.db

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
