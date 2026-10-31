package io.github.eightbrows.appwatch.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 言語（仕様書 6章「表示」）。
 * AppCompat のアプリ別言語で切り替える。Android 13 以降は OS が、12 以下は AppCompat が保存する。
 * 切り替えると Activity が作り直され、すぐに反映される。
 */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    JAPANESE("ja"),
    ENGLISH("en");

    companion object {
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val language = locales[0]?.language
            return entries.firstOrNull { it.languageTag == language } ?: SYSTEM
        }

        fun apply(language: AppLanguage) {
            val locales = language.languageTag
                ?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
