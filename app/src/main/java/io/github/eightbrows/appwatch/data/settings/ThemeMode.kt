package io.github.eightbrows.appwatch.data.settings

import android.os.Build

/** テーマ（仕様書 6章「表示」）。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * ダークで表示するかどうかを決める。
 * Android 9 以下は端末にダークモードの設定がないため、「システムに従う」をライト扱いにする。
 */
fun ThemeMode.isDark(
    systemIsDark: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> sdkInt >= Build.VERSION_CODES.Q && systemIsDark
}
