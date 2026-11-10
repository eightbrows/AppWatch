package io.github.eightbrows.appwatch.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.model.ReviewReason
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import kotlinx.coroutines.launch

/**
 * 注意の説明（下から出る画面、仕様書 6章「注意の説明」）。
 * 6つの注意をいつも同じ順で並べ、[applying]（札に出している理由）に当てはまるものだけにチェックと色を付ける。
 * 入りきらないときは注意の一覧がスクロールする。「閉じる」はいつも見える。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonExplanationSheet(appName: String, applying: List<ReviewReason>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = AppWatchTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.reason_sheet_title, appName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.reason_sheet_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ReviewReason.explanationItems(applying).forEach { (reason, applies) -> ExplanationItem(reason, applies) }
            }
            Button(
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.safeAction, contentColor = colors.onSafeAction),
            ) {
                Text(stringResource(R.string.reason_sheet_close), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 注意の1行。当てはまるものはチェックと色（警告：オレンジ系、要注意：赤系）、当てはまらないものは灰色。 */
@Composable
private fun ExplanationItem(reason: ReviewReason, applies: Boolean) {
    val colors = AppWatchTheme.colors
    val (container, border, mark) = when {
        !applies -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outline)
        reason.isCaution -> Triple(colors.dangerContainer, colors.danger.copy(alpha = 0.45f), colors.danger)
        else -> Triple(colors.warningContainer, colors.warningBorder, colors.warningStrong)
    }
    val state = stringResource(if (applies) R.string.reason_applies else R.string.reason_not_applies)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.5.dp, border, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            // チェックの有無を読み上げでも分かるようにする
            .semantics(mergeDescendants = true) { stateDescription = state },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(26.dp)
                .clip(CircleShape)
                .then(if (applies) Modifier.background(mark) else Modifier.border(2.dp, mark, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (applies) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.onDanger,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(reason.explanationTitleRes()),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (applies) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(reason.explanationRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 説明の行の見出し。要注意だけ「（管理者が設定）」を添える。 */
@StringRes
private fun ReviewReason.explanationTitleRes(): Int =
    if (this == ReviewReason.CAUTION) R.string.reason_sheet_caution_title else labelRes()

@StringRes
private fun ReviewReason.explanationRes(): Int = when (this) {
    ReviewReason.NOT_FROM_PLAY -> R.string.reason_desc_not_from_play
    ReviewReason.UNKNOWN_SOURCE -> R.string.reason_desc_unknown_source
    ReviewReason.DEBUG_SIGNATURE -> R.string.reason_desc_debug_signature
    ReviewReason.SIGNATURE_CHANGED -> R.string.reason_desc_signature_changed
    ReviewReason.REINSTALLED -> R.string.reason_desc_reinstalled
    ReviewReason.CAUTION -> R.string.reason_desc_caution
}
