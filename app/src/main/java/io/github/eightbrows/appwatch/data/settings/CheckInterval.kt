package io.github.eightbrows.appwatch.data.settings

import androidx.annotation.StringRes
import io.github.eightbrows.appwatch.R

/** チェック間隔の選択肢（仕様書 5章）。DataStore には分で保存する。 */
enum class CheckInterval(val minutes: Int, @param:StringRes val labelRes: Int) {
    MINUTES_30(30, R.string.check_interval_30m),
    HOURS_1(60, R.string.check_interval_1h),
    HOURS_3(180, R.string.check_interval_3h),
    HOURS_6(360, R.string.check_interval_6h),
    ;

    companion object {
        /** 初期値（30分）。 */
        val DEFAULT: CheckInterval = MINUTES_30

        /** 保存されている分から選択肢を選ぶ。知らない値なら初期値。 */
        fun fromMinutes(minutes: Int): CheckInterval = entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}
