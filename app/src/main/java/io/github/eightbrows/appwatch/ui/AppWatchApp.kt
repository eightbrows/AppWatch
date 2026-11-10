package io.github.eightbrows.appwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.record.CheckState
import io.github.eightbrows.appwatch.data.settings.StartDestination
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.ui.navigation.Screen
import io.github.eightbrows.appwatch.ui.navigation.rememberNavigator
import io.github.eightbrows.appwatch.ui.history.HistoryController
import io.github.eightbrows.appwatch.ui.history.HistoryRoute
import io.github.eightbrows.appwatch.ui.imported.ImportedDataRoute
import io.github.eightbrows.appwatch.ui.transfer.ExportImportRoute
import io.github.eightbrows.appwatch.ui.transfer.TransferController
import io.github.eightbrows.appwatch.ui.settings.HealthCheckRoute
import io.github.eightbrows.appwatch.ui.settings.LicenseRoute
import io.github.eightbrows.appwatch.ui.settings.SettingsController
import io.github.eightbrows.appwatch.ui.settings.SettingsDestination
import io.github.eightbrows.appwatch.ui.settings.SettingsMenuRoute
import io.github.eightbrows.appwatch.ui.setup.SetupController
import io.github.eightbrows.appwatch.ui.setup.SetupScreen
import io.github.eightbrows.appwatch.ui.status.AppDetailRoute
import io.github.eightbrows.appwatch.ui.status.DetailViewer
import io.github.eightbrows.appwatch.ui.status.AppStatusController
import io.github.eightbrows.appwatch.ui.status.AppStatusRoute
import io.github.eightbrows.appwatch.ui.status.PreRegisterRoute
import io.github.eightbrows.appwatch.ui.status.SharedText
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.data.status.AppStatusEntry
import io.github.eightbrows.appwatch.data.system.UninstallEvent
import io.github.eightbrows.appwatch.data.system.UninstallResult
import io.github.eightbrows.appwatch.data.system.Uninstaller
import io.github.eightbrows.appwatch.ui.components.DeleteConfirmDialog
import io.github.eightbrows.appwatch.ui.components.DeleteResultDialog
import io.github.eightbrows.appwatch.ui.components.DeleteTarget
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
    historyController: HistoryController,
    transferController: TransferController,
    /** アプリに戻ってくるたびに増える数（初回設定で、設定画面から戻ったときに状態を読み直す） */
    resumeCount: Int,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    top: TopData,
    onRetryCheck: () -> Unit,
    /** Android の削除・アプリ情報の画面を開く直前に呼ぶ（戻ったときに必ずチェックし直すため） */
    onOpenSystemScreen: () -> Unit,
    /** 共有で開いたか（このときは事前登録の画面を最初の画面にする） */
    startedFromShare: Boolean = false,
    /** 共有で受け取った文面。事前登録の画面に入れたら [onSharedTextConsumed] を呼ぶ */
    sharedText: SharedText? = null,
    onSharedTextConsumed: () -> Unit = {},
    /** ファイルを開いて（ACTION_VIEW）起動したか（このときは書き出し・取り込みの画面を最初の画面にする） */
    startedFromFile: Boolean = false,
    /** ファイルアプリなどから開いたファイル。取り込みの流れに入れたら [onOpenFileConsumed] を呼ぶ */
    openFileUri: android.net.Uri? = null,
    onOpenFileConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val navigator = rememberNavigator(
        start = when {
            startedFromShare -> Screen.PRE_REGISTER
            startedFromFile -> Screen.EXPORT_IMPORT
            else -> startDestination.toScreen()
        },
    )
    // 画面ごとの状態（選んでいるタブ、スクロール位置など）を、別の画面から戻ったときにも保つ
    val stateHolder = rememberSaveableStateHolder()
    // 最初の画面を切り替える。それまでの画面の状態（初回設定の途中の画面など）は捨てる
    val resetTo: (Screen) -> Unit = { target ->
        navigator.snapshot().forEach { stateHolder.removeState(it.name) }
        navigator.resetTo(target)
    }
    // 初回設定の完了・開発版のやり直しで行き先が変わったら、最初の画面を切り替える
    LaunchedEffect(startDestination) {
        // 共有・ファイルで開いた画面は、事前登録・取り込みだけのための画面なので切り替えない
        if (!startedFromShare && !startedFromFile && navigator.root != startDestination.toScreen()) resetTo(startDestination.toScreen())
    }
    val goBack: () -> Unit = {
        val leaving = navigator.current
        if (navigator.back()) stateHolder.removeState(leaving.name)
    }
    BackHandler(enabled = navigator.canGoBack, onBack = goBack)
    // この画面（Activity）を閉じる。共有で開いた事前登録の画面から戻るときに使い、共有元のアプリに戻る
    val onFinish: () -> Unit = {
        var current: android.content.Context? = context
        while (current is android.content.ContextWrapper && current !is android.app.Activity) current = current.baseContext
        (current as? android.app.Activity)?.finish()
    }
    /** 1つ前の画面に戻る。最初の画面で戻れなければ false。 */
    val goBackOrFalse: () -> Boolean = {
        val leaving = navigator.current
        navigator.back().also { moved -> if (moved) stateHolder.removeState(leaving.name) }
    }
    // 共有を受け取ったら、事前登録の画面を開く（開いているこの画面の上に重ねず、同じ画面なら入れ直すだけ）
    LaunchedEffect(sharedText) {
        if (sharedText != null && navigator.current != Screen.PRE_REGISTER) navigator.navigate(Screen.PRE_REGISTER)
    }

    // ファイルを開いたら、書き出し・取り込みの画面で取り込みの流れに入る
    LaunchedEffect(openFileUri) {
        if (openFileUri != null && navigator.current != Screen.EXPORT_IMPORT) navigator.navigate(Screen.EXPORT_IMPORT)
    }
    // 「閲覧のみ」で取り込んだら、取り込んだデータの画面へ（すでに開いていればそこまで戻る）
    val onImportedForViewing: (io.github.eightbrows.appwatch.data.transfer.ImportOutcome) -> Unit = {
        navigator.popTo(Screen.IMPORTED_DATA).forEach { stateHolder.removeState(it.name) }
    }
    // 復元したら、最初の画面（トップ）に戻る
    val onRestored: () -> Unit = {
        android.widget.Toast.makeText(context, R.string.restore_done, android.widget.Toast.LENGTH_LONG).show()
        resetTo(startDestination.toScreen())
    }

    // 削除の確認を出しているアプリ（画面の回転でも消えないように保存する）
    var deleteTarget by rememberSaveable(stateSaver = DeleteTarget.Saver) { mutableStateOf<DeleteTarget?>(null) }
    // 削除の結果（アプリ名と、削除されたか）
    var deleteResultName by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteResultDeleted by rememberSaveable { mutableStateOf(false) }
    // 削除の流れ：Android の確認の画面を開く・結果を表示する
    LaunchedEffect(Unit) {
        Uninstaller.eventFlow.collect { event ->
            when (event) {
                is UninstallEvent.Confirm -> {
                    onOpenSystemScreen()
                    AppActions.startConfirm(context, event.intent)
                }
                is UninstallEvent.Finished -> {
                    deleteResultName = event.appName
                    deleteResultDeleted = event.result == UninstallResult.DELETED
                }
            }
        }
    }

    // アプリの詳細（管理者）で開いているアプリのパッケージ名
    var detailPackage by rememberSaveable { mutableStateOf<String?>(null) }
    // アプリの詳細（本人）で開いているアプリのパッケージ名
    var userDetailPackage by rememberSaveable { mutableStateOf<String?>(null) }

    // 削除するときは、まず本アプリの確認のダイアログを出す（その後に Android の確認）
    val requestDelete: (AppStatusEntry) -> Unit = { entry ->
        deleteTarget = DeleteTarget(entry.packageName, entry.name, entry.versionName, entry.warnings, entry.isSelf)
    }
    val openAppInfo: (AppStatusEntry) -> Unit = { entry ->
        onOpenSystemScreen()
        AppActions.openAppInfo(context, entry.packageName)
    }

    val screen = navigator.current
    stateHolder.SaveableStateProvider(screen.name) {
        when (screen) {
            Screen.SETUP -> SetupScreen(
                controller = setupController,
                resumeCount = resumeCount,
                onCompleted = { resetTo(Screen.TOP) },
            )

            Screen.IMPORTED_DATA -> ImportedDataRoute(
                controller = transferController,
                onBack = goBack,
                onOpenStatus = { navigator.navigate(Screen.APP_STATUS) },
                onOpenHistory = { navigator.navigate(Screen.HISTORY) },
                onImportedForViewing = onImportedForViewing,
                onRestored = onRestored,
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
                    AppDetailRoute(
                        controller = statusController,
                        packageName = packageName,
                        viewer = DetailViewer.HELPER,
                        onBack = goBack,
                        onDelete = requestDelete,
                        onOpenAppInfo = openAppInfo,
                    )
                }
            }

            Screen.PRE_REGISTER -> PreRegisterRoute(
                controller = statusController,
                sharedText = sharedText,
                onSharedTextConsumed = onSharedTextConsumed,
                // 共有で開いたとき（最初の画面）は、戻ると画面を閉じて共有元に戻る
                onBack = { if (!goBackOrFalse()) onFinish() },
                onOpenDetail = { packageName ->
                    detailPackage = packageName
                    navigator.navigate(Screen.APP_DETAIL)
                },
            )

            Screen.USER_DETAIL -> {
                val packageName = userDetailPackage
                if (packageName == null) {
                    LaunchedEffect(Unit) { goBack() }
                } else {
                    // トップから開くアプリの詳細：管理者用と同じ形で、見るだけ
                    AppDetailRoute(
                        controller = statusController,
                        packageName = packageName,
                        viewer = DetailViewer.PERSON,
                        onBack = goBack,
                        onDelete = requestDelete,
                        onOpenAppInfo = openAppInfo,
                    )
                }
            }

            Screen.HISTORY -> HistoryRoute(
                controller = historyController,
                statusController = statusController,
                onBack = goBack,
                onOpenDetail = { packageName ->
                    detailPackage = packageName
                    navigator.navigate(Screen.APP_DETAIL)
                },
            )

            Screen.EXPORT_IMPORT -> ExportImportRoute(
                controller = transferController,
                openUri = openFileUri,
                onOpenUriConsumed = onOpenFileConsumed,
                // ファイルを開いて起動したとき（最初の画面）は、戻ると画面を閉じて開いたアプリに戻る
                onBack = { if (!goBackOrFalse()) onFinish() },
                onImportedForViewing = onImportedForViewing,
                onRestored = onRestored,
            )

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
                        onDelete = { app ->
                            deleteTarget = DeleteTarget(app.packageName, app.name, app.versionName, app.warnings, app.isSelf)
                        },
                        onOpenAppInfo = { app ->
                            onOpenSystemScreen()
                            AppActions.openAppInfo(context, app.packageName)
                        },
                        onShowDetails = { app ->
                            userDetailPackage = app.packageName
                            navigator.navigate(Screen.USER_DETAIL)
                        },
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
                onBack = goBack,
            )
        }
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            target = target,
            onCancel = { deleteTarget = null },
            onConfirmDelete = {
                deleteTarget = null
                // 結果を受け取れる方法で削除を頼む。頼めなかったら、今までどおり Android の削除画面を開く（結果は出ない）
                if (!Uninstaller.start(context, target.packageName, target.name)) {
                    onOpenSystemScreen()
                    AppActions.uninstall(context, target.packageName)
                }
            },
        )
    }

    deleteResultName?.let { name ->
        DeleteResultDialog(
            appName = name,
            result = if (deleteResultDeleted) UninstallResult.DELETED else UninstallResult.NOT_DELETED,
            onDismiss = { deleteResultName = null },
        )
    }
}
