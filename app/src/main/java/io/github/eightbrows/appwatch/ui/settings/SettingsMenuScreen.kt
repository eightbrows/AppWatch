package io.github.eightbrows.appwatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.settings.AppLanguage
import io.github.eightbrows.appwatch.data.settings.CheckInterval
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.model.UsageMode
import io.github.eightbrows.appwatch.ui.components.AdminTopBar
import io.github.eightbrows.appwatch.ui.components.SegmentedToggle
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

/** 設定メニューに出す値。 */
data class SettingsMenuState(
    /** この端末の「要注意」の件数（0件なら印を出さない） */
    val cautionCount: Int,
    /** 取り込んだデータの名前（まだないので空） */
    val importedDataNames: List<String>,
    val usageMode: UsageMode,
    val checkInterval: CheckInterval,
    /** 動作チェックが「すべて正常」か */
    val healthAllOk: Boolean,
    val themeMode: ThemeMode,
    val language: AppLanguage,
    val batteryOptimizationExcluded: Boolean,
    val unusedAppRestrictionsExcluded: Boolean,
    /** 未使用アプリの一時停止の行を出すか（Android 11 以上） */
    val showsUnusedAppRestrictions: Boolean,
    val versionName: String,
)

/** 設定メニューのボタンを押したときの処理。 */
data class SettingsMenuActions(
    val onBack: () -> Unit,
    val onOpenAppStatus: () -> Unit,
    val onOpenPreRegister: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenExportImport: () -> Unit,
    val onOpenImportedData: () -> Unit,
    /** 使い方を選んだとき（閲覧専用にするときは、確認を済ませてから呼ぶ） */
    val onUsageModeChange: (UsageMode) -> Unit,
    val onCheckIntervalChange: (CheckInterval) -> Unit,
    val onOpenHealthCheck: () -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onLanguageChange: (AppLanguage) -> Unit,
    val onOpenBatterySettings: () -> Unit,
    /** 未使用アプリの一時停止の設定画面を開く（案内を見せてから呼ぶ） */
    val onOpenUnusedAppSettings: () -> Unit,
    val onOpenLicenses: () -> Unit,
    val onOpenWebsite: () -> Unit,
)

/** 開いているダイアログ。 */
private enum class MenuDialog { NONE, USAGE_MODE, VIEW_ONLY_CONFIRM, CHECK_INTERVAL, UNUSED_GUIDE }

/**
 * 設定メニュー（管理者）。画面サンプル 05_settings_menu.png、仕様書 6章「設定メニュー」。
 * 閲覧専用の端末では「チェック間隔」「動作チェック」と「権限」の区分を出さない。
 *
 * @param unusedAppHint 未使用アプリの一時停止の設定画面で、どのスイッチを切り替えるかの案内
 */
@Composable
fun SettingsMenuScreen(
    state: SettingsMenuState,
    unusedAppHint: String,
    actions: SettingsMenuActions,
    modifier: Modifier = Modifier,
) {
    val colors = AppWatchTheme.colors
    val navigationBars = WindowInsets.navigationBars.asPaddingValues()
    val isViewOnly = state.usageMode == UsageMode.VIEW_ONLY
    var dialog by rememberSaveable { mutableStateOf(MenuDialog.NONE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AdminTopBar(title = stringResource(R.string.settings_title), onBack = actions.onBack)
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = navigationBars.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "watch") {
                SettingsSection(stringResource(R.string.settings_section_watch)) {
                    SettingsRow(
                        title = stringResource(R.string.settings_app_status),
                        description = stringResource(R.string.settings_app_status_desc),
                        onClick = actions.onOpenAppStatus,
                        trailing = {
                            if (state.cautionCount > 0) {
                                Text(
                                    text = pluralStringResource(R.plurals.settings_caution_count, state.cautionCount, state.cautionCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onDanger,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.danger)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_pre_register),
                        description = stringResource(R.string.settings_pre_register_desc),
                        onClick = actions.onOpenPreRegister,
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_history),
                        description = stringResource(R.string.settings_history_desc),
                        onClick = actions.onOpenHistory,
                    )
                }
            }

            item(key = "data") {
                SettingsSection(stringResource(R.string.settings_section_data)) {
                    SettingsRow(
                        title = stringResource(R.string.settings_export_import),
                        description = stringResource(R.string.settings_export_import_desc),
                        onClick = actions.onOpenExportImport,
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_imported_data),
                        description = if (state.importedDataNames.isEmpty()) {
                            stringResource(R.string.settings_imported_data_none)
                        } else {
                            state.importedDataNames.joinToString(stringResource(R.string.settings_list_separator))
                        },
                        onClick = actions.onOpenImportedData,
                    )
                }
            }

            item(key = "behavior") {
                SettingsSection(stringResource(R.string.settings_section_behavior)) {
                    SettingsRow(
                        title = stringResource(R.string.settings_usage_mode),
                        onClick = { dialog = MenuDialog.USAGE_MODE },
                        trailing = { ValueText(stringResource(usageModeLabel(state.usageMode))) },
                    )
                    // 閲覧専用の端末では、チェック間隔と動作チェックを出さない
                    if (!isViewOnly) {
                        RowDivider()
                        SettingsRow(
                            title = stringResource(R.string.settings_check_interval),
                            onClick = { dialog = MenuDialog.CHECK_INTERVAL },
                            trailing = { ValueText(stringResource(state.checkInterval.labelRes)) },
                        )
                        RowDivider()
                        SettingsRow(
                            title = stringResource(R.string.settings_health_check),
                            onClick = actions.onOpenHealthCheck,
                            trailing = {
                                if (state.healthAllOk) {
                                    StateText(stringResource(R.string.health_all_ok), colors.ok)
                                } else {
                                    StateText(stringResource(R.string.health_needs_attention), colors.warning)
                                }
                            },
                        )
                    }
                }
            }

            item(key = "display") {
                SettingsSection(stringResource(R.string.settings_section_display)) {
                    ToggleRow(
                        title = stringResource(R.string.settings_theme),
                        description = stringResource(R.string.settings_theme_desc),
                    ) {
                        SegmentedToggle(
                            options = listOf(
                                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                                ThemeMode.DARK to stringResource(R.string.theme_dark),
                            ),
                            selected = state.themeMode,
                            onSelect = actions.onThemeModeChange,
                            textStyle = CompactToggleText,
                        )
                    }
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_language),
                        description = stringResource(R.string.settings_language_desc),
                    ) {
                        SegmentedToggle(
                            options = listOf(
                                AppLanguage.SYSTEM to stringResource(R.string.language_system),
                                AppLanguage.JAPANESE to stringResource(R.string.language_japanese),
                                AppLanguage.ENGLISH to stringResource(R.string.language_english),
                            ),
                            selected = state.language,
                            onSelect = actions.onLanguageChange,
                            textStyle = CompactToggleText,
                        )
                    }
                }
            }

            // 閲覧専用の端末では、権限の区分を出さない（この端末のアプリを記録しないため）
            if (!isViewOnly) {
                item(key = "permissions") {
                    SettingsSection(stringResource(R.string.settings_section_permissions)) {
                        PermissionRow(
                            title = stringResource(R.string.perm_query_all),
                            description = stringResource(R.string.perm_query_all_desc),
                            ok = true,
                            okText = stringResource(R.string.state_granted),
                        )
                        RowDivider()
                        PermissionRow(
                            title = stringResource(R.string.perm_delete),
                            description = stringResource(R.string.perm_delete_desc),
                            ok = true,
                            okText = stringResource(R.string.state_granted),
                        )
                        RowDivider()
                        PermissionRow(
                            title = stringResource(R.string.perm_battery),
                            description = stringResource(R.string.perm_battery_desc),
                            ok = state.batteryOptimizationExcluded,
                            okText = stringResource(R.string.state_configured),
                            onClick = actions.onOpenBatterySettings,
                        )
                        // 未使用アプリの一時停止は Android 11 以上の仕組み（仕様書 2章）
                        if (state.showsUnusedAppRestrictions) {
                            RowDivider()
                            PermissionRow(
                                title = stringResource(R.string.perm_unused),
                                description = stringResource(R.string.perm_unused_desc),
                                ok = state.unusedAppRestrictionsExcluded,
                                okText = stringResource(R.string.state_configured),
                                onClick = { dialog = MenuDialog.UNUSED_GUIDE },
                            )
                        }
                    }
                }
            }

            item(key = "info") {
                SettingsSection(stringResource(R.string.settings_section_info)) {
                    SettingsRow(
                        title = stringResource(R.string.settings_version),
                        trailing = {
                            Text(
                                text = state.versionName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_license),
                        description = stringResource(R.string.settings_license_desc),
                        onClick = actions.onOpenLicenses,
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_website),
                        description = stringResource(R.string.settings_website_url),
                        onClick = actions.onOpenWebsite,
                    )
                }
            }
        }
    }

    when (dialog) {
        MenuDialog.USAGE_MODE -> ChoiceDialog(
            title = stringResource(R.string.settings_usage_mode),
            options = UsageMode.entries.map { it to stringResource(usageModeLabel(it)) },
            selected = state.usageMode,
            onDismiss = { dialog = MenuDialog.NONE },
            onSelect = { mode ->
                dialog = when {
                    mode == state.usageMode -> MenuDialog.NONE
                    // 閲覧専用にするときは確認を出す
                    mode == UsageMode.VIEW_ONLY -> MenuDialog.VIEW_ONLY_CONFIRM
                    else -> {
                        actions.onUsageModeChange(mode)
                        MenuDialog.NONE
                    }
                }
            },
        )

        MenuDialog.VIEW_ONLY_CONFIRM -> AlertDialog(
            onDismissRequest = { dialog = MenuDialog.NONE },
            title = { Text(stringResource(R.string.viewonly_confirm_title)) },
            text = { Text(stringResource(R.string.viewonly_confirm_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = MenuDialog.NONE
                    actions.onUsageModeChange(UsageMode.VIEW_ONLY)
                }) { Text(stringResource(R.string.viewonly_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = MenuDialog.NONE }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )

        MenuDialog.CHECK_INTERVAL -> ChoiceDialog(
            title = stringResource(R.string.settings_check_interval),
            options = CheckInterval.entries.map { it to stringResource(it.labelRes) },
            selected = state.checkInterval,
            onDismiss = { dialog = MenuDialog.NONE },
            onSelect = { interval ->
                dialog = MenuDialog.NONE
                if (interval != state.checkInterval) actions.onCheckIntervalChange(interval)
            },
        )

        MenuDialog.UNUSED_GUIDE -> AlertDialog(
            onDismissRequest = { dialog = MenuDialog.NONE },
            title = { Text(stringResource(R.string.perm_unused)) },
            text = { Text(unusedAppHint, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = MenuDialog.NONE
                    actions.onOpenUnusedAppSettings()
                }) { Text(stringResource(R.string.unused_dialog_open)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = MenuDialog.NONE }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )

        MenuDialog.NONE -> Unit
    }
}

/** 3つの選択肢の切り替えボタンの文字（画面サンプルと同じく小さめ。幅の狭い端末でも「システムに従う」が切れない） */
private val CompactToggleText: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)

private fun usageModeLabel(mode: UsageMode): Int = when (mode) {
    UsageMode.MONITOR -> R.string.usage_mode_monitor
    UsageMode.VIEW_ONLY -> R.string.usage_mode_view_only
}

/** 選択肢から1つ選ぶダイアログ（使い方・チェック間隔）。選ぶとすぐに閉じる。 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .selectable(selected = value == selected, onClick = { onSelect(value) }, role = Role.RadioButton)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp, bottom = 6.dp)
                .semantics { heading() },
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 設定メニューの1行。[onClick] があれば押せる行になり、右に「›」を出す。 */
@Composable
private fun SettingsRow(
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, description: String, toggle: @Composable () -> Unit) {
    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        toggle()
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    ok: Boolean,
    okText: String,
    onClick: (() -> Unit)? = null,
) {
    val colors = AppWatchTheme.colors
    SettingsRow(
        title = title,
        description = description,
        onClick = onClick,
        trailing = {
            if (ok) {
                StateText(okText, colors.ok)
            } else {
                StateText(stringResource(R.string.state_not_configured), colors.warning)
            }
        },
    )
}

@Composable
private fun ValueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun StateText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}
