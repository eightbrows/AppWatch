package io.github.eightbrows.appwatch.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** ウィジェットの置き方。 */
object WidgetPlacement {

    private fun component(context: Context) = ComponentName(context, AppWatchWidgetReceiver::class.java)

    /** ホーム画面に1つ以上置かれているか。 */
    fun isPlaced(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

    /** ホーム画面が「ボタンで追加」（requestPinAppWidget）に対応しているか。 */
    fun canRequestPin(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /**
     * ホーム画面に追加する確認を出す。追加するかどうかは、ホーム画面の確認で利用者が決める。
     * @return 確認を出せたら true
     */
    fun requestPin(context: Context): Boolean =
        AppWidgetManager.getInstance(context).requestPinAppWidget(component(context), null, null)

    /**
     * ウィジェットの受け取りは常に有効（マニフェストの既定）。
     * 前の版では閲覧専用にすると無効にしていた。その端末を更新したときのために、無効になっていれば既定に戻す。
     * 無効になっていなければ何もしない。
     */
    fun restoreDefaultEnabled(context: Context) {
        val pm = context.packageManager
        if (pm.getComponentEnabledSetting(component(context)) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(component(context), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        }
    }

    /** ウィジェットの受け取りが有効か（確かめる用）。 */
    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}
