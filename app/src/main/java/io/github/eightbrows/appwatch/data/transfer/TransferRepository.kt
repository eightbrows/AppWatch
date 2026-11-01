package io.github.eightbrows.appwatch.data.transfer

import androidx.room.withTransaction
import io.github.eightbrows.appwatch.data.db.AppWatchDatabase
import io.github.eightbrows.appwatch.data.db.DatasetEntity
import io.github.eightbrows.appwatch.data.db.DatasetKind
import io.github.eightbrows.appwatch.data.record.DeviceEnvironment
import io.github.eightbrows.appwatch.data.record.RecordRepository
import io.github.eightbrows.appwatch.model.InstalledApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.UUID

/** 「閲覧のみ」で取り込んだ結果。 */
data class ImportOutcome(val datasetId: String, val name: String, val replaced: Boolean)

/** 復元の結果。[newlyFound] は「はい」で新しく見つかったプリインストール・システムのアプリ（まとめて問題なしにするか尋ねる）。 */
data class RestoreOutcome(val localDatasetId: String, val newlyFound: List<String>)

/**
 * 書き出し・取り込み・復元のデータベースの処理（仕様書 7章）。
 * 置き換えはどれも1つのトランザクションで行い、途中で失敗したら元のデータのままにする。
 */
class TransferRepository(
    private val db: AppWatchDatabase,
    private val records: RecordRepository,
    private val environment: DeviceEnvironment,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.transferDao()

    // ---- 書き出し ----

    /** この端末のデータを書き出しファイルの中身にする（取り込んだデータは入れない）。 */
    suspend fun buildExport(settings: TransferSettings, now: Long = clock()): AppWatchFile {
        val id = records.localDatasetId()
        val content = db.withTransaction {
            DatasetContent(
                baseline = dao.getBaseline(id),
                current = dao.getCurrent(id),
                events = dao.getEvents(id),
                policies = dao.getPolicies(id),
                scanDays = dao.getScanDays(id),
            )
        }
        return AppWatchFile(
            formatVersion = AppWatchFile.CURRENT_VERSION,
            exportedAt = now,
            device = DeviceInfo(
                id = id,
                model = environment.deviceModel,
                osVersion = environment.osVersion,
                sdkInt = environment.sdkInt,
                appVersion = environment.appVersion,
            ),
            settings = settings,
            content = content,
        )
    }

    // ---- 取り込んだデータ ----

    /** 取り込んだデータ（新しく取り込んだ順）と、それぞれの要注意の件数。 */
    fun observeImported(): Flow<List<ImportedDataset>> =
        combine(dao.observeImported(), dao.observeCautionCounts()) { datasets, counts ->
            val countById = counts.associate { it.datasetId to it.count }
            datasets.map { it.toImported(countById[it.id] ?: 0) }
        }

    suspend fun getImported(): List<ImportedDataset> = observeImported().first()

    /**
     * 「閲覧のみ」：取り込んだデータとして追加する。同じ端末（識別番号が同じ）のデータがあれば中身を置き換え、
     * 前のデータの書き出し日時を「前回の書き出し日時」に残す。
     */
    suspend fun importForViewing(file: AppWatchFile, name: String): ImportOutcome {
        val now = clock()
        val displayName = name.trim().ifEmpty { file.device.model }
        return db.withTransaction {
            val existing = dao.getImported().firstOrNull { it.deviceId == file.device.id }
            val id = existing?.id ?: newId()
            if (existing != null) clearContent(id)
            // 同じファイルをもう一度取り込んだときは、前回の書き出し日時を変えない
            val previous = when {
                existing == null -> null
                existing.exportedAt == file.exportedAt -> existing.previousExportedAt
                else -> existing.exportedAt
            }
            dao.upsertDataset(
                DatasetEntity(
                    id = id,
                    name = displayName,
                    kind = DatasetKind.IMPORTED,
                    deviceId = file.device.id,
                    deviceModel = file.device.model,
                    osVersion = file.device.osVersion,
                    sdkInt = file.device.sdkInt,
                    appVersion = file.device.appVersion,
                    createdAt = existing?.createdAt ?: now,
                    exportedAt = file.exportedAt,
                    previousExportedAt = previous,
                    importedAt = now,
                ),
            )
            insertContent(file.content.withDataset(id))
            ImportOutcome(datasetId = id, name = displayName, replaced = existing != null)
        }
    }

    suspend fun rename(datasetId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) dao.renameDataset(datasetId, trimmed)
    }

    /** 取り込んだデータを削除する（この端末のデータは消さない）。 */
    suspend fun deleteImported(datasetId: String) {
        db.withTransaction {
            val dataset = dao.getDataset(datasetId) ?: return@withTransaction
            if (dataset.kind != DatasetKind.IMPORTED) return@withTransaction
            clearContent(datasetId)
            dao.deleteDatasetRow(datasetId)
        }
    }

    // ---- 復元 ----

    /**
     * この端末に復元する。この端末のデータの番号はファイルのもの（端末の識別番号）にする。
     * @param scanned 「はい」（端末を初期化・機種変更した）のときの今の端末のアプリ。今のアプリを新しい基準にする。
     *   「いいえ」なら null で、ファイルの中身をそのまま戻す（次のチェックで差が記録される）。
     */
    suspend fun restore(file: AppWatchFile, scanned: List<InstalledApp>?): RestoreOutcome {
        val now = clock()
        val plan = if (scanned != null) ImportRules.resetContent(file, scanned, now, zone()) else ResetPlan(file.content, emptyList())
        val newLocalId = file.device.id
        db.withTransaction {
            db.datasetDao().getLocal()?.let { old ->
                clearContent(old.id)
                dao.deleteDatasetRow(old.id)
            }
            dao.insertDataset(
                DatasetEntity(
                    id = newLocalId,
                    name = environment.deviceModel,
                    kind = DatasetKind.LOCAL,
                    deviceId = newLocalId,
                    deviceModel = environment.deviceModel,
                    osVersion = environment.osVersion,
                    sdkInt = environment.sdkInt,
                    appVersion = environment.appVersion,
                    createdAt = now,
                ),
            )
            insertContent(plan.content.withDataset(newLocalId))
        }
        records.onLocalDatasetReplaced(newLocalId)
        return RestoreOutcome(localDatasetId = newLocalId, newlyFound = plan.newlyFound)
    }

    // ---- 共通 ----

    private suspend fun clearContent(datasetId: String) {
        dao.deleteBaseline(datasetId)
        dao.deleteCurrent(datasetId)
        dao.deleteEvents(datasetId)
        dao.deletePolicies(datasetId)
        dao.deleteScanDays(datasetId)
    }

    private suspend fun insertContent(content: DatasetContent) {
        if (content.baseline.isNotEmpty()) dao.insertBaseline(content.baseline)
        if (content.current.isNotEmpty()) dao.insertCurrent(content.current)
        if (content.events.isNotEmpty()) dao.insertEvents(content.events)
        if (content.policies.isNotEmpty()) dao.insertPolicies(content.policies)
        if (content.scanDays.isNotEmpty()) dao.insertScanDays(content.scanDays)
    }

    private fun DatasetEntity.toImported(cautionCount: Int) = ImportedDataset(
        id = id,
        deviceId = deviceId,
        name = name,
        deviceModel = deviceModel,
        osVersion = osVersion,
        exportedAt = exportedAt ?: createdAt,
        previousExportedAt = previousExportedAt,
        importedAt = importedAt ?: createdAt,
        cautionCount = cautionCount,
    )
}
