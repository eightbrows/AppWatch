package io.github.eightbrows.appwatch.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.annotation.StringRes
import androidx.work.WorkManager
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.settings.CheckInterval
import io.github.eightbrows.appwatch.data.settings.SetupState
import io.github.eightbrows.appwatch.data.system.DeviceSettings
import io.github.eightbrows.appwatch.data.system.ScanDay
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.model.UsageMode
import io.github.eightbrows.appwatch.work.CheckScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 設定メニューと、そこから開く画面（動作チェックなど）で使う値と処理。画面の部品から切り離してある。 */
class SettingsController(context: Context) {

    private val appContext = context.applicationContext
    private val app = appContext as AppWatchApplication

    // ---- 値（変わると流れる） ----

    /** この端末の「要注意」の件数（端末にあるアプリ）。 */
    val cautionCount: Flow<Int> = app.recordRepository.observePresentApps()
        .map { apps -> apps.count { it.reviewStatus == ReviewStatus.CAUTION } }
        .distinctUntilChanged()

    val setupState: Flow<SetupState> = app.settingsRepository.setupState

    val checkInterval: Flow<CheckInterval> = app.settingsRepository.checkIntervalMinutes.map(CheckInterval::fromMinutes)

    val lastCheckAt: Flow<Long?> = app.recordRepository.observeLastCheckAt()

    val scanDays: Flow<List<ScanDay>> = app.recordRepository.observeScanDays()

    /** 定期チェックが登録されているか（WorkManager に一意のジョブがあるか）。 */
    val periodicCheckRegistered: Flow<Boolean> = WorkManager.getInstance(appContext)
        .getWorkInfosForUniqueWorkFlow(CheckScheduler.UNIQUE_WORK_NAME)
        .map { infos -> infos.any { !it.state.isFinished } }
        .distinctUntilChanged()

    // ---- 端末の設定（設定画面から戻ったら読み直す） ----

    fun isBatteryOptimizationExcluded(): Boolean = DeviceSettings.isBatteryOptimizationExcluded(appContext)

    fun isUnusedAppRestrictionsExcluded(): Boolean = DeviceSettings.isUnusedAppRestrictionsExcluded(appContext)

    val showsUnusedAppRestrictions: Boolean = DeviceSettings.hasUnusedAppRestrictions

    fun batteryOptimizationIntent(): Intent = DeviceSettings.batteryOptimizationIntent(appContext)

    fun unusedAppRestrictionsIntent(): Intent = DeviceSettings.unusedAppRestrictionsIntent(appContext)

    @get:StringRes
    val unusedAppRestrictionsHint: Int get() = DeviceSettings.unusedAppRestrictionsHint()

    // ---- 処理 ----

    /** チェック間隔を保存する。保存すると、アプリ側で定期チェックが新しい間隔で登録し直される。 */
    fun setCheckInterval(interval: CheckInterval) {
        app.appScope.launch { app.settingsRepository.setCheckIntervalMinutes(interval.minutes) }
    }

    /**
     * 閲覧専用にする（仕様書 6章「使い方の切り替え」）。初回設定は完了のまま。
     * 保存すると、アプリ側で定期チェックの登録が外れ、ウィジェットの受け取りが無効になり、
     * 最初の画面が「取り込んだデータ」に変わる。この端末の記録は消さない。
     */
    fun switchToViewOnly() {
        // 画面が切り替わっても保存が途中で止まらないよう、アプリ全体の処理で行う
        app.appScope.launch { app.settingsRepository.setUsageModeAndSetup(UsageMode.VIEW_ONLY, completed = true) }
    }

    /**
     * 見守る端末に戻す。初回設定を未完了にするので、初回設定（2の画面から）に進む。
     * 3の画面でもう一度チェックし、4で完了するとトップへ。途中でアプリを閉じたら、次は初回設定の1から。
     */
    fun switchToMonitor() {
        app.appScope.launch { app.settingsRepository.setUsageModeAndSetup(UsageMode.MONITOR, completed = false) }
    }

    /** ウィジェットを更新する（言語・テーマを切り替えた後）。 */
    fun refreshWidget() = app.refreshWidget()

    fun websiteIntent(): Intent = viewIntent(appContext.getString(R.string.settings_website_url))

    fun repositoryIntent(): Intent = viewIntent(appContext.getString(R.string.repository_url))

    /** ブラウザでページを開く（インターネットの権限はブラウザ側が持つので、本アプリには要らない）。 */
    private fun viewIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, url.toUri())
}
