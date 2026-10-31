package io.github.eightbrows.appwatch.ui.settings

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.BuildConfig
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.dummy.DummyData
import io.github.eightbrows.appwatch.data.settings.AppLanguage
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.model.PermissionState
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.model.UsageMode
import io.github.eightbrows.appwatch.ui.components.AdminTopBar
import io.github.eightbrows.appwatch.ui.components.SegmentedToggle
import io.github.eightbrows.appwatch.ui.components.labelRes
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

/**
 * 設定メニュー（管理者）。画面サンプル 05_settings_menu.png。
 * このステップで動くのはテーマ・言語・バージョンの表示だけ。他の項目は並べているだけで、値はダミー。
 */
@Composable
fun SettingsMenuScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppWatchTheme.colors
    val cautionCount = DummyData.apps.count { it.reviewStatus == ReviewStatus.CAUTION }
    val navigationBars = WindowInsets.navigationBars.asPaddingValues()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AdminTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
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
                        showChevron = true,
                        trailing = {
                            if (cautionCount > 0) {
                                Text(
                                    text = pluralStringResource(R.plurals.settings_caution_count, cautionCount, cautionCount),
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
                        showChevron = true,
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_history),
                        description = stringResource(R.string.settings_history_desc),
                        showChevron = true,
                    )
                }
            }

            item(key = "data") {
                SettingsSection(stringResource(R.string.settings_section_data)) {
                    SettingsRow(
                        title = stringResource(R.string.settings_export_import),
                        description = stringResource(R.string.settings_export_import_desc),
                        showChevron = true,
                    )
                    RowDivider()
                    val imported = DummyData.importedDataNames
                    SettingsRow(
                        title = stringResource(R.string.settings_imported_data),
                        description = if (imported.isEmpty()) {
                            stringResource(R.string.settings_imported_data_none)
                        } else {
                            imported.joinToString(stringResource(R.string.settings_list_separator))
                        },
                        showChevron = true,
                    )
                }
            }

            item(key = "behavior") {
                SettingsSection(stringResource(R.string.settings_section_behavior)) {
                    SettingsRow(
                        title = stringResource(R.string.settings_usage_mode),
                        showChevron = true,
                        trailing = {
                            ValueText(
                                stringResource(
                                    when (DummyData.usageMode) {
                                        UsageMode.MONITOR -> R.string.usage_mode_monitor
                                        UsageMode.VIEW_ONLY -> R.string.usage_mode_view_only
                                    },
                                ),
                            )
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_check_interval),
                        trailing = {
                            ValueText(stringResource(R.string.check_interval_minutes, DummyData.checkIntervalMinutes))
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_health_check),
                        trailing = {
                            if (DummyData.healthCheckAllOk) {
                                StateText(stringResource(R.string.health_all_ok), colors.ok)
                            } else {
                                StateText(stringResource(R.string.health_needs_attention), colors.warning)
                            }
                        },
                    )
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
                            selected = themeMode,
                            onSelect = onThemeModeChange,
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
                            selected = language,
                            onSelect = onLanguageChange,
                        )
                    }
                }
            }

            item(key = "permissions") {
                SettingsSection(stringResource(R.string.settings_section_permissions)) {
                    PermissionRow(
                        title = stringResource(R.string.perm_query_all),
                        description = stringResource(R.string.perm_query_all_desc),
                        state = DummyData.permissionQueryAllPackages,
                        showChevron = false,
                    )
                    RowDivider()
                    PermissionRow(
                        title = stringResource(R.string.perm_delete),
                        description = stringResource(R.string.perm_delete_desc),
                        state = DummyData.permissionDeletePackages,
                        showChevron = false,
                    )
                    RowDivider()
                    PermissionRow(
                        title = stringResource(R.string.perm_battery),
                        description = stringResource(R.string.perm_battery_desc),
                        state = DummyData.batteryOptimizationExcluded,
                        showChevron = true,
                    )
                    // 未使用アプリの一時停止は Android 11 以上の仕組み（仕様書 2章）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        RowDivider()
                        PermissionRow(
                            title = stringResource(R.string.perm_unused),
                            description = stringResource(R.string.perm_unused_desc),
                            state = DummyData.unusedAppRestrictionsExcluded,
                            showChevron = true,
                        )
                    }
                }
            }

            item(key = "info") {
                SettingsSection(stringResource(R.string.settings_section_info)) {
                    SettingsRow(
                        title = stringResource(R.string.settings_version),
                        trailing = {
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_license),
                        description = stringResource(R.string.settings_license_desc),
                        showChevron = true,
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_website),
                        description = stringResource(R.string.settings_website_url),
                        showChevron = true,
                    )
                }
            }
        }
    }
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

@Composable
private fun SettingsRow(
    title: String,
    description: String? = null,
    showChevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        if (showChevron) {
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
private fun PermissionRow(title: String, description: String, state: PermissionState, showChevron: Boolean) {
    val colors = AppWatchTheme.colors
    SettingsRow(
        title = title,
        description = description,
        showChevron = showChevron,
        trailing = {
            val color = when (state) {
                PermissionState.GRANTED, PermissionState.CONFIGURED -> colors.ok
                PermissionState.NOT_CONFIGURED -> colors.warning
            }
            StateText(stringResource(state.labelRes()), color)
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
