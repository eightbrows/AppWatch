package io.github.eightbrows.appwatch.ui.imported

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.transfer.ImportOutcome
import io.github.eightbrows.appwatch.data.transfer.ImportedDataset
import io.github.eightbrows.appwatch.ui.components.AdminTopBar
import io.github.eightbrows.appwatch.ui.components.DateForm
import io.github.eightbrows.appwatch.ui.components.formatDate
import io.github.eightbrows.appwatch.ui.components.withDaysAgo
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import io.github.eightbrows.appwatch.ui.transfer.ImportFlowDialogs
import io.github.eightbrows.appwatch.ui.transfer.TransferController
import io.github.eightbrows.appwatch.ui.transfer.TransferNotice
import io.github.eightbrows.appwatch.ui.transfer.rememberImportFlow
import kotlinx.coroutines.launch

/** 取り込んだデータ（12_imported_data.png、仕様書 7章）。設定メニューと、ファイルを開いたときの取り込みの後から開く。 */
@Composable
fun ImportedDataRoute(
    controller: TransferController,
    onBack: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenHistory: () -> Unit,
    onImportedForViewing: (ImportOutcome) -> Unit,
    onRestored: () -> Unit,
) {
    val imported by remember { controller.imported }.collectAsState(initial = null)
    val notice by controller.notice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val importFlow = rememberImportFlow(controller, onImportedForViewing, onRestored)
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    // 取り込み・削除の後のお知らせ
    LaunchedEffect(notice) {
        val current = notice ?: return@LaunchedEffect
        controller.consumeNotice()
        val text = when (current) {
            is TransferNotice.Imported -> resources.getString(R.string.import_done, current.name)
            is TransferNotice.Deleted -> resources.getString(R.string.imported_deleted, current.name)
        }
        snackbarHostState.showSnackbar(text)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AdminTopBar(title = stringResource(R.string.imported_title), onBack = onBack)
        Box(Modifier.weight(1f)) {
            val list = imported
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (list != null && list.isEmpty()) {
                    item(key = "empty") { EmptyCard() }
                }
                items(list.orEmpty(), key = { it.id }) { dataset ->
                    DatasetCard(
                        dataset = dataset,
                        onOpenStatus = {
                            scope.launch {
                                controller.select(dataset)
                                onOpenStatus()
                            }
                        },
                        onOpenHistory = {
                            scope.launch {
                                controller.select(dataset)
                                onOpenHistory()
                            }
                        },
                        onRename = { renameTarget = dataset.id },
                        onDelete = { deleteTarget = dataset.id },
                    )
                }
                if (!list.isNullOrEmpty()) {
                    item(key = "note") {
                        Text(
                            text = stringResource(R.string.imported_note),
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = importFlow::pick,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.imported_import_button), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
    ImportFlowDialogs(importFlow)

    imported?.firstOrNull { it.id == renameTarget }?.let { dataset ->
        var name by remember(dataset.id) { mutableStateOf(dataset.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.imported_rename)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        controller.rename(dataset, name)
                        renameTarget = null
                    },
                ) { Text(stringResource(R.string.imported_rename_ok)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }

    imported?.firstOrNull { it.id == deleteTarget }?.let { dataset ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.imported_delete_title, dataset.name)) },
            text = { Text(stringResource(R.string.imported_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    controller.delete(dataset)
                    deleteTarget = null
                }) { Text(stringResource(R.string.imported_delete_ok), color = AppWatchTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

/** 取り込んだデータ1件のカード。 */
@Composable
private fun DatasetCard(
    dataset: ImportedDataset,
    onOpenStatus: () -> Unit,
    onOpenHistory: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppWatchTheme.colors
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dataset.name,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (dataset.cautionCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.imported_caution, dataset.cautionCount, dataset.cautionCount),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.danger)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onDanger,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.imported_no_caution),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ok,
                    )
                }
            }
            Text(
                text = stringResource(R.string.imported_device, dataset.deviceModel, dataset.osVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 「時点」と「取り込み」は、それぞれ曜日と何日前を添えて1行ずつ
            Text(
                text = withDaysAgo(stringResource(R.string.imported_snapshot, formatDate(dataset.exportedAt, DateForm.LIST)), dataset.exportedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = withDaysAgo(stringResource(R.string.imported_imported_at, formatDate(dataset.importedAt, DateForm.LIST)), dataset.importedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardButton(stringResource(R.string.imported_open_status), onOpenStatus, Modifier.weight(1f))
                CardButton(stringResource(R.string.imported_open_history), onOpenHistory, Modifier.weight(1f))
                Box(Modifier.weight(1f)) {
                    var menu by remember { mutableStateOf(false) }
                    val moreDesc = stringResource(R.string.imported_more_desc, dataset.name)
                    CardButton(
                        text = stringResource(R.string.imported_more),
                        onClick = { menu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = moreDesc },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.imported_rename)) }, onClick = {
                            menu = false
                            onRename()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.imported_delete), color = colors.danger) }, onClick = {
                            menu = false
                            onDelete()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun CardButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun EmptyCard() {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.imported_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.imported_howto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
