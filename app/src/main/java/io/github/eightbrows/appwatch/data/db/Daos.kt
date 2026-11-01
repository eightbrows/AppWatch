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

    @Query("SELECT * FROM app_event WHERE dataset_id = :datasetId ORDER BY occurred_at, id")
    suspend fun getEvents(datasetId: String): List<AppEventEntity>
}

@Dao
interface PolicyDao {
    @Query("SELECT * FROM app_policy WHERE dataset_id = :datasetId")
    suspend fun getAll(datasetId: String): List<AppPolicyEntity>

    @Query("SELECT * FROM app_policy WHERE dataset_id = :datasetId")
    fun observeAll(datasetId: String): Flow<List<AppPolicyEntity>>

    @Query("SELECT * FROM app_policy WHERE dataset_id = :datasetId AND package_name = :packageName")
    suspend fun get(datasetId: String, packageName: String): AppPolicyEntity?

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
}
