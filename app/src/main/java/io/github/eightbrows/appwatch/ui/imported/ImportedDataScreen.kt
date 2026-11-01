package io.github.eightbrows.appwatch.ui.imported

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.ui.components.AdminTopBar
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import io.github.eightbrows.appwatch.ui.top.LongPressSettingsButton

/**
 * 取り込んだデータ（閲覧専用の端末の最初の画面、画面サンプル 12_imported_data.png）。
 * このステップでは仮の画面で、見出しと「まだ取り込んだデータはありません」だけを出す（取り込みは後のステップ）。
 * 右上の歯車は、トップと同じく長押しで設定メニューを開く（使い方を変えられるようにするため）。
 */
@Composable
fun ImportedDataScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** 設定メニューから開いたときの戻る操作。最初の画面（閲覧専用）なら null で、代わりに歯車を出す */
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) {
        Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AdminTopBar(title = stringResource(R.string.imported_title), onBack = onBack)
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { EmptyCard() }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.imported_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.imported_badge),
                style = MaterialTheme.typography.labelSmall,
                color = AppWatchTheme.colors.onAdminBadge,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppWatchTheme.colors.adminBadge)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            LongPressSettingsButton(onLongPress = onOpenSettings)
        }
        EmptyCard()
    }
}

@Composable
private fun EmptyCard() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.imported_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        )
    }
}
