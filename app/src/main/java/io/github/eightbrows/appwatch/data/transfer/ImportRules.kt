package io.github.eightbrows.appwatch.data.transfer

import io.github.eightbrows.appwatch.data.db.ScanDailyEntity
import io.github.eightbrows.appwatch.data.db.toBaselineEntity
import io.github.eightbrows.appwatch.data.db.toCurrentEntity
import io.github.eightbrows.appwatch.data.db.toEntity
import io.github.eightbrows.appwatch.data.record.AppChange
import io.github.eightbrows.appwatch.data.record.ChangeDetector
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.InstalledApp
import io.github.eightbrows.appwatch.model.UsageMode
import java.time.Instant
import java.time.ZoneId

// 取り込み・復元・データの切り替えの決まり（仕様書 7章）。Android を使わない。

/** 取り込んだデータ1件（dataset の種類＝取り込み）。 */
data class ImportedDataset(
    val id: String,
    /** 書き出した端末の識別番号 */
    val deviceId: String,
    val name: String,
    val deviceModel: String,
    val osVersion: String,
    val exportedAt: Long,
    /** 同じ端末のデータを置き換えたときの、前のデータの書き出し日時 */
    val previousExportedAt: Long?,
    val importedAt: Long,
    /** 端末にあるアプリのうち「要注意」の数 */
    val cautionCount: Int,
)

/** 復元の「はい」で、今の端末のアプリを新しい基準にした中身。 */
data class ResetPlan(
    val content: DatasetContent,
    /** 新しく見つかったプリインストールとシステムのアプリ（ファイルの最新の状態になく、確認状態もないもの）。まとめて「問題なし」にするか尋ねる */
    val newlyFound: List<String>,
)

object ImportRules {

    /** この端末自身のファイルか（ファイルの識別番号が、この端末のデータの番号と同じ）。 */
    fun isOwnFile(file: AppWatchFile, localDeviceId: String): Boolean = file.device.id == localDeviceId

    /** 同じ端末（識別番号が同じ）の取り込んだデータ。なければ null。 */
    fun sameDevice(file: AppWatchFile, imported: List<ImportedDataset>): ImportedDataset? =
        imported.firstOrNull { it.deviceId == file.device.id }

    /** 端末の名前の初期値：同じ端末の取り込んだデータがあればその名前、なければ機種名。 */
    fun defaultName(file: AppWatchFile, imported: List<ImportedDataset>): String =
        sameDevice(file, imported)?.name ?: file.device.model

    /** 「閲覧のみ」を選べるか。この端末自身のファイルは選べない。 */
    fun canViewOnly(file: AppWatchFile, localDeviceId: String): Boolean = !isOwnFile(file, localDeviceId)

    /** 「この端末に復元」を選べるか。閲覧専用の端末では選べない。 */
    fun canRestore(usageMode: UsageMode?): Boolean = usageMode != UsageMode.VIEW_ONLY

    /**
     * 復元の「はい」（端末を初期化・機種変更した）。確認状態・経緯・チェックの実行記録はファイルのものを引き継ぎ、
     * 今の端末のアプリ（[scanned]）を新しい基準と最新の状態にする。変化の記録に「初期化」を1件足し、
     * 取り直しも1回のチェックとして数える。
     */
    fun resetContent(file: AppWatchFile, scanned: List<InstalledApp>, now: Long, zone: ZoneId): ResetPlan {
        val tracked = ChangeDetector.baseline(scanned, now)
        val reset = AppChange(type = ChangeType.RESET, packageName = "", name = "", occurredAt = now).toEntity("")
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
        val todayRow = file.content.scanDays.firstOrNull { it.date == today }
        val scanDays = file.content.scanDays.filter { it.date != today } +
            ScanDailyEntity(datasetId = "", date = today, count = (todayRow?.count ?: 0) + 1, lastAt = now)
        val content = DatasetContent(
            baseline = tracked.map { it.toBaselineEntity("", capturedAt = now) },
            current = tracked.map { it.toCurrentEntity("") },
            events = file.content.events + reset,
            policies = file.content.policies,
            scanDays = scanDays.sortedBy { it.date },
        )
        // 新しく見つかった：ファイルの最新の状態（app_current）になく、確認状態もないもの
        val known = file.content.current.mapTo(HashSet()) { it.packageName } + file.content.policies.map { it.packageName }
        val newlyFound = scanned
            .filter { (it.kind == AppKind.PREINSTALLED || it.kind == AppKind.SYSTEM) && it.packageName !in known }
            .map { it.packageName }
            .sorted()
        return ResetPlan(content, newlyFound)
    }
}

/** データの切り替えの選択肢。 */
data class DatasetOption(
    val id: String,
    val name: String,
    val isLocal: Boolean,
    /** 取り込んだデータの書き出し日時（この端末のデータは null） */
    val exportedAt: Long? = null,
    val previousExportedAt: Long? = null,
)

/** 選択中のデータと、切り替えの選択肢。 */
data class DataSelection(
    /** 選択中のデータ。閲覧専用で取り込んだデータがなければ null */
    val selected: DatasetOption?,
    val options: List<DatasetOption>,
    val importedCount: Int,
) {
    /** 切り替えを出すか（取り込んだデータがあるときだけ）。 */
    val showsSwitcher: Boolean get() = importedCount > 0

    /** 取り込んだデータを見ているか（変更の操作を出さない）。 */
    val isViewingImported: Boolean get() = selected?.isLocal == false
}

object DatasetSelector {
    /**
     * 選択中のデータを決める。保存した番号のデータがなければ（削除されたなど）、
     * 見守る端末では「この端末」、閲覧専用では取り込んだデータの最初（なければ null）にする。
     * 閲覧専用の端末では「この端末」を選択肢に出さない。
     */
    fun resolve(
        savedId: String?,
        localId: String,
        localName: String,
        imported: List<ImportedDataset>,
        viewOnly: Boolean,
    ): DataSelection {
        val importedOptions = imported.map {
            DatasetOption(id = it.id, name = it.name, isLocal = false, exportedAt = it.exportedAt, previousExportedAt = it.previousExportedAt)
        }
        val options = if (viewOnly) importedOptions else listOf(DatasetOption(localId, localName, isLocal = true)) + importedOptions
        val selected = options.firstOrNull { it.id == savedId }
            ?: if (viewOnly) importedOptions.firstOrNull() else options.first()
        return DataSelection(selected = selected, options = options, importedCount = imported.size)
    }
}
