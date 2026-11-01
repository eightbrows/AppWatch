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
import io.github.eightbrows.appwatch.data.db.toChange
import io.github.eightbrows.appwatch.data.db.toTracked
import io.github.eightbrows.appwatch.data.status.AppStatusEntry
import io.github.eightbrows.appwatch.data.status.AppStatusRules
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

/** 1つのアプリへの管理者の変更。[status] が null なら確認状態は変えない。[setsMemo] が false なら管理者メモは変えない。 */
data class PolicyEdit(
    val packageName: String,
    val status: ReviewStatus? = null,
    val memo: String? = null,
    val setsMemo: Boolean = false,
)

/** 操作の結果。[operationId] を「元に戻す」に渡す。 */
data class OperationResult(val operationId: String, val changedApps: Int)

/** アプリの詳細（管理者用）の中身。 */
data class AppDetail(val entry: AppStatusEntry?, val events: List<AppChange>)

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

    /** アプリの確認状態の画面の行（端末にないアプリは、確認状態・管理者メモ・事前登録が残っているものだけ）。 */
    fun observeStatusEntries(): Flow<List<AppStatusEntry>> = flow {
        val datasetId = localDatasetId()
        emitAll(
            combine(recordDao.observeCurrent(datasetId), policyDao.observeAll(datasetId)) { current, policies ->
                AppStatusRules.buildEntries(
                    tracked = current.map { it.toTracked() },
                    policies = policies.associate { it.packageName to it.toPolicy() },
                    selfPackageName = selfPackageName,
                )
            },
        )
    }

    /** アプリの詳細（管理者用）。記録も設定もないアプリなら entry は null。 */
    fun observeAppDetail(packageName: String): Flow<AppDetail> = flow {
        val datasetId = localDatasetId()
        emitAll(
            combine(
                recordDao.observeCurrentOne(datasetId, packageName),
                policyDao.observe(datasetId, packageName),
                recordDao.observeEventsFor(datasetId, packageName),
            ) { current, policy, events ->
                val policyState = policy?.toPolicy()
                val entry = when {
                    current != null -> AppStatusRules.entryOf(current.toTracked(), policyState, selfPackageName)
                    policyState != null -> AppStatusRules.entryOf(policyState, selfPackageName)
                    else -> null
                }
                AppDetail(entry = entry, events = events.map { it.toChange() })
            },
        )
    }

    // ---- 管理者の設定 ----

    /**
     * 確認状態を変える（一括操作・初回設定）。[applyOperation] の簡単な形。
     * @return 実際に変わったアプリの数
     */
    suspend fun setReviewStatus(
        packageNames: Collection<String>,
        status: ReviewStatus,
        operationId: String = newId(),
    ): Int = applyOperation(packageNames.distinct().map { PolicyEdit(it, status = status) }, operationId).changedApps

    /** 管理者メモを保存する（空ならメモなし）。 */
    suspend fun setAdminMemo(packageName: String, memo: String?) {
        applyOperation(listOf(PolicyEdit(packageName, memo = memo, setsMemo = true)))
    }

    /**
     * 確認状態・管理者メモの変更を、1つの操作として行う（仕様書 6章）。
     * 変更前の値（確認状態・管理者メモ・消した警告）を変化として記録し、同じ [operationId] を付ける（「元に戻す」に使う）。
     * 「問題なし」にしたときは、「以前に削除したアプリ」「署名が変わった」の警告を消す（仕様書 4章）。
     * 変化の記録（app_event）はそのまま残るので、「これまでの経緯」には出る。
     */
    suspend fun applyOperation(edits: List<PolicyEdit>, operationId: String = newId()): OperationResult {
        val datasetId = localDatasetId()
        val now = clock()
        return db.withTransaction {
            val current = recordDao.getCurrent(datasetId).associateBy { it.packageName }
            val policies = policyDao.getAll(datasetId).associateBy { it.packageName }
            val newPolicies = mutableListOf<PolicyState>()
            val events = mutableListOf<AppChange>()
            val cleared = mutableListOf<String>()
            for (edit in edits) {
                val before = policies[edit.packageName]?.toPolicy()
                var after = before ?: emptyPolicy(edit.packageName, now)
                val row = current[edit.packageName]
                val name = row?.name ?: before?.provisionalName ?: edit.packageName
                val oldStatus = before?.status ?: ReviewStatus.UNCONFIRMED
                val hasRecordedWarnings = row != null && (row.reinstalledAt != null || row.signatureChangedAt != null)
                // すでに「問題なし」でも、後から再導入の警告が付いたアプリは、「問題なし」にし直すと警告を消す
                val newStatus = edit.status
                if (newStatus != null &&
                    (newStatus != oldStatus || (newStatus == ReviewStatus.OK && hasRecordedWarnings))
                ) {
                    val clears = newStatus == ReviewStatus.OK && hasRecordedWarnings
                    after = after.copy(status = newStatus, statusReason = StatusChangeReason.ADMIN)
                    events += AppChange(
                        type = ChangeType.STATUS_CHANGED,
                        packageName = edit.packageName,
                        name = name,
                        occurredAt = now,
                        oldStatus = oldStatus,
                        newStatus = newStatus,
                        reason = StatusChangeReason.ADMIN,
                        oldReinstalledAt = if (clears) row?.reinstalledAt else null,
                        oldSignatureChangedAt = if (clears) row?.signatureChangedAt else null,
                        operationId = operationId,
                    )
                    if (clears) cleared += edit.packageName
                }
                val newMemo = edit.memo?.takeIf { it.isNotBlank() }
                if (edit.setsMemo && newMemo != before?.adminMemo) {
                    after = after.copy(adminMemo = newMemo)
                    events += AppChange(
                        type = ChangeType.MEMO_CHANGED,
                        packageName = edit.packageName,
                        name = name,
                        occurredAt = now,
                        oldAdminMemo = before?.adminMemo,
                        newAdminMemo = newMemo,
                        reason = StatusChangeReason.ADMIN,
                        operationId = operationId,
                    )
                }
                if (after != (before ?: emptyPolicy(edit.packageName, now))) newPolicies += after.copy(updatedAt = now)
            }
            if (newPolicies.isNotEmpty()) policyDao.upsert(newPolicies.map { it.toEntity(datasetId) })
            if (events.isNotEmpty()) recordDao.insertEvents(events.map { it.toEntity(datasetId) })
            if (cleared.isNotEmpty()) recordDao.clearRecordedWarnings(datasetId, cleared, now)
            OperationResult(
                operationId = operationId,
                changedApps = events.mapTo(HashSet()) { it.packageName }.size,
            )
        }
    }

    /**
     * 操作を取り消す（「元に戻す」）。変更前の確認状態・管理者メモに戻し、消した警告も戻す。
     * 取り消しも変化として記録する（理由は「元に戻す」、新しい操作の番号）。
     * @return 戻したアプリの数
     */
    suspend fun undoOperation(operationId: String): Int {
        val datasetId = localDatasetId()
        val now = clock()
        val undoId = newId()
        return db.withTransaction {
            val done = recordDao.getEventsByOperation(datasetId, operationId).map { it.toChange() }
            val policies = policyDao.getAll(datasetId).associateBy { it.packageName }
            val newPolicies = LinkedHashMap<String, PolicyState>()
            val events = mutableListOf<AppChange>()
            // 後に記録したものから戻す
            for (change in done.asReversed()) {
                val before = newPolicies[change.packageName]
                    ?: policies[change.packageName]?.toPolicy()
                    ?: emptyPolicy(change.packageName, now)
                when (change.type) {
                    ChangeType.STATUS_CHANGED -> {
                        val restored = change.oldStatus ?: continue
                        newPolicies[change.packageName] =
                            before.copy(status = restored, statusReason = StatusChangeReason.UNDO, updatedAt = now)
                        events += change.copy(
                            occurredAt = now,
                            oldStatus = before.status,
                            newStatus = restored,
                            reason = StatusChangeReason.UNDO,
                            oldReinstalledAt = null,
                            oldSignatureChangedAt = null,
                            operationId = undoId,
                        )
                        if (change.oldReinstalledAt != null || change.oldSignatureChangedAt != null) {
                            recordDao.restoreRecordedWarnings(
                                datasetId,
                                change.packageName,
                                reinstalledAt = change.oldReinstalledAt,
                                signatureChangedAt = change.oldSignatureChangedAt,
                                now = now,
                            )
                        }
                    }
                    ChangeType.MEMO_CHANGED -> {
                        newPolicies[change.packageName] = before.copy(adminMemo = change.oldAdminMemo, updatedAt = now)
                        events += change.copy(
                            occurredAt = now,
                            oldAdminMemo = before.adminMemo,
                            newAdminMemo = change.oldAdminMemo,
                            reason = StatusChangeReason.UNDO,
                            operationId = undoId,
                        )
                    }
                    else -> Unit
                }
            }
            if (newPolicies.isNotEmpty()) policyDao.upsert(newPolicies.values.map { it.toEntity(datasetId) })
            if (events.isNotEmpty()) recordDao.insertEvents(events.map { it.toEntity(datasetId) })
            newPolicies.size
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
