package io.github.eightbrows.appwatch.ui.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.data.settings.RecentExport
import io.github.eightbrows.appwatch.data.transfer.AppWatchFile
import io.github.eightbrows.appwatch.data.transfer.AppWatchFileCodec
import io.github.eightbrows.appwatch.data.transfer.ImportOutcome
import io.github.eightbrows.appwatch.data.transfer.ImportRules
import io.github.eightbrows.appwatch.data.transfer.ImportedDataset
import io.github.eightbrows.appwatch.data.transfer.ReadResult
import io.github.eightbrows.appwatch.data.transfer.RestoreOutcome
import io.github.eightbrows.appwatch.data.transfer.TransferSettings
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.model.UsageMode
import io.github.eightbrows.appwatch.work.MonitoringEffects
import io.github.eightbrows.appwatch.work.MonitoringPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FilterOutputStream
import java.io.OutputStream
import java.time.LocalDateTime

/** 書き出しの結果。 */
sealed interface ExportResult {
    data class Done(val fileName: String, val sizeBytes: Long) : ExportResult
    data object Failed : ExportResult
}

/** 取り込むファイルを読んだ結果。 */
sealed interface OpenResult {
    data class Ready(val candidate: ImportCandidate) : OpenResult
    data object NotAppWatch : OpenResult
    data class TooNew(val version: Int) : OpenResult
    data object CannotOpen : OpenResult
}

/** 取り込みの選択の画面に出す内容。 */
data class ImportCandidate(
    val file: AppWatchFile,
    /** 端末の名前の初期値（同じ端末の取り込んだデータがあればその名前、なければ機種名） */
    val defaultName: String,
    val isOwnFile: Boolean,
    val canViewOnly: Boolean,
    val canRestore: Boolean,
)

/** 書き出し・取り込みの後に、次の画面で出すお知らせ。 */
sealed interface TransferNotice {
    data class Imported(val name: String) : TransferNotice
    data class Deleted(val name: String) : TransferNotice
}

/** 書き出し・取り込み・取り込んだデータの画面で使う値と処理。画面の部品から切り離してある。 */
class TransferController(context: Context) {

    private val appContext = context.applicationContext
    private val app = appContext as AppWatchApplication
    private val settings = app.settingsRepository
    private val transfer = app.transferRepository

    val recentExports: Flow<List<RecentExport>> = settings.recentExports
    val imported: Flow<List<ImportedDataset>> = transfer.observeImported()
    val usageMode: Flow<UsageMode?> = settings.usageMode

    private val _notice = MutableStateFlow<TransferNotice?>(null)

    /** 取り込み・削除の後に、取り込んだデータの画面で出すお知らせ。 */
    val notice: StateFlow<TransferNotice?> = _notice.asStateFlow()

    fun consumeNotice() {
        _notice.value = null
    }

    /** 保存の画面のファイル名の初期値。 */
    fun defaultFileName(): String = AppWatchFile.fileName(LocalDateTime.now())

    // ---- 書き出し ----

    /**
     * この端末のデータを [uri] に書き出す。書き出しの前に一度チェックして、最新の状態を入れる（見守る端末のとき）。
     * 画面を閉じても途中で止まらないようにする。
     */
    suspend fun export(uri: Uri): ExportResult = withContext(NonCancellable + Dispatchers.IO) {
        try {
            if (settings.setupState.first().isMonitoring) app.checkRunner.check(force = true)
            val file = transfer.buildExport(
                TransferSettings(
                    checkIntervalMinutes = settings.checkIntervalMinutes.first(),
                    themeMode = settings.themeMode.first(),
                ),
            )
            val out = openForWrite(uri) ?: return@withContext ExportResult.Failed
            val counter = CountingOutputStream(out)
            AppWatchFileCodec.write(file, counter) // 書き終わると閉じる
            val name = displayName(uri) ?: defaultFileName()
            settings.addRecentExport(RecentExport(file.exportedAt, name, counter.count))
            ExportResult.Done(name, counter.count)
        } catch (e: Exception) {
            ExportResult.Failed
        }
    }

    /** 上書きで開く（"wt" に対応しない保存場所もあるので、そのときは "w"）。 */
    private fun openForWrite(uri: Uri): OutputStream? = try {
        appContext.contentResolver.openOutputStream(uri, "wt")
    } catch (e: IllegalArgumentException) {
        appContext.contentResolver.openOutputStream(uri, "w")
    } catch (e: UnsupportedOperationException) {
        appContext.contentResolver.openOutputStream(uri, "w")
    }

    private fun displayName(uri: Uri): String? = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    // ---- 取り込み ----

    /** [uri] のファイルを読んで、取り込みの選択に出す内容を作る。 */
    suspend fun open(uri: Uri): OpenResult = withContext(Dispatchers.IO) {
        val result = try {
            appContext.contentResolver.openInputStream(uri)?.use { AppWatchFileCodec.read(it) } ?: return@withContext OpenResult.CannotOpen
        } catch (e: Exception) {
            return@withContext OpenResult.CannotOpen
        }
        when (result) {
            is ReadResult.Success -> {
                val file = result.file
                val localId = app.recordRepository.localDatasetId()
                val imported = transfer.getImported()
                val usage = settings.usageMode.first()
                OpenResult.Ready(
                    ImportCandidate(
                        file = file,
                        defaultName = ImportRules.defaultName(file, imported),
                        isOwnFile = ImportRules.isOwnFile(file, localId),
                        canViewOnly = ImportRules.canViewOnly(file, localId),
                        canRestore = ImportRules.canRestore(usage),
                    ),
                )
            }
            ReadResult.NotAppWatch -> OpenResult.NotAppWatch
            is ReadResult.TooNew -> OpenResult.TooNew(result.version)
        }
    }

    /** 「閲覧のみ」で取り込む。終わったら、取り込んだデータの画面で出すお知らせを置く。 */
    suspend fun importForViewing(candidate: ImportCandidate, name: String): ImportOutcome = withContext(NonCancellable) {
        transfer.importForViewing(candidate.file, name).also { _notice.value = TransferNotice.Imported(it.name) }
    }

    /**
     * この端末に復元する（仕様書 7章）。チェックと同時には動かさない。
     * 終わったら、ファイルの設定（チェック間隔・テーマ）を戻し、定期チェックを登録し直して、ウィジェットを更新する。
     * @param reset 「はい、初期化しました」なら true（今の端末のアプリを新しい基準にする）
     */
    suspend fun restore(file: AppWatchFile, reset: Boolean): RestoreOutcome = withContext(NonCancellable) {
        val outcome = app.checkRunner.exclusive { scan -> transfer.restore(file, if (reset) scan() else null) }
        settings.restoreTransferSettings(file.settings.checkIntervalMinutes, file.settings.themeMode)
        val decision = MonitoringPolicy.decide(settings.setupState.first(), settings.checkIntervalMinutes.first())
        MonitoringEffects.apply(appContext, decision)
        app.refreshWidget()
        outcome
    }

    /** 復元の「はい」の後、新しく見つかったプリインストール・システムのアプリをまとめて「問題なし」にする（1つの操作）。 */
    suspend fun markOk(packageNames: List<String>): Int = withContext(NonCancellable) {
        app.recordRepository.setReviewStatus(packageNames, ReviewStatus.OK).also { app.refreshWidget() }
    }

    // ---- 取り込んだデータ ----

    fun rename(dataset: ImportedDataset, name: String) {
        app.appScope.launch { transfer.rename(dataset.id, name) }
    }

    fun delete(dataset: ImportedDataset) {
        app.appScope.launch {
            // 選んでいたデータなら、選択を外す（この端末、または閲覧専用では一覧の最初に戻る）
            if (settings.selectedDatasetId.first() == dataset.id) app.dataSelector.clear()
            transfer.deleteImported(dataset.id)
            _notice.value = TransferNotice.Deleted(dataset.name)
        }
    }

    /** そのデータを選んでから、確認状態・履歴と比較を開く。 */
    suspend fun select(dataset: ImportedDataset) = app.dataSelector.select(dataset.id)
}

/** 書いた大きさを数える。 */
private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    var count: Long = 0
        private set

    override fun write(b: Int) {
        out.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }
}
