package io.github.eightbrows.appwatch.ui.setup

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.record.CheckOutcome
import io.github.eightbrows.appwatch.data.settings.StartDestination
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.model.UsageMode
import io.github.eightbrows.appwatch.ui.components.StatusMark
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import kotlinx.coroutines.launch

private const val STEP_COUNT = 4

/**
 * 初回設定（仕様書 6章、画面サンプル 15_initial_setup.png）。
 * 見守る端末は4画面、閲覧専用は1の画面のあと「取り込んだデータ」へ進む。
 * 戻る操作で前の画面へ。1の画面で戻るとアプリを閉じる（この画面が最初の画面なので）。
 *
 * @param resumeCount アプリに戻ってくるたびに増える数。設定画面から戻ったときに状態を読み直すのに使う
 * @param onCompleted 初回設定が終わったときに、次に開く画面を渡して呼ぶ
 */
@Composable
fun SetupScreen(
    controller: SetupController,
    resumeCount: Int,
    onCompleted: (StartDestination) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var mode by rememberSaveable { mutableStateOf(UsageMode.MONITOR) }
    var checkedNames by rememberSaveable {
        mutableStateOf(SetupSelection.DEFAULT_CHECKED.map { it.name })
    }
    // 最初のチェックは1回だけ行う（4から3に戻ったときに、もう一度行わない）
    var firstCheckDone by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = step > 1) { step-- }

    when (step) {
        1 -> ModeStep(
            selected = mode,
            onSelect = { mode = it },
            onNext = {
                scope.launch {
                    controller.chooseMode(mode)
                    if (mode == UsageMode.VIEW_ONLY) onCompleted(StartDestination.IMPORTED_DATA) else step = 2
                }
            },
        )

        2 -> SettingsStep(controller = controller, resumeCount = resumeCount, onNext = { step = 3 })

        3 -> FirstCheckStep(
            controller = controller,
            alreadyChecked = firstCheckDone,
            onChecked = { firstCheckDone = true },
            checked = checkedNames.mapNotNull { name -> SetupCategory.entries.firstOrNull { it.name == name } }.toSet(),
            onCheckedChange = { checked -> checkedNames = checked.map { it.name } },
            onNext = { step = 4 },
        )

        else -> WidgetStep(
            controller = controller,
            resumeCount = resumeCount,
            onFinish = {
                scope.launch {
                    controller.complete()
                    onCompleted(StartDestination.TOP)
                }
            },
        )
    }
}

// ---- 共通の形 ----

/** 上部に「初回設定 n / 4」と進み具合の点、真ん中に内容、下部に大きなボタン。 */
@Composable
private fun SetupScaffold(
    step: Int,
    title: String,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.setup_progress, step, STEP_COUNT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                ProgressDots(current = step)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = bottom,
        )
    }
}

/** 進み具合の点。今の画面は横長、済んだ画面は濃い色、まだの画面は薄い色。 */
@Composable
private fun ProgressDots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..STEP_COUNT) {
            val color = if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Box(
                Modifier
                    .size(width = if (i == current) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BodyText(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

/** 設定済みの印（✓ 設定済み）。 */
@Composable
private fun DoneLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_widget_check),
            contentDescription = null,
            tint = AppWatchTheme.colors.ok,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = AppWatchTheme.colors.ok)
    }
}

// ---- 1 使い方の選択 ----

@Composable
private fun ModeStep(selected: UsageMode, onSelect: (UsageMode) -> Unit, onNext: () -> Unit) {
    SetupScaffold(
        step = 1,
        title = stringResource(R.string.setup_mode_title),
        bottom = { PrimaryButton(stringResource(R.string.setup_next), onNext) },
    ) {
        BodyText(stringResource(R.string.setup_mode_body))
        ModeOption(
            title = stringResource(R.string.usage_mode_monitor),
            description = stringResource(R.string.setup_mode_monitor_desc),
            selected = selected == UsageMode.MONITOR,
            onClick = { onSelect(UsageMode.MONITOR) },
        )
        ModeOption(
            title = stringResource(R.string.usage_mode_view_only),
            description = stringResource(R.string.setup_mode_viewer_desc),
            selected = selected == UsageMode.VIEW_ONLY,
            onClick = { onSelect(UsageMode.VIEW_ONLY) },
        )
        Text(
            text = stringResource(R.string.setup_mode_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- 2 見守りを続けるための設定 ----

@Composable
private fun SettingsStep(controller: SetupController, resumeCount: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    // 設定画面から戻ってきたとき（resumeCount が増えたとき）に読み直す
    val batteryOk = remember(resumeCount) { controller.isBatteryOptimizationExcluded() }
    val unusedOk = remember(resumeCount) { controller.isUnusedAppRestrictionsExcluded() }
    val allOk = batteryOk && (unusedOk || !controller.showsUnusedAppRestrictions)

    fun open(intent: android.content.Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error_cannot_open_screen, Toast.LENGTH_LONG).show()
        }
    }

    SetupScaffold(
        step = 2,
        title = stringResource(R.string.setup_settings_title),
        bottom = {
            // 未設定が残っている間は「次へ」を押せない。「後で設定する」で進める
            PrimaryButton(stringResource(R.string.setup_next), onNext, enabled = allOk)
            if (!allOk) SecondaryButton(stringResource(R.string.setup_later), onNext)
        },
    ) {
        BodyText(stringResource(R.string.setup_settings_body))
        Card {
            SettingRow(
                title = stringResource(R.string.perm_battery),
                description = stringResource(R.string.setup_battery_desc),
                done = batteryOk,
                onConfigure = { open(controller.batteryOptimizationIntent()) },
            )
            if (controller.showsUnusedAppRestrictions) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = stringResource(R.string.perm_unused),
                    description = stringResource(R.string.setup_unused_desc),
                    done = unusedOk,
                    onConfigure = { open(controller.unusedAppRestrictionsIntent()) },
                )
            }
        }
        Text(
            text = stringResource(R.string.setup_permissions_note),
            style = MaterialTheme.typography.bodySmall,
            color = AppWatchTheme.colors.ok,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppWatchTheme.colors.ok.copy(alpha = 0.10f))
                .padding(14.dp),
        )
    }
}

@Composable
private fun SettingRow(title: String, description: String, done: Boolean, onConfigure: () -> Unit) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        if (done) {
            DoneLabel(stringResource(R.string.setup_configured))
        } else {
            Button(onClick = onConfigure, shape = RoundedCornerShape(10.dp)) {
                Text(stringResource(R.string.setup_configure), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ---- 3 最初のチェック ----

private sealed interface FirstCheckState {
    data object Running : FirstCheckState
    data object Failed : FirstCheckState
    data class Done(val apps: List<AppSummary>) : FirstCheckState
}

@Composable
private fun FirstCheckStep(
    controller: SetupController,
    alreadyChecked: Boolean,
    onChecked: () -> Unit,
    checked: Set<SetupCategory>,
    onCheckedChange: (Set<SetupCategory>) -> Unit,
    onNext: () -> Unit,
) {
    var state by remember { mutableStateOf<FirstCheckState>(FirstCheckState.Running) }
    var attempt by remember { mutableIntStateOf(0) }
    var applying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // この画面を開いたときにチェックを行う（初回なら基準を作る）。済んでいれば記録を読むだけ
    LaunchedEffect(attempt) {
        state = FirstCheckState.Running
        val failed = !alreadyChecked && controller.runFirstCheck() is CheckOutcome.Failed
        state = if (failed) {
            FirstCheckState.Failed
        } else {
            onChecked()
            FirstCheckState.Done(controller.presentApps())
        }
    }

    val current = state
    val title = when (current) {
        is FirstCheckState.Done -> pluralStringResource(R.plurals.setup_check_title, current.apps.size, current.apps.size)
        FirstCheckState.Failed -> stringResource(R.string.setup_check_failed)
        FirstCheckState.Running -> stringResource(R.string.setup_check_running)
    }
    SetupScaffold(
        step = 3,
        title = title,
        bottom = {
            when (current) {
                is FirstCheckState.Done -> PrimaryButton(
                    text = stringResource(R.string.setup_next),
                    enabled = !applying,
                    onClick = {
                        applying = true
                        scope.launch {
                            controller.markOk(checked)
                            applying = false
                            onNext()
                        }
                    },
                )
                FirstCheckState.Failed -> PrimaryButton(stringResource(R.string.action_retry), { attempt++ })
                FirstCheckState.Running -> PrimaryButton(stringResource(R.string.setup_next), {}, enabled = false)
            }
        },
    ) {
        when (current) {
            FirstCheckState.Running -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            FirstCheckState.Failed -> Unit

            is FirstCheckState.Done -> {
                BodyText(stringResource(R.string.setup_check_body))
                StatusLegend()
                BodyText(stringResource(R.string.setup_check_instruction))
                CategoryList(
                    counts = SetupSelection.countByCategory(current.apps),
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
                Text(
                    text = stringResource(R.string.setup_check_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusLegend() {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LegendRow(ReviewStatus.OK, stringResource(R.string.setup_status_ok_desc))
            LegendRow(ReviewStatus.UNCONFIRMED, stringResource(R.string.setup_status_unconfirmed_desc))
            LegendRow(ReviewStatus.CAUTION, stringResource(R.string.setup_status_caution_desc))
        }
    }
}

@Composable
private fun LegendRow(status: ReviewStatus, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusMark(status = status, size = 24.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(
                when (status) {
                    ReviewStatus.OK -> R.string.status_ok
                    ReviewStatus.UNCONFIRMED -> R.string.status_unconfirmed
                    ReviewStatus.CAUTION -> R.string.status_caution
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(72.dp),
        )
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryList(
    counts: Map<SetupCategory, Int>,
    checked: Set<SetupCategory>,
    onCheckedChange: (Set<SetupCategory>) -> Unit,
) {
    Card {
        SetupCategory.entries.forEachIndexed { index, category ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val isChecked = category in checked
            CategoryRow(
                category = category,
                count = counts[category] ?: 0,
                checked = isChecked,
                onToggle = { onCheckedChange(if (isChecked) checked - category else checked + category) },
            )
        }
    }
}

@Composable
private fun CategoryRow(category: SetupCategory, count: Int, checked: Boolean, onToggle: () -> Unit) {
    // 入手元に注意が必要な項目（Google Play 以外・入手元が分からない）はオレンジで出す
    val warns = category == SetupCategory.OTHER_SOURCE || category == SetupCategory.UNKNOWN_SOURCE
    val accent = if (warns) AppWatchTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
    val title = when (category) {
        SetupCategory.SYSTEM -> stringResource(R.string.kind_system)
        SetupCategory.PREINSTALLED -> stringResource(R.string.kind_preinstalled)
        SetupCategory.GOOGLE_PLAY -> stringResource(R.string.source_google_play)
        SetupCategory.OTHER_SOURCE -> stringResource(R.string.source_other)
        SetupCategory.UNKNOWN_SOURCE -> stringResource(R.string.source_unknown)
        SetupCategory.SELF -> stringResource(R.string.setup_category_self, stringResource(R.string.app_name))
    }
    val description = when (category) {
        SetupCategory.SYSTEM -> R.string.setup_category_system_desc
        SetupCategory.PREINSTALLED -> R.string.setup_category_preinstalled_desc
        SetupCategory.GOOGLE_PLAY -> R.string.setup_category_play_desc
        SetupCategory.OTHER_SOURCE -> R.string.setup_category_other_desc
        SetupCategory.UNKNOWN_SOURCE -> R.string.setup_category_unknown_desc
        SetupCategory.SELF -> R.string.setup_category_self_desc
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = { onToggle() }, role = Role.Checkbox)
            .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = stringResource(description), style = MaterialTheme.typography.bodySmall, color = accent)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = pluralStringResource(R.plurals.count_apps, count, count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (warns) AppWatchTheme.colors.warning else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---- 4 ウィジェットの追加 ----

@Composable
private fun WidgetStep(controller: SetupController, resumeCount: Int, onFinish: () -> Unit) {
    // 追加の確認から戻ってきたとき（resumeCount が増えたとき）に読み直す
    val placed = remember(resumeCount) { controller.isWidgetPlaced() }
    val canPin = remember { controller.canRequestWidgetPin() }
    var finishing by remember { mutableStateOf(false) }

    SetupScaffold(
        step = 4,
        title = stringResource(R.string.setup_widget_title),
        bottom = {
            // ウィジェットを置かなくても完了できる
            PrimaryButton(
                text = stringResource(R.string.setup_finish),
                enabled = !finishing,
                onClick = {
                    finishing = true
                    onFinish()
                },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_widget_list),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.setup_widget_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Card {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setup_widget_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(if (canPin || placed) R.string.setup_widget_card_desc else R.string.setup_widget_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    placed -> {
                        Spacer(Modifier.width(10.dp))
                        DoneLabel(stringResource(R.string.setup_widget_added))
                    }
                    canPin -> {
                        Spacer(Modifier.width(10.dp))
                        Button(onClick = { controller.requestWidgetPin() }, shape = RoundedCornerShape(10.dp)) {
                            Text(stringResource(R.string.setup_widget_add), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    // 対応していないホーム画面では、ボタンの代わりに置き方を文字で案内する（上の説明文）
                    else -> Unit
                }
            }
        }
    }
}
