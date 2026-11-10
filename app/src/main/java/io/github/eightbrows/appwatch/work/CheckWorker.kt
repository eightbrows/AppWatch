package io.github.eightbrows.appwatch.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.data.record.CheckOutcome
import io.github.eightbrows.appwatch.widget.AppWatchWidget
import java.util.concurrent.TimeUnit

/**
 * 定期チェック（仕様書 5章）。アプリを開いたときと同じ CheckRunner を使うので、
 * 同時に2回動かない・直前1分以内なら省略、もそのまま効く。チェックのあとウィジェットを更新する。
 */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AppWatchApplication
        // 初回設定の前は何もしない（登録はアプリ側で外すが、残っていても安全なように）
        val outcome = app.checkRunner.checkIfAllowed()
        // 省略したときも、時間の経過で「停止」の表示が変わることがあるので更新する
        AppWatchWidget.updateAll(applicationContext)
        return if (outcome is CheckOutcome.Failed) Result.retry() else Result.success()
    }
}

/** 定期チェックの登録。 */
object CheckScheduler {

    /** 一意のジョブの名前。同じ名前で登録し直しても、ジョブは1つのまま。 */
    const val UNIQUE_WORK_NAME = "periodic_check"

    /**
     * 定期チェックを登録する。アプリの起動時に毎回呼ぶ。
     * - すでに同じ間隔で登録されていれば、次の実行の予定は変わらない（UPDATE）
     * - 間隔が変わっていれば、新しい間隔にする（設定メニューのチェック間隔）
     * - 端末を再起動しても、WorkManager が登録を引き継ぐ
     * @param uniqueName 一意のジョブの名前（テストで本物の登録に触れないように、別の名前を渡せる）
     */
    fun schedule(context: Context, intervalMinutes: Int, uniqueName: String = UNIQUE_WORK_NAME) {
        val request = PeriodicWorkRequestBuilder<CheckWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** 定期チェックの登録を外す（初回設定の前）。登録がなければ何もしない。 */
    fun cancel(context: Context, uniqueName: String = UNIQUE_WORK_NAME) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
    }
}
