package io.github.eightbrows.appwatch.data.transfer

import io.github.eightbrows.appwatch.data.record.RecordRepository
import io.github.eightbrows.appwatch.data.settings.SettingsRepository
import io.github.eightbrows.appwatch.model.UsageMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 選択中のデータ（仕様書 7章）。確認状態・履歴と比較・アプリの詳細で共通にする。
 * 選択は DataStore に保存し、選んでいたデータが削除されたら [DatasetSelector] の決まりで戻す。
 */
class DataSelector(
    private val settings: SettingsRepository,
    records: RecordRepository,
    transfer: TransferRepository,
) {
    val selection: Flow<DataSelection> = combine(
        settings.selectedDatasetId,
        settings.usageMode,
        records.observeLocalDatasetId(),
        transfer.observeImported(),
    ) { saved, mode, localId, imported ->
        DatasetSelector.resolve(
            savedId = saved,
            localId = localId,
            localName = "",
            imported = imported,
            viewOnly = mode == UsageMode.VIEW_ONLY,
        )
    }.distinctUntilChanged()

    suspend fun select(datasetId: String) = settings.setSelectedDatasetId(datasetId)

    /** 保存している選択を外す（選んでいたデータを削除したとき）。 */
    suspend fun clear() = settings.setSelectedDatasetId(null)
}
