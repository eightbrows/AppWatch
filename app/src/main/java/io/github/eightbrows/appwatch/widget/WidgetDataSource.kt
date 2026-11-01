package io.github.eightbrows.appwatch.widget

import android.content.Context
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.BuildConfig
import io.github.eightbrows.appwatch.data.system.DeviceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * ウィジェットの内容を、データベースと設定値から作る。データベースが変わると新しい内容が流れる。
 */
class WidgetDataSource(context: Context) {

    private val appContext = context.applicationContext
    private val app = appContext as AppWatchApplication

    fun observe(): Flow<WidgetState> = combine(
        app.recordRepository.observePresentApps(),
        app.recordRepository.observeLastCheckAt(),
        app.settingsRepository.checkIntervalMinutes,
        app.settingsRepository.setupCompleted,
        WidgetDebugOverride.status,
    ) { apps, lastCheckAt, interval, setupCompleted, override ->
        val (caution, needsReview) = WidgetStatusDecider.countApps(apps)
        val state = WidgetStatusDecider.decide(
            WidgetInput(
                cautionCount = caution,
                needsReviewCount = needsReview,
                lastCheckAt = lastCheckAt,
                now = System.currentTimeMillis(),
                checkIntervalMinutes = interval,
                deviceSettingsOk = DeviceSettings.allOk(appContext),
                setupCompleted = setupCompleted,
            ),
        )
        if (BuildConfig.DEBUG && override != null) WidgetDebugOverride.apply(state, override) else state
    }
}

/**
 * 【開発版だけ】ウィジェットの6つの状態を、データを変えずに表示して確かめるための上書き。
 * 開発版にだけ入る DebugWidgetReceiver（app/src/debug）から adb で切り替える。正式版では使われない。
 */
object WidgetDebugOverride {
    private val _status = MutableStateFlow<WidgetStatus?>(null)
    val status: StateFlow<WidgetStatus?> = _status.asStateFlow()

    fun set(status: WidgetStatus?) {
        _status.value = status
    }

    internal fun apply(state: WidgetState, override: WidgetStatus): WidgetState = state.copy(
        status = override,
        count = when (override) {
            WidgetStatus.CAUTION_APPS -> 1
            WidgetStatus.NEEDS_REVIEW -> 2
            else -> 0
        },
        lastCheckAt = if (override == WidgetStatus.SETUP_REQUIRED) null else state.lastCheckAt,
    )
}
