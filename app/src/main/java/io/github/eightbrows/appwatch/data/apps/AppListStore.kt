package io.github.eightbrows.appwatch.data.apps

import io.github.eightbrows.appwatch.model.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** アプリ一覧の状態。 */
data class AppListState(
    /** 判定済みのアプリ。まだ一度も取得できていなければ null。 */
    val apps: List<InstalledApp>? = null,
    /** 取得中かどうか（取り直し中は、前回の一覧を出したまま true になる）。 */
    val isLoading: Boolean = false,
    /** 最後に取得できた時刻（エポックミリ秒）。 */
    val loadedAt: Long? = null,
    /** 直前の取得に失敗したか。 */
    val failed: Boolean = false,
)

/**
 * アプリ一覧を裏で取得して持つ。アプリ全体で1つ（AppWatchApplication が持つ）なので、
 * 画面の回転や言語の切り替えで Activity が作り直されても、一覧は消えない。
 */
class AppListStore(
    private val scanner: () -> List<PackageSnapshot>,
    private val selfPackageName: String,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** 取得できたときに呼ばれる（開発版で件数をログに出すのに使う）。 */
    private val onLoaded: (List<InstalledApp>) -> Unit = {},
) {
    private val _state = MutableStateFlow(AppListState())
    val state: StateFlow<AppListState> = _state.asStateFlow()

    private var job: Job? = null

    /** 一覧を取り直す。取得中に呼ばれたら何もしない。 */
    fun refresh() {
        if (job?.isActive == true) return
        _state.update { it.copy(isLoading = true) }
        job = scope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    scanner().map { AppClassifier.classify(it, selfPackageName) }
                }
                _state.value = AppListState(apps = apps, isLoading = false, loadedAt = clock(), failed = false)
                onLoaded(apps)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, failed = true) }
            }
        }
    }
}
