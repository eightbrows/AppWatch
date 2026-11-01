package io.github.eightbrows.appwatch.ui.transfer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.transfer.ImportOutcome
import io.github.eightbrows.appwatch.ui.components.formatSnapshotTime
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 取り込みの流れの今の段階。 */
sealed interface ImportStage {
    data object Idle : ImportStage
    data class Working(@StringRes val message: Int) : ImportStage
    data class Error(@StringRes val message: Int) : ImportStage
    data class Choice(val candidate: ImportCandidate) : ImportStage
    data class RestoreConfirm(val candidate: ImportCandidate) : ImportStage
    data class BulkOk(val packageNames: List<String>) : ImportStage
}

/**
 * 取り込みの流れ（ファイルを選ぶ → 読む → 取り込みの選択 → 閲覧のみ／復元の確認 → まとめて問題なしの質問）。
 * 書き出し・取り込みの画面、取り込んだデータの画面、ファイルアプリから開いたときで共通に使う。
 */
@Stable
class ImportFlowState internal constructor(
    private val controller: TransferController,
    private val scope: CoroutineScope,
    private val onImported: () -> (ImportOutcome) -> Unit,
    private val onRestored: () -> () -> Unit,
) {
    var stage: ImportStage by mutableStateOf(ImportStage.Idle)
        internal set

    internal var launchPicker: () -> Unit = {}

    /** Android のファイルの選択を開く（種類で絞り込めないので、全ファイルから選び、中身で判断する）。 */
    fun pick() = launchPicker()

    /** 選んだ・開いたファイルを読む。 */
    fun open(uri: Uri) {
        stage = ImportStage.Working(R.string.transfer_reading)
        scope.launch {
            stage = when (val result = controller.open(uri)) {
                is OpenResult.Ready -> ImportStage.Choice(result.candidate)
                OpenResult.NotAppWatch -> ImportStage.Error(R.string.import_error_not_appwatch)
                is OpenResult.TooNew -> ImportStage.Error(R.string.import_error_too_new)
                OpenResult.CannotOpen -> ImportStage.Error(R.string.import_error_cannot_open)
            }
        }
    }

    internal fun cancel() {
        stage = ImportStage.Idle
    }

    internal fun importForViewing(candidate: ImportCandidate, name: String) {
        stage = ImportStage.Working(R.string.import_working)
        scope.launch {
            try {
                val outcome = controller.importForViewing(candidate, name)
                stage = ImportStage.Idle
                onImported()(outcome)
            } catch (e: Exception) {
                stage = ImportStage.Error(R.string.import_failed)
            }
        }
    }

    internal fun askRestore(candidate: ImportCandidate) {
        stage = ImportStage.RestoreConfirm(candidate)
    }

    internal fun restore(candidate: ImportCandidate, reset: Boolean) {
        stage = ImportStage.Working(R.string.restore_working)
        scope.launch {
            try {
                val outcome = controller.restore(candidate.file, reset)
                if (reset && outcome.newlyFound.isNotEmpty()) {
                    stage = ImportStage.BulkOk(outcome.newlyFound)
                } else {
                    stage = ImportStage.Idle
                    onRestored()()
                }
            } catch (e: Exception) {
                stage = ImportStage.Error(R.string.restore_failed)
            }
        }
    }

    internal fun finishBulk(packageNames: List<String>, markOk: Boolean) {
        stage = ImportStage.Working(R.string.restore_working)
        scope.launch {
            if (markOk) controller.markOk(packageNames)
            stage = ImportStage.Idle
            onRestored()()
        }
    }
}

@Composable
fun rememberImportFlow(
    controller: TransferController,
    onImportedForViewing: (ImportOutcome) -> Unit,
    onRestored: () -> Unit,
): ImportFlowState {
    val scope = rememberCoroutineScope()
    val currentOnImported by rememberUpdatedState(onImportedForViewing)
    val currentOnRestored by rememberUpdatedState(onRestored)
    val state = remember(controller, scope) {
        ImportFlowState(controller, scope, { currentOnImported }, { currentOnRestored })
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.open(uri)
    }
    state.launchPicker = { launcher.launch(arrayOf("*/*")) }
    return state
}

/** 取り込みの流れのダイアログ（今の段階に合わせて出す）。 */
@Composable
fun ImportFlowDialogs(state: ImportFlowState) {
    when (val stage = state.stage) {
        ImportStage.Idle -> Unit
        is ImportStage.Working -> WorkingDialog(stage.message)
        is ImportStage.Error -> AlertDialog(
            onDismissRequest = state::cancel,
            title = { Text(stringResource(R.string.import_error_title)) },
            text = { Text(stringResource(stage.message)) },
            confirmButton = { TextButton(onClick = state::cancel) { Text(stringResource(R.string.import_error_close)) } },
        )
        is ImportStage.Choice -> ImportChoiceDialog(
            candidate = stage.candidate,
            onCancel = state::cancel,
            onViewOnly = { name -> state.importForViewing(stage.candidate, name) },
            onRestore = { state.askRestore(stage.candidate) },
        )
        is ImportStage.RestoreConfirm -> RestoreConfirmDialog(
            candidate = stage.candidate,
            onYes = { state.restore(stage.candidate, reset = true) },
            onNo = { state.restore(stage.candidate, reset = false) },
            onCancel = state::cancel,
        )
        is ImportStage.BulkOk -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.restore_bulk_title)) },
            text = {
                Text(pluralStringResource(R.plurals.restore_bulk_body, stage.packageNames.size, stage.packageNames.size))
            },
            confirmButton = {
                TextButton(onClick = { state.finishBulk(stage.packageNames, markOk = true) }) {
                    Text(stringResource(R.string.restore_bulk_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { state.finishBulk(stage.packageNames, markOk = false) }) {
                    Text(stringResource(R.string.restore_bulk_no))
                }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        )
    }
}

@Composable
private fun WorkingDialog(@StringRes message: Int) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** 取り込みの選択（11_import_choice.png）。 */
@Composable
private fun ImportChoiceDialog(
    candidate: ImportCandidate,
    onCancel: () -> Unit,
    onViewOnly: (String) -> Unit,
    onRestore: () -> Unit,
) {
    val file = candidate.file
    var name by remember(candidate) { mutableStateOf(candidate.defaultName) }
    // 初めは選べる方（閲覧のみ → 復元の順）
    var restoreSelected by remember(candidate) { mutableStateOf(!candidate.canViewOnly && candidate.canRestore) }
    val canConfirm = if (restoreSelected) candidate.canRestore else candidate.canViewOnly
    DialogCard(onDismissRequest = onCancel) {
        Text(
            text = stringResource(R.string.import_title),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            InfoLine(stringResource(R.string.import_info_device, file.device.model, file.device.osVersion))
            InfoLine(stringResource(R.string.import_info_exported, formatSnapshotTime(file.exportedAt)))
            InfoLine(stringResource(R.string.import_info_counts, file.content.presentAppCount, file.content.cautionCount))
        }
        Text(
            text = stringResource(R.string.import_name_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME) },
            enabled = !restoreSelected,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        OptionCard(
            title = stringResource(R.string.import_view_only),
            description = stringResource(R.string.import_view_only_desc),
            note = if (candidate.isOwnFile) stringResource(R.string.import_view_only_own) else null,
            selected = !restoreSelected,
            enabled = candidate.canViewOnly,
            onClick = { restoreSelected = false },
        )
        OptionCard(
            title = stringResource(R.string.import_restore),
            description = stringResource(R.string.import_restore_desc),
            note = if (!candidate.canRestore) stringResource(R.string.import_restore_view_only) else null,
            selected = restoreSelected,
            enabled = candidate.canRestore,
            onClick = { restoreSelected = true },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(stringResource(R.string.import_cancel)) }
            Button(
                onClick = { if (restoreSelected) onRestore() else onViewOnly(name) },
                enabled = canConfirm,
                modifier = Modifier
                    .weight(1.6f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(stringResource(R.string.import_confirm), fontWeight = FontWeight.Bold) }
        }
    }
}

/** 復元の確認（14_restore_confirm.png）。選択肢を押すと、そのまま復元する。 */
@Composable
private fun RestoreConfirmDialog(
    candidate: ImportCandidate,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onCancel: () -> Unit,
) {
    DialogCard(onDismissRequest = onCancel) {
        Text(
            text = stringResource(R.string.restore_title),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.restore_exported, formatSnapshotTime(candidate.file.exportedAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OptionCard(
            title = stringResource(R.string.restore_yes),
            description = stringResource(R.string.restore_yes_desc),
            selected = true,
            enabled = true,
            onClick = onYes,
            role = Role.Button,
        )
        OptionCard(
            title = stringResource(R.string.restore_no),
            description = stringResource(R.string.restore_no_desc),
            selected = false,
            enabled = true,
            onClick = onNo,
            role = Role.Button,
        )
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.restore_cancel), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DialogCard(onDismissRequest: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

/** 選択肢の枠。選んでいるものは青い枠。選べないものは薄くして、理由を添える。 */
@Composable
private fun OptionCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    note: String? = null,
    role: Role = Role.RadioButton,
) {
    val active = selected && enabled
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.role = role
                if (role == Role.RadioButton) this.selected = active
            },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = AppWatchTheme.colors.warning,
                )
            }
        }
    }
}

private const val MAX_NAME = 40
