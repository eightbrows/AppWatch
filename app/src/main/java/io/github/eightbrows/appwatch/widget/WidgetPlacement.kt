package io.github.eightbrows.appwatch.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** ウィジェットの置き方と、使う・使わないの切り替え。 */
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
     * ウィジェットを使うかどうかを切り替える。使わないとき（閲覧専用）は受け取りを無効にし、
     * ウィジェットの一覧に出ないようにする（仕様書 3章）。今と同じなら何もしない。
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val wanted = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT // マニフェストの既定（有効）に戻す
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (pm.getComponentEnabledSetting(component(context)) != wanted) {
            pm.setComponentEnabledSetting(component(context), wanted, PackageManager.DONT_KILL_APP)
        }
    }

    /** ウィジェットの受け取りが有効か（確かめる用）。 */
    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}
