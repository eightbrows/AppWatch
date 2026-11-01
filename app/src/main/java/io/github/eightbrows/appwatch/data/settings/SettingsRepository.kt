package io.github.eightbrows.appwatch.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import io.github.eightbrows.appwatch.model.UsageMode
import io.github.eightbrows.appwatch.data.status.AppStatusRules
import io.github.eightbrows.appwatch.data.status.FilterChip

/** DataStore のファイル名。自動バックアップの対象（res/xml の backup_rules・data_extraction_rules）にも書いてある。 */
internal const val SETTINGS_DATASTORE_NAME = "settings"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_DATASTORE_NAME)

/**
 * 設定値（DataStore、仕様書 8章）。
 * 言語は AppCompat のアプリ別言語の仕組みが保存するため、ここでは持たない（AppLanguage.kt）。
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    /** チェック間隔（分）。30／60／180／360 のどれか（仕様書 5章）。変更する画面は後のステップ。 */
    val checkIntervalMinutes: Flow<Int> = dataStore.data
        .map { prefs -> prefs[KEY_CHECK_INTERVAL]?.takeIf { it in CHECK_INTERVALS } ?: DEFAULT_CHECK_INTERVAL }
        .distinctUntilChanged()

    suspend fun setCheckIntervalMinutes(minutes: Int) {
        require(minutes in CHECK_INTERVALS) { "チェック間隔は $CHECK_INTERVALS のどれか: $minutes" }
        dataStore.edit { it[KEY_CHECK_INTERVAL] = minutes }
    }

    /**
     * 初回設定が終わったか。保存されていなければ「未完了」（[SETUP_COMPLETED_IF_UNSET]）。
     * 起動時の行き先、チェックを行うかどうか、ウィジェットの「管理者に初期設定を依頼して下さい」の判定に使う。
     */
    val setupCompleted: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_SETUP_COMPLETED] ?: SETUP_COMPLETED_IF_UNSET }
        .distinctUntilChanged()

    suspend fun setSetupCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_SETUP_COMPLETED] = completed }
    }

    /** 使い方（仕様書 3章）。初回設定の1の画面で選ぶまでは null。 */
    val usageMode: Flow<UsageMode?> = dataStore.data
        .map { prefs -> prefs[KEY_USAGE_MODE]?.let { saved -> UsageMode.entries.firstOrNull { it.name == saved } } }
        .distinctUntilChanged()

    suspend fun setUsageMode(mode: UsageMode) {
        dataStore.edit { it[KEY_USAGE_MODE] = mode.name }
    }

    /**
     * 使い方と初回設定の完了状態を、1回の書き込みでまとめて保存する。
     * 別々に保存すると、1つ目を保存した時点で画面が切り替わり、2つ目が保存されないことがあるため。
     */
    suspend fun setUsageModeAndSetup(mode: UsageMode, completed: Boolean) {
        dataStore.edit {
            it[KEY_USAGE_MODE] = mode.name
            it[KEY_SETUP_COMPLETED] = completed
        }
    }

    /** 初回設定の状態（完了したか・使い方）をまとめて読む。 */
    val setupState: Flow<SetupState> = dataStore.data
        .map { prefs ->
            SetupState(
                completed = prefs[KEY_SETUP_COMPLETED] ?: SETUP_COMPLETED_IF_UNSET,
                usageMode = prefs[KEY_USAGE_MODE]?.let { saved -> UsageMode.entries.firstOrNull { it.name == saved } },
            )
        }
        .distinctUntilChanged()

    /** 初回設定の完了状態と使い方を消す（開発版の「初回設定のやり直し」だけで使う）。記録や他の設定値は残す。 */
    suspend fun clearSetupState() {
        dataStore.edit {
            it.remove(KEY_SETUP_COMPLETED)
            it.remove(KEY_USAGE_MODE)
        }
    }

    /** アプリの確認状態の画面の絞り込み。オフにした選択肢を保存する（保存がなければ「システム」だけオフ）。 */
    val statusFilterEnabled: Flow<Set<FilterChip>> = dataStore.data
        .map { prefs ->
            val off = prefs[KEY_STATUS_FILTER_OFF]
                ?.mapNotNull { saved -> FilterChip.entries.firstOrNull { it.name == saved } }
                ?.toSet()
                ?: AppStatusRules.DEFAULT_OFF
            AppStatusRules.enabledFrom(off)
        }
        .distinctUntilChanged()

    suspend fun setStatusFilterEnabled(enabled: Set<FilterChip>) {
        dataStore.edit { prefs ->
            prefs[KEY_STATUS_FILTER_OFF] = (FilterChip.entries.toSet() - enabled).mapTo(HashSet()) { it.name }
        }
    }

    companion object {
        val CHECK_INTERVALS: List<Int> = CheckInterval.entries.map { it.minutes }
        val DEFAULT_CHECK_INTERVAL: Int = CheckInterval.DEFAULT.minutes

        /** 保存されていなければ未完了。今入っている端末も、更新後に開くと初回設定から始まる。 */
        const val SETUP_COMPLETED_IF_UNSET: Boolean = false

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_CHECK_INTERVAL = intPreferencesKey("check_interval_minutes")
        private val KEY_SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        private val KEY_USAGE_MODE = stringPreferencesKey("usage_mode")
        private val KEY_STATUS_FILTER_OFF = stringSetPreferencesKey("status_filter_off")
    }
}
