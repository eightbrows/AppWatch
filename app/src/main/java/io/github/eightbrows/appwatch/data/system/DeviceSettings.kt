package io.github.eightbrows.appwatch.data.system

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * 見守りを続けるための端末の設定（仕様書 6章 初回設定の2）の状態を読む。
 * ウィジェットの「設定の確認が必要です」と、後のステップの設定メニュー・動作チェックで使う。
 */
object DeviceSettings {

    /** 電池の最適化から除外されているか。 */
    fun isBatteryOptimizationExcluded(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 未使用アプリの一時停止から除外されているか。仕組みがあるのは Android 11 以上なので、10 以下は true。
     * Android 12 以降の「使用していないアプリを一時停止」も、同じ設定で切り替わる。
     */
    fun isUnusedAppRestrictionsExcluded(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.isAutoRevokeWhitelisted
        } else {
            true
        }

    /** どちらも設定済みか（ウィジェットで使う）。 */
    fun allOk(context: Context): Boolean =
        isBatteryOptimizationExcluded(context) && isUnusedAppRestrictionsExcluded(context)
}
