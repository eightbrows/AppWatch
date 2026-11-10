package io.github.eightbrows.appwatch.ui.status

import android.content.Context
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.data.record.AppDetail
import io.github.eightbrows.appwatch.data.record.PolicyEdit
import io.github.eightbrows.appwatch.data.status.AppStatusEntry
import io.github.eightbrows.appwatch.data.status.AppStatusRules
import io.github.eightbrows.appwatch.data.status.FilterChip
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.data.transfer.DataSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    /** 注意アプリとして事前登録した */
    data class PreRegistered(val name: String, override val operationId: String) : StatusNotice

    /** 事前登録を解除した */
    data class Unregistered(val name: String, override val operationId: String) : StatusNotice

    /** 元に戻した */
    data object Undone : StatusNotice {
        override val operationId: String? get() = null
    }
}

/** アプリの確認状態の画面とアプリの詳細（管理者）で使う値と処理。画面の部品から切り離してある。 */
class AppStatusController(context: Context) {

    private val app = context.applicationContext as AppWatchApplication
    private val repository = app.recordRepository

    /** 選択中のデータ（この端末／取り込んだデータ）。確認状態・履歴と比較・アプリの詳細で共通。 */
    val selection: Flow<DataSelection> = app.dataSelector.selection

    fun selectDataset(datasetId: String) {
        app.appScope.launch { app.dataSelector.select(datasetId) }
    }

    /** 確認状態の画面の行（選択中のデータ）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: Flow<List<AppStatusEntry>> = selection.flatMapLatest { sel ->
        sel.selected?.let { repository.observeStatusEntries(it.id) } ?: flowOf(emptyList())
    }

    /** この端末のデータの行（事前登録の画面で、登録済み・端末にあるかを調べる）。 */
    val localEntries: Flow<List<AppStatusEntry>> = repository.observeStatusEntries()

    val filterEnabled: Flow<Set<FilterChip>> = app.settingsRepository.statusFilterEnabled

    fun setFilterEnabled(enabled: Set<FilterChip>) {
        app.appScope.launch { app.settingsRepository.setStatusFilterEnabled(enabled) }
    }

    /** アプリの詳細（管理者用、選択中のデータ）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun detail(packageName: String): Flow<AppDetail> = selection.flatMapLatest { sel ->
        sel.selected?.let { repository.observeAppDetail(it.id, packageName) } ?: flowOf(AppDetail(entry = null, events = emptyList()))
    }

    /** アプリの詳細（本人用）。いつもこの端末のデータ。 */
    fun localDetail(packageName: String): Flow<AppDetail> = repository.observeAppDetail(packageName)

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

    /**
     * 注意アプリの事前登録（仕様書 6章）。1つの操作として記録し、「元に戻す」付きのお知らせを返す。
     * @param displayName お知らせに出す名前（仮のアプリ名、端末上の名前、パッケージ名のどれか）
     */
    suspend fun preRegister(packageName: String, provisionalName: String, memo: String, displayName: String): StatusNotice =
        persist {
            val result = repository.preRegister(packageName, provisionalName, memo)
            StatusNotice.PreRegistered(displayName, result.operationId)
        }

    /** 事前登録の解除（管理者用の詳細）。前の画面に戻ってから「元に戻す」付きのお知らせを出す。 */
    fun unregister(entry: AppStatusEntry) {
        app.appScope.launch {
            val result = repository.unregister(entry.packageName)
            if (result.changedApps > 0) {
                _pendingNotice.value = StatusNotice.Unregistered(entry.name, result.operationId)
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
