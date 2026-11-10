package io.github.eightbrows.appwatch.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.annotation.StringRes
import androidx.work.WorkManager
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.settings.CheckInterval
import io.github.eightbrows.appwatch.data.system.DeviceSettings
import io.github.eightbrows.appwatch.data.system.ScanDay
import io.github.eightbrows.appwatch.model.ReviewStatus
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

    /** 取り込んだデータの名前（設定メニューの「取り込んだデータ」の行の補足）。 */
    val importedNames: Flow<List<String>> = app.transferRepository.observeImported().map { list -> list.map { it.name } }

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

    /** ウィジェットを更新する（言語・テーマを切り替えた後）。 */
    fun refreshWidget() = app.refreshWidget()

    /** 言語を切り替えた後のウィジェットの更新。言語の保存を待ってから行う。 */
    fun refreshWidgetAfterLanguageChange() = app.refreshWidget(delayMillis = 1_500)

    fun websiteIntent(): Intent = viewIntent(appContext.getString(R.string.settings_website_url))

    fun repositoryIntent(): Intent = viewIntent(appContext.getString(R.string.repository_url))

    /** ブラウザでページを開く（インターネットの権限はブラウザ側が持つので、本アプリには要らない）。 */
    private fun viewIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, url.toUri())
}
