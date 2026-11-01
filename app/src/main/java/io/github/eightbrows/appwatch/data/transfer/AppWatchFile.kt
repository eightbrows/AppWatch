package io.github.eightbrows.appwatch.data.transfer

import io.github.eightbrows.appwatch.data.db.AppCurrentEntity
import io.github.eightbrows.appwatch.data.db.AppEventEntity
import io.github.eightbrows.appwatch.data.db.AppPolicyEntity
import io.github.eightbrows.appwatch.data.db.BaselineAppEntity
import io.github.eightbrows.appwatch.data.db.ScanDailyEntity
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.model.ReviewStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// 書き出しファイル（.appwatch、仕様書 7章）の中身の形。Android を使わない。

/** 書き出した端末の情報。[id] は端末の識別番号（書き出した端末の「この端末のデータ」の番号）。 */
data class DeviceInfo(
    val id: String,
    val model: String,
    val osVersion: String,
    val sdkInt: Int,
    val appVersion: String,
)

/** ファイルに入れる設定（言語・初回設定の状態・使い方は入れない）。 */
data class TransferSettings(
    val checkIntervalMinutes: Int,
    val themeMode: ThemeMode,
)

/**
 * 1つのデータの記録（5つの表の行）。各行の datasetId は、ファイルから読んだときは空で、
 * データベースに入れるときに [withDataset] で入れる先の番号に書き換える。
 */
data class DatasetContent(
    val baseline: List<BaselineAppEntity>,
    val current: List<AppCurrentEntity>,
    /** 古い順（日時、記録した順）。データベースに入れるときに番号を振り直す */
    val events: List<AppEventEntity>,
    val policies: List<AppPolicyEntity>,
    val scanDays: List<ScanDailyEntity>,
) {
    fun withDataset(datasetId: String) = DatasetContent(
        baseline = baseline.map { it.copy(datasetId = datasetId) },
        current = current.map { it.copy(datasetId = datasetId) },
        events = events.map { it.copy(datasetId = datasetId, id = 0) },
        policies = policies.map { it.copy(datasetId = datasetId) },
        scanDays = scanDays.map { it.copy(datasetId = datasetId) },
    )

    /** 端末にあるアプリの数（取り込みの前に見せる「アプリ n件」）。 */
    val presentAppCount: Int get() = current.count { it.isPresent }

    /** 端末にあるアプリのうち「要注意」の数（設定メニューの要注意の件数と同じ数え方）。 */
    val cautionCount: Int
        get() {
            val present = current.filter { it.isPresent }.mapTo(HashSet()) { it.packageName }
            return policies.count { it.reviewStatus == ReviewStatus.CAUTION && it.packageName in present }
        }

    companion object {
        val EMPTY = DatasetContent(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** 書き出しファイルの中身。 */
data class AppWatchFile(
    val formatVersion: Int,
    val exportedAt: Long,
    val device: DeviceInfo,
    val settings: TransferSettings,
    val content: DatasetContent,
) {
    companion object {
        /** 形式の名前（ファイルの先頭の "format"）。 */
        const val FORMAT_NAME = "appwatch"

        /** このアプリが書き出す形式の版。形式を変えたら1つ増やし、古い版の変換を FileMigrations に足す。 */
        const val CURRENT_VERSION = 1

        const val EXTENSION = "appwatch"

        /** 保存の画面で使う種類。.appwatch に決まった種類はないので、中身（gzip）に合わせる。 */
        const val MIME_TYPE = "application/octet-stream"

        private val FILE_NAME_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

        /** ファイル名の初期値 `appwatch_YYYYMMDD_HHMM.appwatch`（端末の時間帯の日時）。 */
        fun fileName(time: LocalDateTime): String = "appwatch_${time.format(FILE_NAME_TIME)}.$EXTENSION"
    }
}

/** ファイルを読んだ結果。 */
sealed interface ReadResult {
    data class Success(val file: AppWatchFile) : ReadResult

    /** 壊れている、または AppWatch のファイルでない */
    data object NotAppWatch : ReadResult

    /** このアプリより新しい版で書き出されたファイル（アプリの更新が必要） */
    data class TooNew(val version: Int) : ReadResult
}

/**
 * 古い版のファイルを、今の版の形に直す（仕様書 7章「古い形式は変換して取り込む」）。
 * 今は版 1 だけなので、変換はない。版を増やしたら、ここに「版 n → n+1」の変換を足していく。
 */
object FileMigrations {
    fun toCurrent(file: AppWatchFile): AppWatchFile {
        var upgraded = file
        while (upgraded.formatVersion < AppWatchFile.CURRENT_VERSION) {
            upgraded = when (upgraded.formatVersion) {
                // 例：1 -> upgradeFrom1(upgraded)
                else -> throw InvalidFileException("変換できない版です: ${upgraded.formatVersion}")
            }
        }
        return upgraded
    }
}

/** ファイルの中身が正しくない（必要な項目がない、知らない値がある、など）。 */
class InvalidFileException(message: String) : Exception(message)
