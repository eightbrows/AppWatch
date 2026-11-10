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
import io.github.eightbrows.appwatch.model.ReviewReason
import io.github.eightbrows.appwatch.ui.components.SegmentedToggle
import io.github.eightbrows.appwatch.ui.components.StatusMark
import io.github.eightbrows.appwatch.ui.components.formatDateTime
import io.github.eightbrows.appwatch.ui.components.labelRes
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import java.time.LocalDateTime

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
) {
    var tab by rememberSaveable { mutableStateOf(TopTab.NEEDS_REVIEW) }
    // 検索の文字は保存しない（画面を離れたら空に戻す）
    var query by remember { mutableStateOf("") }
    val shown = remember(lists, query) { lists?.filtered(query) }
    val searching = query.isNotBlank()
    val systemBars = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = systemBars.calculateTopPadding() + 16.dp,
            bottom = systemBars.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            TopHeader(lastCheckAt = lastCheckAt, isLoading = isLoading, onOpenSettings = actions.onOpenSettings)
        }
        item(key = "search") {
            AppSearchField(query = query, onQueryChange = { query = it }, modifier = Modifier.fillMaxWidth())
        }
        item(key = "tabs") {
            Column {
                SegmentedToggle(
                    options = listOf(
                        TopTab.NEEDS_REVIEW to stringResource(R.string.top_tab_needs_review),
                        TopTab.ALL to stringResource(R.string.top_tab_all),
                    ),
                    selected = tab,
                    onSelect = { tab = it },
                    itemHeight = 48.dp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
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
        }
        when {
            shown == null && loadFailed -> item(key = "failed") { LoadFailed(actions.onRetry) }
            shown == null -> item(key = "loading") { FirstLoading() }
            else -> {
                if (loadFailed) item(key = "refresh_failed") { LoadFailed(actions.onRetry) }
                when (tab) {
                    TopTab.NEEDS_REVIEW -> needsReviewContent(shown, searching, actions)
                    TopTab.ALL -> allContent(shown, searching, actions)
                }
            }
        }
    }
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

private fun LazyListScope.needsReviewContent(lists: TopLists, searching: Boolean, actions: TopActions) {
    if (lists.cards.isEmpty() && lists.unconfirmed.isEmpty()) {
        emptyMessage(if (searching) R.string.top_search_empty else R.string.top_nothing_to_review)
        return
    }
    if (lists.cards.isNotEmpty()) {
        item(key = "cards_title") { SectionTitle(stringResource(R.string.top_new_apps), lists.cards.size) }
        items(lists.cards, key = { "card_" + it.packageName }) { app -> NewAppCard(app, actions) }
    }
    if (lists.unconfirmed.isNotEmpty()) {
        item(key = "unconfirmed_title") {
            SectionTitle(
                stringResource(R.string.top_unconfirmed_apps),
                lists.unconfirmed.size,
                modifier = Modifier.padding(top = 8.dp),
            )
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
    item(key = "all_title") { SectionTitle(stringResource(R.string.top_all_apps), lists.all.size) }
    items(lists.all, key = { "all_" + it.packageName }) { app ->
        AppRow(app = app, showStatus = true, actions = actions)
    }
}

@Composable
private fun TopHeader(lastCheckAt: LocalDateTime?, isLoading: Boolean, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            val status = when {
                lastCheckAt != null -> stringResource(R.string.top_last_check, formatDateTime(lastCheckAt))
                isLoading -> stringResource(R.string.top_loading)
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
        LongPressSettingsButton(onLongPress = onOpenSettings)
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

@Composable
private fun SectionTitle(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = pluralStringResource(R.plurals.count_apps, count, count),
            style = MaterialTheme.typography.titleMedium,
            color = AppWatchTheme.colors.warning,
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
                        text = addedText(app, withSource = reasons.none { it.isSource }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ReasonChips(reasons)

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
private fun addedText(app: AppSummary, withSource: Boolean): String {
    val date = formatDateTime(app.installedAt)
    return if (withSource) {
        stringResource(R.string.app_added_at_with_source, date, stringResource(app.source.labelRes()))
    } else {
        stringResource(R.string.app_added_at, date)
    }
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
                    if (reasons.none { it.isSource }) RowDetail(stringResource(app.source.labelRes()))
                } else {
                    // プリインストール・システムは追加日時や入手元に意味がないので、種類を出す
                    RowDetail(stringResource(app.kind.labelRes()))
                }
                ReasonChips(reasons, Modifier.padding(top = 6.dp))
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
