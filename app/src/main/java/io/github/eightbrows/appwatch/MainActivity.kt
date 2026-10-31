package io.github.eightbrows.appwatch

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.data.settings.isDark
import io.github.eightbrows.appwatch.ui.AppWatchApp
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import kotlinx.coroutines.launch

/**
 * 言語の切り替え（AppCompatDelegate.setApplicationLocales）を Android 12 以下でも効かせるため、
 * AppCompatActivity を使う。
 */
class MainActivity : AppCompatActivity() {

    private val app: AppWatchApplication get() = application as AppWatchApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 保存済みのテーマを読み終えるまでは何も描かない（一瞬違う色で表示されるのを防ぐ）
            val themeMode: ThemeMode? by app.settingsRepository.themeMode.collectAsState(initial = null)
            themeMode?.let { ThemedContent(it) }
        }
    }

    /** 起動したときと、アプリに戻ってきたとき（削除やアプリ情報の画面から戻ったときなど）に一覧を取り直す。 */
    override fun onResume() {
        super.onResume()
        app.appListStore.refresh()
    }

    @Composable
    private fun ThemedContent(mode: ThemeMode) {
        val darkTheme = mode.isDark(systemIsDark = isSystemInDarkTheme())
        val scope = rememberCoroutineScope()
        val appList by app.appListStore.state.collectAsState()

        // ステータスバー・ナビゲーションバーの文字の色を、選んだテーマに合わせる
        DisposableEffect(darkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
            )
            onDispose {}
        }

        AppWatchTheme(darkTheme = darkTheme) {
            AppWatchApp(
                themeMode = mode,
                onThemeModeChange = { newMode -> scope.launch { app.settingsRepository.setThemeMode(newMode) } },
                appList = appList,
                onRefreshAppList = app.appListStore::refresh,
            )
        }
    }

    private companion object {
        // androidx.activity の既定と同じ半透明の色（ボタン式ナビゲーションの背景）
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
