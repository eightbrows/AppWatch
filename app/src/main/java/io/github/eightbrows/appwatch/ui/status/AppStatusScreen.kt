package io.github.eightbrows.appwatch.ui.status

import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.status.AppStatusEntry
import io.github.eightbrows.appwatch.data.status.AppStatusRules
import io.github.eightbrows.appwatch.data.status.FilterChip
import io.github.eightbrows.appwatch.data.status.FilterRow
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.data.transfer.DataSelection
import io.github.eightbrows.appwatch.ui.components.AdminTopBar
import io.github.eightbrows.appwatch.ui.components.DataSwitcher
import io.github.eightbrows.appwatch.ui.components.ViewingFooter
import io.github.eightbrows.appwatch.ui.components.StatusMark
import io.github.eightbrows.appwatch.ui.components.AppIcon
import io.github.eightbrows.appwatch.ui.components.AppSearchField
import io.github.eightbrows.appwatch.ui.components.ReasonChip
import io.github.eightbrows.appwatch.model.AppSearch
import io.github.eightbrows.appwatch.model.ReviewReason
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import io.github.eightbrows.appwatch.ui.components.formatDateTime
import io.github.eightbrows.appwatch.ui.components.formatShortDate
import io.github.eightbrows.appwatch.ui.components.labelRes
import io.github.eightbrows.appwatch.ui.components.toLocalDateTime
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import kotlinx.coroutines.launch

/**
 * アプリの確認状態（管理者）。画面サンプル 06_app_status.png、仕様書 6章。
 * 変更のたびに「元に戻す」付きのお知らせを出す。元に戻せるのはお知らせが出ている間だけ。
 */
@Composable
fun AppStatusRoute(
    controller: AppStatusController,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onPreRegister: () -> Unit,
) {
    val entries by remember { controller.entries }.collectAsState(initial = null)
    val selection by remember { controller.selection }.collectAsState(initial = null)
    // 取り込んだデータを見ているときは、変更の操作（選択・まとめての変更・事前登録）を出さない
    val readOnly = selection?.isViewingImported == true
    val enabled by remember { controller.filterEnabled }.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    // 選んだアプリ（パッケージ名）。画面の回転でも消えないように保存する
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showNotice = rememberNoticePresenter(controller, snackbarHostState)

    val currentEntries = entries
    val currentEnabled = enabled
    val visible = remember(currentEntries, currentEnabled, query) {
        if (currentEntries == null || currentEnabled == null) emptyList()
        else AppStatusRules.visible(currentEntries, currentEnabled, query)
    }
    // 絞り込みや検索で見えなくなったアプリは、まとめて変える対象にしない
    val selectedVisible = remember(visible, selected, readOnly) {
        val set = if (readOnly) emptySet() else selected.toSet()
        visible.filter { it.packageName in set }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AdminTopBar(title = stringResource(R.string.settings_app_status), onBack = onBack)
        Box(Modifier.weight(1f)) {
            if (currentEntries == null || currentEnabled == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                StatusList(
                    visible = visible,
                    selection = selection,
                    onSelectDataset = { id ->
                        selected = emptyList()
                        controller.selectDataset(id)
                    },
                    readOnly = readOnly,
                    enabled = currentEnabled,
                    query = query,
                    selected = selectedVisible.mapTo(HashSet()) { it.packageName },
                    onQueryChange = { query = it },
                    onPreRegister = onPreRegister,
                    onToggleChip = { chip ->
                        val next = if (chip in currentEnabled) currentEnabled - chip else currentEnabled + chip
                        controller.setFilterEnabled(next)
                    },
                    onToggleSelect = { packageName ->
                        selected = if (packageName in selected) selected - packageName else selected + packageName
                    },
                    onSelectAllVisible = { selected = (selected + visible.map { it.packageName }).distinct() },
                    onClearSelection = { selected = emptyList() },
                    onOpenDetail = onOpenDetail,
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 下部のボタンがないときは、ナビゲーションバーに重ならないようにする
                    .then(if (selectedVisible.isEmpty() && !readOnly) Modifier.navigationBarsPadding() else Modifier)
                    .padding(horizontal = 8.dp),
            )
        }
        if (readOnly) ViewingFooter()
        if (selectedVisible.isNotEmpty()) {
            BulkActions(
                onSelect = { status ->
                    val targets = selectedVisible
                    scope.launch {
                        val notice = controller.applyBulk(status, targets)
                        // 変えた後は選択を外す（何も変えなかったときは、選び直せるように残す）
                        if (notice !is StatusNotice.AllWarned && notice !is StatusNotice.NoChange) selected = emptyList()
                        showNotice(notice)
                    }
                },
            )
        }
    }
}

/**
 * 「元に戻す」付きのお知らせを出す関数を返す。押されたら取り消し、その結果も短く知らせる。
 * アプリの詳細で保存・解除して戻ってきたときのお知らせ（[AppStatusController.pendingNotice]）も、ここで出す。
 */
@Composable
internal fun rememberNoticePresenter(
    controller: AppStatusController,
    snackbarHostState: SnackbarHostState,
): (StatusNotice) -> Unit {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val show: (StatusNotice) -> Unit = remember(controller, snackbarHostState, resources) {
        { notice ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val operationId = notice.operationId
                val result = snackbarHostState.showSnackbar(
                    message = noticeText(resources, notice),
                    actionLabel = if (operationId != null) resources.getString(R.string.notice_undo) else null,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed && operationId != null) {
                    val undone = controller.undo(operationId)
                    snackbarHostState.showSnackbar(noticeText(resources, undone), duration = SnackbarDuration.Short)
                }
            }
        }
    }
    val pendingNotice by controller.pendingNotice.collectAsState()
    LaunchedEffect(pendingNotice) {
        pendingNotice?.let {
            controller.consumePendingNotice()
            show(it)
        }
    }
    return show
}

/** お知らせの文。 */
internal fun noticeText(resources: Resources, notice: StatusNotice): String = when (notice) {
    is StatusNotice.BulkOk -> if (notice.skipped > 0) {
        resources.getQuantityString(R.plurals.notice_bulk_ok_skipped, notice.changed, notice.changed, notice.skipped)
    } else {
        resources.getQuantityString(R.plurals.notice_bulk_ok, notice.changed, notice.changed)
    }
    is StatusNotice.AllWarned -> resources.getQuantityString(R.plurals.notice_bulk_all_warned, notice.count, notice.count)
    is StatusNotice.Bulk -> when (notice.status) {
        ReviewStatus.CAUTION -> resources.getQuantityString(R.plurals.notice_bulk_caution, notice.changed, notice.changed)
        ReviewStatus.UNCONFIRMED ->
            resources.getQuantityString(R.plurals.notice_bulk_unconfirmed, notice.changed, notice.changed)
        ReviewStatus.OK -> resources.getQuantityString(R.plurals.notice_bulk_ok, notice.changed, notice.changed)
    }
    StatusNotice.NoChange -> resources.getString(R.string.notice_no_change)
    is StatusNotice.DetailSaved -> {
        val status = notice.status
        when {
            status != null && notice.memoChanged ->
                resources.getString(R.string.notice_saved_both, notice.name, resources.getString(status.labelRes()))
            status != null -> resources.getString(R.string.notice_saved_status, notice.name, resources.getString(status.labelRes()))
            else -> resources.getString(R.string.notice_saved_memo, notice.name)
        }
    }
    is StatusNotice.PreRegistered -> resources.getString(R.string.notice_pre_registered, notice.name)
    is StatusNotice.Unregistered -> resources.getString(R.string.notice_unregistered, notice.name)
    StatusNotice.Undone -> resources.getString(R.string.notice_undone)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusList(
    visible: List<AppStatusEntry>,
    selection: DataSelection?,
    onSelectDataset: (String) -> Unit,
    readOnly: Boolean,
    enabled: Set<FilterChip>,
    query: String,
    selected: Set<String>,
    onQueryChange: (String) -> Unit,
    onPreRegister: () -> Unit,
    onToggleChip: (FilterChip) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // データの切り替え（この端末／取り込んだデータ）と、閲覧中の帯
        if (selection != null && selection.showsSwitcher) {
            item(key = "data") { DataSwitcher(selection, onSelectDataset, Modifier.padding(top = 4.dp)) }
        }
        item(key = "search") { SearchRow(query, onQueryChange, onPreRegister, preRegisterEnabled = !readOnly) }
        item(key = "filters") { Filters(enabled, onToggleChip) }
        if (!readOnly) {
            stickyHeader(key = "selection") {
                SelectionBar(
                    visibleCount = visible.size,
                    selectedCount = selected.size,
                    onSelectAllVisible = onSelectAllVisible,
                    onClearSelection = onClearSelection,
                )
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.status_empty),
                    modifier = Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(visible, key = { it.packageName }) { entry ->
            StatusRow(
                entry = entry,
                isSelected = entry.packageName in selected,
                showCheckbox = !readOnly,
                onToggleSelect = { onToggleSelect(entry.packageName) },
                onClick = { onOpenDetail(entry.packageName) },
            )
        }
    }
}

@Composable
private fun SearchRow(query: String, onQueryChange: (String) -> Unit, onPreRegister: () -> Unit, preRegisterEnabled: Boolean = true) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppSearchField(query = query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f))
        Button(
            onClick = onPreRegister,
            enabled = preRegisterEnabled,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.status_pre_register), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** 絞り込み（5行）。ラベルは末尾にコロンを付ける（仕様書 6章）。 */
@Composable
private fun Filters(enabled: Set<FilterChip>, onToggle: (FilterChip) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterRow.entries.forEach { row ->
            val chips = FilterChip.entries.filter { it.row == row }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (row == FilterRow.DEVICE) {
                    // 5行目は「端末：あり なし」「登録：事前登録」「状態：無効」を1行に並べる
                    FilterLabel(R.string.filter_label_device)
                    ChipView(FilterChip.DEVICE_PRESENT, enabled, onToggle)
                    ChipView(FilterChip.DEVICE_ABSENT, enabled, onToggle)
                    FilterLabel(R.string.filter_label_registered, start = 6.dp)
                    ChipView(FilterChip.REGISTERED, enabled, onToggle)
                    FilterLabel(R.string.filter_label_state, start = 6.dp)
                    ChipView(FilterChip.DISABLED, enabled, onToggle)
                } else {
                    FilterLabel(row.labelRes())
                    chips.forEach { ChipView(it, enabled, onToggle) }
                }
            }
        }
    }
}

private fun FilterRow.labelRes(): Int = when (this) {
    FilterRow.STATUS -> R.string.filter_label_status
    FilterRow.KIND -> R.string.filter_label_kind
    FilterRow.SOURCE -> R.string.filter_label_source
    FilterRow.WARNING -> R.string.filter_label_warning
    FilterRow.DEVICE -> R.string.filter_label_device
}

@Composable
private fun FilterLabel(res: Int, start: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = stringResource(res),
        modifier = Modifier.padding(start = start, end = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 絞り込みの選択肢。オンは青で強調する。 */
@Composable
private fun ChipView(chip: FilterChip, enabled: Set<FilterChip>, onToggle: (FilterChip) -> Unit) {
    val on = chip in enabled
    val shape = RoundedCornerShape(50)
    Text(
        text = stringResource(chip.labelRes()),
        modifier = Modifier
            .clip(shape)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(
                width = if (on) 1.5.dp else 1.dp,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(chip) })
            .heightIn(min = 28.dp)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun SelectionBar(
    visibleCount: Int,
    selectedCount: Int,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
) {
    // 上に重なってスクロールするので、背景の色で塗った上に置く
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedCount > 0) {
                    pluralStringResource(R.plurals.status_selected_count, selectedCount, selectedCount)
                } else {
                    pluralStringResource(R.plurals.status_visible_count, visibleCount, visibleCount)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
            TextButton(onClick = onSelectAllVisible, enabled = visibleCount > 0, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.status_select_all_visible), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            TextButton(onClick = onClearSelection, enabled = selectedCount > 0, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.status_clear_selection), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** 一覧の1行。端末にないアプリは灰色。行を押すとアプリの詳細を開く。 */
@Composable
private fun StatusRow(entry: AppStatusEntry, isSelected: Boolean, showCheckbox: Boolean, onToggleSelect: () -> Unit, onClick: () -> Unit) {
    val colors = AppWatchTheme.colors
    val dim = !entry.isPresent
    val mainColor = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = if (showCheckbox) 0.dp else 14.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)) {
            if (showCheckbox) {
                val selectLabel = stringResource(R.string.status_select_app, entry.name)
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.semantics { contentDescription = selectLabel },
                )
            }
            RowIcon(
                entry = entry,
                loadIcon = showCheckbox,
                rowColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = mainColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.isSelf) SmallLabel(stringResource(R.string.app_self_label))
                    if (entry.isPresent && !entry.isEnabled) SmallLabel(stringResource(R.string.app_disabled_label))
                }
                entry.versionName?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.format_version, it), style = MaterialTheme.typography.bodySmall, color = subColor)
                }
                Text(
                    text = AppSearch.breakablePackageName(entry.packageName),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = subColor,
                )
                SourceLine(entry)
                RowWarnings(entry)
                entry.adminMemo?.takeIf { it.isNotBlank() }?.let { memo ->
                    Text(
                        text = stringResource(R.string.status_row_memo, memo),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 行の先頭のアイコンと、右下の角に置いた確認状態の印（印は行の背景の色で縁取る）。
 * 印の中心をアイコンの右下の角に置き、印の大部分をアイコンの外に出す（重なるのは印の 1/4 まで）。
 * 外に出た分も場所を取り、文字と重ならないようにする。
 * 端末にないアプリはアプリの詳細と同じ灰色の頭文字の印、取り込んだデータを見ているとき（[loadIcon] が false）は色付きの頭文字の印。
 * 無効にしたアプリは灰色で薄く出す。
 */
@Composable
private fun RowIcon(entry: AppStatusEntry, loadIcon: Boolean, rowColor: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(ROW_ICON_SIZE + ROW_MARK_OUTER / 2)) {
        if (entry.isPresent) {
            AppIcon(
                name = entry.name,
                packageName = entry.packageName,
                size = ROW_ICON_SIZE,
                versionKey = entry.versionName.orEmpty(),
                loadIcon = loadIcon,
                dimmed = !entry.isEnabled,
            )
        } else {
            AbsentMark(entry.name, size = ROW_ICON_SIZE)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(ROW_MARK_OUTER)
                .clip(CircleShape)
                .background(rowColor)
                .padding(ROW_MARK_RING),
        ) {
            StatusMark(status = entry.status, size = ROW_MARK_SIZE)
        }
    }
}

private val ROW_ICON_SIZE = 48.dp
private val ROW_MARK_SIZE = 24.dp

/** 印の縁取り（行の背景の色）。どのアイコンの色の上でも印を見分けられるようにする。 */
private val ROW_MARK_RING = 2.5.dp
private val ROW_MARK_OUTER = ROW_MARK_SIZE + ROW_MARK_RING * 2

/** 入手元・日時（後から導入）、種類（プリインストール・システム）、端末にないこと。警告は下の札で出す。 */
@Composable
private fun SourceLine(entry: AppStatusEntry) {
    val removedAt = entry.removedAt
    val kind = entry.kind
    val source = entry.source
    val text = when {
        !entry.isPresent && removedAt != null ->
            stringResource(R.string.status_row_removed, formatShortDate(removedAt.toLocalDateTime()))
        !entry.isPresent && kind == null -> stringResource(R.string.status_row_not_installed)
        kind == AppKind.USER_INSTALLED && source != null -> {
            val date = entry.firstInstallTime?.takeIf { it > 0 }?.let { formatDateTime(it.toLocalDateTime()) }
            if (date != null) {
                stringResource(R.string.status_row_source_date, stringResource(source.labelRes()), date)
            } else {
                stringResource(source.labelRes())
            }
        }
        kind != null -> stringResource(kind.labelRes())
        else -> null
    }
    if (text != null) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 警告の札（トップ・アプリの詳細と同じ見た目）と、再インストール・署名の変更を見つけた日。確認状態は印で出しているので札にしない。 */
@Composable
private fun RowWarnings(entry: AppStatusEntry) {
    val reasons = ReviewReason.of(entry.warnings, entry.status, includeCaution = false)
    if (reasons.isEmpty()) return
    FlowRow(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        reasons.forEach { reason ->
            ReasonChip(reason)
            // 見つけた日は、その札のすぐ後に出す
            val date = when (reason) {
                ReviewReason.REINSTALLED -> entry.reinstalledAt?.let {
                    stringResource(R.string.status_reinstalled_at, formatShortDate(it.toLocalDateTime()))
                }
                ReviewReason.SIGNATURE_CHANGED -> entry.signatureChangedAt?.let {
                    stringResource(R.string.status_signature_changed_at, formatShortDate(it.toLocalDateTime()))
                }
                else -> null
            }
            if (date != null) {
                Text(text = date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SmallLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 選択中に下部に出す「問題なし」「要注意」「未確認に戻す」。 */
@Composable
private fun BulkActions(onSelect: (ReviewStatus) -> Unit) {
    val colors = AppWatchTheme.colors
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val buttonModifier = Modifier
                .weight(1f)
                .height(56.dp)
            Button(
                onClick = { onSelect(ReviewStatus.OK) },
                modifier = buttonModifier,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.ok, contentColor = colors.onOk),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { BulkLabel(R.string.mark_ok, R.string.status_ok) }
            Button(
                onClick = { onSelect(ReviewStatus.CAUTION) },
                modifier = buttonModifier,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = colors.onDanger),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { BulkLabel(R.string.mark_caution, R.string.status_caution) }
            OutlinedButton(
                onClick = { onSelect(ReviewStatus.UNCONFIRMED) },
                modifier = buttonModifier,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { BulkLabel(R.string.mark_unconfirmed, R.string.status_action_unconfirmed) }
        }
    }
}

@Composable
private fun BulkLabel(mark: Int, label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(mark), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
