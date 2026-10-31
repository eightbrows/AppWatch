package io.github.eightbrows.appwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import io.github.eightbrows.appwatch.data.dummy.DummyData
import io.github.eightbrows.appwatch.data.settings.AppLanguage
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.ui.navigation.Screen
import io.github.eightbrows.appwatch.ui.navigation.rememberNavigator
import io.github.eightbrows.appwatch.ui.settings.SettingsMenuScreen
import io.github.eightbrows.appwatch.ui.top.TopScreen

/** アプリ全体の画面。今はトップと設定メニューだけ。 */
@Composable
fun AppWatchApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val navigator = rememberNavigator()
    // 画面ごとの状態（選んでいるタブ、スクロール位置など）を、別の画面から戻ったときにも保つ
    val stateHolder = rememberSaveableStateHolder()
    val goBack: () -> Unit = {
        val leaving = navigator.current
        if (navigator.back()) stateHolder.removeState(leaving.name)
    }
    BackHandler(enabled = navigator.canGoBack, onBack = goBack)

    val screen = navigator.current
    stateHolder.SaveableStateProvider(screen.name) {
        when (screen) {
            Screen.TOP -> TopScreen(
                apps = DummyData.apps,
                lastCheckAt = DummyData.lastCheckAt,
                onOpenSettings = { navigator.navigate(Screen.SETTINGS_MENU) },
            )

            Screen.SETTINGS_MENU -> {
                // 言語を切り替えると Activity が作り直されるので、ここでは表示用に持つだけでよい
                var language by remember { mutableStateOf(AppLanguage.current()) }
                SettingsMenuScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    language = language,
                    onLanguageChange = {
                        if (it != language) {
                            language = it
                            AppLanguage.apply(it)
                        }
                    },
                    onBack = goBack,
                )
            }
        }
    }
}
