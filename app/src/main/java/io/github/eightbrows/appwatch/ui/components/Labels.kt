package io.github.eightbrows.appwatch.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.PermissionState
import io.github.eightbrows.appwatch.model.ReviewReason
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.data.status.FilterChip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 値と画面の文言（strings.xml）の対応をまとめたもの。

@StringRes
fun AppWarning.labelRes(): Int = when (this) {
    AppWarning.NOT_FROM_PLAY -> R.string.warning_not_from_play
    AppWarning.UNKNOWN_SOURCE -> R.string.warning_unknown_source
    AppWarning.DEBUG_SIGNATURE -> R.string.warning_debug_signature
    AppWarning.SIGNATURE_CHANGED -> R.string.warning_signature_changed
    AppWarning.REINSTALLED -> R.string.warning_reinstalled
}

@StringRes
fun ReviewReason.labelRes(): Int = when (this) {
    ReviewReason.CAUTION -> R.string.reason_caution
    ReviewReason.REINSTALLED -> R.string.reason_reinstalled
    ReviewReason.SIGNATURE_CHANGED -> R.string.reason_signature_changed
    ReviewReason.DEBUG_SIGNATURE -> R.string.reason_debug_signature
    ReviewReason.NOT_FROM_PLAY -> R.string.reason_not_from_play
    ReviewReason.UNKNOWN_SOURCE -> R.string.reason_unknown_source
}

@StringRes
fun InstallSource.labelRes(): Int = when (this) {
    InstallSource.GOOGLE_PLAY -> R.string.source_google_play
    InstallSource.OTHER -> R.string.source_other
    InstallSource.UNKNOWN -> R.string.source_unknown
}

@StringRes
fun ReviewStatus.labelRes(): Int = when (this) {
    ReviewStatus.UNCONFIRMED -> R.string.status_unconfirmed
    ReviewStatus.OK -> R.string.status_ok
    ReviewStatus.CAUTION -> R.string.status_caution
}

@StringRes
fun PermissionState.labelRes(): Int = when (this) {
    PermissionState.GRANTED -> R.string.state_granted
    PermissionState.CONFIGURED -> R.string.state_configured
    PermissionState.NOT_CONFIGURED -> R.string.state_not_configured
}

@StringRes
fun AppKind.labelRes(): Int = when (this) {
    AppKind.USER_INSTALLED -> R.string.kind_user_installed
    AppKind.PREINSTALLED -> R.string.kind_preinstalled
    AppKind.SYSTEM -> R.string.kind_system
}

@StringRes
fun FilterChip.labelRes(): Int = when (this) {
    FilterChip.STATUS_UNCONFIRMED -> R.string.filter_status_unconfirmed
    FilterChip.STATUS_CAUTION -> R.string.filter_status_caution
    FilterChip.STATUS_OK -> R.string.filter_status_ok
    FilterChip.KIND_USER -> R.string.filter_kind_user
    FilterChip.KIND_PREINSTALLED -> R.string.filter_kind_preinstalled
    FilterChip.KIND_SYSTEM -> R.string.filter_kind_system
    FilterChip.SOURCE_PLAY -> R.string.filter_source_play
    FilterChip.SOURCE_OTHER -> R.string.filter_source_other
    FilterChip.SOURCE_UNKNOWN -> R.string.filter_source_unknown
    FilterChip.WARNING_NONE -> R.string.filter_warning_none
    FilterChip.WARNING_DEBUG -> R.string.filter_warning_debug
    FilterChip.WARNING_SIGNATURE -> R.string.filter_warning_signature
    FilterChip.WARNING_REINSTALL -> R.string.filter_warning_reinstall
    FilterChip.DEVICE_PRESENT -> R.string.filter_device_present
    FilterChip.DEVICE_ABSENT -> R.string.filter_device_absent
    FilterChip.REGISTERED -> R.string.filter_registered
    FilterChip.DISABLED -> R.string.filter_disabled
}

/** エポックミリ秒を端末の時間帯の日時にする。 */
fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

/** 短い日付（今年なら「10/02」、それより前なら「2025/10/02」）。これまでの経緯・警告の説明に使う。 */
@Composable
fun formatShortDate(dateTime: LocalDateTime): String {
    val sameYear = dateTime.year == LocalDate.now().year
    val pattern = stringResource(if (sameYear) R.string.format_short_date else R.string.format_short_date_with_year)
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    return formatter.format(dateTime)
}

/**
 * 日時を strings.xml の書式で文字にする。
 * 今年なら月日と時刻（format_date_time）、それより前なら年月日（format_date_with_year）。
 */
@Composable
fun formatDateTime(dateTime: LocalDateTime): String {
    val sameYear = dateTime.year == LocalDate.now().year
    val pattern = stringResource(if (sameYear) R.string.format_date_time else R.string.format_date_with_year)
    val configuration = LocalConfiguration.current
    val formatter = remember(pattern, configuration) {
        val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        DateTimeFormatter.ofPattern(pattern, locale)
    }
    return formatter.format(dateTime)
}
