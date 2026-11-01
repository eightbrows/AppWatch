package io.github.eightbrows.appwatch.ui.status

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.record.AppChange
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.data.record.StatusChangeReason
import io.github.eightbrows.appwatch.data.status.AppStatusEntry
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.ui.components.AdminTopBar
import io.github.eightbrows.appwatch.ui.components.AppIcon
import io.github.eightbrows.appwatch.ui.components.formatDateTime
import io.github.eightbrows.appwatch.ui.components.formatShortDate
import io.github.eightbrows.appwatch.ui.components.labelRes
import io.github.eightbrows.appwatch.ui.components.toLocalDateTime
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

/**
 * アプリの詳細（管理者）。画面サンプル 07_app_detail_admin.png。本人用の詳細（03）と同じ形で、確認状態と管理者メモを編集できる。
 * 「保存」で前の画面に戻り、そこで「元に戻す」付きのお知らせを出す。保存しないで戻るときは確認を出す。
 */
@Composable
fun AdminAppDetailRoute(
    controller: AppStatusController,
    packageName: String,
    onBack: () -> Unit,
    /** Android のアンインストール画面を開く（本アプリなら先に削除警告を出す） */
    onUninstall: (AppStatusEntry) -> Unit,
    onOpenAppInfo: (AppStatusEntry) -> Unit,
) {
    val detail by remember(packageName) { controller.detail(packageName) }.collectAsState(initial = null)
    val entry = detail?.entry

    // 編集中の値（画面の回転でも消えないように保存する）。読み込んだ最初の1回だけ、保存されている値を入れる
    var initialized by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf(ReviewStatus.UNCONFIRMED) }
    var memo by rememberSaveable { mutableStateOf("") }
    // 「問題なし」のボタンを押したか（すでに問題なしでも、後から付いた再導入の警告を消せるように）
    var pressedOk by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(entry) {
        if (!initialized && entry != null) {
            status = entry.status
            memo = entry.adminMemo.orEmpty()
            initialized = true
        }
    }

    val isDirty = entry != null && initialized && (
        status != entry.status ||
            memo.trim() != entry.adminMemo.orEmpty() ||
            (pressedOk && status == ReviewStatus.OK && entry.hasRecordedWarnings)
        )
    val tryBack: () -> Unit = { if (isDirty) confirmDiscard = true else onBack() }
    BackHandler(enabled = isDirty) { confirmDiscard = true }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        AdminTopBar(title = stringResource(R.string.detail_title), onBack = tryBack)
        val current = detail
        if (current == null || entry == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (current == null) CircularProgressIndicator()
            }
            return@Column
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(entry)
            WarningBox(entry, current.events)
            SectionLabel(stringResource(R.string.detail_status))
            StatusButtons(
                selected = status,
                onSelect = {
                    status = it
                    if (it == ReviewStatus.OK) pressedOk = true
                },
            )
            SectionLabel(stringResource(R.string.app_admin_memo))
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text(stringResource(R.string.detail_memo_hint)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            SectionLabel(stringResource(R.string.detail_history))
            History(current.events)
        }
        BottomButtons(
            entry = entry,
            canSave = isDirty,
            onSave = {
                controller.saveDetail(entry, status, memo)
                onBack()
            },
            onOpenAppInfo = { onOpenAppInfo(entry) },
            onUninstall = { onUninstall(entry) },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.detail_discard_title)) },
            text = { Text(stringResource(R.string.detail_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.detail_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.detail_discard_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** アイコン、アプリ名、バージョン、入手元・インストール日、パッケージ名。端末にないアプリは灰色の印。 */
@Composable
private fun Header(entry: AppStatusEntry) {
    Row(verticalAlignment = Alignment.Top) {
        if (entry.isPresent) {
            AppIcon(name = entry.name, packageName = entry.packageName, size = 56.dp, versionKey = entry.versionName.orEmpty())
        } else {
            AbsentMark(entry.name)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (entry.isPresent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.isSelf) SmallLabel(stringResource(R.string.app_self_label))
                if (entry.isPresent && !entry.isEnabled) SmallLabel(stringResource(R.string.app_disabled_label))
            }
            entry.versionName?.takeIf { it.isNotBlank() }?.let {
                DetailText(stringResource(R.string.format_version, it))
            }
            val removedAt = entry.removedAt
            when {
                !entry.isPresent && removedAt != null ->
                    DetailText(stringResource(R.string.status_row_removed, formatShortDate(removedAt.toLocalDateTime())))
                !entry.isPresent && entry.kind == null -> DetailText(stringResource(R.string.status_row_not_installed))
            }
            val kind = entry.kind
            val source = entry.source
            val installed = entry.firstInstallTime?.takeIf { it > 0 }
            when {
                kind == AppKind.USER_INSTALLED && source != null && installed != null -> DetailText(
                    stringResource(R.string.detail_installed, stringResource(source.labelRes()), formatDateTime(installed.toLocalDateTime())),
                )
                kind != null -> DetailText(stringResource(kind.labelRes()))
            }
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 端末にないアプリの、アイコンの代わりの灰色の印。 */
@Composable
private fun AbsentMark(name: String) {
    val description = stringResource(R.string.detail_absent_mark)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppWatchTheme.colors.unconfirmed.copy(alpha = 0.35f))
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name.take(1), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/** 警告の欄。要注意は赤、それ以外で警告があれば橙。 */
@Composable
private fun WarningBox(entry: AppStatusEntry, events: List<AppChange>) {
    val colors = AppWatchTheme.colors
    val isCaution = entry.status == ReviewStatus.CAUTION
    if (!isCaution && entry.warnings.isEmpty()) return
    val container = if (isCaution) colors.dangerContainer else colors.warningContainer
    val content = if (isCaution) colors.onDangerContainer else colors.onWarningContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(if (isCaution) R.string.detail_caution_title else R.string.detail_warning_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = content,
            )
        }
        entry.warnings.sortedBy { it.ordinal }.forEach { warning ->
            Text(
                text = stringResource(R.string.detail_warning_item, warningText(warning, entry, events)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 警告の説明。再インストールは削除した日と入れ直した日、署名の変更は見つけた日を付ける。 */
@Composable
private fun warningText(warning: AppWarning, entry: AppStatusEntry, events: List<AppChange>): String {
    val reinstalledAt = entry.reinstalledAt
    val signatureAt = entry.signatureChangedAt
    return when {
        warning == AppWarning.REINSTALLED && reinstalledAt != null -> {
            val removed = events.lastOrNull { it.type == ChangeType.REMOVED && it.occurredAt <= reinstalledAt }
            val reinstalled = formatShortDate(reinstalledAt.toLocalDateTime())
            if (removed != null) {
                stringResource(R.string.detail_reinstalled_dates, formatShortDate(removed.occurredAt.toLocalDateTime()), reinstalled)
            } else {
                stringResource(R.string.detail_reinstalled_date, reinstalled)
            }
        }
        warning == AppWarning.SIGNATURE_CHANGED && signatureAt != null ->
            stringResource(R.string.detail_signature_changed_date, formatShortDate(signatureAt.toLocalDateTime()))
        else -> stringResource(warning.labelRes())
    }
}

/** 確認の状態の3つのボタン（問題なし／未確認／要注意）。 */
@Composable
private fun StatusButtons(selected: ReviewStatus, onSelect: (ReviewStatus) -> Unit) {
    val colors = AppWatchTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ReviewStatus.OK, ReviewStatus.UNCONFIRMED, ReviewStatus.CAUTION).forEach { status ->
            val isSelected = status == selected
            val (accent, container) = when (status) {
                ReviewStatus.OK -> colors.ok to MaterialTheme.colorScheme.surface
                ReviewStatus.UNCONFIRMED -> MaterialTheme.colorScheme.onSurface to MaterialTheme.colorScheme.surfaceContainerHighest
                ReviewStatus.CAUTION -> colors.danger to colors.dangerContainer
            }
            Surface(
                onClick = { onSelect(status) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) container else MaterialTheme.colorScheme.surface,
                border = if (isSelected) BorderStroke(2.dp, accent) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(status.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** これまでの経緯（古い順）。 */
@Composable
private fun History(events: List<AppChange>) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (events.isEmpty()) {
                Text(
                    text = stringResource(R.string.detail_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            events.forEach { event ->
                Row(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(
                        text = formatShortDate(event.occurredAt.toLocalDateTime()),
                        modifier = Modifier.width(64.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = historyText(event, events),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** これまでの経緯の1行の文。 */
@Composable
private fun historyText(event: AppChange, all: List<AppChange>): String = when (event.type) {
    ChangeType.ADDED -> if (event.isReinstall) {
        val signatureChanged = all.any {
            it.type == ChangeType.SIGNATURE_CHANGED && it.occurredAt == event.occurredAt && it.packageName == event.packageName
        }
        stringResource(if (signatureChanged) R.string.history_reinstalled_changed else R.string.history_reinstalled_same)
    } else {
        stringResource(R.string.history_added)
    }
    ChangeType.REMOVED -> stringResource(R.string.history_removed)
    ChangeType.UPDATED -> stringResource(
        R.string.history_updated,
        event.oldVersionName ?: event.oldVersionCode?.toString().orEmpty(),
        event.versionName ?: event.versionCode?.toString().orEmpty(),
    )
    ChangeType.ENABLED -> stringResource(R.string.history_enabled)
    ChangeType.DISABLED -> stringResource(R.string.history_disabled)
    ChangeType.SIGNATURE_CHANGED -> stringResource(R.string.history_signature_changed)
    ChangeType.RESET -> stringResource(R.string.history_reset)
    ChangeType.STATUS_CHANGED -> {
        val newLabel = stringResource((event.newStatus ?: ReviewStatus.UNCONFIRMED).labelRes())
        when (event.reason) {
            StatusChangeReason.UNDO ->
                stringResource(R.string.history_status_undo, stringResource((event.oldStatus ?: ReviewStatus.UNCONFIRMED).labelRes()), newLabel)
            StatusChangeReason.SIGNATURE_CHANGED -> stringResource(R.string.history_status_signature, newLabel)
            else -> stringResource(R.string.history_status_set, newLabel)
        }
    }
    ChangeType.MEMO_CHANGED -> when {
        event.reason == StatusChangeReason.UNDO -> stringResource(R.string.history_memo_undo)
        event.newAdminMemo == null -> stringResource(R.string.history_memo_removed)
        else -> stringResource(R.string.history_memo_changed)
    }
}

/** 下部の「保存」「Android のアプリ情報」「アンインストール」。端末にないアプリは、アプリ情報とアンインストールを押せない。 */
@Composable
private fun BottomButtons(
    entry: AppStatusEntry,
    canSave: Boolean,
    onSave: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val colors = AppWatchTheme.colors
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.detail_save), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenAppInfo,
                    enabled = entry.isPresent,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.action_app_info),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (entry.isPresent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
                // 削除できるのは後から導入したアプリだけ（プリインストール・システムは Android のアプリ情報から）
                val canUninstall = entry.isPresent && entry.kind == AppKind.USER_INSTALLED
                OutlinedButton(
                    onClick = onUninstall,
                    enabled = canUninstall,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (canUninstall) colors.danger else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.detail_uninstall),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
