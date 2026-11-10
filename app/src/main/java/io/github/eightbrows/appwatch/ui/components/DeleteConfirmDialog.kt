package io.github.eightbrows.appwatch.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.system.UninstallResult
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.ReviewReason
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

/** 削除の確認のダイアログに出すアプリ。 */
data class DeleteTarget(
    val packageName: String,
    val name: String,
    val versionName: String?,
    val warnings: Set<AppWarning>,
    val isSelf: Boolean,
) {
    /**
     * ダイアログに添える警告の札（トップ・アプリの詳細と同じ札、優先の順）。
     * 本アプリ自身は、札の代わりに「見守りが止まる」の説明を出すので、札は出さない。
     */
    val shownReasons: List<ReviewReason>
        get() = ReviewReason.of(warnings, ReviewStatus.UNCONFIRMED, includeCaution = false, isSelf = isSelf)

    companion object {

        /** 画面の回転などで消えないように保存する（null は「ダイアログを出していない」）。 */
        val Saver: Saver<DeleteTarget?, Any> = listSaver(
            save = { target ->
                if (target == null) {
                    emptyList()
                } else {
                    listOf(
                        target.packageName,
                        target.name,
                        target.versionName.orEmpty(),
                        target.warnings.joinToString(",") { it.name },
                        target.isSelf.toString(),
                    )
                }
            },
            restore = { saved ->
                if (saved.size < 5) {
                    null
                } else {
                    DeleteTarget(
                        packageName = saved[0],
                        name = saved[1],
                        versionName = saved[2].ifEmpty { null },
                        warnings = saved[3].split(",").mapNotNull { name -> AppWarning.entries.firstOrNull { it.name == name } }.toSet(),
                        isSelf = saved[4].toBoolean(),
                    )
                }
            },
        )
    }
}

/**
 * 削除の確認（仕様書 6章、画面サンプル 04_self_delete_warning.png の形）。トップ・アプリの詳細のどこから削除しても出す。
 * アイコン・アプリ名・バージョン、大きな文字の「〇〇を削除しますか？」、次に Android の確認が出ることの説明。
 * 「やめる」を目立たせ、「削除する」を押したときだけ Android の削除の確認に進む。
 * 本アプリ自身なら、見守りが止まり記録も消えることを、このダイアログの中に出す（ダイアログを重ねない）。
 */
@Composable
fun DeleteConfirmDialog(target: DeleteTarget, onCancel: () -> Unit, onConfirmDelete: () -> Unit) {
    val colors = AppWatchTheme.colors
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(name = target.name, packageName = target.packageName, size = 56.dp, versionKey = target.versionName.orEmpty())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    target.versionName?.let { version ->
                        Text(
                            text = stringResource(R.string.format_version, version),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.delete_dialog_title, target.name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                if (target.isSelf) {
                    WarningBox(stringResource(R.string.delete_dialog_self_body))
                } else {
                    // 警告はトップ・アプリの詳細と同じ札で出す
                    ReasonChips(target.shownReasons, Modifier.fillMaxWidth())
                }
                Text(
                    text = stringResource(R.string.delete_dialog_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.safeAction,
                        contentColor = colors.onSafeAction,
                    ),
                ) {
                    Text(stringResource(R.string.delete_dialog_cancel), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onConfirmDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.danger),
                ) {
                    Text(
                        text = stringResource(R.string.delete_dialog_confirm),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.danger,
                    )
                }
            }
        }
    }
}

/** 本アプリ自身を削除するときの説明（橙色の囲み、先頭に警告の印）。 */
@Composable
private fun WarningBox(text: String) {
    val colors = AppWatchTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.warningContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = colors.onWarningContainer,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.onWarningContainer)
    }
}

/** 削除の結果（「〇〇を削除しました」／「〇〇は削除されませんでした」）。 */
@Composable
fun DeleteResultDialog(appName: String, result: UninstallResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = stringResource(
                    if (result == UninstallResult.DELETED) R.string.delete_result_deleted else R.string.delete_result_not_deleted,
                    appName,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_ok)) }
        },
    )
}
