package io.github.eightbrows.appwatch.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** つまみをつかんでいる時の吹き出しの中身（組の名前と、一覧の一番上のアプリ名）。 */
data class ScrollLabel(val group: String, val name: String)

/**
 * つかんで動かせるスクロールバーの計算（画面に関係しない。単体テストあり）。
 *
 * - 位置は「一覧の何番目の項目が一番上にあるか」で決める（カードと行で高さが違っても、動きが安定する）。
 *   つまみの長さが見えている割合どおりなら、上端の位置は「一番上の項目の番号 ÷ 全部の数」と同じ。
 *   最低の長さでのばした時は、のばした分を引いた残りの範囲で同じ割合にする（一番上と一番下に必ず届く）。
 * - 長さは見えている項目の割合。短すぎるとつかめないので、最低の長さ（44dp）を下回らない。
 * - 一覧が約2画面に収まる時は出さない。
 */
internal object ScrollbarMath {
    /** これより長い一覧（画面の数）の時だけ出す。 */
    const val SHOW_SCREENS = 2f

    fun shouldShow(total: Int, visibleItems: Float): Boolean =
        total > 0 && visibleItems > 0f && total > visibleItems * SHOW_SCREENS

    /** つまみの長さ（px）。 */
    fun thumbLength(track: Float, total: Int, visibleItems: Float, minLength: Float): Float {
        if (track <= 0f || total <= 0) return 0f
        val raw = track * (visibleItems / total).coerceIn(0f, 1f)
        return raw.coerceIn(min(minLength, track), track)
    }

    /**
     * 今の場所（0 = 一番上、1 = 一番下）。[firstExact] は一番上の項目の番号（途中まで隠れている分を小数で足す）。
     * 一番上・一番下に着いている時は、必ず 0・1 にする。
     */
    fun progress(firstExact: Float, total: Int, visibleItems: Float, atTop: Boolean, atBottom: Boolean): Float = when {
        atTop -> 0f
        atBottom -> 1f
        else -> {
            val range = total - visibleItems
            if (range <= 0f) 0f else (firstExact / range).coerceIn(0f, 1f)
        }
    }

    /** つまみの上端（つまみが動ける溝の上からの px）。 */
    fun thumbTop(progress: Float, track: Float, thumb: Float): Float =
        progress.coerceIn(0f, 1f) * max(track - thumb, 0f)

    /** つまみの上端を [thumbTop] に動かした時に、一覧の一番上に出す項目の番号。 */
    fun targetIndex(thumbTop: Float, track: Float, thumb: Float, total: Int, visibleItems: Float): Int {
        if (total <= 0) return 0
        val range = track - thumb
        val p = if (range <= 0f) 0f else (thumbTop / range).coerceIn(0f, 1f)
        if (p >= 1f) return total - 1
        val index = (p * max(total - visibleItems, 0f)).toInt()
        return index.coerceIn(0, total - 1)
    }
}

/** 一覧の今の様子（見えている項目の数を小数で数える）。 */
private class ListView(val total: Int, val visibleItems: Float, val firstExact: Float, val atTop: Boolean, val atBottom: Boolean)

private fun LazyListState.view(): ListView? {
    val info = layoutInfo
    val total = info.totalItemsCount
    if (total == 0 || info.visibleItemsInfo.isEmpty()) return null
    val start = info.viewportStartOffset
    val end = info.viewportEndOffset
    var visible = 0f
    for (item in info.visibleItemsInfo) {
        if (item.size <= 0) continue
        val shown = min(end, item.offset + item.size) - max(start, item.offset)
        if (shown > 0) visible += shown.toFloat() / item.size
    }
    val firstSize = info.visibleItemsInfo.firstOrNull { it.index == firstVisibleItemIndex }?.size ?: 0
    val firstExact = firstVisibleItemIndex + if (firstSize > 0) firstVisibleItemScrollOffset.toFloat() / firstSize else 0f
    return ListView(total, visible, firstExact, atTop = !canScrollBackward, atBottom = !canScrollForward)
}

/** 溝とつまみの位置（px。溝の上端は [trackStart]）。 */
private class Geometry(val trackStart: Float, val track: Float, val thumb: Float, val thumbTop: Float, val total: Int, val visibleItems: Float)

private fun geometry(state: LazyListState, height: Float, topPad: Float, bottomPad: Float, minThumb: Float): Geometry? {
    val view = state.view() ?: return null
    if (!ScrollbarMath.shouldShow(view.total, view.visibleItems)) return null
    val track = height - topPad - bottomPad
    if (track <= minThumb) return null
    val thumb = ScrollbarMath.thumbLength(track, view.total, view.visibleItems, minThumb)
    val progress = ScrollbarMath.progress(view.firstExact, view.total, view.visibleItems, view.atTop, view.atBottom)
    return Geometry(topPad, track, thumb, ScrollbarMath.thumbTop(progress, track, thumb), view.total, view.visibleItems)
}

/** 見た目の幅・つかめる範囲など。 */
private val TRACK_MARGIN = 6.dp
private val THUMB_WIDTH = 6.dp
private val THUMB_WIDTH_DRAG = 10.dp
private val THUMB_END = 3.dp
private val MIN_THUMB = 44.dp
private val TOUCH_WIDTH = 30.dp
private val TOUCH_MIN_HEIGHT = 48.dp
private const val HIDE_DELAY_MS = 2000L

/**
 * 一覧（[content] の LazyColumn）の右端に、つかんで動かせるスクロールバーを重ねる。トップの両方のタブと確認状態の画面で使う。
 *
 * - 指で一覧を動かしている時に、右端に細い灰色のつまみを出す。指を離して約2秒で消える。一覧が約2画面に収まる時は出さない。
 * - つまみが見えている時だけ、つまみの周り（幅 30dp・高さ 48dp 以上）を押してつかめる。溝のほかの所を押しても何もしない
 *   （一覧の操作のまま）。上下に動かすと、その割合の位置へ一気に飛ぶ。
 * - つかんでいる間は、つまみを太く青くし、左に吹き出し（[labelFor] で組の名前と一番上のアプリ名）を出す。離すと消す。
 * - 読み上げ（TalkBack）には出さない。溝の下端は [bottomInset] だけ上げる（下部のボタン・ナビゲーションバー）。
 *
 * つかんだ指の動きは、一覧より先に受け取って横取りする（一覧のスクロールや行の押下にはしない）。
 */
@Composable
fun ScrollbarBox(
    state: LazyListState,
    labelFor: (key: Any) -> ScrollLabel?,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragThumb by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            shown = true
        } else {
            delay(HIDE_DELAY_MS)
            shown = false
        }
    }
    val topPad = with(density) { TRACK_MARGIN.toPx() }
    val bottomPad = with(density) { (TRACK_MARGIN + bottomInset).toPx() }
    val minThumb = with(density) { MIN_THUMB.toPx() }
    val grabbable by rememberUpdatedState(shown)
    val currentBottomPad by rememberUpdatedState(bottomPad)

    Box(
        modifier
            .onSizeChanged { height = it.height }
            .pointerInput(state, density) {
                val touchWidth = TOUCH_WIDTH.toPx()
                val touchMinHeight = TOUCH_MIN_HEIGHT.toPx()
                awaitEachGesture {
                    // 一覧より先に（Initial）見て、つまみの周りを押した時だけ横取りする
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!grabbable) return@awaitEachGesture
                    val geo = geometry(state, size.height.toFloat(), topPad, currentBottomPad, minThumb)
                        ?: return@awaitEachGesture
                    val center = geo.trackStart + geo.thumbTop + geo.thumb / 2
                    val half = max(geo.thumb, touchMinHeight) / 2
                    val hit = down.position.x >= size.width - touchWidth && down.position.y in (center - half)..(center + half)
                    if (!hit) return@awaitEachGesture
                    down.consume()
                    val startY = down.position.y
                    dragTop = geo.thumbTop
                    dragThumb = geo.thumb
                    dragging = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            val top = (geo.thumbTop + change.position.y - startY).coerceIn(0f, max(geo.track - geo.thumb, 0f))
                            dragTop = top
                            val index = ScrollbarMath.targetIndex(top, geo.track, geo.thumb, geo.total, geo.visibleItems)
                            scope.launch { state.scrollToItem(index) }
                        }
                    } finally {
                        dragging = false
                    }
                }
            },
    ) {
        content()
        if (height > 0) {
            ScrollbarOverlay(
                state = state,
                labelFor = labelFor,
                shown = shown,
                dragging = dragging,
                dragTop = dragTop,
                dragThumb = dragThumb,
                height = height.toFloat(),
                topPad = topPad,
                bottomPad = bottomPad,
                minThumb = minThumb,
            )
        }
    }
}

/**
 * つまみと吹き出し。一覧の動きで毎回描き直すのはここだけ（一覧そのものは描き直さない）。
 * つかんでいる間のつまみは、指の位置（[dragTop]）とつかんだ時の長さ（[dragThumb]）で描く。
 */
@Composable
private fun BoxScope.ScrollbarOverlay(
    state: LazyListState,
    labelFor: (key: Any) -> ScrollLabel?,
    shown: Boolean,
    dragging: Boolean,
    dragTop: Float,
    dragThumb: Float,
    height: Float,
    topPad: Float,
    bottomPad: Float,
    minThumb: Float,
) {
    val density = LocalDensity.current
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 150 else 400), label = "scrollbar")
    val geo by remember(state, height, topPad, bottomPad, minThumb) {
        derivedStateOf { geometry(state, height, topPad, bottomPad, minThumb) }
    }
    // 一覧の一番上の項目のキー（吹き出しの中身）
    val topKey by remember(state) {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == state.firstVisibleItemIndex }?.key }
    }
    val current = geo ?: return
    if (alpha <= 0f) return
    val thumbTop = if (dragging) dragTop else current.thumbTop
    val thumbLength = if (dragging) dragThumb else current.thumb
    val y = current.trackStart + thumbTop
    val width = if (dragging) THUMB_WIDTH_DRAG else THUMB_WIDTH
    val color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset { IntOffset(-(THUMB_END - (width - THUMB_WIDTH) / 2).roundToPx(), y.roundToInt()) }
            .alpha(alpha)
            .size(width, with(density) { thumbLength.toDp() })
            .background(color, RoundedCornerShape(50))
            .clearAndSetSemantics { },
    )
    if (dragging) {
        val label = topKey?.let(labelFor)
        if (label != null) ScrollBubble(label, y, height)
    }
}

/** つまみの左に出す吹き出し（組の名前と、一番上のアプリ名）。画面からはみ出さないように上下を詰める。 */
@Composable
private fun BoxScope.ScrollBubble(label: ScrollLabel, y: Float, boxHeight: Float) {
    var bubbleHeight by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .offset {
                val top = min(y, boxHeight - bubbleHeight).coerceAtLeast(0f)
                IntOffset(-TOUCH_WIDTH.roundToPx(), top.roundToInt())
            }
            .onSizeChanged { bubbleHeight = it.height }
            .widthIn(max = 240.dp)
            .background(
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clearAndSetSemantics { },
    ) {
        Text(
            text = label.group,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

