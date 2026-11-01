package io.github.eightbrows.appwatch.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.eightbrows.appwatch.BuildConfig
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.settings.AppLanguage
import io.github.eightbrows.appwatch.data.settings.CheckInterval
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.data.system.HealthCheck
import io.github.eightbrows.appwatch.data.system.HealthInput
import io.github.eightbrows.appwatch.model.UsageMode
import java.time.LocalDate

/** 設定メニューから開く画面。 */
enum class SettingsDestination { APP_STATUS, PRE_REGISTER, HISTORY, EXPORT_IMPORT, IMPORTED_DATA, HEALTH_CHECK, LICENSES }

/** 設定画面などを開く。開けなかったら知らせる。 */
private fun android.content.Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.error_cannot_open_screen, Toast.LENGTH_LONG).show()
    }
}

/** 動作チェックの判定に使う値をまとめて読む（設定メニューの行と、動作チェックの画面で使う）。 */
@Composable
private fun rememberHealth(controller: SettingsController, resumeCount: Int): Pair<HealthInput, Int> {
    val interval by controller.checkInterval.collectAsState(initial = CheckInterval.DEFAULT)
    val lastCheckAt by controller.lastCheckAt.collectAsState(initial = null)
    val registered by controller.periodicCheckRegistered.collectAsState(initial = false)
    val scanDays by controller.scanDays.collectAsState(initial = emptyList())
    // 設定画面から戻ってきたとき（resumeCount が増えたとき）に読み直す
    val batteryOk = remember(resumeCount) { controller.isBatteryOptimizationExcluded() }
    val unusedOk = remember(resumeCount) { controller.isUnusedAppRestrictionsExcluded() }
    val input = HealthInput(
        batteryOptimizationExcluded = batteryOk,
        unusedAppRestrictionsExcluded = unusedOk,
        periodicCheckRegistered = registered,
        lastCheckAt = lastCheckAt,
        now = System.currentTimeMillis(),
        checkIntervalMinutes = interval.minutes,
    )
    return input to HealthCheck.checksInLastDays(scanDays, LocalDate.now())
}

/**
 * 設定メニュー（値を集めて SettingsMenuScreen に渡す）。
 * @param onSwitchToMonitor 見守る端末に戻すとき、保存の前に呼ぶ（初回設定を2の画面から始めるため）
 */
@Composable
fun SettingsMenuRoute(
    controller: SettingsController,
    resumeCount: Int,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onSwitchToMonitor: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cautionCount by controller.cautionCount.collectAsState(initial = 0)
    val setup by controller.setupState.collectAsState(initial = null)
    val interval by controller.checkInterval.collectAsState(initial = CheckInterval.DEFAULT)
    val (healthInput, _) = rememberHealth(controller, resumeCount)
    // 言語を切り替えると Activity が作り直されるので、ここでは表示用に持つだけでよい
    var language by remember { mutableStateOf(AppLanguage.current()) }

    SettingsMenuScreen(
        state = SettingsMenuState(
            cautionCount = cautionCount,
            importedDataNames = emptyList(), // 取り込みは後のステップ
            usageMode = setup?.usageMode ?: UsageMode.MONITOR,
            checkInterval = interval,
            healthAllOk = HealthCheck.evaluate(healthInput).allOk,
            themeMode = themeMode,
            language = language,
            batteryOptimizationExcluded = healthInput.batteryOptimizationExcluded,
            unusedAppRestrictionsExcluded = healthInput.unusedAppRestrictionsExcluded,
            showsUnusedAppRestrictions = controller.showsUnusedAppRestrictions,
            versionName = BuildConfig.VERSION_NAME,
        ),
        unusedAppHint = stringResource(controller.unusedAppRestrictionsHint),
        actions = SettingsMenuActions(
            onBack = onBack,
            onOpenAppStatus = { onNavigate(SettingsDestination.APP_STATUS) },
            onOpenPreRegister = { onNavigate(SettingsDestination.PRE_REGISTER) },
            onOpenHistory = { onNavigate(SettingsDestination.HISTORY) },
            onOpenExportImport = { onNavigate(SettingsDestination.EXPORT_IMPORT) },
            onOpenImportedData = { onNavigate(SettingsDestination.IMPORTED_DATA) },
            onUsageModeChange = { mode ->
                when (mode) {
                    UsageMode.VIEW_ONLY -> controller.switchToViewOnly()
                    UsageMode.MONITOR -> {
                        onSwitchToMonitor()
                        controller.switchToMonitor()
                    }
                }
            },
            onCheckIntervalChange = controller::setCheckInterval,
            onOpenHealthCheck = { onNavigate(SettingsDestination.HEALTH_CHECK) },
            onThemeModeChange = { mode ->
                onThemeModeChange(mode)
                controller.refreshWidget()
            },
            onLanguageChange = { selected ->
                if (selected != language) {
                    language = selected
                    controller.refreshWidget()
                    AppLanguage.apply(selected)
                }
            },
            onOpenBatterySettings = { context.startSafely(controller.batteryOptimizationIntent()) },
            onOpenUnusedAppSettings = { context.startSafely(controller.unusedAppRestrictionsIntent()) },
            onOpenLicenses = { onNavigate(SettingsDestination.LICENSES) },
            onOpenWebsite = { context.startSafely(controller.websiteIntent()) },
        ),
    )
}

/** 動作チェック（値を集めて HealthCheckScreen に渡す）。 */
@Composable
fun HealthCheckRoute(controller: SettingsController, resumeCount: Int, onBack: () -> Unit) {
    val interval by controller.checkInterval.collectAsState(initial = CheckInterval.DEFAULT)
    val (input, checksInLast7Days) = rememberHealth(controller, resumeCount)
    val result = HealthCheck.evaluate(input)
    HealthCheckScreen(
        state = HealthCheckState(
            allOk = result.allOk,
            stopped = result.stopped,
            batteryOptimizationExcluded = input.batteryOptimizationExcluded,
            unusedAppRestrictionsExcluded = input.unusedAppRestrictionsExcluded,
            showsUnusedAppRestrictions = controller.showsUnusedAppRestrictions,
            lastCheckAt = input.lastCheckAt,
            checkInterval = interval,
            checksInLast7Days = checksInLast7Days,
            periodicCheckRegistered = input.periodicCheckRegistered,
        ),
        onBack = onBack,
    )
}

/** ライセンス（リポジトリを開くボタン付き）。 */
@Composable
fun LicenseRoute(controller: SettingsController, onBack: () -> Unit) {
    val context = LocalContext.current
    LicenseScreen(onBack = onBack, onOpenRepository = { context.startSafely(controller.repositoryIntent()) })
}
