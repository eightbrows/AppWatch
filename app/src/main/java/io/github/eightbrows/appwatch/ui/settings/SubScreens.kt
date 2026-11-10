package io.github.eightbrows.appwatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.settings.CheckInterval
import io.github.eightbrows.appwatch.ui.components.ScreenTopBar
import io.github.eightbrows.appwatch.ui.components.formatDate
import io.github.eightbrows.appwatch.ui.components.withDaysAgo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

// 設定メニューから開く画面（動作チェック、ライセンス、後のステップで作る画面の仮の画面）。

/**
 * 設定メニューから開く画面の共通の形（上部の「戻る・題名・管理者用」と、スクロールする中身）。
 * 動作チェックはトップからも開くので、[showAdminBadge] を false にして「管理者用」の印を出さない。
 */
@Composable
private fun AdminPage(
    title: String,
    onBack: () -> Unit,
    showAdminBadge: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScreenTopBar(title = title, onBack = onBack, showAdminBadge = showAdminBadge)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp)
            .semantics { heading() },
    )
}

/** 「項目　値」の1行。 */
@Composable
private fun InfoRow(label: String, value: String, valueColor: Color? = null, bold: Boolean = false) =
    InfoRow(label, AnnotatedString(value), valueColor, bold)

/**
 * [valueWraps] が true なら、項目名を折り返さず、長い値（日時など）の方を右寄せで折り返す。
 */
@Composable
private fun InfoRow(
    label: String,
    value: AnnotatedString,
    valueColor: Color? = null,
    bold: Boolean = false,
    valueWraps: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = if (valueWraps) Modifier else Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = if (valueWraps) TextAlign.End else null,
            modifier = if (valueWraps) Modifier.weight(1f) else Modifier,
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

// ---- 動作チェック ----

/** 動作チェックに出す値。 */
data class HealthCheckState(
    val allOk: Boolean,
    val stopped: Boolean,
    val batteryOptimizationExcluded: Boolean,
    val unusedAppRestrictionsExcluded: Boolean,
    val showsUnusedAppRestrictions: Boolean,
    val lastCheckAt: Long?,
    val checkInterval: CheckInterval,
    val checksInLast7Days: Int,
    val periodicCheckRegistered: Boolean,
    /** 下部に出すアプリのバージョン */
    val versionName: String,
)

/**
 * 動作チェック（仕様書 6章「設定メニュー」）：各設定の状態、最終チェック日時、チェック間隔、直近7日のチェック回数、
 * 定期チェックが登録されているか。判定はウィジェットの「設定の確認が必要です」「停止」と同じ条件。
 * 設定メニューからもトップからも開く同じ画面で、「管理者用」の印は出さない。下部にアプリのバージョンを出す。
 */
@Composable
fun HealthCheckScreen(state: HealthCheckState, onBack: () -> Unit) {
    val colors = AppWatchTheme.colors
    AdminPage(title = stringResource(R.string.settings_health_check), onBack = onBack, showAdminBadge = false) {
        // 全体の結果
        Text(
            text = stringResource(if (state.allOk) R.string.health_all_ok else R.string.health_needs_attention),
            style = MaterialTheme.typography.titleLarge,
            color = if (state.allOk) colors.ok else colors.onWarningContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (state.allOk) colors.ok.copy(alpha = 0.12f) else colors.warningContainer)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )

        SectionLabel(stringResource(R.string.health_section_settings))
        Card {
            StatusRow(stringResource(R.string.perm_battery), state.batteryOptimizationExcluded)
            if (state.showsUnusedAppRestrictions) {
                Divider()
                StatusRow(stringResource(R.string.perm_unused), state.unusedAppRestrictionsExcluded)
            }
        }

        SectionLabel(stringResource(R.string.health_section_check))
        Card {
            val lastCheck = state.lastCheckAt?.let { withDaysAgo(formatDate(it), it) }
                ?: AnnotatedString(stringResource(R.string.health_never_checked))
            InfoRow(
                label = stringResource(R.string.health_last_check),
                value = lastCheck,
                valueColor = if (state.stopped) colors.warning else null,
                bold = state.stopped,
                valueWraps = true,
            )
            Divider()
            InfoRow(stringResource(R.string.settings_check_interval), stringResource(state.checkInterval.labelRes))
            Divider()
            InfoRow(
                label = stringResource(R.string.health_checks_7days),
                value = pluralStringResource(R.plurals.health_check_count, state.checksInLast7Days, state.checksInLast7Days),
            )
            Divider()
            InfoRow(
                label = stringResource(R.string.health_periodic),
                value = stringResource(if (state.periodicCheckRegistered) R.string.health_registered else R.string.health_not_registered),
                valueColor = if (state.periodicCheckRegistered) colors.ok else colors.warning,
                bold = true,
            )
        }
        if (state.stopped) {
            Text(
                text = stringResource(R.string.health_stopped_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onWarningContainer,
            )
        }
        Text(
            text = stringResource(R.string.health_version, state.versionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    val colors = AppWatchTheme.colors
    InfoRow(
        label = label,
        value = stringResource(if (ok) R.string.state_configured else R.string.state_not_configured),
        valueColor = if (ok) colors.ok else colors.warning,
        bold = true,
    )
}

// ---- ライセンス ----

/**
 * ライセンス：本アプリ（Apache-2.0）と、使っている部品の名前とライセンスの一覧。
 * 全文はアプリに入れず、全文を見たい人のためにリポジトリを開くボタンを置く。
 */
@Composable
fun LicenseScreen(onBack: () -> Unit, onOpenRepository: () -> Unit) {
    val names = stringArrayResource(R.array.license_component_names)
    val licenses = stringArrayResource(R.array.license_component_licenses)
    AdminPage(title = stringResource(R.string.settings_license), onBack = onBack) {
        SectionLabel(stringResource(R.string.license_app_heading))
        Card {
            InfoRow(stringResource(R.string.app_name), stringResource(R.string.license_apache2))
        }
        SectionLabel(stringResource(R.string.license_components_heading))
        Card {
            names.forEachIndexed { index, name ->
                if (index > 0) Divider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(text = name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = licenses.getOrElse(index) { "" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.license_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onOpenRepository, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(stringResource(R.string.license_open_repository))
        }
        Spacer(Modifier.height(8.dp))
    }
}

