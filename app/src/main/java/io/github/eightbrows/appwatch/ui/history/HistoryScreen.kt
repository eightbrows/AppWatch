package io.github.eightbrows.appwatch.ui.history

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.MainActivity
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.ui.components.AppIconWithMark
import io.github.eightbrows.appwatch.ui.components.ROW_MARK_SIZE
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import io.github.eightbrows.appwatch.ui.components.DateForm
import io.github.eightbrows.appwatch.ui.components.formatDate
import io.github.eightbrows.appwatch.ui.components.withDaysAgo
import androidx.compose.ui.text.AnnotatedString
import io.github.eightbrows.appwatch.data.history.DateRange
import io.github.eightbrows.appwatch.data.history.HistoryEmpty
import io.github.eightbrows.appwatch.data.history.HistoryKind
import io.github.eightbrows.appwatch.data.history.HistoryResult
import io.github.eightbrows.appwatch.data.history.HistoryRules
import io.github.eightbrows.appwatch.data.history.HistoryShareTexts
import io.github.eightbrows.appwatch.data.history.QuickRange
import io.github.eightbrows.appwatch.data.record.AppChange
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.data.history.HistoryClock
import io.github.eightbrows.appwatch.data.transfer.DataSelection
import io.github.eightbrows.appwatch.ui.components.AdminTopBar
import io.github.eightbrows.appwatch.ui.components.DataSwitcher
import io.github.eightbrows.appwatch.ui.components.formatSnapshotTime
import io.github.eightbrows.appwatch.ui.status.AppStatusController
import io.github.eightbrows.appwatch.ui.status.rememberNoticePresenter
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 履歴と比較（管理者）。画面サンプル 09_history_compare.png、仕様書 6章。
 * 期間内に記録された端末の変化を、新しい順に1件ずつ出す。行を押すとアプリの詳細（管理者）を開く。
 */
@Composable
fun HistoryRoute(
    controller: HistoryController,
    /** アプリの詳細で保存して戻ってきたときの「元に戻す」付きのお知らせに使う */
    statusController: AppStatusController,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val records by remember { controller.records }.collectAsState(initial = null)
    val selection by remember { controller.selection }.collectAsState(initial = null)
    val zone = remember { ZoneId.systemDefault() }
    val viewing = selection?.selected?.takeIf { !it.isLocal }
    // 取り込んだデータでは、「今日」の代わりにファイルの書き出し日を使う（仕様書 7章）
    val today = viewing?.exportedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now(zone)
    val previousExportDate = viewing?.previousExportedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    // 期間：すぐ選べるボタンで選んだものか、カレンダーで選んだもの（画面の回転でも消えないように保存する）。
    // データを切り替えたら、開いたときと同じ「直近7日」・すべて表示に戻す
    val datasetKey = selection?.selected?.id
    var quickName by rememberSaveable(datasetKey) { mutableStateOf<String?>(QuickRange.LAST_7_DAYS.name) }
    var customStart by rememberSaveable(datasetKey) { mutableStateOf<Long?>(null) }
    var customEnd by rememberSaveable(datasetKey) { mutableStateOf<Long?>(null) }
    var hiddenNames by rememberSaveable(datasetKey) { mutableStateOf(listOf<String>()) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    rememberNoticePresenter(statusController, snackbarHostState)
    // 行のアイコン：この端末のデータを見ているときは、今端末にあるアプリ（パッケージ名 → 有効か）のアイコンを出す
    val localEntries by remember { statusController.localEntries }.collectAsState(initial = null)
    val presentApps = remember(localEntries) {
        localEntries.orEmpty().filter { it.isPresent }.associate { it.packageName to it.isEnabled }
    }
    val texts = rememberShareTexts()
    val context = LocalContext.current
    val deviceLine = viewing?.let { stringResource(R.string.history_share_device, it.name, it.exportedAt?.let { t -> formatSnapshotTime(t) }.orEmpty()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AdminTopBar(title = stringResource(R.string.settings_history), onBack = onBack)
        val current = records
        val currentSelection = selection
        if (current == null || currentSelection == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        val checkedDates = remember(current.scanDays, today) { HistoryRules.checkedDates(current.scanDays, today) }
        val clock = HistoryClock(today = today, firstCheckDate = checkedDates.minOrNull(), previousExportDate = previousExportDate)
        val firstDate = clock.firstCheckDate
        val quick = quickName?.let { name -> clock.quickButtons.firstOrNull { it.name == name } }
        val start = customStart
        val end = customEnd
        val range = if (quick == null && start != null && end != null) {
            HistoryRules.clamp(DateRange(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end)), firstDate, today)
        } else {
            HistoryRules.quickRange(quick ?: QuickRange.LAST_7_DAYS, clock)
        }
        // 青くするのは1つだけ：最後に押したボタン、カレンダーで選んだときは同じ期間のうち最も短いもの
        val highlighted = HistoryRules.highlighted(if (customStart == null) quick ?: QuickRange.LAST_7_DAYS else null, range, clock)
        val hidden = hiddenNames.mapNotNullTo(HashSet()) { name -> HistoryKind.entries.firstOrNull { it.name == name } }
        val result = remember(current, range, hidden, zone) { HistoryRules.build(current.changes, current.scanDays, range, hidden, zone) }

        Box(Modifier.weight(1f)) {
            HistoryList(
                result = result,
                texts = texts,
                zone = zone,
                presentApps = presentApps,
                viewingImported = viewing != null,
                selection = currentSelection,
                onSelectDataset = controller::select,
                buttons = clock.quickButtons,
                highlighted = highlighted,
                canPick = firstDate != null,
                onOpenPicker = { showPicker = true },
                onQuick = { selected ->
                    quickName = selected.name
                    customStart = null
                    customEnd = null
                },
                onToggleKind = { kind ->
                    hiddenNames = if (kind.name in hiddenNames) hiddenNames - kind.name else hiddenNames + kind.name
                },
                onOpenDetail = onOpenDetail,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp),
            )
        }
        ShareBar(
            enabled = result.rows.isNotEmpty(),
            onShare = { share(context, texts.title, HistoryRules.shareText(result, texts, zone, deviceLine)) },
        )

        if (showPicker && firstDate != null) {
            PeriodPickerDialog(
                range = range,
                checkedDates = checkedDates,
                firstDate = firstDate,
                today = today,
                onDismiss = { showPicker = false },
                onConfirm = { picked ->
                    showPicker = false
                    quickName = null
                    customStart = picked.start.toEpochDay()
                    customEnd = picked.end.toEpochDay()
                },
            )
        }
    }
}

/** 共有の文字と一覧の行に使う言葉（strings.xml から）。 */
@Composable
private fun rememberShareTexts(): HistoryShareTexts {
    val resources = LocalResources.current
    return remember(resources) { shareTexts(resources) }
}

private fun shareTexts(r: Resources) = HistoryShareTexts(
    title = r.getString(R.string.history_share_title, r.getString(R.string.app_name)),
    periodFormat = r.getString(R.string.history_share_period),
    datePattern = r.getString(R.string.history_date),
    countFormat = r.getString(R.string.history_share_count),
    countSeparator = r.getString(R.string.history_share_count_separator),
    excludedFormat = r.getString(R.string.history_share_excluded),
    kindJoiner = r.getString(R.string.history_kind_joiner),
    kindLabels = HistoryKind.entries.associateWith { r.getString(it.labelRes()) },
    typeLabels = mapOf(
        ChangeType.ADDED to r.getString(R.string.history_type_added),
        ChangeType.REMOVED to r.getString(R.string.history_type_removed),
        ChangeType.UPDATED to r.getString(R.string.history_type_updated),
        ChangeType.ENABLED to r.getString(R.string.history_type_enabled),
        ChangeType.DISABLED to r.getString(R.string.history_type_disabled),
        ChangeType.SIGNATURE_CHANGED to r.getString(R.string.history_type_signature),
        ChangeType.RESET to r.getString(R.string.history_type_reset),
    ),
    markFormat = r.getString(R.string.history_share_mark),
    reinstallSuffix = r.getString(R.string.history_share_reinstall),
    versionFormat = r.getString(R.string.format_version),
    updateVersionFormat = r.getString(R.string.history_version_update),
    removedVersionFormat = r.getString(R.string.history_version_removed),
    packageFormat = r.getString(R.string.history_share_package),
    rowTimePattern = r.getString(R.string.history_row_time),
    rowTimeWithYearPattern = r.getString(R.string.history_row_time_with_year),
    sourceLabels = mapOf(
        InstallSource.GOOGLE_PLAY to r.getString(R.string.source_google_play),
        InstallSource.OTHER to r.getString(R.string.source_other),
        InstallSource.UNKNOWN to r.getString(R.string.source_unknown),
    ),
    signatureNote = r.getString(R.string.history_signature_note),
    resetText = r.getString(R.string.history_reset_text),
)

private fun HistoryKind.labelRes(): Int = when (this) {
    HistoryKind.ADDED -> R.string.history_kind_added
    HistoryKind.REMOVED -> R.string.history_kind_removed
    HistoryKind.UPDATED -> R.string.history_kind_updated
    HistoryKind.DISABLED -> R.string.history_kind_disabled
    HistoryKind.SIGNATURE -> R.string.history_kind_signature
}

/** Android の共有（選択の画面付き）で送る。本アプリ（事前登録の受け取り）は選択肢から外す。 */
private fun share(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.history_share_chooser)).apply {
        putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
    }
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_cannot_open_screen, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun HistoryList(
    result: HistoryResult,
    texts: HistoryShareTexts,
    zone: ZoneId,
    /** 今この端末にあるアプリ（パッケージ名 → 有効か） */
    presentApps: Map<String, Boolean>,
    /** 取り込んだデータを見ているか（そのときはアイコンの代わりに頭文字の印） */
    viewingImported: Boolean,
    selection: DataSelection,
    onSelectDataset: (String) -> Unit,
    buttons: List<QuickRange>,
    highlighted: QuickRange?,
    canPick: Boolean,
    onOpenPicker: () -> Unit,
    onQuick: (QuickRange) -> Unit,
    onToggleKind: (HistoryKind) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
    ) {
        if (selection.showsSwitcher) {
            item(key = "data") { DataSwitcher(selection, onSelectDataset, Modifier.padding(bottom = 10.dp)) }
        }
        item(key = "period") { PeriodBoxes(result.range, canPick, onOpenPicker) }
        item(key = "quick") { QuickButtons(buttons, highlighted, onQuick) }
        item(key = "counts") { CountButtons(result, onToggleKind) }
        item(key = "header") { ListHeader(result) }
        val emptyReason = HistoryRules.emptyReason(result)
        if (emptyReason != null) {
            item(key = "empty") { EmptyCard(emptyReason, result.checkCount) }
        }
        itemsIndexed(result.rows, key = { index, row -> "${row.occurredAt}-${row.type}-${row.packageName}-$index" }) { index, row ->
            ChangeRow(
                change = row,
                texts = texts,
                // 取り込んだデータは別の端末のアプリなので、色付きの頭文字の印（端末にあるものとして扱う）
                present = viewingImported || row.packageName in presentApps,
                enabled = presentApps[row.packageName] ?: true,
                loadIcon = !viewingImported,
                // 行の日時は曜日付き（何日前は ChangeRow で添える）。共有の文字は今までどおり曜日・何日前なし
                time = formatDate(row.occurredAt, DateForm.LIST),
                isFirst = index == 0,
                isLast = index == result.rows.lastIndex,
                onClick = if (row.type == ChangeType.RESET) null else ({ onOpenDetail(row.packageName) }),
            )
        }
    }
}

/** 「比較元（この日から）→ 比較先（この日まで）」。どちらを押しても同じカレンダーを開く。 */
@Composable
private fun PeriodBoxes(range: DateRange, enabled: Boolean, onOpenPicker: () -> Unit) {
    val pattern = stringResource(R.string.history_date)
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    Row(
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PeriodBox(stringResource(R.string.history_from_label), range.start.format(formatter), enabled, onOpenPicker, Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(24.dp),
        )
        PeriodBox(stringResource(R.string.history_to_label), range.end.format(formatter), enabled, onOpenPicker, Modifier.weight(1f))
    }
}

@Composable
private fun PeriodBox(label: String, date: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val clickLabel = stringResource(R.string.history_choose_period)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClickLabel = clickLabel, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // 文字を大きくしている端末では2行に折り返す
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** すぐ選べる期間。今の期間と同じボタンは青で強調する。 */
@Composable
private fun QuickButtons(buttons: List<QuickRange>, highlighted: QuickRange?, onQuick: (QuickRange) -> Unit) {
    // 4つ（前回以降あり）のときは、1行に収まるように小さめにする
    val compact = buttons.size > 3
    FlowRow(
        modifier = Modifier.padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        buttons.forEach { quick ->
            val on = quick == highlighted
            val shape = RoundedCornerShape(50)
            Text(
                text = stringResource(
                    when (quick) {
                        QuickRange.LAST_7_DAYS -> R.string.history_quick_7
                        QuickRange.LAST_30_DAYS -> R.string.history_quick_30
                        QuickRange.SINCE_LAST -> R.string.history_quick_since_last
                        QuickRange.ALL -> R.string.history_quick_all
                    },
                ),
                modifier = Modifier
                    .clip(shape)
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (on) 1.5.dp else 1.dp,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = shape,
                    )
                    .clickable(role = Role.Button) { onQuick(quick) }
                    .semantics { selected = on }
                    .heightIn(min = 34.dp)
                    .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 6.dp),
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 種類ごとの件数ボタン。押すたびに表示・非表示を切り替える。表示中は青い枠、非表示は灰色の点線。 */
@Composable
private fun CountButtons(result: HistoryResult, onToggle: (HistoryKind) -> Unit) {
    val colors = AppWatchTheme.colors
    Row(
        modifier = Modifier.padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HistoryKind.entries.forEach { kind ->
            val shown = kind !in result.hidden
            val count = result.counts[kind] ?: 0
            val label = stringResource(kind.labelRes())
            val countColor = when {
                !shown -> MaterialTheme.colorScheme.outline
                kind == HistoryKind.ADDED -> MaterialTheme.colorScheme.primary
                kind == HistoryKind.REMOVED || kind == HistoryKind.SIGNATURE -> colors.warningStrong
                else -> MaterialTheme.colorScheme.onSurface
            }
            val shape = RoundedCornerShape(12.dp)
            val description = stringResource(R.string.history_kind_desc, label, count)
            val state = stringResource(if (shown) R.string.history_kind_shown else R.string.history_kind_hidden)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(shape)
                    .then(
                        if (shown) {
                            Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                .border(2.dp, MaterialTheme.colorScheme.primary, shape)
                        } else {
                            Modifier.dashedBorder(MaterialTheme.colorScheme.outline, 12.dp)
                        },
                    )
                    .toggleable(value = shown, role = Role.Checkbox, onValueChange = { onToggle(kind) })
                    .semantics(mergeDescendants = true) {
                        contentDescription = description
                        stateDescription = state
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = countColor,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (shown) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 角の丸い点線の枠。 */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val strokeWidth = 1.5.dp.toPx()
    val dash = 5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
        size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.8f))),
    )
}

/** 「変化の一覧（新しい順）」と、右に件数（非表示の種類があれば「更新を除いて表示中 ・ n件」）。 */
@Composable
private fun ListHeader(result: HistoryResult) {
    val hiddenKinds = HistoryKind.entries.filter { it in result.hidden }
    val joiner = stringResource(R.string.history_kind_joiner)
    val hiddenLabels = hiddenKinds.map { stringResource(it.labelRes()) }.joinToString(joiner)
    val count = result.rows.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.history_list_title),
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (hiddenKinds.isEmpty()) {
                pluralStringResource(R.plurals.history_list_count, count, count)
            } else {
                pluralStringResource(R.plurals.history_list_count_hidden, count, hiddenLabels, count)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun EmptyCard(reason: HistoryEmpty, checkCount: Int) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when (reason) {
                HistoryEmpty.NO_CHANGES -> pluralStringResource(R.plurals.history_empty_no_changes, checkCount, checkCount)
                HistoryEmpty.NO_CHECKS -> stringResource(R.string.history_empty_no_checks)
                HistoryEmpty.ALL_HIDDEN -> stringResource(R.string.history_empty_all_hidden)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 変化の1行。署名が変わった行は薄い橙の背景。 */
@Composable
private fun ChangeRow(
    change: AppChange,
    texts: HistoryShareTexts,
    /** アプリが今端末にあるか（ないときは灰色の頭文字の印） */
    present: Boolean,
    enabled: Boolean,
    loadIcon: Boolean,
    time: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: (() -> Unit)?,
) {
    val colors = AppWatchTheme.colors
    val corner = 14.dp
    val shape = RoundedCornerShape(
        topStart = if (isFirst) corner else 0.dp,
        topEnd = if (isFirst) corner else 0.dp,
        bottomStart = if (isLast) corner else 0.dp,
        bottomEnd = if (isLast) corner else 0.dp,
    )
    val isSignature = change.type == ChangeType.SIGNATURE_CHANGED
    val clickLabel = stringResource(R.string.history_open_detail)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSignature) colors.warningContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (change.type == ChangeType.RESET) {
                // 初期化の行は、今までどおりの印
                ResetMark()
                Spacer(Modifier.width(12.dp))
            } else {
                // アプリのアイコン（確認状態の画面と同じ大きさ）と、右下に変化の種類の印
                AppIconWithMark(
                    name = change.name,
                    packageName = change.packageName,
                    versionKey = change.versionName.orEmpty(),
                    present = present,
                    loadIcon = loadIcon,
                    dimmed = !enabled,
                    ringColor = if (isSignature) colors.warningContainer else MaterialTheme.colorScheme.surface,
                ) {
                    TypeBadge(change.type)
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (change.type == ChangeType.RESET) {
                    Text(stringResource(R.string.history_reset_text), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    RowDetail(withDaysAgo(stringResource(R.string.status_row_source_date, time, stringResource(R.string.history_reset_detail)), change.occurredAt))
                    return@Column
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    // 変化の種類の言葉（印の色・記号だけに頼らない）
                    TypeLabel(change.type)
                    Text(
                        text = change.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (change.type == ChangeType.ADDED && change.isReinstall) ReinstallBadge()
                }
                HistoryRules.versionText(change, texts)?.let { RowDetail(it) }
                Text(
                    text = change.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val detected = stringResource(R.string.history_detected, time)
                val extra = when (change.type) {
                    ChangeType.ADDED, ChangeType.UPDATED -> change.source?.let { texts.sourceLabels[it] }
                    ChangeType.SIGNATURE_CHANGED -> texts.signatureNote
                    else -> null
                }
                RowDetail(
                    text = withDaysAgo(
                        if (extra != null) stringResource(R.string.status_row_source_date, detected, extra) else detected,
                        change.occurredAt,
                    ),
                    color = if (isSignature) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!isLast) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun RowDetail(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun RowDetail(text: AnnotatedString, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** 「再インストール」の印（橙）。 */
@Composable
private fun ReinstallBadge() {
    val colors = AppWatchTheme.colors
    Text(
        text = stringResource(R.string.history_reinstall),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.warningStrong)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = colors.onDanger,
    )
}

/** 初期化の行の印（アイコンの代わり）。 */
@Composable
private fun ResetMark() {
    val colors = AppWatchTheme.colors
    val container = colors.dangerContainer
    val content = colors.onDangerContainer
    Box(Modifier.width(64.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text = stringResource(R.string.history_mark_reset),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(container)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

/** 変化の種類ごとの色（言葉の札と、アイコンの右下の印）。 */
@Composable
private fun typeColors(type: ChangeType): Pair<Color, Color> {
    val colors = AppWatchTheme.colors
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        ChangeType.ADDED -> scheme.primary to scheme.onPrimary
        ChangeType.REMOVED -> colors.warningStrong to Color.White
        ChangeType.UPDATED -> scheme.secondary to scheme.onSecondary
        ChangeType.ENABLED -> colors.ok to colors.onOk
        ChangeType.DISABLED -> colors.unconfirmed to Color.White
        ChangeType.SIGNATURE_CHANGED -> colors.danger to colors.onDanger
        else -> colors.unconfirmed to Color.White
    }
}

/** アイコンの右下の、変化の種類の印（＋ − ↑ ✓ ‖ !）。言葉は行の中の札で出すので、読み上げは札に任せる。 */
@Composable
private fun TypeBadge(type: ChangeType) {
    val (container, content) = typeColors(type)
    val symbol = when (type) {
        ChangeType.ADDED -> R.string.history_symbol_added
        ChangeType.REMOVED -> R.string.history_symbol_removed
        ChangeType.UPDATED -> R.string.history_symbol_updated
        ChangeType.ENABLED -> R.string.mark_ok
        ChangeType.DISABLED -> R.string.history_symbol_disabled
        else -> R.string.mark_caution
    }
    Box(
        modifier = Modifier
            .size(ROW_MARK_SIZE)
            .clip(CircleShape)
            .background(container)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(symbol), color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** 変化の種類の言葉の札（名前の前）。 */
@Composable
private fun TypeLabel(type: ChangeType) {
    val (container, content) = typeColors(type)
    val label = when (type) {
        ChangeType.ADDED -> R.string.history_type_added
        ChangeType.REMOVED -> R.string.history_type_removed
        ChangeType.UPDATED -> R.string.history_type_updated
        ChangeType.ENABLED -> R.string.history_type_enabled
        ChangeType.DISABLED -> R.string.history_type_disabled
        ChangeType.SIGNATURE_CHANGED -> R.string.history_type_signature
        else -> R.string.history_type_reset
    }
    Text(
        text = stringResource(label),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = content,
    )
}

/** 下部に固定の「この結果を共有」。表示中の行がないときは押せない。 */
@Composable
private fun ShareBar(enabled: Boolean, onShare: () -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = onShare,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.history_share), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** 日付 → カレンダーの UTC の時刻（DateRangePicker は日付を UTC の 0:00 で扱う）。 */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcToLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** 期間選択のカレンダー。選べるのは、最初のチェックの日から今日までの、チェックの記録がある日だけ。時刻は選ばない。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodPickerDialog(
    range: DateRange,
    checkedDates: Set<LocalDate>,
    firstDate: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (DateRange) -> Unit,
) {
    val selectable = remember(checkedDates, firstDate, today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                HistoryRules.isSelectable(utcTimeMillis.utcToLocalDate(), checkedDates, today)

            override fun isSelectableYear(year: Int): Boolean = year in firstDate.year..today.year
        }
    }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = range.start.toUtcMillis(),
        initialSelectedEndDateMillis = range.end.toUtcMillis(),
        // 月は縦に並ぶので、比較元の月から出す（下に比較先までが続く）
        initialDisplayedMonthMillis = range.start.toUtcMillis(),
        yearRange = firstDate.year..today.year,
        initialDisplayMode = DisplayMode.Picker,
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.utcToLocalDate() ?: return@TextButton
                    val end = state.selectedEndDateMillis?.utcToLocalDate() ?: start
                    onConfirm(DateRange(start, end))
                },
            ) { Text(stringResource(R.string.history_picker_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    text = stringResource(R.string.history_picker_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            showModeToggle = false,
        )
    }
}
