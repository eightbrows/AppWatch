package io.github.eightbrows.appwatch.data.record

import io.github.eightbrows.appwatch.model.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** チェックの結果を保存する側（データベース）。テストでは偽物に差し替える。 */
interface CheckRecorder {
    /** 最後にチェックした時刻。まだ一度もなければ null。 */
    suspend fun lastCheckAt(): Long?

    /** 今回取得したアプリを記録する（最初なら基準、2回目以降は変化）。 */
    suspend fun record(scanned: List<InstalledApp>, now: Long): RecordOutcome
}

/** 1回のチェックで記録した内容。 */
data class RecordOutcome(
    /** 最初のチェック（基準を作った）なら true */
    val isBaseline: Boolean,
    val appCount: Int,
    val changes: List<AppChange>,
)

/** チェックの状態（画面の表示用）。 */
data class CheckState(
    val isChecking: Boolean = false,
    /** 直前のチェックに失敗したか */
    val failed: Boolean = false,
)

/** 1回のチェックの結末。 */
sealed interface CheckOutcome {
    data class Recorded(val outcome: RecordOutcome) : CheckOutcome

    /** 直前1分以内にチェック済みなので省略した */
    data object SkippedRecent : CheckOutcome

    /** 別のチェックが動いていたので省略した */
    data object AlreadyRunning : CheckOutcome

    data class Failed(val error: Exception) : CheckOutcome
}

/**
 * チェックを動かす（仕様書 5章）。アプリ全体で1つだけ持つ。
 * - 同時に2回は動かない（動いている間に頼まれたら省略する）
 * - 直前1分以内にチェック済みなら省略する（[force] のときを除く）
 * - 一覧の取得は裏のスレッドで行う
 */
class CheckRunner(
    private val scope: CoroutineScope,
    private val scanner: () -> List<InstalledApp>,
    private val recorder: CheckRecorder,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    private val onRecorded: (RecordOutcome) -> Unit = {},
) {
    private val mutex = Mutex()
    private val forceNext = AtomicBoolean(false)
    private val _state = MutableStateFlow(CheckState())
    val state: StateFlow<CheckState> = _state.asStateFlow()

    /**
     * 次の [request] だけは1分以内でもチェックする。
     * アプリから削除やアプリ情報の画面を開いたとき、戻ってきてすぐに結果を反映するために使う。
     */
    fun forceNextRequest() {
        forceNext.set(true)
    }

    /** 裏でチェックを頼む（アプリを開いたとき・戻ってきたときなど）。 */
    fun request(): Job = scope.launch { check(force = forceNext.getAndSet(false)) }

    /** チェックを1回行い、終わるまで待つ。 */
    suspend fun check(force: Boolean = false): CheckOutcome {
        if (!mutex.tryLock()) return CheckOutcome.AlreadyRunning
        try {
            if (!force && isRecentlyChecked()) return CheckOutcome.SkippedRecent
            _state.update { it.copy(isChecking = true) }
            val scanned = withContext(ioDispatcher) { scanner() }
            val outcome = recorder.record(scanned, clock())
            _state.value = CheckState(isChecking = false, failed = false)
            onRecorded(outcome)
            return CheckOutcome.Recorded(outcome)
        } catch (e: CancellationException) {
            _state.update { it.copy(isChecking = false) }
            throw e
        } catch (e: Exception) {
            _state.value = CheckState(isChecking = false, failed = true)
            return CheckOutcome.Failed(e)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun isRecentlyChecked(): Boolean {
        val last = recorder.lastCheckAt() ?: return false
        val elapsed = clock() - last
        // 時計が戻された（elapsed が負）ときは、直前とはみなさない
        return elapsed in 0 until minIntervalMillis
    }

    companion object {
        const val MIN_INTERVAL_MILLIS: Long = 60_000
    }
}
