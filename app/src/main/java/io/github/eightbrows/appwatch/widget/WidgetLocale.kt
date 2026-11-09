package io.github.eightbrows.appwatch.widget

import android.content.Context
import android.content.res.Configuration
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.ConfigurationCompat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ウィジェットの文字を、アプリで選んだ言語で作る（仕様書 6章。Android 12 以下も）。
 * ウィジェットはアプリの画面（AppCompatActivity）の外で作られるので、AppCompatDelegate.getApplicationLocales() は
 * Android 12 以下では空のことがある。そのため、保存されている言語を LocaleManagerCompat で読み、その言語の Context を作る。
 * 「システムに従う」（保存なし）なら、そのままの Context（端末の言語）を使う。
 */
object WidgetLocale {

    /** 言語を切り替えたことを知らせる（ウィジェットの内容を作り直すきっかけ）。 */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun notifyChanged() {
        _changes.update { it + 1 }
    }

    /** アプリで選んだ言語（「システムに従う」なら空）。 */
    fun languageTag(context: Context): String = LocaleManagerCompat.getApplicationLocales(context).toLanguageTags()

    fun localized(context: Context): Context {
        val locales = LocaleManagerCompat.getApplicationLocales(context)
        if (locales.isEmpty) return context
        val config = Configuration(context.resources.configuration)
        ConfigurationCompat.setLocales(config, locales)
        return context.createConfigurationContext(config)
    }

    /** その Context の表示の言語（日時の書式に使う）。 */
    fun localeOf(context: Context): Locale =
        ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
}
