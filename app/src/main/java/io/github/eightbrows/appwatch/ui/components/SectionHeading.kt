package io.github.eightbrows.appwatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

/** 見出しの色の組（左の縦棒と、件数の字）。確認状態の印・カードの枠と同じ色。 */
enum class SectionTone { RED, ORANGE, GRAY, GREEN, BLUE }

/**
 * 一覧の中の見出し（トップの「確認が必要」のタブ、確認状態の画面で共通）。
 * 左に色の短い縦棒、名前（14sp の太字）、件数（同じ色の太字）。一覧と一緒にスクロールする。読み上げでは見出しとして読む。
 */
@Composable
fun SectionHeading(title: String, count: Int, tone: SectionTone, modifier: Modifier = Modifier) {
    val colors = AppWatchTheme.colors
    val (bar, text) = when (tone) {
        SectionTone.RED -> colors.danger to colors.danger
        SectionTone.ORANGE -> colors.warningStrong to colors.warning
        SectionTone.GRAY -> colors.unconfirmed to MaterialTheme.colorScheme.onSurfaceVariant
        SectionTone.GREEN -> colors.ok to colors.ok
        // 日付の見出し（確認状態の画面の「最近の更新」）。確認状態の色と重ならない青
        SectionTone.BLUE -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .background(bar, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.section_count, count),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = text,
        )
    }
}

/** 確認状態の画面の見出しの色（要注意は赤、未確認はオレンジ、問題なしは緑）。 */
fun ReviewStatus.sectionTone(): SectionTone = when (this) {
    ReviewStatus.CAUTION -> SectionTone.RED
    ReviewStatus.UNCONFIRMED -> SectionTone.ORANGE
    ReviewStatus.OK -> SectionTone.GREEN
}
