package io.github.eightbrows.appwatch.ui.top

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.dummy.DummyData
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.RemoveAction
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.model.removeAction
import io.github.eightbrows.appwatch.ui.components.AppIcon
import io.github.eightbrows.appwatch.ui.components.AppSearchField
import io.github.eightbrows.appwatch.ui.components.ReasonChips
import io.github.eightbrows.appwatch.ui.components.ReasonExplanationSheet
import io.github.eightbrows.appwatch.ui.components.withDaysAgo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.stateDescription
import io.github.eightbrows.appwatch.model.ReviewReason
import io.github.eightbrows.appwatch.ui.components.StatusMark
import io.github.eightbrows.appwatch.ui.components.formatDateTime
import io.github.eightbrows.appwatch.ui.components.labelRes
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import java.time.LocalDateTime
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import io.github.eightbrows.appwatch.ui.components.DateForm
import io.github.eightbrows.appwatch.ui.components.toEpochMillis
import io.github.eightbrows.appwatch.ui.components.formatDate
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import io.github.eightbrows.appwatch.ui.components.SectionHeading
import io.github.eightbrows.appwatch.ui.components.SectionTone

private enum class TopTab { NEEDS_REVIEW, ALL }

/** トップ画面のボタンを押したときの処理。 */
data class TopActions(
    val onOpenSettings: () -> Unit,
    /** 削除の確認を出す（その後に Android の削除の確認） */
    val onDelete: (AppSummary) -> Unit,
    /** Android のアプリ情報を開く */
    val onOpenAppInfo: (AppSummary) -> Unit,
    /** アプリの詳細を開く（カード・行を押したとき、「詳しく」） */
    val onShowDetails: (AppSummary) -> Unit,
    val onRetry: () -> Unit,
    /** 動作チェックの画面を開く（押すだけで開く） */
    val onOpenHealthCheck: () -> Unit = {},
    /** 札を押したときに注意の説明を開く（TopScreen の中で決める） */
    val onExplainReasons: (AppSummary) -> Unit = {},
)

/**
 * トップ（本人）。画面サンプル 01_top.png。
 * [lists] が null なのは、まだ一度もアプリ一覧を取得できていないとき。
 */
@Composable
fun TopScreen(
    lists: TopLists?,
    isLoading: Boolean,
    loadFailed: Boolean,
    lastCheckAt: LocalDateTime?,
    actions: TopActions,
    modifier: Modifier = Modifier,
    /** 動作チェックが「確認が必要」か（動作チェックのボタンにオレンジの点を付ける） */
    healthNeedsAttention: Boolean = false,
) {
    var tab by rememberSaveable { mutableStateOf(TopTab.NEEDS_REVIEW) }
    // 注意の説明を出しているアプリ（パッケージ名）
    var explainFor by rememberSaveable { mutableStateOf<String?>(null) }
    val cardActions = remember(actions) { actions.copy(onExplainReasons = { explainFor = it.packageName }) }
    // 検索の文字は保存しない（画面を離れたら空に戻す）
    var query by remember { mutableStateOf("") }
    val shown = remember(lists, query) { lists?.filtered(query) }
    val searching = query.isNotBlank()
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        // 上部（タイトル・検索・タブ）。一覧をスクロールしても動かない。
        // 各段の高さと間は、確認状態の画面の上部（題の行・検索の行・選択の帯）と同じ
        Column(
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = systemBars.calculateTopPadding() + 4.dp,
            ),
        ) {
            TopHeader(
                lastCheckAt = lastCheckAt,
                isLoading = isLoading,
                healthNeedsAttention = healthNeedsAttention,
                onOpenHealthCheck = actions.onOpenHealthCheck,
                onOpenSettings = actions.onOpenSettings,
            )
            Spacer(Modifier.height(8.dp))
            AppSearchField(query = query, onQueryChange = { query = it }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TopTabs(
                selected = tab,
                needsReviewCount = shown?.needsReviewCount,
                allCount = shown?.all?.size,
                onSelect = { tab = it },
            )
            // 取り直し中は、前回の一覧を出したまま細い棒で知らせる
            if (isLoading && lists != null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = systemBars.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                topListContent(shown, loadFailed, tab, searching, cardActions, actions.onRetry)
            }
            // 一覧がスクロールされている時は、上部の下に薄い影を付けて区切る
            HeaderShadow(visible = listState.canScrollBackward)
        }
    }

    // 札を押したときの注意の説明（トップに出している札と同じ理由にチェック）
    val explained = explainFor?.let { pkg -> lists?.all?.firstOrNull { it.packageName == pkg } }
    if (explained != null) {
        ReasonExplanationSheet(
            appName = explained.name,
            applying = ReviewReason.forTop(explained),
            onDismiss = { explainFor = null },
        )
    }
}

/** トップの一覧の中身（読み込み中・失敗・タブごとの一覧）。 */
private fun LazyListScope.topListContent(
    shown: TopLists?,
    loadFailed: Boolean,
    tab: TopTab,
    searching: Boolean,
    actions: TopActions,
    onRetry: () -> Unit,
) {
    when {
        shown == null && loadFailed -> item(key = "failed") { LoadFailed(onRetry) }
        shown == null -> item(key = "loading") { FirstLoading() }
        else -> {
            if (loadFailed) item(key = "refresh_failed") { LoadFailed(onRetry) }
            when (tab) {
                TopTab.NEEDS_REVIEW -> needsReviewContent(shown, searching, actions)
                TopTab.ALL -> allContent(shown, searching, actions)
            }
        }
    }
}

/**
 * トップのタブ：「確認が必要 n」「すべて n」。数は小さな丸い札（確認が必要はオレンジ、0件と「すべて」は灰色）。
 * 高さは確認状態の画面の選択の帯と同じ（上下 4dp の余白と、最低 48dp の帯）。文字が大きい時は帯がのびる。
 * 読み上げは「確認が必要、n件」と、選択中かどうか。
 */
@Composable
private fun TopTabs(selected: TopTab, needsReviewCount: Int?, allCount: Int?, onSelect: (TopTab) -> Unit) {
    val colors = AppWatchTheme.colors
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.segmentTrack)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TopTabItem(
            label = stringResource(R.string.top_tab_needs_review),
            count = needsReviewCount,
            countColor = if ((needsReviewCount ?: 0) > 0) colors.warningStrong else colors.unconfirmed,
            selected = selected == TopTab.NEEDS_REVIEW,
            onClick = { onSelect(TopTab.NEEDS_REVIEW) },
            modifier = Modifier.weight(1f),
        )
        TopTabItem(
            label = stringResource(R.string.top_tab_all),
            count = allCount,
            countColor = colors.unconfirmed,
            selected = selected == TopTab.ALL,
            onClick = { onSelect(TopTab.ALL) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TopTabItem(
    label: String,
    count: Int?,
    countColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = AppWatchTheme.colors
    val description = if (count != null) {
        pluralStringResource(R.plurals.top_tab_desc, count, label, count)
    } else {
        label
    }
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(if (selected) Modifier.shadow(1.dp, shape).background(colors.segmentSelected, shape) else Modifier)
            .clip(shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Tab
                this.selected = selected
                onClick { onClick(); true }
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    modifier = Modifier
                        .background(countColor, RoundedCornerShape(50))
                        .padding(horizontal = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 一覧がスクロールされている時に、上部の下に出す薄い影。 */
@Composable
internal fun BoxScope.HeaderShadow(visible: Boolean) {
    if (!visible) return
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(6.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent))),
    )
}

/** 一覧が空のときの文。検索しているときは「当てはまるアプリはありません」。 */
private fun LazyListScope.emptyMessage(text: Int) {
    item(key = "nothing") {
        Text(
            text = stringResource(text),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 「確認が必要」のタブ：3つの組に分け、それぞれの前に見出し（要注意のアプリ／新しく入った・変わったアプリ／まだ確認していないアプリ）。
 * 0件の組は見出しごと出さない。3つの件数の合計はタブの数と同じ。
 */
private fun LazyListScope.needsReviewContent(lists: TopLists, searching: Boolean, actions: TopActions) {
    if (lists.cards.isEmpty() && lists.unconfirmed.isEmpty()) {
        emptyMessage(if (searching) R.string.top_search_empty else R.string.top_nothing_to_review)
        return
    }
    val caution = lists.cautionCards
    if (caution.isNotEmpty()) {
        item(key = "caution_title") { SectionHeading(stringResource(R.string.top_section_caution), caution.size, SectionTone.RED) }
        items(caution, key = { "card_" + it.packageName }) { app -> NewAppCard(app, actions) }
    }
    val changed = lists.changedCards
    if (changed.isNotEmpty()) {
        item(key = "changed_title") { SectionHeading(stringResource(R.string.top_section_changed), changed.size, SectionTone.ORANGE) }
        items(changed, key = { "card_" + it.packageName }) { app -> NewAppCard(app, actions) }
    }
    if (lists.unconfirmed.isNotEmpty()) {
        item(key = "unconfirmed_title") {
            SectionHeading(stringResource(R.string.top_unconfirmed_apps), lists.unconfirmed.size, SectionTone.GRAY)
        }
        items(lists.unconfirmed, key = { "unconfirmed_" + it.packageName }) { app ->
            AppRow(app = app, showStatus = false, actions = actions)
        }
    }
}

private fun LazyListScope.allContent(lists: TopLists, searching: Boolean, actions: TopActions) {
    if (searching && lists.all.isEmpty()) {
        emptyMessage(R.string.top_search_empty)
        return
    }
    items(lists.all, key = { "all_" + it.packageName }) { app ->
        AppRow(app = app, showStatus = true, actions = actions)
    }
}

@Composable
private fun TopHeader(
    lastCheckAt: LocalDateTime?,
    isLoading: Boolean,
    healthNeedsAttention: Boolean,
    onOpenHealthCheck: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                // 文字の大きさはそのまま、行の高さだけを詰める（最終チェックの行と合わせて 48dp）
                style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 32.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            val status = when {
                // 上部を詰めるため、一覧の行と同じ短い形（「11/15(日) 12:57」）にして1行に収める
                lastCheckAt != null -> withDaysAgo(
                    stringResource(R.string.top_last_check, formatDate(lastCheckAt.toEpochMillis(), DateForm.LIST)),
                    lastCheckAt,
                )
                isLoading -> AnnotatedString(stringResource(R.string.top_loading))
                else -> null
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HealthCheckButton(needsAttention = healthNeedsAttention, onClick = onOpenHealthCheck)
        LongPressSettingsButton(onLongPress = onOpenSettings)
    }
}

/**
 * 動作チェックを開くボタン（歯車の左。押すだけで開く）。盾にチェックの形。
 * 動作チェックが「確認が必要」のときは、右上に小さなオレンジの点を付ける（読み上げでも状態を伝える）。
 */
@Composable
private fun HealthCheckButton(needsAttention: Boolean, onClick: () -> Unit) {
    val colors = AppWatchTheme.colors
    val description = stringResource(R.string.settings_health_check)
    val state = stringResource(R.string.health_needs_attention)
    val ringColor = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (needsAttention) stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Icon(
                painter = painterResource(R.drawable.ic_health),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            if (needsAttention) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-2).dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(ringColor)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(colors.warningStrong),
                )
            }
        }
    }
}

@Composable
private fun FirstLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.top_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppWatchTheme.colors.warningContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.top_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = AppWatchTheme.colors.onWarningContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

/** 右上の歯車。薄い色で、長押しのときだけ反応する（短く押しても何も起きない）。 */
@Composable
internal fun LongPressSettingsButton(onLongPress: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val description = stringResource(R.string.top_open_settings)
    val actionLabel = stringResource(R.string.top_open_settings_action)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnLongPress()
                    },
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                // TalkBack では「ダブルタップして長押し」で開ける
                onLongClick(label = actionLabel) {
                    currentOnLongPress()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = null,
            tint = AppWatchTheme.colors.subtleIcon,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * 「新しく入ったアプリ」のカード。要注意は赤枠、警告ありはオレンジ枠、その他は枠なし。
 * 確認が必要になった理由は、ふつうの説明の文と分けて色付きの札で出す。カードを押すとアプリの詳細を開く。
 */
@Composable
private fun NewAppCard(app: AppSummary, actions: TopActions) {
    val colors = AppWatchTheme.colors
    val isCaution = app.reviewStatus == ReviewStatus.CAUTION
    val hasWarning = app.warnings.isNotEmpty()
    val reasons = ReviewReason.forTop(app)
    val border = when {
        isCaution -> BorderStroke(3.dp, colors.danger)
        hasWarning -> BorderStroke(2.dp, colors.warningBorder)
        else -> null
    }
    Surface(
        onClick = { actions.onShowDetails(app) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AppIcon(name = app.name, packageName = app.packageName, versionKey = app.versionName)
                    if (isCaution) {
                        StatusMark(
                            status = ReviewStatus.CAUTION,
                            size = 24.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-8).dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    AppName(app, style = MaterialTheme.typography.titleLarge)
                    // 入手元の札を出すときは、同じことを書かない
                    Text(
                        text = addedText(app, withSource = !ReviewReason.hidesSourceText(reasons)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ReasonChips(reasons, onClick = { actions.onExplainReasons(app) })

            if (isCaution && !app.adminMemo.isNullOrBlank()) {
                AdminMemoBox(app.adminMemo)
            }

            // 各カード（要注意を含む）に「削除する」と「詳しく」。消し方はアプリの種類で変わる
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (app.removeAction) {
                    RemoveAction.UNINSTALL -> DeleteButton(
                        onClick = { actions.onDelete(app) },
                        modifier = Modifier.weight(2f),
                    )
                    RemoveAction.APP_INFO -> AppInfoButton(
                        onClick = { actions.onOpenAppInfo(app) },
                        modifier = Modifier.weight(2f),
                    )
                    RemoveAction.NONE -> Unit
                }
                OutlinedButton(
                    onClick = { actions.onShowDetails(app) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_details),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** アプリ名と「本アプリ」「無効」のラベル。幅が足りないときは、名前を詰めずにラベルを次の行に送る。 */
@Composable
private fun AppName(app: AppSummary, style: TextStyle) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = app.name,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (app.isSelf) NameLabel(stringResource(R.string.app_self_label))
        if (!app.isEnabled) NameLabel(stringResource(R.string.app_disabled_label))
    }
}

@Composable
private fun NameLabel(text: String) {
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

@Composable
private fun addedText(app: AppSummary, withSource: Boolean): AnnotatedString {
    val date = formatDateTime(app.installedAt)
    val text = if (withSource) {
        stringResource(R.string.app_added_at_with_source, date, stringResource(app.source.labelRes()))
    } else {
        stringResource(R.string.app_added_at, date)
    }
    return withDaysAgo(text, app.installedAt)
}

@Composable
private fun AdminMemoBox(memo: String) {
    val colors = AppWatchTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.dangerContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_admin_memo),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onDangerContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = memo,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppWatchTheme.colors.danger,
            contentColor = AppWatchTheme.colors.onDanger,
        ),
    ) {
        Text(text = stringResource(R.string.action_delete_app), style = MaterialTheme.typography.labelLarge)
    }
}

/** 削除できないプリインストールのアプリ用。「削除する」の代わりに出す。 */
@Composable
private fun AppInfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.action_app_info),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一覧の1行（「まだ確認していないアプリ」「すべて」）。行を押すとアプリの詳細を開く。 */
@Composable
private fun AppRow(app: AppSummary, showStatus: Boolean, actions: TopActions) {
    val colors = AppWatchTheme.colors
    val reasons = ReviewReason.forTop(app)
    Surface(
        onClick = { actions.onShowDetails(app) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 確認状態の印はアイコンの右下に重ねる（幅の狭い端末でも名前の幅を確保するため）
            Box {
                AppIcon(name = app.name, packageName = app.packageName, size = 44.dp, versionKey = app.versionName)
                if (showStatus) {
                    StatusMark(
                        status = app.reviewStatus,
                        size = 20.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 5.dp, y = 5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                AppName(app, style = MaterialTheme.typography.titleMedium)
                if (app.kind == AppKind.USER_INSTALLED) {
                    // 幅が狭いので、追加日時と入手元は行を分ける（「Google Play」の途中で折り返さないように）
                    RowDetail(addedText(app, withSource = false))
                    // 入手元の札を出すときは、同じことを書かない
                    if (!ReviewReason.hidesSourceText(reasons)) RowDetail(stringResource(app.source.labelRes()))
                } else {
                    // プリインストール・システムは追加日時や入手元に意味がないので、種類を出す
                    RowDetail(stringResource(app.kind.labelRes()))
                }
                ReasonChips(reasons, Modifier.padding(top = 6.dp), onClick = { actions.onExplainReasons(app) })
            }
            when (app.removeAction) {
                RemoveAction.UNINSTALL -> RowButton(
                    text = stringResource(R.string.action_delete),
                    color = colors.danger,
                    onClick = { actions.onDelete(app) },
                )
                RemoveAction.APP_INFO -> RowButton(
                    text = stringResource(R.string.action_app_info_short),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { actions.onOpenAppInfo(app) },
                )
                // システムのアプリには削除ボタンを出さない（仕様書 6章）
                RemoveAction.NONE -> Unit
            }
        }
    }
}

@Composable
private fun RowDetail(text: AnnotatedString) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RowDetail(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RowButton(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Spacer(Modifier.width(8.dp))
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.5.dp, color),
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun TopScreenPreview() {
    AppWatchTheme {
        TopScreen(
            lists = TopLists.from(DummyData.apps),
            isLoading = false,
            loadFailed = false,
            lastCheckAt = DummyData.lastCheckAt,
            actions = TopActions({}, {}, {}, {}, {}),
        )
    }
}
