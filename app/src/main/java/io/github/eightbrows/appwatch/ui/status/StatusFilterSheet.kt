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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.status.FilterGroup
import io.github.eightbrows.appwatch.data.status.FilterItem
import io.github.eightbrows.appwatch.data.status.StatusFilter
import io.github.eightbrows.appwatch.ui.components.conditionNameRes
import io.github.eightbrows.appwatch.ui.components.labelRes
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign

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
 * 下から出す絞り込みの画面（帯と破線）。高さは中身に合わせ、画面の 85% を上限にする（超える時は中をスクロール）。
 *
 * 親の件数と内訳の件数の関係を形で見せる：
 * - 足すと親の件数になる組（確認状態・導入・状態・種類・入手）は、区切り線だけで分けた1本の帯。
 * - 内訳を持つボタン（導入済・未導入・後から導入）の左右の端から破線を下ろし、下を閉じた枠の中に内訳を入れる。
 * - 警告は1つのアプリに複数付くことがあるので、帯にせず、すき間を空けたボタンにする。
 * 変更はすぐに一覧に効く。下部に「初期値に戻す」と「n件を表示」（押すと閉じる）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusFilterSheet(
    enabled: Set<FilterItem>,
    counts: Map<FilterItem, Int>,
    totalCount: Int,
    visibleCount: Int,
    onChange: (Set<FilterItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // つまみとナビゲーションバーを含めて、画面の 85% に収める
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val navigationBar = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val maxHeight = windowHeight * SHEET_MAX_FRACTION - HANDLE_HEIGHT - navigationBar
    val tree = FilterTree(enabled, counts) { onChange(StatusFilter.toggle(enabled, it)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { SmallDragHandle() },
    ) {
        Column(Modifier.heightIn(max = maxHeight)) {
            SheetHeader(
                totalCount = totalCount,
                onAllOn = { onChange(StatusFilter.ALL_ON) },
                onAllOff = { onChange(StatusFilter.ALL_OFF) },
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            ) {
                SectionLabel(R.string.filter_group_status)
                tree.Band(FilterGroup.STATUS)

                SectionLabel(R.string.filter_group_device)
                // 導入済：未導入＝78：22（件数の比ではなく、内訳を持つ方を広くする）
                Row(Modifier.height(IntrinsicSize.Min)) {
                    tree.Segment(FilterItem.DEVICE_PRESENT, Modifier.weight(WEIGHT_PRESENT).fillMaxHeight(), SegmentShape.TopStart, first = true)
                    tree.Segment(FilterItem.DEVICE_ABSENT, Modifier.weight(WEIGHT_ABSENT).fillMaxHeight(), SegmentShape.TopEnd, first = false)
                }
                Row(verticalAlignment = Alignment.Top) {
                    // 導入済の内訳
                    DashedFrame(
                        active = tree.isOn(FilterItem.DEVICE_PRESENT),
                        first = true,
                        modifier = Modifier.weight(WEIGHT_PRESENT),
                        padding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 7.dp),
                    ) {
                        SectionLabel(R.string.filter_group_state)
                        tree.Band(FilterGroup.STATE)
                        SectionLabel(R.string.filter_group_warning)
                        // 警告は重なりがあるので、帯にしない
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterItem.entries.filter { it.group == FilterGroup.WARNING }.forEach { item ->
                                tree.Segment(item, Modifier.weight(1f).fillMaxHeight(), SegmentShape.Alone, first = true)
                            }
                        }
                        SectionLabel(R.string.filter_group_kind)
                        // 後から導入：プリイン：システム＝58：21：21。プリイン・システムは、後から導入と内訳の枠を合わせた高さ
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Column(Modifier.weight(WEIGHT_USER)) {
                                tree.Segment(FilterItem.KIND_USER, Modifier.fillMaxWidth(), SegmentShape.TopStart, first = true)
                                DashedFrame(
                                    active = tree.isOn(FilterItem.KIND_USER) && tree.isOn(FilterItem.DEVICE_PRESENT),
                                    first = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    padding = PaddingValues(start = 5.dp, end = 5.dp, bottom = 6.dp),
                                ) {
                                    SectionLabel(R.string.filter_group_source)
                                    tree.Band(FilterGroup.SOURCE)
                                }
                            }
                            tree.Segment(FilterItem.KIND_PREINSTALLED, Modifier.weight(WEIGHT_OTHER_KIND).fillMaxHeight(), SegmentShape.Middle, first = false)
                            tree.Segment(FilterItem.KIND_SYSTEM, Modifier.weight(WEIGHT_OTHER_KIND).fillMaxHeight(), SegmentShape.End, first = false)
                        }
                    }
                    // 未導入の内訳（幅が狭いので縦に並べる）
                    DashedFrame(
                        active = tree.isOn(FilterItem.DEVICE_ABSENT),
                        first = false,
                        modifier = Modifier.weight(WEIGHT_ABSENT),
                        padding = PaddingValues(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 6.dp),
                    ) {
                        tree.Segment(FilterItem.ABSENT_REMOVED, Modifier.fillMaxWidth(), SegmentShape.Alone, first = true)
                        Spacer(Modifier.height(4.dp))
                        tree.Segment(FilterItem.ABSENT_PRE_REGISTERED, Modifier.fillMaxWidth(), SegmentShape.Alone, first = true)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        .heightIn(min = 46.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text(stringResource(R.string.filter_reset), textAlign = TextAlign.Center) }
                Button(
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 46.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text(
                        pluralStringResource(R.plurals.filter_show, visibleCount, visibleCount),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 絞り込みの画面の高さの上限（画面の高さに対する割合）。 */
private const val SHEET_MAX_FRACTION = 0.85f

// 幅の割合（件数の比ではない。内訳を持つボタンを広くする）
private const val WEIGHT_PRESENT = 78f
private const val WEIGHT_ABSENT = 22f
private const val WEIGHT_USER = 58f
private const val WEIGHT_OTHER_KIND = 21f

/** つまみの部分の高さ（上下の余白を含む）。 */
private val HANDLE_HEIGHT: Dp = 20.dp

/** ボタンの最小の高さ（押せる範囲。となりのボタンとは重ならない）。 */
private val SEGMENT_MIN_HEIGHT: Dp = 40.dp

/** 帯・ボタンの枠の太さと、角の丸み。 */
private val BORDER: Dp = 1.5.dp
private val CORNER: Dp = 9.dp

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

/** 見出しの行：左に「絞り込み」と「合計 n件」、右に「すべてオン」「すべてオフ」（2段の小さいボタン）。 */
@Composable
private fun SheetHeader(totalCount: Int, onAllOn: () -> Unit, onAllOff: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.filter_sheet_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = pluralStringResource(R.plurals.filter_sheet_total, totalCount, totalCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        AllButton(R.string.filter_all_on, R.string.filter_all_on_desc, onAllOn)
        AllButton(R.string.filter_all_off, R.string.filter_all_off_desc, onAllOff)
    }
}

/** 「すべて／オン」「すべて／オフ」の2段の小さいボタン。 */
@Composable
private fun AllButton(labelRes: Int, descriptionRes: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(descriptionRes)
    val shape = RoundedCornerShape(CORNER)
    Box(
        modifier = Modifier
            .heightIn(min = SEGMENT_MIN_HEIGHT)
            .clip(shape)
            .border(BORDER, scheme.primary, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(labelRes),
            style = SegmentNameStyle,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 組の見出し（「確認状態で分ける」など）。 */
@Composable
private fun SectionLabel(res: Int) {
    Text(
        text = stringResource(res),
        modifier = Modifier.padding(top = 3.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * ボタンの名前の字（どの段も同じ大きさ）。字を大きくする設定で折り返す時は、行の長さをそろえる
 * （「開発署／名」ではなく「開発／署名」）。
 */
private val SegmentNameStyle = TextStyle(fontSize = 11.5.sp, lineHeight = 14.sp, lineBreak = LineBreak.Heading)

/** ボタンの件数の字。 */
private val SegmentCountStyle = TextStyle(fontSize = 12.sp, lineHeight = 15.sp)

/** ボタンの形（帯の中の位置で、丸める角が変わる）。 */
private enum class SegmentShape { Start, Middle, End, TopStart, TopEnd, Alone }

private fun SegmentShape.toShape(): Shape = when (this) {
    SegmentShape.Start -> RoundedCornerShape(topStart = CORNER, bottomStart = CORNER)
    SegmentShape.Middle -> RectangleShape
    SegmentShape.End -> RoundedCornerShape(topEnd = CORNER, bottomEnd = CORNER)
    SegmentShape.TopStart -> RoundedCornerShape(topStart = CORNER)
    SegmentShape.TopEnd -> RoundedCornerShape(topEnd = CORNER)
    SegmentShape.Alone -> RoundedCornerShape(CORNER)
}

/** 絞り込みの今の値と、押した時の処理をまとめて持つ（帯・ボタンを描く関数に渡すものを減らす）。 */
private class FilterTree(
    val enabled: Set<FilterItem>,
    val counts: Map<FilterItem, Int>,
    val onToggle: (FilterItem) -> Unit,
) {
    fun isOn(item: FilterItem): Boolean = item in enabled

    /** 足すと親の件数になる組の、つながった帯（同じ幅で分ける）。 */
    @Composable
    fun Band(group: FilterGroup) {
        val items = FilterItem.entries.filter { it.group == group }
        Row(Modifier.height(IntrinsicSize.Min)) {
            items.forEachIndexed { index, item ->
                val shape = when (index) {
                    0 -> SegmentShape.Start
                    items.lastIndex -> SegmentShape.End
                    else -> SegmentShape.Middle
                }
                Segment(item, Modifier.weight(1f).fillMaxHeight(), shape, first = index == 0)
            }
        }
    }

    /**
     * 1つのボタン：名前と件数の2段（上に名前、下にチェックの印と件数）。
     * オンは薄い青の地・青い字・チェックの印。オフは斜線の地・灰色の字・印なし。
     * 親がオフで効かない時は、枠も字も灰色にして押せなくする（オン・オフは覚えたまま）。
     * [first] が false なら、左の枠を左どなりのボタンの右の枠に重ねて、区切り線を1本にする。
     */
    @Composable
    fun Segment(item: FilterItem, modifier: Modifier, shape: SegmentShape, first: Boolean) {
        val scheme = MaterialTheme.colorScheme
        val on = item in enabled
        val active = StatusFilter.isActive(item, enabled)
        val count = counts[item] ?: 0
        val borderColor = if (active) scheme.primary else scheme.outline
        val fill = when {
            !on -> scheme.surface
            active -> scheme.primaryContainer
            else -> scheme.surfaceVariant // 効かないが、オンのまま覚えている
        }
        val content = when {
            !active -> scheme.onSurfaceVariant.copy(alpha = 0.7f)
            on -> scheme.onPrimaryContainer
            else -> scheme.onSurfaceVariant
        }
        val hatch = scheme.surfaceContainerHighest
        val outline = shape.toShape()
        // 読み上げ：「名前、n件、オン／オフ」。効かない時は、その旨も読む
        val name = stringResource(item.conditionNameRes())
        val countText = pluralStringResource(R.plurals.filter_count, count, count)
        val spokenCount = if (active) countText else stringResource(R.string.filter_inactive, countText)
        Column(
            modifier = modifier
                .heightIn(min = SEGMENT_MIN_HEIGHT)
                .segmentBackground(fill, if (on) null else hatch, outline)
                .segmentBorder(borderColor, outline, overlapStart = !first)
                .clip(outline)
                .toggleable(value = on, enabled = active, role = Role.Switch, onValueChange = { onToggle(item) })
                .padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(item.labelRes()),
                modifier = Modifier.clearAndSetSemantics { contentDescription = name },
                style = SegmentNameStyle,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = content,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.clearAndSetSemantics { contentDescription = spokenCount },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (on && active) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = content, modifier = Modifier.size(11.dp))
                }
                Text(text = count.toString(), style = SegmentCountStyle, fontWeight = FontWeight.Bold, color = content)
            }
        }
    }
}

/** ボタンの地。オフは斜線（[hatch] の色）。 */
private fun Modifier.segmentBackground(fill: Color, hatch: Color?, shape: Shape): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    clipPath(path) {
        drawRect(fill)
        if (hatch != null) {
            // 右上がりの斜線（幅 5dp の縞）
            val step = 10.dp.toPx()
            val stroke = 3.5.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(hatch, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stroke)
                x += step
            }
        }
    }
}

/**
 * ボタンの枠。[overlapStart] が true なら、左の辺を左どなりのボタンの右の辺の上に重ねて描き、区切り線を1本にする。
 */
private fun Modifier.segmentBorder(color: Color, shape: Shape, overlapStart: Boolean): Modifier = drawBehind {
    val stroke = BORDER.toPx()
    val shift = if (overlapStart) stroke else 0f
    val outline = shape.createOutline(Size(size.width + shift - stroke, size.height - stroke), layoutDirection, this)
    translate(left = stroke / 2 - shift, top = stroke / 2) {
        drawOutline(outline, color, style = Stroke(width = stroke))
    }
}

/**
 * 内訳の枠：上のボタンの左右の端から縦の破線を下ろし、下を閉じる。枠の幅は上のボタンと同じ。
 * [active] が false（上のボタンがオフ）なら灰色。[first] が false なら、左の破線を左どなりの枠の右の破線に重ねる。
 */
@Composable
private fun DashedFrame(
    active: Boolean,
    first: Boolean,
    modifier: Modifier,
    padding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier
            .drawBehind {
                val stroke = BORDER.toPx()
                val radius = CORNER.toPx()
                val left = if (first) stroke / 2 else -stroke / 2
                val right = size.width - stroke / 2
                val bottom = size.height - stroke / 2
                val path = Path().apply {
                    moveTo(left, 0f)
                    lineTo(left, bottom - radius)
                    quadraticTo(left, bottom, left + radius, bottom)
                    lineTo(right - radius, bottom)
                    quadraticTo(right, bottom, right, bottom - radius)
                    lineTo(right, 0f)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .padding(padding),
        content = content,
    )
}
