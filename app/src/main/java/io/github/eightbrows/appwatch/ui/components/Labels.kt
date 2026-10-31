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
import io.github.eightbrows.appwatch.model.ReviewStatus
import java.time.LocalDate
import java.time.LocalDateTime
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
