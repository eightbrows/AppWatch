package io.github.eightbrows.appwatch.ui.status

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.status.AppStatusEntry
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.ui.components.StatusMark
import io.github.eightbrows.appwatch.ui.components.labelRes
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

/**
 * アプリの詳細（本人）。画面サンプル 03_app_detail_user.png。管理者用の詳細と同じ部品・文字の大きさで、見るだけ。
 * 「削除する」は後から導入のアプリだけ（本アプリなら削除警告）。削除して戻ってきたら、チェックの後にトップへ戻る。
 */
@Composable
fun UserAppDetailRoute(
    controller: AppStatusController,
    packageName: String,
    onBack: () -> Unit,
    /** Android のアンインストール画面を開く（本アプリなら先に削除警告を出す） */
    onUninstall: (AppStatusEntry) -> Unit,
    onOpenAppInfo: (AppStatusEntry) -> Unit,
) {
    val detail by remember(packageName) { controller.localDetail(packageName) }.collectAsState(initial = null)
    val entry = detail?.entry
    // 「削除する」を押したか。戻ってきてチェックで端末から消えたのが分かったら、トップへ戻る
    var uninstallRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(entry?.isPresent) {
        if (uninstallRequested && entry != null && !entry.isPresent) onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        UserTopBar(onBack)
        val current = detail
        if (current == null || entry == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (current == null) CircularProgressIndicator()
            }
            return@Column
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(entry)
            WarningBox(entry, current.events)
            SectionLabel(stringResource(R.string.detail_status))
            StatusView(entry.status)
            entry.adminMemo?.takeIf { it.isNotBlank() }?.let { memo ->
                SectionLabel(stringResource(R.string.app_admin_memo))
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = memo,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            SectionLabel(stringResource(R.string.detail_history))
            History(current.events)
        }
        UserBottomButtons(
            entry = entry,
            onUninstall = {
                uninstallRequested = true
                onUninstall(entry)
            },
            onOpenAppInfo = { onOpenAppInfo(entry) },
        )
    }
}

/** 本人用の上部（戻る・題名）。「管理者用」の印は出さない。 */
@Composable
private fun UserTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
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
            text = stringResource(R.string.detail_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** 確認の状態（見るだけ）。「未確認」以外は「（管理者が設定）」を添える。 */
@Composable
private fun StatusView(status: ReviewStatus) {
    val colors = AppWatchTheme.colors
    val color = when (status) {
        ReviewStatus.CAUTION -> colors.danger
        ReviewStatus.OK -> colors.ok
        ReviewStatus.UNCONFIRMED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusMark(status = status, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(status.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            if (status != ReviewStatus.UNCONFIRMED) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_status_by_admin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 下部の「削除する」（後から導入のアプリだけ）と「Android のアプリ情報」。 */
@Composable
private fun UserBottomButtons(entry: AppStatusEntry, onUninstall: () -> Unit, onOpenAppInfo: () -> Unit) {
    val colors = AppWatchTheme.colors
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entry.isPresent && entry.kind == AppKind.USER_INSTALLED) {
                Button(
                    onClick = onUninstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = colors.onDanger),
                ) {
                    Text(stringResource(R.string.action_delete_app), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = onOpenAppInfo,
                enabled = entry.isPresent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    stringResource(R.string.action_app_info),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (entry.isPresent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
