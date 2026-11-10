package io.github.eightbrows.appwatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.eightbrows.appwatch.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.model.ReviewReason
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

/**
 * 確認が必要になった理由の札を並べる（幅が足りなければ次の行へ）。
 * 色だけに頼らず、札の文字で理由が分かるようにしている。[reasons] が空なら何も出さない。
 * [onClick] があれば、札のどこを押しても注意の説明を開く。
 */
@Composable
fun ReasonChips(reasons: List<ReviewReason>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (reasons.isEmpty()) return
    val clickLabel = stringResource(R.string.reason_open_explanation)
    FlowRow(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        reasons.forEach { ReasonChip(it) }
    }
}

/** 理由の札1つ。警告はオレンジ系、要注意は赤系の背景に、読める濃さの文字。 */
@Composable
fun ReasonChip(reason: ReviewReason, modifier: Modifier = Modifier) {
    val colors = AppWatchTheme.colors
    val (container, content) = if (reason.isCaution) {
        colors.dangerChip to colors.onDangerChip
    } else {
        colors.warningChip to colors.onWarningChip
    }
    Text(
        text = stringResource(reason.labelRes()),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = content,
    )
}
