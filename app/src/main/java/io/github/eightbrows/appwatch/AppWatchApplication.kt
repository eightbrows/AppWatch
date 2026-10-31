package io.github.eightbrows.appwatch

import android.app.Application
import android.util.Log
import io.github.eightbrows.appwatch.data.apps.AppListStore
import io.github.eightbrows.appwatch.data.apps.PackageScanner
import io.github.eightbrows.appwatch.data.settings.SettingsRepository
import io.github.eightbrows.appwatch.model.InstalledApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** アプリ全体で1つだけ持つものを置く。 */
class AppWatchApplication : Application() {

    /** アプリが動いている間ずっと続く処理用。 */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val appListStore: AppListStore by lazy {
        val scanner = PackageScanner(this)
        AppListStore(
            scanner = scanner::scan,
            selfPackageName = packageName,
            scope = appScope,
            onLoaded = { if (BuildConfig.DEBUG) logSummary(it) },
        )
    }

    /** 開発版だけ、判定結果の件数をログに出す（adb logcat -s AppWatch で見られる）。 */
    private fun logSummary(apps: List<InstalledApp>) {
        val kinds = apps.groupingBy { it.kind }.eachCount()
        val sources = apps.groupingBy { it.kind to it.source }.eachCount()
        val warnings = apps.flatMap { app -> app.warnings.map { it to app.kind } }.groupingBy { it }.eachCount()
        Log.d(LOG_TAG, "total=${apps.size} kinds=$kinds")
        Log.d(LOG_TAG, "sources=$sources")
        Log.d(LOG_TAG, "warnings=$warnings disabled=${apps.count { !it.isEnabled }}")
        apps.filter { it.warnings.isNotEmpty() }.forEach {
            Log.d(LOG_TAG, "warn ${it.packageName} ${it.kind} installer=${it.installerPackage} ${it.warnings}")
        }
    }

    private companion object {
        const val LOG_TAG = "AppWatch"
    }
}
