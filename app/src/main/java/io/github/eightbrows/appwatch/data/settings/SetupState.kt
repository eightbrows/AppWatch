package io.github.eightbrows.appwatch.data.settings

import io.github.eightbrows.appwatch.model.UsageMode

/** 初回設定の状態（DataStore）。 */
data class SetupState(
    val completed: Boolean,
    /** 使い方。初回設定の1の画面で選ぶまでは null */
    val usageMode: UsageMode?,
) {
    /**
     * この端末で見守り（アプリを開いたときのチェック・定期チェック・ウィジェット）を行うか。
     * 初回設定が終わっていて、閲覧専用でないとき（仕様書 3章・6章）。
     * 使い方が保存されていない完了済みの端末は、見守る端末として扱う。
     */
    val isMonitoring: Boolean get() = completed && usageMode != UsageMode.VIEW_ONLY

    /** ウィジェットを使うか。閲覧専用の端末では使わない（初回設定の前は、初期設定の依頼を出すために使う）。 */
    val usesWidget: Boolean get() = usageMode != UsageMode.VIEW_ONLY

    /** 起動したときに開く画面。 */
    val startDestination: StartDestination
        get() = when {
            !completed -> StartDestination.SETUP
            usageMode == UsageMode.VIEW_ONLY -> StartDestination.IMPORTED_DATA
            else -> StartDestination.TOP
        }
}

/** 起動したときに開く画面。 */
enum class StartDestination {
    /** 初回設定 */
    SETUP,

    /** トップ（見守る端末） */
    TOP,

    /** 取り込んだデータ（閲覧専用） */
    IMPORTED_DATA,
}
