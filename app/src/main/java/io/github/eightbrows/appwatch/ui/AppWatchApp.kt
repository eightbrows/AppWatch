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
import io.github.eightbrows.appwatch.data.record.CheckState
import io.github.eightbrows.appwatch.data.settings.AppLanguage
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.ui.navigation.Screen
import io.github.eightbrows.appwatch.ui.navigation.rememberNavigator
import io.github.eightbrows.appwatch.ui.settings.SettingsMenuScreen
import io.github.eightbrows.appwatch.ui.top.SelfDeleteWarningDialog
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.ui.top.TopActions
import io.github.eightbrows.appwatch.ui.top.TopLists
import io.github.eightbrows.appwatch.ui.top.TopScreen
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** トップに出す内容。 */
data class TopData(
    /** 端末にあるアプリ（データベースの内容）。読み込む前は null */
    val apps: List<AppSummary>?,
    /** 最終チェックの時刻（scan_daily の最後の時刻）。まだ一度もチェックしていなければ null */
    val lastCheckAt: Long?,
    val check: CheckState,
)

/** アプリ全体の画面。今はトップと設定メニューだけ。 */
@Composable
fun AppWatchApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    top: TopData,
    onRetryCheck: () -> Unit,
    /** Android の削除・アプリ情報の画面を開く直前に呼ぶ（戻ったときに必ずチェックし直すため） */
    onOpenSystemScreen: () -> Unit,
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
                // まずデータベースの内容をすぐに出す。一度もチェックしていなければ「取得中」を出す
                val lists = remember(top.apps, top.lastCheckAt) {
                    if (top.apps == null || top.lastCheckAt == null) null else TopLists.from(top.apps)
                }
                val lastCheckAt = remember(top.lastCheckAt) {
                    top.lastCheckAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
                }
                TopScreen(
                    lists = lists,
                    isLoading = top.check.isChecking,
                    loadFailed = top.check.failed,
                    lastCheckAt = lastCheckAt,
                    actions = TopActions(
                        onOpenSettings = { navigator.navigate(Screen.SETTINGS_MENU) },
                        onUninstall = { app ->
                            if (app.isSelf) {
                                selfDeleteTarget = app.packageName
                            } else {
                                onOpenSystemScreen()
                                AppActions.uninstall(context, app.packageName)
                            }
                        },
                        onOpenAppInfo = { app ->
                            onOpenSystemScreen()
                            AppActions.openAppInfo(context, app.packageName)
                        },
                        onShowDetails = { /* アプリの詳細は後のステップ */ },
                        onRetry = onRetryCheck,
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
                onOpenSystemScreen()
                AppActions.uninstall(context, packageName)
            },
        )
    }
}
