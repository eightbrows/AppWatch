package io.github.eightbrows.appwatch.ui.status

import android.content.Context
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.data.record.AppDetail
import io.github.eightbrows.appwatch.data.record.PolicyEdit
import io.github.eightbrows.appwatch.data.status.AppStatusEntry
import io.github.eightbrows.appwatch.data.status.AppStatusRules
import io.github.eightbrows.appwatch.data.status.FilterChip
import io.github.eightbrows.appwatch.model.ReviewStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable

/** 確認状態を変えた後に出すお知らせ。[operationId] があれば「元に戻す」を付ける。 */
sealed interface StatusNotice {
    val operationId: String?

    /** まとめて「問題なし」。[skipped] は警告があるので変えなかった件数 */
    data class BulkOk(val changed: Int, val skipped: Int, override val operationId: String?) : StatusNotice

    /** 選んだアプリがすべて警告ありで、何も変えなかった */
    data class AllWarned(val count: Int) : StatusNotice {
        override val operationId: String? get() = null
    }

    /** まとめて「要注意」「未確認」 */
    data class Bulk(val status: ReviewStatus, val changed: Int, override val operationId: String) : StatusNotice

    /** 変えるものがなかった */
    data object NoChange : StatusNotice {
        override val operationId: String? get() = null
    }

    /** アプリの詳細で保存した。[status] は確認状態を変えたときだけ */
    data class DetailSaved(
        val name: String,
        val status: ReviewStatus?,
        val memoChanged: Boolean,
        override val operationId: String,
    ) : StatusNotice

    /** 元に戻した */
    data object Undone : StatusNotice {
        override val operationId: String? get() = null
    }
}

/** アプリの確認状態の画面とアプリの詳細（管理者）で使う値と処理。画面の部品から切り離してある。 */
class AppStatusController(context: Context) {

    private val app = context.applicationContext as AppWatchApplication
    private val repository = app.recordRepository

    val entries: Flow<List<AppStatusEntry>> = repository.observeStatusEntries()

    val filterEnabled: Flow<Set<FilterChip>> = app.settingsRepository.statusFilterEnabled

    fun setFilterEnabled(enabled: Set<FilterChip>) {
        app.appScope.launch { app.settingsRepository.setStatusFilterEnabled(enabled) }
    }

    fun detail(packageName: String): Flow<AppDetail> = repository.observeAppDetail(packageName)

    private val _pendingNotice = MutableStateFlow<StatusNotice?>(null)

    /** アプリの詳細で保存した後、確認状態の画面に戻ってから出すお知らせ。 */
    val pendingNotice: StateFlow<StatusNotice?> = _pendingNotice.asStateFlow()

    fun consumePendingNotice() {
        _pendingNotice.value = null
    }

    /** 選んだアプリの確認状態をまとめて変える。「問題なし」では警告のあるアプリを除く（仕様書 6章）。 */
    suspend fun applyBulk(status: ReviewStatus, selected: List<AppStatusEntry>): StatusNotice = persist {
        if (status == ReviewStatus.OK) {
            val plan = AppStatusRules.bulkOkPlan(selected)
            when {
                plan.targets.isEmpty() && plan.skippedWithWarnings > 0 -> StatusNotice.AllWarned(plan.skippedWithWarnings)
                plan.targets.isEmpty() -> StatusNotice.NoChange
                else -> {
                    val result = repository.applyOperation(plan.targets.map { PolicyEdit(it, status = status) })
                    StatusNotice.BulkOk(result.changedApps, plan.skippedWithWarnings, result.operationId)
                }
            }
        } else {
            val targets = selected.filter { it.status != status }.map { PolicyEdit(it.packageName, status = status) }
            if (targets.isEmpty()) {
                StatusNotice.NoChange
            } else {
                val result = repository.applyOperation(targets)
                StatusNotice.Bulk(status, result.changedApps, result.operationId)
            }
        }
    }

    /**
     * アプリの詳細の「保存」。画面を閉じても保存が途中で止まらないよう、アプリ全体の処理で行い、
     * 終わったら確認状態の画面に出すお知らせを置く。
     */
    fun saveDetail(entry: AppStatusEntry, status: ReviewStatus, memo: String) {
        app.appScope.launch {
            val newMemo = memo.trim().takeIf { it.isNotEmpty() }
            val memoChanged = newMemo != entry.adminMemo
            val result = repository.applyOperation(
                listOf(PolicyEdit(entry.packageName, status = status, memo = newMemo, setsMemo = memoChanged)),
            )
            if (result.changedApps > 0) {
                _pendingNotice.value = StatusNotice.DetailSaved(
                    name = entry.name,
                    status = status.takeIf {
                        it != entry.status || (it == ReviewStatus.OK && entry.hasRecordedWarnings)
                    },
                    memoChanged = memoChanged,
                    operationId = result.operationId,
                )
                app.refreshWidget()
            }
        }
    }

    /** 「元に戻す」。 */
    suspend fun undo(operationId: String): StatusNotice = persist {
        repository.undoOperation(operationId)
        StatusNotice.Undone
    }

    /** 画面を離れても書き込みが途中で止まらないようにし、終わったらウィジェットを更新する。 */
    private suspend fun persist(block: suspend () -> StatusNotice): StatusNotice =
        withContext(NonCancellable) { block() }.also { app.refreshWidget() }
}

/** 「以前に削除したアプリ」「署名が変わった」の警告が記録されているか（「問題なし」にすると消える）。 */
val AppStatusEntry.hasRecordedWarnings: Boolean get() = reinstalledAt != null || signatureChangedAt != null
