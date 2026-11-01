package io.github.eightbrows.appwatch.data.history

import io.github.eightbrows.appwatch.data.record.AppChange
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.data.system.ScanDay
import io.github.eightbrows.appwatch.model.InstallSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 履歴と比較（仕様書 6章）の計算。Android を使わない純粋な関数だけを置く。
// 期間は日付で持ち、比較元はその日の 0:00、比較先はその日の終わりまでを含める（端末の時間帯で）。

/** 件数ボタンの種類。有効化は「無効化」に含める。初期化は件数ボタンに含めない。 */
enum class HistoryKind { ADDED, REMOVED, UPDATED, DISABLED, SIGNATURE }

/** すぐ選べる期間。「前回以降」は取り込んだデータを見ているときだけ出す（ステップ10で足す）。 */
enum class QuickRange { LAST_7_DAYS, LAST_30_DAYS, ALL }

/** 期間（両端の日を含む）。 */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "比較先が比較元より前です: $start → $end" }
    }
}

/** 履歴と比較の画面に出す内容。 */
data class HistoryResult(
    val range: DateRange,
    /** 期間内の件数（表示・非表示に関係なく数える）。件数が 0 の種類も入れる */
    val counts: Map<HistoryKind, Int>,
    /** 表示する行（新しい順）。初期化の行は件数ボタンに関係なく入れる */
    val rows: List<AppChange>,
    /** 期間内のチェックの回数 */
    val checkCount: Int,
    val hidden: Set<HistoryKind>,
    /** 期間内の初期化の数（件数ボタンには含めない） */
    val resetCount: Int = 0,
) {
    /** 期間内に記録された端末の変化の数（表示・非表示に関係なく。初期化も含む） */
    val totalChanges: Int get() = counts.values.sum() + resetCount
}

/** 変化がないときに出す案内の種類。 */
enum class HistoryEmpty {
    /** 期間内にチェックがない */
    NO_CHECKS,

    /** チェックはあったが変化がない */
    NO_CHANGES,

    /** 変化はあるが、件数ボタンで全部を非表示にした */
    ALL_HIDDEN,
}

/** 共有の文字に使う言葉（strings.xml から渡す。テストでは固定の文字を渡す）。 */
data class HistoryShareTexts(
    /** 1行目（例：「アプリ見守当番 履歴と比較」） */
    val title: String,
    /** 2行目（例：「期間：%1$s 〜 %2$s」） */
    val periodFormat: String,
    /** 期間の日付の形（例：「yyyy/MM/dd」） */
    val datePattern: String,
    /** 件数の1つ（例：「%1$s %2$d」） */
    val countFormat: String,
    /** 件数の区切り（例：「 ・ 」） */
    val countSeparator: String,
    /** 非表示の種類（例：「（%1$sは除く）」） */
    val excludedFormat: String,
    /** 非表示の種類が2つ以上のときの区切り（例：「・」） */
    val kindJoiner: String,
    /** 件数ボタンの名前（追加・削除・更新・無効化・署名） */
    val kindLabels: Map<HistoryKind, String>,
    /** 行の種類の名前（追加・削除・更新・有効化・無効化・署名が変わった・初期化） */
    val typeLabels: Map<ChangeType, String>,
    /** 行の種類の印（例：「[%1$s]」） */
    val markFormat: String,
    /** 再インストールの添え書き（例：「（再インストール）」） */
    val reinstallSuffix: String,
    /** バージョン（例：「v%1$s」） */
    val versionFormat: String,
    /** 更新のバージョン（例：「v%1$s → v%2$s」） */
    val updateVersionFormat: String,
    /** 削除のバージョン（例：「v%1$s（削除時）」） */
    val removedVersionFormat: String,
    /** パッケージ名（例：「(%1$s)」） */
    val packageFormat: String,
    /** 行の日時の形。期間が1年の中なら [rowTimePattern]、年をまたぐなら [rowTimeWithYearPattern] */
    val rowTimePattern: String,
    val rowTimeWithYearPattern: String,
    /** 入手元の名前 */
    val sourceLabels: Map<InstallSource, String>,
    /** 署名が変わった行の説明（例：「削除→再インストールの可能性」） */
    val signatureNote: String,
    /** 初期化の行の文（例：「記録を取り直しました」） */
    val resetText: String,
)

object HistoryRules {

    /** 変化の種類 → 件数ボタンの種類。端末の変化でないもの（確認状態など）と初期化は null。 */
    fun kindOf(type: ChangeType): HistoryKind? = when (type) {
        ChangeType.ADDED -> HistoryKind.ADDED
        ChangeType.REMOVED -> HistoryKind.REMOVED
        ChangeType.UPDATED -> HistoryKind.UPDATED
        ChangeType.ENABLED, ChangeType.DISABLED -> HistoryKind.DISABLED
        ChangeType.SIGNATURE_CHANGED -> HistoryKind.SIGNATURE
        ChangeType.RESET,
        ChangeType.STATUS_CHANGED,
        ChangeType.MEMO_CHANGED,
        ChangeType.PRE_REGISTERED,
        ChangeType.UNREGISTERED,
        -> null
    }

    /** 一覧に出す、端末の変化か（確認状態・管理者メモ・事前登録・その取り消しは出さない）。 */
    fun isDeviceChange(type: ChangeType): Boolean = type == ChangeType.RESET || kindOf(type) != null

    /** 端末の変化の種類（データベースから読むときの絞り込みに使う）。 */
    val deviceChangeTypes: List<ChangeType> = ChangeType.entries.filter(::isDeviceChange)

    // ---- 期間 ----

    /** 比較元の日の 0:00（含む）。 */
    fun startMillis(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 比較先の翌日の 0:00（含まない）。 */
    fun endExclusiveMillis(date: LocalDate, zone: ZoneId): Long = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun contains(range: DateRange, time: Long, zone: ZoneId): Boolean =
        time >= startMillis(range.start, zone) && time < endExclusiveMillis(range.end, zone)

    /** チェックの記録がある日（今日より後の日は除く）。 */
    fun checkedDates(scanDays: List<ScanDay>, today: LocalDate): Set<LocalDate> =
        scanDays.filter { it.count > 0 }.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .filterTo(HashSet()) { !it.isAfter(today) }

    /** 最初のチェックの日。まだチェックがなければ null。 */
    fun firstCheckDate(scanDays: List<ScanDay>, today: LocalDate): LocalDate? = checkedDates(scanDays, today).minOrNull()

    /** カレンダーで選べる日：最初のチェックの日から今日までの中で、チェックの記録がある日。 */
    fun isSelectable(date: LocalDate, checkedDates: Set<LocalDate>, today: LocalDate): Boolean =
        !date.isAfter(today) && date in checkedDates

    /**
     * すぐ選べる期間。直近7日は今日を含む7日、直近30日は今日を含む30日、全期間は最初のチェックの日から。
     * どれも最初のチェックの日より前には広げない。まだチェックがなければ今日だけ。
     */
    fun quickRange(quick: QuickRange, firstCheckDate: LocalDate?, today: LocalDate): DateRange {
        val first = firstCheckDate?.takeIf { !it.isAfter(today) } ?: today
        val start = when (quick) {
            QuickRange.LAST_7_DAYS -> today.minusDays(6)
            QuickRange.LAST_30_DAYS -> today.minusDays(29)
            QuickRange.ALL -> first
        }
        return DateRange(maxOf(start, first), today)
    }

    /** 今の期間と同じになる、すぐ選べるボタン（青で強調する）。 */
    fun matchingQuick(range: DateRange, firstCheckDate: LocalDate?, today: LocalDate): Set<QuickRange> =
        QuickRange.entries.filterTo(LinkedHashSet()) { quickRange(it, firstCheckDate, today) == range }

    /** カレンダーで選んだ期間を、選べる範囲（最初のチェックの日から今日まで）に収める。 */
    fun clamp(range: DateRange, firstCheckDate: LocalDate?, today: LocalDate): DateRange {
        val first = firstCheckDate?.takeIf { !it.isAfter(today) } ?: today
        val start = range.start.coerceIn(first, today)
        val end = range.end.coerceIn(start, today)
        return DateRange(start, end)
    }

    // ---- 件数と一覧 ----

    /**
     * 期間内の変化から、件数と表示する行を作る。
     * @param changes 記録された変化（端末の変化以外が入っていても除く）
     * @param hidden 件数ボタンで非表示にした種類
     */
    fun build(
        changes: List<AppChange>,
        scanDays: List<ScanDay>,
        range: DateRange,
        hidden: Set<HistoryKind>,
        zone: ZoneId,
    ): HistoryResult {
        val inRange = changes.filter { isDeviceChange(it.type) && contains(range, it.occurredAt, zone) }
        val counts = HistoryKind.entries.associateWith { kind -> inRange.count { kindOf(it.type) == kind } }
        val rows = inRange
            .filter { change -> kindOf(change.type).let { it == null || it !in hidden } }
            .sortedWith(compareByDescending<AppChange> { it.occurredAt }.thenBy { it.type.rowOrder() }.thenBy { it.packageName })
        val checkCount = scanDays
            .filter { day -> runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it in range } == true }
            .sumOf { it.count }
        val resetCount = inRange.count { it.type == ChangeType.RESET }
        return HistoryResult(range, counts, rows, checkCount, hidden, resetCount)
    }

    /** 表示する行がないときの案内。行があれば null。 */
    fun emptyReason(result: HistoryResult): HistoryEmpty? = when {
        result.rows.isNotEmpty() -> null
        result.totalChanges > 0 -> HistoryEmpty.ALL_HIDDEN
        result.checkCount > 0 -> HistoryEmpty.NO_CHANGES
        else -> HistoryEmpty.NO_CHECKS
    }

    /** 同じ時刻の行の並び（記録のときと同じく、削除 → 追加 → 更新 → 有効・無効 → 署名）。 */
    private fun ChangeType.rowOrder(): Int = when (this) {
        ChangeType.RESET -> 0
        ChangeType.REMOVED -> 1
        ChangeType.ADDED -> 2
        ChangeType.UPDATED -> 3
        ChangeType.ENABLED, ChangeType.DISABLED -> 4
        ChangeType.SIGNATURE_CHANGED -> 5
        else -> 6
    }

    private operator fun DateRange.contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    // ---- 共有の文字 ----

    /**
     * 表示中の一覧を共有する文字にする。
     * 1行目に題名、2行目に期間、3行目に表示中の種類の件数（非表示の種類があれば「（更新は除く）」のように）、その後に1行ずつ変化。
     */
    fun shareText(result: HistoryResult, texts: HistoryShareTexts, zone: ZoneId): String {
        val dateFormatter = DateTimeFormatter.ofPattern(texts.datePattern)
        val sameYear = result.range.start.year == result.range.end.year
        val rowFormatter = DateTimeFormatter.ofPattern(if (sameYear) texts.rowTimePattern else texts.rowTimeWithYearPattern)

        val visibleKinds = HistoryKind.entries.filter { it !in result.hidden }
        val hiddenKinds = HistoryKind.entries.filter { it in result.hidden }
        val countLine = buildString {
            append(
                visibleKinds.joinToString(texts.countSeparator) { kind ->
                    texts.countFormat.format(texts.kindLabels.getValue(kind), result.counts[kind] ?: 0)
                },
            )
            if (hiddenKinds.isNotEmpty()) {
                append(texts.excludedFormat.format(hiddenKinds.joinToString(texts.kindJoiner) { texts.kindLabels.getValue(it) }))
            }
        }

        return buildList {
            add(texts.title)
            add(texts.periodFormat.format(result.range.start.format(dateFormatter), result.range.end.format(dateFormatter)))
            add(countLine)
            result.rows.forEach { add(rowText(it, texts, rowFormatter, zone)) }
        }.joinToString("\n")
    }

    private fun rowText(change: AppChange, texts: HistoryShareTexts, formatter: DateTimeFormatter, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(change.occurredAt).atZone(zone).format(formatter)
        val typeLabel = texts.typeLabels.getValue(change.type)
        val mark = texts.markFormat.format(
            if (change.type == ChangeType.ADDED && change.isReinstall) typeLabel + texts.reinstallSuffix else typeLabel,
        )
        if (change.type == ChangeType.RESET) return listOf(mark, texts.resetText, time).joinToString(" ")
        val parts = mutableListOf(mark, change.name)
        versionText(change, texts)?.let { parts += it }
        parts += texts.packageFormat.format(change.packageName)
        parts += time
        when (change.type) {
            ChangeType.ADDED, ChangeType.UPDATED -> change.source?.let { source -> texts.sourceLabels[source]?.let { parts += it } }
            ChangeType.SIGNATURE_CHANGED -> parts += texts.signatureNote
            else -> Unit
        }
        return parts.joinToString(" ")
    }

    /** 行のバージョン。更新は「旧 → 新」、削除は「（削除時）」。バージョンがなければ null。 */
    fun versionText(change: AppChange, texts: HistoryShareTexts): String? {
        val new = change.versionName?.takeIf { it.isNotBlank() } ?: change.versionCode?.toString()
        return when (change.type) {
            ChangeType.UPDATED -> {
                val old = change.oldVersionName?.takeIf { it.isNotBlank() } ?: change.oldVersionCode?.toString()
                if (old != null && new != null) texts.updateVersionFormat.format(old, new) else new?.let { texts.versionFormat.format(it) }
            }
            ChangeType.REMOVED -> new?.let { texts.removedVersionFormat.format(it) }
            else -> new?.let { texts.versionFormat.format(it) }
        }
    }
}
