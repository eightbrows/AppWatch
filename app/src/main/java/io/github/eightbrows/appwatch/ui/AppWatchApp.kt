package io.github.eightbrows.appwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.record.CheckState
import io.github.eightbrows.appwatch.data.settings.StartDestination
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.ui.navigation.Screen
import io.github.eightbrows.appwatch.ui.navigation.rememberNavigator
import io.github.eightbrows.appwatch.ui.imported.ImportedDataScreen
import io.github.eightbrows.appwatch.ui.settings.HealthCheckRoute
import io.github.eightbrows.appwatch.ui.settings.LicenseRoute
import io.github.eightbrows.appwatch.ui.settings.PlaceholderScreen
import io.github.eightbrows.appwatch.ui.settings.SettingsController
import io.github.eightbrows.appwatch.ui.settings.SettingsDestination
import io.github.eightbrows.appwatch.ui.settings.SettingsMenuRoute
import io.github.eightbrows.appwatch.ui.setup.SetupController
import io.github.eightbrows.appwatch.ui.setup.SetupScreen
import io.github.eightbrows.appwatch.ui.status.AdminAppDetailRoute
import io.github.eightbrows.appwatch.ui.status.AppStatusController
import io.github.eightbrows.appwatch.ui.status.AppStatusRoute
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

/** 起動したときに開く画面に当たる、画面の種類。 */
private fun StartDestination.toScreen(): Screen = when (this) {
    StartDestination.SETUP -> Screen.SETUP
    StartDestination.TOP -> Screen.TOP
    StartDestination.IMPORTED_DATA -> Screen.IMPORTED_DATA
}

private fun SettingsDestination.toScreen(): Screen = when (this) {
    SettingsDestination.APP_STATUS -> Screen.APP_STATUS
    SettingsDestination.PRE_REGISTER -> Screen.PRE_REGISTER
    SettingsDestination.HISTORY -> Screen.HISTORY
    SettingsDestination.EXPORT_IMPORT -> Screen.EXPORT_IMPORT
    SettingsDestination.IMPORTED_DATA -> Screen.IMPORTED_DATA
    SettingsDestination.HEALTH_CHECK -> Screen.HEALTH_CHECK
    SettingsDestination.LICENSES -> Screen.LICENSES
}

/** アプリ全体の画面（初回設定、トップ、取り込んだデータ、設定メニュー）。 */
@Composable
fun AppWatchApp(
    startDestination: StartDestination,
    setupController: SetupController,
    settingsController: SettingsController,
    statusController: AppStatusController,
    /** アプリに戻ってくるたびに増える数（初回設定で、設定画面から戻ったときに状態を読み直す） */
    resumeCount: Int,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    top: TopData,
    onRetryCheck: () -> Unit,
    /** Android の削除・アプリ情報の画面を開く直前に呼ぶ（戻ったときに必ずチェックし直すため） */
    onOpenSystemScreen: () -> Unit,
) {
    val context = LocalContext.current
    val navigator = rememberNavigator(start = startDestination.toScreen())
    // 画面ごとの状態（選んでいるタブ、スクロール位置など）を、別の画面から戻ったときにも保つ
    val stateHolder = rememberSaveableStateHolder()
    // 最初の画面を切り替える。それまでの画面の状態（初回設定の途中の画面など）は捨てる
    val resetTo: (Screen) -> Unit = { target ->
        navigator.snapshot().forEach { stateHolder.removeState(it.name) }
        navigator.resetTo(target)
    }
    // 初回設定の完了・使い方の変更・開発版のやり直しで行き先が変わったら、最初の画面を切り替える
    LaunchedEffect(startDestination) {
        if (navigator.root != startDestination.toScreen()) resetTo(startDestination.toScreen())
    }
    // 見守る端末に戻すときは、初回設定を2の画面から始める（アプリを閉じて開き直したら1から）
    var setupInitialStep by rememberSaveable { mutableIntStateOf(1) }
    val goBack: () -> Unit = {
        val leaving = navigator.current
        if (navigator.back()) stateHolder.removeState(leaving.name)
    }
    BackHandler(enabled = navigator.canGoBack, onBack = goBack)

    // 本アプリの削除警告を出しているときは、そのパッケージ名（画面の回転でも消えないように保存する）
    var selfDeleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    // アプリの詳細（管理者）で開いているアプリのパッケージ名
    var detailPackage by rememberSaveable { mutableStateOf<String?>(null) }

    val screen = navigator.current
    stateHolder.SaveableStateProvider(screen.name) {
        when (screen) {
            Screen.SETUP -> SetupScreen(
                controller = setupController,
                resumeCount = resumeCount,
                initialStep = setupInitialStep,
                onCompleted = { destination ->
                    setupInitialStep = 1
                    resetTo(destination.toScreen())
                },
            )

            Screen.IMPORTED_DATA -> ImportedDataScreen(
                onOpenSettings = { navigator.navigate(Screen.SETTINGS_MENU) },
                // 設定メニューから開いたとき（最初の画面でないとき）は、戻るボタンを出す
                onBack = if (navigator.root == Screen.IMPORTED_DATA) null else goBack,
            )

            Screen.HEALTH_CHECK -> HealthCheckRoute(settingsController, resumeCount, onBack = goBack)

            Screen.LICENSES -> LicenseRoute(settingsController, onBack = goBack)

            Screen.APP_STATUS -> AppStatusRoute(
                controller = statusController,
                onBack = goBack,
                onOpenDetail = { packageName ->
                    detailPackage = packageName
                    navigator.navigate(Screen.APP_DETAIL)
                },
                onPreRegister = { navigator.navigate(Screen.PRE_REGISTER) },
            )

            Screen.APP_DETAIL -> {
                val packageName = detailPackage
                if (packageName == null) {
                    LaunchedEffect(Unit) { goBack() }
                } else {
                    AdminAppDetailRoute(
                        controller = statusController,
                        packageName = packageName,
                        onBack = goBack,
                        onUninstall = { entry ->
                            if (entry.isSelf) {
                                selfDeleteTarget = entry.packageName
                            } else {
                                onOpenSystemScreen()
                                AppActions.uninstall(context, entry.packageName)
                            }
                        },
                        onOpenAppInfo = { entry ->
                            onOpenSystemScreen()
                            AppActions.openAppInfo(context, entry.packageName)
                        },
                    )
                }
            }

            Screen.PRE_REGISTER -> PlaceholderScreen(stringResource(R.string.settings_pre_register), onBack = goBack)

            Screen.HISTORY -> PlaceholderScreen(stringResource(R.string.settings_history), onBack = goBack)

            Screen.EXPORT_IMPORT -> PlaceholderScreen(stringResource(R.string.settings_export_import), onBack = goBack)

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

            Screen.SETTINGS_MENU -> SettingsMenuRoute(
                controller = settingsController,
                resumeCount = resumeCount,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onNavigate = { destination -> navigator.navigate(destination.toScreen()) },
                onSwitchToMonitor = { setupInitialStep = 2 },
                onBack = goBack,
            )
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
