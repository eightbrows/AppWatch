package io.github.eightbrows.appwatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import kotlin.math.absoluteValue

/** 管理者用の画面の右上に出す印。 */
@Composable
fun AdminBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.admin_badge),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppWatchTheme.colors.adminBadge)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = AppWatchTheme.colors.onAdminBadge,
        style = MaterialTheme.typography.labelSmall,
    )
}

/** 管理者用の画面の上部（戻る・題名・「管理者用」の印）。 */
@Composable
fun AdminTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        AdminBadge()
    }
}

/** 横並びの切り替えボタン（トップの切り替え、テーマ、言語）。 */
@Composable
fun <T> SegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 44.dp,
) {
    val colors = AppWatchTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.segmentTrack)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                selected = isSelected,
                onClick = { onSelect(value) },
                modifier = Modifier
                    .weight(1f)
                    .height(itemHeight)
                    .semantics { role = Role.Tab },
                shape = RoundedCornerShape(9.dp),
                color = if (isSelected) colors.segmentSelected else Color.Transparent,
                shadowElevation = if (isSelected) 1.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val IconPalette = listOf(
    Color(0xFF6E5A9E), Color(0xFF5B6770), Color(0xFF2F7BC0),
    Color(0xFF2E8B6E), Color(0xFFB0653A), Color(0xFF8E4D6E),
)

/**
 * アプリのアイコン。今は名前の1文字目を色付きの四角に出す。
 * 後のステップで、端末から取得したアイコンに差し替える。
 */
@Composable
fun AppIcon(name: String, packageName: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val color = IconPalette[packageName.hashCode().absoluteValue % IconPalette.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1),
            color = Color.White,
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 確認状態の印（未確認：灰色の点線の丸に「?」／問題なし：緑の丸に「✓」／要注意：赤の丸に「!」）。 */
@Composable
fun StatusMark(status: ReviewStatus, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val colors = AppWatchTheme.colors
    val description = stringResource(status.labelRes())
    val textSize = (size.value * 0.55f).sp
    val (background, foreground, mark) = when (status) {
        ReviewStatus.UNCONFIRMED -> Triple(Color.Transparent, colors.unconfirmed, R.string.mark_unconfirmed)
        ReviewStatus.OK -> Triple(colors.ok, colors.onOk, R.string.mark_ok)
        ReviewStatus.CAUTION -> Triple(colors.danger, colors.onDanger, R.string.mark_caution)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (status == ReviewStatus.UNCONFIRMED) {
            Canvas(Modifier.matchParentSize()) {
                val strokeWidth = 1.5.dp.toPx()
                drawCircle(
                    color = colors.unconfirmed,
                    radius = (this.size.minDimension - strokeWidth) / 2f,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())),
                    ),
                )
            }
        }
        Text(
            text = stringResource(mark),
            color = foreground,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
        )
    }
}
