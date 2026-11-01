package io.github.eightbrows.appwatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.transfer.DataSelection
import io.github.eightbrows.appwatch.data.transfer.DatasetOption
import java.time.format.DateTimeFormatter

/** 選択肢の名前。この端末のデータは「この端末」。 */
@Composable
fun DatasetOption.label(): String = if (isLocal) stringResource(R.string.data_this_device) else name

/** 取り込んだデータの「時点」の日時（例：2026/10/28 21:00）。 */
@Composable
fun formatSnapshotTime(time: Long): String {
    val pattern = stringResource(R.string.data_snapshot_time)
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    return time.toLocalDateTime().format(formatter)
}

/**
 * データの切り替え（13_app_status_viewing.png の上部）と、取り込んだデータを見ているときの「閲覧中」の帯。
 * 取り込んだデータがないときは何も出さない。
 */
@Composable
fun DataSwitcher(selection: DataSelection, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if (!selection.showsSwitcher) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var expanded by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(12.dp)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .clickable(role = Role.DropdownList, onClickLabel = stringResource(R.string.data_switch)) { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.data_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = selection.selected?.label().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
                Text(
                    text = pluralStringResource(R.plurals.data_imported_count, selection.importedCount, selection.importedCount),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "▾", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                selection.options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label(),
                                fontWeight = if (option.id == selection.selected?.id) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(option.id)
                        },
                    )
                }
            }
        }
        ViewingBand(selection)
    }
}

/** 取り込んだデータを見ているときの帯「閲覧中：端末A（2026/10/28 21:00 時点）」。 */
@Composable
fun ViewingBand(selection: DataSelection, modifier: Modifier = Modifier) {
    val selected = selection.selected ?: return
    if (selected.isLocal) return
    val time = selected.exportedAt?.let { formatSnapshotTime(it) }.orEmpty()
    Text(
        text = stringResource(R.string.data_viewing_band, selected.name, time),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

/** 閲覧中の画面の下部「閲覧中のデータは変更できません」。 */
@Composable
fun ViewingFooter(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.data_viewing_read_only),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
