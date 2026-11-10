package io.github.eightbrows.appwatch.ui.history

import android.content.Context
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.data.record.HistoryRecords
import io.github.eightbrows.appwatch.data.transfer.DataSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** 履歴と比較の画面で使う値。画面の部品から切り離してある。 */
class HistoryController(context: Context) {

    private val app = context.applicationContext as AppWatchApplication
    private val repository = app.recordRepository

    /** 選択中のデータ（確認状態・アプリの詳細と共通）。 */
    val selection: Flow<DataSelection> = app.dataSelector.selection

    /** 選択中のデータの記録（端末の変化とチェックの実行記録）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val records: Flow<HistoryRecords> = selection.flatMapLatest { sel ->
        sel.selected?.let { repository.observeHistory(it.id) } ?: flowOf(HistoryRecords(emptyList(), emptyList()))
    }

    fun select(datasetId: String) {
        app.appScope.launch { app.dataSelector.select(datasetId) }
    }
}
