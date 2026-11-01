package io.github.eightbrows.appwatch

import android.app.Application
import android.os.Build
import android.util.Log
import io.github.eightbrows.appwatch.data.apps.AppClassifier
import io.github.eightbrows.appwatch.data.apps.PackageScanner
import io.github.eightbrows.appwatch.data.db.AppWatchDatabase
import io.github.eightbrows.appwatch.data.record.CheckRunner
import io.github.eightbrows.appwatch.data.record.DeviceEnvironment
import io.github.eightbrows.appwatch.data.record.RecordOutcome
import io.github.eightbrows.appwatch.data.record.RecordRepository
import io.github.eightbrows.appwatch.data.settings.SettingsRepository
import io.github.eightbrows.appwatch.data.settings.SetupState
import io.github.eightbrows.appwatch.widget.AppWatchWidget
import io.github.eightbrows.appwatch.widget.WidgetPlacement
import io.github.eightbrows.appwatch.work.CheckScheduler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** アプリ全体で1つだけ持つものを置く。 */
class AppWatchApplication : Application() {

    /** アプリが動いている間ずっと続く処理用。 */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val database: AppWatchDatabase by lazy { AppWatchDatabase.create(this) }

    val recordRepository: RecordRepository by lazy {
        RecordRepository(
            db = database,
            environment = DeviceEnvironment(
                deviceModel = Build.MODEL,
                osVersion = "Android ${Build.VERSION.RELEASE}",
                sdkInt = Build.VERSION.SDK_INT,
                appVersion = BuildConfig.VERSION_NAME,
            ),
            selfPackageName = packageName,
        )
    }

    val checkRunner: CheckRunner by lazy {
        val scanner = PackageScanner(this)
        CheckRunner(
            scope = appScope,
            scanner = { scanner.scan().map { AppClassifier.classify(it, packageName) } },
            recorder = recordRepository,
            // 初回設定が終わるまでと閲覧専用では、自動のチェックを行わない（仕様書 3章・6章）
            isAutomaticCheckAllowed = { settingsRepository.setupState.first().isMonitoring },
            onRecorded = { outcome ->
                if (BuildConfig.DEBUG) logOutcome(outcome)
                // チェックのあとウィジェットを更新する（アプリを開いたときのチェックも含む）
                appScope.launch { AppWatchWidget.updateAll(this@AppWatchApplication) }
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 起動時と、初回設定・使い方・チェック間隔が変わったときに、見守りの仕組みを合わせる
        appScope.launch {
            combine(settingsRepository.setupState, settingsRepository.checkIntervalMinutes, ::Pair)
                .collect { (setup, minutes) -> applyMonitoringState(setup, minutes) }
        }
    }

    /**
     * 見守る端末（初回設定が完了）なら定期チェックを登録し、そうでなければ登録を外す。
     * 閲覧専用ではウィジェットの受け取りを無効にして、ウィジェットの一覧に出ないようにする。
     * 記録（データベース）はどの場合も消さない。
     */
    private suspend fun applyMonitoringState(setup: SetupState, intervalMinutes: Int) {
        if (setup.isMonitoring) {
            CheckScheduler.schedule(this, intervalMinutes)
        } else {
            CheckScheduler.cancel(this)
        }
        WidgetPlacement.setEnabled(this, setup.usesWidget)
        if (setup.usesWidget) AppWatchWidget.updateAll(this)
    }

    /** 開発版だけ、チェックの結果をログに出す（adb logcat -s AppWatch で見られる）。 */
    private fun logOutcome(outcome: RecordOutcome) {
        if (outcome.isBaseline) {
            Log.d(LOG_TAG, "baseline: ${outcome.appCount} apps")
            return
        }
        Log.d(LOG_TAG, "check: ${outcome.appCount} apps, ${outcome.changes.size} changes")
        outcome.changes.forEach { change ->
            val detail = buildList {
                change.oldVersionName?.let { add("${it} -> ${change.versionName}") }
                if (change.isReinstall) add("reinstall")
                change.newStatus?.let { add("${change.oldStatus} -> $it (${change.reason})") }
            }.joinToString(" ")
            Log.d(LOG_TAG, "  ${change.type} ${change.packageName} $detail")
        }
    }

    private companion object {
        const val LOG_TAG = "AppWatch"
    }
}
