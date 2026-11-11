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
import io.github.eightbrows.appwatch.data.status.FilterItem
import io.github.eightbrows.appwatch.data.status.StatusFilter

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

    /** チェック間隔（分）。[CHECK_INTERVALS] のどれか（仕様書 5章）。保存されていなければ初期値（12時間）。 */
    val checkIntervalMinutes: Flow<Int> = dataStore.data
        .map { prefs -> prefs[KEY_CHECK_INTERVAL]?.takeIf { it in CHECK_INTERVALS } ?: DEFAULT_CHECK_INTERVAL }
        .distinctUntilChanged()

    suspend fun setCheckIntervalMinutes(minutes: Int) {
        require(minutes in CHECK_INTERVALS) { "チェック間隔は $CHECK_INTERVALS のどれか: $minutes" }
        dataStore.edit { it[KEY_CHECK_INTERVAL] = minutes }
    }

    /**
     * 初回設定の状態。保存されていなければ「未完了」（[SETUP_COMPLETED_IF_UNSET]）。
     * 前の版で閲覧専用だった端末も「未完了」になる（[SetupState.fromStored]）。
     * 起動時の行き先、チェックを行うかどうか、ウィジェットの「管理者に初期設定を依頼して下さい」の判定に使う。
     */
    val setupState: Flow<SetupState> = dataStore.data
        .map { prefs ->
            SetupState.fromStored(
                completed = prefs[KEY_SETUP_COMPLETED] ?: SETUP_COMPLETED_IF_UNSET,
                legacyUsageMode = prefs[KEY_LEGACY_USAGE_MODE],
            )
        }
        .distinctUntilChanged()

    /** 初回設定を終えたことを保存する。前の版の使い方の値は、もう使わないのでここで消す。 */
    suspend fun completeSetup() {
        dataStore.edit {
            it[KEY_SETUP_COMPLETED] = true
            it.remove(KEY_LEGACY_USAGE_MODE)
        }
    }

    /** 初回設定の完了状態を消す（開発版の「初回設定のやり直し」だけで使う）。記録や他の設定値は残す。 */
    suspend fun clearSetupState() {
        dataStore.edit {
            it.remove(KEY_SETUP_COMPLETED)
            it.remove(KEY_LEGACY_USAGE_MODE)
        }
    }

    /**
     * アプリの確認状態の画面の絞り込み。オフにした項目の名前を保存する（保存がなければ「システム」だけオフ）。
     * 前の版の名前は読み替える（[StatusFilter.LEGACY_NAMES]）。
     */
    val statusFilterEnabled: Flow<Set<FilterItem>> = dataStore.data
        .map { prefs ->
            val off = prefs[KEY_STATUS_FILTER_OFF]?.let { StatusFilter.offFromSaved(it) } ?: StatusFilter.DEFAULT_OFF
            StatusFilter.enabledFrom(off)
        }
        .distinctUntilChanged()

    suspend fun setStatusFilterEnabled(enabled: Set<FilterItem>) {
        dataStore.edit { prefs -> prefs[KEY_STATUS_FILTER_OFF] = StatusFilter.savedNames(enabled) }
    }

    /** 選択中のデータの番号（仕様書 7章）。保存がなければ null（「この端末」）。 */
    val selectedDatasetId: Flow<String?> = dataStore.data
        .map { prefs -> prefs[KEY_SELECTED_DATASET] }
        .distinctUntilChanged()

    suspend fun setSelectedDatasetId(id: String?) {
        dataStore.edit { prefs -> if (id == null) prefs.remove(KEY_SELECTED_DATASET) else prefs[KEY_SELECTED_DATASET] = id }
    }

    /** 最近の書き出し（新しい順、最大 [RECENT_EXPORTS_MAX] 件）。ファイルは開かず、表示だけに使う。 */
    val recentExports: Flow<List<RecentExport>> = dataStore.data
        .map { prefs -> RecentExport.decode(prefs[KEY_RECENT_EXPORTS].orEmpty()) }
        .distinctUntilChanged()

    suspend fun addRecentExport(export: RecentExport) {
        dataStore.edit { prefs ->
            val list = listOf(export) + RecentExport.decode(prefs[KEY_RECENT_EXPORTS].orEmpty())
            prefs[KEY_RECENT_EXPORTS] = RecentExport.encode(list.take(RECENT_EXPORTS_MAX))
        }
    }

    /** 復元したファイルの設定（チェック間隔・テーマ）を、1回の書き込みで戻す。 */
    suspend fun restoreTransferSettings(checkIntervalMinutes: Int, themeMode: ThemeMode) {
        dataStore.edit { prefs ->
            if (checkIntervalMinutes in CHECK_INTERVALS) prefs[KEY_CHECK_INTERVAL] = checkIntervalMinutes
            prefs[KEY_THEME_MODE] = themeMode.name
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
        /** 前の版の使い方（見守る端末／閲覧専用）。今は読むだけで、初回設定を終えると消す。 */
        private val KEY_LEGACY_USAGE_MODE = stringPreferencesKey("usage_mode")
        private val KEY_STATUS_FILTER_OFF = stringSetPreferencesKey("status_filter_off")
        private val KEY_SELECTED_DATASET = stringPreferencesKey("selected_dataset_id")
        private val KEY_RECENT_EXPORTS = stringPreferencesKey("recent_exports")
        const val RECENT_EXPORTS_MAX = 5
    }
}
