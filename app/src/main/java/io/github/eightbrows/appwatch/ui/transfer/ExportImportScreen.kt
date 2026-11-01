package io.github.eightbrows.appwatch.ui.transfer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.settings.RecentExport
import io.github.eightbrows.appwatch.data.transfer.AppWatchFile
import io.github.eightbrows.appwatch.data.transfer.ImportOutcome
import io.github.eightbrows.appwatch.model.UsageMode
import io.github.eightbrows.appwatch.ui.components.AdminTopBar
import io.github.eightbrows.appwatch.ui.components.formatSnapshotTime
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil

/**
 * 書き出し・取り込み（管理者）。画面サンプル 10_export_import.png、仕様書 7章。
 * 閲覧専用の端末では、書き出しの欄を出さない。
 * @param openUri ファイルアプリなどから開いたファイル。読んだら [onOpenUriConsumed] を呼ぶ
 */
@Composable
fun ExportImportRoute(
    controller: TransferController,
    openUri: Uri?,
    onOpenUriConsumed: () -> Unit,
    onBack: () -> Unit,
    onImportedForViewing: (ImportOutcome) -> Unit,
    onRestored: () -> Unit,
) {
    val usageMode by remember { controller.usageMode }.collectAsState(initial = null)
    val recent by remember { controller.recentExports }.collectAsState(initial = emptyList())
    val viewOnly = usageMode == UsageMode.VIEW_ONLY
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var exporting by rememberSaveable { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(AppWatchFile.MIME_TYPE)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        scope.launch {
            val result = controller.export(uri)
            exporting = false
            snackbarHostState.showSnackbar(
                resources.getString(if (result is ExportResult.Done) R.string.transfer_exported else R.string.transfer_export_failed),
            )
        }
    }
    val importFlow = rememberImportFlow(controller, onImportedForViewing, onRestored)
    LaunchedEffect(openUri) {
        if (openUri != null) {
            onOpenUriConsumed()
            importFlow.open(openUri)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AdminTopBar(title = stringResource(R.string.transfer_title), onBack = onBack)
        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!viewOnly) {
                    SectionTitle(stringResource(R.string.transfer_export_section))
                    ActionCard(
                        title = stringResource(R.string.transfer_export_title),
                        description = stringResource(R.string.transfer_export_desc),
                        button = stringResource(R.string.transfer_export_button),
                        busy = exporting,
                        onClick = { saveLauncher.launch(controller.defaultFileName()) },
                    )
                    Text(
                        text = stringResource(R.string.transfer_note),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SectionTitle(stringResource(R.string.transfer_import_section))
                ActionCard(
                    title = stringResource(R.string.transfer_import_title),
                    description = stringResource(R.string.transfer_import_desc),
                    button = stringResource(R.string.transfer_import_button),
                    busy = false,
                    onClick = importFlow::pick,
                )
                if (!viewOnly) {
                    SectionTitle(stringResource(R.string.transfer_recent_section))
                    RecentExports(recent)
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp),
            )
        }
    }
    ImportFlowDialogs(importFlow)
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 4.dp, top = 8.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 題名・説明と右のボタンの枠（書き出し・取り込み）。 */
@Composable
private fun ActionCard(title: String, description: String, button: String, busy: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = if (busy) stringResource(R.string.transfer_exporting) else description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            if (busy) {
                CircularProgressIndicator(Modifier.size(28.dp))
            } else {
                OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text(button, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** 最近の書き出し（日時・ファイル名・大きさ）。表示だけで、ファイルは開かない。 */
@Composable
private fun RecentExports(recent: List<RecentExport>) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.transfer_recent_empty),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            recent.forEachIndexed { index, export ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = formatSnapshotTime(export.exportedAt),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.transfer_recent_line, export.fileName, formatSize(export.sizeBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** ファイルの大きさ（1MB 未満は KB で切り上げ、それ以上は MB で小数1桁）。 */
@Composable
internal fun formatSize(bytes: Long): String = if (bytes < 1024 * 1024) {
    stringResource(R.string.transfer_size_kb, ceil(bytes / 1024.0).toLong().coerceAtLeast(1).toString())
} else {
    stringResource(R.string.transfer_size_mb, String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0)))
}
