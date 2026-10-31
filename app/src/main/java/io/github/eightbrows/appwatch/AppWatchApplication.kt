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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
            onRecorded = { if (BuildConfig.DEBUG) logOutcome(it) },
        )
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
