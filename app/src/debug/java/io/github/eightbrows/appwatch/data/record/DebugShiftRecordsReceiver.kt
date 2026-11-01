package io.github.eightbrows.appwatch.data.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import io.github.eightbrows.appwatch.AppWatchApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 【開発版だけ】この端末の記録を、まとめて n 日前にずらす（n がマイナスなら後にずらして戻す）。正式版には入らない。
 * 履歴と比較を、何日分もの記録で確かめるために使う（端末の時計は変えない）。
 *
 * 使い方（adb から。DUMP の権限が要るので、端末上の他のアプリからは呼べない）：
 *   adb shell am broadcast -n io.github.eightbrows.appwatch/.data.record.DebugShiftRecordsReceiver --ei days 10
 *
 * ずらすのは、記録した時刻（基準・最新の状態・変化・管理者の設定・チェックの実行記録）だけ。
 * 端末から読んだ値（インストール日時など）はそのまま。
 */
class DebugShiftRecordsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val days = intent.getIntExtra("days", 0)
        if (days == 0) {
            Log.w(TAG, "days を 0 以外で指定して下さい")
            return
        }
        val app = context.applicationContext as AppWatchApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val datasetId = app.recordRepository.localDatasetId()
                val shift = days * 86_400_000L
                val db = app.database
                db.withTransaction {
                    val sql = db.openHelper.writableDatabase
                    val args = arrayOf<Any>(shift, datasetId)
                    sql.execSQL("UPDATE dataset SET created_at = created_at - ?1 WHERE id = ?2", args)
                    sql.execSQL("UPDATE baseline_app SET captured_at = captured_at - ?1 WHERE dataset_id = ?2", args)
                    sql.execSQL(
                        "UPDATE app_current SET first_seen_at = first_seen_at - ?1, added_at = added_at - ?1, " +
                            "removed_at = removed_at - ?1, reinstalled_at = reinstalled_at - ?1, " +
                            "signature_changed_at = signature_changed_at - ?1, updated_at = updated_at - ?1 WHERE dataset_id = ?2",
                        args,
                    )
                    sql.execSQL(
                        "UPDATE app_event SET occurred_at = occurred_at - ?1, old_reinstalled_at = old_reinstalled_at - ?1, " +
                            "old_signature_changed_at = old_signature_changed_at - ?1 WHERE dataset_id = ?2",
                        args,
                    )
                    sql.execSQL("UPDATE app_policy SET updated_at = updated_at - ?1 WHERE dataset_id = ?2", args)
                    // 日付は主キーなので、一度別の形にしてから戻す（ずらす途中で同じ日付がぶつからないように）
                    sql.execSQL(
                        "UPDATE scan_daily SET date = 'x' || date(date, ?3), last_at = last_at - ?1 WHERE dataset_id = ?2",
                        arrayOf<Any>(shift, datasetId, if (days > 0) "-$days days" else "+${-days} days"),
                    )
                    sql.execSQL("UPDATE scan_daily SET date = substr(date, 2) WHERE dataset_id = ?1 AND date LIKE 'x%'", arrayOf<Any>(datasetId))
                }
                Log.i(TAG, "記録を $days 日前にずらしました")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AppWatch"
    }
}
