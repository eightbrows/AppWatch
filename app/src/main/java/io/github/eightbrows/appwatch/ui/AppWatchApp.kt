package io.github.eightbrows.appwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.eightbrows.appwatch.data.apps.AppListState
import io.github.eightbrows.appwatch.data.apps.toSummary
import io.github.eightbrows.appwatch.data.settings.AppLanguage
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.ui.navigation.Screen
import io.github.eightbrows.appwatch.ui.navigation.rememberNavigator
import io.github.eightbrows.appwatch.ui.settings.SettingsMenuScreen
import io.github.eightbrows.appwatch.ui.top.ProvisionalTopRule
import io.github.eightbrows.appwatch.ui.top.SelfDeleteWarningDialog
import io.github.eightbrows.appwatch.ui.top.TopActions
import io.github.eightbrows.appwatch.ui.top.TopScreen
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** アプリ全体の画面。今はトップと設定メニューだけ。 */
@Composable
fun AppWatchApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appList: AppListState,
    onRefreshAppList: () -> Unit,
) {
    val context = LocalContext.current
    val navigator = rememberNavigator()
    // 画面ごとの状態（選んでいるタブ、スクロール位置など）を、別の画面から戻ったときにも保つ
    val stateHolder = rememberSaveableStateHolder()
    val goBack: () -> Unit = {
        val leaving = navigator.current
        if (navigator.back()) stateHolder.removeState(leaving.name)
    }
    BackHandler(enabled = navigator.canGoBack, onBack = goBack)

    // 本アプリの削除警告を出しているときは、そのパッケージ名（画面の回転でも消えないように保存する）
    var selfDeleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val screen = navigator.current
    stateHolder.SaveableStateProvider(screen.name) {
        when (screen) {
            Screen.TOP -> {
                val summaries = remember(appList.apps) { appList.apps?.map { it.toSummary() } }
                // 【仮の決まり・ステップ2】記録ができたら TopLists.from に置き換える
                val lists = remember(summaries) { summaries?.let(ProvisionalTopRule::lists) }
                val lastCheckAt = remember(appList.loadedAt) {
                    appList.loadedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
                }
                TopScreen(
                    lists = lists,
                    isLoading = appList.isLoading,
                    loadFailed = appList.failed,
                    lastCheckAt = lastCheckAt,
                    actions = TopActions(
                        onOpenSettings = { navigator.navigate(Screen.SETTINGS_MENU) },
                        onUninstall = { app ->
                            if (app.isSelf) {
                                selfDeleteTarget = app.packageName
                            } else {
                                AppActions.uninstall(context, app.packageName)
                            }
                        },
                        onOpenAppInfo = { app -> AppActions.openAppInfo(context, app.packageName) },
                        onShowDetails = { /* アプリの詳細は後のステップ */ },
                        onRetry = onRefreshAppList,
                    ),
                )
            }

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

    selfDeleteTarget?.let { packageName ->
        SelfDeleteWarningDialog(
            onCancel = { selfDeleteTarget = null },
            onConfirmDelete = {
                selfDeleteTarget = null
                AppActions.uninstall(context, packageName)
            },
        )
    }
}
