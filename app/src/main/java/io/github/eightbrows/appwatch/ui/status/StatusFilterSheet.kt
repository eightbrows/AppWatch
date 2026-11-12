package io.github.eightbrows.appwatch.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.status.CheckState
import io.github.eightbrows.appwatch.data.status.FilterCondition
import io.github.eightbrows.appwatch.data.status.FilterGroup
import io.github.eightbrows.appwatch.data.status.FilterItem
import io.github.eightbrows.appwatch.data.status.StatusFilter
import io.github.eightbrows.appwatch.ui.components.conditionNameRes
import io.github.eightbrows.appwatch.ui.components.labelRes
import kotlinx.coroutines.launch
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.sp

// アプリの確認状態の画面の絞り込み（仕様書 6章）：検索の右の「絞り込み」ボタン、一覧の上の条件の1行、下から出す絞り込みの画面。

/** 検索の右の「絞り込み」ボタン。初期値と違う条件の数を小さな丸の数字で出す（0なら出さない）。 */
@Composable
internal fun FilterButton(changedCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.filter_button)
    val description = if (changedCount > 0) {
        pluralStringResource(R.plurals.filter_button_desc, changedCount, changedCount)
    } else {
        label
    }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.primaryContainer)
            .border(1.5.dp, scheme.primary, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer, maxLines = 1)
        if (changedCount > 0) {
            Box(
                modifier = Modifier
                    .heightIn(min = 18.dp)
                    .clip(CircleShape)
                    .background(scheme.primary)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(changedCount.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
            }
        }
    }
}

/**
 * 一覧の上の条件の1行（折り返しあり）：「表示 n件」と、初期値と違う条件を外せる札。札が2つ以上なら「初期値に戻す」。
 * 初期値の「システムを除く」も出す（✕で外すとシステムも出る）。
 */
@Composable
internal fun ConditionLine(
    total: Int,
    conditions: List<FilterCondition>,
    onClear: (FilterCondition) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.filter_summary_total, total, total),
            modifier = Modifier.padding(end = 2.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        conditions.forEach { condition -> ConditionChip(condition, onClear = { onClear(condition) }) }
        if (conditions.size >= 2) {
            TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.heightIn(min = 32.dp)) {
                Text(stringResource(R.string.filter_reset), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 条件の文：「〜を除く」「〜だけ」「〜を含む」。 */
@Composable
internal fun conditionText(condition: FilterCondition): String {
    val name = stringResource(condition.item.conditionNameRes())
    return when (condition.type) {
        FilterCondition.Type.EXCLUDE -> stringResource(R.string.filter_exclude, name)
        FilterCondition.Type.ONLY -> stringResource(R.string.filter_only, name)
        FilterCondition.Type.INCLUDE -> stringResource(R.string.filter_include, name)
    }
}

/** 外せる札（✕で、その条件だけを初期値に戻す）。 */
@Composable
private fun ConditionChip(condition: FilterCondition, onClear: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val text = conditionText(condition)
    val removeLabel = stringResource(R.string.filter_condition_remove, text)
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(scheme.primaryContainer)
            .border(1.dp, scheme.primary.copy(alpha = 0.5f), shape)
            .clickable(role = Role.Button, onClick = onClear)
            .clearAndSetSemantics { contentDescription = removeLabel }
            .heightIn(min = 32.dp)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer, maxLines = 1)
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(scheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * 下から出す絞り込みの画面（案A：1行に詰めたツリー）。高さは中身に合わせ、画面の6割ほどを上限にする（超える時は中をスクロール）。
 * 上に一覧が見えるので、変えた結果がすぐ分かる（変更はすぐに一覧に効く）。下部に「初期値に戻す」と「n件を表示」（押すと閉じる）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusFilterSheet(
    enabled: Set<FilterItem>,
    counts: Map<FilterItem, Int>,
    visibleCount: Int,
    onChange: (Set<FilterItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val toggle: (FilterItem) -> Unit = { onChange(StatusFilter.toggle(enabled, it)) }
    // つまみとナビゲーションバーを含めて、画面の6割ほどに収める
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val navigationBar = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val maxHeight = windowHeight * SHEET_MAX_FRACTION - HANDLE_HEIGHT - navigationBar
    val dimColor = MaterialTheme.colorScheme.outlineVariant
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { SmallDragHandle() },
    ) {
        Column(Modifier.heightIn(max = maxHeight)) {
            // 見出しと、同じ行に小さく説明
            Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.filter_sheet_title),
                    modifier = Modifier
                        .alignByBaseline()
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.filter_sheet_hint),
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    // 一番上の行の押せる範囲（上に 4dp はみ出す）がスクロールの枠で切れないように、上に余白
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
            ) {
                LabeledChips(R.string.filter_group_status, FilterGroup.STATUS, enabled, counts, toggle)
                HorizontalDivider(Modifier.padding(vertical = 2.dp), color = dimColor)

                CheckNode(FilterItem.DEVICE_PRESENT, enabled, counts, toggle)
                Branch(dim = FilterItem.DEVICE_PRESENT !in enabled) {
                    LabeledChips(R.string.filter_group_state, FilterGroup.STATE, enabled, counts, toggle)
                    CheckNode(FilterItem.KIND_USER, enabled, counts, toggle)
                    Branch(dim = FilterItem.KIND_USER !in enabled && StatusFilter.isActive(FilterItem.KIND_USER, enabled)) {
                        LabeledChips(R.string.filter_group_source, FilterGroup.SOURCE, enabled, counts, toggle)
                        LabeledChips(R.string.filter_group_warning, FilterGroup.WARNING, enabled, counts, toggle)
                    }
                    // プリインストールとシステムは1行に2つ
                    NodePair(FilterItem.KIND_PREINSTALLED, FilterItem.KIND_SYSTEM, enabled, counts, toggle)
                }
                HorizontalDivider(Modifier.padding(vertical = 2.dp), color = dimColor)
                CheckNode(FilterItem.DEVICE_ABSENT, enabled, counts, toggle)
                Branch(dim = FilterItem.DEVICE_ABSENT !in enabled) {
                    NodePair(FilterItem.ABSENT_REMOVED, FilterItem.ABSENT_PRE_REGISTERED, enabled, counts, toggle, bold = false)
                }
            }
            HorizontalDivider(color = dimColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onChange(StatusFilter.DEFAULT_ENABLED) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text(stringResource(R.string.filter_reset), maxLines = 1) }
                Button(
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text(
                        pluralStringResource(R.plurals.filter_show, visibleCount, visibleCount),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 絞り込みの画面の高さの上限（画面の高さに対する割合）。 */
private const val SHEET_MAX_FRACTION = 0.6f

/** つまみの部分の高さ（上下の余白を含む）。 */
private val HANDLE_HEIGHT: Dp = 20.dp

/** 小さなつまみ（標準のつまみは上下の余白が大きいので、高さを抑える）。 */
@Composable
private fun SmallDragHandle() {
    Box(Modifier.height(HANDLE_HEIGHT), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
        )
    }
}

/** 1段の字下げ（親子は字下げだけで見せる）。 */
private val INDENT: Dp = 18.dp

/** 押せる範囲の最小の高さ（見た目のチップ・チェックボックスはこれより小さい）。 */
private val TOUCH_MIN: Dp = 40.dp

/** 1行の高さ（並びの間隔）。押せる範囲は上下にはみ出して [TOUCH_MIN] を保つ。 */
private val ROW_HEIGHT: Dp = 32.dp

/**
 * 押せる範囲（高さ [TOUCH_MIN]）はそのままに、並びの上では [ROW_HEIGHT] の高さとして扱う。
 * はみ出した分（上下 4dp）は隣の行と重なるが、どちらかが押せる。
 */
private fun Modifier.compactRow(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val height = maxOf(ROW_HEIGHT.roundToPx(), placeable.height - (TOUCH_MIN - ROW_HEIGHT).roundToPx())
    layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
}

/** 1段下げた枝。外した親の下は灰色（押せない）。[dim] は外した親のすぐ下の枝だけ（重ねて薄くしない）。 */
@Composable
private fun Branch(dim: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = INDENT)
            .alpha(if (dim) 0.4f else 1f),
        content = content,
    )
}

/** 左に小さな見出し（幅をそろえる）、右にチップ（入らなければ折り返す）。 */
@Composable
private fun LabeledChips(
    labelRes: Int,
    group: FilterGroup,
    enabled: Set<FilterItem>,
    counts: Map<FilterItem, Int>,
    onToggle: (FilterItem) -> Unit,
) {
    Row {
        Box(
            modifier = Modifier
                .width(dimensionResource(R.dimen.filter_label_width))
                .height(ROW_HEIGHT),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterItem.entries.filter { it.group == group }.forEach { item ->
                FilterChipView(item, on = item in enabled, active = StatusFilter.isActive(item, enabled), count = counts[item] ?: 0, onToggle = onToggle)
            }
        }
    }
}

/** 親のチェックボックスを1行に2つ（プリインストールとシステム、削除したと事前登録だけ）。入らなければ折り返す。 */
@Composable
private fun NodePair(
    first: FilterItem,
    second: FilterItem,
    enabled: Set<FilterItem>,
    counts: Map<FilterItem, Int>,
    onToggle: (FilterItem) -> Unit,
    bold: Boolean = true,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CheckNode(first, enabled, counts, onToggle, bold)
        CheckNode(second, enabled, counts, onToggle, bold)
    }
}

/**
 * ツリーの親のチェックボックス（18dp、オン・オフ・一部の3つの見た目）と名前・件数。
 * 見た目は小さく、並びの上では高さ 32dp。押せる範囲は高さ 40dp（上下にはみ出す）。
 */
@Composable
private fun CheckNode(
    item: FilterItem,
    enabled: Set<FilterItem>,
    counts: Map<FilterItem, Int>,
    onToggle: (FilterItem) -> Unit,
    bold: Boolean = true,
) {
    val active = StatusFilter.isActive(item, enabled)
    val checkState = StatusFilter.checkState(item, enabled)
    val state = when (checkState) {
        CheckState.ON -> ToggleableState.On
        CheckState.OFF -> ToggleableState.Off
        CheckState.PARTIAL -> ToggleableState.Indeterminate
    }
    // 読み上げ：名前と件数をまとめて、チェックボックスのオン・オフ・一部と一緒に読む
    Row(
        modifier = Modifier
            .compactRow()
            .clip(RoundedCornerShape(8.dp))
            .triStateToggleable(state = state, enabled = active, role = Role.Checkbox, onClick = { onToggle(item) })
            .heightIn(min = TOUCH_MIN)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallCheckBox(checkState)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(item.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.width(6.dp))
        CountPill(counts[item] ?: 0)
    }
}

/** 18dp のチェックボックスの見た目（押す処理と読み上げは行の側）。一部だけオンは「−」。 */
@Composable
private fun SmallCheckBox(state: CheckState) {
    val scheme = MaterialTheme.colorScheme
    val filled = state != CheckState.OFF
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (filled) scheme.primary else Color.Transparent)
            .border(2.dp, if (filled) scheme.primary else scheme.onSurfaceVariant, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            CheckState.ON -> Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
            CheckState.PARTIAL -> Box(
                Modifier
                    .size(width = 8.dp, height = 2.5.dp)
                    .background(scheme.onPrimary, RoundedCornerShape(2.dp)),
            )
            CheckState.OFF -> Unit
        }
    }
}

@Composable
private fun CountPill(count: Int) {
    val spoken = pluralStringResource(R.plurals.filter_count, count, count)
    Text(
        text = count.toString(),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 組のチップ（オンは青の枠と薄い青、チェックの印）。文字は 12sp、件数は少し小さく。
 * 見た目は高さ 24dp ほど、並びの上では高さ 32dp。押せる範囲は高さ 40dp（上下の余白も押せる）。
 */
@Composable
private fun FilterChipView(item: FilterItem, on: Boolean, active: Boolean, count: Int, onToggle: (FilterItem) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val spoken = pluralStringResource(R.plurals.filter_count, count, count)
    val content = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .compactRow()
            .toggleable(value = on, enabled = active, role = Role.Checkbox, interactionSource = interaction, indication = null, onValueChange = { onToggle(item) })
            .heightIn(min = TOUCH_MIN),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(if (on) scheme.primaryContainer else scheme.surface)
                .border(width = if (on) 1.5.dp else 1.dp, color = if (on) scheme.primary else scheme.outline, shape = shape)
                .indication(interaction, ripple())
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (on) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(11.dp))
            Text(
                text = stringResource(item.labelRes()),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = content,
                maxLines = 1,
            )
            Text(
                text = count.toString(),
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
