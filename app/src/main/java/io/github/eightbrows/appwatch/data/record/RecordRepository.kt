package io.github.eightbrows.appwatch.data.record

import androidx.room.withTransaction
import io.github.eightbrows.appwatch.data.db.AppWatchDatabase
import io.github.eightbrows.appwatch.data.db.DatasetEntity
import io.github.eightbrows.appwatch.data.db.DatasetKind
import io.github.eightbrows.appwatch.data.db.ScanDailyEntity
import io.github.eightbrows.appwatch.data.db.toBaselineEntity
import io.github.eightbrows.appwatch.data.db.toCurrentEntity
import io.github.eightbrows.appwatch.data.db.toEntity
import io.github.eightbrows.appwatch.data.db.toPolicy
import io.github.eightbrows.appwatch.data.db.toTracked
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.InstalledApp
import io.github.eightbrows.appwatch.model.ReviewStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import io.github.eightbrows.appwatch.data.system.ScanDay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** この端末の情報（dataset に書く）。 */
data class DeviceEnvironment(
    val deviceModel: String,
    val osVersion: String,
    val sdkInt: Int,
    val appVersion: String,
)

/**
 * 記録の読み書き（Room）。チェックの保存（[CheckRecorder]）と、管理者の設定（確認状態など）の保存を受け持つ。
 * 書き込むのはこの端末のデータ（dataset の LOCAL）だけ。
 */
class RecordRepository(
    private val db: AppWatchDatabase,
    private val environment: DeviceEnvironment,
    private val selfPackageName: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : CheckRecorder {

    private val datasetDao = db.datasetDao()
    private val recordDao = db.appRecordDao()
    private val policyDao = db.policyDao()
    private val scanDailyDao = db.scanDailyDao()

    private val datasetMutex = Mutex()

    @Volatile
    private var localId: String? = null

    /**
     * この端末のデータの番号。初回起動時に UUID で作り、端末の識別番号を兼ねる。
     * 起動ごとに1回、アプリのバージョンと OS の情報を最新にする。
     */
    suspend fun localDatasetId(): String = localId ?: datasetMutex.withLock {
        localId ?: run {
            val existing = datasetDao.getLocal()
            val id = if (existing != null) {
                datasetDao.updateEnvironment(existing.id, environment.appVersion, environment.osVersion, environment.sdkInt)
                existing.id
            } else {
                newId().also { id ->
                    datasetDao.insert(
                        DatasetEntity(
                            id = id,
                            name = environment.deviceModel,
                            kind = DatasetKind.LOCAL,
                            deviceModel = environment.deviceModel,
                            osVersion = environment.osVersion,
                            sdkInt = environment.sdkInt,
                            appVersion = environment.appVersion,
                            createdAt = clock(),
                        ),
                    )
                }
            }
            id.also { localId = it }
        }
    }

    // ---- チェックの保存 ----

    override suspend fun lastCheckAt(): Long? = scanDailyDao.lastCheckAt(localDatasetId())

    override suspend fun record(scanned: List<InstalledApp>, now: Long): RecordOutcome {
        val datasetId = localDatasetId()
        return db.withTransaction {
            val outcome = if (recordDao.baselineCount(datasetId) == 0) {
                // 最初のチェック：全件を基準と最新の状態に保存する。変化は記録しない
                val apps = ChangeDetector.baseline(scanned, now)
                recordDao.insertBaseline(apps.map { it.toBaselineEntity(datasetId, capturedAt = now) })
                recordDao.upsertCurrent(apps.map { it.toCurrentEntity(datasetId) })
                RecordOutcome(isBaseline = true, appCount = apps.size, changes = emptyList())
            } else {
                val previous = recordDao.getCurrent(datasetId).map { it.toTracked() }
                val policies = policyDao.getAll(datasetId).associate { it.packageName to it.toPolicy() }
                val diff = ChangeDetector.diff(previous, scanned, policies, now)
                if (diff.changedApps.isNotEmpty()) recordDao.upsertCurrent(diff.changedApps.map { it.toCurrentEntity(datasetId) })
                if (diff.changes.isNotEmpty()) recordDao.insertEvents(diff.changes.map { it.toEntity(datasetId) })
                if (diff.changedPolicies.isNotEmpty()) policyDao.upsert(diff.changedPolicies.map { it.toEntity(datasetId) })
                RecordOutcome(isBaseline = false, appCount = scanned.size, changes = diff.changes)
            }
            countCheck(datasetId, now)
            outcome
        }
    }

    /** チェックの実行記録を日ごとにまとめる（日付、回数、最後の時刻）。 */
    private suspend fun countCheck(datasetId: String, now: Long) {
        val date = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate().toString()
        val day = scanDailyDao.get(datasetId, date)
        scanDailyDao.upsert(
            ScanDailyEntity(datasetId = datasetId, date = date, count = (day?.count ?: 0) + 1, lastAt = now),
        )
    }

    // ---- 画面用 ----

    /** トップに出す、端末にあるアプリ。データベースが変わるたびに流れる。 */
    fun observePresentApps(): Flow<List<AppSummary>> = flow {
        val datasetId = localDatasetId()
        emitAll(
            combine(recordDao.observePresent(datasetId), policyDao.observeAll(datasetId)) { current, policies ->
                val policyByPackage = policies.associate { it.packageName to it.toPolicy() }
                current.map { it.toTracked().toSummary(policyByPackage[it.packageName], selfPackageName) }
            },
        )
    }

    /** チェックの実行記録（日ごと）。動作チェックの「直近7日のチェック回数」に使う。 */
    fun observeScanDays(): Flow<List<ScanDay>> = flow {
        emitAll(
            scanDailyDao.observeAll(localDatasetId()).map { rows ->
                rows.map { ScanDay(date = it.date, count = it.count, lastAt = it.lastAt) }
            },
        )
    }

    /** 最終チェックの時刻（scan_daily の最後の時刻）。 */
    fun observeLastCheckAt(): Flow<Long?> = flow {
        emitAll(scanDailyDao.observeLastCheckAt(localDatasetId()))
    }

    // ---- 管理者の設定（画面からの変更は後のステップ） ----

    /**
     * 確認状態を変える。変更前の値を持つ変化（STATUS_CHANGED）を記録する（「元に戻す」に使う）。
     * 複数のアプリをまとめて変えたときは、同じ [operationId] を付ける。
     * @return 実際に変わったアプリの数
     */
    suspend fun setReviewStatus(
        packageNames: Collection<String>,
        status: ReviewStatus,
        operationId: String = newId(),
    ): Int {
        val datasetId = localDatasetId()
        val now = clock()
        return db.withTransaction {
            val current = recordDao.getCurrent(datasetId).associateBy { it.packageName }
            val policies = policyDao.getAll(datasetId).associateBy { it.packageName }
            val changed = packageNames.distinct().mapNotNull { packageName ->
                val before = policies[packageName]?.toPolicy()
                val oldStatus = before?.status ?: ReviewStatus.UNCONFIRMED
                if (oldStatus == status) return@mapNotNull null
                val after = (before ?: emptyPolicy(packageName, now)).copy(
                    status = status,
                    statusReason = StatusChangeReason.ADMIN,
                    updatedAt = now,
                )
                val name = current[packageName]?.name ?: before?.provisionalName ?: packageName
                after to AppChange(
                    type = ChangeType.STATUS_CHANGED,
                    packageName = packageName,
                    name = name,
                    occurredAt = now,
                    oldStatus = oldStatus,
                    newStatus = status,
                    reason = StatusChangeReason.ADMIN,
                    operationId = operationId,
                )
            }
            if (changed.isNotEmpty()) {
                policyDao.upsert(changed.map { it.first.toEntity(datasetId) })
                recordDao.insertEvents(changed.map { it.second.toEntity(datasetId) })
                // 「問題なし」にした時点で、「以前に削除したアプリ」「署名が変わった」の警告を消す（仕様書 4章）。
                // 変化の記録（app_event）はそのまま残るので、「これまでの経緯」には出る
                if (status == ReviewStatus.OK) {
                    recordDao.clearRecordedWarnings(datasetId, changed.map { it.first.packageName }, now)
                }
            }
            changed.size
        }
    }

    /** 管理者メモを保存する（空ならメモなし）。 */
    suspend fun setAdminMemo(packageName: String, memo: String?) {
        val datasetId = localDatasetId()
        val now = clock()
        db.withTransaction {
            val before = policyDao.get(datasetId, packageName)?.toPolicy() ?: emptyPolicy(packageName, now)
            policyDao.upsert(listOf(before.copy(adminMemo = memo?.takeIf { it.isNotBlank() }, updatedAt = now).toEntity(datasetId)))
        }
    }

    /** 注意アプリの事前登録（仕様書 6章）。確認状態は「要注意」で登録する。 */
    suspend fun preRegister(packageName: String, provisionalName: String?, memo: String?) {
        val datasetId = localDatasetId()
        val now = clock()
        db.withTransaction {
            val before = policyDao.get(datasetId, packageName)?.toPolicy()
            val after = (before ?: emptyPolicy(packageName, now)).copy(
                status = ReviewStatus.CAUTION,
                adminMemo = memo?.takeIf { it.isNotBlank() } ?: before?.adminMemo,
                isPreRegistered = true,
                provisionalName = provisionalName?.takeIf { it.isNotBlank() } ?: before?.provisionalName,
                statusReason = StatusChangeReason.ADMIN,
                updatedAt = now,
            )
            policyDao.upsert(listOf(after.toEntity(datasetId)))
            val oldStatus = before?.status ?: ReviewStatus.UNCONFIRMED
            if (oldStatus != ReviewStatus.CAUTION) {
                recordDao.insertEvents(
                    listOf(
                        AppChange(
                            type = ChangeType.STATUS_CHANGED,
                            packageName = packageName,
                            name = after.provisionalName ?: packageName,
                            occurredAt = now,
                            oldStatus = oldStatus,
                            newStatus = ReviewStatus.CAUTION,
                            reason = StatusChangeReason.ADMIN,
                            operationId = newId(),
                        ).toEntity(datasetId),
                    ),
                )
            }
        }
    }

    private fun emptyPolicy(packageName: String, now: Long) = PolicyState(
        packageName = packageName,
        status = ReviewStatus.UNCONFIRMED,
        adminMemo = null,
        isPreRegistered = false,
        provisionalName = null,
        statusReason = null,
        updatedAt = now,
    )
}
