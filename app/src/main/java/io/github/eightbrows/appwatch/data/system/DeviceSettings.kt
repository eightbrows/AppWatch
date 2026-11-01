package io.github.eightbrows.appwatch.data.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.IntentCompat
import io.github.eightbrows.appwatch.R

/**
 * 見守りを続けるための端末の設定（仕様書 6章 初回設定の2）の状態を読み、設定画面を開く。
 * 初回設定の2、設定メニューの「権限」「動作チェック」、ウィジェットの「設定の確認が必要です」で使う。
 */
object DeviceSettings {

    /** 電池の最適化から除外されているか。 */
    fun isBatteryOptimizationExcluded(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 未使用アプリの一時停止は Android 11 以上の仕組み。10 以下では行を出さない。 */
    val hasUnusedAppRestrictions: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * 未使用アプリの一時停止から除外されているか。仕組みがないので、10 以下は true。
     * Android 12 以降の「使用していないアプリを一時停止」も、同じ設定で切り替わる。
     */
    fun isUnusedAppRestrictionsExcluded(context: Context): Boolean =
        if (hasUnusedAppRestrictions) context.packageManager.isAutoRevokeWhitelisted else true

    /** どちらも設定済みか。 */
    fun allOk(context: Context): Boolean =
        isBatteryOptimizationExcluded(context) && isUnusedAppRestrictionsExcluded(context)

    /**
     * 電池の最適化の設定を開く。未設定なら除外の確認（REQUEST_IGNORE_BATTERY_OPTIMIZATIONS が必要）、
     * 設定済みなら、元に戻したいとき用に電池の最適化の一覧を開く。
     */
    @SuppressLint("BatteryLife") // 見守りのための定期チェックが目的（仕様書 2章）。Google Play では配布しない
    fun batteryOptimizationIntent(context: Context): Intent =
        if (isBatteryOptimizationExcluded(context)) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.fromParts("package", context.packageName, null),
            )
        }

    /** 未使用アプリの一時停止の設定画面（本アプリの画面）を開く。 */
    fun unusedAppRestrictionsIntent(context: Context): Intent =
        IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)

    /**
     * 未使用アプリの一時停止の設定画面で、どのスイッチを切り替えるかの案内（仕様書 6章 初回設定の2）。
     * Android 12 以上は「使用していないアプリを一時停止」、11 は名前が違う（権限を自動で削除する設定）。
     */
    @StringRes
    fun unusedAppRestrictionsHint(sdkInt: Int = Build.VERSION.SDK_INT): Int =
        if (sdkInt >= Build.VERSION_CODES.S) R.string.unused_hint_android12 else R.string.unused_hint_android11
}
