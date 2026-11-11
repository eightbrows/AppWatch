package io.github.eightbrows.appwatch.widget

/** ウィジェットの状態の色（縁・札・見出し・丸）。実際の色は res/values・values-night の colors.xml（widget_*）。 */
enum class WidgetTone { RED, GRAY, ORANGE, BLUE, GREEN }

/** ウィジェットの丸の中の印。 */
enum class WidgetMark {
    /** 「!」 */
    EXCLAMATION,

    /** 一時停止（‖） */
    PAUSE,

    /** 「?」 */
    QUESTION,

    /** 歯車 */
    GEAR,

    /** 「i」 */
    INFO,

    /** チェック（✓） */
    CHECK,
}

/** 状態ごとの色と印の選び方（仕様書 6章のウィジェットの表）。Android を使わない純粋な関数。 */
object WidgetLook {

    fun toneOf(status: WidgetStatus): WidgetTone = when (status) {
        WidgetStatus.CAUTION_APPS -> WidgetTone.RED
        WidgetStatus.CHECK_STOPPED -> WidgetTone.GRAY
        WidgetStatus.NEEDS_REVIEW -> WidgetTone.ORANGE
        WidgetStatus.SETTINGS_NEEDED -> WidgetTone.ORANGE
        WidgetStatus.SETUP_REQUIRED -> WidgetTone.BLUE
        WidgetStatus.ALL_CLEAR -> WidgetTone.GREEN
    }

    fun markOf(status: WidgetStatus): WidgetMark = when (status) {
        WidgetStatus.CAUTION_APPS -> WidgetMark.EXCLAMATION
        WidgetStatus.CHECK_STOPPED -> WidgetMark.PAUSE
        WidgetStatus.NEEDS_REVIEW -> WidgetMark.QUESTION
        WidgetStatus.SETTINGS_NEEDED -> WidgetMark.GEAR
        WidgetStatus.SETUP_REQUIRED -> WidgetMark.INFO
        WidgetStatus.ALL_CLEAR -> WidgetMark.CHECK
    }
}
