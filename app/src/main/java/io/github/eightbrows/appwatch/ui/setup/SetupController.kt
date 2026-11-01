package io.github.eightbrows.appwatch.ui.setup

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.data.record.CheckOutcome
import io.github.eightbrows.appwatch.data.system.DeviceSettings
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.model.UsageMode
import io.github.eightbrows.appwatch.widget.WidgetPlacement
import kotlinx.coroutines.flow.first
import java.util.UUID

/** 初回設定の画面から使う処理（Android・データベース・設定値）。画面の部品から切り離してある。 */
class SetupController(context: Context) {

    private val appContext = context.applicationContext
    private val app = appContext as AppWatchApplication

    // ---- 1 使い方の選択 ----

    /**
     * 使い方を保存する。閲覧専用なら初回設定はここで完了する（2〜4は出さない）。
     * 完了すると、アプリ側で定期チェックの登録とウィジェットの有効・無効が切り替わる。
     */
    suspend fun chooseMode(mode: UsageMode) {
        app.settingsRepository.setUsageMode(mode)
        if (mode == UsageMode.VIEW_ONLY) app.settingsRepository.setSetupCompleted(true)
    }

    // ---- 2 見守りを続けるための設定 ----

    fun isBatteryOptimizationExcluded(): Boolean = DeviceSettings.isBatteryOptimizationExcluded(appContext)

    fun isUnusedAppRestrictionsExcluded(): Boolean = DeviceSettings.isUnusedAppRestrictionsExcluded(appContext)

    /** 未使用アプリの一時停止は Android 11 以上の仕組み。10 以下では行を出さない。 */
    val showsUnusedAppRestrictions: Boolean = DeviceSettings.hasUnusedAppRestrictions

    fun batteryOptimizationIntent(): Intent = DeviceSettings.batteryOptimizationIntent(appContext)

    fun unusedAppRestrictionsIntent(): Intent = DeviceSettings.unusedAppRestrictionsIntent(appContext)

    /** 未使用アプリの一時停止の設定画面で、どのスイッチを切り替えるかの案内。 */
    @get:StringRes
    val unusedAppRestrictionsHint: Int get() = DeviceSettings.unusedAppRestrictionsHint()

    // ---- 3 最初のチェック ----

    /** 最初のチェック。初回設定の前でも行う（初回なら基準を作る）。 */
    suspend fun runFirstCheck(): CheckOutcome = app.checkRunner.check(force = true)

    /** 記録した、端末にあるアプリ。 */
    suspend fun presentApps(): List<AppSummary> = app.recordRepository.observePresentApps().first()

    /**
     * チェックを付けた項目の「未確認」のアプリを、まとめて「問題なし」にする。
     * 通常の確認状態の変更と同じく、変更前の値を持つ変化として1つの操作の番号で記録する（仕様書 8章）。
     */
    suspend fun markOk(checked: Set<SetupCategory>): Int {
        val targets = SetupSelection.targets(presentApps(), checked)
        if (targets.isEmpty()) return 0
        return app.recordRepository.setReviewStatus(targets, ReviewStatus.OK, operationId = UUID.randomUUID().toString())
    }

    // ---- 4 ウィジェットの追加 ----

    fun isWidgetPlaced(): Boolean = WidgetPlacement.isPlaced(appContext)

    fun canRequestWidgetPin(): Boolean = WidgetPlacement.canRequestPin(appContext)

    fun requestWidgetPin(): Boolean = WidgetPlacement.requestPin(appContext)

    /** 初回設定を完了する（見守る端末）。完了すると、アプリ側で定期チェックが登録され、ウィジェットが更新される。 */
    suspend fun complete() {
        app.settingsRepository.setUsageMode(UsageMode.MONITOR)
        app.settingsRepository.setSetupCompleted(true)
    }
}
