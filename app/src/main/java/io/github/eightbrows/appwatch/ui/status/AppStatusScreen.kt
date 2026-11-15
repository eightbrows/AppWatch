package io.github.eightbrows.appwatch.ui.status

import android.content.res.Resources
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.status.AppStatusEntry
import io.github.eightbrows.appwatch.data.status.AppStatusRules
import io.github.eightbrows.appwatch.data.status.StatusFilter
import io.github.eightbrows.appwatch.ui.components.LabelChip
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.data.transfer.DataSelection
import io.github.eightbrows.appwatch.ui.components.AdminTopBar
import io.github.eightbrows.appwatch.ui.components.DataSwitcher
import io.github.eightbrows.appwatch.ui.components.ViewingFooter
import io.github.eightbrows.appwatch.ui.components.StatusMark
import io.github.eightbrows.appwatch.ui.components.AppIconWithMark
import io.github.eightbrows.appwatch.ui.components.ROW_MARK_SIZE
import io.github.eightbrows.appwatch.ui.components.AppSearchField
import io.github.eightbrows.appwatch.ui.components.ReasonChip
import io.github.eightbrows.appwatch.ui.components.DateForm
import io.github.eightbrows.appwatch.ui.components.formatDate
import io.github.eightbrows.appwatch.ui.components.withDaysAgo
import androidx.compose.ui.text.AnnotatedString
import io.github.eightbrows.appwatch.model.AppSearch
import io.github.eightbrows.appwatch.model.ReviewReason
import androidx.compose.ui.graphics.Color
import io.github.eightbrows.appwatch.ui.components.formatShortDate
import io.github.eightbrows.appwatch.ui.components.labelRes
import io.github.eightbrows.appwatch.ui.components.toLocalDateTime
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import io.github.eightbrows.appwatch.ui.top.HeaderShadow
import io.github.eightbrows.appwatch.ui.components.SectionHeading
import io.github.eightbrows.appwatch.ui.components.sectionTone
import io.github.eightbrows.appwatch.ui.components.ScrollLabel
import io.github.eightbrows.appwatch.ui.components.ScrollbarBox
import io.github.eightbrows.appwatch.ui.components.shownAppName
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.Dp

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
    // 下から出す絞り込みの画面を開いているか
    var showFilter by rememberSaveable { mutableStateOf(false) }
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
    // 絞り込みの各項目の件数（ほかの条件・検索に関係しない）
    val counts = remember(currentEntries) { StatusFilter.counts(currentEntries.orEmpty()) }
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
                    changedCount = StatusFilter.changedCount(currentEnabled),
                    query = query,
                    selected = selectedVisible.mapTo(HashSet()) { it.packageName },
                    onQueryChange = { query = it },
                    onPreRegister = onPreRegister,
                    onOpenFilter = { showFilter = true },
                    onToggleSelect = { packageName ->
                        selected = if (packageName in selected) selected - packageName else selected + packageName
                    },
                    onSelectAllVisible = { selected = (selected + visible.map { it.packageName }).distinct() },
                    onClearSelection = { selected = emptyList() },
                    onOpenDetail = onOpenDetail,
                    // 下部の3つのボタンがある時は、一覧の枠がその上で終わる。ない時はナビゲーションバーの上で止める
                    scrollbarBottomInset = if (selectedVisible.isEmpty() && !readOnly) {
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    } else {
                        0.dp
                    },
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
        if (showFilter && currentEnabled != null) {
            StatusFilterSheet(
                enabled = currentEnabled,
                counts = counts,
                totalCount = currentEntries.orEmpty().size,
                visibleCount = visible.size,
                onChange = controller::setFilterEnabled,
                onDismiss = { showFilter = false },
            )
        }
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

@Composable
private fun StatusList(
    visible: List<AppStatusEntry>,
    selection: DataSelection?,
    onSelectDataset: (String) -> Unit,
    readOnly: Boolean,
    changedCount: Int,
    query: String,
    selected: Set<String>,
    onQueryChange: (String) -> Unit,
    onPreRegister: () -> Unit,
    onOpenFilter: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenDetail: (String) -> Unit,
    scrollbarBottomInset: Dp,
) {
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        // 上部（データの切り替え・検索の行・選択の帯）。一覧をスクロールしても動かない。高さ・並びは今のまま
        Column(Modifier.padding(horizontal = 16.dp)) {
            // データの切り替え（この端末／取り込んだデータ）と、閲覧中の帯
            if (selection != null && selection.showsSwitcher) {
                DataSwitcher(selection, onSelectDataset, Modifier.padding(top = 4.dp, bottom = 8.dp))
            }
            SearchRow(query, onQueryChange, onPreRegister, changedCount, onOpenFilter, preRegisterEnabled = !readOnly)
            Spacer(Modifier.height(8.dp))
            // 表示の件数と、選択の帯（閲覧中は選べないので、件数だけ）
            SelectionBar(
                visibleCount = visible.size,
                selectedCount = selected.size,
                showActions = !readOnly,
                onSelectAllVisible = onSelectAllVisible,
                onClearSelection = onClearSelection,
            )
        }
        val scrollLabels = statusScrollLabels(visible)
        // 右端につかんで動かせるスクロールバー（トップと同じ部品）。チェックの範囲・行と重ならないよう、右の余白を 12dp 広げる
        ScrollbarBox(
            state = listState,
            labelFor = { scrollLabels[it] },
            modifier = Modifier.weight(1f),
            bottomInset = scrollbarBottomInset,
        ) {
            StatusRows(visible, selected, readOnly, onToggleSelect, onOpenDetail, listState)
            // 一覧がスクロールされている時は、上部の下に薄い影を付けて区切る
            HeaderShadow(visible = listState.canScrollBackward)
        }
    }
}

/** スクロールバーの吹き出しに出す、一覧の項目（キー）ごとの組の名前（確認状態）とアプリ名。見出しはその組の最初のアプリ。 */
@Composable
private fun statusScrollLabels(visible: List<AppStatusEntry>): Map<Any, ScrollLabel> {
    val selfName = stringResource(R.string.app_name)
    val statusNames = ReviewStatus.entries.associateWith { stringResource(it.labelRes()) }
    return remember(visible, selfName, statusNames) {
        val map = HashMap<Any, ScrollLabel>()
        AppStatusRules.sections(visible).forEach { (status, entries) ->
            val group = statusNames.getValue(status)
            entries.firstOrNull()?.let { map["section_" + status.name] = ScrollLabel(group, shownAppName(it.name, it.isSelf, selfName)) }
            entries.forEach { map[it.packageName] = ScrollLabel(group, shownAppName(it.name, it.isSelf, selfName)) }
        }
        map
    }
}

/** 一覧の行（上部の下を流れる）。 */
@Composable
private fun StatusRows(
    visible: List<AppStatusEntry>,
    selected: Set<String>,
    readOnly: Boolean,
    onToggleSelect: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 28.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        // 確認状態の組ごとに見出し（要注意 → 未確認 → 問題なし）。件数は絞り込み・検索の後の数。閲覧中も同じ
        AppStatusRules.sections(visible).forEach { (status, entries) ->
            item(key = "section_" + status.name) {
                SectionHeading(stringResource(status.labelRes()), entries.size, status.sectionTone())
            }
            items(entries, key = { it.packageName }) { entry ->
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
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onPreRegister: () -> Unit,
    changedCount: Int,
    onOpenFilter: () -> Unit,
    preRegisterEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppSearchField(query = query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f), shortHint = true)
        FilterButton(changedCount = changedCount, onClick = onOpenFilter)
        // 事前登録は幅の都合で「＋」だけ（読み上げは「事前登録」）
        Button(
            onClick = onPreRegister,
            enabled = preRegisterEnabled,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.status_pre_register),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SelectionBar(
    visibleCount: Int,
    selectedCount: Int,
    showActions: Boolean,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
) {
    // 上部に固定して置く（上下 4dp の余白を含む。今までと同じ高さ）
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
                .heightIn(min = 48.dp)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：選んでいない時は「表示 n件」、選んでいる時は「n件選択中」
            Text(
                text = if (selectedCount > 0) {
                    pluralStringResource(R.plurals.status_selected_count, selectedCount, selectedCount)
                } else {
                    pluralStringResource(R.plurals.filter_summary_total, visibleCount, visibleCount)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
            if (showActions) {
                TextButton(onClick = onSelectAllVisible, enabled = visibleCount > 0, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.status_select_all_visible), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                TextButton(onClick = onClearSelection, enabled = selectedCount > 0, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.status_clear_selection), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/**
 * 一覧の1行。未導入のアプリは灰色。
 * 選べる時は、行の左端からアイコンの右端まで（上から下まで）を押すと選択のオン・オフ、右の名前などを押すとアプリの詳細を開く。
 * 2つの範囲は重ならず、押した時の波紋もそれぞれの範囲だけに出る。閲覧中（選べない時）は、行のどこを押しても詳細を開く。
 */
@Composable
private fun StatusRow(entry: AppStatusEntry, isSelected: Boolean, showCheckbox: Boolean, onToggleSelect: () -> Unit, onClick: () -> Unit) {
    val rowColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = rowColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (showCheckbox) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        if (showCheckbox) {
            val selectLabel = stringResource(R.string.status_select_app, shownAppName(entry.name, entry.isSelf))
            SplitRow(
                left = {
                    // 左の範囲：チェックとアイコン。読み上げは今のチェックボックスと同じ（アプリの名前、選択／未選択）
                    Row(
                        modifier = Modifier
                            .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onToggleSelect() })
                            .clearAndSetSemantics {
                                contentDescription = selectLabel
                                role = Role.Checkbox
                                toggleableState = ToggleableState(isSelected)
                                onClick { onToggleSelect(); true }
                            }
                            .padding(top = 4.dp, bottom = 12.dp, end = 6.dp),
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        RowIcon(entry = entry, loadIcon = true, rowColor = rowColor, modifier = Modifier.padding(top = 10.dp))
                    }
                },
                right = {
                    // 右の範囲：名前など。押すと詳細を開く（確認状態は左のアイコンの印にあるので、ここで読み上げる）
                    Box(
                        Modifier
                            .clickable(onClick = onClick)
                            .padding(top = 14.dp, end = 12.dp, bottom = 12.dp),
                    ) {
                        StatusRowText(entry, statusForReader = true)
                    }
                },
            )
        } else {
            Row(Modifier.padding(start = 14.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)) {
                RowIcon(entry = entry, loadIcon = false, rowColor = rowColor, modifier = Modifier.padding(top = 10.dp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).padding(top = 10.dp)) { StatusRowText(entry, statusForReader = false) }
            }
        }
    }
}

/**
 * 行を左右の2つの範囲に分ける。右の高さ（名前・札など。折り返しで変わる）を先に決めてから、
 * 左（チェックとアイコン）を同じ高さにのばす。どちらも行の上から下まで押せる。
 */
@Composable
private fun SplitRow(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Layout(contents = listOf(left, right)) { (leftMeasurables, rightMeasurables), constraints ->
        val leftMeasurable = leftMeasurables.single()
        val rightMeasurable = rightMeasurables.single()
        val width = constraints.maxWidth
        val leftWidth = leftMeasurable.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(width)
        val leftMinHeight = leftMeasurable.minIntrinsicHeight(leftWidth)
        val rightWidth = width - leftWidth
        val rightPlaceable = rightMeasurable.measure(
            Constraints(minWidth = rightWidth, maxWidth = rightWidth, minHeight = leftMinHeight),
        )
        val height = rightPlaceable.height
        val leftPlaceable = leftMeasurable.measure(Constraints.fixed(leftWidth, height))
        layout(width, height) {
            leftPlaceable.place(0, 0)
            rightPlaceable.place(leftWidth, 0)
        }
    }
}

/**
 * 行の右側（アプリ名・バージョン・パッケージ名・入手元・札・管理者メモ）。
 * [statusForReader] が true なら、確認状態を読み上げだけに足す（アイコンの印が左の範囲に入って読まれないため）。
 */
@Composable
private fun StatusRowText(entry: AppStatusEntry, statusForReader: Boolean) {
    val dim = !entry.isPresent
    val mainColor = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant
    val statusLabel = stringResource(entry.status.labelRes())
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (statusForReader) Box(Modifier.clearAndSetSemantics { contentDescription = statusLabel })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = shownAppName(entry.name, entry.isSelf),
                style = MaterialTheme.typography.titleMedium,
                color = mainColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (entry.isSelf) SmallLabel(stringResource(R.string.app_self_label))
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
        val reasons = ReviewReason.of(entry.warnings, entry.status, includeCaution = false, isSelf = entry.isSelf)
        SourceLine(entry, withSource = !ReviewReason.hidesSourceText(reasons))
        RowWarnings(entry, reasons)
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

/** 行の先頭のアイコンと、右下の角の確認状態の印（共通の部品 AppIconWithMark）。無効にしたアプリは灰色で薄く出す。 */
@Composable
private fun RowIcon(entry: AppStatusEntry, loadIcon: Boolean, rowColor: Color, modifier: Modifier = Modifier) {
    AppIconWithMark(
        name = shownAppName(entry.name, entry.isSelf),
        packageName = entry.packageName,
        versionKey = entry.versionName.orEmpty(),
        present = entry.isPresent,
        loadIcon = loadIcon,
        dimmed = !entry.isEnabled,
        ringColor = rowColor,
        modifier = modifier,
    ) {
        StatusMark(status = entry.status, size = ROW_MARK_SIZE)
    }
}

/**
 * 入手元・日時（後から導入）、種類（プリインストール・システム）、端末にないこと。警告は下の札で出す。
 * [withSource] が false（入手元の札を出す行）なら、入手元を外して日時だけにする。
 */
@Composable
private fun SourceLine(entry: AppStatusEntry, withSource: Boolean) {
    val removedAt = entry.removedAt
    val kind = entry.kind
    val source = entry.source
    // 文と、「何日前」を添える日時（添えないときは null）
    val (text, agoTime) = when {
        !entry.isPresent && removedAt != null ->
            stringResource(R.string.status_row_removed, formatShortDate(removedAt.toLocalDateTime())) to removedAt
        !entry.isPresent && kind == null -> stringResource(R.string.status_row_not_installed) to null
        kind == AppKind.USER_INSTALLED && source != null -> {
            val installed = entry.firstInstallTime?.takeIf { it > 0 }
            val date = installed?.let { formatDate(it, DateForm.LIST) }
            when {
                !withSource -> date to installed
                date != null -> stringResource(R.string.status_row_source_date, stringResource(source.labelRes()), date) to installed
                else -> stringResource(source.labelRes()) to null
            }
        }
        kind != null -> stringResource(kind.labelRes()) to null
        else -> null to null
    }
    if (text != null) {
        Text(
            text = if (agoTime != null) withDaysAgo(text, agoTime) else AnnotatedString(text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 警告の札（トップ・アプリの詳細と同じ見た目）と、再インストール・署名の変更を見つけた日。
 * 確認状態は印で出しているので札にしない。本アプリ自身には札を出さない（[reasons] が空）。
 */
@Composable
private fun RowWarnings(entry: AppStatusEntry, reasons: List<ReviewReason>) {
    val disabled = entry.isPresent && !entry.isEnabled
    val preRegisteredOnly = StatusFilter.isPreRegisteredOnly(entry)
    if (reasons.isEmpty() && !disabled && !preRegisteredOnly) return
    FlowRow(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // 無効（濃い灰色に白い字。トップ・アプリの詳細と同じ）・事前登録だけ（青）の札。警告の札と同じ形
        if (disabled) {
            LabelChip(
                text = stringResource(R.string.app_disabled_label),
                container = AppWatchTheme.colors.unconfirmed,
                content = Color.White,
            )
        }
        if (preRegisteredOnly) {
            LabelChip(
                text = stringResource(R.string.status_badge_pre_registered),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
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
