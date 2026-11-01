package io.github.eightbrows.appwatch.ui.history

import android.content.Context
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.data.record.HistoryRecords
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** 履歴と比較の画面で使う値。画面の部品から切り離してある。 */
class HistoryController(context: Context) {

    private val app = context.applicationContext as AppWatchApplication
    private val repository = app.recordRepository

    /**
     * 見ているデータの記録（端末の変化とチェックの実行記録）。
     * 今はこの端末のデータだけ。取り込んだデータへの切り替えはステップ10で、読むデータの番号を変えて行う。
     */
    val records: Flow<HistoryRecords> = flow {
        emitAll(repository.observeHistory(repository.localDatasetId()))
    }
}
