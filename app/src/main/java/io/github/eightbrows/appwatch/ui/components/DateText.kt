package io.github.eightbrows.appwatch.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.core.os.ConfigurationCompat
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.model.DateDisplay
import io.github.eightbrows.appwatch.model.DaysAgo
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

// 画面に出す日付（曜日と何日前）。決まりは DateDisplay（単体テストあり）、ここは文言と色を付けるだけ。

/** 日付の形。どちらも曜日を付け、今と違う年なら年を出す（そのときは時刻を出さない）。 */
enum class DateForm {
    /** 「11月8日(土) 21:16」「Sat, Nov 8, 21:16」：トップのカード・アプリの詳細・動作チェック */
    LONG,

    /** 「11/8(土) 21:16」「Sat, Nov 8, 21:16」：一覧の行（確認状態・履歴と比較・取り込んだデータ）、トップの最終チェック */
    LIST,
}

/** アプリで選んでいる言語（曜日・月名の言葉を決める）。 */
@Composable
private fun appLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT

/** LocalDateTime（端末の時間帯）をエポックミリ秒にする。 */
fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 曜日付きの日時。 */
@Composable
fun formatDate(timeMillis: Long, form: DateForm = DateForm.LONG): String {
    val (pattern, withYear) = when (form) {
        DateForm.LONG -> R.string.format_date_time to R.string.format_date_with_year
        DateForm.LIST -> R.string.format_list_date_time to R.string.format_list_date_with_year
    }
    return DateDisplay.format(
        timeMillis = timeMillis,
        nowMillis = System.currentTimeMillis(),
        zone = ZoneId.systemDefault(),
        pattern = stringResource(pattern),
        withYearPattern = stringResource(withYear),
        locale = appLocale(),
    )
}

/** 「今日」「昨日」「n日前」（暦の日で数える）。 */
@Composable
fun daysAgoText(timeMillis: Long): String =
    when (val ago = DateDisplay.daysAgo(timeMillis, System.currentTimeMillis(), ZoneId.systemDefault())) {
        DaysAgo.Today -> stringResource(R.string.date_today)
        DaysAgo.Yesterday -> stringResource(R.string.date_yesterday)
        is DaysAgo.Days -> {
            val count = ago.count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            pluralStringResource(R.plurals.date_days_ago, count, count)
        }
    }

/**
 * 文字の間で折り返さないようにする（日本語は「今日」の間でも折り返せるので、文字の間に WORD JOINER を入れる）。
 * WORD JOINER は幅がなく、表示も読み上げもされない。
 */
private fun String.unbreakable(): String = toCharArray().joinToString("⁠")

/** [text] の後に「 ・ 何日前」を足す。何日前の部分は青の太字で、途中では折り返さない。 */
@Composable
fun withDaysAgo(text: String, timeMillis: Long): AnnotatedString {
    val ago = daysAgoText(timeMillis).unbreakable()
    val full = stringResource(R.string.date_with_ago, text, ago)
    val start = full.lastIndexOf(ago)
    val color = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        if (start < 0) {
            append(full)
        } else {
            append(full.substring(0, start))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(ago) }
            append(full.substring(start + ago.length))
        }
    }
}

@Composable
fun withDaysAgo(text: String, dateTime: LocalDateTime): AnnotatedString = withDaysAgo(text, dateTime.toEpochMillis())
