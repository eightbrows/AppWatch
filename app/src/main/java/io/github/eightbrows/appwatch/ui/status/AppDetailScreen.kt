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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import io.github.eightbrows.appwatch.ui.components.ScreenTopBar
import io.github.eightbrows.appwatch.ui.components.StatusMark
import io.github.eightbrows.appwatch.ui.components.ViewingBand
import io.github.eightbrows.appwatch.ui.components.ViewingFooter
import io.github.eightbrows.appwatch.ui.components.AppIcon
import io.github.eightbrows.appwatch.ui.components.ReasonChips
import io.github.eightbrows.appwatch.ui.components.ReasonExplanationSheet
import io.github.eightbrows.appwatch.ui.components.formatDate
import io.github.eightbrows.appwatch.ui.components.withDaysAgo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.clickable
import io.github.eightbrows.appwatch.model.AppSearch
import io.github.eightbrows.appwatch.model.ReviewReason
import io.github.eightbrows.appwatch.ui.components.formatShortDate
import io.github.eightbrows.appwatch.ui.components.labelRes
import io.github.eightbrows.appwatch.ui.components.toLocalDateTime
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

/** アプリの詳細を見る人。 */
enum class DetailViewer {
    /** 管理者（設定メニューから）。確認状態・管理者メモを編集できる。選択中のデータを見る */
    HELPER,

    /** 本人（トップから）。見るだけ。いつもこの端末のデータを見る */
    PERSON,
}

/**
 * アプリの詳細。画面サンプル 07_app_detail_admin.png。本人用と管理者用で同じ部品・同じ並びを使う。
 * - 管理者用：確認状態と管理者メモを編集できる。「保存」で前の画面に戻り、そこで「元に戻す」付きのお知らせを出す。
 *   保存しないで戻るときは確認を出す。取り込んだデータを見ているときは見るだけ。
 * - 本人用：確認状態・管理者メモは見るだけ（「（管理者が設定）」を添える）。下部は「Android のアプリ情報」と「削除する」だけ。
 *   「管理者用」の印は出さない。削除して戻ってきたら、チェックで端末から消えたのが分かった時点でトップへ戻る。
 * 「削除する」は、削除の確認を出してから Android の削除の確認に進む。
 */
@Composable
fun AppDetailRoute(
    controller: AppStatusController,
    packageName: String,
    viewer: DetailViewer,
    onBack: () -> Unit,
    /** 削除の確認を出す */
    onDelete: (AppStatusEntry) -> Unit,
    onOpenAppInfo: (AppStatusEntry) -> Unit,
) {
    val forHelper = viewer == DetailViewer.HELPER
    val detail by remember(packageName, viewer) {
        if (forHelper) controller.detail(packageName) else controller.localDetail(packageName)
    }.collectAsState(initial = null)
    val selection by remember { controller.selection }.collectAsState(initial = null)
    // 取り込んだデータを見ているときは、見るだけ（保存・アプリ情報・削除・登録の解除を出さない）
    val viewing = forHelper && selection?.isViewingImported == true
    // 確認状態・管理者メモを編集できるか（管理者が、この端末のデータを見ているとき）
    val editable = forHelper && !viewing
    val entry = detail?.entry

    // 編集中の値（画面の回転でも消えないように保存する）。読み込んだ最初の1回だけ、保存されている値を入れる
    var initialized by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf(ReviewStatus.UNCONFIRMED) }
    var memo by rememberSaveable { mutableStateOf("") }
    // 「問題なし」のボタンを押したか（すでに問題なしでも、後から付いた再導入の警告を消せるように）
    var pressedOk by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmUnregister by rememberSaveable { mutableStateOf(false) }
    // 注意の説明（下から出る画面）を出しているか
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(entry) {
        if (!initialized && entry != null) {
            status = entry.status
            memo = entry.adminMemo.orEmpty()
            initialized = true
        }
    }
    // 本人用：「削除する」を押したか。戻ってきてチェックで端末から消えたのが分かったら、トップへ戻る
    var deleteRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(detail, deleteRequested) {
        if (!forHelper && deleteRequested && detail != null && entry?.isPresent != true) onBack()
    }

    val isDirty = editable && entry != null && initialized && (
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
        ScreenTopBar(title = stringResource(R.string.detail_title), onBack = tryBack, showAdminBadge = forHelper)
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
            if (forHelper) selection?.let { ViewingBand(it) }
            Header(entry, loadIcon = !viewing)
            WarningBox(entry, current.events, onExplain = { showExplanation = true })
            SectionLabel(stringResource(R.string.detail_status))
            if (editable) {
                StatusButtons(
                    selected = status,
                    onSelect = {
                        status = it
                        if (it == ReviewStatus.OK) pressedOk = true
                    },
                )
            } else {
                // 見るだけ：本人には「（管理者が設定）」を添える。取り込んだデータは選ばれた確認状態だけを示す
                StatusView(entry.status, showsByAdmin = !forHelper)
            }
            SectionLabel(stringResource(R.string.app_admin_memo))
            if (editable) {
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
            } else {
                MemoView(entry.adminMemo?.takeIf { it.isNotBlank() } ?: stringResource(R.string.detail_memo_none))
            }
            SectionLabel(stringResource(R.string.detail_history))
            History(current.events)
        }
        if (viewing) {
            ViewingFooter()
            return@Column
        }
        BottomButtons(
            entry = entry,
            viewer = viewer,
            canSave = isDirty,
            onSave = {
                controller.saveDetail(entry, status, memo)
                onBack()
            },
            onOpenAppInfo = { onOpenAppInfo(entry) },
            onDelete = {
                deleteRequested = true
                onDelete(entry)
            },
            onUnregister = { confirmUnregister = true },
        )
    }

    if (showExplanation && entry != null) {
        ReasonExplanationSheet(
            appName = entry.name,
            applying = ReviewReason.of(entry.warnings, entry.status, isSelf = entry.isSelf),
            onDismiss = { showExplanation = false },
        )
    }

    if (confirmUnregister && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmUnregister = false },
            title = { Text(stringResource(R.string.detail_unregister_title)) },
            text = { Text(stringResource(R.string.detail_unregister_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnregister = false
                    controller.unregister(entry)
                    onBack()
                }) { Text(stringResource(R.string.detail_unregister_confirm), color = AppWatchTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnregister = false }) { Text(stringResource(R.string.detail_unregister_cancel)) }
            },
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
internal fun SectionLabel(text: String) {
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
internal fun Header(entry: AppStatusEntry, loadIcon: Boolean = true) {
    Row(verticalAlignment = Alignment.Top) {
        if (entry.isPresent) {
            AppIcon(name = entry.name, packageName = entry.packageName, size = 56.dp, versionKey = entry.versionName.orEmpty(), loadIcon = loadIcon)
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
                    DetailText(withDaysAgo(stringResource(R.string.status_row_removed, formatShortDate(removedAt.toLocalDateTime())), removedAt))
                !entry.isPresent && entry.kind == null -> DetailText(stringResource(R.string.status_row_not_installed))
            }
            val kind = entry.kind
            val source = entry.source
            val installed = entry.firstInstallTime?.takeIf { it > 0 }
            when {
                kind == AppKind.USER_INSTALLED && source != null && installed != null -> DetailText(
                    withDaysAgo(stringResource(R.string.detail_installed, stringResource(source.labelRes()), formatDate(installed)), installed),
                )
                kind != null -> DetailText(stringResource(kind.labelRes()))
            }
            Text(
                text = AppSearch.breakablePackageName(entry.packageName),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DetailText(text: AnnotatedString) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun DetailText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 端末にないアプリの、アイコンの代わりの灰色の印。 */
@Composable
internal fun AbsentMark(name: String, size: Dp = 56.dp) {
    val description = stringResource(R.string.detail_absent_mark)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(AppWatchTheme.colors.unconfirmed.copy(alpha = 0.35f))
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name.take(1), color = Color.White, fontSize = (size.value * 0.43f).sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 警告の欄。理由はトップ・確認状態の画面と同じ札で出し、再インストール・署名の変更は日付の説明を添える。
 * 要注意は赤、それ以外で警告があれば橙の枠。本アプリ自身には警告の札を出さないので、要注意でなければ欄ごと出さない。
 * 見出しの右に「説明 ›」を出し、欄のどこを押しても注意の説明（[onExplain]）を開く。
 */
@Composable
internal fun WarningBox(entry: AppStatusEntry, events: List<AppChange>, onExplain: () -> Unit) {
    val colors = AppWatchTheme.colors
    val isCaution = entry.status == ReviewStatus.CAUTION
    val reasons = ReviewReason.of(entry.warnings, entry.status, isSelf = entry.isSelf)
    if (reasons.isEmpty()) return
    val accent = if (isCaution) colors.danger else colors.warning
    val clickLabel = stringResource(R.string.reason_open_explanation)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, if (isCaution) colors.danger else colors.warningBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onExplain),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(if (isCaution) R.string.detail_caution_title else R.string.detail_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.reason_more),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            ReasonChips(reasons)
            // 札と同じ順（以前に削除 → 署名の変更）。札を出していない警告の説明は出さない
            listOf(AppWarning.REINSTALLED, AppWarning.SIGNATURE_CHANGED).filter { it in entry.warnings && !entry.isSelf }.forEach { warning ->
                val text = datedWarningText(warning, entry, events) ?: return@forEach
                Text(
                    text = stringResource(R.string.detail_warning_item, text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 日付の分かる警告の説明。再インストールは削除した日と入れ直した日、署名の変更は見つけた日。それ以外は null（札だけ）。 */
@Composable
private fun datedWarningText(warning: AppWarning, entry: AppStatusEntry, events: List<AppChange>): String? {
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
        else -> null
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
                    .heightIn(min = 52.dp)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) container else MaterialTheme.colorScheme.surface,
                border = if (isSelected) BorderStroke(2.dp, accent) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text(
                        text = stringResource(status.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** これまでの経緯（古い順）。 */
@Composable
internal fun History(events: List<AppChange>) {
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
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(
                        text = historyText(event, events),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = withDaysAgo(formatDate(event.occurredAt), event.occurredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    ChangeType.PRE_REGISTERED -> stringResource(
        if (event.reason == StatusChangeReason.UNDO) R.string.history_pre_registered_undo else R.string.history_pre_registered,
    )
    ChangeType.UNREGISTERED -> stringResource(
        if (event.reason == StatusChangeReason.UNDO) R.string.history_unregistered_undo else R.string.history_unregistered,
    )
}

/**
 * 下部のボタン。
 * - 管理者用：「保存」「Android のアプリ情報」「削除する」。端末にないアプリは、アプリ情報と削除を押せない。
 *   端末にない登録済みのアプリは、アプリ情報と削除の代わりに「登録を解除」。
 * - 本人用：「Android のアプリ情報」と「削除する」だけ。「削除する」は後から導入のアプリだけに出す。
 */
@Composable
private fun BottomButtons(
    entry: AppStatusEntry,
    viewer: DetailViewer,
    canSave: Boolean,
    onSave: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onDelete: () -> Unit,
    onUnregister: () -> Unit,
) {
    val colors = AppWatchTheme.colors
    val forHelper = viewer == DetailViewer.HELPER
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (forHelper) {
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
            }
            if (forHelper && entry.isPreRegistered && !entry.isPresent) {
                OutlinedButton(
                    onClick = onUnregister,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.danger),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
                ) {
                    Text(stringResource(R.string.detail_unregister), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                return@Column
            }
            // 削除できるのは後から導入したアプリだけ（プリインストール・システムは Android のアプリ情報から）
            val canDelete = entry.isPresent && entry.kind == AppKind.USER_INSTALLED
            // 本人用は、削除できないアプリでは「削除する」を出さない（管理者用は押せない形で出す）
            val showDelete = forHelper || canDelete
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenAppInfo,
                    enabled = entry.isPresent,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.action_app_info),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (entry.isPresent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
                if (showDelete) {
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = canDelete,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            if (canDelete) 1.5.dp else 1.dp,
                            if (canDelete) colors.danger else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(
                            stringResource(R.string.action_delete_app),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 確認の状態（見るだけ）。「未確認」以外は「（管理者が設定）」を添える。 */
@Composable
internal fun StatusView(status: ReviewStatus, showsByAdmin: Boolean = true) {
    val colors = AppWatchTheme.colors
    val color = when (status) {
        ReviewStatus.CAUTION -> colors.danger
        ReviewStatus.OK -> colors.ok
        ReviewStatus.UNCONFIRMED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusMark(status = status, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(status.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            if (showsByAdmin && status != ReviewStatus.UNCONFIRMED) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_status_by_admin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 管理者メモ（見るだけ）。本人用と、取り込んだデータを見ているときの管理者用で使う。 */
@Composable
internal fun MemoView(memo: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = memo,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
