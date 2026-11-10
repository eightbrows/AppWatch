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

    fun observe(): Flow<WidgetState> = combine(observeState(), WidgetLocale.changes) { state, _ ->
        // 言語を切り替えたら、状態が同じでも描き直すように、言語を状態に入れる
        state.copy(languageTag = WidgetLocale.languageTag(appContext))
    }

    private fun observeState(): Flow<WidgetState> = combine(
        app.recordRepository.observePresentApps(),
        app.recordRepository.observeLastCheckAt(),
        app.settingsRepository.checkIntervalMinutes,
        app.settingsRepository.setupState,
        WidgetDebugOverride.override,
    ) { apps, lastCheckAt, interval, setup, override ->
        val (caution, needsReview) = WidgetStatusDecider.countApps(apps)
        val state = WidgetStatusDecider.decide(
            WidgetInput(
                cautionCount = caution,
                needsReviewCount = needsReview,
                lastCheckAt = lastCheckAt,
                now = System.currentTimeMillis(),
                checkIntervalMinutes = interval,
                deviceSettingsOk = DeviceSettings.allOk(appContext),
                setupCompleted = setup.completed,
            ),
        )
        if (BuildConfig.DEBUG && override != null) WidgetDebugOverride.apply(state, override) else state
    }
}

/** 開発版だけの上書きの中身。[lastCheckAt] を渡すと「最終チェック」もその時刻にする（見本の画像を撮るため）。 */
data class WidgetOverride(val status: WidgetStatus, val lastCheckAt: Long? = null)

/**
 * 【開発版だけ】ウィジェットの6つの状態を、データを変えずに表示して確かめるための上書き。
 * 開発版にだけ入る DebugWidgetReceiver（app/src/debug）から adb で切り替える。正式版では使われない。
 */
object WidgetDebugOverride {
    private val _override = MutableStateFlow<WidgetOverride?>(null)
    val override: StateFlow<WidgetOverride?> = _override.asStateFlow()

    fun set(override: WidgetOverride?) {
        _override.value = override
    }

    internal fun apply(state: WidgetState, override: WidgetOverride): WidgetState = state.copy(
        status = override.status,
        count = when (override.status) {
            WidgetStatus.CAUTION_APPS -> 1
            WidgetStatus.NEEDS_REVIEW -> 2
            else -> 0
        },
        lastCheckAt = when {
            override.status == WidgetStatus.SETUP_REQUIRED -> null
            override.lastCheckAt != null -> override.lastCheckAt
            else -> state.lastCheckAt
        },
    )
}
