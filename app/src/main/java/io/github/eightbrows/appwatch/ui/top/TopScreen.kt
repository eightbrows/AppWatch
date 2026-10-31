package io.github.eightbrows.appwatch.ui.top

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.ui.components.AppIcon
import io.github.eightbrows.appwatch.ui.components.SegmentedToggle
import io.github.eightbrows.appwatch.ui.components.StatusMark
import io.github.eightbrows.appwatch.ui.components.formatDateTime
import io.github.eightbrows.appwatch.ui.components.labelRes
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import java.time.LocalDateTime

private enum class TopTab { NEEDS_REVIEW, ALL }

/**
 * トップ（本人）。画面サンプル 01_top.png。
 * 「削除する」「詳しく」「削除」はこのステップでは何もしない（後のステップで処理を付ける）。
 */
@Composable
fun TopScreen(
    apps: List<AppSummary>,
    lastCheckAt: LocalDateTime,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteApp: (AppSummary) -> Unit = {},
    onShowDetails: (AppSummary) -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(TopTab.NEEDS_REVIEW) }
    val lists = remember(apps) { TopLists.from(apps) }
    val systemBars = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = systemBars.calculateTopPadding() + 16.dp,
            bottom = systemBars.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { TopHeader(lastCheckAt = lastCheckAt, onOpenSettings = onOpenSettings) }
        item(key = "tabs") {
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
        }
        when (tab) {
            TopTab.NEEDS_REVIEW -> needsReviewContent(lists, onDeleteApp, onShowDetails)
            TopTab.ALL -> allContent(lists, onDeleteApp)
        }
    }
}

private fun LazyListScope.needsReviewContent(
    lists: TopLists,
    onDeleteApp: (AppSummary) -> Unit,
    onShowDetails: (AppSummary) -> Unit,
) {
    if (lists.cards.isEmpty() && lists.unconfirmed.isEmpty()) {
        item(key = "nothing") {
            Text(
                text = stringResource(R.string.top_nothing_to_review),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    if (lists.cards.isNotEmpty()) {
        item(key = "cards_title") { SectionTitle(stringResource(R.string.top_new_apps), lists.cards.size) }
        items(lists.cards, key = { "card_" + it.packageName }) { app ->
            NewAppCard(app = app, onDelete = { onDeleteApp(app) }, onDetails = { onShowDetails(app) })
        }
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
            AppRow(app = app, showStatus = false, onDelete = { onDeleteApp(app) })
        }
    }
}

private fun LazyListScope.allContent(lists: TopLists, onDeleteApp: (AppSummary) -> Unit) {
    item(key = "all_title") { SectionTitle(stringResource(R.string.top_all_apps), lists.all.size) }
    items(lists.all, key = { "all_" + it.packageName }) { app ->
        AppRow(app = app, showStatus = true, onDelete = { onDeleteApp(app) })
    }
}

@Composable
private fun TopHeader(lastCheckAt: LocalDateTime, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.top_last_check, formatDateTime(lastCheckAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LongPressSettingsButton(onLongPress = onOpenSettings)
    }
}

/** 右上の歯車。薄い色で、長押しのときだけ反応する（短く押しても何も起きない）。 */
@Composable
private fun LongPressSettingsButton(onLongPress: () -> Unit) {
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

/** 「新しく入ったアプリ」のカード。要注意は赤枠、警告ありはオレンジ枠、その他は枠なし。 */
@Composable
private fun NewAppCard(app: AppSummary, onDelete: () -> Unit, onDetails: () -> Unit) {
    val colors = AppWatchTheme.colors
    val isCaution = app.reviewStatus == ReviewStatus.CAUTION
    val hasWarning = app.warnings.isNotEmpty()
    val border = when {
        isCaution -> BorderStroke(3.dp, colors.danger)
        hasWarning -> BorderStroke(2.dp, colors.warningBorder)
        else -> null
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AppIcon(name = app.name, packageName = app.packageName)
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
                    if (isCaution && AppWarning.REINSTALLED in app.warnings) {
                        Text(
                            text = stringResource(R.string.app_previously_deleted),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.danger,
                        )
                    } else {
                        Text(
                            text = addedText(app, withSource = !hasWarning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 要注意のカードでは「以前に削除したアプリです」を見出しの下に出しているので、ここでは除く
            val warningsToShow = app.warnings
                .filterNot { isCaution && it == AppWarning.REINSTALLED }
                .sortedBy { it.ordinal }
            warningsToShow.forEach { WarningLine(it) }

            if (isCaution && !app.adminMemo.isNullOrBlank()) {
                AdminMemoBox(app.adminMemo)
            }

            if (isCaution) {
                DeleteButton(onClick = onDelete, modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeleteButton(onClick = onDelete, modifier = Modifier.weight(2f))
                    OutlinedButton(
                        onClick = onDetails,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            text = stringResource(R.string.action_details),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppName(app: AppSummary, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = app.name,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (app.isSelf) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.app_self_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
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
private fun WarningLine(warning: AppWarning) {
    val colors = AppWatchTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.warningContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = colors.onWarningContainer,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(warning.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onWarningContainer,
        )
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

/** 一覧の1行（「まだ確認していないアプリ」「すべて」）。 */
@Composable
private fun AppRow(app: AppSummary, showStatus: Boolean, onDelete: () -> Unit) {
    val colors = AppWatchTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showStatus) {
                StatusMark(status = app.reviewStatus, size = 24.dp)
                Spacer(Modifier.width(10.dp))
            }
            AppIcon(name = app.name, packageName = app.packageName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AppName(app, style = MaterialTheme.typography.titleMedium)
                // 幅が狭いので、追加日時と入手元は行を分ける（「Google Play」の途中で折り返さないように）
                Text(
                    text = addedText(app, withSource = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(app.source.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // システムのアプリは通常アンインストールできないため、ボタンを出さない
            if (app.kind != AppKind.SYSTEM) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, colors.danger),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.danger,
                    )
                }
            }
        }
    }
}
