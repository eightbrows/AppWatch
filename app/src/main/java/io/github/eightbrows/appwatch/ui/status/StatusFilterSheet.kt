package io.github.eightbrows.appwatch.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
 * 下から出す絞り込みの画面（ほぼ全画面の高さ、中はスクロール）。変更はすぐに一覧に効く。
 * 確認状態の行、区切り線、親子の関係が分かるツリー。下部に「初期値に戻す」と「n件を表示」（押すと閉じる）。
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxHeight()) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.filter_sheet_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.filter_sheet_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            ) {
                GroupLabel(R.string.filter_group_status)
                ChipGroup(FilterGroup.STATUS, enabled, counts, toggle)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                CheckNode(FilterItem.DEVICE_PRESENT, enabled, counts, toggle)
                Branch(dim = FilterItem.DEVICE_PRESENT !in enabled) {
                    GroupLabel(R.string.filter_group_state)
                    ChipGroup(FilterGroup.STATE, enabled, counts, toggle)
                    GroupLabel(R.string.filter_group_kind)
                    CheckNode(FilterItem.KIND_USER, enabled, counts, toggle)
                    Branch(dim = FilterItem.KIND_USER !in enabled && StatusFilter.isActive(FilterItem.KIND_USER, enabled)) {
                        GroupLabel(R.string.filter_group_source)
                        ChipGroup(FilterGroup.SOURCE, enabled, counts, toggle)
                        GroupLabel(R.string.filter_group_warning)
                        ChipGroup(FilterGroup.WARNING, enabled, counts, toggle)
                    }
                    CheckNode(FilterItem.KIND_PREINSTALLED, enabled, counts, toggle)
                    CheckNode(FilterItem.KIND_SYSTEM, enabled, counts, toggle)
                }
                CheckNode(FilterItem.DEVICE_ABSENT, enabled, counts, toggle)
                Branch(dim = FilterItem.DEVICE_ABSENT !in enabled) {
                    CheckNode(FilterItem.ABSENT_REMOVED, enabled, counts, toggle, bold = false)
                    CheckNode(FilterItem.ABSENT_PRE_REGISTERED, enabled, counts, toggle, bold = false)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onChange(StatusFilter.DEFAULT_ENABLED) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text(stringResource(R.string.filter_reset), maxLines = 1) }
                Button(
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp),
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

private val INDENT: Dp = 11.dp
private val LINE_WIDTH: Dp = 2.dp

/** 1段下げた枝。左に細い線で親子を見せる。外した親の下は灰色（押せない）。[dim] は外した親のすぐ下の枝だけ（重ねて薄くしない）。 */
@Composable
private fun Branch(dim: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = INDENT)
            .drawBehind {
                drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = LINE_WIDTH.toPx())
            }
            .padding(start = 12.dp)
            .alpha(if (dim) 0.45f else 1f),
        content = content,
    )
}

/** 組の見出し（状態・種類・入手・警告・確認状態）。 */
@Composable
private fun GroupLabel(res: Int) {
    Text(
        text = stringResource(res),
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** ツリーの親のチェックボックスの行（オン・オフ・一部の3つの見た目）と、右に件数。 */
@Composable
private fun CheckNode(
    item: FilterItem,
    enabled: Set<FilterItem>,
    counts: Map<FilterItem, Int>,
    onToggle: (FilterItem) -> Unit,
    bold: Boolean = true,
) {
    val active = StatusFilter.isActive(item, enabled)
    val state = when (StatusFilter.checkState(item, enabled)) {
        CheckState.ON -> ToggleableState.On
        CheckState.OFF -> ToggleableState.Off
        CheckState.PARTIAL -> ToggleableState.Indeterminate
    }
    val count = counts[item] ?: 0
    // 読み上げ：行の中の名前と件数をまとめて、チェックボックスのオン・オフ・一部と一緒に読む
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .triStateToggleable(state = state, enabled = active, role = Role.Checkbox, onClick = { onToggle(item) })
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = state, onClick = null, enabled = active, modifier = Modifier.clearAndSetSemantics {})
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(item.labelRes()),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        CountPill(count)
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
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 組のチップ（オンは青の枠と薄い青、チェックの印）。文字の後に件数。 */
@Composable
private fun ChipGroup(group: FilterGroup, enabled: Set<FilterItem>, counts: Map<FilterItem, Int>, onToggle: (FilterItem) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterItem.entries.filter { it.group == group }.forEach { item ->
            FilterChipView(item, on = item in enabled, active = StatusFilter.isActive(item, enabled), count = counts[item] ?: 0, onToggle = onToggle)
        }
    }
}

@Composable
private fun FilterChipView(item: FilterItem, on: Boolean, active: Boolean, count: Int, onToggle: (FilterItem) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val spoken = pluralStringResource(R.plurals.filter_count, count, count)
    val content = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (on) scheme.primaryContainer else scheme.surface)
            .border(width = if (on) 1.5.dp else 1.dp, color = if (on) scheme.primary else scheme.outline, shape = shape)
            .toggleable(value = on, enabled = active, role = Role.Checkbox, onValueChange = { onToggle(item) })
            .heightIn(min = 36.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (on) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(14.dp))
        Text(
            text = stringResource(item.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = content,
            maxLines = 1,
        )
        Text(
            text = count.toString(),
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
