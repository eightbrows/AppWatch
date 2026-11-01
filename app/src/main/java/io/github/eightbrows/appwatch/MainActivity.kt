package io.github.eightbrows.appwatch

import android.content.Intent
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.eightbrows.appwatch.data.settings.SetupState
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.data.settings.isDark
import io.github.eightbrows.appwatch.ui.AppWatchApp
import io.github.eightbrows.appwatch.ui.TopData
import io.github.eightbrows.appwatch.ui.settings.SettingsController
import io.github.eightbrows.appwatch.ui.setup.SetupController
import io.github.eightbrows.appwatch.ui.status.AppStatusController
import io.github.eightbrows.appwatch.ui.status.SharedText
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 言語の切り替え（AppCompatDelegate.setApplicationLocales）を Android 12 以下でも効かせるため、
 * AppCompatActivity を使う。
 */
class MainActivity : AppCompatActivity() {

    private val app: AppWatchApplication get() = application as AppWatchApplication

    private val setupController by lazy { SetupController(this) }
    private val settingsController by lazy { SettingsController(this) }
    private val statusController by lazy { AppStatusController(this) }

    /** アプリに戻ってくるたびに増える数（初回設定で、設定画面から戻ったときに状態を読み直すのに使う）。 */
    private val resumeCount = MutableStateFlow(0)

    /** 共有で受け取った文面（注意アプリの事前登録の画面に入れる）。入れ終わったら null に戻す。 */
    private val sharedText = MutableStateFlow<SharedText?>(null)

    /**
     * 共有（ACTION_SEND）で開いたか。Google Play から共有すると、共有元のタスクの上にこの画面が開くので、
     * 事前登録の画面だけを最初の画面にし、戻ると共有元（Google Play）に戻る。
     */
    private val startedFromShare: Boolean by lazy { intent?.action == Intent.ACTION_SEND }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receiveShare(intent)
        setContent {
            // 保存済みのテーマと初回設定の状態を読み終えるまでは何も描かない
            // （一瞬違う色や違う画面が出るのを防ぐ）
            val themeMode: ThemeMode? by app.settingsRepository.themeMode.collectAsState(initial = null)
            val setupState: SetupState? by app.settingsRepository.setupState.collectAsState(initial = null)
            val mode = themeMode
            val setup = setupState
            if (mode != null && setup != null) ThemedContent(mode, setup)
        }
    }

    /** singleTop で、開いているこの画面に共有が届いたとき（同じ画面を重ねない）。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShare(intent)
    }

    private fun receiveShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/") != true) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        sharedText.value = SharedText(text = text, subject = intent.getStringExtra(Intent.EXTRA_SUBJECT), id = System.nanoTime())
    }

    /**
     * アプリを開いたとき・戻ってきたときに1回チェックする（仕様書 5章）。
     * 直前1分以内にチェック済みなら省略される（削除・アプリ情報の画面から戻ったときは省略しない）。
     * 初回設定が終わるまでと閲覧専用では、チェックしない（CheckRunner が判断する）。
     */
    override fun onResume() {
        super.onResume()
        resumeCount.value++
        app.checkRunner.request()
    }

    @Composable
    private fun ThemedContent(mode: ThemeMode, setup: SetupState) {
        val darkTheme = mode.isDark(systemIsDark = isSystemInDarkTheme())
        val scope = rememberCoroutineScope()
        // データベースの内容をすぐに出し、チェックが終わってデータベースが変わったら自動で更新される
        val apps by remember { app.recordRepository.observePresentApps() }.collectAsState(initial = null)
        val lastCheckAt by remember { app.recordRepository.observeLastCheckAt() }.collectAsState(initial = null)
        val checkState by app.checkRunner.state.collectAsState()
        val resumes by resumeCount.collectAsState()
        val shared by sharedText.collectAsState()

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
                startDestination = setup.startDestination,
                setupController = setupController,
                settingsController = settingsController,
                statusController = statusController,
                resumeCount = resumes,
                themeMode = mode,
                onThemeModeChange = { newMode -> scope.launch { app.settingsRepository.setThemeMode(newMode) } },
                top = TopData(apps = apps, lastCheckAt = lastCheckAt, check = checkState),
                onRetryCheck = { app.checkRunner.request() },
                onOpenSystemScreen = app.checkRunner::forceNextRequest,
                startedFromShare = startedFromShare,
                sharedText = shared,
                onSharedTextConsumed = { sharedText.value = null },
            )
        }
    }

    private companion object {
        // androidx.activity の既定と同じ半透明の色（ボタン式ナビゲーションの背景）
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
