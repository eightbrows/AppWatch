package io.github.eightbrows.appwatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

/** タブの1つ：名前、数（まだ分からない時は null）、数の札の地の色。 */
class SegmentTab(val label: String, val count: Int?, val countColor: Color)

/**
 * 帯の形のタブ（トップの「確認が必要／すべて」、確認状態の画面の「状態別／最近の更新」で共通）。
 * 数は小さな丸い札（白い字）。高さは上下 4dp の余白と、最低 48dp の帯。文字が大きい時は帯がのびる。
 * 読み上げは「名前、n件」と、選択中かどうか。
 */
@Composable
fun SegmentTabs(tabs: List<SegmentTab>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppWatchTheme.colors
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.segmentTrack)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            SegmentTabItem(
                tab = tab,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentTabItem(tab: SegmentTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = AppWatchTheme.colors
    val count = tab.count
    val description = if (count != null) {
        pluralStringResource(R.plurals.top_tab_desc, count, tab.label, count)
    } else {
        tab.label
    }
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(if (selected) Modifier.shadow(1.dp, shape).background(colors.segmentSelected, shape) else Modifier)
            .clip(shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Tab
                this.selected = selected
                onClick { onClick(); true }
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = tab.label,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    modifier = Modifier
                        .background(tab.countColor, RoundedCornerShape(50))
                        .padding(horizontal = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}
