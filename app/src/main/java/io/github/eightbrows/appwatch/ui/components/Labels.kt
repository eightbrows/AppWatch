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
import io.github.eightbrows.appwatch.data.status.FilterItem
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

/** 絞り込みの項目の名前（下から出す絞り込みの画面。チップは短い名前）。 */
@StringRes
fun FilterItem.labelRes(): Int = when (this) {
    FilterItem.STATUS_UNCONFIRMED -> R.string.filter_item_unconfirmed
    FilterItem.STATUS_CAUTION -> R.string.filter_item_caution
    FilterItem.STATUS_OK -> R.string.filter_item_ok
    FilterItem.DEVICE_PRESENT -> R.string.filter_item_present
    FilterItem.ENABLED -> R.string.filter_item_enabled
    FilterItem.DISABLED -> R.string.filter_item_disabled
    FilterItem.KIND_USER -> R.string.filter_item_user
    FilterItem.KIND_PREINSTALLED -> R.string.filter_item_preinstalled
    FilterItem.KIND_SYSTEM -> R.string.filter_item_system
    FilterItem.SOURCE_PLAY -> R.string.filter_item_play
    FilterItem.SOURCE_OTHER -> R.string.filter_item_other
    FilterItem.SOURCE_UNKNOWN -> R.string.filter_item_unknown
    FilterItem.WARNING_NONE -> R.string.filter_item_warning_none
    FilterItem.WARNING_DEBUG -> R.string.filter_item_debug
    FilterItem.WARNING_SIGNATURE -> R.string.filter_item_signature
    FilterItem.WARNING_REINSTALL -> R.string.filter_item_reinstall
    FilterItem.DEVICE_ABSENT -> R.string.filter_item_absent
    FilterItem.ABSENT_REMOVED -> R.string.filter_item_removed
    FilterItem.ABSENT_PRE_REGISTERED -> R.string.filter_item_pre_registered
}

/** 一覧の上の条件の1行で使う名前（「〜を除く」「〜だけ」の中。英語は「Hide 〜」で自然に読める形）。 */
@StringRes
fun FilterItem.conditionNameRes(): Int = when (this) {
    FilterItem.STATUS_UNCONFIRMED -> R.string.filter_name_unconfirmed
    FilterItem.STATUS_CAUTION -> R.string.filter_name_caution
    FilterItem.STATUS_OK -> R.string.filter_name_ok
    FilterItem.DEVICE_PRESENT -> R.string.filter_name_present
    FilterItem.ENABLED -> R.string.filter_name_enabled
    FilterItem.DISABLED -> R.string.filter_name_disabled
    FilterItem.KIND_USER -> R.string.filter_name_user
    FilterItem.KIND_PREINSTALLED -> R.string.filter_name_preinstalled
    FilterItem.KIND_SYSTEM -> R.string.filter_name_system
    FilterItem.SOURCE_PLAY -> R.string.filter_name_play
    FilterItem.SOURCE_OTHER -> R.string.filter_name_other
    FilterItem.SOURCE_UNKNOWN -> R.string.filter_name_unknown
    FilterItem.WARNING_NONE -> R.string.filter_name_warning_none
    FilterItem.WARNING_DEBUG -> R.string.filter_name_debug
    FilterItem.WARNING_SIGNATURE -> R.string.filter_name_signature
    FilterItem.WARNING_REINSTALL -> R.string.filter_name_reinstall
    FilterItem.DEVICE_ABSENT -> R.string.filter_name_absent
    FilterItem.ABSENT_REMOVED -> R.string.filter_name_removed
    FilterItem.ABSENT_PRE_REGISTERED -> R.string.filter_name_pre_registered
}

/** エポックミリ秒を端末の時間帯の日時にする。 */
fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

/** 曜日付きの短い日付（今年なら「11/8(土)」、それより前なら「2025/9/3(水)」）。警告の説明の中の日付に使う。 */
@Composable
fun formatShortDate(dateTime: LocalDateTime): String {
    val sameYear = dateTime.year == LocalDate.now().year
    val pattern = stringResource(if (sameYear) R.string.format_short_date else R.string.format_short_date_with_year)
    val configuration = LocalConfiguration.current
    val formatter = remember(pattern, configuration) {
        val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        DateTimeFormatter.ofPattern(pattern, locale)
    }
    return formatter.format(dateTime)
}

/**
 * 曜日付きの日時（strings.xml の書式）。
 * 今年なら月日と時刻（format_date_time）、それより前なら年月日（format_date_with_year）。
 */
@Composable
fun formatDateTime(dateTime: LocalDateTime): String = formatDate(dateTime.toEpochMillis(), DateForm.LONG)
